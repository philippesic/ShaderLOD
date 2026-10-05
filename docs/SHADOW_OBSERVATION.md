# Development shadow input observer

The observer records shadow transform/input equality and a limited projected
motion proxy. It never cancels rendering, skips clears, changes matrices,
modifies textures or authorizes shadow reuse. Production mod sources and jar
remain separate from this development source set. Its motivation and the
transaction hazards are in [PHOTON_SHADOW_CACHE_DESIGN.md](PHOTON_SHADOW_CACHE_DESIGN.md).

Enable only for a separate diagnostic capture:

```text
-PshadowObserve=true
```

The coordinator maps that property to
`shaderlod.dev.shadowObserve=true`. Default is false. Disabled hooks return
original expressions/matrices immediately before reading game inputs; recording
entrypoints perform no work, GL calls, allocations or clock reads. Disabled
`snapshot()` returns a cached report object. Preallocated arrays and that object
are allocated once at class initialization, not during frame recording.

## Registration and report API

Register these dev mixin class names in `shaderlod-dev.mixins.json`:

```text
ShadowInputObserverMixin
ShadowMatrixObserverMixin
```

Mixin registration and `WorkloadSession` wiring are coordinator-owned. Public
API on `com.pp.shaderlod.dev.timing.ShadowInputObserver`:

```java
configurePhotonFeatures(Map<String, String> effectiveOptions);
beginMeasurement();
Snapshot snapshot();
```

Call configuration once with the actual loaded Photon option map, then
`beginMeasurement()` at capture start and `snapshot()` for report serialization.
Missing/non-boolean WAVING_PLANTS, WAVING_LEAVES, WATER_DISPLACEMENT,
WATER_CAUSTICS or COLORED_LIGHTS entries remain unknown. Configuration allocation
and report/map allocation happen outside frame hooks. The snapshot excludes the
current pending renderer frame, which completes at the next observed renderer
start; it does not finalize or mutate rendering state. Starting measurement
excludes any pending warmup frame and discards earlier comparisons.

The frame ID comes from the existing development `GpuTimers.beginTick()` counter,
which advances independently of whether GPU queries are enabled/sparse. Duplicate
or nonconsecutive render frame IDs are reported rather than treated as adjacent
comparisons. The observer is intended for the coordinator’s ordinary single-view
client-thread captures; VR/multiple-view semantics require another audit.

## Pinned source hooks and side-effect boundaries

Audited Iris jar: `iris-neoforge-1.8.12+mc1.21.1.jar`, SHA1
`a3e6355915c7d3b2bc392724795113e51d289378`. Hooks are strict (`require=1`,
`expect=1`) on that binary; a different version needs source/runtime checks.

`ShadowInputObserverMixin` uses MixinExtras `ModifyExpressionValue`, returning
the identical original value. It observes the first `Minecraft.getInstance()`
expression inside `ShadowRenderer.renderShadows`, after the zero-shadow-distance
early return and before shadow renderer mutation. It reads existing camera
position/angles/entity, world day/game time, render directives and resolution.
`SystemTimeUniforms.TIMER.getFrameTimeCounter()` is a side-effect-free field
getter in the pinned bytecode; it does not advance the timer or reevaluate a
smoother/uniform supplier. No world/entity/chunk enumeration or reflection occurs.

At the second `LevelRendererAccessor.getLevel()` invocation within the slice
after `ShadowCompositeRenderer.renderAll()`, it observes completed geometry and
existing `ShadowRenderer.MODELVIEW`/`PROJECTION` floats plus rendered entity/block
entity counts. At this point Iris has restored its GL/render buffers/culling and
cleared ACTIVE. The early-zero-distance path does not pass through these hooks
and therefore contributes no renderer frame; it is not represented as a reused
map. Exceptions/incomplete paths never produce certified completeness.

`ShadowMatrixObserverMixin` uses `ModifyReturnValue` on the pinned methods:

```text
MatrixUniforms.lambda$addMatrixUniforms$0(PackDirectives): Matrix4fc
MatrixUniforms.lambda$addMatrixUniforms$1(PackDirectives): Matrix4fc
```

These are the original model-view/projection supplier bodies. Each handler
returns the same matrix reference without modification and does not call the
supplier again. All return calls during an active observer frame are counted;
only the first return for each matrix kind is copied. Renderer matrices and
consumer supplier matrices are separate observations. Missing probes or source
versus first-consumer disagreements are explicitly reported. It does not claim
that every program supplier was inspected or that later evaluations cannot
change. No extra JOML matrices/CallbackInfo objects or varargs arrays are created
by these hooks; the observer uses fixed primitive arrays and scalar operations.

