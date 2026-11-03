#!/usr/bin/env python3
"""Freeze fresh original/.75/.70/.75/original forest cost controls; never launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
import copy
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import shutil
import sys

import prepare_photon_pixel_forest_quality as quality
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

baseline, copies, parser, matrix = quality.baseline, quality.copies, quality.parser, quality.matrix
OUTPUT = ROOT / 'run-workloads/pixel-forest-cost-r1'
MARKER = 'pixel-forest-cost-preparation.json'
ORDER = ('native-before','stock75-before','stock70','stock75-after','native-after')
MODES = ('native','stock75','stock70','stock75','native')
FINAL = 'FIVE_CURRENT_R8_RUNTIME_UNUSED_FOREST_COST_CONTROLS_READY_ROOT_REVIEW_PENDING'
QUALITY_MARKER = quality.OUTPUT / quality.MARKER
QUALITY_MARKER_SHA256 = '76918efa3af7ccf32d6fa49bcee0b13ed200634f846a662dcae628fddd4770cc'
QUALITY_REVIEW = ROOT / 'dev/logs/pixel-forest-quality-r1/independent-qualification.json'
IMAGE_REVIEW = ROOT / 'dev/logs/pixel-forest-quality-r1/independent-image-review.json'
OFF = tuple(dict.fromkeys(tuple(k for k in quality.OFF if k not in ('gpuTiming','cpuTiming'))
    + ('pixelHistoryCapture','pixelHistoryCaptureOnly')))
EVIDENCE = dict(diagnostic_only=True,performanceEvidence=False,adapter_gain=False,
    runtime='unverified',quality='gross-still-screen-only',motion='unverified',shader_support_claim=False,
    evidence_class='stock-pixel-budget-forest-original-denominator-cost-screen-preparation')


def command(profile):
    argv = ['./gradlew','runClientLodOn','-PrunDirectory=' + str(Path(profile).resolve()),
        '-PperformanceStack=minimal','-PincludeDh=false','-PmeasureWorkload=true','-PmeasureOnly=true',
        '-PinitialWarmup=60','-PmeasurementSeconds=20','-PbenchmarkWindowScale=2',
        '-PhideWorkloadGui=true','-PcomparisonFullscreen=false','-PfixedWetness=0',
        '-PgpuSampleInterval=5','-PgpuTiming=true','-PcpuTiming=true','-PcameraQuality=']
    argv += ['-P' + flag + '=false' for flag in OFF]
    for task in quality.canonical.BUILD_TASKS: argv += ['-x',task]
    return argv


def plan(output=OUTPUT,root=ROOT):
    root = Path(root).resolve(); copies.require(not Path(output).is_symlink(),'Symlink output unsupported')
    output = Path(output).resolve()
    copies.require(output == root / 'run-workloads/pixel-forest-cost-r1','Only owned fresh family allowed')
    copies.require(not output.exists(),'Preserve previous output; use --verify')
    return EVIDENCE | dict(schema=1,output=str(output),order=list(ORDER),modes=list(MODES),scene='forest',
        initial_warmup_seconds=60,measurement_seconds=20,gpu_sample_interval=5,minimum_gpu_samples=60,
        coarse_cpu_scopes=['game-render','render-and-blit','run-tick','swap-and-events'],
        framebuffer=[2976,1672],window=[1488,836],requested_chunks=24,pose=copies.POSES['forest'],
        maximum_original_frame_and_gpu_drift=.10,maximum_stock75_frame_and_gpu_drift=.10,
        minimum_each_original_frame_reduction=.05,main_mod_present=True,global_enabled=True,dh_present=False,
        sampler_and_other_probes=False,client_gpu_owner='root',launch_authorized=False,owned_live_processes=[],
        stop_procedure=quality.canonical.STOP,
        launch_policy='Root serial five-arm quiet interval; no builds/tests/encoders or unrelated CPU jobs. Stop on any guard failure.',
        denominator='Original stock Photon native TAAU=false; no fixed-TAAU OFF or mod-gain denominator substitution')


def maintained_sources():
    paths = [ROOT / 'scripts/prepare_photon_pixel_forest_cost.py',ROOT / 'tests/test_photon_pixel_forest_cost.py']
    for module in tuple(sys.modules.values()):
        name = getattr(module,'__file__',None)
        if name:
            path = Path(name).resolve()
            if path.suffix == '.py' and path.is_relative_to(ROOT / 'scripts'): paths.append(path)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def qualify_quality_provenance():
    copies.require(file_hash(QUALITY_MARKER) == QUALITY_MARKER_SHA256,'Historical quality preparation drift')
    component = json.loads(QUALITY_REVIEW.read_text()); image = json.loads(IMAGE_REVIEW.read_text())
    copies.require(component.get('status') == 'QUALIFIED_QUERY_FREE_FOREST_STILL_COMPONENTS_ONLY'
        and component.get('check_count') == 290,'Independent forest component screen missing/different')
    copies.require(image.get('status') == 'NO_GROSS_FOREST_STILL_REJECTION_AT_075_OR_070','Forest gross still rejection/missing acceptance')
    paths = [QUALITY_MARKER,QUALITY_REVIEW,IMAGE_REVIEW]
    for name,pin in component.get('input_sha256',{}).items():
        copies.require(file_hash(Path(name)) == pin,'Independent quality raw input drift: ' + name)
        paths.append(Path(name))
    for arm in quality.ARMS:
        profile = quality.OUTPUT / arm / 'control'
        paths += [profile / 'workload-measurement.json',profile / 'screenshots/workload-final.png']
        copies.require(file_hash(paths[-1]) == image['source_png_sha256'][arm],'Reviewed raw forest image drift')
    return dict(files_sha256={str(p):file_hash(p) for p in sorted(set(paths))},quality='gross-still-screen-only',
        source_r7_receipt_sha256=quality.RECEIPT_SHA256,
        limitations='Historical R7 stills are retained; no motion or new R8 runtime acceptance inferred.')


def qualify_runtime_receipt(path,sha256):
    path = Path(path).resolve()
    copies.require(path.parent.parent == ROOT / 'build' and path.parent.name.endswith('-r8'),
        'Explicit new R8 independent receipt required; R7 is historical quality provenance')
    external, directories = baseline.coverage.qualify_receipt(path,sha256)
    copies.require(file_hash(quality.JAR) == quality.JAR_SHA256
        and external['receipt']['independent_evidence_files_sha256'].get(str(quality.JAR)) == quality.JAR_SHA256,
        'Current independent main 5f84 JAR differs')
    for relative in ('com/pp/shaderlod/dev/PixelHistorySession.class','com/pp/shaderlod/dev/mixin/PixelHistoryFrameMixin.class'):
        copies.require((ROOT / 'build/classes/java/devClient' / relative).is_file(),'New R8 capture class missing: ' + relative)
    return external,directories


def validate_manifest(manifest,native):
    arm = manifest.get('arm'); copies.require(arm in ORDER,'Unknown forest cost arm')
    mode = MODES[ORDER.index(arm)]; copies.require(manifest.get('mode') == mode,'Arm/mode mismatch')
    projected = copy.deepcopy(manifest); projected['arm'] = mode
    result = quality.validate_manifest(projected,native)
    return result | dict(arm=arm,mode=mode)


def validate_prepared_settings(rows):
    copies.require([r['arm'] for r in rows] == list(ORDER) and [r['mode'] for r in rows] == list(MODES),
        'Missing/reordered forest cost settings')
    reference = None
    for row in rows:
        writable = row['prepared_writable_inventory']; requested = dict(writable['sidecar']['requested'])
        for key in ('TAAU','TAAU_RENDER_SCALE'): requested.pop(key,None)
        signature = dict(settings=writable['settings'],nonshader_identities=writable['nonshader_identities'],
            login_caches=writable['login_caches'],other_requested_shader_options=requested,shader_defaults=writable['sidecar']['defaults'])
        if reference is None: reference = signature
        copies.require(signature == reference,'Complete prepared controls differ beyond temporal selectors')
    return dict(complete_configuration_match=True,permitted_shader_options=['TAAU','TAAU_RENDER_SCALE'])


def prepare(receipt_path,receipt_sha256,output=OUTPUT):
    operation = plan(output); matrix.require_exclusive_runtime(); scene = quality.qualify_source()
    external,directories = qualify_runtime_receipt(receipt_path,receipt_sha256)
    provenance = qualify_quality_provenance(); frozen = maintained_sources()
    compiled = matrix.development_fingerprints(); runtime_sources = baseline.control_prep.current_runtime_sources()
    output = Path(operation['output'])
    with unlocked_world(scene['world']):
        copies.require(world_fingerprint(scene['world']) == quality.WORLD_SHA256,'Canonical stopped forest save drift')
        output.mkdir(parents=True); checks = output / 'source-checks'; checks.mkdir(); marker = output / MARKER
        state = EVIDENCE | dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            source_qualification=scene['source_qualification'],source_world_sha256=quality.WORLD_SHA256,
            historical_quality_provenance=provenance,maintained_sources_sha256=frozen,runtime_sources_sha256=runtime_sources,
            development_runtime_inputs=compiled,independent_artifact_receipt=external,auxiliary_directory_identities=directories,
            production_jar=dict(path=str(quality.JAR),sha256=quality.JAR_SHA256),launch_plan=[],launch_authorized=False,
            owned_live_processes=[],postanalysis_plan=postanalysis_plan())
        copies.write_json(marker,state)
        try:
            inventories = {mode:parser.discover(scene['archive'],scene['requested_options'][mode],checks,mode) for mode in quality.ARMS}
            for mode in quality.ARMS:
                copies.require(inventories[mode]['options'] == scene['effective_maps'][mode]
                    and inventories[mode]['option_count'] == 366,'Actual full forest Iris discovery differs: ' + mode)
            inventory_path = checks / 'option-inventory.json'; copies.write_json(inventory_path,inventories)
            state['complete_option_inventory'] = dict(path=str(inventory_path),sha256=file_hash(inventory_path))
            for arm,mode in zip(ORDER,MODES):
                base = output / arm; base.mkdir(); profile = base / 'control'
                control = scene['control'] | {'enabled':True}; copies.write_json(base / 'control.json',control)
                expected = dict(archive=scene['archive'].name,archive_sha256=quality.canonical.ORIGINAL_SHA256,
                    requested_options=scene['requested_options'][mode],expected_effective_options=inventories[mode]['options'],
                    expected_effective_options_sha256=inventories[mode]['sha256'],requested_main_mod_present=True,
                    main_mod_presence_verified=False,global_enabled=True,dh_present=False)
                manifest = EVIDENCE | dict(schema=1,scene='forest',arm=arm,mode=mode,configuration_only=False,
                    source_world_sha256=quality.WORLD_SHA256,control=control,experiment_arms={'control':expected},source_qualification=scene['source_qualification'])
                validate_manifest(manifest,scene['effective_maps']['native']); copies.write_json(base / 'workload-manifest.json',manifest)
                storage_copytree(scene['profile'],profile,ignore=shutil.ignore_patterns(*quality.canonical.TRANSIENT))
                copied_dh = profile / 'mods' / Path(scene['source_qualification']['original_source_dh']['path']).name
                copies.require(file_hash(copied_dh,'sha512') == scene['source_qualification']['original_source_dh']['sha512'],'Copied known DH differs')
                copied_dh.unlink(); copies.require(not any((profile / 'mods').glob('*.jar')),'Extra profile mod/noDH scope differs')
                (profile / 'saves').mkdir(exist_ok=True)
                storage_copytree(scene['world'],profile / 'saves' / WORLD,ignore=shutil.ignore_patterns('session.lock'))
                packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True); archive = packs / scene['archive'].name; shutil.copy2(scene['archive'],archive)
                archive.with_name(archive.name + '.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(expected['requested_options'].items())))
                iris = matrix.read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive.name
                (profile / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                (profile / 'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('adaptive'))
                effects = baseline.coverage.effects_config('inherit'); quality.validate_effects(effects)
                (profile / 'config/shaderlod-effects-client.toml').write_text(effects)
                request = EVIDENCE | dict(run_id=output.name + '-' + arm,case='forest-' + arm,task='runClientLodOn',stack='minimal',
                    include_dh=False,initial_warmup_seconds=60,measurement_seconds=20,gpu_queries=True,cpu_timing=True,
                    cloud_sampler_probe=False,fixed_wetness=0,hide_workload_gui=True,sample_interval=5,
                    expected_framebuffer=[2976,1672],expected_window=[1488,836])
                copies.write_json(profile / 'workload-request.json',request); snapshot = baseline.profile_snapshot(profile)
                copies.require(snapshot['copied_world_sha256'] == quality.WORLD_SHA256
                    and snapshot['inputs']['shader_sha256'] == quality.canonical.ORIGINAL_SHA256,'Copied source/world differs')
                state['launch_plan'].append(dict(arm=arm,mode=mode,profile=str(profile),command=command(profile),request=request,
                    expected=expected,inputs=snapshot['inputs'],prepared_profile=snapshot,
                    prepared_writable_inventory=snapshot['prepared_writable_inventory'],owner='root',status='not-launched'))
            copies.require(world_fingerprint(scene['world']) == quality.WORLD_SHA256 and maintained_sources() == frozen,
                'Canonical save/source changed during preparation')
            state['prepared_controls_comparison'] = validate_prepared_settings(state['launch_plan'])
            state['status'] = FINAL; verify(state); copies.write_json(marker,state)
        except BaseException as failure:
            state.update(status='FAILED_PREPARATION',error=str(failure)); copies.write_json(marker,state); raise
    return state


def verify(state):
    copies.require(state.get('status') == FINAL,'Incomplete preparation')
    for field in ('maintained_sources_sha256','runtime_sources_sha256'):
        for path,pin in state[field].items(): copies.require(file_hash(Path(path)) == pin,'Frozen source drift: ' + path)
    copies.require(maintained_sources() == state['maintained_sources_sha256']
        and baseline.control_prep.current_runtime_sources() == state['runtime_sources_sha256'],'Complete source inventory drift')
    for path,pin in state['source_qualification']['source_files_sha256'].items(): copies.require(file_hash(Path(path)) == pin,'Canonical source drift: ' + path)
    copies.require(matrix.development_fingerprints() == state['development_runtime_inputs'],'Current compiled/resource drift')
    copies.require(world_fingerprint(quality.SOURCE / 'saves' / WORLD) == quality.WORLD_SHA256,'Canonical stopped forest save drift')
    copies.require(qualify_quality_provenance() == state['historical_quality_provenance'],'Historical quality provenance drift')
    entry = state['independent_artifact_receipt']
    copies.require(qualify_runtime_receipt(entry['path'],entry['sha256']) == (entry,state['auxiliary_directory_identities']),'Independent R8 receipt drift')
    copies.require(state['production_jar'] == dict(path=str(quality.JAR),sha256=quality.JAR_SHA256),'Production JAR receipt drift')
    item = state['complete_option_inventory']; copies.require(file_hash(Path(item['path'])) == item['sha256'],'Full Iris inventory drift')
    inventories = json.loads(Path(item['path']).read_text()); maps = quality.option_maps(inventories['native']['options'])
    copies.require([r['arm'] for r in state['launch_plan']] == list(ORDER) and [r['mode'] for r in state['launch_plan']] == list(MODES),'Missing/reordered cost arm')
    for row in state['launch_plan']:
        arm,mode,profile = row['arm'],row['mode'],Path(row['profile'])
        copies.require(profile == OUTPUT / arm / 'control','Owned cost arm/profile drift')
        copies.require(inventories[mode]['options'] == maps[mode] and inventories[mode]['option_count'] == 366,'Full actual forest map drift')
        for path,pin in inventories[mode]['parser_dependency_sha256'].items(): copies.require(file_hash(Path(path)) == pin,'Pinned parser dependency drift')
        copies.require(file_hash(Path(inventories[mode]['log'])) == inventories[mode]['log_sha256'],'Discovery log drift')
        copies.require(row['command'] == command(profile) and baseline.profile_snapshot(profile) == row['prepared_profile'],'Fresh source/save/config/argv drift')
        copies.require(not (profile / 'workload-measurement.json').exists(),'Profile used; retain historical capture')
        manifest = json.loads((profile.parent / 'workload-manifest.json').read_text()); validate_manifest(manifest,maps['native'])
        copies.require(row['expected'] == manifest['experiment_arms']['control']
            and row['request'] == json.loads((profile / 'workload-request.json').read_text()),'Manifest/request row drift')
        quality.validate_effects((profile / 'config/shaderlod-effects-client.toml').read_text())
    copies.require(validate_prepared_settings(state['launch_plan']) == state['prepared_controls_comparison'],'Complete prepared comparison drift')
    return EVIDENCE | dict(status='verified-five-current-R8-unused-forest-cost-controls',launch_authorized=False,owned_live_processes=[])


def screen(frame_ms,gpu_ms):
    """Arithmetic only after actual guards. All denominators remain original native."""
    copies.require(len(frame_ms) == len(gpu_ms) == 5 and all(type(v) in (int,float) and math.isfinite(v) and v > 0
        for v in (*frame_ms,*gpu_ms)),'Five positive finite frame/GPU metrics required')
    drift = {name:dict(frame=abs(frame_ms[b]/frame_ms[a]-1),gpu=abs(gpu_ms[b]/gpu_ms[a]-1))
        for name,a,b in (('original',0,4),('stock75',1,3))}
    result = {}
    for label,indices in (('stock75',(1,3)),('stock70',(2,))):
        result[label] = {str(index):dict(fps_gain_vs_each_original=[frame_ms[n]/frame_ms[index]-1 for n in (0,4)],
            frame_reduction_vs_each_original=[1-frame_ms[index]/frame_ms[n] for n in (0,4)],
            gpu_reduction_vs_each_original=[1-gpu_ms[index]/gpu_ms[n] for n in (0,4)]) for index in indices}
    candidate = result['stock70']['2']
    verdict = ('inconclusive-control-drift' if max(v for d in drift.values() for v in d.values()) > .10 + 1e-12 else
        'stop-no-useful-original-signal' if min(candidate['frame_reduction_vs_each_original']) <= .05 + 1e-12
        or min(candidate['gpu_reduction_vs_each_original']) <= 0 else 'promising-stock70-calibration-only')
    return EVIDENCE | dict(verdict=verdict,control_drift=drift,original_comparisons=result,
        stock70_incremental_fps_vs_each_stock75=[frame_ms[i]/frame_ms[2]-1 for i in (1,3)],
        limitation='One serial stock calibration, not a ShaderLOD or repeated/quality-equivalent gain.')


def postanalysis_plan():
    return dict(per_arm=['Actual full366 maps/source531/LICENSE/native scene; both complete captured boundaries stable with exact permitted temporal selectors.',
        'Direct actual ten-mod/nine-root inventory against supplied current R8 receipt; noDH; all7 effects INHERIT/unity; exact API Base variant/raw provenance retained.',
        'Complete actual configuration via existing gui.qualify_configuration at both boundaries, including narrowly qualified first backups; actual hide_gui/gui_scale2 and forwarded GUI property.',
        'Native2976x1672/logical1488x836, focus/visible/noniconified/pacing constant, original forest pose/day7000/weather clear/wetness0/chunks24; observed temporal suppliers.',
        'Full positive contiguous epoch GPU game-render N5 >=60 samples; FOUR separate coarse CPU scopes and full capture coverage; no invented query counters or summed nested CPU clocks.',
        'Frame/pass/layer/probes/history capture all inactive; exact owned birth/cwd/VM args and terminal absence; final still rejection review before escalation.'],
        cross_arm=['Native controls AND .75 controls drift <=10% in both frame and GPU; either violation makes whole bracket inconclusive.',
            'Compare each stock result against BOTH original native arms; .70 >5% mean frame reduction and GPU lower versus both admits more tests only.',
            'Report .70 vs BOTH .75 separately as incremental calibration; no denominator substitution. Quiet interval excludes worker CPU jobs/builds/tests/encoders.'],
        limitations=['Stock TAAU calibration only, no adapter gain. One .70 arm is not a repeated candidate sample.',
            'Still screen only; foliage animation, motion, disocclusion and dynamic scale/history behavior remain unaccepted.'])


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--dry-run',action='store_true')
    p.add_argument('--verify',type=Path); p.add_argument('--artifact-receipt',type=Path); p.add_argument('--artifact-sha256')
    args = p.parse_args()
    if args.verify:
        copies.require(args.verify.resolve() == OUTPUT / MARKER,'Only owned preparation marker allowed')
        matrix.require_exclusive_runtime(); result = verify(json.loads(args.verify.read_text()))
    elif args.dry_run: result = plan()
    else:
        copies.require(args.artifact_receipt is not None and args.artifact_sha256 is not None,'Explicit current R8 receipt path and SHA-256 required')
        state = prepare(args.artifact_receipt,args.artifact_sha256)
        result = dict(status=state['status'],marker=str(OUTPUT / MARKER),marker_sha256=file_hash(OUTPUT / MARKER),
            order=list(ORDER),launch_authorized=False,owned_live_processes=[])
    print(json.dumps(result,sort_keys=True,indent=2))


if __name__ == '__main__': main()
