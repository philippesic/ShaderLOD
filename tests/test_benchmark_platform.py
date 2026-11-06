import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
from types import SimpleNamespace

SCRIPTS = Path(__file__).resolve().parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
from benchmark_file_locks import world_lock, replace_published, read_published_json
import benchmark_file_locks as file_locks
import benchmark_bliss_qualification as qualification
import dev_client as dev
import benchmark_iteration as iteration


class BenchmarkPlatformTests(unittest.TestCase):
    def test_ownership_read_retries_windows_sharing_but_not_bad_json(self):
        error=PermissionError('sharing');error.winerror=5
        with patch.object(file_locks,'os',SimpleNamespace(name='nt')), patch.object(file_locks.time,'sleep'):
            with patch.object(Path,'read_text',side_effect=[error, '{"status":"ready"}']) as read:
                self.assertEqual(read_published_json('owner.json'),{'status':'ready'})
                self.assertEqual(read.call_count,2)
            with patch.object(Path,'read_text',return_value='{broken') as read:
                with self.assertRaises(json.JSONDecodeError):read_published_json('owner.json')
                self.assertEqual(read.call_count,1)

    def test_ownership_read_bounds_persistent_windows_denial(self):
        error=PermissionError('access denied');error.winerror=5
        with patch.object(file_locks,'os',SimpleNamespace(name='nt')), patch.object(file_locks.time,'monotonic',side_effect=[0,1,4]), patch.object(file_locks.time,'sleep'):
            with patch.object(Path,'read_text',side_effect=error) as read:
                with self.assertRaises(PermissionError):read_published_json('owner.json')
                self.assertEqual(read.call_count,2)
        with patch.object(file_locks,'os',SimpleNamespace(name='posix')), patch.object(Path,'read_text',side_effect=error) as read:
            with self.assertRaises(PermissionError):read_published_json('owner.json')
            self.assertEqual(read.call_count,1)

    @unittest.skipUnless(os.name == 'nt', 'Native Windows sharing check')
    def test_publication_survives_brief_reader_lock(self):
        import threading
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'record.json';source=Path(temp)/'new.tmp'
            path.write_text('old');source.write_text('new')
            stream=path.open('rb')
            release=threading.Timer(.15,stream.close);release.start()
            try:replace_published(source,path)
            finally:release.join();stream.close()
            self.assertEqual(path.read_text(),'new')
            self.assertFalse(source.exists())

    def test_world_contents_restore_retains_live_lock(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);source=root/'source';target=root/'target'
            for base in (source,target):
                (base/'World/region').mkdir(parents=True)
            (source/'World/level.dat').write_bytes(b'original')
            (source/'World/region/one.mca').write_bytes(b'original region')
            (target/'World/level.dat').write_bytes(b'changed')
            (target/'World/region/new.mca').write_bytes(b'new region')
            lock=target/'World/session.lock';lock.write_bytes(b'lock')
            with world_lock(lock):
                with patch.object(iteration.storage,'MIN_FREE',0):
                    iteration.restore_locked_world_contents(source,target)
            self.assertEqual(lock.read_bytes(),b'lock')
            self.assertEqual(iteration.tree_map(source,save_locks=True),iteration.tree_map(target,save_locks=True))

    def test_windows_launcher_retirement_requires_absence_and_never_signals(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            proof = dict(pid=42, birth='recorded', argv='cmd.exe /c owned.bat')
            (root/'owner.json').write_text(json.dumps({'launcher': proof, 'launcher_session': None}))
            with patch.object(dev, 'os', SimpleNamespace(name='nt')):
                with patch.object(dev, 'process_table', return_value={}):
                    result = dev.retire_source_launcher(root, natural_exit_grace=0)
                    self.assertTrue(result['released'])
                    self.assertFalse(result['signalSent'])
                with patch.object(dev, 'process_table', return_value={42: proof | {'ppid': 1}}):
                    with self.assertRaisesRegex(ValueError, 'remains'):
                        dev.retire_source_launcher(root, natural_exit_grace=0)
                with patch.object(dev, 'process_table', return_value={42: proof | {'birth': 'reused', 'ppid': 1}}):
                    with self.assertRaisesRegex(ValueError, 'identity changed'):
                        dev.retire_source_launcher(root, natural_exit_grace=0)

    def test_world_lock_excludes_other_process_and_releases(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'session.lock'
            path.write_bytes(b'lock')
            code = ('import sys;from pathlib import Path;sys.path.insert(0,sys.argv[1]);'
                    'from benchmark_file_locks import world_lock;'
                    'lock=world_lock(Path(sys.argv[2]));lock.__enter__();lock.__exit__(None,None,None)')
            def contender():
                return subprocess.run([sys.executable, '-c', code, str(SCRIPTS), str(path)],
                                      capture_output=True, text=True)
            with world_lock(path):
                self.assertNotEqual(contender().returncode, 0)
                # The shared lock file can move; its parent remains in place.
                moved = path.with_name('moved.lock')
                path.rename(moved)
                moved.rename(path)
            self.assertEqual(contender().returncode, 0)
            self.assertEqual(path.read_bytes(), b'lock')

    def test_windows_fault_delta_does_not_invent_linux_counters(self):
        before = dict(started_ns=1, finished_ns=2, process=dict(pid=1, start_ticks=7, page_faults=10),
                      pressure={}, swap_pages={}, page_bytes=None)
        after = before | dict(started_ns=1000000002, process=dict(pid=1, start_ticks=7, page_faults=12))
        result = qualification.memory_delta(before, after)
        self.assertEqual(result['client_fault_deltas'], {'page_faults': 2})
        self.assertEqual(result['host_swap_bytes'], {})
        with self.assertRaisesRegex(ValueError, 'process changed'):
            qualification.memory_delta(before, after | {'process': dict(pid=1, start_ticks=8, page_faults=12)})
        with self.assertRaisesRegex(ValueError, 'counters reset'):
            qualification.memory_delta(before, after | {'process': dict(pid=1, start_ticks=7, page_faults=9)})

    @unittest.skipUnless(os.name == 'nt', 'Native Windows API check')
    def test_native_windows_memory_endpoints(self):
        before = qualification.memory_observation(os.getpid())
        time.sleep(.02)
        after = qualification.memory_observation(os.getpid())
        result = qualification.memory_delta(before, after)
        self.assertGreater(before['memory']['MemAvailable_kib'], 0)
        self.assertGreater(before['process']['VmRSS_kib'], 0)
        self.assertGreaterEqual(result['client_fault_deltas']['page_faults'], 0)


if __name__ == '__main__':
    unittest.main()
