"""Fixed general-system pilot gates and paired analysis; no build, client or GPU."""
import copy
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
from unittest import mock

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import run_shared_lod_benchmark as pilot


class SharedBenchmarkTests(unittest.TestCase):
    def fixture(self):
        path=ROOT/'dev/logs/shared-lod-system-qa-r2/smoke-workload.json'
        if not path.exists():self.skipTest('Local accepted runtime fixture absent')
        report=json.loads(path.read_text())
        report['backend']='ELAPSED_ALTERNATING';report['elapsed_seconds']=20.01
        report['gpu_samples']=[dict(scope='game-render',milliseconds=10+i%3,frameId=1001+5*i,epoch=2) for i in range(40)]
        report['runtime_properties']['shaderlod.dev.gpuTiming']='true'
        for key in pilot.OFF_PROPERTIES:
            report['runtime_properties']['shaderlod.dev.'+key]='false'
        return report

    def test_exact_reviewed_runtime_equivalence_and_mutated_content_rejection(self):
        if not (ROOT/'dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar').exists() or not (ROOT/'dev/logs/shared-lod-benchmark-r1/003-b1-adaptive-measurement.json').exists():
            self.skipTest('Pinned local runtime/failed-block fixtures absent')
        equivalence=pilot.verified_equivalences();children={}
        for variant in equivalence['entry']['variants']:
            with zipfile.ZipFile(ROOT/'dev/runtime'/variant['parent_filename']) as archive:
                children[variant['parent_mod_id']]=archive.read(variant['embedded_path'])
        pilot.verify_embedded_payloads(equivalence,children)
        reports=[json.loads((ROOT/'dev/logs/shared-lod-benchmark-r1'/name).read_text()) for name in
            ('001-b1-original-measurement.json','003-b1-adaptive-measurement.json')]
        original,records=pilot.runtime_identities(reports[0],equivalence)
        adaptive,records=pilot.runtime_identities(reports[1],equivalence)
        self.assertEqual(original,adaptive);self.assertEqual(len(records),1)
        with zipfile.ZipFile(io.BytesIO(children['iris'])) as archive:files={i.filename:archive.read(i) for i in archive.infolist() if not i.is_dir()}
        member=next(n for n in files if n.endswith('.class'));files[member]+=b'mutated executable bytes'
        buffer=io.BytesIO()
        with zipfile.ZipFile(buffer,'w') as archive:
            for name,data in files.items():archive.writestr(name,data)
        with self.assertRaisesRegex(ValueError,'raw bytes'):pilot.verify_embedded_payloads(equivalence,children|{'iris':buffer.getvalue()})
        bad=copy.deepcopy(reports[1]);bad['loaded_mod_artifacts']['fabric_api_base']['sha256']='0'*64
        with self.assertRaisesRegex(ValueError,'Unknown equivalence'):pilot.runtime_identities(bad,equivalence)
        bad=copy.deepcopy(reports[1]);bad['loaded_mod_artifacts']['iris']['sha256']='0'*64
        with self.assertRaisesRegex(ValueError,'parent mismatch'):pilot.runtime_identities(bad,equivalence)

    def test_exact_mapping_balanced_positions_and_frozen_timing_command(self):
        rows=pilot.plan(ROOT/'run-workloads/fixture')
        self.assertEqual(len(rows),9);self.assertEqual(len({r['profile'] for r in rows}),9)
        self.assertEqual([[r['arm'] for r in rows if r['block']==b] for b in (1,2,3)],list(map(list,pilot.ORDERS)))
        for arm in pilot.CASES:
            selected=[r for r in rows if r['arm']==arm]
            self.assertEqual({r['position'] for r in selected},{1,2,3})
            self.assertEqual({r['case'] for r in selected},{pilot.CASES[arm][1]})
        for row in rows:
            for key in pilot.OFF_PROPERTIES:self.assertIn('-P'+key+'=false',row['command'])
            for key in ('-PgpuTiming=true','-PgpuSampleInterval=5','-PcomparisonFullscreen=false','-PinitialWarmup=25','-PmeasurementSeconds=20'):self.assertIn(key,row['command'])
            self.assertIn('compileJava',row['command']);self.assertIn('compileDevClientJava',row['command'])

    def test_registered_only_option_and_source_acceptance(self):
        report=self.fixture();self.assertTrue(pilot.validate_pilot(report,'adaptive')['frame_only'])
        for name,value in (('CLOUDS_CUMULUS','false'),('SHADERLOD_UNKNOWN','true'),(pilot.prep.consumer.SWITCH,'false')):
            bad=copy.deepcopy(report);bad['effective_shader_options'][name]=value
            with self.assertRaisesRegex(ValueError,'Exact loaded options'):pilot.validate_pilot(bad,'adaptive')
        bad=copy.deepcopy(report);bad['actual_shader_sha256']='different'
        with self.assertRaisesRegex(ValueError,'source archive'):pilot.validate_pilot(bad,'adaptive')
        with self.assertRaisesRegex(ValueError,'Unknown'):pilot.validate_pilot(report,'combined')
        original=copy.deepcopy(report);original['actual_shader_sha256']=pilot.prep.consumer.BASE_SHA256
        original['effective_shader_options'].pop(pilot.prep.consumer.SWITCH)
        pilot.validate_pilot(original,'original')
        pilot.validate_pilot(report,'disabled')

    def test_window_backend_observer_and_actual_frame_cadence_rejections(self):
        report=self.fixture()
        for mutate,reason in (
            (lambda r:r['window_state']['capture']['focused'].update(true_observations=1),'focused'),
            (lambda r:r['window_state']['capture']['framebuffer_size_counts'].update({'1920x1200':1}),'framebuffer'),
            (lambda r:r['runtime_properties'].update({'shaderlod.dev.cloudSamplerProbe':'true'}),'explicitly off'),
            (lambda r:r.update(backend='TIMESTAMP'),'backend'),
            (lambda r:r['gpu_samples'][0].update(scope='iris/deferred1'),'GPU samples'),
            (lambda r:r['gpu_samples'][0].update(epoch=1),'GPU samples'),
            (lambda r:r['gpu_samples'][0].update(frameId=1002),'cadence'),
        ):
            bad=copy.deepcopy(report);mutate(bad)
            with self.assertRaisesRegex(ValueError,reason):pilot.validate_pilot(bad,'adaptive')

    def test_metrics_use_elapsed_frame_and_total_wall_not_summed_passes(self):
        report=self.fixture();report['cpu_samples']=[dict(scope='run-tick',wallMilliseconds=x) for x in (15,20,25)]+[dict(scope='game-render',wallMilliseconds=99)]
        metrics=pilot.metrics(report)
        self.assertEqual(metrics['total_frame_wall']['median_ms'],20)
        self.assertEqual(metrics['total_frame_wall']['p95_ms'],25)
        self.assertEqual(metrics['gpu_elapsed']['samples'],40)

    def test_paired_run_level_analysis_positive_negative_and_incomplete(self):
        rows=pilot.plan(ROOT/'run-workloads/fixture')
        for row in rows:
            value={'original':20,'disabled':20,'adaptive':15}[row['arm']]
            row.update(status='complete',metrics=dict(gpu_elapsed=dict(median_ms=value,p95_ms=value*1.1),
                total_frame_wall=dict(median_ms=value,p95_ms=value*1.2),elapsed_per_rendered_frame_ms=value))
        analysis=pilot.analyze(rows);self.assertTrue(analysis['useful_consistent_signal'])
        self.assertEqual(analysis['comparisons']['original_to_adaptive']['gpu_elapsed_median_ms']['per_block_percent_time_reduction'],[25]*3)
        self.assertEqual(analysis['comparisons']['original_to_adaptive']['total_throughput_ratio']['per_block'],[20/15]*3)
        bad=copy.deepcopy(rows);bad[2]['metrics']['gpu_elapsed']['median_ms']=22
        self.assertFalse(pilot.analyze(bad)['useful_consistent_signal'])
        bad[3]['status']='failed';self.assertEqual(pilot.analyze(bad)['status'],'incomplete')
        self.assertFalse(pilot.analyze([])['useful_consistent_signal'])

    def test_fresh_ignored_path_and_warmup_only_action(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve();base=root/'ignored';base.mkdir()
            self.assertEqual(pilot.ignored_path(base/'fresh',base),base/'fresh')
            with self.assertRaisesRegex(ValueError,'ignored'):pilot.ignored_path(root/'tracked',base)
            with self.assertRaisesRegex(ValueError,'fresh'):pilot.ignored_path(base,root)
            console=base/'console.log';console.write_text('ShaderLOD landscape camera applied: fixture')
            event=base/'focus.json'
            with mock.patch.object(pilot.subprocess,'run',return_value=mock.Mock(returncode=0,stderr='')) as activation,mock.patch.object(pilot.matrix,'minecraft_processes',return_value=[123]):
                stop,thread,result=pilot.arm_focus(console,event);thread.join(3);self.assertEqual(result['status'],'complete')
                activation.assert_called_once();self.assertEqual(result['client_java_pids'],[123])
            console.write_text('ShaderLOD workload measurement started: fixture')
            with mock.patch.object(pilot.subprocess,'run') as activation:
                stop,thread,result=pilot.arm_focus(console,event);thread.join(3)
                self.assertEqual(result['status'],'failed');activation.assert_not_called()


if __name__=='__main__':unittest.main()
