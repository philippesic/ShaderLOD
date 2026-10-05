"""Bounded source/algebra fixtures only; never build a full pack or run a compiler/client."""
import copy
import json
import math
from pathlib import Path
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import build_photon_cloud_secondary_probe as probe

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / probe.MANIFEST).read_text())


def select_probe(source: bytes, enabled: bool) -> bytes:
    """Select only this probe's conditionals, preserving all baseline directives."""
    text = source.decode()
    declaration = MANIFEST["option_declaration"]
    text = text.replace(declaration, "", 1).replace(
        declaration.replace(f"//#define {probe.SWITCH}", f"#define {probe.SWITCH}"), "", 1)
    selected = []
    branch = None
    for line in text.splitlines(keepends=True):
        if line == f"#ifdef {probe.SWITCH}\n":
            if branch is not None:
                raise AssertionError("Unexpected nested probe block")
            branch = enabled
        elif line == "#else\n" and branch is not None:
            branch = not branch
        elif line == f"#endif // {probe.SWITCH}\n":
            if branch is None:
                raise AssertionError("Unmatched probe directive")
            branch = None
        elif branch is None or branch:
            selected.append(line)
    if branch is not None:
        raise AssertionError("Unclosed probe directive")
    return "".join(selected).encode()


def body(text: str, anchor: str) -> str:
    start = text.index("{", text.index(anchor)) + 1
    level = 1
    for index in range(start, len(text)):
        level += (text[index] == "{") - (text[index] == "}")
        if level == 0:
            return text[start:index]
    raise AssertionError(f"Unclosed fixture body: {anchor}")


def f32(value):
    return struct.unpack("f", struct.pack("f", value))[0]


def ratio32(count, original):
    # Preserve source's left-associated RHS; do not cancel either count division.
    count_ratio = f32(f32(count) / f32(original))
    numerator = f32(math.exp2(f32(original)) - 1.0)
    denominator = f32(math.exp2(f32(count)) - 1.0)
    return f32(f32(count_ratio * numerator) / denominator)


def ray_fixture(initial_length, count, origin, direction, dither):
    """Float32 model of unchanged secondary growth/order and a spatial density field."""
    position = [f32(value) for value in origin]
    step = [f32(f32(value) * initial_length) for value in (*direction, 1.0)]
    samples = []
    depth = f32(0.0)
    for _ in range(count):
        step = [f32(value * 2.0) for value in step]
        sample = tuple(f32(position[index] + f32(step[index] * dither)) for index in range(3))
        density = f32(max(0.0, math.sin(sample[0] * 0.07) + math.cos(sample[1] * 0.03) - sample[2] * 0.002))
        depth = f32(depth + f32(density * step[3]))
        samples.append(sample)
        position = [f32(position[index] + step[index]) for index in range(3)]
    return samples, depth, tuple(position)


