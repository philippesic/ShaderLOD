"""Small pinned-source, policy and real-parser checks; no client or GPU work."""
import copy
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import build_photon_volume_light_rate as probe
M = json.loads((ROOT / probe.MANIFEST).read_text())


def function(source, signature):
    start = source.index(signature)
    bracket = source.index('{', start)
    depth, end = 1, bracket + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def tokens(source):
    return re.sub(r'\s+', '', re.sub(r'//[^\n]*|/\*.*?\*/', '', source, flags=re.S))


def select(source, private=False, stage=False, reuse=False):
    result, stack = [], []
    source = source.replace(M['option_declaration'], '')
    source = source.replace(M['option_declaration'].replace('//#define ' + probe.SWITCH + '\n', '#define ' + probe.SWITCH + '\n'), '')
    source = source.replace(M['option_declaration'].replace('//#define', '#define'), '')
    for line in source.splitlines(keepends=True):
        if line == '#ifdef ' + probe.SWITCH + '\n': stack.append(private)
        elif line == '#ifdef ' + probe.REUSE_SWITCH + '\n': stack.append(reuse)
        elif line == M['profile_condition'] + '\n': stack.append(stage)
        elif line in ('#else // ' + probe.SWITCH + '\n', '#else // ' + probe.REUSE_SWITCH + '\n', '#else // SHADERLOD_RATE_STAGE\n'):
            stack[-1] = not stack[-1]
        elif line in ('#endif // ' + probe.SWITCH + '\n', '#endif // ' + probe.REUSE_SWITCH + '\n', '#endif // SHADERLOD_RATE_STAGE\n'):
            stack.pop()
        elif not stack or all(stack): result.append(line)
    if stack: raise AssertionError('Unclosed private guard')
    return ''.join(result)


def allow(distance, footprint, rank, density, previous_density, altitude, previous_altitude):
    if not all(math.isfinite(v) for v in (distance, footprint, rank, density, previous_density, altitude, previous_altitude)):
        return False
    if (rank < 0 or rank >= 1 or distance <= 512 or footprint < 0 or footprint >= 48 or
        min(density, previous_density) < .08 or min(altitude, previous_altitude) < .1 or
        max(altitude, previous_altitude) > .9 or abs(altitude - previous_altitude) > .05 or
        abs(density - previous_density) > .1 * max(density, previous_density)):
        return False
    return rank < min(1, (distance-512)/1024) * min(1, (48-footprint)/16)


def expand(files, name):
    return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*',
                  lambda m: expand(files, 'shaders' + m[1] if m[1].startswith('/') else str(Path(name).parent / m[1])),
                  files[name].decode(), flags=re.M)


class VolumeLightRateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = probe.archive_members(ROOT / probe.BASE)
        cls.enabled = probe.patch_members(cls.original, M, 'enabled')
        cls.helper = cls.enabled[probe.HELPER].decode()

    def test_pins_scope_license_and_exact_off_nonconsumer(self):
        self.assertEqual(probe.BASE_SHA256, probe.sha256((ROOT / probe.BASE).read_bytes()))
        for arm in probe.ARMS:
            files = probe.patch_members(self.original, M, arm)
            for private, stage in ((False, False), (False, True), (True, False)):
                for name, payload in self.original.items():
                    actual = select(files[name].decode(), private, stage).encode() if name in probe.MEMBERS else files[name]
                    self.assertEqual(payload, actual, (arm, private, stage, name))
            for name, payload in self.original.items():
                if arm == 'original' or name not in probe.MEMBERS:
                    self.assertEqual(payload, files[name], (arm, name))
        for name in (*probe.MEMBERS, probe.COVERAGE, 'LICENSE'):
            with self.assertRaises(ValueError):
                probe.patch_members(self.original | {name: self.original[name] + b'x'}, M, 'enabled')
        bad = copy.deepcopy(M); bad['addition']['source_path'] = 'elsewhere'
        with self.assertRaises(ValueError): probe.patch_members(self.original, bad, 'enabled')
        with self.assertRaises(ValueError): probe.build(ROOT / 'elsewhere.zip')

    def test_original_bodies_and_sampling_only_clone_identity(self):
        upstream = self.original[probe.CUMULUS].decode()
        active_cumulus = select(self.enabled[probe.CUMULUS].decode(), True, True)
        original_draw = function(upstream, 'CloudsResult draw_cumulus_clouds(')
        renamed = function(active_cumulus, 'CloudsResult shaderlod_rate_original_draw_cumulus_clouds(')
        self.assertEqual(original_draw, renamed.replace('shaderlod_rate_original_draw_cumulus_clouds(', 'draw_cumulus_clouds('))
        for signature in ('float clouds_cumulus_density(', 'float clouds_cumulus_optical_depth(', 'vec2 clouds_cumulus_scattering('):
            self.assertEqual(function(upstream, signature), function(active_cumulus, signature))
        sampling = select(self.helper, True, True, False)
        self.assertNotIn('cached_', sampling)
        self.assertNotIn('shaderlod_rate_reuse_allowed', sampling)
        draw = function(sampling, 'CloudsResult shaderlod_rate_draw_cumulus_clouds(')
        draw = draw.replace('shaderlod_rate_draw_cumulus_clouds(', 'draw_cumulus_clouds(').replace('shaderlod_rate_density(', 'clouds_cumulus_density(').replace('shaderlod_rate_optical_depth(', 'clouds_cumulus_optical_depth(')
        self.assertEqual(tokens(original_draw), tokens(draw))
        density = function(self.helper, 'float shaderlod_rate_density(')
        density = density.replace('shaderlod_rate_density(', 'clouds_cumulus_density(').replace('shaderlod_rate_local_coverage(', 'clouds_cumulus_local_coverage(')
        density = re.sub(r'textureLod\(([^\n]+), 0\.0\)', r'texture(\1)', density)
        self.assertEqual(function(upstream, 'float clouds_cumulus_density('), density)
        optical = function(self.helper, 'float shaderlod_rate_optical_depth(').replace('shaderlod_rate_optical_depth(', 'clouds_cumulus_optical_depth(').replace('shaderlod_rate_density(', 'clouds_cumulus_density(')
        self.assertEqual(function(upstream, 'float clouds_cumulus_optical_depth('), optical)
        coverage = function(self.helper, 'float shaderlod_rate_local_coverage(').replace('shaderlod_rate_local_coverage(', 'clouds_cumulus_local_coverage(')
        coverage = re.sub(r'textureLod\(([^\n]+), 0\.0\)', r'texture(\1)', coverage)
        self.assertEqual(function(self.original[probe.COVERAGE].decode(), 'float clouds_cumulus_local_coverage('), coverage)

    def test_work_avoidance_and_uniform_fallback_precede_private_state(self):
        wrapper = function(self.helper, 'CloudsResult draw_cumulus_clouds(')
        self.assertLess(wrapper.index('shaderlodBridgeVersion != 2'), wrapper.index('shaderlod_rate_draw_cumulus_clouds('))
        self.assertIn('shaderlodEnabled != 1', wrapper)
        self.assertNotIn('cached_', wrapper)
        draw = function(select(self.helper, True, True, True), 'CloudsResult shaderlod_rate_draw_cumulus_clouds(')
        branch = draw.split('if (reuse_lighting) {', 1)[1].split('} else {', 1)[0]
        self.assertNotIn('optical_depth(', branch)
        self.assertIn('light_optical_depth = cached_depths.x;', branch)
        self.assertIn('(i & 1u) == 1u', draw)
        self.assertIn('cached_smooth', draw)
        self.assertIn('max(depth_delta.x, depth_delta.y) <= 0.1', draw)
        empty = draw.split('if (density < eps) {', 1)[1].split('continue;', 1)[0]
        self.assertIn('cached_valid = false', empty)
        self.assertIn('!moonlit && light_dir.y > 0.2 && cos_theta < 0.8', draw)
        self.assertNotIn('dFdx', self.helper); self.assertNotIn('dFdy', self.helper)
        for signature in ('float shaderlod_rate_density(', 'float shaderlod_rate_local_coverage('):
            self.assertNotIn('texture(', function(self.helper, signature))
        self.assertEqual(5, self.helper.count('textureLod('))
        # Original primary cell coordinates, optical integration and output remain
        # byte exact in the sampling clone; reuse changes only lighting depths.
        original = function(self.original[probe.CUMULUS].decode(), 'CloudsResult draw_cumulus_clouds(')
        start = original.index('        // guess optical depth')
        self.assertEqual(original[start:], draw[draw.index('        // guess optical depth'):])

    def test_policy_bands_finite_protection_and_single_cell_reuse_bound(self):
        good = [1536, 32, .5, .5, .5, .5, .5]
        self.assertTrue(allow(*good))
        for index, bad in ((0,512),(1,48),(2,1),(3,.079),(4,.079),(5,.09),(6,.91)):
            values = good.copy(); values[index] = bad
            self.assertFalse(allow(*values), (index, bad))
        for index in range(7):
            for bad in (math.nan, math.inf, -math.inf):
                values = good.copy(); values[index] = bad
                self.assertFalse(allow(*values))
        self.assertFalse(allow(1536,32,.5,.3,.5,.5,.5))
        self.assertFalse(allow(1536,32,.5,.5,.5,.5,.6))
        self.assertTrue(allow(1024,32,.49,.5,.5,.5,.5))
        self.assertFalse(allow(1024,32,.50,.5,.5,.5,.5))
        for n in range(1,81):
            # Even primary indices are always fresh; empty/continuity fallbacks
            # can only remove reused indices. Never more than half are reused.
            reused = [i for i in range(n) if i & 1 and i >= 3]
            self.assertLessEqual(len(reused), n//2)
            self.assertTrue(all(b-a >= 2 for a,b in zip(reused,reused[1:])))

    def test_guard_is_not_a_remote_shadow_certificate(self):
        # Smooth primary densities and prior fresh lighting can all agree while
        # an unseen remote blocker affects only the next secondary ray. This
        # deliberate counterexample prevents source acceptance being mislabeled
        # as a quality bound. The driver still must render this kind of scene.
        prior_fresh_depths = [1.0, 1.0, 1.0]
        unseen_odd_depth = 5.0
        extinction = .1
        smooth = abs(prior_fresh_depths[-1]-prior_fresh_depths[-2])*extinction <= .1
        self.assertTrue(smooth and allow(2000,24,.5,.5,.5,.5,.5))
        original_visibility = math.exp(-extinction*unseen_odd_depth)
        reused_visibility = math.exp(-extinction*prior_fresh_depths[-1])
        self.assertGreater(abs(reused_visibility-original_visibility), .25)

    @unittest.skipUnless(shutil.which('glslangValidator'), 'No offline GLSL compiler')
    def test_complete_deferred1_and_nonconsumer_semantic_compilation(self):
        folder = ROOT / 'build/volume-light-rate-source-checks'
        folder.mkdir(parents=True, exist_ok=True)
        for world, stage, arm in [('world0','deferred1','enabled'), ('world0','deferred1','sampling'),
                                  ('world0','deferred1','disabled'), ('world0','deferred','enabled'),
                                  ('world1','deferred1','enabled')]:
            files = probe.patch_members(self.original, M, arm)
            settings = files[probe.SETTINGS].decode()
            for option in ('COLORED_LIGHTS','SH_SKYLIGHT','ENVIRONMENT_REFLECTIONS','SKY_REFLECTIONS'):
                settings = re.sub(r'^\s*#define '+option+r'\s*$', '//#define '+option, settings, flags=re.M)
            files[probe.SETTINGS] = settings.encode()
            source = expand(files, f'shaders/{world}/{stage}.fsh').replace('\\\n', '')
            source = source.replace('#version 400 compatibility', '#version 400 compatibility\n#define MC_VERSION 12101\n#define MC_SHADOW_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n#define IS_IRIS\n#define IRIS_FEATURE_SEPARATE_ENTITY_DRAWS', 1)
            path = folder / f'{world}-{stage}-{arm}.frag'
            path.write_text(source)
            result = subprocess.run(['glslangValidator','-S','frag',str(path)], capture_output=True,text=True,timeout=20)
            (folder / (path.name + '.log')).write_text(result.stdout + result.stderr)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_actual_iris_private_option_discovery_override_and_no_stage_leak(self):
        evidence = M['iris_option_discovery']; iris = ROOT / evidence['jar_path']
        self.assertEqual(evidence['jar_sha256'], probe.sha256(iris.read_bytes()))
        cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
        dependencies = []
        for relative, filename in [('com.google.guava/guava/32.1.2-jre','guava-32.1.2-jre.jar'),
                                   ('it.unimi.dsi/fastutil/8.5.12','fastutil-8.5.12.jar')]:
            matches = list((cache / relative).glob('*/' + filename))
            self.assertEqual(1,len(matches)); dependencies.append(matches[0])
        harness = r'''
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
class VolumeRateIris {
 static void require(boolean v, String why) { if (!v) throw new AssertionError(why); }
 public static void main(String[] args) throws Exception {
  var refs = new HashSet<String>();
  for (int i=0;i<2;i++) refs.addAll(new OptionAnnotatedSource(Files.readString(Path.of(args[i]))).getBooleanDefineReferences().keySet());
  require(refs.contains("SHADERLOD_VOLUME_LIGHT_RATE"), "private gate reference missing");
  require(refs.contains("SHADERLOD_VOLUME_LIGHT_RATE_REUSE"), "reuse reference missing");
  require(!refs.contains("PROGRAM_DEFERRED1") && !refs.contains("WORLD_OVERWORLD"), "stage option leaked");
  for (int i=2;i<5;i++) {
   var source = new OptionAnnotatedSource(Files.readString(Path.of(args[i])));
   var options = source.getOptionSet(AbsolutePackPath.fromAbsolutePath("/settings.glsl"), refs);
   for (String name : List.of("SHADERLOD_VOLUME_LIGHT_RATE", "SHADERLOD_VOLUME_LIGHT_RATE_REUSE")) {
    require(options.getBooleanOptions().containsKey(name), "option undiscovered: "+name);
    var option=source.getBooleanOptions().values().stream().filter(v -> v.getName().equals(name)).findFirst().orElseThrow();
    boolean expectedDefault = name.endsWith("_REUSE") ? i==4 : i>=3;
    require(option.getDefaultValue()==expectedDefault, "wrong default: "+name);
    String changed=source.apply(new MutableOptionValues(options, Map.of(name,Boolean.toString(!expectedDefault))));
    require(changed.lines().anyMatch((!expectedDefault ? "#define " : "//#define ").concat(name)::equals), "override failed: "+name);
   }
  }
  System.out.println("2 private options discovered; all 3 defaults/overrides pass; stage guards do not leak");
 }
}
'''
        with tempfile.TemporaryDirectory(prefix='volume-light-rate-iris-') as temporary:
            folder = Path(temporary); java = folder / 'VolumeRateIris.java'; java.write_text(harness)
            paths = []
            for label,payload in [('cumulus',self.enabled[probe.CUMULUS]),('helper',self.enabled[probe.HELPER]),
                                  *[(arm,probe.patch_members(self.original,M,arm)[probe.SETTINGS]) for arm in ('disabled','sampling','enabled')]]:
                path=folder / (label+'.glsl'); path.write_bytes(payload); paths.append(path)
            result=subprocess.run(['java','--class-path',os.pathsep.join(map(str,[iris,*dependencies])),str(java),*map(str,paths)],capture_output=True,text=True,timeout=20)
            out=ROOT / 'build/volume-light-rate-source-checks'; out.mkdir(parents=True,exist_ok=True)
            (out / 'iris-discovery.log').write_text(result.stdout+result.stderr)
            self.assertEqual(0,result.returncode,result.stdout+result.stderr)


if __name__ == '__main__':
    unittest.main()
