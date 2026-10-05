#!/usr/bin/env python3
"""Assemble pinned, default-off Photon projected-footprint procedural water LOD."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = Path('dev/upstream/photon_v1.3b.zip')
BASE_SHA256 = '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
MANIFEST = Path('patches/photon-experiments/procedural-lod.json')
SWITCH = 'SHADERLOD_PROCEDURAL_FOOTPRINT'
SETTINGS = 'shaders/settings.glsl'
PROGRAM = 'shaders/program/gbuffers_all_translucent.fsh'
MEMBERS = (SETTINGS, PROGRAM)
METADATA = 'shaderlod-procedural-lod.json'
ARMS = ('original', 'disabled', 'enabled')
HELPER_ROOT = Path('shaderpacks/photon-procedural-lod')


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def archive_members(base: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(base) as archive:
        names = archive.namelist()
        if len(set(names)) != len(names):
            raise ValueError('Duplicate original Photon archive entry')
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or '..' in path.parts or '\\' in name:
                raise ValueError('Unsafe Photon archive entry')
        return {info.filename: archive.read(info) for info in archive.infolist() if not info.is_dir()}


def patch_members(files: dict[str, bytes], manifest: dict, arm: str,
                  root: Path = ROOT) -> dict[str, bytes]:
    if arm not in ARMS:
        raise ValueError('Unknown control arm')
    if (manifest.get('schema') != 1 or manifest.get('base_sha256') != BASE_SHA256
            or manifest.get('switch') != SWITCH or manifest.get('quality_class') != 'experimental_unaccepted'
            or manifest.get('base_version') != 'v1.3b'):
        raise ValueError('Unexpected procedural LOD manifest')
    if tuple(e['path'] for e in manifest['members']) != MEMBERS:
        raise ValueError('Unexpected procedural patch scope/order')
    for pin in manifest['pins']:
        if pin['path'] not in files or sha256(files[pin['path']]) != pin['sha256']:
            raise ValueError('Pinned upstream dependency changed: ' + pin['path'])
    if (root / HELPER_ROOT / 'LICENSE').read_bytes() != files['LICENSE']:
        raise ValueError('Derivative source LICENSE must exactly retain original Photon license')
    result = files.copy()
    for entry in manifest['members']:
        name = entry['path']
        if sha256(files[name]) != entry['source_sha256']:
            raise ValueError('Pinned Photon member changed: ' + name)
        source = files[name].decode()
        for replacement in entry['replacements']:
            before = replacement['before']
            if not before or source.count(before) != 1:
                raise ValueError('Anchor must appear exactly once: ' + name)
            source = source.replace(before, replacement['after'], 1)
        if sha256(source.encode()) != entry['patched_sha256']:
            raise ValueError('Reviewed patch fingerprint changed: ' + name)
        if arm == 'enabled' and name == SETTINGS:
            source = source.replace('//#define ' + SWITCH + '\n', '#define ' + SWITCH + '\n', 1)
            if sha256(source.encode()) != entry['enabled_sha256']:
                raise ValueError('Enabled settings fingerprint changed')
        result[name] = files[name] if arm == 'original' else source.encode()
    for helper in manifest['helpers']:
        name = helper['path']
        if name in files:
            raise ValueError('Helper collides with original member')
        data = (root / HELPER_ROOT / name).read_bytes()
        if sha256(data) != helper['sha256']:
            raise ValueError('Reviewed helper fingerprint changed: ' + name)
        if arm != 'original':
            result[name] = data
    return result


def build(output: Path | None = None, base: Path | None = None,
          arm: str = 'disabled', root: Path = ROOT) -> dict:
    base = (base or root / BASE).resolve()
    output = (output or root / 'build' / ('Photon-ProceduralFootprint-' + arm + '.zip')).resolve()
    if arm not in ARMS or output.suffix.lower() != '.zip' or not output.is_relative_to((root / 'build').resolve()) or output == base:
        raise ValueError('Output must be a fresh ZIP under ignored build/; valid arm required')
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError('Original Photon ZIP fingerprint mismatch')
    manifest_bytes = (root / MANIFEST).read_bytes()
    manifest = json.loads(manifest_bytes)
    original = archive_members(base)
    if METADATA in original:
        raise ValueError('Metadata collides with original archive')
    files = patch_members(original, manifest, arm, root)
    modified = sorted(n for n in original if files[n] != original[n])
    added = sorted(set(files) - set(original))
    if modified != ([] if arm == 'original' else sorted(MEMBERS)) or added != ([] if arm == 'original' else sorted(e['path'] for e in manifest['helpers'])):
        raise ValueError('Archive scope changed')
    metadata = {k: manifest[k] for k in ('base_version', 'base_sha256', 'switch', 'quality_class', 'distance_units', 'transition', 'fallback', 'attribution', 'activation')}
    metadata.update(arm=arm, manifest_sha256=sha256(manifest_bytes), modified_upstream_files=modified,
                    added_helpers=added, member_sha256={n: sha256(files[n]) for n in (*MEMBERS, *added)},
                    license_sha256=sha256(files['LICENSE']), unchanged_upstream_member_count=len(original)-len(modified),
                    consumed_core_fields=['shaderlodBridgeVersion', 'shaderlodEnabled'] if arm == 'enabled' else [],
                    consumed_extension_fields=[], runtime_compilation='unverified', gpu_benefit='unmeasured')
    files[METADATA] = (json.dumps(metadata, sort_keys=True, indent=2) + '\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix='.zip', delete=False) as temporary:
        path = Path(temporary.name)
    try:
        with zipfile.ZipFile(path, 'w') as archive:
            for name, data in sorted(files.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data, compresslevel=9)
        path.replace(output)
    finally:
        path.unlink(missing_ok=True)
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--arm', choices=ARMS, default='disabled')
    parser.add_argument('--base', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        metadata = build(args.output, args.base, args.arm)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, str(error) + '\n')
    print(json.dumps(metadata, sort_keys=True, indent=2))

if __name__ == '__main__':
    main()
