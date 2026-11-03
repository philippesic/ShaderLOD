#!/usr/bin/env python3
"""Prepare one fresh guarded water OFF/ON/OFF/ON cost profile; never launch/build."""
from __future__ import annotations
from run_storage import copytree as storage_copytree
import argparse
from datetime import datetime,timezone
import json
from pathlib import Path
import shutil
import sys
import tomllib
import uuid

import analyze_water_shadow_control as analyzer
import prepare_photon_water_shadow_zero_runtime as water
from prepare_reduced_effects_profiles import require,write_json
from setup_comparison import ROOT,WORLD,file_hash,world_fingerprint,unlocked_world

baseline,canonical,parser,matrix=water.baseline,water.canonical,water.parser,water.matrix
OUTPUT=ROOT/'run-workloads/water-shadow-control-r1'
BUILD=ROOT/'build/water-shadow-control-r1'
MARKER='water-shadow-control-preparation.json'
RECEIPT=ROOT/'build/water-temporal-integrated-artifacts-r7/artifact-receipt.json'
RECEIPT_SHA256='77adf1f9f810f4ac01b19396902edce288fa3e1ed1c2744a00b1a1b81979755d'
ANALYZER_SHA256='4e6bfa2ea77f28018e8d864f06c157962552188f3ade9601708b535cc9b6dcc7'
PHASES=[False,True,False,True]
OFF=tuple(dict.fromkeys(water.OFF+('cloudSamplerProbe',)))
EVIDENCE=dict(diagnostic_only=True,performanceEvidence=False,quality='unaccepted',motion='unverified',
    water_stage_runtime_qualification=False,executed_skip_coverage_proof=False,
    original_native_denominator=False,adapter_gain=False,source_counts_are_runtime_coverage=False,
    evidence_class='incremental-water-exact-zero-candidate-cost-screen-preparation')
FINAL='SOURCE_CURRENT_R7_RUNTIME_FRESH_PROFILE_READY_ROOT_PREREQUISITES_HOLD'


def command(profile):
    argv=['./gradlew','runClientLodOn','-PrunDirectory='+str(Path(profile).resolve()),'-PperformanceStack=minimal',
        '-PincludeDh=false','-PgpuBenchmark=true','-PbenchmarkOnly=true','-PbenchmarkFeature=water_shadow_zero',
        '-PbenchmarkStartEnabled=false','-PinitialWarmup=30','-PbenchmarkSettleSeconds=8','-PmeasurementSeconds=20',
        '-PgpuTiming=true','-PcpuTiming=true','-PgpuSampleInterval=5','-PbenchmarkWindowScale=2',
        '-PcomparisonFullscreen=false','-PfixedWetness=0','-PmeasureWorkload=false','-PmeasureOnly=false','-PcameraQuality=']
    # gpuBenchmark is the Gradle alias enabling BenchmarkSession, not the unused
    # Java observer property with that spelling. Do not emit conflicting argv.
    argv+=['-P'+name+'=false' for name in OFF if name not in ('gpuTiming','cpuTiming','gpuBenchmark','benchmarkOnly')]
    for task in canonical.BUILD_TASKS:argv+=['-x',task]
    return argv


