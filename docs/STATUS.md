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
variation. MakeUp G01 now completes its first valid balanced process with actual copy removal,
a promising lower mean at n1; independent repeats, controls and quality remain
pending. No repeated optimization-benefit conclusion is available yet. The completed
11-operation baseline batch is restored/released and its prior source/classes
are verified/preserved before replacement. Canonical source now has a finite C02
extension for the separate Complementary bloom outputs, plus balanced order and
bounded motion support; functional activation/restoration now passes for all three bloom targets on real
4.5/4.6. Performance and quality remain pending.
The first G01 score was interrupted by Windows ownership-file sharing and an
untracked generated FML config backup; it is invalid, released and fully restored
with backup bytes retained. Both hosts pass the maintained harness repairs. A
first59-operation scoring queue is active, with remaining modern-context baselines
and the first comparison/control block. A later ownership-file failure exposed
plain CRT errno13 without winerror; the minimal bounded-retry correction passes
103 benchmark tests on both hosts, including a real Windows lock regression.
The failed baseline is fully recovered with uncertain bytes preserved.
The first batch stopped safely at35/59 operations:30 valid, five invalid, all
restored/released. Windows source-gate and context repairs are deployed; native
BSL G06 and MakeUp G08 functional retries pass. Actual-archive tests and maintained
resource/catalog synchronization repair a stale authored helper copy that refused
three checks before launch. MakeUp G04 hardware validation also passes. A fresh97-operation continuation
is running with the remaining repair checks, baselines, controls and both timing
orders. BSL G09 now requests real4.6 for its required compute sampler proof. The97-operation continuation has now finished:85 valid/12 invalid, all restored;
that scoring queue is complete. All completed timing datasets are verified off-host. Current
descriptive analysis includes32 measured cells; work/quality qualification is
incomplete. MakeUp G03/G05 and all four N01 CPU cases need repair/retest. The
canonical stale-recipe-pin correction passes its new Java resource regression,
and is now deployed after verified preservation. All eight focused hardware checks pass with restoration. A24-operation affected
comparison/control queue is now running; expected duration about3 hours. Automatic
collection and descriptive chart generation follow its completion. Old MakeUp
runtime/source cohorts are separated from repaired implementation statistics. N01's native certificate requires4.1;
failed runs used3.2 and need4.6 admission tests. Final repairs, independent
measurement qualification, regression investigation, quality and charts remain.
Verified off-host preservation of178 terminal restoration copies recovers10.37GiB,
leaving44.48GiB free before the new timing batch. Live worlds/results remain intact.
Further capacity checks apply before large motion captures. The earlier7–8-hour ETA covered the completed queue, not the final audit.
BSL bloom VRS now passes actual4.5/4.6 activation/restoration. A C01 core-profile
polygon-state repair also observes real native GPU-query work, with independent
primitive, timing and quality checks pending. See the
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
