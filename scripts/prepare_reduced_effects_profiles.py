#!/usr/bin/env python3
"""Prepare pinned original/off/combined reduced-SSR profiles; no build or client."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
from contextlib import ExitStack
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import tomllib
import zipfile

from analyze_inprocess_control import ssr_cost_members, validate_ssr_cost_qualification
from prepare_isolations import archive_contents, filter_effective_options, ORIGINAL_SHA256
from run_proof_matrix import (archive_defaults, canonical_options_hash, normalize_requested_options,
                              read_option_properties, require_exclusive_runtime)
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

REDUCED_SHA256 = '636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83'
REDUCED_ARCHIVE = ROOT / 'build/Photon-SSRFarReducedTraceProbe-enabled.zip'
DEFAULT_SOURCES = {'forest': ROOT / 'run-workloads/native-terrain-b15-r2/forest/high',
                   'coastalwater': ROOT / 'run-workloads/photon-ssr-cost-controls-r1/coastalwater/high'}
ARMS = ('high', 'patched-off', 'adaptive')
SCENES = tuple(DEFAULT_SOURCES)
OVERRIDES = ('enabled', 'terrainEnabled', 'terrainChunkCap', 'warmSourceConfirmed')
POSES = {'forest': {'x': 256.5, 'y': 95.0, 'z': 768.5, 'yaw': 135.0, 'pitch': 9.0, 'time': 7000},
         'coastalwater': {'x': 500.5, 'y': 68.0, 'z': 660.5, 'yaw': 87.38, 'pitch': 3.735, 'time': 12500}}
PATCHED_FLAGS = dict.fromkeys(('SHADERLOD_CLOUDS', 'SHADERLOD_WATER', 'SHADERLOD_FOG',
                             'SHADERLOD_SSR', 'SHADERLOD_MATERIALS'), 'true') | {
    'SHADERLOD_USE_MOD_CONFIG': 'true', 'SHADERLOD_PRESERVE_LIGHTING': 'true',
    'SHADERLOD_CACHED_SKY': 'false', 'SHADERLOD_WATER_ANALYTIC': 'false', 'SHADERLOD_DEBUG': 'false',
    'SHADERLOD_NEAR': '32.0', 'SHADERLOD_FAR': '96.0', 'SHADERLOD_TRANSITION': '8.0',
    'PHOTON_SSR_FAR_REDUCED_TRACE_PROBE': 'true'}
TRANSIENT = ('saves', 'shaderpacks', 'logs', 'screenshots', 'camera-quality', 'camera-quality*',
             'benchmark*.json', 'benchmark*.json.tmp', 'workload-request*.json', 'workload-measurement*.json')


def require(condition, message):
    if not condition: raise ValueError(message)


def plan(output, scenes=SCENES, sources=None, reduced_archive=REDUCED_ARCHIVE):
    """Pure explicit paths/pins; no archive/save reads or client invocation."""
    sources = DEFAULT_SOURCES | (sources or {})
    output, reduced_archive = Path(output), Path(reduced_archive)
    require(not output.is_symlink() and not reduced_archive.is_symlink(), 'No symlink destinations/archive selection')
    output, reduced_archive = output.resolve(), reduced_archive.resolve()
    require(not output.exists(), 'Preserving existing output; use a fresh directory')
    require(scenes and len(scenes) == len(set(scenes)) and set(scenes) <= set(SCENES), 'Unknown/empty/duplicate scenes')
    operations = []
    for scene in scenes:
        source = Path(sources[scene])
        require(not source.is_symlink(), 'No symlink source profile')
        source = source.resolve()
        require(source != output and source not in output.parents and output not in source.parents
                and source.parent != output and source.parent not in output.parents,
                'Output must be separate from source collection')
        operations.append({'scene': scene, 'source_profile': str(source),
                           'target_profiles': {arm: str(output / scene / arm) for arm in ARMS}})
    require(output != reduced_archive and output not in reduced_archive.parents, 'Output may not contain source archive')
    return {'schema': 1, 'output': str(output), 'scene_operations': operations,
            'original_sha256': ORIGINAL_SHA256, 'reduced_archive': str(reduced_archive), 'reduced_sha256': REDUCED_SHA256,
            'arms': list(ARMS), 'dh_present': False, 'requested_chunks': 24,
            'validation': 'Pure plan only; runtime/source qualification and copies pending'}


def control_for_scene(control, scene):
    result = dict(control)
    removed = {name: result.pop(name) for name in OVERRIDES if name in result}
    require(all(result.get(name) == value for name, value in POSES[scene].items()), 'Source inspected pose/time changed: ' + scene)
    require(result.get('weather') == 'clear' and result.get('preset') == 'PERFORMANCE' and result.get('debug') is False,
            'Source weather/preset/debug not the inspected control')
    require(not ({'capture', 'cameraQuality', 'terrainCap'} & result.keys()), 'Unsafe capture/terrain override in control')
    return result, removed


def fresh_config(arm):
    require(arm in ARMS, 'Unknown arm')
    return ('[lod]\nenabled=' + str(arm == 'adaptive').lower() + '\npreset="PERFORMANCE"\n'
            'near=32.0\nfar=96.0\ntransition=8.0\ndebug=false\n'
            '[terrain]\nenabled=false\nvanillaChunkCap=16\nwarmSourceConfirmed=false\nwarmupSeconds=30\n'
            '[rendering]\noptimizeShadowCulling=false\nseparateShadowVisibilityLists=false\nshadowFrustumCoherence=false\n')


def native_options(profile, original, reduced):
    option_path = profile / 'shaderpacks' / (read_option_properties((profile / 'config/iris.properties').read_text())['shaderPack'] + '.txt')
    requested = read_option_properties(option_path.read_text()) if option_path.exists() else {}
    report_path = profile / 'workload-measurement.json'
    require(report_path.is_file(), 'Inspected original loaded-option evidence missing')
    report = json.loads(report_path.read_text())
    require(report.get('status') == 'complete' and report.get('actual_shader_sha256') == ORIGINAL_SHA256,
            'Original source report pin/status mismatch')
    effective = report.get('effective_shader_options')
    require(isinstance(effective, dict) and effective and report.get('effective_shader_options_sha256') == canonical_options_hash(effective),
            'Original source effective option fingerprint mismatch')
    native, evidence = filter_effective_options(effective, requested, original, reduced)
    patched = native | PATCHED_FLAGS
    with zipfile.ZipFile(Path(reduced['path'])) as archive: reduced_defaults = archive_defaults(archive)
    with zipfile.ZipFile(Path(original['path'])) as archive: original_defaults = archive_defaults(archive)
    normalize_requested_options(original_defaults, native)
    normalize_requested_options(reduced_defaults, patched)
    for name, value in original_defaults.items():
        if name in reduced_defaults and not name.startswith('SHADERLOD_'):
            require(reduced_defaults[name] == value, 'Reduced archive changed upstream default: ' + name)
    require(not any(name.startswith(('SHADERLOD_', 'PHOTON_SSR_FAR_')) for name in native), 'Original source contains experimental switches')
    return native, patched, evidence


def qualify_archives(original_path, reduced_path, runtime):
    photon = runtime['artifacts']['photon']
    original = archive_contents(original_path, ORIGINAL_SHA256, photon)
    require(file_hash(original_path, 'sha512') == photon['hashes']['sha512'], 'Original runtime-lock checksum mismatch')
    reduced = archive_contents(reduced_path, REDUCED_SHA256, photon, original['license_bytes'])
    with zipfile.ZipFile(reduced_path) as archive:
        members = {name: hashlib.sha256(archive.read(name)).hexdigest() for name in ssr_cost_members(REDUCED_SHA256)}
        probe = json.loads(archive.read('shaderlod-ssr-far-reduced-probe.json'))
    qualification = {'qualified': True, 'archive_sha256': REDUCED_SHA256, 'policy': 'reduced',
                     'member_sha256': members, 'probe_metadata': probe}
    validate_ssr_cost_qualification(qualification)
    original['path'], reduced['path'] = str(original_path), str(reduced_path)
    return original, reduced, qualification


def inspect_scene(item, reduced_path, runtime):
    scene, profile = item['scene'], Path(item['source_profile'])
    require(not any(path.is_symlink() for path in profile.rglob('*')), 'Symlinks in source tree are unsupported')
    world = profile / 'saves' / WORLD
    require((world / 'level.dat').is_file(), 'Stopped original source save missing')
    manifest_path, control_path = profile.parent / 'workload-manifest.json', profile.parent / 'control.json'
    manifest = json.loads(manifest_path.read_text())
    require(manifest.get('configuration_only') is False and manifest.get('source_world_sha256'), 'Source save provenance missing')
    control, removed = control_for_scene(json.loads(control_path.read_text()), scene)
    iris_path = profile / 'config/iris.properties'
    iris = read_option_properties(iris_path.read_text())
    selected = iris.get('shaderPack', '')
    require(selected and Path(selected).name == selected and iris.get('enableShaders') == 'true', 'Invalid source Iris pack selection')
    original_path = profile / 'shaderpacks' / selected
    original, reduced, qualification = qualify_archives(original_path, reduced_path, runtime)
    native, patched, option_evidence = native_options(profile, original, reduced)
    options_path = profile / 'options.txt'
    minecraft = {key: value for line in options_path.read_text().splitlines() if ':' in line for key, value in [line.split(':', 1)]}
    require(minecraft.get('renderDistance') == '24', 'Original source requested radius must remain24')
    jars = sorted((profile / 'mods').glob('*.jar'))
    dh = runtime['artifacts']['distanthorizons']
    if scene == 'forest':
        require(len(jars) == 1 and jars[0].name == dh['filename'] and file_hash(jars[0], 'sha512') == dh['hashes']['sha512'],
                'Forest source must contain only its pinned DH jar')
    else:
        require(not jars, 'Coastal source must contain no profile mod jars')
    files = [manifest_path, control_path, iris_path, options_path, original_path, reduced_path,
             profile / 'config/shaderlod-client.toml', profile / 'workload-measurement.json', *jars]
    for path in (profile / 'shaderpacks' / (selected + '.txt'), profile / 'config/DistantHorizons.toml', profile / 'config/sodium-options.json'):
        if path.exists(): files.append(path)
    require(all(path.is_file() for path in files), 'Source config/provenance file missing')
    tomllib.loads((profile / 'config/shaderlod-client.toml').read_text())
    return {'scene': scene, 'profile': profile, 'world': world, 'manifest': manifest, 'control': control,
            'removed_control_overrides': removed, 'original': original, 'reduced': reduced, 'qualification': qualification,
            'native_options': native, 'patched_options': patched, 'option_evidence': option_evidence,
            'source_files_sha256': {str(path): file_hash(path) for path in files},
            'provenance_files': {'control-original.json': control_path, 'workload-manifest-original.json': manifest_path,
                'shaderlod-client-original.toml': profile / 'config/shaderlod-client.toml',
                'iris-original.properties': iris_path, 'options-original.txt': options_path}}


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')


def prepare(output, scenes=SCENES, sources=None, reduced_archive=REDUCED_ARCHIVE, runtime_lock=None):
    operation = plan(output, scenes, sources, reduced_archive)
    require_exclusive_runtime()
    runtime_lock = Path(runtime_lock or ROOT / 'dependencies/runtime.lock.json')
    runtime = json.loads(runtime_lock.read_text())
    output = Path(operation['output'])
    with ExitStack() as locks:
        for item in operation['scene_operations']: locks.enter_context(unlocked_world(Path(item['source_profile']) / 'saves' / WORLD))
        selected = [inspect_scene(item, Path(operation['reduced_archive']), runtime) for item in operation['scene_operations']]
        for scene in selected: scene['world_sha256'] = world_fingerprint(scene['world'])
        output.parent.mkdir(parents=True, exist_ok=True); output.mkdir(exist_ok=False)
        state = {'schema': 1, 'status': 'preparing', 'operation': operation,
                 'created_at_utc': datetime.now(timezone.utc).isoformat(), 'runtime_lock_sha256': file_hash(runtime_lock),
                 'validation': 'Preparation only; compilation/rendering/performance/quality pending', 'scenes': {}}
        state_path = output / 'reduced-effects-preparation.json'; write_json(state_path, state)
        try:
            for scene in selected:
                base = output / scene['scene']; base.mkdir()
                provenance = base / 'source-provenance'; provenance.mkdir()
                for name, path in scene['provenance_files'].items(): shutil.copy2(path, provenance / name)
                copies, arms = {}, {}
                for arm in ARMS:
                    profile = base / arm
                    storage_copytree(scene['profile'], profile, ignore=shutil.ignore_patterns(*TRANSIENT))
                    world = profile / 'saves' / WORLD; world.parent.mkdir()
                    storage_copytree(scene['world'], world, ignore=shutil.ignore_patterns('session.lock'))
                    copies[arm] = world_fingerprint(world)
                    require(copies[arm] == scene['world_sha256'], 'Copied canonical save differs: ' + arm)
                    dh_jar = profile / 'mods' / runtime['artifacts']['distanthorizons']['filename']
                    removed_dh = dh_jar.exists()
                    if removed_dh:
                        require(scene['scene'] == 'forest', 'Unexpected fresh coastal DH jar')
                        require(file_hash(dh_jar, 'sha512') == runtime['artifacts']['distanthorizons']['hashes']['sha512'], 'Fresh copied DH jar differs from pin')
                        dh_jar.unlink()  # Only this fresh copied, already-qualified DH jar.
                    require(not any((profile / 'mods').glob('*.jar')), 'Fresh output has unmanaged profile mods')
                    archive = scene['original'] if arm == 'high' else scene['reduced']
                    archive_path = Path(archive['path']); (profile / 'shaderpacks').mkdir()
                    shutil.copy2(archive_path, profile / 'shaderpacks' / archive_path.name)
                    options = scene['native_options'] if arm == 'high' else scene['patched_options']
                    (profile / 'shaderpacks' / (archive_path.name + '.txt')).write_text(''.join(f'{name}={value}\n' for name, value in sorted(options.items())))
                    iris = read_option_properties((profile / 'config/iris.properties').read_text())
                    iris['shaderPack'] = archive_path.name
                    (profile / 'config/iris.properties').write_text(''.join(f'{name}={value}\n' for name, value in sorted(iris.items())))
                    (profile / 'config/shaderlod-client.toml').write_text(fresh_config(arm))
                    require(file_hash(profile / 'shaderpacks' / archive_path.name) == archive['sha256'], 'Copied archive changed')
                    arms[arm] = {'archive': archive_path.name, 'sha256': archive['sha256'], 'requested_options': options,
                                 'global_enabled': arm == 'adaptive', 'main_mod_present': arm != 'high',
                                 'dh_present': False, 'removed_fresh_copied_dh_jar': removed_dh,
                                 'config_sha256': file_hash(profile / 'config/shaderlod-client.toml')}
                require(world_fingerprint(scene['world']) == scene['world_sha256'], 'Source world changed during copying')
                require({path: file_hash(Path(path)) for path in scene['source_files_sha256']} == scene['source_files_sha256'], 'Source files changed during preparation')
                write_json(base / 'control.json', scene['control'])
                manifest = {'schema': 1, 'scene': scene['scene'], 'created_at_utc': state['created_at_utc'],
                    'configuration_only': False, 'preparation_status': 'complete', 'proof_profiles': list(ARMS),
                    'source_world': str(scene['world']), 'source_world_sha256': scene['world_sha256'], 'copied_world_sha256': copies,
                    'source_provenance': scene['manifest'], 'source_files_sha256': scene['source_files_sha256'],
                    'source_option_evidence': scene['option_evidence'], 'control': scene['control'],
                    'removed_control_overrides': scene['removed_control_overrides'], 'experiment_family': 'reduced-ssr-all-five-effects',
                    'experiment_arms': arms, 'quality_options': {arm: arms[arm]['requested_options'] for arm in ARMS},
                    'original_sha256': ORIGINAL_SHA256, 'adaptive_pack_sha256': REDUCED_SHA256,
                    'reduced_source_qualification': scene['qualification'], 'requested_chunks': 24,
                    'dh_installed': False, 'geometry_enabled': False, 'shader_only': True,
                    'validation': 'Pinned archives/options/config, canonical equal copies and physical DH removal only; real no-DH/native rendering and repeated performance/quality pending'}
                write_json(base / 'workload-manifest.json', manifest)
                state['scenes'][scene['scene']] = {'source_world_sha256': scene['world_sha256'], 'copied_world_sha256': copies}
                write_json(state_path, state)
            state.update(status='complete', completed_at_utc=datetime.now(timezone.utc).isoformat()); write_json(state_path, state)
        except BaseException as error:
            state.update(status='failed', error=str(error)); write_json(state_path, state); raise
    return state


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--scene', choices=('both', *SCENES), default='both')
    parser.add_argument('--forest-source', type=Path, default=DEFAULT_SOURCES['forest'])
    parser.add_argument('--coastal-source', type=Path, default=DEFAULT_SOURCES['coastalwater'])
    parser.add_argument('--reduced-archive', type=Path, default=REDUCED_ARCHIVE, help='Alternate explicit location; same exact hash required')
    parser.add_argument('--dry-run', action='store_true', help='Pure path/pin plan; no archive/save reads or copies')
    args = parser.parse_args()
    scenes = SCENES if args.scene == 'both' else (args.scene,)
    sources = {'forest': args.forest_source, 'coastalwater': args.coastal_source}
    try:
        result = plan(args.output, scenes, sources, args.reduced_archive) if args.dry_run else prepare(args.output, scenes, sources, args.reduced_archive)
        print(json.dumps(result, indent=2))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, 'Cannot prepare reduced combined profiles: ' + str(error) + '\n')


if __name__ == '__main__': main()
