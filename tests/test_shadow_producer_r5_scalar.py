"""Source-grounded scalar falsifiers, not shader/runtime/performance tests."""
from fractions import Fraction
from pathlib import Path
import math
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / 'dev/logs/mechanism-evaluation-r5/shadow-producer'


class ShadowProducerScalarTests(unittest.TestCase):

    def test_bsl_tile_union_contains_every_native_bilinear_support_texel(self):
        # Parse the actual nine positions from the freshly archive-pinned source.
        with __import__('zipfile').ZipFile(ROOT / 'dependencies/upstream/other-pack-research/BSL_v10.1.8.zip') as archive:
            source = archive.read('shaders/lib/lighting/shadows.glsl').decode()
        body = source.split('vec2 shadowOffsets[9] = vec2[9](', 1)[1].split(');', 1)[0]
        offsets = [(float(x), float(y)) for x, y in re.findall(r'vec2\(\s*([-\d.]+),\s*([-\d.]+)\)', body)]
        self.assertEqual(len(offsets), 9)
        # Ordinary offset = 1/N. x/y are sample coordinates measured in texels.
        for ix in range(64):
            for iy in range(64):
                x, y = 8 + ix / 16, 8 + iy / 16
                actual = set()
                for ox, oy in offsets:
                    bx, by = math.floor(x + ox - .5), math.floor(y + oy - .5)
                    actual.update((bx + dx, by + dy) for dx in (0, 1) for dy in (0, 1))
                # Bound the entire radius-one support then align to 4x4 tiles.
                xlo, xhi = math.floor(x - 1.5), math.floor(x + .5) + 1
                ylo, yhi = math.floor(y - 1.5), math.floor(y + .5) + 1
                tiles = {(xx // 4, yy // 4) for xx in range(xlo, xhi + 1) for yy in range(ylo, yhi + 1)}
                self.assertLessEqual(len(tiles), 4)
                covered = {(tx * 4 + dx, ty * 4 + dy) for tx, ty in tiles for dx in range(4) for dy in range(4)}
                self.assertTrue(actual <= covered)
                # An adversarial dark texel anywhere in actual support must also
                # enter a queried minimum; no four-corner sampling shortcut.
                for blocker in actual:
                    self.assertIn(blocker, covered)

    def test_four_lit_probes_do_not_certify_uniform_interior(self):
        depth = {(x, y): 1 for x in range(3) for y in range(3)}
        depth[(1, 1)] = 0
        self.assertTrue(all(depth[p] > .5 for p in ((0, 0), (0, 2), (2, 0), (2, 2))))
        self.assertFalse(all(d > .5 for d in depth.values()))

    def test_physical_extent_ratio_two_breaks_four_tile_assumption(self):
        # Native unit radius is normalized by declared resolution. Twice that
        # physical extent makes radius two actual texels, even at subsurface=0.
        with __import__('zipfile').ZipFile(ROOT / 'dependencies/upstream/other-pack-research/BSL_v10.1.8.zip') as archive:
            source = archive.read('shaders/lib/lighting/shadows.glsl').decode()
        body = source.split('vec2 shadowOffsets[9] = vec2[9](', 1)[1].split(');', 1)[0]
        offsets = [(float(x), float(y)) for x, y in re.findall(r'vec2\(\s*([-\d.]+),\s*([-\d.]+)\)', body)]
        x = y = 5.5
        actual_tiles = set()
        for ox, oy in offsets:
            bx, by = math.floor(x + 2 * ox - .5), math.floor(y + 2 * oy - .5)
            actual_tiles.update(((bx + dx)//4, (by + dy)//4) for dx in (0, 1) for dy in (0, 1))
        self.assertGreater(len(actual_tiles), 4)
        bounds = {(xx//4, yy//4) for xx in range(3, 9) for yy in range(3, 9)}
        self.assertEqual(len(bounds), 9)
        self.assertTrue(actual_tiles <= bounds)

    def test_ignoring_hardware_bilinear_neighbour_can_false_accept(self):
        depths = (1, 1, 1, 0)
        nearest_only = depths[0] > .5
        bilinear_compare = sum(d > .5 for d in depths) / 4
        self.assertTrue(nearest_only)
        self.assertEqual(bilinear_compare, .75)

    def test_primary_all_dark_does_not_erase_bsl_colored_transmission(self):
        # Native filtered return: squared colored layer * (1-shadow0)+shadow0.
        primary, secondary, color = 0.0, 1.0, .5
        output = (secondary * color) ** 2 * (1-primary) + primary
        self.assertEqual(output, .25)
        self.assertNotEqual(output, 0)

    def test_bliss_accumulated_offsets_exceed_max_single_offset(self):
        # An in-place += receiver cannot be enclosed by max length(offset_i).
        offsets = ((1., 0.), (1., 0.), (1., 0.))
        single_bound = max(math.hypot(*p) for p in offsets)
        prefix_position = tuple(sum(p[i] for p in offsets) for i in (0, 1))
        self.assertGreater(math.hypot(*prefix_position), single_bound)

    def test_identical_two_moments_have_different_visibility(self):
        depths = [Fraction(i, 3) for i in range(4)]
        counts_a, counts_b = (1, 3, 3, 1), (0, 6, 0, 2)
        def moment(counts, exponent):
            return sum(n * d**exponent for n, d in zip(counts, depths)) / sum(counts)
        self.assertEqual(moment(counts_a, 1), Fraction(1, 2))
        self.assertEqual(moment(counts_a, 2), Fraction(1, 3))
        for k in (1, 2):
            self.assertEqual(moment(counts_a, k), moment(counts_b, k))
        def lit(counts):
            return Fraction(sum(n for n, d in zip(counts, depths) if d >= Fraction(1, 2)), sum(counts))
        self.assertEqual(lit(counts_a), Fraction(1, 2))
        self.assertEqual(lit(counts_b), Fraction(1, 4))

    def test_solas_lit_certificate_needs_ramp_width(self):
        reference = .5
        depth = reference + 1 / 131072
        self.assertTrue(depth > reference)
        self.assertEqual(min(max((depth - reference) * 65536, 0), 1), .5)

    def test_storage_and_optimistic_expression_budget(self):
        n, f = 2048, 1920 * 1200
        levels = sum((n >> level)**2 for level in range(12))
        self.assertEqual(8 * levels, 44739240)
        self.assertEqual(8 * (levels - n*n), 11184808)
        self.assertEqual(4 * (n//4)**2, 1048576)
        self.assertEqual(4 * n*n, 16777216)  # actual full-sized target, one image
        self.assertAlmostEqual((4 + n*n/f) / 9, .646716049382716, places=12)
        self.assertAlmostEqual((4 + n*n/(.5*f)) / 9, .8489876543209877, places=12)
        self.assertGreater((4 + n*n/(.36*f)) / 9, 1)
        # Forward invocation density can exceed one despite ≤100% screen coverage.
        self.assertLess((4 + n*n/(2*f)) / 9, (4 + n*n/f) / 9)

    def test_smaller_cascade_texel_count_does_not_reduce_duplicate_vertices(self):
        self.assertEqual(2 * 1024**2 / 2048**2, .5)
        original_vertices = 1200
        shared_caster_vertices = 2 * original_vertices
        self.assertGreater(shared_caster_vertices, original_vertices)

    def test_low_sun_visible_shadow_exceeds_caster_height(self):
        length = 10 / math.tan(math.radians(10))
        self.assertAlmostEqual(length, 56.712818196177096)
        self.assertGreater(length, 5 * 10)


if __name__ == '__main__':
    unittest.main()
