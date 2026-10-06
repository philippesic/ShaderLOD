#!/usr/bin/env python3
"""Assemble the pinned, default-off shared-work volumetric reference consumer."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import tempfile
import zipfile
from build_photon_density_detail_probe import archive_members, sha256

ROOT = Path(__file__).resolve().parents[1]
BASE = Path('dev/upstream/photon_v1.3b.zip')
BASE_SHA256 = '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
MANIFEST = Path('patches/photon-experiments/volumetric-lod.json')
EXPERIMENT = 'photon-primary-volumetric-integration-lod'
SWITCH = 'SHADERLOD_VOLUMETRIC_INTEGRATION_LOD'
CUMULUS = 'shaders/include/sky/clouds/cumulus.glsl'
SETTINGS = 'shaders/settings.glsl'
MEMBERS = (CUMULUS, SETTINGS)
ADDITIONS = (
    ('shaders/include/shaderlod/volume_budget.glsl', 'shaderpacks/common/shaders/include/shaderlod/volume_budget.glsl'),
    ('shaders/include/shaderlod/photon_volume_adapter.glsl', 'shaderpacks/photon-volumetric-lod/shaders/include/shaderlod/photon_volume_adapter.glsl'),
    ('shaders/include/shaderlod/effect_policy.glsl', 'shaderpacks/common/shaders/include/shaderlod/effect_policy.glsl'),
    ('shaders/include/shaderlod/work_policy.glsl', 'shaderpacks/common/shaders/include/shaderlod/work_policy.glsl'))
REFERENCE_MEMBERS = ('LICENSE', 'shaders/world0/deferred1.fsh', 'shaders/program/d1_clouds.fsh',
    'shaders/include/sky/clouds.glsl', *MEMBERS, *(entry[0] for entry in ADDITIONS))
REQUIRED_OPTIONS = {SWITCH: ['true'], 'CLOUDS_CUMULUS': ['true'], 'BLOCKY_CLOUDS': ['false']}
ARMS = ('original', 'disabled', 'enabled')
METADATA = 'shaderlod-volumetric-lod.json'


def patch_members(files: dict[str, bytes], manifest: dict, arm: str, root: Path = ROOT) -> dict[str, bytes]:
    if arm not in ARMS:
        raise ValueError('Unknown arm')
    if (manifest.get('schema') != 1 or manifest.get('experiment') != EXPERIMENT or
        manifest.get('base_version') != 'v1.3b' or manifest.get('base_sha256') != BASE_SHA256 or
        manifest.get('switch') != SWITCH or manifest.get('core_version') != 2 or
        manifest.get('effect_versions') != [1, 2] or manifest.get('retained_minimum') != 8 or
        manifest.get('inherit_retained_ratios') != [1.0, 1.0] or
        manifest.get('capability_schema') != 1 or
        manifest.get('capability_operation') != 'shaderlod:volume_integration' or
        manifest.get('required_capability_fields') != ['shaderlodCapabilitiesVersion', 'shaderlodVolumeIntegrationAvailable'] or
        manifest.get('required_effective_options') != REQUIRED_OPTIONS or
        tuple(manifest.get('reference_members', ())) != REFERENCE_MEMBERS or
        manifest.get('sampler_constraints', {}).get('status') != 'controlled_profile_only'):
        raise ValueError('Unreviewed volumetric manifest contract')
    if tuple(e['path'] for e in manifest['members']) != MEMBERS:
        raise ValueError('Unexpected patched member scope')
    if tuple((e['path'], e['source_path']) for e in manifest['additions']) != ADDITIONS:
        raise ValueError('Unexpected helper scope')
    if 'LICENSE' not in files or sha256(files['LICENSE']) != manifest['license_sha256']:
        raise ValueError('Photon license missing or changed')
    result = files.copy()
    for entry in manifest['members']:
        name = entry['path']
        if name not in files or sha256(files[name]) != entry['source_sha256']:
            raise ValueError(f'Pinned source changed: {name}')
        if len(entry['replacements']) != (2 if name == CUMULUS else 1):
            raise ValueError('Unexpected anchor count')
        source = files[name].decode()
        for replacement in entry['replacements']:
            before, after = replacement['before'], replacement['after']
            if not before or source.count(before) != 1:
                raise ValueError(f'Nonunique reviewed anchor: {name}')
            source = source.replace(before, after, 1)
        disabled = source.encode()
        if sha256(disabled) != entry['patched_sha256']:
            raise ValueError(f'Patched fingerprint changed: {name}')
        enabled = disabled
        if name == SETTINGS:
            gate = f'//#define {SWITCH}\n'.encode()
            if disabled.count(gate) != 1:
                raise ValueError('Private option must default off exactly once')
            enabled = disabled.replace(gate, f'#define {SWITCH}\n'.encode(), 1)
            if sha256(enabled) != entry['enabled_sha256']:
                raise ValueError('Enabled option fingerprint changed')
        result[name] = files[name] if arm == 'original' else enabled if arm == 'enabled' else disabled
    for entry in manifest['additions']:
        name = entry['path']
        source = (root / entry['source_path']).read_bytes()
        if name in files or sha256(source) != entry['sha256']:
            raise ValueError(f'Helper duplicate or fingerprint changed: {name}')
        if arm != 'original':
            result[name] = source
    return result


def build(output: Path | None = None, base: Path | None = None, arm: str = 'disabled', root: Path = ROOT) -> dict:
    if arm not in ARMS:
        raise ValueError('Unknown arm')
    output = (output or root / 'build' / f'Photon-VolumetricLOD-{arm}.zip').resolve()
    base = (base or root / BASE).resolve()
    if output == base or output.suffix.lower() != '.zip' or not output.is_relative_to((root / 'build').resolve()):
        raise ValueError('Output must be a ZIP under ignored build/, distinct from original')
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError('Original archive SHA256 mismatch')
    manifest_bytes = (root / MANIFEST).read_bytes()
    manifest = json.loads(manifest_bytes)
    original = archive_members(base)
    files = patch_members(original, manifest, arm, root)
    modified = sorted(name for name in original if original[name] != files[name])
    if modified != ([] if arm == 'original' else sorted(MEMBERS)):
        raise ValueError('Changed upstream member scope differs')
    added = sorted(set(files) - set(original))
    if added != ([] if arm == 'original' else sorted(e[0] for e in ADDITIONS)) or METADATA in original:
        raise ValueError('Added member scope differs')
    metadata = {'experiment': EXPERIMENT, 'arm': arm, 'base_version': 'v1.3b',
        'base_sha256': BASE_SHA256, 'manifest_sha256': sha256(manifest_bytes),
        'modified_upstream_files': modified, 'added_files': added,
        'unchanged_upstream_member_count': len(original) - len(modified),
        'member_sha256': {name: sha256(files[name]) for name in (*MEMBERS, *added)},
        'license_sha256': sha256(files['LICENSE']), 'switch': SWITCH, 'option_default': arm == 'enabled',
        'core_version': 2, 'effect_versions': [1, 2], 'capability_schema': 1,
        'capability_operation': 'shaderlod:volume_integration', 'retained_minimum': 8,
        'inherit_retained_ratios': [1.0, 1.0],
        'public_flags': {SWITCH: ['false', 'true']},
        'required_effective_options': ({} if arm == 'original' else REQUIRED_OPTIONS),
        'reference_member_sha256': ({} if arm == 'original' else
            {name: sha256(files[name]) for name in REFERENCE_MEMBERS}),
        'linked_consumption': 'unverified',
        'source_referenced_fields': ([] if arm == 'original' else ['shaderlodCapabilitiesVersion', 'shaderlodVolumeIntegrationAvailable', 'shaderlodBridgeVersion', 'shaderlodEnabled', 'shaderlodNear', 'shaderlodFar',
            'shaderlodTransition', 'shaderlodEffectsVersion', 'shaderlodVolumetricsMode',
            'shaderlodVolumetricsNear', 'shaderlodVolumetricsFar', 'shaderlodVolumetricsTransition',
            'shaderlodVolumetricsMediumWorkRatio', 'shaderlodVolumetricsFarWorkRatio']),
        'program_scope': 'PROGRAM_DEFERRED1 cumulus primary integration only; no atmosphere transmittance LUT',
        'disabled_baseline': 'private off and nonselected stage preprocess to literal upstream; global off/absent/invalid controls call original function',
        'units': manifest['units'], 'integration': manifest['integration'],
        'quality_status': manifest['quality_status'], 'sampling_status': 'Explicit LOD0 active density path; frozen-source sampler qualification only; new driver parity pending',
        'runtime_compilation': 'unverified', 'gpu_benefit': 'unmeasured',
        'attribution': 'Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained'}
    files[METADATA] = (json.dumps(metadata, indent=2, sort_keys=True) + '\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix='.zip', delete=False) as temporary:
        tmp = Path(temporary.name)
    try:
        with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, data in sorted(files.items()):
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data, compresslevel=9)
        tmp.replace(output)
    finally:
        tmp.unlink(missing_ok=True)
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--arm', choices=ARMS, default='disabled')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--base', type=Path)
    args = parser.parse_args()
    try:
        result = build(args.output, args.base, args.arm)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as exc:
        parser.exit(1, f'Cannot assemble Photon volumetric integration: {exc}\n')
    print(json.dumps(result, indent=2, sort_keys=True))

if __name__ == '__main__':
    main()
