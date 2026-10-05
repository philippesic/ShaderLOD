"""Scalar/source fixtures for the uniform-free policy; no compiler, ZIP or GPU.

Evaluate only this helper's flat scalar statements with rounded binary32 arithmetic.
This is an executable source fixture, not a GLSL implementation or driver validator.
"""
import math
from pathlib import Path
import re
import struct
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'shaderpacks/common/shaders/include/shaderlod/effect_policy.glsl'


class F32(float):
    def __new__(cls, value):
        try:
            rounded = struct.unpack('f', struct.pack('f', value))[0]
        except OverflowError:
            rounded = math.copysign(math.inf, value)
        return super().__new__(cls, rounded)

    def __add__(self, other): return F32(float(self) + float(other))
    def __radd__(self, other): return F32(float(other) + float(self))
    def __sub__(self, other): return F32(float(self) - float(other))
    def __rsub__(self, other): return F32(float(other) - float(self))
    def __mul__(self, other): return F32(float(self) * float(other))
    def __rmul__(self, other): return F32(float(other) * float(self))
    def __truediv__(self, other): return F32(float(self) / float(other))
    def __rtruediv__(self, other): return F32(float(other) / float(self))


class ScalarSourceFixture:
    """Deliberately limited to five brace-free scalar functions in the owned helper."""
    def __init__(self):
        self.source = SOURCE.read_text()
        clean = re.sub(r'//[^\n]*', '', self.source)
        self.functions = {}
        self.environment = {
            'isnan': math.isnan, 'isinf': math.isinf, 'min': min, 'max': max,
            'clamp': lambda value, low, high: min(high, max(low, value)),
            'ceil': lambda value: F32(math.ceil(value)), 'float': F32, 'int': int,
        }
        for name, value in re.findall(r'^#define (SHADERLOD_EFFECT_\w+) ([0-9.]+)$', clean, re.M):
            self.environment[name] = F32(float(value)) if '.' in value else int(value)
        for kind, name, parameters, body in re.findall(
                r'(bool|float|int) (shaderlod_effect_\w+)\((.*?)\)\s*\{([^{}]*)\}', clean, re.S):
            params = [tuple(p.split()) for p in parameters.split(',')]
            statements = [s.strip() for s in body.split(';') if s.strip()]
            self.functions[name] = (kind, params, statements)
            self.environment[name] = lambda *args, name=name: self.call(name, *args)
        if len(self.functions) != 5 or clean.count('{') != 5:
            raise AssertionError('Fixture requires the five explicit flat scalar functions')

    @staticmethod
    def expression(source, scope):
        source = source.replace('&&', ' and ').replace('||', ' or ')
        source = re.sub(r'!(?!=)', ' not ', source).strip()
        source = ' '.join(source.split())
        # Expressions come only from the owned source. No Python builtins or external I/O.
        return eval(source, {'__builtins__': {}}, scope)

    def call(self, name, *arguments):
        kind, parameters, statements = self.functions[name]
        if len(arguments) != len(parameters):
            raise AssertionError(f'Argument count mismatch for {name}')
        scope = dict(self.environment)
        for (param_kind, param_name), value in zip(parameters, arguments):
            scope[param_name] = F32(value) if param_kind == 'float' else value
        for statement in statements:
            conditional = re.fullmatch(r'if\s*\((.*)\)\s*return\s+(.*)', statement, re.S)
            if conditional:
                if not self.expression(conditional[1], scope):
                    continue
                value = self.expression(conditional[2], scope)
                return F32(value) if kind == 'float' else value
            returned = re.fullmatch(r'return\s+(.*)', statement, re.S)
            if returned:
                value = self.expression(returned[1], scope)
                return F32(value) if kind == 'float' else value
            assigned = re.fullmatch(r'(float|int)\s+(\w+)\s*=\s*(.*)', statement, re.S)
            if not assigned:
                raise AssertionError(f'Unsupported source statement: {statement}')
            value = self.expression(assigned[3], scope)
            scope[assigned[2]] = F32(value) if assigned[1] == 'float' else int(value)
        raise AssertionError(f'No result in {name}')


class EffectPolicyGlslTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = ScalarSourceFixture()

    def ratio(self, distance, bands=(32, 96, 8), ratios=(0.5, 0.25),
              flags=(True, True, False, False)):
        return self.fixture.call('shaderlod_effect_retained_ratio', *flags, distance, *bands, *ratios)

    def cap(self, upstream, minimum, distance, bands=(32, 96, 8), ratios=(0.5, 0.25),
            flags=(True, True, False, False)):
        return self.fixture.call('shaderlod_effect_work_cap', upstream, minimum,
                                 *flags, distance, *bands, *ratios)

    def test_helper_has_no_shader_or_transport_dependencies(self):
        clean = re.sub(r'//[^\n]*', '', self.fixture.source)
        self.assertNotRegex(clean, r'\b(uniform|sampler\w*|texture\w*|texelFetch|discard)\b')
        self.assertNotIn('#include', clean)
        self.assertNotIn('Photon', clean)
        self.assertNotIn('shaderlodBridgeVersion', clean)
        self.assertNotIn('shaderlodEffectsVersion', clean)
        self.assertNotIn('shaderlod_preset', clean)
        self.assertEqual(1, clean.count('int(ceil(float(upstream_work) * ratio))'))

    def test_transition_boundary_fixtures_match_java_contract(self):
        for distance, ratio, cap in ((0, 1, 64), (28, 1, 64), (30, .921875, 59),
                                     (32, .75, 48), (36, .5, 32), (92, .5, 32),
                                     (96, .375, 24), (100, .25, 16), (1000, .25, 16)):
            self.assertEqual(ratio, self.ratio(distance))
            self.assertEqual(cap, self.cap(64, 1, distance))

    def test_zero_width_and_touching_bands(self):
        self.assertEqual(1, self.ratio(31.999, bands=(32, 96, 0)))
        self.assertEqual(.5, self.ratio(32, bands=(32, 96, 0)))
        self.assertEqual(.25, self.ratio(96, bands=(32, 96, 0)))
        self.assertEqual(.5, self.ratio(64, bands=(32, 96, 64)))
        self.assertEqual(1, self.ratio(0, bands=(32, 96, 64)))
        self.assertEqual(.25, self.ratio(128, bands=(32, 96, 64)))

    def test_global_effect_full_and_protection_controls_preserve_work(self):
        for flags in ((False, True, False, False), (True, False, False, False),
                      (True, True, True, False), (True, True, False, True)):
            self.assertEqual(1, self.ratio(1000, ratios=(0, 0), flags=flags))
            self.assertEqual(64, self.cap(64, 1, 1000, ratios=(0, 0), flags=flags))

    def test_nonfinite_negative_and_out_of_domain_inputs_fail_open(self):
        for distance in (-1, math.nan, math.inf, -math.inf, 16777218):
            self.assertEqual(1, self.ratio(distance, ratios=(0, 0)))
            self.assertEqual(64, self.cap(64, 1, distance, ratios=(0, 0)))
        for bands in ((0, 96, 8), (96, 32, 8), (32, 32, 0), (32, 96, -1),
                      (32, 96, 65), (1, 96, 3), (32, 96, .0001),
                      (1024, 2048, 513), (32, 4097, 8), (math.nan, 96, 8),
                      (32, math.inf, 8), (32, 96, math.nan)):
            self.assertEqual(64, self.cap(64, 1, 1000, bands=bands, ratios=(0, 0)))
        for ratios in ((-1, 0), (.5, -1), (1.1, 1), (.25, .5), (math.nan, 0),
                       (1, math.nan), (math.inf, 0)):
            self.assertEqual(64, self.cap(64, 1, 1000, ratios=ratios))

    def test_integer_domain_floor_skip_and_upward_quantization(self):
        self.assertEqual(3, self.cap(9, 0, 1000))
        self.assertEqual(4, self.cap(9, 4, 1000))
        self.assertEqual(1, self.cap(1, 0, 1000))
        self.assertEqual(0, self.cap(9, 0, 1000, ratios=(0, 0)))
        self.assertEqual(1, self.cap(9, 1, 1000, ratios=(0, 0)))
        self.assertEqual(0, self.cap(0, 0, 1000))
        self.assertEqual(4194304, self.cap(16777216, 0, 1000))
        for upstream, minimum in ((-1, 0), (16777217, 0), (2147483647, 1), (64, -1), (64, 65)):
            self.assertEqual(upstream, self.cap(upstream, minimum, 1000, ratios=(0, 0)))

    def test_float_domain_endpoints_and_smallest_supported_band(self):
        self.assertEqual(.25, self.ratio(16777216, bands=(4095, 4096, 0)))
        width = 1 / 1024
        self.assertEqual(1, self.ratio(32 - width / 2, bands=(32, 96, width)))
        self.assertEqual(.75, self.ratio(32, bands=(32, 96, width)))
        self.assertEqual(.5, self.ratio(32 + width / 2, bands=(32, 96, width)))

    def test_continuity_and_caps_against_independent_double_oracle(self):
        def blend(distance, boundary):
            t = min(1, max(0, (distance - (boundary - 4)) / 8))
            return t * t * (3 - 2 * t)

        for upstream in (0, 1, 9, 24, 64, 65535, 16777216):
            previous = upstream
            for step in range(1025):
                distance = step / 8
                medium = 1 - .5 * blend(distance, 32)
                expected_ratio = medium * (1 - blend(distance, 96)) + .25 * blend(distance, 96)
                actual_ratio = self.ratio(distance)
                self.assertAlmostEqual(expected_ratio, actual_ratio, delta=2e-7)
                actual_cap = self.cap(upstream, 0, distance)
                self.assertGreaterEqual(actual_cap, 0)
                self.assertLessEqual(actual_cap, previous)
                # Rounded binary32 products may cross an integer threshold versus Java doubles.
                self.assertLessEqual(abs(actual_cap - math.ceil(upstream * expected_ratio)), 1)
                previous = actual_cap


if __name__ == '__main__':
    unittest.main()
