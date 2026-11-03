#!/usr/bin/env python3
"""Qualify actual five-arm forest stock pixel-cost Workload reports; never launch."""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import sys
import struct
import zlib
import zipfile

import prepare_photon_pixel_forest_cost as prep
import analyze_photon_pixel_baseline as actual

ROOT = prep.ROOT
require = actual.require
ORDER = prep.ORDER
EVIDENCE = dict(performanceEvidence=False, adapter_gain=False, diagnostic_only=True,
    quality='gross-still-screen-only', motion='unverified', repeated_gain=False,
    evidence_class='stock-pixel-budget-forest-original-denominator-cost-screen')
PASS = 'PASS actual Workload forest cost components'
# Only HH:mm:ss varies. Logger/thread/category and payload grammar remain strict.
PREFIX = r'\[(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]\] \[Render thread/INFO\] '
GPU = re.compile(PREFIX + r'\[co\.pp\.sh\.de\.ti\.GpuTimers/\]: ShaderLOD GPU timers: '
    r'backend=(ELAPSED_ALTERNATING), timestampBits=(0), elapsedBits=(32), '
    r'GL=(4\.1 Metal - 91\.7), renderer=(Apple M3 Max), policy=(.+)')
CPU = re.compile(PREFIX + r'\[co\.pp\.sh\.de\.ti\.CpuTimers/\]: ShaderLOD CPU timers: '
    r'currentThreadCpuSupported=(true), layers=(false)')


def source_pins():
    paths = [ROOT/'scripts/analyze_photon_pixel_forest_cost.py', ROOT/'tests/test_photon_pixel_forest_cost_analysis.py']
    for module in tuple(sys.modules.values()):
        name = getattr(module,'__file__',None)
        if name:
            path = Path(name).resolve()
            if path.suffix == '.py' and path.is_relative_to(ROOT/'scripts'): paths.append(path)
    return {str(p):prep.file_hash(p) for p in sorted(set(paths))}


def hardware_context(console):
    lines = [line for line in console.splitlines() if 'ShaderLOD GPU timers:' in line or 'ShaderLOD CPU timers:' in line]
    require(len(lines)==2,'Expected exactly two actual timer context lines')
    result = {}
    for line in lines:
        gpu, cpu = GPU.fullmatch(line), CPU.fullmatch(line)
        if gpu:
            require('gpu' not in result and gpu[6]==actual.SAMPLING,'Duplicate/different GPU policy')
            result['gpu'] = dict(backend=gpu[1],timestampBits=int(gpu[2]),elapsedBits=int(gpu[3]),
                GL=gpu[4],renderer=gpu[5],policy=gpu[6])
        elif cpu:
            require('cpu' not in result,'Duplicate CPU context')
            result['cpu'] = dict(currentThreadCpuSupported=True,layers=False)
        else: raise ValueError('Unknown actual timer context prefix/payload: '+line)
    require(set(result)=={'cpu','gpu'},'Missing timer context')
    return dict(typed=result,raw_lines=lines,
        normalization='Only strict HH:mm:ss logging prefix differs; actual GpuTimers/CpuTimers payloads and logger/thread identity are typed and exact.')


