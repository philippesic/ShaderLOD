#!/usr/bin/env python3
"""Prepare original-high/original-medium/adaptive Photon controls with matched DH data."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
from contextlib import ExitStack
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import sqlite3
import zipfile

from setup_comparison import ROOT, PACK, WORLD, file_hash, unlocked_world, world_fingerprint, dh_coverage, set_toml_values

MODES = ('high', 'lower', 'adaptive')
SCENES = ('forest', 'cloudstorm', 'coastalwater')
# Keep the previously rendered Mac sampler-safe configuration. Cached sky is
# deliberately excluded: it changes global ambient/reflection quality.
MAC = dict.fromkeys(('SH_SKYLIGHT', 'SHADOW_COLOR', 'COLORED_LIGHTS',
                     'CLOUD_SHADOWS', 'ENVIRONMENT_REFLECTIONS', 'SKY_REFLECTIONS',
                     'SHADOW_VPS', 'BORDER_FOG'), 'false')


def declared_options(settings: str) -> dict:
    result = {}
    for line in settings.splitlines():
        match = re.match(r'\s*(?://)?#define\s+(\w+)(?:\s+([^/]+?))?\s*(?://.*)?$', line)
        if match:
            name, default = match.group(1), (match.group(2) or '').strip()
        else:
            match = re.match(r'\s*const\s+\w+\s+(\w+)\s*=\s*([^;]+);', line)
            if not match:
                continue
            name, default = match.groups()
        allowed = re.search(r'//\s*\[([^]]+)\]', line)
        result[name] = allowed.group(1).split() if allowed else ['true', 'false'] if not default else [default.strip()]
    return result


def profile_options(properties: str, profile: str) -> dict:
    match = re.search(r'^profile\.' + re.escape(profile) + r'\s*=([^\n]+)', properties, re.M)
    if not match:
        raise ValueError(f'Pinned Photon profile missing: {profile}')
    result = {}
    for token in match.group(1).split():
        if token.startswith('!'):
            result[token[1:]] = 'false'
        elif '=' in token:
            name, value = token.split('=', 1)
            result[name] = value
        else:
            result[token] = 'true'
    # Pinned properties contain an obsolete boolean; the live selector is an enum.
    if 'GTAO' in result:
        result['SHADER_AO'] = 'SHADER_AO_GTAO' if result.pop('GTAO') == 'true' else 'SHADER_AO_NONE'
    return result


def quality_options(properties: str, settings: str, mode: str, cloud_upscaling: int = 4) -> dict:
    options = profile_options(properties, 'medium' if mode == 'lower' else 'high')
    options.update(MAC)
    # Explicit default budgets make the workload independent of inherited profile files.
    options.update({'CLOUDS_CUMULUS_PRIMARY_STEPS_H': '40', 'CLOUDS_CUMULUS_PRIMARY_STEPS_Z': '20',
                    'CLOUDS_CUMULUS_LIGHTING_STEPS': '6', 'CLOUDS_CUMULUS_AMBIENT_STEPS': '2',
                    'GTAO_SLICES': '2', 'GTAO_HORIZON_STEPS': '3', 'SHADOW_SSRT_STEPS': '10',
                    'WATER_WAVES': 'true', 'WATER_WAVE_ITERATIONS': '3',
                    'CLOUDS_TEMPORAL_UPSCALING': str(4 if mode == 'lower' else cloud_upscaling)})
    declarations = declared_options(settings)
    for name, value in options.items():
        if name not in declarations or value not in declarations[name]:
            raise ValueError(f'Undeclared/out-of-range pinned Photon option: {name}={value}')
    return options


def prepare(args):
    source, output, pack = args.source.resolve(), args.output.resolve() / args.scene, args.pack.resolve()
    if output == source or source in output.parents or output in source.parents:
        raise ValueError('Workload output must be separate from source profile')
    lock = json.loads((ROOT / 'dependencies/runtime.lock.json').read_text())
    photon, dh = lock['artifacts']['photon'], lock['artifacts']['distanthorizons']
    original, dh_jar = ROOT / 'dependencies/upstream' / photon['filename'], ROOT / 'dependencies/runtime' / dh['filename']
    for artifact, path in ((photon, original), (dh, dh_jar)):
        if file_hash(path, 'sha512') != artifact['hashes']['sha512']:
            raise ValueError(f'Pinned artifact checksum mismatch: {path}')
    with zipfile.ZipFile(original) as archive:
        settings, properties = (archive.read(name).decode() for name in ('shaders/settings.glsl', 'shaders/shaders.properties'))
    with zipfile.ZipFile(pack) as archive:
        build = json.loads(archive.read('shaderlod-build.json'))
    if build['base_version'] != photon['version']:
        raise ValueError('Adaptive archive uses a different Photon version')
    options = {mode: quality_options(properties, settings, mode, args.cloud_upscaling) for mode in MODES}
    assert options['high'] == options['adaptive']
    options['adaptive']['SHADERLOD_CACHED_SKY'] = 'false'
    control_path = source.parent / 'control.json'
    control = json.loads(control_path.read_text()) if control_path.exists() else {
        'x': 256.5, 'y': 95.0, 'z': 768.5, 'yaw': 135.0, 'pitch': 9.0, 'time': 7000,
    }
    control.update({'time': 1800 if args.scene == 'cloudstorm' else 7000,
                    'weather': 'rain' if args.scene == 'cloudstorm' else 'clear',
                    'preset': args.preset, 'debug': False})
    control.pop('enabled', None)
    if args.camera:
        control.update(dict(zip(('x', 'y', 'z', 'yaw', 'pitch'), args.camera)))
    if args.scene == 'cloudstorm' and not args.camera:
        control['pitch'] = -15.0
    world = source / 'saves' / args.source_world
    if not args.config_only and not (world / 'level.dat').exists():
        raise ValueError(f'Source save missing: {world}')
    targets = [output / mode for mode in MODES]
    target_worlds = [target / 'saves' / WORLD for target in targets]
    if not args.config_only and any(p.exists() for p in target_worlds) and not all(p.exists() for p in target_worlds) and not args.replace_worlds:
        raise ValueError('Partially populated workload saves; use --replace-worlds to back up and recopy all three')
    dh_config = set_toml_values((source / 'config/DistantHorizons.toml').read_text(), {
        ('common.multiThreading', 'numberOfThreads'): 2,
        ('common.multiThreading', 'threadRunTimeRatio'): '0.5',
        ('common.worldGenerator', 'generatorPlan'): 'DISABLED',
        ('client.advanced.autoUpdater', 'enableAutoUpdater'): False,
        ('client.advanced.graphics.quality', 'lodChunkRenderDistanceRadius'): 128,
    })
    with ExitStack() as stack:
        if not args.config_only:
            stack.enter_context(unlocked_world(world))
        for existing in target_worlds:
            if existing.exists():
                stack.enter_context(unlocked_world(existing))
        coverage = [] if args.config_only else dh_coverage(world)
        print(f'Validated {args.scene}: original high, original medium, adaptive {args.preset}; vanilla {args.render_distance}, DH 128')
        print(f'Cloud high/adaptive linear resolution: 1/{args.cloud_upscaling}; lower: 1/4 (upstream default)')
        print('Only option/configuration evidence; Apple Iris linker/runtime sampler count remains unverified for new combinations.')
        if args.dry_run:
            print('No files changed.')
            return
        fingerprint = None if args.config_only else world_fingerprint(world)
        stamp, copied = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'), {}
        output.mkdir(parents=True, exist_ok=True)
        (output / 'control.json').write_text(json.dumps(control, indent=2) + '\n')
        for mode, target, target_world in zip(MODES, targets, target_worlds):
            if not args.config_only:
                target_world.parent.mkdir(parents=True, exist_ok=True)
                if target_world.exists() and args.replace_worlds:
                    backup = target / 'save-backups' / f'{WORLD}-{stamp}'
                    backup.parent.mkdir(parents=True, exist_ok=True)
                    target_world.rename(backup)
                if not target_world.exists():
                    storage_copytree(world, target_world, ignore=shutil.ignore_patterns('session.lock'))
                    copied[mode] = world_fingerprint(target_world)
                    if copied[mode] != fingerprint:
                        raise ValueError(f'Copied save differs: {target_world}')
            (target / 'shaderpacks').mkdir(parents=True, exist_ok=True)
            (target / 'config').mkdir(exist_ok=True)
            (target / 'mods').mkdir(exist_ok=True)
            selected = PACK if mode == 'adaptive' else original.name
            shutil.copy2(pack if mode == 'adaptive' else original, target / 'shaderpacks' / selected)
            (target / 'shaderpacks' / (selected + '.txt')).write_text(''.join(f'{key}={value}\n' for key, value in sorted(options[mode].items())))
            (target / 'config/iris.properties').write_text(f'enableShaders=true\nshaderPack={selected}\nallowUnknownShaders=false\nmaxShadowRenderDistance=32\ncolorSpace=SRGB\n')
            (target / 'config/DistantHorizons.toml').write_text(dh_config)
            (target / 'config/shaderlod-client.toml').write_text(
                f'[lod]\nenabled = {str(mode == "adaptive").lower()}\npreset = "{args.preset}"\nnear = 32.0\nfar = 96.0\ntransition = 8.0\ndebug = false\n')
            (target / 'options.txt').write_text(f'renderDistance:{args.render_distance}\nsimulationDistance:8\nmaxFps:260\nenableVsync:false\npauseOnLostFocus:false\nfullscreen:false\nguiScale:2\nclouds:0\nfov:0.0\n')
            sodium = source / 'config/sodium-options.json'
            if sodium.exists():
                shutil.copy2(sodium, target / 'config' / sodium.name)
            shutil.copy2(dh_jar, target / 'mods' / dh_jar.name)
            print(f'Prepared {target}; {"configuration only" if args.config_only else "save copied" if mode in copied else "save preserved"}')
        manifest = {
            'schema': 1, 'scene': args.scene, 'created_at_utc': stamp,
            'validation': 'Pinned option declarations/ranges and configuration only; runtime pending',
            'configuration_only': args.config_only, 'camera_verified': False,
            'camera_note': 'Operator supplied' if args.camera else 'Inherited forest camera; coastalwater needs actual shoreline verification',
            'source_world': str(world), 'world_name': WORLD, 'source_world_sha256': fingerprint,
            'copied_world_sha256': copied, 'source_dh_coverage': coverage,
            'runtime_lock': lock, 'adaptive_pack_sha256': file_hash(pack), 'adaptive_build': build,
            'control': control, 'quality_options': options, 'render_distance_chunks': args.render_distance,
            'cloud_upscaling': {'high': args.cloud_upscaling, 'lower': 4, 'adaptive': args.cloud_upscaling},
            'cloud_buffer_pixel_fraction': {mode: 1.0 / int(opts['CLOUDS_TEMPORAL_UPSCALING']) ** 2 for mode, opts in options.items()},
            'dh_radius_chunks': 128, 'sampler_limit': 16, 'sampler_validation': 'Actual Iris driver linking required',
            'unsupported_tests': ['SSR disabled until DH-enabled Iris/Apple linker sampler-budget proof', 'POM requires a heightmap resource pack'],
        }
        (output / 'workload-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scene', choices=SCENES, default='forest')
    parser.add_argument('--source', type=Path, default=ROOT / 'run-landscape/lod-on')
    parser.add_argument('--source-world', default=WORLD)
    parser.add_argument('--output', type=Path, default=ROOT / 'run-workloads')
    parser.add_argument('--pack', type=Path, default=ROOT / 'run/shaderpacks' / PACK)
    parser.add_argument('--render-distance', type=int, choices=(16, 24, 32), default=24)
    parser.add_argument('--cloud-upscaling', type=int, choices=(1, 2, 3, 4), default=4,
                        help='High/adaptive cloud resolution divisor; 1 is premium full resolution, 4 is upstream default; lower stays 4')
    parser.add_argument('--preset', choices=('BALANCED', 'PERFORMANCE', 'AGGRESSIVE'), default='BALANCED')
    parser.add_argument('--camera', type=float, nargs=5, metavar=('X', 'Y', 'Z', 'YAW', 'PITCH'))
    parser.add_argument('--config-only', action='store_true', help='Write managed configs/packs without reading or copying source saves')
    parser.add_argument('--replace-worlds', action='store_true', help='Back up old workload saves and create fresh matched copies')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    try:
        prepare(args)
    except (OSError, ValueError, KeyError, sqlite3.Error, zipfile.BadZipFile) as error:
        parser.exit(1, f'Cannot prepare workloads: {error}\n')


if __name__ == '__main__':
    main()