class SecondaryProbeFixtureTests(unittest.TestCase):
    def fixture(self):
        manifest = copy.deepcopy(MANIFEST)
        files = {"LICENSE": b"unmodified license fixture", "shaderlod-build.json": b"baseline fixture"}
        for member in manifest["members"]:
            anchors = [item["before"] for item in member["replacements"]]
            if member["path"] == probe.SETTINGS:
                source = anchors[0].encode()
            else:
                source = (anchors[0] + "\n    float step_length = 1.0;\n"
                          "    if (step_count != original_steps) {\n" + anchors[1]
                          + "    }\n    return step_length;\n}\n"
                          "void fixture_primary_ray() {\n" + anchors[2]
                          + "    for (uint i = 0u; i < primary_steps; ++i) {\n"
                          + anchors[3] + "\n" + anchors[4] + "\n    }\n}\n").encode()
            files[member["path"]] = source
            member["source_sha256"] = probe.sha256(source)
            patched = source.decode()
            for item in member["replacements"]:
                patched = patched.replace(item["before"], item["after"], 1)
            member["patched_sha256"] = probe.sha256(patched.encode())
            if member["path"] == probe.SETTINGS:
                enabled = patched.replace(f"//#define {probe.SWITCH}\n", f"#define {probe.SWITCH}\n", 1)
                member["enabled_sha256"] = probe.sha256(enabled.encode())
        return files, manifest

    def test_probe_disabled_recovers_exact_baseline_and_original_arm_keeps_all_bytes(self):
        files, manifest = self.fixture()
        self.assertEqual(files, probe.patch_members(files, manifest, "original"))
        off = probe.patch_members(files, manifest, "disabled")
        for name in probe.MEMBERS:
            self.assertEqual(files[name], select_probe(off[name], False), name)
        self.assertEqual(files["LICENSE"], off["LICENSE"])
        self.assertEqual(files["shaderlod-build.json"], off["shaderlod-build.json"])

    def test_enabled_only_changes_option_declaration(self):
        files, manifest = self.fixture()
        off = probe.patch_members(files, manifest, "disabled")
        on = probe.patch_members(files, manifest, "enabled")
        changed = {name for name in files if off[name] != on[name]}
        self.assertEqual({probe.SETTINGS}, changed)
        self.assertEqual(on[probe.SETTINGS], off[probe.SETTINGS].replace(
            f"//#define {probe.SWITCH}\n".encode(), f"#define {probe.SWITCH}\n".encode(), 1))

    def test_exact_ratio_expression_and_original_secondary_body_are_preserved(self):
        files, manifest = self.fixture()
        patched = probe.patch_members(files, manifest, "enabled")
        for member in manifest["members"][:-1]:
            path = member["path"]
            layer = Path(path).stem
            active = select_probe(patched[path], True).decode()
            original_rhs = member["replacements"][1]["before"].strip().removeprefix("step_length *= ").removesuffix(";")
            helper = body(active, f"float clouds_{layer}_secondary_extent_scale(")
            self.assertEqual("return " + original_rhs + ";", helper.strip())
            new_body = body(active, f"float clouds_{layer}_optical_depth(")
            new_body = new_body.replace("step_length *= extent_scale;", "step_length *= " + original_rhs + ";", 1)
            self.assertEqual(body(files[path].decode(), f"float clouds_{layer}_optical_depth("), new_body)

    def test_each_ratio_is_count_guarded_and_selected_once_before_primary_loop(self):
        files, manifest = self.fixture()
        patched = probe.patch_members(files, manifest, "enabled")
        for member in manifest["members"][:-1]:
            layer = Path(member["path"]).stem
            macro = "CLOUDS_" + layer.upper()
            active = select_probe(patched[member["path"]], True).decode()
            setup = member["replacements"][2]["after"]
            for prefix, option in (("lighting", "LIGHTING"), ("ambient", "AMBIENT")):
                self.assertIn(f"float {prefix}_extent_scale = 1.0;", setup)
                guarded = body(setup, f"if ({prefix}_steps != {macro}_{option}_STEPS)")
                self.assertIn(f"{prefix}_extent_scale = clouds_{layer}_secondary_extent_scale(", guarded)
            self.assertLess(active.index("float lighting_extent_scale"), active.index("for (uint i = 0u; i < primary_steps; ++i)"))
            primary = body(active, "for (uint i = 0u; i < primary_steps; ++i)")
            self.assertNotIn("secondary_extent_scale(", primary)
            self.assertNotIn("exp2(", primary)
            self.assertEqual(1, primary.count("lighting_extent_scale"))
            self.assertEqual(1, primary.count("ambient_extent_scale"))
            self.assertIn("if (step_count != original_steps)", body(active, f"float clouds_{layer}_optical_depth("))

    def test_float32_step_positions_support_and_weighted_outputs_match_all_option_counts(self):
        # All advertised secondary counts are <=12; test reduced and equal counts,
        # layer base lengths, sunrise/sunset factors and distinct secondary jitters.
        cases = 0
        for original in range(1, 13):
            for count in range(1, original + 1):
                for base in (0.1 * 1500.0, 0.25 * 900.0, 0.5 * 900.0):
                    initial = f32(f32(base) / f32(count))
                    hoisted = ratio32(count, original) if count != original else 1.0
                    for dither in (0.0, 0.37, 0.999):
                        baseline_initial = initial
                        if count != original:
                            baseline_initial = f32(baseline_initial * ratio32(count, original))
                        probe_initial = initial
                        if count != original:
                            probe_initial = f32(probe_initial * hoisted)
                        reference = ray_fixture(baseline_initial, count, (124.2, 7321.5, -45.7), (0.4, 0.8, -0.2), dither)
                        candidate = ray_fixture(probe_initial, count, (124.2, 7321.5, -45.7), (0.4, 0.8, -0.2), dither)
                        self.assertEqual(reference, candidate)
                        cases += 1
        self.assertEqual(702, cases)

    def test_changed_source_output_hash_or_member_scope_fails_closed(self):
        files, manifest = self.fixture()
        corrupt = files.copy()
        corrupt[probe.MEMBERS[0]] += b"\n"
        with self.assertRaisesRegex(ValueError, "member missing or changed"):
            probe.patch_members(corrupt, manifest, "enabled")
        changed = copy.deepcopy(manifest)
        changed["members"][0]["replacements"][0]["after"] += "// unreviewed\n"
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            probe.patch_members(files, changed, "original")
        changed = copy.deepcopy(manifest)
        changed["members"][0]["path"] = "shaders/include/shaderlod/lod.glsl"
        with self.assertRaisesRegex(ValueError, "scope"):
            probe.patch_members(files, changed, "disabled")

    def test_missing_duplicate_anchor_and_invalid_arm_fail_closed(self):
        files, manifest = self.fixture()
        path = probe.MEMBERS[0]
        anchor = manifest["members"][0]["replacements"][0]["before"].encode()
        for data in (files[path] + anchor, files[path].replace(anchor, b"", 1)):
            corrupt = files | {path: data}
            changed = copy.deepcopy(manifest)
            changed["members"][0]["source_sha256"] = probe.sha256(data)
            with self.assertRaisesRegex(ValueError, "exactly once"):
                probe.patch_members(corrupt, changed, "disabled")
        with self.assertRaisesRegex(ValueError, "Unknown"):
            probe.patch_members(files, manifest, "invented")

    def test_output_rejects_baseline_replacement_and_nonignored_paths_without_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "immutable B15"):
                probe.build(root / probe.BASE, root=root)
            with self.assertRaisesRegex(ValueError, "ignored build"):
                probe.build(root / "run/shaderpacks/probe.zip", root=root)
            self.assertEqual([], list(root.iterdir()))


