"""Bounded density qualification and common-copy fixtures; no real saves or clients."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import prepare_photon_density_detail_profiles as prep
from build_photon_density_detail_probe import BASE
from run_proof_matrix import canonical_options_hash
from setup_comparison import WORLD, file_hash, world_fingerprint


class DensityProfilesTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve(); self.output = self.root / 'run-workloads/new'
        self.probe = self.root / 'fixture.zip'

    def test_pure_fresh_coastal_plan_declares_diagnostic_and_no_world_io(self):
        plan = prep.plan(self.output, probe=self.probe, root=self.root)
        self.assertFalse(self.output.exists()); self.assertEqual(plan['arms'], ['high','patched-off','adaptive'])
        self.assertEqual(plan['quality_class'],'quality_changing_cost_control')
        self.assertEqual(plan['framebuffer'],[2976,1672]); self.assertFalse(plan['dh_present'])
        self.assertTrue(plan['manifest'].endswith(str(prep.MANIFEST)))
        with self.assertRaisesRegex(ValueError,'ignored'): prep.plan(self.root/'tracked',root=self.root)
        self.output.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError,'Preserving'): prep.plan(self.output,root=self.root)

    def metadata(self, original, patched, manifest_bytes):
        return dict(experiment=prep.EXPERIMENT, arm='disabled', base_version='v1.3b', base_sha256=prep.BASE_SHA256,
            manifest_sha256=prep.sha256(manifest_bytes), switch=prep.SWITCH, switch_enabled=False,
            modified_upstream_files=sorted(prep.MEMBERS), member_sha256={name:prep.sha256(patched[name]) for name in prep.MEMBERS},
            license_sha256=prep.sha256(original['LICENSE']), unchanged_upstream_member_count=len(original)-2,
            consumed_core_fields=[], activation='private option AND PROGRAM_DEFERRED1 AND bridge version 2 AND global enabled exactly 1',
            program_scope='PROGRAM_DEFERRED1 only; main-pass cumulus primary and secondary density evaluations',
            diagnostic_fine_noise=0.5, quality_class='quality_changing_cost_control',
            source_policy='Fine worley_1 texture replaced by declared 0.5 diagnostic; coarse noise, literal ray counts/extents/seeds/phase functions and reconstruction unchanged',
            physical_work_caveat='Density/occupancy and upstream early exits can change; not an isolated fetch budget or accepted optimization',
            distance_units='upstream atmosphere coordinates; diagnostic has no distance/footprint threshold',
            transition='uniform global control; intentional density change, no quality transition or calibration',
            fallback='literal original fine texture sample for private off, other programs, absent/unknown core or global off',
            runtime_compilation='unverified', gpu_benefit='unmeasured',
            attribution='Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained')

    def test_real_pinned_members_metadata_and_inspected_coastal_options(self):
        base=ROOT/BASE
        if not base.exists(): self.skipTest('Local pinned original Photon absent')
        manifest_bytes=(ROOT/prep.MANIFEST).read_bytes(); manifest=json.loads(manifest_bytes)
        original=prep.archive_members(base); patched=prep.patch_members(original,manifest,'disabled')
        cumulus=patched[prep.MEMBERS[0]].decode()
        # R1's combined #if was not discovered by the actual Iris option lexer.
        # These source expectations supplement the builder's real lexer fixtures.
        self.assertEqual(cumulus.count('#ifdef '+prep.SWITCH+'\n'),2)
        self.assertNotIn('#if defined '+prep.SWITCH+' && defined PROGRAM_DEFERRED1',cumulus)
        self.assertIn('#if defined PROGRAM_DEFERRED1',cumulus)
        self.assertEqual(prep.sha256(patched[prep.MEMBERS[0]]),'3a5b3fbae654259567e76e660fac012fa0c2743e60c09519945f57d2e7440024')
        metadata=self.metadata(original,patched,manifest_bytes)
        def write(files,meta):
            with zipfile.ZipFile(self.probe,'w',compression=zipfile.ZIP_DEFLATED) as archive:
                for name,data in files.items(): archive.writestr(name,data)
                archive.writestr(prep.METADATA,json.dumps(meta))
        runtime=json.loads((ROOT/'dev/runtime.lock.json').read_text())
        write(patched,metadata)
        original_info,probe,qualified=prep.qualify_archives(base,self.probe,runtime)
        self.assertEqual(qualified['archive_sha256'],file_hash(self.probe))
        self.assertEqual(qualified['quality_class'],'quality_changing_cost_control')
        if (prep.SOURCE/'workload-measurement.json').exists():
            record=prep.inspect_scene(dict(scene='coastalwater',source_profile=str(prep.SOURCE)),self.probe,runtime)
            self.assertEqual(record['patched_options'],record['native_options']|{prep.SWITCH:'true'})
            self.assertEqual(record['qualification']['manifest_path'],str(ROOT/prep.MANIFEST))
        write(patched|{'extra-member':b'injected'},metadata)
        with self.assertRaisesRegex(ValueError,'inventory'): prep.qualify_archives(base,self.probe,runtime)
        write(patched|{prep.MEMBERS[0]:patched[prep.MEMBERS[0]]+b'changed'},metadata)
        with self.assertRaisesRegex(ValueError,'frozen density patch'): prep.qualify_archives(base,self.probe,runtime)
        write(patched,metadata|{'quality_class':'accepted_optimization'})
        with self.assertRaisesRegex(ValueError,'metadata'): prep.qualify_archives(base,self.probe,runtime)

    def option_fixture(self):
        profile=self.root/'option-source'; (profile/'config').mkdir(parents=True); (profile/'shaderpacks').mkdir()
        (profile/'config/iris.properties').write_text('shaderPack=original.zip\n')
        effective=prep.CLOUD_OPTIONS.copy()
        state=dict(framebuffer_width=2976,framebuffer_height=1672,requested_chunks=24,effective_chunks=24,shaders_in_use=True,fallback=False)
        report=dict(status='complete',actual_shader_sha256=prep.BASE_SHA256,effective_shader_options=effective,
                    effective_shader_options_sha256=canonical_options_hash(effective),input_state_start=state,input_state_end=state)
        path=profile/'workload-measurement.json'; path.write_text(json.dumps(report))
        original=dict(path='original.zip',settings=''.join(f'#define {k} {v}\n' for k,v in effective.items()),declarations={})
        return profile,effective,report,path,original

    def test_equal_cloud_native_settings_true_gate_and_changed_budgets_reject(self):
        profile,effective,report,path,original=self.option_fixture()
        with mock.patch.object(prep.zipfile,'ZipFile'),mock.patch.object(prep,'archive_defaults',side_effect=[effective|{prep.SWITCH:'false'},effective]):
            native,patched,_=prep.native_options(profile,original,{'path':'private.zip'})
        self.assertEqual(native,effective); self.assertEqual(patched,native|{prep.SWITCH:'true'})
        report['effective_shader_options']['CLOUDS_TEMPORAL_UPSCALING']='1'
        report['effective_shader_options_sha256']=canonical_options_hash(report['effective_shader_options']); path.write_text(json.dumps(report))
        with self.assertRaisesRegex(ValueError,'cloud settings'): prep.native_options(profile,original,{'path':'private.zip'})

    def test_source_camera_or_viewport_cannot_silently_change(self):
        profile,effective,report,path,original=self.option_fixture()
        report['input_state_end']['framebuffer_width']=1488; path.write_text(json.dumps(report))
        with self.assertRaisesRegex(ValueError,'native/radius'): prep.native_options(profile,original,{'path':'private.zip'})
        with self.assertRaisesRegex(ValueError,'coastalwater only'): prep.inspect_scene({'scene':'forest'},self.probe,{})

    def test_common_copy_callback_keeps_source_equal_and_density_identity(self):
        profile=self.root/'source/coastalwater/high'; world=profile/'saves'/WORLD
        for directory in ('config','mods','shaderpacks','logs','saves/'+WORLD): (profile/directory).mkdir(parents=True)
        (world/'level.dat').write_bytes(b'tiny stopped coast')
        (profile/'config/iris.properties').write_text('enableShaders=true\nshaderPack=original.zip\n')
        (profile/'config/shaderlod-client.toml').write_text('[old]\nkeep=true\n')
        (profile/'options.txt').write_text('renderDistance:24\n'); (profile/'logs/old.log').write_text('retained')
        original=profile/'shaderpacks/original.zip'; original.write_bytes(b'original fixture'); self.probe.write_bytes(b'private fixture')
        runtime=self.root/'runtime.json'; runtime.write_text(json.dumps({'artifacts':{'distanthorizons':{'filename':'DH.jar'}}}))
        fingerprint=world_fingerprint(world)
        record=dict(scene='coastalwater',profile=profile,world=world,manifest={'fixture':True},
            control=prep.copies.POSES['coastalwater']|dict(preset='PERFORMANCE',debug=False,weather='clear'),removed_control_overrides={},
            original=dict(path=str(original),sha256=file_hash(original)),probe=dict(path=str(self.probe),sha256=file_hash(self.probe)),
            qualification=dict(manifest_path=str(self.root/prep.MANIFEST),manifest_sha256=prep.MANIFEST_SHA256,quality_class='quality_changing_cost_control'),
            native_options=prep.CLOUD_OPTIONS.copy(),patched_options=prep.CLOUD_OPTIONS|{prep.SWITCH:'true'},option_evidence={'fixture':True},
            source_files_sha256={str(profile/'options.txt'):file_hash(profile/'options.txt')},
            provenance_files={'shaderlod-client-original.toml':profile/'config/shaderlod-client.toml'})
        with mock.patch.object(prep.copies,'require_exclusive_runtime'),mock.patch.object(prep,'inspect_scene',return_value=record):
            result=prep.prepare(self.output,profile,self.probe,runtime,self.root)
        self.assertEqual(result['status'],'complete'); self.assertEqual(result['operation']['manifest'],str(self.root/prep.MANIFEST))
        self.assertTrue((self.output/'photon-density-detail-preparation.json').exists())
        self.assertFalse((self.output/'photon-ao-reuse-preparation.json').exists())
        manifest=json.loads((self.output/'coastalwater/workload-manifest.json').read_text())
        self.assertEqual(manifest['experiment_family'],prep.EXPERIMENT)
        self.assertIn('density_detail_source_qualification',manifest); self.assertNotIn('ao_reuse_source_qualification',manifest)
        self.assertEqual(manifest['quality_options']['patched-off'],manifest['quality_options']['adaptive'])
        for arm in prep.copies.ARMS:
            fresh=self.output/'coastalwater'/arm
            self.assertEqual(world_fingerprint(fresh/'saves'/WORLD),fingerprint)
            self.assertFalse((fresh/'logs').exists()); self.assertFalse(list((fresh/'mods').glob('*.jar')))
        self.assertTrue((profile/'logs/old.log').exists()); self.assertEqual(world_fingerprint(world),fingerprint)


if __name__=='__main__': unittest.main()
