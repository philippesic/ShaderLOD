# Four-phase live-bridge control in one JVM

Source-only proposal, 2026-10-04. No code, build, profile, shader reload or capture
was performed for this design. The coordinator's running matrix remains the
only GPU owner. This control addresses between-launch variability and possible
live-bridge scaffolding costs; it does not establish a performance improvement.

## Question and fixed rendering workload

Run one already-prepared all-five-effects-off Photon candidate and change only
`ShaderLodConfig.ENABLED` between four phases: OFF/ON/OFF/ON. Keep one JVM, world,
camera, shader-pack archive, effective shader options, shader pipeline and
compiled programs. Do not reload shaders, switch packs or recopy a save between
phases. All other production config values remain fixed.

Use a pinned **no-atmosphere-guard** archive. Current shared source contains
`shaderlod_needs_atmosphere`: without border fog/blocky clouds its terrain result
is `depth == 1.0 || !shaderlod_enabled()`. A structural candidate can therefore
change atmosphere work when global enablement changes even with all five effect
switches false. That would invalidate this workload. Verify the selected archive
actually retains the original unconditional `d4_deferred_shading` atmosphere
call; a helper declaration alone is not evidence of an active call. Record the
exact archive hash and build metadata, not merely a filename containing
"no-atmosphere".

Required effective pack controls are:

| Control | Required value |
| --- | --- |
| `SHADERLOD_CLOUDS`, `SHADERLOD_WATER`, `SHADERLOD_FOG`, `SHADERLOD_SSR`, `SHADERLOD_MATERIALS` | false |
| `SHADERLOD_USE_MOD_CONFIG` | true |
| `SHADERLOD_PRESERVE_LIGHTING` | true |
| `SHADERLOD_CACHED_SKY`, `SHADERLOD_WATER_ANALYTIC`, `SHADERLOD_DEBUG` | false |
| Mod debug | false throughout |
| Terrain policy and warmed-scene confirmation | false |
| `rendering.optimizeShadowCulling`, `rendering.separateShadowVisibilityLists` | false |

The main mod is present in every phase. Off/on is a runtime uniform/config
control, distinct from removing the mod or recompiling a pack with different
macros. Keep all upstream quality options identical, including reflections,
cloud resolution, ray counts and any material resource packs. Use an inspected
fixed-camera scene; shoreline clear weather with fixed shader wetness 0 is one
practical initial control. Storm needs a separate matched fixed-wetness 1 run.
Actual Minecraft rain and day/world-age values remain unchanged within a
session. FrameCounter, frameTime and animation clocks continue normally.

## Remaining scaffolding that must be acknowledged

The five switches own cloud, water, fog, SSR and materials. They do not globally
remove every ShaderLOD expression. Current explicit source still:

- Computes a generic `shaderlod_quality_at` in deferred lighting and passes one
  from translucent shading. With `SHADERLOD_PRESERVE_LIGHTING`, PCF/SSRT/GTAO/SSAO
  select tier zero and original budgets; source-level tier expressions can
  remain before optimization. Do not assume the driver eliminates all of them.
- Retains the deferred debug branch
  `if (shaderlod_debug_enabled() && !is_hand)`. The debug helper reads its bridge
  flag independently of global enablement. Debug false must be verified from
  config/observations; global OFF is not a substitute for disabling debug.
- Retains uniform declarations and the bridge sentinel. `shaderlod_quality_at`
  returns early when disabled, while enabled can compute generic tiers even
  when no effect changes its final work. This is the specific scaffolding/code
  path difference the proposed control can expose.

`CommonUniformsMixin` registers bridge version 2, enabled, preset, near/far,
transition and debug at `PER_FRAME`. Several getters construct a validated
`ShaderLodConfig.snapshot()`. Only the enabled value changes here; the other
getters and registrations remain present. Supplier execution or intended config
values do not, by themselves, prove a GPU uniform upload or active linked
uniform. Some unused uniforms can be optimized out. If a bounded observer is
added, observe the existing registered integer supplier before returning its
original value; do not reevaluate it at report time or infer linked uniform
state from one registration log. Identify observed versus unobserved suppliers.

The global flag also changes CPU short-circuit paths:
`GeometryLodController` evaluates enabled before terrain-enabled, and
`ShadowCullingRuntime` evaluates enabled before its optimization flag. Keeping
these features false prevents geometry/culling/list-ownership work changes, but an extra config
read on ON is still possible. Commands/runtime-status reporting may log one
flag transition; the settling interval excludes that transition from capture.
Therefore any ON/OFF difference measures the complete live-flag/scaffolding
path, not exclusively the cost of one GLSL branch or `glUniform` call.

"Same workload" here means preserved effect budgets and expected rendering
behavior under these controls. It does not claim byte-identical source to
original Photon, pixel-identical temporal frames, or equal instruction counts.

## Bounded upgrade to the existing session

