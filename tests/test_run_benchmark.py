import unittest
from run_benchmark import (verify_requested_dh, scenario_timing_complete, scenario_evaluation,
                           comparison_evaluation, FAST_CASES, DIAGNOSTIC_CASES, evidence_snapshot, instrument_controls_passed, analyze_request)
import json
import tempfile
from pathlib import Path

class DhRequestContractTest(unittest.TestCase):
    def manifest(self, state):
        return {"windows": [{"configuration": {"dhState": state}}]}
    def test_selected_modes_match_explicit_cli_request(self):
        for installed, mode, state in [(False, "DISABLED", "ABSENT"), (True, "DISABLED", "INSTALLED_DISABLED"), (True, "DEFAULT", "ENABLED")]:
            self.assertEqual(state, verify_requested_dh(self.manifest(state), installed, mode))
    def test_restored_disabled_state_cannot_pass_enabled_request(self):
        with self.assertRaises(ValueError):
            verify_requested_dh(self.manifest("INSTALLED_DISABLED"), True, "DEFAULT")
    def test_partial_or_missing_identity_is_not_assumed_to_match(self):
        for manifest in [{"windows": []}, {"windows": [{"configuration": None}]}, self.manifest("UNKNOWN")]:
            with self.assertRaises(ValueError): verify_requested_dh(manifest, True, "DEFAULT")

class ScenarioTimingContractTest(unittest.TestCase):
    def test_completed_orchestrator_does_not_hide_invalid_clocks(self):
        window={"scenario":{"caseId":"day-terrain"},"hostObservations":{"case_verdict":"COMPLETE"},"validity":"VALID","statistics":{"count":100}}
        self.assertTrue(scenario_timing_complete({"windows":[window]}, ["day-terrain"]))
        window["validity"]="INVALID"
        self.assertFalse(scenario_timing_complete({"windows":[window]}, ["day-terrain"]))
    def test_empty_untested_and_partial_cases_do_not_pass(self):
        for windows in [[],[{}],[{"hostObservations":{"case_verdict":"UNAVAILABLE"},"validity":"VALID","statistics":{"count":100}}]]:
            self.assertFalse(scenario_timing_complete({"windows":windows}))


class RequiredScenarioCoverageTest(unittest.TestCase):
    def test_one_valid_case_cannot_complete_the_fast_schedule(self):
        window = {"scenario": {"caseId": "day-terrain"},
                  "hostObservations": {"case_verdict": "COMPLETE"},
                  "validity": "VALID", "statistics": {"count": 100}}
        self.assertFalse(scenario_timing_complete({"windows": [window]}))

    def manifest(self, ids):
        return {"windows": [{"scenario": {"caseId": case}, "hostObservations": {"case_verdict": "COMPLETE"},
                             "validity": "VALID", "statistics": {"count": 100}} for case in ids]}

    def test_fast_and_diagnostic_require_their_declared_cases(self):
        self.assertTrue(scenario_timing_complete(self.manifest(FAST_CASES)))
        self.assertTrue(scenario_timing_complete(self.manifest(DIAGNOSTIC_CASES), DIAGNOSTIC_CASES))
        partial = scenario_evaluation(self.manifest(FAST_CASES[:4]))
        self.assertEqual("INCOMPLETE", partial["completion"])
        self.assertEqual(6, len(partial["missing_cases"]))
        self.assertEqual("NOT_EVALUATED", partial["improvement"])

    def test_duplicates_and_unknown_cases_cannot_replace_required_coverage(self):
        for ids in [(*FAST_CASES, FAST_CASES[0]), (*FAST_CASES, "unknown"), (*FAST_CASES[:-1], FAST_CASES[0])]:
            result = scenario_evaluation(self.manifest(ids))
            self.assertEqual("INCOMPLETE", result["completion"])
            self.assertEqual("INVALID", result["validity"])

    def test_capture_completion_is_separate_from_clock_validity(self):
        manifest = self.manifest(FAST_CASES)
        manifest["windows"][0]["validity"] = "INVALID"
        result = scenario_evaluation(manifest)
        self.assertEqual("COMPLETE", result["completion"])
        self.assertEqual("INVALID", result["validity"])
        self.assertFalse(scenario_timing_complete(manifest))


