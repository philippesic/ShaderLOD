#!/usr/bin/env python3
"""Prepare a private pinned MakeUp End-cloud candidate and unapplied product overlay."""
from __future__ import annotations
import argparse
import copy
import difflib
import json
from pathlib import Path
import shutil
import tempfile

from build_supported_pack_adapters import (
    ROOT, CATALOG, RESOURCE_ROOT, apply, build, read_archive, sha256,
    shader_source_digest, sync_resources,
)

ADAPTER = 'makeup-ultra-fast-shaders'
RECIPE = f'patches/adapters/{ADAPTER}.json'
END_MEMBER = 'shaders/lib/volumetric_clouds_end.glsl'
ENTRY_MEMBER = 'shaders/world1/deferred.fsh'
END_SHA = 'c238e158ebb61c32fa7e52553b5381abaf8e7c7603b9e9ff35417959eac50835'
ENTRY_SHA = '11e66dbbccf974c4445a31dbac79fc9aea717dd5654664a5f8f12c5d0a9de21e'
FUNCTION = 'vec3 get_end_cloud(vec3 eyeDirection, vec3 blockColor, float bright, float dither, vec3 base_pos, int samples) {'
STEP = '        vec3 increment = (intersection_pos_sup - intersection_pos) / samples;'
GUARD = ('SHADERLOD_REFINEMENT_ADAPTER == 1 && defined DEFERRED_SHADER && defined THE_END '
         '&& !defined NETHER && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY')
COUNT_INSERT = ('        // Same native End slab and opacity weights; change count before stride.\n'
                f'        #if {GUARD}\n'
                '        samples = shaderlod_makeup_volume_budget(samples);\n'
                '        #endif\n')


def candidate_manifest(current):
    candidate = copy.deepcopy(current)
    if candidate['id'] != ADAPTER or candidate['version'] != '9.5g':
        raise ValueError('Only pinned MakeUp 9.5g is supported')
    if END_MEMBER in candidate['source_members'] or ENTRY_MEMBER in candidate['source_members']:
        raise ValueError('End consumer already pinned; review current recipe before preparing')
    candidate['source_members'].update({END_MEMBER: END_SHA, ENTRY_MEMBER: ENTRY_SHA})
    candidate['patches'].extend([
        {'path': END_MEMBER, 'before': FUNCTION,
         'after': '#include "/lib/shaderlod/adapter.glsl"\n\n' + FUNCTION},
        {'path': END_MEMBER, 'before': STEP, 'after': COUNT_INSERT + STEP},
    ])
    candidate.setdefault('ported_consumers', {})['end_clouds'] = {
        'mechanism': 'Existing uniform_sample_budget; native End slab and length-weighted opacity',
        'scope': 'THE_END DEFERRED_SHADER, no Nether/unknown/DH/VOXY; other callers unchanged',
        'original_native_samples': 10, 'balanced_samples': 9, 'performance_samples': 8,
        'performance_native_renderer_bypasses_shader_budget': False,
        'route_scope': 'Public Apply while already in The End; inspected native renderer gates require Overworld',
        'dimension_transition_reselection_verified': False,
        'distance_controls': False, 'runtime_verified': False,
        'quality_verified': False, 'performance_verified': False,
    }
    return candidate


