"""G05 bounded non-game checks. No GL context; float model is not driver proof."""
import hashlib
import json
from pathlib import Path
import random
import re
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import build_makeup_projected_miss as candidate
from build_makeup_adapter import read_archive
BASE = (ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip')


def f(x):
    return struct.unpack('f', struct.pack('f', x))[0]


def add(a, b):
    return tuple(f(x + y) for x, y in zip(a, b))


def mul(a, k):
    return tuple(f(x * k) for x in a)


def project(p, py=1.4):
    return (f(f(f(p[0] / -p[2]) * .5) + .5),
            f(f(f(f(py * p[1]) / -p[2]) * .5) + .5),
            f(f(f(f(-1.002 * p[2]) - .1001) / -p[2]) * .5 + .5))


def zero_tail(p, d, err, py=1.4):
    edge = f(f(py * err[1]) + err[2])
    slope = f(1e-5 * f(f(f(py * abs(d[1])) + abs(d[2])) + 1))
    return (p[2] < -err[2] and d[2] <= 0 and
            f(f(py * p[1]) + p[2]) > edge and f(f(py * d[1]) + d[2]) > slope)


def trace(h, d, jitter, mode, enabled):
    p = h
    sp = project(p)
    prev = sp[2]
    hitdepth = prev
    search = hidden = out = far = hiddens = False
    count = 0
    diff = 1
    err = tuple(f(.0001 * f(f(abs(x) + f(abs(v) * 2048)) + 1)) for x, v in zip(h, d))
    for i in range(10):
        if enabled and i > 0 and not search and out and sp[1] > 1 and zero_tail(p, d, err):
            return sp, 1.0, 10-i
        if search:
            inc = mul(inc, .5)
            p = add(p, mul(inc, 1 if diff > 0 else -1 if diff < 0 else 0))
        else:
            old = p
            p = add(h, add(mul(d, f(2 ** f(i+jitter))), mul(d, -1)))
            inc = add(p, mul(old, -1))
        last = sp
        sp = project(p)
        out |= sp[0] < 0 or sp[0] > 1 or sp[1] < 0 or sp[1] > 1 or sp[2] < 0
        far |= sp[2] > .9999
        # Several bounded depth fields, including discontinuities and sky misses.
        if mode == 0:
            depth = 1.0
        elif mode == 1:
            depth = .99 if int(abs(sp[0] * 127)) % 7 == 0 else 1.0
        elif mode == 2:
            depth = f(.95 + .05 * min(max(sp[1], 0), 1))
        else:
            depth = f(.9 + .1 * ((int(abs(sp[0]*63)) ^ int(abs(sp[1]*63))) % 17) / 16)
        diff = f(depth-sp[2])
        if diff < 0 and abs(f(depth-prev)) > abs(f(sp[2]-last[2])):
            hidden = hiddens = True
        elif diff > 0:
            hidden = False
            if not hiddens:
                count += 1
        if not search and diff < 0 and not hidden:
            search = True
        prev = depth
    infinite = float(depth > .9999)
    if out:
        infinite = 1
    elif far and depth <= .9999 and count >= 3 and depth <= hitdepth:
        infinite = 1
        sp = (1., 1., 1.)
    return sp, infinite, 0


class ProjectedMissTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = read_archive(BASE)
        cls.files = candidate.transform(cls.original)

    def cpp(self, active=True, **defines):
        source = self.files[candidate.WATER].decode().replace('#include "/lib/shaderlod/g05_projected_miss.glsl"',
                                                             self.files[candidate.HELPER].decode())
        defs = dict(SHADERLOD_G05_PROJECTED_MISS=int(active), SHADERLOD_G05_DEBUG=0,
                    IS_IRIS=1, MC_VERSION=12101, IRIS_VERSION=10812, GBUFFER_WATER=1,
                    WATER_F=1, REFLECTION=1, SSR_TYPE=1, RAYMARCH_STEPS=10, SUN_REFLECTION=1)
        defs.update(defines)
        command = ['clang', '-E', '-P', '-x', 'c', '-'] + ['-D%s=%s' % a for a in defs.items() if a[1] is not None]
        return subprocess.run(command, input=source, text=True, capture_output=True, check=True).stdout

    def test_off_and_refused_scopes_preserve_native_tokens(self):
        native = subprocess.run(['clang', '-E', '-P', '-x', 'c', '-', '-DSSR_TYPE=1', '-DSUN_REFLECTION=1', '-DREFLECTION=1', '-DRAYMARCH_STEPS=10'],
                                input=self.original[candidate.WATER].decode(), text=True, capture_output=True, check=True).stdout
        tokens = lambda s: re.findall(r'\w+|[^\w\s]', s)
        self.assertEqual(tokens(self.cpp(False)), tokens(native))
        for other in [dict(DISTANT_HORIZONS=1), dict(VOXY=1), dict(VOXY_PATCH=1), dict(NETHER=1),
                      dict(THE_END=1), dict(UNKNOWN_DIM=1), dict(IRIS_VERSION=10814), dict(MC_VERSION=12111),
                      dict(RAYMARCH_STEPS=12), dict(GBUFFER_WATER=None), dict(WATER_F=None), dict(IS_IRIS=None),
                      dict(SSR_TYPE=0), dict(REFLECTION=0)]:
            with self.subTest(other=other):
                self.assertNotIn('vec3 shaderlodG05Raymarch(', self.cpp(**other))
        self.assertIn('vec3 shaderlodG05Raymarch(', self.cpp())
        self.assertNotIn('shaderlodG05SavedSteps', self.cpp())
        self.assertIn('shaderlodG05SavedSteps', self.cpp(SHADERLOD_G05_DEBUG=1))

    def test_native_trace_glass_profiles_options_and_licenses_retained(self):
        before, after = self.original[candidate.WATER].decode(), self.files[candidate.WATER].decode()
        native = before[before.index('vec3 fastRaymarch('):before.index('\n#if SUN_REFLECTION')]
        self.assertIn(native, after)
        self.assertEqual(before[before.index('//  GLASS'):], after[after.index('//  GLASS'):])
        self.assertEqual(self.original['shaders/lib/config.glsl'] + candidate.OPTIONS.encode(),
                         self.files['shaders/lib/config.glsl'])
        for name, data in self.original.items():
            if name not in [candidate.WATER, 'shaders/lib/config.glsl', 'shaders/shaders.properties',
                            'shaders/common/water_blocks_fragment.glsl']:
                self.assertEqual(data, self.files[name], name)
        oldprops = self.original['shaders/shaders.properties'].decode().splitlines()
        newprops = self.files['shaders/shaders.properties'].decode().splitlines()
        self.assertEqual([s for s in oldprops if s.startswith('profile.')], [s for s in newprops if s.startswith('profile.')])

    def test_artifact_deterministic_exact_pin_and_sidecar(self):
        with tempfile.TemporaryDirectory() as td:
            td = Path(td)
            opt = td / 'native.txt'
            opt.write_bytes(b'# native option preimage\nREFLECTION_SLIDER=2\nAA_TYPE=2\n')
            first, second = td/'first.zip', td/'second.zip'
            candidate.build(BASE, first, opt)
            candidate.build(BASE, second, opt)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            with zipfile.ZipFile(first) as z:
                for name in z.namelist():
                    if name.startswith('g05-source/'):
                        z.extract(name, td)
            rebuilt = td / 'rebuilt.zip'
            subprocess.run([sys.executable, str(td/'g05-source/scripts/build_makeup_projected_miss.py'),
                            '--base', str(BASE), '--output', str(rebuilt)], check=True, capture_output=True)
            self.assertEqual(first.read_bytes(), rebuilt.read_bytes())
            self.assertTrue(Path(str(first)+'.txt').read_bytes().startswith(opt.read_bytes()))
            with self.assertRaisesRegex(ValueError, 'Fresh'):
                candidate.build(BASE, first)
            bad = td/'bad.zip'
            bad.write_bytes(BASE.read_bytes()+b'changed')
            with self.assertRaisesRegex(ValueError, 'Exact original'):
                candidate.build(bad, td/'badout.zip')

    def test_float32_differential_and_removable_work(self):
        rng = random.Random(20261026)
        saved = active = 0
        for mode in range(4):
            for n in range(2500):
                z = -10 ** rng.uniform(-.5, 3.7)
                h = tuple(map(f, (rng.uniform(-.9,.9)*-z, rng.uniform(-.95,.95)*-z/1.4, z)))
                d = tuple(map(f, (rng.uniform(-.5,.5), rng.uniform(-1,1), rng.uniform(-1,-.001))))
                jitter = f(rng.random())
                original = trace(h, d, jitter, mode, False)
                on = trace(h, d, jitter, mode, True)
                if on[2]:
                    active += 1
                    saved += on[2]
                    self.assertGreater(original[0][1], 1.0)
                    self.assertEqual(original[1], 1.0)
                    self.assertGreater(on[0][1], 1.0)
                else:
                    self.assertEqual(original, on)
        self.assertGreater(active, 50)
        print(json.dumps({'modeled_rays': 10000, 'certified_zero_weight_rays': active,
                          'modeled_depth_queries_omitted': saved, 'driver_evidence': False}))

    def test_guard_rejects_eye_crossing_lower_border_and_ambiguous_slope(self):
        self.assertTrue(zero_tail((0, 20, -10), (0, 1, -.1), (.1,.1,.1)))
        for p,d in [((0,20,-10),(0,1,.1)), ((0,-20,-10),(0,-1,-.1)),
                    ((0,20,-10),(0,0,-.1)), ((0,20,-.01),(0,1,-.1)),
                    ((0,float('nan'),-10),(0,1,-.1))]:
            self.assertFalse(zero_tail(p,d,(.1,.1,.1)))

    def test_glsl450_translated_function_syntax_without_gl_context(self):
        # glslc requires modern GLSL/SPIR-V. This validates extracted syntax,
        # not the actual Iris GLSL120 driver compilation or its lowering.
        source = self.cpp(SHADERLOD_G05_DEBUG=1)
        source = source[:source.index('vec3 normal_waves(')]
        source = source.replace('texture2D(', 'texture(').replace('texture2DLod(', 'textureLod(')
        prefix = '''#version 450
layout(location=0) out vec4 result;
uniform mat4 gbufferProjection;
uniform sampler2D depthtex1;
uniform vec3 sunPosition;
uniform vec3 moonPosition;
uniform int worldTime;
uniform float rainStrength;
uniform vec2 lmcoord;
vec3 camera_to_screen(vec3 p) {vec4 c=gbufferProjection*vec4(p,1);return c.xyz/c.w*.5+.5;}
'''
        suffix = '\nvoid main(){float inf=0;result=vec4(shaderlodG05Raymarch(vec3(0,1,-.1),vec3(0,0,-10),inf,.5),inf);}\n'
        # cpp has substituted RAYMARCH_STEPS and SUN_REFLECTION.
        with tempfile.TemporaryDirectory() as td:
            path = Path(td)/'syntax.frag'
            path.write_text(prefix+source+suffix)
            command = ['glslc', '--target-env=opengl', '-fauto-map-locations', '-fauto-bind-uniforms', str(path), '-o', str(Path(td)/'syntax.spv')]
            result = subprocess.run(command, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stdout+result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
