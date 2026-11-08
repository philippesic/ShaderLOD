"""Inspect preserved untimed framebuffer sequences without extracting raw frames.

RGB8 screenshot differences include animation, temporal history and pose jitter.
They are bounded visual diagnostics, never HDR equivalence or timing evidence.
"""
import argparse
from collections import Counter
from contextlib import ExitStack
import hashlib
import io
import json
from pathlib import Path
import statistics
import zipfile

from PIL import Image, ImageChops, ImageDraw, ImageStat


def difference(left, right):
    delta = ImageChops.difference(left, right)
    stats = ImageStat.Stat(delta)
    maximum = ImageChops.lighter(ImageChops.lighter(*delta.split()[:2]), delta.split()[2])
    histogram = maximum.histogram()
    pixels = left.width * left.height
    cumulative = 0
    p99 = 255
    for value, count in enumerate(histogram):
        cumulative += count
        if cumulative >= .99 * pixels:
            p99 = value
            break
    return {
        'mean_absolute_rgb_u8': statistics.mean(stats.mean),
        'rms_rgb_u8': (statistics.mean(x*x for x in stats.rms))**.5,
        'p99_max_channel_u8': p99,
        'pixels_fraction_max_channel_above': {
            str(threshold): sum(histogram[threshold+1:])/pixels
            for threshold in (4, 8, 16, 32, 64)
        },
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archives', nargs='+', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assert not args.output.exists(), 'Fresh analysis output required'
    args.output.mkdir(parents=True)
    with ExitStack() as stack:
        preserved, owners = {}, {}
        for path in args.archives:
            archive = stack.enter_context(zipfile.ZipFile(path))
            for row in json.loads(archive.read('PRESERVATION-MANIFEST.json')):
                assert row['path'] not in preserved, 'Duplicate arm/file across archives'
                preserved[row['path']] = row
                owners[row['path']] = archive

        def read(name):
            data = owners[name].read(name)
            assert len(data) == preserved[name]['bytes']
            assert hashlib.sha256(data).hexdigest() == preserved[name]['sha256'], name
            return data

        manifests = {}
        frame_maps = {}
        for arm in ('native', 'candidate', 'return'):
            name = 'quality-' + arm + '/manifest.json'
            if name not in preserved:
                continue
            manifest = json.loads(read(name))
            manifests[arm] = {
                key: manifest.get(key) for key in (
                    'status', 'png_callback_successes', 'skipped_capture_slots',
                    'writer_pending_at_finalization', 'actual_motion_wall_seconds',
                    'actual_motion_ticks', 'gl_vendor', 'gl_renderer', 'gl_version',
                    'held_path_ticks', 'held_writer_ticks', 'writer_capacity_wait_render_frames',
                    'config', 'camera_contract', 'evidence_kind', 'clock_policy',
                )
            }
            manifests[arm]['frame_status_counts'] = dict(Counter(f.get('status') for f in manifest['frames']))
            manifests[arm]['skipped_slots'] = [
                {'slot': f['slot'], 'status': f['status']}
                for f in manifest['frames'] if f.get('status') != 'written'
            ]
            frame_maps[arm] = {f['slot']: f for f in manifest['frames'] if f.get('status') == 'written'}

        complete = len(manifests) == 3 and all(
            m['status'] == 'complete' and m['png_callback_successes'] == 121
            and m['skipped_capture_slots'] == 0 and m['writer_pending_at_finalization'] is False
            for m in manifests.values())
        common = sorted(set.intersection(*(set(frames) for frames in frame_maps.values())))
        rows = []
        sheet = Image.new('RGB', (640 * 3, 390 * 3), '#181818')
        draw = ImageDraw.Draw(sheet)
        for slot in common:
            images = {}
            row = {'slot': slot, 'poses': {}, 'differences': {}}
            for column, (arm, frames) in enumerate(frame_maps.items()):
                frame = frames[slot]
                name = 'quality-' + arm + '/' + Path(frame['file']).name
                image = Image.open(io.BytesIO(read(name))).convert('RGB')
                assert image.size == (3840, 2160), image.size
                images[arm] = image
                row['poses'][arm] = {key: frame.get(key) for key in (
                    'camera_x', 'camera_y', 'camera_z', 'camera_yaw', 'camera_pitch',
                    'observed_path_seconds', 'scheduled_path_seconds', 'motion_wall_seconds',
                    'source_iris_frame_time_counter', 'source_iris_frame_counter',
                )}
                if slot in (0, 60, 120):
                    top = (slot // 60) * 390
                    thumbnail = image.copy()
                    thumbnail.thumbnail((640, 360), Image.Resampling.LANCZOS)
                    sheet.paste(thumbnail, (column * 640, top + 25))
                    draw.text((column * 640 + 8, top + 7), f'{arm} / slot {slot}', fill='white')
            for left, right in (('candidate', 'native'), ('return', 'native'), ('candidate', 'return')):
                if left in images and right in images:
                    metrics = difference(images[left], images[right])
                    if slot in (0, 60, 120):
                        metrics['regions'] = {
                            label: difference(images[left].crop(box), images[right].crop(box))
                            for label, box in (
                                ('upper_half', (0, 0, 3840, 1080)),
                                ('lower_half', (0, 1080, 3840, 2160)),
                            )
                        }
                    row['differences'][left + '_vs_' + right] = metrics
            rows.append(row)
            if slot % 20 == 0:
                print('analyzed', slot, flush=True)
        pairs = sorted({pair for row in rows for pair in row['differences']})
        aggregate = {}
        for pair in pairs:
            values = [row['differences'][pair]['mean_absolute_rgb_u8'] for row in rows]
            aggregate[pair] = dict(slots=len(values), mean_absolute_rgb_u8=statistics.mean(values),
                                   median_absolute_rgb_u8=statistics.median(values), max_absolute_rgb_u8=max(values))
        report = dict(
            scope='Untimed final RGB8 framebuffer only. Native animation/history and capture stalls advance; slot pairing is not deterministic pixel equivalence. Inspect actual poses and native-return variation. No HDR, DH, PBR, all-scene or performance qualification.',
            archives=[str(path) for path in args.archives], captures_complete=complete, manifests=manifests,
            common_written_slots=len(common), aggregate=aggregate, frames=rows,
        )
        (args.output/'quality-analysis.json').write_text(json.dumps(report, indent=2)+'\n')
        sheet.save(args.output/'contact-sheet.jpg', quality=95)
        print(json.dumps(dict(captures_complete=complete, common_written_slots=len(common), aggregate=aggregate)))


if __name__ == '__main__':
    main()
