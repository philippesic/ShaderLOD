import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
import build_g08_lean_layout as b
from test_makeup_g06 import MACROS, preprocess


class LeanLayoutTests(unittest.TestCase):
    def test_graph_and_native_fallback(self):
        original = ROOT/'dependencies/shortlist-test-inputs/originals/MakeUpNativeOriginal.zip'
        native = b.read_archive(original)
        macros = {k: v for k, v in MACROS.items() if k != 'MC_OS_MAC'}
        macros['MC_OS_LINUX'] = 1
        options = {b.OPTION: 1, 'V_CLOUDS': 2, 'CLOUD_VOL_STYLE': 0, 'CLOUD_SPEED': 0,
                   'CLOUD_STEPS_AVG': 16, 'AA_TYPE': 2}
        with tempfile.TemporaryDirectory() as td:
            for control in ('clone', 'forcedfalse', 'skip'):
                out = Path(td)/(control+'.zip')
                b.build(original, ROOT/'dependencies/shortlist-test-inputs/g08/native100.txt', out, control)
                with zipfile.ZipFile(out) as z:
                    files = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
                self.assertEqual(files['LICENSE'], native['LICENSE'])
                self.assertEqual(files['shaders/common/deferred_fragment.glsl'], native['shaders/common/deferred_fragment.glsl'])
                for path, data in native.items():
                    if path not in (b.group.m.CLOUD, 'shaders/lib/config.glsl', 'shaders/shaders.properties'):
                        self.assertEqual(files[path], data, path)
                selected = preprocess(files, 'shaders/world0/deferred.fsh', macros=macros, options=options)
                self.assertEqual(selected.count('vec3 get_cloud('), 1)
                for forbidden in ('GroupReference', 'GroupAttempts', 'GroupPotential', 'GroupBadSteps', 'GroupMismatch', 'gl_FragData[2]', 'colortex8Format'):
                    self.assertNotIn(forbidden, selected)
                self.assertIn('uniform.int.'+b.PERMISSION+'=0', files['shaders/shaders.properties'].decode())
                self.assertTrue(Path(str(out)+'.txt').read_text().endswith(b.OPTION+'=0\n'))
                if control != 'clone':
                    self.assertIn('bool allowed='+b.PERMISSION+'==1', selected)
                    self.assertIn('intersection_pos += increment;', selected)
                    native_selected = preprocess(native, 'shaders/world0/deferred.fsh', macros=macros, options=options)
                    start = native_selected.index('float current_value = texture2D(gaux2,')
                    end = native_selected.index('intersection_pos += increment;', start)
                    body = native_selected[start:end]
                    self.assertIn(' '.join(body.split()), ' '.join(selected.split()))
                    self.assertEqual(selected.count('intersection_pos += increment;'), 1)
                for overrides in ({b.OPTION: 0}, {'V_CLOUDS': 0}, {'CLOUD_SPEED': 1}):
                    opts = dict(options, **overrides)
                    actual = preprocess(files, 'shaders/world0/deferred.fsh', macros=macros, options=opts)
                    expected = preprocess(native, 'shaders/world0/deferred.fsh', macros=macros, options=opts)
                    self.assertEqual(actual.split(), expected.split())


if __name__ == '__main__':
    unittest.main()
