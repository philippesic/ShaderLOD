#!/usr/bin/env python3
"""Fetch the pinned development stack without touching existing game profiles."""
import argparse
import hashlib
import json
import platform
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fetch(artifact, destination):
    expected = artifact['hashes']['sha512']
    if destination.exists() and hashlib.sha512(destination.read_bytes()).hexdigest() == expected:
        print(f"Verified {destination.relative_to(ROOT)}")
        return
    request = urllib.request.Request(artifact['url'], headers={'User-Agent': 'ShaderLOD-dev-bootstrap/0.1'})
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()
    if hashlib.sha512(data).hexdigest() != expected:
        raise RuntimeError(f"Checksum mismatch: {artifact['filename']}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + '.tmp')
    temporary.write_bytes(data)
    temporary.replace(destination)
    print(f"Downloaded {destination.relative_to(ROOT)}")


def write_new(path, contents):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        path.write_text(contents, encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--download-only', action='store_true')
    parser.add_argument('--profile', choices=['standard', 'mac'], default='mac' if platform.system() == 'Darwin' else 'standard')
    args = parser.parse_args()
    lock = json.loads((ROOT / 'dependencies/runtime.lock.json').read_text())
    for name, artifact in lock['artifacts'].items():
        destination = ROOT / ('dependencies/upstream' if name == 'photon' else 'dependencies/runtime') / artifact['filename']
        fetch(artifact, destination)
    if args.download_only:
        return
    subprocess.run([sys.executable, str(ROOT / 'scripts/build_shaderpack.py')], cwd=ROOT, check=True)
    pack = ROOT / 'run/shaderpacks/Photon-ShaderLOD.zip'
    baseline = ROOT / 'dependencies/upstream' / lock['artifacts']['photon']['filename']
    dh = lock['artifacts']['distanthorizons']['filename']
    (ROOT / 'run/mods').mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / 'dependencies/runtime' / dh, ROOT / 'run/mods' / dh)
    for directory in [ROOT / 'run', ROOT / 'run-iris']:
        (directory / 'shaderpacks').mkdir(parents=True, exist_ok=True)
        if directory.name != 'run':
            shutil.copy2(pack, directory / 'shaderpacks' / pack.name)
        shutil.copy2(baseline, directory / 'shaderpacks' / baseline.name)
        write_new(directory / 'config/iris.properties',
                  'enableShaders=true\nshaderPack=Photon-ShaderLOD.zip\n')
        write_new(directory / 'options.txt',
                  'renderDistance:12\nsimulationDistance:8\nmaxFps:120\nenableVsync:false\n')
        # Upstream suggests disabling SH skylight and colored shadows on Apple.
        # The extra options reproduce our limited-sampler smoke profile.
        if args.profile == 'mac':
            options = ('SH_SKYLIGHT=false\nSHADOW_COLOR=false\nCOLORED_LIGHTS=false\n'
                       'CLOUD_SHADOWS=false\nENVIRONMENT_REFLECTIONS=false\n'
                       'SKY_REFLECTIONS=false\nSHADOW_VPS=false\nBORDER_FOG=false\n')
            for name in [pack.name, baseline.name]:
                write_new(directory / 'shaderpacks' / (name + '.txt'), options)
    print(f"Prepared profiles: run (Iris + DH), run-iris (Iris only); shader profile={args.profile}")
    print('Launch ./gradlew runClient or ./gradlew runClientIris')


if __name__ == '__main__':
    main()
