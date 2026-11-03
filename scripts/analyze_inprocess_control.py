#!/usr/bin/env python3
"""Validate four correlated live-bridge phases and summarize separate timing streams."""
from __future__ import annotations
import argparse
import copy
from datetime import datetime
import hashlib
import json
import math
from pathlib import Path
import statistics
import uuid

from summarize_workload import stream
from run_proof_matrix import read_option_properties

METHOD = 'One JVM/world/pipeline; all five modules off; only live global bridge enabled changes'
EFFECTS_METHOD = 'One JVM/world/pipeline; fixed five-module effect options; only live global bridge enabled changes'
COHERENCE_METHOD = 'One JVM/world/pipeline; original Photon; only shadow frustum coherence changes'
TERRAIN_METHOD = 'One JVM/world/pipeline; fixed B15/global enabled; only terrain policy enabled changes plus recorded fixed-scene manual re-arm'
SSR_COST_METHOD = 'One JVM/world/pipeline; qualified fixed SSR-only B15/private trace policy; only live global bridge enabled changes'
TERRAIN_PHOTON_SHA256 = 'b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c'
ORIGINAL_NULL_OPTIONS_SHA256 = 'ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669'
ORIGINAL_PHOTON_SHA256 = '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
OFF_OPTIONS = ('SHADERLOD_CLOUDS', 'SHADERLOD_WATER', 'SHADERLOD_FOG', 'SHADERLOD_SSR', 'SHADERLOD_MATERIALS',
               'SHADERLOD_CACHED_SKY', 'SHADERLOD_WATER_ANALYTIC', 'SHADERLOD_DEBUG')
EFFECT_OPTIONS = OFF_OPTIONS[:5]
FALSE_FLAGS = ('terrain_active', 'terrain_configured', 'terrain_confirmation', 'shadow_culling_configured',
               'shadow_culling_active', 'shadow_lists_configured', 'shadow_lists_active')
CONFIG_FIELDS = {'lod': {'enabled', 'preset', 'near', 'far', 'transition', 'debug'},
                 'terrain': {'enabled', 'vanillaChunkCap', 'warmSourceConfirmed', 'warmupSeconds'},
                 'rendering': {'optimizeShadowCulling', 'separateShadowVisibilityLists'}}
SSR_COST_COMMON_MEMBERS = {
    'LICENSE': '514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5',
    'shaderlod-build.json': '5653a52f590ce10cd75fbaff24f1ed053e49564ade7b4c79bb2747f69feed084',
    'shaders/include/shaderlod/lod.glsl': '2ac35e39e408d953e2d40769db4302aeff2fadd8e5884c47cfc757fa7d294b7f',
    'shaders/program/d4_deferred_shading.fsh': 'd195159465a86f88b918c504f25cc56f5c987e27774906108e02993d24bbe000'}
SSR_COST_ARCHIVES = {
    TERRAIN_PHOTON_SHA256: {'policy': 'skip', 'settings': 'd1f571e5d96ecffdf7ebcfcf28a13d34dc5bfd5c3606702e06832f95f88aa3cd',
        'specular': '6a4a8c53675f7287cf28168913f3e3e2b7ce9bd9d2fb5755548a27927ee4d92d'},
    '41ecbee8b72039654fb0d25b16a841e3a2f4500025ec918687f94c0b8cba1555': {'policy': 'full',
        'settings': '33e39309f4e9a31990ce722c32cd56bbd981880f6447756f87bbbebebad69602',
        'specular': 'd57fce9683b3675f60edb08dd7f52168833852c7b57e15196387d337e8938df8',
        'switch': 'PHOTON_SSR_FAR_FULL_TRACE_PROBE', 'metadata': 'shaderlod-ssr-far-full-trace-probe.json',
        'metadata_sha256': '30f3a1ffddbc8391a4f50536b8cd25b9e6982c826ac6156e90e5142da34c1c8b',
        'manifest_sha256': 'f801ab5eaa05268997ed9b705ed04839e1807e0f4250ea3a80d1f553c647442d'},
    '636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83': {'policy': 'reduced',
        'settings': 'a61e7f1c13a78fd3c6eb9ce72d9aec079b707b14c1ae2c3fb6251aa072de7299',
        'specular': '6db453ffc732600265d642d3fd4b7ff9a2c34fda66ec64010ec9f9e24afa471a',
        'switch': 'PHOTON_SSR_FAR_REDUCED_TRACE_PROBE', 'metadata': 'shaderlod-ssr-far-reduced-probe.json',
        'metadata_sha256': 'a05b241f23ccf5d40680061cf0d10ca73b6c8ae075fcdd721dedccd6ab286d60',
        'manifest_sha256': '86ebfa2c42b28571867caddc634df1667efb3d6abbc89c103a87a2f462c686fa'}}


# Frozen explicit Photon idle-work adapter contract, independently checked against actual Java exports.
SPATIAL_METHOD = 'One JVM/world/pipeline; pinned shared spatial Photon; fixed opaque ADAPTIVE policy; only live global bridge enabled changes; diagnostic incremental cost, OFF retains extra pass'
SPATIAL_IDENTITY = {'id': 'shaderlod:photon-shared-spatial',
    'revision': 'v1.3b-shared-spatial-cf9acc6617dbfdf388b0c54894914242270ce8e582eeef565e0a7bd08039139e'}
SPATIAL_OPERATION = {'effect': 'OPAQUE_LIGHTING', 'operation': 'shaderlod:opaque_spatial_irradiance', 'kind': 'RATE'}
SPATIAL_ARCHIVES = {
    'db5a81b57e584237cc6e2f47434215a0d33ae365dde1677a82d3420971c861b9': 'bf612686d1c9b79a71e3b1338600d67739fb5455a0612c2b1caea944e324d186',
    '50c134eca19cdd587cc8e317c57e68b4c76ace5c9cb155970f7640cf4345fc99': '2aec76ec624573ee793834b5849f1d0cb1f215aca188750a82650373bb02e981'}
SPATIAL_EFFECTS = ('SHADOW_FILTERING', 'AMBIENT_OCCLUSION', 'REFLECTIONS', 'VOLUMETRICS', 'WATER_SURFACE', 'MATERIALS', 'OPAQUE_LIGHTING')
SPATIAL_MIN_GPU_SAMPLES = 60  # Retained rejection-pilot coverage floor; short traces do not waive it.
SPATIAL_MEMBERS = {
    'LICENSE': '514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5',
    'shaders/include/buffers.glsl': 'c096d28c2c2c5232fea8770af133f6dec22901d6a3c3e25b951dede10be5672b',
    'shaders/include/lighting/shadows/pcss.glsl': 'fb5a28fd5594c5465e3b4e1b30ccbdee61957a6c86d8e64fd70f047d28a9db9f',
    'shaders/include/lighting/shadows/ssrt.glsl': '00b43e59666a8a7d1f35cb01c5463a4e6f624a1e7eeea8adc2d73810eb939a2c',
    'shaders/include/shaderlod/effect_policy.glsl': '5df5427a1cfa51e68446978040235061ab6db60ec3f5079d40ffa0c39bad6943',
    'shaders/include/shaderlod/photon_linear_basis.glsl': 'c3dcddf89570759be947be1e98d6c2c983cfa4948d0df2bd0b1c1099b660b5a2',
    'shaders/include/shaderlod/photon_original_sky.glsl': '830671927217110800d91f45c7f5074d20ea9820c03b783b068aaaf6efd8e629',
    'shaders/include/shaderlod/photon_spatial_adapter.glsl': 'f0e0c5917a061cf6d999b07c8c937424ad083104c694552d4f32050799f6bb78',
    'shaders/include/shaderlod/spatial_pipeline.glsl': '89706bd84d258058dad6e81f05e125aed5af3019ca5b303d879a802037a4d124',
    'shaders/include/shaderlod/spatial_plan.glsl': 'b3e92b9a26f59c03dc1b14a90e5ab40012fe38fc5b226664429f7a138bab46c9',
    'shaders/include/shaderlod/surface_region_continuity.glsl': '497c38b723df0255e9b07b5b62e695d644f0d0eb19538f14db5f95846e9cc040',
    'shaders/include/shaderlod/work_policy.glsl': '1b70d1b34959778ac34ae9316797c23d858531ff366b55373bae1c7ee55ee309',
    'shaders/program/d4_deferred_shading.fsh': '0c7bff3c6369df0601781ea08488e8a9cd2f6e7634e43b526ae2815c899a9def',
    'shaders/program/shaderlod_spatial_prepass.fsh': '034f7cc924608720dd760397b4f621579a08ccdfee3cf595a90f1444cbefabae',
    'shaders/shaders.properties': 'cd83f7009b5b254865468fd3a3d3b7bbf621d143833fe7f3780de438fa29db47',
    'shaders/world-1/deferred4.fsh': '965b7698e385459af2af916455b27f08b76583c694fa949138e62dd2cc61bad0',
    'shaders/world-1/deferred5.fsh': '91a5c585d8756d1ad085e1faf6d5419e929f1e00336da436a5bf77323b6c8c56',
    'shaders/world-1/deferred5.vsh': 'c076400a410a980af1f505ec6f9bf863518141014a302b55c919215773113097',
    'shaders/world0/deferred4.fsh': '70fe571207f38ead75d5223eab3088d3b11d0fa65894b64c73842e15069c5e69',
    'shaders/world0/deferred5.fsh': '7e2ad5e02559b740b661f7d5fdd92fe32e54f40ed84ab0f7dad0e626236e5b95',
    'shaders/world0/deferred5.vsh': '70e1a7a4657765cff24e50aa92be4ae481ea4994e961416cf7fe434ac0660187',
    'shaders/world1/deferred4.fsh': '0cd8f95ff73c14e130e83705d322d8fefdd9e9ade7ffc7950fa455f74e26d8a6',
    'shaders/world1/deferred5.fsh': '41246aedab1a3020c82bfb347b0a6efbeaca2a8c2b2efa199fca0c8f331c4cd0',
    'shaders/world1/deferred5.vsh': 'dd5d9becf6aae7cce4237fa2afcd339d30976457ff8f6500a7b844fdc7cd3975',
}
SPATIAL_REQUIRED_OPTIONS = {
    'BLOCKY_CLOUDS': ['false'],
    'BORDER_FOG': ['false'],
    'CLOUD_SHADOWS': ['false'],
    'COLORED_LIGHTS': ['false'],
    'ENVIRONMENT_REFLECTIONS': ['false'],
    'GALAXY': ['false'],
    'NORMAL_MAPPING': ['false'],
    'SHADERLOD_SHARED_SPATIAL': ['true'],
    'SHADOW_COLOR': ['false'],
    'SHADOW_VPS': ['false'],
    'SH_SKYLIGHT': ['false'],
    'SKY_REFLECTIONS': ['false'],
    'SPECULAR_MAPPING': ['false'],
    'TAAU': ['false'],
}


# Separate, explicit PhotonReferenceAdapter contract; volume never inherits spatial pins.
VOLUME_METHOD = 'One JVM/world/pipeline; registered shared volume Photon; fixed volumetrics ADAPTIVE policy; only live global bridge enabled changes; diagnostic incremental cost, OFF retains identical volume graph'
VOLUME_IDENTITY = {'id': 'shaderlod:photon-volume-integration', 'revision': 'photon-v1.3b/shared-work-v1'}
VOLUME_OPERATION = {'effect': 'VOLUMETRICS', 'operation': 'shaderlod:volume_integration', 'kind': 'COUNT'}
VOLUME_MANIFEST_SHA256 = '40efd7e702e41388033255d48a007c543e1055ccb4e2513c1908c2d82b8f2677'
VOLUME_ARCHIVES = {
    '5de7561dd24cdca80860a1d5f2c60400e74163a489cc163e916f3fd47feb27de': 'b9b1c432b484d190c036f7cf243059f1f5d33c5c2c0564a9592c5323e05dd348',
    '5f3772c8e97a89590b9a55a24d330de93543324befec6419aba9aed69991e5d3': 'e69f2812c0c1ef49eeeef9d43c2748cfb06d173a9dfa0ef7d77daec09bc76160'}
