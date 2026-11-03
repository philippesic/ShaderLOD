#!/usr/bin/env python3
"""Strict four-phase actual same-JVM fixedgraph Photon air-fog cost qualification."""
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
from urllib.parse import urlsplit, unquote

from analyze_inprocess_control import (canonical, digest, finite, integer, metric,
    require_sha, require_uuid, time_reduction,
    validate_fixed_window, validate_samples, validate_temporal, validate_volume_artifacts,
    FALSE_FLAGS, SPATIAL_EFFECTS)
from summarize_workload import stream
from qualify_volume_fast_artifacts import MODS
from run_proof_matrix import read_option_properties

import prepare_photon_air_fog_inprocess as prep
import prepare_photon_air_fog_ablation_runtime as driver
import analyze_photon_air_fog_ablation_cost as standalone
from qualify_volume_fast_artifacts import validate_inventory_against_receipt, java_file_uri

ROOT=prep.ROOT
RECEIPT_SHA256=prep.RECEIPT_SHA256  # Exact supplied preparation receipt, never an inferred revision.
OWNER_CLASSES=('com.pp.shaderlod.dev.BenchmarkSession','com.pp.shaderlod.dev.WorkloadInputs')
METHOD = 'One JVM/world/pipeline; pinned native VL=true Photon; only core global changes; deliberate C0 air-fog removal cost diagnostic, not quality optimization'
PRODUCER_LIMITS = 'Private OFF denominator, purposeful fog loss; no original-quality gain. C0 identity, neutral RT6/RT7 texels and physical body retirement remain unknown. CPU rain getter and wetness supplier evidence are not GL coefficients. Same-JVM correlation does not replace independent repeats.'
LIMITS = 'Four correlated settled private fixedgraph phases; deliberate fog loss, not quality/mod/original gain or strict additive body cost. Actual C0 stage, neutral RT6/RT7 and retired work remain unobserved. Raw runtime world/pipeline/camera object IDs unexported. CPU rain/wetness evidence is not GL coefficients/biome/step eligibility. Independent repeats remain necessary.'
ARCHIVE='e5f3c0605e5fd1e164c6996cbdf372e547fb964565c73d0deb5377e1e91f9c1b'
MEMBERS_HASH='d9cc9a2b8799b5db5e55b9767623a6be7fae4a35966611762cff4d2aeea56bb2'
OPTIONS_HASH='c65d0f75579d75b86668d5e2ef607e37b2dbb824e4b3a18ad7dcb0ea2f078ab6'
WORLD='89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46'
SAMPLING='frame only, every sampled frame; sample interval=5 render frames, poll only sampled frames'
MAIN='config/shaderlod-client.toml'
BACKUP='config/shaderlod-client-1.toml.bak'
EFFECTS='config/shaderlod-effects-client.toml'
IRIS='config/iris.properties'
SIDECAR='shaderpacks/air-fog-cost-ablation-enabled.zip.txt'

