import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

import benchmark_iteration as iteration
import run_benchmark as runner
from contextlib import nullcontext
from types import SimpleNamespace


class NaturalTerrainControlTest(unittest.TestCase):
    def test_only_native_cpu_or_empty_native_control_uses_fixed_spawn(self):
        args=SimpleNamespace(standard_test=True,candidate='native-control',features=None,feature=None,dh=False,saved_pose=0)
        runner.natural_terrain_inputs(args)
        self.assertEqual(args.world,'ShaderLOD-Smoke')
        for key,value in [('feature','dead_frame_copy'),('dh',True),('saved_pose',1)]:
            bad=SimpleNamespace(**(vars(args)|{key:value}))
            with self.assertRaisesRegex(ValueError,'Natural terrain requires'):runner.natural_terrain_inputs(bad)
        args.candidate='public';args.feature='quad_command_compaction'
        runner.natural_terrain_inputs(args)
        args.feature='dead_frame_copy'
        with self.assertRaisesRegex(ValueError,'Natural terrain requires'):runner.natural_terrain_inputs(args)


class AoScaleWorkTest(unittest.TestCase):
    def test_next_event_needs_nearest_permissions_and_native_has_no_grants(self):
        family='makeup-ultra-fast-shaders'
        def state(n,linear=0):
            return {'installed':{'reflection_reuse_work':{'family':family,'status':'cached','counters':{'permission_grants':n+linear,'nearest_grants':n,'linear_grants':linear,'owned_programs':1}}}}
        before=state(3)
        receipt=iteration.reflection_reuse_work(state(8),family=family,active=True,before=before)
        self.assertEqual(receipt['window_delta']['nearest_grants'],5)
        with self.assertRaisesRegex(ValueError,'NEAREST'):iteration.reflection_reuse_work(state(3,10),family=family,active=True,before=before)
        iteration.reflection_reuse_work(before,family=family,active=False,before=before)
        with self.assertRaisesRegex(ValueError,'NEAREST'):iteration.reflection_reuse_work(state(8),family=family,active=False,before=before)

    def test_depth_reuse_requires_actual_permission_progress(self):
        family='complementary-reimagined'
        def state(n,owned=1):
            return {'installed':{'depth_reuse_work':{'family':family,'counters':{'qualified_draws':n,'owned_programs':owned}}}}
        before=state(3)
        self.assertEqual(iteration.depth_reuse_work(state(8),family=family,active=True,before=before)['window_qualified_draws'],5)
        for after in (state(3),state(8,0)):
            with self.assertRaisesRegex(ValueError,'progress differs'):iteration.depth_reuse_work(after,family=family,active=True,before=before)
        iteration.depth_reuse_work(before,family=family,active=False,before=before)
        with self.assertRaisesRegex(ValueError,'progress differs'):iteration.depth_reuse_work(state(8),family=family,active=False,before=before)

    def test_exact_window_requires_producer_returns_and_consumption(self):
        family='bsl-shaders'
        def state(n,consumer=None):
            return {'installed':{'ao_scale_work':{'family':family,'counters':{'producer_ready':n,'producer_returns':n,'consumer_ready':n if consumer is None else consumer},'status':'cached'}}}
        before=state(10)
        receipt=iteration.ao_scale_work(state(20),family=family,active=True,before=before)
        self.assertEqual(receipt['window_delta']['consumer_ready'],10)
        for after in (state(10),state(20,10)):
            with self.assertRaisesRegex(ValueError,'progress differs'):
                iteration.ao_scale_work(after,family=family,active=True,before=before)
        iteration.ao_scale_work(before,family=family,active=False,before=before)
        with self.assertRaisesRegex(ValueError,'progress differs'):
            iteration.ao_scale_work(state(20),family=family,active=False,before=before)


class OwnershipDiagnosticTest(unittest.TestCase):
    def test_incomplete_graph_always_disarms(self):
        control=Mock()
        control.send.return_value={'g01_graph':{'active':False,'completed_final_passes':1}}
        with self.assertRaises(ValueError):
            iteration.ownership_diagnostic(control,'g01-graph',active=True)
        self.assertEqual(control.send.call_args.args,('g01_graph',))
        self.assertEqual(control.send.call_args.kwargs,{'cancel':True})

    def test_storage_observation_cannot_claim_active_optimization(self):
        receipt={'status':'property-only native hold; no support/H/admission','owner_retired':True,
                 'image_absent':True,'descriptors_unpublished':True,'actual_active_after':{'shaderlodBslPrefixActive':1}}
        control=Mock();control.send.return_value={'prefix_status':json.dumps(receipt)}
        with self.assertRaises(ValueError):
            iteration.ownership_diagnostic(control,'g09-storage',active=True)
        receipt['actual_active_after']['shaderlodBslPrefixActive']=0
        control.send.return_value={'prefix_status':json.dumps(receipt)}
        self.assertEqual(iteration.ownership_diagnostic(control,'g09-storage',active=True)['storage_receipt'],receipt)


