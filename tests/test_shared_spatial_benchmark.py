"""Strict spatial measurement mutation fixtures; no client, builds or source parsers."""
import copy
from collections import Counter
import json
from pathlib import Path
import sys
import struct
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'scripts'))
import run_shared_spatial_benchmark as bench


class SpatialBenchmarkTests(unittest.TestCase):
    def fixture(self):
        upstream = {'option'+str(i):'v' for i in range(366)}
        options = upstream | {bench.prep.adapter.SWITCH:'true'}
        request = dict(run_id='fixture', case='spatial-timing-privateON-ADAPTIVE', task='runClientLodOn',
            include_dh=False, measurement_seconds=30., gpu_queries=True, fixed_wetness=0., diagnostic_only=False)
        control = dict(x=256.5,y=95.,z=768.5,yaw=135.,pitch=9.)
        state = dict(framebuffer_width=2976,framebuffer_height=1672,requested_chunks=24,effective_chunks=24,
            shader_pack='enabled.zip',shaders_in_use=True,fallback=False,dimension='minecraft:overworld',
            day_time=7000,rain=0,thunder=0,lod_settings=dict(enabled=True),terrain_configured=False,
            terrain_confirmation=False,**control)
        end = dict(availability='available',focused='false',visible='true',iconified='false',
            window_width=1488,window_height=836,framebuffer_width=2976,framebuffer_height=1672,
            pacing=dict(pause_on_lost_focus=False,fullscreen=False,configured_framerate_limit=260,vsync=False))
        window = dict(observations=100,available_observations=100,unavailable_observations=0,
            transitions=[],transition_counts={},dropped_transitions=0,start=dict(state=end),end=dict(state=end),
            window_size_counts={'1488x836':100},framebuffer_size_counts={'2976x1672':100})
        for field,value in (('focused',False),('visible',True),('iconified',False)):
            window[field] = dict(true_observations=100 if value else 0,false_observations=0 if value else 100,unknown_observations=0)
        wet = dict(mode='fixed',fixedWetness=0.,measurementReads=10,suppliers=[dict(supplierReads=10,
            appliedWetness=0.,measurementReads=10,appliedMin=0.,appliedMax=0.)])
        report = dict(status='complete',request=request,modPresent=True,dhPresent=False,actual_shader_sha256=bench.prep.ARCHIVES['enabled'],
            effective_shader_options=options,effective_shader_options_sha256=bench.prep.matrix.canonical_options_hash(options),
            input_state_start=state,input_state_end=state,control_sha256='control',warmup_seconds=30,
            elapsed_seconds=30.01,render_frames=1500,loaded_mods={'shaderlod':'0.1.0'},
            shadow_culling_active=False,separate_shadow_lists_active=False,shadow_frustum_coherence_active=False,
            runtime_properties={'shaderlod.dev.'+key:'false' for key in bench.OFF},
            backend='ELAPSED_ALTERNATING',sampling_policy='frame only, every sampled frame; sample interval=5 render frames, poll only sampled frames',
            gpu_samples=[dict(scope='game-render',epoch=2,frameId=1001+5*i,milliseconds=10.) for i in range(300)],
            cpu_samples=[dict(scope='run-tick',epoch=2,frameId=1001+i,wallMilliseconds=20.) for i in range(1500)],
            window_state=dict(schema=1,capture=window),runtime_mod_jars_sha256={},
            shader_temporal_inputs_start=dict(wetness=wet),shader_temporal_inputs_end=dict(wetness=wet))
        report['runtime_properties'].update({'shaderlod.dev.gpuTiming':'true','shaderlod.dev.gpuSampleInterval':'5'})
        expected = dict(request=request,archive_sha256=report['actual_shader_sha256'],expected_effective_options=options,
            expected_effective_options_sha256=report['effective_shader_options_sha256'])
        inputs = dict(shader_sha256=report['actual_shader_sha256'],requested_shader_options=options,shader_option_defaults={},
            shader_pack='enabled.zip',control=control,control_sha256='control',shadow_culling_configured=False)
        return report,expected,inputs

    def test_williams_positions_predecessors_and_fresh_jvms(self):
        plan = bench.plan(ROOT/'run-workloads/fixture')
        self.assertEqual(len(plan['runs']),32)
        self.assertEqual(len({r['profile'] for r in plan['runs']}),32)
        for cycle in (plan['orders'][:4],plan['orders'][4:]):
            self.assertTrue(all(Counter(order[position] for order in cycle)==Counter(bench.ARMS) for position in range(4)))
            pairs = Counter((a,b) for order in cycle for a,b in zip(order,order[1:]))
            self.assertEqual(pairs,Counter({(a,b):1 for a in bench.ARMS for b in bench.ARMS if a!=b}))
        self.assertEqual(len(bench.plan(ROOT/'run-workloads/pilot','pilot')['runs']),4)

    def test_minimal_frozen_command_and_no_launch_api(self):
        for row in bench.plan(ROOT/'run-workloads/fixture')['runs']:
            for flag in bench.OFF:self.assertIn('-P'+flag+'=false',row['command'])
            for flag in ('-PcpuTiming=true','-PgpuTiming=true','-PgpuSampleInterval=5','-PinitialWarmup=30','-PmeasurementSeconds=30'):
                self.assertIn(flag,row['command'])
            self.assertEqual(row['command'].count('-x'),4)
        self.assertFalse(hasattr(bench,'run'))

    def test_positive_report_and_exact_options(self):
        report,expected,inputs = self.fixture()
        self.assertIn('property_limits',bench.validate_timing(report,expected,inputs))
        for mutate in (lambda r:r['effective_shader_options'].update(unknown='true'),
                       lambda r:r['effective_shader_options'].pop('option3')):
            bad = copy.deepcopy(report); mutate(bad)
            bad['effective_shader_options_sha256']=bench.prep.matrix.canonical_options_hash(bad['effective_shader_options'])
            with self.assertRaises(ValueError):bench.validate_timing(bad,expected,inputs)

    def test_uuid_source_global_scene_and_temporal_mutations(self):
        for mutate in (lambda r:r['request'].update(run_id='stale'),
                lambda r:r.update(actual_shader_sha256='0'*64),
                lambda r:r['input_state_end']['lod_settings'].update(enabled=False),
                lambda r:r['input_state_end'].update(terrain_configured=True),
                lambda r:r['input_state_end'].update(day_time=7001),
                lambda r:r['input_state_end'].update(x=256.7),
                lambda r:r['shader_temporal_inputs_end']['wetness']['suppliers'][0].update(appliedMax=.1)):
            report,expected,inputs = self.fixture();bad=copy.deepcopy(report);mutate(bad)
            with self.subTest(mutation=mutate):
                with self.assertRaises(ValueError):bench.validate_timing(bad,expected,inputs)

    def test_window_native_pacing_transitions_and_unknown_mutations(self):
        for mutate in (lambda w:w['focused'].update(true_observations=1),
                lambda w:w.update(unavailable_observations=1),
                lambda w:w['framebuffer_size_counts'].update({'1920x1080':1}),
                lambda w:w['start']['state']['pacing'].update(vsync=True),
                lambda w:w['visible'].update(unknown_observations=1),
                lambda w:w['transition_counts'].update(focused=1)):
            report,expected,inputs = self.fixture();bad=copy.deepcopy(report);mutate(bad['window_state']['capture'])
            with self.assertRaises(ValueError):bench.validate_timing(bad,expected,inputs)

    def test_actual_instrumentation_stream_mutations(self):
        for mutate in (lambda r:r['runtime_properties'].update({'shaderlod.dev.cloudSamplerProbe':'true'}),
                lambda r:r.update(diagnostic_only=True),
                lambda r:r.update(performanceEvidence=False),
                lambda r:r['gpu_samples'][0].update(scope='iris/deferred1'),
                lambda r:r['gpu_samples'][0].update(milliseconds=float('nan')),
                lambda r:r['gpu_samples'][0].update(epoch=1),
                lambda r:r['gpu_samples'][0].update(frameId=1002),
                lambda r:r['cpu_samples'][0].update(scope='iris-layer'),
                lambda r:r['cpu_samples'][0].update(wallMilliseconds=0),
                lambda r:r['cpu_samples'][0].update(frameId=1002),
                lambda r:r.update(backend='TIMESTAMP'),
                lambda r:r.update(warmup_seconds=25),
                lambda r:r.update(runtime_mod_jars_sha256={'unexpected.jar':'0'*64})):
            report,expected,inputs=self.fixture();bad=copy.deepcopy(report);mutate(bad)
            with self.assertRaises(ValueError):bench.validate_timing(bad,expected,inputs)

    def synthetic_rows(self,stage='repeated',values=None):
        values = values or {'original':24.,'privateOFF':24.,'privateON-FULL':25.,'privateON-ADAPTIVE':15.}
        rows=bench.plan(ROOT/'run-workloads/fixture',stage)['runs']
        for row in rows:
            v=values[row['arm']]
            row.update(status='complete',metrics=dict(gpu_elapsed=dict(median_ms=v,p95_ms=v*1.1),
                total_frame_wall=dict(median_ms=v,p95_ms=v*1.2),elapsed_per_rendered_frame_ms=v))
        return rows

    def test_original_denominator_metrics_and_quality_requirement(self):
        report,unused,inputs=self.fixture();metrics=bench.common.metrics(report)
        self.assertEqual(metrics['total_frame_wall']['median_ms'],20)
        self.assertEqual(metrics['elapsed_per_rendered_frame_ms'],1000*30.01/1500)
        result=bench.analyze(self.synthetic_rows(),'repeated',quality_accepted=True)
        self.assertTrue(result['timing_target_met']);self.assertTrue(result['acceptance_candidate'])
        self.assertEqual(result['comparisons']['original_to_privateON-ADAPTIVE']['total_throughput_ratio']['per_block'],[1.6]*8)
        self.assertEqual(result['comparisons']['original_to_privateON-FULL']['total_throughput_ratio']['per_block'],[24/25]*8)
        self.assertFalse(bench.analyze(self.synthetic_rows(),'repeated')['acceptance_candidate'])

    def test_single_block_incomplete_duplicate_and_nominal_rate_never_pass(self):
        self.assertFalse(bench.analyze(self.synthetic_rows('pilot'),'pilot',quality_accepted=True)['timing_target_met'])
        self.assertFalse(bench.analyze(self.synthetic_rows()[:4],'repeated',quality_accepted=True)['timing_target_met'])
        rows=self.synthetic_rows(values={a:24 for a in bench.ARMS})
        self.assertFalse(bench.analyze(rows,'repeated',quality_accepted=True)['timing_target_met'])
        with self.assertRaisesRegex(ValueError,'Duplicate'):bench.analyze(rows+[rows[0]],'repeated')

    def test_tail_instability_reversal_and_harmful_pilot_stop(self):
        for mutation in ('tail','spread','reversal'):
            rows=self.synthetic_rows()
            for row in rows:
                if row['arm']=='privateON-ADAPTIVE':
                    if mutation=='tail':row['metrics']['gpu_elapsed']['p95_ms']=30
                    if mutation=='spread' and row['block']%2==0:row['metrics']['gpu_elapsed']['median_ms']=18
                    if mutation=='reversal' and row['block']==1:row['metrics']['elapsed_per_rendered_frame_ms']=25
            self.assertFalse(bench.analyze(rows,'repeated',quality_accepted=True)['timing_target_met'])
        rows=self.synthetic_rows('pilot',{'original':24,'privateOFF':24,'privateON-FULL':31,'privateON-ADAPTIVE':15})
        self.assertTrue(bench.analyze(rows,'pilot')['pilot_stop'])
        self.assertFalse(bench.analyze(self.synthetic_rows('pilot'),'pilot')['pilot_stop'])

    def test_complete_inventory_add_remove_mutate_and_symlink(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'x.class').write_bytes(b'class');(root/'resource').write_bytes(b'resource')
            initial=bench.tree_inventory(root)
            (root/'extra').write_bytes(b'extra');self.assertNotEqual(initial,bench.tree_inventory(root));(root/'extra').unlink()
            (root/'resource').write_bytes(b'drift');self.assertNotEqual(initial,bench.tree_inventory(root));(root/'resource').unlink()
            self.assertNotEqual(initial,bench.tree_inventory(root))
            (root/'link').symlink_to(root/'x.class')
            with self.assertRaisesRegex(ValueError,'Symlink'):bench.tree_inventory(root)

    def ledger_fixture(self,root):
        evidence=root/'evidence';evidence.write_text('runtime evidence')
        reference=dict(path=str(evidence.resolve()),sha256=bench.prep.file_hash(evidence))
        ledger=dict(schema=1,scope={'pin':'exact'},accepted_by='root',resource_owner='runtime',
            hardware=dict(gpu='actual GPU',driver='actual driver',opengl='actual GL',os='actual OS',java='actual Java',
                          framebuffer=[2976,1672],window=[1488,836]),hardware_evidence=[reference],
            quiet_protocol=dict(no_builds_tests_encoders=True,no_source_readbacks=True,context_only_between_windows=True,sole_client_gpu_owner=True))
        for name,checks in (('driver',bench.DRIVER_CHECKS),('quality',bench.QUALITY_CHECKS)):
            ledger[name]=dict(accepted=True,checks={c:True for c in checks},evidence=[reference])
        path=root/'ledger.json';path.write_text(json.dumps(ledger));return path,ledger,evidence

    def test_missing_stale_quality_driver_and_pilot_evidence_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path,ledger,evidence=self.ledger_fixture(Path(folder))
            self.assertEqual(bench.prerequisites(path,{'pin':'exact'},'pilot'),ledger)
            with self.assertRaisesRegex(ValueError,'Pilot'):bench.prerequisites(path,{'pin':'exact'},'repeated')
            for mutate in (lambda l:l['quality'].update(accepted=False),lambda l:l['driver']['checks'].update(sampler_state=False),
                    lambda l:l.update(scope={'pin':'stale'}),lambda l:l['hardware'].pop('driver'),
                    lambda l:l['quiet_protocol'].update(no_builds_tests_encoders=False)):
                bad=copy.deepcopy(ledger);mutate(bad);path.write_text(json.dumps(bad))
                with self.assertRaises(ValueError):bench.prerequisites(path,{'pin':'exact'},'pilot')
            path.write_text(json.dumps(ledger));evidence.write_text('changed')
            with self.assertRaisesRegex(ValueError,'Evidence bytes'):bench.prerequisites(path,{'pin':'exact'},'pilot')

    def test_receipt_command_resource_freeze_hardware_and_quiet_guards(self):
        q=dict(development_freeze={'directories':{'full':'pin'}})
        row=dict(command=['exact'],expected=dict(request={'run_id':'fresh'},input_files_sha256={'full':'pin'}))
        state=dict(qualification=q,prerequisites=dict(resource_owner='runtime',hardware={'actual':'GPU'}))
        valid=dict(command=row['command'],request=row['expected']['request'],resource_owner='runtime',
            prelaunch_development=q['development_freeze'],postrun_development=q['development_freeze'],
            prelaunch_input_files={'full':'pin'},prelaunch_world_sha256=bench.prep.WORLD_SHA256,hardware={'actual':'GPU'},
            quiet_window=True,owned_processes_stopped=True,launcher_pid=1,java_pid=2,
            focus_action_before_capture=True,watcher_exited_before_capture=True,quality_regression_observed=False)
        mutations=[('command',['changed']),('resource_owner','other'),('prelaunch_development',{'partial':'pin'}),
            ('postrun_development',{'partial':'pin'}),('prelaunch_input_files',{}),('hardware',{'other':'GPU'}),
            ('quiet_window',False),('owned_processes_stopped',False),('java_pid',None),
            ('focus_action_before_capture',False),('quality_regression_observed',True)]
        for key,value in mutations:
            with self.subTest(key=key):
                with self.assertRaises(ValueError):bench.compare_receipt(valid|{key:value},row,state)

    def test_positive_receipt_and_hardware_screenshot_mutations(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); report,expected,inputs=self.fixture()
            hardware=dict(gpu='Apple M3 Max',driver='Metal - 91.7',opengl='4.1 Metal - 91.7',
                os='actual operator OS',java='actual launched Java',framebuffer=[2976,1672],window=[1488,836])
            blobs=dict(report=json.dumps(report).encode(),console=b'OpenGL Renderer: Apple M3 Max\nOpenGL Version: 4.1 Metal - 91.7\nShaderLOD CPU timers: currentThreadCpuSupported=true, layers=false\n',
                hardware_evidence=json.dumps(hardware).encode(),screenshot=b'\x89PNG\r\n\x1a\n'+b'\x00\x00\x00\x0dIHDR'+struct.pack('>II',2976,1672),
                prelaunch_context=b'quiet context',postrun_context=b'quiet context')
            refs={}
            for key,data in blobs.items():
                path=root/key;path.write_bytes(data);refs[key]=dict(path=str(path),sha256=bench.prep.file_hash(path))
            frozen={'full':'inventory'};global_config={'global':'held'};effects={'effects':'held'}
            row=dict(command=['exact'],arm='privateON-ADAPTIVE',expected=expected|dict(input_files_sha256={'file':'pin'}))
            state=dict(qualification=dict(development_freeze=frozen,scope=dict(global_config=global_config,adaptive_effects=effects)),
                prerequisites=dict(resource_owner='runtime',hardware=hardware))
            receipt=refs|dict(command=['exact'],request=expected['request'],resource_owner='runtime',
                prelaunch_development=frozen,postrun_development=frozen,prelaunch_input_files={'file':'pin'},
                prelaunch_world_sha256=bench.prep.WORLD_SHA256,hardware=hardware,quiet_window=True,owned_processes_stopped=True,
                launcher_pid=1,java_pid=2,focus_action_before_capture=True,watcher_exited_before_capture=True,
                quality_regression_observed=False,started_ns=0,instrumentation_arguments={key:'false' for key in bench.OFF}|dict(gpuTiming='true',cpuTiming='true',gpuSampleInterval='5'),
                postrun_global_config=global_config,postrun_effects_config=effects)
            self.assertEqual(bench.compare_receipt(receipt,row,state),report)
            for key,value in (('instrumentation_arguments',{}),('postrun_effects_config',{}),('postrun_global_config',{}),('started_ns',10**30)):
                with self.assertRaises(ValueError):bench.compare_receipt(receipt|{key:value},row,state)
            console=root/'console';console.write_text(blobs['console'].decode().replace('Apple M3 Max','other GPU'))
            receipt['console']['sha256']=bench.prep.file_hash(console)
            with self.assertRaisesRegex(ValueError,'console GL'):bench.compare_receipt(receipt,row,state)

    def test_fresh_profile_extra_input_and_stale_report_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'saves'/bench.prep.WORLD).mkdir(parents=True);(root/'mods').mkdir()
            (root/'input').write_text('held')
            expected=dict(input_files_sha256={'input':bench.prep.file_hash(root/'input')})
            with mock.patch.object(bench.prep,'world_fingerprint',return_value=bench.prep.WORLD_SHA256),mock.patch.object(bench.prep.matrix,'profile_inputs',return_value={'qualified':True}):
                self.assertEqual(bench.fresh_inputs(root,expected),{'qualified':True})
                (root/'extra').write_text('extra')
                with self.assertRaisesRegex(ValueError,'inventory'):bench.fresh_inputs(root,expected)
                (root/'extra').unlink();(root/'workload-measurement.json').write_text('{}')
                with self.assertRaisesRegex(ValueError,'Stale'):bench.fresh_inputs(root,expected)

    def test_known_equivalence_only_no_main_mod_identity_bypass(self):
        equivalence=bench.common.load_equivalences(ROOT/'dev/runtime-artifact-equivalences.json')
        entry=equivalence['entry'];mods=entry['required_loaded_mods']|{'fabric_api_base':entry['mod_version'],'shaderlod':'0.1.0'}
        artifacts={n:dict(sha256=h) for n,h in entry['required_loaded_mod_artifacts_sha256'].items()}
        artifacts.update(fabric_api_base=dict(sha256=entry['raw_sha256'][0]),shaderlod=dict(kind='development-directory',path='/frozen/classes'))
        report=dict(loaded_mods=mods,loaded_mod_artifacts=artifacts)
        identity,records=bench.matched_identity(report,equivalence)
        self.assertIn('shaderlod',identity['artifacts']);self.assertEqual(len(records),1)
        for key in ('fabric_api_base','iris'):
            bad=copy.deepcopy(report);bad['loaded_mod_artifacts'][key]['sha256']='0'*64
            with self.assertRaises(ValueError):bench.matched_identity(bad,equivalence)


if __name__=='__main__':unittest.main()
