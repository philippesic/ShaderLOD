"""Read-only native terminal/process and canonical-profile verification."""
import argparse
import json
import os
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT/'scripts'))
import benchmark_iteration as iteration
import dev_client as dev
import experiment_lifecycle as life


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    life.require(os.name == 'nt', 'Use the actual native Windows allocation')
    life.require(not args.output.exists(), 'Fresh audit output required')
    profiles = [ROOT/'run-workloads/world-pool'/slot/'client' for slot in ('rtx5080-baseline', 'rtx5080-pbr')]
    rows, issues = [], []
    with iteration.stopped_profiles(*profiles):
        table = dev.process_table()
        life.require(not dev.clients(table), 'Client present; no terminal handoff')
        for report in sorted(HERE.iterdir()):
            path = report/'operation.json'
            if not report.is_dir() or not path.is_file():
                continue
            operation = json.loads(path.read_text())
            summary_path = report/'summary.json'
            summary = json.loads(summary_path.read_text()) if summary_path.exists() else {}
            controller_absent = True
            try:
                life.process_absent(operation.get('processes', {}).get('controller'), table, dev.identity)
            except (ValueError, RuntimeError):
                controller_absent = False
            profile_record = operation.get('iteration_profile', {})
            reset = profile_record.get('reset_verified') if profile_record else 'no_profile_transaction'
            row = dict(report=report.name, state=operation['state'],
                       release_verified=operation.get('release_verified', False),
                       configuration_restored=operation.get('configuration_restored', False),
                       original_summary_restored=summary.get('configuration_restored'),
                       reset_verified=reset, controller_absent=controller_absent,
                       validity=summary.get('evaluation', {}).get('validity'),
                       recovery_conflicts=operation.get('recovery_conflicts', []))
            valid = (row['state'] in ('complete', 'failed-restored') and row['release_verified']
                     and row['configuration_restored'] and controller_absent
                     and reset in (True, 'no_profile_transaction'))
            if not valid:
                issues.append(report.name)
            rows.append(row)
        canonical = [dict(profile=str(profile), **iteration.check_profile_inputs(profile)) for profile in profiles]
    result = dict(scope='Read-only final native audit; original failed summaries remain unchanged. Current operation/recovery flags and exact controller identity are checked, followed by full input bytes/modes/presence equality of both managed slots and their canonical sources.',
                  operations=rows, operation_count=len(rows), canonical_profiles=canonical,
                  clients_remaining=0, all_terminal_restored_released=not issues, issues=issues)
    life.atomic_json(args.output, result)
    print(json.dumps(dict(operations=len(rows), canonical_profiles=len(canonical), clients_remaining=0,
                         all_terminal_restored_released=not issues, issues=issues)))
    life.require(not issues, 'Unqualified terminal operations remain: '+', '.join(issues))


if __name__ == '__main__':
    main()
