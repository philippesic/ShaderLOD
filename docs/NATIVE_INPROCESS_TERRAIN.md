# Native-resolution same-JVM terrain control

Source-only integration; runtime compilation/rendering remains pending.
`benchmarkFeature=terrain` holds the same B15 archive, pipeline, world, camera,
global enable and all five effect options fixed while alternating the existing
terrain policy flag. ON must actually use requested24/effective16; OFF must
restore requested24/effective24. Both retain DH128 and protected lighting.
Global/all-off, coherence and effects controls retain their existing strict modes.

The terminal four-JVM pilot5636 accepted all four arms, with original/off/
adaptive/geometry GPU15.2692/16.7714/15.0119/14.4778ms and thread CPU9.122/
8.796/8.975/7.406ms. Geometry was actually active16, but total gain was weak
despite reduced CPU time. This motivates a fixed-JVM diagnostic; it does not
identify a source culprit or establish the ordinary15% gate.

## Explicit selection and manual re-arm

The selected config field is `terrain.enabled`. Global `lod.enabled` remains
true. The existing controller revokes its scene acknowledgement when the policy
is disabled, so later ON phases require the existing `confirmCurrentScene()` API
to re-arm the already-inspected scene. The coordinator explicitly authorized
this once-only action for this diagnostic. This is **terrain toggle plus a
necessary recorded manual guard re-arm**, not a config-only intervention or an
automatic travel/coverage mechanism. The acknowledgement config remains true;
no main policy, API, renderer, shader, default or radius bound changes.

After initial warmup, the session captures a real initial observation and fixes
world/pipeline/camera object references, first-person pose/FOV, requested radius and control/config
identities. Each ON phase polls cached eligibility only during its bounded
settling interval. Once eligible, it validates the live B15/DH/global/manual
prerequisites and unchanged identity, then calls `confirmCurrentScene()` once.
The phase records success, call count, UTC/relative timing, an opaque scene label
and its actual pre-call input observation. Failure, missing eligibility, a late
re-arm or changed scene rejects the run. It never retries a failed call or
confirms a new scene after travel. OFF performs no confirmation.

The scene label identifies runtime reference/pose/control checks within the
session. Raw Java object IDs and continuous cache coverage are not exported;
the label alone is not independently reconstructed world/pipeline evidence.

## Required inputs and settling

- Exact no-atmosphere B15 SHA256
  `b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c`.
  All five CLOUDS/WATER/FOG/SSR/MATERIALS switches true; USE_MOD_CONFIG and
  PRESERVE_LIGHTING true; DEBUG/CACHED_SKY/WATER_ANALYTIC false. Complete maps and
  every other shader/config input remain fixed. Inactive upstream features are
  still inactive; a true module flag does not establish executed effect work.
- Global enabled, actual ShaderLOD/Iris/DH present, live Iris without fallback,
  hidden HUD, cap16, requested24, manual warm acknowledgement true and policy
  warmup30seconds. Every CPU culling/list/coherence experiment and diagnostic
  observer is off. No per-profile control-file terrain/global overrides.
- Actual native2976×1672 and unchanged window/pacing. Public pinned DH API
  boundary observations must report loaded world, normal DEFAULT rendering,
  rendering enabled and reach128 even in OFF phases. Readiness is not a terrain
  submission or replacement-ring coverage proof.
- At least policy warmup+30seconds per settling interval; recommended75seconds.
  ON additionally requires at least60seconds between its successful re-arm and
  measurement. A re-arm too late to fit the configured interval rejects rather
  than silently starting an unsettled capture. Measurement must be at least
 30seconds. Renderer rebuild/upload settling still needs inspection.

`WorkloadInputs.terrainInputState()` exports actual controller status at **both**
measurement boundaries. No start policy is fabricated from end data. ON requires
active16, cap16/requested24, warmupTicks600, stableTicks≥600 and the active reason;
OFF requires inactive24, stableTicks0 and the disabled reason. Advancing stable
tick counts are retained as observations but excluded from strict semantic
equality; a counter reset during capture rejects. Only selected terrain enable,
derived radius/activity and the corresponding policy reason differ between
states. All other config, options, camera/control, DH and window fields remain
identical. Runtime checks also preserve world/pipeline and fixed camera identity
at existing ticks; changes between observations are not a continuous proof.

The session restores the original terrain flag, global enable and HUD state on
completion/failure. If the final OFF phase revoked acknowledgement, restoring
an originally true flag leaves the existing guard fail-open until a later
explicit inspected-scene confirmation. The report verifies flag restoration;
it does not claim the original effective cap instantly resumed.

## Coordinator-only execution after freeze

No compiler, client or GPU was run by the source owner. First stop other GPU
jobs, integrate the frozen dev sources, then compile with the coordinator's
usual checks. Use a fresh inspection-qualified clone of the warmed geometry
profile, preserving earlier pilot/world/report evidence. The existing
`native-terrain-b15-r2/forest/geometry` supplies the audited config/source, but
root must choose and record the fresh runtime directory before timing.

The exact launch properties for the coordinator are:

```sh
./gradlew runClientLodOn \
  -PrunDirectory=FRESH_PROFILE -PperformanceStack=minimal -PincludeDh=true \
  -PgpuBenchmark=true -PbenchmarkOnly=true -PbenchmarkFeature=terrain \
  -PbenchmarkStartEnabled=false -PinitialWarmup=75 \
  -PbenchmarkSettleSeconds=75 -PmeasurementSeconds=30 \
  -PgpuSampleInterval=16 -PgpuPassTiming=false -PcpuLayerTiming=false \
  -PfixedWetness=0 -PbenchmarkWindowScale=2
python3 scripts/analyze_inprocess_control.py FRESH_PROFILE/benchmark.json \
  --expected-shader-sha256 b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c \
  --profile FRESH_PROFILE --output dev/logs/EXPERIMENT/terrain-analysis.json
```

Only root launches these commands. For the reverse direction, select
`-PbenchmarkStartEnabled=true` and a separate immutable profile/report. Keep
source/code/artifacts fixed during a session. `-x compileDevClientJava` is useful
only when intentionally keeping a **previously compiled** harness frozen; it
cannot enable this new mode until the coordinator has compiled its source.

The analyzer requires explicit terrain feature/method/selected flag, B15 and
pinned loaded runtime versions, real initial/boundary policy, restored original
flag, fixed manual acknowledgement, bounded re-arm identity/timing, complete
options/config, native window observations and existing timing/image/wetness
checks. Missing/inactive/warming policy, effective24 ON, effective16 OFF, changed
DH/camera/control, window transitions/unknowns, repeated/missing confirmations
and insufficient settling reject. Other modes do not inherit radius exceptions.

Interpret the two adjacent contrasts and same-state drift as correlated phases,
without independent-JVM or frame-derived confidence intervals. Keep GPU, CPU and
derived frame duration separate. Any reduction is the existing detail-radius/DH
replacement tradeoff at fixed shader enable; never label it shader-only savings
or a novel mesh renderer. Actual ring/shadow/water quality, travel coverage,
restoration, repeated ordinary performance and human default/tradeoff choices
remain separate acceptance work. No goal/default/support promotion follows.

Source acceptance: 29 lightweight in-process fixtures passed, including all20
prior modes, forward/reverse terrain, warm policy and missing-boundary rejection,
once-only fixed-scene re-arm, long/post-rearm settling, original-flag restoration,
exact options/config/DH/window/session identity and static Java route checks.
All24 stack analyzer fixtures also passed after the shared semantic helper edits.
Java compilation and actual terrain four-phase rendering remain coordinator work.
