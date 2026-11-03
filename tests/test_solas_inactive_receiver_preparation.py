"""Actual pinned Solas producer regression; no driver, quality or speed claim."""
import copy
import hashlib
import json
from pathlib import Path
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from build_supported_pack_adapters import apply, read_archive

TERRAIN = 'shaders/programs/gbuffers_terrain.glsl'
GUARD = '    if (shaderlodSolasReceiverPermission == 1 && shaderlod_optimization_enabled(shaderlodOptShadowReceiverFootprint)) {'
COMMENT = '    // Draw-uniform gate: derivatives never depend on per-fragment eligibility or LOD.'
LEGACY_BODY_SHA = '7907dae93ebcce2871d288302425eeb9dcc016983c7e8dd03548e66fc1e1472e'
CONSUMER_SHA = 'acc3a6a2df19902528232271b6f0dd20d4189930d1726bcd745ed781b964aa64'


class SolasInactiveReceiverPreparationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((ROOT / 'patches/adapters/solas-shader.json').read_text())
        cls.stock = read_archive(ROOT / 'dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip')
        cls.generated = apply(cls.stock, cls.manifest)
        cls.patch = next(p for p in cls.manifest['patches']
                         if p['path'] == TERRAIN and 'vec3 shaderlodReceiverWorldNormal' in p['after'])

    def legacy_preparation(self):
        head, guarded = self.patch['after'].split('\n' + COMMENT + '\n' + GUARD + '\n', 1)
        body, tail = guarded.rsplit('\n    }\n#endif', 1)
        self.assertTrue(all(line.startswith('    ') for line in body.splitlines()))
        original = '\n'.join(line[4:] for line in body.splitlines())
        self.assertEqual(LEGACY_BODY_SHA, hashlib.sha256(original.encode()).hexdigest(),
                         'Enabled receiver calculations and order must remain byte-exact')
        return head + '\n' + original + '\n#endif' + tail

    def test_actual_producer_changes_only_guarded_terrain_preparation(self):
        previous = copy.deepcopy(self.manifest)
        index = self.manifest['patches'].index(self.patch)
        previous['patches'][index]['after'] = self.legacy_preparation()
        legacy = apply(self.stock, previous)
        self.assertEqual({TERRAIN}, {name for name in legacy if legacy[name] != self.generated[name]})
        shader = self.generated[TERRAIN].decode()
        self.assertEqual(1, shader.count(GUARD))
        self.assertIn(self.patch['after'], shader)
        self.assertEqual(legacy[TERRAIN].decode(), shader.replace(self.patch['after'], self.legacy_preparation(), 1))

    def test_gate_is_draw_uniform_and_disabled_outputs_keep_native_fallback(self):
        self.legacy_preparation()
        helper = self.generated['shaders/lib/shaderlod/solas_receiver_footprint.glsl'].decode()
        self.assertIn('uniform int shaderlodSolasReceiverPermission = 0;', helper)
        self.assertIn('uniform int shaderlodOptShadowReceiverFootprint;', helper)
        self.assertIn('vec2 shaderlodSolasReceiverDx = vec2(0.0),shaderlodSolasReceiverDy = vec2(0.0);', helper)
        self.assertIn('bool shaderlodSolasReceiverEligible = false;', helper)
        flags = self.generated['shaders/include/shaderlod/optimization_flags.glsl'].decode()
        function = re.search(r'bool shaderlod_optimization_enabled\(int flag\)\s*\{([^}]+)\}', flags).group(1)
        self.assertEqual(
            'returnshaderlodOptimizationFlagsVersion==1&&flag==1&&shaderlodBridgeVersion==2&&'
            'shaderlodEnabled==1&&shaderlodPreset>=0&&shaderlodPreset<=2;',
            re.sub(r'\s+', '', function))
        # The gate contains only permission and protocol uniforms. Fragment material,
        # distance, eligibility and gradients remain inside, never in the gate.
        consumer = next(p for p in self.manifest['patches'] if 'float shaderlodReceiverWeight =' in p['after'])
        self.assertEqual(CONSUMER_SHA, hashlib.sha256(consumer['after'].encode()).hexdigest())
        self.assertIn('shaderlodSolasReceiverEligible && !shaderlodTemporalPair', consumer['after'])
        self.assertTrue(consumer['after'].endswith('    for (int i = 0; i < shadowSamples; i++) {'))

    def test_changed_original_and_helper_pins_still_refuse(self):
        changed = dict(self.stock)
        changed[TERRAIN] += b'\n'
        with self.assertRaisesRegex(ValueError, 'Pinned shader source identity changed'):
            apply(changed, self.manifest)
        bad = copy.deepcopy(self.manifest)
        bad['additions']['shaders/lib/shaderlod/solas_receiver_footprint.glsl']['sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'Adapter helper changed'):
            apply(self.stock, bad)


if __name__ == '__main__':
    unittest.main()
