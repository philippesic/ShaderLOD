#!/usr/bin/env python3
"""Assemble the pinned default-off spatial irradiance prototype, not a measured gain."""
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
MANIFEST = Path('patches/photon-experiments/lighting-rate-probe.json')
OVERLAY = Path('shaderpacks/photon-lighting-rate')
SWITCH = 'SHADERLOD_LIGHTING_RATE_PROBE'
EXPERIMENT = 'photon-distant-lighting-rate-prototype'
METADATA = 'shaderlod-lighting-rate-probe.json'
ARMS = ('original', 'disabled', 'enabled')
MEMBERS = ('shaders/settings.glsl', 'shaders/shaders.properties', 'shaders/include/buffers.glsl',
           'shaders/include/lighting/shadows/pcss.glsl', 'shaders/program/d4_deferred_shading.fsh',
           'shaders/world0/deferred4.fsh', 'shaders/world1/deferred4.fsh', 'shaders/world-1/deferred4.fsh',
           'shaders/include/lighting/shadows/ssrt.glsl')
ANCHORS = (1, 1, 1, 4, 7, 1, 1, 1, 1)
CORE_FIELDS = ('shaderlodBridgeVersion', 'shaderlodEnabled', 'shaderlodDebug',
               'shaderlodNear', 'shaderlodFar', 'shaderlodTransition')
EFFECT_FIELDS = ('shaderlodEffectsVersion', 'shaderlodOpaqueLightingMode', 'shaderlodOpaqueLightingNear',
                 'shaderlodOpaqueLightingFar', 'shaderlodOpaqueLightingTransition',
                 'shaderlodOpaqueLightingMediumWorkRatio', 'shaderlodOpaqueLightingFarWorkRatio')

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def patch_members(files: dict[str, bytes], manifest: dict, arm: str) -> dict[str, bytes]:
    if arm not in ARMS:
        raise ValueError('Unknown prototype arm')
    if (manifest.get('schema') != 1 or manifest.get('experiment') != EXPERIMENT
            or manifest.get('base_sha256') != BASE_SHA256 or manifest.get('base_version') != 'v1.3b'
            or manifest.get('switch') != SWITCH or manifest.get('target') != 2
            or manifest.get('viewport_scale') != 0.5 or manifest.get('rates') != [1, 4]):
        raise ValueError('Unexpected lighting-rate contract')
    if tuple(entry['path'] for entry in manifest['members']) != MEMBERS:
        raise ValueError('Unexpected upstream source scope/order')
    if 'LICENSE' not in files or sha256(files['LICENSE']) != manifest['license_sha256']:
        raise ValueError('Pinned Photon license mismatch')
    result = files.copy()
    for entry, count in zip(manifest['members'], ANCHORS):
        name = entry['path']
        if name not in files or sha256(files[name]) != entry['source_sha256']:
            raise ValueError('Pinned member changed: ' + name)
        if len(entry['replacements']) != count:
            raise ValueError('Reviewed anchor scope changed: ' + name)
        source = files[name].decode()
        for replacement in entry['replacements']:
            before = replacement['before']
            if not before or source.count(before) != 1:
                raise ValueError('Anchor must be unique: ' + name)
            source = source.replace(before, replacement['after'], 1)
        disabled = source.encode()
        if sha256(disabled) != entry['patched_sha256']:
            raise ValueError('Private source fingerprint changed: ' + name)
        if name == 'shaders/settings.glsl':
            declaration = f'//#define {SWITCH}\n'
            if source.count(declaration) != 1:
                raise ValueError('Option must default off')
            enabled = source.replace(declaration, f'#define {SWITCH}\n', 1).encode()
            if sha256(enabled) != entry['enabled_sha256']:
                raise ValueError('Enabled settings fingerprint changed')
        else:
            enabled = disabled
        result[name] = files[name] if arm == 'original' else enabled if arm == 'enabled' else disabled
    return result

