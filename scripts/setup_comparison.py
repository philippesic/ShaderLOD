#!/usr/bin/env python3
"""Prepare matched Photon + DH landscape profiles without launching clients."""
from __future__ import annotations

import argparse
from contextlib import ExitStack, contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
from pathlib import Path
import re
import shutil
import sqlite3
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
WORLD = 'ShaderLOD-Landscape'
PACK = 'Photon-ShaderLOD.zip'
MAC_OPTIONS = dict.fromkeys(('SH_SKYLIGHT', 'SHADOW_COLOR', 'COLORED_LIGHTS',
                            'CLOUD_SHADOWS', 'ENVIRONMENT_REFLECTIONS',
                            'SKY_REFLECTIONS', 'SHADOW_VPS', 'BORDER_FOG'), 'false')
MAC_OPTIONS['SHADERLOD_CACHED_SKY'] = 'true'


def file_hash(path: Path, algorithm='sha256') -> str:
    digest = hashlib.new(algorithm)
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def set_toml_values(contents: str, values: dict) -> str:
    """Modify audited generated keys, preserving DH's other values/comments."""
    section, remaining, output = '', dict(values), []
    for line in contents.splitlines(keepends=True):
        header = re.match(r'\s*\[([^]]+)\]\s*(?:#.*)?$', line)
        if header:
            section = header.group(1)
        entry = re.match(r'(\s*)([A-Za-z0-9_]+)\s*=.*', line)
        key = (section, entry.group(2)) if entry else None
        if key in remaining:
            line = f'{entry.group(1)}{key[1]} = {json.dumps(remaining.pop(key))}\n'
        output.append(line)
    if remaining:
        raise ValueError(f'Missing audited DH config keys: {sorted(remaining)}')
    result = ''.join(output)
    tomllib.loads(result)
    return result


