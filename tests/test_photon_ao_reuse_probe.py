"""Pinned source and scalar reuse fixtures; no ZIP assembly, compiler or client."""
import copy
import hashlib
import json
import math
from pathlib import Path
import random
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import build_photon_ao_reuse_probe as probe

MANIFEST = json.loads((ROOT / probe.MANIFEST).read_text())


def select_probe(source: bytes, enabled: bool) -> bytes:
    """Resolve only this private option, preserving all upstream directives."""
    text = source.decode()
    declaration = MANIFEST["option_declaration"]
    text = text.replace(declaration, "", 1).replace(
        declaration.replace(f"//#define {probe.SWITCH}", f"#define {probe.SWITCH}"), "", 1)
    output, selected = [], None
    for line in text.splitlines(keepends=True):
        if line == f"#ifdef {probe.SWITCH}\n":
            if selected is not None:
                raise AssertionError("Nested probe directive")
            selected = enabled
        elif line == "#else\n" and selected is not None:
            selected = not selected
        elif line == f"#endif // {probe.SWITCH}\n":
            if selected is None:
                raise AssertionError("Unmatched probe directive")
            selected = None
        elif selected is None or selected:
            output.append(line)
    if selected is not None:
        raise AssertionError("Unclosed probe directive")
    return "".join(output).encode()


def function(source: str, name: str) -> str:
    start = source.index("float " + name + "(")
    body = source.index("{", start)
    depth = 1
    for end in range(body + 1, len(source)):
        depth += (source[end] == "{") - (source[end] == "}")
        if depth == 0:
            return source[start:end + 1]
    raise AssertionError("Unclosed function")


class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base = ROOT / probe.BASE
        if not base.exists():
            raise unittest.SkipTest("Pinned local Photon source not present")
        if probe.sha256(base.read_bytes()) != probe.BASE_SHA256:
            raise AssertionError("Pinned original Photon archive changed")
        cls.files = probe.archive_members(base)

    def test_disabled_preprocessing_restores_every_upstream_byte(self):
        for arm in probe.ARMS:
            patched = probe.patch_members(self.files, MANIFEST, arm)
            self.assertEqual(set(patched), set(self.files))
            for name, source in self.files.items():
                effective = select_probe(patched[name], False) if name in probe.MEMBERS else patched[name]
                self.assertEqual(source, effective, (arm, name))
            self.assertEqual(self.files["LICENSE"], patched["LICENSE"])

    def test_exact_original_kernel_retained_and_active_math_identical(self):
        original = function(self.files[probe.GTAO].decode(), "compute_maximum_horizon_angle")
        for arm in ("disabled", "enabled"):
            source = probe.patch_members(self.files, MANIFEST, arm)[probe.GTAO].decode()
            self.assertEqual(original, function(source, "compute_maximum_horizon_angle"))
            active = function(source, "shaderlod_maximum_horizon_angle_reuse")
            normalized = active.replace("shaderlod_maximum_horizon_angle_reuse(", "compute_maximum_horizon_angle(", 1)
            self.assertEqual(1, normalized.count(MANIFEST["cache_state"]))
            self.assertEqual(1, normalized.count(MANIFEST["cache_fetch"]))
            normalized = normalized.replace(MANIFEST["cache_state"], "", 1).replace(
                MANIFEST["cache_fetch"], MANIFEST["original_fetch"], 1)
            self.assertEqual(original, normalized)

    def test_gate_and_fetch_precede_cache_use_without_derivative_changes(self):
        patched = probe.patch_members(self.files, MANIFEST, "enabled")[probe.GTAO].decode()
        wrapper = function(patched, "shaderlod_maximum_horizon_angle")
        self.assertIn("if (shaderlodBridgeVersion == 2 && shaderlodEnabled == 1)", wrapper)
        self.assertIn("return compute_maximum_horizon_angle(", wrapper)
        active = function(patched, "shaderlod_maximum_horizon_angle_reuse")
        self.assertIn("all(lessThan(texel, depth_texture_size))", active)
        self.assertIn("all(greaterThanEqual(texel, ivec2(0)))", active)
        self.assertIn("} else {\n            depth = texelFetch(combined_depth_tex, texel, 0).x;", active)
        self.assertEqual(1, active.count("texelFetch("))
        self.assertNotIn("dFdx", active)
        self.assertNotIn("dFdy", active)
        self.assertNotIn("shaderlodNear", patched)

    def test_option_defaults_off_and_only_two_members_change(self):
        for arm in probe.ARMS:
            files = probe.patch_members(self.files, MANIFEST, arm)
            changed = sorted(name for name in files if files[name] != self.files[name])
            self.assertEqual([] if arm == "original" else sorted(probe.MEMBERS), changed)
            if arm != "original":
                declaration = f"{'#define' if arm == 'enabled' else '//#define'} {probe.SWITCH}\n".encode()
                self.assertEqual(1, files[probe.SETTINGS].count(declaration))

    def test_changed_archive_member_and_license_fail_closed(self):
        for name in (*probe.MEMBERS, "LICENSE"):
            files = self.files | {name: self.files[name] + b"\n"}
            with self.assertRaises(ValueError):
                probe.patch_members(files, MANIFEST, "original")

    def test_mutated_manifest_scope_anchors_and_active_source_fail_closed(self):
        mutations = [lambda m: m.update(base_sha256="bad"),
                     lambda m: m["members"].reverse(),
                     lambda m: m["members"][0]["replacements"].pop(),
                     lambda m: m["members"][0]["replacements"][0].update(before=""),
                     lambda m: m["members"][0]["replacements"][0].update(after="bad"),
                     lambda m: m["members"][1].update(enabled_sha256="bad")]
        for mutation in mutations:
            manifest = copy.deepcopy(MANIFEST)
            mutation(manifest)
            with self.assertRaises(ValueError):
                probe.patch_members(self.files, manifest, "enabled")

    def test_unknown_arm_and_output_scope_fail_closed_without_assembly(self):
        with self.assertRaises(ValueError):
            probe.patch_members(self.files, MANIFEST, "other")
        with self.assertRaises(ValueError):
            probe.build(output=ROOT / "Photon-should-not-be-written.zip")