VOLUME_MEMBERS = {
    'LICENSE': '514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5',
    'shaders/world0/deferred1.fsh': '23fc8e1c4a3bad9bf11b68b9d73ffb71db922b9f0438c5991f048e51635edee8',
    'shaders/program/d1_clouds.fsh': '1f0dd330b837becff393380283eb767e3a55fe48e9b04705bf825fde535d0cae',
    'shaders/include/sky/clouds.glsl': '07bac05a93b29cff55841d6f7425fe46d1555fe1e6b2cfc037d9fb9fee992972',
    'shaders/include/sky/clouds/cumulus.glsl': '5290d3c0c56693608c6cfe8efec831b67023334729d4f549b3551f430603d8e2',
    'shaders/include/shaderlod/volume_budget.glsl': 'cf37ff0af1b3ebb6cc3f3f95ae83c708518a8e48d03545e8665962d14cf731ac',
    'shaders/include/shaderlod/photon_volume_adapter.glsl': '74349a36793081be528b34f9f047fc9ed195a8f2b12545caade0d4637af75905',
    'shaders/include/shaderlod/effect_policy.glsl': '5df5427a1cfa51e68446978040235061ab6db60ec3f5079d40ffa0c39bad6943',
    'shaders/include/shaderlod/work_policy.glsl': '1b70d1b34959778ac34ae9316797c23d858531ff366b55373bae1c7ee55ee309'}
VOLUME_REQUIRED_OPTIONS = {'SHADERLOD_VOLUMETRIC_INTEGRATION_LOD': ['true'], 'CLOUDS_CUMULUS': ['true'], 'BLOCKY_CLOUDS': ['false']}
VOLUME_MIN_GPU_SAMPLES = 60

def ssr_cost_members(pin):
    if pin not in SSR_COST_ARCHIVES: raise ValueError('Unknown SSR cost source intervention')
    record = SSR_COST_ARCHIVES[pin]
    members = SSR_COST_COMMON_MEMBERS | {'shaders/settings.glsl': record['settings'],
                                       'shaders/include/lighting/specular_lighting.glsl': record['specular']}
    if 'metadata' in record: members[record['metadata']] = record['metadata_sha256']
    return members


def validate_ssr_cost_qualification(qualification):
    if not isinstance(qualification, dict) or qualification.get('qualified') is not True: raise ValueError('SSR cost source qualification missing')
    pin = qualification.get('archive_sha256')
    members = ssr_cost_members(pin)
    record = SSR_COST_ARCHIVES[pin]
    if qualification.get('policy') != record['policy'] or qualification.get('member_sha256') != members:
        raise ValueError('SSR cost exact member/policy qualification mismatch')
    metadata = qualification.get('probe_metadata')
    if 'metadata' in record:
        if not isinstance(metadata, dict): raise ValueError('SSR cost private metadata missing')
        encoded = (json.dumps(metadata, indent=2, sort_keys=True) + '\n').encode()
        if hashlib.sha256(encoded).hexdigest() != record['metadata_sha256']: raise ValueError('SSR cost parsed private metadata fingerprint mismatch')
        if metadata.get('arm') != 'enabled' or metadata.get('switch_enabled') is not True or metadata.get('switch') != record['switch'] or metadata.get('base_sha256') != TERRAIN_PHOTON_SHA256 or metadata.get('manifest_sha256') != record['manifest_sha256']:
            raise ValueError('SSR cost private gate/provenance mismatch')
    elif metadata is not None:
        raise ValueError('SSR cost B15 cannot carry private probe metadata')
    return pin


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False, allow_nan=False)


def ordered_digest(value):
    # Spatial records hash the stored Gson object order, unlike the sorted Iris option map.
    encoded = json.dumps(value, separators=(',', ':'), ensure_ascii=False, allow_nan=False)
    encoded = encoded.replace('\u2028', '\\u2028').replace('\u2029', '\\u2029')
    return hashlib.sha256(encoded.encode()).hexdigest()


def spatial_source_binding(pin):
    if pin not in SPATIAL_ARCHIVES: raise ValueError('Unknown spatial source archive')
    return {'adapter': SPATIAL_IDENTITY, 'sourceSha256': pin,
            'memberSha256': SPATIAL_MEMBERS | {'shaders/settings.glsl': SPATIAL_ARCHIVES[pin]},
            'requiredOptions': SPATIAL_REQUIRED_OPTIONS}


def validate_spatial_state(state, pin):
    controls = state.get('spatial_effect_controls')
    if not isinstance(controls, dict) or set(controls) != set(SPATIAL_EFFECTS): raise ValueError('Complete spatial effect controls missing')
    if controls != state['loaded_semantic_config'].get('effects'): raise ValueError('Spatial loaded semantic effects differ')
    for name, control in controls.items():
        opaque = name == 'OPAQUE_LIGHTING'
        if not isinstance(control, dict) or set(control) != {'mode', 'requested', 'policy', 'repaired'}:
            raise ValueError('Spatial control shape differs')
        policy = control.get('policy')
        if not isinstance(policy, dict) or set(policy) != {'enabled', 'preserveSalientLighting', 'bands', 'mediumWorkRatio', 'farWorkRatio'}:
            raise ValueError('Spatial effect policy incomplete')
        if control.get('repaired') is not False or control.get('requested') != policy or control.get('mode') != ('ADAPTIVE' if opaque else 'INHERIT'):
            raise ValueError('Spatial repaired/mode/requested policy differs')
        if policy.get('enabled') is not opaque or policy.get('preserveSalientLighting') is not True or finite(policy.get('mediumWorkRatio'), 'medium ratio') != 1 or finite(policy.get('farWorkRatio'), 'far ratio') != (.25 if opaque else 1):
            raise ValueError('Spatial fixed ratios/protection differ')
        bands = policy.get('bands', {})
        if set(bands) != {'nearBlocks', 'farBlocks', 'transitionBlocks'}: raise ValueError('Spatial bands incomplete')
        near, far, width = [finite(bands.get(key), key) for key in ('nearBlocks', 'farBlocks', 'transitionBlocks')]
        if near <= 0 or far <= near or not 0 <= width <= min(near * 2, far - near): raise ValueError('Spatial invalid effect bands')
        if opaque and (near, far, width) != (32, 96, 8): raise ValueError('Spatial opaque bands differ')
    binding = state.get('spatial_adapter_binding', {})
    descriptor = binding.get('descriptor', {})
    if binding.get('reason') != 'AVAILABLE' or descriptor.get('identity') != SPATIAL_IDENTITY or binding.get('operations') != [SPATIAL_OPERATION]:
        raise ValueError('Spatial actual operation unavailable or descriptor identity differs')
    if descriptor.get('protocols') != [{'id': 'shaderlod:core', 'acceptedVersions': [2]}, {'id': 'shaderlod:effects', 'acceptedVersions': [2]}, {'id': 'shaderlod:capabilities', 'acceptedVersions': [1]}]:
        raise ValueError('Spatial exact protocol requirements differ')
    capabilities = descriptor.get('capabilities', [])
    if len(capabilities) != 1 or capabilities[0].get('key') != SPATIAL_OPERATION or sorted(capabilities[0].get('domain', {}).get('retainedRates', [])) != [.25, 1.0]:
        raise ValueError('Spatial exact rate capability differs')
    source = state.get('spatial_source_qualification', {})
    declared = spatial_source_binding(pin)
    if source.get('qualified') is not True or source.get('source_binding') != declared: raise ValueError('Spatial exact declared source binding differs')
    observed = source.get('source_fingerprint', {})
    if observed.get('sourceSha256') != pin or not isinstance(observed.get('memberSha256'), dict): raise ValueError('Spatial observed source fingerprint missing/different')
    for name, value in observed['memberSha256'].items():
        if not isinstance(name, str) or not name or name.startswith('/') or '\\' in name or any(part in ('', '.', '..') for part in name.split('/')): raise ValueError('Spatial observed source member path invalid')
        require_sha(value)
    if any(observed['memberSha256'].get(name) != value for name, value in declared['memberSha256'].items()): raise ValueError('Spatial observed consuming source member differs')
    options = state['effective_shader_options']
    if not isinstance(options, dict) or any(not isinstance(key, str) or not isinstance(value, str) for key, value in options.items()): raise ValueError('Spatial full string-valued options missing')
    if any(options.get(name) not in values for name, values in SPATIAL_REQUIRED_OPTIONS.items()): raise ValueError('Spatial exact loaded source options differ')
    for field in ('spatial_effect_controls', 'spatial_adapter_binding', 'spatial_source_qualification'):
        if require_sha(state.get(field + '_sha256')) != ordered_digest(state[field]): raise ValueError('Spatial stored object fingerprint mismatch: ' + field)
    require_sha(state.get('config/shaderlod-effects-client.toml_sha256'))
    for field in ('shadow_coherence_configured', 'shadow_coherence_active'):
        if state.get(field) is not False: raise ValueError('Spatial coherence must stay off')
    if state.get('spatial_dh_present') is not False: raise ValueError('Spatial diagnostic requires actual DH absence')
    if state.get('spatial_camera_type') != 'FIRST_PERSON' or integer(state.get('spatial_fov'), 'spatial FOV') < 1: raise ValueError('Spatial fixed first-person/FOV missing')
    if state.get('requested_chunks') != 24 or state.get('effective_chunks') != 24: raise ValueError('Spatial fixed radius24 differs')
    window = state.get('spatial_window_state', {})
    if window.get('availability') != 'available' or window.get('focused') not in ('true', 'false') or window.get('visible') != 'true' or window.get('iconified') != 'false': raise ValueError('Spatial window unknown/hidden')
    for name in ('framebuffer_width', 'framebuffer_height'):
        if integer(window.get(name), name) != state.get(name): raise ValueError('Spatial window framebuffer differs')
    for name in ('window_width', 'window_height'):
        if integer(window.get(name), name) < 1: raise ValueError('Spatial window extent unavailable')
    pacing = window.get('pacing', {})
    if pacing.get('vsync') is not False or not isinstance(pacing.get('pause_on_lost_focus'), bool) or not isinstance(pacing.get('fullscreen'), bool) or integer(pacing.get('configured_framerate_limit'), 'frame limit') < 1: raise ValueError('Spatial pacing unavailable')
    if pacing['pause_on_lost_focus'] and window['focused'] != 'true': raise ValueError('Spatial unfocused with focus pause')


def volume_source_binding(pin):
    if pin not in VOLUME_ARCHIVES: raise ValueError('Unknown volume source archive')
    return {'adapter': VOLUME_IDENTITY, 'sourceSha256': pin,
            'memberSha256': VOLUME_MEMBERS | {'shaders/settings.glsl': VOLUME_ARCHIVES[pin]},
            'requiredOptions': VOLUME_REQUIRED_OPTIONS}


