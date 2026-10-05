"""Preparation guards use tiny archives only; no world copies or clients."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from prepare_isolations import plan, archive_contents, isolation_options, read_control, BANDS, PINNED, filter_effective_options


class IsolationTests(unittest.TestCase):
    def arguments(self, root, **kwargs):
        values = dict(source_proof_root=root / 'source', output=root / 'output', scenes=['forest', 'coastalwater'],
                      archive='analytic', archive_path=None, analytic='on', water_bands='current', backup_existing=False)
        values.update(kwargs)
        return argparse.Namespace(**values)

    def test_plan_preserves_source_and_selects_exact_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            operation = plan(self.arguments(root))
            self.assertEqual(operation['archive_sha256'], PINNED['analytic'][1])
            self.assertEqual(operation['water_bands'], BANDS['current'])
            self.assertEqual(len(operation['scene_operations']), 2)
            self.assertFalse((root / 'output').exists())
            old = plan(self.arguments(root, archive='structural', analytic='off', water_bands='old'))
            self.assertEqual(old['archive_sha256'], PINNED['structural'][1])
            self.assertFalse(old['analytic_enabled'])
            self.assertEqual(old['water_bands'], (32.0, 96.0, 8.0))

    def test_overlap_duplicate_collision_guards(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for args in [self.arguments(root, output=root / 'source/inside'), self.arguments(root, output=root),
                         self.arguments(root, scenes=['forest', 'forest'])]:
                with self.assertRaises(ValueError): plan(args)
            (root / 'output').mkdir()
            with self.assertRaises(ValueError): plan(self.arguments(root))
            with self.assertRaises(ValueError): plan(self.arguments(root, backup_existing=True))
            (root / 'output/isolation-manifest.json').write_text('{"schema": 1}')
            plan(self.arguments(root, backup_existing=True))

    def archive(self, root, **changes):
        photon = {'version': 'v1.3b', 'hashes': {'sha512': 'base'}}
        metadata = dict(base_version='v1.3b', base_sha512='base', bridge_version=2)
        metadata.update(changes)
        path = root / 'fixture.zip'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('LICENSE', 'Photon license retained')
            archive.writestr('shaders/settings.glsl', '#define QUALITY high // [high low]\n')
            archive.writestr('shaderlod-build.json', json.dumps(metadata))
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        return path, digest, photon

    def test_exact_archive_hash_metadata_license_guards(self):
        with tempfile.TemporaryDirectory() as directory:
            path, digest, photon = self.archive(Path(directory))
            result = archive_contents(path, digest, photon, b'Photon license retained')
            self.assertEqual(result['metadata']['bridge_version'], 2)
            for expected in (None, '', '0' * 64):
                with self.assertRaises(ValueError): archive_contents(path, expected, photon)
            with self.assertRaises(ValueError): archive_contents(path, digest, photon, b'Wrong license')
            with self.assertRaises(ValueError): archive_contents(path, digest, photon | {'version': 'wrong'}, b'Photon license retained')
            path, digest, photon = self.archive(Path(directory), bridge_version=99)
            with self.assertRaises(ValueError): archive_contents(path, digest, photon, b'Photon license retained')

    def test_missing_license_guard(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'missing.zip'
            with zipfile.ZipFile(path, 'w') as archive: archive.writestr('shaders/settings.glsl', '#define A')
            with self.assertRaises(ValueError): archive_contents(path, hashlib.sha256(path.read_bytes()).hexdigest(), {'version': 'v1.3b'})

    def test_control_removes_mode_and_terrain_overrides(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / 'high'; source.mkdir()
            control = dict(x=1, y=70, z=4, yaw=180, pitch=3, enabled=True, terrainEnabled=True, terrainChunkCap=8, warmSourceConfirmed=True)
            (root / 'control.json').write_text(json.dumps(control))
            matched = read_control(source)
            self.assertTrue(all(name not in matched for name in ('enabled', 'terrainEnabled', 'terrainChunkCap', 'warmSourceConfirmed')))
            self.assertEqual(matched['pitch'], 3)
            self.assertEqual(matched['preset'], 'PERFORMANCE')
            control['x'] = float('nan'); (root / 'control.json').write_text(json.dumps(control))
            with self.assertRaises(ValueError): read_control(source)

    def test_iris_header_defaults_transparent_and_explicit_overrides_preserved(self):
        settings = '#define QUALITY high // [high low]\n'
        declarations = {'QUALITY': [['shaders/settings.glsl', 'high']],
                        'FRAGMENT_SHADER': [['shaders/pass.fsh', 'true']],
                        'HEADER_GUARD': [['shaders/include/foo.glsl', 'true']],
                        'EXTERNAL_CONTROL': [['shaders/include/foo.glsl', '4']]}
        original = {'settings': settings, 'declarations': declarations}
        candidate = {'settings': settings, 'declarations': copy.deepcopy(declarations)}
        effective = {'QUALITY': 'high', 'FRAGMENT_SHADER': 'true', 'HEADER_GUARD': 'true', 'EXTERNAL_CONTROL': '4'}
        common, evidence = filter_effective_options(effective, {}, original, candidate)
        self.assertEqual(common, {'QUALITY': 'high'})
        self.assertEqual(evidence['source_effective_shader_options'], effective)
        self.assertEqual(evidence['omitted_non_settings_defaults']['FRAGMENT_SHADER'], 'true')
        common, evidence = filter_effective_options(effective, {'EXTERNAL_CONTROL': '4'}, original, candidate)
        self.assertEqual(common['EXTERNAL_CONTROL'], '4')
        self.assertNotIn('EXTERNAL_CONTROL', evidence['omitted_non_settings_defaults'])
        candidate['declarations']['EXTERNAL_CONTROL'] = [['shaders/include/foo.glsl', '8']]
        with self.assertRaises(ValueError): filter_effective_options(effective, {'EXTERNAL_CONTROL': '4'}, original, candidate)
        with self.assertRaises(ValueError): filter_effective_options(effective, {'UNKNOWN_USER_KNOB': 'true'}, original, candidate)

    def settings(self):
        return '''#define QUALITY high // [high low]
#define SHADERLOD_PRESERVE_LIGHTING
//#define SHADERLOD_CACHED_SKY
#define SHADERLOD_USE_MOD_CONFIG
#define SHADERLOD_ENABLED
#define SHADERLOD_WATER_ANALYTIC
#define SHADERLOD_WATER_NEAR 96.0 // [32.0 96.0]
#define SHADERLOD_WATER_FAR 256.0 // [96.0 256.0]
#define SHADERLOD_WATER_TRANSITION 32.0 // [8.0 32.0]
#define SHADERLOD_CLOUD_NEAR 128.0 // [128.0]
#define SHADERLOD_CLOUD_FAR 1024.0 // [1024.0]
#define SHADERLOD_CLOUD_TRANSITION 64.0 // [64.0]
#define SHADERLOD_NEAR 32.0 // [32.0]
#define SHADERLOD_FAR 96.0 // [96.0]
#define SHADERLOD_TRANSITION 8.0 // [8.0]
//#define SHADERLOD_DEBUG
'''

    def test_isolation_knobs_do_not_change_upstream_values(self):
        common = {'QUALITY': 'high'}
        current = isolation_options(common, self.settings(), True, BANDS['current'])
        old = isolation_options(common, self.settings(), False, BANDS['old'])
        self.assertEqual(current['QUALITY'], old['QUALITY'])
        self.assertEqual(old['SHADERLOD_WATER_NEAR'], '32.0')
        self.assertEqual(old['SHADERLOD_WATER_ANALYTIC'], 'false')
        self.assertEqual(old['SHADERLOD_PRESERVE_LIGHTING'], 'true')
        self.assertEqual(old['SHADERLOD_CACHED_SKY'], 'false')
        with self.assertRaises(ValueError): isolation_options(common, self.settings(), True, (1, 2, 3))


if __name__ == '__main__': unittest.main()
