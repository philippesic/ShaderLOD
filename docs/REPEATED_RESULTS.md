# First repeated Photon matrix

Measured 2026-10-04 on the pinned Minecraft 1.21.1 NeoForge/Iris/Photon/DH
stack, Apple M3 Max. Source checkpoint 3f594b2, structural candidate archive
SHA256 999eae1c4c3551e0f02d45ae06b517c87f966b87b7ca6a8e779a16566c6a003b.
All 36 serial JVM captures completed; UUID, archive, loaded options, stable
camera/radius, shader fallback and runtime checks passed. Raw manifest, reports,
summaries and paired analysis: ignored `dev/logs/sustained-v3-main/`.

Three repeat blocks compare original Photon with ShaderLOD absent, patched
pack with mod bridge disabled, and active candidate. Stacks are minimal
Iris/Sodium/DH versus the same plus ModernFix, FerriteCore and ImmediatelyFast.
All use 1488x836, 24 detailed chunks and DH 128; the terrain policy is disabled.
Forest uses default quarter-resolution clouds; storm uses matched premium
full-resolution clouds. Warmup 35 seconds, measurement 20 seconds, frame-only
GPU elapsed queries each frame, CPU layer profiling disabled.

## Paired reductions versus original Photon

Positive values mean lower time. Percentages below use independent JVM pairs
within each repeat block, not thousands of frames as independent samples.

| Workload / stack | Median frame reduction | Median GPU render reduction | GPU reductions by block |
| --- | ---: | ---: | --- |
| Forest / minimal | 5.74% | 5.83% | -29.08%, 5.83%, 7.60% |
| Forest / optimized | 0.68% | 1.72% | 0.67%, 1.72%, 10.83% |
| Premium storm / minimal | 25.46% | 25.91% | 30.21%, 24.93%, 25.91% |
| Premium storm / optimized | 22.67% | 23.43% | 6.28%, 24.95%, 23.43% |

The forest's first poor active run is retained. Its slowdown did not repeat,
but no measured culprit has been established. The ordinary-scene provisional
15% gate is unmet. The cloud results are promising across both stacks but do
not establish a default-scene gain or superiority over an original lower preset.

Patched-disabled median GPU changes versus original were -1.61%/-2.27% in
forest and -3.38%/-4.45% in storm (minimal/optimized). These suggest evaluating
integration/code-shape overhead, but variance and input limitations prevent
assigning all of that difference to ShaderLOD. The optimized stack is not
universally faster; keep its effect distinct from the mod's incremental effect.

## Important input limitation

Source auditing after these captures found that Photon uses Iris's smoothed
wetness for weather humidity/wind. The reports verify rain level but do not
capture actual wetness. Its pinned rising half-life is effectively 60 seconds;
35 seconds after changing a clear save to rain does not prove convergence.
Reused storm saves can begin already raining on subsequent JVM runs and alter
initialization. This is a plausible mechanism, not a demonstrated explanation
of any particular timing. Treat these results as exploratory and retain them.

Loaded options and camera checks do not prove settled terrain upload work,
unchanging driver clocks, heap state, or every temporal shader input. GPU elapsed
intervals include driver/command gaps, not busy-time utilization. Per-run p95,
range and MAD are in the raw analysis; three repeats provide no significance
claim. Concurrent preview windows are not the source of these serial numbers.

## Next experiments

Capture applied/raw wetness per registered program without reevaluating its
smoother. Add an opt-in fixed-wetness diagnostic for matched cloud probes while
preserving normal-clock motion tests. Preserve individual screenshots in the
runner so repeats do not overwrite quality evidence. Run atmosphere/water
isolations using the prepared tooling and a DH-absent SSR driver probe suggested
by the source sampler audit. Do not expand shader targets until Photon meets
the meaningful-performance and quality gates.

## DH-absent SSR and water pilots

The original Photon shoreline with environment/sky reflections enabled linked
and rendered on the Apple driver with DH absent. The inspected image contains
shoreline/tree and sky reflections. This verifies that narrow masked profile;
full desktop options and DH+SSR remain unverified. The new dev-only wetness
wrapper applied fixed zero to every observed supplier while retaining raw zero
and advancing real clocks/counters. Its registration IDs are not program IDs.

The first smoke tag `ssr-original-probe` failed the prepared-camera guard because
Minecraft normalizes 180 degrees to -180. Its raw capture remains excluded from
performance rankings. The guard now compares yaw circularly, rejects nonfinite
values and retains linear position/pitch checks. Fresh `ssr-lod-pilot` completed:

| Single pilot / minimal, no DH | FPS | GPU mean | Thread CPU |
| --- | ---: | ---: | ---: |
| Original SSR shoreline | 139.87 | 6.580 ms | 6.634 ms |
| Patched disabled, analytic archive | 139.33 | 6.540 ms | 6.429 ms |
| Active analytic archive | 113.05 | 8.135 ms | 7.138 ms |

A separate `ssr-finite-pilot` using the same archive and water bands with analytic
normals disabled measured original 143.11 FPS / 6.404 ms and active 133.99 /
6.887 ms. These are single exploratory pairs, not independent multi-repeat
proof of a culprit. They motivate rejecting the current analytic path as a
recommended default and isolating compiler-visible loop structure.

Source audit found the earlier patch changed original constant water loops into
per-pixel dynamic loops, including nested parallax work. The new specialization
restores untouched original full functions and calls literal-count cheap
kernels. Analytic remains available but defaults off. Static numerical checks
and 1017 GLSL compilations passed; its single runtime pilot under
`dev/logs/ssr-static-pilot/` completed: original 143.79 FPS / 6.378 ms,
patched disabled 139.33 / 6.597, active 123.18 / 7.435. It still regressed. No performance improvement is inferred from those
static checks. Cloud source was unchanged so that factor remains a potential
confounder of the combined active shoreline arm.

