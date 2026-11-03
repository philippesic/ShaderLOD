#!/usr/bin/env python3
"""Read-only batch checks for existing dev_client plan.json or owner.json records.

No preparation, process inspection, Java, Gradle, UI or launch is performed.
Passing means static consistency only; historical inspection is never admission.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shlex
import sys
import tomllib

import dev_client as dc


def check_plan(record, *, freeze=None, receipt=None, historical=False,
               check_built=False, now=None):
    errors, checked = [], []

    def check(name, operation):
        try:
            operation()
            checked.append(name)
        except (ValueError, OSError, KeyError, TypeError, AttributeError, dc.zipfile.BadZipFile) as error:
            errors.append({'check': name, 'error': str(error)})

    def require(ok, message):
        dc.require(ok, message)

    def file(value):
        require(isinstance(value, str) and bool(value), 'Expected nonempty file path')
        path = Path(value)
        require(path.is_absolute() and path.is_file(), 'Missing absolute file: ' + value)
        return path

    def read_json(value):
        data = json.loads(file(value).read_text())
        require(isinstance(data, dict), 'Expected JSON object: ' + value)
        return data

    def finite(value):
        return isinstance(value, (float, int)) and not isinstance(value, bool) and math.isfinite(value)

    if not isinstance(record, dict):
        return {'errors': [{'check': 'schema', 'error': 'Expected JSON object'}], 'checked': []}
    p = record.get('plan', record)
    if not isinstance(p, dict):
        return {'errors': [{'check': 'schema', 'error': 'Expected plan object'}], 'checked': []}
    check('mode', lambda: require(p.get('mode') in ('on', 'off'), 'mode must be on/off'))
    launcher = p.get('launcher', 'neoforge1211')
    check('launcher', lambda: require(launcher in ('neoforge1211', 'fabric1211', 'fabric12111', 'fabric2612', 'fabric262', 'neoforge262'),
                                     'Unknown explicit launcher adapter'))
    for key in ('fullscreen', 'include_dh', 'already_built', 'apply_lod_settings'):
        check(key, lambda key=key: require(type(p.get(key)) is bool, key + ' must be boolean'))
    check('ready_timeout', lambda: require(finite(p.get('ready_timeout')) and
          1 <= p['ready_timeout'] <= 300, 'ready_timeout must be 1..300 seconds'))

    def deadline():
        require(isinstance(p.get('stop_at_utc'), str), 'Finite probe requires stop_at_utc ISO timestamp')
        end = datetime.fromisoformat(p['stop_at_utc'].replace('Z', '+00:00'))
        require(end.tzinfo is not None and end.utcoffset() is not None, 'Deadline needs timezone')
        if not historical:
            remaining = (end - (now or datetime.now(timezone.utc))).total_seconds()
            require(0 < remaining <= 43200, 'Deadline must be in next 12 hours')
            require(finite(p.get('ready_timeout')) and remaining > p['ready_timeout'] + 10,
                    'Deadline leaves no readiness plus 10-second teardown margin')
    check('finite_deadline', deadline)

    def profile():
        root = Path(p['profile'])
        require(root.is_absolute() and root.is_dir(), 'Profile missing/not absolute')
        world = p.get('world', dc.WORLD)
        require(isinstance(world, str) and re.fullmatch(r'[A-Za-z0-9_-]{1,64}', world),
                'Safe saved-world name required')
        require(world == dc.WORLD or p.get('benchmark_control_probe'),
                'Custom world requires neutral control')
        file(str(root / 'saves' / world / 'level.dat'))
        file(str(root / 'config/iris.properties'))
    check('profile_world_iris', profile)
    check('shader_archive', lambda: require(dc.inspect_pack(file(p['pack'])) == p['archive_descriptor'],
                                          'archive_descriptor differs from actual ZIP'))
    check('pack_paths', lambda: require(Path(p['pack']).name == p['pack_name'] and
          Path(p['installed_pack']) == Path(p['profile']) / 'shaderpacks' / p['pack_name'],
          'pack_name/installed_pack disagree with source/profile'))
    if p.get('pack_options') is not None:
        check('pack_options', lambda: file(p['pack_options']).read_text())
    if p.get('apply_lod_settings'):
        check('settings_apply', lambda: require(p.get('mode') == 'on' and p.get('lod_state') in ('on', 'off'),
                                               'Settings Apply requires On task and lod_state on/off'))

    def fabric262_bsl_control_command(argv):
        module=dc.ROOT/'ports/26.2/fabric';root=Path(p['profile'])
        makeup=p.get('makeup262VolumeControl') is True
        require(not (makeup and p.get('bsl262VolumeControl') is True),'Distinct BSL/MakeUp262 selectors required')
        pack_name='MakeUp-UltraFast-9.5g.zip' if makeup else 'BSL_v10.1.8.zip'
        selector='makeup-volume' if makeup else 'bsl-fallback'
        require(p['mode']=='on' and p['fullscreen'] and not p['include_dh'] and not p['apply_lod_settings'] and p['already_built'], 'BSL262 control native runtime required')
        require((p.get('makeupVolumeControl') is True and p.get('bslFallbackControl') is False if makeup else p.get('bslFallbackControl') is True) and p.get('bslVolumeControl') is False and p.get('fullQualityRoundtrip') is False,
                'Explicit fourstage BSL262 control schema required')
        require(argv.count('-p')==1 and any(argv[i:i+2]==['-p',str(module)] for i in range(len(argv)-1)) and argv.count('runClient')==1,'Exact BSL262 control project required')
        props,skips,args,inits={},[],[],[];i=1
        while i<len(argv):
            value=argv[i]
            if value=='-p':i+=2;continue
            if value in ('-x','-I'):require(i+1<len(argv),'Missing BSL262 skip/init');(skips if value=='-x' else inits).append(argv[i+1]);i+=2;continue
            if value.startswith('-P'):
                key,sep,val=value[2:].partition('=');require(sep and key not in props,'Duplicate/missing control property');props[key]=val
            elif value.startswith('--args='):args.append(shlex.split(value.split('=',1)[1]))
            else:require(value in ('runClient','--no-daemon','--max-workers=2','--console=plain'),'Unknown control argument')
            i+=1
        stamp=props.get('portStartupDeadlineEpochMillis','');require(stamp.isdigit() and int(stamp)==int(datetime.fromisoformat(p['stop_at_utc']).timestamp()*1000) and int(stamp)>int((now or datetime.now(timezone.utc)).timestamp()*1000),'Paired BSL262 control deadline required')
        require(props=={'portRoundtrip':'true','portNativeFamily':selector,'portRoundtripOriginal':pack_name,'portQualityRoundtrip':'false','portRoundtripUi':'true','portRunDirectory':p['profile'],'portStartupDeadlineEpochMillis':stamp},'Exact BSL262 controls only flags required')
        require(args==[['--gameDir',p['profile']]] and len(skips)==4 and set(skips)==set(dc.SKIPPABLE),'Actual DSL gameDir/four skips required')
        require(root==root.resolve() and root.parts[-3:]==('run-workloads','port-262-runtime-r1','fabric') and p['duration_seconds']==330 and p['roundtripStages']==['original','on','quality','off'],'BSL262 finite private profile/stages required')
        require(p['explicit_env']=={'JAVA_HOME':str(dc.ROOT/'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home'),'JAVA_TOOL_OPTIONS':'-XX:ActiveProcessorCount=2'},'BSL262 pinned JDK invocation required')
        require(p['pack_name']==pack_name and p['expected_loaded_sha256']==('0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8' if makeup else '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5') and all(hashlib.sha256(file(p[k]).read_bytes()).hexdigest()==p['expected_loaded_sha256'] for k in ('pack','installed_pack')),'Exact BSL archive required')
        native_path=dc.ROOT/('src/main/resources/assets/shaderlod/makeupquad/native100.json' if makeup else 'src/main/resources/assets/shaderlod/bslquad/native384.json');native=read_json(str(native_path));require(hashlib.sha256(native_path.read_bytes()).hexdigest()==('0c11f31b3d5e51d9d1bff56a84ac13a3f1265472f7313997a493c74435a418bb' if makeup else '0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394') and len(native)==p['expected_options_count']==(100 if makeup else 384) and (native['AA_TYPE']=='2' if makeup else native['AURORA']=='0') and read_json(p['expected_options_source'])==native and all(dc.properties(file(path).read_text())==native for path in (p['pack_options'],p['installed_pack']+'.txt')),'Complete native384/Aurora0 control inputs required')
        iris=dc.properties(file(str(root/'config/iris.properties')).read_text());cfg=read_json(str(root/'config/shaderlod-client.json'));sodium=read_json(str(root/'config/sodium-options.json'))
        require(iris.get('shaderPack')==p['pack_name'] and iris.get('enableShaders')=='true' and iris.get('enableDebugOptions')=='false' and cfg.get('enabled') is False and cfg.get('preset')=='BALANCED' and cfg.get('debug') is False and sodium.get('performance',{}).get('use_no_error_g_l_context') is False,'OffBalanced/native/exportOff/guarded context required')
        require(not (root/'patched_shaders').exists() and not (root/'patched_shaders').is_symlink(),'No source-export diagnostic allowed')
        jar=dc.ROOT/('dev/artifacts/general-ports-20261011/shaderlod-fabric-26.2-manual-preview.jar' if makeup else 'dev/artifacts/volumetric-ports-solas-20261011/shaderlod-fabric-26.2-manual-preview.jar');require(p['manualArtifact']==str(jar) and p['manualArtifactSha256']==('01ebfd8cdf38eee6a444841172498c915a360087f7b5184050d7ad4af939c5e2' if makeup else '045b8230021c21af6eb7362c101f9fd1be34233806421fdc50e3c7657ae3779c') and hashlib.sha256(file(str(jar)).read_bytes()).hexdigest()==p['manualArtifactSha256'],'Exact new262 artifact required')
        init=file(p['manualArtifactInit']);require(inits==[str(init)] and hashlib.sha256(init.read_bytes()).hexdigest()==p['manualArtifactInitSha256'],'Exact BSL262 artifact init required')
        actual=read_json(p['manualArtifactInputs']);dirs={str(module/'build/classes/java/main'),str(module/'build/resources/main')}
        require(actual['mainModSourceSets']==[] and actual['modFiles']==[str(jar)] and actual['classpath'].count(str(jar))==1 and not dirs.intersection(actual['classpath']),'Actual262 jar-only main CP required')
        required=['--gameDir',p['profile'],'--username','ShaderLOD','--fullscreen','--width','1920','--height','1200','--quickPlaySingleplayer',dc.WORLD]
        require(actual['combinedProgramArguments']==required and actual['actualRunConfiguration']['runDirectory']==p['profile'],'ActualLoom262 combined gameDir/program/runDirectory join required')
        dli=file(p['manualArtifactDli']).read_text();groups=str(jar)+'::'+str(module/'build/classes/java/devClient')+':shaderlod_dev%%'+str(module/'build/resources/devClient')
        require(('fabric.classPathGroups='+groups) in dli and not any(path in dli for path in dirs),'Actual262 artifact DLI groups required')

    def fabric262_bsl_source_command(argv):
        module = dc.ROOT/'ports/26.2/fabric'
        require(p['mode']=='on' and p['fullscreen'] and not p['include_dh'] and not p['apply_lod_settings'] and p['already_built'], 'BSL262 source-only native runtime required')
        require(argv.count('-p')==1 and any(argv[i:i+2]==['-p',str(module)] for i in range(len(argv)-1)) and argv.count('runClient')==1, 'Exact BSL262 source project/task required')
        props,skips,arguments={},[],[]; i=1
        while i<len(argv):
            a=argv[i]
            if a=='-p':i+=2;continue
            if a=='-x':require(i+1<len(argv),'Missing skip');skips.append(argv[i+1]);i+=2;continue
            if a.startswith('-P'):
                k,sep,v=a[2:].partition('=');require(sep and k not in props,'Missing/duplicate BSL262 property');props[k]=v
            elif a.startswith('--args='):arguments.append(shlex.split(a.split('=',1)[1]))
            else:require(a in ('runClient','--no-daemon','--max-workers=2','--console=plain'),'Unexpected BSL262 argument')
            i+=1
        stamp=props.get('portStartupDeadlineEpochMillis','')
        require(stamp.isdigit() and int(stamp)>int((now or datetime.now(timezone.utc)).timestamp()*1000) and int(stamp)==int(datetime.fromisoformat(p['stop_at_utc']).timestamp()*1000),'BSL262 paired positive deadline required')
        require(props=={'portBslOriginalSource':'true','portRoundtripOriginal':'BSL_v10.1.8.zip','portRunDirectory':p['profile'],'portStartupDeadlineEpochMillis':stamp},'Exact original-only flags/DSL profile required')
        require(arguments==[['--gameDir',p['profile']]],'Explicit typed BSL262 gameDir override required; DSL supplies native remaining args')
        require(len(skips)==4 and set(skips)==set(dc.SKIPPABLE) and p['duration_seconds']==330 and p.get('roundtripStages')==['original'],'Finite original-only330/four skips required')
        root=Path(p['profile']);require(root==root.resolve() and root.parts[-3:]==('run-workloads','port-262-runtime-r1','fabric'),'Exact BSL262 private profile suffix required')
        require(p['explicit_env']=={'JAVA_HOME':str(dc.ROOT/'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home'),'JAVA_TOOL_OPTIONS':'-XX:ActiveProcessorCount=2'},'Locked JDK25 source runtime required')
        require(p['pack_name']=='BSL_v10.1.8.zip' and p['expected_loaded_sha256']=='36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5' and all(hashlib.sha256(file(p[k]).read_bytes()).hexdigest()==p['expected_loaded_sha256'] for k in ('pack','installed_pack')),'Exact BSL original required')
        native_path=dc.ROOT/'src/main/resources/assets/shaderlod/bslquad/native384.json';native=read_json(str(native_path))
        require(hashlib.sha256(native_path.read_bytes()).hexdigest()=='0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394' and len(native)==p['expected_options_count']==384 and read_json(p['expected_options_source'])==native,'Exact BSL native384 certificate required')
        require(all(dc.properties(file(x).read_text())==native for x in (p['pack_options'],p['installed_pack']+'.txt')),'Full actual BSL384 sidecars required')
        iris=dc.properties(file(str(root/'config/iris.properties')).read_text());cfg=read_json(str(root/'config/shaderlod-client.json'));sodium=read_json(str(root/'config/sodium-options.json'))
        require(iris.get('shaderPack')==p['pack_name'] and iris.get('enableShaders')=='true' and iris.get('enableDebugOptions')=='true' and cfg.get('enabled') is False and cfg.get('preset')=='PERFORMANCE' and cfg.get('debug') is False and sodium.get('performance',{}).get('use_no_error_g_l_context') is False,'BSL Off/Performance/debugfalse plus export/debug-GL context input required')
        exports=root/'patched_shaders';require(not exports.is_symlink() and (not exports.exists() or exports.is_dir() and not any(exports.iterdir())),'Fresh nonsymlink empty BSL export directory required')
        proof_pins={'native384.json':'0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394','source-proof.json':'82d47e661df8ead0923ee1b05380561910c5465a67ca215b94e26fbd1cb86ee2','api-proof.json':'806daab5da2ec861b16ff3271c481dbb579bf789743c78afee6103fe0ab2ea07'}
        for name,digest in proof_pins.items():
            require(not (module/'build/resources/main/assets/shaderlod/bslquad'/name).exists() and hashlib.sha256(file(str(module/'build/resources/devClient/assets/shaderlod/bslquad'/name)).read_bytes()).hexdigest()==digest,'BSL proof must be current DEV-only pinned resource: '+name)

    def fabric2612_shader_fps_command(argv):
        module = dc.ROOT/'ports/26.1.2/fabric'
        families = {'makeup':('makeupquad',100),'sildur':('sildurquad',115),'bsl':('bslquad',384),
                    'reimagined':('reimaginedquad',398),'unbound':('unboundquad',398),
                    'photon':('photonquad',366),'bliss':('blissquad',463)}
        family = p.get('measurementFamily'); require(family in families, 'Unknown measured shader family')
        folder,count = families[family]
        require(p['mode']=='on' and p['fullscreen'] and not p['include_dh'] and not p['apply_lod_settings']
                and p['already_built'], 'FPS native/fullscreen/no external Apply contract required')
        require(p.get('nativeFamilyControl') is False and p.get('bslFallbackControl') is False
                and p.get('fullQualityRoundtrip') is False, 'FPS mode must be distinct from prior controls')
        require(argv.count('-p')==1 and any(argv[i:i+2]==['-p',str(module)] for i in range(len(argv)-1))
                and argv.count('runClient')==1, 'Exact Fabric2612 runClient required')
        props,skips,inits,args={ },[],[],[];i=1
        while i<len(argv):
            value=argv[i]
            if value=='-p':i+=2;continue
            if value in ('-x','-I'):
                require(i+1<len(argv),'Missing FPS skip/init');(skips if value=='-x' else inits).append(argv[i+1]);i+=2;continue
            if value.startswith('-P'):
                key,sep,val=value[2:].partition('=');require(sep and key not in props,'Duplicate FPS property');props[key]=val
            elif value.startswith('--args='):args.append(shlex.split(value.split('=',1)[1]))
            else:require(value in ('runClient','--no-daemon','--max-workers=2','--console=plain'),'Unknown FPS command argument')
            i+=1
        stamp=props.get('portStartupDeadlineEpochMillis','')
        require(stamp.isdigit() and int(stamp)==int(datetime.fromisoformat(p['stop_at_utc']).timestamp()*1000)
                and int(stamp)>int((now or datetime.now(timezone.utc)).timestamp()*1000), 'FPS paired future deadline required')
        native_path=dc.ROOT/f'src/main/resources/assets/shaderlod/{folder}/native{count}.json'
        native=read_json(str(native_path));proof=read_json(str(native_path.parent/'source-proof.json'))
        info=read_json(p['familyInput']);require(info['family']==family and info['nativeOptions']==native
                and info['nativeCount']==count and info['nativeOptionsPath']==str(native_path)
                and info['nativeSha256']==hashlib.sha256(native_path.read_bytes()).hexdigest(), 'Exact FPS portable family input required')
        changes = {'SHADERLOD_LIVE_PIXEL_BUDGET':'true','SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED':'true',
                   'SHADERLOD_RENDER_BUDGET_INCLUDED':'true','TAAU':'true','TAAU_RENDER_SCALE':'1.00'} if family=='photon' else {
                   'SHADERLOD_EFFECT_ADAPTER':'1','SHADERLOD_EFFECT_ADAPTER_INCLUDED':'true',
                   'SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED':'true'} if family in ('sildur','bliss') else {
                   'SHADERLOD_REFINEMENT_ADAPTER':'1','SHADERLOD_REFINEMENT_ADAPTER_INCLUDED':'true',
                   'SHADERLOD_REFINEMENT_BUDGET_INCLUDED':'true'}
        require(info['balancedDeclaredChanges']==changes and info['expectedGeneratedOptions']==native|changes
                and info['generatedOptionCount']==len(native|changes), 'Exact source-declared generated option map required')
        require(props=={'portRoundtrip':'true','portNativeFamily':'measure-'+family,'portRoundtripOriginal':p['pack_name'],
                        'portQualityRoundtrip':'false','portRoundtripUi':'true','portStartupDeadlineEpochMillis':stamp},
                'Exact FPS observation flags required')
        require(len(skips)==4 and set(skips)==set(dc.SKIPPABLE), 'Exact FPS compile/resource skips required')
        require(args==[['--gameDir',p['profile'],'--username','ShaderLOD','--fullscreen','--width','1920','--height','1200',
                        '--quickPlaySingleplayer',dc.WORLD]], 'Exact FPS native gameDir argv required')
        root=Path(p['profile']);require(root==root.resolve() and root.parts[-3:]==('run-workloads','port-2612-runtime-r1','fabric'),
                                      'FPS private BackupPrompt suffix required')
        require(p.get('duration_seconds')==600 and p.get('settleSeconds')==40 and p.get('measurementSeconds')==30
                and p.get('roundtripStages')==['original','performance','balanced','quality','off'], 'Exact FPS stage/window contract required')
        require(len(native)==p['expected_options_count']==count and read_json(p['expected_options_source'])==native
                and all(dc.properties(file(path).read_text())==native for path in [p['pack_options'],p['installed_pack']+'.txt']),
                'Complete native portable FPS sidecars required')
        require(p['expected_loaded_sha256']==info['archiveSha256']==proof['archive_sha256'] and p['pack_name']==info['packName']
                and all(hashlib.sha256(file(p[key]).read_bytes()).hexdigest()==proof['archive_sha256'] for key in ('pack','installed_pack')),
                'Exact pinned FPS archive required')
        iris=dc.properties(file(str(root/'config/iris.properties')).read_text());cfg=read_json(str(root/'config/shaderlod-client.json'))
        opts=dc.properties('\n'.join(line.replace(':','=',1) for line in file(str(root/'options.txt')).read_text().splitlines()))
        require(iris.get('shaderPack')==p['pack_name'] and iris.get('enableShaders')=='true' and iris.get('enableDebugOptions')=='false'
                and cfg.get('enabled') is False and cfg.get('preset')=='PERFORMANCE' and cfg.get('debug') is False,
                'Native original Off/Performance/debugOff/exportOff required')
        inactivity = json.loads(opts.get('inactivityFpsLimit','null'))
        require(opts.get('enableVsync')=='false' and opts.get('maxFps')=='260' and inactivity=='minimized'
                and opts.get('renderDistance')=='24','Exact FPS configured limiter/scene options required')
        require(not (root/'patched_shaders').exists() and not (root/'patched_shaders').is_symlink(),'FPS source exports must be Off')
        require(p['explicit_env']=={'JAVA_HOME':str(dc.ROOT/'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home'),
                                    'JAVA_TOOL_OPTIONS':'-XX:ActiveProcessorCount=2'},'Locked FPS JDK invocation required')
        delivered=dc.ROOT/'dev/artifacts/manual-coverage-20261011/shaderlod-fabric-26.1.2-manual-preview.jar'
        require(p['manualArtifact']==str(delivered) and p['manualArtifactSha256']=='65832fdf5ead9a415167a7be20a80c1e40d7f45f1fd036552a12352cb39790bd'
                and hashlib.sha256(file(str(delivered)).read_bytes()).hexdigest()==p['manualArtifactSha256'],'Exact delivered FPS artifact required')
        init=file(p['manualArtifactInit']);require(inits==[str(init)] and hashlib.sha256(init.read_bytes()).hexdigest()==p['manualArtifactInitSha256'],
                                                'Exact frozen FPS consumer init required')
        actual=read_json(p['manualArtifactInputs']);main_dirs={str(module/'build/classes/java/main'),str(module/'build/resources/main')}
        require(actual.get('mainModSourceSets')==[] and actual.get('modFiles')==[str(delivered)]
                and actual['classpath'].count(str(delivered))==1 and not main_dirs.intersection(actual['classpath']),
                'FPS main origin must be delivered jar')
        dli=file(p['manualArtifactDli']).read_text();groups=str(delivered)+'::'+str(module/'build/classes/java/devClient')+':shaderlod_dev%%'+str(module/'build/resources/devClient')
        require(('fabric.classPathGroups='+groups) in dli and not any(path in dli for path in main_dirs),'Actual FPS DLI groups required')

    def fabric2612_quality_command(argv):
        if p.get('manualShaderFps') is True:
            fabric2612_shader_fps_command(argv)
            return
        module = dc.ROOT / 'ports/26.1.2/fabric'
        require(p['mode'] == 'on' and p['fullscreen'] and not p['include_dh'] and not p['apply_lod_settings'],
                'Fabric2612 Quality requires native source runtime/no external Apply')
        require(argv.count('-p') == 1 and any(argv[i:i+2] == ['-p', str(module)] for i in range(len(argv)-1)),
                'Exact Fabric2612 project required')
        require(argv.count('runClient') == 1, 'One actual Fabric2612 runClient required')
        props, skips, arguments, inits = {}, [], [], []
        manual_artifact = p.get('manualArtifact') is not None
        i = 1
        while i < len(argv):
            a = argv[i]
            if a == '-p': i += 2; continue
            if a == '-I':
                require(manual_artifact and i+1 < len(argv), 'Manual artifact init is explicit only'); inits.append(argv[i+1]); i += 2; continue
            if a == '-x':
                require(i+1 < len(argv), 'Missing skip task'); skips.append(argv[i+1]); i += 2; continue
            if a.startswith('-P'):
                k, sep, v = a[2:].partition('='); require(sep and k not in props, 'Duplicate/missing Fabric2612 property'); props[k] = v
            elif a.startswith('--args='): arguments.append(shlex.split(a.split('=', 1)[1]))
            else: require(a in ('runClient', '--no-daemon', '--max-workers=2', '--console=plain'), 'Unknown Fabric2612 option')
            i += 1
        stamp = props.get('portStartupDeadlineEpochMillis', '')
        require(stamp.isdigit() and int(stamp) == int(datetime.fromisoformat(p['stop_at_utc']).timestamp()*1000)
                and int(stamp) > int((now or datetime.now(timezone.utc)).timestamp()*1000), 'Fabric2612 paired positive deadline required')
        native_family = p.get('nativeFamilyControl') is True
        bsl_fallback = p.get('bslFallbackControl') is True
        bsl_volume = p.get('bslVolumeControl') is True
        makeup_volume = p.get('makeupVolumeControl') is True
        complementary_volume = p.get('complementaryVolumeControl') is True
        volume_family = p.get('complementaryVolumeFamily')
        if complementary_volume: require(volume_family in ('reimagined','unbound'), 'Explicit R/U volume family required')
        require(sum((native_family,bsl_fallback,bsl_volume,makeup_volume,complementary_volume))<=1, 'Native control modes are exclusive')
        require(not (native_family and bsl_fallback), 'Native-family and BSL fallback modes are exclusive')
        native_mode = props.get('portNativeFamily', '')
        if native_family: require(native_mode in ('reimagined','unbound'), 'Unknown explicit native family')
        if bsl_fallback: require(native_mode == 'bsl-fallback' and manual_artifact, 'BSL fallback requires explicit delivered-artifact selector')
        if bsl_volume: require(native_mode == 'bsl-volume' and manual_artifact, 'Explicit new-artifact BSL volume selector required')
        if makeup_volume: require(native_mode == 'makeup-volume' and manual_artifact, 'Explicit delivered MakeUp volume selector required')
        if complementary_volume: require(native_mode == volume_family+'-volume' and manual_artifact, 'Exact R/U volume selector required')
        native_unbound = native_family and native_mode == 'unbound' or complementary_volume and volume_family == 'unbound'
        if manual_artifact:
            require(native_family and native_mode in ('reimagined','unbound') or bsl_fallback and native_mode == 'bsl-fallback' or bsl_volume and native_mode == 'bsl-volume' or makeup_volume and native_mode == 'makeup-volume' or complementary_volume and native_mode == volume_family+'-volume', 'Delivered manual artifact supports only explicit R/U native or BSL fallback here')
            delivered = dc.ROOT/('dev/artifacts/volumetric-ports-solas-20261011/shaderlod-fabric-26.1.2-manual-preview.jar' if bsl_volume else 'dev/artifacts/volumetric-ports-20261011/shaderlod-fabric-26.1.2-manual-preview.jar' if makeup_volume or complementary_volume else 'dev/artifacts/manual-coverage-20261011/shaderlod-fabric-26.1.2-manual-preview.jar')
            require(p['manualArtifact'] == str(delivered) and p.get('manualArtifactSha256') == ('ceb61cc808f4277c290fe5b0ddbc68d9e908bd2113e919b04baa12c72c59a3de' if bsl_volume else 'b14159cbc91b233885fc3f31e24265b06075da566068492dc5a963fa574da7f8' if makeup_volume or complementary_volume else '65832fdf5ead9a415167a7be20a80c1e40d7f45f1fd036552a12352cb39790bd')
                    and hashlib.sha256(file(str(delivered)).read_bytes()).hexdigest() == p['manualArtifactSha256'], 'Exact delivered manual artifact required')
            init = file(p.get('manualArtifactInit',''))
            require(inits == [str(init)] and hashlib.sha256(init.read_bytes()).hexdigest() == p.get('manualArtifactInitSha256'),
                    'Exact frozen artifact-consumer init required')
            captured = read_json(p.get('manualArtifactInputs',''))
            main_dirs = {str(module/'build/classes/java/main'), str(module/'build/resources/main')}
            require(captured.get('mainModSourceSets') == [] and captured.get('modFiles') == [str(delivered)]
                    and captured['classpath'].count(str(delivered)) == 1 and not main_dirs.intersection(captured['classpath']),
                    'Delivered jar must replace compiled-main origin, not merely appear on classpath')
            dli = file(p.get('manualArtifactDli','')).read_text()
            groups = str(delivered)+'::'+str(module/'build/classes/java/devClient')+':shaderlod_dev%%'+str(module/'build/resources/devClient')
            require(('fabric.classPathGroups='+groups) in dli and not any(path in dli for path in main_dirs),
                    'Actual DLI groups must consume delivered main jar and independent dev outputs')
        else:
            require(not inits, 'Historical compiled-main caller does not accept an init')
        pack_name = ('ComplementaryUnbound_r5.9.3.zip' if native_unbound else 'ComplementaryReimagined_r5.9.3.zip') if complementary_volume else 'MakeUp-UltraFast-9.5g.zip' if makeup_volume else 'BSL_v10.1.8.zip' if bsl_fallback or bsl_volume else ('ComplementaryUnbound_r5.9.3.zip' if native_unbound else 'ComplementaryReimagined_r5.9.3.zip') if native_family else 'photon_v1.3b.zip'
        expected_props = {'portRoundtrip':'true', 'portRoundtripOriginal':pack_name, 'portQualityRoundtrip':'false' if native_family or bsl_fallback or bsl_volume or makeup_volume or complementary_volume else 'true',
                          'portRoundtripUi':'true' if native_family or bsl_fallback or bsl_volume or makeup_volume or complementary_volume else 'false', 'portStartupDeadlineEpochMillis':stamp}
        if native_family or bsl_fallback or bsl_volume or makeup_volume or complementary_volume: expected_props['portNativeFamily'] = native_mode
        require(props == expected_props, 'Exact named native-family/Quality-only flags required')
        require(len(skips) == 4 and set(skips) == set(dc.SKIPPABLE) and p['already_built'], 'Exact four build skips required')
        require(arguments == [['--gameDir', p['profile'], '--username', 'ShaderLOD', '--fullscreen', '--width', '1920',
                               '--height', '1200', '--quickPlaySingleplayer', dc.WORLD]], 'Exact Fabric2612 gameDir/native argv required')
        root = Path(p['profile']); require(root == root.resolve() and root.parts[-3:] == ('run-workloads','port-2612-runtime-r1','fabric'),
                                            'Exact private BackupPrompt profile suffix required')
        require(p.get('duration_seconds') == 330 and p.get('fullQualityRoundtrip') is (not (native_family or bsl_fallback or bsl_volume or makeup_volume or complementary_volume)), 'Explicit finite330s named contract required')
        stages = ['original','on','quality','off'] if bsl_fallback or bsl_volume or makeup_volume or complementary_volume else ['original','quality','performance','quality-return','off'] if native_family else ['original','quality','native-info-edit','native-info-restored','native-upscaled',
                 'native-aa-restored','performance','quality-return','off']
        require(p.get('roundtripStages') == stages, 'Actual named stage consumer required')
        require(p['explicit_env'] == {'JAVA_HOME': str(dc.ROOT/'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home'),
                                     'JAVA_TOOL_OPTIONS':'-XX:ActiveProcessorCount=2'}, 'Locked JDK25 invocation required')
        require('JAVA_VERSION="25.0.2"' in file(str(Path(p['explicit_env']['JAVA_HOME'])/'release')).read_text(), 'Actual JDK25.0.2 release required')
        archive = ('98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a' if native_unbound else 'fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38') if complementary_volume else '0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8' if makeup_volume else '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5' if bsl_fallback or bsl_volume else ('98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a' if native_unbound else
                   'fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38' if native_family else
                   '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead')
        require(p['pack_name'] == pack_name and p['expected_loaded_sha256'] == archive and
                all(hashlib.sha256(file(p[key]).read_bytes()).hexdigest() == archive for key in ('pack','installed_pack')),
                'Exact Photon original required')
        count = 398 if complementary_volume else 100 if makeup_volume else 384 if bsl_fallback or bsl_volume else 398 if native_family else 366
        native_path = dc.ROOT/f'src/main/resources/assets/shaderlod/{volume_family if complementary_volume else "makeup" if makeup_volume else "bsl" if bsl_fallback or bsl_volume else "unbound" if native_unbound else "reimagined" if native_family else "photon"}quad/native{count}.json'
        native_sha = ('b64d94998c99bf867e80d350a4a0a8108c58507e0401eb053a70e3aca81a5036' if native_unbound else 'ccfca334aeaa074a74d1f8e9071b707d32161a6b771366fefcc0f87f3b1175f4') if complementary_volume else '0c11f31b3d5e51d9d1bff56a84ac13a3f1265472f7313997a493c74435a418bb' if makeup_volume else '0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394' if bsl_fallback or bsl_volume else ('b64d94998c99bf867e80d350a4a0a8108c58507e0401eb053a70e3aca81a5036' if native_unbound else
                      'ccfca334aeaa074a74d1f8e9071b707d32161a6b771366fefcc0f87f3b1175f4' if native_family else '9b1d4db811f54cc986c97e8c8ce917f6731fbaf1f006927ccdef351bb3c5797f')
        require(hashlib.sha256(file(str(native_path)).read_bytes()).hexdigest() == native_sha, 'Named portable option certificate changed')
        if native_unbound:
            for name,digest in {'source-proof.json':'4da7e9f155925752ff043576856825898d70c211bd9b3b9792f4816f788dac2d',
                                'api-proof.json':'80768f1ca89a62e3a8746f0df5e49cdce8b45b91810f671dfd03b5f586544bb7'}.items():
                require(hashlib.sha256(file(str(native_path.parent/name)).read_bytes()).hexdigest()==digest, 'Unbound ancestry resource differs: '+name)
        native = read_json(str(native_path)); fixture = {} if native_family or bsl_fallback or bsl_volume or makeup_volume or complementary_volume else {'TAA':'true','TAAU':'false','TAAU_RENDER_SCALE':'1.00','INFO':'2'}
        require(len(native) == p['expected_options_count'] == count and read_json(p['expected_options_source']) == native | fixture,
                'Exact full366 configured-AA fixture required')
        require(all(dc.properties(file(path).read_text()) == native | fixture for path in (p['pack_options'], p['installed_pack']+'.txt')),
                'Complete adopted portable366 plus AA fixture sidecars required')
        iris = dc.properties(file(str(root/'config/iris.properties')).read_text()); cfg = read_json(str(root/'config/shaderlod-client.json'))
        require(iris.get('shaderPack') == p['pack_name'] and iris.get('enableShaders') == 'true' and iris.get('enableDebugOptions') == 'false'
                and cfg.get('enabled') is False and cfg.get('preset') == ('BALANCED' if bsl_fallback or bsl_volume or makeup_volume or complementary_volume else 'PERFORMANCE') and cfg.get('debug') is False, 'Native original CoreOff/exportOff required')
        require(not (root/'patched_shaders').exists() and not (root/'patched_shaders').is_symlink(), 'Fresh no-export profile required')

    def fabric_command(argv):
        port = launcher in ('fabric12111', 'fabric262')
        require(p['mode'] == 'on' and p['fullscreen'] and not p['include_dh']
                and not p['apply_lod_settings'], 'Fabric renderer probe requires On/fullscreen/no DH/no external Apply')
        project = {'fabric12111': str(dc.ROOT / 'ports/1.21.11/fabric'),
                   'fabric262': str(dc.ROOT / 'ports/26.2/fabric')}.get(launcher, 'fabric')
        require(argv.count('-p') == 1 and
                any(argv[i:i+2] == ['-p', project] for i in range(len(argv)-1)),
                'Fabric launcher requires exactly -p ' + project)
        require(argv.count('runClient') == 1, 'Fabric launcher requires exactly one runClient task')
        props, skipped, arguments = {}, [], []
        index = 1
        while index < len(argv):
            arg = argv[index]
            if arg == '-p':
                index += 2
                continue
            if arg == '-x':
                require(index + 1 < len(argv), 'Missing Fabric skip task')
                skipped.append(argv[index + 1])
                index += 2
                continue
            if arg.startswith('-P'):
                key, sep, value = arg[2:].partition('=')
                require(sep and key not in props, 'Missing/duplicate command property: ' + key)
                props[key] = value
            elif arg.startswith('--args='):
                arguments.append(arg.split('=', 1)[1])
            else:
                require(arg in ('runClient', '--offline', '--no-daemon', '--max-workers=2', '--console=plain'),
                        'Unexpected Fabric task/project/option: ' + arg)
            index += 1
        single_phase = props.get('fabricRendererSinglePhase')
        single_window = 'fabricRendererSinglePhase' in props
        if single_window:
            require(launcher == 'fabric1211' and single_phase in ('original', 'on', 'original-return')
                    and 'fabricNativeCostControl' not in props
                    and props.get('fabricRendererResearchFamily') == 'bsl'
                    and props.get('fabricRendererSpeedProbe') == 'true'
                    and props.get('fabricRendererSourceProbe', 'false') == 'false'
                    and props.get('fabricOriginalSourceProbe', 'false') == 'false',
                    'Single window requires explicit named BSL speed phase without mixed controls')
        native_control = 'fabricNativeCostControl' in props
        if native_control:
            require(launcher == 'fabric1211' and props['fabricNativeCostControl'] == 'true'
                    and props.get('fabricRendererResearchFamily') == 'bsl'
                    and props.get('fabricRendererSpeedProbe') == 'true'
                    and props.get('fabricRendererSourceProbe', 'false') == 'false'
                    and props.get('fabricOriginalSourceProbe', 'false') == 'false',
                    'Native cost control requires explicit named BSL speed mode only')
        port_bsl_original = launcher == 'fabric12111' and props.get('portBslOriginalSource') == 'true'
        port_sildur_original = launcher == 'fabric12111' and props.get('portSildurOriginalSource') == 'true'
        port_bsl_budget = launcher == 'fabric12111' and props.get('portBslGpuBudget') == 'true'
        port_bsl_alpha = launcher == 'fabric12111' and props.get('portBslAlphaAdmission') == 'true'
        port_bsl_private = launcher == 'fabric12111' and props.get('portBslEarlyAlphaObserver') == 'true'
        port_bsl_zero = launcher == 'fabric12111' and props.get('portBslZeroEmissionObserver') == 'true'
        port_bsl_native_diagnostic = port_bsl_budget or port_bsl_alpha
        require(sum((port_bsl_original, port_sildur_original, port_bsl_budget, port_bsl_alpha, port_bsl_private, port_bsl_zero)) <= 1,
                'Original-only families/budget cannot mix')
        original_only = port_bsl_original or port_sildur_original or launcher == 'fabric1211' and props.get('fabricOriginalSourceProbe') == 'true'
        named_bsl = 'fabricRendererResearchFamily' in props
        if named_bsl:
            require(launcher == 'fabric1211' and props['fabricRendererResearchFamily'] == 'bsl',
                    'Only explicit root Fabric BSL renderer research is admitted')
        probe_keys = ('portRendererSpeedProbe',) if port else ('fabricRendererSpeedProbe', 'fabricRendererSourceProbe')
        enabled_probes = [key for key in probe_keys if props.get(key) == 'true']
        require(all(props.get(key, 'false') in ('true', 'false') for key in probe_keys), 'Invalid Fabric probe value')
        if port_bsl_zero:
            require(not enabled_probes and not named_bsl, 'Zero-emission observer cannot mix renderer/source modes')
            arm = p.get('zero_emission_arm')
            require(arm in ('stock', 'control', 'candidate'), 'Explicit zero-emission arm required')
            stock_observer = arm == 'stock'
            require(p['pack_name'] == ('BSL_v10.1.8.zip' if stock_observer else 'BSL-zero-emission-r1-' + arm + '.zip'),
                    'Zero-emission exact arm archive required')
            stamp = props.get('portStartupDeadlineEpochMillis', '')
            require(stamp.isdigit() and int(stamp) > int((now or datetime.now(timezone.utc)).timestamp() * 1000) and
                    int(stamp) == int(datetime.fromisoformat(p['stop_at_utc'].replace('Z', '+00:00')).timestamp() * 1000),
                    'Zero-emission positive paired finite deadline required')
            expected = {'portBslZeroEmissionObserver': 'true', 'portBslZeroEmissionArm': arm,
                        'portBslZeroEmissionCore': 'off' if stock_observer else 'on', 'portStartupDeadlineEpochMillis': stamp}
            private_sha = '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5'
            if not stock_observer:
                expected['portBslZeroEmission'] = 'true'
                manifest = read_json(p['private_shader_manifest'])
                private_sha = {'control': 'c521e68e25b120da34600507d9a2497cf758eb5d6098ec102a0e0759705d6881',
                               'candidate': '7889f6f44c444c415ad212e985be21fbb3caef5b6720273a41eab91baee1ae81'}[arm]
                require(manifest.get('base_sha256') == '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5' and
                        manifest.get('native384_sha256') == '0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394' and
                        manifest['arms'][arm]['sha256'] == private_sha,
                        'Zero-emission native/archive manifest differs')
            require(read_json(p['native_source_ancestry']).get('originalSourceFamily') == 'bsl', 'Zero-emission original ancestry required')
        elif port_bsl_private:
            require(not enabled_probes and not named_bsl, 'Private BSL observer cannot mix renderer/source modes')
            arm = p.get('private_alpha_arm')
            require(arm in ('stock', 'control', 'candidate'), 'Explicit stock/private BSL observer arm required')
            stock_observer = arm == 'stock'
            require(p['pack_name'] == ('BSL_v10.1.8.zip' if stock_observer else 'BSL-early-alpha-r1-' + arm + '.zip'),
                    'Stock/private BSL exact arm archive required')
            stamp = props.get('portStartupDeadlineEpochMillis', '')
            require(stamp.isdigit() and int(stamp) > int((now or datetime.now(timezone.utc)).timestamp() * 1000) and
                    int(stamp) == int(datetime.fromisoformat(p['stop_at_utc'].replace('Z', '+00:00')).timestamp() * 1000),
                    'Private BSL positive paired finite startup deadline required')
            expected = {'portBslEarlyAlphaObserver': 'true',
                        'portBslEarlyAlphaArm': arm, 'portBslEarlyAlphaCore': 'off' if stock_observer else 'on',
                        'portStartupDeadlineEpochMillis': stamp}
            private_sha = '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5'
            if not stock_observer:
                expected['portBslEarlyAlpha'] = 'true'
                manifest = read_json(p['private_shader_manifest'])
                private_sha = {'control': '82ddff3530ab9c99cee4e779cc8718643627074a4b1b308bc55dea40b8223086',
                               'candidate': 'ee73a4272233d5b7ca7e13d6d009d4e95253cdd9fd234d6393871d0091e69b92'}[arm]
                require(manifest.get('original_sha256') == '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5' and
                        manifest.get('native384_sha256') == '0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394' and
                        manifest['arms'][arm]['sha256'] == private_sha,
                        'Private BSL current R2 original/native/archive manifest differs')
            require(read_json(p['native_source_ancestry']).get('originalSourceFamily') == 'bsl',
                    'Private BSL authentic original source ancestry required')
        elif port_bsl_native_diagnostic:
            require(not enabled_probes and not named_bsl, 'BSL GPU budget cannot mix renderer/source modes')
            require(p['pack_name'] == 'BSL_v10.1.8.zip', 'BSL GPU budget requires exact original archive basename')
            stamp = props.get('portStartupDeadlineEpochMillis', '')
            require(stamp.isdigit() and int(stamp) > int((now or datetime.now(timezone.utc)).timestamp() * 1000) and
                    int(stamp) == int(datetime.fromisoformat(p['stop_at_utc'].replace('Z', '+00:00')).timestamp() * 1000),
                    'BSL GPU budget needs positive paired finite startup deadline')
            expected = {'portBslAlphaAdmission' if port_bsl_alpha else 'portBslGpuBudget': 'true', 'portRoundtripOriginal': p['pack_name'],
                        'portStartupDeadlineEpochMillis': stamp}
        elif original_only:
            require(not enabled_probes and not named_bsl, 'Original-only cannot mix renderer research or roundtrip modes')
            expected = ({'portSildurOriginalSource': 'true', 'portRoundtripOriginal': p['pack_name']} if port_sildur_original else
                        {'portBslOriginalSource': 'true', 'portRoundtripOriginal': p['pack_name']} if port_bsl_original else
                        {'fabricOriginalSourceProbe': 'true', 'fabricOriginalSourceFamily': 'bsl',
                         'fabricRoundtripOriginal': p['pack_name']})
            require(props.get('fabricRoundtrip', 'false') in ('true', 'false'), 'Invalid base roundtrip flag')

        else:
            require(len(enabled_probes) == 1, 'Fabric requires exactly one explicit speed or untimed source probe')
            expected = ({'portRoundtrip': 'true', 'portRendererSpeedCandidate': 'true',
                         'portRendererSpeedProbe': 'true', 'portRoundtripOriginal': p['pack_name']} if port else
                        {'fabricRoundtrip': 'true', enabled_probes[0]: 'true', 'fabricRoundtripOriginal': p['pack_name']})
        if original_only or named_bsl or port_bsl_native_diagnostic or port_bsl_private or port_bsl_zero:
            family, count = ('sildur', 115) if port_sildur_original else ('bsl', 384)
            archive_sha = ('824504bc937f9570277c0a809f5059ae4a5fe70c413cf4459b6ccff4dfed7ebe' if port_sildur_original else
                           '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5')
            if port_bsl_private or port_bsl_zero:
                archive_sha = private_sha
            native_path = file(str(dc.ROOT / f'src/main/resources/assets/shaderlod/{family}quad/native{count}.json'))
            native_sha = ('795a8b210019380e8d120924aa4ef24555afc8bf9ea5ff9bffe8dafb9d631bd3' if port_sildur_original else
                          '0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394')
            require(hashlib.sha256(native_path.read_bytes()).hexdigest() == native_sha,
                    'Named native option certificate bytes changed or unavailable')
            native = read_json(str(native_path))
            for name, digest in {
                    'source-proof.json': ('896abface1ed04cf72f79f8460e55fa5821702a69382d9a5691ef47583cf6d51' if port_sildur_original else
                                          '82d47e661df8ead0923ee1b05380561910c5465a67ca215b94e26fbd1cb86ee2'),
                    'api-proof.json': '806daab5da2ec861b16ff3271c481dbb579bf789743c78afee6103fe0ab2ea07'}.items():
                require(hashlib.sha256(file(str(native_path.parent / name)).read_bytes()).hexdigest() == digest,
                        'Named ancestry certificate changed or unavailable: ' + name)
            require(len(native) == count and read_json(p['expected_options_source']) == native and
                    p['expected_options_count'] == count, 'Original-only requires the exact certified native option map')
            require(hashlib.sha256(file(p['pack']).read_bytes()).hexdigest() == archive_sha and
                    p['expected_loaded_sha256'] == archive_sha, 'Original-only requires the certified original archive')
            require(hashlib.sha256(file(p['installed_pack']).read_bytes()).hexdigest() == archive_sha,
                    'Original-only installed archive is absent or differs')
            option_inputs = [file(p['pack_options'])] if p.get('pack_options') else []
            sidecar = Path(p['installed_pack'] + '.txt')
            if sidecar.exists():
                option_inputs.append(file(str(sidecar)))
            for options_file in option_inputs:
                overrides = dc.properties(options_file.read_text())
                require((not (port_bsl_original or port_sildur_original or port_bsl_native_diagnostic or port_bsl_private or port_bsl_zero) or not overrides) and
                        all(key in native and native[key] == value for key, value in overrides.items()),
                        'Original-only options contain extra/changed native overrides')
            iris = dc.properties(file(str(Path(p['profile']) / 'config/iris.properties')).read_text())
            require(iris.get('shaderPack') == p['pack_name'] and iris.get('enableShaders') == 'true'
                    and iris.get('enableDebugOptions') == ('true' if original_only or props.get('fabricRendererSourceProbe') == 'true' else 'false'),
                    'Original-only profile needs selected original/shaders/debug exports before launch')
            if launcher == 'fabric12111' and original_only:
                sodium = read_json(str(Path(p['profile']) / 'config/sodium-options.json'))
                require(sodium.get('performance', {}).get('use_no_error_g_l_context') is False,
                        'Iris debug exports require Sodium no-error GL context disabled before startup')
            config = read_json(str(Path(p['profile']) / 'config/shaderlod-client.json'))
            require(config.get('enabled') is ((port_bsl_private or port_bsl_zero) and not stock_observer or single_window and single_phase == 'on') and config.get('preset') == 'PERFORMANCE'
                    and config.get('debug') is False, 'Named profile requires phase-matched preloaded core/Performance/debugfalse')
            exports = Path(p['profile']) / 'patched_shaders'
            if port_bsl_native_diagnostic or port_bsl_private or port_bsl_zero:
                require(not exports.exists() and not exports.is_symlink(), 'BSL GPU budget source exports must be absent')
            require(not exports.is_symlink() and (not (original_only or props.get('fabricRendererSourceProbe') == 'true')
                    or not exports.exists() or exports.is_dir() and not any(exports.iterdir())),
                    'Original-only needs a fresh empty compiler export directory')
        require(all(props.get(k) == v for k, v in expected.items()),
                'Fabric original/roundtrip/candidate flags disagree with plan')
        excluded = ('portQualityRoundtrip', 'portRoundtripUi') if port else ('fabricRoundtripDh', 'fabricRoundtripUi')
        for key in excluded:
            require(props.get(key, 'false') == 'false', 'Fabric speed probe requires ' + key + '=false')
        permitted = set(expected) | set(probe_keys) | set(excluded)
        if port_bsl_native_diagnostic or port_bsl_private or port_bsl_zero:
            permitted = set(expected)
        if native_control:
            permitted.add('fabricNativeCostControl')
        if single_window:
            permitted.add('fabricRendererSinglePhase')
        if original_only and not (port_bsl_original or port_sildur_original):
            permitted.add('fabricRoundtrip')
        if named_bsl:
            permitted.add('fabricRendererResearchFamily')
        require(set(props) <= permitted,
                'Unrecognized Fabric property could alter candidate inputs')
        if launcher == 'fabric1211' and not original_only and not named_bsl:
            require(hashlib.sha256(file(p['pack']).read_bytes()).hexdigest() !=
                    '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5',
                    'BSL renderer source/speed requires explicit fabricRendererResearchFamily=bsl')
        require(len(skipped) == len(set(skipped)) and set(skipped) <= set(dc.SKIPPABLE),
                'Unexpected/duplicate Fabric skip task')
        if p['already_built']:
            require(set(skipped) == set(dc.SKIPPABLE), 'already_built lacks Fabric skip-task vector')
        require(len(arguments) == 1, 'Fabric requires one forwarded --args vector')
        game = shlex.split(arguments[0], posix=True)
        options, fullscreen = {}, 0
        index = 0
        while index < len(game):
            arg = game[index]
            if arg == '--fullscreen':
                fullscreen += 1
                index += 1
                continue
            require(arg in ('--gameDir', '--quickPlaySingleplayer', '--username', '--width', '--height'),
                    'Unknown Fabric game argument: ' + arg)
            require(arg not in options and index + 1 < len(game),
                    'Missing/duplicate Fabric game argument: ' + arg)
            options[arg] = game[index + 1]
            index += 2
        require(fullscreen == 1 and options.get('--gameDir') == p['profile']
                and options.get('--quickPlaySingleplayer') == dc.WORLD,
                'Fabric gameDir/fullscreen/Quick Play disagree with plan')
        if '--username' in options:
            require(re.fullmatch(r'[A-Za-z0-9_]{1,16}', options['--username']), 'Invalid Fabric username')
        if '--width' in options or '--height' in options:
            require(options.get('--width') == '1920' and options.get('--height') == '1200',
                    'Fabric probe explicit dimensions must be the pinned 1920x1200 pair')

    def neoforge262_command(argv):
        u_control=p.get('unboundNeoMainControl') is True
        require(not u_control or not p.get('reimaginedNeoMainControl') and not p.get('bslNeoMainControl') and not p.get('makeupNeoControl') and not p.get('makeupNeoMainControl'),'Distinct Unbound family selector required')
        r_control=p.get('reimaginedNeoMainControl') is True
        require(not r_control or not p.get('bslNeoMainControl') and not p.get('makeupNeoControl') and not p.get('makeupNeoMainControl'),'Distinct Reimagined/BSL/MakeUp selectors required')
        bsl_control=p.get('bslNeoMainControl') is True
        require(not bsl_control or not p.get('makeupNeoControl') and not p.get('makeupNeoMainControl'),'Distinct BSL/MakeUp control selectors required')
        control=p.get('makeupNeoControl') is True or bsl_control or r_control or u_control
        main_control=p.get('makeupNeoMainControl') is True or bsl_control or r_control or u_control
        require(not main_control or control,'MAIN correction is limited to named MakeUp controls')
        require(p['mode'] == 'on' and p['fullscreen'] and not p['include_dh'] and not p['apply_lod_settings'],
                'NeoForge262 named probe requires fullscreen/no DH/no launch Apply')
        module = dc.ROOT / 'ports/26.2/neoforge'
        require(argv.count('-p') == 1 and any(argv[i:i+2] == ['-p', str(module)] for i in range(len(argv)-1)),
                'NeoForge262 requires exact module')
        props, skipped, inits = {}, [], []
        index = 1
        while index < len(argv):
            arg = argv[index]
            if arg == '-p': index += 2; continue
            if arg == '-I' and control:
                require(index+1<len(argv), 'Missing artifact init');inits.append(argv[index+1]);index+=2;continue
            if arg == '-x':
                require(index+1 < len(argv), 'Missing skip task')
                skipped.append(argv[index+1]); index += 2; continue
            if arg.startswith('-P'):
                key, sep, value = arg[2:].partition('=')
                require(sep and key not in props, 'Missing/duplicate NeoForge property')
                props[key] = value
            else:
                require(arg in ('runClient', '--no-daemon', '--max-workers=2', '--console=plain', '--offline'),
                        'Foreign NeoForge task/argument: ' + arg)
            index += 1
        require(argv.count('runClient') == 1 and len(skipped) == len(set(skipped))
                and set(skipped) == set(dc.SKIPPABLE) and p['already_built'], 'NeoForge262 exact built skip vector required')
        original_only = props.get('portOriginalOnly') == 'true'
        speed_probe = props.get('portRendererSpeedProbe') == 'true'
        require(control and not original_only and not speed_probe or not control and original_only != speed_probe, 'NeoForge262 requires exactly one named original/speed mode')
        expected = {'portOriginalOnly' if original_only else 'portRendererSpeedProbe': 'true',
                    'portRoundtripOriginal': p['pack_name'], 'portRunDirectory': p['profile']}
        if control:
            expected={'portRoundtrip':'true','portNativeFamily':'unbound-volume' if u_control else 'reimagined-volume' if r_control else 'bsl-volume' if bsl_control else 'makeup-volume','portRoundtripOriginal':p['pack_name'],'portQualityRoundtrip':'false','portRoundtripUi':'true','portBindingInputFix':'true','portRunDirectory':p['profile']}
            if main_control: expected.update(portBindingInputFix='false',portMainBindingCompat='true')
            require(set(props)==set(expected)|{'portStartupDeadlineEpochMillis'} and all(props.get(k)==v for k,v in expected.items()),'Exact family MAIN/DEV flags required')
            require(p['pack_name']==('ComplementaryUnbound_r5.9.3.zip' if u_control else 'ComplementaryReimagined_r5.9.3.zip' if r_control else 'BSL_v10.1.8.zip' if bsl_control else 'MakeUp-UltraFast-9.5g.zip'),'Exact family original basename required')
            require(p['duration_seconds']==330 and p['roundtripStages']==['original','on','quality','off'],'Exact fourstage330 control required')
            require(p['explicit_env']=={'JAVA_HOME':str(dc.ROOT/'dependencies/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home'),'JAVA_TOOL_OPTIONS':'-XX:ActiveProcessorCount=2'},'Pinned JDK25 required')
            require(inits==[p['manualArtifactInit']] and hashlib.sha256(file(inits[0]).read_bytes()).hexdigest()==p['manualArtifactInitSha256'],'Frozen artifact init required')
            jar=dc.ROOT/('dev/artifacts/neoforge-262-unbound-main-compat-20261013/shaderlod-neoforge-26.2-main-preview.jar' if u_control else 'dev/artifacts/neoforge-262-reimagined-main-compat-20261011/shaderlod-neoforge-26.2-main-preview.jar' if r_control else 'dev/artifacts/neoforge-262-bsl-main-compat-20261011/shaderlod-neoforge-26.2-main-preview.jar' if bsl_control else 'dev/artifacts/neoforge-262-main-compat-20261011/shaderlod-neoforge-26.2-main-preview.jar' if main_control else 'dev/artifacts/general-ports-20261011/shaderlod-neoforge-26.2-manual-preview.jar')
            require(p['manualArtifact']==str(jar) and p['manualArtifactSha256']==('cb2fd0fc083186804b175b4372681006fa2d5b2847d430c31a678998fcd84693' if u_control else 'a92a285357966fd3fb0257c70ef377ebc61c865023aea96682a523fa1ae78eee' if r_control else '459edc96196743239555ffa93dec2f33cdebba9a7ad76a5036f5857a83923a62' if bsl_control else '4d4ab6113a0eff9f5f7b4a1237a23f817b000bf96fd840036925e4bfce7f8f44' if main_control else '58bca40e78427b8fc2e6f7ef8cf19e178b3cfad3f1de76c85a3ec88e8e22e24e') and hashlib.sha256(file(str(jar)).read_bytes()).hexdigest()==p['manualArtifactSha256'],'Exact delivered NF artifact required')
            actual=read_json(p['manualArtifactInputs']);dirs={str(module/'build/classes/java/main'),str(module/'build/resources/main')}
            folders='-Dfml.modFolders=shaderlod%%'+str(jar)+':shaderlod_dev%%'+str(module/'build/classes/java/devClient')+':shaderlod_dev%%'+str(module/'build/resources/devClient')
            require(actual['modFolders']==folders and actual['runModFolders']==[folders],'Both real run and launch FML mod folders must be JAR-only')
            require(all(cp.count(str(jar))==1 and not dirs.intersection(cp) for cp in (actual['classpath'],actual['launchClasspath'])) and actual['gameDirectory']==p['profile'],'JAR-only main classpath/gameDir join required')
            require(Path(p['profile']).parts[-3:]==('run-workloads','port-262-runtime-r1','neoforge'),'Exact NF private suffix required')
        binding_fix = 'portBindingInputFix' in props
        require(not speed_probe or binding_fix, 'NeoForge262 speed probe requires the same explicit binding correction across Off/On/return')
        if binding_fix and not main_control:
            require(props['portBindingInputFix'] == 'true', 'Binding input fix needs explicit true opt-in')
            expected['portBindingInputFix'] = 'true'
            mixins = read_json(str(module / 'build/resources/devClient/shaderlod-port-binding-input-dev.mixins.json'))
            require(mixins.get('package') == 'com.pp.shaderlod.dev.bindingfix.mixin' and
                    mixins.get('client') == ['BindingInputDrawMixin', 'BindingInputPipelineMixin'],
                    'Binding helper classes must remain outside the reserved mixin package')
            classes = module / 'build/classes/java/devClient'
            for name in mixins['client']:
                file(str(classes / 'com/pp/shaderlod/dev/bindingfix/mixin' / (name + '.class')))
            file(str(classes / 'com/pp/shaderlod/dev/bindingfix/BindingInputContext.class'))
        require(set(props) == set(expected) | {'portStartupDeadlineEpochMillis'}
                and all(props.get(k) == v for k,v in expected.items()), 'NeoForge named probe flags/profile mismatch')
        stamp = props.get('portStartupDeadlineEpochMillis', '')
        require(re.fullmatch(r'[1-9][0-9]{12}', stamp or '') is not None,
                'NeoForge producer pending: positive finite startup deadline required (baseline0 not launch admission)')
        stop = datetime.fromisoformat(p['stop_at_utc'].replace('Z', '+00:00'))
        require(int(stamp) == int(stop.timestamp()*1000), 'Startup deadline differs from finite stop')
        profile = Path(p['profile'])
        require((profile.is_relative_to(dc.ROOT / 'run-workloads/concept-transfer-r1') if control else profile.is_relative_to(dc.ROOT / 'run-workloads/speed-port-r1')) and
                not any(x.is_symlink() for x in (profile, *profile.parents)), 'Expected private nonsymlink profile copy')
        # LoadingWarningsPrompt consumes this private-prefix component before world admission.
        outer = file(str(profile.parents[2] / 'ports/26.2/runtime/sodium-neoforge-0.9.2+mc26.2.jar'))
        require(not outer.is_symlink() and hashlib.sha256(outer.read_bytes()).hexdigest() ==
                '8364f21b122ffe975392dea12eba0b3bc90609c982113e92dc2e30df2c335805',
                'NeoForge warning guard requires the pinned private-prefix Sodium outer archive')
        with dc.zipfile.ZipFile(outer) as archive:
            member = 'META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.9.2+mc26.2-mod.jar'
            require(archive.namelist().count(member) == 1 and
                    hashlib.sha256(archive.read(member)).hexdigest() ==
                    '709bff1cf59d37a8ed59589f7fd7bf335fdc5268602eece5bc6c1269a3bceffb',
                    'NeoForge warning guard requires the exact pinned nested Sodium member')
        native_path = file(str(dc.ROOT / ('src/main/resources/assets/shaderlod/unboundquad/native398.json' if u_control else 'src/main/resources/assets/shaderlod/reimaginedquad/native398.json' if r_control else 'src/main/resources/assets/shaderlod/bslquad/native384.json' if bsl_control else 'src/main/resources/assets/shaderlod/makeupquad/native100.json')))
        require(hashlib.sha256(native_path.read_bytes()).hexdigest() ==
                ('b64d94998c99bf867e80d350a4a0a8108c58507e0401eb053a70e3aca81a5036' if u_control else 'ccfca334aeaa074a74d1f8e9071b707d32161a6b771366fefcc0f87f3b1175f4' if r_control else '0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394' if bsl_control else '0c11f31b3d5e51d9d1bff56a84ac13a3f1265472f7313997a493c74435a418bb'), 'Named family native certificate changed')
        native = read_json(str(native_path)); sha = '98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a' if u_control else 'fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38' if r_control else '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5' if bsl_control else '0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8'
        require(len(native)==(398 if u_control or r_control else 384 if bsl_control else 100) and (native.get('TAA')=='true' and native.get('SHADER_STYLE')=='4' and native.get('RP_MODE')=='1' if u_control else native.get('TAA')=='true' and native.get('SHADER_STYLE')=='1' and native.get('RP_MODE')=='1' if r_control else native.get('AURORA')=='0' if bsl_control else native.get('AA_TYPE')=='2') and read_json(p['expected_options_source'])==native
                and p['expected_options_count']==(398 if u_control or r_control else 384 if bsl_control else 100) and p['expected_loaded_sha256']==sha
                and all(hashlib.sha256(file(p[k]).read_bytes()).hexdigest()==sha for k in ('pack','installed_pack')),
                'NeoForge original archive/named native options mismatch')
        for options in (file(p['pack_options']), Path(p['installed_pack']+'.txt')):
            if control: require(dc.properties(file(str(options)).read_text())==native,'Full actual named native override map required')
            if options.exists(): require(all(k in native and native[k]==v for k,v in dc.properties(options.read_text()).items()),
                                        'Extra/foreign native override')
        iris = dc.properties(file(str(profile/'config/iris.properties')).read_text())
        require(iris.get('shaderPack')==p['pack_name'] and iris.get('enableShaders')=='true'
                and iris.get('enableDebugOptions')=='false', 'NeoForge original Iris selection/debug mismatch')
        config = tomllib.loads(file(str(profile/'config/shaderlod-client.toml')).read_text())['lod']
        require(config.get('enabled') is False and config.get('preset')==('BALANCED' if control else 'PERFORMANCE') and config.get('debug') is False,
                'NeoForge original requires Off/Performance/debugfalse TOML')
        receipt_root = Path(p['expected_options_source']).parent
        producer = json.loads(file(str(receipt_root/'producer-command.json')).read_text())
        wanted = [argv[0], '-p', str(module), 'prepareClientRun', 'createClientLaunchScript', *argv[4:]]
        if control: wanted=[*argv[:3], 'captureNeoMakeupInputs', *argv[4:]]
        require(producer == wanted, 'Generated producer command does not match final launch flags/deadline')
        program = module/'build/moddev/clientRunProgramArgs.txt'; vm = module/'build/moddev/clientRunVmArgs.txt'
        for generated in (program, vm):
            copied = file(str(receipt_root/'producer-after'/generated.name))
            require(not any(x.is_symlink() for x in (generated,*generated.parents)) and
                    file(str(generated)).read_bytes()==copied.read_bytes(), 'Missing/changed generated receipt: '+generated.name)
        args = dc.neoforge_program_arguments(program.read_text())
        require(len(args)>=7 and args[:6]==['net.neoforged.fml.startup.Client','--version','26.2.0.88','--assetIndex','32','--assetsDir']
                and args[6]==str(Path.home()/'.gradle/caches/neoformruntime/assets')
                and args[7:]==['--gameDir',p['profile'],'--username','ShaderLOD','--fullscreen','--width','1920',
                               '--height','1200','--quickPlaySingleplayer',dc.WORLD],
                'Generated FML/gameDir/fullscreen/QuickPlay vector differs')
        vmargs = dc.neoforge_program_arguments(vm.read_text())
        expected_vm = ['--sun-misc-unsafe-memory-access=allow','--enable-native-access=ALL-UNNAMED','--add-opens',
                       'java.base/java.lang.invoke=ALL-UNNAMED','--add-exports','jdk.naming.dns/com.sun.jndi.dns=java.naming',
                       '-XstartOnFirstThread','-Dlog4j2.configurationFile='+str(module/'build/moddev/clientLog4j2.xml'),
                       '-Djava.net.preferIPv6Addresses=system','-Dneoforge.enableGameTest=true',
                       '-Dshaderlod.dev.portOriginalOnly=true','-Dshaderlod.dev.portRoundtrip=true',
                       '-Dshaderlod.dev.portRoundtripOriginal='+p['pack_name'],'-Dshaderlod.dev.startupDeadlineEpochMillis='+stamp]
        if control:
            named=['-Dshaderlod.dev.portRoundtrip=true','-Dshaderlod.dev.portNativeFamily='+('unbound-volume' if u_control else 'reimagined-volume' if r_control else 'bsl-volume' if bsl_control else 'makeup-volume'),'-Dshaderlod.dev.portRoundtripUi=true','-Dshaderlod.dev.portQualityRoundtrip=false','-Dshaderlod.dev.portRoundtripOriginal='+p['pack_name'],'-Dshaderlod.dev.startupDeadlineEpochMillis='+stamp]
            if main_control:
                named.insert(1,'-Dshaderlod.dev.portMainBindingCompat=true');named.insert(len(named)-1,'-Dshaderlod.bindingCompatibilityProbe=true')
            else: named.insert(1,'-Dshaderlod.dev.portBindingInputFix=true')
            require(vmargs[:10]==expected_vm[:10] and vmargs[10:]==named,'Exact CONTROL VM flags required')
            sodium=read_json(str(profile/'config/sodium-options.json'));require(sodium.get('performance',{}).get('use_no_error_g_l_context') is False,'Guarded Sodium context required')
            require(not (profile/'patched_shaders').exists(),'No source export diagnostic')
        elif original_only:
            if binding_fix:
                expected_vm.insert(len(expected_vm)-1, '-Dshaderlod.dev.portBindingInputFix=true')
            require(vmargs==expected_vm, 'Generated VM flags/deadline differ from pinned original-only producer')
        else:
            prefix = expected_vm[:10]
            require(vmargs[:10] == prefix, 'Generated native FML JVM prefix differs')
            # Authentic producer bytes above bind ordering; these property names/values reject all extras and duplicates.
            named = ['-Dshaderlod.dev.portRendererSpeedProbe=true', '-Dshaderlod.dev.portRoundtrip=true',
                     '-Dshaderlod.dev.portRoundtripOriginal='+p['pack_name'],
                     '-Dshaderlod.experimentalRendererPort092=true', '-Dshaderlod.dev.portBindingInputFix=true',
                     '-Dshaderlod.dev.startupDeadlineEpochMillis='+stamp]
            require(len(vmargs[10:]) == len(named) and set(vmargs[10:]) == set(named),
                    'Generated VM flags/deadline differ from explicit speed-mode producer')

    def command():
        argv = p['command']
        require(isinstance(argv, list) and len(argv) >= 2 and all(isinstance(a, str) for a in argv),
                'command must be an argv list')
        file(argv[0])
        expected_launcher = {
            'fabric2612': dc.ROOT / 'ports/26.1.2/runtime/gradle-9.4.0/bin/gradle',
            'fabric12111': dc.ROOT / 'ports/1.21.11/runtime/gradle-9.4.0/bin/gradle',
            'fabric262': dc.ROOT / 'ports/26.2/runtime/gradle-9.5.0/bin/gradle',
            'neoforge262': dc.ROOT / 'ports/26.2/runtime/gradle-9.5.0/bin/gradle',
        }.get(launcher, dc.ROOT / ('gradlew.bat' if sys.platform == 'win32' else 'gradlew'))
        require(argv[0] == str(expected_launcher),
                'Expected existing project Gradle launcher')
        if launcher == 'fabric2612':
            fabric2612_quality_command(argv)
            return
        if launcher == 'fabric262' and (p.get('bsl262VolumeControl') is True or p.get('makeup262VolumeControl') is True):
            fabric262_bsl_control_command(argv)
            return
        if launcher == 'fabric262' and '-PportBslOriginalSource=true' in argv:
            fabric262_bsl_source_command(argv)
            return
        if launcher == 'neoforge262':
            neoforge262_command(argv)
            return
        if launcher in ('fabric1211', 'fabric12111', 'fabric262'):
            fabric_command(argv)
            return
        require(argv.count('runClientLodOn' if p['mode'] == 'on' else 'runClientLodOff') == 1 and
                sum(a in ('runClientLodOn', 'runClientLodOff') for a in argv) == 1, 'Task/mode mismatch')
        props = {}
        for arg in argv:
            if arg.startswith('-P'):
                key, sep, value = arg[2:].partition('=')
                require(sep and key not in props, 'Missing/duplicate command property: ' + key)
                props[key] = value
        # build.gradle:24-31: minimal bypasses optional setup; other names must
        # be keys in the existing performance lock, never an invented enum.
        stack = props.get('performanceStack') or 'minimal'
        if stack != 'minimal':
            stacks = read_json(str(dc.ROOT / 'dependencies/performance-mods.lock.json'))['stacks']
            require(isinstance(stacks, dict) and stack in stacks,
                    'Unknown performanceStack: ' + stack)
        for key, expected in {'runDirectory': p['profile'], 'includeDh': str(p['include_dh']).lower(),
                              'comparisonFullscreen': str(p['fullscreen']).lower()}.items():
            require(props.get(key) == expected, key + ' disagrees with plan')
        if p.get('diagnostic_gpu_pass_timing'):
            require(p.get('benchmark_tuner_probe') and props.get('gpuTiming') == 'true'
                    and props.get('gpuPassTiming') == 'true' and props.get('gpuAttribution') == 'false'
                    and props.get('gpuSampleInterval') == '4', 'Diagnostic pass-timing command disagrees with plan')
        if p['already_built']:
            require(all(any(argv[i:i+2] == ['-x', task] for i in range(len(argv)-1))
                        for task in dc.SKIPPABLE), 'already_built lacks launcher skip-task vector')
    check('launcher_command', command)

    def mods():
        if launcher in ('fabric1211', 'fabric12111', 'fabric262', 'neoforge262'):
            directory = Path(p['profile']) / 'mods'
            require(directory.is_dir(), 'Profile mods directory missing')
            require(not p['include_dh'] and not list(directory.glob('*.jar')),
                    'Fabric renderer profile requires no extra mods/DH')
            return
        runtime = read_json(str(dc.ROOT / 'dependencies/runtime.lock.json'))
        expected = [runtime['artifacts']['distanthorizons']['filename']] if p['include_dh'] else []
        directory = Path(p['profile']) / 'mods'
        require(directory.is_dir(), 'Profile mods directory missing')
        actual = sorted(f.name for f in directory.glob('*.jar'))
        require(actual == expected, 'Profile mods disagree with pinned DH/minimal runtime')
    check('profile_mods', mods)

    if 'scene_input' in p:
        def control():
            actual = read_json(str(Path(p['profile']).parent / 'control.json'))
            require(isinstance(p['scene_input'], dict), 'scene_input must be object')
            mismatched = sorted(k for k, v in p['scene_input'].items() if actual.get(k) != v)
            require(not mismatched, 'control.json differs from scene_input: ' + ', '.join(mismatched))
        check('control_scene', control)
    if 'expected_loaded_sha256' in p:
        check('archive_hash', lambda: require(hashlib.sha256(file(p['pack']).read_bytes()).hexdigest() ==
              p['expected_loaded_sha256'], 'Selected archive hash mismatch'))
    if 'expected_loaded_pack' in p:
        check('loaded_pack_expectation', lambda: require(p['expected_loaded_pack'] == p['pack_name'],
                                                       'Expected loaded pack differs from selected pack'))
    if 'expected_options_source' in p:
        check('expected_options', lambda: require(len(read_json(p['expected_options_source'])) ==
              p['expected_options_count'], 'Expected loaded option count differs from saved map'))
    if 'sourceReview' in p:
        check('source_review_pin', lambda: require(freeze is not None and
              hashlib.sha256(file(p['sourceReview']).read_bytes()).hexdigest() ==
              freeze['files'].get(p['sourceReview']), 'sourceReview missing from freeze.files or hash differs'))
    if 'protocol_capture_name' in p:
        def capture():
            name = p['protocol_capture_name']
            require(isinstance(name, str) and re.fullmatch(r'[a-zA-Z0-9_-]{1,64}', name), 'Invalid named capture')
            argv = p['command']
            destinations = [a.split('=', 1)[1] for a in argv if a.startswith('-PcontrolCaptureReceipt=')]
            originals = [a.split('=', 1)[1] for a in argv if a.startswith('-PcontrolCaptureOriginal=')]
            require(len(destinations) == len(originals) == 1, 'Capture destination/original command missing or duplicated')
            destination = Path(destinations[0])
            require(destination.is_absolute() and '..' not in destination.parts,
                    'Capture destination must be absolute without parent traversal')
            ancestor = destination
            while not ancestor.exists() and not ancestor.is_symlink():
                ancestor = ancestor.parent
            require(ancestor.is_dir(), 'Capture destination/ancestor is not a directory: ' + str(ancestor))
            require(os.access(ancestor, os.W_OK | os.X_OK),
                    'Capture destination/ancestor lacks write/traverse access: ' + str(ancestor))
            original = originals[0]
            require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+() '-]{0,191}\.zip", original), 'Invalid original archive basename')
            dc.inspect_pack(file(str(Path(p['profile']) / 'shaderpacks' / original)))
            if not historical:
                require(not (Path(destinations[0]) / (name + '.json')).exists(), 'Named receipt already exists')
        check('named_capture', capture)
    if check_built and p.get('already_built'):
        if launcher in ('fabric1211', 'fabric12111', 'fabric262'):
            check('current_build_receipt', lambda: require(False,
                  'Fabric build receipt requires parent artifact verification; NeoForge receipt cannot admit Fabric'))
        else:
            check('current_build_receipt', dc.validate_built)
    if receipt is not None:
        def capture_receipt():
            require(isinstance(receipt, dict), 'Capture receipt must be object')
            mismatched = []
            for key, expected in {'status': 'saved-observations', 'irisLoadedPack': p['pack_name'],
                'irisSelectedPack': p['pack_name'], 'selectedArchiveSha256': p['expected_loaded_sha256'],
                'loadedIrisOptions': read_json(p['expected_options_source']), 'irisFallback': False,
                'shaderPackInUse': True, 'fullscreen': True, 'focused': True,
                'framebufferWidth': 1920, 'framebufferHeight': 1200}.items():
                if receipt.get(key) != expected:
                    mismatched.append(key)
            require(not mismatched, 'ControlCaptureReceipt mismatches: ' + ', '.join(mismatched))
        check('recorded_capture_fields', capture_receipt)
    return {'scope': 'historical static inspection' if historical else 'static prelaunch consistency',
            'checked': checked, 'errors': errors,
            'unverified': ['live lane ownership/availability', 'actual JVM/argfile consumption', 'optional runtime artifact availability/hashes',
                           'driver compilation', 'linked uniforms/executed work', 'quality', 'performance']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('plan', type=Path, help='saved plan.json or owner.json containing plan')
    parser.add_argument('--freeze', type=Path, help='existing freeze.json; only selected sourceReview pin checked')
    parser.add_argument('--receipt', type=Path, help='existing ControlCaptureReceipt JSON; not a new runtime check')
    parser.add_argument('--historical', action='store_true', help='inspect historical deadline/receipt; never launch admission')
    parser.add_argument('--check-built', action='store_true', help='reuse dev_client.validate_built; hashes existing source/build only')
    args = parser.parse_args()
    try:
        record = json.loads(args.plan.read_text())
        freeze = json.loads(args.freeze.read_text()) if args.freeze else None
        receipt = json.loads(args.receipt.read_text()) if args.receipt else None
        result = check_plan(record, freeze=freeze, receipt=receipt,
                            historical=args.historical, check_built=args.check_built)
    except (ValueError, OSError) as error:
        result = {'errors': [{'check': 'input_json', 'error': str(error)}], 'checked': []}
    print(json.dumps(result, indent=2))
    return 1 if result['errors'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
