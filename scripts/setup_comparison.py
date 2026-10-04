#!/usr/bin/env python3
"""Prepare independent matched Iris-only worlds for an LOD on/off preview."""
from pathlib import Path
import shutil
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def main():
    source = ROOT / 'run-iris'
    world = source / 'saves/ShaderLOD-Smoke'
    if not (world / 'level.dat').exists():
        raise SystemExit('Create the fixture first: ./gradlew runClientIris -PsmokeTest=true')
    for mode in ['on', 'off']:
        target = ROOT / 'run-compare' / ('lod-' + mode)
        if (target / 'saves/ShaderLOD-Smoke').exists():
            raise SystemExit(f'{target} already exists; preserving it. Move it aside before a fresh setup.')
    for mode in ['on', 'off']:
        target = ROOT / 'run-compare' / ('lod-' + mode)
        (target / 'saves').mkdir(parents=True, exist_ok=True)
        shutil.copytree(world, target / 'saves/ShaderLOD-Smoke', ignore=shutil.ignore_patterns('session.lock'))
        shutil.copytree(source / 'shaderpacks', target / 'shaderpacks')
        (target / 'config').mkdir()
        shutil.copy2(source / 'config/iris.properties', target / 'config/iris.properties')
        config = f'[lod]\nenabled = {str(mode == "on").lower()}\nnear = 32.0\nfar = 96.0\ntransition = 8.0\ndebug = false\n'
        tomllib.loads(config)
        (target / 'config/shaderlod-client.toml').write_text(config)
        (target / 'options.txt').write_text('renderDistance:12\nsimulationDistance:8\nmaxFps:260\nenableVsync:false\npauseOnLostFocus:false\n')
        print('Prepared',target)
    print('Run ./gradlew runClientLodOn and ./gradlew runClientLodOff in separate terminals.')


if __name__ == '__main__':
    main()