def validate_volume_state(state, pin):
    controls = state.get('volume_effect_controls')
    if not isinstance(controls, dict) or set(controls) != set(SPATIAL_EFFECTS): raise ValueError('Complete volume effect controls missing')
    if controls != state['loaded_semantic_config'].get('effects'): raise ValueError('Volume loaded semantic effects differ')
    for name, control in controls.items():
        selected = name == 'VOLUMETRICS'
        if not isinstance(control, dict) or set(control) != {'mode', 'requested', 'policy', 'repaired'}:
            raise ValueError('Volume control shape differs')
        policy = control.get('policy')
        if not isinstance(policy, dict) or set(policy) != {'enabled', 'preserveSalientLighting', 'bands', 'mediumWorkRatio', 'farWorkRatio'}:
            raise ValueError('Volume effect policy incomplete')
        if control.get('repaired') is not False or control.get('requested') != policy or control.get('mode') != ('ADAPTIVE' if selected else 'INHERIT'):
            raise ValueError('Volume repaired/mode/requested policy differs')
        if policy.get('enabled') is not selected or policy.get('preserveSalientLighting') is not True or finite(policy.get('mediumWorkRatio'), 'medium ratio') != (.85 if selected else 1) or finite(policy.get('farWorkRatio'), 'far ratio') != (.75 if selected else 1):
            raise ValueError('Volume fixed ratios/protection differ')
        bands = policy.get('bands', {})
        if set(bands) != {'nearBlocks', 'farBlocks', 'transitionBlocks'}: raise ValueError('Volume bands incomplete')
        near, far, width = [finite(bands.get(key), key) for key in ('nearBlocks', 'farBlocks', 'transitionBlocks')]
        if near <= 0 or far <= near or not 0 <= width <= min(near * 2, far - near): raise ValueError('Volume invalid effect bands')
        if selected and (near, far, width) != (512, 1536, 128): raise ValueError('Volume selected bands differ')
    binding = state.get('volume_adapter_binding', {})
    descriptor = binding.get('descriptor', {})
    if binding.get('reason') != 'AVAILABLE' or descriptor.get('identity') != VOLUME_IDENTITY or binding.get('operations') != [VOLUME_OPERATION]:
        raise ValueError('Volume actual operation unavailable or descriptor identity differs')
    protocols = copy.deepcopy(descriptor.get('protocols'))
    if not isinstance(protocols, list): raise ValueError('Volume protocol requirements missing')
    for protocol in protocols:
        versions = protocol.get('acceptedVersions') if isinstance(protocol, dict) else None
        if not isinstance(versions, list) or any(not isinstance(x, int) or isinstance(x, bool) for x in versions): raise ValueError('Volume protocol versions missing/invalid')
        protocol['acceptedVersions'] = sorted(versions)
    if protocols != [{'id': 'shaderlod:core', 'acceptedVersions': [2]}, {'id': 'shaderlod:effects', 'acceptedVersions': [1, 2]}, {'id': 'shaderlod:capabilities', 'acceptedVersions': [1]}]:
        raise ValueError('Volume exact protocol requirements differ')
    capabilities = descriptor.get('capabilities', [])
    if len(capabilities) != 1 or capabilities[0].get('key') != VOLUME_OPERATION or capabilities[0].get('domain') != {'minimumRetainedWork': 8}:
        raise ValueError('Volume exact count capability differs')
    source = state.get('volume_source_qualification', {})
    declared = volume_source_binding(pin)
    if source.get('qualified') is not True or source.get('source_binding') != declared: raise ValueError('Volume exact declared source binding differs')
    observed = source.get('source_fingerprint', {})
    if observed.get('sourceSha256') != pin or not isinstance(observed.get('memberSha256'), dict): raise ValueError('Volume observed source fingerprint missing/different')
    for name, value in observed['memberSha256'].items():
        if not isinstance(name, str) or not name or name.startswith('/') or '\\' in name or any(part in ('', '.', '..') for part in name.split('/')): raise ValueError('Volume observed source member path invalid')
        require_sha(value)
    if any(observed['memberSha256'].get(name) != value for name, value in declared['memberSha256'].items()): raise ValueError('Volume observed consuming source member differs')
    options = state['effective_shader_options']
    if not isinstance(options, dict) or any(not isinstance(key, str) or not isinstance(value, str) for key, value in options.items()): raise ValueError('Volume full string-valued options missing')
    if any(options.get(name) not in values for name, values in VOLUME_REQUIRED_OPTIONS.items()): raise ValueError('Volume exact loaded source options differ')
    for field in ('volume_effect_controls', 'volume_adapter_binding', 'volume_source_qualification'):
        if require_sha(state.get(field + '_sha256')) != ordered_digest(state[field]): raise ValueError('Volume stored object fingerprint mismatch: ' + field)
    require_sha(state.get('config/shaderlod-effects-client.toml_sha256'))
    for field in ('shadow_coherence_configured', 'shadow_coherence_active'):
        if state.get(field) is not False: raise ValueError('Volume coherence must stay off')
    if state.get('volume_dh_present') is not False: raise ValueError('Volume diagnostic requires actual DH absence')
    if state.get('volume_camera_type') != 'FIRST_PERSON' or integer(state.get('volume_fov'), 'volume FOV') < 1: raise ValueError('Volume fixed first-person/FOV missing')
    if state.get('requested_chunks') != 24 or state.get('effective_chunks') != 24: raise ValueError('Volume fixed radius24 differs')
    window = state.get('volume_window_state', {})
    if window.get('availability') != 'available' or window.get('focused') not in ('true', 'false') or window.get('visible') != 'true' or window.get('iconified') != 'false': raise ValueError('Volume window unknown/hidden')
    for name in ('framebuffer_width', 'framebuffer_height'):
        if integer(window.get(name), name) != state.get(name): raise ValueError('Volume window framebuffer differs')
    for name in ('window_width', 'window_height'):
        if integer(window.get(name), name) < 1: raise ValueError('Volume window extent unavailable')
    pacing = window.get('pacing', {})
    if pacing.get('vsync') is not False or not isinstance(pacing.get('pause_on_lost_focus'), bool) or not isinstance(pacing.get('fullscreen'), bool) or integer(pacing.get('configured_framerate_limit'), 'frame limit') < 1: raise ValueError('Volume pacing unavailable')
    if pacing['pause_on_lost_focus'] and window['focused'] != 'true': raise ValueError('Volume unfocused with focus pause')


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''): result.update(block)
    return result.hexdigest()


def require_uuid(value):
    try: uuid.UUID(value)
    except (ValueError, TypeError, AttributeError): raise ValueError('Invalid session/phase UUID') from None


def integer(value, name):
    if not isinstance(value, int) or isinstance(value, bool) or value < 0: raise ValueError('Invalid integer: ' + name)
    return value


def finite(value, name):
    if not isinstance(value, (int, float)) or isinstance(value, bool) or not math.isfinite(value): raise ValueError('Invalid finite number: ' + name)
    return value


def require_sha(value):
    if not isinstance(value, str) or len(value) != 64 or any(character not in '0123456789abcdef' for character in value):
        raise ValueError('Exact lowercase SHA256 required')
    return value


def semantic_state(state, strip_enabled=False, feature='global'):
    result = copy.deepcopy(state)
    semantic = result.get('loaded_semantic_config', {})
    for section, fields in CONFIG_FIELDS.items():
        required = fields - {'enabled'} if section == 'lod' and strip_enabled and feature in ('global', 'effects', 'ssr_cost', 'spatial', 'volume') else fields
        if section == 'terrain' and strip_enabled and feature == 'terrain': required = required - {'enabled'}
        if section == 'rendering' and feature == 'coherence' and not strip_enabled: required = required | {'shadowFrustumCoherence'}
        if section == 'rendering' and feature in ('effects', 'terrain', 'ssr_cost', 'spatial', 'volume'): required = required | {'shadowFrustumCoherence'}
        if not required <= semantic.get(section, {}).keys(): raise ValueError('Incomplete loaded semantic config: ' + section)
    result.pop('config/shaderlod-client.toml_sha256', None)
    if state.get('original_null_control') is True:
        normalized = original_null_configuration_semantics(state)
        if normalized != state.get('original_null_configuration_semantics'): raise ValueError('Original NULL independent normalization differs')
        result['configuration_files'] = normalized
        result.pop('config/iris.properties_sha256', None)
    if feature == 'terrain':
        policy = result.get('geometry_policy')
        if not isinstance(policy, dict): raise ValueError('Actual terrain boundary policy missing')
        policy.pop('stableTicks', None)  # Real eligibility ticks advance during a stable ON capture.
    if strip_enabled:
        if feature in ('global', 'effects', 'ssr_cost', 'spatial', 'volume'):
            result.get('lod_settings', {}).pop('enabled', None)
            semantic['lod'].pop('enabled', None)
        elif feature == 'terrain':
            for field in ('effective_chunks', 'terrain_configured', 'terrain_active'): result.pop(field, None)
            semantic['terrain'].pop('enabled', None)
            for field in ('active', 'effectiveChunks', 'reason'): result['geometry_policy'].pop(field, None)
        else:
            result.pop('shadow_coherence_configured', None)
            result.pop('shadow_coherence_active', None)
            semantic['rendering'].pop('shadowFrustumCoherence', None)
    return result


def validate_state(state, enabled, shader_hash, feature='global', original_null=False):
    semantic_state(state, feature=feature)
    if state.get('mod_present') is not True or state.get('shaders_in_use') is not True or state.get('fallback') is not False:
        raise ValueError('Main mod or live Iris pipeline missing')
    if state.get('hide_gui') is not True: raise ValueError('Expected fixed hidden HUD')
    global_enabled = enabled if feature in ('global', 'effects', 'ssr_cost', 'spatial', 'volume') else True
    if state.get('lod_settings', {}).get('enabled') is not global_enabled or state['loaded_semantic_config']['lod'].get('enabled') is not global_enabled:
        raise ValueError('Wrong live global flag in phase')
    if state['lod_settings'].get('debug') is not False: raise ValueError('Unexpected debug')
    semantic = state['loaded_semantic_config']
    sections = (('rendering', ('optimizeShadowCulling', 'separateShadowVisibilityLists')),)
    if feature != 'terrain': sections += (('terrain', ('enabled', 'warmSourceConfirmed')),)
    for section, fields in sections:
        if any(semantic[section][field] is not False for field in fields): raise ValueError('Semantic feature config not disabled')
    if semantic['lod']['debug'] is not False: raise ValueError('Semantic debug not disabled')
    for field in ('near', 'far', 'transition'): finite(semantic['lod'][field], 'semantic ' + field)
    for field in FALSE_FLAGS:
        if feature == 'terrain' and field.startswith('terrain_'): continue
        if state.get(field) is not False: raise ValueError('Unexpected feature activity/config: ' + field)
    if state.get('actual_shader_sha256') != shader_hash: raise ValueError('Actual shader archive differs from pin')
    options = state.get('effective_shader_options', {})
    if not options: raise ValueError('Full effective options missing')
    if original_null:
        if feature != 'global' or state.get('original_null_control') is not True or shader_hash != ORIGINAL_PHOTON_SHA256:
            raise ValueError('Explicit pinned original NULL state required')
        if not isinstance(options, dict) or len(options) != 366 or any(not isinstance(k, str) or not isinstance(v, str) or 'shaderlod' in k.lower() for k, v in options.items()) or hashlib.sha256(canonical(options).encode()).hexdigest() != ORIGINAL_NULL_OPTIONS_SHA256:
            raise ValueError('Exact complete original 366-option map required')
        for name in ('shadow_coherence_configured', 'shadow_coherence_active'):
            if state.get(name) is not False: raise ValueError('Original NULL coherence consumer active/missing')
        if semantic['rendering'].get('shadowFrustumCoherence') is not False: raise ValueError('Original NULL coherence config active/missing')
    elif feature in ('global', 'effects', 'terrain', 'ssr_cost'):
        if feature in ('effects', 'terrain', 'ssr_cost'):
            if not isinstance(options, dict) or any(not isinstance(key, str) or not isinstance(value, str) for key, value in options.items()):
                raise ValueError('Full string-valued effective option map required')
            for name in EFFECT_OPTIONS:
                if options.get(name) not in ('true', 'false'): raise ValueError('Complete boolean effect option required: ' + name)
        for name in OFF_OPTIONS if feature == 'global' else OFF_OPTIONS[5:]:
            if options.get(name) != 'false': raise ValueError('Alloff option mismatch: ' + name)
        for name in ('SHADERLOD_USE_MOD_CONFIG', 'SHADERLOD_PRESERVE_LIGHTING'):
            if options.get(name) != 'true': raise ValueError('Bridge/lighting option mismatch: ' + name)
        for name in ('shadow_coherence_configured', 'shadow_coherence_active'):
            if name in state and state[name] is not False: raise ValueError('Coherence active in global control')
        if 'shadowFrustumCoherence' in semantic['rendering'] and semantic['rendering']['shadowFrustumCoherence'] is not False:
            raise ValueError('Coherence config enabled in global control')
        if feature in ('effects', 'terrain', 'ssr_cost'):
            for name in ('shadow_coherence_configured', 'shadow_coherence_active'):
                if state.get(name) is not False: raise ValueError('Coherence must stay off in effects control')
        if feature == 'ssr_cost': validate_ssr_cost_state(state, shader_hash)
        if feature == 'terrain':
            validate_terrain_prerequisites(state)
            for field in ('terrain_configured', 'terrain_active'):
                if state.get(field) is not enabled: raise ValueError('Terrain activity/config differs from selected phase')
            if semantic['terrain'].get('enabled') is not enabled: raise ValueError('Wrong selected terrain semantic flag')
            radius = 16 if enabled else 24
            if integer(state.get('effective_chunks'), 'effective_chunks') != radius: raise ValueError('Terrain phase must be effective16 ON /24 OFF')
            policy = state['geometry_policy']
            if policy.get('active') is not enabled or integer(policy.get('effectiveChunks'), 'policy effectiveChunks') != radius:
                raise ValueError('Actual terrain policy does not match phase')
            if policy.get('reason') != ('DH terrain cap active' if enabled else 'terrain policy disabled'):
                raise ValueError('Terrain eligibility reason differs')
            ticks = integer(policy.get('stableTicks'), 'stableTicks')
            if (ticks < 600 if enabled else ticks != 0): raise ValueError('Terrain policy warming/not settled')
    elif feature == 'spatial':
        validate_spatial_state(state, shader_hash)
    elif feature == 'volume':
        validate_volume_state(state, shader_hash)
    else:
        if shader_hash != ORIGINAL_PHOTON_SHA256: raise ValueError('Coherence requires exact original Photon')
        for name in ('shadow_coherence_configured', 'shadow_coherence_active'):
            if state.get(name) is not enabled: raise ValueError('Coherence settled activity/config mismatch')
        if semantic['rendering'].get('shadowFrustumCoherence') is not enabled: raise ValueError('Coherence semantic config mismatch')
    if state.get('effective_shader_options_sha256') != hashlib.sha256(canonical(options).encode()).hexdigest():
        raise ValueError('Effective option fingerprint mismatch')
    for field in ('x', 'y', 'z', 'yaw', 'pitch', 'day_time', 'rain', 'thunder'):
        finite(state.get(field), field)
    for field in ('framebuffer_width', 'framebuffer_height', 'requested_chunks', 'effective_chunks', 'gui_scale'):
        if integer(state.get(field), field) < 1: raise ValueError('Nonpositive input: ' + field)


