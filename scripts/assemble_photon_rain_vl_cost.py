#!/usr/bin/env python3
"""Assemble the immutable advisor's three rain/VL ceiling controls; never launch."""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import sys

import prepare_photon_rain_vl_cost as advisor
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

cost, matrix, copies = advisor.cost,advisor.cost.matrix,advisor.cost.copies
OUTPUT = ROOT / 'run-workloads/rain-vl-cost-r1'
BUILD = ROOT / 'build/rain-vl-runtime-r1'
MARKER = 'rain-vl-cost-preparation.json'
FINAL = 'THREE_UNUSED_CURRENT_R10_RAIN_VL_CEILING_CONTROLS_READY_ROOT_QUIET_REVIEW'
FREEZE = ROOT / 'build/rain-vl-cost-r1/freeze.json'
FREEZE_SHA256 = '265608afffd6de0c3269d3eab701bb42525ce1c21633dfdeca1f516e02506949'
RECIPE = ROOT / 'build/rain-vl-cost-r1/source-recipe.json'
RECIPE_SHA256 = '8a73a34015b22731da782bdeee62c6b956f9694848120a0d21add31ac771ac26'
ORDER, MODES = advisor.ORDER,advisor.MODES
EVIDENCE = dict(advisor.EVIDENCE)


def command(profile): return advisor.command(profile)


def plan(output=OUTPUT,build=BUILD,root=ROOT):
    root = Path(root).resolve()
    advisor.require(not Path(output).is_symlink() and not Path(build).is_symlink(),'Symlink output unsupported')
    output,build = Path(output).resolve(),Path(build).resolve()
    advisor.require(output == root / 'run-workloads/rain-vl-cost-r1' and build == root / 'build/rain-vl-runtime-r1','Only owned fresh assembly families allowed')
    advisor.require(not output.exists() and not build.exists(),'Preserve existing output; use --verify')
    return EVIDENCE | dict(schema=1,output=str(output),build=str(build),scene='coastalwater',arms=list(ORDER),modes=list(MODES),
        profile_count=3,weather='rain',fixed_wetness=1,initial_warmup_seconds=60,measurement_seconds=20,
        framebuffer=[2976,1672],window=[1488,836],requested_chunks=24,gpu_sample_interval=5,minimum_gpu_samples=60,
        coarse_cpu_scopes=['game-render','render-and-blit','run-tick','swap-and-events'],client_gpu_owner='root',
        launch_authorized=False,owned_live_processes=[],denominator='Matched controlled rain VLtrue; not default-weather speed or mod gain',
        minimum_each_control_fps_gain=.05,maximum_each_control_frame_and_gpu_drift=.10,stop_procedure=cost.canonical.STOP)


def qualify_recipe():
    advisor.require(file_hash(FREEZE) == FREEZE_SHA256 and file_hash(RECIPE) == RECIPE_SHA256,'Immutable advisor freeze/recipe changed')
    freeze = json.loads(FREEZE.read_text())
    advisor.require(freeze.get('status') == 'SOURCE_READY_NO_RUNTIME_ASSEMBLY' and freeze.get('profiles_assembled') is False,'Advisor source freeze scope differs')
    for field in ('source_files_sha256','recipe_dependency_files_sha256'):
        for name,pin in freeze[field].items(): advisor.require(file_hash(Path(name)) == pin,'Frozen advisor source/dependency drift: ' + name)
    recipe = json.loads(RECIPE.read_text())
    advisor.require(recipe['source_gate'] == advisor.source_gate(),'Pinned conditional rain source gate differs')
    advisor.require([r['arm'] for r in recipe['recipes']] == list(ORDER),'Advisor recipe order differs')
    return dict(freeze=dict(path=str(FREEZE),sha256=FREEZE_SHA256),recipe=dict(path=str(RECIPE),sha256=RECIPE_SHA256),
        retained_source_freeze=freeze,source_recipe=recipe)


def maintained_sources():
    paths = [ROOT / 'scripts/assemble_photon_rain_vl_cost.py',ROOT / 'tests/test_photon_rain_vl_cost_assembly.py']
    for module in tuple(sys.modules.values()):
        name = getattr(module,'__file__',None)
        if name:
            path = Path(name).resolve()
            if path.suffix == '.py' and path.is_relative_to(ROOT / 'scripts'): paths.append(path)
    return {str(p):file_hash(p) for p in sorted(set(paths))}


