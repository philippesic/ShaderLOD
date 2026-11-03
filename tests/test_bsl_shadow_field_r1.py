"""Source/resource/scalar checks. No Java/GLSL driver compilation or GPU gain."""
import importlib.util
import math
from pathlib import Path
import re
import subprocess
import unittest
from pinned_tools import java_tool

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('bsl_field', ROOT / 'scripts/build_bsl_shadow_field_r1.py')
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)


class BslShadowFieldTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = m.original_files()
        cls.patched = m.apply(cls.original)

    def test_pinned_scope_and_original_producer_preservation(self):
        self.assertEqual({n for n in self.original if self.original[n] != self.patched[n]},
            {m.TERRAIN, m.SHADOWS, m.SETTINGS, m.PROPERTIES, m.COMPOSITE})
        self.assertEqual(len(set(self.patched) - set(self.original)), 5)
        for name in ('shaders/program/shadow.glsl', 'shaders/program/shadowcomp.glsl',
            'shaders/lib/atmospherics/lightShafts.glsl', 'shaders/program/dh_terrain.glsl',
            'shaders/program/gbuffers_water.glsl', 'shaders/program/final.glsl'):
            self.assertEqual(self.original[name], self.patched[name])
        self.assertIn(b'BSL Shaders v10 Series by Capt Tatsu', self.patched[m.TERRAIN])

    def test_native_nine_and_continuation_byte_exact_after_undo_gate(self):
        shadow = self.patched[m.SHADOWS]
        restored = shadow.removeprefix(m.ALIAS).replace(m.INCLUDE, b'', 1).replace(m.PRIMARY_REPLACEMENT, m.PRIMARY, 1)
        self.assertEqual(restored, self.original[m.SHADOWS])
        self.assertIn(b'shadow0 = 9.0', m.PRIMARY_REPLACEMENT)
        self.assertNotIn(b'return ', m.PRIMARY_REPLACEMENT)
        self.assertNotIn(b'texture2D(', m.PRIMARY_REPLACEMENT)
        self.assertIn(b'shadow0 /= 9.0', shadow)
        self.assertEqual(self.patched[m.COMPOSITE].replace(m.ALIAS, b'', 1), self.original[m.COMPOSITE])

    def test_fixed_native_options_and_private_zero_source_control(self):
        settings = self.patched[m.SETTINGS]
        addition = (b'//ShaderLOD private current-shadow field; native options remain unchanged.\r\n'
            b'#define SHADERLOD_BSL_SHADOW_FIELD 0 // [0 1]\r\n\r\n')
        self.assertEqual(settings.replace(addition, b'', 1), self.original[m.SETTINGS])
        self.assertIn('!defined ADVANCED_MATERIALS', m.GUARD)
        self.assertIn('!defined DISTANT_HORIZONS', m.GUARD)
        control = (m.PATCH / 'field_control_r1.glsl').read_text()
        self.assertIn('shaderlodBslShadowFieldQualified == 1', control)
        self.assertNotIn('shaderlodPreset == 3', control)
        props = self.patched[m.PROPERTIES]
        self.assertIn(b'scale.shadowcomp1=0.0625 0.0 0.0', props)
        self.assertIn(b'iris.features.required=HIGHER_SHADOWCOLOR SEPARATE_HARDWARE_SAMPLERS', props)

    def test_complete_256_texel_reduction_not_four_probes_or_mips(self):
        producer = (m.PATCH / 'field_producer_r1.glsl').read_text()
        self.assertIn('for (int y = 0; y < 16; ++y)', producer)
        self.assertIn('for (int x = 0; x < 16; ++x)', producer)
        self.assertIn('texelFetch(shadowtex0, base + ivec2(x, y), 0)', producer)
        self.assertIn('shadowcolor2Mipmap = false', producer)
        self.assertIn('shadowcolor2Format = R32F', producer)
        self.assertIn('shadowcolor2Clear = false', producer)
        self.assertIn('vec4(2.0, 2.0, 0.0, 1.0)', producer)
        query = (m.PATCH / 'field_query_r1.glsl').read_text()
        self.assertEqual(query.count('texelFetch('), 5)
        self.assertNotIn('dFdx', query)
        self.assertNotIn('texture2D(', query)

    def test_support_all_native_offsets_and_bilinear_neighbours(self):
        source = self.original[m.SHADOWS].decode()
        body = source.split('vec2 shadowOffsets[9] = vec2[9](', 1)[1].split(');', 1)[0]
        offsets = [(float(x), float(y)) for x,y in re.findall(r'vec2\(\s*([-\d.]+),\s*([-\d.]+)\)', body)]
        self.assertEqual(len(offsets), 9)
        for ix in range(64):
            for iy in range(64):
                x, y = 16 + ix/4, 16 + iy/4
                cells = m.support_cells(x/2048, y/2048)
                self.assertLessEqual(len(cells), 4)
                for ox, oy in offsets:
                    bx, by = math.floor(x+ox-.5), math.floor(y+oy-.5)
                    for dx in (0,1):
                        for dy in (0,1):
                            self.assertIn(((bx+dx)//16, (by+dy)//16), cells)

    def test_one_interior_caster_invalidates_full_cell(self):
        # 4M-depth producer reference is synthetic scalar data, not an experiment.
        depth = [1.] * 2048**2
        depth[23*2048+23] = .25
        field = m.scalar_field(depth, 2048, 41)
        self.assertEqual(field[1*128+1], .25)
        self.assertFalse(m.scalar_hit(field, 24/2048, 24/2048, .5, 41))
        self.assertTrue(m.scalar_hit(field, 80/2048, 80/2048, .5, 41))

    def test_epoch_reserved_tile_borders_and_precision_fail_closed(self):
        field = [1.] * 16384; field[-1] = -42.
        self.assertFalse(m.scalar_hit(field, .5, .5, .5, 42))
        self.assertTrue(m.scalar_hit(field, .5, .5, .5, 41))
        self.assertFalse(m.scalar_hit(field, 2038/2048, 2038/2048, .5, 41))
        self.assertIsNone(m.support_cells(0, .5))
        self.assertIsNone(m.support_cells(float('nan'), .5))
        self.assertFalse(m.scalar_hit(field, .5, .5, 1-1/65536, 41))
        self.assertFalse(m.scalar_hit(field, .5, .5, .5, 16777215))
        field[-1] = 0
        self.assertFalse(m.scalar_hit(field, .5, .5, .5, 0))

    def test_full_resource_and_nine_pcf_cost_not_sixteen(self):
        value = m.cost(2048, 1920*1200, 1920*1200, 0)
        self.assertEqual(value['producer_raw_reads'], 4194048)
        self.assertEqual(value['physical_r32f_pair_bytes'], 33554432)
        self.assertEqual(value['useful_bytes'], 65536)
        self.assertEqual(value['native_primary_expressions'], 9*1920*1200)
        self.assertEqual(value['tile_min_queries_per_eligible_receiver'] + value['epoch_queries_per_eligible_receiver'], 5)
        self.assertGreater(value['optimistic_hit_fraction_threshold'], .75)
        self.assertFalse(value['gpu_gain_measured'])

    def test_actual_iris_legal_graph_and_missing_producer_uniform_seam(self):
        # Inspect the hash-pinned real Iris dependency, not an absent report copy.
        self.assertEqual(m.IRIS_SHA, m.digest(m.IRIS.read_bytes()))
        def inspect(name):
            return subprocess.run([java_tool('javap'), '-classpath', str(m.IRIS),
                '-p', '-c', 'net.irisshaders.iris.' + name],
                check=True, capture_output=True, text=True).stdout
        shadow = inspect('shadows.ShadowCompositeRenderer')
        targets = inspect('shadows.ShadowRenderTargets')
        samplers = inspect('samplers.IrisSamplers')
        uniforms = inspect('uniforms.CommonUniforms')
        create = shadow.split('private net.irisshaders.iris.gl.program.Program createProgram(', 1)[1].split('private ', 1)[0]
        self.assertIn('CommonUniforms.addDynamicUniforms', create)
        self.assertNotIn('CommonUniforms.generalCommonUniforms', create)
        self.assertNotIn('CommonUniforms.addNonDynamicUniforms', create)
        self.assertIn('ViewportData.scale', shadow)
        self.assertIn('FullScreenQuadRenderer.renderQuad', shadow)
        self.assertIn('FeatureFlags.HIGHER_SHADOWCOLOR', targets)
        self.assertIn('RenderTarget$Builder.setDimensions', targets)
        self.assertIn('String shadowtex0HW', samplers)
        self.assertIn('String shadowtex0', samplers)
        dynamic = uniforms.split('public static void addDynamicUniforms(', 1)[1].split('public static ', 1)[0]
        self.assertNotIn('generalCommonUniforms', dynamic)
        staging = (m.PATCH / 'BslShadowFieldUniformStaging.java').read_text()
        self.assertIn('CommonUniforms.generalCommonUniforms(fieldBuilder, notifier, directives)', staging)
        self.assertIn('SystemTimeUniforms.addSystemTimeUniforms(fieldBuilder)', staging)
        clears = inspect('targets.ClearPassCreator')
        self.assertIn('SamplingSettings.getClear', clears)
        self.assertIn('createFramebufferWritingToAlt', clears)
        self.assertIn('createFramebufferWritingToMain', clears)
        self.assertIn('ifnull', clears)  # Lazy unused higher targets are skipped.

    def test_pin_and_output_escapes_refused(self):
        modified = dict(self.original); modified[m.SHADOWS] += b'\n'
        with self.assertRaises(ValueError): m.apply(modified)
        with self.assertRaises(ValueError): m.build(ROOT / 'field.zip', 1)


if __name__ == '__main__':
    unittest.main()