class ComparisonEvaluationTest(unittest.TestCase):
    def decision(self, verdict, validity="VALID", kind="OPTIMIZATION"):
        return {"comparisonId": "target", "validity": validity, "verdict": verdict, "controlKind": kind}

    def test_valid_negative_is_distinct_from_drift_invalid_and_missing(self):
        negative = comparison_evaluation([self.decision("NEGATIVE")], ["target"])
        self.assertEqual("VALID_NEGATIVE", negative["improvement"])
        drift = comparison_evaluation([self.decision("INCONCLUSIVE")], ["target"])
        self.assertEqual("VALID", drift["validity"])
        self.assertEqual("INCONCLUSIVE", drift["improvement"])
        invalid = comparison_evaluation([self.decision("INVALID", "INVALID")], ["target"])
        self.assertEqual("UNRESOLVED", invalid["improvement"])
        missing = comparison_evaluation([self.decision("NEGATIVE")], ["target", "untested"])
        self.assertEqual("INCOMPLETE", missing["completion"])
        self.assertEqual("UNRESOLVED", missing["improvement"])

    def test_known_effect_positive_does_not_become_optimization_benefit(self):
        result = comparison_evaluation([self.decision("POSITIVE", kind="KNOWN_EFFECT")], ["target"])
        self.assertEqual("NOT_EVALUATED", result["improvement"])

    def test_mixed_resolved_and_unresolved_targets_do_not_become_a_gain(self):
        unresolved = self.decision("UNAVAILABLE", "UNTESTED") | {"comparisonId": "other"}
        result = comparison_evaluation([self.decision("POSITIVE"), unresolved], ["target", "other"])
        self.assertEqual("UNRESOLVED", result["improvement"])

    def test_evidence_binds_actual_files_and_rejects_invalid_export_even_with_green_guard(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            request = {"brackets": [{arm: str(root) for arm in ("before", "candidate", "return")}]}
            request_path = root / "request.json"
            request_path.write_text(json.dumps(request))
            (root / "before.json").write_text("{}")
            (root / "guard.json").write_text('{"valid": true}')
            result_path = root / "result.json"
            result_path.write_text('{"manifest":{"windows":[{"validity":"VALID"}]}}')
            first = evidence_snapshot(request_path, request)
            (root / "before.json").write_text('{"source": "changed"}')
            self.assertNotEqual(first, evidence_snapshot(request_path, request))
            result_path.write_text('{"manifest":{"windows":[{"validity":"INVALID"}]}}')
            with self.assertRaisesRegex(ValueError, "Invalid or ambiguous"):
                evidence_snapshot(request_path, request)

    def test_mixed_positive_and_negative_targets_are_reported_per_target(self):
        other = self.decision("NEGATIVE") | {"comparisonId": "other"}
        result = comparison_evaluation([self.decision("POSITIVE"), other], ["target", "other"])
        self.assertEqual("MIXED", result["improvement"])

    def test_missing_calibration_or_null_drift_cannot_pass_instrument_controls(self):
        effect = self.decision("POSITIVE", kind="KNOWN_EFFECT")
        null = self.decision("INCONCLUSIVE", kind="KNOWN_NULL")
        null["reasons"] = ["known-null calibration only; cannot be an optimization win"]
        self.assertTrue(instrument_controls_passed([null, effect]))
        for reason in ["return drift exceeds predeclared case-specific allowance",
                       "insufficient chronological blocks: baseline",
                       "known-null control failure: apparent win suspends promotion and requires diagnosis"]:
            self.assertFalse(instrument_controls_passed([null | {"reasons": [reason]}, effect]))

    def test_missing_request_is_reported_as_invalid_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = analyze_request(root / "missing.json", root / "output")
            self.assertEqual("FAILED", result["execution"])
            self.assertEqual("INVALID", result["verdict"])
            self.assertFalse((root / "output").exists())


class VersionedCallerTest(unittest.TestCase):
    def test_untimed_mip_reload_owns_delayed_configuration_and_restores(self):
        import run_benchmark as runner
        import experiment_lifecycle as life
        import time
        from unittest.mock import Mock, patch
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);profile=root/'slot/client';profile.mkdir(parents=True);config=profile/'settings';config.write_text('original')
            op=life.Operation.create(root/'report',profile,{},[config]);op.data['state']='running';op.save()
            (op.output/'receipts').mkdir()
            atomic=life.atomic_json
            def publish(path,value):
                atomic(path,value)
                if Path(path)==op.output/'control.json':
                    config.write_text('reload-owned')
                    atomic(op.output/'receipts'/(value['request_id']+'.json'),
                           {'request_id':value['request_id'],'success':True,'status':'COMPLETE'})
            control=runner.Control(op,Mock(),time.monotonic()+30)
            control.ready=Mock(side_effect=lambda:config.write_text('late-owned'))
            with patch.object(life,'atomic_json',side_effect=publish):
                control.send('bsl_mip_observe',enabled=True,reload=True,reset=True)
            control.ready.assert_called_once()
            self.assertFalse(op.data['mutation_pending']);op.assert_expected()
            self.assertEqual('late-owned',config.read_text())
            op.restore();self.assertEqual('original',config.read_text())

    def test_readiness_uses_completed_render_progress_without_a_capture(self):
        import run_benchmark as runner
        from unittest.mock import Mock, patch
        control=runner.Control(Mock(),Mock(),100)
        clock=[0]
        control.wait=lambda seconds,**kwargs:clock.__setitem__(0,clock[0]+seconds)
        calls=[0]
        def status(action):
            self.assertEqual('status',action)
            calls[0]+=1
            return {'state':{'pipeline_identity':1,'pack_identity':2,'width':2560,'height':1600,
                             'world_loaded':True,'meshes_ready':True,'fallback':False,
                             'world_frames':0,'render_frames':calls[0]}}
        control.send=status
        with patch.object(runner.time,'monotonic',side_effect=lambda:clock[0]):
            state=control.ready()
        self.assertEqual(0,state['world_frames'])
        self.assertGreater(state['render_frames'],1)

    def test_static_ready_status_cannot_pass_without_completed_render(self):
        import run_benchmark as runner
        from unittest.mock import Mock, patch
        control=runner.Control(Mock(),Mock(),3)
        clock=[0]
        control.wait=lambda seconds,**kwargs:clock.__setitem__(0,clock[0]+seconds)
        control.send=lambda action:{'state':{'pipeline_identity':1,'pack_identity':2,'width':2560,'height':1600,
                                            'world_loaded':True,'meshes_ready':True,'fallback':False,
                                            'world_frames':100,'render_frames':12}}
        with patch.object(runner.time,'monotonic',side_effect=lambda:clock[0]):
            with self.assertRaisesRegex(TimeoutError,'readiness'):control.ready()

    def test_default_entry_preflights_without_running(self):
        import run_benchmark as runner
        from unittest.mock import patch
        with patch.object(runner,'preflight',return_value={'errors':[],'protocol':'fixture'}), patch.object(runner,'run_operation') as run:
            self.assertEqual(0,runner.main(['--pack','/tiny/pack.zip','--output','/tiny/report']))
            run.assert_not_called()

    def test_independent_preflight_defects_are_batched_without_output(self):
        import run_benchmark as runner
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve()
            args=runner.arguments(['--pack',str(root/'missing.zip'),'--profile',str(root/'missing-profile'),'--output',str(root/'report'),'--seconds','-1'])
            with patch.object(runner.dev,'validate_built',side_effect=ValueError('stale build')):
                result=runner.preflight(args)
            checks={e['check'] for e in result['errors']}
            self.assertTrue({'bounded_inputs','existing_pool_slot','source_hooks_export_policy','existing_build_binding','generated_plan','destination_capacity'} <= checks)
            self.assertFalse(args.output.exists())

    def test_corrupt_archive_is_batched_with_independent_defects(self):
        import run_benchmark as runner
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve()
            args=runner.arguments(['--pack',str(root/'bad.zip'),'--profile',str(root/'missing-profile'),'--output',str(root/'report'),'--seconds','-1'])
            with patch.object(runner.dev,'plan',side_effect=runner.dev.zipfile.BadZipFile('corrupt input')), patch.object(runner.dev,'validate_built',side_effect=ValueError('stale build')):
                result=runner.preflight(args)
            self.assertTrue({'generated_plan','bounded_inputs','existing_build_binding','existing_pool_slot'} <= {e['check'] for e in result['errors']})
            self.assertFalse(args.output.exists())

    def test_setup_failure_runs_restore_and_retains_reviewable_manifest(self):
        import run_benchmark as runner
        import experiment_lifecycle as life
        import run_storage as storage
        import os
        from unittest.mock import patch, Mock
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve();profile=root/'slot/client';(profile/'config').mkdir(parents=True)
            file=profile/'config/iris.properties';file.write_text('original')
            pack=root/'pack.zip';pack.write_bytes(b'fixture input');out=root/'report'
            args=runner.arguments(['--pack',str(pack),'--profile',str(profile),'--output',str(out),'--suite','smoke'])
            plan={'profile':str(profile),'pack_name':pack.name}
            with patch.object(runner.storage,'admit'), patch.object(runner.storage,'storage_lock',runner.dev.runtime_lock), patch.object(runner.dev,'validate_built',side_effect=ValueError('build drift before launch')), patch.object(runner.dev,'clients',return_value={}), patch.object(runner.dev,'LOCK',root/'lane.lock'), patch.object(storage.shutil,'disk_usage',return_value=Mock(free=40*storage.GIB)):
                self.assertEqual(1,runner.run_operation(args,{'plan':plan,'errors':[]}))
            op=life.Operation(out)
            self.assertEqual('failed-restored',op.data['state']);self.assertTrue(op.data['configuration_restored'])
            self.assertEqual('original',file.read_text())
            self.assertEqual({},op.data['captures'])

    def test_recovery_entry_never_replays_completed_commands(self):
        import run_benchmark as runner
        import experiment_lifecycle as life
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve();profile=root/'slot/client';profile.mkdir(parents=True)
            file=profile/'options.txt';file.write_text('before')
            op=life.Operation.create(root/'report',profile,{},[file]);op.begin_mutation('prepare');file.write_text('owned');op.acknowledge_mutation('prepare')
            op.data['captures']['already']={'complete':True};op.save()
            with patch.object(runner.dev,'clients',return_value={}), patch.object(runner.dev,'process_table',return_value={}), patch.object(runner.dev,'LOCK',root/'lane.lock'), patch.object(runner.subprocess,'Popen',side_effect=AssertionError('recovery replay')):
                self.assertEqual(0,runner.main(['recover','--output',str(op.output)]))
                self.assertEqual(0,runner.main(['recover','--output',str(op.output)]))
            recovered=life.Operation(op.output)
            self.assertEqual('before',file.read_text());self.assertTrue(recovered.data['captures']['already']['complete']);self.assertEqual([],recovered.data['commands'])

    def test_legacy_restoration_bug_reproduces_but_new_recovery_preserves_external_edit(self):
        # Execute the exact old restoration block on tiny authentic file records.
        # This freezes the logic failure, not a missing-new-API test.
        import run_benchmark as runner
        import experiment_lifecycle as life
        import hashlib
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve();profile=root/'slot/client';(profile/'config').mkdir(parents=True)
            file=profile/'config/iris.properties';file.write_text('original')
            op=life.Operation.create(root/'report',profile,{},[file]);op.begin_mutation('configure');file.write_text('owned');op.acknowledge_mutation('configure');file.write_text('external owner')
            with self.assertRaisesRegex(ValueError,'Uncertain'):op.restore()
            self.assertEqual('external owner',file.read_text())


