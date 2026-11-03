#!/usr/bin/env python3
"""Prepare original/OFF/ON water sampler controls; cost feature remains blocked."""
from __future__ import annotations
from run_storage import copytree as storage_copytree
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import sys
import zipfile

import build_photon_water_shadow_zero as adapter
import prepare_photon_cloud_tail_diagnostic as gui
import prepare_photon_shadow_atlas_probe as probe_prep
from prepare_reduced_effects_profiles import require, write_json
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

baseline, canonical, parser, matrix = gui.baseline,gui.canonical,gui.live,gui.matrix
OUTPUT=ROOT/'run-workloads/water-shadow-zero-runtime-r1'
BUILD=ROOT/'build/water-shadow-zero-runtime-r1'
FREEZE=ROOT/'build/water-shadow-zero-source-checks/freeze.json'
FREEZE_SHA256='7bb171d2d3bacbdb27d34f9f50779a817e7373d6445bcefe3663084aba1bf2ee'
ARCHIVE=ROOT/'build/water-shadow-zero-enabled.zip'
ARCHIVE_SHA256='eb411fa4a5ca13adb222a5e9e96b46b587d4f79538758c8035fca524156b4983'
RECEIPT=ROOT/'build/core-frame-integrated-artifacts-r6/artifact-receipt.json'
RECEIPT_SHA256='d8f80b1c79ba40d25dc14c281778211e8202bb1c1c1604d9dab3e77762d0af81'
JAR=ROOT/'build/libs/shaderlod-0.1.0-dev.jar'
JAR_SHA256='5f84d03813de4e6f7c70d2d2c95f85a404603975d6dae2b4dc8be775778c2b03'
MARKER='water-shadow-zero-runtime-preparation.json'
STAGED='THREE_FRESH_SOURCE_CONTROLS_STAGED_R7_RUNTIME_RECEIPT_PENDING'
FINAL='THREE_FRESH_SOURCE_CURRENT_RUNTIME_CONTROLS_READY_ROOT_REVIEW_PENDING'
ARMS=('original','global-off','global-on')
OFF=tuple(k for k in gui.OFF if k!='cloudSamplerProbe')
EVIDENCE=dict(diagnostic_only=True,performanceEvidence=False,runtime='unverified',quality='unaccepted',
    motion='unverified',executed_skip_coverage='unobserved',stage_identity='unknown numeric program/draw origin only',
    effect_consumption='existing core2/global only; no effect/capability consumption claim')


def command(profile):
    argv=['./gradlew','runClientLodOn','-PrunDirectory='+str(Path(profile).resolve()),'-PperformanceStack=minimal',
        '-PincludeDh=false','-PmeasureWorkload=true','-PmeasureOnly=true','-PinitialWarmup=60','-PmeasurementSeconds=8',
        '-PbenchmarkWindowScale=2','-PhideWorkloadGui=true','-PcomparisonFullscreen=false','-PfixedWetness=0',
        '-PgpuSampleInterval=5','-PcameraQuality=','-PcloudSamplerProbe=true']
    argv+=['-P'+key+'=false' for key in OFF]
    for task in canonical.BUILD_TASKS:argv+=['-x',task]
    return argv


def plan(output=OUTPUT,build=BUILD,root=ROOT):
    root=Path(root).resolve();output,build=Path(output),Path(build)
    require(not output.is_symlink() and not build.is_symlink(),'Symlink output unsupported')
    output,build=output.resolve(),build.resolve()
    require(output==root/'run-workloads/water-shadow-zero-runtime-r1' and build==root/'build/water-shadow-zero-runtime-r1','Only owned fresh family allowed')
    require(not output.exists() and not build.exists(),'Preserve previous outputs; use verify')
    return EVIDENCE|dict(schema=1,output=str(output),build=str(build),arms=list(ARMS),initial_warmup_seconds=60,
        measurement_seconds=8,framebuffer=[2976,1672],window=[1488,836],requested_chunks=24,taau=False,vps=False,
        shadow_map_resolution=2048,main_mod_present=True,dh_present=False,client_gpu_owner='root',launch_authorized=False,
        global_enabled={'original':False,'global-off':False,'global-on':True},stop_procedure=canonical.STOP,
        sampler_contract='shadowtex1 actual raster alias: LINEAR min/mag, zero LOD bias, base0, min/maxLOD -1000/1000, aniso1, comparison34894/func515, depth2048; effective bound sampler overrides honored',
        quality_contract='Root inspects original/OFF/ON final stills for gross shoreline/glint/water/reflection/absorption damage. Temporal-unsynchronized stills do not establish exact pixel or wave-motion parity.',
        cost_status='BLOCKED before timing: parent is integrating explicit water_shadow_zero guards; new runtime receipt and actual driver/sampler/parity acceptance pending')


