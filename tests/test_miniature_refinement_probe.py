"""Pure source/eligibility/evidence and tiny packaging fixtures; no real ZIP build."""
import copy
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from build_miniature_refinement_probe import apply_patches, build, validate_qualification
from build_miniature_bridge_probe import read_archive


class MiniatureRefinementTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((ROOT / "patches/miniature/2.19-refinement-probe.json").read_text())
        self.helper = (ROOT / "shaderpacks/miniature-refinement-probe/shaders/common/shaderlodRefinement.glsl").read_text()

    def evidence(self, manifest):
        q = manifest["qualification"]
        samplers = {}
        for name, filters in q["min_mag_filter"].items():
            samplers[name] = {"active_uniform_name": name, "active_uniform_type": 35678,
                              "active_uniform_size": 1, "texture_target": 3553,
                              "bound_sampler": 0, "bound_texture": 42,
                              "effective_sampling_parameters": {
                                  "status": "queried_without_gl_error_consumption",
                                  "min_filter": filters, "mag_filter": filters, "lod_bias": 0.0,
                                  "max_anisotropy": 1.0, "base_level": 0, "max_level": 1000,
                                  "min_lod": -1000.0, "max_lod": 1000.0}}
        sampler = {"programs": [{"current_program": q["program"], "linked": True,
                                  "uniform_scan_truncated": False, "active_texture_before": 33984,
                                  "active_texture_after": 33984, "samplers": samplers}]}
        state = {"framebuffer_width": q["framebuffer"][0], "framebuffer_height": q["framebuffer"][1],
                 "shaders_in_use": True, "fallback": False, "dimension": "minecraft:overworld"}
        workload = {"status": "complete", "dhPresent": False, "modPresent": False,
                    "actual_shader_sha256": manifest["archive_hashes"]["sha256"],
                    "request": {"stack": "minimal", "gpu_queries": True},
                    "runtime_properties": {"shaderlod.dev.gpuTiming": "false"}, "gpu_samples": [],
                    "effective_shader_options": q["native_profile_options"].copy(),
                    "loaded_mod_artifacts": {"iris": {"sha256": q["iris_sha256"]}},
                    "input_state_start": state.copy(), "input_state_end": state.copy()}
        return sampler, workload

    def fixture(self, root):
        m = copy.deepcopy(self.manifest)
        files = {name: b"untouched fixture\r\n" for name in m["source_sha256"]}
        for name in {p["path"] for p in m["patches"]}:
            files[name] = "\r\nfixture separator\r\n".join(p["before"] for p in m["patches"] if p["path"] == name).encode()
        files["shaders/unchanged.fsh"] = b"unchanged\r\n"
        base = root / "base.zip"
        with zipfile.ZipFile(base, "w") as z:
            for name, content in files.items():
                z.writestr(name, content)
        m["archive_hashes"] = {a: hashlib.new(a, base.read_bytes()).hexdigest() for a in m["archive_hashes"]}
        m["source_sha256"] = {name: hashlib.sha256(files[name]).hexdigest() for name in m["source_sha256"]}
        q = m["qualification"]
        iris = root / q["iris_path"]
        iris.parent.mkdir(parents=True)
        with zipfile.ZipFile(iris, "w") as z:
            for name in q["translator_class_sha256"]:
                content = ("fixture " + name).encode()
                z.writestr(name, content)
                q["translator_class_sha256"][name] = hashlib.sha256(content).hexdigest()
        q["iris_sha256"] = hashlib.sha256(iris.read_bytes()).hexdigest()
        sampler, workload = self.evidence(m)
        for key, value in (("sampler", sampler), ("workload", workload)):
            path = root / q[key + "_path"]
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(value))
            q[key + "_sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        (root / "patches/miniature").mkdir(parents=True)
        (root / "patches/miniature/2.19-refinement-probe.json").write_text(json.dumps(m))
        shutil.copy(ROOT / "patches/miniature/LICENSE.Miniature", root / "patches/miniature/LICENSE.Miniature")
        (root / "dev/shader-candidates.lock.json").write_text(json.dumps({"candidates": [{
            "modrinth_project": "miniature-shader", "release": {
                "version": "2.19", "filename": "base.zip", **m["archive_hashes"]}}]}))
        (root / "docs").mkdir()
        (root / "docs/MINIATURE_REFINEMENT_PROBE.md").write_text("fixture contract")
        shutil.copytree(ROOT / "shaderpacks/miniature-refinement-probe", root / "shaderpacks/miniature-refinement-probe")
        return base, files, m

    def test_literal_kernels_differ_only_budget_and_explicit_fetches(self):
        normalized = []
        for count in (4, 3, 2):
            start = self.helper.index(f"vec4 shaderlodMiniatureExplicit{count}(")
            end = self.helper.index("\n}\n", start) + 2
            body = self.helper[start:end]
            self.assertIn(f"j < {count}", body)
            self.assertNotIn("texture2D(", body)
            self.assertEqual(body.count("texture2DLod(depthtex0, curUV, 0.0)"), 2)
            self.assertEqual(body.count("texture2DLod(colortex0, curUV, 0.0)"), 1)
            restored = body.replace(f"shaderlodMiniatureExplicit{count}", "getReflectionColor").replace(
                f"j < {count}", "j < SSR_BINARY_STEPS").replace(
                "texture2DLod(depthtex0, curUV, 0.0)", "texture2D(depthtex0, curUV)").replace(
                "texture2DLod(colortex0, curUV, 0.0)", "texture2D(colortex0, curUV)")
            normalized.append(restored)
        self.assertEqual(normalized[0], normalized[1])
        self.assertEqual(normalized[0], normalized[2])
        source = ROOT / "dev/upstream/other-pack-research/miniature-release/shaders/common/getReflectionColor.fsh"
        if source.exists():
            original = source.read_bytes().decode().replace("\r\n", "\n")
            self.assertEqual(normalized[0], original[original.index("vec4 getReflectionColor("):])

    def test_source_eligibility_and_native_count_boundary_fixtures(self):
        include = next(p for p in self.manifest["patches"] if p["path"] == "shaders/program/final.fsh")["after"]
        expression = re.search(r"#if (.+)\r\n", include).group(1)
        for mode in (0, 1, 2, 3):
            for binary in range(1, 11):
                for outer in (10, 16):
                    terms = {"SHADERLOD_MINIATURE_REFINE_MODE": mode, "SSR_BINARY_STEPS": binary, "SSR_MAX_STEPS": outer}
                    predicate = expression
                    for name, value in terms.items():
                        predicate = predicate.replace(name, str(value))
                    predicate = predicate.replace("&&", "and").replace("||", "or")
                    self.assertRegex(predicate, r"^[0-9() =andor]+$")
                    self.assertEqual(eval(predicate, {"__builtins__": {}}, {}), mode in (1, 2) and binary == 4 and outer == 10)
        dispatch = self.helper[self.helper.index("vec4 shaderlodMiniatureRefinement("):]
        self.assertIn("shaderlodBridgeVersion != 2 || shaderlodEnabled == 0 || SSR_STEP_SIZE != 1.6", dispatch)
        rejection = re.search(r"if \((shaderlodBridgeVersion.+?)\) \{", dispatch).group(1)
        for version in (-1, 0, 1, 2, 3):
            for enabled in (0, 1):
                for stride in (1.2, 1.6):
                    predicate = rejection.replace("shaderlodBridgeVersion", str(version)).replace("shaderlodEnabled", str(enabled)).replace("SSR_STEP_SIZE", str(stride)).replace("||", "or")
                    self.assertRegex(predicate, r"^[0-9. !=or-]+$")
                    self.assertEqual(eval(predicate, {"__builtins__": {}}, {}), version != 2 or enabled == 0 or stride != 1.6)
        self.assertLess(dispatch.index("return getReflectionColor(depth, normal, viewPos)"), dispatch.index("float nearBlocks"))
        self.assertIn("length(viewPos)", dispatch)
        self.assertIn("farBlocks = max(shaderlodFar, nearBlocks + 1.0)", dispatch)
        self.assertIn("radialBlocks <= nearBlocks", dispatch)
        self.assertIn("radialBlocks >= farBlocks", dispatch)
        self.assertNotIn("mix(", dispatch)
        for absent in ("shaderlodPreset", "shaderlodTransition", "shaderlodDebug", "length(feetPos)"):
            self.assertNotIn(absent, self.helper)

    def test_probe_off_exact_source_projection_and_unchanged_members(self):
        with tempfile.TemporaryDirectory() as directory:
            _, files, m = self.fixture(Path(directory))
            result = apply_patches(files, m)
            for name in files:
                if name not in {p["path"] for p in m["patches"]}:
                    self.assertEqual(result[name], files[name])
            projected = result.copy()
            for p in reversed(m["patches"]):
                if p["path"] not in ("shaders/program/final.fsh", "shaders/shader.h"):
                    continue
                if p["path"] == "shaders/program/final.fsh":
                    self.assertIn("SSR_BINARY_STEPS == 4 && SSR_MAX_STEPS == 10", p["after"])
                    self.assertIn(p["before"], p["after"])
                else:
                    self.assertIn("#define SHADERLOD_MINIATURE_REFINE_MODE 0", p["after"])
                projected[p["path"]] = projected[p["path"]].replace(p["after"].encode(), p["before"].encode(), 1)
            self.assertEqual(projected["shaders/program/final.fsh"], files["shaders/program/final.fsh"])
            self.assertEqual(projected["shaders/shader.h"], files["shaders/shader.h"])

    def test_evidence_state_rejections(self):
        sampler, workload = self.evidence(self.manifest)
        validate_qualification(sampler, workload, self.manifest)
        mutations = [lambda w: w.update(dhPresent=True), lambda w: w.update(modPresent=True),
                     lambda w: w["effective_shader_options"].update(SSR_BINARY_STEPS="2"),
                     lambda w: w["runtime_properties"].update({"shaderlod.dev.gpuTiming": "true"}),
                     lambda w: w.update(gpu_samples=[{}]),
                     lambda w: w["input_state_end"].update(fallback=True),
                     lambda w: w["input_state_start"].update(framebuffer_width=1920),
                     lambda w: w["loaded_mod_artifacts"]["iris"].update(sha256="different")]
        for mutate in mutations:
            changed = copy.deepcopy(workload)
            mutate(changed)
            with self.assertRaises(ValueError):
                validate_qualification(sampler, changed, self.manifest)
        for field, value in (("min_filter", 9987), ("lod_bias", 1.0), ("max_anisotropy", 4.0), ("base_level", 1)):
            changed = copy.deepcopy(sampler)
            changed["programs"][0]["samplers"]["colortex0"]["effective_sampling_parameters"][field] = value
            with self.assertRaisesRegex(ValueError, "sampler state"):
                validate_qualification(changed, workload, self.manifest)
        changed = copy.deepcopy(sampler)
        changed["programs"][0]["active_texture_after"] = 33985
        with self.assertRaisesRegex(ValueError, "restore active"):
            validate_qualification(changed, workload, self.manifest)

    def test_fixture_deterministic_packaging_and_no_runtime_claim(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, original, _ = self.fixture(root)
            first, second = root / "first.zip", root / "second.zip"
            meta = build(first, base, root)
            self.assertEqual(meta, build(second, base, root))
            self.assertEqual(first.read_bytes(), second.read_bytes())
            files = read_archive(first)
            self.assertEqual(files["shaders/common/getReflectionColor.fsh"], original["shaders/common/getReflectionColor.fsh"])
            self.assertEqual(files["LICENSE.Miniature"], (ROOT / "patches/miniature/LICENSE.Miniature").read_bytes())
            self.assertEqual(meta["default_mode"], 0)
            self.assertTrue(meta["validation"]["source_qualified"])
            self.assertFalse(any(value for key, value in meta["validation"].items() if key != "source_qualified"))

    def test_fail_closed_evidence_overlay_archive_and_source_guards(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, files, m = self.fixture(root)
            with self.assertRaisesRegex(ValueError, "overwrite"):
                build(base, base, root)
            path = root / m["qualification"]["sampler_path"]
            previous = path.read_bytes()
            path.write_bytes(previous + b"changed")
            with self.assertRaisesRegex(ValueError, "evidence changed"):
                build(root / "out.zip", base, root)
            path.write_bytes(previous)
            helper = root / "shaderpacks/miniature-refinement-probe/shaders/common/shaderlodRefinement.glsl"
            previous = helper.read_bytes()
            helper.write_bytes(previous + b"changed")
            with self.assertRaisesRegex(ValueError, "overlay changed"):
                build(root / "out.zip", base, root)
            helper.write_bytes(previous)
            base.write_bytes(base.read_bytes() + b"changed")
            with self.assertRaisesRegex(ValueError, "release sha256 mismatch"):
                build(root / "out.zip", base, root)
            changed = files.copy()
            changed["shaders/program/final.fsh"] += b"changed"
            with self.assertRaisesRegex(ValueError, "source changed"):
                apply_patches(changed, m)
            m["patches"][0]["before"] = ""
            with self.assertRaisesRegex(ValueError, "exactly once"):
                apply_patches(files, m)

    def test_actual_pinned_evidence_translator_and_release_readonly(self):
        q = self.manifest["qualification"]
        for key in ("sampler", "workload", "iris"):
            path = ROOT / q[key + "_path"]
            if not path.exists():
                self.skipTest("Ignored qualification artifact unavailable")
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), q[key + "_sha256"])
        validate_qualification(json.loads((ROOT / q["sampler_path"]).read_bytes()),
                               json.loads((ROOT / q["workload_path"]).read_bytes()), self.manifest)
        with zipfile.ZipFile(ROOT / q["iris_path"]) as z:
            for name, expected in q["translator_class_sha256"].items():
                self.assertEqual(hashlib.sha256(z.read(name)).hexdigest(), expected)
        base = ROOT / "dev/upstream/other-pack-research/miniature-shader-2.19.zip"
        if not base.exists():
            self.skipTest("Ignored original release unavailable")
        original = read_archive(base)
        result = apply_patches(original, self.manifest)
        self.assertEqual({name for name in original if result[name] != original[name]}, {p["path"] for p in self.manifest["patches"]})
        self.assertEqual(result["shaders/common/getReflectionColor.fsh"], original["shaders/common/getReflectionColor.fsh"])
        before, after = original["shaders/program/final.fsh"].decode(), result["shaders/program/final.fsh"].decode()
        self.assertEqual(before[before.index("      color.rgb = mix("):], after[after.index("      color.rgb = mix("):])
        self.assertEqual(before.count("texture2D("), after.count("texture2D("))


if __name__ == "__main__":
    unittest.main()
