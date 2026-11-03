import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_iteration as b

class GpuModeContractTest(unittest.TestCase):
 def test_gpu_path_requires_actual_query_progress_without_cpu_reads(self):
  for mode in ('deferred','deferred_wait'):
   before={'mode':mode,'remaining_frames':200,'frames':2,'proxy_queries':4,'cpu_result_reads':0}
   after=before|{'frames':4,'proxy_queries':8}
   b.c01_work(after,active=True,before=before,mode=mode)
   for wrong in (after|{'cpu_result_reads':1},before,after|{'mode':'deferred_cpu'}):
    with self.assertRaises(ValueError):b.c01_work(wrong,active=True,before=before,mode=mode)
 def test_cpu_control_still_requires_read_progress(self):
  before={'mode':'deferred_cpu','remaining_frames':200,'frames':2,'proxy_queries':4,'cpu_result_reads':4}
  b.c01_work(before|{'frames':4,'proxy_queries':8,'cpu_result_reads':8},active=True,before=before)
  with self.assertRaises(ValueError):b.c01_work(before|{'frames':4,'proxy_queries':8},active=True,before=before)

if __name__=='__main__':unittest.main()
