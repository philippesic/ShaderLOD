"""Offline checks; authentic saved plan schema, temporary files, no client/build."""
import copy
from datetime import datetime, timedelta, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
SPEC = importlib.util.spec_from_file_location('preflight', ROOT / 'scripts/check_experiment_plan.py')
pre = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pre)
FIXTURE = ROOT / 'tests/fixtures/experiment-plan/reimagined-interval-cue-r1.json'


class ExperimentPlanTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.now = datetime(2026, 10, 10, 20, tzinfo=timezone.utc)
        self.p = json.loads(FIXTURE.read_text())
        old_root = '/Users/pippo/github/shaderlod'
        # Relocate the real argv/paths; only dependent filesystem contents are small stand-ins.
        self.p = json.loads(json.dumps(self.p).replace(old_root, str(self.root)))
        profile = Path(self.p['profile'])
        for name in ('config', 'mods', 'shaderpacks', 'saves/' + pre.dc.WORLD):
            (profile / name).mkdir(parents=True, exist_ok=True)
        (profile / 'saves' / pre.dc.WORLD / 'level.dat').write_bytes(b'not a Minecraft world')
        (profile / 'config/iris.properties').write_text('shaderPack=' + self.p['pack_name'] + '\n')
        (self.root / 'gradlew').write_text('not executable; checker never launches')
        (self.root / 'dev').mkdir(exist_ok=True)
        (self.root / 'dev/performance-mods.lock.json').write_text(json.dumps({'stacks': {'minimal': [], 'optimized': [], 'optimized-culling': []}}))
        (self.root / 'dev/runtime.lock.json').write_text(json.dumps({'artifacts': {'distanthorizons': {'filename': 'dh.jar'}}}))
        for pack in (Path(self.p['pack']), profile / 'shaderpacks/ComplementaryReimagined_r5.9.3.zip'):
            pack.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(pack, 'w') as z:
                z.writestr('shaders/shaders.properties', 'screen=MODE\n')
                z.writestr('shaders/composite.fsh', 'void main() {}')
        self.p['expected_loaded_sha256'] = hashlib.sha256(Path(self.p['pack']).read_bytes()).hexdigest()
        options = Path(self.p['pack_options'])
        options.parent.mkdir(parents=True, exist_ok=True)
        options.write_text('MODE=3\n')
        Path(self.p['expected_options_source']).write_text(json.dumps({'MODE': '3', 'NATIVE': 'true'}))
        self.p['expected_options_count'] = 2
        (profile.parent / 'control.json').write_text(json.dumps(self.p['scene_input']))
        review = Path(self.p['sourceReview'])
        review.parent.mkdir(parents=True, exist_ok=True)
        review.write_text('Small stand-in for a pinned source review, not source admission.')
        self.freeze = {'files': {str(review): hashlib.sha256(review.read_bytes()).hexdigest()}}
        self.p['stop_at_utc'] = (self.now + timedelta(seconds=120)).isoformat()
        self.root_patch = patch.object(pre.dc, 'ROOT', self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)

    def check(self, p=None, **kwargs):
        return pre.check_plan(p or self.p, freeze=self.freeze, now=self.now, **kwargs)

    def test_real_record_shape_and_owner_wrapper(self):
        self.assertEqual(self.check()['errors'], [])
        self.assertEqual(self.check({'plan': self.p, 'status': 'pending'})['errors'], [])
        self.assertIn('actual JVM/argfile consumption', self.check()['unverified'])

    def test_independent_errors_reported_together(self):
        p = copy.deepcopy(self.p)
        p['stop_at_utc'] = None
        p['ready_timeout'] = 1000
        p['profile'] = str(self.root / 'missing')
        p['pack'] = str(self.root / 'missing.zip')
        p['command'] += ['-PincludeDh=true']
        p['expected_options_count'] = 400
        self.freeze['files'] = {}
        errors = {e['check'] for e in self.check(p)['errors']}
        self.assertTrue({'finite_deadline', 'ready_timeout', 'profile_world_iris', 'shader_archive',
                         'launcher_command', 'expected_options', 'source_review_pin'}.issubset(errors))

    def test_authentic_null_deadline_and_unpinned_review(self):
        # The archived plan has both defects; no repaired fixture is mislabeled original.
        raw = json.loads(FIXTURE.read_text())
        self.assertIsNone(raw['stop_at_utc'])
        self.assertIn('photon-display', raw['sourceReview'])
        self.p['stop_at_utc'] = raw['stop_at_utc']
        self.freeze['files'] = {}
        errors = {e['check'] for e in self.check()['errors']}
        self.assertEqual(errors, {'finite_deadline', 'source_review_pin'})

    def test_deadline_margin_and_historical_scope(self):
        self.p['stop_at_utc'] = (self.now + timedelta(seconds=75)).isoformat()
        self.assertIn('finite_deadline', {e['check'] for e in self.check()['errors']})
        self.p['stop_at_utc'] = (self.now - timedelta(seconds=5)).isoformat()
        self.assertEqual(self.check(historical=True)['errors'], [])
        self.assertEqual(self.check(historical=True)['scope'], 'historical static inspection')

    def test_command_profile_and_task_conflicts(self):
        self.p['command'][1] = 'runClientLodOff'
        self.assertIn('launcher_command', {e['check'] for e in self.check()['errors']})

    def test_performance_stack_uses_existing_lock(self):
        self.p['command'] = ['-PperformanceStack=full' if a.startswith('-PperformanceStack=') else a
                             for a in self.p['command']]
        errors = {e['check']: e['error'] for e in self.check()['errors']}
        self.assertEqual(set(errors), {'launcher_command'})
        self.assertIn('Unknown performanceStack: full', errors['launcher_command'])
        self.p['command'] = ['-PperformanceStack=optimized' if a.startswith('-PperformanceStack=') else a
                             for a in self.p['command']]
        self.assertEqual(self.check()['errors'], [])
        self.assertIn('optional runtime artifact availability/hashes', self.check()['unverified'])

    def test_named_capture_collision_and_original_archive(self):
        destination = next(a.split('=', 1)[1] for a in self.p['command'] if a.startswith('-PcontrolCaptureReceipt='))
        target = Path(destination) / (self.p['protocol_capture_name'] + '.json')
        target.parent.mkdir(parents=True)
        target.write_text('{}')
        self.assertIn('named_capture', {e['check'] for e in self.check()['errors']})
        self.assertEqual(self.check(historical=True)['errors'], [])

    def test_capture_destination_file_and_independent_deadline_defect(self):
        self.p['command'] = [('-PcontrolCaptureReceipt=' + str(self.root / 'gradlew'))
            if a.startswith('-PcontrolCaptureReceipt=') else a for a in self.p['command']]
        self.p['stop_at_utc'] = None
        errors = {e['check']: e['error'] for e in self.check()['errors']}
        self.assertEqual(set(errors), {'named_capture', 'finite_deadline'})
        self.assertIn('not a directory', errors['named_capture'])

    def test_capture_destination_missing_ancestor_and_access(self):
        self.p['command'] = [('-PcontrolCaptureReceipt=' + str(self.root / 'gradlew/missing'))
            if a.startswith('-PcontrolCaptureReceipt=') else a for a in self.p['command']]
        self.assertIn('named_capture', {e['check'] for e in self.check()['errors']})
        self.p['command'] = [('-PcontrolCaptureReceipt=' + str(self.root / 'new/captures'))
            if a.startswith('-PcontrolCaptureReceipt=') else a for a in self.p['command']]
        self.assertEqual(self.check()['errors'], [])
        with patch.object(pre.os, 'access', return_value=False):
            self.assertIn('named_capture', {e['check'] for e in self.check()['errors']})
        self.assertFalse((self.root / 'new').exists())

    def test_receipt_exact_fields_and_mismatch_batch(self):
        receipt = dict(status='saved-observations', irisLoadedPack=self.p['pack_name'],
                       irisSelectedPack=self.p['pack_name'], selectedArchiveSha256=self.p['expected_loaded_sha256'],
                       loadedIrisOptions={'MODE': '3', 'NATIVE': 'true'}, irisFallback=False,
                       shaderPackInUse=True, fullscreen=True, focused=True,
                       framebufferWidth=1920, framebufferHeight=1200)
        self.assertEqual(self.check(receipt=receipt)['errors'], [])
        receipt.update(focused=False, loadedIrisOptions={})
        error = self.check(receipt=receipt)['errors'][0]['error']
        self.assertIn('focused', error)
        self.assertIn('loadedIrisOptions', error)

    def test_authentic_receipt_schema(self):
        receipt = json.loads((FIXTURE.parent / 'reimagined-control-receipt-fields-r1.json').read_text())
        self.assertEqual(receipt['status'], 'saved-observations')
        self.assertEqual(len(receipt['loadedIrisOptions']), 399)
        Path(self.p['expected_options_source']).write_text(json.dumps(receipt['loadedIrisOptions']))
        self.p['expected_options_count'] = 399
        # Rebase only the expected hash to the small temporary archive.
        receipt['selectedArchiveSha256'] = self.p['expected_loaded_sha256']
        self.assertEqual(self.check(receipt=receipt)['errors'], [])

    def test_no_mutation_or_process_calls(self):
        before = {p: p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        with patch.object(pre.dc, 'prepare', side_effect=AssertionError('mutation')), \
             patch.object(pre.dc, 'process_table', side_effect=AssertionError('process inspection')), \
             patch.object(pre.dc, 'supervise', side_effect=AssertionError('launch')):
            self.assertEqual(self.check()['errors'], [])
        after = {p: p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual(before, after)

    def test_malformed_records_refused_without_traceback(self):
        for record in (None, [], {'plan': []}):
            self.assertTrue(pre.check_plan(record)['errors'])
        self.p['command'] = None
        self.assertIn('launcher_command', {e['check'] for e in self.check()['errors']})

    def test_optional_build_reuses_existing_api(self):
        with patch.object(pre.dc, 'validate_built') as validate:
            self.assertEqual(self.check(check_built=True)['errors'], [])
            validate.assert_called_once_with()


if __name__ == '__main__':
    unittest.main()
