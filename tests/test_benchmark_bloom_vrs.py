import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_bloom_vrs as vrs

class BloomVrsTests(unittest.TestCase):
    def status(self, active=True, count=10):
        return dict(requested=active,hardware_available=True,qualified_draws=count,
                    backend=dict(failed=False,activated_draws=count,restored_scopes=count))

    def test_executed_candidate_and_quiet_controls(self):
        vrs.progress(self.status(),active=True,before=self.status(count=5))
        vrs.progress(self.status(False),active=False,before=self.status(False))
        for active,before,after in [(True,self.status(),self.status()),
                                    (False,self.status(False,count=5),self.status(False))]:
            with self.assertRaises(ValueError):vrs.progress(after,active=active,before=before)

    def test_reject_missing_capability_failed_backend_or_unrestored_scope(self):
        for key,value in [('hardware_available',False),('requested',False),('qualified_draws',0)]:
            status=self.status();status[key]=value
            with self.assertRaises(ValueError):vrs.progress(status,active=True)
        for key,value in [('failed',True),('restored_scopes',9),('activated_draws',None)]:
            status=self.status();status['backend'][key]=value
            with self.assertRaises(ValueError):vrs.progress(status,active=True)

    def test_identity_rejects_advanced_material_options(self):
        with patch.object(vrs.bsl,'validate',return_value={'advanced_material':'true'}):
            with self.assertRaisesRegex(ValueError,'non-PBR'):vrs.identity('pack','options')

    def test_native_selection_and_no_other_feature_required(self):
        state=dict(pack='original.zip',effective_options={'AA':'native'},installed=dict(master=True,flags={'other':'false'}))
        contract={'native_options':state['effective_options']}
        vrs.activation(state,contract,'original.zip')
        for change in [dict(pack='generated.zip'),dict(effective_options={}),dict(installed={'master':False,'flags':{'other':'false'}}),dict(installed={'master':True,'flags':{'other':'true'}})]:
            altered=copy.deepcopy(state);altered.update(change)
            with self.assertRaises(ValueError):vrs.activation(altered,contract,'original.zip')

if __name__=='__main__':unittest.main()
