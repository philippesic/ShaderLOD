"""Actual Gson state/window replay; full four-phase report is an explicit UNIT MODEL.

GPU/CPU timings, images and artifact locators in fixture_report are synthetic.
Passing them establishes gates/schema compatibility, never a Minecraft capture.
"""
import copy
import hashlib
import json
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
import analyze_water_shadow_control as water
from test_live_pixel_control import fixture_report as pixel_model

FIXTURE=ROOT/'build/water-shadow-guard-r1'


def actual_state():
    return json.loads((FIXTURE/'off-state.json').read_text())


def record(text):
    return dict(bytes=len(text.encode()),utf8=text,sha256=hashlib.sha256(text.encode()).hexdigest())


def rehash(state):
    for field in ('water_shadow_zero_source_qualification','water_shadow_zero_effect_controls'):
        state[field+'_sha256']=water.hashed(state[field])
    state['effective_shader_options_sha256']=water.hashed(state['effective_shader_options'])
    state['water_shadow_zero_configuration_semantics']=water.configuration(state['configuration_files'])[0]
    for name in (water.MAIN,water.EFFECTS,water.IRIS):
        state[name+'_sha256']=state['configuration_files']['files'][name]['sha256']


def fixture_report():
    report,receipt=pixel_model()  # Existing explicit unit timing/artifact topology only.
    report.update(benchmark_feature='water_shadow_zero',method=water.METHOD,configured_hide_gui=True,
        water_stage_runtime_qualification=False,executed_skip_coverage_proof=False,control_receipt_schema=2,
        water_shadow_zero_diagnostic_limits=water.PRODUCER_LIMITS,initial_feature_enabled=report['initial_enabled'])
    for key in ('raster_policy_proof','live_pixel_diagnostic_limits'):report.pop(key,None)
    report['instrumentation_properties'].update(benchmarkFeature='water_shadow_zero',fixedWetness='0',performanceStack='minimal',includeDh='false',smoke='false')
    state=actual_state();source=state['water_shadow_zero_source_qualification']
    report['workload-manifest.json']=dict(schema=1,scene='coastalwater',configuration_only=False,source_world_sha256=water.WORLD,
        source_qualification=dict(archive_sha256=water.ARCHIVE,complete_member_sha256=source['source_fingerprint']['memberSha256']),
        experiment_arms={'toggle':dict(archive_sha256=water.ARCHIVE,expected_effective_options=state['effective_shader_options'],expected_effective_options_sha256=water.OPTIONS_HASH)},
        benchmark_recipe=dict(feature='water_shadow_zero',phases=[False,True,False,True]))
    window=json.loads((FIXTURE/'window.json').read_text())
    for phase in report['phases']:
        for boundary in ('inputStart','inputEnd'):
            state=actual_state();state['lod_settings']['enabled']=phase['enabled'];state['loaded_semantic_config']['lod']['enabled']=phase['enabled']
            phase[boundary]=state
        phase['windowState']=copy.deepcopy(window)
        phase['windowState']['capture']['start']['frame_id']=phase['startFrame']
        phase['windowState']['capture']['end']['frame_id']=phase['endFrame']
    report['stable_input_reference']=water.stable(report['phases'][0]['inputStart'])
    return report,receipt


