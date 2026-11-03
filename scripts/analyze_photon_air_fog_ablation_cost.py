#!/usr/bin/env python3
"""Strict actual private fixed-graph air-fog global OFF/ON/OFF cost reader."""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
import math
from pathlib import Path
import re
import sys
import zipfile

import prepare_photon_air_fog_ablation_cost as prep
import prepare_photon_air_fog_ablation_runtime as driver
import analyze_photon_rain_vl_cost as rain

ROOT=prep.ROOT
shared=rain.shared
quality=shared.quality
actual=shared.actual
producer=shared.producer
require=actual.require
ORDER,ENABLED=prep.ORDER,prep.ENABLED
PASS='PASS actual Workload fixed-graph air-fog ablation cost components'
EVIDENCE=dict(performanceEvidence=False,adapter_gain=False,repeated_gain=False,quality_equivalent=False,
    quality='deliberately missing air fog',diagnostic_only=True,fixed_graph=True,vl_analytic_fallback=False,
    neutral_output_branch_proof=False,retired_gpu_work_proof=False,strict_additive_body_cost_bound=False,
    stage_identity='unobserved by cost recipe',evidence_class='private-fixed-graph-air-fog-ablation-cost-calibration')


def source_pins():
    paths=[ROOT/'scripts/analyze_photon_air_fog_ablation_cost.py',ROOT/'tests/test_photon_air_fog_ablation_cost_analysis.py']
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            path=Path(name).resolve()
            if path.suffix=='.py'and path.is_relative_to(ROOT/'scripts'):paths.append(path)
    return {str(p):prep.file_hash(p)for p in sorted(set(paths))}


def qualify_preflight(path,preparation_path,pin):
    record = json.loads(Path(path).read_text())
    require(record.get('argv')==['python3','scripts/prepare_photon_air_fog_ablation_cost.py','--verify',str(Path(preparation_path).resolve())]
        and type(record.get('returncode')) is int and record['returncode']==0,'Standalone fresh prep verify invocation failed/differs')
    require(record.get('preparation_sha256')==pin,'Preflight preparation hash differs')
    output = json.loads(record.get('stdout',''))
    require(output.get('status')=='verified-three-current-R10-unused-fixedgraph-air-fog-cost-controls'
        and output.get('launch_authorized') is False and output.get('owned_live_processes')==[], 'Standalone prep verify result differs')
    actual.integer(record.get('verified_ns'),'preflight timestamp',1)
    origin = record.get('verified_time_provenance',{})
    require(origin.get('kind')=='filesystem_write_timestamp_of_prior_successful_standalone_cli_receipt',
        'Preflight timestamp source differs/missing')
    raw_path = Path(origin['path'])
    require(raw_path.resolve().is_relative_to(ROOT/'dev/logs') or raw_path.resolve().is_relative_to(ROOT/'build'),
        'Preflight raw provenance must remain ignored')
    require(prep.file_hash(raw_path)==origin.get('sha256') and raw_path.stat().st_mtime_ns==record['verified_ns'],
        'Raw preflight hash/write timestamp differs')
    raw_record = {k:v for k,v in record.items() if k not in ('verified_ns','verified_time_provenance')}
    require(json.loads(raw_path.read_text())==raw_record,'Derived preflight fields differ from raw CLI receipt')
    return record




def main_enabled_signature(settings,enabled):
    """Preserve every class/config setting; only this boolean delta is authorized."""
    require(type(enabled)is bool and isinstance(settings,dict),'Typed complete settings/global required')
    result=copy.deepcopy(settings)
    main=result.get('config/shaderlod-client.toml',{})
    require(main.get('format')=='toml'and isinstance(main.get('value'),dict)
        and type(main['value'].get('lod',{}).get('enabled'))is bool and main['value']['lod']['enabled']is enabled,
        'Complete captured main enabled differs/missing')
    del main['value']['lod']['enabled']
    return result


