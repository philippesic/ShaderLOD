import json,sys,tempfile,unittest,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
import build_g08_native_layout as b
from test_makeup_g06 import preprocess,MACROS
class NativeLayoutTests(unittest.TestCase):
 def test_native_kernel_and_counter_boundary(self):
  original=ROOT/'dependencies/shortlist-test-inputs/originals/MakeUpNativeOriginal.zip'
  with zipfile.ZipFile(original) as z:native={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
  macros={k:v for k,v in MACROS.items() if k!='MC_OS_MAC'};macros['MC_OS_LINUX']=1
  with tempfile.TemporaryDirectory() as td:
   for audit in [False,True]:
    for control in ['clone','forcedfalse','skip']:
     p=Path(td)/f'{control}{audit}.zip';b.build(original,ROOT/'dependencies/shortlist-test-inputs/g04/native100.txt',p,control,audit)
     with zipfile.ZipFile(p) as z:f={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
     self.assertEqual(f['LICENSE'],native['LICENSE']);self.assertTrue(f[b.group.m.CLOUD].startswith(native[b.group.m.CLOUD]))
     s=f[b.group.m.CLOUD].decode()
     if not audit:
      for word in ['shaderlodG08GroupAttempts','shaderlodG08GroupPotential','shaderlodG08GroupBadSteps']:self.assertNotIn(word,s)
     active=preprocess(f,'shaders/world0/deferred.fsh',macros=macros,options={b.group.OPTION:1,'V_CLOUDS':2,'CLOUD_VOL_STYLE':0,'CLOUD_SPEED':0})
     self.assertIn('shaderlodGroupCandidate=shaderlodG08CandidateCloud(',active)
     self.assertIn('shaderlodGroupNative=get_cloud(',active)
     self.assertIn('shaderlodG08GroupSkip',active)
     for opts in [{b.group.OPTION:0},{b.group.OPTION:1,'V_CLOUDS':0}]:
      off=preprocess(f,'shaders/world0/deferred.fsh',macros=macros,options=opts)
      self.assertNotIn('shaderlodGroupCandidate=',off)
