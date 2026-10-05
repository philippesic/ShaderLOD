# Density detail cost: decision after corrected pilot

2026-10-05. `performance_audit` owns only this new document. Read the active
checkpoint, corrected r2 summaries/raw GPU and CPU arrays, terminal runtime audit,
source-control description and existing runner/analyzer source. No client, GPU,
build, compiler, test, encoder or image processing ran. No existing file changed.

**Decision: defer opacity calibration and additional density benchmarks.**
The corrected diagnostic produced a small favorable difference in one serial
block, after intentionally changing fine density. Its mean GPU contrast is
0.408 ms against 15.372 ms; no reliable high-share optimization is established.
This is useful triage, not a measured isolated texture budget or strict ceiling
on procedural-detail LOD. Carry the corrected option/focus discipline into the
next broader shading-rate hypothesis. Validate a useful new signal in reverse
source order before spending time on calibration or another profiling project.

## Actual matched evidence

Primary local root: `dev/logs/photon-density-detail-coast-pilot-r2/`;
profiles: `run-workloads/photon-density-detail-coast-pilot-r2/coastalwater/`.
All original/off/on clients and focus watcher terminated; parent returned GPU
ownership to root. Existing contracts/audit accepted source/options/config,
native dimensions, timing, focus and PNG hashes. This audit recomputed scalar
statistics from saved data, not shader execution or visual quality.

One serial JVM per arm, 45 s warmup/25 s capture, frame-only elapsed GPU queries
every 5 render frames, detailed CPU layers off. Same canonical world/pose/time,
clear weather and fixed wetness 0, minimal runtime/noDH, requested/effective 24,
2976×1672 framebuffer. All capture focus observations were available/unfocused,
visible/noniconified, no unknowns or state transitions, identical vsync false,
cap 260, pause-on-lost-focus false and fullscreen false. Counts 502/502/501 are
observations, not continuous time coverage; largest sampled gaps 68.41/69.29/
68.43ms. Quiet worker coordination was recorded; clocks/thermal state and
external load were not continuously monitored.

| Arm | FPS | Mean total ms,1000/FPS | runTick wall mean / median / p95 ms | GPU mean / median / p95 ms | runTick thread mean ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original, main mod absent | 60.1758 | 16.6180 | 16.5969 /16.3062 /23.1605 | 15.4416 /15.3553 /16.8092 | 7.7597 |
| Same private source, global false | 60.8841 | 16.4246 | 16.4028 /16.1934 /23.0032 | 15.3720 /15.2213 /17.1652 | 7.4837 |
| Same private source, global true | 62.1495 | 16.0902 | 16.0644 /15.9111 /22.5294 | 14.9639 /14.8469 /16.3433 | 7.8453 |

GPU counts 301/304/310; runTick counts 1506/1524/1553. All exported GPU records
have epoch 2 and frameId residue 1 modulo 5; no zero intervals. Counters advanced
2713→4220 /2671→4196 /2786→4340, matching rendered-frame counts 1507/1525/1554.
Shader time remains real despite fixed day 12500/weather/wetness. Frame samples
are correlated and provide no independent run-level uncertainty.

| Enabled comparison | Mean GPU reduction | GPU median / p95 reduction | Capture total-duration reduction | FPS uplift |
| --- | ---: | ---: | ---: | ---: |
| Versus same private disabled source | 0.408144ms /2.6551% | 2.4597% /4.7885% | 2.0361% | 2.0784% |
| Versus original | 0.477734ms /3.0938% | 3.3110% /2.7720% | 3.1758% | 3.2800% |

Against disabled, runTick wall mean/median/p95 improve 2.0627%/1.7431%/2.0597%,
but mean render-thread CPU **increases 0.36159ms /4.8317%**. CPU wall, thread,
GPU and FPS are distinct metrics; the result does not establish CPU execution
work avoidance. Swap wall means 0.8478/0.8090/0.7444ms are separate overlapping
frame components, not amounts to add to GPU means. Small boundary differences
between capture duration and runTick mean are preserved.

## Movement within the recorded runs

