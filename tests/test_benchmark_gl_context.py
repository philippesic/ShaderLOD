import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import benchmark_gl_context as context
import run_benchmark


class GlContextEvidenceTest(unittest.TestCase):
    def test_native_and_higher_returned_versions_are_honestly_labeled(self):
        native = context.observe('native', {'gpu_driver': '3.2.0 NVIDIA 617.42'})
        self.assertEqual([3, 2], native['actual_version'])
        higher = context.observe('4.5', {'gpu_driver': '4.6.0 NVIDIA 617.42'})
        self.assertEqual('4.5', higher['requested'])
        self.assertEqual([4, 6], higher['actual_version'])
        self.assertEqual([4, 6], context.observe('4.6', {'gpu_driver': '4.6 (Core Profile) Mesa 25.1'})['actual_version'])

    def test_older_missing_or_es_context_cannot_satisfy_desktop_request(self):
        for state in ({'gpu_driver': '3.2.0 NVIDIA 617.42'}, {},
                      {'gpu_driver': 'OpenGL ES 3.2 driver'}, {'gpu_driver': 'unavailable'}):
            with self.assertRaises(ValueError): context.observe('4.5', state)
        for request in ('4.4', 'true', '4.6spoof'):
            with self.assertRaises(ValueError): context.validate_request(request)

    def test_frozen_qualification_keeps_its_native_context_contract(self):
        context.validate_request('native', frozen_qualification=True)
        for request in ('3.2', '4.3', '4.5', '4.6'):
            with self.assertRaisesRegex(ValueError, 'frozen'):
                context.validate_request(request, frozen_qualification=True)
        parser = run_benchmark.argument_parser()
        required = ['--pack', 'original.zip', '--output', 'fresh-report']
        self.assertEqual('native', parser.parse_args(required).gl_context)
        self.assertEqual('4.6', parser.parse_args(required + ['--gl-context', '4.6']).gl_context)