def validate_terrain_prerequisites(state):
    if state.get('actual_shader_sha256') != TERRAIN_PHOTON_SHA256: raise ValueError('Terrain control requires exact B15 archive')
    if state.get('dh_present') is not True or state.get('terrain_confirmation') is not True: raise ValueError('Terrain requires DH and explicit manual acknowledgement')
    if state.get('mod_present') is not True or state.get('shaders_in_use') is not True or state.get('fallback') is not False or state.get('hide_gui') is not True:
        raise ValueError('Terrain mod/pipeline/hidden HUD evidence missing')
    semantic = state.get('loaded_semantic_config', {})
    if state.get('lod_settings', {}).get('enabled') is not True or semantic.get('lod', {}).get('enabled') is not True:
        raise ValueError('Global enable must remain true in terrain control')
    terrain = semantic.get('terrain', {})
    if terrain.get('warmSourceConfirmed') is not True or integer(terrain.get('vanillaChunkCap'), 'cap config') != 16 or integer(terrain.get('warmupSeconds'), 'policy warmup') != 30:
        raise ValueError('Terrain requires fixed cap16/manual acknowledgement/30s policy warmup')
    if integer(state.get('requested_chunks'), 'requested_chunks') != 24: raise ValueError('Terrain requested radius must remain24')
    if state.get('terrain_camera_type') != 'FIRST_PERSON' or integer(state.get('terrain_fov'), 'terrain FOV') < 1:
        raise ValueError('Terrain fixed first-person camera/FOV evidence missing')
    options = state.get('effective_shader_options', {})
    for name in EFFECT_OPTIONS:
        if options.get(name) != 'true': raise ValueError('Terrain requires all five fixed effects active')
    for name in ('SHADERLOD_USE_MOD_CONFIG', 'SHADERLOD_PRESERVE_LIGHTING'):
        if options.get(name) != 'true': raise ValueError('Terrain bridge/lighting protection changed')
    for name in OFF_OPTIONS[5:]:
        if options.get(name) != 'false': raise ValueError('Terrain debug/cache/analytic option changed')
    for name in FALSE_FLAGS[3:] + ('shadow_coherence_configured', 'shadow_coherence_active'):
        if state.get(name) is not False: raise ValueError('Terrain CPU experiment active/missing')
    if semantic.get('rendering', {}).get('shadowFrustumCoherence') is not False: raise ValueError('Terrain coherence config changed')
    dh = state.get('terrain_dh_state', {})
    if dh.get('world_loaded') is not True or dh.get('rendering_enabled') is not True or dh.get('rendering_mode') != 'DEFAULT' or integer(dh.get('chunks'), 'DH reach') != 128:
        raise ValueError('Actual DH128 normal-world readiness missing')
    policy = state.get('geometry_policy')
    if not isinstance(policy, dict): raise ValueError('Actual boundary terrain policy missing')
    for name, expected in (('cap', 16), ('requestedChunks', 24), ('warmupTicks', 600)):
        if integer(policy.get(name), 'policy ' + name) != expected: raise ValueError('Terrain policy pin differs: ' + name)
    ticks = integer(policy.get('stableTicks'), 'observed policy stableTicks')
    if not isinstance(policy.get('active'), bool) or state.get('terrain_active') is not policy['active']:
        raise ValueError('Actual terrain policy/boundary activity inconsistent')
    radius = 16 if policy['active'] else 24
    if integer(policy.get('effectiveChunks'), 'observed policy effectiveChunks') != radius or integer(state.get('effective_chunks'), 'observed effective radius') != radius or (policy['active'] and ticks < 600):
        raise ValueError('Actual terrain policy/radius/warmup inconsistent')
    if policy.get('detail') != '': raise ValueError('Terrain policy API/readiness failure')
    window = state.get('terrain_window_state', {})
    if window.get('availability') != 'available' or window.get('visible') != 'true' or window.get('iconified') != 'false' or window.get('focused') not in ('true', 'false'):
        raise ValueError('Actual terrain window evidence unavailable/hidden')
    for name, expected in (('framebuffer_width', 2976), ('framebuffer_height', 1672)):
        if integer(state.get(name), name) != expected or integer(window.get(name), name) != expected:
            raise ValueError('Terrain native framebuffer differs')
    pacing = window.get('pacing', {})
    if pacing.get('vsync') is not False or not isinstance(pacing.get('fullscreen'), bool) or not isinstance(pacing.get('pause_on_lost_focus'), bool) or integer(pacing.get('configured_framerate_limit'), 'frame limit') < 1:
        raise ValueError('Terrain pacing evidence missing/changed')
    if pacing['pause_on_lost_focus'] and window['focused'] != 'true': raise ValueError('Terrain unfocused with focus pause')
    for name in ('window_width', 'window_height'):
        if integer(window.get(name), name) < 1: raise ValueError('Terrain logical window dimensions missing')


def validate_terrain_phase(report, phase, reference):
    settle = finite(phase.get('settleElapsedSeconds'), 'actual terrain settle')
    minimum = reference['loaded_semantic_config']['terrain']['warmupSeconds'] + 30
    if settle < report['settle_seconds'] or settle < minimum: raise ValueError('Terrain phase settling too short')
    rearm = phase.get('terrainRearm')
    if phase['enabled']:
        if not isinstance(rearm, dict) or integer(rearm.get('attempts'), 'terrain re-arm attempts') != 1 or rearm.get('succeeded') is not True:
            raise ValueError('Terrain ON missing/succeeded once-only manual re-arm evidence')
        if rearm.get('scene_identifier') != report['terrain_scene_identifier'] or rearm.get('api') != 'GeometryLodController.confirmCurrentScene':
            raise ValueError('Terrain manual re-arm scene/API identity mismatch')
        delay = finite(rearm.get('elapsed_settle_seconds'), 'terrain re-arm delay')
        if delay < 0 or settle - delay < minimum: raise ValueError('Terrain insufficient post-rearm settling')
        try:
            at_utc = datetime.fromisoformat(rearm['at_utc'].replace('Z', '+00:00'))
            if at_utc.tzinfo is None: raise ValueError()
        except (KeyError, ValueError, TypeError, AttributeError):
            raise ValueError('Terrain manual re-arm timestamp missing/invalid') from None
        observed = rearm.get('observed_input', {})
        validate_terrain_prerequisites(observed)
        if observed.get('terrain_configured') is not True or observed['loaded_semantic_config']['terrain'].get('enabled') is not True:
            raise ValueError('Terrain ON not configured before manual re-arm')
        if observed['geometry_policy'].get('reason') not in ('warming DH scene', 'DH replacement coverage unconfirmed', 'DH terrain cap active'):
            raise ValueError('Terrain re-arm occurred before eligible fixed scene')
        if canonical(semantic_state(observed, True, 'terrain')) != canonical(reference): raise ValueError('Terrain re-arm scene inputs changed')
    elif rearm is not None:
        raise ValueError('Terrain OFF phase must not manually re-arm')
    if phase['enabled'] and phase['inputEnd']['geometry_policy']['stableTicks'] < phase['inputStart']['geometry_policy']['stableTicks']:
        raise ValueError('Terrain eligibility counter reset during capture')
    validate_fixed_window(phase, 'terrain_window_state')


def validate_fixed_window(phase, state_field):
    window = phase.get('windowState', {})
    if window.get('schema') != 1: raise ValueError('Terrain window observation schema missing')
    capture = window.get('capture', {})
    count = integer(capture.get('observations'), 'terrain window observations')
    if count < 1 or integer(capture.get('available_observations'), 'available window observations') != count or integer(capture.get('unavailable_observations'), 'unavailable window observations') != 0 or integer(capture.get('dropped_transitions'), 'dropped transitions') != 0 or capture.get('transition_counts') != {} or capture.get('transitions') != []:
        raise ValueError('Terrain capture window/pacing transition or missing observations')
    for name in ('start', 'end'):
        if capture.get(name, {}).get('state') != phase['inputStart'][state_field]:
            raise ValueError('Terrain window tracker/boundary identity differs')
    state = phase['inputStart'][state_field]
    for flag in ('focused', 'iconified', 'visible'):
        counts = capture.get(flag, {})
        expected_true = count if state[flag] == 'true' else 0
        if integer(counts.get('true_observations'), flag + ' true') != expected_true or integer(counts.get('false_observations'), flag + ' false') != count - expected_true or integer(counts.get('unknown_observations'), flag + ' unknown') != 0:
            raise ValueError('Terrain window flag changed/unknown between boundaries')


