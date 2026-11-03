#!/usr/bin/env python3
"""Offline independent requests and raw paired timing summaries; live Apply is in the mod UI."""
import argparse
import csv
import json
import math
import os
from pathlib import Path
import re
import tempfile

REPOSITORY = Path(__file__).resolve().parents[1]


def registry():
    source = (REPOSITORY / 'src/main/java/com/pp/shaderlod/optimization/OptimizationId.java').read_text()
    return {key: default == 'true' for key, default in re.findall(r'\w+\("([a-z_]+)", "[A-Za-z]+", (true|false)\)', source)}


def read_config(path, shader=None):
    values = registry()
    explicit = {}
    if path.exists():
        for line in path.read_text().splitlines():
            if not line or line.lstrip().startswith(('#', '!')):
                continue
            key, _, value = line.partition('=')
            explicit[key.strip()] = value.strip()
            if key.strip() in values:
                if value.strip() not in ('true', 'false'):
                    raise ValueError(f'Invalid flag {key}')
                values[key.strip()] = value.strip() == 'true'
    aliases = {'quad_index_fill': 'quad_command_compaction', 'quad_submission_copy': 'quad_command_compaction', 'command_metadata_cache': 'shadow_admission_cache'}
    for legacy, canonical in aliases.items():
        if canonical not in explicit and legacy in explicit:
            if explicit[legacy] not in ('true', 'false'):
                raise ValueError(f'Invalid legacy flag {legacy}')
            values[canonical] = explicit[legacy] == 'true'
            explicit[canonical] = explicit[legacy]
    if shader is not None:
        prefix = f'shader.{shader}.'
        for key in values:
            value = explicit.get(prefix + key)
            if value is None:
                value = next((explicit[prefix + alias] for alias, canonical in aliases.items()
                              if canonical == key and prefix + alias in explicit), None)
            if value is not None:
                if value not in ('true', 'false'):
                    raise ValueError(f'Invalid flag {prefix + key}')
                values[key] = value == 'true'
    return values


