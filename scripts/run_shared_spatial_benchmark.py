#!/usr/bin/env python3
"""Prepare/qualify spatial timing controls and ingest operator evidence; never launch."""
from __future__ import annotations
import argparse
import copy
import json
import math
import re
import struct
from pathlib import Path
import statistics
import tomllib

import prepare_shared_spatial_runtime as prep
import run_shared_lod_benchmark as common
from analyze_stack_matrix import artifact_identities, qualified_identities, window_reference

ROOT = prep.ROOT
ARMS = tuple(prep.ARMS)
# Four-treatment Williams design: each treatment/position and directed within-block
# predecessor pair occurs once in each cycle. JVMs/worlds are independent.
WILLIAMS = ((0, 1, 3, 2), (1, 2, 0, 3), (2, 3, 1, 0), (3, 0, 2, 1))
ORDERS = tuple(tuple(ARMS[i] for i in order) for order in WILLIAMS) * 2
WARMUP, CAPTURE, INTERVAL = 30, 30, 5
JAR_SHA256 = '0d499564c24d652c782a5c0e6b26fbae4a93b323542c5a4c58bbeee58da8ae9f'
OFF = ('gpuPassTiming', 'cpuLayerTiming', 'cloudSamplerProbe', 'workTrace',
       'dhDrawEvidence', 'waterSceneSurvey', 'shadowObserve', 'visibilityLists', 'shadowCoherenceOracle')
DIRECTORIES = ('build/classes/java/devClient', 'build/resources/devClient',
               'build/classes/java/main', 'build/resources/main')
DRIVER_CHECKS = ('original_off_parity', 'full_driver_parity', 'consuming_stage_capability',
                 'physical_layout_cache_completion', 'sampler_state', 'fallback_guards')
QUALITY_CHECKS = ('near_far_transition_stills', 'motion_popping_flicker', 'foliage_thin_shadows',
                  'water_reflections', 'salient_lighting', 'full_parity')
HARDWARE_FIELDS = ('gpu', 'driver', 'opengl', 'os', 'java', 'framebuffer', 'window')
# Frozen before any timing: not significance claims or target substitutions.
LIMITS = dict(max_pilot_off_overhead=1.10, max_pilot_full_overhead=1.25, max_pilot_adaptive_regression=1.10,
              max_run_relative_mad=.05, min_each_block_throughput=1.5,
              min_median_throughput=1.5, max_tail_ratio=1.05)
require = common.require
write = prep.matrix.write_json


def command(profile):
    result = ['./gradlew', 'runClientLodOn', '-PrunDirectory=' + str(profile),
              '-PperformanceStack=minimal', '-PincludeDh=false', '-PmeasureWorkload=true',
              '-PmeasureOnly=true', '-PinitialWarmup=' + str(WARMUP),
              '-PmeasurementSeconds=' + str(CAPTURE), '-PgpuTiming=true', '-PcpuTiming=true',
              '-PgpuSampleInterval=' + str(INTERVAL), '-PbenchmarkWindowScale=2',
              '-PcomparisonFullscreen=false', '-PfixedWetness=0']
    result += ['-P' + flag + '=false' for flag in OFF]
    for task in ('compileJava', 'compileDevClientJava', 'processResources', 'processDevClientResources'):
        result += ['-x', task]
    return result


def plan(profiles, stage='repeated'):
    require(stage in ('pilot', 'repeated'), 'Unknown stage')
    profiles = Path(profiles).resolve()
    orders = (ARMS,) if stage == 'pilot' else ORDERS
    rows = []
    for block, order in enumerate(orders, 1):
        for position, arm in enumerate(order, 1):
            profile = profiles / ('b' + str(block)) / 'forest' / arm
            rows.append(dict(block=block, position=position, arm=arm, profile=str(profile),
                             command=command(profile), status='planned'))
    return dict(schema=1, stage=stage, launch_status='HOLD: sole runtime owner executes separately',
                acceptance='Diagnostic pilot only' if stage == 'pilot' else 'Repeated target requires all independent gates',
                orders=[list(o) for o in orders], runs=rows, thresholds=LIMITS,
                minimum_warmup_capture_seconds=len(rows) * (WARMUP + CAPTURE),
                balance='Four-treatment Williams cycle twice; within-cycle positions/predecessors balanced; cross-block thermal history remains a qualification')


