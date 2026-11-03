#!/usr/bin/env python3
"""Prepare fixed-VL-graph rain air-body OFF/ON driver controls; never launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree
import argparse
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import shutil
import sys

import build_photon_air_fog_cost_ablation as adapter
import assemble_photon_rain_vl_cost as rain
import prepare_photon_cloud_global_quality as current
import prepare_photon_water_shadow_zero_runtime as water
from prepare_reduced_effects_profiles import require,write_json
from setup_comparison import ROOT,WORLD,file_hash,world_fingerprint,unlocked_world

baseline,canonical,parser,matrix=current.baseline,current.canonical,current.parser,current.matrix
OUTPUT=ROOT/'run-workloads/air-fog-ablation-runtime-r1'
BUILD=ROOT/'build/air-fog-ablation-runtime-r1'
MARKER='air-fog-ablation-preparation.json'
FREEZE=ROOT/'build/air-fog-cost-ablation-source-checks/frozen-evidence.json'
FREEZE_SHA256='e27d5512f9f1f447d5472ea8dbb58ed55d294e94225c70c4d8b6760481ab3364'
ARCHIVE=ROOT/'build/air-fog-cost-ablation-enabled.zip'
ARCHIVE_SHA256='e5f3c0605e5fd1e164c6996cbdf372e547fb964565c73d0deb5377e1e91f9c1b'
ARMS=('global-off','global-on')
FINAL='TWO_UNUSED_FIXEDGRAPH_RAIN_AIR_FOG_DRIVER_CONTROLS_CURRENT_R10_READY_ROOT_REVIEW'
EVIDENCE=dict(diagnostic_only=True,performanceEvidence=False,runtime='unverified',quality='deliberately missing fog when active; not an optimization',
    neutral_output_branch_proof=False,retired_gpu_work_proof=False,stage_identity='numeric program/draw origin unknown stage',
    fixed_graph=True,vl_analytic_fallback=False,shader_support_claim=False,default_promotion=False)


def command(profile):
    argv=water.command(profile)
    require(argv.count('-PfixedWetness=0')==1,'Inherited wetness command differs')
    return ['-PfixedWetness=1' if x=='-PfixedWetness=0' else x for x in argv]+['-PpixelHistoryCapture=false','-PpixelHistoryCaptureOnly=false']


def plan(output=OUTPUT,build=BUILD,root=ROOT):
    root=Path(root).resolve();output,build=Path(output),Path(build)
    require(not output.is_symlink() and not build.is_symlink(),'Symlink output unsupported');output,build=output.resolve(),build.resolve()
    require(output==root/'run-workloads/air-fog-ablation-runtime-r1' and build==root/'build/air-fog-ablation-runtime-r1','Only owned fresh family allowed')
    require(not output.exists() and not build.exists(),'Preserve old outputs; use verify')
    return EVIDENCE|dict(schema=1,output=str(output),build=str(build),arms=list(ARMS),initial_warmup_seconds=60,measurement_seconds=8,
        framebuffer=[2976,1672],window=[1488,836],weather='rain',rain=1,thunder=0,fixed_wetness=1,vl=True,
        lpv_vl=True,colored_lights=False,colored_shafts=False,dh_present=False,main_mod_present=True,
        client_gpu_owner='root',launch_authorized=False,owned_live_processes=[],stop_procedure=canonical.STOP,
        acceptance='Source peer review before launch; sameprivateenabled367map, core2/global0or1 typed actualreadback, alteredPNG supports activation only. Intentional fogloss never quality/default/perf acceptance.')


def qualify_archive():
    require(file_hash(FREEZE)==FREEZE_SHA256,'Accepted fixedgraph source freeze changed');f=json.loads(FREEZE.read_text())
    require(f['status']=='source_checked_frozen_for_parent_acceptance','Wrong fixedgraph source status')
    for name,pin in f['sha256'].items():require(file_hash(ROOT/name)==pin,'Frozen ablation source/evidence changed: '+name)
    require(file_hash(ARCHIVE)==ARCHIVE_SHA256,'Exact enabled ablation archive changed')
    original=adapter.archive_members(ROOT/adapter.BASE);actual=adapter.archive_members(ARCHIVE)
    expected=adapter.patch_members(original,json.loads((ROOT/adapter.MANIFEST).read_text()),'enabled')
    require(set(actual)==set(expected)|{adapter.METADATA} and all(actual[n]==v for n,v in expected.items()),'Complete ablation assembly changed')
    meta=json.loads(actual[adapter.METADATA]);require(meta['arm']=='enabled' and meta['manifest_sha256']==file_hash(ROOT/adapter.MANIFEST),'Ablation metadata differs')
    require(actual['LICENSE']==original['LICENSE'],'Original license changed')
    inventory=ROOT/'build/air-fog-cost-ablation-source-checks/full-inventories.json';reviewed=json.loads(inventory.read_text())['enabled']['options']
    return dict(archive=str(ARCHIVE),archive_sha256=ARCHIVE_SHA256,source_freeze=dict(path=str(FREEZE),sha256=FREEZE_SHA256),
        complete_member_sha256={n:hashlib.sha256(v).hexdigest() for n,v in sorted(actual.items())},metadata=meta,
        reviewed_full_inventory=dict(path=str(inventory),sha256=file_hash(inventory),options=reviewed))


def validate_maps(native,options):
    rain.advisor.option_maps(native)
    require(len(options)==367 and options==native|{adapter.SWITCH:'true'},'Full canonical367 map differs beyond diagnostic switch')
    require(options.get('VL')=='true' and options.get('LPV_VL')=='true' and options.get('COLORED_LIGHTS')=='false'
        and options.get('AIR_FOG_COLORED_LIGHT_SHAFTS')=='false','Fixedgraph/inactiveLPV source controls differ')


def maintained_sources():
    paths=[ROOT/'scripts/prepare_photon_air_fog_ablation_runtime.py',ROOT/'tests/test_photon_air_fog_ablation_runtime.py']
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            p=Path(name).resolve()
            if p.suffix=='.py' and p.is_relative_to(ROOT/'scripts'):paths.append(p)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def prepare(output=OUTPUT,build=BUILD,artifact_receipt=current.RECEIPT,artifact_sha256=current.RECEIPT_SHA256):
    operation=plan(output,build);matrix.require_exclusive_runtime();source=qualify_archive();scene=canonical.qualify_source()
    require(artifact_sha256==current.RECEIPT_SHA256,'Approved current R10 receipt required')
    ext,dirs,jar=current.camera.qualify_runtime_receipt(artifact_receipt,artifact_sha256)
    frozen=maintained_sources();runtime=baseline.control_prep.current_runtime_sources();compiled=matrix.development_fingerprints()
    control=rain.advisor.rain_control(scene['control']);native=scene['qualification']['effective_option_maps']['native']
    expected_options=native|{adapter.SWITCH:'true'};validate_maps(native,expected_options)
    output,build=Path(operation['output']),Path(operation['build'])
    with unlocked_world(scene['world']):
        fingerprint=world_fingerprint(scene['world']);require(fingerprint==baseline.coverage.WORLD_SHA256,'Canonical stopped source world differs')
        output.mkdir(parents=True);build.mkdir(parents=True);marker=output/MARKER
        state=EVIDENCE|dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            source_qualification=source,source_world_sha256=fingerprint,canonical_source_qualification=scene['qualification'],
            canonical_rain_control=control,independent_artifact_receipt=ext,auxiliary_directory_identities=dirs,production_jar=jar,
            runtime_sources_sha256=runtime,development_runtime_inputs=compiled,maintained_source_files_sha256=frozen,
            launch_plan=[],launch_authorized=False,owned_live_processes=[],postanalysis_plan=postanalysis_plan())
        write_json(marker,state)
        try:
            requested=scene['requested_options']['native']|{adapter.SWITCH:'true','VL':'true'}
            actual=parser.discover(ARCHIVE,requested,build,'actual-fixedgraph-private');validate_maps(native,actual['options'])
            require(actual['options']==source['reviewed_full_inventory']['options'],'Reviewed full actual canonical options differ')
            write_json(build/'option-inventory.json',actual);state['complete_option_inventory']=dict(path=str(build/'option-inventory.json'),sha256=file_hash(build/'option-inventory.json'))
            for arm in ARMS:
                enabled=arm=='global-on';base=output/arm;base.mkdir();profile=base/'control'
                controls=control|{'enabled':enabled};write_json(base/'control.json',controls)
                expected=dict(archive=ARCHIVE.name,archive_sha256=ARCHIVE_SHA256,requested_options=requested,expected_effective_options=actual['options'],
                    expected_effective_options_sha256=actual['sha256'],requested_main_mod_present=True,main_mod_presence_verified=False,global_enabled=enabled,dh_present=False)
                write_json(base/'workload-manifest.json',EVIDENCE|dict(schema=1,scene='coastalwater',weather='rain',configuration_only=False,control=controls,
                    source_world_sha256=fingerprint,source_qualification=source,experiment_arms={'control':expected}))
                storage_copytree(scene['source'],profile,ignore=shutil.ignore_patterns(*canonical.TRANSIENT));(profile/'saves').mkdir(exist_ok=True)
                storage_copytree(scene['world'],profile/'saves'/WORLD,ignore=shutil.ignore_patterns('session.lock'));require(not any((profile/'mods').glob('*.jar')),'Extra noDH/minimal mods')
                packs=profile/'shaderpacks';packs.mkdir(exist_ok=True);archive=packs/ARCHIVE.name;shutil.copy2(ARCHIVE,archive)
                archive.with_name(archive.name+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(requested.items())))
                iris=matrix.read_option_properties((profile/'config/iris.properties').read_text());iris['shaderPack']=archive.name
                (profile/'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                (profile/'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('adaptive' if enabled else 'high'))
                (profile/'config/shaderlod-effects-client.toml').write_text(baseline.coverage.effects_config('inherit'))
                request=EVIDENCE|dict(run_id=output.name+'-'+arm,case='air-ablation-'+arm,task='runClientLodOn',stack='minimal',include_dh=False,
                    initial_warmup_seconds=60,measurement_seconds=8,gpu_queries=False,cpu_timing=False,cloud_sampler_probe=True,
                    sample_interval=5,fixed_wetness=1,hide_workload_gui=True,expected_framebuffer=[2976,1672],expected_window=[1488,836])
                write_json(profile/'workload-request.json',request);snapshot=baseline.profile_snapshot(profile);require(snapshot['copied_world_sha256']==fingerprint,'Fresh world copy differs')
                state['launch_plan'].append(dict(arm=arm,profile=str(profile),command=command(profile),request=request,expected=expected,inputs=snapshot['inputs'],
                    prepared_profile=snapshot,prepared_writable_inventory=snapshot['prepared_writable_inventory'],owner='root',status='not-launched'))
            require(world_fingerprint(scene['world'])==fingerprint and maintained_sources()==frozen,'Source world/recipe changed')
            require(baseline.control_prep.current_runtime_sources()==runtime and matrix.development_fingerprints()==compiled,'Current source/classes changed')
            state['status']=FINAL;write_json(marker,state);verify(state)
        except BaseException as failure:state.update(status='FAILED',error=str(failure));write_json(marker,state);raise
    return state


def verify(state):
    require(state.get('status')==FINAL,'Incomplete preparation');matrix.require_exclusive_runtime()
    for field in ('maintained_source_files_sha256','runtime_sources_sha256'):
        for name,pin in state[field].items():require(file_hash(Path(name))==pin,'Frozen source changed: '+name)
    require(matrix.development_fingerprints()==state['development_runtime_inputs'],'Current roots changed');ext=state['independent_artifact_receipt']
    require(current.camera.qualify_runtime_receipt(ext['path'],ext['sha256'])==(ext,state['auxiliary_directory_identities'],state['production_jar']),'Current R10 artifacts/JAR changed')
    require(qualify_archive()==state['source_qualification'],'Fixedgraph accepted source/archive changed');item=state['complete_option_inventory'];require(file_hash(Path(item['path']))==item['sha256'],'Complete367inventory changed')
    require([r['arm'] for r in state['launch_plan']]==list(ARMS),'OFF/ON order changed')
    for row in state['launch_plan']:
        profile=Path(row['profile']);require(baseline.profile_snapshot(profile)==row['prepared_profile'] and command(profile)==row['command'],'Fresh source/settings/world/argv changed')
        require(not (profile/'workload-measurement.json').exists(),'Profile already used; preserve raw capture')
    return EVIDENCE|dict(status='verified-two-unused-fixedgraph-air-fog-rain-controls',owned_live_processes=[])


def qualify_probe(probe,enabled):
    require(type(enabled) is bool,'Expected global must be boolean')
    require(probe.get('schema')==1 and probe.get('core_uniform_schema')==2 and probe.get('probe')=='shaderlod.dev.cloudSamplerProbe'
        and probe.get('program_limit_reached') is False,'Current nontruncated core probe missing')
    require(probe.get('gl_renderer')=='Apple M3 Max' and probe.get('gl_version')=='4.1 Metal - 91.7','Actual driver differs')
    programs=probe.get('programs');require(isinstance(programs,list) and programs and len(programs)==probe.get('captured_programs'),'Complete nativeprogram inventory missing')
    known=[];unknown=[];ids=set()
    for p in programs:
        pid=p.get('current_program');require(type(pid) is int and pid>0 and pid not in ids,'Invalid/duplicate program ID');ids.add(pid)
        require(p.get('linked') is True and 'capture_failure' not in p and p.get('uniform_scan_truncated') is False and p.get('uniform_name_length_truncated') is False,'Unlinked/failed/truncated shaderprogram')
        require(type(p.get('active_texture_before')) is int and p['active_texture_before']==p.get('active_texture_after'),'Active texture unit not restored')
        values={}
        for name,value in (('shaderlodBridgeVersion',2),('shaderlodEnabled',int(enabled))):
            r=p.get('core_uniforms',{}).get(name,{})
            if r.get('status')!='queried_without_gl_error_consumption':unknown.append(dict(program_id=pid,field=name,record=r));continue
            require(r.get('found_in_linked_inventory') is True and r.get('active_uniform_name')==name and r.get('active_uniform_type')==5124
                and r.get('active_uniform_size')==1 and type(r.get('uniform_location')) is int and r['uniform_location']>=0
                and p.get('linked_uniforms',{}).get(name)==dict(type=5124,size=1) and type(r.get('value')) is int and r['value']==value,'Actual typed core/global differs')
            values[name]=r['value']
        if len(values)==2:known.append(dict(program_id=pid,draw_origin=p.get('draw_origin'),known_fields=values,stage_identity='unknown'))
    require(any(x['draw_origin']=='iris-fullscreen-quad' for x in known),'No source-compatible fullscreen program with actual core2/global')
    return EVIDENCE|dict(status='OBSERVED_FULLSCREEN_CORE2_GLOBAL_ABLATION_INPUTS',known_programs=known,unknown_fields=unknown,
        limitation='Actual sameprogram typedinputs; numericalcontext is not C0source binding/air-medium/neutral-output/branch/retiredwork proof. Firstdraw may precede canonical settledrain.')


def postanalysis_plan():
    return dict(prerequisites=['Root independentablation source-review beforelaunch; fixedVLtrue,unchangedC1cachedsky/pass/format/allocation source with onlyC0ordinary-air gatedbody. LPVtrue/coloredfalse inactiveLPV combination retained; noVLfalse/C1analyticfallback',
        'Actual fullsource/member367options/currentR10mainJar/effects7/coreGlobal/GUI/native/focus/pacing/camera/time12500/noDH/no fallback directartifacts/config allstrict. Rainweather appliedONLYnewcontrolcopies, sourceWorld89c unchanged.',
        '60warm8 Workload+hiddenGUI queryfree, ONLYexistingcloudSamplerProbe true. AllotherCPU/GPU/pass/detailed/history/camera/benchmark/diagnosticsfalse. Generic MissingCPUstream failure raw ifobserved, nofake rows.',
        'rain.advisor.qualify_rain actualboundaries rain1/thunder0/CPUshadergetter rain_strength_source1/fixedwetness1 appliedallobservedsuppliers/ranges; actualbiome/weather parameters ifavailable remainCPUevidence, notGLrainStrength/palettecoefficients',
        'qualify_probe completeprogram/readback/typedINTcore2/global0or1 fullscreen; oldobservernumericcontext hasNOactualC0source association orair-cameraGPUuniform proof. Retainunknowns; do notclaim stage',
        'Actualfinalstills deliberatefogloss maysupportactivation alongsideinputs butdoesnotproveeveryfragmentneutraloutputs/raymarchremoved/retiredGPUwork. Noqualitypromotion/grossqualityequivalence requirement. AnyfutureOFFONOFFcost source/driver/quietwholeframe guards separate; ablation overhead/reg allocation canchangebotharms so notliteralbodytime decomposition'],
        performanceEvidence=False)


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--prepare',action='store_true');ap.add_argument('--dry-run',action='store_true');ap.add_argument('--verify',type=Path)
    ap.add_argument('--artifact-receipt',type=Path,default=current.RECEIPT);ap.add_argument('--artifact-sha256',default=current.RECEIPT_SHA256);a=ap.parse_args()
    try:
        require(sum(bool(v) for v in (a.prepare,a.dry_run,a.verify))==1,'Select prepare/dry-run/verify')
        result=verify(json.loads(a.verify.read_text())) if a.verify else plan() if a.dry_run else prepare(artifact_receipt=a.artifact_receipt,artifact_sha256=a.artifact_sha256);print(json.dumps(result,indent=2,sort_keys=True))
    except (OSError,ValueError,KeyError,TypeError) as e:ap.exit(1,'Cannot prepare fixedgraph air fog runtime: '+str(e)+'\n')


if __name__=='__main__':main()
