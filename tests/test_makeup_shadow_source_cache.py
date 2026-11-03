"""Current runtime cache call-site/lifecycle checks; current cache behavior is covered by JUnit."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class MakeUpShadowSourceCacheTests(unittest.TestCase):
 def test_runtime_join_retains_frame_boundary_and_lifecycle(self):
  s=(ROOT/'src/main/java/com/pp/shaderlod/optimization/makeupshadow/MakeUpShadowRuntime.java').read_text()
  frame=s.split('public static void frame(){',1)[1].split('public static void own',1)[0]
  self.assertNotIn('SOURCE_CACHE.invalidate()',frame)
  for token in ('SOURCE_CACHE.check(MakeUpShadowRuntime.class','colored?"colored-budget":"noncolored-shadow"','Map.copyOf(Iris.getShaderPackOptionQueue())','()->sourceUncached(m)','if(!sourceReady)SOURCE_CACHE.invalidate()'):
   self.assertIn(token,s)
  self.assertRegex(s,r'public static void invalidate\(\)\{[^}]*SOURCE_CACHE.invalidate\(\)')
  self.assertRegex(s,r'public static void destroy\(Object pipeline\)\{[^}]*SOURCE_CACHE.invalidate\(\)')
  self.assertIn('IrisSourceLifecycle.proof',s)
if __name__=='__main__':unittest.main()
