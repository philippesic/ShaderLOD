"""Pinned-source and raster-coordinate counterexamples, not driver acceptance."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import build_complementary_raster_r1 as raster


class RasterContractTests(unittest.TestCase):
    def base(self, pack="reimagined"):
        path = ROOT / f"dependencies/upstream/other-pack-research/Complementary{pack.title()}_r5.9.3.zip"
        if not path.exists():
            self.skipTest("Pinned original private archive unavailable")
        return path

    def test_actual_both_original_pins_and_native_consumers(self):
        for pack in ("reimagined", "unbound"):
            result = raster.audit(self.base(pack), pack="complementary-" + pack)
            self.assertEqual(result["member_count"], 436)
            self.assertTrue(result["native_c3_preserved"])
            self.assertFalse(result["actual_mod_consuming_viewport_bridge"])

    def test_native_control_is_entire_original_archive_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "native.zip"
            result = raster.build(self.base(), output, pack="complementary-reimagined")
            self.assertEqual(output.read_bytes(), self.base().read_bytes())
            self.assertEqual(result["modified_upstream_members"], [])
            self.assertFalse(result["active"])

    def test_activation_failure_leaves_no_candidate(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "active.zip"
            with self.assertRaisesRegex(ValueError, "renderer/mip ownership"):
                raster.build(self.base(), output, pack="complementary-reimagined", activate=True)
            self.assertFalse(output.exists())

    def test_archive_identity_change_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            changed = Path(tmp) / "changed.zip"
            changed.write_bytes(self.base().read_bytes() + b"changed")
            with self.assertRaisesRegex(ValueError, "original archive changed"):
                raster.audit(changed, pack="complementary-reimagined")

    def test_rounded_extents_use_two_axis_actual_ratio(self):
        extent = raster.active_extent(1921, 1201)
        self.assertEqual(extent, (1440, 900))
        uv = raster.current_uv((0.5, 0.5), extent, (1921, 1201))
        self.assertEqual(uv, (720 / 1921, 450 / 1201))
        self.assertNotEqual(uv[0], 0.5 * raster.SCALE)

    def test_current_history_domain_counterexample(self):
        previous_screen_uv = (0.75, 0.25)
        current_address = raster.current_uv(previous_screen_uv, (1440, 900), (1920, 1200))
        self.assertEqual(current_address, (0.5625, 0.1875))
        # Native colortex2 remains full-size: current mapping moves its history
        # lookup by 360 x 75 output texels in this concrete case.
        self.assertEqual(tuple((a - b) * n for a, b, n in
                              zip(previous_screen_uv, current_address, (1920, 1200))), (360.0, 75.0))

    def test_base_clamp_does_not_repair_mip_content(self):
        witness = raster.mip_witness()
        self.assertEqual(witness["native_full_allocation_mip"], [[1.0, 0.5], [0.5, 0.25]])
        self.assertEqual(witness["separate_active_source_mip"], [[1.0, 1.0], [1.0, 1.0]])
        self.assertEqual(witness["base_uv_at_last_active_texel"], (383.5 / 512, 383.5 / 512))
        self.assertFalse(witness["driver_observed"])

    def test_end_singleton_needs_explicit_address_transfer(self):
        output, active = (1920, 1200), raster.active_extent(1920, 1200)
        native_singleton = (output[0] - 1, output[1] - 1)
        scaled_singleton = (active[0] - 1, active[1] - 1)
        self.assertEqual(native_singleton, (1919, 1199))
        self.assertEqual(scaled_singleton, (1439, 899))
        self.assertTrue(any(n >= a for n, a in zip(native_singleton, active)))
        # Full-size history c5 retains the old scalar outside current validity.
        # Clearing or remapping unrelated samplers does not relocate this write.

    def test_disabled_native_quality_and_taa_off_fallback_explicit(self):
        manifest = json.loads(raster.MANIFEST.read_bytes())
        self.assertIn("Off", manifest["policy"]["baseline"])
        self.assertIn("Quality", manifest["policy"]["baseline"])
        self.assertIn("TAA off", manifest["policy"]["baseline"])
        self.assertEqual(manifest["policy"]["presets"], [0, 1, 2])

    def test_private_candidate_retains_c3_and_all_native_mrt_sizes(self):
        manifest, native, _, _ = raster.validate(self.base(), "complementary-reimagined")
        candidate = raster.apply(native, manifest, option=True)
        for name in ("shaders/program/composite3.glsl", "shaders/world0/composite3.fsh", "shaders/world0/composite3.vsh"):
            self.assertEqual(native[name], candidate[name])
        self.assertTrue(candidate["shaders/shaders.properties"].startswith(native["shaders/shaders.properties"]))
        added_properties = candidate["shaders/shaders.properties"][len(native["shaders/shaders.properties"]):]
        self.assertEqual(re.findall(rb"size.buffer.colortex(\d+)", added_properties), [b"9", b"10"])
        self.assertEqual(candidate["License.txt"], native["License.txt"])
        self.assertIn(b"RENDERTARGETS: 9", candidate["shaders/program/composite2.glsl"])
        self.assertIn(b"RENDERTARGETS: 10", candidate["shaders/program/deferred2.glsl"])

    @unittest.skipUnless(shutil.which("glslangValidator"), "GLSL parser unavailable")
    def test_helper_stages_and_analytic_bloom_gradients(self):
        helper = (ROOT / "patches/complementary-raster-r1/raster.glsl").read_text()
        self.assertNotIn("dFdx(", helper)
        self.assertNotIn("dFdy(", helper)
        header = """#version 130
