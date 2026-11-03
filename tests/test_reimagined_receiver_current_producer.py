"""Actual normal producer + current receiver pin closure, no game/GL or platform stubs."""
import hashlib,json,os,re,shlex,shutil,subprocess,tempfile,unittest,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RUN=ROOT/'run-workloads/performance-readiness-20261016/known-win-r61/reimagined'
class ReimaginedReceiverCurrentProducerTests(unittest.TestCase):
 def test_actual_api_pin_closure(self):
  source=(ROOT/'src/main/java/com/pp/shaderlod/optimization/reimaginedearlyalpha/mixin/ReimaginedEarlyAlphaPlugin.java').read_text()
  pins=re.findall(r'\{"([^"]+\.class)","([0-9a-f]{64})"\}',source);self.assertEqual(len(pins),14);self.assertEqual(len({name for name,_ in pins}),len(pins))
  jars=[ROOT/'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar',ROOT/'dependencies/runtime/sodium-neoforge-0.6.13+mc1.21.1.jar']
  for name,digest in pins:
   matches=[]
   for jar in jars:
    with zipfile.ZipFile(jar) as z:
     if name in z.namelist():matches.append(hashlib.sha256(z.read(name)).hexdigest())
   self.assertEqual(matches,[digest],name)
if __name__=='__main__':unittest.main()
