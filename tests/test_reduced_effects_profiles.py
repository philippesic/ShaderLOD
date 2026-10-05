"""Pure real-pin checks and tiny mocked-qualified copies; no actual world prep."""
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import tomllib
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import prepare_reduced_effects_profiles as prep
from run_proof_matrix import CASES, canonical_options_hash
from setup_comparison import WORLD, world_fingerprint, file_hash


class ReducedEffectsTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(); self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve(); self.output = self.root / 'out'
        self.sources = {scene: self.root / 'sources' / scene / 'high' for scene in prep.SCENES}
        self.reduced = self.root / 'reduced.zip'

    def test_pure_plan_uses_exact_real_pins_and_existing_runner_cases(self):
        result = prep.plan(self.output, prep.SCENES, self.sources, self.reduced)
        self.assertEqual(result['original_sha256'], '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead')
        self.assertEqual(result['reduced_sha256'], '636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83')
        self.assertEqual(result['arms'], ['high', 'patched-off', 'adaptive'])
        self.assertFalse(result['dh_present']); self.assertFalse(self.output.exists())
        for scene in prep.SCENES:
            prefix = 'forest' if scene == 'forest' else 'water'
            for arm in prep.ARMS:
                self.assertEqual(CASES[prefix + '-' + arm], (scene + '/' + arm, 'runClientLodOff' if arm == 'high' else 'runClientLodOn'))

    def test_fresh_collision_path_overlap_and_scene_guards(self):
        for scenes in ((), ('unknown',), ('forest', 'forest')):
            with self.assertRaises(ValueError): prep.plan(self.output, scenes, self.sources, self.reduced)
        with self.assertRaises(ValueError): prep.plan(self.sources['forest'].parent / 'new', ('forest',), self.sources, self.reduced)
        self.output.mkdir()
        with self.assertRaisesRegex(ValueError, 'Preserving'): prep.plan(self.output, prep.SCENES, self.sources, self.reduced)

    def test_symlink_selection_is_not_followed(self):
        target = self.root / 'target'; target.mkdir()
        link = self.root / 'link'; link.symlink_to(target, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'symlink'): prep.plan(link, ('forest',), self.sources, self.reduced)
        with self.assertRaisesRegex(ValueError, 'symlink'): prep.plan(self.output, ('forest',), {'forest': link}, self.reduced)

    def test_config_disabled_vs_active_preserves_fixed_nonselected_policy(self):
        configs = {arm: tomllib.loads(prep.fresh_config(arm)) for arm in prep.ARMS}
        for arm, config in configs.items():
            self.assertIs(config['lod']['enabled'], arm == 'adaptive')
            self.assertEqual(config['lod']['preset'], 'PERFORMANCE')
            self.assertEqual((config['lod']['near'], config['lod']['far'], config['lod']['transition']), (32, 96, 8))
            self.assertFalse(config['lod']['debug']); self.assertFalse(config['terrain']['enabled'])
            self.assertFalse(config['terrain']['warmSourceConfirmed'])
            self.assertEqual(config['terrain']['vanillaChunkCap'], 16)
            self.assertTrue(all(value is False for value in config['rendering'].values()))
        configs['adaptive']['lod']['enabled'] = False
        self.assertEqual(configs['patched-off'], configs['adaptive'])

    def test_control_overrides_removed_and_inspected_poses_preserved(self):
        for scene in prep.SCENES:
            original = prep.POSES[scene] | {'preset': 'PERFORMANCE', 'debug': False, 'weather': 'clear'}
            source = original | {'enabled': True, 'terrainEnabled': True, 'terrainChunkCap': 8, 'warmSourceConfirmed': True}
            result, removed = prep.control_for_scene(source, scene)
            self.assertEqual(result, original); self.assertEqual(len(removed), 4)
            self.assertTrue(source['enabled'])  # No mutation of original metadata.
            with self.assertRaises(ValueError): prep.control_for_scene(original | {'x': 0}, scene)
            with self.assertRaises(ValueError): prep.control_for_scene(original | {'capture': 'old.png'}, scene)

    def test_all_five_patch_switches_and_private_reduced_gate_are_fixed(self):
        for name in ('CLOUDS', 'WATER', 'FOG', 'SSR', 'MATERIALS'):
            self.assertEqual(prep.PATCHED_FLAGS['SHADERLOD_' + name], 'true')
        for name in ('SHADERLOD_CACHED_SKY', 'SHADERLOD_WATER_ANALYTIC', 'SHADERLOD_DEBUG'):
            self.assertEqual(prep.PATCHED_FLAGS[name], 'false')
        self.assertEqual(prep.PATCHED_FLAGS['PHOTON_SSR_FAR_REDUCED_TRACE_PROBE'], 'true')
        self.assertEqual(prep.PATCHED_FLAGS['SHADERLOD_PRESERVE_LIGHTING'], 'true')
        self.assertEqual(prep.PATCHED_FLAGS['SHADERLOD_USE_MOD_CONFIG'], 'true')

    def test_unknown_original_archive_rejected_before_any_copy(self):
        path = self.root / 'unknown.zip'; path.write_bytes(b'unknown archive')
        runtime = {'artifacts': {'photon': {'version': 'v1.3b', 'hashes': {'sha512': '0' * 128}}}}
        with self.assertRaisesRegex(ValueError, 'checksum'): prep.qualify_archives(path, self.reduced, runtime)

    def fixture_records(self):
        # Copy/manifest tests use tiny stand-ins, while immutable production pins
        # remain untouched. Archive/member qualification has separate strict tests.
        self.reduced.write_bytes(b'synthetic-reduced')
        records = {}
        dh_name = 'DistantHorizons-fixture.jar'
        for scene, profile in self.sources.items():
            for name in ('config', 'shaderpacks', 'mods', 'logs', 'screenshots', 'camera-quality', 'saves/' + WORLD):
                (profile / name).mkdir(parents=True)
            world = profile / 'saves' / WORLD
            (world / 'level.dat').write_bytes(('tiny canonical ' + scene).encode())
            (world / 'session.lock').write_bytes(b'fixture')
            original = profile / 'shaderpacks/original.zip'; original.write_bytes(b'synthetic-original')
            (profile / 'config/shaderlod-client.toml').write_text('[old]\nretained="source"\n')
            (profile / 'config/iris.properties').write_text('enableShaders=true\nshaderPack=original.zip\n')
            (profile / 'options.txt').write_text('renderDistance:24\n')
            (profile / 'logs/old.log').write_text('old'); (profile / 'screenshots/old.png').write_text('old')
            (profile / 'workload-measurement.json').write_text('old history')
            (profile / 'benchmark.json').write_text('old history')
            (profile / 'camera-quality.json').write_text('old history')
            (profile.parent / 'control.json').write_text(json.dumps(prep.POSES[scene] | {'preset': 'PERFORMANCE', 'debug': False, 'weather': 'clear', 'enabled': True}))
            manifest_path = profile.parent / 'workload-manifest.json'; manifest_path.write_text(json.dumps({'source_world_sha256': 'old', 'configuration_only': False}))
            if scene == 'forest': (profile / 'mods' / dh_name).write_bytes(b'qualified copied DH')
            records[scene] = {'scene': scene, 'profile': profile, 'world': world, 'manifest': {'source_world_sha256': 'old'},
                'control': prep.POSES[scene] | {'preset': 'PERFORMANCE', 'debug': False, 'weather': 'clear'},
                'removed_control_overrides': {'enabled': True},
                'original': {'path': str(original), 'sha256': prep.ORIGINAL_SHA256},
                'reduced': {'path': str(self.reduced), 'sha256': prep.REDUCED_SHA256},
                'native_options': {'QUALITY': 'high'}, 'patched_options': {'QUALITY': 'high'} | prep.PATCHED_FLAGS,
                'qualification': {'fixture': 'qualified pinned source is injected for copy tests'}, 'option_evidence': {'fixture': True},
                'source_files_sha256': {str(profile / 'options.txt'): file_hash(profile / 'options.txt')},
                'provenance_files': {'shaderlod-client-original.toml': profile / 'config/shaderlod-client.toml',
                                     'control-original.json': profile.parent / 'control.json'}}
        runtime_path = self.root / 'runtime.json'
        runtime_path.write_text(json.dumps({'artifacts': {'distanthorizons': {'filename': dh_name,
            'hashes': {'sha512': hashlib.sha512(b'qualified copied DH').hexdigest()}}}}))
        return records, runtime_path

    @contextmanager
    def fixture_io(self, records):
        def hash_file(path, algorithm='sha256'):
            payload = Path(path).read_bytes()
            if algorithm == 'sha256' and payload == b'synthetic-original': return prep.ORIGINAL_SHA256
            if algorithm == 'sha256' and payload == b'synthetic-reduced': return prep.REDUCED_SHA256
            return file_hash(path, algorithm)
        with mock.patch.object(prep, 'inspect_scene', side_effect=lambda item, *_: records[item['scene']]), \
                mock.patch.object(prep, 'require_exclusive_runtime'), mock.patch.object(prep, 'file_hash', side_effect=hash_file):
            yield

    def test_tiny_both_scene_copies_equal_no_dh_source_configs_preserved(self):
        records, runtime = self.fixture_records()
        before = {scene: world_fingerprint(record['world']) for scene, record in records.items()}
        with self.fixture_io(records): prep.prepare(self.output, prep.SCENES, self.sources, self.reduced, runtime)
        for scene in prep.SCENES:
            base = self.output / scene
            manifest = json.loads((base / 'workload-manifest.json').read_text())
            self.assertEqual(set(manifest['copied_world_sha256'].values()), {before[scene]})
            self.assertEqual(manifest['proof_profiles'], list(prep.ARMS)); self.assertFalse(manifest['dh_installed'])
            for arm in prep.ARMS:
                profile = base / arm
                self.assertEqual(world_fingerprint(profile / 'saves' / WORLD), before[scene])
                self.assertFalse(any((profile / 'mods').glob('*.jar')))
                for old in ('logs', 'screenshots', 'camera-quality', 'workload-measurement.json', 'benchmark.json', 'camera-quality.json'):
                    self.assertFalse((profile / old).exists())
            self.assertEqual((base / 'source-provenance/shaderlod-client-original.toml').read_text(), '[old]\nretained="source"\n')
            self.assertEqual((records[scene]['profile'] / 'config/shaderlod-client.toml').read_text(), '[old]\nretained="source"\n')
            self.assertEqual(world_fingerprint(records[scene]['world']), before[scene])
        self.assertTrue(any((self.sources['forest'] / 'mods').glob('*.jar')))
        with self.assertRaisesRegex(ValueError, 'Preserving'): prep.plan(self.output, prep.SCENES, self.sources, self.reduced)

    def test_world_lock_rejection_creates_no_output(self):
        records, runtime = self.fixture_records()
        @contextmanager
        def locked(_):
            raise ValueError('World is open')
            yield
        with self.fixture_io(records), mock.patch.object(prep, 'unlocked_world', locked):
            with self.assertRaisesRegex(ValueError, 'World is open'): prep.prepare(self.output, prep.SCENES, self.sources, self.reduced, runtime)
        self.assertFalse(self.output.exists())

    def test_failure_retained_without_deleting_unmanaged_mods_or_prior_output(self):
        records, runtime = self.fixture_records()
        (self.sources['coastalwater'] / 'mods/unmanaged.jar').write_bytes(b'do not silently remove')
        with self.fixture_io(records):
            with self.assertRaisesRegex(ValueError, 'unmanaged'): prep.prepare(self.output, prep.SCENES, self.sources, self.reduced, runtime)
        self.assertEqual(json.loads((self.output / 'reduced-effects-preparation.json').read_text())['status'], 'failed')
        self.assertTrue((self.sources['coastalwater'] / 'mods/unmanaged.jar').is_file())
        self.assertTrue((self.output / 'coastalwater/high/mods/unmanaged.jar').is_file())

    def test_native_options_normalize_exact_defaults_and_preserve_upstream_values(self):
        profile = self.root / 'profile'; (profile / 'config').mkdir(parents=True); (profile / 'shaderpacks').mkdir()
        (profile / 'config/iris.properties').write_text('shaderPack=original.zip\n')
        (profile / 'shaderpacks/original.zip.txt').write_text('QUALITY=high\n')
        report_path = profile / 'workload-measurement.json'
        report = {'status': 'complete', 'actual_shader_sha256': prep.ORIGINAL_SHA256,
                  'effective_shader_options': {'QUALITY': 'high'}, 'effective_shader_options_sha256': canonical_options_hash({'QUALITY': 'high'})}
        report_path.write_text(json.dumps(report))
        archive_data = {'path': 'fixture.zip', 'settings': '#define QUALITY high // [high low]\n', 'declarations': {}}
        defaults = [{'QUALITY': 'high'} | prep.PATCHED_FLAGS, {'QUALITY': 'high'}]
        with mock.patch.object(prep.zipfile, 'ZipFile'), mock.patch.object(prep, 'archive_defaults', side_effect=defaults):
            native, patched, evidence = prep.native_options(profile, archive_data, archive_data)
        self.assertEqual(native, {'QUALITY': 'high'}); self.assertEqual(patched['QUALITY'], 'high')
        self.assertTrue(all(patched[name] == value for name, value in prep.PATCHED_FLAGS.items()))
        (profile / 'shaderpacks/original.zip.txt').write_text('QUALITY=low\n')
        with self.assertRaisesRegex(ValueError, 'differs'): prep.native_options(profile, archive_data, archive_data)

    def test_native_options_reject_missing_report_and_undeclared_private_control(self):
        profile = self.root / 'profile'; (profile / 'config').mkdir(parents=True); (profile / 'shaderpacks').mkdir()
        (profile / 'config/iris.properties').write_text('shaderPack=original.zip\n')
        archive_data = {'path': 'fixture.zip', 'settings': '#define QUALITY high\n', 'declarations': {}}
        with self.assertRaisesRegex(ValueError, 'evidence missing'): prep.native_options(profile, archive_data, archive_data)
        report = {'status': 'complete', 'actual_shader_sha256': prep.ORIGINAL_SHA256,
                  'effective_shader_options': {'QUALITY': 'high'}, 'effective_shader_options_sha256': canonical_options_hash({'QUALITY': 'high'})}
        (profile / 'workload-measurement.json').write_text(json.dumps(report))
        with mock.patch.object(prep.zipfile, 'ZipFile'), mock.patch.object(prep, 'archive_defaults', side_effect=[{'QUALITY': 'high'}, {'QUALITY': 'high'}]):
            with self.assertRaisesRegex(ValueError, 'undeclared'): prep.native_options(profile, archive_data, archive_data)


if __name__ == '__main__': unittest.main()
