"""G04 pinned source/dataflow, fallback, archive and GLSL syntax checks only."""
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("g04", ROOT / "scripts/build_makeup_unused_sky.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
BASE = Path(os.environ.get("G04_NATIVE", str(ROOT / "dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip")))
OPTIONS = ROOT / "dependencies/shortlist-test-inputs/g04/native100.txt"
MACROS = {"IS_IRIS": 1, "IRIS_VERSION": 10812, "MC_VERSION": 12101, "MC_OS_MAC": 1}


def preprocess(files, entry, option=1, macros=None, native_options=None):
    def expand(name, stack=()):
        if name in stack:
            raise ValueError("Include cycle")
        # Iris resolves includes by lines, even when an included file has no
        # final newline. Do not concatenate its last directive with the caller.
        return re.sub(r'^[ \t]*#include[ \t]+"(/[^\"]+)"[ \t]*$',
                      lambda p: expand("shaders" + p[1], stack + (name,)) + "\n",
                      files[name].decode(), flags=re.M)
    source = expand(entry)
    overrides = dict(native_options or {}, **{m.OPTION: option})
    for key, value in overrides.items():
        source = re.sub(r'(^\s*#define\s+' + key + r')\s+[^\n]*',
                        lambda p: p[1] + " " + str(value), source, flags=re.M)
    source = re.sub(r'^\s*#(?:version|extension).*$', '', source, flags=re.M)
    result = subprocess.run(["clang", "-E", "-P", "-x", "c"] +
                            [f"-D{k}={v}" for k, v in (MACROS if macros is None else macros).items()] + ["-"],
                            input=source, text=True, capture_output=True)
    if result.returncode:
        raise AssertionError(result.stderr)
    return result.stdout


def normalized(source):
    return re.sub(r"\s+", " ", source).strip()


class UnusedSkyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.native = m.read_archive(BASE)
        cls.candidate = m.patch(cls.native)
        cls.cue = m.patch(cls.native, True)

    def test_actual_native_def_use_and_pure_cloud_contract(self):
        src = self.native[m.CALLER].decode()
        start, end = src.index(m.START), src.index(m.END)
        prefix, suffix = src[:start], src[end:]
        self.assertNotIn("skyColorReflect", prefix)
        self.assertEqual(suffix.count("skyColorReflect"), 3)
        self.assertIn("if(blockType > 2.5)", suffix)
        self.assertIn("if(blockType > 1.5)", suffix)
        self.assertIn("// Otros translúcidos", suffix)
        vertex = self.native["shaders/common/water_blocks_vertex.glsl"].decode()
        self.assertIn("blockType = 0.0;", vertex)
        self.assertEqual(re.findall(r'blockType = ([0-9.]+);', vertex), ["0.0", "3.0", "2.0"])
        cloud = self.native["shaders/lib/volumetric_clouds.glsl"].decode()
        self.assertNotIn("inout", cloud)
        self.assertNotIn("discard", cloud)
        # All shader writes in the removed region are to the unused local;
        # get_cloud has value parameters and local-only assignments.
        self.assertEqual(src[start:end].count("get_cloud("), 1)
        self.assertEqual(self.candidate["shaders/lib/volumetric_clouds.glsl"], cloud.encode())
        # Honey is an actual unclassified translucent vanilla material; ice,
        # slime and stained glass are expressly native class 2, never skipped.
        blocks = self.native["shaders/block.properties"].decode()
        self.assertNotIn("honey_block", blocks)
        self.assertRegex(blocks, r'block\.10079 = ice slime_block white_stained_glass')

    def test_only_exact_zero_and_native_region_unchanged(self):
        src = self.candidate[m.CALLER].decode()
        native = self.native[m.CALLER].decode()
        restored = src.replace(m.OPEN, "").replace(m.CLOSE, "")
        self.assertEqual(restored, native)
        self.assertIn("if (blockType == 0.0)", src)
        self.assertEqual(src[src.index(m.END):], native[native.index(m.END):])
        self.assertEqual(self.candidate["shaders/common/deferred_fragment.glsl"],
                         self.native["shaders/common/deferred_fragment.glsl"])

    def test_only_one_new_discoverable_define_and_native_profiles_preserved(self):
        defines = []
        for name in (m.CALLER, m.CONFIG):
            defines += re.findall(r'^\s*#define\s+(SHADERLOD_\w+)', self.candidate[name].decode(), re.M)
        self.assertEqual(defines, [m.OPTION])
        native_profiles = [l for l in self.native[m.PROPERTIES].decode().splitlines() if l.startswith('profile.')]
        candidate_profiles = [l for l in self.candidate[m.PROPERTIES].decode().splitlines() if l.startswith('profile.')]
        self.assertEqual(candidate_profiles, native_profiles)

    def test_off_and_all_other_native_programs_are_token_identical(self):
        entries = ["shaders/world0/gbuffers_water.fsh", "shaders/world0/gbuffers_water.vsh",
                   "shaders/world0/gbuffers_hand_water.fsh", "shaders/world0/deferred.fsh",
                   "shaders/world0/prepare.fsh", "shaders/world-1/gbuffers_water.fsh",
                   "shaders/world1/gbuffers_water.fsh", "shaders/gbuffers_water.fsh"]
        for entry in entries:
            with self.subTest(entry=entry):
                self.assertEqual(normalized(preprocess(self.candidate, entry, 0)),
                                 normalized(preprocess(self.native, entry)))
                if entry != entries[0]:
                    self.assertEqual(normalized(preprocess(self.candidate, entry, 1)),
                                     normalized(preprocess(self.native, entry)))

    def test_unsupported_version_platform_dh_voxy_and_options_are_native(self):
        cases = []
        for key in ("IS_IRIS", "MC_OS_MAC"):
            macros = dict(MACROS); del macros[key]
            cases.append((macros, {}))
        for key, value in (("IRIS_VERSION", 10811), ("MC_VERSION", 12102),
                           ("DISTANT_HORIZONS", 1), ("VOXY", 1), ("VOXY_PATCH", 1)):
            cases.append((dict(MACROS, **{key: value}), {}))
        for key, value in (("V_CLOUDS", 0), ("V_CLOUDS", 2), ("CLOUD_VOL_STYLE", 1),
                           ("CLOUD_STEPS_AVG", 16), ("AA_TYPE", 0), ("AA_TYPE", 3)):
            cases.append((MACROS, {key: value}))
        for macros, opts in cases:
            with self.subTest(macros=macros, options=opts):
                self.assertEqual(normalized(preprocess(self.candidate, "shaders/world0/gbuffers_water.fsh", 1, macros, opts)),
                                 normalized(preprocess(self.native, "shaders/world0/gbuffers_water.fsh", 1, macros, opts)))

    def test_windows_active_and_linux_native(self):
        windows = dict(MACROS); del windows["MC_OS_MAC"]; windows["MC_OS_WINDOWS"] = 1
        entry = "shaders/world0/gbuffers_water.fsh"
        self.assertIn("if (blockType == 0.0)", preprocess(self.candidate, entry, macros=windows))
        linux = dict(windows); del linux["MC_OS_WINDOWS"]; linux["MC_OS_LINUX"] = 1
        self.assertEqual(normalized(preprocess(self.candidate, entry, macros=linux)),
                         normalized(preprocess(self.native, entry, macros=linux)))

    def test_active_guard_and_cue_are_real_skip_branch(self):
        src = preprocess(self.candidate, "shaders/world0/gbuffers_water.fsh")
        self.assertIn("if (blockType == 0.0)", src)
        self.assertLess(src.index("if (blockType == 0.0)"), src.index("skyColorReflect = get_cloud("))
        self.assertNotIn("shaderlodG04Skipped", src)
        cue = self.cue[m.CALLER].decode()
        self.assertIn("skyColorReflect = vec3(0.0);\n" + m.CUE_SET, cue)
        self.assertEqual(cue.replace(m.CUE_INIT, "").replace(m.CUE_SET, "").replace(m.CUE_PAINT, ""),
                         self.candidate[m.CALLER].decode())
        self.assertLess(cue.index('#include "/src/finalcolor.glsl"'), cue.index(m.CUE_PAINT))
        self.assertLess(cue.index(m.CUE_PAINT), cue.index('#include "/src/writebuffers.glsl"'))

    def test_archive_determinism_options_ancestry_and_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            outputs = [directory / "one.zip", directory / "two.zip"]
            for out in outputs:
                receipt = m.build(BASE, OPTIONS, out)
                self.assertEqual(receipt["changed_members"], sorted([m.CALLER, m.CONFIG, m.PROPERTIES]))
                self.assertTrue(Path(str(out) + ".txt").read_bytes().startswith(OPTIONS.read_bytes()))
                with zipfile.ZipFile(out) as z:
                    self.assertEqual(z.read("LICENSE"), self.native["LICENSE"])
                    self.assertIn(b"default OFF", z.read(m.CONFIG))
                    for name in self.native:
                        if name not in receipt["changed_members"]:
                            self.assertEqual(z.read(name), self.native[name], name)
            self.assertEqual(outputs[0].read_bytes(), outputs[1].read_bytes())
            with self.assertRaises(ValueError):
                m.build(BASE, OPTIONS, outputs[0])
            bad = directory / "bad.zip"; bad.write_bytes(b"wrong family")
            with self.assertRaisesRegex(ValueError, "Exact native"):
                m.build(bad, OPTIONS, directory / "rejected.zip")
            bad_opts = directory / "bad.txt"; bad_opts.write_text(m.OPTION + "=1\n")
            with self.assertRaisesRegex(ValueError, "original native sidecar"):
                m.build(BASE, bad_opts, directory / "rejected.zip")
            self.assertFalse((directory / "rejected.zip").exists())

    def test_glsl_syntax_on_actual_expanded_programs(self):
        with tempfile.TemporaryDirectory() as directory:
            for name, files, option in (("native", self.native, 0), ("control", self.candidate, 0),
                                        ("on", self.candidate, 1), ("cue", self.cue, 1)):
                for stage, entry in (("frag", "shaders/world0/gbuffers_water.fsh"),
                                     ("vert", "shaders/world0/gbuffers_water.vsh")):
                    path = Path(directory) / (name + "." + stage)
                    path.write_text("#version 120\n#extension GL_ARB_shader_texture_lod : enable\n" + preprocess(files, entry, option))
                    run = subprocess.run(["glslangValidator", "-S", stage, str(path)],
                                         capture_output=True, text=True)
                    self.assertEqual(run.returncode, 0, name + ": " + run.stdout + run.stderr)


if __name__ == "__main__":
    unittest.main()
