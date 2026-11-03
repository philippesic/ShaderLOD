"""Pinned source, actual Iris parser and independent integration math; no GPU result."""
import copy
import json
import io
import math
import os
from pathlib import Path
import posixpath
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import build_photon_volumetric_lod as probe
MANIFEST = json.loads((ROOT / probe.MANIFEST).read_text())
HELPERS = {name: (ROOT / source).read_text() for name, source in probe.ADDITIONS}
HELPER = HELPERS['shaders/include/shaderlod/volume_budget.glsl']
ADAPTER = HELPERS['shaders/include/shaderlod/photon_volume_adapter.glsl']


def select(source, private, stage):
    text = source.decode().replace(MANIFEST['option_declaration'], '', 1).replace(
        MANIFEST['option_declaration'].replace('//#define', '#define'), '', 1)
    out, active = [], []
    for line in text.splitlines(keepends=True):
        if line == f'#ifdef {probe.SWITCH}\n':
            active.append(private)
        elif line == MANIFEST['stage_guard'] and active:
            active.append(stage)
        elif line == '#else\n' and active:
            active[-1] = not active[-1]
        elif line == '#endif // SHADERLOD_VOLUME_STAGE\n':
            assert len(active) == 2
            active.pop()
        elif line == f'#endif // {probe.SWITCH}\n':
            assert len(active) == 1
            active.pop()
        elif not active or all(active):
            out.append(line)
    assert not active
    return ''.join(out).encode()


def smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t*t*(3-2*t)


def target(original=40, minimum=8, enabled=True, entry=1000., interval=100., footprint=1.,
           feature=.3, overhead=0., bands=(32.,96.,16.), ratios=(.85,.75),
           core=2, effects=2, mode=2, capability_schema=1, available=1):
    # Independent policy oracle, rather than executing/translating the GLSL.
    values = (entry, interval, footprint, feature, overhead, *bands, *ratios)
    n, f, w = bands
    medium, far = ratios
    valid = (all(math.isfinite(v) for v in values) and original >= minimum >= 1 and
             original <= 65536 and 0 <= entry <= 16777216 and 0 < interval <= 16777216 and
             footprint > 0 and feature > 0 and 0 <= overhead <= 1 and
             0 < n < f <= 4096 and 0 <= w <= min(512, 2*n, f-n) and
             (w == 0 or w >= 1/1024) and 0 <= far <= medium <= 1)
    if (not enabled or not valid or core != 2 or effects not in (1,2) or
        mode not in (0,2) or capability_schema != 1 or available != 1):
        return float(original)
    if mode == 0: return float(original) # Explicit shared/default unity INHERIT.
    blend = lambda boundary: float(entry >= boundary) if w == 0 else smooth(boundary-w/2, boundary+w/2, entry)
    intermediate = (1-blend(n)) + medium*blend(n)
    ratio = intermediate*(1-blend(f)) + far*blend(f)
    eligible = smooth(feature, 2*feature, footprint)*(1-overhead)
    return max(minimum, min(original, original*((1-eligible)+eligible*ratio)))


def cells(length, work, jitter):
    width = length/work
    return [(i*width, min(width, max(0., length-i*width)),
             i*width + min(width, max(0., length-i*width))*jitter)
            for i in range(math.ceil(work))]


def f32(x):
    return struct.unpack('f', struct.pack('f', x))[0]


class VolumeSourceFixture:
    """Extend the maintained restricted scalar fixture with owned volume statements.

    This executes source order in binary32, not GLSL driver arithmetic/contraction.
    It rejects unsupported syntax instead of approximating a shader interpreter.
    """
    def __init__(self):
        from test_shared_lod_policy import SharedSourceFixture
        from test_effect_policy_glsl import F32
        self.engine = SharedSourceFixture()
        self.engine.environment['mix'] = lambda a,b,t: F32(a*(F32(1)-t)+b*t)
        self.engine.environment['smoothstep'] = lambda a,b,x: F32(smooth(a,b,x))
        clean = re.sub(r'//[^\n]*', '', HELPER)
        clean = re.sub(r'\b(\d+)u\b', r'\1', clean)
        # A bool assignment is represented as an opaque scalar fixture value.
        clean = clean.replace('bool quality_valid =', 'ShaderlodVolumeBoolean quality_valid =')
        for match in re.finditer(r'(bool|float) (shaderlod_volume_\w+)\((.*?)\)\s*\{', clean, re.S):
            kind,name,params = match.groups()
            end = self.engine.close(clean, match.end()-1, '{','}')
            self.engine.functions[name] = (kind,[p.split() for p in params.split(',')],clean[match.end():end])
            self.engine.environment[name] = lambda *args,name=name: self.engine.call(name,*args)
        if len(self.engine.functions) != 8:
            raise AssertionError('Expected shared four plus volume four functions')

    def target(self, policy, original=40, minimum=8, entry=1000., interval=100.,
               footprint=1., feature=.3, overhead=0., capability=True):
        return self.engine.call('shaderlod_volume_work_target', policy, capability,
                                original, minimum, entry, interval, footprint, feature, overhead)


