"""Source and packaging checks; never driver, pixel, lifecycle or speed evidence."""
from __future__ import annotations

import itertools
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import build_mip_liveness_candidate as candidate


def preprocess(source, defines):
    compiler = shutil.which("clang")
    if compiler is None:
        raise unittest.SkipTest("clang source preprocessor unavailable")
    source = re.sub(r"^\s*#(?:version|extension)\b.*$", "", source, flags=re.M)
    prefix = "".join(f"#define {key} {value}\n" for key, value in defines.items())
    return subprocess.run([compiler, "-E", "-P", "-x", "c", "-", "-Wno-invalid-pp-token"],
                          input=prefix + source, text=True, capture_output=True, check=True).stdout


class MipLivenessTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(candidate.MANIFEST.read_text())
        cls.base = ROOT / "dependencies/upstream/other-pack-research/BSL_v10.1.8.zip"
        cls.options = ROOT / "src/main/resources/assets/shaderlod/bslquad/native384.json"
        cls.iris = ROOT / "dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar"
        if not all(path.exists() for path in (cls.base, cls.options, cls.iris)):
            raise unittest.SkipTest("Private pinned source/runtime inputs unavailable")
        cls.files = candidate.read_archive(cls.base)
        # Same complete native map and cryptographic identities as the source
        # certificate; no historical capture receipt or loaded-runtime claim.
        cls.values = json.loads(cls.options.read_text())

    def expand(self, files, member, switches=None, stack=()):
        self.assertNotIn(member, stack)
        source = files[member].decode()
        if member == "shaders/lib/settings.glsl":
            for name, value in (switches or {}).items():
                pattern = rf"^\s*(?://)?#define {name}(?:\s+[^\n]*)?$"
                # Only named boolean definitions are toggled for these cases.
                source, count = re.subn(pattern, f"{'#define' if value else '//#define'} {name}",
                                        source, flags=re.M)
                self.assertEqual(count, 1, name)
        return re.sub(r'^\s*#include "(/[^\"]+)"\s*$',
                      lambda m: self.expand(files, "shaders" + m[1], switches, (*stack, member)),
                      source, flags=re.M)

    def test_guard_fallbacks(self):
        defines = {"IS_IRIS": 1, "IRIS_VERSION": 10812, "MC_VERSION": 12101,
                   "OVERWORLD": 1, "TAA": 1, "LIGHT_SHAFT": 1}
        self.assertIn("= false", preprocess(candidate.patch(True).decode(), defines))
        self.assertIn("= true", preprocess(candidate.patch(False).decode(), defines))
        for missing in defines:
            with self.subTest(missing=missing):
                self.assertIn("= true", preprocess(candidate.patch(True).decode(),
                                                   {k: v for k, v in defines.items() if k != missing}))
        for key, value in (("IRIS_VERSION", 10813), ("MC_VERSION", 12111), ("RETRO_FILTER", 1)):
            self.assertIn("= true", preprocess(candidate.patch(True).decode(), {**defines, key: value}))

    def test_complete_map_required(self):
        candidate.read_options(self.options, None, self.manifest)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "options.json"
            changed = dict(self.values); changed["TAA_MODE"] = "1"
            typed = dict(self.values); typed["TAA"] = True
            sparse = {k: self.values[k] for k in ("LIGHT_SHAFT", "TAA", "FXAA")}
            for value in ({}, sparse, changed, typed, {"loadedIrisOptions": self.values}):
                path.write_text(json.dumps(value))
                with self.assertRaises(ValueError):
                    candidate.read_options(path, None, self.manifest)

    def test_all_members_properties_and_attribution_preserved(self):
        result = candidate.apply(self.files, self.manifest, True)
        self.assertEqual(set(self.files), set(result))
        self.assertEqual([n for n in self.files if result[n] != self.files[n]], [candidate.MEMBER])
        self.assertEqual(result[candidate.MEMBER].split(b"//Settings//")[0],
                         self.files[candidate.MEMBER].split(b"//Settings//")[0])
        self.assertEqual(result["shaders/shaders.properties"], self.files["shaders/shaders.properties"])
        self.assertEqual(result["shaders/program/composite6.glsl"], self.files["shaders/program/composite6.glsl"])

    def test_source_mutation_and_new_consumer_refused(self):
        for name in ("shaders/program/final.glsl", "shaders/lib/antialiasing/taa.glsl",
                     "shaders/lib/settings.glsl", "shaders/shaders.properties"):
            changed = dict(self.files); changed[name] += b"\n// changed\n"
            with self.assertRaises(ValueError):
                candidate.apply(changed, self.manifest)
        changed = dict(self.files); changed["shaders/world0/composite8.fsh"] = b"new consumer"
        with self.assertRaises(ValueError):
            candidate.apply(changed, self.manifest)

    def test_all_dimension_c7_and_final_equivalence_with_dh_pbr(self):
        changed = candidate.apply(self.files, self.manifest, True)
        for dimension, stage, dh, pbr in itertools.product(("world0", "world-1", "world1"),
                                                         ("fsh", "vsh"), (False, True), (False, True)):
            defines = {"IS_IRIS": 1, "IRIS_VERSION": 10812, "MC_VERSION": 12101}
            if dh:
                defines["DISTANT_HORIZONS"] = 1
            switches = {"ADVANCED_MATERIALS": pbr}
            member = f"shaders/{dimension}/composite7.{stage}"
            old = preprocess(self.expand(self.files, member, switches), defines)
            new = preprocess(self.expand(changed, member, switches), defines)
            expected = old.replace("colortex1MipmapEnabled = true", "colortex1MipmapEnabled = false") \
                if dimension == "world0" and stage == "fsh" else old
            self.assertEqual(new, expected, (member, dh, pbr))
        # Final sources, including its entire sharpen/chroma/retro path, are byte-identical.
        self.assertEqual(self.files["shaders/program/final.glsl"], changed["shaders/program/final.glsl"])

    def test_native_option_fallbacks(self):
        changed = candidate.apply(self.files, self.manifest, True)
        for switches in ({"LIGHT_SHAFT": False}, {"SHADOW": False}, {"TAA": False}, {"RETRO_FILTER": True}):
            defines = {"IS_IRIS": 1, "IRIS_VERSION": 10812, "MC_VERSION": 12101}
            member = "shaders/world0/composite7.fsh"
            self.assertEqual(preprocess(self.expand(self.files, member, switches), defines),
                             preprocess(self.expand(changed, member, switches), defines))

    def test_every_post_c7_rt1_read_is_explicit_lod_zero(self):
        # Pinning the full source prevents unseen consumers. This extracts balanced
        # calls rather than accepting a line containing a coincidental final zero.
        members = (candidate.MEMBER, "shaders/lib/antialiasing/taa.glsl", "shaders/program/final.glsl")
        calls = []
        for member in members:
            text = re.sub(r"/\*.*?\*/|//[^\n]*", "", self.files[member].decode(), flags=re.S)
            for match in re.finditer(r"\b(texture\w*|texelFetch\w*)\s*\(\s*colortex1\s*,", text):
                start = text.index("(", match.start()); depth = 1; end = start + 1
                while depth:
                    depth += (text[end] == "(") - (text[end] == ")"); end += 1
                call = text[start + 1:end - 1]
                depth = 0; last = 0; args = []
                for index, char in enumerate(call):
                    depth += (char == "(") - (char == ")")
                    if char == "," and depth == 0:
                        args.append(call[last:index].strip()); last = index + 1
                args.append(call[last:].strip())
                self.assertEqual(match[1], "texture2DLod", member)
                self.assertEqual(len(args), 3, member)
                self.assertEqual(float(args[-1]), 0.0, member)
                calls.append((member, call))
        self.assertEqual(len(calls), 12)

    def test_exact_iris_pins_and_private_build_guards(self):
        pin = self.manifest["iris_source_certificate"]
        self.assertEqual(candidate.sha256(self.iris.read_bytes()), pin["jar_sha256"])
        with zipfile.ZipFile(self.iris) as archive:
            for cls in pin["classes"].values():
                self.assertEqual(candidate.sha256(archive.read(cls["member"])), cls["class_sha256"])
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            # Build a source-only default-off artifact. The historical runtime
            # receipt is unavailable, so do not claim private activation coverage.
            kwargs = dict(iris=self.iris)
            one = candidate.build(self.base, directory / "a.zip", **kwargs)
            two = candidate.build(self.base, directory / "b.zip", **kwargs)
            self.assertEqual(one["output_sha256"], two["output_sha256"])
            self.assertFalse(one["validation"]["exact_pixel_parity"])
            for target in (self.base, directory / "a.zip"):
                with self.assertRaises(ValueError):
                    candidate.build(self.base, target, **kwargs)
            with self.assertRaises(ValueError):
                candidate.build(self.base, directory / "c.zip", activate_private=True)
            bad = directory / "iris.jar"; bad.write_bytes(b"wrong jar")
            with self.assertRaises(ValueError):
                candidate.build(self.base, directory / "d.zip", iris=bad)


if __name__ == "__main__":
    unittest.main()
