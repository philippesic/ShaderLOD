#!/usr/bin/env python3
"""Prepare isolated Reimagined r5.9.3 lazy prior-weight reuse; no runtime adoption."""
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

ADAPTER = 'complementary-reimagined'
RECIPE = f'patches/adapters/{ADAPTER}.json'
MEMBER = 'shaders/lib/atmospherics/volumetricLight/volumetricLight.glsl'
MEMBER_SHA = 'd380c3243df73b390433c015fcb5b9182cf98a511b49f7a98d5ccac1313ae8ff'
CERTIFICATE = ('ports/26.2/neoforge/src/main/java/com/pp/shaderlod/integration/'
               'bindingcompat/ReimaginedSourceCertificate.java')
CERTIFICATE_SHA = 'e131107997af42421a110569c6d4ba4ef8e599dc395785555ee8e224f3c6d416'
GUARD = ('SHADERLOD_REFINEMENT_ADAPTER == 1 && defined COMPOSITE1 && defined OVERWORLD '
         '&& !defined DISTANT_HORIZONS && !defined VOXY && RP_MODE == 1 '
         '&& !defined GENERATED_NORMALS && !defined COATED_TEXTURES')
NATIVE_CURRENT = '        float currentWeight = smoothstep1(pow(lastDistance / maxDistance, fogCurve));'
LAST = '    float lastDistance = 0.0;'
TAIL = '        lastDistance = nextDistance;'
# Preserve CRLF in the authentic member, like the current maintained recipe.
DECLARATION = '\r\n'.join([
    f'    #if {GUARD}',
    '    // Exact current generated-volume eligibility; Off/Quality/unknown bridge stay native.',
    '    bool shaderlodReuseVLWeight = shaderlodBridgeVersion == 2 && shaderlodEnabled == 1 &&',
    '        shaderlodPreset == 0 && isEyeInWater == 0 &&',
    '        translucentMult == vec3(1.0) &&',
    '        viewWidth + viewHeight - gl_FragCoord.x - gl_FragCoord.y >= 1.5;',
    '    float shaderlodPriorVLWeight = 0.0;',
    '    #endif',
])
CURRENT = '\r\n'.join([
    f'        #if {GUARD}',
    '        float currentWeight;',
    '        if (shaderlodReuseVLWeight && i > 0) currentWeight = shaderlodPriorVLWeight;',
    '        else currentWeight = smoothstep1(pow(lastDistance / maxDistance, fogCurve));',
    '        #else',
    NATIVE_CURRENT,
    '        #endif',
])
UPDATE = '\r\n'.join([
    f'        #if {GUARD}',
    '        if (shaderlodReuseVLWeight) shaderlodPriorVLWeight = nextWeight;',
    '        #endif',
])


