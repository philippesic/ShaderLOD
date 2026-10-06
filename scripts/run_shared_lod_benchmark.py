#!/usr/bin/env python3
"""Run one fixed, position-balanced nine-JVM shared LOD pilot; never build or tune."""
from __future__ import annotations
import argparse
from collections import defaultdict
from datetime import datetime, timezone
import fcntl
import hashlib
import io
import json
import math
import os
from pathlib import Path
import shutil
import statistics
import subprocess
import threading
import time
import tomllib
import uuid
import zipfile

import prepare_shared_lod_runtime as prep
import run_proof_matrix as matrix
from summarize_workload import stats
from analyze_stack_matrix import load_equivalences, artifact_identities, qualified_identities

ROOT = prep.ROOT
JAR_SHA256 = 'aa65cf9157e0c5dac7db6ea65d38a42b3eec06a71838e394389063ead5b3e672'
CASES = {
    'original': ('high', 'water-high', 'runClientLodOff'),
    'disabled': ('patched-off', 'water-patched-off', 'runClientLodOn'),
    'adaptive': ('adaptive', 'water-adaptive', 'runClientLodOn'),
}
ORDERS = (('original', 'disabled', 'adaptive'), ('disabled', 'adaptive', 'original'), ('adaptive', 'original', 'disabled'))
OFF_PROPERTIES = ('gpuPassTiming', 'cpuLayerTiming', 'cloudSamplerProbe', 'dhDrawEvidence',
                  'waterSceneSurvey', 'shadowObserve', 'visibilityLists', 'shadowCoherenceOracle')
MATCH_FIELDS = ('framebuffer_width', 'framebuffer_height', 'requested_chunks', 'effective_chunks',
                'dimension', 'day_time', 'rain', 'thunder', 'x', 'y', 'z', 'yaw', 'pitch')


def verify_embedded_payloads(equivalences, children):
    """Recheck exact reviewed nested bytes and executable/resource member inventories."""
    entry=equivalences['entry'];payloads={}
    digest=lambda data:hashlib.sha256(data).hexdigest()
    def inventory(files):
        return dict(file_count=len(files),sha256=digest(''.join(name+'\0'+digest(data)+'\n' for name,data in sorted(files.items())).encode()))
    for variant in entry['variants']:
        name=variant['parent_mod_id'];data=children[name]
        require(digest(data)==variant['sha256'] and len(data)==variant['size'],'Exact embedded raw bytes differ: '+name)
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            infos=archive.infolist();require(len({i.filename for i in infos})==len(infos),'Duplicate embedded members')
            files={i.filename:archive.read(i) for i in infos if not i.is_dir()}
        classes={n:b for n,b in files.items() if n.endswith('.class')}
        resources={n:b for n,b in files.items() if not n.endswith('.class') and n!='META-INF/neoforge.mods.toml'}
        for key,selected in (('classes',classes),('resources_except_changed_metadata',resources),('all_files_except_changed_metadata',classes|resources)):
            require(inventory(selected)==entry['proof'][key],'Reviewed embedded content inventory differs: '+key)
        require(digest(files['META-INF/neoforge.mods.toml'])==variant['metadata_sha256'],'Reviewed dependency metadata differs')
        payloads[name]=files
    require(set(payloads['iris'])==set(payloads['sodium']),'Embedded member-name sets differ')
    require([n for n in sorted(payloads['iris']) if payloads['iris'][n]!=payloads['sodium'][n]]==['META-INF/neoforge.mods.toml'],
            'Unexpected differing embedded payload')
    require(payloads['iris']['META-INF/neoforge.mods.toml'].replace(b'[21.1.169,)',b'[21.1.115,)')==payloads['sodium']['META-INF/neoforge.mods.toml'],
            'Metadata difference exceeds reviewed compatible lower bound')


def verified_equivalences(root=ROOT):
    """Use only the existing exact registry, actual locked parents and nested content."""
    root=Path(root);result=load_equivalences(root/'dev/runtime-artifact-equivalences.json')
    runtime=json.loads((root/'dev/runtime.lock.json').read_text());children={}
    for variant in result['entry']['variants']:
        name=variant['parent_mod_id'];artifact=runtime['artifacts'][name]
        require(variant['parent_filename']==artifact['filename'],'Registry parent filename differs from runtime lock')
        parent=root/'dev/runtime'/variant['parent_filename']
        require(matrix.sha256(parent)==variant['parent_sha256'] and prep.file_hash(parent,'sha512')==artifact['hashes']['sha512'],
                'Actual pinned equivalence parent differs')
        with zipfile.ZipFile(parent) as archive:children[name]=archive.read(variant['embedded_path'])
    verify_embedded_payloads(result,children)
    return result