def qualify_archive():
    require(file_hash(FREEZE)==FREEZE_SHA256,'Accepted water source freeze changed')
    freeze=json.loads(FREEZE.read_text())
    for field in ('source_sha256','evidence_sha256'):
        for name,pin in freeze[field].items():require(file_hash(ROOT/name)==pin,'Water source/evidence drift: '+name)
    require(file_hash(ROOT/'build/water-shadow-zero-source-checks-r4.log')==freeze['test_log_sha256'],'Water source tests log changed')
    require(file_hash(ARCHIVE)==ARCHIVE_SHA256,'Exact enabled water archive changed')
    original=adapter.archive_members(ROOT/adapter.BASE);actual=adapter.archive_members(ARCHIVE)
    manifest=json.loads((ROOT/adapter.MANIFEST).read_text());expected=adapter.patch_members(original,manifest,'enabled')
    require(set(actual)==set(expected)|{adapter.METADATA} and all(actual[k]==v for k,v in expected.items()),'Complete water source/member assembly differs')
    metadata=json.loads(actual[adapter.METADATA]);require(metadata['arm']=='enabled' and metadata['manifest_sha256']==file_hash(ROOT/adapter.MANIFEST),'Water metadata differs')
    require(len(original)==531 and len(actual)==533 and actual['LICENSE']==original['LICENSE'],'Original/candidate inventory/license differs')
    inventory=ROOT/'build/water-shadow-zero-source-checks/full-inventories.json';reviewed=json.loads(inventory.read_text())
    return dict(archive=str(ARCHIVE),archive_sha256=ARCHIVE_SHA256,source_freeze=dict(path=str(FREEZE),sha256=FREEZE_SHA256),
        complete_member_sha256={k:hashlib.sha256(v).hexdigest() for k,v in sorted(actual.items())},metadata=metadata,
        original_member_sha256={k:hashlib.sha256(v).hexdigest() for k,v in sorted(original.items())},
        reviewed_inventory=dict(path=str(inventory),sha256=file_hash(inventory),canonical_original=reviewed['canonical-original']['options'],canonical_enabled=reviewed['canonical-enabled']['options']))


def validate_maps(original,candidate):
    require(len(original)==366 and len(candidate)==368 and matrix.canonical_options_hash(original)==canonical.SOURCE_OPTIONS_SHA256,'Original canonical map differs')
    require(candidate==original|{adapter.SWITCH:'true','SHADERLOD_WATER_SHADOW_ZERO_INCLUDED':'true'},'Water complete original-plus-two map differs')
    require(original.get('TAAU')=='false' and original.get('SHADOW_VPS')=='false' and original.get('shadowMapResolution')=='2048','Protected native/VPS/allocation source options differ')