## Effect sweep, source forensics and measurement variability

The nine-arm `effects-shoreline-r1` sweep completed with same upstream SSR
settings, fixed wetness zero and effect-source controls. FPS: original139.84,
global-disabled140.63, alloff77.35, materials-only82.11, clouds-only117.15,
fog-only130.87, water-only134.72, SSR-only135.32, combined130.39. These remain
single diagnostic controls; inactive POM cannot demonstrate material savings.

Actual Iris-translated source dumps were preserved under
`dev/logs/effect-source-forensics/source-dumps/`. The audit confirms intended
original effect branches, const water loops, direct water wrappers and no flag
substitution anomaly. Original/global-disabled/alloff CPU-only diagnostic at
15s warmup/10s capture gave139.75/141.99/140.05FPS. The old77FPS loss did not
repeat, but this differing protocol does not establish query causation.

Sparse 16-frame-query repeat (`effects-sparse-r2`,35s warm/20s capture) retained
large variation too: original145.35 then74.56FPS, alloff144.53 then89.74,
combined114.86 then117.23. Thus neither a code-shape story nor query cadence
alone explains the slow regime. AC power, no reported low/high-power override
and ample memory were observed later; one host snapshot cannot prove every
run's conditions. These are not reliable acceptance evidence.

## CPU recording and exact culling candidate

A completed no-query original recording captured144.12FPS, runTick wall6.933 /
thread6.411ms. It is a good regime only. JFR shows visibility/culling among Java
samples and native multi-draw/copies/mipmaps; command fill and GC are minor.
Sample streams do not establish exact per-function time or driver busy versus
wait. No bad-regime recording was obtained yet. See RENDER_CPU_FORENSICS.md.

The exact boolean shadow-frustum specialization removes unused negative-vertex
calculations while retaining acceptance semantics, box-culler ordering, FMA
fallback and floating-point edge behavior. Eight tests include50k randomized
sets under both FMA policies. First runtime attempt failed inherited shadow
resolution in the reversed mixin; repair uses base-injected helpers. The failed
smoke is retained. Fresh smoke rendered original Photon with fast-path active,
no fallback,143.47FPS. This is compatibility evidence, not a speedup proof.

Three paired repeats per stack (`culling-stacks-r3`) used original shader source
in both arms, with the main mod absent or present and only its culling fast path
active. Minimal frame reductions:-0.61%,-15.86%,-2.35%; thread CPU:-2.16%,-1.72%,
-3.44%. Optimized frame reductions:20.04%,7.02%,0.03%; threadCPU:16.33%,0.74%,-1.11%.
The optimized baseline spread prevents a robust general gain claim. This
candidate now defaults off; mathematically equivalent does not mean faster.
Both benchmark controls and shader quality checks remain necessary.


## Cloud transition pilot at ordinary cloud resolution

Source edffbef, matrix `dev/logs/cloud-shore-default-r1` (session45404) ended
exit0 with all7captures accepted. Same SSR shoreline, minimal stack/noDH,
fixedwetness0, 35swarmup/15scapture, sparse GPU interval16, cloudupscale4. Clean
archive SHA256 8fbaa117d87b4f729ea64ecce4d86390cdc56d03cd3b5a02ec2be9ffe2213235.
Other four effects off, protected lighting, cached sky/analytic water off; no
upstream sample-count increase. Real driver rendered new modes1/2 without
fallback. Every capture recorded focused=true/visible=true/iconified=false,
no observed transitions, logical744×418/framebuffer1488×836, vsyncfalse,
configuredlimit260 and pauseOnLostFocusfalse. Sampling can miss between-tick
changes; this does not measure GPU clocks or exclude native scheduling stalls.

| Arm | FPS | GPU render mean ms | CPU frame wall/thread mean ms |
| --- | ---: | ---: | ---: |
| Original, mod absent | 145.23 | 6.418 | 6.876 / 6.388 |
| Patched globally disabled | 142.00 | 6.446 | 7.034 / 6.535 |
| Smooth budgets, overhead protected | 143.70 | 6.384 | 6.949 / 6.422 |
| All five effects off, bridge enabled | 114.74 | 8.229 | 8.703 / 6.436 |
| Separate tier noise, overhead protected | 116.55 | 7.684 | 8.567 / 6.584 |
| Current stochastic mode | 133.46 | 6.830 | 7.482 / 6.171 |
| Separate tier noise, unprotected | 142.20 | 6.353 | 7.023 / 6.003 |

One run per arm is exploratory. No meaningful ordinary gain is established.
Smooth mode's GPU difference is only~0.5%, with slightly lowerFPS; unprotected
noise's GPUdifference~1% also does not establish gain. Alloff's slowdown has
nearly unchanged threadCPU but much higher wall/GPUintervals; focus loss is not
observed. This is evidence for another controlled diagnosis, not proof that
queries, shader branches, overhead geometry or thermal state caused the loss.
Do not recommend new modes or interpret protected/unprotected differences
causally without repeats/in-process controls.

Root inspected the smooth screenshot: near shore/tree shadows remain present
and no gross overhead-cloud loss is evident in this static view. Motion,
shimmer, cloud crossings and selected/executed GPU sample counters remain
unverified. All screenshots/reports/failures are retained. Next: source-equivalent
alloff in-process bridge toggles with the same program/JVM and captured state,
plus correctness prerequisites for exact CPU visibility reuse. New modes remain
experimental, default mode0; Photon ordinary gate and second shader stay pending.
