#!/usr/bin/env python3
"""Prepare an isolated pinned Bliss primary-cloud count port and unapplied overlay."""
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

ADAPTER = 'bliss-shader'
RECIPE = f'patches/adapters/{ADAPTER}.json'
VOLUME = 'shaders/lib/volumetricClouds.glsl'
DIMENSION = 'shaders/dimensions/composite2.fsh'
ENTRY = 'shaders/world0/composite3.fsh'
VOLUME_SHA = '3750109b2f73a19c825ff886396edb6327f3da07ae389bc1358f53b937b7229a'
DIMENSION_SHA = 'de7eb25909fb3296d59816d596ec27917d6bc353079ab977d25d2f367c6eafb3'
ENTRY_SHA = 'fdf2e8be1c8b07c2f459298d690498a6f7e51f46075d49b7d07ffe6bca6dbb20'
MARKER = 'SHADERLOD_BLISS_PRIMARY_CLOUD_BUDGET'
DIM_INCLUDE = '\t#include "/lib/volumetricClouds.glsl"\r\n'
DIM_REPLACE = (f'\t#define {MARKER} 1\r\n' + DIM_INCLUDE + f'\t#undef {MARKER}\r\n')
VOL_START = '#ifdef HQ_CLOUDS\r\n'
VOL_REPLACE = (f'#if defined({MARKER}) && {MARKER} == 1 && defined(OVERWORLD_SHADER)\r\n'
               '#include "/lib/shaderlod/adapter.glsl"\r\n#endif\r\n' + VOL_START)
NATIVE_COUNT = '\tmaxIT_clouds = int(clamp(maxIT_clouds / sqrt(exp2(viewPos.y)),0.0, maxIT));\r\n'
GUARD = (f'defined({MARKER}) && {MARKER} == 1 && defined(OVERWORLD_SHADER) '
         '&& defined(VOLUMETRIC_CLOUDS) && SHADERLOD_EFFECT_ADAPTER == 1 '
         '&& !defined(DISTANT_HORIZONS) && !defined(VOXY)')
COUNT_INSERT = (f'#if {GUARD}\r\n'
                '\tmaxIT_clouds = shaderlod_effect_budget(maxIT_clouds, 4);\r\n'
                '#endif\r\n')


def candidate_manifest(current):
    candidate = copy.deepcopy(current)
    if candidate['id'] != ADAPTER or candidate['version'] != '2.1.2':
        raise ValueError('Only pinned Bliss 2.1.2 is supported')
    if VOLUME in candidate['source_members'] or any(p['path'] == VOLUME for p in candidate['patches']):
        raise ValueError('Cloud consumer already pinned; review current recipe before preparing')
    if ENTRY in candidate['source_members']:
        raise ValueError('World entry already pinned; review current recipe before preparing')
    if candidate['source_members'].get(DIMENSION) != DIMENSION_SHA:
        raise ValueError('Selected dimension preimage changed')
    candidate['source_members'].update({VOLUME: VOLUME_SHA, ENTRY: ENTRY_SHA})
    candidate['patches'].extend([
        {'path': DIMENSION, 'before': DIM_INCLUDE, 'after': DIM_REPLACE},
        {'path': VOLUME, 'before': VOL_START, 'after': VOL_REPLACE},
        {'path': VOLUME, 'before': NATIVE_COUNT, 'after': NATIVE_COUNT + COUNT_INSERT},
    ])
    candidate.setdefault('ported_consumers', {})['overworld_primary_cumulus'] = {
        'entry': ENTRY, 'dimension': DIMENSION, 'member': VOLUME,
        'mechanism': 'Existing uniform_sample_budget and Core ABI2; dynamic post-angle count',
        'scope': 'Selected world0/composite3 primary clouds, no DH/VOXY; other volume includes unchanged',
        'native_hq_count_cap': 15, 'balanced_hq_count_cap': 14,
        'performance_hq_count_cap_on_generated_graph': 12,
        'performance_renderer_may_bypass': True,
        'runtime_verified': False, 'quality_verified': False, 'performance_verified': False,
    }
    return candidate


def recover(member: str, source: bytes) -> bytes:
    if member == DIMENSION:
        return source.replace(DIM_REPLACE.encode(), DIM_INCLUDE.encode(), 1)
    if member == VOLUME:
        return source.replace(VOL_REPLACE.encode(), VOL_START.encode(), 1).replace(COUNT_INSERT.encode(), b'', 1)
    raise ValueError(member)


