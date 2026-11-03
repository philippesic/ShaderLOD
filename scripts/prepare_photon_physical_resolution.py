#!/usr/bin/env python3
"""Prepare one original-native Photon physical-output diagnostic; never launch/build."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import sys
import tomllib
import uuid

import prepare_photon_pixel_baseline as baseline
from prepare_reduced_effects_profiles import require, write_json
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

coverage, canonical, matrix = baseline.coverage, baseline.canonical, baseline.matrix
OUTPUT = ROOT / 'run-workloads/physical-resolution-r8'
BUILD = ROOT / 'build/physical-resolution-prep-r8'
OWNED_FAMILIES = {'physical-resolution-r1': 'physical-resolution-prep-r1',
    'physical-resolution-r2': 'physical-resolution-prep-r2', 'physical-resolution-r3': 'physical-resolution-prep-r3',
    'physical-resolution-r4': 'physical-resolution-prep-r4', 'physical-resolution-r5': 'physical-resolution-prep-r5',
    'physical-resolution-r6': 'physical-resolution-prep-r6', 'physical-resolution-r7': 'physical-resolution-prep-r7',
    'physical-resolution-r8': 'physical-resolution-prep-r8'}
QUERY_FREE_FAMILIES = ('physical-resolution-r7', 'physical-resolution-r8')
RECEIPT = ROOT / 'build/mac-window-state-integrated-artifacts-r14/artifact-receipt.json'
RECEIPTS = {family: ROOT / 'build' / ('mac-window-state-integrated-artifacts-r14' if family in ('physical-resolution-r3','physical-resolution-r4','physical-resolution-r5','physical-resolution-r6','physical-resolution-r7','physical-resolution-r8')
    else 'physical-resolution-integrated-artifacts-r13') / 'artifact-receipt.json' for family in OWNED_FAMILIES}
JAR_SHA256 = 'e86b77eb478c5bbcae9f91fb14cf1712eb9b9fcb93d760a104eeb8245072b864'
MARKER = 'physical-resolution-preparation.json'
R1_MARKER = ROOT / 'run-workloads/physical-resolution-r1' / MARKER
R1_REPORT = R1_MARKER.parent / 'coastalwater/native/physical-resolution.json'
R1_MARKER_SHA256 = '4b7e1ece7d6817f11c5778def486c0bf6674b64199c83a372c86f696e5bf6bc9'
R1_REPORT_SHA256 = '08b54b9840d3276ba6fbeaea4f061fb7eb733620adb889b83d042b5f16b2d271'
R2_MARKER = ROOT / 'run-workloads/physical-resolution-r2' / MARKER
R2_REPORT = R2_MARKER.parent / 'coastalwater/native/physical-resolution.json'
R2_MARKER_SHA256 = '69f32905313dfd533152065f37d5539468626fe2744be4741d51dfcf940d7bf5'
R2_REPORT_SHA256 = '1649986a04a2566882e139fac357fb28cc080beccc8cefdaa8c26c354795c7b6'
R3_MARKER = ROOT / 'run-workloads/physical-resolution-r3' / MARKER
R3_REPORT = R3_MARKER.parent / 'coastalwater/native/physical-resolution.json'
R3_MARKER_SHA256 = '0f4277566e1ad401ef82f2222af4fa83c7ea97dfa9315e01e3ee6966e83bdc78'
R3_REPORT_SHA256 = 'd1adf0f9b928ad404db2bf3d35e014d4e72cc217a5c2931a67500f7f7a9d1eec'
R4_MARKER = ROOT / 'run-workloads/physical-resolution-r4' / MARKER
R4_REPORT = R4_MARKER.parent / 'coastalwater/native/physical-resolution.json'
R4_MARKER_SHA256 = 'ad974bd483525b4b187897baf318b34f545f98208cb68328c31f328556d53a4e'
R4_REPORT_SHA256 = 'acfd024ac7e5120fe68186925a035e805d7de6f1d4fb3fe9b51484b29c388f7b'
FINAL = 'ONE_UNUSED_ORIGINAL_NATIVE_PHYSICAL_RESOLUTION_PROFILE_READY_ROOT_REVIEW'
METHOD = 'Original native-TAA Photon; one JVM; actual physical output native/reduced/native; resized histories explicitly qualified'
EXTENTS = [[2976, 1672], [2232, 1254], [2976, 1672]]
WINDOWS = [[1488, 836], [1116, 627], [1488, 836]]
OFF = tuple(dict.fromkeys(canonical.OFF + (
    'measureWorkload', 'benchmark', 'cameraQualityOnly', 'pixelCoverage', 'pixelCoverageOnly',
    'coreUniformProbe', 'effectRuntimeProbe', 'rasterProbe', 'pixelHistory', 'pixelHistoryCapture',
    'pixelHistoryCaptureOnly', 'smoke')))
EVIDENCE = dict(diagnostic_only=True, quality_changing=True, performanceEvidence=False,
    adapter_gain=False, optimization_acceptance=False, repeated_gain=False,
    runtime='unverified', quality='unaccepted', shader_support_claim=False,
    evidence_class='original-native-physical-output-headroom-preparation')


def command(profile):
    query_free = Path(profile).parents[1].name in QUERY_FREE_FAMILIES
    argv = ['./gradlew', 'runClientLodOn', '-PrunDirectory=' + str(Path(profile).resolve()),
        '-PperformanceStack=minimal', '-PincludeDh=false', '-PphysicalResolution=true', '-PmeasureOnly=true',
        '-PbenchmarkWindowScale=2', '-PcomparisonFullscreen=false', '-PfixedWetness=1',
        '-PinitialWarmup=30', '-PbenchmarkSettleSeconds=8', '-PmeasurementSeconds=20',
        '-PgpuTiming=' + ('false' if query_free else 'true'), '-PcpuTiming=true', '-PgpuSampleInterval=5', '-PcameraQuality=']
    if Path(profile).parents[1].name in ('physical-resolution-r3','physical-resolution-r4','physical-resolution-r5','physical-resolution-r6'): argv += ['-PmacWindowState=true']
    argv += ['-P' + name + '=false' for name in OFF]
    for task in canonical.BUILD_TASKS: argv += ['-x', task]
    return argv


def plan(output=OUTPUT, build=BUILD, root=ROOT):
    root = Path(root).resolve()
    require(not Path(output).is_symlink() and not Path(build).is_symlink(), 'Symlink output unsupported')
    output, build = Path(output).resolve(), Path(build).resolve()
    require(output.parent == root / 'run-workloads' and output.name in OWNED_FAMILIES
        and build == root / 'build' / OWNED_FAMILIES[output.name], 'Only owned fresh family allowed')
    require(not output.exists() and not build.exists(), 'Preserve previous output; use --verify')
    profile = output / 'coastalwater/native'
    return EVIDENCE | dict(schema=1, family=output.name, output=str(output), build=str(build),
        profile=str(profile), profile_count=1, command=command(profile), method=METHOD,
        physical_extents=EXTENTS, logical_extents=WINDOWS, reduced_pixel_fraction=9/16,
        initial_warmup_seconds=30, settle_seconds=8, measurement_seconds=20, resize_timeout_seconds=30,
        minimum_session_seconds=114, minimum_render_frames_per_phase=100,
        gpu_queries=output.name not in QUERY_FREE_FAMILIES, minimum_gpu_samples_per_phase=0 if output.name in QUERY_FREE_FAMILIES else 60,
        gpu_sample_interval=5, coarse_cpu=True, main_mod_present=True, global_enabled=False,
        effects='seven INHERIT/unity', terrain_culling_shadow_features=False, requested_chunks=24,
        weather='clear', fixed_wetness=1, FOV=70, first_person=True, gui_scale=2, gui_hidden=True,
        client_gpu_owner='root', launch_authorized=False, owned_live_processes=[], stop_procedure=canonical.STOP,
        mac_window_startup_diagnostic=output.name in ('physical-resolution-r3','physical-resolution-r4','physical-resolution-r5','physical-resolution-r6'),
        actual_launch_variant='Root records supported native .app launch, flattened equivalent VM/program args and absolute --gameDir separately; CLI recipe is not actual native launch birth proof.' if output.name in ('physical-resolution-r4','physical-resolution-r5','physical-resolution-r6') else 'Exact recorded CLI launch recipe; actual process evidence remains required.',
        native_launch_semantics=dict(required_user_dir=str(profile), absolute_game_dir=str(profile),
            os_cwd='Record actual LaunchServices OS cwd separately; user.dir does not change OS cwd.',
            activation='Root performs and records actual title-bar input during startup; AXRaise alone previously did not establish focus.',
            logging='Preserve previous missing-relative-logging facts; qualify actual current logging paths and birth arguments.') if output.name in ('physical-resolution-r5','physical-resolution-r6') else None,
        primary_question='Qualify actual focus and native controls, then query-free physical-output headroom.' if output.name in QUERY_FREE_FAMILIES else 'Resolve actual window predicate/Cocoa startup state before any headroom interpretation.',
        assembly_hold='Parent accepts exact independent family-bound receipt and frozen source universe before assembly.',
        denominator='Original Photon/native TAA at changed physical output; deliberate quality change, not an upscaler or mod gain')


def maintained_sources():
    paths = [ROOT / 'scripts/prepare_photon_physical_resolution.py', ROOT / 'tests/test_photon_physical_resolution.py',
        ROOT / 'docs/PHOTON_PHYSICAL_RESOLUTION_RUNTIME.md']
    for module in tuple(sys.modules.values()):
        name = getattr(module, '__file__', None)
        if name:
            path = Path(name).resolve()
            if path.suffix == '.py' and path.is_relative_to(ROOT / 'scripts'): paths.append(path)
    return {str(p): file_hash(p) for p in sorted(set(paths))}


def qualify_runtime_receipt(path, pin, family='physical-resolution-r8'):
    require(family in OWNED_FAMILIES and Path(path).resolve() == RECEIPTS[family],
        'Explicit family-bound R14/R13 physical-resolution receipt required')
    external, dirs = coverage.qualify_receipt(path, pin)
    jar = ROOT / 'build/libs/shaderlod-0.1.0-dev.jar'
    require(external['receipt']['independent_evidence_files_sha256'].get(str(jar)) == file_hash(jar) == JAR_SHA256,
        'Current coherent main e86 JAR differs')
    return external, dirs, dict(path=str(jar), sha256=JAR_SHA256)


def r1_failure_provenance():
    require(file_hash(R1_MARKER) == R1_MARKER_SHA256 and file_hash(R1_REPORT) == R1_REPORT_SHA256,
        'Retained failed R1 preparation/report bytes changed')
    report = json.loads(R1_REPORT.read_text())
    require(report.get('status') == 'failed' and report.get('failure') ==
        'java.lang.IllegalStateException: Matched focused window/pacing contract failed'
        and report.get('native_dimensions_restored') is True and report.get('gui_restored') is True,
        'Retained R1 focus failure/restoration differs')
    require(len(report.get('phases', [])) == 1 and report['phases'][0].get('status') == 'failed'
        and report['phases'][0].get('gpu_samples_partial') == [] and report['phases'][0].get('cpu_samples_partial') == [],
        'Retained R1 unexpected captured timing evidence')
    return dict(files_sha256={str(R1_MARKER): R1_MARKER_SHA256, str(R1_REPORT): R1_REPORT_SHA256},
        scope='R1 failed focused window contract before capture; resource hooks observed but no speed evidence. Root activates actual window for fresh R2; focus gate unchanged.')


def r2_failure_provenance():
    require(file_hash(R2_MARKER) == R2_MARKER_SHA256 and file_hash(R2_REPORT) == R2_REPORT_SHA256,
        'Retained failed R2 preparation/report bytes changed')
    report = json.loads(R2_REPORT.read_text())
    require(report.get('status') == 'failed' and report.get('failure') ==
        'java.lang.IllegalStateException: Matched focused window/pacing contract failed'
        and report.get('native_dimensions_restored') is True and report.get('gui_restored') is True,
        'Retained R2 focus failure/restoration differs')
    require(len(report.get('phases', [])) == 1 and report['phases'][0].get('status') == 'failed'
        and report['phases'][0].get('gpu_samples_partial') == [] and report['phases'][0].get('cpu_samples_partial') == [],
        'Retained R2 unexpected captured timing evidence')
    return dict(files_sha256={str(R2_MARKER): R2_MARKER_SHA256, str(R2_REPORT): R2_REPORT_SHA256},
        scope='R2 failed focused window contract before capture; R3 first investigates actual predicate and Cocoa startup source. No performance conclusion or guard relaxation.')


def r3_failure_provenance():
    return actual_window_failure(R3_MARKER,R3_MARKER_SHA256,R3_REPORT,R3_REPORT_SHA256,'R3')


def r4_failure_provenance():
    return actual_window_failure(R4_MARKER,R4_MARKER_SHA256,R4_REPORT,R4_REPORT_SHA256,'R4')


def actual_window_failure(marker, marker_pin, path, report_pin, label):
    require(file_hash(marker) == marker_pin and file_hash(path) == report_pin,
        'Retained failed ' + label + ' preparation/report bytes changed')
    report = json.loads(path.read_text())
    prefix = 'java.lang.IllegalStateException: Matched focused window/pacing contract failed; actual='
    require(report.get('status') == 'failed' and report.get('failure', '').startswith(prefix)
        and report.get('native_dimensions_restored') is True and report.get('gui_restored') is True,
        'Retained ' + label + ' focus failure/restoration differs')
    actual = json.loads(report['failure'][len(prefix):])
    require(actual.get('focused') == 'false' and actual.get('visible') == 'true' and actual.get('iconified') == 'false'
        and [actual.get('framebuffer_width'), actual.get('framebuffer_height')] == [2976,1672],
        'Retained ' + label + ' actual window predicate differs')
    require(len(report.get('phases', [])) == 1 and report['phases'][0].get('status') == 'failed'
        and report['phases'][0].get('gpu_samples_partial') == [] and report['phases'][0].get('cpu_samples_partial') == [],
        'Retained ' + label + ' unexpected captured timing evidence')
    return dict(files_sha256={str(marker):marker_pin, str(path):report_pin}, actual_failed_window=actual,
        scope=label + ' focused=false before capture; later bounded launch-path diagnosis does not change source predicate or quality gate.')


def validate_options(options):
    require(type(options) is dict and len(options) == 366
        and matrix.canonical_options_hash(options) == canonical.SOURCE_OPTIONS_SHA256,
        'Exact complete canonical native366 map differs')
    require(options.get('TAA') == 'true' and options.get('TAAU') == 'false'
        and options.get('TAAU_RENDER_SCALE') == '0.75', 'Original native temporal selectors differ')


def validate_config(main_text, effects_text):
    expected = tomllib.loads(coverage.fresh_config('high'))
    require(tomllib.loads(main_text) == expected, 'Exact global OFF/inert terrain/culling/shadow config differs')
    effects = tomllib.loads(effects_text)
    require(effects == tomllib.loads(coverage.effects_config('inherit')) and len(effects['effects']) == 7,
        'Exact seven INHERIT/unity effect controls differ')
    return dict(main=expected, effects=effects)


def validate_manifest(manifest, canonical_control):
    require(manifest.get('schema') == 1 and manifest.get('scene') == 'coastalwater'
        and manifest.get('configuration_only') is False and manifest.get('source_world_sha256') == coverage.WORLD_SHA256,
        'Frozen coastal scene/save/schema differs')
    require(manifest.get('control') == canonical_control | {'enabled': False}, 'Exact clear/global OFF control differs')
    source = manifest.get('source_qualification', {}); model = manifest.get('experiment_arms', {}).get('native', {})
    members = source.get('complete_member_sha256', {})
    require(len(members) == 531 and matrix.canonical_options_hash(members) == source.get('complete_member_map_sha256')
        and source.get('archive_sha256') == model.get('archive_sha256') == canonical.ORIGINAL_SHA256,
        'Pinned complete original531 source differs')
    validate_options(model.get('expected_effective_options'))
    require(model.get('expected_effective_options_sha256') == canonical.SOURCE_OPTIONS_SHA256
        and model.get('requested_main_mod_present') is True and model.get('dh_present') is False,
        'Original/main/noDH option scope differs')
    require(manifest.get('physical_resolution_recipe', {}).get('method') == METHOD
        and manifest['physical_resolution_recipe'].get('physical_extents') == EXTENTS,
        'Physical native/reduced/native recipe differs')
    return dict(member_count=531, option_count=366, main_global_enabled=False)


def prepare(receipt_path, receipt_pin, output=OUTPUT, build=BUILD):
    operation = plan(output, build); matrix.require_exclusive_runtime()
    historical = r1_failure_provenance() if operation['family'] != 'physical-resolution-r1' else None
    second_failure = r2_failure_provenance() if operation['family'] in ('physical-resolution-r3','physical-resolution-r4','physical-resolution-r5','physical-resolution-r6') else None
    third_failure = r3_failure_provenance() if operation['family'] in ('physical-resolution-r4','physical-resolution-r5','physical-resolution-r6') else None
    fourth_failure = r4_failure_provenance() if operation['family'] in ('physical-resolution-r5','physical-resolution-r6') else None
    external, dirs, jar = qualify_runtime_receipt(receipt_path, receipt_pin, operation['family'])
    scene = canonical.qualify_source(); native = scene['qualification']['effective_option_maps']['native']; validate_options(native)
    settings = canonical.semantic_properties((scene['source'] / 'options.txt').read_text(), ':')
    require(settings.get('fov') == '0.0' and settings.get('guiScale') == '2' and settings.get('renderDistance') == '24',
        'Canonical FOV70/gui2/chunks24 differs')
    runtime = baseline.control_prep.current_runtime_sources(); compiled = matrix.development_fingerprints(); frozen = maintained_sources()
    output, build = Path(operation['output']), Path(operation['build'])
    with unlocked_world(scene['world']):
        fingerprint = world_fingerprint(scene['world']); require(fingerprint == coverage.WORLD_SHA256, 'Canonical stopped coastal world differs')
        build.mkdir(parents=True); output.mkdir(parents=True); marker = output / MARKER
        state = EVIDENCE | dict(schema=1, status='PREPARING', created_at_utc=datetime.now(timezone.utc).isoformat(),
            operation=operation, canonical_source_qualification=scene['qualification'], source_world_sha256=fingerprint,
            maintained_source_files_sha256=frozen, runtime_sources_sha256=runtime, development_runtime_inputs=compiled,
            independent_artifact_receipt=external, auxiliary_directory_identities=dirs, production_jar=jar,
            r1_failure_provenance=historical,
            r2_failure_provenance=second_failure,
            r3_failure_provenance=third_failure,
            r4_failure_provenance=fourth_failure,
            launch_authorized=False, owned_live_processes=[], postanalysis_plan=postanalysis_plan())
        write_json(marker, state)
        try:
            inventory = coverage.live.discover(scene['archive'], scene['requested_options']['native'], build, 'original-native')
            validate_options(inventory['options']); require(inventory['options'] == native, 'Actual pinned Iris discovery differs')
            inventory_path = build / 'option-inventory.json'; write_json(inventory_path, inventory)
            state['complete_option_inventory'] = dict(path=str(inventory_path), sha256=file_hash(inventory_path))
            source = dict(archive_sha256=canonical.ORIGINAL_SHA256,
                complete_member_sha256=scene['qualification']['complete_archive_member_sha256'])
            source['complete_member_map_sha256'] = matrix.canonical_options_hash(source['complete_member_sha256'])
            profile = Path(operation['profile']); profile.parent.mkdir(parents=True)
            control = scene['control'] | {'enabled': False}; write_json(profile.parent / 'control.json', control)
            model = dict(archive=scene['archive'].name, archive_sha256=canonical.ORIGINAL_SHA256,
                requested_options=scene['requested_options']['native'], expected_effective_options=native,
                expected_effective_options_sha256=canonical.SOURCE_OPTIONS_SHA256,
                requested_main_mod_present=True, main_mod_presence_verified=False, dh_present=False)
            manifest = EVIDENCE | dict(schema=1, scene='coastalwater', configuration_only=False,
                source_world_sha256=fingerprint, control=control, experiment_arms={'native': model},
                source_qualification=source, physical_resolution_recipe=operation)
            validate_manifest(manifest, scene['control']); write_json(profile.parent / 'workload-manifest.json', manifest)
            storage_copytree(scene['source'], profile, ignore=shutil.ignore_patterns(*canonical.TRANSIENT))
            (profile / 'saves').mkdir(exist_ok=True)
            storage_copytree(scene['world'], profile / 'saves' / WORLD, ignore=shutil.ignore_patterns('session.lock'))
            require(not any((profile / 'mods').glob('*.jar')), 'Extra minimal/noDH profile mods')
            packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True); archive = packs / scene['archive'].name
            shutil.copy2(scene['archive'], archive)
            archive.with_name(archive.name + '.txt').write_text(''.join(f'{k}={v}\n' for k, v in sorted(model['requested_options'].items())))
            iris_path = profile / 'config/iris.properties'; iris = matrix.read_option_properties(iris_path.read_text()); iris['shaderPack'] = archive.name
            iris_path.write_text(''.join(f'{k}={v}\n' for k, v in sorted(iris.items())))
            main, effects = coverage.fresh_config('high'), coverage.effects_config('inherit')
            state['prepared_config'] = validate_config(main, effects)
            (profile / 'config/shaderlod-client.toml').write_text(main)
            (profile / 'config/shaderlod-effects-client.toml').write_text(effects)
            request = EVIDENCE | dict(run_id=str(uuid.uuid4()), method=METHOD, physical_extents=EXTENTS,
                initial_warmup_seconds=30, settle_seconds=8, measurement_seconds=20, fixed_wetness=1,
                gpu_queries=operation['gpu_queries'], coarse_cpu=True, gpu_sample_interval=5, provenance_only=True,
                consumer='PhysicalResolutionSession consumes exact startup properties; runtime UUID is generated in-client')
            write_json(profile / 'physical-resolution-request.json', request); state['request'] = request
            state['prepared_profile'] = baseline.profile_snapshot(profile)
            require(state['prepared_profile']['copied_world_sha256'] == fingerprint, 'Copied world differs')
            require(world_fingerprint(scene['world']) == fingerprint and maintained_sources() == frozen
                and baseline.control_prep.current_runtime_sources() == runtime and matrix.development_fingerprints() == compiled,
                'Complete source universe/world/compiled runtime changed during preparation')
            state['status'] = FINAL; verify(state); write_json(marker, state)
        except BaseException as error:
            state.update(status='FAILED_PREPARATION', error=str(error)); write_json(marker, state); raise
    return state


def verify(state):
    require(state.get('status') == FINAL and state['operation'].get('family') in OWNED_FAMILIES, 'Incomplete/current-family preparation')
    family = state['operation']['family']
    if family != 'physical-resolution-r1':
        require(r1_failure_provenance() == state.get('r1_failure_provenance'), 'Retained failed R1 provenance changed')
    if family in ('physical-resolution-r3','physical-resolution-r4','physical-resolution-r5','physical-resolution-r6'):
        require(r2_failure_provenance() == state.get('r2_failure_provenance'), 'Retained failed R2 provenance changed')
    if family in ('physical-resolution-r4','physical-resolution-r5','physical-resolution-r6'):
        require(r3_failure_provenance() == state.get('r3_failure_provenance'), 'Retained failed R3 provenance changed')
    if family in ('physical-resolution-r5','physical-resolution-r6'):
        require(r4_failure_provenance() == state.get('r4_failure_provenance'), 'Retained failed R4 provenance changed')
    require(maintained_sources() == state['maintained_source_files_sha256'], 'Recipe/source drift')
    require(baseline.control_prep.current_runtime_sources() == state['runtime_sources_sha256']
        and matrix.development_fingerprints() == state['development_runtime_inputs'], 'Complete runtime source/class universe drift')
    ext = state['independent_artifact_receipt']
    require(qualify_runtime_receipt(ext['path'], ext['sha256'], family) ==
        (ext, state['auxiliary_directory_identities'], state['production_jar']), 'Current family receipt/main/artifact drift')
    for name, pin in state['canonical_source_qualification']['source_files_sha256'].items():
        require(file_hash(Path(name)) == pin, 'Canonical source drift: ' + name)
    require(world_fingerprint(canonical.SOURCE / 'saves' / WORLD) == state['source_world_sha256'], 'Canonical stopped world drift')
    item = state['complete_option_inventory']; require(file_hash(Path(item['path'])) == item['sha256'], 'Actual Iris inventory drift')
    inventory = json.loads(Path(item['path']).read_text()); validate_options(inventory['options'])
    for name, pin in inventory['parser_dependency_sha256'].items(): require(file_hash(Path(name)) == pin, 'Pinned parser dependency drift')
    require(file_hash(Path(inventory['log'])) == inventory['log_sha256'], 'Parser log drift')
    profile = Path(state['operation']['profile'])
    require(profile == ROOT / 'run-workloads' / family / 'coastalwater/native'
        and Path(state['operation']['build']) == ROOT / 'build' / OWNED_FAMILIES[family]
        and state['operation']['command'] == command(profile)
        and baseline.profile_snapshot(profile) == state['prepared_profile'], 'Fresh profile/settings/world/argv drift')
    require(not any((profile / name).exists() for name in ('physical-resolution.json', 'benchmark.json', 'workload-measurement.json')),
        'Profile used; preserve runtime reports')
    manifest = json.loads((profile.parent / 'workload-manifest.json').read_text())
    validate_manifest(manifest, json.loads((canonical.SOURCE.parent / 'control.json').read_text()))
    require(state['request'] == json.loads((profile / 'physical-resolution-request.json').read_text())
        and state['request']['gpu_queries'] == (family not in QUERY_FREE_FAMILIES)
        and state['operation']['gpu_queries'] == state['request']['gpu_queries'], 'Provenance request/timing drift')
    require(validate_config((profile / 'config/shaderlod-client.toml').read_text(),
        (profile / 'config/shaderlod-effects-client.toml').read_text()) == state['prepared_config'], 'Prepared main/effects drift')
    return EVIDENCE | dict(status='verified-one-current-family-unused-original-native-physical-profile', family=family,
        launch_authorized=False, owned_live_processes=[], client_gpu_owner='root')


def postanalysis_plan():
    return dict(required=[
        'Root alone reserves client/GPU; exact argv, proven initial launcher/Java PID birth/cwd and terminal absence. No builders/tests/encoders/workers in quiet captures; actual focus/visible/noniconified guards remain mandatory.',
        'R3 first resolves actual window predicate/Cocoa source from before/after startup-only macWindowState JSON and physical failure actual-window JSON. Immediate snapshots do not establish asynchronous activation or later stable focus. No periodic observer or focus-policy mutation.',
        'R4 parent owns supported native-app launch generation/operation. Independently qualify exact flattened equivalent VM/program args, absolute --gameDir, actual bundle/process identity and birth/cwd; recorded CLI argv cannot substitute for actual native launch evidence.',
        'R5 native launch requires explicit -Duser.dir equal to its profile, absolute --gameDir, actual OS cwd/logging and activation evidence. R7 reuses CLI launch after human foreground availability; no automatic activation retry, source change or guard waiver.',
        'Actual physical-resolution.json schema1/UUID/method, complete three native/reduced/native phases, warm30/continuously stable settle8/capture20, exact GLFW logical/framebuffer + main allocation/view + Iris extents; no resize/reallocation/settling frame admitted.',
        'Complete original531 source/map366: compare all366 actual loaded option values and exact canonical eced hash at every boundary, never count alone. TAA=true/TAAU=false, main loaded/globalOFF/debugOFF/sevenINHERIT unity, noDH/minimal; complete actual configuration/artifact/source/readback receipts and fixed scene/FOV/time/weather/chunks.',
        'Arm-before-completed resource boundaries, main color/depth versions and Iris color/depth storage/allocation epochs advance for reduced/restored output; stable resources/pipeline identity during each capture. CPU clear submission return is not GPU completion/history convergence.',
        'When GPU queries requested: N5 positive unique ordered full-game-render rows >=60/phase. R7 is query-free and requires empty GPU rows. Both require >=100 rendered frames, finite coarse CPU rows/single epochs and coverage evidence. No pass/layer/probe/readback/screenshot contamination.',
        'Fixed wetness1 supplier/value and normally advancing clocks; exact source/config/settings/camera inputs across phases except authorized framebuffer width/height. No phase-synchronized temporal/history proof inferred.',
        'Success or failure independently labels native dimensions and GUI restoration; preserve failed/partial report and startup logs. Timing arithmetic only after all actual gates.'],
        limits=['Deliberate physical quality change measures headroom only. No delivered upscaler, original-quality optimization, per-effect attribution, independent repetition, support/default promotion or causal host explanation.'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true'); parser.add_argument('--prepare', action='store_true'); parser.add_argument('--verify', type=Path)
    parser.add_argument('--artifact-receipt', type=Path); parser.add_argument('--artifact-sha256'); parser.add_argument('--sources-frozen', action='store_true')
    args = parser.parse_args()
    require(sum(bool(v) for v in (args.dry_run, args.prepare, args.verify)) == 1, 'Select exactly one dry-run/prepare/verify')
    if args.verify:
        marker = args.verify.resolve()
        require(marker.parent.parent == ROOT / 'run-workloads' and marker.parent.name in OWNED_FAMILIES
            and marker.name == MARKER, 'Only owned marker allowed'); matrix.require_exclusive_runtime(); result = verify(json.loads(marker.read_text()))
    elif args.dry_run: result = plan()
    else:
        require(args.sources_frozen and args.artifact_receipt is not None and args.artifact_sha256 is not None,
            'Root frozen-source release and explicit accepted current-family receipt path/hash required')
        state = prepare(args.artifact_receipt, args.artifact_sha256)
        result = dict(status=state['status'], marker=str(OUTPUT / MARKER), marker_sha256=file_hash(OUTPUT / MARKER),
            profile=state['operation']['profile'], launch_authorized=False, owned_live_processes=[])
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == '__main__': main()
