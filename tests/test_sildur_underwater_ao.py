import sys
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import build_sildur_underwater_ao as producer


class SildurUnderwaterAoTest(unittest.TestCase):
    def test_only_exact_unused_call_guard_changes(self):
        pack = producer.ROOT / "dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip"
        with tempfile.TemporaryDirectory() as directory:
            options=Path(directory)/'native.txt'
            options.write_text(''.join(f'{k}={v}\n' for k,v in sorted(json.loads(producer.PROFILE.read_text()).items())))
            candidate = Path(directory) / 'candidate.zip'
            receipt = producer.build(pack, options, candidate)
            self.assertEqual(len(receipt['native_options']), 115)
            self.assertEqual(Path(str(candidate) + '.txt').read_bytes(), options.read_bytes())
            with zipfile.ZipFile(pack) as native, zipfile.ZipFile(candidate) as actual:
                self.assertEqual(actual.namelist(), native.namelist())
                for name in native.namelist():
                    data = actual.read(name)
                    if name in producer.MEMBERS:
                        self.assertEqual(data.count(producer.REPLACEMENT), 1)
                        self.assertEqual(data.count(producer.ANCHOR), 0)
                        data = data.replace(producer.REPLACEMENT, producer.ANCHOR)
                    self.assertEqual(data, native.read(name), name)
            with self.assertRaises(ValueError):
                producer.build(pack, options, candidate)
            changed = Path(directory) / 'changed-options.txt'
            changed.write_text(options.read_text().replace('SSDO=true', 'SSDO=false'))
            with self.assertRaises(ValueError):
                producer.build(pack, changed, Path(directory) / 'disabled.zip')


if __name__ == '__main__':
    unittest.main()
