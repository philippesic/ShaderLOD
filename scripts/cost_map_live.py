"""Bounded native diagnostic launch, using the maintained exact-process owner and CAS backups."""
import hashlib, json, os, subprocess, sys, time
from pathlib import Path
from types import SimpleNamespace
import dev_client as dev
import experiment_lifecycle as life
import run_storage as storage
import benchmark_iteration as iteration
ROOT=Path(__file__).resolve().parents[1]


def run(a):
    if not os.environ.get('SHADERLOD_ARM64_RUNTIME_READY'):
        os.execv(sys.executable,[sys.executable,str(ROOT/'scripts/linux_arm64_runtime.py'),'benchmark',*sys.argv[1:]])
    import headless_display
    env=headless_display.environment(dict(os.environ))
    profile=a.profile.resolve();output=a.output.resolve()
    if not 1<=a.seconds<=30:raise ValueError('Live smoke window must be 1..30 seconds')
    if a.dh_transition_check and not a.dh:raise ValueError('DH transition check requires --dh')
    storage.admit(profile,64*1024**2)
    if dev.clients(dev.process_table()):raise ValueError('Another client owns the machine')
    runtime=json.loads((ROOT/'dependencies/runtime.lock.json').read_text())['artifacts']['distanthorizons']
    dh_target=profile/'mods'/runtime['filename']
    stage_dh=a.dh and not dh_target.exists()
    if stage_dh and hashlib.sha512((ROOT/'dependencies/runtime'/runtime['filename']).read_bytes()).hexdigest()!=runtime['hashes']['sha512']:raise ValueError('Pinned DH artifact mismatch')
    if a.world!='ShaderLOD-Benchmark-overworld-3-flat':raise ValueError('Live smoke currently owns the prepared authored save only')
    control=output/'control.json'
    # plan() requires an existing neutral control, before output/backup creation.
    import tempfile
    with tempfile.TemporaryDirectory(prefix='cost-map-plan-') as tmp:
        stub=Path(tmp)/'control.json';stub.write_text('{}')
        args=SimpleNamespace(mode='on',profile=profile,pack=a.pack,pack_options=None,dh=a.dh and not stage_dh,world=a.world,
            preset=None,already_built=False,ready_timeout=180,benchmark_control_probe=stub,benchmark_tuner_probe=None)
        plan=dev.plan(args)
    if stage_dh:
        plan['include_dh']=True
        plan['command']=[x.replace('-PincludeDh=false','-PincludeDh=true') for x in plan['command']]
    import zipfile
    with zipfile.ZipFile(plan['pack']) as archive:
        if 'shaderlod-selection.json' in archive.namelist():raise ValueError('Native cost mapping requires an original pack')
    plan['benchmark_control_probe']=str(control)
    plan['command']=[x.replace(str(stub),str(control)) for x in plan['command']]
    plan['command']+=['-PdhDrawEvidence=true','-PmanualProfiling=true','-PcostMap=true','-PcpuTiming=true','-PgpuTiming=true','-PgpuPassTiming=true','-PgpuAttribution=true','-PgpuSampleInterval=4','-PsavedAuthoredScene=true']
    op=life.Operation.create(output,profile,plan,life.configuration_paths(profile,plan['pack'])+([dh_target] if stage_dh else []))
    iteration.prepare_profile(op)
    session=output/'session';session.mkdir()
    life.atomic_json(control,{'request_id':'initial_status','action':'status'})
    dev.write_json(session/'owner.json',dict(status='pending',plan=plan,clients={}))
    sequence=0
    def send(action,**kw):
        nonlocal sequence
        sequence+=1;identity='live_'+str(sequence)
        life.atomic_json(control,dict(request_id=identity,action=action,**kw))
        receipt=output/'receipts'/(identity+'.json');deadline=time.monotonic()+90
        while time.monotonic()<deadline:
            if receipt.exists():
                data=json.loads(receipt.read_text())
                if data.get('status')!='PENDING':
                    if not data.get('success'):raise ValueError(str(data))
                    return data
            owner=dev.read_published_json(session/'owner.json')
            if owner.get('status') in ('exited','startup-failed','startup-timeout'):raise ValueError(str(owner))
            time.sleep(.25)
        raise TimeoutError(action)
    completed=False
    proc=None
    op.begin_mutation('live-runtime')
    try:
        if stage_dh:
            dh_target.parent.mkdir(exist_ok=True)
            storage.clone_file(ROOT/'dependencies/runtime'/runtime['filename'],dh_target)
        with (output/'supervisor.log').open('x') as log:
            proc=subprocess.Popen([sys.executable,str(ROOT/'scripts/dev_client.py'),'_supervise','--session',str(session)],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        deadline=time.monotonic()+180
        while not (output/'receipts/initial_status.json').exists():
            owner=dev.read_published_json(session/'owner.json')
            if owner.get('status') in ('startup-failed','startup-timeout','exited'):raise ValueError(str(owner))
            if time.monotonic()>deadline:raise TimeoutError('initial client receipt')
            time.sleep(.5)
        send('open_saved_authored')
        deadline=time.monotonic()+100
        while True:
            state=send('status')['state']
            if state.get('world_loaded') and state.get('meshes_ready'):break
            if time.monotonic()>deadline:raise TimeoutError('world/mesh readiness')
            time.sleep(1)
        if (state.get('width'),state.get('height'))!=(2560,1600) or not state.get('fullscreen'):raise ValueError('Native 2560x1600 fullscreen required')
        if any(x in state.get('gpu_renderer','').lower() for x in ('llvmpipe','softpipe','software')):raise ValueError('Software renderer refused')
        send('cost_map',op='start');time.sleep(a.seconds/2)
        send('reload');time.sleep(a.seconds/2)
        if a.dh_transition_check:
            send('cost_map',op='dh_rendering',enabled=False);time.sleep(2)
            send('cost_map',op='dh_rendering',enabled=True);time.sleep(2)
        send('cost_map',op='stop')
        from cost_map import export
        captures=sorted((output/'live-cost-map').glob('cohort-*/capture.json'))
        if not captures:raise ValueError('No live canonical capture')
        records=[json.loads(p.read_text()) for p in captures]
        cpu=sum(e.get('cpu',{}).get('sampling',{}).get('status')=='MEASURED' for d in records for e in d['events'] if e.get('cpu'))
        gpu=sum(e.get('gpu',{}).get('availability',{}).get('status')=='MEASURED' for d in records for e in d['events'] if e.get('gpu'))
        if not cpu or not gpu:raise ValueError('Live CPU and GPU hooks must both resolve samples')
        if len({d['manifest']['graph_generation'] for d in records})<2:raise ValueError('Reload did not create a new graph generation')
        if a.dh_transition_check and 'INSTALLED_RENDERING_DISABLED' not in {d['manifest']['dh_state'] for d in records}:raise ValueError('DH setting transition was not captured')
        for capture in captures:export(json.loads(capture.read_text()),capture.parent/'export')
        completed=True
        life.atomic_json(output/'live-result.json',{'launched':True,'captures':[str(p) for p in captures],'state':state,'scope':'bounded functional smoke; not performance qualification'})
    finally:
        owner=dev.read_published_json(session/'owner.json')
        if owner.get('clients'):
            try:send('cost_map',op='stop')
            except Exception:pass
            dev.stop(session)
            dev.retire_source_launcher(session)
        elif owner.get('launcher'):
            dev.retire_source_launcher(session)
        elif proc is not None:
            raise RuntimeError('Supervisor did not publish launcher ownership; retain operation for recovery')
        if proc is not None:proc.wait(timeout=30)
        # These are runtime-owned writes under the exclusive process lane; backups precede launch.
        op.acknowledge_mutation('live-runtime')
        op.restore();op.data['release_verified']=True;op.data['configuration_restored']=True;op.save()
        iteration.finish_profile(op)
        op.data['state']='complete' if completed else 'failed';op.save()
    return 0
