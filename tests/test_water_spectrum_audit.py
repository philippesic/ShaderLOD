import importlib.util
import json
import math
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("water_spectrum_audit", ROOT / "scripts/build_water_spectrum_audit.py")
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


class WaterSpectrumPremiseTests(unittest.TestCase):
    def test_carrier_is_not_iteration_lacunarity(self):
        f = AUDIT.photon_carriers(3)
        self.assertAlmostEqual(f[2], 0.899111111111111)
        self.assertLess(f[2] / f[0], 1.29)
        # Allowed option values can make the carrier sequence decrease.
        self.assertLess(AUDIT.photon_carriers(2, lacunarity_option=0.5)[1], f[0])

    def test_native_shape_has_second_harmonic(self):
        for i in range(41):
            p = i * 0.173
            self.assertAlmostEqual(AUDIT.gerstner_shape(p), 3 / 8 + math.sin(p) / 2 - math.cos(2 * p) / 8)

    def test_noise_phase_gradient_is_required(self):
        w = AUDIT.phase_gradient_witness()
        self.assertAlmostEqual(w["full_chain_derivative"], w["central_difference_derivative"], places=7)
        self.assertGreater(abs(w["omitted_derivative"]), 3.9)
        self.assertGreater(abs(w["native_h_0_1_forward_slope"] - w["full_chain_derivative"]), 0.1)

    def test_equal_moments_do_not_fix_ggx_response(self):
        w = AUDIT.moments_witness()
        self.assertAlmostEqual(w["a"]["mean_slope"], w["b"]["mean_slope"])
        self.assertAlmostEqual(w["a"]["second_slope_moment"], w["b"]["second_slope_moment"])
        self.assertGreater(w["ndf_ratio_b_over_a"], 400)

    def make_pack(self, directory, entries):
        path = Path(directory) / "fixture.zip"
        with zipfile.ZipFile(path, "w") as z:
            for name, data in entries:
                z.writestr(name, data)
        return path, {"archive_sha256": AUDIT.sha256(path.read_bytes()),
                      "members": {name: AUDIT.sha256(data) for name, data in entries}}

    def test_archive_hash_fail_closed(self):
        with tempfile.TemporaryDirectory() as d:
            path, pin = self.make_pack(d, [("shaders/a.glsl", b"native")])
            pin["archive_sha256"] = "0" * 64
            with self.assertRaisesRegex(ValueError, "archive hash"):
                AUDIT.read_pinned_pack(path, pin)

    def test_member_hash_and_missing_fail_closed(self):
        with tempfile.TemporaryDirectory() as d:
            path, pin = self.make_pack(d, [("shaders/a.glsl", b"native")])
            pin["members"]["shaders/a.glsl"] = "0" * 64
            with self.assertRaisesRegex(ValueError, "member hash"):
                AUDIT.read_pinned_pack(path, pin)
            pin["members"] = {"shaders/missing.glsl": "0" * 64}
            with self.assertRaisesRegex(ValueError, "missing source"):
                AUDIT.read_pinned_pack(path, pin)

    def test_unsafe_and_duplicate_members_refused(self):
        with tempfile.TemporaryDirectory() as d:
            path, pin = self.make_pack(d, [("../a.glsl", b"native")])
            with self.assertRaisesRegex(ValueError, "unsafe"):
                AUDIT.read_pinned_pack(path, pin)
            import warnings
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                path, pin = self.make_pack(d, [("a", b"1"), ("a", b"2")])
            with self.assertRaisesRegex(ValueError, "duplicate"):
                AUDIT.read_pinned_pack(path, pin)

    def test_local_pinned_archives_and_consumer_inventory(self):
        manifest = json.loads(AUDIT.MANIFEST.read_text())
        for pack in manifest["packs"]:
            path = ROOT / pack["archive"]
            if not path.is_file():
                # Location may change; complete archive identity never does.
                path=next((p for p in (ROOT/'dependencies/upstream').rglob('*.zip')
                           if AUDIT.sha256(p.read_bytes())==pack['archive_sha256']),path)
            if not path.is_file():
                self.skipTest("Exact pinned archive unavailable: "+pack['pack'])
            members = AUDIT.read_pinned_pack(path, pack)
            inventory = AUDIT.source_inventory(members, pack["symbols"])
            self.assertTrue(inventory["symbol_occurrences_including_definitions_and_comments"])
            self.assertTrue(inventory["option_definitions_unpreprocessed"])
            if pack["pack"] == "photon":
                self.assertIn("shaders/program/c1_blend_layers.fsh", members)
            if pack["pack"] == "sildurs-vibrant-shaders":
                self.assertIn("shaders/deferred1.fsh", members)


if __name__ == "__main__":
    unittest.main()
