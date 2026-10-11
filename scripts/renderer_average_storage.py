#!/usr/bin/env python3
"""Versioned storage adapter for the existing Bliss renderer averaged recipe.

No clients are launched by inventory/prepare. run is an explicit runtime action,
using the frozen scientific runner with only its storage disposal replaced.
"""
from __future__ import annotations
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys

import benchmark_storage as storage

RUNNER_SHA256 = '162f479eecfdd357b8e7d8d9b90dc694dc9b28634446a15510e609ee730ef340'
ARMS = ('original', 'candidate', 'return')
WORLD = 'ShaderLOD-Landscape'
ROOT = Path(__file__).resolve().parents[1]


def load_client(root):
    spec = importlib.util.spec_from_file_location('storage_dev_client', root / 'scripts/dev_client.py')
    client = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(client)
    return client


def inputs(template, world, profiles, output):
    template, world, profiles, output = map(storage.safe_path, (template, world, profiles, output))
    storage.require(not profiles.exists() and not output.exists(), 'Use fresh next-attempt profile/report roots')
    protected = [template, world, *[storage.safe_path(json.loads((template/a/'plan.json').read_text())['profile']) for a in ARMS]]
    for target in (profiles, output):
        storage.require(all(target != p and p not in target.parents and target not in p.parents for p in protected),
                        'Output overlaps canonical or template input')
    storage.require(profiles != output and profiles not in output.parents and output not in profiles.parents,
                    'Reports must be separate from profiles')
    storage.capacity(profiles)  # Cheap destination reserve admission before traversal.
    storage.capacity(output)
    world_size = storage.inventory(world, ('session.lock',))
    records = []
    required = 0
    for arm in ARMS:
        stage = template / arm
        plan = json.loads((stage/'plan.json').read_text())
        profile = storage.safe_path(plan['profile'])
        # This recipe copies only the same config/options/mods inputs as current preparation.
        sizes = [storage.inventory(profile/n)['bytes'] for n in ('config', 'mods')]
        sizes += [(profile/'options.txt').stat().st_size, storage.safe_path(plan['pack']).stat().st_size,
                  (stage/'options.txt').stat().st_size]
        required += sum(sizes) + world_size['bytes']
        records.append(dict(arm=arm, plan=plan, template_profile=str(profile)))
    admission = storage.capacity(profiles, required)
    report_admission = storage.capacity(output)
    if admission['device'] == report_admission['device']:
        admission = storage.capacity(profiles, required, 2 * storage.RESERVE_BYTES)
    return dict(profiles=str(profiles), output=str(output), canonical_world=str(world),
                world_inventory=world_size, profile_admission=admission,
                report_admission=report_admission, arms=records)


def prepare(template, world, profiles, output, root=ROOT):
    client = load_client(root)
    with client.runtime_lock():
        storage.require(not client.clients(client.process_table()), 'Active client; preparation refused')
        operation = inputs(template, world, profiles, output)
        client.validate_built()
        # Authentic existing freeze map; no invented world fingerprint or quality admission.
        sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
        worldmap = {str(p.relative_to(world)): sha(p) for p in sorted(world.rglob('*'))
                    if p.is_file() and p.name != 'session.lock'}
        for arm in ARMS:
            freeze = json.loads((template/arm/'freeze.json').read_text())
            expected = {p:h for p,h in freeze['canonicalWorld'].items() if Path(p).name != 'session.lock'}
            storage.require(worldmap == expected, 'Canonical world differs from authentic freeze')
            storage.require(client.build_fingerprint() == freeze['build'], 'Existing build freeze drift')
            for name, expected_sha in freeze['files'].items():
                storage.require(sha(storage.safe_path(name)) == expected_sha, 'Authentic template freeze drift: '+name)
        profiles.mkdir(parents=True); output.mkdir(parents=True)
        manifest = storage.create_manifest(profiles, [world, template], operation)
        for row in operation['arms']:
            arm = row['arm']; source = template/arm; stage = output/arm; stage.mkdir()
            profile = profiles/arm/'client'; profile.mkdir(parents=True)
            prior = Path(row['template_profile'])
            for name in ('config', 'mods'):
                shutil.copytree(prior/name, profile/name)
            shutil.copy2(prior/'options.txt', profile/'options.txt')
            dst = profile/'saves'/WORLD; dst.parent.mkdir()
            shutil.copytree(world, dst, ignore=shutil.ignore_patterns('session.lock'))
            storage.register_clone(profiles, dst)
            storage.require(worldmap == {str(p.relative_to(dst)):sha(p) for p in sorted(dst.rglob('*')) if p.is_file()},
                            'Copied world differs')
            for name in ('options.txt', 'expected-options.json', 'control.json', 'core.toml'):
                shutil.copy2(source/name, stage/name)
            plan = row['plan'] | dict(profile=str(profile), installed_pack=str(profile/'shaderpacks'/row['plan']['pack_name']),
                pack_options=str(stage/'options.txt'), expected_options_source=str(stage/'expected-options.json'), stop_at_utc=None)
            plan['command'] = [('-PrunDirectory='+str(profile)) if x.startswith('-PrunDirectory=') else
                               ('-PcontrolCaptureReceipt='+str(stage/'captures')) if x.startswith('-PcontrolCaptureReceipt=') else x
                               for x in plan['command']]
            client.prepare(plan, stage/'preparation-backups')
            shutil.copy2(stage/'core.toml', profile/'config/shaderlod-client.toml')
            shutil.copy2(stage/'control.json', profile.parent/'control.json')
            storage.write(stage/'plan.json', plan)
            files = [Path(plan['pack']), stage/'options.txt', stage/'core.toml', stage/'control.json',
                     stage/'expected-options.json', stage/'plan.json', Path(plan['sourceReview'])]
            storage.write(stage/'freeze.json', dict(build=client.build_fingerprint(),
                files={str(p):sha(p) for p in files}, canonicalWorld=worldmap))
        storage.write(output/'storage-recipe.json', dict(schema=1, profiles=str(profiles), owner=manifest['owner'],
                      runner_sha256=RUNNER_SHA256, template=str(template)))
    return operation


