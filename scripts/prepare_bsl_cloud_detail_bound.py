#!/usr/bin/env python3
"""Prepare isolated pinned BSL clear-sky primary-cloud zero-detail bound."""
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

ADAPTER = 'bsl-shaders'
RECIPE = f'patches/adapters/{ADAPTER}.json'
HELPER = f'patches/adapters/{ADAPTER}.glsl'
CERT = 'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/integration/bindingcompat/BslSourceCertificate.java'
CLOUD = 'shaders/lib/atmospherics/clouds.glsl'
DEFERRED = 'shaders/program/deferred1.glsl'
ENTRY = 'shaders/world0/deferred1.fsh'
NOISE = 'shaders/tex/noise.png'
NOISE_META = 'shaders/tex/noise.png.mcmeta'
MARKER = 'SHADERLOD_BSL_PRIMARY_CLOUD_CONTEXT'
PINS = {
    CLOUD: 'c0e21ee39082d8154f6aa8792a77f8a60c7fd3967614d56b1abaa06887256750',
    DEFERRED: '6d4cd8420e085d9254957f396a3aeca6f8bc0c9b1116c231eabd6c0f1541742e',
    ENTRY: '3095370970d6ced710c8d859f44908b7c12a3f946fddcb53a274aee3660579d3',
    NOISE: '5ff93225ed7018e90822824a599e4e080e985fb882fdc15ac9e117dd8ba22a34',
    NOISE_META: 'e9c800d49d941e58d1c9f114b4564886b0c984369fab42515935497948ea8440',
}

def crlf(text): return text.replace('\n', '\r\n')

HELPER_BEFORE = 'uniform int shaderlodPreset;'
HELPER_AFTER = 'uniform int shaderlodPreset = -1;'
DEFERRED_BEFORE = '#include "/lib/atmospherics/clouds.glsl"\r\n'
DEFERRED_AFTER = crlf(
    '#if defined OVERWORLD && defined FSH && !defined DISTANT_HORIZONS && !defined VOXY && !defined ADVANCED_MATERIALS\n'
    f'#define {MARKER} 1\n'
    '#endif\n') + DEFERRED_BEFORE + crlf(f'#ifdef {MARKER}\n#undef {MARKER}\n#endif\n')
CLOUD_INSERT_BEFORE = 'float InvLerp(float v, float l, float h) {'
CLOUD_EXTRA = crlf('''#if defined(SHADERLOD_BSL_PRIMARY_CLOUD_CONTEXT) && SHADERLOD_BSL_PRIMARY_CLOUD_CONTEXT == 1 && defined OVERWORLD && defined FSH && CLOUDS == 2 && CLOUD_BASE == 0 && SHADERLOD_REFINEMENT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY && !defined ADVANCED_MATERIALS
// Same two blue-channel detail lookups and height interpolation at explicit LOD0.
// The pinned native 512x512 noise PNG and runtime sampler level/filter parity remain admission gates.
float ShaderLodCloudDetailLod0(vec2 coord, float cloudGradient) {
    float detailZ = floor(cloudGradient * float(CLOUD_THICKNESS)) * 0.04;
    float detailFrac = fract(cloudGradient * float(CLOUD_THICKNESS));
    float noiseDetailLow = texture2DLod(noisetex, coord.xy + detailZ, 0.0).b;
    float noiseDetailHigh = texture2DLod(noisetex, coord.xy + detailZ + 0.04, 0.0).b;
    return mix(noiseDetailLow, noiseDetailHigh, detailFrac);
}
float ShaderLodCloudPrimaryClearBound(vec2 coord, vec2 wind, float cloudGradient, float sunCoverage, float dither) {
    if (shaderlodBridgeVersion != 2 || shaderlodEnabled != 1 || shaderlodPreset != 0 || rainStrength != 0.0)
        return CloudSample(coord, wind, cloudGradient, sunCoverage, dither);
    vec2 nativeCoord = coord;
    coord *= 0.004 * CLOUD_STRETCH;
    vec2 baseCoord = coord * 0.25 + wind;
    vec2 detailCoord = coord.xy * 0.5 - wind * 2.0;
    float noiseBase = CloudSampleBasePerlin(baseCoord);
    float noiseCoverage = CloudCoverageDefault(cloudGradient);
    float detailWeight = 0.0476 * CLOUD_DETAIL;
    if (!(noiseBase >= 0.0 && noiseBase <= 1.0 && cloudGradient >= 0.0 && cloudGradient <= 1.0 &&
          sunCoverage >= 0.0 && sunCoverage <= 1.0 && noiseCoverage >= 0.0 && noiseCoverage <= 6.0 &&
          detailWeight >= 0.0 && detailWeight <= 1.0 && all(lessThan(abs(detailCoord), vec2(1e20)))))
        return CloudSample(nativeCoord, wind, cloudGradient, sunCoverage, dither);
    // Detail blue is normalized to [0,1]. Preserve native ordered clear-weather pre-max arithmetic.
    float upper = mix(noiseBase, 1.0, detailWeight) * 21.0;
    upper = upper - noiseCoverage;
    upper = upper - (sunCoverage * 3.0 + CLOUD_AMOUNT);
    if (upper < -0.001) return 0.0;
    float noiseDetail = ShaderLodCloudDetailLod0(detailCoord, cloudGradient);
    return CloudCombineDefault(noiseBase, noiseDetail, noiseCoverage, sunCoverage);
}
#endif

''')
CLOUD_CALL_BEFORE = '\t\tfloat noise = CloudSample(cloudCoord, wind, cloudGradient, sunCoverage, dither);'
CLOUD_CALL_AFTER = crlf(f'''#if defined({MARKER}) && {MARKER} == 1 && defined OVERWORLD && defined FSH && CLOUDS == 2 && CLOUD_BASE == 0 && SHADERLOD_REFINEMENT_ADAPTER == 1 && !defined DISTANT_HORIZONS && !defined VOXY && !defined ADVANCED_MATERIALS
        float noise;
        if (fadeFaster) noise = CloudSample(cloudCoord, wind, cloudGradient, sunCoverage, dither);
        else noise = ShaderLodCloudPrimaryClearBound(cloudCoord, wind, cloudGradient, sunCoverage, dither);
#else
''') + CLOUD_CALL_BEFORE + crlf('\n#endif')