def qualify_preflight(path,preparation_path,pin):
    record = json.loads(Path(path).read_text())
    require(record.get('argv')==['python3','scripts/prepare_photon_pixel_forest_cost.py','--verify',str(Path(preparation_path).resolve())]
        and type(record.get('returncode')) is int and record['returncode']==0,'Standalone fresh prep verify invocation failed/differs')
    require(record.get('preparation_sha256')==pin,'Preflight preparation hash differs')
    output = json.loads(record.get('stdout',''))
    require(output.get('status')=='verified-five-current-R8-unused-forest-cost-controls'
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
    require(re.fullmatch('[0-9a-f]{64}',pin or '') and prep.file_hash(Path(path))==pin,'Exact explicit fresh preparation hash differs')
    require(state.get('status')==prep.FINAL,'Incomplete source preparation')
    for field in ('maintained_sources_sha256','runtime_sources_sha256'):
        for name,hash_value in state[field].items(): require(prep.file_hash(Path(name))==hash_value,'Frozen source drift: '+name)
    require(prep.baseline.control_prep.current_runtime_sources()==state['runtime_sources_sha256'],'Complete current runtime source inventory differs')
    require(prep.matrix.development_fingerprints()==state['development_runtime_inputs'],'Current compiled/resource roots differ')
    source = state['source_qualification']
    for name,hash_value in source['source_files_sha256'].items():require(prep.file_hash(Path(name))==hash_value,'Canonical forest source drift: '+name)
    require(state['source_world_sha256']==source['source_world_sha256']==prep.quality.WORLD_SHA256,
        'Forest source world differs')
    require(prep.world_fingerprint(prep.quality.SOURCE/'saves'/prep.WORLD)==prep.quality.WORLD_SHA256,'Current canonical forest save differs')
    require(prep.qualify_quality_provenance()==state['historical_quality_provenance'],'Reviewed historical still provenance differs')
    external = state['independent_artifact_receipt']
    require(prep.qualify_runtime_receipt(external['path'],external['sha256'])==(external,state['auxiliary_directory_identities']), 'Current supplied R8 direct receipt differs')
    require(state['production_jar']==dict(path=str(prep.quality.JAR),sha256=prep.quality.JAR_SHA256), 'Production JAR record differs')
    inventory = state['complete_option_inventory']
    require(prep.file_hash(Path(inventory['path']))==inventory['sha256'],'Complete Iris inventory drift')
    inventories = json.loads(Path(inventory['path']).read_text())
    maps = prep.quality.option_maps(inventories['native']['options'])
    rows = state['launch_plan']
    require([r['arm'] for r in rows]==list(ORDER) and [r['mode'] for r in rows]==list(prep.MODES),'Five-arm order/mode differs')
    require(prep.validate_prepared_settings(rows)==state['prepared_controls_comparison'],'Prepared complete control comparison differs')
    for row in rows:
        profile = Path(row['profile']); mode = row['mode']; item = inventories[mode]
        require(profile==prep.OUTPUT/row['arm']/'control' and row['command']==prep.command(profile),'Owned forest profile/argv differs')
        require(item['option_count']==366 and item['options']==maps[mode]==row['expected']['expected_effective_options']
            and item['sha256']==prep.matrix.canonical_options_hash(maps[mode])==row['expected']['expected_effective_options_sha256'], 'Complete actual forest option map differs')
        for name,hash_value in item['parser_dependency_sha256'].items():require(prep.file_hash(Path(name))==hash_value,'Pinned Iris discovery dependency drift')
        require(prep.file_hash(Path(item['log']))==item['log_sha256'],'Actual Iris discovery log drift')
        manifest = json.loads((profile.parent/'workload-manifest.json').read_text()); prep.validate_manifest(manifest,maps['native'])
        require(manifest['source_qualification']==source and manifest['experiment_arms']['control']==row['expected'],'Complete manifest source/model differs')
        for name,hash_value in row['prepared_profile']['parent_files_sha256'].items():require(prep.file_hash(Path(name))==hash_value,'Frozen parent recipe drift')
        require(json.loads((profile/'workload-request.json').read_text())==row['request'],'Frozen workload request differs')
    return external['receipt']


def timing(report):
    result = actual.timing(report)
    require(all((s['frameId'] - 1) % 5 == 0 for s in report['gpu_samples']),'Observed GPU sample cadence phase differs from source schedule')
    wall = sum(s['wallMilliseconds'] for s in report['cpu_samples'] if s['scope']=='run-tick')/1000
    require(.98<=wall/result['elapsed_seconds']<=1.02,'Coarse frame wall coverage differs from actual elapsed capture')
    result['frame_wall_coverage_fraction'] = wall/result['elapsed_seconds']
    return result


def launch_controls(row,launch):
    require(row['command']==prep.command(row['profile']),'Exact forest cost argv differs')
    values = actual.launch_controls(row,launch)
    for key,target in (('hideWorkloadGui','true'),('pixelHistoryCapture','false')):
        require(values.get('shaderlod.dev.'+key)==target,'Actual Java forest control differs/missing: '+key)
    require(values.get('shaderlod.dev.pixelHistoryCaptureOnly','false')=='false','Unexpected history-only alias')
    return values


def terminal_controls(row,launch,terminal):
    require(terminal.get('owner')=='root' and terminal.get('status')=='terminal-audit'
        and type(terminal.get('exit_code')) is int and terminal['exit_code']==0,'Root terminal/normal exit receipt missing')
    launcher = actual.integer(launch.get('launcher_pid'),'initial launcher PID',1)
    require(terminal.get('launcher_pid')==launcher and terminal.get('launcher_present') is None,'Owned launcher still present/differs')
    owned = launch.get('java_process_ownership',{})
    require(len(owned)==1,'Exactly one owned Java birth required')
    java = next(iter(owned.values()));pid = actual.integer(java.get('pid'),'owned Java PID',1)
    require(terminal.get('java_present')=={str(pid):None},'Exact owned Java terminal absence unproved')
    events = terminal.get('events',[])
    require(any(e.get('pid')==pid and e.get('profile')==row['profile'] and e.get('identity')==java['identity']
        and e.get('status')=='owned' for e in events),'Terminal audit ownership association differs')
    return dict(launcher_pid=launcher,java_pid=pid,normal_exit=True,recorded_absence=True,
        limitation='Recorded terminal audit, not a claim that arbitrary current processes are absent.')


def boundary(report,row):
    expected = row['expected']; decoded = []
    require(report.get('effective_shader_options')==expected['expected_effective_options']
        and report.get('effective_shader_options_sha256')==expected['expected_effective_options_sha256'],'Report complete loaded map differs')
    for name in ('input_state_start','input_state_end'):
        state = report.get(name,{})
        require(state.get('effective_shader_options')==expected['expected_effective_options']
            and state.get('effective_shader_options_sha256')==expected['expected_effective_options_sha256'],'Boundary complete loaded map differs')
        require(state.get('shader_pack')==expected['archive'] and state.get('shaders_in_use') is True
            and state.get('fallback') is False and state.get('dimension')=='minecraft:overworld','Selected original shader/pipeline/dimension differs')
        for key,target in dict(framebuffer_width=2976,framebuffer_height=1672,requested_chunks=24,effective_chunks=24,day_time=7000).items():
            require(type(state.get(key)) is int and state[key]==target,'Fixed forest scalar differs: '+key)
        for key,target in dict(x=256.5,y=95.,z=768.5,yaw=135.,pitch=9.,rain=0.,thunder=0.).items():
            value = actual.finite(state.get(key),'forest '+key)
            require(abs(value-target)<=.002,'Fixed forest camera/weather differs: '+key)
        require(state.get('lod_settings')==dict(enabled=True,near=32.,far=96.,transition=8.,debug=False)
            and state.get('lod_preset')=='PERFORMANCE' and state.get('terrain_active') is False,'Loaded core/terrain controls differ')
        qualified = prep.quality.gui.qualify_configuration(state.get('configuration_files',{}),row)
        for config in ('config/shaderlod-client.toml','config/shaderlod-effects-client.toml'):
            require(qualified['settings'][config]==row['prepared_writable_inventory']['settings'][config],'Complete main/effects settings differ')
        prep.quality.validate_effects(state['configuration_files']['files']['config/shaderlod-effects-client.toml']['utf8'])
        for key,value in state.items():
            if key.startswith('config/') and key.endswith('_sha256'):
                require(state['configuration_files']['files'].get(key[:-7],{}).get('sha256')==value,'Raw config alias hash differs')
        decoded.append(qualified)
    require(report['input_state_start']==report['input_state_end'] and decoded[0]==decoded[1],'Full captured forest inputs/configuration changed')
    archive = Path(row['profile'])/'shaderpacks'/expected['archive']
    require(prep.file_hash(archive)==expected['archive_sha256']==prep.quality.canonical.ORIGINAL_SHA256,'Actual selected original archive differs')
    with zipfile.ZipFile(archive) as z:
        names = [n for n in z.namelist() if not n.endswith('/')]
        require(len(names)==len(set(names))==531,'Original 531-member archive differs/duplicates')
        members = {n:hashlib.sha256(z.read(n)).hexdigest() for n in names}
    manifest = report.get('workload-manifest.json',{})
    require(members==manifest['source_qualification']['complete_member_sha256'],'Complete original archive members differ')
    return decoded[0]


def screenshot(path,started_ns):
    """Fresh complete lossless PNG structure; gross image review remains separate."""
    path = Path(path)
    require(path.stat().st_mtime_ns>=started_ns,'Final PNG predates actual birth')
    data = path.read_bytes();require(data[:8]==b'\x89PNG\r\n\x1a\n','Final still is not PNG')
    offset=8;chunks=[];size=None;ended=False
    while offset<len(data):
        require(offset+12<=len(data),'Truncated PNG chunk')
        length=struct.unpack('>I',data[offset:offset+4])[0];kind=data[offset+4:offset+8];end=offset+12+length
        require(end<=len(data),'Truncated PNG payload')
        content=data[offset+8:offset+8+length];crc=struct.unpack('>I',data[offset+8+length:end])[0]
        require(zlib.crc32(kind+content)&0xffffffff==crc,'PNG chunk checksum differs')
        if not chunks:
            require(kind==b'IHDR' and length==13,'PNG first IHDR missing/different')
            size=list(struct.unpack('>II',content[:8]));require(size==[2976,1672],'Final PNG native dimensions differ')
        require(kind!=b'IHDR' or not chunks,'Duplicate PNG header')
        chunks.append(kind.decode('ascii'));offset=end
        if kind==b'IEND':
            require(length==0 and offset==len(data),'PNG terminator/trailing bytes differ');ended=True;break
    require(ended and 'IDAT' in chunks,'Incomplete PNG data/terminator')
    return dict(path=str(path.resolve()),sha256=hashlib.sha256(data).hexdigest(),native_dimensions=size,
        bytes=len(data),chunks=chunks,quality='unreviewed actual full still; structural checks do not establish visual equivalence')


def validate_arm(report,row,preparation,launch,terminal,console,report_path=None):
    require(report.get('schema')==2 and report.get('request')==row['request'],'Actual Workload schema/request differs')
    require(row['request'].get('gpu_queries') is True and row['request'].get('cpu_timing') is True,'Actual full-cost request differs')
    prep.matrix.validate_report(report,row['request'],row['inputs'],launch['started_ns'],report_path)
    temporal = prep.matrix.validate_temporal_inputs(report,row['request'])
    properties = launch_controls(row,launch); gui = prep.quality.gui.qualify_gui_report(report,row)
    for key,value in report.get('runtime_properties',{}).items():require(properties.get(key)==value,'Report/actual Java property mismatch: '+key)
    require(report.get('runtime_mod_jars_sha256')=={},'Unexpected profile jar/DH')
    require(all(report.get(k) is False for k in ('shadow_culling_active','separate_shadow_lists_active','shadow_frustum_coherence_active')),'Unrelated culling controls active')
    policy = report.get('geometry_policy',{})
    require(policy.get('active') is False and policy.get('requestedChunks')==policy.get('effectiveChunks')==24,'Actual terrain policy differs')
    require(not any(k in report for k in ('pixel_coverage_evidence','work_trace_evidence','spatial_layout_evidence','pixel_history_evidence')),'Native diagnostic contaminated timing')
    require(not (Path(row['profile'])/'logs/cloud-sampler-state.json').exists(),'Sampler probe artifact present in timed profile')
    qualified = boundary(report,row)
    evidence = report.get('loaded_artifact_evidence',{});receipt = preparation['independent_artifact_receipt']['receipt']
    require(evidence.get('valid') is True and evidence.get('complete_mod_coverage') is True
        and evidence.get('complete_identities') is True and evidence.get('errors')==[],'Incomplete/error direct artifact evidence')
    require(evidence.get('loaded_mods')==report.get('loaded_mods')==receipt['expected_loaded_mods']
        and len(report['loaded_mods'])==10,'Exact ten-mod registry differs')
    inventory = report.get('loaded_artifact_inventory')
    require(isinstance(inventory,list) and len(inventory)==9 and inventory==evidence.get('loaded_artifact_inventory'),'Complete direct nine-root inventory differs')
    prep.baseline.cost.artifacts.validate_inventory_against_receipt(inventory,receipt['loaded_artifact_inventory'],report['loaded_mods'])
    require(report.get('control.json')==row['inputs']['control'] and report.get('control_sha256')==row['inputs']['control_sha256'],'Full actual forest control differs')
    require(report.get('workload-manifest.json')==json.loads((Path(row['profile']).parent/'workload-manifest.json').read_text()),'Full frozen manifest differs')
    still = screenshot(Path(row['profile'])/'screenshots/workload-final.png',launch['started_ns'])
    contexts = hardware_context(console);result = timing(report)
    result.update(arm=row['arm'],mode=row['mode'],status=PASS,window_reference=actual.window(report),
        temporal=temporal,gui=gui,hardware_context=contexts,complete_settings=qualified['settings'],
        actual_configurations=qualified['actual_configuration_files'],qualified_backups=qualified['qualified_exact_first_backups'],
        actual_inventory=inventory,loaded_mods=report['loaded_mods'],raw_control=report['control.json'],
        source_archive_sha256=row['expected']['archive_sha256'],full_options=report['effective_shader_options'],
        terminal=terminal_controls(row,launch,terminal),final_still=still)
    return result


def cross_arm(arms):
    require([r.get('arm') for r in arms]==list(ORDER),'Need five qualified forest cost arms in exact order')
    reference = arms[0];settings = [];maps = prep.quality.option_maps(reference['full_options'])
    for row,mode in zip(arms,prep.MODES):
        require(row.get('status')==PASS and row.get('mode')==mode,'Unqualified/mislabeled forest arm')
        for key in ('window_reference','loaded_mods','raw_control','actual_inventory','source_archive_sha256'):
            require(row[key]==reference[key],'Cross-arm exact control/artifact differs: '+key)
        require(row['hardware_context']['typed']==reference['hardware_context']['typed'],'Cross-arm actual typed hardware context differs')
        require(row['full_options']==maps[mode],'Cross-arm complete options differ beyond temporal selectors')
        normalized = copy.deepcopy(row['complete_settings'])
        for base in ('shaderlod-client','shaderlod-effects-client'): normalized.pop('config/'+base+'-1.toml.bak',None)
        settings.append(normalized)
    require(all(s==settings[0] for s in settings),'Cross-arm complete other configuration differs')
    return prep.screen([r['frame_mean_ms'] for r in arms],[r['gpu_median_ms'] for r in arms])


def analyze(preparation_path,pin,preflight_path,logs,arm=None):
    state = json.loads(Path(preparation_path).read_text());qualify_frozen_preparation(state,preparation_path,pin)
    preflight = qualify_preflight(preflight_path,preparation_path,pin)
    selected = [r for r in state['launch_plan'] if arm is None or r['arm']==arm];require(selected,'Unknown arm')
    result = EVIDENCE | dict(schema=1,status='FAILED',preparation=dict(path=str(Path(preparation_path).resolve()),sha256=pin),
        preflight=dict(path=str(Path(preflight_path).resolve()),sha256=prep.file_hash(Path(preflight_path))),
        analyzer_source_files_sha256=source_pins(),arms=[],failures=[])
    for row in selected:
        log = Path(logs)/row['arm'];paths = dict(report=Path(row['profile'])/'workload-measurement.json',
            initial=log/'initial-process.json',terminal=log/'terminal-process.json',console=log/'console.log')
        entry = dict(arm=row['arm'],raw_inputs_sha256={str(p):prep.file_hash(p) for p in paths.values() if p.is_file()})
        try:
            launch = json.loads(paths['initial'].read_text())
            require(launch.get('preparation')==str(Path(preparation_path).resolve()) and launch.get('preparation_sha256')==pin,'Birth preparation receipt differs')
            require(launch['started_ns']>preflight['verified_ns'],'Successful unused-profile preflight must precede actual birth')
            entry.update(validate_arm(json.loads(paths['report'].read_text()),row,state,launch,
                json.loads(paths['terminal'].read_text()),paths['console'].read_text(),paths['report']))
        except (OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile) as error:
            entry.update(status='FAILED',reason=str(error));result['failures'].append(dict(arm=row['arm'],reason=str(error)))
        result['arms'].append(entry)
        if result['failures']:break
    if not result['failures']:
        if arm is not None:result['status']='PASS single forest arm; bracket pending'
        else:
            try:
                result['screen']=cross_arm(result['arms']);result['status']='PASS five-arm components; diagnostic stock screen only'
            except (ValueError,KeyError,TypeError) as error:result['failures'].append(dict(cross_arm=str(error)))
    require(source_pins()==result['analyzer_source_files_sha256'],'Analyzer source changed during analysis')
    result['limitations']='One serial stock calibration only; no ShaderLOD, repeated, motion, DH, or quality-equivalent gain. Runtime FOV/camera type/effect consumption and dropped-query counters unexported. Quiet interval and full still inspection remain separate root acceptance. Original raw failures preserved.'
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preparation',type=Path,default=prep.OUTPUT/prep.MARKER)
    parser.add_argument('--preparation-sha256',required=True);parser.add_argument('--preflight',type=Path,required=True)
    parser.add_argument('--logs',type=Path,required=True);parser.add_argument('--arm',choices=ORDER);parser.add_argument('--output',type=Path)
    args = parser.parse_args()
    try:result=analyze(args.preparation,args.preparation_sha256,args.preflight,args.logs,args.arm)
    except (OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile) as error:
        result=EVIDENCE | dict(status='FAILED before arm qualification',failures=[str(error)],analyzer_source_files_sha256=source_pins())
    payload=json.dumps(result,indent=2,sort_keys=True)+'\n'
    if args.output:
        require(not args.output.exists(),'Preserve earlier derived output; use a fresh filename')
        require(args.output.resolve().is_relative_to(ROOT/'build') or args.output.resolve().is_relative_to(ROOT/'dev/logs'),'Derived output must remain ignored')
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(payload)
    else:print(payload,end='')
    if result['status'].startswith('FAILED') or result.get('failures'):sys.exit(1)


if __name__=='__main__':main()
