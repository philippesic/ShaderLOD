# Current state

Updated 2026-11-08 (Pacific). The cross-shader/cross-machine screening audit is
complete. Supported targets are Complementary Reimagined, Complementary Unbound,
BSL and MakeUp Ultra Fast. The retention decision below supersedes earlier
shortlists and recommendations to continue screening these candidates.

## Latest outcome and next action

**Philip's retention decision, 2026-11-08 (Pacific): retain an optimization only
if it showed a positive result across all four supported shaders AND both
completed machines. None qualified. All sixteen candidates — G01–G10, C01–C02,
N01–N04 — are parked. No default enablement.** A held or unavailable cell is not
a positive result. Parking preserves implementations, reports, charts and all
other evidence; it does not erase scoped gains or claim universal impossibility.

The completed machines are Fedora Asahi Remix at 2560×1600 and native Windows
RTX 5080 / driver 617.42 at 3840×2160, with original shader settings and AA.
The Asahi audit classified 64 cells, 36 timed; the RTX audit has dispositions for
all 64 cells: 40 measured with two independent primary processes each, 22
source-held, and two shared G01 rows. Existing evidence remains authoritative:
[Asahi conclusions](../dev/experiments/asahi-four-target-audit-20261104/audit-conclusions.json),
[Asahi audit detail](STATUS_AUDIT_20261105.md) and
[RTX report and charts](../dev/experiments/rtx5080-four-target-audit-20261106/REPORT.md).
No new measurements accompany this decision. Percentages below are mean
frame-time reductions against matched controls; positive means faster.

Near-misses are **parked with reasons**, not retained winners:

- **MakeUp G01 copy omission:** about +8.14% on Asahi and +3.24% on RTX;
  replicated on both machines, but only MakeUp, with the other packs held.
- **C02 bloom VRS:** RTX BSL/Reimagined/Unbound +2.24%/+2.01%/+1.77%;
  MakeUp held. NVIDIA-only quality tradeoff, unavailable on Asahi because the
  required NV capabilities are absent; fails the all-shader/all-machine rule.
- **BSL G02 mip omission:** RTX BSL +3.95%, but Reimagined -5.67% and
  Unbound -10.01%; a BSL-only win that regresses Complementary.
- **MakeUp G06 exact AO attenuation:** about +4.14% repeated on Asahi;
  RTX MakeUp about +0.05% (~zero), with harmful results on the other packs.
  The Asahi-only gain does not qualify.

MakeUp N04's RTX +1.12% is sky-view-only and negative on the other packs;
MakeUp G04's +0.26% has the other packs held. G03/G05/G06/G07 and most other
measured RTX cells are slower or approximately zero. None changes the parking
decision. MakeUp G01/N02/N03 share one deletion result and must not be added
together; isolated effects from other routes must not be added either.

**Next useful engineering:** extend the existing measurement system into a
[cost map](COST_MAP.md) of named Iris passes, CPU work, GPU intervals and waits,
with native-pack baselines for all four targets on both machines and first-class
absent/enabled-DH cohorts, LOD CPU/GPU coverage and horizon scenes. Philip authorized stages 2–4 implementation and bounded Asahi end-to-end testing
on 2026-11-08 (“go build the whole thing and test it well e2e”). The section 10
capture program, RTX/Nsight calibration, training and stage 6 review remain unauthorized. The cost map should identify large slices to
investigate; the maintained A/B total-frame protocol remains the proof method.
Do not start another G-number optimization campaign from the parked shortlist.

**Cost-map stage 1 landed (2026-11-08):** development-only versioned event records,
integer scope registry, frame/epoch/graph/invocation ownership, explicit missingness
(including DH identity), bounded buffers and loss health. CPU-only synthetic checks
cover all eight stage-1 acceptance items; adjacent sampling-policy/statistics checks
also pass. No runtime timing or client was run. See the [stage-1 contract notes](COST_MAP_STAGE1.md)
for the test command and stage-2 seams. The subsequent [implementation checkpoint](COST_MAP_IMPLEMENTATION.md) adds timer/probe
lifecycle changes, optional canonical live-hook records, source seeds and an offline
export pipeline. Stages 2–4 are **partially implemented, not accepted complete**;
that note lists remaining authorized work. Stage-1 checks and synthetic pipeline
e2e pass. The follow-up now adds the owned live launcher, automatic graph/cohort
boundaries and physical DH joins. Bounded original-MakeUp runs on Apple M1 at
2560×1600 passed absent-DH and enabled-DH CPU/GPU hook/export checks, Iris reload,
and explicit DH rendering off/on transitions, with config/whole-save restoration.
DH residency remains unverified (enabled-not-ready). All-four-pack enabled semantic
joins, worker/upload timings and other remaining acceptance items are listed in
the implementation note; stages 2–4 are not represented as fully accepted.
Machine qualification remains unperformed.

## Completed audit context (2026-11-07)

The RTX audit retains nine original-only baseline cohorts with two independent
processes each: native 3.2 and real 4.6 for every pack, plus BSL's optimized-stack/
native cohort. Frame, window-mean and independent-process variance remain separate.
The G02 result above is the public singleton request, not the private prototype.

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
These completed screening and recovery records remain evidence; they do not
retain any candidate under the 2026-11-08 rule or authorize production engineering.

RTX2080Ti remains outside this allocation; renew availability before using it.
The active source branch is `rtx5080-audit`; milestone pushes go to user-owned origin.

## Find context

- [Benchmark guide](BENCHMARK.md) and [candidate reference](BENCHMARK_CANDIDATES.md): maintained routes.
- [Experiment history](EXPERIMENTS.md): previous attempts, evidence and reasons to revisit.
- [Cost-map design sketch](COST_MAP.md): proposed measurement extension; no runtime validation yet.
- [Latest audit detail](STATUS_AUDIT_20261105.md) and [earlier campaign snapshot](STATUS_HISTORY_20261105.md): historical results/decisions.

At milestones update objective, latest meaningful result/limits, owner/run evidence
if active, and next action here. Link detailed records; do not copy every progress
update or old ownership statement into current state.
