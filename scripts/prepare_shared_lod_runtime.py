#!/usr/bin/env python3
"""Prepare the pinned general LOD reference runtime controls; never build or launch."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import tomllib
from types import SimpleNamespace
import zipfile

import build_photon_volumetric_lod as consumer
import prepare_photon_ao_reuse_profiles as copies
import prepare_photon_density_detail_profiles as canonical
from prepare_parallel_lod_profiles import effects_config, native_options
from prepare_isolations import archive_contents
from run_proof_matrix import archive_defaults
from setup_comparison import ROOT, WORLD, file_hash, world_fingerprint

ARCHIVE_SHA256 = '5de7561dd24cdca80860a1d5f2c60400e74163a489cc163e916f3fd47feb27de'
MANIFEST_SHA256 = '40efd7e702e41388033255d48a007c543e1055ccb4e2513c1908c2d82b8f2677'
FREEZE = Path('dev/logs/shared-volume-consumer-source-freeze.json')
FREEZE_SHA256 = 'bce667558457a932361bb379774bc505226d3205d8392e89980b766db7d866d3'
WORLD_SHA256 = '89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46'
PROBE = ROOT / 'build/Photon-VolumetricLOD-disabled.zip'
SOURCE = copies.DEFAULT_SOURCES['coastalwater']
EXPERIMENT = 'shared-lod-volume-integration-reference'
require = copies.require


def qualify_archives(original_path, probe_path, runtime, root=ROOT):
    root = Path(root)
    original = archive_contents(original_path, consumer.BASE_SHA256, runtime['artifacts']['photon'])
    require(file_hash(original_path, 'sha512') == runtime['artifacts']['photon']['hashes']['sha512'], 'Original runtime checksum drift')
    manifest_path = root / consumer.MANIFEST
    require(file_hash(manifest_path) == MANIFEST_SHA256, 'Shared consumer manifest drift')
    require(file_hash(root / FREEZE) == FREEZE_SHA256, 'Reviewed source freeze drift')
    freeze = json.loads((root / FREEZE).read_text())['archives']['disabled']
    require(freeze['sha256'] == ARCHIVE_SHA256, 'Reviewed archive identity drift')
    manifest = json.loads(manifest_path.read_text())
    originals, private = consumer.archive_members(original_path), consumer.archive_members(probe_path)
    expected = consumer.patch_members(originals, manifest, 'disabled', root)
    require(set(private) == set(expected) | {consumer.METADATA}, 'Shared member inventory drift')
    require(all(private[name] == data for name, data in expected.items()), 'Shared member/helper bytes drift')
    metadata = json.loads(private[consumer.METADATA])
    require(metadata == freeze['metadata'], 'Shared complete metadata drift')
    require(all(consumer.sha256(private[name]) == pin for name, pin in metadata['reference_member_sha256'].items()), 'Registered reference member drift')
    require(file_hash(probe_path) == ARCHIVE_SHA256, 'Registered complete archive drift')
    with zipfile.ZipFile(probe_path) as archive:
        require(archive_defaults(archive).get(consumer.SWITCH) == 'false', 'Private integration must default off')
    original['path'] = str(original_path)
    private_record = dict(path=str(probe_path), sha256=ARCHIVE_SHA256, metadata=metadata)
    qualification = dict(qualified=True, manifest_path=str(manifest_path), manifest_sha256=MANIFEST_SHA256,
        archive_sha256=ARCHIVE_SHA256, freeze_sha256=FREEZE_SHA256, metadata=metadata,
        operation='shaderlod:volume_integration', capability_schema=1,
        validation='Exact registered source/member/helper/license/options eligibility; runtime acceptance pending')
    return original, private_record, qualification


def inspect_scene(item, probe_path, runtime, root=ROOT):
    require(item['scene'] == 'coastalwater', 'Shared reference audit is coastalwater only')
    profile = Path(item['source_profile']); world = profile / 'saves' / WORLD
    require(not any(p.is_symlink() for p in profile.rglob('*')), 'Symlink source unsupported')
    require(world_fingerprint(world) == WORLD_SHA256, 'Canonical coastal world drift')
    report_path = profile / 'workload-measurement.json'
    require(file_hash(report_path) == canonical.SOURCE_REPORT_SHA256, 'Canonical coastal report drift')
    report = json.loads(report_path.read_text())
    require(report['effective_shader_options_sha256'] == canonical.SOURCE_OPTIONS_SHA256, 'Canonical options drift')
    require(len(report['effective_shader_options']) == 366, 'Canonical upstream option inventory drift')
    manifest_path, control_path = profile.parent / 'workload-manifest.json', profile.parent / 'control.json'
    manifest = json.loads(manifest_path.read_text())
    require(manifest.get('configuration_only') is False and manifest.get('source_world_sha256'), 'Canonical provenance missing')
    control, removed = copies.control_for_scene(json.loads(control_path.read_text()), 'coastalwater')
    iris_path = profile / 'config/iris.properties'
    iris = canonical.read_option_properties(iris_path.read_text()); selected = iris.get('shaderPack', '')
    require(selected and Path(selected).name == selected and iris.get('enableShaders') == 'true', 'Canonical Iris selection drift')
    original_path = profile / 'shaderpacks' / selected
    original, private, qualification = qualify_archives(original_path, probe_path, runtime, root)
    native, patched, evidence = native_options(profile, original, private, SimpleNamespace(builder=consumer))
    effective = report['effective_shader_options']
    omitted = evidence['omitted_non_settings_defaults']
    require(native | omitted == effective and len(effective) == 366, 'All upstream effective options must be held')
    require(all(patched.get(k) in values for k, values in consumer.REQUIRED_OPTIONS.items()), 'Required adapter option incompatibility')
    with zipfile.ZipFile(probe_path) as archive:
        canonical.normalize_requested_options(archive_defaults(archive), patched)
    options_path = profile / 'options.txt'
    game = dict(line.split(':', 1) for line in options_path.read_text().splitlines() if ':' in line)
    require(game.get('renderDistance') == '24' and not list((profile / 'mods').glob('*.jar')), 'Canonical native24/noDH drift')
    tomllib.loads((profile / 'config/shaderlod-client.toml').read_text())
    files = [manifest_path, control_path, iris_path, options_path, report_path, original_path, probe_path,
             Path(root) / consumer.MANIFEST, Path(root) / FREEZE]
    files += [Path(root) / source for _, source in consumer.ADDITIONS]
    files += [p for p in profile.rglob('*') if p.is_file() and
              (p.is_relative_to(profile / 'config') or p.is_relative_to(profile / 'resourcepacks')) and p not in files]
    sidecar = original_path.with_name(selected + '.txt')
    if sidecar.exists(): files.append(sidecar)
    return dict(scene='coastalwater', profile=profile, world=world, manifest=manifest, control=control,
        removed_control_overrides=removed, original=original, probe=private, qualification=qualification,
        native_options=native, patched_options=patched, option_evidence=evidence,
        source_files_sha256={str(p): file_hash(p) for p in files},
        provenance_files={'control-original.json': control_path, 'workload-manifest-original.json': manifest_path,
            'shaderlod-client-original.toml': profile / 'config/shaderlod-client.toml',
            'iris-original.properties': iris_path, 'options-original.txt': options_path,
            'workload-measurement-original.json': report_path})


def plan(output, source=SOURCE, probe=PROBE, root=ROOT):
    return copies.plan(output, ('coastalwater',), {'coastalwater': source}, probe, root) | dict(
        experiment=EXPERIMENT, manifest=str(Path(root) / consumer.MANIFEST), manifest_sha256=MANIFEST_SHA256,
        archive_sha256=ARCHIVE_SHA256, world_sha256=WORLD_SHA256, capability_schema=1, upstream_effective_option_count=366,
        untimed=True, gpu_timing=False, private_options={consumer.SWITCH: 'true'},
        runtime_scope='Only ON; driver/render/capability readback/still; no physical work, parity, motion or performance claim')


def prepare(output, source=SOURCE, probe=PROBE, root=ROOT, runtime_lock=None):
    operation = plan(output, source, probe, root)
    state = copies.prepare(output, ('coastalwater',), {'coastalwater': source}, probe, runtime_lock, root,
        source_inspector=inspect_scene, experiment=EXPERIMENT, marker_name='shared-lod-preparation.json',
        source_qualification_key='shared_lod_source_qualification')
    marker = Path(state['operation']['output']) / 'shared-lod-preparation.json'
    state.update(status='preparing-effects', shared_operation=operation); copies.write_json(marker, state)
    try:
        base = Path(state['operation']['output']) / 'coastalwater'
        manifest_path = base / 'workload-manifest.json'; manifest = json.loads(manifest_path.read_text())
        manifest.update(configuration_only=True, preparation_status='preparing-effects'); copies.write_json(manifest_path, manifest)
        for arm in copies.ARMS:
            profile = base / arm
            effects_path = profile / 'config/shaderlod-effects-client.toml'; effects_path.write_text(effects_config('adaptive'))
            record = manifest['experiment_arms'][arm]
            record.update(effects_config_sha256=file_hash(effects_path), requested_effects_config=tomllib.loads(effects_path.read_text()),
                qa_only=True, capability_schema=1)
            world = profile / 'saves' / WORLD
            record['input_files_sha256'] = {str(p.relative_to(profile)): file_hash(p) for p in profile.rglob('*') if p.is_file() and not p.is_relative_to(world)}
        manifest.update(configuration_only=False, preparation_status='complete', shared_operation=operation)
        copies.write_json(manifest_path, manifest)
        state['status'] = 'complete'; copies.write_json(marker, state)
    except BaseException as error:
        state.update(status='failed', error=str(error)); copies.write_json(marker, state); raise
    return state


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True); parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    try: print(json.dumps((plan if args.dry_run else prepare)(args.output), indent=2, sort_keys=True))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, 'Cannot prepare shared LOD reference runtime: ' + str(error) + '\n')


if __name__ == '__main__': main()
