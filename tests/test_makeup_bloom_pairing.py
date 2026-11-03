"""G10 bounded numeric/source/compiler checks. Never creates an OpenGL context."""
import importlib.util
import json
import math
from pathlib import Path
import random
import re
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
spec = importlib.util.spec_from_file_location('g10', ROOT / 'scripts/build_makeup_bloom_pairing.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def preprocess(files, mode=0, extra=None, dimension='world0', dof=False, aa=2, bloom=True):
    def expand(name):
        return re.sub(r'^\s*#include\s+"(/[^\"]+)"\s*$',
                      lambda match: expand('shaders' + match[1]), files[name].decode(), flags=re.M)
    source = expand(f'shaders/{dimension}/composite1.fsh')
    source = source.replace('#define ' + m.OPTION + ' 0', '#define ' + m.OPTION + ' ' + str(mode))
    source = source.replace('// #define DOF ', '#define DOF ' if dof else '// #define DOF ')
    source = source.replace('#define AA_TYPE 2 ', '#define AA_TYPE ' + str(aa) + ' ')
    if not bloom:
        source = source.replace('#define BLOOM //', '// #define BLOOM //')
    source = re.sub(r'^\s*#(?:version|extension).*$', '', source, flags=re.M)
    macros = {'IS_IRIS': 1, 'IRIS_VERSION': 10812, 'MC_VERSION': 12101}
    macros.update(extra or {})
    macros = {k: v for k, v in macros.items() if v is not None}
    result = subprocess.run(['clang', '-E', '-P', '-x', 'c'] +
                            [f'-D{k}={v}' for k, v in macros.items()] + ['-'],
                            input=source, text=True, capture_output=True, check=True)
    return result.stdout


def cell(coords, radius, size):
    lo = [(c - r) * s - .5 for c, r, s in zip(coords, radius, size)]
    hi = [(c + r) * s - .5 for c, r, s in zip(coords, radius, size)]
    return all(l >= .01 and h <= s - 1.01 and math.floor(l - .01) == math.floor(h + .01)
               for l, h, s in zip(lo, hi, size))


def taps(v):
    x, y = v
    return [(x, y), (-x, -y), (-1.25*y, 1.25*x), (1.25*y, -1.25*x)]


def filtered(values, uv, bits=None):
    x, y = uv
    if bits:
        x, y = round(x * 2**bits) / 2**bits, round(y * 2**bits) / 2**bits
    a, b, c, d = values
    return (a*(1-x) + b*x)*(1-y) + (c*(1-x) + d*x)*y


def quantize_hdr(value, mantissa):
    # Positive normal R11F/G11F/B10F domain used by the native bloom source.
    if value == 0:
        return value
    exponent = max(-14, math.floor(math.log2(value)))
    step = 2**(exponent - mantissa)
    return round(value / step) * step


class PairingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base = m.read_archive(ROOT / 'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip')
        cls.candidate = m.patch(cls.base)

    def test_pinned_release_and_change_scope(self):
        self.assertEqual(m.sha((ROOT / 'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip').read_bytes()), m.BASE_SHA256)
        self.assertEqual(set(self.base), set(self.candidate))
        self.assertEqual({n for n in self.base if self.base[n] != self.candidate[n]},
                         {m.CONFIG, m.BLOOM, m.CALLER, m.PROPS, m.LANG, m.ENTRY})
        for name in self.base:
            if (name.endswith(('.vsh', '.fsh')) and name != m.ENTRY) or 'LICENSE' in name or 'fast_taa' in name or 'fxaa' in name:
                self.assertEqual(self.base[name], self.candidate[name])

    def test_off_and_mismatch_preprocess_to_native(self):
        cases = [dict(mode=0), dict(mode=99), dict(extra={'IRIS_VERSION': 10814}),
                 dict(extra={'MC_VERSION': 12102}), dict(extra={'IS_IRIS': None}),
                 dict(extra={'IRIS_VERSION': None}), dict(extra={'DISTANT_HORIZONS': 1}),
                 dict(extra={'VOXY': 1}), dict(dimension='world-1'), dict(dimension='world1'),
                 dict(bloom=False)]
        for case in cases:
            kwargs = dict(mode=1)
            kwargs.update(case)
            with self.subTest(case=case):
                self.assertEqual(re.sub(r'\s+', ' ', preprocess(self.base, **kwargs)).strip(),
                                 re.sub(r'\s+', ' ', preprocess(self.candidate, **kwargs)).strip())

    def test_native_option_definitions_and_profiles_preserved(self):
        # Whole original config is an exact suffix/prefix around the one insertion.
        new_config = self.candidate[m.CONFIG].decode()
        insertion = new_config[new_config.index('// ShaderLOD G10 private'):new_config.index('#define REFLECTION_SLIDER')]
        self.assertEqual(new_config.replace(insertion, ''), self.base[m.CONFIG].decode())
        for line in self.base[m.PROPS].splitlines():
            if line.startswith(b'profile.'):
                self.assertIn(line, self.candidate[m.PROPS].splitlines())

    def test_zero_dither_certificate_keeps_native_fallback_and_additions(self):
        import struct
        f32 = lambda x: struct.unpack('f', struct.pack('f', x))[0]
        files = m.patch(self.base, zero_dither_only=True)
        for mode in [0, 99]:
            self.assertEqual(re.sub(r'\s+', ' ', preprocess(self.base, mode=mode)).strip(),
                             re.sub(r'\s+', ' ', preprocess(files, mode=mode)).strip())
        for mode in [3, 4]:
            source = preprocess(files, mode=mode)
            self.assertIn('dither == 0.0', source)
            self.assertNotIn('g10A', source)
            self.assertEqual(source.count('blurSample += g10Sample;'), 4)
            self.assertIn(m.NATIVE_TAPS, source)
            with tempfile.TemporaryDirectory() as tmp:
                fragment = Path(tmp)/'zero.frag'
                fragment.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+source)
                compiled = subprocess.run(['glslangValidator','-S','frag',str(fragment)],text=True,capture_output=True)
                self.assertEqual(compiled.returncode,0,compiled.stdout+compiled.stderr)
        # Pinned fullscreen positive UV: +/- either signed zero retains each
        # native coordinate exactly. No bilinear moment or reassociation used.
        for width, height in [(1920,1200),(1919,1199),(4096,4096)]:
            for x,y in [(0,0),(width//2,height//2),(width-1,height-1)]:
                for c in [f32((x+.5)/width),f32((y+.5)/height)]:
                    self.assertGreater(c,0)
                    for z in [0.0,-0.0]:
                        self.assertEqual(f32(c+z),c)
                        self.assertEqual(f32(c-z),c)

    def test_cell_guards_reject_borders_and_crossings(self):
        self.assertFalse(cell((.001, .5), (.01, .01), (240, 150)))
        self.assertFalse(cell((.999, .5), (.01, .01), (240, 150)))
        self.assertFalse(cell((.5, .5), (.1, .1), (240, 150)))
        self.assertFalse(cell((.5, .5), (0, 0), (1, 1)))
        self.assertTrue(cell((.5, .5), (.0001, .0001), (240, 150)))
        # Odd texture sizes: checking just the lower mip is insufficient.
        self.assertTrue(cell((.5, .5), (.0001, .0001), (240, 150)))
        self.assertFalse(cell((.5, .5), (.0001, .0001), (119, 75)))

    def test_bilinear_moment_identity_quantized_hdr_and_trilinear(self):
        rng = random.Random(10)
        largest = 0.0
        quantized_max = 0.0
        for _ in range(12000):
            x, y = rng.uniform(.15, .85), rng.uniform(.15, .85)
            v = (rng.uniform(-.1, .1), rng.uniform(-.1, .1))
            offsets = taps(v)
            pair = [(v[0]*.75, v[1]*-.375), (-v[0]*.75, v[1]*.375)]
            native, paired, nq, pq = [], [], [], []
            for level in range(2):
                values = [quantize_hdr(rng.random()*50, rng.choice([5, 6])) for _ in range(4)]
                native.append(sum(filtered(values, (x+dx, y+dy)) for dx, dy in offsets) / 4)
                paired.append(sum(filtered(values, (x+dx, y+dy)) for dx, dy in pair) / 2)
                nq.append(sum(filtered(values, (x+dx, y+dy), 8) for dx, dy in offsets) / 4)
                pq.append(sum(filtered(values, (x+dx, y+dy), 8) for dx, dy in pair) / 2)
            blend = rng.random()
            n = native[0]*(1-blend) + native[1]*blend
            p = paired[0]*(1-blend) + paired[1]*blend
            largest = max(largest, abs(n-p))
            quantized_max = max(quantized_max, abs((nq[0]-pq[0])*(1-blend) + (nq[1]-pq[1])*blend))
        self.assertLess(largest, 1e-12)
        # A model of 8-bit filter weights is NOT bit-exact. Bound conservatively.
        self.assertLess(quantized_max, 4*50/256)
        print(json.dumps({'real_arithmetic_max_absolute_error': largest,
                          'eight_bit_filter_weight_model_max_absolute_error': quantized_max,
                          'cases': 12000, 'driver_precision_proven': False}))

    def test_reject_wrong_archive_preserve_original_and_options(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)
            wrong = path / 'wrong.zip'
            wrong.write_bytes(b'wrong')
            with self.assertRaises(ValueError):
                m.build(wrong, path/'candidate.zip')
        raw = b'# native selected options\nAA_TYPE=3\nDOF=true\nBLOOM_SAMPLES=3.0\n'
        for mode in [0, 1, 2]:
            self.assertEqual(m.options_bytes(raw, mode), raw + f'{m.OPTION}={mode}\n'.encode())
        with self.assertRaises(ValueError):
            m.options_bytes(m.options_bytes(raw, 0), 1)

    def test_deterministic_archive(self):
        self.assertEqual(m.archive_bytes(self.candidate), m.archive_bytes(self.candidate))

    def test_glsl120_compiler_matrix_without_driver(self):
        matrix = [dict(mode=0), dict(mode=1), dict(mode=2), dict(mode=1, dof=True),
                  dict(mode=1, aa=0), dict(mode=1, aa=3), dict(mode=1, bloom=False),
                  dict(mode=1, dimension='world-1'), dict(mode=1, dimension='world1'),
                  dict(mode=1, extra={'DISTANT_HORIZONS': 1}),
                  dict(mode=1, extra={'VOXY': 1}), dict(mode=1, extra={'IRIS_VERSION': 10814})]
        with tempfile.TemporaryDirectory() as tmp:
            for i, kwargs in enumerate(matrix):
                source = '#version 120\n#extension GL_ARB_shader_texture_lod : enable\n' + preprocess(self.candidate, **kwargs)
                file = Path(tmp)/f'case{i}.frag'
                file.write_text(source)
                result = subprocess.run(['glslangValidator', '-S', 'frag', str(file)],
                                        text=True, capture_output=True)
                self.assertEqual(result.returncode, 0, str(kwargs) + '\n' + result.stdout + result.stderr)
        print(json.dumps({'offline_fragment_compilations': len(matrix), 'driver_compiled': False}))


if __name__ == '__main__':
    unittest.main(verbosity=2)
