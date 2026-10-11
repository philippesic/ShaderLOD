import contextlib
import fcntl
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
import benchmark_storage as storage
import renderer_average_storage as recipe


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

    def test_documented_relative_prepare_cli_with_tiny_saved_plan_inputs(self):
        # Three saved plans use the same fields/argv consumed by the authentic
        # Bliss renderer recipe; payloads and dependency APIs are tiny fixtures.
        template = self.base/'template'; template.mkdir()
        pack = self.base/'Bliss_v2.1.2.zip'; pack.write_bytes(b'fixture-pack')
        review = self.base/'source-review.md'; review.write_text('Fixture source evidence')
        sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
        worldmap = {'level.dat': sha(self.source/'level.dat')}
        for arm in recipe.ARMS:
            prior = self.base/'templates'/arm/'client'
            (prior/'config').mkdir(parents=True); (prior/'mods').mkdir()
            (prior/'config/shaderlod-client.toml').write_text('[lod]\nenabled=false\n')
            (prior/'options.txt').write_text('renderDistance:24\n')
            stage = template/arm; stage.mkdir()
            for name, data in [('options.txt','QUALITY=3\n'), ('core.toml','[lod]\nenabled=false\n'),
                               ('control.json','{"x":256.5,"time":7000}'), ('expected-options.json','{"QUALITY":"3"}')]:
                (stage/name).write_text(data)
            plan = dict(profile=str(prior), pack=pack.name, pack_name=pack.name, sourceReview=review.name,
                        command=[str(self.base/'gradlew'), 'runClientLodOff', '-PrunDirectory='+str(prior),
                                 '-PcontrolCaptureReceipt='+str(stage/'captures')])
            storage.write(stage/'plan.json', plan)
            storage.write(stage/'freeze.json', dict(build={}, canonicalWorld=worldmap,
                          files={str(stage/'plan.json'):sha(stage/'plan.json')}))
        client = mock.Mock()
        client.runtime_lock.side_effect = contextlib.nullcontext
        client.clients.return_value = {}; client.build_fingerprint.return_value = {}
        observed = []
        def consume(plan, backup):
            observed.append(plan)
            for key in ('profile', 'installed_pack', 'pack', 'pack_options', 'expected_options_source', 'sourceReview'):
                self.assertTrue(Path(plan[key]).is_absolute(), key)
            self.assertTrue(Path(backup).is_absolute())
        client.prepare.side_effect = consume
        prior_cwd = Path.cwd()
        try:
            os.chdir(self.base)
            argv = ['renderer_average_storage.py', 'prepare', '--template', 'template', '--world', 'canonical',
                    '--profiles', 'run-workloads/cli-next', '--output', 'reports/cli-next']
            with mock.patch.object(recipe, 'load_client', return_value=client), \
                 mock.patch.object(sys, 'argv', argv), contextlib.redirect_stdout(io.StringIO()):
                recipe.main()
        finally:
            os.chdir(prior_cwd)
        profiles = self.base/'run-workloads/cli-next'; output = self.base/'reports/cli-next'
        manifest = json.loads((profiles/storage.MARKER).read_text())
        self.assertEqual(len(manifest['disposable_world_clones']), 3)
        self.assertEqual(manifest['root'], str(profiles))
        self.assertEqual(len(observed), 3)
        for arm in recipe.ARMS:
            world = profiles/arm/'client/saves'/recipe.WORLD
            self.assertEqual((world/'level.dat').read_bytes(), b'save')
            generated = json.loads((output/arm/'plan.json').read_text())
            self.assertIn('-PrunDirectory='+str(profiles/arm/'client'), generated['command'])
            self.assertIn('-PcontrolCaptureReceipt='+str(output/arm/'captures'), generated['command'])
            freeze = json.loads((output/arm/'freeze.json').read_text())
            self.assertTrue(all(Path(name).is_absolute() for name in freeze['files']))

    def test_adapter_default_preserve_and_boundary_pin(self):
        recipe.dispose(None, None, None, False)
        with self.assertRaisesRegex(ValueError, 'Frozen renderer runner'):
            recipe.adapted_runner('changed', self.base, self.report.parent, False)


if __name__ == '__main__': unittest.main()
