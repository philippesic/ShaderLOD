import math,random,struct,sys,unittest,zipfile
from pathlib import Path
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'))
from check_makeup_hierarchy_lower import lower_bits,bits,value
class Fixed16Checks(unittest.TestCase):
 def test_odd_reduction_matches_native_descendants(self):
  rng=random.Random(16)
  for w,h in [(1,1),(3,17),(17,19),(65,33),(513,207)]:
   data=[rng.choice([rng.randrange(0x3f800001),0x7fc00000,0x3f800001,0x80000000]) for _ in range(w*h)]
   a=[lower_bits(b) for b in data];sw,sh=w,h
   for _ in range(2):
    ow,oh=(sw+3)//4,(sh+3)//4
    a=[min(a[yy*sw+xx] for yy in range(y*4,min(y*4+4,sh)) for xx in range(x*4,min(x*4+4,sw))) for y in range(oh) for x in range(ow)];sw,sh=ow,oh
   self.assertEqual((sw,sh),((w+15)//16,(h+15)//16))
   direct=[min(lower_bits(data[yy*w+xx]) for yy in range(y*16,min(y*16+16,h)) for xx in range(x*16,min(x*16+16,w))) for y in range(sh) for x in range(sw)]
   self.assertEqual(a,direct)
 def test_nearest_query_inside_margin_has_same_tile(self):
  rng=random.Random(16000)
  for w,h in [(2560,1600),(17,19),(513,207)]:
   for _ in range(20000):
    u,v=rng.random(),rng.random();px,py=u*w,v*h;cx,cy=math.floor(px/16),math.floor(py/16)
    if cx*16+2<px<min(cx*16+16,w)-2 and cy*16+2<py<min(cy*16+16,h)-2:
     self.assertEqual((int(px)//16,int(py)//16),(cx,cy))
     self.assertEqual((int(((cx+.5)/math.ceil(w/16))*math.ceil(w/16)),int(((cy+.5)/math.ceil(h/16))*math.ceil(h/16))),(cx,cy))
 def test_texture_state_is_owned_and_shader_compile_is_texture_free(self):
  java=(R/'src/devClient/java/com/pp/shaderlod/experiment/hierarchy/DepthHierarchyR50.java').read_text()
  compile=java[java.index(' private static int shader('):java.index(' private static void create(')]
  self.assertNotIn('glTex',compile)
  allocation=java[java.index(' private static int texture('):java.index(' public static void copied(')]
  for state in ['GL_TEXTURE_BASE_LEVEL,0','GL_TEXTURE_MAX_LEVEL,0','GL_TEXTURE_COMPARE_MODE,GL11.GL_NONE','0x84FE,1.0f']:
   self.assertIn(state,allocation)
  self.assertLess(allocation.index('glGenTextures'),allocation.index('GL_TEXTURE_BASE_LEVEL'))
 def test_gate_publication_and_failure_paths(self):
  java=(R/'src/devClient/java/com/pp/shaderlod/experiment/hierarchy/DepthHierarchyR50.java').read_text()
  copied=java[java.index(' public static void copied('):java.index(' public static void destroy(')]
  self.assertLess(copied.index('if(!producerEnabled()'),copied.index('new State()'))
  self.assertLess(copied.index('copiedDepth=0;copiedFrame=-1'),copied.index('create('))
  self.assertLess(copied.index('glDrawArrays'),copied.index('copiedDepth=targets'))
  self.assertIn('if(!fixed16())throw e;copiedDepth=0;copiedFrame=-1;',copied)
  self.assertIn('finally{state.restore();}',copied)
 def test_native_recurrence_and_repair_preserved(self):
  B=R/'dependencies/shortlist-test-inputs';old=zipfile.ZipFile(B/'g05-r2/lean-active.zip').read('shaders/lib/water.glsl').decode();new=zipfile.ZipFile(B/'g05-fixed16/lean-active.zip').read('shaders/lib/water.glsl').decode()
  self.assertEqual(new,old.replace('128.0','16.0').replace('native128block','native16block'))
  self.assertNotIn('128.0',new[new.index('bool shaderlodPreviousSkipped'):new.index('\n#else',new.index('bool shaderlodPreviousSkipped'))])
if __name__=='__main__':unittest.main()