def candidate_manifest(current):
    candidate = copy.deepcopy(current)
    if candidate['id'] != ADAPTER or candidate['version'] != 'r5.9.3':
        raise ValueError('Only pinned Reimagined r5.9.3 is supported')
    if candidate['source_members'].get(MEMBER) != MEMBER_SHA:
        raise ValueError('Unexpected native volume member pin')
    if any('shaderlodPriorVLWeight' in p['after'] for p in candidate['patches']):
        raise ValueError('Prior-weight candidate already present; review current recipe')
    candidate['patches'].extend([
        {'path': MEMBER, 'before': LAST, 'after': LAST + '\r\n' + DECLARATION},
        {'path': MEMBER, 'before': NATIVE_CURRENT, 'after': CURRENT},
        {'path': MEMBER, 'before': TAIL, 'after': TAIL + '\r\n' + UPDATE},
    ])
    candidate.setdefault('ported_consumers', {})['lazy_prior_volume_weight'] = {
        'mechanism': 'Carry native nextWeight at the native lastDistance update; first use stays at native site',
        'scope': 'Existing generated Overworld composite1 volume eligibility and bridge2 On Balanced0',
        'counts_positions_texture_operations_changed': False,
        'fallback': 'Native expression for Off/Quality/Performance/Aggressive/unknown bridge/preset and all excluded paths',
        'closed_related_trial': 'docs/evaluation/reimagined-vl-weight-early-cost-r1.md',
        'closed_trial_reopened': False,
        'driver_equivalence_verified': False,
        'performance_verified': False,
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
    differences = sorted(n for n in set(assembled) | set(changed) if assembled.get(n) != changed.get(n))
    if differences != [MEMBER] or set(changed) != set(assembled):
        raise ValueError('Candidate exceeded volume-only scope')
    recovered = changed[MEMBER].replace((LAST + '\r\n' + DECLARATION).encode(), LAST.encode(), 1)
    recovered = recovered.replace(CURRENT.encode(), NATIVE_CURRENT.encode(), 1)
    recovered = recovered.replace((TAIL + '\r\n' + UPDATE).encode(), TAIL.encode(), 1)
    if recovered != assembled[MEMBER]:
        raise ValueError('Current generated volume body was not preserved')
    certificate_before = (root / CERTIFICATE).read_bytes()
    if sha256(certificate_before) != CERTIFICATE_SHA:
        raise ValueError('NeoForge26.2 certificate preimage changed; review before preparing')
    certificate_anchor = ('public static final String RECIPE_SHA="' + sha256(current_bytes) + '";').encode()
    if certificate_before.count(certificate_anchor) != 1:
        raise ValueError('Expected one exact current NeoForge26.2 recipe certificate pin')
    certificate_after = certificate_before.replace(certificate_anchor,
        ('public static final String RECIPE_SHA="' + sha256(candidate_bytes) + '";').encode(), 1)
    source_inputs = {RECIPE: current_bytes, CATALOG: (root / CATALOG).read_bytes(),
                     CERTIFICATE: certificate_before}
    for subtree in ('patches/adapters', RESOURCE_ROOT + 'patches/adapters'):
        for p in sorted((root / subtree).rglob('*')):
            if p.is_file(): source_inputs[p.relative_to(root).as_posix()] = p.read_bytes()
    with tempfile.TemporaryDirectory(prefix='complementary-weight-stage-') as temporary:
        stage = Path(temporary)
        product = stage / 'product'
        for name, data in source_inputs.items():
            target = product / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        (product / RECIPE).write_bytes(candidate_bytes)
        (product / CERTIFICATE).write_bytes(certificate_after)
        sync = sync_resources(base, ADAPTER, root=product, write=True)
        if set(sync['changed_files']) != {RESOURCE_ROOT + RECIPE, CATALOG}:
            raise ValueError('Unexpected resource/catalog delta')
        artifact = stage / 'artifact'
        artifact.mkdir()
        metadata = build(base, artifact / 'candidate.zip', product / RECIPE, root=product)
        parts, overlay = [], {}
        for name in (RECIPE, RESOURCE_ROOT + RECIPE, CATALOG, CERTIFICATE):
            before, after = source_inputs[name], (product / name).read_bytes()
            target = artifact / 'overlay' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(after)
            overlay[name] = {'before': sha256(before), 'after': sha256(after)}
            parts.extend(difflib.unified_diff(before.decode().splitlines(keepends=True),
                after.decode().splitlines(keepends=True), fromfile='a/' + name, tofile='b/' + name))
        (artifact / 'integration.patch').write_text(''.join(parts))
        (artifact / 'candidate-manifest.json').write_bytes(candidate_bytes)
        receipt = dict(schema=1, source_only=True, adapter=ADAPTER,
            original_archive_sha256=sha256(base.read_bytes()), original_member_sha256=MEMBER_SHA,
            original_source_digest=shader_source_digest(original),
            current_adapter_digest=shader_source_digest(assembled),
            candidate_digest=shader_source_digest(changed),
            candidate_zip_sha256=sha256((artifact / 'candidate.zip').read_bytes()),
            changed_from_current_adapter=differences, added_shader_members=[],
            current_generated_body_recovered=True, guard=GUARD, overlay=overlay,
            sync=sync, validation=metadata['validation'],
            closed_related_trial='docs/evaluation/reimagined-vl-weight-early-cost-r1.md',
            limitations='Same carry mechanism as closed no-gain trial; lazy first use and current generated eligibility only. No renewed speed premise, driver equivalence, runtime, quality, DH or PBR claim.',
            certificate_change='Exact NeoForge26.2 RECIPE_SHA pin only; complete source/state/options/vertex/native-return validation unchanged',
            future_consumer='Unapplied recipe/resource/catalog plus exact NeoForge26.2 source-certificate overlay. Paused sealed Reimagined runtime remains next, unchanged. Later separate source/build/runtime adoption needs fresh selection state and actual Balanced402 options/bridge/native398 fallback/recovery checks; old generated state still refuses. No automatic FPS retry.')
        (artifact / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
        if any((root / name).read_bytes() != data for name, data in source_inputs.items()):
            raise ValueError('Maintained input changed during preparation')
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(artifact, output)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.base, args.output), indent=2))


if __name__ == '__main__':
    main()
