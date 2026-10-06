#!/usr/bin/env python3
"""Run at most four frozen spatial controls sequentially, without timing or builds."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time

import prepare_shared_spatial_runtime as prep
import run_shared_lod_benchmark as runtime

ROOT=prep.ROOT
JAR_SHA256='0d499564c24d652c782a5c0e6b26fbae4a93b323542c5a4c58bbeee58da8ae9f'
require=prep.require
write=prep.write


def guard_profile(expected):
    profile=Path(expected['profile'])
    require(expected['command']==prep.command(profile),'Held command drift')
    require(expected['request']['diagnostic_only'] is True and expected['request']['performanceEvidence'] is False,
            'Diagnostic request missing')
    require(all(prep.file_hash(profile/f)==pin for f,pin in expected['input_files_sha256'].items()),'Prepared input drift')
    require(prep.file_hash(Path(expected['source_registration']))==expected['source_registration_sha256'],'Registration drift')
    require(prep.world_fingerprint(profile/'saves'/prep.WORLD)==prep.WORLD_SHA256,'Fresh world drift')
    require(not (profile/'workload-measurement.json').exists(),'Stale report; no automatic retry')
    return prep.matrix.profile_inputs(profile)


def probe_summary(probe):
    require(probe.get('schema')==1 and probe.get('capability_uniform_schema')==1 and probe.get('programs'),'Probe missing/schema mismatch')
    rows=[]
    for entry in probe['programs']:
        require(not entry.get('capture_failure'),'Probe capture failure')
        if 'active_texture_before' in entry:
            require(entry.get('active_texture_after')==entry['active_texture_before'],'Active texture restoration mismatch')
        rows.append({k:entry.get(k) for k in ('current_program','timestamp_utc','draw_origin','linked',
            'uniform_scan_truncated','uniform_name_length_truncated','core_uniforms','effect_uniforms','capability_uniforms')})
    return dict(diagnostic_only=True,performanceEvidence=False,programs=rows,
        hardware={k:probe.get(k) for k in ('gl_version','gl_renderer','gl_vendor','glsl_version','max_combined_texture_units')},
        stage_mapping='unverified; numeric program and hook origin alone do not identify a consuming stage',
        settled_availability='unverified; first observed draw only',physical_layout='unverified; no attachment/completion capture')


def process_identity(pid):
    # Start time rejects ordinary PID reuse even when the command is identical.
    row=subprocess.run(['ps','-p',str(pid),'-o','pid=,ppid=,pgid=,lstart=,command='],capture_output=True,text=True).stdout.strip()
    return row or None


def prove_client_ownership(pid, profile):
    """Require a stable live identity plus narrow OS evidence for this unique arm."""
    profile=Path(profile).resolve(); before=process_identity(pid)
    record=dict(pid=pid,profile=str(profile),identity=before,status='ambiguous',evidence=[])
    if before is None:
        record.update(status='exited',reason='Process absent before profile association');return record
    commands=[['/usr/sbin/lsof','-a','-p',str(pid),'-d','cwd','-Fn'],
              ['/usr/sbin/lsof','-a','-p',str(pid),'-Fn','--',str(profile/'logs/latest.log'),
               str(profile/'saves'/prep.WORLD/'session.lock')]]
    known_paths={str(profile),str(profile/'logs/latest.log'),str(profile/'saves'/prep.WORLD/'session.lock')}
    matched=False
    for index,command in enumerate(commands):
        try:
            result=subprocess.run(command,capture_output=True,text=True,timeout=3)
            lines=result.stdout.splitlines()
            # The -p selection and explicit p record must both name this PID.
            paths=[line[1:] for line in lines if line.startswith('n')]
            selected_pids=[line[1:] for line in lines if line.startswith('p')]
            record['evidence'].append(dict(command=command,returncode=result.returncode,
                selected_pids=selected_pids,paths=paths))
            allowed={str(profile)} if index==0 else known_paths-{str(profile)}
            if result.returncode==0 and selected_pids==[str(pid)] and any(path in allowed for path in paths):
                matched=True;record['association']='cwd' if index==0 else 'open_exact_profile_log_or_world_lock';break
        except (OSError,subprocess.TimeoutExpired) as error:
            record['evidence'].append(dict(command=command,error=str(error)))
    after=process_identity(pid)
    if after is None:record.update(status='exited',reason='Process ended during ownership check')
    elif after!=before:record.update(reason='Process identity changed during ownership check',identity_after=after)
    elif matched:record['status']='owned'
    else:record['reason']='No stable association with this arm; foreign or unavailable OS evidence'
    return record


def discover_clients(profile, owned, events):
    """A global candidate is never ownership evidence by itself."""
    for pid in prep.matrix.minecraft_processes():
        previous=owned.get(pid)
        if previous is not None and process_identity(pid)==previous['identity']:continue
        proof=prove_client_ownership(pid,profile);events.append(proof)
        if proof['status']=='exited':continue
        # A reused PID may not inherit the old process's authority, even if it
        # now happens to have an association with our profile.
        require(previous is None,'Previously owned Java PID reused/identity changed: '+str(pid))
        require(proof['status']=='owned','Foreign or ambiguous Minecraft client: '+str(pid))
        owned[pid]=proof


def signal_owned_clients(owned, signum, events):
    """Reprove identity and profile association immediately before each signal."""
    for pid,previous in owned.items():
        if process_identity(pid)!=previous['identity']:
            events.append(dict(pid=pid,status='not_signaled',reason='Exited or reused process identity'));continue
        proof=prove_client_ownership(pid,previous['profile']);events.append(proof)
        if proof['status']!='owned' or proof['identity']!=previous['identity']:
            events.append(dict(pid=pid,status='not_signaled',reason='Live profile ownership no longer proven'));continue
        try:os.kill(pid,signum);events.append(dict(pid=pid,status='signaled',signal=int(signum)))
        except ProcessLookupError:events.append(dict(pid=pid,status='exited_before_signal'))


def run(profiles, output):
    profiles=Path(profiles).resolve(); output=runtime.ignored_path(output,ROOT/'dev/logs')
    state=json.loads((profiles/'shared-spatial-preparation.json').read_text())
    require(state['status']=='complete' and list(state['operation']['arms'])==list(prep.ARMS),'Prepared sequence drift')
    require(prep.qualify()['artifacts']==state['artifacts'],'Source freeze drift')
    with (ROOT/'dev/logs/.proof-matrix.lock').open('a+') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB);prep.matrix.require_exclusive_runtime()
        frozen=prep.matrix.development_fingerprints()
        require(prep.file_hash(ROOT/'build/libs/shaderlod-0.1.0-dev.jar')==JAR_SHA256,'Production JAR changed')
        for expected in state['arms'].values(): guard_profile(expected)
        output.mkdir(); marker=output/'shared-spatial-terminal-audit.json'
        audit=dict(schema=1,status='running',diagnostic_only=True,performanceEvidence=False,runner_pid=os.getpid(),
            runner_session=os.getsid(0),owner='spatial_runtime_preparation',created_utc=prep.matrix.utc_now(),
            preparation=str(profiles/'shared-spatial-preparation.json'),preparation_sha256=prep.file_hash(profiles/'shared-spatial-preparation.json'),
            development_runtime_inputs=frozen,production_jar_sha256=JAR_SHA256,
            git_head=subprocess.run(['git','rev-parse','HEAD'],cwd=ROOT,capture_output=True,text=True,check=True).stdout.strip(),
            host=dict(os=subprocess.run(['sw_vers'],capture_output=True,text=True,check=True).stdout.strip(),
                kernel=subprocess.run(['uname','-a'],capture_output=True,text=True,check=True).stdout.strip()),runs=[])
        audit['client_ownership_events']=[]
        write(marker,audit); process=None; watcher=None; owned={}; row=None
        try:
            # JSON keys are sorted on disk; explicit reviewed order is authoritative.
            for index,arm in enumerate(prep.ARMS,1):
                expected=state['arms'][arm]; profile=Path(expected['profile'])
                prep.matrix.require_exclusive_runtime(); inputs=guard_profile(expected)
                require(prep.matrix.development_fingerprints()==frozen,'Frozen classes/resources changed')
                console=output/(f'{index:02d}-'+arm+'-console.log'); started_ns=time.time_ns()
                row=dict(arm=arm,status='running',request=expected['request'],command=expected['command'],
                    started_utc=prep.matrix.utc_now(),console=str(console),java_process_identities={},java_process_ownership={},
                    stop_procedure=expected['stop_procedure'],diagnostic_only=True,performanceEvidence=False)
                audit['runs'].append(row);write(marker,audit)
                watcher=runtime.arm_focus(console,output/(arm+'-warmup-focus.json'))
                with console.open('w') as log:
                    process=subprocess.Popen(expected['command'],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                    row.update(launcher_pid=process.pid,launcher_session=os.getsid(process.pid),launcher_identity=process_identity(process.pid))
                    write(marker,audit);print('START '+arm+' UUID='+expected['request']['run_id']+' launcherPID='+str(process.pid),flush=True)
                    deadline=time.monotonic()+240
                    while process.poll() is None:
                        try:discover_clients(profile,owned,audit['client_ownership_events'])
                        finally:
                            for pid,proof in owned.items():
                                if proof['profile']==str(profile.resolve()):
                                    row['java_process_identities'][str(pid)]=proof['identity'];row['java_process_ownership'][str(pid)]=proof
                            write(marker,audit)
                        require(time.monotonic()<deadline,'Client deadline reached')
                        require(not prep.matrix.FATAL_LOG.findall(console.read_text(errors='replace')),'Driver/runtime error; see '+str(console))
                        time.sleep(.5)
                    row['exit_code']=process.returncode;require(process.returncode==0,'Launcher failed');process=None
                watcher[1].join(2);row['warmup_focus']=watcher[2];require(watcher[2].get('status')=='complete','Warmup focus guard failed');watcher=None
                prep.matrix.require_exclusive_runtime()
                require(prep.matrix.development_fingerprints()==frozen,'Frozen runtime changed during launch')
                report_path=profile/'workload-measurement.json'
                require(report_path.stat().st_mtime_ns>=started_ns,'Stale measurement timestamp')
                report=json.loads(report_path.read_text());write(output/(arm+'-workload-measurement.json'),report)
                require(not prep.matrix.FATAL_LOG.findall(console.read_text(errors='replace')),'Runtime shader error')
                row['workload_validation']=prep.validate_report(report,expected,inputs['control'])
                require(report['control_sha256']==inputs['control_sha256'],'Control drift')
                require(row['warmup_focus']['utc']<report['measurement_started_at_utc'],'Focus changed during capture')
                prep.matrix.validate_optional_stack(report,'minimal',json.loads((ROOT/'dev/performance-mods.lock.json').read_text()))
                for mod in ('minecraft','neoforge','iris','sodium'):
                    require(report['loaded_mods'].get(mod)==state['source_loaded_mods'][mod],'Component version drift: '+mod)
                row.update(prep.matrix.preserve_screenshot(profile,output,arm,started_ns))
                probe_path=profile/'logs/cloud-sampler-state.json';require(probe_path.stat().st_mtime_ns>=started_ns,'Stale probe timestamp')
                probe=json.loads(probe_path.read_text());write(output/(arm+'-cloud-sampler-state.json'),probe)
                row['probe_summary']=probe_summary(probe)
                row.update(loaded_mods=report['loaded_mods'],loaded_mod_artifacts=report['loaded_mod_artifacts'],
                    runtime_properties=report['runtime_properties'],status='complete',finished_utc=prep.matrix.utc_now())
                if index>1:
                    require(row['loaded_mods']==audit['runs'][0]['loaded_mods'] and row['loaded_mod_artifacts']==audit['runs'][0]['loaded_mod_artifacts'],
                            'Cross-arm component artifact identity drift')
                write(marker,audit);print('COMPLETE '+arm+' no fallback; exact options/window/input guards passed',flush=True)
            audit['status']='complete'
        except BaseException as error:
            audit.update(status='failed',error=str(error))
            if row is not None:row.update(status='failed',error=str(error))
            print('STOP '+str(error),flush=True)
        finally:
            if watcher:watcher[0].set();watcher[1].join(2)
            # A late-created child still requires the same explicit profile
            # proof. Foreign/ambiguous clients are retained and never signaled.
            if row:
                profile=Path(state['arms'][row['arm']]['profile'])
                try:discover_clients(profile,owned,audit['client_ownership_events'])
                except ValueError as error:audit.update(status='failed',cleanup_ownership_error=str(error))
                for pid,proof in owned.items():
                    if proof['profile']==str(profile.resolve()):
                        row['java_process_identities'][str(pid)]=proof['identity'];row['java_process_ownership'][str(pid)]=proof
            signal_owned_clients(owned,signal.SIGTERM,audit['client_ownership_events'])
            if process is not None and process.poll() is None:
                os.killpg(process.pid,signal.SIGTERM)
                try:process.wait(timeout=20)
                except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
            deadline=time.monotonic()+20
            while any(process_identity(pid)==proof['identity'] for pid,proof in owned.items()) and time.monotonic()<deadline:time.sleep(.5)
            signal_owned_clients(owned,signal.SIGKILL,audit['client_ownership_events'])
            if row:
                profile=Path(state['arms'][row['arm']]['profile'])
                for path,suffix in ((profile/'workload-measurement.json','workload-measurement.json'),
                                    (profile/'logs/cloud-sampler-state.json','cloud-sampler-state.json')):
                    if path.exists():shutil.copy2(path,output/(row['arm']+'-'+suffix))
                screenshot=profile/'screenshots/workload-final.png'
                if screenshot.exists():shutil.copy2(screenshot,output/(row['arm']+'-final.png'))
            audit.update(finished_utc=prep.matrix.utc_now(),remaining_minecraft_pids=prep.matrix.minecraft_processes(),
                         frozen_runtime_unchanged=prep.matrix.development_fingerprints()==frozen)
            if audit['remaining_minecraft_pids']:
                audit.update(status='failed',cleanup_remaining_clients='Live client identities remain; ownership must be reviewed, never broadly stopped')
            write(marker,audit)
        return audit


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--profiles',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();result=run(args.profiles,args.output)
    if result['status']!='complete' or result['remaining_minecraft_pids']:raise SystemExit(1)

if __name__=='__main__':main()
