"""Bounded source/recipe guard checks; no Gradle, client, encoder or save copy."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
import prepare_photon_taau75_calibration as prep
from run_proof_matrix import canonical_options_hash


class Taau75CalibrationTests(unittest.TestCase):
    def test_full_source_defined_map_changes_only_taau(self):
        source = ROOT/'src/main/resources/assets/shaderlod/photonquad/native366.json'
        self.assertTrue(source.exists(), 'Required pinned local source absent')
        native = json.loads(source.read_text())
        maps = prep.option_maps(native)
        self.assertEqual(maps['native'], native)
        self.assertEqual({k for k in native.keys() | maps['taau75'].keys()
                          if native.get(k) != maps['taau75'].get(k)}, {'TAAU'})
        self.assertEqual(maps['taau75']['TAAU_RENDER_SCALE'], '0.75')
        self.assertEqual(maps['taau75']['TAA'], 'true')
        with self.assertRaisesRegex(ValueError, 'map differs'):
            prep.option_maps(native | {'shadowMapResolution': '1024'})


    def test_pure_plan_preserves_existing_outputs_and_baseline(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); output=root/'run-workloads/fresh'
            result=prep.plan(output, root/'source/high', root)
            self.assertFalse(output.exists())
            self.assertEqual(result['groups']['cost-bracket'], ['native-before','taau75','native-after'])
            self.assertFalse(result['performanceEvidence']); self.assertFalse(result['adapter_gain'])
            self.assertFalse(result['launch_authorized']); self.assertIn('quality', result['launch_status'])
            output.mkdir(parents=True)
            with self.assertRaisesRegex(ValueError,'Preserving'): prep.plan(output, root=root)
            with self.assertRaisesRegex(ValueError,'ignored'): prep.plan(root/'tracked', root=root)

    def test_existing_commands_query_free_quality_and_guarded_cost(self):
        profile=ROOT/'run-workloads/example'
        still=prep.quality.command(profile,'still')
        motion=prep.quality.command(profile,'motion',profile/'camera.json')
        cost=prep.cost.command(profile)
        for argv in (still,motion,cost):
            self.assertEqual(argv[:2], ['./gradlew','runClientLodOff'])
            self.assertIn('-PincludeDh=false',argv); self.assertIn('-PperformanceStack=minimal',argv)
            for probe in prep.quality.OFF: self.assertIn('-P'+probe+'=false',argv)
            for task in prep.quality.BUILD_TASKS:
                self.assertEqual(argv[argv.index(task)-1],'-x')
        self.assertIn('-PgpuTiming=false',still); self.assertIn('-PgpuTiming=false',motion)
        for flag in ('gpuTiming=true','gpuSampleInterval=5','initialWarmup=30','measurementSeconds=20',
                     'cpuTiming=true','measureWorkload=true','measureOnly=true'):
            self.assertIn('-P'+flag,cost)

    def test_decision_preserves_both_original_denominators_and_drift(self):
        screen=prep.cost.screen
        self.assertEqual(screen([20,18.9,19.5],[16,15,16])['verdict'],'stop-no-useful-signal')
        self.assertEqual(screen([20,18,20],[16,16.1,16])['verdict'],'stop-no-useful-signal')
        self.assertEqual(screen([20,15,22.01],[16,12,16])['verdict'],'inconclusive-original-drift')
        self.assertEqual(screen([20,18,20],[16,15,16])['verdict'],'promising-diagnostic-only')
        self.assertFalse(screen([20,18,20],[16,15,16])['performanceEvidence'])

    def test_reused_config_guard_requires_actual_taau_nondefault(self):
        defaults={'TAA':'true','TAAU':'false','TAAU_RENDER_SCALE':'0.75'}
        expected=canonical_options_hash(defaults|{'TAAU':'true'})
        prepared=dict(settings={},nonshader_identities={},sidecar=dict(defaults=defaults,semantic_sha256=expected))
        import hashlib
        text='TAAU=true\n'
        snapshot=dict(complete=True,errors=[],files={'shaderpacks/photon_v1.3b.zip.txt':
            dict(utf8=text,bytes=len(text.encode()),sha256=hashlib.sha256(text.encode()).hexdigest())})
        self.assertEqual(prep.cost.qualify_configuration_snapshot(snapshot,prepared)['sidecar_semantic_sha256'],expected)
        altered=copy.deepcopy(snapshot); altered['files'].clear()
        with self.assertRaisesRegex(ValueError,'semantics changed'):
            prep.cost.qualify_configuration_snapshot(altered,prepared)
        altered=copy.deepcopy(snapshot); altered['complete']=False
        with self.assertRaisesRegex(ValueError,'Incomplete'):
            prep.cost.qualify_configuration_snapshot(altered,prepared)

    def test_postanalysis_map_delta_and_classification_are_not_inflated(self):
        plan=prep.postanalysis_plan(); text=json.dumps(plan)
        self.assertNotIn('taau90',text); self.assertNotIn('inactive0.75→0.90',text)
        self.assertIn('scale0.75 unchanged',text)
        self.assertFalse(plan['adapter_gain']); self.assertFalse(plan['shader_support_claim'])
        self.assertIn('BOTH',plan['decision_arithmetic']); self.assertIn('gross',plan['quality_first'][1])


if __name__=='__main__': unittest.main()
