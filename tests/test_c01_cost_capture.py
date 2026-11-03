import copy
import sys
from pathlib import Path
import unittest
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_c01_cost as diagnostic


class CostCaptureTest(unittest.TestCase):
    def fixture(self):
        return {'work':{'frames':128,'remaining_frames':0,'cpu_result_reads':128,
                        'cost':{'enabled':True,'observing':True,'dropped':0,
                                'rows':[{'frame':1,'iris_frame':101,'thread_cpu_ns':100,'wall_ns':200}]}},
                'observation':{'remaining_frames':0,'pending_gpu_queries':0,'frames':[{'iris_frame':n} for n in range(101,229)]}}

    def test_invalid_clocks_drops_and_membership_always_disarm(self):
        for field,value in [('thread_cpu_ns',-1),('frame',0),('frame',129),('iris_frame',9999)]:
            row=self.fixture();row['work']['cost']['rows'][0][field]=value
            control=Mock();control.send.return_value={'c01_cost':row}
            with self.assertRaises(ValueError):diagnostic.capture(control)
            self.assertEqual(control.send.call_args.kwargs,{'observe':False})
        row=self.fixture();row['work']['cost']['dropped']=1
        control=Mock();control.send.return_value={'c01_cost':row}
        with self.assertRaises(ValueError):diagnostic.capture(control)
        self.assertEqual(control.send.call_args.kwargs,{'observe':False})

    def test_pending_gpu_results_are_completed_before_return(self):
        first=self.fixture();first['observation']['pending_gpu_queries']=1
        final=self.fixture();control=Mock()
        control.send.side_effect=[{'c01_cost':first},{'c01_cost':final},{}]
        result=diagnostic.capture(control)
        control.wait.assert_called_once_with(.25)
        self.assertEqual(result['observation']['pending_gpu_queries'],0)
        self.assertEqual(control.send.call_args.kwargs,{'observe':False})


if __name__=='__main__':unittest.main()
