#!/usr/bin/env python3
"""Independent qualification of the private, settled WATER_SHADOW_ZERO four-phase screen.

This wrapper deliberately does not route the archive through any older feature.
Shared timing/window/artifact primitives are read-only dependencies.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
import math
from pathlib import Path
import re
import struct
import sys
import tomllib

from analyze_inprocess_control import (canonical, digest, finite, integer, metric,
    require_sha, require_uuid, time_reduction,
    validate_fixed_window, validate_samples, validate_temporal, validate_volume_artifacts,
    FALSE_FLAGS, SPATIAL_EFFECTS)
from summarize_workload import stream
from qualify_volume_fast_artifacts import MODS
from run_proof_matrix import read_option_properties

METHOD = 'One JVM/world/pipeline; pinned native Photon water receiver zero gate; only live core global enabled changes; incremental candidate OFF/ON cost screen'
PRODUCER_LIMITS = 'Exact-zero source proof is separate from actual driver/sampler water-stage qualification, executed skip coverage, finite texture inputs, wave-motion parity and speed. Candidate global OFF is not the original archive denominator; original Photon comparison and independent repeats remain required.'
ARCHIVE = 'eb411fa4a5ca13adb222a5e9e96b46b587d4f79538758c8035fca524156b4983'
MEMBERS_HASH = '030d50c0c724bbf52812d4a8d55c9fc3fbaba1b814bc9eeb11e501866259fada'
OPTIONS_HASH = '16afc3e2270bd30aa1e440889e98b8550f6d4ecc345cb0e0009b117d4b933fae'
WORLD = '89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46'
SAMPLING = 'frame only, every sampled frame; sample interval=5 render frames, poll only sampled frames'
MAIN = 'config/shaderlod-client.toml'
BACKUP = 'config/shaderlod-client-1.toml.bak'
EFFECTS = 'config/shaderlod-effects-client.toml'
IRIS = 'config/iris.properties'
SIDECAR = 'shaderpacks/water-shadow-zero-enabled.zip.txt'
LIMITS = ('Four correlated settled candidate OFF/ON phases. OFF retains the new guard/selector '
    'and is not original Photon. Source exact-zero reasoning is not executed skip coverage, '
    'physical shadow-fetch counts, finite texture input proof, water-stage driver qualification, '
    'wave-motion parity, repeated performance or original-native gain. Raw world/pipeline/camera '
    'object IDs are unexported; current pinned producer guards equality. GPU intervals are not utilization.')
PROBES = ('shadowCoherenceOracle', 'visibilityLists', 'shadowObserve', 'workTrace',
    'workTraceOnly', 'spatialLayoutProbe', 'spatialLayoutOnly', 'cloudSamplerProbe',
    'dhDrawEvidence', 'waterSceneSurvey', 'smokeTest', 'gpuBenchmark', 'cameraQualityOnly',
    'gpuPassTiming', 'cpuLayerTiming', 'gpuAttribution', 'pixelCoverage', 'pixelCoverageOnly', 'smoke')


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def hashed(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def source_pins():
    root = Path(__file__).resolve().parents[1]
    paths = [Path(__file__), root/'tests/test_water_shadow_control.py',
        root/'src/devClient/java/com/pp/shaderlod/dev/BenchmarkSession.java',
        root/'src/devClient/java/com/pp/shaderlod/dev/WorkloadInputs.java',
        root/'src/devClient/java/com/pp/shaderlod/dev/timing/GpuTimers.java',
        root/'src/devClient/java/com/pp/shaderlod/dev/timing/CpuTimers.java']
    for module in tuple(sys.modules.values()):
        name = getattr(module, '__file__', None)
        if name:
            path = Path(name).resolve()
            if path.suffix == '.py' and path.is_relative_to(root/'scripts'): paths.append(path)
    return {str(p.resolve()):digest(p) for p in sorted(set(paths))}


def normalize_main(text):
    """Only a literal bool token may differ; parse the entire TOML first."""
    model = tomllib.loads(text)
    require(isinstance(model.get('lod', {}).get('enabled'), bool), 'Persisted lod.enabled must be boolean')
    # Match exactly the [lod] table, not similarly named nested tables or comments.
    table = re.search(r'(?m)^\s*\[lod\][ \t]*(?:#[^\r\n]*)?\r?\n', text)
    require(table is not None, 'Literal [lod] table missing')
    tail = text[table.end():]
    following = re.search(r'(?m)^[ \t]*\[', tail)
    length = following.start() if following else len(tail)
    pattern = re.compile(r'(?m)^([ \t]*enabled[ \t]*=[ \t]*)(true|false)([ \t]*(?:#[^\r\n]*)?\r?$)')
    assignments = list(pattern.finditer(tail[:length]))
    require(len(assignments) == 1, 'Exact persisted lod.enabled assignment missing/ambiguous')
    token = assignments[0]
    start, end = table.end() + token.start(2), table.end() + token.end(2)
    return text[:start] + '<live-global>' + text[end:], model


def normalize_generated_timestamp(text):
    """Only Java Properties.store's exact Date.toString comment is excluded."""
    pattern = re.compile(r'(?m)^#(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun) '
        r'(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) '
        r'[0-3][0-9] [0-2][0-9]:[0-5][0-9]:[0-5][0-9] [A-Za-z0-9:+_-]+ [0-9]{4}(?=\r?$)')
    matches = list(pattern.finditer(text))
    require(len(matches) == 1, 'Exactly one generated Iris timestamp comment required')
    match = matches[0]
    return text[:match.start()] + '#<iris-startup-timestamp>' + text[match.end():]


