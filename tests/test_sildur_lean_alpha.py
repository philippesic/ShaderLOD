import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import build_sildur_lean_alpha as producer


class SildurLeanAlphaTest(unittest.TestCase):
    def test_only_original_overworld_fragment_changes(self):
        pack = producer.ROOT / "dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip"
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            options = root / 'native.txt'
            settings = json.loads(producer.PROFILE.read_text())
            options.write_text(''.join(f'{k}={v}\n' for k, v in sorted(settings.items())))
            target = root / 'lean.zip'
            receipt = producer.build(pack, options, target)
            with zipfile.ZipFile(pack) as native, zipfile.ZipFile(target) as candidate:
                self.assertEqual(native.namelist(), candidate.namelist())
                self.assertEqual([producer.MEMBER], [n for n in native.namelist() if native.read(n) != candidate.read(n)])
                value = candidate.read(producer.MEMBER).decode().replace('\r\n', '\n')
                self.assertEqual(value.count(producer.HELPER), 1)
                self.assertEqual(value.count(producer.GUARDED_SOURCE), 1)
                self.assertLess(value.index(producer.GUARDED_SOURCE), value.index('getPCSS = funPCSS(noise);'))
                restored = value.replace(producer.EXTENSION, '', 1).replace('\n' + producer.HELPER, '', 1)
                restored = restored.replace('\n' + producer.GUARDED_SOURCE, '', 1).replace(producer.REWRITTEN_QUERY, producer.QUERY, 1)
                self.assertEqual(restored, native.read(producer.MEMBER).decode().replace('\r\n', '\n'))
            self.assertEqual(settings, receipt['native_options'])
            self.assertEqual(options.read_bytes(), Path(str(target) + '.txt').read_bytes())
            for key, value in [('nMap', '1'), ('PCSS', 'false'), ('TAA', 'false')]:
                options.write_text(''.join(f'{k}={v}\n' for k, v in sorted((settings | {key: value}).items())))
                with self.subTest(key=key), self.assertRaises(ValueError):
                    producer.validate(pack, options)
