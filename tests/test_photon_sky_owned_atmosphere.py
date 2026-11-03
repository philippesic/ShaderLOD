"""Verify the isolated real helper, fallback source and affected GLSL programs."""
import copy
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import build_photon_sky_owned_atmosphere as pilot
import build_photon_live_pixel_budget as current


def flatten(files, name, options):
    source = files[name].decode()
    if name == "shaders/settings.glsl":
        for macro, enabled in options.items():
            pattern = rf"^[ \t]*(?://)?#define {macro}(?=[ \t]|$)[^\n]*"
            match = re.search(pattern, source, flags=re.M)
            if match is None:
                raise AssertionError("Missing actual setting " + macro)
            value = f"#define {macro}" if enabled else f"//#define {macro}"
            source = source[:match.start()] + value + source[match.end():]
    return re.sub(r'^\s*#include "([^\"]+)"\s*$', lambda m: flatten(
        files, "shaders" + m[1] if m[1].startswith("/") else str(PurePosixPath(name).parent / m[1]), options),
        source, flags=re.M)


class SkyOwnedAtmosphereTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(pilot.MANIFEST.read_text())
        cls.current_manifest = json.loads((ROOT/current.MANIFEST).read_text())
        cls.original = current.archive_members(ROOT/current.BASE)
        cls.candidate = current.patch_members(cls.original,cls.current_manifest,'enabled')

    def test_scope_and_global_source_are_exact(self):
        self.assertEqual(current.BASE_SHA256,current.sha256((ROOT/current.BASE).read_bytes()))
        self.assertEqual(set(current.MEMBERS),{n for n in self.original if self.original[n] != self.candidate[n]})
        self.assertIn(pilot.HELPER,self.candidate.keys() - self.original.keys())
        self.assertEqual(self.original["LICENSE"], self.candidate["LICENSE"])
        self.assertEqual(self.original["shaders/include/sky/atmosphere.glsl"],
                         self.candidate["shaders/include/sky/atmosphere.glsl"])

    def test_local_helper_changes_only_name_and_four_explicit_lod_reads(self):
        original = self.original["shaders/include/sky/atmosphere.glsl"].decode()
        start = original.index("vec3 atmosphere_scattering(\n    vec3 ray_dir,\n    vec3 sun_color,")
        end = original.index("\n}\n", start) + 3
        original = original[start:end]
        self.assertEqual(self.manifest["original_helper"]["function_sha256"], pilot.sha256(original.encode()))
        local = self.candidate[pilot.HELPER].decode()
        start = local.index("vec3 shaderlod_sky_owned_atmosphere(")
        end = local.index("\n}\n", start) + 3
        local = local[start:end]
        self.assertEqual(4, local.count("textureLod(ATMOSPHERE_SCATTERING_LUT,"))
        self.assertNotIn("texture(", local)
        local = local.replace("vec3 shaderlod_sky_owned_atmosphere(", "vec3 atmosphere_scattering(", 1)
        for uv in ("uv_sc", "uv_sm", "uv_mc", "uv_mm"):
            local = local.replace(f"textureLod(ATMOSPHERE_SCATTERING_LUT, {uv}, 0.0)",
                                  f"texture(ATMOSPHERE_SCATTERING_LUT, {uv})", 1)
        self.assertEqual(original, local)

    def test_d4_reversible_anchors_preserve_every_other_operation(self):
        source = self.candidate[pilot.D4].decode()
        self.assertNotIn("shaderlod_enabled", source)
        for replacement in reversed(next(e for e in self.current_manifest['members'] if e['path']==pilot.D4)['replacements']):
            self.assertEqual(1, source.count(replacement["after"]))
            source = source.replace(replacement["after"], replacement["before"], 1)
        self.assertEqual(self.original[pilot.D4].decode(), source)

    def test_source_and_scope_drift_refuse(self):
        for name in (pilot.D4,):
            with self.assertRaises(ValueError):
                current.patch_members(self.original|{name:self.original[name]+b'changed'},self.current_manifest,'enabled')
        with tempfile.TemporaryDirectory() as directory:
            archive=Path(directory)/'mutated.zip'
            with zipfile.ZipFile(archive,'w') as output:
                for name,data in self.original.items():
                    output.writestr(name,data+(b'changed' if name=='shaders/include/sky/atmosphere.glsl' else b''))
            with self.assertRaisesRegex(ValueError,'Pinned original archive'):
                current.build(ROOT/'build/live-pixel-budget-source-mutation.zip',base=archive)
        altered=copy.deepcopy(self.current_manifest)
        next(e for e in altered['members'] if e['path']==pilot.D4)['replacements'][0]['before']='missing'
        with self.assertRaises(ValueError):current.patch_members(self.original,altered,'enabled')

    @unittest.skipUnless(shutil.which("glslangValidator"), "glslangValidator unavailable; runtime still required")
    def test_current_integrated_affected_programs_and_excluded_contexts_compile(self):
        variants = [("world0", border, blocky, False) for border, blocky in
                    ((False, False), (True, False), (False, True), (True, True))]
        variants += [("world0", False, False, True), ("world1", False, False, False),
                     ("world-1", False, False, False)]
        with tempfile.TemporaryDirectory() as directory:
            for world, border, blocky, dh in variants:
                name = f"{world}-border{int(border)}-blocky{int(blocky)}-dh{int(dh)}"
                with self.subTest(case=name):
                    options = {"BORDER_FOG": border, "BLOCKY_CLOUDS": blocky}
                    defines = "#define MC_VERSION 12101\n#define MC_SHADOW_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n#define IS_IRIS\n#define IRIS_FEATURE_SEPARATE_ENTITY_DRAWS\n"
                    if dh:
                        defines += "#define DISTANT_HORIZONS\n"
                    def write(files, suffix):
                        source = flatten(files, f"shaders/{world}/deferred4.fsh", options)
                        source = source.replace("#version 400 compatibility", "#version 400 compatibility\n" + defines, 1).replace("\\\n", "")
                        path = Path(directory) / (name + suffix + ".fsh")
                        path.write_text(source)
                        return str(path)
                    candidate_path = write(self.candidate, "-candidate")
                    result = subprocess.run(["glslangValidator", "-S", "frag", candidate_path], capture_output=True, text=True)
                    self.assertEqual(0, result.returncode, result.stdout + result.stderr)



if __name__ == "__main__":
    unittest.main()
