#!/usr/bin/env python3
"""Fresh pinned Complementary native motion controls; prepare or validate, never launch."""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re
import struct
import zlib

import prepare_pack_experiment as pack_preparation
from run_proof_matrix import COMPLEMENTARY_RELEASE_SHA256, read_option_properties
from setup_comparison import ROOT, WORLD, file_hash

DEFAULT_SOURCE = ROOT / 'run-workloads/photon-ssr-cost-controls-r1/coastalwater/high'
SOURCE_PHOTON_SHA256 = '120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead'
ARMS = {'high': '2', 'water-sky': '0', 'water-potato': '1'}
FIXED = dict(x=500.5, y=68.0, z=660.5, yaw=87.38, pitch=3.735,
             time=12500, weather='clear', preset='PERFORMANCE', debug=False)
NATIVE = dict(SHADER_STYLE='1', RP_MODE='1', BLOCK_REFLECT_QUALITY='3',
              DETAIL_QUALITY='2', CLOUD_QUALITY='2', shadowDistance='192.0',
              COLORED_LIGHTING='0', WORLD_SPACE_REFLECTIONS='-1')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def fixed_control(control):
    for key, expected in FIXED.items():
        actual = control.get(key)
        if isinstance(expected, (int, float)) and not isinstance(expected, bool):
            require(isinstance(actual, (int, float)) and not isinstance(actual, bool)
                    and math.isfinite(actual), 'Fixed control requires finite numeric ' + key)
        require(type(actual) is bool if isinstance(expected, bool) else True,
                'Fixed control boolean type mismatch: ' + key)
        require(actual == expected, 'Source is not the inspected fixed coastal control: ' + key)
    require('capture' not in control, 'Remove an old manual capture instruction in a separate source profile first')
    return {key: control[key] for key in FIXED}


def camera_request(control, label='complementary-native-water-motion-r1'):
    fixed_control(control)
    require(isinstance(label, str) and re.fullmatch(r'[a-zA-Z0-9_-]{1,64}', label), 'Invalid immutable request label')
    pose = {key: control[key] for key in ('x', 'y', 'z', 'yaw', 'pitch')}
    return dict(schema=1, label=label, warmupSeconds=30, fps=2, pngCompressionLevel=0,
                maximumInFlightImages=1, expectedCameraFluid='NONE', rejectFullBlockCamera=True,
                waypoints=[dict(pose, seconds=0), dict(pose, seconds=7, yaw=96.0, pitch=5.0),
                           dict(pose, seconds=14, yaw=78.0, pitch=2.2), dict(pose, seconds=20)])


