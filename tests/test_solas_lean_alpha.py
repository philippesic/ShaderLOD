import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import build_solas_lean_alpha as candidate


class SolasLeanAlphaTest(unittest.TestCase):
    def test_pinned_derivation_and_native_fallback(self):
        pack = candidate.ROOT / 'dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip'
        options = candidate.ROOT / 'dev/experiments/shortlist-closure-20261101/solas-native.txt'
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'candidate.zip'
            result = candidate.build(pack, options, output)
            self.assertEqual(len(result['native_options']), 428)
            self.assertEqual(Path(str(output) + '.txt').read_bytes(), options.read_bytes())
            with zipfile.ZipFile(pack) as original, zipfile.ZipFile(output) as actual:
                added = set(actual.namelist()) - set(original.namelist())
                self.assertEqual(added, {candidate.PREFIX + n + '.glsl' for n in candidate.LIBRARIES})
                for name in original.namelist():
                    if name != candidate.TERRAIN:
                        self.assertEqual(actual.read(name), original.read(name), name)
                before = original.read(candidate.TERRAIN).decode()
                after = actual.read(candidate.TERRAIN).decode()
                self.assertEqual(after.split('#ifdef VSH')[1], before.split('#ifdef VSH')[1])
                restored = after.replace(candidate.HELPER, '')
                for name in candidate.LIBRARIES:
                    restored = restored.replace(f'/lib/shaderlod_n04/{name}.glsl', f'/lib/lighting/{name}.glsl')
                    clone = actual.read(candidate.PREFIX + name + '.glsl').decode()
                    if name == 'gbuffersLighting':
                        self.assertEqual(clone.count(candidate.GUARD), 1)
                        self.assertLess(clone.index('texture3D(floodfillSampler'), clone.index(candidate.GUARD))
                        clone = clone.replace(candidate.GUARD, '')
                    for query in candidate.QUERIES.get(name, ()):
                        clone = clone.replace(query.replace('texture2D(', 'shaderlodN04Texture(', 1), query)
                    self.assertEqual(clone, original.read(f'shaders/lib/lighting/{name}.glsl').decode())
                self.assertEqual(restored, before)
            with self.assertRaises(ValueError):
                candidate.build(pack, options, output)

    def test_changed_query_refused(self):
        pack = candidate.ROOT / 'dependencies/upstream/pack-expansion/Solas Shader V3.7b.zip'
        with zipfile.ZipFile(pack) as source:
            members = {n: source.read(n) for n in source.namelist()}
        name = 'shaders/lib/lighting/shadows.glsl'
        members[name] = members[name].replace(b'texture2D(shadowtex, shadowPos.xy)', b'texture2D(shadowtex, shadowPos.xy + 0.01)')
        with self.assertRaises(ValueError):
            candidate.derive(members)


if __name__ == '__main__':
    unittest.main()
