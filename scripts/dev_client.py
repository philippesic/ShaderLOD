#!/usr/bin/env python3
"""Fast manual NeoForge clients, persistent unless an explicit owned stop time is supplied."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import shlex
import subprocess
import sys
import time
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROFILES = {
    'on': ROOT / 'run-workloads/manual-lod-on-20261006-1728/enabled',
    'off': ROOT / 'run-workloads/manual-lod-off-20261006-1722/original',
}
LOGS = ROOT / 'dev/logs/dev-client'
LOCK = ROOT / 'dev/logs/.proof-matrix.lock'
WORLD = 'ShaderLOD-Landscape'
CLIENT_MARKERS = ('net.neoforged.devlaunch.Main', 'net.neoforged.fml.startup.Client',
                  'net.minecraft.client.main.Main',
                  'cpw.mods.bootstraplauncher.BootstrapLauncher', 'forgeclientdev',
                  'neoforgeclient', 'clientLodOnRunProgramArgs.txt',
                  'clientLodOffRunProgramArgs.txt', 'clientIrisRunProgramArgs.txt',
                  'clientRunProgramArgs.txt', 'net.fabricmc.devlaunchinjector.Main',
                  'net.fabricmc.loader.impl.launch.knot.KnotClient')
FATAL = re.compile(r'BUILD FAILED|Failed to load shaderpack|Failed to create shader rendering pipeline|'
                   r'Shader compilation (?:failed|error)|Error compiling|Exception in thread|'
                   r'ShaderPackLoadException|fallback=true|Landscape control rejected|'
                   r'ShaderLOD explicit settings failed', re.I)
SKIPPABLE = ('compileJava', 'compileDevClientJava', 'processResources', 'processDevClientResources')


def require(ok, message):
    if not ok:
        raise ValueError(message)


def validate_mixin_config_names(root=ROOT):
    """The normal On client loads both mods; FML decorates each config once."""
    import tomllib
    owners = {}
    for relative in ('src/main/templates/META-INF/neoforge.mods.toml',
                     'src/devClient/resources/META-INF/neoforge.mods.toml'):
        document = tomllib.loads((root / relative).read_text())
        for entry in document.get('mixins', []):
            name = entry['config']
            require(name not in owners,
                    'Duplicate Mixin config ' + name + ' in ' + relative +
                    ' and ' + owners.get(name, '') + '; use distinct normal/private names')
            owners[name] = relative


def stop_deadline(value):
    """Translate an explicit absolute time once; wall-clock changes cannot defer it."""
    if value is None:
        return None
    try:
        instant = datetime.fromisoformat(value.replace('Z', '+00:00'))
    except (ValueError, AttributeError) as error:
        raise ValueError('--stop-at requires an ISO timestamp with a timezone') from error
    require(instant.tzinfo is not None and instant.utcoffset() is not None,
            '--stop-at requires an explicit timezone')
    remaining = instant.timestamp() - time.time()
    require(0 < remaining <= 12 * 3600, '--stop-at must be in the next 12 hours')
    return time.monotonic() + remaining


def deadline_stop(session, state, deadline):
    """Independent supervisor stop, using the existing exact-client identity checks."""
    if deadline is None or time.monotonic() < deadline or 'deadline_stop_requested_utc' in state:
        return
    state['deadline_stop_requested_utc'] = datetime.now(timezone.utc).isoformat()
    # Publish freshly discovered proofs before stop rereads the owner manifest.
    try:
        write_json(session / 'owner.json', state)
        stop(session)
        state['deadline_stop_completed_utc'] = datetime.now(timezone.utc).isoformat()
    except Exception as error:
        # Keep ownership; never broaden targets or retry a refused identity check.
        state['deadline_stop_error'] = str(error)
        print('Deadline stop refused or failed: ' + str(error), file=sys.stderr, flush=True)
    try:
        write_json(session / 'owner.json', state)
    except OSError as error:
        state['deadline_stop_receipt_error'] = str(error)
        print('Deadline stop receipt failed: ' + str(error), file=sys.stderr, flush=True)


def ready_capture(session, state):
    """Arm the existing capture control once, after actual launcher readiness."""
    name = state['plan'].get('capture_when_ready')
    if name is None or 'ready_capture_requested_utc' in state:
        return
    control = Path(state['plan']['profile']).parent / 'control.json'
    data = json.loads(control.read_text())
    data['capture'] = name
    write_json(control, data)
    state['ready_capture_requested_utc'] = datetime.now(timezone.utc).isoformat()
    write_json(session / 'owner.json', state)


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2) + '\n')
    from benchmark_file_locks import replace_published
    replace_published(temporary, path)


def properties(text):
    return {m[1].strip(): m[2].strip() for line in text.splitlines()
            if (m := re.match(r'^\s*([^#!][^=:]*?)[=:](.*)$', line))}


def java_property_value(value):
    """ASCII value accepted by Properties.load(InputStream), including UTF16 escapes."""
    result=[]
    for i,char in enumerate(value):
        if char=='\\':result.append('\\\\')
        elif char in '\n\r\t\f':result.append({'\n':'\\n','\r':'\\r','\t':'\\t','\f':'\\f'}[char])
        elif i==0 and char==' ':result.append('\\ ')
        elif ord(char)>126 or ord(char)<32:
            units=char.encode('utf-16-be')
            result.extend('\\u'+units[j:j+2].hex() for j in range(0,len(units),2))
        else:result.append(char)
    return ''.join(result)


def java_properties(text):
    """Iris ASCII-key values, with Java escapes; Minecraft options stay separate."""
    def decode(value):
        result=[];i=0
        while i<len(value):
            char=value[i];i+=1
            if char=='\\':
                require(i<len(value),'Truncated Java property escape');char=value[i];i+=1
                if char=='u':
                    digits=value[i:i+4];require(len(digits)==4 and re.fullmatch('[0-9a-fA-F]{4}',digits),'Invalid Java Unicode escape');char=chr(int(digits,16));i+=4
                else:char={'n':'\n','r':'\r','t':'\t','f':'\f'}.get(char,char)
            result.append(char)
        return ''.join(result).encode('utf-16-be','surrogatepass').decode('utf-16-be')
    return {m[1].strip():decode(m[2].lstrip(' \t\f')) for line in text.splitlines()
            if (m:=re.match(r'^\s*([^#!][^=:]*?)[=:](.*)$',line))}


def replace_properties(text, values, separator='='):
    lines = [line for line in text.splitlines()
             if not any(re.match(r'^\s*' + re.escape(key) + r'\s*[:=]', line) for key in values)]
    return '\n'.join(lines + [key + separator + value for key, value in values.items()]) + '\n'


def inspect_pack(path):
    require(path.is_file() and path.suffix.lower() == '.zip', 'Selected shader ZIP missing: ' + str(path))
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) and all(not n.startswith('/') and '..' not in Path(n).parts
                                                    and '\\' not in n for n in names), 'Unsafe/duplicate shader ZIP members')
        shaders = [n for n in names if n.endswith('shaders/shaders.properties')]
        require(len(shaders) == 1, 'ZIP must contain exactly one shaders/shaders.properties')
        prefix = shaders[0][:-len('shaders.properties')]
        require(any(n.startswith(prefix) and n.endswith(('.fsh', '.vsh')) for n in names),
                'ZIP has no shader programs')
        # Read the selected pack descriptor; this also checks its CRC without unpacking the world/ZIP.
        archive.read(shaders[0]).decode('utf-8-sig')
    return shaders[0]


def plan(args):
    if args.mode == 'on':
        validate_mixin_config_names()
    background_launch = sys.platform == 'darwin' and not getattr(args, 'foreground', False)
    absolute_stop = getattr(args, 'stop_at', None)
    stop_deadline(absolute_stop)  # Reject invalid/expired requests before any mutation.
    apply_settings = getattr(args, 'apply_lod_settings', False)
    consumer_request = getattr(args, 'consumer_uniforms', None)
    diagnostic_gpu_pass_timing = getattr(args, 'diagnostic_gpu_pass_timing', False)
    if consumer_request:
        consumer_request = consumer_request.resolve()
        require(apply_settings and args.mode == 'on', '--consumer-uniforms requires explicit On-task settings Apply')
        require(consumer_request.is_file() and consumer_request.stat().st_size <= 8192,
                'Consumer request missing or exceeds 8192 bytes: ' + str(consumer_request))
    lod_state = getattr(args, 'lod_state', None)
    require(not lod_state or apply_settings, '--lod-state requires --apply-lod-settings')
    if apply_settings:
        require(args.mode == 'on', '--apply-lod-settings requires the On task with the main mod loaded')
        require(lod_state in ('on', 'off'), '--apply-lod-settings requires --lod-state on or off')
        require(args.pack, '--apply-lod-settings requires an explicit source --pack')
    profile = (args.profile or PROFILES[args.mode]).resolve()
    benchmark_control = getattr(args, 'benchmark_control_probe', None)
    if benchmark_control:
        benchmark_control = benchmark_control.resolve()
        require(benchmark_control.is_file() and not apply_settings and not consumer_request, 'Benchmark control requires a control file without unrelated startup mutation')
    benchmark_probe = getattr(args, 'benchmark_tuner_probe', None)
    require(not (benchmark_control and benchmark_probe), 'One benchmark control owner required')
    shortlist_combination_runtime = getattr(args, 'shortlist_combination_runtime', False)
    require(type(shortlist_combination_runtime) is bool and (not shortlist_combination_runtime or (benchmark_control is not None and args.mode == 'on' and not args.dh)),
            'Shortlist combination runtime requires neutral main-mod control without DH')
    shortlist_g10_runtime = getattr(args, 'shortlist_g10_runtime', False)
    require(type(shortlist_g10_runtime) is bool and (not shortlist_g10_runtime or shortlist_combination_runtime and getattr(args,'g10_benchmark_runtime',False) is True), 'Private combined G10 requires explicit shortlist combination runtime')
    g10_benchmark_runtime = getattr(args, 'g10_benchmark_runtime', False)
    require(type(g10_benchmark_runtime) is bool and (not g10_benchmark_runtime or (benchmark_control is not None and args.mode == 'on' and not args.dh)),
            'G10 runtime requires neutral main-mod control without DH')
    c01_bounds_runtime = getattr(args, 'c01_bounds_runtime', False)
    require(type(c01_bounds_runtime) is bool and (not c01_bounds_runtime or (benchmark_control is not None and args.mode == 'on' and not args.dh)),
            'C01 runtime requires neutral main-mod control without DH')
    bsl_mip_witness_runtime = getattr(args, 'bsl_mip_witness_runtime', False)
    require(type(bsl_mip_witness_runtime) is bool, 'BSL witness hook selection must be boolean')
    require(not bsl_mip_witness_runtime or (benchmark_control is not None and args.mode == 'on'),
            'BSL witness hooks require the neutral control and main mod')
    if benchmark_probe:
        benchmark_probe = benchmark_probe.resolve()
        require(args.mode == 'on' and not apply_settings and not consumer_request,
                'Benchmark probe requires On task without landscape settings/consumer actions')
        require(benchmark_probe.is_file(), 'Benchmark probe control file missing')
    world = getattr(args, 'world', WORLD)
    require(re.fullmatch(r'[A-Za-z0-9_-]{1,64}', world), 'Safe saved-world name required')
    require(world == WORLD or benchmark_control is not None, 'Custom world requires the neutral benchmark control')
    require(benchmark_probe or (profile / 'saves' / world / 'level.dat').is_file(),
            'Prepared landscape world missing: ' + str(profile) + '; use an existing --profile')
    iris = profile / 'config/iris.properties'
    require(iris.is_file(), 'Iris config missing: ' + str(iris))
    selected = args.pack or java_properties(iris.read_text()).get('shaderPack')
    require(bool(selected), 'No selected pack; provide --pack ZIP')
    selected_path = Path(selected).expanduser()
    # A bare name selects an installed ZIP; a path selects an external ZIP to copy explicitly.
    source = (profile / 'shaderpacks' / selected_path if selected_path.name == str(selected_path)
              else selected_path.resolve())
    descriptor = inspect_pack(source)
    requested_preset = getattr(args, 'preset', None)
    if requested_preset or apply_settings:
        require((profile.parent / 'control.json').is_file(),
                '--preset/--apply-lod-settings requires a prepared landscape control.json')
    options = args.pack_options.resolve() if args.pack_options else Path(str(source) + '.txt')
    if args.pack_options:
        require(options.is_file(), 'Shader options file missing: ' + str(options))
    target = profile / 'shaderpacks' / source.name
    runtime = json.loads((ROOT / 'dependencies/runtime.lock.json').read_text())
    performance_stack = getattr(args, 'performance_stack', 'minimal')
    if performance_stack != 'minimal':
        performance_lock = json.loads((ROOT / 'dependencies/performance-mods.lock.json').read_text())
        require(performance_stack in performance_lock['stacks'], 'Unknown performance stack: ' + str(performance_stack))
    dh = runtime['artifacts']['distanthorizons']['filename']
    jars = sorted(p.name for p in (profile / 'mods').glob('*.jar'))
    require(jars == ([dh] if args.dh else []),
            'Profile mod JARs disagree with --dh/managed runtime: ' + str(jars))
    command = [str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')),
               'runClientLodOn' if args.mode == 'on' else 'runClientLodOff',
               '-PrunDirectory=' + str(profile), '-PperformanceStack=' + performance_stack,
               '-PincludeDh=' + str(args.dh).lower(), '-PcomparisonFullscreen=' + str(not background_launch and not getattr(args, 'windowed', False)).lower(),
               '-PbackgroundLaunch=' + str(background_launch).lower(),
               '-PgpuBenchmark=false', '-PbenchmarkOnly=false', '-PmeasureWorkload=false',
               '-PmeasureOnly=false', '-PsmokeTest=false', '-PgpuTiming=false', '-PcpuTiming=false',
               '--console=plain']
    if diagnostic_gpu_pass_timing:
        require(benchmark_probe is not None, 'Diagnostic GPU pass timing requires the benchmark control probe')
        command[command.index('-PgpuTiming=false')] = '-PgpuTiming=true'
        command += ['-PgpuPassTiming=true', '-PgpuAttribution=false', '-PgpuSampleInterval=4']
    if consumer_request:
        command += ['-PsettledConsumerUniforms=' + str(consumer_request)]
    if benchmark_probe:
        command += ['-PbenchmarkTunerProbe=' + str(benchmark_probe)]
    if benchmark_control:
        command += ['-PbenchmarkControlProbe=' + str(benchmark_control)]
        command += ['-PbslMipWitnessRuntime=' + str(bsl_mip_witness_runtime).lower()]
        if c01_bounds_runtime:command += ['-Pc01Bounds=true','-Pc01NativeR66=true']
        if shortlist_combination_runtime:command += ['-PshortlistCombinationRuntime=true']
        if shortlist_g10_runtime:command += ['-PshortlistG10Runtime=true']
        if g10_benchmark_runtime:command += ['-Pg10BenchmarkRuntime=true','-PfragmentWorkProbe=true']
    if args.already_built:
        for task in SKIPPABLE:
            command += ['-x', task]
    return dict(mode=args.mode, preset=requested_preset, profile=str(profile), pack=str(source), pack_name=source.name,
                apply_lod_settings=apply_settings, lod_state=lod_state,
                pack_options=str(options) if options.is_file() else None,
                installed_pack=str(target), archive_descriptor=descriptor,
                fullscreen=not background_launch and not getattr(args, 'windowed', False), windowed=getattr(args, 'windowed', False), background_launch=background_launch, include_dh=args.dh, already_built=args.already_built,
                command=command, performance_stack=performance_stack, ready_timeout=args.ready_timeout,
                benchmark_tuner_probe=str(benchmark_probe) if benchmark_probe else None,
                benchmark_control_probe=str(benchmark_control) if benchmark_control else None,
                bsl_mip_witness_runtime=bsl_mip_witness_runtime,
                c01_bounds_runtime=c01_bounds_runtime,
                shortlist_combination_runtime=shortlist_combination_runtime,
                g10_benchmark_runtime=g10_benchmark_runtime,shortlist_g10_runtime=shortlist_g10_runtime,
                diagnostic_gpu_pass_timing=diagnostic_gpu_pass_timing,
                world=world,
                stop_at_utc=absolute_stop,
                world_policy='private benchmark save via normal mod APIs' if benchmark_probe else 'reuse existing world; no copy or world hashing')


def prepare(p, backup):
    profile = Path(p['profile'])
    # Preserve exact old bytes in the launch session before each mutation.
    def update(path, data):
        prior = path.read_bytes() if path.exists() else None
        if prior == data:
            return
        if prior is not None:
            destination = backup / path.relative_to(profile.parent)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(prior)
            require(destination.read_bytes() == prior, 'Configuration backup mismatch: ' + str(path))
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_name(path.name + '.shaderlod-prepare.tmp')
        require(not temporary.exists(), 'Preparation temporary file already exists: ' + str(temporary))
        temporary.write_bytes(data)
        try:
            current = path.read_bytes() if path.exists() else None
            require(current == prior, 'Configuration changed during preparation: ' + str(path))
            temporary.replace(path)
        finally:
            temporary.unlink(missing_ok=True)

    source, target = Path(p['pack']), Path(p['installed_pack'])
    if source.resolve() != target.resolve():
        update(target, source.read_bytes())
    options_target = Path(str(target) + '.txt')
    if p['pack_options']:
        options_source = Path(p['pack_options'])
        if options_source.resolve() != options_target.resolve():
            update(options_target, options_source.read_bytes())
    elif not options_target.exists():
        update(options_target, b'# No overrides: selected shader defaults.\n')
    iris = profile / 'config/iris.properties'
    update(iris, replace_properties(iris.read_text(), {'shaderPack': java_property_value(p['pack_name']),
                                                     'enableShaders': 'true'}).encode())
    options = profile / 'options.txt'
    update(options, replace_properties(options.read_text() if options.exists() else '',
                                       {'fullscreen': str(p['fullscreen']).lower(), 'pauseOnLostFocus': 'false'}, ':').encode())
    if p.get('background_launch') or p.get('requested_gl_context', 'native') != 'native':
        # An early NeoForge window may reuse an already-created context, ignoring
        # later GLFW hints. Explicit benchmark contexts require a fresh window.
        # Existing preparation backup/CAS and whole-profile restoration apply.
        fml = profile / 'config/fml.toml'
        text = fml.read_text() if fml.exists() else ''
        text = re.sub(r'(?m)^earlyWindowControl\s*=.*$', 'earlyWindowControl = false', text) if re.search(r'(?m)^earlyWindowControl\s*=', text) else text + '\nearlyWindowControl = false\n'
        update(fml, text.encode())
    control = profile.parent / 'control.json'
    if control.exists():
        data = json.loads(control.read_text())
        data['enabled'] = p['lod_state'] == 'on' if p.get('apply_lod_settings') else p['mode'] == 'on'
        if p.get('apply_lod_settings'):
            data['applyPackSettings'] = True
        else:
            data.pop('applyPackSettings', None)
        if p.get('preset'):
            data['preset'] = p['preset']
        # The inspection command does not arm a stale capture request.
        data.pop('capture', None)
        update(control, (json.dumps(data, indent=2) + '\n').encode())
    require(java_properties(iris.read_text()).get('shaderPack') == target.name and target.is_file()
            and options_target.is_file(), 'Selected pack/config/options disagree after prepare')
    inspect_pack(target)


def source_fingerprint():
    paths = [ROOT / n for n in ('build.gradle', 'settings.gradle', 'gradle.properties',
                               'gradlew', 'gradle/wrapper/gradle-wrapper.properties',
                               'gradle/wrapper/gradle-wrapper.jar',
                               'dependencies/runtime.lock.json', 'dependencies/performance-mods.lock.json',
                               'gradle/linux-arm64.gradle', 'dependencies/linux-arm64-runtime.lock.json')]
    runtime = json.loads((ROOT / 'dependencies/runtime.lock.json').read_text())
    paths += [ROOT / 'dependencies/runtime' / runtime['artifacts'][name]['filename'] for name in ('iris', 'sodium')]
    source_roots=['src/main','src/devClient']
    if (ROOT/'src/neoforge1211').is_dir():source_roots.append('src/neoforge1211')
    if (ROOT/'src/benchmarkCore').is_dir():source_roots.append('src/benchmarkCore')
    for name in source_roots:
        directory = ROOT / name
        require(directory.is_dir(), 'Source directory missing: ' + str(directory))
        paths += sorted(p for p in directory.rglob('*') if p.is_file())
    return {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}


def build_fingerprint():
    """Small source/classes receipt only; never traverse profiles/worlds."""
    paths = []
    build_roots=['build/classes/java/main','build/classes/java/devClient','build/resources/main','build/resources/devClient']
    if (ROOT/'src/benchmarkCore').is_dir():build_roots.append('build/classes/java/benchmarkCore')
    for name in build_roots:
        directory = ROOT / name
        require(directory.is_dir(), 'Build/source directory missing: ' + str(directory))
        paths += sorted(p for p in directory.rglob('*') if p.is_file())
    return source_fingerprint() | {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}


def validate_built():
    # A receipt can describe a source/output pair without proving resources were
    # processed together. Static mixin registrations must match before skipping Gradle.
    mixins = ROOT / 'src/devClient/resources/shaderlod-dev.mixins.json'
    processed = ROOT / 'build/resources/devClient/shaderlod-dev.mixins.json'
    require(processed.is_file() and mixins.read_bytes() == processed.read_bytes(),
            'Developer mixin resources are stale; run processDevClientResources before --already-built')
    receipt = LOGS / 'built.json'
    require(receipt.exists(), 'No successful launcher build receipt; launch without --already-built first')
    require(json.loads(receipt.read_text()) == build_fingerprint(),
            'Sources/classes/resources changed; omit --already-built to compile current changes')


def compile_current(*, env=None, timeout=180):
    """One offline producer; retain the old receipt and replace it only by CAS."""
    before=source_fingerprint();receipt=LOGS/'built.json'
    previous=receipt.read_bytes() if receipt.exists() else None
    result=subprocess.run([str(ROOT/('gradlew.bat' if os.name == 'nt' else 'gradlew')),'compileJava','compileDevClientJava','processResources','processDevClientResources',
                           '--offline','--no-daemon','--console=plain'],cwd=ROOT,env=env,timeout=timeout)
    require(result.returncode==0,'Offline build failed; no runtime launched')
    require(source_fingerprint()==before,'Sources changed during build; receipt withheld')
    require((receipt.read_bytes() if receipt.exists() else None)==previous,'Shared build receipt changed; replacement withheld')
    if previous is not None:
        (LOGS/('built.before-'+str(time.time_ns())+'.json')).write_bytes(previous)
    write_json(receipt,build_fingerprint())
    validate_built()


def process_table():
    if os.name == 'nt':
        script = ('Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId,CreationDate,CommandLine '
                  '| ConvertTo-Json -Compress')
        rows = json.loads(subprocess.run(['powershell', '-NoProfile', '-Command', script],
                          text=True, capture_output=True, check=True, timeout=5).stdout)
        return {int(r['ProcessId']): dict(pid=int(r['ProcessId']), ppid=int(r['ParentProcessId']),
                birth=r['CreationDate'], argv=r['CommandLine'] or '') for r in rows}
    output = subprocess.run(['ps', '-axo', 'pid=,ppid=,lstart=,command='],
                            text=True, capture_output=True, check=True,
                            timeout=15 if sys.platform == 'darwin' else 5).stdout
    rows = {}
    for line in output.splitlines():
        fields = line.split(None, 7)
        if len(fields) == 8:
            pid, ppid = map(int, fields[:2])
            rows[pid] = dict(pid=pid, ppid=ppid, birth=' '.join(fields[2:7]), argv=fields[7])
    return rows


def clients(table):
    return {pid: row for pid, row in table.items() if 'java' in row['argv'].lower()
            and any(marker in row['argv'] for marker in CLIENT_MARKERS)}


def identity(row):
    # Parent PID can change after an exit; birth + complete argv rejects PID reuse.
    return {key: row[key] for key in ('pid', 'birth', 'argv')}


def mac_process_argv(row):
    """Kernel argv vector, never flattened ps text or the trailing environment."""
    import ctypes
    import struct
    try:
        library = ctypes.CDLL(None, use_errno=True)
        mib = (ctypes.c_int * 3)(1, 49, int(row['pid']))  # SDK CTL_KERN / KERN_PROCARGS2
        size = ctypes.c_size_t(0)
        require(library.sysctl(mib, 3, None, ctypes.byref(size), None, 0) == 0,
                'Native argv size unavailable')
        require(4 < size.value <= 1024 * 1024, 'Native argv size out of bounds')
        buffer = ctypes.create_string_buffer(size.value)
        require(library.sysctl(mib, 3, buffer, ctypes.byref(size), None, 0) == 0,
                'Native argv unavailable')
        raw = buffer.raw[:size.value]
        count = struct.unpack_from('i', raw)[0]
        require(0 < count <= 10000, 'Native argv count out of bounds')
        cursor = raw.index(b'\0', 4) + 1  # executable path, then padding, then argc arguments
        while cursor < len(raw) and raw[cursor] == 0:
            cursor += 1
        arguments = []
        for _ in range(count):
            end = raw.index(b'\0', cursor)
            arguments.append(raw[cursor:end].decode('utf-8', errors='strict'))
            cursor = end + 1
        current = process_table().get(int(row['pid']))
        require(current is not None and identity(current) == identity(row), 'PID identity changed during argv read')
        return arguments
    except (ValueError, OSError, UnicodeError, KeyError):
        return None


def argv_profile_matches(arguments, profile):
    positions = [i for i, value in enumerate(arguments) if value == '--gameDir']
    return len(positions) == 1 and positions[0] + 1 < len(arguments) and \
        Path(arguments[positions[0] + 1]).resolve() == profile


_NEOFORGE_ARGFILE_HASHES = {}


def neoforge_program_arguments(text):
    """ModDev's one-argument-per-line subset of DevLaunch 1.0.2 StreamTokenizer.

    UTF-8, ASCII whitespace, standalone # comments, quote characters and Java
    quoted escapes; never shell parsing, nested @ expansion or split tokens.
    Unsupported/malformed generated syntax refuses instead of approximating it.
    """
    result = []
    escapes = {'a': '\a', 'b': '\b', 'f': '\f', 'n': '\n', 'r': '\r', 't': '\t', 'v': '\v'}
    for line in re.split(r'\r\n|\r|\n', text):
        value = line.strip(' \t')
        if not value or value.startswith('#'):
            continue
        if value[0] in ('"', "'"):
            quote, decoded, index = value[0], [], 1
            while index < len(value) and value[index] != quote:
                char = value[index]; index += 1
                if char == '\\':
                    require(index < len(value), 'Truncated quoted argument escape')
                    char = value[index]; index += 1
                    if char in '01234567':
                        digits = char
                        limit = 3 if char in '0123' else 2
                        while len(digits) < limit and index < len(value) and value[index] in '01234567':
                            digits += value[index]; index += 1
                        char = chr(int(digits, 8))
                    else:
                        char = escapes.get(char, char)
                decoded.append(char)
            require(index == len(value) - 1 and value[index] == quote, 'Unterminated/combined quoted argument')
            value = ''.join(decoded)
        else:
            require(not any(ord(c) <= 32 or c in ('#', '"', "'") for c in value),
                    'Expected one generated argument per line')
        require(value and not value.startswith('@') and '\x00' not in value,
                'Empty/nested argument-file argument refused')
        result.append(value)
    return result


def process_cwd(row):
    if os.name == 'nt':
        import psutil
        try:
            return Path(psutil.Process(int(row['pid'])).cwd()).resolve()
        except psutil.Error:
            return None
    if sys.platform.startswith('linux'):
        try:
            return Path('/proc/' + str(row['pid']) + '/cwd').resolve()
        except OSError:
            return None
    if sys.platform == 'darwin':
        result = subprocess.run(['lsof', '-n', '-P', '-a', '-p', str(row['pid']), '-d', 'cwd', '-Fn'],
                                text=True, capture_output=True, timeout=12)
        lines = result.stdout.splitlines()
        paths = [line[1:] for line in lines if line.startswith('n')]
        if result.returncode == 0 and 'p' + str(row['pid']) in lines and len(paths) == 1:
            return Path(paths[0]).resolve()
    return None


def owned_argfile_bytes(path, birth):
    require(path.is_absolute() and not any(p.is_symlink() for p in (path, *path.parents)),
            'Symlink/relative generated argfile')
    before = path.stat()
    require(path.is_file() and before.st_size <= 65536, 'Invalid generated argfile')
    require(max(before.st_mtime, before.st_ctime) < birth + 1, 'Argfile changed after process birth')
    data = path.read_bytes(); after = path.stat()
    stable = lambda stat: (stat.st_dev, stat.st_ino, stat.st_size, stat.st_mtime_ns, stat.st_ctime_ns)
    require(stable(before) == stable(after), 'Argfile changed during read')
    return data


def neoforge_argfile_profile_matches(row, arguments, profile):
    """Only inspected root/port program-file+cwd contracts; ports require absolute gameDir."""
    try:
        main = 'net.neoforged.devlaunch.Main'
        require(arguments.count(main) == 1, 'Ambiguous NeoForge main')
        index = arguments.index(main)
        require(len(arguments[index+1:]) == 1 and arguments[index+1].startswith('@'),
                'Expected sole NeoForge program argfile')
        require(not any('--gameDir' in a for a in arguments[:index]), 'Direct/property gameDir conflict')
        path = Path(arguments[index+1][1:])
        allowed = {ROOT / 'build/moddev' / (run + 'RunProgramArgs.txt'):
                   'cpw.mods.bootstraplauncher.BootstrapLauncher' for run in ('clientLodOn', 'clientLodOff', 'packagedMain')}
        port_paths = {ROOT / 'ports' / version / 'neoforge/build/moddev/clientRunProgramArgs.txt'
                      for version in ('1.21.11', '26.1.2', '26.2', '26.3')}
        allowed.update({path: 'net.neoforged.fml.startup.Client' for path in port_paths})
        require(path.is_absolute() and path in allowed and
                not any(p.is_symlink() for p in (path, *path.parents)), 'Foreign/symlink NeoForge argfile')
        if os.name == 'nt':
            match = re.fullmatch(r'/Date\((\d+)\)/', row['birth'])
            require(match is not None, 'Windows process birth unavailable')
            birth = int(match.group(1)) / 1000
        else:
            birth = time.mktime(time.strptime(row['birth'], '%a %b %d %H:%M:%S %Y'))
        # ps birth has one-second resolution. Files produced after that second are unproven.
        data = owned_argfile_bytes(path, birth)
        argv = neoforge_program_arguments(data.decode('utf-8', errors='strict'))
        require(argv and argv[0] == allowed[path], 'Unexpected NeoForge bootstrap')
        positions = [i for i, value in enumerate(argv) if value == '--gameDir']
        require(len(positions) == 1 and positions[0] + 1 < len(argv) and
                not any(value.startswith('--gameDir=') for value in argv), 'Missing/duplicate/conflicting gameDir')
        game_dir = argv[positions[0] + 1]
        if path in port_paths:
            require(Path(game_dir).is_absolute() and Path(game_dir).resolve() == profile,
                    'Port NeoForge requires its actual absolute gameDir/profile')
        vm_digest = None
        if game_dir == '.':
            prefix = arguments[:index]
            require(not any(a.partition('=')[0] == '-Duser.dir' for a in prefix),
                    'Relative gameDir with native user.dir override')
            vm_path = path.with_name(path.name.replace('ProgramArgs.txt', 'VmArgs.txt'))
            references = [a for a in prefix if a.startswith('@')]
            if os.name == 'nt':
                references = ['@' + str(Path(a[1:])) for a in references]
            require(references == ['@' + str(vm_path)], 'Relative gameDir requires its exact generated VM argfile')
            vm_data = owned_argfile_bytes(vm_path, birth)
            vm_args = neoforge_program_arguments(vm_data.decode('utf-8', errors='strict'))
            require(not any(a.partition('=')[0] == '-Duser.dir' for a in vm_args),
                    'Relative gameDir with VM user.dir override')
            vm_digest = hashlib.sha256(vm_data).hexdigest()
        cwd = process_cwd(row)
        require(cwd == profile, 'NeoForge process cwd/profile mismatch')
        require(game_dir == '.' or Path(game_dir).is_absolute() and Path(game_dir).resolve() == profile,
                'NeoForge program gameDir/profile mismatch')
        current = process_table().get(int(row['pid']))
        require(current is not None and identity(current) == identity(row), 'Process identity changed during cwd read')
        key = (row['pid'], row['birth'], row['argv'], str(path))
        digest = (hashlib.sha256(data).hexdigest(), vm_digest)
        require(_NEOFORGE_ARGFILE_HASHES.get(key, digest) == digest, 'Owned program argfile bytes changed')
        _NEOFORGE_ARGFILE_HASHES[key] = digest
        return True
    except (ValueError, OSError, UnicodeError, KeyError):
        return False


def associated(row, profile):
    profile = Path(profile).resolve()
    if sys.platform == 'darwin':
        arguments = mac_process_argv(row)
        if arguments is None:
            return False
        main = 'net.neoforged.devlaunch.Main'
        if main in arguments and any(a.startswith('@') for a in arguments[arguments.index(main)+1:]):
            return neoforge_argfile_profile_matches(row, arguments, profile)
        return argv_profile_matches(arguments, profile)
    if os.name != 'nt':
        try:
            args = shlex.split(row['argv'])
            if argv_profile_matches(args, profile):
                return True
        except ValueError:
            pass
    if os.name == 'nt':
        import psutil
        try:
            arguments = psutil.Process(int(row['pid'])).cmdline()
            current = process_table().get(int(row['pid']))
            if current is None or identity(current) != identity(row):
                return False
            main = 'net.neoforged.devlaunch.Main'
            if main in arguments and any(a.startswith('@') for a in arguments[arguments.index(main)+1:]):
                return neoforge_argfile_profile_matches(row, arguments, profile)
            return argv_profile_matches(arguments, profile)
        except (psutil.Error, ValueError, OSError):
            return False
    if sys.platform.startswith('linux'):
        try:
            return Path('/proc/' + str(row['pid']) + '/cwd').resolve() == profile
        except OSError:
            return False
    result = subprocess.run(['lsof', '-a', '-p', str(row['pid']), '-d', 'cwd', '-Fn'],
                            text=True, capture_output=True, timeout=3)
    return 'p' + str(row['pid']) in result.stdout.splitlines() and 'n' + str(profile) in result.stdout.splitlines()


@contextmanager
def runtime_lock():
    LOCK.parent.mkdir(parents=True, exist_ok=True)
    with LOCK.open('a+b') as lock:
        try:
            if os.name == 'nt':
                import msvcrt
                lock.seek(0)
                if not lock.read(1):
                    lock.write(b'0'); lock.flush()
                lock.seek(0); msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise ValueError('Client lane is already owned (.proof-matrix.lock)') from error
        yield


def applied_settings(text):
    events = re.findall(r'ShaderLOD explicit settings applied: pack=([^\r\n]+?), '
                        r'enabled=(true|false), preset=([A-Z_]+)', text)
    if not events:
        return None
    pack, enabled, preset = events[-1]
    return dict(pack=pack, enabled=enabled == 'true', preset=preset)


def readiness(text, pack, mode, *, apply_lod_settings=False, lod_state=None, preset=None, background_launch=False):
    loaded = re.findall(r'Using shaderpack:\s*([^\r\n]+)', text)
    if apply_lod_settings:
        applied = applied_settings(text)
        expected_pack = applied['pack'] if applied else None
        settings_match = bool(applied) and applied['enabled'] == (lod_state == 'on') and (
            preset is None or applied['preset'] == preset)
        # An On task may apply LOD Off while keeping the main mod loaded.
        side = r'(?:LOD|baseline), modPresent=true'
    else:
        expected_pack, side = pack, 'LOD, modPresent=true' if mode == 'on' else 'baseline, modPresent=false'
    fullscreen = re.findall(r'ShaderLOD inspection native fullscreen: true, monitor=(-?\d+), '
                            r'framebuffer=(\d+)x(\d+), target=(\d+)x(\d+)', text)
    native_fullscreen = bool(fullscreen) and int(fullscreen[-1][0]) != 0 and (
        int(fullscreen[-1][1]) > 0 and int(fullscreen[-1][2]) > 0 and
        fullscreen[-1][1:3] == fullscreen[-1][3:5])
    # Iris removes Minecraft formatting from its log display name. Exact
    # configured/archive/runtime identity is verified independently.
    plain=lambda value:re.sub(r'(?i)§[0-9a-fk-or]','',value)
    result = dict(shader=bool(loaded) and expected_pack is not None and plain(loaded[-1].strip()) == plain(expected_pack),
                in_world=bool(re.search(r'ShaderLOD landscape ready: side=' +
                              side, text)),
                fullscreen=native_fullscreen)
    if background_launch:
        result.pop('fullscreen')
        result['background_window'] = bool(re.search(r'ShaderLOD background window ready: framebuffer=\d+x\d+, target=\d+x\d+, visible=0, focused=0, iconified=0', text))
    if apply_lod_settings:
        result['settings_applied'] = settings_match
    return result


def supervise(session):
    marker = session / 'owner.json'
    state = json.loads(marker.read_text())
    p = state['plan']
    try:
        with runtime_lock():
            supervise_locked(session, state)
    except Exception as error:
        state.update(status='startup-failed', error=str(error))
        write_json(marker, state)


def supervise_locked(session, state):
    """Keep the runtime lock even if ownership/readiness discovery temporarily fails."""
    marker, p = session / 'owner.json', state['plan']
    absolute_stop = stop_deadline(p.get('stop_at_utc'))
    capture_name = p.get('capture_when_ready')
    if capture_name is not None:
        require(isinstance(capture_name, str) and re.fullmatch(r'[a-zA-Z0-9_-]{1,64}', capture_name),
                'capture_when_ready requires a safe 1..64 character capture name')
        require(p['mode'] == 'off' and not p.get('apply_lod_settings'),
                'capture_when_ready supports original-Off without settings Apply only')
        require((Path(p['profile']).parent / 'control.json').is_file(),
                'capture_when_ready requires the existing prepared control.json')
    require(not clients(process_table()), 'Another Minecraft client is running; close it first')
    from run_storage import admit
    admit(Path(p['profile']), 0)
    if p['already_built']:
        validate_built()
    sources_before = source_fingerprint()
    prepare(p, session / 'backups')
    state.update(status='starting', supervisor=identity(process_table()[os.getpid()]))
    write_json(marker, state)
    with (session / 'console.log').open('w') as console:
        launcher = subprocess.Popen(p['command'], cwd=ROOT, stdout=console, stderr=subprocess.STDOUT,
                                    start_new_session=os.name != 'nt')
        deadline = time.monotonic() + p['ready_timeout']
        # Everything after Popen stays inside the ownership loop on discovery errors.
        while True:
            try:
                table = process_table()
                row = table.get(launcher.pid)
                if row and 'launcher' not in state:
                    state['launcher'] = identity(row)
                    state['launcher_session'] = os.getsid(launcher.pid) if os.name != 'nt' else None
                live = clients(table)
                proven = {str(pid): identity(row) for pid, row in live.items() if associated(row, p['profile'])}
                # Keep historical ownership for stop/audit even after the game exits.
                state.setdefault('clients', {}).update(proven)
                state['live_client_pids'] = sorted(live)
                if state['status'] == 'starting':
                    text = (session / 'console.log').read_text(errors='replace')
                    state['readiness'] = readiness(text, p['pack_name'], p['mode'],
                        apply_lod_settings=p.get('apply_lod_settings', False),
                        lod_state=p.get('lod_state'), preset=p.get('preset'), background_launch=p.get('background_launch', False))
                    if p.get('windowed'):
                        state['readiness'].pop('fullscreen', None)
                    if p.get('benchmark_control_probe'):
                        state['readiness']['in_world'] = 'ShaderLOD benchmark control ready: shaders=true, fallback=false' in text
                    if p.get('benchmark_tuner_probe'):
                        state['readiness']['in_world'] = 'ShaderLOD benchmark probe ready: shaders=true, fallback=false' in text
                    if p.get('apply_lod_settings'):
                        state['applied_settings'] = applied_settings(text)
                    if FATAL.search(text):
                        state.update(status='startup-failed', error='Build/shader startup error; see console.log')
                    elif all(state['readiness'].values()) and proven:
                        state.update(status='ready', ready_elapsed_seconds=round(p['ready_timeout'] -
                                     (deadline - time.monotonic()), 2))
                        ready_capture(session, state)
                        if source_fingerprint() == sources_before:
                            built = build_fingerprint()
                            if all(built.get(name) == digest for name, digest in sources_before.items()):
                                write_json(LOGS / 'built.json', built)
                        else:
                            state['build_receipt'] = 'not recorded: source changed during launch'
                    elif time.monotonic() >= deadline:
                        state.update(status='startup-timeout', error='No verified fullscreen shader/world within deadline')
                if launcher.poll() is not None and not live:
                    state.update(status='exited' if state['status'] == 'ready' else 'startup-failed',
                                 exit_code=launcher.returncode)
                    write_json(marker, state)
                    return
                write_json(marker, state)
            except Exception as error:
                state.update(status='startup-failed', error='Supervisor discovery error: ' + str(error))
                # Never release ownership or signal a game because a probe/log write failed.
                try:
                    write_json(marker, state)
                except OSError:
                    pass
                print(state['error'], file=sys.stderr, flush=True)
            # A failed discovery must still reach the one-shot deadline decision.
            # stop() independently revalidates retained identities before any signal.
            deadline_stop(session, state, absolute_stop)
            interval = .5 if state['status'] == 'starting' else 2
            if absolute_stop is not None and 'deadline_stop_requested_utc' not in state:
                interval = min(interval, max(.05, absolute_stop - time.monotonic()))
            time.sleep(interval)


def stop(session):
    marker = session / 'owner.json'
    state = json.loads(marker.read_text())
    saved = state.get('clients', {})
    require(saved, 'No client ownership recorded; close the game window or inspect ' + str(marker))
    # Validate ALL recorded processes before any signal, and never signal a daemon/launcher/group.
    selected = []
    for pid, proof in saved.items():
        row = process_table().get(int(pid))
        if row is None:
            continue
        require(identity(row) == proof and int(pid) in clients({int(pid): row})
                and associated(row, state['plan']['profile']), 'Client identity/profile changed; refused stop')
        selected.append((pid, proof))
    for pid, proof in selected:
        # Recheck birth/argv after the OS association probe, immediately before signaling.
        current = process_table().get(int(pid))
        require(current is not None and identity(current) == proof, 'Client identity changed before signal')
        os.kill(int(pid), signal.SIGTERM)
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        table = process_table()
        if not any(int(pid) in table and identity(table[int(pid)]) == proof for pid, proof in saved.items()):
            print('Owned Minecraft exited. Shared Gradle daemons preserved.')
            return
        time.sleep(.5)
    raise ValueError('Owned game still present after SIGTERM; no forced/group kill. Inspect ' + str(marker))


def retire_source_launcher(session, natural_exit_grace=3.0, signal_exit_grace=3.0):
    """Retire one recorded source launcher after client stop; never signal a group.

    Callers retain ownership on refusal. Historical wrappers are not changed by
    this opt-in operation, and a disappeared PID is checked with a fresh census.
    """
    state = json.loads((session / 'owner.json').read_text())
    proof, owned_session = state.get('launcher'), state.get('launcher_session')
    if os.name == 'nt':
        require(isinstance(proof, dict) and set(proof) == {'pid', 'birth', 'argv'}
                and type(proof['pid']) is int and proof['pid'] > 0
                and all(isinstance(proof[k], str) and proof[k] for k in ('birth', 'argv')),
                'Exact Windows launcher identity required')
        require(0 <= natural_exit_grace <= 10, 'Launcher retirement grace must be bounded to 0..10 seconds')
        receipt = dict(launcher=proof, observations=[], released=False, signalSent=False)
        path = session / 'source-launcher-retirement.json'
        end = time.monotonic() + natural_exit_grace
        while True:
            table = process_table(); row = table.get(proof['pid'])
            absent = row is None and not clients(table)
            receipt['observations'].append(dict(launcherPresent=row is not None,
                actualIdentity=identity(row) if row else None, remainingClientPids=sorted(clients(table))))
            receipt['released'] = absent
            receipt['outcome'] = 'exact-launcher-absent-and-no-clients' if absent else 'waiting-for-natural-exit'
            write_json(path, receipt)
            if absent:
                return receipt
            require(row is None or identity(row) == proof, 'Windows launcher identity changed; retain ownership')
            require(time.monotonic() < end, 'Windows launcher or client remains; retain ownership without group termination')
            time.sleep(.1)
    require(os.name != 'nt' and isinstance(proof, dict) and
            set(proof) == {'pid', 'birth', 'argv'} and type(proof['pid']) is int and proof['pid'] > 0 and
            all(isinstance(proof[key], str) and proof[key] for key in ('birth', 'argv')) and
            type(owned_session) is int and owned_session > 0, 'Exact POSIX launcher identity/session required')
    require(0 <= natural_exit_grace <= 10 and 0 <= signal_exit_grace <= 10,
            'Launcher retirement grace must be bounded to 0..10 seconds')
    receipt = dict(launcher=proof, ownedSession=owned_session, observations=[],
                   released=False, signalSent=False)
    path = session / 'source-launcher-retirement.json'

    def observe(stage):
        table = process_table()
        row = table.get(proof['pid'])
        actual_session = None
        session_error = None
        if row is not None:
            try:
                actual_session = os.getsid(proof['pid'])
            except OSError as error:
                session_error = ('disappeared-during-session-check' if isinstance(error, ProcessLookupError)
                                 else type(error).__name__ + ': ' + str(error))
        result = dict(stage=stage, observedUtc=datetime.now(timezone.utc).isoformat(),
                      launcherPresent=row is not None,
                      actualIdentity=identity(row) if row is not None else None,
                      identityMatches=row is not None and identity(row) == proof,
                      actualSession=actual_session, sessionMatches=actual_session == owned_session,
                      sessionError=session_error, remainingClientPids=sorted(clients(table)))
        receipt['observations'].append(result)
        write_json(path, receipt)
        return result

    def absent(row):
        return not row['launcherPresent'] and not row['remainingClientPids']

    def stable(row):
        return row['identityMatches'] and row['sessionMatches'] and not row['remainingClientPids']

    def release():
        receipt.update(released=True, outcome='exact-launcher-absent-and-no-clients')
        write_json(path, receipt)
        return receipt

    def refuse(reason):
        receipt.update(outcome='refused-retain-ownership', refusal=reason)
        write_json(path, receipt)
        raise ValueError(reason + '; retain ownership; inspect ' + str(path))

    end = time.monotonic() + natural_exit_grace
    while True:
        row = observe('natural-exit-grace')
        if absent(row):
            return release()
        if time.monotonic() >= end:
            break
        time.sleep(.1)
    # Separate fresh censuses reject PID reuse, session drift and new clients.
    for stage in ('signal-verification-1', 'signal-verification-2'):
        row = observe(stage)
        if absent(row):
            return release()
        if not stable(row):
            refuse('Launcher retirement predicates failed: ' + stage)
    try:
        os.kill(proof['pid'], signal.SIGTERM)
        receipt['signalSent'] = True
    except ProcessLookupError:
        receipt['signalRace'] = 'disappeared-before-signal'
    write_json(path, receipt)
    end = time.monotonic() + signal_exit_grace
    while True:
        row = observe('after-signal')
        if absent(row):
            return release()
        if time.monotonic() >= end:
            refuse('Launcher still present or client remains after bounded retirement')
        time.sleep(.1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('on', 'off', 'prepare', 'stop', '_supervise'))
    parser.add_argument('--mode', choices=('on', 'off'), default='on', help='mode for prepare')
    parser.add_argument('--profile', type=Path)
    parser.add_argument('--world', default=WORLD, help='existing saved world; custom names require neutral control probe')
    parser.add_argument('--pack', help='installed ZIP name or ZIP path; current profile selection by default')
    parser.add_argument('--pack-options', type=Path, help='quality overrides; defaults to adjacent ZIP.txt')
    parser.add_argument('--preset', type=str.upper, choices=('PERFORMANCE', 'BALANCED', 'QUALITY', 'AGGRESSIVE'),
                        help='LOD preset; AGGRESSIVE is an experimental visual-quality screen')
    parser.add_argument('--apply-lod-settings', action='store_true',
                        help='On task only: apply explicit settings through the same backend used by the UI')
    parser.add_argument('--lod-state', choices=('on', 'off'),
                        help='requested backend LOD state; requires --apply-lod-settings and explicit source --pack')
    parser.add_argument('--dh', action='store_true', help='profile must already contain pinned DH JAR')
    parser.add_argument('--performance-stack', default='minimal', choices=('minimal', 'optimized', 'optimized-culling', 'optimized-moreculling'),
                        help='existing pinned performance-mod stack; profile mods remain managed separately')
    parser.add_argument('--consumer-uniforms', type=Path,
                        help='explicit untimed named-consumer request; requires --apply-lod-settings')
    parser.add_argument('--foreground', action='store_true', help='explicitly show/activate a foreground client; Mac defaults to hidden background diagnostics')
    parser.add_argument('--windowed', action='store_true', help='visible windowed inspection; full-framebuffer performance qualification remains separate')
    parser.add_argument('--already-built', action='store_true', help='require unchanged successful build receipt')
    parser.add_argument('--benchmark-control-probe', type=Path, help='neutral shared-clock benchmark caller, supports main-mod absent and present')
    parser.add_argument('--benchmark-tuner-probe', type=Path, help='one-shot normal benchmark API caller; owns a fresh demo instead of the landscape')
    parser.add_argument('--diagnostic-gpu-pass-timing', action='store_true',
                        help='opt-in existing development pass queries; excludes normal measurement API')
    parser.add_argument('--ready-timeout', type=float, default=60)
    parser.add_argument('--stop-at', help='optional absolute ISO time with timezone for an exact owned-client stop; reserve teardown margin')
    parser.add_argument('--dry-run', action='store_true', help='validate/show plan; no writes/build/client')
    parser.add_argument('--session', type=Path, help='exact session directory required for stop')
    args = parser.parse_args()
    try:
        if args.action in ('stop', '_supervise'):
            require(args.session is not None, '--session is required')
            require(not args.dry_run, 'Use on/off --dry-run for inspection; stop requires an explicit session')
            return stop(args.session.resolve()) if args.action == 'stop' else supervise(args.session.resolve())
        require(1 <= args.ready_timeout <= 300, '--ready-timeout must be 1..300 seconds')
        if args.action in ('on', 'off'):
            args.mode = args.action
        p = plan(args)
        if args.dry_run:
            print(json.dumps(p, indent=2)); return
        if args.already_built:
            validate_built()
        require(not clients(process_table()), 'Another Minecraft client is running; close it first')
        session = LOGS / (time.strftime('%Y%m%d-%H%M%S-') + args.mode + '-' + uuid.uuid4().hex[:8])
        session.mkdir(parents=True)
        marker = session / 'owner.json'
        if args.action == 'prepare':
            with runtime_lock():
                require(not clients(process_table()), 'Another Minecraft client is running')
                prepare(p, session / 'backups')
                write_json(marker, dict(status='prepared', plan=p))
            print('Prepared ' + p['mode'] + ': ' + p['pack_name'] + '\nBackup/plan: ' + str(session)); return
        write_json(marker, dict(status='pending', plan=p, clients={},
                               stop_command=[sys.executable, str(Path(__file__).resolve()), 'stop', '--session', str(session)]))
        with (session / 'supervisor.log').open('w') as log:
            subprocess.Popen([sys.executable, str(Path(__file__).resolve()), '_supervise', '--session', str(session)],
                             cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, start_new_session=os.name != 'nt')
        print('Starting ' + args.mode + ': ' + p['pack_name'] + '\nSession: ' + str(session), flush=True)
        deadline = time.monotonic() + args.ready_timeout + 5
        while time.monotonic() < deadline:
            state = json.loads(marker.read_text())
            if state['status'] == 'ready':
                if args.apply_lod_settings:
                    applied = state['applied_settings']
                    print('Applied settings: pack=' + applied['pack'] + ', enabled=' +
                          str(applied['enabled']).lower() + ', preset=' + applied['preset'])
                print('Ready: selected shader and world; requested presentation verified (' +
                      str(state['ready_elapsed_seconds']) + ' s).\nStop: python3 scripts/dev_client.py stop --session ' + str(session))
                return
            if state['status'] in ('startup-failed', 'startup-timeout', 'exited'):
                raise ValueError(state.get('error', 'Launcher exited before readiness') + '\nSession: ' + str(session) +
                                 '\nConsole: ' + str(session / 'console.log') +
                                 '\nAn owned live game is retained until you close it or explicitly stop this session.')
            time.sleep(.25)
        raise ValueError('Supervisor did not report readiness; inspect ' + str(session))
    except (ValueError, OSError, zipfile.BadZipFile) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)


if __name__ == '__main__':
    main()
