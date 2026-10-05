# ShaderLOD status

Updated: 2026-10-04.

Active goal: [Sustained performance and compatibility](goals/sustained-performance.md).
The user authorized ongoing team development, Photon first, benchmarks with
optional performance mods, and additional shaders after repeatable Photon gains.
Previous completed goal: [Full-frame rendering proof](goals/full-frame-proof.md).
The preview clients are closed. A serial repeated matrix is active under the
coordinator; see the goal checkpoint for its session, profile paths and logs.
Earlier evidence and limitations are in [FULL_FRAME_PROOF.md](FULL_FRAME_PROOF.md).

Current source: Photon analytic-water and atmosphere candidates, usable cloud
bands, conservative scene-bound terrain safeguards, configuration UI/commands,
and repeated effective-option verification. Java build, 10 geometry tests and
20 Python tests (including 792 GLSL compilations) passed. Candidate rendering is
verified in accepted captures; performance is mixed and not yet an acceptance
claim. Photon's new gate and second shader support remain pending.

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
