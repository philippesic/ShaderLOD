"""Pinned source/assembly checks, explicitly not an Iris/driver rendering test."""

import copy
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("bliss_raster", ROOT / "scripts/build_bliss_raster_candidate.py")
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)
PINNED_DEFINES = ("IRIS_VERSION 10812", "MC_VERSION 12101")


def expand(files, name, stack=()):
    if name in stack:
        raise AssertionError("Include cycle: " + name)
    source = files[name].decode().replace("\r\n", "\n")
    source = re.sub(r'^\s*#include\s+"(/[^\"]+)".*$',
                    lambda m: expand(files, "shaders" + m[1], stack + (name,)), source, flags=re.M)
    return source


def preprocess(files, name, values=None, defines=PINNED_DEFINES):
    source = expand(files, name)
    values = values or {}
    def define_value(match):
        key = match[1]
        if key not in values:
            return match[0]
        value = values[key]
        if isinstance(value, bool):
            return ("#define " if value else "// #define ") + key
        return "#define " + key + " " + str(value)
    source = re.sub(r"^[ \t]*(?://[ \t]*)?#define[ \t]+([A-Za-z_][A-Za-z_0-9]*)(?:[ \t]+[^\r\n]*)?$",
                    define_value, source, flags=re.M)
    # Iris also exposes selected const options. Apply their literal values
    # when comparing the complete retained option map, not just #defines.
    def const_value(match):
        if match[2] not in values:
            return match[0]
        value = values[match[2]]
        return match[1] + (str(value).lower() if isinstance(value, bool) else str(value)) + ";"
    source = re.sub(r"(\bconst\s+(?:bool|int|float)\s+([A-Za-z_][A-Za-z_0-9]*)\s*=\s*)[^;]+;",
                    const_value, source)
    source = re.sub(r"^\s*#(?:version|extension)\b[^\n]*", "", source, flags=re.M)
    source = "\n".join("#define " + d for d in defines) + "\n" + source
    result = subprocess.run(["clang", "-E", "-P", "-x", "c", "-"], input=source,
                            text=True, capture_output=True, timeout=15)
    if result.returncode:
        raise AssertionError(name + "\n" + result.stderr[-4000:])
    return re.sub(r"\s+", "", result.stdout)


class BlissRasterCandidateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = builder.read_manifest()
        if not builder.BASE.exists():
            raise RuntimeError("Pinned original Bliss archive is required for these source-contract tests")
        cls.original = builder.read_members(builder.BASE)
        cls.candidate = builder.patch(cls.original, cls.manifest)

    def test_exact_archive_and_preserved_native_options_license_members(self):
        self.assertEqual(builder.sha256(builder.BASE.read_bytes()), builder.BASE_SHA256)
        self.assertEqual(set(self.original), set(self.candidate))
        modified = {e["path"] for e in self.manifest["patches"]}
        self.assertEqual({n for n in self.original if self.original[n] != self.candidate[n]}, modified)
        for name in set(self.original) - modified:
            self.assertEqual(self.original[name], self.candidate[name], name)
        self.assertEqual(self.original["shaders/lib/settings.glsl"], self.candidate["shaders/lib/settings.glsl"])
        for name, digest in self.manifest["license_members"].items():
            self.assertEqual(builder.sha256(self.candidate[name]), digest)

    def test_member_and_patch_mutation_fail_closed(self):
        for name in ("shaders/world0/composite6.vsh", "shaders/lib/TAA_jitter.glsl", "LICENSE.md"):
            with self.subTest(name=name):
                mutated = self.original | {name: self.original[name] + b"\n"}
                with self.assertRaisesRegex(ValueError, "Pinned source member"):
                    builder.patch(mutated, self.manifest)
        bad = copy.deepcopy(self.manifest)
        bad["patches"][0]["replacements"][0]["after"] += " // altered"
        with self.assertRaisesRegex(ValueError, "Patched member hash"):
            builder.patch(self.original, bad)

    def test_ambiguous_anchor_rejected(self):
        bad = copy.deepcopy(self.manifest)
        bad["patches"][0]["replacements"][0]["count"] += 1
        with self.assertRaisesRegex(ValueError, "ambiguous native anchor"):
            builder.patch(self.original, bad)

    def test_options_duplicates_private_and_malformed_refused(self):
        for value in (b"TAA=true\nTAA=false\n", b"TAA:true\n", b"TAA=true\\\n",
                      b"SHADERLOD_EFFECT_ADAPTER=1\n"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                builder.parse_options(value)
        self.assertEqual(builder.parse_options(b"# original\r\nTAA=false\r\n"), {"TAA": "false"})
        for value in ("NaN", "inf", "0.851", "0", "true"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                builder.policy({"SCALE_FACTOR": value})

    def test_native_smaller_rate_never_increased_and_aa_off_stays_native(self):
        for scale in sorted(builder.SCALE_VALUES):
            values = {"TAA_UPSCALING": "true", "SCALE_FACTOR": str(scale)}
            result = builder.policy(values)
            self.assertLessEqual(result["candidate_effective_scale"], scale)
            self.assertEqual(builder.policy(values, enabled=False)["candidate_effective_scale"], scale)
            values["TAA"] = "false"
            self.assertEqual(builder.policy(values)["candidate_effective_scale"], 1.0)
        self.assertEqual(builder.policy({})["candidate_effective_scale"], 0.85)
        self.assertEqual(builder.policy({"SCALE_FACTOR": "0.10"})["candidate_effective_scale"], 0.85)

    def test_actual_wrapper_graph(self):
        for world in ("world0", "world-1", "world1"):
            for stage in ("vsh", "fsh"):
                self.assertIn(b'"/dimensions/composite4.' + stage.encode() + b'"', self.candidate[f"shaders/{world}/composite5.{stage}"])
                self.assertIn(b'"/dimensions/composite5.' + stage.encode() + b'"', self.candidate[f"shaders/{world}/composite6.{stage}"])
        prep = self.candidate["shaders/dimensions/composite4.fsh"]
        taa = self.candidate["shaders/dimensions/composite5.fsh"]
        self.assertIn(b"DRAWBUFFERS:06", prep)
        self.assertIn(b"DRAWBUFFERS:5", taa)
        self.assertIn(b"texture2D(colortex0, adjTC)", taa)
        self.assertIn(b"texture2D(colortex6, adjTC)", taa)
        self.assertIn(b"FastCatmulRom(colortex5, previousPosition.xy", taa)

    def test_native_property_graph_is_byte_exact_not_dimension_corrected(self):
        # Pinned Iris keeps the whole program path when matching its disabled
        # list. Stock Bliss's bare key does not disable these dimension wrappers.
        # Preserve that native graph; do not "fix" Off by adding qualified keys.
        name = "shaders/shaders.properties"
        self.assertEqual(self.candidate[name], self.original[name])
        self.assertIn(b"program.composite5.enabled = TAA_UPSCALING", self.candidate[name])
        for world in ("world0", "world-1", "world1"):
            self.assertNotIn(f"program.{world}/composite5.enabled".encode(), self.candidate[name])
            for stage in ("vsh", "fsh"):
                self.assertIn(f"shaders/{world}/composite5.{stage}", self.candidate)
        self.assertNotIn(name, {p["path"] for p in self.manifest["patches"]})

    def test_exact_iris_boolean_parser_rejects_numeric_comparison_counterexample(self):
        # Execute the actual shipped parser classes, not a reimplemented grammar.
        # The OptionValues proxy records boolean-name lookups, so numeric terms
        # demonstrably become unknown option names rather than comparisons.
        jar = ROOT / "dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar"
        self.assertEqual(builder.sha256(jar.read_bytes()),
                         "3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83")
        old = "TAA_UPSCALING || (TAA && SHADERLOD_BLISS_RASTER == 1 && RESOURCEPACK_SKY == 0 && DOF_QUALITY != 5 && !SCREENSHOT_MODE)"
        source = '''
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.shaderpack.parsing.BooleanParser;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
class BooleanParserProbe {
  public static void main(String[] args) {
    List<String> names = new ArrayList<>();
    OptionValues values = (OptionValues) Proxy.newProxyInstance(
      OptionValues.class.getClassLoader(), new Class<?>[] {OptionValues.class},
      (p,m,a) -> {
        if (m.getName().equals("getBooleanValueOrDefault")) {
          String key = (String)a[0]; names.add(key);
          return key.equals("TAA");
        }
        throw new AssertionError(m.getName());
      });
    for (String arg : args) {
      names.clear();
      System.out.println(BooleanParser.parse(arg, values) + " " + names);
    }
  }
}
'''
        with tempfile.TemporaryDirectory() as directory, patch.object(builder, "OUTPUT_ROOT", Path(directory)):
            path = Path(directory) / "BooleanParserProbe.java"
            path.write_text(source)
            result = subprocess.run(["java", "--class-path", str(jar), str(path),
                                     "1 == 1", "TAA_UPSCALING", "TAA", old],
                                    text=True, capture_output=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.splitlines(), [
                "false [1==1]", "false [TAA_UPSCALING]", "true [TAA]",
                "false [TAA_UPSCALING, TAA, SHADERLOD_BLISS_RASTER==1, RESOURCEPACK_SKY==0, DOF_QUALITY=5, SCREENSHOT_MODE]",
            ])

    def test_private_off_preprocesses_identically_to_original(self):
        # Diverse actual consumers, including all geometry classes, hand/DH,
        # cloud/shading/preparation/history/post and all dimension wrappers.
        programs = ["gbuffers_terrain.vsh", "gbuffers_hand.vsh", "gbuffers_water.vsh",
                    "gbuffers_hand_water.vsh", "gbuffers_weather.vsh", "gbuffers_beaconbeam.vsh",
                    "dh_terrain.vsh", "dh_water.vsh", "composite1.fsh", "composite2.fsh",
                    "composite5.vsh", "composite6.vsh", "composite6.fsh", "final.fsh"]
        for world, values in (("world0", {}), ("world-1", {"TAA_UPSCALING": True, "SCALE_FACTOR": "0.60"}),
                              ("world1", {"TAA": False, "TAA_UPSCALING": True})):
            for program in programs:
                name = f"shaders/{world}/{program}"
                with self.subTest(name=name, values=values):
                    self.assertEqual(preprocess(self.candidate, name, values), preprocess(self.original, name, values))

    def test_private_on_matches_native_taau_for_default_fixed_rate(self):
        for program in ("gbuffers_terrain.vsh", "gbuffers_hand.vsh", "gbuffers_water.vsh",
                        "dh_terrain.vsh", "dh_water.vsh", "composite1.fsh", "composite2.fsh",
                        "composite5.vsh", "composite6.vsh", "composite6.fsh", "composite12.fsh", "final.fsh"):
            name = "shaders/world0/" + program
            with self.subTest(program=program):
                self.assertEqual(preprocess(self.candidate, name, {builder.OPTION: 1}),
                                 preprocess(self.original, name, {"TAA_UPSCALING": True, "SCALE_FACTOR": "0.85"}))

    def test_shader_falls_back_for_aa_off_and_unsupported_modes(self):
        for values, defines in (({"TAA": False}, ()), ({"RESOURCEPACK_SKY": 1}, ()),
                                ({"DOF_QUALITY": 5}, ()), ({"SCREENSHOT_MODE": True}, ()), ({}, ("VOXY",))):
            for program in ("gbuffers_terrain.vsh", "composite5.vsh", "composite6.fsh"):
                name = "shaders/world0/" + program
                with self.subTest(program=program, values=values, defines=defines):
                    self.assertEqual(preprocess(self.candidate, name, values | {builder.OPTION: 1}, PINNED_DEFINES + defines),
                                     preprocess(self.original, name, values, PINNED_DEFINES + defines))

    def test_unknown_versions_fall_back_but_native_taau_stays_native(self):
        refused = ((), ("MC_VERSION 12101",), ("IRIS_VERSION 10812",),
                   ("IRIS_VERSION 10811", "MC_VERSION 12101"),
                   ("IRIS_VERSION 10813", "MC_VERSION 12101"),
                   ("IRIS_VERSION 10812", "MC_VERSION 12111"))
        for defines in refused:
            for native in ({}, {"TAA_UPSCALING": True, "SCALE_FACTOR": "0.60"}):
                for program in ("gbuffers_terrain.vsh", "composite5.vsh", "composite6.fsh"):
                    name = "shaders/world0/" + program
                    with self.subTest(defines=defines, native=native, program=program):
                        self.assertEqual(preprocess(self.candidate, name, native | {builder.OPTION: 1}, defines),
                                         preprocess(self.original, name, native, defines))

    def test_all_scale_factor_uses_are_local_or_option_metadata(self):
        occurrences = {name: data for name, data in self.original.items() if b"SCALE_FACTOR" in data}
        languages = {"shaders/lang/en_us.lang", "shaders/lang/pt_br.lang", "shaders/lang/ru_RU.lang", "shaders/lang/zh_cn.lang"}
        self.assertEqual(set(occurrences), {"shaders/lib/res_params.glsl", "shaders/shaders.properties"} | languages)
        for line in occurrences["shaders/shaders.properties"].decode().splitlines():
            if "SCALE_FACTOR" in line:
                self.assertTrue(line.lstrip().startswith(("sliders =", "screen.TAA_OPTIONS=")), line)
        for name in languages:
            for line in occurrences[name].decode().splitlines():
                if "SCALE_FACTOR" in line:
                    self.assertRegex(line.strip(), r"^option\.SCALE_FACTOR(?:\.comment)?\s*=")

    def test_all_wrappers_retained_options_match_native_taau_reference(self):
        options = ROOT / "src/main/resources/assets/shaderlod/blissquad/native463.json"
        raw = options.read_bytes()
        values = {k: ({"true": True, "false": False}.get(v, v))
                  for k, v in json.loads(raw).items()}
        self.assertEqual(len(values), 463)
        programs = sorted(n for n in self.original
                          if re.fullmatch(r"shaders/world(?:0|-1|1)/[^/]+\.(?:vsh|fsh|gsh|tcs|tes|csh)", n))
        self.assertEqual(len(programs), 244)
        private = values | {builder.OPTION: 1}
        reference = values | {"TAA_UPSCALING": True, "SCALE_FACTOR": "0.85"}
        records = []
        for dh in (False, True):
            defines = PINNED_DEFINES + ("IS_IRIS", "MC_OS_MAC", "MC_GL_VERSION 410") + (("DISTANT_HORIZONS",) if dh else ())
            for name in programs:
                with self.subTest(name=name, dh=dh):
                    candidate = preprocess(self.candidate, name, private, defines)
                    native = preprocess(self.original, name, reference, defines)
                    records.append({"member": name, "dh_macro": dh,
                                    "private_normalized_source_sha256": builder.sha256(candidate.encode()),
                                    "reference_normalized_source_sha256": builder.sha256(native.encode()),
                                    "equal": candidate == native})
                    self.assertEqual(candidate, native)
        if os.environ.get("BLISS_AUDIT_REPORT"):
            path = builder.OUTPUT_ROOT / os.environ["BLISS_AUDIT_REPORT"]
            self.assertTrue(path.resolve().is_relative_to(builder.OUTPUT_ROOT.resolve()))
            with path.open("x") as handle:
                json.dump({"source_only": True, "driver_compilation": False,
                           "comparison": "private On retained463 map latent0.75 vs original native TAAU enabled and SCALE_FACTOR0.85",
                           "original_options_sha256": builder.sha256(raw),
                           "manifest_sha256": builder.MANIFEST_SHA256,
                           "defines_without_dh": list(PINNED_DEFINES + ("IS_IRIS", "MC_OS_MAC", "MC_GL_VERSION 410")),
                           "wrapper_count": len(programs), "comparison_count": len(records),
                           "records": records}, handle, indent=2)
                handle.write("\n")

    def test_taa_vertex_stays_full_extent_while_preparation_scales(self):
        values = {builder.OPTION: 1}
        taa = preprocess(self.candidate, "shaders/world0/composite6.vsh", values)
        prep = preprocess(self.candidate, "shaders/world0/composite5.vsh", values)
        self.assertNotIn("gl_Position.xy=", taa)
        self.assertIn("gl_Position.xy=(gl_Position.xy*0.5+0.5)*vec2(0.85,0.85)*2.0-1.0", prep)

    def test_lower_native_scale_uses_minimum_in_actual_source(self):
        source = preprocess(self.candidate, "shaders/world0/gbuffers_hand.vsh",
                            {builder.OPTION: 1, "TAA_UPSCALING": True, "SCALE_FACTOR": "0.60"})
        self.assertIn("vec2(min(0.60,0.85),min(0.60,0.85))", source)

    def test_build_determinism_options_prefix_and_immutable_original(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(builder, "OUTPUT_ROOT", Path(directory)):
            directory = Path(directory)
            native = directory / "original.txt"
            raw = b"# source defaults with a lower native rate\r\nTAA_UPSCALING=true\r\nSCALE_FACTOR=0.60\r\n"
            native.write_bytes(raw)
            first = builder.build(builder.BASE, native, directory / "first.zip")
            second = builder.build(builder.BASE, native, directory / "second.zip")
            self.assertEqual(first["output_sha256"], second["output_sha256"])
            self.assertEqual(Path(first["options_output"]).read_bytes(), raw + b"SHADERLOD_BLISS_RASTER=1\n")
            self.assertEqual(native.read_bytes(), raw)
            self.assertEqual(builder.sha256(builder.BASE.read_bytes()), builder.BASE_SHA256)
            receipt = json.loads((directory / "first.json").read_text())
            self.assertTrue(receipt["native_options_preserved_as_exact_prefix"])
            self.assertFalse(any(receipt["validation"].values()))
            with self.assertRaisesRegex(ValueError, "fresh ZIP"):
                builder.build(builder.BASE, native, directory / "first.zip")

    def test_builder_refuses_unqualified_profile_or_wrong_archive_without_output(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(builder, "OUTPUT_ROOT", Path(directory)):
            directory = Path(directory)
            options = directory / "options.txt"
            output = directory / "candidate.zip"
            for raw in (b"RESOURCEPACK_SKY=1\n", b"DOF_QUALITY=5\n", b"SCREENSHOT_MODE=true\n"):
                options.write_bytes(raw)
                with self.assertRaisesRegex(ValueError, "Unqualified"):
                    builder.build(builder.BASE, options, output)
                self.assertFalse(output.exists())
            options.write_bytes(b"")
            wrong = directory / "wrong.zip"
            wrong.write_bytes(b"not the archive")
            with self.assertRaisesRegex(ValueError, "archive mismatch"):
                builder.build(wrong, options, output)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