class WaterShadowControlTests(unittest.TestCase):
    def setUp(self):
        if not (FIXTURE/'off-state.json').exists():self.skipTest('Actual local Gson fixture unavailable; no modeled replacement')

    def test_genuine_java_gson_state_stable_and_window_contract(self):
        state=actual_state();before=water.hashed(state)
        self.assertFalse(water.validate_state(state,False))
        self.assertEqual(water.stable(state),json.loads((FIXTURE/'stable-reference.json').read_text()))
        phase=dict(inputStart=state,inputEnd=copy.deepcopy(state),windowState=json.loads((FIXTURE/'window.json').read_text()),startFrame=1,endFrame=601)
        water.validate_window(phase)
        self.assertEqual(water.hashed(state),before)
        self.assertEqual(len(state['water_shadow_zero_effect_controls']),7)
        for field in ('water_shadow_zero_source_qualification','water_shadow_zero_effect_controls'):
            self.assertEqual(water.hashed(state[field]),state[field+'_sha256'])

    def test_full_schema_unit_model_not_runtime_evidence(self):
        report,receipt=fixture_report();result=water.analyze_report(report,receipt)
        self.assertEqual(result['status'],'QUALIFIED_SETTLED_INCREMENTAL_DIAGNOSTIC_ONLY')
        self.assertEqual(len(result['phases']),4);self.assertFalse(result['performanceEvidence']);self.assertFalse(result['original_native_gain'])
        self.assertFalse(result['executed_skip_coverage_proof']);self.assertFalse(result['water_stage_runtime_qualification'])
        self.assertTrue(all(not p['image_verified'] for p in result['phases']))
        self.assertIn('not original Photon',result['limits'])
        self.assertIn(str(ROOT/'src/devClient/java/com/pp/shaderlod/dev/BenchmarkSession.java'),result['analyzer_source_files_sha256'])

    def test_headers_feature_proofs_restoration_and_probes_reject(self):
        for key,value in (('benchmark_feature','live_pixel'),('method','unknown'),('transport','effects'),('core_protocol',True),
            ('control_receipt_schema',1),('configured_hide_gui',False),('water_stage_runtime_qualification',True),
            ('executed_skip_coverage_proof',True),('restored_initial_enabled',False),('restored_initial_feature',False),
            ('starting_order','ON/OFF/ON/OFF'),('initial_warmup_seconds',29.99),('settle_seconds',7.99),('measurement_seconds',19.99)):
            report,receipt=fixture_report();report[key]=value
            with self.assertRaises(ValueError,msg=key):water.analyze_report(report,receipt)
        for key,value in (('cloudSamplerProbe','true'),('smoke','true'),('cpuLayerTiming','true'),('fixedWetness','1'),('includeDh','true')):
            report,receipt=fixture_report();report['instrumentation_properties'][key]=value
            with self.assertRaises(ValueError,msg=key):water.analyze_report(report,receipt)
        report,receipt=fixture_report();report['raster_policy_proof']=False
        with self.assertRaises(ValueError):water.analyze_report(report,receipt)

    def test_full_source_options_schema_and_unknown_legacy_reject(self):
        for mutation in ('archive','member','option','schema','legacy'):
            report,receipt=fixture_report();state=report['phases'][0]['inputEnd']
            if mutation=='archive':state['actual_shader_sha256']='0'*64
            elif mutation=='member':state['water_shadow_zero_source_qualification']['source_fingerprint']['memberSha256']['LICENSE']='0'*64
            elif mutation=='option':state['effective_shader_options']['SHADOW_VPS']='true'
            elif mutation=='schema':state['water_shadow_zero_control_receipt_schema']=True
            else:report['workload-manifest.json']['adaptive_pack_sha256']=water.ARCHIVE
            rehash(state)
            with self.assertRaises(ValueError,msg=mutation):water.analyze_report(report,receipt)

    def test_canonical_object_order_only_and_complete_unknown_settings(self):
        state=actual_state()
        for field in ('water_shadow_zero_source_qualification','water_shadow_zero_effect_controls'):
            expected=state[field+'_sha256'];state[field]=dict(reversed(list(state[field].items())))
            self.assertEqual(water.hashed(state[field]),expected)
        water.validate_state(state,False)
        report,receipt=fixture_report();state=report['phases'][0]['inputEnd']
        state['configuration_files']['files']['config/foreign.cfg']=record('unknown\n');rehash(state)
        with self.assertRaisesRegex(ValueError,'Stable'):water.analyze_report(report,receipt)

    def test_only_persisted_main_boolean_may_lag_live_actual_flag(self):
        report,receipt=fixture_report();state=report['phases'][0]['inputEnd']
        text=state['configuration_files']['files'][water.MAIN]['utf8'];state['configuration_files']['files'][water.MAIN]=record(text.replace('enabled = false','enabled = true',1));rehash(state)
        result=water.analyze_report(report,receipt);self.assertEqual(result['phases'][0]['persisted_global_start_end'],[False,True])
        state['lod_settings']['enabled']=True
        with self.assertRaises(ValueError):water.analyze_report(report,receipt)

    def test_effect_snapshot_all_seven_inherit_and_backup_are_strict(self):
        for mutation in ('mode','ratio','missing','backup'):
            report,receipt=fixture_report();state=report['phases'][0]['inputEnd']
            if mutation=='mode':state['water_shadow_zero_effect_controls']['OPAQUE_LIGHTING']['mode']='ADAPTIVE'
            elif mutation=='ratio':state['water_shadow_zero_effect_controls']['WATER_SURFACE']['policy']['farWorkRatio']=.5
            elif mutation=='missing':state['water_shadow_zero_effect_controls'].pop('OPAQUE_LIGHTING')
            else:
                name='config/shaderlod-effects-client-1.toml.bak';text=state['configuration_files']['files'][name]['utf8']
                state['configuration_files']['files'][name]=record(text.replace('mode="INHERIT"','mode="ADAPTIVE"',1))
            state['loaded_semantic_config']['effects']=copy.deepcopy(state['water_shadow_zero_effect_controls'])
            with self.assertRaises(ValueError):rehash(state);water.analyze_report(report,receipt)

    def test_gpu_n5_positive_unique_epoch_and_counter_guards(self):
        for mutation in ('few','gap','duplicate','zero','nan','bool','epoch','drop','scheduled','out-of-order'):
            report,receipt=fixture_report();phase=report['phases'][0]
            if mutation=='few':phase['gpu'].pop()
            elif mutation=='gap':phase['gpu'][3]['frameId']+=1
            elif mutation=='duplicate':phase['gpu'][3]['frameId']=phase['gpu'][2]['frameId']
            elif mutation=='epoch':phase['gpu'][3]['epoch']=99
            elif mutation=='drop':phase['timingSnapshot']['scopes']['game-render']['droppedQueries']=1
            elif mutation=='scheduled':phase['timingSnapshot']['scheduledSampledFrames']+=5
            elif mutation=='out-of-order':phase['gpu'][2],phase['gpu'][3]=phase['gpu'][3],phase['gpu'][2]
            else:phase['gpu'][3]['milliseconds']={'zero':0,'nan':float('nan'),'bool':True}[mutation]
            with self.assertRaises(ValueError,msg=mutation):water.analyze_report(report,receipt)

    def test_cpu_four_scope_source_boundary_offsets_and_interior_loss(self):
        report,receipt=fixture_report();phase=report['phases'][0]
        # One extra last-boundary clock in one scope is permitted by actual source
        # draining order; interior gaps/duplicates/reordering remain invalid.
        sample=copy.deepcopy(next(r for r in phase['cpu'] if r['scope']=='game-render'));sample['frameId']=phase['endFrame'];phase['cpu'].append(sample)
        water.analyze_report(report,receipt)
        for mutation in ('run-only','interior','duplicate','unknown','thread-unknown'):
            report,receipt=fixture_report();phase=report['phases'][0]
            if mutation=='run-only':phase['cpu']=[r for r in phase['cpu'] if r['scope']=='run-tick']
            elif mutation=='interior':phase['cpu'].pop(12)
            elif mutation=='duplicate':phase['cpu'].append(copy.deepcopy(phase['cpu'][12]))
            elif mutation=='unknown':phase['cpu'][12]['scope']='invented'
            else:phase['cpu'][12]['threadMilliseconds']=None
            with self.assertRaises(ValueError,msg=mutation):water.analyze_report(report,receipt)

    def test_fixed_coast_window_native_focus_gui_and_time(self):
        for key,value in (('hide_gui',False),('gui_scale',True),('day_time',6000),('yaw',90),('water_shadow_zero_dh_present',True),('water_shadow_zero_fov',71)):
            report,receipt=fixture_report();report['phases'][0]['inputEnd'][key]=value
            with self.assertRaises(ValueError,msg=key):water.analyze_report(report,receipt)
        report,receipt=fixture_report();report['phases'][0]['windowState']['capture']['framebuffer_size_counts']={'1488x836':2}
        with self.assertRaises(ValueError):water.analyze_report(report,receipt)

    def test_complete_direct_artifacts_cannot_match_subset(self):
        for mutation in ('registry','root','evidence','identity'):
            report,receipt=fixture_report()
            if mutation=='registry':report['loaded_mods']['foreign']='1'
            elif mutation=='root':report['loaded_artifact_evidence']['loaded_artifact_inventory'].pop()
            elif mutation=='evidence':report['loaded_artifact_evidence']['valid']=False
            else:receipt['loaded_artifact_inventory'][0]['identity']='0'*64
            with self.assertRaises(ValueError,msg=mutation):water.analyze_report(report,receipt)

    def test_screen_drift_and_original_denominator_not_redefined(self):
        rows=[dict(frame_mean_ms=f,gpu_median_ms=g) for f,g in zip((16,14,16,14),(15,13,15,13))]
        self.assertEqual(water.screen(rows)['verdict'],'promising-incremental-rejection-screen-only')
        rows[2]['frame_mean_ms']=20
        self.assertEqual(water.screen(rows)['verdict'],'inconclusive-off-control-drift')
        rows[2]['frame_mean_ms']=16;rows[1]['frame_mean_ms']=15.5
        self.assertEqual(water.screen(rows)['verdict'],'stop-no-useful-incremental-signal')


if __name__=='__main__':unittest.main()