def tree_inventory(directory):
    require(directory.is_dir() and not directory.is_symlink(), 'Missing/symlink frozen directory: ' + str(directory))
    require(not any(p.is_symlink() for p in directory.rglob('*')), 'Symlink member in frozen inventory')
    files = {p.relative_to(directory).as_posix(): prep.file_hash(p)
             for p in sorted(directory.rglob('*')) if p.is_file()}
    require(files, 'Empty frozen class/resource inventory: ' + str(directory))
    return dict(files=files, sha256=prep.matrix.canonical_options_hash(files))


def development_freeze(root=ROOT):
    root = Path(root)
    inventories = {name: tree_inventory(root / name) for name in DIRECTORIES}
    require(prep.file_hash(root / 'build/libs/shaderlod-0.1.0-dev.jar') == JAR_SHA256,
            'Reviewed production JAR differs')
    files = ('build/libs/shaderlod-0.1.0-dev.jar', 'build.gradle', 'settings.gradle', 'gradlew',
             'gradle/wrapper/gradle-wrapper.properties', 'gradle/wrapper/gradle-wrapper.jar',
             'dev/runtime.lock.json', 'dev/performance-mods.lock.json', 'dev/runtime-artifact-equivalences.json',
             'scripts/run_shared_spatial_benchmark.py', 'scripts/prepare_shared_spatial_runtime.py',
             'scripts/run_proof_matrix.py', 'scripts/run_shared_lod_benchmark.py', 'scripts/analyze_stack_matrix.py')
    pins = {name: prep.file_hash(root / name) for name in files}
    runtime = json.loads((root / 'dev/runtime.lock.json').read_text())
    for name, artifact in runtime['artifacts'].items():
        if name == 'photon': continue  # Already qualified against actual upstream bytes.
        path = root / 'dev/runtime' / artifact['filename']
        require(prep.file_hash(path, 'sha512') == artifact['hashes']['sha512'], 'Pinned dependency differs: ' + name)
        pins[str(path.relative_to(root))] = prep.file_hash(path)
    return dict(directories=inventories, files=pins)


def scope(qualified, scene, frozen):
    return dict(archive_sha256=prep.ARCHIVES, source_freeze_sha256=prep.FREEZE_SHA256,
                manifest_sha256=prep.MANIFEST_SHA256, world_sha256=prep.WORLD_SHA256,
                upstream_effective_options_sha256=prep.OPTIONS_SHA256,
                control_sha256=prep.matrix.canonical_options_hash(scene['control']),
                development_sha256=prep.matrix.canonical_options_hash(frozen),
                runtime_lock_sha256=qualified['runtime_lock_sha256'],
                global_config=tomllib.loads(prep.copies.fresh_config('adaptive')),
                full_effects=tomllib.loads(prep.effects_config('FULL')),
                adaptive_effects=tomllib.loads(prep.effects_config('ADAPTIVE')))


def qualification(root=ROOT):
    qualified = prep.qualify(root)
    scene = prep.inspect_scene(root=root, qualified=qualified)
    frozen = development_freeze(root)
    equivalences = common.verified_equivalences(root)
    return dict(status='source-qualified', performance_accepted=False, launch_status='HOLD',
                scope=scope(qualified, scene, frozen), development_freeze=frozen,
                artifact_equivalence=equivalences, source_files_sha256=scene['source_files_sha256'],
                upstream_effective_options=scene['upstream_effective_options'], control=scene['control'],
                source_loaded_mods=scene['source_loaded_mods'],
                source_loaded_artifacts=json.loads((prep.SOURCE/'workload-measurement.json').read_text())['loaded_mod_artifacts'],
                boundaries='No shader driver, cache, sampler, motion-quality or throughput proof; classes and resources are byte-pinned, not transformed-class readbacks')


