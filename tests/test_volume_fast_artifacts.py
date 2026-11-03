"""Counterexamples for the independently pinned volume artifact locator contract."""
import copy
import hashlib
import io
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from qualify_volume_fast_artifacts import (artifact_locator_matches, canonical_nested_uri,
    build_receipt, directory_identity, expected_mods, inventory_group_alternatives,
    java_file_uri, metadata_mods, mod_absent_launch_contract, normalize_record_locator,
    validate_expected_receipt, validate_inventory_against_receipt)


def nested(mod='fabric_api_base', counter=185, pin='a' * 64, outer='/p/iris.jar', member='fabric.jar', expected=False):
    index = '' if expected else '%23' + str(counter)
    return dict(mods={mod: '1'}, path='', uri=f'jij:{outer}{index}!/META-INF/jarjar/{member}', filesystem_provider='jij', identity=pin)


class VolumeFastArtifactTests(unittest.TestCase):
    def test_explicit_mod_absent_mode_removes_only_production_mod(self):
        self.assertEqual(set(expected_mods()) - set(expected_mods(False)), {'shaderlod'})
        self.assertEqual(expected_mods(False)['shaderlod_dev'], '0.1.0')
        self.assertEqual(len(expected_mods()), 10)
        self.assertEqual(len(expected_mods(False)), 9)
        for malformed in (None, 'false', 'true', 0, 1):
            with self.assertRaises(ValueError): expected_mods(malformed)

    def test_mod_absent_launch_contract_has_only_explicit_dev_roots(self):
        contract = mod_absent_launch_contract('/p/project')
        self.assertEqual(contract['gradle_task'], 'runClientLodOff')
        self.assertEqual(contract['registered_development_mods'], ['shaderlod_dev'])
        self.assertEqual(contract['fml_mod_folders'], 'shaderlod_dev%%/p/project/build/classes/java/devClient:shaderlod_dev%%/p/project/build/resources/devClient')
        self.assertTrue(contract['classpath_file'].endswith('/clientLodOffLegacyClasspath.txt'))





    def test_counter_only_change_matches_independent_exact_content(self):
        expected = nested(expected=True)
        for counter in (0, 185, 9223372036854775807):
            self.assertTrue(artifact_locator_matches(nested(counter=counter), expected))
        self.assertEqual(normalize_record_locator(nested())['uri'], expected['uri'])

    def test_wrong_ancestry_content_mod_provider_rejects(self):
        expected = nested(expected=True)
        for updates in ({'identity': 'b' * 64}, {'mods': {'fabric_api_base': '2'}},
                        {'mods': {'unknown': '1'}}, {'uri': nested(outer='/other/iris.jar')['uri']},
                        {'uri': nested(member='other.jar')['uri']}):
            actual = nested(); actual.update(updates)
            self.assertFalse(artifact_locator_matches(actual, expected))
        for updates in ({'filesystem_provider': 'union'}, {'filesystem_provider': 'file'}, {'path': '/wrong'}, {'identity': None}):
            actual = nested(); actual.update(updates)
            with self.assertRaises(ValueError): artifact_locator_matches(actual, expected)

    def test_unrecognized_uri_layout_never_normalized(self):
        for uri in ('jij:/p/iris.jar!/META-INF/jarjar/fabric.jar',
                    'jij:/p/iris.jar%23x!/META-INF/jarjar/fabric.jar',
                    'jij:/p/iris.jar%231!/META-INF/jarjar/fabric.jar~/x',
                    'jij:/p/iris.jar%231!/META-INF/jarjar/fabric.jar/extra',
                    'jij:/p/../iris.jar%231!/META-INF/jarjar/fabric.jar',
                    'jij:/p/iris.jar%231!/META-INF/jarjar/../fabric.jar',
                    'jij:/p/iris.jar%231!/META-INF/jarjar/fabric.jar?x',
                    'jij:/p/iris.jar%25231!/META-INF/jarjar/fabric.jar',
                    'union:/p/iris.jar%231!/META-INF/jarjar/fabric.jar'):
            with self.assertRaises(ValueError, msg=uri): canonical_nested_uri(uri)

    def test_multiple_empty_paths_are_distinct_roots(self):
        first = nested()
        second = nested(mod='other_mod', member='other.jar', pin='b' * 64)
        expected = [nested(expected=True), nested(mod='other_mod', member='other.jar', pin='b' * 64, expected=True)]
        self.assertTrue(validate_inventory_against_receipt([first, second], expected, {'fabric_api_base': '1', 'other_mod': '1'}))

    def test_duplicate_locator_and_mod_coverage_reject(self):
        first = nested()
        second = nested(mod='other_mod', pin='b' * 64)  # Same actual raw locator cannot be two roots.
        expected = [nested(expected=True), nested(mod='other_mod', pin='b' * 64, expected=True)]
        with self.assertRaisesRegex(ValueError, 'Duplicate actual'): validate_inventory_against_receipt([first, second], expected, {'fabric_api_base': '1', 'other_mod': '1'})
        with self.assertRaisesRegex(ValueError, 'Incomplete actual'): validate_inventory_against_receipt([first], expected[:1], {})
        with self.assertRaisesRegex(ValueError, 'root count'): validate_inventory_against_receipt([first], expected, {'fabric_api_base': '1'})

    def test_preregistered_exact_alternatives(self):
        candidates = {'alternatives': [nested(expected=True), nested(expected=True, outer='/p/sodium.jar', pin='b' * 64)]}
        self.assertTrue(validate_inventory_against_receipt([nested(outer='/p/sodium.jar', pin='b' * 64)], [candidates], {'fabric_api_base': '1'}))
        for actual in [nested(outer='/p/sodium.jar'), nested(outer='/unknown.jar'), nested(pin='c' * 64)]:
            with self.assertRaisesRegex(ValueError, 'frozen inventory'): validate_inventory_against_receipt([actual], [candidates], {'fabric_api_base': '1'})
        for group in ({'alternatives': []}, {'alternatives': [nested(expected=True)] * 2}, {'alternatives': [nested(expected=True), nested(mod='other', expected=True)]}):
            with self.assertRaises(ValueError): inventory_group_alternatives(group)

    def test_coalesced_physical_minecraft_neoforge(self):
        record = dict(mods={'minecraft': '1.21.1', 'neoforge': '21.1.253'}, path='/p/neoforge.jar', uri='file:///p/neoforge.jar', filesystem_provider='file', identity='c' * 64)
        self.assertTrue(validate_inventory_against_receipt([record], [record], record['mods']))
        changed = copy.deepcopy(record); changed['mods'].pop('minecraft')
        with self.assertRaises(ValueError): validate_inventory_against_receipt([changed], [record], record['mods'])

    def test_ordinary_uri_path_cannot_disagree(self):
        record = dict(mods={'iris': '1'}, path='/p/iris.jar', uri='file:///wrong.jar', filesystem_provider='file', identity='c' * 64)
        with self.assertRaisesRegex(ValueError, 'URI/path'): normalize_record_locator(record)

    def test_physical_literal_plus_follows_independent_java_recipe(self):
        record = dict(mods={'iris': '1'}, path='/p/iris-1.8.12+mc1.21.1.jar',
            uri='file:///p/iris-1.8.12+mc1.21.1.jar', filesystem_provider='file', identity='c' * 64)
        self.assertEqual(java_file_uri(record['path']), record['uri'])
        self.assertTrue(artifact_locator_matches(record, copy.deepcopy(record)))
        for changes in ({'uri': 'file:///p/iris-1.8.12%2Bmc1.21.1.jar'},
                        {'uri': 'file:///p/iris-1.8.12 mc1.21.1.jar'},
                        {'uri': 'file:///p/other+mc1.21.1.jar'},
                        {'filesystem_provider': 'union'}):
            changed = copy.deepcopy(record); changed.update(changes)
            with self.assertRaises(ValueError): normalize_record_locator(changed)
        changed = copy.deepcopy(record); changed['identity'] = 'd' * 64
        self.assertFalse(artifact_locator_matches(record, changed))

    def test_java_file_uri_preserves_exact_path_without_alias_decoding(self):
        pairs = {'a+b': 'a+b', 'a%2B': 'a%252B', 'a b': 'a%20b', 'a#b': 'a%23b',
            'a?b': 'a%3Fb', 'a!b': 'a!b', "a'b": "a'b", 'a(b)': 'a(b)', 'a:b': 'a:b',
            'a@b': 'a@b', 'a&b': 'a&b', 'a=b': 'a=b', 'a,b': 'a,b', 'a;b': 'a;b',
            'a[b]': 'a%5Bb%5D', 'ñ': '%C3%B1'}
        for path, uri in pairs.items(): self.assertEqual(java_file_uri('/p/' + path + '.jar'), 'file:///p/' + uri + '.jar')
        self.assertEqual(java_file_uri('/p/root', directory=True), 'file:///p/root/')
        for path in ('relative.jar', '/p/../root.jar', '//p/root.jar', '/p/null\0.jar'):
            with self.assertRaises(ValueError): java_file_uri(path)

    def test_tree_digest_matches_java_recipe_and_excludes_symlinks(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); (root / 'a').write_bytes(b'hello'); (root / 'z').mkdir(); (root / 'z' / 'b').write_bytes(b'x')
            (root / 'symlink').symlink_to(root / 'a'); (root / 'linkdir').symlink_to(root / 'z')
            expected = hashlib.sha256(('a\0' + hashlib.sha256(b'hello').hexdigest() + '\nz/b\0' + hashlib.sha256(b'x').hexdigest() + '\n').encode()).hexdigest()
            identity = directory_identity(root)
            self.assertEqual((identity['files'], identity['bytes'], identity['tree_sha256']), (2, 6, expected))
            (root / 'a').write_bytes(b'other')
            self.assertNotEqual(directory_identity(root)['tree_sha256'], identity['tree_sha256'])

    def test_mod_version_resolves_from_real_nested_metadata(self):
        source = io.BytesIO()
        with zipfile.ZipFile(source, 'w') as z:
            z.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\nImplementation-Version: 1.2.3\r\n\r\n')
            z.writestr('META-INF/neoforge.mods.toml', '[[mods]]\nmodId="test"\nversion="${file.jarVersion}"\n')
        self.assertEqual(metadata_mods(source.getvalue()), {'test': '1.2.3'})


if __name__ == '__main__': unittest.main()
