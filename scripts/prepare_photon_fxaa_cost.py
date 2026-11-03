#!/usr/bin/env python3
"""Prepare two fresh native-FXAA on/off/on cost brackets; never build or launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
from contextlib import ExitStack
from datetime import datetime, timezone
import copy
import json
import math
from pathlib import Path
import shutil
import sys

import prepare_photon_fxaa_quality_r2 as quality
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

baseline, canonical, parser, matrix, copies = quality.baseline,quality.canonical,quality.parser,quality.matrix,quality.copies
OUTPUT = ROOT / 'run-workloads/fxaa-cost-r1'
BUILD = ROOT / 'build/fxaa-cost-r1'
MARKER = 'fxaa-cost-preparation.json'
FINAL = 'SIX_UNUSED_CURRENT_R10_FXAA_COST_CONTROLS_READY_ROOT_QUIET_REVIEW'
SCENES = quality.SCENES
ORDER = ('original-before','fxaa-off','original-after')
MODES = ('original','fxaa-off','original')
QUALITY_MARKER = quality.OUTPUT / quality.MARKER
QUALITY_MARKER_SHA256 = '6b44287b4b121189e61066eb1711453bda5acf063aeaa5ceeb8e364a8b756026'
QUALITY_REVIEW = ROOT / 'dev/logs/fxaa-quality-r2/independent-review'
OFF = tuple(dict.fromkeys(tuple(k for k in quality.OFF if k not in ('gpuTiming','cpuTiming','measureWorkload','measureOnly'))
    + ('cameraQualityOnly',)))
EVIDENCE = dict(diagnostic_only=True,performanceEvidence=False,adapter_gain=False,runtime='unverified',
    quality='gross-still-and-sparse-slow-pan-screen-only',general_motion_quality_accepted=False,
    repeated_gain=False,shader_support_claim=False,
    evidence_class='stock-native-FXAA-post-AA-original-denominator-cost-preparation')


def command(profile):
    argv = ['./gradlew','runClientLodOn','-PrunDirectory=' + str(Path(profile).resolve()),
        '-PperformanceStack=minimal','-PincludeDh=false','-PmeasureWorkload=true','-PmeasureOnly=true',
        '-PinitialWarmup=60','-PmeasurementSeconds=20','-PbenchmarkWindowScale=2',
        '-PhideWorkloadGui=true','-PcomparisonFullscreen=false','-PfixedWetness=0',
        '-PgpuSampleInterval=5','-PgpuTiming=true','-PcpuTiming=true','-PcameraQuality=']
    argv += ['-P' + key + '=false' for key in OFF]
    for task in canonical.BUILD_TASKS: argv += ['-x',task]
    return argv


def plan(output=OUTPUT,build=BUILD,root=ROOT):
    root = Path(root).resolve(); copies.require(not Path(output).is_symlink() and not Path(build).is_symlink(),'Symlink output unsupported')
    output,build = Path(output).resolve(),Path(build).resolve()
    copies.require(output == root / 'run-workloads/fxaa-cost-r1' and build == root / 'build/fxaa-cost-r1','Only owned fresh families allowed')
    copies.require(not output.exists() and not build.exists(),'Preserve existing output; use --verify')
    return EVIDENCE | dict(schema=1,output=str(output),build=str(build),scenes=list(SCENES),arms=list(ORDER),modes=list(MODES),
        order=[s + '/' + a for s in SCENES for a in ORDER],profile_count=6,initial_warmup_seconds=60,measurement_seconds=20,
        framebuffer=[2976,1672],window=[1488,836],requested_chunks=24,gpu_sample_interval=5,minimum_gpu_samples=60,
        coarse_cpu_scopes=['game-render','render-and-blit','run-tick','swap-and-events'],
        maximum_control_frame_and_gpu_drift=.10,minimum_each_original_frame_reduction=.05,
        client_gpu_owner='root',launch_authorized=False,owned_live_processes=[],stop_procedure=canonical.STOP,
        launch_hold='Both scene gross-quality reviews accepted by root and all client handles terminal before assembly; quiet resource window before timing.',
        denominator='Original native stock Photon TAA=true/FXAA=true/CAS=true .50/TAAU=false',
        pass_removal='Source gate predicts composite16 disabled; no actual retired-pass or physical-fetch proof exported.')


def write_control_files(base,control,manifest):
    base = Path(base); copies.require(not base.exists() and not base.is_symlink(),'Preserve existing arm directory')
    base.mkdir(parents=True)
    copies.write_json(base / 'control.json',control); copies.write_json(base / 'workload-manifest.json',manifest)


def maintained_sources():
    paths = [ROOT / 'scripts/prepare_photon_fxaa_cost.py',ROOT / 'tests/test_photon_fxaa_cost.py']
    for module in tuple(sys.modules.values()):
        name = getattr(module,'__file__',None)
        if name:
            path = Path(name).resolve()
            if path.suffix == '.py' and path.is_relative_to(ROOT / 'scripts'): paths.append(path)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def qualify_runtime_receipt(path,pin):
    return quality.qualify_runtime_receipt(path,pin)


def qualify_quality_provenance():
    """Freeze actual component/pair records; root independently accepts gross visuals."""
    copies.require(file_hash(QUALITY_MARKER) == QUALITY_MARKER_SHA256,'R2 quality preparation changed')
    paths = [QUALITY_MARKER,ROOT / 'docs/PHOTON_FXAA_QUALITY_R2.md']; scenes = {}
    for scene in SCENES:
        pair_path = QUALITY_REVIEW / (scene + '-pair.json')
        pair = json.loads(pair_path.read_text())
        copies.require(pair.get('scene') == scene and pair.get('scope') == 'complete paired boundary/input comparability; no pixel parity or causal quality metric',
            'Complete independent scene pair comparison missing')
        paths.append(pair_path); components = {}
        for mode in quality.MODES:
            path = QUALITY_REVIEW / (scene + '-' + mode + '.json'); report = json.loads(path.read_text()); paths.append(path)
            copies.require(report.get('failures') == [] and report.get('written_slots') == list(range(61))
                and report.get('status_counts') == {'written':61} and report.get('preparation_sha256') == QUALITY_MARKER_SHA256,
                'Complete 61-slot independent quality component missing/failed')
            manifest = Path(report['manifest']); copies.require(file_hash(manifest) == report['manifest_sha256'],'Reviewed raw camera manifest changed')
            raw = json.loads(manifest.read_text())
            copies.require(raw.get('status') == 'complete' and raw.get('png_callback_successes') == 61
                and raw.get('skipped_capture_slots') == 0 and raw.get('writer_pending_at_finalization') is False,'Incomplete raw camera coverage/drain')
            paths.append(manifest); components[mode] = dict(component_path=str(path),manifest=str(manifest),manifest_sha256=report['manifest_sha256'])
        scenes[scene] = dict(pair_path=str(pair_path),components=components)
    return dict(files_sha256={str(p):file_hash(p) for p in sorted(set(paths))},scenes=scenes,
        evidence_scope='Complete independent scene components/pair data plus root-authorized gross visual review; sparse3Hz is not general motion quality.',
        general_motion_quality_accepted=False)


def validate_manifest(manifest,native):
    arm = manifest.get('arm'); copies.require(arm in ORDER,'Unknown cost arm')
    mode = MODES[ORDER.index(arm)]; copies.require(manifest.get('mode') == mode,'Arm/mode mismatch')
    projected = copy.deepcopy(manifest); projected['experiment_arms'] = {'capture':manifest.get('experiment_arms',{}).get('control',{})}
    return quality.validate_manifest(projected,native) | dict(arm=arm)


def validate_pairs(rows):
    copies.require([(r['scene'],r['arm'],r['mode']) for r in rows] == [(s,a,m) for s in SCENES for a,m in zip(ORDER,MODES)],
        'Missing/reordered scene bracket')
    for scene in SCENES:
        signatures = []
        for row in [r for r in rows if r['scene'] == scene]:
            writable = row['prepared_writable_inventory']; requested = dict(writable['sidecar']['requested']); requested.pop('FXAA',None)
            signatures.append(dict(settings=writable['settings'],identities=writable['nonshader_identities'],login=writable['login_caches'],
                other_requested_shader_options=requested,defaults=writable['sidecar']['defaults']))
        copies.require(all(s == signatures[0] for s in signatures),'Complete paired controls differ beyond FXAA: ' + scene)
    return dict(complete_other_settings_match=True,permitted_shader_options=['FXAA'])


def prepare(receipt_path,receipt_pin,output=OUTPUT,build=BUILD):
    operation = plan(output,build); matrix.require_exclusive_runtime()
    scenes = {s:quality.qualify_scene(s) for s in SCENES}; external,dirs,jar = qualify_runtime_receipt(receipt_path,receipt_pin)
    provenance = qualify_quality_provenance(); frozen = maintained_sources(); compiled = matrix.development_fingerprints()
    runtime = baseline.control_prep.current_runtime_sources(); output,build = Path(operation['output']),Path(operation['build'])
    with ExitStack() as locks:
        for scene,item in scenes.items():
            locks.enter_context(unlocked_world(item['world']))
            copies.require(world_fingerprint(item['world']) == quality.WORLD_SHA256[scene],'Canonical stopped scene save drift')
        output.mkdir(parents=True); build.mkdir(parents=True); marker = output / MARKER
        state = EVIDENCE | dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            scene_qualifications={s:item['source_qualification'] for s,item in scenes.items()},quality_provenance=provenance,
            maintained_sources_sha256=frozen,runtime_sources_sha256=runtime,development_runtime_inputs=compiled,
            independent_artifact_receipt=external,auxiliary_directory_identities=dirs,production_jar=jar,
            launch_plan=[],launch_authorized=False,owned_live_processes=[],postanalysis_plan=postanalysis_plan())
        copies.write_json(marker,state)
        try:
            inventories = {scene:{mode:parser.discover(item['archive'],item['requested_options'][mode],build,scene+'-'+mode)
                for mode in quality.MODES} for scene,item in scenes.items()}
            for scene,item in scenes.items():
                for mode in quality.MODES:
                    copies.require(inventories[scene][mode]['options'] == item['effective_maps'][mode]
                        and inventories[scene][mode]['option_count'] == 366,'Actual full Iris map differs')
            inventory_path = build / 'option-inventory.json'; copies.write_json(inventory_path,inventories)
            state['complete_option_inventory'] = dict(path=str(inventory_path),sha256=file_hash(inventory_path))
            for scene,item in scenes.items():
                for arm,mode in zip(ORDER,MODES):
                    base = output / scene / arm; profile = base / 'control'; control = item['control'] | {'enabled':True}
                    expected = dict(archive=item['archive'].name,archive_sha256=canonical.ORIGINAL_SHA256,
                        requested_options=item['requested_options'][mode],expected_effective_options=inventories[scene][mode]['options'],
                        expected_effective_options_sha256=inventories[scene][mode]['sha256'],requested_main_mod_present=True,
                        main_mod_presence_verified=False,global_enabled=True,dh_present=False)
                    manifest = EVIDENCE | dict(schema=1,scene=scene,arm=arm,mode=mode,configuration_only=False,
                        source_world_sha256=quality.WORLD_SHA256[scene],control=control,experiment_arms={'control':expected},source_qualification=item['source_qualification'])
                    validate_manifest(manifest,item['effective_maps']['original']); write_control_files(base,control,manifest)
                    storage_copytree(item['profile'],profile,ignore=shutil.ignore_patterns(*canonical.TRANSIENT))
                    dh = item['source_qualification']['original_source_dh']
                    if dh:
                        copied = profile / 'mods' / Path(dh['path']).name
                        copies.require(file_hash(copied,'sha512') == dh['sha512'],'Copied known DH differs'); copied.unlink()
                    copies.require(not any((profile / 'mods').glob('*.jar')),'Extra profile mod/noDH scope differs')
                    (profile / 'saves').mkdir(exist_ok=True); storage_copytree(item['world'],profile / 'saves' / WORLD,ignore=shutil.ignore_patterns('session.lock'))
                    packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True); archive = packs / item['archive'].name; shutil.copy2(item['archive'],archive)
                    archive.with_name(archive.name+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(expected['requested_options'].items())))
                    iris = matrix.read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive.name
                    (profile / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                    (profile / 'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('adaptive'))
                    effects = baseline.coverage.effects_config('inherit'); quality.forest.validate_effects(effects)
                    (profile / 'config/shaderlod-effects-client.toml').write_text(effects)
                    request = EVIDENCE | dict(run_id=output.name+'-'+scene+'-'+arm,case=scene+'-'+arm,task='runClientLodOn',stack='minimal',
                        include_dh=False,initial_warmup_seconds=60,measurement_seconds=20,gpu_queries=True,cpu_timing=True,
                        cloud_sampler_probe=False,fixed_wetness=0,hide_workload_gui=True,sample_interval=5,
                        expected_framebuffer=[2976,1672],expected_window=[1488,836])
                    copies.write_json(profile / 'workload-request.json',request); snapshot = baseline.profile_snapshot(profile)
                    copies.require(snapshot['copied_world_sha256'] == quality.WORLD_SHA256[scene]
                        and snapshot['inputs']['shader_sha256'] == canonical.ORIGINAL_SHA256,'Copied save/source differs')
                    state['launch_plan'].append(dict(scene=scene,arm=arm,mode=mode,profile=str(profile),command=command(profile),request=request,
                        expected=expected,inputs=snapshot['inputs'],prepared_profile=snapshot,
                        prepared_writable_inventory=snapshot['prepared_writable_inventory'],owner='root',status='not-launched'))
            for scene,item in scenes.items(): copies.require(world_fingerprint(item['world']) == quality.WORLD_SHA256[scene],'Canonical save changed')
            copies.require(maintained_sources() == frozen and baseline.control_prep.current_runtime_sources() == runtime
                and matrix.development_fingerprints() == compiled,'Recipe/runtime source/classes changed')
            state['prepared_pairs_comparison'] = validate_pairs(state['launch_plan']); state['status'] = FINAL
            verify(state); copies.write_json(marker,state)
        except BaseException as failure:
            state.update(status='FAILED_PREPARATION',error=str(failure)); copies.write_json(marker,state); raise
    return state


def verify(state):
    copies.require(state.get('status') == FINAL,'Incomplete preparation')
    for field in ('maintained_sources_sha256','runtime_sources_sha256'):
        for name,pin in state[field].items(): copies.require(file_hash(Path(name)) == pin,'Frozen source drift: ' + name)
    copies.require(baseline.control_prep.current_runtime_sources() == state['runtime_sources_sha256']
        and matrix.development_fingerprints() == state['development_runtime_inputs'],'Complete current runtime source/class drift')
    entry = state['independent_artifact_receipt']; copies.require(qualify_runtime_receipt(entry['path'],entry['sha256']) ==
        (entry,state['auxiliary_directory_identities'],state['production_jar']),'Exact current R10 artifact/JAR drift')
    copies.require(qualify_quality_provenance() == state['quality_provenance'],'Actual quality prerequisite provenance drift')
    item = state['complete_option_inventory']; copies.require(file_hash(Path(item['path'])) == item['sha256'],'Full Iris inventory drift')
    inventories = json.loads(Path(item['path']).read_text())
    for scene,source in state['scene_qualifications'].items():
        for name,pin in source['source_files_sha256'].items(): copies.require(file_hash(Path(name)) == pin,'Canonical source drift')
        source_profile = quality.forest.SOURCE if scene == 'forest' else canonical.SOURCE
        copies.require(world_fingerprint(source_profile / 'saves' / WORLD) == quality.WORLD_SHA256[scene],'Canonical stopped source save drift')
    for row in state['launch_plan']:
        scene,arm,mode,profile = row['scene'],row['arm'],row['mode'],Path(row['profile']); discovery = inventories[scene][mode]
        copies.require(profile == OUTPUT / scene / arm / 'control','Owned arm/profile association differs')
        copies.require(discovery['options'] == quality.option_maps(scene,inventories[scene]['original']['options'])[mode]
            and discovery['option_count'] == 366,'Full source option map drift')
        for name,pin in discovery['parser_dependency_sha256'].items(): copies.require(file_hash(Path(name)) == pin,'Pinned parser dependency drift')
        copies.require(file_hash(Path(discovery['log'])) == discovery['log_sha256'],'Discovery log drift')
        copies.require(row['command'] == command(profile) and baseline.profile_snapshot(profile) == row['prepared_profile'],'Fresh source/save/configuration/argv drift')
        copies.require(not (profile / 'workload-measurement.json').exists() and not (profile / 'camera-quality').exists(),'Profile used; preserve raw capture')
        manifest = json.loads((profile.parent / 'workload-manifest.json').read_text()); validate_manifest(manifest,inventories[scene]['original']['options'])
        copies.require(row['expected'] == manifest['experiment_arms']['control'] and row['request'] == json.loads((profile / 'workload-request.json').read_text()),'Manifest/request row differs')
        quality.forest.validate_effects((profile / 'config/shaderlod-effects-client.toml').read_text())
    copies.require(validate_pairs(state['launch_plan']) == state['prepared_pairs_comparison'],'Complete prepared pair comparison drift')
    return EVIDENCE | dict(status='verified-six-current-R10-unused-FXAA-cost-controls',launch_authorized=False,owned_live_processes=[])


def screen(frame_ms,gpu_ms):
    copies.require(len(frame_ms) == len(gpu_ms) == 3 and all(type(v) in (int,float) and math.isfinite(v) and v > 0
        for v in (*frame_ms,*gpu_ms)),'Three positive finite frame/GPU metrics required')
    drift = dict(frame=abs(frame_ms[2]/frame_ms[0]-1),gpu=abs(gpu_ms[2]/gpu_ms[0]-1))
    reduction = [1-frame_ms[1]/frame_ms[i] for i in (0,2)]; gpu = [1-gpu_ms[1]/gpu_ms[i] for i in (0,2)]
    verdict = ('inconclusive-original-control-drift' if max(drift.values()) > .10 + 1e-12 else
        'stop-no-useful-original-signal' if min(reduction) <= .05 + 1e-12 or min(gpu) <= 0 else 'promising-stock-FXAA-calibration-only')
    return EVIDENCE | dict(verdict=verdict,control_drift=drift,frame_reduction_vs_each_original=reduction,
        gpu_reduction_vs_each_original=gpu,fps_gain_vs_each_original=[frame_ms[i]/frame_ms[1]-1 for i in (0,2)],
        limitation='Arithmetic after full actual guards; one serial stock screen, not adapter/repeated/quality-equivalent gain.')


def postanalysis_plan():
    return dict(per_arm=['Actual source531/LICENSE/full366 options at both boundaries; native scene/world/camera/day/weather/wetness/chunks, full seven effects and complete captured settings; only FXAA differs.',
        'Current direct ten-mod/nine-root R10 artifacts and coherent main JAR/compiled roots. Independently listed JarJar alternatives qualify individually only; retain actual selected records/URIs/identities.',
        '60s warmup/20s capture; full game-render GPU N5 >=60 positive unique contiguous epoch rows; four coarse CPU scopes with complete contiguous wall coverage, kept separate.',
        'Hidden actualGUI/native2976x1672/logical1488x836, focused/visible/noniconified stable pacing, exact VMbirth/cwd/terminal receipts. No pass/layer/probe/camera/history or readback contamination.'],
        cross_arm=['Each scene original-on/off/original-on independently; frame AND GPU original drift <=10%. >5% frame reduction and lowerGPU versus BOTH originals only admits further diagnostics.',
            'Require exact raw selected artifact inventories across arms first; known-alternative mismatch must retain strict failure and separate explicitly reviewed component qualification. Never silently normalize away fields.',
            'Root owns quiet lane; no worker/source/tests/build/hash/encoder activity. Source composite16 gate predicts removal but actual retired-pass absence is not exported.'],
        limitations=['Sparse3Hz gross still/slowpan prerequisites do not establish general motion quality. No hard user-goal threshold or quality-equivalent/repeated/mod gain acceptance.',
            'Two scenes are not interchangeable denominators. Stop adverse/low-signal scene escalation; do not lower gates or infer a cause for drift.'])


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--dry-run',action='store_true'); p.add_argument('--verify',type=Path)
    p.add_argument('--artifact-receipt',type=Path); p.add_argument('--artifact-sha256'); args = p.parse_args()
    if args.verify:
        copies.require(args.verify.resolve() == OUTPUT / MARKER,'Only owned marker allowed'); matrix.require_exclusive_runtime()
        result = verify(json.loads(args.verify.read_text()))
    elif args.dry_run: result = plan()
    else:
        copies.require(args.artifact_receipt is not None and args.artifact_sha256 is not None,'Explicit current R10 receipt path/SHA-256 required')
        state = prepare(args.artifact_receipt,args.artifact_sha256)
        result = dict(status=state['status'],marker=str(OUTPUT / MARKER),marker_sha256=file_hash(OUTPUT / MARKER),
            profiles=6,launch_authorized=False,owned_live_processes=[])
    print(json.dumps(result,sort_keys=True,indent=2))


if __name__ == '__main__': main()
