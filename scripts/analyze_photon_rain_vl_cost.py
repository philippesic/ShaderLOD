#!/usr/bin/env python3
"""Strict actual rainy-coast original VL-on/off/on maximum-removal cost reader."""
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

import assemble_photon_rain_vl_cost as prep
import analyze_photon_fxaa_cost as shared

ROOT=prep.ROOT
advisor=prep.advisor
quality=prep.cost.quality
actual=shared.actual
producer=shared.producer
require=actual.require
integer=actual.integer
vm_arguments=actual.vm_arguments
ORDER,MODES=prep.ORDER,prep.MODES
PASS='PASS actual Workload rainy-coast VL ceiling components'
PREPARATION_SHA256='c3004368d4ef353ccef013ad45130fe558236404bbb139361509d10b01c922d9'
EVIDENCE=dict(advisor.EVIDENCE)|dict(repeated_gain=False,effective_pass_absence='unobserved',
    eligibility='unmeasured',actual_fog_coefficients='unmeasured',actual_biome='unmeasured')


def source_pins():
    paths=[ROOT/'scripts/analyze_photon_rain_vl_cost.py',ROOT/'tests/test_photon_rain_vl_cost_analysis.py']
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            path=Path(name).resolve()
            if path.suffix=='.py'and path.is_relative_to(ROOT/'scripts'):paths.append(path)
    return {str(p):prep.file_hash(p)for p in sorted(set(paths))}


