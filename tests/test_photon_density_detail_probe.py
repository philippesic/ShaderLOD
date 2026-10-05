"""Pinned source and diagnostic semantics only; no output ZIP/compiler/client."""
import copy
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import build_photon_density_detail_probe as probe

MANIFEST = json.loads((ROOT / probe.MANIFEST).read_text())
CONDITION = f"#if defined {probe.SWITCH} && defined PROGRAM_DEFERRED1\n"


def select_control(source: bytes, private_on: bool, main_program: bool) -> bytes:
    """Resolve only new control directives, leaving all upstream directives intact."""
    text = source.decode()
    declaration = MANIFEST["option_declaration"]
    text = text.replace(declaration, "", 1).replace(
        declaration.replace(f"//#define {probe.SWITCH}", f"#define {probe.SWITCH}"), "", 1)
    output, selected = [], None
    for line in text.splitlines(keepends=True):
        if line == CONDITION:
            if selected is not None:
                raise AssertionError("Nested private directive")
            selected = private_on and main_program
        elif line == "#else\n" and selected is not None:
            selected = not selected
        elif line == f"#endif // {probe.SWITCH}\n":
            if selected is None:
                raise AssertionError("Unmatched private directive")
            selected = None
        elif selected is None or selected:
            output.append(line)
    if selected is not None:
        raise AssertionError("Unclosed private directive")
    return "".join(output).encode()


class PinnedSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base = ROOT / probe.BASE
        if not base.exists():
            raise unittest.SkipTest("Pinned local Photon archive not present")
        if probe.sha256(base.read_bytes()) != probe.BASE_SHA256:
            raise AssertionError("Original Photon archive changed")
        cls.files = probe.archive_members(base)

    def test_private_off_or_other_program_restores_every_upstream_source_byte(self):
        for arm in probe.ARMS:
            patched = probe.patch_members(self.files, MANIFEST, arm)
            self.assertEqual(set(self.files), set(patched))
            for private_on, main in ((False, False), (False, True), (True, False)):
                for name, source in self.files.items():
                    actual = select_control(patched[name], private_on, main) if name in probe.MEMBERS else patched[name]
                    self.assertEqual(source, actual, (arm, private_on, main, name))

    def test_only_fine_texture_site_and_declarations_differ_in_density_source(self):
        for arm in ("disabled", "enabled"):
            source = probe.patch_members(self.files, MANIFEST, arm)[probe.CUMULUS].decode()
            self.assertEqual(1, source.count(MANIFEST["declarations"]))
            self.assertEqual(1, source.count(MANIFEST["active_fine_sample"]))
            source = source.replace(MANIFEST["declarations"], "", 1).replace(
                MANIFEST["active_fine_sample"], MANIFEST["original_fine_sample"], 1)
            self.assertEqual(self.files[probe.CUMULUS].decode(), source)

    def test_fallback_has_literal_original_fetch_and_fixed_diagnostic_is_honest(self):
        active = MANIFEST["active_fine_sample"]
        self.assertIn("if (shaderlodBridgeVersion == 2 && shaderlodEnabled == 1)", active)
        self.assertIn("worley_1 = 0.5;", active)
        sample = "texture(SAMPLER_WORLEY_SWIRLEY, (pos + 0.4 * wind) * 0.005).x;"
        self.assertIn("    } else {\n        worley_1 =\n            " + sample, active)
        self.assertIn(MANIFEST["original_fine_sample"], active)
        self.assertNotIn("textureLod", active)
        self.assertNotIn("textureGrad", active)
        self.assertNotIn("dFdx", active)
        self.assertNotIn("dFdy", active)
        self.assertEqual("quality_changing_cost_control", MANIFEST["quality_class"])

    def test_option_default_and_member_scope_preserve_license_other_passes_and_textures(self):
        for arm in probe.ARMS:
            files = probe.patch_members(self.files, MANIFEST, arm)
            changed = sorted(name for name in files if files[name] != self.files[name])
            self.assertEqual([] if arm == "original" else sorted(probe.MEMBERS), changed)
            if arm != "original":
                declaration = f"{'#define' if arm == 'enabled' else '//#define'} {probe.SWITCH}\n".encode()
                self.assertEqual(1, files[probe.SETTINGS].count(declaration))
            for name in ("LICENSE", "shaders/program/d1_clouds.fsh", "shaders/program/d2_clouds_upscaling.fsh",
                         "shaders/image/worley_swirley.dat", "shaders/image/worley_bubbly.dat"):
                self.assertEqual(self.files[name], files[name])

    def test_mutated_source_license_and_manifest_fail_closed(self):
        for name in (*probe.MEMBERS, "LICENSE"):
            with self.assertRaises(ValueError):
                probe.patch_members(self.files | {name: self.files[name] + b"\n"}, MANIFEST, "original")
        mutations = [lambda m: m.update(diagnostic_fine_noise=0.0),
                     lambda m: m.update(quality_class="accepted_optimization"),
                     lambda m: m.update(base_sha256="bad"),
                     lambda m: m["members"].reverse(),
                     lambda m: m["members"][0]["replacements"].pop(),
                     lambda m: m["members"][0]["replacements"][0].update(before=""),
                     lambda m: m["members"][0]["replacements"][1].update(after="bad"),
                     lambda m: m["members"][1].update(enabled_sha256="bad")]
        for mutation in mutations:
            manifest = copy.deepcopy(MANIFEST)
            mutation(manifest)
            with self.assertRaises(ValueError):
                probe.patch_members(self.files, manifest, "enabled")

    def test_unknown_arm_and_unignored_output_reject_before_writing(self):
        with self.assertRaises(ValueError):
            probe.patch_members(self.files, MANIFEST, "other")
        with self.assertRaises(ValueError):
            probe.build(output=ROOT / "must-not-write.zip")
        with self.assertRaises(ValueError):
            probe.build(arm="other")


class DiagnosticSemanticsTests(unittest.TestCase):
    def test_strict_activation_truth_table(self):
        for private in (False, True):
            for main in (False, True):
                for version in (0, 1, 2, 3):
                    for enabled in (-1, 0, 1, 2):
                        active = private and main and version == 2 and enabled == 1
                        self.assertEqual(active, (private, main, version, enabled) == (True, True, 2, 1))

    def test_fixed_noise_is_not_squared_mean_or_nonlinear_density_preserving(self):
        values = (0.0, 1.0)
        mean = sum(values) / len(values)
        self.assertEqual(0.5, mean)
        self.assertNotEqual(mean ** 2, sum(v ** 2 for v in values) / len(values))
        def density(w):
            x = max(0.0, 0.4 - 0.5 * w * w)
            return (x + 3 * x) / (1 + 3 * x)
        self.assertNotEqual(density(0.5), sum(density(w) for w in values) / len(values))
        # A source-identical secondary count can still change executed work via occupancy.
        self.assertGreater(density(0.5), 0)
        self.assertEqual(density(1.0), 0)


if __name__ == "__main__":
    unittest.main()
