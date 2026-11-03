import sys,unittest,subprocess,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
import build_g09_initial_native as m
class InitialNativeTest(unittest.TestCase):
 def test_literal_shadow_caller_and_licenses(self):
  native=m.reuse.read_archive(ROOT/'dependencies/shortlist-test-inputs/originals/BSL_v10.1.8.zip')
  for control in ['native','forcedfalse','initial']:
   files=m.patch(native,control)
   for n in native:
    if n not in [m.reuse.KERNEL,m.reuse.PROPS]:self.assertEqual(files[n],native[n],n)
   self.assertEqual(files[m.reuse.KERNEL].split(b'float GetParallaxShadow')[1],native[m.reuse.KERNEL].split(b'float GetParallaxShadow')[1])
   def pp(data,defs):
    return subprocess.run(['cpp','-P','-']+['-D'+x for x in defs],input=data.decode(),text=True,capture_output=True,check=True).stdout
   norm=lambda x:re.sub(r'\s+',' ',x).strip()
   for defs in [[],['MC_OS_LINUX','IS_IRIS','IRIS_VERSION=10814','MC_VERSION=12101','OVERWORLD'],['MC_OS_LINUX','IS_IRIS','IRIS_VERSION=10812','MC_VERSION=12101','OVERWORLD','DISTANT_HORIZONS']]:
    self.assertEqual(norm(pp(files[m.reuse.KERNEL],defs)),norm(pp(native[m.reuse.KERNEL],defs)))
   active=pp(files[m.reuse.KERNEL],['MC_OS_LINUX','IS_IRIS','IRIS_VERSION=10812','MC_VERSION=12101','OVERWORLD'])
   self.assertEqual('shaderlodG09Initial.a' in active,control!='native')
if __name__=='__main__':unittest.main()