_raw_digest=digest
def digest(path):return _raw_digest(Path(path))
PROBES = ('shadowCoherenceOracle', 'visibilityLists', 'shadowObserve', 'workTrace',
    'workTraceOnly', 'spatialLayoutProbe', 'spatialLayoutOnly', 'cloudSamplerProbe',
    'dhDrawEvidence', 'waterSceneSurvey', 'smokeTest', 'gpuBenchmark', 'cameraQualityOnly',
    'gpuPassTiming', 'cpuLayerTiming', 'gpuAttribution', 'pixelCoverage', 'pixelCoverageOnly', 'smoke','pixelHistoryCapture','pixelHistoryCaptureOnly')


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def hashed(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def source_pins():
    root = Path(__file__).resolve().parents[1]
    paths = [Path(__file__), root/'tests/test_photon_air_fog_inprocess_analysis.py',
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
    require(iris.get('shaderPack') == 'air-fog-cost-ablation-enabled.zip' and iris.get('enableShaders') == 'true', 'Exact persisted Iris selection differs')
    return normalized, parsed


def air_semantic_state(state):
    require(integer(state.get('air_fog_ablation_control_receipt_schema'), 'air fog shadow zero control receipt schema') == 2,
        'Explicit air fog shadow zero control receipt schema 2 required')
    result = base_semantic_state(state)
    normalized, _ = configuration(state.get('configuration_files'))
    require(normalized == state.get('air_fog_ablation_configuration_semantics'), 'Independent configuration normalization differs')
    require(state.get(IRIS + '_sha256') == state['configuration_files']['files'][IRIS]['sha256'], 'Captured Iris raw hash alias differs')
    for field in ('air_fog_ablation_source_qualification', 'air_fog_ablation_effect_controls'):
        require(hashed(state.get(field)) == state.get(field + '_sha256'), 'Canonical air fog shadow zero derived hash differs: ' + field)
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
    result = air_semantic_state(state)
    result['lod_settings'].pop('enabled', None)
    result['loaded_semantic_config']['lod'].pop('enabled', None)
    return result


def validate_effects(state, parsed):
    controls = state.get('air_fog_ablation_effect_controls')
    require(isinstance(controls, dict) and set(controls) == set(SPATIAL_EFFECTS), 'Complete air fog shadow zero effects missing')
    require(controls == state['loaded_semantic_config'].get('effects'), 'Loaded effects differ from actual snapshot')
    require(state.get('air_fog_ablation_effect_controls_sha256') == hashed(controls), 'Live effects canonical fingerprint differs')
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
    require(integer(state.get('air_fog_ablation_control_receipt_schema'), 'air fog shadow zero control receipt schema') == 2,
        'Explicit air fog shadow zero control receipt schema 2 required')
    base_semantic_state(state)
    require(state.get('mod_present') is True and state.get('shaders_in_use') is True and state.get('fallback') is False,
        'Actual mod/Iris pipeline missing or fallback')
    require(state.get('hide_gui') is True and integer(state.get('gui_scale'), 'GUI scale') == 2, 'Fixed HUD policy differs')
    require(state.get('shader_pack') == 'air-fog-cost-ablation-enabled.zip', 'Exact selected air fog archive name differs')
    require(state.get('lod_settings', {}).get('enabled') is enabled and
        state['loaded_semantic_config']['lod'].get('enabled') is enabled, 'Wrong actual live global flag')
    require(state.get('actual_shader_sha256') == ARCHIVE, 'Actual source archive differs')
    options = state.get('effective_shader_options')
    require(isinstance(options, dict) and len(options) == 367 and all(isinstance(k, str) and isinstance(v, str) for k, v in options.items())
        and hashed(options) == OPTIONS_HASH and state.get('effective_shader_options_sha256') == OPTIONS_HASH,
        'Complete exact 367-key loaded option map differs')
    source = state.get('air_fog_ablation_source_qualification', {})
    require(isinstance(source, dict) and set(source) == {'qualified', 'source_fingerprint', 'complete_member_count',
        'complete_member_sha256', 'option_count', 'complete_options_sha256'}, 'Unknown source qualification fields')
    fingerprint = source.get('source_fingerprint', {})
    require(isinstance(fingerprint, dict) and set(fingerprint) == {'sourceSha256', 'memberSha256'}, 'Unknown source fingerprint fields')
    members = fingerprint.get('memberSha256')
    require(source.get('qualified') is True and fingerprint.get('sourceSha256') == ARCHIVE
        and isinstance(members, dict) and len(members) == 533 and hashed(members) == MEMBERS_HASH
        and integer(source.get('complete_member_count'), 'complete members') == 533 and source.get('complete_member_sha256') == MEMBERS_HASH
        and integer(source.get('option_count'), 'complete options') == 367 and source.get('complete_options_sha256') == OPTIONS_HASH,
        'Full source fingerprint/member/options qualification differs')
    require(all(isinstance(k, str) and k and require_sha(v) for k, v in members.items()), 'Source member map malformed')
    require(state.get('air_fog_ablation_source_qualification_sha256') == hashed(source), 'Source qualification canonical fingerprint differs')
    require('air_fog_ablation_adapter_binding' not in state and 'air_fog_ablation_availability' not in state
        and not any(k.startswith('live_pixel_') for k in state),
        'Core-only prototype cannot claim adapter availability')
    normalized, parsed = configuration(state.get('configuration_files'))
    require(normalized == state.get('air_fog_ablation_configuration_semantics'), 'Independent configuration normalization differs')
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
    require(state.get('air_fog_ablation_dh_present') is False and state.get('air_fog_ablation_camera_type') == 'FIRST_PERSON'
        and integer(state.get('air_fog_ablation_fov'), 'fixed FOV') == 70, 'Fixed noDH first-person/FOV differs')
    for name, value in (('framebuffer_width', 2976), ('framebuffer_height', 1672), ('requested_chunks', 24), ('effective_chunks', 24), ('day_time', 12500)):
        require(integer(state.get(name), name) == value, 'Native extent/chunks/time differs: ' + name)
    for name, value in (('x', 500.5), ('y', 68), ('z', 660.5), ('rain', 1), ('thunder', 0)):
        # The source camera guard uses exact fixed coordinates / float angles;
        # Gson serializes those Float values as these shortest decimal tokens.
        require(finite(state.get(name), name) == value, 'Fixed coastal pose/weather differs: ' + name)
    for key,target in (('yaw',87.38),('pitch',3.735)):
        value=finite(state.get(key),key)
        require(struct.pack('!f',value)==struct.pack('!f',target),'Fixed source Float camera angle differs: '+key)
    require(state.get('dimension') == 'minecraft:overworld', 'Wrong world dimension')
    require_sha(state.get('control_sha256'))
    window = state.get('air_fog_ablation_window_state', {})
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
    validate_fixed_window(phase, 'air_fog_ablation_window_state')
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
        'stop-no-useful-incremental-signal' if min(fps_gain) <= .05 + 1e-12 or min(gpu_reduction) <= 0 else
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
        'Frozen complete air-fog-ablation archive/member manifest differs')
    require(manifest.get('schema') == 1 and manifest.get('scene') == 'coastalwater'
        and manifest.get('configuration_only') is False, 'Explicit air-fog-ablation manifest scene/schema differs')
    model = manifest.get('experiment_arms', {}).get('toggle', {})
    options = model.get('expected_effective_options')
    require(model.get('archive_sha256') == ARCHIVE and isinstance(options, dict)
        and len(options) == 367 and hashed(options) == OPTIONS_HASH
        and model.get('expected_effective_options_sha256') == OPTIONS_HASH,
        'Frozen complete air-fog-ablation archive/options model differs')
    recipe = manifest.get('benchmark_recipe', {})
    require(recipe.get('feature') == 'air_fog_ablation' and recipe.get('phases') == [False, True, False, True]
        and all(type(v) is bool for v in recipe['phases']),
        'Frozen air-fog-ablation feature/order differs')
    return 'explicit-complete-air-fog-ablation-source-schema1'


def analyze_report(report, artifact_receipt, profile=None, prepared=None):
    pins = source_pins()
    require(integer(report.get('schema'), 'report schema') == 3 and report.get('status') == 'complete' and report.get('benchmark_feature') == 'air_fog_ablation',
        'Require complete explicit schema-3 AIR_FOG_ABLATION report')
    require_uuid(report.get('session_id'))
    require(report.get('method') == METHOD and report.get('selected_flag') == 'lod.enabled'
        and report.get('starting_order') == 'OFF/ON/OFF/ON' and report.get('atmosphere_guard_absent') is True,
        'Explicit air fog shadow zero method/selected flag/order/atmosphere guard differs')
    require(integer(report.get('core_protocol'), 'expected core protocol') == 2 and report.get('transport') == 'core_global_only'
        and all(report.get(k)is False for k in ('c0_stage_identity_proof','neutral_render_target_proof','retired_gpu_work_proof','quality_gain'))
        and integer(report.get('control_receipt_schema'), 'header receipt schema') == 2
        and report.get('air_fog_ablation_diagnostic_limits') == PRODUCER_LIMITS
        and 'raster_policy_proof' not in report, 'Air fog-specific core-only transport/limits declarations differ')
    require(report.get('configured_hide_gui') is True, 'Explicit producer hide GUI header missing')
    require(report.get('initial_enabled') is False and report.get('restored_initial_enabled') is True
        and type(report.get('initial_feature_enabled')) is bool and report['initial_feature_enabled'] is report['initial_enabled']
        and report.get('restored_initial_feature') is True, 'Actual initial state/restoration missing')
    require(report.get('gpu_requested') is True and report.get('cpu_requested') is True
        and report.get('backend') in ('ELAPSED_ALTERNATING', 'TIMESTAMP') and report.get('sampling_policy') == SAMPLING,
        'Full-frame GPU timing/cadence required')
    for name, minimum in (('initial_warmup_seconds', 30), ('settle_seconds', 8), ('measurement_seconds', 20)):
        require(finite(report.get(name), name) >= minimum, 'Insufficient warmup/settle/capture declaration')
    props = report.get('instrumentation_properties', {})
    require(props.get('benchmarkFeature') == 'air_fog_ablation' and props.get('gpuTiming') == 'true'
        and props.get('gpuSampleInterval') == '5' and props.get('cpuTiming') == str(report['cpu_requested']).lower(),
        'Explicit instrumentation feature/timers/cadence differs')
    require(all(props.get(n) == 'false' for n in PROBES), 'Diagnostic/pass/layer observer enabled/missing declaration')
    require(props.get('fixedWetness') == '1' and props.get('performanceStack') == 'minimal' and props.get('includeDh') == 'false',
        'Actual fixed wetness/minimal/noDH instrumentation differs')
    validate_artifacts(report, artifact_receipt)
    initial_artifacts=report.get('air_fog_ablation_artifact_state_initial')
    validate_artifact_state(initial_artifacts,artifact_receipt)
    require(initial_artifacts['loaded_artifacts']==report['loaded_artifact_evidence'],'Initial direct artifact receipt differs from header')
    validate_rain_temporal(report.get('shader_temporal_inputs_initial'),False)
    previous_temporal=report['shader_temporal_inputs_initial']
    manifest_contract = validate_manifest(report.get('workload-manifest.json'))
    if prepared is not None:
        profile=Path(prepared['operation']['profile'])
        expected=json.loads((profile.parent/'workload-manifest.json').read_text())
        require(report.get('workload-manifest.json')==expected,'Exact frozen inprocess manifest differs')
        require(report.get('control.json')==prepared['prepared_profile']['inputs']['control'],'Exact frozen initial OFF/rain control differs')
        for name in ('control.json','workload-manifest.json'):
            require(report.get(name+'_sha256')==digest(profile.parent/name),'Header frozen parent file hash differs')
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
            validate_artifact_state(state.get('air_fog_ablation_artifact_state'),artifact_receipt)
            require(state['air_fog_ablation_artifact_state']==initial_artifacts,'Actual class/root registry differs at phase boundary')
            if prepared is not None:
                require(state['effective_shader_options']==prepared['expected_effective_options'],'Frozen complete367 options differ')
                require(state['air_fog_ablation_source_qualification']['source_fingerprint']['memberSha256']==prepared['source_qualification']['complete_member_sha256'],'Frozen all533 member hashes differ')
            require(stable(state) == reference, 'Stable inputs differ beyond selected global flag/persisted boolean')
        # Only persisted main enabled may lag; live in-phase semantics must be unchanged.
        a, b = [stable(phase[k]) for k in ('inputStart', 'inputEnd')]
        require(a == b, 'In-phase input mutation')
        validate_window(phase)
        validate_temporal(phase)
        for boundary in ('temporalStart','temporalEnd'):validate_rain_temporal(phase[boundary],boundary=='temporalEnd')
        advancing_clocks(previous_temporal,phase['temporalStart'])
        advancing_clocks(phase['temporalStart'],phase['temporalEnd'])
        previous_temporal=phase['temporalEnd']
        require(phase['temporalStart']['wetness']['fixedWetness'] == 1, 'Fixed wetness must be one')
        gpu, wall, thread = timing(phase, report, gpu_epochs, cpu_epochs)
        require(phase['gpuEpoch']==phase['cpuEpoch']==2*index+1,'Benchmark reset epochs differ from source sequence')
        require(all((sample['frameId']-1)%5==0 for sample in phase['gpu']),'GPU N5 phase differs from source schedule')
        wall_fraction=sum(sample['wallMilliseconds']for sample in phase['cpu']if sample['scope']=='run-tick')/(1000*elapsed)
        require(.98<=wall_fraction<=1.02,'Benchmark run-tick wall coverage differs from capture clock')
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
            elapsed_seconds=elapsed, frame_wall_coverage_fraction=wall_fraction, render_fps=fps, frame_mean_ms=1000 / fps,
            gpu_mean_ms=metric(gpu, 'game-render'), gpu_median_ms=metric(gpu, 'game-render', 'median_ms'),
            gpu=gpu, cpu_wall=wall, cpu_thread=thread, persisted_global_start_end=persisted,
            source_frame_interval=[first, last], gpu_epoch=phase['gpuEpoch'], cpu_epoch=phase.get('cpuEpoch'),
            image=image, image_sha256=image_hash, image_verified=profile is not None))
    keys = ('frame_mean_ms', 'gpu_mean_ms', 'gpu_median_ms')
    pairs = [dict(off_phase=a['index'], on_phase=b['index'], on_vs_off_percent_time_reduction={k: time_reduction(a[k], b[k]) for k in keys},
        on_vs_off_fps_change_percent=100 * (b['render_fps'] / a['render_fps'] - 1)) for a, b in ((summaries[0], summaries[1]), (summaries[2], summaries[3]))]
    drift = {name: {k: time_reduction(summaries[i][k], summaries[i + 2][k]) for k in keys} for name, i in (('OFF', 0), ('ON', 1))}
    require(source_pins() == pins, 'Analyzer/producer source changed during qualification')
    return dict(schema=1, status='QUALIFIED_SETTLED_INCREMENTAL_DIAGNOSTIC_ONLY', benchmark_feature='air_fog_ablation',
        air_fog_ablation_control_receipt_schema=2,
        session_id=report['session_id'], selected_flag='lod.enabled', method=METHOD, shader_sha256=ARCHIVE, manifest_contract=manifest_contract,
        phases=summaries, adjacent_pairs=pairs, same_state_drift=drift,
        rejection_screen=screen(summaries),performanceEvidence=False,original_native_gain=False,
        source_report_sha256=hashed(report), artifact_receipt_sha256=hashed(artifact_receipt),
        analyzer_source_files_sha256=pins,
        loaded_artifact_evidence=copy.deepcopy(report['loaded_artifact_evidence']),
        limits=LIMITS, transition_accepted=False, quality_accepted=False, repeated_performance_accepted=False,
        c0_stage_identity_proof=False, neutral_render_target_proof=False, retired_gpu_work_proof=False, quality_gain=False, transport='core_global_only', core_protocol=2,
        restoration={'initial_enabled': report['initial_enabled'], 'restored_initial_enabled': True,
            'restored_initial_feature': True, 'postshutdown_byte_identity_verified': False},
        uncertainty='Raw object world/pipeline identities and clocks are not exported; source runtime guard declarations alone do not establish driver/coverage or independent repeats.')


