#!/usr/bin/env python3
"""Prepare fixed-graph private air-fog OFF/ON/OFF costs after driver qualification."""
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

import prepare_photon_air_fog_ablation_runtime as driver
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

baseline, canonical, parser, matrix, copies = driver.baseline,driver.canonical,driver.parser,driver.matrix,driver.rain.copies
OUTPUT = ROOT / 'run-workloads/air-fog-ablation-cost-r1'
BUILD = ROOT / 'build/air-fog-ablation-cost-r1'
MARKER = 'air-fog-ablation-cost-preparation.json'
FINAL = 'THREE_UNUSED_CURRENT_R10_FIXEDGRAPH_AIR_FOG_COST_CONTROLS_READY_ROOT_QUIET_REVIEW'
ORDER = ('global-off-before','global-on','global-off-after')
ENABLED = (False,True,False)
DRIVER_MARKER = driver.OUTPUT/driver.MARKER
DRIVER_MARKER_SHA256 = '5436ffd1ea7aa654e8b720c0f7df6667dab0bab8a1638393accf82e69851c37a'
EVIDENCE = dict(diagnostic_only=True,performanceEvidence=False,adapter_gain=False,runtime='unverified',
    quality='intentional ordinary-air fog loss; not an optimization',quality_equivalent=False,
    fixed_graph=True,vl_analytic_fallback=False,neutral_output_branch_proof=False,
    retired_gpu_work_proof=False,strict_additive_zero_cost=False,shader_support_claim=False,default_promotion=False)


def command(profile): return driver.rain.advisor.command(profile)


def plan(output=OUTPUT,build=BUILD,root=ROOT):
    root=Path(root).resolve()
    driver.require(not Path(output).is_symlink() and not Path(build).is_symlink(),'Symlink output unsupported')
    output,build=Path(output).resolve(),Path(build).resolve()
    driver.require(output==root/'run-workloads/air-fog-ablation-cost-r1' and build==root/'build/air-fog-ablation-cost-r1','Only owned fresh families allowed')
    driver.require(not output.exists() and not build.exists(),'Preserve existing output; use --verify')
    return EVIDENCE|dict(schema=1,output=str(output),build=str(build),scene='coastalwater',arms=list(ORDER),enabled=list(ENABLED),
        profile_count=3,initial_warmup_seconds=60,measurement_seconds=20,gpu_sample_interval=5,minimum_gpu_samples=60,
        framebuffer=[2976,1672],window=[1488,836],requested_chunks=24,weather='rain',fixed_wetness=1,VL=True,
        private_option=True,shader_option_map_identical=True,maximum_control_frame_and_gpu_drift=.10,
        minimum_each_control_fps_gain=.05,client_gpu_owner='root',launch_authorized=False,owned_live_processes=[],
        stop_procedure=canonical.STOP,assembly_hold='Root driver qualification and idle signal required; no feature/Benchmark guard added.',
        denominator='Same private-enabled fixed VLtrue graph/global OFF; not original default-weather/mod/quality-equivalent gain')


def maintained_sources():
    paths=[ROOT/'scripts/prepare_photon_air_fog_ablation_cost.py',ROOT/'tests/test_photon_air_fog_ablation_cost.py']
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            path=Path(name).resolve()
            if path.suffix=='.py' and path.is_relative_to(ROOT/'scripts'): paths.append(path)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def qualify_runtime_receipt(path,pin): return driver.current.camera.qualify_runtime_receipt(path,pin)
def qualify_archive(): return driver.qualify_archive()