def configuration(snapshot):
    """Validate genuine ConfigurationFilesEvidence records and return raw-preserving semantics."""
    require(isinstance(snapshot, dict) and snapshot.get('complete') is True and snapshot.get('errors') == [],
        'Incomplete captured configuration')
    files = snapshot.get('files')
    require(isinstance(files, dict) and 1 <= len(files) <= 128, 'Complete configuration files missing/over limit')
    require(all(name in files for name in (MAIN, EFFECTS, 'options.txt', IRIS, SIDECAR)),
        'Required captured configuration missing')
    normalized = copy.deepcopy(snapshot)
    parsed = {}
    total = 0
    for name, item in files.items():
        require(isinstance(name, str) and name and not Path(name).is_absolute() and '..' not in Path(name).parts
            and '\\' not in name and (name == 'options.txt' or name.startswith('config/') or
                (name.startswith('shaderpacks/') and name.endswith('.txt'))), 'Unsafe/unexpected configuration path')
        require(isinstance(item, dict), 'Malformed configuration record')
        size = integer(item.get('bytes'), 'configuration bytes')
        total += size
        require(total <= 1024 * 1024, 'Captured configuration exceeds 1 MiB')
        require_sha(item.get('sha256'))
        if 'utf8' in item:
            text = item['utf8']
            require(set(item) == {'bytes', 'sha256', 'utf8'} and isinstance(text, str), 'Malformed UTF8 configuration')
            encoded = text.encode()
            require(len(encoded) == size and hashlib.sha256(encoded).hexdigest() == item['sha256'],
                'Captured configuration text/hash/size differs')
            if name.endswith('.toml'):
                parsed[name] = tomllib.loads(text)  # Unknown keys remain in the complete parse.
            if name in (MAIN, BACKUP):
                normalized_text, model = normalize_main(text)
                parsed[name] = model
                normalized['files'][name] = {'utf8_lod_enabled_omitted': normalized_text}
            elif name in (IRIS, SIDECAR):
                normalized['files'][name] = {'utf8_generated_timestamp_omitted': normalize_generated_timestamp(text)}
        else:
            require(set(item) == {'bytes', 'sha256', 'encoding'} and item['encoding'] == 'binary; exact hash only',
                'Unknown captured binary configuration schema')
            require(name not in (MAIN, BACKUP, EFFECTS, 'options.txt', IRIS, SIDECAR), 'Required config UTF8 missing')
    if BACKUP in files:
        require(BACKUP in parsed, 'Main numbered backup must be UTF8 TOML')
        main, backup = copy.deepcopy(parsed[MAIN]), copy.deepcopy(parsed[BACKUP])
        main['lod'].pop('enabled'); backup['lod'].pop('enabled')
        require(main == backup, 'Numbered main backup settings differ beyond global enabled')
    effects_backup = 'config/shaderlod-effects-client-1.toml.bak'
    if effects_backup in files:
        require('utf8' in files[effects_backup], 'Effects first backup must be UTF8')
        require(tomllib.loads(files[effects_backup]['utf8']) == parsed[EFFECTS], 'Effects first backup differs from complete inert settings')
    iris = read_option_properties(files[IRIS]['utf8'])
    require(iris.get('shaderPack') == 'water-shadow-zero-enabled.zip' and iris.get('enableShaders') == 'true', 'Exact persisted Iris selection differs')
    return normalized, parsed


def water_semantic_state(state):
    require(integer(state.get('water_shadow_zero_control_receipt_schema'), 'water shadow zero control receipt schema') == 2,
        'Explicit water shadow zero control receipt schema 2 required')
    result = base_semantic_state(state)
    normalized, _ = configuration(state.get('configuration_files'))
    require(normalized == state.get('water_shadow_zero_configuration_semantics'), 'Independent configuration normalization differs')
    require(state.get(IRIS + '_sha256') == state['configuration_files']['files'][IRIS]['sha256'], 'Captured Iris raw hash alias differs')
    for field in ('water_shadow_zero_source_qualification', 'water_shadow_zero_effect_controls'):
        require(hashed(state.get(field)) == state.get(field + '_sha256'), 'Canonical water shadow zero derived hash differs: ' + field)
    result.pop('configuration_files', None)
    result.pop('config/iris.properties_sha256', None)  # Validated raw alias; only known generated timestamp differs.
    result['configuration_files'] = normalized
    return result


