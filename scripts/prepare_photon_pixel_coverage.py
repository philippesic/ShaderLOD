#!/usr/bin/env python3
"""Prepare fresh source-qualified DEPTHCOVERAGE controls; never launch or build."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
import copy
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

import prepare_photon_live_pixel_budget as live
import prepare_photon_taau_screen as canonical
import qualify_volume_fast_artifacts as artifacts
import run_proof_matrix as matrix
from prepare_parallel_lod_profiles import effects_config
from prepare_reduced_effects_profiles import fresh_config, require, write_json
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

OUTPUT = ROOT / 'run-workloads/pixel-coverage-runtime-r1'
BUILD = ROOT / 'build/pixel-coverage-runtime-r1'
OWNED_FAMILIES = ('pixel-coverage-runtime-r1', 'pixel-coverage-runtime-r2', 'pixel-coverage-runtime-r3', 'pixel-coverage-runtime-r4')
WORLD_SHA256 = '89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46'
OPTIONS_SHA256 = 'd5853a047a582b9ae2c5bb264438afa2510a76e5164fbf8b31976afc8788b8a0'
MEMBERS_SHA256 = '96bb6e5a4c08c140a6882a7a47b5d3872cebf6730fd1030b3d9000339d165888'
ARMS = ('global-off', 'global-on')
OFF = ('gpuTiming', 'cpuTiming', 'cpuLayerTiming', 'gpuPassTiming', 'gpuAttribution',
       'cloudSamplerProbe', 'workTrace', 'workTraceOnly', 'spatialLayoutProbe',
       'spatialLayoutOnly', 'dhDrawEvidence', 'waterSceneSurvey', 'shadowObserve',
       'visibilityLists', 'shadowCoherenceOracle', 'smokeTest', 'gpuBenchmark',
       'benchmarkOnly', 'cameraQualityOnly')
EVIDENCE = dict(diagnostic_only=True, performanceEvidence=False, runtime='unverified',
    quality='unaccepted', transition_accepted=False, coverage_pair='HOLD: actual full raw depth captures required',
    timing_status='HOLD: synchronous diagnostic readback forbids speed acceptance')


def plan(output=OUTPUT, build=BUILD, root=ROOT):
    root = Path(root).resolve()
    require(not Path(output).is_symlink() and not Path(build).is_symlink(), 'Symlink output unsupported')
    output, build = Path(output).resolve(), Path(build).resolve()
    require(output.parent == root / 'run-workloads' and output.name in OWNED_FAMILIES, 'Only owned fresh profile families allowed')
    require(build.parent == root / 'build' and build.name.startswith('pixel-coverage-runtime-'), 'Only owned build prefix allowed')
    require(not output.exists() and not build.exists(), 'Preserve previous outputs; use verify')
    return EVIDENCE | dict(schema=1, output=str(output), build=str(build), arms=list(ARMS),
        framebuffer=[2976, 1672], window=[1488, 836], initial_warmup_seconds=60, capture_seconds=8,
        required_actual_renderer_warmup_seconds=30, expected_side_scales={'global-off': 1.0, 'global-on': .75},
        expected_nominal_footprints={'global-off': [2976, 1672], 'global-on': [2232, 1254]},
        root_launch_policy='Fresh OFF first; stop on guard failure. ON only after root accepts actual OFF source/scene/options/artifact/readback/restoration gates.',
        nominal_footprints_are_observations=False, client_gpu_owner='root', launch_authorized=False)


def command(profile):
    argv = ['./gradlew', 'runClientLodOn', '-PrunDirectory=' + str(Path(profile).resolve()),
        '-PperformanceStack=minimal', '-PincludeDh=false', '-PmeasureWorkload=true', '-PmeasureOnly=true',
        '-PinitialWarmup=60', '-PmeasurementSeconds=8', '-PbenchmarkWindowScale=2',
        '-PcomparisonFullscreen=false', '-PfixedWetness=0', '-PgpuSampleInterval=5',
        '-PpixelCoverage=true', '-PpixelCoverageSceneSha256=' + WORLD_SHA256]
    argv += ['-P' + key + '=false' for key in OFF]
    for task in canonical.BUILD_TASKS: argv += ['-x', task]
    return argv


def qualify_receipt(path, sha256):
    """No historical receipt defaults: caller supplies the independent integrated receipt."""
    path = Path(path).resolve()
    require(re.fullmatch('[0-9a-f]{64}', sha256) is not None, 'Explicit independent receipt SHA-256 required')
    require(file_hash(path) == sha256, 'Independent integrated artifact receipt drift')
    receipt = json.loads(path.read_text())
    directories = {str(ROOT / n): artifacts.directory_identity(ROOT / n) for n in artifacts.DIRS}
    artifacts.validate_expected_receipt(receipt, directories, include_shaderlod=True)
    require(len(receipt['expected_loaded_mods']) == 10 and len(receipt['loaded_artifact_inventory']) == 9,
            'Fresh ten-mod/nine-root receipt required')
    return dict(path=str(path), sha256=sha256, receipt=receipt), directories


def qualify_options(build, scene):
    requested = scene['requested_options']['native'] | {'TAA': 'true', 'TAAU': 'true',
        'TAAU_RENDER_SCALE': '0.75', live.adapter.SWITCH: 'true'}
    original = live.discover(scene['archive'], scene['requested_options']['native'], build, 'original-native')
    require(original['options'] == scene['qualification']['effective_option_maps']['native'], 'Canonical full map drift')
    candidate = live.discover(live.ARCHIVE, requested, build, 'live-fixed-taau')
    delta = live.validate_maps(original['options'], candidate['options'], requested)
    require(candidate['option_count'] == 368 and candidate['sha256'] == OPTIONS_SHA256, 'Probe complete option pin drift')
    stock = live.discover(scene['archive'], scene['requested_options']['native'] | {'TAAU': 'true'}, build, 'stock-taau75')
    require(stock['options'] == original['options'] | {'TAAU': 'true'}, 'Stock .75 option map drift')
    result = dict(original_native=original, live_fixed_taau=candidate, stock_taau75=stock, delta=delta)
    write_json(Path(build) / 'option-inventory.json', result)
    return requested, candidate, result


def prepared_signature(control, settings):
    """Drop only the checked authorized global flag; preserve full other settings."""
    require(type(control.get('enabled')) is bool, 'Control global enable must be a real boolean')
    value = copy.deepcopy(settings)
    config = value['settings']['config/shaderlod-client.toml']['value']
    require(type(config['lod'].get('enabled')) is bool and config['lod']['enabled'] == control['enabled'],
            'Copied config/control global mismatch')
    config['lod'].pop('enabled')
    # Recompute the semantic hash after this one permitted change. Cache identities
    # remain separately preserved and must match prepared copies, not ignored.
    value['settings_sha256'] = matrix.canonical_options_hash(value['settings'])
    other_control = copy.deepcopy(control); other_control.pop('enabled')
    return dict(requestedControlExceptAuthorizedGlobal=other_control, writableSettingsExceptAuthorizedGlobal=value)


def validate_pair(rows):
    require([r['arm'] for r in rows] == list(ARMS), 'Missing/duplicate/out-of-order controls')
    signatures = []
    for row in rows:
        enabled = row['arm'] == 'global-on'
        require(row['inputs']['control']['enabled'] is enabled, 'Unexpected global arm')
        require(row['expected']['global_enabled'] is enabled, 'Expected global arm mismatch')
        require(row['expected']['expected_scale'] == (.75 if enabled else 1.0), 'Expected scale mismatch')
        require(row['expected']['expected_effective_options_sha256'] == OPTIONS_SHA256
                and len(row['expected']['expected_effective_options']) == 368
                and matrix.canonical_options_hash(row['expected']['expected_effective_options']) == OPTIONS_SHA256,
                'Full predicted options mismatch')
        require(row['expected']['archive_sha256'] == live.ARCHIVE_SHA256
                and row['inputs']['shader_sha256'] == live.ARCHIVE_SHA256, 'Expected source archive mismatch')
        require(row['inputs']['source_world_sha256'] == WORLD_SHA256, 'Prepared source-world mismatch')
        require(row['inputs']['requested_shader_options'] == row['expected']['requested_options'], 'Sidecar/request mismatch')
        require(row['command'] == command(row['profile']), 'Launch flags differ from frozen diagnostic recipe')
        signatures.append(prepared_signature(row['inputs']['control'], row['prepared_writable_settings']))
    require(signatures[0] == signatures[1], 'Scene/control/config copies differ beyond authorized global')
    require(rows[0]['expected']['requested_options'] == rows[1]['expected']['requested_options'], 'Sidecar pair differs')
    return dict(status='PREPARED_MATCHED_SCENE_AND_CONTROLS_ONLY',
        signature=signatures[0], signature_sha256=matrix.canonical_options_hash(signatures[0]),
        actual_runtime_comparison='HOLD: actual scene/control/transformed-source/raw/restoration receipts required',
        temporal_synchronization='unproved; shader temporal derivatives may differ between captures',
        performanceEvidence=False, quality='unaccepted')


def source_files():
    names = ('scripts/prepare_photon_pixel_coverage.py', 'tests/test_photon_pixel_coverage_runtime.py',
        'docs/PHOTON_PIXEL_COVERAGE_RUNTIME.md', 'build.gradle', 'dependencies/runtime.lock.json', 'dependencies/performance-mods.lock.json',
        'scripts/prepare_photon_live_pixel_budget.py', 'scripts/build_photon_live_pixel_budget.py',
        'scripts/prepare_photon_taau_screen.py', 'scripts/run_proof_matrix.py', 'scripts/qualify_volume_fast_artifacts.py',
        'scripts/prepare_parallel_lod_profiles.py', 'scripts/prepare_reduced_effects_profiles.py', 'scripts/setup_comparison.py',
        'scripts/prepare_photon_ao_reuse_profiles.py', 'scripts/prepare_isolations.py')
    paths = [ROOT / n for n in names]
    for tree in ('src/main', 'src/devClient', 'src/devClientTest'):
        paths += [p for p in (ROOT / tree).rglob('*') if p.is_file()]
    return {str(p): file_hash(p) for p in sorted(set(paths))}


def prepare(receipt_path, receipt_sha256, output=OUTPUT, build=BUILD):
    operation = plan(output, build); matrix.require_exclusive_runtime()
    qualified = live.qualify_archive(); scene = canonical.qualify_source(source=canonical.SOURCE, root=ROOT, audit=canonical.AUDIT)
    require(len(qualified['complete_member_sha256']) == 533
            and matrix.canonical_options_hash(qualified['complete_member_sha256']) == MEMBERS_SHA256, 'Probe complete member pin drift')
    receipt, directories = qualify_receipt(receipt_path, receipt_sha256)
    frozen = source_files(); compiled = matrix.development_fingerprints()
    output, build = Path(operation['output']), Path(operation['build']); build.mkdir(parents=True)
    requested, candidate, inventory = qualify_options(build, scene)
    with unlocked_world(scene['world']):
        fingerprint = world_fingerprint(scene['world']); require(fingerprint == WORLD_SHA256, 'Canonical source save drift')
        output.mkdir(parents=True)
        state = EVIDENCE | dict(schema=1, status='PREPARING_SOURCE_CONTROLS', operation=operation,
            source_qualification=qualified, canonical_source_qualification=scene['qualification'], source_world_sha256=fingerprint,
            independent_artifact_receipt=receipt, auxiliary_directory_identities=directories,
            development_runtime_inputs=compiled, source_files_sha256=frozen, launch_plan=[],
            complete_option_inventory=dict(path=str(build / 'option-inventory.json'), sha256=file_hash(build / 'option-inventory.json'),
                delta=inventory['delta']), launch_authorized=False, owned_live_processes=[], stop_procedure=canonical.STOP,
            readback_scope='One full native F32LE current Iris RenderTargets depth plane at actual world0/deferred4 quad HEAD; source/program/marker/linked core/global/restoration receipts. No invocation or speed claim.',
            denominator='Private ON/global OFF uses fixed TAAU at 1.0; it is not original/default Photon. Stock .75 and original maps retained separately.')
        marker = output / 'pixel-coverage-preparation.json'; write_json(marker, state)
        try:
            for arm in ARMS:
                enabled = arm == 'global-on'; base = output / arm; base.mkdir()
                control = scene['control'] | {'enabled': enabled}; write_json(base / 'control.json', control)
                expected = dict(requested_options=requested, expected_effective_options=candidate['options'],
                    expected_effective_options_sha256=candidate['sha256'], archive=live.ARCHIVE.name,
                    archive_sha256=live.ARCHIVE_SHA256, global_enabled=enabled, expected_scale=.75 if enabled else 1.0,
                    requested_main_mod_present=True, main_mod_presence_verified=False, dh_present=False)
                write_json(base / 'workload-manifest.json', EVIDENCE | dict(schema=1, scene='coastalwater', control=control,
                    source_world_sha256=fingerprint, configuration_only=False, experiment_arms={'control': expected}, source_qualification=qualified))
                profile = base / 'control'
                storage_copytree(scene['source'], profile, ignore=shutil.ignore_patterns(*canonical.TRANSIENT))
                (profile / 'saves').mkdir(exist_ok=True)
                storage_copytree(scene['world'], profile / 'saves' / WORLD, ignore=shutil.ignore_patterns('session.lock'))
                require(world_fingerprint(profile / 'saves' / WORLD) == fingerprint, 'Copied source save drift')
                require(not any((profile / 'mods').glob('*.jar')), 'Minimal/noDH profile has extra mods')
                packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True); archive = packs / live.ARCHIVE.name
                shutil.copy2(live.ARCHIVE, archive)
                archive.with_name(archive.name + '.txt').write_text(''.join(f'{k}={v}\n' for k, v in sorted(requested.items())))
                iris = matrix.read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive.name
                (profile / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k, v in sorted(iris.items())))
                (profile / 'config/shaderlod-client.toml').write_text(fresh_config('adaptive' if enabled else 'high'))
                (profile / 'config/shaderlod-effects-client.toml').write_text(effects_config('inherit'))
                write_json(profile / 'workload-request.json', EVIDENCE | dict(run_id=output.name + '-' + arm,
                    case='water-' + arm, task='runClientLodOn', stack='minimal', include_dh=False,
                    measurement_seconds=8, initial_warmup_seconds=60, gpu_queries=False, cpu_timing=False,
                    pixel_coverage=True, pixel_coverage_scene_sha256=fingerprint, fixed_wetness=0))
                state['launch_plan'].append(dict(arm=arm, profile=str(profile), command=command(profile), owner='root',
                    status='not-launched', expected=expected, inputs=matrix.profile_inputs(profile),
                    prepared_writable_settings=canonical.writable_settings(profile),
                    input_files_sha256={str(p): file_hash(p) for p in sorted(profile.rglob('*'))
                        if p.is_file() and not p.is_relative_to(profile / 'saves')}))
            state['prepared_pair'] = validate_pair(state['launch_plan'])
            require(source_files() == frozen, 'Maintained source changed during preparation')
            require(matrix.development_fingerprints() == compiled, 'Compiled runtime changed during preparation')
            require(qualify_receipt(receipt_path, receipt_sha256) == (receipt, directories), 'Independent artifacts changed during preparation')
            require(world_fingerprint(scene['world']) == fingerprint, 'Canonical source save changed during preparation')
            state['status'] = 'SOURCE_READY_ROOT_DEPTH_CAPTURE_PENDING_TIMING_HOLD'; write_json(marker, state)
        except BaseException as error:
            state.update(status='FAILED_PREPARATION', error=str(error)); write_json(marker, state); raise
    return state


def verify(state):
    require(state['status'] == 'SOURCE_READY_ROOT_DEPTH_CAPTURE_PENDING_TIMING_HOLD', 'Preparation not qualified')
    require(live.qualify_archive() == state['source_qualification'], 'Archive/source freeze drift')
    require(source_files() == state['source_files_sha256'], 'Maintained source drift')
    require(matrix.development_fingerprints() == state['development_runtime_inputs'], 'Compiled runtime drift')
    entry = state['independent_artifact_receipt']; receipt, directories = qualify_receipt(entry['path'], entry['sha256'])
    require(receipt == entry and directories == state['auxiliary_directory_identities'], 'Independent receipt/directory drift')
    inventory = state['complete_option_inventory']; require(file_hash(Path(inventory['path'])) == inventory['sha256'], 'Option inventory drift')
    for row in state['launch_plan']:
        profile = Path(row['profile'])
        require(matrix.profile_inputs(profile) == row['inputs'], 'Prepared recipe drift')
        require(all(file_hash(Path(p)) == pin for p, pin in row['input_files_sha256'].items()), 'Prepared file drift')
        require(canonical.writable_settings(profile) == row['prepared_writable_settings'], 'Prepared complete settings drift')
        require(world_fingerprint(profile / 'saves' / WORLD) == state['source_world_sha256'], 'Copied source save drift')
        require(command(profile) == row['command'], 'Prepared command drift')
    require(validate_pair(state['launch_plan']) == state['prepared_pair'], 'Prepared pair comparison drift')
    return EVIDENCE | dict(status='verified-source-ready', launch_authorized=False, owned_live_processes=[])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-receipt', type=Path); parser.add_argument('--artifact-receipt-sha256')
    parser.add_argument('--output', type=Path, default=OUTPUT); parser.add_argument('--build', type=Path, default=BUILD)
    parser.add_argument('--dry-run', action='store_true'); parser.add_argument('--verify', type=Path)
    args = parser.parse_args()
    try:
        if args.verify: result = verify(json.loads(args.verify.read_text()))
        elif args.dry_run: result = plan(args.output, args.build)
        else:
            require(args.artifact_receipt is not None and args.artifact_receipt_sha256 is not None,
                'Fresh independent integrated artifact receipt path/hash are essential; no historical default')
            result = prepare(args.artifact_receipt, args.artifact_receipt_sha256, args.output, args.build)
        print(json.dumps(result, indent=2, sort_keys=True))
    except (OSError, ValueError, KeyError, subprocess.SubprocessError, zipfile.BadZipFile) as failure:
        parser.exit(1, 'Cannot prepare pixel-coverage controls: ' + str(failure) + '\n')


if __name__ == '__main__': main()
