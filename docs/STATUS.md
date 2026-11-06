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
Native Windows preflight observes RTX5080/driver617.42, an active3840×2160 console
and idle GPU; previous GPU work is released. A fresh C: check observes63.2GiB free,
resolving the storage blocker. The32GiB reserve remains; the earlier80–100GiB
suggestion was conservative headroom, not a fixed minimum. Cleanup remains limited
to ShaderLOD. Native canonical profiles, two managed slots and an isolated toolchain
are prepared and native compilation passes. Original MakeUp startup succeeds on
the actual RTX5080 at3840×2160/GL3.2, restores inputs and leaves zero clients.
Explicit GL4.6 startup also passes with restoration. MakeUp and Reimagined each have one valid conditioned original-only baseline
process with three20s windows and restoration. An Asahi-only G03 source gate was
identified on NVIDIA; a finite source/harness repair is undergoing live functional
validation, with linked rewritten CUTOUT submissions now observed. No candidate
performance conclusion is available yet. See the campaign
[working state](../dev/experiments/rtx5080-four-target-audit-20261106/STATE.md).

RTX2080Ti remains outside this allocation; renew availability before using it.
The active source branch is `rtx5080-audit`; milestone pushes go to user-owned origin.

## Find context

- [Benchmark guide](BENCHMARK.md) and [candidate reference](BENCHMARK_CANDIDATES.md): maintained routes.
- [Experiment history](EXPERIMENTS.md): previous attempts, evidence and reasons to revisit.
- [Latest audit detail](STATUS_AUDIT_20261105.md) and [earlier campaign snapshot](STATUS_HISTORY_20261105.md): historical results/decisions.

At milestones update objective, latest meaningful result/limits, owner/run evidence
if active, and next action here. Link detailed records; do not copy every progress
update or old ownership statement into current state.
