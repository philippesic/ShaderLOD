# Current state

Updated 2026-11-06. Current scope remains the cross-shader/cross-machine audit of
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

**Active user request:** comprehensive RTX5080 rerun of all16 candidates across
the same four targets, with hardware/GL repairs, work/correctness proof,
investigation of unexpected regressions, repeated timings and variance charts.
See the [campaign plan](../dev/experiments/rtx5080-four-target-audit-20261106/PLAN.md).
Native Windows runs on RTX5080/driver617.42 at3840×2160. Capacity is sufficient;
cleanup remains ShaderLOD-only. Two valid120s-conditioned, three20s-window
original-only native baseline processes are complete for every target. BSL also
has separate optimized-stack and GL4.6 baselines. Real native3.2 and explicit4.3,
4.5 and4.6 original startup checks pass with restoration. Baseline frame/window/
process variances are retained separately in the campaign's compact progress data.

MakeUp G03 passes actual linked rewritten CUTOUT functional admission. BSL source
ownership and startup-race repairs pass its first balanced comparison with verified
submissions/restoration; apparent reduction−0.21% at n1 is unresolved against
variation. No optimization-benefit conclusion is available yet. The completed
11-operation baseline batch is restored/released and its prior source/classes
are verified/preserved before replacement. Canonical source now has a finite C02
extension for the separate Complementary bloom outputs, plus balanced order and
bounded motion support; local tests pass, native activation remains pending.
The first G01 score was interrupted by Windows ownership-file sharing and an
untracked generated FML config backup; it is invalid, released and fully restored
with backup bytes retained. Both hosts pass the maintained harness repairs. A
fresh port-validation queue is active before the opposite-order comparison blocks. See the
campaign [working state](../dev/experiments/rtx5080-four-target-audit-20261106/STATE.md),
[BSL proof](../dev/experiments/rtx5080-four-target-audit-20261106/G03_PORT.md) and
[C02 port scope](../dev/experiments/rtx5080-four-target-audit-20261106/C02_PORT.md).

RTX2080Ti remains outside this allocation; renew availability before using it.
The active source branch is `rtx5080-audit`; milestone pushes go to user-owned origin.

## Find context

- [Benchmark guide](BENCHMARK.md) and [candidate reference](BENCHMARK_CANDIDATES.md): maintained routes.
- [Experiment history](EXPERIMENTS.md): previous attempts, evidence and reasons to revisit.
- [Latest audit detail](STATUS_AUDIT_20261105.md) and [earlier campaign snapshot](STATUS_HISTORY_20261105.md): historical results/decisions.

At milestones update objective, latest meaningful result/limits, owner/run evidence
if active, and next action here. Link detailed records; do not copy every progress
update or old ownership statement into current state.