def validate_ssr_cost_state(state, pin):
    if pin not in SSR_COST_ARCHIVES: raise ValueError('Unknown SSR cost archive')
    if state.get('ssr_cost_dh_present') is not False: raise ValueError('SSR cost requires actual DH absence')
    for name in ('requested_chunks', 'effective_chunks'):
        if integer(state.get(name), name) != 24: raise ValueError('SSR cost radius must remain24')
    for name, target in (('framebuffer_width', 2976), ('framebuffer_height', 1672)):
        if integer(state.get(name), name) != target: raise ValueError('SSR cost native framebuffer mismatch')
    if state.get('ssr_cost_camera_type') != 'FIRST_PERSON' or integer(state.get('ssr_cost_fov'), 'SSR cost FOV') < 1:
        raise ValueError('SSR cost fixed first-person/FOV evidence missing')
    for name, target in (('x', 500.5), ('y', 68), ('z', 660.5), ('yaw', 87.38), ('pitch', 3.735), ('rain', 0), ('thunder', 0)):
        if abs(finite(state.get(name), name) - target) > .002: raise ValueError('SSR cost fixed sun-glint pose/weather mismatch')
    if integer(state.get('day_time'), 'day_time') != 12500 or state.get('dimension') != 'minecraft:overworld':
        raise ValueError('SSR cost fixed sun/time/dimension mismatch')
    options = state['effective_shader_options']
    for name in EFFECT_OPTIONS:
        if options.get(name) != ('true' if name == 'SHADERLOD_SSR' else 'false'): raise ValueError('SSR cost requires only SSR active')
    for name in ('ENVIRONMENT_REFLECTIONS', 'SKY_REFLECTIONS'):
        if options.get(name) != 'true': raise ValueError('SSR cost actual reflections disabled')
    selected = SSR_COST_ARCHIVES[pin].get('switch')
    for name in ('PHOTON_SSR_FAR_FULL_TRACE_PROBE', 'PHOTON_SSR_FAR_REDUCED_TRACE_PROBE'):
        if name == selected:
            if options.get(name) != 'true': raise ValueError('SSR cost selected private gate disabled/missing')
        elif name in options:
            raise ValueError('SSR cost unexpected private gate')
    window = state.get('ssr_cost_window_state', {})
    if window.get('availability') != 'available' or window.get('visible') != 'true' or window.get('iconified') != 'false' or window.get('focused') not in ('true', 'false'):
        raise ValueError('SSR cost window unknown/hidden')
    for name in ('framebuffer_width', 'framebuffer_height'):
        if integer(window.get(name), name) != state[name]: raise ValueError('SSR cost window/buffer evidence mismatch')
    pacing = window.get('pacing', {})
    if pacing.get('vsync') is not False or not isinstance(pacing.get('pause_on_lost_focus'), bool) or not isinstance(pacing.get('fullscreen'), bool) or integer(pacing.get('configured_framerate_limit'), 'frame limit') < 1:
        raise ValueError('SSR cost pacing unknown/changed')
    if pacing['pause_on_lost_focus'] and window['focused'] != 'true': raise ValueError('SSR cost unfocused with focus pause')


def validate_samples(samples, epoch, first_frame, last_frame, fields):
    if samples:
        integer(epoch, 'timing epoch')
    elif epoch is not None:
        raise ValueError('Declared epoch without timing observations')
    for sample in samples:
        if integer(sample.get('epoch'), 'sample epoch') != epoch: raise ValueError('Mixed/mismatched timing epoch')
        frame = integer(sample.get('frameId'), 'sample frame')
        if not first_frame < frame <= last_frame: raise ValueError('Timing sample outside phase frame range')
        if not isinstance(sample.get('scope'), str) or not sample['scope']: raise ValueError('Invalid timing scope')
        for field in fields:
            value = sample.get(field)
            if value is None and field != 'threadMilliseconds': raise ValueError('Required timing value missing: ' + field)
            if value is not None and finite(value, field) < 0: raise ValueError('Negative timing sample')


def metric(streams, scope, name='mean_ms'):
    return (streams.get(scope, {}).get('per_sampled_frame') or {}).get(name)


def validate_temporal(phase):
    targets = []
    for key in ('temporalStart', 'temporalEnd'):
        temporal = phase.get(key)
        if not isinstance(temporal, dict): raise ValueError('Temporal boundary evidence missing')
        wetness = temporal.get('wetness', {})
        if wetness.get('mode') != 'fixed': raise ValueError('Fixed shader weather evidence missing')
        target = finite(wetness.get('fixedWetness'), 'fixed wetness')
        if not 0 <= target <= 1: raise ValueError('Fixed wetness outside [0,1]')
        targets.append(target)
        observed = [supplier for supplier in wetness.get('suppliers', []) if supplier.get('supplierReads', 0) > 0]
        if not observed: raise ValueError('Observed wetness suppliers missing')
        for supplier in observed:
            if abs(finite(supplier.get('appliedWetness'), 'applied wetness') - target) > 1e-6: raise ValueError('Applied wetness mismatch')
            if key == 'temporalEnd' and supplier.get('measurementReads', 0) > 0:
                for field in ('appliedMin', 'appliedMax'):
                    if abs(finite(supplier.get(field), field) - target) > 1e-6: raise ValueError('Wetness varied during phase')
        if key == 'temporalEnd' and wetness.get('measurementReads', 0) < 1: raise ValueError('No capture wetness observations')
    if targets[0] != targets[1]: raise ValueError('Fixed wetness target changed')


def time_reduction(off, on):
    if off is None or on is None or off <= 0: return None
    return 100 * (off - on) / off


def validate_spatial_artifacts(artifacts, expected_directories=None):
    if not isinstance(artifacts, dict) or not artifacts: raise ValueError('Spatial loaded artifact identities missing')
    observed_directories = {}
    for name, value in artifacts.items():
        if not isinstance(name, str) or not name: raise ValueError('Spatial artifact path missing')
        if isinstance(value, str):
            require_sha(value)  # Actual regular-file/JAR entries keep the existing scalar schema.
            continue
        if not isinstance(value, dict) or set(value) != {'kind', 'complete', 'files', 'bytes', 'tree_sha256'}:
            raise ValueError('Spatial loaded directory identity schema differs')
        if value['kind'] != 'development directory; regular files only, symlinks excluded' or value['complete'] is not True:
            raise ValueError('Spatial loaded directory identity incomplete/unknown')
        count, size = integer(value['files'], 'directory file count'), integer(value['bytes'], 'directory bytes')
        if not 1 <= count <= 5000 or not 1 <= size <= 32 * 1024 * 1024: raise ValueError('Spatial loaded directory outside complete tree bounds')
        require_sha(value['tree_sha256'])
        observed_directories[name] = value
    if observed_directories:
        if not isinstance(expected_directories, dict) or not expected_directories: raise ValueError('Spatial loaded directories require frozen external artifact receipt')
        if any(expected_directories.get(name) != value for name, value in observed_directories.items()):
            raise ValueError('Spatial actual loaded directory identity differs from frozen receipt')



def validate_volume_artifacts(report, expected_directories, expected_inventory):
    """Qualify every root against independently pinned content/ancestry.

    Empty virtual path slots may collide in the retained legacy map. Direct
    per-root hashes and raw filesystem locators identify those roots. Only the
    source-proven nested mount counter may differ from the external receipt.
    """
    from qualify_volume_fast_artifacts import artifact_locator_matches, inventory_group_alternatives
    artifacts = report.get('loaded_artifacts_sha256')
    inventory = report.get('loaded_artifact_inventory')
    if not isinstance(artifacts, dict) or not artifacts or not isinstance(inventory, list) or not inventory:
        raise ValueError('Volume complete actual loaded artifact inventory missing')
    if not isinstance(expected_inventory, list) or not expected_inventory:
        raise ValueError('Volume requires independent complete frozen loaded artifact inventory receipt')
    observed_paths, observed_locators, observed_mods, virtual = set(), set(), {}, []
    matched_expected = set()
    for record in inventory:
        if not isinstance(record, dict) or set(record) != {'mods', 'path', 'uri', 'filesystem_provider', 'identity'}:
            raise ValueError('Volume artifact inventory unknown/incomplete locator schema')
        path, uri, provider, mods = [record[name] for name in ('path', 'uri', 'filesystem_provider', 'mods')]
        if not isinstance(path, str): raise ValueError('Volume artifact inventory missing path slot')
        if not isinstance(provider, str) or not provider or not isinstance(uri, str) or not uri.startswith(provider + ':') or uri == provider + ':':
            raise ValueError('Volume artifact inventory unidentified filesystem locator')
        locator = (provider, uri)
        if locator in observed_locators: raise ValueError('Volume artifact inventory duplicate filesystem locator')
        observed_locators.add(locator)
        if not isinstance(mods, dict) or not mods or any(not isinstance(k, str) or not k or not isinstance(v, str) or not v for k, v in mods.items()):
            raise ValueError('Volume artifact inventory mod identities missing')
        if set(observed_mods) & set(mods): raise ValueError('Volume artifact inventory duplicate mod identity')
        observed_mods.update(mods); observed_paths.add(path)
        if path:
            if record['identity'] != artifacts.get(path): raise ValueError('Volume inventory identity differs from raw loaded artifact map')
        else:
            require_sha(record['identity']); virtual.append(record)
        matches = [index for index, group in enumerate(expected_inventory)
                   if any(artifact_locator_matches(record, expected) for expected in inventory_group_alternatives(group))]
        if len(matches) != 1 or matches[0] in matched_expected:
            raise ValueError('Volume actual loaded artifact inventory differs from independent frozen receipt')
        matched_expected.add(matches[0])
    if observed_mods != report.get('loaded_mods') or observed_paths != set(artifacts):
        raise ValueError('Volume artifact inventory does not cover complete actual loaded mod/root set')
    if len(matched_expected) != len(expected_inventory):
        raise ValueError('Volume actual loaded artifact inventory differs from independent frozen receipt')
    if virtual and artifacts.get('') not in [record['identity'] for record in virtual]:
        raise ValueError('Volume raw virtual path map is not a retained actual root identity')
    named = {name: value for name, value in artifacts.items() if name}
    if named: validate_spatial_artifacts(named, expected_directories)
    if not named and not virtual: raise ValueError('Volume loaded artifact identities missing')


