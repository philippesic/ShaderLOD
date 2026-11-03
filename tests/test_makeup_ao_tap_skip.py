"""Pinned native AO pair saturation: finite arithmetic, caller fallback, driver-independent GLSL checks."""
import importlib.util
import math
from pathlib import Path
import random
import shutil
import struct
import subprocess
import tempfile
import unittest
from test_makeup_g06 import preprocess, normalized, MACROS
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('tap_skip',ROOT/'scripts/build_makeup_ao_tap_skip.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

def f(value):return struct.unpack('f',struct.pack('f',value))[0]
def terms(check):return max(0.0,min(1.0,f(.5-check))),max(0.0,min(1.0,f(f(.25*check)-1.0)))
def contribution(first,second,guarded):
 a,d=terms(first)
 if not guarded or not f(a+d)>=1.0:
  b,e=terms(second);a=f(a+b);d=f(d+e)
 return max(0.0,min(1.0,f(a+d)))

class TapSkipTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):cls.native=m.read_archive(ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip');cls.files=m.patch(cls.native)
 def test_only_selected_kernel_include_changes_no_graph_or_native_quality(self):
  self.assertEqual({n for n in self.native if self.native[n]!=self.files[n]},{m.CONFIG,m.CALLER,m.PROPS})
  self.assertEqual(self.files[m.CALLER].decode().replace(m.SELECT,m.INCLUDE),self.native[m.CALLER].decode())
  self.assertEqual(self.files['shaders/lib/ao.glsl'],self.native['shaders/lib/ao.glsl'])
  source=self.files[m.KERNEL].decode();self.assertIn('if (!(angle + dist >= 1.0))',source);self.assertNotIn('shaderlodG07Skipped',source)
  for n in ['LICENSE','shaders/lib/dither.glsl','shaders/lib/depth.glsl','shaders/world0/deferred.fsh','shaders/world0/deferred.vsh']:self.assertEqual(self.files[n],self.native[n])
 def test_pair_bounds_and_native_fp32_accumulation_agree(self):
  checks=[f(x/16) for x in range(-256,257)]+[f(x) for x in [-1e10,1e10,-.5000000596046448,-.4999999701976776,7.999999523162842,8.000000953674316]]
  skipped=0
  for first in checks:
   for second in checks:
    a,d=terms(first);skipped+=f(a+d)>=1.0
    self.assertEqual(contribution(first,second,False),contribution(first,second,True))
    self.assertEqual(contribution(first,second,False),contribution(second,first,True))
    # Query-only retains both native sums; center-depth yields checkDepth0.
    second_query=0.0 if f(a+d)>=1.0 else second
    self.assertEqual(contribution(first,second,False),contribution(first,second_query,False))
    self.assertEqual(contribution(first,second,False),1.0 if first < -1.0 else contribution(first,second,False))
  self.assertGreater(skipped,1000)
  rng=random.Random(8834)
  for trial in range(20000):
   old=new=0.0
   for i in range(5):
    first,second=[f(rng.uniform(-12,12)) for j in range(2)];old=f(old+contribution(first,second,False));new=f(new+contribution(first,second,True))
   self.assertEqual(old,new)
   self.assertEqual(f(math.sqrt(f(f(f(old/5)*f(.7))+f(1.0-f(.7))))),f(math.sqrt(f(f(f(new/5)*f(.7))+f(1.0-f(.7))))))
 def test_off_and_unsupported_dimensions_versions_and_options_are_native(self):
  cases=[(0,MACROS,{}),(1,dict(MACROS,DISTANT_HORIZONS=1),{}),(1,dict(MACROS,VOXY=1),{}),(1,dict(MACROS,IRIS_VERSION=10814),{}),(1,dict(MACROS,MC_VERSION=12111),{}),(1,{k:v for k,v in MACROS.items() if k!='MC_OS_MAC'},{}),(1,MACROS,{'AO':0}),(1,MACROS,{'AOSTEPS':7}),(1,MACROS,{'AA_TYPE':0})]
  for enabled,macros,opts in cases:
   self.assertEqual(normalized(preprocess(self.files,'shaders/world0/deferred.fsh',macros=macros,options=dict(opts,**{m.OPTION:enabled}))),normalized(preprocess(self.native,'shaders/world0/deferred.fsh',macros=macros,options=opts)))
  for entry in ['shaders/world1/deferred.fsh','shaders/world-1/deferred.fsh','shaders/deferred.fsh']:
   self.assertEqual(normalized(preprocess(self.files,entry,options={m.OPTION:1})),normalized(preprocess(self.native,entry)))
 def test_actual_active_source_and_cue_compile(self):
  tool=shutil.which('glslangValidator');self.assertIsNotNone(tool)
  with tempfile.TemporaryDirectory() as tmp:
   for cue,negative,query_only in [(a,b,c) for a in [False,True] for b in [False,True] for c in [False,True]]:
    for enabled in [0,1]:
     text=preprocess(m.patch(self.native,cue,negative,query_only),'shaders/world0/deferred.fsh',options={m.OPTION:enabled})
     if enabled:self.assertIn('if (!(angle + dist >= 1.0))',text)
     p=Path(tmp)/'shader.frag';p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text);r=subprocess.run([tool,'-S','frag',str(p)],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def test_direct_certificate_preserves_native_fallback_and_compiles(self):
  for cue in [False,True]:
   files=m.patch(self.native,cue=cue,direct_first=True)
   active=preprocess(files,'shaders/world0/deferred.fsh',options={m.OPTION:1})
   self.assertIn('if (checkDepth < -1.0)',active)
   self.assertIn('ambientOcclusion += 1.0;',active)
   self.assertIn('continue;',active)
   self.assertIn(m.SECOND,files[m.KERNEL].decode())
   self.assertEqual(normalized(preprocess(files,'shaders/world0/deferred.fsh',options={m.OPTION:0})),normalized(preprocess(self.native,'shaders/world0/deferred.fsh')))
   with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp)/'shader.frag';p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+active)
    r=subprocess.run([shutil.which('glslangValidator'),'-S','frag',str(p)],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
  for kwargs in [dict(negative_first=True),dict(query_only=True)]:
   with self.assertRaises(ValueError):m.kernel(self.native,direct_first=True,**kwargs)

if __name__=='__main__':unittest.main()