def evidence_file(reference):
    path = Path(reference['path'])
    require(path.is_absolute() and path.is_file() and not path.is_symlink(), 'Missing/unsafe evidence file')
    require(prep.file_hash(path) == reference['sha256'], 'Evidence bytes changed: ' + str(path))
    return path


def prerequisites(path, frozen_scope, stage):
    """Operator/root supplies acceptance; source checks never manufacture it."""
    ledger = json.loads(Path(path).read_text())
    require(ledger.get('schema') == 1 and ledger.get('scope') == frozen_scope, 'Prerequisite scope drift')
    require(ledger.get('accepted_by') and ledger.get('resource_owner'), 'Acceptance/resource owner missing')
    hardware = ledger.get('hardware', {})
    require(set(hardware) == set(HARDWARE_FIELDS) and all(hardware.values()), 'Incomplete actual hardware evidence')
    require(hardware['framebuffer'] == [2976, 1672] and hardware['window'] == [1488, 836], 'Hardware extent mismatch')
    for name, checks in (('driver', DRIVER_CHECKS), ('quality', QUALITY_CHECKS)):
        gate = ledger.get(name, {})
        require(gate.get('accepted') is True and gate.get('checks') == {c: True for c in checks}, name + ' acceptance unavailable')
        require(gate.get('evidence'), name + ' evidence missing')
        for reference in gate['evidence']: evidence_file(reference)
    for reference in ledger.get('hardware_evidence', []): evidence_file(reference)
    require(ledger.get('hardware_evidence'), 'Actual hardware provenance absent')
    quiet = ledger.get('quiet_protocol', {})
    require(quiet == dict(no_builds_tests_encoders=True, no_source_readbacks=True,
                          context_only_between_windows=True, sole_client_gpu_owner=True), 'Quiet protocol not agreed')
    if stage == 'repeated':
        pilot = ledger.get('pilot', {})
        require(pilot.get('accepted') is True, 'Pilot stop gate not cleared')
        path = evidence_file(pilot['analysis'])
        result = json.loads(path.read_text())
        require(result.get('stage') == 'pilot' and result.get('status') == 'complete' and
                result.get('pilot_stop') is False and result.get('scope') == frozen_scope,
                'Pilot analysis does not clear exact-scope stop gate')
    return ledger


def fresh_inputs(profile, expected):
    """Before every first/only launch require exact copied bytes and fresh save."""
    profile = Path(profile)
    require(not (profile / 'workload-measurement.json').exists(), 'Stale measurement in fresh profile')
    world = profile / 'saves' / prep.WORLD
    require(prep.world_fingerprint(world) == prep.WORLD_SHA256, 'Fresh canonical world differs')
    require(not any(p.is_symlink() for p in profile.rglob('*')), 'Profile symlink unsupported')
    actual = {str(p.relative_to(profile)): prep.file_hash(p) for p in profile.rglob('*')
              if p.is_file() and not p.is_relative_to(world)}
    require(actual == expected['input_files_sha256'], 'Complete copied input inventory differs')
    require(not list((profile / 'mods').glob('*')), 'Unmanaged profile mod inventory')
    return prep.matrix.profile_inputs(profile)