def qualify_driver_provenance(review_path,review_pin):
    """Freeze both terminal driver records and the parent's independent review."""
    review_path=Path(review_path).resolve()
    driver.require(file_hash(review_path)==review_pin and file_hash(DRIVER_MARKER)==DRIVER_MARKER_SHA256,'Exact driver review/preparation changed')
    review=json.loads(review_path.read_text())
    driver.require(review.get('schema')==1 and review.get('status')=='QUALIFIED_FIXED_GRAPH_AIR_FOG_DRIVER_INPUTS_DISTINCT_OUTPUT_SMOKE'
        and review.get('preparation_sha256')==DRIVER_MARKER_SHA256
        and review.get('other_live_semantic_settings_match_except_authorized_global') is True
        and review.get('independent_exact_process_check',{}).get('all_absent') is True
        and set(review.get('arms',{}))==set(driver.ARMS),'Complete independent driver qualification missing')
    for field in ('actual_C0_stage_identity','neutral_RT6_RT7_texel_proof','retired_GPU_work_proof','quality_equivalent','performanceEvidence'):
        driver.require(review.get(field) is False,'Driver review boundary inflated/missing: '+field)
    doc=ROOT/'docs/PHOTON_AIR_FOG_ABLATION_DRIVER_R1.md'
    driver.require(file_hash(doc)=='875a601d5ba0aae2dda5b5bff51f2e842389f67f5fb447360ad0ca2c04b67056','Accepted independent driver report changed')
    paths=[DRIVER_MARKER,review_path,doc]; raw={}
    for arm in driver.ARMS:
        profile=driver.OUTPUT/arm/'control'; report_path=profile/'workload-measurement.json'
        record=review['arms'][arm]; qualification=Path(record['qualification_path'])
        driver.require(file_hash(qualification)==record['qualification_sha256'],'Independent driver component changed')
        checked=json.loads(qualification.read_text())
        driver.require(checked.get('status')=='PASS startup components only' and len(checked.get('checks',{}))==11
            and all(check.get('passed') is True for check in checked['checks'].values()),'Complete eleven-group driver component missing')
        paths.append(qualification)
        for name,pin in checked['input_sha256'].items():
            driver.require(file_hash(Path(name))==pin,'Reviewed driver source/raw input changed: '+name);paths.append(Path(name))
        report=json.loads(report_path.read_text())
        driver.require(str(report_path)==record['raw_workload_path'] and file_hash(report_path)==record['raw_workload_sha256'],'Raw driver report differs from review')
        driver.require(report.get('status')=='complete' and report.get('gpu_samples')==[] and report.get('cpu_samples')==[],
            'Complete untimed raw driver report missing')
        driver.require(report.get('actual_shader_sha256')==driver.ARCHIVE_SHA256 and report.get('effective_shader_options_sha256')==
            'c65d0f75579d75b86668d5e2ef607e37b2dbb824e4b3a18ad7dcb0ea2f078ab6','Exact private driver source/options differ')
        driver.rain.advisor.qualify_rain(report,report['request'])
        probe=Path(record['raw_probe_path']);driver.require(file_hash(probe)==record['raw_probe_sha256'],'Actual driver probe changed')
        driver.qualify_probe(json.loads(probe.read_text()),arm=='global-on')
        paths.append(probe)
        logdir=ROOT/'dev/logs/air-fog-ablation-runtime-r1'/arm
        paths += [report_path,profile/'screenshots/workload-final.png',logdir/'initial-process.json',logdir/'terminal-process.json',logdir/'console.log']
        raw[arm]=dict(report=str(report_path),sha256=file_hash(report_path))
    for name,pin in review.get('input_sha256',{}).items():
        driver.require(file_hash(Path(name))==pin,'Reviewed driver raw/source input changed: '+name);paths.append(Path(name))
    return dict(review_path=str(review_path),review_sha256=review_pin,review=review,raw_reports=raw,
        files_sha256={str(p):file_hash(p) for p in sorted(set(paths))},
        scope='Actual source/options/config/current driver/core-global activation components only; numeric contexts/PNGs do not prove C0 source association, neutral RT writes or retired work.')


def validate_manifest(manifest,native):
    arm=manifest.get('arm');driver.require(arm in ORDER,'Unknown cost arm');enabled=ENABLED[ORDER.index(arm)]
    driver.require(manifest.get('schema')==1 and manifest.get('scene')=='coastalwater' and manifest.get('weather')=='rain'
        and manifest.get('configuration_only') is False and manifest.get('source_world_sha256')==baseline.coverage.WORLD_SHA256,
        'Fixedgraph scene/world/weather schema differs')
    source=manifest.get('source_qualification',{});model=manifest.get('experiment_arms',{}).get('control',{})
    members=source.get('complete_member_sha256',{})
    driver.require(len(members)==533 and source.get('archive_sha256')==model.get('archive_sha256')==driver.ARCHIVE_SHA256
        and matrix.canonical_options_hash(members)==source.get('complete_member_map_sha256'),'Complete private source/member receipt differs')
    driver.validate_maps(native,model.get('expected_effective_options',{}))
    driver.require(model.get('expected_effective_options_sha256')==matrix.canonical_options_hash(model['expected_effective_options']),
        'Full private367 map hash differs')
    driver.require(type(model.get('global_enabled')) is bool and model['global_enabled']==enabled
        and model.get('requested_main_mod_present') is True and model.get('dh_present') is False,'Expected global/native/noDH scope differs')
    canonical_control=manifest.get('canonical_rain_control',{})
    driver.require(canonical_control==driver.rain.advisor.rain_control(manifest['canonical_clear_control'])
        and manifest.get('control')==canonical_control|{'enabled':enabled},'Only main/global control may differ')
    return dict(arm=arm,enabled=enabled,member_count=533,option_count=367)


