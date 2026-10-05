#!/usr/bin/env python3
"""Prepare stopped, matched Photon isolation profiles; never launch a client."""
from __future__ import annotations

import argparse
from contextlib import ExitStack
from datetime import datetime, timezone
import json
import math
from pathlib import Path, PurePosixPath
import shutil
import tomllib
import zipfile

from setup_comparison import ROOT, PACK, WORLD, file_hash, unlocked_world, world_fingerprint
from run_proof_matrix import read_option_properties, declared_defaults, normalize_requested_options, canonical_options_hash
from prepare_workloads import declared_options

PINNED = {
    'analytic': ('build/photon-analytic-candidate.zip', '7cae01ced084885d6e66102b26803077fb0ed2093f854fe791b1ac8327abba19'),
    'structural': ('build/photon-structural-candidate.zip', '999eae1c4c3551e0f02d45ae06b517c87f966b87b7ca6a8e779a16566c6a003b'),
}
ORIGINAL_SHA256 = '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
SCENES = ('forest', 'cloudstorm', 'coastalwater')
MODES = ('high', 'patched-off', 'adaptive')
BANDS = {'current': (96.0, 256.0, 32.0), 'old': (32.0, 96.0, 8.0)}
CONTROL_OVERRIDES = ('enabled', 'terrainEnabled', 'terrainChunkCap', 'warmSourceConfirmed')


def archive_contents(path, expected_sha256, photon, original_license=None):
    """Verify exact bytes and attribution before using any archive content."""
    if not expected_sha256 or len(expected_sha256) != 64: raise ValueError('Exact archive SHA256 is required')
    if file_hash(path) != expected_sha256: raise ValueError('Isolation archive checksum mismatch: ' + str(path))
    with zipfile.ZipFile(path) as archive:
        names = [info.filename for info in archive.infolist() if not info.is_dir()]
        if len(names) != len(set(names)): raise ValueError('Duplicate archive entries')
        if any(PurePosixPath(name).is_absolute() or '..' in PurePosixPath(name).parts for name in names):
            raise ValueError('Unsafe archive entry')
        required = ['LICENSE', 'shaders/settings.glsl']
        if original_license is not None: required.append('shaderlod-build.json')
        if any(name not in names for name in required): raise ValueError('Archive license/settings/build metadata missing')
        license_bytes = archive.read('LICENSE')
        if not license_bytes.strip() or original_license is not None and license_bytes != original_license:
            raise ValueError('Derivative Photon license differs from pinned original')
        metadata = None
        if original_license is not None:
            metadata = json.loads(archive.read('shaderlod-build.json'))
            if metadata.get('base_version') != photon['version'] or metadata.get('base_sha512') != photon['hashes']['sha512']:
                raise ValueError('Derivative archive metadata does not match pinned Photon')
            if metadata.get('bridge_version') != 2: raise ValueError('Unsupported ShaderLOD uniform bridge')
        settings = archive.read('shaders/settings.glsl').decode()
        declarations = {}
        for name in sorted(names):
            if name.endswith(('.glsl', '.vsh', '.fsh', '.gsh', '.csh')):
                for option, value in declared_defaults(archive.read(name).decode()).items():
                    declarations.setdefault(option, []).append([name, value])
    return {'sha256': expected_sha256, 'license_sha256': __import__('hashlib').sha256(license_bytes).hexdigest(),
            'license_bytes': license_bytes, 'settings': settings, 'metadata': metadata, 'declarations': declarations}