def prepare(base: Path, output: Path, root: Path = ROOT):
    base, output, root = Path(base), Path(output), Path(root)
    if output.exists():
        raise ValueError('Output already exists; choose a fresh evidence directory')
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
    if difference != [DIMENSION, VOLUME] or set(changed) != set(assembled):
        raise ValueError('Candidate exceeded selected cloud source scope')
    for name in difference:
        if recover(name, changed[name]) != assembled[name]:
            raise ValueError('Native/current adapter body did not recover: ' + name)
    if candidate['additions'] != current['additions']:
        raise ValueError('Existing helper policy changed')
    source_inputs = {RECIPE: current_bytes, CATALOG: (root / CATALOG).read_bytes()}
    for folder in ('patches/adapters', RESOURCE_ROOT + 'patches/adapters'):
        for path in sorted((root / folder).rglob('*')):
            if path.is_file(): source_inputs[path.relative_to(root).as_posix()] = path.read_bytes()
    with tempfile.TemporaryDirectory(prefix='bliss-cloud-stage-') as temporary:
        stage = Path(temporary)
        product = stage / 'product'
        for name, data in source_inputs.items():
            target = product / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        (product / RECIPE).write_bytes(candidate_bytes)
        sync = sync_resources(base, ADAPTER, root=product, write=True)
        if set(sync['changed_files']) != {RESOURCE_ROOT + RECIPE, CATALOG}:
            raise ValueError('Unexpected product overlay: ' + repr(sync['changed_files']))
        artifact = stage / 'artifact'
        artifact.mkdir()
        metadata = build(base, artifact / 'candidate.zip', product / RECIPE, root=product)
        patch_parts, overlay_hashes = [], {}
        for name in (RECIPE, RESOURCE_ROOT + RECIPE, CATALOG):
            before, after = source_inputs[name], (product / name).read_bytes()
            target = artifact / 'overlay' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(after)
            overlay_hashes[name] = {'before': sha256(before), 'after': sha256(after)}
            patch_parts.extend(difflib.unified_diff(before.decode().splitlines(keepends=True),
                after.decode().splitlines(keepends=True), fromfile='a/' + name, tofile='b/' + name))
        (artifact / 'integration.patch').write_text(''.join(patch_parts))
        source_patch = []
        for name in difference:
            source_patch.extend(difflib.unified_diff(assembled[name].decode().splitlines(keepends=True),
                changed[name].decode().splitlines(keepends=True), fromfile='a/' + name, tofile='b/' + name))
        (artifact / 'source.patch').write_text(''.join(source_patch))
        (artifact / 'candidate-manifest.json').write_bytes(candidate_bytes)
        receipt = {
            'schema': 1, 'source_only': True,
            'original_archive_sha256': sha256(base.read_bytes()),
            'original_source_digest': shader_source_digest(original),
            'current_adapter_digest': shader_source_digest(assembled),
            'candidate_digest': shader_source_digest(changed),
            'candidate_zip_sha256': sha256((artifact / 'candidate.zip').read_bytes()),
            'changed_from_current_adapter': difference, 'added_shader_members': [],
            'native_current_body_recovered': True,
            'pins': {VOLUME: VOLUME_SHA, DIMENSION: DIMENSION_SHA, ENTRY: ENTRY_SHA},
            'guard': GUARD, 'count_insert': COUNT_INSERT,
            'overlay': overlay_hashes, 'sync': sync,
            'validation': metadata['validation'],
            'runtime_claim': False,
            'next_consumer': 'After parent integration, discover actual Iris generated options; public Balanced generated graph and original/Quality/Off/Cancel recovery on a fixed Overworld scene; inspect composite3/Core/visual quality and early original-relative cost. Performance may select native renderer and bypass this shader budget.',
        }
        (artifact / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
        if any((root / name).read_bytes() != data for name, data in source_inputs.items()):
            raise ValueError('Maintained input changed during preparation')
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(artifact, output)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True, help='New artifact directory; never overwritten')
    args = parser.parse_args()
    print(json.dumps(prepare(args.base, args.output), indent=2))


if __name__ == '__main__':
    main()
