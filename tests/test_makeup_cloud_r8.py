"""Pinned lossless red texture storage, native kernels and diagnostic scope."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'scripts'), str(ROOT / 'tests')]
import build_makeup_cloud_r8 as m
import test_makeup_unused_sky as old


class CloudR8Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.native = m.read_archive(old.BASE)
        cls.files = m.patch(cls.native)

    def test_exact_red_and_native_kernel_ancestry(self):
        rgba = m.decoded_cloud_rgba(self.native[m.TEXTURE])
        self.assertEqual(self.files[m.RAW + '_r.bin'], rgba[::4])
        self.assertEqual(len(self.files[m.RAW + '_r.bin']), 512 * 512)
        self.assertEqual(self.files[m.RAW + '_rgba.bin'], rgba)
        for name, data in self.native.items():
            if name.endswith(('.fsh', '.vsh', '.glsl')) and name != m.CONFIG:
                self.assertEqual(self.files[name], data, name)
        self.assertEqual(self.files['LICENSE'], self.native['LICENSE'])
        self.assertEqual(self.files[m.TEXTURE], self.native[m.TEXTURE])
        self.assertNotIn('SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json', self.files)

    def test_properties_only_selected_deferred_binding_changes(self):
        props = self.files[m.PROPS].decode()
        self.assertIn('texture.gbuffers.gaux2 = textures/clouds_natural_512_R_8bit.png', props)
        self.assertIn('TEXTURE_2D R8 512 512 RED UNSIGNED_BYTE', props)
        self.assertIn('TEXTURE_2D RGBA8 512 512 RGBA UNSIGNED_BYTE', props)
        for suffix in ['_r.bin', '_rgba.bin']:
            self.assertEqual(json.loads(self.files[m.RAW + suffix + '.mcmeta']), {'texture': {'blur': True, 'clamp': False}})
        self.assertIn(f'#define {m.OPTION} 0 // [0 1 2]', self.files[m.CONFIG].decode())
        self.assertNotIn('uniform.float.shaderlod', props)
        # Properties preprocessing includes multiple dimension roots; do not
        # mistake their macros for the runtime dimension.
        self.assertNotIn('THE_END', props[props.index('#if (' + m.BASE):props.index('TEXTURE_2D R8')])

    def test_native_stages_and_diagnostic_compile(self):
        diagnostic = m.patch(self.native, True)
        with tempfile.TemporaryDirectory() as d:
            for files, debug in [(self.files, 0), (diagnostic, 1)]:
                for mode in [0, 1, 2]:
                    options = {m.OPTION: mode, m.DEBUG: debug, 'V_CLOUDS': 2, 'CLOUD_VOL_STYLE': 0, 'CLOUD_SPEED': 0}
                    source = old.preprocess(files, 'shaders/world0/deferred.fsh', native_options=options)
                    if not debug:
                        self.assertEqual(old.normalized(source), old.normalized(old.preprocess(self.native, 'shaders/world0/deferred.fsh', native_options=options)))
                    else:
                        self.assertIn('texture2D(shaderlodG08ReferenceNoise,', source)
                        self.assertIn('shaderlodG08R8Result = any(notEqual(', source)
                        self.assertIn('float shaderlodG08R8Result = 0.0;', source)
                    p = Path(d) / 'cloud.frag'
                    p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n' + source)
                    r = subprocess.run(['glslangValidator', '-S', 'frag', str(p)], text=True, capture_output=True)
                    self.assertEqual(r.returncode, 0, r.stdout + r.stderr)


if __name__ == '__main__':
    unittest.main()