def input_scalar_signature(state,enabled):
    """Complete exported class/scalar map; config evidence handled separately."""
    result=copy.deepcopy(state);settings=result.get('lod_settings',{})
    require(type(settings.get('enabled'))is bool and settings['enabled']is enabled,'Actual class snapshot global differs/missing')
    del settings['enabled']
    result.pop('configuration_files',None)
    # Every hash alias was validated against its raw complete config record;
    # complete semantic settings, all effects and authorized global delta have
    # a separate comparison. Unknown scalar fields stay in this signature.
    allowed_aliases={'config/iris.properties_sha256','config/DistantHorizons.toml_sha256','config/shaderlod-client.toml_sha256'}
    aliases={key for key in result if key.startswith('config/')and key.endswith('_sha256')}
    require(aliases<=allowed_aliases,'Unknown exported configuration hash alias')
    for key in aliases:del result[key]
    require(isinstance(result.get('control_sha256'),str),'Actual control hash alias missing')
    del result['control_sha256']
    return result


def boundary(report,row,preparation):
    expected=row['expected'];enabled=expected['global_enabled'];decoded=[];signatures=[];pose=quality.copies.POSES['coastalwater']
    require(type(enabled)is bool,'Expected global must be boolean')
    require(report.get('effective_shader_options')==expected['expected_effective_options']
        and report.get('effective_shader_options_sha256')==expected['expected_effective_options_sha256'],'Actual complete367 map differs')
    for name in ('input_state_start','input_state_end'):
        state=report.get(name,{})
        require(state.get('effective_shader_options')==expected['expected_effective_options']
            and state.get('effective_shader_options_sha256')==expected['expected_effective_options_sha256'],'Boundary complete367 map differs')
        require(state.get('shader_pack')==expected['archive']and state.get('shaders_in_use')is True
            and state.get('fallback')is False and state.get('dimension')=='minecraft:overworld','Selected private pack/pipeline/dimension differs')
        for key,target in dict(framebuffer_width=2976,framebuffer_height=1672,requested_chunks=24,effective_chunks=24,day_time=12500).items():
            require(type(state.get(key))is int and state[key]==target,'Actual native rain scene scalar differs: '+key)
        for key,target in ({k:v for k,v in pose.items()if k!='time'}|dict(rain=1.,thunder=0.)).items():
            value=state.get(key);require(type(value)in(int,float)and math.isfinite(value)and abs(value-target)<=.002,'Rain scene camera/weather differs: '+key)
        require(state.get('lod_settings')==dict(enabled=enabled,near=32.,far=96.,transition=8.,debug=False)
            and state.get('lod_preset')=='PERFORMANCE'and state.get('terrain_active')is False,'Actual exported class/main controls differ')
        qualified=quality.forest.gui.qualify_configuration(state.get('configuration_files',{}),row)
        for config in ('config/shaderlod-client.toml','config/shaderlod-effects-client.toml'):
            require(qualified['settings'][config]==row['prepared_writable_inventory']['settings'][config],'Complete captured main/effects settings differ')
        quality.forest.validate_effects(state['configuration_files']['files']['config/shaderlod-effects-client.toml']['utf8'])
        main_enabled_signature(qualified['settings'],enabled)
        for key,value in state.items():
            if key.startswith('config/')and key.endswith('_sha256'):
                require(state['configuration_files']['files'].get(key[:-7],{}).get('sha256')==value,'Raw config alias differs')
        require(state.get('control_sha256')==row['inputs']['control_sha256'],'Actual scalar control hash differs')
        decoded.append(qualified);signatures.append(input_scalar_signature(state,enabled))
    require(report['input_state_start']==report['input_state_end']and decoded[0]==decoded[1]and signatures[0]==signatures[1],
        'Complete actual captured boundaries changed')
    archive=Path(row['profile'])/'shaderpacks'/expected['archive']
    require(prep.file_hash(archive)==expected['archive_sha256']==driver.ARCHIVE_SHA256,'Exact private archive differs')
    with zipfile.ZipFile(archive)as z:
        names=[n for n in z.namelist()if not n.endswith('/')];require(len(names)==len(set(names))==533,'Private full533 archive differs/duplicate')
        members={n:hashlib.sha256(z.read(n)).hexdigest()for n in names}
    source=preparation['source_qualification']
    require(members==source['complete_member_sha256']==report['workload-manifest.json']['source_qualification']['complete_member_sha256'],
        'All private source/member hashes differ')
    require(members['LICENSE']=='514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5','Original license differs')
    return decoded[0],signatures[0]


