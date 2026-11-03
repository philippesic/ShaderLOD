"""Source/certificate/scalar checks only; no GLSL driver or timing claims."""
import copy
import importlib.util
import json
import math
from pathlib import Path
import random
import re
import struct
import subprocess
import tempfile
import unittest
from unittest.mock import patch as mock_patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("cloud_b16", ROOT / "scripts/build_cloud_volume_makeup_saturation.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def f32(x):
    return struct.unpack("f", struct.pack("f", x))[0]


def accumulate(opacities, densities, threshold, permission):
    """Native positive-add recurrence, not a texture/raster/GPU emulator."""
    value, density, first = 0.0, 0.0, True
    visited = []
    for i, opacity in enumerate(opacities):
        visited.append(i)
        if opacity > 0.0:
            value = f32(value + opacity)
            if first:
                first, density = False, densities[i]
        if permission == 1.0 and 0.0 < threshold < 1e30 and value >= threshold:
            break
    return (min(max(f32(value / threshold), 0.0), 1.0),
            min(max(density, 0.0001), 1.0)), visited


class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(m.MANIFEST.read_bytes())
        cls.files = m.read_members(ROOT / "dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip")
        cls.control = m.patch(cls.files, cls.manifest, "native-equivalent")
        cls.active = m.patch(cls.files, cls.manifest, "candidate")

    def test_original_archive_and_members_pinned(self):
        self.assertEqual(m.sha((ROOT / "dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip").read_bytes()), self.manifest["base_sha256"])
        for path, digest in self.manifest["source_members"].items():
            self.assertEqual(m.sha(self.files[path]), digest)

    def test_only_cloud_and_append_properties_change(self):
        self.assertEqual({k for k in self.files if self.files[k] != self.active[k]}, {m.CLOUD, m.PROPERTIES})
        self.assertTrue(self.active[m.PROPERTIES].startswith(self.files[m.PROPERTIES]))
        self.assertEqual(self.active[m.CLOUD], self.control[m.CLOUD])

    def test_original_callers_and_output_untouched(self):
        for path in ("shaders/common/water_blocks_fragment.glsl", "shaders/common/deferred_fragment.glsl"):
            self.assertEqual(self.files[path], self.active[path])
        original_tail = self.files[m.CLOUD].split(b"        cloud_value = clamp", 1)[1]
        self.assertEqual(original_tail, self.active[m.CLOUD].split(b"        cloud_value = clamp", 1)[1])
        s = self.active[m.CLOUD].decode()
        self.assertLess(s.index("density = (surface_sup"), s.index("if (shaderlodMakeupCloud"))
        self.assertLess(s.index("if (shaderlodMakeupCloud"), s.index("intersection_pos += increment;"))
        self.assertEqual(s.count("texture2DLod(gaux2"), 2)
        self.assertEqual(s.count("texture2D(gaux2"), 2)

    def test_scope_excludes_reflection_dh_other_dimensions(self):
        for required in ("defined DEFERRED_SHADER", "!defined DISTANT_HORIZONS", "!defined UNKNOWN_DIM",
                         "!defined THE_END", "!defined NETHER", "!defined VOXY", "IRIS_VERSION == 10812",
                         "MC_VERSION == 12101", "V_CLOUDS == 1", "CLOUD_VOL_STYLE == 0"):
            self.assertIn(required, m.GATE)
        self.assertIn("#extension GL_ARB_shader_texture_lod : enable", self.files["shaders/world0/deferred.fsh"].decode())

    def test_real_preprocessor_fallback_paths(self):
        source = self.active[m.CLOUD].decode().replace(
            "#define SHADERLOD_MAKEUP_CLOUD_SATURATION 0", "#define SHADERLOD_MAKEUP_CLOUD_SATURATION 1")
        # Apple's traditional cpp retains // inside macro values; GLSL removes
        # these lexical comments before directive expression evaluation.
        source = re.sub(r"//[^\n]*", "", source)
        defines = {"IS_IRIS": "1", "IRIS_VERSION": "10812", "MC_VERSION": "12101", "MC_OS_MAC": "1",
                   "DEFERRED_SHADER": "1", "V_CLOUDS": "1", "CLOUD_VOL_STYLE": "0", "CLOUD_STEPS_AVG": "10"}
        cases = [(defines, True)]
        for key in ("IS_IRIS", "IRIS_VERSION", "MC_VERSION", "MC_OS_MAC", "DEFERRED_SHADER"):
            variant = dict(defines)
            del variant[key]
            cases.append((variant, False))
        for key in ("UNKNOWN_DIM", "THE_END", "NETHER", "DISTANT_HORIZONS", "VOXY", "VOXY_PATCH"):
            cases.append((dict(defines, **{key: "1"}), False))
        for key, value in (("V_CLOUDS", "2"), ("CLOUD_VOL_STYLE", "1"), ("CLOUD_STEPS_AVG", "9"),
                           ("IRIS_VERSION", "11104"), ("MC_VERSION", "12111")):
            cases.append((dict(defines, **{key: value}), False))
        for macros, active in cases:
            result = subprocess.run(["clang", "-E", "-P", "-x", "c"] + [f"-D{k}={v}" for k, v in macros.items()] + ["-"],
                                    input=source, text=True, capture_output=True, check=True)
            self.assertEqual("texture2DLod(gaux2" in result.stdout, active)
            self.assertEqual("if (shaderlodMakeupCloud" in result.stdout, active)

    def test_mcmeta_filter_semantics(self):
        for style in ("natural", "blocky"):
            p = f"shaders/textures/clouds_{style}_512_R_8bit.png.mcmeta"
            self.assertEqual(json.loads(self.files[p])["texture"], {"blur": True, "clamp": False})

    def test_member_tamper_rejected(self):
        files = dict(self.files)
        files[m.CLOUD] += b"\n"
        with self.assertRaisesRegex(ValueError, "Pinned member"):
            m.patch(files, self.manifest, "candidate")

    def test_unapproved_runtime_fails_before_output(self):
        with tempfile.TemporaryDirectory() as d:
            output = Path(d) / "candidate.zip"
            unapproved = dict(self.manifest, approved_runtime_certificate_sha256=None)
            manifest_path = Path(d) / "unapproved.json"
            manifest_path.write_text(json.dumps(unapproved))
            with mock_patch.object(m, "MANIFEST", manifest_path):
                with self.assertRaisesRegex(ValueError, "not been approved"):
                    m.build(base=ROOT / "dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip", output=output, mode="candidate")
            self.assertFalse(output.exists())
            self.assertFalse(Path(str(output) + ".txt").exists())

    def test_real_world0_deferred_and_water_include_order(self):
        def expand(name, seen=()):
            self.assertNotIn(name, seen, "Unexpected include cycle")
            text = self.active[name].decode()
            return re.sub(r'^\s*#include\s+"(/[^\"]+)"\s*$',
                          lambda found: expand("shaders" + found[1], seen + (name,)), text, flags=re.M)
        for entry, expected in (("shaders/world0/deferred.fsh", True), ("shaders/world0/gbuffers_water.fsh", False)):
            text = expand(entry)
            text = text.replace("#define SHADERLOD_MAKEUP_CLOUD_SATURATION 0", "#define SHADERLOD_MAKEUP_CLOUD_SATURATION 1")
            text = re.sub(r'^\s*#(?:version|extension).*$', '', text, flags=re.M)
            # Modern C lexical preprocessing handles nested comment-looking
            # text inside // comments; regex block stripping cannot do that.
            result = subprocess.run(["clang", "-E", "-P", "-x", "c", "-DIS_IRIS=1", "-DIRIS_VERSION=10812", "-DMC_VERSION=12101", "-DMC_OS_MAC=1", "-"],
                                    input=text, text=True, capture_output=True, check=True)
            self.assertEqual("if (shaderlodMakeupCloud" in result.stdout, expected)
            self.assertEqual("texture2DLod(gaux2" in result.stdout, expected)
            if expected:
                self.assertEqual(result.stdout.count("texture2DLod(gaux2"), 1)

    def test_preview_has_no_deployable_archive(self):
        with tempfile.TemporaryDirectory() as d:
            output = Path(d) / "preview"
            result = m.build(base=ROOT / "dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip", output=output)
            self.assertEqual(result["status"], "SOURCE_PREVIEW_ONLY")
            self.assertFalse(list(output.rglob("*.zip")))
            with self.assertRaisesRegex(ValueError, "overwrite"):
                m.build(base=ROOT / "dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip", output=output)


class ScalarTests(unittest.TestCase):
    def test_first_dense_contact_keeps_first_density_and_native_prefix(self):
        native, _ = accumulate([0, 0.6, 0.6, 1, 1], [0, 0.125, 0.8, 0.9, 1], 1.0, 0)
        active, indices = accumulate([0, 0.6, 0.6, 1, 1], [0, 0.125, 0.8, 0.9, 1], 1.0, 1)
        self.assertEqual(active, native)
        self.assertEqual(active[1], 0.125)
        self.assertEqual(indices, [0, 1, 2])

    def test_thin_empty_late_contact_and_near_threshold(self):
        for opacities in ([0]*10, [0]*9+[0.25], [0.01]*10,
                          [f32(0.99999994)]+[0]*9, [1.0]+[0]*9):
            native, _ = accumulate(opacities, [0.25]*10, 1.0, 0)
            candidate, _ = accumulate(opacities, [0.25]*10, 1.0, 1)
            self.assertEqual(native, candidate)

    def test_finite_float32_sequences(self):
        random_source = random.Random(7102026)
        for _ in range(10000):
            opacities = [f32(random_source.choice([0, 0, 0, random_source.random()*80])) for _ in range(10)]
            densities = [f32(random_source.uniform(-0.2, 1.2)) for _ in range(10)]
            threshold = f32(10 ** random_source.uniform(-3, 5))
            original, full = accumulate(opacities, densities, threshold, 0)
            candidate, prefix = accumulate(opacities, densities, threshold, 1)
            self.assertEqual(original, candidate)
            self.assertEqual(prefix, full[:len(prefix)])

    def test_unknown_permission_and_unbounded_threshold_retain_traversal(self):
        for permission in (-1, 0, 0.5, math.nan, math.inf):
            _, indices = accumulate([2]*10, [0.5]*10, 1, permission)
            self.assertEqual(len(indices), 10)
        _, indices = accumulate([2]*10, [0.5]*10, math.inf, 1)
        self.assertEqual(len(indices), 10)


class CertificateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.dir = Path(self.temp.name)
        self.manifest = json.loads(m.MANIFEST.read_bytes())
        self.sidecar = self.dir / "native.txt"
        self.sidecar.write_text("# synthetic unit fixture, never runtime evidence\n")
        self.iris = self.dir / "iris.jar"
        self.iris.write_bytes(b"synthetic unit fixture")
        self.manifest["iris_sha256"] = m.sha(self.iris.read_bytes())
        self.manifest["native_sidecar_sha256"] = m.sha(self.sidecar.read_bytes())
        values = dict(self.manifest["required_options"])
        values["SYNTHETIC_COMPLETE_MAP"] = "test-only"
        self.manifest["native_options_count"] = len(values)
        self.manifest["native_options_map_sha256"] = m.sha(json.dumps(values, sort_keys=True, separators=(",", ":")).encode())
        source = self.dir / "synthetic-observation.txt"
        source.write_text("Synthetic unit test only: not accepted observation")
        ref = {"path": str(source), "sha256": m.sha(source.read_bytes())}
        state = {"minFilter": 9729, "magFilter": 9729, "wrapS": 10497, "wrapT": 10497,
                 "anisotropy": 1.0, "compareMode": 0, "minLod": -1000.0, "maxLod": 1000.0}
        texture = "shaders/textures/clouds_natural_512_R_8bit.png"
        self.c = {"schema": "shaderlod-makeup-cloud-b16-original-r3", "selectedArchiveSha256": self.manifest["base_sha256"],
                  "irisJarSha256": self.manifest["iris_sha256"], "minecraftVersion": "1.21.1", "dimension": "minecraft:overworld",
                  "irisFallback": False, "dhPresent": False, "voxyPresent": False, "loadedIrisOptions": values,
                  "draw": {"contextId": 1, "frameCounter": 1, "programId": 1, "programName": "world0/deferred", "gaux2TextureUnit": 2},
                  "sampledGaux2": {"contextId": 1, "frameCounter": 1, "programId": 1, "textureUnit": 2, "textureId": 1,
                                  "sourceMember": texture, "sourceSha256": self.manifest["source_members"][texture], "target": 3553,
                                  "width": 512, "height": 512, "level1Width": 0, "baseLevel": 0, "maxLevel": 1000,
                                  "internalFormat": 32856, "swizzleRGBA": [6403, 6404, 6405, 6406], "uploadSourceJoined": True,
                                  "samplerObject": 0, "textureState": state, "effectiveSamplerState": copy.deepcopy(state)},
                  "rawObservation": ref, "linkedFragment": ref, "linkedVertex": ref,
                  "originalSourceMemberSha256": self.manifest["source_members"][m.CLOUD], "explicitLodFragmentSupported": True}

    def validate(self):
        p = self.dir / "certificate.json"
        p.write_text(json.dumps(self.c))
        self.manifest["approved_runtime_certificate_sha256"] = m.sha(p.read_bytes())
        # Structural fixtures never assert real raw evidence. Actual joins use
        # Actual driver captures are separate from these structural unit inputs.
        with mock_patch.object(m, "validate_runtime_joins"):
            return m.validate_certificate(self.manifest, p, self.sidecar, self.iris)

    def test_synthetic_structural_positive(self):
        self.assertEqual(self.validate()["draw"]["programId"], 1)

    def test_mismatched_join_rejected(self):
        for field in ("contextId", "frameCounter", "programId", "textureUnit"):
            with self.subTest(field=field):
                old = self.c["sampledGaux2"][field]
                self.c["sampledGaux2"][field] = 71
                with self.assertRaises(ValueError):
                    self.validate()
                self.c["sampledGaux2"][field] = old

    def test_sparse_option_map_rejected(self):
        del self.c["loadedIrisOptions"]["SYNTHETIC_COMPLETE_MAP"]
        with self.assertRaisesRegex(ValueError, "option map"):
            self.validate()

    def test_sampler_object_override_rejected(self):
        self.c["sampledGaux2"]["samplerObject"] = 7
        self.c["sampledGaux2"]["effectiveSamplerState"]["anisotropy"] = 4
        with self.assertRaisesRegex(ValueError, "filtering"):
            self.validate()

    def test_texture_mips_or_lod_clamp_rejected(self):
        self.c["sampledGaux2"]["level1Width"] = 256
        with self.assertRaisesRegex(ValueError, "custom texture"):
            self.validate()
        self.c["sampledGaux2"]["level1Width"] = 0
        self.c["sampledGaux2"]["textureState"]["minLod"] = 1
        with self.assertRaisesRegex(ValueError, "filtering"):
            self.validate()

    def test_zero_sampler_cannot_override_texture_state(self):
        self.c["sampledGaux2"]["effectiveSamplerState"]["minLod"] = -1
        with self.assertRaisesRegex(ValueError, "Sampler zero"):
            self.validate()



if __name__ == "__main__":
    unittest.main()
