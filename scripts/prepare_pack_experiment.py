#!/usr/bin/env python3
"""Prepare explicit pinned pack experiments from stopped inspected scenes."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import shutil
import zipfile

from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint
from run_proof_matrix import read_option_properties, archive_defaults, normalize_requested_options
from prepare_workloads import profile_options


def prepare(source, output, family, bridge_cue=True, with_dh=False):
    source, output = source.resolve(), output.resolve()
    if output.exists() or source == output or source in output.parents or output in source.parents:
        raise ValueError('Choose a fresh, separate output directory')
    scene = 'coastalwater' if family.startswith(('makeup-', 'miniature')) or family == 'bsl' else 'forest'
    if source.parent.name != scene: raise ValueError('Source must be the matching inspected scene')
    runtime = json.loads((ROOT / 'dev/runtime.lock.json').read_text())
    if with_dh and not family.startswith('makeup-'):
        raise ValueError('DH compatibility override is scoped to pinned MakeUp experiments')
    if family == 'bsl':
        candidates = json.loads((ROOT / 'dev/shader-candidates.lock.json').read_text())
        pin = next(p for p in candidates['candidates'] if p['modrinth_project'] == 'bsl-shaders')['release']
        original = ROOT / 'dev/upstream/other-pack-research' / pin['filename']
        if file_hash(original) != pin['sha256']: raise ValueError('BSL release differs from pin')
        options = {'MULTICOLORED_BLOCKLIGHT': 'false', 'MCBL_SS': 'false', 'WATER_PARALLAX': 'true'}
        arms = {'high': (original, options), 'parallax-off': (original, options | {'WATER_PARALLAX': 'false'})}
        include_dh = False
    elif family.startswith('miniature'):
        candidates = json.loads((ROOT / 'dev/shader-candidates.lock.json').read_text())
        pin = next(p for p in candidates['candidates'] if p['modrinth_project'] == 'miniature-shader')['release']
        original = ROOT / 'dev/upstream/other-pack-research' / pin['filename']
        if file_hash(original) != pin['sha256']: raise ValueError('Miniature release differs from pin')
        options = {'SSR_MAX_STEPS': '10', 'SSR_STEP_SIZE': '1.6', 'SSR_BINARY_STEPS': '4',
                   'REFLECTIONS': '10', 'BLOCK_REFLECTIONS': '5'}
        arms = {'high': (original, options), 'lower': (original, options | {'SSR_BINARY_STEPS': '2'})}
        if family == 'miniature-core-bridge':
            probe = ROOT / 'build/Miniature-ShaderLOD-CoreProbe-2.19.zip'
            controls = options | {'SHADERLOD_MINIATURE_CORE_PROBE': '1',
                                  'SHADERLOD_MINIATURE_CORE_CUE': '1' if bridge_cue else '0'}
            arms = {name: (probe, controls) for name in ('bridge-absent', 'bridge-disabled', 'bridge-enabled', 'bridge-bands')}
        if family == 'miniature-refinement':
            probe = ROOT / 'build/Miniature-ShaderLOD-RefinementProbe-2.19.zip'
            arms.update({name: (probe, options | {'SHADERLOD_MINIATURE_REFINE_MODE': str(mode)})
                         for name, mode in (('refinement-off', 0), ('refinement-full', 1), ('refinement-lod', 2))})
        include_dh = False
    elif family.startswith('makeup-'):
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
        if family == 'makeup-core-bridge':
            probe = ROOT / 'build/MakeUp-ShaderLOD-CoreBridge2-Probe-9.5g.zip'
            controls = options | {'SHADERLOD_MAKEUP_WATER_SSR': '0',
                                 'SHADERLOD_MAKEUP_CORE_BRIDGE': '1', 'SHADERLOD_MAKEUP_BRIDGE_CUE': '1' if bridge_cue else '0'}
            arms = {name: (probe, controls) for name in ('bridge-absent', 'bridge-disabled', 'bridge-enabled', 'bridge-bands')}
        include_dh = False
    elif family in ('photon-cloud-secondary', 'photon-cloud-literal'):
        original = ROOT / 'build/photon-lighting-tier-no-atmosphere.zip'
        if file_hash(original) != 'b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c':
            raise ValueError('B15 baseline differs from frozen source')
        selected = read_option_properties((source / 'config/iris.properties').read_text())['shaderPack']
        if file_hash(source / 'shaderpacks' / selected) != file_hash(original): raise ValueError('Source is not B15')
        options = read_option_properties((source / 'shaderpacks' / (selected + '.txt')).read_text())
        if family == 'photon-cloud-literal':
            with zipfile.ZipFile(original) as archive:
                effective_options = archive_defaults(archive) | options
            if effective_options.get('CLOUDS_CUMULUS_PRECOMPUTE_LOCAL_COVERAGE') != 'true':
                raise ValueError('Literal probe requires the reviewed local-coverage profile')
            arms = {'literal-base': (original, options),
                    'literal-off': (ROOT / 'build/Photon-CloudLiteralSecondaryProbe-disabled.zip', options | {'PHOTON_CLOUD_LITERAL_SECONDARY_PROBE': 'false'}),
                    'literal-on': (ROOT / 'build/Photon-CloudLiteralSecondaryProbe-enabled.zip', options | {'PHOTON_CLOUD_LITERAL_SECONDARY_PROBE': 'true'})}
        else:
            arms = {'secondary-base': (original, options),
                    'secondary-off': (ROOT / 'build/Photon-CloudSecondaryExtentProbe-disabled.zip', options | {'PHOTON_CLOUD_SECONDARY_EXTENT_PROBE': 'false'}),
                    'secondary-on': (ROOT / 'build/Photon-CloudSecondaryExtentProbe-enabled.zip', options | {'PHOTON_CLOUD_SECONDARY_EXTENT_PROBE': 'true'})}
        include_dh = True
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
    if with_dh:
        include_dh = True
        dh = runtime['artifacts']['distanthorizons']
        if file_hash(ROOT / 'dev/runtime' / dh['filename'], 'sha512') != dh['hashes']['sha512']:
            raise ValueError('DH artifact differs from runtime lock')
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
            elif with_dh: shutil.copy2(ROOT / 'dev/runtime' / runtime['artifacts']['distanthorizons']['filename'], profile / 'mods' / runtime['artifacts']['distanthorizons']['filename'])
            if family in ('makeup-core-bridge', 'miniature-core-bridge', 'miniature-refinement'):
                enabled = arm in ('bridge-enabled', 'bridge-bands', 'refinement-off', 'refinement-full', 'refinement-lod')
                near, far = (8.0, 16.0) if arm == 'bridge-bands' else (96.0, 160.0)
                (profile / 'config/shaderlod-client.toml').write_text(
                    f'[lod]\nenabled={str(enabled).lower()}\npreset="PERFORMANCE"\nnear={near}\nfar={far}\ntransition=8.0\ndebug=false\n'
                    '[terrain]\nenabled=false\nvanillaChunkCap=16\nwarmSourceConfirmed=false\nwarmupSeconds=30\n')
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
    parser.add_argument('--family', required=True, choices=('bsl', 'miniature', 'miniature-core-bridge', 'miniature-refinement', 'makeup-water', 'makeup-probes', 'makeup-core-bridge', 'photon-cloud-consumer', 'photon-cloud-secondary', 'photon-cloud-literal'))
    parser.add_argument('--no-bridge-cue', action='store_true', help='Prepare normal core-bridge rendering, with diagnostic color compiled out')
    parser.add_argument('--with-dh', action='store_true', help='Pinned MakeUp compatibility experiment; never a support claim')
    args = parser.parse_args()
    prepare(args.source, args.output, args.family, not args.no_bridge_cue, args.with_dh)


if __name__ == '__main__': main()
