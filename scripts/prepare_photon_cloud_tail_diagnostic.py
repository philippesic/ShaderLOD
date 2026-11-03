#!/usr/bin/env python3
"""Prepare two original-settings query-free Photon tail counter controls; never launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree
import argparse
import copy
from datetime import datetime, timezone
import hashlib
import json
import re
from pathlib import Path
import shutil
import sys
import tomllib
import zipfile

import build_photon_contribution_tail_diagnostic as adapter
import decode_photon_contribution_tail_diagnostic as decoder
import prepare_photon_pixel_baseline as baseline
import prepare_photon_live_pixel_control as runtime_prep
from prepare_reduced_effects_profiles import require, write_json
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

canonical, live, matrix = baseline.canonical, baseline.live, baseline.matrix
OUTPUT=ROOT/'run-workloads/cloud-tail-diagnostic-r3'
BUILD=ROOT/'build/cloud-tail-diagnostic-r3'
FREEZE=ROOT/'build/contribution-tail-diagnostic-source-checks/freeze.json'
FREEZE_SHA256='8e179fab1fddbb241d95bf55517de59399aea189d4275070242e6bf3ad4ec407'
ARCHIVE=ROOT/'build/contribution-tail-diagnostic-enabled.zip'
ARCHIVE_SHA256='2525441fdd23610bc9f62a18df138529fe7e1dd3acb07c0636f26796e5799095'
INVENTORY=ROOT/'build/contribution-tail-diagnostic-source-checks/actual-iris-inventories.json'
WORLD_SHA256=baseline.coverage.WORLD_SHA256
ARMS=('global-off','global-on')
OFF=tuple(dict.fromkeys(live.OFF+('cloudSamplerProbe','pixelCoverage','pixelCoverageOnly')))
EVIDENCE=dict(diagnostic_only=True,performanceEvidence=False,runtime='unverified',quality='unaccepted',
    normal_work_cost_proof=False,shader_support_claim=False,executed_work='pending actual calibrated native PNG',
    hide_gui_observed=False,hide_workload_gui_requested=True,timing_status='FORBIDDEN: diagnostic replaces cloud/final colors')
MARKER='cloud-tail-diagnostic-preparation.json'
MINIMUM_MEANINGFUL_CALLS=1000
CANONICAL_DEFAULT_OVERRIDES={k:'false' for k in ('BORDER_FOG','CLOUD_SHADOWS','SHADOW_COLOR','SHADOW_VPS','SH_SKYLIGHT')}


def command(profile):
    argv=['./gradlew','runClientLodOn','-PrunDirectory='+str(Path(profile).resolve()),
        '-PperformanceStack=minimal','-PincludeDh=false','-PmeasureWorkload=true','-PmeasureOnly=true',
        '-PinitialWarmup=60','-PmeasurementSeconds=8','-PbenchmarkWindowScale=2','-PhideWorkloadGui=true',
        '-PcomparisonFullscreen=false','-PfixedWetness=0','-PgpuSampleInterval=5','-PcameraQuality=']
    argv+=['-P'+flag+'=false' for flag in OFF]
    for task in canonical.BUILD_TASKS:argv+=['-x',task]
    return argv


def plan(output=OUTPUT,build=BUILD,root=ROOT):
    root=Path(root).resolve()
    require(not Path(output).is_symlink() and not Path(build).is_symlink(),'Symlink output unsupported')
    output,build=Path(output).resolve(),Path(build).resolve()
    require(output.parent==root/'run-workloads' and re.fullmatch(r'cloud-tail-diagnostic-r[1-9][0-9]*',output.name) is not None
        and build==root/'build'/output.name,'Only owned fresh family allowed')
    require(not output.exists() and not build.exists(),'Preserve previous outputs; use verify')
    return EVIDENCE|dict(schema=1,output=str(output),build=str(build),arms=list(ARMS),
        initial_warmup_seconds=60,measurement_seconds=8,framebuffer=[2976,1672],window=[1488,836],
        cloud_plane=[744,418],complete_cloud_rays=310992,primary_steps=[40,20],
        preserved_congestus_primary_steps=20,upscaling=4,taau=False,main_mod_present=True,dh_present=False,
        minimum_meaningful_called_cells=MINIMUM_MEANINGFUL_CALLS,
        initial_32_step_assumption='Corrected by root before creation: source/decoder support1–80; preserve original H40/Z20/congestus20; no32 override',
        ui_action='Harness hideWorkloadGui=true; actual generic input boundaries hide_gui=true/gui_scale=2 required. Root reserves/focuses client; no F1/CUA workaround required. No cameraQuality or probe.',
        launch_authorized=False,client_gpu_owner='root',owned_live_processes=[],stop_procedure=canonical.STOP,
        decision='ON q=sum breaks/sum calls must exceed .25 unique-exponential proxy break-even; no meaningful calls or lower q closes scene. No speed/quality conclusion.')


def maintained_sources():
    paths=[ROOT/'scripts/prepare_photon_cloud_tail_diagnostic.py',ROOT/'tests/test_photon_cloud_tail_diagnostic_runtime.py',
        ROOT/'scripts/analyze_photon_pixel_baseline.py']
    for module in tuple(sys.modules.values()):
        name=getattr(module,'__file__',None)
        if name:
            path=Path(name).resolve()
            if path.suffix=='.py' and path.is_relative_to(ROOT/'scripts'):paths.append(path)
    paths+=[ROOT/'src/devClient/java/com/pp/shaderlod/dev/ComparisonClient.java']
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def qualify_archive():
    require(file_hash(FREEZE)==FREEZE_SHA256,'Reviewed diagnostic source freeze differs')
    freeze=json.loads(FREEZE.read_text())
    for name,pin in freeze['source_files'].items():require(file_hash(ROOT/name)==pin,'Reviewed diagnostic source changed: '+name)
    require(file_hash(ROOT/freeze['test_log'])==freeze['test_log_sha256'],'Diagnostic source checks log changed')
    require(file_hash(ARCHIVE)==ARCHIVE_SHA256,'Exact enabled diagnostic archive changed')
    actual=adapter.tail.archive_members(ARCHIVE);expected,metadata=adapter.assemble('enabled')
    require(actual==expected,'Complete assembled archive source/member map differs')
    reviewed=json.loads(INVENTORY.read_text())['enabled']
    require(reviewed['archive_sha256']==ARCHIVE_SHA256 and len(reviewed['options'])==369,'Reviewed complete actual Iris inventory differs')
    require(actual['LICENSE']==adapter.tail.archive_members(ROOT/adapter.tail.BASE)['LICENSE'],'Original license changed')
    return dict(source_freeze=dict(path=str(FREEZE),sha256=FREEZE_SHA256),archive=str(ARCHIVE),archive_sha256=ARCHIVE_SHA256,
        complete_member_sha256={n:hashlib.sha256(data).hexdigest() for n,data in sorted(actual.items())},
        metadata=metadata,reviewed_actual_iris_inventory=dict(path=str(INVENTORY),sha256=file_hash(INVENTORY),options=reviewed['options']),
        decoder=dict(path=str(ROOT/'scripts/decode_photon_contribution_tail_diagnostic.py'),sha256=file_hash(ROOT/'scripts/decode_photon_contribution_tail_diagnostic.py')))


def validate_options(options):
    require(len(options)==369,'Complete diagnostic options must have369 keys')
    expected={'TAAU':'false','CLOUDS_TEMPORAL_UPSCALING':'4','BLOCKY_CLOUDS':'false',
        'CLOUDS_CUMULUS_PRIMARY_STEPS_H':'40','CLOUDS_CUMULUS_PRIMARY_STEPS_Z':'20',
        'CLOUDS_CUMULUS_CONGESTUS_PRIMARY_STEPS':'20',adapter.tail.SWITCH:'true',adapter.SWITCH:'true',
        'SHADERLOD_CONTRIBUTION_TAIL_INCLUDED':'true'}
    require(all(options.get(k)==v for k,v in expected.items()),'Original steps/support/private switches differ')
    require(not any(k.startswith('VOXY') for k in options),'Voxy unsupported diagnostic profile')


def profile_snapshot(profile):
    return baseline.profile_snapshot(profile)


def prepare(output=OUTPUT,build=BUILD,artifact_receipt=None,artifact_sha256=None):
    operation=plan(output,build);matrix.require_exclusive_runtime()
    scene=canonical.qualify_source();qualified=qualify_archive()
    require(artifact_receipt is not None and artifact_sha256 is not None,'Explicit new GUI-integrated independent artifact receipt required')
    external,directories=baseline.coverage.qualify_receipt(artifact_receipt,artifact_sha256)
    require(artifact_sha256!=baseline.RECEIPT_SHA256,'Old r4 GUI-unaware artifact receipt cannot qualify r3')
    compiled=matrix.development_fingerprints();runtime_sources=runtime_prep.current_runtime_sources();sources=maintained_sources()
    bootstrap=baseline.cost.bootstrap_source_evidence()
    output,build=Path(operation['output']),Path(operation['build'])
    with unlocked_world(scene['world']):
        fingerprint=world_fingerprint(scene['world']);require(fingerprint==WORLD_SHA256,'Canonical coastal source save differs')
        output.mkdir(parents=True);build.mkdir(parents=True);marker=output/MARKER
        state=EVIDENCE|dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            source_qualification=qualified,canonical_source_qualification=scene['qualification'],source_world_sha256=fingerprint,
            independent_artifact_receipt=external,auxiliary_directory_identities=directories,development_runtime_inputs=compiled,
            runtime_sources_sha256=runtime_sources,maintained_source_files_sha256=sources,launch_plan=[],launch_authorized=False,
            owned_live_processes=[],bootstrap_source_evidence=bootstrap,postanalysis_plan=postanalysis_plan(),
            runtime_gui_contract='New generic Workload hide_gui/gui_scale actual export; requested hideWorkloadGui=true; no old UI receipt promotion')
        write_json(marker,state)
        try:
            requested=scene['requested_options']['native']|{adapter.tail.SWITCH:'true',adapter.SWITCH:'true'}
            inventory=live.discover(ARCHIVE,requested,build,'actual-enabled-diagnostic')
            validate_options(inventory['options'])
            require(inventory['options']==qualified['reviewed_actual_iris_inventory']['options']|CANONICAL_DEFAULT_OVERRIDES,
                'Actual complete discovery differs from source369 defaults plus exact pinned canonical coastal overrides')
            require(inventory['options']==scene['qualification']['effective_option_maps']['native']|
                {adapter.tail.SWITCH:'true',adapter.SWITCH:'true','SHADERLOD_CONTRIBUTION_TAIL_INCLUDED':'true'},
                'Diagnostic complete map changed original lighting/steps/optics')
            write_json(build/'option-inventory.json',inventory)
            state['complete_option_inventory']=dict(path=str(build/'option-inventory.json'),sha256=file_hash(build/'option-inventory.json'))
            for arm in ARMS:
                enabled=arm=='global-on';base=output/arm;base.mkdir();profile=base/'control'
                control=scene['control']|{'enabled':enabled};write_json(base/'control.json',control)
                expected=dict(archive=ARCHIVE.name,archive_sha256=ARCHIVE_SHA256,requested_options=requested,
                    expected_effective_options=inventory['options'],expected_effective_options_sha256=inventory['sha256'],
                    requested_main_mod_present=True,main_mod_presence_verified=False,dh_present=False,global_enabled=enabled,
                    expected_producer_core=2,expected_producer_global=int(enabled),tail_option=True)
                write_json(base/'workload-manifest.json',EVIDENCE|dict(schema=1,scene='coastalwater',control=control,
                    source_world_sha256=fingerprint,configuration_only=False,experiment_arms={'control':expected},source_qualification=qualified))
                storage_copytree(scene['source'],profile,ignore=shutil.ignore_patterns(*canonical.TRANSIENT))
                (profile/'saves').mkdir(exist_ok=True)
                storage_copytree(scene['world'],profile/'saves'/WORLD,ignore=shutil.ignore_patterns('session.lock'))
                require(not any((profile/'mods').glob('*.jar')),'Extra minimal/noDH profile mods')
                packs=profile/'shaderpacks';packs.mkdir(exist_ok=True);archive=packs/ARCHIVE.name;shutil.copy2(ARCHIVE,archive)
                archive.with_name(archive.name+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(requested.items())))
                iris=matrix.read_option_properties((profile/'config/iris.properties').read_text());iris['shaderPack']=archive.name
                (profile/'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                (profile/'config/shaderlod-client.toml').write_text(baseline.coverage.fresh_config('adaptive' if enabled else 'high'))
                (profile/'config/shaderlod-effects-client.toml').write_text(baseline.coverage.effects_config('inherit'))
                request=EVIDENCE|dict(run_id=output.name+'-'+arm,case='cloud-tail-'+arm,task='runClientLodOn',stack='minimal',
                    include_dh=False,measurement_seconds=8,initial_warmup_seconds=60,gpu_queries=False,cpu_timing=False,
                    sample_interval=5,fixed_wetness=0,hide_workload_gui=True,expected_framebuffer=[2976,1672],expected_window=[1488,836])
                write_json(profile/'workload-request.json',request);snapshot=profile_snapshot(profile)
                require(snapshot['copied_world_sha256']==fingerprint and snapshot['inputs']['shader_sha256']==ARCHIVE_SHA256,'Copied world/archive differs')
                state['launch_plan'].append(dict(arm=arm,profile=str(profile),command=command(profile),request=request,expected=expected,
                    inputs=snapshot['inputs'],prepared_profile=snapshot,prepared_writable_inventory=snapshot['prepared_writable_inventory'],
                    owner='root',status='not-launched',ui_action_pending=False,actual_gui_export_pending=True))
            require(world_fingerprint(scene['world'])==fingerprint,'Canonical source save changed')
            require(matrix.development_fingerprints()==compiled and runtime_prep.current_runtime_sources()==runtime_sources,'Runtime classes/source changed')
            require(maintained_sources()==sources and qualify_archive()==qualified,'Preparation/source graph changed')
            state['status']='SOURCE_CURRENT_RUNTIME_TWO_FRESH_PROFILES_READY_ROOT_CAPTURE_PENDING';write_json(marker,state);verify(state)
        except BaseException as failure:state.update(status='FAILED',error=str(failure));write_json(marker,state);raise
    return state


def verify(state):
    matrix.require_exclusive_runtime();require(state.get('status')=='SOURCE_CURRENT_RUNTIME_TWO_FRESH_PROFILES_READY_ROOT_CAPTURE_PENDING','Incomplete preparation')
    for field in ('maintained_source_files_sha256','runtime_sources_sha256'):
        for name,pin in state[field].items():require(file_hash(Path(name))==pin,'Frozen source drift: '+name)
    require(matrix.development_fingerprints()==state['development_runtime_inputs'],'Compiled runtime roots drift')
    external=state['independent_artifact_receipt'];require(baseline.coverage.qualify_receipt(external['path'],external['sha256'])==
        (external,state['auxiliary_directory_identities']),'Independent current artifact receipt drift')
    require(qualify_archive()==state['source_qualification'],'Diagnostic archive/source/decoder drift')
    item=state['complete_option_inventory'];require(file_hash(Path(item['path']))==item['sha256'],'Actual Iris discovery receipt drift')
    require([r['arm'] for r in state['launch_plan']]==list(ARMS),'OFF thenON order drift')
    for row in state['launch_plan']:
        require(row['command']==command(row['profile']) and profile_snapshot(row['profile'])==row['prepared_profile'],'Fresh profile/source/settings/save/argv drift')
        require(not (Path(row['profile'])/'workload-measurement.json').exists(),'Profile already used; preserve old outputs')
    return EVIDENCE|dict(status='verified-source-runtime-and-two-fresh-profiles',launch_authorized=False,owned_live_processes=[])


def qualify_configuration(snapshot,row):
    """Both optional first backups must be exact prepared copies, never ignored."""
    prepared=copy.deepcopy(row['prepared_writable_inventory']);roles={}
    for base in ('shaderlod-client','shaderlod-effects-client'):
        name='config/'+base+'-1.toml.bak';record=snapshot.get('files',{}).get(name)
        if record is None:continue
        original='config/'+base+'.toml';pin=row['prepared_profile']['files_sha256'][row['profile']+'/'+original]
        require(record.get('sha256')==pin,'First backup differs from exact prepared bytes: '+name)
        require(isinstance(record.get('utf8'),str) and tomllib.loads(record['utf8'])==prepared['settings'][original]['value'],
            'First backup full settings differ: '+name)
        prepared['settings'][name]=dict(format='unrecognized exact bytes',sha256=pin);roles[name]=record
    result=baseline.qualify_boundary_configuration(snapshot,prepared,row['expected']['archive'])
    result['qualified_exact_first_backups']=roles
    # ConfigTracker provenance is existing independently pinned source evidence.
    from analyze_photon_pixel_baseline import effects_backup_source
    if roles:result['startup_backup_source']=effects_backup_source()
    return result


def qualify_gui_report(report,row):
    """Actual new Workload exports only; never retrofit old r2 UI assumptions."""
    require(row['request'].get('hide_workload_gui') is True and '-PhideWorkloadGui=true' in row['command'],
        'GUI hide request/argv missing')
    require(report.get('request')==row['request'],'Fresh GUI report/request differs')
    require(report.get('runtime_properties',{}).get('shaderlod.dev.hideWorkloadGui')=='true',
        'Actual forwarded Workload hide-GUI property missing/disabled')
    for name in ('input_state_start','input_state_end'):
        state=report.get(name,{})
        require(state.get('hide_gui') is True and type(state.get('gui_scale')) is int and state['gui_scale']==2,
            'Actual generic hidden HUD/GUI scale missing/different: '+name)
    return dict(actual_hide_gui=True,actual_gui_scale=2,requested_property='true',
        scope='Actual generic producer state at both capture boundaries; full PNG codec coverage remains independent')


def external_decoder_receipt(report_path,row,preparation):
    """Provenance construction only: root must independently qualify raw runtime."""
    path=Path(report_path);report=json.loads(path.read_text())
    require(report.get('actual_shader_sha256')==ARCHIVE_SHA256,'Actual reported archive differs')
    return dict(archive_sha256=ARCHIVE_SHA256,scene_sha256=preparation['source_world_sha256'],raw_workload_sha256=file_hash(path),
        native_dimensions=[2976,1672],source_tag=decoder.TAG,producer_stage='world0/deferred1.fsh',consumer_stage='world0/final.fsh',tail_option=True,
        expected_producer_global=row['expected']['expected_producer_global'],scope='External source/scene/workload hashes are provenance, not accepted by PNG syntax alone')


def qualify_decoded(decoded,row):
    """Decoded full rectangle/counters only; all separate runtime gates stay pending."""
    metadata=decoded.get('metadata',{})
    require(metadata.get('native_dimensions')==[2976,1672] and metadata.get('cloud_dimensions')==[744,418]
        and metadata.get('primary_steps')==[40,20] and metadata.get('world_time_low16')==12500
        and metadata.get('source_tag')==decoder.TAG and metadata.get('tail_option') is True
        and metadata.get('producer_stage')=='world0/deferred1.fsh' and metadata.get('consumer_stage')=='world0/final.fsh'
        and metadata.get('upscaling')==4,'Decoded exact source/scene/primary footprint differs')
    require(decoded.get('rays')==310992,'Incomplete decoded full cloud footprint')
    markers={int(k):v for k,v in decoded.get('producer_markers',{}).items()}
    require(markers and sum(markers.values())==310992,'Complete marker coverage missing')
    expected={137,138} if row['expected']['global_enabled'] else {128}
    require(set(markers)<=expected,'Decoded core/global/path differs')
    calls=decoded.get('called_cells');breaks=decoded.get('actual_order4_breaks');orders=decoded.get('actual_executed_orders')
    require(type(calls) is int and type(breaks) is int and calls>=0 and 0<=breaks<=calls and orders==8*calls-4*breaks,'Executed-order aggregate identity differs')
    require(row['expected']['global_enabled'] or breaks==0,'OFF control contains unexpected breaks')
    q=breaks/calls if calls else None
    verdict='stop-zero-calls' if calls==0 else 'stop-insufficient-calls' if calls<MINIMUM_MEANINGFUL_CALLS else 'OFF control only' if not row['expected']['global_enabled'] else \
        'stop-no-useful-coverage' if q<=.25 else 'promising-observed-coverage-only'
    return EVIDENCE|dict(status='OBSERVED_COUNTERS_PENDING_SEPARATE_RUNTIME_QUALITY_AND_SPEED',verdict=verdict,
        called_cells=calls,breaks=breaks,q=q,saved_order_fraction=q/2 if q is not None else None,
        unique_exponential_sites_proxy=27-12*q if q is not None else None,
        break_even_q=.25,minimum_meaningful_called_cells=MINIMUM_MEANINGFUL_CALLS,limitation='q>25% is 24→27/15 unique-exponential proxy break-even, not 25% measured GPU savings. Single checkerboard phase; optical digest is not quality or normal cost proof.')


def postanalysis_plan():
    return dict(prerequisites=[
        'Root reserves client/GPU; fresh OFF first, hideWorkloadGui=true. New actual generic Workload boundaries must export hide_gui=true/gui_scale2. No F1/CUA workaround; no fabricated field/oldUIfailedpromotion/overlay masking. qualify_gui_report validates request/property/rawboolean exports; completePNG remains independent.',
        'Prove initial launcher/session/Java birth/cwd/actual @VM args separately; all GPU/CPU/pass/detailed/probes false, measureWorkload/Only true, no benchmark/cameraQuality/pixelCoverage. Artifact discovery beforewarmup and input snapshots outside measurement clocks retained.',
        'Retain complete actual Workload report, configuration_files at both boundaries, full369option map/allarchive members/currentr4tenmods9roots4dirs/nativewindow focustrue/time12500/camera/weather/wetness/source/save/no fallback. Empty GPU/CPU streams mandatory. Generic validate_report requires CPU and may FAIL; preserve raw failure and qualify distinct queryfree components without fake rows.',
        'qualify_configuration allows only exact prepared copies of main/effects first -1 backups, complete known startup TOML/JSON/properties semantics/default sidecar deletion and schema1 Sodium salted cache source roles. Raw records retained; all unknown settings/files/rotations fail.',
        'Use qualify_gui_report, external_decoder_receipt then unchanged pinned decoder.decode_png on full workload-final.png. PNG native2976x1672, complete744x418ray rectangle/no masking, per-channel calibration/header/stamp/count/checksum/producer core2 globalOFF/ON/path allmandatory; actual generic hide_gui/scale plus full failclosed protocol, no inferred UI action.',
        'qualify_decoded enforces exact source tag/stages/time/primary40/20/fullray count/knownmarkers/aggregateorders=8calls-4breaks. Source/options, driver, codec, core/path, work eligibility, normal quality and performance remain separate. OFF breaks0; ON actualq=sum breaks/sumcalls. Stop zero/fewerthan1000calledcells (preregistered rejection-only coverage floor, not independent sample count) orq<=25%; do not change normal bound to rescue eligibility.',
        'q>25% only exceeds source unique-exp proxy break-even; further representative phases/scenes and normal diagnosticOFF source controls needed. No GPU timing until usefulcoverage/overhead/normalquality justified. Preserve raw failures/PNG/UI receipt and exactownedprocess terminalproof.'],
        decoder_api='scripts/decode_photon_contribution_tail_diagnostic.py: decode_png(path,receipt) -> observed counters pending external qualification',
        timing_status='FORBIDDEN in these shader-diagnostic profiles',performanceEvidence=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare',action='store_true');parser.add_argument('--dry-run',action='store_true');parser.add_argument('--verify',type=Path)
    parser.add_argument('--artifact-receipt',type=Path);parser.add_argument('--artifact-sha256')
    args=parser.parse_args()
    try:
        require(sum(bool(x) for x in (args.prepare,args.dry_run,args.verify))==1,'Select exactly one prepare/dry-run/verify')
        result=verify(json.loads(args.verify.read_text())) if args.verify else plan() if args.dry_run else prepare(artifact_receipt=args.artifact_receipt,artifact_sha256=args.artifact_sha256)
        print(json.dumps(result,indent=2,sort_keys=True))
    except (OSError,ValueError,KeyError,zipfile.BadZipFile) as error:parser.exit(1,'Cannot prepare cloud-tail diagnostic: '+str(error)+'\n')


if __name__=='__main__':main()