def prepare(source, output, label='complementary-native-water-motion-r1'):
    raw_output = Path(output)
    require(not raw_output.exists() and not raw_output.is_symlink(), 'Preserving existing output; choose a fresh path')
    source, output = Path(source).resolve(), raw_output.resolve()
    require(source.parent.name == 'coastalwater', 'Source must be the inspected coastalwater profile')
    require((source / 'saves' / WORLD / 'level.dat').is_file(), 'Source save level.dat is missing')
    require(source != output and source not in output.parents and output not in source.parents, 'Choose a fresh separate output')
    control_path = source.parent / 'control.json'
    source_control_hash = file_hash(control_path)
    source_control = json.loads(control_path.read_text())
    request = camera_request(source_control, label)
    selected = read_option_properties((source / 'config/iris.properties').read_text())
    require(selected.get('enableShaders') == 'true', 'Source shaders must be enabled')
    name = selected.get('shaderPack', '')
    require(Path(name).name == name and name not in ('', '.', '..'), 'Invalid source pack name')
    require(file_hash(source / 'shaderpacks' / name) == SOURCE_PHOTON_SHA256, 'Source must be the pinned original Photon profile')
    require(not list((source / 'mods').glob('*.jar')), 'Source must have physical noDH/minimal profile jar layout')
    require(not (source / 'camera-quality').exists(), 'Choose a clean source without old camera capture history')
    options_hash = file_hash(source / 'options.txt')
    options = dict(line.split(':', 1) for line in (source / 'options.txt').read_text().splitlines() if ':' in line)
    require(options.get('renderDistance') == '24', 'Source must retain requested 24 detailed chunks')
    # The existing family implementation pins every original archive, holds the
    # stopped source-world lock, checks equal save copies and removes physical DH.
    pack_preparation.prepare(source, output, 'complementary')
    scene = output / 'coastalwater'
    copied_control = json.loads((scene / 'control.json').read_text())
    fixed_control(copied_control)
    require({k: copied_control[k] for k in ('x', 'y', 'z', 'yaw', 'pitch')}
            == {k: request['waypoints'][0][k] for k in ('x', 'y', 'z', 'yaw', 'pitch')},
            'Copied control differs from the request first pose')
    require(file_hash(control_path) == source_control_hash, 'Source control changed during preparation')
    manifest = json.loads((scene / 'workload-manifest.json').read_text())
    require(set(manifest['experiment_arms']) == set(ARMS) and manifest['dh_installed'] is False, 'Unexpected prepared family arms/DH')
    for arm, value in ARMS.items():
        record = manifest['experiment_arms'][arm]
        require(record['sha256'] == COMPLEMENTARY_RELEASE_SHA256
                and record['requested_options'] == {'WATER_REFLECT_QUALITY': value}, 'Prepared pack/native options mismatch: ' + arm)
        profile = scene / arm
        require(file_hash(profile / 'options.txt') == options_hash, 'Copied native game options changed: ' + arm)
        require(not list((profile / 'mods').glob('*.jar')), 'Prepared profile contains unmanaged jars: ' + arm)
        # Do not overwrite or discard cloned history. Choose a clean stopped
        # source instead if generic family preparation copied an old quality run.
        require(not (profile / 'camera-quality').exists(), 'Prepared profile inherited camera history; choose a clean source')
    request_path = output / 'camera-request.json'
    request_path.write_text(json.dumps(request, indent=2) + '\n')
    result = dict(schema=1, status='prepared', scene='coastalwater', source_profile=str(source),
                  source_control_sha256=source_control_hash, copied_control_sha256=file_hash(scene / 'control.json'),
                  source_minecraft_options_sha256=options_hash,
                  source_shader_sha256=SOURCE_PHOTON_SHA256, shader_sha256=COMPLEMENTARY_RELEASE_SHA256,
                  source_world_sha256=manifest['source_world_sha256'], camera_request=str(request_path),
                  camera_request_sha256=file_hash(request_path), request=request, control=copied_control,
                  arms=ARMS, required_native_options=NATIVE, expected_framebuffer=[2976, 1672],
                  requested_chunks=24, effective_chunks=24, requested_slots=41,
                  validation='Source/options/equal copied saves only; actual motion/context/quality unverified')
    (output / 'complementary-quality-preparation.json').write_text(json.dumps(result, indent=2) + '\n')
    print('Prepared native Complementary motion controls:', output)
    return result


def number(value, name):
    require(isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value), 'Non-finite numeric evidence: ' + name)
    return value


def angles(request, seconds):
    for start, end in zip(request['waypoints'], request['waypoints'][1:]):
        if seconds <= end['seconds']:
            t = min(1.0, max(0.0, (seconds - start['seconds']) / (end['seconds'] - start['seconds'])))
            t = t * t * (3 - 2 * t)
            return tuple(start[key] + (end[key] - start[key]) * t for key in ('yaw', 'pitch'))
    return tuple(request['waypoints'][-1][key] for key in ('yaw', 'pitch'))


