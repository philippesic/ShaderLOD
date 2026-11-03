"""Fresh water cost recipe checks; no client/build/timing rows."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import prepare_water_shadow_control as prep


class WaterShadowControlRuntimeTests(unittest.TestCase):
    def test_owned_fresh_recipe_hold_and_four_phases(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);out=root/'run-workloads/water-shadow-control-r1';build=root/'build/water-shadow-control-r1'
            plan=prep.plan(out,build,root)
            self.assertEqual(plan['feature'],'water_shadow_zero');self.assertEqual(plan['phases'],[False,True,False,True])
            self.assertEqual((plan['initial_warmup_seconds'],plan['settle_seconds'],plan['measurement_seconds']),(30,8,20))
            self.assertEqual(plan['minimum_session_seconds'],142);self.assertEqual(plan['minimum_positive_unique_gpu_samples_per_phase'],60)
            self.assertFalse(plan['launch_authorized']);self.assertFalse(plan['performanceEvidence']);self.assertIn('HOLD',plan['launch_status'])
            self.assertFalse(plan['water_stage_runtime_qualification']);self.assertFalse(plan['executed_skip_coverage_proof'])
            self.assertFalse(out.exists());out.mkdir(parents=True)
            with self.assertRaisesRegex(ValueError,'Preserve'):prep.plan(out,build,root)

    def test_exact_new_feature_commands_no_observer_or_workload_mix(self):
        argv=prep.command(ROOT/'run-workloads/water-shadow-control-r1/coastalwater/toggle')
        self.assertEqual(argv[:2],['./gradlew','runClientLodOn'])
        for flag in ('gpuBenchmark=true','benchmarkOnly=true','benchmarkFeature=water_shadow_zero','benchmarkStartEnabled=false',
            'initialWarmup=30','benchmarkSettleSeconds=8','measurementSeconds=20','gpuSampleInterval=5','gpuTiming=true','cpuTiming=true',
            'measureWorkload=false','measureOnly=false','cameraQuality=','performanceStack=minimal','includeDh=false'):
            self.assertIn('-P'+flag,argv)
        for name in prep.OFF:
            if name not in ('gpuTiming','cpuTiming','gpuBenchmark','benchmarkOnly'):self.assertIn('-P'+name+'=false',argv)
        self.assertNotIn('-PgpuBenchmark=false',argv);self.assertNotIn('-PbenchmarkOnly=false',argv)
        self.assertEqual(len([v for v in argv if v.startswith('-PbenchmarkFeature=')]),1)
        for task in prep.canonical.BUILD_TASKS:self.assertEqual(argv[argv.index(task)-1],'-x')

    def manifest(self):
        source=prep.water.qualify_archive();options=source['reviewed_inventory']['canonical_enabled']
        return dict(schema=1,scene='coastalwater',configuration_only=False,source_world_sha256=prep.analyzer.WORLD,
            source_qualification=source,experiment_arms={'toggle':dict(archive_sha256=prep.analyzer.ARCHIVE,
                expected_effective_options=options,expected_effective_options_sha256=prep.analyzer.OPTIONS_HASH)},
            benchmark_recipe=dict(feature='water_shadow_zero',phases=[False,True,False,True]))



    def test_all_seven_prepared_effects_required_and_not_shader_consumption(self):
        with tempfile.TemporaryDirectory() as temporary:
            profile=Path(temporary);(profile/'config').mkdir()
            path=profile/'config/shaderlod-effects-client.toml';path.write_text(prep.baseline.coverage.effects_config('inherit'))
            result=prep.validate_effects(profile);self.assertEqual(result['count'],7);self.assertIn('opaque_lighting',result['names'])
            self.assertIn('runtime guard',result['source'])
            path.write_text(path.read_text().replace('mode="INHERIT"','mode="ADAPTIVE"',1))
            with self.assertRaisesRegex(ValueError,'INHERIT'):prep.validate_effects(profile)

    def test_screen_prerequisites_and_denominator_no_relabeling(self):
        text=json.dumps(prep.postanalysis_plan())
        for value in ('independent','actual','sevenINHERIT','schema3','source-definedboundaryomission','Both ON','OFF drift<=10%',
            'no original-native claim','no adaptive_pack_sha256','No physical skip counts','quiet CPU'):
            self.assertIn(value,text)
        self.assertFalse(prep.postanalysis_plan()['performanceEvidence'])


if __name__=='__main__':unittest.main()