def validate_pairs(rows):
    driver.require([r['arm'] for r in rows]==list(ORDER),'Missing/reordered fixedgraph bracket')
    signatures=[]
    for row,enabled in zip(rows,ENABLED):
        driver.require(type(row['expected'].get('global_enabled')) is bool and row['expected']['global_enabled']==enabled,'Expected global field differs')
        writable=copy.deepcopy(row['prepared_writable_inventory']);main=writable['settings']['config/shaderlod-client.toml']['value']
        driver.require(type(main['lod'].get('enabled')) is bool and main['lod']['enabled']==enabled,'Prepared main/global mismatch')
        main['lod'].pop('enabled'); writable['settings_sha256']=matrix.canonical_options_hash(writable['settings'])
        signatures.append(writable)
    driver.require(all(s==signatures[0] for s in signatures),'Other complete settings differ beyond main enabled')
    return dict(complete_other_settings_match=True,permitted_main_option='config/shaderlod-client.toml:lod.enabled',shader_option_deltas=[])


def prepare(receipt_path,receipt_pin,review_path,review_pin,output=OUTPUT,build=BUILD):
    operation=plan(output,build);matrix.require_exclusive_runtime();source=qualify_archive()
    scene=canonical.qualify_source();external,dirs,jar=qualify_runtime_receipt(receipt_path,receipt_pin)
    provenance=qualify_driver_provenance(review_path,review_pin);native=scene['qualification']['effective_option_maps']['native']
    source=source|dict(complete_member_map_sha256=matrix.canonical_options_hash(source['complete_member_sha256']))
    requested=scene['requested_options']['native']|{driver.adapter.SWITCH:'true','VL':'true'}
    rain_control=driver.rain.advisor.rain_control(scene['control'])
    frozen=maintained_sources();runtime=baseline.control_prep.current_runtime_sources();compiled=matrix.development_fingerprints()
    output,build=Path(operation['output']),Path(operation['build'])
    with unlocked_world(scene['world']):
        fingerprint=world_fingerprint(scene['world']);driver.require(fingerprint==baseline.coverage.WORLD_SHA256,'Canonical stopped world differs')
        output.mkdir(parents=True);build.mkdir(parents=True);marker=output/MARKER
        state=EVIDENCE|dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            source_qualification=source,canonical_source_qualification=scene['qualification'],source_world_sha256=fingerprint,
            canonical_clear_control=scene['control'],canonical_rain_control=rain_control,driver_provenance=provenance,
            maintained_sources_sha256=frozen,runtime_sources_sha256=runtime,development_runtime_inputs=compiled,
            independent_artifact_receipt=external,auxiliary_directory_identities=dirs,production_jar=jar,
            launch_plan=[],launch_authorized=False,owned_live_processes=[],postanalysis_plan=postanalysis_plan())
        copies.write_json(marker,state)
        try:
            inventory=parser.discover(driver.ARCHIVE,requested,build,'private-fixedgraph-all-arms')
            driver.validate_maps(native,inventory['options']);driver.require(inventory['options']==source['reviewed_full_inventory']['options'],
                'Reviewed complete private367 map differs')
            inventory_path=build/'option-inventory.json';copies.write_json(inventory_path,inventory)
            state['complete_option_inventory']=dict(path=str(inventory_path),sha256=file_hash(inventory_path))
            for arm,enabled in zip(ORDER,ENABLED):
                base=output/arm;profile=base/'control';control=rain_control|{'enabled':enabled}
                expected=dict(archive=driver.ARCHIVE.name,archive_sha256=driver.ARCHIVE_SHA256,requested_options=requested,
                    expected_effective_options=inventory['options'],expected_effective_options_sha256=inventory['sha256'],
                    requested_main_mod_present=True,main_mod_presence_verified=False,global_enabled=enabled,dh_present=False)
                manifest=EVIDENCE|dict(schema=1,scene='coastalwater',weather='rain',arm=arm,configuration_only=False,
                    source_world_sha256=fingerprint,control=control,canonical_clear_control=scene['control'],canonical_rain_control=rain_control,
                    experiment_arms={'control':expected},source_qualification=source)
                validate_manifest(manifest,native);driver.rain.cost.write_control_files(base,control,manifest)
                storage_copytree(scene['source'],profile,ignore=shutil.ignore_patterns(*canonical.TRANSIENT));(profile/'saves').mkdir(exist_ok=True)
                storage_copytree(scene['world'],profile/'saves'/WORLD,ignore=shutil.ignore_patterns('session.lock'))
                driver.require(not any((profile/'mods').glob('*.jar')),'Extra minimal/noDH profile mods')
                packs=profile/'shaderpacks';packs.mkdir(exist_ok=True);archive=packs/driver.ARCHIVE.name;shutil.copy2(driver.ARCHIVE,archive)
                archive.with_name(archive.name+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(requested.items())))
                iris=matrix.read_option_properties((profile/'config/iris.properties').read_text());iris['shaderPack']=archive.name
                (profile/'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                (profile/'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('adaptive' if enabled else 'high'))
                effects=baseline.coverage.effects_config('inherit');driver.current.camera.forest.validate_effects(effects)
                (profile/'config/shaderlod-effects-client.toml').write_text(effects)
                request=EVIDENCE|dict(run_id=output.name+'-'+arm,case='fixedgraph-rain-air-'+arm,task='runClientLodOn',stack='minimal',
                    include_dh=False,initial_warmup_seconds=60,measurement_seconds=20,gpu_queries=True,cpu_timing=True,
                    cloud_sampler_probe=False,fixed_wetness=1,hide_workload_gui=True,sample_interval=5,
                    expected_framebuffer=[2976,1672],expected_window=[1488,836])
                copies.write_json(profile/'workload-request.json',request);snapshot=baseline.profile_snapshot(profile)
                driver.require(snapshot['copied_world_sha256']==fingerprint and snapshot['inputs']['shader_sha256']==driver.ARCHIVE_SHA256,'Copied source/save differs')
                state['launch_plan'].append(dict(arm=arm,scene='coastalwater',profile=str(profile),command=command(profile),request=request,
                    expected=expected,inputs=snapshot['inputs'],prepared_profile=snapshot,
                    prepared_writable_inventory=snapshot['prepared_writable_inventory'],owner='root',status='not-launched'))
            driver.require(world_fingerprint(scene['world'])==fingerprint and maintained_sources()==frozen
                and baseline.control_prep.current_runtime_sources()==runtime and matrix.development_fingerprints()==compiled,'Source/world/classes changed')
            state['prepared_pairs_comparison']=validate_pairs(state['launch_plan']);state['status']=FINAL
            verify(state);copies.write_json(marker,state)
        except BaseException as failure:
            state.update(status='FAILED_PREPARATION',error=str(failure));copies.write_json(marker,state);raise
    return state


