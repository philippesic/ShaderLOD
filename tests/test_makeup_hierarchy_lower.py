import sys,unittest,random,math
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from check_makeup_hierarchy_lower import lower_bits,value,bits,LOWER_GLSL
class LowerProofChecks(unittest.TestCase):
 def test_normal_half_cells(self):
  for b in range(0x38800000,0x3f800000,8192):
   for x in [b,b+1,b+4096,b+8191]:
    q=lower_bits(x);self.assertEqual(q,b);self.assertLessEqual(value(q),value(x));self.assertEqual(lower_bits(q),q)
 def test_small_and_invalid(self):
  for b in [0,1,0x387fffff,0x80000000,0xbf800000,0x7f800000,0xff800000,0x7fc00000,0x3f800001]:self.assertEqual(lower_bits(b),0)
 def test_endpoints(self):
  self.assertEqual(value(lower_bits(bits(1))),1);self.assertEqual(value(lower_bits(bits(2**-14))),2**-14)
 def test_correction_shared_with_constructor(self):
  java=(Path(__file__).resolve().parents[1]/'src/devClient/java/com/pp/shaderlod/experiment/hierarchy/DepthHierarchyR50.java').read_text()
  self.assertIn(LOWER_GLSL,java)
 def test_random_tree_induction(self):
  r=random.Random(5)
  for i in range(1000):
   ds=[r.randrange(0x3f800001) for _ in range(128)]
   first=[min(lower_bits(b) for b in ds[j:j+4]) for j in range(0,len(ds),4)]
   self.assertEqual(lower_bits(min(first)),lower_bits(min(ds)))
if __name__=='__main__':unittest.main()
