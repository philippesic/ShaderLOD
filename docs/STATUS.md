# Current state

Updated 2026-11-07. Current scope remains the cross-shader/cross-machine audit of
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

The main97-operation queue and all24 repaired comparison/control operations are
complete and restored. All eight preceding Runtime-V4 admission checks pass.
The verified timing reduction contains200 reports,175 valid and40 measured cells,
with two independent timing processes per measured primary cell. The other24 rows
are22 source-held/no-certified-route dispositions and two shared MakeUp G01 routes; this is not
complete64-cell qualification. Original-only baselines and matching-context null
controls retain within-window, between-window and between-process variance.

Provisional reductions include MakeUp G01 about3.24%, BSL G02 about3.95% and C02
bloom VRS about1.77–2.24% over its three qualified packs. These are descriptive
means from repeated comparisons, pending visual/correctness and regression review.
BSL G02's measured route is the public singleton dead_mip_chain request; it is
not the separate private native-derived candidate. Several adapter routes regress,
including roughly7–18% on some BSL/Complementary cases; no cause or fix is yet
established. Repaired MakeUp G03 and all four N01 CPU routes show no clear gain.
The interactive provisional charts and standalone PNG/SVG are generated locally.

Untimed quality/profile acquisition continues through maintained check commands.
MakeUp G01 has all363 native/candidate/return frames. BSL mip and bloom captures
are incomplete and remain invalid/restored; missed render slots need diagnosis and
fresh retries. A renderer/writer-bounded logical camera clock now passes native
regression tests and starts five fresh checks, retaining121-frame completeness.
Both fresh BSL checks now pass363/363 captures/restoration; the other three continue.
Two further modern-context G03 comparisons are chained after quality archival.
The earlier batch stopped safely at capacity. Full raw PNG bytes are retained in independently verified archives.
Preserving terminal ShaderLOD recovery/evidence copies off-host reclaimed another
12.59GiB before captures. No NVIDIA cache, other project or personal profile was
touched. See the campaign [working state](../dev/experiments/rtx5080-four-target-audit-20261106/STATE.md)
and [source review](../dev/experiments/rtx5080-four-target-audit-20261106/SOURCE_REVIEW.md).
A reliable ETA for the complete quality/regression/applicability audit is not yet
established; acquisition completion is not the final deliverable.

RTX2080Ti remains outside this allocation; renew availability before using it.
The active source branch is `rtx5080-audit`; milestone pushes go to user-owned origin.

## Find context

- [Benchmark guide](BENCHMARK.md) and [candidate reference](BENCHMARK_CANDIDATES.md): maintained routes.
- [Experiment history](EXPERIMENTS.md): previous attempts, evidence and reasons to revisit.
- [Latest audit detail](STATUS_AUDIT_20261105.md) and [earlier campaign snapshot](STATUS_HISTORY_20261105.md): historical results/decisions.

At milestones update objective, latest meaningful result/limits, owner/run evidence
if active, and next action here. Link detailed records; do not copy every progress
update or old ownership statement into current state.
