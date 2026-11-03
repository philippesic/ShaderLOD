"""Private source/packaging checks; no runtime coverage or cost claim."""
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
    "ssr_coverage", ROOT / "scripts/build_complementary_ssr_coverage_cue.py"
)
cue = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cue)
BASE = ROOT / "dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip"
# Exact bytes are completely specified by the existing builder and manifest
# SHA; this restored sidecar is not evidence of a captured runtime option map.
OPTIONS = ROOT / "tests/fixtures/complementary-shadow-quality2.txt"
ELIGIBLE = {
    "FRAGMENT_SHADER": "1", "MC_OS_MAC": "1", "MC_VERSION": "12101", "RP_MODE": "1",
    "OVERWORLD": "1", "GBUFFERS_WATER": "1", "WORLD_SPACE_REFLECTIONS_INTERNAL": "-1",
    "WORLD_SPACE_REFLECTIONS": "-1", "WATER_REFLECT_QUALITY": "2", "WATER_STYLE": "1",
}


def preprocess(source: bytes, defines: dict[str, str]) -> str:
    compiler = shutil.which("clang") or shutil.which("cc")
    if compiler is None:
        raise unittest.SkipTest("C preprocessor unavailable; no conditional-source check")
    # Includes omitted intentionally: this checks conditional/source identity,
    # not complete shader parsing, actual Iris options or driver compilation.
    source = re.sub(r'^\s*#include\s+.*$', '', source.decode(), flags=re.MULTILINE)
    result = subprocess.run([compiler, "-E", "-P", "-x", "c",
                             *[f"-D{k}={v}" for k, v in defines.items()], "-"],
                            input=source, text=True, capture_output=True, check=True)
    return " ".join(result.stdout.split())


class ManifestTests(unittest.TestCase):
    def test_options_and_mode_fail_closed(self):
        manifest = json.loads(cue.MANIFEST.read_text())
        cue.validate_options(b"SHADOW_QUALITY=2\n", manifest)
        for data in (b"", b"SHADOW_QUALITY=2\r\n", b"SHADOW_QUALITY=1\n",
                     b"SHADOW_QUALITY=2\nWATER_REFLECT_QUALITY=1\n",
                     b"SHADOW_QUALITY=2\nSHADOW_QUALITY=2\n"):
            with self.subTest(data=data):
                with self.assertRaises(ValueError): cue.validate_options(data, manifest)
        with self.assertRaises(ValueError): cue.cue_block("remove-water-terrain")