# This stand-in runs in its own real process and consumes the real file protocol.
# It uses the actual dev.prepare and supervised prepare wrapper; rendering is simulated.
FIXTURE_SUPERVISOR = r'''
import json,os,sys,time
from pathlib import Path
import run_benchmark as runner
import experiment_lifecycle as life
import dev_client as dev
out=Path(sys.argv[1]);dev.LOCK=Path(sys.argv[2])
def supervise(session):
 with dev.runtime_lock():
  op=life.Operation(out);plan=op.data['plan']
  dev.prepare(plan,session/'backups')
  proof=dev.identity(dev.process_table()[os.getpid()])
  owner={'status':'ready','plan':plan,'clients':{str(os.getpid()):proof},'supervisor':proof}
  dev.write_json(session/'owner.json',owner)
  state=dict(game_directory=plan['profile'],client_pid=os.getpid(),pack=plan['pack_name'],pipeline_identity=1,pack_identity=2,
    world_loaded=True,meshes_ready=True,fallback=False,main_mod=plan['mode']=='on',focused=True,fullscreen=True,screen='none',
    width=1920,height=1200,loaded_stack=plan['runtime_versions'],effective_options={'NATIVE_QUALITY':'3'},
    installed={'family':plan.get('fixture_family','fixture-family'),'master':False,'flags':{},'early_alpha_draw_counters':{'qualified_draws':0}})
  if plan.get('combo'):state['installed']['g06_selected_source_skip']=False
  if plan.get('g04_fixture'):state['installed']['g04_selected_source_fastpath']=False
  receipts=out/'receipts';receipts.mkdir()
  dev.write_json(receipts/'initial_prepare.json',{'request_id':'initial_prepare','success':True,'status':'COMPLETE'})
  previous='initial_prepare';label=None;c01={'mode':'off'};g10_draws=0
  while not (out/'fixture-stop').exists():
   control=json.loads((out/'control.json').read_text());identity=control['request_id']
   if identity==previous:time.sleep(.002);continue
   previous=identity;action=control['action'];state['selected_resource_packs']=[];result={'request_id':identity,'success':True,'status':'COMPLETE','state':state}
   if plan.get('alpha') and state['installed']['master'] and not plan.get('alpha_refuse') and not plan.get('alpha_setup'):
    state['installed']['early_alpha_draw_counters']['qualified_draws']+=2
   if plan.get('alpha_setup'):
    state['installed']['early_alpha_source_setup']={'rewritten':state['installed']['master'],'rewritten_programs':1 if state['installed']['master'] else 0}
   if action=='early_alpha_status' and 'observe' in control:
    state['installed']['post_sample_draw_witness']={'observing':control['observe'] or plan.get('fail_observer') is True}
   if action=='early_alpha_status':
    result['makeup_post_sample_draw_witness']={'observing':False,'linked_programs':1 if state['installed']['master'] and plan.get('alpha_setup') else 0,'completed_batches':3 if state['installed']['master'] and plan.get('alpha_setup') else 0}
    result.update(makeup_early_alpha_status='owned draw fixture',makeup_early_alpha_costs=dict(state['installed']['early_alpha_draw_counters']),makeup_early_alpha_source_diagnostic={'source':state['installed']['master']})
   if action=='early_alpha_status' and plan.get('alpha_setup'):
    result['makeup_early_alpha_source_diagnostic']=dict.fromkeys(('exact_api','declaration','profile','source','compile_enabled','pack_view','input_present','input_current'),state['installed']['master'])
   if action=='configure':(Path(plan['profile'])/'config/core.toml').write_text('owned configure')
   if action=='configure':
    if 'master' in control:state['installed']['master']=control['master']
    if 'features' in control:state['installed']['flags']={key:'true' for key in control['features']}
   if action=='settings_apply' and plan.get('g04_fixture'):
    state['installed']['g04_selected_source_fastpath']=control['enabled']
   if action=='settings_apply' and plan.get('combo'):
    state['installed']['g06_selected_source_skip']=control['enabled']
    for key in ('SHADERLOD_FIXTURE_EA','SHADERLOD_FIXTURE_G04'):
     if control['enabled']:state['effective_options'][key]='1'
     else:state['effective_options'].pop(key,None)
    if control['enabled']:state['effective_options']['SHADERLOD_FIXTURE_AO']='1'
    else:state['effective_options'].pop('SHADERLOD_FIXTURE_AO',None)
   if action=='frame_copy_observe' and plan.get('combo'):
    state['installed']['frame_copy']={'apiPinned':True,'finalPassesCompleted':2,'finalPassesEntered':2,'executedSwapCopies':6 if state['installed']['master'] else 12}
   if action=='settings_apply':
    state['installed']['master']=control['enabled']
    state['pack']=plan['generated'] if control['enabled'] else plan['pack_name']
    if control['enabled']:
     state['effective_options']['SHADERLOD_FIXTURE']='0' if plan.get('refuse_compile') else '1'
    else:state['effective_options'].pop('SHADERLOD_FIXTURE',None)
    if control['enabled']:
     (Path(plan['profile'])/'shaderpacks'/plan['generated']).write_bytes(b'fixture generated')
   if action=='diagnostic_pack':
    state['pack']=control['pack'];state['effective_options'].pop('SHADERLOD_G10_BLOOM_PAIRING',None)
    if state['pack'] in [plan.get('generated'),plan.get('g10_work')]:state['effective_options']['SHADERLOD_G10_BLOOM_PAIRING']='4' if state['pack']==plan.get('g10_work') else plan.get('g10_mode','3')
    state['installed']['flags']={'family_default':'true'}  # Actual reload can repopulate defaults.
    (Path(plan['profile'])/'config/iris.properties').write_text('owned diagnostic reload')
   if action=='g10_bloom_status' and 'observe' in control:
    state['installed']['g10_bloom_observing']=control['observe'] or plan.get('fail_observer')=='g10' and state['installed']['master']
   if action=='g10_bloom_status':
    g10_draws+=2;active=state['pack']==plan.get('generated') and plan.get('g10_mode','3')=='3' and not plan.get('g10_control')
    result['g10_bloom']={'program_source':'composite2' if plan.get('combo') and state['installed']['master'] else 'composite1','observing':state['installed'].get('g10_bloom_observing',False),'qualified':not(active and plan.get('fail_g10')),'draws':g10_draws,'program':2 if active else 1,'fragment_source':('if(dither == 0.0){'+'blurSample += g10Sample;'*4+'}') if active else 'native bloom'}
   if action=='g10_bloom_work':
    g10_draws+=1
    result['g10_work']={'draw_receipt':{'qualified':True,'draw_counter':g10_draws,'work_texture':22},'image':{'state_restored':True,'diagnostic':{'kind':'G10-bloom'},'banks':[{'texture':22,'width':2,'height':2,'eligible_pixels':4,'histogram':[0,3,0,0,1]}]}}
   if action=='capture_saved_authored':
    import base64
    image=Path(plan['profile'])/'screenshots'/('fixture-'+identity+'.png');image.parent.mkdir(exist_ok=True)
    image.write_bytes(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='))
    result['capture_requested']=str(image)
   if action=='open_saved_authored':
    world=next((Path(plan['profile'])/'saves').glob('*/level.dat'));world.write_bytes(b'owned game save')
   if action=='prepare_feature_scene':
    world=next((Path(plan['profile'])/'saves').glob('*/level.dat'))
    (world.parent/'feature-scene.dat').write_bytes(b'owned fixture edit')
    state['standard_feature_scene_ready']=True
    result['feature_scene']={'server_completed':True,'block':'minecraft:honey_block','fixture_only':True}
    if plan.get('fail_scene'):result.update(success=False,error='fixture placement uncertain')
   if action=='c01_bounds':
    if 'mode' in control:
     c01={'mode':control['mode'],'frames':0,'proxy_queries':0,'cpu_result_reads':0,'remaining_frames':1000,'refusal':'native source guard' if plan.get('fail_c01') else ''}
    if c01['mode']=='deferred_cpu' and not plan.get('fail_c01'):
     c01['frames']+=2;c01['proxy_queries']+=4;c01['cpu_result_reads']+=4
    result['c01_bounds']=dict(c01)
   if action=='window_start':label=control['label'];recording=control.get('recording',True)
   if action=='window_start' and plan.get('fail_native'):
    result.update(success=False,error='native render failed in process fixture')
   if action=='window_stop':
    directory=out/'windows'/label;directory.mkdir(parents=True)
    ns=8000000 if label=='candidate' else 10000000
    samples=[{'totalFrameNs':ns}]*3
    if plan.get('bad_native_samples')=='empty':samples=[]
    if plan.get('bad_native_samples')=='mixed':samples=[{'totalFrameNs':ns},{'totalFrameNs':0},{'totalFrameNs':ns}]
    guard={'path':str(directory),'label':label,'valid':True,'completed_render_frames':3,'wall_ns':ns*3,'statistics':{'count':3,'throughputFps':100}}
    dev.write_json(directory/'guard.json',guard)
    dev.write_json(directory/'result.json',{'manifest':{'windows':[{'id':label,'validity':'VALID' if recording else 'UNTESTED','statistics':{'count':3},'samples':samples}]}})
    dev.write_json(directory/'before.json',state);dev.write_json(directory/'after.json',state);result['window']=guard
   dev.write_json(receipts/(identity+'.json'),result)
  # Model normal game options persistence at shutdown, inside declared owned exit.
  (Path(plan['profile'])/'options.txt').write_text('owned shutdown persistence')
  owner['status']='exited';dev.write_json(session/'owner.json',owner)
runner.dev.supervise=supervise
runner.supervise_operation(out)
'''


