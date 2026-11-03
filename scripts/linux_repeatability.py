#!/usr/bin/env python3
"""Finite fixed-scene baseline repeatability test of an already-owned live client.

Correctness is a required human gate; this script does not certify screenshots.
No compilation, settings search, feature toggles or shader reloads occur here.
"""
import argparse
import datetime
import json
import math
from pathlib import Path
import statistics
import time
import uuid


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n')


class Control:
    def __init__(self, path):
        self.path = path.resolve()
        self.sequence = uuid.uuid4().hex[:8]
        self.count = 0

    def request(self, action, **values):
        self.count += 1
        identifier = f'repeat-{self.sequence}-{self.count}'
        receipt = self.path.parent / 'receipts' / (identifier + '.json')
        temporary = self.path.with_suffix('.request.tmp')
        write(temporary, dict(request_id=identifier, action=action, **values))
        temporary.replace(self.path)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if receipt.exists():
                # The Java caller publishes receipts with a direct file write.
                # Existence can precede the complete JSON payload.
                try:
                    result = json.loads(receipt.read_text())
                except json.JSONDecodeError:
                    time.sleep(.05)
                    continue
                if 'success' in result:
                    if not result['success']:
                        raise RuntimeError(result.get('error', str(result)))
                    return result
            time.sleep(.05)
        raise TimeoutError('No terminal receipt: ' + identifier)


def stable(state):
    keys = ('main_mod', 'pack', 'width', 'height', 'distance', 'camera',
            'day_time', 'rain_level', 'selected_resource_packs', 'loaded_stack',
            'effective_options', 'fps_limit', 'vsync', 'fullscreen', 'client_pid',
            'client_started_at', 'game_directory', 'pack_identity', 'pipeline_identity')
    result = {key: state[key] for key in keys}
    installed = state.get('installed', {})
    result['installed'] = {key: installed.get(key) for key in ('master', 'flags', 'generation', 'reloads')}
    return result


def require_ready(state):
    if not (state['world_loaded'] and state['focused'] and state['screen'] == 'none'
            and state['window_visible'] and not state['window_iconified'] and not state['fallback']):
        raise ValueError('Focused unpaused shader scene required')
    if state['vsync'] or state['fps_limit'] != 260:
        raise ValueError('VSync off and cap260 required')
    if state.get('installed', {}).get('master', False):
        raise ValueError('Baseline repeatability requires production absent or masterOff')


def summarize(rows, threshold):
    if len(rows) < 4:
        raise ValueError('At least four complete windows required')
    early = statistics.median(row['statistics']['medianMs'] for row in rows[:2])
    late = statistics.median(row['statistics']['medianMs'] for row in rows[-2:])
    drift = (late / early - 1) * 100
    spread = (max(row['statistics']['medianMs'] for row in rows) /
              min(row['statistics']['medianMs'] for row in rows) - 1) * 100
    # At260, a3.846ms cap would bind and invalidate workload qualification.
    nonbinding = all(row['statistics']['medianMs'] > 1000 / 260 * 1.1 and
                    row.get('count_throughput_fps', 0) < 260 * .9 for row in rows)
    return dict(early_median_ms=early, late_median_ms=late, drift_percent=drift,
                median_spread_percent=spread, cap_nonbinding=nonbinding,
                timing_pass=abs(drift) <= threshold and spread <= threshold and nonbinding,
                threshold_percent=threshold,
                limits='Sampled main-present/masterOff or absent median repeatability only; cap clearance by median and throughput; no thermal, tail, GPU-busy or speedup inference')


def warmup_stable(rows, threshold=3):
    if len(rows) < 3:
        return False
    values = [row['statistics']['medianMs'] for row in rows[-3:]]
    return (max(values) / min(values) - 1) * 100 <= threshold


def protocol_valid(rows, starts, elapsed, minutes):
    expected = math.ceil(minutes / 2)
    return (len(rows) == expected and len(starts) == expected and
            minutes * 60 <= elapsed <= minutes * 60 + 30 and
            all(row['valid'] and 19 <= row['wall_ns'] / 1e9 <= 23 for row in rows) and
            all(abs(value - index * 120) <= 3 for index, value in enumerate(starts)))