def plan(args):
    """Pure path/variant planning; no world hash, archive read, copy or launch."""
    source, output = args.source_proof_root.resolve(), args.output.resolve()
    if source == output or source in output.parents or output in source.parents:
        raise ValueError('Isolation output must be separate from the source proof root')
    if len(args.scenes) != len(set(args.scenes)) or not args.scenes: raise ValueError('Duplicate or empty scenes')
    if args.archive not in PINNED: raise ValueError('Unknown isolation archive')
    if args.water_bands not in BANDS: raise ValueError('Unknown water band policy')
    if any(scene not in SCENES for scene in args.scenes): raise ValueError('Unknown scene')
    if output.exists() and not args.backup_existing:
        raise ValueError('Preserving existing isolation output; use a fresh --output or --backup-existing: ' + str(output))
    if output.exists() and args.backup_existing:
        marker = output / 'isolation-manifest.json'
        if not marker.is_file() or json.loads(marker.read_text()).get('schema') != 1:
            raise ValueError('Backup replacement requires an existing isolation output marker')
    archive = (args.archive_path or ROOT / PINNED[args.archive][0]).resolve()
    if output == archive or output in archive.parents: raise ValueError('Output may not contain its source archive')
    return {'source_proof_root': str(source), 'output': str(output), 'archive': str(archive),
            'archive_sha256': PINNED[args.archive][1], 'variant': args.archive,
            'analytic_enabled': args.analytic == 'on', 'water_bands': BANDS[args.water_bands],
            'scene_operations': [dict(scene=scene, source_profile=str(source / scene / 'high'),
                                      target_profiles=[str(output / scene / mode) for mode in MODES]) for scene in args.scenes]}


def common_options(source, selected_archive, candidate=None):
    """Use stopped source's resolved values when available, never normalized file bytes."""
    iris = read_option_properties((source / 'config/iris.properties').read_text())
    selected = iris.get('shaderPack', '')
    if not selected or Path(selected).name != selected: raise ValueError('Invalid source Iris selected archive')
    if file_hash(source / 'shaderpacks' / selected) != selected_archive['sha256']:
        raise ValueError('Source high profile is not pinned original Photon: ' + str(source))
    raw_options = (source / 'shaderpacks' / (selected + '.txt')).read_text()
    requested = read_option_properties(raw_options)
    defaults = declared_defaults(selected_archive['settings'])
    archive_defaults = defaults | {name: records[0][1] for name, records in selected_archive['declarations'].items()
                                   if name not in defaults and len({record[1] for record in records}) == 1}
    normalize_requested_options(archive_defaults, requested)  # reject unknown/ambiguous source requests
    report_path = source / 'workload-measurement.json'
    evidence = 'Static declared defaults plus requested options; runtime unverified'
    if report_path.exists():
        report = json.loads(report_path.read_text())
        if report.get('status') != 'complete' or report.get('actual_shader_sha256') != selected_archive['sha256']:
            raise ValueError('Source measurement is incomplete or uses another archive')
        effective = report.get('effective_shader_options')
        if not isinstance(effective, dict) or not effective: raise ValueError('Source report lacks loaded effective options')
        if report.get('effective_shader_options_sha256') != canonical_options_hash(effective):
            raise ValueError('Source effective-option fingerprint mismatch')
        for name, value in requested.items():
            if name == 'INFO' and name not in effective and value == defaults.get(name): continue
            if effective.get(name) != value: raise ValueError('Current source options differ from measured loaded values: ' + name)
        common, option_evidence = filter_effective_options(effective, requested, selected_archive, candidate)
        evidence = 'Source runtime effective values captured; new profiles runtime pending'
    else:
        # Identical upstream source defaults supply absent values; do not invent
        # editable controls from unused preprocessor macros.
        common = {name: value for name, value in requested.items() if not name.startswith('SHADERLOD_')}
        option_evidence = {'source_effective_shader_options': None, 'omitted_non_settings_defaults': {},
                           'written_explicit_options': requested, 'note': 'No runtime option map available; matching explicit overrides over identical source defaults'}
    return common, evidence, option_evidence


