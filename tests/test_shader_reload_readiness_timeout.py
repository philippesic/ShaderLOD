import sys,time,tempfile,json,unittest
from pathlib import Path
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import run_benchmark as r

class ReloadStatusTimeoutTest(unittest.TestCase):
 def test_owned_shader_readiness_can_outlast_normal_status_timeout(self):
  for pending in ('owned-diagnostic-pack',False):
   with tempfile.TemporaryDirectory() as directory:
    root=Path(directory);(root/'receipts').mkdir();clock=[0.0]
    op=Mock();op.output=root;op.data={'mutation_pending':pending};op.command.return_value={'id':'request'}
    control=r.Control(op,Mock(),1000)
    def wait(seconds,**kwargs):
     clock[0]+=seconds
     if clock[0]>=120:(root/'receipts/request.json').write_text(json.dumps({'request_id':'request','success':True}))
    control.wait=wait
    with patch.object(r.time,'monotonic',side_effect=lambda:clock[0]):
     if pending:self.assertTrue(control.send('status')['success'])
     else:
      with self.assertRaises(TimeoutError):control.send('status')
    self.assertLess(clock[0],181)

if __name__=='__main__':unittest.main()
