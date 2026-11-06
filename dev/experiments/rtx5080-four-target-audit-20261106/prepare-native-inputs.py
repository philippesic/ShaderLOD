"""Admit and verify transported audit inputs, then prepare two managed slots.

Profiles were staged on canonical Asahi with run_storage.copytree. This native
Windows step admits transport extraction, checks every staged file, and uses the
same maintained copy/checkout functions for canonical profiles and runtime slots.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tarfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', required=True, type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    sys.path.insert(0, str(root / 'scripts'))
    import run_storage as storage
    jdk = json.loads((root.parent / 'tools/jdk21.json').read_text())['java_home']
    os.environ['JAVA_HOME'] = jdk
    os.environ['PATH'] = str(Path(jdk) / 'bin') + os.pathsep + os.environ['PATH']
    assert not (root / 'transfer-inputs').exists(), 'Existing transport inputs preserved'
    with tarfile.open(root.parent / 'runtime-inputs.tar.gz') as tf:
        members = tf.getmembers()
        assert all(m.isfile() or m.isdir() for m in members), 'Links refused'
        assert all(m.name.startswith(('transfer-inputs/', 'dependencies/')) for m in members)
        storage.admit(root, sum(m.size for m in members), root=root)
        tf.extractall(root, filter='data')
    manifest = json.loads(args.manifest.read_text())
    for relative, row in manifest.items():
        path = root / 'transfer-inputs' / relative
        with path.open('rb') as stream:
            assert hashlib.file_digest(stream, 'sha256').hexdigest() == row['sha256'], str(path)
        assert path.stat().st_size == row['bytes']
    for name in ['canonical-client', 'canonical-pbr']:
        source, destination = root / 'transfer-inputs' / name, root.parent / name
        storage.copytree(source, destination, root=root)
        before = {w.name: storage.fingerprint(w) for w in (source / 'saves').iterdir()}
        assert before == {w.name: storage.fingerprint(w) for w in (destination / 'saves').iterdir()}
        slot = 'rtx5080-pbr' if name.endswith('pbr') else 'rtx5080-baseline'
        print(storage.checkout(destination, slot, root=root), flush=True)
    lock = json.loads((root / 'benchmarks/profiles/v1/inputs.lock.json').read_text())
    for name, row in lock['profiles'].items():
        assert hashlib.sha256((root / row['original_archive']).read_bytes()).hexdigest() == row['archive_sha256'], name
    print(json.dumps(dict(profile_files_verified=len(manifest), originals_verified=True,
                          free_bytes=shutil.disk_usage(root).free)))


if __name__ == '__main__':
    main()
