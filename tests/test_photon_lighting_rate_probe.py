"""Assemblable source, scalar-policy and actual pinned Iris preprocessing fixtures."""
import copy
import io
import json
import math
import os
from pathlib import Path
import subprocess
import struct
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


def f32(value):
    return struct.unpack('f', struct.pack('f', value))[0]


def ao_base(pixel):
    # Strict maps-off/TAAU-off profile: taau_render_scale=1. Match source float32
    # arithmetic and ivec2 truncation toward zero, rather than Python floor().
    return tuple(int(f32(f32(f32(value + .5) * .5) - .5)) for value in pixel)


def ao_union(origin, view_size, ambient_size, depth_size):
    """Independent coordinate oracle for all four original reconstruction tap sets."""
    if (any(size <= 0 for size in ambient_size) or ambient_size != depth_size or
        any(not math.isfinite(size) or size < 2 for size in view_size) or
        any(p < 0 or p+1 >= int(size) for p, size in zip(origin, view_size))):
        return None
    taps = set()
    for x, y in ((0, 0), (1, 0), (0, 1), (1, 1)):
        base = ao_base((origin[0]+x, origin[1]+y))
        taps.update((base[0]+dx, base[1]+dy) for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)))
    if any(p < 0 or p >= size for tap in taps for p, size in zip(tap, ambient_size)):
        return None
    if any(max(tap[k] for tap in taps)-min(tap[k] for tap in taps) > 2 for k in range(2)):
        return None
    return taps


