"""Strict source/options/config/copy fixtures; no client, reload or GPU."""
import json
from pathlib import Path
import shutil
import sys
import tempfile
import tomllib
import unittest
from unittest import mock
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import prepare_photon_lighting_rate_profiles as prep
from setup_comparison import file_hash,world_fingerprint,WORLD

class LightingProfileTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.source=self.root/'source/forest/high';self.output=self.root/'run-workloads/new'
    def tearDown(self):self.temp.cleanup()

    def test_plans_are_fresh_and_explicit(self):
        result=prep.plan(self.output,self.source,root=self.root,cue=True)
        self.assertTrue(result['optional_cue']);self.assertEqual('minimal/noDH',result['minimum_runtime_stack'])
        self.assertTrue(result['opaque_request']['preserveSalientLighting'])
        self.assertEqual([1,.25],result['opaque_request']['ratios'])
        self.output.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError,'fresh'):prep.plan(self.output,self.source,root=self.root)
        with self.assertRaises(ValueError):prep.plan(self.root/'tracked',self.source,root=self.root)

    def test_actual_complete_archive_source_metadata_helper_and_license_qualification(self):
        runtime=json.loads((ROOT/'dev/runtime.lock.json').read_text())
        original=ROOT/prep.shader.BASE;probe=ROOT/'build/Photon-LightingRatePrototype-disabled.zip'
        if not probe.exists():self.skipTest('Frozen production archive absent')
        info,private,q=prep.qualify_archives(original,probe,runtime)
        self.assertEqual(prep.PROBE_SHA256,private['sha256']);self.assertTrue(q['qualified'])
        self.assertEqual(prep.MANIFEST_SHA256,q['manifest_sha256'])
        self.assertEqual('2fff58476c6d49d40eb61e5f6683dc58697ed964f128a3f444442677b06327b0',q['metadata']['shared_surface_helper']['sha256'])
        damaged=self.root/'damaged.zip';shutil.copy2(probe,damaged)
        with zipfile.ZipFile(damaged,'a') as z:z.writestr('extra-unreviewed.txt','extra')
        with self.assertRaisesRegex(ValueError,'archive'):prep.qualify_archives(original,damaged,runtime)
        with mock.patch.object(prep,'PROBE_SHA256',file_hash(damaged)):
            with self.assertRaisesRegex(ValueError,'inventory'):prep.qualify_archives(original,damaged,runtime)

    def test_source_options_all_held_and_graph_compatible(self):
        runtime=json.loads((ROOT/'dev/runtime.lock.json').read_text())
        original,probe,_=prep.qualify_archives(ROOT/prep.shader.BASE,prep.PROBE,runtime)
        native,private,evidence=prep.native_options(prep.SOURCE,original,probe)
        self.assertEqual(private,native|{prep.shader.SWITCH:'true'})
        self.assertEqual(dict.fromkeys(prep.GRAPH_OPTIONS,'false'),evidence['graph_options'])
        self.assertEqual(prep.SOURCE_OPTIONS_SHA256,evidence['held_effective_options_sha256'])
        self.assertEqual({'DISTANT_HORIZONS':False,'VOXY':False},evidence['expected_environment'])

    def option_fixture(self):
        profile=self.root/'options';(profile/'config').mkdir(parents=True);(profile/'shaderpacks').mkdir()
        (profile/'config/iris.properties').write_text('shaderPack=original.zip\n')
        state=dict(framebuffer_width=2976,framebuffer_height=1672,requested_chunks=24,effective_chunks=24,shaders_in_use=True,fallback=False)
        values=dict.fromkeys(prep.GRAPH_OPTIONS,'false');values.pop('NORMAL_MAPPING')
        report=dict(status='complete',actual_shader_sha256=prep.shader.BASE_SHA256,effective_shader_options=values,
            effective_shader_options_sha256=prep.canonical_options_hash(values),input_state_start=state,input_state_end=state)
        path=profile/'workload-measurement.json';path.write_text(json.dumps(report))
        original=dict(path='original.zip',settings=''.join('//#define '+k+'\n' for k in prep.GRAPH_OPTIONS),declarations={})
        return profile,report,path,original

    def test_missing_public_flags_use_pinned_defaults_and_unsupported_true_rejects(self):
        profile,report,path,original=self.option_fixture();defaults=dict.fromkeys(prep.GRAPH_OPTIONS,'false')
        with mock.patch.object(prep,'filter_effective_options',return_value=(report['effective_shader_options'],{})),\
             mock.patch.object(prep.zipfile,'ZipFile') as archive,\
             mock.patch.object(prep,'archive_defaults',side_effect=[defaults,defaults|{prep.shader.SWITCH:'false'}]):
            native,private,evidence=prep.native_options(profile,original,{'path':'probe.zip'})
        self.assertEqual('false',native['NORMAL_MAPPING']);self.assertIn('NORMAL_MAPPING',evidence['absent_options_resolved_from_pinned_defaults'])
        report['effective_shader_options']['ENVIRONMENT_REFLECTIONS']='true'
        report['effective_shader_options_sha256']=prep.canonical_options_hash(report['effective_shader_options']);path.write_text(json.dumps(report))
        with mock.patch.object(prep,'filter_effective_options',return_value=(report['effective_shader_options'],{})),\
             mock.patch.object(prep.zipfile,'ZipFile'),\
             mock.patch.object(prep,'archive_defaults',side_effect=[defaults,defaults|{prep.shader.SWITCH:'false'}]):
            with self.assertRaisesRegex(ValueError,'ENVIRONMENT_REFLECTIONS'):prep.native_options(profile,original,{'path':'probe.zip'})

    def test_actual_producer_rules_keep_protection_true_and_transport_quarter(self):
        source=prep.producer_semantics()
        self.assertTrue(source['requested']['preserveSalientLighting'])
        self.assertEqual(.25,source['expected_transport']['farWorkRatio'])
        self.assertEqual(2,source['expected_transport']['mode'])
        self.assertEqual('unverified; source normalization and registration evidence only',source['observed_transport'])
        data=tomllib.loads(prep.fresh_effects_config())['effects']
        self.assertEqual(7,len(data));self.assertTrue(all(v['preserveSalientLighting'] for v in data.values()))
        self.assertEqual('ADAPTIVE',data['opaque_lighting']['mode'])
        self.assertTrue(all(v['mode']=='INHERIT' and v['farWorkRatio']==1 for k,v in data.items() if k!='opaque_lighting'))

    def tiny(self):
        profile=self.source
        for folder in ('config','mods','logs','shaderpacks','saves/'+WORLD):(profile/folder).mkdir(parents=True)
        world=profile/'saves'/WORLD;(world/'level.dat').write_bytes(b'stopped fixture')
        (profile/'config/iris.properties').write_text('enableShaders=true\nshaderPack=original.zip\n')
        (profile/'config/shaderlod-client.toml').write_text('[old]\nkeep=true\n')
        (profile/'options.txt').write_text('renderDistance:24\n');(profile/'logs/old.log').write_text('retain')
        original=profile/'shaderpacks/original.zip';original.write_bytes(b'original fixture')
        private=self.root/'private.zip';private.write_bytes(b'private fixture')
        dh=profile/'mods/DH.jar';dh.write_bytes(b'DH fixture')
        runtime=self.root/'runtime.json';runtime.write_text(json.dumps({'artifacts':{'distanthorizons':{'filename':dh.name,'hashes':{'sha512':file_hash(dh,'sha512')}}}}))
        record=dict(scene='forest',profile=profile,world=world,manifest={'fixture':True},
            control=prep.copies.POSES['forest']|{'weather':'clear','preset':'PERFORMANCE','debug':False},removed_control_overrides={},
            original={'path':str(original),'sha256':file_hash(original)},probe={'path':str(private),'sha256':file_hash(private)},
            qualification={'manifest_path':'fixture','manifest_sha256':'fixture'},native_options={'NORMAL_MAPPING':'false'},
            patched_options={'NORMAL_MAPPING':'false',prep.shader.SWITCH:'true'},option_evidence={'fixture':True},
            source_files_sha256={str(profile/'options.txt'):file_hash(profile/'options.txt')},
            provenance_files={'shaderlod-client-original.toml':profile/'config/shaderlod-client.toml'})
        return record,runtime,private

    def test_real_copy_seam_preserves_source_and_single_archive_normal_cue_configs(self):
        record,runtime,private=self.tiny();before=world_fingerprint(record['world']);semantics=prep.producer_semantics()
        with mock.patch.object(prep.copies,'require_exclusive_runtime'),mock.patch.object(prep,'require_exclusive_runtime'),\
             mock.patch.object(prep,'producer_semantics',return_value=semantics):
            state=prep.prepare(self.output,self.source,private,runtime,self.root,cue=True,option_off=True,source_inspector=lambda *args:record)
        self.assertEqual('complete',state['status'])
        manifest=json.loads((self.output/'forest/workload-manifest.json').read_text())
        for arm in prep.copies.ARMS:
            p=self.output/'forest'/arm;core=tomllib.loads((p/'config/shaderlod-client.toml').read_text())
            effect=tomllib.loads((p/'config'/prep.EFFECTS_FILE).read_text())['effects']['opaque_lighting']
            self.assertFalse(core['lod']['debug']);self.assertEqual(arm=='adaptive',core['lod']['enabled'])
            self.assertEqual('ADAPTIVE',effect['mode']);self.assertTrue(effect['preserveSalientLighting'])
            self.assertEqual(before,world_fingerprint(p/'saves'/WORLD));self.assertFalse(list((p/'mods').glob('*.jar')))
            self.assertFalse((p/'logs').exists())
            self.assertEqual(file_hash(p/'config'/prep.EFFECTS_FILE),manifest['experiment_arms'][arm]['effects_config_sha256'])
        self.assertEqual(manifest['experiment_arms']['patched-off']['sha256'],manifest['experiment_arms']['adaptive']['sha256'])
        cue=self.output/'coverage/forest/adaptive'
        self.assertTrue(tomllib.loads((cue/'config/shaderlod-client.toml').read_text())['lod']['debug'])
        cue_manifest=json.loads((cue.parent/'workload-manifest.json').read_text());self.assertFalse(cue_manifest['timing_allowed'])
        option_off=self.output/'option-off/forest/adaptive'
        off_core=tomllib.loads((option_off/'config/shaderlod-client.toml').read_text())
        self.assertTrue(off_core['lod']['enabled']);self.assertFalse(off_core['lod']['debug'])
        off_manifest=json.loads((option_off.parent/'workload-manifest.json').read_text())
        off_record=off_manifest['experiment_arms']['adaptive']
        self.assertEqual('false',off_record['requested_options'][prep.shader.SWITCH])
        self.assertFalse(off_manifest['private_gate_override'])
        self.assertEqual(before,world_fingerprint(option_off/'saves'/WORLD))
        iris=prep.read_option_properties((option_off/'config/iris.properties').read_text())
        sidecar=option_off/'shaderpacks'/(iris['shaderPack']+'.txt')
        self.assertEqual('false',prep.read_option_properties(sidecar.read_text())[prep.shader.SWITCH])
        self.assertEqual(file_hash(sidecar),off_record['input_files_sha256'][str(sidecar.relative_to(option_off))])
        self.assertTrue((record['profile']/'mods/DH.jar').exists());self.assertTrue((record['profile']/'logs/old.log').exists())
        self.assertEqual(before,world_fingerprint(record['world']))

    def test_config_stage_failure_retains_failed_marker(self):
        record,runtime,private=self.tiny();semantics=prep.producer_semantics()
        with mock.patch.object(prep.copies,'require_exclusive_runtime'),mock.patch.object(prep,'producer_semantics',return_value=semantics),\
             mock.patch.object(prep,'update_arm',side_effect=ValueError('fixture rejected')):
            with self.assertRaisesRegex(ValueError,'fixture rejected'):
                prep.prepare(self.output,self.source,private,runtime,self.root,source_inspector=lambda *args:record)
        marker=json.loads((self.output/'photon-lighting-rate-preparation.json').read_text());self.assertEqual('failed',marker['status'])
        self.assertEqual('complete',marker['copy_stage']['status'])

if __name__=='__main__':unittest.main()