def qualify_runtime_receipt(path,pin):
    external,dirs,jar = cost.qualify_runtime_receipt(path,pin)
    advisor.require(jar['sha256'] == advisor.JAR_SHA256,'Current main differs from frozen advisor e86 requirement')
    return external,dirs,jar


def validate_manifest(manifest,native):
    arm = manifest.get('arm'); advisor.require(arm in ORDER,'Unknown rain arm')
    mode = MODES[ORDER.index(arm)]
    advisor.require(manifest.get('schema') == 1 and manifest.get('scene') == 'coastalwater'
        and manifest.get('configuration_only') is False and manifest.get('mode') == mode
        and manifest.get('source_world_sha256') == cost.quality.WORLD_SHA256['coastalwater'],'Rain scene/mode/world differs')
    source = manifest.get('source_qualification',{}); expected = manifest.get('experiment_arms',{}).get('control',{})
    members = source.get('complete_member_sha256',{})
    advisor.require(len(members) == 531 and matrix.canonical_options_hash(members) == source.get('complete_member_map_sha256')
        and source.get('archive_sha256') == expected.get('archive_sha256') == cost.canonical.ORIGINAL_SHA256,'Original source531/LICENSE receipt differs')
    options = advisor.option_maps(native)[arm]
    advisor.require(expected.get('expected_effective_options') == options
        and expected.get('expected_effective_options_sha256') == matrix.canonical_options_hash(options),'Complete only-VL map differs')
    advisor.require(manifest.get('control') == advisor.rain_control(source['canonical_control'])
        and expected.get('requested_main_mod_present') is True and expected.get('dh_present') is False,'Rain/native/core/noDH controls differ')
    return dict(arm=arm,mode=mode,member_count=531,option_count=366)


def validate_pairs(rows):
    advisor.require([r['arm'] for r in rows] == list(ORDER) and [r['mode'] for r in rows] == list(MODES),'Missing/reordered rain bracket')
    signatures = []
    for row in rows:
        writable = row['prepared_writable_inventory']; request = dict(writable['sidecar']['requested']); request.pop('VL',None)
        signatures.append(dict(settings=writable['settings'],identities=writable['nonshader_identities'],login=writable['login_caches'],
            other_requested_shader_options=request,defaults=writable['sidecar']['defaults']))
    advisor.require(all(s == signatures[0] for s in signatures),'Other complete settings differ beyond VL')
    return dict(complete_other_settings_match=True,permitted_shader_options=['VL'])


