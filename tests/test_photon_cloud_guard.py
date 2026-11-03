"""Lightweight source/archive fixtures only: no GLSL compiler, pack build or client."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import build_photon_cloud_guard as guard

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / guard.MANIFEST).read_text())


def select_disabled(source: bytes) -> bytes:
    """Select only this experiment's conditional; leave upstream directives intact."""
    text = source.decode()
    text = text.replace(MANIFEST["option_declaration"], "", 1)
    start = text.index(f"#ifdef {guard.SWITCH}\n")
    fallback = text.index("#else\n", start)
    end = text.index(f"#endif // {guard.SWITCH}\n", fallback)
    return (text[:start] + text[fallback + len("#else\n"):end]
            + text[end + len(f"#endif // {guard.SWITCH}\n"):]).encode()


def brace_body(text: str, anchor: str) -> str:
    start = text.index(anchor) + len(anchor)
    level = 1
    for index in range(start, len(text)):
        level += (text[index] == "{") - (text[index] == "}")
        if level == 0:
            return text[start:index]
    raise AssertionError(f"Unclosed fixture block: {anchor}")


class CloudGuardFixtureTests(unittest.TestCase):
    def fixture(self):
        # The manifest's two full literal anchors form a small upstream-source fixture.
        source = "\n".join(patch["before"] for patch in MANIFEST["replacements"]).encode()
        manifest = copy.deepcopy(MANIFEST)
        manifest["source_sha256"] = guard.sha256(source)
        disabled = source.decode()
        for patch in manifest["replacements"]:
            disabled = disabled.replace(patch["before"], patch["after"], 1)
        enabled = disabled.replace(f"//#define {guard.SWITCH}\n", f"#define {guard.SWITCH}\n", 1)
        manifest["member_sha256_by_arm"] = {
            "original": guard.sha256(source),
            "disabled": guard.sha256(disabled.encode()),
            "enabled": guard.sha256(enabled.encode()),
        }
        return source, manifest

    def test_disabled_fallback_is_byte_exact_and_original_arm_is_untouched(self):
        source, manifest = self.fixture()
        self.assertEqual(source, guard.patch_member(source, manifest, "original"))
        disabled = guard.patch_member(source, manifest, "disabled")
        self.assertEqual(source, select_disabled(disabled))
        self.assertIn(f"//#define {guard.SWITCH}\n".encode(), disabled)

    def test_only_enabled_declaration_differs_between_experiment_arms(self):
        source, manifest = self.fixture()
        disabled = guard.patch_member(source, manifest, "disabled")
        enabled = guard.patch_member(source, manifest, "enabled")
        self.assertEqual(enabled, disabled.replace(
            f"//#define {guard.SWITCH}\n".encode(), f"#define {guard.SWITCH}\n".encode(), 1))

    def test_derivative_sensitive_reads_precede_varying_predicate(self):
        active = MANIFEST["replacements"][1]["after"].split("#else\n", 1)[0]
        predicate = active.index("if (is_translucent || is_translucent_lod)")
        outer = active[:predicate]
        self.assertEqual(1, outer.count("textureGather("))
        self.assertEqual(1, outer.count("texture(colortex12, refracted_uv * taau_render_scale)"))
        self.assertIn("if (LIGHTNING_FLASH_UNIFORM > 0.01)", outer)
        consumer = brace_body(active, "if (is_translucent || is_translucent_lod) {")
        color_body = brace_body(consumer, "if (clouds_apparent_distance < view_distance) {")
        self.assertEqual(1, color_body.count("texelFetch("))
        self.assertEqual(1, color_body.count("textureLod("))
        self.assertNotIn("textureGather(", consumer)
        self.assertNotIn("texture(", consumer)
        self.assertNotIn("texelFetch(", outer)
        self.assertNotIn("textureLod(", outer)

    def test_same_coordinates_filter_alpha_darkening_and_lightning_math(self):
        active = MANIFEST["replacements"][1]["after"].split("#else\n", 1)[0]
        for expression in (
            "ivec2(refracted_uv * view_res * taau_render_scale)",
            "2.0 * dampen(max0(1.0 - 0.1 * pixel_age))",
            "textureLod(colortex11, refracted_uv * taau_render_scale, ld)",
            "min_of(textureGather(colortex12, refracted_uv * taau_render_scale, 0))",
            "clamp01(1.0 - blindness - darknessFactor)",
            "fragment_color * clouds_and_aurora.w + clouds_and_aurora.xyz",
            "LIGHTNING_FLASH_UNIFORM * lightning_flash_intensity *\n                    clouds_ambient_scattering",
        ):
            self.assertIn(expression, active)
        self.assertNotIn("return", active)
        self.assertNotIn("discard", active)

    def test_source_and_output_hash_changes_fail_closed(self):
        source, manifest = self.fixture()
        with self.assertRaisesRegex(ValueError, "member changed"):
            guard.patch_member(source + b"\n", manifest, "enabled")
        broken = copy.deepcopy(manifest)
        broken["replacements"][1]["after"] += "// unreviewed\n"
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            guard.patch_member(source, broken, "original")
        broken = copy.deepcopy(manifest)
        broken["member_sha256_by_arm"]["enabled"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            guard.patch_member(source, broken, "disabled")

    def test_ambiguous_and_missing_anchors_fail_closed(self):
        source, manifest = self.fixture()
        for corrupt in (source + manifest["replacements"][0]["before"].encode(),
                        source.replace(manifest["replacements"][1]["before"].encode(), b"")):
            changed = copy.deepcopy(manifest)
            changed["source_sha256"] = guard.sha256(corrupt)
            with self.assertRaisesRegex(ValueError, "exactly once"):
                guard.patch_member(corrupt, changed, "disabled")

    def test_scope_version_and_arm_are_fixed(self):
        source, manifest = self.fixture()
        for key, value in (("member", "shaders/settings.glsl"), ("base_version", "v2"),
                           ("base_sha256", "0" * 64), ("schema", 2)):
            changed = copy.deepcopy(manifest)
            changed[key] = value
            with self.assertRaisesRegex(ValueError, "manifest"):
                guard.patch_member(source, changed, "disabled")
        with self.assertRaisesRegex(ValueError, "Unknown"):
            guard.patch_member(source, manifest, "unknown")

    def test_duplicate_or_unsafe_archive_entries_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory) / "fixture.zip"
            for names in (("same", "same"), ("../escape",), ("/absolute",), ("bad\\name",)):
                with self.subTest(names=names):
                    with zipfile.ZipFile(base, "w") as archive:
                        # Duplicate warning is expected; this archive is deliberately invalid.
                        import warnings
                        with warnings.catch_warnings():
                            warnings.simplefilter("ignore", UserWarning)
                            for name in names:
                                archive.writestr(name, b"fixture")
                    with self.assertRaises(ValueError):
                        guard.archive_members(base)

    def test_output_restriction_rejects_upstream_before_reading_or_writing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "ignored build"):
                guard.build(root / "dependencies/upstream/photon_v1.3b.zip", root=root)
            self.assertEqual([], list(root.iterdir()))