def validate_capture(output, arm, manifest_path):
    """Readonly structural acceptance of one explicit sequence; never visual/performance acceptance."""
    output, manifest_path = Path(output).resolve(), Path(manifest_path).resolve()
    require(arm in ARMS, 'Unknown motion arm')
    prep = json.loads((output / 'complementary-quality-preparation.json').read_text())
    require(prep.get('status') == 'prepared', 'Preparation did not complete')
    request_path = output / 'camera-request.json'
    require(file_hash(request_path) == prep['camera_request_sha256'], 'Prepared request changed')
    request = json.loads(request_path.read_text())
    require(request == prep['request'], 'Request differs from preparation record')
    require(file_hash(output / 'coastalwater/control.json') == prep['copied_control_sha256'], 'Prepared control changed')
    profile = output / 'coastalwater' / arm
    require(manifest_path.name == 'manifest.json' and manifest_path.parent.parent == profile / 'camera-quality', 'Pass the explicit owned profile camera-quality/UUID/manifest.json')
    report = json.loads(manifest_path.read_text())
    for key, expected in dict(status='complete', requested_capture_slots=41, png_callback_successes=41,
                              skipped_capture_slots=0, writer_pending_at_finalization=False,
                              request_unchanged_at_end=True, maximum_in_flight_images=1).items():
        require(report.get(key) is expected if isinstance(expected, bool) else report.get(key) == expected,
                'Capture contract mismatch: ' + key)
    require(report.get('request_sha256') == prep['camera_request_sha256'], 'Capture request hash mismatch')
    require(bool(report.get('loaded_mod_artifacts')) and report.get('loaded_mod_artifacts') == report.get('loaded_mod_artifacts_at_end'), 'Loaded artifacts missing/changed')
    mods = report.get('loaded_mod_versions', {})
    require('shaderlod' not in mods and 'distanthorizons' not in mods, 'Main mod/DH must remain absent')
    require(all(key in mods for key in ('shaderlod_dev', 'iris', 'sodium')), 'Loaded dev/Iris/Sodium provenance missing')
    require(not any(key in mods for key in ('modernfix', 'ferritecore', 'immediatelyfast', 'entityculling')), 'Optional stack must remain absent')
    tracker = report['capture_write_tracker']
    require(tracker['pendingCount'] == 0 and tracker['imageSlots'] == 0 and tracker['awaitingCallbacks'] == 0
            and tracker['maximum'] == 1 and tracker['peakPendingCount'] <= 1
            and tracker['peakImageSlots'] <= 1 and tracker['peakWritingImages'] <= 1
            and tracker['peakExecutorQueueSize'] <= 1 and tracker['settledCallbacks'] == 41,
            'Image/callback drain/bound mismatch')
    for phase in ('input_state_initial', 'input_state_capture_start', 'input_state_capture_end'):
        state = report[phase]
        for key, expected in dict(framebuffer_width=2976, framebuffer_height=1672, requested_chunks=24,
                                  effective_chunks=24, day_time=12500, dimension='minecraft:overworld',
                                  mod_present=False, shaders_in_use=True, fallback=False, hide_gui=True,
                                  actual_shader_sha256=COMPLEMENTARY_RELEASE_SHA256).items():
            require(state.get(key) is expected if isinstance(expected, bool) else state.get(key) == expected,
                    phase + ' mismatch: ' + key)
        loaded = state['effective_shader_options']
        require(loaded == report['input_state_initial']['effective_shader_options'], 'Loaded shader options drift across boundaries')
        for key, expected in (NATIVE | {'WATER_REFLECT_QUALITY': ARMS[arm]}).items():
            require(loaded.get(key) == expected, phase + ' native option mismatch: ' + key)
    frames = report['frames']
    require(len(frames) == 41 and [f['slot'] for f in frames] == list(range(41)), 'Missing/duplicate/out-of-order slots')
    eye_y = None
    last_render_frame = -1
    for frame in frames:
        slot = frame['slot']
        require(frame.get('status') == 'written' and frame.get('callback_slot_id') == slot
                and frame.get('image_write_slot_id') == slot, 'Slot callback identity/status mismatch')
        require(frame.get('framebuffer_width') == 2976 and frame.get('framebuffer_height') == 1672, 'Non-native frame')
        require(frame.get('shader_archive_sha256') == COMPLEMENTARY_RELEASE_SHA256, 'Frame archive mismatch')
        require(frame.get('effective_shader_options_sha256') == report['input_state_capture_start']['effective_shader_options_sha256'], 'Frame shader option identity mismatch')
        require(frame.get('png_compression_level') == 0 and frame.get('maximum_in_flight_images') == 1, 'Frame encoder/lease mismatch')
        filename = frame.get('file', '')
        require(filename == f'frame-{slot:04d}.png', 'Unsafe/incorrect slot filename')
        image = manifest_path.parent / filename
        require(not image.is_symlink() and image.is_file(), 'PNG absent/linked')
        require(file_hash(image) == frame.get('png_sha256') and image.stat().st_size == frame.get('bytes'), 'PNG on-disk hash/size mismatch')
        with image.open('rb') as stream:
            header = stream.read(33)
        require(len(header) == 33 and header[:8] == b'\x89PNG\r\n\x1a\n'
                and header[8:16] == b'\x00\x00\x00\x0dIHDR', 'PNG signature/IHDR missing')
        require(struct.unpack('>IIBBBBB', header[16:29]) == (2976, 1672, 8, 6, 0, 0, 0)
                and zlib.crc32(header[12:29]) == int.from_bytes(header[29:33], 'big'), 'PNG actual header dimensions/format/CRC mismatch')
        require(bool(frame.get('callback_completed_at_utc')), 'Callback completion missing')
        camera = frame['camera_context']['camera']
        require(camera.get('fog_type') == 'NONE' and camera.get('full_collision_block') is False
                and camera.get('initialized') is True and camera.get('configured_first_person') is True,
                'Camera context not known NONE/clear/first-person')
        guard = camera['position_cell']['loaded_guard']
        require(all(guard.get(key) is True for key in ('level_has_chunk_at', 'client_chunk_cache_has_chunk', 'in_dimension_build_range')), 'Camera cell not loaded')
        require(camera.get('near_plane_positions_finite') is True and bool(camera.get('fog_required_position_guards')), 'Near-plane guards missing')
        for guard in camera['fog_required_position_guards']:
            require(all(guard.get(key) is True for key in ('level_has_chunk_at', 'client_chunk_cache_has_chunk', 'in_dimension_build_range'))
                    and 'state' not in guard, 'Fog required cell unknown/not loaded')
        for key, expected in dict(x=500.5, y=68.0, z=660.5).items():
            require(abs(number(frame['player_pose'][key], 'player ' + key) - expected) <= .0002, 'Player moved from fixed column')
        render_frame = number(frame['session_render_frame'], 'render frame')
        require(render_frame > last_render_frame, 'Source render frames not advancing')
        last_render_frame = render_frame
        number(frame['source_iris_frame_counter'], 'Iris frame')
        number(frame['source_iris_frame_time_counter'], 'Iris time')
        observed = number(frame['observed_path_seconds'], 'path time')
        require(abs(observed - slot / 2) <= .1, 'Capture slot path-time drift')
        motion_tick = number(frame['motion_tick'], 'motion tick')
        partial = number(frame['tick_delta'], 'tick delta')
        require(motion_tick == int(motion_tick) and motion_tick >= 0 and 0 <= partial <= 1,
                'Invalid motion tick/partial evidence')
        interpolated_seconds = 0 if motion_tick == 0 else min(20, (motion_tick - 1 + partial) / 20)
        require(abs(observed - interpolated_seconds) <= 1e-6, 'Recorded partial-tick path clock mismatch')
        # Pinned LocalPlayer.getViewXRot/YRot use current rotation when not
        # riding; Camera.setup only interpolates position/eye height here.
        yaw, pitch = angles(request, min(20, motion_tick / 20))
        require(abs((number(frame['camera_yaw'], 'yaw') - yaw + 180) % 360 - 180) <= .05
                and abs(number(frame['camera_pitch'], 'pitch') - pitch) <= .05, 'Rendered camera angle drift')
        require(abs((number(frame['player_pose']['yaw'], 'player yaw') - yaw + 180) % 360 - 180) <= .05
                and abs(number(frame['player_pose']['pitch'], 'player pitch') - pitch) <= .05,
                'Applied player angle differs from current tick request')
        require(abs(number(frame['camera_x'], 'x') - 500.5) <= .0002
                and abs(number(frame['camera_z'], 'z') - 660.5) <= .0002, 'Rendered camera translation drift')
        current_eye = number(frame['camera_y'], 'eye y')
        require(all(abs(number(camera['position'][key], 'context ' + key) - frame['camera_' + key]) <= 1e-8
                    for key in ('x', 'y', 'z')), 'Context and screenshot camera disagree')
        eye_y = current_eye if eye_y is None else eye_y
        require(abs(current_eye - eye_y) <= .0002 and abs(current_eye - 69.62) <= .01, 'Camera eye height drift')
    print('Validated structural native motion evidence:', arm, manifest_path, '(41 slots; visual/temporal quality remains unverified)')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    prepare_parser = commands.add_parser('prepare')
    prepare_parser.add_argument('--source', type=Path, default=DEFAULT_SOURCE)
    prepare_parser.add_argument('--output', type=Path, required=True)
    prepare_parser.add_argument('--label', default='complementary-native-water-motion-r1')
    check = commands.add_parser('validate')
    check.add_argument('--output', type=Path, required=True)
    check.add_argument('--arm', choices=tuple(ARMS), required=True)
    check.add_argument('--manifest', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == 'prepare':
            prepare(args.source, args.output, args.label)
        else:
            validate_capture(args.output, args.arm, args.manifest)
    except (ValueError, OSError, KeyError) as error:
        parser.exit(1, str(error) + '\n')


if __name__ == '__main__':
    main()
