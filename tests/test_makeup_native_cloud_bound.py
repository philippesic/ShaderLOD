"""Native100 guard/query preservation and real shader syntax; live proof is separate."""
import sys,json,tempfile,subprocess,unittest,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'scripts'),str(ROOT/'tests')]
import build_makeup_native_cloud_bound as b
import test_makeup_unused_sky as shader
class NativeCloudBoundTests(unittest.TestCase):
 def test_native_queries_guards_and_diagnostic_compile(self):
  native=b.read_archive(shader.BASE);patched=b.m.patch(native,tail_steps=4,native_queries=True,native100=True)
  options=json.loads((ROOT/'src/main/resources/assets/shaderlod/makeupquad/native100.json').read_text())
  macros=dict(shader.MACROS);macros.pop('MC_OS_MAC');macros['MC_OS_LINUX']=1
  def pp(files,mode=2,debug=0,extra=None):
   return shader.preprocess(files,'shaders/world0/deferred.fsh',native_options=options|{b.m.OPTION:mode,b.m.DEBUG:debug},macros=macros|(extra or {}))
  self.assertEqual(shader.normalized(pp(patched,0)),shader.normalized(pp(native,0)))
  for excluded in ('DISTANT_HORIZONS','VOXY','VOXY_PATCH'):
   self.assertEqual(shader.normalized(pp(patched,extra={excluded:1})),shader.normalized(pp(native,extra={excluded:1})))
  self.assertEqual(patched[b.m.CLOUD].count(b'texture2D(gaux2,'),native[b.m.CLOUD].count(b'texture2D(gaux2,'))
  self.assertNotIn(b'texture2DLod(gaux2',patched[b.m.CLOUD])
  for files,debug in ((patched,0),(b.work_files(patched,native),1),(b.work_files(b.m.patch(native,tail_steps=4,native_queries=True,native100=True,split_tail=True),native),1)):
   text=pp(files,debug=debug);self.assertIn('shaderlodG08DirectionalBound',text)
   if not shutil.which('glslangValidator'):continue  # actual driver compilation remains required
   with tempfile.TemporaryDirectory() as d:
    source=Path(d)/'bound.frag';source.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text)
    r=subprocess.run(['glslangValidator','-S','frag',str(source)],capture_output=True,text=True)
    self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def test_alu_guard_keeps_all_native_queries_and_has_no_atlas(self):
  native=b.read_archive(shader.BASE);files=b.m.patch(native,native_queries=True,native100=True,zero_guard=True)
  self.assertNotIn(b.m.ATLAS,files)
  self.assertEqual(files[b.m.CLOUD].count(b'texture2D(gaux2,'),native[b.m.CLOUD].count(b'texture2D(gaux2,'))
  work=b.work_files(files,native,layout='alu')
  self.assertIn(b'if ((current_value <= 0.0) && !(current_opacity == 0.0))',work[b.m.CLOUD])
if __name__=='__main__':unittest.main()
