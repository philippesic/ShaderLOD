"""Pinned source/parser and projected-envelope fixtures; no GPU acceptance."""
import copy
import json
import math
import os
import shutil
import subprocess
import struct
from pathlib import Path
import random
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import build_photon_reflection_lod as probe
M = json.loads((ROOT / probe.MANIFEST).read_text())
HELPER = ROOT / M['added_members'][0]['source_path']


def select_private(source, enabled):
    output, selected = [], []
    for line in source.decode().splitlines(keepends=True):
        if line == f'#ifdef {probe.SWITCH}\n': selected.append(enabled)
        elif line == '#else\n' and selected: selected[-1] = not selected[-1]
        elif line == f'#endif // {probe.SWITCH}\n': selected.pop()
        elif not selected or all(selected): output.append(line)
    assert not selected
    return ''.join(output).encode()


def refine(pos, step, count, depth_at, tolerance, cache):
    """Scalar mirror of exact texel reuse and bounded refinement termination."""
    pos, step = list(pos), list(step)
    key, cached, fetches, updates, skipped = None, None, 0, 0, 0
    final = None
    for i in range(count):
        step = [v * .5 for v in step]
        texel = tuple(math.trunc(v) for v in pos[:2])
        valid = all(0 <= x < 128 for x in texel)
        if not(cache and valid and key == texel):
            cached = depth_at(texel)
            fetches += 1
        key = texel if valid else None
        depth = cached
        hit = depth < pos[2] and abs(.02 - (pos[2] - depth)) < .02
        pos = [p + (-s if hit else s) for p, s in zip(pos, step)]
        final = depth
        updates += 1
        remaining = count - i - 1
        if tolerance > 0 and 2 <= remaining <= 7 and key is not None:
            span = [abs(s)*(1-2**(-remaining)) for s in step]
            lower = [pos[j]-span[j] for j in range(2)]
            upper = [pos[j]+span[j] for j in range(2)]
            margin = [max(1e-4, abs(x)*2e-6) for x in upper]
            same = all(lower[j]-margin[j] >= key[j] and upper[j]+margin[j] < key[j]+1 for j in range(2))
            z_margin=max(2e-6,.02*1e-4)
            lo,hi=pos[2]-span[2],pos[2]+span[2]
            uniform=(lo>depth+z_margin and hi<depth+.04-z_margin) or hi<depth-z_margin or lo>depth+.04+z_margin
            if same and uniform and max(span[:2]) <= tolerance and span[2] <= .02*.0625:
                extent=1-2**(-remaining)
                # Convert pixel coordinates to original shader normalized UV.
                p=(pos[0]/128,pos[1]/128,pos[2])
                st=(step[0]/128,step[1]/128,step[2])
                diagonal=(-1.,-1.,0.,-9.999)
                offset=(0.,0.,-1.,10.)
                if view_guard(p,st,extent,diagonal,offset):
                    skipped = remaining
                    break
    return pos, final, fetches, updates, skipped


def f32(value):
    try: return struct.unpack('f', struct.pack('f', value))[0]
    except OverflowError: return math.copysign(math.inf, value)


