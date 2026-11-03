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
import build_photon_live_pixel_budget as probe
M=json.loads((ROOT/probe.MANIFEST).read_text())
OUT=ROOT/'build/portable-render-budget-source-checks'


def select(source, private=False, consumer=False):
    """Select only injected gates, keeping all original preprocessor bytes intact."""
    source=re.sub(r'// ShaderLOD private live pixel-budget diagnostic; default off\.\n(?://)?#define '+probe.SWITCH+r'\n','',source)
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


class LivePixelBudgetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        OUT.mkdir(parents=True,exist_ok=True)
        cls.original=probe.archive_members(ROOT/probe.BASE)
        cls.enabled=probe.patch_members(cls.original,M,'enabled')
        cls.helper=cls.enabled[probe.HELPER].decode()
        cls.common=cls.enabled[probe.COMMON_HELPER].decode()

    def test_pins_scope_and_exact_native_source_recovery(self):
        self.assertEqual(probe.BASE_SHA256,probe.sha256((ROOT/probe.BASE).read_bytes()))
        count=0
        for arm in probe.ARMS:
            files=probe.patch_members(self.original,M,arm)
            for name,payload in self.original.items():
                actual=files[name]
                if arm!='original' and name in probe.MEMBERS:
                    if name==probe.SETTINGS and arm=='enabled':
                        actual=actual.replace(('#define '+probe.SWITCH+'\n').encode(),('//#define '+probe.SWITCH+'\n').encode(),1)
                    entry=next(e for e in M['members'] if e['path']==name)
                    for replacement in reversed(entry['replacements']):
                        self.assertEqual(1,actual.count(replacement['after'].encode()),name)
                        actual=actual.replace(replacement['after'].encode(),replacement['before'].encode(),1)
                self.assertEqual(payload,actual,(arm,name));count+=1
            self.assertEqual(self.original['LICENSE'],files['LICENSE'])
        # Literal recipe recovery is source ownership evidence. Actual fallback
        # source is preprocessed below; this does not claim runtime parity.
        (OUT/'parity.json').write_text(json.dumps({'exact_upstream_member_recoveries':count,
            'method':'reverse every exact pinned sequential replacement; not runtime evidence'},indent=2)+'\n')
        for name in (*probe.MEMBERS,*M['source_dependencies'],'LICENSE'):
            with self.assertRaises(ValueError):probe.patch_members(self.original|{name:self.original[name]+b'x'},M,'enabled')
        for key in ('addition','common_addition'):
            bad=copy.deepcopy(M);bad[key]['source_path']='elsewhere'
            with self.assertRaises(ValueError):probe.patch_members(self.original,bad,'enabled')
        with self.assertRaises(ValueError):probe.patch_members(self.original|{probe.COMMON_HELPER:b'conflict'},M,'enabled')
        with self.assertRaises(ValueError):probe.build(ROOT/'elsewhere.zip')

    def test_exhaustive_scale_dependencies_and_declared_source_ownership(self):
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
        # The maintained recipe now integrates independent cloud, AO, water,
        # shadow and spatial effects. Every edit must remain declared and pinned;
        # unrelated source/dependency bytes stay exact.
        self.assertEqual(set(probe.MEMBERS),{e['path'] for e in M['members']})
        for name,expected in M['source_dependencies'].items():
            self.assertEqual(expected,probe.sha256(self.original[name]))
            if name not in probe.MEMBERS:self.assertEqual(self.original[name],self.enabled[name])

    def test_primary_cloud_join_native_source_and_nonselected_parity(self):
        name='shaders/include/sky/clouds/cumulus.glsl'
        cloud=self.enabled[name].decode(); native=self.original[name].decode()
        self.assertEqual(native.split('float clouds_cumulus_density(vec3 pos)',1)[1].split('float clouds_cumulus_optical_depth(',1)[0],
            cloud.split('float clouds_cumulus_density(vec3 pos)',1)[1].split('#if defined SHADERLOD_LIVE_PIXEL_BUDGET',1)[0])
        self.assertIn('if (!shaderlod_optimization_enabled(optimization_flag))',cloud)
        self.assertIn('if (!shaderlodNativeZero)',cloud)
        self.assertEqual(1,cloud.count('float density = shaderlod_cumulus_primary_density(ray_pos, shaderlodOptCloudPrimaryDetail);'))
        self.assertIn('clouds_cumulus_density(ray_pos + ray_step.xyz * dither)',cloud)
        self.assertEqual(1,cloud.count('shaderlod_cumulus_primary_density(ray_pos + ray_step.xyz * dither, shaderlodOptCloudSecondaryDetail)'))
        # All native density/optical-depth/scattering source can be recovered
        # from the authentic archive with only the declared literal replacements.
        recovered=cloud
        entry=next(e for e in M['members'] if e['path']==name)
        for replacement in reversed(entry['replacements']):
            self.assertEqual(1,recovered.count(replacement['after']))
            recovered=recovered.replace(replacement['after'],replacement['before'],1)
        self.assertEqual(native,recovered)
        # The native density implementation remains exact in nonselected contexts.
        for defines in ['', '#define SHADERLOD_LIVE_PIXEL_BUDGET\n',
                '#define WORLD_OVERWORLD\n#define PROGRAM_DEFERRED1\n']:
            texts=[]
            for files in [self.original,self.enabled]:
                p=subprocess.run(['clang','-E','-P','-x','c','-'],input=defines+expand(files,name),text=True,capture_output=True,timeout=20)
                self.assertEqual(0,p.returncode,p.stderr);texts.append(p.stdout)
            def density(text):
                start=text.index('float clouds_cumulus_density(')
                opening=text.index('{',start);depth=1;end=opening+1
                while depth:
                    depth+=(text[end]=='{')-(text[end]=='}');end+=1
                return text[start:end]
            # The integrated recipe adds independent optional helper functions.
            # Verify the native density body, without a whole-program parity claim.
            self.assertEqual(density(texts[0]),density(texts[1]))
        # Default native-AA and AA-off selected wrappers both declare core once;
        # inherited raster scaling remains absent when native TAAU is disabled.
        for taa in [True,False]:
            files=configured(self.enabled,taa=taa,taau=False)
            source=re.sub(r'(?m)^\s*#(?:version|extension)\b[^\n]*','',expand(files,'shaders/world0/deferred1.fsh'))
            p=subprocess.run(['clang','-E','-P','-x','c','-'],input=source,text=True,capture_output=True,timeout=20)
            self.assertEqual(0,p.returncode,p.stderr)
            self.assertEqual(1,p.stdout.count('uniform int shaderlodBridgeVersion;'))
            self.assertIn('float shaderlod_cumulus_primary_density(',p.stdout)
            self.assertNotIn('float shaderlod_live_pixel_scale()',p.stdout)

    def test_policy_fallback_early_work_and_transition_hazard(self):
        self.assertIn('bridge_version != 2',self.common)
        self.assertIn('(enabled != 0 && enabled != 1)',self.common)
        self.assertIn('shaderlodEnabled, shaderlodPreset, TAAU_RENDER_SCALE',self.helper)
        self.assertEqual(self.common.encode(),(ROOT/M['common_addition']['source_path']).read_bytes())
        self.assertNotIn('texture(',self.helper+self.common); self.assertNotIn('dFdx',self.helper+self.common)
        self.assertNotRegex(self.common,r'(?m)^\s*uniform\s')
        # This is early vertex raster coverage reduction, not a late fragment gate.
        for n in ['gbuffers_all_solid','gbuffers_all_translucent','d1_clouds','d2_clouds_upscaling','d4_deferred_shading','c3_taau_prep']:
            self.assertIn('taau_render_scale',self.enabled['shaders/program/'+n+'.vsh'].decode())
        cloud=self.enabled['shaders/program/d2_clouds_upscaling.fsh'].decode()
        self.assertIn('previous_uv_clamped * taau_render_scale',cloud)
        # Counterexample: history written at .75 cannot be read correctly at 1.
        prior_uv=.8*.75; wrongly_read_uv=.8*1.0
        self.assertAlmostEqual(.2,wrongly_read_uv-prior_uv)
        self.assertIn('UNACCEPTED',M['transition_status'])
        self.assertIn('!defined VOXY',self.enabled[probe.GLOBAL].decode())
        self.assertIn('renderScale',self.original['shaders/program/voxy.json'].decode())

    @unittest.skipUnless(shutil.which('clang++'),'No host compiler for pure GLSL policy semantics')
    def test_actual_shared_policy_presets_and_selected_stock_scale(self):
        # The sample-free GLSL helper runs in C++ with a GLSL min intrinsic shim;
        # execute its actual body,
        # rather than a Python reimplementation. This does not prove GPU behavior.
        cases=[]
        for stock in (1.0,.8,.5):
            for preset,cap in ((0,.875),(1,.75),(2,.667),(3,1.0)):
                cases.extend([(2,1,preset,stock,min(stock,cap)),(2,0,preset,stock,stock)])
            for bridge,enabled,preset in ((0,1,1),(1,1,1),(3,1,1),(2,-1,1),(2,2,1),(2,1,-1),(2,1,4)):
                cases.append((bridge,enabled,preset,stock,stock))
        source=OUT/'render-budget-policy.cpp'
        rows='\n'.join(f'    {{{bridge}, {enabled}, {preset}, {stock}f, {expected}f}},'
            for bridge,enabled,preset,stock,expected in cases)
        source.write_text('#include <algorithm>\n#include <cmath>\n#include <limits>\n#include <iostream>\nfloat min(float a,float b) { return std::min(a,b); }\n'+self.common+'''
struct Case { int bridge, enabled, preset; float stock, expected; };
int main() {
    Case cases[] = {
'''+rows+'''
    };
    for (auto c : cases) {
        float actual = shaderlod_render_budget_scale(c.bridge,c.enabled,c.preset,c.stock);
        if (std::abs(actual-c.expected)>1e-6f) return 1;
    }
    for (float invalid : {0.0f,-1.0f,2.0f,std::numeric_limits<float>::quiet_NaN(),std::numeric_limits<float>::infinity()}) {
        if (shaderlod_render_budget_scale(0,1,1,invalid)!=1.0f) return 2;
        if (shaderlod_render_budget_scale(2,0,1,invalid)!=1.0f) return 3;
        if (shaderlod_render_budget_scale(2,1,1,invalid)!=1.0f) return 4;
    }
    std::cout << "Actual shared GLSL policy: " << sizeof(cases)/sizeof(Case) << " selected-scale/preset cases + 15 invalid-scale cases passed\\n";
}
''')
        executable=OUT/'render-budget-policy'
        result=subprocess.run(['clang++','-std=c++11',str(source),'-o',str(executable)],capture_output=True,text=True,timeout=20)
        (OUT/'render-budget-policy-compile.log').write_text(result.stdout+result.stderr)
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        result=subprocess.run([str(executable)],capture_output=True,text=True,timeout=10)
        (OUT/'render-budget-policy.log').write_text(result.stdout+result.stderr)
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)

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
            ('native',True,True,False,False,[('world0','gbuffers_terrain','fsh'),('world0','composite4','fsh'),('world0','deferred1','fsh')]),
            ('aa-off',True,False,False,False,[('world0','deferred1','fsh')])]
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
 require(refs.contains("SHADERLOD_LIVE_PIXEL_BUDGET"),"private gate reference missing");
 for(int i=1;i<3;i++) {
  var s=new OptionAnnotatedSource(Files.readString(Path.of(args[i])));
  var opts=s.getOptionSet(AbsolutePackPath.fromAbsolutePath("/settings.glsl"),refs);
  require(opts.getBooleanOptions().containsKey("SHADERLOD_LIVE_PIXEL_BUDGET"),"private option missing");
  var o=s.getBooleanOptions().values().stream().filter(v->v.getName().equals("SHADERLOD_LIVE_PIXEL_BUDGET")).findFirst().orElseThrow();
  require(o.getDefaultValue()==(i==2),"wrong default");
  var changed=s.apply(new MutableOptionValues(opts,Map.of("SHADERLOD_LIVE_PIXEL_BUDGET",Boolean.toString(i!=2))));
  require(changed.lines().anyMatch(((i!=2?"#define ":"//#define ")+"SHADERLOD_LIVE_PIXEL_BUDGET")::equals),"override missing");
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
            path=ROOT/'build'/('live-pixel-budget-portable-'+arm+'.zip')
            metadata=probe.build(path,arm=arm)
            first=path.read_bytes();probe.build(path,arm=arm)
            self.assertEqual(first,path.read_bytes())
            files=probe.archive_members(path)
            self.assertEqual(metadata,json.loads(files[probe.METADATA]))
            self.assertEqual(sorted(metadata['added_shader_files']),[] if arm=='original' else sorted((probe.HELPER,probe.COMMON_HELPER,*probe.EXTRA_HELPERS)))
            if arm=='original':
                self.assertEqual(self.original,{n:p for n,p in files.items() if n!=probe.METADATA})

if __name__=='__main__':unittest.main()
