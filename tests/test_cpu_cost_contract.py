import copy
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_cpu_cost as probe

class CpuCostContractTest(unittest.TestCase):
    def row(self):
        cost=dict(calls=30,accepted=30,thread_cpu_ns=100,wall_ns=200)
        return dict(active=False,error='',completed_render_frames=128,
                    cache_before=dict(validations=4),cache_after=dict(validations=4),
                    cost=dict(enabled=True,observing=False,error='',compaction=cost,scalar=dict(cost),
                              **{name:dict(cost) for name in ['compactor_body','scalar_publication','scalar_lifecycle',
                                  'native_fill_inclusive','maximum_query_inclusive','native_submission_delegate']},
                              clock_baseline=dict(cost,calls=4096,accepted=4096)))

    def test_requires_warm_accepted_cost_and_stopped_interval(self):
        probe.validate(self.row())
        for path,value in [(('active',),True),(('completed_render_frames',),127),(('cost','observing'),True),
                           (('cost','scalar','accepted'),29),(('cache_after','validations'),5),(('cost','compactor_body','calls'),0),
                           (('cost','clock_baseline','calls'),4095),(('cost','native_submission_delegate','accepted'),29)]:
            row=self.row();target=row
            for key in path[:-1]:target=target[key]
            target[path[-1]]=value
            with self.subTest(path=path),self.assertRaises(ValueError):probe.validate(row)

    def test_failed_control_always_disarms(self):
        c=Mock();c.send.side_effect=[{},RuntimeError('status lost'),{}]
        with self.assertRaises(RuntimeError):probe.capture(c)
        self.assertEqual({'stop':True},c.send.call_args.kwargs)

    def test_mode_transitions_require_revalidation_and_ignored_mode_hit(self):
        transitions=[dict(dh_opt_in=False,allowed=True,before=dict(validations=1,hits=2),after=dict(validations=1,hits=3)),
                     dict(dh_opt_in=True,allowed=True,before=dict(validations=1,hits=3),after=dict(validations=2,hits=3)),
                     dict(dh_opt_in=False,allowed=True,before=dict(validations=2,hits=3),after=dict(validations=3,hits=3))]
        row=dict(dh_installed=False,properties_restored=True,allowed_after_restore=True,dh_transitions=transitions,
                 irrelevant_modes=dict(allowed=True,before=dict(validations=3,hits=3),after=dict(validations=3,hits=4)))
        probe.validate_transitions(row)
        bad=copy.deepcopy(row);bad['dh_transitions'][1]['after']['validations']=1
        with self.assertRaises(ValueError):probe.validate_transitions(bad)
        bad=copy.deepcopy(row);bad['irrelevant_modes']['after']['validations']=5
        with self.assertRaises(ValueError):probe.validate_transitions(bad)
