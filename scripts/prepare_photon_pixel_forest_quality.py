#!/usr/bin/env python3
"""Freeze unused native/.75/.70 original-Photon forest still controls; never launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tomllib
import zipfile

import prepare_photon_pixel_baseline as baseline
import prepare_photon_cloud_tail_diagnostic as gui
import prepare_reduced_effects_profiles as copies
from prepare_parallel_lod_profiles import EFFECTS, SOURCE_PINS
from prepare_isolations import archive_contents, filter_effective_options
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

canonical, parser, matrix = baseline.canonical, baseline.live, baseline.matrix
OUTPUT = ROOT / 'run-workloads/pixel-forest-quality-r1'
SOURCE = copies.DEFAULT_SOURCES['forest']
WORLD_SHA256 = 'f752339d854ff2f0e0a7dc4e577dd6aedf2d0f69e39a9a7cae9dd0286c7be08b'
HISTORICAL_WORLD_SHA256 = 'fb0bbac2a38d9c3213a85d2987f58306f9d120cb221b53b1cb30bfdce7ec19a3'
LEVEL_DAT_SHA256 = 'e3cefcdbd5dcff1f2d9dd7d9b771ca595c4a13e58b2b8c62b07d9b5a948358bd'
RECEIPT = ROOT / 'build/water-temporal-integrated-artifacts-r7/artifact-receipt.json'
RECEIPT_SHA256 = '77adf1f9f810f4ac01b19396902edce288fa3e1ed1c2744a00b1a1b81979755d'
JAR = ROOT / 'build/libs/shaderlod-0.1.0-dev.jar'
JAR_SHA256 = '5f84d03813de4e6f7c70d2d2c95f85a404603975d6dae2b4dc8be775778c2b03'
ARMS = ('native', 'stock75', 'stock70')
MARKER = 'pixel-forest-quality-preparation.json'
FINAL = 'THREE_CURRENT_RUNTIME_UNUSED_FOREST_STILL_CONTROLS_READY_ROOT_REVIEW_PENDING'
OFF = tuple(dict.fromkeys(gui.OFF + ('gpuTiming', 'cpuTiming', 'cloudSamplerProbe',
    'pixelCoverage', 'pixelCoverageOnly', 'cameraQualityOnly')))
EVIDENCE = dict(diagnostic_only=True, performanceEvidence=False, adapter_gain=False,
    runtime='unverified', quality='unaccepted', motion='unverified', shader_support_claim=False,
    evidence_class='stock-pixel-budget-forest-gross-still-quality-preparation')


def command(profile):
    argv = ['./gradlew', 'runClientLodOn', '-PrunDirectory=' + str(Path(profile).resolve()),
        '-PperformanceStack=minimal', '-PincludeDh=false', '-PmeasureWorkload=true', '-PmeasureOnly=true',
        '-PinitialWarmup=60', '-PmeasurementSeconds=8', '-PbenchmarkWindowScale=2',
        '-PhideWorkloadGui=true', '-PcomparisonFullscreen=false', '-PfixedWetness=0',
        '-PgpuSampleInterval=5', '-PcameraQuality=']
    argv += ['-P' + flag + '=false' for flag in OFF]
    for task in canonical.BUILD_TASKS: argv += ['-x', task]
    return argv


def plan(output=OUTPUT, root=ROOT):
    root = Path(root).resolve()
    require = copies.require
    require(not Path(output).is_symlink(), 'Symlink output unsupported')
    output = Path(output).resolve()
    require(output == root / 'run-workloads/pixel-forest-quality-r1', 'Only owned fresh family allowed')
    require(not output.exists(), 'Preserve existing output; use --verify')
    return EVIDENCE | dict(schema=1, output=str(output), arms=list(ARMS), scene='forest',
        framebuffer=[2976,1672], window=[1488,836], requested_chunks=24,
        pose=copies.POSES['forest'], initial_warmup_seconds=60, capture_seconds=8,
        hide_workload_gui=True, main_mod_present=True, global_enabled=True, dh_present=False,
        gpu_queries=False, cpu_timing=False, sampler_probe=False, client_gpu_owner='root',
        launch_authorized=False, owned_live_processes=[], stop_procedure=canonical.STOP)


def maintained_sources():
    paths = [ROOT / 'scripts/prepare_photon_pixel_forest_quality.py',
        ROOT / 'tests/test_photon_pixel_forest_quality.py']
    for module in tuple(sys.modules.values()):
        name = getattr(module, '__file__', None)
        if name:
            path = Path(name).resolve()
            if path.suffix == '.py' and path.is_relative_to(ROOT / 'scripts'): paths.append(path)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def option_maps(native):
    copies.require(len(native) == 366 and matrix.canonical_options_hash(native) == SOURCE_PINS['forest'][1],
        'Canonical complete forest map differs')
    expected = {'TAA':'true','TAAU':'false','TAAU_RENDER_SCALE':'0.75','CAS':'true','CAS_INTENSITY':'0.50'}
    copies.require(all(native.get(k) == v for k,v in expected.items())
        and not any(k.startswith('SHADERLOD_') for k in native), 'Original temporal/sharpen/private settings differ')
    return {'native':dict(native), 'stock75':native | {'TAAU':'true'},
        'stock70':native | {'TAAU':'true','TAAU_RENDER_SCALE':'0.70'}}


def qualify_source():
    copies.require(not SOURCE.is_symlink() and not any(p.is_symlink() for p in SOURCE.rglob('*')),
        'Symlink forest source unsupported')
    report_path = SOURCE / 'workload-measurement.json'
    copies.require(file_hash(report_path) == SOURCE_PINS['forest'][0], 'Canonical forest report pin differs')
    report = json.loads(report_path.read_text())
    copies.require(report.get('status') == 'complete' and report.get('actual_shader_sha256') == canonical.ORIGINAL_SHA256,
        'Original forest report status/source differs')
    native = report['effective_shader_options']; maps = option_maps(native)
    copies.require(report['effective_shader_options_sha256'] == matrix.canonical_options_hash(native), 'Forest option receipt differs')
    for boundary in ('input_state_start', 'input_state_end'):
        state = report[boundary]
        copies.require(state.get('shaders_in_use') is True and state.get('fallback') is False
            and [state.get('framebuffer_width'),state.get('framebuffer_height')] == [2976,1672]
            and state.get('requested_chunks') == state.get('effective_chunks') == 24, 'Forest source runtime/radius differs')
    manifest_path, control_path = SOURCE.parent / 'workload-manifest.json', SOURCE.parent / 'control.json'
    manifest = json.loads(manifest_path.read_text())
    copies.require(manifest.get('configuration_only') is False and manifest.get('source_world_sha256') == HISTORICAL_WORLD_SHA256,
        'Canonical forest world provenance differs')
    control, removed = copies.control_for_scene(json.loads(control_path.read_text()), 'forest')
    runtime_path = ROOT / 'dependencies/runtime.lock.json'; runtime = json.loads(runtime_path.read_text())
    iris_path = SOURCE / 'config/iris.properties'; iris = matrix.read_option_properties(iris_path.read_text())
    selected = iris.get('shaderPack', '')
    copies.require(selected and Path(selected).name == selected and iris.get('enableShaders') == 'true', 'Forest Iris selection differs')
    archive = SOURCE / 'shaderpacks' / selected
    original = archive_contents(archive, canonical.ORIGINAL_SHA256, runtime['artifacts']['photon'])
    copies.require(file_hash(archive,'sha512') == runtime['artifacts']['photon']['hashes']['sha512'], 'Original archive lock SHA512 differs')
    copies.require(file_hash(ROOT / 'dependencies/upstream/photon_v1.3b.zip') == canonical.ORIGINAL_SHA256, 'Canonical original archive differs')
    sidecar = archive.with_name(archive.name + '.txt')
    requested = matrix.read_option_properties(sidecar.read_text()) if sidecar.exists() else {}
    native_request, evidence = filter_effective_options(native,requested,original,original)
    requests = {'native':native_request, 'stock75':native_request | {'TAAU':'true'},
        'stock70':native_request | {'TAAU':'true','TAAU_RENDER_SCALE':'0.70'}}
    with zipfile.ZipFile(archive) as pack:
        members = {n:hashlib.sha256(pack.read(n)).hexdigest() for n in pack.namelist() if not n.endswith('/')}
        defaults = matrix.archive_defaults(pack)
    copies.require(len(members) == 531, 'Original complete member count differs')
    for request in requests.values(): matrix.normalize_requested_options(defaults,request)
    jars = sorted((SOURCE / 'mods').glob('*.jar')); dh = runtime['artifacts']['distanthorizons']
    copies.require(len(jars) == 1 and jars[0].name == dh['filename']
        and file_hash(jars[0],'sha512') == dh['hashes']['sha512'], 'Canonical forest known DH artifact differs')
    options_path = SOURCE / 'options.txt'; options = canonical.semantic_properties(options_path.read_text(), ':')
    copies.require(options.get('renderDistance') == '24' and options.get('guiScale') == '2', 'Forest radius/GUI scale differs')
    level_dat = SOURCE / 'saves' / WORLD / 'level.dat'
    copies.require(file_hash(level_dat) == LEVEL_DAT_SHA256, 'Explicit current stopped level.dat pin differs')
    files = [report_path,manifest_path,control_path,iris_path,options_path,archive,runtime_path,level_dat,
        ROOT / 'dependencies/upstream/photon_v1.3b.zip',*jars]
    if sidecar.exists(): files.append(sidecar)
    files += [p for p in (SOURCE / 'config').rglob('*') if p.is_file()]
    for key in ('iris','sodium'):
        pin = runtime['artifacts'][key]; p = ROOT / 'dependencies/runtime' / pin['filename']
        copies.require(file_hash(p,'sha512') == pin['hashes']['sha512'], 'Runtime lock artifact differs: ' + key); files.append(p)
    source = dict(archive_sha256=canonical.ORIGINAL_SHA256, complete_member_sha256=members,
        complete_member_map_sha256=matrix.canonical_options_hash(members), license_sha256=original['license_sha256'],
        source_report_sha256=SOURCE_PINS['forest'][0], source_options_sha256=SOURCE_PINS['forest'][1],
        source_files_sha256={str(p):file_hash(p) for p in sorted(set(files))}, source_world_sha256=WORLD_SHA256,
        historical_source_manifest_world_sha256=HISTORICAL_WORLD_SHA256,
        stopped_level_dat=dict(path=str(level_dat),sha256=LEVEL_DAT_SHA256,day_time=7000,
            raining=False,thundering=False,player_pos=[256.5,95.0,768.5],player_rotation=[135.0,9.0],dimension='minecraft:overworld',
            semantics_provenance='Bounded stdlib gzip/NBT read of this exact pinned level.dat; historical report agrees. Not new runtime evidence.'),
        canonical_control=control, removed_legacy_control_overrides=removed, option_evidence=evidence,
        original_source_dh=dict(path=str(jars[0]),sha512=file_hash(jars[0],'sha512')),
        runtime_lock=runtime, unmodified_original=True, attribution='Photon v1.3b by SixthSurge; LICENSE preserved')
    return dict(profile=SOURCE,world=SOURCE / 'saves' / WORLD,archive=archive,control=control,
        source_qualification=source,requested_options=requests,effective_maps=maps)


def validate_manifest(manifest, native):
    copies.require(manifest.get('schema') == 1 and manifest.get('scene') == 'forest'
        and manifest.get('configuration_only') is False and manifest.get('source_world_sha256') == WORLD_SHA256,
        'Forest manifest schema/world differs')
    arm = manifest.get('arm'); copies.require(arm in ARMS, 'Unknown forest arm')
    model = manifest.get('experiment_arms',{}).get('control',{}); source = manifest.get('source_qualification',{})
    members = source.get('complete_member_sha256',{})
    copies.require(len(members) == 531 and matrix.canonical_options_hash(members) == source.get('complete_member_map_sha256')
        and source.get('archive_sha256') == model.get('archive_sha256') == canonical.ORIGINAL_SHA256, 'Original complete source receipt differs')
    expected = option_maps(native)[arm]
    copies.require(model.get('expected_effective_options') == expected
        and model.get('expected_effective_options_sha256') == matrix.canonical_options_hash(expected), 'Complete arm map differs')
    copies.require(manifest.get('control') == source.get('canonical_control',{}) | {'enabled':True}
        and model.get('requested_main_mod_present') is True and model.get('dh_present') is False, 'Forest control/mod/noDH scope differs')
    return dict(arm=arm,member_count=531,option_count=366)


def validate_effects(text):
    parsed = tomllib.loads(text)
    copies.require(set(parsed) == {'effects'} and set(parsed['effects']) == set(EFFECTS) and len(EFFECTS) == 7,
        'Complete seven-effect inventory differs')
    for name, value in parsed['effects'].items():
        copies.require(value['mode'] == 'INHERIT' and value['mediumWorkRatio'] == value['farWorkRatio'] == 1.0
            and value['preserveSalientLighting'] is True, 'Nonunity/noninherit effect: ' + name)
    return parsed


def validate_prepared_settings(rows):
    """Permit only the two declared temporal selectors; retain all other configs."""
    copies.require([r['arm'] for r in rows] == list(ARMS), 'Missing/reordered forest settings')
    reference = None
    for row in rows:
        writable = row['prepared_writable_inventory']
        normalized = dict(writable['sidecar']['requested'])
        for key in ('TAAU','TAAU_RENDER_SCALE'): normalized.pop(key,None)
        signature = dict(settings=writable['settings'],nonshader_identities=writable['nonshader_identities'],
            login_caches=writable['login_caches'],other_requested_shader_options=normalized,
            shader_defaults=writable['sidecar']['defaults'])
        if reference is None: reference = signature
        copies.require(signature == reference, 'Prepared controls differ beyond temporal selectors')
    return dict(complete_configuration_match=True, permitted_shader_options=['TAAU','TAAU_RENDER_SCALE'])


def prepare(output=OUTPUT):
    operation = plan(output); matrix.require_exclusive_runtime(); scene = qualify_source()
    external, directories = baseline.coverage.qualify_receipt(RECEIPT,RECEIPT_SHA256)
    copies.require(file_hash(JAR) == JAR_SHA256
        and external['receipt']['independent_evidence_files_sha256'].get(str(JAR)) == JAR_SHA256, 'R7 current production JAR differs')
    frozen = maintained_sources(); compiled = matrix.development_fingerprints()
    runtime_sources = baseline.control_prep.current_runtime_sources()
    output = Path(operation['output'])
    with unlocked_world(scene['world']):
        copies.require(world_fingerprint(scene['world']) == WORLD_SHA256, 'Canonical stopped forest save drift')
        output.mkdir(parents=True); checks = output / 'source-checks'; checks.mkdir()
        state = EVIDENCE | dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),
            operation=operation,source_qualification=scene['source_qualification'],source_world_sha256=WORLD_SHA256,
            maintained_sources_sha256=frozen,runtime_sources_sha256=runtime_sources,development_runtime_inputs=compiled,
            independent_artifact_receipt=external,auxiliary_directory_identities=directories,
            production_jar=dict(path=str(JAR),sha256=JAR_SHA256),launch_plan=[],launch_authorized=False,
            owned_live_processes=[],postanalysis_plan=postanalysis_plan())
        marker = output / MARKER; copies.write_json(marker,state)
        try:
            inventories = {arm:parser.discover(scene['archive'],scene['requested_options'][arm],checks,arm) for arm in ARMS}
            for arm in ARMS:
                copies.require(inventories[arm]['options'] == scene['effective_maps'][arm]
                    and inventories[arm]['option_count'] == 366, 'Actual full Iris forest discovery differs: ' + arm)
            inventory_path = checks / 'option-inventory.json'; copies.write_json(inventory_path,inventories)
            state['complete_option_inventory'] = dict(path=str(inventory_path),sha256=file_hash(inventory_path))
            for arm in ARMS:
                base = output / arm; base.mkdir(); profile = base / 'control'
                control = scene['control'] | {'enabled':True}; copies.write_json(base / 'control.json',control)
                expected = dict(archive=scene['archive'].name,archive_sha256=canonical.ORIGINAL_SHA256,
                    requested_options=scene['requested_options'][arm],expected_effective_options=inventories[arm]['options'],
                    expected_effective_options_sha256=inventories[arm]['sha256'],requested_main_mod_present=True,
                    main_mod_presence_verified=False,global_enabled=True,dh_present=False)
                manifest = EVIDENCE | dict(schema=1,scene='forest',arm=arm,configuration_only=False,
                    source_world_sha256=WORLD_SHA256,control=control,experiment_arms={'control':expected},
                    source_qualification=scene['source_qualification'])
                validate_manifest(manifest,scene['effective_maps']['native']); copies.write_json(base / 'workload-manifest.json',manifest)
                storage_copytree(scene['profile'],profile,ignore=shutil.ignore_patterns(*canonical.TRANSIENT))
                copied_dh = profile / 'mods' / Path(scene['source_qualification']['original_source_dh']['path']).name
                copies.require(file_hash(copied_dh,'sha512') == scene['source_qualification']['original_source_dh']['sha512'], 'Copied known DH differs')
                copied_dh.unlink(); copies.require(not any((profile / 'mods').glob('*.jar')), 'Extra profile mod/noDH scope differs')
                (profile / 'saves').mkdir(exist_ok=True)
                storage_copytree(scene['world'],profile / 'saves' / WORLD,ignore=shutil.ignore_patterns('session.lock'))
                packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True); archive = packs / scene['archive'].name
                shutil.copy2(scene['archive'],archive)
                archive.with_name(archive.name + '.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(expected['requested_options'].items())))
                iris = matrix.read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive.name
                (profile / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                (profile / 'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('adaptive'))
                effects = baseline.coverage.effects_config('inherit'); validate_effects(effects)
                (profile / 'config/shaderlod-effects-client.toml').write_text(effects)
                request = EVIDENCE | dict(run_id=output.name + '-' + arm,case='forest-' + arm,task='runClientLodOn',stack='minimal',
                    include_dh=False,initial_warmup_seconds=60,measurement_seconds=8,gpu_queries=False,cpu_timing=False,
                    cloud_sampler_probe=False,fixed_wetness=0,hide_workload_gui=True,sample_interval=5,
                    expected_framebuffer=[2976,1672],expected_window=[1488,836])
                copies.write_json(profile / 'workload-request.json',request); snapshot = baseline.profile_snapshot(profile)
                copies.require(snapshot['copied_world_sha256'] == WORLD_SHA256
                    and snapshot['inputs']['shader_sha256'] == canonical.ORIGINAL_SHA256, 'Copied source/world differs')
                state['launch_plan'].append(dict(arm=arm,profile=str(profile),command=command(profile),request=request,
                    expected=expected,inputs=snapshot['inputs'],prepared_profile=snapshot,
                    prepared_writable_inventory=snapshot['prepared_writable_inventory'],owner='root',status='not-launched'))
            copies.require(world_fingerprint(scene['world']) == WORLD_SHA256 and maintained_sources() == frozen,
                'Canonical save/source changed during preparation')
            state['prepared_controls_comparison'] = validate_prepared_settings(state['launch_plan'])
            state['status'] = FINAL; verify(state); copies.write_json(marker,state)
        except BaseException as failure:
            state.update(status='FAILED_PREPARATION',error=str(failure)); copies.write_json(marker,state); raise
    return state


def verify(state):
    copies.require(state.get('status') == FINAL, 'Incomplete preparation')
    for field in ('maintained_sources_sha256','runtime_sources_sha256'):
        for path,pin in state[field].items(): copies.require(file_hash(Path(path)) == pin, 'Frozen source drift: ' + path)
    copies.require(maintained_sources() == state['maintained_sources_sha256']
        and baseline.control_prep.current_runtime_sources() == state['runtime_sources_sha256'], 'Complete source inventory drift')
    for path,pin in state['source_qualification']['source_files_sha256'].items(): copies.require(file_hash(Path(path)) == pin, 'Canonical forest source drift: ' + path)
    copies.require(matrix.development_fingerprints() == state['development_runtime_inputs'], 'Current compiled/resource drift')
    copies.require(world_fingerprint(SOURCE / 'saves' / WORLD) == WORLD_SHA256, 'Canonical stopped forest save drift')
    entry = state['independent_artifact_receipt']
    copies.require(entry['sha256'] == RECEIPT_SHA256 and baseline.coverage.qualify_receipt(entry['path'],entry['sha256']) ==
        (entry,state['auxiliary_directory_identities']), 'R7 independent artifact receipt drift')
    copies.require(state['production_jar'] == dict(path=str(JAR),sha256=JAR_SHA256) and file_hash(JAR) == JAR_SHA256, 'Main JAR drift')
    item = state['complete_option_inventory']; copies.require(file_hash(Path(item['path'])) == item['sha256'], 'Full Iris inventory drift')
    inventories = json.loads(Path(item['path']).read_text()); maps = option_maps(inventories['native']['options'])
    copies.require([r['arm'] for r in state['launch_plan']] == list(ARMS), 'Missing/reordered forest arm')
    for row in state['launch_plan']:
        arm, profile = row['arm'],Path(row['profile'])
        copies.require(profile == OUTPUT / arm / 'control', 'Owned arm/profile association drift')
        copies.require(inventories[arm]['options'] == maps[arm] and inventories[arm]['option_count'] == 366, 'Full actual forest map drift')
        for path,pin in inventories[arm]['parser_dependency_sha256'].items(): copies.require(file_hash(Path(path)) == pin, 'Pinned discovery dependency drift')
        copies.require(file_hash(Path(inventories[arm]['log'])) == inventories[arm]['log_sha256'], 'Discovery log drift')
        copies.require(row['command'] == command(profile) and baseline.profile_snapshot(profile) == row['prepared_profile'], 'Fresh source/save/configuration/argv drift')
        copies.require(not (profile / 'workload-measurement.json').exists(), 'Profile already used; retain historical capture')
        manifest = json.loads((profile.parent / 'workload-manifest.json').read_text())
        validate_manifest(manifest,maps['native'])
        copies.require(row['expected'] == manifest['experiment_arms']['control']
            and row['request'] == json.loads((profile / 'workload-request.json').read_text()), 'Manifest/request row drift')
        validate_effects((profile / 'config/shaderlod-effects-client.toml').read_text())
    copies.require(validate_prepared_settings(state['launch_plan']) == state['prepared_controls_comparison'], 'Complete prepared comparison drift')
    return EVIDENCE | dict(status='verified-three-current-runtime-unused-forest-controls',launch_authorized=False,owned_live_processes=[])


def postanalysis_plan():
    return dict(per_arm=['Actual full366 maps at start/end equal prepared map; exact original531member/archive/LICENSE pins and no fallback.',
        'Direct complete ten-mod/nine-root R7 inventory and four current directory roots qualify; noDH true absence, all7effects INHERIT/unity, complete raw captured settings semantics.',
        'Query-free:GPU/CPU streams empty; all probes and detailed/pass instrumentation off; hideWorkloadGui forwarded and actual hide_gui=true/gui_scale2 at both boundaries.',
        'Native2976x1672/logical1488x836 visible foreground stable focus/pacing, camera/entity pose forest256.5/95/768.5 yaw135 pitch9/day7000, clear/wetness0/chunks24; temporal suppliers observed.'],
        cross_arm=['Only TAAU and TAAU_RENDER_SCALE active-map differences; other complete captured controls and source/artifacts match.',
            'Fixed day/camera is not synchronized shader animation/jitter/history; gross still rejection only.'],
        review=['Native-pixel foliage/branches/shadow/ground texture crops; reject conspicuous merging, missing geometry, halos, borders or changed lighting cues before any timing.',
            '.75 is an intermediate visual anchor, not repeated coastal timing. No cost arms are prepared; root decides next test.'],
        limitations=['No shader performance, representative/motion/disocclusion, history-transition or DH acceptance.',
            'Standard validate_report unconditionally requires CPU timing; use complete component qualification for these query-free reports, never invent CPU rows.'])


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--dry-run',action='store_true'); p.add_argument('--verify',type=Path)
    args = p.parse_args()
    if args.verify:
        copies.require(args.verify.resolve() == OUTPUT / MARKER, 'Only owned marker allowed')
        matrix.require_exclusive_runtime(); result = verify(json.loads(args.verify.read_text()))
    elif args.dry_run: result = plan()
    else:
        state = prepare(); result = dict(status=state['status'],marker=str(OUTPUT / MARKER),
            marker_sha256=file_hash(OUTPUT / MARKER),arms=list(ARMS),launch_authorized=False,owned_live_processes=[])
    print(json.dumps(result,sort_keys=True,indent=2))


if __name__ == '__main__': main()
