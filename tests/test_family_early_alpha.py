import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_iteration as iteration

class FamilyEarlyAlphaTest(unittest.TestCase):
    def state(self,n=12,ready=True,owners=1):
        family='sildurs-vibrant-shaders'
        return {'installed':{'family':family,'family_early_alpha':{'family':family,'counters':{
            'qualified_draws':n,'source_ready':ready,'owned_programs':owners},'status':'actual status'}}}

    def test_owned_permission_progress_and_native_no_progress(self):
        iteration.family_early_alpha_work(self.state(),family='sildurs-vibrant-shaders',active=True,before=self.state(11))
        iteration.family_early_alpha_work(self.state(0,False,0),family='sildurs-vibrant-shaders',active=False,before=self.state(0,False,0))

    def test_flags_without_owner_source_or_progress_refused(self):
        for state in [self.state(0),self.state(ready=False),self.state(owners=0)]:
            with self.assertRaises(ValueError):iteration.family_early_alpha_work(state,family='sildurs-vibrant-shaders',active=True)
        with self.assertRaises(ValueError):iteration.family_early_alpha_work(self.state(),family='sildurs-vibrant-shaders',active=True,before=self.state())
        with self.assertRaises(ValueError):iteration.family_early_alpha_work(self.state(),family='sildurs-vibrant-shaders',active=False,before=self.state(0))
        with self.assertRaises(ValueError):iteration.family_early_alpha_work(self.state(),family='astralex',active=True)

    def test_generic_entry_uses_matching_family_counters(self):
        iteration.early_alpha_work(self.state(),before=self.state(11))
