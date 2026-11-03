"""Source/provenance checks, not Minecraft/driver/quality/performance evidence."""
from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "ssr_diagnostic", ROOT / "scripts/build_complementary_ssr_diagnostic.py"
)
builder = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(builder)
BASE = ROOT / "dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip"
# Exact bytes are completely specified by the existing builder and manifest
# SHA; this restored sidecar is not evidence of a captured runtime option map.
OPTIONS = ROOT / "tests/fixtures/complementary-shadow-quality2.txt"


def preprocess(source: bytes, definitions: dict[str, str]) -> str:
    """Real C preprocessor checks only the isolated GLSL conditional structure.

    Include bodies are omitted, so this is intentionally not a GLSL compile or
    an Iris option discovery test. All original/patched operands are identical.
    """
    compiler = shutil.which("clang") or shutil.which("cc")
    if compiler is None:
        raise unittest.SkipTest("A C preprocessor is required for independent conditional checks")
    text = re.sub(r'^\s*#include\s+.*$', '', source.decode(), flags=re.MULTILINE)
    command = [compiler, "-E", "-P", "-x", "c", *[f"-D{k}={v}" for k, v in definitions.items()], "-"]
    result = subprocess.run(command, input=text, text=True, capture_output=True, check=True)
    return " ".join(result.stdout.split())


ELIGIBLE = {
    "MC_OS_MAC": "1", "MC_VERSION": "12101", "OVERWORLD": "1",
    "GBUFFERS_WATER": "1", "WORLD_SPACE_REFLECTIONS_INTERNAL": "-1",
    "WATER_REFLECT_QUALITY": "2", "WORLD_SPACE_REF_MODE": "2", "WATER_STYLE": "1", "RP_MODE": "1",
}


class SourceOnlyTests(unittest.TestCase):
    def test_unknown_mode_and_ambiguous_boundaries_refused(self):
        minimal = builder.START + b"ray-body\r\n" + builder.END
        for source, mode in ((minimal, "unknown"), (minimal * 2, "native"),
                             (builder.END + builder.START, "native"), (b"", "native")):
            with self.subTest(mode=mode, source=source):
                with self.assertRaises(ValueError):
                    builder.patch_reflections(source, mode)

    def test_options_fail_closed(self):
        manifest = json.loads(builder.MANIFEST.read_text())
        builder.validate_options(b"SHADOW_QUALITY=2\n", manifest)
        for options in (b"", b"SHADOW_QUALITY=2\r\n", b"SHADOW_QUALITY=1\n",
                        b"SHADOW_QUALITY=2\nWATER_REFLECT_QUALITY=1\n",
                        b"SHADOW_QUALITY=2\nSHADOW_QUALITY=2\n",
                        b"# comment\nSHADOW_QUALITY=2\n"):
            with self.subTest(options=options):
                with self.assertRaises(ValueError):
                    builder.validate_options(options, manifest)


