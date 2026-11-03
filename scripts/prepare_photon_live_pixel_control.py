#!/usr/bin/env python3
"""Stage/finalize one fresh LIVE_PIXEL same-JVM settled screen; never launch/build."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import uuid
import zipfile

import analyze_live_pixel_control as analyzer
import prepare_photon_pixel_coverage as coverage
from prepare_reduced_effects_profiles import require, write_json
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

matrix, canonical, live = coverage.matrix, coverage.canonical, coverage.live
OUTPUT = ROOT / 'run-workloads/live-pixel-control-r1'
BUILD = ROOT / 'build/live-pixel-control-preparation-r1'
OWNED_FAMILIES = ('live-pixel-control-r1', 'live-pixel-control-r2')
SEQUENCE = [False, True, False, True]
OFF = ('gpuPassTiming', 'cpuLayerTiming', 'gpuAttribution', 'cloudSamplerProbe',
    'workTrace', 'workTraceOnly', 'spatialLayoutProbe', 'spatialLayoutOnly',
    'pixelCoverage', 'pixelCoverageOnly', 'dhDrawEvidence', 'waterSceneSurvey',
    'shadowObserve', 'visibilityLists', 'shadowCoherenceOracle', 'cameraQualityOnly', 'smokeTest')
EVIDENCE = dict(diagnostic_only=True, performanceEvidence=False, acceptance_candidate=False,
    quality='unaccepted', runtime='unverified', transition_accepted=False,
    raster_policy_proof=False, original_native_denominator=False,
    evidence_class='settled-incremental-live-pixel-cost-screen-preparation')
STAGED = 'SOURCE_PREPARED_RUNTIME_RECEIPT_PENDING'
FINAL = 'SOURCE_AND_CURRENT_RUNTIME_READY_ROOT_COST_SCREEN_PENDING'


def command(profile):
    argv = ['./gradlew', 'runClientLodOn', '-PrunDirectory=' + str(Path(profile).resolve()),
        '-PperformanceStack=minimal', '-PincludeDh=false', '-PgpuBenchmark=true',
        '-PbenchmarkOnly=true', '-PbenchmarkFeature=live_pixel', '-PbenchmarkStartEnabled=false',
        '-PinitialWarmup=30', '-PbenchmarkSettleSeconds=8', '-PmeasurementSeconds=20',
        '-PgpuTiming=true', '-PcpuTiming=true', '-PgpuSampleInterval=5', '-PbenchmarkWindowScale=2',
        '-PcomparisonFullscreen=false', '-PfixedWetness=0', '-PmeasureWorkload=false',
        '-PmeasureOnly=false', '-PcameraQuality=']
    argv += ['-P' + flag + '=false' for flag in OFF]
    for task in canonical.BUILD_TASKS: argv += ['-x', task]
    return argv


def plan(output=OUTPUT, build=BUILD, root=ROOT):
    root = Path(root).resolve()
    require(not Path(output).is_symlink() and not Path(build).is_symlink(), 'Symlink output unsupported')
    output, build = Path(output).resolve(), Path(build).resolve()
    require(output.parent == root / 'run-workloads' and output.name in OWNED_FAMILIES, 'Only owned fresh cost families allowed')
    revision = output.name.rsplit('-', 1)[1]
    require(build == root / 'build' / ('live-pixel-control-preparation-' + revision), 'Only matching owned cost preparation directory allowed')
    require(not output.exists() and not build.exists(), 'Preserve previous outputs; use finalize/verify')
    profile = output / 'coastalwater/toggle'
    return EVIDENCE | dict(schema=1, output=str(output), build=str(build), profile=str(profile),
        command=command(profile), feature='live_pixel', expected_method=analyzer.METHOD,
        phases=SEQUENCE, initial_warmup_seconds=30, settle_seconds=8, measurement_seconds=20,
        minimum_capture_seconds=80, minimum_settling_seconds=32, minimum_session_seconds=142,
        gpu_sample_interval=5, minimum_gpu_samples_per_phase=60, coarse_cpu=True,
        initial_global_enabled=False, client_gpu_owner='root', launch_authorized=False,
        raster_coverage_acceptance='separate and pending; no raster proof from timings',
        denominator='Private ON fixed TAAU graph: OFF1.0 versus ON.75. Original/default native and equivalent stock .75 remain separate.',
        screen=dict(minimum_each_adjacent_on_frame_time_reduction_percent=5,
            gpu_direction='Both ON full-frame medians lower than their adjacent OFF controls',
            maximum_off_drift_percent=10,
            quality='Root rejects conspicuous still-image damage; motion/transition quality remains pending',
            failure_policy='Keep raw guard failures. No favorable-baseline replacement, gate relaxation or automatic retry.'),
        stop_procedure=canonical.STOP)


def maintained_sources():
    """Shader/recipe inputs only; current main/dev source/classes are frozen at finalize."""
    names = ('scripts/prepare_photon_live_pixel_control.py', 'tests/test_photon_live_pixel_control_runtime.py',
        'docs/PHOTON_LIVE_PIXEL_CONTROL_RUNTIME.md', 'scripts/prepare_photon_pixel_coverage.py',
        'scripts/prepare_photon_live_pixel_budget.py', 'scripts/build_photon_live_pixel_budget.py',
        'scripts/prepare_photon_taau_screen.py', 'scripts/run_proof_matrix.py',
        'scripts/qualify_volume_fast_artifacts.py', 'scripts/prepare_parallel_lod_profiles.py',
        'scripts/prepare_reduced_effects_profiles.py', 'scripts/setup_comparison.py',
        'scripts/prepare_photon_ao_reuse_profiles.py', 'scripts/prepare_isolations.py',
        'scripts/analyze_live_pixel_control.py', 'scripts/analyze_inprocess_control.py',
        'scripts/summarize_workload.py', 'dependencies/runtime.lock.json', 'dependencies/performance-mods.lock.json')
    return {str(ROOT / name): file_hash(ROOT / name) for name in names}


def current_runtime_sources():
    paths = [ROOT / 'build.gradle']
    for tree in ('src/main', 'src/devClient', 'src/devClientTest'):
        paths += [p for p in (ROOT / tree).rglob('*') if p.is_file()]
    return {str(p): file_hash(p) for p in sorted(set(paths))}


def profile_snapshot(profile):
    profile = Path(profile)
    return dict(inputs=matrix.profile_inputs(profile), complete_settings=canonical.writable_settings(profile),
        profile_files_sha256={str(p): file_hash(p) for p in sorted(profile.rglob('*'))
            if p.is_file() and not p.is_relative_to(profile / 'saves')},
        parent_files_sha256={str(p): file_hash(p) for p in sorted(profile.parent.rglob('*'))
            if p.is_file() and not p.is_relative_to(profile)},
        copied_world_sha256=world_fingerprint(profile / 'saves' / WORLD))


def stage(output=OUTPUT, build=BUILD):
    operation = plan(output, build); matrix.require_exclusive_runtime()
    source = live.qualify_archive()
    require(source['archive_sha256'] == analyzer.ARCHIVE
        and matrix.canonical_options_hash(source['complete_member_sha256']) == analyzer.MEMBERS_HASH,
        'Analyzer and actual complete source pins differ')
    scene = canonical.qualify_source(source=canonical.SOURCE, root=ROOT, audit=canonical.AUDIT)
    frozen = maintained_sources(); output, build = Path(operation['output']), Path(operation['build'])
    build.mkdir(parents=True)
    requested, candidate, inventory = coverage.qualify_options(build, scene)
    require(candidate['sha256'] == analyzer.OPTIONS_HASH and analyzer.WORLD == coverage.WORLD_SHA256,
        'Analyzer and full actual option/source-world pins differ')
    with unlocked_world(scene['world']):
        fingerprint = world_fingerprint(scene['world']); require(fingerprint == analyzer.WORLD, 'Canonical source-world drift')
        output.mkdir(parents=True); marker = output / 'live-pixel-control-preparation.json'
        state = EVIDENCE | dict(schema=1, status='PREPARING_SOURCE_PROFILE', operation=operation,
            source_qualification=source, canonical_source_qualification=scene['qualification'],
            maintained_source_files_sha256=frozen, source_world_sha256=fingerprint,
            complete_option_inventory=dict(path=str(build / 'option-inventory.json'), sha256=file_hash(build / 'option-inventory.json'),
                delta=inventory['delta']), expected_effective_options=candidate['options'],
            expected_effective_options_sha256=candidate['sha256'], independent_artifact_receipt=None,
            runtime_sources_sha256=None, development_runtime_inputs=None, auxiliary_directory_identities=None,
            launch_authorized=False, owned_live_processes=[], launch_status='HOLD: root final independent current artifact receipt required')
        write_json(marker, state)
        try:
            profile = Path(operation['profile']); profile.parent.mkdir()
            control = scene['control'] | {'enabled': False}; write_json(profile.parent / 'control.json', control)
            model = dict(requested_options=requested, expected_effective_options=candidate['options'],
                expected_effective_options_sha256=candidate['sha256'], archive_sha256=live.ARCHIVE_SHA256,
                requested_main_mod_present=True, main_mod_presence_verified=False, dh_present=False)
            write_json(profile.parent / 'workload-manifest.json', EVIDENCE | dict(schema=1,
                scene='coastalwater', configuration_only=False, source_world_sha256=fingerprint,
                control=control, experiment_arms={'toggle': model}, source_qualification=source,
                benchmark_recipe=operation))
            storage_copytree(scene['source'], profile, ignore=shutil.ignore_patterns(*canonical.TRANSIENT))
            (profile / 'saves').mkdir(exist_ok=True)
            storage_copytree(scene['world'], profile / 'saves' / WORLD, ignore=shutil.ignore_patterns('session.lock'))
            require(world_fingerprint(profile / 'saves' / WORLD) == fingerprint, 'Copied source save drift')
            require(not any((profile / 'mods').glob('*.jar')), 'Extra profile mods/DH in minimal stack')
            packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True); archive = packs / live.ARCHIVE.name
            shutil.copy2(live.ARCHIVE, archive)
            archive.with_name(archive.name + '.txt').write_text(''.join(f'{k}={v}\n' for k, v in sorted(requested.items())))
            iris = matrix.read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive.name
            (profile / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k, v in sorted(iris.items())))
            (profile / 'config/shaderlod-client.toml').write_text(coverage.fresh_config('high'))
            (profile / 'config/shaderlod-effects-client.toml').write_text(coverage.effects_config('inherit'))
            request = EVIDENCE | dict(run_id=str(uuid.uuid4()), feature='live_pixel', phases=SEQUENCE,
                initial_global_enabled=False, initial_warmup_seconds=30, settle_seconds=8,
                measurement_seconds=20, gpu_queries=True, coarse_cpu=True, gpu_sample_interval=5,
                source_world_sha256=fingerprint, provenance_only=True,
                consumer='Existing BenchmarkSession reads Gradle properties; its session UUID is generated at runtime')
            write_json(profile / 'live-pixel-control-request.json', request)
            state.update(status=STAGED, request=request, prepared_profile=profile_snapshot(profile))
            require(state['prepared_profile']['copied_world_sha256'] == fingerprint, 'Final copied source save drift')
            require(maintained_sources() == frozen and live.qualify_archive() == source, 'Recipe/source changed during preparation')
            require(world_fingerprint(scene['world']) == fingerprint, 'Canonical source save changed')
            write_json(marker, state)
        except BaseException as failure:
            state.update(status='FAILED_SOURCE_PREPARATION', error=str(failure)); write_json(marker, state); raise
    return state


def verify_source(state):
    require(state['status'] in (STAGED, FINAL), 'Incomplete/failed source preparation')
    require(maintained_sources() == state['maintained_source_files_sha256'], 'Maintained recipe/source files drift')
    require(live.qualify_archive() == state['source_qualification'], 'Source archive/freeze drift')
    require(command(state['operation']['profile']) == state['operation']['command'], 'Launch argv drift')
    item = state['complete_option_inventory']; require(file_hash(Path(item['path'])) == item['sha256'], 'Complete Iris inventory drift')
    require(profile_snapshot(state['operation']['profile']) == state['prepared_profile'], 'Prepared profile/parent/settings/save drift')
    require(not (Path(state['operation']['profile']) / 'benchmark.json').exists(), 'Profile has been used; preserve it and prepare a new family')
    require(matrix.canonical_options_hash(state['expected_effective_options']) == analyzer.OPTIONS_HASH,
        'Full candidate option map drift')
    return EVIDENCE | dict(status='verified-source-stage', launch_authorized=False, launch_status=state['launch_status'])


def finalize(marker, receipt_path, receipt_sha256):
    marker = Path(marker).resolve()
    require(marker.parent.parent == ROOT / 'run-workloads' and marker.parent.name in OWNED_FAMILIES
        and marker.name == 'live-pixel-control-preparation.json', 'Only owned staged receipt allowed')
    state = json.loads(marker.read_text()); verify_source(state); matrix.require_exclusive_runtime()
    if state['status'] == FINAL:
        require(state['independent_artifact_receipt']['path'] == str(Path(receipt_path).resolve())
            and state['independent_artifact_receipt']['sha256'] == receipt_sha256, 'Finalized receipt replacement forbidden')
        verify(state); return state
    receipt, directories = coverage.qualify_receipt(receipt_path, receipt_sha256)
    runtime_sources = current_runtime_sources(); compiled = matrix.development_fingerprints()
    # No profile/save/control files are rewritten during finalization.
    state.update(status=FINAL, independent_artifact_receipt=receipt, auxiliary_directory_identities=directories,
        runtime_sources_sha256=runtime_sources, development_runtime_inputs=compiled,
        launch_status='READY FOR ROOT REVIEW: separate raster evidence remains pending; settled cost diagnostic only')
    require(coverage.qualify_receipt(receipt_path, receipt_sha256) == (receipt, directories), 'Artifact drift during finalization')
    require(current_runtime_sources() == runtime_sources and matrix.development_fingerprints() == compiled,
        'Runtime sources/classes changed during finalization')
    verify_source(state); write_json(marker, state)
    return state


def verify(state):
    verify_source(state)
    require(state['status'] == FINAL, 'Current independent runtime receipt required before launch')
    require(current_runtime_sources() == state['runtime_sources_sha256'], 'Current runtime sources drift')
    require(matrix.development_fingerprints() == state['development_runtime_inputs'], 'Compiled runtime drift')
    entry = state['independent_artifact_receipt']; receipt, directories = coverage.qualify_receipt(entry['path'], entry['sha256'])
    require(receipt == entry and directories == state['auxiliary_directory_identities'], 'Current artifact receipt/directory drift')
    return EVIDENCE | dict(status='verified-source-and-current-runtime-ready', launch_authorized=False,
        launch_status=state['launch_status'], owned_live_processes=[])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true'); parser.add_argument('--stage', action='store_true')
    parser.add_argument('--finalize', type=Path); parser.add_argument('--verify', type=Path)
    parser.add_argument('--artifact-receipt', type=Path); parser.add_argument('--artifact-receipt-sha256')
    parser.add_argument('--output', type=Path, default=OUTPUT); parser.add_argument('--build', type=Path, default=BUILD)
    args = parser.parse_args()
    try:
        require(sum(bool(x) for x in (args.dry_run, args.stage, args.finalize, args.verify)) == 1, 'Select exactly one dry-run/stage/finalize/verify operation')
        if args.verify: result = verify(json.loads(args.verify.read_text()))
        elif args.finalize:
            require(args.artifact_receipt is not None and args.artifact_receipt_sha256 is not None, 'Explicit root final artifact receipt path/hash required')
            result = finalize(args.finalize, args.artifact_receipt, args.artifact_receipt_sha256)
        else: result = plan(args.output, args.build) if args.dry_run else stage(args.output, args.build)
        print(json.dumps(result, indent=2, sort_keys=True))
    except (OSError, ValueError, KeyError, subprocess.SubprocessError, zipfile.BadZipFile) as failure:
        parser.exit(1, 'Cannot prepare live-pixel settled screen: ' + str(failure) + '\n')


if __name__ == '__main__': main()