class StandardCommandTest(unittest.TestCase):
    def test_b_first_uses_original_snapshot_and_keeps_native_option_guards(self):
        native={'pack':'original.zip','installed':{'family':'family','master':False,'flags':{}},
                'effective_options':{'AA':'2','BLOOM':'true'}}
        baseline=iteration.original_option_snapshot(native,expected_pack='original.zip',family='family')
        candidate={'pack':'candidate.zip','installed':{'family':'family','master':True,'flags':{'copy':'true'}},
                   'effective_options':baseline|{'SHADERLOD_COPY':'1'}}
        fields=dict(feature='copy',enabled=True,expected_pack='candidate.zip',family='family',native_options=baseline,
                    compile_control={'option':'SHADERLOD_COPY','enabled':'1'})
        iteration.activation(candidate,**fields)
        iteration.activation(native,feature='copy',enabled=False,expected_pack='original.zip',family='family',native_options=baseline)
        with self.assertRaisesRegex(ValueError,'selected pack'):
            iteration.original_option_snapshot(candidate,expected_pack='original.zip',family='family')
        candidate['effective_options']['AA']='0'
        with self.assertRaisesRegex(ValueError,'Original native options changed'):
            iteration.activation(candidate,**fields)
        candidate['effective_options']['AA']='2';candidate['effective_options']['UNEXPECTED_NATIVE_OPTION']='1'
        with self.assertRaisesRegex(ValueError,'Unexpected non-adapter'):
            iteration.activation(candidate,**fields)

    def test_balanced_orders_cancel_linear_drift_and_keep_roles(self):
        for order in ('ABBA','BAAB'):
            sequence=iteration.comparison_sequence(order)
            self.assertEqual(list(order),['B' if active else 'A' for _,active in sequence])
            means={label:10+index-(2 if active else 0) for index,(label,active) in enumerate(sequence)}
            with patch.object(iteration,'arm_metrics',side_effect=lambda path,label:{'mean_total_frame_ms':means[label]}):
                result=iteration.balanced_evaluation('/tmp/report',order=order,execution_completed=True,restored=True)
            self.assertEqual('VALID',result['validity'])
            self.assertAlmostEqual(2,result['group_means_ms']['native']-result['group_means_ms']['candidate'])
            self.assertAlmostEqual(100*2/11.5,result['frame_time_reduction_pct'])
        self.assertEqual('INVALID',iteration.balanced_evaluation('/tmp/report',order='ABBA',execution_completed=True,restored=False)['validity'])

    def test_frame_variance_uses_valid_intervals_only(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)
            guard={'valid':True,'completed_render_frames':4,'excluded_initial_unavailable_interval':True,
                   'statistics':{'count':3},'wall_ns':40_000_000}
            row={'id':'native_0','validity':'VALID','statistics':{'count':3},
                 'samples':[{'totalFrameNs':None},*({'totalFrameNs':v} for v in (8_000_000,10_000_000,12_000_000))]}
            (path/'guard.json').write_text(json.dumps(guard))
            (path/'result.json').write_text(json.dumps({'manifest':{'windows':[row]}}))
            result=iteration.arm_metrics(path,'native_0')
            self.assertEqual((10,2,4),tuple(result[k] for k in ('mean_total_frame_ms','sd_total_frame_ms','variance_total_frame_ms2')))
            row['samples'][2]['totalFrameNs']=None
            (path/'result.json').write_text(json.dumps({'manifest':{'windows':[row]}}))
            with self.assertRaisesRegex(ValueError,'Invalid total-frame sample'):iteration.arm_metrics(path,'native_0')

    def test_baseline_is_original_only_with_conditioned_native_windows(self):
        with patch.object(runner,'preflight',return_value={'errors':[]}),patch.object(runner,'run_operation',return_value=0) as run:
            self.assertEqual(0,runner.main(['baseline','--pack','/tmp/original.zip','--output','/tmp/report']))
        args=run.call_args.args[0]
        self.assertTrue(args.native_baseline and args.native_startup_check and args.standard_test)
        self.assertEqual(('smoke','absent',20,120,900),(args.suite,args.arm,args.seconds,args.conditioning,args.budget))

    def test_baseline_variance_uses_window_means_and_rejects_partial_runs(self):
        with patch.object(iteration,'arm_metrics',side_effect=[{'mean_total_frame_ms':10},{'mean_total_frame_ms':12},{'mean_total_frame_ms':14}]):
            result=iteration.baseline_evaluation('/tmp/report',execution_completed=True,restored=True)
        self.assertEqual(('VALID',12,2,4),tuple(result[k] for k in ('validity','mean_window_ms','sd_window_mean_ms','variance_window_mean_ms2')))
        self.assertEqual('INVALID',iteration.baseline_evaluation('/tmp/report',execution_completed=False,restored=True)['validity'])

    def test_one_command_owns_protocol_and_sixty_total_seconds(self):
        with patch.object(runner,'preflight',return_value={'errors':[]}),patch.object(runner,'run_operation',return_value=0) as run:
            self.assertEqual(0,runner.main(['test','--pack','/tmp/original.zip','--output','/tmp/report','--feature','unbound_dead_mips']))
        args=run.call_args.args[0]
        self.assertTrue(args.run and args.standard_test)
        self.assertEqual(('public-smoke','master-off',20,5,120,900),(args.suite,args.arm,args.seconds,args.warmup,args.conditioning,args.budget))
    def test_standard_schedule_cannot_be_replaced_by_diagnostic_schedule(self):
        with patch.object(runner,'preflight') as preflight:
            self.assertEqual(1,runner.main(['test','--pack','/tmp/original.zip','--output','/tmp/report','--suite','smoke']))
        preflight.assert_not_called()
    def test_only_build_binding_failure_is_repaired_once_before_test(self):
        with patch.object(runner,'preflight',side_effect=[{'errors':[{'check':'existing_build_binding'}]},{'errors':[]}]),patch.object(runner.dev,'runtime_lock'),patch.object(runner.dev,'clients',return_value={}),patch.object(runner.dev,'process_table',return_value={}),patch.object(runner.dev,'compile_current') as build,patch.object(runner,'run_operation',return_value=0):
            self.assertEqual(0,runner.main(['test','--pack','/tmp/original.zip','--output','/tmp/report','--feature','feature']))
        build.assert_called_once_with(timeout=180)


