#!/usr/bin/env python3
"""Strict actual original/FXAA-off/original native Workload cost qualification."""
from __future__ import annotations
import argparse
import copy
import hashlib
import io
import json
import math
from pathlib import Path
import re
import sys
import zipfile

import prepare_photon_fxaa_cost as prep
import prepare_photon_fxaa_quality_r2 as quality
import analyze_photon_pixel_forest_cost as producer

ROOT=prep.ROOT
actual=producer.actual
require=actual.require
ORDER=('original-before','fxaa-off','original-after')
MODES=('original','fxaa-off','original')
SCENES=quality.SCENES
PASS='PASS actual Workload native FXAA cost components'
RECEIPT_SHA256='c6ede25dec54edd0b3e9f85b02481dcf4d860419620dad6c18a6ddbcb75e6e06'
JAR_SHA256='e86b77eb478c5bbcae9f91fb14cf1712eb9b9fcb93d760a104eeb8245072b864'
EVIDENCE=dict(performanceEvidence=False,adapter_gain=False,repeated_gain=False,
    quality='sparse-gross-rejection-only',motion='unaccepted-general-motion',diagnostic_only=True,
    effective_pass_absence='unobserved',evidence_class='native-stock-FXAA-cost-screen')


def source_pins():
    paths=[ROOT/'scripts/analyze_photon_fxaa_cost.py',ROOT/'tests/test_photon_fxaa_cost_analysis.py']
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            path=Path(name).resolve()
            if path.suffix=='.py' and path.is_relative_to(ROOT/'scripts'):paths.append(path)
    return {str(p):prep.file_hash(p)for p in sorted(set(paths))}


