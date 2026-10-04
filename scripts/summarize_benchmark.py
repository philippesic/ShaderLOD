#!/usr/bin/env python3
"""Summarize completed serial GPU query samples; never derives GPU time from FPS."""
import argparse
import collections
import json
import math
import statistics
from pathlib import Path


def summarize(values):
    ordered = sorted(values)
    return {'samples': len(ordered), 'mean_ms': statistics.mean(ordered),
            'median_ms': statistics.median(ordered),
            'p95_ms': ordered[math.ceil(len(ordered) * .95) - 1]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = json.loads(args.report.read_text())
    if report['status'] != 'complete' or len(report['phases']) != 4:
        raise SystemExit('Benchmark is incomplete or unsupported')
    pooled = {False: collections.defaultdict(list), True: collections.defaultdict(list)}
    phases = []
    for phase in report['phases']:
        scopes = collections.defaultdict(list)
        for sample in phase['samples']:
            value = sample['milliseconds']
            if not math.isfinite(value) or value < 0: raise SystemExit('Invalid GPU query sample')
            scopes[sample['scope']].append(value)
            pooled[phase['enabled']][sample['scope']].append(value)
        if not scopes['game-render']: raise SystemExit('Missing GPU game-render samples')
        phases.append({'enabled': phase['enabled'], 'scopes': {key: summarize(values) for key, values in scopes.items()}})
    result = {'method': report['method'], 'backend': report['backend'], 'preset': report['preset'],
              'phases': phases, 'pooled': {}, 'mean_reduction_percent': {},
              'limits': 'Single scene/host. Elapsed frame/pass samples alternate; do not sum scopes. Query overhead and clock drift remain.'}
    for enabled in [False, True]:
        result['pooled']['on' if enabled else 'off'] = {key: summarize(values) for key, values in pooled[enabled].items()}
    for scope in pooled[False].keys() & pooled[True].keys():
        baseline = statistics.mean(pooled[False][scope]); lod = statistics.mean(pooled[True][scope])
        if baseline > 0: result['mean_reduction_percent'][scope] = 100 * (baseline - lod) / baseline
    text = json.dumps(result, indent=2) + '\n'
    if args.output: args.output.write_text(text)
    else: print(text, end='')


if __name__ == '__main__':
    main()