uniform float viewWidth, viewHeight;
#define SHADERLOD_RASTER_R1 1
#define OVERWORLD
#define TAA
"""
        with tempfile.TemporaryDirectory() as tmp:
            for stage, macro, entry in (("frag", "FRAGMENT_SHADER", "gl_FragColor=vec4(1.0);"),
                                        ("vert", "VERTEX_SHADER", "gl_Position=vec4(1.0);")):
                for scope in ("COMPOSITE4", "COMPOSITE", "GBUFFERS_TERRAIN", "COMPOSITE6"):
                    source = Path(tmp) / (scope + "." + stage)
                    source.write_text(header + "#define " + macro + "\n#define " + scope + "\n" + helper + "\nvoid main(){" + entry + "}\n")
                    result = subprocess.run(["glslangValidator", "-S", stage, str(source)], text=True, capture_output=True)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    @unittest.skipUnless(shutil.which("glslangValidator"), "GLSL preprocessor unavailable")
    def test_option_off_native_program_token_parity(self):
        manifest, native, _, _ = raster.validate(self.base(), "complementary-reimagined")
        candidate = raster.apply(native, manifest, option=False)
        def expanded(files, name, stack=()):
            self.assertNotIn(name, stack, "Include cycle")
            source = files[name].decode()
            return re.sub(r'^\s*#include\s+"(/[^"]+)"',
                          lambda m: expanded(files, "shaders" + m.group(1), stack + (name,)), source, flags=re.M)
        with tempfile.TemporaryDirectory() as tmp:
            for program in ("gbuffers_terrain", "gbuffers_water", "gbuffers_hand", "deferred1", "composite", "composite1", "composite3", "composite4", "composite5", "composite6", "composite7", "final"):
                outputs = []
                for files in (native, candidate):
                    path = Path(tmp) / "source.frag"
                    source = expanded(files, "shaders/world0/" + program + ".fsh")
                    source = source.replace("#version 130", "#version 330 compatibility\n#define MC_VERSION 12101\n#define MC_OS_MAC\n#define IS_IRIS\n#define IRIS_VERSION 10812\n")
                    path.write_text(source)
                    result = subprocess.run(["glslangValidator", "-S", "frag", "-E", str(path)], text=True, capture_output=True)
                    self.assertEqual(result.returncode, 0, program + ": " + result.stdout + result.stderr)
                    outputs.append(re.findall(r'[A-Za-z_]\w*|\d+(?:\.\d*)?|[^\s]', result.stdout))
                self.assertEqual(*outputs, program + " option-Off changed native tokens")


if __name__ == "__main__":
    unittest.main()
