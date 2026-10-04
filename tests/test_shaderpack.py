"""Verify pinned packaging and compile the actual patched fragment programs."""
import importlib.util
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("shaderpack_builder", ROOT / "scripts/build_shaderpack.py")
BUILDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILDER)


class ShaderpackTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        cls.output = Path(cls.directory.name) / "Photon-ShaderLOD.zip"
        cls.metadata = BUILDER.build(cls.output)
        with zipfile.ZipFile(cls.output) as archive:
            cls.files = {name: archive.read(name) for name in archive.namelist()}
        cls.manifest = json.loads((ROOT / "patches/photon/v1.3b.json").read_text())
        with zipfile.ZipFile(ROOT / "dev/upstream/photon_v1.3b.zip") as archive:
            cls.upstream = {entry.filename: archive.read(entry.filename) for entry in archive.infolist() if not entry.is_dir()}

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    def test_reproducible_archive(self):
        second = Path(self.directory.name) / "second.zip"
        BUILDER.build(second)
        self.assertEqual(self.output.read_bytes(), second.read_bytes())

    def test_upstream_preserved_outside_explicit_targets(self):
        targets = {patch["path"] for patch in self.manifest["patches"]}
        changed = {name for name, data in self.upstream.items() if data != self.files[name]}
        self.assertEqual(targets, changed)
        for name in self.upstream.keys() - targets:
            with self.subTest(path=name):
                self.assertEqual(self.upstream[name], self.files[name])
        self.assertTrue(any("/dh_" in name for name in self.upstream))
        self.assertEqual(self.files["LICENSE"], self.upstream["LICENSE"])

    def test_corrupt_base_rejected_without_output(self):
        damaged = Path(self.directory.name) / "damaged.zip"
        damaged.write_bytes(b"not the pinned Photon archive")
        output = Path(self.directory.name) / "should-not-exist.zip"
        with self.assertRaisesRegex(ValueError, "mismatch"):
            BUILDER.build(output, damaged)
        self.assertFalse(output.exists())

    def test_modified_upstream_source_rejected(self):
        files = self.upstream.copy()
        target = self.manifest["patches"][0]["path"]
        files[target] += b"\n// upstream changed\n"
        with self.assertRaisesRegex(ValueError, "source changed"):
            BUILDER.apply_patches(files, self.manifest)

    def test_missing_and_ambiguous_anchors_rejected(self):
        for anchor in ("this anchor does not exist", "#"):
            manifest = json.loads(json.dumps(self.manifest))
            manifest["patches"][0]["before"] = anchor
            with self.subTest(anchor=anchor), self.assertRaisesRegex(ValueError, "exactly once"):
                BUILDER.apply_patches(self.upstream, manifest)

    def test_cloud_candidate_options_are_legal_iris_values(self):
        source = self.files["shaders/settings.glsl"].decode()
        for macro, required in (("SHADERLOD_CLOUD_NEAR", 128.0),
                                ("SHADERLOD_CLOUD_FAR", 1024.0),
                                ("SHADERLOD_CLOUD_TRANSITION", 64.0),
                                ("SHADERLOD_WATER_NEAR", 96.0),
                                ("SHADERLOD_WATER_FAR", 256.0),
                                ("SHADERLOD_WATER_TRANSITION", 32.0)):
            option = re.search(rf"#define {macro} ([0-9.]+) // \[([^\]]+)\]", source)
            self.assertIsNotNone(option, macro)
            self.assertIn(required, [float(value) for value in option.group(2).split()], macro)
        defaults = dict(re.findall(r"#define (SHADERLOD_CLOUD_\w+) ([0-9.]+)", source))
        self.assertEqual("128.0", defaults["SHADERLOD_CLOUD_NEAR"])
        self.assertEqual("1024.0", defaults["SHADERLOD_CLOUD_FAR"])
        self.assertEqual("64.0", defaults["SHADERLOD_CLOUD_TRANSITION"])

    @unittest.skipUnless(shutil.which("c++"), "C++ compiler unavailable for isolated shader math checks")
    def test_actual_cloud_helper_distance_boundaries(self):
        # Execute the actual GLSL helper bodies on CPU with equivalent scalar
        # builtins/vec3 fields. This tests tier math; it is not GPU rendering.
        helper = self.files["shaders/include/shaderlod/lod.glsl"].decode()
        def function(name):
            match = re.search(rf"(?:bool|int) {name}\([^)]*\) \{{", helper)
            self.assertIsNotNone(match, name)
            end = match.end()
            depth = 1
            while depth:
                depth += (helper[end] == "{") - (helper[end] == "}")
                end += 1
            return helper[match.start():end]
        functions = "\n".join(function(name) for name in (
            "shaderlod_enabled", "shaderlod_tier_with_distances", "shaderlod_cloud_tier"))
        wrappers = """
#include <cmath>
struct vec3 { float x,y,z; vec3(float a,float b,float c):x(a),y(b),z(c){} };
float max(float a,float b){ return a>b?a:b; }
float min(float a,float b){ return a<b?a:b; }
float clamp(float x,float a,float b){ return min(max(x,a),b); }
float smoothstep(float a,float b,float x){ float t=clamp((x-a)/(b-a),0.f,1.f); return t*t*(3.f-2.f*t); }
"""
        fixtures = (
            (128, 1024, 64, True, ((89, 0, 0), (89, .99, 0), (96, .99, 0),
                (128, .25, 1), (128, .75, 0), (160, .99, 1),
                (992, .99, 1), (1024, .25, 2), (1024, .75, 1), (1056, .99, 2))),
            # Oversized width clamps to twice near; boundaries start at zero.
            (64, 256, 512, True, ((-1, .5, 0), (0, .5, 0), (64, .25, 1),
                (64, .75, 0), (128, .99, 1), (320, .99, 2))),
            # Inverted options are sanitized and have nonoverlapping bands.
            (1024, 256, 512, True, ((1000, .5, 0), (1024, .25, 1),
                (1024, .75, 0), (1025.5, .99, 2))),
            (128, 1024, 0, True, ((127, .5, 0), (128, .5, 1), (1024, .5, 2))),
            (128, 1024, 64, False, ((89, .5, 0), (200, .5, 0), (5000, .5, 0))),
        )
        for index, (near, far, band, enabled, samples) in enumerate(fixtures):
            with self.subTest(near=near, far=far, band=band, enabled=enabled):
                definitions = ("#define SHADERLOD_ENABLED\n" if enabled else "") + (
                    f"#define SHADERLOD_CLOUD_NEAR {float(near)}\n"
                    f"#define SHADERLOD_CLOUD_FAR {float(far)}\n"
                    f"#define SHADERLOD_CLOUD_TRANSITION {float(band)}\n")
                checks = "\n".join(f"if(shaderlod_cloud_tier({distance}, {noise}) != {expected}) return {i+1};"
                                     for i, (distance, noise, expected) in enumerate(samples))
                path = Path(self.directory.name) / f"cloud-helper-{index}.cpp"
                executable = path.with_suffix("")
                path.write_text(definitions + wrappers + functions + "\nint main(){\n" + checks + "\nreturn 0;}\n")
                result = subprocess.run(["c++", "-std=c++17", str(path), "-o", str(executable)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                executed = subprocess.run([str(executable)], capture_output=True, text=True)
                self.assertEqual(0, executed.returncode, f"Cloud boundary check {executed.returncode} failed")

    @unittest.skipUnless(shutil.which("c++"), "C++ compiler unavailable for isolated shader math checks")
    def test_actual_water_helper_distance_boundaries(self):
        # Execute the actual GLSL helper bodies on CPU with equivalent scalar
        # builtins/vec3 fields. This tests tier math; it is not GPU rendering.
        helper = self.files["shaders/include/shaderlod/lod.glsl"].decode()
        def function(name):
            match = re.search(rf"(?:bool|int) {name}\([^)]*\) \{{", helper)
            self.assertIsNotNone(match, name)
            end = match.end()
            depth = 1
            while depth:
                depth += (helper[end] == "{") - (helper[end] == "}")
                end += 1
            return helper[match.start():end]
        functions = "\n".join(function(name) for name in (
            "shaderlod_enabled", "shaderlod_tier_with_distances", "shaderlod_water_tier"))
        wrappers = """
#include <cmath>
struct vec3 { float x,y,z; vec3(float a,float b,float c):x(a),y(b),z(c){} };
float max(float a,float b){ return a>b?a:b; }
float min(float a,float b){ return a<b?a:b; }
float clamp(float x,float a,float b){ return min(max(x,a),b); }
float smoothstep(float a,float b,float x){ float t=clamp((x-a)/(b-a),0.f,1.f); return t*t*(3.f-2.f*t); }
"""
        fixtures = (
            (96, 256, 32, True, ((79, .99, 0), (80, .99, 0), (96, .25, 1),
                (96, .75, 0), (112, .99, 1), (240, .99, 1), (272, .99, 2))),
            (128, 1024, 64, True, ((89, 0, 0), (89, .99, 0), (96, .99, 0),
                (128, .25, 1), (128, .75, 0), (160, .99, 1),
                (992, .99, 1), (1024, .25, 2), (1024, .75, 1), (1056, .99, 2))),
            # Oversized width clamps to twice near; boundaries start at zero.
            (64, 256, 512, True, ((-1, .5, 0), (0, .5, 0), (64, .25, 1),
                (64, .75, 0), (128, .99, 1), (320, .99, 2))),
            # Inverted options are sanitized and have nonoverlapping bands.
            (1024, 256, 512, True, ((1000, .5, 0), (1024, .25, 1),
                (1024, .75, 0), (1025.5, .99, 2))),
            (128, 1024, 0, True, ((127, .5, 0), (128, .5, 1), (1024, .5, 2))),
            (128, 1024, 64, False, ((89, .5, 0), (200, .5, 0), (5000, .5, 0))),
        )
        for index, (near, far, band, enabled, samples) in enumerate(fixtures):
            with self.subTest(near=near, far=far, band=band, enabled=enabled):
                definitions = ("#define SHADERLOD_ENABLED\n" if enabled else "") + (
                    f"#define SHADERLOD_WATER_NEAR {float(near)}\n"
                    f"#define SHADERLOD_WATER_FAR {float(far)}\n"
                    f"#define SHADERLOD_WATER_TRANSITION {float(band)}\n")
                checks = "\n".join(f"if(shaderlod_water_tier({distance}, {noise}) != {expected}) return {i+1};"
                                     for i, (distance, noise, expected) in enumerate(samples))
                path = Path(self.directory.name) / f"water-helper-{index}.cpp"
                executable = path.with_suffix("")
                path.write_text(definitions + wrappers + functions + "\nint main(){\n" + checks + "\nreturn 0;}\n")
                result = subprocess.run(["c++", "-std=c++17", str(path), "-o", str(executable)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                executed = subprocess.run([str(executable)], capture_output=True, text=True)
                self.assertEqual(0, executed.returncode, f"Water boundary check {executed.returncode} failed")

    @unittest.skipUnless(shutil.which("c++"), "C++ compiler unavailable for isolated wave math checks")
    def test_actual_water_gradient_matches_constant_noise_height_derivative(self):
        # Execute actual patched GLSL function bodies with constant noise and
        # scalar/vector adapters. A finite difference of upstream wave height is
        # an independent derivative oracle; this cannot validate real noise or
        # visual quality, whose ignored texture derivatives remain a tradeoff.
        water = self.files["shaders/include/surface/water_normal.glsl"].decode()
        def function(name):
            match = re.search(rf"(?:float|vec2) {name}\([^)]*\) \{{", water)
            self.assertIsNotNone(match, name)
            end, depth = match.end(), 1
            while depth:
                depth += (water[end] == "{") - (water[end] == "}")
                end += 1
            return water[match.start():end]
        functions = "\n".join(function(name) for name in (
            "gerstner_wave", "get_water_height", "get_water_gradient"))
        functions = functions.replace("float[WATER_WAVE_ITERATIONS] wave_noise;",
                                      "float wave_noise[WATER_WAVE_ITERATIONS];")
        adapters = r"""
#include <cmath>
#include <algorithm>
using std::max; using std::pow; using std::sin; using std::cos; using std::sqrt;
#define float double
using uint = unsigned;
struct mat2 { float a,b,c,d; mat2(float A,float B,float C,float D):a(A),b(B),c(C),d(D){} };
struct vec2 {
    float x,y; vec2(float v):x(v),y(v){} vec2(float X,float Y):x(X),y(Y){}
    vec2 operator+(vec2 b)const{return vec2(x+b.x,y+b.y);}
    vec2 operator*(float v)const{return vec2(x*v,y*v);}
    vec2& operator+=(vec2 b){x+=b.x;y+=b.y;return *this;}
    vec2& operator*=(float v){x*=v;y*=v;return *this;}
    vec2& operator*=(mat2 m){float X=x*m.a+y*m.b; y=x*m.c+y*m.d;x=X;return *this;}
};
vec2 operator*(float a,vec2 b){return b*a;}
float dot(vec2 a,vec2 b){return a.x*b.x+a.y*b.y;}
float sqr(float a){return a*a;}
struct Sample {float y;};
Sample texture(int,vec2){return {.37};}
const int noisetex=0;
const float tau=6.28318530717958647692;
#define WATER_WAVE_FREQUENCY 1.0
#define WATER_WAVE_PERSISTENCE 1.0
#define WATER_WAVE_LACUNARITY 1.0
#define WATER_WAVE_ITERATIONS 8
"""
        checks = r"""
int main(){
    const vec2 coords[] = {vec2(1.2,-3.4),vec2(200.3,100.8),vec2(-8.1,11.3)};
    const vec2 dirs[] = {vec2(1.,0.),vec2(.8,.6)};
    for(int count: {1,2,4,8}) for(vec2 p:coords) for(vec2 d:dirs){
        mat2 rotation(.737,-.676,.676,.737);
        vec2 slope=get_water_gradient(p,d,rotation,17.3,count);
        const float h=1e-5;
        float x=(get_water_height(p+vec2(h,0.),d,rotation,17.3,count)-
                 get_water_height(p+vec2(-h,0.),d,rotation,17.3,count))/(2.*h);
        float y=(get_water_height(p+vec2(0.,h),d,rotation,17.3,count)-
                 get_water_height(p+vec2(0.,-h),d,rotation,17.3,count))/(2.*h);
        if(std::abs(x-slope.x)>1e-5 || std::abs(y-slope.y)>1e-5) return count;
    }
    return 0;
}
"""
        for varying_height in (False, True):
            with self.subTest(height_variation=varying_height):
                path = Path(self.directory.name) / f"water-gradient-{varying_height}.cpp"
                executable = path.with_suffix("")
                defines = "#define WATER_WAVES_HEIGHT_VARIATION\n" if varying_height else ""
                path.write_text(adapters + defines + functions + checks)
                compiled = subprocess.run(["c++", "-std=c++17", str(path), "-o", str(executable)], capture_output=True, text=True)
                self.assertEqual(0, compiled.returncode, compiled.stdout + compiled.stderr)
                result = subprocess.run([str(executable)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, f"Wave derivative check failed for {result.returncode} octaves")

    @unittest.skipUnless(shutil.which("c++"), "C++ compiler unavailable for isolated bridge checks")
    def test_actual_bridge_disable_and_dynamic_budgets(self):
        helper = self.files["shaders/include/shaderlod/lod.glsl"].decode()
        def function(name):
            match = re.search(rf"(?:bool|int) {name}\([^)]*\) \{{", helper)
            self.assertIsNotNone(match, name)
            end, depth = match.end(), 1
            while depth:
                depth += (helper[end] == "{") - (helper[end] == "}")
                end += 1
            return helper[match.start():end]
        functions = "\n".join(function(name) for name in (
            "shaderlod_use_bridge", "shaderlod_enabled", "shaderlod_preset",
            "shaderlod_budget", "shaderlod_tier_with_distances", "shaderlod_water_tier", "shaderlod_needs_atmosphere"))
        adapters = r"""
#include <cmath>
#include <algorithm>
using std::min; using std::max; using std::ceil;
#define float double
struct vec3 {float x,y,z;vec3(float a,float b,float c):x(a),y(b),z(c){}};
float clamp(float x,float a,float b){return min(max(x,a),b);}
int clamp(int x,int a,int b){return min(max(x,a),b);}
float smoothstep(float a,float b,float x){float t=clamp((x-a)/(b-a),0.f,1.f);return t*t*(3.f-2.f*t);}
#define SHADERLOD_USE_MOD_CONFIG
#define SHADERLOD_ENABLED
#define SHADERLOD_PRESET 1
#define SHADERLOD_WATER_NEAR 96.0
#define SHADERLOD_WATER_FAR 256.0
#define SHADERLOD_WATER_TRANSITION 32.0
int shaderlodBridgeVersion=2, shaderlodEnabled=0, shaderlodPreset=0;
"""
        checks = r"""
int main(){
    for(float distance: {0.f,96.f,200.f,500.f}){
        int tier=shaderlod_water_tier(distance,.25);
        if(tier!=0 || shaderlod_budget(8,tier,1)!=8) return 1;
    }
    if(!shaderlod_needs_atmosphere(.4))return 8;
    shaderlodEnabled=1;
    if(!shaderlod_needs_atmosphere(1.))return 9;
#if defined BORDER_FOG || defined BLOCKY_CLOUDS
    if(!shaderlod_needs_atmosphere(.4))return 10;
#else
    if(shaderlod_needs_atmosphere(.4))return 11;
#endif
    int middle[]={6,4,2}, distant[]={4,2,1};
    for(int preset=0;preset<3;++preset){
        shaderlodPreset=preset;
        if(shaderlod_budget(8,shaderlod_water_tier(200.,.25),1)!=middle[preset])return 2;
        if(shaderlod_budget(8,shaderlod_water_tier(500.,.25),1)!=distant[preset])return 3;
        if(shaderlod_budget(8,shaderlod_water_tier(70.,.25),1)!=8)return 4;
        if(shaderlod_budget(0,2,1)!=0)return 5;
    }
    shaderlodBridgeVersion=1; // V1 doesn't carry a preset; use pack fallback.
    shaderlodPreset=0;
    if(shaderlod_budget(8,1,1)!=4)return 6;
    shaderlodBridgeVersion=99; // Unknown bridge leaves shader fallback enabled.
    shaderlodEnabled=0;
    if(!shaderlod_enabled())return 7;
    return 0;
}
"""
        path = Path(self.directory.name) / "water-bridge.cpp"
        executable = path.with_suffix("")
        for options in ("", "#define BORDER_FOG\n", "#define BLOCKY_CLOUDS\n", "#define BORDER_FOG\n#define BLOCKY_CLOUDS\n"):
            with self.subTest(atmosphere_consumers=options):
                path.write_text(adapters + options + functions + checks)
                result = subprocess.run(["c++", "-std=c++17", str(path), "-o", str(executable)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                executed = subprocess.run([str(executable)], capture_output=True, text=True)
                self.assertEqual(0, executed.returncode, f"Dynamic bridge check failed: {executed.returncode}")

    def flatten(self, name, options):
        source = self.files[name].decode()
        if name == "shaders/settings.glsl":
            for macro, enabled in options.items():
                pattern = rf"^[ \t]*(?://)?#define {macro}(?=[ \t]|$)[^\n]*"
                match = re.search(pattern, source, flags=re.M)
                self.assertIsNotNone(match, macro)
                source = source[:match.start()] + (f"\n#define {macro}" if enabled is True else f"\n//#define {macro}" if enabled is False else f"\n#define {macro} {enabled}") + source[match.end():]
        return re.sub(
            r'^\s*#include "([^\"]+)"\s*$',
            lambda match: self.flatten("shaders" + match.group(1) if match.group(1).startswith("/") else str(PurePosixPath(name).parent / match.group(1)), options),
            source,
            flags=re.M,
        )

    @unittest.skipUnless(shutil.which("glslangValidator"), "glslangValidator unavailable; driver compilation still required")
    def test_actual_fragment_programs_compile(self):
        # Iris expands shaderpack includes and joins preprocessor continuations.
        # This verifies GLSL syntax/types; it does not emulate Iris or GPU behavior.
        variants = (
            ("default", {}, ""),
            ("dh", {}, "#define DISTANT_HORIZONS\n"),
            ("cloud_close", {"SHADERLOD_CLOUD_NEAR": 128.0, "SHADERLOD_CLOUD_FAR": 1024.0, "SHADERLOD_CLOUD_TRANSITION": 64.0}, "#define DISTANT_HORIZONS\n"),
            ("water_analytic_off", {"WATER_WAVES": True, "WATER_PARALLAX": True, "SHADERLOD_WATER_ANALYTIC": False}, "#define DISTANT_HORIZONS\n"),
            ("water_no_height_variation", {"WATER_WAVES": True, "WATER_WAVES_HEIGHT_VARIATION": False, "SHADERLOD_USE_MOD_CONFIG": False}, "#define DISTANT_HORIZONS\n"),
            ("coherent_atmosphere", {"BORDER_FOG": False, "BLOCKY_CLOUDS": False}, "#define DISTANT_HORIZONS\n"),
            ("blocky_atmosphere", {"BORDER_FOG": False, "BLOCKY_CLOUDS": True}, "#define DISTANT_HORIZONS\n"),
            ("cloud_inverted", {"SHADERLOD_CLOUD_NEAR": 1024.0, "SHADERLOD_CLOUD_FAR": 256.0, "SHADERLOD_CLOUD_TRANSITION": 512.0}, "#define DISTANT_HORIZONS\n"),
            ("debug", {"SHADERLOD_DEBUG": True}, ""),
            ("legacy_balanced", {"SHADERLOD_PRESERVE_LIGHTING": False, "SHADERLOD_PRESET": 0, "SHADERLOD_USE_MOD_CONFIG": False}, "#define DISTANT_HORIZONS\n"),
            ("legacy_performance", {"SHADERLOD_PRESERVE_LIGHTING": False, "SHADERLOD_PRESET": 1, "SHADERLOD_USE_MOD_CONFIG": False}, "#define DISTANT_HORIZONS\n"),
            ("legacy_aggressive", {"SHADERLOD_PRESERVE_LIGHTING": False, "SHADERLOD_PRESET": 2, "SHADERLOD_USE_MOD_CONFIG": False}, "#define DISTANT_HORIZONS\n"),
            ("standalone", {"SHADERLOD_USE_MOD_CONFIG": False}, ""),
            ("no_shadow", {"SHADOW": False}, ""),
            ("no_pcf", {"SHADOW_PCF": False}, ""),
            ("no_color", {"SHADOW_COLOR": False}, ""),
            ("no_vps", {"SHADOW_VPS": False}, ""),
            ("lod_off", {"SHADERLOD_ENABLED": False, "SHADERLOD_USE_MOD_CONFIG": False, "SHADERLOD_CACHED_SKY": True}, ""),
            ("balanced", {"SHADERLOD_PRESET": 0, "SHADERLOD_USE_MOD_CONFIG": False, "SHADERLOD_CACHED_SKY": True}, "#define DISTANT_HORIZONS\n"),
            ("performance", {"SHADERLOD_PRESET": 1, "SHADERLOD_USE_MOD_CONFIG": False, "SHADERLOD_CACHED_SKY": True}, "#define DISTANT_HORIZONS\n"),
            ("aggressive", {"SHADERLOD_PRESET": 2, "SHADERLOD_USE_MOD_CONFIG": False, "SHADERLOD_CACHED_SKY": True}, "#define DISTANT_HORIZONS\n"),
            ("ssao", {"SHADER_AO": "SHADER_AO_SSAO"}, "#define DISTANT_HORIZONS\n"),
            ("no_ao", {"SHADER_AO": "SHADER_AO_NONE"}, "#define DISTANT_HORIZONS\n"),
            ("materials", {"POM": True, "NORMAL_MAPPING": True, "SPECULAR_MAPPING": True, "POM_SHADOW": True, "WATER_WAVES": True, "WATER_PARALLAX": True}, "#define DISTANT_HORIZONS\n"),
        )
        for world in ("world0", "world1", "world-1"):
            for program in ("deferred4", "gbuffers_water", "deferred3", "deferred1", "deferred", "composite", "gbuffers_terrain", "dh_terrain", "dh_water", "composite1", "shadow"):
                for variant, options, extra in variants:
                    with self.subTest(world=world, program=program, variant=variant):
                        source = self.flatten(f"shaders/{world}/{program}.fsh", options)
                        defines = "#define MC_VERSION 12101\n#define MC_SHADOW_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n#define IS_IRIS\n#define IRIS_FEATURE_SEPARATE_ENTITY_DRAWS\n" + extra
                        if program.startswith("dh_") and "DISTANT_HORIZONS" not in defines:
                            defines += "#define DISTANT_HORIZONS\n"
                        source = source.replace("#version 400 compatibility", "#version 400 compatibility\n" + defines, 1).replace("\\\n", "")
                        path = Path(self.directory.name) / f"{world}-{program}-{variant}.fsh"
                        path.write_text(source)
                        result = subprocess.run(["glslangValidator", "-S", "frag", str(path)], capture_output=True, text=True)
                        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
