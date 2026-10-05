#!/usr/bin/env python3
"""Prepare fresh Photon AO depth-reuse original/off/on trials; never build or launch."""
from __future__ import annotations

import argparse
from contextlib import ExitStack
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import tomllib
import zipfile

from build_photon_ao_reuse_probe import (BASE_SHA256, EXPERIMENT, MANIFEST, MEMBERS,
                                       METADATA, SWITCH, archive_members, patch_members, sha256)
from prepare_isolations import archive_contents, filter_effective_options
from prepare_reduced_effects_profiles import (ARMS, DEFAULT_SOURCES, POSES, SCENES,
                                             control_for_scene, fresh_config, require, write_json)
from run_proof_matrix import (archive_defaults, canonical_options_hash, normalize_requested_options,
                              read_option_properties, require_exclusive_runtime)
from setup_comparison import ROOT, WORLD, file_hash, unlocked_world, world_fingerprint

PROBE = ROOT / 'build/Photon-AODepthReuseProbe-disabled.zip'
MANIFEST_SHA256 = '0a017ece0d9c9080e727879fc1dbdad312efb6385139a63bc1ba23560aa9729a'
FLAGS = {SWITCH: 'true'}
TRANSIENT = ('saves', 'shaderpacks', 'logs', 'screenshots', 'camera-quality*', 'crash-reports',
             'patched_shaders', 'downloads', '*measurement*.json*', '*benchmark*.json*',
             '*sampler*.json*', 'workload-request*.json*', '*.jfr')


def plan(output, scenes=SCENES, sources=None, probe=PROBE, root=ROOT):
    """Pure paths only. Generated profiles must live under ignored build/ or run-workloads/."""
    root = Path(root).resolve()
    sources = DEFAULT_SOURCES | (sources or {})
    raw_output, raw_probe = Path(output), Path(probe)
    require(not raw_output.is_symlink() and not raw_probe.is_symlink(), 'No symlink output/probe')
    output, probe = raw_output.resolve(), raw_probe.resolve()
    require(any(output.is_relative_to(root / name) and output != root / name
                for name in ('build', 'run-workloads')), 'Output must be under ignored build/ or run-workloads/')
    require(not output.exists(), 'Preserving existing output; use a fresh directory')
    require(scenes and len(set(scenes)) == len(scenes) and set(scenes) <= set(SCENES), 'Unknown/empty/duplicate scenes')
    operations = []
    for scene in scenes:
        source = Path(sources[scene])
        require(not source.is_symlink(), 'No symlink source profile')
        source = source.resolve()
        require(source != output and source not in output.parents and output not in source.parents
                and source.parent != output and source.parent not in output.parents,
                'Output must be separate from source collection')
        operations.append(dict(scene=scene, source_profile=str(source),
                               target_profiles={arm: str(output / scene / arm) for arm in ARMS}))
    require(output != probe and output not in probe.parents, 'Output may not contain source probe')
    return dict(schema=1, output=str(output), probe=str(probe), original_sha256=BASE_SHA256,
                manifest=str(root / MANIFEST), scene_operations=operations, arms=list(ARMS),
                requested_chunks=24, framebuffer=[2976, 1672], dh_present=False,
                validation='Pure plan only; archive/save qualification and copying pending')


def qualify_archives(original_path, probe_path, runtime, root=ROOT):
    """Derive the private pin from reviewed manifest and complete actual member comparison."""
    photon = runtime['artifacts']['photon']
    original = archive_contents(original_path, BASE_SHA256, photon)
    require(file_hash(original_path, 'sha512') == photon['hashes']['sha512'], 'Original runtime-lock checksum mismatch')
    manifest_path = Path(root) / MANIFEST
    manifest_bytes = manifest_path.read_bytes()
    require(sha256(manifest_bytes) == MANIFEST_SHA256, 'Reviewed AO source manifest fingerprint changed')
    manifest = json.loads(manifest_bytes)
    originals, private = archive_members(original_path), archive_members(probe_path)
    require(METADATA not in originals and set(private) == set(originals) | {METADATA}, 'Unexpected private archive member inventory')
    metadata = json.loads(private[METADATA])
    expected = patch_members(originals, manifest, 'disabled')
    changed = sorted(name for name in originals if private[name] != originals[name])
    require(changed == sorted(MEMBERS), 'Private changed member scope differs')
    require(all(private[name] == content for name, content in expected.items()), 'Private source differs from reviewed patch')
    expected_metadata = dict(experiment=EXPERIMENT, arm='disabled', base_version='v1.3b',
        base_sha256=BASE_SHA256, manifest_sha256=sha256(manifest_bytes), switch=SWITCH, switch_enabled=False,
        modified_upstream_files=changed, member_sha256={name: sha256(expected[name]) for name in MEMBERS},
        license_sha256=sha256(originals['LICENSE']), unchanged_upstream_member_count=len(originals)-len(changed),
        consumed_core_fields=[], activation='compile option on AND bridge version 2 AND global enabled exactly 1',
        runtime_compilation='unverified', gpu_benefit='unmeasured')
    require(all(metadata.get(name) == value for name, value in expected_metadata.items()), 'Private metadata differs from reviewed contract')
    with zipfile.ZipFile(probe_path) as archive:
        defaults = archive_defaults(archive)
    require(defaults.get(SWITCH) == 'false', 'Private gate must default off')
    original['path'] = str(original_path)
    probe = dict(path=str(probe_path), sha256=file_hash(probe_path), metadata=metadata)
    qualification = expected_metadata | dict(qualified=True, archive_sha256=probe['sha256'],
        manifest_path=str(manifest_path), upstream_member_count=len(originals),
        option_override_consumed_core_fields=['shaderlodBridgeVersion', 'shaderlodEnabled'],
        validation='Complete upstream member/source/license equality verified; driver/runtime pending')
    return original, probe, qualification


