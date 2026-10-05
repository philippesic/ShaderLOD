# ShaderLOD status

Updated: 2026-10-05.

Active goal: [Sustained performance and compatibility](goals/sustained-performance.md).
The full goal remains active: configurable explicit adapters, useful repeated
Photon performance, optional-stack evaluation, primary-source research and
additional-pack rendering, quality and integration evidence. The ordinary ≥15%
gate and broader configuration/support acceptance remain unmet.

Latest checkpoints: `057ed06` (negative SSR cost), `f7f30d6` (far SSR cost controls), `409734a` (reduced far trace),
`4dfcdfc` (bounded capture and full-trace diagnostic), `b9f4110` (SSR isolation).
Main Java/config/uniform transport, shared helpers and existing Photon patches
remain based on `8da8943` and reserved for the original chat. This performance
chat owns exploratory adapters and all clients/GPU measurements. Exact live
handles, worker ownership and next actions are at the end of the active goal.

## Current evidence and limitations

- Photon native 2976×1672 same-JVM global controls showed roughly 6–11% lower
  GPU intervals in forest/coastal sessions. Independent serial regressions remain
  retained; these controls do not prove an ordinary ≥15% gain versus original.
  [Native results](PHOTON_NATIVE_RESOLUTION.md).
- The native combined cap-16 pilot showed 5.18% lower GPU time versus original,
  with visibly coarser distant mesh detail. A terrain-only same-JVM control
  passed real ON16/OFF24 and re-arm checks, but GPU effects changed sign
  (−30%/+10%), despite consistent CPU reductions. No stable benefit promoted.
  [Terrain control](NATIVE_INPROCESS_TERRAIN.md).
- Nine original-Photon forest captures across minimal, optimized and culling
  stacks established no consistent improvement. Exact versions/hashes and a
  narrowly audited Iris/Sodium API Base metadata difference are recorded.
  [Stack results](STACK_FOREST_RESULTS.md).
- Original/disabled/enabled native shoreline and targeted sun-glint triplets
  each completed 603 verified 10 FPS PNG frames. The enabled glint view revealed
  a bright distant-water band absent original/off. SSR-only reproduced it;
  water-only did not at the tested pose. A default-off private original far-trace control removed the band in the
  inspected pose; a reduced-trace quality/cost experiment is pending. This is a quality failure, not accepted appearance.
  [Glint audit](PHOTON_GLINT_SOURCE_AUDIT.md).
- Original native underwater capture observed WATER and no full-block camera
  collision for 176 written frames, but missed 25 slots due backpressure. Its
  incomplete record is preserved. An explicit bounded two-image option and safe lease tracking now compiled;
  all three native retries completed201/201. Original/off peaks1 do notprove
  throughput causation; enabled peak2 exercised the bounded path.
  [Capture evidence](CAMERA_QUALITY_HARNESS.md).
- MakeUp 9.5g rendered without DH; original DH rendering linked its programs and
  submitted actual world draws alongside inspected distant land/water. Its core
  consumer proved global enable and near/far in water pixels; normal rendering
  established no useful gain. High-rate glint/underwater/adapted-DH checks remain.
- Miniature 2.19 rendered original and experimental modes without DH. Diagnostic
  pixels and normal GPU readback proved core2/global enable/near96/far160. The
  distance-refinement pilot had flat GPU time; transition quality and actual
  cheaper-path coverage remain incomplete. BSL 10.1.8 original HIGH rendered
  without DH; its native parallax-off pilot was a quality-dependent 2.81% change.
  [Pack integration evidence](PACK_COMPATIBILITY_MATRIX.md).

Cloud consumer/extent-hoist/literal-secondary private probes did not establish
incremental useful performance; all remain off. Downloads, source fixtures and
sampler constraints are kept separate from actual rendering and quality proof.
Lighting retains original shadow/AO budgets by default. Cached sky, analytic
water and CPU frustum/cache/list experiments stay off. Terrain reduction requires
explicit warmed-scene acknowledgment; automatic travel coverage is unfinished.
No sunlight cadence or shadow-map refresh was changed.

Previous completed goal: [Full-frame rendering proof](goals/full-frame-proof.md).
Earlier repeated matrices and CPU/JFR evidence remain in
[repeated results](REPEATED_RESULTS.md) and [CPU forensics](RENDER_CPU_FORENSICS.md).
The first JFR captured a fast regime, not the unexplained slowdown.

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


Reduced far tracing removed the broad band in the inspected glint pose while
retaining visible sun reflection; its 41-frame quality capture passed. A four-arm
original/skip/full/reduced native cost pilot is running. This bounded appearance
check does not establish full transition/motion quality or useful GPU savings.


The four-arm native SSR cost pilot completed: original15.04ms, skip15.09ms,
reduced18.90ms andfull18.39ms GPU intervals. Reduced/full removed the inspected
band but did not establish useful performance. The pinned same-JVM SSR-only
control now compiles; 38 analyzer and 24 stack fixtures pass. The reduced
archive OFF/ON/OFF/ON session passed strict checks but GPU contrasts changed sign
(+2.1%/-13.8% time reduction). Reverse order is running under root ownership. See
[SSR control](NATIVE_INPROCESS_SSR_COST.md) and the active goal for the live handle.
The common protocol owner’s last visible turn remains the handoff; its main
Java/config/shared-helper ownership is preserved. No broader transport work is
claimed from this chat’s pack diagnostics.
