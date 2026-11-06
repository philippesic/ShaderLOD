import contextlib
import hashlib
import io
import os
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'scripts'))
if os.name != 'nt':
    import fcntl
    import benchmark_storage as storage


@unittest.skipIf(os.name == 'nt', 'Legacy POSIX storage module; maintained Windows paths use run_storage and experiment_lifecycle')
class StorageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.source = self.base/'canonical'; self.source.mkdir(); (self.source/'level.dat').write_bytes(b'save')
        self.root = self.base/'profiles'; self.root.mkdir()
        self.world = self.root/'original/client/saves/ShaderLOD-Landscape'; self.world.mkdir(parents=True)
        (self.world/'level.dat').write_bytes(b'save'); (self.world/'session.lock').write_bytes(b'lock')
        self.report = self.base/'reports/release.json'; self.report.parent.mkdir(); self.report.write_text('{"zeroClients":true}')
        self.lock = (self.base/'lane.lock').open('a+'); self.addCleanup(self.lock.close)
        fcntl.flock(self.lock, fcntl.LOCK_EX|fcntl.LOCK_NB)
        self.manifest = storage.create_manifest(self.root, [self.source], {})
        storage.register_clone(self.root, self.world)
        self.mutate(release=dict(children_reaped=True, authorized_owner=self.manifest['owner'], lane_lock=self.lock.name))
        self.process_inventory = storage.processes
        self.proc = mock.patch.object(storage, 'processes', return_value=[]); self.proc.start(); self.addCleanup(self.proc.stop)

    def mutate(self, **fields):
        marker = self.root/storage.MARKER
        data = json.loads(marker.read_text()); data.update(fields); storage.write(marker, data)

    def clean(self, **kwargs):
        return storage.cleanup(self.root, self.manifest['owner'], [self.report], self.lock, [43210], **kwargs)

    def test_real_tempfile_dry_run_and_opted_in_cleanup_retains_source_evidence(self):
        receipt = self.clean(); self.assertEqual(receipt['status'], 'dry-run'); self.assertTrue(self.world.exists())
        self.assertEqual(receipt['clones'][0]['bytes'], 8)
        receipt = self.clean(dry_run=False)
        self.assertEqual(receipt['status'], 'complete'); self.assertFalse(self.world.exists())
        self.assertTrue((self.source/'level.dat').exists()); self.assertTrue(self.report.exists())
        self.assertTrue((self.root/storage.MARKER).exists())

    def test_destination_probe_and_exact_byte_reserve(self):
        space = mock.Mock(free=12)
        with mock.patch.object(storage.shutil, 'disk_usage', return_value=space) as usage:
            receipt = storage.capacity(self.base/'new/nested', 8, 4)
            usage.assert_called_once_with(self.base)
            self.assertEqual(receipt['required_bytes'], 12)
            with self.assertRaisesRegex(ValueError, 'Insufficient destination'):
                storage.capacity(self.base/'new', 9, 4)
        self.assertFalse((self.base/'new').exists())

    def test_missing_release_bad_owner_and_lock_refused(self):
        self.mutate(release=None)
        with self.assertRaisesRegex(ValueError, 'release'): self.clean(dry_run=False)
        self.mutate(owner='not-a-uuid')
        with self.assertRaisesRegex(ValueError, 'owner'): self.clean(dry_run=False)
        self.assertTrue(self.world.exists())

    def test_free_lane_lock_refused(self):
        fcntl.flock(self.lock, fcntl.LOCK_UN)
        with self.assertRaisesRegex(ValueError, 'hold lane lock'): self.clean(dry_run=False)
        self.assertTrue(self.world.exists())

    def test_clients_and_exact_launcher_group_refused(self):
        for row, message in [(dict(pid=44, session='44', command='java net.minecraft.client.main.Main'), 'Minecraft'),
                              (dict(pid=45, session='43210', command='Gradle child'), 'launcher')]:
            with mock.patch.object(storage, 'processes', return_value=[row]):
                with self.assertRaisesRegex(ValueError, message): self.clean(dry_run=False)
        self.assertTrue(self.world.exists())

    def test_symlinks_escape_canonical_and_evidence_inside_clone_refused(self):
        link = self.world/'link'; link.symlink_to(self.source/'level.dat')
        with self.assertRaisesRegex(ValueError, 'Symlink'): self.clean(dry_run=False)
        link.unlink()
        self.mutate(disposable_world_clones=[dict(path=str(self.source), identity=storage.identity(self.source))])
        with self.assertRaisesRegex(ValueError, 'escape'): self.clean(dry_run=False)
        storage.register_clone(self.root, self.world)
        self.mutate(disposable_world_clones=[dict(path=str(self.world), identity=storage.identity(self.world))], canonical_inputs=[str(self.world)])
        with self.assertRaisesRegex(ValueError, 'Canonical'): self.clean(dry_run=False)
        self.mutate(canonical_inputs=[str(self.source)])
        with self.assertRaisesRegex(ValueError, 'Evidence inside'):
            storage.cleanup(self.root, self.manifest['owner'], [self.world/'level.dat'], self.lock, [43210], False)

    def test_missing_evidence_identity_drift_and_inventory_limit(self):
        self.report.unlink()
        with self.assertRaisesRegex(ValueError, 'Missing retained evidence'): self.clean(dry_run=False)
        self.report.write_text('{}')
        self.mutate(root_identity=dict(device=-1, inode=-1))
        with self.assertRaisesRegex(ValueError, 'identity'): self.clean(dry_run=False)
        with mock.patch.object(storage, 'MAX_ENTRIES', 0):
            with self.assertRaisesRegex(ValueError, 'limit'): storage.inventory(self.source)

    def test_copy_inventory_cache_requires_readmitted_fingerprint(self):
        storage._WORLD_BYTES.clear()
        profile = self.base/'template'; profile.mkdir(); (profile/'options.txt').write_bytes(b'abc')
        with mock.patch.object(storage, 'inventory', wraps=storage.inventory) as scan:
            first = storage.copy_requirement(profile, self.source, 'a'*64, 3, ())
            second = storage.copy_requirement(profile, self.source, 'a'*64, 3, ())
            self.assertEqual(first['bytes'], 21); self.assertEqual(first, second)
            self.assertEqual(scan.call_count, 3)

    def test_write_failure_prevents_real_delete(self):
        with mock.patch.object(storage, 'write', side_effect=OSError('no room for evidence')):
            with self.assertRaises(OSError): self.clean(dry_run=False)
        self.assertTrue(self.world.exists())

    def test_actual_sid_catches_descendant_in_different_process_group(self):
        output = mock.Mock(stdout='800 900 java descendant\n')
        with mock.patch.object(storage.subprocess, 'run', return_value=output), \
             mock.patch.object(storage.os, 'getsid', return_value=43210) as sid, \
             mock.patch.object(storage, 'processes', side_effect=self.process_inventory):
            with self.assertRaisesRegex(ValueError, 'Active launcher session'):
                self.clean(dry_run=False)
            sid.assert_called_once_with(800)
        self.assertTrue(self.world.exists())

    def test_session_probe_exit_race_and_access_failure(self):
        output = mock.Mock(stdout='800 900 java descendant\n')
        with mock.patch.object(storage.subprocess, 'run', return_value=output):
            with mock.patch.object(storage.os, 'getsid', side_effect=ProcessLookupError):
                self.assertEqual(self.process_inventory(), [])
            with mock.patch.object(storage.os, 'getsid', side_effect=PermissionError):
                with self.assertRaisesRegex(ValueError, 'Cannot verify actual process session'):
                    self.process_inventory()



if __name__ == '__main__': unittest.main()
