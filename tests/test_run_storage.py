"""Exercise actual temporary copies, resets, locks and destructive retention."""
import ctypes
import errno
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import dev_client
import run_storage as storage


class RunStorageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=storage.ROOT)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.source = self.root / 'canonical/client'
        self.world = self.source / 'saves/ShaderLOD-Landscape'
        self.world.mkdir(parents=True)
        (self.world / 'level.dat').write_bytes(b'canonical save')
        (self.world / 'session.lock').write_bytes(b'lock')
        (self.source / 'config').mkdir()
        (self.source / 'config/iris.properties').write_text('shaderPack=Original.zip')
        (self.source.parent / 'control.json').write_text('{"enabled":false}')
        for name in ('camera-quality', 'screenshots', 'backups', 'save-backups'):
            (self.source / name).mkdir()
            (self.source / name / 'old-output').write_bytes(b'not an input')
        (self.source / 'workload-measurement.json').write_text('{"stale":true}')
        for obj, name, value in ((storage, 'ROOT', self.root),
                                 (dev_client, 'LOCK', self.root / 'lane.lock')):
            mock = patch.object(obj, name, value); mock.start(); self.addCleanup(mock.stop)
        mock = patch.object(dev_client, 'process_table', return_value={})
        mock.start(); self.addCleanup(mock.stop)

    def test_actual_copy_has_independent_world_and_excludes_inherited_output(self):
        target = self.root / 'run-workloads/fresh'
        storage.copytree(self.source, target)
        copied = target / 'saves/ShaderLOD-Landscape/level.dat'
        self.assertEqual(copied.read_bytes(), b'canonical save')
        self.assertNotEqual(copied.stat().st_ino, (self.world / 'level.dat').stat().st_ino)
        copied.write_bytes(b'mutated')
        self.assertEqual((self.world / 'level.dat').read_bytes(), b'canonical save')
        self.assertFalse((target / 'saves/ShaderLOD-Landscape/session.lock').exists())
        for name in storage.TRANSIENT:
            self.assertFalse((target / name).exists())

    def test_caller_ignore_and_fresh_destination_contract_survive(self):
        target = self.root / 'fresh'
        storage.copytree(self.world, target, ignore=lambda directory, names: {'level.dat'})
        self.assertFalse((target / 'level.dat').exists())
        with self.assertRaisesRegex(ValueError, 'Existing'):
            storage.copytree(self.world, target)
        with self.assertRaisesRegex(ValueError, 'overlap'):
            storage.copytree(self.world, self.world / 'nested')

    def test_symlink_source_destination_or_tree_is_refused(self):
        link = self.root / 'linked'; link.symlink_to(self.source, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'Symlink'):
            storage.copytree(link, self.root / 'copy')
        (self.world / 'escape').symlink_to(self.root)
        with self.assertRaisesRegex(ValueError, 'Symlink'):
            storage.copytree(self.world, self.root / 'copy')
        (self.world / 'escape').unlink()
        with self.assertRaisesRegex(ValueError, 'Symlink'):
            storage.copytree(self.world, link / 'nested')

    @unittest.skipUnless(sys.platform == 'darwin', 'macOS system tempfile alias')
    def test_system_var_alias_is_accepted_without_admitting_user_links(self):
        if not self.root.is_relative_to('/private/var'):
            self.skipTest('temp directory is outside the system var alias')
        source = Path('/var') / self.world.relative_to('/private/var')
        target = self.root / 'from-var-alias'
        storage.copytree(source, target)
        self.assertEqual((target / 'level.dat').read_bytes(), b'canonical save')

    def test_free_floor_and_workload_limit_refuse_before_creating_destination(self):
        target = self.root / 'run-workloads/new'
        with patch.object(storage.shutil, 'disk_usage', return_value=Mock(free=storage.MIN_FREE)):
            with self.assertRaisesRegex(ValueError, 'reserve'):
                storage.copytree(self.world, target)
        self.assertFalse(target.exists())
        with patch.object(storage, 'MAX_WORKLOAD', 1):
            with self.assertRaisesRegex(ValueError, '96 GiB'):
                storage.copytree(self.world, target)
        self.assertFalse(target.exists())

    def test_clone_falls_back_only_for_unsupported_filesystem(self):
        clone = Mock(return_value=-1)
        with patch.object(storage.sys, 'platform', 'darwin'), \
             patch.object(storage.ctypes, 'CDLL', return_value=Mock(clonefile=clone)), \
             patch.object(storage.ctypes, 'get_errno', return_value=errno.EXDEV):
            storage.clone_file(self.world / 'level.dat', self.root / 'fallback')
        self.assertEqual((self.root / 'fallback').read_bytes(), b'canonical save')
        with patch.object(storage.sys, 'platform', 'darwin'), \
             patch.object(storage.ctypes, 'CDLL', return_value=Mock(clonefile=clone)), \
             patch.object(storage.ctypes, 'get_errno', return_value=errno.ENOSPC):
            with self.assertRaises(OSError):
                storage.clone_file(self.world / 'level.dat', self.root / 'full')
        self.assertFalse((self.root / 'full').exists())

    def test_checkout_resets_same_slot_without_stale_reports_or_new_instances(self):
        profile = storage.checkout(self.source, 'neoforge-1211-coast')
        world = profile / 'saves/ShaderLOD-Landscape/level.dat'
        self.assertFalse((profile / 'workload-measurement.json').exists())
        world.write_bytes(b'game changes')
        again = storage.checkout(self.source, 'neoforge-1211-coast')
        self.assertEqual(profile, again)
        self.assertEqual(world.read_bytes(), b'canonical save')
        self.assertEqual(len(list(profile.parent.parent.iterdir())), 1)
        self.assertEqual((self.world / 'level.dat').read_bytes(), b'canonical save')
        self.assertTrue((profile.parent / 'control.json').is_file())

    def test_checkout_filters_world_metadata_consistently_and_clears_missing_control(self):
        (self.world / '.DS_Store').write_bytes(b'Finder metadata')
        (self.world / 'backups').mkdir()
        (self.world / 'backups/old.zip').write_bytes(b'discarded metadata')
        profile = storage.checkout(self.source, 'scene')
        self.assertFalse((profile / 'saves/ShaderLOD-Landscape/.DS_Store').exists())
        self.assertFalse((profile / 'saves/ShaderLOD-Landscape/backups').exists())
        (self.source.parent / 'control.json').unlink()
        storage.checkout(self.source, 'scene')
        self.assertFalse((profile.parent / 'control.json').exists())

    def test_migrated_preparer_preserves_existing_world_fingerprint_contract(self):
        import setup_comparison as preparer
        (self.world / '.DS_Store').write_bytes(b'legacy map includes this')
        (self.world / 'backups').mkdir()
        (self.world / 'backups/world-payload').write_bytes(b'also included by legacy map')
        expected = preparer.world_fingerprint(self.world)
        world_copy = self.root / 'world-copy'
        preparer.storage_copytree(self.world, world_copy)
        self.assertEqual(preparer.world_fingerprint(world_copy), expected)
        profile_copy = self.root / 'profile-copy'
        preparer.storage_copytree(self.source, profile_copy)
        self.assertEqual(preparer.world_fingerprint(profile_copy / 'saves/ShaderLOD-Landscape'), expected)
        self.assertFalse((profile_copy / 'backups').exists())

    def test_replacement_at_logical_budget_reuses_slot(self):
        profile = storage.checkout(self.source, 'scene')
        pool_usage = storage.tree_bytes(self.root / 'run-workloads')
        with patch.object(storage, 'MAX_WORKLOAD', pool_usage):
            self.assertEqual(storage.checkout(self.source, 'scene'), profile)
        self.assertEqual((profile / 'saves/ShaderLOD-Landscape/level.dat').read_bytes(), b'canonical save')

    def test_active_client_and_lane_lock_preserve_existing_workspace(self):
        profile = storage.checkout(self.source, 'scene')
        sentinel = profile / 'sentinel'; sentinel.write_text('keep')
        with patch.object(dev_client, 'clients', return_value={22: {}}):
            with self.assertRaisesRegex(ValueError, 'Active Minecraft'):
                storage.checkout(self.source, 'scene')
        with dev_client.runtime_lock():
            with self.assertRaisesRegex(ValueError, 'already owned'):
                storage.checkout(self.source, 'scene')
        self.assertTrue(sentinel.exists())

    @unittest.skipIf(os.name == 'nt', 'Mac Java-compatible world locks')
    def test_real_world_session_lock_blocks_destructive_reset(self):
        profile = storage.checkout(self.source, 'scene')
        lock = profile / 'saves/ShaderLOD-Landscape/session.lock'
        lock.write_bytes(b'lock')
        script = 'import fcntl,sys; f=open(sys.argv[1],"r+b"); fcntl.lockf(f,fcntl.LOCK_EX); print("locked",flush=True); sys.stdin.read()'
        process = subprocess.Popen([sys.executable, '-c', script, str(lock)], stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, text=True)
        try:
            self.assertEqual(process.stdout.readline().strip(), 'locked')
            with self.assertRaises(BlockingIOError):
                storage.checkout(self.source, 'scene')
            self.assertEqual((profile / 'saves/ShaderLOD-Landscape/level.dat').read_bytes(), b'canonical save')
        finally:
            process.communicate('', timeout=5)

    def test_unmanaged_changed_source_and_slot_limit_refuse(self):
        pool = self.root / 'run-workloads/world-pool'; (pool / 'unmanaged').mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, 'Unmanaged'):
            storage.checkout(self.source, 'unmanaged')
        profile = storage.checkout(self.source, 'scene')
        with self.assertRaisesRegex(ValueError, 'canonical source'):
            storage.checkout(profile, 'another')
        with patch.object(storage, 'MAX_SLOTS', 2):
            with self.assertRaisesRegex(ValueError, 'Eight'):
                storage.checkout(self.source, 'overflow')
        with self.assertRaisesRegex(ValueError, 'Invalid'):
            storage.checkout(self.source, '../escape')

    def test_retention_keeps_three_frames_reports_and_recent_sequences(self):
        base = self.root / 'run-workloads/old/client/camera-quality/sequence'; base.mkdir(parents=True)
        old = time.time() - 16 * 86400
        for i in range(9):
            path = base / f'frame-{i:04}.png'; path.write_bytes(b'png'); os.utime(path, (old, old))
        (base / 'manifest.json').write_text('{}')
        self.assertEqual(storage.prune_captures(apply=True)['frames'], 0, 'Age alone must preserve motion evidence')
        (base / '.storage-retire').write_text('Investigation complete; compact evidence retained in manifest.json')
        preview = storage.prune_captures(apply=False)
        self.assertEqual(preview['frames'], 6)
        self.assertEqual(len(list(base.glob('*.png'))), 9)
        result = storage.prune_captures(apply=True)
        self.assertEqual(result['frames'], 6)
        self.assertEqual([p.name for p in sorted(base.glob('*.png'))],
                         ['frame-0000.png', 'frame-0004.png', 'frame-0008.png'])
        self.assertTrue((base / 'manifest.json').exists())
        self.assertTrue(json.loads((base / 'STORAGE-RETENTION.json').read_text())['raw_motion_sequence_retired'])
        self.assertTrue(Path(result['receipt']).exists())
        with self.assertRaisesRegex(ValueError, '14 days'):
            storage.prune_captures(days=1)

    def test_pinned_old_capture_is_preserved(self):
        base = self.root / 'run-workloads/pinned/camera-quality/sequence'; base.mkdir(parents=True)
        for i in range(8):
            path = base / f'frame-{i:04}.png'; path.write_bytes(b'png')
            os.utime(path, (1, 1))
        (base / '.storage-retire').write_text('Retired sequence')
        (base.parent / '.storage-keep').write_text('Active quality evidence')
        self.assertEqual(storage.prune_captures(apply=True)['frames'], 0)


    def test_failed_reset_preserves_prior_usable_profile_and_receipt(self):
        profile = storage.checkout(self.source, 'scene')
        sentinel = profile / 'sentinel'
        sentinel.write_bytes(b'previous usable inputs')
        marker = profile.parent / 'workspace.json'
        previous = marker.read_bytes()
        with patch.object(storage, 'copytree', side_effect=OSError('copy failed')):
            with self.assertRaisesRegex(OSError, 'copy failed'):
                storage.checkout(self.source, 'scene')
        self.assertTrue(sentinel.exists(), 'failed reset removed the prior usable profile')
        self.assertEqual(marker.read_bytes(), previous)

    def test_finite_copy_can_use_its_explicit_operation_reserve(self):
        target = self.root / 'finite-copy'
        with patch.object(storage.shutil, 'disk_usage', return_value=Mock(free=2 * storage.GIB)):
            storage.copytree(self.world, target, reserve_bytes=512 * 1024 * 1024)
        self.assertEqual((target / 'level.dat').read_bytes(), b'canonical save')
        default = self.root / 'general-copy'
        with patch.object(storage.shutil, 'disk_usage', return_value=Mock(free=2 * storage.GIB)):
            with self.assertRaisesRegex(ValueError, 'reserve'):
                storage.copytree(self.world, default)
        self.assertFalse(default.exists())


    def test_interrupted_stage_is_preserved_until_its_owner_recovers_it(self):
        profile = storage.checkout(self.source, 'scene')
        sentinel = profile / 'sentinel'; sentinel.write_bytes(b'prior usable inputs')
        interrupted = profile.parent / '.client-reset-interrupted'
        interrupted.mkdir(); (interrupted / 'prior-input').write_bytes(b'preserve')
        with self.assertRaisesRegex(ValueError, 'Interrupted reset preserved'):
            storage.checkout(self.source, 'scene')
        self.assertEqual(sentinel.read_bytes(), b'prior usable inputs')
        self.assertEqual((interrupted / 'prior-input').read_bytes(), b'preserve')

    def test_failed_install_restores_previous_profile_and_cleans_owned_stage(self):
        profile = storage.checkout(self.source, 'scene')
        sentinel = profile / 'sentinel'; sentinel.write_bytes(b'prior usable inputs')
        marker = profile.parent / 'workspace.json'; previous = marker.read_bytes()
        rename = Path.rename
        def fail_install(path, target):
            if path.name == 'client' and path.parent.name.startswith('.client-reset-'):
                raise OSError('install failed')
            return rename(path, target)
        with patch.object(Path, 'rename', fail_install):
            with self.assertRaisesRegex(OSError, 'install failed'):
                storage.checkout(self.source, 'scene')
        self.assertEqual(sentinel.read_bytes(), b'prior usable inputs')
        self.assertEqual(marker.read_bytes(), previous)
        self.assertEqual(list(profile.parent.glob('.client-reset-*')), [])



