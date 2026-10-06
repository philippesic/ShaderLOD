"""Pinned diagnostic counter sites, full Iris grammar and ordinary-off parity."""
import copy
import io
import json
import os
from pathlib import Path
import posixpath
import re
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import build_photon_work_trace as trace
import build_photon_volumetric_lod as volume
MANIFEST=json.loads((ROOT/trace.MANIFEST).read_text())


def expand(files,name,stack=(),seen=None):
    if name in stack: raise AssertionError('cyclic include')
    if seen is not None: seen.add(name)
    def include(m):
        target=m.group(1)
        path='shaders/'+target.lstrip('/') if target.startswith('/') else posixpath.normpath(posixpath.join(posixpath.dirname(name),target))
        return expand(files,path,stack+(name,),seen)
    return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*',include,files[name].decode(),flags=re.M)


class WorkTraceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.originals={kind:trace.archive_members(ROOT/value['path']) for kind,value in MANIFEST['sources'].items()}
        cls.patched={kind:trace.patch_members(files,MANIFEST,kind) for kind,files in cls.originals.items()}

    def test_pinned_inputs_scope_and_original_members_are_preserved(self):
        for kind,original in self.originals.items():
            contract=MANIFEST['sources'][kind]
            self.assertEqual(trace.SOURCES[kind],trace.sha256((ROOT/contract['path']).read_bytes()))
            self.assertEqual(contract['inventory_sha256'],trace.inventory(original))
            candidate=self.patched[kind]
            self.assertEqual(sorted(trace.MEMBERS),sorted(n for n in original if original[n]!=candidate[n]))
            self.assertEqual(original['LICENSE'],candidate['LICENSE'])
            if kind=='shared':
                for n in original:
                    if n.startswith('shaders/include/shaderlod/') or n=='shaderlod-volumetric-lod.json':
                        self.assertEqual(original[n],candidate[n],n)
            for entry in contract['members']:
                self.assertEqual(entry['patched_sha256'],trace.sha256(candidate[entry['path']]))
        altered=self.originals['shared'].copy();altered['LICENSE']+=b'changed'
        with self.assertRaises(ValueError):trace.patch_members(altered,MANIFEST,'shared')
        with self.assertRaises(ValueError):trace.build(ROOT/'must-not-write.zip')

    def test_actual_reached_sites_and_unchanged_sampling_operations(self):
        for kind,candidate in self.patched.items():
            original=self.originals[kind]
            for name in ('shaders/include/sky/clouds/cumulus.glsl','shaders/include/sky/clouds/coverage_map.glsl'):
                before=original[name].decode();after=candidate[name].decode()
                # Literal texture expressions remain identical; each increment sits before reached calls.
                textures=lambda s:re.findall(r'(?:texture|textureLod)\([^;\n]*',s)
                self.assertEqual(textures(before),textures(after),name)
                self.assertEqual(before.count('transmittance *= step_transmittance;'),after.count('transmittance *= step_transmittance;'))
            body=candidate['shaders/include/sky/clouds/cumulus.glsl'].decode()
            self.assertEqual(1,body.count('SHADERLOD_TRACE_PRIMARY(photon_trace);'))
            self.assertEqual(1,body.count('SHADERLOD_TRACE_DENSITY(photon_trace);'))
            self.assertEqual(1,body.count('SHADERLOD_TRACE_SECONDARY(photon_trace);'))
            self.assertEqual(3,body.count('SHADERLOD_TRACE_TEXTURE(photon_trace);'))
            self.assertLess(body.index('if (transmittance < min_transmittance)'),body.index('SHADERLOD_TRACE_PRIMARY(photon_trace);'))
            self.assertLess(body.index('SHADERLOD_TRACE_PRIMARY(photon_trace);'),body.index('float density = clouds_cumulus_density(ray_pos);'))
            self.assertRegex(body,r'float clouds_cumulus_density\(vec3 pos\) \{\s*#ifdef SHADERLOD_WORK_TRACE\s*#if[^\n]*\s*SHADERLOD_TRACE_DENSITY')
            # Exact helper call count unit: two local-coverage texture evaluations.
            self.assertEqual(2,candidate['shaders/include/sky/clouds/coverage_map.glsl'].decode().count('SHADERLOD_TRACE_TEXTURE(photon_trace);'))
        copied=self.patched['shared'][MANIFEST['trace_adapter']['path']].decode()
        production=self.originals['shared']['shaders/include/shaderlod/photon_volume_adapter.glsl'].decode()
        stripped=re.sub(r'#ifdef SHADERLOD_WORK_TRACE\n#if defined PROGRAM_DEFERRED1 && defined WORLD_OVERWORLD && !defined ATMOSPHERE_TRANSMITTANCE_LUT\n.*?\n#endif // SHADERLOD_TRACE_STAGE\n#endif // SHADERLOD_WORK_TRACE\n','',copied,flags=re.S)
        self.assertEqual(production,stripped,'trace copy must only add guarded counters and metadata')
        for statement in ('float cell_length = shaderlod_volume_cell_length(ray_length, work_target, i);','if (cell_length <= 0.0) continue;','distance_sum += distance_to_sample * density * cell_length;','distance_weight_sum += density * cell_length;'):
            self.assertIn(statement,copied)

    def test_simultaneous_output_and_matching_extent_contract(self):
        for files in self.patched.values():
            program=files['shaders/program/d1_clouds.fsh'].decode()
            self.assertIn('/* RENDERTARGETS: 9,10,11 */',program)
            for page,output in enumerate(('clouds','clouds_data','photon_trace_metadata')):
                self.assertIn(f'{output} = shaderlodWorkTraceSchema == 1',program)
                self.assertIn(f'shaderlod_trace_encode(photon_trace, {page}u)',program)
            for target in (9,10,11):
                self.assertIn(f'const int colortex{target}Format = RGBA32F;',files['shaders/include/buffers.glsl'].decode())
            properties=files['shaders/shaders.properties'].decode()
            for scale in ('1.0','0.5','0.333333','0.25'):
                for target in (11,12,15):self.assertIn(f'size.buffer.colortex{target} = {scale} {scale}',properties)
            self.assertNotIn('SHADERLOD_WORK_TRACE_PAGE',program)
            self.assertNotIn('atomic',program)

    def test_deterministic_archive_and_registration(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'build',prefix='work-trace-test-') as temporary:
            folder=Path(temporary)
            for kind in trace.SOURCES:
                one=trace.build(folder/(kind+'-one.zip'),kind)
                two=trace.build(folder/(kind+'-two.zip'),kind)
                self.assertEqual(one['sha256'],two['sha256'])
                registration=json.loads(Path(one['registration']).read_text())
                self.assertEqual([0,1,2],registration['capture']['attachments'])
                self.assertEqual(['true'],registration['sourceBindings'][0]['requiredOptions'][trace.SWITCH])
                self.assertEqual(one['sha256'],registration['sourceBindings'][0]['sourceSha256'])
                self.assertEqual(one['reference_member_sha256'],registration['sourceBindings'][0]['memberSha256'])
                self.assertEqual('VOLUMETRICS',registration['exports'][0]['effect'])
                self.assertEqual('shaderlodWorkTraceSchema',registration['exports'][0]['uniformName'])
                self.assertTrue(one['diagnostic_only'])

    def test_actual_iris_layout_old_mismatch_and_all_affected_passes(self):
        """Real Iris properties preprocessing, dimensions and MRT directive extraction."""
        pins=json.loads((ROOT/volume.MANIFEST).read_text())
        iris=ROOT/pins['iris_option_discovery']['jar_path']
        self.assertEqual(pins['iris_option_discovery']['jar_sha256'],trace.sha256(iris.read_bytes()))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1';dependencies=[]
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar','it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar','org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar'):
            matches=list(cache.glob(pattern));self.assertEqual(1,len(matches));dependencies+=matches
        java=r'''
import java.nio.file.*;import java.util.*;import java.io.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.gl.texture.TextureScaleOverride;
import net.irisshaders.iris.shaderpack.parsing.CommentDirectiveParser;
import net.irisshaders.iris.shaderpack.parsing.CommentDirective;
class WorkTraceLayout {
 static List<StringPair> defines(int scale,boolean trace,boolean dh){
  var d=new ArrayList<StringPair>(List.of(new StringPair("MC_VERSION","12101"),new StringPair("MC_GL_VERSION","410"),new StringPair("MC_GLSL_VERSION","410"),new StringPair("IS_IRIS","1"),new StringPair("CLOUDS_TEMPORAL_UPSCALING",Integer.toString(scale))));
  if(trace)d.add(new StringPair("SHADERLOD_WORK_TRACE","1"));
  if(dh)d.add(new StringPair("DISTANT_HORIZONS","1"));return d;
 }
 static Map<Integer,List<Integer>> sizes(Path properties,int scale,boolean trace)throws Exception{
  String pre=PropertiesPreprocessor.preprocessSource(Files.readString(properties),defines(scale,trace,false));
  Properties p=new Properties();p.load(new StringReader(pre));var result=new TreeMap<Integer,List<Integer>>();
  for(int i=0;i<16;i++){
   String value=p.getProperty("size.buffer.colortex"+i);
   if(value==null)result.put(i,List.of(1708,960));
   else {String[] args=value.trim().split("\\s+");var actual=new TextureScaleOverride(args[0],args[1]);result.put(i,List.of(actual.getX(1708),actual.getY(960)));}
  }return result;
 }
 static int[] group(Path path,int scale,boolean trace,boolean shared,boolean dh)throws Exception{
  var annotated=new OptionAnnotatedSource(Files.readString(path));
  var options=annotated.getOptionSet(AbsolutePackPath.fromAbsolutePath("/source.fsh"),annotated.getBooleanDefineReferences().keySet());
  var values=new HashMap<String,String>();
  for(String n:List.of("BORDER_FOG","CLOUD_SHADOWS","SHADOW_COLOR","SHADOW_VPS","SH_SKYLIGHT"))values.put(n,"false");
  values.put("SHADERLOD_WORK_TRACE",Boolean.toString(trace));values.put("SHADERLOD_VOLUMETRIC_INTEGRATION_LOD",Boolean.toString(shared));
  values.put("CLOUDS_CUMULUS","true");values.put("BLOCKY_CLOUDS","false");values.put("CLOUDS_TEMPORAL_UPSCALING",Integer.toString(scale));
  String prepared=JcppProcessor.glslPreprocessSource(annotated.apply(new MutableOptionValues(options,values)),defines(scale,false,dh));
  var directive=CommentDirectiveParser.findDirective(prepared,CommentDirective.Type.RENDERTARGETS);
  if(directive.isEmpty())return new int[0];
  return Arrays.stream(directive.get().getDirective().split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
 }
 static void coherent(Map<Integer,List<Integer>> dimensions,int[] buffers,String name){
  if(buffers.length<2)return;var original=dimensions.get(buffers[0]);
  for(int b:buffers)if(!original.equals(dimensions.get(b)))throw new IllegalStateException("Pass sizes must match for drawbuffers "+Arrays.toString(buffers)+" original "+original+" new "+dimensions.get(b)+" in "+name);
 }
 public static void main(String[] args)throws Exception{
  Path dir=Path.of(args[0]);int checks=0,groups=0;
  // Reconstruct the prior reviewed diagnostic property bug from its exact source recipe.
  var old=sizes(dir.resolve("old.properties"),4,true);
  if(!old.get(11).equals(List.of(427,240))||!old.get(12).equals(List.of(1708,960)))throw new AssertionError("old failure extent absent "+old);
  int[] oldGroup=group(dir.resolve("old-deferred2.fsh"),4,true,false,false);
  if(!Arrays.equals(oldGroup,new int[]{11,12}))throw new AssertionError("old actual Iris MRT directive differs");
  try{coherent(old,oldGroup,"old deferred2");throw new AssertionError("old failure accepted");}
  catch(IllegalStateException expected){System.out.println("reproduced: "+expected.getMessage());}
  var cache=new HashMap<String,int[]>();
  for(String source:List.of("original","shared"))for(int scale:List.of(1,2,3,4)){
   var before=sizes(dir.resolve(source+"-base.properties"),scale,false);
   var off=sizes(dir.resolve(source+"-fixed.properties"),scale,false);
   var on=sizes(dir.resolve(source+"-fixed.properties"),scale,true);
   if(!before.equals(off))throw new AssertionError("ordinary OFF property layout changed");
   for(int target:List.of(9,10,11,12,15))if(!on.get(target).equals(before.get(9)))throw new AssertionError("original cloud extent changed "+target);
   for(boolean dh:List.of(false,true))for(String row:Files.readAllLines(dir.resolve(source+".list"))){
    String[] fields=row.split("\\t");String key=source+"/"+dh+"/"+fields[0];
    boolean cloud=fields[0].endsWith("/deferred1.fsh")||fields[0].endsWith("/deferred2.fsh");
    int[] buffers=cloud?group(dir.resolve(fields[1]),scale,true,source.equals("shared"),dh):cache.get(key);
    if(buffers==null){buffers=group(dir.resolve(fields[1]),4,true,source.equals("shared"),dh);cache.put(key,buffers);}
    coherent(on,buffers,fields[0]);checks++;if(buffers.length>1)groups++;
    if(fields[0].equals("shaders/world0/deferred1.fsh")&&!Arrays.equals(buffers,new int[]{9,10,11}))throw new AssertionError("same-draw pages absent");
    if(fields[0].equals("shaders/world0/deferred2.fsh")&&!Arrays.equals(buffers,dh?new int[]{11,12,15}:new int[]{11,12}))throw new AssertionError("downstream MRT changed");
   }
  }
  System.out.println("actual Iris layout: "+checks+" pass/profile checks, "+groups+" multi-target groups; ordinary OFF dimensions exact; fixed9/10/11/12/15 share original cloud extent");
 }
}'''
        with tempfile.TemporaryDirectory(prefix='photon-trace-layout-') as temporary:
            folder=Path(temporary)
            with zipfile.ZipFile(iris) as archive:
                jcpp=folder/'jcpp.jar';jcpp.write_bytes(archive.read('META-INF/jarjar/jcpp-1.4.14.jar'))
            properties=self.patched['original']['shaders/shaders.properties'].decode()
            properties=re.sub(r'^size.buffer.colortex(?:12|15) = [^\n]*\n','',properties,flags=re.M)
            self.assertEqual(MANIFEST['layout_regression']['previous_properties_sha256']['original'],trace.sha256(properties.encode()))
            settings=self.originals['original']['shaders/settings.glsl'].decode()
            self.assertRegex(settings,r'#define CLOUDS_TEMPORAL_UPSCALING 4(?:\s|$)')
            self.assertEqual({'BORDER_FOG':'false','CLOUD_SHADOWS':'false','SHADOW_COLOR':'false','SHADOW_VPS':'false','SH_SKYLIGHT':'false'},MANIFEST['layout_regression']['held_profile_options'])
            (folder/'old.properties').write_text(properties)
            (folder/'old-deferred2.fsh').write_text(expand(self.originals['original'],'shaders/world0/deferred2.fsh'))
            for kind,files in self.originals.items():
                (folder/(kind+'-base.properties')).write_bytes(files['shaders/shaders.properties'])
                (folder/(kind+'-fixed.properties')).write_bytes(self.patched[kind]['shaders/shaders.properties'])
                rows=[]
                for i,name in enumerate(sorted(n for n in files if '/world' in n and n.endswith('.fsh'))):
                    path=f'{kind}-{i}.fsh';(folder/path).write_text(expand(self.patched[kind],name));rows.append(name+'\t'+path)
                (folder/(kind+'.list')).write_text('\n'.join(rows))
            harness=folder/'WorkTraceLayout.java';harness.write_text(java)
            run=subprocess.run(['java','-Xmx1G','--class-path',os.pathsep.join(map(str,[iris,*dependencies,jcpp])),str(harness),str(folder)],capture_output=True,text=True,timeout=120)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr)
            self.assertIn('reproduced: Pass sizes must match for drawbuffers [11, 12] original [427, 240] new [1708, 960]',run.stdout)
            self.assertIn('actual Iris layout:',run.stdout)
            print(run.stdout.strip())

    def test_actual_iris_grammar_off_parity_and_option_discovery(self):
        pins=json.loads((ROOT/volume.MANIFEST).read_text())
        iris=ROOT/pins['iris_option_discovery']['jar_path']
        self.assertEqual(pins['iris_option_discovery']['jar_sha256'],trace.sha256(iris.read_bytes()))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1';dependencies=[]
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar','it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar','org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar','org.antlr/antlr4-runtime/4.13.1/*/antlr4-runtime-4.13.1.jar'):
            matches=list(cache.glob(pattern));self.assertEqual(1,len(matches),pattern);dependencies+=matches
        self.assertEqual(pins['glsl_parser_regression']['antlr_sha256'],trace.sha256(dependencies[-1].read_bytes()))
        java=r'''
import java.nio.file.*;import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.helpers.StringPair;
import io.github.douira.glsl_transformer.parser.EnhancedParser;
import net.irisshaders.iris.shaderpack.parsing.ConstDirectiveParser;
class WorkTraceGrammar {
 static String prepare(Path file,boolean trace,boolean shared,boolean lut)throws Exception{
  String raw=Files.readString(file);var annotated=new OptionAnnotatedSource(raw);
  var options=annotated.getOptionSet(AbsolutePackPath.fromAbsolutePath("/source.fsh"),annotated.getBooleanDefineReferences().keySet());
  boolean candidate=file.getFileName().toString().contains("candidate");
  if(candidate&&!options.getBooleanOptions().containsKey("SHADERLOD_WORK_TRACE"))throw new AssertionError("trace flag absent");
  var values=new HashMap<String,String>();
  for(String n:List.of("BORDER_FOG","CLOUD_SHADOWS","SHADOW_COLOR","SHADOW_VPS","SH_SKYLIGHT"))values.put(n,"false");
  values.put("SHADERLOD_WORK_TRACE",Boolean.toString(trace));
  values.put("SHADERLOD_VOLUMETRIC_INTEGRATION_LOD",Boolean.toString(shared));
  values.put("CLOUDS_CUMULUS","true");values.put("BLOCKY_CLOUDS","false");
  String source=annotated.apply(new MutableOptionValues(options,values));
  var defines=new ArrayList<StringPair>(List.of(new StringPair("MC_VERSION","12101"),new StringPair("MC_GL_VERSION","410"),new StringPair("MC_GLSL_VERSION","410"),new StringPair("IS_IRIS","1")));
  if(lut)defines.add(new StringPair("ATMOSPHERE_TRANSMITTANCE_LUT","1"));
  return JcppProcessor.glslPreprocessSource(source,defines);
 }
 static String tokens(String s){return s.replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*","").replaceAll("\\s+","");}
 static void parse(String s){var p=new EnhancedParser();p.setThrowParseErrors(true);p.parse(s);}
 public static void main(String[] args)throws Exception{
  Path dir=Path.of(args[0]);int parses=0,parity=0,active=0;
  for(String row:Files.readAllLines(dir.resolve("graphs.list"))){
   String[] f=row.split("\\t");boolean shared=Boolean.parseBoolean(f[2]),lut=Boolean.parseBoolean(f[3]);
   String before=prepare(dir.resolve(f[0]),false,shared,lut);
   String off=prepare(dir.resolve(f[1]),false,shared,lut);
   String on=prepare(dir.resolve(f[1]),true,shared,lut);
   for(String s:List.of(before,off,on)){try{parse(s);}catch(Throwable e){throw new AssertionError("grammar "+row,e);}parses++;}
   if(!tokens(before).equals(tokens(off)))throw new AssertionError("off parity "+row);parity++;
   if(f.length>4&&f[4].equals("final")){
    var directives=new HashMap<String,String>();
    for(var d:ConstDirectiveParser.findDirectives(on))directives.put(d.getKey(),d.getValue());
    for(int n:List.of(9,10,11))if(!"RGBA32F".equals(directives.get("colortex"+n+"Format")))throw new AssertionError("effective final format "+n+" "+directives);
   }
   if(on.contains("shaderlod_trace_encode(photon_trace, 2u)")){
    if(!on.contains("uniform int shaderlodWorkTraceSchema")||!on.contains("layout(location = 2) out vec4 photon_trace_metadata"))throw new AssertionError("MRT/marker absent");
    active++;
   } else if(!tokens(before).equals(tokens(on)))throw new AssertionError("trace leaked outside selected stage "+row);
  }
  System.out.println(parses+" complete Iris parses; "+parity+" off parity; "+active+" simultaneous trace output graphs");
 }
}'''
        with tempfile.TemporaryDirectory(prefix='photon-trace-grammar-') as temp:
            folder=Path(temp);embedded=[]
            with zipfile.ZipFile(iris) as archive:
                for n in ('glsl-transformer-2.0.1.jar','jcpp-1.4.14.jar'):
                    data=archive.read('META-INF/jarjar/'+n)
                    if n.startswith('glsl'):
                        self.assertEqual(pins['glsl_parser_regression']['transformer_sha256'],trace.sha256(data))
                        with zipfile.ZipFile(io.BytesIO(data)) as z:
                            p=pins['glsl_parser_regression'];self.assertEqual(p['class_sha256'],trace.sha256(z.read(p['class_path'])))
                    path=folder/n;path.write_bytes(data);embedded.append(path)
            rows=[];index=0;selected=[]
            for kind,files in self.originals.items():
                for name in sorted(files):
                    if '/world' not in name or not name.endswith(('.fsh','.vsh','.gsh','.csh')): continue
                    seen=set();before=expand(files,name,seen=seen)
                    if volume.CUMULUS not in seen and name!='shaders/world0/final.fsh': continue
                    orig=f'{index}-original.fsh';candidate=f'{index}-candidate.fsh';index+=1
                    (folder/orig).write_text(before);(folder/candidate).write_text(expand(self.patched[kind],name))
                    rows.append('\t'.join((orig,candidate,str(kind=='shared').lower(),'false','final' if name.endswith('/final.fsh') else 'cloud')))
                    if name=='shaders/world0/deferred1.fsh':selected.append((orig,candidate,str(kind=='shared').lower()))
            # Explicit public private-OFF shared variant is checked with its volume flag OFF too.
            for fields in selected:
                rows.append('\t'.join((*fields,'true')))
            (folder/'graphs.list').write_text('\n'.join(rows))
            harness=folder/'WorkTraceGrammar.java';harness.write_text(java)
            run=subprocess.run(['java','-Xmx1G','--class-path',os.pathsep.join(map(str,[iris,*dependencies,*embedded])),str(harness),str(folder)],capture_output=True,text=True,timeout=120)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr)
            self.assertIn('270 complete Iris parses; 90 off parity; 2 simultaneous trace output graphs',run.stdout)
            print(run.stdout.strip())

if __name__=='__main__':unittest.main()
