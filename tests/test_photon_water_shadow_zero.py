"""Bounded exact-zero/source/parity/Iris/offline checks. Never opens a client."""
import copy
import itertools
import json
import math
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
import build_photon_water_shadow_zero as probe
M=json.loads((ROOT/probe.MANIFEST).read_text())
OUT=ROOT/'build/water-shadow-zero-source-checks'
EPS=1e-6

def select(source,private=False,consumer=False):
    source=source.replace(M['option_declaration'],'').replace(M['option_declaration'].replace('//#define','#define'),'')
    stack=[]; result=[]
    for line in source.splitlines(keepends=True):
        if line=='#ifdef '+probe.SWITCH+'\n':stack.append(private)
        elif line==probe.STAGE+'\n':stack.append(consumer)
        elif line in ('#else // '+probe.SWITCH+'\n','#else // SHADERLOD_WATER_SHADOW_STAGE\n'):stack[-1]=not stack[-1]
        elif line in ('#endif // '+probe.SWITCH+'\n','#endif // SHADERLOD_WATER_SHADOW_STAGE\n'):stack.pop()
        elif not stack or all(stack):result.append(line)
    if stack:raise AssertionError('Unclosed injected gate')
    return ''.join(result)

def function(source,signature):
    start=source.index(signature);end=source.index('{',start)+1;depth=1
    while depth:depth+=(source[end]=='{')-(source[end]=='}');end+=1
    return source[start:end]

def expand(files,name):
    return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*',lambda m:expand(files,'shaders'+m[1] if m[1].startswith('/') else str(Path(name).parent/m[1])),files[name].decode(),flags=re.M)

def gate(albedo,nol,water=True,core=2,enabled=1,vps=False):
    return (not vps and core==2 and enabled==1 and water and
        all(math.isfinite(x) for x in (*albedo,nol)) and max(albedo)<EPS and nol<=EPS)

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

def dependencies():
    iris=ROOT/M['iris_option_discovery']['jar_path']
    assert probe.sha256(iris.read_bytes())==M['iris_option_discovery']['jar_sha256']
    cache=Path.home()/'.gradle/caches/modules-2/files-2.1';deps=[]
    for relative,name in [('com.google.guava/guava/32.1.2-jre','guava-32.1.2-jre.jar'),('it.unimi.dsi/fastutil/8.5.12','fastutil-8.5.12.jar'),('org.slf4j/slf4j-api/2.0.9','slf4j-api-2.0.9.jar')]:
        paths=list((cache/relative).glob('*/'+name));assert len(paths)==1;deps.extend(paths)
    with zipfile.ZipFile(iris) as archive:
        p=OUT/'jcpp-1.4.14.jar';p.write_bytes(archive.read('META-INF/jarjar/jcpp-1.4.14.jar'));deps.append(p)
    return iris,deps

class WaterShadowZeroTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        OUT.mkdir(parents=True,exist_ok=True)
        cls.original=probe.archive_members(ROOT/probe.BASE)
        cls.enabled=probe.patch_members(cls.original,M,'enabled')
        cls.helper=cls.enabled[probe.HELPER].decode()

    def test_pins_original_OFF_and_nonconsumer_full_byte_parity(self):
        self.assertEqual(probe.BASE_SHA256,probe.sha256((ROOT/probe.BASE).read_bytes()))
        comparisons=0
        for arm in probe.ARMS:
            files=probe.patch_members(self.original,M,arm)
            for private,consumer in ((False,False),(False,True),(True,False)):
                for n,payload in self.original.items():
                    actual=select(files[n].decode(),private,consumer).encode() if n in probe.MEMBERS else files[n]
                    self.assertEqual(payload,actual,(arm,private,consumer,n));comparisons+=1
            self.assertEqual(self.original['LICENSE'],files['LICENSE'])
        for n in (*probe.MEMBERS,*probe.DEPENDENCIES,'LICENSE'):
            with self.assertRaises(ValueError):probe.patch_members(self.original|{n:self.original[n]+b'x'},M,'enabled')
        bad=copy.deepcopy(M);bad['excluded_quality']='none'
        with self.assertRaises(ValueError):probe.patch_members(self.original,bad,'enabled')
        with self.assertRaises(ValueError):probe.build(ROOT/'outside.zip')
        (OUT/'parity.json').write_text(json.dumps({'original_member_comparisons':comparisons,'OFF_and_nonconsumer':'byte exact after injected gates selected'},indent=2)+'\n')

    def test_exact_consumers_and_unmodified_appearance_body(self):
        original=self.original[probe.PROGRAM].decode(); active=select(self.enabled[probe.PROGRAM].decode(),True,True)
        entry=M['members'][1]
        # Removing exactly the helper inclusion and call gate restores every
        # remaining program byte, including waves/reflections/absorption/fog.
        for replacement in reversed(entry['replacements']):
            active_replacement=select(replacement['after'],True,True)
            self.assertEqual(1,active.count(active_replacement))
            active=active.replace(active_replacement,replacement['before'],1)
        self.assertEqual(original,active)
        diffuse=function(self.original[probe.DEPENDENCIES[0]].decode(),'vec3 get_diffuse_lighting(')
        self.assertLess(diffuse.index('if (max_of(material.albedo) < eps)'),diffuse.index('vec3 lighting'))
        self.assertIn('return vec3(0.0);',diffuse[:diffuse.index('vec3 lighting')])
        spec=function(self.original[probe.DEPENDENCIES[1]].decode(),'vec3 get_specular_highlight(')
        self.assertIn('if (NoL <= eps) {\n        return vec3(0.0);',spec)
        filt=function(self.original[probe.PCSS].decode(),'vec3 get_filtered_shadows(')
        self.assertEqual(2,len(re.findall(r'sss_depth\s*=',filt)))
        self.assertIn('#ifdef SHADOW_VPS\n    vec2 blocker_search_result',filt)
        self.assertIn('sss_depth = blocker_search_result.y;',filt)
        self.assertIn('sss_depth = 0.0;',filt)
        self.assertIn('max_of(albedo) < eps && NoL <= eps',self.helper)
        self.assertIn('!any(isnan(albedo))',self.helper);self.assertIn('!isinf(NoL)',self.helper)
        pc=select(self.enabled[probe.PCSS].decode(),True,True)
        # Only three sample-site function names differ; all PCF math/counts and
        # lookups not involving shadowtex1 remain exactly upstream.
        pc=pc.replace('shaderlod_water_shadow_sample(shadow_screen_pos)','texture(shadowtex1, shadow_screen_pos)').replace('shaderlod_water_shadow_sample(vec3(uv, shadow_screen_pos.z))','texture(shadowtex1, vec3(uv, shadow_screen_pos.z))')
        self.assertEqual(self.original[probe.PCSS].decode(),pc)
        sample=function(self.helper,'float shaderlod_water_shadow_sample(')
        self.assertIn('if (shaderlod_water_shadow_known_enabled())',sample)
        self.assertIn('return textureLod(shadowtex1, coord, 0.0);',sample)
        self.assertIn('return texture(shadowtex1, coord);',sample)
        self.assertNotIn('dFdx',self.helper);self.assertNotIn('dFdy',self.helper)
        program=select(self.enabled[probe.PROGRAM].decode(),True,True)
        self.assertLess(program.index('if (!shaderlod_water_shadow_zero'),program.index('shadows = get_filtered_shadows'))

    def test_zero_contribution_envelope_and_protected_counterexamples(self):
        count=0;eligible=0
        values=(-1,0,EPS*.5,math.nextafter(EPS,0),EPS,math.nextafter(EPS,math.inf),.1)
        for albedo in itertools.product(values,repeat=3):
            for nol in (-1,0,EPS,math.nextafter(EPS,math.inf),.5):
                for visibility in (0,.2,1):
                    skip=gate(albedo,nol);count+=1
                    if skip:
                        self.assertLess(max(albedo),EPS);self.assertLessEqual(nol,EPS)
                        # Mirrors the two actual early returns. Do not replace
                        # the nonzero diffuse body with a Lambert approximation.
                        original_diffuse=0.0 if max(albedo)<EPS else None
                        original_highlight=0.0 if nol<=EPS else None
                        self.assertEqual(0.0,original_diffuse*visibility+original_highlight*visibility)
                        eligible+=1
        self.assertGreater(eligible,0)
        for a,n,w,c,e,v in [((EPS,0,0),0,True,2,1,False),((0,0,0),2*EPS,True,2,1,False),((0,0,0),0,False,2,1,False),((0,0,0),0,True,1,1,False),((0,0,0),0,True,2,0,False),((0,0,0),0,True,2,2,False),((0,0,0),0,True,2,1,True)]:
            self.assertFalse(gate(a,n,w,c,e,v))
        for value in (math.nan,math.inf,-math.inf):
            self.assertFalse(gate((value,0,0),0));self.assertFalse(gate((0,0,0),value))
        # A simple NoL rejection is wrong for nonzero edge albedo: the real
        # lifted diffuse and bounced light retain positive shadow dependence.
        self.assertFalse(gate((.01,.01,.01),-1))
        a=.1;ambient=.2;extinction=.4
        absorption=lambda depth:ambient+a*math.exp(-extinction*depth)
        self.assertNotEqual(absorption(0),absorption(10)) # VPS falsifier
        # Exact water texture/edge-zero domain; coverage is synthetic only.
        for skylight in (.5,.75,1):
            texture_highlight=.2*(1-min(max(skylight/.5,0),1)**3)
            self.assertEqual(0,texture_highlight)
        for distance,dy in ((.5,1),(5,.1),(500,0.001)):
            self.assertEqual(0,max(1-2*distance*max(abs(dy),EPS),0)**3)
        (OUT/'zero-envelope.json').write_text(json.dumps({'finite_cases':count,'eligible_cases':eligible,'scope':'actual existing early-return conditions; no observed coverage or GPU execution proof'},indent=2)+'\n')

    def test_pinned_nonmip_shadow_sampler_evidence(self):
        iris,_=dependencies();logs={}
        for name in ['net.irisshaders.iris.shaderpack.properties.PackShadowDirectives$SamplingSettings','net.irisshaders.iris.shadows.ShadowRenderer']:
            r=subprocess.run(['javap','-c','-p','-classpath',str(iris),name],capture_output=True,text=True,timeout=20)
            self.assertEqual(0,r.returncode,r.stderr);logs[name]=r.stdout
            (OUT/(name.rsplit('.',1)[-1]+'.javap')).write_text(r.stdout+r.stderr)
        s=logs['net.irisshaders.iris.shaderpack.properties.PackShadowDirectives$SamplingSettings']
        self.assertRegex(s,r'iconst_0\s+6: putfield[^\n]+Field mipmap:Z')
        s=logs['net.irisshaders.iris.shadows.ShadowRenderer']
        configure=s[s.index('private void configureSampler('):s.index('private void generateMipmaps(')]
        self.assertIn('getMipmap:()Z',configure);self.assertIn('sipush        9729',configure)
        declarations=[]
        for n,b in self.original.items():
            if n.endswith(('.glsl','.vsh','.fsh','.properties')):
                for line in b.decode().splitlines():
                    if re.search(r'shadowtex[01]Mipmap|shadowMipmap',line):declarations.append([n,line])
        self.assertEqual([],declarations)
        self.assertIn('const bool shadowHardwareFiltering1 = true;',self.original[probe.SETTINGS].decode())
        (OUT/'sampling-evidence.json').write_text(json.dumps({'iris_sha256':probe.sha256(iris.read_bytes()),'mipmap_default':False,'configured_nonmip_min_filter':'GL_LINEAR (9729)','Photon_mipmap_declarations':declarations,'active_sampling':'explicit LOD0; no derivative dependency','runtime_sampler_readback':'not performed'},indent=2)+'\n')

    @unittest.skipUnless(shutil.which('glslangValidator'),'No offline GLSL compiler')
    def test_complete_relevant_stages_and_quality_fallback_semantics(self):
        iris,deps=dependencies();cp=os.pathsep.join(map(str,[iris,*deps]))
        report=ROOT/'src/main/resources/assets/shaderlod/photonquad/native366.json'
        raw=report.read_bytes();canonical=json.loads(raw)
        self.assertEqual('false',canonical['SHADOW_VPS']);self.assertEqual('true',canonical['SHADOW_PCF'])
        aliases=['gbuffers_water','gbuffers_entities_translucent','gbuffers_hand','gbuffers_textured','gbuffers_particles_translucent','gbuffers_lightning']
        roots=[(w,n,s) for w in ('world0','world1','world-1') for n in aliases for s in ('fsh','vsh') if f'shaders/{w}/{n}.{s}' in self.original]
        roots += [('world0','deferred4','fsh')]
        cases=[('active',True,False,True,roots),('private-off',False,False,True,[('world0','gbuffers_water','fsh')]),('VPS',True,True,True,[('world0','gbuffers_water','fsh')]),('basic',True,False,False,[('world0','gbuffers_water','fsh')])]
        paths=[]
        for label,private,vps,pcf,entries in cases:
            files=self.enabled.copy();overrides=canonical|{probe.SWITCH:str(private).lower(),'SHADOW_VPS':str(vps).lower(),'SHADOW_PCF':str(pcf).lower()}
            files[probe.SETTINGS]=settings_override(files[probe.SETTINGS].decode(),overrides).encode()
            for w,n,s in entries:
                source=expand(files,f'shaders/{w}/{n}.{s}').replace('\\\n','')
                source=source.replace('#version 400 compatibility','#version 400 compatibility\n#define MC_VERSION 12101\n#define MC_SHADOW_QUALITY 1.0\n#define MC_RENDER_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n#define MC_RENDER_STAGE_WORLD_BORDER 22\n#define IS_IRIS\n#define IRIS_FEATURE_SEPARATE_ENTITY_DRAWS',1)
                path=OUT/f'{label}-{w}-{n}.{"frag" if s=="fsh" else "vert"}';path.write_text(source);paths.append(path)
        java=OUT/'WaterShadowPreprocess.java'
        java.write_text('''import java.nio.file.*;import java.util.*;import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
class WaterShadowPreprocess {public static void main(String[] args)throws Exception {for(String p:args)Files.writeString(Path.of(p+".jcpp"),JcppProcessor.glslPreprocessSource(Files.readString(Path.of(p)),List.of()));System.out.println("PASS preprocess "+args.length);}}''')
        r=subprocess.run(['java','--class-path',cp,str(java),*map(str,paths)],capture_output=True,text=True,timeout=25)
        (OUT/'jcpp.log').write_text(r.stdout+r.stderr);self.assertEqual(0,r.returncode,r.stdout+r.stderr)
        checks=[]
        for path in paths:
            processed=Path(str(path)+'.jcpp')
            r=subprocess.run(['glslangValidator','-S',path.suffix[1:],str(processed)],capture_output=True,text=True,timeout=20)
            log=Path(str(processed)+'.log');log.write_text(r.stdout+r.stderr)
            checks.append({'source':str(path.relative_to(ROOT)),'source_sha256':probe.sha256(path.read_bytes()),'preprocessed_sha256':probe.sha256(processed.read_bytes()),'log':str(log.relative_to(ROOT)),'exit_code':r.returncode})
            self.assertEqual(0,r.returncode,r.stdout+r.stderr)
            if path.name=='active-world0-gbuffers_water.frag':
                text=processed.read_text();self.assertIn('shaderlod_water_shadow_zero',text);self.assertIn('textureLod(shadowtex1, coord, 0.0)',text)
            if path.name in ('VPS-world0-gbuffers_water.frag','private-off-world0-gbuffers_water.frag'):
                self.assertNotIn('shaderlod_water_shadow_zero',processed.read_text())
        (OUT/'semantic-checks.json').write_text(json.dumps({'offline_only':True,'canonical_runtime_input':str(report.relative_to(ROOT)),'canonical_report_sha256':probe.sha256(raw),'pinned_Iris_Jcpp':True,'checks':checks,'dependency_sha256':{str(p):probe.sha256(p.read_bytes()) for p in [iris,*deps]}},indent=2)+'\n')

    FULL_INVENTORY_JAVA = 'import java.nio.file.*; import java.util.*; import java.util.zip.*;\nimport java.nio.charset.StandardCharsets;\nimport com.google.common.collect.ImmutableList; import com.google.common.collect.ImmutableMap;\nimport net.irisshaders.iris.shaderpack.option.*; import net.irisshaders.iris.shaderpack.include.*;\nclass WaterShadowInventory {\n public static void main(String[] a) throws Exception {\n  var nodes=ImmutableMap.<AbsolutePackPath,FileNode>builder(); var refs=new TreeMap<String,Set<String>>();\n  try(var zip=new ZipFile(a[0])) {\n   for(var entry:Collections.list(zip.entries())) {\n    String name=entry.getName();\n    if(!name.startsWith("shaders/")||!(name.endsWith(".glsl")||name.endsWith(".vsh")||name.endsWith(".fsh")||name.endsWith(".csh")||name.endsWith(".gsh")||name.endsWith(".tcs")||name.endsWith(".tes")))continue;\n    String text=new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8);\n    var path=AbsolutePackPath.fromAbsolutePath("/"+name.substring(8));\n    nodes.put(path,new FileNode(path,ImmutableList.copyOf(text.split("\\\\R"))));\n    for(String key:new OptionAnnotatedSource(text).getBooleanDefineReferences().keySet())refs.computeIfAbsent(key,k->new TreeSet<>()).add(name);\n   }\n  }\n  var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);\n  var graph=constructor.newInstance(nodes.buildOrThrow(),ImmutableMap.of());\n  var p=new Properties();try(var reader=Files.newBufferedReader(Path.of(a[1]))){p.load(reader);}\n  var overrides=new TreeMap<String,String>();for(String key:p.stringPropertyNames())overrides.put(key,p.getProperty(key));\n  var options=new ShaderPackOptions(graph,overrides);var values=options.getOptionValues();var all=new TreeMap<String,String>();\n  for(String key:options.getOptionSet().getBooleanOptions().keySet())all.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));\n  for(String key:options.getOptionSet().getStringOptions().keySet())if(all.put(key,values.getStringValueOrDefault(key))!=null)throw new AssertionError("duplicate option "+key);\n  for(var entry:all.entrySet())System.out.println("OPTION\\t"+entry.getKey()+"\\t"+entry.getValue());\n  for(var entry:refs.entrySet())System.out.println("REFERENCE\\t"+entry.getKey()+"\\t"+String.join(",",entry.getValue()));\n  System.out.println("SOURCE_COUNT\\t"+graph.getNodes().size());\n }\n}\n'

    def test_actual_full_iris_inventory_and_deterministic_archives(self):
        iris,deps=dependencies();cp=os.pathsep.join(map(str,[iris,*deps]))
        java=OUT/'WaterShadowInventory.java';java.write_text(self.FULL_INVENTORY_JAVA)
        inventories={}
        report=ROOT/'src/main/resources/assets/shaderlod/photonquad/native366.json'
        canonical=json.loads(report.read_text())
        cases=[('original','original',{}),('disabled','disabled',{}),('enabled','enabled',{}),('disabled-override','disabled',{probe.SWITCH:'true'}),('enabled-override','enabled',{probe.SWITCH:'false'}),('canonical-original','original',canonical),('canonical-disabled','disabled',canonical|{probe.SWITCH:'false'}),('canonical-enabled','enabled',canonical|{probe.SWITCH:'true'})]
        for label,arm,requested in cases:
            archive=ROOT/'build'/('water-shadow-zero-'+arm+'.zip');metadata=probe.build(archive,arm=arm)
            first=archive.read_bytes();probe.build(archive,arm=arm);self.assertEqual(first,archive.read_bytes())
            files=probe.archive_members(archive);self.assertEqual(metadata,json.loads(files[probe.METADATA]))
            if arm=='original':self.assertEqual(self.original,{n:b for n,b in files.items() if n!=probe.METADATA})
            p=OUT/(label+'-requested.properties');p.write_text(''.join(k+'='+v+'\n' for k,v in sorted(requested.items())))
            r=subprocess.run(['java','--class-path',cp,str(java),str(archive),str(p)],capture_output=True,text=True,timeout=20)
            (OUT/(label+'-full-iris.log')).write_text(r.stdout+r.stderr);self.assertEqual(0,r.returncode,r.stdout+r.stderr)
            options={};refs={};nodes=None
            for line in r.stdout.splitlines():
                fields=line.split('\t')
                if fields[0]=='OPTION':self.assertNotIn(fields[1],options);options[fields[1]]=fields[2]
                elif fields[0]=='REFERENCE':refs[fields[1]]=fields[2]
                elif fields[0]=='SOURCE_COUNT':nodes=int(fields[1])
            self.assertGreater(nodes,400);self.assertGreater(len(options),300)
            inventories[label]={'options':options,'source_count':nodes,'archive_sha256':probe.sha256(first),'member_sha256':{n:probe.sha256(b) for n,b in sorted(files.items())},'boolean_references':refs}
        original=inventories['original']['options'];additions={probe.SWITCH:'false','SHADERLOD_WATER_SHADOW_ZERO_INCLUDED':'true'}
        for label,value in [('disabled','false'),('enabled','true'),('disabled-override','true'),('enabled-override','false')]:
            self.assertEqual(original|additions|{probe.SWITCH:value},inventories[label]['options'])
            for stage in ('PROGRAM_GBUFFERS_WATER','WORLD_OVERWORLD'):
                self.assertEqual(inventories['original']['boolean_references'].get(stage),inventories[label]['boolean_references'].get(stage))
        self.assertEqual(canonical,inventories['canonical-original']['options'])
        for label,value in [('canonical-disabled','false'),('canonical-enabled','true')]:
            self.assertEqual(canonical|additions|{probe.SWITCH:value},inventories[label]['options'])
        (OUT/'full-inventories.json').write_text(json.dumps(inventories,indent=2,sort_keys=True)+'\n')

if __name__=='__main__':unittest.main()
