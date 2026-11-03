#!/usr/bin/env python3
"""Strict actual WorkloadSession qualification for the five-arm Photon pixel bracket."""
from __future__ import annotations
import argparse
import copy
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import re
import statistics
import sys
import tomllib
import zipfile

import prepare_photon_pixel_baseline as prep
from analyze_inprocess_control import validate_fixed_window
from summarize_workload import stream

ROOT=prep.ROOT
EVIDENCE=dict(performanceEvidence=False, adapter_gain=False, quality='unaccepted',
    transition_accepted=False, repeated_gain=False, diagnostic_only=True)
SCOPES={'run-tick','game-render','render-and-blit','swap-and-events'}
SAMPLING='frame only, every sampled frame; sample interval=5 render frames, poll only sampled frames'
PREPARATION_SHA256='2fa50e93213721e9028db7d0e656720a3a8f6d3756e97a0ef56c7d7f7ef1e5d7'
STOCK_OPTIONS_SHA256='6e91dddf5938b6a8367d1b9f023c1297bc6d92fa122f5972efe6d3f1ef623395'


def require(condition,message):
    if not condition: raise ValueError(message)


def finite(value,label,positive=False):
    require(type(value) in (int,float) and math.isfinite(value) and (value>0 if positive else value>=0), 'Invalid '+label)
    return value


def integer(value,label,minimum=0):
    require(type(value) is int and value>=minimum,'Invalid '+label)
    return value