def prepare(receipt_path,receipt_pin,output=OUTPUT,build=BUILD):
    operation = plan(output,build); matrix.require_exclusive_runtime(); frozen_recipe = qualify_recipe()
    scene = cost.quality.qualify_scene('coastalwater'); external,dirs,jar = qualify_runtime_receipt(receipt_path,receipt_pin)
    advisor.require(scene['source_qualification']['original_source_dh'] is None,'Canonical coastal source optional DH differs')
    recipes = frozen_recipe['source_recipe']['recipes']; native = scene['effective_maps']['original']; maps = advisor.option_maps(native)
    controls = advisor.rain_control(scene['control']); sources = maintained_sources(); compiled = matrix.development_fingerprints()
    runtime = cost.baseline.control_prep.current_runtime_sources(); output,build = Path(operation['output']),Path(operation['build'])
    with unlocked_world(scene['world']):
        fingerprint = world_fingerprint(scene['world']); advisor.require(fingerprint == frozen_recipe['source_recipe']['canonical_source_world_sha256'],'Canonical stopped coastal save differs')
        output.mkdir(parents=True); build.mkdir(parents=True); marker = output / MARKER
        state = EVIDENCE | dict(schema=1,status='PREPARING',created_at_utc=datetime.now(timezone.utc).isoformat(),operation=operation,
            frozen_advisor_recipe=frozen_recipe,source_qualification=scene['source_qualification'],source_world_sha256=fingerprint,
            maintained_sources_sha256=sources,runtime_sources_sha256=runtime,development_runtime_inputs=compiled,
            independent_artifact_receipt=external,auxiliary_directory_identities=dirs,production_jar=jar,
            launch_plan=[],launch_authorized=False,owned_live_processes=[],postanalysis_plan=postanalysis_plan())
        copies.write_json(marker,state)
        try:
            inventories = {}
            for arm,mode,recipe in zip(ORDER,MODES,recipes):
                requested = scene['requested_options']['original'] | {'VL':mode}
                inventory = cost.parser.discover(scene['archive'],requested,build,arm)
                advisor.require(inventory['options'] == maps[arm] == recipe['expected_effective_options']
                    and inventory['option_count'] == 366 and inventory['sha256'] == recipe['expected_effective_options_sha256'],
                    'Actual full Iris inventory differs from frozen advisor recipe')
                inventories[arm] = inventory
            inventory_path = build / 'option-inventory.json'; copies.write_json(inventory_path,inventories)
            state['complete_option_inventory'] = dict(path=str(inventory_path),sha256=file_hash(inventory_path))
            for arm,mode,recipe in zip(ORDER,MODES,recipes):
                base = output / arm; profile = base / 'control'; advisor.require(recipe['profile'] == str(profile),'Frozen advisor profile association differs')
                inventory = inventories[arm]; requested = scene['requested_options']['original'] | {'VL':mode}
                expected = dict(archive=scene['archive'].name,archive_sha256=cost.canonical.ORIGINAL_SHA256,
                    requested_options=requested,expected_effective_options=inventory['options'],expected_effective_options_sha256=inventory['sha256'],
                    requested_main_mod_present=True,main_mod_presence_verified=False,global_enabled=True,dh_present=False)
                manifest = EVIDENCE | dict(schema=1,scene='coastalwater',arm=arm,mode=mode,configuration_only=False,
                    source_world_sha256=fingerprint,control=controls,experiment_arms={'control':expected},source_qualification=scene['source_qualification'])
                validate_manifest(manifest,native); cost.write_control_files(base,controls,manifest)
                storage_copytree(scene['profile'],profile,ignore=shutil.ignore_patterns(*cost.canonical.TRANSIENT))
                advisor.require(not any((profile / 'mods').glob('*.jar')),'Extra profile mods/DH')
                (profile / 'saves').mkdir(exist_ok=True); storage_copytree(scene['world'],profile / 'saves' / WORLD,ignore=shutil.ignore_patterns('session.lock'))
                packs = profile / 'shaderpacks'; packs.mkdir(exist_ok=True); archive = packs / scene['archive'].name; shutil.copy2(scene['archive'],archive)
                archive.with_name(archive.name+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(requested.items())))
                iris = matrix.read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive.name
                (profile / 'config/iris.properties').write_text(''.join(f'{k}={v}\n' for k,v in sorted(iris.items())))
                (profile / 'config/shaderlod-client.toml').write_text(cost.baseline.coverage.fresh_config('adaptive'))
                effects = cost.baseline.coverage.effects_config('inherit'); cost.quality.forest.validate_effects(effects)
                (profile / 'config/shaderlod-effects-client.toml').write_text(effects)
                request = recipe['request']; advisor.require(recipe['command'] == command(profile) and recipe['control'] == controls,'Frozen exact command/rain control differs')
                copies.write_json(profile / 'workload-request.json',request); snapshot = cost.baseline.profile_snapshot(profile)
                advisor.require(snapshot['copied_world_sha256'] == fingerprint and snapshot['inputs']['shader_sha256'] == cost.canonical.ORIGINAL_SHA256,'Copied source/save differs')
                state['launch_plan'].append(dict(arm=arm,mode=mode,scene='coastalwater',profile=str(profile),command=command(profile),request=request,
                    expected=expected,inputs=snapshot['inputs'],prepared_profile=snapshot,
                    prepared_writable_inventory=snapshot['prepared_writable_inventory'],owner='root',status='not-launched'))
            advisor.require(world_fingerprint(scene['world']) == fingerprint and maintained_sources() == sources
                and cost.baseline.control_prep.current_runtime_sources() == runtime and matrix.development_fingerprints() == compiled,
                'Source/world/current runtime changed during assembly')
            state['prepared_pairs_comparison'] = validate_pairs(state['launch_plan']); state['status'] = FINAL
            verify(state); copies.write_json(marker,state)
        except BaseException as failure:
            state.update(status='FAILED_PREPARATION',error=str(failure)); copies.write_json(marker,state); raise
    return state


