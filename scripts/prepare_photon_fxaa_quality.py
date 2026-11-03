#!/usr/bin/env python3
"""Prepare four original-Photon native FXAA camera controls; never build or launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
from contextlib import ExitStack
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import shutil
import sys

import prepare_photon_pixel_forest_quality as forest
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

baseline, canonical, parser, matrix, copies = forest.baseline,forest.canonical,forest.parser,forest.matrix,forest.copies
OUTPUT = ROOT / 'run-workloads/fxaa-quality-r1'
BUILD = ROOT / 'build/fxaa-quality-r1'
MARKER = 'fxaa-quality-preparation.json'
FINAL = 'FOUR_UNUSED_CURRENT_R9_FXAA_MOTION_CONTROLS_READY_ROOT_EVIDENCE_REVIEW'
SCENES = ('forest','coastalwater')
MODES = ('original','fxaa-off')
MAP_SHA256 = {
    'forest':{'original':'4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745',
        'fxaa-off':'4b7c332f53fb57360617ee75f5dfdc7df7e9c060d17426b7707d97c0058bca6c'},
    'coastalwater':{'original':'ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669',
        'fxaa-off':'af4bb729eea85c200cb12eb970df6fa0bd3885d84c60b788d328af8bd322b4a0'}}
WORLD_SHA256 = {'forest':forest.WORLD_SHA256,'coastalwater':baseline.coverage.WORLD_SHA256}
OFF = tuple(dict.fromkeys(tuple(k for k in forest.OFF if k != 'cameraQualityOnly')
    + ('measureWorkload','measureOnly','pixelHistoryCapture','pixelHistoryCaptureOnly')))
EVIDENCE = dict(diagnostic_only=True,performanceEvidence=False,adapter_gain=False,runtime='unverified',
    quality='unaccepted',motion='unverified',shader_support_claim=False,
    evidence_class='native-stock-FXAA-post-AA-budget-still-and-moving-camera-preparation')


def camera_request(scene):
    copies.require(scene in SCENES,'Unknown camera scene')
    pose = {k:v for k,v in copies.POSES[scene].items() if k != 'time'}
    # Held initial/final poses supply still images within the same normal-history sequence.
    # Gentle translation and yaw do not imply successful collision/fluid checks.
    shift = dict(x=pose['x']+4.0,z=pose['z'],yaw=pose['yaw']-12.0)
    return dict(schema=1,label='photon-fxaa-' + scene + '-quality-r1',warmupSeconds=30,fps=5,
        pngCompressionLevel=1,maximumInFlightImages=2,expectedCameraFluid='NONE',rejectFullBlockCamera=True,
        waypoints=[pose | {'seconds':0},pose | {'seconds':3},pose | shift | {'seconds':8},
            pose | shift | {'seconds':13,'yaw':pose['yaw']+12.0},pose | {'seconds':18},pose | {'seconds':20}])


def validate_camera_request(request,scene):
    """Source-aligned bounds only; the actual Java parser/route remains a runtime check."""
    copies.require(request == camera_request(scene),'Matched route/request differs')
    copies.require(request['warmupSeconds'] == 30 and request['fps'] == 5,'Capture clocks differ')
    points = request['waypoints']; total = 0.0
    for a,b in zip(points,points[1:]):
        dt = b['seconds']-a['seconds']; distance = math.sqrt(sum((b[k]-a[k])**2 for k in ('x','y','z'))); total += distance
        copies.require(dt >= .25 and 1.5*distance/dt <= 32 and 1.5*abs(b['yaw']-a['yaw'])/dt <= 90
            and 1.5*abs(b['pitch']-a['pitch'])/dt <= 90,'Actual-parser source speed/segment bound exceeded')
    copies.require(points[0]['seconds'] == 0 and 20 <= points[-1]['seconds'] <= 30 and total <= 512,'Sequence bound differs')
    return dict(slot_count=101,duration_seconds=20,held_still_seconds=[[0,3],[18,20]],
        review_slots=[0,15,40,65,90,100],normal_clocks=True,
        source_scope='Bounds from pinned CameraQualitySession.Config source; actual parser was not executed here')


def command(profile,request):
    argv = ['./gradlew','runClientLodOn','-PrunDirectory=' + str(Path(profile).resolve()),
        '-PperformanceStack=minimal','-PincludeDh=false','-PbenchmarkWindowScale=2','-PcomparisonFullscreen=false',
        '-PfixedWetness=0','-PinitialWarmup=30','-PgpuSampleInterval=5',
        '-PcameraQuality=' + str(Path(request).resolve()),'-PcameraQualityOnly=true']
    argv += ['-P' + flag + '=false' for flag in OFF]
    for task in canonical.BUILD_TASKS: argv += ['-x',task]
    return argv


def plan(output=OUTPUT,build=BUILD,root=ROOT):
    root = Path(root).resolve(); copies.require(not Path(output).is_symlink() and not Path(build).is_symlink(),'Symlink output unsupported')
    output,build = Path(output).resolve(),Path(build).resolve()
    copies.require(output == root / 'run-workloads/fxaa-quality-r1' and build == root / 'build/fxaa-quality-r1','Only owned fresh families allowed')
    copies.require(not output.exists(),'Preserve existing output; use --verify')
    if build.exists():
        failure = build / 'failed-attempt-r1'
        copies.require(set(p.name for p in build.iterdir()) == {'failed-attempt-r1'} and not failure.is_symlink()
            and (failure / 'preparation.json').is_file(),'Preserve existing build output; use --verify')
        retained = json.loads((failure / 'preparation.json').read_text())
        copies.require(retained.get('status') == 'FAILED_PREPARATION'
            and retained.get('error') == "[Errno 2] No such file or directory: '" + str(output / 'forest/camera-request.json') + "'"
            and retained.get('launch_plan') == [],'Only exact archived request-directory failure can precede fresh preparation')
        relocated = json.loads((failure / 'relocation.json').read_text())
        for record in relocated['files']:
            copies.require(file_hash(Path(record['archived_path'])) == record['sha256'],'Archived failed input drift')
    return EVIDENCE | dict(schema=1,output=str(output),build=str(build),scenes=list(SCENES),modes=list(MODES),
        order=[scene + '/' + mode for scene in SCENES for mode in MODES],profile_count=4,
        native_framebuffer=[2976,1672],logical_window=[1488,836],requested_chunks=24,main_mod_present=True,dh_present=False,
        route={s:validate_camera_request(camera_request(s),s) for s in SCENES},gpu_queries=False,cpu_timing=False,
        separate_workload_stills=False,client_gpu_owner='root',launch_authorized=False,owned_live_processes=[],
        stop_procedure=canonical.STOP,
        evidence_hold='Current camera producer direct artifacts/full captured settings/effects/window evidence must be independently assessed before runtime acceptance; preparation is not those observations.')


def option_maps(scene,native):
    copies.require(scene in SCENES and len(native) == 366 and matrix.canonical_options_hash(native) == MAP_SHA256[scene]['original'],'Complete original scene map differs')
    required = {'TAA':'true','TAAU':'false','TAAU_RENDER_SCALE':'0.75','FXAA':'true','CAS':'true','CAS_INTENSITY':'0.50'}
    copies.require(all(native.get(k) == v for k,v in required.items()) and not any(k.startswith('SHADERLOD_') for k in native),'Native TAA/sharpen/private contract differs')
    result = {'original':dict(native),'fxaa-off':native | {'FXAA':'false'}}
    copies.require(matrix.canonical_options_hash(result['fxaa-off']) == MAP_SHA256[scene]['fxaa-off'],'Exact only-FXAA delta differs')
    return result


def qualify_scene(scene):
    copies.require(scene in SCENES,'Unknown source scene')
    if scene == 'forest':
        item = forest.qualify_source(); source = item['source_qualification']
        item.update(effective_maps=option_maps(scene,item['effective_maps']['native']),
            requested_options={'original':item['requested_options']['native'],
                'fxaa-off':item['requested_options']['native'] | {'FXAA':'false'}})
        return item
    item = canonical.qualify_source(); q = item['qualification']
    maps = option_maps(scene,q['effective_option_maps']['native'])
    source = dict(archive_sha256=canonical.ORIGINAL_SHA256,complete_member_sha256=q['complete_archive_member_sha256'],
        complete_member_map_sha256=matrix.canonical_options_hash(q['complete_archive_member_sha256']),license_sha256=q['license_sha256'],
        source_files_sha256=q['source_files_sha256'],source_world_sha256=WORLD_SHA256[scene],canonical_control=item['control'],
        historical_source_manifest_world_sha256=q['source_provenance']['source_world_sha256'],
        original_source_dh=None,unmodified_original=True,attribution=q['attribution'])
    return dict(profile=item['source'],world=item['world'],archive=item['archive'],control=item['control'],source_qualification=source,
        effective_maps=maps,requested_options={'original':item['requested_options']['native'],
            'fxaa-off':item['requested_options']['native'] | {'FXAA':'false'}})


def maintained_sources():
    paths = [ROOT / 'scripts/prepare_photon_fxaa_quality.py',ROOT / 'tests/test_photon_fxaa_quality.py',
        ROOT / 'docs/PHOTON_PIXEL_RECONSTRUCTION_BUDGET.md']
    for module in tuple(sys.modules.values()):
        name = getattr(module,'__file__',None)
        if name:
            path = Path(name).resolve()
            if path.suffix == '.py' and path.is_relative_to(ROOT / 'scripts'): paths.append(path)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def camera_source_contract():
    path = ROOT / 'src/devClient/java/com/pp/shaderlod/dev/CameraQualitySession.java'
    source = path.read_text()
    for token in ('Sequence must last 20-30 seconds','Need 2-32 waypoints','Waypoint segments must last >= 0.25 seconds',
        'warmupSeconds', 'pngCompressionLevel','maximumInFlightImages','expectedCameraFluid','rejectFullBlockCamera'):
        copies.require(token in source,'Current camera request API unsupported/different: ' + token)
    comparison = ROOT / 'src/devClient/java/com/pp/shaderlod/dev/ComparisonClient.java'
    copies.require('client.options.hideGui = cameraQualityRequested || historyCaptureRequested;' in comparison.read_text(),
        'Current camera-mode GUI ownership unsupported')
    return dict(path=str(path),sha256=file_hash(path),comparison_path=str(comparison),comparison_sha256=file_hash(comparison),
        supported_request=validate_camera_request(camera_request('forest'),'forest'),
        full_configuration_export='ConfigurationFilesEvidence' in source,direct_artifact_export='LoadedArtifactEvidence' in source,
        loaded_effect_export='ShaderLodEffectConfig.snapshot()' in source,
        scope='Source API/wiring only; no compiled parser, actual loaded state, motion or pass absence inferred')


def qualify_runtime_receipt(path,sha256):
    path = Path(path).resolve()
    copies.require(path.parent.parent == ROOT / 'build' and path.parent.name.endswith(('-r9','-r9b')),'Explicit current R9 independent receipt required')
    external,dirs = baseline.coverage.qualify_receipt(path,sha256)
    jar = ROOT / 'build/libs/shaderlod-0.1.0-dev.jar'
    pin = external['receipt']['independent_evidence_files_sha256'].get(str(jar))
    copies.require(isinstance(pin,str) and len(pin) == 64 and file_hash(jar) == pin,'Explicit current production JAR from independent R9 receipt differs')
    return external,dirs,dict(path=str(jar),sha256=pin)


def validate_manifest(manifest,native):
    scene,mode = manifest.get('scene'),manifest.get('mode')
    copies.require(scene in SCENES and mode in MODES and manifest.get('schema') == 1
        and manifest.get('configuration_only') is False and manifest.get('source_world_sha256') == WORLD_SHA256[scene],'Scene/mode/world manifest differs')
    source = manifest.get('source_qualification',{}); model = manifest.get('experiment_arms',{}).get('capture',{})
    members = source.get('complete_member_sha256',{})
    copies.require(len(members) == 531 and matrix.canonical_options_hash(members) == source.get('complete_member_map_sha256')
        and source.get('archive_sha256') == model.get('archive_sha256') == canonical.ORIGINAL_SHA256,'Original complete source receipt differs')
    options = option_maps(scene,native)[mode]
    copies.require(model.get('expected_effective_options') == options and model.get('expected_effective_options_sha256') == MAP_SHA256[scene][mode],
        'Complete FXAA arm map/hash differs')
    copies.require(manifest.get('control') == source.get('canonical_control',{}) | {'enabled':True}
        and model.get('requested_main_mod_present') is True and model.get('dh_present') is False,'Complete scene/core/noDH controls differ')
    return dict(scene=scene,mode=mode,member_count=531,option_count=366)


def validate_pairs(rows):
    copies.require([(r['scene'],r['mode']) for r in rows] == [(s,m) for s in SCENES for m in MODES],'Missing/reordered FXAA controls')
    for scene in SCENES:
        signatures = []
        for row in [r for r in rows if r['scene'] == scene]:
            settings = row['prepared_writable_inventory']; selected = dict(settings['sidecar']['requested']); selected.pop('FXAA',None)
            signatures.append(dict(settings=settings['settings'],identities=settings['nonshader_identities'],login=settings['login_caches'],
                other_requested_shader_options=selected,defaults=settings['sidecar']['defaults'],camera_request=row['camera_request']))
        copies.require(signatures[0] == signatures[1],'Complete paired controls differ beyond FXAA: ' + scene)
    return dict(complete_other_settings_match=True,permitted_shader_options=['FXAA'])


def prepare(receipt_path,receipt_sha256,output=OUTPUT,build=BUILD):
    operation = plan(output,build); matrix.require_exclusive_runtime()
    scenes = {s:qualify_scene(s) for s in SCENES}; external,dirs,jar = qualify_runtime_receipt(receipt_path,receipt_sha256)
    api = camera_source_contract()
    copies.require(api['full_configuration_export'] and api['direct_artifact_export'] and api['loaded_effect_export'],
        'Required R9 camera boundary configuration/artifact/effect source exports missing')
    frozen = maintained_sources(); compiled = matrix.development_fingerprints()
    runtime = baseline.control_prep.current_runtime_sources(); output,build = Path(operation['output']),Path(operation['build'])
    with ExitStack() as stack:
        for scene,item in scenes.items():
            stack.enter_context(unlocked_world(item['world']))
            copies.require(world_fingerprint(item['world']) == WORLD_SHA256[scene],'Canonical stopped save differs: ' + scene)
        output.mkdir(parents=True); build.mkdir(parents=True,exist_ok=True); marker = output / MARKER
        state = EVIDENCE | dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            scene_qualifications={s:item['source_qualification'] for s,item in scenes.items()},camera_source_contract=api,
            maintained_sources_sha256=frozen,runtime_sources_sha256=runtime,development_runtime_inputs=compiled,
            independent_artifact_receipt=external,auxiliary_directory_identities=dirs,production_jar=jar,
            launch_plan=[],launch_authorized=False,owned_live_processes=[],postanalysis_plan=postanalysis_plan())
        copies.write_json(marker,state)
        try:
            inventories = {}
            for scene,item in scenes.items():
                inventories[scene] = {mode:parser.discover(item['archive'],item['requested_options'][mode],build,scene + '-' + mode) for mode in MODES}
                for mode in MODES:
                    copies.require(inventories[scene][mode]['options'] == item['effective_maps'][mode]
                        and inventories[scene][mode]['option_count'] == 366,'Full actual Iris discovery differs: ' + scene + '/' + mode)
            inventory_path = build / 'option-inventory.json'; copies.write_json(inventory_path,inventories)
            state['complete_option_inventory'] = dict(path=str(inventory_path),sha256=file_hash(inventory_path))
            for scene,item in scenes.items():
                request = camera_request(scene); request_path = output / scene / 'camera-request.json'
                request_path.parent.mkdir(parents=True,exist_ok=True)
                copies.write_json(request_path,request)
                for mode in MODES:
                    base = output / scene / mode; base.mkdir(parents=True); profile = base / 'capture'
                    control = item['control'] | {'enabled':True}; copies.write_json(base / 'control.json',control)
                    expected = dict(archive=item['archive'].name,archive_sha256=canonical.ORIGINAL_SHA256,
                        requested_options=item['requested_options'][mode],expected_effective_options=inventories[scene][mode]['options'],
                        expected_effective_options_sha256=inventories[scene][mode]['sha256'],requested_main_mod_present=True,
                        main_mod_presence_verified=False,global_enabled=True,dh_present=False)
                    manifest = EVIDENCE | dict(schema=1,scene=scene,mode=mode,configuration_only=False,source_world_sha256=WORLD_SHA256[scene],
                        control=control,experiment_arms={'capture':expected},source_qualification=item['source_qualification'])
                    validate_manifest(manifest,item['effective_maps']['original']); copies.write_json(base / 'workload-manifest.json',manifest)
                    storage_copytree(item['profile'],profile,ignore=shutil.ignore_patterns(*canonical.TRANSIENT))
                    dh = item['source_qualification']['original_source_dh']
                    if dh:
                        copied = profile / 'mods' / Path(dh['path']).name
                        copies.require(file_hash(copied,'sha512') == dh['sha512'],'Copied source DH differs'); copied.unlink()
                    copies.require(not any((profile / 'mods').glob('*.jar')),'Extra profile mod/noDH scope differs')
                    (profile / 'saves').mkdir(exist_ok=True)
                    storage_copytree(item['world'],profile / 'saves' / WORLD,ignore=shutil.ignore_patterns('session.lock'))
                    packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True); archive = packs / item['archive'].name; shutil.copy2(item['archive'],archive)
                    archive.with_name(archive.name + '.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(expected['requested_options'].items())))
                    iris = matrix.read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive.name
                    (profile / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                    (profile / 'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('adaptive'))
                    effects = baseline.coverage.effects_config('inherit'); forest.validate_effects(effects)
                    (profile / 'config/shaderlod-effects-client.toml').write_text(effects)
                    snapshot = baseline.profile_snapshot(profile)
                    copies.require(snapshot['copied_world_sha256'] == WORLD_SHA256[scene]
                        and snapshot['inputs']['shader_sha256'] == canonical.ORIGINAL_SHA256,'Copied save/source drift')
                    state['launch_plan'].append(dict(scene=scene,mode=mode,profile=str(profile),command=command(profile,request_path),
                        camera_request=request,camera_request_path=str(request_path),camera_request_sha256=file_hash(request_path),
                        expected=expected,inputs=snapshot['inputs'],prepared_profile=snapshot,
                        prepared_writable_inventory=snapshot['prepared_writable_inventory'],owner='root',status='not-launched'))
            for scene,item in scenes.items(): copies.require(world_fingerprint(item['world']) == WORLD_SHA256[scene],'Canonical save changed during copying')
            copies.require(maintained_sources() == frozen and baseline.control_prep.current_runtime_sources() == runtime
                and matrix.development_fingerprints() == compiled,'Source/classes drift during preparation')
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
        and matrix.development_fingerprints() == state['development_runtime_inputs'],'Complete runtime source/compiled roots drift')
    entry = state['independent_artifact_receipt']
    copies.require(qualify_runtime_receipt(entry['path'],entry['sha256']) == (entry,state['auxiliary_directory_identities'],state['production_jar']),'Current R9 artifacts/JAR drift')
    copies.require(camera_source_contract() == state['camera_source_contract'],'Current camera API/evidence surface drift')
    item = state['complete_option_inventory']; copies.require(file_hash(Path(item['path'])) == item['sha256'],'Actual complete Iris inventory drift')
    inventories = json.loads(Path(item['path']).read_text())
    for scene,source in state['scene_qualifications'].items():
        for name,pin in source['source_files_sha256'].items(): copies.require(file_hash(Path(name)) == pin,'Canonical source drift: ' + name)
        profile = forest.SOURCE if scene == 'forest' else canonical.SOURCE
        copies.require(world_fingerprint(profile / 'saves' / WORLD) == WORLD_SHA256[scene],'Canonical stopped world drift')
    for row in state['launch_plan']:
        scene,mode,profile = row['scene'],row['mode'],Path(row['profile']); discovery = inventories[scene][mode]
        copies.require(profile == OUTPUT / scene / mode / 'capture','Owned scene/profile association drift')
        maps = option_maps(scene,inventories[scene]['original']['options'])
        copies.require(discovery['options'] == maps[mode] and discovery['option_count'] == 366,'Full actual map drift')
        for name,pin in discovery['parser_dependency_sha256'].items(): copies.require(file_hash(Path(name)) == pin,'Pinned Iris dependency drift')
        copies.require(file_hash(Path(discovery['log'])) == discovery['log_sha256'],'Iris discovery log drift')
        request = Path(row['camera_request_path'])
        copies.require(request == OUTPUT / scene / 'camera-request.json' and file_hash(request) == row['camera_request_sha256']
            and json.loads(request.read_text()) == row['camera_request'],'Immutable camera request drift')
        validate_camera_request(row['camera_request'],scene)
        copies.require(row['command'] == command(profile,request) and baseline.profile_snapshot(profile) == row['prepared_profile'],'Fresh profile/source/settings/world/argv drift')
        copies.require(not (profile / 'camera-quality').exists() and not (profile / 'workload-measurement.json').exists(),'Profile used; preserve captures')
        manifest = json.loads((profile.parent / 'workload-manifest.json').read_text()); validate_manifest(manifest,maps['original'])
        copies.require(row['expected'] == manifest['experiment_arms']['capture'],'Manifest row drift')
        forest.validate_effects((profile / 'config/shaderlod-effects-client.toml').read_text())
    copies.require(validate_pairs(state['launch_plan']) == state['prepared_pairs_comparison'],'Paired settings drift')
    return EVIDENCE | dict(status='verified-four-current-R9-unused-FXAA-camera-controls',launch_authorized=False,owned_live_processes=[])


def postanalysis_plan():
    return dict(root_runtime=['Only root launches; current R9 receipt/main JAR and exact source/class roots; actual native2976x1672 hidden GUI, stable focus/pacing and no competing sessions/CPU/GPU timing/probes.',
        'Four complete CameraQuality manifests, each101 expected slots, no skipped/failed callback, full PNG hashes/CRC/native extent, writer drained before client stop, request unchanged and server daylight/weather frozen.',
        'Both boundaries full366 loaded options exact; only FXAA differs within each scene; source archive531/LICENSE unchanged; camera path at actual partial tick, fluid NONE, collision guard, first-person/spectator, no reload.',
        'Current producer must export or separately qualify required direct loaded artifacts/full capture-time configuration/effects/window evidence. Legacy pre/post filesystem/config aliases are insufficient substitutes. Unknown evidence remains unaccepted.',
        'Actual composite16 absence with FXAA off requires runtime pass submission evidence if desired; source option/properties only predicts it. No new observer enabled by this recipe.'],
        visual=['Still review at held start0-3s and returned18-20s; moving thin foliage/grass, hard edges/shoreline/water speculars, disocclusion, shimmer and halos.',
            '5Hz PNG sampling can miss faster flicker; fixed world day and nominal route do not align shader frame/jitter/time/history across sessions. No parity metric or smooth-video guarantee.'],
        stopping=['Stop immediately on scene/pipeline/option/artifact/config/frame/route/PNG failure; retain raw output.',
            'Reject conspicuous quality regression before any quiet native-on/off/on cost bracket; no cost profiles or mod adaptation prepared.'])


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--dry-run',action='store_true'); p.add_argument('--verify',type=Path)
    p.add_argument('--artifact-receipt',type=Path); p.add_argument('--artifact-sha256'); args = p.parse_args()
    if args.verify:
        copies.require(args.verify.resolve() == OUTPUT / MARKER,'Only owned marker allowed'); matrix.require_exclusive_runtime()
        result = verify(json.loads(args.verify.read_text()))
    elif args.dry_run: result = plan()
    else:
        copies.require(args.artifact_receipt is not None and args.artifact_sha256 is not None,'Explicit current R9 receipt path and SHA-256 required')
        state = prepare(args.artifact_receipt,args.artifact_sha256)
        result = dict(status=state['status'],marker=str(OUTPUT / MARKER),marker_sha256=file_hash(OUTPUT / MARKER),
            profiles=4,launch_authorized=False,owned_live_processes=[])
    print(json.dumps(result,indent=2,sort_keys=True))


if __name__ == '__main__': main()
