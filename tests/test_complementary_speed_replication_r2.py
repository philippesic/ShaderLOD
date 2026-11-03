"""Bounded source/interval checks using the authentic pinned native option maps.

Offline GLSL syntax and scalar models only; no client/GPU performance evidence.
"""
import importlib.util
import json
import math
from pathlib import Path
import random
import re
import subprocess
import tempfile
import unittest
import zipfile
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('complementary_r2', ROOT / 'scripts/build_complementary_speed_replication_r2.py')
import sys
sys.path.insert(0, str(ROOT / 'scripts'))
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class ComplementaryTemporalBudget(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.base = Path(cls.tmp.name)
        cls.manifests = {}
        cls.packs = {}
        for family in builder.PINS:
            out = cls.base / family
            cls.manifests[family] = builder.build(family, out, native_profile=ROOT / 'src/main/resources/assets/shaderlod' / (family+'quad') / 'native398.json')
            for arm in ['original', 'off', 'control', 'candidate', 'cue']:
                with zipfile.ZipFile(out / (arm + '.zip')) as z:
                    cls.packs[(family, arm)] = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_archive_scope_options_and_determinism(self):
        for family, manifest in self.manifests.items():
            original = self.packs[(family, 'original')]
            for arm in ['off', 'control', 'candidate', 'cue']:
                candidate = self.packs[(family, arm)]
                self.assertEqual(sorted(n for n in original if original[n] != candidate[n]), sorted([builder.LIGHT, builder.VL]))
                self.assertEqual(set(candidate) - set(original), {builder.POLICY, builder.PCF, builder.VLB})
                self.assertEqual(candidate['License.txt'], original['License.txt'])
                self.assertEqual(candidate['shaders/lib/common.glsl'], original['shaders/lib/common.glsl'])
                self.assertEqual(candidate['shaders/lib/antialiasing/taa.glsl'], original['shaders/lib/antialiasing/taa.glsl'])
                expected = json.loads((self.base / family / ('expected-' + arm + '-399.json')).read_text())
                self.assertEqual(len(expected), 399)
                self.assertEqual({k: v for k, v in expected.items() if k != builder.MODE}, manifest['native398'])
            second = builder.build(family, self.base / (family + '-rebuild'), native_profile=ROOT / 'src/main/resources/assets/shaderlod' / (family+'quad') / 'native398.json')
            self.assertEqual({k: v['sha256'] for k, v in manifest['archives'].items()},
                             {k: v['sha256'] for k, v in second['archives'].items()})

    def test_profile_identity_and_historical_receipt_refusals(self):
        for family in builder.PINS:
            self.assertEqual(self.manifests[family]['native_map_kind'],
                             'packaged-native-profile-not-runtime-observation')
            self.assertFalse(self.manifests[family]['runtime_loaded_map_verified'])
            bad = self.base / (family+'-wrong-profile.json')
            bad.write_text('{}')
            with self.assertRaisesRegex(ValueError, 'Pinned retained native profile changed'):
                builder.build(family, self.base / (family+'-refused'), native_profile=bad)
            self.assertFalse((self.base / (family+'-refused')).exists())
            # Hold archive identity real while isolating the absent/wrong receipt
            # path, independent of historical files on this workstation.
            receipt=self.base/(family+'-receipt.json')
            archive=self.manifests[family]['original']
            pin=builder.PINS[family][1]
            with mock.patch.dict(builder.PINS,{family:(archive,pin,str(receipt))}):
                with self.assertRaises(FileNotFoundError):
                    builder.build(family,self.base/(family+'-missing-receipt'))
                receipt.write_text(json.dumps({'actual_shader_sha256':'0'*64,
                    'effective_shader_options':self.manifests[family]['native398']}))
                with self.assertRaisesRegex(ValueError,'Authentic original option receipt changed'):
                    builder.build(family,self.base/(family+'-wrong-receipt'))
            self.assertFalse((self.base/(family+'-wrong-receipt')).exists())

    def cpp(self, family, arm, entry, extra=()):
        source = self.base / 'cpp' / family / arm
        if not source.exists():
            native = self.manifests[family]['native398']
            for name, data in self.packs[(family, arm)].items():
                if not name.startswith('shaders/') or Path(name).suffix not in ['.glsl', '.fsh', '.vsh']:
                    continue
                text = re.sub(r'^\s*#(?:version|extension).*$', '', data.decode(), flags=re.M).replace('#include "/', '#include "')
                if name == 'shaders/lib/common.glsl':
                    for key, value in native.items():
                        pattern = r'(?m)^[ \t]*(?://)?[ \t]*#define[ \t]+' + re.escape(key) + r'(?:[ \t]+[^\n]*)?$'
                        if re.search(pattern, text):
                            replacement = '#define ' + key if value == 'true' else '//#define ' + key if value == 'false' else '#define ' + key + ' ' + value
                            text = re.sub(pattern, lambda _: replacement, text)
                dest = source / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_text(text)
        cmd = ['clang', '-E', '-P', '-x', 'c', '-I', str(source / 'shaders'),
               '-DMC_VERSION=12101', '-DIRIS_VERSION=10812', '-DMC_OS_MAC', '-DIS_IRIS',
               '-DMC_SHADOW_QUALITY=1.0', '-DMC_HAND_DEPTH=0.125', '-DMC_RENDER_QUALITY=1.0',
               '-DMC_RENDER_STAGE_TERRAIN_SOLID=8', *extra, str(source / entry)]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=20)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def test_authentic_entrypoints_compile_and_fallback(self):
        entries = ['shaders/world0/gbuffers_terrain.fsh', 'shaders/world0/composite1.fsh']
        for family in builder.PINS:
            for entry in entries:
                original = self.cpp(family, 'original', entry)
                self.assertEqual(self.cpp(family, 'off', entry), original)
                for arm in ['control', 'candidate', 'cue']:
                    text = self.cpp(family, arm, entry)
                    expected_consumer = 'slReceiverR2(' if 'terrain' in entry else 'slVLR2('
                    self.assertIn(expected_consumer, text)
                    fixture = self.base / (family + '-' + arm + '-' + Path(entry).stem + '.frag')
                    fixture.write_text('#version 330 compatibility\n' + text)
                    result = subprocess.run(['glslangValidator', '-S', 'frag', str(fixture)], text=True, capture_output=True, timeout=20)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                for flags in [('-DDISTANT_HORIZONS',), ('-DVOXY',), ('-DCUSTOM_PBR',), ('-DMC_VERSION=12102',)]:
                    self.assertEqual(self.cpp(family, 'candidate', entry, flags), self.cpp(family, 'original', entry, flags))
            for entry in ['shaders/world0/gbuffers_water.fsh', 'shaders/world-1/gbuffers_terrain.fsh', 'shaders/world1/gbuffers_terrain.fsh']:
                self.assertEqual(self.cpp(family, 'candidate', entry), self.cpp(family, 'original', entry))

    def test_temporal_pcf_covers_native_support(self):
        # Freeze native gradient only for this support/normalization oracle;
        # real frame noise/camera/light changes invalidate exact two-frame identity.
        rng = random.Random(821)
        for _ in range(1000):
            taps = [rng.random() for _ in range(4)]
            native = sum(taps) / 4
            even = (taps[0] + taps[1]) / 2
            odd = (taps[2] + taps[3]) / 2
            self.assertAlmostEqual((even + odd) / 2, native, places=14)
        for family in builder.PINS:
            source = self.packs[(family, 'candidate')][builder.PCF].decode()
            self.assertIn('offsetDist(gradientNoise + i, shadowSamples)', source)
            self.assertIn('shadowSamples == 2', source)
            self.assertIn('subsurfaceMode == 0 && !leaves', source)
            self.assertIn('renderStage == MC_RENDER_STAGE_TERRAIN_SOLID', source)

    def test_vl_union_mass_and_weighted_two_frame_response(self):
        # Independent quadrature oracle: unequal interval weights must survive.
        rng = random.Random(1010)
        for _ in range(10000):
            start, middle, end = sorted(rng.random() for _ in range(3))
            wa, wb = middle - start, end - middle
            union = end - start
            if union <= 1e-7:
                continue
            da, db = rng.random() * 4, rng.random() * 4
            native_rgb = da * wa + db * wb
            even = da * (2 * wa / union) * union
            odd = db * (2 * wb / union) * union
            self.assertAlmostEqual((even + odd) / 2, native_rgb, places=13)
            self.assertAlmostEqual(wa + wb, union, places=14)
        for family in builder.PINS:
            source = self.packs[(family, 'candidate')][builder.VLB].decode()
            self.assertIn('lastDistance >= slBudgetStartR2()', source)
            self.assertIn('slEnd2 < rayEnd && slEnd2 <= lViewPos0', source)
            self.assertIn('slEnd2 <= entityLinearDepth', source)
            self.assertIn('float(i + 1) + dither) / float(nearSamples)', source)
            self.assertNotIn('nearSamples =', source.replace('int nearSamples =', '').replace('nearSamples = int(mix(float(nearSamples), 1.5 * nearSamples, vlSceneIntensity));', ''))


if __name__ == '__main__':
    unittest.main()
