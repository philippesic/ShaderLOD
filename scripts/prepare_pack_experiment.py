#!/usr/bin/env python3
"""Prepare only pinned MakeUp or standalone Photon experiment profiles."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import shutil
import zipfile

from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint
from run_proof_matrix import read_option_properties, archive_defaults, normalize_requested_options
from prepare_workloads import profile_options


def prepare(source, output, family):
    source, output = source.resolve(), output.resolve()
    if output.exists() or source == output or source in output.parents or output in source.parents:
        raise ValueError('Choose a fresh, separate output directory')
    scene = 'coastalwater' if family.startswith('makeup-') else 'forest'
    if source.parent.name != scene: raise ValueError('Source must be the matching inspected scene')
    runtime = json.loads((ROOT / 'dev/runtime.lock.json').read_text())
    if family.startswith('makeup-'):
        candidates = json.loads((ROOT / 'dev/shader-candidates.lock.json').read_text())
        pin = next(p for p in candidates['candidates'] if p['modrinth_project'] == 'makeup-ultra-fast-shaders')['release']
        original = ROOT / 'dev/upstream/other-pack-research' / pin['filename']
        if file_hash(original) != pin['sha256']: raise ValueError('MakeUp release differs from pin')
        adapter = ROOT / 'build/MakeUp-ShaderLOD-Experimental-9.5g.zip'
        with zipfile.ZipFile(original) as archive:
            options = profile_options(archive.read('shaders/shaders.properties').decode(), 'medium')
        arms = {'high': (original, options), 'adapter-off': (adapter, options | {'SHADERLOD_MAKEUP_WATER_SSR': '0'}),
                'adapter-on': (adapter, options | {'SHADERLOD_MAKEUP_WATER_SSR': '1',
                    'SHADERLOD_MAKEUP_SSR_START': '96.0', 'SHADERLOD_MAKEUP_SSR_END': '160.0'}),
                'lower': (original, options | {'REFLECTION_SLIDER': '1'})}
        if family == 'makeup-probes':
            arms = {key: value for key, value in arms.items() if key in ('high', 'adapter-off')}
            for probe in ('duplicate-implicit', 'duplicate-explicit', 'classifier-full'):
                arms[probe] = (ROOT / f'build/MakeUp-ShaderLOD-Probe-{probe}-9.5g.zip',
                               options | {'SHADERLOD_MAKEUP_WATER_SSR': '1'})
        include_dh = False
    else:
        original = ROOT / 'dev/upstream' / runtime['artifacts']['photon']['filename']
        if file_hash(original, 'sha512') != runtime['artifacts']['photon']['hashes']['sha512']:
            raise ValueError('Photon release differs from pin')
        selected = read_option_properties((source / 'config/iris.properties').read_text())['shaderPack']
        options = read_option_properties((source / 'shaderpacks' / (selected + '.txt')).read_text())
        options = {key: value for key, value in options.items() if not key.startswith('SHADERLOD_')}
        arms = {'high': (original, options),
                'cloudguard-off': (ROOT / 'build/Photon-CloudConsumerGuard-disabled.zip', options | {'PHOTON_CLOUD_CONSUMER_GUARD': 'false'}),
                'cloudguard-on': (ROOT / 'build/Photon-CloudConsumerGuard-enabled.zip', options | {'PHOTON_CLOUD_CONSUMER_GUARD': 'true'})}
        include_dh = True
    for pack, options in arms.values():
        with zipfile.ZipFile(pack) as archive:
            normalize_requested_options(archive_defaults(archive), options)
    world = source / 'saves' / WORLD
    with unlocked_world(world):
        fingerprint = world_fingerprint(world)
        manifest = json.loads((source.parent / 'workload-manifest.json').read_text())
        control = json.loads((source.parent / 'control.json').read_text())
        for key in ('enabled', 'terrainEnabled', 'terrainChunkCap', 'warmSourceConfirmed'): control.pop(key, None)
        destination = output / scene
        destination.mkdir(parents=True)
        hashes = {}
        for arm, (pack, options) in arms.items():
            profile = destination / arm
            shutil.copytree(source, profile, ignore=shutil.ignore_patterns('session.lock', 'logs', 'screenshots', 'crash-reports', 'downloads'))
            if world_fingerprint(profile / 'saves' / WORLD) != fingerprint: raise ValueError('Copied source save differs')
            shutil.rmtree(profile / 'shaderpacks')
            (profile / 'shaderpacks').mkdir()
            shutil.copy2(pack, profile / 'shaderpacks' / pack.name)
            (profile / 'shaderpacks' / (pack.name + '.txt')).write_text(''.join(f'{k}={v}\n' for k,v in sorted(options.items())))
            iris = read_option_properties((profile / 'config/iris.properties').read_text())
            iris['shaderPack'] = pack.name
            (profile / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in iris.items()))
            if not include_dh: (profile / 'mods' / runtime['artifacts']['distanthorizons']['filename']).unlink(missing_ok=True)
            for stale in ('workload-request.json', 'workload-measurement.json'): (profile / stale).unlink(missing_ok=True)
            hashes[arm] = {'archive': pack.name, 'sha256': file_hash(pack), 'requested_options': options}
        (destination / 'control.json').write_text(json.dumps(control, indent=2) + '\n')
        manifest.update(source_world_sha256=fingerprint, source_profile=str(source), control=control,
                        experiment_family=family, experiment_arms=hashes, dh_installed=include_dh,
                        validation='Prepared source/options only; runtime compilation/rendering unverified')
        (destination / 'workload-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Prepared {family}: {destination}; DH={include_dh}; all experimental runtime evidence pending')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--family', required=True, choices=('makeup-water', 'makeup-probes', 'photon-cloud-consumer'))
    args = parser.parse_args()
    prepare(args.source, args.output, args.family)


if __name__ == '__main__': main()
