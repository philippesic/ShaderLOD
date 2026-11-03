"""Validity-tag clear regression. CPU/source fixtures, not driver rendering evidence."""
import copy
import json
import math
import os
from pathlib import Path
import random
import subprocess
import tempfile
import unittest

ROOT = Path(os.environ.get('SHADERLOD_SPATIAL_SOURCE_ROOT', Path(__file__).resolve().parents[1]))
BANK_ROOT = Path(os.environ.get('SHADERLOD_SPATIAL_BANK_ROOT', ROOT))
FAMILIES = ('Reimagined', 'Unbound')
INVALID = [0.0, 0.0, 0.0, -1.0]


def bank(family):
    return (BANK_ROOT / f'src/main/java/com/pp/shaderlod/optimization/{family.lower()}spatial/{family}SpatialBank.java').read_text()


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def inputs(family):
    return (ROOT / f'patches/adapters/{family.lower()}-spatial/inputs.glsl').read_text()


def valid(fields, frame):
    """Pinned SpatialInput predicate plus Payload, independent of clear policy."""
    a, b, c, d, payload = fields
    finite = lambda v: all(math.isfinite(x) and abs(x) < 1e10 for x in v)
    return (all(finite(v) for v in fields) and d[3] == frame and .56 <= d[2] < 1
            and math.sqrt(sum(x*x for x in c[:3])) > 256
            and 0 <= a[3] <= 1 and .99 < sum(x*x for x in a[:3]) < 1.01
            and 0 <= b[3] <= 1 and 0 <= c[3] <= 1 and 0 <= d[0] < 1000
            and 0 < d[1] < 16777216 and 0 <= payload[3] <= 1)


def exported(frame):
    return [[1., 0., 0., 1.], [300., 5., 2., .5], [300., 0., 0., 1.],
            [1., 42., .7, float(frame)], [.3, .4, .5, .2]]


