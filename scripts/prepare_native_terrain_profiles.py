#!/usr/bin/env python3
"""Prepare four frozen Photon/native terrain controls; never build or launch clients."""
from __future__ import annotations

import argparse
from contextlib import ExitStack, closing
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile
import tomllib
import zipfile

from prepare_isolations import archive_contents, ORIGINAL_SHA256, PINNED
from run_proof_matrix import (archive_defaults, canonical_options_hash, read_option_properties,
                              normalize_requested_options, require_exclusive_runtime)
from setup_comparison import (ROOT, WORLD, file_hash, set_toml_values,
                              unlocked_world, world_fingerprint)

SOURCE_ARMS = ('high', 'patched-off', 'adaptive')
ARMS = SOURCE_ARMS + ('geometry',)
B15_SHA256 = PINNED['lighting-tier-no-atmosphere'][1]
SOURCE_PINS = {'high': ORIGINAL_SHA256, 'patched-off': B15_SHA256, 'adaptive': B15_SHA256}
EFFECT_OPTIONS = ('SHADERLOD_CLOUDS', 'SHADERLOD_WATER', 'SHADERLOD_FOG', 'SHADERLOD_SSR', 'SHADERLOD_MATERIALS')
CONTROL_OVERRIDES = ('enabled', 'terrainEnabled', 'terrainChunkCap', 'warmSourceConfirmed')
RENDERING_FIELDS = ('optimizeShadowCulling', 'separateShadowVisibilityLists', 'shadowFrustumCoherence')
TRANSIENT = ('session.lock', 'logs', 'screenshots', 'workload-request.json', 'workload-measurement*.json',
             'benchmark*.json', 'benchmark*.json.tmp', 'camera-quality*.json')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def plan(source_root, output, acknowledge_warmed_scene=False):
    """Pure paths/conditions, with collision checks but no archive/world reads."""
    source_root, output = Path(source_root).resolve(), Path(output).resolve()
    require(source_root != output and source_root not in output.parents and output not in source_root.parents,
            'Output must be separate from source root')
    require(not output.exists(), 'Preserving existing output; choose a fresh directory')
    require(acknowledge_warmed_scene is True,
            'Explicit --acknowledge-warmed-scene required for this fixed-scene diagnostic')
    return {'schema': 1, 'source_root': str(source_root), 'output': str(output),
            'arms': list(ARMS), 'cases': ['forest-' + arm for arm in ARMS],
            'profile_sources': {arm: 'forest/' + ('adaptive' if arm == 'geometry' else arm) for arm in ARMS},
            'canonical_world_source': str(source_root / 'forest/high/saves' / WORLD),
            'archive_pins': SOURCE_PINS | {'geometry': B15_SHA256},
            'requested_chunks': 24, 'geometry_effective_chunks': 16, 'dh_chunks': 128,
            'policy_warmup_seconds': 30, 'manual_warmed_scene_acknowledgement': True,
            'validation': 'Planning only; no source qualification, world copies, rendering or performance proof'}


def sanitized_control(control):
    result = dict(control)
    for name in CONTROL_OVERRIDES:
        result.pop(name, None)
    require(result.get('debug') is False, 'Source debug must be disabled')
    require(result.get('preset') == 'PERFORMANCE', 'Preserve audited PERFORMANCE preset')
    expected = {'x': 256.5, 'y': 95.0, 'z': 768.5, 'yaw': 135.0, 'pitch': 9.0, 'time': 7000, 'weather': 'clear'}
    require(all(result.get(name) == value for name, value in expected.items()), 'Source is not the audited fixed native forest camera/weather')
    return result