def candidate_manifest(current, helper_sha):
    m = copy.deepcopy(current)
    if m['id'] != ADAPTER or m['version'] != '10.1.8': raise ValueError('Only pinned BSL 10.1.8')
    if any(p.get('before') == CLOUD_INSERT_BEFORE for p in m['patches']): raise ValueError('Cloud bound already pinned')
    for name, digest in PINS.items():
        if name in m['source_members'] and m['source_members'][name] != digest: raise ValueError('Source pin changed: '+name)
        m['source_members'][name] = digest
    m['patches'].extend([
        {'path': DEFERRED, 'before': DEFERRED_BEFORE, 'after': DEFERRED_AFTER},
        {'path': CLOUD, 'before': CLOUD_INSERT_BEFORE, 'after': CLOUD_EXTRA + CLOUD_INSERT_BEFORE},
        {'path': CLOUD, 'before': CLOUD_CALL_BEFORE, 'after': CLOUD_CALL_AFTER},
    ])
    m['additions']['shaders/lib/shaderlod/adapter.glsl']['sha256'] = helper_sha
    m.setdefault('ported_consumers', {})['primary_clear_cloud_detail_bound'] = {
        'entry': ENTRY, 'path': 'world0/deferred1 primary DrawCloudVolumetric false',
        'mode': 'Balanced only, Core bridge2 enabled1; clear rainStrength 0; native for every unknown',
        'member': CLOUD, 'noise_png_sha256': PINS[NOISE], 'noise_mcmeta_sha256': PINS[NOISE_META],
        'runtime_sampler_parity_required': True, 'dh_pbr_voxy': 'native fallback',
        'runtime_verified': False, 'quality_verified': False, 'performance_verified': False,
    }
    return m


def recover(name, data):
    if name == CLOUD:
        return data.replace(CLOUD_EXTRA.encode(), b'', 1).replace(CLOUD_CALL_AFTER.encode(), CLOUD_CALL_BEFORE.encode(), 1)
    if name == DEFERRED: return data.replace(DEFERRED_AFTER.encode(), DEFERRED_BEFORE.encode(), 1)
    if name == 'shaders/lib/shaderlod/adapter.glsl': return data.replace(HELPER_AFTER.encode(), HELPER_BEFORE.encode(), 1)
    raise ValueError(name)