def runtime_identities(report,equivalences):
    """Retain raw variants; normalize only the reviewed exact API Base condition."""
    raw=artifact_identities(report);qualified,records=qualified_identities(report,raw,equivalences)
    qualified.pop('shaderlod',None)
    return qualified,records


def require(condition, reason):
    if not condition: raise ValueError(reason)


def ignored_path(path, parent, fresh=True):
    path = Path(path)
    require(not path.is_symlink(), 'Symlink output unsupported')
    path = path.resolve(); parent = Path(parent).resolve()
    require(path.is_relative_to(parent) and path != parent, 'Output must be in its ignored project root')
    require(not fresh or not path.exists(), 'Preserving existing output; use a fresh target')
    return path


def plan(profiles):
    profiles = Path(profiles).resolve()
    rows = []
    for block, order in enumerate(ORDERS, 1):
        for position, arm in enumerate(order, 1):
            folder, case, task = CASES[arm]
            profile = profiles / f'b{block}' / 'coastalwater' / folder
            command = ['./gradlew', task, '-PrunDirectory=' + str(profile), '-PperformanceStack=minimal',
                '-PincludeDh=false', '-PmeasureWorkload=true', '-PmeasureOnly=true', '-PinitialWarmup=25',
                '-PmeasurementSeconds=20', '-PgpuTiming=true', '-PgpuSampleInterval=5',
                '-PbenchmarkWindowScale=2', '-PcomparisonFullscreen=false', '-PfixedWetness=0']
            command += ['-P' + key + '=false' for key in OFF_PROPERTIES]
            for task_name in ('compileJava', 'compileDevClientJava', 'processResources', 'processDevClientResources'):
                command += ['-x', task_name]
            rows.append(dict(block=block, position=position, arm=arm, case=case, task=task,
                             profile=str(profile), command=command))
    return rows


def prepare(profiles):
    profiles = ignored_path(profiles, ROOT / 'run-workloads')
    matrix.require_exclusive_runtime()
    profiles.mkdir()
    state = dict(schema=1, status='preparing', canonical_world_sha256=prep.WORLD_SHA256,
        source_archive_sha256=prep.ARCHIVE_SHA256, blocks={}, plan=plan(profiles))
    marker = profiles / 'shared-benchmark-preparation.json'; matrix.write_json(marker, state)
    try:
        for block in range(1, 4):
            state['blocks'][str(block)] = prep.prepare(profiles / f'b{block}')
            matrix.write_json(marker, state)
        state['status'] = 'complete'; matrix.write_json(marker, state)
    except BaseException as error:
        state.update(status='failed', error=str(error)); matrix.write_json(marker, state); raise
    return state


def source_inputs(profiles):
    profiles = ignored_path(profiles, ROOT / 'run-workloads', fresh=False)
    state = json.loads((profiles / 'shared-benchmark-preparation.json').read_text())
    require(state['status'] == 'complete' and state['plan'] == plan(profiles), 'Fresh balanced preparation differs')
    require(matrix.sha256(ROOT / 'build/libs/shaderlod-0.1.0-dev.jar') == JAR_SHA256, 'Current reviewed production JAR differs')
    runtime = json.loads((ROOT / 'dev/runtime.lock.json').read_text())
    original, private, qualification = prep.qualify_archives(ROOT / 'dev/upstream/photon_v1.3b.zip', prep.PROBE, runtime)
    result = {}
    for row in plan(profiles):
        profile = Path(row['profile']); manifest = json.loads((profile.parent / 'workload-manifest.json').read_text())
        require(manifest['experiment_family'] == prep.EXPERIMENT and manifest['shared_lod_source_qualification'] == qualification,
                'Exact shared reference qualification differs')
        require(manifest['source_world_sha256'] == prep.WORLD_SHA256 and prep.world_fingerprint(profile / 'saves' / prep.WORLD) == prep.WORLD_SHA256,
                'Fresh canonical world differs')
        require(all(prep.file_hash(Path(p)) == pin for p, pin in manifest['source_files_sha256'].items()), 'Canonical source inputs drifted')
        inputs = matrix.profile_inputs(profile)
        require(inputs['shader_sha256'] == (original['sha256'] if row['arm'] == 'original' else private['sha256']), 'Wrong arm source')
        effects = profile / 'config/shaderlod-effects-client.toml'
        require(tomllib.loads(effects.read_text()) == tomllib.loads(prep.effects_config('adaptive')), 'Requested seven-effect controls differ')
        require(tomllib.loads((profile / 'config/shaderlod-client.toml').read_text()) == tomllib.loads(prep.copies.fresh_config(CASES[row['arm']][0])), 'Requested global controls differ')
        require(not list((profile / 'mods').glob('*.jar')), 'Profile must remain minimal/noDH')
        inputs['files'][str(effects)] = matrix.sha256(effects)
        result[str(profile)] = inputs
    require(prep.world_fingerprint(prep.SOURCE / 'saves' / prep.WORLD) == prep.WORLD_SHA256, 'Canonical source world drifted')
    return result


