import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import build_photon_pbr_attachment_elision as producer

class PhotonPbrAttachmentTest(unittest.TestCase):
    def test_separate_pbr_tuple_preserves_all_other_members_and_choices(self):
        pack=producer.ROOT/'dependencies/upstream/photon_v1.3b.zip'
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);options=root/'pbr.txt';expected=producer.expected_options()
            options.write_text(''.join(f'{k}={v}\n' for k,v in sorted(expected.items())))
            out=root/'candidate.zip';receipt=producer.build(pack,options,out)
            with zipfile.ZipFile(pack) as original,zipfile.ZipFile(out) as candidate:
                self.assertEqual(original.namelist(),candidate.namelist())
                self.assertEqual(set(receipt['changed_members']),{n for n in original.namelist() if original.read(n)!=candidate.read(n)})
                self.assertIn(b'#if defined WORLD_NETHER',candidate.read('shaders/program/d4_deferred_shading.fsh'))
            self.assertEqual(expected,receipt['native_options'])
            self.assertEqual(options.read_bytes(),Path(str(out)+'.txt').read_bytes())
            with self.assertRaisesRegex(ValueError,'native366'):producer.native_reference.validate(pack,options)
            for key,value in [('POM','false'),('TAAU','true'),('NO_NORMAL','false')]:
                changed=expected|{key:value};options.write_text(''.join(f'{k}={v}\n' for k,v in sorted(changed.items())))
                with self.subTest(key=key),self.assertRaisesRegex(ValueError,'N02 native PBR'):producer.validate(pack,options)