def maintained_sources():
    paths=[ROOT/'scripts/prepare_photon_water_shadow_zero_runtime.py',ROOT/'tests/test_photon_water_shadow_zero_runtime.py']
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            path=Path(name).resolve()
            if path.suffix=='.py' and path.is_relative_to(ROOT/'scripts'):paths.append(path)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def cost_source_plan():
    path=ROOT/'src/devClient/java/com/pp/shaderlod/dev/BenchmarkSession.java'
    return EVIDENCE|dict(status='BLOCKED_WATER_COST_RUNTIME_AND_DRIVER_ACCEPTANCE_PENDING',feature='water_shadow_zero',phases=[False,True,False,True],
        benchmark_source_path=str(path),source_guard_owner='pixel_receipt_fix; new source branch in progress, uncompiled/unaccepted at source stage',profile_created=False,launch_authorized=False,
        requested_recipe=dict(initial_warmup_seconds=30,settle_seconds=8,measurement_seconds=20,gpu_sample_interval=5,
            gpu_timing=True,coarse_cpu=True,cloudSamplerProbe=False,all_other_probes=False,minimum_positive_unique_gpu_samples=60,
            maximum_off_frame_and_gpu_drift=.10,minimum_each_adjacent_on_frame_reduction=.05),
        prerequisites=['Driver/source/full366/368 options/currentr6artifacts/capturedconfiguration/actualhiddenGUI/nativewindow/time/focus sampler/readback gates pass',
            'Sampler conditions qualify actual original/OFF/ON; linkedcore2/global0or1 in candidate bound-shadowtex1 raster source-compatible programs. Numericstage unknowns stay unknown',
            'Root accepts gross still parity; exact gate source is not executed skip coverage, coherent retired work, performance or motion proof',
            'Parent accepts newly allocated explicit water_shadow_zero BenchmarkSession/WorkloadInputs source/options/config/artifact/GUI/epoch guards; do not reuse live_pixel/global guards',
            'Fresh unused same-JVM profile/new artifact receipt after any guard integration; all diagnosticsOFF for cost, strict scoped four-coarse-clock coverage and fullGPU N5 required'],
        stop='No timing argv or cost profile launched/prepared. Prior R6 explicitly rejected this feature; parent source owner is integrating R7. Actual driver/sampler/parity and independently accepted guard semantics remain prerequisites.')


def stage(output=OUTPUT,build=BUILD):
    operation=plan(output,build);matrix.require_exclusive_runtime();scene=canonical.qualify_source();source=qualify_archive()
    require(file_hash(JAR)==JAR_SHA256,'Current once-per-render-frame production JAR differs')
    maintained=maintained_sources()
    output,build=Path(operation['output']),Path(operation['build'])
    with unlocked_world(scene['world']):
        fingerprint=world_fingerprint(scene['world']);require(fingerprint==gui.WORLD_SHA256,'Canonical stopped coastal save differs')
        output.mkdir(parents=True);build.mkdir(parents=True);marker=output/MARKER
        state=EVIDENCE|dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            source_qualification=source,canonical_source_qualification=scene['qualification'],source_world_sha256=fingerprint,
            independent_artifact_receipt=None,auxiliary_directory_identities=None,runtime_sources_sha256=None,
            development_runtime_inputs=None,maintained_source_files_sha256=maintained,production_jar=dict(path=str(JAR),sha256=JAR_SHA256),
            launch_plan=[],launch_authorized=False,owned_live_processes=[],postanalysis_plan=postanalysis_plan())
        write_json(marker,state)
        try:
            requested=scene['requested_options']['native'];candidate_requested=requested|{adapter.SWITCH:'true'}
            original_inventory=parser.discover(scene['archive'],requested,build,'actual-original')
            candidate_inventory=parser.discover(ARCHIVE,candidate_requested,build,'actual-enabled-water')
            validate_maps(original_inventory['options'],candidate_inventory['options'])
            require(original_inventory['options']==source['reviewed_inventory']['canonical_original'] and candidate_inventory['options']==source['reviewed_inventory']['canonical_enabled'],'Reviewed complete canonical actual Iris maps differ')
            write_json(build/'option-inventory.json',dict(original=original_inventory,candidate=candidate_inventory))
            state['complete_option_inventory']=dict(path=str(build/'option-inventory.json'),sha256=file_hash(build/'option-inventory.json'))
            cost=cost_source_plan();write_json(output/'cost-source-plan.json',cost);state['cost_source_plan']=cost
            for arm in ARMS:
                enabled=arm=='global-on';is_original=arm=='original';base=output/arm;base.mkdir();profile=base/'control'
                control=scene['control']|{'enabled':enabled};write_json(base/'control.json',control)
                archive_source=scene['archive'] if is_original else ARCHIVE;selected=original_inventory if is_original else candidate_inventory
                expected=dict(archive=archive_source.name,archive_sha256=adapter.BASE_SHA256 if is_original else ARCHIVE_SHA256,
                    requested_options=requested if is_original else candidate_requested,expected_effective_options=selected['options'],
                    expected_effective_options_sha256=selected['sha256'],requested_main_mod_present=True,main_mod_presence_verified=False,
                    dh_present=False,global_enabled=enabled,expected_core=2 if not is_original else 'unknown upstream has no consumer',expected_global=int(enabled))
                qualified=dict(archive_sha256=expected['archive_sha256'],complete_member_sha256=source['original_member_sha256'] if is_original else source['complete_member_sha256'])
                write_json(base/'workload-manifest.json',EVIDENCE|dict(schema=1,scene='coastalwater',control=control,configuration_only=False,
                    source_world_sha256=fingerprint,experiment_arms={'control':expected},source_qualification=qualified))
                storage_copytree(scene['source'],profile,ignore=shutil.ignore_patterns(*canonical.TRANSIENT));(profile/'saves').mkdir(exist_ok=True)
                storage_copytree(scene['world'],profile/'saves'/WORLD,ignore=shutil.ignore_patterns('session.lock'))
                require(not any((profile/'mods').glob('*.jar')),'Extra minimal/noDH profile mods')
                packs=profile/'shaderpacks';packs.mkdir(exist_ok=True);archive=packs/archive_source.name;shutil.copy2(archive_source,archive)
                archive.with_name(archive.name+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(expected['requested_options'].items())))
                iris=matrix.read_option_properties((profile/'config/iris.properties').read_text());iris['shaderPack']=archive.name
                (profile/'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                (profile/'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('adaptive' if enabled else 'high'))
                (profile/'config/shaderlod-effects-client.toml').write_text(baseline.coverage.effects_config('inherit'))
                request=EVIDENCE|dict(run_id=output.name+'-'+arm,case='water-shadow-'+arm,task='runClientLodOn',stack='minimal',include_dh=False,
                    measurement_seconds=8,initial_warmup_seconds=60,gpu_queries=False,cpu_timing=False,cloud_sampler_probe=True,
                    sample_interval=5,fixed_wetness=0,hide_workload_gui=True,expected_framebuffer=[2976,1672],expected_window=[1488,836])
                write_json(profile/'workload-request.json',request);snapshot=baseline.profile_snapshot(profile)
                require(snapshot['copied_world_sha256']==fingerprint and snapshot['inputs']['shader_sha256']==expected['archive_sha256'],'Copied world/archive differs')
                state['launch_plan'].append(dict(arm=arm,profile=str(profile),command=command(profile),request=request,expected=expected,
                    inputs=snapshot['inputs'],prepared_profile=snapshot,prepared_writable_inventory=snapshot['prepared_writable_inventory'],owner='root',status='not-launched'))
            require(world_fingerprint(scene['world'])==fingerprint and maintained_sources()==maintained,'Canonical source/save/recipe changed')
            state['status']=STAGED;write_json(marker,state);verify_source(state)
        except BaseException as failure:state.update(status='FAILED',error=str(failure));write_json(marker,state);raise
    return state