def filter_effective_options(effective, requested, original, candidate):
    """Write settings controls and explicit user overrides, not incidental header guards."""
    defaults = declared_defaults(original['settings'])
    common = {name: value for name, value in effective.items() if name in defaults and not name.startswith('SHADERLOD_')}
    omitted = {name: value for name, value in effective.items() if name not in defaults and not name.startswith('SHADERLOD_')}
    for name, value in requested.items():
        if name.startswith('SHADERLOD_'): raise ValueError('Original source unexpectedly requests ShaderLOD options')
        if name == 'INFO' and name not in effective and value == defaults.get(name): continue
        if effective.get(name) != value: raise ValueError('Requested option differs from measured Iris value: ' + name)
        if name not in defaults:
            a = original['declarations'].get(name)
            b = candidate['declarations'].get(name) if candidate else None
            if not a or candidate and a != b: raise ValueError('Explicit non-settings control lacks matched archive declarations: ' + name)
        common[name] = value
        omitted.pop(name, None)
    return common, {'source_effective_shader_options': effective, 'omitted_non_settings_defaults': omitted,
                    'written_explicit_options': requested,
                    'note': 'Settings controls plus explicit overrides are written. Unrequested non-settings/header defaults retain each archive source; full source map preserved. These are declarations, not universal editable-control assumptions.'}


def isolation_options(common, settings, analytic_enabled, water_bands, declarations=None):
    defaults = declared_defaults(settings)
    if any(name not in defaults and name not in (declarations or {}) for name in common): raise ValueError('Derivative lacks an upstream option from source')
    near, far, transition = water_bands
    options = common | {
        'SHADERLOD_PRESERVE_LIGHTING': 'true', 'SHADERLOD_CACHED_SKY': 'false',
        'SHADERLOD_USE_MOD_CONFIG': 'true', 'SHADERLOD_ENABLED': 'true',
        'SHADERLOD_WATER_ANALYTIC': str(analytic_enabled).lower(),
        'SHADERLOD_WATER_NEAR': str(float(near)), 'SHADERLOD_WATER_FAR': str(float(far)),
        'SHADERLOD_WATER_TRANSITION': str(float(transition)),
        'SHADERLOD_CLOUD_NEAR': '128.0', 'SHADERLOD_CLOUD_FAR': '1024.0', 'SHADERLOD_CLOUD_TRANSITION': '64.0',
        'SHADERLOD_NEAR': '32.0', 'SHADERLOD_FAR': '96.0', 'SHADERLOD_TRANSITION': '8.0',
        'SHADERLOD_DEBUG': 'false',
    }
    allowed = declared_options(settings)
    for name, value in options.items():
        if name.startswith('SHADERLOD_') and (name not in allowed or value not in allowed[name]):
            raise ValueError(f'Unsupported isolation knob in exact archive: {name}={value}')
    return options


def read_control(source):
    control = json.loads((source.parent / 'control.json').read_text())
    for key in CONTROL_OVERRIDES: control.pop(key, None)
    for key in ('x', 'y', 'z', 'yaw', 'pitch'):
        if key not in control or not math.isfinite(control[key]): raise ValueError('Missing/nonfinite camera control: ' + key)
    control.update(debug=False)
    if control.get('preset', 'PERFORMANCE') not in ('BALANCED', 'PERFORMANCE', 'AGGRESSIVE'):
        raise ValueError('Unknown source preset')
    control.setdefault('preset', 'PERFORMANCE')
    return control


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')