def prepare(base: Path, output: Path, root: Path = ROOT):
    base, output, root = Path(base), Path(output), Path(root)
    if output.exists():
        raise ValueError('Output already exists; choose a new directory')
    current_bytes = (root / RECIPE).read_bytes()
    current = json.loads(current_bytes)
    if sha256(base.read_bytes()) != current['release_archive_sha256']:
        raise ValueError('Original archive hash differs from pinned release')
    original = read_archive(base)
    assembled = apply(original, current, root)
    candidate = candidate_manifest(current)
    candidate_bytes = (json.dumps(candidate, indent=2) + '\n').encode()
    changed = apply(original, candidate, root)
    difference = sorted(n for n in set(assembled) | set(changed) if assembled.get(n) != changed.get(n))
    if difference != [END_MEMBER] or set(changed) != set(assembled):
        raise ValueError('Candidate exceeded End-only source scope')
    recovered = changed[END_MEMBER].replace(
        b'#include "/lib/shaderlod/adapter.glsl"\n\n', b'', 1).replace(COUNT_INSERT.encode(), b'', 1)
    if recovered != original[END_MEMBER]:
        raise ValueError('Native End body was not preserved')
    source_inputs = {RECIPE: current_bytes, CATALOG: (root / CATALOG).read_bytes()}
    for n in sorted((root / 'patches/adapters').rglob('*')):
        if n.is_file(): source_inputs[n.relative_to(root).as_posix()] = n.read_bytes()
    for n in sorted((root / (RESOURCE_ROOT + 'patches/adapters')).rglob('*')):
        if n.is_file(): source_inputs[n.relative_to(root).as_posix()] = n.read_bytes()
    # Disposable full metadata root lets existing sync validate other helper consumers.
    with tempfile.TemporaryDirectory(prefix='makeup-end-stage-') as temporary:
        stage = Path(temporary)
        product = stage / 'product'
        for name, data in source_inputs.items():
            target = product / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        (product / RECIPE).write_bytes(candidate_bytes)
        sync = sync_resources(base, ADAPTER, root=product, write=True)
        expected = {RESOURCE_ROOT + RECIPE, CATALOG}
        if set(sync['changed_files']) != expected:
            raise ValueError('Unexpected resource/catalog delta: ' + repr(sync['changed_files']))
        artifact = stage / 'artifact'
        artifact.mkdir()
        metadata = build(base, artifact / 'candidate.zip', product / RECIPE, root=product)
        overlay_names = [RECIPE, RESOURCE_ROOT + RECIPE, CATALOG]
        patch_parts, overlay_hashes = [], {}
        for name in overlay_names:
            before, after = source_inputs[name], (product / name).read_bytes()
            target = artifact / 'overlay' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(after)
            overlay_hashes[name] = {'before': sha256(before), 'after': sha256(after)}
            patch_parts.extend(difflib.unified_diff(before.decode().splitlines(keepends=True),
                after.decode().splitlines(keepends=True), fromfile='a/' + name, tofile='b/' + name))
        (artifact / 'integration.patch').write_text(''.join(patch_parts))
        (artifact / 'candidate-manifest.json').write_bytes(candidate_bytes)
        receipt = {
            'schema': 1, 'source_only': True, 'original_archive_sha256': sha256(base.read_bytes()),
            'original_source_digest': shader_source_digest(original),
            'current_adapter_digest': shader_source_digest(assembled),
            'candidate_digest': shader_source_digest(changed),
            'candidate_zip_sha256': sha256((artifact / 'candidate.zip').read_bytes()),
            'changed_from_current_adapter': difference, 'added_shader_members': [],
            'native_end_body_recovered': True, 'end_member_sha256': END_SHA,
            'end_entry_sha256': ENTRY_SHA, 'guard': GUARD, 'overlay': overlay_hashes,
            'sync': sync, 'validation': metadata['validation'],
            'future_consumer': 'First establish native End rendering on a suitable tuple; NeoForge26.2 binding compatibility is Overworld-only. Then integrate reviewed overlay and public Balanced/Performance Apply while in End; actual controls/images; Quality/Off/Cancel recovery. Dimension-transition reselection unverified. Current production catalog does not recognize candidate.',
        }
        (artifact / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
        # Refuse a concurrent maintained-input edit rather than emit a stale integration delta.
        if any((root / name).read_bytes() != data for name, data in source_inputs.items()):
            raise ValueError('Maintained input changed during preparation')
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(artifact, output)  # exist_ok=False: never replace an existing output.
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True, help='New artifact directory; never overwritten')
    args = parser.parse_args()
    print(json.dumps(prepare(args.base, args.output), indent=2))


if __name__ == '__main__':
    main()