class SpatialInputClearTest(unittest.TestCase):
    def test_exact_mirror_and_validity_domain(self):
        self.assertEqual(bank('Reimagined').replace('Reimagined', 'Family').replace('reimagined', 'family'),
                         bank('Unbound').replace('Unbound', 'Family').replace('unbound', 'family'))
        self.assertEqual(inputs('Reimagined').replace('Reimagined', 'Family').replace('reimagined', 'family'),
                         inputs('Unbound').replace('Unbound', 'Family').replace('unbound', 'family'))
        for family in FAMILIES:
            text = inputs(family)
            predicate = method(text, f'bool shaderlod{family}SpatialInput(')
            self.assertIn(f'd=texelFetch(shaderlod{family}SpatialInput3,p,0)', predicate)
            self.assertIn('d.w==float(frameCounter)', predicate)
            self.assertIn('d.z>=.56&&d.z<1.0', predicate)
            for file in ('lighting.glsl', 'resolve.glsl'):
                text = (ROOT / f'patches/adapters/{family.lower()}-spatial/{file}').read_text()
                self.assertIn(f'shaderlod{family}SpatialInput(', text)
            text = bank(family)
            self.assertIn('attach(inputFbo, input)', text)
            self.assertIn('GL30.GL_COLOR_ATTACHMENT0 + i', text)
            # Both coefficient clears remain; odd half-view edges and readbacks stay invalid.
            self.assertIn('for (int i = 0; i < 2; i++) GL30.glClearBufferfv', text)

    def test_native_mrt_export_is_complete_or_invalid(self):
        for family in FAMILIES:
            recipe = json.loads((ROOT / f'patches/adapters/complementary-{family.lower()}.json').read_text())
            values = []
            def walk(value):
                if isinstance(value, dict):
                    for k, v in value.items():
                        if k == 'after' and 'gl_FragData[7]=vec4(color.rgb,smoothnessG)' in str(v): values.append(v)
                        elif isinstance(v, (dict, list)): walk(v)
                elif isinstance(value, list):
                    for v in value: walk(v)
            walk(recipe)
            self.assertEqual(len(values), 1)
            text = values[0]
            self.assertIn('gl_FragData[6]=vec4(0.0,0.0,0.0,-1.0)', text)
            valid_export = text.split('if(shaderlodSpatialExport){', 1)[1].split('}else', 1)[0]
            for attachment in range(3, 8):
                self.assertIn(f'gl_FragData[{attachment}]=', valid_export)
            self.assertIn('gl_FragData[6]=vec4(highlightMult,float(mat),gl_FragCoord.z,float(frameCounter))', valid_export)
            self.assertNotIn('discard', valid_export)

    def test_poisoned_untouched_and_overdraw_fields(self):
        rng = random.Random(713)
        poison = [-1., 0., 1., float('nan'), float('inf'), -float('inf'), 1e20]
        # Includes frame0, wrap to0, repeated frame, startup, no geometry and partial coverage.
        for frame in (0, 1, 16777215, 0, 0, 19):
            for _ in range(100):
                old = [[rng.choice(poison) for _ in range(4)] for _ in range(5)]
                old[3] = exported(frame)[3]  # Deliberately plausible stale current-frame tag.
                full = [INVALID.copy() for _ in range(5)]
                minimal = copy.deepcopy(old)
                minimal[3] = INVALID.copy()
                self.assertFalse(valid(full, frame))
                self.assertFalse(valid(minimal, frame))
                # Successful eligible export writes all fields atomically through MRT.
                full = exported(frame)
                minimal = exported(frame)
                self.assertTrue(valid(full, frame))
                self.assertEqual(full, minimal)
                # Later ineligible fragment invalidates the tag, even with finite old payload.
                full[3] = INVALID.copy()
                minimal[3] = INVALID.copy()
                self.assertFalse(valid(full, frame))
                self.assertFalse(valid(minimal, frame))

    def test_actual_clear_method_calls_and_restoration(self):
        # Compile the actual production method in a GL recording fixture. This tests
        # routing and exception scope, but cannot establish real GL state or pixels.
        template = r'''
import java.util.*;
public class ClearFixture {
 static boolean fail; static int closedScopes; static List<String> events=new ArrayList<>();
 boolean closed; int scene=91,inputFbo=21,coefficientFbo=22;
 METHOD
 static class GL11 { static final int GL_COLOR=1; }
 static class GL30 {
  static final int GL_DRAW_FRAMEBUFFER=2;
  static void glClearBufferfv(int kind,int index,float[] value) {
   events.add("clear:"+index+":"+Arrays.toString(value));
   if(fail)throw new IllegalStateException("injected");
  }
 }
 static class GlStateManager { static void _glBindFramebuffer(int kind,int fbo){events.add("bind:"+fbo);} }
 static class TestSpatialState implements AutoCloseable {
  void fullscreen(){events.add("fullscreen");}
  public void close(){closedScopes++;}
 }
 static class MemoryStack implements AutoCloseable {
  static MemoryStack stackPush(){return new MemoryStack();}
  float[] floats(float... values){return values;}
  public void close(){}
 }
 public static void main(String[] ignored) {
  ClearFixture bank=new ClearFixture();bank.clearFrame();
  List<String> expected=List.of("fullscreen","bind:21","clear:3:[0.0, 0.0, 0.0, -1.0]",
   "bind:22","clear:0:[0.0, 0.0, 0.0, -1.0]","clear:1:[0.0, 0.0, 0.0, -1.0]");
  if(!events.equals(expected)||bank.scene!=0||closedScopes!=1)throw new AssertionError(events);
  events.clear();fail=true;
  try{bank.clearFrame();throw new AssertionError("failure swallowed");}catch(IllegalStateException good){}
  if(closedScopes!=2)throw new AssertionError("scope not restored");
  bank.closed=true;events.clear();
  try{bank.clearFrame();throw new AssertionError("closed bank accepted");}catch(IllegalStateException good){}
  if(!events.isEmpty())throw new AssertionError("closed bank touched GL");
 }
}
'''
        for family in FAMILIES:
            body = method(bank(family), 'public void clearFrame()').replace(family+'SpatialState', 'TestSpatialState')
            with tempfile.TemporaryDirectory(prefix='spatial-clear-') as temp:
                path = Path(temp)
                (path / 'ClearFixture.java').write_text(template.replace('METHOD', body))
                subprocess.run(['javac', '-d', temp, str(path/'ClearFixture.java')], check=True, capture_output=True, text=True)
                subprocess.run(['java', '-cp', temp, 'ClearFixture'], check=True, capture_output=True, text=True)


if __name__ == '__main__':
    unittest.main()
