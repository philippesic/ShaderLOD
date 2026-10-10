# Sildur averaged renderer speed — independent review

Reviewed October 10, 2026, approximately 14:05 PDT by `/root/makeup_average_review`.
Reviewer owns only this report and ignored `sildur/review/`; root owns runtime/build/source.

**Accept practical original-relative scene-level renderer benefit.** Average elapsed
frame time falls **18.11%** and throughput rises **22.12%** against first original.
Original return is **1.11% lower in ms/frame** than first original. All three clean
stills show no conspicuous missing geometry/shading loss. This does not establish
shader-LOD, motion quality, UI restoration or broader compatibility acceptance.

| Arm | Render frames / elapsed seconds | Elapsed ms/frame | Throughput FPS | Sampled game-render GPU mean / median / p95 ms (n) |
| --- | --- | --- | --- | --- |
| Original, main absent | 2535 / 30.047004750 | 11.852862 | 84.3678 | 10.246084 / 10.111250 / 12.781750 (253) |
| Candidate, main present, Performance | 3096 / 30.050266459 | 9.706158 | 103.0274 | 8.091197 / 7.569376 / 11.637375 (309) |
| Original return, main absent | 2563 / 30.043007125 | 11.721813 | 85.3110 | 10.371808 / 10.331041 / 12.842626 (256) |

Each arm warms40s, then measures30s. Independently recomputed all released GPU
scope statistics and total counters exactly. Elapsed ms/frame is
`1000*elapsed_seconds/render_frames`; throughput is frames/seconds, not a mean of
instantaneous FPS. The reviewed `WorkloadSession` clock starts after input setup
and uses the render-counter difference. GPU elapsed timers alternate FRAME/IRIS
sample categories every5 render frames. P95 is nearest rank `ceil(.95*n)`; these
scope samples do not describe all displayed frames. No terrain/render-and-blit
scope samples exist in this bracket, so none are invented. Shadow-map means are
3.411484 → 2.869665 → 3.129122ms; their variation does not isolate the coalescer's
causal contribution, especially because this route leaves shadow commands native.

## Matching inputs and actual chain

Evidence root: `dev/logs/speed-replication-r1/renderer-averages/sildur/`.
Inspected sequence, all plans/freezes/owners/releases, postrun-world records,
loaded runtime reports/logs and three clean final PNGs. Reports/PNGs remain under
`run-workloads/speed-replication-r1/renderer-averages/sildur/{original,candidate,return}/client/`.
Raw report hashes and actual archive hashes match releases/plans.

- Every arm loads original Sildur Vibrant2.02 Extreme, SHA `824504bc…d7ebe`.
  All115 native options match exactly across arms, expected maps and
  `src/main/resources/assets/shaderlod/sildurquad/native115.json`; native AA retained.
- Start/end states match coast `(256.5,95,768.5)`, yaw135/pitch9, Overworld
  time7000, clear weather, requested/effective24chunks. Shaders active, fallback
  false, noDH; candidate terrain-radius policy false. GUI hidden.
- All602 capture observations per arm are focused/visible/non-iconified at
  1920×1200 fullscreen, vsync false, limit260. Warmup states match. This is
  sampled window evidence and can miss brief inter-observation changes.
- Apple M3 Max/OpenGL4.1 Metal91.7; MC1.21.1, NeoForge21.1.253,
  Iris1.8.12 snapshot local, Sodium0.6.13. Main mod absent/present/absent;
  dev observer in all. Frozen build/canonical-world records match.
- Saved plan → supervised launcher → direct core config/control → ordinary
  `MakeUpQuadRuntime.enabled()` Sildur route → exact Sildur source/native115
  certificate → Sodium native fill return injection → adjacent quad compaction
  → native submission. The shared chain's cheap solid/cutout/non-shadow/config/
  pipeline/API guards precede source hashing; mismatches/exceptions retain native
  behavior. The entire batch is validated before command writes. Current rollout
  admits Sildur and its source hash matches the frozen build. No patched archive
  or terrain-radius change supplies this benefit. This timed bracket adds no
  command-count diagnostic or stronger causal attribution than earlier evidence.

## Stops, disposal and boundaries

Owners record exact PID/birth/argv,140-second deadlines and session stop commands.
All exit0 with no live client PIDs; complete reports/PNGs precede deadlines.
Releases report clients absent/lock free; reviewer process snapshot at
**21:03:43 UTC** found no ShaderLOD client/Gradle launcher. Reviewer has no jobs
and launched/stopped none. No diagnostic-only reports or patched shader dumps.

Actual launch plans disable Gradle configuration cache before measurement.
After verified client absence, runner records each completed disposable world's
238 file hashes in `postrun-world-before-disposal.json`, then deletes only that
profile's `saves/ShaderLOD-Landscape`. Reports/images/configs and canonical source
records remain; deleted clone bytes cannot be independently rehashed now. This
cleanup follows measurement and does not change its frame denominator.

One bracket on one laptop/scene has no host thermal/clock trace or confidence
interval. Equal timer overhead and baseline drift remain explicit. Final stills
naturally differ in water/entity/temporal state; no motion parity claim. Direct
startup Performance and a main-absent return do not prove Apply/Off UI behavior
with the mod installed. DH/PBR, other scenes/loaders/versions and packaged shipping
remain unverified. These boundaries do not block this scoped practical speed result.

Independent recomputation: ignored
`dev/logs/speed-replication-r1/renderer-averages/sildur/review/recomputed.json`.
