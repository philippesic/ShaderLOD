import unittest
from pathlib import Path
from build_g09_initial_sample import recipe
from build_supported_pack_adapters import ROOT, apply, read_archive
import json
import os

class InitialSampleTest(unittest.TestCase):
    def test_only_initial_fetch_changes(self):
        base = read_archive(Path(os.environ['G09_BASE']))
        original_recipe = json.loads((ROOT/'patches/adapters/bsl-shaders.json').read_text())
        control = apply(base, original_recipe)
        candidate = apply(base, recipe())
        changed = [p for p in control if control[p] != candidate[p]]
        self.assertEqual(changed, ['shaders/lib/surface/parallax.glsl'])
        a = control[changed[0]].decode()
        b = candidate[changed[0]].decode()
        self.assertEqual(a[a.index('float GetParallaxShadow'):], b[b.index('float GetParallaxShadow'):])
        restored = b.replace('    vec4 shaderlodG09InitialNormal = ReadNormal(coord);\n    vec3 normalMap = shaderlodG09InitialNormal.xyz * 2.0 - 1.0;', '    vec3 normalMap = ReadNormal(coord).xyz * 2.0 - 1.0;').replace('    float depth = shaderlodG09InitialNormal.a;', '    float depth = ReadNormal(coord).a;')
        self.assertEqual(a, restored)
        self.assertEqual(a.count('ReadNormal(coord)'), b.count('ReadNormal(coord)')+1)
    def test_wrong_source_refused(self):
        base = read_archive(Path(os.environ['G09_BASE']))
        base['shaders/lib/surface/parallax.glsl'] += b'\n'
        with self.assertRaises(ValueError):
            apply(base, recipe())

if __name__ == '__main__':
    unittest.main()
