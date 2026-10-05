"""Binary32 scalar/property fixtures, not a GLSL compiler or rendering proof.

The independent geometry fixtures exercise a direct scalar reference of the frozen
helper. The SHA gate binds the reviewed source to this reference; it does not prove
driver execution, shader integration, shadow-edge detection or visual equivalence.
"""
import copy
import hashlib
import math
from pathlib import Path
import random
import re
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'shaderpacks/common/shaders/include/shaderlod/surface_continuity.glsl'
FROZEN_SHA256 = '2fff58476c6d49d40eb61e5f6683dc58697ed964f128a3f444442677b06327b0'


def f32(x):
    try:
        return struct.unpack('f', struct.pack('f', x))[0]
    except OverflowError:
        return math.copysign(math.inf, x)


def add(a, b): return f32(f32(a) + f32(b))
def sub(a, b): return f32(f32(a) - f32(b))
def mul(a, b): return f32(f32(a) * f32(b))
def vsub(a, b): return tuple(sub(x, y) for x, y in zip(a, b))
def dot(a, b): return add(add(mul(a[0], b[0]), mul(a[1], b[1])), mul(a[2], b[2]))
def length(a): return f32(math.sqrt(dot(a, a)))
def cross(a, b):
    return (sub(mul(a[1], b[2]), mul(a[2], b[1])),
            sub(mul(a[2], b[0]), mul(a[0], b[2])),
            sub(mul(a[0], b[1]), mul(a[1], b[0])))


DEFAULT = dict(plane_absolute_blocks=.015, plane_relative=.015, plane_max_blocks=.08,
               minimum_normal_cosine=.995, maximum_lightmap_delta=1/255,
               minimum_area_ratio=.0001, maximum_ao_shadow_spread=(.10, .025))


def eligible(positions, normals, materials=(0, 0, 0, 0), lightmaps=((.5, 1),)*4,
             protected=(False,)*4, near=32, enabled=True, spread=None, limits=None):
    """Float32 arithmetic reference in source order; all comparisons use rounded inputs."""
    l = copy.deepcopy(DEFAULT if limits is None else limits)
    l = {k: tuple(map(f32, v)) if isinstance(v, tuple) else f32(v) for k, v in l.items()}
    if not enabled or any(not math.isfinite(x) for v in l.values()
                          for x in (v if isinstance(v, tuple) else (v,))):
        return False
    if not (0 <= l['plane_absolute_blocks'] <= l['plane_max_blocks'] <= 2 and
            0 <= l['plane_relative'] <= .25 and 0 <= l['minimum_normal_cosine'] <= 1 and
            0 <= l['maximum_lightmap_delta'] <= 1 and 0 < l['minimum_area_ratio'] <= 1 and
            all(0 <= x <= 1 for x in l['maximum_ao_shadow_spread'])):
        return False
    near = f32(near)
    if not math.isfinite(near) or near < 0 or near > 65536:
        return False
    if spread is not None and any(not math.isfinite(f32(s)) or f32(s) < 0 or f32(s) > maximum
                                  for s, maximum in zip(spread, l['maximum_ao_shadow_spread'])):
        return False
    p, n, lm = ([tuple(map(f32, v)) for v in values] for values in (positions, normals, lightmaps))
    for i in range(4):
        if (protected[i] or materials[i] < 0 or materials[i] != materials[0] or
            any(not math.isfinite(x) or abs(x) > 65536 for x in p[i]) or
            any(not math.isfinite(x) for x in n[i]) or abs(sub(dot(n[i], n[i]), 1)) > f32(.001) or
            any(not math.isfinite(x) or x < 0 or x > 1 for x in lm[i]) or length(p[i]) <= near):
            return False
    ex, ey = vsub(p[1], p[0]), vsub(p[2], p[0])
    lx, ly, area = length(ex), length(ey), length(cross(ex, ey))
    if lx <= f32(.000001) or ly <= f32(.000001) or area <= mul(mul(l['minimum_area_ratio'], lx), ly):
        return False
    tolerance = min(l['plane_max_blocks'], add(l['plane_absolute_blocks'], mul(l['plane_relative'], min(lx, ly))))
    for i in range(4):
        for j in range(i + 1, 4):
            delta = vsub(p[j], p[i])
            if (length(delta) <= f32(.000001) or dot(n[i], n[j]) < l['minimum_normal_cosine'] or
                abs(dot(delta, n[i])) > tolerance or abs(dot(delta, n[j])) > tolerance or
                any(abs(sub(lm[j][k], lm[i][k])) > l['maximum_lightmap_delta'] for k in range(2))):
                return False
    return True


def plane(slope_x=0, slope_y=0, step=.5, distance=100):
    points = [(x*step, y*step, -distance + slope_x*x*step + slope_y*y*step)
              for x, y in ((0, 0), (1, 0), (0, 1), (1, 1))]
    scale = math.sqrt(slope_x*slope_x + slope_y*slope_y + 1)
    normal = (-slope_x/scale, -slope_y/scale, 1/scale)
    return points, [normal]*4


