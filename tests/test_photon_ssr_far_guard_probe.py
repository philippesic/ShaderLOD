"""Pinned source/policy fixtures only; never assemble packs or run a compiler/client."""
import copy
import json
import math
from pathlib import Path
import re
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import build_photon_ssr_far_guard_probe as probe

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / probe.MANIFEST).read_text())


def select_probe(source: bytes, enabled: bool) -> bytes:
    text = source.decode().replace(MANIFEST["option_declaration"], "", 1)
    text = text.replace(MANIFEST["option_declaration"].replace(
        f"//#define {probe.SWITCH}", f"#define {probe.SWITCH}"), "", 1)
    output = []
    inside = False
    for line in text.splitlines(keepends=True):
        if line == f"#ifdef {probe.SWITCH}\n":
            if inside:
                raise AssertionError("Nested probe directive")
            inside = True
        elif line == f"#endif // {probe.SWITCH}\n":
            if not inside:
                raise AssertionError("Unmatched probe directive")
            inside = False
        elif not inside or enabled:
            output.append(line)
    if inside:
        raise AssertionError("Unclosed probe directive")
    return "".join(output).encode()


def trace_environment_block(source: str) -> str:
    start = source.index("#ifdef ENVIRONMENT_REFLECTIONS\n", source.index("vec3 trace_specular_ray("))
    depth = 0
    lines = []
    for line in source[start:].splitlines(keepends=True):
        if line.startswith(("#ifdef ", "#ifndef ", "#if ")):
            depth += 1
        elif line.startswith("#endif"):
            depth -= 1
        lines.append(line)
        if depth == 0:
            return "".join(lines)
    raise AssertionError("Unclosed environment guard")


def select_environment(source: str, environment: bool, enabled: bool) -> str:
    # Only the two reviewed conditional names in this bounded trace region.
    values = {"ENVIRONMENT_REFLECTIONS": environment, probe.SWITCH: enabled}
    stack = []
    output = []
    for line in trace_environment_block(source).splitlines(keepends=True):
        if line.startswith("#ifdef "):
            stack.append(values[line.split()[1]])
        elif line == "#else\n":
            stack[-1] = not stack[-1]
        elif line.startswith("#endif"):
            stack.pop()
        elif all(stack):
            output.append(line)
    return "".join(output)


def diagnostic_tier(tier: int, enabled: bool) -> int:
    # Evaluate the actual reviewed assignment, rather than a separate routing rule.
    after = MANIFEST["members"][0]["replacements"][0]["after"]
    match = re.search(r"if \((quality == \d+)\) quality = (\d+);", after)
    if not match:
        raise AssertionError("Unrecognized diagnostic assignment")
    return int(match[2]) if enabled and eval(match[1], {"__builtins__": {}}, {"quality": tier}) else tier


def reference_tier(distance, noise, enabled=True, transition=8.0):
    """B15 32/96 band fixture, independently used to check diagnostic edges."""
    if not enabled:
        return 0
    if transition <= 0:
        return 0 if distance < 32.0 else 1 if distance < 96.0 else 2
    half = transition * 0.5
    if distance <= 32.0 - half:
        return 0
    if distance >= 96.0 + half:
        return 2
    if 32.0 + half <= distance <= 96.0 - half:
        return 1
    smooth = lambda start, end: (lambda x: x * x * (3.0 - 2.0 * x))(
        min(1.0, max(0.0, (distance - start) / (end - start))))
    low = smooth(96.0 - half, 96.0 + half)
    medium = smooth(32.0 - half, 32.0 + half)
    return 2 if noise < low else 1 if noise < medium else 0


def performance_trace_policy(tier, intersection, refinement, diagnostic):
    selected = diagnostic_tier(tier, diagnostic)
    if selected == 2:
        return None  # Existing B15 branch skips its raymarch.
    steps = intersection if selected == 0 else min(intersection, max(2, math.ceil(intersection * 0.5)))
    refine = refinement if selected == 0 else min(refinement, 2)
    return steps, refine