def base_semantic_state(state):
    """Independent generic config shape, no older feature/archive validation route."""
    result = copy.deepcopy(state)
    required = {'lod': {'enabled', 'preset', 'near', 'far', 'transition', 'debug'},
        'terrain': {'enabled', 'vanillaChunkCap', 'warmSourceConfirmed', 'warmupSeconds'},
        'rendering': {'optimizeShadowCulling', 'separateShadowVisibilityLists', 'shadowFrustumCoherence'}}
    semantic = result.get('loaded_semantic_config', {})
    require(isinstance(semantic, dict) and all(isinstance(semantic.get(s), dict) and names <= semantic[s].keys()
        for s,names in required.items()), 'Incomplete loaded semantic controls')
    result.pop('config/shaderlod-client.toml_sha256', None)
    return result


def stable(state):
    result = water_semantic_state(state)
    result['lod_settings'].pop('enabled', None)
    result['loaded_semantic_config']['lod'].pop('enabled', None)
    return result


def validate_effects(state, parsed):
    controls = state.get('water_shadow_zero_effect_controls')
    require(isinstance(controls, dict) and set(controls) == set(SPATIAL_EFFECTS), 'Complete water shadow zero effects missing')
    require(controls == state['loaded_semantic_config'].get('effects'), 'Loaded effects differ from actual snapshot')
    require(state.get('water_shadow_zero_effect_controls_sha256') == hashed(controls), 'Live effects canonical fingerprint differs')
    disk = parsed.get(EFFECTS, {}).get('effects')
    require(isinstance(disk, dict) and set(disk) == {name.lower() for name in SPATIAL_EFFECTS}, 'Full parsed effect settings missing')
    for name, control in controls.items():
        require(isinstance(control, dict) and set(control) == {'mode', 'requested', 'policy', 'repaired'}, 'Gson effect control shape differs')
        policy = control.get('policy')
        require(isinstance(policy, dict) and set(policy) == {'enabled', 'preserveSalientLighting', 'bands', 'mediumWorkRatio', 'farWorkRatio'},
            'Gson effect policy shape differs')
        require(control['mode'] == 'INHERIT' and control['repaired'] is False and control['requested'] == policy
            and policy['enabled'] is False and policy['preserveSalientLighting'] is True
            and finite(policy['mediumWorkRatio'], 'medium ratio') == 1 and finite(policy['farWorkRatio'], 'far ratio') == 1,
            'Unexpected effect override/repair')
        bands = policy['bands']
        require(isinstance(bands, dict) and set(bands) == {'nearBlocks', 'farBlocks', 'transitionBlocks'}, 'Effect bands incomplete')
        near, far, width = [finite(bands[k], k) for k in ('nearBlocks', 'farBlocks', 'transitionBlocks')]
        require(near > 0 and far > near and 0 <= width <= min(2 * near, far - near), 'Invalid effect bands')
        settings = disk[name.lower()]
        expected = dict(mode='INHERIT', near=near, far=far, transition=width, mediumWorkRatio=1,
            farWorkRatio=1, preserveSalientLighting=True)
        require(settings == expected, 'Parsed effect settings differ from unrepaired snapshot')


