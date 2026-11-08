import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_quality_archive as archive


class QualityArchiveTest(unittest.TestCase):
    def setUp(self):
        # Capacity policy has its own run_storage tests; temporary filesystems
        # need not have the real campaign's 32 GiB reserve for byte-integrity tests.
        admission = patch.object(archive.run_storage, 'admit')
        self.admission = admission.start()
        self.addCleanup(admission.stop)

    def prepare(self, root):
        report = root/'report'
        report.mkdir()
        (report/'operation.json').write_text(json.dumps(dict(
            state='complete', configuration_restored=True, release_verified=True,
            iteration_profile=dict(reset_verified=True), processes=dict(controller=None))))
        for label in ('native', 'candidate', 'return'):
            directory = report/('quality-'+label)
            directory.mkdir()
            (directory/'frame-0000.png').write_bytes(label.encode()*100)
        return report

    def test_all_arm_bytes_are_recoverable_before_sources_are_retired(self):
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(archive.dev_client, 'process_table', return_value=[]), \
                patch.object(archive.dev_client, 'clients', return_value=[]):
            root = Path(temporary)
            report = self.prepare(root)
            result = archive.preserve(report, root/'archives')
            self.assertEqual(3, len(result['archives']))
            self.assertEqual(3, self.admission.call_count)
            for receipt in result['archives']:
                self.assertTrue(receipt['verified'] and receipt['retired'])
                self.assertFalse((report/('quality-'+receipt['label'])).exists())
                with zipfile.ZipFile(receipt['archive']) as output:
                    for row in json.loads(output.read('PRESERVATION-MANIFEST.json')):
                        content = output.read(row['path'])
                        self.assertEqual(row['bytes'], len(content))
                        self.assertEqual(row['sha256'], hashlib.sha256(content).hexdigest())

    def test_live_clients_prevent_any_archive_or_retirement(self):
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(archive.dev_client, 'process_table', return_value=[]), \
                patch.object(archive.dev_client, 'clients', return_value=[{'pid': 123}]):
            root = Path(temporary)
            report = self.prepare(root)
            with self.assertRaisesRegex(ValueError, 'Client active'):
                archive.preserve(report, root/'archives')
            self.assertTrue((report/'quality-native/frame-0000.png').exists())
            self.assertFalse((root/'archives').exists())
            self.admission.assert_not_called()

    def test_concurrent_source_change_preserves_the_source(self):
        with tempfile.TemporaryDirectory() as temporary, \
                patch.object(archive.dev_client, 'process_table', return_value=[]), \
                patch.object(archive.dev_client, 'clients', return_value=[]):
            root = Path(temporary)
            report = self.prepare(root)
            source = report/'quality-native/frame-0000.png'
            original_digest = archive.digest
            calls = 0

            def mutate_on_second_read(path):
                nonlocal calls
                if path == source:
                    calls += 1
                    if calls == 2:
                        path.write_bytes(b'changed source')
                return original_digest(path)

            with patch.object(archive, 'digest', side_effect=mutate_on_second_read):
                with self.assertRaisesRegex(ValueError, '(Capture|Source) changed'):
                    archive.preserve(report, root/'archives')
            self.assertEqual(b'changed source', source.read_bytes())
            self.assertFalse((report/'quality-preservation.json').exists())


if __name__ == '__main__':
    unittest.main()