def prepare(profiles, stage, ledger_path):
    profiles = common.ignored_path(profiles, ROOT / 'run-workloads')
    prep.matrix.require_exclusive_runtime()
    qualified = qualification()
    ledger = prerequisites(ledger_path, qualified['scope'], stage)
    operation = plan(profiles, stage)
    profiles.mkdir(parents=True)
    marker = profiles / 'shared-spatial-benchmark-preparation.json'
    state = operation | dict(status='preparing', qualification=qualified, prerequisites=ledger,
                             prerequisite_file=dict(path=str(Path(ledger_path).resolve()), sha256=prep.file_hash(Path(ledger_path))), blocks={})
    write(marker, state)
    try:
        for block in range(1, len(operation['orders']) + 1):
            copied = prep.prepare(profiles / ('b' + str(block)))
            for row in [r for r in state['runs'] if r['block'] == block]:
                arm = row['arm']; expected = copy.deepcopy(copied['arms'][arm]); profile = Path(row['profile'])
                request = expected['request'] | dict(case='spatial-timing-' + arm, block=block,
                    measurement_seconds=float(CAPTURE), gpu_queries=True, diagnostic_only=stage == 'pilot',
                    performanceEvidence=stage == 'repeated')
                write(profile / 'workload-request.json', request)
                expected.update(request=request, command=row['command'])
                expected['input_files_sha256']['workload-request.json'] = prep.file_hash(profile / 'workload-request.json')
                row.update(expected=expected, inputs=fresh_inputs(profile, expected))
            # Preserve the untimed preparer's source ledger; timing intent is recorded
            # separately, never silently recast its diagnostics as speed evidence.
            state['blocks'][str(block)] = dict(preparation=str(profiles / ('b' + str(block)) / 'shared-spatial-preparation.json'),
                                              preparation_sha256=prep.file_hash(profiles / ('b' + str(block)) / 'shared-spatial-preparation.json'))
            write(marker, state)
        require(development_freeze() == qualified['development_freeze'], 'Runtime drift during copying')
        state['status'] = 'complete'; write(marker, state)
    except BaseException as error:
        state.update(status='failed', error=str(error)); write(marker, state); raise
    return state


def validate_timing(report, expected, inputs):
    request = expected['request']
    require(report.get('request') == request and report.get('status') == 'complete', 'Incomplete/stale timing request')
    require(report.get('diagnostic_only') is not True, 'Readback-contaminated timing report')
    require(report.get('performanceEvidence') is not False, 'Non-performance timing report')
    prep.matrix.validate_report(report, request, inputs, 0)
    require(report['effective_shader_options'] == expected['expected_effective_options'] and
            report['effective_shader_options_sha256'] == expected['expected_effective_options_sha256'], 'Exact loaded complete option map differs')
    require(report['actual_shader_sha256'] == expected['archive_sha256'], 'Exact spatial archive differs')
    require(report['warmup_seconds'] == WARMUP and CAPTURE*.95 <= report['elapsed_seconds'] <= CAPTURE*1.10, 'Warmup/capture duration differs')
    state = report['input_state_end']
    require(state['lod_settings']['enabled'] is True and not state['terrain_configured'] and
            not state['terrain_confirmation'], 'Global/terrain policy differs')
    require([state['framebuffer_width'], state['framebuffer_height']] == [2976, 1672] and
            state['requested_chunks'] == state['effective_chunks'] == 24, 'Native extent/radius differs')
    require(state['dimension'] == 'minecraft:overworld' and state['day_time'] == 7000 and
            state['rain'] == state['thunder'] == 0, 'Forest scene/weather/time differs')
    for key in ('shadow_culling_active', 'separate_shadow_lists_active', 'shadow_frustum_coherence_active'):
        require(report.get(key) is False, 'Other render optimization active: ' + key)
    window = window_reference(report)
    observations = report['window_state']['capture']['observations']
    for field,value in (('focused',False),('visible',True),('iconified',False)):
        require(report['window_state']['capture'][field] == dict(true_observations=observations if value else 0,
                false_observations=0 if value else observations, unknown_observations=0), 'Contradictory window observations: ' + field)
    require(window['focused'] == 'false' and window['pacing'] == dict(pause_on_lost_focus=False,
            fullscreen=False, configured_framerate_limit=260, vsync=False), 'Focus/pacing differs')
    require([window['window_width'], window['window_height']] == [1488, 836], 'Native logical window differs')
    props = report['runtime_properties']
    for key in OFF:
        if key in ('gpuPassTiming', 'workTrace'): continue  # Not exported by schema 2; scopes/frozen command below.
        require(props.get('shaderlod.dev.' + key) == 'false', 'Observer/layer not explicitly off: ' + key)
    require(props.get('shaderlod.dev.gpuTiming') == 'true' and props.get('shaderlod.dev.gpuSampleInterval') == str(INTERVAL), 'GPU query cadence differs')
    require(report['backend'] == 'ELAPSED_ALTERNATING' and report['sampling_policy'] ==
            'frame only, every sampled frame; sample interval=5 render frames, poll only sampled frames', 'GPU frame-only backend/policy differs')
    gpu = report['gpu_samples']; cpu = report['cpu_samples']
    require(len(gpu) >= 60 and all(s['scope'] == 'game-render' and s['epoch'] == 2 and
            math.isfinite(s['milliseconds']) and s['milliseconds'] > 0 for s in gpu), 'Invalid GPU stream/scope/epoch')
    frames = [s['frameId'] for s in gpu]
    require(len(set(frames)) == len(frames) and all((f-1) % INTERVAL == 0 for f in frames) and
            all(b > a and (b-a) % INTERVAL == 0 for a,b in zip(frames, frames[1:])), 'Invalid GPU frame cadence')
    require(cpu and all(s['scope'] in ('run-tick', 'game-render', 'render-and-blit', 'swap-and-events') and
            s['epoch'] == 2 and math.isfinite(s['wallMilliseconds']) and s['wallMilliseconds'] > 0 for s in cpu), 'Invalid CPU stream/layer/epoch')
    ticks = [s for s in cpu if s['scope'] == 'run-tick']; ids = [s['frameId'] for s in ticks]
    require(len(ticks) >= 100 and len(set(ids)) == len(ids) and all(b > a for a,b in zip(ids, ids[1:])), 'Invalid total-frame stream')
    require(len(ticks) >= report['render_frames']*.95 and len(gpu) >= (report['render_frames']/INTERVAL - 1)*.90, 'Timing stream coverage too sparse')
    require(report['runtime_mod_jars_sha256'] == {}, 'Unmanaged runtime jars')
    temporal = prep.matrix.validate_temporal_inputs(report, request)
    return dict(window=window, temporal=temporal, property_limits='cpuTiming/gpuPassTiming/workTrace are not exported in WorkloadSession schema 2; frozen command plus actual scopes establish effective minimal streams')


