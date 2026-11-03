import importlib.util
import json
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('solas_speed_r1', ROOT / 'scripts/build_solas_speed_replication_r1.py')
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class SolasSpeedR1(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        assert builder.sha(builder.ORIGINAL.read_bytes()) == builder.ORIGINAL_SHA
        with zipfile.ZipFile(builder.ORIGINAL) as z:
            cls.original = {n: z.read(n) for n in z.namelist()}

    def test_native_bytes_and_options_preserved(self):
        baseline = builder.prepare(self.original, 'metadata-baseline')
        self.assertEqual([n for n in baseline if baseline[n] != self.original[n]], ['shaders/shaders.properties'])
        candidate = builder.prepare(self.original, 'rough-gather')
        for n in self.original:
            if n not in ('shaders/shaders.properties', 'shaders/lib/pbr/simpleReflection.glsl'):
                self.assertEqual(candidate[n], self.original[n], n)
        for line in self.original['shaders/shaders.properties'].splitlines():
            if line.startswith(b'profile.'):
                self.assertIn(line, candidate['shaders/shaders.properties'])
        self.assertIn(b'#define SHADERLOD_ROUGH_GATHER_R1 0', candidate['shaders/lib/shaderlod/rough_gather_r1.glsl'])

    def test_metadata_mac_and_nonmac_fixture(self):
        # Authentic packaged native profile, replayed through standalone CPP only.
        # This is source evidence, not a captured runtime map.
        record=ROOT/'src/main/resources/assets/shaderlod/solasquad/native428.json'
        self.assertEqual('261a3fbc6b6b6ff58a270ae4727ddec2c7ddcf7c642738c4454fa1f1b6eb4113',builder.sha(record.read_bytes()))
        values=json.loads(record.read_text())
        prefix = '#version 330 compatibility\n#define MC_VERSION 12101\n'
        prefix += ''.join('#define ' + k + (' 1' if v == 'true' else ' ' + v) + '\n'
                          for k, v in values.items() if v != 'false')
        baseline = builder.prepare(self.original, 'metadata-baseline')
        def replay(body, mac):
            # Iris permits decorative # comments; remove only those for GLSL CPP fixture.
            body = '\n'.join(l for l in body.decode().splitlines()
                             if not l.startswith('#') or re.match(r'#(?:if|ifdef|ifndef|else|elif|endif|define|undef)\b', l))
            return self.glsl(prefix + ('#define MC_OS_MAC\n' if mac else '') + body, True)
        native_nonmac = replay(self.original['shaders/shaders.properties'], False)
        fixed_nonmac = replay(baseline['shaders/shaders.properties'], False)
        self.assertEqual(self.tokens(native_nonmac), self.tokens(fixed_nonmac))
        mac = replay(baseline['shaders/shaders.properties'], True)
        self.assertNotIn('image.', mac)
        self.assertNotIn('CUSTOM_IMAGES', mac)
        compact = re.sub(r'\s+', '', mac)
        for key in ('shadowcomp', 'world0/shadowcomp', 'world-1/shadowcomp', 'world1/shadowcomp'):
            self.assertIn('program.' + key + '.enabled=false', compact)

    def test_rough_helper_parser_and_optionoff_tokens(self):
        candidate = builder.prepare(self.original, 'rough-gather')
        native = self.original['shaders/lib/pbr/simpleReflection.glsl'].decode()
        source = candidate['shaders/lib/pbr/simpleReflection.glsl'].decode()
        header = '''#version 330 compatibility
#define REFLECTION 2
#define GENERATED_SPECULAR
#define REFLECTION_ROUGH
#define REFLECTION_SKY_FALLOFF 1
#define WATER_NORMALS 0
#define OVERWORLD
#define TAA
uniform sampler2D noisetex, depthtex0, colortex0, colortex6;
uniform float viewWidth, viewHeight, blindFactor;
uniform int frameCounter, isEyeInWater;
uniform mat4 gbufferProjection;
float pow2(float x) { return x*x; }
vec3 ToWorld(vec3 p) { return p; }
vec3 getAtmosphere(vec3 v, vec3 w, inout float h) { return v; }
vec4 Raytrace(sampler2D t, vec3 v, vec3 n, float d, inout float b, int f, float a, float c, float i) { return vec4(v,1); }
'''
        for option in (0, 1):
            helper = builder.HELPER.replace('SHADERLOD_ROUGH_GATHER_R1 0 //', 'SHADERLOD_ROUGH_GATHER_R1 ' + str(option) + ' //')
            expanded = source.replace('#include "/lib/shaderlod/rough_gather_r1.glsl"', helper)
            text = header + expanded + '\nvoid main(){vec3 c=vec3(0);getReflection(c,vec3(1),vec3(1),vec3(1),0.5,1.0);gl_FragColor=vec4(c,1);}\n'
            self.glsl(text, False)
            if option == 0:
                self.assertEqual(self.tokens(self.glsl(header + native, True)), self.tokens(self.glsl(header + expanded, True)))

    def test_pin_and_overwrite_refusals_and_determinism(self):
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            a = builder.build(builder.ORIGINAL, d / 'a.zip', 'rough-gather', d / 'a.json')
            b = builder.build(builder.ORIGINAL, d / 'b.zip', 'rough-gather', d / 'b.json')
            self.assertEqual(a['output_sha256'], b['output_sha256'])
            with self.assertRaises(ValueError):
                builder.build(builder.ORIGINAL, d / 'a.zip', 'rough-gather', d / 'c.json')
            bad = d / 'bad.zip'
            bad.write_bytes(builder.ORIGINAL.read_bytes() + b'bad')
            with self.assertRaises(ValueError):
                builder.build(bad, d / 'c.zip', 'rough-gather', d / 'c.json')

    def glsl(self, source, preprocess):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'fixture.frag'
            path.write_text(source)
            command = ['glslangValidator', '-S', 'frag'] + (['-E'] if preprocess else []) + [str(path)]
            r = subprocess.run(command, text=True, capture_output=True)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            return r.stdout

    @staticmethod
    def tokens(source):
        return re.findall(r'[A-Za-z_]\w*|\d+(?:\.\d*)?|[^\s]', source)


if __name__ == '__main__':
    unittest.main()