def namespace_identity(roots):
    """Independent full overlay Merkle identity from externally pinned module roots."""
    entries={}
    for root in roots:
        root=Path(root)
        require(root.is_dir()and not root.is_symlink(),'Pinned namespace root missing/symlink')
        for path in sorted(root.rglob('*')):
            require(not path.is_symlink(),'Pinned module namespace contains symlink')
            if not path.is_file():continue
            name=path.relative_to(root).as_posix();item=(digest(path),path.stat().st_size)
            require(name not in entries,'Ambiguous module-root overlay member: '+name)
            entries[name]=item
    require(0<len(entries)<=5000 and 0<sum(v[1]for v in entries.values())<=32*1024*1024,'Complete namespace bounds differ')
    data=''.join(name+'\0'+entries[name][0]+'\n'for name in sorted(entries))
    return dict(kind='development directory; regular files only, symlinks excluded',complete=True,
        files=len(entries),bytes=sum(v[1]for v in entries.values()),tree_sha256=hashlib.sha256(data.encode()).hexdigest())


def source_uri_binding(uri,provider,primary,member=''):
    """Validate URI namespace only AFTER typed primary-root binding, never resolve roots by stripping URI text."""
    require(isinstance(uri,str),'Owning URI missing')
    parsed=urlsplit(uri)
    require(parsed.scheme==provider and not parsed.netloc and not parsed.query and not parsed.fragment,'Owning URI provider/authority/query/fragment differs')
    path=unquote(parsed.path)
    if provider=='file':
        expected=str(Path(primary)/member)+('/'if not member else '')
        require(path==expected,'File owning URI/member binding differs')
        return ('file',str(primary),member)
    require(provider=='union','Unknown owning filesystem provider')
    prefix=str(primary).rstrip('/')+'/'
    require(path.startswith(prefix),'Union URI does not bind the supplied physical primary root')
    tail=path[len(prefix):];match=re.fullmatch(r'#(0|[1-9][0-9]*)!/'+re.escape(member),tail)
    require(match is not None,'Union module-root/class-member URI layout differs')
    return ('union',str(primary),match[1],member)