def prepare(base: Path, output: Path, root: Path = ROOT):
    base, output, root = Path(base), Path(output), Path(root)
    if output.exists(): raise ValueError('Output already exists')
    source_inputs = {RECIPE: (root/RECIPE).read_bytes(), HELPER: (root/HELPER).read_bytes(),
                     CATALOG: (root/CATALOG).read_bytes(), CERT: (root/CERT).read_bytes()}
    current = json.loads(source_inputs[RECIPE])
    if sha256(base.read_bytes()) != current['release_archive_sha256']: raise ValueError('Original archive hash')
    original = read_archive(base)
    for n,pin in PINS.items():
        if sha256(original[n]) != pin: raise ValueError('Original member pin: '+n)
    assembled = apply(original, current, root)
    if source_inputs[HELPER].count(HELPER_BEFORE.encode()) != 1: raise ValueError('Adapter preset anchor')
    helper_after = source_inputs[HELPER].replace(HELPER_BEFORE.encode(), HELPER_AFTER.encode(), 1)
    candidate = candidate_manifest(current, sha256(helper_after))
    candidate_bytes = (json.dumps(candidate, indent=2)+'\n').encode()
    for folder in ('patches/adapters', RESOURCE_ROOT+'patches/adapters'):
        for path in sorted((root/folder).rglob('*')):
            if path.is_file():source_inputs[path.relative_to(root).as_posix()] = path.read_bytes()
    with tempfile.TemporaryDirectory(prefix='bsl-cloud-stage-') as temporary:
        stage=Path(temporary); product=stage/'product'
        for name,data in source_inputs.items():
            path=product/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
        (product/HELPER).write_bytes(helper_after)
        (product/RECIPE).write_bytes(candidate_bytes)
        changed=apply(original,candidate,product)
        difference=sorted(n for n in set(assembled)|set(changed) if assembled.get(n)!=changed.get(n))
        expected=[CLOUD,'shaders/lib/shaderlod/adapter.glsl',DEFERRED]
        if difference != sorted(expected) or set(changed)!=set(assembled): raise ValueError('Source scope mismatch: '+repr(difference))
        for name in difference:
            if recover(name,changed[name]) != assembled[name]: raise ValueError('Native/current body recovery: '+name)
        sync=sync_resources(base,ADAPTER,root=product,write=True)
        expected_sync={RESOURCE_ROOT+RECIPE,RESOURCE_ROOT+HELPER,CATALOG}
        if set(sync['changed_files']) != expected_sync: raise ValueError('Unexpected sync scope: '+repr(sync['changed_files']))
        recipe_sha=sha256(candidate_bytes)
        old_cert=source_inputs[CERT].decode()
        old_sha=sha256(source_inputs[RECIPE])
        if old_cert.count(f'RECIPE_SHA="{old_sha}"')!=1: raise ValueError('Certificate recipe join changed')
        new_cert=old_cert.replace(f'RECIPE_SHA="{old_sha}"', f'RECIPE_SHA="{recipe_sha}"',1).encode()
        (product/CERT).write_bytes(new_cert)
        artifact=stage/'artifact';artifact.mkdir()
        metadata=build(base,artifact/'candidate.zip',product/RECIPE,root=product)
        overlay_names=(RECIPE,HELPER,RESOURCE_ROOT+RECIPE,RESOURCE_ROOT+HELPER,CATALOG,CERT)
        patch_parts=[]; overlay={}
        for name in overlay_names:
            before=source_inputs[name];after=(product/name).read_bytes()
            target=artifact/'overlay'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(after)
            overlay[name]={'before':sha256(before),'after':sha256(after)}
            patch_parts.extend(difflib.unified_diff(before.decode().splitlines(keepends=True),after.decode().splitlines(keepends=True),fromfile='a/'+name,tofile='b/'+name))
        (artifact/'integration.patch').write_text(''.join(patch_parts))
        source_patch=[]
        for name in difference:
            source_patch.extend(difflib.unified_diff(assembled[name].decode().splitlines(keepends=True),changed[name].decode().splitlines(keepends=True),fromfile='a/'+name,tofile='b/'+name))
        (artifact/'source.patch').write_text(''.join(source_patch))
        (artifact/'candidate-manifest.json').write_bytes(candidate_bytes)
        receipt={'schema':1,'source_only':True,'original_archive_sha256':sha256(base.read_bytes()),
                 'original_source_digest':shader_source_digest(original),'current_adapter_digest':shader_source_digest(assembled),
                 'candidate_digest':shader_source_digest(changed),'candidate_zip_sha256':sha256((artifact/'candidate.zip').read_bytes()),
                 'changed_from_current_adapter':difference,'exact_body_recovered':True,'pins':PINS,'overlay':overlay,
                 'sync':sync,'validation':metadata['validation'],'runtime_claim':False,
                 'source_limit':'Clear Balanced exact-zero detail read bound with 0.001 float margin; dynamic sampler LOD0 parity, linked Core/selected primary route and actual density frequency remain unverified'}
        (artifact/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
        if any((root/name).read_bytes()!=data for name,data in source_inputs.items()):raise ValueError('Maintained input changed during preparation')
        output.parent.mkdir(parents=True,exist_ok=True);shutil.copytree(artifact,output)
    return receipt


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(json.dumps(prepare(a.base,a.output),indent=2))
if __name__=='__main__':main()
