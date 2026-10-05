"""Design/safety/source fixtures only: no full archive build, compiler or client."""
import copy
import json
from pathlib import Path
import re
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import build_photon_cloud_literal_probe as probe

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / probe.MANIFEST).read_text())


def select_design(source: bytes, enabled: bool) -> bytes:
    text = source.decode()
    declaration = MANIFEST["option_declaration"]
    text = text.replace(declaration, "", 1).replace(
        declaration.replace(f"//#define {probe.SWITCH}", f"#define {probe.SWITCH}"), "", 1)
    output = []
    branch = None
    for line in text.splitlines(keepends=True):
        if line == f"#ifdef {probe.SWITCH}\n":
            if branch is not None:
                raise AssertionError("Nested design directive")
            branch = enabled
        elif line == "#else\n" and branch is not None:
            branch = not branch
        elif line.rstrip() == f"#endif // {probe.SWITCH}":
            if branch is None:
                raise AssertionError("Unmatched design directive")
            branch = None
        elif branch is None or branch:
            output.append(line)
    if branch is not None:
        raise AssertionError("Unclosed design directive")
    return "".join(output).encode()


def function_body(source: str, name: str) -> str:
    start = source.index("{", source.index(f"float {name}(")) + 1
    depth = 1
    for end in range(start, len(source)):
        depth += (source[end] == "{") - (source[end] == "}")
        if depth == 0:
            return source[start:end]
    raise AssertionError(f"Unclosed function {name}")


