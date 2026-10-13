import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

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
        cls.java = ROOT / 'dev/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin/java'
        cls.iris = ROOT / 'ports/26.1.2/runtime/iris-fabric-1.11.4+mc26.1.2.jar'
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
        cls.iris_sha = '69bdb6899ba006cf91b16565e4445a7a4f331e39b790c81c99626814bf6e22df'
        if parser.file_hash(cls.iris) != cls.iris_sha:
            raise AssertionError('Pinned Iris fixture changed')

    def discover(self, original, generated, native, overrides=None):
        return parser.discover(original, generated, native, self.iris, self.iris_sha,
                               self.dependencies, self.java, overrides)

    def test_actual_bsl_and_reimagined_current_recipes_match_saved_parser_maps(self):
        cases = [('bsl-shaders', 'BSL_v10.1.8.zip', 'bslquad/native384.json',
                  'bsl-runtime/actual-iris-generated-options.json', 384, 388, 385, 389),
                 ('complementary-reimagined', 'ComplementaryReimagined_r5.9.3.zip',
                  'reimaginedquad/native398.json',
                  'complementary-runtime/reimagined-actual-iris-generated-options.json', 398, 402, 403, 407)]
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            overrides = directory / 'overrides.json'
            overrides.write_text(json.dumps({'SHADERLOD_REFINEMENT_ADAPTER': '1'}))
            for adapter, filename, native_name, saved_name, n, c, ob, gb in cases:
                with self.subTest(adapter=adapter):
                    original = ROOT / 'dev/upstream/other-pack-research' / filename
                    generated = directory / (adapter + '.zip')
                    builder.build(original, generated, ROOT / 'patches/adapters' / (adapter + '.json'))
                    native = ROOT / 'src/main/resources/assets/shaderlod' / native_name
                    report = self.discover(original, generated, native, overrides)
                    saved = json.loads((ROOT / 'dev/logs/concept-transfer-r1' / saved_name).read_text())
                    self.assertEqual(report['candidateExpectedOptions'], saved['generated']['effectivePlannedMap'])
                    self.assertEqual(report['addedDeclarations'], saved['actualAddedOptions'])
                    self.assertEqual((report['nativeOptionCount'], report['candidateOptionCount']), (n, c))
                    self.assertEqual((report['originalBroadInventory']['declarationCount'],
                                      report['generatedBroadInventory']['declarationCount']), (ob, gb))
                    self.assertEqual(len(report['excludedOriginalDeclarations']), ob - n)
                    self.assertTrue(report['nativeMapPreserved'])
                    self.assertFalse(report['loadedMapVerified'])
                    self.assertEqual(report['execution']['exitCode'], 0)
                    for key, value in json.loads(native.read_text()).items():
                        self.assertEqual(report['candidateExpectedOptions'][key], value)
                    for key in report['excludedOriginalDeclarations']:
                        self.assertNotIn(key, report['candidateExpectedOptions'])

    def test_actual_bsl_native_value_removed_from_generated_range_refuses(self):
        original = ROOT / 'dev/upstream/other-pack-research/BSL_v10.1.8.zip'
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