@unittest.skipUnless(BASE.is_file() and OPTIONS.is_file(), "Exact private source/options absent")
class OriginalSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(cue.MANIFEST.read_text())
        cls.files = cue.read_archive(BASE)
        cls.program = cls.files[cue.PROGRAM]

    def test_full_original_member_archive_and_license_pins(self):
        self.assertEqual(cue.sha256(BASE.read_bytes()), self.manifest["original_archive_sha256"])
        self.assertEqual(cue.members_digest(self.files), self.manifest["original_members_digest"])
        for name, digest in self.manifest["source_members"].items():
            self.assertEqual(cue.sha256(self.files[name]), digest, name)
        self.assertEqual(cue.sha256(self.files["License.txt"]), self.manifest["license"]["sha256"])

    def test_only_final_water_insertion_reflection_member_and_other_bytes_unchanged(self):
        for mode in cue.MODES:
            patched = cue.apply(self.files, self.manifest, mode)
            self.assertEqual(set(patched), set(self.files))
            self.assertEqual([n for n in patched if patched[n] != self.files[n]], [cue.PROGRAM])
            self.assertEqual(patched[cue.REFLECTIONS], self.files[cue.REFLECTIONS])
            self.assertEqual(patched[cue.PROGRAM].replace(cue.cue_block(mode), b"", 1), self.program)
            body = patched[cue.PROGRAM]
            position = body.index(b"if (mat == 32000) {", body.index(b"// ShaderLOD SSR coverage"))
            self.assertLess(body.index(b"vec4 reflection = GetReflection("), position)
            self.assertLess(body.index(b"DoFog(color, skyFade"), position)
            self.assertLess(body.index(b"color.a *= fog;"), position)
            self.assertLess(position, body.index(b"gl_FragData[0] = color;"))
            self.assertEqual(body.count(b"GetReflection("), self.program.count(b"GetReflection("))

    def test_native_default_preprocesses_identically(self):
        native = cue.apply(self.files, self.manifest)[cue.PROGRAM]
        for definitions in (ELIGIBLE, {**ELIGIBLE, "WATER_STYLE": "3"},
                            {**ELIGIBLE, "DISTANT_HORIZONS": "1"}):
            self.assertEqual(preprocess(native, definitions), preprocess(self.program, definitions))

    def test_every_excluded_scope_preprocesses_identically(self):
        patched = cue.apply(self.files, self.manifest, "water-alpha-cue")[cue.PROGRAM]
        cases = []
        for key in ("MC_OS_MAC", "OVERWORLD", "GBUFFERS_WATER", "FRAGMENT_SHADER"):
            definitions = dict(ELIGIBLE); definitions.pop(key); cases.append(("missing " + key, definitions))
        for key in ("COMPOSITE", "GBUFFERS_COLORWHEEL_TRANSLUCENT", "DISTANT_HORIZONS",
                    "DH_WATER", "DH_TERRAIN", "VOXY", "VOXY_PATCH", "VOXY_TRANSLUCENT"):
            cases.append((key, {**ELIGIBLE, key: "1"}))
        for key, value in (("MC_VERSION", "12111"), ("MC_VERSION", "260100"),
                           ("RP_MODE", "0"), ("RP_MODE", "2"), ("RP_MODE", "3"),
                           ("WORLD_SPACE_REFLECTIONS_INTERNAL", "1"), ("WORLD_SPACE_REFLECTIONS", "1"),
                           ("WATER_REFLECT_QUALITY", "-1"), ("WATER_REFLECT_QUALITY", "0"),
                           ("WATER_REFLECT_QUALITY", "1"), ("WATER_REFLECT_QUALITY", "3")):
            cases.append((key + "=" + value, {**ELIGIBLE, key: value}))
        for label, definitions in cases:
            with self.subTest(scope=label):
                self.assertEqual(preprocess(patched, definitions), preprocess(self.program, definitions))

    def test_active_cue_dominates_final_output_only_for_native_water_id(self):
        source = cue.apply(self.files, self.manifest, "water-alpha-cue")[cue.PROGRAM]
        code = preprocess(source, ELIGIBLE)
        start = code.index("if (mat == 32000) {", code.index("float fogAlpha"))
        brace = code.index("{", start); cursor = brace + 1; depth = 1
        while depth:
            if code[cursor] == "{": depth += 1
            if code[cursor] == "}": depth -= 1
            cursor += 1
        block = code[start:cursor]
        self.assertIn("float shaderlodSsrNativeAlpha = reflection.a;", block)
        self.assertIn("shaderlodSsrNativeAlpha <= 0.001", block)
        self.assertIn("shaderlodSsrNativeAlpha > 0.001", block)
        self.assertIn("shaderlodSsrNativeAlpha <= 1.0", block)
        self.assertIn("vec3(1.0, 0.0, 1.0)", block)
        self.assertIn("color = vec4(shaderlodSsrCue, 1.0);", block)
        self.assertNotIn("texture", block)
        self.assertNotIn("gl_FragData", block)
        self.assertNotIn("reflection =", block)
        self.assertEqual(" ".join((code[:start] + code[cursor:]).split()), preprocess(self.program, ELIGIBLE))
        self.assertIn("block.32000=water flowing_water", self.files["shaders/block.properties"].decode())

    def test_native_alpha_is_not_changed_by_active_background_or_mirror(self):
        reflection = preprocess(self.files[cue.REFLECTIONS], ELIGIBLE)
        background = preprocess(self.files["shaders/lib/materials/materialMethods/reflectionBackground.glsl"], ELIGIBLE)
        self.assertIn("vec4 reflection = vec4(0.0);", reflection)
        self.assertIn("if (reflection.a > 0.001)", reflection)
        self.assertIn("reflection = vec4(texture2D(gaux2, refPos.xy).rgb, 1.0);", reflection)
        self.assertIn("reflection.a = reflectionPrevAlpha;", reflection)
        self.assertIn("reflection.a *= clamp(posDif + 3.0, 0.0, 1.0);", reflection)
        self.assertNotIn("screenPosRM", reflection)  # mirrored-image branch refused
        self.assertNotIn("getWSR", reflection + background)
        self.assertNotRegex(background, r"reflection\.a\s*(?:[+*/-]?=)")
        self.assertNotRegex(background, r"\breflection\s*=")
        self.assertIn("reflection.rgb = mix(skyReflection, reflection.rgb, reflection.a);", background)

    def test_mutation_addition_deletion_and_wrong_patch_pin_refused(self):
        for member in self.manifest["source_members"]:
            changed = dict(self.files); changed[member] += b"\n"
            with self.subTest(member=member):
                with self.assertRaises(ValueError): cue.apply(changed, self.manifest)
        for change in ("added", "removed"):
            changed = dict(self.files)
            if change == "added": changed["unreviewed.txt"] = b""
            else: changed.pop(cue.REFLECTIONS)
            with self.assertRaises(ValueError): cue.apply(changed, self.manifest)
        manifest = copy.deepcopy(self.manifest)
        manifest["patched_program_sha256"]["native"] = "0" * 64
        with self.assertRaises(ValueError): cue.apply(self.files, manifest)

    def test_deterministic_exclusive_builds_and_native_option_preservation(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            for mode in cue.MODES:
                one = cue.build(BASE, folder / (mode + ".zip"), OPTIONS, mode)
                two = cue.build(BASE, folder / (mode + "-2.zip"), OPTIONS, mode)
                self.assertEqual(one["output_sha256"], two["output_sha256"])
                self.assertEqual(Path(one["options_sidecar"]).read_bytes(), OPTIONS.read_bytes())
                result = cue.read_archive(Path(one["output"]))
                for member, data in self.files.items():
                    if member != cue.PROGRAM: self.assertEqual(result[member], data, member)
                self.assertEqual(set(result) - set(self.files), {
                    "shaderlod-private-ssr-coverage.json", "SHADERLOD_PRIVATE_SSR_COVERAGE.txt"
                })
                self.assertFalse(any(one["validation"].values()))
                with self.assertRaises(ValueError): cue.build(BASE, Path(one["output"]), OPTIONS, mode)

    def test_non_original_archive_and_altered_options_refused_before_write(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder); output = folder / "output.zip"; bad = folder / "bad.zip"
            bad.write_bytes(BASE.read_bytes() + b"changed")
            with self.assertRaises(ValueError): cue.build(bad, output, OPTIONS, "water-alpha-cue")
            self.assertFalse(output.exists())
            bad_options = folder / "options.txt"; bad_options.write_bytes(b"SHADOW_QUALITY=1\n")
            with self.assertRaises(ValueError): cue.build(BASE, output, bad_options, "water-alpha-cue")
            self.assertFalse(output.exists())
            with self.assertRaises(ValueError): cue.build(BASE, BASE, OPTIONS)


if __name__ == "__main__":
    unittest.main()
