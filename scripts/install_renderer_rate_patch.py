#!/usr/bin/env python3
"""Check/apply the bounded262 patch after owner release; never build, launch or repin freezes."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ARTIFACT = Path(__file__).resolve().parents[1] / 'patches/benchmark-rate-window-262'
QUALIFIER = Path(__file__).with_name('qualify_renderer_bracket.py')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def install(root, artifact=ARTIFACT, qualifier=QUALIFIER, apply=False, release=None):
    root = Path(root).resolve()
    manifest = json.loads((artifact / 'baseline-sha256.json').read_text())
    for name, hashes in manifest.items():
        path = root / name
        if path.is_symlink() or not path.is_file() or sha(path) != hashes['beforeSha256']:
            raise ValueError('Baseline changed; reconcile owner edits: ' + name)
    target = root / 'scripts/qualify_renderer_bracket.py'
    if target.exists() and (target.is_symlink() or sha(target) != sha(qualifier)):
        raise ValueError('Existing qualifier differs; preserve it')
    command = ['git', 'apply', str((artifact / 'consumer.patch').resolve())]
    subprocess.run(command[:2] + ['--check'] + command[2:], cwd=root, check=True, capture_output=True)
    result = {'patchCheckPassed': True, 'applied': False, 'requiresFreshDevBuildAndAdmissionFreeze': True,
              'files': manifest, 'qualifierSha256': sha(qualifier),
              'limits': 'Owner release receipt is historical evidence, not a live lane census. No build/client/driver or speed acceptance.'}
    if apply:
        if release is None:
            raise ValueError('Apply requires current exact owner release receipt')
        receipt = json.loads(Path(release).read_text())
        if receipt.get('zeroClients') is not True or receipt.get('runtimeLockFree') is not True or receipt.get('errors') != []:
            raise ValueError('Owner release must establish zero exact clients, free lock and no release errors')
        # Recheck immediately before mutation; no source/hash bypass and no freeze rewrite.
        for name, hashes in manifest.items():
            if sha(root / name) != hashes['beforeSha256']:
                raise ValueError('Source changed during admission: ' + name)
        subprocess.run(command, cwd=root, check=True, capture_output=True)
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            shutil.copyfile(qualifier, target)
        for name, hashes in manifest.items():
            if sha(root / name) != hashes['afterSha256']:
                raise ValueError('Applied source differs: ' + name)
        result.update(applied=True, releaseReceipt=str(Path(release).resolve()), releaseReceiptSha256=sha(Path(release)))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', required=True, type=Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--release-receipt', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = install(args.root, apply=args.apply, release=args.release_receipt)
    args.output.write_text(json.dumps(result, indent=2) + '\n')


if __name__ == '__main__':
    main()