class FixtureProcessSmokeTest(unittest.TestCase):
    def test_combination_two_controls_work_witness_and_restoration(self):
        self.smoke(standard=True,combo=True)
    def test_combination_g04_fixture_three_controls_and_restoration(self):
        self.smoke(standard=True,combo=True,g04_fixture=True)
    def test_combination_g04_without_copy_and_restoration(self):
        self.smoke(standard=True,combo=True,g04_fixture=True,no_copy=True)
    def test_combination_early_alpha_source_setup_and_restoration(self):
        self.smoke(standard=True,combo=True,alpha=True,alpha_setup=True)
    def test_combination_all_four_source_setup_fixture_and_restoration(self):
        self.smoke(standard=True,combo=True,alpha=True,alpha_setup=True,g04_fixture=True)
    def test_combination_pair_uses_same_g04_scene_without_g04_request(self):
        self.smoke(standard=True,combo=True,g04_fixture=True,scene_baseline=True)
    def test_combination_c01_query_progress_and_restoration(self):
        self.smoke(standard=True,combo=True,c01=True)
    def test_combination_c01_refusal_skips_candidate_timing_and_restores(self):
        self.smoke(standard=True,combo=True,c01=True,fail_c01=True)
    def test_combination_g10_link_consumption_progress_and_restoration(self):
        self.smoke(standard=True,combo=True,with_g10=True)
    def test_combined_g10_missing_link_refuses_timing_and_restores(self):
        self.smoke(standard=True,combo=True,with_g10=True,fail_g10=True)
    def test_combined_g10_control_keeps_kernel_off_and_moves_observer(self):
        self.smoke(standard=True,combo=True,g10_control=True)
    def test_armed_g10_observer_refuses_candidate_timing_and_restores(self):
        self.smoke(standard=True,combo=True,with_g10=True,fail_observer='g10')
    def test_combination_g04_unmodified_saved_scene_and_restoration(self):
        self.smoke(standard=True,combo=True,g04_fixture=True,scene_saved=True)
    def test_armed_g03_observer_refuses_candidate_timing_and_restores(self):
        self.smoke(standard=True,combo=True,alpha=True,alpha_setup=True,fail_observer=True)
    def test_combination_partial_compile_refusal_skips_timing_and_restores(self):
        self.smoke(standard=True,combo=True,refuse_compile=True)
    def test_feature_scene_is_prepared_before_native_and_whole_save_restored(self):
        self.smoke(standard=True,g04_fixture=True)
    def test_failed_feature_scene_restores_whole_save_before_any_timing(self):
        self.smoke(standard=True,g04_fixture=True,fail_scene=True)
    def test_refused_compile_contract_skips_candidate_timing_and_restores(self):
        self.smoke(standard=True,refuse_compile=True)
    def test_standard_command_runs_three_arms_evaluates_and_resets_whole_slot(self):
        self.smoke(standard=True)
    def test_standard_native_failure_restores_without_candidate_or_speedup(self):
        self.smoke(standard=True,fail_native=True)
    def test_green_guard_with_empty_native_samples_stops_before_candidate(self):
        self.smoke(standard=True,bad_native_samples='empty')
    def test_green_guard_with_mixed_invalid_samples_stops_before_candidate(self):
        self.smoke(standard=True,bad_native_samples='mixed')
    def test_actual_entry_file_protocol_restores_releases_and_retires_only_owned_backups(self):
        self.smoke()

    def test_absent_arm_uses_supported_recovery_without_main_mod_commands(self):
        self.smoke(arm='absent')

    def test_keyboard_interrupt_retains_completed_capture_and_restores_without_replay(self):
        self.smoke(interrupt=True)

    def test_dropped_resource_pack_refuses_timing_and_restores_whole_profile(self):
        self.smoke(standard=True,fail_resources=True)

    def test_c01_original_pack_controls_candidate_progress_and_restoration(self):
        self.smoke(standard=True,c01=True)
    def test_c01_source_refusal_skips_candidate_timing_and_restores(self):
        self.smoke(standard=True,c01=True,fail_c01=True)

    def test_g10_private_stage_draws_work_image_and_restore(self):
        self.smoke(standard=True,g10=True)
    def test_g10_unqualified_link_refuses_candidate_timing_and_restores(self):
        self.smoke(standard=True,g10=True,fail_g10=True)

    def test_g10_native_control_is_labeled_known_null_and_restores(self):
        self.smoke(standard=True,g10=True,g10_native=True)

    def test_early_alpha_draw_admission_and_progress_restore(self):
        self.smoke(standard=True,alpha=True)
    def test_early_alpha_source_only_route_records_coverage_limit_and_restores(self):
        self.smoke(standard=True,alpha=True,alpha_setup=True)
    def test_early_alpha_no_qualified_draw_refuses_timing_and_restores(self):
        self.smoke(standard=True,alpha=True,alpha_refuse=True)

    def smoke(self,arm='empty',interrupt=False,standard=False,fail_native=False,bad_native_samples=None,refuse_compile=False,g04_fixture=False,fail_scene=False,fail_resources=False,c01=False,fail_c01=False,g10=False,fail_g10=False,g10_native=False,alpha=False,alpha_refuse=False,alpha_setup=False,combo=False,no_copy=False,scene_baseline=False,scene_saved=False,with_g10=False,fail_observer=False,g10_control=False):
        import run_benchmark as runner
        import experiment_lifecycle as life
        import run_storage as storage
        import os,subprocess,zipfile,sys
        from unittest.mock import patch, Mock
        with tempfile.TemporaryDirectory(prefix='shaderlod-process-smoke-') as directory:
            root=Path(directory).resolve();profile=root/('run-workloads/world-pool/test-slot/client' if standard else 'slot/client');(profile/'config').mkdir(parents=True)
            (profile/'mods').mkdir();(profile/'saves/ShaderLOD-Landscape').mkdir(parents=True)
            (profile/'shaderpacks').mkdir()
            (profile/'saves/ShaderLOD-Landscape/level.dat').write_bytes(b'tiny canonical fixture')
            (profile/'config/iris.properties').write_text('shaderPack=Old.zip\nenableShaders=false\n')
            (profile/'config/core.toml').write_text('original config')
            (profile/'options.txt').write_text('original options')
            if fail_resources:
                (profile/'resourcepacks').mkdir();(profile/'resourcepacks/height.zip').write_bytes(b'fixture resource')
                (profile/'options.txt').write_text('resourcePacks:["file/height.zip"]\n')
            (profile.parent/'control.json').write_text('{"enabled":false}')
            original={str(p):p.read_bytes() for p in [profile/'config/iris.properties',profile/'config/core.toml',profile/'options.txt',profile.parent/'control.json']}
            if standard:
                import shutil
                source=root/'canonical/client';shutil.copytree(profile,source)
                shutil.copy2(profile.parent/'control.json',source.parent/'control.json')
                (profile.parent/'workspace.json').write_text(json.dumps({'schema':1,'status':'ready','slot':'test-slot','source':str(source)}))
            pack=root/'Original.zip'
            with zipfile.ZipFile(pack,'w') as z:z.writestr('shaders/shaders.properties','screen=\n');z.writestr('shaders/test.fsh','void main() {}')
            out=root/'report';fixture=root/'fixture.py';fixture.write_text(FIXTURE_SUPERVISOR)
            args=runner.arguments(['--run','--pack',str(pack),'--profile',str(profile),'--output',str(out),'--suite','smoke'])
            # Authentic plan builder, then test-only preflight substitution: no real build or pool admission.
            control=root/'temporary-control.json';control.write_text('{}')
            from types import SimpleNamespace
            from datetime import datetime,timezone,timedelta
            plan=runner.dev.plan(SimpleNamespace(mode='off' if arm=='absent' else 'on',profile=profile,pack=pack,pack_options=None,dh=False,already_built=True,
                ready_timeout=60,benchmark_tuner_probe=None,benchmark_control_probe=control,
                stop_at=(datetime.now(timezone.utc)+timedelta(seconds=120)).isoformat(),foreground=True))
            plan['benchmark_control_probe']=str(out/'control.json')
            plan['command']=[('-PbenchmarkControlProbe='+str(out/'control.json')) if x.startswith('-PbenchmarkControlProbe=') else x for x in plan['command']]
            plan['runtime_versions']={'minecraft':'1.21.1','neoforge':'21.1.253','iris':'1.8.12+1.21.1-neoforge','sodium':'mc1.21.1-0.6.13-neoforge'}
            identity={'family':'fixture-family','generated':'ShaderLOD-fixture.zip','state':'.fixture-state.json','legacy_state':'.fixture-legacy.json','recipe_path':str(pack)}
            identity['compile_controls']={'fixture-feature':{'option':'SHADERLOD_FIXTURE','enabled':'1'}}
            feature='early_alpha' if alpha else 'translucent_fastpath' if g04_fixture else 'fixture-feature'
            if combo:
                plan['g10_control']=g10_control
                plan['with_g10']=with_g10
                plan['combo']=True;identity['family']='makeup-ultra-fast-shaders';plan['fixture_family']=identity['family']
                identity['compile_controls']={'dead_frame_copy':{'option':'SHADERLOD_FIXTURE','enabled':'1'},'ao_attenuation':{'option':'SHADERLOD_FIXTURE_AO','enabled':'1'}}
            if alpha:
                plan['alpha']=True;identity['family']='makeup-ultra-fast-shaders';plan['fixture_family']=identity['family'];identity['compile_controls']={feature:{'option':'SHADERLOD_FIXTURE','enabled':'1'}}
            if alpha_refuse:plan['alpha_refuse']=True
            if alpha_setup:plan['alpha_setup']=True
            if g04_fixture:
                plan['g04_fixture']=True
                identity['family']='makeup-ultra-fast-shaders';plan['fixture_family']=identity['family']
                identity['compile_controls'][feature]={'option':'SHADERLOD_FIXTURE','enabled':'1'}
            if combo:
                identity['compile_controls']={key:{'option':option,'enabled':'1'} for key,option in [('ao_attenuation','SHADERLOD_FIXTURE_AO')]+([] if no_copy else [('dead_frame_copy','SHADERLOD_FIXTURE')])+([('early_alpha','SHADERLOD_FIXTURE_EA')] if alpha else [])+([('translucent_fastpath','SHADERLOD_FIXTURE_G04')] if g04_fixture and not scene_baseline else [])}
            if g10:plan['g10_work']='ShaderLOD-fixture-work.zip'
            if g10_native:plan['g10_mode']='0'
            if fail_g10:plan['fail_g10']=True
            if fail_c01:plan['fail_c01']=True
            if fail_observer:plan['fail_observer']=fail_observer
            if fail_scene:plan['fail_scene']=True
            if refuse_compile:plan['refuse_compile']=True
            if standard:plan['generated']=identity['generated']
            if fail_native:plan['fail_native']=True
            if bad_native_samples:plan['bad_native_samples']=bad_native_samples
            actual_popen=subprocess.Popen;children=[];requested=[]
            def launch(argv,**kwargs):
                if '_supervise-operation' not in argv:return actual_popen(argv,**kwargs)
                requested.append(argv)
                environment=dict(kwargs.get('env') or os.environ)
                environment['PYTHONPATH']=os.pathsep.join([str(Path(runner.__file__).parent),environment.get('PYTHONPATH','')])
                kwargs['env']=environment
                proc=actual_popen([sys.executable,str(fixture),str(out),str(root/'lane.lock')],**kwargs)
                children.append(proc);return proc
            original_wait=runner.Control.wait
            interrupted=False;measurement_waits=[]
            def short_wait(instance,seconds,*,full=True):
                nonlocal interrupted
                if standard and seconds==20:measurement_waits.append(seconds)
                if interrupt and not interrupted and instance.op.data['captures'] and not instance.recovery:
                    interrupted=True;raise KeyboardInterrupt('fixture interruption after completed capture')
                return original_wait(instance,min(seconds,.01),full=full)
            def ready(instance):return instance.send('status')['state']
            def admission(args):
                if standard and (not c01 or combo) and not g10:args.public_identity=identity
                if g10:args.private_identity={'candidate':'g10-native-control' if g10_native else 'g10-exact-bloom','compile_mode':plan.get('g10_mode','3'),'native_options':{'NATIVE_QUALITY':'3'},'generated':plan['generated'],'work':plan['g10_work'],'original_sha256':life.digest(pack),'producers':{}}
                if c01:args.private_identity={'candidate':'c01-bounds','native_options':{'NATIVE_QUALITY':'3'},'mode':'deferred_cpu','prefix':1,'group':32,'frames':20000,'delay':256}
                return {'plan':plan,'errors':[]}
            actual_stage=runner.iteration.stage_g10
            def stage(op,pack,options,identity):
                if not g10:return actual_stage(op,pack,options,identity)
                op.begin_mutation('fixture-g10-stage')
                for key in ['generated','work']:
                    for suffix in ['', '.txt']:(profile/'shaderpacks'/(identity[key]+suffix)).write_bytes(b'fixture owned private input')
                op.acknowledge_mutation('fixture-g10-stage');return {'lean_sha256':'fixture','work_sha256':'fixture'}
            actual_checkout=storage.checkout
            try:
                with patch.object(runner.iteration,'stage_g10',side_effect=stage), patch.object(runner.storage,'admit'), patch.object(runner.storage,'storage_lock',__import__('contextlib').nullcontext), patch.object(runner,'preflight',side_effect=admission), patch.object(runner.dev,'validate_built'), patch.object(runner.dev,'LOCK',root/'lane.lock'), patch.object(runner.dev,'clients',return_value={}), patch.object(storage.shutil,'disk_usage',return_value=Mock(free=40*storage.GIB)), patch.object(runner.subprocess,'Popen',side_effect=launch), patch.object(runner.Control,'wait',short_wait), patch.object(runner.Control,'ready',ready), patch.object(runner.dev,'stop',side_effect=lambda session:(out/'fixture-stop').write_text('exact fixture owner stop')), patch.object(storage,'checkout',side_effect=lambda source,slot,**kw:actual_checkout(source,slot,root=root,**kw)) as reset:
                    real_run=subprocess.run
                    def command(argv,**kwargs):
                        if any(str(arg).endswith('dev_client_ui.py') for arg in argv):return subprocess.CompletedProcess(argv,0,'fixture focus','')
                        return real_run(argv,**kwargs)
                    with patch.object(runner.subprocess,'run',side_effect=command):
                        argv=['--pack',str(pack),'--profile',str(profile),'--output',str(out)]
                        code=runner.main(['test',*argv,*(['--candidate','g10-native-control' if g10_native else 'g10-exact-bloom'] if g10 else ['--candidate','c01-bounds',*(['--features','dead_frame_copy,ao_attenuation'] if combo else [])] if c01 else ['--features',','.join(([] if no_copy else ['dead_frame_copy'])+['ao_attenuation']+(['early_alpha'] if alpha else [])+(['translucent_fastpath'] if g04_fixture and not scene_baseline else []))] if combo else ['--feature',feature]),*(['--saved-pose','9'] if g04_fixture and not scene_saved else []),*(['--scene','translucent-fixture'] if scene_baseline else ['--scene','saved'] if scene_saved else []),*(['--with-g10'] if with_g10 else ['--g10-control'] if g10_control else [])] if standard else ['--run',*argv,'--suite','smoke','--arm',arm])
                op=life.Operation(out)
                self.assertEqual(1 if interrupt or fail_native or bad_native_samples or refuse_compile or fail_scene or fail_resources or fail_c01 or fail_g10 or alpha_refuse or fail_observer else 0,code,(out/'summary.json').read_text())
                self.assertEqual('failed-restored' if interrupt or fail_native or bad_native_samples or refuse_compile or fail_scene or fail_resources or fail_c01 or fail_g10 or alpha_refuse or fail_observer else 'complete',op.data['state']);self.assertTrue(op.data['release_verified'])
                self.assertEqual(0 if fail_native or fail_scene or fail_resources else 1 if interrupt or bad_native_samples or refuse_compile or fail_c01 or fail_g10 or alpha_refuse or fail_observer else 3,len(op.data['captures']));self.assertTrue(requested)
                self.assertEqual(original,{p:Path(p).read_bytes() for p in original})
                self.assertTrue(all(child.poll() is not None for child in children))
                self.assertFalse((profile/'shaderpacks/Original.zip').exists())
                self.assertTrue(all(item['status']=='removed' for item in op.data['cleanup']))
                if not fail_native and not fail_scene and not fail_resources:self.assertTrue((out/('windows/native/result.json' if standard else 'windows/null_0/result.json')).exists())
                if standard:
                    result=json.loads((out/'summary.json').read_text())['evaluation']
                    if fail_native or bad_native_samples or refuse_compile or fail_scene or fail_resources or fail_c01 or fail_g10 or alpha_refuse or fail_observer:
                        self.assertEqual('INVALID',result['validity']);self.assertNotIn('speedup_ratio',result)
                        if alpha_refuse:
                            self.assertIn('Early-alpha actual qualified draw admission unavailable',json.loads((out/'summary.json').read_text())['error'])
                            self.assertIn('early_alpha_activation',json.loads((out/'summary.json').read_text()))
                            self.assertEqual([20],measurement_waits);self.assertFalse((out/'windows/candidate').exists())
                        if fail_g10:
                            self.assertIn('G10 linked native bloom draw unavailable',json.loads((out/'summary.json').read_text())['error'])
                            self.assertEqual([20],measurement_waits);self.assertFalse((out/'windows/candidate').exists())
                        if fail_c01:
                            self.assertIn('C01 actual query/read admission unavailable',json.loads((out/'summary.json').read_text())['error'])
                            self.assertEqual([20],measurement_waits);self.assertFalse((out/'windows/candidate').exists())
                        if fail_observer:
                            self.assertIn('G10 observer armed during timing' if fail_observer=='g10' else 'G03 witness armed during timing',json.loads((out/'summary.json').read_text())['error']);self.assertEqual([20],measurement_waits);self.assertFalse((out/'windows/candidate').exists())
                        if refuse_compile:
                            self.assertIn('Candidate compile admission refused',json.loads((out/'summary.json').read_text())['error'])
                            self.assertEqual([20],measurement_waits)
                            self.assertFalse((out/'windows/candidate').exists())
                        elif not alpha_refuse and not fail_observer and not (combo and fail_c01 or with_g10 and fail_g10):self.assertFalse(any(row['action']=='settings_apply' and row['arguments']['enabled'] for row in op.data['commands']))
                        if alpha_refuse:self.assertTrue(any(row['action']=='settings_apply' and row['arguments']['enabled'] for row in op.data['commands']))
                    else:
                        self.assertEqual('VALID',result['validity']);self.assertAlmostEqual(20,result['frame_time_reduction_percent'])
                        self.assertEqual([20,20,20],measurement_waits)
                        if g10_native:self.assertEqual('KNOWN_NULL_CONTROL',result['improvement'])
                    self.assertFalse(result['closure']);self.assertEqual(1,reset.call_count)
                    self.assertEqual(b'tiny canonical fixture',(profile/'saves/ShaderLOD-Landscape/level.dat').read_bytes())
                    self.assertTrue(op.data['iteration_profile']['reset_verified'])
                    if g04_fixture:
                        self.assertFalse((profile/'saves/ShaderLOD-Landscape/feature-scene.dat').exists())
                        actions=[row['action'] for row in op.data['commands']]
                        if scene_saved:self.assertNotIn('prepare_feature_scene',actions)
                        elif fail_scene:self.assertNotIn('window_start',actions)
                        else:self.assertLess(actions.index('prepare_feature_scene'),actions.index('window_start'))
                evidence=os.environ.get('SHADERLOD_TEST_EVIDENCE_DIR')
                if evidence:
                    name=('fixture-combination-'+('g10-observer-refusal' if fail_observer=='g10' else 'observer-refusal' if fail_observer else 'g10-control' if g10_control else 'g10-refusal' if with_g10 and fail_g10 else 'g10-addition' if with_g10 else 'saved-scene' if scene_saved else 'c01-refusal' if c01 and fail_c01 else 'c01' if c01 else 'refusal' if refuse_compile else 'scene-baseline' if scene_baseline else 'four' if alpha and g04_fixture else 'alpha' if alpha else 'no-copy' if no_copy else 'g04-triple' if g04_fixture else 'pair')+'.json') if combo else ('fixture-alpha-source-setup.json' if alpha_setup else 'fixture-alpha-refusal.json' if alpha_refuse else 'fixture-alpha-success.json') if alpha else 'fixture-g10-native-control.json' if g10_native else ('fixture-g10-refusal.json' if fail_g10 else 'fixture-g10-success.json') if g10 else ('fixture-c01-refusal.json' if fail_c01 else 'fixture-c01-success.json') if c01 else 'fixture-resource-refusal.json' if fail_resources else ('fixture-scene-failure.json' if fail_scene else 'fixture-scene-success.json') if g04_fixture else 'fixture-compile-refusal.json' if refuse_compile else ('fixture-native-samples-'+bad_native_samples+'.json') if bad_native_samples else ('fixture-standard-native-failure.json' if fail_native else 'fixture-standard-success.json') if standard else ('fixture-interruption.json' if interrupt else 'fixture-smoke-'+arm+'.json')
                    destination=Path(evidence)/name
                    destination.parent.mkdir(parents=True,exist_ok=True)
                    destination.write_text(json.dumps({'fixture_only':True,'rendering':'simulated','entry':'run_benchmark.main --run','requested_supervisor':requested,
                        'code':code,'operation':op.data,'summary':json.loads((out/'summary.json').read_text()),'all_spawned_processes_reaped':all(child.poll() is not None for child in children),'original_configuration_restored':True},indent=2)+'\n')
            finally:
                # Only real fixture processes spawned in this test are ever stopped.
                for child in children:
                    if child.poll() is None:child.terminate()
                    child.wait(timeout=5)