def launch_controls(row,launch):
    require(row['command']==prep.command(row['profile'])==rain.prep.command(row['profile']),'Exact shared rainy cost argv differs')
    return rain.launch_controls(row,launch)


def validate_arm(report,row,preparation,launch,terminal,console,report_path=None):
    require(report.get('schema')==2 and report.get('request')==row['request'],'Actual fixed-graph Workload schema/request differs')
    require(row['request'].get('gpu_queries')is True and row['request'].get('cpu_timing')is True,'Full cost request differs')
    quality.matrix.validate_report(report,row['request'],row['inputs'],launch['started_ns'],report_path)
    rain_qualification=prep.qualify_rain(report,row['request']);temporal=quality.matrix.validate_temporal_inputs(report,row['request'])
    # Validate the actual fixed-wetness1 properties directly, using the shared
    # birth/VM schema. No private report is disguised as an original archive.
    require(row['command']==prep.command(row['profile'])==rain.prep.command(row['profile']),'Exact shared rainy Workload argv differs')
    properties=rain._birth_and_controls(row,launch)
    for key,target in (('hideWorkloadGui','true'),('pixelHistoryCapture','false')):
        require(properties.get('shaderlod.dev.'+key)==target,'Actual Java cost control differs: '+key)
    require(properties.get('shaderlod.dev.pixelHistoryCaptureOnly','false')=='false'
        and properties.get('shaderlod.dev.cameraQuality','')=='','Unexpected history/camera capture')
    gui=quality.forest.gui.qualify_gui_report(report,row)
    for key,value in report.get('runtime_properties',{}).items():require(properties.get(key)==value,'Actual Java/report property differs: '+key)
    require(report.get('runtime_mod_jars_sha256')=={},'Unexpected profile jar/DH')
    require(all(report.get(k)is False for k in ('shadow_culling_active','separate_shadow_lists_active','shadow_frustum_coherence_active')),'Unrelated culling active')
    policy=report.get('geometry_policy',{})
    require(policy.get('active')is False and policy.get('requestedChunks')==policy.get('effectiveChunks')==24,'Actual terrain policy differs')
    require(not any(k in report for k in ('pixel_coverage_evidence','work_trace_evidence','spatial_layout_evidence','pixel_history_evidence','water_scene_survey','dh_draw_evidence')),
        'Readback diagnostic contaminated cost')
    require(not (Path(row['profile'])/'logs/cloud-sampler-state.json').exists(),'Sampler probe artifact in timed profile')
    qualified,scalar_signature=boundary(report,row,preparation)
    evidence=report.get('loaded_artifact_evidence',{});receipt=preparation['independent_artifact_receipt']['receipt']
    require(evidence.get('valid')is True and evidence.get('complete_mod_coverage')is True
        and evidence.get('complete_identities')is True and evidence.get('errors')==[],'Actual direct artifact evidence incomplete/error')
    require(evidence.get('loaded_mods')==report.get('loaded_mods')==receipt['expected_loaded_mods']and len(report['loaded_mods'])==10,'Exact ten loaded mods differ')
    inventory=report.get('loaded_artifact_inventory')
    require(isinstance(inventory,list)and len(inventory)==9 and inventory==evidence.get('loaded_artifact_inventory'),'Complete nine-root inventory differs')
    quality.baseline.cost.artifacts.validate_inventory_against_receipt(inventory,receipt['loaded_artifact_inventory'],report['loaded_mods'])
    require(report.get('control.json')==row['inputs']['control']and report.get('control_sha256')==row['inputs']['control_sha256'],'Actual full global/rain control differs')
    manifest_qualification=shared.qualify_actual_manifest(report.get('workload-manifest.json'),row)
    result=producer.timing(report)
    result.update(arm=row['arm'],global_enabled=row['expected']['global_enabled'],status=PASS,window_reference=actual.window(report),
        hardware_context=producer.hardware_context(console),gui=gui,temporal=temporal,rain_qualification=rain_qualification,
        complete_settings=qualified['settings'],actual_configurations=qualified['actual_configuration_files'],
        qualified_backups=qualified['qualified_exact_first_backups'],loaded_mods=report['loaded_mods'],actual_inventory=inventory,
        raw_control=report['control.json'],full_options=report['effective_shader_options'],source_archive_sha256=row['expected']['archive_sha256'],
        input_scalar_signature=scalar_signature,manifest_qualification=manifest_qualification,
        terminal=producer.terminal_controls(row,launch,terminal),
        final_still=producer.screenshot(Path(row['profile'])/'screenshots/workload-final.png',launch['started_ns']))
    return result

