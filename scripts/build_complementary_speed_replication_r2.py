#!/usr/bin/env python3
"""Pinned private native-support temporal PCF/VL budget; no runtime launch.

The builder preserves native sample coordinates and the nearSamples denominator.
It produces original/control/candidate/cue archives for parent-owned consumption.
"""
from __future__ import annotations
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import zipfile

from build_supported_pack_adapters import read_archive

ROOT = Path(__file__).resolve().parents[1]
MODE = 'SHADERLOD_COMPLEMENTARY_TEMPORAL_BUDGET_R2'
LIGHT = 'shaders/lib/lighting/mainLighting.glsl'
VL = 'shaders/lib/atmospherics/volumetricLight/volumetricLight.glsl'
SHADOW = 'shaders/lib/lighting/shadowSampling.glsl'
POLICY = 'shaders/lib/shaderlod/complementary_budget_policy_r2.glsl'
PCF = 'shaders/lib/shaderlod/complementary_pcf_r2.glsl'
VLB = 'shaders/lib/shaderlod/complementary_vl_r2.glsl'
PINS = {
    'reimagined': (
        'dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip',
        'fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38',
        'run-workloads/cross-shader-performance-r3/reimagined-original-budget-r1/client/workload-measurement.json'),
    'unbound': (
        'dev/logs/cross-shader-performance-r2/unbound-cloud-runtime-r1/archives/ComplementaryUnbound-r5.9.3-original-r2.zip',
        '98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a',
        'dev/logs/cross-shader-performance-r2/complementary-temporal-design-r1/parent-execution/workload-measurement.json'),
}
GATE = ('defined MC_OS_MAC && defined IS_IRIS && MC_VERSION == 12101 && '
        'IRIS_VERSION == 10812 && defined OVERWORLD && defined TAA && '
        'TAA_JITTER > 0 && SHADOW_QUALITY == 2 && RP_MODE == 1 && '
        '!defined DISTANT_HORIZONS && !defined VOXY && !defined CUSTOM_PBR && '
        '!defined GENERATED_NORMALS && !defined COLORWHEEL')
HEADER = '''// Complementary Shaders by EminGT; original License.txt is retained verbatim.
// Private pinned r5.9.3 experiment. Selected native AA/options remain unchanged.
uniform int shaderlodBridgeVersion = -1;
uniform int shaderlodEnabled = -1;
uniform int shaderlodPreset = -1;
uniform float shaderlodNear = -1.0;
uniform float shaderlodFar = -1.0;
uniform float shaderlodTransition = -1.0;
bool slBudgetActiveR2() {
    return shaderlodBridgeVersion == 2 && shaderlodEnabled == 1 &&
        shaderlodPreset >= 0 && shaderlodPreset <= 2 &&
        shaderlodNear >= 0.0 && shaderlodNear <= 4096.0 &&
        shaderlodFar > shaderlodNear && shaderlodFar <= 4096.0 &&
        shaderlodTransition >= 0.0 && shaderlodTransition <= 4096.0 &&
        frameCounter >= 0 && isEyeInWater == 0;
}
float slBudgetStartR2() { return shaderlodFar + shaderlodTransition; }
'''


def sha(data):
    return hashlib.sha256(data).hexdigest()


def replace_once(text, before, after):
    if text.count(before) != 1:
        raise ValueError('Native seam changed: ' + before[:80])
    return text.replace(before, after, 1)


def function(text, name):
    begin = text.index(name)
    begin = text.rfind('\n', 0, begin) + 1
    brace = text.index('{', begin)
    depth = 1
    end = brace + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[begin:end]


