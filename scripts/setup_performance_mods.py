#!/usr/bin/env python3
"""Fetch pinned optional NeoForge optimizers; never change a client or its config."""
import argparse
import hashlib
import json
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / 'dependencies/performance-mods.lock.json'


def validate(data, artifact):
    if len(data) != artifact['size']:
        raise RuntimeError(f"Size mismatch: {artifact['filename']}")
    for algorithm in ('sha256', 'sha512'):
        if hashlib.new(algorithm, data).hexdigest() != artifact['hashes'][algorithm]:
            raise RuntimeError(f"{algorithm} mismatch: {artifact['filename']}")


def fetch(artifact, destination, verify_only=False):
    if destination.exists():
        # A corrupted cache is a failure, rather than a silent replacement.
        validate(destination.read_bytes(), artifact)
        return 'Verified'
    if verify_only:
        raise RuntimeError(f"Missing artifact: {destination}")
    request = urllib.request.Request(artifact['url'], headers={
        'User-Agent': 'ShaderLOD-optional-runtime/0.1 (+https://modrinth.com)'
    })
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()
    validate(data, artifact)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + '.tmp')
    try:
        temporary.write_bytes(data)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return 'Downloaded'


def main():
    lock = json.loads(LOCK.read_text())
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stack', choices=lock['stacks'], default='optimized')
    parser.add_argument('--verify-only', action='store_true')
    parser.add_argument('--print-files', action='store_true',
                        help='After verification, print only absolute runtime jar paths.')
    args = parser.parse_args()
    runtime = json.loads((ROOT / lock['runtime_lock']).read_text())
    for key in ('minecraft', 'neoforge', 'java'):
        if runtime[key] != lock[key]:
            raise RuntimeError(f"Optional stack {key} no longer matches runtime.lock.json")
    for name in lock['stacks'][args.stack]:
        artifact = lock['artifacts'][name]
        if 'neoforge' not in artifact['loaders'] or lock['minecraft'] not in artifact['game_versions']:
            raise RuntimeError(f"Wrong loader or Minecraft artifact: {name}")
        filename = artifact['filename']
        if Path(filename).name != filename:
            raise RuntimeError(f"Invalid artifact filename: {filename}")
        destination = ROOT / lock['destination'] / filename
        action = fetch(artifact, destination, args.verify_only)
        print(destination if args.print_files else f'{action} {destination.relative_to(ROOT)}')
    if not args.print_files:
        print(f"Prepared optional stack {args.stack}; client configuration was not changed.")


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError) as error:
        print(f'Optional runtime error: {error}', file=sys.stderr)
        sys.exit(1)
