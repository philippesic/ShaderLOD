import sys,time,unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_c01_scene as p

class SceneCaptureTest(unittest.TestCase):
 def test_positive_actual_work_selects_and_always_disarms(self):
  row={'frames':16,'remaining_frames':0,'native_indices':200,'cpu_hidden_indices':120,'proxy_queries':2}
  control=Mock();control.end=time.monotonic()+900
  def send(action,**fields):
   if action=='c01_natural_site':return {'natural_site':{'x':1,'y':2,'z':3}}
   return {'c01_bounds':row}
  control.send.side_effect=send;identity={'group':32}
  result=p.find(control,{'sites':[{}]},identity)
  self.assertEqual(result['selected']['hidden_index_fraction'],.6)
  self.assertEqual(control.send.call_args.kwargs,{'mode':'off'})
 def test_visibility_cannot_accept_false_hidden_sample(self):
  control=Mock();control.send.return_value={'c01_bounds':{'frames':32,'remaining_frames':0,'proxy_queries':32,
   'false_hidden_observations':1,'records':[{'proxy_passed':False,'native_samples':1}]}}
  with self.assertRaises(ValueError):p.visibility(control,{'prefix':1,'group':32,'delay':256})
  self.assertEqual(control.send.call_args.kwargs,{'mode':'off'})

if __name__=='__main__':unittest.main()