def analyze_report(report, expected_shader_sha256=None, profile=None, expected_directory_artifacts=None, expected_artifact_inventory=None, expected_configuration_row=None):
    if report.get('schema') != 3 or report.get('status') != 'complete': raise ValueError('Require a complete schema-3 control')
    feature = report.get('benchmark_feature', 'global')
    original_null = report.get('original_null_control', False)
    if type(original_null) is not bool: raise ValueError('Original NULL header flag must be boolean')
    if original_null:
        from analyze_photon_air_fog_inprocess import timing as null_timing, validate_rain_temporal, advancing_clocks, validate_effects as null_effects
        from prepare_photon_cloud_tail_diagnostic import qualify_configuration
        if feature != 'global' or report.get('selected_flag') != 'lod.enabled' or report.get('starting_order') != 'OFF/ON/OFF/ON': raise ValueError('Original NULL requires existing global OFF/ON/OFF/ON')
        if report.get('gpu_requested') is not True or report.get('cpu_requested') is not True: raise ValueError('Original NULL requires complete GPU/CPU observations')
        for key, minimum in (('initial_warmup_seconds', 30), ('settle_seconds', 8), ('measurement_seconds', 20)):
            if finite(report.get(key), key) < minimum: raise ValueError('Original NULL warm/settle/capture too short')
        properties = report.get('instrumentation_properties', {})
        if properties.get('benchmarkFeature') != 'global' or properties.get('gpuSampleInterval') != '5' or properties.get('fixedWetness') != '1': raise ValueError('Original NULL timing/weather properties differ')
        from analyze_photon_air_fog_inprocess import PROBES
        if any(properties.get(key, 'unspecified') not in ('false', 'unspecified') for key in PROBES): raise ValueError('Original NULL interfering observer')
        if not isinstance(expected_configuration_row, dict): raise ValueError('Original NULL requires independently frozen full configuration row')
        validate_rain_temporal(report.get('shader_temporal_inputs_initial'), False)
    if feature not in ('global', 'coherence', 'effects', 'terrain', 'ssr_cost', 'spatial', 'volume'): raise ValueError('Unknown benchmark feature')
    methods = {'global': METHOD, 'coherence': COHERENCE_METHOD, 'effects': EFFECTS_METHOD, 'terrain': TERRAIN_METHOD, 'ssr_cost': SSR_COST_METHOD, 'spatial': SPATIAL_METHOD, 'volume': VOLUME_METHOD}
    if report.get('method') != methods[feature]: raise ValueError('Unrecognized pipeline/world guard method')
    if feature == 'effects':
        if report.get('selected_flag') != 'lod.enabled': raise ValueError('Wrong selected effects flag')
        if report.get('instrumentation_properties', {}).get('benchmarkFeature') != 'effects': raise ValueError('Explicit effects instrumentation feature missing')
        if report.get('atmosphere_guard_absent') is not True: raise ValueError('Effects control requires verified absent atmosphere guard')
    require_uuid(report.get('session_id'))
    if report.get('restored_initial_enabled') is not True: raise ValueError('Initial config restoration not reported')
    if feature == 'coherence':
        if report.get('restored_initial_feature') is not True or report.get('initial_enabled') is not True: raise ValueError('Coherence/global restoration evidence missing')
        if report.get('selected_flag') != 'rendering.shadowFrustumCoherence': raise ValueError('Wrong selected coherence flag')
        policy = report.get('diagnostic_policy', {})
        if policy.get('shadow_coherence_oracle') is not False or policy.get('visibility_phase_observer') is not False: raise ValueError('Oracle/phase observer timing interference')
        for name in ('shadowCoherenceOracle', 'visibilityLists'):
            if report.get('instrumentation_properties', {}).get(name, 'unspecified') not in ('false', 'unspecified'): raise ValueError('Diagnostic observer property enabled')
    if feature == 'terrain':
        if report.get('selected_flag') != 'terrain.enabled' or report.get('instrumentation_properties', {}).get('benchmarkFeature') != 'terrain':
            raise ValueError('Explicit terrain feature/selected flag mismatch')
        if report.get('restored_initial_feature') is not True or report.get('initial_enabled') is not True or not isinstance(report.get('initial_feature_enabled'), bool):
            raise ValueError('Terrain original flag/global restoration evidence missing')
        if report.get('atmosphere_guard_absent') is not True: raise ValueError('Terrain requires absent atmosphere guard')
        if finite(report.get('settle_seconds'), 'terrain settle seconds') < 60 or finite(report.get('measurement_seconds'), 'terrain measurement seconds') < 30:
            raise ValueError('Terrain requires >=60s settle and >=30s measurement')
        if report.get('terrain_scene_identifier') != report.get('session_id') + ':inspected-fixed-scene': raise ValueError('Terrain inspected scene identifier mismatch')
        for name in ('shadowCoherenceOracle', 'visibilityLists', 'shadowObserve'):
            if report.get('instrumentation_properties', {}).get(name, 'unspecified') not in ('false', 'unspecified'): raise ValueError('Terrain diagnostic observer enabled')
    if feature == 'ssr_cost':
        if report.get('selected_flag') != 'lod.enabled' or report.get('instrumentation_properties', {}).get('benchmarkFeature') != 'ssr_cost' or report.get('atmosphere_guard_absent') is not True:
            raise ValueError('Explicit SSR cost method/flag/no-atmosphere evidence missing')
        validate_ssr_cost_qualification(report.get('ssr_cost_archive_qualification'))
        for name in ('shadowCoherenceOracle', 'visibilityLists', 'shadowObserve'):
            if report.get('instrumentation_properties', {}).get(name, 'unspecified') not in ('false', 'unspecified'): raise ValueError('SSR cost diagnostic observer enabled')
    if feature == 'spatial':
        properties = report.get('instrumentation_properties', {})
        if report.get('selected_flag') != 'lod.enabled' or properties.get('benchmarkFeature') != 'spatial' or report.get('atmosphere_guard_absent') is not True:
            raise ValueError('Explicit spatial method/flag/no-atmosphere evidence missing')
        if report.get('starting_order') != 'OFF/ON/OFF/ON': raise ValueError('Spatial diagnostic requires fixed OFF/ON/OFF/ON')
        if report.get('gpu_requested') is not True: raise ValueError('Spatial cost diagnostic requires GPU frame observations')
        for name in ('shadowCoherenceOracle', 'visibilityLists', 'shadowObserve', 'workTrace', 'spatialLayoutProbe', 'gpuPassTiming', 'cpuLayerTiming'):
            if properties.get(name, 'unspecified') not in ('false', 'unspecified'): raise ValueError('Spatial diagnostic observer enabled: ' + name)
    if feature == 'volume':
        properties = report.get('instrumentation_properties', {})
        if report.get('selected_flag') != 'lod.enabled' or properties.get('benchmarkFeature') != 'volume' or report.get('atmosphere_guard_absent') is not True:
            raise ValueError('Explicit volume method/flag/no-atmosphere evidence missing')
        if report.get('starting_order') != 'OFF/ON/OFF/ON': raise ValueError('Volume diagnostic requires fixed OFF/ON/OFF/ON')
        if report.get('gpu_requested') is not True: raise ValueError('Volume cost diagnostic requires GPU frame observations')
        for name in ('shadowCoherenceOracle', 'visibilityLists', 'shadowObserve', 'workTrace', 'volumeLayoutProbe', 'gpuPassTiming', 'cpuLayerTiming'):
            if properties.get(name, 'unspecified') not in ('false', 'unspecified'): raise ValueError('Volume diagnostic observer enabled: ' + name)
    if feature == 'volume' and finite(report.get('measurement_seconds'), 'volume measurement seconds') < 20:
        raise ValueError('Volume requires >=20s measurement per phase')

    if report.get('starting_order') not in ('OFF/ON/OFF/ON', 'ON/OFF/ON/OFF'): raise ValueError('Invalid four-phase order')
    if not isinstance(report.get('gpu_requested'), bool) or not isinstance(report.get('cpu_requested'), bool): raise ValueError('Timing policy missing')
    pin = ORIGINAL_PHOTON_SHA256 if feature == 'coherence' else TERRAIN_PHOTON_SHA256 if feature == 'terrain' else expected_shader_sha256 or report.get('workload-manifest.json', {}).get('adaptive_pack_sha256')
    if feature == 'ssr_cost':
        pin = validate_ssr_cost_qualification(report['ssr_cost_archive_qualification'])
        if expected_shader_sha256 is not None and expected_shader_sha256 != pin: raise ValueError('Explicit SSR cost pin differs from qualified archive')
    if feature == 'coherence' and expected_shader_sha256 is not None and expected_shader_sha256 != pin: raise ValueError('Explicit pin conflicts with required original Photon')
    if feature == 'terrain' and expected_shader_sha256 is not None and expected_shader_sha256 != pin: raise ValueError('Explicit pin conflicts with required terrain B15')
    pin = require_sha(pin)
    if original_null and pin != ORIGINAL_PHOTON_SHA256: raise ValueError('Original NULL archive pin differs')
    reference = report.get('stable_input_reference')
    if not isinstance(reference, dict): raise ValueError('Stable semantic input reference missing')
    reference = semantic_state(reference, True, feature)
    if reference.get('actual_shader_sha256') != pin: raise ValueError('Pinned archive/reference mismatch')
    if feature == 'terrain':
        require_sha(reference.get('control_sha256'))
        require_sha(report.get('workload-manifest.json', {}).get('source_world_sha256'))
        initial = report.get('terrain_initial_observation', {})
        validate_state(initial, report['initial_feature_enabled'], pin, feature)
        if canonical(semantic_state(initial, True, feature)) != canonical(reference): raise ValueError('Initial terrain scene/reference mismatch')
    if feature == 'ssr_cost':
        require_sha(reference.get('control_sha256'))
        require_sha(report.get('workload-manifest.json', {}).get('source_world_sha256'))
    if feature == 'spatial':
        spatial_source_binding(pin)
        require_sha(reference.get('control_sha256'))
        require_sha(report.get('workload-manifest.json', {}).get('source_world_sha256'))
        if expected_shader_sha256 is not None and reference.get('actual_shader_sha256') != expected_shader_sha256: raise ValueError('Explicit spatial pin differs')
    if feature == 'volume':
        volume_source_binding(pin)
        require_sha(reference.get('control_sha256'))
        require_sha(report.get('workload-manifest.json', {}).get('source_world_sha256'))
        if expected_shader_sha256 is not None and reference.get('actual_shader_sha256') != expected_shader_sha256: raise ValueError('Explicit volume pin differs')
    if report['gpu_requested'] and report.get('backend') not in ('TIMESTAMP', 'ELAPSED_ALTERNATING'):
        raise ValueError('Requested GPU timing backend unsupported')
    if not report.get('loaded_mods') or not report.get('loaded_artifacts_sha256'): raise ValueError('Runtime versions/artifact identities missing')
    if feature in ('terrain', 'ssr_cost'):
        pinned_mods = {'minecraft': '1.21.1', 'neoforge': '21.1.253',
                       'iris': '1.8.12-snapshot+mc1.21.1-local', 'sodium': '0.6.13+mc1.21.1'}
        if feature == 'terrain': pinned_mods['distanthorizons'] = '3.3.3'
        elif 'distanthorizons' in report['loaded_mods']: raise ValueError('SSR cost loaded DH despite absent requirement')
        if any(report['loaded_mods'].get(name) != version for name, version in pinned_mods.items()) or not report['loaded_mods'].get('shaderlod'):
            raise ValueError('Terrain loaded runtime differs from pinned mod set')
    if feature == 'spatial':
        pinned_mods = {'minecraft': '1.21.1', 'neoforge': '21.1.253', 'iris': '1.8.12-snapshot+mc1.21.1-local', 'sodium': '0.6.13+mc1.21.1'}
        if 'distanthorizons' in report['loaded_mods'] or not report['loaded_mods'].get('shaderlod') or any(report['loaded_mods'].get(name) != version for name, version in pinned_mods.items()): raise ValueError('Spatial loaded runtime differs from pinned noDH mod set')
        validate_spatial_artifacts(report['loaded_artifacts_sha256'], expected_directory_artifacts)
    if feature == 'volume':
        pinned_mods = {'minecraft': '1.21.1', 'neoforge': '21.1.253', 'iris': '1.8.12-snapshot+mc1.21.1-local', 'sodium': '0.6.13+mc1.21.1'}
        if 'distanthorizons' in report['loaded_mods'] or not report['loaded_mods'].get('shaderlod') or any(report['loaded_mods'].get(name) != version for name, version in pinned_mods.items()): raise ValueError('Volume loaded runtime differs from pinned noDH mod set')
        validate_volume_artifacts(report, expected_directory_artifacts, expected_artifact_inventory)
    if original_null:
        from qualify_volume_fast_artifacts import MODS
        evidence = report.get('loaded_artifact_evidence', {})
        if report['loaded_mods'] != MODS or evidence.get('schema') != 1 or evidence.get('valid') is not True or evidence.get('errors') != [] or evidence.get('complete_mod_coverage') is not True or evidence.get('complete_identities') is not True or evidence.get('loaded_mods') != report['loaded_mods'] or evidence.get('loaded_artifact_inventory') != report.get('loaded_artifact_inventory'):
            raise ValueError('Original NULL complete direct loaded artifact evidence differs')
        validate_volume_artifacts(report, expected_directory_artifacts, expected_artifact_inventory)
    phases = report.get('phases', [])
    if len(phases) != 4: raise ValueError('Require exactly four phases')
    ids, images, gpu_epochs, cpu_epochs = set(), set(), set(), set()
    previous_end = -1
    output = []
    verified_iris = None
    if profile is not None:
        iris_path = profile / 'config/iris.properties'
        iris_pin = phases[0].get('inputStart', {}).get('config/iris.properties_sha256') if original_null else reference.get('config/iris.properties_sha256')
        if not iris_path.is_file() or digest(iris_path) != iris_pin:
            raise ValueError('Stopped profile Iris bytes do not match capture fingerprint')
        verified_iris = {'raw_sha256': digest(iris_path), 'properties': read_option_properties(iris_path.read_text()),
                         'verification': 'Current stopped-profile bytes matched the captured reference SHA256 before comment/order normalization'}
    start_enabled = report['starting_order'].startswith('ON')
    null_gpu_epochs, null_cpu_epochs = set(), set()
    null_config = None
    previous_temporal = report.get('shader_temporal_inputs_initial')
    for index, phase in enumerate(phases, 1):
        if phase.get('status') != 'complete' or phase.get('index') != index: raise ValueError('Incomplete or misordered phase')
        require_uuid(phase.get('id'))
        if phase['id'] in ids: raise ValueError('Duplicate phase UUID')
        ids.add(phase['id'])
        enabled = start_enabled if index % 2 else not start_enabled
        if phase.get('enabled') is not enabled: raise ValueError('Wrong phase alternation')
        first, last = integer(phase.get('startFrame'), 'startFrame'), integer(phase.get('endFrame'), 'endFrame')
        if first <= previous_end or last <= first: raise ValueError('Overlapping/reversed source-frame intervals')
        previous_end = last
        frames = integer(phase.get('renderFrames'), 'renderFrames')
        if frames != last - first or frames < 30: raise ValueError('Render-frame count mismatch')
        elapsed = finite(phase.get('elapsedSeconds'), 'elapsedSeconds')
        requested_duration = finite(report.get('measurement_seconds'), 'measurement_seconds')
        if requested_duration <= 0 or elapsed < requested_duration * .95: raise ValueError('Phase capture too short')
        for state in (phase.get('inputStart', {}), phase.get('inputEnd', {})):
            validate_state(state, enabled, pin, feature, original_null)
            if original_null:
                if state.get('loaded_artifact_evidence') != report.get('loaded_artifact_evidence'): raise ValueError('Original NULL boundary artifact evidence changed/differs from header')
                validate_original_null_state(state, expected_configuration_row, qualify_configuration, null_effects)
                config = original_null_configuration_semantics(state)
                if null_config is not None and config != null_config: raise ValueError('Original NULL complete config changed')
                null_config = config
            if canonical(semantic_state(state, True, feature)) != canonical(reference): raise ValueError('Stable inputs changed beyond selected feature')
        if canonical(semantic_state(phase['inputStart'], feature=feature)) != canonical(semantic_state(phase['inputEnd'], feature=feature)):
            raise ValueError('In-phase semantic mutation')
        if feature == 'terrain': validate_terrain_phase(report, phase, reference)
        if feature == 'ssr_cost': validate_fixed_window(phase, 'ssr_cost_window_state')
        if feature == 'spatial': validate_fixed_window(phase, 'spatial_window_state')
        if feature == 'volume': validate_fixed_window(phase, 'volume_window_state')
        validate_temporal(phase)
        if original_null:
            if finite(phase.get('settleElapsedSeconds'), 'actual settling') < report['settle_seconds']: raise ValueError('Original NULL actual settling too short')
            validate_fixed_window(phase, 'original_null_window_state')
            cap = phase['windowState']['capture']; count = cap['observations']
            if cap.get('window_size_counts') != {'1488x836': count} or cap.get('framebuffer_size_counts') != {'2976x1672': count} or cap['start'].get('frame_id') != first or cap['end'].get('frame_id') != last: raise ValueError('Original NULL sampled native extent/frame identity differs')
            for boundary in ('temporalStart', 'temporalEnd'): validate_rain_temporal(phase[boundary], boundary == 'temporalEnd')
            advancing_clocks(previous_temporal, phase['temporalStart']); advancing_clocks(phase['temporalStart'], phase['temporalEnd'])
            previous_temporal = phase['temporalEnd']
            null_timing(phase, report, null_gpu_epochs, null_cpu_epochs)
        gpu, cpu = phase.get('gpu', []), phase.get('cpu', [])
        if not isinstance(gpu, list) or not isinstance(cpu, list): raise ValueError('Timing arrays missing')
        if report['gpu_requested'] and not any(sample.get('scope') == 'game-render' for sample in gpu): raise ValueError('Requested GPU frame observations missing')
        if not report['gpu_requested'] and gpu: raise ValueError('Unexpected GPU samples in query-free control')
        if report['cpu_requested'] and not any(sample.get('scope') == 'run-tick' for sample in cpu): raise ValueError('Requested CPU observations missing')
        if not report['cpu_requested'] and cpu: raise ValueError('Unexpected CPU samples in timer-free control')
        validate_samples(gpu, phase.get('gpuEpoch'), first, last, ('milliseconds',))
        validate_samples(cpu, phase.get('cpuEpoch'), first, last, ('wallMilliseconds', 'threadMilliseconds'))
        if feature == 'spatial':
            frame_samples = [sample for sample in gpu if sample.get('scope') == 'game-render']
            if len(frame_samples) < SPATIAL_MIN_GPU_SAMPLES or any(sample['milliseconds'] <= 0 for sample in frame_samples): raise ValueError('Spatial insufficient/nonpositive GPU frame coverage')
            if len({sample['frameId'] for sample in frame_samples}) != len(frame_samples): raise ValueError('Spatial duplicate GPU frame observations')
            if any(sample.get('scope') != 'game-render' for sample in gpu): raise ValueError('Spatial mixed GPU scope stream')
        if feature == 'volume':
            frame_samples = [sample for sample in gpu if sample.get('scope') == 'game-render']
            if len(frame_samples) < VOLUME_MIN_GPU_SAMPLES or any(sample['milliseconds'] <= 0 for sample in frame_samples): raise ValueError('Volume insufficient/nonpositive GPU frame coverage')
            if len({sample['frameId'] for sample in frame_samples}) != len(frame_samples): raise ValueError('Volume duplicate GPU frame observations')
            if any(sample.get('scope') != 'game-render' for sample in gpu): raise ValueError('Volume mixed GPU scope stream')
        if feature == 'volume' and elapsed < 20:
            raise ValueError('Volume actual phase capture shorter than 20s')
        for samples, epoch, used in ((gpu, phase.get('gpuEpoch'), gpu_epochs), (cpu, phase.get('cpuEpoch'), cpu_epochs)):
            if samples:
                if epoch in used: raise ValueError('Timing epoch reused between phases')
                used.add(epoch)
        image = phase.get('image')
        if not isinstance(image, str) or Path(image).name != image or not image.endswith('.png') or image in images:
            raise ValueError('Phase image names must be unique local PNGs')
        images.add(image)
        image_hash = require_sha(phase.get('imageSha256'))
        if profile is not None:
            image_path = profile / 'screenshots' / image
            if not image_path.is_file() or digest(image_path) != image_hash: raise ValueError('Phase image missing/hash mismatch')
            with image_path.open('rb') as source:
                if source.read(8) != b'\x89PNG\r\n\x1a\n': raise ValueError('Phase image is not a PNG')
        gpu_stats, cpu_wall, cpu_thread = stream(gpu, 'milliseconds'), stream(cpu, 'wallMilliseconds'), stream(cpu, 'threadMilliseconds')
        fps = frames / elapsed
        output.append({'index': index, 'phase_id': phase['id'], 'enabled': enabled, 'render_fps': fps, 'frame_mean_ms': 1000 / fps,
                       'elapsed_seconds': elapsed, 'render_frames': frames, 'source_frame_interval': [first, last],
                       'gpu_epoch': phase.get('gpuEpoch'), 'cpu_epoch': phase.get('cpuEpoch'),
                       'gpu': gpu_stats, 'cpu_wall': cpu_wall, 'cpu_thread': cpu_thread,
                       'gpu_mean_ms': metric(gpu_stats, 'game-render'), 'gpu_median_ms': metric(gpu_stats, 'game-render', 'median_ms'),
                       'gpu_p95_ms': metric(gpu_stats, 'game-render', 'p95_ms'), 'gpu_p99_ms': metric(gpu_stats, 'game-render', 'p99_ms'),
                       'cpu_wall_mean_ms': metric(cpu_wall, 'run-tick'), 'cpu_thread_mean_ms': metric(cpu_thread, 'run-tick'),
                       'image': image, 'image_sha256': image_hash, 'image_verified': profile is not None,
                       'terrain_policy_start': copy.deepcopy(phase['inputStart'].get('geometry_policy')),
                       'terrain_policy_end': copy.deepcopy(phase['inputEnd'].get('geometry_policy')),
                       'terrain_rearm': copy.deepcopy(phase.get('terrainRearm')), 'settle_elapsed_seconds': phase.get('settleElapsedSeconds'),
                       'window_state': copy.deepcopy(phase.get('windowState')), 'temporal_start': copy.deepcopy(phase.get('temporalStart')),
                       'temporal_end': copy.deepcopy(phase.get('temporalEnd')), 'uniform_observation': copy.deepcopy(phase.get('uniform_observation'))})
    pairs = []
    keys = ('frame_mean_ms', 'gpu_mean_ms', 'gpu_median_ms', 'gpu_p95_ms', 'cpu_wall_mean_ms', 'cpu_thread_mean_ms')
    for a, b in ((output[0], output[1]), (output[2], output[3])):
        off, on = (a, b) if not a['enabled'] else (b, a)
        pairs.append({'off_phase': off['index'], 'on_phase': on['index'],
                      'on_vs_off_percent_time_reduction': {key: time_reduction(off[key], on[key]) for key in keys},
                      'on_vs_off_fps_change_percent': 100 * (on['render_fps'] / off['render_fps'] - 1)})
    drift = {}
    for enabled in (False, True):
        same = [phase for phase in output if phase['enabled'] is enabled]
        drift['ON' if enabled else 'OFF'] = {'phases': [phase['index'] for phase in same],
                                           'later_vs_earlier_percent_time_reduction': {key: time_reduction(same[0][key], same[1][key]) for key in keys}}
    null_screen = original_null_screen(output) if original_null else None
    return {'schema': 1, 'session_id': report['session_id'], 'original_null_control': original_null, 'original_null_screen': null_screen, 'benchmark_feature': feature,
            'method': methods[feature],
            'selected_flag': 'rendering.shadowFrustumCoherence' if feature == 'coherence' else 'terrain.enabled' if feature == 'terrain' else 'lod.enabled',
            'effect_attribution': 'Existing detailed-radius/DH replacement tradeoff; fixed shader/global enable; not a shader-only or novel-mesh gain' if feature == 'terrain' else 'Selected live flag; fixed observed inputs',
            'ssr_cost_archive_qualification': copy.deepcopy(report.get('ssr_cost_archive_qualification')),
            'ssr_cost_limits': 'Within one fixed archive/pipeline/world only. OFF intends original B15 trace budgets; ON applies that archive policy. No cross-archive cause, shader busy-time, physical trace-call count or actual uniform observation is inferred.' if feature == 'ssr_cost' else None,
            'spatial_diagnostic_limits': 'Incremental global OFF/ON in the same spatial graph. OFF retains extra pass/attachments. Correlated cost diagnostic only: no original Photon 1.5x throughput, quality acceptance, physical work count or linked-uniform observation is inferred.' if feature == 'spatial' else None,
            'volume_diagnostic_limits': 'Incremental global OFF/ON in one identical volume graph; existing-pass budget only. Correlated cost diagnostic only: no original Photon 1.5x throughput, quality acceptance, physical work count or linked-uniform observation is inferred.' if feature == 'volume' else None,
            'loaded_artifact_inventory': copy.deepcopy(report.get('loaded_artifact_inventory')) if feature == 'volume' else None,
            'shader_sha256': pin, 'phases': output, 'adjacent_pairs': pairs,
            'same_state_drift': drift, 'stable_input_reference': reference, 'loaded_mods': report['loaded_mods'],
            'loaded_artifacts_sha256': report['loaded_artifacts_sha256'], 'instrumentation': report.get('instrumentation_properties'),
            'backend': report.get('backend'), 'sampling_policy': report.get('sampling_policy'),
            'verified_iris_configuration': verified_iris,
            'pipeline_world_check': 'Complete report declares the source method that checks reference equality; raw world/pipeline IDs are not exported and cannot be independently reconstructed.',
            'uncertainty': 'Four correlated phases in one JVM, two adjacent contrasts; no independent-run CI/significance. Frame samples are autocorrelated. Window/temporal unknowns are retained, not inferred stable.',
            'limits': 'Positive reduction means ON faster. GPU elapsed intervals are not busy time; CPU includes driver work. Query-free controls have no GPU effect estimate. Source/archive changes across sessions are separate causal interventions, not an ordinary-scene ShaderLOD gain.'}