@unittest.skipUnless(BASE.is_file() and OPTIONS.is_file(), "Exact private source/options unavailable")
class PinnedArchiveTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(builder.MANIFEST.read_text())
        cls.original = builder.read_archive(BASE)
        cls.reflection = cls.original[builder.REFLECTIONS]

    def test_original_full_archive_members_license_and_options_pins(self):
        self.assertEqual(builder.sha256(BASE.read_bytes()), self.manifest["original_archive_sha256"])
        self.assertEqual(builder.members_digest(self.original), self.manifest["original_members_digest"])
        for name, expected in self.manifest["source_members"].items():
            self.assertEqual(builder.sha256(self.original[name]), expected, name)
        builder.validate_options(OPTIONS.read_bytes(), self.manifest)
        self.assertEqual(self.manifest["license"]["sha256"], builder.sha256(self.original["License.txt"]))

    def test_source_or_license_mutation_addition_and_deletion_refused(self):
        for name in self.manifest["source_members"]:
            with self.subTest(name=name):
                changed = dict(self.original)
                changed[name] += b"\n"
                with self.assertRaises(ValueError):
                    builder.apply(changed, self.manifest)
        for operation in ("added", "deleted"):
            changed = dict(self.original)
            if operation == "added":
                changed["unreviewed.glsl"] = b""
            else:
                changed.pop("License.txt")
            with self.assertRaises(ValueError):
                builder.apply(changed, self.manifest)

    def test_wrong_expected_patch_pin_refused(self):
        manifest = copy.deepcopy(self.manifest)
        manifest["patched_reflections_sha256"]["native"] = "0" * 64
        with self.assertRaises(ValueError):
            builder.apply(self.original, manifest)

    def test_only_trace_enclosure_changes_all_surviving_bytes_exact(self):
        for mode in builder.MODES:
            result = builder.apply(self.original, self.manifest, mode)
            self.assertEqual(set(result), set(self.original))
            self.assertEqual([n for n in result if result[n] != self.original[n]], [builder.REFLECTIONS])
            patched = result[builder.REFLECTIONS]
            # Remove the two added blocks; every native byte must be recovered.
            restored = re.sub(
                rb'            // ShaderLOD SSR diagnostic R1: baked[^\r]*\r\n'
                rb'            #if [^\r]*\r\n'
                rb'            if \(mat != 32000\) \{[^\r]*\r\n'
                rb'            #endif\r\n', b'', patched,
            )
            restored = re.sub(
                rb'            #if [^\r]*\r\n'
                rb'            \} // ShaderLOD SSR diagnostic R1:[^\r]*\r\n'
                rb'            #endif\r\n', b'', restored,
            )
            self.assertEqual(restored, self.reflection)
            # Scope starts after prepare and zero initialization, ends before
            # mirror/background and normalM side effect. No early return/reset.
            self.assertLess(patched.index(b"vec4 reflection = vec4(0.0);"), patched.index(b"if (mat != 32000)"))
            self.assertLess(patched.index(b"water keeps the native miss state"), patched.index(b"Method 2:"))
            self.assertEqual(patched.count(b"float refDist = far;"), 1)
            self.assertEqual(patched.count(b"refDist = dist;"), 1)
            self.assertEqual(patched.count(b"return reflection;"), self.reflection.count(b"return reflection;"))

    def test_native_control_preprocessed_identity_and_eligible_material_branch(self):
        native = builder.apply(self.original, self.manifest)[builder.REFLECTIONS]
        removal = builder.apply(self.original, self.manifest, "remove-water-terrain")[builder.REFLECTIONS]
        for water_style in ("1", "3"):
            definitions = {**ELIGIBLE, "WATER_STYLE": water_style, "GENERATED_NORMALS": "1"}
            expected = preprocess(self.reflection, definitions)
            self.assertEqual(preprocess(native, definitions), expected)
            candidate = preprocess(removal, definitions)
            self.assertEqual(candidate.count("if (mat != 32000)"), 1)
            branch_start = candidate.index("if (mat != 32000)")
            body_start = candidate.index("{", branch_start)
            balance, body_end = 1, body_start + 1
            while balance:
                if candidate[body_end] == "{": balance += 1
                if candidate[body_end] == "}": balance -= 1
                body_end += 1
            branch_body = candidate[body_start + 1:body_end - 1]
            self.assertIn("for (int i = 0; i < sampleCount; i++)", branch_body)
            self.assertIn("refDist = dist;", branch_body)
            self.assertIn("DoFog(reflection", branch_body)
            self.assertNotIn("AddBackgroundReflection", branch_body)
            self.assertNotIn("normalM = normalMR", branch_body)
            # Removing the single enclosure yields the full native token stream.
            unenclosed = candidate[:branch_start] + branch_body + candidate[body_end:]
            self.assertEqual(" ".join(unenclosed.split()), expected)

    def test_every_refused_scope_keeps_native_preprocessed_source(self):
        removal = builder.apply(self.original, self.manifest, "remove-water-terrain")[builder.REFLECTIONS]
        cases = []
        for macro in ("MC_OS_MAC", "OVERWORLD", "GBUFFERS_WATER"):
            definition = dict(ELIGIBLE); definition.pop(macro); cases.append(("absent " + macro, definition))
        for macro in ("COMPOSITE", "GBUFFERS_COLORWHEEL_TRANSLUCENT", "DISTANT_HORIZONS",
                      "DH_WATER", "DH_TERRAIN", "VOXY", "VOXY_PATCH", "VOXY_TRANSLUCENT"):
            cases.append((macro, {**ELIGIBLE, macro: "1"}))
        for macro, value in (("MC_VERSION", "12111"), ("MC_VERSION", "260100"),
                             ("RP_MODE", "0"), ("RP_MODE", "2"), ("RP_MODE", "3"),
                             ("WORLD_SPACE_REFLECTIONS_INTERNAL", "1"),
                             ("WATER_REFLECT_QUALITY", "1"), ("WATER_REFLECT_QUALITY", "0"),
                             ("WATER_REFLECT_QUALITY", "-1")):
            cases.append((macro + "=" + value, {**ELIGIBLE, macro: value}))
        for label, definitions in cases:
            with self.subTest(label=label):
                original = preprocess(self.reflection, definitions)
                self.assertEqual(preprocess(removal, definitions), original)
                self.assertNotIn("mat != 32000", original)

    def test_material_identity_call_and_composition_are_pinned(self):
        program = self.original["shaders/program/gbuffers_water.glsl"].decode()
        self.assertIn("flat in int mat;", program)
        self.assertIn("mat = int(mc_Entity.x + 0.5);", program)
        self.assertIn("block.32000=water flowing_water", self.original["shaders/block.properties"].decode())
        call = program.index("vec4 reflection = GetReflection(")
        self.assertLess(program.index("DoLighting(color, shadowMult"), call)
        self.assertLess(call, program.index("color.rgb = mix(color.rgb, reflection.rgb, fresnelM);"))
        self.assertLess(call, program.index("DoFog(color, skyFade"))
        self.assertIn("depthtex1, dither, skyLightFactor, fresnel", program)

    def test_build_is_reproducible_preserves_all_members_and_options(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            for mode in builder.MODES:
                one, two = folder / (mode + ".zip"), folder / (mode + "-2.zip")
                receipt = builder.build(BASE, one, OPTIONS, mode)
                other = builder.build(BASE, two, OPTIONS, mode)
                self.assertEqual(receipt["output_sha256"], other["output_sha256"])
                self.assertEqual(Path(receipt["options_sidecar"]).read_bytes(), OPTIONS.read_bytes())
                files = builder.read_archive(one)
                for name, data in self.original.items():
                    if name != builder.REFLECTIONS:
                        self.assertEqual(files[name], data, name)
                self.assertEqual(set(files) - set(self.original), {
                    "shaderlod-private-ssr-diagnostic.json", "SHADERLOD_PRIVATE_SSR_DIAGNOSTIC.txt"
                })
                self.assertFalse(any(receipt["validation"].values()))
                with self.assertRaises(ValueError):
                    builder.build(BASE, one, OPTIONS, mode)

    def test_bad_archive_and_options_refused_before_output_creation(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            bad_base, output, bad_options = folder / "base.zip", folder / "output.zip", folder / "options.txt"
            bad_base.write_bytes(BASE.read_bytes() + b"changed zip footer")
            with self.assertRaises(ValueError):
                builder.build(bad_base, output, OPTIONS)
            self.assertFalse(output.exists())
            bad_options.write_bytes(b"SHADOW_QUALITY=2\nWATER_REFLECT_QUALITY=1\n")
            with self.assertRaises(ValueError):
                builder.build(BASE, output, bad_options)
            self.assertFalse(output.exists())
            with self.assertRaises(ValueError):
                builder.build(BASE, BASE, OPTIONS)


if __name__ == "__main__":
    unittest.main()