Upgrade `BenchmarkSession` rather than creating a second world/client/bootstrap
framework. Today it alternates four phases, uses fixed 8-second switch settling
and 20-second capture, stores raw CPU/GPU lists, and restores enabled=true at the
end. It lacks per-phase elapsed/frame counts, current input fingerprints,
window/wetness exports and fresh phase identities. `ComparisonClient` already
ignores control-file `enabled` while a benchmark owns the flag, but other camera,
preset/debug/terrain fields can still change; reject those changes while
capturing. Reject simultaneous benchmark and workload capture at entry.

Suggested initial parameters: 45–60 seconds initial warmup, four phases,
8 seconds settling and 20 seconds capture per phase. This is roughly three
minutes after one JVM startup, with no reload/bootstrap between phases.
Accept an explicit starting order; the first run is OFF/ON/OFF/ON. A later
ON/OFF/ON/OFF session checks ordering drift, not independent frame replicates.
Save the initial enabled value and restore it on completion/failure instead of
unconditionally setting true. Do not persist temporary phase values or alter
other user config. Restore only after the last image/query collection has ended.

A small state machine is enough:

1. Validate eligibility, record session UUID/fingerprints, initialize fixed
   weather policy from startup and warm the one scene.
2. Set expected enabled state on the client/render thread; record a unique phase
   UUID and switch frame/time. Start settling; keep rendering and clocks normal.
3. At capture start, reset `GpuTimers`/`CpuTimers` samples and record their new
   epochs. `GpuTimers.resetSamples()` currently advances its epoch and resets CPU
   history; old pending results are discarded by epoch filtering. Record actual
   frame ID after reset; do not reset Iris frameCounter or histories.
4. Begin a fresh `ShaderInputTracker` measurement window and a per-phase window
   tracker. Record input/temporal snapshots. `beginMeasurement()` clears observed
   ranges/read counts without calling any original wetness supplier. Keep the
   same registration IDs across phases and do not treat lifetime latest values
   as capture-window observations.
5. Capture raw samples only from the current epoch and frame range. At the end,
   record actual elapsed nanoseconds/render-frame delta and all snapshots before
   changing the flag. Preserve unresolved-query/drop counters. Do not block the
   renderer waiting for GPU queries.
6. Save phase checkpoint and an image outside the timing interval. Wait for its
   write callback before proceeding to the next timed phase. No reload is needed
   for these image-equivalent controls. Clear any image/readback disturbance
   during the next settling interval. Retain partial reports on failure.
7. Restore the initial flag, finalize the report atomically, then stop the client
   only when benchmark-only mode requests it. The external runner waits for the
   complete JVM exit and preserves all phase images/hashes.

Initial/control checks should preserve the existing dev harness's mod-absent
workload path; this specialized session explicitly requires the main mod. It
must refuse to label a fallback standalone shader value as live bridge control.
If the active compiled programs expose no observed enabled supplier, label the
uniform path unobserved rather than pretending that the phase proved an upload.

## Report and acceptance fields

Keep one session report with a schema/version, session UUID, status/error,
source revision, exact runtime/artifact identities, actual loaded mod versions,
archive/build metadata, complete effective-options fingerprint, shared scene
source/control provenance and requested instrumentation policy. Per phase:

| Field group | Required evidence |
| --- | --- |
| Identity | Phase UUID/index, intended enabled, starting order, switch/start/end monotonic times and frame IDs |
| Counts | Settling/capture duration, render-frame delta, FPS-derived frame duration, GPU/CPU epoch and sample/drop/pending counts |
| Stable inputs | Same pipeline/world identity token, pack/effective options, camera/dimension, framebuffer, requested/effective radius, upstream config, debug/preset/bands, feature config/activity |
| Expected variable | Config enabled and any observed original enabled-supplier values/counts; no unrelated input exemption |
| Temporal | Initial/start/end wetness per registration, fresh capture ranges/read counts, actual/source day/world-age/rain, real frameCounter/frameTimeCounter/frameTime |
| Window | Fresh per-phase focus/iconified/visible counts, availability, transitions, logical/framebuffer sizes, max observation gap and cached configured pacing |
| Samples/images | Separate raw CPU wall/thread and GPU streams, phase image filename/SHA256, explicit incomplete/unavailable fields |

Across phases compare stable input snapshots after removing **only**
`lod_settings.enabled`; include any separately recorded enabled supplier as an
expected variable. Advancing clocks/counters/raw wetness belong in temporal
metadata, not an overbroad "ignore changed inputs" exception. Require fixed
applied wetness to match every observed supplier and its capture range when a
fixed policy is requested. Record dynamic raw variation; it is not silently
assumed equal. Day time/world age, camera and effective radius stay fixed.

Require original/protected lighting budgets, geometry inactive, separate-shadow
list ownership inactive and `shadow_culling_active=false` throughout every phase. A pipeline reload,
dimension/camera movement, source/options mutation, debug change, wrong flag,
fallback, incomplete image or unexpected feature activation marks the session
invalid. Keep its raw/partial evidence and error reason. Unknown window flags
remain explicit; visible/iconified transitions expose contamination and should
be reviewed rather than silently discarded. Focus differences need a declared
analysis rule, not an assumption that unfocused means a particular cap.

A phase with zero/insufficient observations cannot prove a supplied uniform or
wetness value. Exposure should not be conflated with program identity: wetness
registration IDs currently label holders/frequency, not shader programs.