class VolumeMathTests(unittest.TestCase):
    def test_shared_policy_source_order_continuous_refinement_and_exact_capability_fallback(self):
        from test_shared_lod_policy import policy, vec
        fixture = VolumeSourceFixture()
        for count in (8,19,40,63):
            for distance in (0.,28.,32.,36.,92.,96.,100.,1000.):
                for ratios in ((1.,1.),(.85,.75),(.43,.17),(0.,0.)):
                    for overhead in (0.,.25,.75,1.):
                        p = policy(bands_blocks=vec(32,96,8),retained_ratios=vec(*ratios))
                        actual = fixture.target(p,original=count,entry=distance,overhead=overhead)
                        expected = target(original=count,entry=distance,overhead=overhead,
                                          bands=(32,96,8),ratios=ratios)
                        self.assertAlmostEqual(expected,actual,delta=2e-5)
        # Blend the unrounded/unfloored request. ratio0 with half quality weight
        # requests20 cells; using floor-clamped8 first would incorrectly request24.
        p = policy(retained_ratios=vec(0,0))
        self.assertAlmostEqual(20.,fixture.target(p,footprint=.45),delta=2e-5)
        fractional = fixture.target(policy(retained_ratios=vec(.7,.63)))
        self.assertAlmostEqual(25.2,fractional,delta=2e-5)
        self.assertNotEqual(math.ceil(fractional),fractional)
        for change in ({'core_version':0},{'core_version':3},{'effects_version':0},
                       {'effects_version':3},{'mode':1},{'mode':3},{'global_enabled':False},
                       {'mode':0,'inherited_policy_resolved':False}):
            self.assertEqual(40.,fixture.target(policy(**change)))
        self.assertEqual(40.,fixture.target(policy(),capability=False))
        self.assertEqual(40.,fixture.target(policy(),interval=math.nan))
        self.assertEqual(40.,fixture.target(policy(),overhead=1.))
        self.assertEqual(40.,fixture.target(policy(mode=0,inherited_policy_resolved=True,
                                                 retained_ratios=vec(1,1))))
        for schema,available in ((0,0),(0,1),(1,0),(1,2),(2,1),(-1,1)):
            self.assertEqual(40.,target(capability_schema=schema,available=available))
        self.assertEqual(40.,target(mode=0))
        self.assertEqual(30.,target(mode=2))
        self.assertNotIn('shaderlod_volume_blend',HELPER)
        self.assertNotIn('shaderlod_volume_bands_valid',HELPER)
        self.assertIn('work.continuous_requested_work',HELPER)
        self.assertNotIn('work.realized_work',HELPER)

    def test_interval_endpoints_weights_and_samples_for_partial_integer_and_floor(self):
        for length in (.001, 1., 719., 20000.):
            for work in (1., 8., 8.001, 15.999, 16., 16.001, 29.999, 40.):
                for jitter in (0., .17, .999, 1.):
                    parts = cells(length, work, jitter)
                    self.assertEqual(math.ceil(work), len(parts))
                    self.assertAlmostEqual(length, sum(c[1] for c in parts), places=8)
                    self.assertEqual(0., parts[0][0])
                    self.assertAlmostEqual(length, parts[-1][0]+parts[-1][1], places=8)
                    for start, width, sample in parts:
                        self.assertGreater(width, 0.)
                        self.assertGreaterEqual(sample, start)
                        self.assertLessEqual(sample, start+width)
                        self.assertLessEqual(sample, length+1e-10)

    def test_beer_lambert_constant_medium_transmittance_and_scattering_match_across_counts(self):
        for sigma, density, length in ((.02,.35,800.), (.001,1.,300.), (.15,.01,1200.)):
            expected = math.exp(-sigma*density*length)
            for work in (8.,8.25,12.,18.999,30.,39.999,40.):
                trans, scatter = 1., 0.
                for _, width, _ in cells(length, work, .319):
                    t = math.exp(-sigma*density*width)
                    scatter += trans*(1-t)*.65
                    trans *= t
                self.assertAlmostEqual(expected, trans, places=13)
                self.assertAlmostEqual((1-expected)*.65, scatter, places=13)
        # These conditions exclude variable-density/shadow/noise and early-exit changes.
        self.assertIn('cell_length;', ADAPTER)
        self.assertNotIn('density *= work_target', ADAPTER)

    def test_float32_interval_error_is_bounded_and_no_unchecked_target_cast(self):
        for length in (8192.,19000.,20000.):
            for work in (8.001, 16., 16.000001, 31.75, 39.999999):
                length, work = f32(length), f32(work)
                width = f32(length/work)
                weights = [max(0.,min(width,f32(length-f32(f32(i)*width)))) for i in range(math.ceil(work))]
                self.assertLess(abs(sum(weights)-length)/length, 2e-6)
        self.assertIn('original > 65536u', HELPER)
        self.assertIn('primary_steps = uint(ceil(work_target));', ADAPTER)

    def test_full_near_overhead_invalid_off_floor_and_ratio_order(self):
        self.assertEqual(40., target(entry=20.))
        self.assertEqual(40., target(overhead=1.))
        self.assertEqual(40., target(footprint=.3))
        self.assertEqual(40., target(enabled=False))
        self.assertEqual(4., target(4))
        self.assertEqual(8., target(40, ratios=(0.,0.)))
        self.assertEqual(40., target(ratios=(1.,1.)))
        self.assertEqual(40., target(ratios=(.2,.3)))
        for key in ('entry','interval','footprint','feature','overhead'):
            for bad in (math.nan, math.inf, -math.inf, -1.):
                self.assertEqual(40., target(**{key:bad}))
        for bands in ((32,32,0), (32,96,-1), (32,96,128), (32,96,1e-5), (32,5000,0)):
            self.assertEqual(40.,target(bands=bands))

    def test_transitions_monotonic_continuous_and_count_reduction_real(self):
        previous = 40.
        for i in range(2001):
            value = target(entry=i/10)
            self.assertLessEqual(value,previous+1e-10)
            self.assertGreaterEqual(value,30.)
            previous = value
        self.assertEqual(30,target(40))
        self.assertEqual(30,math.ceil(target(40)))
        for edge in (24.,40.,88.,104.):
            self.assertLess(abs(target(entry=edge-1e-5)-target(entry=edge+1e-5)), 1e-4)
        # Contribution of the last cell vanishes at integer count transitions.
        for work in (8., 16., 30.):
            self.assertLess(cells(100.,work+1e-7,.5)[-1][1],2e-6)

    def test_projected_footprint_lower_bound_includes_off_axis_finite_pixels(self):
        # Exact pinhole half-pixel perturbations at center and wide off-axis rays.
        # This bounds every screen-plane orientation, not just x/y derivatives.
        def norm(v):
            length=math.sqrt(sum(x*x for x in v));return tuple(x/length for x in v)
        for qx,qy in ((0.,0.),(.5,.7),(1.5,0.),(0.,1.5),(2.,2.)):
            q=(qx,qy,-1.);direction=norm(q);forward=-direction[2]
            for focal_x,focal_y in ((1600.,1600.),(1800.,1100.),(100.,90.)):
                half_pixel=1/max(focal_x,focal_y)
                lower=half_pixel*forward*forward/(1+half_pixel*forward)
                for i in range(360):
                    angle=i*math.pi/180
                    perturbed=norm((qx+math.cos(angle)/focal_x,qy+math.sin(angle)/focal_y,-1.))
                    cross=(direction[1]*perturbed[2]-direction[2]*perturbed[1],
                           direction[2]*perturbed[0]-direction[0]*perturbed[2],
                           direction[0]*perturbed[1]-direction[1]*perturbed[0])
                    actual=math.sqrt(sum(c*c for c in cross))
                    self.assertGreaterEqual(actual+1e-15,lower)
        self.assertIn('half_pixel * forward * forward',ADAPTER)

    def test_activation_versions_unknown_mode_and_inside_overhead_guards_explicit(self):
        self.assertIn('shaderlodCapabilitiesVersion == 1 &&',ADAPTER)
        self.assertIn('shaderlodVolumeIntegrationAvailable == 1',ADAPTER)
        self.assertIn('ShaderlodWorkPolicy(shaderlodBridgeVersion',ADAPTER)
        self.assertIn('shaderlodEffectsVersion, 1, shaderlodEnabled == 1, shaderlodVolumetricsMode',ADAPTER)
        self.assertIn('preflight.reason != SHADERLOD_WORK_POLICY',ADAPTER)
        self.assertIn('vec2 retained = vec2(1.0)',ADAPTER)
        self.assertIn('radius >= clouds_cumulus_radius - 1.0',ADAPTER)
        self.assertIn('smoothstep(0.35, 0.65, abs(direction.y))',ADAPTER)
        self.assertIn('shaderlod_original_draw_cumulus_clouds',ADAPTER)


