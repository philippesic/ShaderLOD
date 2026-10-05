"""Light source/fixture checks; no real pack build, compiler, Java or client."""
import copy
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from build_makeup_bridge_probe import apply_bridge_patches, build
from build_makeup_adapter import read_archive


class MakeUpBridgeProbeTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((ROOT / "patches/makeup/bridge2-probe.json").read_text())
        self.helper = (ROOT / "shaderpacks/makeup-bridge-probe/shaders/lib/shaderlod/core_bridge_probe.glsl").read_text()

    def fixture(self, root):
        files = {name: b"untouched source fixture\n" for name in self.manifest["source_sha256"]}
        for name in {patch["path"] for patch in self.manifest["patches"]}:
            files[name] = "\nfixture separator\n".join(
                patch["before"] for patch in self.manifest["patches"] if patch["path"] == name).encode()
        files.update({"LICENSE": b"retained LGPL fixture", "LICENSE.GPL-3.0": b"retained GPL fixture",
                      "shaderlod-source-patch.json": b"{}", "shaderlod-build.json": b"{}",
                      "SHADERLOD_EXPERIMENT.md": b"retained base notice", "shaders/lib/water_dh.glsl": b"DH unchanged"})
        base = root / "base.zip"
        with zipfile.ZipFile(base, "w") as archive:
            for name, content in files.items():
                archive.writestr(name, content)
        manifest = copy.deepcopy(self.manifest)
        manifest["base_sha256"] = hashlib.sha256(base.read_bytes()).hexdigest()
        manifest["source_sha256"] = {name: hashlib.sha256(files[name]).hexdigest() for name in manifest["source_sha256"]}
        (root / "patches/makeup").mkdir(parents=True)
        (root / "patches/makeup/bridge2-probe.json").write_text(json.dumps(manifest))
        (root / "docs").mkdir()
        (root / "docs/MAKEUP_CORE_BRIDGE_PROBE.md").write_text("fixture bridge contract")
        shutil.copytree(ROOT / "shaderpacks/makeup-bridge-probe", root / "shaderpacks/makeup-bridge-probe")
        return base, files, manifest

    def test_core_api_only_opted_exact_version_and_fallback(self):
        declarations = self.helper[self.helper.index("#if SHADERLOD_MAKEUP_CORE_BRIDGE == 1"):self.helper.index("bool shaderlodMakeupCoreKnown")]
        for name, kind in self.manifest["declared_core_fields"].items():
            self.assertIn(f"uniform {kind} {name};", declarations)
        self.assertIn("#endif", declarations)
        self.assertIn("return shaderlodBridgeVersion == 2;", self.helper)
        self.assertNotIn("shaderlodBridgeVersion >=", self.helper)
        for name in self.manifest["unused_core_fields"]:
            self.assertNotIn(name, self.helper)
        known = self.helper[self.helper.index("bool shaderlodMakeupCoreKnown"):self.helper.index("bool shaderlodMakeupCoreEnabled")]
        self.assertIn("#else\n    return false;", known)
        enabled = self.helper[self.helper.index("bool shaderlodMakeupCoreEnabled"):self.helper.index("vec2 shaderlodMakeupCoreBands")]
        self.assertIn("if (shaderlodMakeupCoreKnown()) return shaderlodEnabled != 0;", enabled)
        self.assertIn("#if SHADERLOD_MAKEUP_WATER_SSR == 1", enabled)
        for varying in ("fragpos", "gl_FragCoord", "cameraPosition", "texture"):
            self.assertNotIn(varying, enabled)

    def test_original_dynamic_disabled_body_and_active_sampling(self):
        patch = next(p for p in self.manifest["patches"] if p["before"].startswith("#if SHADERLOD_MAKEUP_WATER_SSR"))
        core = patch["after"]
        self.assertTrue(core.endswith("\n#else\n" + patch["before"] + "\n#endif\n"))
        parent = json.loads((ROOT / "patches/makeup/9.5g.json").read_text())
        original = next(p["before"] for p in parent["patches"] if p["path"] == "shaders/lib/water.glsl")
        renamed = original.replace("vec4 reflection_calc(", "vec4 shaderlodMakeupOriginalReflection(", 1)
        self.assertIn(renamed, core)
        dispatch = core[core.index("// This decision depends only"):core.index("\n#else\n", core.index("// This decision depends only"))]
        self.assertLess(dispatch.index("if (!shaderlodMakeupCoreEnabled())"), dispatch.index("return shaderlodMakeupCoreActiveReflection"))
        self.assertIn("return shaderlodMakeupOriginalReflection(fragpos, normal, reflected, infinite, dither);", dispatch)
        self.assertNotIn("shaderlodMakeupCoreSSRWeight", dispatch)
        active = core[core.index("vec4 shaderlodMakeupCoreActiveReflection"):core.index("// This decision depends only")]
        self.assertIn("shaderlodMakeupCoreSSRWeight(fragpos)", active)
        self.assertLess(active.index("if (ssrWeight <= 0.0)"), active.index("shaderlodWaterRaymarch(reflected"))
        self.assertNotIn("texture2D(", active)
        self.assertIn("texture2DLod(gaux1, pos.xy, 0.0)", active)

    def test_bands_radial_units_pack_fallback_and_no_extra_live_fields(self):
        self.assertIn("vec2 bands = vec2(SHADERLOD_MAKEUP_SSR_START, SHADERLOD_MAKEUP_SSR_END);", self.helper)
        self.assertIn("if (shaderlodMakeupCoreKnown()) bands = vec2(shaderlodNear, shaderlodFar);", self.helper)
        self.assertIn("bands.x = max(bands.x, 0.0);", self.helper)
        self.assertIn("bands.y = max(bands.y, bands.x + 1.0);", self.helper)
        self.assertIn("dot(fragpos, fragpos)", self.helper)
        self.assertIn("smoothstep(bands.x, bands.y, sqrt(distanceSquared))", self.helper)
        self.assertNotIn("cameraPosition", self.helper)
        self.assertNotIn("texture", self.helper)

    def test_cue_separate_compiletime_defaultoff_wateronly(self):
        configs = next(p for p in self.manifest["patches"] if p["path"] == "shaders/lib/config.glsl")["after"]
        for option in ("SHADERLOD_MAKEUP_CORE_BRIDGE", "SHADERLOD_MAKEUP_BRIDGE_CUE"):
            self.assertIn(f"#define {option} 0 // [0 1]", configs)
        cue = next(p for p in self.manifest["patches"] if p["before"].startswith("vec3 water_shader("))
        self.assertTrue(cue["after"].startswith(cue["before"]))
        self.assertIn("#if SHADERLOD_MAKEUP_BRIDGE_CUE == 1\n        return shaderlodMakeupCoreCue(fragpos);", cue["after"])
        self.assertNotIn("cristal", cue["after"])
        for color in ("vec3(0.5)", "vec3(1.0, 0.0, 1.0)", "vec3(1.0, 1.0, 0.0)", "vec3(0.0, 1.0, 0.0)", "vec3(0.0, 0.15, 1.0)"):
            self.assertIn(color, self.helper)

    def test_source_hash_anchor_and_schema_guards(self):
        with tempfile.TemporaryDirectory() as directory:
            _, files, manifest = self.fixture(Path(directory))
            result = apply_bridge_patches(files, manifest)
            self.assertEqual(result["shaders/lib/water_dh.glsl"], files["shaders/lib/water_dh.glsl"])
            self.assertEqual(result["shaders/lib/shaderlod/water_ssr.glsl"], files["shaders/lib/shaderlod/water_ssr.glsl"])
            changed = files.copy()
            changed["shaders/lib/water.glsl"] += b"changed"
            with self.assertRaisesRegex(ValueError, "source changed"):
                apply_bridge_patches(changed, manifest)
            bad = copy.deepcopy(manifest)
            bad["patches"][0]["before"] = ""
            with self.assertRaisesRegex(ValueError, "exactly once"):
                apply_bridge_patches(files, bad)
            bad["kind"] = "unknown"
            with self.assertRaisesRegex(ValueError, "Unsupported"):
                apply_bridge_patches(files, bad)

    def test_small_fixture_builder_determinism_and_source_licenses(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, original, _ = self.fixture(root)
            first, second = root / "first.zip", root / "second.zip"
            meta = build(first, base, root)
            self.assertEqual(meta, build(second, base, root))
            self.assertEqual(first.read_bytes(), second.read_bytes())
            result = read_archive(first)
            for name in ("LICENSE", "LICENSE.GPL-3.0", "shaderlod-build.json", "shaderlod-source-patch.json"):
                self.assertEqual(result[name], original[name])
            self.assertIn("shaderlod-bridge-source.json", result)
            self.assertIn("SHADERLOD_CORE_BRIDGE_PROBE.md", result)
            self.assertEqual(meta["default_bridge_option"], 0)
            self.assertEqual(meta["default_diagnostic_option"], 0)
            self.assertFalse(any(meta["validation"].values()))
            self.assertTrue(meta["prior_build_metadata_is_base_provenance"])

    def test_builder_overlay_archive_and_overwrite_guards(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, _, _ = self.fixture(root)
            with self.assertRaisesRegex(ValueError, "overwrite"):
                build(base, base, root)
            helper = root / "shaderpacks/makeup-bridge-probe/shaders/lib/shaderlod/core_bridge_probe.glsl"
            helper.write_text(helper.read_text() + "changed")
            with self.assertRaisesRegex(ValueError, "overlay changed"):
                build(root / "out.zip", base, root)
            base.write_bytes(base.read_bytes() + b"changed")
            with self.assertRaisesRegex(ValueError, "archive hash mismatch"):
                build(root / "out.zip", base, root)

    def test_available_actual_derivative_and_readonly_transport_source(self):
        source = (ROOT / self.manifest["transport_source"]).read_text()
        self.assertIn('uniform1i(frequency, "shaderlodBridgeVersion", () -> 2)', source)
        self.assertIn("UniformUpdateFrequency.PER_FRAME", source)
        for name, kind in self.manifest["declared_core_fields"].items():
            call = "uniform1i" if kind == "int" else "uniform1f"
            self.assertIn(f'{call}(frequency, "{name}"', source)
        base = ROOT / "build/MakeUp-ShaderLOD-Experimental-9.5g.zip"
        if not base.exists():
            self.skipTest("Ignored frozen derivative absent; source fixtures remain")
        self.assertEqual(hashlib.sha256(base.read_bytes()).hexdigest(), self.manifest["base_sha256"])
        original = read_archive(base)
        result = apply_bridge_patches(original, self.manifest)
        changed = {name for name in original if result[name] != original[name]}
        self.assertEqual(changed, {p["path"] for p in self.manifest["patches"]})
        before, after = original["shaders/lib/water.glsl"].decode(), result["shaders/lib/water.glsl"].decode()
        self.assertEqual(before[before.index("//  GLASS"):], after[after.index("//  GLASS"):])
        self.assertEqual(before[before.index("vec3 fastRaymarch("):before.index("\n#if SUN_REFLECTION")],
                         after[after.index("vec3 fastRaymarch("):after.index("\n#if SUN_REFLECTION")])
        self.assertEqual(result["shaders/lib/shaderlod/water_ssr.glsl"], original["shaders/lib/shaderlod/water_ssr.glsl"])
        self.assertEqual(result["shaders/lib/water_dh.glsl"], original["shaders/lib/water_dh.glsl"])


if __name__ == "__main__":
    unittest.main()
