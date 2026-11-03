import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_workflow as workflow
import run_benchmark as runner
import linux_arm64_runtime as arm

class WorkflowTests(unittest.TestCase):
    def test_plan_never_builds_or_launches_and_keeps_standard_schedule(self):
        with patch.object(runner,'preflight',return_value={'errors':[{'check':'existing_build_binding'}]}), patch.object(runner.dev,'compile_current') as build, patch.object(runner,'run_operation') as run, redirect_stdout(io.StringIO()) as out:
            self.assertEqual(1,runner.main(['plan','--pack','/tmp/original.zip','--output','/tmp/fresh']))
        result=json.loads(out.getvalue())
        self.assertFalse(result['launched']);self.assertTrue(result['build_needed'])
        self.assertEqual(120,result['schedule']['conditioning_seconds'])
        build.assert_not_called();run.assert_not_called()

    def test_historical_drift_and_recovery_take_precedence_over_positive_mean(self):
        data={'configuration_restored':True,'evaluation':{'validity':'VALID','improvement':'OBSERVED_LOWER_FRAME_TIME','drift_comparison':'INCONCLUSIVE_WITHIN_NATIVE_DRIFT'}}
        self.assertEqual('INCONCLUSIVE_WITHIN_NATIVE_DRIFT',workflow.outcome(data)['decision'])
        data['release_error']='conflicting bytes'
        self.assertEqual('RECOVERY_REQUIRED',workflow.outcome(data)['decision'])
        data.pop('release_error');data['evaluation']['validity']='INVALID'
        self.assertEqual('INVALID',workflow.outcome(data)['decision'])

    def test_discovery_preserves_malformed_slot_and_does_not_claim_compatibility(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);slot=root/'run-workloads/world-pool/one';slot.mkdir(parents=True)
            marker=slot/'workspace.json';marker.write_text('{')
            self.assertIn('error',workflow.discover(root)['slots'][0])
            marker.write_text('{"source":"canonical"}')
            row=workflow.discover(root)['slots'][0]
            self.assertFalse(row['exists']);self.assertEqual('canonical',row['source'])

    def test_private_validator_selection_preserves_specialized_lean_variant(self):
        from types import SimpleNamespace
        self.assertIs(runner.bliss_zero,runner.private_consumer(SimpleNamespace(candidate='n04-bliss-zero')))
        self.assertIs(runner.bliss_zero_lean,runner.private_consumer(SimpleNamespace(candidate='n04-bliss-zero',bliss_zero_lean=True)))
        self.assertIs(runner.photon_pbr,runner.private_consumer(SimpleNamespace(candidate='n02-photon-pbr-resources')))

    def test_explicit_incomplete_jdk_is_not_silently_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError,'full JDK21'):arm.java_home(directory)

class ProfileReadinessTests(unittest.TestCase):
    def test_changed_bytes_and_modes_are_reported_without_reset(self):
        import benchmark_iteration as iteration
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);source=root/'canonical';slot=root/'slot';profile=slot/'client'
            for p in (source,profile):
                (p/'config').mkdir(parents=True);(p/'config/options').write_bytes(b'original')
            (slot/'workspace.json').write_text(json.dumps({'source':str(source)}))
            self.assertTrue(iteration.check_profile_inputs(profile)['matching'])
            (profile/'config/options').write_bytes(b'valuable change')
            with self.assertRaisesRegex(ValueError,'config/options'):iteration.check_profile_inputs(profile)
            self.assertEqual(b'valuable change',(profile/'config/options').read_bytes())
            (profile/'config/options').write_bytes(b'original')
            (profile/'config/options').chmod(0o600)
            (source/'config/options').chmod(0o644)
            with self.assertRaisesRegex(ValueError,'modes'):iteration.check_profile_inputs(profile)

    def test_ongoing_report_does_not_instruct_blind_recovery(self):
        self.assertEqual('RUNNING_OR_INTERRUPTED',workflow.outcome({'cost_seconds':{}})['decision'])

