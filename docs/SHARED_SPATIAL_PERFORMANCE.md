# Shared spatial repeated measurement controls

2026-10-05. Source-qualified controls and focused mutation fixtures only. **No
benchmark, Minecraft launch, GPU measurement, copied timing profiles, driver or
quality acceptance occurred in this assignment.** Target remains 1.5 × total
rendering throughput against original Photon, with little discernible quality
loss. The parent accepts integration; these controls never complete a goal.

The [controls script](../scripts/run_shared_spatial_benchmark.py) prepares and
qualifies profiles and ingests operator receipts. It deliberately has no launch
path. The sole runtime owner controls the lock, each JVM, focus, screenshots,
shader reloads and GPU. It reuses the strict source preparer, current workload
harness, matrix profile/temporal/stack validators, frame metrics, and **only** the
existing independently content-verified API Base artifact-equivalence registry.
It preserves raw artifact identities and qualification records, including the
main mod identity in every arm; there is no general hash bypass.

## Fixed design before measurement

| Arm | Source | Private switch | Policy | Purpose |
| --- | --- | --- | --- | --- |
| original | reviewed original Photon control | absent | FULL | goal denominator |
| privateOFF | reviewed OFF archive | false | FULL | private-source erasure/parity control |
| privateON-FULL | reviewed ON archive | true | FULL | added producer/consumer graph overhead |
| privateON-ADAPTIVE | identical ON archive | true | ADAPTIVE | shared LOD system |

All four contain the main mod, global enable true, terrain reduction off,
minimal Iris/Sodium stack and no DH. The exact source/options/world/config freeze
is inherited from [shared spatial controls](SHARED_SPATIAL_RUNTIME.md). Opaque
bands remain 32/96 blocks, transition 8 blocks, ratios 1/0.25; other effects stay
INHERIT/unity with salient-lighting protection. These are private diagnostic
controls. Nominal quarter-rate does not imply a measured speedup. No source,
settings, Java build, resource, or runtime dependency change is allowed between
arms or across a batch.

Forest camera is `(256.5, 95, 768.5)`, yaw 135°, pitch 9°, time 7000, clear
weather, observed fixed wetness 0, 24 detailed chunks, 2976 × 1672 framebuffer
and 1488 × 836 logical window. All 366 upstream **effective** Iris options are
held; the derivatives add exactly the one private switch with its arm's boolean.
Original archive SHA-256 `c4e956…b57f1`, OFF `a8c004…54ceb`, ON
`c2790b…bf6f0`; full digests/member inventories/source registration are enforced
by the existing preparer. Production JAR must remain
`0d499564c24d652c782a5c0e6b26fbae4a93b323542c5a4c58bbeee58da8ae9f`.

A four-JVM **diagnostic pilot** runs original/OFF/FULL/ADAPTIVE. It cannot pass
the repeated target even if its metrics look favorable. It is mandatory before
the repeated batch. Reject/stop before committing the repeated schedule for any
input/freeze/window/compiler/quality/ownership failure; OFF GPU or total-frame
median > 1.10 × original; FULL median > 1.25 × original; or ADAPTIVE GPU or
total-frame median or p95 > 1.10 × original. These conservative spending gates
are not the target and do not establish statistical significance. Preserve a
failed result and diagnose before proposing a new frozen protocol.

The repeated design runs two identical four-treatment Williams cycles, eight
independent blocks and 32 fresh JVM/world copies. With O=original, D=OFF,
F=FULL, A=ADAPTIVE, each cycle is `O D A F`, `D F O A`, `F A D O`, `A O F D`.
Each arm occurs once in each position and every directed within-block predecessor
pair occurs once per cycle. Cross-block thermal history remains a qualification;
fresh independent JVMs avoid shader/world carryover within a process. The pilot
and repeated runs are separate, and pilot frames never enter repeated metrics.
Minimum warmup/capture time is 4 minutes pilot and 32 minutes repeated, excluding
startup, save copying, settling, screenshot and process termination.

Every JVM uses 30 s warmup, 30 s capture, frame-only asynchronous elapsed GPU
queries every five rendered frames, and total-frame CPU wall instrumentation.
No summed pass measurements, per-layer CPU timers, sampler/source readbacks,
work traces, DH evidence, water survey, shadow observation, visibility lists or
coherence oracle. The existing coarse CPU scopes (`run-tick`, `game-render`,
`render-and-blit`, `swap-and-events`) remain instrumented; only `run-tick` supplies
total-frame wall metrics. Timing scopes/epoch/cadence/stream coverage must match.
Native window observations must be available, stable, visible, non-iconified and
unfocused throughout capture, with zero unknown/contradictory counts/transitions.
Vsync off, limit 260, windowed, pause-on-lost-focus false. A warmup-only focus
watcher must exit before capture; no per-capture process scans or unrelated tools.

