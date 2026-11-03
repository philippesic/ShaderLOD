from pinned_tools import java_tool
"""G06 source/GLSL checks. No game, OpenGL context, quality or speed proof."""
import importlib.util
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('g06', ROOT / 'scripts/build_makeup_g06.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
BASE = Path(os.environ.get('SHADERLOD_MAKEUP_ARCHIVE', str(ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip')))
MACROS = {'IS_IRIS': 1, 'IRIS_VERSION': 10812, 'MC_VERSION': 12101, 'MC_OS_MAC': 1}


def preprocess(files, entry, mode=2, macros=None, options=None):
    def expand(n, stack=()):
        if n in stack:
            raise ValueError('include cycle')
        return re.sub(r'^\s*#include\s+"(/[^\"]+)"\s*$',
                      lambda x: expand('shaders' + x[1], stack + (n,)), files[n].decode(), flags=re.M)
    text = expand(entry).replace('#define G06_AO_SPATIAL 0', '#define G06_AO_SPATIAL ' + str(mode))
    if entry.endswith('.properties'):
        text = re.sub(r'^\s*#(?!if\b|ifdef\b|ifndef\b|elif\b|else\b|endif\b|define\b).*$', '', text, flags=re.M)
    for option, value in (options or {}).items():
        text = re.sub(r'(#define ' + option + r') [^ \n]+', r'\g<1> ' + str(value), text)
    text = re.sub(r'^\s*#(?:version|extension).*$', '', text, flags=re.M)
    preprocessor = shutil.which('clang') or shutil.which('cpp')
    if preprocessor is None:
        raise RuntimeError('GLSL source checks require clang or cpp')
    command = [preprocessor, '-E', '-P', '-x', 'c'] + [f'-D{k}={v}' for k, v in (MACROS if macros is None else macros).items()] + ['-']
    return subprocess.run(command, input=text, text=True, capture_output=True, check=True).stdout


def normalized(text):
    return re.sub(r'\s+', ' ', text).strip()


class G06Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.native = m.read(BASE)
        cls.files = m.patch(cls.native)

    def test_graph_native_kernel_and_options_preserved(self):
        changed = {n for n in self.native if self.native[n] != self.files[n]}
        self.assertEqual(changed, {m.CONFIG, m.CALLER, m.PROPS, 'shaders/world0/deferred.fsh', 'shaders/world0/deferred.vsh'})
        self.assertEqual(self.files[m.CONFIG], m.OPTION.encode() + self.native[m.CONFIG])
        for n in ['shaders/lib/ao.glsl', 'shaders/lib/dither.glsl', 'LICENSE', 'shaders/world0/deferred1.vsh']:
            old = n.replace('deferred1.', 'deferred.')
            self.assertEqual(self.files[n], self.native[old])
        self.assertEqual(self.files['shaders/world0/deferred1.fsh'], self.native['shaders/world0/deferred.fsh'])
        self.assertEqual(self.files[m.CALLER].replace(m.INCLUDE.encode(), b'').replace(m.CALL.encode(), m.NATIVE_CALL.encode()).replace(m.PAINT.encode(), b''), self.native[m.CALLER])
        self.assertIn(b'colortex8Format = RG32F', self.files['shaders/lib/g06/producer.glsl'])
        self.assertIn(b'size.buffer.colortex8=0.5 0.5', self.files[m.PROPS])

    def test_off_and_unsupported_consumers_preprocess_to_native(self):
        cases = [(0, MACROS, {}), (4, MACROS, {}), (2, MACROS, {'AO': 0}), (2, MACROS, {'AOSTEPS': 4})]
        for key in ['IS_IRIS', 'MC_OS_MAC']:
            cases.append((2, {k: v for k, v in MACROS.items() if k != key}, {}))
        for key, value in [('IRIS_VERSION', 10814), ('MC_VERSION', 12111), ('DISTANT_HORIZONS', 1), ('VOXY', 1), ('VOXY_PATCH', 1)]:
            cases.append((2, dict(MACROS, **{key: value}), {}))
        for mode, macros, options in cases:
            with self.subTest(mode=mode, macros=macros, options=options):
                self.assertEqual(normalized(preprocess(self.files, 'shaders/world0/deferred1.fsh', mode, macros, options)),
                                 normalized(preprocess(self.native, 'shaders/world0/deferred.fsh', mode, macros, options)))
        for dimension in ['world1', 'world-1', '']:
            entry = 'shaders/' + (dimension + '/' if dimension else '') + 'deferred.fsh'
            self.assertEqual(normalized(preprocess(self.files, entry)), normalized(preprocess(self.native, entry)))

    def test_native_quality_options_remain_in_producer(self):
        for steps, aa in [(5, 0), (5, 2), (11, 3)]:
            text = preprocess(self.files, 'shaders/world0/deferred.fsh', options={'AOSTEPS': steps, 'AA_TYPE': aa})
            self.assertIn(f'i < {steps}', text)
            self.assertIn('shiftedSemiblue(nativePixel)' if aa else 'semiblue(nativePixel)', text)
        control = preprocess(self.files, 'shaders/world0/deferred1.fsh', 1)
        self.assertIn('return dbao(dither);', control)
        self.assertNotIn('texture2DLod(colortex8', control)

    def test_real_graph_properties_guards(self):
        # Properties preprocessor consumes the selected shader options like Iris.
        for enabled, macros in [(True, MACROS), (False, dict(MACROS, DISTANT_HORIZONS=1)), (False, dict(MACROS, IRIS_VERSION=10814))]:
            out = preprocess(self.files, m.PROPS, macros=dict(macros, G06_AO_SPATIAL=2, AO=1, AOSTEPS=5))
            self.assertIn('program.world0/deferred.enabled=' + str(enabled).lower(), out)
            self.assertEqual('size.buffer.colortex8=0.5 0.5' in out, enabled)

    def test_full_native_properties_options_do_not_become_dimension(self):
        # Unlike the initial harness, supply the dimension-local native options
        # that the real global properties preprocessor actually receives.
        full = dict(MACROS, G06_AO_SPATIAL=2, AO=1, AOSTEPS=5, NETHER=1, THE_END=1, UNKNOWN_DIM=1)
        out = preprocess(self.files, m.PROPS, macros=full)
        self.assertIn('program.world0/deferred.enabled=true', out)
        self.assertIn('size.buffer.colortex8=0.5 0.5', out)
        # Actual per-shader guards remain dimension-local and unchanged.
        for dimension in ['NETHER', 'THE_END', 'UNKNOWN_DIM']:
            out = preprocess(self.files, 'shaders/world0/deferred1.fsh', macros=dict(MACROS, **{dimension: 1}))
            self.assertNotIn('float g06Ao(', out)

    def test_early_rejection_matches_vector_predicate(self):
        import itertools
        # Invalid AO, depth discontinuity, exact threshold, valid and NaN
        # preserve the old comparison semantics without widening admission.
        samples = [(-1., 100.), (1.1, 100.), (.5, 101.),
                   (.5, 100.2), (.5, 100.), (float('nan'), 100.)]
        source = self.files['shaders/lib/g06/reconstruct.glsl'].decode()
        for taps in itertools.product(samples, repeat=4):
            old = any(a < 0 for a, d in taps) or any(a > 1 for a, d in taps) or any(abs(d-100.) > .2 for a, d in taps)
            new = any(a < 0 or a > 1 or abs(d-100.) > .2 for a, d in taps)
            self.assertEqual(old, new)
        for current, following in zip('abc', 'bcd'):
            self.assertLess(source.index('if (' + current + '.x'), source.index('vec2 ' + following + ' = texture'))
        self.assertIn('return mix(mix(a.x, b.x, f.x), mix(c.x, d.x, f.x), f.y);', source)

    def test_reconstruction_lattice_and_rejection(self):
        # Actual producer/consumer lattice: anchors align, including right border.
        for size in [2, 4, 1920]:
            for pixel in range(size):
                p = pixel * .5
                lo, hi = min(int(p), size // 2 - 1), min(int(p) + 1, size // 2 - 1)
                f = p % 1
                reconstructed = (2 * lo + .5) * (1-f) + (2 * hi + .5) * f
                self.assertEqual(reconstructed, min(pixel + .5, size - 1.5))
        text = self.files['shaders/lib/g06/reconstruct.glsl'].decode()
        self.assertEqual(text.count('texture2DLod(colortex8'), 4)
        self.assertIn('blocks > 64.0', text)
        self.assertIn('return dbao(dither);', text)
        # Explicitly charge source work, not hardware-counter or speed evidence.
        native_fetches = 1 + 2 * 5
        all_far_reused_fetches = 4 + native_fetches / 4
        self.assertEqual(all_far_reused_fetches, 6.75)
        # Excludes extra producer eligibility read; total 7.0 vs 11.0.
        self.assertEqual(all_far_reused_fetches + .25, 7.0)

    def test_pinned_iris_property_layout_and_public_option_parser(self):
        iris = ROOT / 'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        self.assertEqual(m.sha(iris.read_bytes()), '3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83')
        cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
        deps = []
        for pattern in ['com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar',
                        'it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar',
                        'org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar']:
            matches = list(cache.glob(pattern))
            self.assertEqual(len(matches), 1)
            deps += matches
        java = r'''
import java.nio.file.*; import java.util.*; import java.io.*;
import net.irisshaders.iris.shaderpack.preprocessor.PropertiesPreprocessor;
import net.irisshaders.iris.shaderpack.option.OptionAnnotatedSource;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.gl.texture.TextureScaleOverride;
import net.irisshaders.iris.helpers.StringPair;
class Layout {
 public static void main(String[] args) throws Exception {
  String source=Files.readString(Path.of(args[0]));
  for(int mode=0;mode<4;mode++)for(boolean dh:List.of(false,true)){
   var defs=new ArrayList<StringPair>(List.of(new StringPair("MC_VERSION","12101"),new StringPair("IS_IRIS","1"),new StringPair("IRIS_VERSION","10812"),new StringPair("MC_OS_MAC","1"),new StringPair("G06_AO_SPATIAL",Integer.toString(mode)),new StringPair("AO","1"),new StringPair("AOSTEPS","5")));
   if(dh)defs.add(new StringPair("DISTANT_HORIZONS","1"));
   var p=new Properties();p.load(new StringReader(PropertiesPreprocessor.preprocessSource(source,defs)));
   boolean active=mode>0&&!dh;
   if(!Boolean.toString(active).equals(p.getProperty("program.world0/deferred.enabled")))throw new AssertionError("enable "+mode+" "+dh);
   if(active){var s=p.getProperty("size.buffer.colortex8").split(" ");var scale=new TextureScaleOverride(s[0],s[1]);if(scale.getX(1920)!=960||scale.getY(1200)!=600)throw new AssertionError("half size");}
   else if(p.containsKey("size.buffer.colortex8"))throw new AssertionError("fallback layout changed");
  }
  var annotated=new OptionAnnotatedSource(Files.readString(Path.of(args[1])));
  var options=annotated.getOptionSet(AbsolutePackPath.fromAbsolutePath("/lib/config.glsl"),Set.of());
  if(!options.getStringOptions().containsKey("G06_AO_SPATIAL"))throw new AssertionError("public numeric option missing");
  for(String name:List.of("AA_TYPE","AOSTEPS","AO_STRENGTH"))if(!options.getStringOptions().containsKey(name))throw new AssertionError("native option lost "+name);
  System.out.println("PASS pinned Iris: 8 property layouts, half-size RG32F target extent, public G06/native option discovery");
 }
}
'''
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            with zipfile.ZipFile(iris) as z:
                jcpp = d / 'jcpp.jar'
                jcpp.write_bytes(z.read('META-INF/jarjar/jcpp-1.4.14.jar'))
            source, properties, config = d / 'Layout.java', d / 'shaders.properties', d / 'config.glsl'
            source.write_text(java)
            properties.write_bytes(self.files[m.PROPS])
            config.write_bytes(self.files[m.CONFIG])
            result = subprocess.run([java_tool('java'), '-Xmx256m',
                '--class-path', ':'.join(map(str, [iris, *deps, jcpp])), str(source), str(properties), str(config)],
                capture_output=True, text=True, timeout=40)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            print(result.stdout.strip())

    def test_actual_iris_option_to_properties_regression(self):
        iris = ROOT / 'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
        deps = []
        for pattern in ['com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar',
                        'it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar',
                        'org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar',
                        'com.google.code.gson/gson/2.10.1/*/gson-2.10.1.jar',
                        'org.joml/joml/1.10.5/*/joml-1.10.5.jar']:
            matches = list(cache.glob(pattern))
            self.assertEqual(len(matches), 1)
            deps += matches
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            with zipfile.ZipFile(iris) as z:
                jcpp = d / 'jcpp.jar'
                jcpp.write_bytes(z.read('META-INF/jarjar/jcpp-1.4.14.jar'))
            # Explicit regression mutation of the current candidate, not a
            # substitute for the unavailable historical r1 ZIP. Iris injects
            # dimension flags discovered across all native entry points.
            old = d / 'regression-dimension-guard.properties'
            guard = m.PROPERTY_GUARD.encode()
            self.assertEqual(self.files[m.PROPS].count(guard), 1)
            old.write_bytes(self.files[m.PROPS].replace(guard, guard +
                b' && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM', 1))
            candidate = d / 'r2.zip'
            candidate.write_bytes(m.archive(self.files))
            result = subprocess.run([java_tool('java'), '-Xmx256m',
                '--class-path', ':'.join(map(str, [iris, *deps, jcpp])), str(ROOT / 'tests/fixtures/MakeupG06Properties.java'),
                str(candidate), str(ROOT / 'dependencies/shortlist-test-inputs/g08/native100.txt'), str(old)], capture_output=True, text=True, timeout=40)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            print(result.stdout.strip())

    def test_deterministic_build_native_sidecar_and_tamper(self):
        self.assertEqual(m.archive(self.files), m.archive(m.patch(self.native)))
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            options = d / 'native.txt'
            options.write_bytes(b'AA_TYPE=2\nAO=1\nAOSTEPS=5\nAO_STRENGTH=0.70\n')
            out = d / 'candidate.zip'
            receipt = m.build(BASE, options, out)
            self.assertTrue(receipt['default_off'])
            self.assertEqual(Path(str(out) + '.txt').read_bytes(), options.read_bytes())
            with self.assertRaises(ValueError):
                m.build(BASE, options, out)
            bad = d / 'bad.zip'
            bad.write_bytes(BASE.read_bytes() + b'x')
            with self.assertRaises(ValueError):
                m.read(bad)

    @unittest.skipUnless(shutil.which('glslangValidator'), 'glslangValidator unavailable')
    def test_glsl_syntax_all_actual_stages(self):
        # Offline front end, compatibility syntax; does not create a GL context.
        with tempfile.TemporaryDirectory() as d:
            for mode in [0, 1, 2, 3]:
                for name in ['shaders/world0/deferred.fsh', 'shaders/world0/deferred.vsh', 'shaders/world0/deferred1.fsh', 'shaders/world0/deferred1.vsh']:
                    with self.subTest(mode=mode, name=name):
                        text = preprocess(self.files, name, mode)
                        stage = 'frag' if name.endswith('fsh') else 'vert'
                        path = Path(d) / ('stage.' + stage)
                        # Iris upgrades legacy compatibility programs before compilation.
                        path.write_text('#version 330 compatibility\n#extension GL_ARB_shader_texture_lod : enable\n' + text)
                        result = subprocess.run(['glslangValidator', '-S', stage, str(path)], capture_output=True, text=True)
                        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