def validate_pilot(report, arm):
    """Add exact shared-system/window/cadence gates to the existing matrix validator."""
    require(arm in CASES, 'Unknown pilot arm')
    upstream = report.get('workload-manifest.json', {}).get('source_option_evidence', {}).get('source_effective_shader_options')
    require(isinstance(upstream, dict) and len(upstream) == 366 and matrix.canonical_options_hash(upstream) == prep.canonical.SOURCE_OPTIONS_SHA256,
            'Canonical 366-option provenance differs')
    expected = upstream if arm == 'original' else upstream | {prep.consumer.SWITCH: 'true'}
    require(report.get('effective_shader_options') == expected, 'Exact loaded options differ; only the registered integration gate is allowed')
    require(report.get('actual_shader_sha256') == (prep.consumer.BASE_SHA256 if arm == 'original' else prep.ARCHIVE_SHA256), 'Exact source archive differs')
    state = report['input_state_end']
    require(state['shaders_in_use'] is True and state['fallback'] is False and state['requested_chunks'] == state['effective_chunks'] == 24,
            'Actual source/shader/native radius unavailable')
    require(state['dimension'] == 'minecraft:overworld' and state['day_time'] == 12500 and state['rain'] == state['thunder'] == 0,
            'Scene time/weather differs')
    window = report['window_state']['capture']; count = window['observations']
    require(count > 0 and window['available_observations'] == count and window['unavailable_observations'] == 0, 'Window observation unavailable')
    for field, value in (('focused', False), ('visible', True), ('iconified', False)):
        require(window[field] == dict(true_observations=count if value else 0, false_observations=0 if value else count, unknown_observations=0),
                'Unstable/incorrect capture ' + field)
    require(window['window_size_counts'] == {'1488x836': count} and window['framebuffer_size_counts'] == {'2976x1672': count}, 'Native window/framebuffer differs')
    require(not window['transitions'] and window['dropped_transitions'] == 0 and window['start']['state'] == window['end']['state'], 'Capture window changed')
    require(window['end']['state']['pacing'] == dict(pause_on_lost_focus=False, fullscreen=False, configured_framerate_limit=260, vsync=False), 'Pacing differs')
    properties = report['runtime_properties']
    for key in OFF_PROPERTIES:
        if key == 'gpuPassTiming': continue  # Frame-only sample scopes/policy prove the effective mode below.
        require(properties.get('shaderlod.dev.' + key) == 'false', 'Observer/layer property not explicitly off: ' + key)
    require(properties['shaderlod.dev.gpuTiming'] == 'true' and properties['shaderlod.dev.gpuSampleInterval'] == '5', 'GPU query policy differs')
    require(report['backend'] == 'ELAPSED_ALTERNATING' and report['sampling_policy'] == 'frame only, every sampled frame; sample interval=5 render frames, poll only sampled frames', 'Expected frame-only elapsed GPU backend/cadence differs')
    require(report['warmup_seconds'] == 25 and report['elapsed_seconds'] >= 19, 'Pilot warmup/capture differs')
    samples = report['gpu_samples']
    require(len(samples) >= 30 and all(s['scope'] == 'game-render' and s['epoch'] == 2 and math.isfinite(s['milliseconds']) and s['milliseconds'] > 0 for s in samples), 'Missing/invalid/frame-external GPU samples')
    frames = [s['frameId'] for s in samples]
    require(len(set(frames)) == len(frames) and all((n-1) % 5 == 0 for n in frames) and all(b > a and (b-a) % 5 == 0 for a,b in zip(frames, frames[1:])), 'Actual GPU frame cadence differs')
    require(all(s['scope'] in ('run-tick', 'game-render', 'render-and-blit', 'swap-and-events') and s['epoch']==2 for s in report['cpu_samples']), 'Unexpected detailed CPU layer scope or measurement epoch')
    return dict(window_observations=count, native_window=[1488,836], native_framebuffer=[2976,1672], gpu_samples=len(samples), frame_only=True)


