"""AO preparation contract fixtures; no actual profile preparation or client."""
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import tomllib
import unittest
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import prepare_photon_ao_reuse_profiles as prep
from build_photon_ao_reuse_probe import BASE
from run_proof_matrix import CASES, canonical_options_hash
from setup_comparison import WORLD, file_hash, world_fingerprint


class PreparationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve(); self.output = self.root / 'run-workloads/new'
        self.sources = {scene: self.root / 'sources' / scene / 'high' for scene in prep.SCENES}
        self.probe = self.root / 'probe.zip'

    def test_pure_plan_ignored_fresh_outputs_and_existing_runner_arms(self):
        result = prep.plan(self.output, sources=self.sources, probe=self.probe, root=self.root)
        self.assertFalse(self.output.exists()); self.assertEqual(result['framebuffer'], [2976, 1672])
        self.assertFalse(result['dh_present']); self.assertEqual(result['original_sha256'], prep.BASE_SHA256)
        for prefix, scene in [('forest', 'forest'), ('water', 'coastalwater')]:
            for arm in prep.ARMS:
                self.assertEqual(CASES[prefix + '-' + arm], (scene + '/' + arm, 'runClientLodOff' if arm == 'high' else 'runClientLodOn'))
        with self.assertRaisesRegex(ValueError, 'ignored'): prep.plan(self.root / 'tracked', root=self.root)
        for scenes in [(), ('forest', 'forest'), ('unknown',)]:
            with self.assertRaises(ValueError): prep.plan(self.output, scenes, self.sources, self.probe, self.root)
        self.output.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, 'Preserving'): prep.plan(self.output, root=self.root)

    def test_default_off_gate_override_equal_and_only_global_config_differs(self):
        self.assertEqual(prep.FLAGS, {prep.SWITCH: 'true'})
        disabled, enabled = [tomllib.loads(prep.fresh_config(arm)) for arm in ('patched-off', 'adaptive')]
        self.assertFalse(disabled['lod']['enabled']); self.assertTrue(enabled['lod']['enabled'])
        enabled['lod']['enabled'] = False
        self.assertEqual(disabled, enabled)
        self.assertFalse(enabled['terrain']['enabled']); self.assertTrue(all(value is False for value in enabled['rendering'].values()))

    def test_private_archive_complete_diff_and_metadata_against_real_reviewed_source(self):
        original_path = ROOT / BASE
        if not original_path.exists(): self.skipTest('Pinned local Photon source absent')
        runtime = json.loads((ROOT / 'dependencies/runtime.lock.json').read_text())
        manifest_bytes = (ROOT / prep.MANIFEST).read_bytes(); manifest = json.loads(manifest_bytes)
        original = prep.archive_members(original_path)
        patched = prep.patch_members(original, manifest, 'disabled')
        changed = sorted(prep.MEMBERS)
        metadata = dict(experiment=prep.EXPERIMENT, arm='disabled', base_version='v1.3b', base_sha256=prep.BASE_SHA256,
            manifest_sha256=prep.sha256(manifest_bytes), switch=prep.SWITCH, switch_enabled=False,
            modified_upstream_files=changed, member_sha256={n: prep.sha256(patched[n]) for n in prep.MEMBERS},
            license_sha256=prep.sha256(original['LICENSE']), unchanged_upstream_member_count=len(original)-len(changed),
            consumed_core_fields=[], activation='compile option on AND bridge version 2 AND global enabled exactly 1',
            runtime_compilation='unverified', gpu_benefit='unmeasured')
        def write_archive(files, meta):
            with zipfile.ZipFile(self.probe, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
                for name, content in files.items(): archive.writestr(name, content)
                archive.writestr(prep.METADATA, json.dumps(meta))
        write_archive(patched, metadata)
        original_info, probe, qualification = prep.qualify_archives(original_path, self.probe, runtime)
        self.assertEqual(probe['sha256'], file_hash(self.probe)); self.assertTrue(qualification['qualified'])
        self.assertEqual(qualification['option_override_consumed_core_fields'], ['shaderlodBridgeVersion', 'shaderlodEnabled'])
        # Read inspected sources without locking/copying/mutating their worlds.
        for scene, source in prep.DEFAULT_SOURCES.items():
            if (source / 'workload-measurement.json').exists():
                native, options, _ = prep.native_options(source, original_info, probe)
                self.assertEqual(options, native | prep.FLAGS, scene)
        tampered = patched | {'shaders/readme.txt': b'unexpected member'}
        write_archive(tampered, metadata)
        with self.assertRaisesRegex(ValueError, 'inventory'): prep.qualify_archives(original_path, self.probe, runtime)
        member = next(n for n in patched if n not in prep.MEMBERS and n != 'LICENSE')
        tampered = patched | {member: patched[member] + b'changed'}
        write_archive(tampered, metadata)
        with self.assertRaisesRegex(ValueError, 'scope'): prep.qualify_archives(original_path, self.probe, runtime)
        write_archive(patched, metadata | {'manifest_sha256': '0'*64})
        with self.assertRaisesRegex(ValueError, 'metadata'): prep.qualify_archives(original_path, self.probe, runtime)
        write_archive(patched | {prep.MEMBERS[0]: patched[prep.MEMBERS[0]] + b'changed'}, metadata)
        with self.assertRaisesRegex(ValueError, 'reviewed patch'): prep.qualify_archives(original_path, self.probe, runtime)

    def option_fixture(self):
        profile = self.root / 'option-source'; (profile / 'config').mkdir(parents=True); (profile / 'shaderpacks').mkdir()
        (profile / 'config/iris.properties').write_text('shaderPack=original.zip\n')
        effective = dict(SHADER_AO='SHADER_AO_GTAO', GTAO_HORIZON_STEPS='3', GTAO_SLICES='2', GTAO_RADIUS='2.0')
        state = dict(framebuffer_width=2976, framebuffer_height=1672, requested_chunks=24,
                     effective_chunks=24, shaders_in_use=True, fallback=False)
        report = dict(status='complete', actual_shader_sha256=prep.BASE_SHA256, effective_shader_options=effective,
                      effective_shader_options_sha256=canonical_options_hash(effective), input_state_start=state, input_state_end=state)
        path = profile / 'workload-measurement.json'; path.write_text(json.dumps(report))
        original = dict(path='original.zip', settings=''.join(f'#define {k} {v}\n' for k,v in effective.items()), declarations={})
        return profile, effective, report, path, original

    def test_inspected_native_options_and_ao_budgets_remain_equal(self):
        profile, effective, report, path, original = self.option_fixture()
        with mock.patch.object(prep.zipfile, 'ZipFile') as archive, mock.patch.object(prep, 'archive_defaults', side_effect=[effective | {prep.SWITCH:'false'}, effective]):
            archive.return_value.__enter__.return_value.read.return_value = original['settings'].encode()
            native, patched, _ = prep.native_options(profile, original, {'path': 'probe.zip'})
        self.assertEqual(native, effective); self.assertEqual(patched, effective | prep.FLAGS)
        report['input_state_end']['framebuffer_width'] = 1488; path.write_text(json.dumps(report))
        with self.assertRaisesRegex(ValueError, 'native'): prep.native_options(profile, original, {'path': 'probe.zip'})

    def test_ao_disabled_or_budget_change_rejects(self):
        for key, value in [('SHADER_AO','SHADER_AO_NONE'), ('GTAO_HORIZON_STEPS','2')]:
            profile, effective, report, path, original = self.option_fixture()
            effective[key] = value; report['effective_shader_options_sha256'] = canonical_options_hash(effective); path.write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError, 'GTAO'): prep.native_options(profile, original, {'path':'probe.zip'})
            import shutil
            shutil.rmtree(profile)

    def tiny_scene(self):
        profile = self.sources['forest']
        for directory in ('config', 'mods', 'shaderpacks', 'logs', 'saves/' + WORLD): (profile / directory).mkdir(parents=True)
        world = profile / 'saves' / WORLD; (world / 'level.dat').write_bytes(b'tiny stopped save')
        (profile / 'config/iris.properties').write_text('enableShaders=true\nshaderPack=original.zip\n')
        (profile / 'config/shaderlod-client.toml').write_text('[old]\nkeep=true\n')
        (profile / 'options.txt').write_text('renderDistance:24\n'); (profile / 'logs/old.log').write_text('old')
        (profile / 'workload-measurement.json').write_text('history')
        original = profile / 'shaderpacks/original.zip'; original.write_bytes(b'original stand-in'); self.probe.write_bytes(b'probe stand-in')
        dh = profile / 'mods/DH-fixture.jar'; dh.write_bytes(b'DH stand-in')
        runtime = self.root / 'runtime.json'; runtime.write_text(json.dumps({'artifacts': {'distanthorizons': {
            'filename':dh.name, 'hashes':{'sha512':file_hash(dh, 'sha512')}}}}))
        record = dict(scene='forest', profile=profile, world=world, manifest={'fixture':True},
            control=prep.POSES['forest'] | {'weather':'clear','preset':'PERFORMANCE','debug':False}, removed_control_overrides={},
            original={'path':str(original),'sha256':file_hash(original)}, probe={'path':str(self.probe),'sha256':file_hash(self.probe)},
            qualification={'manifest_sha256':'fixture'}, native_options={'SHADER_AO':'SHADER_AO_GTAO'},
            patched_options={'SHADER_AO':'SHADER_AO_GTAO'} | prep.FLAGS, option_evidence={'fixture':True},
            source_files_sha256={str(profile / 'options.txt'):file_hash(profile / 'options.txt')},
            provenance_files={'shaderlod-client-original.toml':profile / 'config/shaderlod-client.toml'})
        return record, runtime

    def test_tiny_stopped_copies_preserve_source_history_and_provenance(self):
        record, runtime = self.tiny_scene(); before = world_fingerprint(record['world'])
        with mock.patch.object(prep, 'require_exclusive_runtime'), mock.patch.object(prep, 'inspect_scene', return_value=record):
            result = prep.prepare(self.output, ('forest',), self.sources, self.probe, runtime, self.root)
        self.assertEqual(result['status'],'complete')
        base = self.output / 'forest'; manifest = json.loads((base / 'workload-manifest.json').read_text())
        self.assertEqual(set(manifest['copied_world_sha256'].values()), {before})
        for arm in prep.ARMS:
            profile = base / arm
            self.assertFalse(any((profile / 'mods').glob('*.jar')))
            self.assertFalse((profile / 'logs').exists()); self.assertFalse((profile / 'workload-measurement.json').exists())
            self.assertEqual(world_fingerprint(profile / 'saves' / WORLD), before)
            self.assertIn('options.txt', manifest['experiment_arms'][arm]['input_files_sha256'])
        self.assertEqual(manifest['quality_options']['patched-off'], manifest['quality_options']['adaptive'])
        self.assertEqual((base / 'source-provenance/shaderlod-client-original.toml').read_text(), '[old]\nkeep=true\n')
        self.assertTrue((record['profile'] / 'mods/DH-fixture.jar').exists()); self.assertTrue((record['profile'] / 'logs/old.log').exists())
        self.assertEqual(world_fingerprint(record['world']), before)

    def test_locked_source_creates_no_output_and_failure_retains_fresh_artifacts(self):
        record, runtime = self.tiny_scene()
        @contextmanager
        def locked(_):
            raise ValueError('World is open')
            yield
        with mock.patch.object(prep,'require_exclusive_runtime'), mock.patch.object(prep,'unlocked_world',locked):
            with self.assertRaisesRegex(ValueError,'World is open'): prep.prepare(self.output,('forest',),self.sources,self.probe,runtime,self.root)
        self.assertFalse(self.output.exists())
        (record['profile'] / 'mods/unmanaged.jar').write_bytes(b'preserve')
        with mock.patch.object(prep,'require_exclusive_runtime'), mock.patch.object(prep,'inspect_scene',return_value=record):
            with self.assertRaisesRegex(ValueError,'unmanaged'): prep.prepare(self.output,('forest',),self.sources,self.probe,runtime,self.root)
        self.assertEqual(json.loads((self.output / 'photon-ao-reuse-preparation.json').read_text())['status'],'failed')
        self.assertTrue((record['profile'] / 'mods/unmanaged.jar').exists())
        self.assertTrue((self.output / 'forest/high/mods/unmanaged.jar').exists())


if __name__ == '__main__': unittest.main()
