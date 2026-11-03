"""Original Complementary controls: no derivative, scene drift or parser broadening."""
import contextlib
import io
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import prepare_pack_experiment as preparation
from run_proof_matrix import (CASES, COMPLEMENTARY_RELEASE_SHA256, archive_defaults,
                              normalize_requested_options, profile_inputs,
                              read_option_properties)
from setup_comparison import WORLD, file_hash, world_fingerprint


class ComplementaryProfilesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        lock = json.loads((ROOT / 'dependencies/shader-candidates.lock.json').read_text())
        cls.pin = next(c for c in lock['candidates']
                       if c['modrinth_project'] == 'complementary-reimagined')['release']
        cls.archive = ROOT / 'dependencies/upstream/other-pack-research' / cls.pin['filename']

    def require_archive(self):
        if not self.archive.is_file():
            self.skipTest('Pinned ignored Complementary release is not downloaded')
        self.assertEqual(file_hash(self.archive), COMPLEMENTARY_RELEASE_SHA256)

    @contextlib.contextmanager
    def fixture(self):
        self.require_archive()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'dependencies/upstream/other-pack-research').mkdir(parents=True)
            shutil.copy2(self.archive, root / 'dependencies/upstream/other-pack-research' / self.pin['filename'])
            candidates = {'candidates': [{'modrinth_project': 'complementary-reimagined',
                                         'release': dict(self.pin)}]}
            (root / 'dependencies/shader-candidates.lock.json').write_text(json.dumps(candidates))
            runtime = json.loads((ROOT / 'dependencies/runtime.lock.json').read_text())
            (root / 'dependencies/runtime.lock.json').write_text(json.dumps(runtime))
            source = root / 'source/coastalwater/inspected'
            for folder in ('config', 'shaderpacks', 'mods', f'saves/{WORLD}/region'):
                (source / folder).mkdir(parents=True)
            (source / f'saves/{WORLD}/level.dat').write_bytes(b'retained shoreline world fixture')
            (source / f'saves/{WORLD}/region/r.0.0.mca').write_bytes(b'matched terrain fixture')
            (source / f'saves/{WORLD}/session.lock').write_bytes(b'fixture lock')
            (source / 'config/iris.properties').write_text('shaderPack=old.zip\nenableShaders=true\n')
            (source / 'config/shaderlod-client.toml').write_text('[lod]\nenabled=true\n[terrain]\nenabled=false\n')
            (source / 'shaderpacks/old.zip').write_bytes(b'stale source pack to replace')
            (source / 'shaderpacks/old.zip.txt').write_text('WATER_REFLECT_QUALITY=-1\n')
            (source / 'options.txt').write_text('renderDistance:24\nmaxFps:260\nenableVsync:false\n')
            dh = runtime['artifacts']['distanthorizons']['filename']
            (source / 'mods' / dh).write_bytes(b'source fixture DH must be absent in every arm')
            for name in ('workload-request.json', 'workload-measurement.json'):
                (source / name).write_text('{"stale":true}')
            control = {'x': 240.5, 'y': 68, 'z': 680.5, 'yaw': 180, 'pitch': 3,
                       'enabled': True, 'terrainEnabled': True, 'terrainChunkCap': 16,
                       'warmSourceConfirmed': True}
            (source.parent / 'control.json').write_text(json.dumps(control))
            (source.parent / 'workload-manifest.json').write_text(json.dumps({
                'scene': 'coastalwater', 'configuration_only': False,
                'source_world_sha256': world_fingerprint(source / 'saves' / WORLD)}))
            with patch.object(preparation, 'ROOT', root):
                yield root, source, runtime

    def test_exact_release_layout_preserves_native_controls_and_stream(self):
        self.require_archive()
        with zipfile.ZipFile(self.archive) as archive:
            before = archive.fp.tell()
            defaults = archive_defaults(archive)
            # Subsequent ZIP reads still work after exact container identification.
            self.assertTrue(archive.read('License.txt').startswith(b'Complementary License Agreement 1.7'))
            self.assertGreater(before, 0)
        expected = {'SHADER_STYLE': '1', 'RP_MODE': '1', 'WATER_REFLECT_QUALITY': '2',
                    'BLOCK_REFLECT_QUALITY': '3', 'COLORED_LIGHTING': '0',
                    'WORLD_SPACE_REFLECTIONS': '-1', 'DETAIL_QUALITY': '2',
                    'CLOUD_QUALITY': '2', 'shadowDistance': '192.0'}
        for name, value in expected.items():
            self.assertEqual(defaults[name], value, name)
        for value in ('0', '1', '2'):
            effective = normalize_requested_options(defaults, {'WATER_REFLECT_QUALITY': value})
            self.assertEqual(effective['WATER_REFLECT_QUALITY'], value)
            self.assertEqual(effective['BLOCK_REFLECT_QUALITY'], '3')

    def test_unknown_common_layout_is_rejected_not_generalized(self):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w') as archive:
            archive.writestr('shaders/lib/common.glsl', '#define WATER_REFLECT_QUALITY 2\n')
        with zipfile.ZipFile(stream) as archive:
            with self.assertRaisesRegex(ValueError, 'exact pinned r5.9.3'):
                archive_defaults(archive)

    def test_prior_option_layouts_still_take_their_original_path(self):
        for layout in ('shaders/settings.glsl', 'shaders/lib/config.glsl',
                       'shaders/shader.h', 'shaders/lib/settings.glsl'):
            with self.subTest(layout=layout):
                stream = io.BytesIO()
                with zipfile.ZipFile(stream, 'w') as archive:
                    archive.writestr(layout, '#define QUALITY 2 // [1 2]\n//#define FOG\n')
                    archive.writestr('shaders/lib/common.glsl', '#define QUALITY 0\n')
                with zipfile.ZipFile(stream) as archive:
                    defaults = archive_defaults(archive)
                self.assertEqual(defaults['QUALITY'], '2')
                self.assertEqual(defaults['FOG'], 'false')

    def test_preparation_copies_original_world_and_only_native_water_option(self):
        with self.fixture() as (root, source, runtime):
            before_world = world_fingerprint(source / 'saves' / WORLD)
            before_archive = file_hash(self.archive)
            output = root / 'prepared'
            preparation.prepare(source, output, 'complementary')
            scene = output / 'coastalwater'
            manifest = json.loads((scene / 'workload-manifest.json').read_text())
            self.assertEqual(manifest['experiment_family'], 'complementary')
            self.assertFalse(manifest['dh_installed'])
            self.assertEqual(manifest['source_world_sha256'], before_world)
            self.assertEqual(manifest['source_profile'], str(source.resolve()))
            self.assertEqual(set(manifest['experiment_arms']), {'high', 'water-sky', 'water-potato'})
            self.assertEqual(json.loads((scene / 'control.json').read_text()),
                             {'x': 240.5, 'y': 68, 'z': 680.5, 'yaw': 180, 'pitch': 3})
            for arm, value in (('high', '2'), ('water-sky', '0'), ('water-potato', '1')):
                with self.subTest(arm=arm):
                    profile = scene / arm
                    pack = profile / 'shaderpacks' / self.pin['filename']
                    self.assertEqual(pack.read_bytes(), self.archive.read_bytes())
                    self.assertEqual(file_hash(pack), COMPLEMENTARY_RELEASE_SHA256)
                    self.assertEqual(world_fingerprint(profile / 'saves' / WORLD), before_world)
                    self.assertFalse((profile / 'saves' / WORLD / 'session.lock').exists())
                    self.assertEqual((profile / 'options.txt').read_bytes(), (source / 'options.txt').read_bytes())
                    self.assertFalse((profile / 'mods' / runtime['artifacts']['distanthorizons']['filename']).exists())
                    self.assertFalse((profile / 'shaderpacks/old.zip').exists())
                    self.assertEqual(read_option_properties((profile / 'config/iris.properties').read_text())['shaderPack'], self.pin['filename'])
                    self.assertEqual(read_option_properties((pack.parent / (pack.name + '.txt')).read_text()),
                                     {'WATER_REFLECT_QUALITY': value})
                    self.assertEqual(manifest['experiment_arms'][arm], {
                        'archive': self.pin['filename'], 'sha256': COMPLEMENTARY_RELEASE_SHA256,
                        'requested_options': {'WATER_REFLECT_QUALITY': value}})
                    for stale in ('workload-request.json', 'workload-measurement.json'):
                        self.assertFalse((profile / stale).exists())
                    inputs = profile_inputs(profile)
                    self.assertEqual(inputs['shader_sha256'], COMPLEMENTARY_RELEASE_SHA256)
                    self.assertEqual(inputs['requested_shader_options'], {'WATER_REFLECT_QUALITY': value})
            self.assertEqual(world_fingerprint(source / 'saves' / WORLD), before_world)
            self.assertEqual(file_hash(self.archive), before_archive)

    def test_default_option_file_removal_preserves_prepared_high_identity(self):
        with self.fixture() as (root, source, runtime):
            output = root / 'prepared'
            preparation.prepare(source, output, 'complementary')
            profile = output / 'coastalwater/high'
            before = profile_inputs(profile)
            (profile / 'shaderpacks' / (self.pin['filename'] + '.txt')).unlink()
            after = profile_inputs(profile)
            self.assertEqual(before['semantic_shader_options_sha256'], after['semantic_shader_options_sha256'])
            self.assertEqual(after['requested_shader_options'], {'WATER_REFLECT_QUALITY': '2'})
            sky = output / 'coastalwater/water-sky'
            (sky / 'shaderpacks' / (self.pin['filename'] + '.txt')).unlink()
            with self.assertRaisesRegex(ValueError, 'Stored options differ'):
                profile_inputs(sky)

    def test_rejects_wrong_scene_dh_changed_pin_or_archive_before_output(self):
        with self.fixture() as (root, source, runtime):
            output = root / 'prepared'
            with self.assertRaisesRegex(ValueError, 'matching inspected scene'):
                preparation.prepare(source.parent.parent / 'forest/high', output, 'complementary')
            with self.assertRaisesRegex(ValueError, 'DH compatibility override'):
                preparation.prepare(source, output, 'complementary', with_dh=True)
            lock_path = root / 'dependencies/shader-candidates.lock.json'
            lock = json.loads(lock_path.read_text())
            lock['candidates'][0]['release']['version'] = 'future'
            lock_path.write_text(json.dumps(lock))
            with self.assertRaisesRegex(ValueError, 'exact r5.9.3 release pin'):
                preparation.prepare(source, output, 'complementary')
            lock['candidates'][0]['release'] = dict(self.pin)
            lock_path.write_text(json.dumps(lock))
            pack = root / 'dependencies/upstream/other-pack-research' / self.pin['filename']
            pack.write_bytes(pack.read_bytes() + b'changed container')
            with self.assertRaisesRegex(ValueError, 'release differs from pin'):
                preparation.prepare(source, output, 'complementary')
            self.assertFalse(output.exists())

    def test_matrix_cases_are_original_mod_absent_and_prior_cases_unchanged(self):
        expected = {'complementary-high': 'high', 'complementary-water-sky': 'water-sky',
                    'complementary-water-potato': 'water-potato'}
        for case, profile in expected.items():
            self.assertEqual(CASES[case], ('coastalwater/' + profile, 'runClientLodOff'))
        for case, profile, task in (
                ('bsl-high', 'high', 'runClientLodOff'),
                ('bsl-parallax-off', 'parallax-off', 'runClientLodOff'),
                ('makeup-high', 'high', 'runClientLodOff'),
                ('makeup-on', 'adapter-on', 'runClientLodOff')):
            self.assertEqual(CASES[case], ('coastalwater/' + profile, task))


if __name__ == '__main__':
    unittest.main()
