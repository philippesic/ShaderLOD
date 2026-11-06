# Current state

Updated 2026-11-05. Current scope remains the cross-shader/cross-machine audit of
G01–G10, C01–C02 and N01–N04. V1 targets are Complementary Reimagined, Complementary
Unbound, BSL and MakeUp Ultra Fast; shared renderer/LOD mechanisms remain the goal.
The [retained direction and audit](STATUS_AUDIT_20261105.md) preserves the exact
screening/compatibility requirements. This cleanup changes no experimental scope.

## Latest outcome and next action

The Asahi pass is complete: 64 cells classified, 36 timed. G01 and exact G06 had
repeatable local MakeUp gains; broad cross-pack/vendor gains and production
qualification remain unproven. See [conclusions](../dev/experiments/asahi-four-target-audit-20261104/audit-conclusions.json),
[results](../dev/experiments/asahi-four-target-audit-20261104/results.json) and
[terminal restoration](../dev/experiments/asahi-four-target-audit-20261104/terminal-audit.json).

Next research step is second-GPU validation of viable candidates under the existing
plan. RTX 2080 Ti availability must be renewed; the last record assigns it to another
task. RTX 5080 was also unavailable. These are dated allocations, not a live census.
No benchmark is launched or resumed by documentation cleanup.

## Find context

- [Benchmark guide](BENCHMARK.md) and [candidate reference](BENCHMARK_CANDIDATES.md): maintained routes.
- [Experiment history](EXPERIMENTS.md): previous attempts, evidence and reasons to revisit.
- [Latest audit detail](STATUS_AUDIT_20261105.md) and [earlier campaign snapshot](STATUS_HISTORY_20261105.md): historical results/decisions.

At milestones update objective, latest meaningful result/limits, owner/run evidence
if active, and next action here. Link detailed records; do not copy every progress
update or old ownership statement into current state.