def verify_source(state):
    require(state.get('status') in (STAGED,FINAL),'Incomplete source preparation')
    for field in ('maintained_source_files_sha256',):
        for name,pin in state[field].items():require(file_hash(Path(name))==pin,'Frozen source drift: '+name)
    require(file_hash(JAR)==JAR_SHA256,'Production main JAR drift')
    require(qualify_archive()==state['source_qualification'],'Accepted water source/archive drift')
    item=state['complete_option_inventory'];require(file_hash(Path(item['path']))==item['sha256'],'Actual complete Iris inventory drift')
    require([r['arm'] for r in state['launch_plan']]==list(ARMS),'Original/OFF/ON order drift')
    for row in state['launch_plan']:
        require(row['command']==command(row['profile']) and baseline.profile_snapshot(row['profile'])==row['prepared_profile'],'Fresh profile/source/config/world/argv drift')
        require(not (Path(row['profile'])/'workload-measurement.json').exists(),'Profile has been used; preserve previous capture')
    require(json.loads((Path(state['operation']['output'])/'cost-source-plan.json').read_text())==state['cost_source_plan']==cost_source_plan(),'Blocked cost source plan drift')
    return EVIDENCE|dict(status='verified-three-fresh-source-controls-runtime-pending',cost_status=state['cost_source_plan']['status'],owned_live_processes=[])


