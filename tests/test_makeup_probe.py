"""Light fixture/source checks; never assemble real probes or run a GLSL compiler."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from build_makeup_probe import apply_probe, build
from build_makeup_adapter import read_archive


class MakeUpProbeTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((ROOT / "patches/makeup/probes.json").read_text())
        parent = json.loads((ROOT / "patches/makeup/9.5g.json").read_text())
        self.original_reflection = next(p["before"] for p in parent["patches"] if p["path"] == "shaders/lib/water.glsl")
        self.fixture_water = next(p["after"] for p in parent["patches"] if p["path"] == "shaders/lib/water.glsl").encode() + b"\n//  GLASS\nOriginal glass fixture\n"
        self.fixture_helper = (ROOT / "shaderpacks/makeup/shaders/lib/shaderlod/water_ssr.glsl").read_bytes()

    def fixture(self, root):
        files = {"shaders/lib/water.glsl": self.fixture_water,
                 "shaders/lib/shaderlod/water_ssr.glsl": self.fixture_helper,
                 "shaders/lib/water_dh.glsl": b"untouched DH\n",
                 "shaders/lib/config.glsl": b"untouched default off/options\n",
                 "shaders/src/position_vertex_water.glsl": b"untouched originating position\n",
                 "LICENSE": b"retained license fixture\n", "LICENSE.GPL-3.0": b"retained GPL fixture\n",
                 "SHADERLOD_EXPERIMENT.md": b"retained base notice\n",
                 "shaderlod-source-patch.json": b"{}", "shaderlod-build.json": b"{}"}
        base = root / "base.zip"
        with zipfile.ZipFile(base, "w") as archive:
            for name, source in files.items():
                archive.writestr(name, source)
        manifest = copy.deepcopy(self.manifest)
        manifest["base_sha256"] = hashlib.sha256(base.read_bytes()).hexdigest()
        manifest["source_sha256"] = {name: hashlib.sha256(files[name]).hexdigest() for name in manifest["source_sha256"]}
        (root / "patches/makeup").mkdir(parents=True)
        (root / "patches/makeup/probes.json").write_text(json.dumps(manifest))
        return base, files, manifest

    def test_all_fullweight_function_math_and_disabled_branch(self):
        functions = {}
        for probe, item in self.manifest["probes"].items():
            body = item["patches"][0]["after"]
            body = body[body.index("vec4 reflection_calc("):]
            self.assertNotIn("return vec4(0.0)", body)
            self.assertNotIn("border * ssrWeight", body)
            self.assertEqual(item["requires_options"], {"SHADERLOD_MAKEUP_WATER_SSR": "1", "REFLECTION_SLIDER": "2"})
            functions[probe] = body
        implicit = functions["duplicate-implicit"].replace("shaderlodWaterRaymarch(", "fastRaymarch(")
        self.assertEqual(implicit, self.original_reflection)
        explicit = functions["duplicate-explicit"].replace("texture2DLod(gaux1, pos.xy, 0.0)", "texture2D(gaux1, pos.xy)")
        self.assertEqual(explicit, functions["duplicate-implicit"])
        classified = functions["classifier-full"]
        begin = classified.index("float ssrWeight =")
        end = classified.index("\n    #endif", begin)
        restored = classified[:begin] + "vec3 pos = shaderlodWaterRaymarch(reflected, fragpos, infinite, dither);" + classified[end:]
        self.assertEqual(restored, functions["duplicate-explicit"])
        self.assertEqual(classified.count("pos = shaderlodWaterRaymarch(reflected, fragpos, infinite, dither);"), 2)
        self.assertIn("texture2DLod(gaux1, pos.xy, 0.0)", classified)
        self.assertNotIn("texture2D(gaux1", classified)

    def test_patches_preserve_glass_options_origin_and_off_function(self):
        with tempfile.TemporaryDirectory() as directory:
            _, files, manifest = self.fixture(Path(directory))
            for probe in manifest["probes"]:
                result = apply_probe(files, manifest, probe)
                water = result["shaders/lib/water.glsl"].decode()
                self.assertEqual(water[water.index("\n#else\n"):], self.fixture_water.decode()[self.fixture_water.decode().index("\n#else\n"):])
                for name in ("shaders/lib/water_dh.glsl", "shaders/src/position_vertex_water.glsl", "shaders/lib/config.glsl", "LICENSE", "LICENSE.GPL-3.0"):
                    self.assertEqual(result[name], files[name])
                active = water[:water.index("\n#else\n")]
                if probe == "duplicate-implicit":
                    self.assertNotIn("shaderlodWaterSSRWeight(fragpos)", active)
                    self.assertIn(b"texture2D(depthtex1, screenMarchPos.xy)", result["shaders/lib/shaderlod/water_ssr.glsl"])
                else:
                    self.assertIn(b"texture2DLod(depthtex1, screenMarchPos.xy, 0.0)", result["shaders/lib/shaderlod/water_ssr.glsl"])

    def test_fail_closed_member_hashes_names_and_anchors(self):
        with tempfile.TemporaryDirectory() as directory:
            _, files, manifest = self.fixture(Path(directory))
            with self.assertRaisesRegex(ValueError, "Unknown"):
                apply_probe(files, manifest, "unknown")
            changed = files.copy()
            changed["shaders/lib/water.glsl"] += b"tampered"
            with self.assertRaisesRegex(ValueError, "member changed"):
                apply_probe(changed, manifest, "duplicate-explicit")
            altered = copy.deepcopy(manifest)
            altered["probes"]["duplicate-explicit"]["patches"][0]["before"] = ""
            with self.assertRaisesRegex(ValueError, "exactly once"):
                apply_probe(files, altered, "duplicate-explicit")
            altered["probes"]["duplicate-explicit"]["patches"][0]["path"] = "LICENSE"
            with self.assertRaisesRegex(ValueError, "Unpinned"):
                apply_probe(files, altered, "duplicate-explicit")

    def test_small_fixture_build_determinism_and_included_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, original, _ = self.fixture(root)
            for probe in self.manifest["probes"]:
                first, second = root / "first.zip", root / "second.zip"
                meta = build(first, probe, base, root)
                self.assertEqual(meta, build(second, probe, base, root))
                self.assertEqual(first.read_bytes(), second.read_bytes())
                files = read_archive(first)
                self.assertEqual(files["LICENSE"], original["LICENSE"])
                self.assertEqual(files["shaderlod-build.json"], original["shaderlod-build.json"])
                self.assertIn("shaderlod-probe-source.json", files)
                self.assertIn("SHADERLOD_PROBE.md", files)
                self.assertFalse(any(meta["validation"].values()))
                self.assertTrue(meta["prior_build_metadata_is_base_provenance"])

    def test_builder_exact_archive_and_output_guards(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, _, _ = self.fixture(root)
            with self.assertRaisesRegex(ValueError, "overwrite"):
                build(base, "duplicate-explicit", base, root)
            base.write_bytes(base.read_bytes() + b"changed archive")
            with self.assertRaisesRegex(ValueError, "archive hash mismatch"):
                build(root / "out.zip", "duplicate-explicit", base, root)

    def test_available_real_derivative_pin_and_bounded_scope_without_build(self):
        base = ROOT / "build/MakeUp-ShaderLOD-Experimental-9.5g.zip"
        if not base.exists():
            self.skipTest("Ignored frozen derivative absent")
        self.assertEqual(hashlib.sha256(base.read_bytes()).hexdigest(), self.manifest["base_sha256"])
        original = read_archive(base)
        for probe in self.manifest["probes"]:
            result = apply_probe(original, self.manifest, probe)
            changed = {name for name in original if original[name] != result[name]}
            expected = {"shaders/lib/water.glsl"}
            if probe == "duplicate-implicit":
                expected.add("shaders/lib/shaderlod/water_ssr.glsl")
            self.assertEqual(changed, expected)
            before, after = original["shaders/lib/water.glsl"].decode(), result["shaders/lib/water.glsl"].decode()
            self.assertEqual(after[:after.index("#if SHADERLOD_MAKEUP_WATER_SSR")], before[:before.index("#if SHADERLOD_MAKEUP_WATER_SSR")])
            self.assertEqual(after[after.index("\n#else\n"):], before[before.index("\n#else\n"):])


if __name__ == "__main__":
    unittest.main()
