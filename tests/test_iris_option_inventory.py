import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from pinned_tools import java_tool

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('discover_iris_options', ROOT / 'scripts/discover_iris_options.py')
parser = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(parser)
BUILDER_SPEC = importlib.util.spec_from_file_location('option_test_builder', ROOT / 'scripts/build_supported_pack_adapters.py')
builder = importlib.util.module_from_spec(BUILDER_SPEC)
BUILDER_SPEC.loader.exec_module(builder)


class InputTests(unittest.TestCase):
    def test_duplicate_and_nonstring_native_values_refuse(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'map.json'
            for text in ('{"A":"1","A":"2"}', '{"A":true}', '[]'):
                path.write_text(text)
                with self.assertRaises(ValueError):
                    parser.option_map(path)

    def test_bad_pin_refuses_before_java(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'file'
            path.write_text('{}')
            with patch.object(parser.subprocess, 'run', side_effect=AssertionError('Java must not launch')):
                with self.assertRaisesRegex(ValueError, 'hash mismatch'):
                    parser.discover(path, path, path, path, '0' * 64, [], path)


class AuthenticIrisTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java = Path(java_tool('java'))
        cls.iris = ROOT / 'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
        coordinates = [('com.google.guava/guava/32.1.2-jre', 'guava-32.1.2-jre.jar'),
                       ('it.unimi.dsi/fastutil/8.5.12', 'fastutil-8.5.12.jar'),
                       ('org.slf4j/slf4j-api/2.0.9', 'slf4j-api-2.0.9.jar'),
                       ('com.google.code.gson/gson/2.10.1', 'gson-2.10.1.jar')]
        cls.dependencies = []
        for coordinate, filename in coordinates:
            paths = list((cache / coordinate).glob('*/' + filename))
            if len(paths) != 1:
                raise unittest.SkipTest('Exact local parser dependency unavailable: ' + filename)
            cls.dependencies.append(paths[0])
        if not cls.java.is_file() or not cls.iris.is_file():
            raise unittest.SkipTest('Pinned local JDK/Iris fixture unavailable')
        cls.iris_sha = '3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83'
        if parser.file_hash(cls.iris) != cls.iris_sha:
            raise AssertionError('Pinned Iris fixture changed')

    def discover(self, original, generated, native, overrides=None):
        return parser.discover(original, generated, native, self.iris, self.iris_sha,
                               self.dependencies, self.java, overrides)

    def test_actual_bsl_and_reimagined_current_recipes_match_packaged_parser_maps(self):
        cases = [('bsl-shaders','BSL_v10.1.8.zip','bslquad/native384.json',384,385),
                 ('complementary-reimagined','ComplementaryReimagined_r5.9.3.zip','reimaginedquad/native398.json',398,403)]
        expected=json.loads((ROOT/'src/main/resources/assets/shaderlod/rendererquad/generated-main-options.json').read_text())
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            overrides = directory / 'overrides.json'
            overrides.write_text(json.dumps({'SHADERLOD_REFINEMENT_ADAPTER': '1'}))
            for adapter, filename, native_name, n, ob in cases:
                with self.subTest(adapter=adapter):
                    original = ROOT / 'dependencies/upstream/other-pack-research' / filename
                    generated = directory / (adapter + '.zip')
                    builder.build(original, generated, ROOT / 'patches/adapters' / (adapter + '.json'))
                    native = ROOT / 'src/main/resources/assets/shaderlod' / native_name
                    report = self.discover(original, generated, native, overrides)
                    self.assertEqual(report['candidateExpectedOptions'],expected[adapter])
                    self.assertEqual((report['nativeOptionCount'],report['candidateOptionCount']),(n,len(expected[adapter])))
                    self.assertEqual(report['originalBroadInventory']['declarationCount'],ob)
                    self.assertEqual(len(report['excludedOriginalDeclarations']),ob-n)
                    self.assertEqual(set(report['candidateExpectedOptions'])-set(json.loads(native.read_text())),set(report['addedDeclarations']))
                    self.assertTrue(report['nativeMapPreserved'])
                    self.assertFalse(report['loadedMapVerified'])
                    self.assertEqual(report['execution']['exitCode'], 0)
                    for key, value in json.loads(native.read_text()).items():
                        self.assertEqual(report['candidateExpectedOptions'][key], value)
                    for key in report['excludedOriginalDeclarations']:
                        self.assertNotIn(key, report['candidateExpectedOptions'])

    def test_actual_bsl_native_value_removed_from_generated_range_refuses(self):
        original = ROOT / 'dependencies/upstream/other-pack-research/BSL_v10.1.8.zip'
        native = ROOT / 'src/main/resources/assets/shaderlod/bslquad/native384.json'
        with tempfile.TemporaryDirectory() as directory:
            generated = Path(directory) / 'narrowed.zip'
            with zipfile.ZipFile(original) as source, zipfile.ZipFile(generated, 'w') as output:
                for name in source.namelist():
                    data = source.read(name)
                    if name == 'shaders/lib/settings.glsl':
                        before = b'#define FXAA_EDGE_SENSITIVITY 1 //[0 1 2]'
                        self.assertEqual(data.count(before), 1)
                        data = data.replace(before, b'#define FXAA_EDGE_SENSITIVITY 0 //[0 2]')
                    output.writestr(name, data)
            self.assertEqual(json.loads(native.read_text())['FXAA_EDGE_SENSITIVITY'], '1')
            with self.assertRaisesRegex(ValueError, 'declared range rejects supplied value: FXAA_EDGE_SENSITIVITY=1'):
                self.discover(original, generated, native)

    def test_actual_parser_refuses_removed_declarations_and_invalid_added_override(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            original, generated = directory / 'original.zip', directory / 'generated.zip'
            native, overrides = directory / 'native.json', directory / 'overrides.json'
            def archive(path, text):
                with zipfile.ZipFile(path, 'w') as output:
                    output.writestr('shaders/test.gsh', text)
            archive(original, '#define COUNT 8 // [8 16]\n')
            archive(generated, '#define NEW 1 // [1 2]\n')
            native.write_text('{"COUNT":"8"}')
            with self.assertRaisesRegex(ValueError, 'Removed original declarations'):
                self.discover(original, generated, native)
            archive(generated, '#define COUNT 16 // [8 16]\n//#define NEW\n#ifdef NEW\n#endif\n')
            overrides.write_text('{"NEW":"not-a-boolean"}')
            with self.assertRaisesRegex(ValueError, 'Iris rejected supplied value'):
                self.discover(original, generated, native, overrides)
            overrides.write_text('{"COUNT":"16"}')
            with self.assertRaisesRegex(ValueError, 'may not modify native'):
                self.discover(original, generated, native, overrides)
            overrides.write_text('{"TYPO":"true"}')
            with self.assertRaisesRegex(ValueError, 'newly added declarations only'):
                self.discover(original, generated, native, overrides)
            report = self.discover(original, generated, native)
            self.assertEqual(report['candidateExpectedOptions'], {'COUNT': '8', 'NEW': 'false'})
            self.assertEqual(report['changedDefaults'], {'COUNT': {'original': '8', 'generated': '16'}})


if __name__ == '__main__':
    unittest.main()
