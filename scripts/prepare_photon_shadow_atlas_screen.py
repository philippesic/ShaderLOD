#!/usr/bin/env python3
"""Stage/finalize original Photon shadow-atlas quality and cost controls; never launch."""
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
import zipfile

import prepare_photon_pixel_baseline as baseline
import prepare_photon_cloud_tail_diagnostic as gui
import analyze_photon_pixel_baseline as actual
from prepare_reduced_effects_profiles import require, write_json
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

canonical, live, matrix = baseline.canonical, baseline.live, baseline.matrix
OUTPUT = ROOT / 'run-workloads/shadow-atlas-screen-r1'
BUILD = ROOT / 'build/shadow-atlas-screen-r1'
MARKER = 'shadow-atlas-preparation.json'
STAGED = 'SOURCE_FIVE_FRESH_PROFILES_STAGED_RUNTIME_RECEIPT_PENDING'
FINAL = 'SOURCE_CURRENT_RUNTIME_FIVE_FRESH_PROFILES_READY_ROOT_REVIEW_PENDING'
ORDER = (('quality2048', 'quality', 2048), ('quality1536', 'quality', 1536),
         ('cost2048-before', 'cost', 2048), ('cost1536', 'cost', 1536), ('cost2048-after', 'cost', 2048))
OFF = tuple(dict.fromkeys(baseline.OFF + ('cloudSamplerProbe', 'pixelCoverage', 'pixelCoverageOnly')))
EVIDENCE = dict(performanceEvidence=False, adapter_gain=False, shader_support_claim=False,
    diagnostic_only=True, runtime='unverified', quality='unaccepted', motion='unverified',
    evidence_class='stock-shadow-atlas-budget-calibration')


def command(profile, kind):
    require(kind in ('quality', 'cost'), 'Unknown capture kind')
    timed = kind == 'cost'
    argv = ['./gradlew', 'runClientLodOn', '-PrunDirectory=' + str(Path(profile).resolve()),
        '-PperformanceStack=minimal', '-PincludeDh=false', '-PmeasureWorkload=true', '-PmeasureOnly=true',
        '-PinitialWarmup=60', '-PmeasurementSeconds=' + ('20' if timed else '8'), '-PbenchmarkWindowScale=2',
        '-PhideWorkloadGui=true', '-PcomparisonFullscreen=false', '-PfixedWetness=0', '-PgpuSampleInterval=5',
        '-PgpuTiming=' + str(timed).lower(), '-PcpuTiming=' + str(timed).lower(), '-PcameraQuality=']
    argv += ['-P' + flag + '=false' for flag in OFF]
    for task in canonical.BUILD_TASKS: argv += ['-x', task]
    return argv


def plan(output=OUTPUT, build=BUILD, root=ROOT):
    root = Path(root).resolve()
    require(not Path(output).is_symlink() and not Path(build).is_symlink(), 'Symlink output unsupported')
    output, build = Path(output).resolve(), Path(build).resolve()
    require(output == root / 'run-workloads/shadow-atlas-screen-r1' and build == root / 'build/shadow-atlas-screen-r1',
        'Only owned fresh family allowed')
    require(not output.exists() and not build.exists(), 'Preserve previous outputs; use verify')
    return EVIDENCE | dict(schema=1, output=str(output), build=str(build), order=[x[0] for x in ORDER],
        requested_chunks=24, framebuffer=[2976,1672], window=[1488,836], initial_warmup_seconds=60,
        quality_capture_seconds=8, cost_capture_seconds=20, gpu_sample_interval=5, minimum_gpu_samples=60,
        minimum_fps_signal=.05, maximum_frame_control_drift=.10, maximum_gpu_control_drift=.10,
        main_mod_present=True, global_enabled=True, dh_present=False, taau=False, shadow_distance_blocks=128,
        vps=False, minimum_filter_support_increase=1/3, atlas_pixel_reduction=.4375,
        client_gpu_owner='root', launch_authorized=False, owned_live_processes=[], stop_procedure=canonical.STOP,
        sequence_gate='Both query-free quality controls must qualify and root must accept gross still quality before unopened cost bracket',
        denominator='Unmodified Photon at 2048 versus same stock archive at 1536; not an adapter or mod gain',
        focus_contract='Actual sampled GLFW focused=true, visible=true, iconified=false throughout; no invented native-app focus receipt')


