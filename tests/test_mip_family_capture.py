import sys,unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_mip_family as p

class CaptureTest(unittest.TestCase):
 def test_atomic_selected_capture_has_one_reload_and_disarms_on_rejection(self):
  row={'stopped':True,'completed_frames':2,'sources':[{}],'events':[{}]}
  selected={'pack':'native.zip','sha256':'a'*64,'dimension':'minecraft:overworld'}
  control=Mock();control.send.side_effect=[{}, {'mip_family':row},{'graph_export':'owned'},{}]
  p.capture(control,selection=selected)
  self.assertEqual(control.send.call_args_list[0].kwargs,dict(observe=True,reload=True,**selected))
  self.assertEqual(sum(c.kwargs.get('reload',False) for c in control.send.call_args_list),1)
  for failure in (ValueError('identity rejected'),RuntimeError('client terminal')):
   control=Mock();control.send.side_effect=[failure,{}]
   with self.assertRaises(type(failure)):p.capture(control,selection=selected)
   self.assertEqual(control.send.call_args.kwargs,{'observe':False})
 def test_settled_rearm_preserves_initial_receipt_without_second_reload(self):
  initial={'stopped':True,'completed_frames':2,'sources':[{}],'events':[{}]}
  settled=initial|{'settled_rearm':True,'settled_screenshot':'owned.png'}
  control=Mock();control.send.side_effect=[{}, {'mip_family':initial},{}, {'mip_family':settled},{'graph_export':'owned'},{}]
  packet=p.capture(control,settled=True)
  self.assertEqual(packet['initial_after_reload_actual'],initial)
  self.assertEqual(packet['actual'],settled)
  calls=control.send.call_args_list
  self.assertEqual(sum(c.kwargs.get('reload',False) for c in calls),1)
  self.assertEqual(sum(c.kwargs.get('rearm_settled',False) for c in calls),1)
  control.wait.assert_called_once_with(5)

 def test_completed_boundaries_and_sources_are_required(self):
  row={'stopped':True,'completed_frames':2,'sources':[{}],'events':[{}]}
  control=Mock();control.send.side_effect=[{}, {'mip_family':row},{'graph_export':'owned'},{ }]
  self.assertEqual(p.capture(control)['parsed_graph_export'],'owned')
  self.assertEqual(control.send.call_args.kwargs,{'observe':False})
  for bad in (row|{'error':'epoch changed'},row|{'completed_frames':1},row|{'sources':[]}):
   control=Mock();control.send.side_effect=[{}, {'mip_family':bad},{}]
   with self.assertRaises(ValueError):p.capture(control)
   self.assertEqual(control.send.call_args.kwargs,{'observe':False})

if __name__=='__main__':unittest.main()
