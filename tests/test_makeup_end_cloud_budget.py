"""Authentic current MakeUp cloud source checks; no driver or speed evidence."""
import copy
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import re
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from build_supported_pack_adapters import apply, build, read_archive, sha256, shader_source_digest, RESOURCE_ROOT
from prepare_makeup_end_cloud_budget import candidate_manifest
RECIPE = 'patches/adapters/makeup-ultra-fast-shaders.json'
BASE = ROOT / 'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip'
CLOUDS = ('shaders/lib/volumetric_clouds.glsl', 'shaders/lib/volumetric_clouds_end.glsl')

class MakeUpCloudTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not BASE.is_file():
            raise unittest.SkipTest('Authentic pinned MakeUp archive unavailable')
        cls.manifest = json.loads((ROOT / RECIPE).read_bytes())
        cls.original = read_archive(BASE)
        cls.changed = apply(cls.original, cls.manifest)

    def test_native_counts_first_contact_and_final_color_restore_exactly(self):
        for name in CLOUDS:
            actual = self.changed[name]
            self.assertNotIn(b'samples = shaderlod_makeup_volume_budget(samples)', actual)
            self.assertIn(b'cloud_value >= opacity_dist) break;', actual)
            self.assertLess(actual.index(b'first_contact = false;'), actual.index(b'cloud_value >= opacity_dist) break;'))
            tail = b'        cloud_value = clamp(cloud_value / opacity_dist, 0.0, 1.0);'
            self.assertEqual(actual[actual.index(tail):].split(b'\n}',1)[0], self.original[name][self.original[name].index(tail):].split(b'\n}',1)[0])
            restored = actual
            for patch in reversed(self.manifest['patches']):
                if patch['path'] == name:
                    restored = restored.replace(patch['after'].encode(), patch['before'].encode(), 1)
            self.assertEqual(restored, self.original[name])

    def test_selected_end_and_overworld_exclude_other_routes(self):
        clang = shutil.which('clang')
        if not clang:
            self.skipTest('C preprocessor unavailable')
        def expand(name):
            return re.sub(r'#include "(/[^\"]+)"', lambda m: expand('shaders' + m.group(1)), self.changed[name].decode())
        for name in CLOUDS:
            source = expand(name).replace('#define SHADERLOD_REFINEMENT_ADAPTER 0 // [0 1]', '#define SHADERLOD_REFINEMENT_ADAPTER 1 // [0 1]')
            base = '#define DEFERRED_SHADER\n#define V_CLOUDS 1\n#define CLOUD_VOL_STYLE 0\n'
            if name == CLOUDS[1]:
                base += '#define THE_END\n'
            for excluded in (None, 'NETHER', 'UNKNOWN_DIM', 'DISTANT_HORIZONS', 'VOXY', 'VOXY_PATCH'):
                prefix = base + ('#define ' + excluded + '\n' if excluded else '')
                result = subprocess.run([clang, '-E', '-P', '-x', 'c', '-'], input=prefix + source, text=True, capture_output=True, check=True).stdout
                self.assertEqual('cloud_value >= opacity_dist) break;' in result, excluded is None)
                self.assertNotIn('samples = shaderlod_makeup_volume_budget(samples)', result)
        helper = self.changed['shaders/lib/shaderlod/adapter.glsl'].decode()
        self.assertIn('uniform int shaderlodPreset = -1;', helper)
        self.assertIn('return shaderlod_optimization_enabled(shaderlodOptCloudOpacityExit);', helper)
        flags = self.changed['shaders/include/shaderlod/optimization_flags.glsl'].decode()
        self.assertIn('bool shaderlod_optimization_enabled(int flag)', flags)
        self.assertIn('shaderlodPreset >= 0 && shaderlodPreset <= 2', flags)

    def test_primary_entry_points_and_end_renderer_scope(self):
        common = self.original['shaders/common/deferred_fragment.glsl'].decode()
        for symbol in ('get_cloud(', 'get_end_cloud('):
            self.assertIn(symbol, common)
        for entry in ('shaders/world0/deferred.fsh', 'shaders/world1/deferred.fsh'):
            restored=self.changed[entry]
            for patch in reversed(self.manifest['patches']):
                if patch['path']==entry:
                    restored=restored.replace(patch['after'].encode(),patch['before'].encode(),1)
            self.assertEqual(restored,self.original[entry])
            self.assertIn(b'#define DEFERRED_SHADER', self.original[entry])

    def test_source_texture_and_recipe_tamper_refuse(self):
        for name in (*CLOUDS, 'shaders/textures/clouds_natural_512_R_8bit.png', 'shaders/textures/clouds_natural_512_R_8bit.png.mcmeta'):
            bad = dict(self.original); bad[name] += b'changed'
            with self.assertRaises(ValueError):
                apply(bad, self.manifest)
        with self.assertRaisesRegex(ValueError, 'already pinned'):
            candidate_manifest(self.manifest)

    def test_real_preview_mirrors_and_prior_derivative_preserved(self):
        protected = (ROOT / RECIPE).read_bytes()
        self.assertEqual(protected, (ROOT / (RESOURCE_ROOT + RECIPE)).read_bytes())
        catalog = (ROOT / 'src/main/java/com/pp/shaderlod/packs/SupportedPackCatalog.java').read_text()
        self.assertIn(shader_source_digest(self.changed), catalog)
        self.assertIn('956e3ab528c0ee9504bbc307306f269c9a85d3613a0dc5b49cfb80472ea4e5c4', catalog.split('LEGACY_ADAPTER_DIGESTS')[1])
        with tempfile.TemporaryDirectory() as temporary:
            result = build(BASE, Path(temporary) / 'installed.zip', ROOT / RECIPE)
            self.assertEqual(result['adapter_shader_source_digest'], shader_source_digest(self.changed))
            self.assertTrue(all(value is False for value in result['validation'].values()))
        self.assertEqual((ROOT / RECIPE).read_bytes(), protected)

if __name__ == '__main__':
    unittest.main()