def source_config(text, arm):
    config = tomllib.loads(text)
    require(config.get('lod', {}).get('enabled') is (arm == 'adaptive'), 'Wrong source global enable: ' + arm)
    lod = config['lod']
    expected = {'preset': 'PERFORMANCE', 'near': 32.0, 'far': 96.0, 'transition': 8.0, 'debug': False}
    require(all(lod.get(name) == value for name, value in expected.items()), 'Unmatched source LOD config: ' + arm)
    terrain = config.get('terrain', {})
    require(terrain.get('enabled') is False and terrain.get('warmSourceConfirmed') is False
            and terrain.get('vanillaChunkCap') == 16 and terrain.get('warmupSeconds') == 30,
            'Source terrain policy is not the disabled cap16 control: ' + arm)
    require(all(config.get('rendering', {}).get(name, False) is False for name in RENDERING_FIELDS),
            'Source CPU experiments must be disabled')
    return config


def patched_options(options):
    expected = dict.fromkeys(EFFECT_OPTIONS, 'true') | {
        'SHADERLOD_PRESERVE_LIGHTING': 'true', 'SHADERLOD_USE_MOD_CONFIG': 'true',
        'SHADERLOD_CACHED_SKY': 'false', 'SHADERLOD_WATER_ANALYTIC': 'false', 'SHADERLOD_DEBUG': 'false'}
    require(all(options.get(name) == value for name, value in expected.items()), 'Source B15 is not the protected all-five-active control')


def output_config(text, arm):
    """Preserve unmodified TOML values/comments; add explicit renderer defaults."""
    config = tomllib.loads(text)
    updates = {('lod', 'enabled'): arm != 'high' and arm != 'patched-off',
               ('terrain', 'enabled'): arm == 'geometry', ('terrain', 'warmSourceConfirmed'): arm == 'geometry',
               ('terrain', 'vanillaChunkCap'): 16, ('terrain', 'warmupSeconds'): 30}
    text = set_toml_values(text, updates)
    rendering = config.get('rendering', {})
    present = {('rendering', name): False for name in RENDERING_FIELDS if name in rendering}
    if present:
        text = set_toml_values(text, present)
    missing = [name for name in RENDERING_FIELDS if name not in rendering]
    if missing:
        additions = ''.join(name + ' = false\n' for name in missing)
        header = re.search(r'^[ \t]*\[rendering\][ \t]*(?:#.*)?$', text, re.MULTILINE)
        if header:
            position = text.find('\n', header.end())
            if position == -1:
                text += '\n' + additions
            else:
                text = text[:position + 1] + additions + text[position + 1:]
        else:
            text = text.rstrip() + '\n\n[rendering]\n' + additions
    result = tomllib.loads(text)
    expected = {('lod', 'enabled'): arm in ('adaptive', 'geometry'),
                ('terrain', 'enabled'): arm == 'geometry', ('terrain', 'warmSourceConfirmed'): arm == 'geometry'}
    require(all(result[section][name] is value for (section, name), value in expected.items()), 'Output config failed to apply exact arm')
    require(all(result['rendering'][name] is False for name in RENDERING_FIELDS), 'Output renderer defaults not disabled')
    return text