def metrics(report):
    gpu = [s['milliseconds'] for s in report['gpu_samples'] if s['scope'] == 'game-render']
    frame = [s['wallMilliseconds'] for s in report['cpu_samples'] if s['scope'] == 'run-tick']
    require(frame and all(math.isfinite(v) and v > 0 for v in frame), 'Missing/invalid total-frame wall intervals')
    return dict(gpu_elapsed=stats(gpu), total_frame_wall=stats(frame),
        elapsed_per_rendered_frame_ms=1000*report['elapsed_seconds']/report['render_frames'])


def spread(values):
    return dict(runs=len(values), median=statistics.median(values), min=min(values), max=max(values),
                median_absolute_deviation=statistics.median(abs(v-statistics.median(values)) for v in values))


def analyze(rows):
    completed = [r for r in rows if r.get('status') == 'complete']
    by_arm = {arm: [r for r in completed if r['arm'] == arm] for arm in CASES}
    result = dict(status='complete' if len(completed)==9 else 'incomplete', completed_runs=len(completed), by_arm={}, comparisons={})
    paths = (('gpu_elapsed', 'median_ms'), ('gpu_elapsed', 'p95_ms'), ('total_frame_wall', 'median_ms'), ('total_frame_wall', 'p95_ms'))
    for arm, group in by_arm.items():
        if group:
            result['by_arm'][arm] = {stream+'_'+field: spread([r['metrics'][stream][field] for r in group]) for stream,field in paths}
            result['by_arm'][arm]['elapsed_per_rendered_frame_ms'] = spread([r['metrics']['elapsed_per_rendered_frame_ms'] for r in group])
    for baseline, candidate in (('original','adaptive'), ('disabled','adaptive'), ('original','disabled')):
        pairs=[]
        for block in range(1,4):
            selected={r['arm']:r for r in completed if r['block']==block}
            if baseline in selected and candidate in selected: pairs.append((selected[baseline],selected[candidate]))
        if pairs:
            values={}
            for stream,field in paths:
                reductions=[100*(a['metrics'][stream][field]-b['metrics'][stream][field])/a['metrics'][stream][field] for a,b in pairs]
                values[stream+'_'+field]=dict(per_block_percent_time_reduction=reductions,spread=spread(reductions),faster_blocks=sum(v>0 for v in reductions))
            fractions=[a['metrics']['elapsed_per_rendered_frame_ms']/b['metrics']['elapsed_per_rendered_frame_ms'] for a,b in pairs]
            values['total_throughput_ratio']=dict(per_block=fractions,spread=spread(fractions),target=1.5)
            result['comparisons'][baseline+'_to_'+candidate]=values
    signal=result['comparisons'].get('original_to_adaptive',{})
    result['useful_consistent_signal']=(len(completed)==9 and all(signal[k]['faster_blocks']==3 and signal[k]['spread']['median']>=5
        for k in ('gpu_elapsed_median_ms','total_frame_wall_median_ms')) and all(v>1 for v in signal['total_throughput_ratio']['per_block']))
    result['interpretation']='Qualified three-block pilot; independent JVM runs, no summed passes, no physical-work/quality/50%-goal proof; temporal shader clocks are real and not frame-aligned.'
    return result