def verify(state):
    driver.require(state.get('status')==FINAL and [r['arm'] for r in state['launch_plan']]==list(ORDER),'Incomplete/reordered controls')
    for field in ('maintained_sources_sha256','runtime_sources_sha256'):
        for name,pin in state[field].items():driver.require(file_hash(Path(name))==pin,'Frozen source drift: '+name)
    driver.require(baseline.control_prep.current_runtime_sources()==state['runtime_sources_sha256']
        and matrix.development_fingerprints()==state['development_runtime_inputs'],'Complete current source/class drift')
    entry=state['independent_artifact_receipt'];driver.require(qualify_runtime_receipt(entry['path'],entry['sha256'])==
        (entry,state['auxiliary_directory_identities'],state['production_jar']),'Current R10 artifact/JAR drift')
    expected_source=qualify_archive();expected_source['complete_member_map_sha256']=matrix.canonical_options_hash(expected_source['complete_member_sha256'])
    driver.require(expected_source==state['source_qualification'],'Exact reviewed private source changed')
    evidence=state['driver_provenance'];driver.require(qualify_driver_provenance(evidence['review_path'],evidence['review_sha256'])==evidence,'Actual driver prerequisite changed')
    for name,pin in state['canonical_source_qualification']['source_files_sha256'].items():driver.require(file_hash(Path(name))==pin,'Canonical source drift')
    driver.require(world_fingerprint(canonical.SOURCE/'saves'/WORLD)==state['source_world_sha256'],'Canonical stopped source save differs')
    item=state['complete_option_inventory'];driver.require(file_hash(Path(item['path']))==item['sha256'],'Full Iris inventory drift')
    inventory=json.loads(Path(item['path']).read_text());native=state['canonical_source_qualification']['effective_option_maps']['native']
    driver.validate_maps(native,inventory['options'])
    for name,pin in inventory['parser_dependency_sha256'].items():driver.require(file_hash(Path(name))==pin,'Pinned Iris dependency drift')
    driver.require(file_hash(Path(inventory['log']))==inventory['log_sha256'],'Iris log drift')
    for row in state['launch_plan']:
        profile=Path(row['profile']);driver.require(profile==OUTPUT/row['arm']/'control' and row['command']==command(profile)
            and baseline.profile_snapshot(profile)==row['prepared_profile'],'Fresh profile/source/settings/world/argv drift')
        driver.require(not (profile/'workload-measurement.json').exists(),'Profile used; preserve output')
        manifest=json.loads((profile.parent/'workload-manifest.json').read_text());validate_manifest(manifest,native)
        driver.require(row['expected']==manifest['experiment_arms']['control'] and row['request']==json.loads((profile/'workload-request.json').read_text()),'Manifest/request row differs')
        driver.current.camera.forest.validate_effects((profile/'config/shaderlod-effects-client.toml').read_text())
    driver.require(validate_pairs(state['launch_plan'])==state['prepared_pairs_comparison'],'Complete paired control semantics differ')
    return EVIDENCE|dict(status='verified-three-current-R10-unused-fixedgraph-air-fog-cost-controls',launch_authorized=False,owned_live_processes=[])