@contextmanager
def unlocked_world(world: Path):
    """Java NIO FileLock and POSIX lockf share advisory locks on the local Mac."""
    lock = world / 'session.lock'
    if not lock.exists():
        yield
        return
    with lock.open('r+b') as stream:
        try:
            fcntl.lockf(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError(f'World is open; stop its client before preparing: {world}') from error
        try:
            yield
        finally:
            fcntl.lockf(stream, fcntl.LOCK_UN)


def world_fingerprint(world: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(world.rglob('*')):
        if path.is_file() and path.name != 'session.lock':
            digest.update(path.relative_to(world).as_posix().encode())
            digest.update(b'\0')
            digest.update(bytes.fromhex(file_hash(path)))
    return digest.hexdigest()


def dh_coverage(world: Path) -> list[dict]:
    database = world / 'data/DistantHorizons.sqlite'
    if not database.exists():
        raise ValueError(f'Warmed overworld DH database is missing: {database}')
    with sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True) as connection:
        rows = connection.execute('SELECT DetailLevel,count(*),min(PosX),max(PosX),min(PosZ),max(PosZ) FROM FullData GROUP BY DetailLevel').fetchall()
    if not rows:
        raise ValueError('DH database is empty; warm the source landscape before copying')
    return [dict(zip(('detail', 'sections', 'min_x', 'max_x', 'min_z', 'max_z'), row)) for row in rows]


def prepare(args: argparse.Namespace) -> None:
    source, output, pack = args.source.resolve(), args.output.resolve(), args.pack.resolve()
    world = source / 'saves' / args.source_world
    if not (world / 'level.dat').exists():
        raise ValueError(f'Source world does not exist: {world}')
    if output == source or source in output.parents or output in source.parents:
        raise ValueError('Comparison output must be separate from source game directory')
    lock = json.loads((ROOT / 'dev/runtime.lock.json').read_text())
    dh_artifact, photon = lock['artifacts']['distanthorizons'], lock['artifacts']['photon']
    dh_jar = ROOT / 'dev/runtime' / dh_artifact['filename']
    baseline = ROOT / 'dev/upstream' / photon['filename']
    for path, artifact in ((dh_jar, dh_artifact), (baseline, photon)):
        if file_hash(path, 'sha512') != artifact['hashes']['sha512']:
            raise ValueError(f'Pinned artifact checksum mismatch: {path}')
    with zipfile.ZipFile(pack) as archive:
        pack_metadata = json.loads(archive.read('shaderlod-build.json'))
    if pack_metadata['base_version'] != photon['version']:
        raise ValueError('Patched pack is not based on pinned Photon version')
    targets = [output / f'lod-{mode}' for mode in ('on', 'off')]
    worlds = [target / 'saves' / WORLD for target in targets]
    if any(p.exists() for p in worlds) and not all(p.exists() for p in worlds) and not args.replace_worlds:
        raise ValueError('Only one comparison save exists; use --replace-worlds to back up and restore a matched pair')
    values = {
        ('common.multiThreading', 'numberOfThreads'): args.dh_threads,
        ('common.multiThreading', 'threadRunTimeRatio'): '0.5',
        ('common.worldGenerator', 'generatorPlan'): 'SURFACE_THEN_CHUNKS' if args.generate else 'DISABLED',
        ('common.worldGenerator', 'showGenerationProgress'): 'DISABLED',
        ('client.advanced.autoUpdater', 'enableAutoUpdater'): False,
        ('client.advanced.autoUpdater', 'enableSilentUpdates'): False,
        ('client.advanced.debugging', 'rendererMode'): 'DEFAULT',
        ('client.advanced.debugging', 'lodOnlyMode'): False,
        ('client.advanced.graphics.genericRendering', 'enableCloudRendering'): False,
        ('client.advanced.graphics.quality', 'lodChunkRenderDistanceRadius'): args.dh_radius,
    }
    dh_config = set_toml_values((source / 'config/DistantHorizons.toml').read_text(), values)
    common_options = ''.join(f'{key}={value}\n' for key, value in MAC_OPTIONS.items())
    minecraft_options = (f'renderDistance:{args.render_distance}\nsimulationDistance:8\nmaxFps:260\n'
                         'enableVsync:false\npauseOnLostFocus:false\nfullscreen:false\n'
                         'guiScale:2\nclouds:0\nfov:0.0\n')
    with ExitStack() as stack:
        stack.enter_context(unlocked_world(world))
        for existing in worlds:
            if existing.exists():
                stack.enter_context(unlocked_world(existing))
        coverage = dh_coverage(world)
        print(f'Source: {world}; warmed DH sections: {sum(row["sections"] for row in coverage)}')
        print(f'Matched landscape: vanilla {args.render_distance} chunks; DH {args.dh_radius} chunks; {args.dh_threads} DH threads')
        print('DH generation:', 'ON (warm-up only; disable before timing)' if args.generate else 'OFF (saved terrain only)')
        if args.dry_run:
            print('Validated inputs; no profiles changed.')
            return
        source_fingerprint, copied = world_fingerprint(world), {}
        stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
        for mode, target, target_world in zip(('on', 'off'), targets, worlds):
            target_world.parent.mkdir(parents=True, exist_ok=True)
            if args.replace_worlds and target_world.exists():
                backup = target / 'save-backups' / f'{WORLD}-{stamp}'
                backup.parent.mkdir(parents=True, exist_ok=True)
                target_world.rename(backup)
                print(f'Preserved old save: {backup}')
            if not target_world.exists():
                shutil.copytree(world, target_world, ignore=shutil.ignore_patterns('session.lock'))
                copied[mode] = world_fingerprint(target_world)
                if copied[mode] != source_fingerprint:
                    raise ValueError(f'Copied save differs from source: {target_world}')
            (target / 'shaderpacks').mkdir(exist_ok=True)
            for shaderpack in (pack, baseline):
                name = PACK if shaderpack == pack else baseline.name
                shutil.copy2(shaderpack, target / 'shaderpacks' / name)
                (target / 'shaderpacks' / (name + '.txt')).write_text(common_options)
            (target / 'config').mkdir(exist_ok=True)
            selected = PACK if mode == 'on' else baseline.name
            (target / 'config/iris.properties').write_text(f'enableShaders=true\nshaderPack={selected}\nallowUnknownShaders=false\nmaxShadowRenderDistance=32\ncolorSpace=SRGB\n')
            (target / 'config/DistantHorizons.toml').write_text(dh_config)
            shader_config = (f'[lod]\nenabled = {str(mode == "on").lower()}\npreset = "PERFORMANCE"\n'
                             'near = 32.0\nfar = 96.0\ntransition = 8.0\ndebug = false\n')
            tomllib.loads(shader_config)
            (target / 'config/shaderlod-client.toml').write_text(shader_config)
            (target / 'options.txt').write_text(minecraft_options)
            sodium = source / 'config/sodium-options.json'
            if sodium.exists():
                shutil.copy2(sodium, target / 'config' / sodium.name)
            (target / 'mods').mkdir(exist_ok=True)
            shutil.copy2(dh_jar, target / 'mods' / dh_jar.name)
            print(f'Prepared {target}; save {"copied" if mode in copied else "preserved"}; shader={selected}')
        manifest = {
            'prepared_at_utc': stamp, 'source_world': str(world), 'world_name': WORLD,
            'source_world_sha256': source_fingerprint, 'copied_world_sha256': copied,
            'worlds_preserved': not bool(copied), 'source_dh_coverage': coverage,
            'pack_sha256': file_hash(pack), 'pack_metadata': pack_metadata, 'runtime_lock': lock,
            'dh_config_overrides': {f'{table}.{key}': value for (table, key), value in values.items()},
            'common_shader_options': MAC_OPTIONS, 'render_distance_chunks': args.render_distance,
            'baseline_pack': baseline.name, 'simulation_distance_chunks': 8,
        }
        (output / 'comparison-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('Launch ./gradlew runClientLodOn and ./gradlew runClientLodOff separately.')
    print('For GPU measurements use one patched-pack client and alternate LOD; pair is shared-GPU visual preview.')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT / 'run')
    parser.add_argument('--source-world', default='ShaderLOD-Smoke')
    parser.add_argument('--output', type=Path, default=ROOT / 'run-landscape')
    parser.add_argument('--pack', type=Path, default=ROOT / 'run/shaderpacks' / PACK)
    parser.add_argument('--dh-radius', type=int, choices=(128, 256), default=128)
    parser.add_argument('--dh-threads', type=int, choices=(2, 3, 4), default=2)
    parser.add_argument('--render-distance', type=int, choices=(16, 24, 32), default=24)
    parser.add_argument('--generate', action='store_true', help='Enable DH warm-up; do not benchmark while generating')
    parser.add_argument('--replace-worlds', action='store_true', help='Back up saves, then copy a fresh matched pair')
    parser.add_argument('--dry-run', action='store_true', help='Validate inputs/config keys without changing profiles')
    args = parser.parse_args()
    try:
        prepare(args)
    except (OSError, ValueError, KeyError, sqlite3.Error, zipfile.BadZipFile) as error:
        parser.exit(1, f'Cannot prepare landscape comparison: {error}\n')


if __name__ == '__main__':
    main()
