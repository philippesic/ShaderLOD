#!/usr/bin/env python3
"""Prepare/run two bounded untimed same-draw diagnostic arms; never build or tune."""
from __future__ import annotations
import argparse
from collections import Counter
import fcntl
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time
import tomllib
import uuid
import zipfile

import prepare_shared_lod_runtime as shared
import run_proof_matrix as matrix
import run_shared_lod_benchmark as runtime

ROOT = shared.ROOT
LEDGER = ROOT / 'build/photon-work-trace-freeze.json'
ARMS = ('original', 'adaptive')
REVIEWED_MANIFEST_SHA256 = '23ae2d36851b71a4a8f60981a428a535f3687420da34b471c17b3a43c45a4a2f'
REVIEWED_ENABLED = {
    'original': ('89e0a379b9b6a4eeda07fa564c209fdd8487420e114a1e3aa4ffa4620ddd1b70', 'a144222e52b8b88847e84c395cb13706c3feca6a3687a9a0969bcce9754d6b68'),
    'adaptive': ('e8519e91eb463379ec8ecc17658fb22f4130ae9ca72971b900f2ebb343970088', 'b27ee4d831aa44b03481a65d21abee7c86e47cee87cf688d31409f1b9c69471f'),
}
require = shared.require
write = matrix.write_json


def qualify(ledger=LEDGER):
    ledger = Path(ledger)
    data = json.loads(ledger.read_text())
    require(data.get('diagnostic_only') is True and data.get('performanceEvidence') is False, 'Ledger is not diagnostic-only')
    require(data['manifest_sha256'] == REVIEWED_MANIFEST_SHA256 and shared.file_hash(ROOT / 'patches/photon-experiments/shared-work-trace.json') == REVIEWED_MANIFEST_SHA256, 'Reviewed trace manifest drift')
    manifest = json.loads((ROOT / 'patches/photon-experiments/shared-work-trace.json').read_text())
    require(manifest['layout']['cloud_extent_targets'] == [9,10,11,12,15] and manifest['layout']['trace_output_targets'] == [9,10,11], 'Reviewed downstream layout drift')
    selected = {}
    for arm, source in (('original', 'original'), ('adaptive', 'shared')):
        matches = [r for r in data['artifacts'] if r['source'] == source and r['trace_default'] is True]
        require(len(matches) == 1, 'Exactly one enabled trace artifact required')
        row = matches[0]; require((row['sha256'],row['registration_sha256']) == REVIEWED_ENABLED[arm], 'Reviewed enabled artifact pin differs')
        archive, registration = Path(row['archive']), Path(row['registration'])
        require(archive.is_absolute() and registration.is_absolute(), 'Absolute source/registration required')
        require(shared.file_hash(archive) == row['sha256'], 'Exact trace archive drift')
        require(shared.file_hash(registration) == row['registration_sha256'], 'Exact registration drift')
        with zipfile.ZipFile(archive) as z:
            names = [i.filename for i in z.infolist() if not i.is_dir()]
            require(len(names) == len(set(names)), 'Duplicate archive member')
            inventory = {n: hashlib.sha256(z.read(n)).hexdigest() for n in sorted(names)}
            require(all(inventory.get(n) == pin for n, pin in row['member_sha256'].items()), 'Pinned trace member drift')
            defaults = matrix.archive_defaults(z)
            properties = z.read('shaders/shaders.properties').decode()
            for scale in ('1.0','0.5','0.333333','0.25'):
                block = '#ifdef SHADERLOD_WORK_TRACE\n' + ''.join(f'size.buffer.colortex{target} = {scale} {scale}\n' for target in (11,12,15)) + '#endif // SHADERLOD_WORK_TRACE'
                require(properties.count(block) == 1, 'Exact guarded downstream extent block differs')
        metadata = json.loads(registration.read_text()); binding = metadata['sourceBindings']
        require(len(binding) == 1 and binding[0] == dict(sourceSha256=row['sha256'], memberSha256=row['member_sha256'], requiredOptions=row['required_options']), 'Registration binding differs from ledger')
        capture = metadata['capture']
        require(capture['source'] == str(archive) and capture['attachments'] == [0,1,2] and capture['colortexes'] == [9,10,11] and capture['pages'] == [0,1,2] and capture['format'] == 'RGBA32F', 'Page/attachment role contract drift')
        require(metadata['traceContract'].get('layout') == manifest['layout'], 'Registered layout contract differs')
        require(metadata['traceContract']['schema'] == 1 and metadata['traceContract']['diagnostic_only'] is True, 'Trace schema drift')
        require(defaults['SHADERLOD_WORK_TRACE'] == 'true', 'Enabled trace archive required')
        selected[arm] = row | dict(complete_member_sha256=inventory, metadata=metadata)
    return selected