def pcf_helper(native):
    sampler = function(native, 'SampleTAAFilteredShadow(')
    sampler = sampler.replace('SampleTAAFilteredShadow(', 'slPairedShadowR2(', 1)
    sampler = replace_once(sampler,
        'for (int i = 0; i < shadowSamples; i++) {',
        'for (int i = frameCounter % 2; i < shadowSamples; i += 2) {')
    # Preserve offsetDist denominator and every selected native coordinate/bias.
    sampler = replace_once(sampler, 'shadow /= shadowSamples * 2.0;',
                           'shadow /= float(shadowSamples);')
    get = function(native, 'GetShadow(vec3 shadowPos,')
    get = get.replace('GetShadow(', 'slGetBudgetShadowR2(', 1)
    get = get.replace('SampleTAAFilteredShadow(', 'slPairedShadowR2(')
    return sampler + '\n' + get + '''
vec3 slReceiverR2(vec3 shadowPos, float lightmapY, float offset,
                 int shadowSamples, bool leaves, vec3 playerPos,
                 float distanceBlocks, int subsurfaceMode) {
#if SHADERLOD_COMPLEMENTARY_TEMPORAL_BUDGET_R2 >= 2
    if (slBudgetActiveR2() && renderStage == MC_RENDER_STAGE_TERRAIN_SOLID &&
        subsurfaceMode == 0 && !leaves && shadowSamples == 2 &&
        distanceBlocks >= slBudgetStartR2() && distanceBlocks <= 16777216.0) {
#if SHADERLOD_COMPLEMENTARY_TEMPORAL_BUDGET_R2 == 3
        return vec3(8.0, 0.0, 8.0); // untimed exact reached-budget cue
#else
        return slGetBudgetShadowR2(shadowPos, lightmapY, offset, shadowSamples,
                                  leaves, playerPos);
#endif
    }
#endif
    return GetShadow(shadowPos, lightmapY, offset, shadowSamples, leaves, playerPos);
}
'''


def vl_helper(native):
    body = native[native.index('vec4 GetVolumetricLight('):]
    body = body.replace('GetVolumetricLight(', 'slAdaptedVLR2(', 1)
    body = replace_once(body, '    for (int i = 0; i <= totalSamples; i++) {', '''
    bool slMerge = false;
    bool slUsedMerge = false;
    float slIntegralDistance = 0.0;
    float slDensityScale = 1.0;
    for (int i = 0; i <= totalSamples; i += slMerge ? 2 : 1) {
        slMerge = false;
        slDensityScale = 1.0;''')
    body = replace_once(body,
        '            nextDistance = pow(t, sampleDistribution) * activeThreshold;\n'
        '            scenePos = rayDirection * nextDistance;', '''
            nextDistance = pow(t, sampleDistribution) * activeThreshold;
            slIntegralDistance = nextDistance;
#if SHADERLOD_COMPLEMENTARY_TEMPORAL_BUDGET_R2 >= 2
            // Pair (2k,2k+1) keeps both ORIGINAL sample positions/denominator.
            // Only full distant clear-air intervals before ALL native breaks merge.
            if (i > 0 && i % 2 == 0 && i + 1 <= nearSamples &&
                lastDistance >= slBudgetStartR2() && nextDistance >= slBudgetStartR2()) {
                float slT2 = (float(i + 1) + dither) / float(nearSamples);
                float slEnd2 = pow(slT2, sampleDistribution) * activeThreshold;
                if (slEnd2 < rayEnd && slEnd2 <= lViewPos0
#if DETAIL_QUALITY > 0
                    && slEnd2 <= entityLinearDepth
#endif
                ) {
                    float slW0 = smoothstep1(pow(lastDistance / maxDistance, fogCurve));
                    float slW1 = smoothstep1(pow(nextDistance / maxDistance, fogCurve));
                    float slW2 = smoothstep1(pow(slEnd2 / maxDistance, fogCurve));
                    float slUnionWeight = slW2 - slW0;
                    if (slUnionWeight > 0.0000001 && slUnionWeight <= 1.0 &&
                        slW1 >= slW0 && slW2 >= slW1) {
                        slMerge = true;
                        slUsedMerge = true;
                        slIntegralDistance = slEnd2;
                        // Two-frame importance compensation: each native sample's
                        // ORIGINAL quadrature weight, not equal-weight field reuse.
                        if (frameCounter % 2 == 0) {
                            slDensityScale = 2.0 * (slW1 - slW0) / slUnionWeight;
                        } else {
                            slDensityScale = 2.0 * (slW2 - slW1) / slUnionWeight;
                            nextDistance = slEnd2;
                        }
                    }
                }
            }
#endif
            scenePos = rayDirection * nextDistance;''')
    body = replace_once(body, '        // For fog density to not move regardless of how we distribute the samples',
        '        if (!slMerge) slIntegralDistance = nextDistance;\n\n'
        '        // For fog density to not move regardless of how we distribute the samples')
    body = replace_once(body,
        'float nextWeight    = smoothstep1(pow(nextDistance / maxDistance, fogCurve));',
        'float nextWeight    = smoothstep1(pow(slIntegralDistance / maxDistance, fogCurve));')
    body = replace_once(body, '        lastDistance = nextDistance;',
        '        lastDistance = slIntegralDistance;')
    body = replace_once(body,
        '        vec4 stepResult = vec4(localDensity * sliceWeight, sliceWeight);',
        '        vec4 stepResult = vec4(localDensity * (sliceWeight * slDensityScale), sliceWeight);')
    body = replace_once(body, '    return volumetricLight;', '''
#if SHADERLOD_COMPLEMENTARY_TEMPORAL_BUDGET_R2 == 3
    if (slUsedMerge) volumetricLight.rgb = vec3(16.0, 0.0, 16.0);
#endif
    return volumetricLight;''')
    signature = body[:body.index('// ============================== Step 1:')].replace('slAdaptedVLR2', 'slVLR2')
    body += '\n' + signature + '''
#if SHADERLOD_COMPLEMENTARY_TEMPORAL_BUDGET_R2 >= 2
    if (slBudgetActiveR2() && translucentMult == vec3(1.0))
        return slAdaptedVLR2(vlFactor, translucentMult, lViewPos0, lViewPos1,
                            nViewPos, VdotL, VdotU, z0, z1, z1lod, dither);
#endif
    return GetVolumetricLight(vlFactor, translucentMult, lViewPos0, lViewPos1,
                             nViewPos, VdotL, VdotU, z0, z1, z1lod, dither);
}
'''
    return body