def validate_state(state, enabled):
    require(type(enabled) is bool, 'Actual expected global must be boolean')
    require(integer(state.get('water_shadow_zero_control_receipt_schema'), 'water shadow zero control receipt schema') == 2,
        'Explicit water shadow zero control receipt schema 2 required')
    base_semantic_state(state)
    require(state.get('mod_present') is True and state.get('shaders_in_use') is True and state.get('fallback') is False,
        'Actual mod/Iris pipeline missing or fallback')
    require(state.get('hide_gui') is True and integer(state.get('gui_scale'), 'GUI scale') == 2, 'Fixed HUD policy differs')
    require(state.get('shader_pack') == 'water-shadow-zero-enabled.zip', 'Exact selected water archive name differs')
    require(state.get('lod_settings', {}).get('enabled') is enabled and
        state['loaded_semantic_config']['lod'].get('enabled') is enabled, 'Wrong actual live global flag')
    require(state.get('actual_shader_sha256') == ARCHIVE, 'Actual source archive differs')
    options = state.get('effective_shader_options')
    require(isinstance(options, dict) and len(options) == 368 and all(isinstance(k, str) and isinstance(v, str) for k, v in options.items())
        and hashed(options) == OPTIONS_HASH and state.get('effective_shader_options_sha256') == OPTIONS_HASH,
        'Complete exact 368-key loaded option map differs')
    source = state.get('water_shadow_zero_source_qualification', {})
    require(isinstance(source, dict) and set(source) == {'qualified', 'source_fingerprint', 'complete_member_count',
        'complete_member_sha256', 'option_count', 'complete_options_sha256'}, 'Unknown source qualification fields')
    fingerprint = source.get('source_fingerprint', {})
    require(isinstance(fingerprint, dict) and set(fingerprint) == {'sourceSha256', 'memberSha256'}, 'Unknown source fingerprint fields')
    members = fingerprint.get('memberSha256')
    require(source.get('qualified') is True and fingerprint.get('sourceSha256') == ARCHIVE
        and isinstance(members, dict) and len(members) == 533 and hashed(members) == MEMBERS_HASH
        and integer(source.get('complete_member_count'), 'complete members') == 533 and source.get('complete_member_sha256') == MEMBERS_HASH
        and integer(source.get('option_count'), 'complete options') == 368 and source.get('complete_options_sha256') == OPTIONS_HASH,
        'Full source fingerprint/member/options qualification differs')
    require(all(isinstance(k, str) and k and require_sha(v) for k, v in members.items()), 'Source member map malformed')
    require(state.get('water_shadow_zero_source_qualification_sha256') == hashed(source), 'Source qualification canonical fingerprint differs')
    require('water_shadow_zero_adapter_binding' not in state and 'water_shadow_zero_availability' not in state
        and not any(k.startswith('live_pixel_') for k in state),
        'Core-only prototype cannot claim adapter availability')
    normalized, parsed = configuration(state.get('configuration_files'))
    require(normalized == state.get('water_shadow_zero_configuration_semantics'), 'Independent configuration normalization differs')
    model = parsed[MAIN]
    loaded = copy.deepcopy(state['loaded_semantic_config'])
    loaded.pop('effects', None)
    persisted = model['lod']['enabled']
    expected = copy.deepcopy(loaded)
    expected['lod']['enabled'] = persisted
    require(model == expected, 'Complete parsed main settings differ from loaded semantics')
    require({k: loaded['lod'].get(k) for k in ('preset', 'near', 'far', 'transition', 'debug')} ==
        dict(preset='PERFORMANCE', near=32, far=96, transition=8, debug=False)
        and loaded['terrain'].get('vanillaChunkCap') == 16 and loaded['terrain'].get('warmupSeconds') == 30,
        'Fixed core/terrain recipe differs')
    for key, value in state.items():
        if key.startswith('config/') and key.endswith('_sha256'):
            name = key[:-7]
            require(name in state['configuration_files']['files'] and value == state['configuration_files']['files'][name]['sha256'],
                'Captured config hash alias differs: ' + name)
    settings = state.get('lod_settings', {})
    require(settings.get('debug') is False and loaded['lod']['debug'] is False, 'Debug enabled')
    for name in ('near', 'far', 'transition', 'preset'):
        if name in settings:
            require(settings[name] == loaded['lod'][name], 'Actual LOD settings differ from loaded semantics')
    for section, names in (('terrain', ('enabled', 'warmSourceConfirmed')),
        ('rendering', ('optimizeShadowCulling', 'separateShadowVisibilityLists', 'shadowFrustumCoherence'))):
        require(all(loaded[section].get(n) is False for n in names), 'Unexpected terrain/culling config')
    require(all(state.get(n) is False for n in FALSE_FLAGS + ('shadow_coherence_configured', 'shadow_coherence_active')),
        'Unexpected active/configured terrain/culling')
    validate_effects(state, parsed)
    require(state.get('water_shadow_zero_dh_present') is False and state.get('water_shadow_zero_camera_type') == 'FIRST_PERSON'
        and integer(state.get('water_shadow_zero_fov'), 'fixed FOV') == 70, 'Fixed noDH first-person/FOV differs')
    for name, value in (('framebuffer_width', 2976), ('framebuffer_height', 1672), ('requested_chunks', 24), ('effective_chunks', 24), ('day_time', 12500)):
        require(integer(state.get(name), name) == value, 'Native extent/chunks/time differs: ' + name)
    for name, value in (('x', 500.5), ('y', 68), ('z', 660.5), ('yaw', 87.38), ('pitch', 3.735), ('rain', 0), ('thunder', 0)):
        # The source camera guard uses exact fixed coordinates / float angles;
        # Gson serializes those Float values as these shortest decimal tokens.
        require(finite(state.get(name), name) == value, 'Fixed coastal pose/weather differs: ' + name)
    require(state.get('dimension') == 'minecraft:overworld', 'Wrong world dimension')
    require_sha(state.get('control_sha256'))
    window = state.get('water_shadow_zero_window_state', {})
    require(window.get('availability') == 'available' and window.get('focused') == 'true'
        and window.get('visible') == 'true' and window.get('iconified') == 'false', 'Window hidden/unknown')
    for name, value in (('framebuffer_width', 2976), ('framebuffer_height', 1672), ('window_width', 1488), ('window_height', 836)):
        require(integer(window.get(name), name) == value, 'Native logical/output window differs')
    pacing = window.get('pacing', {})
    require(pacing.get('vsync') is False and pacing.get('fullscreen') is False and pacing.get('pause_on_lost_focus') is False
        and pacing.get('configured_framerate_limit') == 260, 'Fixed pacing differs')
    return persisted


