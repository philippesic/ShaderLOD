"""Native singleton early-alpha path: actual sources and GLSL, no speed claim."""
import hashlib,json,subprocess,sys,tempfile,unittest,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'scripts'),str(ROOT/'tests')]
from build_supported_pack_adapters import read_archive,apply,shader_source_digest
from test_makeup_g06 import preprocess,MACROS

class EarlyAlphaLeanTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.native=read_archive(ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip')
  cls.recipe=json.loads((ROOT/'patches/adapters/makeup-ultra-fast-shaders.json').read_text())
  cls.files=apply(cls.native,cls.recipe)
  cls.options={'SHADERLOD_REFINEMENT_ADAPTER':0,'SHADERLOD_MAKEUP_EARLY_ALPHA':1}
  cls.macros=dict(MACROS,SHADERLOD_EARLY_ALPHA_RUNTIME_GUARD=1)
 def test_singleton_drops_other_shader_consumers_but_keeps_draw_permission(self):
  t=preprocess(self.files,'shaders/world0/gbuffers_terrain.fsh',macros=self.macros,options=self.options)
  self.assertIn('shaderlodMakeupEarlyAlphaPermission == 1',t)
  for term in ['shaderlodOptimizationFlagsVersion == 1','flag == 1','shaderlodBridgeVersion == 2','shaderlodEnabled == 1','shaderlodPreset >= 0','shaderlodPreset <= 2','shaderlodPreset != 2']:
   self.assertIn(term,t)
  self.assertIn('shaderlodMakeupNativeAlphaReject(clamp(blockColor.a',t)
  self.assertIn('shadow2DLod(shadowtex1,p,0.0)',t)
  for other in ['shaderlodNear','shaderlodOptCloudPrimaryBudget','shaderlodOptReflectionTraceBudget','shaderlodAoDepthCache']:
   self.assertNotIn(other,t)
  self.assertIn('return shadow2D(shadowtex1,p);',t)
 def test_fullscreen_sources_are_native_tokens_with_native_quality(self):
  def tokens(t):return re.sub(r'\s+','',t)
  for stem in ['deferred','composite']:
   for ext in ['fsh','vsh']:
    p='shaders/world0/'+stem+'.'+ext
    self.assertEqual(tokens(preprocess(self.native,p)),tokens(preprocess(self.files,p,macros=self.macros,options=self.options)),p)
 def test_unsupported_and_peer_requests_do_not_admit_lean_discard(self):
  for macros,opts in [(MACROS,self.options),(dict(self.macros,DISTANT_HORIZONS=1),self.options),(dict(self.macros,VOXY=1),self.options),(dict(self.macros,IRIS_VERSION=10814),self.options),(self.macros,dict(self.options,SHADERLOD_MAKEUP_DEPTH_PRIMING=1)),(self.macros,dict(self.options,SHADERLOD_MAKEUP_EARLY_ALPHA=0))]:
   t=preprocess(self.files,'shaders/world0/gbuffers_terrain.fsh',macros=macros,options=opts)
   self.assertNotIn('shaderlodMakeupNativeAlphaReject(clamp(blockColor.a',t)
 def test_native_terrain_stages_compile_and_exact_resource_pins_agree(self):
  with tempfile.TemporaryDirectory() as d:
   for ext,stage in [('vsh','vert'),('fsh','frag')]:
    t=preprocess(self.files,'shaders/world0/gbuffers_terrain.'+ext,macros=self.macros,options=self.options)
    (Path(d)/('shader.'+stage)).write_text('#version 330 compatibility\n#extension GL_ARB_shader_texture_lod : enable\n#define texture texture2D\n'+t)
   # Legacy built-in blocks cannot link together in this standalone validator;
   # actual Iris/Sodium linking is checked by the finite runtime probe.
   for stage in ['vert','frag']:
    r=subprocess.run(['glslangValidator','-S',stage,str(Path(d)/('shader.'+stage))],capture_output=True,text=True)
    self.assertEqual(r.returncode,0,r.stdout+r.stderr)
  self.assertIn(shader_source_digest(self.files),(ROOT/'src/main/java/com/pp/shaderlod/packs/SupportedPackCatalog.java').read_text())
  recipe_sha=hashlib.sha256((ROOT/'patches/adapters/makeup-ultra-fast-shaders.json').read_bytes()).hexdigest()
  for f in ['makeupao/MakeUpAoSourceProof.java','makeupprime/MakeUpDepthPriming.java']:
   self.assertIn(recipe_sha,(ROOT/'src/main/java/com/pp/shaderlod/optimization'/f).read_text())
  for member,item in self.recipe['additions'].items():
   self.assertEqual((ROOT/item['path']).read_bytes(),(ROOT/'src/main/resources/assets/shaderlod/adapters'/item['path']).read_bytes(),member)
  self.assertEqual(self.native['LICENSE'],self.files['LICENSE'])

if __name__=='__main__':unittest.main()
