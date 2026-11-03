"""Pinned source/math audit for the rejected cloud-resolution extension.

No candidate, shader compiler, client, world operation or GPU measurement.
"""
from pathlib import Path
import hashlib
import re
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'dependencies/upstream/photon_v1.3b.zip'
BASE_SHA256 = '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
PINS = {
    'LICENSE': '514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5',
    'shaders/shaders.properties': 'f8cc8df60f930612e8817e4176a34886c1a9de778c15427a5468ecbc4f71ee9a',
    'shaders/settings.glsl': '93401d268fe859cc1df81929ad41156757a98db80f80cf32af80d9cdca96c394',
    'shaders/program/d1_clouds.fsh': '1f0dd330b837becff393380283eb767e3a55fe48e9b04705bf825fde535d0cae',
    'shaders/program/d2_clouds_upscaling.fsh': '2b026e4bf367527213264b333b267c649a1ac85ba3105155b71085878c3c204a',
    'shaders/include/utility/checkerboard.glsl': '752e3caec5dfdcf07dbe580a5535b4617fe8d489ef7eb7135737e602db83d35e',
    'shaders/include/buffers.glsl': '203cd05b25adec942bce5ab36fd0b7a9719a4f7790e3fe6983b59a8644124f9f',
}


class PhotonVolumeResolutionAudit(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = {}
        with zipfile.ZipFile(BASE) as archive:
            for name in PINS:
                cls.files[name] = archive.read(name)

    def text(self, name):
        return self.files['shaders/' + name].decode()

    def test_exact_source_and_license(self):
        self.assertEqual(hashlib.sha256(BASE.read_bytes()).hexdigest(), BASE_SHA256)
        for name, pin in PINS.items():
            with self.subTest(member=name):
                self.assertEqual(hashlib.sha256(self.files[name]).hexdigest(), pin)

    def test_native_default_is_supported_coarsest_divisor(self):
        settings = self.text('settings.glsl')
        match = re.search(r'#define CLOUDS_TEMPORAL_UPSCALING (\d+) // \[([^]]+)\]', settings)
        self.assertIsNotNone(match)
        default, choices = int(match[1]), list(map(int, match[2].split()))
        self.assertEqual(choices, [1, 2, 3, 4])
        self.assertEqual(default, max(choices))
        properties = self.text('shaders.properties')
        branch = properties.split('#if CLOUDS_TEMPORAL_UPSCALING == 1', 1)[1].split('#endif', 1)[0]
        self.assertEqual(re.findall(r'size.buffer.colortex9\s*=\s*([\d.]+)\s+([\d.]+)', branch),
                         [('1.0', '1.0'), ('0.5', '0.5'), ('0.333333', '0.333333'), ('0.25', '0.25')])
        self.assertEqual(re.findall(r'size.buffer.colortex10\s*=\s*([\d.]+)\s+([\d.]+)', branch),
                         [('1.0', '1.0'), ('0.5', '0.5'), ('0.333333', '0.333333'), ('0.25', '0.25')])

    def test_checkerboard_covers_exact_native_residues(self):
        checkerboard = self.text('include/utility/checkerboard.glsl')
        for divisor in (2, 3, 4):
            with self.subTest(divisor=divisor):
                match = re.search(r'checkerboard_offsets_' + str(divisor) + 'x' + str(divisor) +
                                  r'\s*=\s*ivec2\[\d+\]\((.*?)\);', checkerboard, re.S)
                self.assertIsNotNone(match)
                offsets = [(int(x), int(y)) for x, y in re.findall(r'ivec2\((\d+), (\d+)\)', match[1])]
                self.assertEqual(len(offsets), divisor ** 2)
                self.assertEqual(set(offsets), {(x, y) for x in range(divisor) for y in range(divisor)})
        self.assertEqual(re.findall(r'CLOUDS_TEMPORAL_UPSCALING == (\d+)', checkerboard), ['1', '2', '3', '4'])
        for program in ('program/d1_clouds.fsh', 'program/d2_clouds_upscaling.fsh'):
            self.assertIn('CLOUDS_TEMPORAL_UPSCALING * CLOUDS_TEMPORAL_UPSCALING', self.text(program))
        self.assertIn('dst_texel / CLOUDS_TEMPORAL_UPSCALING', self.text('program/d2_clouds_upscaling.fsh'))
        self.assertIn('dst_texel % CLOUDS_TEMPORAL_UPSCALING', self.text('program/d2_clouds_upscaling.fsh'))

    def test_larger_grid_exhausts_existing_history_age(self):
        settings = self.text('settings.glsl')
        limit = int(re.search(r'#define CLOUDS_ACCUMULATION_LIMIT (\d+)', settings)[1])
        self.assertEqual(limit, 20)
        history = self.text('program/d2_clouds_upscaling.fsh')
        self.assertIn('float history_weight = 1.0 - rcp(max(pixel_age - checkerboard_area, 1.0));', history)
        self.assertIn('clouds_data.y = min(++pixel_age, CLOUDS_ACCUMULATION_LIMIT);', history)
        weight = lambda age, divisor: 1.0 - 1.0 / max(age - divisor ** 2, 1.0)
        self.assertEqual(weight(limit, 4), 0.75)
        # All valid finite ages, including fractional ages interpolated by the
        # upstream history sample, have zero blend weight for divisor >= 5.
        for divisor in range(5, 17):
            for quarter_age in range(4 * limit + 1):
                self.assertEqual(weight(quarter_age / 4.0, divisor), 0.0)

    def test_low_resolution_outputs_are_not_a_skip_certificate(self):
        cloud = self.text('program/d1_clouds.fsh')
        history = self.text('program/d2_clouds_upscaling.fsh')
        buffers = self.text('include/buffers.glsl')
        self.assertIn('const bool colortex9Clear  = false;', buffers)
        self.assertIn('const bool colortex10Clear = false;', buffers)
        self.assertIn('vec4 current = texelFetch(colortex9, src_texel, 0);', history)
        self.assertIn('vec2 current_data = texelFetch(colortex10, src_texel, 0).xy;', history)
        self.assertIn('clouds_data.x = result.apparent_distance * rcp(CLOUDS_SCALE);', cloud)
        self.assertIn('clouds_data.y = result.scattering.w;', cloud)
        # Existing temporal replacement still consumes current on disocclusion.
        self.assertIn('if (disocclusion) {\n        history = current;', history)


if __name__ == '__main__':
    unittest.main()
