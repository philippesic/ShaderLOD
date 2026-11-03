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

BASE = ROOT / 'dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip'
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
        cls.changed = apply(cls.original, cls.current)
        cls.native = json.loads(NATIVE.read_text())

    def test_actual_primary_consumer_native_body_and_march_recovery(self):
        self.assertEqual(sha256(BASE.read_bytes()), self.current['release_archive_sha256'])
        self.assertEqual(len(self.native), 463)
        self.assertEqual(self.native['HQ_CLOUDS'], 'true')
        self.assertEqual(self.native['VOLUMETRIC_CLOUDS'], 'true')
        self.assertEqual(self.native['cloud_LevelOfDetail'], '1')
        self.assertIn(b'#include "/dimensions/composite2.fsh"', self.original[ENTRY])
        self.assertIn(b'renderClouds(viewPos0', self.original[DIMENSION])
        body = self.changed[VOLUME]
        for patch in reversed([p for p in self.current['patches'] if p['path'] == VOLUME]):
            self.assertEqual(body.count(patch['after'].encode()), 1)
            body = body.replace(patch['after'].encode(), patch['before'].encode(), 1)
        self.assertEqual(body, self.original[VOLUME])
        # Independent integrated effects also own later cloud code; exact
        # sequential restoration above checks every declared edit.
        self.assertNotIn(b'dFdx(shaderlod', self.changed[VOLUME])
        self.assertNotIn(b'dFdy(shaderlod', self.changed[VOLUME])

    def test_actual_selected_and_excluded_source_paths(self):
        if not shutil.which('clang'): self.skipTest('clang unavailable')
        helper = 'shaders/lib/shaderlod/adapter.glsl'
        on = dict(self.changed)
        on[helper] = on[helper].replace(b'#define SHADERLOD_EFFECT_ADAPTER 0 // [0 1]',
                                       b'#define SHADERLOD_EFFECT_ADAPTER 1 // [0 1]')
        # CPU positive capability case; no actual GL extension observation is claimed.
        on[ENTRY] = b'#define GL_ARB_shader_texture_lod 1\n' + on[ENTRY]
        self.assertIn('shaderlodDensityUpper', cpp(on, ENTRY, self.native))
        off = dict(on); off[helper] = self.changed[helper]
        self.assertNotIn('shaderlodDensityUpper', cpp(off, ENTRY, self.native))
        no_extension = dict(on); no_extension[ENTRY] = self.changed[ENTRY]
        self.assertNotIn('shaderlodDensityUpper', cpp(no_extension, ENTRY, self.native))
        for entry in ('shaders/world-1/composite3.fsh', 'shaders/world1/composite3.fsh',
                      'shaders/world0/deferred.fsh', 'shaders/world0/composite2.fsh'):
            self.assertNotIn('shaderlodDensityUpper', cpp(on, entry, self.native))
        for macro in ('DISTANT_HORIZONS', 'VOXY'):
            modified = dict(on)
            modified[ENTRY] = on[ENTRY].replace(b'#define OVERWORLD_SHADER',
                                               ('#define OVERWORLD_SHADER\n#define ' + macro).encode())
            self.assertNotIn('shaderlodDensityUpper', cpp(modified, ENTRY, self.native))

    def test_nonnegative_detail_cannot_restore_certified_empty_density(self):
        # Independent monotonicity check over valid coverage/erosion, including extrema.
        certified = 0
        for cov in (.001, .01, .1, .25, .5, .999, 1.0):
            for amount in (.02, .5, 1.0, 3.0):
                for index in range(101):
                    coarse = index / 100.0 * math.sqrt(1.0 - cov)
                    upper = cov - coarse ** 4 * amount
                    if upper < -.001:
                        certified += 1
                        for detail in (0.0, .01, .5, 1.0):
                            noise = coarse + detail * .75 * (1.0 - cov)
                            self.assertEqual(max(cov - noise ** 4 * amount, 0.0), 0.0)
        self.assertGreater(certified, 0)
        # Full ray interval, lighting and extinction stay native, not count-resampled.
        self.assertNotIn(b'maxIT_clouds = shaderlod_effect_budget(maxIT_clouds', self.changed[VOLUME])

    def test_installed_recipe_resource_identity_and_conflicting_count_port_refusal(self):
        from build_supported_pack_adapters import build, shader_source_digest
        self.assertEqual((ROOT / RECIPE).read_bytes(), (ROOT / (RESOURCE_ROOT + RECIPE)).read_bytes())
        self.assertEqual((ROOT / 'patches/adapters/bliss-shader.glsl').read_bytes(),
                         (ROOT / (RESOURCE_ROOT + 'patches/adapters/bliss-shader.glsl')).read_bytes())
        self.assertIn(shader_source_digest(self.changed), (ROOT / CATALOG).read_text())
        for member in (ENTRY, VOLUME, 'shaders/texture/noises.png', 'shaders/texture/noises.png.mcmeta'):
            altered = dict(self.original); altered[member] += b'X'
            with self.assertRaises(ValueError): apply(altered, self.current)
        protected = {name: (ROOT / name).read_bytes() for name in (RECIPE, RESOURCE_ROOT + RECIPE, CATALOG)}
        with tempfile.TemporaryDirectory() as temp:
            artifact = Path(temp) / 'installed.zip'
            result = build(BASE, artifact, ROOT / RECIPE)
            self.assertEqual(result['adapter_shader_source_digest'], shader_source_digest(self.changed))
            self.assertTrue(all(value is False for value in result['validation'].values()))
            count_candidate = Path(temp) / 'conflicting-count-port'
            with self.assertRaisesRegex(ValueError, 'already pinned'):
                prepare(BASE, count_candidate)
            self.assertFalse(count_candidate.exists())
        self.assertEqual(protected, {name: (ROOT / name).read_bytes() for name in protected})


if __name__ == '__main__': unittest.main()
