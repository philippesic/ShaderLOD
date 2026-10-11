#!/usr/bin/env python3
"""Finite 26.2 Fabric MakeUp consumer. Default: preflight; --run explicitly launches.

Lifted from the authentic frozen r3 runner; existing source/options/Off/identity
checks remain. The recorded mode never launches. No other tuple is admitted.
"""
from pathlib import Path
from datetime import datetime,timezone,timedelta
import argparse,hashlib,importlib.util,json,os,shlex,subprocess,sys,threading,time
from qualify_renderer_bracket import qualify
R=Path(__file__).resolve().parents[1];Q=None;PROFILE=None
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()

def readiness_report(report,pack_name,installed_archive_matches):
    """Join actual early262 observations to an immutable owned archive; no nested snapshot required."""
    candidates=[report.get(key,{}) for key in ('firstWorldObservation','startupObservation')] if report else []
    observation=next((s for s in candidates if s.get('worldPresent') is True and s.get('playerPresent') is True),{})
    return {'in_world':observation.get('worldPresent') is True and observation.get('playerPresent') is True,
        'shader':installed_archive_matches and observation.get('renderBackend')=='OpenGL' and
            observation.get('irisSelectedPack')==observation.get('irisLoadedPack')==pack_name and
            observation.get('shaderPackInUse') is True and observation.get('irisFallback') is False and
            observation.get('coreEnabled') is False and observation.get('coreEnabledGate') is True,
        'fullscreen':observation.get('viewportGate') is True and observation.get('fullscreen') is True and
            observation.get('nativeMonitor',0)!=0 and
            observation.get('framebufferWidth')==observation.get('windowFramebufferWidth')==1920 and
            observation.get('framebufferHeight')==observation.get('windowFramebufferHeight')==1200}

def validate_prepared_profile(plan,freeze,dc):
    profile=Path(plan['profile'])
    assert plan['launcher']=='fabric262' and profile==PROFILE and profile.is_relative_to(R/'run-workloads')
    for name,expected in freeze['files'].items():assert sha(Path(name))==expected,'Prepared input drift:'+name
    config=json.loads((profile/'config/shaderlod-client.json').read_text());assert config['enabled'] is False and config['preset']=='PERFORMANCE'
    assert dc.properties((profile/'config/iris.properties').read_text())['shaderPack']==plan['pack_name']
    assert sha(Path(plan['installed_pack']))==plan['expected_loaded_sha256'] and not list((profile/'mods').glob('*.jar'))
    options=dict(line.split(':',1) for line in (profile/'options.txt').read_text().splitlines() if ':' in line)
    assert options['inactivityFpsLimit']=='"minimized"' and options['maxFps']=='260' and options['enableVsync']=='false'

def refreshed_launch_plan(saved):
    return {**saved,'stop_at_utc':(datetime.now(timezone.utc)+timedelta(seconds=360)).isoformat(),'duration_seconds':360}