def inspect_source(source_root, runtime_lock):
    """Archive/config qualification only; caller holds every source save lock."""
    base = Path(source_root) / 'forest'
    manifest = json.loads((base / 'workload-manifest.json').read_text())
    require(manifest.get('configuration_only') is False and manifest.get('source_world_sha256'), 'Source lacks copied-world provenance')
    require(manifest.get('adaptive_pack_sha256') == B15_SHA256, 'Source manifest is not frozen B15')
    control = sanitized_control(json.loads((base / 'control.json').read_text()))
    profiles, source_files = {}, [base / 'control.json', base / 'workload-manifest.json']
    license_bytes = None
    for arm in SOURCE_ARMS:
        profile = base / arm
        require((profile / 'saves' / WORLD / 'level.dat').is_file(), 'Source save missing: ' + arm)
        iris_path = profile / 'config/iris.properties'
        iris = read_option_properties(iris_path.read_text())
        pack = iris.get('shaderPack', '')
        require(pack and Path(pack).name == pack and iris.get('enableShaders') == 'true', 'Invalid source Iris selection')
        archive_path = profile / 'shaderpacks' / pack
        archive = archive_contents(archive_path, SOURCE_PINS[arm], runtime_lock['artifacts']['photon'], license_bytes)
        if arm == 'high':
            license_bytes = archive['license_bytes']
            require(file_hash(archive_path, 'sha512') == runtime_lock['artifacts']['photon']['hashes']['sha512'], 'Original runtime-lock checksum mismatch')
        else:
            with zipfile.ZipFile(archive_path) as opened:
                entry = opened.getinfo('shaders/program/d4_deferred_shading.fsh')
                require(b'shaderlod_needs_atmosphere(' not in opened.read(entry), 'Source B15 contains atmosphere guard')
        option_path = profile / 'shaderpacks' / (pack + '.txt')
        stored = read_option_properties(option_path.read_text()) if option_path.exists() else {}
        with zipfile.ZipFile(archive_path) as opened:
            defaults = archive_defaults(opened)
        semantic = normalize_requested_options(defaults, stored)
        if arm != 'high':
            patched_options(semantic)
        report_path = profile / 'workload-measurement.json'
        report = json.loads(report_path.read_text())
        require(report.get('status') == 'complete' and report.get('actual_shader_sha256') == SOURCE_PINS[arm], 'Source runtime option evidence missing/unmatched: ' + arm)
        effective = report.get('effective_shader_options')
        require(isinstance(effective, dict) and effective and report.get('effective_shader_options_sha256') == canonical_options_hash(effective), 'Source effective map fingerprint mismatch')
        for name, value in stored.items():
            if name == 'INFO' and name not in effective and value == defaults.get(name):
                continue
            require(effective.get(name) == value, 'Source options changed since captured evidence: ' + name)
        if arm != 'high':
            patched_options(effective)
        config_path = profile / 'config/shaderlod-client.toml'
        config_text = config_path.read_text()
        config = source_config(config_text, arm)
        options_path = profile / 'options.txt'
        # Minecraft options use colon separators, unlike Java shader properties.
        minecraft_options = {name: value for line in options_path.read_text().splitlines() if ':' in line
                             for name, value in [line.split(':', 1)]}
        require(minecraft_options.get('renderDistance') == '24', 'Source requested radius must be24')
        dh_path = profile / 'config/DistantHorizons.toml'
        dh = tomllib.loads(dh_path.read_text())
        radius = dh['client']['advanced']['graphics']['quality']['lodChunkRenderDistanceRadius']
        require(isinstance(radius, int) and not isinstance(radius, bool) and radius == 128, 'Source DH radius must be128')
        jars = sorted((profile / 'mods').glob('*.jar'))
        pin = runtime_lock['artifacts']['distanthorizons']
        require(len(jars) == 1 and jars[0].name == pin['filename'] and file_hash(jars[0], 'sha512') == pin['hashes']['sha512'], 'Source must contain only the pinned DH profile jar')
        files = [iris_path, archive_path, report_path, config_path, options_path, dh_path, *jars]
        if option_path.exists():
            files.append(option_path)
        source_files.extend(files)
        profiles[arm] = {'path': profile, 'pack': pack, 'config_text': config_text, 'config': config,
                         'stored_options': stored, 'semantic_options': semantic, 'effective_options': effective, 'dh': dh,
                         'archive_sha256': archive['sha256'], 'license_sha256': archive['license_sha256']}
    require(profiles['patched-off']['semantic_options'] == profiles['adaptive']['semantic_options'], 'B15 source option maps differ')
    require(profiles['patched-off']['effective_options'] == profiles['adaptive']['effective_options'], 'B15 source loaded maps differ')
    original_quality = {name: value for name, value in profiles['high']['effective_options'].items() if not name.startswith('SHADERLOD_')}
    for arm in ('patched-off', 'adaptive'):
        require({name: value for name, value in profiles[arm]['effective_options'].items() if not name.startswith('SHADERLOD_')} == original_quality, 'Source upstream quality differs')
    require(all(profiles[arm]['dh'] == profiles['high']['dh'] for arm in SOURCE_ARMS), 'Source DH quality/settings differ')
    a, b = profiles['patched-off']['config'], profiles['adaptive']['config']
    a, b = json.loads(json.dumps(a)), json.loads(json.dumps(b))
    a['lod'].pop('enabled'); b['lod'].pop('enabled')
    require(a == b, 'Source B15 semantic configs differ beyond global enable')
    return {'manifest': manifest, 'control': control, 'profiles': profiles,
            'source_files_sha256': {str(path): file_hash(path) for path in source_files}}


