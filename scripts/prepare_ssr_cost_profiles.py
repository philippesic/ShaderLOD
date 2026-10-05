#!/usr/bin/env python3
"""Prepare four pinned SSR cost controls at the observed sun-glint pose."""
from contextlib import ExitStack
import argparse
import json
from pathlib import Path
import shutil
import zipfile

from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint
from run_proof_matrix import archive_defaults, normalize_requested_options, read_option_properties


def prepare(output):
    output = output.resolve()
    if output.exists(): raise ValueError('Fresh output required')
    source = ROOT / 'run-workloads/photon-glint-isolations-r1/coastalwater'
    original = ROOT / 'dev/upstream/photon_v1.3b.zip'
    b15 = ROOT / 'build/photon-lighting-tier-no-atmosphere.zip'
    full = ROOT / 'build/Photon-SSRFarFullTraceProbe-enabled.zip'
    reduced = ROOT / 'build/Photon-SSRFarReducedTraceProbe-enabled.zip'
    arms = {'high': ('high', original, '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead', {}),
            'ssr-skip': ('ssr-only', b15, 'b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c', {}),
            'ssr-full': ('ssr-only', full, '41ecbee8b72039654fb0d25b16a841e3a2f4500025ec918687f94c0b8cba1555', {'PHOTON_SSR_FAR_FULL_TRACE_PROBE': 'true'}),
            'ssr-reduced': ('ssr-only', reduced, '636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83', {'PHOTON_SSR_FAR_REDUCED_TRACE_PROBE': 'true'})}
    for _, pack, pin, _ in arms.values():
        if file_hash(pack) != pin: raise ValueError('Archive differs from frozen experiment: ' + str(pack))
    with ExitStack() as locks:
        for arm in ['high', 'ssr-only']: locks.enter_context(unlocked_world(source / arm / 'saves' / WORLD))
        world = source / 'high/saves' / WORLD
        fingerprint = world_fingerprint(world)
        base = output / 'coastalwater'; base.mkdir(parents=True)
        records = {}
        for name, (source_arm, pack, pin, overrides) in arms.items():
            src, dst = source / source_arm, base / name
            if any((src / 'mods').glob('*DistantHorizons*.jar')): raise ValueError('SSR cost controls require physical DH absence')
            shutil.copytree(src, dst, ignore=shutil.ignore_patterns('saves', 'logs', 'screenshots', 'camera-quality', 'benchmark.json', 'workload-request.json', 'workload-measurement.json'))
            (dst / 'saves').mkdir(); shutil.copytree(world, dst / 'saves' / WORLD, ignore=shutil.ignore_patterns('session.lock'))
            if world_fingerprint(dst / 'saves' / WORLD) != fingerprint: raise ValueError('Save copy differs')
            iris = read_option_properties((src / 'config/iris.properties').read_text()); old = iris['shaderPack']
            option_file = src / 'shaderpacks' / (old + '.txt')
            options = (read_option_properties(option_file.read_text()) if option_file.exists() else {}) | overrides
            with zipfile.ZipFile(pack) as archive: normalize_requested_options(archive_defaults(archive), options)
            shutil.rmtree(dst / 'shaderpacks'); (dst / 'shaderpacks').mkdir(); shutil.copy2(pack, dst / 'shaderpacks' / pack.name)
            (dst / 'shaderpacks' / (pack.name + '.txt')).write_text(''.join(f'{k}={v}\n' for k,v in sorted(options.items())))
            iris['shaderPack'] = pack.name; (dst / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in iris.items()))
            records[name] = {'archive': pack.name, 'sha256': pin, 'requested_options': options}
        if world_fingerprint(world) != fingerprint: raise ValueError('Source save changed')
        control = json.loads((source / 'control.json').read_text()); control.update(yaw=87.380, pitch=3.735)
        (base / 'control.json').write_text(json.dumps(control, indent=2) + '\n')
        manifest = json.loads((source / 'workload-manifest.json').read_text())
        manifest.update(control=control, source_world_sha256=fingerprint, copied_world_sha256={a:fingerprint for a in arms},
                        proof_profiles=list(arms), experiment_family='far-ssr-cost-controls', experiment_arms=records,
                        validation='Pinned configuration and copied saves only; actual timing/rendering pending')
        (base / 'workload-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('Prepared four SSR cost controls', output, fingerprint)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--output', type=Path, required=True)
    prepare(parser.parse_args().output)
