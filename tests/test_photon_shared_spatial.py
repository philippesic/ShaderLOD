"""Pinned explicit adapter: exact source, complete Iris graph, storage and shared execution."""
import copy
import io
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import build_photon_shared_spatial as adapter
import test_spatial_lod_pipeline as kernel
M=json.loads((ROOT/adapter.MANIFEST).read_text())

def expand(files,name,stack=()):
    if name in stack: raise AssertionError('include cycle')
    return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*',lambda m:expand(files,('shaders'+m[1]) if m[1].startswith('/') else str(Path(name).parent/m[1]),stack+(name,)),files[name].decode(),flags=re.M)

def function(source,name):
    start=source.index(name+'('); start=source.rfind('\n',0,start)+1; end=source.index('{',start)+1;level=1
    while level:
        if source[end]=='{':level+=1
        if source[end]=='}':level-=1
        end+=1
    return source[start:end]

class SharedSpatialTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original=adapter.archive_members(ROOT/adapter.BASE)
        cls.overlays=adapter.overlays(ROOT,M,cls.original)
        cls.candidate=adapter.patch_members(cls.original,M,'disabled')|cls.overlays

    def test_frozen_assembly_and_scope(self):
        for arm in adapter.ARMS:
            folder=ROOT/'build/shared-spatial-source-fixtures';folder.mkdir(parents=True,exist_ok=True)
            first=folder/(arm+'-a.zip'); second=folder/(arm+'-b.zip')
            try:
                metadata=adapter.build(first,arm=arm);adapter.build(second,arm=arm)
                self.assertEqual(first.read_bytes(),second.read_bytes())
                self.assertEqual(adapter.sha256(first.read_bytes()),metadata['archive_sha256'])
                registration=metadata['reference_registration']
                self.assertEqual([1.0,.25],registration['capability']['retainedRates'])
                if arm!='original':
                    self.assertEqual(metadata['archive_sha256'],registration['sourceBindings'][0]['sourceSha256'])
                    self.assertEqual(metadata['reference_member_sha256'],registration['sourceBindings'][0]['memberSha256'])
                with zipfile.ZipFile(first) as z:
                    for name,data in self.original.items():
                        if arm=='original' or name not in adapter.MEMBERS:self.assertEqual(data,z.read(name),name)
                    self.assertEqual(self.original['LICENSE'],z.read('LICENSE'))
                    self.assertEqual('unverified',metadata['runtime_compilation'])
            finally:first.unlink(missing_ok=True);second.unlink(missing_ok=True)
        changed=copy.deepcopy(M);changed['members'][0]['source_sha256']='0'*64
        with self.assertRaises(ValueError):adapter.patch_members(self.original,changed,'enabled')
        for name,digest in M['shared_helpers'].items():
            common=(ROOT/'shaderpacks/common/shaders/include/shaderlod'/name).read_bytes()
            self.assertEqual(adapter.sha256(common),digest)
            self.assertEqual(common,self.overlays['shaders/include/shaderlod/'+name])

    def test_shared_kernel_consumption_and_factorization(self):
        callback=self.overlays['shaders/include/shaderlod/photon_spatial_adapter.glsl'].decode()
        producer=self.overlays['shaders/program/shaderlod_spatial_prepass.fsh'].decode()
        source=self.candidate['shaders/program/d4_deferred_shading.fsh'].decode()
        self.assertIn('shaderlod_spatial_plan_decide(policy,',callback)
        self.assertIn('shaderlod_spatial_produce(true, true, 2',producer)
        self.assertIn('shaderlod_spatial_prepare(2, tile_coordinate',producer)
        self.assertIn('shaderlod_spatial_resolve(',source)
        self.assertNotRegex(callback,r'\b(smoothstep|shaderlod_surface_region_eligible|shaderlod_rate_weight)\s*\(')
        self.assertIn('false, true, vec3(shaderlodOpaqueLightingNear',callback) # unresolved INHERIT
        self.assertIn('quantity.a < 0.0 || quantity.a > 1.0',callback)
        self.assertIn('shaderlodSpatialResult.linear_quantity.rgb * material.albedo',source)
        # Prove the pinned diffuse tail's canonical-white basis: excluded nonlinear SSS/sheen/metal.
        diffuse=self.original['shaders/include/lighting/diffuse_lighting.glsl'].decode()
        self.assertIn('return max0(lighting) * material.albedo * rcp_pi',diffuse)
        for excluded in ('material.sss_amount == 0.0','material.sheen_amount == 0.0','!material.is_metal','material.porosity == 0.0'):
            self.assertIn(excluded,callback)
        # Full original reflection/highlight/fog tail remains outside the selected lighting block.
        entry=next(e for e in M['members'] if e['path'].endswith('d4_deferred_shading.fsh'))
        self.assertFalse(any('get_specular' in r['before'] or 'apply_fog' in r['before'] for r in entry['replacements']))

    def test_original_sky_exact_extraction_and_pingpong_source_obligation(self):
        original=self.original['shaders/program/d4_deferred_shading.fsh'].decode()
        prelude=original[original.index('    ivec2 texel = ivec2(gl_FragCoord.xy);'):original.index('    if (depth == 1.0) { // Sky')]
        start=original.index('    if (depth == 1.0) { // Sky')+len('    if (depth == 1.0) { // Sky\n')
        body=original[start:original.index('    } else { // Terrain',start)]
        sky=self.overlays[M['sky_source']['helper']].decode()
        self.assertIn(prelude,self.overlays['shaders/program/shaderlod_spatial_prepass.fsh'].decode());self.assertIn(body,sky)
        self.assertEqual(adapter.sha256(prelude.encode()),M['sky_source']['source_prelude_sha256'])
        self.assertEqual(adapter.sha256(body.encode()),M['sky_source']['source_body_sha256'])
        producer=self.overlays['shaders/program/shaderlod_spatial_prepass.fsh'].decode()
        self.assertIn('/* RENDERTARGETS: 0,2 */',producer)
        self.assertLess(producer.index(prelude),producer.index('    if (depth == 1.0)'))
        self.assertLess(producer.index(prelude),producer.index('    if (!shaderlod_photon_cache_contract())'))
        properties=self.candidate['shaders/shaders.properties'].decode()
        self.assertNotRegex(properties,r'flip\.deferred[45]\.colortex[02]')
        # Inspect the pinned executable path: reads choose snapshot side; FBO chooses opposite.
        iris=ROOT/'dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        evidence={}
        for cls in ('net.irisshaders.iris.samplers.IrisSamplers','net.irisshaders.iris.targets.RenderTargets','net.irisshaders.iris.pipeline.CompositeRenderer'):
            run=subprocess.run(['javap','-p','-c','-classpath',str(iris),cls],capture_output=True,text=True,timeout=15)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr);evidence[cls]=run.stdout
        sampler=evidence['net.irisshaders.iris.samplers.IrisSamplers'].split('private static int lambda$addRenderTargetSamplers$0',1)[1]
        self.assertLess(sampler.index('getAltTexture'),sampler.index('getMainTexture'))
        framebuffer=evidence['net.irisshaders.iris.targets.RenderTargets'].split('public net.irisshaders.iris.gl.framebuffer.GlFramebuffer createColorFramebuffer(',1)[1]
        self.assertLess(framebuffer.index('getMainTexture'),framebuffer.index('getAltTexture'))
        renderer=evidence['net.irisshaders.iris.pipeline.CompositeRenderer']
        self.assertIn('ProgramDirectives.getExplicitFlips',renderer)
        self.assertIn('BufferFlipper.flip',renderer)
        # Opposite physical object IDs and completed pass boundary must still be verified by the driver.

    def test_binary32_certificate_domain_and_split_lane_failure_in_actual_kernel(self):
        # Compile actual certificate callback and actual shared reconstruction, not a second algorithm.
        callback=self.overlays['shaders/include/shaderlod/photon_spatial_adapter.glsl'].decode()
        codec=function(callback,'shaderlod_spatial_adapter_cache')
        codec=codec.replace('certificate.z','certificate.z').replace('quantity.a','quantity.w')
        # GLSL finite vector predicates in callback use existing host-vector shims.
        fake=r'''
int frameCounter=16777216, SHADERLOD_PHOTON_QUANTITY=1,colortex2=2;
vec4 stored_quantity(2.f,4.f,6.f,.8f), stored_certificate(16777216.f,258.f,5.f,100.f);
bool shaderlod_photon_cache_contract(){return true;}
int shaderlod_photon_tile_width(){return 4;}
vec4 texelFetch(int,ivec2 p,int){return p.x%2==0?stored_quantity:stored_certificate;}
ShaderlodSpatialPlan active_plan;
ShaderlodSpatialSurface shaderlod_spatial_adapter_surface(ivec2 p){return ShaderlodSpatialSurface(vec3(float(p.x),float(p.y),-100.f),vec3(0.f,0.f,1.f),0,vec2(.5f),false);}
ShaderlodSpatialSpread shaderlod_spatial_adapter_spread(ivec2,int){return ShaderlodSpatialSpread(false,vec2(0.f));}
ShaderlodSpatialPlan shaderlod_spatial_adapter_plan(float){return active_plan;}
vec4 shaderlod_spatial_adapter_evaluate_linear(ivec2,ShaderlodSpatialSurface){return vec4(10.f,12.f,14.f,.6f);}
'''
        main=r'''
int main(){
 auto limits=shaderlod_region_default_limits();ivec2 pixel(3,3),dimensions(8,8);
 active_plan=ShaderlodSpatialPlan(vec3(0.f,1.f,0.f),.25f,.25f,.25f,false,true,false,0);
 auto r=shaderlod_spatial_resolve(true,pixel,dimensions,active_plan,32.f,limits,1,frameCounter);
 assert(!r.full_resolution_required&&r.linear_quantity.x==2.f&&r.linear_quantity.w==.8f);
 stored_quantity.w=-1.f; // Metadata VALID while expensive quantity evaluation failed.
 r=shaderlod_spatial_resolve(true,pixel,dimensions,active_plan,32.f,limits,1,frameCounter);
 assert(r.full_resolution_required);stored_quantity.w=.8f;
 stored_quantity.x=std::numeric_limits<float>::infinity();
 r=shaderlod_spatial_resolve(true,pixel,dimensions,active_plan,32.f,limits,1,frameCounter);assert(r.full_resolution_required);stored_quantity.x=2.f;
 for(int field=0;field<4;field++){float original=stored_certificate[field];stored_certificate[field]=field==3?32.f:original-1.f;
 r=shaderlod_spatial_resolve(true,pixel,dimensions,active_plan,32.f,limits,1,frameCounter);assert(r.full_resolution_required);stored_certificate[field]=original;}
 active_plan=ShaderlodSpatialPlan(vec3(.5f,.5f,0.f),.625f,.625f,1.25f,true,true,false,0);
 r=shaderlod_spatial_resolve(true,pixel,dimensions,active_plan,32.f,limits,1,frameCounter);
 assert(!r.full_resolution_required&&r.linear_quantity.x==6.f&&abs(r.linear_quantity.w-.7f)<.000001f);
 vec3 albedo(.1f,.2f,.9f); assert(r.linear_quantity.x*albedo.x==.6f);
 std::cout<<"actual adapter codec + kernel: exact epoch/tile/key/distance, split-lane invalid, finite fallback, transition, recipient albedo\n";
}
'''
        source=kernel.HOST_VECTORS+'template<class T,int N> V<T,N> operator*(T b,V<T,N>a){return a*b;}\n'+(kernel.BASE/'surface_region_continuity.glsl').read_text()+kernel.PIPELINE.read_text()+fake+codec+main
        source=re.sub(r'(?<![\w.])(\d+\.\d*(?:[eE][+-]?\d+)?|\d+[eE][+-]?\d+)(?![\w.])',r'\1f',source)
        with tempfile.TemporaryDirectory(prefix='photon-shared-host-') as temp:
            folder=Path(temp);p=folder/'codec.cpp';p.write_text(source)
            run=subprocess.run([shutil.which('c++'),'-std=c++20','-O0',str(p),'-o',str(folder/'codec')],capture_output=True,text=True,timeout=30)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr)
            run=subprocess.run([str(folder/'codec')],capture_output=True,text=True,timeout=10)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr);print(run.stdout.strip())
        f32=lambda x:struct.unpack('f',struct.pack('f',x))[0]
        for x in (0,258,16777215,16777216):self.assertEqual(x,f32(x))
        self.assertNotEqual(16777217,f32(16777217))
        for width,height in ((8192,8192),(8191,8191),(3,5),(2976,1672)):
            tw=(width+1)//2;th=(height+1)//2
            self.assertLessEqual(tw*th-1,16777215)
            for x,y in ((0,0),(tw-1,th-1)):
                index=x+tw*y;self.assertEqual((x,y),(int(f32(index))%tw,int(f32(index))//tw))

    def test_actual_iris_graph_grammar_parity_and_all_target_groups(self):
        iris=ROOT/'dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        self.assertEqual('3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83',adapter.sha256(iris.read_bytes()))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1';dependencies=[]
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar','it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar','org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar','org.antlr/antlr4-runtime/4.13.1/*/antlr4-runtime-4.13.1.jar'):
            found=list(cache.glob(pattern));self.assertEqual(1,len(found));dependencies+=found
        self.assertEqual('54665d2838cc66458343468efc539e454fc95b46a8a04b13c6ac43fc9be63505',adapter.sha256(dependencies[-1].read_bytes()))
        report=ROOT/'run-workloads/native-terrain-b15-r2/forest/high/workload-measurement.json'
        self.assertEqual('6f7662a31427f0d0863d926909c67a57b0dfba3c7ee2539c857f699c7ce3da5e',adapter.sha256(report.read_bytes()))
        native=json.loads(report.read_text())['effective_shader_options']
        self.assertEqual('4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745',adapter.sha256(json.dumps(native,sort_keys=True,separators=(',',':')).encode()))
        self.assertEqual('true',native['USE_SEPARATE_ENTITY_DRAWS']);self.assertEqual('false',native['GALAXY'])
        java=IRIS_PROOF
        with tempfile.TemporaryDirectory(prefix='photon-shared-iris-') as temp:
            folder=Path(temp);embedded=[]
            (folder/'native.properties').write_text('\n'.join(f'{k}={v}' for k,v in sorted(native.items())))
            with zipfile.ZipFile(iris) as archive:
                for n in ('glsl-transformer-2.0.1.jar','jcpp-1.4.14.jar'):
                    data=archive.read('META-INF/jarjar/'+n)
                    if n.startswith('glsl'):
                        self.assertEqual('b8ca5e61b41ae18ddd5d4d5d6785cb9acd8fd7ac0c5c0b0689b872b039c11b9f',adapter.sha256(data))
                    p=folder/n;p.write_bytes(data);embedded.append(p)
            rows=[]
            for i,name in enumerate(sorted(n for n in self.original if '/world' in n and n.endswith(('.fsh','.vsh')))):
                a=f'{i}-original.glsl';b=f'{i}-candidate.glsl'
                (folder/a).write_text(expand(self.original,name));(folder/b).write_text(expand(self.candidate,name));rows.append('\t'.join((name,a,b)))
            for world in ('world0','world1','world-1'):
                for ext in ('fsh','vsh'):
                    name=f'shaders/{world}/deferred5.{ext}';b=world+'-new.'+ext;(folder/b).write_text(expand(self.candidate,name));rows.append('\t'.join((name,'-',b)))
            (folder/'graphs.list').write_text('\n'.join(rows))
            for arm,files in (('original',self.original),('candidate',self.candidate)):
                (folder/(arm+'.properties')).write_bytes(files['shaders/shaders.properties'])
            harness=folder/'SharedSpatialProof.java';harness.write_text(java)
            run=subprocess.run(['java','-Xmx1G','--class-path',os.pathsep.join(map(str,[iris,*dependencies,*embedded])),str(harness),str(folder)],capture_output=True,text=True,timeout=120)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr);print(run.stdout.strip())
            for stage in ('producer','consumer'):
                evidence=ROOT/'build/shared-spatial-source-fixtures'/('actual-'+stage+'.frag')
                evidence.write_bytes((folder/(stage+'.frag')).read_bytes())
                result=subprocess.run([shutil.which('glslangValidator'),'-S','frag','-l','-q',str(folder/(stage+'.frag'))],capture_output=True,text=True,timeout=30)
                self.assertEqual(0,result.returncode,result.stdout+result.stderr)
                samplers=re.findall(r'^(\w+):.*type (?:8b5e|8b5f|8b62)',result.stdout,re.M)
                self.assertEqual(11 if stage=='producer' else 12,len(samplers))
                self.assertLessEqual(len(samplers),14)
                (ROOT/('build/photon-shared-spatial-'+stage+'-reflection.txt')).write_text(result.stdout)
            print('2 complete actual Photon callback stages pass offline GLSL semantic compilation')

IRIS_PROOF=r'''
import java.nio.file.*;import java.util.*;import java.io.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;
import net.irisshaders.iris.helpers.StringPair;
import io.github.douira.glsl_transformer.parser.EnhancedParser;
import net.irisshaders.iris.gl.texture.TextureScaleOverride;
import net.irisshaders.iris.shaderpack.parsing.*;
class SharedSpatialProof {
 static final String FLAG="SHADERLOD_SHARED_SPATIAL";
 static final Set<String> references=new HashSet<>();
 static final Properties nativeOptions=new Properties();
 static List<StringPair> defines(boolean gate,boolean dh,int scale){
  var list=new ArrayList<StringPair>(List.of(new StringPair("MC_VERSION","12101"),new StringPair("MC_GL_VERSION","410"),new StringPair("MC_GLSL_VERSION","410"),new StringPair("IS_IRIS","1"),new StringPair("MC_HAND_DEPTH","0.125"),new StringPair("MC_SHADOW_QUALITY","1.0"),new StringPair("CLOUDS_TEMPORAL_UPSCALING",Integer.toString(scale))));
  if(gate)list.add(new StringPair(FLAG,"1"));if(dh)list.add(new StringPair("DISTANT_HORIZONS","1"));return list;
 }
 static String prepare(Path file,boolean gate,boolean dh,int scale)throws Exception{
  var annotated=new OptionAnnotatedSource(Files.readString(file));
  var options=annotated.getOptionSet(AbsolutePackPath.fromAbsolutePath("/source.fsh"),references);
  if(Files.readString(file).contains("//#define "+FLAG)&&!options.getBooleanOptions().containsKey(FLAG))throw new AssertionError("option discovery absent");
  for(String n:options.getBooleanOptions().keySet())if(n.startsWith("SHADERLOD_")&&!n.equals(FLAG))throw new AssertionError("internal option leak "+n);
  var values=new HashMap<String,String>();for(String key:nativeOptions.stringPropertyNames())values.put(key,nativeOptions.getProperty(key));values.put(FLAG,Boolean.toString(gate));values.put("CLOUDS_TEMPORAL_UPSCALING",Integer.toString(scale));
  return JcppProcessor.glslPreprocessSource(annotated.apply(new MutableOptionValues(options,values)),defines(false,dh,scale));
 }
 static String tokens(String s){return s.replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*","").replaceAll("\\s+","");}
 static void parse(String s){var p=new EnhancedParser();p.setThrowParseErrors(true);p.parse(s);}
 static List<StringPair> propertyDefines(boolean gate,boolean dh,int scale){var d=new ArrayList<StringPair>(defines(gate,dh,scale));return d;}
 static Map<Integer,List<Integer>> sizes(Path file,boolean gate,boolean dh,int scale)throws Exception{
  var p=new Properties();p.load(new StringReader(PropertiesPreprocessor.preprocessSource(Files.readString(file),propertyDefines(gate,dh,scale))));var sizes=new TreeMap<Integer,List<Integer>>();
  for(int i=0;i<16;i++){String v=p.getProperty("size.buffer.colortex"+i);if(v==null)sizes.put(i,List.of(1708,960));else{var a=v.trim().split("\\s+");var d=new TextureScaleOverride(a[0],a[1]);sizes.put(i,List.of(d.getX(1708),d.getY(960)));}}
  if(!"true".equals(p.getProperty("program.world0/deferred5.enabled", "false"))==gate&&!dh)throw new AssertionError("program stage gate");
  if(p.getProperty("scale.deferred4")!=null)throw new AssertionError("original viewport changed");return sizes;
 }
 static void coherent(String source,Map<Integer,List<Integer>> dimensions,String name){
  var directive=CommentDirectiveParser.findDirective(source,CommentDirective.Type.RENDERTARGETS);if(directive.isEmpty())return;
  var ids=Arrays.stream(directive.get().getDirective().split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
  for(int id:ids)if(id<0||id>15||!dimensions.get(ids[0]).equals(dimensions.get(id)))throw new AssertionError("MRT dimensions "+name+Arrays.toString(ids));
 }
 public static void main(String[] args)throws Exception{
  Path dir=Path.of(args[0]);nativeOptions.load(Files.newBufferedReader(dir.resolve("native.properties")));int parsed=0,parity=0,groups=0,consumers=0;
  var rows=Files.readAllLines(dir.resolve("graphs.list"));
  for(String row:rows){var f=row.split("\\t");for(int i=1;i<=2;i++)if(!f[i].equals("-"))references.addAll(new OptionAnnotatedSource(Files.readString(dir.resolve(f[i]))).getBooleanDefineReferences().keySet());}
  for(String row:rows){var f=row.split("\\t");String off=prepare(dir.resolve(f[2]),false,false,4),on=prepare(dir.resolve(f[2]),true,false,4);
   if(!f[1].equals("-")){String before=prepare(dir.resolve(f[1]),false,false,4);if(!tokens(before).equals(tokens(off)))throw new AssertionError("OFF parity "+f[0]);parse(before);parity++;parsed++;}
   try{parse(off);parse(on);}catch(Throwable e){throw new AssertionError("grammar "+f[0],e);}parsed+=2;
   if(f[0].endsWith("final.fsh")){var formats=new HashMap<String,String>();for(var d:ConstDirectiveParser.findDirectives(on))formats.put(d.getKey(),d.getValue());if(!"RGBA32F".equals(formats.get("colortex2Format")))throw new AssertionError("format "+f[0]+formats+on.substring(0,Math.min(600,on.length())));}
   if(on.contains("ShaderlodSpatialResult shaderlodSpatialResult"))consumers++;
   if(f[0].endsWith(".fsh"))for(int scale:List.of(1,2,3,4))for(boolean dh:List.of(false,true)){
    var original=sizes(dir.resolve("original.properties"),false,dh,scale);var candidate=sizes(dir.resolve("candidate.properties"),true,dh,scale);if(!original.equals(candidate))throw new AssertionError("unrelated target size changed");
    coherent(prepare(dir.resolve(f[2]),true,dh,scale),candidate,f[0]);groups++;
   }
  }
  if(consumers!=1)throw new AssertionError("world scope "+consumers);
  String[] selected=rows.stream().map(r->r.split("\\t")).filter(f->f[0].equals("shaders/world0/deferred4.fsh")).findFirst().orElseThrow();
  for(String unsupported:List.of("GALAXY","BORDER_FOG","SKY_REFLECTIONS","NORMAL_MAPPING","SPECULAR_MAPPING","SHADOW_COLOR","SHADOW_VPS","COLORED_LIGHTS","CLOUD_SHADOWS","SH_SKYLIGHT","TAAU","BLOCKY_CLOUDS","ENVIRONMENT_REFLECTIONS")){
   String previous=nativeOptions.getProperty(unsupported);nativeOptions.setProperty(unsupported,"true");
   String original=prepare(dir.resolve(selected[1]),false,false,4),fallback=prepare(dir.resolve(selected[2]),true,false,4);
   if(!tokens(original).equals(tokens(fallback)))throw new AssertionError("unsupported profile changed original "+unsupported);
   nativeOptions.setProperty(unsupported,previous);
  }
  if(!tokens(prepare(dir.resolve(selected[1]),false,true,4)).equals(tokens(prepare(dir.resolve(selected[2]),true,true,4))))throw new AssertionError("DH graph fallback changed");
  for(String row:rows){var f=row.split("\\t");if(f[0].equals("shaders/world0/deferred4.fsh")||f[0].equals("shaders/world0/deferred5.fsh"))Files.writeString(dir.resolve(f[0].contains("deferred4")?"producer.frag":"consumer.frag"),prepare(dir.resolve(f[2]),true,false,4));}
  String bad=prepare(dir.resolve("world0-new.fsh"),true,false,4).replace("int shaderlod_photon_tile_width()", "int shaderlod_photon_tile_width(");
  try{parse(bad);throw new AssertionError("malformed active callback accepted");}catch(org.antlr.v4.runtime.misc.ParseCancellationException expected){}
  System.out.println(parsed+" complete Iris grammar parses; "+parity+" original/privateOFF token parity; "+groups+" all-upstream draw-group/profile layout checks; private option discovery; RGBA32F directive; 13 unsupported-profile original token controls; malformed active callback rejected");
 }
}'''
if __name__=='__main__':unittest.main()