def run_prepared():
    spec=importlib.util.spec_from_file_location('dc',R/'scripts/dev_client.py');d=importlib.util.module_from_spec(spec);spec.loader.exec_module(d)
    p=json.loads((Q/'plan.json').read_text());P=Path(p['profile']);freeze=json.loads((Q/'freeze.json').read_text())
    for name,expected in json.loads((Q/'supervision-freeze.json').read_text()).items():assert sha(Path(name))==expected,'Supervision input drift:'+name
    module=R/'ports/26.2/fabric'
    assert (module/'build/classes/java/devClient/com/pp/shaderlod/fabric/dev/GlfwStartup.class').is_file(),'Parent must compile exact new startup consumer'
    for name in ('fabric.mod.json','shaderlod-port-renderer-dev.mixins.json'):
        assert (module/'src/devClient/resources'/name).read_bytes()==(module/'build/resources/devClient'/name).read_bytes(),'Dev consumer drift:'+name
    assert json.loads((module/'build/resources/devClient/fabric.mod.json').read_text())['mixins']==['shaderlod-port-renderer-dev.mixins.json']
    def validate_port_built():
        for name,expected in json.loads((Q/'port-build-freeze.json').read_text()).items():assert sha(Path(name))==expected,'Source/package drift:'+name
    validate_port_built()
    for name,expected in freeze['files'].items():assert sha(Path(name))==expected,'Input drift:'+name
    assert not (Q/'owner.json').exists(),'This attempt is one-shot; preserve prior ownership/failure'
    assert not list((P/'shaderlod-dev').glob('fabric-roundtrip-*.json')) if (P/'shaderlod-dev').exists() else True
    quiet=json.loads((Q/'quiet.json').read_text());assert quiet['workersReleased'] and quiet['buildsReleased'] and quiet['exclusiveOwner']=='/root'
    p=refreshed_launch_plan(p)
    d.write_json(Q/'launch-plan.json',p)
    pre=subprocess.run([sys.executable,str(R/'scripts/check_experiment_plan.py'),str(Q/'launch-plan.json'),'--freeze',str(Q/'freeze.json')],capture_output=True,text=True)
    (Q/'preflight-launch.json').write_text(pre.stdout+pre.stderr);assert pre.returncode==0,pre.stdout+pre.stderr
    def report():
        paths=list((P/'shaderlod-dev').glob('fabric-roundtrip-*.json'));assert len(paths)<=1,'Ambiguous current receipt'
        if not paths:return None
        try:return json.loads(paths[0].read_text())
        except (json.JSONDecodeError,UnicodeDecodeError):return None # Java receipt replacement is not atomic.
    def readiness(*args,**kwargs):
        return readiness_report(report(),p['pack_name'],sha(Path(p['installed_pack']))==p['expected_loaded_sha256'])
    def prepared_profile_only(plan,backup):
        assert Path(plan['profile'])==P
        validate_prepared_profile(plan,freeze,d)
    d.readiness=readiness;d.validate_built=validate_port_built;d.prepare=prepared_profile_only
    assert p['explicit_env']=={'JAVA_HOME':str(R/'dev/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home'),'JAVA_TOOL_OPTIONS':'-XX:ActiveProcessorCount=2'}
    os.environ.update(p['explicit_env']);errors=[]
    def observe():
        focused=False;end=time.monotonic()+345
        try:
            while time.monotonic()<end:
                owner=json.loads((Q/'owner.json').read_text());current=report()
                if not focused and current and current.get('preworldNativeViewportAndFocusAccepted') is True:
                    # Source already performed the one native focus request before the private backup widget.
                    focused=True
                if not focused and owner.get('status')=='ready':
                    focused=True
                    result=subprocess.run([sys.executable,str(R/'scripts/dev_client_ui.py'),'--session',str(Q),'focus'],capture_output=True,text=True,timeout=20)
                    (Q/'focus.log').write_text(result.stdout+result.stderr);assert result.returncode==0,'Single exact-owned focus failed'
                if current and current.get('status') in ('complete-observations','failed'):
                    d.write_json(Q/'runtime-report.json',current)
                    if current['status']=='failed':errors.append(current.get('failure','Probe failed'))
                    d.stop(Q);return
                if owner.get('status') in ('startup-failed','startup-timeout','exited'):
                    errors.append(owner.get('error',owner['status']))
                    if owner.get('clients'):d.stop(Q)
                    return
                time.sleep(1)
        except Exception as error:
            errors.append(str(error));owner=json.loads((Q/'owner.json').read_text())
            if owner.get('clients'):d.stop(Q)
    d.write_json(Q/'owner.json',{'status':'pending','plan':p,'clients':{},'exclusiveOwner':'/root',
        'stop_command':[sys.executable,str(R/'scripts/dev_client.py'),'stop','--session',str(Q)]})
    thread=threading.Thread(target=observe);thread.start()
    print('START finite262Fabric originalMakeUp/privatecopy',p['stop_at_utc'],flush=True)
    d.supervise(Q);thread.join(timeout=25);assert not thread.is_alive(),'Observer unfinished'
    with d.runtime_lock():table=d.process_table();assert not d.clients(table)
    owner=json.loads((Q/'owner.json').read_text())
    assert not any(int(pid) in table and d.identity(table[int(pid)])==proof for pid,proof in owner.get('clients',{}).items())
    current=report()
    canonical=Path(freeze['canonicalWorldPath']);canonical_after={str(f.relative_to(canonical)):sha(f) for f in canonical.rglob('*') if f.is_file() and f.name!='session.lock'}
    assert canonical_after==freeze['canonicalWorld'],'Canonical world changed'
    d.write_json(Q/'release.json',{'observedUtc':datetime.now(timezone.utc).isoformat(),'zeroClients':True,'runtimeLockFree':True,
        'ownerStatus':owner.get('status'),'errors':errors,'reportStatus':current.get('status') if current else None,
        'canonical237Unchanged':True,'worldClonePreserved':Path(freeze['cloneWorldPath']).is_dir(),'gpuCompletionClaimed':False})
    qualified=qualified_report(current or {},Q/'runtime-report.json')
    if (Q/'runtime-report.json').is_file():qualified['rawReportSha256']=sha(Q/'runtime-report.json')
    d.write_json(Q/'measurement-qualification.json',qualified)
    assert not errors,errors
    assert current and current['status']=='complete-observations','No complete bracket'
    assert qualified['measurementValid'],qualified['errors']
    expected=json.loads((Q/'expected-options.json').read_text())
    for arm in ('original','on','off'):
        state=current[arm]
        assert state['irisSelectedPack']==state['irisLoadedPack']==p['pack_name'] and state['shaderPackInUse'] and not state['irisFallback']
        assert state['loadedIrisOptions']==expected and len(state['loadedIrisOptions'])==100 and state['loadedIrisOptions']['AA_TYPE']=='2'
        assert state['vsync'] is False and state['framerateLimit']==260 and state['vanillaRenderDistance']==24
        assert state['framebufferWidth']==1920 and state['framebufferHeight']==1200 and state['renderFrames']>0 and 30<=state['elapsedSeconds']<32
        assert state['rendererPortEligible']==(arm=='on') and state['originalArchiveUnchanged'] and state['originalArchiveSha256']==p['expected_loaded_sha256']
        assert state['configuredFramerateLimit']==state['effectiveFramerateLimit']==260 and state['framerateThrottleReason']=='NONE'
        assert state['inactivityFpsLimit']=='MINIMIZED' and state['rejectedMeasuredFrames']==0 and state['nativeWindowFocused'] is True and state['windowIconified'] is False
        assert state['glFunctionEntries']>0 and state['gpuCompletionProven'] is False and Path(state['screenshot']).is_file()
    assert current['on']['helperCalls']>0 and current['on']['reducedBatches']>0 and current['on']['copiedGlFunctionEntries']>0
    assert current['on']['inputCommands']>current['on']['outputCommands']
    assert all(current[arm]['helperCalls']==current[arm]['inputCommands']==current[arm]['outputCommands']==current[arm]['copiedGlFunctionEntries']==0 for arm in ('original','off'))
    assert current['off']['originalLoadedOptionsRestored'] and current['off']['originalCoreConfigRestored']
    print('COMPLETE',[(arm,current[arm]['meanElapsedFrameMs']) for arm in ('original','on','off')],flush=True)



