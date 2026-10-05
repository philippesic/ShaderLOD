"""Pinned source, actual Iris discovery and finite-difference footprint fixtures.
No driver compilation, rendering, GPU time or quality acceptance is asserted.
"""
import copy
import io
import json
import math
import os
import posixpath
import re
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import build_photon_procedural_lod as probe
MANIFEST = json.loads((ROOT / probe.MANIFEST).read_text())


def select_private(source, selected, water=True, parallax=False):
    text = source.decode()
    declaration = MANIFEST['option_declaration']
    text = text.replace(declaration, '', 1).replace(declaration.replace('//#define', '#define'), '', 1)
    output, stack = [], []
    for line in text.splitlines(keepends=True):
        if line == '#ifdef ' + probe.SWITCH + '\n':
            stack.append(selected)
        elif stack and line == '#if defined PROGRAM_GBUFFERS_WATER && defined WATER_WAVES\n':
            stack.append(water)
        elif stack and line in ('#else // SHADERLOD_PROCEDURAL_STAGE\n', '#else // ' + probe.SWITCH + '\n'):
            stack[-1] = not stack[-1]
        elif line == '#endif // SHADERLOD_PROCEDURAL_STAGE\n':
            stack.pop()
        elif line == '#endif // ' + probe.SWITCH + '\n':
            stack.pop()
        elif not stack or all(stack):
            output.append(line)
    if stack:
        raise AssertionError('Unclosed private directive')
    return ''.join(output).encode()


def smooth(a, b, x):
    t = max(0, min(1, (x - a) / (b - a)))
    return t*t*(3-2*t)


def cycles(dx, dy, carrier, noise_bound):
    return max(abs(sum(c*x for c, x in zip(carrier, d))) + noise_bound*math.hypot(*d) for d in (dx, dy))


def weights(count, dx, dy, dist, direction=(math.cos(math.pi/6), math.sin(math.pi/6)), flowing=False, frequency=0.7, lacunarity=1.7, size=512):
    result = []
    wavelength, noise_frequency = 1., .007
    for i in range(count):
        c = cycles(dx, dy, tuple(x*frequency/wavelength for x in direction), 2*noise_frequency*size*math.sqrt(2)/wavelength)
        result.append(1. if i == 0 else 1. - smooth(32, 48, dist)*smooth(.125, .25, c))
        frequency *= lacunarity
        wavelength *= 1.5
        noise_frequency *= 2.5
        if not flowing:
            a = math.pi * (3-math.sqrt(5))
            direction = (direction[0]*math.cos(a)+direction[1]*math.sin(a), -direction[0]*math.sin(a)+direction[1]*math.cos(a))
    return result


def decode_green(data):
    """Read pinned noninterlaced RGBA8 PNG without an optional imaging package."""
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise AssertionError('Not PNG')
    pos, parts = 8, []
    while pos < len(data):
        n = struct.unpack('>I', data[pos:pos+4])[0]
        tag, body = data[pos+4:pos+8], data[pos+8:pos+8+n]
        if tag == b'IHDR':
            width, height, depth, kind, compression, filtering, interlace = struct.unpack('>IIBBBBB', body)
            if (depth, kind, compression, filtering, interlace) != (8, 6, 0, 0, 0):
                raise AssertionError('Unexpected pinned noise encoding')
        if tag == b'IDAT':
            parts.append(body)
        pos += n+12
    raw = zlib.decompress(b''.join(parts))
    rows, previous, pos = [], bytearray(width*4), 0
    for y in range(height):
        mode, encoded = raw[pos], raw[pos+1:pos+1+width*4]
        pos += 1+width*4
        row = bytearray(width*4)
        for i, value in enumerate(encoded):
            left = row[i-4] if i >= 4 else 0
            up, ul = previous[i], previous[i-4] if i >= 4 else 0
            if mode == 0: p=0
            elif mode == 1: p=left
            elif mode == 2: p=up
            elif mode == 3: p=(left+up)//2
            elif mode == 4:
                estimate=left+up-ul
                distances=(abs(estimate-left),abs(estimate-up),abs(estimate-ul))
                p=(left,up,ul)[distances.index(min(distances))]
            else: raise AssertionError('Unknown PNG filter')
            row[i]=(value+p)&255
        rows.append([row[i]/255 for i in range(1, width*4, 4)])
        previous=row
    return rows