def verify(state):
    advisor.require(state.get('status') == FINAL,'Incomplete assembly')
    advisor.require(qualify_recipe() == state['frozen_advisor_recipe'],'Immutable advisor recipe/source changed')
    for field in ('maintained_sources_sha256','runtime_sources_sha256'):
        for name,pin in state[field].items(): advisor.require(file_hash(Path(name)) == pin,'Frozen assembler/runtime source drift: ' + name)
    advisor.require(cost.baseline.control_prep.current_runtime_sources() == state['runtime_sources_sha256']
        and matrix.development_fingerprints() == state['development_runtime_inputs'],'Complete runtime source/class drift')
    entry = state['independent_artifact_receipt']; advisor.require(qualify_runtime_receipt(entry['path'],entry['sha256']) ==
        (entry,state['auxiliary_directory_identities'],state['production_jar']),'Explicit current R10 source/artifact/JAR drift')
    for name,pin in state['source_qualification']['source_files_sha256'].items(): advisor.require(file_hash(Path(name)) == pin,'Canonical source drift')
    advisor.require(world_fingerprint(cost.canonical.SOURCE / 'saves' / WORLD) == state['source_world_sha256'],'Canonical stopped source world drift')
    item = state['complete_option_inventory']; advisor.require(file_hash(Path(item['path'])) == item['sha256'],'Complete Iris inventory drift')
    inventories = json.loads(Path(item['path']).read_text()); maps = advisor.option_maps(inventories['vl-before']['options'])
    for row,recipe in zip(state['launch_plan'],state['frozen_advisor_recipe']['source_recipe']['recipes']):
        arm,profile = row['arm'],Path(row['profile']); observed = inventories[arm]
        advisor.require(profile == OUTPUT / arm / 'control' and observed['options'] == maps[arm]
            and observed['option_count'] == 366,'Complete original source/map/profile association differs')
        for name,pin in observed['parser_dependency_sha256'].items(): advisor.require(file_hash(Path(name)) == pin,'Pinned Iris dependency drift')
        advisor.require(file_hash(Path(observed['log'])) == observed['log_sha256'],'Complete discovery log drift')
        advisor.require(row['command'] == recipe['command'] == command(profile) and row['request'] == recipe['request']
            and cost.baseline.profile_snapshot(profile) == row['prepared_profile'],'Frozen exact recipe/fresh profile/configuration/world/argv drift')
        advisor.require(not (profile / 'workload-measurement.json').exists(),'Profile used; preserve historical result')
        manifest = json.loads((profile.parent / 'workload-manifest.json').read_text()); validate_manifest(manifest,inventories['vl-before']['options'])
        advisor.require(row['expected'] == manifest['experiment_arms']['control'] and row['request'] == json.loads((profile / 'workload-request.json').read_text()),'Frozen actual manifest/request row differs')
        cost.quality.forest.validate_effects((profile / 'config/shaderlod-effects-client.toml').read_text())
    advisor.require(validate_pairs(state['launch_plan']) == state['prepared_pairs_comparison'],'Complete prepared pair comparison changed')
    return EVIDENCE | dict(status='verified-three-current-R10-unused-rain-VL-ceiling-controls',launch_authorized=False,owned_live_processes=[])


def qualify_rain(report,request): return advisor.qualify_rain(report,request)
def screen(frame_ms,gpu_ms): return advisor.screen(frame_ms,gpu_ms)


def postanalysis_plan():
    return advisor.postanalysis_plan() | dict(serializer_boundary='Python source_qualification.original_source_dh is explicitly None. Default Gson Workload report can omit exactly this null member; retain raw original manifest and source-backed typed omission, never generic null stripping.',
        quality_scope='VLoff is deliberate major-effect removal, no gross-quality prerequisite or quality acceptance; only a controlled-rain ceiling.',
        control_strength='Actual biome_may_rain/fog coefficient/palette/ray distribution unknown; CPU rain_strength_source/wetness/rain boundaries are not GL uniform or rendering consumption proof.')


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--dry-run',action='store_true'); p.add_argument('--verify',type=Path)
    p.add_argument('--artifact-receipt',type=Path); p.add_argument('--artifact-sha256'); args = p.parse_args()
    if args.verify:
        advisor.require(args.verify.resolve() == OUTPUT / MARKER,'Only owned marker allowed'); matrix.require_exclusive_runtime()
        result = verify(json.loads(args.verify.read_text()))
    elif args.dry_run: result = plan()
    else:
        advisor.require(args.artifact_receipt is not None and args.artifact_sha256 is not None,'Explicit current R10 receipt path/SHA required')
        state = prepare(args.artifact_receipt,args.artifact_sha256)
        result = dict(status=state['status'],marker=str(OUTPUT / MARKER),marker_sha256=file_hash(OUTPUT / MARKER),profiles=3,
            launch_authorized=False,owned_live_processes=[])
    print(json.dumps(result,sort_keys=True,indent=2))


if __name__ == '__main__': main()