def compare_receipt(receipt, row, state):
    """Operator lifecycle receipt is explicit provenance, not automatic enforcement."""
    q = state['qualification']; ledger = state['prerequisites']
    require(receipt.get('command') == row['command'] and receipt.get('request') == row['expected']['request'], 'Actual launch command/request differs')
    require(receipt.get('resource_owner') == ledger['resource_owner'], 'Runtime owner differs')
    for key in ('prelaunch_development', 'postrun_development'):
        require(receipt.get(key) == q['development_freeze'], 'Complete class/resource freeze differs')
    require(receipt.get('prelaunch_input_files') == row['expected']['input_files_sha256'] and
            receipt.get('prelaunch_world_sha256') == prep.WORLD_SHA256, 'Prelaunch complete input/world drift')
    require(receipt.get('hardware') == ledger['hardware'], 'Actual hardware differs')
    require(receipt.get('quiet_window') is True and receipt.get('owned_processes_stopped') is True,
            'Contaminated window or live process ownership')
    require(isinstance(receipt.get('launcher_pid'), int) and receipt['launcher_pid'] > 0 and
            isinstance(receipt.get('java_pid'), int) and receipt['java_pid'] > 0, 'Exact launcher/Java handles absent')
    require(receipt.get('focus_action_before_capture') is True and receipt.get('watcher_exited_before_capture') is True,
            'Warmup-only focus guard unproved')
    require(receipt.get('quality_regression_observed') is False, 'Observed quality regression stops batch')
    for key in ('report', 'console', 'hardware_evidence', 'screenshot', 'prelaunch_context', 'postrun_context'):
        evidence_file(receipt[key])
    require(json.loads(evidence_file(receipt['hardware_evidence']).read_text()) == ledger['hardware'], 'Actual OS/Java/GL evidence differs')
    console = evidence_file(receipt['console']).read_text(errors='replace')
    require(not prep.matrix.FATAL_LOG.search(console), 'Fatal runtime markers in preserved console')
    require('ShaderLOD CPU timers:' in console and 'layers=false' in console, 'Effective CPU layer mode unproved')
    for field,label in (('gpu','Renderer'),('opengl','Version')):
        matches = re.findall(r'OpenGL '+label+r': ([^\r\n]+)', console)
        require(matches and set(matches) == {ledger['hardware'][field]}, 'Actual console GL '+field+' differs')
    screenshot = evidence_file(receipt['screenshot'])
    with screenshot.open('rb') as image: header = image.read(24)
    require(header[:8] == b'\x89PNG\r\n\x1a\n' and header[12:16] == b'IHDR' and
            struct.unpack('>II', header[16:24]) == (2976,1672), 'Native screenshot dimensions invalid')
    require(screenshot.stat().st_mtime_ns >= receipt['started_ns'], 'Screenshot predates launch')
    report_path = evidence_file(receipt['report'])
    require(report_path.stat().st_mtime_ns >= receipt['started_ns'], 'Report predates launch')
    report = json.loads(report_path.read_text())
    props = receipt.get('instrumentation_arguments')
    require(props == {key: 'false' for key in OFF} | dict(gpuTiming='true', cpuTiming='true', gpuSampleInterval=str(INTERVAL)), 'Instrumentation provenance differs')
    require(receipt.get('postrun_global_config') == state['qualification']['scope']['global_config'], 'Runtime global config changed')
    mode = prep.ARMS[row['arm']][1]
    require(receipt.get('postrun_effects_config') == state['qualification']['scope']['adaptive_effects' if mode == 'ADAPTIVE' else 'full_effects'], 'Runtime effects config changed')
    return report


