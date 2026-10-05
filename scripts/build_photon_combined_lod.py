#!/usr/bin/env python3
"""Assemble exactly three pinned Photon LOD tracks from the original archive."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile

import build_photon_reflection_lod as reflection
import build_photon_procedural_lod as procedural
import build_photon_volumetric_lod as volumetric

ROOT = Path(__file__).resolve().parents[1]
BASE = reflection.BASE
BASE_SHA256 = reflection.BASE_SHA256
MANIFEST = Path('patches/photon-experiments/combined-lod.json')
EXPERIMENT = 'photon-explicit-three-track-combined-lod'
TRACKS = ('reflection', 'procedural', 'volumetric')
SWITCHES = (reflection.SWITCH, procedural.SWITCH, volumetric.SWITCH)
SETTINGS = reflection.SETTINGS
METADATA = 'shaderlod-combined-lod.json'
CORE_HEADER = 'shaders/include/shaderlod/combined_core.glsl'
COMMON_CORE = 'uniform int shaderlodBridgeVersion;\nuniform int shaderlodEnabled;\n'
POLICY_CORE = 'uniform float shaderlodNear;\nuniform float shaderlodFar;\nuniform float shaderlodTransition;\n'
# Independent guards: a water-only consumer may arrive before a policy consumer.
# #if !defined deliberately avoids Iris boolean-option discovery for internal guards.
CORE_SOURCE = ('''// ShaderLOD combined adapter: shared core declarations, protocol version 2.
#if !defined SHADERLOD_COMBINED_BASIC_CORE_DECLARED
#define SHADERLOD_COMBINED_BASIC_CORE_DECLARED
''' + COMMON_CORE + '''#endif
#if defined SHADERLOD_COMBINED_NEEDS_POLICY && !defined SHADERLOD_COMBINED_POLICY_CORE_DECLARED
#define SHADERLOD_COMBINED_POLICY_CORE_DECLARED
''' + POLICY_CORE + '''#endif
''').encode()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def inventory(files: dict[str, bytes]) -> str:
    return sha256(json.dumps({name: sha256(data) for name, data in sorted(files.items())},
                            sort_keys=True, separators=(',', ':')).encode())


def controls(enabled=()) -> tuple[str, ...]:
    values = tuple(enabled)
    if len(values) != len(set(values)) or any(name not in TRACKS for name in values):
        raise ValueError('Enable only unique, explicit reflection/procedural/volumetric tracks')
    return tuple(name for name in TRACKS if name in values)


def load_tracks(manifest: dict, root: Path) -> tuple[dict, dict, dict]:
    if (manifest.get('schema') != 1 or manifest.get('experiment') != EXPERIMENT
            or manifest.get('base_version') != 'v1.3b' or manifest.get('base_sha256') != BASE_SHA256
            or manifest.get('settings_anchor') != '#define SETTINGS_INCLUDED\n'
            or manifest.get('core_version') != 2 or manifest.get('effect_versions') != [1, 2]
            or tuple(e['name'] for e in manifest['tracks']) != TRACKS):
        raise ValueError('Unknown combined LOD contract')
    result = []
    for name, switch, entry in zip(TRACKS, SWITCHES, manifest['tracks']):
        if (entry['manifest_path'] != f'patches/photon-experiments/{name}-lod.json'
                or entry['builder_path'] != f'scripts/build_photon_{name}_lod.py'
                or entry['switch'] != switch):
            raise ValueError('Unknown combined track membership')
        data = (root / entry['manifest_path']).read_bytes()
        if (sha256(data) != entry['manifest_sha256']
                or sha256((root / entry['builder_path']).read_bytes()) != entry['builder_sha256']):
            raise ValueError(f'Frozen standalone track changed: {name}')
        result.append(json.loads(data))
    return tuple(result)


def patch_members(original: dict[str, bytes], manifest: dict, enabled=(),
                  root: Path = ROOT, original_arm: bool = False) -> dict[str, bytes]:
    selected = controls(enabled)
    if original_arm and selected:
        raise ValueError('Original arm cannot enable features')
    rm, pm, vm = load_tracks(manifest, root)
    if (inventory(original) != manifest['original_inventory_sha256']
            or sha256(original['LICENSE']) != manifest['license_sha256']):
        raise ValueError('Combined assembly requires the complete pinned original inventory')
    # Qualify each standalone independently against original, never another derivative.
    r = reflection.patch_members(original, rm, 'disabled')
    p = procedural.patch_members(original, pm, 'disabled', root)
    v = volumetric.patch_members(original, vm, 'disabled', root)
    if original_arm:
        return original.copy()
    result = original.copy()
    # These are the only nonoverlapping upstream changes. Settings is merged below.
    for source, name in ((r, reflection.SPECULAR), (p, procedural.PROGRAM), (v, volumetric.CUMULUS)):
        result[name] = source[name]
    anchor = manifest['settings_anchor']
    settings = original[SETTINGS].decode()
    if settings.count(anchor) != 1:
        raise ValueError('Combined settings anchor must occur exactly once')
    declarations = []
    for name, m in zip(TRACKS, (rm, pm, vm)):
        declaration = m['option_declaration']
        if declaration.count(f'//#define {m["switch"]}\n') != 1 or declaration in settings:
            raise ValueError('Unknown or colliding private option declaration')
        if name in selected:
            declaration = declaration.replace('//#define ', '#define ', 1)
        declarations.append(declaration)
    result[SETTINGS] = settings.replace(anchor, anchor + ''.join(declarations), 1).encode()
    additions = [rm['added_members'][0],
                 *({'path': e['path'], 'source_path': str(procedural.HELPER_ROOT / e['path']),
                    'sha256': e['sha256']} for e in pm['helpers']), *vm['additions']]
    for entry in additions:
        name = entry['path']
        data = (root / entry['source_path']).read_bytes()
        if sha256(data) != entry['sha256'] or name in result:
            raise ValueError(f'Combined helper changed or collides: {name}')
        # Exact reviewed helper declarations only; no matching arbitrary GLSL.
        policy = name in (rm['added_members'][0]['path'], vm['additions'][1]['path'])
        if policy or name == 'shaders/include/shaderlod/procedural_water.glsl':
            before = COMMON_CORE + (POLICY_CORE if policy else '')
            source = data.decode()
            if source.count(before) != 1:
                raise ValueError(f'Shared core declaration block changed: {name}')
            after = ('#define SHADERLOD_COMBINED_NEEDS_POLICY\n' if policy else '')
            after += '#include "/include/shaderlod/combined_core.glsl"\n'
            if policy:
                after += '#undef SHADERLOD_COMBINED_NEEDS_POLICY\n'
            data = source.replace(before, after, 1).encode()
        result[name] = data
    if CORE_HEADER in result:
        raise ValueError('Combined core header collides')
    result[CORE_HEADER] = CORE_SOURCE
    expected = manifest.get('disabled_member_sha256')
    if expected is None:
        raise ValueError('Missing reviewed combined output pins')
    for name, digest in expected.items():
        if name == SETTINGS:
            disabled = result[name]
            for switch in SWITCHES:
                disabled = disabled.replace(f'\n#define {switch}\n'.encode(),
                                            f'\n//#define {switch}\n'.encode(), 1)
            digest_actual = sha256(disabled)
        else:
            digest_actual = sha256(result[name])
        if digest_actual != digest:
            raise ValueError(f'Reviewed combined output changed: {name}')
    changed = {name for name in original if original[name] != result[name]}
    added = set(result) - set(original)
    if (changed != {SETTINGS, reflection.SPECULAR, procedural.PROGRAM, volumetric.CUMULUS}
            or added != {e['path'] for e in additions} | {CORE_HEADER}
            or set(expected) != changed | added):
        raise ValueError('Combined changed/added scope mismatch')
    return result


def build(output: Path | None = None, base: Path | None = None, enabled=(),
          root: Path = ROOT, original_arm: bool = False) -> dict:
    selected = controls(enabled)
    base = (base or root / BASE).resolve()
    tag = 'original' if original_arm else '-'.join(selected) or 'disabled'
    output = (output or root / 'build' / f'Photon-CombinedLOD-{tag}.zip').resolve()
    if (output == base or output.suffix.lower() != '.zip'
            or not output.is_relative_to((root / 'build').resolve())):
        raise ValueError('Combined output must be a ZIP in ignored build/, distinct from original')
    if sha256(base.read_bytes()) != BASE_SHA256:
        raise ValueError('Combined base must be the exact original Photon ZIP')
    manifest_bytes = (root / MANIFEST).read_bytes()
    manifest = json.loads(manifest_bytes)
    original = reflection.archive_members(base)
    files = patch_members(original, manifest, selected, root, original_arm)
    if METADATA in files:
        raise ValueError('Combined metadata collides')
    changed = sorted(name for name in original if original[name] != files[name])
    added = sorted(set(files) - set(original))
    metadata = dict(experiment=EXPERIMENT, base_version='v1.3b', base_sha256=BASE_SHA256,
                    manifest_sha256=sha256(manifest_bytes), enabled_tracks=list(selected),
                    original_arm=original_arm, switches=dict(zip(TRACKS, SWITCHES)),
                    modified_upstream_files=changed, added_helpers=added,
                    member_sha256={name: sha256(files[name]) for name in changed + added},
                    license_sha256=sha256(files['LICENSE']),
                    unchanged_upstream_member_count=len(original)-len(changed),
                    track_manifest_sha256={e['name']: e['manifest_sha256'] for e in manifest['tracks']},
                    core_version=2, effect_versions=[1, 2],
                    fallback='Each private option off selects literal upstream program; absent/invalid bridge uses standalone original fallback',
                    quality_status='unverified combined quality and interaction',
                    runtime_compilation='unverified', gpu_benefit='unmeasured; individual gains cannot be summed',
                    attribution='Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained')
    files[METADATA] = (json.dumps(metadata, sort_keys=True, indent=2)+'\n').encode()
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
    parser.add_argument('--enable', choices=TRACKS, action='append', default=[])
    parser.add_argument('--original', action='store_true')
    parser.add_argument('--base', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        result = build(args.output, args.base, args.enable, original_arm=args.original)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f'Cannot assemble explicit combined Photon LOD: {error}\n')
    print(json.dumps(result, sort_keys=True, indent=2))


if __name__ == '__main__':
    main()
