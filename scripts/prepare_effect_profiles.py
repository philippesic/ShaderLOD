#!/usr/bin/env python3
"""Prepare matched compile-time Photon effect controls from stopped original-high profiles."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import zipfile

from prepare_isolations import ROOT, PACK, WORLD, PINNED, SCENES, archive_contents, plan, prepare, write_json
from setup_comparison import unlocked_world, world_fingerprint, file_hash
from run_proof_matrix import read_option_properties, normalize_requested_options, declared_defaults
from prepare_workloads import declared_options

EFFECTS = {'cloud': 'SHADERLOD_CLOUDS', 'water': 'SHADERLOD_WATER', 'ssr': 'SHADERLOD_SSR',
           'fog': 'SHADERLOD_FOG', 'materials': 'SHADERLOD_MATERIALS'}
ARMS = ('high', 'patched-off', 'alloff', 'cloud-only', 'water-only', 'ssr-only', 'fog-only', 'materials-only', 'adaptive')


def effect_flags(arm):
    if arm not in ARMS: raise ValueError('Unknown effect arm: ' + arm)
    enabled = set(EFFECTS) if arm in ('adaptive', 'patched-off') else {arm[:-5]} if arm.endswith('-only') else set()
    return {option: str(effect in enabled).lower() for effect, option in EFFECTS.items()}


def options_for_arm(common, arm, declarations):
    if arm == 'high': return dict(common)
    requested = common | effect_flags(arm) | {'SHADERLOD_WATER_ANALYTIC': 'false', 'SHADERLOD_CACHED_SKY': 'false',
                                            'SHADERLOD_PRESERVE_LIGHTING': 'true'}
    for name in (*EFFECTS.values(), 'SHADERLOD_WATER_ANALYTIC', 'SHADERLOD_CACHED_SKY', 'SHADERLOD_PRESERVE_LIGHTING'):
        if name not in declarations or requested[name] not in declarations[name]:
            raise ValueError('Exact candidate lacks requested effect switch: ' + name)
    return requested


def plan_effects(args):
    args.archive = getattr(args, 'effect_archive', 'effects-no-atmosphere')
    args.analytic = 'off'
    args.water_bands = 'current'
    operation = plan(args)
    operation.update(arms=list(ARMS), source_stack_note='Runtime stacks remain selected by the runner, not installed here',
                     dynamic_mod_enabled_arms=[arm for arm in ARMS if arm not in ('high', 'patched-off')],
                     material_note='No POM benefit can be established if parallax/heightmap work is inactive; this arm remains an overhead/negative control')
    return operation


def prepare_effects(args):
    operation = plan_effects(args)
    runtime = json.loads((ROOT / 'dev/runtime.lock.json').read_text())
    photon = runtime['artifacts']['photon']
    candidate_path = Path(operation['archive'])
    original_path = ROOT / 'dev/upstream' / photon['filename']
    with zipfile.ZipFile(original_path) as original: license_bytes = original.read('LICENSE')
    candidate = archive_contents(candidate_path, operation['archive_sha256'], photon, license_bytes)
    allowed = declared_options(candidate['settings'])
    for arm in ARMS: options_for_arm({}, arm, allowed)
    if args.dry_run:
        prepare(args)  # existing archive/source provenance checks; never save hashes/copies
        print(json.dumps(operation, indent=2)); return
    prepare(args)  # existing collision, attribution, source/target lock and copied-world checks
    output = args.output.resolve()
    state_path = output / 'isolation-manifest.json'
    state = json.loads(state_path.read_text())
    state.update(status='preparing-effects', effect_operation=operation)
    write_json(state_path, state)
    try:
        for scene in args.scenes:
            base = output / scene
            manifest_path = base / 'workload-manifest.json'
            manifest = json.loads(manifest_path.read_text())
            manifest.update(configuration_only=True, preparation_status='preparing-effects')
            write_json(manifest_path, manifest)
            adaptive = base / 'adaptive'
            inherited = read_option_properties((adaptive / 'shaderpacks' / (PACK + '.txt')).read_text())
            fingerprint = manifest['source_world_sha256']
            with unlocked_world(adaptive / 'saves' / WORLD):
                if world_fingerprint(adaptive / 'saves' / WORLD) != fingerprint: raise ValueError('Adaptive copy changed before effect preparation')
                for arm in ARMS:
                    target = base / arm
                    if arm not in ('high', 'patched-off', 'adaptive'):
                        if target.exists(): raise ValueError('Effect profile collision: ' + str(target))
                        shutil.copytree(adaptive, target, ignore=shutil.ignore_patterns('session.lock', 'screenshots', 'logs', 'workload-measurement.json', 'workload-request.json'))
                        copied = world_fingerprint(target / 'saves' / WORLD)
                        if copied != fingerprint: raise ValueError('Effect copy differs from stopped source')
                        manifest['copied_world_sha256'][arm] = copied
                    if arm == 'high': continue
                    options = options_for_arm(inherited, arm, allowed)
                    (target / 'shaderpacks' / (PACK + '.txt')).write_text(''.join(f'{name}={value}\n' for name, value in sorted(options.items())))
                    config = (target / 'config/shaderlod-client.toml').read_text()
                    if arm == 'patched-off':
                        if not config.startswith('[lod]\nenabled=false\n'): raise ValueError('Disabled base config changed')
                    else:
                        if not config.startswith('[lod]\nenabled=true\n'): raise ValueError('Active base config changed')
                    manifest['quality_options'][arm] = options
                if world_fingerprint(adaptive / 'saves' / WORLD) != fingerprint: raise ValueError('Adaptive copy changed during preparation')
            manifest.update(configuration_only=False, preparation_status='complete', proof_profiles=list(ARMS),
                            effect_flags={arm: effect_flags(arm) for arm in ARMS if arm != 'high'},
                            effect_candidate_sha256=operation['archive_sha256'], analytic_enabled=False,
                            atmosphere_guard_present=False, effect_operation=operation,
                            materials_acceptance='Overhead/negative control while material parallax/heightmap work is inactive',
                            validation='Copied source/provenance and declared effect toggles only; Iris compilation, rendering, timings and fidelity pending')
            write_json(manifest_path, manifest)
        state.update(status='complete', effect_profiles=list(ARMS), completed_at_utc=datetime.now(timezone.utc).isoformat())
        write_json(state_path, state)
    except BaseException as error:
        state.update(status='failed', error=str(error)); write_json(state_path, state)
        raise
    print('Prepared effect controls:', output, 'candidate SHA256', operation['archive_sha256'])
    print('All patched arms share upstream options, archive, protected lighting and finite-difference water; effect switches vary.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-proof-root', type=Path, default=ROOT / 'run-workloads/ssr-probe')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--archive-path', type=Path, help='Alternate location; exact effects-no-atmosphere SHA256 still required')
    parser.add_argument('--effect-archive', choices=('effects-no-atmosphere', 'cloud-transition-no-atmosphere', 'lighting-tier-no-atmosphere'), default='effects-no-atmosphere', help='Explicit frozen candidate; preserves exact archive hash checks')
    parser.add_argument('--scenes', nargs='+', choices=SCENES, default=['coastalwater'])
    parser.add_argument('--without-dh', action='store_true', help='Preserve the SSR probe DH-absent path; use --dh absent in timing')
    parser.add_argument('--backup-existing', action='store_true')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    try: prepare_effects(args)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f'Cannot prepare effect profiles: {error}\n')


if __name__ == '__main__': main()
