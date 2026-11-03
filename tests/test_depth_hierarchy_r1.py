"""Conservativeness and complete GL4.1 atlas budget, not shader runtime tests."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

PATH=Path(__file__).resolve().parents[1]/'scripts/build_depth_hierarchy_r1.py'
SPEC=importlib.util.spec_from_file_location('depth_hierarchy_r1',PATH)
m=importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(m)

class DepthHierarchy(unittest.TestCase):
 def test_thin_occluder_not_erased(self):
  grid=[[1.0]*8 for _ in range(8)]; grid[7][7]=.2
  self.assertEqual(m.conservative_pyramid(grid)[-1][0][0],(.2,1.))
  self.assertFalse(m.excludes_native_interval((.2,1.),(.19,.21)))
  average=sum(map(sum,grid))/64
  self.assertTrue(m.excludes_native_interval((average,average),(.19,.21)))
 def test_grazing_equal_boundary_keeps_native(self):
  self.assertFalse(m.excludes_native_interval((.4,.7),(.7,.8)))
  self.assertFalse(m.excludes_native_interval((.4,.7),(.3,.4)))
 def test_unknown_never_empty(self):
  for value in (float('nan'),float('inf'),-.1,1.1):
   self.assertEqual(m.conservative_pyramid([[value]])[0][0][0],(0.,1.))
 def test_odd_edges_all_propagate(self):
  for w,h in ((7,5),(1,17),(19,1),(191,117)):
   grid=[[.8]*w for _ in range(h)]; grid[-1][-1]=.1
   self.assertEqual(m.conservative_pyramid(grid)[-1][0][0],(.1,.8))
 def test_invalid_support_falls_back(self):
  for bound,interval in (((.6,.2),(.3,.4)),((0.,1.),(.5,float('nan')))):
   self.assertFalse(m.excludes_native_interval(bound,interval))
 def test_raw_mc_dh_min_does_not_preserve_linear_order(self):
  def depth(z,n,f): return f/(f-n)-f*n/((f-n)*z)
  mc=depth(10.,.05,256.); dh=depth(100.,4.,4096.)
  self.assertLess(10.,100.); self.assertGreater(mc,dh)
 def test_parity_atlas_contains_every_tile_without_overlap(self):
  for w,h in ((1920,1200),(1,17),(17,1),(7,5)):
   plan=m.atlas_plan(w,h); seen=[]
   for parity,atlas in enumerate(plan['atlases']):
    tiles=atlas['tiles']
    for tile in tiles:
     seen.append(tile['level']); self.assertEqual(tile['level']%2,parity)
     self.assertLessEqual(tile['x']+tile['width'],atlas['width'])
     self.assertLessEqual(tile['y']+tile['height'],atlas['height'])
    for i,a in enumerate(tiles):
     for b in tiles[i+1:]:
      self.assertTrue(a['x']+a['width']<=b['x'] or b['x']+b['width']<=a['x'] or a['y']+a['height']<=b['y'] or b['y']+b['height']<=a['y'])
   self.assertEqual(sorted(seen),list(range(len(plan['levels']))))
 def test_full_budget_includes_base_scratch_writes_and_child_reads(self):
  p=m.atlas_plan(1920,1200)
  self.assertGreaterEqual(p['allocated_bytes'],p['logical_bytes'])
  self.assertEqual(p['minimum_traffic_bytes'],1920*1200*4+p['reduction_child_reads']*8+p['output_cells']*8)
  self.assertEqual(p['draws_per_domain'],12); self.assertEqual(p['consumer_sampler_groups'],2)
 def test_reject_empty_dimensions(self):
  with self.assertRaises(ValueError): m.atlas_plan(0,1200)
 def test_ssr_z_bound_keeps_sphere_touch_and_uncertainty(self):
  self.assertTrue(m.ssr_z_exclusion_math(-10.,(-20.,-15.),4.))
  self.assertFalse(m.ssr_z_exclusion_math(-10.,(-20.,-15.),5.))
  self.assertFalse(m.ssr_z_exclusion_math(-10.,(-20.,-5.),.01))
  self.assertFalse(m.ssr_z_exclusion_math(-10.,(-15.,-20.),4.))
 def test_binary_hit_result_cannot_replace_continuous_shadow(self):
  # Illustrative algebra only: native reversed smoothstep remains unqualified.
  # Continuous attenuation at zDelta .25 differs from both binary outcomes.
  t=.25/.5; attenuation=t*t*(3.-2.*t)
  self.assertNotEqual(1.-attenuation,0.); self.assertNotEqual(1.-attenuation,1.)

if __name__=='__main__': unittest.main()