def maintained_sources():
    paths = [ROOT / 'scripts/prepare_photon_shadow_atlas_screen.py', ROOT / 'tests/test_photon_shadow_atlas_screen.py']
    for module in tuple(sys.modules.values()):
        name = getattr(module, '__file__', None)
        if name:
            path = Path(name).resolve()
            if path.suffix == '.py' and path.is_relative_to(ROOT / 'scripts'): paths.append(path)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def validate_maps(native, candidate):
    require(len(native) == len(candidate) == 366 and native.get('shadowMapResolution') == '2048', 'Full original map differs')
    fixed = {'TAA':'true', 'TAAU':'false', 'TAAU_RENDER_SCALE':'0.75', 'shadowDistance':'128.0', 'SHADOW_VPS':'false'}
    require(all(native.get(k) == v for k,v in fixed.items()) and not any(k.startswith('SHADERLOD_') for k in native),
        'Native temporal/shadow/private controls differ')
    require(candidate == native | {'shadowMapResolution':'1536'}, 'Complete map differs beyond atlas resolution')
    require(matrix.canonical_options_hash(native) == canonical.SOURCE_OPTIONS_SHA256, 'Original complete map pin differs')
    return {'shadowMapResolution':['2048','1536']}


def validate_arm_manifest(manifest):
    require(manifest.get('schema') == 1 and manifest.get('scene') == 'coastalwater'
        and manifest.get('configuration_only') is False and manifest.get('source_world_sha256') == baseline.coverage.WORLD_SHA256,
        'Frozen manifest scene/save differs')
    model = manifest.get('experiment_arms', {}).get('control', {})
    resolution = manifest.get('shadow_resolution')
    require(type(resolution) is int and resolution in (2048,1536), 'Unsupported atlas resolution')
    source = manifest.get('source_qualification', {}); members = source.get('complete_member_sha256')
    require(isinstance(members,dict) and len(members) == 531 and
        matrix.canonical_options_hash(members) == source.get('complete_member_map_sha256'), 'Complete original member receipt differs')
    require(source.get('archive_sha256') == model.get('archive_sha256') == canonical.ORIGINAL_SHA256, 'Original source archive differs')
    options = model.get('expected_effective_options', {})
    require(len(options) == 366 and matrix.canonical_options_hash(options) == model.get('expected_effective_options_sha256'), 'Full option map/hash differs')
    native = options | {'shadowMapResolution':'2048'}; validate_maps(native,native | {'shadowMapResolution':'1536'})
    require(options.get('shadowMapResolution') == str(resolution), 'Resolution label/options differ')
    require(manifest.get('control', {}).get('enabled') is True and model.get('requested_main_mod_present') is True
        and model.get('dh_present') is False, 'Mod/global/noDH scope differs')
    return dict(member_count=531, option_count=366, shadow_resolution=resolution)


