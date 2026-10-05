"""Pinned source, eligibility and actual Iris lexer fixtures; no pack/GL/client."""
import copy
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import build_photon_lighting_cost_probe as probe

MANIFEST = json.loads((ROOT / probe.MANIFEST).read_text())
MARKER = "SHADERLOD_LIGHTING_COST_PROFILE"


def select_probe(source, private_on, profile):
    text = source.decode()
    declaration = MANIFEST["option_declaration"]
    text = text.replace(declaration, "", 1).replace(
        declaration.replace(f"//#define {probe.SWITCH}", f"#define {probe.SWITCH}"), "", 1)
    output, selected = [], []
    for line in text.splitlines(keepends=True):
        if line == f"#ifdef {probe.SWITCH}\n":
            selected.append(private_on)
        elif line == MANIFEST["profile_condition"]:
            selected.append(private_on and profile)
        elif line == MANIFEST["profile_without_switch"]:
            selected.append(profile)
        elif line == f"#else // {MARKER}\n":
            if not selected:
                raise AssertionError("Unmatched private else")
            selected[-1] = not selected[-1]
        elif line in (f"#endif // {MARKER}\n", f"#endif // {probe.SWITCH}\n"):
            if not selected:
                raise AssertionError("Unmatched private endif")
            selected.pop()
        elif not selected or all(selected):
            output.append(line)
    if selected:
        raise AssertionError("Unclosed private directive")
    return "".join(output).encode()


def receiver(version=2, enabled=1, near=32.0, far=96.0, width=8.0, distance=100.0,
             mask=0, hand=False, water=0, wetness=0.0, metal=False, hard_metal=False,
             sss=0.0, sheen=0.0, emission=0.0, porosity=0.0, reflection=0.0,
             roughness=0.9, f0=0.02, albedo_finite=True, normal_finite=True, overlay=False):
    """Host boundary model only; no GLSL execution or physical-work claim."""
    fields = (near, far, width, distance, wetness, roughness, f0)
    if version != 2 or enabled != 1 or not all(math.isfinite(x) for x in fields):
        return False
    if near < 1 or far <= near or width < 0 or width > min(2 * near, far - near):
        return False
    edge = far + width * 0.5
    if not math.isfinite(edge) or distance < edge:
        return False
    return (not hand and water == 0 and wetness == 0 and not overlay and mask == 0
            and not metal and not hard_metal and sss == sheen == emission == porosity == reflection == 0
            and 0.75 <= roughness <= 1 and f0 <= 0.04 and albedo_finite and normal_finite)


class LightingControlTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base = ROOT / probe.BASE
        if not base.exists():
            raise unittest.SkipTest("Pinned local Photon unavailable")
        if probe.sha256(base.read_bytes()) != probe.BASE_SHA256:
            raise AssertionError("Pinned original Photon changed")
        cls.files = probe.archive_members(base)

    def test_private_off_or_unsupported_profile_restores_every_original_member(self):
        for arm in probe.ARMS:
            patched = probe.patch_members(self.files, MANIFEST, arm)
            self.assertEqual(set(self.files), set(patched))
            for private, profile in ((False, False), (False, True), (True, False)):
                for name, original in self.files.items():
                    actual = select_probe(patched[name], private, profile) if name in probe.MEMBERS else patched[name]
                    self.assertEqual(original, actual, (arm, private, profile, name))

    def test_original_fallback_ao_shadow_and_diffuse_expressions_retained(self):
        regions = MANIFEST["regions"]
        self.assertIn(regions["prefetch"], regions["ao_active"])
        ao = regions["ao_fallback"].replace("        ao = ambient_upscaled.x;", "        float ao = ambient_upscaled.x;")
        ao = ao.replace("        ambient_sss = ambient_upscaled.y;", "        float ambient_sss = ambient_upscaled.y;")
        ao = ao.replace("        bent_normal.xy =", "        vec3 bent_normal;\n        bent_normal.xy =", 1)
        self.assertEqual(regions["ao"], ao)
        expected = regions["shadows"]
        for before, after in (("vec3 shadows = vec3(0.0);", "shadows = vec3(0.0);"),
                              ("float shadow_distance_fade = 1.0;", "shadow_distance_fade = 1.0;"),
                              ("float sss_depth = 0.0;", "sss_depth = 0.0;"),
                              ("const vec3 shadows = vec3(1.0);", "shadows = vec3(1.0);")):
            expected = expected.replace(before, after)
        expected = expected.replace("const shadow_distance_fade =", "shadow_distance_fade =").replace("const sss_depth =", "sss_depth =")
        self.assertEqual(expected, regions["shadow_fallback"])
        self.assertIn(regions["diffuse"], regions["diffuse_active"])

    def test_common_material_highlight_reflection_edge_and_composition_unchanged(self):
        original = self.files[probe.D4].decode()
        actual = select_probe(probe.patch_members(self.files, MANIFEST, "enabled")[probe.D4], True, True).decode()
        # Remove only the optional final marker to verify normal-debug0 common source.
        cue = MANIFEST["coverage_cue"]["source"]
        cue_body = cue[len(MANIFEST["profile_condition"]):].removesuffix(f"#endif // {MARKER}\n")
        self.assertEqual(1, actual.count(cue_body))
        actual = actual.replace(cue_body, "", 1)
        suffix = original[original.index("        // Specular highlight"):]
        self.assertTrue(actual.endswith(suffix))
        self.assertEqual(1, actual.count("material_from("))
        for call in ("get_specular_highlight(", "get_specular_reflections(", "get_edge_highlight("):
            self.assertEqual(1, actual.count(call))
        self.assertNotIn("dFdx", MANIFEST["helper_declarations"])
        self.assertNotIn("dFdy", MANIFEST["helper_declarations"])
        ao = MANIFEST["regions"]["ao_active"]
        self.assertLess(ao.index("if (!shaderlod_cost_receiver)"), ao.index("texelFetch(colortex6"))
        self.assertIn("fragment_color = vec3(0.25) * material.albedo * rcp_pi;", MANIFEST["regions"]["diffuse_active"])

    def test_default_option_exact_three_member_scope_and_six_typed_core_fields(self):
        for arm in probe.ARMS:
            actual = probe.patch_members(self.files, MANIFEST, arm)
            changed = sorted(name for name in actual if actual[name] != self.files[name])
            self.assertEqual([] if arm == "original" else sorted(probe.MEMBERS), changed)
            self.assertEqual(self.files["LICENSE"], actual["LICENSE"])
            if arm != "original":
                option = f"{'#define' if arm == 'enabled' else '//#define'} {probe.SWITCH}\n".encode()
                self.assertEqual(1, actual[probe.SETTINGS].count(option))
        declarations = MANIFEST["helper_declarations"]
        for name in ("shaderlodBridgeVersion", "shaderlodEnabled", "shaderlodDebug"):
            self.assertEqual(1, declarations.count("uniform int " + name + ";"))
        for name in ("shaderlodNear", "shaderlodFar", "shaderlodTransition"):
            self.assertEqual(1, declarations.count("uniform float " + name + ";"))
        self.assertNotIn("shaderlodPreset", declarations)

    def test_far_boundary_protocol_and_nonfinite_band_fallbacks(self):
        self.assertTrue(receiver())
        self.assertFalse(receiver(distance=99.999))
        self.assertTrue(receiver(width=0, distance=96))
        self.assertTrue(receiver(near=2, width=4, distance=98))
        self.assertFalse(receiver(near=2, width=4.001, distance=100))
        cases = [dict(version=0), dict(version=1), dict(version=3), dict(enabled=0), dict(enabled=2),
                 dict(near=0), dict(far=32), dict(width=-1), dict(width=65), dict(hand=True), dict(water=1),
                 dict(near=math.nan), dict(far=math.inf), dict(width=math.nan), dict(distance=math.inf),
                 dict(wetness=math.nan), dict(wetness=0.01), dict(normal_finite=False)]
        for changes in cases:
            self.assertFalse(receiver(**changes), changes)
        declarations = MANIFEST["helper_declarations"]
        self.assertIn("isnan(far_edge) || isinf(far_edge)", declarations)
        self.assertIn("isnan(distance_blocks) || isinf(distance_blocks)", declarations)
        self.assertIn("min(2.0 * shaderlodNear, shaderlodFar - shaderlodNear)", declarations)

    def test_optional_coverage_cue_uses_existing_debug_after_common_composition(self):
        cue = MANIFEST["coverage_cue"]
        original = self.files[probe.D4].decode()
        active = probe.patch_members(self.files, MANIFEST, "enabled")[probe.D4].decode()
        self.assertIn(cue["anchor"] + cue["source"], active)
        self.assertGreater(active.index(cue["anchor"] + cue["source"]), active.index("get_edge_highlight("))
        self.assertIn("shaderlodBridgeVersion == 2 && shaderlodDebug == 1 && !is_hand", cue["source"])
        self.assertIn("shaderlod_cost_receiver ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)", cue["source"])
        self.assertNotIn("texture", cue["source"])
        self.assertNotIn("dFdx", cue["source"])
        self.assertNotIn("#define", cue["source"])
        self.assertEqual(1, original.count(cue["anchor"]))

    def test_material_eligibility_checks_fields_beyond_approved_mask(self):
        self.assertTrue(receiver(roughness=0.75, f0=0.04))
        for changes in (dict(mask=1), dict(metal=True), dict(hard_metal=True), dict(sss=0.5), dict(sheen=0.5),
                        dict(emission=0.1), dict(porosity=0.1), dict(reflection=1), dict(roughness=0.749),
                        dict(roughness=1.01), dict(f0=0.041), dict(albedo_finite=False), dict(overlay=True)):
            self.assertFalse(receiver(**changes), changes)
        source = self.files["shaders/include/surface/material.glsl"].decode()
        default = source[source.index("    // Create material with default values"):source.index("    // Hardcoded materials")]
        for field in ("sss_amount", "sheen_amount", "porosity", "ssr_multiplier"):
            self.assertIn("material." + field + " = 0.0;", default)
        branch = source[source.index("if (material_mask == 0u)"):source.index("} else { // 3", source.index("if (material_mask == 0u)"))]
        self.assertNotIn("sss_amount =", branch)
        self.assertNotIn("emission =", branch)

    def test_private_shadow_sampling_level_zero_preserves_original_kernels(self):
        patched = probe.patch_members(self.files, MANIFEST, "enabled")[probe.PCSS]
        active = select_probe(patched, True, True).decode()
        original = self.files[probe.PCSS].decode()
        normalized = active.replace("textureLod(shadowtex1, shadow_screen_pos, 0.0)", "texture(shadowtex1, shadow_screen_pos)")
        normalized = normalized.replace("textureLod(shadowtex1, vec3(uv, shadow_screen_pos.z), 0.0)",
                                        "texture(shadowtex1, vec3(uv, shadow_screen_pos.z))")
        self.assertEqual(original, normalized)
        self.assertEqual(3, active.count("textureLod(shadowtex1,"))
        self.assertEqual("unverified; near/globaloff parity and actual bound sampler/filter required before timing; no sampler readback claimed",
                         MANIFEST["sampling_contract"]["driver_state"])

    def test_changed_source_manifest_license_and_output_scope_fail_closed(self):
        for name in (*probe.MEMBERS, "LICENSE"):
            with self.assertRaises(ValueError):
                probe.patch_members(self.files | {name: self.files[name] + b"\n"}, MANIFEST, "original")
        for update in ({"base_sha256": "bad"}, {"diagnostic_diffuse_coefficient": [0.5, 0.5, 0.5]},
                       {"quality_class": "accepted_optimization"}):
            with self.assertRaises(ValueError):
                probe.patch_members(self.files, MANIFEST | update, "enabled")
        malformed = copy.deepcopy(MANIFEST)
        malformed["members"][0]["replacements"][0]["after"] += "bad"
        with self.assertRaises(ValueError):
            probe.patch_members(self.files, malformed, "enabled")
        with self.assertRaises(ValueError):
            probe.build(output=ROOT / "must-not-write.zip")
        with self.assertRaises(ValueError):
            probe.build(arm="other")

    def test_actual_pinned_iris_parser_adds_only_public_gate_and_applies_override(self):
        evidence = MANIFEST["iris_option_discovery"]
        iris = ROOT / evidence["jar_path"]
        if not iris.exists() or not shutil.which("java"):
            self.skipTest("Actual Iris/JDK unavailable; no parser acceptance")
        self.assertEqual(evidence["jar_sha256"], probe.sha256(iris.read_bytes()))
        with zipfile.ZipFile(iris) as archive:
            self.assertEqual(evidence["class_sha256"], probe.sha256(archive.read(evidence["class_path"])))
        cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
        jars = []
        for relative, filename in (("com.google.guava/guava/32.1.2-jre", "guava-32.1.2-jre.jar"),
                                   ("it.unimi.dsi/fastutil/8.5.12", "fastutil-8.5.12.jar")):
            matches = list((cache / relative).glob("*/" + filename))
            if len(matches) != 1:
                self.skipTest("Existing lexer dependencies unavailable; no downloads/build")
            jars.append(matches[0])
        harness = r'''
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
class LightingIrisLexer {
  static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
  static Set<String> references(List<String> sources) {
    var result = new HashSet<String>();
    for (String source : sources) result.addAll(new OptionAnnotatedSource(source).getBooleanDefineReferences().keySet());
    return result;
  }
  static Set<String> options(List<String> sources, Set<String> refs) {
    var result = new HashSet<String>();
    for (int i=0; i<sources.size(); i++) result.addAll(new OptionAnnotatedSource(sources.get(i)).getOptionSet(
      AbsolutePackPath.fromAbsolutePath("/source"+i+".glsl"),refs).getBooleanOptions().keySet());
    return result;
  }
  public static void main(String[] args) throws Exception {
    String name=args[0]; var original=new ArrayList<String>(); var active=new ArrayList<String>();
    for (int i=1;i<=4;i++) original.add(Files.readString(Path.of(args[i])));
    for (int i=5;i<=8;i++) active.add(Files.readString(Path.of(args[i])));
    var oldRefs=references(original); var newRefs=references(active);
    var added=new HashSet<>(newRefs); added.removeAll(oldRefs);
    check(added.equals(Set.of(name)), "extra references: "+added);
    var expected=options(original,oldRefs); expected.add(name);
    check(options(active,newRefs).equals(expected),"extra/missing boolean options");
    check(!newRefs.contains("PROGRAM_DEFERRED4"),"stage-option leak");
    for (int i=0;i<2;i++) {
      var settings=new OptionAnnotatedSource(i==0 ? active.get(2) : Files.readString(Path.of(args[9])));
      var option=settings.getBooleanOptions().values().stream().filter(v->v.getName().equals(name)).findFirst().orElseThrow();
      check(option.getDefaultValue()==(i==1),"default mismatch");
      var set=settings.getOptionSet(AbsolutePackPath.fromAbsolutePath("/settings.glsl"),newRefs);
      var values=new MutableOptionValues(set,Map.of(name,Boolean.toString(i==0)));
      String expectedLine=(i==0 ? "#define " : "//#define ")+name;
      check(settings.apply(values).lines().anyMatch(expectedLine::equals),"override failed");
    }
    System.out.println("public gate only; no stage/helper leak; defaults and overrides accepted");
  }
}
'''
        disabled = probe.patch_members(self.files, MANIFEST, "disabled")
        enabled = probe.patch_members(self.files, MANIFEST, "enabled")
        names = [*probe.MEMBERS, "shaders/world0/deferred4.fsh"]
        payloads = [self.files[n] for n in names] + [disabled[n] for n in names] + [enabled[probe.SETTINGS]]
        with tempfile.TemporaryDirectory(prefix="shaderlod-lighting-lexer-") as temporary:
            directory = Path(temporary)
            java_source = directory / "LightingIrisLexer.java"
            java_source.write_text(harness)
            inputs = []
            for i, data in enumerate(payloads):
                path = directory / f"source{i}.glsl"
                path.write_bytes(data)
                inputs.append(str(path))
            result = subprocess.run(["java", "--class-path", os.pathsep.join(map(str, (iris, *jars))),
                                     str(java_source), probe.SWITCH, *inputs],
                                    capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("public gate only; no stage/helper leak; defaults and overrides accepted", result.stdout)


if __name__ == "__main__":
    unittest.main()
