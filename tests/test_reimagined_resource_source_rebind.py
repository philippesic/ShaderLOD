"""Current receiver/mip/merged source closure; no live game, sampler or rendering claim."""
import hashlib,json,re,sys,unittest,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from build_supported_pack_adapters import apply,read_archive,shader_source_digest
class ReimaginedResourceSourceRebindTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.recipe=(ROOT/'patches/adapters/complementary-reimagined.json').read_bytes();cls.now=apply(read_archive(ROOT/'dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip'),json.loads(cls.recipe));cls.source=shader_source_digest(cls.now)
 def test_current_mip_and_merged_explicit_pins_and_all_guards_preserved(self):
  mip=(ROOT/'src/main/java/com/pp/shaderlod/optimization/reimaginedmips/ReimaginedDeadMipAdmission.java').read_text();merged=(ROOT/'src/main/java/com/pp/shaderlod/optimization/reimaginedmerged/ReimaginedMergedLinkageGate.java').read_text()
  for text in [mip,merged]:
   self.assertIn(self.source,text);self.assertIn(hashlib.sha256(self.recipe).hexdigest(),text);self.assertIn('verifyGeneratedRendererGraph',text)
   self.assertNotIn('bb847b2915b38d8022c4c5a23f9f6f524f895b7a8c9dab05af93b82c2940cb38',text)
  for token in ['DeadMipAdmission.apiTuple()','Iris.getShaderPackOptionQueue().isEmpty()','profile(actual)','getMipmappedBuffers().contains(0)','getComposite(ProgramArrayId.Composite)','samplersNative(storage)','texture(storage.getMainTexture(),storage)']:self.assertIn(token,mip)
  for token in ['GeneratedRendererOptionContract.accepts','NamedVertexContract.accepts','Iris.isFallback()','MakeUpQuadRuntime.stackMatches','MakeUpQuadRuntime.resourcesMatch','MERGED_SOURCE_CACHE.check']:self.assertIn(token,merged)
 def test_all_current_normal_reimagined_source_readers_and_prefix_registration(self):
  root=ROOT/'src/main/java/com/pp/shaderlod'
  for rel in ['optimization/reimaginedao/ReimaginedAoSourceProof.java','optimization/reimagineddepth/ReimaginedAoDepthRuntime.java','optimization/reimaginedspatial/ReimaginedSpatialRuntime.java','reimaginedprefix/ReimaginedPrefixRuntime.java']:
   text=(root/rel).read_text()
   self.assertTrue(self.source in text or hashlib.sha256(self.recipe).hexdigest() in text,rel)
   self.assertNotIn('bb847b2915b38d8022c4c5a23f9f6f524f895b7a8c9dab05af93b82c2940cb38',text,rel)
   self.assertNotIn('beb02922f2905a9939a5c4e75fc474ad956156ea7c885da12f06cf7ba9a96108',text,rel)
  plugin=(root/'reimaginedprefix/mixin/ReimaginedPrefixPlugin.java').read_text()
  self.assertIn('OptimizationId.POM_EMPTY_HEIGHT_PREFIX',plugin)
  self.assertIn('shaderlod-reimagined-prefix.mixins.json',(ROOT/'src/main/templates/META-INF/neoforge.mods.toml').read_text())
  runtime=(root/'reimaginedprefix/ReimaginedPrefixRuntime.java').read_text()
  for guard in ['ReimaginedPrefixProfile.accepts(actual)','ReimaginedPrefixSourceR8.matches','ReimaginedPrefixSpriteDiscovery.current','o.epoch.active','Iris.isFallback()']:
   self.assertIn(guard,runtime)
 def test_dead_mip_profile_reference_and_independent_compile_fallback(self):
  path=ROOT/'src/main/resources/assets/shaderlod/reimaginedmips/options408.json';profile=json.loads(path.read_text());self.assertEqual(len(profile),408)
  text=(ROOT/'src/main/java/com/pp/shaderlod/optimization/reimaginedmips/ReimaginedDeadMipAdmission.java').read_text();self.assertIn(hashlib.sha256(path.read_bytes()).hexdigest(),text)
  for name in ['SHADERLOD_REIMAGINED_DEAD_MIP_CHAIN','SHADERLOD_REIMAGINED_AO_HALF_RES','SHADERLOD_REIMAGINED_DEPTH_PRIMING']:self.assertEqual(profile[name],'0')
if __name__=='__main__':unittest.main()