def validate_owning_source(record,name,primary,class_pin,virtual_identity):
    fields={'uri','filesystem_provider','path','identity','primary_root','class_member','class_resource_uri','class_sha256',
        'raw_code_source_uri','raw_class_resource_uri'}
    require(isinstance(record,dict)and set(record)==fields,'Unknown/incomplete owning source record')
    require(record['primary_root']==primary,'Owning physical primary root/module identity differs')
    provider=record['filesystem_provider'];member=name.replace('.','/')+'.class'
    require(record['class_member']==member and record['class_sha256']==class_pin,'Owning class resource/member/hash differs')
    root=primary['path'];require(provider in ('file','union'),'Unknown owning provider')
    require(record['path']==('/'if provider=='union'else root),'Owning source is not exact module root')
    require(record['identity']==(virtual_identity if provider=='union'else primary['identity']),'Complete virtual/physical source namespace fingerprint differs')
    code=source_uri_binding(record['uri'],provider,root)
    resource=source_uri_binding(record['class_resource_uri'],provider,root,member)
    require(code[:-1]==resource[:-1],'Class resource has different Union module identifier')
    require(source_uri_binding(record['raw_code_source_uri'],provider,root)==code
        and source_uri_binding(record['raw_class_resource_uri'],provider,root,member)==resource,'Raw loader/resource URI binding differs')
    return record