def sample_noise(rows, uv):
    size=len(rows)
    x,y=(v*size-.5 for v in uv)
    xi,yi=math.floor(x),math.floor(y)
    fx,fy=x-xi,y-yi
    a,b=rows[yi%size][xi%size],rows[yi%size][(xi+1)%size]
    c,d=rows[(yi+1)%size][xi%size],rows[(yi+1)%size][(xi+1)%size]
    return (a*(1-fx)+b*fx)*(1-fy)+(c*(1-fx)+d*fx)*fy


def height(rows, coord, count, octave_weights, t=17., flowing=False, flow=(1.,0.), persistence=.5, frequency=.7, lacunarity=1.7, variation=True):
    direction=flow if flowing else (math.cos(math.pi/6),math.sin(math.pi/6))
    amplitude,wavelength,noise_frequency,total=1.,1.,.007,0.
    fetches=0
    for i in range(count):
        weight=octave_weights[i]
        wave=.375
        if weight > 0:
            noise=sample_noise(rows,(coord[0]*noise_frequency,(coord[1]+.25*t)*noise_frequency))
            phase=math.sqrt(9.8*2*math.pi/wavelength)*t - 2*math.pi/wavelength*(sum(direction[j]*coord[j]*frequency for j in range(2))+noise*2)
            wave=.375*(1-weight)+(math.sin(phase)*.5+.5)**2*weight
            fetches+=1
        total+=wave*amplitude
        amplitude*=persistence
        frequency*=lacunarity
        wavelength*=1.5
        noise_frequency*=2.5
        if not flowing:
            a=math.pi*(3-math.sqrt(5))
            direction=(direction[0]*math.cos(a)+direction[1]*math.sin(a),-direction[0]*math.sin(a)+direction[1]*math.cos(a))
    if variation:
        total*=max(.4,sample_noise(rows,(coord[0]*.001,(coord[1]+.1*t)*.001))*2-.5)
        fetches+=1
    return total*(1-persistence)/(1-persistence**count),fetches


def normal(rows, coord, count, w, influence=.04, **kw):
    h0=height(rows,coord,count,w,**kw)[0]
    h1=height(rows,(coord[0]+.1,coord[1]),count,w,**kw)[0]
    h2=height(rows,(coord[0],coord[1]+.1),count,w,**kw)[0]
    v=((h1-h0)*influence,(h2-h0)*influence,.1)
    norm=math.sqrt(sum(x*x for x in v))
    return tuple(x/norm for x in v)


def parallax(rows, coord, direction, n, *, reference=False, **kw):
    # Independent reference enumerates ray points from q0. The source algorithm
    # incrementally advances q, then interpolates the same bracketing segment.
    if direction[2] >= -1e-6 or kw.get('persistence', .5) >= 1:
        return coord, False, 0
    step=tuple(x/(-direction[2])*.2/4 for x in direction[:2])
    origin=coord
    h=height(rows,coord,n,[1]*n,**kw)[0]
    if not h > 0 or not math.isfinite(h):
        return coord,False,0
    previous=h
    count=0
    while count*.25 < h:
        count+=1
        if count>8:return coord,False,count
        coord=tuple(origin[j]+count*step[j] if reference else coord[j]+step[j] for j in range(2))
        previous,h=h,height(rows,coord,n,[1]*n,**kw)[0]
    before=previous-count*.25+.25
    after=h-count*.25
    denominator=after-before
    if abs(denominator)<=1e-6:return coord,False,count
    f=after/denominator
    result=tuple(origin[j]+(count-f)*step[j] if reference else coord[j]*(1-f)+(coord[j]-step[j])*f for j in range(2))
    return result,all(math.isfinite(x) for x in result),count


class PinnedSourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files=probe.archive_members(ROOT/probe.BASE)
        if probe.sha256((ROOT/probe.BASE).read_bytes()) != probe.BASE_SHA256:
            raise AssertionError('Original Photon changed')

    def test_original_and_disabled_restore_original_source_and_scope(self):
        for arm in probe.ARMS:
            patched=probe.patch_members(self.files,MANIFEST,arm)
            for enabled,water,parallax in ((False,False,False),(False,True,False),(True,False,False),(False,True,True)):
                for name in probe.MEMBERS:
                    self.assertEqual(self.files[name],select_private(patched[name],enabled,water,parallax))
            changed={n for n in self.files if self.files[n] != patched[n]}
            self.assertEqual(set() if arm=='original' else set(probe.MEMBERS),changed)
        for name in ('LICENSE','shaders/program/dh_water.fsh','shaders/program/shadow.fsh','shaders/include/surface/water_normal.glsl'):
            if name in self.files:
                self.assertEqual(self.files[name],probe.patch_members(self.files,MANIFEST,'enabled')[name])

    def test_safe_capture_and_qualified_sampling_before_work(self):
        patched=probe.patch_members(self.files,MANIFEST,'enabled')[probe.PROGRAM].decode()
        self.assertLess(patched.index('dFdx(shaderlod_displaced_coord)'),patched.index('\n        discard;'))
        self.assertLess(patched.index('dFdx(shaderlod_displaced_coord)'),patched.index('if (is_water)'))
        helper=(ROOT/probe.HELPER_ROOT/'shaders/include/shaderlod/procedural_water.glsl').read_text()
        self.assertLess(helper.index('if (weight > 0.0)'),helper.index('textureGrad(noisetex'))
        self.assertNotIn('dFdx',helper)
        self.assertNotIn('texture(',helper)
        self.assertIn('i == 0 ? 1.0',helper)
        self.assertIn('get_water_normal(world_pos',helper)
        self.assertIn('dist <= 32.0',helper)
        self.assertIn('WATER_WAVE_PERSISTENCE >= 2.0',helper)
        self.assertIn('float(WATER_WAVE_ITERATIONS)',helper)

    def test_full_parallax_clone_preserves_height_math_and_qualifies_sampling(self):
        upstream=self.files['shaders/include/surface/water_normal.glsl'].decode()
        original=upstream[upstream.index('float get_water_height('):upstream.index('vec3 get_water_normal(')]
        clone=(ROOT/probe.HELPER_ROOT/'shaders/include/shaderlod/procedural_parallax.glsl').read_text()
        actual=clone[clone.index('float shaderlod_procedural_full_height('):clone.index('vec2 shaderlod_procedural_full_parallax(')]
        actual=actual.replace('float shaderlod_procedural_full_height(', 'float get_water_height(')
        actual=actual.replace('textureLod(noisetex, noise_coord, 0.0)', 'texture(noisetex, noise_coord)')
        actual=actual.replace('textureLod(\n', 'texture(\n').replace('height_variation_frequency, 0.0', 'height_variation_frequency')
        self.assertEqual(original,actual)
        self.assertNotIn('texture(',clone)
        for statement in ('const int step_count = 4;', 'const float parallax_depth = 0.2;',
                          'while (depth_march < depth_value)', 'depth_march += rcp(float(step_count));'):
            self.assertIn(statement,clone)
        for statement in ('valid = false;', 'tangent_dir.z >= -eps', 'if (!(depth_value > 0.0)',
                          'abs(denominator) <= eps', 'safety_steps > 8'):
            self.assertIn(statement,clone)
        main=probe.patch_members(self.files,MANIFEST,'enabled')[probe.PROGRAM].decode().split('void main() {',1)[1]
        first_derivative=main.index('vec2 shaderlod_coord_dx = dFdx(shaderlod_displaced_coord)')
        first_discard=re.search(r'^\s*discard;',main,re.M).start()
        self.assertLess(first_derivative,first_discard)
        self.assertLess(main.index('shaderlod_procedural_full_parallax('),first_derivative)
        self.assertLess(main.index('vec2 shaderlod_base_dx = dFdx'),main.index('shaderlod_procedural_full_parallax('))
        self.assertIn('smoothstep(2.0, 4.0, shaderlod_footprint_expansion)',main)
        # The dedicated water branch closes before derivative execution.
        self.assertIn('shaderlod_coord_valid);\n    }\n#endif\n',main[:first_derivative])
        self.assertIn('vec2 shaderlod_displaced_coord = ',main[:first_derivative])
        self.assertIn('float shaderlod_coord_valid_dx = dFdx(',main)
        self.assertNotIn('&&\n        dFdx',main)

    def test_noise_contract_pins_source_and_filter(self):
        self.assertEqual(self.files['LICENSE'],(ROOT/probe.HELPER_ROOT/'LICENSE').read_bytes())
        self.assertEqual({'texture':{'blur':True,'clamp':False}},json.loads(self.files['shaders/image/noise.png.mcmeta']))
        self.assertIn(b'texture.noise = image/noise.png',self.files['shaders/shaders.properties'])
        pin=MANIFEST['noise_filter_source']
        jar=ROOT/pin['jar_path']
        self.assertEqual(pin['jar_sha256'],probe.sha256(jar.read_bytes()))
        with zipfile.ZipFile(jar) as z:
            self.assertEqual(pin['class_sha256'],probe.sha256(z.read(pin['class_path'])))
        run=subprocess.run(['javap','-c','-p','-classpath',str(jar),'net.irisshaders.iris.targets.backed.NativeImageBackedCustomTexture'],capture_output=True,text=True,timeout=10)
        self.assertEqual(0,run.returncode,run.stderr)
        self.assertIn('shouldBlur',run.stdout)
        self.assertIn('sipush        9729',run.stdout)

    def test_fail_closed_on_pins_manifest_and_output(self):
        for name in (*probe.MEMBERS,*(p['path'] for p in MANIFEST['pins'])):
            with self.assertRaises(ValueError):
                probe.patch_members(self.files|{name:self.files[name]+b'\n'},MANIFEST,'enabled')
        for field in ('base_sha256','switch','quality_class','base_version'):
            m=copy.deepcopy(MANIFEST);m[field]='bad'
            with self.assertRaises(ValueError):probe.patch_members(self.files,m,'enabled')
        m=copy.deepcopy(MANIFEST);m['iris_option_discovery']['helper_guard_contract']='old #ifndef'
        with self.assertRaises(ValueError):probe.patch_members(self.files,m,'enabled')
        with self.assertRaises(ValueError):probe.build(ROOT/'bad.zip')

    def test_actual_iris_jcpp_and_glsl_parser_all_affected_entrypoints(self):
        """Iris's option lexer cannot catch selection syntax; parse full graphs.

        Use the held coastal options, Iris Jcpp, and the pinned EnhancedParser.
        Broken r2 must reproduce the runtime syntax error. This is a syntax
        check, not TransformPatcher execution, GL compilation, or rendering.
        """
        pin=MANIFEST['glsl_parser_regression']
        iris=ROOT/MANIFEST['iris_option_discovery']['jar_path']
        self.assertEqual(MANIFEST['iris_option_discovery']['jar_sha256'],probe.sha256(iris.read_bytes()))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
        dependencies=[]
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar',
                        'it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar',
                        'org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar',
                        'org.antlr/antlr4-runtime/4.13.1/*/antlr4-runtime-4.13.1.jar'):
            matches=list(cache.glob(pattern));self.assertEqual(1,len(matches),pattern)
            dependencies.append(matches[0])
        self.assertEqual(pin['antlr_sha256'],probe.sha256(dependencies[-1].read_bytes()))
        paths=sorted(n for n,data in self.files.items() if
                     n.endswith('.fsh') and b'gbuffers_all_translucent.fsh' in data)
        self.assertEqual(24,len(paths))
        self.assertEqual({'world0','world-1','world1'},{n.split('/')[1] for n in paths})
        patched=probe.patch_members(self.files,MANIFEST,'disabled')
        helper='shaders/include/shaderlod/procedural_water.glsl'
        fixed=b'any(isinf(dy))) {';broken=b'any(isinf(dy)) {'
        self.assertEqual(1,patched[helper].count(fixed))
        regression=patched|{helper:patched[helper].replace(fixed,broken)}
        def expand(files,name,stack=()):
            self.assertNotIn(name,stack,'Cyclic literal include')
            self.assertIn(name,files)
            def include(match):
                target=match.group(1)
                path='shaders/'+target.lstrip('/') if target.startswith('/') else posixpath.normpath(posixpath.join(posixpath.dirname(name),target))
                return expand(files,path,stack+(name,))
            return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*',include,files[name].decode(),flags=re.M)
        java=r'''
import java.nio.file.*; import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.helpers.StringPair;
import io.github.douira.glsl_transformer.parser.EnhancedParser;
import org.antlr.v4.runtime.misc.ParseCancellationException;
class ProceduralGlslProof {
 static String prepare(Path file,boolean enabled,boolean parallax) throws Exception {
  var annotated=new OptionAnnotatedSource(Files.readString(file));
  var options=annotated.getOptionSet(AbsolutePackPath.fromAbsolutePath("/source.fsh"),annotated.getBooleanDefineReferences().keySet());
  if(annotated.getStringOptions().values().stream().noneMatch(v->v.getName().equals("WATER_WAVE_ITERATIONS")&&v.getDefaultValue().equals("3")))
   throw new AssertionError("held coastal three-octave default changed");
  if(annotated.getBooleanOptions().values().stream().noneMatch(v->v.getName().equals("WATER_PARALLAX")&&v.getDefaultValue()))
   throw new AssertionError("held coastal parallax default changed");
  var nativeOptions=new HashMap<String,String>();
  for(String name:List.of("BORDER_FOG","CLOUD_SHADOWS","SHADOW_COLOR","SHADOW_VPS","SH_SKYLIGHT"))nativeOptions.put(name,"false");
  nativeOptions.put("SHADERLOD_PROCEDURAL_FOOTPRINT",Boolean.toString(enabled));
  nativeOptions.put("WATER_PARALLAX",Boolean.toString(parallax));
  String selected=annotated.apply(new MutableOptionValues(options,nativeOptions));
  return JcppProcessor.glslPreprocessSource(selected,List.of(
   new StringPair("MC_VERSION","12101"),new StringPair("MC_GL_VERSION","410"),
   new StringPair("MC_GLSL_VERSION","410"),new StringPair("IS_IRIS","1")));
 }
 static void parse(String source) {
  var parser=new EnhancedParser();parser.setThrowParseErrors(true);parser.parse(source);
 }
 public static void main(String[] args) throws Exception {
  Path folder=Path.of(args[0]);int passes=0,failures=0;
  for(String row:Files.readAllLines(folder.resolve("cases.list"))) {
   String[] fields=row.split("\\t");Path source=folder.resolve(fields[0]);
   boolean gate=Boolean.parseBoolean(fields[1]),parallax=Boolean.parseBoolean(fields[2]);
   String prepared=prepare(source,gate,parallax);
   boolean regression=fields[3].equals("broken");
   if(regression) {
    try {parse(prepared);throw new AssertionError("broken r2 helper parsed: "+fields[0]);}
    catch(ParseCancellationException e) {
     if(!e.getMessage().contains("missing RPAREN at '{'"))throw new AssertionError("unexpected regression error "+fields[0]+": "+e.getMessage(),e);
     failures++;System.out.println("reproduced "+fields[0]+": "+e.getMessage());
    }
   } else {
    try {parse(prepared);}catch(Throwable e){throw new AssertionError("failed "+row,e);}
    passes++;
   }
  }
  if(passes!=75||failures!=3)throw new AssertionError("unexpected counts "+passes+"/"+failures);
  System.out.println("75 complete affected entrypoint/profile parses passed; 3 broken-r2 water regressions reproduced");
 }
}
'''
        with tempfile.TemporaryDirectory(prefix='shaderlod-procedural-glsl-') as tmp:
            folder=Path(tmp);embedded=[]
            with zipfile.ZipFile(iris) as z:
                for name in ('glsl-transformer-2.0.1.jar','jcpp-1.4.14.jar'):
                    data=z.read('META-INF/jarjar/'+name)
                    if name=='glsl-transformer-2.0.1.jar':
                        self.assertEqual(pin['transformer_sha256'],probe.sha256(data))
                        with zipfile.ZipFile(io.BytesIO(data)) as transformer:
                            self.assertEqual(pin['class_sha256'],probe.sha256(transformer.read(pin['class_path'])))
                    path=folder/name;path.write_bytes(data);embedded.append(path)
            rows=[]
            for index,name in enumerate(paths):
                for arm,files,gate in (('original',self.files,False),('off',patched,False),('on',patched,True)):
                    filename=f'{index}-{arm}.fsh';(folder/filename).write_text(expand(files,name))
                    rows.append('\t'.join((filename,str(gate).lower(),'true','good')))
                if name.endswith('/gbuffers_water.fsh'):
                    rows.append('\t'.join((f'{index}-on.fsh','true','false','good')))
                    filename=f'{index}-broken.fsh';(folder/filename).write_text(expand(regression,name))
                    rows.append('\t'.join((filename,'true','true','broken')))
            (folder/'cases.list').write_text('\n'.join(rows))
            source=folder/'ProceduralGlslProof.java';source.write_text(java)
            run=subprocess.run(['java','-Xmx1G','--class-path',os.pathsep.join(map(str,[iris,*dependencies,*embedded])),str(source),str(folder)],capture_output=True,text=True,timeout=120)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr)
            self.assertIn('75 complete affected entrypoint/profile parses passed; 3 broken-r2 water regressions reproduced',run.stdout)
            print(run.stdout.strip())

    def test_actual_iris_discovers_only_private_option_and_applies_overrides(self):
        # Compare complete recursively included water graphs, including helpers.
        # Iris discovers options before conditional GLSL branches are evaluated.
        def include_graph(files):
            graph={}
            def visit(name):
                if name in graph:return
                self.assertIn(name,files,'Missing literal include '+name)
                graph[name]=files[name]
                for target in re.findall(r'^\s*#include\s+"([^"]+)"',files[name].decode(),re.M):
                    path='shaders/'+target.lstrip('/') if target.startswith('/') else posixpath.normpath(posixpath.join(posixpath.dirname(name),target))
                    visit(path)
            for world in ('world0','world-1','world1'):
                visit('shaders/'+world+'/gbuffers_water.fsh')
            self.assertIn(probe.SETTINGS,graph)
            self.assertIn(probe.PROGRAM,graph)
            return graph
        java=r'''
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
class ProceduralIrisLexer {
    static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static Map<String,OptionAnnotatedSource> graph(String directory) throws Exception {
        Path root=Path.of(directory);
        Map<String,OptionAnnotatedSource> result=new TreeMap<>();
        try(var paths=Files.walk(root)) {
            for(Path p:paths.filter(Files::isRegularFile).toList())
                result.put(root.relativize(p).toString().replace('\\','/'),new OptionAnnotatedSource(Files.readString(p)));
        }
        return result;
    }
    static Set<String> references(Map<String,OptionAnnotatedSource> graph) {
        Set<String> result=new TreeSet<>();
        for(var source:graph.values())result.addAll(source.getBooleanDefineReferences().keySet());
        return result;
    }
    static Set<String> options(Map<String,OptionAnnotatedSource> graph, Set<String> refs, boolean strings) {
        Set<String> result=new TreeSet<>();
        for(var entry:graph.entrySet()) {
            var set=entry.getValue().getOptionSet(AbsolutePackPath.fromAbsolutePath("/"+entry.getKey().substring("shaders/".length())),refs);
            result.addAll(strings?set.getStringOptions().keySet():set.getBooleanOptions().keySet());
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        String name=args[0];
        var original=graph(args[1]);
        var disabled=graph(args[2]);
        var enabled=graph(args[3]);
        var beforeRefs=references(original);
        var expectedRefs=new TreeSet<>(beforeRefs);expectedRefs.add(name);
        var beforeBooleans=options(original,beforeRefs,false);
        var expectedBooleans=new TreeSet<>(beforeBooleans);expectedBooleans.add(name);
        var beforeStrings=options(original,beforeRefs,true);
        String[] guards={"SHADERLOD_PROCEDURAL_FOOTPRINT_INCLUDED","SHADERLOD_PROCEDURAL_WATER_INCLUDED","SHADERLOD_PROCEDURAL_PARALLAX_INCLUDED"};
        int index=0;
        for(var graph:List.of(disabled,enabled)) {
            var refs=references(graph);
            require(refs.equals(expectedRefs),"unintended added/removed graph references: "+refs+" vs "+expectedRefs);
            var booleans=options(graph,refs,false);
            require(booleans.equals(expectedBooleans),"graph boolean options differ beyond private gate: "+booleans+" vs "+expectedBooleans);
            require(options(graph,refs,true).equals(beforeStrings),"upstream graph string options changed");
            for(String guard:guards) {
                require(!refs.contains(guard),"internal helper guard leaked into references: "+guard);
                require(!booleans.contains(guard),"internal helper guard leaked into options: "+guard);
            }
            var settings=graph.get("shaders/settings.glsl");
            boolean expectedDefault=index++==1;
            var sourceOption=settings.getBooleanOptions().values().stream().filter(v->v.getName().equals(name)).findFirst().orElseThrow();
            require(sourceOption.getDefaultValue()==expectedDefault,"source default mismatch");
            var set=settings.getOptionSet(AbsolutePackPath.fromAbsolutePath("/settings.glsl"),refs);
            for(boolean override:List.of(!expectedDefault)) {
                var values=new MutableOptionValues(set,Map.of(name,Boolean.toString(override)));
                String edited=settings.apply(values);
                String expectedLine=(override?"#define ":"//#define ")+name;
                require(edited.lines().anyMatch(expectedLine::equals),"actual Iris override failed: "+override+" expected "+expectedLine+" actual "+edited.lines().filter(l->l.contains(name)).toList());
            }
        }
        // Regression proof: the old helper first-line guard IS detected by Iris.
        int guardIndex=0;
        for(String helper:List.of("procedural_footprint.glsl","procedural_water.glsl","procedural_parallax.glsl")) {
            String path="shaders/include/shaderlod/"+helper;
            String source=Files.readString(Path.of(args[2]).resolve(path));
            require(source.startsWith("#if !defined "),"helper guard source contract failed");
            var old=new OptionAnnotatedSource(source.replaceFirst("#if !defined ","#ifndef "));
            String guard=guards[guardIndex++];
            require(old.getBooleanDefineReferences().containsKey(guard),"regression guard unreferenced");
            require(old.getOptionSet(AbsolutePackPath.fromAbsolutePath("/include/shaderlod/"+helper),old.getBooleanDefineReferences().keySet()).getBooleanOptions().containsKey(guard),"regression guard not recognized as option");
        }
        System.out.println("complete original/modified graphs "+original.size()+"/"+disabled.size()+"; all3 helpers covered; sole public gate/reference; string options equal; no internal guard/stage leaks; both defaults and overrides; oldguard regressions reproduced");
    }
}
'''
        pin=MANIFEST['iris_option_discovery']
        jar=ROOT/pin['jar_path']
        self.assertEqual(pin['jar_sha256'],probe.sha256(jar.read_bytes()))
        with zipfile.ZipFile(jar) as archive:
            self.assertEqual(pin['class_sha256'],probe.sha256(archive.read(pin['class_path'])))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
        deps=[]
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar','it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar'):
            matches=list(cache.glob(pattern))
            self.assertEqual(1,len(matches),'Pinned local Iris parser dependency absent')
            deps.append(matches[0])
        graphs=[include_graph(self.files),include_graph(probe.patch_members(self.files,MANIFEST,'disabled')),include_graph(probe.patch_members(self.files,MANIFEST,'enabled'))]
        helper_names={h['path'] for h in MANIFEST['helpers']}
        self.assertEqual(3,len(helper_names))
        self.assertEqual(helper_names,set(graphs[1])-set(graphs[0]))
        self.assertEqual(set(graphs[1]),set(graphs[2]))
        with tempfile.TemporaryDirectory(prefix='shaderlod-procedural-iris-') as tmp:
            directory=Path(tmp);java_file=directory/'ProceduralIrisLexer.java';java_file.write_text(java)
            graph_dirs=[]
            for index,graph in enumerate(graphs):
                graph_dir=directory/str(index);graph_dirs.append(graph_dir)
                for name,data in graph.items():
                    path=graph_dir/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
            run=subprocess.run(['java','--class-path',os.pathsep.join(map(str,[jar,*deps])),str(java_file),probe.SWITCH,*map(str,graph_dirs)],capture_output=True,text=True,timeout=30)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr)
            self.assertIn('all3 helpers covered; sole public gate/reference; string options equal; no internal guard/stage leaks; both defaults and overrides; oldguard regressions reproduced',run.stdout)
            print(run.stdout.strip())


