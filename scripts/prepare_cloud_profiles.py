#!/usr/bin/env python3
"""Prepare fresh, matched cloud-transition controls from stopped original Photon scenes."""
from __future__ import annotations

import argparse
import contextlib
import io
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import zipfile

from prepare_isolations import ROOT, PACK, WORLD, archive_contents, plan, prepare, write_json
from prepare_effect_profiles import EFFECTS
from setup_comparison import unlocked_world, world_fingerprint
from run_proof_matrix import read_option_properties, canonical_options_hash
from prepare_workloads import declared_options

SELECTOR = 'cloud-transition-no-atmosphere'
SCENES = ('coastalwater', 'cloudstorm')
ARMS = ('high', 'patched-off', 'alloff', 'cloud-current', 'cloud-independent',
        'cloud-smooth', 'cloud-independent-unprotected')
MODE_PROTECTION = {
    'patched-off': (0, False), 'alloff': (0, False), 'cloud-current': (0, False),
    'cloud-independent': (1, True), 'cloud-smooth': (2, True),
    'cloud-independent-unprotected': (1, False),
}


def cloud_flags(arm):
    if arm not in ARMS: raise ValueError('Unknown cloud arm: ' + arm)
    if arm == 'high': return {}
    mode, protection = MODE_PROTECTION[arm]
    flags = {option: str(effect == 'cloud' and arm != 'alloff').lower()
             for effect, option in EFFECTS.items()}
    return flags | {'SHADERLOD_CLOUD_MODE': str(mode), 'SHADERLOD_CLOUD_OVERHEAD': str(protection).lower(),
                    'SHADERLOD_PRESERVE_LIGHTING': 'true', 'SHADERLOD_CACHED_SKY': 'false',
                    'SHADERLOD_WATER_ANALYTIC': 'false'}


def options_for_arm(common, arm, declarations, cloud_upscaling):
    if str(cloud_upscaling) not in ('1', '4'): raise ValueError('Cloud resolution control must be 1 or 4')
    requested = dict(common) | {'CLOUDS_TEMPORAL_UPSCALING': str(cloud_upscaling)} | cloud_flags(arm)
    for name in ('CLOUDS_TEMPORAL_UPSCALING', *cloud_flags(arm)):
        if name not in declarations or requested[name] not in declarations[name]:
            raise ValueError(f'Exact archive lacks requested cloud control: {name}={requested[name]}')
    return requested


def plan_clouds(args):
    if getattr(args, 'backup_existing', False): raise ValueError('Cloud profiles require a fresh output; backup replacement is not supported')
    if str(args.cloud_upscaling) not in ('1', '4'): raise ValueError('Cloud resolution control must be 1 or 4')
    if any(scene not in SCENES for scene in args.scenes): raise ValueError('Cloud scenes must be coastalwater or cloudstorm')
    args.archive = SELECTOR
    args.analytic = 'off'
    args.water_bands = 'current'
    args.backup_existing = False
    operation = plan(args)
    operation.update(arms=list(ARMS), cloud_upscaling=int(args.cloud_upscaling),
                     requested_cloud_options={arm: {'CLOUDS_TEMPORAL_UPSCALING': str(args.cloud_upscaling)} | cloud_flags(arm) for arm in ARMS},
                     dynamic_mod_enabled_arms=[arm for arm in ARMS if arm not in ('high', 'patched-off')],
                     preset_policy='Preserve source control preset; no automatic preset changes',
                     source_stack_note='Runner selects runtime stacks; this tool installs no optimization mods',
                     control_note='All arms retain original common sample counts. Independent unprotected separates sampling mode from overhead protection.')
    for item in operation['scene_operations']:
        item['target_profiles'] = [str(Path(operation['output']) / item['scene'] / arm) for arm in ARMS]
    return operation