def validate_artifact_state(state,receipt):
    require(isinstance(state,dict)and set(state)=={'loaded_artifacts','owning_code_sources'},'Complete owning artifact-state schema required')
    evidence=state['loaded_artifacts']
    require(isinstance(evidence,dict)and set(evidence)=={'schema','loaded_mods','loaded_artifact_inventory','errors','limits','complete_mod_coverage','complete_identities','valid'},'Unknown direct artifact-state fields')
    require(evidence['schema']==1 and evidence['valid']is True and evidence['errors']==[]
        and evidence['complete_mod_coverage']is True and evidence['complete_identities']is True,'Invalid direct boundary artifact evidence')
    require(evidence['loaded_mods']==receipt['expected_loaded_mods']==MODS,'Complete boundary mod registry differs')
    validate_inventory_against_receipt(evidence['loaded_artifact_inventory'],receipt['loaded_artifact_inventory'],evidence['loaded_mods'])
    selected=[r for r in evidence['loaded_artifact_inventory']if r['mods']=={'shaderlod_dev':MODS['shaderlod_dev']}]
    require(len(selected)==1 and selected[0]['filesystem_provider']=='file'and isinstance(selected[0]['identity'],dict),'One exact physical owning development root required')
    primary=selected[0];root=Path(primary['path'])
    require(primary['identity']==receipt['loaded_directory_identities'].get(str(root)),'Exact physical development directory differs')
    folders=receipt['launch_artifact_contract']['fml_mod_folders'].split(':')
    module_roots=[Path(s.split('%%',1)[1])for s in folders if s.startswith('shaderlod_dev%%')]
    require(len(module_roots)==2 and root in module_roots,'Pinned module class/resources binding differs')
    for path in module_roots:require(namespace_identity([path])==receipt['loaded_directory_identities'].get(str(path)),'Pinned module-root filesystem identity differs')
    virtual=namespace_identity(module_roots)
    owners=state['owning_code_sources'];require(isinstance(owners,dict)and set(owners)==set(OWNER_CLASSES),'Owning class coverage differs')
    for name in OWNER_CLASSES:
        path=root/Path(name.replace('.','/')+'.class');pin=receipt['independent_evidence_files_sha256'].get(str(path))
        require(pin is not None and digest(path)==pin,'Exact supplied owning class bytes differ: '+name)
        validate_owning_source(owners[name],name,primary,pin,virtual)
    return state