def qualify_preflight(path,preparation_path,pin):
    record = json.loads(Path(path).read_text())
    require(record.get('argv')==['python3','scripts/assemble_photon_rain_vl_cost.py','--verify',str(Path(preparation_path).resolve())]
        and type(record.get('returncode')) is int and record['returncode']==0,'Standalone fresh prep verify invocation failed/differs')
    require(record.get('preparation_sha256')==pin,'Preflight preparation hash differs')
    output = json.loads(record.get('stdout',''))
    require(output.get('status')=='verified-three-current-R10-unused-rain-VL-ceiling-controls'
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



def boundary(report,row):
    expected=row['expected'];scene=row['scene'];decoded=[];pose=quality.copies.POSES[scene]
    require(scene=='coastalwater','Only pinned coastal rain scene is qualified')
    require(report.get('effective_shader_options')==expected['expected_effective_options']
        and report.get('effective_shader_options_sha256')==expected['expected_effective_options_sha256'],'Report complete map differs')
    for name in ('input_state_start','input_state_end'):
        state=report.get(name,{})
        require(state.get('effective_shader_options')==expected['expected_effective_options']
            and state.get('effective_shader_options_sha256')==expected['expected_effective_options_sha256'],'Boundary complete map differs')
        require(state.get('shader_pack')==expected['archive'] and state.get('shaders_in_use') is True
            and state.get('fallback') is False and state.get('dimension')=='minecraft:overworld','Selected shader/pipeline/dimension differs')
        for key,target in dict(framebuffer_width=2976,framebuffer_height=1672,requested_chunks=24,effective_chunks=24,day_time=pose['time']).items():
            require(type(state.get(key))is int and state[key]==target,'Fixed scene scalar differs: '+key)
        for key,target in ({k:v for k,v in pose.items()if k!='time'}|dict(rain=1.,thunder=0.)).items():
            value=state.get(key)
            require(type(value)in(int,float)and math.isfinite(value)and abs(value-target)<=.002,'Fixed scene camera/weather differs: '+key)
        require(state.get('lod_settings')==dict(enabled=True,near=32.,far=96.,transition=8.,debug=False)
            and state.get('lod_preset')=='PERFORMANCE' and state.get('terrain_active') is False,'Loaded core/terrain controls differ')
        qualified=quality.forest.gui.qualify_configuration(state.get('configuration_files',{}),row)
        for config in ('config/shaderlod-client.toml','config/shaderlod-effects-client.toml'):
            require(qualified['settings'][config]==row['prepared_writable_inventory']['settings'][config],'Complete main/effects configuration differs')
        quality.forest.validate_effects(state['configuration_files']['files']['config/shaderlod-effects-client.toml']['utf8'])
        for key,value in state.items():
            if key.startswith('config/')and key.endswith('_sha256'):
                require(state['configuration_files']['files'].get(key[:-7],{}).get('sha256')==value,'Raw config alias differs')
        decoded.append(qualified)
    require(report['input_state_start']==report['input_state_end']and decoded[0]==decoded[1],'Full captured inputs/configuration changed')
    archive=Path(row['profile'])/'shaderpacks'/expected['archive']
    require(prep.file_hash(archive)==expected['archive_sha256']==quality.canonical.ORIGINAL_SHA256,'Actual original archive differs')
    with zipfile.ZipFile(archive)as z:
        names=[n for n in z.namelist()if not n.endswith('/')]
        require(len(names)==len(set(names))==531,'Original 531-member ZIP differs/duplicates')
        members={n:hashlib.sha256(z.read(n)).hexdigest()for n in names}
    manifest=report.get('workload-manifest.json',{})
    require(members==manifest['source_qualification']['complete_member_sha256'],'Complete original member map differs')
    require(members.get('LICENSE')==manifest['source_qualification']['license_sha256'],'Original LICENSE differs')
    return decoded[0]


def _birth_and_controls(row,launch):
    require(launch.get('owner')=='root' and launch.get('profile')==row['profile'] and launch.get('command')==row['command'], 'Proven root launch/profile/argv differs')
    integer(launch.get('started_ns'),'launch timestamp',1)
    # Accept the two real root receipt layouts, not fabricated benchmark metadata.
    if 'java_process_ownership' in launch:
        observed=list(launch['java_process_ownership'].values())
        require(len(observed)==1,'Expected exactly one owned Java birth record')
        java=observed[0]; identity=java.get('identity',''); cwd=java.get('cwd','')
        pid=integer(java.get('pid'),'Java PID',1)
        if not cwd:
            # Actual ownership helper exports selected lsof evidence, not a
            # fabricated flat cwd. Require its exact query, selection and path.
            require(java.get('status')=='owned' and java.get('profile')==row['profile']
                and java.get('association')=='cwd','Owned Java profile association differs')
            matches=[e for e in java.get('evidence',[]) if e.get('command')==
                ['/usr/sbin/lsof','-a','-p',str(pid),'-d','cwd','-Fn']
                and e.get('returncode')==0 and e.get('selected_pids')==[str(pid)]
                and row['profile'] in e.get('paths',[])]
            require(len(matches)==1,'Exact selected Java cwd evidence missing/ambiguous')
            cwd=row['profile']
    else:
        integer(launch.get('java_pid'),'Java PID',1);identity=launch.get('java_identity','');cwd=launch.get('java_cwd','')
    require('net.neoforged.devlaunch.Main' in identity and row['profile'] in cwd,'Java birth/cwd profile association unproved')
    snapshot=launch.get('java_vm_args')
    properties=vm_arguments(snapshot)
    require('@'+snapshot['path'] in identity,'Java birth command does not consume captured VM argument file')
    required=dict(performanceStack='minimal',includeDh='false',benchmarkWindowScale='2',comparisonFullscreen='false',
        fixedWetness='1',gpuTiming='true',gpuSampleInterval='5',initialWarmup='60',measurementSeconds='20',
        cpuTiming='true',measureWorkload='true',measureOnly='true')
    # These are forwarded in current build.gradle. Other OFF flags use hasProperty
    # aliases (smoke/benchmark/camera) and are guarded by exact Gradle argv + absent
    # runtime aliases, not an invented -D...=false forwarding claim.
    forwarded_off=('gpuPassTiming','cpuLayerTiming','gpuAttribution','cloudSamplerProbe','workTrace','workTraceOnly',
        'spatialLayoutProbe','spatialLayoutOnly','pixelCoverage','dhDrawEvidence','waterSceneSurvey',
        'shadowObserve','visibilityLists','shadowCoherenceOracle')
    required.update({k:'false' for k in forwarded_off})
    for key,target in required.items(): require(properties.get('shaderlod.dev.'+key)==target,'Actual Java property differs/missing: '+key)
    for key in ('benchmark','benchmarkOnly','smoke','cameraQualityOnly','pixelCoverageOnly'):
        require(properties.get('shaderlod.dev.'+key,'false')=='false','Unexpected Java diagnostic alias: '+key)
    return properties


def validate_arm(report,row,preparation,launch,terminal,console,report_path=None):
    require(report.get('schema')==2 and report.get('request')==row['request'],'Actual Workload schema/request differs')
    require(row['request'].get('gpu_queries')is True and row['request'].get('cpu_timing')is True,'Full cost request differs')
    quality.matrix.validate_report(report,row['request'],row['inputs'],launch['started_ns'],report_path)
    rain=prep.qualify_rain(report,row['request'])
    temporal=quality.matrix.validate_temporal_inputs(report,row['request'])
    properties=launch_controls(row,launch);gui=quality.forest.gui.qualify_gui_report(report,row)
    for key,value in report.get('runtime_properties',{}).items():require(properties.get(key)==value,'Actual Java/report property differs: '+key)
    require(report.get('runtime_mod_jars_sha256')=={},'Unexpected profile jar/DH')
    require(all(report.get(k)is False for k in ('shadow_culling_active','separate_shadow_lists_active','shadow_frustum_coherence_active')),'Unrelated culling active')
    policy=report.get('geometry_policy',{})
    require(policy.get('active')is False and policy.get('requestedChunks')==policy.get('effectiveChunks')==24,'Actual terrain policy differs')
    require(not any(k in report for k in ('pixel_coverage_evidence','work_trace_evidence','spatial_layout_evidence','pixel_history_evidence','water_scene_survey','dh_draw_evidence')),'Readback diagnostic contaminated cost')
    require(not (Path(row['profile'])/'logs/cloud-sampler-state.json').exists(),'Sampler probe artifact in timed profile')
    qualified=boundary(report,row)
    evidence=report.get('loaded_artifact_evidence',{});receipt=preparation['independent_artifact_receipt']['receipt']
    require(evidence.get('valid')is True and evidence.get('complete_mod_coverage')is True
        and evidence.get('complete_identities')is True and evidence.get('errors')==[],'Direct artifact evidence incomplete/error')
    require(evidence.get('loaded_mods')==report.get('loaded_mods')==receipt['expected_loaded_mods']
        and len(report['loaded_mods'])==10,'Exact ten loaded mods differ')
    inventory=report.get('loaded_artifact_inventory')
    require(isinstance(inventory,list)and len(inventory)==9 and inventory==evidence.get('loaded_artifact_inventory'),'Exact direct nine-root inventory differs')
    quality.baseline.cost.artifacts.validate_inventory_against_receipt(inventory,receipt['loaded_artifact_inventory'],report['loaded_mods'])
    require(report.get('control.json')==row['inputs']['control']and report.get('control_sha256')==row['inputs']['control_sha256'],'Actual complete control differs')
    manifest_qualification=shared.qualify_actual_manifest(report.get('workload-manifest.json'),row)
    result=producer.timing(report)
    result.update(scene=row['scene'],arm=row['arm'],mode=row['mode'],status=PASS,window_reference=actual.window(report),
        hardware_context=producer.hardware_context(console),gui=gui,temporal=temporal,rain_qualification=rain,
        complete_settings=qualified['settings'],actual_configurations=qualified['actual_configuration_files'],
        qualified_backups=qualified['qualified_exact_first_backups'],loaded_mods=report['loaded_mods'],actual_inventory=inventory,
        raw_control=report['control.json'],full_options=report['effective_shader_options'],source_archive_sha256=expected_archive(row),
        terminal=producer.terminal_controls(row,launch,terminal),manifest_qualification=manifest_qualification,
        final_still=producer.screenshot(Path(row['profile'])/'screenshots/workload-final.png',launch['started_ns']))
    return result


def expected_archive(row):
    require(row['expected']['archive_sha256']==quality.canonical.ORIGINAL_SHA256,'Unexpected modified shader source')
    return row['expected']['archive_sha256']


def qualify_frozen_preparation(state,path,pin):
    require(pin==PREPARATION_SHA256 and prep.file_hash(Path(path))==pin,'Exact frozen three-arm preparation hash differs')
    require(state.get('status')==prep.FINAL,'Incomplete rain ceiling preparation')
    require(prep.qualify_recipe()==state['frozen_advisor_recipe'],'Immutable source advisor recipe/freeze differs')
    for field in ('maintained_sources_sha256','runtime_sources_sha256'):
        for name,value in state[field].items():require(prep.file_hash(Path(name))==value,'Frozen source drift: '+name)
    require(prep.cost.baseline.control_prep.current_runtime_sources()==state['runtime_sources_sha256'],'Complete current runtime source inventory differs')
    require(prep.matrix.development_fingerprints()==state['development_runtime_inputs'],'Current compiled/resource roots differ')
    external=state['independent_artifact_receipt']
    require(external['sha256']==shared.RECEIPT_SHA256 and prep.qualify_runtime_receipt(external['path'],external['sha256'])==
        (external,state['auxiliary_directory_identities'],state['production_jar']),'Exact current R10 direct receipt differs')
    require(state['production_jar']==dict(path=str(ROOT/'build/libs/shaderlod-0.1.0-dev.jar'),sha256=shared.JAR_SHA256),'Current coherent main e86 JAR differs')
    source=state['source_qualification']
    require(state['source_world_sha256']==source['source_world_sha256']==quality.WORLD_SHA256['coastalwater'],'Canonical source world differs')
    for name,value in source['source_files_sha256'].items():require(prep.file_hash(Path(name))==value,'Canonical source file drift: '+name)
    require(prep.world_fingerprint(prep.cost.canonical.SOURCE/'saves'/prep.WORLD)==state['source_world_sha256'],'Current canonical clear source save differs')
    item=state['complete_option_inventory'];require(prep.file_hash(Path(item['path']))==item['sha256'],'Complete Iris discovery inventory differs')
    inventories=json.loads(Path(item['path']).read_text());maps=advisor.option_maps(inventories['vl-before']['options'])
    rows=state['launch_plan'];recipes=state['frozen_advisor_recipe']['source_recipe']['recipes']
    require([r['arm']for r in rows]==list(ORDER)and [r['mode']for r in rows]==list(MODES),'Missing/reordered actual rain arms')
    require(prep.validate_pairs(rows)==state['prepared_pairs_comparison'],'Complete prepared settings comparison differs')
    for row,recipe in zip(rows,recipes):
        profile=Path(row['profile']);arm=row['arm'];inventory=inventories[arm]
        require(row['scene']=='coastalwater'and profile==prep.OUTPUT/arm/'control'
            and row['command']==recipe['command']==prep.command(profile)and row['request']==recipe['request'],'Exact source recipe/profile/request/argv differs')
        require(inventory['option_count']==366 and inventory['options']==maps[arm]==row['expected']['expected_effective_options']==recipe['expected_effective_options']
            and inventory['sha256']==prep.matrix.canonical_options_hash(maps[arm])==row['expected']['expected_effective_options_sha256']==recipe['expected_effective_options_sha256'],'Complete actual only-VL option map differs')
        for name,value in inventory['parser_dependency_sha256'].items():require(prep.file_hash(Path(name))==value,'Pinned parser dependency differs')
        require(prep.file_hash(Path(inventory['log']))==inventory['log_sha256'],'Actual Iris discovery log differs')
        manifest=json.loads((profile.parent/'workload-manifest.json').read_text());prep.validate_manifest(manifest,maps['vl-before'])
        require(manifest['source_qualification']==source and manifest['experiment_arms']['control']==row['expected']
            and manifest['control']==recipe['control'],'Full exact source/model/rain recipe manifest differs')
        for name,value in row['prepared_profile']['parent_files_sha256'].items():require(prep.file_hash(Path(name))==value,'Frozen parent recipe differs')
        require(json.loads((profile/'workload-request.json').read_text())==row['request'],'Frozen actual request differs')
    return external['receipt']


def launch_controls(row,launch):
    require(row['command']==prep.command(row['profile']),'Exact rain/VL argv differs')
    values=_birth_and_controls(row,launch)
    for key,target in (('hideWorkloadGui','true'),('pixelHistoryCapture','false')):
        require(values.get('shaderlod.dev.'+key)==target,'Actual Java rain cost control differs: '+key)
    require(values.get('shaderlod.dev.pixelHistoryCaptureOnly','false')=='false'
        and values.get('shaderlod.dev.cameraQuality','')=='','Unexpected history/camera capture mode')
    return values


def cross_arm(arms,receipt):
    require([r.get('arm')for r in arms]==list(ORDER),'Need three qualified rainy-coast arms in exact order')
    reference=arms[0];maps=advisor.option_maps(reference['full_options']);settings=[]
    for row,mode in zip(arms,MODES):
        require(row.get('status')==PASS and row.get('scene')=='coastalwater'and row.get('mode')==mode,'Unqualified/mislabeled rain arm')
        for key in ('window_reference','loaded_mods','raw_control','source_archive_sha256','rain_qualification'):
            require(row[key]==reference[key],'Exact cross-arm scene/controls differ: '+key)
        require(row['hardware_context']['typed']==reference['hardware_context']['typed'],'Typed actual hardware/timer payload differs')
        require(row['full_options']==maps[row['arm']],'Full loaded map differs beyond VL')
        normalized=copy.deepcopy(row['complete_settings'])
        for base in ('shaderlod-client','shaderlod-effects-client'):normalized.pop('config/'+base+'-1.toml.bak',None)
        settings.append(normalized)
    require(all(s==settings[0]for s in settings),'Full other actual settings differ')
    return dict(screen=prep.screen([r['frame_mean_ms']for r in arms],[r['gpu_median_ms']for r in arms]),
        artifact_compatibility=shared.artifact_compatibility(arms,receipt),
        limits='Maximum-removal rainy-scene calibration; VL-off quality deliberately unaccepted, not a default-weather/mod gain. CPU rain/wetness sources do not prove GL fog coefficients, biome eligibility or executed tail steps.')


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
            require(launch['started_ns']>preflight['verified_ns'],'Standalone unused-profile preflight must precede birth')
            entry.update(validate_arm(json.loads(paths['report'].read_text()),row,state,launch,
                json.loads(paths['terminal'].read_text()),paths['console'].read_text(),paths['report']))
        except(OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile)as error:
            entry.update(status='FAILED',reason=str(error));result['failures'].append(dict(arm=row['arm'],reason=str(error)))
        result['arms'].append(entry)
        if result['failures']:break
    if not result['failures']:
        if arm is not None:result['status']='PASS single actual rain/VL ceiling arm; bracket pending'
        else:
            try:
                result['ceiling_diagnostic']=cross_arm(result['arms'],receipt)
                result['status']='QUALIFIED actual rainy-coast components; inspect maximum-removal diagnostic and raw inventory distinctions'
            except(ValueError,KeyError,TypeError,zipfile.BadZipFile)as error:result['failures'].append(dict(cross_arm=str(error)))
    require(source_pins()==result['analyzer_source_files_sha256'],'Analyzer source changed during analysis')
    result['limitations']='Single serial rainy native maximum-removal calibration only; no general/repeated/mod gain, quality acceptance, default-weather inference or per-pass attribution. Root separately confirms quiet windows. Actual GL rainStrength/fog palette/coefficients/biome/ray-step eligibility are unobserved; CPU rain getter and wetness supplier evidence are distinct. Known exact JarJar metadata alternatives retain causal limits. Dropped-query counters and actual runtime FOV/camera type unexported. Raw failures unchanged.'
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preparation',type=Path,default=prep.OUTPUT/prep.MARKER)
    parser.add_argument('--preparation-sha256',required=True);parser.add_argument('--preflight',type=Path,required=True)
    parser.add_argument('--logs',type=Path,required=True);parser.add_argument('--arm',choices=ORDER);parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    try:result=analyze(args.preparation,args.preparation_sha256,args.preflight,args.logs,args.arm)
    except(OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile)as error:
        result=EVIDENCE|dict(status='FAILED before actual rain arm qualification',failures=[str(error)],analyzer_source_files_sha256=source_pins())
    payload=json.dumps(result,indent=2,sort_keys=True)+'\n'
    if args.output:
        require(not args.output.exists(),'Preserve earlier derived output; use fresh filename')
        require(args.output.resolve().is_relative_to(ROOT/'build')or args.output.resolve().is_relative_to(ROOT/'dev/logs'),'Derived output must remain ignored')
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(payload)
    else:print(payload,end='')
    if result['status'].startswith('FAILED')or result.get('failures'):sys.exit(1)


if __name__=='__main__':main()