| Arm | GPU first/middle/last third mean ms | GPU third-mean range ms | runTick wall first/middle/last third mean ms |
| --- | --- | ---: | --- |
| Original | 15.3361 /15.3802 /15.6070 | 0.2709 | 16.3573 /16.6350 /16.7983 |
| Disabled | 15.5053 /15.2892 /15.3221 | 0.2162 | 16.4055 /16.4023 /16.4005 |
| Enabled | 15.0476 /14.9803 /14.8648 | 0.1828 | 16.0425 /16.0385 /16.1123 |

Thirds partition records by export order with nearly equal record counts, not
equal wall-time windows. Ranges are 45–66% of the 0.408 ms disabled/enabled
contrast. That is material movement, **not a calibrated noise floor, confidence
interval or proof that the contrast is entirely drift**. Direction and tails
are favorable here, but source order and different JVMs remain entangled.
Do not pair the hundreds of samples across JVMs as independent matched trials.

Mean runTick wall on frames with an exported GPU result exceeded other frames
by 0.2043/0.3424/0.0797ms for original/off/on; matched counts 301/304/310.
These are observational frame-membership contrasts, not causal query penalties.
Query maintenance, submission gaps and shared scheduling can affect later
frames; the result stream is not a complete scheduled-query census at its edges.
This does not establish that disabling queries preserves the small contrast.
The earlier large pass-attribution concern remains; no pass timing or sum is
used to reconstruct this frame budget.

## Why this stops before calibration

The corrected gate is present in Iris's active option map. Original effective
options are unchanged; both private maps add only
`SHADERLOD_DENSITY_DETAIL_COST_CONTROL=true`. Actual global false/true is recorded.
Cloud primary horizon/zenith counts 40/20, lighting/ambient 6/2 and temporal
upscaling 4 stay original; water iterations 3 and other shader passes are unchanged.
Normal rendering/core registration is accepted activation evidence. No physical
fetch count, extension consumer, density 0.5 GPU readback or shader busy-time
measurement is claimed.

Source replaces fine erosion noise with 0.5 in main-pass cumulus density. Coarse
shape and declared ray counts/extents remain original, but nonlinear shaping,
occupancy, transmittance and early exits can change executed work and appearance.
Therefore its aggregate delta is neither an isolated texture-fetch cost nor
an optimistic ceiling for a quality-preserving calibrated mechanism. Resized
separate-JVM stills do not establish fine detail/opacity or motion parity.

The 1.5× throughput target against this original capture would mean 11.0787ms
total duration. Current enabled duration 16.0902ms still needs 5.0116ms /31.1467%
less duration, assuming this one-run baseline remains appropriate. This is
arithmetic, not accepted progress or additive effect budgeting. A quality-changing
~2% total-duration signal does not justify immediate nonlinear calibration work
as the central route to the goal. Do not infer that larger shading-rate mechanisms
have the same ceiling, or enlarge the default cloud workload to manufacture gain.

R1 remains a failed option-discovery/focus integration attempt; enabled never
ran there. Its data and archive are retained. Corrected r2 does not retroactively
qualify r1, and the earlier AO regression is likewise preserved.

## Minimum reliability check for the next larger candidate

1. **First priority: source-order repeat only after useful initial signal.**
   Keep the upcoming candidate's private archive/options identical between
   disabled/enabled arms, same focus/pacing/window/world, GPU N5/frame-only and
   layer-off. If its initial off→on block shows a materially larger aggregate
   opportunity, use one fresh on→off block. Report both run contrasts and
   same-state spread; stop if direction flips or same-state variation swamps
   the benefit. This is the shortest GPU check of a source-work hypothesis.
   It is a diagnostic repeat, not full 50% acceptance or a reason to revive
   density calibration automatically.
2. **Then corroborate total throughput with GPU queries off**, only if the
   signal survives source-order control. Hold the same source/arm/focus/settings,
   coarse CPU clocks on and detailed layers off; compare disabled/enabled in
   reverse order to the query-bearing pair. A useful GPU optimization must
   survive as a total-frame benefit. Query-off supplies no GPU milliseconds.
   Avoid a new all-instrumentation-off harness; existing `-PgpuTiming=false`
   is sufficient for this bounded question.