class LiteralDesignFixtureTests(unittest.TestCase):
    def fixture(self):
        manifest = copy.deepcopy(MANIFEST)
        files = {"LICENSE": b"unchanged license", "shaderlod-build.json": b"unchanged baseline metadata"}
        for member in manifest["members"]:
            source = ("\n".join(item["before"] for item in member["replacements"]).rstrip("\n") + "\n").encode()
            files[member["path"]] = source
            member["source_sha256"] = probe.sha256(source)
            patched = source.decode()
            for item in member["replacements"]:
                patched = patched.replace(item["before"], item["after"], 1)
            member["patched_sha256"] = probe.sha256(patched.encode())
            if member["path"] == probe.SETTINGS:
                enabled = patched.replace(f"//#define {probe.SWITCH}\n", f"#define {probe.SWITCH}\n", 1)
                member["enabled_sha256"] = probe.sha256(enabled.encode())
        return files, manifest

    def test_disabled_design_restores_exact_source_and_original_generic_function(self):
        files, manifest = self.fixture()
        preview = probe.preview_design(files, manifest)
        for name in probe.MEMBERS:
            self.assertEqual(files[name], select_design(preview[name], False), name)
        for member in manifest["members"][:-1]:
            self.assertIn(member["replacements"][0]["before"], preview[member["path"]].decode())

    def test_private_literal_bodies_match_b15_after_removing_only_count_declarations(self):
        files, manifest = self.fixture()
        preview = probe.preview_design(files, manifest, enabled=True)
        for member in manifest["members"][:-1]:
            name = "clouds_" + Path(member["path"]).stem
            macro = "CLOUDS_" + Path(member["path"]).stem.upper()
            active = select_design(preview[member["path"]], True).decode()
            original_body = function_body(files[member["path"]].decode(), name + "_optical_depth")
            for label, count, option in (("light", 2, "LIGHTING"), ("ambient", 1, "AMBIENT")):
                kernel_name = name + f"_optical_depth_literal_{label}_{count}"
                self.assertEqual(1, active.count(f"float {kernel_name}("))
                body = function_body(active, kernel_name)
                declarations = (f"    const uint step_count = {count}u;\n"
                                f"    const uint original_steps = {macro}_{option}_STEPS;\n")
                self.assertEqual(1, body.count(declarations))
                self.assertEqual(original_body, body.replace(declarations, "", 1))

    def test_dispatch_requires_changed_and_exact_eligible_count_and_keeps_generic_fallback(self):
        for member in MANIFEST["members"][:-1]:
            name = "clouds_" + Path(member["path"]).stem
            macro = "CLOUDS_" + Path(member["path"]).stem.upper()
            for replacement, label, prefix, count, option, direction, component in (
                (member["replacements"][1], "light", "lighting", 2, "LIGHTING", "light_dir", "x"),
                (member["replacements"][2], "sky", "ambient", 1, "AMBIENT", "sky_dir", "y"),
            ):
                active = replacement["after"].split("\n#else\n", 1)[0]
                self.assertIn(f"if ({prefix}_steps != {macro}_{option}_STEPS && {prefix}_steps == {count}u)", active)
                kernel_label = "light" if label == "light" else "ambient"
                self.assertIn(f"{name}_optical_depth_literal_{kernel_label}_{count}(\n"
                              f"                ray_pos,\n                {direction},\n                hash.{component}", active)
                self.assertIn(f"}} else {{\n            {label}_optical_depth = {name}_optical_depth(", active)
                self.assertIn(f"{prefix}_steps,\n                {macro}_{option}_STEPS", active)

    def test_actual_dispatch_predicates_route_full_and_reduced_option_count_fixtures(self):
        cases = [((6, 2), (6, 2), (False, False)),
                 ((3, 1), (6, 2), (False, True)),
                 ((2, 1), (6, 2), (True, True)),
                 ((2, 1), (2, 1), (False, False)),
                 ((1, 1), (1, 1), (False, False)),
                 ((4, 2), (8, 4), (False, False)),
                 ((2, 1), (8, 4), (True, True))]
        for member in MANIFEST["members"][:-1]:
            macro = "CLOUDS_" + Path(member["path"]).stem.upper()
            predicates = [re.search(r"if \(([^\n]+)\) \{", item["after"]).group(1)
                          for item in member["replacements"][1:]]
            for selected, original, expected in cases:
                environment = {"lighting_steps": selected[0], "ambient_steps": selected[1],
                               f"{macro}_LIGHTING_STEPS": original[0],
                               f"{macro}_AMBIENT_STEPS": original[1]}
                result = tuple(eval(re.sub(r"(\d+)u", r"\1", expression).replace("&&", "and"),
                                    {"__builtins__": {}}, environment) for expression in predicates)
                self.assertEqual(expected, result)

    def test_original_bytes_default_off_restoration_and_enabled_declaration_delta(self):
        files, manifest = self.fixture()
        self.assertEqual(files, probe.patch_members(files, manifest, "original"))
        disabled = probe.patch_members(files, manifest, "disabled")
        enabled = probe.patch_members(files, manifest, "enabled")
        for name in probe.MEMBERS:
            self.assertEqual(files[name], select_design(disabled[name], False))
        self.assertEqual({probe.SETTINGS}, {name for name in files if disabled[name] != enabled[name]})
        self.assertEqual(enabled[probe.SETTINGS], disabled[probe.SETTINGS].replace(
            f"//#define {probe.SWITCH}\n".encode(), f"#define {probe.SWITCH}\n".encode(), 1))
        unqualified = copy.deepcopy(manifest)
        unqualified["sampling_safety"]["status"] = "incomplete"
        with self.assertRaisesRegex(ValueError, "qualification"):
            probe.patch_members(files, unqualified, "enabled")

    def test_enabled_assembly_requires_reviewed_report_before_any_archive_write(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(FileNotFoundError):
                probe.build(arm="enabled", root=root)
            self.assertEqual([], list(root.iterdir()))
            report = root / "changed-report.json"
            report.write_text('{}\n')
            with self.assertRaisesRegex(ValueError, "evidence changed"):
                probe.build(arm="enabled", root=root, sampler_evidence=report)
            self.assertFalse((root / "build").exists())

    def test_corrupt_source_output_scope_or_anchor_fails_closed(self):
        files, manifest = self.fixture()
        name = probe.MEMBERS[0]
        with self.assertRaisesRegex(ValueError, "member missing or changed"):
            probe.preview_design(files | {name: files[name] + b"\n"}, manifest)
        changed = copy.deepcopy(manifest)
        changed["members"][0]["replacements"][0]["after"] += "// unreviewed\n"
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            probe.preview_design(files, changed)
        changed = copy.deepcopy(manifest)
        changed["members"][0]["path"] = "shaders/include/shaderlod/lod.glsl"
        with self.assertRaisesRegex(ValueError, "scope"):
            probe.preview_design(files, changed)
        duplicate = files[name] + manifest["members"][0]["replacements"][0]["before"].encode()
        changed = copy.deepcopy(manifest)
        changed["members"][0]["source_sha256"] = probe.sha256(duplicate)
        with self.assertRaisesRegex(ValueError, "exactly once"):
            probe.preview_design(files | {name: duplicate}, changed)

    def test_baseline_and_nonignored_output_replacements_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "immutable B15"):
                probe.build(root / probe.BASE, root=root)
            with self.assertRaisesRegex(ValueError, "ignored build"):
                probe.build(root / "run/shaderpacks/unsafe.zip", root=root)
            self.assertEqual([], list(root.iterdir()))


