"""Source/math tests. These do not execute Iris or a GPU shader."""
import importlib.util
import json
import math
from pathlib import Path
import random
import re
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("bsl_receiver", ROOT / "scripts/build_bsl_receiver_budget.py")
B = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(B)


def preprocess(source, defines):
    # GLSL macro branch evidence only. This deliberately does not interpret
    # Iris properties with a C parser or claim driver compilation.
    source = re.sub(r'^\s*#include[^\n]*', '', source, flags=re.M)
    result = subprocess.run(["clang", "-E", "-P", "-x", "c", "-", *["-D" + x for x in defines]],
                            input=source, text=True, capture_output=True, check=True)
    return result.stdout


CERTIFIED = ["IS_IRIS", "IRIS_VERSION=10812", "MC_VERSION=12101", "MC_OS_MAC", "OVERWORLD", "SHADOW",
             "SHADOW_FILTER", "TAA", "FXAA", "TAA_MODE=0", "SHADOW_PIXEL=0", "SHADOW_BIAS=0"]


class BslReceiverTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(B.MANIFEST.read_bytes())
        cls.files = B.read_members(B.BASE)
        cls.helper = B.HELPER.read_bytes()



    def test_three_native_members_and_one_helper_only(self):
        patched = B.patch(self.files, self.manifest, self.helper, "candidate")
        self.assertEqual({n for n in self.files if patched[n] != self.files[n]},
                         {B.TERRAIN, B.SHADOWS, B.PROPERTIES})
        self.assertEqual(set(patched) - set(self.files), {"shaders/lib/shaderlod/receiver_footprint_budget_r1.glsl"})
        self.assertTrue(patched[B.PROPERTIES].startswith(self.files[B.PROPERTIES]))
        native = self.files[B.SHADOWS].decode()
        changed = patched[B.SHADOWS].decode()
        start = native.index("vec3 SampleFilteredShadow(")
        end = native.index("vec3 GetShadow(", start)
        self.assertIn(native[start:end], changed)
        sss = native[native.index("vec3 GetSubsurfaceShadow("):]
        self.assertIn(sss, changed)

    def test_same_glsl_in_candidate_and_uniform_native_equivalent(self):
        a = B.patch(self.files, self.manifest, self.helper, "candidate")
        b = B.patch(self.files, self.manifest, self.helper, "native-equivalent")
        self.assertEqual({k for k in a if a[k] != b[k]}, {B.PROPERTIES})
        self.assertIn(b"uniform.float.shaderlodReceiverReductionPermission = 1.0", a[B.PROPERTIES])
        self.assertIn(b"uniform.float.shaderlodReceiverReductionPermission = 0.0", b[B.PROPERTIES])
        self.assertIn("shaderlodReceiverReductionPermission == 1.0", B.HOIST)

    def test_pinned_source_and_helper_mismatch_fail_closed(self):
        files = dict(self.files)
        files[B.SHADOWS] += b"\n"
        with self.assertRaisesRegex(ValueError, "member mismatch"):
            B.patch(files, self.manifest, self.helper, "candidate")
        with self.assertRaisesRegex(ValueError, "shared policy"):
            B.patch(self.files, self.manifest, self.helper + b"\n", "candidate")
        with self.assertRaisesRegex(ValueError, "mode"):
            B.patch(self.files, self.manifest, self.helper, "typo")

    def test_source_default_off_and_unknown_runtime_or_material_native(self):
        gate = B.GATE + "\n#ifdef SHADERLOD_BSL_RECEIVER_ACTIVE\nACTIVE_RECEIVER\n#endif\n"
        self.assertNotIn("ACTIVE_RECEIVER", preprocess(gate, CERTIFIED))
        active = gate.replace("#define SHADERLOD_BSL_RECEIVER 0", "#define SHADERLOD_BSL_RECEIVER 1")
        self.assertIn("ACTIVE_RECEIVER", preprocess(active, CERTIFIED))
        for remove in ("IS_IRIS", "IRIS_VERSION=10812", "MC_VERSION=12101", "MC_OS_MAC", "OVERWORLD", "SHADOW", "SHADOW_FILTER", "TAA", "FXAA"):
            with self.subTest(remove=remove):
                self.assertNotIn("ACTIVE_RECEIVER", preprocess(active, [x for x in CERTIFIED if x != remove]))
        for extra in ("ADVANCED_MATERIALS", "DISTANT_HORIZONS", "VOXY", "VOXY_PATCH", "HALF_LAMBERT", "TAA_SELECTIVE", "WATER_CAUSTICS", "MULTICOLORED_BLOCKLIGHT", "MCBL_SS"):
            with self.subTest(extra=extra):
                self.assertNotIn("ACTIVE_RECEIVER", preprocess(active, CERTIFIED + [extra]))
        for key, wrong in (("IRIS_VERSION", "10814"), ("MC_VERSION", "12111"), ("TAA_MODE", "1"), ("SHADOW_PIXEL", "2"), ("SHADOW_BIAS", "1")):
            ds = [x for x in CERTIFIED if not x.startswith(key + "=")] + [key + "=" + wrong]
            self.assertNotIn("ACTIVE_RECEIVER", preprocess(active, ds))

    def test_native_preprocessed_shadow_semantics_default_off(self):
        patched = B.patch(self.files, self.manifest, self.helper, "native")
        # Shared shadow member is used by many programs. With private active
        # marker absent every original function/branch token remains exact.
        for defines in (CERTIFIED, CERTIFIED + ["SHADOW_COLOR", "WATER_CAUSTICS"], ["NETHER"], ["SHADOW", "VOXY_PATCH"]):
            original = preprocess(self.files[B.SHADOWS].decode(), defines)
            native = preprocess(patched[B.SHADOWS].decode(), defines)
            self.assertEqual(re.sub(r"\s", "", original), re.sub(r"\s", "", native))

    def test_derivatives_hoisted_before_discard_and_native_receiver_branch(self):
        patched = B.patch(self.files, self.manifest, self.helper, "candidate")
        source = patched[B.TERRAIN].decode()
        self.assertLess(source.index("vec2 shaderlodDx = dFdx"), source.index("discard;"))
        self.assertLess(source.index("vec2 shaderlodDy = dFdy"), source.index("GetLighting(albedo"))
        self.assertNotRegex(B.FILTER, r"\bdFdx\(|\bdFdy\(")
        self.assertIn("mat == 0.0 && albedo.a >= 0.999", B.HOIST)
        self.assertIn("basicSubsurface == 0.0 && subsurface == 0.0", B.HOIST)

    def test_hostile_footprints_fall_back(self):
        normal = (True, (.01, 0), (0, .01), (.001, .001), 1)
        self.assertEqual(B.reduction_weight(*normal), 1)
        cases = [ (False, normal[1], normal[2], normal[3], 1),
                 (True, (0, 0), (0, 0), normal[3], 1),
                 (True, (.01, .01), (.02, .02), normal[3], 1),
                 (True, (.01, 0), (0, 1e-8), normal[3], 1),
                 (True, (1e100, 0), (0, 1e100), normal[3], 1)]
        for value in (math.nan, math.inf, -math.inf):
            cases += [(True, (value, 0), normal[2], normal[3], 1),
                      (True, normal[1], normal[2], (value, .001), 1),
                      (True, normal[1], normal[2], normal[3], value)]
        for extent in ((0, .001), (-.1, .001), (1.01, .001)):
            cases.append((True, normal[1], normal[2], extent, 1))
        for case in cases:
            self.assertEqual(B.reduction_weight(*case), 0, repr(case))

    def test_boundary_is_continuous_and_output_scale_conservative(self):
        def at_radius(r, scale=1):
            return B.reduction_weight(True, (1, 0), (0, 1), (r/math.sqrt(2), r/math.sqrt(2)), scale)
        self.assertEqual(at_radius(.25), 1)
        self.assertEqual(at_radius(.5), 0)
        values = [at_radius(.25 + i*.0025) for i in range(101)]
        self.assertTrue(all(a >= b for a, b in zip(values, values[1:])))
        self.assertLess(abs(at_radius(.25-1e-6)-at_radius(.25+1e-6)), 1e-8)
        self.assertLess(abs(at_radius(.5-1e-6)-at_radius(.5+1e-6)), 1e-8)
        self.assertLess(at_radius(.2, 2), at_radius(.2, 1))

    def test_weights_preserve_mass_centroid_and_native_support(self):
        offsets = [(0,0),(0,1),(.7,.7),(1,0),(.7,-.7),(0,-1),(-.7,-.7),(-1,0),(-.7,.7)]
        for w in (0, .1, .5, .9, 1):
            weights = [1] + [1+w if i % 2 else 1-w for i in range(1,9)]
            self.assertAlmostEqual(sum(weights), 9)
            for axis in (0,1):
                self.assertAlmostEqual(sum(p[axis]*q for p,q in zip(offsets,weights)), 0)
                moment = sum(p[axis]**2*q/9 for p,q in zip(offsets,weights))
                self.assertAlmostEqual(moment, .44 + w*(4/9-.44))
            self.assertEqual(sum(q > 0 for q in weights), 5 if w == 1 else 9)
        # A diagonal-only occluder is a concrete falsifier of any strict
        # visibility bound: a subpixel footprint alone does not prove equality.
        shadow_comparisons = [1,1,0,1,1,1,1,1,1]
        native = sum(shadow_comparisons)/9
        reduced = (shadow_comparisons[0] + 2*sum(shadow_comparisons[i] for i in (1,3,5,7)))/9
        self.assertEqual(reduced, 1)
        self.assertGreater(reduced-native, .11)

    def test_affine_rectangle_corner_bound(self):
        rng = random.Random(731)
        for _ in range(200):
            a,b,c,d = [rng.uniform(-2,2) for _ in range(4)]
            det = a*d-b*c
            if abs(det) < .05:
                continue
            hx,hy = .01,.03
            ex,ey = (abs(d)*hx+abs(c)*hy)/abs(det), (abs(b)*hx+abs(a)*hy)/abs(det)
            for x in (-hx,hx):
                for y in (-hy,hy):
                    px,py = (d*x-c*y)/det, (-b*x+a*y)/det
                    self.assertLessEqual(px*px+py*py, ex*ex+ey*ey+1e-12)

    def test_reduced_depth_can_add_colored_branch_work(self):
        # Reviewer counterexample: a fractional hardware-PCF cardinal enters
        # native's retained color predicate only after its weight is doubled.
        native = (8 + .995) / 9
        reduced = (7 + 2*.995) / 9
        self.assertGreaterEqual(native, .999)
        self.assertLess(reduced, .999)
        native_fetches = 9 + (18 if native < .999 else 0)
        reduced_fetches = 5 + (10 if reduced < .999 else 0)
        self.assertEqual((native_fetches, reduced_fetches), (9, 15))

    def test_duplicate_archive_and_destination_refusal(self):
        with tempfile.TemporaryDirectory() as tmp:
            bad = Path(tmp)/"bad.zip"
            with zipfile.ZipFile(bad, "w") as z:
                z.writestr("../escape", b"x")
            with self.assertRaisesRegex(ValueError, "Unsafe"):
                B.read_members(bad)
            with self.assertRaisesRegex(ValueError, "fresh ZIP"):
                B.build(output=Path(tmp)/"candidate.zip")


if __name__ == "__main__":
    unittest.main()
