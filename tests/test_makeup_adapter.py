"""Bounded source/archive checks only: no GLSL compiler, client or GPU work."""
import copy
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from build_makeup_adapter import apply_patches, build, read_archive


class MakeUpAdapterTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((ROOT / "patches/makeup/9.5g.json").read_text())

    def fixture(self, root):
        """Create a small synthetic archive with real literal patch anchors."""
        files = {name: b"fixture source\n" for name in self.manifest["source_sha256"]}
        for name in {patch["path"] for patch in self.manifest["patches"]}:
            files[name] = "\nfixture separator\n".join(
                patch["before"] for patch in self.manifest["patches"] if patch["path"] == name).encode()
        files["LICENSE"] = (ROOT / "patches/makeup/LICENSE.MakeUp").read_bytes()
        files["shaders/lib/water_dh.glsl"] = b"DH unchanged fixture\n"
        files["README.md"] = b"Upstream attribution fixture\n"
        archive_path = root / "upstream.zip"
        with zipfile.ZipFile(archive_path, "w") as archive:
            for name, content in files.items():
                archive.writestr(name, content)
        manifest = copy.deepcopy(self.manifest)
        manifest["source_sha256"] = {name: hashlib.sha256(files[name]).hexdigest() for name in manifest["source_sha256"]}
        manifest["archive_hashes"] = {algorithm: hashlib.new(algorithm, archive_path.read_bytes()).hexdigest()
                                      for algorithm in manifest["archive_hashes"]}
        (root / "patches/makeup").mkdir(parents=True)
        for name in ("LICENSE.MakeUp", "LICENSE.GPL-3.0"):
            shutil.copy(ROOT / "patches/makeup" / name, root / "patches/makeup" / name)
        (root / "patches/makeup/9.5g.json").write_text(json.dumps(manifest))
        (root / "dev").mkdir()
        (root / "dependencies").mkdir(exist_ok=True)
        (root / "dependencies/shader-candidates.lock.json").write_text(json.dumps({"candidates": [{
            "modrinth_project": "makeup-ultra-fast-shaders",
            "release": {"version": "9.5g", "filename": "upstream.zip", **manifest["archive_hashes"]},
            "source": {"revision": manifest["source_revision"]},
        }]}))
        shutil.copytree(ROOT / "shaderpacks/makeup", root / "shaderpacks/makeup")
        return archive_path, files, manifest

    def test_hash_and_anchor_guards(self):
        with tempfile.TemporaryDirectory() as directory:
            _, files, manifest = self.fixture(Path(directory))
            result = apply_patches(files, manifest)
            self.assertEqual(result["shaders/lib/water_dh.glsl"], files["shaders/lib/water_dh.glsl"])
            changed = files.copy()
            changed["shaders/lib/water.glsl"] += b"changed"
            with self.assertRaisesRegex(ValueError, "source changed"):
                apply_patches(changed, manifest)
            changed_manifest = copy.deepcopy(manifest)
            changed_manifest["patches"][0]["before"] = "missing anchor"
            with self.assertRaisesRegex(ValueError, "exactly once"):
                apply_patches(files, changed_manifest)
            changed_manifest["patches"][0]["before"] = ""
            with self.assertRaisesRegex(ValueError, "exactly once"):
                apply_patches(files, changed_manifest)
            unpinned = copy.deepcopy(manifest)
            unpinned["patches"][0]["path"] = "README.md"
            with self.assertRaisesRegex(ValueError, "Unpinned"):
                apply_patches(files, unpinned)

    def test_duplicate_and_unsafe_archive_members_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory) / "archive.zip"
            for name in ("../escape", "/absolute", "shaders\\escape"):
                with zipfile.ZipFile(base, "w") as archive:
                    archive.writestr(name, b"no")
                with self.assertRaisesRegex(ValueError, "Unsafe"):
                    read_archive(base)
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(base, "w") as archive:
                    archive.writestr("duplicate", b"one")
                    archive.writestr("duplicate", b"two")
            with self.assertRaisesRegex(ValueError, "Duplicate"):
                read_archive(base)

    def test_fixture_archive_determinism_attribution_and_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, original, _ = self.fixture(root)
            first, second = root / "first.zip", root / "second.zip"
            metadata = build(first, base, root)
            self.assertEqual(metadata, build(second, base, root))
            self.assertEqual(first.read_bytes(), second.read_bytes())
            result = read_archive(first)
            self.assertEqual(result["LICENSE"], original["LICENSE"])
            self.assertEqual(result["README.md"], original["README.md"])
            self.assertEqual(result["shaders/lib/water_dh.glsl"], original["shaders/lib/water_dh.glsl"])
            self.assertIn("LICENSE.GPL-3.0", result)
            self.assertIn("shaderlod-source-patch.json", result)
            self.assertIn("SHADERLOD_EXPERIMENT.md", result)
            self.assertFalse(metadata["common_protocol_connected"])
            self.assertFalse(metadata["default_enabled"])
            self.assertFalse(any(metadata["validation"].values()))
            self.assertEqual(metadata["modified_upstream_files"], sorted({p["path"] for p in self.manifest["patches"]}))

    def test_builder_rejects_archive_lock_license_overlay_and_overwrite_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, _, _ = self.fixture(root)
            original = base.read_bytes()
            base.write_bytes(original + b"changed")
            with self.assertRaisesRegex(ValueError, "base sha256 mismatch"):
                build(root / "out.zip", base, root)
            base.write_bytes(original)
            with self.assertRaisesRegex(ValueError, "overwrite"):
                build(base, base, root)
            lock_path = root / "dependencies/shader-candidates.lock.json"
            lock = lock_path.read_text()
            content = json.loads(lock)
            content["candidates"][0]["source"]["revision"] = "changed"
            lock_path.write_text(json.dumps(content))
            with self.assertRaisesRegex(ValueError, "version/source pin"):
                build(root / "out.zip", base, root)
            lock_path.write_text(lock)
            license_path = root / "patches/makeup/LICENSE.MakeUp"
            retained = license_path.read_bytes()
            license_path.write_bytes(b"wrong license")
            with self.assertRaisesRegex(ValueError, "license disagrees"):
                build(root / "out.zip", base, root)
            license_path.write_bytes(retained)
            (root / "shaderpacks/makeup/shaders/lib/water.glsl").write_text("overwrite")
            with self.assertRaisesRegex(ValueError, "Overlay"):
                build(root / "out.zip", base, root)

    def test_disabled_original_function_and_early_skip_contract(self):
        patch = next(p for p in self.manifest["patches"] if p["path"] == "shaders/lib/water.glsl")
        active, disabled = patch["after"].split("\n#else\n", 1)
        self.assertEqual(disabled.removesuffix("\n#endif\n"), patch["before"])
        self.assertIn("SHADERLOD_MAKEUP_WATER_SSR == 1 && REFLECTION == 1 && SSR_TYPE == 1", active)
        skip = active.index("if (ssrWeight <= 0.0)")
        self.assertLess(skip, active.index("vec3 pos = shaderlodWaterRaymarch"))
        self.assertLess(skip, active.index("texture2DLod(gaux1"))
        self.assertIn("infinite = 1.0;\n        return vec4(0.0);", active)
        self.assertNotIn("texture2D(", active)
        self.assertIn("border * ssrWeight", active)
        config = next(p for p in self.manifest["patches"] if p["path"] == "shaders/lib/config.glsl")
        self.assertIn("#define SHADERLOD_MAKEUP_WATER_SSR 0 // [0 1]", config["after"])

    def test_helper_distance_and_sampling_contract(self):
        helper = (ROOT / "shaderpacks/makeup/shaders/lib/shaderlod/water_ssr.glsl").read_text()
        self.assertIn("#ifndef SHADERLOD_MAKEUP_WATER_SSR_HELPER\n#define SHADERLOD_MAKEUP_WATER_SSR_HELPER", helper)
        self.assertNotIn("#ifndef SHADERLOD_MAKEUP_WATER_SSR\n", helper)
        weight = helper[helper.index("float shaderlodWaterSSRWeight"):helper.index("vec3 shaderlodWaterRaymarch")]
        self.assertIn("dot(fragpos, fragpos)", weight)
        self.assertIn("startBlocks + 1.0", weight)
        self.assertIn("smoothstep(startBlocks, endBlocks, sqrt(distanceSquared))", weight)
        self.assertNotIn("cameraPosition", weight)
        self.assertNotIn("texture", weight)
        self.assertNotIn("texture2D(", helper)
        self.assertNotIn("dFdx", helper)
        self.assertNotIn("dFdy", helper)
        self.assertIn("texture2DLod(depthtex1, screenMarchPos.xy, 0.0)", helper)

    def test_available_pinned_source_scope_and_search_math(self):
        base = ROOT / "dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"
        if not base.exists():
            self.skipTest("Ignored pinned upstream archive unavailable; fixtures remain independent")
        self.assertEqual(hashlib.sha256(base.read_bytes()).hexdigest(), self.manifest["archive_hashes"]["sha256"])
        original = read_archive(base)
        result = apply_patches(original, self.manifest)
        changed = {name for name in original if result[name] != original[name]}
        self.assertEqual(changed, {patch["path"] for patch in self.manifest["patches"]})
        water = original["shaders/lib/water.glsl"].decode()
        original_ray = water[water.index("vec3 fastRaymarch("):water.index("\n#if SUN_REFLECTION")]
        helper = (ROOT / "shaderpacks/makeup/shaders/lib/shaderlod/water_ssr.glsl").read_text()
        copied_ray = helper[helper.index("vec3 shaderlodWaterRaymarch("):helper.rindex("\n#endif")]
        restored_ray = copied_ray.replace("vec3 shaderlodWaterRaymarch(", "vec3 fastRaymarch(").replace(
            "texture2DLod(depthtex1, screenMarchPos.xy, 0.0)", "texture2D(depthtex1, screenMarchPos.xy)")
        self.assertEqual(restored_ray, original_ray)
        modified = result["shaders/lib/water.glsl"].decode()
        self.assertEqual(modified[modified.index("//  GLASS"):], water[water.index("//  GLASS"):])
        self.assertIn(b"#define RAYMARCH_STEPS 10", result["shaders/lib/config.glsl"])
        self.assertEqual(result["shaders/src/position_vertex_water.glsl"], original["shaders/src/position_vertex_water.glsl"])
        for name in original:
            if name.endswith((".fsh", ".glsl", ".properties")):
                self.assertNotIn(b"gaux1MipmapEnabled", original[name])
                self.assertNotIn(b"colortex4MipmapEnabled", original[name])
        for dimension in ("", "world0/", "world1/", "world-1/"):
            for entry in ("gbuffers_water.fsh", "gbuffers_hand_water.fsh"):
                self.assertIn(b"#extension GL_ARB_shader_texture_lod : enable", original["shaders/" + dimension + entry])


if __name__ == "__main__":
    unittest.main()
