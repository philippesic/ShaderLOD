"""G01 pinned source, native-path, history scheduling and real GLSL linkage."""
import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_makeup_g06 import preprocess, normalized, MACROS
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('scalar',ROOT/'scripts/build_makeup_scalar_exposure.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class ScalarExposureTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.native=m.read_archive(ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip');cls.files=m.patch(cls.native)
 def test_off_and_unsupported_native_program_tokens_and_other_dimensions(self):
  entries=['shaders/world0/'+stem+'.'+ext for stem in ['composite','composite1','composite2','final'] for ext in ['vsh','fsh']]+['shaders/world0/gbuffers_armor_glint.vsh']
  for entry in entries:
   for enabled,macros in [(0,MACROS),(1,dict(MACROS,DISTANT_HORIZONS=1)),(1,dict(MACROS,VOXY=1)),(1,dict(MACROS,IRIS_VERSION=10814)),(1,dict(MACROS,MC_VERSION=12111))]:
    with self.subTest(entry=entry,enabled=enabled,macros=macros):
     self.assertEqual(normalized(preprocess(self.files,entry,macros=macros,options={m.SCALAR:enabled})),normalized(preprocess(self.native,entry,macros=macros)))
  for n in self.native:
   if n.startswith(('shaders/world1/','shaders/world-1/')) or n.startswith('shaders/common/') or n in ['LICENSE','shaders/lib/config.glsl']:
    self.assertEqual(self.native[n],self.files[n],n)
 def test_original_bloom_aa_passes_move_without_changing_native_program_tokens(self):
  for current,old in [('composite2','composite1'),('composite3','composite2')]:
   for ext in ['vsh','fsh']:
    self.assertEqual(normalized(preprocess(self.files,'shaders/world0/'+current+'.'+ext,options={m.SCALAR:1})),normalized(preprocess(self.native,'shaders/world0/'+old+'.'+ext)))
 def test_exact_native_arithmetic_uses_fp32_transfer_and_native_r16f_history(self):
  old=self.native['shaders/common/composite_vertex.glsl'].decode()
  producer=self.files['shaders/lib/g01/history_vertex.glsl'].decode()
  self.assertEqual(producer.replace('colortex8','gaux3'),old)
  current=self.files['shaders/lib/g01/composite_vertex.glsl'].decode()
  self.assertIn('exposure = texture2D(colortex9, vec2(0.5)).r;',current)
  self.assertNotIn('float prev_exposure',current)
  fragment=self.files['shaders/lib/g01/composite_fragment.glsl'].decode()
  self.assertNotIn('vec4(exposure,',fragment);self.assertIn('blockColor.rgb * exposure',fragment)
  p=self.files['shaders/lib/g01/history_fragment.glsl'].decode()
  self.assertIn('\nconst int colortex8Format = R16F;\n',p);self.assertIn('\nconst int colortex9Format = R32F;\n',p)
  self.assertIn('colortex1MipmapEnabled = true',p);self.assertNotIn('colortex1MipmapEnabled',fragment)
  self.assertIn('RENDERTARGETS: 8,9',p)
  self.assertIn(b'const int gaux3Format = R16F;',self.files['shaders/lib/g01/final_fragment.glsl'])
  for stem,stage in [('final','vertex'),('final','fragment'),('glint_blocks','vertex')]:
   self.assertEqual(self.files[f'shaders/lib/g01/{stem}_{stage}.glsl'].decode().replace('colortex8','gaux3'),self.native[f'shaders/common/{stem}_{stage}.glsl'].decode())
 def test_property_graph_does_not_modify_native_rt1_flips_in_other_dimensions(self):
  for scalar,copies in [(s,c) for s in range(3) for c in range(3)]:
   macros=dict(MACROS,**{m.SCALAR:scalar,m.COPIES:copies},NETHER=1,THE_END=1,UNKNOWN_DIM=1)
   p=preprocess(self.files,m.PROPS,macros=macros)
   self.assertIn('program.world0/composite3.enabled='+str(bool(scalar)).lower(),p)
   self.assertEqual('size.buffer.colortex8=1 1' in p,bool(scalar))
   self.assertEqual('size.buffer.colortex9=1 1' in p,bool(scalar))
   self.assertNotIn('.colortex1=',p);self.assertEqual('flip.composite1.colortex9=true' in p,scalar==2)
   if copies:self.assertIn('flip.composite'+('3' if scalar else '2')+'.colortex0=true',p)
   else:self.assertNotIn('colortex0=true',p)
   self.assertEqual('flip.composite'+('3' if scalar else '2')+'.colortex4=true' in p,copies==2)
   self.assertNotIn('.colortex3=',p) # Native TAA history remains persistent.
 def test_diagnostic_retains_independent_native_history_and_arithmetic_and_links(self):
  files=m.patch(self.native,diagnostic=True)
  self.assertIn('SHADERLOD_G01_EXPOSURE_DIAGNOSTIC.json',files)
  self.assertNotIn('SHADERLOD_G01_EXPOSURE_DIAGNOSTIC.json',self.files)
  self.assertEqual(files['shaders/lib/g01/composite_vertex.glsl'],self.native['shaders/common/composite_vertex.glsl'])
  self.assertIn(b'RENDERTARGETS: 0,1,6,10',files['shaders/lib/g01/composite_fragment.glsl'])
  self.assertIn(b'colortex10Format = R32F',files['shaders/lib/g01/composite_fragment.glsl'])
  tool=shutil.which('glslangValidator')
  with tempfile.TemporaryDirectory() as tmp:
   for stem in ['composite','composite1']:
    for ext,stage in [('vsh','vert'),('fsh','frag')]:
     text=preprocess(files,'shaders/world0/'+stem+'.'+ext,options={m.SCALAR:1})
     (Path(tmp)/('shader.'+stage)).write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text)
    r=subprocess.run([tool,'-l',str(Path(tmp)/'shader.vert'),str(Path(tmp)/'shader.frag')],capture_output=True,text=True)
    self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def test_selected_native_producer_bloom_and_aa_stages_link(self):
  tool=shutil.which('glslangValidator');self.assertIsNotNone(tool)
  with tempfile.TemporaryDirectory() as tmp:
   for stem in ['composite','composite1','composite2','composite3','final','gbuffers_armor_glint']:
    for ext,stage in [('vsh','vert'),('fsh','frag')]:
     s=preprocess(self.files,'shaders/world0/'+stem+'.'+ext,options={m.SCALAR:1,m.COPIES:1})
     (Path(tmp)/('shader.'+stage)).write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n#define texture texture2D\n'+s)
    r=subprocess.run([tool,'-l',str(Path(tmp)/'shader.vert'),str(Path(tmp)/'shader.frag')],capture_output=True,text=True)
    self.assertEqual(r.returncode,0,stem+'\n'+r.stdout+r.stderr)
if __name__=='__main__':unittest.main()
