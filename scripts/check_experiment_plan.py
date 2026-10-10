#!/usr/bin/env python3
"""Read-only batch checks for existing dev_client plan.json or owner.json records.

No preparation, process inspection, Java, Gradle, UI or launch is performed.
Passing means static consistency only; historical inspection is never admission.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import sys

import dev_client as dc


def check_plan(record, *, freeze=None, receipt=None, historical=False,
               check_built=False, now=None):
    errors, checked = [], []

    def check(name, operation):
        try:
            operation()
            checked.append(name)
        except (ValueError, OSError, KeyError, TypeError, AttributeError, dc.zipfile.BadZipFile) as error:
            errors.append({'check': name, 'error': str(error)})

    def require(ok, message):
        dc.require(ok, message)

    def file(value):
        require(isinstance(value, str) and bool(value), 'Expected nonempty file path')
        path = Path(value)
        require(path.is_absolute() and path.is_file(), 'Missing absolute file: ' + value)
        return path

    def read_json(value):
        data = json.loads(file(value).read_text())
        require(isinstance(data, dict), 'Expected JSON object: ' + value)
        return data

    def finite(value):
        return isinstance(value, (float, int)) and not isinstance(value, bool) and math.isfinite(value)

    if not isinstance(record, dict):
        return {'errors': [{'check': 'schema', 'error': 'Expected JSON object'}], 'checked': []}
    p = record.get('plan', record)
    if not isinstance(p, dict):
        return {'errors': [{'check': 'schema', 'error': 'Expected plan object'}], 'checked': []}
    check('mode', lambda: require(p.get('mode') in ('on', 'off'), 'mode must be on/off'))
    for key in ('fullscreen', 'include_dh', 'already_built', 'apply_lod_settings'):
        check(key, lambda key=key: require(type(p.get(key)) is bool, key + ' must be boolean'))
    check('ready_timeout', lambda: require(finite(p.get('ready_timeout')) and
          1 <= p['ready_timeout'] <= 300, 'ready_timeout must be 1..300 seconds'))

    def deadline():
        require(isinstance(p.get('stop_at_utc'), str), 'Finite probe requires stop_at_utc ISO timestamp')
        end = datetime.fromisoformat(p['stop_at_utc'].replace('Z', '+00:00'))
        require(end.tzinfo is not None and end.utcoffset() is not None, 'Deadline needs timezone')
        if not historical:
            remaining = (end - (now or datetime.now(timezone.utc))).total_seconds()
            require(0 < remaining <= 43200, 'Deadline must be in next 12 hours')
            require(finite(p.get('ready_timeout')) and remaining > p['ready_timeout'] + 10,
                    'Deadline leaves no readiness plus 10-second teardown margin')
    check('finite_deadline', deadline)

    def profile():
        root = Path(p['profile'])
        require(root.is_absolute() and root.is_dir(), 'Profile missing/not absolute')
        file(str(root / 'saves' / dc.WORLD / 'level.dat'))
        file(str(root / 'config/iris.properties'))
    check('profile_world_iris', profile)
    check('shader_archive', lambda: require(dc.inspect_pack(file(p['pack'])) == p['archive_descriptor'],
                                          'archive_descriptor differs from actual ZIP'))
    check('pack_paths', lambda: require(Path(p['pack']).name == p['pack_name'] and
          Path(p['installed_pack']) == Path(p['profile']) / 'shaderpacks' / p['pack_name'],
          'pack_name/installed_pack disagree with source/profile'))
    if p.get('pack_options') is not None:
        check('pack_options', lambda: file(p['pack_options']).read_text())
    if p.get('apply_lod_settings'):
        check('settings_apply', lambda: require(p.get('mode') == 'on' and p.get('lod_state') in ('on', 'off'),
                                               'Settings Apply requires On task and lod_state on/off'))

    def command():
        argv = p['command']
        require(isinstance(argv, list) and len(argv) >= 2 and all(isinstance(a, str) for a in argv),
                'command must be an argv list')
        file(argv[0])
        require(argv[0] == str(dc.ROOT / ('gradlew.bat' if sys.platform == 'win32' else 'gradlew')),
                'Expected existing project Gradle launcher')
        require(argv.count('runClientLodOn' if p['mode'] == 'on' else 'runClientLodOff') == 1 and
                sum(a in ('runClientLodOn', 'runClientLodOff') for a in argv) == 1, 'Task/mode mismatch')
        props = {}
        for arg in argv:
            if arg.startswith('-P'):
                key, sep, value = arg[2:].partition('=')
                require(sep and key not in props, 'Missing/duplicate command property: ' + key)
                props[key] = value
        # build.gradle:24-31: minimal bypasses optional setup; other names must
        # be keys in the existing performance lock, never an invented enum.
        stack = props.get('performanceStack') or 'minimal'
        if stack != 'minimal':
            stacks = read_json(str(dc.ROOT / 'dev/performance-mods.lock.json'))['stacks']
            require(isinstance(stacks, dict) and stack in stacks,
                    'Unknown performanceStack: ' + stack)
        for key, expected in {'runDirectory': p['profile'], 'includeDh': str(p['include_dh']).lower(),
                              'comparisonFullscreen': str(p['fullscreen']).lower()}.items():
            require(props.get(key) == expected, key + ' disagrees with plan')
        if p['already_built']:
            require(all(any(argv[i:i+2] == ['-x', task] for i in range(len(argv)-1))
                        for task in dc.SKIPPABLE), 'already_built lacks launcher skip-task vector')
    check('launcher_command', command)

    def mods():
        runtime = read_json(str(dc.ROOT / 'dev/runtime.lock.json'))
        expected = [runtime['artifacts']['distanthorizons']['filename']] if p['include_dh'] else []
        directory = Path(p['profile']) / 'mods'
        require(directory.is_dir(), 'Profile mods directory missing')
        actual = sorted(f.name for f in directory.glob('*.jar'))
        require(actual == expected, 'Profile mods disagree with pinned DH/minimal runtime')
    check('profile_mods', mods)

    if 'scene_input' in p:
        def control():
            actual = read_json(str(Path(p['profile']).parent / 'control.json'))
            require(isinstance(p['scene_input'], dict), 'scene_input must be object')
            mismatched = sorted(k for k, v in p['scene_input'].items() if actual.get(k) != v)
            require(not mismatched, 'control.json differs from scene_input: ' + ', '.join(mismatched))
        check('control_scene', control)
    if 'expected_loaded_sha256' in p:
        check('archive_hash', lambda: require(hashlib.sha256(file(p['pack']).read_bytes()).hexdigest() ==
              p['expected_loaded_sha256'], 'Selected archive hash mismatch'))
    if 'expected_loaded_pack' in p:
        check('loaded_pack_expectation', lambda: require(p['expected_loaded_pack'] == p['pack_name'],
                                                       'Expected loaded pack differs from selected pack'))
    if 'expected_options_source' in p:
        check('expected_options', lambda: require(len(read_json(p['expected_options_source'])) ==
              p['expected_options_count'], 'Expected loaded option count differs from saved map'))
    if 'sourceReview' in p:
        check('source_review_pin', lambda: require(freeze is not None and
              hashlib.sha256(file(p['sourceReview']).read_bytes()).hexdigest() ==
              freeze['files'].get(p['sourceReview']), 'sourceReview missing from freeze.files or hash differs'))
    if 'protocol_capture_name' in p:
        def capture():
            name = p['protocol_capture_name']
            require(isinstance(name, str) and re.fullmatch(r'[a-zA-Z0-9_-]{1,64}', name), 'Invalid named capture')
            argv = p['command']
            destinations = [a.split('=', 1)[1] for a in argv if a.startswith('-PcontrolCaptureReceipt=')]
            originals = [a.split('=', 1)[1] for a in argv if a.startswith('-PcontrolCaptureOriginal=')]
            require(len(destinations) == len(originals) == 1, 'Capture destination/original command missing or duplicated')
            destination = Path(destinations[0])
            require(destination.is_absolute() and '..' not in destination.parts,
                    'Capture destination must be absolute without parent traversal')
            ancestor = destination
            while not ancestor.exists() and not ancestor.is_symlink():
                ancestor = ancestor.parent
            require(ancestor.is_dir(), 'Capture destination/ancestor is not a directory: ' + str(ancestor))
            require(os.access(ancestor, os.W_OK | os.X_OK),
                    'Capture destination/ancestor lacks write/traverse access: ' + str(ancestor))
            original = originals[0]
            require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+() '-]{0,191}\.zip", original), 'Invalid original archive basename')
            dc.inspect_pack(file(str(Path(p['profile']) / 'shaderpacks' / original)))
            if not historical:
                require(not (Path(destinations[0]) / (name + '.json')).exists(), 'Named receipt already exists')
        check('named_capture', capture)
    if check_built and p.get('already_built'):
        check('current_build_receipt', dc.validate_built)
    if receipt is not None:
        def capture_receipt():
            require(isinstance(receipt, dict), 'Capture receipt must be object')
            mismatched = []
            for key, expected in {'status': 'saved-observations', 'irisLoadedPack': p['pack_name'],
                'irisSelectedPack': p['pack_name'], 'selectedArchiveSha256': p['expected_loaded_sha256'],
                'loadedIrisOptions': read_json(p['expected_options_source']), 'irisFallback': False,
                'shaderPackInUse': True, 'fullscreen': True, 'focused': True,
                'framebufferWidth': 1920, 'framebufferHeight': 1200}.items():
                if receipt.get(key) != expected:
                    mismatched.append(key)
            require(not mismatched, 'ControlCaptureReceipt mismatches: ' + ', '.join(mismatched))
        check('recorded_capture_fields', capture_receipt)
    return {'scope': 'historical static inspection' if historical else 'static prelaunch consistency',
            'checked': checked, 'errors': errors,
            'unverified': ['live lane ownership/availability', 'actual JVM/argfile consumption', 'optional runtime artifact availability/hashes',
                           'driver compilation', 'linked uniforms/executed work', 'quality', 'performance']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('plan', type=Path, help='saved plan.json or owner.json containing plan')
    parser.add_argument('--freeze', type=Path, help='existing freeze.json; only selected sourceReview pin checked')
    parser.add_argument('--receipt', type=Path, help='existing ControlCaptureReceipt JSON; not a new runtime check')
    parser.add_argument('--historical', action='store_true', help='inspect historical deadline/receipt; never launch admission')
    parser.add_argument('--check-built', action='store_true', help='reuse dev_client.validate_built; hashes existing source/build only')
    args = parser.parse_args()
    try:
        record = json.loads(args.plan.read_text())
        freeze = json.loads(args.freeze.read_text()) if args.freeze else None
        receipt = json.loads(args.receipt.read_text()) if args.receipt else None
        result = check_plan(record, freeze=freeze, receipt=receipt,
                            historical=args.historical, check_built=args.check_built)
    except (ValueError, OSError) as error:
        result = {'errors': [{'check': 'input_json', 'error': str(error)}], 'checked': []}
    print(json.dumps(result, indent=2))
    return 1 if result['errors'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