## Query-free and timer-free controls

The legacy session currently treats an unsupported GPU backend as a reason to
stop; update that policy so requested query-free modes can complete correctly.
Do not fabricate GPU milliseconds from inverse FPS.

- **GPU + CPU:** Match frame-only sparse/full query cadence across all four
  phases, CPU layer timings disabled unless attribution is specifically needed.
- **No GPU queries, CPU-only:** Set GPU timing false, keep CPU wall/thread clocks
  on. Export backend disabled, absent GPU stream and actual frame counts/FPS.
  `GpuTimers`' tick frame IDs still advance when queries are disabled. No timing
  code may initialize/poll/read a GL query in this mode.
- **Both timer streams off, FPS-only:** Optional diagnostic if timer overhead is
  suspected. Keep frame counts and phase-boundary elapsed clock; CPU/GPU streams
  must be absent, not zero-valued. This is not a CPU-timing measurement. Do not
  call it "CPU-only" merely because GPU queries are absent.

Keep window/wetness metadata cadence and HUD policy identical within a session.
Avoid state-dependent HUD text/color/draw workload in a small-overhead control;
a static benchmark overlay or hidden HUD is preferable, recorded explicitly.
Image capture and hashing stay outside measured ranges. No new synchronous query
or full-world hashing is needed between phases; full artifact/config hashes are
boundary/session work, not frame hooks.

## Interpretation and next step

Report both neighboring paired reductions (phase 1→2 and 3→4) and every phase's
frame/CPU/GPU mean, median and tail. Two pairs in one JVM are correlated phases,
not two independent JVM replicates. Also compare OFF phases with each other and
ON phases with each other to expose drift. A large common drift weakens a flag
causal claim. Do not pick the faster ON phase or invent confidence from frame
sample counts. Retain the corresponding reverse-order and query-free sessions
when deciding whether a reproducible difference exists.

A reproducible ON penalty while all effect work is held constant would justify
inspecting remaining live flag/debug/lighting expressions and CPU config paths.
It would not identify which one caused it. Unstable or disappearing differences
would weaken the earlier cross-JVM causal story but would not prove a particular
weather/thermal/cache cause. Query-dependent effects can reflect instrumentation
or driver command/tile interactions; elapsed GPU time is not busy-time
utilization. CPU thread time includes driver work and does not isolate Java.
Uniform source identity cannot reveal driver-generated ISA or shader execution
occupancy. Meaningful rendering optimization still needs the separate ordinary
scene/quality/repeated-performance gate.

Implementation acceptance before any timed launch: pure phase-state/epoch tests,
current semantic/input/window/wetness guard fixtures, restoration/failure tests,
query-disabled no-GL tests, image freshness/UUID preservation and dev-only jar
exclusion. Runtime acceptance then confirms one unchanged pipeline and shader
archive, four expected flag phases, advancing counters, actual supplier
observations where available, matching fixed input/fixed wetness and preserved
phase images. Only the coordinator should run that capture after the current
matrix is finished and these changes are reviewed.

## Implemented bounded checkpoint

The coordinator authorized the upgrade after reviewing this proposal.
`WorkloadInputs` now shares the existing option/temporal/input/hash routines with
`WorkloadSession`; ordinary workload report fields are retained. The specialized
benchmark captures every loaded LOD/terrain/rendering config field, verifies
alloff/no-atmosphere conditions, keeps world/pipeline references, and exposes
`BenchmarkSession.phaseMarker()` for a separate bounded observation worker.
Marker fields are sessionId, phaseId, phaseIndex, enabled and sampling; it allocates
only at boundaries. Four phases get unique images/checkpoints, fresh timing
windows/frame counts, independent window summaries and restored initial state.

Raw ShaderLOD config-file hashes remain in observations. Specialized semantic
identity removes that byte hash and replaces it with the complete loaded config,
then removes only enabled for cross-phase comparison. Within-phase enabled stays
part of semantic identity. Pinned NeoForge `ConfigValue.set` calls
`loadedConfig.config().set(path,value)` and updates its cache; it does not call
`save()` explicitly. The benchmark does not call save. Underlying persistence
behavior is not assumed to preserve raw file bytes across an expected flag
change, and changes to every other loaded field remain detectable.

Configured hide_gui=true and GUI scale are captured in the specialized inputs;
unexpected HUD/scale changes fail. The coordinator updated the comparison draw
handler to respect that hidden HUD. These sessions therefore must not be compared
as absolute FPS controls against earlier visible-HUD captures.

Extraction and the initial implementation compiled successfully. Standalone
Java fixtures passed for complete semantic identity, expected enabled/raw-file
changes, unexpected terrain mutation, culling/list activation and an enabled
module. Subsequent small HUD/backend/checkpoint additions await the coordinator's
final compile; runtime application remains unverified. No Minecraft capture was
run by this worker. GPU-disabled policy accepts query-free CPU-only/FPS-only
modes and reports disabled/absent GPU data; requested GPU modes require a supported
backend. Root still owns observer wiring, final integrated checks and launch.
