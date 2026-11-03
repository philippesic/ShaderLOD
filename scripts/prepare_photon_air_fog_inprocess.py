#!/usr/bin/env python3
"""Prepare one fresh R12 guarded rain air-fog OFF/ON/OFF/ON profile; never launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import sys
import uuid

import prepare_photon_air_fog_ablation_cost as ablation
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

driver=ablation.driver
baseline,canonical,parser,matrix,copies=ablation.baseline,ablation.canonical,ablation.parser,ablation.matrix,ablation.copies
OUTPUT=ROOT/'run-workloads/air-fog-inprocess-r2'
BUILD=ROOT/'build/air-fog-inprocess-r2'
MARKER='air-fog-inprocess-preparation.json'
FINAL='ONE_UNUSED_CURRENT_R12_GUARDED_AIR_FOG_TOGGLE_PROFILE_READY_ROOT_REVIEW'
RECEIPT=ROOT/'build/air-fog-inprocess-integrated-artifacts-r12/artifact-receipt.json'
RECEIPT_SHA256='74360cc3120bf078784a08b6b3b24427d3ba08e70462e9de5bee9b1227c21887'
DRIVER_REVIEW=ROOT/'dev/logs/air-fog-ablation-runtime-r1/independent-review/summary.json'
DRIVER_REVIEW_SHA256='eecd6745450e4c85926864951c4f5e0d5d1be83c4fc28bc0123636f06a70e74d'
PHASES=[False,True,False,True]
METHOD='One JVM/world/pipeline; pinned native VL=true Photon; only core global changes; deliberate C0 air-fog removal cost diagnostic, not quality optimization'
FAMILY='air-fog-inprocess-r2'
PREPARATION_VERSION=2
R1_MARKER=ROOT/'run-workloads/air-fog-inprocess-r1/air-fog-inprocess-preparation.json'
R1_MARKER_SHA256='6431e66442575dd074809968c1bf8974d788b0ea1292b12c6f2287985304bd3d'
OFF=tuple(dict.fromkeys(driver.rain.advisor.OFF+('measureWorkload','measureOnly','pixelHistoryCapture','pixelHistoryCaptureOnly')))
EVIDENCE=ablation.EVIDENCE|dict(same_JVM_correlation=True,original_native_denominator=False,
    independent_repeated_gain=False,evidence_class='guarded-same-JVM-fixedgraph-air-fog-ablation-cost-preparation')


def command(profile):
    argv=['./gradlew','runClientLodOn','-PrunDirectory='+str(Path(profile).resolve()),'-PperformanceStack=minimal',
        '-PincludeDh=false','-PgpuBenchmark=true','-PbenchmarkOnly=true','-PbenchmarkFeature=air_fog_ablation',
        '-PbenchmarkStartEnabled=false','-PinitialWarmup=30','-PbenchmarkSettleSeconds=8','-PmeasurementSeconds=20',
        '-PgpuTiming=true','-PcpuTiming=true','-PgpuSampleInterval=5','-PbenchmarkWindowScale=2',
        '-PcomparisonFullscreen=false','-PfixedWetness=1','-PcameraQuality=']
    # Gradle gpuBenchmark enables Java benchmark, not Java gpuBenchmark observer.
    argv+=['-P'+name+'=false' for name in OFF if name not in ('gpuTiming','cpuTiming','gpuBenchmark','benchmarkOnly')]
    for task in canonical.BUILD_TASKS:argv+=['-x',task]
    return argv


def plan(output=OUTPUT,build=BUILD,root=ROOT):
    root=Path(root).resolve()
    driver.require(not Path(output).is_symlink() and not Path(build).is_symlink(),'Symlink output unsupported')
    output,build=Path(output).resolve(),Path(build).resolve()
    driver.require(output==root/'run-workloads/air-fog-inprocess-r2' and build==root/'build/air-fog-inprocess-r2','Only owned fresh family allowed')
    driver.require(not output.exists() and not build.exists(),'Preserve existing output; use --verify')
    profile=output/'coastalwater/toggle'
    return EVIDENCE|dict(schema=1,family=FAMILY,preparation_version=PREPARATION_VERSION,output=str(output),build=str(build),profile=str(profile),command=command(profile),
        feature='air_fog_ablation',phases=PHASES,expected_method=METHOD,initial_global_enabled=False,
        initial_warmup_seconds=30,settle_seconds=8,measurement_seconds=20,minimum_capture_seconds=80,
        minimum_settling_seconds=32,minimum_session_seconds=142,gpu_sample_interval=5,minimum_positive_unique_gpu_samples_per_phase=60,
        coarse_cpu=True,coarse_scopes=['run-tick','game-render','render-and-blit','swap-and-events'],
        framebuffer=[2976,1672],window=[1488,836],gui_hidden=True,gui_scale=2,first_person=True,FOV=70,
        preset='PERFORMANCE',bands=[32,96,8],weather='rain',rain=1,thunder=0,fixed_wetness=1,VL=True,
        client_gpu_owner='root',launch_authorized=False,owned_live_processes=[],stop_procedure=canonical.STOP,
        launch_hold='Parent accepts independent compiled R12 guard review and reserves quiet lane before launch; preparation cannot prove execution.',
        denominator='Same private-enabled VLtrue graph/global OFF; deliberate fog loss, not original-quality mod gain')


def maintained_sources():
    paths=[ROOT/'scripts/prepare_photon_air_fog_inprocess.py',ROOT/'tests/test_photon_air_fog_inprocess.py',ROOT/'docs/AIR_FOG_ABLATION_TOGGLE_GUARD.md']
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            path=Path(name).resolve()
            if path.suffix=='.py' and path.is_relative_to(ROOT/'scripts'):paths.append(path)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def qualify_runtime_receipt(path,pin):
    path=Path(path).resolve()
    driver.require(path==RECEIPT and pin==RECEIPT_SHA256,'Explicit accepted R12 receipt required; R10 cannot qualify new classes')
    external,dirs=baseline.coverage.qualify_receipt(path,pin)
    jar=ROOT/'build/libs/shaderlod-0.1.0-dev.jar';sha=external['receipt']['independent_evidence_files_sha256'].get(str(jar))
    driver.require(isinstance(sha,str) and file_hash(jar)==sha==driver.rain.advisor.JAR_SHA256,'Current coherent main e86 receipt differs')
    return external,dirs,dict(path=str(jar),sha256=sha)


def qualify_archive():return ablation.qualify_archive()
def qualify_driver_provenance():return ablation.qualify_driver_provenance(DRIVER_REVIEW,DRIVER_REVIEW_SHA256)


def r1_failure_provenance():
    driver.require(file_hash(R1_MARKER)==R1_MARKER_SHA256,'Historical failed R1 preparation changed')
    logs=ROOT/'dev/logs/air-fog-inprocess-r1/root-launch'
    paths=[R1_MARKER,logs/'console.log',logs/'initial-process.json',logs/'terminal-process.json']
    driver.require('Air fog owning class identity unavailable' in (logs/'console.log').read_text(),
        'Historical owning-source constructor failure evidence missing')
    return dict(files_sha256={str(p):file_hash(p) for p in paths},scope='R1 failed during constructor before phases; no shader/performance evidence. Profile/preparation/raw logs preserved.')


def validate_manifest(manifest,native):
    driver.require(manifest.get('schema')==1 and manifest.get('scene')=='coastalwater' and manifest.get('configuration_only') is False
        and manifest.get('source_world_sha256')==baseline.coverage.WORLD_SHA256,'Same-JVM scene/source world differs')
    recipe=manifest.get('benchmark_recipe',{});control=manifest.get('control',{})
    driver.require(recipe.get('feature')=='air_fog_ablation' and recipe.get('phases')==PHASES
        and recipe.get('initial_global_enabled') is False and recipe.get('expected_method')==METHOD
        and recipe.get('family')==FAMILY and recipe.get('preparation_version')==PREPARATION_VERSION,'Explicit R2 guarded feature/order/method differs')
    driver.require(control==driver.rain.advisor.rain_control(manifest['canonical_clear_control'])|{'enabled':False},'Rain initial OFF control differs')
    source=manifest.get('source_qualification',{});model=manifest.get('experiment_arms',{}).get('toggle',{})
    driver.require(len(source.get('complete_member_sha256',{}))==533 and source.get('archive_sha256')==model.get('archive_sha256')==driver.ARCHIVE_SHA256,
        'Exact private source533 differs')
    driver.validate_maps(native,model.get('expected_effective_options',{}))
    driver.require(model.get('expected_effective_options_sha256')=='c65d0f75579d75b86668d5e2ef607e37b2dbb824e4b3a18ad7dcb0ea2f078ab6'
        and model.get('requested_main_mod_present') is True and model.get('dh_present') is False,'Exact full367/core/noDH scope differs')
    return dict(member_count=533,option_count=367,phases=PHASES,initial_global_enabled=False)


def prepare(receipt_path,receipt_pin,output=OUTPUT,build=BUILD):
    operation=plan(output,build);matrix.require_exclusive_runtime();source=qualify_archive();scene=canonical.qualify_source()
    external,dirs,jar=qualify_runtime_receipt(receipt_path,receipt_pin);provenance=qualify_driver_provenance();failed=r1_failure_provenance()
    native=scene['qualification']['effective_option_maps']['native'];requested=scene['requested_options']['native']|{driver.adapter.SWITCH:'true','VL':'true'}
    config=canonical.semantic_properties((scene['source']/'options.txt').read_text(),':')
    driver.require(config.get('fov')=='0.0','Canonical persisted FOV setting differs from 70 default')
    runtime=baseline.control_prep.current_runtime_sources();compiled=matrix.development_fingerprints();frozen=maintained_sources()
    output,build=Path(operation['output']),Path(operation['build'])
    with unlocked_world(scene['world']):
        fingerprint=world_fingerprint(scene['world']);driver.require(fingerprint==baseline.coverage.WORLD_SHA256,'Canonical stopped coastal save differs')
        output.mkdir(parents=True);build.mkdir(parents=True);marker=output/MARKER
        state=EVIDENCE|dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            source_qualification=source,canonical_source_qualification=scene['qualification'],source_world_sha256=fingerprint,
            driver_provenance=provenance,maintained_source_files_sha256=frozen,runtime_sources_sha256=runtime,
            development_runtime_inputs=compiled,independent_artifact_receipt=external,auxiliary_directory_identities=dirs,
            production_jar=jar,r1_failure_provenance=failed,launch_authorized=False,owned_live_processes=[],postanalysis_plan=postanalysis_plan())
        copies.write_json(marker,state)
        try:
            inventory=parser.discover(driver.ARCHIVE,requested,build,'actual-private-fixedgraph')
            driver.validate_maps(native,inventory['options']);driver.require(inventory['options']==source['reviewed_full_inventory']['options'],'Reviewed full367 discovery differs')
            inventory_path=build/'option-inventory.json';copies.write_json(inventory_path,inventory)
            state['complete_option_inventory']=dict(path=str(inventory_path),sha256=file_hash(inventory_path))
            state['expected_effective_options']=inventory['options'];state['expected_effective_options_sha256']=inventory['sha256']
            profile=Path(operation['profile']);control=driver.rain.advisor.rain_control(scene['control'])|{'enabled':False}
            model=dict(archive=driver.ARCHIVE.name,archive_sha256=driver.ARCHIVE_SHA256,requested_options=requested,
                expected_effective_options=inventory['options'],expected_effective_options_sha256=inventory['sha256'],
                requested_main_mod_present=True,main_mod_presence_verified=False,dh_present=False)
            manifest=EVIDENCE|dict(schema=1,scene='coastalwater',configuration_only=False,source_world_sha256=fingerprint,
                canonical_clear_control=scene['control'],control=control,experiment_arms={'toggle':model},source_qualification=source,benchmark_recipe=operation)
            validate_manifest(manifest,native);driver.rain.cost.write_control_files(profile.parent,control,manifest)
            storage_copytree(scene['source'],profile,ignore=shutil.ignore_patterns(*canonical.TRANSIENT));(profile/'saves').mkdir(exist_ok=True)
            storage_copytree(scene['world'],profile/'saves'/WORLD,ignore=shutil.ignore_patterns('session.lock'))
            driver.require(not any((profile/'mods').glob('*.jar')),'Extra minimal/noDH profile mods')
            packs=profile/'shaderpacks';packs.mkdir(exist_ok=True);archive=packs/driver.ARCHIVE.name;shutil.copy2(driver.ARCHIVE,archive)
            archive.with_name(archive.name+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(requested.items())))
            iris=matrix.read_option_properties((profile/'config/iris.properties').read_text());iris['shaderPack']=archive.name
            (profile/'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
            (profile/'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('high'))
            effects=baseline.coverage.effects_config('inherit');state['prepared_effects']=driver.current.camera.forest.validate_effects(effects)
            (profile/'config/shaderlod-effects-client.toml').write_text(effects)
            request=EVIDENCE|dict(run_id=str(uuid.uuid4()),feature='air_fog_ablation',phases=PHASES,initial_global_enabled=False,
                initial_warmup_seconds=30,settle_seconds=8,measurement_seconds=20,gpu_queries=True,coarse_cpu=True,gpu_sample_interval=5,
                fixed_wetness=1,source_world_sha256=fingerprint,provenance_only=True,
                consumer='BenchmarkSession uses exact Gradle properties and creates its own runtime session UUID')
            copies.write_json(profile/'air-fog-inprocess-request.json',request)
            state['request']=request;state['prepared_profile']=baseline.profile_snapshot(profile)
            driver.require(state['prepared_profile']['copied_world_sha256']==fingerprint,'Copied world differs')
            driver.require(world_fingerprint(scene['world'])==fingerprint and maintained_sources()==frozen
                and baseline.control_prep.current_runtime_sources()==runtime and matrix.development_fingerprints()==compiled,'Source/world/current runtime changed')
            state['status']=FINAL;verify(state);copies.write_json(marker,state)
        except BaseException as failure:
            state.update(status='FAILED_PREPARATION',error=str(failure));copies.write_json(marker,state);raise
    return state


def verify(state):
    driver.require(state.get('status')==FINAL and state['operation'].get('family')==FAMILY
        and state['operation'].get('preparation_version')==PREPARATION_VERSION,'Incomplete/current-family preparation')
    for field in ('maintained_source_files_sha256','runtime_sources_sha256'):
        for name,pin in state[field].items():driver.require(file_hash(Path(name))==pin,'Frozen source drift: '+name)
    driver.require(baseline.control_prep.current_runtime_sources()==state['runtime_sources_sha256']
        and matrix.development_fingerprints()==state['development_runtime_inputs'],'Complete current runtime source/class drift')
    ext=state['independent_artifact_receipt'];driver.require(qualify_runtime_receipt(ext['path'],ext['sha256'])==
        (ext,state['auxiliary_directory_identities'],state['production_jar']),'Exact R12/main/artifact roots changed')
    driver.require(qualify_archive()==state['source_qualification'] and qualify_driver_provenance()==state['driver_provenance'],'Private source or actual driver provenance changed')
    driver.require(r1_failure_provenance()==state['r1_failure_provenance'],'Retained constructor-failure R1 provenance changed')
    for name,pin in state['canonical_source_qualification']['source_files_sha256'].items():driver.require(file_hash(Path(name))==pin,'Canonical source drift')
    driver.require(world_fingerprint(canonical.SOURCE/'saves'/WORLD)==state['source_world_sha256'],'Canonical stopped world changed')
    item=state['complete_option_inventory'];driver.require(file_hash(Path(item['path']))==item['sha256'],'Complete Iris inventory changed')
    inventory=json.loads(Path(item['path']).read_text());driver.validate_maps(state['canonical_source_qualification']['effective_option_maps']['native'],inventory['options'])
    for name,pin in inventory['parser_dependency_sha256'].items():driver.require(file_hash(Path(name))==pin,'Pinned parser dependency changed')
    driver.require(file_hash(Path(inventory['log']))==inventory['log_sha256'],'Parser log changed')
    profile=Path(state['operation']['profile']);driver.require(profile==OUTPUT/'coastalwater/toggle' and state['operation']['command']==command(profile)
        and baseline.profile_snapshot(profile)==state['prepared_profile'],'Fresh profile/config/world/argv changed')
    driver.require(not (profile/'benchmark.json').exists() and not list(profile.glob('benchmark*.json')),'Profile used; preserve runtime reports')
    manifest=json.loads((profile.parent/'workload-manifest.json').read_text());validate_manifest(manifest,state['canonical_source_qualification']['effective_option_maps']['native'])
    driver.require(state['request']==json.loads((profile/'air-fog-inprocess-request.json').read_text()),'Provenance request changed')
    driver.require(driver.current.camera.forest.validate_effects((profile/'config/shaderlod-effects-client.toml').read_text())==state['prepared_effects'],'Seven effect controls changed')
    return EVIDENCE|dict(status='verified-one-current-R12-unused-guarded-air-fog-toggle-profile',launch_authorized=False,owned_live_processes=[])


def postanalysis_plan():
    return dict(root_prerequisites=['Independent compiled R12 guard review accepted before launch; actual source/data-model fixtures are not four runtime phase receipts.',
        'Exact unused prep/current R12/source533/map367/driver22group provenance/native rain scenario, no build/source/test/encoder activity during quiet capture.'],
        actual=['Benchmark schema3/feature air_fog_ablation/exact METHOD/selected lod.enabled/OFFONOFFON; original globalFalse restored, GUI restoration/source/classes unchanged.',
            'Actual initial +each4start/end control_receipt_schema2, same world/pipeline/camera object identities and exact Float pose/FOV70/FIRST_PERSON; no stale source/model substitution.',
            'Current R12 direct artifacts +owning code-source and resource identities; complete captured configs/effects/source/maps, only allowed main/global token/startup formatting roles with raw bytes retained.',
            '30initialwarm/8settle/20measurement each, advancing normal counter/frame-time/rain CPU-source1/fixedwetness1 suppliers and ranges; no GL rain/fog/biome readback claim.',
            '>=60 positive contiguous full-game-render GPU N5 rows and four separate coarse CPU coverage streams per phase, same valid epochs/counters; no pass/layer/probe/history/camera/Workload contamination.'],
        limits=['Same-JVM correlation changes the drift strategy but is not independent repetition, original-quality gain or literal additive C0 body time.',
            'Known driver inputs/distinct output supports activation only; C0 identity, neutral RT6/7 texels, physical retired work and quality optimization remain unproved.'])


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--dry-run',action='store_true');p.add_argument('--verify',type=Path)
    p.add_argument('--artifact-receipt',type=Path);p.add_argument('--artifact-sha256');a=p.parse_args()
    if a.verify:
        driver.require(a.verify.resolve()==OUTPUT/MARKER,'Only owned marker allowed');matrix.require_exclusive_runtime();result=verify(json.loads(a.verify.read_text()))
    elif a.dry_run:result=plan()
    else:
        driver.require(a.artifact_receipt is not None and a.artifact_sha256 is not None,'Explicit current R12 receipt path/hash required')
        state=prepare(a.artifact_receipt,a.artifact_sha256);result=dict(status=state['status'],marker=str(OUTPUT/MARKER),marker_sha256=file_hash(OUTPUT/MARKER),
            profile=state['operation']['profile'],launch_authorized=False,owned_live_processes=[])
    print(json.dumps(result,sort_keys=True,indent=2))


if __name__=='__main__':main()
