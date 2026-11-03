"""Exact existing Reimagined secondary-cloud port; source/preprocessor and real producer only."""
import hashlib,json,re,shlex,os,shutil,subprocess,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from build_supported_pack_adapters import apply,read_archive,shader_source_digest
MEMBER='shaders/lib/atmospherics/clouds/reimaginedClouds.glsl'
class UnboundSecondaryCloudPortTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.recipe=json.loads((ROOT/'patches/adapters/complementary-unbound.json').read_text());cls.native=read_archive(ROOT/'dependencies/upstream/other-pack-research/ComplementaryUnbound_r5.9.3.zip');cls.after=apply(cls.native,cls.recipe)
 def test_exact_donor_and_only_declared_fragment_member_changes(self):
  donor=json.loads((ROOT/'patches/adapters/complementary-reimagined.json').read_text());a=[p for p in donor['patches'] if p['path']==MEMBER and 'shaderlodOptCloudSecondary' in p['after']];b=[p for p in self.recipe['patches'] if p['path']==MEMBER];self.assertEqual(a,b)
  self.assertEqual((ROOT/'src/main/resources/assets/shaderlod/adapters/patches/adapters/complementary-unbound.json').read_bytes(),(ROOT/'patches/adapters/complementary-unbound.json').read_bytes())
 def preprocess(self,style,cloud=-1,master=1,extra=''):
  common=self.after['shaders/lib/common.glsl'].decode();common=re.sub(r'(?m)^(\s*#define SHADER_STYLE\s+)\S+',lambda m:m[1]+str(style),common);common=re.sub(r'(?m)^(\s*#define CLOUD_STYLE_DEFINE\s+)\S+',lambda m:m[1]+str(cloud),common)
  text=f'#define OVERWORLD\n#define FRAGMENT_SHADER\n#define DEFERRED1\n#define SHADERLOD_REFINEMENT_ADAPTER {master}\n'+extra+'\n'+common+'\n#ifdef CLOUDS_REIMAGINED\n'+self.after[MEMBER].decode()+'\n#else\n'+self.after['shaders/lib/atmospherics/clouds/unboundClouds.glsl'].decode()+'\n#endif\n'
  text=re.sub(r'(?m)^\s*#(?:include|version|extension)\b[^\n]*','',text)
  r=subprocess.run(['clang','-E','-P','-x','c','-'],input=text,text=True,capture_output=True,timeout=15);self.assertEqual(r.returncode,0,r.stderr);return r.stdout
 def test_authentic_native_style_dispatch_and_negative_guards(self):
  for style,cloud in [(1,-1),(4,1)]:
   with self.subTest(style=style,cloud=cloud):
    text=self.preprocess(style,cloud);self.assertIn('shaderlodOptCloudSecondaryDetail',text);self.assertIn('shaderlodOptCloudSecondaryBudget',text);self.assertIn('2.0 * Get2DCloudSample',text)
  self.assertNotIn('shaderlodNativeCloudShade',self.preprocess(4,-1))
  for extra in ['#define DISTANT_HORIZONS\n','#define VOXY\n']:
   self.assertNotIn('shaderlodOptCloudSecondaryBudget',self.preprocess(1,-1,extra=extra))
  self.assertNotIn('shaderlodOptCloudSecondaryBudget',self.preprocess(1,-1,master=0))
 def test_native_two_tap_recovery_midpoint_weight_and_current_readers(self):
  patch=next(p for p in self.recipe['patches'] if p['path']==MEMBER);a=patch['after'].replace('\r\n','\n');native_tail=patch['before'].split(';',1)[1];recovered=re.search(r'} else \{(.*?)\n\s*}\n\s*}',a,re.S).group(1);self.assertEqual(re.sub(r'\s+','',native_tail),re.sub(r'\s+','',recovered))
  for line in ['cLightPos += (1.0 + gradientNoise) * cLightPosAdd;','light -= Get2DCloudSample(cLightPos.xz) * cloudShadingM;','cLightPos += gradientNoise * cLightPosAdd;','cloudShadingM == 0.0','lTracePos >= shaderlodNear','RP_MODE == 1','!defined GENERATED_NORMALS','!defined COATED_TEXTURES']:self.assertIn(line,a)
  for noise in [0.,.125,.5,1.]:self.assertEqual(((1+noise)+(1+2*noise))/2,1+1.5*noise)
  digest=shader_source_digest(self.after);recipe_sha=hashlib.sha256((ROOT/'patches/adapters/complementary-unbound.json').read_bytes()).hexdigest()
  for p in (ROOT/'src/main/java/com/pp/shaderlod').rglob('*.java'):
   if 'unbound' not in str(p).lower():continue
   text=p.read_text();self.assertNotIn('853896ca7c23c5c6b24717d04275f6b8c97d990fc0bde36301846c37e157d5de',text,str(p));self.assertNotIn('decc8c122ecec3ce50469b5255fa2a6e6f0a74bbb73faad1bddbdafe1e9f1c5a',text,str(p))
  self.assertIn(digest,(ROOT/'src/main/java/com/pp/shaderlod/packs/SupportedPackCatalog.java').read_text())
if __name__=='__main__':unittest.main()