def plan(output, root=ROOT):
    output = runtime.ignored_path(output, Path(root) / 'run-workloads')
    return dict(schema=1, output=str(output), arms=list(ARMS), diagnostic_only=True, performanceEvidence=False,
        canonical_world_sha256=shared.WORLD_SHA256, window=[1488,836], framebuffer=[2976,1672],
        warmup_seconds=25, capture_seconds=5, dh_present=False, main_mod_present=True,
        gpuTiming=False, cpuTiming=False, cpuLayerTiming=False)


def prepare(output):
    operation = plan(output); matrix.require_exclusive_runtime(); artifacts = qualify()
    runtime_lock = json.loads((ROOT / 'dev/runtime.lock.json').read_text())
    with shared.copies.unlocked_world(shared.SOURCE / 'saves' / shared.WORLD):
        scene = shared.inspect_scene(dict(scene='coastalwater', source_profile=str(shared.SOURCE)), shared.PROBE, runtime_lock)
        output = Path(operation['output']); output.mkdir(parents=True)
        base = output / 'coastalwater'; base.mkdir(); provenance = base / 'source-provenance'; provenance.mkdir()
        for name, path in scene['provenance_files'].items(): shutil.copy2(path, provenance / name)
        state = dict(status='preparing', operation=operation, ledger_sha256=shared.file_hash(LEDGER),
            artifacts=artifacts, source_files_sha256=scene['source_files_sha256'],
            upstream_effective_options=scene['option_evidence']['source_effective_shader_options'], arms={})
        marker = output / 'work-trace-preparation.json'; write(marker, state)
        try:
            for arm in ARMS:
                profile = base / arm; row = artifacts[arm]
                shutil.copytree(scene['profile'], profile, ignore=shutil.ignore_patterns(*shared.copies.TRANSIENT))
                world = profile / 'saves' / shared.WORLD; world.parent.mkdir()
                shutil.copytree(scene['world'], world, ignore=shutil.ignore_patterns('session.lock'))
                require(shared.world_fingerprint(world) == shared.WORLD_SHA256, 'Fresh canonical world mismatch')
                require(not list((profile / 'mods').glob('*.jar')), 'noDH/minimal profile required')
                packs = profile / 'shaderpacks'; packs.mkdir(); archive = Path(row['archive']); shutil.copy2(archive, packs / archive.name)
                flags = {name: values[0] for name, values in row['required_options'].items()}
                require(all(len(values) == 1 for values in row['required_options'].values()), 'Single exact option value required')
                options = scene['native_options'] | flags
                (packs / (archive.name + '.txt')).write_text(''.join(f'{n}={v}\n' for n,v in sorted(options.items())))
                iris = matrix.read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive.name
                (profile / 'config/iris.properties').write_text(''.join(f'{n}={v}\n' for n,v in sorted(iris.items())))
                (profile / 'config/shaderlod-client.toml').write_text(shared.copies.fresh_config('adaptive' if arm == 'adaptive' else 'patched-off'))
                (profile / 'config/shaderlod-effects-client.toml').write_text(shared.effects_config('adaptive'))
                expected = state['upstream_effective_options'] | flags
                require(len(expected) == 366 + (2 if arm == 'adaptive' else 1), 'Exact option inventory count differs')
                state['arms'][arm] = dict(profile=str(profile), archive_sha256=row['sha256'], registration=row['registration'],
                    expected_effective_options=expected, source_world_sha256=shared.WORLD_SHA256,
                    global_enabled=arm == 'adaptive', main_mod_present=True, diagnostic_only=True, performanceEvidence=False,
                    input_files_sha256={str(p.relative_to(profile)): shared.file_hash(p) for p in profile.rglob('*') if p.is_file() and not p.is_relative_to(world)})
            require(shared.world_fingerprint(scene['world']) == shared.WORLD_SHA256, 'Canonical world changed during prep')
            require(all(shared.file_hash(Path(p)) == pin for p,pin in scene['source_files_sha256'].items()), 'Canonical source changed during prep')
            write(base / 'control.json', scene['control'])
            write(base / 'workload-manifest.json', dict(schema=1, configuration_only=False, diagnostic_only=True, performanceEvidence=False,
                source_world_sha256=shared.WORLD_SHA256, experiment_arms=state['arms'], source_option_evidence=scene['option_evidence'], control=scene['control']))
            state['status'] = 'complete'; write(marker,state)
        except BaseException as error:
            state.update(status='failed',error=str(error)); write(marker,state); raise
    return state


