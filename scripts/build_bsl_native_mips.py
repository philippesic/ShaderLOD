#!/usr/bin/env python3
"""Private G02 discriminator: remove only the two certified unused mip requests.

This candidate has no public adapter, bridge, quality changes or admission path.
The maintained benchmark owns installation, native controls and restoration.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import zipfile

import dev_client

ROOT = Path(__file__).resolve().parents[1]
ORIGINAL = '36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5'
MEMBERS = {
    'shaders/program/composite6.glsl': 'ed05d328124273cb80ef7cfc89f4757550871c7f09a3ff72b71c6f7743a84b03',
    'shaders/program/composite7.glsl': 'a5ccbc5e132b8c43d9195ddb3c3b0f9c292dcf13e2aad9d0f4ba9e0a084d5c0a',
}
ANCHOR = b'const bool colortex1MipmapEnabled = true;'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def validate(pack, options):
    expected = json.loads((ROOT/'src/main/resources/assets/shaderlod/bslquad/native384.json').read_text())
    if digest(Path(pack).read_bytes()) != ORIGINAL:
        raise ValueError('Pinned original BSL10.1.8 required')
    pbr=json.loads((ROOT/'src/main/resources/assets/shaderlod/adapters/qualification/bsl-prefix-native-options.json').read_text())
    actual=dev_client.properties(Path(options).read_text())
    if actual not in (expected,pbr):
        raise ValueError('Exact pinned native384 non-PBR or advanced-material profile required')
    return actual


def build(pack, options, output):
    expected = validate(pack, options)
    output = Path(output); sidecar = Path(str(output)+'.txt')
    if output.exists() or sidecar.exists():
        raise ValueError('Fresh candidate and sidecar required')
    with zipfile.ZipFile(pack) as source:
        names = source.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate original members')
        replacements = {}
        for name, pin in MEMBERS.items():
            raw = source.read(name)
            if digest(raw) != pin or raw.count(ANCHOR) != 1:
                raise ValueError('Certified mip declaration changed: '+name)
            replacements[name] = raw.replace(ANCHOR, ANCHOR.replace(b'true', b'false'))
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, 'x') as candidate:
            for info in source.infolist():
                candidate.writestr(copy.copy(info), replacements.get(info.filename, source.read(info.filename)))
    sidecar.write_bytes(Path(options).read_bytes())
    return {'original_sha256': ORIGINAL, 'candidate_sha256': digest(output.read_bytes()),
            'native_options': expected, 'changed_members': list(MEMBERS),
            'scope': 'Private certified native384 unused c6+c7 mip deletion; no adapter or public enablement.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack', required=True, type=Path)
    parser.add_argument('--options', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    print(json.dumps(build(**vars(parser.parse_args())), indent=2))
