"""Pinned Photon rough-ray native-kernel checks. No client, archive build or GPU claim."""
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import unittest
import zipfile

ROOT = Path(os.environ.get('SHADERLOD_TEST_ROOT', Path(__file__).resolve().parents[1]))
RECIPE = Path(os.environ.get('SHADERLOD_REFLECTION_RECIPE', ROOT/'patches/photon-experiments/live-pixel-budget.json'))
OUT = Path(os.environ.get('SHADERLOD_REFLECTION_CHECKS', ROOT/'build/reflection-ray-budget-checks'))
sys.path.insert(0, str(ROOT/'scripts'))
import build_photon_live_pixel_budget as builder
MEMBER = 'shaders/include/lighting/specular_lighting.glsl'


def tokens(s):
    return re.sub(r'\s+', '', re.sub(r'//[^\n]*|/\*.*?\*/', '', s, flags=re.S))


def block(s, marker):
    start = s.index(marker)
    brace = s.index('{', start)
    depth = 1
    end = brace+1
    while depth:
        depth += (s[end] == '{') - (s[end] == '}')
        end += 1
    return s[brace+1:end-1], end


def expand(files, name):
    return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*',
        lambda m: expand(files, 'shaders'+m[1] if m[1].startswith('/') else str(Path(name).parent/m[1])),
        files[name].decode(), flags=re.M)


class ReflectionRayNativeKernelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(RECIPE.read_bytes())
        cls.original = builder.archive_members(ROOT/builder.BASE)
        cls.files = builder.patch_members(cls.original, cls.manifest, 'enabled', ROOT)
        cls.source = cls.files[MEMBER].decode()
        cls.full, end = block(cls.source, 'if (ray_count == SSR_RAY_COUNT)')
        cls.reduced, _ = block(cls.source[end:], 'else {')
        OUT.mkdir(parents=True, exist_ok=True)

    def test_exact_native_loop_with_existing_independent_ownership_guards(self):
        native = self.original[MEMBER].decode()
        start = native.index('        for (int i = 0; i < SSR_RAY_COUNT; ++i)')
        end = native.index('        if (any(isnan(reflection)))', start)
        full = re.sub(r'// Native Photon roughness=.*?#endif\n', '', self.full, flags=re.S)
        full = full.replace('shaderlod_photon_opaque_rough_next_event=false;', '')
        self.assertEqual(tokens(native[start:end]), tokens(full))
        # Both paths retain the exact same trace arguments, samples and independent guard.
        normalized = self.reduced.replace('i < ray_count', 'i < SSR_RAY_COUNT').replace('float(ray_count)', 'float(SSR_RAY_COUNT)')
        self.assertEqual(tokens(self.full), tokens(normalized))
        self.assertIn('if (!is_water)', self.source)
        self.assertIn('#if defined SSR_ROUGHNESS_SUPPORT && defined SPECULAR_MAPPING', self.source)

    def test_reduced_body_matches_authenticated_pre_repair_body(self):
        # Frozen token digest of the existing reduced body, including next-event scope.
        expected = '61a2c66a10e0eabbbb9999d739cfac88e1bb3a59fea5ed163d6be5807781ce03'
        self.assertEqual(expected, hashlib.sha256(tokens(self.reduced).encode()).hexdigest())

    def test_pinned_assembly_original_recovery_and_tamper_refusal(self):
        self.assertEqual(builder.BASE_SHA256, builder.sha256((ROOT/builder.BASE).read_bytes()))
        self.assertEqual(self.original, builder.patch_members(self.original, self.manifest, 'original', ROOT))
        native = self.original[MEMBER].decode()
        self.assertTrue(self.source.endswith('\n#else\n'+native+'\n#endif\n'))
        self.assertEqual(self.original['LICENSE'], self.files['LICENSE'])
        bad = self.original | {MEMBER: self.original[MEMBER]+b'\n'}
        with self.assertRaises(ValueError): builder.patch_members(bad, self.manifest, 'enabled', ROOT)
        changed = copy.deepcopy(self.manifest)
        entry = next(e for e in changed['members'] if e['path'] == MEMBER)
        entry['replacements'][0]['after'] += '\n'
        with self.assertRaises(ValueError): builder.patch_members(self.original, changed, 'enabled', ROOT)

    def test_actual_policy_controls_and_rounding(self):
        # Compile the actual current GLSL scalar policy as C++; GLSL float semantics are
        # represented with float overloads. Rendering/driver branch lowering is separate.
        helpers = self.files['shaders/include/shaderlod/refinement_budget.glsl'].decode()
        flags = self.files['shaders/include/shaderlod/optimization_flags.glsl'].decode()
        flags = flags[flags.index('bool shaderlod_optimization_enabled'):flags.rindex('#endif')]
        source = '''#include <algorithm>
#include <cmath>
#include <cassert>
#include <limits>
using std::min; using std::max; using std::ceil;
float mix(float a,float b,float x){return a*(1-x)+b*x;}
float smoothstep(float a,float b,float x){float t=max(0.f,min(1.f,(x-a)/(b-a)));return t*t*(3-2*t);}
int shaderlodBridgeVersion=2,shaderlodEnabled=1,shaderlodPreset=0,shaderlodOptimizationFlagsVersion=1;
float shaderlodNear=32,shaderlodFar=96,shaderlodTransition=16;
'''+helpers+'\n'+flags+'''
int budget(int flag,float distance,int n){int c=n;if(shaderlod_optimization_enabled(flag))c=shaderlod_refinement_budget(shaderlodBridgeVersion,shaderlodEnabled,shaderlodPreset,distance,n,1);return c;}
int main(){
 for(int n=1;n<=8;n++)for(int p=0;p<=3;p++)for(float d:{0.f,23.f,32.f,64.f,96.f,104.f,200.f}){
  shaderlodPreset=p;assert(budget(0,d,n)==n);int c=budget(1,d,n);assert(c>=1&&c<=n);
  if(d<=24||p==3)assert(c==n);
  for(int bad:{-1,0,1,3}){shaderlodBridgeVersion=bad;assert(budget(1,d,n)==n);}shaderlodBridgeVersion=2;
  shaderlodEnabled=0;assert(budget(1,d,n)==n);shaderlodEnabled=1;
  shaderlodOptimizationFlagsVersion=0;assert(budget(1,d,n)==n);shaderlodOptimizationFlagsVersion=1;
 }
 shaderlodPreset=0;assert(budget(1,200,4)==4);
 shaderlodPreset=1;assert(budget(1,200,4)==3);
 shaderlodPreset=2;assert(budget(1,200,4)==3);
 for(int p:{-1,3,4}){shaderlodPreset=p;assert(budget(1,200,4)==4);}shaderlodPreset=1;
 for(float d:{-1.f,std::numeric_limits<float>::quiet_NaN(),std::numeric_limits<float>::infinity()})assert(budget(1,d,4)==4);
 shaderlodNear=0;assert(budget(1,200,4)==4);shaderlodNear=32;
 shaderlodFar=16;assert(budget(1,200,4)==4);shaderlodFar=96;
 shaderlodTransition=1000;assert(budget(1,200,4)==4);
}
'''
        cpp=OUT/'policy.cpp'; cpp.write_text(source)
        compiled=subprocess.run(['clang++','-std=c++11',str(cpp),'-o',str(OUT/'policy')],capture_output=True,text=True,timeout=30)
        self.assertEqual(0,compiled.returncode,compiled.stdout+compiled.stderr)
        result=subprocess.run([str(OUT/'policy')],capture_output=True,text=True,timeout=10)
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)

    @unittest.skipUnless(shutil.which('glslangValidator'), 'offline GLSL compiler unavailable')
    def test_real_entry_points_iris_jcpp_and_glslang(self):
        iris=ROOT/self.manifest['iris_option_discovery']['jar_path']
        self.assertEqual(self.manifest['iris_option_discovery']['jar_sha256'],builder.sha256(iris.read_bytes()))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
        deps=[]
        for rel,name in [('com.google.guava/guava/32.1.2-jre','guava-32.1.2-jre.jar'),('it.unimi.dsi/fastutil/8.5.12','fastutil-8.5.12.jar'),('org.slf4j/slf4j-api/2.0.9','slf4j-api-2.0.9.jar')]:
            paths=list((cache/rel).glob('*/'+name)); self.assertEqual(1,len(paths)); deps.extend(paths)
        with zipfile.ZipFile(iris) as z:
            jar=OUT/'jcpp-1.4.14.jar'; jar.write_bytes(z.read('META-INF/jarjar/jcpp-1.4.14.jar')); deps.append(jar)
        java=OUT/'ReflectionPreprocess.java'
        java.write_text('import java.nio.file.*;import java.util.*;import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;class ReflectionPreprocess{public static void main(String[] a)throws Exception{for(String p:a)Files.writeString(Path.of(p+".jcpp"),JcppProcessor.glslPreprocessSource(Files.readString(Path.of(p)),List.of()));}}')
        paths=[]
        for label,pbr in [('native-default',False),('pbr-source-fixture',True)]:
            files=self.files.copy()
            # Offline option coverage only. Never writes live options or changes native AA.
            if pbr:
                settings=files[builder.SETTINGS].decode()
                settings,n=re.subn(r'(?m)^//#define SPECULAR_MAPPING$', '#define SPECULAR_MAPPING', settings)
                self.assertEqual(1,n); files[builder.SETTINGS]=settings.encode()
            for world in ['world0','world-1','world1']:
                for stage in ['deferred4','gbuffers_water']:
                    source=expand(files,f'shaders/{world}/{stage}.fsh').replace('\\\n','')
                    source=source.replace('#version 400 compatibility','#version 400 compatibility\n#define MC_VERSION 12101\n#define MC_SHADOW_QUALITY 1.0\n#define MC_RENDER_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n#define IS_IRIS\n#define IRIS_FEATURE_SEPARATE_ENTITY_DRAWS',1)
                    path=OUT/f'{label}-{world}-{stage}.frag'; path.write_text(source); paths.append(path)
        run=subprocess.run(['java','-Xmx512m','--class-path',os.pathsep.join(map(str,[iris,*deps])),str(java),*map(str,paths)],capture_output=True,text=True,timeout=60)
        (OUT/'jcpp.log').write_text(run.stdout+run.stderr)
        self.assertEqual(0,run.returncode,run.stdout+run.stderr)
        checks=[]
        for path in paths:
            processed=Path(str(path)+'.jcpp'); source=processed.read_text()
            has_budget='if (ray_count == 4)' in source
            self.assertEqual(path.name.startswith('pbr-source-fixture'),has_budget,path.name)
            result=subprocess.run(['glslangValidator','-S','frag',str(processed)],capture_output=True,text=True,timeout=20)
            Path(str(path)+'.log').write_text(result.stdout+result.stderr)
            checks.append({'entry':path.name,'sha256':builder.sha256(processed.read_bytes()),'ray_budget_present':has_budget,'exit_code':result.returncode})
        (OUT/'compilation.json').write_text(json.dumps({'offline_only':True,'native_options_unchanged':True,'pbr_is_source_fixture_only':True,'checks':checks},indent=2)+'\n')
        self.assertEqual([], [c for c in checks if c['exit_code']])

if __name__ == '__main__': unittest.main()
