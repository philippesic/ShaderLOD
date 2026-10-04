# Full-frame attribution and adaptive rendering proof

Created: 2026-10-04. Status: complete.

## Objective

Determine the dominant CPU/GPU rendering costs, compare original high, original
lower, and adaptive workloads, and implement changes targeting the measured
bottleneck. The user authorized geometry/submission work when justified. Preserve
NeoForge/Iris/Photon scope and optional DH, then leave the final comparison open.

## Scope and ownership

Pinned Minecraft 1.21.1 / NeoForge 21.1.253 / Iris 1.8.12 / Sodium 0.6.13 /
DH 3.3.3 / Photon v1.3b. The Mac M3 Max supports elapsed queries but no useful
GPU timestamps. No alternative desktop host was supplied; desktop-only effects
remain unverified. No external push, publication, or remote operation was used.

The coordinator owned interactive clients and measurement runs. Bounded agents
owned extended timing, workload preparation, and shader/geometry review in
separate file partitions. All workers finished; the source changes are integrated.

## Results

Full attribution found meaningful native draw submission cost in the ordinary
forest. Narrow CPU hooks measured approximately 0.145 ms command construction
versus 1.44 ms native submission. These scopes overlap their parents and include
driver execution; they do not prove a purely Java bottleneck. An invasive command
cache was not justified by the small construction cost.

The original cloud bands left most visible clouds at full cost. Revised
128/1024/64-block cloud bands preserve overhead sampling while reducing farther
work. Original shadow and AO budgets stay protected; cached-sky approximation is
off. Premium-cloud GPU render reductions ranged approximately 13–29% across runs.
The complete-JVM repeat measured original high 62.98 FPS / 15.124 ms, adaptive
72.43 FPS / 13.113 ms, and simple medium 112.93 FPS / 8.197 ms. Medium remains
faster with different quality; no superiority claim is made.

A default-off warmed terrain cap reduces detailed chunks from 24 to 16 while
preserving requested settings and DH 128 reach. Repeated forest controls:
original high 124.30 FPS / 7.482 ms; geometry-only 138.02 / 6.582; final combined
143.04 / 6.319. Coarser DH did not help. A separate global TAAU diagnostic was
not robust enough to establish an advantage. Failed and anomalous controls are
retained and explained in [the full report](../FULL_FRAME_PROOF.md).

The terrain policy exposes an existing radius tradeoff rather than a new mesh
renderer. It requires explicit scene coverage acknowledgement; elapsed warmup
alone does not prove a complete cache. Automatic coverage during travel is
future work. Runtime disable restored 24 chunks, then re-enable restored 16
following warmup. Raw requested settings remained unchanged.

## Acceptance evidence

- Complete-frame and pass CPU/GPU attribution, separate CPU wall/thread clocks,
  non-nesting asynchronous queries, and optional sparse sampling implemented.
- Realistic matched high/lower/adaptive cloud and forest controls run serially,
  waiting for complete JVM termination, with actual options and artifact hashes.
- Shader, geometry, simple preset, global resolution, and DH-quality hypotheses
  evaluated. The report distinguishes shader-only and combined results.
- Java build and geometry/restoration JUnit tests passed. Eight packaging tests,
  660 GLSL fragment compilations, helper math fixtures, and timing/CPU/sparse
  policy tests passed. Actual driver rendering is recorded separately.
- Development profiling code excluded from the production mod jar. Original
  Photon baseline runs with ShaderLOD absent. DH rendering and restoration
  verified. All generated data remains ignored by Git.

## Final handoff

The ordinary forest comparison is open: left protected-lighting ShaderLOD plus
warmed cap 16, right original Photon with detailed radius 24. Both retain DH 128,
matching cameras, and FPS/GPU/CPU/radius overlays. Preview GPU queries use a
16-frame cadence. Concurrent-window readings share the GPU; use serial reports
for performance conclusions. User visual acceptance remains their decision.

At handoff, left PID 33267 / session 95919, right PID 33442 / session 88670;
profiles `run-workloads/forest/final-adaptive` and `run-workloads/forest/high`.
Console logs: `dev/logs/final-proof-left-console.log` and
`dev/logs/final-proof-right-console.log`. Verify process identity before stopping;
these identifiers become stale after a relaunch.

Further instructions and evidence: [full report](../FULL_FRAME_PROOF.md),
[GPU timing](../GPU_TIMING.md), [terrain policy](../GEOMETRY_LOD.md), and
[workload preparation](../WORKLOADS.md).