class PinnedCloudGuardSourceTests(unittest.TestCase):
    @unittest.skipUnless((ROOT / "dependencies/upstream/photon_v1.3b.zip").exists(), "Pinned original not downloaded")
    def test_exact_pinned_source_hashes_fallback_and_entry_points(self):
        base = ROOT / "dependencies/upstream/photon_v1.3b.zip"
        self.assertEqual(guard.BASE_SHA256, guard.sha256(base.read_bytes()))
        with zipfile.ZipFile(base) as archive:
            original = archive.read(guard.MEMBER)
            self.assertEqual(MANIFEST["license_sha256"], guard.sha256(archive.read("LICENSE")))
            self.assertEqual(original, select_disabled(guard.patch_member(original, MANIFEST, "disabled")))
            self.assertEqual(MANIFEST["member_sha256_by_arm"]["enabled"],
                             guard.sha256(guard.patch_member(original, MANIFEST, "enabled")))
            for world in ("world0", "world1", "world-1"):
                entry = archive.read(f"shaders/{world}/composite1.fsh").decode()
                self.assertIn('#include "/program/c1_blend_layers.fsh"', entry)
            self.assertIn(b"Photon Shader by SixthSurge", guard.patch_member(original, MANIFEST, "enabled"))


if __name__ == "__main__":
    unittest.main()
