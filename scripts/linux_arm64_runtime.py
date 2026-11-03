#!/usr/bin/env python3
"""Prepare locked Linux ARM64 natives, or launch the existing exact-client owner.

No personal Java/Gradle configuration is changed. JAVA_HOME must name a full JDK21.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify_libraries(lock, directory):
    for name, expected in lock['libraries'].items():
        path = directory / name
        if path.is_symlink() or not path.is_file() or digest(path.read_bytes()) != expected:
            raise ValueError('Missing or changed ARM64 native: ' + name)


def setup(lock, directory):
    directory.mkdir(parents=True, exist_ok=True)
    for artifact in lock['artifacts']:
        jar = directory / Path(artifact['url']).name
        if not jar.exists() or digest(jar.read_bytes()) != artifact['sha256']:
            with urllib.request.urlopen(artifact['url'], timeout=60) as response:
                data = response.read()
            if digest(data) != artifact['sha256']:
                raise ValueError('Native archive checksum mismatch: ' + jar.name)
            temporary = jar.with_suffix('.tmp')
            temporary.write_bytes(data)
            temporary.replace(jar)
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.infolist():
                name = Path(entry.filename).name
                if name not in lock['libraries']:
                    continue
                data = archive.read(entry)
                if digest(data) != lock['libraries'][name]:
                    raise ValueError('Extracted library checksum mismatch: ' + name)
                target = directory / 'natives' / name
                target.parent.mkdir(exist_ok=True)
                temporary = target.with_suffix('.tmp')
                temporary.write_bytes(data)
                temporary.replace(target)
    verify_libraries(lock, directory / 'natives')


def java_home(value):
    if not value:
        compiler = shutil.which('javac')
        if not compiler:raise ValueError('Full JDK21 missing; pass --jdk or set JAVA_HOME (javac not on PATH)')
        value = str(Path(compiler).resolve().parent.parent)
    home = Path(value).expanduser().resolve()
    if not all((home / p).is_file() for p in ('bin/java', 'bin/javac', 'lib/ct.sym')):
        raise ValueError('JAVA_HOME must contain a full JDK21 (java, javac and lib/ct.sym)')
    release = (home / 'release').read_text()
    if 'JAVA_VERSION="21.' not in release:
        raise ValueError('JDK21 required')
    return home


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('setup', 'check', 'client', 'benchmark', 'build'))
    parser.add_argument('--jdk', default=os.environ.get('JAVA_HOME', ''))
    args, forwarded = parser.parse_known_args(argv)
    if platform.system() != 'Linux' or platform.machine() not in ('aarch64', 'arm64'):
        parser.error('This helper is Linux ARM64 only; other hosts retain their normal workflow')
    lock = json.loads((ROOT / 'dependencies/linux-arm64-runtime.lock.json').read_text())
    directory = ROOT / 'dependencies/runtime/linux-arm64'
    if args.action == 'setup':
        setup(lock, directory)
        return
    verify_libraries(lock, directory / 'natives')
    home = java_home(args.jdk)
    if args.action == 'check':
        print('Full JDK21 and locked Linux ARM64 natives verified')
        return
    env = dict(os.environ, JAVA_HOME=str(home), PATH=str(home / 'bin') + os.pathsep + os.environ['PATH'])
    env.setdefault('ORG_GRADLE_PROJECT_clientHeap', '2304m')
    env.setdefault('ORG_GRADLE_PROJECT_clientProcessors', '2')
    env['GRADLE_OPTS'] = env.get('GRADLE_OPTS', '') + ' -Dorg.gradle.daemon=false -Dorg.gradle.workers.max=1 -Dorg.gradle.parallel=false -Dorg.gradle.jvmargs=-Xmx768m'
    if args.action=='build':
        import dev_client as dc
        dc.compile_current(env=env)
        print('Offline producer and source/classes receipt verified')
        return 0
    script='run_benchmark.py' if args.action=='benchmark' else 'dev_client.py'
    os.execve(sys.executable, [sys.executable, str(ROOT / 'scripts' / script), *forwarded], env)


if __name__ == '__main__':
    sys.exit(main())
