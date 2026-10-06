"""Source-order binary32 spatial plans, independent policy oracle, pinned Iris grammar.

The restricted interpreter and grammar parser are not GLSL driver execution.
No renderer, cache, quality or physical-work/performance acceptance is implied.
"""
import hashlib
import io
import math
import os
from pathlib import Path
import random
import re
import struct
import subprocess
import tempfile
import unittest
import zipfile
from types import SimpleNamespace

from test_effect_policy_glsl import F32
from test_shared_lod_policy import BASE, WORK, SharedSourceFixture, policy, vec

ROOT = Path(__file__).resolve().parents[1]
SOURCE = BASE / 'spatial_plan.glsl'
FIELDS = 'weights requested_ratio reconstruction_ratio evaluation_ratio evaluate_full evaluate_medium evaluate_far reason'.split()


class SpatialSourceFixture(SharedSourceFixture):
    def __init__(self):
        super().__init__()
        self.environment['vec3'] = vec
        self.environment['ShaderlodSpatialPlan'] = lambda *args: SimpleNamespace(**dict(zip(FIELDS, args)))
        self.source = SOURCE.read_text()
        clean = re.sub(r'//[^\n]*', '', self.source)
        found = 0
        for match in re.finditer(r'(float|int|ShaderlodSpatialPlan) (shaderlod_spatial_\w+)\((.*?)\)\s*\{', clean, re.S):
            kind, name, parameters = match.groups()
            end = self.close(clean, match.end() - 1, '{', '}')
            self.functions[name] = (kind, [p.split() for p in parameters.split(',')], clean[match.end():end])
            self.environment[name] = lambda *args, name=name: self.call(name, *args)
            found += 1
        if found != 4 or clean.count('{') != 5:
            raise AssertionError('Expected one plan struct and four explicit helper functions')


def independent_blend(distance, boundary, width):
    """Java-style double arithmetic on transported scalar values, no source evaluator."""
    if width == 0:
        return float(distance >= boundary)
    parameter = max(0.0, min(1.0, (distance - (boundary - width / 2)) / width))
    return parameter * parameter * (3 - 2 * parameter)


def independent_plan(distance, bands, ratios):
    medium = independent_blend(distance, bands[0], bands[2])
    far = independent_blend(distance, bands[1], bands[2])
    contributions = (1 - medium) * (1 - far), medium * (1 - far), far
    weights = [0.0, 0.0, 0.0]
    for ratio, contribution in zip((1.0, *ratios), contributions):
        weights[(1.0, .25, .0625).index(ratio)] += contribution
    requested = sum(w * r for w, r in zip(weights, (1.0, .25, .0625)))
    return weights, requested


def adjacent_float(value, direction):
    bits = struct.unpack('I', struct.pack('f', value))[0]
    return struct.unpack('f', struct.pack('I', bits + direction))[0]


class SpatialLodPlanTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = SpatialSourceFixture()

    def plan(self, distance=1000, ratios=(.25, .0625), bands=(32, 96, 8), mask=7,
             flags=(True, True, False), **overrides):
        return self.fixture.call('shaderlod_spatial_plan_decide',
            policy(retained_ratios=vec(*ratios), bands_blocks=vec(*bands), **overrides),
            *flags, distance, mask)

    def assert_plan(self, result):
        weights = tuple(getattr(result.weights, axis) for axis in 'xyz')
        self.assertTrue(all(math.isfinite(w) and 0 <= w <= 1 for w in weights))
        self.assertAlmostEqual(1, sum(weights), delta=2e-7)
        active = tuple(w > 0 for w in weights)
        self.assertEqual(active, (result.evaluate_full, result.evaluate_medium, result.evaluate_far))
        self.assertEqual(sum(rate for rate, selected in zip((1, .25, .0625), active) if selected), result.evaluation_ratio)
        self.assertAlmostEqual(sum(w * r for w, r in zip(weights, (1, .25, .0625))), result.reconstruction_ratio, delta=2e-7)
        return weights

    def assert_full(self, result, reason):
        self.assertEqual((1, 0, 0), self.assert_plan(result))
        self.assertEqual((1, 1, reason), (result.reconstruction_ratio, result.evaluation_ratio, result.reason))

    def test_shared_dependency_and_no_sampling_or_radiance_operation(self):
        clean = re.sub(r'//[^\n]*', '', self.fixture.source)
        self.assertIn('shaderlod_work_decide(', clean)
        self.assertNotRegex(clean, r'\b(uniform|sampler\w*|texture\w*|texelFetch|dFdx|dFdy|fwidth|discard|Photon)\b')
        self.assertNotIn('#include', clean)
        self.assertNotIn('work.realized_work', clean)
        self.assertNotIn('work.requested_work', clean)

    def test_centered_endpoints_transition_weights_and_extra_work(self):
        cases = ((0, (1, 0, 0)), (28, (1, 0, 0)), (30, (.84375, .15625, 0)),
                 (32, (.5, .5, 0)), (36, (0, 1, 0)), (92, (0, 1, 0)),
                 (96, (0, .5, .5)), (100, (0, 0, 1)), (16777216, (0, 0, 1)))
        for distance, expected in cases:
            result = self.plan(distance)
            self.assertEqual(expected, self.assert_plan(result))
            self.assertEqual(0, result.reason)
        first = self.plan(32)
        self.assertEqual((.625, .625, 1.25), (first.requested_ratio, first.reconstruction_ratio, first.evaluation_ratio))
        second = self.plan(96)
        self.assertEqual((.15625, .3125), (second.requested_ratio, second.evaluation_ratio))

    def test_all_legal_endpoint_combinations_collapse_duplicate_tiers(self):
        for medium in (1, .25, .0625):
            for far in (1, .25, .0625):
                if far > medium:
                    continue
                for distance in (0, 28, 32, 36, 64, 92, 96, 100, 1000):
                    result = self.plan(distance, ratios=(medium, far))
                    expected, request = independent_plan(distance, (32, 96, 8), (medium, far))
                    self.assertEqual(tuple(expected), self.assert_plan(result))
                    self.assertEqual(request, result.requested_ratio)
        self.assertEqual(1, self.plan(32, ratios=(1, 1)).evaluation_ratio)
        self.assertEqual(.25, self.plan(96, ratios=(.25, .25)).evaluation_ratio)
        self.assertEqual(1.0625, self.plan(96, ratios=(1, .0625)).evaluation_ratio)

    def test_unsupported_endpoints_and_masks_never_snap(self):
        for ratios in ((.5, .25), (.25, 0), (.75, .5), (.1, .0625), (.25, adjacent_float(.0625, 1))):
            for distance in (0, 32, 96, 1000):
                result = self.plan(distance, ratios=ratios)
                self.assert_full(result, 7)
                self.assertGreaterEqual(result.requested_ratio, ratios[1])
        for mask in range(1, 8):
            for ratios in ((1, 1), (1, .25), (1, .0625), (.25, .25), (.25, .0625), (.0625, .0625)):
                result = self.plan(ratios=ratios, mask=mask)
                if not mask & 1:
                    self.assert_full(result, 1)
                elif any(not mask & {1: 1, .25: 2, .0625: 4}[ratio] for ratio in ratios):
                    self.assert_full(result, 7)
                else:
                    self.assertEqual(0, result.reason)
                    self.assert_plan(result)
        for mask in (-1, 0, 8, 2147483647):
            self.assert_full(self.plan(mask=mask), 1)

    def test_shared_modes_protocol_quality_and_protection_fallback(self):
        for overrides, flags, reason in ((dict(global_enabled=False), (True, True, False), 2),
                (dict(mode=1), (True, True, False), 3), (dict(mode=0), (True, True, False), 4),
                (dict(policy_enabled=False), (True, True, False), 5),
                ({}, (True, True, True), 6), ({}, (False, True, False), 7), ({}, (True, False, False), 8)):
            result = self.plan(flags=flags, **overrides)
            self.assert_full(result, reason)
            self.assertEqual(1, result.requested_ratio)
        for key, values in (('core_version', (-1, 0, 1, 3)), ('effects_version', (0, 3)),
                            ('minimum_effects_version', (0, 3)), ('mode', (-1, 3))):
            for value in values:
                self.assert_full(self.plan(**{key: value}), 1)
        self.assert_full(self.plan(effects_version=1, minimum_effects_version=2), 1)
        self.assertEqual(0, self.plan(effects_version=1).reason)
        self.assertEqual(0, self.plan(mode=0, inherited_policy_resolved=True).reason)

    def test_nonfinite_invalid_domains_and_float32_endpoints(self):
        for distance in (-1, math.nan, math.inf, -math.inf, 16777218):
            self.assert_full(self.plan(distance), 1)
        for bands in ((0, 96, 8), (32, 32, 0), (32, 96, -1), (32, 96, 65),
                      (32, 96, .0001), (32, 4097, 8), (32, 96, math.nan), (32, math.inf, 8)):
            self.assert_full(self.plan(bands=bands), 1)
        for ratios in ((-1, 0), (1.1, 1), (.0625, .25), (1, math.nan), (math.inf, 0)):
            self.assert_full(self.plan(ratios=ratios), 1)
        for width, near, far in ((1/1024, 32, 96), (1/1024, 4095, 4096), (8, 32, 96)):
            for boundary in (near, far):
                for endpoint in (boundary - width/2, boundary + width/2):
                    for distance in (adjacent_float(endpoint, -1), endpoint, adjacent_float(endpoint, 1)):
                        result = self.plan(distance, bands=(near, far, width))
                        expected, requested = independent_plan(float(F32(distance)), (near, far, width), (.25, .0625))
                        for actual, reference in zip(self.assert_plan(result), expected):
                            self.assertAlmostEqual(reference, actual, delta=2e-7)
                        self.assertAlmostEqual(requested, result.requested_ratio, delta=2e-7)

    def test_zero_width_touching_bands_and_monotonic_scalar_oracle(self):
        for distance, expected in ((adjacent_float(32, -1), (1, 0, 0)), (32, (0, 1, 0)), (96, (0, 0, 1))):
            self.assertEqual(expected, self.assert_plan(self.plan(distance, bands=(32, 96, 0))))
        self.assertEqual((0, 1, 0), self.assert_plan(self.plan(64, bands=(32, 96, 64))))
        rng = random.Random(3051)
        for ratios in ((1, 1), (1, .25), (1, .0625), (.25, .25), (.25, .0625), (.0625, .0625)):
            previous = 1.0
            for distance in sorted([float(F32(rng.uniform(0, 128))) for _ in range(100)]):
                result = self.plan(distance, ratios=ratios)
                expected, requested = independent_plan(distance, (32, 96, 8), ratios)
                for actual, reference in zip(self.assert_plan(result), expected):
                    self.assertAlmostEqual(reference, actual, delta=2e-7)
                self.assertAlmostEqual(requested, result.requested_ratio, delta=2e-7)
                self.assertLessEqual(result.reconstruction_ratio, previous + 2e-7)
                self.assertLessEqual(sum((result.evaluate_full, result.evaluate_medium, result.evaluate_far)), 2)
                previous = result.reconstruction_ratio

    def test_float32_smoothstep_rounding_cannot_make_negative_weights(self):
        # Sample both transition interiors densely near their completed endpoints.
        for boundary in (512, 1536):
            endpoint = boundary + 256
            distance = float(F32(endpoint))
            for _ in range(2048):
                distance = adjacent_float(distance, -1)
                self.assert_plan(self.plan(distance, bands=(512, 1536, 512)))

    def test_actual_dependency_free_java_policy_oracle(self):
        cases = [(distance, width, medium, far) for width in (0, 8, 64)
                 for medium, far in ((1, 1), (1, .25), (1, .0625), (.25, .25), (.25, .0625), (.0625, .0625))
                 for distance in (0, 28, 32, 36, 64, 92, 96, 100, 128)]
        java = 'import com.pp.shaderlod.lod.*; class SpatialOracle {public static void main(String[] a){'
        for distance, width, medium, far in cases:
            java += f'{{var p=new EffectPolicy(true,false,new DistanceBands(32,96,{width}),{medium},{far});System.out.println(p.decide(true,Effect.OPAQUE_LIGHTING,{distance},16,0).retentionRatio());}}'
        java += '}}'
        with tempfile.TemporaryDirectory(prefix='shaderlod-spatial-oracle-') as temporary:
            folder = Path(temporary); source = folder / 'SpatialOracle.java'; source.write_text(java)
            inputs = [ROOT/'src/main/java/com/pp/shaderlod/lod'/f'{name}.java' for name in ('DistanceBands', 'EffectPolicy', 'Effect', 'WorkBudget')]
            compiled = subprocess.run(['javac', '-d', str(folder), *map(str, inputs), str(source)], capture_output=True, text=True, timeout=30)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            ran = subprocess.run(['java', '-cp', str(folder), 'SpatialOracle'], capture_output=True, text=True, timeout=30)
            self.assertEqual(0, ran.returncode, ran.stderr)
        self.assertEqual(len(cases), len(ran.stdout.splitlines()))
        for (distance, width, medium, far), line in zip(cases, ran.stdout.splitlines()):
            result = self.plan(distance, bands=(32, 96, width), ratios=(medium, far))
            self.assertAlmostEqual(float(line), result.requested_ratio, delta=2e-7)

    def test_actual_pinned_iris_glsl410_grammar_and_no_options(self):
        iris = ROOT/'dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        self.assertEqual('3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83', hashlib.sha256(iris.read_bytes()).hexdigest())
        cache = Path.home()/'.gradle/caches/modules-2/files-2.1'; dependencies = []
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar',
                        'it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar',
                        'org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar',
                        'org.antlr/antlr4-runtime/4.13.1/*/antlr4-runtime-4.13.1.jar'):
            matches = list(cache.glob(pattern)); self.assertEqual(1, len(matches), pattern); dependencies.append(matches[0])
        self.assertEqual('54665d2838cc66458343468efc539e454fc95b46a8a04b13c6ac43fc9be63505', hashlib.sha256(dependencies[-1].read_bytes()).hexdigest())
        java = r'''
import java.nio.file.*;import java.util.*;
import net.irisshaders.iris.shaderpack.option.OptionAnnotatedSource;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import io.github.douira.glsl_transformer.parser.EnhancedParser;
import org.antlr.v4.runtime.misc.ParseCancellationException;
class SpatialGrammarProof {
 static void parse(String source){var p=new EnhancedParser();p.setThrowParseErrors(true);p.parse(source);}
 public static void main(String[] a)throws Exception {
  String source=Files.readString(Path.of(a[0]));var annotated=new OptionAnnotatedSource(source);
  var refs=annotated.getBooleanDefineReferences().keySet();var options=annotated.getOptionSet(AbsolutePackPath.fromAbsolutePath("/spatial.glsl"),refs);
  if(!refs.isEmpty()||!options.getBooleanOptions().isEmpty()||!options.getStringOptions().isEmpty())throw new AssertionError("option leak");
  for(int mode:new int[]{0,1,2})for(String ratios:new String[]{"1,1","1,.25","1,.0625",".25,.25",".25,.0625",".0625,.0625"}) {
   String main="\nvoid main(){ShaderlodWorkPolicy p=ShaderlodWorkPolicy(2,2,2,true,"+mode+",true,true,vec3(32,96,8),vec2("+ratios+"));"+
    "ShaderlodSpatialPlan s=shaderlod_spatial_plan_decide(p,true,true,false,32.0,7);"+
    "if(s.evaluate_full){s=shaderlod_spatial_plan_full(s.requested_ratio,s.reason);}"+
    "gl_FragColor=vec4(s.weights,s.evaluation_ratio);}";
   String processed=JcppProcessor.glslPreprocessSource("#version 410 compatibility\n"+source+"\n"+source+main,List.of());
   if(processed.split("struct ShaderlodSpatialPlan",-1).length!=2)throw new AssertionError("include guard failed");parse(processed);
  }
  String broken=source.replace("int supported_rates) {","int supported_rates {");
  try{parse(JcppProcessor.glslPreprocessSource("#version 410 compatibility\n"+broken,List.of()));throw new AssertionError("malformed parameter accepted");}catch(ParseCancellationException expected){}
  System.out.println("18 GLSL410 graphs parsed; no Iris option leak; repeated include guard and malformed parameter checks passed");
 }
}'''
        with tempfile.TemporaryDirectory(prefix='shaderlod-spatial-grammar-') as temporary:
            folder = Path(temporary); embedded = []
            with zipfile.ZipFile(iris) as archive:
                for name in ('glsl-transformer-2.0.1.jar', 'jcpp-1.4.14.jar'):
                    data = archive.read('META-INF/jarjar/'+name)
                    if name == 'glsl-transformer-2.0.1.jar':
                        self.assertEqual('b8ca5e61b41ae18ddd5d4d5d6785cb9acd8fd7ac0c5c0b0689b872b039c11b9f', hashlib.sha256(data).hexdigest())
                        with zipfile.ZipFile(io.BytesIO(data)) as parser:
                            self.assertEqual('11133ca7cbfbc89777b15f06a31323b26c1ab6448b1822f4086e9503f3527814', hashlib.sha256(parser.read('io/github/douira/glsl_transformer/parser/EnhancedParser.class')).hexdigest())
                    path = folder/name; path.write_bytes(data); embedded.append(path)
            expanded = WORK.read_text().replace('#include "/include/shaderlod/effect_policy.glsl"', (BASE/'effect_policy.glsl').read_text())+'\n'+SOURCE.read_text()
            fixture = folder/'expanded.glsl'; fixture.write_text(expanded)
            source = folder/'SpatialGrammarProof.java'; source.write_text(java)
            result = subprocess.run(['java', '--class-path', os.pathsep.join(map(str, [iris, *dependencies, *embedded])), str(source), str(fixture)], capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout+result.stderr)
            self.assertIn('18 GLSL410 graphs parsed', result.stdout)


if __name__ == '__main__':
    unittest.main()
