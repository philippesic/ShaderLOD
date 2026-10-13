#!/usr/bin/env python3
"""Stage the exact NF26.2 archive-digest allocation fix without changing production."""
from __future__ import annotations

import argparse
import difflib
import hashlib
import json
from pathlib import Path
import shutil
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/integration/bindingcompat/'
PINS = {
    'MakeUpSourceCertificate.java': '404be80e5e140e0239a54fa50a98468928481b73740431f6d95d6f325c749186',
    'BslSourceCertificate.java': '05f75596b651ad1a27dfef362ccd63c35d2d874ad14dae016f479abd848520e6',
    'ReimaginedSourceCertificate.java': 'e131107997af42421a110569c6d4ba4ef8e599dc395785555ee8e224f3c6d416',
}
BEFORE = 'String as=sha(Files.readAllBytes(a)),bs=a.equals(b)?as:sha(Files.readAllBytes(b));'
AFTER = 'String as=SourceArchiveDigest.sha256(a),bs=a.equals(b)?as:SourceArchiveDigest.sha256(b);'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def prepare(output, root=ROOT):
    output, root = Path(output), Path(root)
    if output.exists():
        raise ValueError('Output already exists; choose a new directory')
    helper_name = PACKAGE + 'SourceArchiveDigest.java'
    if (root / helper_name).exists():
        raise ValueError('Production helper already exists; review adoption instead of overwriting')
    helper_path = root / 'scripts/java/SourceArchiveDigest.java'
    helper = helper_path.read_bytes()
    before, after = {}, {}
    for name, expected in PINS.items():
        target = PACKAGE + name
        data = (root / target).read_bytes()
        if sha(data) != expected or data.count(BEFORE.encode()) != 1:
            raise ValueError('Source certificate changed: ' + name)
        before[target] = data
        after[target] = data.replace(BEFORE.encode(), AFTER.encode(), 1)
    before[helper_name] = b''
    after[helper_name] = helper
    patch = ''.join(''.join(difflib.unified_diff(
        before[name].decode().splitlines(keepends=True), data.decode().splitlines(keepends=True),
        fromfile='a/' + name if before[name] else '/dev/null', tofile='b/' + name))
        for name, data in after.items())
    receipt = {
        'schema': 1, 'scope': 'NF26.2 MakeUp/BSL/Reimagined archive stamp allocation only',
        'production_modified': False, 'runtime_verified': False,
        'full_byte_sha256_preserved': True, 'stamp_cadence_changed': False,
        'state_hash_and_source_admission_changed': False, 'buffer_bytes_per_hash': 65536,
        'inputs': {name: sha(data) for name, data in before.items() if data},
        'helper_source_sha256': sha(helper),
        'outputs': {name: sha(data) for name, data in after.items()},
        'future_consumer': 'Compile/package the reviewed overlay; same real public-control operation and source mutation/refusal cases. No FPS claim from source or standalone digest tests.',
    }
    with tempfile.TemporaryDirectory(prefix='shaderlod-stream-digest-') as temporary:
        stage = Path(temporary)
        for name, data in after.items():
            target = stage / 'overlay' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        (stage / 'integration.patch').write_text(patch)
        (stage / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
        for name, data in before.items():
            if data and (root / name).read_bytes() != data:
                raise ValueError('Source changed during preparation: ' + name)
        if helper_path.read_bytes() != helper or (root / helper_name).exists():
            raise ValueError('Helper changed during preparation')
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(stage, output)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True, help='New directory for an unapplied patch/overlay')
    args = parser.parse_args()
    print(json.dumps(prepare(args.output), indent=2))


if __name__ == '__main__':
    main()
