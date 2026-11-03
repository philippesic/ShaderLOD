#!/usr/bin/env python3
"""Independent qualification of the private, settled LIVE_PIXEL four-phase screen.

This wrapper deliberately does not route the archive through any older feature.
Shared timing/window/artifact primitives are read-only dependencies.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import struct
import tomllib

from analyze_inprocess_control import (canonical, digest, finite, integer, metric,
    require_sha, require_uuid, semantic_state, time_reduction,
    validate_fixed_window, validate_samples, validate_temporal, validate_volume_artifacts,
    FALSE_FLAGS, SPATIAL_EFFECTS)
from summarize_workload import stream
from qualify_volume_fast_artifacts import MODS

METHOD = 'One JVM/world/pipeline; pinned live pixel Photon; fixed TAAU graph; only live core global enabled changes; settled diagnostic incremental cost, OFF scale 1.0 is not original native'
ARCHIVE = '0da5e4b446fca7ce300217781f3431f3e76ff0a5c3a2b0caafb2bb84f7563abb'
MEMBERS_HASH = '96bb6e5a4c08c140a6882a7a47b5d3872cebf6730fd1030b3d9000339d165888'
OPTIONS_HASH = 'd5853a047a582b9ae2c5bb264438afa2510a76e5164fbf8b31976afc8788b8a0'
WORLD = '89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46'
SAMPLING = 'frame only, every sampled frame; sample interval=5 render frames, poll only sampled frames'
MAIN = 'config/shaderlod-client.toml'
BACKUP = 'config/shaderlod-client-1.toml.bak'
EFFECTS = 'config/shaderlod-effects-client.toml'
IRIS = 'config/iris.properties'
SIDECAR = 'shaderpacks/live-pixel-budget-enabled.zip.txt'
LIMITS = ('Settled incremental OFF 1.0 versus ON .75 in one fixed TAAU graph. '
    'OFF is not original/default native Photon; equivalent stock .75 and original native '
    'are separate denominators. No driver, linked consumption, raster coverage, normal '
    'transition, representative still/motion quality or repeated performance acceptance '
    'is inferred. Cloud toggle transients remain unaccepted. GPU elapsed intervals are not utilization.')
PROBES = ('shadowCoherenceOracle', 'visibilityLists', 'shadowObserve', 'workTrace',
    'workTraceOnly', 'spatialLayoutProbe', 'spatialLayoutOnly', 'cloudSamplerProbe',
    'dhDrawEvidence', 'waterSceneSurvey', 'smokeTest', 'gpuBenchmark', 'cameraQualityOnly',
    'gpuPassTiming', 'cpuLayerTiming', 'gpuAttribution', 'pixelCoverage', 'pixelCoverageOnly')


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def hashed(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


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
    return normalized, parsed


def live_semantic_state(state):
    require(integer(state.get('live_pixel_control_receipt_schema'), 'live pixel control receipt schema') == 2,
        'Explicit live pixel control receipt schema 2 required')
    result = semantic_state(state, False, 'volume')  # Generic shape only; no volume archive/adapter validation.
    normalized, _ = configuration(state.get('configuration_files'))
    require(normalized == state.get('live_pixel_configuration_semantics'), 'Independent configuration normalization differs')
    require(state.get(IRIS + '_sha256') == state['configuration_files']['files'][IRIS]['sha256'], 'Captured Iris raw hash alias differs')
    for field in ('live_pixel_source_qualification', 'live_pixel_effect_controls'):
        require(hashed(state.get(field)) == state.get(field + '_sha256'), 'Canonical live pixel derived hash differs: ' + field)
    result.pop('configuration_files', None)
    result.pop('config/iris.properties_sha256', None)  # Validated raw alias; only known generated timestamp differs.
    result['configuration_files'] = normalized
    return result


def stable(state):
    result = live_semantic_state(state)
    result['lod_settings'].pop('enabled', None)
    result['loaded_semantic_config']['lod'].pop('enabled', None)
    return result


def validate_effects(state, parsed):
    controls = state.get('live_pixel_effect_controls')
    require(isinstance(controls, dict) and set(controls) == set(SPATIAL_EFFECTS), 'Complete live pixel effects missing')
    require(controls == state['loaded_semantic_config'].get('effects'), 'Loaded effects differ from actual snapshot')
    require(state.get('live_pixel_effect_controls_sha256') == hashed(controls), 'Live effects canonical fingerprint differs')
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
    require(integer(state.get('live_pixel_control_receipt_schema'), 'live pixel control receipt schema') == 2,
        'Explicit live pixel control receipt schema 2 required')
    semantic_state(state, feature='volume')
    require(state.get('mod_present') is True and state.get('shaders_in_use') is True and state.get('fallback') is False,
        'Actual mod/Iris pipeline missing or fallback')
    require(state.get('hide_gui') is True and state.get('gui_scale') == 2, 'Fixed HUD policy differs')
    require(state.get('lod_settings', {}).get('enabled') is enabled and
        state['loaded_semantic_config']['lod'].get('enabled') is enabled, 'Wrong actual live global flag')
    require(state.get('actual_shader_sha256') == ARCHIVE, 'Actual source archive differs')
    options = state.get('effective_shader_options')
    require(isinstance(options, dict) and len(options) == 368 and all(isinstance(k, str) and isinstance(v, str) for k, v in options.items())
        and hashed(options) == OPTIONS_HASH and state.get('effective_shader_options_sha256') == OPTIONS_HASH,
        'Complete exact 368-key loaded option map differs')
    source = state.get('live_pixel_source_qualification', {})
    require(isinstance(source, dict) and set(source) == {'qualified', 'source_fingerprint', 'complete_member_count',
        'complete_member_sha256', 'option_count', 'complete_options_sha256'}, 'Unknown source qualification fields')
    fingerprint = source.get('source_fingerprint', {})
    require(isinstance(fingerprint, dict) and set(fingerprint) == {'sourceSha256', 'memberSha256'}, 'Unknown source fingerprint fields')
    members = fingerprint.get('memberSha256')
    require(source.get('qualified') is True and fingerprint.get('sourceSha256') == ARCHIVE
        and isinstance(members, dict) and len(members) == 533 and hashed(members) == MEMBERS_HASH
        and source.get('complete_member_count') == 533 and source.get('complete_member_sha256') == MEMBERS_HASH
        and source.get('option_count') == 368 and source.get('complete_options_sha256') == OPTIONS_HASH,
        'Full source fingerprint/member/options qualification differs')
    require(all(isinstance(k, str) and k and require_sha(v) for k, v in members.items()), 'Source member map malformed')
    require(state.get('live_pixel_source_qualification_sha256') == hashed(source), 'Source qualification canonical fingerprint differs')
    require('live_pixel_adapter_binding' not in state and 'live_pixel_availability' not in state,
        'Core-only prototype cannot claim adapter availability')
    normalized, parsed = configuration(state.get('configuration_files'))
    require(normalized == state.get('live_pixel_configuration_semantics'), 'Independent configuration normalization differs')
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
    require(state.get('live_pixel_dh_present') is False and state.get('live_pixel_camera_type') == 'FIRST_PERSON'
        and state.get('live_pixel_fov') == 70, 'Fixed noDH first-person/FOV differs')
    for name, value in (('framebuffer_width', 2976), ('framebuffer_height', 1672), ('requested_chunks', 24), ('effective_chunks', 24), ('day_time', 12500)):
        require(integer(state.get(name), name) == value, 'Native extent/chunks/time differs: ' + name)
    for name, value in (('x', 500.5), ('y', 68), ('z', 660.5), ('yaw', 87.38), ('pitch', 3.735), ('rain', 0), ('thunder', 0)):
        require(abs(finite(state.get(name), name) - value) <= .002, 'Fixed coastal pose/weather differs: ' + name)
    require(state.get('dimension') == 'minecraft:overworld', 'Wrong world dimension')
    require_sha(state.get('control_sha256'))
    window = state.get('live_pixel_window_state', {})
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
    if cpu:
        # CpuTimers.begin() records these ordinary coarse nested scopes even
        # with cpuLayerTiming=false. Frame identity is unique within a scope.
        scopes = {'run-tick', 'game-render', 'render-and-blit', 'swap-and-events'}
        require({s.get('scope') for s in cpu} == scopes
            and len({(s['scope'], s['frameId']) for s in cpu}) == len(cpu),
            'Unknown/missing/duplicate coarse CPU scope/frame stream')
        frames = {scope: {s['frameId'] for s in cpu if s['scope'] == scope} for scope in scopes}
        allowed_frames = (set(range(first + 1, last + 1)),
            set(range(first + 1, last)), set(range(first + 2, last + 1)))
        require(all(ids == frames['run-tick'] for ids in frames.values())
            and frames['run-tick'] in allowed_frames,
            'Incomplete/misaligned/noncontiguous coarse CPU frame coverage')
        # Nested clocks remain separate in stream(); never sum parent/children.
    for samples, key, epochs in ((gpu, 'gpuEpoch', gpu_epochs), (cpu, 'cpuEpoch', cpu_epochs)):
        if samples:
            require(phase[key] not in epochs, 'Timing epoch reused across phases')
            epochs.add(phase[key])
    snapshot = phase.get('timingSnapshot', {})
    require(snapshot.get('backend') == report['backend'] and snapshot.get('samplingPolicy') == SAMPLING
        and snapshot.get('sampleInterval') == 5 and snapshot.get('renderFrames') == last
        and set(snapshot.get('scopes', {})) == {'game-render'}, 'Timing snapshot backend/cadence/frame/scopes differs')
    pending = integer(snapshot.get('pendingQueries'), 'pending queries')
    scheduled = integer(snapshot.get('scheduledSampledFrames'), 'scheduled frames')
    require(pending <= 12 and abs(scheduled - phase['renderFrames'] / 5) <= 1, 'Query ring/scheduled-frame cap exceeded')
    stats = snapshot['scopes']['game-render']
    total = integer(stats.get('totalSamples'), 'total samples')
    require(stats.get('droppedQueries') == 0 and stats.get('zeroSamples') == 0
        and len(gpu) <= total <= len(gpu) + 2 and stats.get('samples') == min(240, total)
        and 0 <= scheduled - total <= pending + 2, 'Dropped/zero/history/sample counter cap exceeded')
    return stream(gpu, 'milliseconds'), stream(cpu, 'wallMilliseconds'), stream(cpu, 'threadMilliseconds')


def validate_manifest(manifest):
    require(isinstance(manifest, dict) and manifest.get('source_world_sha256') == WORLD,
        'Frozen source save differs')
    # Retain the older modeled manifest contract; actual live-pixel preparation
    # uses explicit complete source qualification instead of the volume field.
    if 'adaptive_pack_sha256' in manifest:
        require(manifest['adaptive_pack_sha256'] == ARCHIVE, 'Frozen legacy archive differs')
        if 'source_qualification' not in manifest:
            return 'legacy-explicit-archive'
    source = manifest.get('source_qualification', {})
    members = source.get('complete_member_sha256')
    require(source.get('archive_sha256') == ARCHIVE and isinstance(members, dict)
        and len(members) == 533 and hashed(members) == MEMBERS_HASH,
        'Frozen complete live-pixel archive/member manifest differs')
    require(manifest.get('schema') == 1 and manifest.get('scene') == 'coastalwater'
        and manifest.get('configuration_only') is False, 'Explicit live-pixel manifest scene/schema differs')
    model = manifest.get('experiment_arms', {}).get('toggle', {})
    options = model.get('expected_effective_options')
    require(model.get('archive_sha256') == ARCHIVE and isinstance(options, dict)
        and len(options) == 368 and hashed(options) == OPTIONS_HASH
        and model.get('expected_effective_options_sha256') == OPTIONS_HASH,
        'Frozen complete live-pixel archive/options model differs')
    recipe = manifest.get('benchmark_recipe', {})
    require(recipe.get('feature') == 'live_pixel' and recipe.get('phases') == [False, True, False, True],
        'Frozen live-pixel feature/order differs')
    return 'explicit-complete-live-pixel-source-schema1'


def analyze_report(report, artifact_receipt, profile=None):
    require(report.get('schema') == 3 and report.get('status') == 'complete' and report.get('benchmark_feature') == 'live_pixel',
        'Require complete explicit schema-3 LIVE_PIXEL report')
    require_uuid(report.get('session_id'))
    require(report.get('method') == METHOD and report.get('selected_flag') == 'lod.enabled'
        and report.get('starting_order') == 'OFF/ON/OFF/ON' and report.get('atmosphere_guard_absent') is True,
        'Explicit live pixel method/selected flag/order/atmosphere guard differs')
    require(report.get('core_protocol') == 2 and report.get('transport') == 'core_global_only'
        and report.get('raster_policy_proof') is False, 'Core-only transport declarations differ')
    require(isinstance(report.get('initial_enabled'), bool) and report.get('restored_initial_enabled') is True
        and report.get('restored_initial_feature') is True, 'Actual initial state/restoration missing')
    require(report.get('gpu_requested') is True and report.get('cpu_requested') is True
        and report.get('backend') in ('ELAPSED_ALTERNATING', 'TIMESTAMP') and report.get('sampling_policy') == SAMPLING,
        'Full-frame GPU timing/cadence required')
    for name, minimum in (('initial_warmup_seconds', 30), ('settle_seconds', 8), ('measurement_seconds', 20)):
        require(finite(report.get(name), name) >= minimum, 'Insufficient warmup/settle/capture declaration')
    props = report.get('instrumentation_properties', {})
    require(props.get('benchmarkFeature') == 'live_pixel' and props.get('gpuTiming') == 'true'
        and props.get('gpuSampleInterval') == '5' and props.get('cpuTiming') == str(report['cpu_requested']).lower(),
        'Explicit instrumentation feature/timers/cadence differs')
    require(all(props.get(n) in ('false', 'unspecified') for n in PROBES), 'Diagnostic/pass/layer observer enabled/missing declaration')
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
        require(phase.get('status') == 'complete' and phase.get('index') == index and phase.get('enabled') is (index % 2 == 0),
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
        validate_fixed_window(phase, 'live_pixel_window_state')
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
    return dict(schema=1, status='QUALIFIED_SETTLED_INCREMENTAL_DIAGNOSTIC_ONLY', benchmark_feature='live_pixel',
        live_pixel_control_receipt_schema=2,
        session_id=report['session_id'], selected_flag='lod.enabled', method=METHOD, shader_sha256=ARCHIVE, manifest_contract=manifest_contract,
        phases=summaries, adjacent_pairs=pairs, same_state_drift=drift,
        source_report_sha256=hashed(report), artifact_receipt_sha256=hashed(artifact_receipt),
        loaded_artifact_evidence=copy.deepcopy(report['loaded_artifact_evidence']),
        limits=LIMITS, transition_accepted=False, quality_accepted=False, repeated_performance_accepted=False,
        raster_policy_proof=False, transport='core_global_only', core_protocol=2,
        restoration={'initial_enabled': report['initial_enabled'], 'restored_initial_enabled': True,
            'restored_initial_feature': True, 'postshutdown_byte_identity_verified': False},
        uncertainty='Raw object world/pipeline identities and clocks are not exported; source runtime guard declarations alone do not establish driver/coverage or independent repeats.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--artifact-receipt', type=Path, required=True)
    parser.add_argument('--profile', type=Path)
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
        args.output.write_text(encoded)
    else:
        print(encoded, end='')
    raise SystemExit(code)


if __name__ == '__main__':
    main()
