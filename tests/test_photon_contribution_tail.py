"""Pinned source and finite-series tests; no client, Gradle or GPU operations."""
import copy
import itertools
import json
import math
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import build_photon_contribution_tail as probe
M=json.loads((ROOT/probe.MANIFEST).read_text())
ISO=1/(4*math.pi)

def function(source,signature):
    start=source.index(signature); bracket=source.index('{',start); depth=1; end=bracket+1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}'); end+=1
    return source[start:end]

def select(source,private=False,stage=False):
    source=source.replace(M['option_declaration'],'').replace(M['option_declaration'].replace('//#define','#define'),'')
    result=[]; stack=[]
    for line in source.splitlines(keepends=True):
        if line=='#ifdef '+probe.SWITCH+'\n': stack.append(private)
        elif line==M['profile_condition']+'\n': stack.append(stage)
        elif line in ('#else // '+probe.SWITCH+'\n','#else // SHADERLOD_TAIL_STAGE\n'): stack[-1]=not stack[-1]
        elif line in ('#endif // '+probe.SWITCH+'\n','#endif // SHADERLOD_TAIL_STAGE\n'): stack.pop()
        elif not stack or all(stack): result.append(line)
    if stack: raise AssertionError('Unclosed guards')
    return ''.join(result)

def expand(files,name):
    return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*',lambda m:expand(files,'shaders'+m[1] if m[1].startswith('/') else str(Path(name).parent/m[1])),files[name].decode(),flags=re.M)

def f32(v):
    try: return struct.unpack('f',struct.pack('f',v))[0]
    except OverflowError: return math.copysign(math.inf,v)

def recurrence(args,quantize=lambda x:x):
    """Original eight-order recurrence and actual proposed order-4 finite bound."""
    q=quantize; density,c,ld,sd,gd,sc,ex,shadow,bounce=args
    mul=lambda a,b:q(q(a)*q(b)); add=lambda a,b:q(q(a)+q(b))
    hg=lambda c,g:q(q(ISO*(1-g*g))/q(q(1+g*g-2*g*c)*q(math.sqrt(q(1+g*g-2*g*c)))))
    hg_peak=lambda g:q(q(ISO*(1+g))/mul(1-g,1-g))
    phase=add(mul(.8,max(q(2600/(2*math.pi*(2600-2600*c+1)*math.log(5201))),hg(c,.8))),mul(.2,hg(c,-.2)))
    p=q(math.pi*density/(density+.15)); t=q(.8*(c*.5+.5)**2); p=add(mul(p,1-t),t)
    lift=q(min(sc/.1,1)*1.33/(1+min(sc/.1,1)*.33)); t=q(c*.5+.5)
    falloff=mul(.55,add(mul(lift,1-t),t)); a=q(sc); e=q(ex)
    g=[q(v**(1+ld)) for v in (.6,.9,.3)]; orders=[]; bound=None
    for i in range(8):
        attenuation=[q(math.exp(-mul(e,d))) for d in (ld,sd,gd)]
        x=add(add(mul(mul(mul(a,attenuation[0]),phase),1-.5*shadow),mul(mul(mul(a,attenuation[2]),ISO),bounce)),mul(mul(mul(mul(a,attenuation[1]),ISO),shadow),.5))
        y=mul(mul(a,attenuation[1]),ISO); orders.append((x,y))
        a=mul(a,mul(falloff,p)); e=mul(e,.4); g=[mul(v,.8) for v in g]
        p=add(mul(p,.5),mul(q(math.sqrt(p)),.5))
        if i==3:
            aa,pp=a,p; amp_sum=aa
            for j in range(3):
                aa=mul(aa,mul(falloff,pp)); pp=add(mul(pp,.5),mul(q(math.sqrt(pp)),.5)); amp_sum=add(amp_sum,aa)
            phase_peak=q(sum(mul(w,hg_peak(v)) for w,v in zip((.65,.1,.25),g)))
            atten=[q(math.exp(-mul(mul(e,.064),d))) for d in (ld,sd,gd)]
            bx=mul(amp_sum,add(mul(mul(atten[0],phase_peak),1-.5*shadow),mul(ISO,add(mul(atten[2],bounce),mul(mul(atten[1],.5),shadow)))))
            by=mul(mul(amp_sum,atten[1]),ISO); bound=(bx,by)
        phase=q(sum(mul(w,hg(c,v)) for w,v in zip((.65,.1,.25),(g[0],g[1],-g[2]))))
    prefix=tuple(q(sum(v[k] for v in orders[:4])) for k in (0,1))
    tail=tuple(q(sum(v[k] for v in orders[4:])) for k in (0,1))
    eligible=all(math.isfinite(s) and 1e-20<=s<=1e20 and math.isfinite(b) and 0<=b<=1e20 and mul(b,1.01)<=mul(s,q(.02*.99)) for s,b in zip(prefix,bound))
    return prefix,tail,bound,eligible

class ContributionTailTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original=probe.archive_members(ROOT/probe.BASE)
        cls.enabled=probe.patch_members(cls.original,M,'enabled')
        cls.helper=cls.enabled[probe.HELPER].decode()

    def test_exact_off_nonconsumer_scope_and_pins(self):
        self.assertEqual(probe.BASE_SHA256,probe.sha256((ROOT/probe.BASE).read_bytes()))
        comparisons=0
        for arm in probe.ARMS:
            files=probe.patch_members(self.original,M,arm)
            for private,stage in ((False,False),(False,True),(True,False)):
                for name,payload in self.original.items():
                    actual=select(files[name].decode(),private,stage).encode() if name in probe.MEMBERS else files[name]
                    self.assertEqual(payload,actual,(arm,private,stage,name)); comparisons+=1
        self.assertEqual(4779,comparisons)
        for name in (*probe.MEMBERS,*probe.DEPENDENCIES,'LICENSE'):
            with self.assertRaises(ValueError): probe.patch_members(self.original|{name:self.original[name]+b'x'},M,'enabled')
        bad=copy.deepcopy(M); bad['component_error_budget']=.05
        with self.assertRaises(ValueError): probe.patch_members(self.original,bad,'enabled')
        with self.assertRaises(ValueError): probe.build(ROOT/'elsewhere.zip')

    def test_original_body_guard_and_no_sampling_changes(self):
        upstream=self.original[probe.CUMULUS].decode(); active=select(self.enabled[probe.CUMULUS].decode(),True,True)
        original=function(upstream,'vec2 clouds_cumulus_scattering(')
        retained=function(active,'vec2 shaderlod_tail_original_scattering(').replace('shaderlod_tail_original_scattering(','clouds_cumulus_scattering(',1)
        self.assertEqual(original,retained)
        clone=function(self.helper,'vec2 shaderlod_tail_scattering(').replace('shaderlod_tail_scattering(','clouds_cumulus_scattering(',1)
        begin=clone.index('        // State now describes order i+1.')
        end=clone.index('        phase = clouds_phase_multi',begin)
        self.assertEqual(original,clone[:begin]+clone[end:])
        for signature in ('float clouds_cumulus_density(', 'float clouds_cumulus_optical_depth(', 'CloudsResult draw_cumulus_clouds('):
            self.assertEqual(function(upstream,signature),function(active,signature))
        wrapper=function(self.helper,'vec2 clouds_cumulus_scattering(')
        self.assertLess(wrapper.index('shaderlodBridgeVersion != 2'),wrapper.index('shaderlod_tail_scattering('))
        for token in ('texture(', 'textureLod(', 'dFdx(', 'dFdy('): self.assertNotIn(token,self.helper)
        self.assertIn('i == 3u',self.helper); self.assertIn('prefix * (0.02 * 0.99)',self.helper)
        self.assertIn('last_extinction = extinction * 0.064',self.helper)

    def test_analytic_and_float_envelope(self):
        grid=itertools.product((.001,.03,.2,1),(-1,0,.9,1),(0,10,1000),(0,10,1000),(0,1000),(.025,.1),(.025,.1),(0,.5,1),(0,.1))
        counts={'cases':0,'component_bounds':0,'float_eligible':0,'double_eligible':0}
        for args in grid:
            prefix,tail,bound,eligible=recurrence(args)
            fp,ft,fb,fe=recurrence(args,f32)
            for t,b in zip(tail,bound): self.assertLessEqual(t,b*(1+1e-12)+1e-300); counts['component_bounds']+=1
            # Numerical margin is explicitly checked against double original,
            # not merely the same rounded float expression used for eligibility.
            if fe:
                for s,t in zip(prefix,tail): self.assertLessEqual(t,.02*s)
                counts['float_eligible']+=1
            if eligible: counts['double_eligible']+=1
            counts['cases']+=1
        self.assertGreater(counts['float_eligible'],0)
        folder=ROOT/'build/contribution-tail-source-checks'; folder.mkdir(parents=True,exist_ok=True)
        (folder/'analytic.json').write_text(json.dumps(counts|{'scope':'synthetic, not runtime eligibility; f32 model is not a GPU driver proof'},indent=2)+'\n')

    def test_blind_cut_falsifier_and_edgecases(self):
        p,t,b,eligible=recurrence((.5,0,100,100,100,.05,.05,.5,.1))
        self.assertGreater(max(v/(s+v) for s,v in zip(p,t)),.40); self.assertFalse(eligible)
        # Extreme sky depth makes a weak retained ambient component. Never
        # infer absence of later orders from near-zero early attenuation.
        p,t,b,eligible=recurrence((1,0,0,1e20,0,.1,.1,1,0),f32)
        self.assertFalse(eligible)
        self.assertIn('any(lessThan(prefix, vec2(1e-20)))',self.helper)
        for name in ('density','light_depth','sky_depth','ground_depth','bounced_light','step_transmittance'):
            self.assertIn('shaderlod_tail_finite_nonnegative('+name+')',self.helper)
        self.assertIn('abs(cos_theta) <= 1.0',self.helper)
        self.assertIn('clouds_params.l0_extinction_coeff >= 1e-20',self.helper)
        self.assertIn('clouds_params.l0_shadow <= 1.0',self.helper)

    @unittest.skipUnless(shutil.which('glslangValidator'),'No offline compiler')
    def test_complete_active_and_nonconsumer_semantic_compile(self):
        folder=ROOT/'build/contribution-tail-source-checks'; folder.mkdir(parents=True,exist_ok=True)
        for world,stage,arm in [('world0','deferred1','enabled'),('world0','deferred1','disabled'),('world0','deferred','enabled'),('world1','deferred1','enabled')]:
            files=probe.patch_members(self.original,M,arm)
            settings=files[probe.SETTINGS].decode()
            for opt in ('COLORED_LIGHTS','SH_SKYLIGHT','ENVIRONMENT_REFLECTIONS','SKY_REFLECTIONS'):
                settings=re.sub(r'^\s*#define '+opt+r'\s*$','//#define '+opt,settings,flags=re.M)
            files[probe.SETTINGS]=settings.encode()
            source=expand(files,f'shaders/{world}/{stage}.fsh').replace('\\\n','')
            source=source.replace('#version 400 compatibility','#version 400 compatibility\n#define MC_VERSION 12101\n#define MC_SHADOW_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n#define IS_IRIS\n#define IRIS_FEATURE_SEPARATE_ENTITY_DRAWS',1)
            path=folder/f'{world}-{stage}-{arm}.frag'; path.write_text(source)
            result=subprocess.run(['glslangValidator','-S','frag',str(path)],capture_output=True,text=True,timeout=20)
            (folder/(path.name+'.log')).write_text(result.stdout+result.stderr)
            self.assertEqual(0,result.returncode,result.stdout+result.stderr)

    FULL_INVENTORY_JAVA = 'import java.nio.file.*; import java.util.*; import java.util.zip.*;\nimport java.nio.charset.StandardCharsets;\nimport com.google.common.collect.ImmutableList; import com.google.common.collect.ImmutableMap;\nimport net.irisshaders.iris.shaderpack.option.*; import net.irisshaders.iris.shaderpack.include.*;\nclass TailFullInventory {\n public static void main(String[] a) throws Exception {\n  var nodes=ImmutableMap.<AbsolutePackPath,FileNode>builder(); var refs=new TreeMap<String,Set<String>>();\n  try(var zip=new ZipFile(a[0])) {\n   for(var entry:Collections.list(zip.entries())) {\n    String name=entry.getName();\n    if(!name.startsWith("shaders/")||!(name.endsWith(".glsl")||name.endsWith(".vsh")||name.endsWith(".fsh")||name.endsWith(".csh")||name.endsWith(".gsh")||name.endsWith(".tcs")||name.endsWith(".tes")))continue;\n    String text=new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8);\n    var path=AbsolutePackPath.fromAbsolutePath("/"+name.substring(8));\n    nodes.put(path,new FileNode(path,ImmutableList.copyOf(text.split("\\\\R"))));\n    for(String key:new OptionAnnotatedSource(text).getBooleanDefineReferences().keySet())refs.computeIfAbsent(key,k->new TreeSet<>()).add(name);\n   }\n  }\n  var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);\n  var graph=constructor.newInstance(nodes.buildOrThrow(),ImmutableMap.of());\n  var p=new Properties();try(var reader=Files.newBufferedReader(Path.of(a[1]))){p.load(reader);}\n  var overrides=new TreeMap<String,String>();for(String key:p.stringPropertyNames())overrides.put(key,p.getProperty(key));\n  var options=new ShaderPackOptions(graph,overrides);var values=options.getOptionValues();var all=new TreeMap<String,String>();\n  for(String key:options.getOptionSet().getBooleanOptions().keySet())all.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));\n  for(String key:options.getOptionSet().getStringOptions().keySet())if(all.put(key,values.getStringValueOrDefault(key))!=null)throw new AssertionError("duplicate option "+key);\n  for(var entry:all.entrySet())System.out.println("OPTION\\t"+entry.getKey()+"\\t"+entry.getValue());\n  for(var entry:refs.entrySet())System.out.println("REFERENCE\\t"+entry.getKey()+"\\t"+String.join(",",entry.getValue()));\n  System.out.println("SOURCE_COUNT\\t"+graph.getNodes().size());\n }\n}\n'

    def test_actual_pinned_iris_discovery_override_no_stage_leak(self):
        iris=ROOT/M['iris_option_discovery']['jar_path']
        self.assertEqual(M['iris_option_discovery']['jar_sha256'],probe.sha256(iris.read_bytes()))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1'; deps=[]
        for relative,filename in [('com.google.guava/guava/32.1.2-jre','guava-32.1.2-jre.jar'),('it.unimi.dsi/fastutil/8.5.12','fastutil-8.5.12.jar')]:
            matches=list((cache/relative).glob('*/'+filename)); self.assertEqual(1,len(matches)); deps.append(matches[0])
        harness='''
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
class TailIris {
 static void check(boolean v,String m) {if(!v)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {
  var refs=new HashSet<String>();
  for(int i=0;i<2;i++)refs.addAll(new OptionAnnotatedSource(Files.readString(Path.of(args[i]))).getBooleanDefineReferences().keySet());
  check(refs.contains("SHADERLOD_CONTRIBUTION_TAIL"),"private reference missing");
  check(!refs.contains("PROGRAM_DEFERRED1")&&!refs.contains("WORLD_OVERWORLD"),"stage leaked");
  for(int i=2;i<4;i++) {
   var source=new OptionAnnotatedSource(Files.readString(Path.of(args[i])));
   var opts=source.getOptionSet(AbsolutePackPath.fromAbsolutePath("/settings.glsl"),refs);
   check(opts.getBooleanOptions().containsKey("SHADERLOD_CONTRIBUTION_TAIL"),"option undiscovered");
   var opt=source.getBooleanOptions().values().stream().filter(o->o.getName().equals("SHADERLOD_CONTRIBUTION_TAIL")).findFirst().orElseThrow();
   check(opt.getDefaultValue()==(i==3),"wrong default");
   String changed=source.apply(new MutableOptionValues(opts,Map.of("SHADERLOD_CONTRIBUTION_TAIL",Boolean.toString(i==2))));
   check(changed.lines().anyMatch((i==2?"#define SHADERLOD_CONTRIBUTION_TAIL":"//#define SHADERLOD_CONTRIBUTION_TAIL")::equals),"override failed");
  }
  System.out.println("PASS pinned Iris discovery; OFF/ON defaults/overrides; no stage leakage");
 }
}'''
        with tempfile.TemporaryDirectory(prefix='contribution-tail-iris-') as tmp:
            folder=Path(tmp); java=folder/'TailIris.java'; java.write_text(harness); paths=[]
            for label,payload in [('cumulus',self.enabled[probe.CUMULUS]),('helper',self.enabled[probe.HELPER]),*[(arm,probe.patch_members(self.original,M,arm)[probe.SETTINGS]) for arm in ('disabled','enabled')]]:
                p=folder/(label+'.glsl'); p.write_bytes(payload); paths.append(p)
            result=subprocess.run(['java','--class-path',os.pathsep.join(map(str,[iris,*deps])),str(java),*map(str,paths)],capture_output=True,text=True,timeout=20)
            out=ROOT/'build/contribution-tail-source-checks'; out.mkdir(parents=True,exist_ok=True)
            (out/'iris-discovery.log').write_text(result.stdout+result.stderr)
            self.assertEqual(0,result.returncode,result.stdout+result.stderr)
            # Full ShaderPackOptions discovery across every actual GLSL node,
            # not just hand-picked declaration/reference snippets.
            slf=list((cache/'org.slf4j/slf4j-api/2.0.9').glob('*/slf4j-api-2.0.9.jar'))
            self.assertEqual(1,len(slf)); cp=os.pathsep.join(map(str,[iris,*deps,*slf]))
            full=out/'TailFullInventory.java'; full.write_text(self.FULL_INVENTORY_JAVA)
            inventories={}
            for label,arm,requested in [('original','original',{}),('disabled','disabled',{}),
                ('enabled','enabled',{}),('disabled-override','disabled',{probe.SWITCH:'true'}),
                ('enabled-override','enabled',{probe.SWITCH:'false'})]:
                archive=ROOT/'build'/('contribution-tail-'+arm+'.zip')
                metadata=probe.build(archive,arm=arm)
                first=archive.read_bytes(); probe.build(archive,arm=arm)
                self.assertEqual(first,archive.read_bytes())
                files=probe.archive_members(archive)
                self.assertEqual(metadata,json.loads(files[probe.METADATA]))
                if arm=='original': self.assertEqual(self.original,{n:v for n,v in files.items() if n!=probe.METADATA})
                overrides=out/(label+'-requested.properties')
                overrides.write_text(''.join(k+'='+v+'\n' for k,v in sorted(requested.items())))
                r=subprocess.run(['java','--class-path',cp,str(full),str(archive),str(overrides)],capture_output=True,text=True,timeout=20)
                (out/(label+'-full-iris.log')).write_text(r.stdout+r.stderr)
                self.assertEqual(0,r.returncode,r.stdout+r.stderr)
                options={}; source_count=None
                for line in r.stdout.splitlines():
                    fields=line.split('\t')
                    if fields[0]=='OPTION': self.assertNotIn(fields[1],options); options[fields[1]]=fields[2]
                    elif fields[0]=='SOURCE_COUNT': source_count=int(fields[1])
                self.assertGreater(source_count,400); self.assertGreater(len(options),300)
                inventories[label]={'options':options,'source_count':source_count,'archive_sha256':probe.sha256(first)}
            base=inventories['original']['options']
            additions={'SHADERLOD_CONTRIBUTION_TAIL_INCLUDED':'true',probe.SWITCH:'false'}
            for label,value in [('disabled','false'),('enabled','true'),('disabled-override','true'),('enabled-override','false')]:
                self.assertEqual(base|additions|{probe.SWITCH:value},inventories[label]['options'])
            (out/'full-inventories.json').write_text(json.dumps(inventories,indent=2,sort_keys=True)+'\n')

if __name__=='__main__': unittest.main()
