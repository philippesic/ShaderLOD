#!/usr/bin/env python3
"""Assemble pinned original/OFF/ON controls for existing-pass shadow tail budgets."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile
from build_photon_density_detail_probe import archive_members

ROOT = Path(__file__).resolve().parents[1]
BASE = Path('dependencies/upstream/photon_v1.3b.zip')
BASE_SHA256 = '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
MANIFEST = Path('patches/photon-experiments/shadow-budget.json')
SWITCH = 'SHADERLOD_SHADOW_BUDGET'
PCSS = 'shaders/include/lighting/shadows/pcss.glsl'
SETTINGS = 'shaders/settings.glsl'
MEMBERS = (PCSS, SETTINGS)
HELPER = 'shaders/include/shaderlod/shadow_budget.glsl'
ARMS = ('original', 'disabled', 'enabled')
METADATA = 'shaderlod-shadow-budget.json'


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def patch_members(files, manifest, arm, root=ROOT):
    if arm not in ARMS:
        raise ValueError('Unknown shadow-budget arm')
    if (manifest['schema'] != 1 or manifest['base_sha256'] != BASE_SHA256 or
        manifest['base_version'] != 'v1.3b' or manifest['switch'] != SWITCH or
        manifest['experiment'] != 'photon-existing-pass-shadow-tail-budget' or
        tuple(e['path'] for e in manifest['members']) != MEMBERS or
        manifest['addition']['path'] != HELPER or
        manifest['addition']['source_path'] != 'shaderpacks/photon-shadow-budget/' + HELPER):
        raise ValueError('Unexpected shadow-budget source scope')
    if sha256(files['LICENSE']) != manifest['license_sha256']:
        raise ValueError('Pinned upstream license changed')
    result = files.copy()
    for entry in manifest['members']:
        name = entry['path']
        if sha256(files[name]) != entry['source_sha256']:
            raise ValueError('Pinned upstream member changed: ' + name)
        source = files[name].decode()
        for replacement in entry['replacements']:
            before = replacement['before']
            count = replacement.get('occurrences', 1)
            if not before or source.count(before) != count:
                raise ValueError('Missing or ambiguous anchor: ' + name)
            if count == 1:
                source = source.replace(before, replacement['after'], 1)
            elif count == 2 and len(replacement['after_occurrences']) == 2:
                pieces = source.split(before)
                first, second = replacement['after_occurrences']
                source = pieces[0] + first + pieces[1] + second + pieces[2]
            else:
                raise ValueError('Unreviewed occurrence count')
        payload = source.encode()
        if sha256(payload) != entry['patched_sha256']:
            raise ValueError('Patched member changed: ' + name)
        if arm == 'enabled' and name == SETTINGS:
            declaration = f'//#define {SWITCH}\n'.encode()
            if payload.count(declaration) != 1:
                raise ValueError('Private switch must default off once')
            payload = payload.replace(declaration, f'#define {SWITCH}\n'.encode(), 1)
            if sha256(payload) != entry['enabled_sha256']:
                raise ValueError('Enabled settings changed')
        result[name] = files[name] if arm == 'original' else payload
    helper = (root / manifest['addition']['source_path']).read_bytes()
    if HELPER in files or sha256(helper) != manifest['addition']['sha256']:
        raise ValueError('New helper missing, conflicting or changed')
    if arm != 'original':
        result[HELPER] = helper
    return result


def build(output=None, base=None, arm='disabled', root=ROOT):
    base = (base or root / BASE).resolve()
    output = (output or root / 'build' / f'shadow-budget-{arm}.zip').resolve()
    if (not output.is_relative_to((root / 'build').resolve()) or
        not output.name.startswith('shadow-budget-') or output.suffix != '.zip' or output == base):
        raise ValueError('Output must be an ignored build/shadow-budget-*.zip')
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError('Pinned original archive changed')
    manifest_bytes = (root / MANIFEST).read_bytes()
    manifest = json.loads(manifest_bytes)
    original = archive_members(base)
    if METADATA in original:
        raise ValueError('Original unexpectedly contains metadata')
    files = patch_members(original, manifest, arm, root)
    modified = sorted(n for n in original if original[n] != files[n])
    if modified != ([] if arm == 'original' else sorted(MEMBERS)):
        raise ValueError('Unexpected modification scope')
    metadata = {
        'experiment': manifest['experiment'], 'arm': arm,
        'base_sha256': BASE_SHA256, 'manifest_sha256': sha256(manifest_bytes),
        'switch': SWITCH, 'modified_upstream_files': modified,
        'added_shader_files': [] if arm == 'original' else [HELPER],
        'member_sha256': {n: sha256(files[n]) for n in (*MEMBERS, 'LICENSE')},
        'helper_sha256': manifest['addition']['sha256'] if arm != 'original' else None,
        'activation': 'private switch AND overworld deferred4 AND core version 2/global enable 1',
        'distance_band_blocks': [64, 112], 'footprint_radius_output_pixels': [1, 2],
        'fallback': 'private OFF/non-target programs token-original; private ON global OFF retains counts/distribution with explicit level-0 PCF comparisons',
        'quality_policy': 'Keep original first four probes and early exits, radius/bias/fade/color/SSS/sharpening; weighted stratified tail only. VPS/color/pixelated/SSS protected.',
        'mod_linkage': 'existing core version/global only; new effect controls NOT consumed',
        'runtime_compilation': 'unverified', 'quality': 'unaccepted', 'gpu_benefit': 'unmeasured',
        'attribution': 'Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained',
    }
    files[METADATA] = (json.dumps(metadata, indent=2, sort_keys=True) + '\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix='.zip', delete=False) as temporary:
        temporary_path = Path(temporary.name)
    try:
        with zipfile.ZipFile(temporary_path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, data in sorted(files.items()):
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data, compresslevel=9)
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
