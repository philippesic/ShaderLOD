#!/usr/bin/env python3
"""Qualify a pinned density diagnostic and reuse stopped original coastal copies; no launch."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import tomllib
import zipfile

import prepare_photon_ao_reuse_profiles as copies
from build_photon_density_detail_probe import (BASE_SHA256, EXPERIMENT, MANIFEST, MEMBERS,
                                              METADATA, SWITCH, archive_members, patch_members, sha256)
from prepare_isolations import archive_contents, filter_effective_options
from run_proof_matrix import archive_defaults, canonical_options_hash, normalize_requested_options, read_option_properties
from setup_comparison import ROOT, WORLD, file_hash

MANIFEST_SHA256 = '542e6e61b1565532ce1d8975416917a29a866bc07165f80d5536d1a6124ad9dd'
PROBE = ROOT / 'build/Photon-DensityDetailCostControl-disabled.zip'
SOURCE = copies.DEFAULT_SOURCES['coastalwater']
SOURCE_REPORT_SHA256 = '9524f3fdc9dec30f59a03704ad45f9cffa84b2058fe2ed48ea6a3095c31ba252'
SOURCE_OPTIONS_SHA256 = 'ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669'
CLOUD_OPTIONS = dict(CLOUDS_TEMPORAL_UPSCALING='4', CLOUDS_SCALE='10.0',
    CLOUDS_CUMULUS_PRIMARY_STEPS_H='40', CLOUDS_CUMULUS_PRIMARY_STEPS_Z='20',
    CLOUDS_CUMULUS_LIGHTING_STEPS='6', CLOUDS_CUMULUS_AMBIENT_STEPS='2',
    CLOUDS_CUMULUS_DETAIL_STRENGTH='1.00', CLOUDS_CUMULUS_DENSITY='1.00',
    CLOUDS_CUMULUS_COVERAGE='1.00', CLOUDS_CUMULUS_PRECOMPUTE_LOCAL_COVERAGE='true',
    CLOUD_SHADOWS='false', COLORED_LIGHTS='false', TAAU='false')
require = copies.require


def qualify_archives(original_path, probe_path, runtime, root=ROOT):
    original = archive_contents(original_path, BASE_SHA256, runtime['artifacts']['photon'])
    require(file_hash(original_path, 'sha512') == runtime['artifacts']['photon']['hashes']['sha512'], 'Original runtime-lock checksum mismatch')
    manifest_path = Path(root) / MANIFEST; manifest_bytes = manifest_path.read_bytes()
    require(sha256(manifest_bytes) == MANIFEST_SHA256, 'Frozen density source manifest changed')
    manifest = json.loads(manifest_bytes); originals, private = archive_members(original_path), archive_members(probe_path)
    require(METADATA not in originals and set(private) == set(originals) | {METADATA}, 'Unexpected private member inventory')
    expected = patch_members(originals, manifest, 'disabled')
    changed = sorted(name for name in originals if private[name] != originals[name])
    require(changed == sorted(MEMBERS), 'Private changed member scope differs')
    require(all(private[name] == data for name, data in expected.items()), 'Private source differs from frozen density patch')
    metadata = json.loads(private[METADATA])
    contract = dict(experiment=EXPERIMENT, arm='disabled', base_version='v1.3b', base_sha256=BASE_SHA256,
        manifest_sha256=MANIFEST_SHA256, switch=SWITCH, switch_enabled=False, modified_upstream_files=changed,
        member_sha256={name: sha256(expected[name]) for name in MEMBERS}, license_sha256=sha256(originals['LICENSE']),
        unchanged_upstream_member_count=len(originals)-len(changed), consumed_core_fields=[],
        activation='private option AND PROGRAM_DEFERRED1 AND bridge version 2 AND global enabled exactly 1',
        program_scope='PROGRAM_DEFERRED1 only; main-pass cumulus primary and secondary density evaluations',
        diagnostic_fine_noise=0.5, quality_class='quality_changing_cost_control',
        source_policy='Fine worley_1 texture replaced by declared 0.5 diagnostic; coarse noise, literal ray counts/extents/seeds/phase functions and reconstruction unchanged',
        physical_work_caveat='Density/occupancy and upstream early exits can change; not an isolated fetch budget or accepted optimization',
        distance_units='upstream atmosphere coordinates; diagnostic has no distance/footprint threshold',
        transition='uniform global control; intentional density change, no quality transition or calibration',
        fallback='literal original fine texture sample for private off, other programs, absent/unknown core or global off',
        runtime_compilation='unverified', gpu_benefit='unmeasured',
        attribution='Photon v1.3b by Benjamin Stott (SixthSurge); original LICENSE retained')
    require(metadata == contract, 'Private metadata differs from density contract')
    with zipfile.ZipFile(probe_path) as archive: require(archive_defaults(archive).get(SWITCH) == 'false', 'Density private gate must default off')
    original['path'] = str(original_path)
    probe = dict(path=str(probe_path), sha256=file_hash(probe_path), metadata=metadata)
    qualification = contract | dict(qualified=True, archive_sha256=probe['sha256'], manifest_path=str(manifest_path),
        option_override_consumed_core_fields=['shaderlodBridgeVersion', 'shaderlodEnabled'],
        physical_work_caveat='Density/occupancy/early exits can change; no isolated fetch-budget or LOD acceptance')
    return original, probe, qualification


def native_options(profile, original, probe):
    iris = read_option_properties((profile / 'config/iris.properties').read_text())
    sidecar = profile / 'shaderpacks' / (iris['shaderPack'] + '.txt')
    requested = read_option_properties(sidecar.read_text()) if sidecar.exists() else {}
    report = json.loads((profile / 'workload-measurement.json').read_text()); effective = report.get('effective_shader_options')
    require(report.get('status') == 'complete' and report.get('actual_shader_sha256') == BASE_SHA256, 'Original source report pin/status mismatch')
    require(isinstance(effective, dict) and effective and report.get('effective_shader_options_sha256') == canonical_options_hash(effective), 'Original effective options fingerprint differs')
    require(all(effective.get(key) == value for key, value in CLOUD_OPTIONS.items()), 'Inspected native cloud settings changed')
    for boundary in ('input_state_start', 'input_state_end'):
        state = report.get(boundary, {})
        require([state.get('framebuffer_width'), state.get('framebuffer_height')] == [2976, 1672]
                and state.get('requested_chunks') == state.get('effective_chunks') == 24
                and state.get('shaders_in_use') is True and state.get('fallback') is False, 'Inspected native/radius/shader evidence differs')
    with zipfile.ZipFile(probe['path']) as archive: private_defaults = archive_defaults(archive)
    with zipfile.ZipFile(original['path']) as archive: original_defaults = archive_defaults(archive)
    require(all(private_defaults.get(key) == value for key, value in original_defaults.items()), 'Private changes upstream defaults')
    native, evidence = filter_effective_options(effective, requested, original, original)
    require(not any(name.startswith('SHADERLOD_') for name in native), 'Original private option contamination')
    normalize_requested_options(original_defaults, native)
    patched = native | {SWITCH: 'true'}; normalize_requested_options(private_defaults, patched)
    return native, patched, evidence


def inspect_scene(item, probe_path, runtime, root=ROOT):
    require(item['scene'] == 'coastalwater', 'Density pilot is coastalwater only')
    profile = Path(item['source_profile']); world = profile / 'saves' / WORLD
    require(not any(path.is_symlink() for path in profile.rglob('*')), 'Symlink source tree unsupported')
    require((world / 'level.dat').is_file(), 'Stopped coastal source save missing')
    report_path = profile / 'workload-measurement.json'
    require(file_hash(report_path) == SOURCE_REPORT_SHA256, 'Inspected coastal report source pin differs')
    report = json.loads(report_path.read_text())
    require(report.get('effective_shader_options_sha256') == SOURCE_OPTIONS_SHA256, 'Coastal source preset pin differs')
    manifest_path, control_path = profile.parent / 'workload-manifest.json', profile.parent / 'control.json'
    manifest = json.loads(manifest_path.read_text())
    require(manifest.get('configuration_only') is False and manifest.get('source_world_sha256'), 'Coastal save provenance missing')
    control, removed = copies.control_for_scene(json.loads(control_path.read_text()), 'coastalwater')
    iris_path = profile / 'config/iris.properties'; iris = read_option_properties(iris_path.read_text()); selected = iris.get('shaderPack', '')
    require(selected and Path(selected).name == selected and iris.get('enableShaders') == 'true', 'Invalid source Iris selection')
    original_path = profile / 'shaderpacks' / selected
    original, probe, qualification = qualify_archives(original_path, probe_path, runtime, root)
    native, patched, evidence = native_options(profile, original, probe)
    options_path = profile / 'options.txt'; minecraft = dict(line.split(':', 1) for line in options_path.read_text().splitlines() if ':' in line)
    require(minecraft.get('renderDistance') == '24' and not list((profile / 'mods').glob('*.jar')), 'Coastal source must be native24/no profile jars')
    tomllib.loads((profile / 'config/shaderlod-client.toml').read_text())
    files = [manifest_path, control_path, iris_path, options_path, report_path, original_path, probe_path, Path(root) / MANIFEST]
    files += [path for path in profile.rglob('*') if path.is_file() and (path.is_relative_to(profile / 'config') or path.is_relative_to(profile / 'resourcepacks')) and path not in files]
    sidecar = original_path.with_name(selected + '.txt')
    if sidecar.exists(): files.append(sidecar)
    return dict(scene='coastalwater', profile=profile, world=world, manifest=manifest, control=control,
        removed_control_overrides=removed, original=original, probe=probe, qualification=qualification,
        native_options=native, patched_options=patched, option_evidence=evidence,
        source_files_sha256={str(path): file_hash(path) for path in files},
        provenance_files={'control-original.json': control_path, 'workload-manifest-original.json': manifest_path,
                          'shaderlod-client-original.toml': profile / 'config/shaderlod-client.toml',
                          'iris-original.properties': iris_path, 'options-original.txt': options_path,
                          'workload-measurement-original.json': report_path})


def plan(output, source=SOURCE, probe=PROBE, root=ROOT):
    return copies.plan(output, ('coastalwater',), {'coastalwater': source}, probe, root) | dict(manifest=str(Path(root) / MANIFEST), experiment=EXPERIMENT, quality_class='quality_changing_cost_control')


def prepare(output, source=SOURCE, probe=PROBE, runtime_lock=None, root=ROOT):
    return copies.prepare(output, ('coastalwater',), {'coastalwater': source}, probe, runtime_lock, root,
        source_inspector=inspect_scene, experiment=EXPERIMENT, marker_name='photon-density-detail-preparation.json',
        source_qualification_key='density_detail_source_qualification')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True); parser.add_argument('--source', type=Path, default=SOURCE)
    parser.add_argument('--probe', type=Path, default=PROBE); parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    try:
        print(json.dumps((plan if args.dry_run else prepare)(args.output, args.source, args.probe), indent=2, sort_keys=True))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, 'Cannot prepare density detail cost control: ' + str(error) + '\n')


if __name__ == '__main__': main()
