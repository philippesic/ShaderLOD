"""Explicit combined assembly, real Iris lexer and C preprocessing fixtures.

C preprocessing is source evidence, not GLSL driver compilation or GPU evidence.
"""
import copy
import itertools
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import build_photon_combined_lod as c
M = json.loads((ROOT / c.MANIFEST).read_text())


def write_sources(files, directory):
    for name, data in files.items():
        if name.startswith('shaders/') and Path(name).suffix in ('.glsl', '.fsh', '.vsh', '.gsh', '.csh'):
            path = directory / name
            path.parent.mkdir(parents=True, exist_ok=True)
            # C preprocessor handles GLSL conditional/include semantics; strip only
            # directives meaningful exclusively to the GLSL driver, not source tokens.
            text = data.decode()
            text = re.sub(r'^\s*#(?:version|extension).*$', '', text, flags=re.M)
            text = text.replace('#include "/', '#include "')
            path.write_text(text)


def preprocess(directory, name, switches=(), extra=()):
    command = ['clang', '-E', '-P', '-x', 'c', '-I', str(directory / 'shaders'), '-DMC_VERSION=12101',
               '-DIS_IRIS', '-DIRIS_FEATURE_SSBO', '-DDOF', *('-D' + s for s in switches),
               *extra, str(directory / name)]
    result = subprocess.run(command, capture_output=True, text=True, timeout=10)
    if result.returncode:
        raise AssertionError(result.stderr)
    # Ignore comments/whitespace; token stream retains numbers/operators/order.
    return re.findall(r'[A-Za-z_]\w*|\d+(?:\.\d*)?(?:[eE][+-]?\d+)?|[^\s]', result.stdout)


def private_off(source):
    """Select only known private guards; preserve all upstream directives/bytes."""
    output, stack = [], []
    for line in source.decode().splitlines(keepends=True):
        if line.strip() in {'#ifdef ' + name for name in c.SWITCHES}:
            stack.append(False)
        elif stack and re.match(r'\s*#(?:if|ifdef|ifndef)\b', line):
            stack.append(True)
        elif stack and line.startswith('#else'):
            stack[-1] = not stack[-1]
        elif stack and line.startswith('#endif'):
            stack.pop()
        elif not stack or all(stack):
            output.append(line)
    if stack: raise AssertionError('Private source guard unbalanced')
    return ''.join(output).encode()


LEXER = r'''
import java.nio.file.*;
import java.util.*;
import net.irisshaders.iris.shaderpack.option.*;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
class CombinedLexer {
    static void require(boolean value, String text) { if (!value) throw new AssertionError(text); }
    static List<String> sources(Path directory) throws Exception {
        var result = new ArrayList<String>();
        try (var paths = Files.walk(directory)) {
            for (var path : paths.filter(Files::isRegularFile).sorted().toList()) {
                if (path.toString().matches(".*\\.(glsl|fsh|vsh|gsh|csh)$"))
                    result.add(Files.readString(path));
            }
        }
        return result;
    }
    static Set<String> references(List<String> sources) {
        var result = new HashSet<String>();
        for (var source : sources)
            result.addAll(new OptionAnnotatedSource(source).getBooleanDefineReferences().keySet());
        return result;
    }
    static Set<String> options(List<String> sources, Set<String> references, boolean strings) {
        var result = new HashSet<String>();
        int i = 0;
        for (var source : sources) {
            var set = new OptionAnnotatedSource(source).getOptionSet(
                AbsolutePackPath.fromAbsolutePath("/source" + i++ + ".glsl"), references);
            result.addAll(strings ? set.getStringOptions().keySet() : set.getBooleanOptions().keySet());
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        var original = sources(Path.of(args[0]));
        var combined = sources(Path.of(args[1]));
        var gates = Set.of(args[2], args[3], args[4]);
        var originalRefs = references(original);
        var combinedRefs = references(combined);
        var addedRefs = new HashSet<>(combinedRefs); addedRefs.removeAll(originalRefs);
        require(addedRefs.equals(gates), "extra references " + addedRefs);
        var expected = options(original, originalRefs, false); expected.addAll(gates);
        require(options(combined, combinedRefs, false).equals(expected), "boolean option union mismatch");
        require(options(combined, combinedRefs, true).equals(options(original, originalRefs, true)),
                "upstream string options changed");
        var source = new OptionAnnotatedSource(Files.readString(Path.of(args[1], "shaders/settings.glsl")));
        var set = source.getOptionSet(AbsolutePackPath.fromAbsolutePath("/settings.glsl"), combinedRefs);
        for (var gate : gates) {
            require(!set.getBooleanOptions().get(gate).getOption().getDefaultValue(), "default not off " + gate);
            var overrides = new MutableOptionValues(set, Map.of(gate, "true"));
            var fresh = new OptionAnnotatedSource(Files.readString(Path.of(args[1], "shaders/settings.glsl")));
            String applied = fresh.apply(overrides);
            require(applied.lines().anyMatch(line -> line.equals("#define " + gate)), "override failed " + gate);
            for (var other : gates)
                if (!other.equals(gate)) require(applied.lines().anyMatch(line -> line.matches("//+#define " + other)),
                    "override changed unrelated gate " + other);
        }
        System.out.println("exact original boolean/string union plus three gates; all helpers included; independent overrides");
    }
}
'''


class CombinedTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = c.reflection.archive_members(ROOT / c.BASE)
        cls.disabled = c.patch_members(cls.original, M)
        cls.rm, cls.pm, cls.vm = c.load_tracks(M, ROOT)

    def test_explicit_scope_license_and_source_inventory(self):
        self.assertEqual(531, len(self.original))
        self.assertEqual(self.original['LICENSE'], self.disabled['LICENSE'])
        self.assertEqual(7, len(set(self.disabled)-set(self.original)))
        self.assertEqual(set(M['disabled_member_sha256']),
                         {n for n in self.disabled if n not in self.original or self.disabled[n] != self.original[n]})
        self.assertEqual(self.original, c.patch_members(self.original, M, original_arm=True))

    def test_every_original_member_byte_exact_with_all_private_switches_off(self):
        for name, data in self.original.items():
            restored = private_off(self.disabled[name]) if name in (
                c.reflection.SPECULAR, c.procedural.PROGRAM, c.volumetric.CUMULUS) else self.disabled[name]
            if name == c.SETTINGS:
                for manifest in (self.rm, self.pm, self.vm):
                    restored = restored.replace(manifest['option_declaration'].encode(), b'', 1)
            self.assertEqual(data, restored, name)

    def test_controls_fail_closed_changed_inputs_unknown_members_and_pins(self):
        for controls in (('lighting',), ('reflection', 'reflection')):
            with self.assertRaises(ValueError): c.patch_members(self.original, M, controls)
        for name in ('LICENSE', c.SETTINGS, 'shaders/image/noise.png'):
            with self.assertRaises(ValueError): c.patch_members(self.original | {name: b'changed'}, M)
        with self.assertRaises(ValueError): c.patch_members(self.original | {'unexpected.glsl': b''}, M)
        with self.assertRaises(ValueError): c.patch_members(self.disabled, M)
        for change in (lambda m: m['tracks'][0].update(manifest_sha256='bad'),
                       lambda m: m['tracks'][1].update(builder_sha256='bad'),
                       lambda m: m['tracks'][2].update(manifest_path='../arbitrary'),
                       lambda m: m['disabled_member_sha256'].update({c.CORE_HEADER: 'bad'}),
                       lambda m: m.update(core_version=3)):
            bad = copy.deepcopy(M); change(bad)
            with self.assertRaises(ValueError): c.patch_members(self.original, bad)
        with self.assertRaises(ValueError): c.build(ROOT / 'outside.zip')
        with self.assertRaises(ValueError): c.patch_members(self.original, M, ('reflection',), original_arm=True)

    def test_eight_combinations_and_archive_determinism(self):
        for bits in itertools.product((False, True), repeat=3):
            enabled = tuple(n for n, active in zip(c.TRACKS, bits) if active)
            files = c.patch_members(self.original, M, enabled)
            for switch, active in zip(c.SWITCHES, bits):
                line = (('#define ' if active else '//#define ') + switch + '\n').encode()
                self.assertEqual(1, files[c.SETTINGS].count(line))
            output = ROOT / 'build' / ('combined-fixture-' + ''.join(map(lambda v: str(int(v)), bits)) + '.zip')
            metadata = c.build(output, enabled=enabled)
            first = c.sha256(output.read_bytes())
            self.assertEqual(metadata, c.build(output, enabled=tuple(reversed(enabled))))
            self.assertEqual(first, c.sha256(output.read_bytes()))
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(set(files) | {c.METADATA}, set(archive.namelist()))
                self.assertEqual(self.original['LICENSE'], archive.read('LICENSE'))
                for name, data in files.items(): self.assertEqual(data, archive.read(name))

    def test_off_every_entry_program_matches_original_preprocessed_tokens(self):
        self.assertIsNotNone(shutil.which('clang'))
        with tempfile.TemporaryDirectory(prefix='shaderlod-combined-off-') as temporary:
            directory = Path(temporary)
            write_sources(self.original, directory / 'original')
            write_sources(self.disabled, directory / 'combined')
            entries = [n for n in self.original if re.fullmatch(r'shaders/world[01-]+/[^/]+\.(?:fsh|vsh)', n)]
            self.assertGreater(len(entries), 100)
            for name in entries:
                with self.subTest(entry=name):
                    if name.endswith('/composite3.fsh'):
                        # Pinned upstream c3_taau_prep has an unmatched trailing
                        # #endif; this dormant stage is not changed by any track.
                        # Preserve/error-record it; do not relabel a C error as pass.
                        self.assertEqual(self.original[name], self.disabled[name])
                        self.assertEqual(self.original['shaders/program/c3_taau_prep.fsh'],
                                         self.disabled['shaders/program/c3_taau_prep.fsh'])
                        with self.assertRaisesRegex(AssertionError, '#endif without #if'):
                            preprocess(directory / 'original', name)
                        with self.assertRaisesRegex(AssertionError, '#endif without #if'):
                            preprocess(directory / 'combined', name)
                        continue
                    self.assertEqual(preprocess(directory / 'original', name), preprocess(directory / 'combined', name))

    def test_single_feature_all_entry_programs_match_standalone_and_no_duplicate_core_uniforms(self):
        standalone = {
            'reflection': c.reflection.patch_members(self.original, self.rm, 'disabled'),
            'procedural': c.procedural.patch_members(self.original, self.pm, 'disabled'),
            'volumetric': c.volumetric.patch_members(self.original, self.vm, 'disabled'),
        }
        entry = self.rm['added_members'][0]
        standalone['reflection'][entry['path']] = (ROOT / entry['source_path']).read_bytes()
        with tempfile.TemporaryDirectory(prefix='shaderlod-combined-isolated-') as temporary:
            directory = Path(temporary)
            write_sources(self.disabled, directory / 'combined')
            entries = [n for n in self.original if re.fullmatch(r'shaders/world[01-]+/[^/]+\.(?:fsh|vsh)', n)
                       and not n.endswith('/composite3.fsh')]
            for track, switch in zip(c.TRACKS, c.SWITCHES):
                write_sources(standalone[track], directory / track)
                for name in entries:
                    with self.subTest(track=track, entry=name):
                        a = preprocess(directory / track, name, (switch,))
                        b = preprocess(directory / 'combined', name, (switch,))
                        self.assertEqual(a, b)
            # Combined water actually expands both helpers; each shared uniform once.
            for name in entries:
                tokens = preprocess(directory / 'combined', name, c.SWITCHES)
                text = ' '.join(tokens)
                for field in ('shaderlodBridgeVersion', 'shaderlodEnabled', 'shaderlodNear', 'shaderlodFar', 'shaderlodTransition'):
                    self.assertLessEqual(len(re.findall(r'uniform (?:int|float) '+field+r' ;', text)), 1, name+' '+field)
            water = ' '.join(preprocess(directory / 'combined', 'shaders/world0/gbuffers_water.fsh', c.SWITCHES))
            self.assertIn('shaderlod_procedural_normal', water)
            self.assertIn('shaderlod_reflection_depth_trace', water)

    def test_actual_full_source_pinned_iris_lexer_exact_option_union_and_overrides(self):
        pin = self.rm['iris_option_discovery']; jar = ROOT / pin['jar_path']
        self.assertEqual(pin['jar_sha256'], c.sha256(jar.read_bytes()))
        with zipfile.ZipFile(jar) as z:
            self.assertEqual(pin['class_sha256'], c.sha256(z.read(pin['class_path'])))
        deps = []
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar',
                        'it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar'):
            found = list((Path.home()/'.gradle/caches/modules-2/files-2.1').glob(pattern))
            self.assertEqual(1, len(found)); deps.extend(found)
        with tempfile.TemporaryDirectory(prefix='shaderlod-combined-iris-') as temporary:
            directory = Path(temporary)
            write_sources(self.original, directory / 'original')
            write_sources(self.disabled, directory / 'combined')
            source = directory / 'CombinedLexer.java'; source.write_text(LEXER)
            command = ['java', '--class-path', os.pathsep.join(map(str, (jar, *deps))), str(source),
                       str(directory / 'original'), str(directory / 'combined'), *c.SWITCHES]
            result = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout+result.stderr)
            self.assertIn('all helpers included; independent overrides', result.stdout)


if __name__ == '__main__': unittest.main()