PACK = 'MakeUp-UltraFast-9.5g.zip'
ARCHIVE_SHA = '0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8'


def qualified_report(report, producer):
    # Select from actual producer metadata, never from a user-invented version.
    result = qualify(report, report.get('minecraftTarget', 'missing actual minecraftTarget'))
    if report.get('minecraftTarget') != '26.2' or report.get('originalPack') != PACK or report.get('rendererSpeedProbe') is not True:
        result['errors'].append('No admitted finite consumer tuple: require actual 26.2 Fabric MakeUp9.5g renderer-speed report')
        result['measurementValid'] = False
    for arm in ('original','on','off'):
        state=report.get(arm,{})
        for key,expected in (('expectedConfiguredFramerateLimit',260),('rateUnavailableFrames',0)):
            if type(state.get(key)) is not int or state.get(key)!=expected:
                result['errors'].append(arm+': Missing or rejected actual observation: '+key)
                result['measurementValid']=False
    if 'rendererRejectedWindow' in report:
        result['rejectedWindow']=report['rendererRejectedWindow']
    if 'rendererRejectedRate' in report:
        result['firstRejectedRate']=report['rendererRejectedRate']
    result.update(consumer=str(Path(__file__).resolve()), producer=str(producer),
                  qualityAccepted=False, runtimeAdmissionInferred=False,
                  nextAction='Preserve fresh-client baseline diagnosis first; a later distinct port comparison needs a fresh prepared profile/build/source freeze and actual quality review. No closed candidate rerun.')
    return result


