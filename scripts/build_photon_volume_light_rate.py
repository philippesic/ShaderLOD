#!/usr/bin/env python3
"""Pinned Photon original/OFF/sampling-only/reuse archives; no client operations."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = Path('dependencies/upstream/photon_v1.3b.zip')
BASE_SHA256 = '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
MANIFEST = Path('patches/photon-experiments/volume-light-rate.json')
SWITCH = 'SHADERLOD_VOLUME_LIGHT_RATE'
REUSE_SWITCH = 'SHADERLOD_VOLUME_LIGHT_RATE_REUSE'
CUMULUS = 'shaders/include/sky/clouds/cumulus.glsl'
SETTINGS = 'shaders/settings.glsl'
COVERAGE = 'shaders/include/sky/clouds/coverage_map.glsl'
MEMBERS = (CUMULUS, SETTINGS)
HELPER = 'shaders/include/shaderlod/volume_light_rate.glsl'
METADATA = 'shaderlod-volume-light-rate.json'
ARMS = ('original', 'disabled', 'sampling', 'enabled')


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def archive_members(path):
    with zipfile.ZipFile(path) as archive:
        names = [n for n in archive.namelist() if not n.endswith('/')]
        if len(names) != len(set(names)):
            raise ValueError('Duplicate archive member')
        if any(n.startswith('/') or '..' in Path(n).parts for n in names):
            raise ValueError('Unsafe archive member')
        return {n: archive.read(n) for n in names}


def patch_members(files, manifest, arm, root=ROOT):
    if (arm not in ARMS or manifest['schema'] != 1 or
        manifest['base_sha256'] != BASE_SHA256 or manifest['base_version'] != 'v1.3b' or
        manifest['experiment'] != 'photon-existing-pass-volume-light-rate' or
        manifest['switch'] != SWITCH or manifest['reuse_switch'] != REUSE_SWITCH or
        tuple(e['path'] for e in manifest['members']) != MEMBERS or
        manifest['addition']['path'] != HELPER or
        manifest['addition']['source_path'] != 'shaderpacks/photon-volume-light-rate/' + HELPER or
        set(manifest['source_dependencies']) != {COVERAGE}):
        raise ValueError('Unreviewed light-rate scope or arm')
    if sha256(files['LICENSE']) != manifest['license_sha256']:
        raise ValueError('Upstream license changed')
    for name, expected in manifest['source_dependencies'].items():
        if sha256(files[name]) != expected:
            raise ValueError('Pinned clone dependency changed: ' + name)
    result = files.copy()
    for entry in manifest['members']:
        name = entry['path']
        if sha256(files[name]) != entry['source_sha256']:
            raise ValueError('Pinned source changed: ' + name)
        source = files[name].decode()
        for replacement in entry['replacements']:
            before = replacement['before']
            if not before:
                raise ValueError('Empty source anchor')
            if replacement.get('occurrence') == 'final':
                if not source.endswith(before):
                    raise ValueError('Missing final include-guard anchor')
                source = source[:-len(before)] + replacement['after']
            else:
                if source.count(before) != 1:
                    raise ValueError('Missing or ambiguous source anchor')
                source = source.replace(before, replacement['after'], 1)
        payload = source.encode()
        if sha256(payload) != entry['patched_sha256']:
            raise ValueError('Patched source changed: ' + name)
        if name == SETTINGS and arm in ('sampling', 'enabled'):
            payload = payload.replace(f'//#define {SWITCH}\n'.encode(), f'#define {SWITCH}\n'.encode(), 1)
            if arm == 'enabled':
                payload = payload.replace(f'//#define {REUSE_SWITCH}\n'.encode(), f'#define {REUSE_SWITCH}\n'.encode(), 1)
            if sha256(payload) != entry['sampling_sha256' if arm == 'sampling' else 'enabled_sha256']:
                raise ValueError('Control settings changed')
        result[name] = files[name] if arm == 'original' else payload
    helper = (root / manifest['addition']['source_path']).read_bytes()
    if HELPER in files or sha256(helper) != manifest['addition']['sha256']:
        raise ValueError('Conflicting or changed helper')
    if arm != 'original':
        result[HELPER] = helper
    return result


def build(output=None, base=None, arm='disabled', root=ROOT):
    base = (base or root / BASE).resolve()
    output = (output or root / 'build' / f'volume-light-rate-{arm}.zip').resolve()
    if (not output.is_relative_to((root / 'build').resolve()) or
        not output.name.startswith('volume-light-rate-') or output.suffix != '.zip' or output == base):
        raise ValueError('Output must be ignored build/volume-light-rate-*.zip')
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError('Pinned original archive changed')
    manifest_bytes = (root / MANIFEST).read_bytes()
    manifest = json.loads(manifest_bytes)
    original = archive_members(base)
    if METADATA in original:
        raise ValueError('Conflicting metadata')
    files = patch_members(original, manifest, arm, root)
    modified = sorted(n for n in original if original[n] != files[n])
    if modified != ([] if arm == 'original' else sorted(MEMBERS)):
        raise ValueError('Unexpected upstream changes')
    metadata = {
        'experiment': manifest['experiment'], 'arm': arm,
        'base_sha256': BASE_SHA256, 'manifest_sha256': sha256(manifest_bytes),
        'modified_upstream_files': modified,
        'member_sha256': {n: sha256(files[n]) for n in (*MEMBERS, COVERAGE, 'LICENSE')},
        'helper_sha256': manifest['addition']['sha256'] if arm != 'original' else None,
        'activation': 'private switch/Overworld deferred1; core version 2/global enable 1',
        'mod_linkage': 'existing core version/global only; extension registration and effects transport NOT consumed',
        'sampling_control': 'sampling arm executes original counts with private explicit LOD0 density clones',
        'fallback': 'private OFF/nonconsumer/global OFF/unknown core select upstream body before cache setup',
        'quality': 'unaccepted; local and prior lighting continuity are heuristics, not remote-shadow certification',
        'runtime_compilation': 'unverified', 'gpu_benefit': 'unmeasured',
        'attribution': 'Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained',
    }
    files[METADATA] = (json.dumps(metadata, indent=2, sort_keys=True) + '\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix='.zip', delete=False) as temporary:
        temporary_path = Path(temporary.name)
    try:
        with zipfile.ZipFile(temporary_path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, payload in sorted(files.items()):
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                archive.writestr(info, payload, compresslevel=9)
        temporary_path.replace(output)
    finally:
        temporary_path.unlink(missing_ok=True)
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--arm', choices=ARMS, default='disabled')
    parser.add_argument('--base', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    print(json.dumps(build(args.output, args.base, args.arm), indent=2))


if __name__ == '__main__':
    main()
