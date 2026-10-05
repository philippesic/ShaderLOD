"""Tiny synthetic archives/worlds and pure geometry guards; no client/build work."""
from contextlib import contextmanager, closing
import copy
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import sys
import tempfile
import tomllib
import unittest
from unittest import mock
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import prepare_native_terrain_profiles as terrain
from run_proof_matrix import validate_report, canonical_options_hash
from setup_comparison import WORLD, world_fingerprint, file_hash, unlocked_world


CONFIG = '''[lod]
enabled = false
preset = "PERFORMANCE"
near = 32.0
far = 96.0
transition = 8.0
debug = false
[terrain]
enabled = false
vanillaChunkCap = 16
warmSourceConfirmed = false
warmupSeconds = 30
[other]
keep = "retained"
'''
CONTROL = {'x': 256.5, 'y': 95.0, 'z': 768.5, 'yaw': 135.0, 'pitch': 9.0,
           'time': 7000, 'weather': 'clear', 'preset': 'PERFORMANCE', 'debug': False}
DH_CONFIG = '[client.advanced.graphics.quality]\nlodChunkRenderDistanceRadius = 128\nhorizontalQuality = "MEDIUM"\n'


class NativeTerrainPreparationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(); self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / 'source'; self.output = self.root / 'output'
        self.runtime_check = mock.patch.object(terrain, 'require_exclusive_runtime')
        self.runtime_mock = self.runtime_check.start(); self.addCleanup(self.runtime_check.stop)

    def fixture(self):
        base = self.source / 'forest'; base.mkdir(parents=True)
        original_path = self.root / 'original.zip'
        settings = '#define QUALITY high // [high low]\n'
        with zipfile.ZipFile(original_path, 'w') as archive:
            archive.writestr('LICENSE', b'pinned synthetic fixture license')
            archive.writestr('shaders/settings.glsl', settings)
        original_hash = hashlib.sha256(original_path.read_bytes()).hexdigest()
        original_sha512 = hashlib.sha512(original_path.read_bytes()).hexdigest()
        flags = dict.fromkeys(terrain.EFFECT_OPTIONS, 'true') | {'SHADERLOD_PRESERVE_LIGHTING': 'true',
            'SHADERLOD_USE_MOD_CONFIG': 'true', 'SHADERLOD_CACHED_SKY': 'false',
            'SHADERLOD_WATER_ANALYTIC': 'false', 'SHADERLOD_DEBUG': 'false'}
        candidate = self.root / 'candidate.zip'
        candidate_settings = settings + ''.join(('' if value == 'true' else '//') + '#define ' + name + '\n' for name, value in flags.items())
        with zipfile.ZipFile(candidate, 'w') as archive:
            archive.writestr('LICENSE', b'pinned synthetic fixture license')
            archive.writestr('shaders/settings.glsl', candidate_settings)
            archive.writestr('shaders/program/d4_deferred_shading.fsh', 'void main() {}\n')
            archive.writestr('shaderlod-build.json', json.dumps({'base_version': 'v1.3b', 'base_sha512': original_sha512, 'bridge_version': 2}))
        candidate_hash = hashlib.sha256(candidate.read_bytes()).hexdigest()
        pins = {'high': original_hash, 'patched-off': candidate_hash, 'adaptive': candidate_hash}
        self.pin_patch = mock.patch.multiple(terrain, SOURCE_PINS=pins, B15_SHA256=candidate_hash)
        self.pin_patch.start(); self.addCleanup(self.pin_patch.stop)
        lock = {'artifacts': {'photon': {'version': 'v1.3b', 'hashes': {'sha512': original_sha512}},
                              'distanthorizons': {'filename': 'dh-fixture.jar', 'hashes': {'sha512': hashlib.sha512(b'dh-fixture').hexdigest()}}}}
        self.lock_path = self.root / 'runtime.lock.json'; self.lock_path.write_text(json.dumps(lock))
        for arm in terrain.SOURCE_ARMS:
            profile = base / arm
            for child in ('config', 'shaderpacks', 'mods', 'screenshots', 'logs', 'saves/' + WORLD):
                (profile / child).mkdir(parents=True)
            world = profile / 'saves' / WORLD
            (world / 'data').mkdir()
            (world / 'level.dat').write_text('prior-' + arm)
            (world / 'session.lock').write_text('fixture lock')
            with closing(sqlite3.connect(world / 'data/DistantHorizons.sqlite')) as db:
                with db:
                    db.execute('CREATE TABLE FullData (DetailLevel INT, PosX INT, PosZ INT)')
                    db.execute('INSERT INTO FullData VALUES (0,0,0)')
            selected = 'original.zip' if arm == 'high' else 'candidate.zip'
            shutil.copy2(original_path if arm == 'high' else candidate, profile / 'shaderpacks' / selected)
            options = {'QUALITY': 'high'} if arm == 'high' else {'QUALITY': 'high'} | flags
            (profile / 'shaderpacks' / (selected + '.txt')).write_text(''.join(name + '=' + value + '\n' for name, value in options.items()))
            (profile / 'config/iris.properties').write_text('enableShaders=true\nshaderPack=' + selected + '\n')
            (profile / 'config/shaderlod-client.toml').write_text(CONFIG.replace('enabled = false', 'enabled = true', 1) if arm == 'adaptive' else CONFIG)
            (profile / 'config/DistantHorizons.toml').write_text(DH_CONFIG)
            (profile / 'options.txt').write_text('renderDistance:24\nresourcePacks:[]\n')
            (profile / 'mods/dh-fixture.jar').write_bytes(b'dh-fixture')
            (profile / 'screenshots/old.png').write_text('old'); (profile / 'logs/old.log').write_text('old')
            (profile / 'benchmark.json').write_text('old')
            (profile / 'workload-request.json').write_text('old')
            report = {'status': 'complete', 'actual_shader_sha256': pins[arm], 'effective_shader_options': options,
                      'effective_shader_options_sha256': canonical_options_hash(options)}
            (profile / 'workload-measurement.json').write_text(json.dumps(report))
        (base / 'control.json').write_text(json.dumps(CONTROL | {'enabled': False, 'terrainEnabled': False, 'terrainChunkCap': 8, 'warmSourceConfirmed': False}))
        (base / 'workload-manifest.json').write_text(json.dumps({'configuration_only': False,
            'source_world_sha256': 'old-provenance-is-retained-only', 'adaptive_pack_sha256': candidate_hash,
            'proof_profiles': list(terrain.SOURCE_ARMS)}))

    def prepare(self):
        return terrain.prepare(self.source, self.output, True, self.lock_path)

    def test_pure_plan_fixed_pins_arms_and_geometry_from_adaptive(self):
        planned = terrain.plan(self.source, self.output, True)
        self.assertEqual(planned['archive_pins']['geometry'], 'b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c')
        self.assertEqual(planned['profile_sources']['geometry'], 'forest/adaptive')
        self.assertEqual(planned['cases'], ['forest-high', 'forest-patched-off', 'forest-adaptive', 'forest-geometry'])
        self.assertEqual((planned['requested_chunks'], planned['geometry_effective_chunks'], planned['dh_chunks']), (24, 16, 128))
        self.assertFalse(self.output.exists())
        self.runtime_mock.assert_not_called()

    def test_explicit_acknowledgement_collision_and_overlap_required(self):
        with self.assertRaisesRegex(ValueError, 'acknowledge'): terrain.plan(self.source, self.output)
        for target in (self.source, self.source / 'nested', self.root):
            with self.assertRaises(ValueError): terrain.plan(self.source, target, True)
        self.output.mkdir()
        with self.assertRaisesRegex(ValueError, 'Preserving'): terrain.plan(self.source, self.output, True)

    def test_tiny_fixture_restores_one_world_and_geometry_exact_adaptive_pack(self):
        self.fixture()
        original_worlds = {arm: world_fingerprint(self.source / 'forest' / arm / 'saves' / WORLD) for arm in terrain.SOURCE_ARMS}
        self.assertEqual(len(set(original_worlds.values())), 3)
        self.prepare()
        base = self.output / 'forest'
        manifest = json.loads((base / 'workload-manifest.json').read_text())
        for arm in terrain.ARMS:
            self.assertEqual(world_fingerprint(base / arm / 'saves' / WORLD), original_worlds['high'])
            self.assertEqual((base / arm / 'saves' / WORLD / 'level.dat').read_text(), 'prior-high')
            self.assertFalse((base / arm / 'logs').exists())
            self.assertFalse((base / arm / 'workload-request.json').exists())
            self.assertFalse((base / arm / 'workload-measurement.json').exists())
            self.assertFalse((base / arm / 'benchmark.json').exists())
            config = tomllib.loads((base / arm / 'config/shaderlod-client.toml').read_text())
            self.assertIs(config['terrain']['enabled'], arm == 'geometry')
            self.assertIs(config['terrain']['warmSourceConfirmed'], arm == 'geometry')
            self.assertEqual(config['terrain']['vanillaChunkCap'], 16)
            self.assertEqual(config['terrain']['warmupSeconds'], 30)
            self.assertEqual(config['other']['keep'], 'retained')
            self.assertTrue(all(config['rendering'][name] is False for name in terrain.RENDERING_FIELDS))
        for child in ('candidate.zip', 'candidate.zip.txt'):
            self.assertEqual((base / 'adaptive/shaderpacks' / child).read_bytes(), (base / 'geometry/shaderpacks' / child).read_bytes())
        self.assertEqual(manifest['experiment_arms']['geometry']['source_profile'], str((base / 'adaptive').resolve()))
        self.assertEqual(manifest['source_world_sha256'], original_worlds['high'])
        self.assertEqual(set(manifest['copied_world_sha256'].values()), {original_worlds['high']})
        self.assertFalse(manifest['shader_only'])
        self.assertEqual(json.loads((base / 'control.json').read_text()), CONTROL)
        self.assertEqual({arm: world_fingerprint(self.source / 'forest' / arm / 'saves' / WORLD) for arm in terrain.SOURCE_ARMS}, original_worlds)
        with self.assertRaisesRegex(ValueError, 'Preserving'): self.prepare()

    def test_source_pin_unknown_archive_rejected_before_output(self):
        self.fixture()
        (self.source / 'forest/adaptive/shaderpacks/candidate.zip').write_bytes(b'unknown')
        with self.assertRaisesRegex(ValueError, 'checksum'): self.prepare()
        self.assertFalse(self.output.exists())

    def test_source_policy_preset_and_cpu_experiments_rejected(self):
        for old, new in (('vanillaChunkCap = 16', 'vanillaChunkCap = 8'),
                         ('warmSourceConfirmed = false', 'warmSourceConfirmed = true'),
                         ('preset = "PERFORMANCE"', 'preset = "BALANCED"')):
            with self.assertRaises(ValueError): terrain.source_config(CONFIG.replace(old, new), 'patched-off')
        with self.assertRaises(ValueError): terrain.source_config(CONFIG + '[rendering]\noptimizeShadowCulling=true\n', 'patched-off')

    def test_source_options_changed_or_no_longer_allactive_rejected(self):
        self.fixture()
        profile = self.source / 'forest/adaptive'
        path = profile / 'shaderpacks/candidate.zip.txt'
        original = path.read_text(); path.write_text(original.replace('SHADERLOD_CLOUDS=true', 'SHADERLOD_CLOUDS=false'))
        with self.assertRaisesRegex(ValueError, 'five-active'): self.prepare()
        path.write_text(original.replace('QUALITY=high', 'QUALITY=low'))
        with self.assertRaisesRegex(ValueError, 'changed since'): self.prepare()
        self.assertFalse(self.output.exists())

    def test_dh_distance_or_unmanaged_optimizer_profile_jar_rejected(self):
        self.fixture()
        path = self.source / 'forest/adaptive/config/DistantHorizons.toml'
        original = path.read_text(); path.write_text(original.replace('128', '64'))
        with self.assertRaisesRegex(ValueError, 'DH radius'): self.prepare()
        path.write_text(original)
        (self.source / 'forest/adaptive/mods/unmanaged.jar').write_text('unknown')
        with self.assertRaisesRegex(ValueError, 'pinned DH'): self.prepare()

    def test_canonical_world_lock_failure_retains_source_and_no_output(self):
        self.fixture()
        @contextmanager
        def locked(_):
            raise ValueError('World is open')
            yield
        with mock.patch.object(terrain, 'unlocked_world', locked):
            with self.assertRaisesRegex(ValueError, 'World is open'): self.prepare()
        self.assertFalse(self.output.exists())

    def test_failed_copy_preserved_as_failed_preparation(self):
        self.fixture()
        original = terrain.shutil.copytree
        def damaged(source, target, *args, **kwargs):
            result = original(source, target, *args, **kwargs)
            if Path(target).name == 'geometry': (Path(target) / 'saves' / WORLD / 'level.dat').write_text('damaged')
            return result
        with mock.patch.object(terrain.shutil, 'copytree', damaged):
            with self.assertRaisesRegex(ValueError, 'copied world differs'): self.prepare()
        manifest = json.loads((self.output / 'native-terrain-preparation.json').read_text())
        self.assertEqual(manifest['status'], 'failed')
        self.assertTrue((self.output / 'forest/geometry').exists())

    def test_config_append_renderer_defaults_preserves_following_section(self):
        text = CONFIG.replace('[other]', '[rendering]\n[other]')
        config = tomllib.loads(terrain.output_config(text, 'geometry'))
        self.assertEqual(config['other'], {'keep': 'retained'})
        self.assertEqual(config['rendering'], dict.fromkeys(terrain.RENDERING_FIELDS, False))
        self.assertIs(config['lod']['enabled'], True)

    def test_missing_or_empty_canonical_dh_cache_cannot_be_acknowledged_by_preparer(self):
        self.fixture()
        database = self.source / 'forest/high/saves' / WORLD / 'data/DistantHorizons.sqlite'
        with closing(sqlite3.connect(database)) as connection:
            with connection: connection.execute('DELETE FROM FullData')
        with self.assertRaisesRegex(ValueError, 'database is empty'): self.prepare()
        self.assertFalse(self.output.exists())
        database.unlink()
        with self.assertRaisesRegex(ValueError, 'database missing'): self.prepare()

    def test_source_dh_quality_change_and_archive_metadata_mismatch_rejected(self):
        self.fixture()
        path = self.source / 'forest/adaptive/config/DistantHorizons.toml'
        path.write_text(DH_CONFIG.replace('MEDIUM', 'LOW'))
        with self.assertRaisesRegex(ValueError, 'DH quality'): self.prepare()
        path.write_text(DH_CONFIG)
        manifest_path = self.source / 'forest/workload-manifest.json'
        manifest = json.loads(manifest_path.read_text()); manifest['adaptive_pack_sha256'] = '0' * 64
        manifest_path.write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, 'manifest is not frozen B15'): self.prepare()

    def test_closed_wal_mode_source_stays_byte_identical_through_preparation(self):
        self.fixture()
        world = self.source / 'forest/high/saves' / WORLD
        database = world / 'data/DistantHorizons.sqlite'
        with closing(sqlite3.connect(database)) as connection:
            self.assertEqual(connection.execute('PRAGMA journal_mode=WAL').fetchone()[0], 'wal')
        self.assertFalse(database.with_name(database.name + '-wal').exists())
        self.assertFalse(database.with_name(database.name + '-shm').exists())
        before = {p.relative_to(world).as_posix(): file_hash(p) for p in world.rglob('*') if p.is_file()}
        fingerprint = world_fingerprint(world)
        self.prepare()
        self.assertEqual({p.relative_to(world).as_posix(): file_hash(p) for p in world.rglob('*') if p.is_file()}, before)
        manifest = json.loads((self.output / 'forest/workload-manifest.json').read_text())
        self.assertEqual(set(manifest['copied_world_sha256'].values()), {fingerprint})


class WalMetadataSnapshotTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(); self.addCleanup(self.temporary.cleanup)
        self.world = Path(self.temporary.name) / 'world'
        (self.world / 'data').mkdir(parents=True)
        (self.world / 'level.dat').write_bytes(b'tiny stopped-world fixture')
        (self.world / 'session.lock').write_bytes(b'fixture world lock')
        self.database = self.world / 'data/DistantHorizons.sqlite'

    def connection(self):
        connection = sqlite3.connect(self.database)
        self.addCleanup(connection.close)
        self.assertEqual(connection.execute('PRAGMA journal_mode=WAL').fetchone()[0], 'wal')
        connection.execute('PRAGMA wal_autocheckpoint=0')
        with connection:
            connection.execute('CREATE TABLE FullData (DetailLevel INT, PosX INT, PosZ INT)')
            connection.execute('INSERT INTO FullData VALUES (0,1,2)')
        self.assertEqual(connection.execute('PRAGMA wal_checkpoint(TRUNCATE)').fetchone()[0], 0)
        return connection

    def source_files(self):
        return {p.relative_to(self.world).as_posix(): file_hash(p) for p in self.world.rglob('*') if p.is_file()}

    def read_preserving_source(self):
        before, fingerprint = self.source_files(), world_fingerprint(self.world)
        with unlocked_world(self.world):
            result = terrain.cached_dh_metadata(self.world)
        self.assertEqual(self.source_files(), before)
        self.assertEqual(world_fingerprint(self.world), fingerprint)
        return result

    def test_no_sidecars_closed_wal_database_metadata_read_never_opens_source(self):
        connection = self.connection(); connection.close()
        self.assertFalse(self.database.with_name(self.database.name + '-wal').exists())
        self.assertFalse(self.database.with_name(self.database.name + '-shm').exists())
        result = self.read_preserving_source()
        self.assertEqual(result, [{'detail': 0, 'sections': 1, 'min_x': 1, 'max_x': 1, 'min_z': 2, 'max_z': 2}])
        self.assertFalse(self.database.with_name(self.database.name + '-wal').exists())
        self.assertFalse(self.database.with_name(self.database.name + '-shm').exists())

    def test_nonempty_wal_latest_committed_rows_are_visible_and_source_is_unchanged(self):
        connection = self.connection()
        database_hash = file_hash(self.database)
        with connection:
            connection.execute('INSERT INTO FullData VALUES (0,11,-3)')
            connection.execute('INSERT INTO FullData VALUES (1,8,12)')
        wal = self.database.with_name(self.database.name + '-wal')
        self.assertGreater(wal.stat().st_size, 32)
        self.assertEqual(file_hash(self.database), database_hash)  # Latest rows exist only in the WAL.
        result = self.read_preserving_source()
        self.assertEqual(result, [{'detail': 0, 'sections': 2, 'min_x': 1, 'max_x': 11, 'min_z': -3, 'max_z': 2},
                                  {'detail': 1, 'sections': 1, 'min_x': 8, 'max_x': 8, 'min_z': 12, 'max_z': 12}])
        self.assertGreater(wal.stat().st_size, 32)

    def test_zero_length_wal_and_existing_shm_are_preserved(self):
        self.connection()
        wal = self.database.with_name(self.database.name + '-wal')
        shm = self.database.with_name(self.database.name + '-shm')
        self.assertEqual(wal.stat().st_size, 0)
        self.assertTrue(shm.is_file())
        self.assertEqual(self.read_preserving_source()[0]['sections'], 1)
        self.assertEqual(wal.stat().st_size, 0)

    def test_private_snapshot_removed_on_query_failure_without_source_changes(self):
        connection = self.connection()
        with connection: connection.execute('DROP TABLE FullData')
        before = self.source_files()
        real_temporary_directory = terrain.tempfile.TemporaryDirectory
        snapshots = []
        def tracked(*args, **kwargs):
            temporary = real_temporary_directory(*args, **kwargs)
            snapshots.append(Path(temporary.name))
            return temporary
        with mock.patch.object(terrain.tempfile, 'TemporaryDirectory', tracked):
            with self.assertRaises(sqlite3.OperationalError): terrain.cached_dh_metadata(self.world)
        self.assertEqual(self.source_files(), before)
        self.assertEqual(len(snapshots), 1)
        self.assertFalse(snapshots[0].exists())


