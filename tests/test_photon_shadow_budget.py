"""Bounded Photon shadow source/packing/parser checks; no client/GPU work."""
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
import build_photon_shadow_budget as probe
M = json.loads((ROOT / probe.MANIFEST).read_text())


def expand(files, name):
    return re.sub(r'^\s*#include\s+"([^"]+)"[^\n]*',
                  lambda m: expand(files, 'shaders' + m[1] if m[1].startswith('/') else str(Path(name).parent / m[1])),
                  files[name].decode(), flags=re.M)


def select_private(source, private, profile):
    """Select only our directives; leave every upstream line byte-exact."""
    source = source.decode().replace(M['option_declaration'], '').replace(
        M['option_declaration'].replace('//#define', '#define'), '')
    selected, result = [], []
    for line in source.splitlines(keepends=True):
        if line == f'#ifdef {probe.SWITCH}\n': selected.append(private)
        elif line == M['profile_condition'] + '\n': selected.append(profile)
        elif line in ('#else // SHADERLOD_SHADOW_PROFILE\n', f'#else // {probe.SWITCH}\n'):
            selected[-1] = not selected[-1]
        elif line in ('#endif // SHADERLOD_SHADOW_PROFILE\n', f'#endif // {probe.SWITCH}\n'):
            selected.pop()
        elif not selected or all(selected): result.append(line)
    if selected: raise AssertionError('Unclosed source guard')
    return ''.join(result).encode()


class ShadowBudgetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = probe.archive_members(ROOT / probe.BASE)
        cls.enabled = probe.patch_members(cls.original, M, 'enabled')

    def test_default_off_and_nonconsumer_restore_every_upstream_member(self):
        for arm in probe.ARMS:
            files = probe.patch_members(self.original, M, arm)
            for private, profile in ((False, False), (False, True), (True, False)):
                for name, original in self.original.items():
                    actual = select_private(files[name], private, profile) if name in probe.MEMBERS else files[name]
                    self.assertEqual(original, actual, (arm, private, profile, name))

    def test_manifest_corruption_and_scope_rejected(self):
        for name in (probe.PCSS, 'LICENSE'):
            broken = self.original | {name: self.original[name] + b'changed'}
            with self.assertRaises(ValueError): probe.patch_members(broken, M, 'enabled')
        bad = copy.deepcopy(M)
        bad['members'][0]['replacements'][0]['before'] = '#'
        with self.assertRaises(ValueError): probe.patch_members(self.original, bad, 'enabled')
        with self.assertRaises(ValueError): probe.build(ROOT / 'not-allowed.zip')

    def test_deterministic_control_assembly_license_and_scope(self):
        import zipfile
        for arm in probe.ARMS:
            first = ROOT / 'build' / f'shadow-budget-fixture-{arm}-a.zip'
            second = ROOT / 'build' / f'shadow-budget-fixture-{arm}-b.zip'
            try:
                metadata = probe.build(first, arm=arm)
                probe.build(second, arm=arm)
                self.assertEqual(first.read_bytes(), second.read_bytes())
                with zipfile.ZipFile(first) as z:
                    for name, data in self.original.items():
                        if arm == 'original' or name not in probe.MEMBERS:
                            self.assertEqual(data, z.read(name), name)
                    self.assertEqual(metadata, json.loads(z.read(probe.METADATA)))
                    self.assertEqual('unmeasured', metadata['gpu_benefit'])
            finally:
                first.unlink(missing_ok=True); second.unlink(missing_ok=True)

    def test_tail_quadrature_preserves_constant_visibility_and_original_certainty_probes(self):
        # General sampling invariant: reweight a stratified outer annulus so
        # its integrated contribution remains the original annulus weight.
        for original in range(4, 33):
            for budget in range(max(4, 4 + math.ceil((original-4)/2)), original+1):
                if budget == 4: continue
                weight = (original-4)/(budget-4)
                indices = [4 + math.floor((i+.5)*weight) for i in range(budget-4)]
                self.assertEqual(len(indices), len(set(indices)))
                self.assertTrue(all(4 <= i < original for i in indices))
                self.assertAlmostEqual(original, 4+(budget-4)*weight)
        active = select_private(self.enabled[probe.PCSS], True, True).decode()
        first = active.split('// perform first 4 iterations')[1].split('// perform remaining iterations')[0]
        self.assertIn('vogel_disc_sample(i, step_count, dither * tau)', first)
        self.assertNotIn('tail_weight', first)
        self.assertIn('if (shadow > 4.0 - eps)', first)
        self.assertIn('else if (shadow < eps)', first)
        self.assertNotIn('texture(shadowtex1', active.split('vec3 shadow_pcf(')[1].split('vec3 get_filtered_shadows(')[0])
        self.assertLess(active.index('int budget_steps'), active.index('// perform first 4 iterations'))

    @unittest.skipUnless(shutil.which('glslangValidator'), 'No offline GLSL compiler')
    def test_complete_entry_semantic_compilation(self):
        variants = [('world0', 'deferred4', True, False), ('world0', 'deferred4', False, False),
                    ('world0', 'deferred4', True, True), ('world1', 'deferred4', True, False),
                    ('world0', 'gbuffers_water', True, False)]
        folder = ROOT / 'build' / 'shadow-budget-source-checks'
        folder.mkdir(parents=True, exist_ok=True)
        for world, stage, private, protected in variants:
            files = probe.patch_members(self.original, M, 'enabled' if private else 'disabled')
            settings = files[probe.SETTINGS].decode()
            if not protected:
                for option in ('SHADOW_VPS', 'SHADOW_COLOR', 'SH_SKYLIGHT', 'COLORED_LIGHTS',
                               'CLOUD_SHADOWS', 'ENVIRONMENT_REFLECTIONS', 'SKY_REFLECTIONS', 'BORDER_FOG'):
                    settings = re.sub(r'^\s*#define ' + option + r'\s*$', '//#define ' + option, settings, flags=re.M)
            files[probe.SETTINGS] = settings.encode()
            source = expand(files, f'shaders/{world}/{stage}.fsh').replace('\\\n', '')
            source = source.replace('#version 400 compatibility', '#version 400 compatibility\n#define MC_VERSION 12101\n#define MC_SHADOW_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n#define IS_IRIS\n#define IRIS_FEATURE_SEPARATE_ENTITY_DRAWS', 1)
            path = folder / f'{world}-{stage}-{private}-{protected}.frag'
            path.write_text(source)
            result = subprocess.run(['glslangValidator', '-S', 'frag', str(path)], capture_output=True, text=True, timeout=30)
            (folder / (path.name + '.log')).write_text(result.stdout + result.stderr)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_actual_iris_discovery_and_override(self):
        evidence = M['iris_option_discovery']
        iris = ROOT / evidence['jar_path']
        self.assertEqual(evidence['jar_sha256'], probe.sha256(iris.read_bytes()))
        cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
        dependencies = []
        for relative, filename in [('com.google.guava/guava/32.1.2-jre', 'guava-32.1.2-jre.jar'),
                                   ('it.unimi.dsi/fastutil/8.5.12', 'fastutil-8.5.12.jar')]:
            matches = list((cache / relative).glob('*/' + filename))
            self.assertEqual(1, len(matches))
            dependencies.append(matches[0])
        harness = r'''
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
class ShadowIris {
 static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
 public static void main(String[] args) throws Exception {
  String name=args[0];
  var pcss=new OptionAnnotatedSource(Files.readString(Path.of(args[1])));
  var refs=pcss.getBooleanDefineReferences().keySet();
  require(refs.contains(name), "private option not referenced");
  require(!refs.contains("PROGRAM_DEFERRED4"), "stage leaked into option refs");
  for (int i=2;i<=3;i++) {
   var source=new OptionAnnotatedSource(Files.readString(Path.of(args[i])));
   var options=source.getOptionSet(AbsolutePackPath.fromAbsolutePath("/settings.glsl"), refs);
   require(options.getBooleanOptions().containsKey(name), "private option undiscovered");
   var option=source.getBooleanOptions().values().stream().filter(v -> v.getName().equals(name)).findFirst().orElseThrow();
   require(option.getDefaultValue()==(i==3), "wrong option default");
   boolean override=i==2;
   String changed=source.apply(new MutableOptionValues(options, Map.of(name, Boolean.toString(override))));
   String expected=(override ? "#define " : "//#define ")+name;
   require(changed.lines().anyMatch(expected::equals), "option override not applied");
  }
  System.out.println("private option discovered; defaults/overrides pass; no stage leak");
 }
}
'''
        with tempfile.TemporaryDirectory(prefix='shadow-budget-lexer-') as temporary:
            d = Path(temporary)
            java = d / 'ShadowIris.java'; java.write_text(harness)
            pcss = d / 'pcss.glsl'; pcss.write_bytes(self.enabled[probe.PCSS])
            off = d / 'off.glsl'; off.write_bytes(probe.patch_members(self.original, M, 'disabled')[probe.SETTINGS])
            on = d / 'on.glsl'; on.write_bytes(self.enabled[probe.SETTINGS])
            run = subprocess.run(['java', '--class-path', os.pathsep.join(map(str, [iris, *dependencies])),
                                  str(java), probe.SWITCH, str(pcss), str(off), str(on)],
                                 capture_output=True, text=True, timeout=30)
            self.assertEqual(0, run.returncode, run.stdout + run.stderr)
            folder = ROOT / 'build/shadow-budget-source-checks'; folder.mkdir(parents=True, exist_ok=True)
            (folder / 'iris-discovery.log').write_text(run.stdout + run.stderr)


if __name__ == '__main__': unittest.main()