def command(profile, registration, trace_output):
    args = ['./gradlew','runClientLodOn', '-PrunDirectory='+str(profile), '-PperformanceStack=minimal','-PincludeDh=false',
        '-PmeasureWorkload=true','-PmeasureOnly=true','-PinitialWarmup=25','-PmeasurementSeconds=5',
        '-PbenchmarkWindowScale=2','-PcomparisonFullscreen=false','-PfixedWetness=0',
        '-PworkTrace=true','-PworkTraceOnly=true','-PworkTraceMetadata='+str(registration),'-PworkTraceOutput='+str(trace_output)]
    args += ['-P'+key+'=false' for key in ('gpuTiming','cpuTiming','cpuLayerTiming','gpuPassTiming','cloudSamplerProbe','dhDrawEvidence','waterSceneSurvey','shadowObserve','visibilityLists','shadowCoherenceOracle')]
    for task in ('compileJava','compileDevClientJava','processResources','processDevClientResources'): args += ['-x',task]
    return args


def validate_trace(trace):
    require(trace.get('performanceEvidence') is False and trace.get('captureWindowFinished') is True, 'Trace must be terminal diagnostic')
    require(trace.get('terminalStatus') == 'CAPTURED' and trace.get('captures'), 'No matching diagnostic draws')
    totals = Counter(); paths = Counter(); points = []
    for capture in trace['captures']:
        require(capture.get('status') == 'RAW_SAME_DRAW_CAPTURE', 'Unknown capture: '+str(capture.get('reason')))
        require(capture['performanceEvidence'] is False and not capture.get('restoreErrors'), 'State restoration failure')
        require(capture['attachments'] == [0,1,2] and capture['schema'] == 1, 'Trace pages/schema mismatch')
        images = capture['attachmentImages']; require(len(images) == 3, 'Missing attachment images')
        require(all(i['internalFormat'] == 34836 and i['width'] == images[0]['width'] and i['height'] == images[0]['height'] for i in images), 'RGBA32F extents differ')
        require(capture['schemaUniform'].get('value') == 1 and capture['schemaUniform'].get('type') == 5124 and capture['schemaUniform'].get('location', -1) >= 0, 'Typed marker did not read one')
        viewport=capture['viewport']; require(len(viewport)==4 and viewport[0]>=0 and viewport[1]>=0 and viewport[2]>0 and viewport[3]>0, 'Invalid viewport')
        require(capture['program']>0 and capture['drawFramebuffer']>0, 'Invalid producer/FBO')
        require(all(i['object']>0 and i['level']==0 and viewport[0]+viewport[2]<=i['width'] and viewport[1]+viewport[3]<=i['height'] for i in images), 'Attachment identity/viewport mismatch')
        for sample in capture['samples']:
            d=sample['decoded']; require(d.get('status') == 'VALID_SCHEMA_1', 'Unknown/sentinel sample: '+str(d.get('reason')))
            pages=sample['pages']; require(len(pages)==3 and all(len(p)==4 for p in pages), 'Missing raw pages')
            require(all(isinstance(v,(int,float)) and math.isfinite(v) for p in pages for v in p), 'Nonfinite pages')
            require(pages[2] == [1,0,1,0] and d['pathTag'] in (0,1,2), 'Unsupported schema/reason/path')
            require(all(0<=v<=16777215 for p in pages for v in p), 'Raw values out of range')
            require(all(v==int(v) for v in pages[0]+[pages[1][0],pages[1][3]]+pages[2]), 'Fractional raw integer')
            require([d[k] for k in ('primaryIterations','densityEvaluations','secondaryVisits','textureEvaluations')]==pages[0], 'Decoded counters differ from raw')
            require([d[k] for k in ('originalWork','continuousRequestedWork','continuousSelectedWork','pathTag')]==pages[1], 'Decoded budgets/path differ from raw')
            require(d['selectedCeilTarget']==math.ceil(pages[1][2]) and d['reasonTag']==0, 'Ceil target/reason differs')
            require(viewport[0]<=sample['x']<viewport[0]+viewport[2] and viewport[1]<=sample['y']<viewport[1]+viewport[3], 'Sample outside viewport')
            for field in ('primaryIterations','densityEvaluations','secondaryVisits','textureEvaluations'): totals[field]+=d[field]
            paths[str(d['pathTag'])]+=1
            points.append(dict(x=sample['x'],y=sample['y'],decoded=d,worldGameTime=capture.get('worldGameTime'),frameCounter=capture.get('frameCounter'),frameTimeCounter=capture.get('frameTimeCounter'),drawOrdinal=capture['drawOrdinal'],renderFrameOrdinal=capture['renderFrameOrdinal']))
    return dict(diagnostic_only=True,performanceEvidence=False,captures=len(trace['captures']),valid_pixels=len(points),unknown_pixels=0,counts=dict(totals),paths=dict(paths),points=points,
        gl_errors='unobserved; capture intentionally does not consume GL error flags',state_restoration='all restoration attempts returned without exception; no before/after equality snapshot')


