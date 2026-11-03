"""Bounded source, parity, actual Iris discovery and complete C0 offline checks."""
import copy
import itertools
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
import build_photon_air_fog_cost_ablation as probe
M=json.loads((ROOT/probe.MANIFEST).read_text())
OUT=ROOT/'build/air-fog-cost-ablation-source-checks'
CANONICAL=ROOT/'src/main/resources/assets/shaderlod/photonquad/native366.json'

def select(source, private=False, consumer=False):
    source=source.replace(M['option_declaration'],'').replace(M['option_declaration'].replace('//#define','#define'),'')
    stack=[];result=[]
    for line in source.splitlines(keepends=True):
        if line=='#ifdef '+probe.SWITCH+'\n':stack.append(private)
        elif line==probe.STAGE+'\n':stack.append(consumer)
        elif line in ('#else // '+probe.SWITCH+'\n','#else // SHADERLOD_AIR_FOG_COST_STAGE\n'):stack[-1]=not stack[-1]
        elif line in ('#endif // '+probe.SWITCH+'\n','#endif // SHADERLOD_AIR_FOG_COST_STAGE\n'):stack.pop()
        elif not stack or all(stack):result.append(line)
    if stack:raise AssertionError('Unclosed injected gate')
    return ''.join(result)

def dependencies():
    iris=ROOT/M['iris_option_discovery']['jar_path']
    assert probe.sha256(iris.read_bytes())==M['iris_option_discovery']['jar_sha256']
    cache=Path.home()/'.gradle/caches/modules-2/files-2.1';deps=[]
    for relative,name in [('com.google.guava/guava/32.1.2-jre','guava-32.1.2-jre.jar'),('it.unimi.dsi/fastutil/8.5.12','fastutil-8.5.12.jar'),('org.slf4j/slf4j-api/2.0.9','slf4j-api-2.0.9.jar')]:
        paths=list((cache/relative).glob('*/'+name));assert len(paths)==1;deps.extend(paths)
    with zipfile.ZipFile(iris) as archive:
        p=OUT/'jcpp-1.4.14.jar';p.write_bytes(archive.read('META-INF/jarjar/jcpp-1.4.14.jar'));deps.append(p)
    return iris,deps

class AirFogCostAblationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        OUT.mkdir(parents=True,exist_ok=True)
        cls.original=probe.archive_members(ROOT/probe.BASE)
        cls.enabled=probe.patch_members(cls.original,M,'enabled')

    def test_all_original_members_OFF_nonconsumer_exact_parity(self):
        count=0
        for arm in probe.ARMS:
            files=probe.patch_members(self.original,M,arm)
            for private,consumer in ((False,False),(False,True),(True,False)):
                for n,b in self.original.items():
                    actual=select(files[n].decode(),private,consumer).encode() if n in probe.MEMBERS else files[n]
                    self.assertEqual(b,actual,(arm,private,consumer,n));count+=1
            self.assertEqual(self.original['LICENSE'],files['LICENSE'])
            self.assertEqual(set(self.original)|({probe.HELPER} if arm!='original' else set()),set(files))
        (OUT/'parity.json').write_text(json.dumps({'original_member_count':len(self.original),'comparisons':count,'private_OFF_nonconsumer':'exact bytes after selecting only injected source guards'},indent=2)+'\n')

    def test_tamper_rejected_and_output_scope(self):
        for n in (*probe.MEMBERS,*probe.DEPENDENCIES,'LICENSE'):
            with self.assertRaises(ValueError):probe.patch_members(self.original|{n:self.original[n]+b'x'},M,'enabled')
        for key in ('profile_condition','activation','switch','base_sha256'):
            bad=copy.deepcopy(M);bad[key]='tampered'
            with self.assertRaises(ValueError):probe.patch_members(self.original,bad,'enabled')
        for key in ('patched_sha256','source_sha256','enabled_sha256'):
            bad=copy.deepcopy(M);bad['members'][0][key]='0'*64
            with self.assertRaises(ValueError):probe.patch_members(self.original,bad,'enabled')
        bad=copy.deepcopy(M);bad['addition']['sha256']='0'*64
        with self.assertRaises(ValueError):probe.patch_members(self.original,bad,'enabled')
        with self.assertRaises(ValueError):probe.build(ROOT/'outside.zip')

    def test_only_original_air_body_call_gated_neutral_output_ABI(self):
        source=self.enabled[probe.PROGRAM].decode();original=self.original[probe.PROGRAM].decode()
        for r in reversed(M['members'][1]['replacements']):
            self.assertEqual(1,source.count(r['after']));source=source.replace(r['after'],r['before'],1)
        self.assertEqual(original,source)
        active=select(self.enabled[probe.PROGRAM].decode(),True,True)
        body=active[active.index('case 0:'):active.index('case 1:')]
        self.assertLess(body.index('if (shaderlod_air_fog_cost_ablation_active())'),body.index('raymarch_air_fog('))
        self.assertIn('} else {\n                fog = raymarch_air_fog(',body)
        self.assertIn('fog_scattering = fog[0];',body);self.assertIn('fog_transmittance = fog[1];',body)
        self.assertIn('layout(location = 0) out vec3 fog_transmittance;',original)
        self.assertIn('layout(location = 1) out vec3 fog_scattering;',original)
        self.assertIn('/* RENDERTARGETS: 6,7 */',original)
        helper=self.enabled[probe.HELPER].decode()
        self.assertIn('uniform int shaderlodBridgeVersion;',helper);self.assertIn('uniform int shaderlodEnabled;',helper)
        self.assertIn('shaderlodBridgeVersion == 2 && shaderlodEnabled == 1',helper)
        self.assertEqual(1,helper.count('return mat2x3(vec3(0.0), vec3(1.0));'))
        self.assertIn('return mat2x3(vec3(0.0), vec3(1.0));',self.original[probe.DEPENDENCIES[0]].decode())
        self.assertNotRegex(helper,r'\b(texture|textureLod|dFdx|dFdy)\s*\(')
        c1=self.original['shaders/program/c1_blend_layers.fsh'].decode()
        self.assertIn('fragment_color = fragment_color * fog_transmittance + fog_scattering;',c1)
        self.assertIn('#if !defined VL\n    // Analytic fog',c1)
        # Neutral column output preserves the stock downstream affine blend;
        # this proves neutral algebra, not visual parity with real fog.
        cases=0
        for radiance in itertools.product((0.,.01,.5,1.,16.),repeat=3):
            self.assertEqual(radiance,tuple(x*1.+0. for x in radiance));cases+=1
        for core,enabled,air,private,consumer in itertools.product((-1,0,1,2,3),(0,1,2),(False,True),(False,True),(False,True)):
            active_gate=private and consumer and air and core==2 and enabled==1
            if active_gate:self.assertEqual((core,enabled,air,private,consumer),(2,1,True,True,True))
            cases+=1
        (OUT/'neutral-source-cases.json').write_text(json.dumps({'scalar_cases':cases,'neutral_scattering':[0,0,0],'neutral_transmittance':[1,1,1],'outputs':{'RT6':'transmittance','RT7':'scattering'},'scope':'source gate and stock C1 algebra only; no driver execution'},indent=2)+'\n')

    def test_actual_full_Iris_inventory_and_frozen_archives(self):
        iris,deps=dependencies();cp=os.pathsep.join(map(str,[iris,*deps]))
        java=OUT/'AirFogInventory.java';java.write_text(FULL_INVENTORY_JAVA)
        canonical=json.loads(CANONICAL.read_text());inventories={}
        for label,arm,value in [('original','original',None),('disabled','disabled',None),('enabled','enabled',None),('disabled-override','disabled','true'),('enabled-override','enabled','false')]:
            archive=ROOT/'build'/('air-fog-cost-ablation-'+arm+'.zip');metadata=probe.build(archive,arm=arm)
            first=archive.read_bytes();probe.build(archive,arm=arm);self.assertEqual(first,archive.read_bytes())
            files=probe.archive_members(archive);self.assertEqual(metadata,json.loads(files[probe.METADATA]))
            if arm=='original':self.assertEqual(self.original,{n:b for n,b in files.items() if n!=probe.METADATA})
            requested=canonical|({probe.SWITCH:value} if value else {})
            p=OUT/(label+'-requested.properties');p.write_text(''.join(k+'='+v+'\n' for k,v in sorted(requested.items())))
            r=subprocess.run(['java','--class-path',cp,str(java),str(archive),str(p)],capture_output=True,text=True,timeout=25)
            (OUT/(label+'-full-iris.log')).write_text(r.stdout+r.stderr);self.assertEqual(0,r.returncode,r.stdout+r.stderr)
            options={};refs={};nodes=None
            for line in r.stdout.splitlines():
                fields=line.split('\t')
                if fields[0]=='OPTION':self.assertNotIn(fields[1],options);options[fields[1]]=fields[2]
                elif fields[0]=='REFERENCE':refs[fields[1]]=fields[2]
                elif fields[0]=='SOURCE_COUNT':nodes=int(fields[1])
            inventories[label]={'options':options,'option_count':len(options),'source_count':nodes,'archive_sha256':probe.sha256(first),'member_sha256':{n:probe.sha256(b) for n,b in sorted(files.items())},'boolean_references':refs}
        self.assertEqual(canonical,inventories['original']['options'])
        for label,value in [('disabled','false'),('enabled','true'),('disabled-override','true'),('enabled-override','false')]:
            self.assertEqual(canonical|{probe.SWITCH:value},inventories[label]['options'])
            for stage in ('WORLD_OVERWORLD','PROGRAM_COMPOSITE0','VL','LPV_VL','COLORED_LIGHTS','AIR_FOG_COLORED_LIGHT_SHAFTS'):
                # Added references occur only at the assigned C0 consumer.
                before=set(inventories['original']['boolean_references'].get(stage,'').split(','))-{''}
                after=set(inventories[label]['boolean_references'].get(stage,'').split(','))-{''}
                self.assertTrue(before.issubset(after),(label,stage))
                self.assertTrue((after-before).issubset({probe.PROGRAM}),(label,stage))
            self.assertEqual(probe.PROGRAM,inventories[label]['boolean_references'][probe.SWITCH])
        (OUT/'full-inventories.json').write_text(json.dumps(inventories,indent=2,sort_keys=True)+'\n')

    @unittest.skipUnless(shutil.which('glslangValidator'),'No offline GLSL compiler')
    def test_complete_C0_entry_offline_semantics_and_scoped_fallbacks(self):
        iris,deps=dependencies();cp=os.pathsep.join(map(str,[iris,*deps]))
        canonical=json.loads(CANONICAL.read_text())
        self.assertEqual(('true','true','false','false'),tuple(canonical[k] for k in ('VL','LPV_VL','COLORED_LIGHTS','AIR_FOG_COLORED_LIGHT_SHAFTS')))
        cases=[('active',True,{}),('private-off',False,{}),('colored-fallback',True,{'AIR_FOG_COLORED_LIGHT_SHAFTS':'true','SHADOW_COLOR':'true'}),('lpv-fallback',True,{'COLORED_LIGHTS':'true'})]
        paths=[]
        for label,private,extra in cases:
            files=self.enabled.copy();overrides=canonical|{probe.SWITCH:str(private).lower()}|extra
            files[probe.SETTINGS]=settings_override(files[probe.SETTINGS].decode(),overrides).encode()
            source=expand(files,'shaders/world0/composite.fsh').replace('\\\n','')
            source=source.replace('#version 400 compatibility','#version 400 compatibility\n#define MC_VERSION 12101\n#define MC_SHADOW_QUALITY 1.0\n#define MC_RENDER_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n#define IS_IRIS\n#define IRIS_FEATURE_SEPARATE_ENTITY_DRAWS',1)
            path=OUT/(label+'-world0-composite.frag');path.write_text(source);paths.append(path)
        java=OUT/'AirFogPreprocess.java'
        java.write_text('import java.nio.file.*;import java.util.*;import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor; class AirFogPreprocess {public static void main(String[] a)throws Exception {for(String p:a)Files.writeString(Path.of(p+".jcpp"),JcppProcessor.glslPreprocessSource(Files.readString(Path.of(p)),List.of()));}}')
        r=subprocess.run(['java','--class-path',cp,str(java),*map(str,paths)],capture_output=True,text=True,timeout=25)
        (OUT/'jcpp.log').write_text(r.stdout+r.stderr);self.assertEqual(0,r.returncode,r.stdout+r.stderr)
        checks=[]
        for path in paths:
            processed=Path(str(path)+'.jcpp');text=processed.read_text()
            r=subprocess.run(['glslangValidator','-S','frag',str(processed)],capture_output=True,text=True,timeout=20)
            log=Path(str(processed)+'.log');log.write_text(r.stdout+r.stderr)
            checks.append({'source':str(path.relative_to(ROOT)),'source_sha256':probe.sha256(path.read_bytes()),'preprocessed_sha256':probe.sha256(processed.read_bytes()),'log':str(log.relative_to(ROOT)),'exit_code':r.returncode})
            self.assertEqual(0,r.returncode,r.stdout+r.stderr)
            if path.name.startswith('active-'):
                self.assertIn('shaderlod_air_fog_cost_ablation_active()',text)
                self.assertIn('fog = shaderlod_air_fog_cost_ablation_neutral();',text)
            else:self.assertNotIn('shaderlod_air_fog_cost_ablation_active',text)
        (OUT/'semantic-checks.json').write_text(json.dumps({'offline_only':True,'pinned_Iris_Jcpp':True,'canonical_input':str(CANONICAL.relative_to(ROOT)),'canonical_sha256':probe.sha256(CANONICAL.read_bytes()),'checks':checks,'dependency_sha256':{str(p):probe.sha256(p.read_bytes()) for p in [iris,*deps]}},indent=2)+'\n')

