#!/usr/bin/env python3
"""Freeze original Photon native/.75 quality controls and a conditional cost screen.

Uses existing source, artifact and WorkloadSession helpers. Never builds or launches.
"""
from __future__ import annotations
from run_storage import copytree as storage_copytree

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import zipfile

import prepare_photon_taau_screen as quality
import prepare_photon_taau_cost_bracket as cost
import qualify_volume_fast_artifacts as artifacts
from prepare_reduced_effects_profiles import require, write_json
from run_proof_matrix import canonical_options_hash, development_fingerprints, profile_inputs, normalize_requested_options, require_exclusive_runtime
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

OUTPUT = ROOT / 'run-workloads/taau75-calibration-r1'
RECEIPT = ROOT / 'build/taau-artifact-evidence-r2/artifact-receipt-clock-fixed.json'
RECEIPT_SHA256 = '4b3e734779e8e885fc0825748582e50da1a7b3a31db6d4688753b68e6c31bef0'
GROUPS = {'still': ('native', 'taau75'), 'motion': ('native', 'taau75'),
          'cost-bracket': ('native-before', 'taau75', 'native-after')}
EVIDENCE = cost.EVIDENCE | dict(evidence_class='stock-lower-quality-calibration-preparation', runtime='unverified')


def option_maps(effective):
    # Validate the complete canonical loaded map, including inactive default .75.
    quality.option_maps(effective)
    return {'native': dict(effective), 'taau75': effective | {'TAAU': 'true'}}


def plan(output=OUTPUT, source=quality.SOURCE, root=ROOT):
    result = quality.plan(output, source, root)
    result.pop('deferred_arm', None)
    return result | EVIDENCE | dict(arms=['native', 'taau75'], kinds=list(GROUPS),
        groups={k: list(v) for k,v in GROUPS.items()}, changed_effective_options=['TAAU'],
        scale='0.75 (unchanged source-defined default; inactive native)',
        initial_warmup_seconds=30, quality_capture_seconds=5, cost_capture_seconds=20,
        cost_gpu_sample_interval=5, minimum_positive_unique_gpu_samples=60,
        maximum_original_drift_fraction=.10, minimum_frame_reduction_fraction=.05,
        launch_authorized=False, launch_status='HOLD: root owns client/GPU; stills first; cost conditional on root gross-quality review',
        validation='Pure plan only; exact source/save/artifact qualification pending')


def qualify_source():
    scene = quality.qualify_source()
    native = scene['qualification']['effective_option_maps']['native']
    maps = option_maps(native)
    with zipfile.ZipFile(scene['archive']) as archive:
        defaults = quality.archive_defaults(archive)
        settings = archive.read('shaders/settings.glsl').decode()
        global_source = archive.read('shaders/include/global.glsl').decode()
        properties = archive.read('shaders/shaders.properties').decode()
    require(defaults.get('TAAU_RENDER_SCALE') == '0.75' and defaults.get('TAAU') == 'false',
            'Original source-defined TAAU defaults differ')
    require('#define TAAU_RENDER_SCALE 0.75' in settings and '#if defined TAA && defined TAAU' in global_source,
            'Original temporal scale selection differs')
    require(re.search(r'^program\.world0/composite3\.enabled\s*=\s*TAAU\s*$', properties, re.MULTILINE),
            'Original extra TAAU pass selection differs')
    requested = scene['requested_options']['native']
    requests = {'native': dict(requested), 'taau75': requested | {'TAAU': 'true'}}
    baseline = normalize_requested_options(defaults, requests['native'])
    for arm, values in requests.items():
        normalized = normalize_requested_options(defaults, values)
        require(normalized == (baseline if arm=='native' else baseline | {'TAAU':'true'}),
                'Requested complete declared-default map differs')
        require(normalized['TAAU_RENDER_SCALE'] == '0.75', 'Requested inactive default scale changed')
    scene['requested_options'] = requests
    scene['qualification'].update(effective_option_maps=maps,
        effective_option_hashes={k: canonical_options_hash(v) for k,v in maps.items()},
        dynamic_predicted_option_counts={k: len(v) for k,v in maps.items()},
        changed_effective_options={'TAAU': ['false', 'true']},
        source_defined_scale='0.75',
        option_prediction_scope='Complete canonical loaded map plus only discovered TAAU toggle; original defaults and prior exact-Iris discovery checked. No new .75 Jcpp/driver compilation claimed.')
    return scene