class SSRFarGuardFixtureTests(unittest.TestCase):
    def fixture(self):
        manifest = copy.deepcopy(MANIFEST)
        files = {"LICENSE": b"untouched license", "shaderlod-build.json": b"untouched metadata"}
        for member in manifest["members"]:
            source = member["replacements"][0]["before"].encode()
            files[member["path"]] = source
            member["source_sha256"] = probe.sha256(source)
            disabled = member["replacements"][0]["after"].encode()
            member["patched_sha256"] = probe.sha256(disabled)
            if member["path"] == probe.SETTINGS:
                enabled = disabled.replace(f"//#define {probe.SWITCH}\n".encode(), f"#define {probe.SWITCH}\n".encode(), 1)
                member["enabled_sha256"] = probe.sha256(enabled)
        return files, manifest

    def test_original_and_disabled_restore_exact_b15_and_keep_metadata(self):
        files, manifest = self.fixture()
        self.assertEqual(files, probe.patch_members(files, manifest, "original"))
        off = probe.patch_members(files, manifest, "disabled")
        for name in probe.MEMBERS:
            self.assertEqual(files[name], select_probe(off[name], False))
        for name in ("LICENSE", "shaderlod-build.json"):
            self.assertEqual(files[name], off[name])

    def test_enabled_differs_from_disabled_only_by_option_declaration(self):
        files, manifest = self.fixture()
        off = probe.patch_members(files, manifest, "disabled")
        on = probe.patch_members(files, manifest, "enabled")
        self.assertEqual({probe.SETTINGS}, {name for name in files if on[name] != off[name]})
        self.assertEqual(on[probe.SETTINGS], off[probe.SETTINGS].replace(
            f"//#define {probe.SWITCH}\n".encode(), f"#define {probe.SWITCH}\n".encode(), 1))

    def test_actual_assignment_retains_near_mid_and_restores_caller_budgets_far(self):
        for intersection, refinement in ((16, 4), (32, 6), (7, 3), (1, 1)):
            self.assertEqual((intersection, refinement), performance_trace_policy(0, intersection, refinement, False))
            self.assertEqual(performance_trace_policy(0, intersection, refinement, False),
                             performance_trace_policy(0, intersection, refinement, True))
            self.assertEqual(performance_trace_policy(1, intersection, refinement, False),
                             performance_trace_policy(1, intersection, refinement, True))
            self.assertIsNone(performance_trace_policy(2, intersection, refinement, False))
            self.assertEqual((intersection, refinement), performance_trace_policy(2, intersection, refinement, True))
        self.assertEqual((8, 2), performance_trace_policy(1, 16, 4, True))

    def test_band_edges_keep_classification_and_restore_only_selected_far(self):
        for distance, noise, expected in ((28.0, 0.25, 0), (36.0, 0.99, 1), (92.0, 0.0, 1),
                                          (96.0, 0.25, 2), (96.0, 0.5, 1), (96.0, 0.99, 1),
                                          (100.0, 0.99, 2), (101.0, 0.99, 2)):
            tier = reference_tier(distance, noise)
            self.assertEqual(expected, tier)
            restored = performance_trace_policy(tier, 16, 4, True)
            self.assertEqual((8, 2) if expected == 1 else (16, 4), restored)
        self.assertEqual(1, reference_tier(95.999, 0.5, transition=0))
        self.assertEqual(2, reference_tier(96.0, 0.5, transition=0))
        self.assertEqual(0, reference_tier(1000.0, 0.0, enabled=False))
        self.assertEqual((16, 4), performance_trace_policy(reference_tier(1000.0, 0.0, enabled=False), 16, 4, True))

    def test_source_pin_anchors_scope_and_output_hashes_fail_closed(self):
        files, manifest = self.fixture()
        path = probe.SPECULAR
        with self.assertRaisesRegex(ValueError, "member missing or changed"):
            probe.patch_members(files | {path: files[path] + b"\n"}, manifest, "enabled")
        changed = copy.deepcopy(manifest)
        changed["members"][0]["replacements"][0]["after"] += "// unreviewed\n"
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            probe.patch_members(files, changed, "original")
        changed = copy.deepcopy(manifest)
        changed["members"][0]["path"] = "shaders/include/shaderlod/lod.glsl"
        with self.assertRaisesRegex(ValueError, "scope"):
            probe.patch_members(files, changed, "disabled")
        for data in (b"", files[path] + files[path]):
            changed = copy.deepcopy(manifest)
            changed["members"][0]["source_sha256"] = probe.sha256(data)
            with self.assertRaisesRegex(ValueError, "exactly once"):
                probe.patch_members(files | {path: data}, changed, "disabled")

    def test_output_cannot_overwrite_b15_or_leave_ignored_build(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "immutable B15"):
                probe.build(root / probe.BASE, root=root)
            with self.assertRaisesRegex(ValueError, "ignored build"):
                probe.build(root / "run/shaderpacks/probe.zip", root=root)
            with self.assertRaisesRegex(ValueError, "Unknown"):
                probe.build(arm="invented", root=root)
            self.assertEqual([], list(root.iterdir()))


class PinnedSSRFarGuardTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ROOT / probe.BASE).exists():
            raise unittest.SkipTest("Immutable B15 unavailable")
        cls.original = probe.archive_members(ROOT / probe.BASE)
        cls.on = probe.patch_members(cls.original, MANIFEST, "enabled")

    def test_exact_archive_license_metadata_and_other_member_bytes(self):
        self.assertEqual(probe.BASE_SHA256, probe.sha256((ROOT / probe.BASE).read_bytes()))
        for name, key in (("LICENSE", "license_sha256"), ("shaderlod-build.json", "baseline_metadata_sha256")):
            self.assertEqual(MANIFEST[key], probe.sha256(self.on[name]))
            self.assertEqual(self.original[name], self.on[name])
        for name in self.original.keys() - set(probe.MEMBERS):
            self.assertEqual(self.original[name], self.on[name], name)
        self.assertEqual(self.original[probe.SPECULAR], select_probe(self.on[probe.SPECULAR], False))

    def test_env_false_never_reenables_trace_and_diagnostic_is_inside_original_guard(self):
        source = self.on[probe.SPECULAR].decode()
        for enabled in (False, True):
            disabled_environment = select_environment(source, False, enabled)
            self.assertIn("const bool hit = false;", disabled_environment)
            self.assertNotIn("raymarch_depth_buffer", disabled_environment)
            self.assertNotIn("quality", disabled_environment)
        environment = select_environment(source, True, True)
        self.assertEqual(1, environment.count("raymarch_depth_buffer("))
        self.assertLess(environment.index("if (quality == 2) quality = 0;"), environment.index("if (quality != 2)"))

    def test_only_new_integer_remap_changes_specular_source(self):
        new = select_probe(self.on[probe.SPECULAR], True).decode()
        original = self.original[probe.SPECULAR].decode()
        remap = ("    // Quality/causality control: restore upstream per-ray budgets at far tier.\n"
                 "    // Keep environment/material eligibility and the existing trace/fallback path.\n"
                 "    if (quality == 2) quality = 0;\n")
        self.assertEqual(original, new.replace(remap, "", 1))
        self.assertNotIn("do_ssr", original)
        self.assertEqual(original.count("texture("), new.count("texture("))
        self.assertEqual(original.count("textureLod("), new.count("textureLod("))
        self.assertEqual(original.count("raymarch_depth_buffer("), new.count("raymarch_depth_buffer("))

    def test_every_local_quality_use_rough_ray_count_and_upstream_caller_gates_are_unchanged(self):
        original = self.original[probe.SPECULAR].decode()
        start = original.index("int quality = shaderlod_ssr_tier(")
        end = original.index("vec3 get_specular_reflections(", start)
        local = original[start:end]
        for expression in ("if (quality != 2)",
                           "uint(shaderlod_budget(int(intersection_step_count), quality, 2))",
                           "quality == 0 ? refinement_step_count : min(refinement_step_count, 2u)"):
            self.assertIn(expression, local)
        self.assertEqual(4, len(re.findall(r"\bquality\b", re.sub(r"//[^\n]*", "", local))))
        self.assertIn("shaderlod_budget(SSR_RAY_COUNT,\n            shaderlod_ssr_tier(length(view_pos), dither), 1)", original)
        for name in ("shaders/program/gbuffers_all_translucent.fsh", "shaders/program/d4_deferred_shading.fsh"):
            source = self.on[name].decode()
            self.assertIn("if (material.ssr_multiplier > eps)", source)
            self.assertIn("#if defined ENVIRONMENT_REFLECTIONS || defined SKY_REFLECTIONS", source)
        self.assertIn("if (NoL < eps)", original)
        self.assertIn("#if defined SSR_ROUGHNESS_SUPPORT && defined SPECULAR_MAPPING", original)
        self.assertIn("if (!is_water)", original)

    def test_depth_march_sampling_and_downstream_hit_fog_sky_paths_stay_literal(self):
        raytracer = self.original["shaders/include/misc/raytracer.glsl"].decode()
        self.assertIn("texelFetch(", raytracer)
        self.assertNotIn("texture(", raytracer)
        specular = self.on[probe.SPECULAR].decode()
        self.assertIn("textureLod(colortex5, hit_uv_prev, mip_level)", specular)
        self.assertIn("texture(colortex7, hit_uv_prev)", specular)
        self.assertIn("get_sky_reflection(ray_dir, skylight, hit_pos)", specular)
        self.assertIn("bicubic_filter(colortex4, project_sky(ray_dir))", specular)
        self.assertEqual(self.original["shaders/include/utility/bicubic.glsl"],
                         self.on["shaders/include/utility/bicubic.glsl"])


if __name__ == "__main__":
    unittest.main()
