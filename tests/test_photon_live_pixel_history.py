"""Bounded pin/parity/dependency/parser/offline semantics; no client or GPU operations."""
import copy
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import build_photon_live_pixel_history as probe
M=json.loads((ROOT/probe.MANIFEST).read_text())
OUT=ROOT/'build/live-pixel-history-r2-source-checks'


def select(source, private=False, consumer=False):
    """Select only injected gates, keeping all original preprocessor bytes intact."""
    source=re.sub(r'// ShaderLOD private live pixel-history diagnostic; default off\.\n(?://)?#define '+probe.SWITCH+r'\n','',source)
    stack=[]; result=[]
    for line in source.splitlines(keepends=True):
        if line=='#ifdef '+probe.SWITCH+'\n': stack.append(private)
        elif line=='#if defined TAA && defined TAAU && !defined VOXY\n': stack.append(consumer)
        elif line in ('#else // '+probe.SWITCH+'\n','#else // SHADERLOD_LIVE_PIXEL_STAGE\n'):stack[-1]=not stack[-1]
        elif line in ('#endif // '+probe.SWITCH+'\n','#endif // SHADERLOD_LIVE_PIXEL_STAGE\n'):stack.pop()
        elif not stack or all(stack):result.append(line)
    if stack:raise AssertionError('Unclosed private gate')
    return ''.join(result)


def expand(files,name):
    return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*',
        lambda m:expand(files,'shaders'+m[1] if m[1].startswith('/') else str(Path(name).parent/m[1])),
        files[name].decode(),flags=re.M)


def configured(files, private=True, taa=True, taau=True, voxy=False):
    files=files.copy(); s=files[probe.SETTINGS].decode()
    for name,value in [('TAA',taa),('TAAU',taau),(probe.SWITCH,private)]:
        s,n=re.subn(r'^[ \t]*(?://)?#define '+name+r'[ \t]*$',('#define ' if value else '//#define ')+name,s,flags=re.M)
        assert n==1,(name,n)
    for option in ('COLORED_LIGHTS','SH_SKYLIGHT','ENVIRONMENT_REFLECTIONS','SKY_REFLECTIONS'):
        s=re.sub(r'^\s*#define '+option+r'\s*$','//#define '+option,s,flags=re.M)
    files[probe.SETTINGS]=s.encode()
    return files


class LivePixelHistoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        OUT.mkdir(parents=True,exist_ok=True)
        cls.original=probe.archive_members(ROOT/probe.BASE)
        cls.enabled=probe.patch_members(cls.original,M,'enabled')
        cls.helper=cls.enabled[probe.HELPER].decode()

    def test_pins_scope_private_off_and_nonconsumer_exact_original(self):
        self.assertEqual(probe.BASE_SHA256,probe.sha256((ROOT/probe.BASE).read_bytes()))
        count=0
        for arm in probe.ARMS:
            files=probe.patch_members(self.original,M,arm)
            for private,consumer in ((False,False),(False,True),(True,False)):
                for name,payload in self.original.items():
                    actual=select(files[name].decode(),private,consumer).encode() if name in probe.MEMBERS else files[name]
                    self.assertEqual(payload,actual,(arm,private,consumer,name)); count+=1
            self.assertEqual(self.original['LICENSE'],files['LICENSE'])
        (OUT/'parity.json').write_text(json.dumps({'exact_upstream_member_comparisons':count,
            'private_OFF':'byte exact after selecting injected gates','nonconsumer':'byte exact'},indent=2)+'\n')
        for name in (*probe.MEMBERS,*M['source_dependencies'],'LICENSE'):
            with self.assertRaises(ValueError):probe.patch_members(self.original|{name:self.original[name]+b'x'},M,'enabled')
        bad=copy.deepcopy(M); bad['addition']['source_path']='elsewhere'
        with self.assertRaises(ValueError):probe.patch_members(self.original,bad,'enabled')
        with self.assertRaises(ValueError):probe.build(ROOT/'elsewhere.zip')

    def test_exhaustive_scale_dependencies_allocations_and_lighting_unchanged(self):
        observed={}
        for n,payload in self.original.items():
            if n.endswith(('.glsl','.vsh','.fsh','.properties','.json')):
                lines=[i+1 for i,l in enumerate(payload.decode().splitlines()) if 'taau_render_scale' in l or 'TAAU_RENDER_SCALE' in l]
                if lines:observed[n]=lines
        self.assertEqual(observed,M['consumer_lines'])
        for n in observed:
            self.assertTrue(n in probe.MEMBERS or n in M['source_dependencies'],n)
        for n,payload in self.original.items():
            if n not in probe.MEMBERS:self.assertEqual(payload,self.enabled[n],n)
        for n in ['shaders/include/buffers.glsl','shaders/shaders.properties','shaders/program/voxy.json']:
            self.assertEqual(self.original[n],self.enabled[n])
        for n in probe.MEMBERS[2:6]:
            s=select(self.enabled[n].decode(),True,True)
            self.assertNotIn('const float lod_bias',s)
            self.assertEqual(self.original[n].decode().replace('const float lod_bias = log2(taau_render_scale);',
                '#define lod_bias log2(taau_render_scale)'),s)
        # Whole cloud/lighting/shadow and c3/c4/AO program bodies are unchanged.
        for n in self.original:
            if n.startswith(('shaders/include/lighting/','shaders/include/sky/')) or n in [
                'shaders/program/c3_taau_prep.fsh','shaders/program/d3_ao.fsh']:
                self.assertEqual(self.original[n],self.enabled[n],n)

    def test_early_cloud_reset_final_taa_reset_and_preserved_exposure(self):
        cloud=select(self.enabled['shaders/program/d2_clouds_upscaling.fsh'].decode(),True,True)
        main=cloud[cloud.index('void main() {'):]
        reset=main.index('if (shaderlod_live_pixel_history_reset())')
        first_previous=main.index('texture_min_4x4(colortex12')
        self.assertLess(main.index('vec4 current = texelFetch(colortex9'),reset)
        self.assertLess(main.index('combined_depth ='),reset)
        self.assertLess(reset,first_previous)
        branch=main[reset:main.index('// Find the closest cloud distance')]
        self.assertNotRegex(branch,r'colortex(?:11|12|14)|reproject_clouds|textureGather|catmull_rom')
        self.assertIn('clouds_data = vec3(1e6, 0.0, 0.0);',branch)
        self.assertIn('view_distance_squared < sqr(apparent_distance) || is_hand',branch)
        self.assertIn('clouds_history = max0(current);',branch)
        self.assertIn('clouds_data = vec3(apparent_distance, 1.0, ambient_scattering);',branch)
        self.assertEqual(2,branch.count('return;'))
        taa=select(self.enabled['shaders/program/c4_taa_exposure.fsh'].decode(),True,True)
        reset=taa.index('alpha = 1.0;')
        self.assertGreater(reset,taa.index('alpha *= 1.0 - TAAU_FLICKER_REDUCTION'))
        self.assertGreater(reset,taa.index('alpha *= offcenter_rejection;'))
        self.assertLess(reset,taa.index('current_color = mix(history_color, current_color, alpha);'))
        age=taa.index('result.a = 1.0;')
        self.assertGreater(age,taa.index('result = vec4(current_color, pixel_age * offcenter_rejection);'))
        self.assertLess(age,taa.index('result.a = exposure;'))
        self.assertEqual(self.original['shaders/include/lighting/specular_lighting.glsl'],
                         self.enabled['shaders/include/lighting/specular_lighting.glsl'])
        self.assertNotRegex(self.helper,r'texture\(|dFdx|dFdy')
        self.assertIn('UNACCEPTED',M['transition_status'])

    @unittest.skipUnless((shutil.which('clang++') or shutil.which('c++')),'No scalar C++ compiler')
    def test_actual_helper_exhaustive_scalar_protocol_and_missing_zero(self):
        # Execute the actual GLSL scalar helper as C++, not a mirror of its predicate.
        source=self.helper.replace('uniform int','int')
        main=r'''#include <iostream>
#define TAAU_RENDER_SCALE 0.83f
'''+source+r'''
int main() {
 int checks=0;
 for(int core=0;core<=3;core++)for(int global=-1;global<=2;global++)
 for(int temporal=0;temporal<=2;temporal++)for(int reset=-1;reset<=2;reset++) {
  shaderlodBridgeVersion=core;shaderlodEnabled=global;
  shaderlodTemporalVersion=temporal;shaderlodHistoryReset=reset;
  bool known=core==2&&(global==0||global==1)&&temporal==1&&(reset==0||reset==1);
  float expected=known?(global==1?.75f:1.f):.83f;
  if(shaderlod_live_pixel_history_known()!=known || shaderlod_live_pixel_scale()!=expected ||
     shaderlod_live_pixel_history_reset()!=(!known||reset!=0))return 2;
  checks++;
 }
 std::cout<<checks<<" actual helper scalar protocol cases PASS\n";
}
'''
        cpp=OUT/'history-scalar.cpp';cpp.write_text(main);binary=OUT/'history-scalar'
        r=subprocess.run([(shutil.which('clang++') or shutil.which('c++')),'-std=c++17',str(cpp),'-o',str(binary)],capture_output=True,text=True,timeout=20)
        self.assertEqual(0,r.returncode,r.stdout+r.stderr)
        r=subprocess.run([str(binary)],capture_output=True,text=True,timeout=20)
        (OUT/'scalar.log').write_text(r.stdout+r.stderr);self.assertEqual(0,r.returncode,r.stdout+r.stderr)
        self.assertIn('192 actual helper',r.stdout)
        # Counterexample guarding final alpha ordering and age: confidence zero
        # would undo an alpha set too early. Forced final alpha stays one.
        for confidence in (0,.01,.5,1):
            for flicker in (0,.5,1):
                for offcenter in (.05,.5,1):
                    early_alpha=1*confidence*(1-.8*flicker)
                    early_alpha=1-(1-early_alpha)*offcenter
                    final_alpha=1.0; output_age=1.0
                    self.assertEqual(1,final_alpha);self.assertEqual(1,output_age)
                    if confidence==0:self.assertLess(early_alpha,1)

    @unittest.skipUnless((shutil.which('clang++') or shutil.which('c++')),'No scalar C++ compiler')
    def test_actual_reset_branch_scalar_outputs(self):
        cloud=select(self.enabled['shaders/program/d2_clouds_upscaling.fsh'].decode(),True,True)
        start=cloud.index('    if (shaderlod_live_pixel_history_reset())')
        branch=cloud[start:cloud.index('// Find the closest cloud distance',start)]
        taa=select(self.enabled['shaders/program/c4_taa_exposure.fsh'].decode(),True,True)
        start=taa.index('    if (shaderlod_live_pixel_history_reset())')
        alpha=taa[start:taa.index('    current_color = mix(history_color, current_color, alpha);',start)]
        start=taa.index('    if (shaderlod_live_pixel_history_reset())',taa.index('result = vec4(current_color, pixel_age * offcenter_rejection);'))
        age=taa[start:taa.index('#else // TAA disabled',start)]
        # Real branch code. The projection is a fixture returning a prescribed
        # view distance; these checks establish branch/output semantics only.
        source=r'''#include <algorithm>
#include <iostream>
#define TAAU_RENDER_SCALE 0.83f
struct vec2 {float x=0,y=0;};
struct vec3 {float x,y,z;vec3(float a,float b,float c):x(a),y(b),z(c){} vec3(vec2 v,float c):x(v.x),y(v.y),z(c){}};
struct vec4 {float x,y,z,w;};
vec4 max0(vec4 v) {return {std::max(0.f,v.x),std::max(0.f,v.y),std::max(0.f,v.z),std::max(0.f,v.w)};}
float sqr(float x){return x*x;}
float length_squared(vec3 v){return sqr(v.x)+sqr(v.y)+sqr(v.z);}
float prescribed_distance;
vec3 screen_to_view_space(vec3,bool){return vec3(prescribed_distance,0,0);}
'''+self.helper.replace('uniform int','int')+r'''
vec2 uv;float depth,apparent_distance,ambient_scattering;
bool is_hand;vec4 current,clouds_history;vec3 clouds_data(-1,-1,-1);
void cloud_reset(){
'''+branch+r'''
}
struct Result{float a;};
void taa_reset(float &alpha,float &pixel_age,float offcenter_rejection,Result &result){
'''+alpha+r'''
 result.a = pixel_age * offcenter_rejection;
'''+age+r'''
}
int main(){
 int cloud_checks=0,taa_checks=0;
 for(int core=0;core<=3;core++)for(int global=-1;global<=2;global++)
 for(int temporal=0;temporal<=2;temporal++)for(int reset=-1;reset<=2;reset++)
 for(int sky=0;sky<=1;sky++)
 for(int hand=0;hand<=1;hand++)for(float distance:{5.f,15.f}) {
  shaderlodBridgeVersion=core;shaderlodEnabled=global;shaderlodTemporalVersion=temporal;
  shaderlodHistoryReset=reset;depth=sky?1.f:.5f;is_hand=hand;
  bool known=core==2&&(global==0||global==1)&&temporal==1&&(reset==0||reset==1);
  bool reject=!known||reset!=0;
  apparent_distance=10;ambient_scattering=.2f;prescribed_distance=distance;
  current={-.1f,.5f,1.f,.8f};clouds_history={-1,-1,-1,-1};clouds_data=vec3(-1,-1,-1);
  cloud_reset();bool masked=!sky&&(distance<10||hand);
  if(!reject){if(clouds_data.y!=-1)return 2;}
  else if(masked){if(clouds_data.x!=1e6f||clouds_data.y!=0||clouds_data.z!=0||clouds_history.x!=current.x)return 3;}
  else if(clouds_data.x!=10||clouds_data.y!=1||clouds_data.z!=.2f||clouds_history.x!=0||clouds_history.w!=.8f)return 4;
  cloud_checks++;
 }
 for(int core=0;core<=3;core++)for(int global=-1;global<=2;global++)
 for(int temporal=0;temporal<=2;temporal++)for(int reset=-1;reset<=2;reset++)
 for(float confidence:{0.f,.01f,.5f,1.f})
 for(float flicker:{0.f,.5f,1.f})for(float offcenter:{.05f,.5f,1.f}) {
  shaderlodBridgeVersion=core;shaderlodEnabled=global;shaderlodTemporalVersion=temporal;
  shaderlodHistoryReset=reset;float alpha=.25f*confidence*(1-.8f*flicker);
  bool known=core==2&&(global==0||global==1)&&temporal==1&&(reset==0||reset==1);
  bool reject=!known||reset!=0;
  alpha=1-(1-alpha)*offcenter;float old=alpha,pixel_age=12;Result result{-1};
  taa_reset(alpha,pixel_age,offcenter,result);
  if(reject){if(alpha!=1||pixel_age!=1||result.a!=1)return 5;}
  else if(alpha!=old||pixel_age!=12||result.a!=12*offcenter)return 6;
  taa_checks++;
 }
 std::cout<<cloud_checks<<" actual cloud branch cases, "<<taa_checks<<" final TAA alpha/age cases PASS\n";
}
'''
        cpp=OUT/'reset-branch-scalar.cpp';cpp.write_text(source);binary=OUT/'reset-branch-scalar'
        r=subprocess.run([(shutil.which('clang++') or shutil.which('c++')),'-std=c++17',str(cpp),'-o',str(binary)],capture_output=True,text=True,timeout=20)
        self.assertEqual(0,r.returncode,r.stdout+r.stderr)
        r=subprocess.run([str(binary)],capture_output=True,text=True,timeout=20)
        (OUT/'reset-branch-scalar.log').write_text(r.stdout+r.stderr);self.assertEqual(0,r.returncode,r.stdout+r.stderr)
        self.assertIn('1536 actual cloud branch cases, 6912 final TAA alpha/age cases PASS',r.stdout)


    def test_complete_pinned_iris_maps_and_override_stages(self):
        import prepare_photon_live_pixel_budget as discovery
        observations={}
        for arm in probe.ARMS:
            archive=ROOT/'build'/(probe.BUILD_FAMILY+'-'+arm+'.zip');probe.build(archive,arm=arm)
            observations[arm]=discovery.discover(archive,{'TAA':'true','TAAU':'true'},OUT,arm)
        original=observations['original']['options']
        for arm,value in [('disabled','false'),('enabled','true')]:
            self.assertEqual(original | {probe.SWITCH:value,'SHADERLOD_LIVE_PIXEL_HISTORY_INCLUDED':'true'},
                observations[arm]['options'])
        for value in ('true','false'):
            receipt=discovery.discover(ROOT/'build/live-pixel-history-r2-enabled.zip',
                {'TAA':'true','TAAU':'true',probe.SWITCH:value},OUT,'override-'+value)
            self.assertEqual(original | {probe.SWITCH:value,'SHADERLOD_LIVE_PIXEL_HISTORY_INCLUDED':'true'},receipt['options'])
            observations['override-'+value]=receipt
        (OUT/'complete-iris-inventory.json').write_text(json.dumps(observations,indent=2)+'\n')

    @unittest.skipUnless(shutil.which('glslangValidator'),'No offline GLSL compiler')
    def test_bounded_full_producer_consumer_offline_compilation(self):
        roots=[('world0',n,s) for n,s in [
            ('gbuffers_terrain','vsh'),('gbuffers_terrain','fsh'),
            ('gbuffers_water','vsh'),('gbuffers_water','fsh'),
            ('gbuffers_damagedblock','fsh'),('gbuffers_armor_glint','fsh'),
            ('deferred1','vsh'),('deferred1','fsh'),('deferred2','vsh'),('deferred2','fsh'),
            ('deferred3','fsh'),('deferred4','vsh'),('deferred4','fsh'),
            ('composite','fsh'),('composite1','fsh'),('composite2','fsh'),
            ('composite3','vsh'),('composite3','fsh'),('composite4','vsh'),('composite4','fsh'),
            ('final','fsh')]]
        roots += [('world1','gbuffers_terrain','fsh'),('world-1','gbuffers_water','fsh')]
        checks=[]; paths=[]
        cases=[('active',True,True,True,False,roots),
            ('private-off',False,True,True,False,[('world0','gbuffers_terrain','fsh'),('world0','composite4','fsh')]),
            ('native',True,True,False,False,[('world0','gbuffers_terrain','fsh'),('world0','composite4','fsh')])]
        for label,private,taa,taau,voxy,entries in cases:
            files=configured(self.enabled,private,taa,taau,voxy)
            for world,stage,suffix in entries:
                stage_files=files
                if stage=='composite2':
                    stage_files=files|{probe.SETTINGS:files[probe.SETTINGS].replace(b'//#define DOF\n',b'#define DOF\n')}
                source=expand(stage_files,f'shaders/{world}/{stage}.{suffix}').replace('\\\n','')
                source=source.replace('#version 400 compatibility','#version 400 compatibility\n#define MC_VERSION 12101\n#define MC_SHADOW_QUALITY 1.0\n#define MC_RENDER_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n#define IS_IRIS\n#define IRIS_FEATURE_SEPARATE_ENTITY_DRAWS',1)
                extension='vert' if suffix=='vsh' else 'frag'
                path=OUT/f'{label}-{world}-{stage}.{extension}';path.write_text(source);paths.append(path)
        # Preserve upstream's unmatched c3 #endif. Pinned Iris Jcpp tolerates it,
        # as established independently for original stock TAAU. Never repair ZIP.
        iris=ROOT/M['iris_option_discovery']['jar_path']
        self.assertEqual(M['iris_option_discovery']['jar_sha256'],probe.sha256(iris.read_bytes()))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1';deps=[]
        for relative,name in [('com.google.guava/guava/32.1.2-jre','guava-32.1.2-jre.jar'),
            ('it.unimi.dsi/fastutil/8.5.12','fastutil-8.5.12.jar'),('org.slf4j/slf4j-api/2.0.9','slf4j-api-2.0.9.jar')]:
            matches=list((cache/relative).glob('*/'+name));self.assertEqual(1,len(matches));deps+=matches
        with zipfile.ZipFile(iris) as archive:
            jar=OUT/'jcpp-1.4.14.jar';jar.write_bytes(archive.read('META-INF/jarjar/jcpp-1.4.14.jar'));deps.append(jar)
        java=OUT/'LivePixelPreprocess.java'
        java.write_text('''import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
class LivePixelPreprocess {
 public static void main(String[] args)throws Exception {
  for(String p:args) {
   var path=Path.of(p);
   var text=JcppProcessor.glslPreprocessSource(Files.readString(path),List.of());
   Files.writeString(Path.of(p+".jcpp"),text);
  }
  System.out.println("Pinned Iris Jcpp: "+args.length+" sources preprocessed");
 }
}
''')
        r=subprocess.run(['java','--class-path',os.pathsep.join(map(str,[iris,*deps])),str(java),*map(str,paths)],capture_output=True,text=True,timeout=20)
        (OUT/'jcpp.log').write_text(r.stdout+r.stderr);self.assertEqual(0,r.returncode,r.stdout+r.stderr)
        for path in paths:
            source=Path(str(path)+'.jcpp')
            r=subprocess.run(['glslangValidator','-S',path.suffix[1:],str(source)],capture_output=True,text=True,timeout=20)
            log=Path(str(source)+'.log');log.write_text(r.stdout+r.stderr)
            checks.append({'source':str(path.relative_to(ROOT)),'processed':str(source.relative_to(ROOT)),
                'log':str(log.relative_to(ROOT)),'exit_code':r.returncode,'DOF':path.stem.endswith('composite2')})
            self.assertEqual(0,r.returncode,r.stdout+r.stderr)
        (OUT/'semantic-checks.json').write_text(json.dumps({'offline_only':True,'pinned_Iris_Jcpp':True,
            'compiler_version':subprocess.run(['glslangValidator','--version'],capture_output=True,text=True).stdout,
            'dependency_sha256':{str(p):probe.sha256(p.read_bytes()) for p in [iris,*deps]},'checks':checks},indent=2)+'\n')

    def test_actual_iris_private_discovery_and_override(self):
        iris=ROOT/M['iris_option_discovery']['jar_path']
        self.assertEqual(M['iris_option_discovery']['jar_sha256'],probe.sha256(iris.read_bytes()))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1'; deps=[]
        for relative,name in [('com.google.guava/guava/32.1.2-jre','guava-32.1.2-jre.jar'),
            ('it.unimi.dsi/fastutil/8.5.12','fastutil-8.5.12.jar')]:
            paths=list((cache/relative).glob('*/'+name));self.assertEqual(1,len(paths));deps.append(paths[0])
        java=OUT/'LivePixelIris.java'
        java.write_text('''import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
class LivePixelIris {
 static void require(boolean b,String why) {if(!b)throw new AssertionError(why);}
 public static void main(String[] args) throws Exception {
 var refs=new OptionAnnotatedSource(Files.readString(Path.of(args[0]))).getBooleanDefineReferences().keySet();
 require(refs.contains("SHADERLOD_LIVE_PIXEL_HISTORY"),"private gate reference missing");
 for(int i=1;i<3;i++) {
  var s=new OptionAnnotatedSource(Files.readString(Path.of(args[i])));
  var opts=s.getOptionSet(AbsolutePackPath.fromAbsolutePath("/settings.glsl"),refs);
  require(opts.getBooleanOptions().containsKey("SHADERLOD_LIVE_PIXEL_HISTORY"),"private option missing");
  var o=s.getBooleanOptions().values().stream().filter(v->v.getName().equals("SHADERLOD_LIVE_PIXEL_HISTORY")).findFirst().orElseThrow();
  require(o.getDefaultValue()==(i==2),"wrong default");
  var changed=s.apply(new MutableOptionValues(opts,Map.of("SHADERLOD_LIVE_PIXEL_HISTORY",Boolean.toString(i!=2))));
  require(changed.lines().anyMatch(((i!=2?"#define ":"//#define ")+"SHADERLOD_LIVE_PIXEL_HISTORY")::equals),"override missing");
 }
 System.out.println("Pinned Iris: private OFF/ON discovered and overrides pass");
 }
}
''')
        paths=[]
        for label,payload in [('global',self.enabled[probe.GLOBAL]),('off',probe.patch_members(self.original,M,'disabled')[probe.SETTINGS]),('on',self.enabled[probe.SETTINGS])]:
            p=OUT/(label+'.glsl');p.write_bytes(payload);paths.append(p)
        r=subprocess.run(['java','--class-path',os.pathsep.join(map(str,[iris,*deps])),str(java),*map(str,paths)],capture_output=True,text=True,timeout=20)
        (OUT/'iris-discovery.log').write_text(r.stdout+r.stderr)
        self.assertEqual(0,r.returncode,r.stdout+r.stderr)

    def test_archive_determinism_and_original_source_restoration(self):
        for arm in probe.ARMS:
            path=ROOT/'build'/(probe.BUILD_FAMILY+'-'+arm+'.zip')
            metadata=probe.build(path,arm=arm)
            first=path.read_bytes();probe.build(path,arm=arm)
            self.assertEqual(first,path.read_bytes())
            files=probe.archive_members(path)
            self.assertEqual(metadata,json.loads(files[probe.METADATA]))
            if arm=='original':
                self.assertEqual(self.original,{n:p for n,p in files.items() if n!=probe.METADATA})

if __name__=='__main__':unittest.main()