class PinnedSecondarySourceTests(unittest.TestCase):
    @unittest.skipUnless((ROOT / probe.BASE).exists(), "Immutable B15 archive not available")
    def test_exact_b15_pins_fallback_invariant_primary_body_and_unchanged_members(self):
        base = ROOT / probe.BASE
        self.assertEqual(probe.BASE_SHA256, probe.sha256(base.read_bytes()))
        original = probe.archive_members(base)
        for name, key in (("LICENSE", "license_sha256"), ("shaderlod-build.json", "baseline_metadata_sha256")):
            self.assertEqual(MANIFEST[key], probe.sha256(original[name]))
        off = probe.patch_members(original, MANIFEST, "disabled")
        on = probe.patch_members(original, MANIFEST, "enabled")
        for name in original.keys() - set(probe.MEMBERS):
            self.assertEqual(original[name], on[name], name)
        for name in probe.MEMBERS:
            self.assertEqual(original[name], select_probe(off[name], False), name)
        for member in MANIFEST["members"][:-1]:
            path = member["path"]
            active = select_probe(on[path], True).decode()
            before_body = body(original[path].decode(), "for (uint i = 0u; i < primary_steps; ++i)")
            after_body = body(active, "for (uint i = 0u; i < primary_steps; ++i)")
            for prefix, option in (("lighting", "LIGHTING"), ("ambient", "AMBIENT")):
                macro = "CLOUDS_" + Path(path).stem.upper()
                after_body = after_body.replace(
                    f"{macro}_{option}_STEPS,\n            {prefix}_extent_scale",
                    f"{macro}_{option}_STEPS", 1)
            self.assertEqual(before_body, after_body, path)
            # The entire original SHADERLOD_CLOUDS-off tail remains literal.
            baseline_source = original[path].decode()
            include_guard = baseline_source.splitlines()[2]
            tail_anchor = "\n#else\n" + include_guard + "\n"
            self.assertEqual(1, baseline_source.count(tail_anchor))
            self.assertTrue(active.endswith(baseline_source.split(tail_anchor, 1)[1]))


if __name__ == "__main__":
    unittest.main()
