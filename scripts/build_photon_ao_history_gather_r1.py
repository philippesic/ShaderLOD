#!/usr/bin/env python3
"""Build a private, default-off Photon dead-history-gather candidate; no client."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = Path('dependencies/upstream/photon_v1.3b.zip')
BASE_SHA256 = '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
MANIFEST = Path('patches/photon-experiments/ao-history-gather-r1.json')
MANIFEST_SHA256 = '24795d62eb503a85dcf346719fe93d05c6155b85842703962cc1bd09dbfee45c'
SWITCH = 'SHADERLOD_AO_HISTORY_GATHER_R1'
AO = 'shaders/program/d3_ao.fsh'
SETTINGS = 'shaders/settings.glsl'
MEMBERS = (AO, SETTINGS)
HELPER = 'shaders/include/shaderlod/ao_history_gather_r1.glsl'
HELPER_SOURCE = Path('shaderpacks/photon-ao-history-gather-r1') / HELPER
METADATA = 'shaderlod-ao-history-gather-r1.json'
ARMS = ('original', 'disabled', 'enabled')


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_manifest(root=ROOT):
    raw = (root / MANIFEST).read_bytes()
    if sha256(raw) != MANIFEST_SHA256:
        raise ValueError('Reviewed history-gather manifest changed')
    return json.loads(raw)


def archive_members(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate upstream archive member')
        for name in names:
            p = PurePosixPath(name)
            if p.is_absolute() or '..' in p.parts or '\\' in name:
                raise ValueError('Unsafe archive member')
        return {n: archive.read(n) for n in names if not n.endswith('/')}


def patch_members(files, manifest, arm, root=ROOT):
    # Full reviewed manifest identity, rather than trusting self-described hashes.
    if manifest != load_manifest(root) or arm not in ARMS:
        raise ValueError('Unreviewed manifest/arm')
    if (manifest['base_sha256'] != BASE_SHA256 or manifest['switch'] != SWITCH or
        tuple(e['path'] for e in manifest['members']) != MEMBERS):
        raise ValueError('Unexpected scope')
    if sha256(files['LICENSE']) != manifest['license_sha256']:
        raise ValueError('Photon license changed')
    for name, digest in manifest['source_dependencies'].items():
        if sha256(files[name]) != digest:
            raise ValueError('Pinned history/consumer dependency changed: ' + name)
    helper = (root / HELPER_SOURCE).read_bytes()
    if HELPER in files or METADATA in files or sha256(helper) != manifest['addition']['sha256']:
        raise ValueError('Conflicting/changed private helper')
    result = files.copy()
    for entry in manifest['members']:
        name = entry['path']
        if sha256(files[name]) != entry['source_sha256']:
            raise ValueError('Pinned source changed: ' + name)
        text = files[name].decode()
        for r in entry['replacements']:
            if not r['before'] or text.count(r['before']) != 1:
                raise ValueError('Missing/ambiguous source anchor: ' + name)
            text = text.replace(r['before'], r['after'], 1)
        if sha256(text.encode()) != entry['patched_sha256']:
            raise ValueError('Patched source changed: ' + name)
        if arm == 'enabled' and name == SETTINGS:
            declaration = '//#define ' + SWITCH + '\n'
            if text.count(declaration) != 1:
                raise ValueError('Private option must default off')
            text = text.replace(declaration, '#define ' + SWITCH + '\n', 1)
            if sha256(text.encode()) != entry['enabled_sha256']:
                raise ValueError('Enabled settings changed')
        if arm != 'original':
            result[name] = text.encode()
    if arm != 'original':
        result[HELPER] = helper
    return result


def build(output=None, base=None, arm='disabled', root=ROOT):
    output = (output or root / 'build' / ('photon-ao-history-gather-r1-' + arm + '.zip')).resolve()
    base = (base or root / BASE).resolve()
    if (not output.is_relative_to((root / 'build').resolve()) or
        not output.name.startswith('photon-ao-history-gather-r1-') or output.suffix != '.zip' or output == base):
        raise ValueError('Output must be ignored build/photon-ao-history-gather-r1-*.zip')
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError('Original Photon archive changed')
    manifest = load_manifest(root)
    original = archive_members(base)
    files = patch_members(original, manifest, arm, root)
    changed = sorted(n for n in original if files[n] != original[n])
    if changed != ([] if arm == 'original' else sorted(MEMBERS)):
        raise ValueError('Unexpected upstream modification')
    metadata = dict(experiment=manifest['experiment'], arm=arm, base_sha256=BASE_SHA256,
        manifest_sha256=MANIFEST_SHA256, modified_upstream_files=changed,
        helper_sha256=sha256(files[HELPER]) if arm != 'original' else None,
        member_sha256={n:sha256(files[n]) for n in (*MEMBERS, 'LICENSE')},
        consumed_core_fields=['shaderlodBridgeVersion', 'shaderlodEnabled', 'shaderlodPreset'],
        contract=manifest['native_contract'], driver_compilation='unverified',
        shader_consumption='unverified', gpu_benefit='unmeasured',
        attribution='Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained')
    if arm != 'original':
        files[METADATA] = (json.dumps(metadata, indent=2, sort_keys=True) + '\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix='.zip', delete=False) as temp:
        temporary = Path(temp.name)
    try:
        with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, data in sorted(files.items()):
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data, compresslevel=9)
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    metadata['archive_sha256'] = sha256(output.read_bytes())
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--arm', choices=ARMS, default='disabled')
    parser.add_argument('--base', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        result = build(args.output, args.base, args.arm)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, 'Cannot prepare Photon history-gather candidate: ' + str(error) + '\n')
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == '__main__':
    main()
