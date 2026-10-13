#!/usr/bin/env python3
"""Derive an offline Iris declaration delta anchored to an observed native option map.

Explicit jars/JDK only; no downloads, Gradle, client, sidecar edits or loaded-map claim.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

SOURCE = Path(__file__).resolve().parent / 'java/IrisOptionInventory.java'


def file_hash(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def option_map(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate option key: ' + key)
            result[key] = value
        return result
    value = json.loads(Path(path).read_text(), object_pairs_hook=unique)
    if not isinstance(value, dict) or any(not isinstance(v, str) for v in value.values()):
        raise ValueError('Expected a JSON object with string option values: ' + str(path))
    return value


def discover(original, generated, native_options, iris, iris_sha256, dependencies,
             java, overrides=None, timeout=30):
    paths = [Path(p).resolve(strict=True) for p in (original, generated, native_options, iris, java)]
    original, generated, native_options, iris, java = paths
    dependencies = [Path(p).resolve(strict=True) for p in dependencies]
    if not all(p.is_file() for p in paths + dependencies):
        raise ValueError('All inputs must be files')
    if file_hash(iris) != iris_sha256:
        raise ValueError('Pinned Iris JAR hash mismatch')
    if iris in dependencies or len(dependencies) != len(set(dependencies)):
        raise ValueError('Duplicate parser classpath entry')
    native = option_map(native_options)
    if not native:
        raise ValueError('Native observation must not be empty')
    requested = {} if overrides is None else option_map(overrides)
    if set(requested) & set(native):
        raise ValueError('Overrides may not modify native observed keys')
    inputs = [original, generated, native_options, iris, *dependencies, SOURCE, java]
    if overrides is not None:
        inputs.append(Path(overrides).resolve(strict=True))
    before = {str(path): file_hash(path) for path in inputs}
    with tempfile.TemporaryDirectory(prefix='shaderlod-iris-options-') as directory:
        requested_path = Path(directory) / 'added-overrides.json'
        requested_path.write_text(json.dumps(requested))
        command = [str(java), '--class-path', os.pathsep.join(map(str, [iris, *dependencies])),
                   str(SOURCE), str(original), str(generated), str(native_options), str(requested_path)]
        result = subprocess.run(command, text=True, capture_output=True, timeout=timeout)
        if result.returncode:
            raise ValueError(f'Iris discovery exited {result.returncode}:\n{result.stdout}\n{result.stderr}')
        report = json.loads(result.stdout)
    if any(file_hash(path) != digest for path, digest in before.items()):
        raise ValueError('Parser input changed during discovery')
    report['execution'] = {'command': command, 'exitCode': result.returncode,
                           'stderr': result.stderr, 'inputSha256': before,
                           'requestedAddedOverrides': requested}
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('original', 'generated', 'native-options', 'iris', 'iris-sha256', 'java'):
        parser.add_argument('--' + name, required=True)
    parser.add_argument('--dependency', action='append', default=[], help='Exact local parser dependency JAR; repeat as needed')
    parser.add_argument('--overrides', help='JSON string map for newly added options only')
    parser.add_argument('--output', type=Path, help='New report path; omitted prints JSON')
    args = parser.parse_args()
    if args.output and args.output.exists():
        parser.error('Output exists; preserve previous evidence')
    try:
        report = discover(args.original, args.generated, args.native_options, args.iris,
                          args.iris_sha256, args.dependency, args.java, args.overrides)
        content = json.dumps(report, indent=2, sort_keys=True) + '\n'
        if args.output:
            with args.output.open('x') as stream:
                stream.write(content)
        else:
            print(content, end='')
    except (ValueError, OSError, subprocess.TimeoutExpired) as failure:
        parser.exit(1, str(failure) + '\n')


if __name__ == '__main__':
    main()