def context():
    """Read load/process context only between windows; do not alter other apps."""
    rows=subprocess.run(['ps','-axo','pid=,ppid=,%cpu=,comm='],text=True,capture_output=True,check=True).stdout.splitlines()
    processes=[]
    for line in rows:
        columns=line.split(None,3)
        if len(columns)==4:
            try: cpu=float(columns[2])
            except ValueError: continue
            processes.append(dict(pid=int(columns[0]),ppid=int(columns[1]),cpu_percent=cpu,command=columns[3]))
    return dict(utc=matrix.utc_now(),load_average=os.getloadavg(),processes=sorted(processes,key=lambda p:p['cpu_percent'],reverse=True)[:15])


def arm_focus(console, event_path):
    """Existing warmup-only policy, armed before client; no process scan or work in capture."""
    stop=threading.Event();result={};armed=threading.Event()
    def watch():
        armed.set()
        deadline=time.monotonic()+240
        while not stop.wait(.5) and time.monotonic()<deadline:
            text=console.read_text(errors='replace') if console.exists() else ''
            if 'ShaderLOD workload measurement started:' in text:
                result.update(status='failed',error='Focus action missed warmup');return
            if 'ShaderLOD landscape camera applied:' in text:
                action=subprocess.run(['/usr/bin/open','-a','Codex'],capture_output=True,text=True)
                result.update(status='complete' if action.returncode==0 else 'failed',utc=matrix.utc_now(),
                    action='open -a Codex during early warmup',returncode=action.returncode,stderr=action.stderr,
                    client_java_pids=matrix.minecraft_processes())
                matrix.write_json(event_path,result);return
        if not stop.is_set(): result.update(status='failed',error='Warmup watcher deadline reached')
    thread=threading.Thread(target=watch,name='shared-lod-warmup-focus',daemon=True);thread.start()
    require(armed.wait(2),'Focus watcher failed to arm')
    return stop,thread,result