def original_null_configuration_semantics(state):
    from analyze_photon_air_fog_inprocess import normalize_main, normalize_generated_timestamp
    normalized = copy.deepcopy(state.get('configuration_files'))
    if not isinstance(normalized, dict): raise ValueError('Original NULL complete config missing')
    files = normalized.get('files', {})
    # Java stable_input_reference already carries this proven normalization.
    if 'utf8_lod_enabled_omitted' in files.get('config/shaderlod-client.toml', {}): return normalized
    for name in ('config/shaderlod-client.toml', 'config/shaderlod-client-1.toml.bak'):
        if name in files:
            files[name] = {'utf8_lod_enabled_omitted': normalize_main(files[name]['utf8'])[0]}
    for name in ('config/iris.properties', 'shaderpacks/' + state.get('shader_pack', '') + '.txt'):
        if name not in files: raise ValueError('Original NULL exact Iris/sidecar missing')
        files[name] = {'utf8_generated_timestamp_omitted': normalize_generated_timestamp(files[name]['utf8'])}
    return normalized


def validate_original_null_state(state, row, qualifier, validate_effects):
    """Reuse complete configuration/INHERIT contracts; no absent option is fabricated."""
    import struct
    import tomllib
    parsed = {name: tomllib.loads(state['configuration_files']['files'][name]['utf8'])
              for name in ('config/shaderlod-client.toml', 'config/shaderlod-effects-client.toml')}
    selected_row = copy.deepcopy(row)
    selected_row['prepared_writable_inventory']['settings']['config/shaderlod-client.toml']['value']['lod']['enabled'] = parsed['config/shaderlod-client.toml']['lod']['enabled']
    qualified = qualifier(state.get('configuration_files', {}), selected_row)
    if state.get('shader_pack') != row['expected']['archive']: raise ValueError('Original NULL selected archive name differs')
    loaded = copy.deepcopy(state['loaded_semantic_config']); loaded.pop('effects', None)
    loaded['lod']['enabled'] = parsed['config/shaderlod-client.toml']['lod']['enabled']
    if loaded != parsed['config/shaderlod-client.toml']: raise ValueError('Original NULL full loaded/persisted semantics differ')
    controls = state.get('original_null_effect_controls')
    if controls != state['loaded_semantic_config'].get('effects') or state.get('original_null_effect_controls_sha256') != hashlib.sha256(canonical(controls).encode()).hexdigest(): raise ValueError('Original NULL complete effect snapshot fingerprint differs')
    if {k:loaded['lod'].get(k) for k in ('preset','near','far','transition','debug')} != dict(preset='PERFORMANCE',near=32,far=96,transition=8,debug=False) or loaded['terrain'].get('vanillaChunkCap') != 16 or loaded['terrain'].get('warmupSeconds') != 30: raise ValueError('Original NULL core/terrain recipe differs')
    if state.get('lod_preset') != 'PERFORMANCE' or any(state['lod_settings'].get(k) != loaded['lod'][k] for k in ('near','far','transition')): raise ValueError('Original NULL core snapshot differs')
    alias = dict(state, air_fog_ablation_effect_controls=controls,
                 air_fog_ablation_effect_controls_sha256=hashlib.sha256(canonical(controls).encode()).hexdigest())
    validate_effects(alias, parsed)
    for key, value in state.items():
        if key.startswith('config/') and key.endswith('_sha256') and state['configuration_files']['files'].get(key[:-7], {}).get('sha256') != value: raise ValueError('Original NULL raw config alias differs')
    if integer(state.get('gui_scale'), 'GUI scale') != 2: raise ValueError('Original NULL fixed GUI scale differs')
    if state.get('original_null_dh_present') is not False or state.get('original_null_camera_type') != 'FIRST_PERSON' or state.get('original_null_fov') != 70: raise ValueError('Original NULL noDH/camera/FOV differs')
    for key, value in dict(framebuffer_width=2976, framebuffer_height=1672, requested_chunks=24, effective_chunks=24, day_time=12500, x=500.5, y=68, z=660.5, rain=1, thunder=0).items():
        if finite(state.get(key), key) != value: raise ValueError('Original NULL native scene differs: ' + key)
    for key, value in (('yaw',87.38), ('pitch',3.735)):
        if struct.pack('!f',finite(state.get(key),key)) != struct.pack('!f',value): raise ValueError('Original NULL Float pose differs')
    if state.get('dimension') != 'minecraft:overworld': raise ValueError('Original NULL dimension differs')
    require_sha(state.get('control_sha256'))
    window = state.get('original_null_window_state', {})
    if any(window.get(k) != v for k,v in dict(availability='available', focused='true', visible='true', iconified='false', framebuffer_width=2976, framebuffer_height=1672, window_width=1488, window_height=836).items()): raise ValueError('Original NULL window/focus/extent differs')
    if window.get('pacing') != dict(vsync=False, fullscreen=False, pause_on_lost_focus=False, configured_framerate_limit=260): raise ValueError('Original NULL pacing differs')
    return qualified