def qualify_frozen_preparation(state,path,pin):
    require(re.fullmatch('[0-9a-f]{64}',pin or '')and prep.file_hash(Path(path))==pin,'Exact explicit fixedgraph preparation hash differs')
    require(state.get('status')==prep.FINAL,'Incomplete fixedgraph cost preparation')
    for field in ('maintained_sources_sha256','runtime_sources_sha256'):
        for name,value in state[field].items():require(prep.file_hash(Path(name))==value,'Frozen source drift: '+name)
    require(quality.baseline.control_prep.current_runtime_sources()==state['runtime_sources_sha256'],'Complete current runtime source inventory differs')
    require(quality.matrix.development_fingerprints()==state['development_runtime_inputs'],'Current class/resource roots differ')
    external=state['independent_artifact_receipt']
    require(external['sha256']==shared.RECEIPT_SHA256 and prep.qualify_runtime_receipt(external['path'],external['sha256'])==
        (external,state['auxiliary_directory_identities'],state['production_jar']),'Exact current R10 direct artifacts differ')
    require(state['production_jar']==dict(path=str(ROOT/'build/libs/shaderlod-0.1.0-dev.jar'),sha256=shared.JAR_SHA256),'Current coherent e86 main JAR differs')
    source=prep.qualify_archive();source['complete_member_map_sha256']=quality.matrix.canonical_options_hash(source['complete_member_sha256'])
    require(source==state['source_qualification'],'Exact e27 source freeze/private533 archive differs')
    evidence=state['driver_provenance']
    require(prep.qualify_driver_provenance(evidence['review_path'],evidence['review_sha256'])==evidence,'Actual two-driver component prerequisite differs')
    canonical=state['canonical_source_qualification'];native=canonical['effective_option_maps']['native']
    for name,value in canonical['source_files_sha256'].items():require(prep.file_hash(Path(name))==value,'Canonical source drift: '+name)
    require(state['source_world_sha256']==quality.WORLD_SHA256['coastalwater']and
        prep.world_fingerprint(quality.canonical.SOURCE/'saves'/prep.WORLD)==state['source_world_sha256'],'Canonical stopped source world differs')
    require(state['canonical_rain_control']==driver.rain.advisor.rain_control(state['canonical_clear_control']),'Canonical matched rain control differs')
    item=state['complete_option_inventory'];require(prep.file_hash(Path(item['path']))==item['sha256'],'Complete private Iris inventory differs')
    inventory=json.loads(Path(item['path']).read_text());driver.validate_maps(native,inventory['options'])
    require(inventory['option_count']==367 and inventory['options']==source['reviewed_full_inventory']['options'],'Same complete367 map differs from reviewed source')
    for name,value in inventory['parser_dependency_sha256'].items():require(prep.file_hash(Path(name))==value,'Pinned actual Iris dependency differs')
    require(prep.file_hash(Path(inventory['log']))==inventory['log_sha256'],'Actual Iris discovery log differs')
    rows=state['launch_plan'];require([r['arm']for r in rows]==list(ORDER),'Missing/reordered OFF/ON/OFF arms')
    require(prep.validate_pairs(rows)==state['prepared_pairs_comparison'],'Complete main-enabled-only prepared controls differ')
    for row,enabled in zip(rows,ENABLED):
        profile=Path(row['profile']);require(profile==prep.OUTPUT/row['arm']/'control'and
            row['command']==prep.command(profile)==rain.prep.command(profile),'Exact owned profile/shared rainy cost argv differs')
        require(type(row['expected']['global_enabled'])is bool and row['expected']['global_enabled']is enabled,
            'Expected typed main/global control differs')
        require(row['expected']['expected_effective_options']==inventory['options']and
            row['expected']['expected_effective_options_sha256']==inventory['sha256'],'Full same367 row options differ')
        require(row['inputs']['control']==state['canonical_rain_control']|{'enabled':enabled},'Complete main-enabled-only control differs')
        manifest=json.loads((profile.parent/'workload-manifest.json').read_text());prep.validate_manifest(manifest,native)
        require(manifest['source_qualification']==source and manifest['experiment_arms']['control']==row['expected'],'Complete private source/model manifest differs')
        for name,value in row['prepared_profile']['parent_files_sha256'].items():require(prep.file_hash(Path(name))==value,'Frozen parent recipe differs')
        require(json.loads((profile/'workload-request.json').read_text())==row['request'],'Frozen actual request differs')
    return external['receipt']


