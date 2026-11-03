import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_makeup_g06 import preprocess,normalized
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('combo',ROOT/'scripts/build_makeup_ao_exact_combo.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class ComboTests(unittest.TestCase):
 def test_controls_match_each_isolated_native_or_exact_path_and_joint_links(self):
  native=m.read_archive(ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip');files=m.patch(native);a=m.attenuation.OPTION;b=m.tap.OPTION
  for ao,taps,expected in [(0,0,native),(1,0,m.attenuation.patch(native,vertex_guard=True)),(0,1,m.tap.patch(native))]:
   for entry in ['shaders/world0/deferred.fsh','shaders/world0/deferred.vsh']:
    self.assertEqual(normalized(preprocess(files,entry,options={a:ao,b:taps})),normalized(preprocess(expected,entry,options={a:ao,b:taps})))
  tool=shutil.which('glslangValidator');self.assertIsNotNone(tool)
  with tempfile.TemporaryDirectory() as tmp:
   for suffix,stage in [('fsh','frag'),('vsh','vert')]:
    text=preprocess(files,'shaders/world0/deferred.'+suffix,options={a:1,b:1});p=Path(tmp)/('shader.'+stage);p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text)
   r=subprocess.run([tool,'-l',str(Path(tmp)/'shader.vert'),str(Path(tmp)/'shader.frag')],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
if __name__=='__main__':unittest.main()