def dispose(client, stage, profile, enabled):
    if not enabled:
        return  # Default preservation, including current-run clones.
    output = stage.parent
    recipe = json.loads((output/'storage-recipe.json').read_text())
    profiles = storage.safe_path(recipe['profiles'])
    # Each arm releases one clone, without permitting a standalone/all-runs cleanup.
    marker = profiles/storage.MARKER
    record = json.loads(marker.read_text())
    world = profile/'saves'/WORLD
    with client.runtime_lock():
        table = client.process_table()
        owner = json.loads((stage/'owner.json').read_text())
        storage.require(not client.clients(table), 'Active client; clone retained')
        saved = list(owner.get('clients', {}).items())
        storage.require(saved and owner.get('launcher'), 'Missing exact child/launcher ownership')
        storage.require(all(int(pid) not in table for pid, proof in saved), 'Recorded child PID remains; clone retained')
        launcher = owner['launcher']
        storage.require(int(launcher['pid']) not in table, 'Recorded launcher PID remains; clone retained')
        # Complete prior-arm cleanup remains recorded; operate only on current exact clone.
        selected = [c for c in record['disposable_world_clones'] if c['path'] == str(world)]
        storage.require(len(selected) == 1, 'Current clone absent from manifest')
        all_clones = record['disposable_world_clones']
        record['disposable_world_clones'] = selected
        record['release'] = dict(children_reaped=True, authorized_owner=recipe['owner'], lane_lock=str(client.LOCK))
        storage.write(marker, record)
        try:
            with current_lock(client.LOCK) as lock:
                storage.cleanup(profiles, recipe['owner'],
                [stage/'release.json', stage/'postrun-world-before-disposal.json', stage/'owner.json',
                 stage/'console.log', profile/'workload-measurement.json', profile/'screenshots/workload-final.png'],
                    lock, [owner['launcher_session']], dry_run=False)
        finally:
            updated = json.loads(marker.read_text())
            updated['disposable_world_clones'] = [c for c in all_clones if Path(c['path']).exists()]
            updated.setdefault('arm_cleanup', {})[stage.name] = updated.get('cleanup')
            storage.write(marker, updated)


def current_lock(path):
    # cleanup verifies another descriptor cannot acquire the lock; context above
    # owns the actual descriptor, this descriptor identifies the same lock file.
    return open(path, 'a+')


def adapted_runner(source, root, output, cleanup_enabled):
    storage.require(hashlib.sha256(source.encode()).hexdigest() == RUNNER_SHA256, 'Frozen renderer runner changed; review adapter')
    old = "R=Path('/Users/pippo/github/shaderlod');Q=Path(__file__).resolve().parent/sys.argv[1]"
    storage.require(source.count(old) == 1 and source.count('shutil.rmtree(world)') == 1, 'Runner adapter boundary drift')
    source = source.replace(old, 'R=Path('+repr(str(root))+');Q=Path('+repr(str(output))+')')
    return source.replace('shutil.rmtree(world)', '_storage_dispose(d,A,P,'+repr(cleanup_enabled)+')')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('inventory', 'prepare', 'run'))
    parser.add_argument('--template', type=Path, required=True)
    parser.add_argument('--world', type=Path)
    parser.add_argument('--profiles', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--cleanup-world-clones', action='store_true')
    args = parser.parse_args()
    if args.action == 'run':
        recipe = json.loads(storage.safe_path(args.output/'storage-recipe.json').read_text())
        profiles = storage.safe_path(recipe['profiles'])
        manifest = json.loads(storage.safe_path(profiles/storage.MARKER).read_text())
        storage.require(recipe['runner_sha256'] == RUNNER_SHA256 and manifest['owner'] == recipe['owner']
                        and manifest['root_identity'] == storage.identity(profiles), 'Next-attempt storage manifest drift')
        for arm in ARMS:
            plan = json.loads(storage.safe_path(args.output/arm/'plan.json').read_text())
            storage.require(storage.safe_path(plan['profile']) == profiles/arm/'client', 'Plan escapes owned next-attempt profiles')
        source = (args.template.parent/'run_bliss_renderer.py').read_text()
        adapted = adapted_runner(source, ROOT, args.output, args.cleanup_world_clones)
        exec(compile(adapted, str(args.template.parent/'run_bliss_renderer.py'), 'exec'),
             dict(__file__=str(args.template.parent/'run_bliss_renderer.py'), _storage_dispose=dispose))
    else:
        parser.error('--world and --profiles required') if not args.world or not args.profiles else None
        value = (inputs if args.action == 'inventory' else prepare)(args.template, args.world, args.profiles, args.output)
        print(json.dumps(value, indent=2))


if __name__ == '__main__': main()