def write_json(path, contents):
    path.write_text(json.dumps(contents, indent=2, sort_keys=True) + '\n')


def cached_dh_metadata(world):
    """Read pinned metadata from a private DB+WAL snapshot, never open the source.

    SQLite mode=ro can create WAL/SHM sidecars beside a WAL-mode database. The
    caller holds the stopped source world lock; copy any existing WAL so its
    latest committed rows remain visible. SHM is recreated only in temp space.
    """
    database = world / 'data/DistantHorizons.sqlite'
    require(database.is_file(), 'Warmed source DH database missing')
    with tempfile.TemporaryDirectory(prefix='shaderlod-dh-metadata-') as directory:
        snapshot = Path(directory) / database.name
        shutil.copy2(database, snapshot)
        wal = database.with_name(database.name + '-wal')
        if wal.is_file():
            shutil.copy2(wal, snapshot.with_name(snapshot.name + '-wal'))
        with closing(sqlite3.connect(snapshot.resolve().as_uri() + '?mode=ro', uri=True)) as connection:
            rows = connection.execute('SELECT DetailLevel,count(*),min(PosX),max(PosX),min(PosZ),max(PosZ) FROM FullData GROUP BY DetailLevel').fetchall()
    require(rows, 'Warmed source DH database is empty')
    return [dict(zip(('detail', 'sections', 'min_x', 'max_x', 'min_z', 'max_z'), row)) for row in rows]


