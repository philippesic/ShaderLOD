"""Source/packaging checks only: no claim of driver, graph or pixel validation."""
from __future__ import annotations

import hashlib
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
import build_complementary_pass_liveness as candidate


class PreprocessorMixin:
    def preprocess(self, source, defines):
        compiler = shutil.which("clang")
        if compiler is None:
            self.skipTest("clang C preprocessor unavailable; this is not an Iris test")
        # GLSL versions and plain properties comments are not C directives.
        source = re.sub(r"^\s*#(?:version|extension)\b.*$", "", source, flags=re.M)
        source = re.sub(r"^\s*# (?!if|endif).*$", "", source, flags=re.M)
        prefix = "".join(f"#define {key} {value}\n" for key, value in defines.items())
        result = subprocess.run([compiler, "-E", "-P", "-x", "c", "-",
                                 "-Wno-invalid-pp-token", "-Wno-macro-redefined"],
                                input=prefix + source, text=True, capture_output=True, check=True)
        return result.stdout


class PropertyGateTests(PreprocessorMixin, unittest.TestCase):
    def test_runtime_guard_matrix(self):
        base = {"IS_IRIS": 1, "IRIS_VERSION": 10812, "MC_VERSION": 12101, "WORLD_BLUR": 0, "MOTION_BLUR_EFFECT": -1}
        for dimension, blur in itertools.product(candidate.DIMENSIONS, (0, 1, 2, -1, 3)):
            source = candidate.properties_patch(True, dimension).decode()
            output = self.preprocess(source, {**base, "WORLD_BLUR": blur})
            self.assertEqual(".enabled=false" in output, blur == 0)
            if blur == 0:
                self.assertEqual(output.strip(), f"program.{dimension}/composite3.enabled=false")
        source = candidate.properties_patch(True).decode()
        for missing in base:
            self.assertNotIn(".enabled=false", self.preprocess(source, {k: v for k, v in base.items() if k != missing}))
        for key, value in (("IRIS_VERSION", 10813), ("IRIS_VERSION", 10811), ("MC_VERSION", 12111),
                           ("MOTION_BLUR_EFFECT", 1), ("MOTION_BLUR_EFFECT", 0)):
            self.assertNotIn(".enabled=false", self.preprocess(source, {**base, key: value}))
        self.assertNotIn(".enabled=false", self.preprocess(candidate.properties_patch().decode(), base))

    def test_unknown_dimension_refused(self):
        with self.assertRaisesRegex(ValueError, "Unknown dimension"):
            candidate.properties_patch(True, "world42")

    def test_options_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "options.json"
            for options in ({}, {"WORLD_BLUR": 1}, {"WORLD_BLUR": False}, {"OTHER": "0"}, {"WORLD_BLUR": "0", "X": []}):
                path.write_text(json.dumps(options))
                with self.assertRaises(ValueError):
                    candidate.read_effective_options(path)
            for value in (None, 0, 1, True, "unknown"):
                path.write_text(json.dumps({"WORLD_BLUR": "0", "MOTION_BLUR_EFFECT": value}))
                with self.assertRaises(ValueError):
                    candidate.read_effective_options(path)
            path.write_text(json.dumps({"original": {"WORLD_BLUR": "0", "BLOOM_ENABLED": "1", "MOTION_BLUR_EFFECT": "-1"}}))
            with self.assertRaises(ValueError):
                candidate.read_effective_options(path)
            result = candidate.read_effective_options(path, "original")
            self.assertEqual(result["file_sha256"], hashlib.sha256(path.read_bytes()).hexdigest())
            self.assertEqual(result["value_count"], 3)