def depth_sequence(texels, size, reuse):
    """Count pure valid fetches; undefined edge fetches vary to expose unsafe reuse."""
    calls, valid, previous, cached = 0, False, None, 0.0
    result = []
    for texel in texels:
        in_bounds = all(0 <= texel[axis] < size[axis] for axis in (0, 1))
        if reuse and in_bounds and valid and previous == texel:
            depth = cached
        else:
            calls += 1
            depth = ((texel[0] * 37 + texel[1] * 11) % 99) / 100 if in_bounds else -float(calls)
        # Undefined values intentionally cannot be compared across reduced fetch streams.
        result.append(depth if in_bounds else None)
        valid, previous, cached = in_bounds, texel, depth
    return result, calls


class ReuseMathTests(unittest.TestCase):
    def test_consecutive_duplicates_save_fetches_without_dropping_samples(self):
        texels = [(2, 3)] * 4 + [(3, 3)] * 3 + [(4, 3)]
        original, original_calls = depth_sequence(texels, (10, 10), False)
        active, active_calls = depth_sequence(texels, (10, 10), True)
        self.assertEqual(original, active)
        self.assertEqual((8, 3), (original_calls, active_calls))

    def test_undefined_edges_never_hit_cache_and_clear_previous_cache(self):
        texels = [(2, 2), (-1, 2), (-1, 2), (2, 2), (10, 2), (10, 2)]
        self.assertEqual(depth_sequence(texels, (10, 10), False), depth_sequence(texels, (10, 10), True))

    def test_random_projected_rays_preserve_depth_and_fractional_horizon_inputs(self):
        rng = random.Random(9033)
        for _ in range(400):
            start = (rng.uniform(0, 64), rng.uniform(0, 64))
            step = (rng.uniform(-2, 2), rng.uniform(-2, 2))
            rays = [(start[0] + i * step[0], start[1] + i * step[1]) for i in range(32)]
            texels = [(int(max(0, min(1, x / 64)) * 64 - 0.5),
                       int(max(0, min(1, y / 64)) * 64 - 0.5)) for x, y in rays]
            full, calls = depth_sequence(texels, (64, 64), False)
            reused, reuse_calls = depth_sequence(texels, (64, 64), True)
            self.assertEqual(full, reused)
            self.assertLessEqual(reuse_calls, calls)
            # Every fractional ray position survives, including repeated depth texels.
            def horizon(depths):
                values = []
                for (x, y), depth in zip(rays, depths):
                    if depth in (None, 0.0):
                        continue
                    offset = (x * depth - start[0], y * depth - start[1], 1 + depth)
                    length = math.sqrt(sum(c * c for c in offset))
                    falloff = min(1, max(0, (length - 30) / 10))
                    cosine = offset[2] / length
                    values.append(cosine * (1 - falloff) - falloff)
                return max(values, default=-1)
            self.assertEqual(horizon(full), horizon(reused))

    def test_repeated_texel_is_not_proof_of_redundant_horizon_iteration(self):
        # Same depth at two fractional positions still gives different cosine/falloff.
        depth = 0.5
        first = (0.1 * depth, 0.0, 1.0)
        second = (0.9 * depth, 0.0, 1.0)
        cosine = lambda offset: offset[2] / math.sqrt(sum(c * c for c in offset))
        self.assertNotEqual(cosine(first), cosine(second))


if __name__ == "__main__":
    unittest.main()
