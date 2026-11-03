"""Refuse CPU feature timing conclusions without actual consumer work."""
import unittest
import sys
from pathlib import Path
from types import SimpleNamespace
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_iteration as iteration
import run_benchmark as runner

class CpuBenchmarkTest(unittest.TestCase):
    def state(self, **changes):
        work=dict(enabled=True,armed=False,invalid_compaction_metadata=0,
                  compaction_eligible_batches=9,scalar_hits=3)
        work.update(changes)
        return {'installed':{'compaction_admitted':True,'renderer_work':work}}

    def test_consuming_pair_and_triple(self):
        iteration.cpu_work(self.state(),features=['quad_command_compaction','shadow_admission_cache','index_scalar_cache'],active=True,
                           after={'admission_cache_window':{'before':{'hits':100},'after':{'hits':109}}})

    def test_cache_lifetime_hits_are_not_interval_progress(self):
        for before,after in [(None,None),(None,{'admission_cache_window':{'before':{'hits':100},'after':{'hits':100}}}),
                             (None,{'admission_cache_window':{'before':{'hits':100},'after':{'hits':99}}})]:
            with self.subTest(before=before,after=after),self.assertRaises(ValueError):
                iteration.cpu_work(self.state(),features=['shadow_admission_cache'],active=True,before=before,after=after)

    def test_refuses_missing_refused_or_armed_work(self):
        for change in [dict(enabled=False),dict(armed=True),dict(invalid_compaction_metadata=1),dict(compaction_eligible_batches=0),dict(scalar_hits=0)]:
            with self.subTest(change=change),self.assertRaises(ValueError):
                iteration.cpu_work(self.state(**change),features=['quad_command_compaction','index_scalar_cache'],active=True)
        with self.assertRaises(ValueError):
            iteration.cpu_work({'installed':{'renderer_work':{}}},features=['quad_command_compaction'],active=True)

    def test_controls_cannot_consume_candidate(self):
        with self.assertRaises(ValueError):
            iteration.cpu_work(self.state(),features=['quad_command_compaction'],active=False)
        iteration.cpu_work(self.state(compaction_eligible_batches=0,scalar_hits=0),features=['quad_command_compaction'],active=False)

    def test_cpu_subsets_do_not_admit_shader_combinations(self):
        self.assertTrue(runner.cpu_features(SimpleNamespace(features=['quad_command_compaction','shadow_admission_cache'])))
        for features in [[],['shadow_admission_cache','early_alpha']]:
            self.assertFalse(runner.cpu_features(SimpleNamespace(features=features)))

    def test_natural_scene_only_admits_fixed_native_cpu_protocol(self):
        values=dict(standard_test=True,candidate='public',features=['quad_command_compaction','shadow_admission_cache'],dh=False,saved_pose=0)
        args=SimpleNamespace(**values);runner.natural_terrain_inputs(args)
        self.assertEqual('ShaderLOD-Smoke',args.world)
        for changed in [dict(standard_test=False),dict(candidate='c01-bounds'),dict(features=['early_alpha']),dict(dh=True),dict(saved_pose=1)]:
            with self.subTest(changed=changed),self.assertRaises(ValueError):
                runner.natural_terrain_inputs(SimpleNamespace(**(values|changed)))
