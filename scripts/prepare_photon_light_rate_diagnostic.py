#!/usr/bin/env python3
"""Prepare a source-qualified noon light-rate raw diagnostic; never build or launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import shutil
import sys
import tomllib
import zipfile

import build_photon_volume_light_rate_diagnostic as diagnostic
import prepare_photon_taau_screen as canonical
import qualify_volume_fast_artifacts as artifacts
import run_proof_matrix as matrix
from prepare_parallel_lod_profiles import effects_config
from prepare_reduced_effects_profiles import fresh_config, require, write_json
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

OUTPUT = ROOT / 'run-workloads/light-rate-diagnostic-r1'
BUILD = ROOT / 'build/light-rate-runtime-r1'
EXPERIMENT = 'photon-light-rate-noon-executed-reuse-diagnostic-r1'
FRAMEBUFFER = [2976, 1672]
TRACE_EXTENT = [744, 418]
MARKER = 713001
SOURCE_WORLD_SHA256 = '89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46'
OFF = ('gpuTiming', 'cpuTiming', 'cpuLayerTiming', 'gpuPassTiming', 'gpuAttribution',
       'cloudSamplerProbe', 'spatialLayoutProbe', 'spatialLayoutOnly', 'dhDrawEvidence',
       'waterSceneSurvey', 'shadowObserve', 'visibilityLists', 'shadowCoherenceOracle',
       'smokeTest', 'gpuBenchmark', 'benchmarkOnly', 'cameraQualityOnly')
STOP = canonical.STOP


def plan(output=OUTPUT, build=BUILD, root=ROOT):
    root = Path(root).resolve(); raw_output, raw_build = Path(output), Path(build)
    require(not raw_output.is_symlink() and not raw_build.is_symlink(), 'Symlink output unsupported')
    output, build = raw_output.resolve(), raw_build.resolve()
    require(output == root / 'run-workloads/light-rate-diagnostic-r1', 'Only owned fresh profile family allowed')
    require(build.parent == root / 'build' and build.name.startswith('light-rate-runtime-'), 'Only owned build prefix allowed')
    require(not output.exists() and not build.exists(), 'Preserve existing output; use verify/finalize after preparation')
    return dict(schema=1, experiment=EXPERIMENT, output=str(output), build=str(build), arm='enabled',
        source_time=12500, expected_time=6000, framebuffer=FRAMEBUFFER, trace_extent=TRACE_EXTENT,
        diagnostic_only=True, performanceEvidence=False, requested_main_mod_present=True,
        main_mod_presence_verified=False, dh_present=False, client_gpu_owner='root', launch_authorized=False)


def derive_control(control):
    expected = dict(debug=False, pitch=3.735, preset='PERFORMANCE', time=12500, weather='clear',
                    x=500.5, y=68.0, yaw=87.38, z=660.5)
    require(control == expected, 'Canonical 12500 control differs before daylight derivation')
    return dict(control, time=6000)


def qualify_source_boundaries(scene):
    report = json.loads((scene['source'] / 'workload-measurement.json').read_text())
    result = {}
    for boundary in ('input_state_start', 'input_state_end'):
        observed = report[boundary]
        require(observed.get('day_time') == 12500 and observed.get('dimension') == 'minecraft:overworld'
            and observed.get('rain') == observed.get('thunder') == 0, 'Canonical actual sunset boundary differs')
        for key in ('x', 'y', 'z', 'yaw', 'pitch'):
            require(matrix.camera_difference(observed.get(key), scene['control'][key], circular=key == 'yaw') <= .002,
                    'Canonical actual camera differs: ' + key)
        result[boundary] = {k: observed[k] for k in ('day_time', 'dimension', 'rain', 'thunder', 'x', 'y', 'z', 'yaw', 'pitch')}
    return result


def qualify_archive(archive, registration, root=ROOT):
    """Reproduce reviewed manifest outputs; reject any extra member/metadata/binding change."""
    root = Path(root).resolve(); archive, registration = Path(archive).resolve(), Path(registration).resolve()
    require(file_hash(root / diagnostic.normal.BASE) == diagnostic.normal.BASE_SHA256, 'Original source drift')
    manifest_bytes = (root / diagnostic.MANIFEST).read_bytes(); manifest = json.loads(manifest_bytes)
    original = diagnostic.archive_members(root / diagnostic.normal.BASE)
    expected = diagnostic.patch_members(original, manifest, 'enabled', True, root)
    actual = diagnostic.archive_members(archive)
    require(set(actual) == set(expected) | {diagnostic.METADATA}, 'Diagnostic member inventory differs')
    require(all(actual[name] == payload for name, payload in expected.items()), 'Diagnostic assembled member differs')
    metadata = json.loads(actual[diagnostic.METADATA]); reg = json.loads(registration.read_text())
    require(metadata['arm'] == 'enabled' and metadata['trace_default'] is True
            and metadata['diagnostic_only'] is True and metadata['performanceEvidence'] is False,
            'Enabled diagnostic metadata required')
    require(metadata['manifest_sha256'] == hashlib.sha256(manifest_bytes).hexdigest()
            and metadata['normal_inputs'] == manifest['normal_inputs'], 'Manifest provenance differs')
    for key in ('storage', 'layout', 'modes', 'paths', 'attribution'):
        require(metadata[key] == manifest[key], 'Diagnostic metadata differs: ' + key)
    refs = metadata['reference_member_sha256']
    require(len(refs) == 11 and all(hashlib.sha256(actual[n]).hexdigest() == pin for n, pin in refs.items()), 'Reference member pins differ')
    options = {diagnostic.SWITCH: ['true'], diagnostic.normal.SWITCH: ['true'],
               diagnostic.normal.REUSE_SWITCH: ['true'], 'CLOUDS_CUMULUS': ['true'], 'BLOCKY_CLOUDS': ['false']}
    require(metadata['required_effective_options'] == options, 'Private/consumer options differ')
    require(actual['LICENSE'] == original['LICENSE'], 'Attribution/license changed')
    archive_hash = file_hash(archive)
    capability = dict(effect='VOLUMETRICS', operation='shaderlod:work_trace', kind='COUNT', minimumRetainedWork=1)
    expected_registration = dict(metadataVersion=1,
        descriptor=dict(id='shaderlod:photon-light-rate-diagnostic-enabled', revision=1,
            protocols={'shaderlod:core': 2, 'shaderlod:effects': 2, 'shaderlod:capabilities': 1}, capabilities=[capability]),
        sourceBindings=[dict(sourceSha256=archive_hash, memberSha256=refs, requiredOptions=options)],
        exports=[dict(effect='VOLUMETRICS', operation='shaderlod:work_trace', kind='COUNT', uniformName=diagnostic.MARKER_UNIFORM)],
        capture=dict(source=str(archive), attachments=[0, 1, 2], pages=[0, 1, 2],
            markerUniform=diagnostic.MARKER_UNIFORM, markerValue=1, gridSide=8, maxCaptures=8,
            intervalDraws=60, format='RGBA32F', program='deferred1', colortexes=[9, 10, 11]), traceContract=metadata)
    require(reg == expected_registration, 'Exact WorkTrace registration contract differs')
    with zipfile.ZipFile(archive) as z:
        defaults = matrix.archive_defaults(z)
    require(all(defaults[k] == 'true' for k in (diagnostic.SWITCH, diagnostic.normal.SWITCH, diagnostic.normal.REUSE_SWITCH)), 'Enabled diagnostic defaults differ')
    return dict(archive=str(archive), archive_sha256=archive_hash, registration=str(registration),
        registration_sha256=file_hash(registration), metadata=metadata, registration_metadata=reg,
        complete_member_sha256={n: hashlib.sha256(b).hexdigest() for n, b in sorted(actual.items())},
        required_options=options, defaults=defaults, source_qualified=True, runtime='unverified')


def command(profile, registration, trace_output):
    argv = ['./gradlew', 'runClientLodOn', '-PrunDirectory=' + str(Path(profile).resolve()),
        '-PperformanceStack=minimal', '-PincludeDh=false', '-PmeasureWorkload=true', '-PmeasureOnly=true',
        '-PinitialWarmup=30', '-PmeasurementSeconds=8', '-PbenchmarkWindowScale=2',
        '-PcomparisonFullscreen=false', '-PfixedWetness=0', '-PworkTrace=true', '-PworkTraceOnly=true',
        '-PworkTraceMetadata=' + str(Path(registration).resolve()), '-PworkTraceOutput=' + str(Path(trace_output).resolve())]
    argv += ['-P' + name + '=false' for name in OFF]
    for task in canonical.BUILD_TASKS: argv += ['-x', task]
    return argv


def source_freeze(root=ROOT):
    root = Path(root).resolve()
    names = ('build.gradle', 'settings.gradle', 'gradlew', 'gradle/wrapper/gradle-wrapper.jar',
        'gradle/wrapper/gradle-wrapper.properties', 'dependencies/runtime.lock.json', 'dependencies/performance-mods.lock.json',
        'scripts/prepare_photon_light_rate_diagnostic.py', 'scripts/build_photon_volume_light_rate_diagnostic.py',
        'scripts/build_photon_volume_light_rate.py', 'scripts/prepare_photon_taau_screen.py',
        'scripts/run_proof_matrix.py', 'scripts/prepare_isolations.py', 'scripts/setup_comparison.py',
        'scripts/prepare_reduced_effects_profiles.py', 'scripts/prepare_photon_ao_reuse_profiles.py',
        'scripts/prepare_parallel_lod_profiles.py', 'scripts/qualify_volume_fast_artifacts.py',
        str(diagnostic.MANIFEST), str(diagnostic.normal.MANIFEST))
    paths = [root / n for n in names]
    for tree in ('src/main', 'src/devClient', 'shaderpacks/photon-volume-light-rate', 'shaderpacks/photon-volume-light-rate-diagnostic'):
        paths += [p for p in (root / tree).rglob('*') if p.is_file()]
    for module in tuple(sys.modules.values()):
        name = getattr(module, '__file__', None)
        if name:
            path = Path(name).resolve()
            if path.is_relative_to(root / 'scripts') and path.is_file(): paths.append(path)
    paths.append(root / 'tests/test_photon_light_rate_diagnostic.py')
    return {str(p): file_hash(p) for p in sorted(set(paths))}


def prepare(output=OUTPUT, build=BUILD):
    operation = plan(output, build); matrix.require_exclusive_runtime()
    # Canonical qualification stays pinned to its existing 12500 source family.
    scene = canonical.qualify_source(); source_boundaries = qualify_source_boundaries(scene)
    control = derive_control(scene['control'])
    output, build = Path(operation['output']), Path(operation['build'])
    build.mkdir(parents=True)
    built = diagnostic.build(build / 'volume-light-rate-diagnostic-enabled-on.zip', 'enabled', True)
    qualified = qualify_archive(built['archive'], built['registration'])
    with unlocked_world(scene['world']):
        original_fingerprint = world_fingerprint(scene['world'])
        require(original_fingerprint == SOURCE_WORLD_SHA256, 'Pinned canonical post-capture world differs')
        output.mkdir(parents=True); base = output / 'coast6000'; base.mkdir()
        provenance = output / 'source-provenance'; provenance.mkdir()
        for name, source in (('control12500.json', scene['source'].parent / 'control.json'),
            ('source-manifest.json', scene['source'].parent / 'workload-manifest.json'),
            ('source-measurement.json', scene['source'] / 'workload-measurement.json')):
            shutil.copy2(source, provenance / name)
        write_json(base / 'control.json', control)
        profile = base / 'enabled'
        storage_copytree(scene['source'], profile, ignore=shutil.ignore_patterns(*canonical.TRANSIENT))
        world = profile / 'saves' / WORLD; world.parent.mkdir(exist_ok=True)
        storage_copytree(scene['world'], world, ignore=shutil.ignore_patterns('session.lock'))
        require(world_fingerprint(world) == original_fingerprint, 'Copied world fingerprint differs')
        require(not any((profile / 'mods').glob('*.jar')), 'Fresh profile must remain minimal/noDH')
        packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True)
        archive = packs / Path(qualified['archive']).name; shutil.copy2(qualified['archive'], archive)
        flags = {name: values[0] for name, values in qualified['required_options'].items()}
        requested = scene['requested_options']['native'] | flags
        effective = scene['qualification']['effective_option_maps']['native'] | flags
        require(len(effective) == 369 and effective['CLOUDS_TEMPORAL_UPSCALING'] == '4', 'Native reduced cloud map differs')
        matrix.normalize_requested_options(qualified['defaults'], requested)
        archive.with_name(archive.name + '.txt').write_text(''.join(f'{k}={v}\n' for k, v in sorted(requested.items())))
        iris_path = profile / 'config/iris.properties'; iris = matrix.read_option_properties(iris_path.read_text()); iris['shaderPack'] = archive.name
        iris_path.write_text(''.join(f'{k}={v}\n' for k, v in sorted(iris.items())))
        (profile / 'config/shaderlod-client.toml').write_text(fresh_config('adaptive'))
        (profile / 'config/shaderlod-effects-client.toml').write_text(effects_config('inherit'))
        request = dict(run_id=EXPERIMENT, case='water-adaptive', task='runClientLodOn', stack='minimal',
            include_dh=False, measurement_seconds=8, gpu_queries=False, fixed_wetness=0,
            diagnostic_only=True, performanceEvidence=False)
        write_json(profile / 'workload-request.json', request)
        arm = dict(profile=str(profile), archive=archive.name, archive_sha256=file_hash(archive),
            requested_options=requested, expected_effective_options=effective,
            expected_effective_options_sha256=matrix.canonical_options_hash(effective),
            requested_main_mod_present=True, main_mod_presence_verified=False, dh_present=False,
            global_enabled=True, source_world_sha256=original_fingerprint,
            core_config=tomllib.loads((profile / 'config/shaderlod-client.toml').read_text()),
            effects_config=tomllib.loads((profile / 'config/shaderlod-effects-client.toml').read_text()),
            prepared_writable_settings=canonical.writable_settings(profile))
        write_json(base / 'workload-manifest.json', dict(schema=1, scene='coast6000', expected_time=6000,
            configuration_only=False, diagnostic_only=True, performanceEvidence=False, experiment_family=EXPERIMENT,
            source_world_sha256=original_fingerprint, control=control, experiment_arms={'enabled': arm}))
        arm['profile_inputs'] = matrix.profile_inputs(profile)
        arm['input_files_sha256'] = {str(p): file_hash(p) for p in profile.rglob('*') if p.is_file() and not p.is_relative_to(world)}
        require(world_fingerprint(scene['world']) == original_fingerprint, 'Canonical source world changed')
        require(all(file_hash(Path(p)) == pin for p, pin in scene['qualification']['source_files_sha256'].items()), 'Canonical source inputs changed')
        state = dict(schema=1, status='SOURCE_PREPARED_HOLD_ARTIFACT_RECEIPT', operation=operation,
            diagnostic_only=True, performanceEvidence=False, quality='unverified', reuse='unobserved',
            source_qualification=scene['qualification'], qualified_archive=qualified, arm=arm,
            source_boundary_qualification=source_boundaries,
            control_derivation=dict(source=scene['control'], runtime=control,
                source_sha256=file_hash(provenance / 'control12500.json'), runtime_sha256=file_hash(base / 'control.json'),
                changed_fields=['time']), source_world_sha256=original_fingerprint,
            sourceFrozen=None, artifact_receipt=None, launch_ready=False, launch_authorized=False,
            requested_main_mod_present=True, main_mod_presence_verified=False,
            command=command(profile, qualified['registration'], build / 'raw-work-trace.json'), request=request,
            stop_procedure=STOP, owned_live_processes=[], created_at_utc=matrix.utc_now(),
            next_action='Root reviews freshly independently derived mod-present artifact receipt, then finalizes sourceFrozen before launch.')
        write_json(output / 'light-rate-preparation.json', state)
        return state


def finalize(marker, receipt, receipt_sha256):
    """Root supplies an independently generated, reviewed current mod-present receipt."""
    marker, receipt = Path(marker).resolve(), Path(receipt).resolve()
    require(marker == OUTPUT / 'light-rate-preparation.json', 'Only owned preparation marker accepted')
    state = json.loads(marker.read_text())
    require(state['status'] == 'SOURCE_PREPARED_HOLD_ARTIFACT_RECEIPT', 'Preparation must be on artifact HOLD')
    require(isinstance(receipt_sha256, str) and len(receipt_sha256) == 64 and file_hash(receipt) == receipt_sha256,
            'Exact independently root-reviewed receipt required')
    external = json.loads(receipt.read_text())
    require(external.get('include_shaderlod') is True, 'Explicit new mod-present receipt required')
    directories = {str(ROOT / p): artifacts.directory_identity(ROOT / p) for p in artifacts.DIRS}
    artifacts.validate_expected_receipt(external, directories, include_shaderlod=True)
    verify_prepared(state)
    state.update(status='PREPARED_SOURCE_FROZEN_ROOT_LAUNCH_PENDING', launch_ready=True,
        source_boundary_qualification=qualify_source_boundaries(canonical.qualify_source()),
        sourceFrozen=dict(source_files_sha256=source_freeze(), development_runtime_inputs=matrix.development_fingerprints(),
            auxiliary_directory_identities=directories, frozen_at_utc=matrix.utc_now()),
        artifact_receipt=dict(path=str(receipt), sha256=receipt_sha256, receipt=external),
        # Receipt freezes expectations. Actual runtime mod presence remains unverified.
        main_mod_presence_verified=False, next_action='Root reserves idle client/GPU, verifies sourceFrozen, records exact launcher/Java identities and executes the frozen command.')
    write_json(marker, state); return state


def verify_prepared(state):
    row = state['arm']; profile = Path(row['profile']); q = state['qualified_archive']
    require(qualify_archive(q['archive'], q['registration']) == q, 'Qualified archive/registration drift')
    require(all(file_hash(Path(p)) == pin for p, pin in row['input_files_sha256'].items()), 'Fresh profile input drift')
    require(matrix.profile_inputs(profile) == row['profile_inputs'], 'Fresh profile/control/manifest drift')
    require(world_fingerprint(profile / 'saves' / WORLD) == state['source_world_sha256'], 'Fresh copied world drift')
    require(derive_control(state['control_derivation']['source']) == row['profile_inputs']['control'], '6000 derivation drift')
    scene = canonical.qualify_source(); qualify_source_boundaries(scene)
    require(scene['qualification'] == state['source_qualification'], 'Original source qualification drift')
    require(world_fingerprint(scene['world']) == SOURCE_WORLD_SHA256 == state['source_world_sha256'], 'Canonical source world drift')
    if state.get('sourceFrozen'):
        frozen = state['sourceFrozen']
        require(source_freeze() == frozen['source_files_sha256'], 'SourceFrozen source drift')
        require(matrix.development_fingerprints() == frozen['development_runtime_inputs'], 'SourceFrozen compiled runtime drift')
        e = state['artifact_receipt']; require(file_hash(Path(e['path'])) == e['sha256'], 'Independent artifact receipt drift')
        artifacts.validate_expected_receipt(e['receipt'], frozen['auxiliary_directory_identities'], include_shaderlod=True)
    return True


def decode_raw(pages, lighting_steps, ambient_steps):
    require(isinstance(pages, list) and len(pages) == 3 and all(isinstance(p, list) and len(p) == 4 for p in pages), 'Missing raw pages/channels')
    require(all(isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v)
        and 0 <= v <= 16777215 and v == int(v) for p in pages for v in p), 'Raw sentinel/range/nonfinite/fractional integer')
    require(pages[2] == [1, MARKER, 1, 0], 'Wrong light-rate schema/marker/valid/reserved')
    n, q, r, s = map(int, pages[0]); l, a, mode, path = map(int, pages[1])
    require((l, a) == (lighting_steps, ambient_steps) and 0 <= mode <= 3 and 0 <= path <= 2, 'Configured work/mode/path mismatch')
    require(n >= q >= r >= 0 and s == (q - r) * (l + a), 'N/Q/R/S accounting mismatch')
    require(mode == 3 or r == 0, 'Reuse in nonreuse mode')
    require(r <= max(0, (q - 1) // 2), 'Reuse exceeds fresh-history bound')
    require((path == 0 and mode == 0 and n == q == r == s == 0)
        or (path == 1 and mode == 1 and r == 0) or (path == 2 and mode == 3), 'Enabled diagnostic path/mode contract mismatch')
    return dict(N=n, Q=q, R=r, S=s, L=l, A=a, mode=mode, path=path)


def validate_trace(trace, qualified, effective):
    require(trace.get('kind') == 'synchronous_raw_work_trace_diagnostic' and trace.get('evidenceVersion') == 1
        and trace.get('performanceEvidence') is False and trace.get('captureWindowFinished') is True
        and trace.get('terminalStatus') == 'CAPTURED' and trace.get('captures'), 'Terminal same-draw trace missing')
    require(trace.get('metadata') == qualified['registration_metadata'] and trace.get('metadataPath') == qualified['registration'], 'Exact captured registration mismatch')
    require(trace.get('hook') == 'FullScreenQuadRenderer.renderQuad()V TAIL', 'Unknown capture hook')
    captures = trace['captures']; require(1 <= len(captures) <= 8, 'Capture count outside registered bound')
    totals, modes, paths = Counter(), Counter(), Counter(); points = []; ordinals = []
    for capture in captures:
        require(capture.get('status') == 'RAW_SAME_DRAW_CAPTURE' and capture.get('performanceEvidence') is False
            and not capture.get('restoreErrors'), 'Raw status/state restoration failure')
        require(capture.get('attachments') == [0, 1, 2] and capture.get('schema') == 1, 'Raw attachment/schema mismatch')
        require(capture.get('viewport') == [0, 0, *TRACE_EXTENT], 'Quarter-native trace viewport required')
        require(capture.get('program', 0) > 0 and capture.get('drawFramebuffer', 0) > 0, 'Producer/FBO absent')
        uniform = capture.get('schemaUniform', {})
        require(uniform.get('location', -1) >= 0 and uniform.get('type') == 5124 and uniform.get('value') == 1, 'Typed dedicated schema marker absent')
        images = capture.get('attachmentImages', [])
        require(len(images) == 3 and all(i.get('internalFormat') == 34836 and i.get('object', 0) > 0
            and i.get('level') == 0 and [i.get('width'), i.get('height')] == TRACE_EXTENT for i in images), 'RGBA32F exact image extent/identity differs')
        require(len({i['object'] for i in images}) == 3, 'Diagnostic pages alias one texture')
        samples = capture.get('samples', []); require(len(samples) == 64, 'Complete registered 8x8 raw grid required')
        expected_grid = {(int((2*x+1)*744/16), int((2*y+1)*418/16)) for y in range(8) for x in range(8)}
        require({(p['x'], p['y']) for p in samples} == expected_grid, 'Raw sample grid differs')
        require(isinstance(capture.get('drawOrdinal'), int) and capture['drawOrdinal'] > 0
            and isinstance(capture.get('renderFrameOrdinal'), int) and capture['renderFrameOrdinal'] >= 0, 'Draw/frame identity missing')
        ordinals.append(capture['drawOrdinal'])
        for sample in samples:
            # Deliberately do not use legacy decoded labels/status for this separate raw contract.
            d = decode_raw(sample['pages'], int(effective['CLOUDS_CUMULUS_LIGHTING_STEPS']), int(effective['CLOUDS_CUMULUS_AMBIENT_STEPS']))
            totals.update({k: d[k] for k in ('N', 'Q', 'R', 'S')}); modes[d['mode']] += 1; paths[d['path']] += 1
            points.append(dict(x=sample['x'], y=sample['y'], raw_light_rate=d, drawOrdinal=capture['drawOrdinal'],
                renderFrameOrdinal=capture['renderFrameOrdinal'], worldGameTime=capture.get('worldGameTime'),
                frameCounter=capture.get('frameCounter'), frameTimeCounter=capture.get('frameTimeCounter')))
    require(all(b > a for a, b in zip(ordinals, ordinals[1:])), 'Draw identities repeated/out of order')
    return dict(diagnostic_only=True, performanceEvidence=False, captures=len(captures), sampled_rays=len(points),
        counts=dict(totals), modes=dict(modes), paths=dict(paths), positive_reuse_observed=totals['R'] > 0,
        next_action='Normal-candidate matched quality screen only' if totals['R'] > 0 else 'HOLD: sampled reuse absent; no timing justification',
        points=points, limits='Unpaired cold capture in one JVM. Sampled branch observations only; no causal work percentage, whole-screen coverage, quality or speed.')


def validate_report(report, state):
    expected, inputs, request = state['arm'], state['arm']['profile_inputs'], state['request']
    require(report.get('status') == 'complete' and report.get('request') == request, 'Stale/incomplete workload report')
    require(report.get('diagnostic_only') is True and report.get('performanceEvidence') is False, 'Diagnostic-only report required')
    require(report.get('modPresent') is True and report.get('dhPresent') is False, 'Actual mod/noDH mismatch')
    require(report.get('actual_shader_sha256') == expected['archive_sha256'], 'Loaded archive differs')
    require(report.get('effective_shader_options') == expected['expected_effective_options']
        and report.get('effective_shader_options_sha256') == expected['expected_effective_options_sha256'], 'Complete loaded option map differs')
    require(report.get('control_sha256') == inputs['control_sha256'], 'Control changed')
    require(report.get('input_state_start') == report.get('input_state_end'), 'Inputs changed during capture')
    for boundary in ('input_state_start', 'input_state_end'):
        observed = report.get(boundary, {})
        require(observed.get('day_time') == 6000 and observed.get('dimension') == 'minecraft:overworld'
            and observed.get('rain') == observed.get('thunder') == 0, 'Actual boundary time/weather/dimension differs: ' + boundary)
        require(observed.get('shaders_in_use') is True and observed.get('fallback') is False
            and observed.get('lod_settings', {}).get('enabled') is True, 'Actual pipeline/global bridge differs')
        require(observed.get('shader_pack') == inputs['shader_pack'] and [observed.get('framebuffer_width'), observed.get('framebuffer_height')] == FRAMEBUFFER
            and observed.get('requested_chunks') == observed.get('effective_chunks') == 24, 'Native source/extent/radius differs')
        for k in ('x', 'y', 'z', 'yaw', 'pitch'):
            require(matrix.camera_difference(observed.get(k), inputs['control'][k], circular=k == 'yaw') <= .002, 'Camera differs: ' + k)
    require(report.get('gpu_samples') == [] and report.get('cpu_samples') == [], 'Diagnostic query/timing streams present')
    require(report.get('warmup_seconds') == 30 and report.get('elapsed_seconds', 0) >= 7.75, 'Untimed capture duration shorter than requested')
    window = report.get('window_state', {}).get('capture', {}); count = window.get('observations', 0)
    require(count > 0 and window.get('available_observations') == count and not window.get('transitions')
        and not window.get('dropped_transitions'), 'Stable available window observation required')
    require(window.get('window_size_counts') == {'1488x836': count} and window.get('framebuffer_size_counts') == {'2976x1672': count}, 'Actual sampled native window differs')
    for field, value in (('focused', True), ('visible', True), ('iconified', False)):
        require(window.get(field) == dict(true_observations=count if value else 0,
            false_observations=0 if value else count, unknown_observations=0), 'Actual window ' + field + ' differs')
    external = state.get('artifact_receipt'); require(external is not None, 'Root-reviewed current artifact receipt absent')
    evidence = report.get('loaded_artifact_evidence', {})
    require(evidence.get('valid') is True and evidence.get('errors') == []
        and evidence.get('complete_mod_coverage') is True and evidence.get('complete_identities') is True, 'Direct loaded artifact capture failed')
    inventory = report.get('loaded_artifact_inventory')
    require(inventory == evidence.get('loaded_artifact_inventory') and report.get('loaded_mods') == external['receipt']['expected_loaded_mods'], 'Actual artifact registry/inventory differs')
    artifacts.validate_inventory_against_receipt(inventory, external['receipt']['loaded_artifact_inventory'], report['loaded_mods'])
    return matrix.validate_temporal_inputs(report, request)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=OUTPUT); parser.add_argument('--build-output', type=Path, default=BUILD)
    parser.add_argument('--dry-run', action='store_true'); parser.add_argument('--verify', type=Path)
    parser.add_argument('--finalize', type=Path); parser.add_argument('--artifact-receipt', type=Path); parser.add_argument('--artifact-sha256')
    args = parser.parse_args()
    if args.finalize and (not args.artifact_receipt or not args.artifact_sha256): parser.error('--finalize requires root-reviewed --artifact-receipt and --artifact-sha256')
    if args.finalize: result = finalize(args.finalize, args.artifact_receipt, args.artifact_sha256)
    elif args.verify: result = dict(verified=verify_prepared(json.loads(args.verify.read_text())), runtime='unverified')
    else: result = plan(args.output, args.build_output) if args.dry_run else prepare(args.output, args.build_output)
    print(json.dumps({k: v for k, v in result.items() if k in ('status', 'verified', 'runtime', 'launch_ready', 'main_mod_presence_verified', 'sourceFrozen') and k != 'sourceFrozen'}, indent=2))


if __name__ == '__main__': main()
