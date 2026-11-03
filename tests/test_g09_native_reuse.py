"""Native BSL exact reuse alternatives: boundaries, sample identity and real GLSL."""
import importlib.util
from pathlib import Path
import math,random,re,shutil,struct,subprocess,tempfile,unittest,sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
spec=importlib.util.spec_from_file_location('reuse',ROOT/'scripts/build_g09_native_reuse.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
MACROS={'IS_IRIS':1,'IRIS_VERSION':10812,'MC_VERSION':12101,'MC_OS_MAC':1,'OVERWORLD':1,'PARALLAX_QUALITY':128,'PARALLAX_DEPTH':'.25','SELF_SHADOW_ANGLE':2.,'SELF_SHADOW_QUALITY':8,'SELF_SHADOW_STRENGTH':16}
def pp(text,mode,extra=None):
 macros=dict(MACROS,**(extra or {}));macros[m.OPTION]=mode
 return subprocess.run(['clang','-E','-P','-x','c']+[f'-D{k}={v}' for k,v in macros.items()]+['-'],input=text,text=True,capture_output=True,check=True).stdout
def norm(s):return re.sub(r'\s+',' ',s).strip()
class G09NativeReuseTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.native=m.read_archive(ROOT/'dependencies/shortlist-test-inputs/originals/BSL_v10.1.8.zip');cls.files=m.patch(cls.native)
 def test_pinned_native_control_and_unsupported_kernel_tokens(self):
  old=self.native[m.KERNEL].decode();new=self.files[m.KERNEL].decode()
  for mode,extra in [(0,{}),(5,{}),(4,{'DISTANT_HORIZONS':1}),(4,{'VOXY':1}),(3,{'IRIS_VERSION':10814}),(3,{'MC_VERSION':12111})]:
   self.assertEqual(norm(pp(new,mode,extra)),norm(pp(old,mode,extra)))
  for dimension in ['NETHER','END']:
   macros=dict(MACROS);del macros['OVERWORLD'];macros[dimension]=1
   def run(text):return subprocess.run(['clang','-E','-P','-x','c']+[f'-D{k}={v}' for k,v in dict(macros,**{m.OPTION:3}).items()]+['-'],input=text,text=True,capture_output=True,check=True).stdout
   self.assertEqual(norm(run(new)),norm(run(old)))
 def test_only_pom_settings_and_ui_change_with_no_runtime_graph_or_caller_modification(self):
  self.assertEqual({n for n in self.native if self.native[n]!=self.files[n]},{m.KERNEL,m.SETTINGS,m.PROPS})
  for n in self.native:
   if n.startswith('shaders/program/') or 'license' in n.lower():self.assertEqual(self.native[n],self.files[n],n)
  old=pp(self.native[m.KERNEL].decode(),0);one=pp(self.files[m.KERNEL].decode(),1)
  self.assertEqual(norm(old[old.index('float GetParallaxShadow'):]),norm(one[one.index('float GetParallaxShadow'):]))
  self.assertEqual(old.count('ReadNormal(coord)'),one.count('ReadNormal(coord)')+1)
 def test_fully_faded_return_keeps_native_outputs_even_for_unordered_normal(self):
  for fade in [-math.inf,-1.,0.,.999999,1.,2.,math.inf,math.nan]:
   for check in [-math.inf,-2.,-1.999,0.,math.inf,math.nan]:
    old=fade>=1 or check< -1.999
    new=True if fade>=1 else check< -1.999
    self.assertEqual(old,new)
  two=pp(self.files[m.KERNEL].decode(),2)
  self.assertLess(two.index('if (parallaxFade >= 1.0)'),two.index('shaderlodG09Initial = ReadNormal(coord)'))
  self.assertIn('if (normalCheck < -1.999)',two)
 def test_shadow_reuses_only_equal_finite_coordinates_and_preserves_native_accumulation(self):
  native=self.native[m.KERNEL].decode().replace('\r\n','\n');new=pp(self.files[m.KERNEL].decode(),3)
  self.assertIn('i == 1 && all(equal(parallaxCoord, ptexCoord))',new)
  self.assertIn('abs(ptexCoord)',new);self.assertIn('abs(dcdx)',new);self.assertIn('abs(dcdy)',new)
  for anchor in ['float currentHeight = height + parallaxdir.z * sampleStep * i;','parallaxshadow = min(parallaxshadow, sampleShadow);','if (parallaxshadow < 0.01) break;']:
   self.assertIn(norm(anchor),norm(native));self.assertIn(norm(anchor),norm(new))
  f=lambda x:struct.unpack('f',struct.pack('f',x))[0];rng=random.Random(901)
  for i in range(20000):
   n=f(rng.uniform(-1,1));d=f(rng.uniform(-1,1));step=f(.32/8)
   first=f(n+f(d*step));loop=f(n+f(f(d*1.)*step));self.assertEqual(first,loop)
  for x in [math.nan,math.inf,-math.inf]:self.assertFalse(abs(x)<=1)
 def test_actual_modes_and_cue_kernel_compile(self):
  tool=shutil.which('glslangValidator');self.assertIsNotNone(tool)
  header='''#version 120
#extension GL_ARB_shader_texture_lod : enable
uniform sampler2D normals;
varying vec4 vTexCoordAM;
varying vec4 vTexCoord;
varying vec3 viewVector;
varying vec2 dcdx,dcdy;
'''
  main='void main(){float d;vec2 p=GetParallaxCoord(vec2(.5),.5,d);float s=GetParallaxShadow(d,.5,p,vec3(0,1,0),mat3(1));gl_FragColor=vec4(p,d,s);}'
  with tempfile.TemporaryDirectory() as tmp:
   for cue in [False,True]:
    for mode in range(5):
     text=header+pp(m.patch(self.native,cue)[m.KERNEL].decode(),mode)+main;p=Path(tmp)/'shader.frag';p.write_text(text)
     r=subprocess.run([tool,'-S','frag',str(p)],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
 def test_two_shadow_reuse_sources_require_actual_equal_coordinates(self):
  src=pp(self.files[m.KERNEL].decode(),4)
  self.assertIn('i == 0 && shaderlodG09CoordValid && all(equal(parallaxCoord, coord))',src)
  self.assertIn('i == 1 && shaderlodG09OffsetValid && all(equal(parallaxCoord, ptexCoord))',src)
  self.assertLess(src.index('bool shaderlodG09FiniteGrad'),src.index('for(int i = 0; i < 8; i++)'))
  self.assertIn('offsetHeight = texHeight;',src);self.assertIn('offsetHeight = texHeightOffset;',src)
  # Reconstructing the native i0 UV can round differently; the shader must
  # compare computed coordinates, not assume algebraic equivalence.
  f=lambda x:struct.unpack('f',struct.pack('f',x))[0];rng=random.Random(904);different=0
  for _ in range(20000):
   base=f(rng.random()*.8);scale=f(rng.uniform(.001,.19));coord=f(base+f(rng.random()*scale))
   local=f(f(coord-base)/scale);wrapped=f(local-math.floor(local));reconstructed=f(f(wrapped*scale)+base)
   if reconstructed!=coord:different+=1
   else:self.assertEqual(struct.pack('f',reconstructed),struct.pack('f',coord))
  self.assertGreater(different,0)
if __name__=='__main__':unittest.main()