class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = probe.archive_members(ROOT/probe.BASE)
        if probe.sha256((ROOT/probe.BASE).read_bytes()) != probe.BASE_SHA256:
            raise AssertionError('Original archive mutated')

    def test_disabled_other_stage_and_transmittance_lut_restore_literal_upstream(self):
        for arm in probe.ARMS:
            output = probe.patch_members(self.files, MANIFEST, arm)
            for private,stage in ((False,False),(False,True),(True,False)):
                for name in probe.MEMBERS:
                    self.assertEqual(self.files[name], select(output[name],private,stage))
            self.assertEqual(self.files['LICENSE'],output['LICENSE'])
            for name in self.files.keys()-set(probe.MEMBERS):
                self.assertEqual(self.files[name],output[name])
            self.assertEqual(len(self.files)+(0 if arm=='original' else len(probe.ADDITIONS)),len(output))

    def test_deterministic_archive_exact_shared_packaging_and_reference_pins(self):
        with tempfile.TemporaryDirectory(prefix='volume-assembly-fixture-',dir=ROOT/'build') as tmp:
            for arm in probe.ARMS:
                path=Path(tmp)/(arm+'.zip');metadata=probe.build(path,arm=arm)
                first=path.read_bytes();self.assertEqual(metadata,probe.build(path,arm=arm))
                self.assertEqual(first,path.read_bytes())
                files=probe.archive_members(path)
                self.assertEqual(metadata,json.loads(files[probe.METADATA]))
                self.assertEqual(self.files['LICENSE'],files['LICENSE'])
                expected=[] if arm=='original' else [e[0] for e in probe.ADDITIONS]
                self.assertEqual(sorted(expected),metadata['added_files'])
                self.assertEqual(arm=='enabled',metadata['option_default'])
                self.assertEqual(8,metadata['retained_minimum'])
                self.assertEqual(1,metadata['capability_schema'])
                pins=metadata['reference_member_sha256']
                self.assertEqual(set() if arm=='original' else set(probe.REFERENCE_MEMBERS),set(pins))
                for member,digest in pins.items():self.assertEqual(digest,probe.sha256(files[member]))
                self.assertEqual({} if arm=='original' else probe.REQUIRED_OPTIONS,
                                 metadata['required_effective_options'])

    def test_primary_work_selected_before_march_no_density_strength_or_secondary_extent_change(self):
        source=self.files[probe.CUMULUS].decode()
        # Exact secondary stepping retained except names and explicit density lookup.
        old=source[source.index('float clouds_cumulus_optical_depth'):source.index('vec2 clouds_cumulus_scattering')]
        renamed=old.replace('clouds_cumulus_optical_depth','shaderlod_volume_optical_depth').replace('clouds_cumulus_density','shaderlod_volume_density')
        self.assertIn(renamed,ADAPTER)
        self.assertIn('if (transmittance < min_transmittance) {\n            break;',ADAPTER)
        self.assertIn('float cell_length = shaderlod_volume_cell_length',ADAPTER)
        self.assertIn('distance_weight_sum += density * cell_length;',ADAPTER)
        self.assertLess(ADAPTER.index('if (target >= float(full))'),ADAPTER.rindex('return shaderlod_volume_draw_cumulus'))
        for forbidden in ('dFdx','dFdy','fwidth','textureGrad','frameCounter /','density *= work_target'):
            self.assertNotIn(forbidden,ADAPTER)
        # Active density+coverage functions use only explicit LOD; original fallback remains literal.
        before_draw=ADAPTER[:ADAPTER.index('CloudsResult shaderlod_volume_draw_cumulus')]
        self.assertNotIn('texture(',before_draw)
        self.assertEqual(5,before_draw.count('textureLod('))
        self.assertLess(ADAPTER.index('if (!capability_valid'),ADAPTER.index('textureSize('))
        self.assertLess(ADAPTER.index('preflight.reason'),ADAPTER.index('intersect_spherical_shell(viewer'))
        self.assertIn('length(camera_relative_entry_blocks)',ADAPTER)
        self.assertIn('direction * (dists.x / CLOUDS_SCALE)',ADAPTER)
        self.assertIn('full, 8u,',ADAPTER)
        # Retain the upstream marcher body byte-for-byte across this connection:
        # only transport/eligibility wrapper and shared decision helper changed.
        body=ADAPTER[ADAPTER.index('float shaderlod_volume_local_coverage'):ADAPTER.index('CloudsResult draw_cumulus_clouds(vec3 viewer')]
        self.assertEqual(MANIFEST['unchanged_march_sha256'],probe.sha256(body.encode()))

    def test_mutated_source_manifest_and_helper_fail_closed(self):
        for name in (*probe.MEMBERS,'LICENSE'):
            with self.assertRaises(ValueError):
                probe.patch_members(self.files|{name:self.files[name]+b'\n'},MANIFEST,'disabled')
        for change in (lambda m:m.update(base_sha256='bad'),lambda m:m.update(effect_versions=[1,3]),lambda m:m.update(capability_schema=2),
                       lambda m:m.update(inherit_retained_ratios=[.85,.75]),
                       lambda m:m.update(required_effective_options={probe.SWITCH:['true']}),
                       lambda m:m.update(reference_members=[]),
                       lambda m:m['members'][0].update(patched_sha256='bad'),
                       lambda m:m['additions'][0].update(sha256='bad'),
                       lambda m:m['additions'][0].update(source_path='../other'),
                       lambda m:m['members'][0]['replacements'][0].update(before='')):
            manifest=copy.deepcopy(MANIFEST);change(manifest)
            with self.assertRaises(ValueError):probe.patch_members(self.files,manifest,'disabled')
        with self.assertRaises(ValueError):probe.build(output=ROOT/'must-not-write.zip')
        with self.assertRaises(ValueError):probe.patch_members(self.files,MANIFEST,'other')

    def test_actual_iris_complete_graph_grammar_off_and_stage_isolation(self):
        """Real Jcpp/EnhancedParser syntax and token parity; no GL driver claims."""
        pin=MANIFEST['glsl_parser_regression']
        iris=ROOT/MANIFEST['iris_option_discovery']['jar_path']
        self.assertEqual(MANIFEST['iris_option_discovery']['jar_sha256'],probe.sha256(iris.read_bytes()))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1';dependencies=[]
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar',
                        'it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar',
                        'org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar',
                        'org.antlr/antlr4-runtime/4.13.1/*/antlr4-runtime-4.13.1.jar'):
            matches=list(cache.glob(pattern));self.assertEqual(1,len(matches),pattern);dependencies.append(matches[0])
        self.assertEqual(pin['antlr_sha256'],probe.sha256(dependencies[-1].read_bytes()))
        def expand(files,name,stack=(),seen=None):
            self.assertNotIn(name,stack,'Cyclic literal include')
            self.assertIn(name,files,'Missing literal include')
            if seen is not None:seen.add(name)
            def include(match):
                target=match.group(1)
                path='shaders/'+target.lstrip('/') if target.startswith('/') else posixpath.normpath(posixpath.join(posixpath.dirname(name),target))
                return expand(files,path,stack+(name,),seen)
            return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*',include,files[name].decode(),flags=re.M)
        originals={}
        for name in sorted(self.files):
            if '/world' in name and name.endswith(('.fsh','.vsh','.gsh','.csh')):
                seen=set();source=expand(self.files,name,seen=seen)
                if probe.CUMULUS in seen:originals[name]=source
        self.assertEqual(43,len(originals))
        patched=probe.patch_members(self.files,MANIFEST,'disabled')
        helper='shaders/include/shaderlod/volume_budget.glsl'
        self.assertEqual(1,patched[helper].count(b'int(minimum));'))
        broken=patched|{helper:patched[helper].replace(b'int(minimum));',b'int(minimum);')}
        java=r'''
import java.nio.file.*;import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.helpers.StringPair;
import io.github.douira.glsl_transformer.parser.EnhancedParser;
import org.antlr.v4.runtime.misc.ParseCancellationException;
class VolumeGlslProof {
 static String prepare(Path file,boolean gate,boolean lut)throws Exception{
  var annotated=new OptionAnnotatedSource(Files.readString(file));
  var options=annotated.getOptionSet(AbsolutePackPath.fromAbsolutePath("/source.fsh"),annotated.getBooleanDefineReferences().keySet());
  if(Files.readString(file).contains("#define WORLD_OVERWORLD\n#define PROGRAM_DEFERRED1")&&!options.getBooleanOptions().keySet().containsAll(Set.of("CLOUDS_CUMULUS","BLOCKY_CLOUDS")))throw new AssertionError("required effective public flags absent in "+file+": "+options.getBooleanOptions().keySet());
  if(file.toString().contains("candidate")&&!options.getBooleanOptions().containsKey("SHADERLOD_VOLUMETRIC_INTEGRATION_LOD"))throw new AssertionError("private flag absent");
  var selected=new HashMap<String,String>();
  for(String name:List.of("BORDER_FOG","CLOUD_SHADOWS","SHADOW_COLOR","SHADOW_VPS","SH_SKYLIGHT"))selected.put(name,"false");
  selected.put("SHADERLOD_VOLUMETRIC_INTEGRATION_LOD",Boolean.toString(gate));
  selected.put("CLOUDS_CUMULUS","true");selected.put("BLOCKY_CLOUDS","false");
  String source=annotated.apply(new MutableOptionValues(options,selected));
  var defines=new ArrayList<StringPair>(List.of(new StringPair("MC_VERSION","12101"),
   new StringPair("MC_GL_VERSION","410"),new StringPair("MC_GLSL_VERSION","410"),new StringPair("IS_IRIS","1")));
  if(lut)defines.add(new StringPair("ATMOSPHERE_TRANSMITTANCE_LUT","1"));
  return JcppProcessor.glslPreprocessSource(source,defines);
 }
 static void parse(String source){var p=new EnhancedParser();p.setThrowParseErrors(true);p.parse(source);}
 static String tokens(String source){return source.replaceAll("(?s)/\\*.*?\\*/|//[^\\n]*","").replaceAll("\\s+","");}
 public static void main(String[] args)throws Exception{
  Path folder=Path.of(args[0]);int count=0,off=0,isolation=0,active=0;
  for(String row:Files.readAllLines(folder.resolve("graphs.list"))){
   String[] fields=row.split("\\t");boolean lut=Boolean.parseBoolean(fields[2]);
   String original=prepare(folder.resolve(fields[0]),false,lut);
   String disabled=prepare(folder.resolve(fields[1]),false,lut);
   String enabled=prepare(folder.resolve(fields[1]),true,lut);
   for(String source:List.of(original,disabled,enabled)){try{parse(source);}catch(Throwable e){throw new AssertionError("full graph failed "+row,e);}count++;}
   if(!tokens(original).equals(tokens(disabled)))throw new AssertionError("off parity "+row);off++;
   if(!enabled.contains("struct ShaderlodWorkPolicy")){
    if(!tokens(original).equals(tokens(enabled)))throw new AssertionError("stage/LUT isolation "+row);isolation++;
   }else{
    if(!enabled.contains("uniform int shaderlodCapabilitiesVersion")||!enabled.contains("work.continuous_requested_work"))throw new AssertionError("shared consumer absent");active++;
   }
  }
  try{parse(prepare(folder.resolve("broken.fsh"),true,false));throw new AssertionError("broken shared call accepted");}
  catch(ParseCancellationException expected){}
  if(count!=132||off!=44||active!=1||isolation!=43)throw new AssertionError("unexpected counts "+count+"/"+off+"/"+active+"/"+isolation);
  System.out.println("132 complete Iris grammar parses; 44 off parity; 43 stage/LUT isolation; 1 active shared consumer; broken-call regression rejected");
 }
}'''
        with tempfile.TemporaryDirectory(prefix='shaderlod-volume-grammar-') as tmp:
            folder=Path(tmp);embedded=[]
            with zipfile.ZipFile(iris) as archive:
                for name in ('glsl-transformer-2.0.1.jar','jcpp-1.4.14.jar'):
                    data=archive.read('META-INF/jarjar/'+name)
                    if name=='glsl-transformer-2.0.1.jar':
                        self.assertEqual(pin['transformer_sha256'],probe.sha256(data))
                        with zipfile.ZipFile(io.BytesIO(data)) as parser:
                            self.assertEqual(pin['class_sha256'],probe.sha256(parser.read(pin['class_path'])))
                    path=folder/name;path.write_bytes(data);embedded.append(path)
            rows=[]
            for i,(name,source) in enumerate(originals.items()):
                original=f'{i}-original.fsh';candidate=f'{i}-candidate.fsh'
                (folder/original).write_text(source);(folder/candidate).write_text(expand(patched,name))
                rows.append('\t'.join((original,candidate,'false')))
                if name=='shaders/world0/deferred1.fsh':rows.append('\t'.join((original,candidate,'true')))
            (folder/'broken.fsh').write_text(expand(broken,'shaders/world0/deferred1.fsh'))
            (folder/'graphs.list').write_text('\n'.join(rows))
            harness=folder/'VolumeGlslProof.java';harness.write_text(java)
            run=subprocess.run(['java','-Xmx1G','--class-path',os.pathsep.join(map(str,[iris,*dependencies,*embedded])),str(harness),str(folder)],capture_output=True,text=True,timeout=120)
            self.assertEqual(0,run.returncode,run.stdout+run.stderr)
            self.assertIn('132 complete Iris grammar parses',run.stdout)
            print(run.stdout.strip())

    def test_actual_pinned_iris_parser_sole_new_boolean_option_and_both_overrides(self):
        evidence=MANIFEST['iris_option_discovery'];iris=ROOT/evidence['jar_path']
        if not iris.exists() or not shutil.which('java'):
            self.skipTest('Pinned actual Iris/JDK unavailable')
        self.assertEqual(evidence['jar_sha256'],probe.sha256(iris.read_bytes()))
        with zipfile.ZipFile(iris) as archive:
            self.assertEqual(evidence['class_sha256'],probe.sha256(archive.read(evidence['class_path'])))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
        dependencies=[]
        for relative,name in (('com.google.guava/guava/32.1.2-jre','guava-32.1.2-jre.jar'),
                              ('it.unimi.dsi/fastutil/8.5.12','fastutil-8.5.12.jar')):
            matches=sorted((cache/relative).glob('*/'+name))
            if len(matches)!=1:self.skipTest('Existing pure lexer dependency unavailable')
            dependencies.append(matches[0])
        disabled=probe.patch_members(self.files,MANIFEST,'disabled')
        enabled=probe.patch_members(self.files,MANIFEST,'enabled')
        with tempfile.TemporaryDirectory(prefix='shaderlod-volume-lexer-') as temporary:
            directory=Path(temporary);java_source=directory/'DensityIrisLexer.java'
            java_source.write_text(JAVA_HARNESS)
            inputs=[directory/name for name in ('active.glsl','disabled.glsl','enabled.glsl','original.glsl','original-settings.glsl','entry.fsh')]
            active=disabled[probe.CUMULUS]+b'\n'+b'\n'.join(value.encode() for value in HELPERS.values())
            payloads=(active,disabled[probe.SETTINGS],enabled[probe.SETTINGS],self.files[probe.CUMULUS],self.files[probe.SETTINGS],self.files['shaders/world0/deferred1.fsh'])
            for path,data in zip(inputs,payloads):path.write_bytes(data)
            command=['java','--class-path',os.pathsep.join(map(str,(iris,*dependencies))),str(java_source),probe.SWITCH,*map(str,inputs)]
            result=subprocess.run(command,capture_output=True,text=True,timeout=30)
            self.assertEqual(0,result.returncode,result.stdout+result.stderr)
            self.assertIn('sole new option; no stage leak',result.stdout)



