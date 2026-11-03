import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import run_benchmark as benchmark


class CpuAffinityContractTest(unittest.TestCase):
    def window(self):
        return {'completed_render_frames':100,'render_cpu_affinity':{'requested_cpu':4,'original_cpus':[0,1,2,3,4,5,6,7],'applied':True,'restored':True,'error':'','inherited_restricted_tasks':[]},
                'cpu_placement':{'requested':True,'error':'','pending':False,'unknown_heads':0,'unknown_tails':0,'changed_boundary_cpu':0,'starts':100,'ends':100,
                                 'head_cpus':{'4':100},'tail_cpus':{'4':100}}}

    def test_actual_pin_restore_and_every_recorded_boundary_required(self):
        benchmark.affinity_window(self.window(),4)
        for field,value in [('restored',False),('applied',False),('inherited_restricted_tasks',['new']),('error','restore failed')]:
            row=self.window();row['render_cpu_affinity'][field]=value
            with self.assertRaisesRegex(ValueError,'apply/restore'):benchmark.affinity_window(row,4)
        for field,value in [('head_cpus',{'0':1,'4':99}),('unknown_tails',1),('ends',99),('pending',True)]:
            row=self.window();row['cpu_placement'][field]=value
            with self.assertRaisesRegex(ValueError,'boundary placement'):benchmark.affinity_window(row,4)


if __name__=='__main__':unittest.main()