def validate_artifacts(report, receipt):
    require(isinstance(receipt, dict) and isinstance(receipt.get('expected_loaded_mods'), dict), 'Independent artifact receipt required')
    evidence = report.get('loaded_artifact_evidence', {})
    require(evidence.get('schema') == 1 and evidence.get('valid') is True and evidence.get('errors') == []
        and evidence.get('complete_mod_coverage') is True and evidence.get('complete_identities') is True,
        'Direct artifact evidence incomplete/invalid')
    require(report.get('loaded_mods') == evidence.get('loaded_mods') == receipt['expected_loaded_mods'] == MODS
        and len(report['loaded_mods']) == 10 and 'distanthorizons' not in report['loaded_mods'], 'Exact 10-mod registry differs')
    inventory = evidence.get('loaded_artifact_inventory')
    require(isinstance(inventory, list) and len(inventory) == 9 and inventory == report.get('loaded_artifact_inventory'),
        'Exact direct 9-root inventory differs')
    validate_volume_artifacts(report, receipt.get('loaded_directory_identities'), receipt.get('loaded_artifact_inventory'))


def timing(phase, report, gpu_epochs, cpu_epochs):
    first, last = phase['startFrame'], phase['endFrame']
    gpu, cpu = phase.get('gpu'), phase.get('cpu')
    require(isinstance(gpu, list) and isinstance(cpu, list), 'Timing arrays missing')
    require(len(gpu) >= 60 and all(s.get('scope') == 'game-render' and finite(s.get('milliseconds'), 'GPU ms') > 0 for s in gpu)
        and len({s.get('frameId') for s in gpu}) == len(gpu), 'Insufficient/nonpositive/duplicate/mixed GPU frame observations')
    validate_samples(gpu, phase.get('gpuEpoch'), first, last, ('milliseconds',))
    validate_samples(cpu, phase.get('cpuEpoch'), first, last, ('wallMilliseconds', 'threadMilliseconds'))
    require(report['cpu_requested'] == bool(cpu), 'CPU requested/observed mismatch')
    ids = [s['frameId'] for s in gpu]
    require(all(b-a == 5 for a,b in zip(ids,ids[1:])) and ids[0]-first <= 5 and last-ids[-1] <= 10,
        'Incomplete/nonmonotonic N5 GPU phase coverage')
    if cpu:
        # CpuTimers.begin() records these ordinary coarse nested scopes even
        # with cpuLayerTiming=false. Frame identity is unique within a scope.
        scopes = {'run-tick', 'game-render', 'render-and-blit', 'swap-and-events'}
        require({s.get('scope') for s in cpu} == scopes
            and len({(s['scope'], s['frameId']) for s in cpu}) == len(cpu),
            'Unknown/missing/duplicate coarse CPU scope/frame stream')
        frames = {scope: [s['frameId'] for s in cpu if s['scope'] == scope] for scope in scopes}
        allowed_frames = (list(range(first + 1, last + 1)),
            list(range(first + 1, last)), list(range(first + 2, last + 1)))
        require(all(ids in allowed_frames for ids in frames.values()),
            'Incomplete/misaligned/noncontiguous coarse CPU frame coverage')
        common = set.intersection(*(set(v) for v in frames.values()))
        require(len(common) >= phase['renderFrames']-2 and common == set(range(min(common),max(common)+1)),
            'Coarse CPU clocks do not share contiguous phase coverage')
        require(all(s.get('threadMilliseconds') is not None for s in cpu), 'Coarse thread timing unknown')
        # Nested clocks remain separate in stream(); never sum parent/children.
    for samples, key, epochs in ((gpu, 'gpuEpoch', gpu_epochs), (cpu, 'cpuEpoch', cpu_epochs)):
        if samples:
            require(phase[key] not in epochs and (not epochs or phase[key] > max(epochs)), 'Timing epoch reused/nonmonotonic across phases')
            epochs.add(phase[key])
    snapshot = phase.get('timingSnapshot', {})
    require(snapshot.get('backend') == report['backend'] and snapshot.get('samplingPolicy') == SAMPLING
        and integer(snapshot.get('sampleInterval'), 'actual sample interval') == 5 and integer(snapshot.get('renderFrames'), 'snapshot frame') == last
        and set(snapshot.get('scopes', {})) == {'game-render'}, 'Timing snapshot backend/cadence/frame/scopes differs')
    pending = integer(snapshot.get('pendingQueries'), 'pending queries')
    scheduled = integer(snapshot.get('scheduledSampledFrames'), 'scheduled frames')
    require(pending <= 12 and abs(scheduled - phase['renderFrames'] / 5) <= 1, 'Query ring/scheduled-frame cap exceeded')
    stats = snapshot['scopes']['game-render']
    total = integer(stats.get('totalSamples'), 'total samples')
    require(integer(stats.get('droppedQueries'), 'dropped queries') == 0 and integer(stats.get('zeroSamples'), 'zero queries') == 0
        and len(gpu) <= total <= len(gpu) + 2 and integer(stats.get('samples'), 'history samples') == min(240, total)
        and 0 <= scheduled - total <= pending + 2, 'Dropped/zero/history/sample counter cap exceeded')
    return stream(gpu, 'milliseconds'), stream(cpu, 'wallMilliseconds'), stream(cpu, 'threadMilliseconds')