## Reading the report

- `completedFrames` and `comparablePairs` count valid observed renderer frames
  and adjacent pairs with complete finite matrix probes and stable world/renderer
  identity. Missing probes, incomplete frames and identity/frame gaps are recorded.
- `observedEqualPairs` / `observedInputEqualityFraction` describe equality of
  the observed camera/world clocks/matrices/caster counts. They are **not cache
  hit rate or eligibility**. `exactReusableFrames` always stays zero.
- `changeCounts` overlap: camera origin/pose, clock/game tick, render/consumer
  transforms, light-orientation 3×3 matrix, caster counts, identity/gaps,
  missing/nonfinite inputs and source/consumer disagreement. Parent reasons are
  not exclusive categories; do not sum them into a frame count.
- `blockerCounts` fail closed for unobserved terrain/mesh, material/texture,
  callback and view/caster-visibility inputs. Player/entity/block-entity render
  directives block unobserved poses even when rendered counts are zero. Waving,
  caustics/voxel writes, unknown flags and unobserved weather inputs are distinct
  blockers. Equal positions/counts never establish equal poses, alpha or wind.
- `projectedReferenceDeltaTexels*` compares seven static world anchors using
  observed `projection × modelView`, remapping current camera-relative coordinates
  to the previous world origin. The anchors are the origin and ±32 blocks X/Z,
  ±16 blocks Y. Delta is converted from undistorted NDC to shadow texels at the
  observed resolution. Photon nonlinear distortion, caster silhouettes, receiver
  coverage, frustum changes, texture alpha and wind displacement are excluded.
  Quarter/one-texel counts are diagnostic proxies, not quality guarantees.
- `potentialTwoFrameCadencePairs` counts only the projection proxy ≤0.25 texel
  and observed renderer spacing ≤8.333333ms. It excludes no dynamic-caster or
  fidelity risks and does not simulate skipped frames. It indicates a narrow
  geometric/scheduling opportunity to investigate, not demonstrated savings.
- `engineBeginFinalizeMeanMs/MaxMs` measure the engine’s begin/finalize work with
  `System.nanoTime()`. They exclude mixin game-field reads, supplier handlers and
  matrix recording, and are neither total overhead nor CPU-thread time. Actual
  enabled/disabled runtime overhead still needs matched capture measurements.

Wetness/biome values, resource/source revisions and full caster poses are not
observed here. Reusing the wetness tracker report alongside this observer is
useful context but does not close those gaps. No exact eligibility switch exists
in the observer API, so a caller cannot turn an identical-input observation into
a rendering permission.

## Validation and runtime acceptance

Standalone checks without Minecraft/Iris/JOML/OpenGL:

```sh
mkdir -p build/dev-shadow-tests
javac -d build/dev-shadow-tests src/devClient/java/com/pp/shaderlod/dev/timing/ShadowInputObserver.java src/devClientTest/java/com/pp/shaderlod/dev/timing/ShadowObservationPolicyTest.java
java -cp build/dev-shadow-tests com.pp.shaderlod.dev.timing.ShadowObservationPolicyTest
./gradlew compileDevClientJava --console=plain
```

These pass disabled gating, observed equality with exact eligibility still
blocked, camera-relative coordinate cancellation, projected texel scaling,
light rotation and input reason separation, identity/frame isolation, missing
and nonfinite probes, warmup reset, player/waving/voxel/unknown flag blockers and
single-copy supplier gating. Build/source checks do not establish mixin
application or actual in-world overhead.

Coordinator runtime checks, still pending at implementation handoff:

1. Load original Photon with observer disabled; verify normal rendering and no
   diagnostic recording. Repeat with observer enabled and the same clocks/options.
2. Confirm both mixins apply without selector failure and renderer/consumer probes
   appear. Inspect counters under fixed camera/time, then unfrozen world and wind
   clocks. Exact reusable frames must remain zero in all cases.
3. Move/turn the camera, change worlds/reload shader pipeline, and include player,
   moving entities, water/caustics and voxel-feature rejection. Report reason
   changes and missing probes; do not freeze extra inputs to make equality improve.
4. Measure enabled versus disabled observer full-frame distributions and guard
   scope. Keep diagnostic captures separate from performance proof runs.
5. Use live-world projection/clock/pose reason rates and current shadow CPU/GPU
   contribution to decide whether a bounded cadence prototype is worthwhile.
   No rendering reuse is implemented by this observer.