def validate_report(report, request, expected, inputs):
    require(report.get('status') == 'complete' and report.get('request',{}).get('run_id') == request['run_id'], 'Report incomplete/stale')
    require(report.get('diagnostic_only') is True and report.get('performanceEvidence') is False, 'Workload is not diagnostic-only')
    require(report['modPresent'] is True and report['dhPresent'] is False, 'Mod/noDH contract failed')
    require(report['actual_shader_sha256'] == expected['archive_sha256'], 'Loaded source differs')
    require(report['effective_shader_options'] == expected['expected_effective_options'], 'Exact loaded option inventory differs')
    require(report['effective_shader_options_sha256'] == matrix.canonical_options_hash(report['effective_shader_options']), 'Loaded option hash differs')
    require(report['control_sha256'] == inputs['control_sha256'] and report['input_state_start'] == report['input_state_end'], 'Inputs/control changed')
    state=report['input_state_end']
    require(state['shaders_in_use'] is True and state['fallback'] is False and state['lod_settings']['enabled'] is expected['global_enabled'], 'Shader/global state mismatch')
    require([state['framebuffer_width'],state['framebuffer_height']] == [2976,1672] and state['requested_chunks'] == state['effective_chunks'] == 24, 'Native extent/radius mismatch')
    require(state['dimension']=='minecraft:overworld' and state['day_time']==12500 and state['rain']==state['thunder']==0, 'Scene weather/time mismatch')
    for key in ('x','y','z','yaw','pitch'): require(matrix.camera_difference(state[key],inputs['control'][key],key=='yaw') < .002, 'Camera differs: '+key)
    require(not report['gpu_samples'] and not report['cpu_samples'], 'Diagnostic timing stream unexpectedly present')
    require(report['warmup_seconds']==25 and report['elapsed_seconds']>=4.75, 'Warmup/capture shorter than requested')
    window=report['window_state']['capture']; count=window['observations']; require(count>0 and window['available_observations']==count and not window['transitions'] and not window['dropped_transitions'], 'Window unavailable/changed')
    require(window['window_size_counts']=={'1488x836':count} and window['framebuffer_size_counts']=={'2976x1672':count}, 'Window extent changed')
    for field,value in (('focused',False),('visible',True),('iconified',False)): require(window[field]==dict(true_observations=count if value else 0,false_observations=0 if value else count,unknown_observations=0), 'Window '+field+' mismatch')
    return matrix.validate_temporal_inputs(report,request)