def validate_window(phase):
    validate_fixed_window(phase, 'water_shadow_zero_window_state')
    capture = phase['windowState']['capture']; count = integer(capture.get('observations'), 'window observations')
    require(capture.get('window_size_counts') == {'1488x836': count}
        and capture.get('framebuffer_size_counts') == {'2976x1672': count}, 'Sampled native window dimensions differ')
    require(integer(capture['start'].get('frame_id'), 'window start frame') == phase['startFrame']
        and integer(capture['end'].get('frame_id'), 'window end frame') == phase['endFrame'], 'Window/phase boundary frames differ')


def screen(summaries):
    """Preregistered rejection-only arithmetic; no original/native denominator."""
    require(len(summaries) == 4, 'Four summaries required')
    metrics = ('frame_mean_ms', 'gpu_median_ms')
    for row in summaries:
        require(all(finite(row.get(k), k) > 0 for k in metrics), 'Positive finite frame/GPU metrics required')
    off_drift = {k: abs(summaries[2][k] / summaries[0][k] - 1) for k in metrics}
    on_drift = {k: abs(summaries[3][k] / summaries[1][k] - 1) for k in metrics}
    fps_gain = [summaries[i]['frame_mean_ms'] / summaries[i+1]['frame_mean_ms'] - 1 for i in (0,2)]
    frame_reduction = [1-summaries[i+1]['frame_mean_ms']/summaries[i]['frame_mean_ms'] for i in (0,2)]
    gpu_reduction = [1-summaries[i+1]['gpu_median_ms']/summaries[i]['gpu_median_ms'] for i in (0,2)]
    verdict = ('inconclusive-off-control-drift' if max(off_drift.values()) > .10 + 1e-12 else
        'stop-no-useful-incremental-signal' if min(frame_reduction) <= .05 + 1e-12 or min(gpu_reduction) <= 0 else
        'promising-incremental-rejection-screen-only')
    return dict(verdict=verdict,off_frame_gpu_drift=off_drift,on_frame_gpu_drift=on_drift,
        adjacent_fps_gain=fps_gain,adjacent_frame_reduction=frame_reduction,adjacent_gpu_reduction=gpu_reduction,
        denominator='Same candidate guard/selector OFF; original native is separate',performanceEvidence=False)


def validate_manifest(manifest):
    require(isinstance(manifest, dict) and manifest.get('source_world_sha256') == WORLD,
        'Frozen source save differs')
    require('adaptive_pack_sha256' not in manifest, 'Old legacy feature manifest forbidden')
    source = manifest.get('source_qualification', {})
    members = source.get('complete_member_sha256')
    require(source.get('archive_sha256') == ARCHIVE and isinstance(members, dict)
        and len(members) == 533 and hashed(members) == MEMBERS_HASH,
        'Frozen complete water-shadow-zero archive/member manifest differs')
    require(manifest.get('schema') == 1 and manifest.get('scene') == 'coastalwater'
        and manifest.get('configuration_only') is False, 'Explicit water-shadow-zero manifest scene/schema differs')
    model = manifest.get('experiment_arms', {}).get('toggle', {})
    options = model.get('expected_effective_options')
    require(model.get('archive_sha256') == ARCHIVE and isinstance(options, dict)
        and len(options) == 368 and hashed(options) == OPTIONS_HASH
        and model.get('expected_effective_options_sha256') == OPTIONS_HASH,
        'Frozen complete water-shadow-zero archive/options model differs')
    recipe = manifest.get('benchmark_recipe', {})
    require(recipe.get('feature') == 'water_shadow_zero' and recipe.get('phases') == [False, True, False, True]
        and all(type(v) is bool for v in recipe['phases']),
        'Frozen water-shadow-zero feature/order differs')
    return 'explicit-complete-water-shadow-zero-source-schema1'


