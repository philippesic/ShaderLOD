"""Authentic MakeUp End candidate checks; no driver, image or speed evidence."""
from pathlib import Path
import copy
import json
import math
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from prepare_makeup_end_cloud_budget import (
    ADAPTER, RECIPE, END_MEMBER, ENTRY_MEMBER, COUNT_INSERT, GUARD,
    candidate_manifest, prepare,
)
from build_supported_pack_adapters import apply, build, read_archive, sha256, shader_source_digest, CATALOG, RESOURCE_ROOT

BASE = ROOT / 'dev/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip'


class MakeUpEndCloudTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not BASE.is_file():
            raise unittest.SkipTest('Authentic pinned MakeUp archive unavailable')
        cls.current = json.loads((ROOT / RECIPE).read_bytes())
        cls.original = read_archive(BASE)
        cls.installed = END_MEMBER in cls.current['source_members']
        if cls.installed:
            # Exercise the actual installed recipe and compare it to its unchanged
            # Overworld predecessor; do not prepare a second End port.
            cls.candidate = copy.deepcopy(cls.current)
            cls.current['source_members'].pop(END_MEMBER)
            cls.current['source_members'].pop(ENTRY_MEMBER)
            cls.current['patches'] = [p for p in cls.current['patches'] if p['path'] != END_MEMBER]
            cls.current['ported_consumers'].pop('end_clouds')
        else:
            cls.candidate = candidate_manifest(cls.current)
        cls.assembled = apply(cls.original, cls.current)
        cls.changed = apply(cls.original, cls.candidate)

    def test_exact_consumer_scope_and_native_body_recovery(self):
        self.assertEqual(set(self.assembled), set(self.changed))
        self.assertEqual([n for n in self.changed if self.changed[n] != self.assembled[n]], [END_MEMBER])
        member = self.changed[END_MEMBER]
        self.assertEqual(member.replace(b'#include "/lib/shaderlod/adapter.glsl"\n\n', b'', 1)
                         .replace(COUNT_INSERT.encode(), b'', 1), self.original[END_MEMBER])
        self.assertLess(member.index(b'samples = shaderlod_makeup_volume_budget(samples)'),
                        member.index(b'vec3 increment ='))
        for name in self.current['additions']:
            self.assertEqual(self.changed[name], self.assembled[name])
        entry = self.original[ENTRY_MEMBER].decode()
        self.assertIn('#define THE_END', entry)
        self.assertIn('#define DEFERRED_SHADER', entry)
        self.assertIn('#include "/common/deferred_fragment.glsl"', entry)
        common = self.original['shaders/common/deferred_fragment.glsl'].decode()
        self.assertIn('#include "/lib/volumetric_clouds_end.glsl"', common)
        self.assertIn('get_end_cloud(eyeDirection, blockColor.rgb, bright, dither, cameraPosition, CLOUD_STEPS_AVG)', common)
        self.assertEqual(self.candidate['additions'], self.current['additions'])

    def test_end_apply_route_is_not_overworld_renderer_route(self):
        route = self.candidate['ported_consumers']['end_clouds']
        self.assertFalse(route['performance_native_renderer_bypasses_shader_budget'])
        self.assertFalse(route['dimension_transition_reselection_verified'])
        paths = [
            'src/main/java/com/pp/shaderlod/integration/makeupquad/MakeUpQuadRuntime.java',
            'fabric/src/main/java/com/pp/shaderlod/integration/makeupquad/MakeUpQuadRuntime.java',
            'ports/26.1.2/fabric/src/main/java/com/pp/shaderlod/integration/makeupquad/MakeUpQuadRuntime.java',
            'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/integration/makeupquad/MakeUpQuadRuntime.java',
        ]
        for path in paths:
            source = (ROOT / path).read_text().split('boolean nativeGraphAvailable()', 1)[1]
            self.assertRegex(source, r'client\.level\.dimension\(\)\s*!=\s*Level\.OVERWORLD')
        selection = (ROOT / 'src/main/java/com/pp/shaderlod/packs/PackSelectionService.java').read_text()
        self.assertIn('preset == LodPreset.PERFORMANCE && available && RECIPES.contains(adapterId)', selection)

    def test_real_preprocessor_guards_and_unchanged_helper_fallbacks(self):
        clang = shutil.which('clang')
        if not clang:
            self.skipTest('C preprocessor unavailable; GLSL driver is not being tested')
        def expand(name):
            text = self.changed[name].decode()
            return re.sub(r'#include "(/[^\"]+)"',
                          lambda m: expand('shaders' + m.group(1)), text)
        source = expand(END_MEMBER)
        # Model Iris option substitution of this exact declaration, not a second definition.
        enabled = source.replace('#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]',
                                 '#define SHADERLOD_REFINEMENT_ADAPTER 1 // [0 1]')
        self.assertNotEqual(source, enabled)
        cases = [((), True), (('NETHER',), False), (('UNKNOWN_DIM',), False),
                 (('DISTANT_HORIZONS',), False), (('VOXY',), False)]
        for excluded, expected in cases:
            with self.subTest(excluded=excluded):
                prefix = '#define THE_END\n#define DEFERRED_SHADER\n'
                prefix += ''.join('#define ' + name + '\n' for name in excluded)
                result = subprocess.run([clang, '-E', '-P', '-x', 'c', '-'], input=prefix + enabled,
                                        text=True, capture_output=True, check=True).stdout
                self.assertEqual('samples = shaderlod_makeup_volume_budget(samples);' in result, expected)
        for prefix, text in [('#define DEFERRED_SHADER\n', enabled),
                             ('#define THE_END\n', enabled),
                             ('#define THE_END\n#define DEFERRED_SHADER\n', source)]:
            result = subprocess.run([clang, '-E', '-P', '-x', 'c', '-'], input=prefix + text,
                                    text=True, capture_output=True, check=True).stdout
            self.assertNotIn('samples = shaderlod_makeup_volume_budget(samples);', result)
        # Shared helper remains exact; existing bridge/Off/Quality/invalid fallback is present.
        helper = self.changed['shaders/lib/shaderlod/uniform_sample_budget.glsl'].decode()
        self.assertIn('bridge != 2 || enabled != 1 || preset < 0 || preset > 2 ||', helper)
        self.assertIn('original < minimum || minimum < 1 || original > 65536) return original;', helper)

    def test_native_slab_quadrature_and_source_work_counts(self):
        # Independent constant-medium quadrature: total path length is invariant when
        # every sample lies inside a slab and cloud_thickness does not cap increments.
        # Native first-contact and nonconstant density can still change; no image equality.
        for native in range(7, 17):  # actual selectable CLOUD_STEPS_AVG domain
            for retained in (0.875, 0.75):
                count = max(4, math.ceil(native * retained))
                for length in (10.0, 80.0, 400.0):
                    stride = length / count
                    for dither in (0.0, 0.25, 0.999):
                        positions = [(i + dither) * stride for i in range(count)]
                        self.assertGreaterEqual(positions[0], 0.0)
                        self.assertLess(positions[-1], length)
                        self.assertAlmostEqual(sum([stride] * count), length)
                        # Dither shifts strata without truncating the native nominal interval.
                        self.assertAlmostEqual(stride * count, (length / native) * native)
        self.assertEqual(math.ceil(10 * .875), 9)
        self.assertEqual(math.ceil(10 * .75), 8)
        self.assertEqual(2 * (10 - math.ceil(10 * .875)), 2)

    def test_pin_drift_and_reentry_refuse(self):
        for member in (END_MEMBER, ENTRY_MEMBER):
            bad = dict(self.original)
            bad[member] += b'\n'
            with self.assertRaisesRegex(ValueError, 'Pinned shader source identity changed'):
                apply(bad, self.candidate)
        with self.assertRaisesRegex(ValueError, 'already pinned'):
            candidate_manifest(self.candidate)
        wrong = copy.deepcopy(self.current)
        wrong['version'] = '9.5h'
        with self.assertRaises(ValueError):
            candidate_manifest(wrong)

    def test_real_artifact_overlay_legacy_and_no_overwrites(self):
        protected = {p: (ROOT / p).read_bytes() for p in (RECIPE, RESOURCE_ROOT + RECIPE, CATALOG)}
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'candidate'
            if self.installed:
                # Installed source uses the real builder and packaged/catalog joins.
                self.assertEqual(protected[RECIPE], protected[RESOURCE_ROOT + RECIPE])
                catalog = protected[CATALOG].decode()
                self.assertIn(shader_source_digest(self.changed), catalog)
                self.assertIn(shader_source_digest(self.assembled), catalog.split('LEGACY_ADAPTER_DIGESTS')[1])
                certificate = (ROOT / 'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/integration/bindingcompat/MakeUpSourceCertificate.java').read_text()
                self.assertIn('RECIPE_SHA="' + sha256(protected[RECIPE]) + '"', certificate)
                metadata = build(BASE, Path(temporary) / 'installed.zip', ROOT / RECIPE)
                self.assertEqual(metadata['adapter_shader_source_digest'], shader_source_digest(self.changed))
                self.assertTrue(all(value is False for value in metadata['validation'].values()))
                with self.assertRaisesRegex(ValueError, 'already pinned'):
                    prepare(BASE, output)
                self.assertFalse(output.exists())
                for name, value in protected.items():
                    self.assertEqual((ROOT / name).read_bytes(), value)
                return
            receipt = prepare(BASE, output)
            self.assertTrue(receipt['source_only'])
            self.assertEqual(receipt['changed_from_current_adapter'], [END_MEMBER])
            self.assertEqual(set(receipt['overlay']), set(protected))
            self.assertEqual((output / 'overlay' / RECIPE).read_bytes(),
                             (output / 'overlay' / (RESOURCE_ROOT + RECIPE)).read_bytes())
            catalog = (output / 'overlay' / CATALOG).read_text()
            self.assertIn(receipt['candidate_digest'], catalog)
            self.assertIn(receipt['current_adapter_digest'], catalog.split('LEGACY_ADAPTER_DIGESTS')[1])
            for name, before in protected.items():
                self.assertEqual((ROOT / name).read_bytes(), before)
            self.assertTrue(all(value is False for value in receipt['validation'].values()))
            saved = (output / 'receipt.json').read_bytes()
            with self.assertRaisesRegex(ValueError, 'already exists'):
                prepare(BASE, output)
            self.assertEqual((output / 'receipt.json').read_bytes(), saved)
            bad = Path(temporary) / 'wrong.zip'
            bad.write_bytes(BASE.read_bytes() + b'wrong')
            with self.assertRaisesRegex(ValueError, 'archive hash'):
                prepare(bad, Path(temporary) / 'refused')
            self.assertFalse((Path(temporary) / 'refused').exists())


if __name__ == '__main__':
    unittest.main()