def postanalysis_plan():
    result = cost.postanalysis_plan()
    result['cross_arm'] = [text.replace('taau90', 'taau75').replace(
        'TAAU false→true and inactive0.75→0.90', 'TAAU false→true; scale0.75 unchanged') for text in result['cross_arm']]
    result['quality_first'] = ['Fresh query-free native still then .75 still using existing WorkloadSession; complete actual options/config/artifacts/window/temporal guards required',
        'Root reviews gross still quality before authorizing any cost arm; reject conspicuous texture/foliage, water/glint/reflection, cloud/shadow, border or trail degradation',
        'Separate prepared native/.75 camera controls remain query-free and untimed; full motion/representative-scene acceptance and temporal phase alignment are still unverified']
    result['decision_arithmetic'] = 'Reuse prepare_photon_taau_cost_bracket.screen: >5% mean total-frame reduction against BOTH native controls, GPU lower against BOTH, native frame/GPU drift<=10%; guards first; otherwise stop or inconclusive'
    result.update(EVIDENCE)
    return result


def prepare(output=OUTPUT, receipt=RECEIPT, receipt_sha256=RECEIPT_SHA256):
    operation = plan(output)
    require_exclusive_runtime()
    scene = qualify_source()
    receipt = Path(receipt).resolve()
    require(receipt_sha256 == RECEIPT_SHA256 and file_hash(receipt) == receipt_sha256,
            'Root-reviewed clock-fixed mod-absent receipt pin differs')
    external = json.loads(receipt.read_text())
    identities = {str(ROOT/n): artifacts.directory_identity(ROOT/n) for n in cost.DIRECTORIES}
    artifacts.validate_expected_receipt(external, identities, include_shaderlod=False)
    require(len(external['expected_loaded_mods']) == 9 and len(external['loaded_artifact_inventory']) == 8,
            'Expected exact nine-mod/eight-root receipt differs')
    equivalence = cost.audit_equivalence(ROOT / 'dependencies/runtime-artifact-equivalences.json', external)
    bootstrap = cost.bootstrap_source_evidence()
    immutable = cost.freeze_sources()
    immutable.update(scene['qualification']['source_files_sha256'])
    immutable.update(bootstrap['audited_excerpts_files_sha256'])
    immutable.update({v['path']:v['sha256'] for v in bootstrap['pinned_source_records'].values()})
    for name in ('scripts/prepare_photon_taau75_calibration.py', 'tests/test_photon_taau75_calibration.py'):
        immutable[str(ROOT/name)] = file_hash(ROOT/name)
    output = Path(operation['output'])
    marker = output / 'taau75-calibration-preparation.json'
    with unlocked_world(scene['world']):
        fingerprint = world_fingerprint(scene['world'])
        output.mkdir(parents=True, exist_ok=False)
        state = EVIDENCE | dict(schema=1, status='preparing', operation=operation,
            created_at_utc=datetime.now(timezone.utc).isoformat(), launch_authorized=False,
            source_qualification=scene['qualification'], source_world_sha256=fingerprint,
            immutable_source_files_sha256=immutable, development_runtime_inputs=development_fingerprints(),
            independent_artifact_receipt=dict(path=str(receipt), sha256=receipt_sha256, receipt=external),
            auxiliary_directory_identities=identities, api_base_equivalence=equivalence,
            bootstrap_source_evidence=bootstrap, launch_plan=[], owned_live_processes=[],
            postanalysis_plan=postanalysis_plan(), stop_procedure=quality.STOP,
            launch_status=operation['launch_status'])
        write_json(marker, state)
        try:
            camera = output / 'camera-request-motion.json'
            write_json(camera, quality.motion_request() | {'label': 'photon-taau75-coast-motion-r1'})
            preparation_paths = [camera]
            for kind, arms in GROUPS.items():
                base = output / kind; base.mkdir()
                control = base / 'control.json'; write_json(control, scene['control'])
                arm_models = {}
                for arm in arms:
                    mode = 'taau75' if arm == 'taau75' else 'native'
                    arm_models[arm] = dict(archive=scene['archive'].name, sha256=quality.ORIGINAL_SHA256,
                        requested_options=scene['requested_options'][mode],
                        expected_effective_options=scene['qualification']['effective_option_maps'][mode],
                        expected_effective_options_sha256=scene['qualification']['effective_option_hashes'][mode],
                        copied_world_sha256=fingerprint, main_mod_present=False, dh_present=False,
                        task='runClientLodOff')
                manifest = base / 'workload-manifest.json'
                write_json(manifest, dict(schema=1, scene='coastalwater', configuration_only=False,
                    preparation_status='complete', source_world=str(scene['world']), source_world_sha256=fingerprint,
                    original_sha256=quality.ORIGINAL_SHA256, experiment_arms=arm_models, requested_chunks=24,
                    expected_framebuffer=[2976,1672], control=scene['control'], dh_installed=False,
                    main_mod_present=False, **EVIDENCE))
                preparation_paths += [control, manifest]
                for arm in arms:
                    profile = base / arm
                    storage_copytree(scene['source'], profile, ignore=shutil.ignore_patterns(*quality.TRANSIENT))
                    (profile/'saves').mkdir(exist_ok=True)
                    storage_copytree(scene['world'], profile/'saves'/WORLD, ignore=shutil.ignore_patterns('session.lock'))
                    require(world_fingerprint(profile/'saves'/WORLD) == fingerprint, 'Copied world differs')
                    (profile/'shaderpacks').mkdir(exist_ok=True)
                    archive = profile/'shaderpacks'/scene['archive'].name
                    shutil.copy2(scene['archive'], archive)
                    archive.with_name(archive.name+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(arm_models[arm]['requested_options'].items())))
                    require(file_hash(archive) == quality.ORIGINAL_SHA256 and not any((profile/'mods').glob('*.jar')),
                            'Copied original/runtime scope differs')
                    request = EVIDENCE | dict(run_id=f'taau75-calibration-r1-{kind}-{arm}', case='photon-'+arm,
                        task='runClientLodOff', stack='minimal', include_dh=False, fixed_wetness=0,
                        measurement_seconds=20 if kind=='cost-bracket' else 5,
                        gpu_queries=kind=='cost-bracket', sample_interval=5,
                        expected_framebuffer=[2976,1672], expected_window=[1488,836])
                    if kind != 'motion': write_json(profile/'workload-request.json', request)
                    argv = cost.command(profile) if kind=='cost-bracket' else quality.command(profile,kind,camera)
                    state['launch_plan'].append(dict(kind=kind, arm=arm, profile=str(profile), command=argv,
                        request=str(camera) if kind=='motion' else request,
                        inputs=profile_inputs(profile), prepared_writable_inventory=cost.writable_inventory(profile),
                        profile_prelaunch_files_sha256={str(p):file_hash(p) for p in sorted(profile.rglob('*'))
                            if p.is_file() and not p.is_relative_to(profile/'saves')},
                        status='not-launched', owner='root',
                        launch_condition='Root resource reservation; query-free stills first' if kind!='cost-bracket' else
                            'HOLD until root accepts gross still quality and announces quiet client/GPU window'))
            require(world_fingerprint(scene['world']) == fingerprint, 'Source world changed during preparation')
            require(all(file_hash(Path(p)) == h for p,h in immutable.items()), 'Source/build changed during preparation')
            state.update(status='complete', immutable_preparation_files_sha256={str(p):file_hash(p) for p in preparation_paths})
            write_json(marker, state)
        except BaseException as error:
            state.update(status='failed', error=str(error)); write_json(marker,state); raise
    return state


def verify(state):
    # Existing strict prelaunch recipe/world/config/artifact guard applies unchanged.
    return cost.verify(state) | EVIDENCE


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--verify', type=Path)
    args = parser.parse_args()
    try:
        result = verify(json.loads(args.verify.read_text())) if args.verify else plan(args.output) if args.dry_run else prepare(args.output)
        print(json.dumps(result, indent=2, sort_keys=True))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, 'Cannot prepare original Photon .75 calibration: '+str(error)+'\n')


if __name__ == '__main__': main()