def hashed(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest()


def source_pins():
    """Independent analyzer/import graph; separate from the already frozen preparation."""
    paths=[ROOT/'scripts/analyze_photon_pixel_baseline.py', ROOT/'tests/test_photon_pixel_baseline_analysis.py']
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            path=Path(name).resolve()
            if path.suffix=='.py' and path.is_relative_to(ROOT/'scripts'): paths.append(path)
    return {str(p):prep.file_hash(p) for p in sorted(set(paths))}


def qualify_frozen_preparation(state,path):
    require(prep.file_hash(path)==PREPARATION_SHA256,'Frozen fresh preparation receipt differs')
    require(state.get('status')=='SOURCE_AND_CURRENT_RUNTIME_READY_ROOT_REVIEW_PENDING','Incomplete source preparation')
    # Do not call prep.maintained_sources() from this newly extended import graph.
    # Its frozen entries are each checked; this analyzer receives its own receipt.
    for field in ('maintained_sources_sha256','runtime_sources_sha256'):
        for name,pin in state[field].items(): require(prep.file_hash(Path(name))==pin,'Frozen source drift: '+name)
    require(prep.matrix.development_fingerprints()==state['development_runtime_inputs'],'Current class/resource root drift')
    external=state['independent_artifact_receipt']
    require(prep.coverage.qualify_receipt(external['path'],external['sha256'])==
        (external,state['auxiliary_directory_identities']),'Current direct artifact receipt drift')
    require(prep.live.qualify_archive()==state['live_source_qualification'],'Frozen private archive/source drift')
    inventory=state['complete_option_inventory']
    require(prep.file_hash(Path(inventory['path']))==inventory['sha256'],'Frozen full Iris inventory drift')
    require([r['arm'] for r in state['launch_plan']]==list(prep.ORDER),'Five-arm bracket order drift')
    return external['receipt']


def timing(report):
    """Actual producer streams: no BenchmarkSession fields or synthetic CPU rows."""
    require(report.get('backend')=='ELAPSED_ALTERNATING' and report.get('sampling_policy')==SAMPLING,'Frame-only GPU backend/cadence differs')
    elapsed=finite(report.get('elapsed_seconds'),'elapsed seconds',True)
    require(elapsed>=20 and report.get('warmup_seconds')==60,'60 s warmup / >=20 s capture required')
    frames=integer(report.get('render_frames'),'render frames',1)
    capture=report.get('window_state',{}).get('capture',{})
    first=integer(capture.get('start',{}).get('frame_id'),'capture first frame',1)
    last=integer(capture.get('end',{}).get('frame_id'),'capture last frame',1)
    require(last-first==frames,'Capture frame ledger/total mismatch')
    gpu,cpu=report.get('gpu_samples'),report.get('cpu_samples')
    require(isinstance(gpu,list) and len(gpu)>=60 and isinstance(cpu,list) and cpu,'Required full GPU/coarse CPU stream missing')
    require({s.get('scope') for s in cpu}==SCOPES,'Unknown/missing coarse CPU scopes')
    require(len({(s.get('scope'),s.get('frameId')) for s in cpu})==len(cpu),'Duplicate coarse scope/frame tuple')
    epochs=set()
    for s in gpu+cpu:
        epochs.add(integer(s.get('epoch'),'sample epoch',1))
        frame=integer(s.get('frameId'),'sample frame',1)
        require(first<=frame<last,'Timing observation outside actual capture frame range')
    # Actual Workload start resets initialized epoch 1 once; source pins required
    # by full qualification. A same-epoch stream alone cannot certify freshness.
    require(epochs=={2},'Mixed/stale/wrong Workload timing epoch')
    ids=[]
    for s in gpu:
        require(s.get('scope')=='game-render','Non-full-frame GPU scope')
        finite(s.get('milliseconds'),'GPU milliseconds',True); ids.append(s['frameId'])
    require(len(set(ids))==len(ids) and all(b-a==5 for a,b in zip(ids,ids[1:])), 'Duplicate/nonmonotonic/missing-interior N5 GPU samples')
    require(ids[0]-first<=5 and last-ids[-1]<=10,'GPU boundary sample coverage incomplete')
    full=list(range(first,last)); boundary_omissions=(full,full[1:],full[:-1])
    cpu_ranges={}
    for scope in sorted(SCOPES):
        selected=[s for s in cpu if s['scope']==scope]
        values=[s['frameId'] for s in selected]
        require(values in boundary_omissions,'Interior/multiple/misaligned coarse CPU frame omission: '+scope)
        for s in selected:
            finite(s.get('wallMilliseconds'),'CPU wall milliseconds')
            finite(s.get('threadMilliseconds'),'CPU thread milliseconds')
        cpu_ranges[scope]=dict(rows=len(values),first=values[0],last=values[-1],omitted_boundary_frames=frames-len(values))
    common=set.intersection(*(set(s['frameId'] for s in cpu if s['scope']==scope) for scope in SCOPES))
    require(len(common)>=frames-2 and common==set(range(min(common),max(common)+1)), 'Coarse CPU scopes do not share contiguous capture coverage')
    return dict(frame_mean_ms=elapsed*1000/frames, fps=frames/elapsed, gpu_n=len(gpu),
        gpu_median_ms=statistics.median(s['milliseconds'] for s in gpu), render_frames=frames,
        elapsed_seconds=elapsed, epoch=2, capture_frame_ids=[first,last], gpu_frame_ids=[ids[0],ids[-1]],
        cpu_ranges=cpu_ranges,cpu_wall=stream(cpu,'wallMilliseconds'),cpu_thread=stream(cpu,'threadMilliseconds'),
        limits='Nested coarse scopes remain separate, never summed. No Workload query counters/timingSnapshot are exported; contiguous positive N5 observations prove observed coverage, not unexported counter values.')


def window(report):
    ledger=report.get('window_state',{})
    capture=ledger.get('capture',{})
    reference=capture.get('start',{}).get('state',{})
    validate_fixed_window(dict(windowState=ledger,inputStart={'observed':reference},inputEnd={'observed':capture.get('end',{}).get('state')}),'observed')
    require(reference.get('availability')=='available' and reference.get('focused')=='true'
        and reference.get('visible')=='true' and reference.get('iconified')=='false','Capture not focused/visible/noniconified')
    n=capture['observations']
    require(capture.get('window_size_counts')=={'1488x836':n} and capture.get('framebuffer_size_counts')=={'2976x1672':n},'Capture dimensions changed/unknown')
    require([reference.get(k) for k in ('window_width','window_height','framebuffer_width','framebuffer_height')]==[1488,836,2976,1672],'Native dimensions differ')
    require(reference.get('pacing')==dict(pause_on_lost_focus=False,fullscreen=False,configured_framerate_limit=260,vsync=False),'Pacing differs')
    return reference


def vm_arguments(snapshot):
    require(isinstance(snapshot,dict) and isinstance(snapshot.get('path'),str) and isinstance(snapshot.get('utf8'),str), 'Launch-time Java VM argument snapshot missing')
    text=snapshot['utf8']
    require(hashlib.sha256(text.encode()).hexdigest()==snapshot.get('sha256'),'Launch VM argument snapshot hash differs')
    require(Path(snapshot['path']).name=='clientLodOnRunVmArgs.txt','Wrong Java VM argument file')
    values={}
    for line in text.splitlines():
        line=line.strip()
        if line.startswith('-Dshaderlod.dev.'):
            key,separator,value=line[2:].partition('=')
            require(separator and key not in values,'Duplicate/malformed Java control property')
            values[key]=value
    return values


def launch_controls(row,launch):
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
        fixedWetness='0',gpuTiming='true',gpuSampleInterval='5',initialWarmup='60',measurementSeconds='20',
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


def effects_backup_source():
    """Reuse independently disassembled exact FML backup route, retaining pins."""
    proof=ROOT/'build/pixel-control-review-r1/source-provenance.json'
    record=json.loads(proof.read_text())[0]
    jar=Path(record['argv'][4])
    require(prep.file_hash(jar)=='8390398e67df23e72d85ed809685be2752c8ec545b7cbb6ad4b70fdc81e4dcd4', 'Exact FML backup implementation jar drift')
    with zipfile.ZipFile(jar) as z:
        require(hashlib.sha256(z.read('net/neoforged/fml/config/ConfigTracker.class')).hexdigest()==
            'e8680453a8ecf0e273194a091f4dfbc64f55bf4ada948cc48846aae069b85fca','Exact FML backup implementation class drift')
    excerpt=ROOT/record['stdout_path']
    require(prep.file_hash(excerpt)=='4f1418e9ad8316eda76a1c347cfc006e4ab60811b9b4bd2525594dbe4da3f965','Reviewed backup disassembly drift')
    return dict(provenance_path=str(proof),provenance_sha256=prep.file_hash(proof),record=record,
        policy='ConfigTracker copies original registered configuration before correction into basename-1.extension.bak. Only exact effects first-backup bytes equal to the frozen prepared effects file are qualified; no settings/key/hash omission or rotation.')


def configuration_for_row(snapshot,row):
    """Explicit new analyzer startup role; frozen original helper/preparation untouched."""
    prepared=copy.deepcopy(row['prepared_writable_inventory'])
    name='config/shaderlod-effects-client-1.toml.bak'
    backup=snapshot.get('files',{}).get(name)
    source=None
    if backup is not None:
        source=effects_backup_source()
        pin=row['prepared_profile']['files_sha256'][row['profile']+'/config/shaderlod-effects-client.toml']
        require(backup.get('sha256')==pin,'Effects first backup is not exact prepared bytes')
        require(isinstance(backup.get('utf8'),str) and
            tomllib.loads(backup['utf8'])==prepared['settings']['config/shaderlod-effects-client.toml']['value'],
            'Effects first backup complete settings differ')
        # Add an exact-byte expected record only for this source-proven copied
        # non-live backup. The actual captured snapshot is never modified.
        prepared['settings'][name]=dict(format='unrecognized exact bytes',sha256=pin)
    result=prep.qualify_boundary_configuration(snapshot,prepared,row['expected']['archive'])
    result['qualified_effects_first_backup']=backup
    result['effects_first_backup_source']=source
    return result


def boundary(report,row):
    decoded=[]; expected=row['expected']; profile=Path(row['profile'])
    for name in ('input_state_start','input_state_end'):
        state=report.get(name,{})
        require(state.get('effective_shader_options')==expected['expected_effective_options'] and
            state.get('effective_shader_options_sha256')==expected['expected_effective_options_sha256'],'Complete loaded option map differs')
        require(state.get('shader_pack')==expected['archive'] and state.get('shaders_in_use') is True and state.get('fallback') is False,'Selected shader/pipeline fallback differs')
        for key,target in (('framebuffer_width',2976),('framebuffer_height',1672),('requested_chunks',24),('effective_chunks',24),('day_time',12500)):
            require(type(state.get(key)) is int and state[key]==target,'Fixed scene scalar differs: '+key)
        require(state.get('dimension')=='minecraft:overworld','Wrong scene dimension')
        for key,target in (('x',500.5),('y',68),('z',660.5),('yaw',87.38),('pitch',3.735),('rain',0),('thunder',0)):
            value=state.get(key); require(type(value) in (float,int) and math.isfinite(value) and abs(value-target)<=.002,'Fixed camera/weather differs: '+key)
        require(state.get('lod_settings')==dict(enabled=True,near=32.0,far=96.0,transition=8.0,debug=False)
            and state.get('lod_preset')=='PERFORMANCE' and state.get('terrain_active') is False,'Loaded core/terrain controls differ')
        qualified=configuration_for_row(state.get('configuration_files',{}),row)
        main=qualified['settings']['config/shaderlod-client.toml']['value']
        require(main['lod']['enabled'] is True and main['lod']['preset']=='PERFORMANCE','Persisted main controls differ')
        require(qualified['settings']['config/shaderlod-effects-client.toml']==
            row['prepared_writable_inventory']['settings']['config/shaderlod-effects-client.toml'],'Complete effects settings changed')
        for key,value in state.items():
            if key.startswith('config/') and key.endswith('_sha256'):
                require(state['configuration_files']['files'].get(key[:-7],{}).get('sha256')==value,'Raw config hash alias differs')
        decoded.append(qualified)
    require(decoded[0]==decoded[1],'Complete captured boundary configuration changed')
    archive=profile/'shaderpacks'/expected['archive']
    require(prep.file_hash(archive)==expected['archive_sha256'],'Selected archive bytes changed after capture')
    with zipfile.ZipFile(archive) as z:
        names=[n for n in z.namelist() if not n.endswith('/')]
        require(len(names)==len(set(names)),'Duplicate archive members')
        members={n:hashlib.sha256(z.read(n)).hexdigest() for n in names}
    manifest=report.get('workload-manifest.json',{})
    prep.validate_arm_manifest(manifest)
    require(members==manifest['source_qualification']['complete_member_sha256'],'Complete archive member pin differs')
    target_hash={'native':prep.canonical.SOURCE_OPTIONS_SHA256,'stock':STOCK_OPTIONS_SHA256,'live':prep.coverage.OPTIONS_SHA256}[row['mode']]
    require(expected['expected_effective_options_sha256']==target_hash,'Pinned complete option hash differs')
    return decoded[0]


def validate_arm(report,row,preparation,launch,console_text,report_path=None):
    """Pure report/receipt validation apart from immutable source/ZIP reads; raises on failure."""
    require(report.get('schema')==2 and report.get('request')==row['request'],'Actual Workload schema/request differs')
    prep.matrix.validate_report(report,row['request'],row['inputs'],launch['started_ns'],report_path)
    temporal=prep.matrix.validate_temporal_inputs(report,row['request'])
    require(report.get('warmup_seconds')==60 and report.get('elapsed_seconds',0)>=20,'Wrong workload clocks')
    properties=launch_controls(row,launch)
    for key,value in report.get('runtime_properties',{}).items():
        require(properties.get(key)==value,'Report/actual Java property mismatch: '+key)
    require(report.get('runtime_mod_jars_sha256')=={},'Unexpected profile runtime jar/DH')
    require(all(report.get(k) is False for k in ('shadow_culling_active','separate_shadow_lists_active','shadow_frustum_coherence_active')),'Active unrelated culling controls')
    require(report.get('geometry_policy',{}).get('active') is False and
        report.get('geometry_policy',{}).get('requestedChunks')==report.get('geometry_policy',{}).get('effectiveChunks')==24,'Actual terrain policy differs')
    require(not any(k in report for k in ('pixel_coverage_evidence','work_trace_evidence','spatial_layout_evidence')),'Readback diagnostic contaminated timing')
    qualified=boundary(report,row)
    evidence=report.get('loaded_artifact_evidence',{}); receipt=preparation['independent_artifact_receipt']['receipt']
    require(evidence.get('valid') is True and evidence.get('complete_mod_coverage') is True and
        evidence.get('complete_identities') is True and evidence.get('errors')==[],'Incomplete/error direct artifact evidence')
    require(evidence.get('loaded_mods')==report.get('loaded_mods')==receipt['expected_loaded_mods'] and len(report['loaded_mods'])==10,'Exact ten-mod loaded registry differs')
    inventory=report.get('loaded_artifact_inventory')
    require(isinstance(inventory,list) and len(inventory)==9 and inventory==evidence.get('loaded_artifact_inventory'),'Complete direct nine-root inventory differs')
    prep.cost.artifacts.validate_inventory_against_receipt(inventory,receipt['loaded_artifact_inventory'],report['loaded_mods'])
    require(report.get('control.json')==row['inputs']['control'] and report.get('control_sha256')==row['inputs']['control_sha256'],'Actual full control recipe differs')
    require(report.get('workload-manifest.json')==json.loads((Path(row['profile']).parent/'workload-manifest.json').read_text()),'Actual manifest differs from frozen file')
    expected_files=row['prepared_profile']['parent_files_sha256']
    for path,pin in expected_files.items(): require(prep.file_hash(Path(path))==pin,'Frozen parent control/manifest drift')
    contexts=[l for l in console_text.splitlines() if 'ShaderLOD GPU timers:' in l or 'ShaderLOD CPU timers:' in l]
    require(len(contexts)==2 and any('renderer=Apple M3 Max' in l and 'GL=4.1 Metal - 91.7' in l and 'backend=ELAPSED_ALTERNATING' in l for l in contexts)
        and any('currentThreadCpuSupported=true, layers=false' in l for l in contexts),'Actual hardware/driver/CPU timer context missing/different')
    result=timing(report)
    result.update(arm=row['arm'],mode=row['mode'],status='PASS actual Workload component gates',
        window_reference=window(report),temporal=temporal,hardware_context=contexts,
        complete_settings=qualified['settings'], actual_configurations=qualified['actual_configuration_files'],
        qualified_backup=qualified['qualified_first_backup'],qualified_effects_first_backup=qualified['qualified_effects_first_backup'],
        effects_first_backup_source=qualified['effects_first_backup_source'],actual_inventory=inventory,
        loaded_mods=report['loaded_mods'],raw_control=report['control.json'],
        source_archive_sha256=row['expected']['archive_sha256'],full_options_sha256=row['expected']['expected_effective_options_sha256'])
    return result


def cross_arm(arms):
    require([r['arm'] for r in arms]==list(prep.ORDER),'Need five qualified arms in exact order')
    reference=arms[0]
    settings=[]
    for row in arms:
        require(row.get('status')=='PASS actual Workload component gates','Unqualified arm cannot enter cost screen')
        for key in ('window_reference','loaded_mods','raw_control'):
            require(row[key]==reference[key],'Cross-arm control differs: '+key)
        normalized=copy.deepcopy(row['complete_settings'])
        iris=normalized['config/iris.properties']['value']
        require(iris.get('shaderPack') in ('photon_v1.3b.zip',prep.live.ARCHIVE.name),'Unknown selected archive name')
        iris.pop('shaderPack')
        # The optional unregistered first effects backup was independently
        # proven exact bytes of the same frozen prepared effects settings. Its
        # raw presence/bytes stay in each arm; only live settings enter this comparison.
        normalized.pop('config/shaderlod-effects-client-1.toml.bak',None)
        settings.append(normalized)
    require(all(s==settings[0] for s in settings),'Cross-arm complete other settings differ')
    # Receipts allow only the independently audited API Base metadata alternative;
    # each raw selected parent/member/hash stays in the arm output. Unknown changes
    # already fail exact per-arm inventory validation against the same receipt.
    return prep.screen([r['frame_mean_ms'] for r in arms],[r['gpu_median_ms'] for r in arms])


def analyze(preparation_path,logs,arm=None):
    state=json.loads(Path(preparation_path).read_text())
    qualify_frozen_preparation(state,preparation_path)
    selected=[r for r in state['launch_plan'] if arm is None or r['arm']==arm]
    require(selected,'Unknown arm')
    result=EVIDENCE | dict(schema=1,preparation=dict(path=str(Path(preparation_path).resolve()),sha256=prep.file_hash(preparation_path)),
        analyzer_source_files_sha256=source_pins(),status='FAILED',arms=[],failures=[])
    for row in selected:
        profile=Path(row['profile']); log=Path(logs)/row['arm']; report_path=profile/'workload-measurement.json'
        paths=dict(report=report_path,initial=log/'initial-process.json',console=log/'console.log')
        entry=dict(arm=row['arm'],raw_inputs_sha256={str(p):prep.file_hash(p) for p in paths.values() if p.is_file()})
        try:
            report=json.loads(report_path.read_text()); launch=json.loads(paths['initial'].read_text())
            entry.update(validate_arm(report,row,state,launch,paths['console'].read_text(),report_path))
        except (OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile) as error:
            entry.update(status='FAILED',reason=str(error)); result['failures'].append(dict(arm=row['arm'],reason=str(error)))
        result['arms'].append(entry)
        if result['failures']: break  # Fail fast; no automatic second arm/run.
    if not result['failures']:
        if arm is not None: result['status']='PASS single arm; bracket and quality pending'
        else:
            try: result['screen']=cross_arm(result['arms']); result['status']='PASS five-arm component/cross-arm guards; inspect screen verdict'
            except (ValueError,KeyError,TypeError) as error: result['failures'].append(dict(cross_arm=str(error)))
    require(source_pins()==result['analyzer_source_files_sha256'],'Analyzer source changed during analysis')
    result['limitations']='Single serial screen only. Gross still/motion/transition quality and repeated/query-free acceptance pending; process terminal/quiet-window receipts require separate root confirmation. No missing measurement rows or failed raw statuses are changed.'
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preparation',type=Path,default=prep.OUTPUT/prep.MARKER)
    parser.add_argument('--logs',type=Path,required=True);parser.add_argument('--arm',choices=prep.ORDER)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    try: result=analyze(args.preparation,args.logs,args.arm)
    except (OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile) as error:
        result=EVIDENCE|dict(status='FAILED before arm qualification',failures=[str(error)],analyzer_source_files_sha256=source_pins())
    payload=json.dumps(result,indent=2,sort_keys=True)+'\n'
    if args.output:
        require(not args.output.exists(),'Preserve earlier analysis output; use a fresh filename')
        require(args.output.resolve().is_relative_to(ROOT/'dev/logs') or args.output.resolve().is_relative_to(ROOT/'build'),'Derived output must be ignored')
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(payload)
    else: print(payload,end='')
    if result['status'].startswith('FAILED') or result.get('failures'): sys.exit(1)


if __name__=='__main__': main()