class NumericalFixtures(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows=decode_green(probe.archive_members(ROOT/probe.BASE)['shaders/image/noise.png'])

    def test_exact_phase_mean_and_retained_coarse_energy(self):
        samples=[(math.sin(2*math.pi*i/4096)*.5+.5)**2 for i in range(4096)]
        self.assertAlmostEqual(.375,sum(samples)/len(samples),places=14)
        for count in range(1,9):
            for dist in (0,32,40,48,1000):
                w=weights(count,(1,0),(0,1),dist)
                self.assertEqual(1,w[0])
                # Full N normalization preserves coarse amplitude/slope at all LODs.
                norm=(1-.5)/(1-.5**count)
                self.assertEqual(norm,w[0]*norm)

    def test_noise_lipschitz_bound_and_unit_scaling(self):
        for p in ((.3,.7),(-.3,-.7),(1.0,1.0),(.00001,.99999)):
            for d in ((.00001,0),(0,.00001),(.00001,-.00001)):
                delta=abs(sample_noise(self.rows,p)-sample_noise(self.rows,(p[0]+d[0],p[1]+d[1])))
                self.assertLessEqual(delta,512*math.sqrt(2)*math.hypot(*d)+1e-10)
        self.assertAlmostEqual(cycles((.1,0),(0,.1),(.7,0),2),2*cycles((.05,0),(0,.05),(.7,0),2))

    def test_smooth_weights_full_near_and_skip_fetches_1_to_8(self):
        for n in range(1,9):
            self.assertEqual([1]*n,weights(n,(1,0),(0,1),32))
            for dist in (32,48):
                a,b=weights(n,(.1,0),(0,.1),dist-1e-5),weights(n,(.1,0),(0,.1),dist+1e-5)
                self.assertLess(max(abs(x-y) for x,y in zip(a,b)),1e-8)
            w=weights(n,(.1,0),(0,.1),48)
            _,fetch=height(self.rows,(7,13),n,w,variation=False)
            self.assertEqual(1,fetch)
            self.assertEqual([1]+[0]*(n-1),w)

    def test_full_weight_original_near_normal_and_finite_worst_conditions(self):
        maximum=0
        for n in (1,3,8):
            for coord in ((7,13),(17.3,-4.7),(513,111),(-13,-99)):
                for flowing,flow in ((False,(0,0)),(True,(1,0)),(True,(0,-1)),(True,(.6,.8))):
                    for strength in (0,.04,.1,.19,.38):
                        for t in (0,17,113):
                            kw=dict(flowing=flowing,flow=flow,t=t)
                            near=normal(self.rows,coord,n,weights(n,(.1,0),(0,.1),32),influence=strength,**kw)
                            original=normal(self.rows,coord,n,[1]*n,influence=strength,**kw)
                            self.assertEqual(original,near)
                            far=normal(self.rows,coord,n,weights(n,(.1,0),(0,.1),48,flowing=flowing,direction=flow if flowing else (math.cos(math.pi/6),math.sin(math.pi/6))),influence=strength,**kw)
                            self.assertTrue(all(math.isfinite(x) for x in far))
                            self.assertAlmostEqual(1,sum(x*x for x in far),places=12)
                            angle=math.degrees(math.acos(max(-1,min(1,sum(x*y for x,y in zip(original,far))))))
                            maximum=max(maximum,angle)
        # This is a measured fixture bound, not a perceptual acceptance threshold.
        print('procedural scalar worst normal angle degrees:',maximum)
        self.assertLess(maximum,23) # Fixture envelope only; not global or perceptual acceptance.

    def test_parallax_reference_and_real_displaced_footprints(self):
        maximum_error,maximum_footprint_ratio,count_changes=0.,0.,0
        for n in (1,3,8):
            for variation in (False,True):
                for coord in ((7,13),(17.3,-4.7),(513,111),(-13,-99),(.00001,.99999)):
                    for direction in ((.2,.1,-1),(.8,-.1,-.5),(.8,.1,-.01)):
                        for flowing,flow in ((False,(0,0)),(True,(0,-1)),(True,(.6,.8))):
                            kw=dict(variation=variation,flowing=flowing,flow=flow)
                            mapped,valid,count=parallax(self.rows,coord,direction,n,**kw)
                            reference,rv,rc=parallax(self.rows,coord,direction,n,reference=True,**kw)
                            self.assertEqual(valid,rv)
                            self.assertEqual(count,rc)
                            if not valid:continue
                            error=math.hypot(*(x-y for x,y in zip(mapped,reference)))
                            maximum_error=max(maximum_error,error)
                            self.assertLess(error,1e-8)
                            # Adjacent invocation's actual mapped coordinate,
                            # including direction changes and adaptive count.
                            q=(coord[0]+.01,coord[1])
                            d=(direction[0]+.0005,direction[1],direction[2])
                            adjacent,av,ac=parallax(self.rows,q,d,n,**kw)
                            if av:
                                footprint=math.hypot(*(x-y for x,y in zip(adjacent,mapped)))
                                self.assertTrue(math.isfinite(footprint))
                                maximum_footprint_ratio=max(maximum_footprint_ratio,footprint/.01)
                                count_changes+=ac!=count
        self.assertGreater(maximum_footprint_ratio,1.1)
        self.assertGreater(count_changes,0)
        print('parallax max reference error / footprint expansion / count switches:',maximum_error,maximum_footprint_ratio,count_changes)

    def test_parallax_expansion_confidence_is_continuous_and_full_fallback(self):
        self.assertEqual(1,1-smooth(2,4,1))
        self.assertEqual(0,1-smooth(2,4,249.736186))
        for edge in (2,4):
            self.assertLess(abs(smooth(2,4,edge-1e-5)-smooth(2,4,edge+1e-5)),1e-8)

    def test_parallax_invalid_guards_and_parameter_extremes(self):
        for direction in ((0,0,0),(1,0,1),(1,0,-1e-8)):
            self.assertFalse(parallax(self.rows,(7,13),direction,3)[1])
        self.assertFalse(parallax(self.rows,(7,13),(.2,.1,-1),3,persistence=1)[1])
        for n in (1,3,8):
            for persistence in (0,.5,.995):
                for frequency in (0,.7,1.4):
                    q,valid,count=parallax(self.rows,(7,13),(.2,.1,-1),n,persistence=persistence,frequency=frequency)
                    self.assertTrue(valid)
                    self.assertLessEqual(count,6)
                    self.assertTrue(all(math.isfinite(x) for x in q))

if __name__ == '__main__':unittest.main()
