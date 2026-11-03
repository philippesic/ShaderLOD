#!/usr/bin/env python3
"""Prepare one original-Photon H40/Z20 versus H30/Z15 gross-motion pair; never launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree
import argparse
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import shutil
import sys

import prepare_photon_fxaa_quality_r2 as camera
from prepare_reduced_effects_profiles import require,write_json
from setup_comparison import ROOT,WORLD,file_hash,world_fingerprint,unlocked_world

baseline,canonical,parser,matrix=camera.baseline,camera.canonical,camera.parser,camera.matrix
OUTPUT=ROOT/'run-workloads/cloud-global-quality-r1'
BUILD=ROOT/'build/cloud-global-quality-r1'
MARKER='cloud-global-quality-preparation.json'
MODES=('original','primary30-15')
RECEIPT=ROOT/'build/camera-quality-integrated-artifacts-r10/artifact-receipt.json'
RECEIPT_SHA256='c6ede25dec54edd0b3e9f85b02481dcf4d860419620dad6c18a6ddbcb75e6e06'
ADVISOR=ROOT/'build/cloud-global-sample-calibration-r1/evidence.json'
FINAL='TWO_UNUSED_CURRENT_R10_GLOBAL_CLOUD_PRIMARY_MOTION_CONTROLS_READY_ROOT_REVIEW'
EVIDENCE=dict(diagnostic_only=True,performanceEvidence=False,adapter_gain=False,runtime='unverified',quality='unaccepted',
    motion='gross-motion-only-3Hz',general_motion_quality_accepted=False,density_error_bound=False,
    deferred1_only_optimization=False,stock_option_calibration=True,shader_support_claim=False)


def camera_request():
    return camera.camera_request('coastalwater')|{'label':'photon-cloud-global-primary-coastalwater-r1'}


def validate_camera_request(request):
    require(request==camera_request(),'Matched camera route/clock differs')
    original=request|{'label':camera.camera_request('coastalwater')['label']}
    return camera.validate_camera_request(original,'coastalwater')


def command(profile,request):
    return camera.command(profile,request)


def plan(output=OUTPUT,build=BUILD,root=ROOT):
    root=Path(root).resolve();output,build=Path(output),Path(build)
    require(not output.is_symlink() and not build.is_symlink(),'Symlink output unsupported');output,build=output.resolve(),build.resolve()
    require(output==root/'run-workloads/cloud-global-quality-r1' and build==root/'build/cloud-global-quality-r1','Only owned fresh family allowed')
    require(not output.exists() and not build.exists(),'Preserve existing output; use verify')
    return EVIDENCE|dict(schema=1,output=str(output),build=str(build),modes=list(MODES),profile_count=2,scene='coastalwater',
        route=validate_camera_request(camera_request()),framebuffer=[2976,1672],window=[1488,836],main_mod_present=True,global_enabled=True,dh_present=False,
        primary_counts={'original':[40,20],'primary30-15':[30,15]},secondary_counts=dict(direct=6,ambient=2),
        all_count_paths=['world0/deferred1.fsh -> d1_clouds -> clouds/cumulus','world0/deferred.fsh -> d0_sky_map -> sky -> clouds/cumulus -> cached sky/reflected sky'],
        client_gpu_owner='root',launch_authorized=False,owned_live_processes=[],stop_procedure=canonical.STOP,
        stop='Reject gross silhouette/grain/flicker/interior/silverlining/cachedsky/reflection/alpha damage before any matched cost; sparse3Hz cannot accept fine temporal parity')


def option_maps(native):
    require(len(native)==366 and matrix.canonical_options_hash(native)==canonical.SOURCE_OPTIONS_SHA256,'Complete original canonical map differs')
    required={'TAA':'true','TAAU':'false','FXAA':'true','CAS':'true','CAS_INTENSITY':'0.50','CLOUDS_TEMPORAL_UPSCALING':'4',
        'CLOUDS_CUMULUS_PRIMARY_STEPS_H':'40','CLOUDS_CUMULUS_PRIMARY_STEPS_Z':'20','CLOUDS_CUMULUS_LIGHTING_STEPS':'6','CLOUDS_CUMULUS_AMBIENT_STEPS':'2'}
    require(all(native.get(k)==v for k,v in required.items()),'Protected original TAA/AA/cloud controls differ')
    return {'original':dict(native),'primary30-15':native|{'CLOUDS_CUMULUS_PRIMARY_STEPS_H':'30','CLOUDS_CUMULUS_PRIMARY_STEPS_Z':'15'}}


def validate_maps(original,tuned):
    expected=option_maps(original)['primary30-15'];require(tuned==expected and len(tuned)==366,'Not exact two-primary-option delta')
    require({k for k in original if original[k]!=tuned[k]}=={'CLOUDS_CUMULUS_PRIMARY_STEPS_H','CLOUDS_CUMULUS_PRIMARY_STEPS_Z'},'Unexpected count/secondary/AA delta')


def maintained_sources():
    paths=[ROOT/'scripts/prepare_photon_cloud_global_quality.py',ROOT/'tests/test_photon_cloud_global_quality.py',
        ROOT/'docs/PHOTON_CLOUD_GLOBAL_SAMPLE_CALIBRATION.md',ADVISOR]
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            p=Path(name).resolve()
            if p.suffix=='.py' and p.is_relative_to(ROOT/'scripts'):paths.append(p)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def prepare(output=OUTPUT,build=BUILD,artifact_receipt=RECEIPT,artifact_sha256=RECEIPT_SHA256):
    operation=plan(output,build);matrix.require_exclusive_runtime();scene=canonical.qualify_source();maps=option_maps(scene['qualification']['effective_option_maps']['native'])
    require(artifact_sha256==RECEIPT_SHA256,'Approved current R10 receipt required')
    external,dirs,jar=camera.qualify_runtime_receipt(artifact_receipt,artifact_sha256)
    api=camera.camera_source_contract()
    require(all(api[k] for k in ('full_configuration_export','direct_artifact_export','loaded_effect_export','quality_capture_end_export','boundary_window_export','per_png_window_export')),'Actual R10 camera metadata/window/GUI sources missing')
    advisor=json.loads(ADVISOR.read_text());require(advisor['archive_sha256']==canonical.ORIGINAL_SHA256 and advisor['effective_map_sha256']==canonical.SOURCE_OPTIONS_SHA256,'Pinned source advisor recipe differs')
    q=scene['qualification'];source=dict(archive_sha256=canonical.ORIGINAL_SHA256,complete_member_sha256=q['complete_archive_member_sha256'],
        license_sha256=q['license_sha256'],source_files_sha256=q['source_files_sha256'],unmodified_original=True,original_source_dh=None,
        consumer_graph_provenance=dict(path=str(ADVISOR),sha256=file_hash(ADVISOR),references=advisor['graph_references']),attribution=q['attribution'])
    require(len(source['complete_member_sha256'])==531,'Original complete source inventory differs')
    frozen=maintained_sources();runtime=baseline.control_prep.current_runtime_sources();compiled=matrix.development_fingerprints()
    output,build=Path(operation['output']),Path(operation['build'])
    with unlocked_world(scene['world']):
        fingerprint=world_fingerprint(scene['world']);require(fingerprint==baseline.coverage.WORLD_SHA256,'Canonical stopped world differs')
        output.mkdir(parents=True);build.mkdir(parents=True);marker=output/MARKER
        state=EVIDENCE|dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            source_qualification=source,source_world_sha256=fingerprint,independent_artifact_receipt=external,auxiliary_directory_identities=dirs,
            production_jar=jar,camera_source_contract=api,maintained_source_files_sha256=frozen,runtime_sources_sha256=runtime,
            development_runtime_inputs=compiled,launch_plan=[],launch_authorized=False,owned_live_processes=[],postanalysis_plan=postanalysis_plan())
        write_json(marker,state)
        try:
            requested={'original':scene['requested_options']['native'],'primary30-15':scene['requested_options']['native']|{'CLOUDS_CUMULUS_PRIMARY_STEPS_H':'30','CLOUDS_CUMULUS_PRIMARY_STEPS_Z':'15'}}
            inventories={mode:parser.discover(scene['archive'],requested[mode],build,'actual-'+mode) for mode in MODES}
            for mode in MODES:require(inventories[mode]['options']==maps[mode] and inventories[mode]['option_count']==366,'Actual complete Iris366 discovery differs: '+mode)
            validate_maps(inventories['original']['options'],inventories['primary30-15']['options'])
            write_json(build/'option-inventory.json',inventories);state['complete_option_inventory']=dict(path=str(build/'option-inventory.json'),sha256=file_hash(build/'option-inventory.json'))
            request=camera_request();request_path=output/'camera-request.json';write_json(request_path,request)
            for mode in MODES:
                base=output/mode;base.mkdir();profile=base/'capture';control=scene['control']|{'enabled':True};write_json(base/'control.json',control)
                expected=dict(archive=scene['archive'].name,archive_sha256=canonical.ORIGINAL_SHA256,requested_options=requested[mode],
                    expected_effective_options=inventories[mode]['options'],expected_effective_options_sha256=inventories[mode]['sha256'],
                    requested_main_mod_present=True,main_mod_presence_verified=False,global_enabled=True,dh_present=False)
                write_json(base/'workload-manifest.json',EVIDENCE|dict(schema=1,scene='coastalwater',mode=mode,configuration_only=False,
                    source_world_sha256=fingerprint,control=control,experiment_arms={'capture':expected},source_qualification=source))
                storage_copytree(scene['source'],profile,ignore=shutil.ignore_patterns(*canonical.TRANSIENT));(profile/'saves').mkdir(exist_ok=True)
                storage_copytree(scene['world'],profile/'saves'/WORLD,ignore=shutil.ignore_patterns('session.lock'));require(not any((profile/'mods').glob('*.jar')),'Extra noDH/minimal mods')
                packs=profile/'shaderpacks';packs.mkdir(exist_ok=True);archive=packs/scene['archive'].name;shutil.copy2(scene['archive'],archive)
                archive.with_name(archive.name+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(requested[mode].items())))
                iris=matrix.read_option_properties((profile/'config/iris.properties').read_text());iris['shaderPack']=archive.name
                (profile/'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                (profile/'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('adaptive'))
                effects=baseline.coverage.effects_config('inherit');camera.forest.validate_effects(effects);(profile/'config/shaderlod-effects-client.toml').write_text(effects)
                snapshot=baseline.profile_snapshot(profile);require(snapshot['copied_world_sha256']==fingerprint,'Copied world differs')
                state['launch_plan'].append(dict(mode=mode,profile=str(profile),command=command(profile,request_path),camera_request=request,
                    camera_request_path=str(request_path),camera_request_sha256=file_hash(request_path),expected=expected,inputs=snapshot['inputs'],
                    prepared_profile=snapshot,prepared_writable_inventory=snapshot['prepared_writable_inventory'],owner='root',status='not-launched'))
            require(world_fingerprint(scene['world'])==fingerprint and maintained_sources()==frozen,'Canonical source/recipe drift during preparation')
            require(baseline.control_prep.current_runtime_sources()==runtime and matrix.development_fingerprints()==compiled,'Source/classes changed')
            state['status']=FINAL;write_json(marker,state);verify(state)
        except BaseException as failure:state.update(status='FAILED',error=str(failure));write_json(marker,state);raise
    return state


def verify(state):
    require(state.get('status')==FINAL,'Incomplete preparation');matrix.require_exclusive_runtime()
    for field in ('maintained_source_files_sha256','runtime_sources_sha256'):
        for name,pin in state[field].items():require(file_hash(Path(name))==pin,'Frozen source drift: '+name)
    require(matrix.development_fingerprints()==state['development_runtime_inputs'],'Current roots drift');ext=state['independent_artifact_receipt']
    require(camera.qualify_runtime_receipt(ext['path'],ext['sha256'])==(ext,state['auxiliary_directory_identities'],state['production_jar']),'Current R10 artifacts/JAR drift')
    require(camera.camera_source_contract()==state['camera_source_contract'],'Current source camera schema changed')
    inventory=state['complete_option_inventory'];require(file_hash(Path(inventory['path']))==inventory['sha256'],'Full option inventory drift')
    require([r['mode'] for r in state['launch_plan']]==list(MODES),'Original/tuned order changed')
    for row in state['launch_plan']:
        profile=Path(row['profile']);require(baseline.profile_snapshot(profile)==row['prepared_profile'] and command(profile,row['camera_request_path'])==row['command'],'Fresh profile/world/source/settings/argv drift')
        require(file_hash(Path(row['camera_request_path']))==row['camera_request_sha256'],'Exact camera request changed');validate_camera_request(row['camera_request'])
        require(not (profile/'camera-quality').exists(),'Capture profile already used; preserve output')
    validate_maps(state['launch_plan'][0]['expected']['expected_effective_options'],state['launch_plan'][1]['expected']['expected_effective_options'])
    return EVIDENCE|dict(status='verified-two-unused-original-cloud-count-quality-controls',owned_live_processes=[])


def postanalysis_plan():
    return dict(gates=['Root owns serial original thenH30Z15 camera clients and exactinitialbirth/cwd/VMargs/source/sourceclass/R10/artifact/jar/terminal receipts; no CPU/GPU timings/encoders/probes for speed claim',
        'Actual full531source/366maps with ONLY2countoptions different; TAAtrue/TAAUfalse/FXAAtrue/CAS.50/divisor4/secondary6+2/otherlayers7effects fixed. Sourceallconsumer graphincludesD1 andD0cachedsky andreflection paths; no deferred1-only/densityerrorbound or25%retiredwork inference',
        '61/61 PNG slots/opaque native2976x1672/hashes/3Hz/30warm20duration/comp1/max2; everyactual R10 perPNG windowfocused/visible/noniconified/hiddenGUI/nativelogical/pacing/no skipped/backpressure slots. Writercompletion/drain/error state verified, sparse3Hzgross only',
        'Four rawsource/settings/window/effect metadata boundaries initial/capture_start/capture_end/end exactsource/loadedoptions/config/directinitial+endartifact current10mod9root/4dirs/camera/pose/scene/time12500/clear/wetness0/native/radius/noDH/no fallback; normalizeknownstartup nullableoriginal_source_dh onlybyactualsourceprovenance ifneeded, retainrawfailure',
        'Rootinspect silverlinings/silhouette/cloudinteriorgrain/alpha/cachedsky/reflectedsky/crepuscularlighting heldstills+slowpan motion forpopping/flicker/disocclusion. CameraRoute staysFXAAR2, overhead/storm/fineframeto-frame qualitynotaccepted. Unsynchronizedwind/pig/watermotion notcausalpixelparity',
        'Rejectconspicuousloss before anymatchedoriginal/tuned/original cost; no mandatorythirdqualityclient. Onlymeaningfulrepeatedoriginalframe/GPU gainwithqualitybeforeanadaptivemoddesign; stockqualityoption calibration not shaderLODframework/adapter/support/default promotion'],performanceEvidence=False)


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--prepare',action='store_true');ap.add_argument('--dry-run',action='store_true');ap.add_argument('--verify',type=Path)
    ap.add_argument('--artifact-receipt',type=Path,default=RECEIPT);ap.add_argument('--artifact-sha256',default=RECEIPT_SHA256);a=ap.parse_args()
    try:
        require(sum(bool(x) for x in (a.prepare,a.dry_run,a.verify))==1,'Select prepare/dry-run/verify')
        result=verify(json.loads(a.verify.read_text())) if a.verify else plan() if a.dry_run else prepare(artifact_receipt=a.artifact_receipt,artifact_sha256=a.artifact_sha256)
        print(json.dumps(result,indent=2,sort_keys=True))
    except (OSError,ValueError,KeyError,TypeError) as failure:ap.exit(1,'Cannot prepare global cloud quality: '+str(failure)+'\n')


if __name__=='__main__':main()
