"""Pinned full-budget trace dispatch: structural equivalence, no driver/speed claim."""
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
# Isolated candidate invocation can pass the actual repository path.
if '--repository' in sys.argv:
    at = sys.argv.index('--repository')
    ROOT = Path(sys.argv[at + 1])
    del sys.argv[at:at + 2]
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / 'scripts'))
import build_photon_live_pixel_budget as producer

MEMBER = 'shaders/include/misc/raytracer.glsl'

class TraceNativeDispatchTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        candidate = Path(__file__).resolve().parents[1] / producer.MANIFEST
        cls.manifest = json.loads((candidate if candidate.exists() else ROOT / producer.MANIFEST).read_text())
        cls.original = producer.archive_members(ROOT / producer.BASE)
        cls.assembled = producer.patch_members(cls.original, cls.manifest, 'enabled')
        entry = next(e for e in cls.manifest['members'] if e['path'] == MEMBER)
        cls.patch = next(r for r in entry['replacements'] if 'uint shaderlodNativeIntersectionCount' in r['after'] and 'uint shaderlodNativeIntersectionCount' not in r['before'])

    def test_full_and_reduced_loop_bodies_identical_to_native(self):
        import re
        native = self.original[MEMBER].decode()
        start = native.index('    for (int i = 0; i < intersection_step_count; ++i, ray_pos += ray_step) {')
        end = native.index('\n\n    if (!hit)', start)
        loop = native[start:end]
        after = self.patch['after']
        self.assertEqual(2, after.count(loop))  # reduced active and literal private-off
        for macro in ('SSR_INTERSECTION_STEPS_SMOOTH', 'SSR_INTERSECTION_STEPS_ROUGH'):
            self.assertEqual(1, after.count(loop.replace('i < intersection_step_count', 'i < ' + macro)))
            self.assertIn('intersection_step_count == shaderlodNativeIntersectionCount &&\n        intersection_step_count == uint(' + macro + ')', after)

    def test_private_off_is_pinned_native(self):
        marker = '\n#else\n#if !defined INCLUDE_MISC_RAYTRACER\n'
        before, after = self.patch['before'], self.patch['after']
        self.assertEqual(before.split(marker)[1], after.split(marker)[1])
        self.assertEqual(self.original[MEMBER].decode(), '#if !defined INCLUDE_MISC_RAYTRACER\n' + after.split(marker)[1].removesuffix('\n#endif\n'))

    def test_setup_refinement_and_fallback_are_unchanged(self):
        before, after = self.patch['before'], self.patch['after']
        start = after.index('    // Full-budget kernels')
        end = after.index('\n\n    if (!hit)', start)
        old_start = before.index('    for (int i = 0; i < intersection_step_count; ++i, ray_pos += ray_step) {')
        old_end = before.index('\n\n    if (!hit)', old_start)
        recovered = after[:start] + before[old_start:old_end] + after[end:]
        recovered = recovered.replace('    uint shaderlodNativeIntersectionCount = intersection_step_count;\n', '', 1)
        self.assertEqual(before, recovered)

if __name__ == '__main__':
    unittest.main()