def write_config(path, values, shader=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    prefix = f'shader.{shader}.' if shader is not None else ''
    # Keep every other family's profile and unrelated property/comment verbatim.
    previous = path.read_text().splitlines(keepends=True) if path.exists() else []
    owned = {prefix + key for key in values}
    kept = [line for line in previous if line.partition('=')[0].strip() not in owned]
    if path.exists():
        backup = path.with_name(path.name + '.bak')
        index = 1
        while backup.exists():
            backup = path.with_name(path.name + f'.bak.{index}')
            index += 1
        backup.write_bytes(path.read_bytes())
    fd, name = tempfile.mkstemp(prefix='shaderlod-flags-', suffix='.tmp', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as output:
            output.writelines(kept)
            if kept and not kept[-1].endswith('\n'):
                output.write('\n')
            if not previous:
                output.write('# ShaderLOD independent requests\n')
            for key, value in values.items():
                output.write(f'{prefix}{key}={str(value).lower()}\n')
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def summarize(path, metadata=None):
    with path.open(newline='') as stream:
        rows = list(csv.DictReader(stream))
    metrics = ['frame_wall_ns', 'render_wall_ns', 'render_cpu_ns', 'process_cpu_ns', 'gpu_ns']
    phases = {}
    for phase in ['OFF', 'ON', 'RETURN']:
        selected = [row for row in rows if row['phase'] == phase]
        result = {'raw_samples': len(selected)}
        for metric in metrics:
            values = [int(row[metric]) for row in selected if row[metric]]
            if any(value < 0 for value in values):
                raise ValueError(f'Invalid negative clock {metric}')
            result[metric] = {'valid_samples': len(values), 'mean_ms': sum(values) / len(values) / 1e6 if values else None}
        wall = result['frame_wall_ns']['mean_ms']
        result['fps'] = 1000 / wall if wall else None
        phases[phase] = result
    result = {'phases': phases, 'complete_bracket': False, 'metrics': {}}
    # CSV presence alone cannot establish that the exporter completed its bracket.
    metadata = metadata if metadata is not None else path.with_name('metadata.properties')
    issues = []
    properties = {}
    if not metadata.is_file():
        issues.append('Exporter metadata unavailable; completion unverified')
    else:
        # Only simple exporter-owned scalar keys are consumed here; encoded pin
        # maps remain in the original file and are not interpreted as properties.
        for line in metadata.read_text().splitlines():
            key, separator, value = line.partition('=')
            if separator and not key.lstrip().startswith(('#', '!')):
                properties[key.strip()] = value.strip()
        if properties.get('phase') != 'COMPLETE':
            issues.append('Exporter phase is not COMPLETE')
        if any(row['phase'] not in phases for row in rows):
            issues.append('Unexpected CSV phase')
        for phase, summary in phases.items():
            if summary['raw_samples'] < 2:
                issues.append(f'{phase}: insufficient retained samples')
            try:
                if int(properties['samples_' + phase]) != summary['raw_samples']:
                    issues.append(f'{phase}: raw/metadata sample count mismatch')
                for metric in metrics:
                    stem = metric.removesuffix('_ns') + '_' + phase.lower()
                    measured = summary[metric]
                    count = float(properties[stem + '_valid_samples'])
                    if not math.isfinite(count) or count != measured['valid_samples']:
                        issues.append(f'{phase}/{metric}: valid denominator mismatch')
                    exported = properties.get(stem + '_ms')
                    if measured['valid_samples']:
                        if exported is None or not math.isclose(float(exported), measured['mean_ms'], rel_tol=1e-10, abs_tol=1e-10):
                            issues.append(f'{phase}/{metric}: raw/metadata mean mismatch')
                    elif exported is not None:
                        issues.append(f'{phase}/{metric}: unavailable metric has a mean')
            except (KeyError, ValueError, OverflowError):
                issues.append(f'{phase}: missing or malformed exporter counts/means')
        for row in rows:
            status = row.get('gpu_status', '')
            if row.get('gpu_ns'):
                bits = re.search(r'counterBits=([0-9]+)', status)
                if int(row['gpu_ns']) <= 0 or not status.startswith('valid elapsed query; ') or 'GL_TIME_ELAPSED' not in status or not bits or int(bits[1]) <= 0:
                    issues.append('GPU value lacks valid native elapsed-query provenance')
                    break
            elif not status or status.startswith('valid elapsed query;'):
                issues.append('Unavailable GPU value lacks an explicit unavailable status')
                break
        if any(not row.get('frame_wall_ns') or int(row['frame_wall_ns']) <= 0 for row in rows):
            issues.append('Missing or nonpositive frame interval')
        result['feature'] = properties.get('feature')
        result['exporter_phase'] = properties.get('phase')
    # Exports before baseline-oriented brackets always returned OFF.
    return_state = properties.get('return_state', 'OFF')
    if return_state not in ('OFF', 'ON'):
        issues.append('Invalid exporter return_state')
    if properties.get('baseline_state', return_state) != return_state:
        issues.append('Exporter baseline/return states differ')
    result['return_state'] = return_state
    for metric in metrics:
        off, on, returned = [phases[phase][metric]['mean_ms'] for phase in ['OFF', 'ON', 'RETURN']]
        baseline = phases[return_state][metric]['mean_ms'] if return_state in ('OFF', 'ON') else None
        result['metrics'][metric] = {'on_minus_off_ms': on - off if off is not None and on is not None else None,
                                    'baseline_drift_ms': returned - baseline if baseline is not None and returned is not None else None}
    result['complete_bracket'] = not issues
    result['validation_issues'] = list(dict.fromkeys(issues))
    result['validation_scope'] = 'Exporter completion, raw clocks/counts/means and GPU provenance; source pins, workload stability and causal performance are separate evidence'
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    for name in ['list', 'set', 'all']:
        command = sub.add_parser(name)
        command.add_argument('--config', type=Path, required=True)
        command.add_argument('--shader', choices=['photon', 'complementary-reimagined',
            'complementary-unbound', 'bsl-shaders', 'makeup-ultra-fast-shaders',
            'sildurs-vibrant-shaders', 'solas-shader', 'bliss-shader', 'astralex'],
            help='Edit one shader profile; omitted edits the legacy defaults only')
        if name == 'set':
            command.add_argument('--feature', choices=list(registry()), required=True)
        if name != 'list':
            command.add_argument('--enabled', choices=['true', 'false'], required=True)
    command = sub.add_parser('summarize')
    command.add_argument('--samples', type=Path, required=True)
    command.add_argument('--metadata', type=Path, help='Exporter metadata; defaults to metadata.properties beside samples.csv')
    args = parser.parse_args()
    if args.action == 'summarize':
        result = summarize(args.samples, args.metadata)
    else:
        values = read_config(args.config, args.shader)
        if args.action == 'set':
            values[args.feature] = args.enabled == 'true'
        if args.action == 'all':
            values = {key: args.enabled == 'true' for key in values}
        if args.action != 'list':
            write_config(args.config, values, args.shader)
        result = {'shader_profile': args.shader or 'legacy defaults', 'requested_flags': values,
                  'loads': 'next client start; use the live optimization editor for immediate Apply/reload'}
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
