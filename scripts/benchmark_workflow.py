"""Read-only discovery and interpretation for the maintained benchmark runner."""
from __future__ import annotations
import argparse
import json
import platform
from pathlib import Path


def discover(root):
    slots = []
    for marker in sorted((root / 'run-workloads').glob('world-pool/*/workspace.json')):
        try:
            data = json.loads(marker.read_text())
            if not isinstance(data,dict):raise ValueError('Workspace marker must be a JSON object')
            if not isinstance(data.get('source'),str) or not data['source']:raise ValueError('Workspace marker has no canonical source')
            source=Path(data['source'])
            profile = marker.parent / 'client'
            iris = profile / 'config/iris.properties'
            slots.append({'slot': marker.parent.name, 'profile': str(profile),
                          'source': data.get('source'), 'canonical_exists':source.is_dir(), 'exists': profile.is_dir(),
                          'availability':'PROFILE_MISSING' if not profile.is_dir() else 'CANONICAL_SOURCE_MISSING' if not source.is_dir() else 'PREFLIGHT_REQUIRED',
                          'worlds': sorted(p.name for p in (profile / 'saves').glob('*') if p.is_dir()),
                          'selected_pack': next((line.split('=', 1)[1] for line in iris.read_text().splitlines()
                                                if line.startswith('shaderPack=')), None) if iris.exists() else None,
                          'shaderpacks': sorted(p.name for p in (profile / 'shaderpacks').glob('*.zip')),
                          'inputs': [{'pack':str(p),'options':str(p)+'.txt' if Path(str(p)+'.txt').is_file() else None}
                                     for p in sorted((profile/'shaderpacks').glob('*.zip'))]})
        except (OSError, ValueError) as error:
            slots.append({'slot': marker.parent.name, 'error': str(error)})
    return {'slots': slots, 'launcher': 'python3 scripts/run_benchmark.py',
            'platform': platform.system() + '/' + platform.machine(),
            'scope': 'Inventory only; input compatibility and slot ownership require plan/preflight.'}


def outcome(summary):
    evaluation = summary.get('evaluation') or {}
    if not isinstance(evaluation,dict):raise ValueError('Evaluation must be a JSON object')
    if summary.get('finished') is False or (not evaluation and not summary.get('finished')):
        return {'decision':'RUNNING_OR_INTERRUPTED','next_action':'Inspect operation ownership and current process before recovery or retry.'}
    if not summary.get('configuration_restored') or summary.get('release_error') or summary.get('clients_remaining'):
        decision, action = 'RECOVERY_REQUIRED', 'Inspect operation.json and use recover --output on this report; preserve conflicting files.'
    elif evaluation.get('validity') == 'UNTESTED' and summary.get('execution_completed') and not summary.get('error'):
        decision, action = 'NOT_EVALUATED', 'Execution completed; this diagnostic did not evaluate performance.'
    elif summary.get('error') or evaluation.get('validity') != 'VALID':
        decision, action = 'INVALID', 'Correct the reported failure before a fresh run; do not interpret timing as performance evidence.'
    elif evaluation.get('drift_comparison'):
        decision = evaluation['drift_comparison']
        action = 'Development comparison only; inspect execution witnesses and images before deciding on a repeat.'
    else:
        decision, action = evaluation.get('improvement', 'NOT_EVALUATED'), 'Inspect the protocol-specific evaluation and its scope.'
    costs = summary.get('cost_seconds', {})
    phases = {k: v for k, v in costs.items() if k != 'total' and isinstance(v, (int, float))}
    measurement = sum(v for k,v in phases.items() if k.endswith('_measurement'))
    return {'decision': decision, 'execution': evaluation.get('execution'),
            'restored': summary.get('configuration_restored', False),
            'total_seconds': costs.get('total'),
            'command_total_seconds': summary.get('command_total_seconds'),
            'build_seconds': summary.get('workflow_build_seconds'),
            'measurement_phase_seconds': measurement,
            'other_phase_seconds': max(0,costs['total']-measurement) if isinstance(costs.get('total'),(int,float)) else None,
            'largest_phase': max(phases, key=phases.get) if phases else None,
            'error': summary.get('error') or summary.get('release_error') or evaluation.get('error'),
            'next_action': action, 'limits': evaluation.get('limits'),
            'closure_review': 'Required only when closing an optimization under the requested review workflow.'}


def command(argv, runner):
    action, *rest = argv
    if action == 'discover':
        p = argparse.ArgumentParser(); p.add_argument('--slot')
        a = p.parse_args(rest)
        result = discover(runner.ROOT)
        if a.slot:
            result['slots'] = [s for s in result['slots'] if s['slot'] == a.slot]
            if not result['slots']:p.error('unknown managed slot')
        parser = runner.argument_parser()
        result['choices'] = {a.dest:list(a.choices) for a in parser._actions if a.dest in ('candidate','scene','untimed_diagnostic') and a.choices}
        result['private_lifecycle_candidates'] = {name:{'module':spec[0].__name__,'settled_observation':spec[2]} for name,spec in runner.PRIVATE_CANDIDATES.items()}
        print(json.dumps(result, indent=2)); return 0
    if action == 'report':
        p = argparse.ArgumentParser(); p.add_argument('--output', required=True, type=Path)
        a = p.parse_args(rest)
        try:
            data = json.loads((a.output / 'summary.json').read_text())
            if not isinstance(data,dict):raise ValueError('Summary must be a JSON object')
            print(json.dumps(outcome(data), indent=2)); return 0
        except (OSError, ValueError) as error:
            print(json.dumps({'error': str(error)})); return 1
    return None


def remedy(check):
    return {
        'runtime_available': 'Inspect the active client owner; do not reset its profile or terminate unrelated work.',
        'managed_profile_inputs': 'Inspect the differing paths; preserve valuable edits before an explicit managed checkout/reset.',
        'existing_pool_slot': 'Use discover to select an existing managed slot.',
        'existing_build_binding': 'test rebuilds stale source/output receipts automatically; plan never builds.',
        'compiled_control_hooks': 'test rebuilds missing compiled hooks automatically.',
        'runtime_artifact_pins': 'Prepare the pinned runtime dependencies before launch.',
        'destination_capacity': 'Choose a fresh report on the profile filesystem and preserve the storage reserve.',
        'standard_public_identity': 'Select the exact original archive/options supported by the candidate; inspect the candidate reference.',
        'bounded_inputs': 'Correct the input paths and bounded protocol arguments; use a fresh output directory.',
    }.get(check, 'Correct this preflight error before launching; no client was started.')