def finalize(marker,receipt_path,receipt_sha256):
    marker=Path(marker).resolve();require(marker==OUTPUT/MARKER,'Only owned staged marker allowed')
    state=json.loads(marker.read_text());verify_source(state);matrix.require_exclusive_runtime()
    require(state['status']==STAGED,'Already finalized; use verify and preserve original receipt')
    require(receipt_sha256!=RECEIPT_SHA256,'Old r6 receipt cannot qualify source-editing interval')
    external,directories=baseline.coverage.qualify_receipt(receipt_path,receipt_sha256)
    state.update(status=FINAL,independent_artifact_receipt=external,auxiliary_directory_identities=directories,
        runtime_sources_sha256=gui.runtime_prep.current_runtime_sources(),development_runtime_inputs=matrix.development_fingerprints())
    require(b'water_shadow_zero' in (ROOT/'build/classes/java/devClient/com/pp/shaderlod/dev/BenchmarkSession.class').read_bytes()
        and b'water_shadow_zero' in (ROOT/'build/classes/java/devClient/com/pp/shaderlod/dev/WorkloadInputs.class').read_bytes(),
        'New water feature not present in integrated bytecode; parent must separately accept full guard semantics')
    verify_source(state);write_json(marker,state);verify(state);return state


def verify(state):
    verify_source(state);require(state['status']==FINAL,'New R7 integrated independent receipt required before launch')
    matrix.require_exclusive_runtime()
    require(gui.runtime_prep.current_runtime_sources()==state['runtime_sources_sha256'],'Final runtime source freeze drift')
    require(matrix.development_fingerprints()==state['development_runtime_inputs'],'Final current compiled roots drift')
    external=state['independent_artifact_receipt'];require(baseline.coverage.qualify_receipt(external['path'],external['sha256'])==
        (external,state['auxiliary_directory_identities']),'Final independent runtime artifacts drift')
    return EVIDENCE|dict(status='verified-source-current-runtime-three-controls',owned_live_processes=[])



def qualify_probe(probe,arm):
    """Read actual sampler state; stage identity remains unknown, no skip inference."""
    require(arm in ARMS,'Unknown arm');base=probe_prep.qualify_probe(probe,2048)
    require(probe.get('core_uniform_schema')==2,'Current core readback schema missing')
    observations=[];unknown_core=[]
    for program in probe['programs']:
        if program['draw_origin']!='sodium-raster-submission' or 'shadowtex1' not in program.get('samplers',{}):continue
        sampler=program['samplers']['shadowtex1'];effective=sampler.get('effective_sampling_parameters',{});texture=sampler.get('texture_parameters',{})
        if sampler['bound_sampler']==0:
            require(sampler.get('effective_parameter_source')=='texture; sampler object zero' and effective==texture,'Effective texture-state source mismatch')
        else:
            require(sampler.get('effective_parameter_source')=='sampler object overrides filtering/LOD/anisotropy/comparison; texture retains base/max levels/allocation'
                and effective==sampler.get('sampler_parameters'),'Effective sampler-object overrides mismatch')
        require(effective.get('min_filter')==effective.get('mag_filter')==9729 and effective.get('compare_mode')==34894
            and effective.get('compare_func')==515 and effective.get('lod_bias')==0.0 and effective.get('max_anisotropy')==1.0
            and effective.get('min_lod')==-1000.0 and effective.get('max_lod')==1000.0 and texture.get('base_level')==0,
            'Effective raster shadowtex1 sampling/LOD0 equivalence unknown/different')
        require(sampler.get('active_uniform_type')==35682,'Raster shadowtex1 is not hardware comparison sampler')
        known={}
        for name,target in (('shaderlodBridgeVersion',2),('shaderlodEnabled',int(arm=='global-on'))):
            value=program.get('core_uniforms',{}).get(name,{})
            if value.get('status')=='queried_without_gl_error_consumption':
                require(value.get('found_in_linked_inventory') is True and value.get('active_uniform_type')==5124
                    and value.get('active_uniform_size')==1 and value.get('active_uniform_name')==name
                    and type(value.get('uniform_location')) is int and value['uniform_location']>=0
                    and program.get('linked_uniforms',{}).get(name)==dict(type=5124,size=1)
                    and type(value.get('value')) is int and value['value']==target,
                    'Actual linked core/global differs')
                known[name]=value['value']
            else:unknown_core.append(dict(program=program['current_program'],field=name,record=value))
        observations.append(dict(program_id=program['current_program'],draw_origin=program['draw_origin'],stage_identity='unknown',
            sampler=sampler,known_core_global=known))
    require(observations,'No actual raster shadowtex1 alias observed')
    if arm!='original':require(any(len(x['known_core_global'])==2 for x in observations),'No source-compatible raster sampler with actual known core2/global')
    return EVIDENCE|dict(status='OBSERVED_RASTER_SHADOWTEX1_LOD0_SAMPLER_CONTRACT',arm=arm,base_probe=base,
        observations=observations,unknown_core_readbacks=unknown_core,
        limitation='First numeric-program draw; source-compatible alias/linked uniforms do not supply stage name, settled association, physical fetch count, exact zero eligibility, quality or speed.')