def prepare(source_root, output, acknowledge_warmed_scene=False, runtime_lock_path=None):
    operation = plan(source_root, output, acknowledge_warmed_scene)
    require_exclusive_runtime()
    source_root, output = Path(operation['source_root']), Path(operation['output'])
    runtime_lock_path = Path(runtime_lock_path or ROOT / 'dev/runtime.lock.json')
    runtime_lock = json.loads(runtime_lock_path.read_text())
    with ExitStack() as locks:
        for arm in SOURCE_ARMS:
            locks.enter_context(unlocked_world(source_root / 'forest' / arm / 'saves' / WORLD))
        source = inspect_source(source_root, runtime_lock)
        canonical_world = source_root / 'forest/high/saves' / WORLD
        fingerprint = world_fingerprint(canonical_world)
        coverage = cached_dh_metadata(canonical_world)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.mkdir(exist_ok=False)
        state = {'schema': 1, 'status': 'preparing', 'operation': operation,
                 'created_at_utc': datetime.now(timezone.utc).isoformat(),
                 'runtime_lock_sha256': file_hash(runtime_lock_path),
                 'source_files_sha256': source['source_files_sha256']}
        state_path = output / 'native-terrain-preparation.json'
        write_json(state_path, state)
        try:
            base = output / 'forest'; base.mkdir()
            copies, arms = {}, {}
            for arm in ARMS:
                target = base / arm
                # Geometry's source is the new adaptive profile, including its
                # canonical save; other source saves are deliberately excluded.
                original = base / 'adaptive' if arm == 'geometry' else source['profiles'][arm]['path']
                ignored = TRANSIENT + (() if arm == 'geometry' else ('saves',))
                shutil.copytree(original, target, ignore=shutil.ignore_patterns(*ignored))
                world = target / 'saves' / WORLD
                if arm != 'geometry':
                    world.parent.mkdir()
                    shutil.copytree(canonical_world, world, ignore=shutil.ignore_patterns('session.lock'))
                copies[arm] = world_fingerprint(world)
                require(copies[arm] == fingerprint, 'New copied world differs: ' + arm)
                source_arm = 'adaptive' if arm == 'geometry' else arm
                entry = source['profiles'][source_arm]
                config = output_config(entry['config_text'], arm)
                (target / 'config/shaderlod-client.toml').write_text(config)
                require(file_hash(target / 'shaderpacks' / entry['pack']) == entry['archive_sha256'], 'Copied shader archive differs')
                arms[arm] = {'source_profile': str(original), 'source_config_sha256': file_hash(original / 'config/shaderlod-client.toml'),
                             'requested_options': entry['stored_options'], 'full_effective_source_options': entry['effective_options'],
                             'shader_sha256': entry['archive_sha256'], 'global_enabled': arm in ('adaptive', 'geometry'),
                             'terrain_enabled': arm == 'geometry', 'manual_warmed_acknowledgement': arm == 'geometry',
                             'requested_chunks': 24, 'expected_effective_chunks': 16 if arm == 'geometry' else 24,
                             'config_sha256': file_hash(target / 'config/shaderlod-client.toml')}
            require(world_fingerprint(canonical_world) == fingerprint, 'Canonical source world changed during preparation')
            require({path: file_hash(Path(path)) for path in source['source_files_sha256']} == source['source_files_sha256'], 'Source config/archive/report changed during preparation')
            write_json(base / 'control.json', source['control'])
            manifest = source['manifest'] | {'created_at_utc': state['created_at_utc'], 'configuration_only': False,
                'preparation_status': 'complete', 'source_world': str(canonical_world), 'source_world_sha256': fingerprint,
                'copied_world_sha256': copies, 'source_dh_coverage': coverage, 'source_provenance': source['manifest'],
                'control': source['control'], 'proof_profiles': list(ARMS), 'geometry_enabled': True, 'shader_only': False,
                'adaptive_pack_sha256': B15_SHA256, 'effect_candidate_sha256': B15_SHA256,
                'effect_flags': {arm: dict.fromkeys(EFFECT_OPTIONS, 'true') for arm in ('patched-off', 'adaptive', 'geometry')},
                'experiment_arms': arms, 'quality_options': {arm: arms[arm]['requested_options'] for arm in ARMS},
                'native_terrain_operation': operation,
                'validation': 'Exact source/archive/options/config pins and equal fresh world copies; manually acknowledged fixed scene. Actual Iris/DH rendering, coverage, native dimensions and timings pending.'}
            write_json(base / 'workload-manifest.json', manifest)
            state.update(status='complete', copied_world_sha256=copies, source_world_sha256=fingerprint,
                         completed_at_utc=datetime.now(timezone.utc).isoformat())
            write_json(state_path, state)
        except BaseException as error:
            state.update(status='failed', error=str(error))
            write_json(state_path, state)
            raise
    return operation


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, default=ROOT / 'run-workloads/native-effect-isolation')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--acknowledge-warmed-scene', action='store_true', help='Explicit manual fixed-scene diagnostic acknowledgement; not automatic coverage')
    parser.add_argument('--dry-run', action='store_true', help='Pure path/arm plan only; no archive/world qualification or copies')
    args = parser.parse_args()
    try:
        result = plan(args.source_root, args.output, args.acknowledge_warmed_scene) if args.dry_run else prepare(args.source_root, args.output, args.acknowledge_warmed_scene)
        print(json.dumps(result, indent=2))
    except (OSError, ValueError, KeyError, sqlite3.Error, zipfile.BadZipFile) as error:
        parser.exit(1, 'Cannot prepare native terrain controls: ' + str(error) + '\n')


if __name__ == '__main__':
    main()
