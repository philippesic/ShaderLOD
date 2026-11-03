import importlib.util
from pathlib import Path
import unittest
import copy
spec=importlib.util.spec_from_file_location('abba',Path(__file__).parents[1]/'scripts/linux_abba_check.py')
a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)
class BracketTests(unittest.TestCase):
 def receipts(self, values):
  state=dict(client_pid=1,client_started_at='same',camera={},width=960,height=600,distance=8,day_time=6000,rain_level=0,selected_resource_packs=[],loaded_stack={},vsync=False,fps_limit=260,fullscreen=False,effective_options={'AA_TYPE':'2'})
  return [dict(window=dict(valid=True,wall_ns=20e9,statistics={'medianMs':v},count_throughput_fps=1000/v),state=state|dict(at_ns=i,pack='A' if i in (0,3) else 'B',installed={'master':i in (1,2)})) for i,v in enumerate(values)]
 def test_balanced_control_and_noise_rejection(self):
  self.assertTrue(a.evaluate(self.receipts([10,8,8,10]))['distinguishable'])
  self.assertFalse(a.evaluate(self.receipts([10,9.9,9.9,10.2]))['distinguishable'])
  self.assertFalse(a.evaluate(self.receipts([10,8,8,12]))['distinguishable'])
 def test_quality_vector_and_short_capture_rejected(self):
  r=self.receipts([10,8,8,10]);r[1]['state']['effective_options']={'AA_TYPE':'0'}
  with self.assertRaises(ValueError):a.evaluate(r)
  r=self.receipts([10,8,8,10]);r[0]['window']['wall_ns']=1e9
  with self.assertRaises(ValueError):a.evaluate(r)