## Acceptance and analysis

Analysis uses independent JVM runs as replicates, not correlated individual
frames. Report each run's GPU elapsed and total-frame wall median/p95, plus
`1000 × elapsed_seconds / render_frames`. Per-arm median/min/max/MAD and all six
paired block contrasts retain original→OFF, original→FULL, OFF→FULL,
original→ADAPTIVE, OFF→ADAPTIVE and FULL→ADAPTIVE separately.

The primary throughput ratio is original elapsed per rendered frame divided by
ADAPTIVE elapsed per rendered frame. 1.5 × means 50% greater throughput and
33.333% less elapsed time per frame. The repeated timing target requires all
32 valid runs; every original→ADAPTIVE block at least 1.5 ×; median at least
1.5 ×; every OFF→ADAPTIVE block faster; each original→ADAPTIVE GPU median
improved; GPU and total-frame p95 at most 1.05 × original in every block; and
relative run MAD ≤ 5% for each arm's GPU median, total-frame median and elapsed
per-frame mean. No single block, incomplete batch or nominal retention passes.
These are preregistered stability/target guards, not a confidence interval.

An acceptance **candidate** also requires separately accepted, exact-scope
consuming-stage driver/layout/cache/sampler/fallback evidence and representative
still/motion quality evidence. The script never manufactures these approvals.
Final screenshot headers/hashes prove capture extent and provenance, not motion
quality. Preserve adverse/contaminated windows; stop on observed quality loss or
input/runtime drift. Do not replace runs within a frozen batch to chase a gain.
No DH, other GPU, desktop Ultra/POM or shipping-support conclusion follows.

## Operator handoff

The operator/root supplies a JSON prerequisite ledger, schema 1, with:

- `scope`: exact `scope` object emitted by `--qualify-only`, including full class/
  resource inventory digest, source/option/world/control pins and complete configs.
- `accepted_by`, `resource_owner`, and exact `hardware` object with only `gpu`,
  `driver`, `opengl`, `os`, `java`, `framebuffer`, `window`. `gpu` and `opengl`
  must exactly match actual console OpenGL Renderer/Version values; OS/Java/driver
  provenance belongs to the sole operator's evidence, not hardcoded Mac defaults.
- `driver`: `accepted: true`, `checks` containing all six `DRIVER_CHECKS` keys
  from the script as true, and nonempty `evidence` references.
- `quality`: `accepted: true`, all six `QUALITY_CHECKS` as true, and nonempty
  still/motion `evidence`. The separate [quality plan](SHARED_SPATIAL_QUALITY.md)
  is preparation only and cannot substitute for review of actual captures.
- Nonempty `hardware_evidence` references; `quiet_protocol` exactly
  `{ "no_builds_tests_encoders": true, "no_source_readbacks": true,
  "context_only_between_windows": true, "sole_client_gpu_owner": true }`.
- For repeated preparation, `pilot: { "accepted": true, "analysis": <reference> }`
  pointing to this script's complete, exact-scope pilot analysis with no stop gate.

Every evidence reference is `{ "path": "/absolute/file", "sha256": "…" }`.
Missing, changed or unaccepted prerequisites fail. A frozen source pass never
sets runtime/quality gates true. Requalification catches later builds/resource
regeneration and script/launch/dependency drift; prior acceptance must match the
new complete scope rather than treating a known version as equivalent.

`--prepare` copies all worlds **before** capture, guarded by exclusive-client
availability and source-world locking. It preserves the existing untimed
preparation ledgers and records separate timing requests/commands/expected files
in `shared-spatial-benchmark-preparation.json`. Do not edit either ledger. UUIDs
are fresh for every copied profile. The preparer's diagnostic metadata remains
source ancestry and is not relabeled measured performance evidence.

Before each launch the operator acquires `dev/logs/.proof-matrix.lock`, checks
no live Minecraft, invokes `fresh_inputs(profile, expected)` and
`development_freeze()`, and compares their complete inventories with the timing
ledger. A repeat launch is forbidden after the world changes. Observe the agreed
quiet protocol; builds, tests, parsers, encoders and unrelated CPU activity are
excluded. Read load/process context only before/after windows. Snapshot exact
hardware/Java/OS, launcher PID/session, Java PID, owned process group, start time,
command, logs and request. Use the existing warmup-only focus API if sound for
that owner. Kill only the exact owned JavaPID/process group on abort with SIGTERM,
then a 20 s bound before SIGKILL; verify both absent before next arm or handoff.
The controls script does not launch, kill, hold the runtime lock or enforce
thermal/quiet state itself.

