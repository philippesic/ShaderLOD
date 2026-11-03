"""Numerical/source/package checks; never starts a graphics context or client."""
import hashlib
import importlib.util
import itertools
import json
import math
import re
import subprocess
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("g07", ROOT / "scripts/build_g07_makeup_sampling.py")
g07 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g07)
BASE = (ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip')


class G07SamplingTests(unittest.TestCase):
    def test_native_entry_preprocessing_and_glsl120_leaf_parse(self):
        with zipfile.ZipFile(BASE) as archive:
            files = g07.transform({n: archive.read(n) for n in archive.namelist()})
        def expand(name):
            return re.sub(r'^\s*#include\s+"(/[^\"]+)"\s*$',
                lambda m: expand("shaders" + m[1]), files[name].decode(), flags=re.M)
        for entry, excluded in (("world0", None), ("world-1", "NETHER"), ("world1", "THE_END"), ("", "UNKNOWN_DIM")):
            for mode in (0, 1, 2):
                source = expand("shaders/" + (entry + "/" if entry else "") + "deferred.fsh")
                source = source.replace(f"#define {g07.OPTION} 0", f"#define {g07.OPTION} {mode}")
                source = re.sub(r'^\s*#(?:version|extension).*$', '', source, flags=re.M)
                args = ["clang", "-E", "-P", "-x", "c", "-DMC_VERSION=12101", "-DIRIS_VERSION=10812", "-"]
                result = subprocess.run(args, input=source, text=True, capture_output=True, check=True).stdout
                self.assertEqual("float g07TemporalAo" in result, mode == 2 and excluded is None)
        # Parse the authentic AO function in a minimal native GLSL120 environment.
        # This is syntax/type evidence only, not Iris transformation or a driver compile.
        body = files["shaders/lib/ao.glsl"].decode()
        header = """#version 120
#extension GL_ARB_shader_texture_lod : enable
#define MC_VERSION 12101
#define IRIS_VERSION 10812
#define DEFERRED_SHADER
#define AOSTEPS 5
#define AA_TYPE 2
#define AO 1
#define AO_STRENGTH 0.70
uniform sampler2D depthtex0;
uniform int frameCounter;
uniform float far, aspectRatioInverse, fovYInverse;
varying vec2 texcoord;
float ld(float d) { return d; }
"""
        with tempfile.TemporaryDirectory() as tmp:
            for mode, extra in ((0, ""), (1, ""), (2, ""), (2, "#define DISTANT_HORIZONS\n"), (2, "#define NETHER\n")):
                shader = Path(tmp) / "ao.frag"
                shader.write_text(header + f"#define {g07.OPTION} {mode}\n" + extra + body +
                    "\nvoid main() { gl_FragColor = vec4(dbao(0.5)); }\n")
                result = subprocess.run(["glslangValidator", "-S", "frag", str(shader)], text=True, capture_output=True)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_each_native_dither_phase_has_equal_stratum_coverage(self):
        coverage = [[0] * 5 for _ in range(16)]
        for frame in range(80):
            chosen = [j + (j >= frame % 5) for j in range(4)]
            self.assertEqual(len(set(chosen)), 4)
            self.assertTrue(all(0 <= i < 5 for i in chosen))
            for i in chosen:
                coverage[frame % 16][i] += 1
        self.assertEqual(coverage, [[4] * 5 for _ in range(16)])

    def test_pre_sqrt_normalization_but_do_not_claim_nonlinear_equivalence(self):
        # Extreme bounded pair contributions plus smooth/radial/edge distributions.
        worst_bias = 0
        for values in itertools.product((0.0, 1.0), repeat=5):
            native = sum(values) / 5
            arms = [(sum(values) - values[omit]) / 4 for omit in range(5)]
            self.assertAlmostEqual(sum(arms) / 5, native)
            self.assertTrue(all(0 <= a <= 1 for a in arms))
            mapped = sum(math.sqrt(.7 * a + .3) for a in arms) / 5
            target = math.sqrt(.7 * native + .3)
            self.assertLessEqual(mapped, target + 1e-14)  # Jensen bias is real.
            worst_bias = max(worst_bias, target - mapped)
        self.assertGreater(worst_bias, .001)
        self.assertLess(worst_bias, .01)

    def test_original_body_and_native_aa_graph_untouched(self):
        with zipfile.ZipFile(BASE) as archive:
            original = {n: archive.read(n) for n in archive.namelist()}
        patched = g07.transform(original)
        native = original["shaders/lib/ao.glsl"].replace(b"float dbao(float dither)", b"float g07NativeAo(float dither, float linearDepth)")
        native = native.replace(b"    float linearDepth = texture2DLod(depthtex0, texcoord.xy, 0.0).r;", b"")
        self.assertTrue(patched["shaders/lib/ao.glsl"].startswith(native))
        changed = {n for n in original if original[n] != patched[n]}
        self.assertEqual(changed, {"shaders/lib/ao.glsl", "shaders/lib/config.glsl", "shaders/shaders.properties"})
        source = patched["shaders/lib/ao.glsl"].decode()
        temporal = source.split("float g07TemporalAo", 1)[1].split("#endif", 1)[0]
        self.assertEqual(temporal.count("texture2DLod(depthtex0"), 2)  # shared center + two per iteration
        self.assertIn("j < 4", temporal)
        self.assertIn("ambientOcclusion *= 0.25", temporal)
        for gate in ("AOSTEPS == 5", "AA_TYPE == 2", "!defined DISTANT_HORIZONS", "!defined THE_END", "shaderlodHistoryReset == 0", "shaderlodPreset == 1"):
            self.assertIn(gate, source)

    def test_deterministic_zip_native_options_and_wrong_source_refusal(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = Path(tmp) / "a.zip", Path(tmp) / "b.zip"
            one, two = g07.build(BASE, a), g07.build(BASE, b)
            self.assertEqual(a.read_bytes(), b.read_bytes())
            java = (ROOT / "src/main/java/com/pp/shaderlod/optimization/g07/G07SamplingAdmission.java").read_text()
            with zipfile.ZipFile(BASE) as native, zipfile.ZipFile(a) as built:
                self.assertEqual(set(native.namelist()), set(built.namelist()))
                self.assertEqual(native.read("LICENSE"), built.read("LICENSE"))
                self.assertEqual(built.testzip(), None)
            for label, mode in (("off", "0"), ("control", "1"), ("on", "2")):
                options = dict(line.split("=", 1) for line in Path(str(a) + f".{label}.txt").read_text().splitlines())
                self.assertEqual(options.pop(g07.OPTION), mode)
                self.assertEqual(options, one["native_options"])
            wrong = Path(tmp) / "wrong.zip"; wrong.write_bytes(b"wrong version")
            with self.assertRaisesRegex(ValueError, "pinned"):
                g07.build(wrong, Path(tmp) / "no.zip")
            # Keep strict archive admission last so deterministic packaging,
            # native sidecars and wrong-input refusal are checked independently.
            pin=re.search(r'ARCHIVE_SHA = "([a-f0-9]{64})"',java).group(1)
            self.assertEqual(pin,one["sha256"],
                'Current deterministic G07 output differs from the admitted archive; '
                'accepted historical ZIP or a sealed member map is required before repinning')


if __name__ == "__main__":
    unittest.main()
