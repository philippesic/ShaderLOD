"""Authentic Bliss primary-cloud source and math checks; no driver/quality/speed claim."""
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
from build_supported_pack_adapters import apply, read_archive, sha256, CATALOG, RESOURCE_ROOT
from prepare_bliss_cloud_budget import (
    ADAPTER, RECIPE, VOLUME, DIMENSION, ENTRY, MARKER, COUNT_INSERT,
    candidate_manifest, recover, prepare,
)

BASE = ROOT / 'dev/upstream/other-pack-research/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip'
NATIVE = ROOT / 'src/main/resources/assets/shaderlod/blissquad/native463.json'


def expanded(files, name, options):
    source = files[name].decode().replace('\r\n', '\n')
    for key, value in options.items():
        if value == 'false':
            source = re.sub(r'(?m)^(\s*)#define ' + re.escape(key) + r'\b[^\n]*',
                            r'\1// disabled native ' + key, source)
        elif value == 'true':
            source = re.sub(r'(?m)^(\s*)//\s*#define ' + re.escape(key) + r'\b',
                            r'\1#define ' + key, source)
        else:
            source = re.sub(r'(?m)^(\s*#define ' + re.escape(key) + r'\s+)\S+',
                            lambda m: m[1] + value, source)
    return re.sub(r'^\s*#include\s+"([^"]+)"\s*$',
                  lambda m: expanded(files, 'shaders' + m[1] if m[1].startswith('/')
                                     else str(Path(name).parent / m[1]), options), source, flags=re.M)


def cpp(files, entry, options):
    source = re.sub(r'(?m)^\s*#(?:version|extension)\b[^\n]*', '', expanded(files, entry, options))
    process = subprocess.run(['clang', '-E', '-P', '-x', 'c', '-'], input=source,
                             text=True, capture_output=True, timeout=15)
    if process.returncode:
        raise AssertionError(process.stderr)
    return process.stdout


class BlissCloudBudgetTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not BASE.is_file(): raise unittest.SkipTest('Pinned Bliss original absent')
        cls.original = read_archive(BASE)
        cls.current = json.loads((ROOT / RECIPE).read_text())
        cls.assembled = apply(cls.original, cls.current)
        cls.candidate = candidate_manifest(cls.current)
        cls.changed = apply(cls.original, cls.candidate)
        cls.native = json.loads(NATIVE.read_text())

    def test_exact_pins_selected_route_and_body_recovery(self):
        self.assertEqual(sha256(BASE.read_bytes()), self.current['release_archive_sha256'])
        self.assertEqual(len(self.native), 463)
        self.assertEqual(self.native['HQ_CLOUDS'], 'true')
        self.assertEqual(self.native['VOLUMETRIC_CLOUDS'], 'true')
        self.assertEqual((self.native['minRayMarchSteps'], self.native['maxRayMarchSteps']), ('15','15'))
        self.assertEqual(self.native['CLOUDS_QUALITY'], '0.5')
        self.assertEqual(set(self.assembled), set(self.changed))
        self.assertEqual(sorted(n for n in self.changed if self.changed[n] != self.assembled[n]),
                         [DIMENSION, VOLUME])
        for name in (DIMENSION, VOLUME):
            self.assertEqual(recover(name, self.changed[name]), self.assembled[name])
        self.assertEqual(self.changed[ENTRY], self.original[ENTRY])
        self.assertIn(b'#define OVERWORLD_SHADER', self.original[ENTRY])
        self.assertIn(b'#include "/dimensions/composite2.fsh"', self.original[ENTRY])
        dimension = self.changed[DIMENSION]
        self.assertLess(dimension.index(('\t#define ' + MARKER).encode()),
                        dimension.index(b'#include "/lib/volumetricClouds.glsl"'))
        self.assertLess(dimension.index(b'#include "/lib/volumetricClouds.glsl"'),
                        dimension.index(('\t#undef ' + MARKER).encode()))
        volume = self.changed[VOLUME]
        self.assertLess(volume.index(b'int(clamp(maxIT_clouds / sqrt(exp2(viewPos.y))'),
                        volume.index(b'maxIT_clouds = shaderlod_effect_budget(maxIT_clouds, 4);'))
        self.assertLess(volume.index(b'maxIT_clouds = shaderlod_effect_budget(maxIT_clouds, 4);'),
                        volume.index(b'90.0/abs(dV_view.y)/maxIT_clouds'))
        self.assertEqual(self.candidate['additions'], self.current['additions'])
        self.assertIn(b'bridge != 2 || enabled != 1 || preset < 0 || preset > 2',
                      self.changed['shaders/lib/shaderlod/uniform_sample_budget.glsl'])

    def test_actual_preprocessor_selected_and_excluded_routes(self):
        if not shutil.which('clang'): self.skipTest('clang unavailable')
        # Actual selected wrapper; value1 models the generated EFFECT_ADAPTER option.
        on = dict(self.changed)
        helper = 'shaders/lib/shaderlod/adapter.glsl'
        on[helper] = on[helper].replace(b'#define SHADERLOD_EFFECT_ADAPTER 0 // [0 1]',
                                       b'#define SHADERLOD_EFFECT_ADAPTER 1 // [0 1]')
        self.assertNotEqual(on[helper], self.changed[helper])
        entry = 'shaders/world0/composite3.fsh'
        count = 'maxIT_clouds = shaderlod_effect_budget(maxIT_clouds, 4);'
        self.assertIn(count, cpp(on, entry, self.native))
        self.assertNotIn(count, cpp(self.changed, entry, self.native))
        for wrapper in ('world-1', 'world1'):
            self.assertNotIn(count, cpp(on, f'shaders/{wrapper}/composite3.fsh', self.native))
        for key in ('DISTANT_HORIZONS', 'VOXY'):
            modified = dict(on)
            modified[entry] = on[entry].replace(b'#define OVERWORLD_SHADER',
                                                  ('#define OVERWORLD_SHADER\n#define ' + key).encode())
            self.assertNotIn(count, cpp(modified, entry, self.native))
        off = dict(self.native); off['VOLUMETRIC_CLOUDS'] = 'false'
        self.assertNotIn(count, cpp(on, entry, off))
        # No derivative, texture read, new attachment or pass appears in the inserted text.
        self.assertNotRegex(COUNT_INSERT, r'\b(?:texture\w*|dFdx|dFdy|fwidth)\s*\(')

    def test_count_and_homogeneous_full_layer_invariant(self):
        expected_b = [9, 10, 11, 12, 13, 14]
        expected_p = [8, 9, 9, 10, 11, 12]
        for index, native in enumerate(range(10, 16)):
            for retained, expected in ((.875, expected_b[index]), (.75, expected_p[index])):
                count = max(4, math.ceil(native * retained))
                self.assertEqual(count, expected)
                self.assertLess(count, native)
                for mu, light, direction_y in ((.01, 1.0, .25),(.5,2.0,.8)):
                    # Native h=100/N and mult=90/(|v_y|N) for each full slab.
                    nstep = 90.0 / (abs(direction_y) * native)
                    cstep = 90.0 / (abs(direction_y) * count)
                    self.assertAlmostEqual(native*(100.0/native), count*(100.0/count))
                    self.assertAlmostEqual(native*nstep, count*cstep)
                    native_t = math.exp(-mu*nstep)**native
                    candidate_t = math.exp(-mu*cstep)**count
                    self.assertAlmostEqual(native_t, candidate_t, places=12)
                    self.assertAlmostEqual(light*(1-native_t), light*(1-candidate_t), places=12)
        # A varying density or truncated prefix invalidates the homogeneous full-slab equality.
        n, m = 15, 12
        varying_n = sum((1 + i/n)**2 * 90/n for i in range(n))
        varying_m = sum((1 + i/m)**2 * 90/m for i in range(m))
        self.assertNotAlmostEqual(varying_n, varying_m, places=4)
        self.assertNotAlmostEqual(3 * 90/n, 3 * 90/m, places=4)

    def test_real_overlay_and_refusals(self):
        protected = {p: (ROOT / p).read_bytes() for p in (RECIPE, RESOURCE_ROOT + RECIPE, CATALOG)}
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / 'candidate'
            receipt = prepare(BASE, output)
            self.assertTrue(receipt['native_current_body_recovered'])
            self.assertEqual(receipt['changed_from_current_adapter'], [DIMENSION, VOLUME])
            self.assertEqual(set(receipt['overlay']), set(protected))
            self.assertEqual((output/'overlay'/RECIPE).read_bytes(),
                             (output/'overlay'/(RESOURCE_ROOT+RECIPE)).read_bytes())
            catalog = (output/'overlay'/CATALOG).read_text()
            self.assertIn(receipt['candidate_digest'], catalog)
            self.assertIn(receipt['current_adapter_digest'], catalog.split('LEGACY_ADAPTER_DIGESTS')[1])
            self.assertTrue(all(v is False for v in receipt['validation'].values()))
            self.assertTrue((output/'source.patch').is_file())
            self.assertTrue((output/'integration.patch').is_file())
            self.assertEqual(protected, {p: (ROOT / p).read_bytes() for p in protected})
            saved = (output/'receipt.json').read_bytes()
            with self.assertRaisesRegex(ValueError, 'already exists'): prepare(BASE, output)
            self.assertEqual(saved, (output/'receipt.json').read_bytes())
            bad = Path(temp)/'bad.zip'; bad.write_bytes(BASE.read_bytes()+b'wrong')
            with self.assertRaisesRegex(ValueError, 'archive hash'): prepare(bad,Path(temp)/'refused')
            self.assertFalse((Path(temp)/'refused').exists())
        for member in (ENTRY, DIMENSION, VOLUME):
            altered = dict(self.original); altered[member] += b'\n'
            with self.assertRaisesRegex(ValueError, 'Pinned shader source identity changed'):
                apply(altered, self.candidate)
        with self.assertRaisesRegex(ValueError, 'already pinned'): candidate_manifest(self.candidate)
        wrong = copy.deepcopy(self.current); wrong['version'] = '2.2.0'
        with self.assertRaises(ValueError): candidate_manifest(wrong)


if __name__ == '__main__': unittest.main()
