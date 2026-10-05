#!/usr/bin/env python3
"""Compare independent JVM repeats, pairing conditions within each order block."""
from __future__ import annotations
import argparse
import itertools
import json
from pathlib import Path
import statistics


METRICS = ('frame_ms', 'gpu_mean_ms', 'gpu_median_ms', 'gpu_p95_ms', 'cpu_wall_ms', 'cpu_thread_ms', 'cpu_wall_p95_ms')
MATCH_FIELDS = ('framebuffer_width', 'framebuffer_height', 'requested_chunks', 'dimension', 'day_time', 'rain', 'thunder', 'x', 'y', 'z', 'yaw', 'pitch')


def run_metrics(summary):
    def field(stream, scope, name):
        return (summary.get(stream, {}).get(scope, {}).get('per_sampled_frame') or {}).get(name)
    return {'frame_ms': 1000 / summary['render_fps'],
            'gpu_mean_ms': field('gpu', 'game-render', 'mean_ms'),
            'gpu_median_ms': field('gpu', 'game-render', 'median_ms'),
            'gpu_p95_ms': field('gpu', 'game-render', 'p95_ms'),
            'cpu_wall_ms': field('cpu_wall', 'run-tick', 'mean_ms'),
            'cpu_thread_ms': field('cpu_thread', 'run-tick', 'mean_ms'),
            'cpu_wall_p95_ms': field('cpu_wall', 'run-tick', 'p95_ms')}


def spread(values):
    if not values: return None
    median = statistics.median(values)
    return {'independent_jvm_runs': len(values), 'median': median, 'min': min(values), 'max': max(values),
            'median_absolute_deviation': statistics.median(abs(value - median) for value in values)}


def quality_options(text):
    return {key.strip(): value.strip() for line in text.splitlines()
            if '=' in line and not line.lstrip().startswith(('#', '!'))
            for key, value in [line.split('=', 1)] if not key.strip().startswith('SHADERLOD_')}


def check_pair(baseline, candidate, baseline_report, candidate_report, manifest):
    """Reject unmatched cameras/stacks, describe intentionally different quality controls."""
    problems = []
    for field in MATCH_FIELDS:
        if baseline_report['input_state_end'].get(field) != candidate_report['input_state_end'].get(field):
            problems.append('Unmatched actual input: ' + field)
    if baseline_report['dhPresent'] != candidate_report['dhPresent']: problems.append('Unmatched DH presence')
    if baseline_report['sampling_policy'] != candidate_report['sampling_policy']: problems.append('Unmatched timing cadence')
    mods = lambda report: {key: value for key, value in report['loaded_mods'].items() if key != 'shaderlod'}
    if mods(baseline_report) != mods(candidate_report): problems.append('Unmatched loaded runtime versions')
    inputs = manifest['source_inputs']
    if inputs[baseline['case']]['source_world_sha256'] != inputs[candidate['case']]['source_world_sha256']:
        problems.append('Unmatched source save')
    if problems: raise ValueError('; '.join(problems))
    changes = {}
    a = {key: value for key, value in baseline_report['effective_shader_options'].items() if not key.startswith('SHADERLOD_')}
    b = {key: value for key, value in candidate_report['effective_shader_options'].items() if not key.startswith('SHADERLOD_')}
    differences = {key: [a.get(key), b.get(key)] for key in sorted(a.keys() | b.keys()) if a.get(key) != b.get(key)}
    # Adaptive/disabled must hold original upstream quality constant. Explicit controls may differ.
    protected_candidate = candidate['case'].endswith(('adaptive', 'patched-off', 'final', 'alloff', '-only'))
    protected_original_against_isolation = candidate['case'].endswith('-high') and baseline['case'].endswith(('alloff', '-only'))
    if differences and (protected_candidate or protected_original_against_isolation):
        raise ValueError('Adaptive/disabled changed upstream quality settings: ' + repr(differences))
    if differences: changes['upstream_shader_options'] = differences
    flags_a = {key: value for key, value in baseline_report['effective_shader_options'].items() if key.startswith('SHADERLOD_')}
    flags_b = {key: value for key, value in candidate_report['effective_shader_options'].items() if key.startswith('SHADERLOD_')}
    flag_changes = {key: [flags_a.get(key), flags_b.get(key)] for key in sorted(flags_a.keys() | flags_b.keys()) if flags_a.get(key) != flags_b.get(key)}
    if flag_changes: changes['shaderlod_pack_options'] = flag_changes
    for field in ('effective_chunks', 'terrain_active'):
        a, b = baseline_report['input_state_end'].get(field), candidate_report['input_state_end'].get(field)
        if a != b: changes[field] = [a, b]
    if baseline_report.get('actual_dh_configuration') != candidate_report.get('actual_dh_configuration'):
        changes['dh_configuration_differs'] = True
    return changes


