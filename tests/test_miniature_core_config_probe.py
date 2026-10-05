"""Source and independent math/evidence fixtures; no real pack build or compiler."""
import copy
import hashlib
import json
import math
from pathlib import Path
import random
import re
import shutil
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from build_miniature_core_config_probe import apply_patches, build, read_archive, validate_qualification


def policy(preset, near, far, width, distance):
    """Reference centered-band contract, independent of GPU execution."""
    middle, low = ((4, 3), (3, 2), (2, 2))[preset]
    if width == 0:
        count = 4 if distance < near else middle if distance < far else low
        return count, count, 0.0
    half = width / 2
    if distance <= near - half:
        return 4, 4, 0.0
    if distance < near + half:
        a, b, x = 4, middle, (distance - near + half) / width
    elif distance <= far - half:
        return middle, middle, 0.0
    elif distance < far + half:
        a, b, x = middle, low, (distance - far + half) / width
    else:
        return low, low, 0.0
    w = x * x * (3 - 2 * x)
    return (a, a, 0.0) if a == b or w <= 0 else (b, b, 0.0) if w >= 1 else (a, b, w)


def blend(a, b, w):
    if w <= 0:
        return a
    if w >= 1:
        return b
    opacity = a[3] * (1 - w) + b[3] * w
    if opacity == 0:
        return (0, 0, 0, 0)
    color = tuple((a[i] * a[3] * (1 - w) + b[i] * b[3] * w) / opacity for i in range(3))
    return color + (opacity,)


def native_composition(base, endpoint, strength):
    return tuple(base[i] * (1 - endpoint[3] * strength) + endpoint[i] * endpoint[3] * strength for i in range(3))


class MiniatureCoreConfigurationTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((ROOT / "patches/miniature/2.19-core-config-probe.json").read_text())
        self.helper = (ROOT / "shaderpacks/miniature-core-config-probe/shaders/common/shaderlodCoreConfiguration.glsl").read_text()

    def fixture(self, root):
        # Reuse the already-scoped qualification fixture, not its builder or tests.
        # Import inside this method avoids discovering another test class here.
        from test_miniature_refinement_probe import MiniatureRefinementTests
        seed = MiniatureRefinementTests()
        seed.manifest = self.manifest
        base, files, m = seed.fixture(root)
        (root / "patches/miniature/2.19-core-config-probe.json").write_text(json.dumps(m))
        shutil.copytree(ROOT / "shaderpacks/miniature-core-config-probe", root / "shaderpacks/miniature-core-config-probe")
        (root / "docs/MINIATURE_CORE_CONFIGURATION_EVIDENCE.md").write_text("fixture core contract")
        return base, files, m

    def test_all_seven_types_flags_ordinals_and_invalid_bands(self):
        for name, kind in self.manifest["declared_core_fields"].items():
            self.assertIn(f"uniform {kind} {name};", self.helper)
        self.assertEqual(len(self.manifest["declared_core_fields"]), 7)
        validity = self.helper[self.helper.index("bool shaderlodMiniatureConfigurationValid"):self.helper.index("vec2 shaderlodMiniaturePresetBudgets")]
        for predicate in ("shaderlodBridgeVersion != 2", "shaderlodEnabled != 0 && shaderlodEnabled != 1",
                          "shaderlodDebug != 0 && shaderlodDebug != 1", "shaderlodPreset < 0 || shaderlodPreset > 2",
                          "shaderlodNear >= 1.0", "shaderlodNear <= 4095.0", "shaderlodFar <= 4096.0",
                          "shaderlodFar > shaderlodNear", "shaderlodTransition >= 0.0", "shaderlodTransition <= 512.0",
                          "shaderlodTransition <= min(2.0 * shaderlodNear, shaderlodFar - shaderlodNear)"):
            self.assertIn(predicate, validity)
        self.assertNotIn("clamp(", validity)
        predicate = " ".join(re.search(r"return (shaderlodNear.+?);", validity, re.S).group(1).replace("&&", "and").split())
        cases = [(32, 96, 8, True), (32, 96, 64, True), (32, 96, 65, False),
                 (1, 2, 1, True), (0, 96, 8, False), (32, 32, 0, False),
                 (32, 31, 0, False), (32, 96, -1, False), (4096, 4097, 0, False)]
        for field in range(3):
            for bad in (math.nan, math.inf, -math.inf):
                values = [32, 96, 8]
                values[field] = bad
                cases.append((*values, False))
        for near, far, width, expected in cases:
            values = {"shaderlodNear": near, "shaderlodFar": far, "shaderlodTransition": width, "min": min}
            self.assertEqual(bool(eval(predicate, {"__builtins__": {}}, values)), expected)
        source_rejections = re.findall(r"if \((.*?)\) return false;", validity, re.S)
        self.assertEqual(len(source_rejections), 3)
        for version in (-1, 0, 1, 2, 3):
            for enabled in (-1, 0, 1, 2):
                for debug in (-1, 0, 1, 2):
                    for preset in (-1, 0, 1, 2, 3):
                        rejection = version != 2 or enabled not in (0, 1) or debug not in (0, 1) or preset not in (0, 1, 2)
                        fields = {"shaderlodBridgeVersion": version, "shaderlodEnabled": enabled, "shaderlodDebug": debug, "shaderlodPreset": preset}
                        actual = any(bool(eval(" ".join(expr.replace("&&", "and").replace("||", "or").split()), {"__builtins__": {}}, fields)) for expr in source_rejections)
                        self.assertEqual(actual, rejection)
        dispatch = self.helper[self.helper.index("vec4 shaderlodMiniatureCoreConfiguration("):]
        self.assertLess(dispatch.index("return getReflectionColor"), dispatch.index("shaderlodDebug == 1"))
        self.assertIn("shaderlodEnabled != 1 || SSR_STEP_SIZE != 1.6", dispatch)
        self.assertIn("#if SHADERLOD_MINIATURE_CORE_DEBUG_ALLOW == 1", dispatch)

    def test_preset_budgets_centered_edges_equal_collapse_and_width_zero(self):
        self.assertEqual(self.manifest["preset_budgets"], {"0": [4, 4, 3], "1": [4, 3, 2], "2": [4, 2, 2]})
        for source in ("if (shaderlodPreset == 0) return vec2(4.0, 3.0)", "if (shaderlodPreset == 1) return vec2(3.0, 2.0)", "return vec2(2.0, 2.0)"):
            self.assertIn(source, self.helper)
        for preset, middle, far in ((0, 4, 3), (1, 3, 2), (2, 2, 2)):
            self.assertEqual(policy(preset, 96, 160, 8, 92), (4, 4, 0))
            self.assertEqual(policy(preset, 96, 160, 8, 100), (middle, middle, 0))
            self.assertEqual(policy(preset, 96, 160, 8, 156), (middle, middle, 0))
            self.assertEqual(policy(preset, 96, 160, 8, 164), (far, far, 0))
            self.assertEqual(policy(preset, 96, 160, 0, 96), (middle, middle, 0))
            self.assertEqual(policy(preset, 96, 160, 0, 160), (far, far, 0))
        self.assertEqual(policy(1, 96, 160, 8, 96), (4, 3, .5))
        self.assertEqual(policy(1, 96, 160, 8, 160), (3, 2, .5))
        self.assertEqual(policy(0, 96, 160, 8, 96), (4, 4, 0))
        self.assertEqual(policy(2, 96, 160, 8, 160), (2, 2, 0))
        self.assertEqual(policy(1, 32, 96, 64, 64), (3, 3, 0))
        for preset in range(3):
            for d in range(220):
                a, b, w = policy(preset, 96, 160, 8, d)
                self.assertIn(a, (2, 3, 4)); self.assertIn(b, (2, 3, 4)); self.assertTrue(0 <= w <= 1)
        self.assertIn("length(viewPos)", self.helper)
        self.assertNotIn("length(feetPos)", self.helper)
        self.assertIn("0.5 * width", self.helper)
        self.assertNotIn("random", self.helper)

    def test_endpoint_blend_preserves_native_composition(self):
        self.assertIn("mix(a.rgb * a.a, b.rgb * b.a, weight)", self.helper)
        self.assertIn("contribution / opacity", self.helper)
        self.assertIn("if (opacity == 0.0) return vec4(0.0)", self.helper)
        rng = random.Random(719)
        for _ in range(1000):
            a = tuple(rng.random() * 4 for _ in range(3)) + (rng.choice((0.0, rng.random() * 2)),)
            b = tuple(rng.random() * 4 for _ in range(3)) + (rng.choice((0.0, rng.random() * 2)),)
            base = tuple(rng.random() for _ in range(3))
            strength, w = rng.random(), rng.random()
            actual = native_composition(base, blend(a, b, w), strength)
            first, second = native_composition(base, a, strength), native_composition(base, b, strength)
            expected = tuple(x * (1 - w) + y * w for x, y in zip(first, second))
            for x, y in zip(actual, expected):
                self.assertAlmostEqual(x, y, places=12)
            self.assertEqual(blend(a, b, 0), a); self.assertEqual(blend(a, b, 1), b)
        self.assertEqual(blend((0, 0, 0, 0), (0, 0, 0, 0), .5), (0, 0, 0, 0))
        for actual, expected in zip(blend((9, 8, 7, 0), (1, 2, 3, .8), .5), (1, 2, 3, .4)):
            self.assertAlmostEqual(actual, expected)

    def test_one_chain_refinement_stops_capture_prefix_and_color_bounds(self):
        # Independent bracket reference verifies 2/3 stops are exact prefixes of4.
        def chain(steps, function):
            a, b, points = -2.0, -18.0, []
            for _ in range(steps):
                point = (a + b) * .5
                scene_z = function(point)
                if -point < -scene_z: a = point
                else: b = point
                points.append((point, scene_z))
            return points
        for callback in (lambda x: -9, lambda x: -7 + math.sin(x), lambda x: -.2 * x - 11):
            full = chain(4, callback)
            for a in (2, 3, 4):
                for b in (2, 3, 4):
                    for mode in (1, 2, 3):
                        steps = max(a, b) if mode == 3 else 4
                        retained = chain(steps, callback)
                        self.assertEqual(retained, full[:steps])
                        self.assertEqual(retained[a - 1], full[a - 1])
                        self.assertEqual(retained[b - 1], full[b - 1])
        active = self.helper[self.helper.index("vec4 shaderlodMiniatureConfigurationActive("):self.helper.index("vec4 shaderlodMiniatureCoreConfiguration(")]
        self.assertEqual(active.count("for (int i = 0; i < SSR_MAX_STEPS; i++)"), 1)
        self.assertEqual(active.count("for (int j = 0; j < 4; j++)"), 1)
        self.assertIn("if (j >= steps) break", active)
        self.assertIn("#if SHADERLOD_MINIATURE_CORE_CONFIG_MODE == 3", active)
        self.assertIn("firstCount > secondCount ? firstCount : secondCount", active)
        self.assertLess(active.index("if (firstCount == secondCount || policy.z <= 0.0) return first"), active.index("vec4 second = shaderlodMiniatureEndpoint"))
        self.assertNotIn("texture2D(", self.helper)
        self.assertEqual(self.helper.count("texture2DLod(colortex0,"), 1)
        self.assertEqual(active.count("texture2DLod(depthtex0,"), 2)
        self.assertNotIn("sampler", self.helper)

    def test_source_gate_defaults_native_fallback_and_original_march(self):
        header = self.manifest["patches"][0]["after"]
        self.assertIn("#define SHADERLOD_MINIATURE_CORE_CONFIG_MODE 0", header)
        self.assertIn("#define SHADERLOD_MINIATURE_CORE_DEBUG_ALLOW 0", header)
        include = self.manifest["patches"][1]["after"]
        predicate = re.search(r"#if (.+)\r\n", include).group(1)
        for mode in (-1, 0, 1, 2, 3, 4):
            for binary in (1, 2, 3, 4, 8):
                for outer in (10, 16):
                    expr = predicate.replace("SHADERLOD_MINIATURE_CORE_CONFIG_MODE", str(mode)).replace("SSR_BINARY_STEPS", str(binary)).replace("SSR_MAX_STEPS", str(outer)).replace("&&", "and").replace("||", "or")
                    self.assertRegex(expr, r"^[0-9() =andor-]+$")
                    self.assertEqual(eval(expr, {"__builtins__": {}}, {}), mode in (1, 2, 3) and binary == 4 and outer == 10)
        source = ROOT / "dev/upstream/other-pack-research/miniature-release/shaders/common/getReflectionColor.fsh"
        if source.exists():
            original = source.read_bytes().decode()
            active = self.helper[self.helper.index("vec4 shaderlodMiniatureConfigurationActive("):]
            before = original[original.index("vec3 V ="):original.index("if (diffZ < 0.0)")]
            after = active[active.index("vec3 V ="):active.index("if (diffZ < 0.0)")]
            after = after.replace("texture2DLod(depthtex0, curUV, 0.0)", "texture2D(depthtex0, curUV)")
            self.assertEqual(re.sub(r"\s+", "", before), re.sub(r"\s+", "", after))
            native_step = "if (-curPos.z < -sceneZ) { a = curPos; }\n               else                     { b = curPos; }"
            self.assertIn(re.sub(r"\s+", "", native_step), re.sub(r"\s+", "", active))

    def test_small_qualified_fixture_determinism_and_package_defaults(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, original, m = self.fixture(root)
            first, second = root / "first.zip", root / "second.zip"
            meta = build(first, base, root)
            self.assertEqual(meta, build(second, base, root)); self.assertEqual(first.read_bytes(), second.read_bytes())
            files = read_archive(first)
            self.assertEqual(files["shaders/common/getReflectionColor.fsh"], original["shaders/common/getReflectionColor.fsh"])
            self.assertEqual(files["LICENSE.Miniature"], (ROOT / "patches/miniature/LICENSE.Miniature").read_bytes())
            self.assertEqual(meta["default_mode"], 0); self.assertEqual(meta["default_debug_allow"], 0)
            self.assertEqual(len(meta["declared_core_fields"]), 7)
            self.assertFalse(any(v for k, v in meta["validation"].items() if k != "source_qualified"))
            projected = apply_patches(original, m)
            for p in reversed(m["patches"]):
                if p["path"] in ("shaders/shader.h", "shaders/program/final.fsh"):
                    projected[p["path"]] = projected[p["path"]].replace(p["after"].encode(), p["before"].encode(), 1)
            self.assertEqual(projected["shaders/shader.h"], original["shaders/shader.h"])
            self.assertEqual(projected["shaders/program/final.fsh"], original["shaders/program/final.fsh"])

    def test_negative_evidence_overlay_source_and_anchor_guards(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, files, m = self.fixture(root)
            with self.assertRaisesRegex(ValueError, "overwrite"): build(base, base, root)
            evidence = root / m["qualification"]["sampler_path"]
            previous = evidence.read_bytes(); evidence.write_bytes(previous + b"changed")
            with self.assertRaisesRegex(ValueError, "evidence changed"): build(root / "out.zip", base, root)
            evidence.write_bytes(previous)
            helper = root / "shaderpacks/miniature-core-config-probe/shaders/common/shaderlodCoreConfiguration.glsl"
            helper.write_bytes(helper.read_bytes() + b"changed")
            with self.assertRaisesRegex(ValueError, "overlay changed"): build(root / "out.zip", base, root)
            changed = files.copy(); changed["shaders/program/final.fsh"] += b"changed"
            with self.assertRaisesRegex(ValueError, "source changed"): apply_patches(changed, m)
            m["patches"][0]["before"] = ""
            with self.assertRaisesRegex(ValueError, "exactly once"): apply_patches(files, m)

    def test_actual_pinned_evidence_and_untouched_original_members_readonly(self):
        q = self.manifest["qualification"]
        for key in ("sampler", "workload", "iris"):
            p = ROOT / q[key + "_path"]
            if not p.exists(): self.skipTest("Ignored qualification artifact unavailable")
            self.assertEqual(hashlib.sha256(p.read_bytes()).hexdigest(), q[key + "_sha256"])
        validate_qualification(json.loads((ROOT / q["sampler_path"]).read_bytes()), json.loads((ROOT / q["workload_path"]).read_bytes()), self.manifest)
        original = read_archive(ROOT / "dev/upstream/other-pack-research/miniature-shader-2.19.zip")
        result = apply_patches(original, self.manifest)
        changed = {n for n in original if result[n] != original[n]}
        self.assertEqual(changed, {p["path"] for p in self.manifest["patches"]}); self.assertEqual(len(changed), 4)
        self.assertEqual(result["shaders/common/getReflectionColor.fsh"], original["shaders/common/getReflectionColor.fsh"])
        before, after = original["shaders/program/final.fsh"].decode(), result["shaders/program/final.fsh"].decode()
        self.assertEqual(before[before.index("      color.rgb = mix("):], after[after.index("      color.rgb = mix("):])
        self.assertEqual(before.count("texture2D("), after.count("texture2D("))


if __name__ == "__main__":
    unittest.main()
