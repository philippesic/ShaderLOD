#!/usr/bin/env python3
"""Run balanced, repeated configurations serially; retain every result and failure."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import math
import os
from pathlib import Path
import re
import signal
import subprocess
import time
import tomllib
import uuid
import zipfile

from summarize_workload import summarize

ROOT = Path(__file__).resolve().parents[1]
CASES = {
    'miniature-high': ('coastalwater/high', 'runClientLodOff'),
    'miniature-lower': ('coastalwater/lower', 'runClientLodOff'),
    'bsl-high': ('coastalwater/high', 'runClientLodOff'),
    'bsl-parallax-off': ('coastalwater/parallax-off', 'runClientLodOff'),
    'miniature-bridge-absent': ('coastalwater/bridge-absent', 'runClientLodOff'),
    'miniature-bridge-disabled': ('coastalwater/bridge-disabled', 'runClientLodOn'),
    'miniature-bridge-enabled': ('coastalwater/bridge-enabled', 'runClientLodOn'),
    'miniature-bridge-bands': ('coastalwater/bridge-bands', 'runClientLodOn'),
    'miniature-refinement-off': ('coastalwater/refinement-off', 'runClientLodOn'),
    'miniature-refinement-full': ('coastalwater/refinement-full', 'runClientLodOn'),
    'miniature-refinement-lod': ('coastalwater/refinement-lod', 'runClientLodOn'),
    'makeup-high': ('coastalwater/high', 'runClientLodOff'),
    'makeup-off': ('coastalwater/adapter-off', 'runClientLodOff'),
    'makeup-on': ('coastalwater/adapter-on', 'runClientLodOff'),
    'makeup-lower': ('coastalwater/lower', 'runClientLodOff'),
    'makeup-duplicate-implicit': ('coastalwater/duplicate-implicit', 'runClientLodOff'),
    'makeup-duplicate-explicit': ('coastalwater/duplicate-explicit', 'runClientLodOff'),
    'makeup-classifier-full': ('coastalwater/classifier-full', 'runClientLodOff'),
    'makeup-bridge-absent': ('coastalwater/bridge-absent', 'runClientLodOff'),
    'makeup-bridge-disabled': ('coastalwater/bridge-disabled', 'runClientLodOn'),
    'makeup-bridge-enabled': ('coastalwater/bridge-enabled', 'runClientLodOn'),
    'makeup-bridge-bands': ('coastalwater/bridge-bands', 'runClientLodOn'),
    'forest-cloudguard-off': ('forest/cloudguard-off', 'runClientLodOff'),
    'forest-cloudguard-on': ('forest/cloudguard-on', 'runClientLodOff'),
    'forest-secondary-base': ('forest/secondary-base', 'runClientLodOn'),
    'forest-secondary-off': ('forest/secondary-off', 'runClientLodOn'),
    'forest-secondary-on': ('forest/secondary-on', 'runClientLodOn'),
    'forest-literal-base': ('forest/literal-base', 'runClientLodOn'),
    'forest-literal-off': ('forest/literal-off', 'runClientLodOn'),
    'forest-literal-on': ('forest/literal-on', 'runClientLodOn'),
    'cloud-high': ('cloudstorm/high', 'runClientLodOff'),
    'cloud-lower': ('cloudstorm/lower', 'runClientLodOff'),
    'cloud-adaptive': ('cloudstorm/adaptive', 'runClientLodOn'),
    'cloud-patched-off': ('cloudstorm/patched-off', 'runClientLodOn'),
    'cloud-combined': ('cloudstorm/combined', 'runClientLodOn'),
    'forest-high': ('forest/high', 'runClientLodOff'),
    'forest-adaptive': ('forest/adaptive', 'runClientLodOn'),
    'forest-patched-off': ('forest/patched-off', 'runClientLodOn'),
    'forest-final': ('forest/final-adaptive', 'runClientLodOn'),
    'forest-geometry': ('forest/geometry', 'runClientLodOn'),
    'forest-raster75': ('forest/raster75', 'runClientLodOff'),
    'forest-dh-low': ('forest/dh-low', 'runClientLodOff'),
    'water-high': ('coastalwater/high', 'runClientLodOff'),
    'water-ssr-skip': ('coastalwater/ssr-skip', 'runClientLodOn'),
    'water-ssr-full': ('coastalwater/ssr-full', 'runClientLodOn'),
    'water-ssr-reduced': ('coastalwater/ssr-reduced', 'runClientLodOn'),
    'water-adaptive': ('coastalwater/adaptive', 'runClientLodOn'),
    'water-patched-off': ('coastalwater/patched-off', 'runClientLodOn'),
    'water-culling': ('coastalwater/culling', 'runClientLodOn'),
}
for _prefix, _scene in (('forest', 'forest'), ('cloud', 'cloudstorm'), ('water', 'coastalwater')):
    for _mode in ('alloff', 'cloud-only', 'water-only', 'ssr-only', 'fog-only', 'materials-only'):
        CASES[_prefix + '-' + _mode] = (_scene + '/' + _mode, 'runClientLodOn')
CLOUD_CASE_OPTIONS = {}
for _prefix, _scene in (('cloud', 'cloudstorm'), ('water-cloud', 'coastalwater')):
    for _name, _mode, _overhead in (('current', '0', 'false'), ('independent', '1', 'true'),
                                     ('smooth', '2', 'true'), ('independent-unprotected', '1', 'false')):
        _case = _prefix + '-' + _name
        CASES[_case] = (_scene + '/cloud-' + _name, 'runClientLodOn')
        CLOUD_CASE_OPTIONS[_case] = {'SHADERLOD_CLOUDS': 'true', 'SHADERLOD_WATER': 'false',
                                    'SHADERLOD_FOG': 'false', 'SHADERLOD_SSR': 'false', 'SHADERLOD_MATERIALS': 'false',
                                    'SHADERLOD_CLOUD_MODE': _mode, 'SHADERLOD_CLOUD_OVERHEAD': _overhead,
                                    'SHADERLOD_PRESERVE_LIGHTING': 'true', 'SHADERLOD_CACHED_SKY': 'false',
                                    'SHADERLOD_WATER_ANALYTIC': 'false'}
FATAL_LOG = re.compile(r'(fallback=true|Failed to create shader rendering pipeline|Shader compilation failed|Shader compilation error|Cannot save workload measurement|Landscape control rejected|Exception in thread|BUILD FAILED)', re.I)


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''): digest.update(chunk)
    return digest.hexdigest()


def development_fingerprints():
    """Freeze compiled local inputs separately from loaded external jar hashes."""
    result = {}
    for name in ('build/classes/java/devClient', 'build/resources/devClient',
                 'build/classes/java/main', 'build/resources/main'):
        directory = ROOT / name
        if directory.is_dir():
            files = {path.relative_to(directory).as_posix(): sha256(path)
                     for path in sorted(directory.rglob('*')) if path.is_file()}
            result[name] = {'files': files, 'sha256': canonical_options_hash(files)}
    if 'build/classes/java/devClient' not in result:
        raise ValueError('Compiled development harness missing')
    return result


def canonical_options_hash(options):
    return hashlib.sha256(json.dumps(options, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()).hexdigest()


def read_option_properties(contents):
    result = {}
    for line in contents.splitlines():
        if '=' in line and not line.lstrip().startswith(('#', '!')):
            key, value = line.split('=', 1); result[key.strip()] = value.strip()
    return result


def declared_defaults(settings):
    """Pack-source defaults only normalize files; loaded Iris values are authoritative."""
    result = {}
    for line in settings.splitlines():
        match = re.match(r'\s*(//)?\s*#define\s+(\w+)(?:\s+([^/]*))?', line)
        if match:
            commented, name, value = match.groups()
            result[name] = (value or '').strip() or ('false' if commented else 'true')
        else:
            match = re.match(r'\s*const\s+\w+\s+(\w+)\s*=\s*([^;]+);', line)
            if match: result[match.group(1)] = match.group(2).strip()
    return result


def archive_defaults(archive):
    # Explicit layouts for pinned pack experiments. This inspects
    # declared controls; it does not rewrite or establish support for a pack.
    settings = next((name for name in ('shaders/settings.glsl', 'shaders/lib/config.glsl', 'shaders/shader.h', 'shaders/lib/settings.glsl')
                     if name in archive.namelist()), None)
    if settings is None: raise ValueError('Unsupported experiment option layout')
    defaults = declared_defaults(archive.read(settings).decode())
    outside = {}
    for name in sorted(archive.namelist()):
        if name.endswith(('.glsl', '.fsh', '.vsh', '.gsh', '.csh')):
            for key, value in declared_defaults(archive.read(name).decode()).items():
                if key not in defaults: outside.setdefault(key, set()).add(value)
    # Explicit non-settings options require a single source default; conflicting
    # per-stage macros are not safe normalization controls.
    return defaults | {key: next(iter(values)) for key, values in outside.items() if len(values) == 1}


def normalize_requested_options(defaults, requested):
    unknown = requested.keys() - defaults.keys()
    if unknown: raise ValueError('Requested undeclared shader options: ' + repr(sorted(unknown)))
    return defaults | requested


def camera_difference(actual, expected, circular=False):
    """Minecraft normalizes yaw; all camera values still require finite numbers."""
    def finite_number(value):
        if not isinstance(value, (int, float)) or isinstance(value, bool):
            raise ValueError('Camera value must be a finite number')
        try: converted = float(value)
        except (OverflowError, ValueError): raise ValueError('Camera value must be a finite number') from None
        if not math.isfinite(converted): raise ValueError('Camera value must be a finite number')
        return converted
    a, b = finite_number(actual), finite_number(expected)
    if circular: return abs(((a % 360 - b % 360 + 180) % 360) - 180)
    return abs(a - b)


def balanced_plan(cases, stacks, repeats):
    """Williams rows balance positions/carryover over a complete design cycle.

    Partial 3-5-repeat cycles are interleaved, not claimed perfectly balanced.
    A block includes every case/stack exactly once; pairing is within its stack.
    """
    if repeats < 1 or not cases or not stacks: raise ValueError('Empty/invalid matrix')
    if len(set(cases)) != len(cases) or len(set(stacks)) != len(stacks):
        raise ValueError('Duplicate condition')
    conditions = [(case, stack) for stack in stacks for case in cases]
    n = len(conditions)
    base, low, high = [0], 1, n - 1
    while len(base) < n:
        base.append(low); low += 1
        if len(base) < n: base.append(high); high -= 1
    rows = [[(value + shift) % n for value in base] for shift in range(n)]
    if n % 2 and n > 1: rows += [list(reversed(row)) for row in rows]
    return [dict(block=block + 1, position=position + 1, case=conditions[index][0], stack=conditions[index][1])
            for block in range(repeats) for position, index in enumerate(rows[block % len(rows)])]


def minecraft_processes():
    result = subprocess.run(['ps', '-axo', 'pid=,command='], capture_output=True, text=True, check=True)
    found = []
    for line in result.stdout.splitlines():
        if 'java' in line and any(marker in line for marker in ('net.neoforged.devlaunch.Main', 'net.minecraft.client.main.Main', 'cpw.mods.bootstraplauncher.BootstrapLauncher', 'forgeclientdev', 'neoforgeclient', 'clientLodOnRunProgramArgs.txt', 'clientLodOffRunProgramArgs.txt', 'clientIrisRunProgramArgs.txt', 'clientRunProgramArgs.txt')):
            found.append(int(line.strip().split(None, 1)[0]))
    return found


def require_exclusive_runtime():
    processes = minecraft_processes()
    if processes: raise ValueError('Close existing Minecraft clients before timing; active PIDs: ' + str(processes))


def profile_inputs(profile):
    iris = profile / 'config/iris.properties'
    if not iris.exists(): raise ValueError('Missing profile: ' + str(profile))
    properties = read_option_properties(iris.read_text())
    pack = properties.get('shaderPack', '')
    if not pack or Path(pack).name != pack: raise ValueError('Invalid selected pack')
    files = [iris, profile / 'shaderpacks' / pack, profile / 'shaderpacks' / (pack + '.txt'),
             profile / 'config/shaderlod-client.toml', profile / 'options.txt',
             profile.parent / 'control.json', profile.parent / 'workload-manifest.json']
    files += [p for p in (profile / 'config/DistantHorizons.toml', profile / 'config/sodium-options.json') if p.exists()]
    options_path = profile / 'shaderpacks' / (pack + '.txt')
    for path in files:
        if path == options_path and not path.exists(): continue  # Iris deletes an all-default option file.
        if not path.is_file(): raise ValueError('Missing benchmark input: ' + str(path))
    manifest = json.loads((profile.parent / 'workload-manifest.json').read_text())
    if manifest.get('configuration_only') or not manifest.get('source_world_sha256'):
        raise ValueError('Prepared profile lacks a verified source save')
    stored = read_option_properties(options_path.read_text()) if options_path.exists() else {}
    with zipfile.ZipFile(profile / 'shaderpacks' / pack) as archive:
        defaults = archive_defaults(archive)
    semantic = normalize_requested_options(defaults, stored)
    # Experimental preparation retains intended controls even when Iris removes
    # their default-valued lines/file. Check normalization against that record.
    prepared = manifest.get('experiment_arms', {}).get(profile.name, {}).get('requested_options')
    if prepared is not None and semantic != normalize_requested_options(defaults, prepared):
        raise ValueError('Stored options differ from prepared experiment controls')
    requested = prepared if prepared is not None else stored
    rendering = tomllib.loads((profile / 'config/shaderlod-client.toml').read_text()).get('rendering', {})
    shadow_culling = rendering.get('optimizeShadowCulling', False)
    if not isinstance(shadow_culling, bool): raise ValueError('Shadow culling config must be boolean')
    return {'files': {str(p.resolve()): sha256(p) if p.exists() else 'missing-default-options-file' for p in files}, 'shader_pack': pack,
            'shader_sha256': sha256(profile / 'shaderpacks' / pack),
            'shader_options_sha256': sha256(options_path) if options_path.exists() else None,
            'requested_shader_options': requested, 'shader_option_defaults': defaults,
            'semantic_shader_options_sha256': canonical_options_hash(semantic),
            'shadow_culling_configured': shadow_culling,
            'control_sha256': sha256(profile.parent / 'control.json'),
            'control': json.loads((profile.parent / 'control.json').read_text()),
            'source_world_sha256': manifest['source_world_sha256']}


def validate_report(report, request, expected_inputs, started_ns, report_path=None):
    if report.get('status') != 'complete': raise ValueError('Incomplete capture')
    if report.get('request', {}).get('run_id') != request['run_id']: raise ValueError('Stale or unrelated report UUID')
    if report_path is not None and report_path.stat().st_mtime_ns < started_ns:
        raise ValueError('Report predates launch')
    if report.get('modPresent') != (request['task'] == 'runClientLodOn'):
        raise ValueError('Unexpected ShaderLOD presence')
    if report.get('dhPresent') != request['include_dh']: raise ValueError('Unexpected DH presence')
    if report.get('actual_shader_sha256') != expected_inputs['shader_sha256']: raise ValueError('Selected shader archive changed')
    effective = report.get('effective_shader_options')
    if not isinstance(effective, dict) or not effective: raise ValueError('Loaded Iris effective options missing')
    if report.get('effective_shader_options_sha256') != canonical_options_hash(effective): raise ValueError('Effective shader options fingerprint mismatch')
    for name, value in expected_inputs['requested_shader_options'].items():
        if name == 'INFO' and name not in effective and value == expected_inputs['shader_option_defaults'].get(name):
            continue  # Upstream profile information selector; no performance behavior asserted.
        if name not in effective: raise ValueError('Requested option is not an active Iris option: ' + name)
        if effective[name] != value: raise ValueError(f'Loaded shader option differs: {name}={effective[name]}, requested {value}')
    if report.get('control_sha256') != expected_inputs['control_sha256']: raise ValueError('Camera/control changed')
    if report.get('input_state_start') != report.get('input_state_end'): raise ValueError('Runtime inputs changed during measurement')
    state = report.get('input_state_end', {})
    if state.get('shader_pack') != expected_inputs['shader_pack']: raise ValueError('Loaded Iris pack differs from requested archive')
    for key in ('x', 'y', 'z', 'yaw', 'pitch'):
        if key in expected_inputs.get('control', {}):
            if camera_difference(state.get(key), expected_inputs['control'][key], circular=key == 'yaw') > .002:
                raise ValueError('Camera differs from prepared control: ' + key)
    enabled = state.get('lod_settings', {}).get('enabled')
    if request['case'].endswith('patched-off') and enabled is not False: raise ValueError('Patched disabled control has live LOD enabled')
    if request['case'].endswith(('adaptive', 'final', 'combined', 'geometry', 'alloff', '-only', '-culling')) or request['case'] in CLOUD_CASE_OPTIONS:
        if enabled is not True: raise ValueError('Adaptive/effect/culling control has live LOD disabled')
    if request['case'].endswith('geometry'):
        validate_geometry_report(report, request, expected_inputs)
    if request['case'] in CLOUD_CASE_OPTIONS:
        for name, value in CLOUD_CASE_OPTIONS[request['case']].items():
            if effective.get(name) != value: raise ValueError('Cloud isolation arm options do not match its label: ' + name)
    culling_case = request['case'].endswith('-culling')
    configured_culling = expected_inputs.get('shadow_culling_configured', False)
    if not isinstance(configured_culling, bool): raise ValueError('Expected shadow culling config must be boolean')
    if culling_case and (report.get('modPresent') is not True or enabled is not True or configured_culling is not True):
        raise ValueError('Culling arm requires mod present, global enabled and explicit culling config')
    expected_culling_active = culling_case or (configured_culling and report.get('modPresent') is True and enabled is True)
    if report.get('shadow_culling_active') is not expected_culling_active:
        raise ValueError('Actual shadow culling activity differs from requested arm/configuration')
    if not state.get('shaders_in_use') or state.get('fallback'): raise ValueError('Iris pipeline unavailable/fallback')
    if state.get('framebuffer_width', 0) < 1 or state.get('framebuffer_height', 0) < 1: raise ValueError('Invalid framebuffer')
    if report.get('elapsed_seconds', 0) < request['measurement_seconds'] * .95 or report.get('render_frames', 0) < 30:
        raise ValueError('Capture shorter than requested')
    if not report.get('cpu_samples'): raise ValueError('Missing CPU timing stream')
    if request['gpu_queries'] and not any(s.get('scope') == 'game-render' for s in report.get('gpu_samples', [])):
        raise ValueError('Missing GPU frame timing stream')
    if not report.get('loaded_mods'): raise ValueError('Loaded mod inventory missing')


def validate_geometry_report(report, request, expected_inputs):
    """The inspected cap16 diagnostic must actually replace the 24-chunk ring."""
    if report.get('modPresent') is not True or report.get('dhPresent') is not True or request.get('include_dh') is not True:
        raise ValueError('Geometry arm requires actual ShaderLOD and DH')
    if any(name in expected_inputs.get('control', {}) for name in ('enabled', 'terrainEnabled', 'terrainChunkCap', 'warmSourceConfirmed')):
        raise ValueError('Geometry arm control overrides per-profile config')
    for boundary in ('input_state_start', 'input_state_end'):
        state = report.get(boundary, {})
        if state.get('terrain_active') is not True or state.get('lod_settings', {}).get('enabled') is not True:
            raise ValueError('Geometry policy inactive at ' + boundary)
        for field, expected in (('requested_chunks', 24), ('effective_chunks', 16)):
            value = state.get(field)
            if not isinstance(value, int) or isinstance(value, bool) or value != expected:
                raise ValueError('Geometry arm requires requested24/effective16 at ' + boundary)
    policy = report.get('geometry_policy')
    if not isinstance(policy, dict) or policy.get('active') is not True:
        raise ValueError('Missing/inactive geometry policy evidence')
    for field, expected in (('cap', 16), ('requestedChunks', 24), ('effectiveChunks', 16), ('warmupTicks', 600)):
        value = policy.get(field)
        if not isinstance(value, int) or isinstance(value, bool) or value != expected:
            raise ValueError('Geometry policy evidence mismatch: ' + field)
    ticks = policy.get('stableTicks')
    if not isinstance(ticks, int) or isinstance(ticks, bool) or ticks < policy['warmupTicks']:
        raise ValueError('Geometry policy has not completed stable warmup')
    if policy.get('reason') != 'DH terrain cap active':
        raise ValueError('Geometry policy eligibility reason differs')
    for field in ('shadow_culling_active', 'separate_shadow_lists_active', 'shadow_frustum_coherence_active'):
        if report.get(field) is not False:
            raise ValueError('Geometry arm has missing/active CPU experiment: ' + field)
    try:
        dh = tomllib.loads(report.get('actual_dh_configuration', ''))
        radius = dh['client']['advanced']['graphics']['quality']['lodChunkRenderDistanceRadius']
    except (tomllib.TOMLDecodeError, TypeError, KeyError):
        raise ValueError('Geometry arm DH128 configuration evidence missing/invalid') from None
    if not isinstance(radius, int) or isinstance(radius, bool) or radius != 128:
        raise ValueError('Geometry arm requires unchanged DH128')


def validate_optional_stack(report, stack, lock):
    """Prove requested optimizers actually loaded from the pinned artifacts."""
    if stack not in lock['stacks']: raise ValueError('Unknown optional stack: ' + stack)
    if report.get('runtime_properties', {}).get('shaderlod.dev.performanceStack') != stack:
        raise ValueError('Runtime stack differs from requested stack')
    selected = set(lock['stacks'][stack])
    loaded = report.get('loaded_mods', {})
    artifacts = report.get('loaded_mod_artifacts', {})
    for name, artifact in lock['artifacts'].items():
        mod_id = artifact['jar_mod_id']
        if name not in selected:
            if mod_id in loaded: raise ValueError('Unexpected optional optimizer: ' + mod_id)
            continue
        if loaded.get(mod_id) != artifact['jar_version']:
            raise ValueError('Missing or mismatched loaded optimizer: ' + mod_id)
        actual = artifacts.get(mod_id, {})
        if actual.get('sha256') != artifact['hashes']['sha256']:
            raise ValueError('Loaded optimizer artifact differs from lock: ' + mod_id)


def wait_command(command, logfile, timeout):
    """Wait for full Gradle/JVM completion; terminate only our process group on timeout."""
    with logfile.open('w') as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            code = process.wait(timeout=timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            os.killpg(process.pid, signal.SIGTERM)
            try: process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL); process.wait()
            raise
    if code: raise ValueError(f'Gradle failed with exit code {code}; see {logfile}')
    errors = FATAL_LOG.findall(logfile.read_text(errors='replace'))
    if errors: raise ValueError('Runtime error markers: ' + repr(errors[:5]))


def write_json(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2) + '\n'); temporary.replace(path)


def wetness_value(value):
    parsed = float(value)
    if not math.isfinite(parsed) or not 0 <= parsed <= 1: raise argparse.ArgumentTypeError('Fixed wetness must be finite within [0,1]')
    return parsed


def validate_temporal_inputs(report, request):
    """Dynamic drift is reported, while a requested fixed control must be proved."""
    target = request.get('fixed_wetness')
    start = report.get('shader_temporal_inputs_start', {}).get('wetness', {})
    end = report.get('shader_temporal_inputs_end', {}).get('wetness', {})
    def matches(value):
        return isinstance(value, (float, int)) and not isinstance(value, bool) and math.isfinite(value) and abs(value - target) <= 1e-6
    if target is not None:
        for boundary in (start, end):
            if boundary.get('mode') != 'fixed' or not matches(boundary.get('fixedWetness')):
                raise ValueError('Fixed wetness policy missing/mismatched')
            observed = [supplier for supplier in boundary.get('suppliers', []) if supplier.get('supplierReads', 0) > 0]
            if not observed: raise ValueError('No wetness uniform supplier observations')
            for supplier in observed:
                if not matches(supplier.get('appliedWetness')):
                    raise ValueError('Fixed wetness not applied to every observed supplier')
                if supplier.get('measurementReads', 0):
                    if any(not matches(supplier.get(key)) for key in ('appliedMin', 'appliedMax')):
                        raise ValueError('Fixed wetness changed during capture')
        if end.get('measurementReads', 0) < 1: raise ValueError('Fixed wetness was not observed during capture')
    elif start.get('mode') == 'fixed' or end.get('mode') == 'fixed':
        raise ValueError('Unexpected fixed shader weather in dynamic request')
    return {'wetness_policy': 'fixed' if target is not None else 'dynamic', 'fixed_wetness': target,
            'observation_status': 'observed' if end.get('measurementReads', 0) > 0 else 'missing wetness observations',
            'start_raw_wetness': start.get('rawWetness'), 'end_raw_wetness': end.get('rawWetness'),
            'capture_raw_min': end.get('rawMin'), 'capture_raw_max': end.get('rawMax'),
            'capture_applied_min': end.get('appliedMin'), 'capture_applied_max': end.get('appliedMax'),
            'limits': 'Observed values are per-registration suppliers; program IDs are unknown. Dynamic wetness/frame phase may advance and are not strict-input failures.'}


def preserve_screenshot(profile, output, token, started_ns):
    source = profile / 'screenshots/workload-final.png'
    if not source.is_file() or source.stat().st_mtime_ns < started_ns:
        raise ValueError('Fresh workload screenshot missing')
    with source.open('rb') as stream:
        if stream.read(8) != b'\x89PNG\r\n\x1a\n': raise ValueError('Workload screenshot is not a PNG')
    destination = output / (token + '-final.png')
    destination.write_bytes(source.read_bytes())
    return {'screenshot': destination.name, 'screenshot_sha256': sha256(destination)}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('cases', nargs='+', choices=CASES)
    p.add_argument('--profiles-root', type=Path, default=ROOT / 'run-workloads')
    optional_lock = json.loads((ROOT / 'dev/performance-mods.lock.json').read_text())
    p.add_argument('--stacks', nargs='+', choices=tuple(optional_lock['stacks']), default=['minimal'])
    p.add_argument('--repeats', type=int, default=3)
    p.add_argument('--dh', choices=('present', 'absent'), default='present')
    p.add_argument('--no-gpu-queries', action='store_true')
    p.add_argument('--tag', default=None)
    p.add_argument('--sample-interval', type=int, default=1, choices=range(1,61))
    p.add_argument('--window-scale', type=int, default=1, choices=(1,2), help='Scale comparison window; actual framebuffer/window dimensions remain recorded')
    p.add_argument('--warmup', type=float, default=45)
    p.add_argument('--duration', type=float, default=30)
    p.add_argument('--fixed-wetness', type=wetness_value, help='Dev-only artificial shader weather control; real rain/smoother clocks remain unchanged')
    p.add_argument('--timeout', type=float, default=300)
    p.add_argument('--skip-build', action='store_true')
    p.add_argument('--freeze-dev-classes', action='store_true', help='Use the already compiled dev harness; requires --skip-build and retains class hash guards')
    p.add_argument('--dry-run', action='store_true', help='Print ordering/commands without files, build or client launch')
    args = p.parse_args()
    if args.freeze_dev_classes and not args.skip_build: p.error('--freeze-dev-classes requires --skip-build')
    if not 1 <= args.repeats <= 100 or args.warmup < 5 or args.duration < 5: p.error('Invalid repeats/warmup/duration')
    plan = balanced_plan(args.cases, args.stacks, args.repeats)
    profiles_root = args.profiles_root.resolve()
    for row in plan:
        location, row['task'] = CASES[row['case']]
        row['profile'] = str(profiles_root / location)
        row['command'] = ['./gradlew', row['task'], '-PrunDirectory=' + row['profile'],
                          '-PperformanceStack=' + row['stack'], '-PincludeDh=' + str(args.dh == 'present').lower(),
                          '-PmeasureWorkload=true', '-PmeasureOnly=true', '-PgpuPassTiming=false', '-PcpuLayerTiming=false',
                          '-PinitialWarmup=' + str(args.warmup), '-PmeasurementSeconds=' + str(args.duration),
                          '-PgpuSampleInterval=' + str(args.sample_interval),
                          '-PbenchmarkWindowScale=' + str(args.window_scale)]
        if args.no_gpu_queries: row['command'].append('-PgpuTiming=false')
        if args.freeze_dev_classes: row['command'].extend(['-x', 'compileDevClientJava'])
        if args.fixed_wetness is not None: row['command'].append('-PfixedWetness=' + str(args.fixed_wetness))
    if args.dry_run:
        print(json.dumps({'plan': plan, 'minimum_capture_seconds': len(plan) * (args.warmup + args.duration)}, indent=2)); return
    tag = args.tag or ('matrix-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    if Path(tag).name != tag or tag in ('.', '..'): p.error('Tag must be one directory name')
    output = ROOT / 'dev/logs' / tag
    output.parent.mkdir(parents=True, exist_ok=True)
    with (output.parent / '.proof-matrix.lock').open('a+') as lock:
        try: fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError: p.error('Another proof matrix runner is active')
        if output.exists(): p.error('Output already exists; use a new tag to preserve prior evidence')
        require_exclusive_runtime()
        inputs = {case: profile_inputs(profiles_root / CASES[case][0]) for case in args.cases}
        output.mkdir()
        manifest = {'schema': 2, 'created_at_utc': utc_now(), 'status': 'running', 'repeats': args.repeats,
                    'design': 'Williams order; partial cycles are interleaved, not perfectly position-balanced',
                    'profiles_root': str(profiles_root), 'include_dh': args.dh == 'present',
                    'gpu_queries': not args.no_gpu_queries, 'sample_interval': args.sample_interval,
                    'window_scale': args.window_scale,
                    'frozen_dev_classes': args.freeze_dev_classes,
                    'fixed_wetness': args.fixed_wetness,
                    'source_inputs': inputs, 'runs': plan, 'cases': args.cases, 'stacks': args.stacks,
                    'git_head': subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip(),
                    'runtime_lock_sha256': sha256(ROOT / 'dev/runtime.lock.json'),
                    'performance_mods_lock_sha256': sha256(ROOT / 'dev/performance-mods.lock.json'),
                    'performance_mods_lock': optional_lock}
        write_json(output / 'matrix-manifest.json', manifest)
        try:
            if not args.skip_build: subprocess.run(['./gradlew', 'build', 'compileDevClientJava'], cwd=ROOT, check=True)
            manifest['development_runtime_inputs'] = development_fingerprints()
            write_json(output / 'matrix-manifest.json', manifest)
            for index, row in enumerate(plan):
                require_exclusive_runtime()
                if development_fingerprints() != manifest['development_runtime_inputs']:
                    raise ValueError('Compiled local runtime changed since matrix start')
                profile = Path(row['profile'])
                # Minecraft rewrites options.txt on shutdown; hash stable shader/camera inputs separately.
                current = profile_inputs(profile)
                for key in ('shader_sha256', 'semantic_shader_options_sha256', 'control_sha256', 'source_world_sha256', 'shadow_culling_configured'):
                    if current[key] != inputs[row['case']][key]: raise ValueError('Profile changed since matrix start: ' + key)
                token = f"{index + 1:03d}-b{row['block']}-{row['case']}-{row['stack']}"
                request = dict(run_id=str(uuid.uuid4()), case=row['case'], stack=row['stack'], task=row['task'],
                               include_dh=args.dh == 'present', measurement_seconds=args.duration,
                               gpu_queries=not args.no_gpu_queries, block=row['block'])
                request['fixed_wetness'] = args.fixed_wetness
                write_json(profile / 'workload-request.json', request)
                report = profile / 'workload-measurement.json'
                if report.exists(): report.replace(output / (token + '-previous.json'))
                row.update(status='running', request=request, started_at_utc=utc_now(),
                           console=token + '-console.log', measurement=token + '-measurement.json', summary=token + '-summary.json')
                write_json(output / 'matrix-manifest.json', manifest)
                started_ns = time.time_ns()
                print(f"Starting {token} ({index + 1}/{len(plan)})", flush=True)
                wait_command(row['command'], output / row['console'], args.timeout)
                require_exclusive_runtime()
                if development_fingerprints() != manifest['development_runtime_inputs']:
                    raise ValueError('Compiled local runtime changed during run')
                if not report.exists(): raise ValueError('Fresh workload report missing: ' + row['case'])
                data = json.loads(report.read_text())
                # Preserve invalid fresh reports too; exclusion is explicit in the manifest.
                (output / row['measurement']).write_bytes(report.read_bytes())
                row.update(preserve_screenshot(profile, output, token, started_ns))
                validate_report(data, request, inputs[row['case']], started_ns, report)
                validate_optional_stack(data, row['stack'], optional_lock)
                row['temporal_observations'] = validate_temporal_inputs(data, request)
                reference = inputs[row['case']].get('effective_shader_options_sha256')
                if reference is not None and reference != data['effective_shader_options_sha256']: raise ValueError('Effective shader options differ between repeated launches')
                inputs[row['case']]['effective_shader_options_sha256'] = data['effective_shader_options_sha256']
                write_json(output / row['summary'], summarize(report))
                row.update(status='complete', finished_at_utc=utc_now(), render_fps=data['render_frames'] / data['elapsed_seconds'])
                write_json(output / 'matrix-manifest.json', manifest)
                print('Completed', token, row['render_fps'], 'FPS', flush=True)
            manifest['status'] = 'complete'; manifest['finished_at_utc'] = utc_now()
            write_json(output / 'matrix-manifest.json', manifest)
            subprocess.run(['python3', 'scripts/analyze_proof_matrix.py', str(output / 'matrix-manifest.json'), '--output', str(output / 'matrix-analysis.json')], cwd=ROOT, check=True)
        except BaseException as error:
            manifest.update(status='failed', error=str(error), finished_at_utc=utc_now())
            if 'row' in locals() and row.get('status') == 'running':
                row.update(status='failed', error=str(error))
                if 'report' in locals() and report.exists(): (output / row['measurement']).write_bytes(report.read_bytes())
            write_json(output / 'matrix-manifest.json', manifest)
            raise


if __name__ == '__main__': main()