class ApplyReadinessTests(unittest.TestCase):
    def test_reuses_only_unchanged_current_rendered_apply_boundary(self):
        from unittest.mock import Mock
        prior={'pipeline_identity':'p','pack_identity':'q','dimension':'minecraft:overworld','width':2560,'height':1600,'world_loaded':True,'meshes_ready':True,'fallback':False,'render_frames':10}
        control=Mock();control.acknowledged_ready=prior;control.reused_apply_readiness=0
        state=prior|{'render_frames':11};control.send.return_value={'state':state}
        self.assertEqual(state,runner.ready_after_apply(control));control.ready.assert_not_called()
        for delta in ({'pipeline_identity':'different'},{'dimension':'minecraft:nether'},{'pipeline_identity':None},{'meshes_ready':False},{'fallback':True},{'render_frames':10},{'world_loaded':False}):
            control.reset_mock();control.send.return_value={'state':state|delta}
            runner.ready_after_apply(control);control.ready.assert_called_once()

class FunctionalCommandTests(unittest.TestCase):
    def test_check_uses_same_owned_runner_without_performance_schedule(self):
        with patch.object(runner,'preflight',return_value={'errors':[]}),patch.object(runner,'run_operation',return_value=0) as run:
            self.assertEqual(0,runner.main(['check','--pack','/tmp/original.zip','--output','/tmp/fresh']))
        a=run.call_args.args[0]
        self.assertTrue(a.functional_check and a.standard_test)
        self.assertEqual((3,0,5),(a.seconds,a.conditioning,a.warmup))

    def test_frozen_qualification_cannot_be_shortened(self):
        with patch.object(runner,'preflight') as preflight,redirect_stdout(io.StringIO()):
            self.assertEqual(1,runner.main(['check','--pack','/tmp/original.zip','--output','/tmp/fresh','--bliss-qualification','null']))
        preflight.assert_not_called()

class FunctionalEvaluationTests(unittest.TestCase):
    def test_short_check_cannot_be_reinterpreted_as_performance_by_shared_evaluator(self):
        import benchmark_iteration as iteration
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'summary.json').write_text('{"purpose":"functional-check"}')
            result=iteration.evaluate(root,execution_completed=True,restored=True)
            self.assertEqual('NOT_EVALUATED',result['improvement']);self.assertNotIn('speedup_ratio',result)
            self.assertEqual('INVALID',iteration.evaluate(root,execution_completed=True,restored=False)['validity'])

class MissingCanonicalTests(unittest.TestCase):
    def test_orphaned_slot_does_not_masquerade_as_changed_inputs(self):
        import benchmark_iteration as iteration
        with tempfile.TemporaryDirectory() as directory:
            slot=Path(directory)/'slot';profile=slot/'client';profile.mkdir(parents=True)
            (slot/'workspace.json').write_text(json.dumps({'source':str(Path(directory)/'missing')}))
            with self.assertRaisesRegex(ValueError,'Canonical source is missing'):
                iteration.check_profile_inputs(profile)
            self.assertTrue(profile.is_dir())

class CompiledCapabilityTests(unittest.TestCase):
    def test_obsolete_controller_is_rejected_before_launch(self):
        binary=runner.ROOT/'build/classes/java/devClient/com/pp/shaderlod/dev/BenchmarkControlProbe.class'
        if not binary.is_file():self.skipTest('Built developer controller required for binary preflight')
        original=binary.read_bytes();token=b'shaderlod.dev.privateObservedReload'
        if token not in original:self.skipTest('Current developer capability not built')
        read_bytes=Path.read_bytes
        def stale(path):
            return original.replace(token,b'legacy-disabled-capability') if path==binary else read_bytes(path)
        with tempfile.TemporaryDirectory(dir=runner.ROOT) as directory:
            a=runner.arguments(['--pack',str(Path(directory)/'missing.zip'),'--output',str(Path(directory)/'report'),'--candidate','n04-sildur-underwater-ao'])
            a.standard_test=True;a.suite='public-smoke';a.arm='master-off';a.seconds=20;a.warmup=5;a.conditioning=120
            with patch.object(Path,'read_bytes',stale):result=runner.preflight(a)
            errors=[e for e in result['errors'] if e['check']=='compiled_control_hooks']
            self.assertEqual(1,len(errors));self.assertIn('privateObservedReload',errors[0]['error'])
            self.assertFalse(Path(directory,'report').exists())
        self.assertEqual(original,binary.read_bytes())
