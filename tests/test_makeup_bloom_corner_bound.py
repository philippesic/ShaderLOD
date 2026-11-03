"""Bound checks independent of any GL installation or historical report."""
from fractions import Fraction
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from analyze_makeup_bloom_corner_bound import corner_bound


class CornerBoundTests(unittest.TestCase):
    def test_selected_native_and_high_cost_ceiling(self):
        native = corner_bound(2560,1600,3)
        high = corner_bound(2560,1600,8)
        self.assertEqual(native['assumed_finer_mip_size'], [320,200])
        self.assertEqual(native['corner_pixel_ceiling'],64)
        self.assertEqual(native['first_ring_saved_expression_ceiling'],192)
        self.assertEqual(native['native_query_expressions'],49_152_000)
        self.assertEqual(high['native_query_expressions'],131_072_000)
        self.assertEqual(high['all_rings_saved_expression_ceiling'],1536)

    def test_odd_sizes_and_power_boundaries(self):
        self.assertEqual(corner_bound(1919,1199,3)['assumed_finer_mip_size'],[239,149])
        for h, expected in [(127,0),(128,0),(255,0),(256,1),(1023,2),(1024,3)]:
            self.assertEqual(corner_bound(1920,h,3)['finer_mip_level'],expected)
        for args in [(0,100,3),(100,100,0),(4097,100,3)]:
            with self.assertRaises(ValueError):corner_bound(*args)

    def test_exhaustive_small_stencils_cannot_escape_center_ceiling(self):
        # Independently enumerate symmetric native quads with many radii and
        # compare actual same-corner eligibility against the center bound.
        w,h = 16,8
        mx,my = 4,2
        qualified=set()
        centers=set()
        for y in range(h):
            for x in range(w):
                u,v=Fraction(2*x+1,2*w),Fraction(2*y+1,2*h)
                def side(c,size):
                    return -1 if c <= Fraction(1,2*size) else (1 if c >= 1-Fraction(1,2*size) else 0)
                if side(u,mx) and side(v,my):centers.add((x,y))
                for a in range(-8,9):
                    for b in range(-8,9):
                        dx,dy=Fraction(a,64),Fraction(b,64)
                        offsets=[(dx,dy),(-dx,-dy),(-dy*Fraction(5,4),dx*Fraction(5,4)),(dy*Fraction(5,4),-dx*Fraction(5,4))]
                        keys=[(side(u+ox,mx),side(v+oy,my)) for ox,oy in offsets]
                        if keys[0][0] and keys[0][1] and len(set(keys))==1:
                            qualified.add((x,y))
        self.assertTrue(qualified)
        self.assertLessEqual(qualified,centers)
        self.assertEqual(len(centers),16)


if __name__ == '__main__':unittest.main()
