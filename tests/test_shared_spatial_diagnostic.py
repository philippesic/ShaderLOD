"""Pure diagnostic runner acceptance without launching a client."""
import copy
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest import mock

ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
import run_shared_spatial_diagnostic as runner


class DiagnosticTests(unittest.TestCase):
    def test_probe_retains_actual_zero_one_and_unknown_without_stage_inference(self):
        probe=dict(schema=1,capability_uniform_schema=1,programs=[dict(current_program=18,
            active_texture_before=33984,active_texture_after=33984,capability_uniforms={
                'zero':dict(status='queried_without_gl_error_consumption',value=0),
                'one':dict(status='queried_without_gl_error_consumption',value=1),'missing':dict(status='unknown')})])
        result=runner.probe_summary(probe)
        self.assertEqual(result['programs'][0]['capability_uniforms'],probe['programs'][0]['capability_uniforms'])
        self.assertFalse(result['performanceEvidence']);self.assertIn('unverified',result['stage_mapping'])
        changed=copy.deepcopy(probe);changed['programs'][0]['active_texture_after']=33985
        with self.assertRaisesRegex(ValueError,'restoration'):runner.probe_summary(changed)
        changed=copy.deepcopy(probe);changed['programs'][0]['capture_failure']='failed'
        with self.assertRaisesRegex(ValueError,'capture failure'):runner.probe_summary(changed)

    def test_held_command_mutation_rejected_before_file_access(self):
        with self.assertRaisesRegex(ValueError,'Held command drift'):
            runner.guard_profile(dict(profile='/unused',command=['./gradlew','build']))

    def test_owned_cwd_and_daemon_parent_are_explicit_live_evidence(self):
        profile=ROOT/'run-workloads/unique-arm';pid=123
        identity='123 30675 30675 Tue Oct 6 01:00:00 2026 java devlaunch.Main'
        with mock.patch.object(runner,'process_identity',return_value=identity),mock.patch.object(runner.subprocess,'run',
                return_value=SimpleNamespace(returncode=0,stdout='p123\nn'+str(profile)+'\n')) as query:
            proof=runner.prove_client_ownership(pid,profile)
        self.assertEqual(proof['status'],'owned');self.assertEqual(proof['association'],'cwd')
        self.assertEqual(proof['identity'],identity)
        self.assertEqual(query.call_args.args[0],['/usr/sbin/lsof','-a','-p','123','-d','cwd','-Fn'])

    def test_exact_open_profile_log_can_prove_cwd_unavailable_client(self):
        profile=ROOT/'run-workloads/unique-arm';pid=123
        replies=[SimpleNamespace(returncode=1,stdout=''),SimpleNamespace(returncode=0,stdout='p123\nn'+str(profile/'logs/latest.log')+'\n')]
        with mock.patch.object(runner,'process_identity',return_value='same'),mock.patch.object(runner.subprocess,'run',side_effect=replies) as query:
            proof=runner.prove_client_ownership(pid,profile)
        self.assertEqual(proof['status'],'owned');self.assertEqual(proof['association'],'open_exact_profile_log_or_world_lock')
        self.assertEqual(query.call_args.args[0][-2:],[str(profile/'logs/latest.log'),str(profile/'saves'/runner.prep.WORLD/'session.lock')])

    def test_foreign_ambiguous_wrong_pid_and_identity_race_are_unowned(self):
        profile=ROOT/'run-workloads/unique-arm'
        for reply,identities in ((SimpleNamespace(returncode=0,stdout='p123\nn/foreign/client\n'),['same','same']),
                                 (SimpleNamespace(returncode=1,stdout=''),['same','same']),
                                 (SimpleNamespace(returncode=0,stdout='p999\nn'+str(profile)+'\n'),['same','same']),
                                 (SimpleNamespace(returncode=0,stdout='p123\nn'+str(profile)+'\n'),['old-start','new-start'])):
            with self.subTest(reply=reply.stdout,identities=identities):
                with mock.patch.object(runner,'process_identity',side_effect=identities),mock.patch.object(runner.subprocess,'run',return_value=reply):
                    self.assertEqual(runner.prove_client_ownership(123,profile)['status'],'ambiguous')

    def test_foreign_discovery_stops_without_tracking_or_signaling(self):
        owned={};events=[]
        with mock.patch.object(runner.prep.matrix,'minecraft_processes',return_value=[123]),mock.patch.object(runner,'prove_client_ownership',
                return_value=dict(pid=123,status='ambiguous',reason='foreign')),mock.patch.object(runner.os,'kill') as kill:
            with self.assertRaisesRegex(ValueError,'Foreign or ambiguous'):runner.discover_clients('/own',owned,events)
            runner.signal_owned_clients(owned,runner.signal.SIGTERM,events)
        self.assertFalse(owned);kill.assert_not_called();self.assertEqual(events[0]['pid'],123)

    def test_cleanup_signals_only_reproved_owned_pid_never_shared_group(self):
        proof=dict(pid=123,status='owned',identity='old-start',profile='/own')
        with mock.patch.object(runner,'process_identity',return_value='old-start'),mock.patch.object(runner,'prove_client_ownership',
                return_value=proof),mock.patch.object(runner.os,'kill') as kill,mock.patch.object(runner.os,'killpg') as killpg:
            runner.signal_owned_clients({123:proof},runner.signal.SIGTERM,[])
        kill.assert_called_once_with(123,runner.signal.SIGTERM);killpg.assert_not_called()

    def test_reused_or_no_longer_associated_pid_is_not_signaled_or_readopted(self):
        proof=dict(pid=123,status='owned',identity='old-start',profile='/own')
        for identity,new_proof in (('new-start',proof),('old-start',proof|{'status':'ambiguous'})):
            with self.subTest(identity=identity,new_status=new_proof['status']):
                with mock.patch.object(runner,'process_identity',return_value=identity),mock.patch.object(runner,'prove_client_ownership',
                        return_value=new_proof),mock.patch.object(runner.os,'kill') as kill:
                    runner.signal_owned_clients({123:proof},runner.signal.SIGKILL,[])
                kill.assert_not_called()
        owned={123:proof};events=[]
        with (mock.patch.object(runner.prep.matrix,'minecraft_processes',return_value=[123]),
              mock.patch.object(runner,'process_identity',return_value='new-start'),
              mock.patch.object(runner,'prove_client_ownership',return_value=proof|{'identity':'new-start'})):
            with self.assertRaisesRegex(ValueError,'reused/identity changed'):runner.discover_clients('/own',owned,events)
        self.assertEqual(owned[123]['identity'],'old-start')

if __name__=='__main__':unittest.main()
