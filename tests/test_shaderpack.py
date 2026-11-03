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
        with zipfile.ZipFile(ROOT / "dependencies/upstream/photon_v1.3b.zip") as archive:
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

    @unittest.skipUnless((shutil.which("clang++") or shutil.which("c++")), "C++ compiler unavailable for isolated shader math checks")
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
                    "#define SHADERLOD_CLOUDS\n"
                    f"#define SHADERLOD_CLOUD_NEAR {float(near)}\n"
                    f"#define SHADERLOD_CLOUD_FAR {float(far)}\n"
                    f"#define SHADERLOD_CLOUD_TRANSITION {float(band)}\n")
                checks = "\n".join(f"if(shaderlod_cloud_tier({distance}, {noise}) != {expected}) return {i+1};"
                                     for i, (distance, noise, expected) in enumerate(samples))
                path = Path(self.directory.name) / f"cloud-helper-{index}.cpp"
                executable = path.with_suffix("")
                path.write_text(definitions + wrappers + functions + "\nint main(){\n" + checks + "\nreturn 0;}\n")
                result = subprocess.run([(shutil.which("clang++") or shutil.which("c++")), "-std=c++17", str(path), "-o", str(executable)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                executed = subprocess.run([str(executable)], capture_output=True, text=True)
                self.assertEqual(0, executed.returncode, f"Cloud boundary check {executed.returncode} failed")

    @unittest.skipUnless((shutil.which("clang++") or shutil.which("c++")), "C++ compiler unavailable for isolated shader math checks")
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
                    "#define SHADERLOD_WATER\n"
                    f"#define SHADERLOD_WATER_NEAR {float(near)}\n"
                    f"#define SHADERLOD_WATER_FAR {float(far)}\n"
                    f"#define SHADERLOD_WATER_TRANSITION {float(band)}\n")
                checks = "\n".join(f"if(shaderlod_water_tier({distance}, {noise}) != {expected}) return {i+1};"
                                     for i, (distance, noise, expected) in enumerate(samples))
                path = Path(self.directory.name) / f"water-helper-{index}.cpp"
                executable = path.with_suffix("")
                path.write_text(definitions + wrappers + functions + "\nint main(){\n" + checks + "\nreturn 0;}\n")
                result = subprocess.run([(shutil.which("clang++") or shutil.which("c++")), "-std=c++17", str(path), "-o", str(executable)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                executed = subprocess.run([str(executable)], capture_output=True, text=True)
                self.assertEqual(0, executed.returncode, f"Water boundary check {executed.returncode} failed")

    @staticmethod
    def water_function(source, name):
        match = re.search(rf"(?:float|vec2|vec3|void) {name}\([^)]*\) \{{", source)
        if match is None:
            raise AssertionError(f"Missing water function: {name}")
        end, depth = match.end(), 1
        while depth:
            depth += (source[end] == "{") - (source[end] == "}")
            end += 1
        return source[match.start():end]

    def test_upstream_water_functions_are_untouched(self):
        path = "shaders/include/surface/water_normal.glsl"
        original = self.upstream[path].decode()
        patched = self.files[path].decode()
        for name in ("gerstner_wave", "water_waves_setup", "get_water_height",
                     "get_water_normal", "get_water_parallax_coord"):
            self.assertEqual(self.water_function(original, name),
                             self.water_function(patched, name), name)
        self.assertEqual(self.upstream["shaders/program/shadow.fsh"],
                         self.files["shaders/program/shadow.fsh"])
        settings = self.files["shaders/settings.glsl"].decode()
        self.assertRegex(settings, r"(?m)^//#define SHADERLOD_WATER_ANALYTIC$")

    @unittest.skipUnless((shutil.which("clang++") or shutil.which("c++")), "C++ compiler unavailable for isolated wave math checks")
    def test_actual_static_water_paths_match_original_reference(self):
        # Execute actual specialized GLSL bodies against original full-height,
        # finite-normal and parallax bodies instantiated with the same count.
        # Procedural texture values test coordinate-dependent finite differences;
        # constant noise independently checks analytic derivatives. Not rendering.
        original = self.upstream["shaders/include/surface/water_normal.glsl"].decode()
        bodies = {name: self.water_function(original, name) for name in (
            "gerstner_wave", "water_waves_setup", "get_water_height",
            "get_water_normal", "get_water_parallax_coord")}
        actual = "\n".join(bodies.values()) + "\n" + self.flatten(
            "shaders/include/shaderlod/water_lod.glsl", {})
        references = ""
        for count in range(1, 9):
            references += bodies["get_water_height"].replace("get_water_height", f"reference_height_{count}").replace("WATER_WAVE_ITERATIONS", str(count)) + "\n"
            references += bodies["get_water_normal"].replace("get_water_normal", f"reference_normal_{count}").replace("get_water_height", f"reference_height_{count}") + "\n"
            for steps in (1, 2, 3, 4):
                references += bodies["get_water_parallax_coord"].replace("get_water_parallax_coord", f"reference_parallax_{count}_{steps}").replace("get_water_height", f"reference_height_{count}").replace("const int step_count = 4;", f"const int step_count = {steps};") + "\n"
        def adapt(source):
            source = re.sub(r"float\[([^\]]+)\] wave_noise;", r"float wave_noise[\1];", source)
            source = re.sub(r"out (vec2|mat2|float) (\w+)", r"\1& \2", source)
            source = source.replace("tangent_dir.xy", "vec2(tangent_dir.x, tangent_dir.y)")
            return source.replace("normal.xy *= normal_influence;", "normal.x *= normal_influence; normal.y *= normal_influence;")
        adapters = r"""
#include <cmath>
#include <algorithm>
using std::max; using std::pow; using std::sin; using std::cos; using std::sqrt;
#define float double
using uint = unsigned;
struct mat2 {float a,b,c,d;mat2():a(0),b(0),c(0),d(0){} mat2(float v):a(v),b(0),c(0),d(v){} mat2(float A,float B,float C,float D):a(A),b(B),c(C),d(D){}};
struct vec2 {
 float x,y;vec2():x(0),y(0){}vec2(float v):x(v),y(v){}vec2(float X,float Y):x(X),y(Y){}
 vec2 operator+(vec2 b)const{return vec2(x+b.x,y+b.y);}
 vec2 operator-(vec2 b)const{return vec2(x-b.x,y-b.y);}
 vec2 operator*(float v)const{return vec2(x*v,y*v);}
 vec2& operator+=(vec2 b){x+=b.x;y+=b.y;return *this;}
 vec2& operator*=(float v){x*=v;y*=v;return *this;}
 vec2& operator*=(mat2 m){float X=x*m.a+y*m.b;y=x*m.c+y*m.d;x=X;return *this;}
};
vec2 operator*(float a,vec2 b){return b*a;}
struct vec3 {
 float x,y,z;vec3(float X,float Y,float Z):x(X),y(Y),z(Z){}
 vec3(vec2 v,float Z):x(v.x),y(v.y),z(Z){}
 vec3 operator-(vec3 b)const{return vec3(x-b.x,y-b.y,z-b.z);}
};
float dot(vec2 a,vec2 b){return a.x*b.x+a.y*b.y;}
float dot(vec3 a,vec3 b){return a.x*b.x+a.y*b.y+a.z*b.z;}
float distance(vec3 a,vec3 b){vec3 d=a-b;return sqrt(dot(d,d));}
vec3 normalize(vec3 a){float n=sqrt(dot(a,a));return vec3(a.x/n,a.y/n,a.z/n);}
float sqr(float a){return a*a;}
float rcp(float a){return 1./a;}
float dampen(float a){a=std::clamp(a,0.,1.);return a*(2.-a);}
float mix(float a,float b,float w){return a*(1.-w)+b*w;}
vec2 mix(vec2 a,vec2 b,float w){return a*(1.-w)+b*w;}
float smoothstep(float a,float b,float x){float t=std::clamp((x-a)/(b-a),0.,1.);return t*t*(3.-2.*t);}
bool constant_noise=false;
struct Sample{float y;};
Sample texture(int,vec2 p){return {constant_noise?.37:.4+.15*sin(3.*p.x+2.*p.y)};}
const int noisetex=0;
const float tau=6.28318530717958647692,degree=tau/360.,golden_angle=2.39996322972865332;
float frameTimeCounter=17.3,rainStrength=.3;
vec3 cameraPosition(0.,0.,0.);
struct FragCoord{vec2 xy;};FragCoord gl_FragCoord{vec2(10.,10.)};
int frameCounter=7,test_tier=0,test_preset=0;
int shaderlod_water_quality_at(float,vec2,float){return test_tier;}
int shaderlod_preset(){return test_preset;}
#define WORLD_OVERWORLD
#define WATER_WAVE_FREQUENCY 1.0
#define WATER_WAVE_PERSISTENCE 1.0
#define WATER_WAVE_LACUNARITY 1.0
#define WATER_WAVE_SPEED_STILL 1.0
#define WATER_WAVE_SPEED_FLOWING 1.0
#define WATER_WAVE_STRENGTH 1.0
bool close(vec2 a,vec2 b){return std::abs(a.x-b.x)<1e-9 && std::abs(a.y-b.y)<1e-9;}
bool close(vec3 a,vec3 b){return std::abs(a.x-b.x)<1e-9 && std::abs(a.y-b.y)<1e-9 && std::abs(a.z-b.z)<1e-9;}
"""
        checks = "int main(){\n"
        checks += r"""
const vec2 coords[]={vec2(1.2,-3.4),vec2(200.3,100.8),vec2(-8.1,11.3)};
const vec2 dirs[]={vec2(1.,0.),vec2(.8,.6)};
mat2 rotation(.737,-.676,.676,.737);
const vec3 world(100.,64.,50.),flat(0.,1.,0.);
for(float clock:{.4,17.3,60.1}){frameTimeCounter=clock;
for(bool constant:{false,true}){constant_noise=constant;
for(vec2 coord:coords)for(vec2 dir:dirs){
"""
        checks += "#ifdef SHADERLOD_WATER\n"
        for count in range(1, 9):
            checks += f"""
#if SHADERLOD_WATER_REACHABLE({count}) && ({count} < WATER_WAVE_ITERATIONS || defined SHADERLOD_WATER_ANALYTIC)
if(std::abs(shaderlod_water_height_{count}(coord,dir,rotation,frameTimeCounter)-reference_height_{count}(coord,dir,rotation,frameTimeCounter))>1e-9)return 1;
#ifdef SHADERLOD_WATER_ANALYTIC
if(constant){{
 vec2 g=shaderlod_water_gradient_{count}(coord,dir,rotation,frameTimeCounter);const float h=1e-5;
 float gx=(reference_height_{count}(coord+vec2(h,0.),dir,rotation,frameTimeCounter)-reference_height_{count}(coord+vec2(-h,0.),dir,rotation,frameTimeCounter))/(2.*h);
 float gy=(reference_height_{count}(coord+vec2(0.,h),dir,rotation,frameTimeCounter)-reference_height_{count}(coord+vec2(0.,-h),dir,rotation,frameTimeCounter))/(2.*h);
 if(std::abs(g.x-gx)>1e-5 || std::abs(g.y-gy)>1e-5)return 2;
}}
#endif
#endif
"""
        checks += r"""
#endif
for(bool flowing:{false,true})for(int preset:{0,1,2})for(int tier:{0,1,2}){
 test_tier=tier;test_preset=preset;
 int expected_count=tier==0?WATER_WAVE_ITERATIONS:int(std::ceil(WATER_WAVE_ITERATIONS*(preset==0?(tier==1?.75:.5):preset==1?(tier==1?.5:.25):(tier==1?.25:.125))));
 #ifdef SHADERLOD_WATER
 if(shaderlod_water_octaves(tier)!=expected_count)return 3;
 #endif
 vec3 actual_normal=get_water_normal_lod(world,flat,coord,dir,.7,flowing);
#if defined PROGRAM_SHADOW || !defined SHADERLOD_WATER
 expected_count=WATER_WAVE_ITERATIONS;
#endif
 vec3 expected_normal=get_water_normal(world,flat,coord,dir,.7,flowing);
#ifndef SHADERLOD_WATER_ANALYTIC
 switch(expected_count){
"""
        for count in range(1, 9):
            checks += f"case {count}:expected_normal=reference_normal_{count}(world,flat,coord,dir,.7,flowing);break;\n"
        checks += r"""
 }
 if(!close(actual_normal,expected_normal))return 4;
#else
#if defined PROGRAM_SHADOW || !defined SHADERLOD_WATER
 if(!close(actual_normal,expected_normal))return 5;
#else
 if(tier!=0){switch(expected_count){
"""
        for count in range(1, 9):
            checks += f"""
#if SHADERLOD_WATER_REACHABLE({count})
case {count}:expected_normal=shaderlod_water_normal_{count}(world,flat,coord,dir,.7,flowing);break;
#endif
"""
        checks += r"""
 }}
 if(!close(actual_normal,expected_normal))return 5;
#endif
#endif
 for(vec3 tangent:{vec3(.3,.1,-.95),vec3(1.,.7,-.2),vec3(-.2,.1,-1.)}){
 vec2 actual_coord=get_water_parallax_coord_lod(world,tangent,coord,dir,flowing);
 int expected_steps=tier==0?4:(preset==0?3:preset==1?2:1);
 vec2 expected_coord=coord;
#if !defined PROGRAM_SHADOW && defined SHADERLOD_WATER
 if(tier!=2){
#else
 expected_steps=4;
 {
#endif
 switch(expected_count*10+expected_steps){
"""
        for count in range(1, 9):
            for steps in (1, 2, 3, 4):
                checks += f"case {count*10+steps}:expected_coord=reference_parallax_{count}_{steps}(tangent,coord,dir,flowing);break;\n"
        checks += " }\n }\n if(!close(actual_coord,expected_coord))return 6;\n }\n }\n }\n }\n }\n return 0;\n}\n"
        variants = [(count, variation, analytic, False, True) for count in range(1, 9)
                    for variation in (False, True) for analytic in (False, True)]
        variants += [(3, True, analytic, True, True) for analytic in (False, True)]
        variants += [(3, True, analytic, False, False) for analytic in (False, True)]
        for count, variation, analytic, shadow, effect in variants:
            with self.subTest(octaves=count, height_variation=variation, analytic=analytic, shadow=shadow, effect=effect):
                definitions = f"#define WATER_WAVE_ITERATIONS {count}\n"
                if variation: definitions += "#define WATER_WAVES_HEIGHT_VARIATION\n"
                if analytic: definitions += "#define SHADERLOD_WATER_ANALYTIC\n"
                if shadow: definitions += "#define PROGRAM_SHADOW\n"
                if effect: definitions += "#define SHADERLOD_WATER\n"
                path = Path(self.directory.name) / f"water-static-{count}-{variation}-{analytic}-{shadow}.cpp"
                executable = path.with_suffix("")
                path.write_text(adapters + definitions + adapt(actual) + adapt(references) + checks)
                preprocessed = subprocess.run([(shutil.which("clang++") or shutil.which("c++")), "-std=c++17", "-E", "-P", str(path)], capture_output=True, text=True)
                self.assertEqual(0, preprocessed.returncode, preprocessed.stderr)
                reachable = {int(__import__('math').ceil(count * scale)) for scale in (.75, .5, .25, .125)}
                expected_kernels = (reachable if analytic else reachable - {count}) if effect else set()
                actual_kernels = {int(n) for n in re.findall(r"double shaderlod_water_height_(\d+)\(", preprocessed.stdout)}
                self.assertEqual(expected_kernels, actual_kernels)
                compiled = subprocess.run([(shutil.which("clang++") or shutil.which("c++")), "-std=c++17", str(path), "-o", str(executable)], capture_output=True, text=True)
                self.assertEqual(0, compiled.returncode, compiled.stdout + compiled.stderr)
                result = subprocess.run([str(executable)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, f"Actual static water math fixture failed: {result.returncode}")

    @unittest.skipUnless((shutil.which("clang++") or shutil.which("c++")), "C++ compiler unavailable for cloud candidate math")
    def test_actual_cloud_candidates_bounds_geometry_and_noise(self):
        helper = self.files["shaders/include/shaderlod/lod.glsl"].decode()
        names = ("shaderlod_use_bridge", "shaderlod_enabled", "shaderlod_preset", "shaderlod_budget", "shaderlod_noise",
                 "shaderlod_tier_with_distances", "shaderlod_cloud_tier", "shaderlod_cloud_mode",
                 "shaderlod_cloud_overhead_distance", "shaderlod_cloud_effective_distances",
                 "shaderlod_cloud_selection_noise", "shaderlod_cloud_smooth_count", "shaderlod_cloud_ray_budgets")
        functions = []
        for name in names:
            match = re.search(rf"(?:bool|int|float|vec3|ivec3) {name}\([^)]*\) \{{", helper)
            self.assertIsNotNone(match, name)
            end, depth = match.end(), 1
            while depth:
                depth += (helper[end] == "{") - (helper[end] == "}")
                end += 1
            functions.append(helper[match.start():end].replace("viewer.xz", "vec2(viewer.x,viewer.z)"))
        adapters = r"""
#include <cmath>
#include <algorithm>
#include <limits>
using std::min;using std::max;using std::ceil;using std::sqrt;
#define float double
struct vec2 {float x,y;vec2(float a,float b):x(a),y(b){}vec2 operator+(vec2 b)const{return vec2(x+b.x,y+b.y);}vec2& operator+=(vec2 b){x+=b.x;y+=b.y;return *this;}};
vec2 operator*(float a,vec2 b){return vec2(a*b.x,a*b.y);}
struct vec3 {float x,y,z;vec3(float a,float b,float c):x(a),y(b),z(c){}};
struct ivec3 {int x,y,z;ivec3(int a,int b,int c):x(a),y(b),z(c){}};
struct bvec3 {bool x,y,z;};bool any(bvec3 a){return a.x||a.y||a.z;}
bool isnan(float v){return std::isnan(v);}bool isinf(float v){return std::isinf(v);}
bvec3 isnan(vec3 v){return {isnan(v.x),isnan(v.y),isnan(v.z)};}bvec3 isinf(vec3 v){return {isinf(v.x),isinf(v.y),isinf(v.z)};}
float dot(vec2 a,vec2 b){return a.x*b.x+a.y*b.y;}float dot(vec3 a,vec3 b){return a.x*b.x+a.y*b.y+a.z*b.z;}
float mod(float a,float b){return a-b*std::floor(a/b);}float fract(float a){return a-std::floor(a);}
float clamp(float a,float b,float c){return min(max(a,b),c);}int clamp(int a,int b,int c){return min(max(a,b),c);}
float smoothstep(float a,float b,float c){float t=clamp((c-a)/(b-a),0.,1.);return t*t*(3.-2.*t);}
float mix(float a,float b,float c){return a*(1.-c)+b*c;}
bool same(ivec3 a,ivec3 b){return a.x==b.x&&a.y==b.y&&a.z==b.z;}
#define SHADERLOD_USE_MOD_CONFIG
#define SHADERLOD_ENABLED
#define SHADERLOD_PRESET 1
#define CLOUDS_SCALE 10.0
#define SHADERLOD_CLOUD_NEAR 128.0
#define SHADERLOD_CLOUD_FAR 1024.0
#define SHADERLOD_CLOUD_TRANSITION 64.0
int shaderlodBridgeVersion=2,shaderlodEnabled=1,shaderlodPreset=1;
"""
        checks = r"""
int main(){
const float P=6371000.,inner=P+1200.,outer=P+2400.;
float reference=shaderlod_cloud_overhead_distance(vec3(0.,P+50.,0.),inner,outer,true);
#if defined SHADERLOD_CLOUD_OVERHEAD && SHADERLOD_CLOUD_MODE != 0
if(std::abs(reference-115.)>1e-8)return 1;
if(shaderlod_cloud_overhead_distance(vec3(0.,inner+5.,0.),inner,outer,true)!=0.)return 2;
if(shaderlod_cloud_overhead_distance(vec3(0.,outer+5.,0.),inner,outer,true)>=0.)return 3;
if(shaderlod_cloud_overhead_distance(vec3(0.,P+50.,0.),inner,outer,false)!=0.)return 4;
if(shaderlod_cloud_overhead_distance(vec3(0.,P+50.,0.),-1.,outer,true)>=0.)return 5;
// Actual changed layer altitude/scale/thickness: no fixed120-world-block assumption.
if(std::abs(shaderlod_cloud_overhead_distance(vec3(0.,P+50.,0.),P+400.,P+480.,true)-35.)>1e-8)return 6;
#endif
const vec2 pixel(33.2,70.3);const ivec3 minimum(4,2,1);
const ivec3 originals[]={ivec3(40,6,2),ivec3(21,1,1),ivec3(3,1,0)};
for(int preset:{0,1,2}){shaderlodPreset=preset;
for(ivec3 original:originals){
ivec3 previous=original;
for(int sample=0;sample<=24000;++sample){
float distance=sample*.1;
ivec3 budget=shaderlod_cloud_ray_budgets(original,minimum,distance,115.,pixel,7.,.25);
#ifndef SHADERLOD_CLOUDS
if(!same(budget,original))return 7;
#else
if(budget.x>original.x||budget.y>original.y||budget.z>original.z||
 budget.x<min(minimum.x,original.x)||budget.y<min(minimum.y,original.y)||budget.z<min(minimum.z,original.z))return 8;
#if SHADERLOD_CLOUD_MODE == 0
int tier=shaderlod_cloud_tier(distance,.25);
ivec3 expected(shaderlod_budget(original.x,tier,minimum.x),shaderlod_budget(original.y,tier,minimum.y),shaderlod_budget(original.z,tier,minimum.z));
if(!same(budget,expected))return 9;
#elif SHADERLOD_CLOUD_MODE == 1
if(!same(budget,shaderlod_cloud_ray_budgets(original,minimum,distance,115.,pixel,7.,.9)))return 10;
#else
if(budget.x>previous.x||budget.y>previous.y||budget.z>previous.z)return 11;
// Dense distance increments cannot skip integer levels at positive-width transitions.
if(SHADERLOD_CLOUD_TRANSITION>0. && (previous.x-budget.x>1||previous.y-budget.y>1||previous.z-budget.z>1))return 12;
#endif
#endif
previous=budget;
}
vec3 bands=shaderlod_cloud_effective_distances(115.);
if(bands.y-bands.x<bands.z)return 13;
if(!same(shaderlod_cloud_ray_budgets(original,minimum,bands.x-.5*bands.z-(bands.z==0.?1e-6:0.),115.,pixel,7.,.25),original))return 14;
#if defined SHADERLOD_CLOUD_OVERHEAD && SHADERLOD_CLOUD_MODE != 0
if(!same(shaderlod_cloud_ray_budgets(original,minimum,115.,115.,pixel,7.,.25),original))return 15;
#endif
#if SHADERLOD_CLOUD_MODE != 0
if(!same(shaderlod_cloud_ray_budgets(original,minimum,2000.,-1.,pixel,7.,.25),original))return 16;
if(!same(shaderlod_cloud_ray_budgets(original,minimum,std::numeric_limits<float>::quiet_NaN(),115.,pixel,7.,.25),original))return 17;
#endif
shaderlodEnabled=0;
if(!same(shaderlod_cloud_ray_budgets(original,minimum,2000.,115.,pixel,7.,.25),original))return 18;
shaderlodEnabled=1;
}
}
float noise=shaderlod_cloud_selection_noise(pixel,7.,.25);
if(noise<0.||noise>=1.)return 19;
#if SHADERLOD_CLOUD_MODE == 0
if(noise!=.25)return 20;
#else
if(noise==.25||noise!=shaderlod_cloud_selection_noise(pixel,7.,.9))return 21;
#endif
return 0;
}
"""
        variants = [(mode, overhead, cloud, False) for mode in (0, 1, 2)
                    for overhead in (False, True) for cloud in (False, True)]
        variants += [(mode, True, True, True) for mode in (1, 2)]
        for mode, overhead, cloud, zero_width in variants:
                    with self.subTest(mode=mode, overhead=overhead, cloud=cloud, zero_width=zero_width):
                        defs = f"#define SHADERLOD_CLOUD_MODE {mode}\n"
                        if zero_width: defs += "#undef SHADERLOD_CLOUD_TRANSITION\n#define SHADERLOD_CLOUD_TRANSITION 0.0\n"
                        if overhead: defs += "#define SHADERLOD_CLOUD_OVERHEAD\n"
                        if cloud: defs += "#define SHADERLOD_CLOUDS\n"
                        path = Path(self.directory.name) / f"cloud-candidate-{mode}-{overhead}-{cloud}-{zero_width}.cpp"
                        executable = path.with_suffix("")
                        path.write_text(adapters + defs + "\n".join(functions) + checks)
                        compiled = subprocess.run([(shutil.which("clang++") or shutil.which("c++")), "-std=c++17", str(path), "-o", str(executable)], capture_output=True, text=True)
                        self.assertEqual(0, compiled.returncode, compiled.stdout + compiled.stderr)
                        result = subprocess.run([str(executable)], capture_output=True, text=True)
                        self.assertEqual(0, result.returncode, f"Cloud candidate fixture failed: {result.returncode}")

    def test_cloud_density_extent_and_history_source_preserved(self):
        for layer in ("cumulus", "altocumulus", "cumulus_congestus"):
            path = f"shaders/include/sky/clouds/{layer}.glsl"
            source, original = self.files[path].decode(), self.upstream[path].decode()
            for suffix in ("density", "scattering"):
                name = f"clouds_{layer}_{suffix}"
                self.assertEqual(self.water_function(original, name), self.water_function(source, name))
            self.assertIn("float step_length = ray_length * rcp(float(primary_steps));", source)
            self.assertIn("ray_dir * (dists.x + step_length * dither)", source)
            self.assertIn("#if SHADERLOD_CLOUD_MODE == 0\n#if !defined PROGRAM_DEFERRED0\n    int cloud_quality = shaderlod_cloud_tier", source)
        path = "shaders/program/d2_clouds_upscaling.fsh"
        self.assertEqual(self.upstream[path], self.files[path])
        settings = self.files["shaders/settings.glsl"].decode()
        self.assertRegex(settings, r"#define SHADERLOD_CLOUD_MODE 0 // \[0 1 2\]")
        self.assertRegex(settings, r"(?m)^  #define SHADERLOD_CLOUD_OVERHEAD$")

    def test_lighting_tier_consumers_and_protected_call_sites(self):
        d4 = self.files["shaders/program/d4_deferred_shading.fsh"].decode()
        self.assertEqual(4, len(re.findall(r"\bshaderlod_quality\b", d4)))
        self.assertIn("int shaderlod_quality = 0;\n#ifndef SHADERLOD_PRESERVE_LIGHTING", d4)
        self.assertIn("int shaderlod_debug_quality = shaderlod_quality;", d4)
        debug = d4[d4.index("if (shaderlod_debug_enabled() && !is_hand)"):]
        self.assertIn("#ifdef SHADERLOD_PRESERVE_LIGHTING\n            int shaderlod_debug_quality = shaderlod_quality_at", debug)
        translucent = self.files["shaders/program/gbuffers_all_translucent.fsh"].decode()
        self.assertIn("sss_depth,\n#ifdef SHADERLOD_PRESERVE_LIGHTING\n        0\n#else", translucent)
        helper = self.files["shaders/include/shaderlod/lod.glsl"].decode()
        self.assertIn("int shaderlod_lighting_tier(int tier) {\n#ifdef SHADERLOD_PRESERVE_LIGHTING\n    return 0;", helper)

    @unittest.skipUnless((shutil.which("clang++") or shutil.which("c++")), "C++ compiler unavailable for actual lighting/debug fixtures")
    def test_actual_lighting_debug_control_flow_math_and_call_counts(self):
        helper = self.files["shaders/include/shaderlod/lod.glsl"].decode()
        functions = []
        for name in ("shaderlod_use_bridge", "shaderlod_enabled", "shaderlod_debug_enabled", "shaderlod_distances",
                     "shaderlod_noise", "shaderlod_tier_with_distances", "shaderlod_quality_at", "shaderlod_lighting_tier", "shaderlod_debug_color"):
            match = re.search(rf"(?:bool|int|float|vec3) {name}\([^)]*\) \{{", helper)
            self.assertIsNotNone(match, name)
            end, depth = match.end(), 1
            while depth:
                depth += (helper[end] == "{") - (helper[end] == "}")
                end += 1
            body = helper[match.start():end]
            if name == "shaderlod_quality_at":
                # Count entries without altering original tier/noise mathematics.
                body = body.replace("{", "{\n++quality_calls;", 1)
            functions.append(body)
        intro = next(patch["after"] for patch in self.manifest["patches"]
                     if patch["path"] == "shaders/program/d4_deferred_shading.fsh" and patch["before"] == "    vec3 position_world = position_scene + cameraPosition;")
        intro = intro[intro.index("    int shaderlod_quality"):]
        debug = next(patch["after"] for patch in self.manifest["patches"]
                     if patch["path"] == "shaders/program/d4_deferred_shading.fsh" and patch["before"] == "        fragment_color = purkinje_shift(fragment_color, light_levels);")
        argument = next(patch["after"] for patch in self.manifest["patches"]
                     if patch["path"] == "shaders/program/gbuffers_all_translucent.fsh" and patch["before"] == "        shadow_distance_fade,\n        sss_depth\n    );")
        argument = argument[argument.index("#ifdef SHADERLOD_PRESERVE_LIGHTING"):argument.index("#endif")+len("#endif")]
        adapters = r"""
#include <cmath>
#include <algorithm>
using std::max;using std::min;
#define float double
struct vec2 {float x,y;vec2(float a,float b):x(a),y(b){}vec2& operator+=(vec2 b){x+=b.x;y+=b.y;return *this;}};
vec2 operator*(float a,vec2 b){return vec2(a*b.x,a*b.y);}
struct vec3 {float x,y,z;vec3(float a,float b,float c):x(a),y(b),z(c){}vec3 operator*(float v)const{return vec3(x*v,y*v,z*v);}vec3 operator+(vec3 b)const{return vec3(x+b.x,y+b.y,z+b.z);}};
float length(vec3 v){return sqrt(v.x*v.x+v.y*v.y+v.z*v.z);}
float dot(vec2 a,vec2 b){return a.x*b.x+a.y*b.y;}
float mod(float a,float b){return a-b*std::floor(a/b);}float fract(float a){return a-std::floor(a);}
float clamp(float a,float b,float c){return min(max(a,b),c);}
float smoothstep(float a,float b,float c){float t=clamp((c-a)/(b-a),0.,1.);return t*t*(3.-2.*t);}
vec3 mix(vec3 a,vec3 b,float v){return a*(1.-v)+b*v;}
vec3 purkinje_shift(vec3 c,vec2){return c;} // Same unmodified caller input in both arms.
struct Frag{vec2 xy;};Frag gl_FragCoord{vec2(10.,20.)};
int frameCounter=42,quality_calls=0;
#define SHADERLOD_USE_MOD_CONFIG
#define SHADERLOD_ENABLED
#define SHADERLOD_NEAR 32.0
#define SHADERLOD_FAR 96.0
#define SHADERLOD_TRANSITION 8.0
int shaderlodBridgeVersion=2,shaderlodEnabled=1,shaderlodDebug=0;
float shaderlodNear=32.,shaderlodFar=96.,shaderlodTransition=8.;
struct Output{int lighting,calls;vec3 color;};
"""
        reference = r"""
Output reference(bool sky,bool hand,bool debug_on,float distance,bool enabled){
 shaderlodEnabled=enabled;shaderlodDebug=debug_on;quality_calls=0;
 vec3 position_view(distance,0.,0.);float depth=sky?1.:.5;bool is_hand=hand;
 int shaderlod_quality=(depth==1.||is_hand)?0:shaderlod_quality_at(length(position_view),gl_FragCoord.xy,float(frameCounter));
 vec3 fragment_color(.2,.3,.4);vec2 light_levels(.5,.7);
 if(!sky){fragment_color=purkinje_shift(fragment_color,light_levels);
  if(shaderlod_debug_enabled()&&!is_hand)fragment_color=mix(fragment_color,shaderlod_debug_color(shaderlod_quality),.7);
 }
 return {shaderlod_lighting_tier(shaderlod_quality),quality_calls,fragment_color};
}
"""
        candidate = """Output candidate(bool sky,bool hand,bool debug_on,float distance,bool enabled){
shaderlodEnabled=enabled;shaderlodDebug=debug_on;quality_calls=0;
vec3 position_view(distance,0.,0.);float depth=sky?1.:.5;bool is_hand=hand;
vec3 fragment_color(.2,.3,.4);vec2 light_levels(.5,.7);
""" + intro + "\nif(!sky){\n" + debug + "\n}\nreturn {shaderlod_quality,quality_calls,fragment_color};\n}\n"
        translucent = "int translucent(){vec3 position_view(200.,0.,0.);return\n" + argument + "\n;}\n"
        checks = r"""
int main(){
for(bool sky:{false,true})for(bool hand:{false,true})for(bool debug:{false,true})for(bool enabled:{false,true})for(float distance:{8.,32.,64.,96.,200.}){
 Output old=reference(sky,hand,debug,distance,enabled),now=candidate(sky,hand,debug,distance,enabled);
 if(old.lighting!=now.lighting)return 1;
 if(std::abs(old.color.x-now.color.x)>1e-12||std::abs(old.color.y-now.color.y)>1e-12||std::abs(old.color.z-now.color.z)>1e-12)return 2;
#ifdef SHADERLOD_PRESERVE_LIGHTING
 int expected=(!sky&&!hand&&debug)?1:0;
#else
 int expected=(!sky&&!hand)?1:0;
#endif
 if(now.calls!=expected)return 3;
}
shaderlodEnabled=1;quality_calls=0;
int result=translucent();
#ifdef SHADERLOD_PRESERVE_LIGHTING
if(result!=0||quality_calls!=0)return 4;
#else
if(result!=2||quality_calls!=1)return 5;
#endif
return 0;
}
"""
        for protected in (False, True):
            with self.subTest(protected=protected):
                definitions = "#define SHADERLOD_PRESERVE_LIGHTING\n" if protected else ""
                path = Path(self.directory.name) / f"lighting-tier-{protected}.cpp"
                executable = path.with_suffix("")
                path.write_text(adapters + definitions + "\n".join(functions) + reference + candidate + translucent + checks)
                compiled = subprocess.run([(shutil.which("clang++") or shutil.which("c++")), "-std=c++17", str(path), "-o", str(executable)], capture_output=True, text=True)
                self.assertEqual(0, compiled.returncode, compiled.stdout + compiled.stderr)
                result = subprocess.run([str(executable)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, f"Actual lighting/debug fixture failed: {result.returncode}")

    def test_off_effect_source_branches_are_pinned_original(self):
        paths = {
            "SHADERLOD_CLOUDS": ("sky/clouds/cumulus.glsl", "sky/clouds/altocumulus.glsl", "sky/clouds/cumulus_congestus.glsl"),
            "SHADERLOD_FOG": ("fog/overworld/raymarched.glsl", "fog/water_fog_vl.glsl", "fog/end_fog_vl.glsl", "fog/lpv_fog.glsl"),
            "SHADERLOD_SSR": ("lighting/specular_lighting.glsl",),
            "SHADERLOD_MATERIALS": ("surface/parallax.glsl",),
        }
        for flag, names in paths.items():
            for name in names:
                path = "shaders/include/" + name
                source = self.files[path].decode()
                original = self.upstream[path].decode()
                self.assertTrue(source.endswith("\n#else\n" + original + "\n#endif // " + flag + "\n"), path)
        settings = self.files["shaders/settings.glsl"].decode()
        for effect in ("CLOUDS", "WATER", "FOG", "SSR", "MATERIALS"):
            self.assertRegex(settings, rf"(?m)^  #define SHADERLOD_{effect}$")
        fog = self.files["shaders/include/fog/overworld/raymarched.glsl"].decode().split("\n#else\n" + self.upstream["shaders/include/fog/overworld/raymarched.glsl"].decode())[0]
        self.assertIn("shaderlod_fog_tier", fog)
        self.assertNotIn("shaderlod_tier(", fog)
        ssr = self.files["shaders/include/lighting/specular_lighting.glsl"].decode().split("\n#else\n" + self.upstream["shaders/include/lighting/specular_lighting.glsl"].decode())[0]
        self.assertIn("shaderlod_ssr_tier", ssr)
        self.assertNotIn("shaderlod_tier(", ssr)

    @unittest.skipUnless((shutil.which("clang++") or shutil.which("c++")), "C++ compiler unavailable for effect getter math")
    def test_actual_effect_getters_respect_flags_and_bridge_disable(self):
        helper = self.files["shaders/include/shaderlod/lod.glsl"].decode()
        functions = []
        for name in ("shaderlod_use_bridge", "shaderlod_enabled", "shaderlod_distances", "shaderlod_preset",
                     "shaderlod_tier_with_distances", "shaderlod_tier", "shaderlod_fog_tier", "shaderlod_ssr_tier",
                     "shaderlod_cached_sky_tier", "shaderlod_cloud_tier", "shaderlod_water_tier", "shaderlod_material_tier"):
            match = re.search(rf"(?:bool|int|vec3) {name}\([^)]*\) \{{", helper)
            self.assertIsNotNone(match, name)
            end, depth = match.end(), 1
            while depth:
                depth += (helper[end] == "{") - (helper[end] == "}")
                end += 1
            functions.append(helper[match.start():end])
        adapters = r"""
#include <cmath>
#include <algorithm>
using std::max; using std::min;
#define float double
struct vec3 {float x,y,z;vec3(float a,float b,float c):x(a),y(b),z(c){}};
float clamp(float a,float b,float c){return min(max(a,b),c);}
int clamp(int a,int b,int c){return min(max(a,b),c);}
float smoothstep(float a,float b,float c){float t=clamp((c-a)/(b-a),0.,1.);return t*t*(3.-2.*t);}
#define SHADERLOD_USE_MOD_CONFIG
#define SHADERLOD_ENABLED
#define SHADERLOD_CACHED_SKY
#define SHADERLOD_PRESET 1
#define SHADERLOD_NEAR 32.0
#define SHADERLOD_FAR 96.0
#define SHADERLOD_TRANSITION 8.0
#define SHADERLOD_CLOUD_NEAR 128.0
#define SHADERLOD_CLOUD_FAR 1024.0
#define SHADERLOD_CLOUD_TRANSITION 64.0
#define SHADERLOD_WATER_NEAR 96.0
#define SHADERLOD_WATER_FAR 256.0
#define SHADERLOD_WATER_TRANSITION 32.0
int shaderlodBridgeVersion=2,shaderlodEnabled=1,shaderlodPreset=1;
float shaderlodNear=32.,shaderlodFar=96.,shaderlodTransition=8.;
"""
        effects = ("CLOUDS", "WATER", "FOG", "SSR", "MATERIALS")
        variants = ((), effects, *((effect,) for effect in effects))
        for variant in variants:
            checks = "int main(){\n"
            for enabled in (1, 0):
                checks += f"shaderlodEnabled={enabled};\n"
                for effect, call in (("CLOUDS", "shaderlod_cloud_tier(2000.,.5)"),
                                     ("WATER", "shaderlod_water_tier(2000.,.5)"),
                                     ("FOG", "shaderlod_fog_tier(2000.,.5)"),
                                     ("SSR", "shaderlod_ssr_tier(2000.,.5)"),
                                     ("MATERIALS", "shaderlod_material_tier(2000.,1000.,.5)")):
                    expected = 2 if enabled and effect in variant else 0
                    checks += f"if({call}!={expected})return 1;\n"
                expected = 1 if enabled and "CLOUDS" in variant else 0
                checks += f"if(shaderlod_cached_sky_tier()!={expected})return 2;\n"
            checks += "return 0;}\n"
            definitions = "".join(f"#define SHADERLOD_{effect}\n" for effect in variant)
            path = Path(self.directory.name) / ("effect-getters-" + "-".join(variant) + ".cpp")
            executable = path.with_suffix("")
            path.write_text(adapters + definitions + "\n".join(functions) + checks)
            result = subprocess.run([(shutil.which("clang++") or shutil.which("c++")), "-std=c++17", str(path), "-o", str(executable)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual(0, subprocess.run([str(executable)]).returncode, variant)

    @unittest.skipUnless((shutil.which("clang++") or shutil.which("c++")), "C++ compiler unavailable for isolated bridge checks")
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
#define SHADERLOD_WATER
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
                result = subprocess.run([(shutil.which("clang++") or shutil.which("c++")), "-std=c++17", str(path), "-o", str(executable)], capture_output=True, text=True)
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
            ("water_analytic", {"WATER_WAVES": True, "WATER_PARALLAX": True, "SHADERLOD_WATER_ANALYTIC": True}, "#define DISTANT_HORIZONS\n"),
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
        effect_flags = {f"SHADERLOD_{effect}": False for effect in ("CLOUDS", "WATER", "FOG", "SSR", "MATERIALS")}
        variants += (("effects_all_off", effect_flags, "#define DISTANT_HORIZONS\n"),)
        for effect in ("CLOUDS", "WATER", "FOG", "SSR", "MATERIALS"):
            variants += (("effect_" + effect.lower(), {**effect_flags, f"SHADERLOD_{effect}": True}, "#define DISTANT_HORIZONS\n"),)
        variants += (("effects_off_lpv", {**effect_flags, "COLORED_LIGHTS": True, "LPV_VL": True}, "#define DISTANT_HORIZONS\n"),)
        for mode in (0, 1, 2):
            for overhead in (False, True):
                variants += ((f"cloud_mode_{mode}_overhead_{overhead}",
                    {"SHADERLOD_CLOUD_MODE": mode, "SHADERLOD_CLOUD_OVERHEAD": overhead}, "#define DISTANT_HORIZONS\n"),)
        variants += (
            ("protected_debug_dh", {"SHADERLOD_DEBUG": True, "SHADERLOD_PRESERVE_LIGHTING": True}, "#define DISTANT_HORIZONS\n"),
            ("legacy_debug_dh", {"SHADERLOD_DEBUG": True, "SHADERLOD_PRESERVE_LIGHTING": False}, "#define DISTANT_HORIZONS\n"),
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


    @unittest.skipUnless(shutil.which("glslangValidator"), "glslangValidator unavailable")
    def test_water_count_and_preset_programs_compile(self):
        for count in range(1, 9):
            for preset in (0, 1, 2):
                for analytic in (False, True):
                    for program in ("gbuffers_water", "dh_water", "shadow", "composite1"):
                        with self.subTest(count=count, preset=preset, analytic=analytic, program=program):
                            options = {"WATER_WAVES": True, "WATER_PARALLAX": True,
                                "WATER_WAVE_ITERATIONS": count, "SHADERLOD_PRESET": preset,
                                "SHADERLOD_WATER_ANALYTIC": analytic, "SHADERLOD_USE_MOD_CONFIG": False}
                            source = self.flatten(f"shaders/world0/{program}.fsh", options)
                            defines = "#define MC_VERSION 12101\n#define MC_SHADOW_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n#define IS_IRIS\n#define IRIS_FEATURE_SEPARATE_ENTITY_DRAWS\n#define DISTANT_HORIZONS\n"
                            source = source.replace("#version 400 compatibility", "#version 400 compatibility\n" + defines, 1).replace("\\\n", "")
                            path = Path(self.directory.name) / f"water-count-{count}-{preset}-{analytic}-{program}.fsh"
                            path.write_text(source)
                            result = subprocess.run(["glslangValidator", "-S", "frag", str(path)], capture_output=True, text=True)
                            self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