def expand(files,name):
    return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*',lambda m:expand(files,'shaders'+m[1] if m[1].startswith('/') else str(Path(name).parent/m[1])),files[name].decode(),flags=re.M)

def settings_override(source,overrides):
    for k,v in overrides.items():
        pattern=r'^([ \t]*)(?://)?#define '+re.escape(k)+r'(?:[ \t]+([^\n]*))?$'
        def replace(m):
            tail=m[2] or ''
            if v in ('true','false'):
                # Only boolean declarations, not unrelated similarly named macros.
                if tail and not tail.startswith('//'):return m[0]
                return m[1]+('' if v=='true' else '//')+'#define '+k+(' '+tail if tail else '')
            comment=tail[tail.find('//'):] if '//' in tail else ''
            return m[1]+'#define '+k+' '+v+(' '+comment if comment else '')
        source=re.sub(pattern,replace,source,flags=re.M)
    return source

FULL_INVENTORY_JAVA = 'import java.nio.file.*; import java.util.*; import java.util.zip.*;\nimport java.nio.charset.StandardCharsets;\nimport com.google.common.collect.ImmutableList; import com.google.common.collect.ImmutableMap;\nimport net.irisshaders.iris.shaderpack.option.*; import net.irisshaders.iris.shaderpack.include.*;\nclass AirFogInventory {\n public static void main(String[] a) throws Exception {\n  var nodes=ImmutableMap.<AbsolutePackPath,FileNode>builder(); var refs=new TreeMap<String,Set<String>>();\n  try(var zip=new ZipFile(a[0])) {\n   for(var entry:Collections.list(zip.entries())) {\n    String name=entry.getName();\n    if(!name.startsWith("shaders/")||!(name.endsWith(".glsl")||name.endsWith(".vsh")||name.endsWith(".fsh")||name.endsWith(".csh")||name.endsWith(".gsh")||name.endsWith(".tcs")||name.endsWith(".tes")))continue;\n    String text=new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8);\n    var path=AbsolutePackPath.fromAbsolutePath("/"+name.substring(8));\n    nodes.put(path,new FileNode(path,ImmutableList.copyOf(text.split("\\\\R"))));\n    for(String key:new OptionAnnotatedSource(text).getBooleanDefineReferences().keySet())refs.computeIfAbsent(key,k->new TreeSet<>()).add(name);\n   }\n  }\n  var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);\n  var graph=constructor.newInstance(nodes.buildOrThrow(),ImmutableMap.of());\n  var p=new Properties();try(var reader=Files.newBufferedReader(Path.of(a[1]))){p.load(reader);}\n  var overrides=new TreeMap<String,String>();for(String key:p.stringPropertyNames())overrides.put(key,p.getProperty(key));\n  var options=new ShaderPackOptions(graph,overrides);var values=options.getOptionValues();var all=new TreeMap<String,String>();\n  for(String key:options.getOptionSet().getBooleanOptions().keySet())all.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));\n  for(String key:options.getOptionSet().getStringOptions().keySet())if(all.put(key,values.getStringValueOrDefault(key))!=null)throw new AssertionError("duplicate option "+key);\n  for(var entry:all.entrySet())System.out.println("OPTION\\t"+entry.getKey()+"\\t"+entry.getValue());\n  for(var entry:refs.entrySet())System.out.println("REFERENCE\\t"+entry.getKey()+"\\t"+String.join(",",entry.getValue()));\n  System.out.println("SOURCE_COUNT\\t"+graph.getNodes().size());\n }\n}\n'

if __name__=='__main__':unittest.main()
