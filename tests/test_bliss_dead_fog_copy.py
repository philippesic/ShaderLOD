import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import build_bliss_dead_fog_copy as producer

class BlissDeadFogCopyTest(unittest.TestCase):
    def test_only_last_stage_property_changes_all_shader_bytes_native(self):
        pack=producer.ROOT/'dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip'
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);options=root/'native.txt';profile=json.loads(producer.PROFILE.read_text())
            options.write_text(''.join(f'{k}={v}\n' for k,v in sorted(profile.items())))
            out=root/'candidate.zip';receipt=producer.build(pack,options,out)
            self.assertEqual(profile,receipt['native_options'])
            with zipfile.ZipFile(pack) as original,zipfile.ZipFile(out) as candidate:
                self.assertEqual(original.namelist(),candidate.namelist())
                self.assertEqual([producer.PROPERTIES],[n for n in original.namelist() if original.read(n)!=candidate.read(n)])
                self.assertEqual(original.read(producer.PROPERTIES)+producer.ADDITION,candidate.read(producer.PROPERTIES))
            self.assertEqual(options.read_bytes(),Path(str(out)+'.txt').read_bytes())
