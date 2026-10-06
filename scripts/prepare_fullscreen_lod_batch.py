#!/usr/bin/env python3
"""Prepare six explicit Photon controls for a fresh fullscreen smoke and batch.

No assembly, compiler, client or measurement. All private profiles use one
frozen default-off combined archive. Actual fullscreen dimensions remain unknown
until a separate current-class smoke records them.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import tomllib
import zipfile

import build_photon_combined_lod as combined
import prepare_parallel_lod_profiles as qa
import prepare_photon_ao_reuse_profiles as copies
from run_proof_matrix import archive_defaults, canonical_options_hash, declared_defaults, normalize_requested_options
from setup_comparison import ROOT, WORLD, file_hash, world_fingerprint


MANIFEST_SHA256 = 'dcbb31d5878387bd00bdb048703be9b6c22539b30844ee8054dcc455a533cf19'
ARCHIVE_SHA256 = '87e12ac10401cf59c36d76eaabd1b7fdb9b8dc66365dfebdcfa2c7e7a329e41a'
ARCHIVE_NAME = 'Photon-CombinedLOD-disabled.zip'
ARMS = ('high', 'patched-off', 'reflection-only', 'volume-only', 'procedural-only', 'combined')
ARM_TRACKS = {'high': (), 'patched-off': (), 'reflection-only': ('reflection',),
              'volume-only': ('volumetric',), 'procedural-only': ('procedural',),
              'combined': combined.TRACKS}
EXPERIMENT = 'photon-fullscreen-explicit-six-arm-lod-batch'
MARKER = 'fullscreen-lod-preparation.json'
QUALIFICATION_KEY = 'fullscreen_lod_source_qualification'
KNOWN_RUNTIME_BLOCKED = ('reflection-only', 'procedural-only', 'combined')
RUNTIME_FAILURE = 'Retained procedural r1 missing RPAREN; corrected reflection r2/procedural r3/combined r2 need new driver QA; parser/source checks do not promote timing'
require = copies.require


def feature_flags(arm):
    require(arm in ARMS, 'Unknown explicit fullscreen arm')
    if arm == 'high': return {}
    return {switch: str(track in ARM_TRACKS[arm]).lower()
            for track, switch in zip(combined.TRACKS, combined.SWITCHES)}


def metadata_contract(original, expected, manifest, manifest_bytes):
    changed = sorted(name for name in original if original[name] != expected[name])
    added = sorted(set(expected) - set(original))
    return dict(experiment=combined.EXPERIMENT, base_version='v1.3b', base_sha256=combined.BASE_SHA256,
        manifest_sha256=combined.sha256(manifest_bytes), enabled_tracks=[], original_arm=False,
        switches=dict(zip(combined.TRACKS, combined.SWITCHES)), modified_upstream_files=changed,
        added_helpers=added, member_sha256={name: combined.sha256(expected[name]) for name in changed + added},
        license_sha256=combined.sha256(expected['LICENSE']), unchanged_upstream_member_count=len(original)-len(changed),
        track_manifest_sha256={entry['name']: entry['manifest_sha256'] for entry in manifest['tracks']},
        core_version=2, effect_versions=[1, 2],
        fallback='Each private option off selects literal upstream program; absent/invalid bridge uses standalone original fallback',
        quality_status='unverified combined quality and interaction', runtime_compilation='unverified',
        gpu_benefit='unmeasured; individual gains cannot be summed',
        attribution='Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained')


def qualify_combined(original_path, private_path, runtime, root=ROOT):
    root = Path(root)
    original_path, private_path = Path(original_path), Path(private_path)
    require(file_hash(original_path) == combined.BASE_SHA256, 'Original Photon archive pin differs')
    require(file_hash(original_path, 'sha512') == runtime['artifacts']['photon']['hashes']['sha512'], 'Runtime Photon lock differs')
    manifest_path = root / combined.MANIFEST
    manifest_bytes = manifest_path.read_bytes()
    require(combined.sha256(manifest_bytes) == MANIFEST_SHA256, 'Frozen combined manifest changed')
    manifest = json.loads(manifest_bytes)
    original = combined.reflection.archive_members(original_path)
    expected = combined.patch_members(original, manifest, (), root)
    private = combined.reflection.archive_members(private_path)
    require(set(private) == set(expected) | {combined.METADATA}, 'Unexpected combined member inventory')
    require(all(private[name] == value for name, value in expected.items()), 'Combined source/helper bytes differ')
    require(private['LICENSE'] == original['LICENSE'] and file_hash(private_path) == ARCHIVE_SHA256, 'Combined archive/license pin differs')
    metadata = metadata_contract(original, expected, manifest, manifest_bytes)
    expected_metadata = (json.dumps(metadata, sort_keys=True, indent=2) + '\n').encode()
    require(private[combined.METADATA] == expected_metadata, 'Complete combined metadata differs')
    with zipfile.ZipFile(private_path) as archive:
        defaults = archive_defaults(archive)
    require(all(defaults.get(name) == 'false' for name in combined.SWITCHES), 'Combined private gates must default off')
    return dict(qualified=True, experiment=EXPERIMENT, manifest_path=str(manifest_path),
        manifest_sha256=MANIFEST_SHA256, archive_sha256=ARCHIVE_SHA256,
        metadata_sha256=combined.sha256(expected_metadata), metadata=metadata,
        member_sha256={name: combined.sha256(value) for name, value in sorted(expected.items())},
        license_sha256=qa.LICENSE_SHA256, runtime_option_subsets=[list(combined.controls(names)) for names in
            ((), ('reflection',), ('procedural',), ('volumetric',), ('reflection','procedural'),
             ('reflection','volumetric'), ('procedural','volumetric'), combined.TRACKS)],
        validation='Exact frozen archive/whole metadata/member/helper/license; no driver/quality/performance acceptance')


def inspect_scene(item, private_path, runtime, root=ROOT):
    """Canonical inspection reused from the pinned QA family; never accept its private qualification as combined proof."""
    root = Path(root)
    private_path = Path(private_path)
    # Reuse the existing complete canonical scene/options/lock/jar inspection.
    # Its separate reflection qualification stays explicit in provenance.
    reflection_path = root / 'build' / qa.FAMILIES['reflection'].archive_name
    record = qa.inspect_scene(item, reflection_path, runtime, root, 'reflection')
    qualification = qualify_combined(record['original']['path'], private_path, runtime, root)
    with zipfile.ZipFile(record['original']['path']) as archive:
        original_defaults = archive_defaults(archive)
        original_settings = declared_defaults(archive.read(combined.SETTINGS).decode())
    with zipfile.ZipFile(private_path) as archive:
        defaults = archive_defaults(archive)
        private_settings = declared_defaults(archive.read(combined.SETTINGS).decode())
    require(all(defaults.get(key) == value for key, value in original_defaults.items()), 'Combined changes upstream defaults')
    # archive_defaults also inventories non-option helper include guards. Public
    # declarations belong to this exact qualified settings member; actual Iris
    # active-option union remains separately checked at runtime.
    require(set(private_settings) - set(original_settings) == set(combined.SWITCHES), 'Unexpected combined settings option declaration')
    patched = record['native_options'] | feature_flags('patched-off')
    normalize_requested_options(defaults, patched)
    record['canonical_reflection_qualification'] = record['qualification']
    record['qualification'] = qualification
    record['probe'] = dict(path=str(private_path), sha256=ARCHIVE_SHA256, metadata=qualification['metadata'])
    record['patched_options'] = patched
    record['source_files_sha256'][str(private_path)] = file_hash(private_path)
    record['source_files_sha256'][str(root / combined.MANIFEST)] = MANIFEST_SHA256
    for entry in json.loads((root / combined.MANIFEST).read_text())['tracks']:
        for key in ('manifest_path', 'builder_path'):
            path = root / entry[key]
            record['source_files_sha256'][str(path)] = file_hash(path)
        track_manifest = json.loads((root/entry['manifest_path']).read_text())
        if entry['name'] == 'reflection':
            helpers = [Path(value['source_path']) for value in track_manifest['added_members']]
        elif entry['name'] == 'procedural':
            helpers = [combined.procedural.HELPER_ROOT/value['path'] for value in track_manifest['helpers']]
        else:
            helpers = [Path(value['source_path']) for value in track_manifest['additions']]
        for helper in helpers:
            record['source_files_sha256'][str(root/helper)] = file_hash(root/helper)
    return record


def compiled_inputs(root=ROOT):
    root = Path(root)
    results = {}
    for name in ('build/classes/java/devClient', 'build/resources/devClient', 'build/classes/java/main', 'build/resources/main'):
        directory = root / name
        require(directory.is_dir(), 'Current compiled runtime tree missing: ' + name)
        files = {str(path.relative_to(directory)): file_hash(path) for path in sorted(directory.rglob('*')) if path.is_file()}
        results[name] = dict(files=files, sha256=canonical_options_hash(files))
    for name in ('com/pp/shaderlod/mixin/EffectUniformsMixin.class', 'com/pp/shaderlod/EffectUniformFrame.class'):
        require(name in results['build/classes/java/main']['files'], 'Current effects producer class missing: ' + name)
    require('com/pp/shaderlod/dev/timing/CloudSamplerStateProbe.class' in results['build/classes/java/devClient']['files'],
            'Current observer class missing')
    return results


def plan(output, scenes=copies.SCENES, sources=None, probe=None, root=ROOT):
    operation = copies.plan(output, scenes, sources, probe or Path(root)/'build'/ARCHIVE_NAME, root)
    operation.update(arms=list(ARMS), manifest=str(Path(root)/combined.MANIFEST), manifest_sha256=MANIFEST_SHA256,
        probe_sha256=ARCHIVE_SHA256, framebuffer=None, source_evidence_framebuffer=[2976,1672],
        fullscreen_requested=True, observed_fullscreen=None, producer_effects_version=2,
        experiment=EXPERIMENT, class_policy='Same current main/dev/resources for original and all private controls',
        source_dependency='Existing frozen reflection qualification supplies canonical scene inspection; combined independently qualified',
        runtime_blocked_arms=list(KNOWN_RUNTIME_BLOCKED), runtime_failure=RUNTIME_FAILURE,
        validation='Pure plan; dimensions discovered only by later fullscreen smoke; no copy/driver/timing evidence')
    for item in operation['scene_operations']:
        item['target_profiles'] = {arm: str(Path(operation['output'])/item['scene']/arm) for arm in ARMS}
    return operation


def prepare(output, scenes=copies.SCENES, sources=None, probe=None, root=ROOT, runtime_lock=None):
    root = Path(root)
    operation = plan(output, scenes, sources, probe, root)
    frozen_classes = compiled_inputs(root)
    state = copies.prepare(output, scenes, sources, Path(operation['probe']), runtime_lock, root,
        source_inspector=inspect_scene, experiment=EXPERIMENT, marker_name=MARKER, source_qualification_key=QUALIFICATION_KEY)
    marker = Path(operation['output']) / MARKER
    state.update(status='preparing-fullscreen-controls', operation=operation, fullscreen_operation=operation,
                 development_runtime_inputs=frozen_classes)
    copies.write_json(marker, state)
    try:
        copies.require_exclusive_runtime()
        for scene in scenes:
            copies.require_exclusive_runtime()
            base = Path(operation['output']) / scene
            path = base / 'workload-manifest.json'
            manifest = json.loads(path.read_text())
            manifest.update(configuration_only=True, preparation_status='preparing-fullscreen-controls')
            copies.write_json(path, manifest)
            (base/'adaptive').rename(base/'combined')
            for arm in ('reflection-only', 'volume-only', 'procedural-only'):
                shutil.copytree(base/'combined', base/arm, ignore=shutil.ignore_patterns('session.lock'))
            native = manifest['experiment_arms']['high']['requested_options']
            source_digest = manifest['source_world_sha256']
            arms, digests = {}, {}
            for arm in ARMS:
                profile = base / arm
                private = arm != 'high'
                archive_name = ARCHIVE_NAME if private else manifest['experiment_arms']['high']['archive']
                expected_pin = manifest['experiment_arms']['patched-off' if private else 'high']['sha256']
                require(file_hash(profile/'shaderpacks'/archive_name) == expected_pin, 'Copied fullscreen archive changed')
                requested = native | feature_flags(arm)
                (profile/'shaderpacks'/(archive_name+'.txt')).write_text(''.join(f'{key}={value}\n' for key,value in sorted(requested.items())))
                enabled = arm not in ('high','patched-off')
                (profile/'config/shaderlod-client.toml').write_text(copies.fresh_config('adaptive' if enabled else arm))
                (profile/'config/shaderlod-effects-client.toml').write_text(qa.effects_config('adaptive'))
                world = profile/'saves'/WORLD
                digests[arm] = world_fingerprint(world)
                require(digests[arm] == source_digest, 'Fullscreen arm canonical copy differs: ' + arm)
                require(not any((profile/'mods').glob('*.jar')), 'Fullscreen batch must physically omit DH/profile jars')
                arms[arm] = dict(archive=archive_name, sha256=expected_pin, requested_options=requested,
                    global_enabled=enabled, main_mod_present=private, task='runClientLodOn' if private else 'runClientLodOff',
                    dh_present=False, private_tracks=list(ARM_TRACKS[arm]),
                    runtime_blocked=arm in KNOWN_RUNTIME_BLOCKED,
                    runtime_status=RUNTIME_FAILURE if arm in KNOWN_RUNTIME_BLOCKED else 'Requires current combined-family fullscreen driver/quality smoke; standalone QA does not promote timing',
                    config_sha256=file_hash(profile/'config/shaderlod-client.toml'),
                    effects_config_sha256=file_hash(profile/'config/shaderlod-effects-client.toml'),
                    requested_effects_config=tomllib.loads(qa.effects_config('adaptive')),
                    input_files_sha256={str(p.relative_to(profile)):file_hash(p) for p in profile.rglob('*')
                                       if p.is_file() and not p.is_relative_to(world)})
            manifest.update(configuration_only=False, preparation_status='complete', proof_profiles=list(ARMS),
                experiment_arms=arms, copied_world_sha256=digests, quality_options={arm:arms[arm]['requested_options'] for arm in ARMS},
                expected_framebuffer=None, source_evidence_framebuffer=[2976,1672], fullscreen_required=True,
                fullscreen_operation=operation, development_runtime_inputs=frozen_classes,
                validation='Prepared equal source/config controls only; require fresh actual fullscreen smoke and driver/quality acceptance')
            copies.write_json(path, manifest)
            state['scenes'][scene]['copied_world_sha256'] = digests
        require(compiled_inputs(root) == frozen_classes, 'Compiled runtime changed during fullscreen preparation')
        state['status'] = 'complete'
        copies.write_json(marker, state)
    except BaseException as error:
        state.update(status='failed', error=str(error))
        copies.write_json(marker, state)
        raise
    return state


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--scene', choices=('forest','coastalwater','both'), default='both')
    parser.add_argument('--probe', type=Path)
    parser.add_argument('--forest-source', type=Path, default=copies.DEFAULT_SOURCES['forest'])
    parser.add_argument('--coastal-source', type=Path, default=copies.DEFAULT_SOURCES['coastalwater'])
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    scenes = copies.SCENES if args.scene == 'both' else (args.scene,)
    try:
        result = (plan if args.dry_run else prepare)(args.output, scenes,
            sources={'forest':args.forest_source,'coastalwater':args.coastal_source}, probe=args.probe)
        print(json.dumps(result, indent=2, sort_keys=True))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, 'Cannot prepare explicit fullscreen LOD batch: ' + str(error) + '\n')


if __name__ == '__main__': main()
