"""Assemblable source, scalar-policy and actual pinned Iris preprocessing fixtures."""
import copy
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import build_photon_lighting_rate_probe as probe
M = json.loads((ROOT / probe.MANIFEST).read_text())


def rate(distance, near=32., far=96., width=8., medium=1., low=.25, mode=2, core=2, effects=2, enabled=1):
    legal = lambda x: math.isfinite(x) and (x == 1 or 0 < x <= .25)
    if core != 2 or effects != 2 or enabled != 1 or mode not in (0, 2): return 0.
    if mode == 0: medium, low = 1., .25
    if not all(math.isfinite(x) for x in (distance, near, far, width)) or near < 1 or far <= near: return 0.
    if width < 0 or width > min(2*near, far-near) or not legal(medium) or not legal(low) or low > medium: return 0.
    threshold = near if medium <= .25 else far
    if medium == low == 1: return 0.
    if width == 0: return float(distance >= threshold)
    x = max(0., min(1., (distance - (threshold-width/2)) / width))
    return x*x*(3-2*x)


class LightingRateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = probe.archive_members(ROOT / probe.BASE)
        if probe.sha256((ROOT / probe.BASE).read_bytes()) != probe.BASE_SHA256: raise AssertionError('base changed')
        cls.patched = probe.patch_members(cls.files, M, 'enabled')
        cls.overlay = probe.overlays(ROOT, M, cls.files)

    def test_original_arm_every_member_identical_and_license_preserved(self):
        self.assertEqual(self.files, probe.patch_members(self.files, M, 'original'))
        self.assertEqual(self.files['LICENSE'], self.patched['LICENSE'])
        changed = sorted(n for n in self.files if self.patched[n] != self.files[n])
        self.assertEqual(sorted(probe.MEMBERS), changed)
        self.assertEqual(9, len(self.overlay))

    def test_overlay_and_upstream_mutations_fail_closed(self):
        for name in probe.MEMBERS:
            with self.assertRaises(ValueError): probe.patch_members(self.files | {name:self.files[name]+b'\n'}, M, 'enabled')
        malformed = copy.deepcopy(M); malformed['members'][0]['replacements'][0]['after'] += 'bad'
        with self.assertRaises(ValueError): probe.patch_members(self.files, malformed, 'enabled')
        with self.assertRaises(ValueError): probe.build(output=ROOT/'not-ignored.zip')
        self.assertEqual(M['shared_surface_helper']['sha256'], probe.sha256(self.overlay[M['shared_surface_helper']['copy']]))

    def test_current_pixel_material_and_common_specular_composition_are_not_cached(self):
        d4=self.patched['shaders/program/d4_deferred_shading.fsh'].decode()
        original=self.files['shaders/program/d4_deferred_shading.fsh'].decode()
        tail=original[original.index('        // Specular highlight'):]
        cue=M['members'][4]['replacements'][-1]['after'].split(M['fragment_condition'],1)[1].split('#endif // SHADERLOD_LIGHTING_RATE_SCOPE',1)[0]
        # Common material function remains once, and suffix changes only optional debug cue.
        self.assertEqual(1,d4.count('material_from('))
        self.assertTrue(d4.replace(M['fragment_condition']+cue+'#endif // SHADERLOD_LIGHTING_RATE_SCOPE\n','',1).endswith(tail))
        self.assertIn('shaderlodRateCache.rgb * material.albedo',d4)
        prepass=self.overlay['shaders/program/shaderlod_lighting_prepass.fsh'].decode()
        self.assertIn('material.albedo = vec3(1.0)',prepass)
        self.assertNotIn('get_specular_highlight(',prepass)
        self.assertNotIn('get_specular_reflections(',prepass)
        self.assertNotIn('fragment_color = purkinje_shift(',prepass)

    def test_representative_keeps_original_shadow_ao_math_and_pixel_seed(self):
        prepass=self.overlay['shaders/program/shaderlod_lighting_prepass.fsh'].decode()
        for token in ('get_filtered_shadows(', 'get_screen_space_shadows(', 'get_diffuse_lighting(', 'ambient_depth_00', 'ambient_11'):
            self.assertIn(token,prepass)
        self.assertIn('shaderlodLightingPixel = vec2(origin) + 0.5',prepass)
        for name in ('pcss','ssrt'):
            self.assertIn('ivec2(shaderlodLightingPixel)',self.patched[f'shaders/include/lighting/shadows/{name}.glsl'].decode())
        self.assertEqual(3,self.patched['shaders/include/lighting/shadows/pcss.glsl'].decode().count('textureLod(shadowtex1,'))
        self.assertIn('return vec4(fragment_color, clamp01(shadows.x * ao))',prepass)
        self.assertIn('shaderlod_surface_tile_eligible',prepass)

    def test_discrete_rates_bands_and_invalid_packets(self):
        self.assertEqual(0,rate(92));self.assertEqual(.5,rate(96));self.assertEqual(1,rate(100))
        self.assertEqual(1,rate(36,medium=.25,low=.125))
        self.assertEqual(0,rate(200,medium=1,low=1))
        for args in (dict(low=.5),dict(medium=.75),dict(low=0),dict(effects=1),dict(core=1),dict(mode=1),dict(mode=3),
                     dict(enabled=0),dict(width=65),dict(near=2,width=5),dict(far=math.nan)):
            self.assertEqual(0,rate(200,**args),args)
        self.assertEqual(1,rate(100,mode=0,low=1))
        policy=self.overlay['shaders/include/shaderlod/lighting_rate_policy.glsl'].decode()
        for name in (*probe.CORE_FIELDS,*probe.EFFECT_FIELDS):self.assertEqual(1,policy.count(' '+name+';'))

    def test_negative_and_nonfinite_cache_cannot_poison_original_fallback(self):
        d4=self.patched['shaders/program/d4_deferred_shading.fsh'].decode()
        self.assertIn('{ shaderlodRateWeight = 0.0; shaderlodRateCache = vec4(0.0, 0.0, 0.0, -1.0); }',d4)
        self.assertIn('if (shaderlodRateWeight < 1.0)',d4)
        self.assertIn('if (shaderlodRateWeight > 0.0) { shadows',d4)
        self.assertIn('shaderlodLightingCache = vec4(0.0, 0.0, 0.0, -1.0)',self.overlay['shaders/program/shaderlod_lighting_prepass.fsh'].decode())

    def test_real_assembly_is_reproducible_and_preserves_complete_scope(self):
        # Writing own ignored worktree archives is permitted; never builds/launches a client.
        folder=ROOT/'build/lighting-rate-source-fixtures';folder.mkdir(parents=True,exist_ok=True)
        first=folder/'a.zip';second=folder/'b.zip'
        a=probe.build(first,arm='enabled');b=probe.build(second,arm='enabled')
        self.assertEqual(first.read_bytes(),second.read_bytes());self.assertEqual(a,b)
        with zipfile.ZipFile(first) as z:
            for name,value in self.files.items():
                if name not in probe.MEMBERS:self.assertEqual(value,z.read(name),name)
            self.assertEqual(set(self.files)|set(self.overlay)|{probe.METADATA},set(z.namelist()))
        for path in (first,second):path.unlink()

    def test_actual_iris_lexer_jcpp_format_and_property_parser(self):
        iris=ROOT/'dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        self.assertEqual('3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83',probe.sha256(iris.read_bytes()))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
        jars=[]
        for rel,pattern in [('com.google.guava/guava/32.1.2-jre','guava-*.jar'),('it.unimi.dsi/fastutil/8.5.12','fastutil-*.jar'),
                            ('org.joml/joml/1.10.5','joml-*.jar'),('org.slf4j/slf4j-api/2.0.9','slf4j-api-*.jar'),('com.mojang/brigadier/1.3.10','brigadier-*.jar')]:
            candidates=list((cache/rel).glob('*/'+pattern));self.assertEqual(1,len(candidates),rel);jars+=candidates
        minecraft=Path.home()/'.gradle/caches/neoformruntime/intermediate_results/compiledWithNeoForge_14005814aca71498f3705030e9545a3b184086e8_output.jar'
        self.assertEqual('a3f878f97a229b543a2c872c4a3c03b19ebd3efbd11af2caed9c258c08aebf55',probe.sha256(minecraft.read_bytes()))
        jars.append(minecraft)
        harness=r'''
import java.nio.file.*; import java.util.*;
import com.google.common.collect.ImmutableList; import com.google.common.collect.ImmutableMap;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor; import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;
import net.irisshaders.iris.shaderpack.parsing.ConstDirectiveParser;
import net.irisshaders.iris.helpers.StringPair;
class RateIrisProof {
 static void check(boolean x,String m){if(!x)throw new AssertionError(m);}
 public static void main(String[] a)throws Exception{
  String name=a[0]; Path folder=Path.of(a[1]);
  var refs=new HashSet<String>();
  for(String source:Files.readAllLines(folder.resolve("sources.list")))refs.addAll(new OptionAnnotatedSource(Files.readString(Path.of(source))).getBooleanDefineReferences().keySet());
  var settings=new OptionAnnotatedSource(Files.readString(folder.resolve("settings.glsl")));
  var option=settings.getOptionSet(AbsolutePackPath.fromAbsolutePath("/settings.glsl"),refs);
  check(option.getBooleanOptions().containsKey(name),"public option undiscovered");
  check(!refs.contains("SHADERLOD_LIGHTING_RATE_POLICY_INCLUDED")&&!refs.contains("SHADERLOD_SURFACE_CONTINUITY_INCLUDED"),"internal guard leak");
  String buffers=Files.readString(folder.resolve("buffers.glsl"));
  for(boolean on:new boolean[]{false,true}){
   var macros=on?List.of(new StringPair(name,"1")):List.<StringPair>of();
   String folded=JcppProcessor.glslPreprocessSource(buffers,macros);
   var formats=ConstDirectiveParser.findDirectives(folded).stream().filter(v->v.getKey().equals("colortex2Format")).toList();
   check(formats.size()==1,"ambiguous buffer format");check(formats.get(0).getValue().equals(on?"RGBA16F":"RGBA16"),"wrong buffer format");
  }
  String incompatible=JcppProcessor.glslPreprocessSource(buffers,List.of(new StringPair(name,"1"),new StringPair("NORMAL_MAPPING","1")));
  check(ConstDirectiveParser.findDirectives(incompatible).stream().filter(v->v.getKey().equals("colortex2Format")).findFirst().orElseThrow().getValue().equals("RGBA16"),"unsupported profile format changed");
  String minimum="//#define "+name+"\n#ifdef "+name+"\n#endif\n";Files.writeString(folder.resolve("minimal.glsl"),minimum);
  var nodePath=AbsolutePackPath.fromAbsolutePath("/minimal.glsl");
  var node=new FileNode(nodePath,ImmutableList.copyOf(minimum.split("\\R")));
  // Actual immutable graph data constructor avoids unrelated Minecraft file-error UI dependencies.
  var graphConstructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);
  graphConstructor.setAccessible(true);
  var graph=graphConstructor.newInstance(ImmutableMap.of(nodePath,node),ImmutableMap.of());
  for(boolean on:new boolean[]{false,true}){
   var options=new ShaderPackOptions(graph,Map.of(name,Boolean.toString(on)));
   String foldedProperties=PropertiesPreprocessor.preprocessSource(Files.readString(folder.resolve("properties.glsl")),options,List.of());
   var raw=new Properties();raw.load(new java.io.StringReader(foldedProperties));
   var constructor=ShaderProperties.class.getDeclaredConstructor();constructor.setAccessible(true);
   var props=constructor.newInstance();
   var scaleParser=ShaderProperties.class.getDeclaredMethod("lambda$new$34",String.class,String.class);scaleParser.setAccessible(true);
   String scale=raw.getProperty("scale.deferred4");if(scale!=null)scaleParser.invoke(props,scale,"deferred4");
   var programParser=ShaderProperties.class.getDeclaredMethod("handleProgramEnabledDirective",String.class,String.class,String.class,java.util.function.Consumer.class);programParser.setAccessible(true);
   String key="program.world0/deferred5.enabled";String value=raw.getProperty(key);
   java.util.function.Consumer<String> receiver=pass->props.getConditionallyEnabledPrograms().put(pass,value);
   programParser.invoke(null,"program.",key,value,receiver);
   check(props.getViewportScaleOverrides().containsKey("deferred4")==on,"viewport graph mismatch");
   if(on)check(props.getViewportScaleOverrides().get("deferred4").scale()==.5f,"not half viewport");
   check(props.getConditionallyEnabledPrograms().get("world0/deferred5").equals(Boolean.toString(on)),"stage5 toggle mismatch");
  }
  System.out.println("actual Iris lexer, Jcpp, directive format and ShaderProperties accepted");
 }
}
'''
        with tempfile.TemporaryDirectory(prefix='shaderlod-rate-iris-') as temp:
            folder=Path(temp);extra=[]
            with zipfile.ZipFile(iris) as z:
                for name in ('jcpp-1.4.14.jar','glsl-transformer-2.0.1.jar'):
                    p=folder/name;p.write_bytes(z.read('META-INF/jarjar/'+name));extra.append(p)
            (folder/'RateIrisProof.java').write_text(harness)
            (folder/'settings.glsl').write_bytes(probe.patch_members(self.files,M,'disabled')['shaders/settings.glsl'])
            (folder/'buffers.glsl').write_bytes(self.patched['shaders/include/buffers.glsl'])
            (folder/'properties.glsl').write_text(M['members'][1]['replacements'][0]['after'].split('flip.composite1.colortex6 = true\n',1)[1])
            paths=[]
            for i,(n,data) in enumerate({**self.patched,**self.overlay}.items()):
                if n.endswith(('.glsl','.fsh','.vsh')):
                    p=folder/f'source{i}.glsl';p.write_bytes(data);paths.append(str(p))
            (folder/'sources.list').write_text('\n'.join(paths))
            result=subprocess.run(['java','--class-path',os.pathsep.join(map(str,(iris,*jars,*extra))),str(folder/'RateIrisProof.java'),probe.SWITCH,str(folder)],capture_output=True,text=True,timeout=30)
            self.assertEqual(0,result.returncode,result.stdout+result.stderr)
            self.assertIn('actual Iris lexer, Jcpp, directive format and ShaderProperties accepted',result.stdout)

if __name__=='__main__':unittest.main()