def cross_arm(arms,receipt):
    require([r.get('arm')for r in arms]==list(ORDER),'Need three qualified fixedgraph OFF/ON/OFF arms')
    reference=arms[0];settings=[];controls=[]
    for row,enabled in zip(arms,ENABLED):
        require(row.get('status')==PASS and row.get('global_enabled')is enabled,'Unqualified/mislabeled global arm')
        for key in ('window_reference','loaded_mods','source_archive_sha256','full_options','rain_qualification','input_scalar_signature'):
            require(row[key]==reference[key],'Complete cross-arm fixedgraph input/control differs: '+key)
        require(row['hardware_context']['typed']==reference['hardware_context']['typed'],'Typed actual hardware/timer payload differs')
        normalized=main_enabled_signature(row['complete_settings'],enabled)
        for base in ('shaderlod-client','shaderlod-effects-client'):normalized.pop('config/'+base+'-1.toml.bak',None)
        settings.append(normalized)
        control=copy.deepcopy(row['raw_control']);require(type(control.get('enabled'))is bool and control['enabled']is enabled,
            'Actual complete parent control enabled differs')
        del control['enabled'];controls.append(control)
    require(all(s==settings[0]for s in settings),'Complete captured settings differ beyond lod.enabled')
    require(all(s==controls[0]for s in controls),'Complete parent controls differ beyond enabled')
    return dict(screen=prep.screen([r['frame_mean_ms']for r in arms],[r['gpu_median_ms']for r in arms]),
        captured_settings_signature_sha256=quality.matrix.canonical_options_hash(settings[0]),
        complete_scalar_signature_sha256=quality.matrix.canonical_options_hash(reference['input_scalar_signature']),
        artifact_compatibility=shared.artifact_compatibility(arms,receipt),
        limits='Same private-enabled fixed VLtrue graph, only ordinary core/global main enabled changes. Deliberate missing fog; no original/default-weather/mod/quality gain or strict additive C0 loop-time ceiling. Branch/compiler/register/global overhead can affect both arms. Actual C0 stage/neutral RT/retired work unobserved by cost recipe.')