class GeometryAcceptanceTests(unittest.TestCase):
    def fixture(self, case='forest-geometry'):
        options = dict.fromkeys(terrain.EFFECT_OPTIONS, 'true') | {'SHADERLOD_PRESERVE_LIGHTING': 'true',
            'SHADERLOD_USE_MOD_CONFIG': 'true', 'SHADERLOD_CACHED_SKY': 'false', 'SHADERLOD_WATER_ANALYTIC': 'false', 'SHADERLOD_DEBUG': 'false'}
        state = {'shaders_in_use': True, 'fallback': False, 'framebuffer_width': 2976, 'framebuffer_height': 1672,
                 'shader_pack': 'B15.zip', 'lod_settings': {'enabled': True}, 'terrain_active': True,
                 'requested_chunks': 24, 'effective_chunks': 16}
        request = {'run_id': 'fixture', 'case': case, 'task': 'runClientLodOn', 'include_dh': True,
                   'measurement_seconds': 30, 'gpu_queries': True}
        inputs = {'shader_pack': 'B15.zip', 'shader_sha256': terrain.B15_SHA256, 'control_sha256': 'fixture',
                  'requested_shader_options': options, 'shader_option_defaults': options, 'control': {}}
        report = {'status': 'complete', 'request': {'run_id': 'fixture'}, 'modPresent': True, 'dhPresent': True,
                  'actual_shader_sha256': terrain.B15_SHA256, 'control_sha256': 'fixture',
                  'effective_shader_options': options, 'effective_shader_options_sha256': canonical_options_hash(options),
                  'input_state_start': state, 'input_state_end': copy.deepcopy(state),
                  'geometry_policy': {'active': True, 'requestedChunks': 24, 'effectiveChunks': 16, 'cap': 16,
                                      'stableTicks': 700, 'warmupTicks': 600, 'reason': 'DH terrain cap active'},
                  'actual_dh_configuration': DH_CONFIG, 'shadow_culling_active': False,
                  'separate_shadow_lists_active': False, 'shadow_frustum_coherence_active': False,
                  'elapsed_seconds': 30, 'render_frames': 100, 'cpu_samples': [1],
                  'gpu_samples': [{'scope': 'game-render'}], 'loaded_mods': {'iris': '1.8.12'}}
        return report, request, inputs

    def test_active_warmed_requested24_effective16_dh128_accepted(self):
        validate_report(*self.fixture(), 0)

    def test_geometry_boundary_radius_active_and_nonzero_policy_evidence_required(self):
        for boundary in ('input_state_start', 'input_state_end'):
            for name, value in (('terrain_active', False), ('terrain_active', 1), ('effective_chunks', 24),
                                ('effective_chunks', 0), ('effective_chunks', 16.0), ('requested_chunks', 16)):
                report, request, inputs = self.fixture(); report[boundary][name] = value
                with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)
        for modify in (lambda r: r.pop('geometry_policy'), lambda r: r.update(geometry_policy={}),
                       lambda r: r['geometry_policy'].update(active=False), lambda r: r['geometry_policy'].update(active=1),
                       lambda r: r['geometry_policy'].update(cap=8), lambda r: r['geometry_policy'].update(cap=0),
                       lambda r: r['geometry_policy'].update(requestedChunks=16), lambda r: r['geometry_policy'].update(effectiveChunks=24),
                       lambda r: r['geometry_policy'].update(warmupTicks=0), lambda r: r['geometry_policy'].update(stableTicks=599),
                       lambda r: r['geometry_policy'].update(reason='warming DH scene')):
            report, request, inputs = self.fixture(); modify(report)
            with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)

    def test_geometry_rejects_dh_absence_changed_dh_and_cpu_experiments(self):
        for modify in (lambda r: r.update(dhPresent=False), lambda r: r.pop('actual_dh_configuration'),
                       lambda r: r.update(actual_dh_configuration=DH_CONFIG.replace('128', '64')),
                       lambda r: r.update(separate_shadow_lists_active=True), lambda r: r.update(shadow_frustum_coherence_active=True)):
            report, request, inputs = self.fixture(); modify(report)
            with self.assertRaises(ValueError): validate_report(report, request, inputs, 0)
        report, request, inputs = self.fixture(); inputs['control']['terrainEnabled'] = False
        with self.assertRaisesRegex(ValueError, 'overrides'): validate_report(report, request, inputs, 0)

    def test_non_geometry_restored24_does_not_require_geometry_policy(self):
        report, request, inputs = self.fixture('forest-adaptive')
        report.pop('geometry_policy'); report.pop('actual_dh_configuration')
        for name in ('input_state_start', 'input_state_end'):
            report[name]['effective_chunks'] = 24; report[name]['terrain_active'] = False
        validate_report(report, request, inputs, 0)
        request['case'] = 'forest-patched-off'
        for name in ('input_state_start', 'input_state_end'): report[name]['lod_settings']['enabled'] = False
        validate_report(report, request, inputs, 0)


if __name__ == '__main__':
    unittest.main()