def native_options(profile, original, probe):
    iris = read_option_properties((profile / 'config/iris.properties').read_text())
    sidecar = profile / 'shaderpacks' / (iris['shaderPack'] + '.txt')
    requested = read_option_properties(sidecar.read_text()) if sidecar.exists() else {}
    report_path = profile / 'workload-measurement.json'
    require(report_path.is_file(), 'Inspected original loaded-option evidence missing')
    report = json.loads(report_path.read_text())
    require(report.get('status') == 'complete' and report.get('actual_shader_sha256') == BASE_SHA256,
            'Original source report pin/status mismatch')
    effective = report.get('effective_shader_options')
    require(isinstance(effective, dict) and effective
            and report.get('effective_shader_options_sha256') == canonical_options_hash(effective), 'Original effective option fingerprint mismatch')
    for boundary in ('input_state_start', 'input_state_end'):
        state = report.get(boundary, {})
        require([state.get('framebuffer_width'), state.get('framebuffer_height')] == [2976, 1672]
                and state.get('requested_chunks') == state.get('effective_chunks') == 24
                and state.get('shaders_in_use') is True and state.get('fallback') is False,
                'Inspected original native/radius/shader evidence differs: ' + boundary)
    # Only GTAO is patched. Keep its inspected budgets; SSAO/disabled is not eligible.
    require(effective.get('SHADER_AO') == 'SHADER_AO_GTAO' and effective.get('GTAO_HORIZON_STEPS') == '3'
            and effective.get('GTAO_SLICES') == '2' and effective.get('GTAO_RADIUS') == '2.0', 'Inspected GTAO selection/budgets differ')
    with zipfile.ZipFile(probe['path']) as archive:
        probe_defaults = archive_defaults(archive)
        probe_settings = archive.read('shaders/settings.glsl').decode()
    candidate = dict(settings=probe_settings, declarations=original['declarations'])
    native, evidence = filter_effective_options(effective, requested, original, candidate)
    require(not any(name.startswith('SHADERLOD_') for name in native), 'Original private option contamination')
    with zipfile.ZipFile(original['path']) as archive:
        original_defaults = archive_defaults(archive)
    require(all(probe_defaults.get(name) == value for name, value in original_defaults.items()), 'Private archive changes upstream option defaults')
    normalize_requested_options(original_defaults, native)
    normalize_requested_options(probe_defaults, native | FLAGS)
    require(normalize_requested_options(probe_defaults, native | FLAGS).get('SHADER_AO') == 'SHADER_AO_GTAO', 'GTAO became disabled')
    return native, native | FLAGS, evidence


