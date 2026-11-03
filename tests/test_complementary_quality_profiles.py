"""Small real-archive/tiny-save preparation and synthetic evidence contract fixtures."""
import contextlib
import copy
import hashlib
import io
import json
from pathlib import Path
import shutil
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch
import zlib

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import prepare_complementary_quality_profiles as quality
from setup_comparison import WORLD, file_hash, world_fingerprint


class ComplementaryQualityTest(unittest.TestCase):
    def test_request_starts_at_exact_parent_not_old_yaw78(self):
        request = quality.camera_request(dict(quality.FIXED))
        self.assertEqual(request['waypoints'][0], dict(x=500.5, y=68.0, z=660.5,
                                                     yaw=87.38, pitch=3.735, seconds=0))
        self.assertEqual([(p['seconds'], p['yaw'], p['pitch']) for p in request['waypoints']],
                         [(0, 87.38, 3.735), (7, 96.0, 5.0), (14, 78.0, 2.2), (20, 87.38, 3.735)])
        self.assertEqual(request['fps'] * request['waypoints'][-1]['seconds'] + 1, 41)
        self.assertEqual({k: request[k] for k in ('pngCompressionLevel', 'maximumInFlightImages',
                         'expectedCameraFluid', 'rejectFullBlockCamera')},
                         dict(pngCompressionLevel=0, maximumInFlightImages=1,
                              expectedCameraFluid='NONE', rejectFullBlockCamera=True))

    def test_wrong_pose_weather_time_types_and_label_rejected(self):
        for changes in ({'yaw': 78.0}, {'pitch': 2.2}, {'time': 7000}, {'weather': 'rain'},
                        {'x': float('nan')}, {'y': True}, {'debug': 0}, {'capture': 'old'}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                quality.camera_request(quality.FIXED | changes)
        with self.assertRaises(ValueError):
            quality.camera_request(dict(quality.FIXED), '../unsafe')

    @contextlib.contextmanager
    def fixture(self):
        lock = json.loads((ROOT / 'dependencies/shader-candidates.lock.json').read_text())
        pin = next(c['release'] for c in lock['candidates'] if c['modrinth_project'] == 'complementary-reimagined')
        release = ROOT / 'dependencies/upstream/other-pack-research' / pin['filename']
        photon = ROOT / 'dependencies/upstream/photon_v1.3b.zip'
        if not release.is_file() or not photon.is_file():
            self.skipTest('Pinned ignored Complementary/Photon archives unavailable; no downloads by tests')
        self.assertEqual(file_hash(release), quality.COMPLEMENTARY_RELEASE_SHA256)
        self.assertEqual(file_hash(photon), quality.SOURCE_PHOTON_SHA256)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive_dir = root / 'dependencies/upstream/other-pack-research'
            archive_dir.mkdir(parents=True)
            shutil.copy2(release, archive_dir / pin['filename'])
            (root / 'dependencies/shader-candidates.lock.json').write_text(json.dumps({'candidates': [
                {'modrinth_project': 'complementary-reimagined', 'release': pin}]}))
            shutil.copy2(ROOT / 'dependencies/runtime.lock.json', root / 'dependencies/runtime.lock.json')
            source = root / 'source/coastalwater/high'
            for folder in ('mods', 'config', 'shaderpacks', f'saves/{WORLD}/region'):
                (source / folder).mkdir(parents=True)
            shutil.copy2(photon, source / 'shaderpacks/photon_v1.3b.zip')
            (source / 'config/iris.properties').write_text('enableShaders=true\nshaderPack=photon_v1.3b.zip\n')
            (source / 'options.txt').write_text('renderDistance:24\nenableVsync:false\nfov:0.0\n')
            world = source / 'saves' / WORLD
            (world / 'level.dat').write_bytes(b'tiny save identity fixture, not a runnable world')
            (world / 'region/r.0.0.mca').write_bytes(b'unchanged region fixture')
            (world / 'session.lock').write_bytes(b'fixture advisory lock')
            (source.parent / 'control.json').write_text(json.dumps(quality.FIXED, indent=2) + '\n')
            (source.parent / 'workload-manifest.json').write_text(json.dumps({'scene': 'coastalwater'}))
            with patch.object(quality.pack_preparation, 'ROOT', root), contextlib.redirect_stdout(io.StringIO()):
                yield source, root / 'prepared', pin

    def test_real_archive_preparation_preserves_source_and_three_native_controls(self):
        with self.fixture() as (source, output, pin):
            before = world_fingerprint(source / 'saves' / WORLD)
            control_hash = file_hash(source.parent / 'control.json')
            result = quality.prepare(source, output)
            request = json.loads((output / 'camera-request.json').read_text())
            self.assertEqual(request['waypoints'][0]['yaw'], 87.38)
            self.assertEqual(result['requested_slots'], 41)
            self.assertEqual(result['source_world_sha256'], before)
            for arm, option in quality.ARMS.items():
                profile = output / 'coastalwater' / arm
                self.assertEqual(file_hash(profile / 'shaderpacks' / pin['filename']), quality.COMPLEMENTARY_RELEASE_SHA256)
                self.assertEqual(world_fingerprint(profile / 'saves' / WORLD), before)
                self.assertFalse((profile / 'saves' / WORLD / 'session.lock').exists())
                self.assertEqual(quality.read_option_properties((profile / 'shaderpacks' / (pin['filename'] + '.txt')).read_text()),
                                 {'WATER_REFLECT_QUALITY': option})
                self.assertEqual((profile / 'options.txt').read_bytes(), (source / 'options.txt').read_bytes())
            self.assertEqual(world_fingerprint(source / 'saves' / WORLD), before)
            self.assertEqual(file_hash(source.parent / 'control.json'), control_hash)
            with self.assertRaisesRegex(ValueError, 'Preserving existing'):
                quality.prepare(source, output)

    def test_bad_control_pack_history_or_layout_rejected_before_copy(self):
        for failure in ('control', 'pack', 'history', 'jar', 'radius'):
            with self.subTest(failure=failure), self.fixture() as (source, output, pin):
                if failure == 'control':
                    (source.parent / 'control.json').write_text(json.dumps(quality.FIXED | {'yaw': 78}))
                elif failure == 'pack':
                    (source / 'shaderpacks/photon_v1.3b.zip').write_bytes(b'wrong container')
                elif failure == 'history':
                    (source / 'camera-quality').mkdir()
                elif failure == 'jar':
                    (source / 'mods/DistantHorizons.jar').write_bytes(b'unmanaged source jar')
                else:
                    (source / 'options.txt').write_text('renderDistance:16\n')
                with self.assertRaises(ValueError):
                    quality.prepare(source, output)
                self.assertFalse(output.exists())

    def test_locked_source_delegates_rejection_without_preparing_output(self):
        with self.fixture() as (source, output, pin):
            with patch.object(quality.pack_preparation, 'unlocked_world', side_effect=ValueError('World is open')):
                with self.assertRaisesRegex(ValueError, 'World is open'):
                    quality.prepare(source, output)
            self.assertFalse(output.exists())

    def synthetic_capture(self, output, arm):
        """Synthetic contract/header bytes only; no PNG encoder/world/client runs."""
        prep = json.loads((output / 'complementary-quality-preparation.json').read_text())
        directory = output / 'coastalwater' / arm / 'camera-quality/fixture-sequence'
        directory.mkdir(parents=True)
        header = b'IHDR' + struct.pack('>IIBBBBB', 2976, 1672, 8, 6, 0, 0, 0)
        png = b'\x89PNG\r\n\x1a\n' + struct.pack('>I', 13) + header + struct.pack('>I', zlib.crc32(header))
        state = dict(framebuffer_width=2976, framebuffer_height=1672, requested_chunks=24,
                     effective_chunks=24, day_time=12500, dimension='minecraft:overworld',
                     mod_present=False, shaders_in_use=True, fallback=False, hide_gui=True,
                     actual_shader_sha256=quality.COMPLEMENTARY_RELEASE_SHA256,
                     effective_shader_options=quality.NATIVE | {'WATER_REFLECT_QUALITY': quality.ARMS[arm]})
        state['effective_shader_options_sha256'] = hashlib.sha256(json.dumps(state['effective_shader_options'], sort_keys=True).encode()).hexdigest()
        guard = dict(level_has_chunk_at=True, client_chunk_cache_has_chunk=True, in_dimension_build_range=True)
        frames = []
        for slot in range(41):
            seconds = slot / 2
            motion_tick = int(seconds * 20) + 1
            angle_seconds = min(20, motion_tick / 20)
            if angle_seconds <= 7:
                t, a, b = angle_seconds / 7, (87.38, 3.735), (96, 5)
            elif angle_seconds <= 14:
                t, a, b = (angle_seconds - 7) / 7, (96, 5), (78, 2.2)
            else:
                t, a, b = (angle_seconds - 14) / 6, (78, 2.2), (87.38, 3.735)
            weight = t * t * (3 - 2 * t)
            yaw, pitch = tuple(x + (y - x) * weight for x, y in zip(a, b))
            name = f'frame-{slot:04d}.png'
            (directory / name).write_bytes(png)
            camera = dict(fog_type='NONE', full_collision_block=False, initialized=True,
                          configured_first_person=True, near_plane_positions_finite=True,
                          position_cell={'loaded_guard': guard}, fog_required_position_guards=[guard],
                          position=dict(x=500.5, y=69.62, z=660.5))
            frames.append(dict(slot=slot, status='written', callback_slot_id=slot, image_write_slot_id=slot,
                               framebuffer_width=2976, framebuffer_height=1672,
                               shader_archive_sha256=quality.COMPLEMENTARY_RELEASE_SHA256,
                               effective_shader_options_sha256=state['effective_shader_options_sha256'],
                               png_compression_level=0, maximum_in_flight_images=1, file=name,
                               png_sha256=file_hash(directory / name), bytes=len(png), callback_completed_at_utc='fixture',
                               camera_context={'camera': camera}, observed_path_seconds=seconds,
                               motion_tick=motion_tick, tick_delta=0,
                               camera_x=500.5, camera_y=69.62, camera_z=660.5, camera_yaw=yaw, camera_pitch=pitch,
                               player_pose=dict(x=500.5, y=68.0, z=660.5, yaw=yaw, pitch=pitch), session_render_frame=slot * 30 + 1,
                               source_iris_frame_counter=slot * 30 + 1, source_iris_frame_time_counter=seconds))
        report = dict(status='complete', requested_capture_slots=41, png_callback_successes=41,
                      skipped_capture_slots=0, writer_pending_at_finalization=False, request_unchanged_at_end=True,
                      maximum_in_flight_images=1, request_sha256=prep['camera_request_sha256'],
                      loaded_mod_artifacts={'fixture': {'sha256': 'fixture'}},
                      loaded_mod_artifacts_at_end={'fixture': {'sha256': 'fixture'}},
                      loaded_mod_versions={'shaderlod_dev': 'fixture', 'iris': 'fixture', 'sodium': 'fixture'},
                      capture_write_tracker=dict(pendingCount=0, imageSlots=0, awaitingCallbacks=0, maximum=1,
                                                 peakPendingCount=1, peakImageSlots=1, peakWritingImages=1,
                                                 peakExecutorQueueSize=0, settledCallbacks=41),
                      input_state_initial=state, input_state_capture_start=state, input_state_capture_end=state, frames=frames)
        path = directory / 'manifest.json'
        path.write_text(json.dumps(report))
        return path, report

    def test_validator_checks_complete_native_sequence_and_rejects_contract_drift(self):
        with self.fixture() as (source, output, pin):
            quality.prepare(source, output)
            path, original = self.synthetic_capture(output, 'water-potato')
            quality.validate_capture(output, 'water-potato', path)
            changes = {
                'gap': lambda m: m.update(skipped_capture_slots=1),
                'radius': lambda m: m['input_state_capture_start'].update(effective_chunks=16),
                'shader': lambda m: m['frames'][0].update(shader_archive_sha256='wrong'),
                'fluid': lambda m: m['frames'][0]['camera_context']['camera'].update(fog_type='WATER'),
                'callback': lambda m: m['frames'][2].update(callback_slot_id=1),
                'pose': lambda m: m['frames'][0].update(camera_yaw=78),
                'clock': lambda m: m['frames'][20].update(tick_delta=.5),
                'motiontick': lambda m: m['frames'][20].update(motion_tick=201.5),
                'playerangle': lambda m: m['frames'][20]['player_pose'].update(yaw=78),
                'resolution': lambda m: m['frames'][0].update(framebuffer_width=1488),
                'mainmod': lambda m: m['loaded_mod_versions'].update(shaderlod='unexpected'),
                'pending': lambda m: m['capture_write_tracker'].update(pendingCount=1),
                'loaded': lambda m: m['frames'][0]['camera_context']['camera']['position_cell']['loaded_guard'].update(client_chunk_cache_has_chunk=False),
            }
            for reason, mutate in changes.items():
                altered = copy.deepcopy(original)
                mutate(altered)
                path.write_text(json.dumps(altered))
                with self.subTest(reason=reason), self.assertRaises(ValueError):
                    quality.validate_capture(output, 'water-potato', path)
            path.write_text(json.dumps(original))
            (path.parent / 'frame-0000.png').write_bytes(b'corrupt retained fixture')
            with self.assertRaisesRegex(ValueError, 'hash/size'):
                quality.validate_capture(output, 'water-potato', path)

    def test_local_view_uses_current_tick_while_position_clock_uses_partial(self):
        with self.fixture() as (source, output, pin):
            quality.prepare(source, output)
            path, report = self.synthetic_capture(output, 'high')
            frame = report['frames'][20]
            self.assertEqual(frame['observed_path_seconds'], 10)
            self.assertEqual(frame['motion_tick'], 201)
            self.assertEqual(frame['tick_delta'], 0)
            self.assertAlmostEqual(frame['camera_yaw'], 88.7261516035, places=5)
            quality.validate_capture(output, 'high', path)
            # The earlier interpolated-angle model is wrong for LocalPlayer.
            frame['camera_yaw'] = 88.915451895
            path.write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError, 'Rendered camera angle drift'):
                quality.validate_capture(output, 'high', path)


if __name__ == '__main__':
    unittest.main()