def qualify_preflight(path,preparation_path,pin):
    record = json.loads(Path(path).read_text())
    require(record.get('argv')==['python3','scripts/prepare_photon_fxaa_cost.py','--verify',str(Path(preparation_path).resolve())]
        and type(record.get('returncode')) is int and record['returncode']==0,'Standalone fresh prep verify invocation failed/differs')
    require(record.get('preparation_sha256')==pin,'Preflight preparation hash differs')
    output = json.loads(record.get('stdout',''))
    require(output.get('status')=='verified-six-current-R10-unused-FXAA-cost-controls'
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



def qualify_frozen_preparation(state,path,pin):
    require(re.fullmatch('[0-9a-f]{64}',pin or '') and prep.file_hash(Path(path))==pin,'Explicit fresh preparation hash differs')
    require(state.get('status')==prep.FINAL,'Incomplete source preparation')
    for field in ('maintained_sources_sha256','runtime_sources_sha256'):
        for name,value in state[field].items():require(prep.file_hash(Path(name))==value,'Frozen source drift: '+name)
    require(quality.baseline.control_prep.current_runtime_sources()==state['runtime_sources_sha256'],'Complete current runtime source inventory differs')
    require(quality.matrix.development_fingerprints()==state['development_runtime_inputs'],'Current compiled/resource roots differ')
    external=state['independent_artifact_receipt']
    require(external['sha256']==RECEIPT_SHA256,'Current R10 receipt pin differs')
    require(prep.qualify_runtime_receipt(external['path'],external['sha256'])==
        (external,state['auxiliary_directory_identities'],state['production_jar']),'Current exact R10 direct receipt differs')
    require(state['production_jar']==dict(path=str(ROOT/'build/libs/shaderlod-0.1.0-dev.jar'),sha256=JAR_SHA256),'Current main e86 JAR differs')
    require(prep.qualify_quality_provenance()==state['quality_provenance'],'Sparse quality prerequisite provenance differs')
    item=state['complete_option_inventory'];require(prep.file_hash(Path(item['path']))==item['sha256'],'Complete Iris discovery inventory drift')
    inventories=json.loads(Path(item['path']).read_text())
    rows=state['launch_plan']
    require([(r['scene'],r['arm'],r['mode'])for r in rows]==[(s,a,m)for s in SCENES for a,m in zip(ORDER,MODES)],'Six-arm scene/order/mode drift')
    require(prep.validate_pairs(rows)==state['prepared_pairs_comparison'],'Complete prepared settings comparison differs')
    for scene in SCENES:
        source=state['scene_qualifications'][scene]
        require(source['source_world_sha256']==quality.WORLD_SHA256[scene],'Scene source world differs')
        for name,value in source['source_files_sha256'].items():require(prep.file_hash(Path(name))==value,'Canonical source drift: '+name)
        require(prep.world_fingerprint(quality.copies.DEFAULT_SOURCES[scene]/'saves'/prep.WORLD)==quality.WORLD_SHA256[scene],'Canonical stopped scene world differs')
        maps=quality.option_maps(scene,inventories[scene]['original']['options'])
        for row in [r for r in rows if r['scene']==scene]:
            profile=Path(row['profile']);mode=row['mode'];inventory=inventories[scene][mode]
            require(profile==prep.OUTPUT/scene/row['arm']/'control' and row['command']==prep.command(profile),'Owned profile/exact argv differs')
            require(inventory['option_count']==366 and inventory['options']==maps[mode]==row['expected']['expected_effective_options']
                and inventory['sha256']==quality.matrix.canonical_options_hash(maps[mode])==row['expected']['expected_effective_options_sha256'],'Full actual scene Iris map differs')
            for name,value in inventory['parser_dependency_sha256'].items():require(prep.file_hash(Path(name))==value,'Exact parser dependency drift')
            require(prep.file_hash(Path(inventory['log']))==inventory['log_sha256'],'Actual Iris discovery log differs')
            manifest=json.loads((profile.parent/'workload-manifest.json').read_text());prep.validate_manifest(manifest,maps['original'])
            require(manifest['source_qualification']==source and manifest['experiment_arms']['control']==row['expected'],'Complete manifest source/model differs')
            for name,value in row['prepared_profile']['parent_files_sha256'].items():require(prep.file_hash(Path(name))==value,'Frozen parent recipe differs')
            require(json.loads((profile/'workload-request.json').read_text())==row['request'],'Frozen actual request differs')
    return external['receipt']


def boundary(report,row):
    expected=row['expected'];scene=row['scene'];decoded=[];pose=quality.copies.POSES[scene]
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
        for key,target in ({k:v for k,v in pose.items()if k!='time'}|dict(rain=0.,thunder=0.)).items():
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


def launch_controls(row,launch):
    require(row['command']==prep.command(row['profile']),'Exact FXAA cost argv differs')
    values=actual.launch_controls(row,launch)
    for key,target in (('hideWorkloadGui','true'),('pixelHistoryCapture','false')):
        require(values.get('shaderlod.dev.'+key)==target,'Actual Java cost control differs: '+key)
    require(values.get('shaderlod.dev.pixelHistoryCaptureOnly','false')=='false'
        and values.get('shaderlod.dev.cameraQuality','')=='','Unexpected camera/history capture mode')
    return values


def manifest_serialization_proof():
    """Immutable bytecode from the actual Java birth's pinned Gson dependency."""
    path=ROOT/'build/fxaa-cost-analysis-r2/gson-source-proof.json'
    require(prep.file_hash(path)=='9ae6c2533189019222c4cad5d39ee09ae2c2ef1b3401e75d7b55c191f2356d17','Pinned Gson omission source proof differs')
    proof=json.loads(path.read_text())
    require(proof['jar']['sha256']=='4241c14a7727c34feea6507ec801318a3d4a90f070e4525681079fb94ee4c593', 'Actual Gson2.10.1 dependency pin differs')
    for item in (proof['jar'],proof['launch_receipt'],proof['workload_source'],proof['workload_class']):
        require(prep.file_hash(Path(item['path']))==item['sha256'],'Current serialization proof input differs: '+item['path'])
    for item in proof['disassembly']:
        require(item['returncode']==0 and prep.file_hash(Path(item['stdout_path']))==item['stdout_sha256'],'Pinned serialization disassembly differs')
    return dict(path=str(path),sha256=prep.file_hash(path),jar=proof['jar'],workload_source=proof['workload_source'],
        source_behavior='WorkloadSession parses the frozen manifest into a JsonObject and writes through GsonBuilder without serializeNulls; the exact JsonElement adapter calls nullValue and JsonWriter discards its deferred object name when serializeNulls=false.')


def qualify_actual_manifest(actual_manifest,row):
    expected_path=Path(row['profile']).parent/'workload-manifest.json'
    expected=json.loads(expected_path.read_text())
    if actual_manifest==expected:
        return dict(status='EXACT_ACTUAL_FROZEN_MANIFEST',qualified_omissions=[])
    require(isinstance(actual_manifest,dict),'Actual frozen manifest missing/not object')
    source=expected.get('source_qualification',{});observed=actual_manifest.get('source_qualification',{})
    require(row.get('scene')==expected.get('scene')==actual_manifest.get('scene')=='coastalwater'
        and isinstance(source,dict)and isinstance(observed,dict)
        and 'original_source_dh'in source and source['original_source_dh']is None
        and 'original_source_dh'not in observed,'Actual frozen manifest differs; no exact permitted null omission')
    # Project only the known nullable provenance metadata in a copy of frozen
    # expected input. Actual report, manifest and all other fields stay untouched.
    projected=copy.deepcopy(expected);del projected['source_qualification']['original_source_dh']
    require(actual_manifest==projected,'Actual frozen manifest differs beyond exact coastal nullable DH metadata omission')
    return dict(status='QUALIFIED_EXACT_COASTAL_GSON_NULL_METADATA_OMISSION',
        qualified_omissions=['$.source_qualification.original_source_dh'],expected_value=None,
        expected_frozen_manifest_sha256=prep.file_hash(expected_path),source_proof=manifest_serialization_proof(),
        limitation='Only known static coastal provenance metadata; actual DH/mod inventory gates stay unchanged. No global null removal or unknown field/setting omission.')


def validate_arm(report,row,preparation,launch,terminal,console,report_path=None):
    require(report.get('schema')==2 and report.get('request')==row['request'],'Actual Workload schema/request differs')
    require(row['request'].get('gpu_queries')is True and row['request'].get('cpu_timing')is True,'Full cost request differs')
    quality.matrix.validate_report(report,row['request'],row['inputs'],launch['started_ns'],report_path)
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
    manifest_qualification=qualify_actual_manifest(report.get('workload-manifest.json'),row)
    result=producer.timing(report)
    result.update(scene=row['scene'],arm=row['arm'],mode=row['mode'],status=PASS,window_reference=actual.window(report),
        hardware_context=producer.hardware_context(console),gui=gui,temporal=temporal,
        complete_settings=qualified['settings'],actual_configurations=qualified['actual_configuration_files'],
        qualified_backups=qualified['qualified_exact_first_backups'],loaded_mods=report['loaded_mods'],actual_inventory=inventory,
        raw_control=report['control.json'],full_options=report['effective_shader_options'],source_archive_sha256=expected_archive(row),
        terminal=producer.terminal_controls(row,launch,terminal),manifest_qualification=manifest_qualification,
        final_still=producer.screenshot(Path(row['profile'])/'screenshots/workload-final.png',launch['started_ns']))
    return result


def expected_archive(row):
    require(row['expected']['archive_sha256']==quality.canonical.ORIGINAL_SHA256,'Unexpected modified shader source')
    return row['expected']['archive_sha256']


def artifact_compatibility(arms,receipt):
    """Exact raw receipts remain; no unknown parent, member, identity or metadata waiver."""
    artifacts=quality.baseline.cost.artifacts;proofs=[]
    for arm in arms:artifacts.validate_inventory_against_receipt(arm['actual_inventory'],receipt['loaded_artifact_inventory'],arm['loaded_mods'])
    for group in receipt['loaded_artifact_inventory']:
        candidates=artifacts.inventory_group_alternatives(group);chosen=[]
        for arm in arms:
            matches=[r for r in arm['actual_inventory']if r['mods']==candidates[0]['mods']]
            require(len(matches)==1 and any(artifacts.artifact_locator_matches(matches[0],c)for c in candidates),'Unknown actual receipt selection')
            chosen.append(dict(arm=arm['arm'],raw=matches[0]))
        if candidates[0]['filesystem_provider']!='jij':
            require(all(x['raw']==chosen[0]['raw']for x in chosen),'Ordinary actual root differs across arms');continue
        member_maps=[]
        for candidate in candidates:
            uri=artifacts.canonical_nested_uri(candidate['uri'],expected=True);parent,member=uri[4:].split('!/',1)
            with zipfile.ZipFile(parent)as z:data=z.read(member)
            require(hashlib.sha256(data).hexdigest()==candidate['identity'],'Frozen nested alternative bytes differ')
            with zipfile.ZipFile(io.BytesIO(data))as z:
                names=[n for n in z.namelist()if not n.endswith('/')];require(len(names)==len(set(names)),'Duplicate nested member')
                member_maps.append({n:hashlib.sha256(z.read(n)).hexdigest()for n in names})
        different={n:[m.get(n)for m in member_maps]for n in set().union(*member_maps)if len({m.get(n)for m in member_maps})>1}
        if candidates[0]['mods']=={'fabric_api_base':'0.4.42+d1308ded19'}:
            require(different=={'META-INF/neoforge.mods.toml':[
                '5f6be55b97d223bc9a95792026a8fecad1215710a9c8e94247ad3fe7e1d9bc9e',
                '249046698cf78d7d6ccdd3d5c8c1e837a728fcfc822d0fedfca2e82abaf36d4b']},'Exact pinned API Base metadata alternative differs')
        else:require(different=={},'Other nested runtime member/resource differs')
        proofs.append(dict(mods=candidates[0]['mods'],frozen_candidates=candidates,raw_selections=chosen,
            complete_member_maps=member_maps,exact_differing_members=different,
            unchanged_class_members=len([n for n in member_maps[0]if n.endswith('.class')and n not in different])))
    same=all(arm['actual_inventory']==arms[0]['actual_inventory']for arm in arms)
    return dict(status='EXACT_R10_COMPONENT_ALTERNATIVES_QUALIFIED',strict_raw_selected_inventory_equality=same,
        strict_cross_failure=None if same else 'Raw selected inventory equality differs; not relabeled as PASS. Exact independently pinned alternatives and metadata/loader confound retained.',
        nested_member_proofs=proofs,limitation='Identical runtime class/resources plus exact metadata alternatives do not establish causal end-to-end timing equivalence.')


def screen(frame_ms,gpu_ms):
    require(len(frame_ms)==len(gpu_ms)==3 and all(type(v)in(int,float)and math.isfinite(v)and v>0 for v in (*frame_ms,*gpu_ms)),'Need three positive finite actual metrics')
    drift=dict(frame=frame_ms[2]/frame_ms[0]-1,gpu=gpu_ms[2]/gpu_ms[0]-1)
    compare=dict(fps_gain_vs_each_original=[frame_ms[i]/frame_ms[1]-1 for i in (0,2)],
        frame_reduction_vs_each_original=[1-frame_ms[1]/frame_ms[i]for i in (0,2)],
        gpu_reduction_vs_each_original=[1-gpu_ms[1]/gpu_ms[i]for i in (0,2)])
    verdict=('inconclusive-original-control-drift'if max(abs(v)for v in drift.values())>.10+1e-12 else
        'stop-no-useful-original-signal'if min(compare['frame_reduction_vs_each_original'])<=.05+1e-12
        or min(compare['gpu_reduction_vs_each_original'])<=0 else 'promising-native-stock-FXAA-calibration-only')
    return EVIDENCE|dict(verdict=verdict,original_control_drift=drift,fxaa_off_vs_each_original=compare,
        limitation='One serial stock diagnostic, no repeated/quality-equivalent mod gain or per-pass attribution.')


def cross_scene(arms,scene,receipt):
    require(scene in SCENES and [r.get('arm')for r in arms]==list(ORDER),'Need three qualified scene arms in exact order')
    reference=arms[0];settings=[];maps=quality.option_maps(scene,reference['full_options'])
    for row,mode in zip(arms,MODES):
        require(row.get('status')==PASS and row.get('scene')==scene and row.get('mode')==mode,'Unqualified/mislabeled scene arm')
        for key in ('window_reference','loaded_mods','raw_control','source_archive_sha256'):
            require(row[key]==reference[key],'Scene exact controls differ: '+key)
        require(row['hardware_context']['typed']==reference['hardware_context']['typed'],'Scene actual typed hardware/timer payload differs')
        require(row['full_options']==maps[mode],'Full scene map differs beyond FXAA')
        normalized=copy.deepcopy(row['complete_settings'])
        for base in ('shaderlod-client','shaderlod-effects-client'):normalized.pop('config/'+base+'-1.toml.bak',None)
        settings.append(normalized)
    require(all(s==settings[0]for s in settings),'Complete other live scene settings differ')
    compatibility=artifact_compatibility(arms,receipt)
    return dict(screen=screen([r['frame_mean_ms']for r in arms],[r['gpu_median_ms']for r in arms]),artifact_compatibility=compatibility)


def analyze(preparation_path,pin,preflight_path,logs,scene,arm=None):
    require(scene in SCENES,'Unknown scene')
    state=json.loads(Path(preparation_path).read_text());receipt=qualify_frozen_preparation(state,preparation_path,pin)
    preflight=qualify_preflight(preflight_path,preparation_path,pin)
    selected=[r for r in state['launch_plan']if r['scene']==scene and (arm is None or r['arm']==arm)];require(selected,'Unknown arm')
    result=EVIDENCE|dict(schema=1,status='FAILED',scene=scene,
        preparation=dict(path=str(Path(preparation_path).resolve()),sha256=pin),
        preflight=dict(path=str(Path(preflight_path).resolve()),sha256=prep.file_hash(Path(preflight_path))),
        analyzer_source_files_sha256=source_pins(),arms=[],failures=[])
    for row in selected:
        log=Path(logs)/scene/row['arm'];paths=dict(report=Path(row['profile'])/'workload-measurement.json',
            initial=log/'initial-process.json',terminal=log/'terminal-process.json',console=log/'console.log')
        entry=dict(scene=scene,arm=row['arm'],raw_inputs_sha256={str(p):prep.file_hash(p)for p in paths.values()if p.is_file()})
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
        if arm is not None:result['status']='PASS single actual FXAA cost arm; scene bracket pending'
        else:
            try:
                result['scene_diagnostic']=cross_scene(result['arms'],scene,receipt)
                result['status']='QUALIFIED actual scene components; inspect stock diagnostic and retained raw inventory differences'
            except(ValueError,KeyError,TypeError,zipfile.BadZipFile)as error:result['failures'].append(dict(cross_scene=str(error)))
    require(source_pins()==result['analyzer_source_files_sha256'],'Analyzer source changed during analysis')
    result['limitations']='Single-scene serial screen; no general motion acceptance, repeated/mod gain, GPU pass absence or attribution. Root separately confirms quiet interval/new still inspection. Exact JarJar metadata alternatives retain causal limits. Actual runtime FOV/camera type/effect consumption and dropped-query counters unexported. Raw failures unchanged.'
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preparation',type=Path,default=prep.OUTPUT/prep.MARKER)
    parser.add_argument('--preparation-sha256',required=True);parser.add_argument('--preflight',type=Path,required=True)
    parser.add_argument('--logs',type=Path,required=True);parser.add_argument('--scene',choices=SCENES,required=True)
    parser.add_argument('--arm',choices=ORDER);parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    try:result=analyze(args.preparation,args.preparation_sha256,args.preflight,args.logs,args.scene,args.arm)
    except(OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile)as error:
        result=EVIDENCE|dict(status='FAILED before actual arm qualification',failures=[str(error)],analyzer_source_files_sha256=source_pins())
    payload=json.dumps(result,indent=2,sort_keys=True)+'\n'
    if args.output:
        require(not args.output.exists(),'Preserve earlier derived output; use fresh filename')
        require(args.output.resolve().is_relative_to(ROOT/'build')or args.output.resolve().is_relative_to(ROOT/'dev/logs'),'Derived output must remain ignored')
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(payload)
    else:print(payload,end='')
    if result['status'].startswith('FAILED')or result.get('failures'):sys.exit(1)


if __name__=='__main__':main()