def run(profiles, output):
    output=ignored_path(output,ROOT/'dev/logs');profiles=Path(profiles).resolve()
    with (ROOT/'dev/logs/.proof-matrix.lock').open('a+') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB);matrix.require_exclusive_runtime()
        inputs=source_inputs(profiles);equivalences=verified_equivalences();classes=matrix.development_fingerprints();rows=plan(profiles)
        output.mkdir()
        manifest=dict(schema=1,status='running',created_utc=matrix.utc_now(),runner_pid=os.getpid(),
            git_head=subprocess.run(['git','rev-parse','HEAD'],capture_output=True,text=True,check=True,cwd=ROOT).stdout.strip(),
            design='Three Latin rotations: O,D,A / D,A,O / A,O,D; position balance, not full carryover balance',
            runtime_jar_sha256=JAR_SHA256,development_runtime_inputs=classes,source_inputs=inputs,runs=rows,
            artifact_equivalence=equivalences,artifact_policy='Exact independently content-verified API Base registry; all other raw identities strict',
            prior_linkage_evidence='dev/logs/shared-lod-system-qa-r2/shared-lod-terminal-audit.json',
            runtime_lock_sha256=matrix.sha256(ROOT/'dev/runtime.lock.json'),initial_context=context())
        marker=output/'shared-benchmark-manifest.json';matrix.write_json(marker,manifest)
        active_watch=None
        try:
            for index,row in enumerate(rows,1):
                matrix.require_exclusive_runtime();require(matrix.development_fingerprints()==classes,'Frozen runtime changed before run')
                profile=Path(row['profile']);current=matrix.profile_inputs(profile)
                require(all(current[k]==inputs[str(profile)][k] for k in ('shader_sha256','semantic_shader_options_sha256','control_sha256','source_world_sha256')),'Prepared profile changed before run')
                require(prep.world_fingerprint(profile/'saves'/prep.WORLD)==prep.WORLD_SHA256,'Fresh run world changed')
                token=f"{index:03d}-b{row['block']}-{row['arm']}"
                console=output/(token+'-console.log');report_path=profile/'workload-measurement.json'
                request=dict(run_id=str(uuid.uuid4()),case=row['case'],stack='minimal',task=row['task'],include_dh=False,
                    measurement_seconds=20.0,gpu_queries=True,block=row['block'],fixed_wetness=0.0)
                require(not report_path.exists(),'Stale report in fresh run profile')
                matrix.write_json(profile/'workload-request.json',request)
                row.update(status='running',request=request,started_utc=matrix.utc_now(),prelaunch_context=context(),
                    measurement=token+'-measurement.json',console=console.name,summary=token+'-summary.json',focus=token+'-warmup-focus.json')
                active_watch=arm_focus(console,output/row['focus']);row['watcher']='Armed in runner before wait_command; thread exits at early-warmup action; no capture activity'
                matrix.write_json(marker,manifest)
                started_ns=time.time_ns();print(f"START {index}/9 {token} UUID={request['run_id']}; focus ARMED; exact command in manifest",flush=True)
                matrix.wait_command(row['command'],console,240)
                matrix.require_exclusive_runtime();require(matrix.development_fingerprints()==classes,'Frozen runtime changed during run')
                active_watch[1].join(2);focus=active_watch[2];require(focus.get('status')=='complete','Warmup focus watcher failed: '+repr(focus))
                shutil.copy2(report_path,output/row['measurement']);report=json.loads(report_path.read_text())
                row.update(matrix.preserve_screenshot(profile,output,token,started_ns))
                matrix.validate_report(report,request,inputs[str(profile)],started_ns,report_path)
                matrix.validate_optional_stack(report,'minimal',json.loads((ROOT/'dev/performance-mods.lock.json').read_text()))
                row['temporal_observations']=matrix.validate_temporal_inputs(report,request)
                row['pilot_validation']=validate_pilot(report,row['arm'])
                require(focus['utc']<report['measurement_started_at_utc'],'Focus action occurred during capture')
                require(tomllib.loads((profile/'config/shaderlod-client.toml').read_text())==tomllib.loads(prep.copies.fresh_config(CASES[row['arm']][0])),'Runtime global config semantics changed')
                require(tomllib.loads((profile/'config/shaderlod-effects-client.toml').read_text())==tomllib.loads(prep.effects_config('adaptive')),'Runtime seven-effect config semantics changed')
                row['metrics']=metrics(report);row['matched_state']={k:report['input_state_end'][k] for k in MATCH_FIELDS}
                row['loaded_mods']={k:v for k,v in report['loaded_mods'].items() if k!='shaderlod'}
                row['loaded_artifacts']={k:v for k,v in report['loaded_mod_artifacts'].items() if k!='shaderlod'}
                row['qualified_loaded_artifacts'],row['artifact_equivalence_records']=runtime_identities(report,equivalences)
                if index>1:
                    require(row['matched_state']==rows[0]['matched_state'] and row['loaded_mods']==rows[0]['loaded_mods'] and row['qualified_loaded_artifacts']==rows[0]['qualified_loaded_artifacts'],'Cross-run scene/runtime mismatch')
                matrix.write_json(output/row['summary'],row['metrics'])
                row.update(status='complete',finished_utc=matrix.utc_now(),postrun_context=context());matrix.write_json(marker,manifest)
                print('COMPLETE '+token+' GPUmedian='+str(row['metrics']['gpu_elapsed']['median_ms'])+'ms totalmedian='+str(row['metrics']['total_frame_wall']['median_ms'])+'ms',flush=True)
                active_watch=None
            manifest.update(status='complete',finished_utc=matrix.utc_now(),final_context=context())
            matrix.write_json(marker,manifest);matrix.write_json(output/'shared-benchmark-analysis.json',analyze(rows))
        except BaseException as error:
            if active_watch: active_watch[0].set();active_watch[1].join(2)
            manifest.update(status='failed',error=str(error),finished_utc=matrix.utc_now())
            if 'row' in locals() and row.get('status')=='running':
                row.update(status='failed',error=str(error))
                if report_path.exists(): shutil.copy2(report_path,output/row['measurement'])
            matrix.write_json(marker,manifest);matrix.write_json(output/'shared-benchmark-analysis.json',analyze(rows));raise
        return manifest


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profiles-root',type=Path,required=True);parser.add_argument('--output',type=Path)
    modes=parser.add_mutually_exclusive_group(required=True);modes.add_argument('--prepare',action='store_true');modes.add_argument('--dry-run',action='store_true');modes.add_argument('--run',action='store_true')
    args=parser.parse_args()
    if args.run and not args.output: parser.error('--run requires --output')
    if args.prepare: result=prepare(args.profiles_root)
    elif args.dry_run: result=dict(plan=plan(args.profiles_root),minimum_warmup_capture_seconds=405)
    else: result=run(args.profiles_root,args.output)
    print(json.dumps(result if not args.run else dict(status=result['status']),indent=2),flush=True)


if __name__=='__main__': main()