def prepare_clouds(args):
    operation = plan_clouds(args)
    runtime = json.loads((ROOT / 'dev/runtime.lock.json').read_text())
    photon = runtime['artifacts']['photon']
    original_path = ROOT / 'dev/upstream' / photon['filename']
    with zipfile.ZipFile(original_path) as original:
        license_bytes = original.read('LICENSE')
        original_allowed = declared_options(original.read('shaders/settings.glsl').decode())
    candidate = archive_contents(Path(operation['archive']), operation['archive_sha256'], photon, license_bytes)
    candidate_allowed = declared_options(candidate['settings'])
    for arm in ARMS:
        options_for_arm({}, arm, original_allowed if arm == 'high' else candidate_allowed, args.cloud_upscaling)
    # Reuse pinned source/archive/license/config/report validation. Dry-run does
    # not lock/hash/copy worlds or change any files.
    if args.dry_run:
        with contextlib.redirect_stdout(io.StringIO()):
            prepare(args)
        operation['validation'] = 'Pinned archive/config/source provenance checks only; no world hashes, copies, writes, or runtime checks'
        print(json.dumps(operation, indent=2)); return
    prepare(args)
    output = args.output.resolve()
    state_path = output / 'isolation-manifest.json'
    state = json.loads(state_path.read_text())
    state.update(status='preparing-clouds', cloud_operation=operation)
    write_json(state_path, state)
    try:
        for scene in args.scenes:
            base = output / scene
            manifest_path = base / 'workload-manifest.json'
            manifest = json.loads(manifest_path.read_text())
            manifest.update(configuration_only=True, preparation_status='preparing-clouds')
            write_json(manifest_path, manifest)
            # Base isolation preparation made three verified copies. Move its
            # adaptive copy to alloff rather than make an unnecessary extra copy.
            adaptive, alloff = base / 'adaptive', base / 'alloff'
            if alloff.exists(): raise ValueError('Cloud profile collision: ' + str(alloff))
            fingerprint = manifest['source_world_sha256']
            with unlocked_world(adaptive / 'saves' / WORLD):
                if world_fingerprint(adaptive / 'saves' / WORLD) != fingerprint:
                    raise ValueError('Adaptive copy changed before cloud preparation')
            adaptive.rename(alloff)
            manifest['copied_world_sha256']['alloff'] = manifest['copied_world_sha256'].pop('adaptive')
            inherited = read_option_properties((alloff / 'shaderpacks' / (PACK + '.txt')).read_text())
            common = manifest['quality_options']['high']
            with unlocked_world(alloff / 'saves' / WORLD):
                for arm in ARMS:
                    target = base / arm
                    if arm not in ('high', 'patched-off', 'alloff'):
                        if target.exists(): raise ValueError('Cloud profile collision: ' + str(target))
                        shutil.copytree(alloff, target, ignore=shutil.ignore_patterns(
                            'session.lock', 'screenshots', 'logs', 'workload-measurement.json', 'workload-request.json'))
                        copied = world_fingerprint(target / 'saves' / WORLD)
                        if copied != fingerprint: raise ValueError('Cloud copy differs from stopped source')
                        manifest['copied_world_sha256'][arm] = copied
                    declarations = original_allowed if arm == 'high' else candidate_allowed
                    options = options_for_arm(common if arm == 'high' else inherited, arm, declarations, args.cloud_upscaling)
                    selected = photon['filename'] if arm == 'high' else PACK
                    (target / 'shaderpacks' / (selected + '.txt')).write_text(
                        ''.join(f'{name}={value}\n' for name, value in sorted(options.items())))
                    config = (target / 'config/shaderlod-client.toml').read_text()
                    expected = '[lod]\nenabled=' + str(arm not in ('high', 'patched-off')).lower() + '\n'
                    if not config.startswith(expected): raise ValueError('Base mod enable/preset config changed: ' + arm)
                    manifest['quality_options'][arm] = options
                if world_fingerprint(alloff / 'saves' / WORLD) != fingerprint:
                    raise ValueError('Template world changed during cloud preparation')
            manifest['quality_options'].pop('adaptive')
            manifest.update(configuration_only=False, preparation_status='complete', proof_profiles=list(ARMS),
                            cloud_flags={arm: cloud_flags(arm) for arm in ARMS}, cloud_operation=operation,
                            cloud_candidate_sha256=operation['archive_sha256'], analytic_enabled=False,
                            atmosphere_guard_present=False, cloud_upscaling=int(args.cloud_upscaling),
                            validation='Pinned archive/source provenance, copied save fingerprints and declared requested flags only; runtime options, shader rendering, timing and quality pending')
            write_json(manifest_path, manifest)
            state['scenes'][scene]['copied_world_sha256'] = manifest['copied_world_sha256']
            state['scenes'][scene]['requested_common_options_sha256'] = canonical_options_hash(manifest['quality_options']['high'])
        state.update(status='complete', cloud_profiles=list(ARMS), completed_at_utc=datetime.now(timezone.utc).isoformat())
        write_json(state_path, state)
    except BaseException as error:
        state.update(status='failed', error=str(error)); write_json(state_path, state)
        raise
    print('Prepared cloud controls:', output, 'candidate SHA256', operation['archive_sha256'])
    print('Original and patched arms share cloud spatial resolution and common sample counts; source presets are preserved.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-proof-root', type=Path, default=ROOT / 'run-workloads/photon-proof-v2')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--archive-path', type=Path, help='Alternate location; exact pinned cloud-transition SHA256 is still required')
    parser.add_argument('--scenes', nargs='+', choices=SCENES, default=['cloudstorm'])
    parser.add_argument('--cloud-upscaling', type=int, choices=(1, 4), default=1)
    parser.add_argument('--without-dh', action='store_true')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    try: prepare_clouds(args)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f'Cannot prepare cloud profiles: {error}\n')


if __name__ == '__main__': main()