def analyze_report(report, artifact_receipt, profile=None):
    pins = source_pins()
    require(integer(report.get('schema'), 'report schema') == 3 and report.get('status') == 'complete' and report.get('benchmark_feature') == 'water_shadow_zero',
        'Require complete explicit schema-3 WATER_SHADOW_ZERO report')
    require_uuid(report.get('session_id'))
    require(report.get('method') == METHOD and report.get('selected_flag') == 'lod.enabled'
        and report.get('starting_order') == 'OFF/ON/OFF/ON' and report.get('atmosphere_guard_absent') is True,
        'Explicit water shadow zero method/selected flag/order/atmosphere guard differs')
    require(integer(report.get('core_protocol'), 'expected core protocol') == 2 and report.get('transport') == 'core_global_only'
        and report.get('water_stage_runtime_qualification') is False and report.get('executed_skip_coverage_proof') is False
        and integer(report.get('control_receipt_schema'), 'header receipt schema') == 2
        and report.get('water_shadow_zero_diagnostic_limits') == PRODUCER_LIMITS
        and 'raster_policy_proof' not in report, 'Water-specific core-only transport/limits declarations differ')
    require(report.get('configured_hide_gui') is True, 'Explicit producer hide GUI header missing')
    require(isinstance(report.get('initial_enabled'), bool) and report.get('restored_initial_enabled') is True
        and type(report.get('initial_feature_enabled')) is bool and report['initial_feature_enabled'] is report['initial_enabled']
        and report.get('restored_initial_feature') is True, 'Actual initial state/restoration missing')
    require(report.get('gpu_requested') is True and report.get('cpu_requested') is True
        and report.get('backend') in ('ELAPSED_ALTERNATING', 'TIMESTAMP') and report.get('sampling_policy') == SAMPLING,
        'Full-frame GPU timing/cadence required')
    for name, minimum in (('initial_warmup_seconds', 30), ('settle_seconds', 8), ('measurement_seconds', 20)):
        require(finite(report.get(name), name) >= minimum, 'Insufficient warmup/settle/capture declaration')
    props = report.get('instrumentation_properties', {})
    require(props.get('benchmarkFeature') == 'water_shadow_zero' and props.get('gpuTiming') == 'true'
        and props.get('gpuSampleInterval') == '5' and props.get('cpuTiming') == str(report['cpu_requested']).lower(),
        'Explicit instrumentation feature/timers/cadence differs')
    require(all(props.get(n) == 'false' for n in PROBES), 'Diagnostic/pass/layer observer enabled/missing declaration')
    require(props.get('fixedWetness') == '0' and props.get('performanceStack') == 'minimal' and props.get('includeDh') == 'false',
        'Actual fixed wetness/minimal/noDH instrumentation differs')
    validate_artifacts(report, artifact_receipt)
    manifest_contract = validate_manifest(report.get('workload-manifest.json'))
    phases = report.get('phases')
    require(isinstance(phases, list) and len(phases) == 4, 'Exactly four phases required')
    reference = report.get('stable_input_reference')
    require(isinstance(reference, dict), 'Stable reference missing')
    ids, images, gpu_epochs, cpu_epochs = set(), set(), set(), set()
    previous = -1
    summaries = []
    for index, phase in enumerate(phases, 1):
        require(phase.get('status') == 'complete' and integer(phase.get('index'), 'phase index') == index and phase.get('enabled') is (index % 2 == 0),
            'Incomplete/misordered/incorrect OFF ON phase')
        require_uuid(phase.get('id'))
        require(phase['id'] not in ids, 'Duplicate phase UUID')
        ids.add(phase['id'])
        first, last = integer(phase.get('startFrame'), 'start frame'), integer(phase.get('endFrame'), 'end frame')
        frames = integer(phase.get('renderFrames'), 'render frames')
        require(first > previous and last > first and frames == last - first and frames >= 30, 'Overlapping/mismatched phase frames')
        previous = last
        elapsed = finite(phase.get('elapsedSeconds'), 'actual capture seconds')
        require(elapsed >= max(20, report['measurement_seconds'] * .95)
            and finite(phase.get('settleElapsedSeconds'), 'actual settle seconds') >= report['settle_seconds'], 'Actual settling/capture too short')
        persisted = []
        for boundary in ('inputStart', 'inputEnd'):
            state = phase.get(boundary, {})
            persisted.append(validate_state(state, phase['enabled']))
            require(stable(state) == reference, 'Stable inputs differ beyond selected global flag/persisted boolean')
        # Only persisted main enabled may lag; live in-phase semantics must be unchanged.
        a, b = [stable(phase[k]) for k in ('inputStart', 'inputEnd')]
        require(a == b, 'In-phase input mutation')
        validate_window(phase)
        validate_temporal(phase)
        require(phase['temporalStart']['wetness']['fixedWetness'] == 0, 'Fixed wetness must be zero')
        gpu, wall, thread = timing(phase, report, gpu_epochs, cpu_epochs)
        image = phase.get('image')
        require(isinstance(image, str) and Path(image).name == image and image.endswith('.png') and image not in images,
            'Unique local phase PNG required')
        images.add(image)
        image_hash = require_sha(phase.get('imageSha256'))
        if profile is not None:
            path = Path(profile) / 'screenshots' / image
            require(path.is_file() and digest(path) == image_hash, 'Phase PNG missing/hash differs')
            with path.open('rb') as source:
                header = source.read(24)
                require(len(header) == 24 and header[:8] == b'\x89PNG\r\n\x1a\n' and header[12:16] == b'IHDR'
                    and struct.unpack('>II', header[16:24]) == (2976, 1672), 'Phase image is not native PNG')
        fps = frames / elapsed
        summaries.append(dict(index=index, enabled=phase['enabled'], phase_id=phase['id'], render_frames=frames,
            elapsed_seconds=elapsed, render_fps=fps, frame_mean_ms=1000 / fps,
            gpu_mean_ms=metric(gpu, 'game-render'), gpu_median_ms=metric(gpu, 'game-render', 'median_ms'),
            gpu=gpu, cpu_wall=wall, cpu_thread=thread, persisted_global_start_end=persisted,
            source_frame_interval=[first, last], gpu_epoch=phase['gpuEpoch'], cpu_epoch=phase.get('cpuEpoch'),
            image=image, image_sha256=image_hash, image_verified=profile is not None))
    keys = ('frame_mean_ms', 'gpu_mean_ms', 'gpu_median_ms')
    pairs = [dict(off_phase=a['index'], on_phase=b['index'], on_vs_off_percent_time_reduction={k: time_reduction(a[k], b[k]) for k in keys},
        on_vs_off_fps_change_percent=100 * (b['render_fps'] / a['render_fps'] - 1)) for a, b in ((summaries[0], summaries[1]), (summaries[2], summaries[3]))]
    drift = {name: {k: time_reduction(summaries[i][k], summaries[i + 2][k]) for k in keys} for name, i in (('OFF', 0), ('ON', 1))}
    require(source_pins() == pins, 'Analyzer/producer source changed during qualification')
    return dict(schema=1, status='QUALIFIED_SETTLED_INCREMENTAL_DIAGNOSTIC_ONLY', benchmark_feature='water_shadow_zero',
        water_shadow_zero_control_receipt_schema=2,
        session_id=report['session_id'], selected_flag='lod.enabled', method=METHOD, shader_sha256=ARCHIVE, manifest_contract=manifest_contract,
        phases=summaries, adjacent_pairs=pairs, same_state_drift=drift,
        rejection_screen=screen(summaries),performanceEvidence=False,original_native_gain=False,
        source_report_sha256=hashed(report), artifact_receipt_sha256=hashed(artifact_receipt),
        analyzer_source_files_sha256=pins,
        loaded_artifact_evidence=copy.deepcopy(report['loaded_artifact_evidence']),
        limits=LIMITS, transition_accepted=False, quality_accepted=False, repeated_performance_accepted=False,
        water_stage_runtime_qualification=False, executed_skip_coverage_proof=False, transport='core_global_only', core_protocol=2,
        restoration={'initial_enabled': report['initial_enabled'], 'restored_initial_enabled': True,
            'restored_initial_feature': True, 'postshutdown_byte_identity_verified': False},
        uncertainty='Raw object world/pipeline identities and clocks are not exported; source runtime guard declarations alone do not establish driver/coverage or independent repeats.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--artifact-receipt', type=Path, required=True)
    parser.add_argument('--profile', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = json.loads(args.report.read_text())
    receipt = json.loads(args.artifact_receipt.read_text())
    try:
        result = analyze_report(report, receipt, args.profile)
        code = 0
    except (ValueError, TypeError, KeyError) as failure:
        result = dict(schema=1, status='FAILED', error=str(failure), report=str(args.report),
            report_raw_sha256=digest(args.report), artifact_receipt_raw_sha256=digest(args.artifact_receipt), limits=LIMITS)
        code = 1
    result.update(analyzer_source_sha256=digest(Path(__file__)), report_path=str(args.report), report_raw_sha256=digest(args.report),
        artifact_receipt_path=str(args.artifact_receipt), artifact_receipt_raw_sha256=digest(args.artifact_receipt))
    encoded = json.dumps(result, indent=2, sort_keys=True, allow_nan=False) + '\n'
    if args.output:
        require(not args.output.exists(), 'Preserve previous derived analysis')
        root = Path(__file__).resolve().parents[1]
        require(args.output.resolve().is_relative_to(root/'dev/logs') or args.output.resolve().is_relative_to(root/'build'),
            'Derived output must remain ignored')
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded)
    else:
        print(encoded, end='')
    raise SystemExit(code)


if __name__ == '__main__':
    main()