def original_null_screen(phases):
    if len(phases) != 4: raise ValueError('Original NULL needs all four phases')
    metrics = {}
    for key in ('frame_mean_ms', 'gpu_median_ms'):
        values = [finite(phase.get(key),key) for phase in phases]
        if min(values) <= 0: raise ValueError('Original NULL positive total/GPU timing required')
        spread, drift = max(values)/min(values)-1, abs(values[-1]/values[0]-1)
        metrics[key] = dict(all_four_spread=spread, first_last_drift=drift, passes= max(spread,drift) <= .03 + 1e-12)
    return dict(verdict='stable-rendering-equivalent-null-diagnostic' if all(m['passes'] for m in metrics.values()) else 'inconclusive-null-drift',
                metrics=metrics, primary='total elapsed/rendered frames', corroboration='GPU median separately gated',
                performanceEvidence=False, limitation='Rendering-equivalent flag toggle; not unchanged-state A/A or optimizer; all phases retained, no contention correction.')


def compare_sessions(summaries):
    first = summaries[0]
    if first.get('benchmark_feature') == 'terrain' and any(summary.get('shader_sha256') != TERRAIN_PHOTON_SHA256 for summary in summaries):
        raise ValueError('Terrain session comparison requires the same exact B15 archive')
    if first.get('benchmark_feature') == 'ssr_cost' and any(summary.get('shader_sha256') != first['shader_sha256'] for summary in summaries):
        raise ValueError('SSR cost sessions compare within one exact archive only; no cross-archive causality')
    if first.get('benchmark_feature') == 'spatial' and any(summary.get('shader_sha256') != first['shader_sha256'] for summary in summaries):
        raise ValueError('Spatial sessions compare within one exact archive only; no cross-archive causality')
    if first.get('benchmark_feature') == 'volume' and any(summary.get('shader_sha256') != first['shader_sha256'] for summary in summaries):
        raise ValueError('Volume sessions compare within one exact archive only; no cross-archive causality')
    identity = copy.deepcopy(first['stable_input_reference'])
    identity.pop('actual_shader_sha256', None)
    iris_verified = all(summary.get('verified_iris_configuration') is not None for summary in summaries)
    if iris_verified:
        maps = [summary['verified_iris_configuration']['properties'] for summary in summaries]
        if any(properties != maps[0] for properties in maps[1:]): raise ValueError('Across-session meaningful Iris configuration difference')
        identity.pop('config/iris.properties_sha256', None)
    for summary in summaries[1:]:
        if summary.get('benchmark_feature', 'global') != first.get('benchmark_feature', 'global'): raise ValueError('Across-session selected-feature mismatch')
        candidate = copy.deepcopy(summary['stable_input_reference']); candidate.pop('actual_shader_sha256', None)
        if iris_verified: candidate.pop('config/iris.properties_sha256', None)
        if canonical(candidate) != canonical(identity): raise ValueError('Across-session quality/renderer/input mismatch')
        for field in ('loaded_mods', 'loaded_artifacts_sha256', 'instrumentation', 'sampling_policy', 'backend') + (('loaded_artifact_inventory',) if first.get('benchmark_feature') == 'volume' else ()):
            if summary[field] != first[field]: raise ValueError('Across-session runtime/instrumentation mismatch: ' + field)
    return {'session_ids': [summary['session_id'] for summary in summaries],
            'shader_sha256': [summary['shader_sha256'] for summary in summaries],
            'archive_source_delta': len({summary['shader_sha256'] for summary in summaries}) > 1,
            'iris_configurations': [summary.get('verified_iris_configuration') for summary in summaries],
            'iris_semantically_verified': iris_verified,
            'note': 'Matched observed inputs/runtime only. Different archives document a separate source intervention; comparisons remain separate correlated-phase sessions, not paired independent repeats or proof of a specific code-path cause.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reports', nargs='+', type=Path)
    parser.add_argument('--expected-shader-sha256', help='For one report; otherwise each report must carry its preparation pin')
    parser.add_argument('--profile', type=Path, help='Verify actual phase images in this profile/screenshots directory (one report only)')
    parser.add_argument('--profiles', nargs='+', type=Path, help='Stopped profile for every report in the same order; verifies images and captured Iris bytes')
    parser.add_argument('--artifact-receipt', type=Path, help='Frozen preparation receipt carrying loaded_directory_identities and, for volume, independent complete loaded_artifact_inventory (one report only)')
    parser.add_argument('--configuration-row', type=Path, help='Independent frozen prepared row; required original NULL control')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if len(args.reports) > 1 and (args.profile or args.expected_shader_sha256): parser.error('Profile/explicit archive pin require one report')
    if len(args.reports) > 1 and args.artifact_receipt: parser.error('Frozen artifact receipt requires one report')
    if args.profile and args.profiles: parser.error('Use either --profile or --profiles')
    if args.profiles and len(args.profiles) != len(args.reports): parser.error('Provide one stopped profile per report')
    if args.output and args.output.resolve() in {path.resolve() for path in args.reports}: parser.error('Output may not overwrite an original report')
    profiles = args.profiles or [args.profile] * len(args.reports)
    receipt = json.loads(args.artifact_receipt.read_text()) if args.artifact_receipt else {}
    expected_directories = receipt.get('loaded_directory_identities')
    expected_inventory = receipt.get('loaded_artifact_inventory')
    configuration_row = json.loads(args.configuration_row.read_text()) if args.configuration_row else None
    summaries = [analyze_report(json.loads(path.read_text()), args.expected_shader_sha256, profile, expected_directories, expected_inventory, configuration_row) for path, profile in zip(args.reports, profiles)]
    for path, summary in zip(args.reports, summaries): summary['source'] = str(path.resolve())
    result = summaries[0] if len(summaries) == 1 else {'sessions': summaries, 'comparison': compare_sessions(summaries)}
    contents = json.dumps(result, indent=2, allow_nan=False) + '\n'
    if args.output: args.output.write_text(contents)
    else: print(contents, end='')


if __name__ == '__main__': main()
