from pathlib import Path
import json
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import benchmark_bliss_zero_lean as lean


class BlissZeroLeanTest(unittest.TestCase):
    def test_only_albedo_guard_is_removed_and_native_fallback_survives(self):
        producer = lean.producer
        pack = producer.ROOT / 'dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip'
        self.assertNotIn(b'abs(Albedo)', producer.LEAN_GUARD)
        self.assertIn(b'floatBitsToUint(Emission) == 0u', producer.LEAN_GUARD)
        self.assertIn(b'abs(exposure) <= 8.0', producer.LEAN_GUARD)
        self.assertIn(b'greaterThanEqual(abs(Lighting)', producer.LEAN_GUARD)
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            options = root / 'native.txt'
            options.write_text(''.join(f'{k}={v}\n' for k, v in sorted(json.loads(producer.PROFILE.read_text()).items())))
            output = root / 'lean.zip'
            producer.build(pack, options, output, lean=True)
            with zipfile.ZipFile(pack) as native, zipfile.ZipFile(output) as candidate:
                self.assertEqual(native.namelist(), candidate.namelist())
                for name in native.namelist():
                    value = candidate.read(name)
                    if name == producer.MEMBER:
                        guard = producer.LEAN_GUARD.replace(b'\n', b'\r\n')
                        self.assertEqual(value.count(guard), 1)
                        self.assertEqual(value.replace(guard, b'', 1), native.read(name))
                    else:
                        self.assertEqual(value, native.read(name))
            self.assertEqual(options.read_bytes(), Path(str(output) + '.txt').read_bytes())

    def test_lean_selection_requires_prior_complete_native_domain(self):
        with self.assertRaises(ValueError):
            lean.require_native_domain({})
        with self.assertRaises(ValueError):
            lean.require_native_domain({'native': {'actual': dict(error='', completed_frames=1)}})