def cheap_boundary_errors(plan, session, profile):
    errors = []
    def require(ok, message):
        if not ok: errors.append(message)
    require(isinstance(plan, dict), 'Prepared plan must be an object')
    if not isinstance(plan, dict): return errors
    require(plan.get('launcher') == 'fabric262' and plan.get('pack_name') == PACK and plan.get('expected_loaded_sha256') == ARCHIVE_SHA,
            'Only actual 26.2/Fabric/MakeUp9.5g native100 tuple supported')
    require(profile.parts[-3:]==('run-workloads','port-262-runtime-r1','fabric') and profile==profile.resolve(),
            'Actual Java consumer requires nonsymlink private profile suffix run-workloads/port-262-runtime-r1/fabric')
    require(plan.get('profile') == str(profile) and profile.is_absolute() and profile.is_relative_to(R/'run-workloads'),
            'Explicit fresh profile must match saved actual plan under this checkout run-workloads')
    require(session.is_absolute() and session != profile and not session.is_relative_to(profile) and not profile.is_relative_to(session),
            'Session outputs and prepared profile must be separate absolute paths')
    for name in ('owner.json','runtime-report.json','release.json','measurement-qualification.json','launch-plan.json'):
        require(not (session/name).exists(), 'One-shot output already exists: '+name+'; preserve it and prepare a distinct attempt')
    require(plan.get('explicit_env') == {'JAVA_HOME':str(R/'dev/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home'),
                'JAVA_TOOL_OPTIONS':'-XX:ActiveProcessorCount=2'}, 'Actual explicit environment differs or is missing')
    argv=plan.get('command')
    require(isinstance(argv,list) and bool(argv) and all(isinstance(a,str) for a in argv), 'Actual prepared argv missing; no command synthesized')
    if isinstance(argv,list) and argv and all(isinstance(a,str) for a in argv):
        require(argv[0]==str(R/'ports/26.2/runtime/gradle-9.5.0/bin/gradle') and argv.count('runClient')==1,
                'Actual pinned launcher/runClient differs')
        require(argv.count('-p')==1 and any(argv[i:i+2]==['-p',str(R/'ports/26.2/fabric')] for i in range(len(argv)-1)),
                'Actual launcher project differs')
        args=[a[len('--args='):] for a in argv if a.startswith('--args=')]
        try:
            require(len(args)==1 and shlex.split(args[0])==['--gameDir',str(profile),'--username','ShaderLOD','--fullscreen',
                '--width','1920','--height','1200','--quickPlaySingleplayer','ShaderLOD-Landscape'],
                'Actual gameDir/native viewport/world arguments differ')
        except ValueError:errors.append('Malformed actual game arguments')
    require(plan.get('duration_seconds') == 360, 'Actual finite recipe requires 360-second limit')
    require(plan.get('exclusive_owner') == '/root', 'Finite recipe retains actual /root exclusive resource owner')
    for name in ('freeze.json','port-build-freeze.json','supervision-freeze.json','quiet.json','expected-options.json'):
        require((session/name).is_file(), 'Missing actual prepared input: '+name)
    require(not list((profile/'shaderlod-dev').glob('fabric-roundtrip-*.json')), 'Prepared profile already has runtime receipts')
    return errors


