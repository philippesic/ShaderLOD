import copy,sys,unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_private_off as p

class PrivateOffResetTest(unittest.TestCase):
 def fixture(self):return {'pack':'private.zip','pipeline_identity':1,'pack_identity':2,'effective_options':{'native':'yes'},'installed':{'master':False,'flags':{'inherited':'true'}}}
 def test_empty_requests_without_loaded_input_or_pipeline_change(self):
  before=self.fixture();after=copy.deepcopy(before);after['installed']['flags']={}
  c=Mock();c.send.return_value={'state':before};c.ready.return_value=after
  p.clear(c,'private.zip','a'*64)
  self.assertEqual(c.send.call_args.args,('diagnostic_empty_requests',))
  self.assertEqual(c.send.call_args.kwargs,{'pack':'private.zip','sha256':'a'*64})
 def test_on_or_changed_pipeline_is_refused(self):
  for altered in ('on','pipeline'):
   before=self.fixture();after=copy.deepcopy(before);after['installed']['flags']={}
   if altered=='on':before['installed']['master']=True
   else:after['pipeline_identity']=3
   c=Mock();c.send.return_value={'state':before};c.ready.return_value=after
   with self.assertRaises(ValueError):p.clear(c,'private.zip','a'*64)

if __name__=='__main__':unittest.main()