def plan(output=OUTPUT,build=BUILD,root=ROOT):
    root=Path(root).resolve();output,build=Path(output),Path(build)
    require(not output.is_symlink() and not build.is_symlink(),'Symlink output unsupported')
    output,build=output.resolve(),build.resolve()
    require(output==root/'run-workloads/water-shadow-control-r1' and build==root/'build/water-shadow-control-r1','Only owned fresh family allowed')
    require(not output.exists() and not build.exists(),'Preserve previous outputs; use verify')
    profile=output/'coastalwater/toggle'
    return EVIDENCE|dict(schema=1,output=str(output),build=str(build),profile=str(profile),command=command(profile),
        feature='water_shadow_zero',phases=PHASES,expected_method=analyzer.METHOD,initial_warmup_seconds=30,
        settle_seconds=8,measurement_seconds=20,minimum_capture_seconds=80,minimum_settling_seconds=32,
        minimum_session_seconds=142,gpu_sample_interval=5,minimum_positive_unique_gpu_samples_per_phase=60,
        coarse_cpu=True,coarse_scopes=['run-tick','game-render','render-and-blit','swap-and-events'],
        initial_global_enabled=False,taau=False,vps=False,shadow_map_resolution=2048,framebuffer=[2976,1672],window=[1488,836],
        client_gpu_owner='root',launch_authorized=False,stop_procedure=canonical.STOP,
        launch_status='HOLD: independent three-driver/sampler review and root gross-still parity acceptance before quiet launch',
        focus_contract='Actual GLFW focused=true/visible/noniconified throughout; no CUA/JXA/oldCodex helper assumption. Stop failed focus before timing; parent allocates supported dev focus if needed.',
        screen=dict(minimum_each_adjacent_on_frame_reduction=.05,gpu_direction='both ON medians lower than adjacent OFF',
            maximum_off_frame_and_gpu_drift=.10,normal_quality='unaccepted',failure_policy='Raw failures retained; no gate relaxation/favorable baseline replacement/automatic retry'),
        denominator='Incremental candidate private-enabled global OFF/ON; OFF retains selector/guard and is not original Photon')


def maintained_sources():
    paths=[ROOT/'scripts/prepare_water_shadow_control.py',ROOT/'tests/test_water_shadow_control_runtime.py',
        ROOT/'scripts/analyze_water_shadow_control.py',ROOT/'tests/test_water_shadow_control.py']
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            path=Path(name).resolve()
            if path.suffix=='.py' and path.is_relative_to(ROOT/'scripts'):paths.append(path)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def validate_effects(profile):
    parsed=tomllib.loads((Path(profile)/'config/shaderlod-effects-client.toml').read_text())['effects']
    names={'shadow_filtering','ambient_occlusion','reflections','volumetrics','water_surface','materials','opaque_lighting'}
    require(set(parsed)==names,'Complete seven effects inventory differs')
    require(all(v['mode']=='INHERIT' and v['mediumWorkRatio']==v['farWorkRatio']==1.0
        and v['preserveSalientLighting'] is True for v in parsed.values()),'Effect controls not complete INHERIT/unity')
    return dict(count=7,names=sorted(parsed),all_inherit_unity=True,source='Complete prepared TOML; actual loaded snapshots still require runtime guard')


