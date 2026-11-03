import sys,unittest,tempfile,zipfile,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import build_photon_reuse_probe as p
import benchmark_g08_probe as b

class CloudProbeTest(unittest.TestCase):
 def test_exact_source_instrumentation_and_other_members(self):
  with tempfile.TemporaryDirectory() as directory:
   target=Path(directory)/'probe.zip';record=p.build(b.BASE,target)
   with zipfile.ZipFile(b.BASE) as native,zipfile.ZipFile(target) as work:
    changed={n for n in set(native.namelist())&set(work.namelist()) if native.read(n)!=work.read(n)}
    self.assertEqual(set(native.namelist())-set(work.namelist()),{'shaderlod-selection.json'})
    self.assertEqual(changed,{p.HELPER,p.PROGRAM})
    self.assertEqual(set(work.namelist())-set(native.namelist()),{p.MARKER})
    helper=work.read(p.HELPER).decode();program=work.read(p.PROGRAM).decode()
    self.assertEqual(helper.count('shaderlodG08Hits += 1u;'),1)
    self.assertIn('floatBitsToUint(cached_depths)',helper)
    self.assertIn('shaderlodG08Native=true;',program)
    self.assertIn('shaderlodG08ReferenceData',program)
    self.assertEqual(json.loads(work.read(p.MARKER))['schema'],1023)
    self.assertEqual(record['original_sha256'],p.ORIGINAL)
 def test_wrong_base_and_replacement_refused(self):
  with tempfile.TemporaryDirectory() as directory:
   target=Path(directory)/'probe.zip';target.write_bytes(b'preserve')
   with self.assertRaises(ValueError):p.build(b.BASE,target)
   self.assertEqual(target.read_bytes(),b'preserve')

if __name__=='__main__':unittest.main()
