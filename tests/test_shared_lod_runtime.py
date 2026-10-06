"""Strict general-system source qualification; no client or build."""
import json
from pathlib import Path
import sys
import tempfile
import tomllib
import unittest
from unittest import mock
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import prepare_shared_lod_runtime as prep


class SharedRuntimeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.runtime = json.loads((ROOT / 'dev/runtime.lock.json').read_text())
        self.original = ROOT / 'dev/upstream/photon_v1.3b.zip'

    def test_pure_fresh_ignored_plan_and_exact_registered_pins(self):
        target = self.root / 'run-workloads/fresh'
        plan = prep.plan(target, root=self.root)
        self.assertFalse(target.exists()); self.assertTrue(plan['untimed']); self.assertFalse(plan['gpu_timing'])
        self.assertEqual(plan['private_options'], {prep.consumer.SWITCH: 'true'})
        self.assertEqual(plan['capability_schema'], 1)
        with self.assertRaisesRegex(ValueError, 'ignored'): prep.plan(self.root / 'tracked', root=self.root)
        target.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError, 'fresh'): prep.plan(target, root=self.root)

    def test_actual_registered_archive_and_all_canonical_options(self):
        original, private, qualified = prep.qualify_archives(self.original, prep.PROBE, self.runtime)
        self.assertEqual(private['sha256'], prep.ARCHIVE_SHA256)
        self.assertEqual(len(qualified['metadata']['reference_member_sha256']), 10)
        self.assertEqual(qualified['metadata']['capability_schema'], 1)
        record = prep.inspect_scene({'scene': 'coastalwater', 'source_profile': str(prep.SOURCE)}, prep.PROBE, self.runtime)
        self.assertEqual(len(record['native_options']), 354)
        self.assertEqual(len(record['native_options'] | record['option_evidence']['omitted_non_settings_defaults']), 366)
        self.assertEqual(record['patched_options'], record['native_options'] | {prep.consumer.SWITCH: 'true'})
        self.assertEqual(record['patched_options']['CLOUDS_CUMULUS'], 'true')
        self.assertEqual(record['patched_options']['BLOCKY_CLOUDS'], 'false')

    def test_member_metadata_and_archive_drift_rejected(self):
        files = prep.consumer.archive_members(prep.PROBE); target = self.root / 'mutated.zip'
        def write(changed):
            with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
                for name, content in changed.items(): archive.writestr(name, content)
        write(files | {'unexpected': b'new'})
        with self.assertRaisesRegex(ValueError, 'inventory'): prep.qualify_archives(self.original, target, self.runtime)
        member = prep.consumer.ADDITIONS[-1][0]
        write(files | {member: files[member] + b'changed'})
        with self.assertRaisesRegex(ValueError, 'bytes'): prep.qualify_archives(self.original, target, self.runtime)
        metadata = json.loads(files[prep.consumer.METADATA]); metadata['gpu_benefit'] = 'claimed'
        write(files | {prep.consumer.METADATA: json.dumps(metadata).encode()})
        with self.assertRaisesRegex(ValueError, 'metadata'): prep.qualify_archives(self.original, target, self.runtime)
        write(files)
        with self.assertRaisesRegex(ValueError, 'complete archive'): prep.qualify_archives(self.original, target, self.runtime)

    def test_canonical_world_drift_rejected_before_copy(self):
        with mock.patch.object(prep, 'world_fingerprint', return_value='changed'):
            with self.assertRaisesRegex(ValueError, 'world drift'):
                prep.inspect_scene({'scene': 'coastalwater', 'source_profile': str(prep.SOURCE)}, prep.PROBE, self.runtime)

    def test_all_seven_effects_and_global_controls(self):
        effects = tomllib.loads(prep.effects_config('adaptive'))['effects']
        self.assertEqual(len(effects), 7)
        self.assertEqual(effects['volumetrics'], dict(mode='ADAPTIVE', near=512., far=1536., transition=128.,
            mediumWorkRatio=.85, farWorkRatio=.75, preserveSalientLighting=True))
        for name, values in effects.items():
            self.assertTrue(values['preserveSalientLighting'])
            if name != 'volumetrics':
                self.assertEqual(values['mode'], 'INHERIT'); self.assertEqual(values['mediumWorkRatio'], 1)
                self.assertEqual(values['farWorkRatio'], 1)
        for arm in prep.copies.ARMS:
            config = tomllib.loads(prep.copies.fresh_config(arm))
            self.assertEqual(config['lod']['enabled'], arm == 'adaptive')
            self.assertFalse(config['terrain']['enabled'])


if __name__ == '__main__': unittest.main()