class ScopedUsageTests(unittest.TestCase):
    def test_atomic_publication_between_discovery_and_stat_is_reported(self):
        with tempfile.TemporaryDirectory(dir=storage.ROOT) as directory:
            root=Path(directory).resolve();temporary=root/'manifest.json.tmp';final=root/'manifest.json'
            temporary.write_text('retained evidence');safe_path=storage.safe_path
            def publish(path):
                result=safe_path(path)
                if Path(path)==temporary and temporary.exists():temporary.rename(final)
                return result
            with patch.object(storage,'safe_path',side_effect=publish):
                report=storage.scoped_usage([root])
            self.assertEqual(1,report['vanished_entries'])
            self.assertEqual('retained evidence',final.read_text())
            self.assertEqual(len('retained evidence'),storage.scoped_usage([root])['logical_bytes'])

    def test_exact_roots_categories_overlaps_allocation_and_free_are_separate(self):
        with tempfile.TemporaryDirectory(dir=storage.ROOT) as directory:
            root=Path(directory).resolve()
            paths={'report/frame-0001.png':5,'client/saves/world/data.sqlite':7,
                   'report/generated/source.frag':9,'client/backups/world.zip':11,'report/console.log':13}
            for name,size in paths.items():
                p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'x'*size)
            report=storage.scoped_usage([root,root/'report'])
            self.assertEqual(45,report['logical_bytes']);self.assertEqual(5,report['files'])
            self.assertEqual({'captures','worlds','generated-shaders','backups','logs'},set(report['categories']))
            self.assertIn('APFS',report['allocation_note']);self.assertIn('WSL',report['allocation_note'])
            self.assertTrue(report['filesystems']);self.assertGreaterEqual(report['allocated_bytes'],0)
    def test_budget_symlinks_and_unknown_paths_never_authorize_cleanup(self):
        with tempfile.TemporaryDirectory(dir=storage.ROOT) as directory:
            root=Path(directory).resolve();(root/'unknown').write_text('protected')
            with self.assertRaisesRegex(ValueError,'entry limit'):storage.scoped_usage([root],max_entries=1)
            (root/'escape').symlink_to(root/'unknown')
            with self.assertRaisesRegex(ValueError,'Symlink'):storage.scoped_usage([root])
            self.assertEqual('protected',(root/'unknown').read_text())
    def test_hardlinked_readonly_metadata_counts_unique_inodes_once(self):
        with tempfile.TemporaryDirectory(dir=storage.ROOT) as directory:
            root=Path(directory).resolve();(root/'source').write_bytes(b'1234');os.link(root/'source',root/'link')
            report=storage.scoped_usage([root]);self.assertEqual(4,report['logical_bytes']);self.assertEqual(1,report['files'])

if __name__ == '__main__':
    unittest.main()