def analyze(preparation_path,pin,preflight_path,logs,arm=None):
    state=json.loads(Path(preparation_path).read_text());receipt=qualify_frozen_preparation(state,preparation_path,pin)
    preflight=qualify_preflight(preflight_path,preparation_path,pin)
    selected=[r for r in state['launch_plan']if arm is None or r['arm']==arm];require(selected,'Unknown arm')
    result=EVIDENCE|dict(schema=1,status='FAILED',
        preparation=dict(path=str(Path(preparation_path).resolve()),sha256=pin),
        preflight=dict(path=str(Path(preflight_path).resolve()),sha256=prep.file_hash(Path(preflight_path))),
        analyzer_source_files_sha256=source_pins(),arms=[],failures=[])
    for row in selected:
        log=Path(logs)/row['arm'];paths=dict(report=Path(row['profile'])/'workload-measurement.json',
            initial=log/'initial-process.json',terminal=log/'terminal-process.json',console=log/'console.log')
        entry=dict(arm=row['arm'],raw_inputs_sha256={str(p):prep.file_hash(p)for p in paths.values()if p.is_file()})
        try:
            launch=json.loads(paths['initial'].read_text())
            require(launch.get('preparation')==str(Path(preparation_path).resolve())and launch.get('preparation_sha256')==pin,'Birth preparation provenance differs')
            require(launch['started_ns']>preflight['verified_ns'],'Standalone unused-profile preflight must precede actual birth')
            entry.update(validate_arm(json.loads(paths['report'].read_text()),row,state,launch,
                json.loads(paths['terminal'].read_text()),paths['console'].read_text(),paths['report']))
        except(OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile)as error:
            entry.update(status='FAILED',reason=str(error));result['failures'].append(dict(arm=row['arm'],reason=str(error)))
        result['arms'].append(entry)
        if result['failures']:break
    if not result['failures']:
        if arm is not None:result['status']='PASS single actual fixedgraph air-fog cost arm; bracket pending'
        else:
            try:
                result['ablation_diagnostic']=cross_arm(result['arms'],receipt)
                result['status']='QUALIFIED actual fixedgraph air-fog components; inspect ablation screen and raw inventory distinctions'
            except(ValueError,KeyError,TypeError,zipfile.BadZipFile)as error:result['failures'].append(dict(cross_arm=str(error)))
    require(source_pins()==result['analyzer_source_files_sha256'],'Analyzer source changed during analysis')
    result['limitations']='Single serial fixedgraph private global OFF/ON/OFF removal calibration; no quality/mod/original/default-weather/repeated gain or strict body-cost upper bound. Driver typed core2/global/probe/PNG smoke is separate from actual C0 source-stage/neutral RT/retired branch proof. Cost probes OFF. CPU rain/wetness getters are not GL coefficients/biome/step eligibility. Root separately confirms quiet windows/final PNG inspection. Dropped-query counters/actual runtime FOV/camera type/broader loaded semantic config unexported. Raw failures and exact JarJar selection distinctions unchanged.'
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preparation',type=Path,default=prep.OUTPUT/prep.MARKER)
    parser.add_argument('--preparation-sha256',required=True);parser.add_argument('--preflight',type=Path,required=True)
    parser.add_argument('--logs',type=Path,required=True);parser.add_argument('--arm',choices=ORDER);parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    try:result=analyze(args.preparation,args.preparation_sha256,args.preflight,args.logs,args.arm)
    except(OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile)as error:
        result=EVIDENCE|dict(status='FAILED before actual fixedgraph air-fog arm qualification',failures=[str(error)],analyzer_source_files_sha256=source_pins())
    payload=json.dumps(result,indent=2,sort_keys=True)+'\n'
    if args.output:
        require(not args.output.exists(),'Preserve earlier derived output; use fresh filename')
        require(args.output.resolve().is_relative_to(ROOT/'build')or args.output.resolve().is_relative_to(ROOT/'dev/logs'),'Derived output must remain ignored')
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(payload)
    else:print(payload,end='')
    if result['status'].startswith('FAILED')or result.get('failures'):sys.exit(1)


if __name__=='__main__':main()