The operator writes one receipt per completed run to an ignored directory, named
`b{block}-p{position}-{arm}.json`. Required receipt fields:

- Exact `command`, `request`, `resource_owner`, positive `launcher_pid` and
  `java_pid`, and integer `started_ns` (wall-clock nanoseconds before launch).
- `prelaunch_development`, `postrun_development`: the entire freeze object;
  `prelaunch_input_files`: expected complete copied input map;
  `prelaunch_world_sha256`: the exact stopped world pin.
- `hardware`: exact accepted map; `hardware_evidence`: reference to an actual
  operator-produced JSON containing that hardware map.
- `report`, `console`, `screenshot`, `prelaunch_context`, `postrun_context`:
  immutable evidence references. Report UUID/mtime and screenshot mtime/native
  PNG header are checked, as are console GL identity and fatal/CPU-layer markers.
- `quiet_window`, `owned_processes_stopped`, `focus_action_before_capture`,
  `watcher_exited_before_capture` all true; `quality_regression_observed` false.
  These are explicit operator assertions with preserved evidence, not newly
  sampled runtime enforcement.
- `instrumentation_arguments`: all `OFF` keys as the string `"false"`, plus
  `gpuTiming: "true"`, `cpuTiming: "true"`, `gpuSampleInterval: "5"`.
- `postrun_global_config`, `postrun_effects_config`: parsed complete TOML objects
  equal to their respective scope values. NeoForge formatting rewrites may be
  semantically equal; no config behavior change is accepted.

`--analyze` ingests receipts in scheduled order, stops at a missing receipt and
labels incomplete evidence, strictly validates reports/options/native inputs/
window/timing/effective loaded versions and artifacts, and preserves each raw
receipt hash and exact equivalence record. All actual scene/window/hardware and
loaded identities must match across runs; only deliberate archive/option/Iris
selection differences are normalized. The expected loaded version map comes
from the original source inventory, removing DH and adding the main mod.

Schema 2 WorkloadSession does not export `cpuTiming`, `gpuPassTiming`, `workTrace`,
OS/Java/GPU identity or consuming effects readback. Effective minimal streams are
checked through exact frozen commands, actual scope/cadence and console evidence;
OS/Java and live-process/quiet assertions remain operator provenance. Separate
accepted untimed driver/readback evidence and exact classes/source/config freeze
support linkage but do not resample consuming uniforms during timing. Real
shader temporal phase and GPU clocks advance; this is not phase-aligned physical
work attribution. GPU elapsed intervals include command starvation, not just busy
time. Preserve these boundaries in any resulting performance report.

## Checks actually run

```sh
python3 -m unittest discover -s tests -p test_shared_spatial_benchmark.py -v > build/shared-spatial-benchmark-fixtures-final.log 2>&1
python3 scripts/run_shared_spatial_benchmark.py --dry-run --stage pilot --profiles-root run-workloads/shared-spatial-perf-pilot-r1 > build/shared-spatial-benchmark-pilot-dry-run-final.json
python3 scripts/run_shared_spatial_benchmark.py --dry-run --stage repeated --profiles-root run-workloads/shared-spatial-perf-repeated-r1 > build/shared-spatial-benchmark-repeated-dry-run-final.json
python3 scripts/run_shared_spatial_benchmark.py --qualify-only --profiles-root run-workloads/shared-spatial-perf-pilot-r1 > build/shared-spatial-benchmark-qualification-final.json
```

Fifteen lightweight tests passed, no skips, 0.069 s. Mutation fixtures reject
source/UUID/options/weather/camera/global/terrain/wetness drift; native focus,
pacing, unknown and contradictory observations; wrong timing scope/epoch/cadence,
NaN/zero values and sparse streams; incomplete or duplicate schedules; adverse
tails/drift/reversal; class/resource additions/removals/mutations/symlinks; missing
or changed quality/driver/hardware prerequisites; changed receipts/configs/owner,
main-mod identity bypass and unknown equivalence hashes. A fully positive receipt
and independent run-level analysis pass their synthetic controls. Initial r1
failure (contradictory focus counts) is retained, then repaired; final checks
supersede it without relabeling that raw failure.

Actual source/scene/class/resource/dependency/equivalence qualification completed
with `source-qualified`, `performance_accepted: false`, `launch_status: HOLD`.
No accepted prerequisite ledger exists. Neither actual copying through this new
wrapper nor receipt ingestion from a live batch has been executed. Those remain
integration checks for the sole runtime owner after driver/quality acceptance.
No owned live processes, GPU work, Gradle builds or large source suites.