def validate_rain_temporal(temporal,capture):
    require(isinstance(temporal,dict),'Actual air temporal object missing')
    require(temporal.get('clock_policy')=='normal; counter/frame-time remain real','Normal advancing clock declaration differs')
    for key,target in dict(day_time_source=12500,world_time_source=12500,world_day_source=0,photon_world_age_source=625,rain_strength_source=1).items():
        require(finite(temporal.get(key),key)==target,'Rain/day/world-age CPU source differs: '+key)
    for key in ('frame_counter_source','frame_time_counter_source','frame_time_source'):
        require(finite(temporal.get(key),key)>0,'Positive advancing source clock required: '+key)
    wetness=temporal.get('wetness',{})
    require(wetness.get('mode')=='fixed'and finite(wetness.get('fixedWetness'),'fixed wetness')==1,'Fixed wetness1 required')
    suppliers=wetness.get('suppliers');require(isinstance(suppliers,list),'Actual supplier list missing')
    observed=[s for s in suppliers if integer(s.get('supplierReads'),'supplier reads')>0]
    require(observed,'Active wetness supplier observations missing')
    for supplier in observed:
        require(abs(finite(supplier.get('appliedWetness'),'applied wetness')-1)<=1e-6,'Applied wetness differs')
        if capture and integer(supplier.get('measurementReads'),'supplier capture reads')>0:
            require(all(abs(finite(supplier.get(k),k)-1)<=1e-6 for k in ('appliedMin','appliedMax')),'Captured wetness range differs')
    if capture:require(integer(wetness.get('measurementReads'),'capture wetness reads')>0,'No captured wetness observations')


def advancing_clocks(previous,current):
    for name in ('frame_counter_source','frame_time_counter_source'):
        require(finite(current.get(name),name)>finite(previous.get(name),name),'Normal clock did not strictly advance: '+name)


def qualify_preflight(path,preparation_path,pin):
    record=json.loads(Path(path).read_text())
    require(record.get('argv')==['python3','scripts/prepare_photon_air_fog_inprocess.py','--verify',str(Path(preparation_path).resolve())]
        and type(record.get('returncode'))is int and record['returncode']==0,'Exact standalone same-JVM preflight differs/failed')
    require(record.get('preparation_sha256')==pin,'Preflight preparation hash differs')
    result=json.loads(record['stdout'])
    require(result.get('status')=='verified-one-current-R12-unused-guarded-air-fog-toggle-profile'
        and result.get('launch_authorized')is False and result.get('owned_live_processes')==[],'Exact unused supplied runtime preflight result differs')
    integer(record.get('verified_ns'),'preflight write timestamp')
    origin=record.get('verified_time_provenance',{})
    require(origin.get('kind')=='filesystem_write_timestamp_of_prior_successful_standalone_cli_receipt','Preflight timestamp provenance differs')
    raw=Path(origin['path'])
    require(digest(raw)==origin.get('sha256')and raw.stat().st_mtime_ns==record['verified_ns'],'Raw preflight hash/timestamp differs')
    require(json.loads(raw.read_text())=={k:v for k,v in record.items()if k not in ('verified_ns','verified_time_provenance')},'Qualified/raw preflight fields differ')
    return record