def prepare(output=OUTPUT,build=BUILD):
    operation=plan(output,build);matrix.require_exclusive_runtime()
    require(file_hash(ROOT/'scripts/analyze_water_shadow_control.py')==ANALYZER_SHA256,'Reviewed water analyzer changed')
    source=water.qualify_archive();require(source['archive_sha256']==analyzer.ARCHIVE and
        matrix.canonical_options_hash(source['complete_member_sha256'])==analyzer.MEMBERS_HASH,'Analyzer/complete source pins differ')
    scene=canonical.qualify_source();require(file_hash(water.JAR)==water.JAR_SHA256,'Current main frame JAR changed')
    external,directories=baseline.coverage.qualify_receipt(RECEIPT,RECEIPT_SHA256)
    runtime=water.gui.runtime_prep.current_runtime_sources();compiled=matrix.development_fingerprints();frozen=maintained_sources()
    output,build=Path(operation['output']),Path(operation['build'])
    with unlocked_world(scene['world']):
        fingerprint=world_fingerprint(scene['world']);require(fingerprint==analyzer.WORLD,'Canonical source-world differs')
        output.mkdir(parents=True);build.mkdir(parents=True);marker=output/MARKER
        state=EVIDENCE|dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            source_qualification=source,canonical_source_qualification=scene['qualification'],source_world_sha256=fingerprint,
            maintained_source_files_sha256=frozen,runtime_sources_sha256=runtime,development_runtime_inputs=compiled,
            independent_artifact_receipt=external,auxiliary_directory_identities=directories,production_jar=dict(path=str(water.JAR),sha256=water.JAR_SHA256),
            launch_authorized=False,owned_live_processes=[],launch_status=operation['launch_status'],postanalysis_plan=postanalysis_plan())
        write_json(marker,state)
        try:
            requested=scene['requested_options']['native']|{water.adapter.SWITCH:'true'}
            candidate=parser.discover(water.ARCHIVE,requested,build,'actual-water-enabled')
            water.validate_maps(scene['qualification']['effective_option_maps']['native'],candidate['options'])
            require(candidate['options']==source['reviewed_inventory']['canonical_enabled'] and candidate['sha256']==analyzer.OPTIONS_HASH,'Reviewed complete368 actual map differs')
            write_json(build/'option-inventory.json',candidate)
            state['complete_option_inventory']=dict(path=str(build/'option-inventory.json'),sha256=file_hash(build/'option-inventory.json'))
            state['expected_effective_options']=candidate['options'];state['expected_effective_options_sha256']=candidate['sha256']
            profile=Path(operation['profile']);profile.parent.mkdir()
            control=scene['control']|{'enabled':False};write_json(profile.parent/'control.json',control)
            model=dict(requested_options=requested,expected_effective_options=candidate['options'],expected_effective_options_sha256=candidate['sha256'],
                archive_sha256=analyzer.ARCHIVE,requested_main_mod_present=True,main_mod_presence_verified=False,dh_present=False)
            manifest=EVIDENCE|dict(schema=1,scene='coastalwater',configuration_only=False,source_world_sha256=fingerprint,
                control=control,experiment_arms={'toggle':model},source_qualification=source,benchmark_recipe=operation)
            # Exact actual analyzer schema; no legacy adaptive archive shortcut.
            analyzer.validate_manifest(manifest);write_json(profile.parent/'workload-manifest.json',manifest)
            storage_copytree(scene['source'],profile,ignore=shutil.ignore_patterns(*canonical.TRANSIENT));(profile/'saves').mkdir(exist_ok=True)
            storage_copytree(scene['world'],profile/'saves'/WORLD,ignore=shutil.ignore_patterns('session.lock'))
            require(not any((profile/'mods').glob('*.jar')),'Extra minimal/noDH profile mods')
            packs=profile/'shaderpacks';packs.mkdir(exist_ok=True);archive=packs/water.ARCHIVE.name;shutil.copy2(water.ARCHIVE,archive)
            archive.with_name(archive.name+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(requested.items())))
            iris=matrix.read_option_properties((profile/'config/iris.properties').read_text());iris['shaderPack']=archive.name
            (profile/'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
            (profile/'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('high'))
            (profile/'config/shaderlod-effects-client.toml').write_text(baseline.coverage.effects_config('inherit'))
            request=EVIDENCE|dict(run_id=str(uuid.uuid4()),feature='water_shadow_zero',phases=PHASES,initial_global_enabled=False,
                initial_warmup_seconds=30,settle_seconds=8,measurement_seconds=20,gpu_queries=True,coarse_cpu=True,
                gpu_sample_interval=5,source_world_sha256=fingerprint,provenance_only=True,
                consumer='BenchmarkSession uses exact Gradle properties and creates its own runtime session UUID')
            write_json(profile/'water-shadow-control-request.json',request)
            state['request']=request;state['prepared_profile']=baseline.profile_snapshot(profile);state['prepared_effects']=validate_effects(profile)
            require(state['prepared_profile']['copied_world_sha256']==fingerprint,'Copied world differs')
            require(world_fingerprint(scene['world'])==fingerprint and maintained_sources()==frozen,'Canonical save/recipe changed')
            require(water.gui.runtime_prep.current_runtime_sources()==runtime and matrix.development_fingerprints()==compiled,'Current source/classes changed')
            state['status']=FINAL;write_json(marker,state);verify(state)
        except BaseException as failure:state.update(status='FAILED',error=str(failure));write_json(marker,state);raise
    return state


def verify(state):
    require(state.get('status')==FINAL,'Incomplete preparation');matrix.require_exclusive_runtime()
    for field in ('maintained_source_files_sha256','runtime_sources_sha256'):
        for name,pin in state[field].items():require(file_hash(Path(name))==pin,'Frozen source drift: '+name)
    require(matrix.development_fingerprints()==state['development_runtime_inputs'] and file_hash(water.JAR)==water.JAR_SHA256,'Current roots/JAR drift')
    external=state['independent_artifact_receipt'];require(baseline.coverage.qualify_receipt(external['path'],external['sha256'])==
        (external,state['auxiliary_directory_identities']),'Current R7 artifact drift')
    require(water.qualify_archive()==state['source_qualification'],'Source/accepted archive drift')
    item=state['complete_option_inventory'];require(file_hash(Path(item['path']))==item['sha256'],'Actual368inventory drift')
    profile=Path(state['operation']['profile']);require(baseline.profile_snapshot(profile)==state['prepared_profile'],'Fresh profile/config/source/world drift')
    require(command(profile)==state['operation']['command'] and validate_effects(profile)==state['prepared_effects'],'Exact argv/effects drift')
    analyzer.validate_manifest(json.loads((profile.parent/'workload-manifest.json').read_text()))
    require(not (profile/'benchmark.json').exists(),'Profile already used; preserve raw capture')
    return EVIDENCE|dict(status='verified-fresh-guarded-water-cost-profile',launch_status=state['launch_status'],launch_authorized=False,owned_live_processes=[])


def postanalysis_plan():
    return dict(prerequisites=['Root accepts independent actual original/OFF/ON driver/sampler component review and gross still parity before launching; preparation holds launch',
        'Current R7 source/classes/tenmods9roots4dirs/JAR5f84/freshworld/all533 members/full368 options/completepreparedsevenINHERITunity/nativecoast/time12500/TAAUfalse/VPSfalse/2048 strict',
        'One GPU/client owner; root records exact initial birth/cwd/actual VMargs, focus true before capture, quiet CPU window, terminal handles separately. Do not use CUA/JXA Codex focus helper or infer focus from launch',
        'Analyze raw schema3 water report with analyze_water_shadow_control.analyze_report(report,artifact_receipt,profile); actual complete manifest/source qualifiers, no adaptive_pack_sha256 fallback',
        'Header water_stage_runtime_qualification=false/executed_skip_coverage_proof=false remain true limitations, not promoted by timers. Actual world/pipeline/camera object IDs unexported; current Java source guards identity',
        'Four phases OFFONOFFON30warm8actualsettle20capture/fullGPU N5>=60positiveunique/epochs/counters/coarse four separate scopes/source-definedboundaryomission/full configuration and artifact snapshots/window/pacing/restoration/phase images qualify',
        'All sampler/diagnostic/pass/detailed/attribution/camera probes OFF. No physical skip counts or q inferred from source/uniforms/mean timings. Retain failed raw guards and derived inputs separately',
        'Both ON frame reductions must exceed5% and GPU medians lower against adjacent OFF, OFF drift<=10%frame/GPU. FPS denominator OFFcandidate; no original-native claim. Flat/adverse/insufficient/inconclusive screen stops escalation',
        'Any useful signal requires original comparison, independent repeats/queryfree corroboration/representative still and wave/motion/noneligible sampling quality before promotion'],
        screen_only=True,performanceEvidence=False)


def main():
    args_parser=argparse.ArgumentParser(description=__doc__);args_parser.add_argument('--prepare',action='store_true');args_parser.add_argument('--dry-run',action='store_true');args_parser.add_argument('--verify',type=Path)
    args=args_parser.parse_args()
    try:
        require(sum(bool(x) for x in (args.prepare,args.dry_run,args.verify))==1,'Select one prepare/dry-run/verify')
        result=verify(json.loads(args.verify.read_text())) if args.verify else plan() if args.dry_run else prepare()
        print(json.dumps(result,indent=2,sort_keys=True))
    except (OSError,ValueError,KeyError,TypeError) as failure:args_parser.exit(1,'Cannot prepare guarded water control: '+str(failure)+'\n')


if __name__=='__main__':main()