class G10ContractTest(unittest.TestCase):
    def test_real_producer_stage_restores_existing_files_and_preserves_original(self):
        import experiment_lifecycle as life
        import zipfile
        root=Path(__file__).resolve().parents[1]
        pack=root/'dependencies/shortlist-test-inputs/originals/MakeUpNativeOriginal.zip'
        options=root/'dev/experiments/shortlist-resume-20261101/makeup-native100.txt'
        identity=iteration.g10_identity(pack,options,root);original=life.digest(pack)
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);profile=base/'slot/client';(profile/'shaderpacks').mkdir(parents=True)
            paths=[profile/'shaderpacks'/(identity[k]+suffix) for k in ['generated','work'] for suffix in ['', '.txt']]
            paths[0].write_bytes(b'preserve prior private artifact');paths[1].write_bytes(b'preserve prior options')
            op=life.Operation.create(base/'report',profile,{},paths)
            staged=iteration.stage_g10(op,pack,options,identity)
            self.assertEqual(original,life.digest(pack));self.assertEqual(staged['lean_sha256'],life.digest(paths[0]))
            with zipfile.ZipFile(paths[0]) as archive:
                self.assertIn(b'dither == 0.0',archive.read('shaders/lib/bloom.glsl'))
            self.assertIn(b'SHADERLOD_G10_BLOOM_PAIRING=3',paths[1].read_bytes())
            self.assertIn(b'SHADERLOD_G10_BLOOM_PAIRING=4',paths[3].read_bytes())
            op.restore();self.assertEqual(b'preserve prior private artifact',paths[0].read_bytes());self.assertEqual(b'preserve prior options',paths[1].read_bytes())
            self.assertFalse(paths[2].exists());self.assertFalse(paths[3].exists())

    def test_only_private_selector_may_differ_from_native_off(self):
        identity={'native_options':{'AA':'2'}}
        state={'pack':'Candidate.zip','effective_options':{'AA':'2','SHADERLOD_G10_BLOOM_PAIRING':'3'},'installed':{'master':False,'flags':{}}}
        iteration.g10_activation(state,identity,active=True,expected_pack='Candidate.zip')
        for options in [{'AA':'0','SHADERLOD_G10_BLOOM_PAIRING':'3'},{'AA':'2','SHADERLOD_G10_BLOOM_PAIRING':'1'},{'AA':'2','SHADERLOD_G10_BLOOM_PAIRING':'3','OTHER':'1'}]:
            with self.assertRaises(ValueError):iteration.g10_activation(state|{'effective_options':options},identity,active=True,expected_pack='Candidate.zip')
    def test_native_control_has_its_own_name_and_requires_selector_zero(self):
        root=Path(__file__).resolve().parents[1];pack=root/'dependencies/shortlist-test-inputs/originals/MakeUpNativeOriginal.zip';options=root/'dev/experiments/shortlist-resume-20261101/makeup-native100.txt'
        identity=iteration.g10_identity(pack,options,root,native_control=True)
        self.assertEqual('0',identity['compile_mode']);self.assertIn('native-',identity['generated'])
        state={'pack':'Control.zip','effective_options':identity['native_options']|{'SHADERLOD_G10_BLOOM_PAIRING':'0'},'installed':{'master':False,'flags':{}}}
        iteration.g10_activation(state,identity,active=True,expected_pack='Control.zip')
        state['effective_options']['SHADERLOD_G10_BLOOM_PAIRING']='3'
        with self.assertRaises(ValueError):iteration.g10_activation(state,identity,active=True,expected_pack='Control.zip')

    def test_actual_linked_branch_and_consuming_draw_progress_required(self):
        record={'qualified':True,'draws':4,'program':7,'fragment_source':'if (dither == 0.0) {'+'blurSample += g10Sample;'*4+'}'}
        iteration.g10_draw(record,active=True)
        iteration.g10_draw(record|{'draws':8},active=True,before=record)
        for change in [{'qualified':False},{'draws':0},{'fragment_source':'native fallback'}]:
            with self.assertRaises(ValueError):iteration.g10_draw(record|change,active=True)
        with self.assertRaises(ValueError):iteration.g10_draw(record,active=True,before=record)
        with self.assertRaises(ValueError):iteration.g10_draw(record|{'program':8,'draws':9},active=True,before=record)
    def test_work_histogram_requires_actual_writer_texture_and_integer_encoding(self):
        record={'draw_receipt':{'qualified':True,'work_texture':22,'draw_counter':31},'image':{'state_restored':True,'diagnostic':{'kind':'G10-bloom'},'banks':[{'texture':22,'width':2,'height':2,'eligible_pixels':4,'histogram':[0,3,0,0,1]}]}}
        value=iteration.g10_image(record);self.assertEqual((15,1,3),(value['producer_phase'],value['reuse_pixels'],value['omitted_source_query_expressions']))
        record['draw_receipt']['work_texture']=33
        with self.assertRaisesRegex(ValueError,'joined'):iteration.g10_image(record)
        record['draw_receipt']['work_texture']=22;record['image']['banks'][0]['histogram']=[1,2,0,0,1]
        with self.assertRaisesRegex(ValueError,'encoding'):iteration.g10_image(record)


