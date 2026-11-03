#!/usr/bin/env python3
"""Prepare a fresh original/stock/live/stock/original Photon cost bracket; never launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
import copy
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import shutil
import sys
import tomllib
import zipfile

import prepare_photon_taau_cost_bracket as cost
import prepare_photon_pixel_coverage as coverage
import prepare_photon_live_pixel_control as control_prep
from prepare_reduced_effects_profiles import require, write_json
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

canonical, live, matrix = coverage.canonical, coverage.live, coverage.matrix
OUTPUT = ROOT / 'run-workloads/live-pixel-original-bracket-r1'
BUILD = ROOT / 'build/live-pixel-original-bracket-r1'
RECEIPT = ROOT / 'build/live-pixel-integrated-artifacts-r4/artifact-receipt.json'
RECEIPT_SHA256 = 'b2b0f2427af9f9ea517124e6faf26ab273e45566002fccaeac1cb1ed3c73d79e'
ORDER = ('native-before', 'stock75-before', 'live75', 'stock75-after', 'native-after')
MODES = ('native', 'stock', 'live', 'stock', 'native')
OFF = tuple(dict.fromkeys(canonical.OFF + ('pixelCoverage', 'pixelCoverageOnly', 'cameraQualityOnly')))
EVIDENCE = dict(diagnostic_only=True, performanceEvidence=False, runtime='unverified',
    quality='unaccepted', transition_accepted=False, adapter_gain=False,
    evidence_class='original-denominator-and-stock-adapter-overhead-cost-screen-preparation')
MARKER = 'pixel-original-baseline-preparation.json'


def command(profile):
    argv = ['./gradlew', 'runClientLodOn', '-PrunDirectory=' + str(Path(profile).resolve()),
        '-PperformanceStack=minimal', '-PincludeDh=false', '-PbenchmarkWindowScale=2',
        '-PcomparisonFullscreen=false', '-PfixedWetness=0', '-PgpuTiming=true',
        '-PgpuSampleInterval=5', '-PinitialWarmup=60', '-PmeasurementSeconds=20',
        '-PcpuTiming=true', '-PmeasureWorkload=true', '-PmeasureOnly=true', '-PcameraQuality=']
    argv += ['-P' + flag + '=false' for flag in OFF]
    for task in canonical.BUILD_TASKS: argv += ['-x', task]
    return argv


def plan(output=OUTPUT, build=BUILD, root=ROOT):
    root = Path(root).resolve()
    require(not Path(output).is_symlink() and not Path(build).is_symlink(), 'Symlink output unsupported')
    output, build = Path(output).resolve(), Path(build).resolve()
    require(output == root / 'run-workloads/live-pixel-original-bracket-r1' and
        build == root / 'build/live-pixel-original-bracket-r1', 'Only owned fresh bracket family allowed')
    require(not output.exists() and not build.exists(), 'Preserve previous outputs; use verify')
    return EVIDENCE | dict(schema=1, output=str(output), build=str(build), order=list(ORDER),
        modes=list(MODES), initial_warmup_seconds=60, measurement_seconds=20, gpu_sample_interval=5,
        minimum_gpu_samples_per_arm=60, coarse_cpu=True, requested_chunks=24,
        framebuffer=[2976, 1672], window=[1488, 836], main_mod_present=True, global_enabled=True,
        maximum_original_drift_fraction=.10, maximum_stock_drift_fraction=.10,
        minimum_each_original_frame_reduction_fraction=.05,
        client_gpu_owner='root', launch_authorized=False, stop_procedure=canonical.STOP,
        launch_status='ROOT ONLY: reserve client/GPU, announce quiet interval, activate proven Java PID during early warmup',
        denominator='Unmodified Photon TAA=true/TAAU=false native versus private live ON .75; stock TAAU .75 isolates adapter overhead')


def maintained_sources():
    paths = [ROOT / 'scripts/prepare_photon_pixel_baseline.py', ROOT / 'tests/test_photon_pixel_baseline.py']
    for module in tuple(sys.modules.values()):
        name = getattr(module, '__file__', None)
        if name:
            path = Path(name).resolve()
            if path.suffix == '.py' and path.is_relative_to(ROOT / 'scripts'): paths.append(path)
    return {str(path): file_hash(path) for path in sorted(set(paths))}


def validate_arm_manifest(manifest):
    """Actual per-arm Workload schema, without the legacy adaptive_pack_sha256 field."""
    require(manifest.get('schema') == 1 and manifest.get('scene') == 'coastalwater' and
        manifest.get('configuration_only') is False and manifest.get('source_world_sha256') == coverage.WORLD_SHA256,
        'Frozen scene/schema/save differs')
    source = manifest.get('source_qualification', {})
    model = manifest.get('experiment_arms', {}).get('control', {})
    mode = manifest.get('mode')
    require(mode in ('native', 'stock', 'live'), 'Unknown bracket mode')
    members = source.get('complete_member_sha256')
    require(isinstance(members, dict) and len(members) == (533 if mode == 'live' else 531)
        and all(isinstance(n, str) and isinstance(h, str) and len(h) == 64 for n, h in members.items()),
        'Complete source member inventory missing')
    expected_archive = live.ARCHIVE_SHA256 if mode == 'live' else canonical.ORIGINAL_SHA256
    require(source.get('archive_sha256') == model.get('archive_sha256') == expected_archive,
        'Frozen selected archive differs')
    require(matrix.canonical_options_hash(members) == source.get('complete_member_map_sha256'), 'Complete member hash differs')
    if mode == 'live': require(source['complete_member_map_sha256'] == coverage.MEMBERS_SHA256, 'Live complete member pin differs')
    options = model.get('expected_effective_options')
    require(isinstance(options, dict) and len(options) == (368 if mode == 'live' else 366)
        and matrix.canonical_options_hash(options) == model.get('expected_effective_options_sha256'),
        'Complete option map/hash differs')
    require(options.get('TAA') == 'true' and options.get('TAAU') == ('false' if mode == 'native' else 'true')
        and options.get('TAAU_RENDER_SCALE') == '0.75', 'Fixed original/stock/live temporal options differ')
    if mode == 'native': require(model['expected_effective_options_sha256'] == canonical.SOURCE_OPTIONS_SHA256, 'Native option pin differs')
    if mode == 'live': require(model['expected_effective_options_sha256'] == coverage.OPTIONS_SHA256, 'Live option pin differs')
    require(manifest.get('control', {}).get('enabled') is True and model.get('requested_main_mod_present') is True
        and model.get('dh_present') is False, 'Mod/global/noDH scope differs')
    return dict(mode=mode, archive_sha256=expected_archive, member_count=len(members), option_count=len(options))


def profile_snapshot(profile):
    profile = Path(profile)
    return dict(inputs=matrix.profile_inputs(profile), prepared_writable_inventory=cost.writable_inventory(profile),
        files_sha256={str(p): file_hash(p) for p in sorted(profile.rglob('*'))
            if p.is_file() and not p.is_relative_to(profile / 'saves')},
        parent_files_sha256={str(p): file_hash(p) for p in sorted(profile.parent.iterdir()) if p.is_file()},
        copied_world_sha256=world_fingerprint(profile / 'saves' / WORLD))


def prepare(output=OUTPUT, build=BUILD, receipt=RECEIPT, receipt_sha256=RECEIPT_SHA256):
    operation = plan(output, build); matrix.require_exclusive_runtime()
    scene = canonical.qualify_source(); source = live.qualify_archive()
    external, directories = coverage.qualify_receipt(receipt, receipt_sha256)
    require(external['sha256'] == RECEIPT_SHA256, 'Root-reviewed r4 receipt required')
    compiled = matrix.development_fingerprints(); runtime_sources = control_prep.current_runtime_sources()
    frozen = maintained_sources(); bootstrap = cost.bootstrap_source_evidence()
    equivalence = cost.audit_equivalence(ROOT / 'dependencies/runtime-artifact-equivalences.json', external['receipt'])
    output, build = Path(operation['output']), Path(operation['build'])
    with unlocked_world(scene['world']):
        fingerprint = world_fingerprint(scene['world'])
        require(fingerprint == coverage.WORLD_SHA256, 'Canonical stopped coastal world differs')
        output.mkdir(parents=True); build.mkdir(parents=True)
        marker = output / MARKER
        state = EVIDENCE | dict(schema=1, status='PREPARING', created_at_utc=datetime.now(timezone.utc).isoformat(),
            operation=operation, source_world_sha256=fingerprint, canonical_source_qualification=scene['qualification'],
            live_source_qualification=source, independent_artifact_receipt=external,
            auxiliary_directory_identities=directories, development_runtime_inputs=compiled,
            runtime_sources_sha256=runtime_sources, maintained_sources_sha256=frozen,
            bootstrap_source_evidence=bootstrap, api_base_equivalence=equivalence,
            launch_plan=[], launch_authorized=False, owned_live_processes=[], postanalysis_plan=postanalysis_plan())
        write_json(marker, state)
        try:
            requested_live, candidate, inventory = coverage.qualify_options(build, scene)
            require(inventory['original_native']['option_count'] == inventory['stock_taau75']['option_count'] == 366,
                'Complete original/stock inventory count differs')
            models = {'native': inventory['original_native'], 'stock': inventory['stock_taau75'], 'live': candidate}
            requests = {'native': scene['requested_options']['native'],
                'stock': scene['requested_options']['native'] | {'TAAU': 'true'}, 'live': requested_live}
            original_source = dict(archive_sha256=canonical.ORIGINAL_SHA256,
                complete_member_sha256=scene['qualification']['complete_archive_member_sha256'])
            sources = {'native': original_source, 'stock': original_source, 'live': source}
            for arm, mode in zip(ORDER, MODES):
                base = output / arm; base.mkdir()
                profile = base / 'control'
                control = scene['control'] | {'enabled': True}; write_json(base / 'control.json', control)
                archive_path = live.ARCHIVE if mode == 'live' else scene['archive']
                qualified = copy.deepcopy(sources[mode])
                qualified['complete_member_map_sha256'] = matrix.canonical_options_hash(qualified['complete_member_sha256'])
                expected = dict(archive=archive_path.name, archive_sha256=qualified['archive_sha256'],
                    requested_options=requests[mode], expected_effective_options=models[mode]['options'],
                    expected_effective_options_sha256=models[mode]['sha256'], global_enabled=True,
                    expected_scale=1.0 if mode == 'native' else .75,
                    requested_main_mod_present=True, main_mod_presence_verified=False, dh_present=False)
                manifest = EVIDENCE | dict(schema=1, scene='coastalwater', mode=mode, control=control,
                    configuration_only=False, source_world_sha256=fingerprint,
                    main_mod_present=True, experiment_arms={'control': expected}, source_qualification=qualified)
                validate_arm_manifest(manifest); write_json(base / 'workload-manifest.json', manifest)
                storage_copytree(scene['source'], profile, ignore=shutil.ignore_patterns(*canonical.TRANSIENT))
                (profile / 'saves').mkdir(exist_ok=True)
                storage_copytree(scene['world'], profile / 'saves' / WORLD, ignore=shutil.ignore_patterns('session.lock'))
                require(not any((profile / 'mods').glob('*.jar')), 'Minimal/noDH profile has extra jars')
                packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True)
                archive = packs / archive_path.name; shutil.copy2(archive_path, archive)
                archive.with_name(archive.name + '.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(requests[mode].items())))
                iris = matrix.read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive.name
                (profile / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                (profile / 'config/shaderlod-client.toml').write_text(coverage.fresh_config('adaptive'))
                (profile / 'config/shaderlod-effects-client.toml').write_text(coverage.effects_config('inherit'))
                request = EVIDENCE | dict(run_id=output.name + '-' + arm, case='photon-' + arm,
                    task='runClientLodOn', stack='minimal', include_dh=False, initial_warmup_seconds=60,
                    measurement_seconds=20, gpu_queries=True, sample_interval=5, fixed_wetness=0,
                    expected_framebuffer=[2976,1672], expected_window=[1488,836])
                write_json(profile / 'workload-request.json', request)
                snapshot = profile_snapshot(profile)
                require(snapshot['copied_world_sha256'] == fingerprint and snapshot['inputs']['shader_sha256'] == expected['archive_sha256'],
                    'Copied archive/save differs')
                state['launch_plan'].append(dict(arm=arm, mode=mode, profile=str(profile), command=command(profile),
                    request=request, expected=expected, inputs=snapshot['inputs'], prepared_profile=snapshot,
                    prepared_writable_inventory=snapshot['prepared_writable_inventory'], owner='root', status='not-launched'))
            state.update(status='SOURCE_AND_CURRENT_RUNTIME_READY_ROOT_REVIEW_PENDING',
                complete_option_inventory=dict(path=str(build / 'option-inventory.json'), sha256=file_hash(build / 'option-inventory.json')))
            require(world_fingerprint(scene['world']) == fingerprint, 'Canonical source save changed')
            require(maintained_sources() == frozen and control_prep.current_runtime_sources() == runtime_sources
                and matrix.development_fingerprints() == compiled, 'Sources/classes changed during preparation')
            require(coverage.qualify_receipt(receipt, receipt_sha256) == (external, directories), 'Independent artifacts changed')
            write_json(marker, state)
            verify(state)
        except BaseException as failure:
            state.update(status='FAILED_PREPARATION', error=str(failure)); write_json(marker,state); raise
    return state


def verify(state):
    require(state.get('status') == 'SOURCE_AND_CURRENT_RUNTIME_READY_ROOT_REVIEW_PENDING', 'Incomplete preparation')
    matrix.require_exclusive_runtime()
    require(state['operation']['order'] == list(ORDER), 'Bracket order drift')
    require(maintained_sources() == state['maintained_sources_sha256'], 'Recipe/source drift')
    require(control_prep.current_runtime_sources() == state['runtime_sources_sha256'], 'Runtime source drift')
    require(matrix.development_fingerprints() == state['development_runtime_inputs'], 'Compiled root drift')
    external = state['independent_artifact_receipt']
    require(coverage.qualify_receipt(external['path'], external['sha256']) == (external,state['auxiliary_directory_identities']), 'Artifact receipt drift')
    require(live.qualify_archive() == state['live_source_qualification'], 'Live source/freeze drift')
    item = state['complete_option_inventory']; require(file_hash(Path(item['path'])) == item['sha256'], 'Actual Iris inventory drift')
    require(len(state['launch_plan']) == 5, 'Missing bracket arm')
    for row, arm, mode in zip(state['launch_plan'], ORDER, MODES):
        require(row['arm'] == arm and row['mode'] == mode and row['command'] == command(row['profile']), 'Arm/mode/argv drift')
        require(profile_snapshot(row['profile']) == row['prepared_profile'], 'Fresh profile/source/settings/save drift')
        manifest = json.loads((Path(row['profile']).parent / 'workload-manifest.json').read_text())
        validate_arm_manifest(manifest)
        require(not (Path(row['profile']) / 'workload-measurement.json').exists(), 'Profile has been used; preserve previous capture')
    return EVIDENCE | dict(status='verified-source-current-runtime-and-five-fresh-profiles',
        launch_authorized=False, owned_live_processes=[], client_gpu_owner='root')


def screen(frame_ms, gpu_ms):
    """Arithmetic only; strict independent runtime and quality guards precede this decision."""
    require(len(frame_ms) == len(gpu_ms) == 5 and all(type(v) in (int,float) and math.isfinite(v) and v > 0
        for v in (*frame_ms,*gpu_ms)), 'Need five positive finite frame/GPU metrics')
    drift = {name:dict(frame=abs(frame_ms[end]/frame_ms[start]-1), gpu=abs(gpu_ms[end]/gpu_ms[start]-1))
        for name,start,end in (('original',0,4), ('stock',1,3))}
    frame_gain = [frame_ms[i]/frame_ms[2]-1 for i in (0,4)]
    frame_reduction = [1-frame_ms[2]/frame_ms[i] for i in (0,4)]
    gpu_reduction = [1-gpu_ms[2]/gpu_ms[i] for i in (0,4)]
    overhead = dict(frame=[frame_ms[2]/frame_ms[i]-1 for i in (1,3)], gpu=[gpu_ms[2]/gpu_ms[i]-1 for i in (1,3)])
    verdict = ('inconclusive-control-drift' if max(x for d in drift.values() for x in d.values()) > .10 + 1e-12 else
        'stop-no-useful-original-signal' if min(frame_reduction) <= .05 + 1e-12 or min(gpu_reduction) <= 0 else
        'promising-original-denominator-diagnostic-only')
    return EVIDENCE | dict(verdict=verdict, control_drift=drift, fps_gain_vs_each_original=frame_gain,
        frame_reduction_vs_each_original=frame_reduction, gpu_reduction_vs_each_original=gpu_reduction,
        adapter_overhead_vs_each_stock=overhead,
        limitation='No repeated/causal/quality-equivalent gain. Guarded serial arithmetic only; .75 reduces raster detail.')


def postanalysis_plan():
    return EVIDENCE | dict(per_arm=[
        'Root reserves client/GPU; serial foreground launches only. Preserve initial launcher/session/Java PID birth command and cwd separately from terminal records; activate ONLY proven Java PID in early warmup. Actual GLFW focus true/visible/noniconified in every sample.',
        'Prelaunch verify preparation, complete copied save, full source and class/resource freezes. No build/test/encoder/other CPU activity in declared quiet interval. Exclude all four compile/resource tasks in exact argv.',
        'Use run_proof_matrix.validate_report and validate_temporal_inputs plus explicit full Workload component validation. Actual report UUID/newer-than-launch, initial warmup60/capture20, full-frame GPU N5/coarse CPU only, >=60 positive unique GPU rows with one epoch and no dropped/zero queries. Never synthesize missing CPU rows.',
        'Both raw input boundaries identical; actual direct loaded_artifact_evidence valid/complete/error-free and ten-mod/nine-root registry/inventory checked against r4 independent receipt via qualify_volume_fast_artifacts. Four class/resource roots unchanged. Exact API Base audited metadata-only alternative only; raw actual variant retained.',
        'validate_arm_manifest uses source_qualification.complete_member_sha256 plus selected archive/map hash; complete loaded Iris map must equal full prepared expected366/366/368 map at both boundaries. Do not use legacy adaptive_pack_sha256 or BenchmarkSession toggle validator for Workload reports.',
        'Qualify complete boundary configuration_files against prepared_writable_inventory with qualify_boundary_configuration, retaining raw bytes. Full declared-default sidecar map, known source-qualified Properties.store formatting/default removal, full TOML/JSON semantics, schema1 Sodium salted identity role; only exact first main backup equal to full prepared main is qualified. Unknown files/keys/values/binary byte changes reject.',
        'Exact Overworld coastal save/pose/time12500/clear/wetness0/24chunks/FOV70/window1488x836/framebuffer2976x1672. Global true PERFORMANCE and six effects INHERIT/unity throughout; all terrain/culling/DH/probes/detailed/pass/attribution/camera features off. Output extent/focus/pacing stable, fullscreen/vsync/pause false limit260.',
        'Inspect actual final still for conspicuous foliage/water/reflection/cloud/shadow/border/trail damage. Reject gross loss before escalation; native GUI, representative motion, exact temporal history alignment and toggle transitions remain pending. Prove exact owned client/launcher handles absent after each arm.'],
        cross_arm=[
        'Only Photon original TAAUfalse native versus original TAAUtrue .75 versus private live globalON .75 differs. Mod present/global true/core/effects/runtime/hardware/driver/scene/window/pacing/full other configuration match. Selected shaderPack filename/expected option map/source archive are explicit authorized deltas, not ignored settings.',
        'Both native-before/native-after AND stock75-before/stock75-after mean-frame and median-GPU drift must be <=10%; either failure makes whole bracket inconclusive.',
        'Compare live75 to EACH original native separately; >5% mean frame-time reduction and lower GPU against BOTH only admits further tests. Report FPS using (original_frame_ms/live_frame_ms)-1. Report adapter overhead separately versus EACH stock .75; no denominator swapping.',
        'One serial bracket is rejection evidence only. Repeat reverse order/quiet/query-free corroboration plus representative still/motion quality before promoting a useful general pixel-budget adapter.'],
        evidence_paths='Per-arm control/workload-measurement.json, logs/screenshots and root initial/terminal process receipts; retain all raw failures')


def qualify_boundary_configuration(snapshot, prepared, archive_name):
    """Complete capture-time semantics with narrow source-backed startup roles; raw retained."""
    require(snapshot.get('complete') is True and snapshot.get('errors') == [] and isinstance(snapshot.get('files'),dict),
        'Incomplete capture-boundary configuration')
    files=snapshot['files']; settings={}; identities={}; sidecars={}; backups={}
    expected=set(prepared['settings']) | set(prepared['nonshader_identities'])
    for name,item in files.items():
        require(isinstance(name,str) and not Path(name).is_absolute() and '..' not in Path(name).parts,
            'Unsafe capture configuration path')
        require(isinstance(item,dict) and type(item.get('bytes')) is int and item['bytes'] >= 0
            and isinstance(item.get('sha256'),str), 'Malformed configuration record')
        text=item.get('utf8')
        if text is not None:
            require(isinstance(text,str) and len(text.encode()) == item['bytes']
                and hashlib.sha256(text.encode()).hexdigest() == item['sha256'], 'Configuration text/hash mismatch')
        if name.startswith('shaderpacks/') and name.endswith('.zip.txt'):
            require(name == 'shaderpacks/' + archive_name + '.txt' and text is not None, 'Unqualified sidecar inventory')
            sidecars[name]=canonical.semantic_properties(text); continue
        if name == 'config/shaderlod-client-1.toml.bak' and name not in expected:
            require(text is not None and tomllib.loads(text) == prepared['settings']['config/shaderlod-client.toml']['value'],
                'First main backup differs from complete prepared main')
            backups[name]=item; continue
        require(name in expected, 'Unqualified startup configuration inventory: ' + name)
        if name == 'config/sodium-fingerprint.json':
            require(text is not None, 'Sodium UTF8 missing'); value=json.loads(text)
            require(set(value)=={'v','s','u','p','t'} and type(value['v']) is int and value['v']==1
                and type(value['t']) is int and all(isinstance(value[k],str) and len(value[k])==128
                and all(c in '0123456789abcdef' for c in value[k]) for k in ('s','u','p')), 'Unknown Sodium cache schema')
            identities[name]=dict(raw=value,sha256=item['sha256']); continue
        model=prepared['settings'][name]; fmt=model['format']
        if fmt == 'toml': require(text is not None,'TOML UTF8 missing'); value=tomllib.loads(text)
        elif fmt == 'json': require(text is not None,'JSON UTF8 missing'); value=json.loads(text)
        elif fmt in ('simple literal properties','minecraft colon properties'):
            require(text is not None,'Properties UTF8 missing'); value=canonical.semantic_properties(text, ':' if fmt=='minecraft colon properties' else '=')
        else:
            require(item['sha256']==model['sha256'], 'Unknown configuration bytes changed')
            settings[name]=dict(format=fmt,sha256=item['sha256']); continue
        settings[name]=dict(format=fmt,value=value)
    require(set(files)-set(sidecars)-set(backups) == expected, 'Captured configuration inventory missing')
    require(settings == prepared['settings'], 'Unqualified startup configuration semantic change')
    values=next(iter(sidecars.values()),{})
    normalized=canonical.normalize_requested_options(prepared['sidecar']['defaults'],values)
    require(matrix.canonical_options_hash(normalized) == prepared['sidecar']['semantic_sha256'], 'Capture full sidecar semantics changed')
    return dict(settings=settings, nonshader_identities=identities, qualified_first_backup=backups,
        sidecar_semantic_sha256=matrix.canonical_options_hash(normalized), actual_configuration_files=snapshot)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run',action='store_true'); parser.add_argument('--prepare',action='store_true'); parser.add_argument('--verify',type=Path)
    parser.add_argument('--output',type=Path,default=OUTPUT); parser.add_argument('--build',type=Path,default=BUILD)
    args=parser.parse_args()
    try:
        require(sum(bool(x) for x in (args.dry_run,args.prepare,args.verify))==1, 'Select exactly one dry-run/prepare/verify')
        result=verify(json.loads(args.verify.read_text())) if args.verify else plan(args.output,args.build) if args.dry_run else prepare(args.output,args.build)
        print(json.dumps(result,indent=2,sort_keys=True))
    except (OSError,ValueError,KeyError,zipfile.BadZipFile) as failure:
        parser.exit(1,'Cannot prepare original Photon pixel baseline: '+str(failure)+'\n')


if __name__ == '__main__': main()
