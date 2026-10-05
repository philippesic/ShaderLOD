"""Pinned source, actual Iris parser and independent integration math; no GPU result."""
import copy
import json
import math
import os
from pathlib import Path
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
HELPER = (ROOT / probe.ADDITIONS[0][1]).read_text()
ADAPTER = (ROOT / probe.ADDITIONS[1][1]).read_text()


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
           feature=.3, overhead=0., bands=(32.,96.,16.), ratios=(.85,.75)):
    # Independent policy oracle, rather than executing/translating the GLSL.
    values = (entry, interval, footprint, feature, overhead, *bands, *ratios)
    n, f, w = bands
    medium, far = ratios
    valid = (all(math.isfinite(v) for v in values) and original >= minimum >= 1 and
             original <= 65536 and 0 <= entry <= 16777216 and 0 < interval <= 16777216 and
             footprint > 0 and feature > 0 and 0 <= overhead <= 1 and
             0 < n < f <= 4096 and 0 <= w <= min(512, 2*n, f-n) and
             (w == 0 or w >= 1/1024) and 0 <= far <= medium <= 1)
    if not enabled or not valid:
        return float(original)
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


class VolumeMathTests(unittest.TestCase):
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
        self.assertIn('shaderlodBridgeVersion == 2 && shaderlodEnabled == 1',ADAPTER)
        self.assertIn('(shaderlodEffectsVersion == 1 || shaderlodEffectsVersion == 2)',ADAPTER)
        self.assertIn('shaderlodVolumetricsMode < 0 || shaderlodVolumetricsMode > 2',ADAPTER)
        self.assertIn('shaderlodVolumetricsMode == 1',ADAPTER)
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
            self.assertEqual(len(self.files)+(0 if arm=='original' else 2),len(output))

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

    def test_mutated_source_manifest_and_helper_fail_closed(self):
        for name in (*probe.MEMBERS,'LICENSE'):
            with self.assertRaises(ValueError):
                probe.patch_members(self.files|{name:self.files[name]+b'\n'},MANIFEST,'disabled')
        for change in (lambda m:m.update(base_sha256='bad'),lambda m:m.update(effect_versions=[1,3]),
                       lambda m:m['members'][0].update(patched_sha256='bad'),
                       lambda m:m['additions'][0].update(sha256='bad'),
                       lambda m:m['additions'][0].update(source_path='../other'),
                       lambda m:m['members'][0]['replacements'][0].update(before='')):
            manifest=copy.deepcopy(MANIFEST);change(manifest)
            with self.assertRaises(ValueError):probe.patch_members(self.files,manifest,'disabled')
        with self.assertRaises(ValueError):probe.build(output=ROOT/'must-not-write.zip')
        with self.assertRaises(ValueError):probe.patch_members(self.files,MANIFEST,'other')

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
            active=disabled[probe.CUMULUS]+b'\n'+HELPER.encode()+b'\n'+ADAPTER.encode()
            payloads=(active,disabled[probe.SETTINGS],enabled[probe.SETTINGS],self.files[probe.CUMULUS],self.files[probe.SETTINGS],self.files['shaders/world0/deferred1.fsh'])
            for path,data in zip(inputs,payloads):path.write_bytes(data)
            command=['java','--class-path',os.pathsep.join(map(str,(iris,*dependencies))),str(java_source),probe.SWITCH,*map(str,inputs)]
            result=subprocess.run(command,capture_output=True,text=True,timeout=30)
            self.assertEqual(0,result.returncode,result.stdout+result.stderr)
            self.assertIn('sole new option; no stage leak',result.stdout)

    def test_prior_sampler_report_qualifies_no_mip_filters_only_not_new_pack_rendering(self):
        import build_photon_cloud_literal_probe as sampler
        evidence=MANIFEST['sampler_constraints']
        report=(ROOT/evidence['report_path']).read_bytes()
        self.assertEqual(evidence['report_sha256'],probe.sha256(report))
        self.assertEqual(2,len(sampler.validate_sampler_report(json.loads(report))['candidate_program_ids']))


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
