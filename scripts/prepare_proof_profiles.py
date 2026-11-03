#!/usr/bin/env python3
"""Prepare isolated original/disabled/adaptive Photon profiles for repeated proof."""
from __future__ import annotations
from run_storage import copytree as storage_copytree
import argparse
import json
from pathlib import Path
import shutil
import zipfile
from setup_comparison import ROOT, PACK, WORLD, file_hash, unlocked_world
from prepare_workloads import prepare


def prepare_scene(args, scene):
    base = args.output / scene
    if base.exists():
        raise ValueError(f'Preserving existing proof profiles: {base}; choose a fresh --output')
    camera = args.water_camera if scene == 'coastalwater' else None
    workload = argparse.Namespace(source=args.source, output=args.output, scene=scene,
        pack=args.pack, source_world=WORLD, config_only=False, replace_worlds=False,
        dry_run=False, camera=camera, preset='PERFORMANCE', render_distance=24,
        cloud_upscaling=args.cloud_upscaling if scene == 'cloudstorm' else 4)
    prepare(workload)
    control_path = base / 'control.json'
    control = json.loads(control_path.read_text())
    for key in ('enabled', 'terrainEnabled', 'terrainChunkCap', 'warmSourceConfirmed'):
        control.pop(key, None)
    control_path.write_text(json.dumps(control, indent=2) + '\n')
    # The disabled control contains the same patched code and loaded mod as active.
    disabled = base / 'patched-off'
    with unlocked_world(base / 'adaptive/saves' / WORLD):
        storage_copytree(base / 'adaptive', disabled)
    lock = json.loads((ROOT / 'dependencies/runtime.lock.json').read_text())
    for mode in ('high', 'lower', 'adaptive', 'patched-off'):
        profile = base / mode
        enabled = mode == 'adaptive'
        (profile / 'config/shaderlod-client.toml').write_text(
            '[lod]\nenabled=' + str(enabled).lower() + '\npreset="PERFORMANCE"\n'
            'near=32.0\nfar=96.0\ntransition=8.0\ndebug=false\n[terrain]\nenabled=false\n'
            'vanillaChunkCap=16\nwarmSourceConfirmed=false\nwarmupSeconds=30\n')
        if mode in ('adaptive', 'patched-off'):
            options = profile / 'shaderpacks' / (PACK + '.txt')
            options.write_text(options.read_text() +
                'SHADERLOD_PRESERVE_LIGHTING=true\nSHADERLOD_CACHED_SKY=false\n'
                'SHADERLOD_CLOUD_NEAR=128.0\nSHADERLOD_CLOUD_FAR=1024.0\nSHADERLOD_CLOUD_TRANSITION=64.0\n')
        if args.without_dh:
            (profile / 'mods' / lock['artifacts']['distanthorizons']['filename']).unlink()
    manifest_path = base / 'workload-manifest.json'
    manifest = json.loads(manifest_path.read_text())
    manifest.update({'proof_profiles': ['high', 'lower', 'adaptive', 'patched-off'],
        'control': control, 'dh_installed': not args.without_dh,
        'geometry_enabled': False, 'shader_only': True,
        'camera_verified': False if scene == 'coastalwater' else True,
        'validation': 'Pinned source/options, copied warm data; runtime validation pending'})
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, default=ROOT / 'run-workloads/photon-proof')
    p.add_argument('--source', type=Path, default=ROOT / 'run-workloads/forest/high')
    p.add_argument('--pack', type=Path, default=ROOT / 'run/shaderpacks' / PACK)
    p.add_argument('--scenes', nargs='+', choices=('forest', 'cloudstorm', 'coastalwater'), default=['forest', 'cloudstorm'])
    p.add_argument('--without-dh', action='store_true')
    p.add_argument('--cloud-upscaling', type=int, choices=(1,2,3,4), default=1)
    p.add_argument('--water-camera', nargs=5, type=float, default=[200.5,70.0,680.5,135.0,7.0])
    args = p.parse_args()
    if len(args.scenes) != len(set(args.scenes)): p.error('Duplicate scenes')
    try:
        for scene in args.scenes: prepare_scene(args, scene)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        p.exit(1, f'Cannot prepare proof profiles: {error}\n')

if __name__ == '__main__': main()
