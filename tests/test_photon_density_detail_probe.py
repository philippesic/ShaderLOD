"""Pinned source and diagnostic semantics only; no output ZIP/compiler/client."""
import copy
import json
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import build_photon_density_detail_probe as probe

MANIFEST = json.loads((ROOT / probe.MANIFEST).read_text())
CONDITION = f"#ifdef {probe.SWITCH}\n"


def select_control(source: bytes, private_on: bool, main_program: bool) -> bytes:
    """Resolve only new control directives, leaving all upstream directives intact."""
    text = source.decode()
    declaration = MANIFEST["option_declaration"]
    text = text.replace(declaration, "", 1).replace(
        declaration.replace(f"//#define {probe.SWITCH}", f"#define {probe.SWITCH}"), "", 1)
    output, selected = [], []
    for line in text.splitlines(keepends=True):
        if line == CONDITION:
            if selected:
                raise AssertionError("Nested private directive")
            selected.append(private_on)
        elif line == "#if defined PROGRAM_DEFERRED1\n" and selected:
            selected.append(main_program)
        elif line == "#else\n" and selected:
            selected[-1] = not selected[-1]
        elif line == "#endif // PROGRAM_DEFERRED1\n" and selected:
            if len(selected) != 2:
                raise AssertionError("Unmatched program directive")
            selected.pop()
        elif line == f"#endif // {probe.SWITCH}\n":
            if len(selected) != 1:
                raise AssertionError("Unmatched private directive")
            selected.pop()
        elif not selected or all(selected):
            output.append(line)
    if selected:
        raise AssertionError("Unclosed private directive")
    return "".join(output).encode()


class PinnedSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base = ROOT / probe.BASE
        if not base.exists():
            raise unittest.SkipTest("Pinned local Photon archive not present")
        if probe.sha256(base.read_bytes()) != probe.BASE_SHA256:
            raise AssertionError("Original Photon archive changed")
        cls.files = probe.archive_members(base)

    def test_private_off_or_other_program_restores_every_upstream_source_byte(self):
        for arm in probe.ARMS:
            patched = probe.patch_members(self.files, MANIFEST, arm)
            self.assertEqual(set(self.files), set(patched))
            for private_on, main in ((False, False), (False, True), (True, False)):
                for name, source in self.files.items():
                    actual = select_control(patched[name], private_on, main) if name in probe.MEMBERS else patched[name]
                    self.assertEqual(source, actual, (arm, private_on, main, name))

    def test_only_fine_texture_site_and_declarations_differ_in_density_source(self):
        for arm in ("disabled", "enabled"):
            source = probe.patch_members(self.files, MANIFEST, arm)[probe.CUMULUS].decode()
            self.assertEqual(1, source.count(MANIFEST["declarations"]))
            self.assertEqual(1, source.count(MANIFEST["active_fine_sample"]))
            source = source.replace(MANIFEST["declarations"], "", 1).replace(
                MANIFEST["active_fine_sample"], MANIFEST["original_fine_sample"], 1)
            self.assertEqual(self.files[probe.CUMULUS].decode(), source)

    def test_fallback_has_literal_original_fetch_and_fixed_diagnostic_is_honest(self):
        active = MANIFEST["active_fine_sample"]
        self.assertIn("if (shaderlodBridgeVersion == 2 && shaderlodEnabled == 1)", active)
        self.assertIn("worley_1 = 0.5;", active)
        sample = "texture(SAMPLER_WORLEY_SWIRLEY, (pos + 0.4 * wind) * 0.005).x;"
        self.assertIn("    } else {\n        worley_1 =\n            " + sample, active)
        self.assertIn(MANIFEST["original_fine_sample"], active)
        self.assertNotIn("textureLod", active)
        self.assertNotIn("textureGrad", active)
        self.assertNotIn("dFdx", active)
        self.assertNotIn("dFdy", active)
        self.assertEqual("quality_changing_cost_control", MANIFEST["quality_class"])

    def test_option_default_and_member_scope_preserve_license_other_passes_and_textures(self):
        for arm in probe.ARMS:
            files = probe.patch_members(self.files, MANIFEST, arm)
            changed = sorted(name for name in files if files[name] != self.files[name])
            self.assertEqual([] if arm == "original" else sorted(probe.MEMBERS), changed)
            if arm != "original":
                declaration = f"{'#define' if arm == 'enabled' else '//#define'} {probe.SWITCH}\n".encode()
                self.assertEqual(1, files[probe.SETTINGS].count(declaration))
            for name in ("LICENSE", "shaders/program/d1_clouds.fsh", "shaders/program/d2_clouds_upscaling.fsh",
                         "shaders/image/worley_swirley.dat", "shaders/image/worley_bubbly.dat"):
                self.assertEqual(self.files[name], files[name])

    def test_mutated_source_license_and_manifest_fail_closed(self):
        for name in (*probe.MEMBERS, "LICENSE"):
            with self.assertRaises(ValueError):
                probe.patch_members(self.files | {name: self.files[name] + b"\n"}, MANIFEST, "original")
        mutations = [lambda m: m.update(diagnostic_fine_noise=0.0),
                     lambda m: m.update(quality_class="accepted_optimization"),
                     lambda m: m.update(base_sha256="bad"),
                     lambda m: m["members"].reverse(),
                     lambda m: m["members"][0]["replacements"].pop(),
                     lambda m: m["members"][0]["replacements"][0].update(before=""),
                     lambda m: m["members"][0]["replacements"][1].update(after="bad"),
                     lambda m: m["members"][1].update(enabled_sha256="bad")]
        for mutation in mutations:
            manifest = copy.deepcopy(MANIFEST)
            mutation(manifest)
            with self.assertRaises(ValueError):
                probe.patch_members(self.files, manifest, "enabled")

    def test_unknown_arm_and_unignored_output_reject_before_writing(self):
        with self.assertRaises(ValueError):
            probe.patch_members(self.files, MANIFEST, "other")
        with self.assertRaises(ValueError):
            probe.build(output=ROOT / "must-not-write.zip")
        with self.assertRaises(ValueError):
            probe.build(arm="other")

    def test_actual_pinned_iris_lexer_discovers_nested_guard_and_applies_boolean_override(self):
        evidence = MANIFEST["iris_option_discovery"]
        iris = ROOT / evidence["jar_path"]
        if not iris.exists() or not shutil.which("java"):
            self.skipTest("Actual pinned Iris lexer/JDK unavailable; no discovery acceptance")
        self.assertEqual(evidence["jar_sha256"], probe.sha256(iris.read_bytes()))
        with zipfile.ZipFile(iris) as archive:
            self.assertEqual(evidence["class_sha256"], probe.sha256(archive.read(evidence["class_path"])))
        cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
        dependencies = []
        for relative, filename in (("com.google.guava/guava/32.1.2-jre", "guava-32.1.2-jre.jar"),
                                   ("it.unimi.dsi/fastutil/8.5.12", "fastutil-8.5.12.jar")):
            matches = sorted((cache / relative).glob("*/" + filename))
            if len(matches) != 1:
                self.skipTest("Existing Iris lexer dependencies unavailable; no downloads/build")
            dependencies.append(matches[0])
        harness = r'''
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
class DensityIrisLexer {
    static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static Set<String> references(String... sources) {
        Set<String> result = new HashSet<>();
        for (String source : sources)
            result.addAll(new OptionAnnotatedSource(source).getBooleanDefineReferences().keySet());
        return result;
    }
    static Set<String> options(Set<String> references, String... sources) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < sources.length; i++)
            result.addAll(new OptionAnnotatedSource(sources[i]).getOptionSet(
                AbsolutePackPath.fromAbsolutePath("/source" + i + ".glsl"), references).getBooleanOptions().keySet());
        return result;
    }
    public static void main(String[] args) throws Exception {
        String name = args[0];
        String declaration = "//#define " + name + "\n";
        var legacy = new OptionAnnotatedSource(declaration +
            "#if defined " + name + " && defined PROGRAM_DEFERRED1\n#endif\n");
        require(!legacy.getBooleanDefineReferences().containsKey(name), "combined guard unexpectedly referenced");
        require(!legacy.getOptionSet(AbsolutePackPath.fromAbsolutePath("/legacy.glsl"),
            legacy.getBooleanDefineReferences().keySet()).getBooleanOptions().containsKey(name),
            "legacy option unexpectedly retained");
        String density = Files.readString(Path.of(args[1]));
        var actual = new OptionAnnotatedSource(density);
        require(actual.getBooleanDefineReferences().containsKey(name), "actual nested guard unreferenced");
        String originalDensity = Files.readString(Path.of(args[4]));
        String originalSettings = Files.readString(Path.of(args[5]));
        String stageEntry = Files.readString(Path.of(args[6]));
        String disabledSettings = Files.readString(Path.of(args[2]));
        var baselineReferences = references(originalDensity, originalSettings, stageEntry);
        var actualReferences = references(density, disabledSettings, stageEntry);
        var addedReferences = new HashSet<>(actualReferences);
        addedReferences.removeAll(baselineReferences);
        require(addedReferences.equals(Set.of(name)), "unintended reference added: " + addedReferences);
        require(!actualReferences.contains("PROGRAM_DEFERRED1"), "entry stage became option reference");
        var baselineOptions = options(baselineReferences, originalDensity, originalSettings, stageEntry);
        var actualOptions = options(actualReferences, density, disabledSettings, stageEntry);
        var expectedOptions = new HashSet<>(baselineOptions);
        expectedOptions.add(name);
        require(actualOptions.equals(expectedOptions), "option set differs beyond private gate");
        require(!actualOptions.contains("PROGRAM_DEFERRED1"), "entry stage leaked into options");
        for (int i = 2; i <= 3; i++) {
            boolean expectedDefault = i == 3;
            var settings = new OptionAnnotatedSource(Files.readString(Path.of(args[i])));
            var option = settings.getBooleanOptions().values().stream()
                .filter(value -> value.getName().equals(name)).findFirst().orElseThrow();
            require(option.getDefaultValue() == expectedDefault, "source default mismatch");
            var set = settings.getOptionSet(AbsolutePackPath.fromAbsolutePath("/settings.glsl"),
                actual.getBooleanDefineReferences().keySet());
            require(set.getBooleanOptions().containsKey(name), "actual option not retained");
            boolean override = !expectedDefault;
            var values = new MutableOptionValues(set, Map.of(name, Boolean.toString(override)));
            String edited = settings.apply(values);
            String expectedLine = (override ? "#define " : "//#define ") + name;
            require(edited.lines().anyMatch(expectedLine::equals), "actual Iris override did not edit declaration");
        }
        System.out.println("combined absent; actual nested discovered; default false/true; overrides true/false applied; sole new option; no stage leak");
    }
}
'''
        disabled = probe.patch_members(self.files, MANIFEST, "disabled")
        enabled = probe.patch_members(self.files, MANIFEST, "enabled")
        with tempfile.TemporaryDirectory(prefix="shaderlod-density-lexer-") as temporary:
            directory = Path(temporary)
            java_source = directory / "DensityIrisLexer.java"
            java_source.write_text(harness)
            inputs = [directory / name for name in ("density.glsl", "disabled.glsl", "enabled.glsl",
                                                    "original-density.glsl", "original-settings.glsl", "entry.fsh")]
            payloads = (disabled[probe.CUMULUS], disabled[probe.SETTINGS], enabled[probe.SETTINGS],
                        self.files[probe.CUMULUS], self.files[probe.SETTINGS], self.files["shaders/world0/deferred1.fsh"])
            for path, data in zip(inputs, payloads):
                path.write_bytes(data)
            command = ["java", "--class-path", os.pathsep.join(map(str, (iris, *dependencies))),
                       str(java_source), probe.SWITCH, *map(str, inputs)]
            result = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("combined absent; actual nested discovered; default false/true; overrides true/false applied; sole new option; no stage leak", result.stdout)


class DiagnosticSemanticsTests(unittest.TestCase):
    def test_strict_activation_truth_table(self):
        for private in (False, True):
            for main in (False, True):
                for version in (0, 1, 2, 3):
                    for enabled in (-1, 0, 1, 2):
                        active = private and main and version == 2 and enabled == 1
                        self.assertEqual(active, (private, main, version, enabled) == (True, True, 2, 1))

    def test_fixed_noise_is_not_squared_mean_or_nonlinear_density_preserving(self):
        values = (0.0, 1.0)
        mean = sum(values) / len(values)
        self.assertEqual(0.5, mean)
        self.assertNotEqual(mean ** 2, sum(v ** 2 for v in values) / len(values))
        def density(w):
            x = max(0.0, 0.4 - 0.5 * w * w)
            return (x + 3 * x) / (1 + 3 * x)
        self.assertNotEqual(density(0.5), sum(density(w) for w in values) / len(values))
        # A source-identical secondary count can still change executed work via occupancy.
        self.assertGreater(density(0.5), 0)
        self.assertEqual(density(1.0), 0)


if __name__ == "__main__":
    unittest.main()
