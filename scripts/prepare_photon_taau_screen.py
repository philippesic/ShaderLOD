#!/usr/bin/env python3
"""Prepare original Photon native/TAAU quality profiles; never build or launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import tomllib
import zipfile

from prepare_isolations import ORIGINAL_SHA256, archive_contents, filter_effective_options
from prepare_photon_ao_reuse_profiles import TRANSIENT
from prepare_reduced_effects_profiles import control_for_scene, fresh_config, require, write_json
from run_proof_matrix import (archive_defaults, canonical_options_hash, development_fingerprints,
    normalize_requested_options, profile_inputs, read_option_properties, require_exclusive_runtime)
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

SOURCE = ROOT / 'run-workloads/photon-ssr-cost-controls-r1/coastalwater/high'
SOURCE_REPORT_SHA256 = '9524f3fdc9dec30f59a03704ad45f9cffa84b2058fe2ed48ea6a3095c31ba252'
SOURCE_OPTIONS_SHA256 = 'ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669'
AUDIT = ROOT / 'build/render-scale-audit-source/native-options-screen.json'
EXPERIMENT = 'photon-original-native-taau-pixel-budget-calibration'
ARMS = ('native', 'taau90')
KINDS = ('still', 'motion')
OFF = ('gpuPassTiming', 'cpuLayerTiming', 'gpuAttribution', 'cloudSamplerProbe',
       'workTrace', 'workTraceOnly', 'spatialLayoutProbe', 'spatialLayoutOnly', 'dhDrawEvidence',
       'waterSceneSurvey', 'shadowObserve', 'visibilityLists', 'shadowCoherenceOracle',
       'smokeTest', 'gpuBenchmark', 'benchmarkOnly')
BUILD_TASKS = ('compileJava', 'compileDevClientJava', 'processResources', 'processDevClientResources')
EVIDENCE = dict(performanceEvidence=False, adapter_gain=False, shader_support_claim=False,
    evidence_class='upstream-option-calibration-preparation', quality='unverified', runtime='unverified')
STOP = ('Root reserves client/GPU and records launcher session and Java start identity/profile before launch; '
        'SIGTERM only the proven owned Java PID and launcher session, wait 20 s, re-prove identity before '
        'SIGKILL if required, verify both absent. Do not signal shared Gradle daemons or kill by name.')


def option_maps(effective):
    require(canonical_options_hash(effective) == SOURCE_OPTIONS_SHA256, 'Canonical effective map differs')
    require(effective.get('TAA') == 'true' and effective.get('TAAU') == 'false'
            and effective.get('TAAU_RENDER_SCALE') == '0.75', 'Canonical temporal options differ')
    require(not any(k.startswith(('SHADERLOD_', 'PHOTON_')) for k in effective), 'Private option contamination')
    return {'native': dict(effective), 'taau90': effective | {'TAAU': 'true', 'TAAU_RENDER_SCALE': '0.90'},
            'taau85': effective | {'TAAU': 'true', 'TAAU_RENDER_SCALE': '0.85'}}


def plan(output, source=SOURCE, root=ROOT):
    root = Path(root).resolve(); raw = Path(output)
    require(not raw.is_symlink() and not Path(source).is_symlink(), 'Symlink source/output unsupported')
    output, source = raw.resolve(), Path(source).resolve()
    require(any(output.is_relative_to(root / n) and output != root / n for n in ('build', 'run-workloads')), 'Output must be ignored')
    require(not output.exists(), 'Preserving existing output; use a fresh directory')
    require(output != source and output not in source.parents and source not in output.parents
            and source.parent not in output.parents, 'Output must be separate from source')
    return EVIDENCE | dict(schema=1, output=str(output), source=str(source), original_sha256=ORIGINAL_SHA256,
        arms=list(ARMS), kinds=list(KINDS), deferred_arm='taau85 only after root accepts 0.90 still/motion quality',
        expected_framebuffer=[2976, 1672], expected_window=[1488, 836], requested_chunks=24,
        main_mod_present=False, dh_present=False, performance_stack='minimal', client_gpu_owner='root',
        validation='Path plan only; save/source qualification pending')


def semantic_properties(text, separator='='):
    """Narrow config syntax; do not silently discard unknown/duplicate/continued lines."""
    result = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith(('#', '!')): continue
        require(separator in line and not line.rstrip().endswith('\\'), 'Unsupported property syntax')
        key, value = line.split(separator, 1); key, value = key.strip(), value.strip()
        require(key and key not in result, 'Duplicate/empty property key')
        # Preserve escaped literal bytes, rather than claiming Java escape equivalence.
        result[key] = value
    return result


def writable_settings(profile):
    """Complete semantic config inventory; explicit Sodium cache identity is retained separately."""
    profile = Path(profile)
    settings, identities = {}, {}
    paths = [profile / 'options.txt'] + sorted(p for p in (profile / 'config').rglob('*') if p.is_file())
    require(not any(p.is_symlink() for p in paths), 'Symlink writable configuration')
    for path in paths:
        name = path.relative_to(profile).as_posix(); text = path.read_text()
        if name == 'config/sodium-fingerprint.json':
            value = json.loads(text)
            require(set(value) == {'v', 's', 'u', 'p', 't'} and value['v'] == 1
                    and isinstance(value['t'], int) and not isinstance(value['t'], bool)
                    and all(isinstance(value[k], str) and re.fullmatch('[0-9a-f]{128}', value[k]) for k in ('s', 'u', 'p')),
                    'Unknown Sodium fingerprint identity schema')
            identities[name] = dict(role='Sodium hardware/user/process cache identity, not a shader setting',
                                   raw=value, sha256=file_hash(path))
        elif path.suffix == '.toml': settings[name] = dict(format='toml', value=tomllib.loads(text))
        elif path.suffix == '.json': settings[name] = dict(format='json', value=json.loads(text))
        elif name == 'options.txt': settings[name] = dict(format='minecraft colon properties', value=semantic_properties(text, ':'))
        elif path.suffix == '.properties': settings[name] = dict(format='simple literal properties', value=semantic_properties(text))
        else: settings[name] = dict(format='unrecognized exact bytes', sha256=file_hash(path))
    return dict(settings=settings, settings_sha256=canonical_options_hash(settings), nonshader_identities=identities,
        policy='Pin prepared receipt; before capture compare complete startup-normalized settings across arms and end boundary. '
               'No generic ignored files/keys. Preserve raw cache identity receipts separately; any other semantic change rejects.')


def qualify_source(source=SOURCE, root=ROOT, audit=AUDIT):
    source, root = Path(source).resolve(), Path(root).resolve()
    require(not any(p.is_symlink() for p in source.rglob('*')), 'Source contains symlinks')
    report_path = source / 'workload-measurement.json'
    require(file_hash(report_path) == SOURCE_REPORT_SHA256, 'Canonical source report pin differs')
    report = json.loads(report_path.read_text())
    require(report.get('status') == 'complete' and report.get('actual_shader_sha256') == ORIGINAL_SHA256
            and report.get('dhPresent') is False and report.get('modPresent') is False, 'Source status/archive/mod presence differs')
    effective = report['effective_shader_options']; maps = option_maps(effective)
    require(report.get('effective_shader_options_sha256') == SOURCE_OPTIONS_SHA256, 'Source map receipt differs')
    for boundary in ('input_state_start', 'input_state_end'):
        state = report[boundary]
        require([state.get('framebuffer_width'), state.get('framebuffer_height')] == [2976, 1672]
                and state.get('requested_chunks') == state.get('effective_chunks') == 24
                and state.get('shaders_in_use') is True and state.get('fallback') is False, 'Source viewport/radius/pipeline differs')
    runtime_path = root / 'dependencies/runtime.lock.json'; runtime = json.loads(runtime_path.read_text())
    iris = read_option_properties((source / 'config/iris.properties').read_text()); selected = iris['shaderPack']
    require(Path(selected).name == selected and iris.get('enableShaders') == 'true', 'Source Iris selection invalid')
    archive_path = source / 'shaderpacks' / selected
    original = archive_contents(archive_path, ORIGINAL_SHA256, runtime['artifacts']['photon'])
    require(file_hash(archive_path, 'sha512') == runtime['artifacts']['photon']['hashes']['sha512'], 'Original SHA512 differs')
    canonical = root / 'dependencies/upstream/photon_v1.3b.zip'
    require(file_hash(canonical) == ORIGINAL_SHA256, 'Canonical upstream archive differs')
    with zipfile.ZipFile(archive_path) as archive:
        defaults = archive_defaults(archive)
        members = {n: __import__('hashlib').sha256(archive.read(n)).hexdigest() for n in archive.namelist() if not n.endswith('/')}
    require(not any(n.startswith('shaderlod-') for n in members), 'Archive must be unmodified original')
    sidecar = archive_path.with_name(selected + '.txt'); requested = read_option_properties(sidecar.read_text()) if sidecar.exists() else {}
    native, option_evidence = filter_effective_options(effective, requested, original, original)
    requests = {'native': native, 'taau90': native | {'TAAU': 'true', 'TAAU_RENDER_SCALE': '0.90'}}
    for arm, values in requests.items(): normalize_requested_options(defaults, values)
    audit = Path(audit); audit_data = json.loads(audit.read_text())
    require(audit_data['source_report_sha256'] == SOURCE_REPORT_SHA256
            and audit_data['source_effective_options_sha256'] == SOURCE_OPTIONS_SHA256, 'Parser audit source pin differs')
    for arm, scale in (('taau90', '0.90'), ('taau85', '0.85')):
        audited = audit_data['candidate_effective_options'][scale]
        require(audited['options'] == maps[arm] and audited['sha256'] == canonical_options_hash(maps[arm]), 'Parser audit map prediction differs')
    manifest_path, control_path = source.parent / 'workload-manifest.json', source.parent / 'control.json'
    manifest = json.loads(manifest_path.read_text())
    require(manifest.get('configuration_only') is False and manifest.get('source_world_sha256'), 'Source world provenance absent')
    control, removed = control_for_scene(json.loads(control_path.read_text()), 'coastalwater')
    require(not any((source / 'mods').glob('*.jar')), 'Source must have no profile mods or DH')
    settings = writable_settings(source)
    require(settings['settings']['options.txt']['value']['renderDistance'] == '24', 'Source detailed radius differs')
    files = [report_path, manifest_path, control_path, archive_path, canonical, runtime_path, audit]
    files += [p for p in (source / 'config').rglob('*') if p.is_file()] + [source / 'options.txt']
    if sidecar.exists(): files.append(sidecar)
    for name in ('iris', 'sodium'):
        pin = runtime['artifacts'][name]; path = root / 'dependencies/runtime' / pin['filename']
        require(file_hash(path, 'sha512') == pin['hashes']['sha512'], 'Pinned runtime jar differs: ' + name); files.append(path)
    return dict(source=source, world=source / 'saves' / WORLD, archive=archive_path, control=control,
        qualification=dict(qualified=True, archive_sha256=ORIGINAL_SHA256,
            archive_sha512=file_hash(archive_path, 'sha512'), complete_archive_member_sha256=members,
            license_sha256=original['license_sha256'], attribution='Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained',
            modified_upstream_members=[], upstream_option_calibration=True, runtime_lock=runtime,
            runtime_lock_sha256=file_hash(runtime_path), effective_option_maps=maps,
            effective_option_hashes={k: canonical_options_hash(v) for k, v in maps.items()},
            dynamic_predicted_option_counts={k: len(v) for k, v in maps.items()},
            option_evidence=option_evidence, removed_control_overrides=removed, source_provenance=manifest,
            source_files_sha256={str(p): file_hash(p) for p in sorted(set(files))}),
        requested_options=requests, source_writable_settings=settings)


def command(profile, kind, camera_request=None):
    require(kind in KINDS, 'Unknown quality kind')
    require(kind != 'motion' or camera_request is not None, 'Motion request missing')
    argv = ['./gradlew', 'runClientLodOff', '-PrunDirectory=' + str(Path(profile).resolve()),
            '-PperformanceStack=minimal', '-PincludeDh=false', '-PbenchmarkWindowScale=2',
            '-PcomparisonFullscreen=false', '-PfixedWetness=0', '-PgpuTiming=false', '-PgpuSampleInterval=5',
            '-PinitialWarmup=30', '-PmeasurementSeconds=5', '-PcpuTiming=' + str(kind == 'still').lower(),
            '-PmeasureWorkload=' + str(kind == 'still').lower(), '-PmeasureOnly=' + str(kind == 'still').lower(),
            '-PcameraQualityOnly=' + str(kind == 'motion').lower(),
            '-PcameraQuality=' + (str(Path(camera_request).resolve()) if kind == 'motion' else '')]
    argv += ['-P' + name + '=false' for name in OFF]
    for task in BUILD_TASKS: argv += ['-x', task]
    return argv


def motion_request():
    pose = dict(x=500.5, y=68.0, z=660.5, yaw=87.38, pitch=3.735)
    return dict(schema=1, label='photon-taau-coast-motion-r1', warmupSeconds=30, fps=10,
        pngCompressionLevel=0, maximumInFlightImages=2, expectedCameraFluid='NONE', rejectFullBlockCamera=True,
        waypoints=[pose | {'seconds': 0}, pose | {'seconds': 3},
                   pose | dict(seconds=8, x=504.5, yaw=72.38),
                   pose | dict(seconds=13, x=504.5, yaw=102.38),
                   pose | {'seconds': 18}, pose | {'seconds': 20}])


def prepare(output, source=SOURCE, root=ROOT, audit=AUDIT):
    operation = plan(output, source, root); require_exclusive_runtime(); scene = qualify_source(source, root, audit)
    output = Path(operation['output']); root = Path(root).resolve()
    with unlocked_world(scene['world']):
        fingerprint = world_fingerprint(scene['world'])
        output.mkdir(parents=True, exist_ok=False)
        state = EVIDENCE | dict(schema=1, status='preparing', operation=operation,
            source_qualification=scene['qualification'], source_world_sha256=fingerprint,
            created_at_utc=datetime.now(timezone.utc).isoformat(), launch_plan=[], stop_procedure=STOP,
            acceptance='Untimed driver/options/native extent, settled still then motion quality; no speed acceptance',
            runtime_guard_gaps=['Independent r2 artifact receipt and exact loaded inventory comparison pending root integration',
                'Full startup-normalized writable settings receipt must be captured before quality/timing acceptance',
                'Loaded complete Iris map must equal predicted per-arm map; source map is not runtime consumption',
                'Linked c3/c4 and active raster/native-history allocation not established by source parser',
                'Focus/window/pacing must be stably sampled and match baseline; motion harness lacks full window capture ledger',
                'Shader temporal clocks advance normally; time/wetness fixed but exact temporal phase alignment remains unproved',
                'Native GUI extent needs separate observation: quality camera hides GUI; Workload screenshot does not prove GUI sharpness'])
        marker = output / 'taau-screen-preparation.json'; write_json(marker, state)
        try:
            camera_path = output / 'camera-request-motion.json'; write_json(camera_path, motion_request())
            provenance = output / 'source-provenance'; provenance.mkdir()
            for src, name in ((scene['source'] / 'workload-measurement.json', 'workload-original.json'),
                              (scene['source'].parent / 'control.json', 'control-original.json'),
                              (scene['source'].parent / 'workload-manifest.json', 'manifest-original.json')):
                shutil.copy2(src, provenance / name)
            write_json(provenance / 'writable-settings-original.json', scene['source_writable_settings'])
            for kind in KINDS:
                base = output / kind; base.mkdir(); write_json(base / 'control.json', scene['control'])
                arms = {}
                for arm in ARMS:
                    profile = base / arm
                    storage_copytree(scene['source'], profile, ignore=shutil.ignore_patterns(*TRANSIENT))
                    world = profile / 'saves' / WORLD; world.parent.mkdir(exist_ok=True)
                    storage_copytree(scene['world'], world, ignore=shutil.ignore_patterns('session.lock'))
                    require(world_fingerprint(world) == fingerprint, 'Copied world differs')
                    (profile / 'shaderpacks').mkdir(exist_ok=True)
                    archive = profile / 'shaderpacks' / scene['archive'].name; shutil.copy2(scene['archive'], archive)
                    values = scene['requested_options'][arm]
                    archive.with_name(archive.name + '.txt').write_text(''.join(f'{k}={v}\n' for k, v in sorted(values.items())))
                    iris_path = profile / 'config/iris.properties'; iris = read_option_properties(iris_path.read_text()); iris['shaderPack'] = archive.name
                    iris_path.write_text(''.join(f'{k}={v}\n' for k, v in sorted(iris.items())))
                    (profile / 'config/shaderlod-client.toml').write_text(fresh_config('high'))
                    require(file_hash(archive) == ORIGINAL_SHA256 and not any((profile / 'mods').glob('*.jar')), 'Copied original/runtime scope differs')
                    writable = writable_settings(profile)
                    arms[arm] = dict(archive=archive.name, sha256=ORIGINAL_SHA256, requested_options=values,
                        expected_effective_options=scene['qualification']['effective_option_maps'][arm],
                        expected_effective_options_sha256=scene['qualification']['effective_option_hashes'][arm],
                        predicted_active_count=len(scene['qualification']['effective_option_maps'][arm]),
                        main_mod_present=False, global_enabled=False, dh_present=False, task='runClientLodOff',
                        prepared_writable_settings=writable, copied_world_sha256=fingerprint)
                    request = dict(run_id=f'taau-screen-r1-{kind}-{arm}', case='photon-native' if arm == 'native' else 'photon-taau90',
                        task='runClientLodOff', stack='minimal', include_dh=False, measurement_seconds=5,
                        gpu_queries=False, fixed_wetness=0, performanceEvidence=False, evidence_class='untimed-quality-screen')
                    if kind == 'still': write_json(profile / 'workload-request.json', request)
                    state['launch_plan'].append(dict(kind=kind, arm=arm, profile=str(profile), command=command(profile, kind, camera_path),
                        request=request if kind == 'still' else str(camera_path), status='not-launched', owner='root'))
                write_json(base / 'workload-manifest.json', dict(schema=1, scene='coastalwater', configuration_only=False,
                    preparation_status='complete', source_world=str(scene['world']), source_world_sha256=fingerprint,
                    original_sha256=ORIGINAL_SHA256, experiment_family=EXPERIMENT, experiment_arms=arms,
                    requested_chunks=24, expected_framebuffer=[2976, 1672], control=scene['control'], dh_installed=False,
                    main_mod_present=False, upstream_option_calibration=True, validation='Preparation only; no runtime/speed/quality acceptance'))
                for arm in ARMS:
                    profile = base / arm; inputs = profile_inputs(profile)
                    state.setdefault('profile_input_receipts', {})[str(profile)] = inputs
            require(world_fingerprint(scene['world']) == fingerprint, 'Source save changed during preparation')
            require(all(file_hash(Path(p)) == digest for p, digest in scene['qualification']['source_files_sha256'].items()), 'Source inputs changed during preparation')
            state['development_runtime_inputs'] = development_fingerprints()
            source_paths = [root / p for p in ('build.gradle', 'settings.gradle', 'gradlew', 'gradle/wrapper/gradle-wrapper.jar',
                'gradle/wrapper/gradle-wrapper.properties', 'dependencies/runtime.lock.json', 'dependencies/performance-mods.lock.json',
                'scripts/prepare_photon_taau_screen.py', 'scripts/run_proof_matrix.py', 'scripts/prepare_isolations.py',
                'scripts/setup_comparison.py', 'scripts/prepare_reduced_effects_profiles.py', 'scripts/prepare_photon_ao_reuse_profiles.py')]
            for tree in ('src/main', 'src/devClient'): source_paths += [p for p in (root / tree).rglob('*') if p.is_file()]
            state['immutable_source_files_sha256'] = {str(p): file_hash(p) for p in sorted(set(source_paths))}
            state.update(status='complete', launch_authorized=False, owned_live_processes=[])
            write_json(marker, state)
        except BaseException as error:
            state.update(status='failed', error=str(error)); write_json(marker, state); raise
    return state


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True); parser.add_argument('--source', type=Path, default=SOURCE)
    parser.add_argument('--audit', type=Path, default=AUDIT); parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    try:
        result = plan(args.output, args.source) if args.dry_run else prepare(args.output, args.source, audit=args.audit)
        print(json.dumps(result, indent=2, sort_keys=True))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, 'Cannot prepare Photon TAAU screen: ' + str(error) + '\n')


if __name__ == '__main__': main()