def matched_identity(report, equivalences):
    raw = artifact_identities(report)
    qualified, records = qualified_identities(report, raw, equivalences)
    # Main mod is present in EVERY arm: never strip its identity.
    return dict(mods=report['loaded_mods'], artifacts=qualified), records


def analyze(rows, stage, frozen_scope=None, quality_accepted=False):
    expected = plan(ROOT / 'run-workloads/analysis', stage)['runs']
    identities = [(r['block'],r['position'],r['arm']) for r in rows]
    require(len(set(identities)) == len(identities), 'Duplicate block/position/arm')
    allowed = {(r['block'],r['position'],r['arm']) for r in expected}
    require(set(identities) <= allowed, 'Unexpected schedule row')
    complete = [r for r in rows if r.get('status') == 'complete']
    result = dict(schema=1, stage=stage, scope=frozen_scope, status='complete' if len(complete) == len(expected) else 'incomplete',
                  completed_runs=len(complete), thresholds=LIMITS, comparisons={}, by_arm={})
    for arm in ARMS:
        selected = [r for r in complete if r['arm'] == arm]
        if selected:
            result['by_arm'][arm] = {stream+'_'+field: common.spread([r['metrics'][stream][field] for r in selected])
                for stream in ('gpu_elapsed','total_frame_wall') for field in ('median_ms','p95_ms')}
            result['by_arm'][arm]['elapsed_per_rendered_frame_ms'] = common.spread([r['metrics']['elapsed_per_rendered_frame_ms'] for r in selected])
    for baseline, candidate in (('original','privateOFF'), ('original','privateON-FULL'),
            ('privateOFF','privateON-FULL'), ('original','privateON-ADAPTIVE'),
            ('privateOFF','privateON-ADAPTIVE'), ('privateON-FULL','privateON-ADAPTIVE')):
        pairs = []
        for block in sorted({r['block'] for r in complete}):
            selected = {r['arm']: r for r in complete if r['block'] == block}
            if baseline in selected and candidate in selected: pairs.append((selected[baseline],selected[candidate]))
        if not pairs: continue
        comparison = {}
        for stream in ('gpu_elapsed', 'total_frame_wall'):
            for field in ('median_ms', 'p95_ms'):
                ratios = [b['metrics'][stream][field]/a['metrics'][stream][field] for a,b in pairs]
                comparison[stream+'_'+field] = dict(per_block_candidate_over_baseline=ratios,
                    per_block_percent_time_reduction=[100*(1-v) for v in ratios], spread=common.spread(ratios))
        ratios = [a['metrics']['elapsed_per_rendered_frame_ms']/b['metrics']['elapsed_per_rendered_frame_ms'] for a,b in pairs]
        comparison['total_throughput_ratio'] = dict(per_block=ratios, spread=common.spread(ratios), denominator=baseline)
        result['comparisons'][baseline+'_to_'+candidate] = comparison
    private_off = result['comparisons'].get('original_to_privateOFF', {})
    full = result['comparisons'].get('original_to_privateON-FULL', {})
    adaptive = result['comparisons'].get('original_to_privateON-ADAPTIVE', {})
    result['pilot_stop'] = (result['status'] != 'complete' or (stage == 'pilot' and (
        any(private_off.get(stream+'_median_ms', {}).get('spread', {}).get('max', math.inf) > LIMITS['max_pilot_off_overhead'] for stream in ('gpu_elapsed','total_frame_wall')) or
        any(full.get(stream+'_median_ms', {}).get('spread', {}).get('max', math.inf) > LIMITS['max_pilot_full_overhead'] for stream in ('gpu_elapsed','total_frame_wall')) or
        any(adaptive.get(stream+'_median_ms', {}).get('spread', {}).get('max', math.inf) > LIMITS['max_pilot_adaptive_regression'] for stream in ('gpu_elapsed','total_frame_wall')) or
        any(adaptive.get(stream+'_p95_ms', {}).get('spread', {}).get('max', math.inf) > LIMITS['max_pilot_adaptive_regression'] for stream in ('gpu_elapsed','total_frame_wall')))))
    signal = adaptive.get('total_throughput_ratio', {}).get('per_block', [])
    stability = result['status'] == 'complete' and stage == 'repeated'
    for arm in ARMS:
        for field in ('gpu_elapsed_median_ms', 'total_frame_wall_median_ms', 'elapsed_per_rendered_frame_ms'):
            distribution = result['by_arm'].get(arm, {}).get(field, {})
            stability = stability and bool(distribution) and distribution['median_absolute_deviation']/distribution['median'] <= LIMITS['max_run_relative_mad']
    tail = all(adaptive.get(stream+'_p95_ms', {}).get('spread', {}).get('max', math.inf) <= LIMITS['max_tail_ratio'] for stream in ('gpu_elapsed','total_frame_wall'))
    gpu = adaptive.get('gpu_elapsed_median_ms', {}).get('spread', {}).get('max', math.inf) < 1
    off = result['comparisons'].get('privateOFF_to_privateON-ADAPTIVE', {}).get('total_throughput_ratio', {}).get('per_block', [])
    result['timing_target_met'] = bool(stability and len(signal) == 8 and min(signal) >= LIMITS['min_each_block_throughput'] and
        statistics.median(signal) >= LIMITS['min_median_throughput'] and tail and gpu and len(off) == 8 and min(off) > 1)
    result['quality_accepted'] = quality_accepted
    result['acceptance_candidate'] = bool(result['timing_target_met'] and quality_accepted)
    result['interpretation'] = ('Run-level matched paired metrics; frames are autocorrelated, not independent replicates. '
        '1.5x means 50% greater throughput and 33.333% less elapsed time per rendered frame versus original. '
        'Elapsed GPU is not busy time. Nominal retained rate 0.25 predicts no speedup. '
        'Thermal/clock and temporal phase are not synchronized. Parent accepts integration; no goal completion follows.')
    return result


