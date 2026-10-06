"""Explicit frozen QA qualifiers and tiny shared-copy fixtures; no actual clients/world preparation."""
import json
from pathlib import Path
import sys
import tempfile
import tomllib
import unittest
from unittest import mock
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import prepare_parallel_lod_profiles as prep
from setup_comparison import WORLD,file_hash,world_fingerprint


class ParallelProfilesTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name).resolve(); self.output=self.root/'run-workloads/new'

    def test_explicit_pure_plan_ignored_fresh_sources_and_no_shader_tuning(self):
        for name in ('reflection','volume','procedural','lighting-cost'):
            plan=prep.plan(name,self.output,root=self.root)
            self.assertFalse(self.output.exists()); self.assertFalse(plan['gpu_timing']); self.assertTrue(plan['smoke_only'])
            self.assertEqual(plan['arms'],['high','patched-off','adaptive']); self.assertEqual(plan['framebuffer'],[2976,1672])
            self.assertEqual(plan['original_sha256'],prep.ORIGINAL_SHA256); self.assertTrue(plan['manifest_sha256'])
        with self.assertRaisesRegex(ValueError,'ignored'): prep.plan('reflection',self.root/'tracked',root=self.root)
        with self.assertRaisesRegex(ValueError,'Unknown'): prep.plan('universal',self.output,root=self.root)
        self.assertEqual(prep.FAMILIES['procedural'].manifest_sha256,'b529fdb01aabef5670128e04a25224bfd4368c2a63641214023c352099d100e2')
        family=prep.FAMILIES['volume']
        self.assertEqual(family.manifest_sha256,'40efd7e702e41388033255d48a007c543e1055ccb4e2513c1908c2d82b8f2677')
        self.assertEqual(family.archive_sha256,'5de7561dd24cdca80860a1d5f2c60400e74163a489cc163e916f3fd47feb27de')
        self.assertEqual(family.metadata_sha256,'b7ce3bac5c13e35e13ce2e58a0bc1fb5bf6fc628d2a5f6b8cafc83f3107c0761')
        self.assertEqual(family.helpers,prep.volume.ADDITIONS)
        self.assertIn('capability schema1',prep.plan('volume',self.output,root=self.root)['policy_note'])

    def test_requested_effects_seven_defaults_and_conservative_volume_override(self):
        defaults=tomllib.loads(prep.effects_config())['effects']
        self.assertEqual(set(defaults),set(prep.EFFECTS))
        self.assertTrue(all(v['mode']=='INHERIT' and v['mediumWorkRatio']==v['farWorkRatio']==1 for v in defaults.values()))
        self.assertTrue(all(v['preserveSalientLighting'] is True for v in defaults.values()))
        adaptive=tomllib.loads(prep.effects_config('adaptive'))['effects']
        self.assertEqual(adaptive['volumetrics'],dict(mode='ADAPTIVE',near=512.,far=1536.,transition=128.,mediumWorkRatio=.85,farWorkRatio=.75,preserveSalientLighting=True))
        for name in defaults.keys()-{'volumetrics'}: self.assertEqual(defaults[name],adaptive[name])
        with self.assertRaises(ValueError): prep.plan('reflection',self.output,root=self.root,volume_mode='adaptive')
        with self.assertRaises(ValueError): prep.plan('volume',self.output,root=self.root,debug_cue=True)

    def test_actual_ready_archives_complete_source_helpers_metadata_and_native_sources(self):
        original=ROOT/'dev/upstream/photon_v1.3b.zip'
        if not original.exists(): self.skipTest('Local original Photon absent')
        runtime=json.loads((ROOT/'dev/runtime.lock.json').read_text())
        for name in ('reflection','volume','procedural'):
            family=prep.FAMILIES[name]; archive=ROOT/'build'/family.archive_name
            if not archive.exists(): self.fail('Frozen assembled QA archive missing: '+str(archive))
            _,_,qualification=prep.qualify_archives(name,original,archive,runtime)
            self.assertTrue(qualification['qualified']); self.assertEqual(qualification['archive_sha256'],family.archive_sha256)
            self.assertEqual(set(qualification['helper_sha256']),{n for n,_ in family.helpers})
            if name=='volume':
                metadata=qualification['metadata']
                self.assertEqual(qualification['capability_schema'],1)
                self.assertEqual(qualification['capability_operation'],'shaderlod:volume_integration')
                self.assertEqual(metadata['inherit_retained_ratios'],[1.0,1.0])
                self.assertEqual(metadata['linked_consumption'],'unverified')
                self.assertEqual(metadata['source_referenced_fields'][:2],['shaderlodCapabilitiesVersion','shaderlodVolumeIntegrationAvailable'])
                self.assertEqual(metadata['required_effective_options'],prep.volume.REQUIRED_OPTIONS)
                self.assertEqual(set(metadata['reference_member_sha256']),set(prep.volume.REFERENCE_MEMBERS))
                self.assertEqual(set(qualification['helper_sha256']),{'shaders/include/shaderlod/'+name+'.glsl'
                    for name in ('volume_budget','photon_volume_adapter','effect_policy','work_policy')})
            if name=='procedural':
                self.assertEqual(qualification['metadata']['consumed_core_fields'],[])
                self.assertEqual(qualification['option_on_source_core_fields'],['shaderlodBridgeVersion','shaderlodEnabled'])
            for scene in ('coastalwater','forest'):
                source=prep.copies.DEFAULT_SOURCES[scene]
                if not (source/'workload-measurement.json').exists(): continue
                record=prep.inspect_scene({'scene':scene,'source_profile':str(source)},archive,runtime,ROOT,name)
                self.assertEqual(record['patched_options'],record['native_options']|{family.builder.SWITCH:'true'})
                self.assertFalse(any(k.startswith('SHADERLOD_') for k in record['native_options']))

    def test_archive_injection_helper_corruption_and_metadata_mutation_rejected(self):
        original=ROOT/'dev/upstream/photon_v1.3b.zip'; family=prep.FAMILIES['reflection']; archive=ROOT/'build'/family.archive_name
        if not archive.exists() or not original.exists(): self.skipTest('Frozen local source/pack absent')
        runtime=json.loads((ROOT/'dev/runtime.lock.json').read_text()); files=family.builder.archive_members(archive)
        fixture=self.root/'mutated.zip'
        def write(data):
            with zipfile.ZipFile(fixture,'w',compression=zipfile.ZIP_DEFLATED) as z:
                for name,content in data.items(): z.writestr(name,content)
        write(files|{'unexpected':b'cannot accept arbitrary additions'})
        with self.assertRaisesRegex(ValueError,'inventory'): prep.qualify_archives('reflection',original,fixture,runtime)
        helper=family.helpers[0][0]; write(files|{helper:files[helper]+b'changed'})
        with self.assertRaisesRegex(ValueError,'helper bytes'): prep.qualify_archives('reflection',original,fixture,runtime)
        metadata=family.builder.METADATA; obj=json.loads(files[metadata]); obj['gpu_benefit']='promoted'
        write(files|{metadata:json.dumps(obj).encode()})
        with self.assertRaisesRegex(ValueError,'metadata'): prep.qualify_archives('reflection',original,fixture,runtime)
        family=prep.FAMILIES['volume']; files=family.builder.archive_members(ROOT/'build'/family.archive_name)
        helper='shaders/include/shaderlod/work_policy.glsl'; write(files|{helper:files[helper]+b'changed'})
        with self.assertRaisesRegex(ValueError,'helper bytes'): prep.qualify_archives('volume',original,fixture,runtime)
        metadata=family.builder.METADATA
        for field,value in (('capability_schema',0),('inherit_retained_ratios',[.85,.75]),('source_referenced_fields',[])):
            obj=json.loads(files[metadata]); obj[field]=value; write(files|{metadata:json.dumps(obj).encode()})
            with self.assertRaisesRegex(ValueError,'metadata'): prep.qualify_archives('volume',original,fixture,runtime)

    def test_pending_assembly_lighting_contract_still_pins_all_members_and_full_metadata(self):
        original_path=ROOT/'dev/upstream/photon_v1.3b.zip'
        if not original_path.exists(): self.skipTest('Pinned local original absent')
        family=prep.FAMILIES['lighting-cost']; module=family.builder
        original=module.archive_members(original_path); manifest=json.loads((ROOT/module.MANIFEST).read_text())
        patched=module.patch_members(original,manifest,'disabled'); metadata=prep.lighting_metadata(manifest,original,patched,sorted(family.members))
        fixture=self.root/'lighting-fixture.zip'
        def write(meta):
            with zipfile.ZipFile(fixture,'w',compression=zipfile.ZIP_DEFLATED) as z:
                for name,data in patched.items(): z.writestr(name,data)
                z.writestr(module.METADATA,json.dumps(meta))
        runtime=json.loads((ROOT/'dev/runtime.lock.json').read_text())
        write(metadata); _,_,qualification=prep.qualify_archives('lighting-cost',original_path,fixture,runtime)
        self.assertEqual(qualification['metadata']['quality_class'],'quality_changing_cost_control')
        write(metadata|{'coverage_cue':{'wrong':'cannot relabel eligibility'}})
        with self.assertRaisesRegex(ValueError,'Lighting metadata'): prep.qualify_archives('lighting-cost',original_path,fixture,runtime)

    def test_tiny_scene_copies_shared_loop_preserves_source_then_records_new_controls(self):
        source=self.root/'source/coastalwater/high'; world=source/'saves'/WORLD
        for directory in ('config','mods','shaderpacks','logs','saves/'+WORLD): (source/directory).mkdir(parents=True)
        (world/'level.dat').write_bytes(b'tiny canonical source')
        (source/'config/iris.properties').write_text('enableShaders=true\nshaderPack=original.zip\n')
        (source/'config/shaderlod-client.toml').write_text('[old]\nkeep=true\n')
        (source/'options.txt').write_text('renderDistance:24\n'); (source/'logs/old.log').write_text('retain')
        original=source/'shaderpacks/original.zip'; original.write_bytes(b'original fixture')
        private=self.root/'private.zip'; private.write_bytes(b'private fixture')
        runtime=self.root/'runtime.json'; runtime.write_text(json.dumps({'artifacts':{'distanthorizons':{'filename':'DH.jar'}}}))
        fingerprint=world_fingerprint(world); family=prep.FAMILIES['volume']
        record=dict(scene='coastalwater',profile=source,world=world,manifest={'fixture':True},
            control=prep.copies.POSES['coastalwater']|dict(preset='PERFORMANCE',debug=False,weather='clear'),removed_control_overrides={},
            original=dict(path=str(original),sha256=file_hash(original)),probe=dict(path=str(private),sha256=file_hash(private)),
            qualification=dict(manifest_path=str(self.root/family.builder.MANIFEST),manifest_sha256=family.manifest_sha256),
            native_options={'UPSTREAM':'unchanged'},patched_options={'UPSTREAM':'unchanged',family.builder.SWITCH:'true'},option_evidence={'fixture':True},
            source_files_sha256={str(source/'options.txt'):file_hash(source/'options.txt')},
            provenance_files={'shaderlod-client-original.toml':source/'config/shaderlod-client.toml'})
        with mock.patch.object(prep.copies,'require_exclusive_runtime'),mock.patch.object(prep,'inspect_scene',return_value=record):
            state=prep.prepare('volume',self.output,sources={'coastalwater':source},probe=private,root=self.root,runtime_lock=runtime,volume_mode='adaptive')
        self.assertEqual(state['status'],'complete'); manifest=json.loads((self.output/'coastalwater/workload-manifest.json').read_text())
        self.assertFalse(manifest['configuration_only']); self.assertEqual(manifest['qa_operation']['volume_mode'],'adaptive')
        self.assertEqual(manifest['quality_options']['patched-off'],manifest['quality_options']['adaptive'])
        for arm in prep.copies.ARMS:
            profile=self.output/'coastalwater'/arm
            self.assertEqual(world_fingerprint(profile/'saves'/WORLD),fingerprint)
            self.assertFalse((profile/'logs').exists()); self.assertFalse(list((profile/'mods').glob('*.jar')))
            effects=tomllib.loads((profile/'config/shaderlod-effects-client.toml').read_text())
            self.assertEqual(effects['effects']['volumetrics']['mode'],'ADAPTIVE')
            self.assertEqual(manifest['experiment_arms'][arm]['effects_config_sha256'],file_hash(profile/'config/shaderlod-effects-client.toml'))
        self.assertEqual(world_fingerprint(world),fingerprint); self.assertTrue((source/'logs/old.log').exists())


if __name__=='__main__': unittest.main()