class PinnedSourceTests(PreprocessorMixin, unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(candidate.MANIFEST.read_text())
        cls.archives = {}
        for pack, name in (("complementary-reimagined", "ComplementaryReimagined_r5.9.3.zip"),
                           ("complementary-unbound", "ComplementaryUnbound_r5.9.3.zip")):
            path = ROOT / "dependencies/upstream/other-pack-research" / name
            if not path.exists():
                raise unittest.SkipTest("Private pinned original archives unavailable")
            cls.archives[pack] = path
        cls.files = {pack: candidate.read_archive(path) for pack, path in cls.archives.items()}
        cls.iris = ROOT / "dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar"

    def expand(self, files, member, options, stack=()):
        if member in stack:
            raise AssertionError("Recursive include: " + member)
        source = files[member].decode()
        if member == "shaders/lib/common.glsl":
            for key, value in options.items():
                source, count = re.subn(rf"(^\s*#define {key} )[^\s/]+", rf"\g<1>{value}", source, flags=re.M)
                self.assertEqual(count, 1, key)
        return re.sub(r'^\s*#include "(/[^\"]+)"\s*$',
                      lambda match: self.expand(files, "shaders" + match[1], options, (*stack, member)),
                      source, flags=re.M)

    def test_zero_blur_all_dimension_sources_and_bloom_dh_gates(self):
        for pack, dimension, bloom, dh in itertools.product(self.files, candidate.DIMENSIONS, (-1, 1), (False, True)):
            files = self.files[pack]
            source = self.expand(files, f"shaders/{dimension}/composite3.fsh", {"WORLD_BLUR": 0, "BLOOM_ENABLED": bloom})
            defines = {"IS_IRIS": 1, "IRIS_VERSION": 10812, "MC_VERSION": 12101, "MC_OS_MAC": 1}
            if dh:
                defines["DISTANT_HORIZONS"] = 1
            output = self.preprocess(source, defines)
            body = re.search(r"void main\(\)\s*\{(.*?)\}", output, re.S)[1]
            self.assertEqual(re.sub(r"\s+", " ", body).strip(),
                             "vec3 color = texelFetch(colortex0, texelCoord, 0).rgb; gl_FragData[0] = vec4(color, 1.0);")
            self.assertNotIn("colortex0MipmapEnabled", output)
            self.assertNotIn("DoWorldBlur", output)
            self.assertNotIn("GetBloomFog", body)
            # Bloom remains in composite1 at blur=0 when its native gates enable it.
            output1 = self.preprocess(self.expand(files, f"shaders/{dimension}/composite1.fsh",
                                                   {"WORLD_BLUR": 0, "BLOOM_ENABLED": bloom}), defines)
            self.assertEqual("color *= GetBloomFog" in output1,
                             bloom != -1 and dimension != "world1",
                             (pack, dimension, bloom, dh))

    def test_native_blur_preserves_work(self):
        for pack, dimension, blur in itertools.product(self.files, candidate.DIMENSIONS, (1, 2)):
            output = self.preprocess(self.expand(self.files[pack], f"shaders/{dimension}/composite3.fsh", {"WORLD_BLUR": blur}),
                                     {"IS_IRIS": 1, "MC_VERSION": 12101, "MC_OS_MAC": 1})
            self.assertIn("DoWorldBlur(color, z1, lViewPos)", output)
            self.assertIn("colortex0MipmapEnabled = true", output)

    def test_properties_only_and_licenses_identical(self):
        for pack, files in self.files.items():
            for dimension in candidate.DIMENSIONS:
                changed = candidate.apply(files, self.manifest, pack, active=True, dimension=dimension)
                self.assertEqual(set(changed), set(files))
                self.assertEqual([n for n in files if files[n] != changed[n]], [candidate.PROPERTIES])
                self.assertEqual(changed["License.txt"], files["License.txt"])
                self.assertTrue(changed[candidate.PROPERTIES].startswith(files[candidate.PROPERTIES]))

    def test_mutated_source_license_and_added_compute_refused(self):
        pack = "complementary-reimagined"
        for member in ("License.txt", "shaders/lib/pipelineSettings.glsl", "shaders/program/composite3.glsl", candidate.PROPERTIES):
            changed = self.files[pack].copy()
            changed[member] += b"\nchanged"
            with self.assertRaises(ValueError):
                candidate.apply(changed, self.manifest, pack)
        changed = self.files[pack].copy()
        changed["shaders/world0/composite3.csh"] = b"void main() {}"
        with self.assertRaises(ValueError):
            candidate.apply(changed, self.manifest, pack)

    def test_exact_iris_class_pins(self):
        if not self.iris.exists():
            self.skipTest("Pinned Iris JAR unavailable")
        pin = self.manifest["iris_source_certificate"]
        self.assertEqual(candidate.sha256(self.iris.read_bytes()), pin["jar_sha256"])
        with zipfile.ZipFile(self.iris) as archive:
            for member, digest in pin["classes_sha256"].items():
                self.assertEqual(candidate.sha256(archive.read(member)), digest, member)

    def test_build_guards_and_determinism(self):
        if not self.iris.exists():
            self.skipTest("Pinned Iris JAR unavailable")
        pack = "complementary-reimagined"
        retained = ROOT / "src/main/resources/assets/shaderlod/reimaginedquad/native398.json"
        if not retained.exists():
            self.skipTest("Retained full effective option snapshot unavailable")
        with tempfile.TemporaryDirectory() as temp:
            temp = Path(temp)
            options = temp / "options.json"
            options.write_text(json.dumps(json.loads(retained.read_text())))
            kwargs = dict(pack=pack, iris=self.iris, options=options, activate_private=True)
            a = candidate.build(self.archives[pack], temp / "a.zip", **kwargs)
            b = candidate.build(self.archives[pack], temp / "b.zip", **kwargs)
            self.assertEqual(a["output_sha256"], b["output_sha256"])
            self.assertFalse(a["validation"]["exact_pixel_parity"])
            self.assertFalse(a["validation"]["rendered"])
            with self.assertRaisesRegex(ValueError, "Output already exists"):
                candidate.build(self.archives[pack], temp / "a.zip", **kwargs)
            with self.assertRaisesRegex(ValueError, "Cannot overwrite"):
                candidate.build(self.archives[pack], self.archives[pack], **kwargs)
            with self.assertRaisesRegex(ValueError, "requires exact"):
                candidate.build(self.archives[pack], temp / "c.zip", pack=pack, activate_private=True)
            options.write_text('{"WORLD_BLUR":"0","MOTION_BLUR_EFFECT":"-1"}')
            with self.assertRaisesRegex(ValueError, "incomplete effective option inventory"):
                candidate.build(self.archives[pack], temp / "c.zip", **kwargs)
            bad_iris = temp / "unknown.jar"
            bad_iris.write_bytes(b"unknown")
            with self.assertRaisesRegex(ValueError, "Uncertified Iris"):
                candidate.build(self.archives[pack], temp / "c.zip", pack=pack, iris=bad_iris)
            with zipfile.ZipFile(temp / "repacked.zip", "w") as archive:
                for name, data in self.files[pack].items():
                    archive.writestr(name, data)
            with self.assertRaisesRegex(ValueError, "Pinned original archive"):
                candidate.build(temp / "repacked.zip", temp / "c.zip", pack=pack)
            self.assertFalse((temp / "c.zip").exists())


if __name__ == "__main__":
    unittest.main()
