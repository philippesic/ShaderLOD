"""Verify pinned packaging and compile the actual patched fragment programs."""
import importlib.util
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("shaderpack_builder", ROOT / "scripts/build_shaderpack.py")
BUILDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILDER)


class ShaderpackTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        cls.output = Path(cls.directory.name) / "Photon-ShaderLOD.zip"
        cls.metadata = BUILDER.build(cls.output)
        with zipfile.ZipFile(cls.output) as archive:
            cls.files = {name: archive.read(name) for name in archive.namelist()}
        cls.manifest = json.loads((ROOT / "patches/photon/v1.3b.json").read_text())
        with zipfile.ZipFile(ROOT / "dev/upstream/photon_v1.3b.zip") as archive:
            cls.upstream = {entry.filename: archive.read(entry.filename) for entry in archive.infolist() if not entry.is_dir()}

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    def test_reproducible_archive(self):
        second = Path(self.directory.name) / "second.zip"
        BUILDER.build(second)
        self.assertEqual(self.output.read_bytes(), second.read_bytes())

    def test_upstream_preserved_outside_explicit_targets(self):
        targets = {patch["path"] for patch in self.manifest["patches"]}
        changed = {name for name, data in self.upstream.items() if data != self.files[name]}
        self.assertEqual(targets, changed)
        for name in self.upstream.keys() - targets:
            with self.subTest(path=name):
                self.assertEqual(self.upstream[name], self.files[name])
        self.assertTrue(any("/dh_" in name for name in self.upstream))
        self.assertEqual(self.files["LICENSE"], self.upstream["LICENSE"])

    def test_corrupt_base_rejected_without_output(self):
        damaged = Path(self.directory.name) / "damaged.zip"
        damaged.write_bytes(b"not the pinned Photon archive")
        output = Path(self.directory.name) / "should-not-exist.zip"
        with self.assertRaisesRegex(ValueError, "mismatch"):
            BUILDER.build(output, damaged)
        self.assertFalse(output.exists())

    def test_modified_upstream_source_rejected(self):
        files = self.upstream.copy()
        target = self.manifest["patches"][0]["path"]
        files[target] += b"\n// upstream changed\n"
        with self.assertRaisesRegex(ValueError, "source changed"):
            BUILDER.apply_patches(files, self.manifest)

    def test_missing_and_ambiguous_anchors_rejected(self):
        for anchor in ("this anchor does not exist", "#"):
            manifest = json.loads(json.dumps(self.manifest))
            manifest["patches"][0]["before"] = anchor
            with self.subTest(anchor=anchor), self.assertRaisesRegex(ValueError, "exactly once"):
                BUILDER.apply_patches(self.upstream, manifest)

    def flatten(self, name, options):
        source = self.files[name].decode()
        if name == "shaders/settings.glsl":
            for macro, enabled in options.items():
                pattern = rf"^[ \t]*(?://)?#define {macro}(?=[ \t]|$)[^\n]*"
                match = re.search(pattern, source, flags=re.M)
                self.assertIsNotNone(match, macro)
                source = source[:match.start()] + (f"\n#define {macro}" if enabled else f"\n//#define {macro}") + source[match.end():]
        return re.sub(
            r'^\s*#include "([^\"]+)"\s*$',
            lambda match: self.flatten("shaders" + match.group(1) if match.group(1).startswith("/") else str(PurePosixPath(name).parent / match.group(1)), options),
            source,
            flags=re.M,
        )

    @unittest.skipUnless(shutil.which("glslangValidator"), "glslangValidator unavailable; driver compilation still required")
    def test_actual_fragment_programs_compile(self):
        # Iris expands shaderpack includes and joins preprocessor continuations.
        # This verifies GLSL syntax/types; it does not emulate Iris or GPU behavior.
        variants = (
            ("default", {}, ""),
            ("dh", {}, "#define DISTANT_HORIZONS\n"),
            ("debug", {"SHADERLOD_DEBUG": True}, ""),
            ("standalone", {"SHADERLOD_USE_MOD_CONFIG": False}, ""),
            ("no_shadow", {"SHADOW": False}, ""),
            ("no_pcf", {"SHADOW_PCF": False}, ""),
            ("no_color", {"SHADOW_COLOR": False}, ""),
            ("no_vps", {"SHADOW_VPS": False}, ""),
        )
        for world in ("world0", "world1", "world-1"):
            for program in ("deferred4", "gbuffers_water"):
                for variant, options, extra in variants:
                    with self.subTest(world=world, program=program, variant=variant):
                        source = self.flatten(f"shaders/{world}/{program}.fsh", options)
                        defines = "#define MC_VERSION 12101\n#define MC_SHADOW_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n#define IS_IRIS\n#define IRIS_FEATURE_SEPARATE_ENTITY_DRAWS\n" + extra
                        source = source.replace("#version 400 compatibility", "#version 400 compatibility\n" + defines, 1).replace("\\\n", "")
                        path = Path(self.directory.name) / f"{world}-{program}-{variant}.fsh"
                        path.write_text(source)
                        result = subprocess.run(["glslangValidator", "-S", "frag", str(path)], capture_output=True, text=True)
                        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
