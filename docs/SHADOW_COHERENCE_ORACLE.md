# Shadow frustum coherence correctness oracle

Development diagnostic captures only. It compares actual Iris/Sodium `testAab`
boolean results with the pinned original classifier and records real coherence
helper outcomes. It never changes a result, cancels traversal, alters matrices,
textures, camera, shader options or world state. Oracle captures intentionally
perform extra original plane/distance work and cannot be used as performance
proof. Keep the property false in every timing run.

## Coordinator registration and API

Register these dev mixin classes:

```text
ShadowCoherenceOracleMixin
ShadowCoherenceOracleMixin$Reversed
```

Map Gradle `-PshadowCoherenceOracle=true` to JVM property
`shaderlod.dev.shadowCoherenceOracle=true`. Both main counters and dev oracle
read a **final, default-false** property gate. Property changes after class
initialization do not toggle this diagnostic. Disabled AABB hooks return before
original reevaluation or main-class resolution. No config reads, GL calls,
reflection, allocations or clock reads are added by production per-call counter
hooks. One-time diagnostic class initialization/report allocations are separate.

Root owns property/mixin/Workload wiring. Public dev API:

```java
ShadowCoherenceOracle.beginMeasurement(); // After warmup; resets/arms both counters when available.
ShadowCoherenceOracle.snapshot();         // Serialize before disarming to retain latest identities.
ShadowCoherenceOracle.endMeasurement();   // Disarm and release retained frustum/helper references.
```

The dev class has no direct reference to the main diagnostic class. It resolves
`ShadowFrustumCoherenceDiagnostics` via cached reflection only at an enabled
measurement boundary, calling `beginMeasurement`, `snapshot`, `endMeasurement`.
Physical absent-main-class captures still run the original-only dev oracle and
report counter unavailability explicitly. Class availability alone does not
prove the mod or production mixins loaded; use the runtime mod/feature report.

## Production helper instrumentation scope

Only `ShadowFrustumCoherence.java` received instrumentation. Its cache arrays,
slot count/key/hash, containment guards, plane arithmetic/grouping, distance
proof, state updates and return behavior remain unchanged. Final-property-gated
calls record:

- Every actual `canAccept` request.
- New positive proof accepts and existing positive-proof cache-hit accepts.
- New negative proofs and cached negative proofs returning to exact fallback.
- Invalid AABB and non-containing coarse-box fallback.
- Actual context resets and proof invalidations.

Counters are client-thread aggregate scalars, armed only at the diagnostic
boundary. There are no per-request records/timers/identity hashes, no new proof
cache structure and no automatic policy change. Snapshot performs the latest
helper identity lookup and allocation. Request and invalidation sequences are
**global measurement counters**, not per-frustum generations. Main helper count
and latest plane-count/FMA/distance metadata are separate from dev frustum
metadata. Infinite/NaN/unobserved limits serialize as null numeric values plus
an explicit limit-kind string, avoiding invalid JSON floats.

A cached negative proof only records that the coarse proof failed; it does not
reject a child or claim the fallback child was invisible. Therefore helper
`totalAccepts + totalFallbacks == canAcceptRequests` under normal completed
calls. Helper counts can remain zero when the feature is inactive, no supported
frustum uses it, or a main-mod class is absent.

## Pinned original oracle and binding safety

Pinned Iris artifact: `iris-neoforge-1.8.12+mc1.21.1.jar`, SHA1
`a3e6355915c7d3b2bc392724795113e51d289378`.
`javap -c -p` confirms the original entry points:

```text
AdvancedShadowCullingFrustum.testAab(float×6): boolean
  primary box rejects → false; otherwise original checkCornerVisibility > 0

ReversedAdvancedShadowCullingFrustum.testAab(float×6): boolean
  distance box rejects → false
  primary box accepts → true
  otherwise original checkCornerVisibility > 0
```

MixinExtras `ModifyReturnValue` observes every executed RETURN, evaluates that
original prefix/plane decision, records it and returns the identical actual
boolean. It does not call `testAab` recursively. Multiple possible RETURN
instructions still produce one observation per executed call. The original
protected `checkCornerVisibility` remains unchanged by the main optimization.
The oracle checks both original INSIDE and INTERSECT as visible (`>0`) without
using a coarse INSIDE result as a cached child proof.

