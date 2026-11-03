import unittest,tempfile,sys,zipfile,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import build_makeup_bloom_coordinate_census as b
class Native12(unittest.TestCase):
 def test_native_source_options_and_one_group(self):
  root=Path(__file__).resolve().parents[1];base=root/'dependencies/shortlist-test-inputs/g04/native100.txt'
  with tempfile.TemporaryDirectory() as d:
   out=Path(d)/'native12.zip';r=b.build(root/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip',base,out,True)
   self.assertEqual(Path(str(out)+'.txt').read_bytes(),base.read_bytes())
   with zipfile.ZipFile(out) as z:
    s=z.read('shaders/lib/bloom.glsl').decode();self.assertIn('shaderlodBloomGroup=0;',s);self.assertNotIn('shaderlodBloomGroup*10',s);self.assertIn('blurSample /= (BLOOM_SAMPLES * 4.0);',s);self.assertIn('MC_OS_LINUX',s);self.assertNotIn('MC_OS_MAC',s)
   self.assertEqual(r['kind'],'G10-native12-coordinates')
 def test_explicit_admission(self):
  with self.assertRaises(ValueError):b.build('unused','unused','unused')
if __name__=='__main__':unittest.main()