class PinnedLiteralDesignTests(unittest.TestCase):
    @unittest.skipUnless((ROOT / probe.BASE).exists(), "Immutable B15 archive not available")
    def test_exact_archive_metadata_and_proposed_call_site_placement(self):
        base = ROOT / probe.BASE
        self.assertEqual(probe.BASE_SHA256, probe.sha256(base.read_bytes()))
        original = probe.archive_members(base)
        self.assertEqual(MANIFEST["license_sha256"], probe.sha256(original["LICENSE"]))
        self.assertEqual(MANIFEST["baseline_metadata_sha256"], probe.sha256(original["shaderlod-build.json"]))
        preview = probe.preview_design(original, MANIFEST, enabled=True)
        for member in MANIFEST["members"][:-1]:
            path = member["path"]
            source = preview[path].decode()
            self.assertLess(source.index("if (density < eps)"), source.index("if (lighting_steps !="))
            self.assertLess(source.index("for (uint i = 0u; i < primary_steps; ++i)"), source.index("if (lighting_steps !="))
            self.assertEqual(original[path], select_design(preview[path], False))
        for name in original.keys() - set(probe.MEMBERS):
            self.assertEqual(original[name], preview[name], name)
        self.assertEqual(original, probe.patch_members(original, MANIFEST, "original"))

    @unittest.skipUnless((ROOT / probe.BASE).exists(), "Immutable B15 archive not available")
    def test_implicit_sampling_initial_finding_and_controlled_scope_are_retained(self):
        original = probe.archive_members(ROOT / probe.BASE)
        for layer in ("cumulus", "altocumulus", "cumulus_congestus"):
            self.assertIn(b"texture(", original[f"shaders/include/sky/clouds/{layer}.glsl"])
        properties = original["shaders/shaders.properties"].decode()
        self.assertIn("texture.noise = image/noise.png", properties)
        self.assertIn("texture.deferred.colortex6.1 = image/worley_bubbly.dat TEXTURE_3D R8", properties)
        self.assertIn("texture.deferred.colortex7.1 = image/worley_swirley.dat TEXTURE_3D R8", properties)
        for name in ("noise.png", "worley_bubbly.dat", "worley_swirley.dat"):
            filtering = json.loads(original[f"shaders/image/{name}.mcmeta"])["texture"]
            self.assertEqual({"blur": True, "clamp": False}, filtering)
        self.assertEqual("incomplete", MANIFEST["initial_safety_finding"]["status"])
        self.assertTrue(MANIFEST["initial_safety_finding"]["enabled_archive_blocked"])
        self.assertEqual("controlled_profile_only", MANIFEST["sampling_safety"]["status"])
        self.assertFalse(MANIFEST["sampling_safety"]["enabled_archive_blocked"])
        self.assertEqual(probe.SAMPLER_EVIDENCE_SHA256, MANIFEST["sampling_safety"]["evidence_sha256"])
        prior = MANIFEST["prior_qualifications"][0]
        self.assertEqual("dev/logs/cloud-sampler-state-diagnostic-r2.json", prior["evidence_path"])
        self.assertEqual(1488, prior["context_correction"]["framebuffer_width"])
        self.assertEqual(836, prior["context_correction"]["framebuffer_height"])