def qualify_rain(report,request):return driver.rain.advisor.qualify_rain(report,request)
def screen(frame_ms,gpu_ms):
    result=driver.rain.advisor.screen(frame_ms,gpu_ms)
    return EVIDENCE|{k:v for k,v in result.items() if k in ('control_drift','fps_gain_vs_each_vl','gpu_reduction_vs_each_vl')}|dict(
        verdict=result['verdict'].replace('useful-maximum-removal-calibration-only','useful-fixedgraph-air-body-removal-diagnostic-only'),
        denominator='Each private global-OFF control; fps_gain_vs_each_vl is inherited metric key, VL remains true in ALL arms',
        limitation='Added branch/compiler/register/global-state effects prevent strict additive or zero-cost body decomposition; neutral outputs and retired work unproved.')


def postanalysis_plan():
    return driver.postanalysis_plan()|dict(cost_protocol='Separate normal Workload OFF/ON/OFF; no Benchmark feature or new Java gate. 60warm20capture/N5fullGPU/coarse4/probes/camera/historyOFF/hiddenGUI/fixedwetness1.',
        cost_actual='Require full367 same loaded maps/all533 source/current R10/rain1-thunder0-CPUgetter1/appliedwetness1/all7effects/native focus/window/time12500 and exact artifacts/config/source guards.',
        allowed_control_delta='ONLY main config lod.enabled and matching parent control enabled; preserve complete raw settings/source metadata and exact narrowly qualified startup backup roles.',
        stop='OFF before/after frame AND GPU drift <=10%; ON >5% higher FPS and lowerGPU vs BOTH controls only permits more bounded diagnosis. No optical-tail implementation, quality or default promotion.',
        output_premise='Source-supported neutral C0 air RT6/7 with no VLfalse/C1 analytic fallback; actual typedinputs and different PNG support activation, not neutral pixel writes or physical retirement.',
        null_boundary='Qualify exact source-backed Gson nullable omission only if present; no generic null stripping or artifact/metadata dropping.')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--dry-run',action='store_true');p.add_argument('--verify',type=Path)
    p.add_argument('--artifact-receipt',type=Path);p.add_argument('--artifact-sha256');p.add_argument('--driver-review',type=Path);p.add_argument('--driver-review-sha256')
    a=p.parse_args()
    if a.verify:
        driver.require(a.verify.resolve()==OUTPUT/MARKER,'Only owned marker allowed');matrix.require_exclusive_runtime();result=verify(json.loads(a.verify.read_text()))
    elif a.dry_run:result=plan()
    else:
        driver.require(all(x is not None for x in (a.artifact_receipt,a.artifact_sha256,a.driver_review,a.driver_review_sha256)),
            'Explicit current R10 and independently qualified driver review paths/hashes required')
        state=prepare(a.artifact_receipt,a.artifact_sha256,a.driver_review,a.driver_review_sha256)
        result=dict(status=state['status'],marker=str(OUTPUT/MARKER),marker_sha256=file_hash(OUTPUT/MARKER),profiles=3,
            launch_authorized=False,owned_live_processes=[])
    print(json.dumps(result,sort_keys=True,indent=2))


if __name__=='__main__':main()
