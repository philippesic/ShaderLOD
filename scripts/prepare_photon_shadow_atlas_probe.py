#!/usr/bin/env python3
"""Prepare separate query-free physical shadow allocation controls; never launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree
import argparse
import copy
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import sys

import prepare_photon_shadow_atlas_screen as screen
from prepare_reduced_effects_profiles import require, write_json
from setup_comparison import ROOT, WORLD, file_hash, world_fingerprint, unlocked_world

OUTPUT = ROOT / 'run-workloads/shadow-atlas-probe-r1'
BUILD = ROOT / 'build/shadow-atlas-probe-r1'
MARKER = 'shadow-atlas-probe-preparation.json'
SCREEN_SHA256 = '6bc3b45f178c4fc77633c8e973cd0e49ee5daa35fd17d9240b3240a53a3406ef'
RECEIPT_SHA256 = 'd8f80b1c79ba40d25dc14c281778211e8202bb1c1c1604d9dab3e77762d0af81'
JAR_SHA256 = '5f84d03813de4e6f7c70d2d2c95f85a404603975d6dae2b4dc8be775778c2b03'
EVIDENCE = dict(performanceEvidence=False, adapter_gain=False, quality='unaccepted',
    diagnostic_only=True, runtime='unverified', evidence_class='bound-shadow-depth-allocation-probe',
    stage_identity='unknown: numeric GL program and draw origin only',
    sample_scope='First draw per numeric GL program ID, possibly before canonical scene settles; not timed or settled coverage')


def command(profile):
    argv = screen.command(profile,'quality')
    require(argv.count('-PcloudSamplerProbe=false') == 1, 'Known parent probe flag missing/duplicate')
    return ['-PcloudSamplerProbe=true' if v == '-PcloudSamplerProbe=false' else v for v in argv]


def source_files():
    paths = [ROOT / 'scripts/prepare_photon_shadow_atlas_probe.py', ROOT / 'tests/test_photon_shadow_atlas_probe.py']
    for module in tuple(sys.modules.values()):
        name = getattr(module,'__file__',None)
        if name:
            path = Path(name).resolve()
            if path.suffix == '.py' and path.is_relative_to(ROOT / 'scripts'): paths.append(path)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def plan(output=OUTPUT,build=BUILD,root=ROOT):
    root = Path(root).resolve()
    require(not Path(output).is_symlink() and not Path(build).is_symlink(), 'Symlink output unsupported')
    output,build = Path(output).resolve(),Path(build).resolve()
    require(output == root / 'run-workloads/shadow-atlas-probe-r1' and build == root / 'build/shadow-atlas-probe-r1', 'Only owned fresh family allowed')
    require(not output.exists() and not build.exists(), 'Preserve historical probes; use verify')
    return EVIDENCE | dict(output=str(output),build=str(build),order=['shadow2048','shadow1536'],
        initial_warmup_seconds=60,measurement_seconds=8,framebuffer=[2976,1672],window=[1488,836],
        launch_authorized=False,client_gpu_owner='root',owned_live_processes=[],stop_procedure=screen.canonical.STOP)


def prepare(output=OUTPUT,build=BUILD):
    operation = plan(output,build); screen.matrix.require_exclusive_runtime()
    parent = screen.OUTPUT / screen.MARKER
    require(file_hash(parent) == SCREEN_SHA256, 'Exact finalized five-profile receipt differs')
    original = json.loads(parent.read_text()); screen.verify(original)
    require(original['independent_artifact_receipt']['sha256'] == RECEIPT_SHA256 and original['production_jar']['sha256'] == JAR_SHA256,
        'Current root-approved r6/JAR differs')
    output,build = Path(operation['output']),Path(operation['build']); output.mkdir(parents=True); build.mkdir(parents=True)
    sources = source_files()
    state = EVIDENCE | dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
        parent_preparation=dict(path=str(parent),sha256=SCREEN_SHA256),source_qualification=original['source_qualification'],
        complete_option_inventory=original['complete_option_inventory'],source_world_sha256=original['source_world_sha256'],
        independent_artifact_receipt=original['independent_artifact_receipt'],auxiliary_directory_identities=original['auxiliary_directory_identities'],
        development_runtime_inputs=original['development_runtime_inputs'],runtime_sources_sha256=original['runtime_sources_sha256'],
        production_jar=original['production_jar'],maintained_source_files_sha256=sources,launch_plan=[],
        launch_authorized=False,owned_live_processes=[],postanalysis_plan=postanalysis_plan())
    marker = output / MARKER; write_json(marker,state)
    try:
        for source_row in original['launch_plan'][:2]:
            resolution = source_row['shadow_resolution']; arm = 'shadow'+str(resolution)
            source = Path(source_row['profile']); base = output / arm; base.mkdir(); profile = base / 'control'
            for name in ('control.json','workload-manifest.json'): shutil.copy2(source.parent / name,base / name)
            manifest_path = base / 'workload-manifest.json'; manifest = json.loads(manifest_path.read_text())
            manifest.update(EVIDENCE); manifest['kind'] = 'allocation-probe'; screen.validate_arm_manifest(manifest); write_json(manifest_path,manifest)
            with unlocked_world(source / 'saves' / WORLD):
                storage_copytree(source,profile,ignore=shutil.ignore_patterns(*screen.canonical.TRANSIENT))
                (profile / 'saves').mkdir(exist_ok=True)
                storage_copytree(source / 'saves' / WORLD,profile / 'saves' / WORLD,ignore=shutil.ignore_patterns('session.lock'))
            storage_copytree(source / 'shaderpacks',profile / 'shaderpacks')
            request = copy.deepcopy(source_row['request']); request.update(EVIDENCE)
            request.update(run_id=output.name+'-'+arm,case='photon-shadow-allocation-'+arm,cloud_sampler_probe=True,
                gpu_queries=False,cpu_timing=False,hide_workload_gui=True)
            write_json(profile / 'workload-request.json',request)
            snapshot = screen.baseline.profile_snapshot(profile)
            require(snapshot['copied_world_sha256'] == state['source_world_sha256'], 'Copied world differs')
            require(snapshot['inputs']['shader_sha256'] == screen.canonical.ORIGINAL_SHA256 and not any((profile / 'mods').glob('*.jar')), 'Original/minimal/noDH profile differs')
            row = dict(arm=arm,kind='allocation-probe',shadow_resolution=resolution,profile=str(profile),request=request,
                command=command(profile),expected=source_row['expected'],inputs=snapshot['inputs'],prepared_profile=snapshot,
                prepared_writable_inventory=snapshot['prepared_writable_inventory'],owner='root',status='not-launched')
            state['launch_plan'].append(row)
        require(file_hash(parent) == SCREEN_SHA256, 'Original finalized controls rewritten')
        state['status'] = 'CURRENT_RUNTIME_TWO_FRESH_ALLOCATION_PROBES_READY'; verify(state); write_json(marker,state)
    except BaseException as failure:
        state.update(status='FAILED',error=str(failure)); write_json(marker,state); raise
    return state


def verify(state):
    require(state.get('status') == 'CURRENT_RUNTIME_TWO_FRESH_ALLOCATION_PROBES_READY', 'Incomplete preparation')
    for name,pin in state['maintained_source_files_sha256'].items(): require(file_hash(Path(name)) == pin, 'Probe recipe drift: '+name)
    for name,pin in state['runtime_sources_sha256'].items(): require(file_hash(Path(name)) == pin, 'Runtime source drift: '+name)
    require(screen.matrix.development_fingerprints() == state['development_runtime_inputs'], 'Class/resource freeze drift')
    entry = state['independent_artifact_receipt']
    require(screen.baseline.coverage.qualify_receipt(entry['path'],entry['sha256']) == (entry,state['auxiliary_directory_identities']), 'Current r6 direct artifact drift')
    require(entry['sha256'] == RECEIPT_SHA256 and file_hash(Path(state['production_jar']['path'])) == JAR_SHA256, 'Current receipt/JAR pin drift')
    parent = state['parent_preparation']; require(file_hash(Path(parent['path'])) == parent['sha256'] == SCREEN_SHA256, 'Finalized original controls changed')
    inventory = state['complete_option_inventory']; require(file_hash(Path(inventory['path'])) == inventory['sha256'], 'Complete Iris discovery drift')
    require(len(state['launch_plan']) == 2, 'Missing probe arm')
    for row,resolution in zip(state['launch_plan'],(2048,1536)):
        require(row['arm'] == 'shadow'+str(resolution) and row['shadow_resolution'] == resolution and row['command'] == command(row['profile']), 'Probe order/argv drift')
        require(screen.baseline.profile_snapshot(row['profile']) == row['prepared_profile'], 'Fresh probe files/world drift')
        require(not (Path(row['profile']) / 'workload-measurement.json').exists() and not (Path(row['profile']) / 'logs/cloud-sampler-state.json').exists(), 'Probe used; preserve raw capture')
        screen.validate_arm_manifest(json.loads((Path(row['profile']).parent / 'workload-manifest.json').read_text()))
    return EVIDENCE | dict(status='verified-two-current-runtime-unused-probes',launch_authorized=False,owned_live_processes=[])


def qualify_probe(probe,resolution):
    """Pure actual bound-texture qualification; not source-stage or scene/time association."""
    require(type(resolution) is int and resolution in (2048,1536), 'Unsupported resolution')
    require(probe.get('schema') == 1 and probe.get('probe') == 'shaderlod.dev.cloudSamplerProbe', 'Wrong native probe producer/schema')
    require(probe.get('gl_renderer') == 'Apple M3 Max' and probe.get('gl_version') == '4.1 Metal - 91.7', 'Driver/hardware differs')
    require(probe.get('program_limit_reached') is False and probe.get('sampler_objects_supported') is True, 'Unknown/truncated sampler inventory')
    programs = probe.get('programs'); require(isinstance(programs,list) and programs and len(programs) == probe.get('captured_programs'), 'Incomplete program inventory')
    ids = set(); observations = []; names = set()
    for program in programs:
        program_id = program.get('current_program')
        require(type(program_id) is int and program_id > 0 and program_id not in ids, 'Invalid/duplicate program ID'); ids.add(program_id)
        require(program.get('linked') is True and 'capture_failure' not in program and program.get('uniform_scan_truncated') is False
            and program.get('uniform_name_length_truncated') is False, 'Unlinked/failed/truncated actual program readback')
        require(type(program.get('active_texture_before')) is int and program['active_texture_before'] == program.get('active_texture_after'), 'Active texture unit not restored')
        require(program.get('draw_origin') in ('iris-fullscreen-quad','sodium-raster-submission'), 'Unknown draw origin')
        for name,sampler in program.get('samplers',{}).items():
            if name not in ('shadowtex0','shadowtex1') or 'bound_texture' not in sampler: continue
            require(sampler.get('active_uniform_name') == name and sampler.get('active_uniform_size') == 1
                and sampler.get('active_uniform_type') in (35678,35682) and sampler.get('texture_target') == 3553
                and type(sampler.get('bound_texture')) is int and sampler['bound_texture'] > 0
                and type(sampler.get('uniform_location')) is int and sampler['uniform_location'] >= 0
                and type(sampler.get('texture_unit')) is int and 0 <= sampler['texture_unit'] < probe['max_combined_texture_units'], 'Unknown/non-scalar/unbound shadow sampler')
            linked = program.get('linked_uniforms',{}).get(name)
            require(linked == {'type':sampler['active_uniform_type'],'size':1}, 'Linked inventory/sampler identity differs')
            image = sampler.get('texture_level_zero',{})
            require(image.get('status') == 'queried_without_gl_error_consumption' and image.get('level') == 0 and image.get('dimensions') == 2
                and type(image.get('width')) is int and type(image.get('height')) is int and image['width'] == image['height'] == resolution
                and image.get('internal_format') in (33189,33190,33191,36012), 'Actual shadow depth allocation differs/unknown')
            sampling = sampler.get('effective_sampling_parameters',{})
            require(sampling.get('status') == 'queried_without_gl_error_consumption' and sampling.get('min_filter') in (9728,9729)
                and sampling.get('mag_filter') in (9728,9729) and sampling.get('compare_mode') in (0,34894)
                and type(sampler.get('bound_sampler')) is int and sampler['bound_sampler'] >= 0
                and isinstance(sampler.get('effective_parameter_source'),str), 'Unknown effective filter/compare/sampler object')
            observations.append(dict(program_id=program_id,draw_origin=program['draw_origin'],active_uniform_name=name,
                active_uniform_type=sampler['active_uniform_type'],bound_texture=sampler['bound_texture'],bound_sampler=sampler['bound_sampler'],
                texture_unit=sampler['texture_unit'],allocation=image,effective_sampling_parameters=sampling,
                effective_parameter_source=sampler['effective_parameter_source']))
            names.add(name)
    require(names == {'shadowtex0','shadowtex1'}, 'Both actual bound shadow depth aliases required')
    return EVIDENCE | dict(status='OBSERVED_BOUND_SHADOW_DEPTH_ALLOCATION',resolution=resolution,
        observed_aliases=sorted(names),observations=observations,
        limitation='Numeric program/draw origin and linked alias/bound image only. No stage names, texture contents, physical fetch counts, settled scene association, completeness or performance proof. Inspect actual GL logs; probe never consumes glGetError.')


def postanalysis_plan():
    return dict(scope='Same original531members/full366maps/nativecoast/currentr6JAR as existing quality controls; only stockresolution and new observer enable differ. Both timersOFF, hiddenGUItrue,60warm8; observed firstdraw may precede warmup/canonicalscene.',
        qualification='Fresh probe file newer than exactownedlaunch and actual Java property cloudSamplerProbe=true; full strict Workload source/options/config/artifacts/nativewindow/time/gui guards remain. Generic matrix unconditional CPU gate does not apply to emptyCPU stream. qualify_probe requires alllinkedentries healthy/no truncation/unitrestoration and bothshadowtex0/1depthimages exactR; programorigin neverstageidentity.',
        remaining='Separate original queryfree quality pair required with observerOFF. Only grossstillquality success authorizes unopened cost; probe getters may stall and never supply speed or settledquality evidence.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run',action='store_true'); parser.add_argument('--prepare',action='store_true'); parser.add_argument('--verify',type=Path)
    args = parser.parse_args()
    try:
        require(sum(bool(x) for x in (args.dry_run,args.prepare,args.verify)) == 1, 'Select one operation')
        result = verify(json.loads(args.verify.read_text())) if args.verify else plan() if args.dry_run else prepare()
        print(json.dumps(result,indent=2,sort_keys=True))
    except (OSError,ValueError,KeyError,TypeError) as failure: parser.exit(1,'Cannot prepare shadow allocation probes: '+str(failure)+'\n')


if __name__ == '__main__': main()