JAVA_HARNESS = r'''
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
class DensityIrisLexer {
    static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static Set<String> references(String... sources) {
        Set<String> result = new HashSet<>();
        for (String source : sources)
            result.addAll(new OptionAnnotatedSource(source).getBooleanDefineReferences().keySet());
        return result;
    }
    static Set<String> options(Set<String> references, String... sources) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < sources.length; i++)
            result.addAll(new OptionAnnotatedSource(sources[i]).getOptionSet(
                AbsolutePackPath.fromAbsolutePath("/source" + i + ".glsl"), references).getBooleanOptions().keySet());
        return result;
    }
    public static void main(String[] args) throws Exception {
        String name = args[0];
        String declaration = "//#define " + name + "\n";
        var legacy = new OptionAnnotatedSource(declaration +
            "#if defined " + name + " && defined PROGRAM_DEFERRED1\n#endif\n");
        require(!legacy.getBooleanDefineReferences().containsKey(name), "combined guard unexpectedly referenced");
        require(!legacy.getOptionSet(AbsolutePackPath.fromAbsolutePath("/legacy.glsl"),
            legacy.getBooleanDefineReferences().keySet()).getBooleanOptions().containsKey(name),
            "legacy option unexpectedly retained");
        String density = Files.readString(Path.of(args[1]));
        var actual = new OptionAnnotatedSource(density);
        require(actual.getBooleanDefineReferences().containsKey(name), "actual nested guard unreferenced");
        String originalDensity = Files.readString(Path.of(args[4]));
        String originalSettings = Files.readString(Path.of(args[5]));
        String stageEntry = Files.readString(Path.of(args[6]));
        String disabledSettings = Files.readString(Path.of(args[2]));
        var baselineReferences = references(originalDensity, originalSettings, stageEntry);
        var actualReferences = references(density, disabledSettings, stageEntry);
        var addedReferences = new HashSet<>(actualReferences);
        addedReferences.removeAll(baselineReferences);
        require(addedReferences.equals(Set.of(name)), "unintended reference added: " + addedReferences);
        require(!actualReferences.contains("PROGRAM_DEFERRED1"), "entry stage became option reference");
        var baselineOptions = options(baselineReferences, originalDensity, originalSettings, stageEntry);
        var actualOptions = options(actualReferences, density, disabledSettings, stageEntry);
        var expectedOptions = new HashSet<>(baselineOptions);
        expectedOptions.add(name);
        require(actualOptions.equals(expectedOptions), "option set differs beyond private gate");
        require(!actualOptions.contains("PROGRAM_DEFERRED1"), "entry stage leaked into options");
        for (int i = 2; i <= 3; i++) {
            boolean expectedDefault = i == 3;
            var settings = new OptionAnnotatedSource(Files.readString(Path.of(args[i])));
            var option = settings.getBooleanOptions().values().stream()
                .filter(value -> value.getName().equals(name)).findFirst().orElseThrow();
            require(option.getDefaultValue() == expectedDefault, "source default mismatch");
            var set = settings.getOptionSet(AbsolutePackPath.fromAbsolutePath("/settings.glsl"),
                actual.getBooleanDefineReferences().keySet());
            require(set.getBooleanOptions().containsKey(name), "actual option not retained");
            boolean override = !expectedDefault;
            var values = new MutableOptionValues(set, Map.of(name, Boolean.toString(override)));
            String edited = settings.apply(values);
            String expectedLine = (override ? "#define " : "//#define ") + name;
            require(edited.lines().anyMatch(expectedLine::equals), "actual Iris override did not edit declaration");
        }
        System.out.println("combined absent; actual nested discovered; default false/true; overrides true/false applied; sole new option; no stage leak");
    }
}
'''

if __name__ == "__main__":
    unittest.main()
