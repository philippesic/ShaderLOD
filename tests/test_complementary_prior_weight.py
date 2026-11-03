"""Authentic isolated source/fallback checks; CPU arithmetic is not driver proof."""
from pathlib import Path
import copy
import json
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from prepare_complementary_prior_weight import (
    RECIPE, MEMBER, GUARD, LAST, TAIL, NATIVE_CURRENT, DECLARATION, CURRENT,
    UPDATE, CERTIFICATE, CERTIFICATE_SHA, candidate_manifest, prepare,
)
from build_supported_pack_adapters import apply, read_archive, sha256, CATALOG, RESOURCE_ROOT

BASE = ROOT / 'dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip'


class ComplementaryPriorWeightTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not BASE.is_file():
            raise unittest.SkipTest('Authentic pinned archive unavailable')
        cls.current = json.loads((ROOT / RECIPE).read_bytes())
        cls.original = read_archive(BASE)
        cls.assembled = apply(cls.original, cls.current)
        cls.candidate = candidate_manifest(cls.current)
        cls.changed = apply(cls.original, cls.candidate)

    def test_exact_body_recovery_and_consumer_scope(self):
        self.assertEqual(set(self.changed), set(self.assembled))
        self.assertEqual([n for n in self.changed if self.changed[n] != self.assembled[n]], [MEMBER])
        body = self.changed[MEMBER]
        restored = body.replace((LAST + '\r\n' + DECLARATION).encode(), LAST.encode(), 1)
        restored = restored.replace(CURRENT.encode(), NATIVE_CURRENT.encode(), 1)
        restored = restored.replace((TAIL + '\r\n' + UPDATE).encode(), TAIL.encode(), 1)
        self.assertEqual(restored, self.assembled[MEMBER])
        self.assertEqual(self.current['additions'], self.candidate['additions'])
        # Exact native placement: first expression stays after near/end breaks,
        # entity rejection stays after weights, and carry updates after accumulation.
        self.assertLess(body.index(b'if (nextDistance >= rayEnd) break;'), body.index(CURRENT.encode()))
        self.assertLess(body.index(CURRENT.encode()), body.index(b'if (nextDistance > entityLinearDepth) break;'))
        self.assertLess(body.index(b'volumetricLight += stepResult;'), body.index(UPDATE.encode()))
        loop = self.assembled[MEMBER].decode().split('for (int i = 0; i <= totalSamples; i++) {', 1)[1]
        loop = loop.split('// ============================== End of Step 3', 1)[0]
        self.assertNotIn('continue', loop)
        self.assertIsNone(re.search(r'\b(?:maxDistance|fogCurve)\s*=', loop))
        entry = self.original['shaders/world0/composite1.fsh'].decode()
        consumer = self.original['shaders/program/composite1.glsl'].decode()
        self.assertIn('#define OVERWORLD', entry)
        self.assertIn('#define COMPOSITE1', entry)
        self.assertIn('#include "/program/composite1.glsl"', entry)
        self.assertIn('#include "/lib/atmospherics/volumetricLight/volumetricLight.glsl"', consumer)
        self.assertIn('GetVolumetricLight(vlFactorM', consumer)

    def test_actual_preprocessor_excluded_paths_equal_current(self):
        clang = shutil.which('clang')
        if not clang:
            self.skipTest('C preprocessor unavailable')
        def expand(files, name):
            return re.sub(r'#include "(/[^\"]+)"',
                lambda m: expand(files, 'shaders' + m.group(1)), files[name].decode())
        baseline, candidate = expand(self.assembled, MEMBER), expand(self.changed, MEMBER)
        baseline = baseline.replace('#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]',
                                    '#define SHADERLOD_REFINEMENT_ADAPTER 1 // [0 1]')
        candidate = candidate.replace('#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]',
                                      '#define SHADERLOD_REFINEMENT_ADAPTER 1 // [0 1]')
        def cpp(source, definitions):
            prefix = ''.join('#define ' + k + ' ' + str(v) + '\n' for k, v in definitions.items())
            return subprocess.run([clang, '-E', '-P', '-x', 'c', '-'], input=prefix + source,
                                  text=True, capture_output=True, check=True).stdout
        active = dict(OVERWORLD=1, COMPOSITE1=1, RP_MODE=1, LIGHTSHAFT_QUALI_DEFINE=2,
                      SHADOW_QUALITY=2, DETAIL_QUALITY=2)
        self.assertIn('shaderlodPriorVLWeight', cpp(candidate, active))
        cases = [dict(active, **{x: 1}) for x in
                 ('DISTANT_HORIZONS', 'VOXY', 'GENERATED_NORMALS', 'COATED_TEXTURES')]
        cases += [dict(active, RP_MODE=0), {k:v for k,v in active.items() if k != 'COMPOSITE1'},
                  {**{k:v for k,v in active.items() if k != 'OVERWORLD'}, 'END': 1}]
        for definitions in cases:
            with self.subTest(definitions=definitions):
                self.assertEqual(cpp(candidate, definitions), cpp(baseline, definitions))
        # Adapter option Off removes all candidate declarations and operations too.
        self.assertEqual(cpp(expand(self.changed, MEMBER), active),
                         cpp(expand(self.assembled, MEMBER), active))

    def test_actual_inserted_scalar_snippets_runtime_fallback_and_exit_counts(self):
        compiler = shutil.which('clang++')
        if not compiler:
            self.skipTest('C++ compiler unavailable')
        # Compile the actual candidate snippets, not a second Python carry formula.
        # GLSL vector equality's constant predicate is represented as scalar here;
        # native pow/rounding/texture operations remain outside this CPU claim.
        declaration = DECLARATION.replace('vec3(1.0)', '1.0f')
        defines = '\n'.join('#define ' + x for x in ('COMPOSITE1', 'OVERWORLD'))
        source = r'''
#include <cmath>
#include <cassert>
#include <iostream>
#include <vector>
static int evaluations;
float smoothstep1(float x) { ++evaluations; return x*x*(3.0f-2.0f*x); }
using std::pow;
#define SHADERLOD_REFINEMENT_ADAPTER 1
#define RP_MODE 1
DEFINES
int main() {
  int cases=0;
  for(int bridge: {-1,0,1,2,3}) for(int enabled: {-1,0,1,2})
  for(int preset: {-1,0,1,2,3,4}) for(int eye: {0,1}) for(float trans: {1.0f,0.5f})
  for(float corner: {0.0f,2.0f}) for(int visits: {0,1,2,9,14})
  for(bool entityReject: {false,true}) {
    int shaderlodBridgeVersion=bridge,shaderlodEnabled=enabled,shaderlodPreset=preset,isEyeInWater=eye;
    float translucentMult=trans,viewWidth=corner,viewHeight=0.0f;
    struct {float x,y;} gl_FragCoord{0.0f,0.0f};
    float maxDistance=128.0f,fogCurve=0.9f;
    float lastDistance=0.0f;
DECLARATION
    bool active = bridge==2 && enabled==1 && preset==0 && eye==0 && trans==1.0f && corner>=1.5f;
    std::vector<float> actual;
    evaluations=0;
    for(int i=0;i<visits;i++) {
      float nextDistance=float(i+1)*3.75f;
CURRENT
      float nextWeight=smoothstep1(pow(nextDistance/maxDistance,fogCurve));
      float sliceWeight=nextWeight-currentWeight;
      actual.push_back(sliceWeight);
      // Entity rejection happens after both weights, before tail assignment.
      if(entityReject && i==visits-1) break;
      lastDistance=nextDistance;
UPDATE
    }
    int observed=evaluations;
    assert(observed == (active && visits>0 ? visits+1 : visits*2));
    evaluations=0; lastDistance=0.0f;
    for(int i=0;i<visits;i++) {
      float nextDistance=float(i+1)*3.75f;
      float currentWeight=smoothstep1(pow(lastDistance/maxDistance,fogCurve));
      float nextWeight=smoothstep1(pow(nextDistance/maxDistance,fogCurve));
      assert(actual[i]==nextWeight-currentWeight);
      if(entityReject && i==visits-1) break;
      lastDistance=nextDistance;
    }
    ++cases;
  }
  std::cout << cases << " runtime snippet cases passed\n";
}
'''
        source = source.replace('DEFINES', defines).replace('DECLARATION', declaration)
        source = source.replace('CURRENT', CURRENT).replace('UPDATE', UPDATE)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary)
            (path / 'model.cc').write_text(source)
            subprocess.run([compiler, '-std=c++17', '-O0', str(path/'model.cc'), '-o', str(path/'model')],
                           text=True, capture_output=True, check=True)
            result = subprocess.run([str(path/'model')], text=True, capture_output=True, check=True)
            self.assertIn('9600 runtime snippet cases passed', result.stdout)

    def test_authentic_pin_drift_and_duplicate_candidate_refuse(self):
        bad = dict(self.original)
        bad[MEMBER] += b'\r\n'
        with self.assertRaisesRegex(ValueError, 'Pinned shader source identity changed'):
            apply(bad, self.candidate)
        with self.assertRaisesRegex(ValueError, 'already present'):
            candidate_manifest(self.candidate)
        bad_recipe = copy.deepcopy(self.current)
        bad_recipe['version'] = 'r5.9.4'
        with self.assertRaisesRegex(ValueError, 'Only pinned'):
            candidate_manifest(bad_recipe)


    def test_changed_certificate_preimage_refuses_before_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)/'root'
            for name in (RECIPE, CERTIFICATE, *(p['path'] for p in self.current['additions'].values())):
                target = root/name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((ROOT/name).read_bytes())
            (root/CERTIFICATE).write_bytes((root/CERTIFICATE).read_bytes()+b'\n')
            output = Path(temporary)/'refused'
            with self.assertRaisesRegex(ValueError, 'certificate preimage changed'):
                prepare(BASE, output, root=root)
            self.assertFalse(output.exists())



if __name__ == '__main__':
    unittest.main()
