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

**RTX5080 audit complete for the current screening scope (2026-11-07).**
All64 target/goal cells have dispositions:40 measured with two independent primary
processes each,22 source-held/no-certified-route cells, and two shared G01 rows.
This does not claim every combination runs or production/default qualification.
Native Windows RTX5080/driver617.42 at3840×2160 preserved original shader options
and AA. Nine separate original-only baseline cohorts have n2: native3.2 and real4.6
for every pack, plus BSL's optimized-stack/native cohort. Frame, window-mean and
independent-process variance remain separate. [Report and charts](../dev/experiments/rtx5080-four-target-audit-20261106/REPORT.md).

The clearest scoped reductions are MakeUp G01 copy omission3.24% and BSL G02 mip
omission3.95%. BSL G02 is the public singleton request, not the separate private
prototype. Bloom VRS gives1.77–2.24% lower means over BSL and both Complementary
packs, with a quality tradeoff and NVIDIA-only scope outside shared v1. MakeUp N04
has a small sky-view-only1.12% result; broad benefit/fragment skip frequency remain
unqualified. Other routes are slower, inconclusive, or held. No broadly useful
shared/default candidate qualified; do not add these isolated effects together.

Windows sharing/recovery, C01 core polygon state, finite platform admission,
authored/resource/catalog drift, recipe pins and required-context failures were
repaired and validated on hardware. All eight focused Runtime-V4 checks and all24
affected comparisons/controls pass/restored. Extra4.6 G03 diagnostics remain6.99%
and10.81% slower on Reimagined/Unbound; native context did not explain the regression.
All572 valid measured windows pass the exploratory2% quarter/pair trend review,
without trimming samples. G06's extra full-size target/half viewport is intentional.
Source/work/JFR diagnosis does not establish hardware-optimal implementations or
isolate every CPU/GPU bottleneck.

Six motion checks retain2,178 complete lossless native/candidate/return images.
All frames were decoded/hashed and compared; reviewed overview/unscaled samples
show no gross defect. Shader clocks/history remain unmatched, so deterministic
pixel/HDR equivalence, brief temporal artifacts, all scenes/transitions and broad
DH/PBR compatibility remain unqualified. Earlier incomplete attempts stay invalid.
The recorder now holds its logical path for render/writer capacity, preserving
121-frame completeness without feeding capture stalls into timing data.

The final [terminal audit](../dev/experiments/rtx5080-four-target-audit-20261106/terminal-audit-v7.json)
verifies217 operations terminal/restored/released, both managed profiles matching
canonical inputs and zero Minecraft clients. The final owned scheduled task is
removed. Cleanup remained ShaderLOD-only with verified backups; the32GiB reserve
was preserved. [Artifact locations/recovery](../dev/experiments/rtx5080-four-target-audit-20261106/ARTIFACTS.md).
The broader shared-renderer v1 objective remains; this completes the requested
current-machine screening audit, not production engineering of held mechanisms.

RTX2080Ti remains outside this allocation; renew availability before using it.
The active source branch is `rtx5080-audit`; milestone pushes go to user-owned origin.

## Find context

- [Benchmark guide](BENCHMARK.md) and [candidate reference](BENCHMARK_CANDIDATES.md): maintained routes.
- [Experiment history](EXPERIMENTS.md): previous attempts, evidence and reasons to revisit.
- [Latest audit detail](STATUS_AUDIT_20261105.md) and [earlier campaign snapshot](STATUS_HISTORY_20261105.md): historical results/decisions.

At milestones update objective, latest meaningful result/limits, owner/run evidence
if active, and next action here. Link detailed records; do not copy every progress
update or old ownership statement into current state.
