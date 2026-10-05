#!/usr/bin/env python3
"""Prepare original-Photon ownership diagnostics; never run Minecraft or time a gain."""
import argparse
import json
from pathlib import Path
import shutil
import tomllib

from prepare_isolations import ROOT, WORLD, ORIGINAL_SHA256, prepare, write_json
from setup_comparison import file_hash, unlocked_world, world_fingerprint


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-proof-root', type=Path, default=ROOT / 'run-workloads/cloud-shore-default')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--coherence', action='store_true', help='Test frame-local frustum coherence; separate region lists stay off')
    args = parser.parse_args()
    args.scenes = ['coastalwater']
    args.archive = 'cloud-transition-no-atmosphere'
    args.archive_path = None
    args.analytic = 'off'
    args.water_bands = 'current'
    args.backup_existing = False
    args.without_dh = True
    args.dry_run = False
    prepare(args)  # Existing stopped-save, original archive, provenance and copy guards.
    output = args.output.resolve()
    state_path = output / 'isolation-manifest.json'
    state = json.loads(state_path.read_text())
    state['status'] = 'preparing-visibility'
    write_json(state_path, state)
    try:
        base = output / 'coastalwater'
        manifest_path = base / 'workload-manifest.json'
        manifest = json.loads(manifest_path.read_text())
        manifest.update(configuration_only=True, preparation_status='preparing-visibility')
        write_json(manifest_path, manifest)
        runtime = json.loads((ROOT / 'dev/runtime.lock.json').read_text())
        original = runtime['artifacts']['photon']['filename']
        original_file = ROOT / 'dev/upstream' / original
        if file_hash(original_file) != ORIGINAL_SHA256:
            raise ValueError('Pinned original archive changed')
        common = manifest['quality_options']['high']
        copied = {}
        for source_name, arm in [('high', 'baseline'), ('patched-off', 'disabled'), ('adaptive', 'enabled')]:
            source = base / source_name
            target = base / arm
            if target.exists(): raise ValueError('Diagnostic target collision: ' + str(target))
            with unlocked_world(source / 'saves' / WORLD):
                if world_fingerprint(source / 'saves' / WORLD) != manifest['source_world_sha256']:
                    raise ValueError('Prepared diagnostic save changed before configuration')
            source.rename(target)
            packs = target / 'shaderpacks'
            if not (packs / original).exists(): shutil.copyfile(original_file, packs / original)
            if file_hash(packs / original) != ORIGINAL_SHA256:
                raise ValueError('Original diagnostic archive checksum mismatch')
            (packs / (original + '.txt')).write_text(''.join(f'{k}={v}\n' for k, v in sorted(common.items())))
            for file in packs.iterdir():
                if file.name not in (original, original + '.txt'):
                    if not file.is_file(): raise ValueError('Unexpected shaderpack directory')
                    file.unlink()  # Only files in the new disposable diagnostic copy.
            iris = target / 'config/iris.properties'
            properties = iris.read_text().splitlines()
            if sum(line.startswith('shaderPack=') for line in properties) != 1:
                raise ValueError('Selected shader pack property missing or repeated')
            iris.write_text('\n'.join('shaderPack=' + original if line.startswith('shaderPack=') else line for line in properties) + '\n')
            config = target / 'config/shaderlod-client.toml'
            old = tomllib.loads(config.read_text())
            old['lod']['enabled'] = arm != 'baseline'
            old['lod']['debug'] = False
            old['terrain']['enabled'] = False
            old['terrain']['warmSourceConfirmed'] = False
            old['rendering'] = dict(optimizeShadowCulling=False,
                                    separateShadowVisibilityLists=arm == 'enabled' and not args.coherence,
                                    shadowFrustumCoherence=arm == 'enabled' and args.coherence)
            config.write_text(''.join('[' + section + ']\n' + ''.join(k + '=' + (str(v).lower() if isinstance(v, bool) else json.dumps(v)) + '\n' for k, v in values.items()) for section, values in old.items()))
            copied[arm] = world_fingerprint(target / 'saves' / WORLD)
            if copied[arm] != manifest['source_world_sha256']: raise ValueError('Diagnostic copy fingerprint changed')
        manifest.update(configuration_only=False, preparation_status='complete', proof_profiles=list(copied),
                        copied_world_sha256=copied, quality_options={arm: common for arm in copied},
                        visibility_diagnostic=True, original_photon_sha256=ORIGINAL_SHA256,
                        diagnostic_kind='frustum-coherence' if args.coherence else 'region-list-ownership',
                        validation='Stopped original save copies and configuration only; transformed ownership, sprites and rendering remain unverified. Diagnostic overhead prevents performance claims.')
        write_json(manifest_path, manifest)
        state.update(status='complete', visibility_diagnostic=True, diagnostic_profiles=list(copied))
        write_json(state_path, state)
    except BaseException as failure:
        state.update(status='failed', error=str(failure))
        write_json(state_path, state)
        raise
    print('Prepared original-Photon ownership diagnostics:', output)


if __name__ == '__main__': main()
