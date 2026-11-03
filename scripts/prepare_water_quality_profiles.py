#!/usr/bin/env python3
"""Prepare pinned Photon targeted quality controls; no client or world edits."""
from run_storage import copytree as storage_copytree
import argparse
from contextlib import ExitStack
import json
from pathlib import Path
import shutil
import zipfile

from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint
from run_proof_matrix import archive_defaults, normalize_requested_options, read_option_properties

PINS = {'high': '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead',
        'patched-off': 'b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c',
        'adaptive': 'b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c'}


def prepare(source_root, output, scene, isolations=False):
    source_root, output = source_root.resolve(), output.resolve()
    if output.exists() or source_root == output or source_root in output.parents or output in source_root.parents:
        raise ValueError('Choose a fresh separate output')
    if scene not in ('glint', 'underwater'):
        raise ValueError('Unknown targeted quality scene')
    base = source_root / 'coastalwater'
    control = json.loads((base / 'control.json').read_text())
    for key in ('enabled', 'terrainEnabled', 'terrainChunkCap', 'warmSourceConfirmed'):
        control.pop(key, None)
    control.update(x=500.5, z=660.5, y=68.0 if scene == 'glint' else 60.0,
                   yaw=78.0 if scene == 'glint' else 180.0,
                   pitch=2.2 if scene == 'glint' else -10.0,
                   time=12500 if scene == 'glint' else 7000, weather='clear', debug=False)
    pose = {key: control[key] for key in ('x', 'y', 'z', 'yaw', 'pitch')}
    if scene == 'glint':
        waypoints = [dict(pose, seconds=0), dict(pose, seconds=5, yaw=87.380, pitch=3.735),
                     dict(pose, seconds=10, yaw=96.0, pitch=5.0),
                     dict(pose, seconds=15, yaw=87.380, pitch=3.735), dict(pose, seconds=20)]
    else:
        waypoints = [dict(pose, seconds=0), dict(pose, seconds=20, z=640.5)]
    request = {'schema': 1, 'label': 'photon-' + scene + '-quality-r1', 'warmupSeconds': 30,
               'fps': 10, 'pngCompressionLevel': 0, 'expectedCameraFluid': 'NONE' if scene == 'glint' else 'WATER',
               'rejectFullBlockCamera': True, 'waypoints': waypoints}
    with ExitStack() as locks:
        for arm in PINS: locks.enter_context(unlocked_world(base / arm / 'saves' / WORLD))
        for arm, pin in PINS.items():
            profile = base / arm
            selected = read_option_properties((profile / 'config/iris.properties').read_text())['shaderPack']
            if file_hash(profile / 'shaderpacks' / selected) != pin:
                raise ValueError('Source pack differs: ' + arm)
            if any((profile / 'mods').glob('*DistantHorizons*.jar')):
                raise ValueError('Targeted SSR quality requires physical DH absence')
        world = base / 'high/saves' / WORLD
        fingerprint = world_fingerprint(world)
        destination = output / 'coastalwater'
        destination.mkdir(parents=True)
        arms = list(PINS) + (['alloff', 'water-only', 'ssr-only', 'fog-only', 'cloud-only'] if isolations else [])
        for arm in arms:
            target = destination / arm
            source_arm = arm if arm in PINS else 'adaptive'
            storage_copytree(base / source_arm, target, ignore=shutil.ignore_patterns('saves', 'logs', 'screenshots', 'camera-quality',
                            'workload-request.json', 'workload-measurement.json', 'benchmark.json'))
            (target / 'saves').mkdir()
            storage_copytree(world, target / 'saves' / WORLD, ignore=shutil.ignore_patterns('session.lock'))
            if world_fingerprint(target / 'saves' / WORLD) != fingerprint:
                raise ValueError('Copied canonical world differs: ' + arm)
            if arm not in PINS:
                pack = read_option_properties((target / 'config/iris.properties').read_text())['shaderPack']
                option_path = target / 'shaderpacks' / (pack + '.txt')
                options = read_option_properties(option_path.read_text()) if option_path.exists() else {}
                for effect in ('CLOUDS', 'WATER', 'SSR', 'FOG', 'MATERIALS'):
                    options['SHADERLOD_' + effect] = str(arm == effect.lower() + '-only').lower()
                with zipfile.ZipFile(target / 'shaderpacks' / pack) as archive:
                    normalize_requested_options(archive_defaults(archive), options)
                option_path.write_text(''.join(f'{key}={value}\n' for key, value in sorted(options.items())))
        if world_fingerprint(world) != fingerprint: raise ValueError('Source world changed')
        (destination / 'control.json').write_text(json.dumps(control, indent=2) + '\n')
        manifest = json.loads((base / 'workload-manifest.json').read_text())
        manifest.update(control=control, source_world_sha256=fingerprint, source_profile=str(base / 'high'),
                        copied_world_sha256={arm: fingerprint for arm in arms}, proof_profiles=arms,
                        quality_isolations=isolations,
                        targeted_water_scene=scene, preparation_status='complete',
                        validation='Pinned source and equal save copies only; sampled survey is not continuous fluid/glint quality proof')
        (destination / 'workload-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        (output / 'camera-request.json').write_text(json.dumps(request, indent=2) + '\n')
    print('Prepared', scene, destination, 'source world', fingerprint)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, default=ROOT / 'run-workloads/native-coastal-effects')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--scene', choices=('glint', 'underwater'), required=True)
    parser.add_argument('--isolations', action='store_true', help='Add fixed-pack alloff and single-effect quality controls')
    args = parser.parse_args()
    prepare(args.source_root, args.output, args.scene, args.isolations)
