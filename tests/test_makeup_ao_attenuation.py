"""Exact native-attenuation alternative: finite arithmetic and unsupported-source boundaries."""
import importlib.util
import math
from pathlib import Path
import random
import re
import shutil
import subprocess
import tempfile
import unittest
import struct

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('attenuation',ROOT/'scripts/build_makeup_ao_attenuation.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
from test_makeup_g06 import preprocess, normalized, MACROS

class AttenuationTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.native=m.read_archive(ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip');cls.files=m.patch(cls.native)
 def test_only_caller_option_and_ui_change_no_pass_or_quality_change(self):
  self.assertEqual({n for n in self.native if self.native[n]!=self.files[n]},{m.CONFIG,m.CALLER,m.PROPS})
  self.assertEqual(self.files[m.CALLER].decode().replace(m.CALL,m.NATIVE_CALL),self.native[m.CALLER].decode())
  for n in ['shaders/lib/ao.glsl','shaders/lib/depth.glsl','shaders/lib/dither.glsl','LICENSE','shaders/world0/deferred.fsh','shaders/world0/deferred.vsh']:
   self.assertEqual(self.files[n],self.native[n])
 def test_off_and_unsupported_entries_preserve_native_tokens(self):
  cases=[(0,MACROS,{}),(1,dict(MACROS,DISTANT_HORIZONS=1),{}),(1,dict(MACROS,VOXY=1),{}),(1,dict(MACROS,VOXY_PATCH=1),{}),(1,dict(MACROS,IRIS_VERSION=10814),{}),(1,dict(MACROS,MC_VERSION=12111),{}),(1,{k:v for k,v in MACROS.items() if k!='MC_OS_MAC'},{}),(1,MACROS,{'AO':0}),(1,MACROS,{'AOSTEPS':7}),(1,MACROS,{'AA_TYPE':0})]
  for enabled,macros,options in cases:
   with self.subTest(enabled=enabled,macros=macros,options=options):
    self.assertEqual(normalized(preprocess(self.files,'shaders/world0/deferred.fsh',macros=macros,options=dict(options,**{m.OPTION:enabled}))),normalized(preprocess(self.native,'shaders/world0/deferred.fsh',macros=macros,options=options)))
  for entry in ['shaders/world1/deferred.fsh','shaders/world-1/deferred.fsh','shaders/deferred.fsh']:
   self.assertEqual(normalized(preprocess(self.files,entry,options={m.OPTION:1})),normalized(preprocess(self.native,entry)))
 def test_real_selected_guard_and_cue(self):
  text=preprocess(self.files,'shaders/world0/deferred.fsh',options={m.OPTION:1})
  self.assertIn('if (!shaderlodG06Skipped)',text);self.assertIn('ao_att == 1.0',text)
  self.assertNotIn('colortex8',text);self.assertNotIn('vec3(0.0, 1.0, 0.0)',text)
  cue=preprocess(m.patch(self.native,True),'shaders/world0/deferred.fsh',options={m.OPTION:1});self.assertIn('blockColor.rgb = shaderlodG06Skipped ?',cue)
 def test_fp32_depth_and_ao_ranges_make_exact_one_mix_finite(self):
  def f(value):return struct.unpack('f',struct.pack('f',value))[0]
  depths=[f(i/65536) for i in range(65537)]+[f(.56)]
  for near in [.01,.05,1]:
   for far in [16,192,4096]:
    for depth in depths:
     denominator=f(f(far)+f(near))-f(depth*f(f(far)-f(near)))
     linear=f(f(f(2)*f(near))/f(denominator))
     self.assertTrue(math.isfinite(linear) and linear>0)
  rng=random.Random(6789)
  clamp=lambda x:max(0.0,min(1.0,x))
  for trial in range(20000):
   total=0.0
   for i in range(5):
    x,y=[f(rng.uniform(-2e7,2e7)) for j in range(2)]
    angle=f(clamp(f(.5-x))+clamp(f(.5-y)))
    distance=f(clamp(f(f(.25*x)-1.0))+clamp(f(f(.25*y)-1.0)))
    total=f(total+clamp(f(angle+distance)))
   value=f(math.sqrt(f(f(f(total/5)*f(.7))+f(1.0-f(.7)))))
   self.assertTrue(math.isfinite(value) and 0<=value<=1)
   self.assertEqual(f(f(value*0.0)+1.0),1.0)
   self.assertEqual(f(value+f(f(1.0-value)*1.0)),1.0)
 def test_vertex_guard_preserves_fallback_and_moves_only_uniform_checks(self):
  files=m.patch(self.native,vertex_guard=True)
  for entry in ['shaders/world0/deferred.fsh','shaders/world0/deferred.vsh']:
   for macros in [MACROS,dict(MACROS,DISTANT_HORIZONS=1),dict(MACROS,IRIS_VERSION=10814)]:
    enabled=0 if macros==MACROS else 1
    self.assertEqual(normalized(preprocess(files,entry,macros=macros,options={m.OPTION:enabled})),normalized(preprocess(self.native,entry,macros=macros)))
  fragment=preprocess(files,'shaders/world0/deferred.fsh',options={m.OPTION:1});vertex=preprocess(files,'shaders/world0/deferred.vsh',options={m.OPTION:1})
  self.assertIn('shaderlodG06FiniteCamera > 0.5',fragment);self.assertNotIn('near >= 0.01',fragment)
  self.assertIn('near >= 0.01',vertex);self.assertIn('shaderlodG06FiniteCamera =',vertex)
  tool=shutil.which('glslangValidator')
  with tempfile.TemporaryDirectory() as tmp:
   for suffix,stage in [('fsh','frag'),('vsh','vert')]:
    text=preprocess(files,'shaders/world0/deferred.'+suffix,options={m.OPTION:1});p=Path(tmp)/('shader.'+stage);p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text);r=subprocess.run([tool,'-S',stage,str(p)],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def test_pre_pow_keeps_native_fallback_and_exact_kernel(self):
  files=m.patch(self.native,vertex_guard=True,pre_pow=True)
  for enabled,macros in [(0,MACROS),(1,dict(MACROS,DISTANT_HORIZONS=1)),(1,dict(MACROS,IRIS_VERSION=10814))]:
   for suffix in ['fsh','vsh']:
    entry='shaders/world0/deferred.'+suffix
    self.assertEqual(normalized(preprocess(files,entry,macros=macros,options={m.OPTION:enabled})),normalized(preprocess(self.native,entry,macros=macros)))
  self.assertEqual(files['shaders/lib/ao.glsl'],self.native['shaders/lib/ao.glsl'])
  text=preprocess(files,'shaders/world0/deferred.fsh',options={m.OPTION:1})
  self.assertIn('linearDepth >= 0.625',text);self.assertIn('fogDensityCoeff >= 0.0',text)
  self.assertLess(text.index('if (!shaderlodG06Skipped)'),text.index('float ao_att'))
  branch=text.split('if (!shaderlodG06Skipped)',1)[1].split('blockColor.rgb',1)[0]
  self.assertIn('final_ao = mix(dbao(dither), 1.0, ao_att);',branch)
  self.assertNotIn('float final_ao',branch)
  tool=shutil.which('glslangValidator')
  with tempfile.TemporaryDirectory() as tmp:
   for suffix,stage in [('fsh','frag'),('vsh','vert')]:
    text=preprocess(files,'shaders/world0/deferred.'+suffix,options={m.OPTION:1});p=Path(tmp)/('shader.'+stage);p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text);r=subprocess.run([tool,'-S',stage,str(p)],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def test_vertex_fog_guard_moves_only_draw_constant_checks(self):
  files=m.patch(self.native,vertex_guard=True,pre_pow=True,vertex_fog_guard=True)
  fragment=preprocess(files,'shaders/world0/deferred.fsh',options={m.OPTION:1})
  vertex=preprocess(files,'shaders/world0/deferred.vsh',options={m.OPTION:1})
  self.assertNotIn('fogDensityCoeff >= 0.0',fragment);self.assertNotIn('rainStrength <= 1.0',fragment)
  self.assertIn('linearDepth >= 0.625 && shaderlodG06FiniteCamera > 0.5',fragment)
  self.assertGreater(vertex.index('fogDensityCoeff >= 0.0'),vertex.index('fogDensityCoeff ='))
  for cloud in [0,1,2]:
   for macros in [MACROS,dict(MACROS,NO_CLOUDY_SKY=1)]:
    with tempfile.TemporaryDirectory() as tmp:
     paths=[]
     for suffix,stage in [('fsh','frag'),('vsh','vert')]:
      text=preprocess(files,'shaders/world0/deferred.'+suffix,options={m.OPTION:1,'V_CLOUDS':cloud},macros=macros);p=Path(tmp)/('shader.'+stage);p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text);paths.append(str(p))
     r=subprocess.run([shutil.which('glslangValidator'),'-l',*paths],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
  for enabled,macros in [(0,MACROS),(1,dict(MACROS,DISTANT_HORIZONS=1))]:
   for suffix in ['fsh','vsh']:
    entry='shaders/world0/deferred.'+suffix
    self.assertEqual(normalized(preprocess(files,entry,macros=macros,options={m.OPTION:enabled})),normalized(preprocess(self.native,entry,macros=macros)))
 def test_pre_pow_threshold_native_fp32_base_is_one(self):
  def f(x):return struct.unpack('f',struct.pack('f',x))[0]
  for bits in [0x3f200000,0x3f200001,0x3f7fffff,0x3f800000]:
   depth=struct.unpack('f',struct.pack('I',bits))[0]
   self.assertGreaterEqual(depth,.625);self.assertEqual(min(1.,max(0.,f(depth*f(1.6)))),1.)
  rng=random.Random(213)
  for i in range(10000):
   depth=f(rng.uniform(.625,1));fog=f(rng.uniform(0,16));rain=f(rng.uniform(0,1))
   exponent=f(f(fog*f(1-rain))+rain)
   self.assertTrue(math.isfinite(exponent));self.assertEqual(f(math.pow(1.,exponent)),1.)
 def test_cached_center_reuses_identical_native_sample_and_preserves_fallback(self):
  files=m.patch(self.native,vertex_guard=True,pre_pow=True,reuse_center=True)
  native=self.native[m.CALLER].decode()
  self.assertIn('float depth = texture2DLod(depthtex0, texcoord, 0).r;',native)
  self.assertIn('float linearDepth = ld(depth);',native)
  kernel=m.cached_center_kernel(self.native)
  self.assertEqual(kernel.count('texture2DLod(depthtex0'),2)
  self.assertIn('shaderlodCenterDepth < 0.56',kernel)
  for enabled,macros in [(0,MACROS),(1,dict(MACROS,DISTANT_HORIZONS=1)),(1,dict(MACROS,IRIS_VERSION=10814))]:
   for suffix in ['fsh','vsh']:
    entry='shaders/world0/deferred.'+suffix
    self.assertEqual(normalized(preprocess(files,entry,macros=macros,options={m.OPTION:enabled})),normalized(preprocess(self.native,entry,macros=macros)))
  for fog,raw in [(False,False),(True,False),(True,True)]:
   candidate=m.patch(self.native,vertex_guard=True,pre_pow=True,reuse_center=True,vertex_fog_guard=fog,raw_center=raw)
   if raw:
    kernel=m.cached_center_kernel(self.native,raw_center=True)
    self.assertIn('float linearDepth = shaderlodCenterDepth;',kernel)
    self.assertIn('linearDepth = ld(linearDepth);',kernel)
    self.assertEqual(kernel.count('texture2DLod(depthtex0'),2)
    self.assertEqual(normalized(preprocess(candidate,'shaders/world0/deferred.fsh',options={m.OPTION:0})),normalized(preprocess(self.native,'shaders/world0/deferred.fsh')))
   with tempfile.TemporaryDirectory() as tmp:
    paths=[]
    for suffix,stage in [('fsh','frag'),('vsh','vert')]:
     text=preprocess(candidate,'shaders/world0/deferred.'+suffix,options={m.OPTION:1})
     p=Path(tmp)/('shader.'+stage);p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text);paths.append(str(p))
    result=subprocess.run([shutil.which('glslangValidator'),'-l',*paths],capture_output=True,text=True)
    self.assertEqual(result.returncode,0,result.stdout+result.stderr)
 def test_pinned_fragment_programs_compile(self):
  tool=shutil.which('glslangValidator');self.assertIsNotNone(tool)
  with tempfile.TemporaryDirectory() as tmp:
   for enabled in [0,1]:
    for cue in [False,True]:
     text=preprocess(m.patch(self.native,cue),'shaders/world0/deferred.fsh',options={m.OPTION:enabled});p=Path(tmp)/'shader.frag';p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text);r=subprocess.run([tool,'-S','frag',str(p)],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)

if __name__=='__main__':unittest.main()