# Profiles are packaged source inputs, not captured loaded-state observations.
NATIVE_PROFILES = {
    'reimagined': ('reimaginedquad', 'ccfca334aeaa074a74d1f8e9071b707d32161a6b771366fefcc0f87f3b1175f4'),
    'unbound': ('unboundquad', 'b64d94998c99bf867e80d350a4a0a8108c58507e0401eb053a70e3aca81a5036'),
}


def build(pack, output, *, native_profile=None):
    archive_name, pin, raw_name = PINS[pack]
    archive, raw = ROOT / archive_name, ROOT / raw_name
    if native_profile is not None and pack == 'unbound':
        archive = ROOT / 'dependencies/upstream/other-pack-research/ComplementaryUnbound_r5.9.3.zip'
    data = archive.read_bytes()
    if sha(data) != pin:
        raise ValueError('Original archive changed')
    native_map_kind = 'captured-runtime-receipt'
    if native_profile is None:
        measurement = json.loads(raw.read_text())
        native_options = measurement['effective_shader_options']
        if measurement['actual_shader_sha256'] != pin or len(native_options) != 398:
            raise ValueError('Authentic original option receipt changed')
    else:
        raw = Path(native_profile)
        profile_name, profile_sha = NATIVE_PROFILES[pack]
        if sha(raw.read_bytes()) != profile_sha:
            raise ValueError('Pinned retained native profile changed')
        native_options = json.loads(raw.read_bytes())
        native_map_kind = 'packaged-native-profile-not-runtime-observation'
    if (not isinstance(native_options, dict) or len(native_options) != 398 or
            not all(isinstance(k, str) and isinstance(v, str) for k, v in native_options.items())):
        raise ValueError('Native profile requires 398 string option values')
    original = read_archive(archive)
    lighting = original[LIGHT].decode().replace('\r\n', '\n')
    shadow = original[SHADOW].decode().replace('\r\n', '\n')
    vl = original[VL].decode().replace('\r\n', '\n')
    policy = HEADER.encode()
    pcf = pcf_helper(shadow).encode()
    vla = vl_helper(vl).encode()
    pcf_gate = GATE + ' && defined GBUFFERS_TERRAIN && defined MC_RENDER_STAGE_TERRAIN_SOLID'
    vl_gate = GATE + ' && defined COMPOSITE1 && LIGHTSHAFT_QUALI_DEFINE == 2 && DETAIL_QUALITY == 2 && !defined LIGHTSHAFT_SMOKE'
    output.mkdir(parents=True, exist_ok=True)
    manifest = {'schema': 1, 'status': 'SOURCE_CANDIDATE_NOT_RENDERED', 'pack': pack,
        'original': str(archive), 'original_sha256': pin,
        'native_map_kind': native_map_kind,
        'runtime_loaded_map_verified': False,
        'native_map_source': str(raw), 'native_map_source_sha256': sha(raw.read_bytes()),
        'native398': native_options, 'native_options_mutated': False,
        'modified_members': [LIGHT, VL], 'added_members': [POLICY, PCF, VLB],
        'source_pins': {n: sha(original[n]) for n in [LIGHT, SHADOW, VL, 'License.txt']},
        'helper_sha256': {POLICY: sha(policy), PCF: sha(pcf), VLB: sha(vla)},
        'archives': {}, 'owned_jobs': [], 'driver_compiled': False,
        'quality_checked': False, 'performance_measured': False}
    (output / 'original.zip').write_bytes(data)
    for mode, arm in enumerate(['off', 'control', 'candidate', 'cue']):
        files = original.copy()
        option = '#define ' + MODE + ' ' + str(mode) + ' // [0 1 2 3]\n'
        include = '\n' + option + '#if ' + MODE + ' > 0 && ' + pcf_gate + '\n'
        include += '#include "/lib/shaderlod/complementary_budget_policy_r2.glsl"\n'
        include += '#include "/lib/shaderlod/complementary_pcf_r2.glsl"\n#endif\n'
        changed = replace_once(lighting, '    #include "/lib/lighting/shadowSampling.glsl"',
            '    #include "/lib/lighting/shadowSampling.glsl"' + include)
        call = 'GetShadow(shadowPos, lightmap.y, offset, shadowSamples, leaves, playerPos)'
        changed = replace_once(changed, '                        shadowMult *= ' + call + ';',
            '                        #if ' + MODE + ' > 0 && ' + pcf_gate + '\n'
            '                        shadowMult *= slReceiverR2(shadowPos, lightmap.y, offset, shadowSamples, leaves, playerPos, lViewPos, subsurfaceMode);\n'
            '                        #else\n                        shadowMult *= ' + call + ';\n                        #endif')
        files[LIGHT] = changed.encode()
        files[VL] = original[VL] + ('\n' + option + '#if ' + MODE + ' > 0 && ' + vl_gate + '\n'
            '#include "/lib/shaderlod/complementary_budget_policy_r2.glsl"\n'
            '#include "/lib/shaderlod/complementary_vl_r2.glsl"\n'
            '#define GetVolumetricLight slVLR2\n#endif\n').encode()
        files.update({POLICY: policy, PCF: pcf, VLB: vla})
        dest = output / (arm + '.zip')
        with zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED) as z:
            for name, content in sorted(files.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info, content)
        written = read_archive(dest)
        assert written == files
        assert sorted(n for n in original if written[n] != original[n]) == sorted([LIGHT, VL])
        assert written['License.txt'] == original['License.txt']
        manifest['archives'][arm] = {'path': str(dest.resolve()), 'sha256': sha(dest.read_bytes()), 'mode': mode}
        expected = dict(native_options, **{MODE: str(mode)})
        (output / ('expected-' + arm + '-399.json')).write_text(json.dumps(expected, indent=2, sort_keys=True) + '\n')
        (output / ('options-' + arm + '.txt')).write_text(''.join(f'{k}={v}\n' for k, v in sorted(expected.items())))
        for name in [LIGHT, VL]:
            (output / (arm + '-' + Path(name).stem + '.diff')).write_text(''.join(difflib.unified_diff(
                original[name].decode().splitlines(True), files[name].decode().splitlines(True), fromfile=name, tofile=name)))
    for name, content in {POLICY: policy, PCF: pcf, VLB: vla}.items():
        (output / Path(name).name).write_bytes(content)
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack', choices=PINS, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = build(args.pack, args.output)
    print(json.dumps({'pack': result['pack'], 'archives': result['archives'], 'rendered': False}))


if __name__ == '__main__':
    main()
