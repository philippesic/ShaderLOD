"""Public G04/G06 source selection matches only the accepted native-derived pilots."""
import sys,json,unittest,subprocess,tempfile,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'scripts'),str(ROOT/'tests')]
import build_supported_pack_adapters as b,build_makeup_translucent_fastpath as g4,build_makeup_ao_attenuation as g6
from test_makeup_g06 import normalized,MACROS
import re
def preprocess(files,entry,macros=None,options=None):
 def expand(n):
  return re.sub(r'^[ \t]*#include[ \t]+"(/[^\"]+)"[^\S\n]*$',lambda m:expand("shaders"+m[1])+"\n",files[n].decode(),flags=re.M)
 text=expand(entry)
 for k,v in (options or {}).items():text=re.sub(r"(#define "+k+r") [^ \n]+",lambda m:m[1]+" "+str(v),text)
 text=re.sub(r"^[ \t]*#(?:version|extension).*?$","",text,flags=re.M)
 return subprocess.run([(shutil.which("clang") or shutil.which("cpp")),"-E","-P","-x","c"]+[f"-D{k}={v}" for k,v in (MACROS if macros is None else macros).items()]+["-"],input=text,text=True,capture_output=True,check=True).stdout
class ExactMaintainedTests(unittest.TestCase):
 @classmethod
 def setUpClass(c):
  c.native=b.read_archive(ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip');c.files=b.apply(c.native,json.loads((ROOT/'patches/adapters/makeup-ultra-fast-shaders.json').read_text()))
 def test_matches_accepted_pilot_and_native_off(self):
  stems=sorted({Path(n).stem for n in self.native if n.startswith('shaders/world0/') and n.endswith(('.vsh','.fsh'))})
  for module,private in [(g4,g4.patch(self.native)),(g6,g6.patch(self.native,vertex_guard=True,pre_pow=True))]:
   for stem in stems:
    for ext in ['vsh','fsh']:
     n='shaders/world0/'+stem+'.'+ext
     for on in [1]:
      opts={module.OPTION:on,'SHADERLOD_REFINEMENT_ADAPTER':0}
      self.assertEqual(normalized(preprocess(self.files,n,options=opts)),normalized(preprocess(private,n,options=opts)),(n,on))
 def test_mismatch_native_fallback(self):
  for macro in [dict(MACROS,DISTANT_HORIZONS=1),dict(MACROS,VOXY=1),dict(MACROS,IRIS_VERSION=10814)]:
   for stem in ['gbuffers_water','deferred']:
    for ext in ['vsh','fsh']:
     n='shaders/world0/'+stem+'.'+ext;opts={g4.OPTION:1,g6.OPTION:1,'SHADERLOD_REFINEMENT_ADAPTER':0}
     self.assertEqual(normalized(preprocess(self.files,n,macros=macro,options=opts)),normalized(preprocess(self.native,n,macros=macro)))
 def test_asahi_capability_and_fail_closed_native(self):
  linux={k:v for k,v in MACROS.items() if k!='MC_OS_MAC'};linux['MC_OS_LINUX']=1
  options={g6.OPTION:1,'SHADERLOD_REFINEMENT_ADAPTER':0}
  for ext in ['vsh','fsh']:
   n='shaders/world0/deferred.'+ext
   mac=normalized(preprocess(self.files,n,options=options))
   self.assertEqual(mac,normalized(preprocess(self.files,n,macros=dict(linux,SHADERLOD_G06_ASAHI_RUNTIME_GUARD=1),options=options)))
   for macros in [linux,dict(linux,DISTANT_HORIZONS=1,SHADERLOD_G06_ASAHI_RUNTIME_GUARD=1),dict(linux,IRIS_VERSION=10814,SHADERLOD_G06_ASAHI_RUNTIME_GUARD=1)]:
    self.assertEqual(normalized(preprocess(self.files,n,macros=macros,options=options)),normalized(preprocess(self.native,n,macros=macros)))
 def test_g04_asahi_capability_and_fail_closed_native(self):
  linux={k:v for k,v in MACROS.items() if k!='MC_OS_MAC'};linux['MC_OS_LINUX']=1
  options={g4.OPTION:1,'SHADERLOD_REFINEMENT_ADAPTER':0}
  for ext in ['vsh','fsh']:
   n='shaders/world0/gbuffers_water.'+ext
   mac=normalized(preprocess(self.files,n,options=options))
   self.assertEqual(mac,normalized(preprocess(self.files,n,macros=dict(linux,SHADERLOD_G04_ASAHI_RUNTIME_GUARD=1),options=options)))
   for macros in [linux,dict(linux,SHADERLOD_G06_ASAHI_RUNTIME_GUARD=1),dict(linux,DISTANT_HORIZONS=1,SHADERLOD_G04_ASAHI_RUNTIME_GUARD=1),dict(linux,IRIS_VERSION=10814,SHADERLOD_G04_ASAHI_RUNTIME_GUARD=1)]:
    self.assertEqual(normalized(preprocess(self.files,n,macros=macros,options=options)),normalized(preprocess(self.native,n,macros=macros)))
 def test_public_actual_ao_assignment_no_shadowing(self):
  text=preprocess(self.files,'shaders/world0/deferred.fsh',options={g6.OPTION:1,'SHADERLOD_REFINEMENT_ADAPTER':0})
  branch=text.split('if (!shaderlodG06Skipped)',1)[1].split('blockColor.rgb *= final_ao',1)[0]
  self.assertIn('final_ao = mix(dbao(dither)',branch)
  self.assertNotIn('float final_ao',branch)
 def test_actual_stages_compile_link(self):
  with tempfile.TemporaryDirectory() as d:
   for option,stem in [(g4.OPTION,'gbuffers_water'),(g6.OPTION,'deferred')]:
    for ext,stage in [('vsh','vert'),('fsh','frag')]:
     text=preprocess(self.files,'shaders/world0/'+stem+'.'+ext,options={option:1,'SHADERLOD_REFINEMENT_ADAPTER':0});(Path(d)/('s.'+stage)).write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text)
    r=subprocess.run([shutil.which('glslangValidator'),'-l',str(Path(d)/'s.vert'),str(Path(d)/'s.frag')],text=True,capture_output=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
if __name__=='__main__':unittest.main()
