"""Bounded preparation/decision checks; no launch, Gradle, save copies or encoder."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import prepare_photon_taau_cost_bracket as prep
from run_proof_matrix import canonical_options_hash


class PhotonTaauCostBracketTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)

    def test_pure_three_arm_plan_preserves_existing_outputs(self):
        output=self.root/'run-workloads/fresh'
        plan=prep.plan(output,self.root/'source/profile',self.root)
        self.assertFalse(output.exists());self.assertEqual(plan['arms'],list(prep.ORDER))
        self.assertFalse(plan['performanceEvidence']);self.assertFalse(plan['launch_authorized'])
        self.assertEqual(plan['initial_warmup_seconds'],30);self.assertEqual(plan['capture_seconds'],20)
        self.assertEqual(plan['gpu_sample_interval'],5)
        output.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError,'Preserving'):prep.plan(output,root=self.root)

    def test_no_build_or_launch_and_all_diagnostics_off(self):
        commands=[prep.command(self.root/arm) for arm in prep.ORDER]
        for command in commands:
            self.assertEqual(command[:2],['./gradlew','runClientLodOff'])
            for flag in ('performanceStack=minimal','includeDh=false','gpuTiming=true','cpuTiming=true',
                         'gpuSampleInterval=5','initialWarmup=30','measurementSeconds=20',
                         'measureWorkload=true','measureOnly=true','cameraQuality='):
                self.assertIn('-P'+flag,command)
            for name in prep.quality.OFF:self.assertIn('-P'+name+'=false',command)
            self.assertNotIn('build',command)
            for task in prep.quality.BUILD_TASKS:
                index=command.index(task);self.assertEqual(command[index-1],'-x')
        stripped=[[x for x in command if not x.startswith('-PrunDirectory=')] for command in commands]
        self.assertEqual(stripped[0],stripped[1]);self.assertEqual(stripped[1],stripped[2])

    def test_unity_graph_current_controls_and_queryfree_command(self):
        plan=prep.plan(self.root/'run-workloads/fresh',self.root/'source/profile',self.root,unity_graph=True)
        self.assertEqual(plan['arms'],list(prep.UNITY_ORDER))
        self.assertEqual(plan['initial_warmup_seconds'],60)
        self.assertTrue(plan['main_mod_present']);self.assertFalse(plan['global_enabled'])
        self.assertFalse(plan['gpu_queries']);self.assertEqual(plan['fixed_wetness'],1)
        self.assertIsNone(plan['minimum_frame_reduction_fraction'])
        argv=prep.command(self.root/'native-before',True)
        self.assertEqual(argv[1],'runClientLodOn')
        for flag in ('gpuTiming=false','initialWarmup=60','measurementSeconds=20','fixedWetness=1','measureWorkload=true','physicalResolution=false','pixelHistory=false'):
            self.assertIn('-P'+flag,argv)
        self.assertNotIn('-PgpuTiming=true',argv)
        self.assertNotIn('-PmeasureWorkload=false',argv)
        self.assertIn('cloudhistory',prep.unity_postanalysis_plan()['cross_arm'][1])
        self.assertEqual(prep.UNITY_RECEIPT_SHA256,'5e6b8a3305342ae18655de054b2175f2c0f64a1219269fe404bd2aafd7c96b16')

    def test_screen_rejects_no_signal_even_if_gpu_improves(self):
        result=prep.screen([20,19.01,20],[16,14,16])
        self.assertEqual(result['verdict'],'stop-no-useful-signal')
        self.assertFalse(result['performanceEvidence']);self.assertFalse(result['adapter_gain'])

    def test_screen_requires_both_original_denominators_and_gpu_direction(self):
        self.assertEqual(prep.screen([20,18.9,19.5],[16,15,16])['verdict'],'stop-no-useful-signal')
        self.assertEqual(prep.screen([20,18,20],[16,16.1,16])['verdict'],'stop-no-useful-signal')
        self.assertEqual(prep.screen([20,18,20],[16,15,16])['verdict'],'promising-diagnostic-only')
        self.assertEqual(prep.screen([20,19,20],[16,15,16])['verdict'],'stop-no-useful-signal')

    def test_screen_drift_is_inconclusive_before_intervention_signal(self):
        self.assertEqual(prep.screen([20,15,22.01],[16,12,16])['verdict'],'inconclusive-original-drift')
        self.assertEqual(prep.screen([20,15,20],[16,12,17.61])['verdict'],'inconclusive-original-drift')
        self.assertEqual(prep.screen([20,15,22],[16,12,16])['verdict'],'promising-diagnostic-only')
        for value in (0,-1,float('nan'),float('inf'),True):
            with self.assertRaises(ValueError):prep.screen([20,value,20],[16,15,16])

    def fixture(self):
        defaults={'TAA':'true','TAAU':'false','TAAU_RENDER_SCALE':'0.75'}
        texts={'options.txt':'renderDistance:24\nmaxFps:260\n',
               'config/iris.properties':'# timestamp\nenableShaders=true\nshaderPack=photon_v1.3b.zip\n',
               'config/other.toml':'[section]\nvalue=true\n',
               'config/other.json':'{"quality":1}',
               'config/unknown.data':'preserve exact',
               'config/sodium-fingerprint.json':json.dumps(dict(v=1,s='a'*128,u='b'*128,p='c'*128,t=1)),
               'shaderpacks/photon_v1.3b.zip.txt':'TAA=true\nTAAU=false\nTAAU_RENDER_SCALE=0.75\n'}
        snapshot=dict(complete=True,errors=[],files={name:dict(bytes=len(text.encode()),utf8=text,
            sha256=hashlib.sha256(text.encode()).hexdigest()) for name,text in texts.items()})
        settings={'options.txt':dict(format='minecraft colon properties',value={'renderDistance':'24','maxFps':'260'}),
                  'config/iris.properties':dict(format='simple literal properties',value={'enableShaders':'true','shaderPack':'photon_v1.3b.zip'}),
                  'config/other.toml':dict(format='toml',value={'section':{'value':True}}),
                  'config/other.json':dict(format='json',value={'quality':1}),
                  'config/unknown.data':dict(format='unrecognized exact bytes',sha256=snapshot['files']['config/unknown.data']['sha256'])}
        prepared=dict(settings=settings,nonshader_identities={'config/sodium-fingerprint.json':{}},
                      sidecar=dict(defaults=defaults,semantic_sha256=canonical_options_hash(defaults)))
        return snapshot,prepared

    def replace(self,snapshot,name,text):
        snapshot['files'][name]=dict(bytes=len(text.encode()),utf8=text,sha256=hashlib.sha256(text.encode()).hexdigest())

    def test_capture_semantics_retain_full_inventory_and_source_default_sidecar(self):
        snapshot,prepared=self.fixture()
        result=prep.qualify_configuration_snapshot(snapshot,prepared)
        self.assertEqual(result['settings'],prepared['settings'])
        snapshot['files'].pop('shaderpacks/photon_v1.3b.zip.txt')
        self.replace(snapshot,'config/iris.properties','shaderPack=photon_v1.3b.zip\n# updated\nenableShaders=true\n')
        self.replace(snapshot,'config/other.toml','[section]\n# normalized\nvalue = true\n')
        self.assertEqual(prep.qualify_configuration_snapshot(snapshot,prepared)['settings'],prepared['settings'])

    def test_capture_semantic_changes_and_unknown_new_config_reject(self):
        for name,text in [('options.txt','renderDistance:16\nmaxFps:260\n'),
                          ('config/other.toml','[section]\nvalue=false\n'),
                          ('config/other.json','{"quality":0}'),
                          ('config/iris.properties','enableShaders=false\nshaderPack=photon_v1.3b.zip\n'),
                          ('config/unknown.data','hidden change')]:
            snapshot,prepared=self.fixture();self.replace(snapshot,name,text)
            with self.assertRaises(ValueError):prep.qualify_configuration_snapshot(snapshot,prepared)
        snapshot,prepared=self.fixture();self.replace(snapshot,'config/new.json','{}')
        with self.assertRaisesRegex(ValueError,'inventory'):prep.qualify_configuration_snapshot(snapshot,prepared)

    def test_capture_requires_actual_complete_consistent_raw_evidence(self):
        snapshot,prepared=self.fixture();snapshot['complete']=False
        with self.assertRaisesRegex(ValueError,'Incomplete'):prep.qualify_configuration_snapshot(snapshot,prepared)
        snapshot,prepared=self.fixture();snapshot['files']['options.txt']['sha256']='0'*64
        with self.assertRaisesRegex(ValueError,'text/hash'):prep.qualify_configuration_snapshot(snapshot,prepared)
        snapshot,prepared=self.fixture();snapshot['files'].pop('config/other.toml')
        with self.assertRaisesRegex(ValueError,'missing'):prep.qualify_configuration_snapshot(snapshot,prepared)
        snapshot,prepared=self.fixture();snapshot['errors']=['unreadable config']
        with self.assertRaisesRegex(ValueError,'Incomplete'):prep.qualify_configuration_snapshot(snapshot,prepared)

    def test_sodium_cache_role_is_schema_bounded_and_raw_retained(self):
        snapshot,prepared=self.fixture()
        self.replace(snapshot,'config/sodium-fingerprint.json',json.dumps(dict(v=1,s='d'*128,u='e'*128,p='f'*128,t=2)))
        result=prep.qualify_configuration_snapshot(snapshot,prepared)
        self.assertEqual(result['nonshader_identities']['config/sodium-fingerprint.json']['raw']['p'],'f'*128)
        self.replace(snapshot,'config/sodium-fingerprint.json',json.dumps(dict(v=1,s='d'*128,u='e'*128,p='f'*128,t=2,quality=0)))
        with self.assertRaisesRegex(ValueError,'schema'):prep.qualify_configuration_snapshot(snapshot,prepared)

    def test_capture_full_sidecar_map_rejects_quality_or_unknown_values(self):
        snapshot,prepared=self.fixture()
        self.replace(snapshot,'shaderpacks/photon_v1.3b.zip.txt','TAAU=true\nTAAU_RENDER_SCALE=0.90\n')
        with self.assertRaisesRegex(ValueError,'sidecar semantics'):prep.qualify_configuration_snapshot(snapshot,prepared)
        self.replace(snapshot,'shaderpacks/photon_v1.3b.zip.txt','UNKNOWN=false\n')
        with self.assertRaisesRegex(ValueError,'undeclared'):prep.qualify_configuration_snapshot(snapshot,prepared)

    def test_postanalysis_plan_preserves_runtime_boundaries_and_original_baseline(self):
        plan=prep.postanalysis_plan();text=json.dumps(plan)
        for token in ('validate_report','validate_temporal_inputs','include_shaderlod=False',
                      '60 positive','10%','EACH native','quality','DIAGNOSTIC','configuration_files'):
            self.assertIn(token,text)
        self.assertFalse(plan['performanceEvidence']);self.assertEqual(plan['quality'],'unverified')




if __name__=='__main__':unittest.main()
