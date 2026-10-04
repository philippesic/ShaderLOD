#!/usr/bin/env python3
"""Keep CPU and GPU metrics separate, grouping repeated invocations by frame."""
import argparse
import collections
import json
import math
import statistics
from pathlib import Path


def stats(values):
    if not values: return None
    ordered = sorted(values)
    return {'samples': len(values), 'mean_ms': statistics.mean(values), 'median_ms': statistics.median(values),
            'p95_ms': ordered[math.ceil(len(values) * .95) - 1],
            'p99_ms': ordered[math.ceil(len(values) * .99) - 1],
            'zero_fraction': sum(v == 0 for v in values) / len(values)}


def stream(samples, field):
    invocations = collections.defaultdict(list)
    frames = collections.defaultdict(lambda: collections.defaultdict(float))
    for sample in samples:
        value = sample.get(field)
        if value is None: continue
        if not math.isfinite(value) or value < 0: raise ValueError('Invalid timing sample')
        invocations[sample['scope']].append(value)
        frames[sample['scope']][sample['frameId']] += value
    return {key: {'invocations': stats(values), 'per_sampled_frame': stats(list(frames[key].values()))}
            for key, values in invocations.items()}


def summarize(path):
    report = json.loads(path.read_text())
    if report['status'] != 'complete': raise ValueError('Incomplete workload capture')
    return {'profile': report['profile'], 'mod_present': report['modPresent'],
            'request': report.get('request'), 'loaded_mods': report.get('loaded_mods'),
            'input_state': report.get('input_state_end'),
            'effective_shader_options_sha256': report.get('effective_shader_options_sha256'),
            'render_fps': report['render_frames'] / report['elapsed_seconds'],
            'elapsed_seconds': report['elapsed_seconds'], 'sampling_policy': report['sampling_policy'],
            'backend': report['backend'], 'gpu': stream(report['gpu_samples'], 'milliseconds'),
            'cpu_wall': stream(report['cpu_samples'], 'wallMilliseconds'),
            'cpu_thread': stream(report['cpu_samples'], 'threadMilliseconds'),
            'source': str(path.resolve()),
            'limits': 'GPU timeline not busy time; coarse tile attribution. Parent/child scopes overlap; rotated categories use different frames. Do not sum all scopes. Frame samples are autocorrelated; their count is not an independent replicate count.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = summarize(args.report)
    contents = json.dumps(result, indent=2) + '\n'
    if args.output: args.output.write_text(contents)
    else: print(contents, end='')


if __name__ == '__main__': main()
