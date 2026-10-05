# ShaderLOD status

Updated: 2026-10-04.

Active goal: [Sustained performance and compatibility](goals/sustained-performance.md).
The user authorized ongoing team development, Photon first, benchmarks with
optional performance mods, and additional shaders after repeatable Photon gains.
Previous completed goal: [Full-frame rendering proof](goals/full-frame-proof.md).
Root owns a live seven-arm shoreline cloud pilot (session45404). The first 36-run matrix, water pilots,
nine-arm effect sweep, compiled-source controls, sparse-query repeats, JFR
capture and exact shadow-culling repeats have finished. Their results remain
mixed; the ordinary-scene performance gate is unmet. Second shader support has
not started. See [REPEATED_RESULTS.md](REPEATED_RESULTS.md).

Current committed source: `edffbef`. Explicit compile-time effect controls retain
original code when off. Water analytic normals and the exact shadow-frustum
fast path default off after negative or inconsistent measurements. Shadows/AO
retain original budgets. Terrain radius policy remains opt-in and requires
explicit warmed-scene acknowledgement. Mod enable/preset use the verified Iris
uniform bridge; effect switches and cloud/water bands are shader options.

The completed original-Photon JFR diagnostic found substantial visibility
traversal and native GL submission work in a fast 144 FPS run. It did not capture
or explain the 145 to 75 FPS baseline variation. See
[RENDER_CPU_FORENSICS.md](RENDER_CPU_FORENSICS.md). Exact shadow culling rendered
successfully after repairing an inherited Mixin lookup, but repeated minimal
stack CPU times regressed; its default remains off.

Cloud candidates, seven matched controls, report-only window telemetry and strict
culling/cloud guards passed static checks. Root is testing the real driver. A
worker is developing a no-skip list-ownership prerequisite in an isolated worktree. Root owns all client/GPU launches;
no worker may launch a game. Static checks establish source correctness only.
The active goal checkpoint records detailed ownership and the next experiments.

## Previous completed proof

The ordinary forest has substantial terrain and native driver submission costs;
default clouds already run at low resolution. Revised cloud distance bands
reduced GPU render time by roughly 13–29% across premium-cloud workload runs,
with considerable variation. A simple medium preset remained faster at different
quality. The original shader-only approach did not address the dominant costs
in the default scene.

A default-off terrain policy caps the effective detailed radius at 16 chunks
while preserving the requested 24 and DH's 128-chunk reach. The repeated combined
forest candidate improved from 124 to 143 FPS, with GPU render time falling from
7.482 to 6.319 ms. This exposes an existing vanilla/DH detail tradeoff through a
guarded policy; it is not a new mesh renderer or a universal speedup.

Previous comparison: left protected-lighting ShaderLOD plus the warmed terrain cap;
right original Photon with ShaderLOD absent. Both display FPS, real GPU elapsed
time, CPU wall/thread clocks, and requested/effective render radius. Concurrent
windows share the GPU; serial captures provide the performance evidence.
Disabling the terrain policy restored 24 detailed chunks and re-enabling it
returned to 16 after warmup. Automatic coverage checks during travel remain
unfinished, so the policy requires explicit warmed-scene confirmation.

Shadow filtering, screen-space shadows, and AO retain original budgets by
default. The cached-lighting approximation is disabled in the final candidate.
The user's earlier observation of distant trees losing shadows is recorded as
an unacceptable tradeoff for the small gain in the previous aggressive profile.

## Environment and validation

Minecraft 1.21.1 / NeoForge 21.1.253 / Java 21 is the first target. Exact Iris,
Sodium, DH, and Photon artifacts are locked in `dev/runtime.lock.json`. Iris is
required; DH is optional for users and present in the development runtime.
The Apple M3 Max uses a reduced Photon profile. Full desktop SSR, voxel lighting,
POM and Ultra rendering remain unverified on suitable hardware.

Java build, geometry/restoration JUnit tests, timing/policy/CPU tests, and eight
shaderpack tests including 660 GLSL compilations and helper-distance fixtures
passed. Actual Iris/Photon rendering, the absent-mod original baseline, DH
rendering, live policy restoration, and production jar exclusion of development
profiling code were checked separately. GPU queries use asynchronous elapsed
counters; timestamps are unavailable on this host. Scope limitations are in
[GPU_TIMING.md](GPU_TIMING.md).

## Earlier completed goals

- [Development environment](goals/setup-environment.md): NeoForge skeleton,
  pinned runtime, agent roles, documentation, and basic explicit Photon LOD.
- [GPU timing and broader Photon/DH LOD](goals/gpu-lod-dh-comparison.md): wider
  effect budgets and comparison tooling. The aggressive cached-lighting capture
  measured 7.960 to 7.523 ms, but its distant lighting loss was rejected.

No remote host, publishing destination, or push is configured. Generated clients,
worlds, binaries, screenshots, and raw reports remain outside Git.