def prepare(args):
    operation = plan(args)
    source_root, output = Path(operation['source_proof_root']), Path(operation['output'])
    runtime_lock_path = ROOT / 'dev/runtime.lock.json'
    runtime_lock = json.loads(runtime_lock_path.read_text())
    photon = runtime_lock['artifacts']['photon']
    original_path = ROOT / 'dev/upstream' / photon['filename']
    if output == original_path or output in original_path.parents: raise ValueError('Output contains the original archive')
    original = archive_contents(original_path, ORIGINAL_SHA256, photon)
    if file_hash(original_path, 'sha512') != photon['hashes']['sha512']: raise ValueError('Original runtime-lock checksum mismatch')
    archive_path = Path(operation['archive'])
    candidate = archive_contents(archive_path, operation['archive_sha256'], photon, original['license_bytes'])
    scenes = []
    for item in operation['scene_operations']:
        source = Path(item['source_profile'])
        world = source / 'saves' / WORLD
        if not (world / 'level.dat').is_file(): raise ValueError('Missing source save: ' + str(world))
        if not (source.parent / 'workload-manifest.json').is_file(): raise ValueError('Missing source workload provenance')
        provenance = json.loads((source.parent / 'workload-manifest.json').read_text())
        if provenance.get('configuration_only') or not provenance.get('source_world_sha256'):
            raise ValueError('Source lacks verified save provenance')
        control = read_control(source)
        common, evidence, option_evidence = common_options(source, original, candidate)
        patched = isolation_options(common, candidate['settings'], operation['analytic_enabled'], operation['water_bands'], candidate['declarations'])
        original_defaults, candidate_defaults = declared_defaults(original['settings']), declared_defaults(candidate['settings'])
        if any(candidate_defaults.get(name) != value for name, value in original_defaults.items() if not name.startswith('SHADERLOD_')):
            raise ValueError('Candidate changes upstream source defaults; not an isolated control')
        sources = [source / 'config/iris.properties', source / 'shaderpacks' / (photon['filename'] + '.txt'),
                   source / 'config/shaderlod-client.toml', source / 'options.txt', source.parent / 'control.json', source.parent / 'workload-manifest.json']
        sources += [path for path in (source / 'config/DistantHorizons.toml', source / 'config/sodium-options.json', source / 'workload-measurement.json') if path.exists()]
        for path in sources:
            if not path.is_file(): raise ValueError('Source input missing: ' + str(path))
        jars = list((source / 'mods').glob('*.jar'))
        dh = runtime_lock['artifacts']['distanthorizons']
        if any(jar.name != dh['filename'] for jar in jars): raise ValueError('Source has unmanaged optional profile mod jars')
        if jars and file_hash(jars[0], 'sha512') != dh['hashes']['sha512']: raise ValueError('Source DH artifact checksum mismatch')
        scenes.append(dict(scene=item['scene'], source=source, world=world, common=common, patched=patched,
                           control=control, provenance=provenance, evidence=evidence, option_evidence=option_evidence, source_files=sources, dh_jars=jars,
                           raw_source_files_sha256={str(path): file_hash(path) for path in sources}))
    if args.dry_run:
        print(json.dumps(operation | {'validation': 'Archive/config/provenance checks only; no world hashes/copies or runtime checks'}, indent=2)); return
    with ExitStack() as locks:
        for scene in scenes: locks.enter_context(unlocked_world(scene['world']))
        # Lock existing targets before preserving the full directory as a backup.
        if output.exists():
            for world in output.glob('*/' + '*/saves/' + WORLD): locks.enter_context(unlocked_world(world))
        stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
        backup = None
        if output.exists():
            backup = output.with_name(output.name + '.backup-' + stamp)
            if backup.exists(): raise ValueError('Backup collision')
            output.rename(backup)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.mkdir(exist_ok=False)  # reserve atomically; never merge with an existing tree
        state = {'schema': 1, 'status': 'preparing', 'created_at_utc': stamp, 'operation': operation,
                 'backup': str(backup) if backup else None, 'runtime_lock_sha256': file_hash(runtime_lock_path),
                 'original_sha256': original['sha256'], 'candidate_sha256': candidate['sha256'],
                 'candidate_metadata': candidate['metadata'], 'license_sha256': candidate['license_sha256'], 'scenes': {}}
        write_json(output / 'isolation-manifest.json', state)
        try:
            for scene in scenes:
                base = output / scene['scene']; base.mkdir()
                fingerprint = world_fingerprint(scene['world'])
                copies = {}
                for mode in MODES:
                    target = base / mode
                    (target / 'saves').mkdir(parents=True)
                    copied_world = target / 'saves' / WORLD
                    shutil.copytree(scene['world'], copied_world, ignore=shutil.ignore_patterns('session.lock'))
                    copies[mode] = world_fingerprint(copied_world)
                    if copies[mode] != fingerprint: raise ValueError('Copied save differs from stopped source')
                    shutil.copytree(scene['source'] / 'config', target / 'config')
                    (target / 'mods').mkdir()
                    if not args.without_dh:
                        for jar in scene['dh_jars']: shutil.copy2(jar, target / 'mods' / jar.name)
                    shutil.copy2(scene['source'] / 'options.txt', target / 'options.txt')
                    (target / 'shaderpacks').mkdir()
                    selected = photon['filename'] if mode == 'high' else PACK
                    shutil.copy2(original_path if mode == 'high' else archive_path, target / 'shaderpacks' / selected)
                    options = scene['common'] if mode == 'high' else scene['patched']
                    (target / 'shaderpacks' / (selected + '.txt')).write_text(''.join(f'{name}={value}\n' for name, value in sorted(options.items())))
                    iris = read_option_properties((scene['source'] / 'config/iris.properties').read_text())
                    iris.update(enableShaders='true', shaderPack=selected)
                    (target / 'config/iris.properties').write_text(''.join(f'{name}={value}\n' for name, value in sorted(iris.items())))
                    config = ('[lod]\nenabled=' + str(mode == 'adaptive').lower() + '\npreset="' + scene['control']['preset'] + '"\n'
                              'near=32.0\nfar=96.0\ntransition=8.0\ndebug=false\n[terrain]\nenabled=false\n'
                              'vanillaChunkCap=16\nwarmSourceConfirmed=false\nwarmupSeconds=30\n')
                    tomllib.loads(config)
                    (target / 'config/shaderlod-client.toml').write_text(config)
                if world_fingerprint(scene['world']) != fingerprint: raise ValueError('Source save changed during preparation')
                if {str(path): file_hash(path) for path in scene['source_files']} != scene['raw_source_files_sha256']:
                    raise ValueError('Source config/control/report changed during preparation')
                write_json(base / 'control.json', scene['control'])
                manifest = scene['provenance'] | {
                    'created_at_utc': stamp, 'scene': scene['scene'], 'configuration_only': False,
                    'source_world': str(scene['world']), 'source_world_sha256': fingerprint, 'copied_world_sha256': copies,
                    'control': scene['control'], 'proof_profiles': list(MODES), 'geometry_enabled': False, 'shader_only': True,
                    'adaptive_pack_sha256': candidate['sha256'], 'adaptive_build': candidate['metadata'],
                    'quality_options': {'high': scene['common'], 'patched-off': scene['patched'], 'adaptive': scene['patched']},
                    'validation': scene['evidence'], 'isolation_variant': operation,
                    'source_option_evidence': scene['option_evidence'],
                    'raw_source_files_sha256': scene['raw_source_files_sha256'],
                    'source_provenance': scene['provenance'], 'runtime_lock': runtime_lock,
                    'dh_installed': bool(scene['dh_jars']) and not args.without_dh,
                }
                write_json(base / 'workload-manifest.json', manifest)
                state['scenes'][scene['scene']] = {'source_world_sha256': fingerprint, 'copied_world_sha256': copies,
                                                  'common_effective_options_sha256': canonical_options_hash(scene['common'])}
                write_json(output / 'isolation-manifest.json', state)
            state['status'] = 'complete'; write_json(output / 'isolation-manifest.json', state)
        except BaseException as error:
            state.update(status='failed', error=str(error)); write_json(output / 'isolation-manifest.json', state)
            raise
    print('Prepared', output, 'variant', args.archive, 'analytic', args.analytic, 'water bands', args.water_bands)
    print('Static copies/options only; shader compile, rendering, timing and visual checks remain separate.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-proof-root', type=Path, default=ROOT / 'run-workloads/photon-proof-v2')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--archive', choices=PINNED, default='analytic')
    parser.add_argument('--archive-path', type=Path, help='Alternate location, still requires exact preserved archive hash')
    parser.add_argument('--analytic', choices=('on', 'off'), default='on')
    parser.add_argument('--water-bands', choices=BANDS, default='current')
    parser.add_argument('--scenes', nargs='+', choices=SCENES, default=['forest', 'cloudstorm', 'coastalwater'])
    parser.add_argument('--without-dh', action='store_true')
    parser.add_argument('--backup-existing', action='store_true', help='Preserve existing output under a timestamped sibling backup before recreating')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    try: prepare(args)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f'Cannot prepare isolations: {error}\n')


if __name__ == '__main__': main()