class SamplerConstraintTests(unittest.TestCase):
    def report(self):
        base = {"status": "queried_without_gl_error_consumption", "min_filter": 9729,
                "mag_filter": 9729, "min_lod": 0.0, "max_lod": 0.0, "lod_bias": 0.0,
                "max_anisotropy": 1.0, "base_level": 0, "max_level": 0}
        samplers = {}
        for name in ("noisetex", "colortex8", "customtex0", "customtex1", "customtex2"):
            parameters = base | ({"min_lod": -1000.0, "max_lod": 1000.0, "max_level": 1000}
                                 if name == "colortex8" else {})
            volume = name.startswith("customtex")
            samplers[name] = {"active_uniform_type": 35679 if volume else 35678,
                              "texture_target": 32879 if volume else 3553,
                              "bound_texture": 51, "bound_sampler": 0,
                              "effective_parameter_source": "texture; sampler object zero",
                              "texture_parameters": parameters.copy(),
                              "effective_sampling_parameters": parameters.copy()}
        return {"schema": 1, "probe": "shaderlod.dev.cloudSamplerProbe",
                "hook": "net.irisshaders.iris.pathways.FullScreenQuadRenderer.renderQuad()V HEAD",
                "pinned_iris": "1.8.12+1.21.1-neoforge", "gl_renderer": "Apple M3 Max",
                "gl_version": "4.1 Metal - 91.7", "anisotropy_supported": True,
                "sampler_objects_supported": True,
                "programs": [{"current_program": index, "linked": True,
                              "uniform_scan_truncated": False, "cloud_worley_sampler_candidate": True,
                              "active_texture_before": 33984, "active_texture_after": 33984,
                              "samplers": copy.deepcopy(samplers)} for index in (17, 18)]}

    def test_nonmipmap_coverage_with_broad_level_bounds_is_reviewed_constraint(self):
        report = self.report()
        # The real probe also flags an atmosphere-LUT-only draw; it is not the
        # complete cloud-input candidate and must not be assigned a stage name.
        report["programs"].append({"current_program": 21, "cloud_worley_sampler_candidate": True,
                                   "active_texture_before": 33984, "active_texture_after": 33984,
                                   "samplers": {"customtex0": report["programs"][0]["samplers"]["customtex0"]}})
        summary = probe.validate_sampler_report(report)
        self.assertEqual([17, 18], summary["candidate_program_ids"])
        self.assertIn("no derivative/parity guarantee", summary["scope"])
        self.assertNotIn("report_sha256", summary)
        self.assertIn("no verified hash/resolution/profile/stage", summary["scope"])

    def test_changed_mipmap_filter_anisotropy_binding_or_unknown_sampler_is_rejected(self):
        for name, key, value in (("colortex8", "min_filter", 9987),
                                 ("customtex1", "max_anisotropy", 2.0),
                                 ("noisetex", "max_level", 1)):
            report = self.report()
            report["programs"][0]["samplers"][name]["effective_sampling_parameters"][key] = value
            with self.subTest(name=name, key=key), self.assertRaisesRegex(ValueError, "constraint"):
                probe.validate_sampler_report(report)
        for key, value in (("bound_sampler", 2), ("bound_texture", 0), ("active_uniform_type", None)):
            report = self.report()
            report["programs"][0]["samplers"]["customtex0"][key] = value
            with self.assertRaisesRegex(ValueError, "bound sampler"):
                probe.validate_sampler_report(report)

    def test_incomplete_program_scan_restore_or_runtime_is_rejected(self):
        for key, value in (("linked", False), ("uniform_scan_truncated", True),
                           ("active_texture_after", 33985)):
            report = self.report()
            report["programs"][0][key] = value
            with self.assertRaises(ValueError):
                probe.validate_sampler_report(report)
        report = self.report()
        report["programs"].pop()
        with self.assertRaisesRegex(ValueError, "two distinct"):
            probe.validate_sampler_report(report)
        report = self.report()
        report["pinned_iris"] = "other version"
        with self.assertRaisesRegex(ValueError, "reviewed runtime"):
            probe.validate_sampler_report(report)

    @unittest.skipUnless((ROOT / probe.SAMPLER_EVIDENCE).exists(), "Reviewed runtime report unavailable")
    def test_exact_preserved_native_r3_report_has_computed_verified_hash(self):
        result = probe.load_sampler_evidence(ROOT / probe.SAMPLER_EVIDENCE)
        actual = probe.sha256((ROOT / probe.SAMPLER_EVIDENCE).read_bytes())
        self.assertEqual(probe.SAMPLER_EVIDENCE_SHA256, actual)
        self.assertEqual(actual, result["report_sha256"])
        self.assertEqual([17, 18], result["candidate_program_ids"])

    @unittest.skipUnless((ROOT / "dev/logs/cloud-sampler-state-diagnostic-r2.json").exists(), "Prior r2 report unavailable")
    def test_r2_can_satisfy_constraints_without_qualifying_as_native_r3_evidence(self):
        path = ROOT / "dev/logs/cloud-sampler-state-diagnostic-r2.json"
        summary = probe.validate_sampler_report(json.loads(path.read_bytes()))
        self.assertEqual([17, 18], summary["candidate_program_ids"])
        self.assertNotIn("report_sha256", summary)
        with self.assertRaisesRegex(ValueError, "evidence changed"):
            probe.load_sampler_evidence(path)

    @unittest.skipUnless((ROOT / MANIFEST["sampling_safety"]["workload_evidence_path"]).exists(), "Native r3 workload unavailable")
    def test_native_workload_dimensions_and_source_vs_diagnostic_profile_match_qualifier(self):
        safety = MANIFEST["sampling_safety"]
        raw = (ROOT / safety["workload_evidence_path"]).read_bytes()
        self.assertEqual(safety["workload_evidence_sha256"], probe.sha256(raw))
        workload = json.loads(raw)
        context = safety["context"]
        for state in (workload, workload["input_state_start"], workload["input_state_end"]):
            self.assertEqual(context["framebuffer_width"], state["framebuffer_width"])
            self.assertEqual(context["framebuffer_height"], state["framebuffer_height"])
        source = Path(workload["workload-manifest.json"]["source_profile"])
        self.assertEqual(ROOT / context["source_profile"], source)
        self.assertEqual("run-workloads/cloud-sampler-state-probe/forest/secondary-base", context["diagnostic_profile"])
        self.assertNotEqual(context["source_profile"], context["diagnostic_profile"])
        self.assertEqual("secondary-base", workload["profile"])
        self.assertEqual(probe.BASE_SHA256, workload["actual_shader_sha256"])
        self.assertEqual("true", workload["effective_shader_options"]["CLOUDS_CUMULUS_PRECOMPUTE_LOCAL_COVERAGE"])
        self.assertTrue(workload["dhPresent"])
        self.assertEqual("minimal", workload["runtime_properties"]["shaderlod.dev.performanceStack"])
        self.assertEqual("false", workload["runtime_properties"]["shaderlod.dev.gpuTiming"])
        self.assertEqual([], workload["gpu_samples"])
        observed = workload["window_state"]["capture"]["start"]["state"]
        self.assertEqual(context["logical_window_width"], observed["window_width"])
        self.assertEqual(context["logical_window_height"], observed["window_height"])


if __name__ == "__main__":
    unittest.main()
