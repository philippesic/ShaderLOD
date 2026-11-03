"""Pinned G09 witness: native references, output ownership and real GLSL."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import build_g09_native_reuse_parity as m
from test_g09_native_reuse import pp

ROOT = Path(__file__).resolve().parents[1]


class G09ReuseParityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.native = m.reuse.read_archive(ROOT / 'dependencies/shortlist-test-inputs/originals/BSL_v10.1.8.zip')
        cls.files = m.patch(cls.native)

    def test_reference_is_native_kernel_with_names_only_changed(self):
        text = self.files[m.reuse.KERNEL].decode()
        reference = text[text.index('vec4 shaderlodG09ReferenceReadNormal'):].removesuffix('\n#endif\n')
        for name in ['ReadNormal', 'GetParallaxCoord', 'GetParallaxShadow']:
            reference = reference.replace('shaderlodG09Reference' + name, name)
        self.assertEqual(reference.strip(), self.native[m.reuse.KERNEL].decode().replace('\r\n', '\n').strip())

    def test_native_outputs_and_extra_attachment_are_separate(self):
        terrain = self.files[m.reuse.TERRAIN].decode()
        self.assertIn('newCoord = shaderlodG09ReferenceGetParallaxCoord', terrain)
        self.assertIn('parallaxShadow = shaderlodG09ReferenceGetParallaxShadow', terrain)
        self.assertIn('any(notEqual(newCoord, shaderlodG09CandidateCoord))', terrain)
        self.assertIn('surfaceDepth != shaderlodG09CandidateDepth', terrain)
        self.assertIn('parallaxShadow != shaderlodG09CandidateShadow', terrain)
        self.assertIn('/* DRAWBUFFERS:03678 */', terrain)
        self.assertIn('!defined MCBL_SS', self.files[m.reuse.KERNEL].decode())
        self.assertIn('const vec4 colortex8ClearColor = vec4(0.0, 0.0, 0.0, 0.0);', self.files['shaders/program/final.glsl'].decode())
        self.assertIn('shaderlodG09Mismatch > 0.0 ? 257.0', terrain)
        for n in self.native:
            if n.startswith('shaders/program/') and n not in [m.reuse.TERRAIN, 'shaders/program/final.glsl']:
                self.assertEqual(self.files[n], self.native[n])

    def test_all_kernel_modes_compile_with_actual_references(self):
        header = '''#version 120
#extension GL_ARB_shader_texture_lod : enable
uniform sampler2D normals;
varying vec4 vTexCoordAM;
varying vec4 vTexCoord;
varying vec3 viewVector;
varying vec2 dcdx,dcdy;
'''
        main = '''void main(){float a,b;vec2 p=GetParallaxCoord(vec2(.5),.5,a);
vec2 q=shaderlodG09ReferenceGetParallaxCoord(vec2(.5),.5,b);
float s=GetParallaxShadow(a,.5,p,vec3(0,1,0),mat3(1));
float t=shaderlodG09ReferenceGetParallaxShadow(b,.5,q,vec3(0,1,0),mat3(1));
gl_FragColor=vec4(p-q,a-b,s-t);}'''
        tool = shutil.which('glslangValidator')
        self.assertIsNotNone(tool)
        with tempfile.TemporaryDirectory() as tmp:
            for mode in range(5):
                text = pp(self.files[m.reuse.KERNEL].decode(), mode,
                          dict(PARALLAX=1, ADVANCED_MATERIALS=1, REFLECTION_SPECULAR=1))
                path = Path(tmp) / 'shader.frag'
                path.write_text(header + text + main)
                r = subprocess.run([tool, '-S', 'frag', str(path)], capture_output=True, text=True)
                self.assertEqual(r.returncode, 0, str(mode) + r.stdout + r.stderr)

    def test_diagnostic_final_directives_do_not_nest_native_comment(self):
        import re
        text = self.files['shaders/program/final.glsl'].decode()
        depth = 0
        for token in re.findall(r'/\*|\*/', text):
            depth += 1 if token == '/*' else -1
            self.assertIn(depth, [0, 1])
        self.assertEqual(depth, 0)
        self.assertEqual(text.count('colortex8Format = R16F'), 1)
        self.assertEqual(text.count('colortex8Format = RGB8'), 1)

    def test_integer_encoding_has_no_carry_or_half_precision_loss(self):
        # Maximum source omissions: one initial lookup plus both loop reuses.
        codes = {1 + saved + 16 * flags
                 for saved in range(4) for flags in range(16)}
        self.assertEqual(len(codes), 4 * 16)
        self.assertLess(max(codes), 257)


if __name__ == '__main__':
    unittest.main()
