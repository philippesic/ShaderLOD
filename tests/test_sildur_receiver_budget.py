"""Source/refusal and quadrature evidence only; no driver or quality claim."""
import importlib.util
import copy
import json
import math
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('sildur_builder', ROOT / 'scripts/build_sildur_receiver_budget.py')
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)
BASE = ROOT / "dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip"
DEFINES = {'IS_IRIS':'1','IRIS_VERSION':'10812','MC_VERSION':'12101','MC_OS_MAC':'1'}


def preprocess(files, name, defines=DEFINES, settings=None):
    def expand(member):
        source = (settings if member == 'shaders/shaders.settings' and settings is not None else files[member]).decode()
        def include(match):
            ref = match.group(1)
            child = 'shaders/' + ref[1:] if ref.startswith('/') else str(Path(member).parent / ref)
            return expand(child)
        return re.sub(r'^\s*#include "([^"]+)"\s*$', include, source, flags=re.M)
    source = re.sub(r'^\s*#(?:version|extension).*$', '', expand(name), flags=re.M)
    result = subprocess.run(['clang','-E','-P','-x','c','-'] + ['-D'+k+'='+v for k,v in defines.items()],
                            input=source, text=True, capture_output=True, check=True)
    return re.sub(r'\s+', ' ', result.stdout).strip()


def quadrature(noise, blend):
    points, weights = [], []
    for i in range(12):
        alpha = (i + noise) / 12.0
        angle = (noise + alpha * 4.0) * 6.2831853
        points.append((math.cos(angle)*math.sqrt(alpha), math.sin(angle)*math.sqrt(alpha)))
        weights.append((1-blend) if 6 <= i < 9 else (1+0.5*blend if i >= 3 else 1))
    return points, weights


class ReceiverTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(builder.MANIFEST.read_text())
        cls.original = builder.read_archive(BASE)
        cls.native = builder.apply(cls.original, cls.manifest)
        cls.candidate = builder.apply(cls.original, cls.manifest, 'candidate')
        cls.equivalent = builder.apply(cls.original, cls.manifest, 'native-equivalent')

    def test_exact_pins_and_only_two_upstream_members_changed(self):
        self.assertEqual(builder.sha256(BASE.read_bytes()), self.manifest['original_archive_sha256'])
        self.assertEqual({n for n in self.original if self.original[n] != self.candidate[n]},
                         {builder.MEMBER, builder.PROPERTIES})
        self.assertEqual(set(self.candidate) - set(self.original), {builder.INCLUDE})
        for name in self.original:
            if name not in (builder.MEMBER, builder.PROPERTIES):
                self.assertEqual(self.original[name], self.candidate[name])

    def test_native_preprocess_exact_including_new_settings_exposure(self):
        # All6 deferred dimension/stage wrappers, AA on/off and nMap0/1/2.
        for nmap in (0,1,2):
            for taa in (True,False):
                settings = self.original['shaders/shaders.settings'].replace(b'#define nMap 0',f'#define nMap {nmap}'.encode())
                if not taa:
                    settings = settings.replace(b'#define TAA ',b'//#define TAA ')
                for name in [n for n in self.original if n.endswith(('/deferred.fsh','/deferred.vsh'))]:
                    with self.subTest(nmap=nmap, taa=taa, name=name):
                        self.assertEqual(preprocess(self.original,name,settings=settings),
                                         preprocess(self.native,name,settings=settings))

    def test_unknown_runtime_and_domain_guards_restore_original(self):
        cases = [{}, {'IS_IRIS':'1'}, {**DEFINES,'IRIS_VERSION':'10811'},
                 {**DEFINES,'IRIS_VERSION':'10813'},{**DEFINES,'MC_VERSION':'12111'},
                 {k:v for k,v in DEFINES.items() if k!='MC_OS_MAC'},
                 {**DEFINES,'DISTANT_HORIZONS':'1'},{**DEFINES,'VOXY':'1'}]
        for defines in cases:
            with self.subTest(defines=defines):
                self.assertEqual(preprocess(self.original,builder.MEMBER,defines),
                                 preprocess(self.candidate,builder.MEMBER,defines))

    def test_native_options_and_aa_off_refusals(self):
        settings = self.original['shaders/shaders.settings']
        changes = [(b'#define TAA ',b'//#define TAA '),(b'#define PCSS ',b'//#define PCSS '),
                   (b'#define Shadows\t',b'//#define Shadows\t'),
                   (b'#define nMap 0',b'#define nMap 1'),(b'#define nMap 0',b'#define nMap 2'),
                   (b'#define shadow_samples 12',b'#define shadow_samples 9'),
                   (b'//#define raytracedShadows',b'#define raytracedShadows')]
        for before,after in changes:
            with self.subTest(change=after):
                self.assertIn(before,settings)
                altered = settings.replace(before,after)
                self.assertEqual(preprocess(self.original,builder.MEMBER,settings=altered),
                                 preprocess(self.candidate,builder.MEMBER,settings=altered))

    def test_native_equivalent_is_runtime_uniform_not_compile_constant(self):
        self.assertEqual(self.candidate[builder.MEMBER],self.equivalent[builder.MEMBER])
        self.assertIn(b'uniform float shaderlodReceiverReductionPermission;',self.candidate[builder.MEMBER])
        candidate = self.candidate[builder.PROPERTIES]
        equivalent = self.equivalent[builder.PROPERTIES]
        self.assertEqual(candidate.replace(b'ReductionPermission=1.0',b'ReductionPermission=0.0'),equivalent)
        expanded = preprocess(self.candidate,builder.MEMBER)
        self.assertIn('dFdx(slUv)',expanded)
        self.assertIn('shaderlodReceiverReductionPermission == 1.0',expanded)

    def test_derivatives_precede_material_sky_diffuse_and_loop_branches(self):
        source = self.candidate[builder.MEMBER].decode()
        main = source[source.index('void main()'):]
        derivatives = [m.start() for m in re.finditer(r'dFd[xy]\(',main)]
        self.assertEqual(len(derivatives),12)
        self.assertLess(max(derivatives),main.index('bool slEligible'))
        self.assertLess(max(derivatives),main.index('if(depth0 >= 1.0)'))
        self.assertLess(max(derivatives),main.index('funShadows(noise'))

    def test_native_kernel_positions_bias_normalization_and_color_pair_unchanged(self):
        source = self.candidate[builder.MEMBER].decode()
        for line in ('float alpha = (float(i) + noise) * rShadowSamples;',
                     'float angle = (noise + alpha * 4.0) * 6.2831853;',
                     'vec2 offsetS = vec2(cos(angle), sin(angle)) * sqrt(alpha);',
                     'float weight = 1.0+(i+noise)*rdMul*rShadowSamples*shadowMapResolution;',
                     'shading = shadows * rShadowSamples;'):
            self.assertEqual(source.count(line),3)
        self.assertEqual(source.count('.x * slTapWeight;'),2)
        for line in self.original[builder.MEMBER].decode().splitlines():
            if 'finalShading =' in line or 'finalShading *=' in line:
                self.assertIn(line,source)

    def test_weights_positive_mass_second_moment_and_bias(self):
        for noise in (0.0,1e-7,0.125,0.5,0.875,1-1e-7):
            for blend in (0.0,0.01,0.5,0.99,1.0):
                _, weights = quadrature(noise,blend)
                self.assertAlmostEqual(sum(weights),12)
                self.assertGreaterEqual(min(weights),0)
                self.assertLessEqual(max(weights),1.5)
                for spoke in range(3):
                    indices = [spoke+q for q in (0,3,6,9)]
                    self.assertAlmostEqual(sum(weights[i] for i in indices),4)
                    self.assertAlmostEqual(sum(weights[i]*(i+noise)/12 for i in indices),
                                           sum((i+noise)/12 for i in indices))
                self.assertEqual(sum(w>0 for w in weights),9 if blend==1 else 12)

    def test_thin_occluder_counterexample_is_not_hidden_by_moments(self):
        # Only discarded positions see an opaque/thin occluder. Native shade=.75;
        # reduced shade=1 despite matching moments. Quality remains unproved.
        visibility = [0.0 if 6<=i<9 else 1.0 for i in range(12)]
        _, weights = quadrature(0.5,1)
        native = sum(visibility)/12
        candidate = sum(a*b for a,b in zip(visibility,weights))/12
        self.assertEqual(native,0.75)
        self.assertEqual(candidate,1.0)
        self.assertEqual(candidate-native,0.25)

    def test_boundary_weight_output_continuity_for_adverse_visibility(self):
        visibility = [0.0 if 6<=i<9 else 1.0 for i in range(12)]
        for blend in (0,1e-6,0.2,0.8,1-1e-6,1):
            _, weights = quadrature(0.1,blend)
            self.assertAlmostEqual(sum(w*v for w,v in zip(weights,visibility))/12,0.75+0.25*blend)

    def test_changed_sources_modes_and_private_options_rejected(self):
        altered = dict(self.original)
        altered['shaders/shaders.settings'] += b'\n'
        with self.assertRaisesRegex(ValueError,'member set/content'):
            builder.apply(altered,self.manifest,'candidate')
        with self.assertRaises(ValueError):builder.apply(self.original,self.manifest,'lower-quality')
        with self.assertRaises(ValueError):builder.validate_options(b'SHADERLOD_SILDUR_RECEIVER_BUDGET=1\n')

    def test_build_preserves_options_license_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            options = root/'native.txt'
            options.write_bytes(b'# Native user choice\nTAA=false\nnMap=2\nshadow_samples=9\n')
            output = root/'private.zip'
            with self.assertRaisesRegex(ValueError,'full original option observation'):
                builder.build(BASE,output,options,'candidate')
            receipt = builder.build(BASE,output,options,'native-equivalent')
            self.assertEqual(Path(str(output)+'.txt').read_bytes(),options.read_bytes())
            self.assertFalse(any(receipt['validation'].values()))
            self.assertIn(b'Copyright (c) Sildur.',builder.read_archive(output)[builder.MEMBER])
            with self.assertRaises(ValueError):builder.build(BASE,output,options,'candidate')
            with self.assertRaises(ValueError):builder.build(BASE,BASE,options,'candidate')
            wrong_iris = root/'wrong-iris.jar'
            wrong_iris.write_bytes(b'unknown')
            with self.assertRaisesRegex(ValueError,'Uncertified Iris'):
                builder.build(BASE,root/'other.zip',options,'native',iris=wrong_iris)







if __name__ == '__main__':
    unittest.main()