class C01ContractTest(unittest.TestCase):
    def test_native_off_original_and_full_map_remain_required(self):
        identity={'native_options':{'AA':'2'}}
        state={'pack':'Original.zip','effective_options':{'AA':'2'},'installed':{'master':False,'flags':{}}}
        iteration.c01_activation(state,identity,expected_pack='Original.zip')
        for field,value in [('pack','Other.zip'),('effective_options',{'AA':'0'}),('installed',{'master':True,'flags':{}}),('installed',{'master':False,'flags':{'other':'true'}})]:
            changed=dict(state);changed[field]=value
            with self.assertRaises(ValueError):iteration.c01_activation(changed,identity,expected_pack='Original.zip')
    def test_actual_query_read_progress_and_finite_active_mode_required(self):
        before=dict(mode='deferred_cpu',remaining_frames=1000,proxy_queries=10,cpu_result_reads=10,frames=3,refusal='')
        iteration.c01_work(before,active=True)
        after=before|dict(proxy_queries=20,cpu_result_reads=20,frames=6)
        iteration.c01_work(after,active=True,before=before)
        for change in [dict(mode='off'),dict(remaining_frames=0),dict(proxy_queries=0),dict(cpu_result_reads=0),dict(refusal='native source guard')]:
            with self.assertRaises(ValueError):iteration.c01_work(before|change,active=True)
        with self.assertRaisesRegex(ValueError,'progress'):iteration.c01_work(before,active=True,before=before)
        iteration.c01_work({'mode':'off'},active=False)


class ResourceSelectionTest(unittest.TestCase):
    def test_declared_resource_bytes_and_selected_ids_are_required(self):
        with tempfile.TemporaryDirectory() as directory:
            profile=Path(directory);(profile/'resourcepacks').mkdir()
            pack=profile/'resourcepacks/height.zip';pack.write_bytes(b'authentic fixture bytes')
            (profile/'options.txt').write_text('resourcePacks:["file/height.zip"]\n')
            contract=iteration.resource_contract(profile)
            self.assertIn('file/height.zip',contract['files'])
            baseline=['vanilla','mod_resources','file/height.zip']
            self.assertEqual(baseline,iteration.resource_selection({'selected_resource_packs':baseline},contract))
            for selected in [None,[],['vanilla','mod_resources'],baseline+['file/extra.zip'],baseline+['vanilla']]:
                with self.assertRaises(ValueError):iteration.resource_selection({'selected_resource_packs':selected},contract)
            with self.assertRaisesRegex(ValueError,'between arms'):
                iteration.resource_selection({'selected_resource_packs':list(reversed(baseline))},contract,baseline=baseline)
            pack.unlink()
            with self.assertRaisesRegex(ValueError,'missing'):iteration.resource_contract(profile)


