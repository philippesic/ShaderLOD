"""Offline authentic control-operation contracts. No Gradle or Minecraft launch."""
import contextlib
import copy
from datetime import datetime, timezone
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
SPEC = importlib.util.spec_from_file_location('control_operation', ROOT / 'scripts/run_neoforge_control.py')
operation = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(operation)
REIMAGINED = ROOT / 'dev/logs/concept-transfer-r1/reimagined-neoforge-main/runtime-r1'
BSL = ROOT / 'dev/logs/concept-transfer-r1/bsl-neoforge-main/runtime-r1'


class OperationTests(unittest.TestCase):
    def authentic(self, session=REIMAGINED):
        if not (session / 'plan.json').is_file():
            self.skipTest('Local authentic saved plans unavailable')
        return json.loads((session / 'plan.json').read_text())

    def test_authentic_pending_reimagined_shape_and_paired_stamp(self):
        plan = self.authentic()
        before = copy.deepcopy(plan)
        family, props = operation.shape(plan, REIMAGINED)
        self.assertEqual(family, 'reimagined')
        now = datetime(2026, 10, 13, 2, 0, tzinfo=timezone.utc)
        result = operation.stamped(plan, now)
        self.assertEqual(plan, before)
        self.assertEqual(result['stop_at_utc'], '2026-10-13T02:05:30+00:00')
        stamp = str(int(datetime.fromisoformat(result['stop_at_utc']).timestamp() * 1000))
        self.assertIn('-PportStartupDeadlineEpochMillis=' + stamp, result['command'])
        self.assertFalse(result['finalCallerStampPending'])
        self.assertEqual(props['portNativeFamily'], 'reimagined-volume')

    def test_authentic_bsl_command_projection(self):
        # Historical BSL cannot relaunch. Only its actual argv projected back to
        # a pending stamp exercises the same static family grammar.
        plan = self.authentic(BSL)
        plan['stop_at_utc'] = 'PENDING_FINAL330_STAMP'
        plan['finalCallerStampPending'] = True
        plan['command'] = ['-PportStartupDeadlineEpochMillis=0' if x.startswith('-PportStartupDeadlineEpochMillis=') else x for x in plan['command']]
        self.assertEqual(operation.shape(plan, BSL)[0], 'bsl')

    def test_authentic_negative_shapes(self):
        original = self.authentic()
        cases = []
        p = copy.deepcopy(original); p['bslNeoMainControl'] = True; cases.append(p)
        p = copy.deepcopy(original); p['makeupNeoMainControl'] = True; cases.append(p)
        p = copy.deepcopy(original); p['command'].append('-PportRendererSpeedProbe=true'); cases.append(p)
        p = copy.deepcopy(original); p['command'].append('-PportStartupDeadlineEpochMillis=0'); cases.append(p)
        p = copy.deepcopy(original); p['command'] = [x for x in p['command'] if not x.startswith('-PportStartupDeadlineEpochMillis=')]; cases.append(p)
        p = copy.deepcopy(original); p['command'][3] = 'prepareClientRun'; cases.append(p)
        p = copy.deepcopy(original); p['command'].extend(['-x', 'compileJava']); cases.append(p)
        p = copy.deepcopy(original); p['command'].append('--offline'); cases.append(p)
        p = copy.deepcopy(original); p['command'] = [x.replace('portBindingInputFix=false', 'portBindingInputFix=true') for x in p['command']]; cases.append(p)
        p = copy.deepcopy(original); p['stop_at_utc'] = '2026-10-12T01:00:00Z'; cases.append(p)
        p = copy.deepcopy(original); p['manualArtifactInputs'] = str(BSL / 'actual-runClient-inputs.json'); cases.append(p)
        p = copy.deepcopy(original); p['expected_options_count'] = 384; cases.append(p)
        for number, plan in enumerate(cases):
            with self.subTest(case=number), self.assertRaises(ValueError):
                operation.shape(plan, REIMAGINED)

    def test_actual_read_only_inspection_never_spawns_or_mutates(self):
        self.authentic()
        paths = [REIMAGINED / name for name in ('plan.json', 'quiet.json', 'preparation-source-freeze.json', 'run.py', 'execute-final.py')]
        before = {p: p.read_bytes() for p in paths}
        with patch.object(operation.subprocess, 'Popen', side_effect=AssertionError('No Popen in inspection')), \
             patch.object(operation.dc, 'write_json', side_effect=AssertionError('No writes')), contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(operation.main(['--session', str(REIMAGINED)]), 0)
        result = json.loads(output.getvalue())
        self.assertFalse(result['operationFrozen'])
        self.assertFalse(result['runtimeAdmission'])
        self.assertFalse(result['stampStarted'])
        self.assertEqual(before, {p: p.read_bytes() for p in paths})

    def test_sealed_operation_cannot_execute(self):
        with patch.object(operation.subprocess, 'Popen') as spawn, self.assertRaisesRegex(ValueError, 'Fresh preparation'):
            operation.execute(REIMAGINED, {'operationFrozen': False})
        spawn.assert_not_called()

    def test_caller_hash_refuses_independent_of_frozen_hash(self):
        self.authentic()
        real_sha = operation.sha
        def changed(path):
            return '0' * 64 if path == REIMAGINED / 'run.py' else real_sha(path)
        with patch.object(operation, 'verify_pins'), patch.object(operation, 'sha', side_effect=changed), self.assertRaisesRegex(ValueError, 'Unreviewed family caller'):
            operation.inspect(REIMAGINED)

    def test_producer_exact_identity_signal_and_mismatch(self):
        proof = dict(pid=712, birth='birth', argv='exact producer')
        job = Mock(pid=712); job.poll.return_value = None
        row = dict(proof, ppid=30)
        with patch.object(operation.dc, 'process_table', side_effect=[{712: row}, {712: row}]), patch.object(operation.os, 'getsid', return_value=712):
            operation.retire_producer(job, proof, 712)
        job.terminate.assert_called_once_with()
        job.wait.assert_called_once_with(timeout=10)
        job.reset_mock()
        changed = dict(row, birth='reused')
        with patch.object(operation.dc, 'process_table', side_effect=[{712: row}, {712: changed}]), patch.object(operation.os, 'getsid', return_value=712), self.assertRaisesRegex(ValueError, 'identity changed'):
            operation.retire_producer(job, proof, 712)
        job.terminate.assert_not_called()

    def test_naturally_exited_producer_not_signaled(self):
        job = Mock(pid=712); job.poll.return_value = 1
        with patch.object(operation.dc, 'process_table') as table:
            operation.retire_producer(job, {}, 712)
        table.assert_not_called(); job.terminate.assert_not_called()

    def test_producer_timeout_uses_retained_exact_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            session = Path(directory)
            job = Mock(pid=712); job.poll.return_value = None
            job.wait.side_effect = [subprocess.TimeoutExpired(['producer'], 90), 0]
            row = dict(pid=712, birth='birth', argv='actual producer', ppid=30)
            with patch.object(operation.subprocess, 'Popen', return_value=job), patch.object(operation.dc, 'process_table', return_value={712: row}), patch.object(operation.os, 'getsid', return_value=712), self.assertRaisesRegex(ValueError, 'Producer operation failed'):
                operation.produce(['producer'], session, {'explicit_env': {}, 'stop_at_utc': 'finite'})
            job.terminate.assert_called_once_with()
            self.assertEqual(json.loads((session / 'producer-owner.json').read_text())['process']['pid'], 712)

    def test_identity_missing_retains_pid_and_never_signals(self):
        with tempfile.TemporaryDirectory() as directory:
            session = Path(directory)
            job = Mock(pid=712); job.poll.return_value = None
            job.wait.side_effect = subprocess.TimeoutExpired(['producer'], 90)
            with patch.object(operation.subprocess, 'Popen', return_value=job), patch.object(operation.dc, 'process_table', return_value={}), patch.object(operation.time, 'sleep'), self.assertRaisesRegex(ValueError, 'childExited=False'):
                operation.produce(['producer'], session, {'explicit_env': {}, 'stop_at_utc': 'finite'})
            job.terminate.assert_not_called()
            owner = json.loads((session / 'producer-owner.json').read_text())
            self.assertEqual(owner['pid'], 712)
            self.assertIsNone(owner['process'])
            self.assertFalse(owner['retainedChildExited'])
            job.wait.assert_called_once_with(timeout=90)

    def test_owner_write_failure_waits_retained_child_without_signal(self):
        with tempfile.TemporaryDirectory() as directory:
            job = Mock(pid=712); job.poll.return_value = 0
            with patch.object(operation.subprocess, 'Popen', return_value=job), patch.object(operation.dc, 'write_json', side_effect=OSError('disk failed')), self.assertRaisesRegex(ValueError, 'retained PID=712'):
                operation.produce(['producer'], Path(directory), {'explicit_env': {}, 'stop_at_utc': 'finite'})
            job.wait.assert_called_once_with(timeout=90)
            job.terminate.assert_not_called()

    def test_real_harmless_producer_success_and_failure(self):
        # Real Python subprocesses exercise Popen -> native ps identity -> wait
        # -> receipt. No launcher, Gradle, game, or profile is involved.
        for exit_code in (0, 7):
            with self.subTest(exit_code=exit_code), tempfile.TemporaryDirectory() as directory:
                session = Path(directory)
                command = [sys.executable, '-c', 'import time; time.sleep(0.3); raise SystemExit(' + str(exit_code) + ')']
                if exit_code:
                    with self.assertRaisesRegex(ValueError, 'Producer failed: exit 7'):
                        operation.produce(command, session, {'explicit_env': {}, 'stop_at_utc': 'test-only'})
                    self.assertTrue(json.loads((session / 'producer-owner.json').read_text())['retainedChildExited'])
                else:
                    proof = operation.produce(command, session, {'explicit_env': {}, 'stop_at_utc': 'test-only'})
                    self.assertNotIn(proof['pid'], operation.dc.process_table())
                    self.assertEqual(json.loads((session / 'producer-owner.json').read_text())['process'], proof)

    def test_runtime_failure_stops_then_retires_exact_owner(self):
        # Execute the real operation's error path using temporary receipt IO,
        # authentic command shape, and a failed family caller result.
        plan = self.authentic()
        with tempfile.TemporaryDirectory() as directory:
            session = Path(directory)
            observation = dict(operationFrozen=True, quietAcknowledged=True, plan=plan, pins={})
            events = []
            def run(argv, **kwargs):
                if '--preflight' in argv:
                    events.append('preflight'); return subprocess.CompletedProcess(argv, 0, '', '')
                events.append('caller')
                (session / 'owner.json').write_text(json.dumps({'clients': {'712': {'pid': 712}}}))
                return subprocess.CompletedProcess(argv, 1)
            with patch.object(operation, 'inspect', return_value=observation), patch.object(operation, 'static_inputs', return_value={}), \
                 patch.object(operation, 'produce', return_value={'pid': 700}), patch.object(operation, 'freeze_generated'), \
                 patch.object(operation.dc, 'runtime_lock', side_effect=lambda: contextlib.nullcontext()), \
                 patch.object(operation.dc, 'process_table', return_value={}), patch.object(operation.subprocess, 'run', side_effect=run), \
                 patch.object(operation.dc, 'stop', side_effect=lambda s: events.append('stop')), \
                 patch.object(operation.dc, 'retire_source_launcher', side_effect=lambda s: events.append('retire')), \
                 self.assertRaisesRegex(ValueError, 'Finite caller failed'):
                operation.execute(session, observation)
            self.assertEqual(events, ['preflight', 'caller', 'stop', 'retire'])
            self.assertFalse((session / 'final-admission.json').exists())

    def test_existing_release_is_preserved_after_independent_absence_check(self):
        with tempfile.TemporaryDirectory() as directory:
            session = Path(directory)
            (session / 'owner.json').write_text('{"clients":{"712":{}},"launcher":{"pid":700}}')
            (session / 'release.json').write_text('{"zeroClients":true}')
            with patch.object(operation.dc, 'process_table', return_value={}), patch.object(operation.dc, 'stop') as stop, patch.object(operation.dc, 'retire_source_launcher') as retire:
                operation.cleanup_caller(session)
            stop.assert_not_called(); retire.assert_not_called()
            self.assertEqual((session / 'release.json').read_text(), '{"zeroClients":true}')

    def test_stop_refusal_never_proceeds_to_launcher_retirement(self):
        with tempfile.TemporaryDirectory() as directory:
            session = Path(directory)
            (session / 'owner.json').write_text('{"clients":{"712":{}}}')
            with patch.object(operation.dc, 'stop', side_effect=ValueError('changed identity')), patch.object(operation.dc, 'retire_source_launcher') as retire, self.assertRaisesRegex(ValueError, 'changed identity'):
                operation.cleanup_caller(session)
            retire.assert_not_called()


if __name__ == '__main__':
    unittest.main()