class SurfaceContinuityTest(unittest.TestCase):
    def test_frozen_source_and_api_has_no_implicit_work(self):
        source = SOURCE.read_text()
        self.assertEqual(FROZEN_SHA256, hashlib.sha256(SOURCE.read_bytes()).hexdigest())
        clean = re.sub(r'//[^\n]*', '', source)
        self.assertNotRegex(clean, r'\b(uniform|sampler\w*|texture\w*|texelFetch|dFdx|dFdy|fwidth|discard)\b')
        self.assertNotRegex(clean, r'#ifn?def\b|#include\b')
        self.assertEqual(1, clean.count('bool shaderlod_surface_tile_eligible('))
        self.assertIn('vec3 normals_view[4]', clean)
        self.assertIn('int material_ids[4]', clean)
        self.assertIn('bool protected_members[4]', clean)
        self.assertIn('abs(dot(delta, normals_view[i]))', clean)
        self.assertIn('abs(dot(delta, normals_view[j]))', clean)

    def test_flat_and_sloped_planes_accept_without_equal_linear_z(self):
        for sx, sy in ((0, 0), (.5, .2), (4, -3), (-7, 1), (30, 15)):
            p, n = plane(sx, sy)
            self.assertTrue(eligible(p, n), (sx, sy))
        p, n = plane(4, -3)
        self.assertGreater(max(v[2] for v in p) - min(v[2] for v in p), 3)

    def test_seeded_planar_property_translation_and_scale(self):
        rng = random.Random(417)
        for _ in range(300):
            p, n = plane(rng.uniform(-8, 8), rng.uniform(-8, 8),
                         rng.uniform(.02, 2), rng.uniform(80, 2000))
            translation = (rng.uniform(-100, 100), rng.uniform(-100, 100), 0)
            p = [tuple(x+y for x, y in zip(point, translation)) for point in p]
            self.assertTrue(eligible(p, n))

    def test_thin_foreground_depth_step_and_far_cap_reject(self):
        p, n = plane()
        p[3] = (p[3][0], p[3][1], p[3][2] + .1)
        self.assertFalse(eligible(p, n))
        p, n = plane(step=100, distance=2000)
        p[3] = (p[3][0], p[3][1], p[3][2] + .09)
        self.assertFalse(eligible(p, n))

    def test_material_lightmap_and_normal_discontinuities(self):
        p, n = plane()
        for i in range(4):
            materials = [0]*4; materials[i] = 1
            self.assertFalse(eligible(p, n, materials=materials))
            lm = [(.5, 1)]*4; lm[i] = (.51, 1)
            self.assertFalse(eligible(p, n, lightmaps=lm))
            changed = list(n); changed[i] = (.2, 0, math.sqrt(.96))
            self.assertFalse(eligible(p, changed))
        self.assertFalse(eligible(p, n, materials=(-1,)*4))
        self.assertFalse(eligible(p, [(0, 0, 2)]*4))

    def test_nonfinite_and_out_of_domain_always_reject(self):
        p, n = plane()
        for bad in (math.nan, math.inf, -math.inf):
            for i in range(4):
                for axis in range(3):
                    q = copy.deepcopy(p); q[i] = tuple(bad if a == axis else v for a, v in enumerate(q[i]))
                    self.assertFalse(eligible(q, n))
                    nn = copy.deepcopy(n); nn[i] = tuple(bad if a == axis else v for a, v in enumerate(nn[i]))
                    self.assertFalse(eligible(p, nn))
                lm = [(.5, 1)]*4; lm[i] = (bad, 1)
                self.assertFalse(eligible(p, n, lightmaps=lm))
            self.assertFalse(eligible(p, n, near=bad))
            self.assertFalse(eligible(p, n, spread=(bad, 0)))
            for name in DEFAULT:
                limits = copy.deepcopy(DEFAULT)
                limits[name] = (bad, 0) if name == 'maximum_ao_shadow_spread' else bad
                self.assertFalse(eligible(p, n, limits=limits))
        p[0] = (65537, 0, -100)
        self.assertFalse(eligible(p, n))

    def test_near_protected_disabled_and_singular_baseline(self):
        p, n = plane()
        self.assertFalse(eligible(p, n, enabled=False))
        self.assertFalse(eligible(p, n, near=100))
        for i in range(4):
            protection = [False]*4; protection[i] = True
            self.assertFalse(eligible(p, n, protected=protection))
        self.assertFalse(eligible([(0, 0, -100)]*4, n))
        self.assertFalse(eligible([(i, 0, -100) for i in range(4)], n))
        p[3] = p[0]
        self.assertFalse(eligible(p, n))

    def test_optional_lighting_spread_is_not_shadow_certificate(self):
        p, n = plane()
        self.assertTrue(eligible(p, n, spread=None))
        self.assertTrue(eligible(p, n, spread=(.05, .01)))
        for spread in ((.101, 0), (0, .026), (-.01, 0), (0, 1.1)):
            self.assertFalse(eligible(p, n, spread=spread))

    def test_plane_error_tolerance_and_tightening(self):
        p, n = plane()
        # Float32-decoded small reconstruction error can remain eligible.
        p[3] = (p[3][0], p[3][1], p[3][2] + .005)
        self.assertTrue(eligible(p, n))
        tight = copy.deepcopy(DEFAULT); tight['plane_absolute_blocks'] = 0; tight['plane_relative'] = 0
        self.assertFalse(eligible(p, n, limits=tight))


if __name__ == '__main__':
    unittest.main()