def inspect_scene(item, probe_path, runtime, root=ROOT):
    scene, profile = item['scene'], Path(item['source_profile'])
    require(not any(path.is_symlink() for path in profile.rglob('*')), 'Symlinks in source tree unsupported')
    world = profile / 'saves' / WORLD
    require((world / 'level.dat').is_file(), 'Stopped source save missing')
    manifest_path, control_path = profile.parent / 'workload-manifest.json', profile.parent / 'control.json'
    manifest = json.loads(manifest_path.read_text())
    require(manifest.get('configuration_only') is False and manifest.get('source_world_sha256'), 'Source save provenance missing')
    control, removed = control_for_scene(json.loads(control_path.read_text()), scene)
    iris_path = profile / 'config/iris.properties'
    iris = read_option_properties(iris_path.read_text()); selected = iris.get('shaderPack', '')
    require(selected and Path(selected).name == selected and iris.get('enableShaders') == 'true', 'Invalid source Iris selection')
    original_path = profile / 'shaderpacks' / selected
    original, probe, qualification = qualify_archives(original_path, probe_path, runtime, root)
    native, patched, evidence = native_options(profile, original, probe)
    options_path = profile / 'options.txt'
    minecraft = dict(line.split(':', 1) for line in options_path.read_text().splitlines() if ':' in line)
    require(minecraft.get('renderDistance') == '24', 'Requested source radius must remain 24')
    jars = sorted((profile / 'mods').glob('*.jar')); dh = runtime['artifacts']['distanthorizons']
    require(all(jar.name == dh['filename'] and file_hash(jar, 'sha512') == dh['hashes']['sha512'] for jar in jars), 'Unmanaged source profile jars')
    require(len(jars) == (1 if scene == 'forest' else 0), 'Source optional jar inventory differs')
    tomllib.loads((profile / 'config/shaderlod-client.toml').read_text())
    files = [manifest_path, control_path, iris_path, options_path, original_path, probe_path,
             Path(root) / MANIFEST, profile / 'config/shaderlod-client.toml', profile / 'workload-measurement.json', *jars]
    files += [path for path in profile.rglob('*') if path.is_file() and
              (path.is_relative_to(profile / 'config') or path.is_relative_to(profile / 'resourcepacks')) and path not in files]
    sidecar = original_path.with_name(selected + '.txt')
    if sidecar.exists(): files.append(sidecar)
    return dict(scene=scene, profile=profile, world=world, manifest=manifest, control=control,
        removed_control_overrides=removed, original=original, probe=probe, qualification=qualification,
        native_options=native, patched_options=patched, option_evidence=evidence,
        source_files_sha256={str(path): file_hash(path) for path in files},
        provenance_files={'control-original.json': control_path, 'workload-manifest-original.json': manifest_path,
                          'shaderlod-client-original.toml': profile / 'config/shaderlod-client.toml',
                          'iris-original.properties': iris_path, 'options-original.txt': options_path,
                          'workload-measurement-original.json': profile / 'workload-measurement.json'})