def archive_members(base: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(base) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(names) != len(set(names)):
            raise ValueError('Duplicate upstream archive member')
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or '..' in path.parts or '\\' in name:
                raise ValueError('Unsafe upstream member')
        return {entry.filename: archive.read(entry) for entry in entries if not entry.is_dir()}

def overlays(root: Path, manifest: dict, original: dict) -> dict[str, bytes]:
    found = {path.relative_to(root / OVERLAY).as_posix(): path.read_bytes()
             for path in (root / OVERLAY).rglob('*') if path.is_file()}
    if set(found) != set(manifest['overlays']):
        raise ValueError('Overlay source scope changed')
    for name, data in found.items():
        if name in original or sha256(data) != manifest['overlays'][name]:
            raise ValueError('Overlay collision or fingerprint changed: ' + name)
    return found

def build(output: Path | None = None, base: Path | None = None, arm='disabled', root=ROOT) -> dict:
    if arm not in ARMS:
        raise ValueError('Unknown prototype arm')
    base = (base or root / BASE).resolve()
    output = (output or root / 'build' / f'Photon-LightingRatePrototype-{arm}.zip').resolve()
    if not output.is_relative_to((root / 'build').resolve()) or output.suffix.lower() != '.zip' or output == base:
        raise ValueError('Output must be a separate ZIP under ignored build/')
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError('Pinned original Photon archive changed')
    manifest_bytes = (root / MANIFEST).read_bytes()
    manifest = json.loads(manifest_bytes)
    original = archive_members(base)
    files = patch_members(original, manifest, arm)
    overlay = overlays(root, manifest, original)
    if arm != 'original':
        files.update(overlay)
    modified = sorted(name for name in original if files[name] != original[name])
    if modified != ([] if arm == 'original' else sorted(MEMBERS)):
        raise ValueError('Unexpected actual upstream diff')
    metadata = dict(experiment=EXPERIMENT, arm=arm, base_version='v1.3b', base_sha256=BASE_SHA256,
        manifest_sha256=sha256(manifest_bytes), switch=SWITCH, switch_enabled=arm == 'enabled',
        modified_upstream_files=modified, member_sha256={name: sha256(files[name]) for name in MEMBERS},
        overlay_sha256={} if arm == 'original' else manifest['overlays'],
        license_sha256=sha256(files['LICENSE']), shader_target=2, viewport_scale=0.5,
        supported_rates=[1, 4], core_fields=list(CORE_FIELDS), effect_fields=list(EFFECT_FIELDS),
        effects_version=2, activation='core2/global1/effects2; INHERIT private core bands and 1/.25, FULL original, ADAPTIVE all six Opaque fields',
        approximation='RGB irradiance coefficient only; current albedo/specular functions; shared representative AO*shadow highlight visibility in alpha',
        fallback='invalid/unsupported fields, ratios, geometry, material or texture data execute original per-pixel lighting; unsupported pack profiles retain original graph',
        profile_limits=manifest['graph_condition'], shared_surface_helper=manifest['shared_surface_helper'],
        temporal='current-frame static2x2 phase0; no history; original shadow/SSRT seeds use representative pixel',
        transition='full original plus cache in centered band; K1 only at full weight; intermediate ratio requests full fallback',
        disabled_baseline='default option off restores original stage4 shading; stage5 disabled, original buffer2 format; wrapper loads settings before original body',
        runtime_compilation='unverified', quality='unaccepted', gpu_benefit='unmeasured',
        attribution='Photon v1.3b by Benjamin Stott/SixthSurge; original LICENSE preserved; copied common helper retains MIT notice')
    files[METADATA] = (json.dumps(metadata, indent=2, sort_keys=True) + '\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, suffix='.zip', delete=False) as temporary:
        temp = Path(temporary.name)
    try:
        with zipfile.ZipFile(temp, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, data in sorted(files.items()):
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data, compresslevel=9)
        temp.replace(output)
    finally:
        temp.unlink(missing_ok=True)
    return metadata

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--arm', choices=ARMS, default='disabled')
    parser.add_argument('--base', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(build(args.output, args.base, args.arm), indent=2, sort_keys=True))
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        parser.exit(1, f'Cannot assemble lighting-rate prototype: {error}\n')
if __name__ == '__main__':
    main()