def ingest(marker, receipts, output):
    state = json.loads(Path(marker).read_text())
    require(state.get('status') == 'complete', 'Prepared collection incomplete')
    reference = plan(Path(marker).parent, state['stage'])
    require(state['orders'] == reference['orders'] and state['thresholds'] == LIMITS, 'Frozen schedule/threshold drift')
    qualified = qualification()
    require(state['qualification'] == qualified, 'Source/runtime qualification drift')
    prereq_path = evidence_file(state['prerequisite_file'])
    require(prerequisites(prereq_path, qualified['scope'], state['stage']) == state['prerequisites'], 'Prerequisite evidence changed')
    for pin in state['blocks'].values():
        require(prep.file_hash(Path(pin['preparation'])) == pin['preparation_sha256'], 'Copied provenance drift')
    rows = []; matched = None
    for expected_row, row in zip(reference['runs'], state['runs'], strict=True):
        require(all(row[k] == expected_row[k] for k in ('block','position','arm','profile','command')), 'Prepared run schedule drift')
        token = f"b{row['block']}-p{row['position']}-{row['arm']}.json"
        receipt_path = Path(receipts) / token
        if not receipt_path.exists(): break
        receipt = json.loads(receipt_path.read_text()); report = compare_receipt(receipt, row, state)
        validation = validate_timing(report, row['expected'], row['inputs'])
        prep.matrix.validate_optional_stack(report, 'minimal', json.loads((ROOT/'dev/performance-mods.lock.json').read_text()))
        identity, records = matched_identity(report, qualified['artifact_equivalence'])
        expected_mods = {k:v for k,v in qualified['source_loaded_mods'].items() if k != 'distanthorizons'} | {'shaderlod':'0.1.0'}
        require(report['loaded_mods'] == expected_mods, 'Canonical loaded version inventory differs')
        source = dict(loaded_mods=qualified['source_loaded_mods'],loaded_mod_artifacts=qualified['source_loaded_artifacts'])
        source_identity, unused = matched_identity(source, qualified['artifact_equivalence'])
        expected_artifacts = {k:v for k,v in source_identity['artifacts'].items() if k not in ('distanthorizons','shaderlod_dev')}
        observed_artifacts = {k:v for k,v in identity['artifacts'].items() if k not in ('shaderlod','shaderlod_dev')}
        require(observed_artifacts == expected_artifacts, 'Canonical loaded artifact inventory differs')
        # Compare all actually observed state except intentional source/options/name.
        comparable = copy.deepcopy(report['input_state_end'])
        for key in ('shader_pack','actual_shader_sha256','effective_shader_options','effective_shader_options_sha256','config/iris.properties_sha256'):
            comparable.pop(key, None)
        signature = dict(state=comparable, identity=identity, window=validation['window'], hardware=receipt['hardware'])
        require(matched is None or matched == signature, 'Cross-run actual state/runtime/hardware mismatch')
        matched = signature
        rows.append({k:row[k] for k in ('block','position','arm')} | dict(status='complete',
            metrics=common.metrics(report), receipt=dict(path=str(receipt_path.resolve()),sha256=prep.file_hash(receipt_path)),
            validation=validation, raw_loaded_artifacts=report['loaded_mod_artifacts'], artifact_equivalence_records=records))
        if state['stage'] == 'pilot' and len(rows) == 4 and analyze(rows, 'pilot')['pilot_stop']: break
    result = analyze(rows, state['stage'], qualified['scope'], quality_accepted=True)
    result['prepared_manifest'] = dict(path=str(Path(marker).resolve()), sha256=prep.file_hash(Path(marker)))
    result['runs'] = rows
    output = common.ignored_path(output, ROOT/'dev/logs')
    output.parent.mkdir(parents=True, exist_ok=True); write(output, result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profiles-root', type=Path, required=True)
    parser.add_argument('--stage', choices=('pilot','repeated'), default='pilot')
    parser.add_argument('--prerequisites', type=Path)
    parser.add_argument('--receipts', type=Path); parser.add_argument('--output', type=Path)
    modes = parser.add_mutually_exclusive_group(required=True)
    for mode in ('dry-run','qualify-only','prepare','analyze'): modes.add_argument('--'+mode, action='store_true')
    args = parser.parse_args()
    if args.prepare and not args.prerequisites: parser.error('--prepare requires accepted --prerequisites')
    if args.analyze and (not args.receipts or not args.output): parser.error('--analyze requires --receipts and fresh ignored --output')
    try:
        if args.dry_run:
            result = plan(common.ignored_path(args.profiles_root,ROOT/'run-workloads'),args.stage)
        elif args.qualify_only:
            result = qualification()
            if args.prerequisites: result['prerequisites'] = prerequisites(args.prerequisites,result['scope'],args.stage)
        elif args.prepare: result = prepare(args.profiles_root,args.stage,args.prerequisites)
        else: result = ingest(args.profiles_root/'shared-spatial-benchmark-preparation.json',args.receipts,args.output)
        print(json.dumps(result, indent=2, sort_keys=True))
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(1, 'Cannot qualify spatial benchmark: '+str(error)+'\n')


if __name__ == '__main__': main()