def qualify_frozen_preparation(state,path,pin):
    require_sha(pin);require(digest(path)==pin and state.get('status')==prep.FINAL,'Exact final fresh supplied runtime preparation differs')
    for field in ('maintained_source_files_sha256','runtime_sources_sha256'):
        for name,value in state[field].items():require(digest(name)==value,'Frozen source drift: '+name)
    require(prep.baseline.control_prep.current_runtime_sources()==state['runtime_sources_sha256']
        and prep.matrix.development_fingerprints()==state['development_runtime_inputs'],'Current supplied runtime source/class roots differ')
    external=state['independent_artifact_receipt']
    require(external['sha256']==RECEIPT_SHA256 and prep.qualify_runtime_receipt(external['path'],external['sha256'])==
        (external,state['auxiliary_directory_identities'],state['production_jar']),'Exact supplied runtime direct receipt differs')
    require(prep.qualify_archive()==state['source_qualification']and prep.qualify_driver_provenance()==state['driver_provenance'],'Private source/driver review drift')
    for name,value in state['canonical_source_qualification']['source_files_sha256'].items():require(digest(name)==value,'Canonical source drift: '+name)
    require(prep.world_fingerprint(prep.canonical.SOURCE/'saves'/prep.WORLD)==state['source_world_sha256']==WORLD,'Canonical stopped source world differs')
    item=state['complete_option_inventory'];require(digest(item['path'])==item['sha256'],'Actual Iris inventory drift')
    inventory=json.loads(Path(item['path']).read_text());driver.validate_maps(state['canonical_source_qualification']['effective_option_maps']['native'],inventory['options'])
    require(inventory['option_count']==367 and inventory['options']==state['expected_effective_options']
        and inventory['sha256']==state['expected_effective_options_sha256']==OPTIONS_HASH,'Full fresh367 options differ')
    for name,value in inventory['parser_dependency_sha256'].items():require(digest(name)==value,'Pinned parser dependency differs')
    require(digest(inventory['log'])==inventory['log_sha256'],'Actual Iris parse log drift')
    profile=Path(state['operation']['profile'])
    require(profile==prep.OUTPUT/'coastalwater/toggle'and state['operation']['command']==prep.command(profile),'Exact owned single-profile argv differs')
    for name,value in state['prepared_profile']['parent_files_sha256'].items():require(digest(name)==value,'Frozen parent recipe differs')
    prep.validate_manifest(json.loads((profile.parent/'workload-manifest.json').read_text()),state['canonical_source_qualification']['effective_option_maps']['native'])
    require(json.loads((profile/'air-fog-inprocess-request.json').read_text())==state['request'],'Provenance-only request bytes differ')
    return external['receipt']


def launch_controls(state,launch,terminal):
    profile=Path(state['operation']['profile']);row=dict(profile=str(profile),command=state['operation']['command'])
    require(launch.get('owner')=='root'and launch.get('profile')==str(profile)and launch.get('command')==row['command'],'Exact root/profile/argv differs')
    owned=launch.get('java_process_ownership',{});require(len(owned)==1,'One exact Java birth required')
    java=next(iter(owned.values()));pid=integer(java.get('pid'),'Java PID')
    require(java.get('status')=='owned'and java.get('association')=='cwd'and java.get('profile')==str(profile),'Exact owned cwd association differs')
    require(any(e.get('command')==['/usr/sbin/lsof','-a','-p',str(pid),'-d','cwd','-Fn']and e.get('returncode')==0
        and e.get('selected_pids')==[str(pid)]and str(profile)in e.get('paths',[])for e in java.get('evidence',[])),'Exact selected Java cwd evidence missing')
    snapshot=launch.get('java_vm_args');props=standalone.rain.actual.vm_arguments(snapshot)
    require('@'+snapshot['path']in java.get('identity','')and 'net.neoforged.devlaunch.Main'in java['identity'],'Birth does not consume captured VM file')
    required=dict(benchmark='true',benchmarkOnly='true',benchmarkFeature='air_fog_ablation',benchmarkStartEnabled='false',initialWarmup='30',benchmarkSettleSeconds='8',measurementSeconds='20',gpuTiming='true',cpuTiming='true',gpuSampleInterval='5',fixedWetness='1',performanceStack='minimal',includeDh='false',benchmarkWindowScale='2',comparisonFullscreen='false',measureWorkload='false',measureOnly='false',cameraQuality='')
    required.update({name:'false'for name in PROBES if name!='gpuBenchmark'})
    source_absent_defaults={'measureWorkload','measureOnly','smokeTest','smoke'}
    for name,target in required.items():require(props.get('shaderlod.dev.'+name,'false'if name in source_absent_defaults else None)==target,'Actual same-JVM Java property differs/missing: '+name)
    require(props.get('shaderlod.dev.gpuBenchmark','false')=='false','Gradle enable alias leaked as observer Java property')
    standalone.producer.terminal_controls(row,launch,terminal)
    return props