def run(profiles, output):
    profiles=Path(profiles).resolve(); output=runtime.ignored_path(output,ROOT/'dev/logs')
    state=json.loads((profiles/'work-trace-preparation.json').read_text()); require(state['status']=='complete','Preparation incomplete')
    require(qualify()==state['artifacts'],'Trace artifact freeze changed')
    with (ROOT/'dev/logs/.proof-matrix.lock').open('a+') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB);matrix.require_exclusive_runtime()
        require(shared.file_hash(ROOT/'build/libs/shaderlod-0.1.0-dev.jar')==runtime.JAR_SHA256,'Production JAR changed')
        frozen=matrix.development_fingerprints(); output.mkdir(); marker=output/'work-trace-terminal-audit.json'
        audit=dict(status='running',diagnostic_only=True,performanceEvidence=False,runner_pid=os.getpid(),created_utc=matrix.utc_now(),development_runtime_inputs=frozen,preparation=str(profiles/'work-trace-preparation.json'),runs=[])
        write(marker,audit); active_watch=None; process=None
        try:
            for arm in ARMS:
                expected=state['arms'][arm]; profile=Path(expected['profile']); matrix.require_exclusive_runtime()
                require(matrix.development_fingerprints()==frozen,'Classes/resources changed')
                require(shared.world_fingerprint(profile/'saves'/shared.WORLD)==shared.WORLD_SHA256,'Fresh world changed')
                require(all(shared.file_hash(profile/p)==pin for p,pin in expected['input_files_sha256'].items()),'Prepared profile input drift')
                inputs=matrix.profile_inputs(profile);trace_path=output/(arm+'-raw-work-trace.json'); console=output/(arm+'-console.log')
                request=dict(run_id=str(uuid.uuid4()),case='water-adaptive' if arm=='adaptive' else 'water-patched-off',stack='minimal',task='runClientLodOn',include_dh=False,measurement_seconds=5.,gpu_queries=False,fixed_wetness=0.,diagnostic_only=True,performanceEvidence=False)
                write(profile/'workload-request.json',request)
                row=dict(arm=arm,status='running',request=request,command=command(profile,Path(expected['registration']),trace_path),started_utc=matrix.utc_now(),diagnostic_only=True,performanceEvidence=False)
                audit['runs'].append(row);active_watch=runtime.arm_focus(console,output/(arm+'-warmup-focus.json'));row['focus_watcher']='armed before launch; early warmup only';write(marker,audit)
                with console.open('w') as log:
                    process=subprocess.Popen(row['command'],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                    row.update(launcher_pid=process.pid,launcher_session=os.getsid(process.pid),stop_procedure='SIGTERM exact launcher process group; after 20 s SIGKILL same group only');write(marker,audit)
                    print('START '+arm+' PID='+str(process.pid)+' UUID='+request['run_id'],flush=True)
                    deadline=time.monotonic()+240
                    while process.poll() is None:
                        require(time.monotonic()<deadline, 'Client deadline reached')
                        require(not matrix.FATAL_LOG.findall(console.read_text(errors='replace')), 'Driver/runtime error before terminal capture; see '+str(console))
                        time.sleep(.5)
                    code=process.returncode;row['exit_code']=code;require(code==0,'Gradle/client failed; see '+str(console));process=None
                active_watch[1].join(2); require(active_watch[2].get('status')=='complete','Focus watcher failed');active_watch=None
                matrix.require_exclusive_runtime();require(matrix.development_fingerprints()==frozen,'Frozen inputs changed during run')
                require(not matrix.FATAL_LOG.findall(console.read_text(errors='replace')),'Driver/runtime errors in '+str(console))
                report=json.loads((profile/'workload-measurement.json').read_text());write(output/(arm+'-workload-measurement.json'),report)
                row['temporal_observations']=validate_report(report,request,expected,inputs)
                trace=json.loads(trace_path.read_text()); require(trace.get('metadata')==state['artifacts'][arm]['metadata'], 'Runtime trace metadata differs');row['trace_summary']=validate_trace(trace);row['loaded_mods']=report['loaded_mods'];row['system_info']=report.get('system_info',{})
                row.update(status='complete',finished_utc=matrix.utc_now());write(marker,audit)
                print('COMPLETE '+arm+' counts='+str(row['trace_summary']['counts']),flush=True)
            audit.update(status='complete',comparison='Counts are diagnostic sampled rays. Independent JVM world-age/projection/TAA may differ; compare only proven temporal matches, never arm totals causally.')
        except BaseException as error:
            if process is not None and process.poll() is None:
                # Gradle may start Minecraft under its existing daemon, outside
                # the launcher group. Exclusivity was proved before our launch.
                owned_clients=matrix.minecraft_processes();audit['stopped_exact_client_pids']=owned_clients
                for client_pid in owned_clients:
                    try:os.kill(client_pid,signal.SIGTERM)
                    except ProcessLookupError:pass
                os.killpg(process.pid,signal.SIGTERM)
                try:process.wait(timeout=20)
                except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
            if active_watch:active_watch[0].set();active_watch[1].join(2)
            audit.update(status='failed',error=str(error))
            if audit['runs']:audit['runs'][-1].update(status='failed',error=str(error))
            print('STOP '+str(error),flush=True)
        finally:
            audit.update(finished_utc=matrix.utc_now(),remaining_minecraft_pids=matrix.minecraft_processes(),frozen_runtime_unchanged=matrix.development_fingerprints()==frozen)
            write(marker,audit)
    return audit


def main():
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--output',type=Path,required=True);parser.add_argument('--profiles',type=Path);parser.add_argument('--run',action='store_true');parser.add_argument('--dry-run',action='store_true');args=parser.parse_args()
    if args.run and not args.profiles:parser.error('--run requires --profiles')
    result=run(args.profiles,args.output) if args.run else (plan(args.output) if args.dry_run else prepare(args.output))
    print(json.dumps(dict(status=result.get('status','planned'),diagnostic_only=True,performanceEvidence=False),indent=2))
    if result.get('status')=='failed':raise SystemExit(1)

if __name__=='__main__':main()