def paired_effects(pairs):
    """Percent time reduction: positive is faster. Repeats, not frames, are units."""
    result = {}
    for metric in METRICS:
        reductions = [100 * (base[metric] - candidate[metric]) / base[metric]
                      for base, candidate in pairs if base.get(metric) is not None and candidate.get(metric) is not None and base[metric] > 0]
        if reductions:
            result[metric] = {'percent_time_reduction': spread(reductions), 'per_block_percent': reductions,
                              'faster_blocks': sum(value > 0 for value in reductions)}
    return result


def analyze(path, baseline_case=None):
    manifest = json.loads(path.read_text())
    if manifest.get('schema') != 2 or manifest.get('status') != 'complete': raise ValueError('Only complete schema-2 matrices can be compared')
    runs = manifest['runs']
    if any(run.get('status') != 'complete' for run in runs): raise ValueError('Incomplete/error runs in matrix')
    root = path.parent
    summaries, reports, metrics = {}, {}, {}
    seen = set()
    for run in runs:
        key = (run['block'], run['case'], run['stack'])
        if key in seen: raise ValueError('Duplicate repeat condition')
        seen.add(key)
        summaries[key] = json.loads((root / run['summary']).read_text())
        reports[key] = json.loads((root / run['measurement']).read_text())
        if reports[key].get('request', {}).get('run_id') != run['request']['run_id']: raise ValueError('Report/manifest UUID mismatch')
        metrics[key] = run_metrics(summaries[key])
    conditions = sorted({(run['case'], run['stack']) for run in runs})
    aggregate = []
    for case, stack in conditions:
        values = [value for (block, condition, runtime), value in metrics.items() if condition == case and runtime == stack]
        if len(values) != manifest['repeats']: raise ValueError('Missing repeats for condition')
        aggregate.append({'case': case, 'stack': stack,
                          'metrics': {metric: spread([value[metric] for value in values if value[metric] is not None]) for metric in METRICS}})
    comparisons = []
    for candidate_case, stack in conditions:
        base_case = baseline_case or candidate_case.split('-', 1)[0] + '-high'
        if base_case == candidate_case or (base_case, stack) not in conditions: continue
        pairs, changes = [], []
        for block in range(1, manifest['repeats'] + 1):
            base_key, candidate_key = (block, base_case, stack), (block, candidate_case, stack)
            base_run = next(run for run in runs if (run['block'], run['case'], run['stack']) == base_key)
            candidate_run = next(run for run in runs if (run['block'], run['case'], run['stack']) == candidate_key)
            changes.append(check_pair(base_run, candidate_run, reports[base_key], reports[candidate_key], manifest))
            pairs.append((metrics[base_key], metrics[candidate_key]))
        comparisons.append({'baseline': base_case, 'candidate': candidate_case, 'stack': stack,
                            'intentional_input_changes': changes, 'effects': paired_effects(pairs)})
    stack_comparisons = []
    for case in sorted({case for case, stack in conditions}):
        if (case, 'minimal') in conditions and (case, 'optimized') in conditions:
            pairs = [(metrics[(block, case, 'minimal')], metrics[(block, case, 'optimized')]) for block in range(1, manifest['repeats'] + 1)]
            stack_comparisons.append({'case': case, 'baseline_stack': 'minimal', 'candidate_stack': 'optimized', 'effects': paired_effects(pairs)})
    return {'schema': 1, 'manifest': str(path.resolve()), 'conditions': aggregate, 'paired_comparisons': comparisons,
            'stack_comparisons': stack_comparisons,
            'uncertainty': 'Median, range and MAD describe independent JVM runs. Paired blocks reduce order drift but are not simultaneous controls. No significance claim or IID confidence interval: frame samples are autocorrelated; 3-5 repeats remain exploratory. Per-run p95 is a tail metric, not uncertainty on the effect.',
            'limits': 'FPS-derived frame duration, actual GPU intervals, CPU wall and thread clocks remain separate. Different quality/detail controls are annotated. Geometry gain is not a novel shader gain. Failed/anomalous runs are retained and must be explicitly rerun, not silently removed.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('--baseline')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = analyze(args.manifest, args.baseline)
    contents = json.dumps(result, indent=2) + '\n'
    if args.output: args.output.write_text(contents)
    else: print(contents, end='')


if __name__ == '__main__': main()
