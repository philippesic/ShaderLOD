"""Static protocol/scope and small ZIP fixtures; never compile or build real packs."""
import copy
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import tempfile
import unittest
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from build_miniature_bridge_probe import apply_patches, build, read_archive


class MiniatureBridgeTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((ROOT / "patches/miniature/2.19-core-bridge-probe.json").read_text())
        self.helper = (ROOT / "shaderpacks/miniature-bridge-probe/shaders/common/shaderlodCoreProbe.glsl").read_text()

    def fixture(self, root):
        files = {name: b"untouched source fixture\r\n" for name in self.manifest["source_sha256"]}
        for name in {patch["path"] for patch in self.manifest["patches"]}:
            files[name] = "\r\nfixture separator\r\n".join(
                patch["before"] for patch in self.manifest["patches"] if patch["path"] == name).encode()
        files["shaders/unmodified.fsh"] = b"untouched entire member\r\n"
        base = root / "base.zip"
        with zipfile.ZipFile(base, "w") as archive:
            for name, content in files.items():
                archive.writestr(name, content)
        manifest = copy.deepcopy(self.manifest)
        manifest["archive_hashes"] = {a: hashlib.new(a, base.read_bytes()).hexdigest() for a in manifest["archive_hashes"]}
        manifest["source_sha256"] = {name: hashlib.sha256(files[name]).hexdigest() for name in manifest["source_sha256"]}
        (root / "patches/miniature").mkdir(parents=True)
        (root / "patches/miniature/2.19-core-bridge-probe.json").write_text(json.dumps(manifest))
        for name in ("LICENSE.Miniature", "ATTRIBUTION.md"):
            shutil.copy(ROOT / "patches/miniature" / name, root / "patches/miniature" / name)
        (root / "dev").mkdir()
        (root / "dev/shader-candidates.lock.json").write_text(json.dumps({"candidates": [{
            "modrinth_project": "miniature-shader", "release": {
                "version": "2.19", "filename": "base.zip", **manifest["archive_hashes"]}}]}))
        (root / "docs").mkdir()
        (root / "docs/MINIATURE_CORE_BRIDGE_PROBE.md").write_text("fixture diagnostic contract")
        shutil.copytree(ROOT / "shaderpacks/miniature-bridge-probe", root / "shaderpacks/miniature-bridge-probe")
        return base, files, manifest

    def assert_cue_off_restores_original_source(self, original, patched, manifest):
        """Project only newly added compile-time cue blocks/option definitions out."""
        restored = patched.copy()
        for patch in reversed(manifest["patches"]):
            if patch["path"] not in ("shaders/shader.h", "shaders/program/final.fsh"):
                continue
            if patch["path"] == "shaders/program/final.fsh":
                addition = patch["after"].removeprefix(patch["before"])
                self.assertIn("#if SHADERLOD_MINIATURE_CORE_CUE == 1\r\n", addition)
                self.assertTrue(addition.endswith("#endif\r\n"))
            else:
                prefix = patch["after"].removesuffix(patch["before"])
                self.assertEqual(len(prefix.splitlines()), 2)
                self.assertTrue(all(line.startswith("#define SHADERLOD_MINIATURE_") for line in prefix.splitlines()))
            source = restored[patch["path"]].decode()
            self.assertEqual(source.count(patch["after"]), 1)
            restored[patch["path"]] = source.replace(patch["after"], patch["before"], 1).encode()
        for name in ("shaders/shader.h", "shaders/program/final.fsh"):
            self.assertEqual(restored[name], original[name])

    def test_protocol_boundary_and_declaration_fixtures(self):
        sentinel = int(re.search(r"if \(shaderlodBridgeVersion != (\d+)\)", self.helper).group(1))
        self.assertEqual(sentinel, 2)
        for version in (-1, 0, 1, 3, 99):
            with self.subTest(version=version):
                self.assertTrue(version != sentinel)
        self.assertFalse(2 != sentinel)
        self.assertIn("if (shaderlodEnabled == 0) return vec3(1.0, 1.0, 0.0);", self.helper)
        self.assertLess(self.helper.index("shaderlodBridgeVersion != 2"), self.helper.index("float nearBlocks"))
        self.assertLess(self.helper.index("shaderlodEnabled == 0"), self.helper.index("length(viewPos)"))
        guard = self.helper[self.helper.index("#if SHADERLOD_MINIATURE_CORE_PROBE == 1"):self.helper.index("vec3 shaderlodMiniatureCoreCue")]
        for name, kind in self.manifest["declared_core_fields"].items():
            self.assertIn(f"uniform {kind} {name};", guard)
        self.assertIn("#endif", guard)
        for field in self.manifest["unused_core_fields"]:
            self.assertNotIn(field, self.helper)
        self.assertIn("#else\n    return vec3(0.5);", self.helper)
        self.assertIn("length(viewPos)", self.helper)
        self.assertIn("max(shaderlodFar, nearBlocks + 1.0)", self.helper)
        self.assertNotIn("length(feetPos)", self.helper)
        self.assertNotIn("length(worldPos)", self.helper)
        self.assertNotIn("texture2D", self.helper)
        self.assertNotIn("sampler", self.helper)

    def test_defaultoff_overlay_after_native_mix_no_new_sampler(self):
        options = next(p for p in self.manifest["patches"] if p["path"] == "shaders/shader.h")["after"]
        self.assertIn("#define SHADERLOD_MINIATURE_CORE_PROBE 0 //[0 1]", options)
        self.assertIn("#define SHADERLOD_MINIATURE_CORE_CUE 0 //[0 1]", options)
        final_patches = [p for p in self.manifest["patches"] if p["path"] == "shaders/program/final.fsh"]
        self.assertEqual(len(final_patches), 2)
        for patch in final_patches:
            self.assertTrue(patch["after"].startswith(patch["before"]))
            added = patch["after"].removeprefix(patch["before"])
            self.assertNotIn("sampler", added)
            self.assertNotIn("texture2D", added)
            self.assertNotIn("getReflectionColor(", added)
        cue = final_patches[1]
        self.assertIn("reflectionColor.a * reflectivity * 0.1*REFLECTIONS", cue["before"])
        self.assertIn("color.rgb = mix(color.rgb, shaderlodMiniatureCoreCue(viewPos), 0.8);", cue["after"])
        self.assertNotIn("color.a", cue["after"])

    def test_crlf_and_cue_off_exact_source_projection(self):
        with tempfile.TemporaryDirectory() as directory:
            _, files, manifest = self.fixture(Path(directory))
            patched = apply_patches(files, manifest)
            self.assert_cue_off_restores_original_source(files, patched, manifest)
            for name, content in files.items():
                if name not in {p["path"] for p in manifest["patches"]}:
                    self.assertEqual(patched[name], content)
            self.assertIn(b"\r\n", patched["shaders/program/final.fsh"])

    def test_negative_source_anchor_and_unpinned_patch_guards(self):
        with tempfile.TemporaryDirectory() as directory:
            _, files, manifest = self.fixture(Path(directory))
            changed = files.copy()
            changed["shaders/program/final.fsh"] += b"changed"
            with self.assertRaisesRegex(ValueError, "source changed"):
                apply_patches(changed, manifest)
            for before in ("", "missing literal"):
                bad = copy.deepcopy(manifest)
                bad["patches"][0]["before"] = before
                with self.assertRaisesRegex(ValueError, "exactly once"):
                    apply_patches(files, bad)
            duplicated = files.copy()
            duplicated["shaders/shader.h"] += manifest["patches"][0]["before"].encode()
            bad = copy.deepcopy(manifest)
            bad["source_sha256"]["shaders/shader.h"] = hashlib.sha256(duplicated["shaders/shader.h"]).hexdigest()
            with self.assertRaisesRegex(ValueError, "exactly once"):
                apply_patches(duplicated, bad)
            bad = copy.deepcopy(manifest)
            bad["patches"][0]["path"] = "shaders/unmodified.fsh"
            with self.assertRaisesRegex(ValueError, "Unpinned"):
                apply_patches(files, bad)

    def test_small_fixture_determinism_restored_license_and_attribution(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, original, _ = self.fixture(root)
            first, second = root / "first.zip", root / "second.zip"
            meta = build(first, base, root)
            self.assertEqual(meta, build(second, base, root))
            self.assertEqual(first.read_bytes(), second.read_bytes())
            result = read_archive(first)
            self.assertEqual(result["shaders/common/getReflectionColor.fsh"], original["shaders/common/getReflectionColor.fsh"])
            self.assertEqual(result["LICENSE.Miniature"], (ROOT / "patches/miniature/LICENSE.Miniature").read_bytes())
            self.assertIn(b"Copyright (c) 2022 Mateus A. Kreuch", result["LICENSE.Miniature"])
            self.assertIn("SHADERLOD_MINIATURE_ATTRIBUTION.md", result)
            self.assertIn("shaderlod-miniature-source.json", result)
            self.assertTrue(meta["normal_ssr_source_unchanged"])
            self.assertFalse(any(meta["validation"].values()))

    def test_builder_archive_license_overlay_and_overwrite_guards(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, _, _ = self.fixture(root)
            with self.assertRaisesRegex(ValueError, "overwrite"):
                build(base, base, root)
            license_path = root / "patches/miniature/LICENSE.Miniature"
            original = license_path.read_bytes()
            license_path.write_bytes(b"changed license")
            with self.assertRaisesRegex(ValueError, "license changed"):
                build(root / "out.zip", base, root)
            license_path.write_bytes(original)
            helper = root / "shaderpacks/miniature-bridge-probe/shaders/common/shaderlodCoreProbe.glsl"
            helper.write_text(helper.read_text() + "changed")
            with self.assertRaisesRegex(ValueError, "overlay changed"):
                build(root / "out.zip", base, root)
            base.write_bytes(base.read_bytes() + b"changed")
            with self.assertRaisesRegex(ValueError, "base sha256 mismatch"):
                build(root / "out.zip", base, root)

    def test_unsafe_and_duplicate_archive_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory) / "base.zip"
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

    def test_available_exact_release_all_untouched_members_and_mask(self):
        base = ROOT / "dev/upstream/other-pack-research/miniature-shader-2.19.zip"
        if not base.exists():
            self.skipTest("Ignored publisher release absent; fixtures remain independent")
        self.assertEqual(hashlib.sha256(base.read_bytes()).hexdigest(), self.manifest["archive_hashes"]["sha256"])
        original = read_archive(base)
        patched = apply_patches(original, self.manifest)
        changed = {name for name in original if original[name] != patched[name]}
        self.assertEqual(changed, {p["path"] for p in self.manifest["patches"]})
        self.assertEqual(len(changed), 4)
        self.assert_cue_off_restores_original_source(original, patched, self.manifest)
        before, after = original["shaders/program/final.fsh"].decode(), patched["shaders/program/final.fsh"].decode()
        mask = "if (reflectivity > MIN_REFLECTIVITY && abs(reflectivityAndRoughness.z - 0.5) < 0.01) {"
        self.assertEqual(before.count(mask), 1)
        self.assertEqual(after.count(mask), 1)
        self.assertLess(after.index(mask), after.index("shaderlodMiniatureCoreCue(viewPos)"))
        self.assertEqual(after.count("getReflectionColor(depth, normal, viewPos)"), 1)
        self.assertLess(after.index("getReflectionColor(depth, normal, viewPos)"), after.index("shaderlodMiniatureCoreCue(viewPos)"))
        self.assertEqual(re.findall(r"uniform sampler2D \w+;", before), re.findall(r"uniform sampler2D \w+;", after))
        self.assertEqual(before.count("texture2D("), after.count("texture2D("))


if __name__ == "__main__":
    unittest.main()