Both dev mixins use priority 900, after the main default-priority 1000 overwrite,
so their RETURN observation should survive the main body replacement. Actual
transformation/application still requires a runtime check; a source build alone
cannot establish observation count or compatibility with other overrides.

The BASE dev mixin declares only members owned by the BASE pinned target:
`boxCuller`, `checkCornerVisibility`, `planeCount`, `FMA_SUPPORT`, x/y/z. It
injects a dev-only `OriginalAccess` interface supplying corner/primary helpers
and metadata. Its private generation counter advances on existing `prepare`
and sole `addPlane` RETURN events while the property is enabled. It is a local
observed mutation sequence, not a world/mesh revision or globally unique epoch.

The reversed dev mixin declares **only** its own `distanceCuller`. It calls the
inherited dev interface for base primary acceptance/corner classification and
metadata. It never declares inherited `@Shadow` fields/methods on the subclass,
avoiding the earlier inherited-member binding problem. This interface is dev
owned and works without any main-mod PlaneAccess method/class.

## Report and limits

`Snapshot` contains enable/recording state, `base` and `reversed` class counts,
bounded mismatch/error examples, main counter availability and optional main
counter snapshot. Each class counts all armed completed comparisons and all
four actual/original boolean combinations, mismatches and unavailable-original
observations. It records the latest actual frustum class/identity, render-frame
ID, local prepare/plane generation and plane count.

At most eight mismatch examples retain class/identity, frame/generation,
plane count/FMA policy, prepared origin, both outputs, original prefix branch
and AABB endpoints. Raw float endpoint bits preserve signed zero/NaN details;
nonfinite endpoint/origin numeric fields serialize as null. Examples do not
retain whole frusta or world objects. Exceptions from diagnostic reevaluation
are counted/reported, and the actual optimized decision is still returned.
Unavailable original classification is not counted as a successful equality.
No mismatch is automatically repaired, hidden, or used to turn the feature off;
the coordinator must reject a candidate capture containing mismatches/errors.

This checks boolean decisions for observed AABBs, not shader/GPU fidelity,
ordered final caster sets, mesh uploads or every possible float/input. Zero
mismatches require nonzero comparable observations and useful real helper
accept/cache-hit counts; all-fallback or missing-hook success is insufficient.
Frustum identities are process-local diagnostics, not permission keys.

## Fixture evidence and runtime acceptance

Tiny fixtures in `/tmp/shaderlod-coherence-oracle-fixture/` compiled the actual
main helper/counter and dev aggregate engine, without Minecraft or OpenGL.
Enabled/default-disabled JVM runs passed all six helper outcome branches,
request/reset/invalidation accounting, unchanged output against the reviewed
worker helper on 100 small boxes, mismatch bounds/output counts/raw bits,
JSON-safe limits, measurement disarm and disabled main-binding gates. A separate
JVM classpath containing no main classes passed absent-main boundary handling.
Text checks confirmed the plane proof arithmetic, cell/slot helpers and matches
predicate remain identical to the reviewed worker source. These are source/tiny
fixture checks; no Gradle build, transformed mixin or game was run by this worker.

Coordinator runtime plan:

1. Compile/register and smoke original absent-mod, present-disabled and coherence
   enabled captures with identical Photon options/world/camera. Verify both BASE
   and reversed bindings where actually used; explicit unused-class zero counts
   are acceptable only with another targeted runtime test for that class.
2. Arm after warmup, collect nonzero AABB observations and actual helper requests,
   new proofs/cache hits/fallbacks/invalidations. Require zero mismatches and zero
   unavailable-original errors. Retain failed examples rather than ranking their
   timing or silently changing camera/program inputs.
3. Exercise prepared origin/camera movement, negative coordinates, tangent
   geometry, daylight/night transitions, shader reload, world changes, request
   radius/DH variants and minimal/optimized stacks. Keep options unchanged within
   each controlled arm. Confirm invalidations coincide with observed preparation
   or context changes and do not imply temporal traversal reuse.
4. Capture ordered caster/region membership and inspect images separately.
   Then run repeated serial performance controls with this property **false**;
   diagnostic plane reevaluation/counters cannot substantiate a speedup.