def preflight(session, profile):
    result = dict(consumer=str(Path(__file__).resolve()), supportedTuple='26.2/Fabric/MakeUp-UltraFast9.5g/native100',
                  launchPerformed=False, errors=[], checked=[],
                  nextAction='Finish bounded fresh-client native baseline diagnosis; do not reopen closed MakeUp/BSL candidates.',
                  unverified=['current exclusive client/build owner and quiet-window release',
                              'actual Java callback/driver behavior of new fields', 'fresh consumer source/build admission',
                              'stills/motion quality and measured runtime restoration', 'causal speed; no rate result proves gain'])
    try:
        plan=json.loads((session/'plan.json').read_text())
    except (OSError, ValueError) as error:
        result['errors'].append(str(error)); return result
    result['errors'] = cheap_boundary_errors(plan,session,profile)
    # Collect independent metadata failures before hashing archives/worlds/source.
    if result['errors']: return result
    result['actualCommand']=plan['command'];result['actualEnvironment']=plan.get('explicit_env')
    def check(name, operation):
        try: operation();result['checked'].append(name)
        except (AssertionError,OSError,ValueError,KeyError,TypeError) as error:
            result['errors'].append(name+': '+str(error))
    import dev_client as dc
    def hashes(name):
        value=json.loads((session/name).read_text())
        assert isinstance(value,dict) and value, 'Empty freeze'
        for path,digest in value.items():assert sha(Path(path))==digest,'Drift: '+path
    def supervision():
        frozen=json.loads((session/'supervision-freeze.json').read_text())
        for path in (Path(__file__).resolve(),R/'scripts/qualify_renderer_bracket.py'):
            assert str(path) in frozen,'Fresh supervision freeze must include '+str(path)
        hashes('supervision-freeze.json')
    check('consumer/qualifier supervision freeze',supervision)
    def built_freeze():
        frozen=json.loads((session/'port-build-freeze.json').read_text())
        module=R/'ports/26.2/fabric'
        names=('FabricRoundtrip','GlfwStartup','PortRendererFrames','PortRendererRateChecks','mixin/PortRendererFramesMixin')
        for name in names:
            for prefix,suffix in (('src/devClient/java','.java'),('build/classes/java/devClient','.class')):
                path=module/prefix/'com/pp/shaderlod/fabric/dev'/(name+suffix)
                assert str(path) in frozen,'Missing actual source/compiled role: '+str(path)
        for name in ('fabric.mod.json','shaderlod-port-renderer-dev.mixins.json'):
            for directory in ('src/devClient/resources','build/resources/devClient'):
                path=module/directory/name
                assert str(path) in frozen,'Missing actual metadata role: '+str(path)
        hashes('port-build-freeze.json')
    check('source/build/package freeze',built_freeze)
    def inputs():
        freeze=json.loads((session/'freeze.json').read_text())
        validate_prepared_profile(plan,freeze,dc)
        assert freeze['cloneWorldPath']==str(profile/'saves'/dc.WORLD)
        assert Path(freeze['canonicalWorldPath']) != Path(freeze['cloneWorldPath'])
        assert freeze['canonicalWorld'] == freeze['cloneWorld'] and freeze['canonicalWorld']
        expected=json.loads((session/'expected-options.json').read_text())
        assert len(expected)==100 and expected['AA_TYPE']=='2'
    check('prepared profile/native100/isolated world',inputs)
    def quiet():
        value=json.loads((session/'quiet.json').read_text())
        assert value['workersReleased'] is True and value['buildsReleased'] is True and value['exclusiveOwner']=='/root'
    check('saved quiet release (live census remains required)',quiet)
    from check_experiment_plan import check_plan
    # A deadline is created only at actual run; no stored expired deadline is renewed in this read-only check.
    static=check_plan(plan,freeze=json.loads((session/'freeze.json').read_text()),historical=True)
    result['checked'].extend(static['checked'])
    result['errors'].extend(str(error) for error in static['errors'])
    result['readyForExplicitRun']=not result['errors']
    return result


def write_new(path, result):
    with path.open('x') as stream: json.dump(result,stream,indent=2);stream.write('\n')


def main():
    global Q,PROFILE
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--session',type=Path)
    parser.add_argument('--profile',type=Path)
    parser.add_argument('--recorded-report',type=Path,help='Qualify authentic recorded262 report only; never launch')
    parser.add_argument('--output',type=Path,help='New result file; existing files are refused')
    parser.add_argument('--run',action='store_true',help='Explicit finite launch after preflight, by acknowledged resource owner')
    args=parser.parse_args()
    if args.run and args.output:parser.error('--run stores results in the owned session; --output is for read-only modes')
    if args.recorded_report:
        if args.run or args.session or args.profile:parser.error('Recorded qualification cannot mix launch inputs')
        report=json.loads(args.recorded_report.read_text())
        result=qualified_report(report,args.recorded_report)
        result['rawReportSha256']=sha(args.recorded_report)
        result['recordedOnly']=True
    else:
        if not args.session or not args.profile:parser.error('Provide actual prepared --session and --profile, or --recorded-report')
        Q=args.session;PROFILE=args.profile
        result=preflight(Q,PROFILE)
        if args.run:
            if result['errors']:
                print(json.dumps(result,indent=2));return 1
            # Admission and process identity are rechecked by the inherited real supervisor.
            run_prepared();return 0
    if args.output:write_new(args.output,result)
    else:print(json.dumps(result,indent=2))
    return 0 if result.get('measurementValid',result.get('readyForExplicitRun',False)) else 1


if __name__=='__main__':raise SystemExit(main())