def postanalysis_plan():
    return dict(per_arm=[
        'Root reserves exact client/GPU, records initial birth/cwd/VMargs/root source/classes receipt separately from terminal audits. Serial fresh original then privateglobalOFF thenON only; stop failed sampler/source/GUI/driver gates.',
        'CloudSamplerProbe=true only in thesequeryfreecontrols. Actual runtime property/completeprobe JSON fresh; all GPU/CPU/pass/detailed/other probes false,60warm8capture, actual GUI hide/scale from gui.qualify_gui_report, empty timing streams required. Generic MissingCPUstream failure stays retained if observed; no inventedrows.',
        'Full selected archive/all531/533 members/native366/candidate368 maps/control/world89c962…7b46/capturedfullconfiguration/newR7tenmods9roots4dirs/JAR5f84…2b03/focus/native/time12500/camera/weather/wetness/no fallback checked independently. gui.qualify_configuration exactfirstmain/effectsbackup copies only; unknown changes reject.',
        'qualify_probe validates complete linkedprogram inventory, actual2048depth bothaliases, texture/sampler-object effective shadowtex1 on raster: LINEAR min/mag, compare34894/func515, bias0/aniso1/base0/LODlimits unchanged, activeunitrestoration. Candidate needs linkedcore2/global0or1 in sameobserved raster sampler; originalunknowns remainunknown. Numericprogram/origin notwaterstageidentity.',
        'Root reviews actual original/OFF/ON stills for gross shore/glints/waves/reflection/underwater/lighting damage. Unsynchronized temporal phases are not causal pixel equality or motion parity. ExplicitLOD also affects noneligible water; globalOFFparity alone insufficient.',
        'Exactzero source correctness != actual eligible/fragmentskips/retiredGPUwork/coherence or gain. No new diagnostic framework. If later required, parent can allocate bounded source-only eligibility diagnostic separately.'],
        cost='Unused OFFONOFFON source recipe BLOCKED pending parent explicit water_shadow_zero source/class guard acceptance plus actual driver/sampler/parity. Do not reuse unrelatedlive_pixel guard. Parent owns integration/new build and runtime acceptance.',performanceEvidence=False)


def main():
    parser_arg=argparse.ArgumentParser(description=__doc__);parser_arg.add_argument('--stage',action='store_true');parser_arg.add_argument('--dry-run',action='store_true');parser_arg.add_argument('--verify',type=Path);parser_arg.add_argument('--verify-source',type=Path)
    parser_arg.add_argument('--finalize',type=Path);parser_arg.add_argument('--artifact-receipt',type=Path);parser_arg.add_argument('--artifact-sha256')
    args=parser_arg.parse_args()
    try:
        require(sum(bool(v) for v in (args.stage,args.dry_run,args.verify,args.verify_source,args.finalize))==1,'Select one stage/dry-run/verify-source/finalize/verify')
        if args.finalize:
            require(args.artifact_receipt is not None and args.artifact_sha256 is not None,'Explicit new independent integrated receipt path/hash required')
            result=finalize(args.finalize,args.artifact_receipt,args.artifact_sha256)
        else:result=verify(json.loads(args.verify.read_text())) if args.verify else verify_source(json.loads(args.verify_source.read_text())) if args.verify_source else plan() if args.dry_run else stage()
        print(json.dumps(result,indent=2,sort_keys=True))
    except (OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile) as failure:parser_arg.exit(1,'Cannot prepare water-shadow-zero runtime: '+str(failure)+'\n')


if __name__=='__main__':main()