def view_guard(pos, step, extent, diagonal, offset):
    """Float32 expression mirror of new division-free conservative bound."""
    p, st, d, b = [tuple(map(f32,x)) for x in (pos,step,diagonal,offset)]
    if not all(math.isfinite(x) for x in (*p,*st,*d,*b)): return False
    if max(map(abs,(*d,*b)))>1e6 or max(map(abs,(*p,*st)))>4: return False
    extent=f32(extent)
    def mul(a,b): return f32(f32(a)*f32(b))
    def add(a,b): return f32(f32(a)+f32(b))
    def sub(a,b): return f32(f32(a)-f32(b))
    ndc=tuple(sub(mul(2,x),1) for x in p)
    delta=tuple(mul(mul(2,x),extent) for x in st)
    h=tuple(add(mul(d[j],ndc[min(j,2)]),b[j]) for j in range(4))
    dh=tuple(mul(d[j],delta[min(j,2)]) for j in range(4))
    error=[]
    for j in range(4):
        k=min(j,2)
        magnitude=add(add(mul(2,abs(p[k])),mul(2,abs(st[k]))),1)
        terms=add(add(add(mul(abs(d[j]),magnitude),mul(2,abs(b[j]))),abs(h[j])),abs(dh[j]))
        error.append(add(mul(1e-6,terms),1e-30))
    aw=abs(h[3]);mw=sub(sub(aw,abs(dh[3])),error[3])
    if not (aw>1e-6 and mw>1e-6): return False
    denominator=mul(mul(aw,mw),.99999)
    upper=[]
    for j in range(3):
        n=abs(sub(mul(dh[j],h[3]),mul(h[j],dh[3])))
        n=add(n,mul(1e-6,add(abs(mul(dh[j],h[3])),abs(mul(h[j],dh[3])))))
        n=add(add(n,mul(error[j],aw)),mul(abs(h[j]),error[3]))
        division_error=mul(1e-6,add(mul(abs(h[j]),mw),mul(add(add(abs(h[j]),abs(dh[j])),error[j]),aw)))
        upper.append(mul(add(n,division_error),1.00001))
    if not upper[2]<=mul(.049,denominator): return False
    ns=add(add(mul(upper[0],upper[0]),mul(upper[1],upper[1])),mul(upper[2],upper[2]))
    ds=mul(denominator,denominator)
    return math.isfinite(ns) and math.isfinite(ds) and ds>0 and ns<=mul(mul(.002401,ds),.99999)


def original_project(pos, diagonal, offset, fused=False):
    """Actual pinned project shape, float32 NDC/homogeneous/view divisions."""
    p,d,b=[tuple(map(f32,x)) for x in (pos,diagonal,offset)]
    ndc=tuple(f32(2*x-1) for x in p)
    h=tuple(f32(d[j]*ndc[min(j,2)]+b[j]) if fused else
            f32(f32(d[j]*ndc[min(j,2)])+b[j]) for j in range(4))
    if h[3]==0: return (math.inf,)*3
    return tuple(f32(x/h[3]) for x in h[:3])


def original_future_positions(pos,step,remaining):
    """Every original remaining float32 +- update, not analytic endpoint only."""
    points={tuple(map(f32,pos))};st=list(map(f32,step))
    for _ in range(remaining):
        st=[f32(x*.5) for x in st]
        points={tuple(f32(p[j]+sign*st[j]) for j in range(3))
                for p in points for sign in (-1,1)}
    return points


class ReflectionLodTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base = ROOT / probe.BASE
        if not base.exists(): raise unittest.SkipTest('Pinned archive unavailable; no source acceptance')
        cls.files = probe.archive_members(base)
        assert probe.sha256(base.read_bytes()) == probe.BASE_SHA256

    def test_off_specular_and_settings_are_literal_upstream(self):
        for arm in probe.ARMS:
            patched = probe.patch_members(self.files, M, arm)
            self.assertEqual(set(self.files), set(patched))
            self.assertEqual(self.files[probe.SPECULAR], select_private(patched[probe.SPECULAR], False))
            settings = patched[probe.SETTINGS].decode().replace(M['option_declaration'], '').replace(M['option_declaration'].replace('//#define', '#define'), '')
            self.assertEqual(self.files[probe.SETTINGS].decode(), settings)
            self.assertEqual(self.files['shaders/include/misc/raytracer.glsl'], patched['shaders/include/misc/raytracer.glsl'])
            self.assertEqual(self.files['LICENSE'], patched['LICENSE'])

    def test_coarse_traversal_and_composition_preserved_no_ray_or_weight_change(self):
        original = self.files[probe.SPECULAR].decode()
        selected = select_private(probe.patch_members(self.files, M, 'enabled')[probe.SPECULAR], True).decode()
        selected = selected.replace('#include "/include/shaderlod/reflection_depth_cache.glsl"\n', '').replace('shaderlod_reflection_depth_trace(', 'raymarch_depth_buffer(')
        self.assertEqual(original, selected)
        source = HELPER.read_text()
        for term in ('for (int i = 0; i < intersection_step_count; ++i, ray_pos += ray_step)',
                     'max(abs(ray_step.z) * 3.0, 0.02 / sqr(view_pos.z))',
                     'return hand_depth < final_depth && final_depth < 1.0;',
                     'ray_pos -= ray_step;', 'ray_pos += ray_step;',
                     'refinement_tolerance_pixels', 'remaining_span.z <= depth_tolerance * 0.0625', 'uniformly_inside_hit || uniformly_outside_hit',
                     'min_w > 1e-6', 'numerator_squared <= 0.002401 * denominator_squared * 0.99999'):
            self.assertIn(term, source)
        self.assertNotIn('dFdx', source)
        self.assertNotIn('dFdy', source)
        self.assertNotIn('textureGrad', source)
        self.assertNotIn('texture(', source)
        self.assertEqual(probe.sha256(HELPER.read_bytes()), M['added_members'][0]['sha256'])

    def test_fail_closed_pins_scope_and_unknown_arms(self):
        for name in (*probe.MEMBERS, 'LICENSE', M['derived_source']['path']):
            with self.assertRaises(ValueError): probe.patch_members(self.files | {name:self.files[name]+b'changed'}, M, 'enabled')
        for key,value in [('switch','wrong'),('refinement_error_pixels',2),('refinement_depth_tolerance_fraction',1),('quality_class','accepted')]:
            bad = copy.deepcopy(M); bad[key]=value
            with self.assertRaises(ValueError): probe.patch_members(self.files,bad,'enabled')
        with self.assertRaises(ValueError): probe.patch_members(self.files,M,'other')
        with self.assertRaises(ValueError): probe.build(output=ROOT/'bad.zip')

    def test_zero_tolerance_is_exact_even_with_cache_and_edges(self):
        rng = random.Random(77)
        for _ in range(3000):
            pos=(rng.uniform(-.2,127.9),rng.uniform(-.2,127.9),rng.uniform(.1,1.1))
            step=(rng.uniform(-2,2),rng.uniform(-2,2),rng.uniform(-.04,.04))
            depth=lambda t: .5 if t[0]%3 else 1.0
            a=refine(pos,step,8,depth,0,False);b=refine(pos,step,8,depth,0,True)
            self.assertEqual(a[:2],b[:2]);self.assertLessEqual(b[2],a[2])

    def test_bounded_termination_preserves_depth_classification_and_error(self):
        rng=random.Random(171); savings=0
        for _ in range(5000):
            pos=(rng.uniform(.1,127.9),rng.uniform(.1,127.9),rng.uniform(.1,1.1))
            step=(rng.uniform(-2,2),rng.uniform(-2,2),rng.uniform(-.04,.04))
            depth=lambda t: [0.0,.5,1.0][t[0]%3] # hand, ordinary, sky discontinuities
            count=rng.randrange(2,9)
            a=refine(pos,step,count,depth,0,False);b=refine(pos,step,count,depth,.25,True)
            self.assertEqual(a[1],b[1]);self.assertEqual(.01<a[1]<1,.01<b[1]<1)
            self.assertLessEqual(max(abs(a[0][j]-b[0][j]) for j in range(2)),.25+1e-12)
            self.assertLessEqual(abs(a[0][2]-b[0][2]),.02*.0625+1e-12)
            if b[4]:savings+=1
        self.assertGreater(savings,500) # actually exercises stopping, not only fallback

    def test_near_and_invalid_policy_full_fallback_source_contract(self):
        source=HELPER.read_text()
        for term in ('shaderlodBridgeVersion == 2','shaderlodEnabled == 1','shaderlodFar > shaderlodNear',
                     'shaderlodTransition <= shaderlodFar - shaderlodNear','shaderlodTransition > 0.0',
                     '!isnan(shaderlodNear)','!isinf(shaderlodFar)','refinement_step_count > 0u',
                     'intersection_step_count <= 256u','refinement_step_count <= 64u',
                     'smoothstep(near_end, far_end, distance_blocks)','0.25 * reuse_weight'):
            self.assertIn(term,source)
        # Smoothstep is exactly zero through full-near endpoint, unity at full-far.
        near,far,width=32,96,8
        def weight(distance):
            t=max(0,min(1,(distance-(near+width/2))/(far-near)))
            return t*t*(3-2*t)
        self.assertEqual(0,weight(36));self.assertEqual(1,weight(100));self.assertEqual(.5,weight(68))

    def test_repeated_undefined_outside_fetches_never_reused(self):
        pos=(-2.2,-2.2,.5);step=(0,0,.001)
        result=refine(pos,step,4,lambda t:.49,0,True)
        self.assertEqual(4,result[2])

    def test_assembly_determinism_license_and_added_scope(self):
        outputs=[]
        for arm in ('disabled','enabled'):
            output=ROOT/'build'/f'reflection-fixture-{arm}.zip'
            metadata=probe.build(output=output,arm=arm)
            first=probe.sha256(output.read_bytes())
            self.assertEqual(metadata,probe.build(output=output,arm=arm))
            self.assertEqual(first,probe.sha256(output.read_bytes()))
            with zipfile.ZipFile(output) as z:
                self.assertEqual(self.files['LICENSE'],z.read('LICENSE'))
                self.assertEqual(set(self.files)|{probe.METADATA,M['added_members'][0]['path']},set(z.namelist()))
            outputs.append(first)
        self.assertNotEqual(*outputs)

    def test_project_source_pinned_and_cheap_gate_order(self):
        evidence=M['projection_source']
        self.assertEqual(evidence['sha256'],probe.sha256(self.files[evidence['path']]))
        self.assertEqual(1,self.files[evidence['path']].decode().count(evidence['helper']))
        source=HELPER.read_text()
        self.assertLess(source.index('if (policy_valid)'),source.index('length(view_pos)'))
        loop=source[source.index('int remaining_steps'):source.index('// No intersection')]
        self.assertLess(loop.index('max(span_pixels.x, span_pixels.y)'),loop.index('vec2 lower_pixels'))
        self.assertIn('remaining_steps >= 2 && remaining_steps <= 7',loop)
        self.assertNotIn('exp2(',source)
        self.assertNotIn('/ center_h.w',source)
        self.assertIn('numerator_z <= 0.049 * denominator',source)
        self.assertLess(source.index('numerator_z <= 0.049 * denominator'),source.index('vec2 ndc_xy'))
        for remaining in range(2,8):
            actual=f32(1-f32(float(1<<(7-remaining))/128))
            self.assertEqual(1-2**(-remaining),actual)

    def test_float32_guard_invariants_poles_nonfinite_extremes_and_cancellation(self):
        normal=((-1.,-1.,0.,-9.999),(0.,0.,-1.,10.))
        self.assertTrue(view_guard((.5,.5,.5),(.001,.001,.00001),.75,*normal))
        for invalid in (math.nan,math.inf,-math.inf,1e30,-1e30):
            self.assertFalse(view_guard((.5,.5,invalid),(.001,.001,0),.75,*normal))
            self.assertFalse(view_guard((.5,.5,.5),(.001,invalid,0),.75,*normal))
            self.assertFalse(view_guard((.5,.5,.5),(.001,.001,0),.75,(invalid,1,0,1),(0,0,1,0)))
        # Pole crossing and large cancellation between homogeneous W terms.
        self.assertFalse(view_guard((.5,.5,.5),(.001,.001,.001),.75,(1,1,0,1),(0,0,1,0)))
        self.assertFalse(view_guard((.5,.5,.999999),(.00001,0,0),.75,(1,1,1,-1e6),(0,0,-1,1e6)))
        self.assertFalse(view_guard((.5,.5,.5),(.001,0,0),.75,(1,1,0,1e-12),(0,0,1,1e-12)))
        # Large offset/displacement and either W sign must not accept Inf<=Inf.
        for sign in (-1,1):
            self.assertFalse(view_guard((.5,.5,.5),(.1,.1,0),.75,(1e6,1e6,1e6,sign*1e6),(1e6,1e6,1e6,sign*1e6)))

    def test_accepted_float32_guard_bounds_actual_original_updates_and_projections(self):
        rng=random.Random(319);accepted=0;far_accepted=0
        matrices=[((-1.,-1.,0.,-9.999),(0.,0.,-1.,10.)),
                  ((1.1,1.8,0.,-4.9995),(0.,0.,-1.,5.0005)),
                  ((-1.1,-1.8,0.,4.9995),(0.,0.,1.,-5.0005)),
                  ((.7,1.2,-.2,-2.),(.03,-.08,-1.,2.5))]
        for _ in range(1400):
            d,b=rng.choice(matrices)
            remaining=rng.randrange(2,8);extent=1-2**(-remaining)
            z=rng.choice((rng.uniform(.1,.8),rng.uniform(.98,.9999)))
            p=tuple(map(f32,(rng.random(),rng.random(),z)))
            st=tuple(map(f32,(rng.uniform(-.00015,.00015),rng.uniform(-.00015,.00015),rng.uniform(-1e-7,1e-7))))
            if not view_guard(p,st,extent,d,b): continue
            accepted+=1
            if z>.98: far_accepted+=1
            points=original_future_positions(p,st,remaining)
            # Also original analytic endpoints under float32 math, both signs.
            points |= {tuple(f32(p[j]+sign*f32(st[j]*f32(extent))) for j in range(3)) for sign in (-1,1)}
            for fused in (False,True):
                center=original_project(p,d,b,fused)
                for point in points:
                    actual=original_project(point,d,b,fused)
                    error=math.sqrt(sum((x-y)**2 for x,y in zip(actual,center)))
                    self.assertTrue(math.isfinite(error))
                    self.assertLessEqual(error,.05,(p,st,remaining,d,b,point,fused))
        self.assertGreater(accepted,500)
        self.assertGreater(far_accepted,40) # exercises nonlinear depth, not only near plane

    def test_actual_pinned_iris_lexer_discovers_nested_guard_and_applies_boolean_override(self):
        evidence = M["iris_option_discovery"]
        iris = ROOT / evidence["jar_path"]
        if not iris.exists() or not shutil.which("java"):
            self.skipTest("Actual pinned Iris lexer/JDK unavailable; no discovery acceptance")
        self.assertEqual(evidence["jar_sha256"], probe.sha256(iris.read_bytes()))
        with zipfile.ZipFile(iris) as archive:
            self.assertEqual(evidence["class_sha256"], probe.sha256(archive.read(evidence["class_path"])))
        cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
        dependencies = []
        for relative, filename in (("com.google.guava/guava/32.1.2-jre", "guava-32.1.2-jre.jar"),
                                   ("it.unimi.dsi/fastutil/8.5.12", "fastutil-8.5.12.jar")):
            matches = sorted((cache / relative).glob("*/" + filename))
            if len(matches) != 1:
                self.skipTest("Existing Iris lexer dependencies unavailable; no downloads/build")
            dependencies.append(matches[0])
        harness = r'''
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
        disabled = probe.patch_members(self.files, M, "disabled")
        enabled = probe.patch_members(self.files, M, "enabled")
        with tempfile.TemporaryDirectory(prefix="shaderlod-density-lexer-") as temporary:
            directory = Path(temporary)
            java_source = directory / "DensityIrisLexer.java"
            java_source.write_text(harness)
            inputs = [directory / name for name in ("density.glsl", "disabled.glsl", "enabled.glsl",
                                                    "original-density.glsl", "original-settings.glsl", "entry.fsh")]
            payloads = (disabled[probe.SPECULAR] + HELPER.read_bytes(), disabled[probe.SETTINGS], enabled[probe.SETTINGS],
                        self.files[probe.SPECULAR], self.files[probe.SETTINGS], self.files["shaders/world0/deferred1.fsh"])
            for path, data in zip(inputs, payloads):
                path.write_bytes(data)
            command = ["java", "--class-path", os.pathsep.join(map(str, (iris, *dependencies))),
                       str(java_source), probe.SWITCH, *map(str, inputs)]
            result = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("combined absent; actual nested discovered; default false/true; overrides true/false applied; sole new option; no stage leak", result.stdout)


if __name__ == "__main__": unittest.main()