def analyze(preparation_path,pin,preflight_path,logs):
    state=json.loads(Path(preparation_path).read_text());receipt=qualify_frozen_preparation(state,preparation_path,pin)
    preflight=qualify_preflight(preflight_path,preparation_path,pin)
    profile=Path(state['operation']['profile']);logs=Path(logs)
    launch=json.loads((logs/'initial-process.json').read_text());terminal=json.loads((logs/'terminal-process.json').read_text())
    require(launch.get('preparation')==str(Path(preparation_path).resolve())and launch.get('preparation_sha256')==pin
        and launch['started_ns']>preflight['verified_ns'],'Birth prep/preflight chronology differs')
    props=launch_controls(state,launch,terminal)
    report_path=profile/'benchmark.json';require(report_path.stat().st_mtime_ns>=launch['started_ns'],'Benchmark report predates birth')
    report=json.loads(report_path.read_text());result=analyze_report(report,receipt,profile,state)
    context=standalone.producer.hardware_context((logs/'console.log').read_text())
    require(report['backend']==context['typed']['gpu']['backend'],'Actual hardware/benchmark backend differs')
    for name,value in report['instrumentation_properties'].items():require(props.get('shaderlod.dev.'+name,'false'if name in PROBES else None)==value,'Header/actual VM property mismatch: '+name)
    raw_main=(profile/'config/shaderlod-client.toml').read_text();require(tomllib.loads(raw_main)['lod']['enabled']is False,'Actual disk main not restored OFF')
    # Every captured normalized full config was compared within the four phases;
    # verify post-shutdown semantics through an actual boundary receipt, without
    # replacing an unknown startup byte rewrite with claimed byte identity.
    last=report['phases'][-1]['inputEnd']['configuration_files'];row=dict(profile=str(profile),expected=dict(archive='air-fog-cost-ablation-enabled.zip'),
        prepared_profile=state['prepared_profile'],prepared_writable_inventory=state['prepared_profile']['prepared_writable_inventory'])
    # Main initial OFF versus in-memory ON may persist during sampling; only
    # exact bool token normalization is allowed, never unknown setting changes.
    initial_main=state['prepared_profile']['prepared_writable_inventory']['settings'][MAIN]['value']
    parsed=tomllib.loads(raw_main);require(parsed==initial_main,'Complete post-shutdown main semantics differ from prepared OFF')
    require(prep.driver.current.camera.forest.validate_effects((profile/EFFECTS).read_text())==state['prepared_effects'],'Post-shutdown seven effects differ')
    result.update(preparation_sha256=pin,preflight_sha256=digest(preflight_path),hardware_context=context,
        report_raw_sha256=digest(report_path),source_report_path=str(report_path),runtime_receipt_sha256=RECEIPT_SHA256,
        actual_VM_snapshot_sha256=launch['java_vm_args']['sha256'],restored_disk_main_OFF=True,
        raw_receipts_sha256={str(p):digest(p)for p in (logs/'initial-process.json',logs/'terminal-process.json',logs/'console.log',Path(preflight_path))})
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--preparation',type=Path,default=prep.OUTPUT/prep.MARKER)
    parser.add_argument('--preparation-sha256',required=True);parser.add_argument('--preflight',type=Path,required=True)
    parser.add_argument('--logs',type=Path,required=True);parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    try:result=analyze(args.preparation,args.preparation_sha256,args.preflight,args.logs);code=0
    except(OSError,ValueError,KeyError,TypeError)as failure:
        result=dict(status='FAILED',error=str(failure),performanceEvidence=False,limits=LIMITS,analyzer_source_files_sha256=source_pins());code=1
    payload=json.dumps(result,indent=2,sort_keys=True,allow_nan=False)+'\n'
    if args.output:
        require(not args.output.exists(),'Preserve earlier derived output')
        require(args.output.resolve().is_relative_to(ROOT/'build')or args.output.resolve().is_relative_to(ROOT/'dev/logs'),'Derived output must stay ignored')
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(payload)
    else:print(payload,end='')
    raise SystemExit(code)


if __name__=='__main__':main()
