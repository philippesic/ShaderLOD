"""Untimed native-reference AO witness admission and active GLSL link checks."""
import importlib.util
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from test_makeup_g06 import preprocess, normalized, MACROS

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
spec=importlib.util.spec_from_file_location('ao_parity',ROOT/'scripts/build_makeup_ao_parity.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class AoParityTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):cls.native=m.read_archive(ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip')
 def test_off_and_unsupported_world_graphs_are_native_source(self):
  for kind,query_only in [(k,False) for k in ['G06-fragment','G06-vertex','G06-prepow','G07-positive','G07-negative']]+[('G07-positive',True),('G07-negative',True)]:
   files,option=m.patch(self.native,kind,query_only)
   for macros,on in [(MACROS,0),(dict(MACROS,DISTANT_HORIZONS=1),1),(dict(MACROS,VOXY=1),1),(dict(MACROS,IRIS_VERSION=10814),1)]:
    for suffix in ['fsh','vsh']:
     entry='shaders/world0/deferred.'+suffix
     self.assertEqual(normalized(preprocess(files,entry,macros=macros,options={option:on})),normalized(preprocess(self.native,entry,macros=macros)))
   for n in ['LICENSE','shaders/lib/ao.glsl','shaders/lib/depth.glsl','shaders/lib/dither.glsl']:
    self.assertEqual(files[n],self.native[n])
 def test_reference_rgb_and_diagnostic_scalar_compile_and_link(self):
  tool=shutil.which('glslangValidator');self.assertIsNotNone(tool)
  for kind,query_only in [(k,False) for k in ['G06-fragment','G06-vertex','G06-prepow','G07-positive','G07-negative']]+[('G07-positive',True),('G07-negative',True)]:
   files,option=m.patch(self.native,kind,query_only)
   with tempfile.TemporaryDirectory() as tmp:
    paths=[]
    for suffix,stage in [('fsh','frag'),('vsh','vert')]:
     text=preprocess(files,'shaders/world0/deferred.'+suffix,options={option:1})
     if stage=='frag':
      self.assertIn('float final_ao = shaderlodAoReference;',text)
      self.assertIn('shaderlodAoReference == shaderlodAoCandidate',text)
      self.assertIn('gl_FragData[2] = vec4(shaderlodAoMismatch > 0.0 ? 257.0 : 1.0 + shaderlodAoOmitted)',text)
     path=Path(tmp)/('deferred.'+stage);path.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text);paths.append(str(path))
    result=subprocess.run([tool,'-l',*paths],text=True,capture_output=True)
    self.assertEqual(result.returncode,0,kind+'\n'+result.stdout+result.stderr)
 def test_vertex_fog_reference_uses_actual_reduced_predicate(self):
  files,option=m.patch(self.native,'G06-prepow',vertex_fog_guard=True)
  with tempfile.TemporaryDirectory() as tmp:
   paths=[]
   for suffix,stage in [('fsh','frag'),('vsh','vert')]:
    text=preprocess(files,'shaders/world0/deferred.'+suffix,options={option:1})
    if stage=='frag':
     self.assertIn('float shaderlodAoReference = mix(dbao(dither), 1.0, ao_att);',text)
     self.assertIn('linearDepth >= 0.625 && shaderlodG06FiniteCamera > 0.5',text)
     self.assertNotIn('fogDensityCoeff >= 0.0',text)
    p=Path(tmp)/('deferred.'+stage);p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text);paths.append(str(p))
   r=subprocess.run([shutil.which('glslangValidator'),'-l',*paths],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def test_pre_pow_witness_replays_actual_candidate_output_assignment(self):
  for fog,center,raw in [(False,False,False),(True,False,False),(False,True,False),(True,True,False),(True,True,True)]:
   files,option=m.patch(self.native,'G06-prepow',vertex_fog_guard=fog,reuse_center=center,raw_center=raw)
   qualified=m.attenuation.candidate_call(fog,center,raw).partition('        #else\n')[0].split('\n',1)[1]
   self.assertIn(qualified.replace('final_ao','shaderlodAoCandidate'),files[m.attenuation.CALLER].decode())
   with tempfile.TemporaryDirectory() as tmp:
    paths=[]
    for suffix,stage in [('fsh','frag'),('vsh','vert')]:
     text=preprocess(files,'shaders/world0/deferred.'+suffix,options={option:1})
     p=Path(tmp)/('deferred.'+stage);p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text);paths.append(str(p))
    r=subprocess.run([shutil.which('glslangValidator'),'-l',*paths],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def test_direct_error_magnitude_and_unchanged_native_control_link(self):
  for flags in [dict(direct_first=True),dict(query_only=True),dict(native_control=True)]:
   files,option=m.patch(self.native,'G07-positive',error_bins=True,**flags)
   with tempfile.TemporaryDirectory() as tmp:
    paths=[]
    for suffix,stage in [('fsh','frag'),('vsh','vert')]:
     text=preprocess(files,'shaders/world0/deferred.'+suffix,options={option:1})
     if stage=='frag':
      self.assertIn('float final_ao = shaderlodAoReference;',text)
      self.assertIn('shaderlodAoError <= 0.0000001',text)
      self.assertNotIn('257.0',text)
     p=Path(tmp)/('deferred.'+stage);p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text);paths.append(str(p))
    r=subprocess.run([shutil.which('glslangValidator'),'-l',*paths],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)

if __name__=='__main__':unittest.main()