def cleanup(control, capturing, initial, out):
    errors, after = [], None
    if capturing:
        try:
            control.request('window_stop')
        except BaseException as failure:
            errors.append('window_stop: ' + str(failure))
            try:
                control.request('cancel')
            except BaseException as failure:
                errors.append('cancel: ' + str(failure))
    try:
        control.request('video', fps_limit=initial['fps_limit'], vsync=initial['vsync'])
    except BaseException as failure:
        errors.append('video: ' + str(failure))
    try:
        after = control.request('status')['state']
        write(out / 'after.json', after)
        if stable(after) != stable(initial):
            errors.append('Restored workload/presentation differs from initial state')
    except BaseException as failure:
        errors.append('status/verification: ' + str(failure))
    result = dict(verified=not errors, errors=errors)
    write(out / 'cleanup.json', result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--control', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--correctness-reviewed', action='store_true', required=True)
    parser.add_argument('--minutes', type=int, default=12, choices=range(8, 16))
    parser.add_argument('--warmup', type=int, default=60)
    parser.add_argument('--warmup-max', type=int, default=600)
    parser.add_argument('--threshold', type=float, default=5)
    args = parser.parse_args()
    if not 0 <= args.warmup < args.warmup_max <= 900 or not 0 < args.threshold <= 20:
        parser.error('Require0<=warmup<warmup-max<=900 and0<threshold<=20')
    args.out.mkdir(parents=True, exist_ok=False)
    control = Control(args.control)
    initial = control.request('status')['state']
    write(args.out / 'before.json', initial)
    rows, resources, starts, warm_rows = [], [], [], []
    start = time.monotonic()
    wall = datetime.datetime.now(datetime.timezone.utc).isoformat()
    capturing = False
    failure_text, summary, protocol_ok = None, None, False
    measured_start = None
    try:
        control.request('video', fps_limit=260, vsync=False)
        state = control.request('status')['state']
        require_ready(state)
        workload = stable(state)
        write(args.out / 'protocol.json', dict(started_at=wall, warmup_seconds=args.warmup,
              warmup_max_seconds=args.warmup_max, warmup_gate='three consecutive20s controls,60s cadence,median spread<=3%',
              minutes=args.minutes, threshold_percent=args.threshold, workload=workload,
              acceptance='All valid windows; absolute early/late median drift and full median spread <=threshold; nonbinding cap; correctness reviewed separately'))
        next_window = start + args.warmup
        end = start + args.warmup_max + args.minutes * 60
        number = 0
        while time.monotonic() < end:
            resources.append(dict(elapsed=time.monotonic()-start,
                meminfo=Path('/proc/meminfo').read_text(),
                cpu=Path('/proc/stat').read_text().splitlines()[0],
                memory_psi=Path('/proc/pressure/memory').read_text(),
                cpu_psi=Path('/proc/pressure/cpu').read_text()))
            if time.monotonic() >= next_window:
                state = control.request('status')['state']
                require_ready(state)
                if stable(state) != workload:
                    raise ValueError('Workload changed before window')
                warming = measured_start is None
                if warming and time.monotonic() - start + 20 > args.warmup_max:
                    raise ValueError('Finite warmup timeout; reject comparison')
                label = 'repeat-' + control.sequence + ('-warm-' + str(len(warm_rows)+1) if warming else '-' + str(number+1))
                sample_started = time.monotonic()
                control.request('window_start', label=label, recording=True,
                                arm='MASTER_OFF' if state['main_mod'] else 'ABSENT', comparison='linux-baseline-repeatability')
                capturing = True
                time.sleep(20)
                result = control.request('window_stop')
                capturing = False
                row = result['window']
                write(args.out / (label + '.json'), result)
                if not row['valid']:
                    raise ValueError('Invalid window: ' + str(row.get('failure')))
                require_ready(result['state'])
                if stable(result['state']) != workload:
                    raise ValueError('Workload changed after window')
                if warming:
                    warm_rows.append(row)
                    print(f'Warmup {len(warm_rows)}: median{row["statistics"]["medianMs"]:.3f}ms', flush=True)
                    if time.monotonic() - start > args.warmup_max:
                        raise ValueError('Warmup exceeded finite timeout; reject comparison')
                    if warmup_stable(warm_rows):
                        measured_start = time.monotonic()
                        end = measured_start + args.minutes * 60
                        next_window = measured_start
                    elif time.monotonic() - start + 60 > args.warmup_max:
                        raise ValueError('No stable warmup before finite timeout; reject comparison')
                    else:
                        next_window += 60
                else:
                    number += 1
                    starts.append(sample_started-measured_start)
                    rows.append(row)
                    print(f'Window {number}: {row["count_throughput_fps"]:.2f}FPS median{row["statistics"]["medianMs"]:.3f}ms', flush=True)
                    next_window += 120
            time.sleep(1)
        protocol_ok = measured_start is not None and protocol_valid(rows, starts, time.monotonic()-measured_start, args.minutes)
        summary = summarize(rows, args.threshold)
    except BaseException as failure:
        failure_text = str(failure)
        write(args.out / 'failure.json', dict(error=str(failure), completed_windows=len(rows)))
    finally:
        restoration = cleanup(control, capturing, initial, args.out)
        write(args.out / 'resources.json', resources)
        accepted = not failure_text and protocol_ok and bool(summary and summary['timing_pass']) and restoration['verified']
        write(args.out / 'result.json', dict(completed=not failure_text, accepted=accepted,
              protocol_valid=protocol_ok, restoration=restoration, failure=failure_text,
              elapsed_seconds=time.monotonic()-start, sample_starts=starts,
              warmup_windows=warm_rows, windows=rows, summary=summary))
    if failure_text or not restoration['verified']:
        raise RuntimeError(failure_text or 'Restoration failed; retain exact client ownership')


if __name__ == '__main__':
    main()
