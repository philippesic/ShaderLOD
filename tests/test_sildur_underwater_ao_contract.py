import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_sildur_underwater_ao as contract


class SildurUnderwaterContractTest(unittest.TestCase):
    def packet(self,active=False):
        fragment='uniform int isEyeInWater;'+(contract.producer.REPLACEMENT if active else contract.producer.ANCHOR).decode().replace('ao_strength','1.0')
        return {'actual':{'settled_rearm':True,'sources':[{'name':'deferred','fragment_source':fragment,'vertex_source':'native quad'},
                {'name':'sodium_cutout','fragment_source':'native terrain','vertex_source':'native vertex'}],
                'events':[{'kind':'completed_quad','frame':f,'name':'deferred','sildur_ao_uniform':{'location':4,'is_eye_in_water':1}} for f in [1,2]]}}

    def test_full_source_and_actual_underwater_uniform_required(self):
        observations={'native':self.packet(),'candidate':self.packet(True),'return':self.packet()}
        contract.assess(observations)
        bad=copy.deepcopy(observations);bad['candidate']['actual']['sources'][1]['fragment_source']+='different'
        with self.assertRaisesRegex(ValueError,'another attached operation'):contract.assess(bad)
        for value in [0,2,True,None]:
            bad=self.packet(True);bad['actual']['events'][0]['sildur_ao_uniform']['is_eye_in_water']=value
            with self.assertRaisesRegex(ValueError,'underwater'):contract.source_work(bad,active=True)
        bad=self.packet(True);bad['actual']['settled_rearm']=False
        with self.assertRaisesRegex(ValueError,'Settled'):contract.source_work(bad,active=True)


if __name__=='__main__':unittest.main()
