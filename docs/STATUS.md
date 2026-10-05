# ShaderLOD status

Updated: 2026-10-05.

Active goal: [Sustained performance and compatibility](goals/sustained-performance.md).
The user authorized ongoing team development, Photon first, benchmarks with
optional performance mods, and immediate research/experimental testing of additional
shaders. Support and performance promotion still require repeated evidence.
Latest source/evidence checkpoints: `b003e05` (MakeUp core bridge), `7945a3c`
(native controls and moving capture), `fc94094` (research and stack experiments).
Main protocol implementation remains based on `8da8943`, reserved for the original
chat. This performance chat owns all game/GPU clients and exploratory adapters.
Current process handles and next actions are at the end of the active goal.

Current verified evidence:

- Native 2976×1672 Photon controls with the same JVM, world, shader pipeline and
  fixed effect options showed enabled GPU reductions of 5.99–11.42% in a clean
  reverse session and 8.01–8.24% in a clean forward session. These are correlated
  phases, not independent-run acceptance. A coastal SSR/no-DH session also
  measured 7.00–8.88% GPU reductions; repeated second-workload acceptance remains. Earlier serial
  active regressions remain retained; the provisional ordinary ≥15% gate is unmet.
  See [native results](PHOTON_NATIVE_RESOLUTION.md).
- Nine original-Photon forest captures across minimal, optimized and culling
  stacks established no consistent benefit. Exact loaded versions and hashes
  include a narrowly qualified Iris/Sodium API Base metadata difference.
  See [stack results](STACK_FOREST_RESULTS.md).
- MakeUp 9.5g original and default-off water experiments compiled and rendered
  without DH. Original/off/on moving shoreline sequences completed 41 verified
  PNGs each at 2 FPS. Higher-rate temporal, glint and underwater checks remain.
  The initial active performance pilot regressed; it is not recommended.
- An isolated MakeUp adapter consumed existing protocol-2 global enable and
  radial near/far values, proved through four real water-color diagnostics.
  Preset, transition, debug and per-effect config are unconsumed. Cue-off normal
  rendering checks completed; no second-pack support or speedup is promoted.
  See [bridge evidence](MAKEUP_CORE_BRIDGE_PROBE.md).

Lighting remains protected by default, cached sky and analytic water remain off,
and terrain radius reduction requires explicit warmed-scene acknowledgment.
CPU shadow/frustum/cache experiments remain off after weak or negative results.
The earlier exact coherence oracle matched 5.16 million real queries, but its
low coverage and slower replay variants did not establish a useful speedup.
No sunlight cadence or shadow-map refresh was changed.

Previous completed goal: [Full-frame rendering proof](goals/full-frame-proof.md).
Earlier matrices, JFR and source diagnostics are retained in
[repeated results](REPEATED_RESULTS.md) and [CPU forensics](RENDER_CPU_FORENSICS.md).
The first JFR captured a fast regime, not the unexplained cross-JVM slowdown.

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


## Current experiments

Cloud-only native controls measured 4.52–5.84% lower GPU time; fog changes were
small and water had no useful forest signal. A secondary extent-hoist source
probe did not establish incremental benefit and remains off. A separate literal
secondary-kernel probe is qualified for controlled testing by an exact live
sampler-state report; candidate compiled/rendered but its first serial pilot regressed and remains off.

Miniature 2.19 original and its native two-refinement control compiled and
rendered a no-DH shoreline. This single pilot establishes neither a distance
adapter nor a useful speedup. MakeUp original now linked its DH programs and submitted actual DH world draws
with inspected distant land/water. Broader boundary and water quality remain. The sole GPU operator is this performance chat; exact process
handles and next actions are at the end of the active goal.


BSL10.1.8 original HIGH and native parallax-off controls now rendered without
DH. One native quality pilot showed2.81%lower GPU time; no shaderderivative or
LOD claim. Miniature's isolated corecue also proved existingversion2/global
on/off/radialbands in actual waterpixels, while leavingSSRwork unchanged.
Original Miniature finalpass filters were observed with non-mip depthnearest/
colorlinear/aniso1/sampler0; a separate refinement experiment is sourcework,
not yet compiled/rendered. See BSL_RUNTIME_PLAN.md and
MINIATURE_CORE_BRIDGE_PROBE.md.
