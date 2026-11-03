"""Exact source launcher teardown boundaries; no JVM/client is launched."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('dev_client_retirement', Path(__file__).parents[1] / 'scripts/dev_client.py')
dev = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(dev)


class RetirementTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.session = Path(self.temp.name)
        self.proof = dict(pid=712, birth='recorded-birth', argv='java exact-gradle-launcher')
        self.row = self.proof | dict(ppid=30)
        (self.session / 'owner.json').write_text(json.dumps(dict(launcher=self.proof, launcher_session=712)))

    def run_case(self, tables, sessions=None, natural=0, post=0):
        # A stopped client and its launcher can disappear between independent censuses.
        self.enterContext(patch.object(dev, 'process_table', side_effect=tables))
        self.enterContext(patch.object(dev.os, 'getsid', side_effect=sessions or lambda_sessions()))
        self.kill = self.enterContext(patch.object(dev.os, 'kill'))
        self.enterContext(patch.object(dev.time, 'sleep'))
        self.enterContext(patch.object(dev.time, 'monotonic', side_effect=range(100)))
        return dev.retire_source_launcher(self.session, natural, post)

    def receipt(self):
        return json.loads((self.session / 'source-launcher-retirement.json').read_text())

    def test_natural_exit_after_transient_changed_identity(self):
        changed = self.row | dict(argv='java exiting-launcher')
        result = self.run_case([{712: changed}, {}], natural=3)
        self.assertTrue(result['released'])
        self.assertFalse(result['observations'][0]['identityMatches'])
        self.kill.assert_not_called()

    def test_persistent_mismatch_refuses_without_signal(self):
        changed = self.row | dict(birth='reused-pid')
        with self.assertRaisesRegex(ValueError, 'retain ownership'):
            self.run_case([{712: changed}] * 2)
        self.kill.assert_not_called()
        self.assertEqual(self.receipt()['outcome'], 'refused-retain-ownership')
        self.assertFalse(self.receipt()['observations'][-1]['identityMatches'])

    def test_changed_session_refuses_without_signal(self):
        with self.assertRaises(ValueError):
            self.run_case([{712: self.row}] * 2, sessions=[999, 999])
        self.kill.assert_not_called()
        self.assertFalse(self.receipt()['observations'][-1]['sessionMatches'])

    def test_remaining_game_refuses_even_when_launcher_absent(self):
        game = dict(pid=800, ppid=712, birth='game-birth', argv='java net.minecraft.client.main.Main')
        with self.assertRaises(ValueError):
            self.run_case([{800: game}] * 2)
        self.kill.assert_not_called()
        self.assertEqual(self.receipt()['observations'][-1]['remainingClientPids'], [800])

    def test_stable_exact_launcher_double_verified_before_signal(self):
        result = self.run_case([{712: self.row}] * 3 + [{}])
        self.kill.assert_called_once_with(712, dev.signal.SIGTERM)
        self.assertTrue(result['released'])
        self.assertTrue(result['signalSent'])
        self.assertEqual([r['stage'] for r in result['observations']],
                         ['natural-exit-grace', 'signal-verification-1', 'signal-verification-2', 'after-signal'])

    def test_changed_identity_at_second_verification_never_signals(self):
        changed = self.row | dict(argv='unrelated-java')
        with self.assertRaises(ValueError):
            self.run_case([{712: self.row}, {712: self.row}, {712: changed}])
        self.kill.assert_not_called()
        self.assertEqual(self.receipt()['observations'][-1]['stage'], 'signal-verification-2')

    def test_launcher_absent_at_second_verification_succeeds(self):
        result = self.run_case([{712: self.row}, {712: self.row}, {}])
        self.assertTrue(result['released'])
        self.kill.assert_not_called()

    def test_new_game_at_second_verification_never_signals(self):
        game = dict(pid=800, ppid=712, birth='game-birth', argv='java net.minecraft.client.main.Main')
        with self.assertRaises(ValueError):
            self.run_case([{712: self.row}, {712: self.row}, {712: self.row, 800: game}])
        self.kill.assert_not_called()
        self.assertEqual(self.receipt()['observations'][-1]['remainingClientPids'], [800])

    def test_signal_does_not_prove_release_when_launcher_persists(self):
        with self.assertRaisesRegex(ValueError, 'after bounded retirement'):
            self.run_case([{712: self.row}] * 4)
        self.kill.assert_called_once_with(712, dev.signal.SIGTERM)
        self.assertFalse(self.receipt()['released'])


def lambda_sessions():
    return lambda pid: 712


if __name__ == '__main__':
    unittest.main()