class IterationEvidenceTest(unittest.TestCase):
    def test_photon_special_manifest_uses_its_pinned_producer(self):
        import build_photon_live_pixel_budget as photon
        import hashlib
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);pack=root/'photon.zip';pack.write_bytes(b'original')
            path=root/'src/main/resources/assets/shaderlod/adapters/patches/photon-experiments/live-pixel-budget.json'
            path.parent.mkdir(parents=True);path.write_text(json.dumps({'base_sha256':hashlib.sha256(b'original').hexdigest()}))
            generated={'shaders/'+d+'/gbuffers_terrain_solid.'+s:b'#define PROGRAM_GBUFFERS_TERRAIN_SOLID\n' for d in ('world0','world1','world-1') for s in ('fsh','vsh')}
            with patch('build_supported_pack_adapters.read_archive',return_value={}),patch('build_supported_pack_adapters.shader_source_digest',return_value='source'),patch.object(photon,'patch_members',return_value=generated) as validate:
                identity=iteration.public_identity(pack,root)
                self.assertEqual('photon',identity['family']);validate.assert_called_once_with({},json.loads(path.read_text()),'enabled',root=root)
                self.assertEqual({'PROGRAM_GBUFFERS_TERRAIN_SOLID':'true'},identity['program_markers'])
                pack.write_bytes(b'changed')
                with self.assertRaisesRegex(ValueError,'pinned maintained original'):iteration.public_identity(pack,root)
                self.assertEqual(1,validate.call_count)
    def test_exact_program_marker_does_not_allow_unknown_options_or_native_edits(self):
        state={'pack':'candidate','effective_options':{'QUALITY':'3','PROGRAM_GBUFFERS_TERRAIN_SOLID':'true'},
               'installed':{'family':'photon','master':True,'flags':{'feature':'true'}}}
        args=dict(feature='feature',enabled=True,expected_pack='candidate',family='photon',native_options={'QUALITY':'3'},program_markers={'PROGRAM_GBUFFERS_TERRAIN_SOLID':'true'})
        iteration.activation(state,**args)
        state['effective_options']['PROGRAM_UNKNOWN']='true'
        with self.assertRaisesRegex(ValueError,'Unexpected non-adapter'):iteration.activation(state,**args)
        del state['effective_options']['PROGRAM_UNKNOWN'];state['effective_options']['PROGRAM_GBUFFERS_TERRAIN_SOLID']='false'
        with self.assertRaisesRegex(ValueError,'program marker differs'):iteration.activation(state,**args)
        state['effective_options']['PROGRAM_GBUFFERS_TERRAIN_SOLID']='true';state['effective_options']['QUALITY']='1'
        with self.assertRaisesRegex(ValueError,'native options changed'):iteration.activation(state,**args)
    def test_bsl_reflection_diagnostic_reports_admission_without_claiming_work(self):
        control=Mock();control.send.return_value={'bsl_exact_reflection':'owners=0; native/refused'}
        evidence=iteration.untimed_work(control,feature='reflection_next_event_refinement',family='bsl-shaders')
        self.assertIn('owners=0',evidence['draw_admission_status'])
        self.assertIn('not a count',evidence['scope']);control.send.assert_called_once_with('reflection_status')
    def test_bsl_ao_diagnostic_preserves_zero_qualified_draws(self):
        control=Mock();control.send.return_value={'ao_depth':{'qualified_draws':0,'permissions':[]}}
        evidence=iteration.untimed_work(control,feature='ao_depth_reuse',family='bsl-shaders')
        self.assertEqual(0,evidence['draw_admission']['qualified_draws'])
        self.assertIn('not a count',evidence['scope']);control.send.assert_called_once_with('ao_depth_status')
    def test_work_witness_is_disarmed_even_when_observation_fails(self):
        control=Mock();control.ready.side_effect=ValueError('lost pipeline')
        with self.assertRaisesRegex(ValueError,'lost pipeline'):
            iteration.untimed_work(control,feature='dead_frame_copy',family='makeup-ultra-fast-shaders')
        self.assertEqual({'enabled':False,'reset':False},control.send.call_args.kwargs)
        self.assertEqual(('frame_copy_observe',),control.send.call_args.args)
    def test_bsl_witness_reload_is_explicit_and_off_before_return(self):
        control=Mock();control.send.return_value={'bsl_mip_witness':{'complete':False,'status':'not qualified'}}
        self.assertFalse(iteration.untimed_work(control,feature='dead_mip_chain',family='bsl-shaders')['complete'])
        self.assertEqual({'enabled':True,'reset':True,'reload':True},control.send.call_args_list[0].kwargs)
        self.assertEqual({'enabled':False,'reset':False},control.send.call_args.kwargs)
    def test_early_alpha_uses_existing_admission_and_counters_without_reload(self):
        control=Mock();control.send.return_value={'makeup_early_alpha_status':'owned=2; qualified','makeup_early_alpha_costs':{'qualified_draws':90},'makeup_early_alpha_source_diagnostic':{'source':True},'makeup_post_sample_draw_witness':{'linked_programs':0,'completed_batches':0}}
        observed=iteration.untimed_work(control,feature='early_alpha',family='makeup-ultra-fast-shaders')
        self.assertEqual(90,observed['draw_counters']['qualified_draws']);self.assertIn('not avoided',observed['scope']);self.assertEqual({'observe':True,'reset':True},control.send.call_args_list[0].kwargs);self.assertEqual({'observe':False,'reset':False},control.send.call_args.kwargs);control.wait.assert_called_once_with(2)
        state={'installed':{'early_alpha_draw_counters':{'qualified_draws':90}}}
        iteration.early_alpha_work(state)
        iteration.early_alpha_work({'installed':{'early_alpha_draw_counters':{'qualified_draws':100}}},before=state)
        for bad in [{},{'installed':{'early_alpha_draw_counters':{'qualified_draws':0}}}]:
            with self.assertRaises(ValueError):iteration.early_alpha_work(bad)
        with self.assertRaisesRegex(ValueError,'progress'):iteration.early_alpha_work(state,before=state)

    def test_early_alpha_observer_disarms_when_receipt_is_missing(self):
        control=Mock();control.send.return_value={}
        with self.assertRaisesRegex(ValueError,'receipt missing'):iteration.untimed_work(control,feature='early_alpha',family='makeup-ultra-fast-shaders')
        self.assertEqual({'observe':False,'reset':False},control.send.call_args.kwargs)

    def test_g10_observation_disarms_even_on_failure(self):
        control=Mock();control.send.return_value={'g10_bloom':{'draws':3}}
        self.assertEqual(3,iteration.g10_observation(control)['draws']);control.wait.assert_called_once_with(2)
        self.assertEqual({'observe':False},control.send.call_args.kwargs)
        control=Mock();control.send.return_value={}
        with self.assertRaises(KeyError):iteration.g10_observation(control)
        self.assertEqual({'observe':False},control.send.call_args.kwargs)

    def test_early_alpha_asahi_setup_is_explicitly_weaker_than_draw_admission(self):
        state={'installed':{'early_alpha_source_setup':{'rewritten':True,'rewritten_programs':1}}}
        activation={'source_diagnostic':dict.fromkeys(('exact_api','declaration','profile','source','compile_enabled','pack_view','input_present','input_current'),True)}
        receipt=iteration.early_alpha_work(state,before=state,activation=activation)
        self.assertEqual('asahi_source_setup_only',receipt['route'])
        self.assertIn('no observed submitted CUTOUT',receipt['limits'])
        linked=dict(activation,post_sample_draw_witness={'linked_programs':1,'completed_batches':3});self.assertEqual('asahi_linked_cutout_submissions',iteration.early_alpha_work(state,activation=linked)['route'])
        with self.assertRaisesRegex(ValueError,'link witness'):iteration.early_alpha_work(state,activation=dict(activation,post_sample_draw_witness={'linked_programs':0}))
        for key in activation['source_diagnostic']:
            bad={'source_diagnostic':dict(activation['source_diagnostic'],**{key:False})}
            with self.assertRaises(ValueError):iteration.early_alpha_work(state,activation=bad)
        with self.assertRaises(ValueError):iteration.early_alpha_work(state)

    def test_combination_activation_requires_both_flags_and_compile_controls(self):
        state={'pack':'generated.zip','installed':{'family':'makeup','master':True,'flags':{'dead_frame_copy':'true','ao_attenuation':'true'}},'effective_options':{'NATIVE':'same','SHADERLOD_COPY':'1','SHADERLOD_AO':'1'}}
        args=dict(feature=['dead_frame_copy','ao_attenuation'],enabled=True,expected_pack='generated.zip',family='makeup',native_options={'NATIVE':'same'},compile_control={'options':{'SHADERLOD_COPY':'1','SHADERLOD_AO':'1'}})
        iteration.activation(state,**args)
        for key in ['dead_frame_copy','ao_attenuation']:
            bad=__import__('copy').deepcopy(state);bad['installed']['flags'][key]='false'
            with self.assertRaises(ValueError):iteration.activation(bad,**args)
        for key in ['SHADERLOD_COPY','SHADERLOD_AO']:
            bad=__import__('copy').deepcopy(state);bad['effective_options'][key]='0'
            with self.assertRaises(ValueError):iteration.activation(bad,**args)

    def test_combination_copy_receipt_checks_counts_and_metadata(self):
        record={'apiPinned':True,'finalPassesCompleted':2,'finalPassesEntered':2,'executedSwapCopies':6}
        iteration.combination_copy_work(record,active=True)
        for bad in [{},dict(record,executedSwapCopies=12),dict(record,apiPinned=False),dict(record,metadataFailures=1),dict(record,finalPassesEntered=3)]:
            with self.assertRaises(ValueError):iteration.combination_copy_work(bad,active=True)
        iteration.combination_copy_work(dict(record,executedSwapCopies=12),active=False)

    def test_unrelated_feature_has_no_observation_or_reload(self):
        control=Mock()
        self.assertIsNone(iteration.untimed_work(control,feature='ao_attenuation',family='makeup-ultra-fast-shaders'))
        control.send.assert_not_called()
    def test_compile_contract_is_captured_with_the_pinned_recipe_identity(self):
        import build_supported_pack_adapters as producer
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);pack=root/'original.zip';pack.write_bytes(b'original')
            recipes=root/'src/main/resources/assets/shaderlod/adapters/patches/adapters';recipes.mkdir(parents=True)
            controls={'feature':{'option':'SHADERLOD_FEATURE','enabled':'1'}}
            recipe=recipes/'family.json';recipe.write_text(json.dumps({
                'id':'family','shader_source_digest':'source','optimization_compile_controls':controls}))
            with patch.object(producer,'read_archive',return_value={}), patch.object(producer,'shader_source_digest',return_value='source'), patch.object(producer,'apply'):
                identity=iteration.public_identity(pack,root)
            recipe.write_text('{}')
            self.assertEqual(controls,identity['compile_controls'])

    def test_requested_candidate_with_refused_compile_option_is_not_measured(self):
        state={'pack':'candidate.zip','effective_options':{'QUALITY':'3','SHADERLOD_FEATURE':'0'},
               'installed':{'family':'family','master':True,'flags':{'feature':'true'}}}
        kwargs=dict(feature='feature',enabled=True,expected_pack='candidate.zip',family='family',
                    compile_control={'option':'SHADERLOD_FEATURE','enabled':'1'})
        with self.assertRaisesRegex(ValueError,'compile admission refused'):iteration.activation(state,**kwargs)
        state['effective_options']['SHADERLOD_FEATURE']='1'
        iteration.activation(state,**kwargs)
        kwargs['compile_control']={'options':{'SHADERLOD_FEATURE':'1','SHADERLOD_PEER':'1'}}
        with self.assertRaisesRegex(ValueError,'SHADERLOD_PEER'):iteration.activation(state,**kwargs)
        state['effective_options']['SHADERLOD_PEER']='1'
        iteration.activation(state,**kwargs)

    def test_midpoint_gain_inside_native_drift_is_inconclusive(self):
        for candidate, expected in [(10.5,'INCONCLUSIVE_WITHIN_NATIVE_DRIFT'),
                                    (9,'BELOW_BOTH_NATIVE_CONTROLS'),
                                    (13,'ABOVE_BOTH_NATIVE_CONTROLS'),
                                    (10,'INCONCLUSIVE_WITHIN_NATIVE_DRIFT')]:
            with self.subTest(candidate=candidate), patch.object(iteration,'arm_metrics',side_effect=[
                {'mean_total_frame_ms':10}, {'mean_total_frame_ms':candidate}, {'mean_total_frame_ms':12}]):
                result=iteration.evaluate(Path('/unused'),execution_completed=True,restored=True)
            self.assertEqual(expected,result['drift_comparison'])
            self.assertEqual(2,len(result['reduction_vs_native_controls_percent']))
            self.assertFalse(result['closure'])

    def test_all_eight_inputs_include_optional_absence_and_modes(self):
        with tempfile.TemporaryDirectory() as directory:
            profile=Path(directory);(profile/'options.txt').write_text('native');(profile/'options.txt').chmod(0o640)
            actual=iteration.profile_map(profile)
            self.assertEqual(8,len(actual));self.assertEqual({'type':'absent'},actual['optionsof.txt'])
            self.assertEqual(0o640,actual['options.txt']['.']['mode'])
    def test_raw_request_repopulation_and_native_option_changes_are_rejected(self):
        state={'pack':'candidate.zip','effective_options':{'QUALITY':'3','SHADERLOD_ADAPTER':'1'},
               'installed':{'family':'family','master':True,'flags':{'feature':'true'}}}
        kwargs=dict(feature='feature',enabled=True,expected_pack='candidate.zip',family='family',native_options={'QUALITY':'3'})
        iteration.activation(state,**kwargs)
        state['installed']['flags']['other']='true'
        with self.assertRaisesRegex(ValueError,'exact feature set'):iteration.activation(state,**kwargs)
        del state['installed']['flags']['other'];state['effective_options']['QUALITY']='1'
        with self.assertRaisesRegex(ValueError,'native options'):iteration.activation(state,**kwargs)
    def test_failed_native_has_no_candidate_or_gain_conclusion(self):
        with tempfile.TemporaryDirectory() as directory:
            result=iteration.evaluate(Path(directory),execution_completed=False,restored=True)
        self.assertEqual('INVALID',result['validity']);self.assertEqual({},result['arms'])
        self.assertNotIn('speedup_ratio',result);self.assertFalse(result['closure'])
    def test_missing_total_frame_samples_cannot_be_replaced_by_guard_fps(self):
        with tempfile.TemporaryDirectory() as directory:
            window=Path(directory)/'windows/native';window.mkdir(parents=True)
            (window/'guard.json').write_text(json.dumps({'valid':True,'completed_render_frames':10,'wall_ns':10**9,'count_throughput_fps':10}))
            (window/'result.json').write_text(json.dumps({'manifest':{'windows':[{'id':'native','validity':'VALID','samples':[]}]}}))
            result=iteration.evaluate(Path(directory),execution_completed=True,restored=True)
        self.assertEqual('INVALID',result['validity']);self.assertNotIn('speedup_ratio',result)
    def test_unknown_original_input_change_prevents_any_reset_or_world_replacement(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);profile=root/'profile';source=root/'canonical';profile.mkdir();source.mkdir()
            (profile/'options.txt').write_text('original');(source/'options.txt').write_text('original')
            before=iteration.profile_map(profile);backup=root/'world-before';backup.mkdir()
            record={'source':str(source),'original':before,'canonical':iteration.profile_map(source),
                    'world_backup':str(backup),'reset_attempted':False}
            op=Mock(output=root,data={'profile':str(profile),'iteration_profile':record,'release_verified':True,'configuration_restored':True})
            (profile/'options.txt').write_text('unknown edit')
            with patch.object(iteration,'stopped_profiles',return_value=nullcontext()),patch.object(iteration.storage,'checkout') as reset:
                with self.assertRaisesRegex(ValueError,'Non-world'):iteration.finish_profile(op)
            reset.assert_not_called();self.assertEqual('unknown edit',(profile/'options.txt').read_text())
    def test_second_reset_is_never_replayed(self):
        op=Mock(data={'iteration_profile':{'reset_attempted':True}})
        with patch.object(iteration.storage,'checkout') as reset:
            with self.assertRaisesRegex(ValueError,'already attempted'):iteration.finish_profile(op)
        reset.assert_not_called()

    def test_verified_reset_rechecks_inputs_without_replaying_or_needing_backups(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);profile=root/'profile';source=root/'canonical';profile.mkdir();source.mkdir()
            for p in (profile,source):(p/'options.txt').write_text('original')
            info=profile.stat()
            record={'source':str(source),'original':iteration.profile_map(profile),'canonical':iteration.profile_map(source),
                    'world_backup':str(root/'disposed-backup'),'reset_attempted':True,'reset_verified':True,'restored':True,
                    'new_profile_identity':[info.st_dev,info.st_ino]}
            op=Mock(output=root,data={'profile':str(profile),'iteration_profile':record,'release_verified':True,'configuration_restored':True})
            with patch.object(iteration,'stopped_profiles',return_value=nullcontext()),patch.object(iteration.storage,'checkout') as reset:
                iteration.finish_profile(op)
                (profile/'options.txt').write_text('unrelated edit')
                with self.assertRaisesRegex(ValueError,'no longer matches'):iteration.finish_profile(op)
            reset.assert_not_called()
            self.assertEqual('unrelated edit',(profile/'options.txt').read_text())



class MipActivationAdmissionTest(unittest.TestCase):
    def test_actual_control_required_even_without_recipe_metadata(self):
        for family, key in [('astralex','SHADERLOD_ASTRALEX_DEAD_MIP_CHAIN'),('photon','SHADERLOD_PHOTON_DEAD_MIP_CHAIN')]:
            state={'pack':'Candidate.zip','installed':{'family':family,'master':True,'flags':{'dead_mip_chain':'true'},'dead_mip_setup':'native proof refused'},'effective_options':{key:'0'}}
            args=dict(feature='dead_mip_chain',enabled=True,expected_pack='Candidate.zip',family=family)
            with self.assertRaisesRegex(ValueError,'native proof refused'):iteration.activation(state,**args)
            state['effective_options'][key]='1'
            iteration.activation(state,**args)

if __name__=='__main__':unittest.main()
