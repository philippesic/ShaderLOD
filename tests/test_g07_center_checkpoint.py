"""The center discriminator must leave native taps, LOD and quality intact."""
import sys,tempfile,unittest,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
import build_g07_center_checkpoint as builder
from test_makeup_g06 import preprocess,MACROS,normalized

class CenterCheckpointTests(unittest.TestCase):
 def test_actual_native_loop_and_licenses_retained_and_private_linux_gate(self):
  original=ROOT/'dependencies/shortlist-test-inputs/originals/MakeUpNativeOriginal.zip'
  options=ROOT/'dependencies/shortlist-test-inputs/g04/native100.txt'
  with zipfile.ZipFile(original) as z:native={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
  with tempfile.TemporaryDirectory() as td:
   for control in ['clone','forcedfalse','skip']:
    out=Path(td)/(control+'.zip');builder.build(original,options,out,control)
    with zipfile.ZipFile(out) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    self.assertEqual(files['shaders/lib/ao.glsl'],native['shaders/lib/ao.glsl']);self.assertEqual(files['LICENSE'],native['LICENSE'])
    source=files[builder.thin.parity.tap.KERNEL].decode();reference=source[source.index('float shaderlodAoReferenceKernel('):]
    native_loop=native['shaders/lib/ao.glsl'].decode().split('    for (int i =')[1]
    self.assertEqual(reference.split('    for (int i =')[1],native_loop)
    self.assertNotIn('shaderlodCenter',native_loop)
    macros={k:v for k,v in MACROS.items() if k!='MC_OS_MAC'};macros['MC_OS_LINUX']=1
    active=preprocess(files,'shaders/world0/deferred.fsh',macros=macros,options={builder.thin.parity.tap.OPTION:1})
    self.assertIn('shaderlodCenterN',active);self.assertIn('shaderlodAoReferenceKernel',active)
    for opts in [{builder.thin.parity.tap.OPTION:0},{builder.thin.parity.tap.OPTION:1,'AA_TYPE':0}]:
     self.assertEqual(normalized(preprocess(files,'shaders/world0/deferred.fsh',macros=macros,options=opts)),normalized(preprocess(native,'shaders/world0/deferred.fsh',macros=macros,options=opts)))