def ao_union_eligible(origin, view_size, ambient_size, depth_size, values):
    taps = ao_union(origin, view_size, ambient_size, depth_size)
    if taps is None:
        return False
    ao = []
    for tap in taps:
        ambient, depth = values[tap]
        ambient = tuple(map(f32, ambient)); depth = f32(depth)
        if (any(not math.isfinite(v) for v in ambient) or not math.isfinite(depth) or
            not 0 <= ambient[0] <= 1 or not 0 <= depth <= 1):
            return False
        ao.append(ambient[0])
    return f32(max(ao)-min(ao)) <= f32(.10)


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
        original=self.files['shaders/program/d4_deferred_shading.fsh'].decode()
        math_start=original.index('        // Upscale ambient occlusion')
        math_end=original.index('        // Calculate lighting dot products', math_start)
        self.assertIn(original[math_start:math_end],prepass)

    def test_ao_union_outer_variation_thresholds_dimensions_and_invalid_data(self):
        prepass=self.overlay['shaders/program/shaderlod_lighting_prepass.fsh'].decode()
        self.assertIn('bool shaderlod_rate_ao_union_eligible(ivec2 origin)',prepass)
        self.assertIn('ivec2 base = ivec2((vec2(pixel) + 0.5) * (0.5 / taau_render_scale) - 0.5)',prepass)
        self.assertIn('textureSize(colortex6, 0)',prepass)
        self.assertIn('textureSize(colortex14, 0)',prepass)
        self.assertIn('any(greaterThanEqual(maximum_tap, ambient_size))',prepass)
        self.assertIn('maximum_ao - minimum_ao <= 0.10',prepass)
        representative=prepass.split('vec4 shaderlod_evaluate_representative(',1)[1]
        self.assertLess(representative.index('shaderlod_rate_ao_union_eligible(texel)'), representative.index('texelFetch(colortex6,'))
        self.assertNotIn('float ao_span',prepass)
        origin=(32, 32); view=(100, 100); size=(50, 50)
        taps=ao_union(origin,view,size,size)
        self.assertEqual({(x,y) for x in (15,16,17) for y in (15,16,17)},taps)
        values={p:((1.,0.,.5,.5),.5) for p in taps}
        self.assertTrue(ao_union_eligible(origin,view,size,size,values))
        rep_taps={(15,15),(15,16),(16,15),(16,16)}
        for outside in ((17,16),(16,17),(17,17)):
            changed=values | {outside:((.2,0.,.5,.5),.5)}
            # Former representative-only check accepted, while the actual full
            # tile union rejects previously unobserved contact-AO variation.
            self.assertEqual(0,max(changed[p][0][0] for p in rep_taps)-min(changed[p][0][0] for p in rep_taps))
            self.assertFalse(ao_union_eligible(origin,view,size,size,changed))
        # Independent equal-depth interpolation counterexample: right member
        # originally sees .8 AO when its extra outer column contains .2.
        self.assertAlmostEqual(.8,.75*1.+.25*.2)
        zero={p:((0.,0.,.5,.5),.5) for p in taps}
        for spread, expected in ((0.,True),(.099,True),(f32(.10),True),(.100001,False)):
            changed=zero | {(17,17):((spread,0.,.5,.5),.5)}
            self.assertEqual(expected,ao_union_eligible(origin,view,size,size,changed))
        for bad in (math.nan,math.inf,-math.inf):
            for channel in range(4):
                ambient=list(values[(17,17)][0]); ambient[channel]=bad
                self.assertFalse(ao_union_eligible(origin,view,size,size,values | {(17,17):(ambient,.5)}))
            self.assertFalse(ao_union_eligible(origin,view,size,size,values | {(17,17):((1.,0.,.5,.5),bad)}))
        for depth in (-.01,1.01):
            self.assertFalse(ao_union_eligible(origin,view,size,size,values | {(17,17):((1.,0.,.5,.5),depth)}))
        for outside_ao in (-.01,1.01):
            self.assertFalse(ao_union_eligible(origin,view,size,size,values | {(17,17):((outside_ao,0.,.5,.5),.5)}))
        for invalid_view in ((math.nan,100),(100,math.inf),(-1,100),(100,1)):
            self.assertIsNone(ao_union(origin,invalid_view,size,size))
        for bad_size in ((0,50),(50,0),(-1,50)):
            self.assertIsNone(ao_union(origin,view,bad_size,bad_size))
        self.assertIsNone(ao_union(origin,view,size,(49,50)))
        self.assertEqual({(0,0),(0,1),(1,0),(1,1)},ao_union((0,0),(8,8),(4,4),(4,4)))
        self.assertIsNone(ao_union((6,6),(8,8),(4,4),(4,4))) # Last AO neighborhood exceeds valid texture edge.
        self.assertIsNone(ao_union((6,4),(7,7),(3,3),(3,3))) # Incomplete final odd-width tile.
        self.assertIsNone(ao_union((4,6),(7,7),(3,3),(3,3))) # Incomplete final odd-height tile.
        for view in ((8,8),(9,7),(2976,1672)):
            size=tuple(v//2 for v in view)
            origins=[(0,0),(2,2),(view[0]-2,view[1]-2)]
            for origin in origins:
                union=ao_union(origin,view,size,size)
                if union is not None:
                    # Every original member footprint is covered; no clamp or
                    # dropped outer tap is used to manufacture eligibility.
                    for dx,dy in ((0,0),(1,0),(0,1),(1,1)):
                        base=ao_base((origin[0]+dx,origin[1]+dy))
                        self.assertTrue({(base[0]+x,base[1]+y) for x,y in ((0,0),(1,0),(0,1),(1,1))}.issubset(union))

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
        iris=ROOT/'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        self.assertEqual('3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83',probe.sha256(iris.read_bytes()))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
        jars=[]
        for rel,pattern in [('com.google.guava/guava/32.1.2-jre','guava-*.jar'),('it.unimi.dsi/fastutil/8.5.12','fastutil-*.jar'),
                            ('org.joml/joml/1.10.5','joml-*.jar'),('org.slf4j/slf4j-api/2.0.9','slf4j-api-*.jar'),('com.mojang/brigadier/1.3.10','brigadier-*.jar')]:
            candidates=list((cache/rel).glob('*/'+pattern));self.assertEqual(1,len(candidates),rel);jars+=candidates
        # The fixture constructs immutable graph data directly; no Minecraft UI
        # path or environment-specific intermediate Minecraft archive is required.
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

    def test_complete_translation_units_with_actual_iris_jcpp_and_pinned_glsl_parser(self):
        """Grammar proof across actual included programs, separate from option proof.

        Full forest options are selected by actual Iris OptionAnnotatedSource,
        then Iris Jcpp and pinned EnhancedParser parse all FSH/VSH entrypoints.
        This does not type-check GLSL, run Iris transformations, link a program or
        validate a GL driver. A deliberately broken active helper must fail.
        """
        iris=ROOT/'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        self.assertEqual('3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83',probe.sha256(iris.read_bytes()))
        forest_report=ROOT/'src/main/resources/assets/shaderlod/photonquad/native366.json'
        # Explicit source-test scenario based on the retained native profile,
        # with environment reflections disabled; no captured runtime-map claim.
        native=json.loads(forest_report.read_text()) | {'ENVIRONMENT_REFLECTIONS':'false'}
        self.assertEqual('a111291c05aa517e89178ec3eb13dfa6ee4d47f9785c965540353ffc60f4f5d5',
                         probe.sha256(json.dumps(native,sort_keys=True,separators=(',',':')).encode()))
        excluded=('NORMAL_MAPPING','SPECULAR_MAPPING','SHADOW_COLOR','SHADOW_VPS','COLORED_LIGHTS',
                  'CLOUD_SHADOWS','SH_SKYLIGHT','TAAU','BLOCKY_CLOUDS','ENVIRONMENT_REFLECTIONS')
        self.assertTrue(all(native[name]=='false' for name in excluded))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1';dependencies=[]
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar',
                        'it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar',
                        'org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar',
                        'org.antlr/antlr4-runtime/4.13.1/*/antlr4-runtime-4.13.1.jar'):
            matches=list(cache.glob(pattern));self.assertEqual(1,len(matches),pattern);dependencies.append(matches[0])
        self.assertEqual('54665d2838cc66458343468efc539e454fc95b46a8a04b13c6ac43fc9be63505',probe.sha256(dependencies[-1].read_bytes()))
        modified=probe.patch_members(self.files,M,'disabled') | self.overlay
        java=r'''
import java.nio.file.*; import java.util.*; import java.util.regex.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.helpers.StringPair;
import io.github.douira.glsl_transformer.parser.EnhancedParser;
import org.antlr.v4.runtime.misc.ParseCancellationException;
class LightingRateGrammarProof {
 static final Pattern INCLUDE=Pattern.compile("(?m)^\\s*#include\\s+\"([^\"]+)\"[^\\n]*");
 static final class Graph {
  final Map<String,OptionAnnotatedSource> nodes=new HashMap<>();
  final Set<String> references=new HashSet<>();
  Graph(Path folder) throws Exception {
   try(var paths=Files.walk(folder)) {
    for(Path file:paths.filter(Files::isRegularFile).toList()) {
     String name=folder.relativize(file).toString().replace('\\','/');
     var annotated=new OptionAnnotatedSource(Files.readString(file));nodes.put(name,annotated);
     references.addAll(annotated.getBooleanDefineReferences().keySet());
    }
   }
  }
 }
 static String expand(Graph graph,String name,Map<String,String> selectedOptions,Set<String> stack) {
  if(!stack.add(name))throw new AssertionError("cyclic literal include "+name);
  var annotated=graph.nodes.get(name);if(annotated==null)throw new AssertionError("missing include "+name);
  var options=annotated.getOptionSet(AbsolutePackPath.fromAbsolutePath("/"+name.substring("shaders/".length())),graph.references);
  // Actual Iris edits each source file before includes expand. Editing a flattened
  // TU updates only one duplicate definition when settings is included twice.
  String selected=annotated.apply(new MutableOptionValues(options,selectedOptions));
  var matcher=INCLUDE.matcher(selected);var expanded=new StringBuffer();
  while(matcher.find()) {
   String target=matcher.group(1);
   String path=target.startsWith("/")?"shaders"+target:Path.of(name).getParent().resolve(target).normalize().toString().replace('\\','/');
   matcher.appendReplacement(expanded,Matcher.quoteReplacement(expand(graph,path,selectedOptions,stack)));
  }
  matcher.appendTail(expanded);stack.remove(name);return expanded.toString();
 }
 static String prepare(Graph graph,String name,Properties nativeOptions,boolean gate) {
  var selectedOptions=new HashMap<String,String>();
  for(String key:nativeOptions.stringPropertyNames())selectedOptions.put(key,nativeOptions.getProperty(key));
  selectedOptions.put("SHADERLOD_LIGHTING_RATE_PROBE",Boolean.toString(gate));
  String selected=expand(graph,name,selectedOptions,new HashSet<>());
  return JcppProcessor.glslPreprocessSource(selected,List.of(
   new StringPair("MC_VERSION","12101"),new StringPair("MC_GL_VERSION","410"),
   new StringPair("MC_GLSL_VERSION","410"),new StringPair("MC_HAND_DEPTH","0.125"),
   new StringPair("MC_SHADOW_QUALITY","1.0"),new StringPair("IS_IRIS","1")));
 }
 static void parse(String source) {
  var parser=new EnhancedParser();parser.setThrowParseErrors(true);parser.parse(source);
 }
 public static void main(String[] args) throws Exception {
  Path folder=Path.of(args[0]);var nativeOptions=new Properties();
  nativeOptions.load(Files.newBufferedReader(folder.resolve("forest.properties")));
  var graphs=new HashMap<String,Graph>();
  for(String name:List.of("original","modified","broken"))graphs.put(name,new Graph(folder.resolve(name)));
  int passed=0,negative=0;
  for(String row:Files.readAllLines(folder.resolve("cases.list"))) {
   String[] fields=row.split("\\t");String source=prepare(graphs.get(fields[0]),fields[1],nativeOptions,Boolean.parseBoolean(fields[2]));
   boolean broken=fields[3].equals("broken");String expected=fields[4];
   if(!source.contains("void main("))throw new AssertionError("incomplete TU: "+row);
   if(source.contains("#include"))throw new AssertionError("unexpanded include: "+row);
   boolean hasCache=source.contains("layout(location = 0) out vec4 shaderlodLightingCache");
   boolean hasUnion=source.contains("bool shaderlod_rate_ao_union_eligible(");
   boolean hasResolve=source.contains("vec4 shaderlodRateCache");
   if(hasCache!=expected.startsWith("cache"))throw new AssertionError("wrong prepass/original selection: "+row);
   if(hasUnion!=expected.equals("cache-world0"))throw new AssertionError("wrong world-scoped union selection: "+row);
   if(hasResolve!=expected.equals("resolve-world0"))throw new AssertionError("wrong cache resolve/original selection: "+row);
   if(broken) {
    try {parse(source);throw new AssertionError("broken delimiter was accepted");}
    catch(ParseCancellationException exception) {
     negative++;System.out.println("deliberately broken active prepass rejected: "+exception.getMessage());
    }
   } else {
    try {parse(source);}catch(Throwable failure){throw new AssertionError("complete TU failed: "+row,failure);}
    passed++;
   }
  }
  if(passed!=31 || negative!=1)throw new AssertionError("wrong grammar case counts "+passed+"/"+negative);
  System.out.println("31 complete deferred4/5 fragment/vertex grammar parses passed across three worlds; broken active helper rejected");
 }
}
'''
        with tempfile.TemporaryDirectory(prefix='shaderlod-lighting-grammar-') as temporary:
            folder=Path(temporary);embedded=[]
            with zipfile.ZipFile(iris) as archive:
                for name in ('glsl-transformer-2.0.1.jar','jcpp-1.4.14.jar'):
                    data=archive.read('META-INF/jarjar/'+name)
                    if name=='glsl-transformer-2.0.1.jar':
                        self.assertEqual('b8ca5e61b41ae18ddd5d4d5d6785cb9acd8fd7ac0c5c0b0689b872b039c11b9f',probe.sha256(data))
                        with zipfile.ZipFile(io.BytesIO(data)) as parser_archive:
                            member='io/github/douira/glsl_transformer/parser/EnhancedParser.class'
                            self.assertEqual('11133ca7cbfbc89777b15f06a31323b26c1ab6448b1822f4086e9503f3527814',probe.sha256(parser_archive.read(member)))
                    path=folder/name;path.write_bytes(data);embedded.append(path)
            (folder/'forest.properties').write_text('\n'.join(f'{key}={value}' for key,value in sorted(native.items())))
            helper='shaders/program/shaderlod_lighting_prepass.fsh'
            before=b'if (!shaderlod_rate_ao_union_eligible(texel)) return'
            self.assertEqual(1,modified[helper].count(before))
            corrupted=modified | {helper:modified[helper].replace(before,b'if (!shaderlod_rate_ao_union_eligible(texel) return')}
            for graph,files in (('original',self.files),('modified',modified),('broken',corrupted)):
                for name,data in files.items():
                    if name.endswith(('.fsh','.vsh','.glsl','.csh')):
                        path=folder/graph/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
            rows=[]
            for world in ('world0','world1','world-1'):
                for stage in ('fsh','vsh'):
                    original=f'shaders/{world}/deferred4.{stage}'
                    rows.append('\t'.join(('original',original,'false','good','original')))
                    for program in ('deferred4','deferred5'):
                        name=f'shaders/{world}/{program}.{stage}'
                        for gate in (False,True):
                            expected=('cache-'+world) if gate and program=='deferred4' and stage=='fsh' else \
                                'resolve-world0' if gate and program=='deferred5' and stage=='fsh' and world=='world0' else 'original'
                            rows.append('\t'.join(('modified',name,str(gate).lower(),'good',expected)))
            name='shaders/world0/deferred4.fsh'
            rows.append('\t'.join(('broken',name,'true','broken','cache-world0')))
            # The same invalid helper must be dormant under option OFF; full
            # source expansion alone cannot prove which branch actually parses.
            rows.append('\t'.join(('broken',name,'false','good','original')))
            self.assertEqual(32,len(rows))
            (folder/'cases.list').write_text('\n'.join(rows))
            source=folder/'LightingRateGrammarProof.java';source.write_text(java)
            result=subprocess.run(['java','-Xmx1G','--class-path',os.pathsep.join(map(str,[iris,*dependencies,*embedded])),
                                   str(source),str(folder)],capture_output=True,text=True,timeout=120)
            self.assertEqual(0,result.returncode,result.stdout+result.stderr)
            self.assertIn('31 complete deferred4/5 fragment/vertex grammar parses passed across three worlds; broken active helper rejected',result.stdout)
            print(result.stdout.strip())

if __name__=='__main__':unittest.main()
