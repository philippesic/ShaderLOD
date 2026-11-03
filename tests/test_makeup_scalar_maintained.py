"""Maintained G01 route: real source identity, GLSL and private-proof equivalence."""
import sys,json,tempfile,subprocess,unittest,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'scripts'),str(ROOT/'tests')]
import build_makeup_scalar_exposure as m
from build_supported_pack_adapters import read_archive,apply,shader_source_digest
from test_makeup_g06 import preprocess,normalized,MACROS
class MaintainedScalarTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.native=read_archive(ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip')
  cls.recipe=json.loads((ROOT/'patches/adapters/makeup-ultra-fast-shaders.json').read_text())
  cls.files=apply(cls.native,cls.recipe);cls.private=m.patch(cls.native)
  cls.on={'SHADERLOD_MAKEUP_DEAD_FRAME_COPY':1,'SHADERLOD_REFINEMENT_ADAPTER':0}
  cls.macros=dict(MACROS,SHADERLOD_FRAMECOPY_RUNTIME_GUARD=1)
 def test_maintained_identity_and_mirrored_editable_helpers(self):
  self.assertIn(shader_source_digest(self.files),(ROOT/'src/main/java/com/pp/shaderlod/packs/SupportedPackCatalog.java').read_text())
  self.assertIn(shader_source_digest(self.files),(ROOT/'src/main/java/com/pp/shaderlod/optimization/makeupprime/MakeUpDepthPriming.java').read_text())
  self.assertEqual((ROOT/'patches/adapters/makeup-ultra-fast-shaders.json').read_bytes(),(ROOT/'src/main/resources/assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json').read_bytes())
  for member,v in self.recipe['additions'].items():
   if '/g01/' in member:self.assertEqual((ROOT/v['path']).read_bytes(),(ROOT/'src/main/resources/assets/shaderlod/adapters'/v['path']).read_bytes())
  self.assertEqual(self.native['LICENSE'],self.files['LICENSE'])
  self.assertIn(hashlib.sha256((ROOT/'patches/adapters/makeup-ultra-fast-shaders.json').read_bytes()).hexdigest(),(ROOT/'src/main/java/com/pp/shaderlod/optimization/makeupao/MakeUpAoSourceProof.java').read_text())
 def test_maintained_on_stages_match_measured_private_scalar2_rt4_tokens(self):
  for stem in ['composite','composite1','composite2','composite3','final','gbuffers_armor_glint']:
   for ext in ['vsh','fsh']:
    entry='shaders/world0/'+stem+'.'+ext
    with self.subTest(entry=entry):
     a=preprocess(self.files,entry,macros=self.macros,options=self.on)
     b=preprocess(self.private,entry,macros=self.macros,options={m.SCALAR:2,m.COPIES:2})
     self.assertEqual(normalized(a),normalized(b))
 def test_linux_guarded_stages_match_measured_private_linux_route(self):
  macros={k:v for k,v in self.macros.items() if k!='MC_OS_MAC'}
  macros['MC_OS_LINUX']=1
  private=m.patch(self.native,platform='linux')
  for stem in ['composite','composite1','composite2','composite3','final','gbuffers_armor_glint']:
   for ext in ['vsh','fsh']:
    entry='shaders/world0/'+stem+'.'+ext
    with self.subTest(entry=entry):
     a=preprocess(self.files,entry,macros=macros,options=self.on)
     b=preprocess(private,entry,macros=macros,options={m.SCALAR:2,m.COPIES:2})
     self.assertEqual(normalized(a),normalized(b))
 def test_off_unsupported_and_peer_graphs_keep_original_programs(self):
  for extra,opts in [(self.macros,{}),(MACROS,self.on),(dict(self.macros,DISTANT_HORIZONS=1),self.on),(dict(self.macros,VOXY=1),self.on),(dict(self.macros,VOXY_PATCH=1),self.on),(dict(self.macros,IRIS_VERSION=10814),self.on),(self.macros,dict(self.on,SHADERLOD_REFINEMENT_ADAPTER=1)),(self.macros,dict(self.on,SHADERLOD_MAKEUP_AO_HALF_RES=1))]:
   for stem in ['composite','composite1','composite2']:
    for ext in ['vsh','fsh']:
     n='shaders/world0/'+stem+'.'+ext
     # Existing native-config adapter declarations can remain, but route must
     # select original common stages, never the scalar producer/shifted passes.
     text=preprocess(self.files,n,macros=extra,options=opts)
     self.assertNotIn('texture2D(colortex9, vec2(0.5)).r',text)
     if stem=='composite' and ext=='vsh':self.assertIn('prev_exposure',text)
  for n in self.native:
   if n.startswith(('shaders/world1/','shaders/world-1/')) and ('composite' in n or '/final.' in n):self.assertEqual(self.native[n],self.files[n])
 def test_native_quality_stages_link_with_actual_mac_defines(self):
  with tempfile.TemporaryDirectory() as d:
   for enabled in [0,1]:
    for stem in ['composite','composite1','composite2','composite3','final','gbuffers_armor_glint']:
     for ext,stage in [('vsh','vert'),('fsh','frag')]:
      text=preprocess(self.files,'shaders/world0/'+stem+'.'+ext,macros=self.macros,options=dict(self.on,SHADERLOD_MAKEUP_DEAD_FRAME_COPY=enabled));(Path(d)/('shader.'+stage)).write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n#define texture texture2D\n'+text)
     r=subprocess.run(['glslangValidator','-l',str(Path(d)/'shader.vert'),str(Path(d)/'shader.frag')],capture_output=True,text=True)
     self.assertEqual(r.returncode,0,stem+'\n'+r.stdout+r.stderr)
if __name__=='__main__':unittest.main()
