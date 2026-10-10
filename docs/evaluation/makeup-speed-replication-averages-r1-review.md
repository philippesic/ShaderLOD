# MakeUp averaged speed replication — independent review

Reviewed October 10, 2026, approximately 13:38 PDT, by `/root/makeup_average_review`.
Only this report and ignored `makeup/review/recomputed.json` were written; root owns the runtime lane.

**Accept as practical original-relative total-render speed evidence in this fixed Mac scene.**
The candidate reduces elapsed time per rendered frame **29.61%**, increasing throughput
**42.07%** against first original. The original return is **1.54% slower in ms/frame**.
No conspicuous terrain, foliage, water or shading loss appears in the three clean final
PNGs. This is renderer coalescing evidence, not full shader-LOD/compatibility acceptance.

| Arm | Render frames / seconds | Elapsed ms/frame | Throughput FPS | Sampled game-render GPU mean / median / p95 ms (n) |
| --- | --- | --- | --- | --- |
| Original, main mod absent | 3815 / 30.042986458 | 7.874964 | 126.9847 | 7.403788 / 7.354375 / 7.748999 (109) |
| Candidate, main present, Performance | 5419 / 30.038283500 | 5.543141 | 180.4031 | 5.089365 / 4.744875 / 6.965334 (154) |
| Original return, main absent | 3757 / 30.043145208 | 7.996578 | 125.0535 | 7.576066 / 7.580916 / 8.136500 (107) |

Each arm warmed 40 seconds. Independent calculations from raw reports exactly match
all release GPU statistics and total throughput. Mean elapsed frame time is
`1000 * elapsed_seconds / render_frames`, not a mean of instantaneous FPS.
`WorkloadSession` starts the clock/frame denominator after setup fingerprints and
subtracts the starting `GpuTimers` render counter. GPU p95 uses nearest rank:
`sorted_samples[ceil(.95*n)-1]`. These are sampled scope timings, not every displayed
frame or display-latency percentiles. World opaque/cutout GPU means are
3.972436 → 2.478666 → 4.149773 ms (108/155/108 samples).

## Evidence and matching inputs

Raw evidence: `dev/logs/speed-replication-r1/renderer-averages/makeup/`.
Inspected `sequence.json` and each arm's plans, freeze, owner, release, console,
focus receipt and raw report/PNG. Reports and images are under
`run-workloads/speed-replication-r1/renderer-averages/makeup/{original,candidate,return}/client/`.
All raw report hashes match releases.

- Loaded original MakeUp 9.5g SHA `0036da17…19db8` in all arms; 100 effective native
  options exactly match each other, expected-options and the eligibility certificate.
  Native AA is unchanged. Frozen build and canonical-world records match.
- Start/end: coast camera `(256.5,95,768.5)`, yaw 135, pitch 9, Overworld time 7000,
  clear weather, requested/effective distance 24 chunks. Shaders in use, fallback
  false, DH absent, GUI hidden; candidate terrain-radius policy inactive.
- Apple M3 Max, OpenGL 4.1 Metal 91.7; MC 1.21.1, NeoForge 21.1.253,
  Iris 1.8.12 snapshot local, Sodium 0.6.13. Main mod absent in originals and
  present only in candidate; dev observer present in all.
- Each arm has 602 capture observations: focused, visible, non-iconified,
  1920×1200 fullscreen, vsync false, configured limit 260 FPS. Warmup matches.
  Startup focus precedes measurement. Sampled states can miss brief changes.

## Actual consumer and recovery paths

Saved plan → finite `dev_client.supervise` → direct startup core config/control →
`MakeUpQuadRuntime.enabled()` → Sodium `fillCommandBuffer` return injection →
`AdjacentQuadCoalescer.compact` → native submission. Solid/cutout/non-shadow
checks precede runtime checks. Config/resources/API/pipeline/GL guards precede
archive hashing. Exact source/options certificates are cached while pipeline,
options, world and lifecycle match. Exceptions/mismatches fail closed. Compaction
checks the entire batch before writes; invalid metadata/capacity leaves native
commands intact. Current rollout permits MakeUp/Sildur by default and its source
hash matches the frozen build. No patched archive or terrain-radius reduction
supplies this gain. This bracket adds no command-count diagnostic or stronger
causal attribution than source/earlier runtime evidence.

Owners record PID/birth/argv, 140-second deadlines and exact session stop commands.
All clients exited successfully after complete reports/PNGs before deadlines.
Runner checks client absence under the lock before advancing. Releases record
lock free; independent process snapshot at **20:36:36 UTC** found no ShaderLOD
client/Gradle launcher. Reviewer launched/stopped no jobs. Quiet receipt records
all source workers released; historical absence of all unrelated host work was
not independently observable. GPU timers are equally enabled: elapsed category
rotation every five render frames. CPU samples absent. No diagnostic-only report,
patched dump or cloud probe found. Console has optional absent-class/GLSL warnings;
no runtime fatal/failed render is established.

The first evaluator failed after valid original measurement because Off/main-absent
reports lack main-only `terrain_active`. The repaired guard checks it only when
main present, separately verifies absence, and reuses the same original report/
owner without relaunch. Absent values remain absent. **Low-severity provenance
limitation:** `evaluator-failure-provenance.json` transcribes actual terminal
sessions 26042/59153; original traceback is in chat tool history, not a raw log file.
This does not invalidate the independently checked original measurement.

## Unverified boundaries

One bracket/scene/laptop has no host thermal/clock trace, confidence interval or
broad workload claim. Final stills naturally differ in entity/water/temporal state;
they do not establish motion stability. Direct startup config and mod-absent return
do not prove Apply/UI/Off restoration with the mod loaded. DH, PBR, other loaders/
versions/scenes and packaged shipping acceptance remain unverified. Those boundaries
do not block this scoped speed result or require a new benchmarking framework.

Independent calculations: ignored
`dev/logs/speed-replication-r1/renderer-averages/makeup/review/recomputed.json`.
Reproduce by loading each release's raw report, dividing elapsed seconds by rendered
frames, then grouping `gpu_samples` by scope for mean, median and nearest-rank p95.