3. **Neutral original+mod versus original without mod is conditional**, not a
   mandatory extra matrix. Use it only if fixed-source disabled costs repeatedly
   differ materially from original or runtime integration becomes the blocking
   question. Current disabled GPU mean is only 0.06959ms below original, and both
   private arms have the same main/extension state. This does not prove zero
   addon overhead, but supplies no evidence requiring an addon profiling project.
   The existing neutral control and its scope limits are in
   [the reliability audit](GENERAL_LOD_MEASUREMENT_RELIABILITY.md).

No further density run is recommended now. If root explicitly needs to falsify
the current small signal, one fresh enabled→disabled N5 pair is more targeted
than three repeated original/off/on blocks, more directly tests GPU/source
direction than starting with query-off, and avoids unrelated mod-overhead work.
Keep it separate from r2, without selecting only favorable results.

The existing runner supports a two-arm two-block design in off/on/on/off order:
`water-patched-off water-adaptive --repeats 2`. Its default aggregation omits a
pair comparison when `water-high` is absent; after terminal, existing
`analyze_proof_matrix.py MANIFEST --baseline water-patched-off --output NEW_JSON`
can compare the two private arms. This is a supported analysis option, not a new
harness. Root must prepare fresh equal source copies and retain private-source
qualification, class/config pins and focus guards; the case names alone do not
qualify an upcoming shader. Do not route a new private archive through the
B15 same-JVM global/effects controls without their required source/option contract.

## Reproduction identities and limits

Hardware/runtime: Apple M3 Max / OpenGL 4.1 Metal 91.7, macOS 27.0 build 26A428,
Oracle HotSpot 21.0.9+7-LTS-338, Minecraft 1.21.1 / NeoForge 21.1.253 /
Iris 1.8.12-snapshot+mc1.21.1-local / Sodium 0.6.13+mc1.21.1, noDH. Timer backend
ELAPSED_ALTERNATING, timestampBits 0 / elapsedBits 32. This does not validate
other GPUs, desktop Ultra or DH rendering. Matrix HEAD
`dd78c2bdbaba264ec548cd8f693fa8514b786d9f`; main/dev trees
`6debde5638cd965e798f3bf5d7cbf9d7586510f954c44efdf753e91401b5402f` /
`26f1d00e2bac0b6a0aa169ad6914ffc12c80365e221dbb12dba72355298b9e76`.
Loaded Iris/Sodium hashes match the preceding reliability audit. Exact source/
artifacts/options/controls/focus actions are in each raw report and terminal audit.

| File or input | SHA256 |
| --- | --- |
| Original Photon | `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` |
| Corrected shared private archive | `066fc9862085cbd977d039c8a9efc31b4c9423eb018ec318533f0248adfdd1c4` |
| Original effective option map | `ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669` |
| Shared private effective option map | `4eb676875da8ada5aa494c5591f185ae9f0f3cc557b439c868d761e2ce4fd3af` |
| 001-b1-water-high-minimal-measurement.json | `5d2609486ca073b0290dbd050814079f9ce3c6e5ad9f80ea642ccbbbc0438311` |
| 002-b1-water-patched-off-minimal-measurement.json | `48ee395f68bcc54dcde4d2fb2d76a09e525ce00d7589f13dfafeabd34e8d17d5` |
| 003-b1-water-adaptive-minimal-measurement.json | `d38221065168b484e5832ddcc023256711548d2841d14e75cf84a3094d467255` |
| matrix-manifest.json | `a25abcf5dba0015fdb923fcc5500d9c6fac4117b29d0ba83d929ae3ea9fb8823` |
| matrix-analysis.json | `1a1a29a63386ac2f3e48bc313b2740ba32f04ed2ee02a89a657305b92a53009a` |
| density-runtime-audit.json | `2c1d6e087710858666a7ff3e9a26bdd3db1f5923a4dc0d7e9c979d72b5d40436` |
| warmup-focus-actions.json | `9eb4530d1bc9a5acd16f9778f5abf12f39b1adfe1987589efde39d0e3a5c27ef` |

All report paths in the last eight rows are under the primary local log root.
Observed capture intervals UTC: original 20:52:10.361559–20:52:35.400778,
disabled 20:53:37.140793–20:54:02.185373, enabled 20:55:03.769156–20:55:28.768466.
User-local date is 2026-10-05; times above are explicitly raw UTC identifiers.
No new runtime check or major quality decision was taken by this owner. Root
owns the next candidate choice and any measurement handoff.