def prepare(output, scenes=SCENES, sources=None, probe=PROBE, runtime_lock=None, root=ROOT):
    operation = plan(output, scenes, sources, probe, root)
    require_exclusive_runtime()
    runtime_lock = Path(runtime_lock or Path(root) / 'dev/runtime.lock.json')
    runtime = json.loads(runtime_lock.read_text()); output = Path(operation['output'])
    with ExitStack() as locks:
        for item in operation['scene_operations']:
            locks.enter_context(unlocked_world(Path(item['source_profile']) / 'saves' / WORLD))
        selected = [inspect_scene(item, Path(operation['probe']), runtime, root) for item in operation['scene_operations']]
        for scene in selected: scene['world_sha256'] = world_fingerprint(scene['world'])
        output.parent.mkdir(parents=True, exist_ok=True); output.mkdir(exist_ok=False)
        state = dict(schema=1, status='preparing', operation=operation, runtime_lock=runtime,
                     runtime_lock_sha256=file_hash(runtime_lock), created_at_utc=datetime.now(timezone.utc).isoformat(), scenes={},
                     validation='Preparation only; compilation/rendering/performance/quality pending')
        marker = output / 'photon-ao-reuse-preparation.json'; write_json(marker, state)
        try:
            for scene in selected:
                base = output / scene['scene']; base.mkdir()
                provenance = base / 'source-provenance'; provenance.mkdir()
                for name, path in scene['provenance_files'].items(): shutil.copy2(path, provenance / name)
                copies, arms = {}, {}
                for arm in ARMS:
                    profile = base / arm
                    shutil.copytree(scene['profile'], profile, ignore=shutil.ignore_patterns(*TRANSIENT))
                    world = profile / 'saves' / WORLD; world.parent.mkdir()
                    shutil.copytree(scene['world'], world, ignore=shutil.ignore_patterns('session.lock'))
                    copies[arm] = world_fingerprint(world)
                    require(copies[arm] == scene['world_sha256'], 'Copied canonical save differs: ' + arm)
                    dh_path = profile / 'mods' / runtime['artifacts']['distanthorizons']['filename']
                    removed_dh = dh_path.exists()
                    if removed_dh:
                        require(file_hash(dh_path, 'sha512') == runtime['artifacts']['distanthorizons']['hashes']['sha512'], 'Copied DH pin differs')
                        dh_path.unlink()  # Only the fresh, already-qualified copy.
                    require(not any((profile / 'mods').glob('*.jar')), 'Fresh output has unmanaged profile mods')
                    archive = scene['original'] if arm == 'high' else scene['probe']; archive_path = Path(archive['path'])
                    (profile / 'shaderpacks').mkdir(); shutil.copy2(archive_path, profile / 'shaderpacks' / archive_path.name)
                    options = scene['native_options'] if arm == 'high' else scene['patched_options']
                    (profile / 'shaderpacks' / (archive_path.name + '.txt')).write_text(''.join(f'{name}={value}\n' for name, value in sorted(options.items())))
                    iris = read_option_properties((profile / 'config/iris.properties').read_text()); iris['shaderPack'] = archive_path.name
                    (profile / 'config/iris.properties').write_text(''.join(f'{name}={value}\n' for name, value in sorted(iris.items())))
                    (profile / 'config/shaderlod-client.toml').write_text(fresh_config(arm))
                    require(file_hash(profile / 'shaderpacks' / archive_path.name) == archive['sha256'], 'Copied archive changed')
                    inputs = [path for path in profile.rglob('*') if path.is_file() and not path.is_relative_to(world)]
                    arms[arm] = dict(archive=archive_path.name, sha256=archive['sha256'], requested_options=options,
                        global_enabled=arm == 'adaptive', main_mod_present=arm != 'high', task='runClientLodOff' if arm == 'high' else 'runClientLodOn',
                        dh_present=False, removed_fresh_copied_dh_jar=removed_dh,
                        input_files_sha256={str(path.relative_to(profile)): file_hash(path) for path in inputs},
                        config_sha256=file_hash(profile / 'config/shaderlod-client.toml'))
                require(world_fingerprint(scene['world']) == scene['world_sha256'], 'Source save changed during copying')
                require({path: file_hash(Path(path)) for path in scene['source_files_sha256']} == scene['source_files_sha256'], 'Source inputs changed during preparation')
                write_json(base / 'control.json', scene['control'])
                manifest = dict(schema=1, scene=scene['scene'], configuration_only=False, preparation_status='complete', proof_profiles=list(ARMS),
                    source_world=str(scene['world']), source_world_sha256=scene['world_sha256'], copied_world_sha256=copies,
                    source_provenance=scene['manifest'], source_files_sha256=scene['source_files_sha256'],
                    source_option_evidence=scene['option_evidence'], removed_control_overrides=scene['removed_control_overrides'], control=scene['control'],
                    experiment_family=EXPERIMENT, experiment_arms=arms, quality_options={arm: arms[arm]['requested_options'] for arm in ARMS},
                    original_sha256=BASE_SHA256, adaptive_pack_sha256=scene['probe']['sha256'], ao_reuse_source_qualification=scene['qualification'],
                    requested_chunks=24, expected_framebuffer=[2976, 1672], runtime_lock=runtime, dh_installed=False, geometry_enabled=False, shader_only=True,
                    validation='Qualified patch/member/options, equal canonical copies and physical DH removal only; actual native rendering and performance/quality pending')
                write_json(base / 'workload-manifest.json', manifest)
                state['scenes'][scene['scene']] = dict(source_world_sha256=scene['world_sha256'], copied_world_sha256=copies,
                                                     probe_sha256=scene['probe']['sha256'], manifest_sha256=scene['qualification']['manifest_sha256'])
                write_json(marker, state)
            state['status'] = 'complete'; write_json(marker, state)
        except BaseException as error:
            state.update(status='failed', error=str(error)); write_json(marker, state); raise
    return state


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--scene', choices=('both', *SCENES), default='both')
    parser.add_argument('--forest-source', type=Path, default=DEFAULT_SOURCES['forest'])
    parser.add_argument('--coastal-source', type=Path, default=DEFAULT_SOURCES['coastalwater'])
    parser.add_argument('--probe', type=Path, default=PROBE)
    parser.add_argument('--dry-run', action='store_true', help='Pure paths only; no archive/save reads or copies')
    args = parser.parse_args(); scenes = SCENES if args.scene == 'both' else (args.scene,)
    sources = dict(forest=args.forest_source, coastalwater=args.coastal_source)
    try:
        result = plan(args.output, scenes, sources, args.probe) if args.dry_run else prepare(args.output, scenes, sources, args.probe)
        print(json.dumps(result, indent=2, sort_keys=True))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, 'Cannot prepare AO depth-reuse profiles: ' + str(error) + '\n')


if __name__ == '__main__': main()