def stage(output=OUTPUT, build=BUILD):
    operation = plan(output,build); matrix.require_exclusive_runtime()
    scene = canonical.qualify_source(); frozen = maintained_sources()
    output, build = Path(operation['output']), Path(operation['build']); build.mkdir(parents=True)
    inventories = {}
    for resolution in (2048,1536):
        requested = scene['requested_options']['native'] | {'shadowMapResolution':str(resolution)}
        inventories[str(resolution)] = live.discover(scene['archive'], requested, build, 'shadow' + str(resolution))
    validate_maps(inventories['2048']['options'],inventories['1536']['options'])
    write_json(build / 'option-inventory.json', inventories)
    with unlocked_world(scene['world']):
        fingerprint = world_fingerprint(scene['world']); require(fingerprint == baseline.coverage.WORLD_SHA256, 'Canonical world differs')
        output.mkdir(parents=True); marker = output / MARKER
        source = dict(archive_sha256=canonical.ORIGINAL_SHA256,
            complete_member_sha256=scene['qualification']['complete_archive_member_sha256'])
        source['complete_member_map_sha256'] = matrix.canonical_options_hash(source['complete_member_sha256'])
        state = EVIDENCE | dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            maintained_source_files_sha256=frozen, canonical_source_qualification=scene['qualification'], source_qualification=source,
            source_world_sha256=fingerprint, complete_option_inventory=dict(path=str(build / 'option-inventory.json'),sha256=file_hash(build / 'option-inventory.json')),
            launch_plan=[],independent_artifact_receipt=None,runtime_sources_sha256=None,development_runtime_inputs=None,
            auxiliary_directory_identities=None,production_jar=None,launch_authorized=False,owned_live_processes=[],postanalysis_plan=postanalysis_plan())
        write_json(marker,state)
        try:
            for arm,kind,resolution in ORDER:
                base = output / arm; base.mkdir(); profile = base / 'control'
                control = scene['control'] | {'enabled':True}; write_json(base / 'control.json',control)
                requested = scene['requested_options']['native'] | {'shadowMapResolution':str(resolution)}
                inventory = inventories[str(resolution)]
                expected = dict(archive=scene['archive'].name,archive_sha256=canonical.ORIGINAL_SHA256,
                    requested_options=requested,expected_effective_options=inventory['options'],expected_effective_options_sha256=inventory['sha256'],
                    shadow_resolution=resolution,global_enabled=True,requested_main_mod_present=True,main_mod_presence_verified=False,dh_present=False)
                manifest = EVIDENCE | dict(schema=1,scene='coastalwater',configuration_only=False,source_world_sha256=fingerprint,
                    control=control,kind=kind,shadow_resolution=resolution,experiment_arms={'control':expected},source_qualification=source)
                validate_arm_manifest(manifest); write_json(base / 'workload-manifest.json',manifest)
                storage_copytree(scene['source'],profile,ignore=shutil.ignore_patterns(*canonical.TRANSIENT))
                (profile / 'saves').mkdir(exist_ok=True)
                storage_copytree(scene['world'],profile / 'saves' / WORLD,ignore=shutil.ignore_patterns('session.lock'))
                require(not any((profile / 'mods').glob('*.jar')), 'Extra profile mods/DH')
                packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True); archive = packs / scene['archive'].name; shutil.copy2(scene['archive'],archive)
                archive.with_name(archive.name + '.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(requested.items())))
                iris = matrix.read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive.name
                (profile / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                (profile / 'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('adaptive'))
                (profile / 'config/shaderlod-effects-client.toml').write_text(baseline.coverage.effects_config('inherit'))
                timed = kind == 'cost'
                request = EVIDENCE | dict(run_id=output.name + '-' + arm,case='photon-shadow-' + arm,task='runClientLodOn',stack='minimal',
                    include_dh=False,initial_warmup_seconds=60,measurement_seconds=20 if timed else 8,gpu_queries=timed,cpu_timing=timed,
                    sample_interval=5,fixed_wetness=0,hide_workload_gui=True,expected_framebuffer=[2976,1672],expected_window=[1488,836])
                write_json(profile / 'workload-request.json',request); snapshot = baseline.profile_snapshot(profile)
                require(snapshot['copied_world_sha256'] == fingerprint and snapshot['inputs']['shader_sha256'] == canonical.ORIGINAL_SHA256, 'Copied save/archive differs')
                state['launch_plan'].append(dict(arm=arm,kind=kind,shadow_resolution=resolution,profile=str(profile),command=command(profile,kind),
                    request=request,expected=expected,inputs=snapshot['inputs'],prepared_profile=snapshot,
                    prepared_writable_inventory=snapshot['prepared_writable_inventory'],owner='root',status='not-launched'))
            require(world_fingerprint(scene['world']) == fingerprint and maintained_sources() == frozen, 'Source changed during preparation')
            state['status'] = STAGED; write_json(marker,state); verify_source(state)
        except BaseException as failure:
            state.update(status='FAILED_PREPARATION',error=str(failure)); write_json(marker,state); raise
    return state


def verify_source(state):
    require(state.get('status') in (STAGED,FINAL), 'Incomplete preparation')
    for name,pin in state['maintained_source_files_sha256'].items(): require(file_hash(Path(name)) == pin, 'Frozen recipe/source drift: ' + name)
    for name,pin in state['canonical_source_qualification']['source_files_sha256'].items(): require(file_hash(Path(name)) == pin, 'Canonical source drift: ' + name)
    item = state['complete_option_inventory']; require(file_hash(Path(item['path'])) == item['sha256'], 'Full actual Iris inventory drift')
    inventories = json.loads(Path(item['path']).read_text()); validate_maps(inventories['2048']['options'],inventories['1536']['options'])
    require(len(state['launch_plan']) == len(ORDER), 'Missing arm')
    for row,(arm,kind,resolution) in zip(state['launch_plan'],ORDER):
        require((row['arm'],row['kind'],row['shadow_resolution']) == (arm,kind,resolution)
            and row['command'] == command(row['profile'],kind), 'Arm/order/argv drift')
        require(baseline.profile_snapshot(row['profile']) == row['prepared_profile'], 'Copied save/source/configuration drift')
        manifest = json.loads((Path(row['profile']).parent / 'workload-manifest.json').read_text()); validate_arm_manifest(manifest)
        require(not (Path(row['profile']) / 'workload-measurement.json').exists(), 'Profile used; preserve historical capture')
    return EVIDENCE | dict(status='verified-five-source-staged-controls',launch_authorized=False,owned_live_processes=[])


def finalize(marker,receipt_path,receipt_sha256,production_jar,production_sha256):
    marker = Path(marker).resolve()
    require(marker == OUTPUT / MARKER, 'Only owned preparation marker allowed')
    state = json.loads(marker.read_text()); verify_source(state); matrix.require_exclusive_runtime()
    require(state['status'] == STAGED, 'Finalization only once; verify an existing final receipt')
    require(Path(receipt_path).resolve().parent.name.endswith('-r6'), 'Explicit new r6 integrated receipt required')
    external,directories = baseline.coverage.qualify_receipt(receipt_path,receipt_sha256)
    production_jar = Path(production_jar).resolve()
    require(production_jar.parent == ROOT / 'build/libs' and file_hash(production_jar) == production_sha256
        and external['receipt'].get('independent_evidence_files_sha256',{}).get(str(production_jar)) == production_sha256, 'Current production JAR/independent receipt pin differs')
    state.update(status=FINAL,independent_artifact_receipt=external,auxiliary_directory_identities=directories,
        runtime_sources_sha256=baseline.control_prep.current_runtime_sources(),development_runtime_inputs=matrix.development_fingerprints(),
        production_jar=dict(path=str(production_jar),sha256=production_sha256),bootstrap_source_evidence=baseline.cost.bootstrap_source_evidence())
    verify(state); write_json(marker,state); return state


def verify(state):
    verify_source(state); require(state['status'] == FINAL, 'Current runtime receipt pending')
    require(baseline.control_prep.current_runtime_sources() == state['runtime_sources_sha256'], 'Runtime source drift')
    require(matrix.development_fingerprints() == state['development_runtime_inputs'], 'Class/resource drift')
    entry = state['independent_artifact_receipt']
    require(baseline.coverage.qualify_receipt(entry['path'],entry['sha256']) == (entry,state['auxiliary_directory_identities']), 'Independent artifact drift')
    jar = state['production_jar']; require(file_hash(Path(jar['path'])) == jar['sha256'], 'Production JAR drift')
    return EVIDENCE | dict(status='verified-current-runtime-five-unused-controls',launch_authorized=False,owned_live_processes=[])


def qualify_configuration(snapshot,row):
    return gui.qualify_configuration(snapshot,row)


def qualify_gui_report(report,row):
    return gui.qualify_gui_report(report,row)


def screen(frame_ms,gpu_ms):
    require(len(frame_ms) == len(gpu_ms) == 3 and all(type(v) in (int,float) and math.isfinite(v) and v > 0 for v in (*frame_ms,*gpu_ms)), 'Three positive finite frame/GPU metrics required')
    drift = dict(frame=abs(frame_ms[2]/frame_ms[0]-1),gpu=abs(gpu_ms[2]/gpu_ms[0]-1))
    gain = [frame_ms[i]/frame_ms[1]-1 for i in (0,2)]
    gpu_reduction = [1-gpu_ms[1]/gpu_ms[i] for i in (0,2)]
    verdict = ('inconclusive-control-drift' if max(drift.values()) > .10 + 1e-12 else
        'stop-no-useful-stock-signal' if min(gain) <= .05 + 1e-12 or min(gpu_reduction) <= 0 else 'promising-stock-calibration-only')
    return EVIDENCE | dict(verdict=verdict,control_drift=drift,fps_gain_vs_each_2048=gain,gpu_reduction_vs_each_2048=gpu_reduction,
        limitation='Arithmetic after full actual guards and gross quality acceptance; single stock bracket, no mod/repeated/quality-equivalent win')


def postanalysis_plan():
    return dict(per_arm=[
        'Root alone reserves client/GPU; retain exact launcher/session/Java birth, cwd, VM args and terminal absence proof. No builds/tests/encoders during quiet cost interval; no JXA/F1 action.',
        'Verify immutable frozen preparation before each unused profile; current source, four compiled roots, complete ten-mod/nine-root direct artifact receipt and new production JAR must match.',
        'Actual schema2 report complete, exact request UUID/newer-than-launch, original archive SHA plus all531 member hashes; full actual366 Iris map at both boundaries must equal prepared inventory. No fallback; actual in-world rendering distinct from source discovery.',
        'qualify_gui_report requires forwarded hideWorkloadGui=true and actual typed hide_gui=true/gui_scale2 at both boundaries. qualifier_configuration retains full captured raw bytes plus complete semantics; only source-backed first backups/default sidecar formatting/Sodium identity roles allowed.',
        'Both full raw input boundaries equal: Overworld coastal camera500.5/68/660.5 yaw87.38 pitch3.735 time12500 clear wetness0 chunks24; lod globaltrue/PERFORMANCE near32/far96/transition8/debugfalse, six effects INHERIT/unity. Full options.txt/config settings including FOV70 must match frozen prepared settings.',
        'actual.window(report) validates every sampled GLFW focus/visible/noniconified/native2976x1672/window1488x836 and stable fullscreenfalse/vsyncfalse/pausefalse/limit260. actual.matrix.validate_temporal_inputs checks observed fixedWetness0.',
        'Query-free quality:60s warmup/8s capture, GPU and CPU streams empty, all probes/pass/attribution/detailed timing off. Standard matrix validator unconditionally requires CPU; use full component guards without fabricating CPU rows or calling it passed.',
        'Cost:60s warmup/20s capture, matrix.validate_report plus actual.timing(report) for >=60 positive contiguous epoch2 N5 fullgame-render rows, all FOUR separate run-tick/game-render/render-and-blit/swap-and-events scopes and wall coverage. Never replace with run-tick-only fixture or sum nested scopes.',
        'Actual Java VM properties must match exact command/request. Detailed timing, probes, terrain/culling/DH and diagnostics off; hardware context Apple M3Max OpenGL4.1 Metal91.7. New core frame snapshot source/reset transport does not prove original Photon consumes reset.',
        'Physical1536 shadow allocation remains unverified unless existing actual sampler observer records both depth dimensions; option/directive source alone cannot prove texture size. No new observer in this prep.'],
        quality_gate='Inspect both unmasked native stills for low-sun terrain/contact/foliage/thin shadows, water/reflection/shaft changes/acne/leaks. Gross degradation stops cost. Static coast does not certify representative foliage or grid-boundary motion/history; those remain required before promotion.',
        cross_arm='Full hardware/artifacts/world/control/config/nativeTAA and all366 options identical except shadowMapResolution2048/1536. Both frame and GPU2048before/after drift <=10%; >5% FPS and lowerGPU against EACH2048 is rejection-only screen. No1024 escalation to rescue; stock reduction not adapter/mod gain.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run',action='store_true'); parser.add_argument('--stage',action='store_true')
    parser.add_argument('--finalize',type=Path); parser.add_argument('--verify',type=Path)
    parser.add_argument('--artifact-receipt',type=Path); parser.add_argument('--artifact-sha256')
    parser.add_argument('--production-jar',type=Path); parser.add_argument('--production-sha256')
    args = parser.parse_args()
    try:
        require(sum(bool(x) for x in (args.dry_run,args.stage,args.finalize,args.verify)) == 1, 'Select one operation')
        if args.finalize: result = finalize(args.finalize,args.artifact_receipt,args.artifact_sha256,args.production_jar,args.production_sha256)
        elif args.verify:
            state = json.loads(args.verify.read_text()); result = verify(state) if state.get('status') == FINAL else verify_source(state)
        else: result = plan() if args.dry_run else stage()
        print(json.dumps(result,sort_keys=True,indent=2))
    except (OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile) as failure: parser.exit(1,'Cannot prepare shadow-atlas screen: ' + str(failure) + '\n')


if __name__ == '__main__': main()