class ReleaseConflictTest(unittest.TestCase):
    def test_config_conflict_still_stops_exact_client_and_preserves_restore_refusal(self):
        import run_benchmark as runner
        from unittest.mock import Mock, patch
        from contextlib import nullcontext
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory);session=out/'session';session.mkdir()
            (session/'owner.json').write_text(json.dumps({'clients':{'123':{'pid':123}},'launcher':{'pid':124}}))
            op=Mock(output=out,data={'mutation_pending':False,'profile':str(out/'profile'),'processes':{}})
            op.begin_mutation.side_effect=ValueError('configuration conflict')
            op.restore.side_effect=ValueError('uncertain configuration retained')
            proc=Mock();proc.poll.return_value=None
            with patch.object(runner.dev,'stop') as stop, patch.object(runner.dev,'retire_source_launcher') as retire, patch.object(runner.dev,'clients',return_value={}), patch.object(runner.dev,'process_table',return_value={}), patch.object(runner.life,'process_absent'), patch.object(runner.dev,'runtime_lock',return_value=nullcontext()):
                with self.assertRaisesRegex(ValueError,'uncertain configuration'):
                    runner.release_runtime(op,proc)
                stop.assert_called_once_with(session);retire.assert_called_once_with(session)
                op.acknowledge_mutation.assert_not_called();op.restore.assert_called_once()
