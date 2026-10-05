# Sparse visibility-list phase observer

Development diagnostics only. The observer runs after original
`RenderSectionManager.createTerrainRenderList` traversals and never skips,
cancels or changes them. It does not write world/list/sprite state, issue GL work,
read back GPU data or authorize visibility reuse. Keep it out of performance
profiles; its bounded reflection, iterators, hashing and snapshot allocations
are deliberate diagnostic overhead.

## Coordinator wiring

Register dev mixin class `VisibilityListPhaseMixin`.
Set JVM property `shaderlod.dev.visibilityLists=true` for dedicated diagnostic
captures only. Default is false. Property-disabled and unarmed callbacks return
before phase/world/list reads or reflection. Class initialization allocates
empty report storage once. Report serialization allocates outside the callback.
The non-cancellable RETURN mixin itself has ordinary Mixin callback dispatch;
this is not a claim that registering the mixin eliminates all framework overhead.

Use the public API on
`com.pp.shaderlod.dev.timing.VisibilityListPhaseObserver`:

```java
VisibilityListPhaseObserver.beginMeasurement(); // After warmup and actual option/config checks.
VisibilityListPhaseObserver.snapshot();         // Immutable report; does not perform live snapshots.
VisibilityListPhaseObserver.endMeasurement();  // Disarm and release borrowed live references.
```

A property alone does not arm sampling. `beginMeasurement()` resets reports and
arms the next eligible shadow traversal. Capture at most eight attempts/pairs,
at least 30 render frames apart. Failed/unmatched attempts consume the budget;
they are retained as unknown/error information rather than silently retried
until eight successes. A pending sampled shadow may pair with a main traversal
within two game render frames on the same manager/world. Another shadow
traversal first discards the sample, avoiding false attribution of shadow
rebuild changes to the main pass. Manager/world changes, backward frame IDs or
larger gaps similarly discard pending pairs. If main graph traversal does not
occur, report that absence rather than treating a normal draw as a rewrite.

## Pinned hook and field access

Artifacts: Sodium 0.6.13 / MC 1.21.1, SHA1
`38af70fa4dc4b2aaac636e92fdba3bedd5a025e1`; Iris 1.8.12 / MC 1.21.1, SHA1
`a3e6355915c7d3b2bc392724795113e51d289378`.
Audited source/disassembly is in ignored `dev/upstream/visibility-audit/`.
The strict RETURN selector is:

```text
RenderSectionManager.createTerrainRenderList(Camera, Viewport, int, boolean): void
```

The dev mixin directly reads pinned main `renderLists`, `lastUpdatedFrame` and
`level`. It separately reads Iris's shadow outer list through cached dev-only
reflection. The pinned Iris jar declares a private `@Unique shadowRenderLists`
field; audited naming candidates are `iris$shadowRenderLists` and
`shadowRenderLists`, with exact SortedRenderLists type required. The report
records the actual resolved field. Missing/changed fields fail to unavailable;
there is no fallback that substitutes main outer lists for shadow lists.

Both outer lists are read at the phase boundary. Existing
`VisibilityListDiagnostics.capture(RenderRegion)` supplies original/shadow
inner membership snapshots and supports the physical absent-mod baseline.
The observer also snapshots the actual ChunkRenderList objects referenced by
Iris's shadow outer list and re-reads those same borrowed references after main
traversal. That distinguishes stable outer-container identity from mutable
shared inner membership. All borrowed objects are dropped after pairing,
discarding, or `endMeasurement()`; completed reports contain only values.

## Bounds and report schema

Each outer scan is capped at 512 region entries. Up to eight inner regions are
selected from shadow outer order. Sprite models are capped at 4,096 section
indices per phase and per compared selection. Region membership arrays are
bounded by pinned Sodium's 256-section region implementation. Reports retain
at most 16 error messages plus total error counts. An incomplete outer scan or
sprite budget overrun is explicitly marked; prefix hashes cannot establish
complete-list preservation.

`Report` contains enable/arm state, bounds, phase calls/attempts/pair count,
unmatched samples/errors, actual Iris field availability, optional ShaderLOD
controller availability, a pending frame and `pairs`.
Each `Pair` contains shadow/main render frame IDs, manager/traversal generation
numbers, manager/world identity tokens, four outer summaries, region rows,
shadow/main sprite models and an explicit diagnostic-only scope.

Outer summaries distinguish:

- Outer object identity and ordered region/list identity hash.
- Ordered region chunk-coordinate hash.
- Ordered source-generation/geometry/sprite/entity count hash.
- Scan completeness and unavailable/truncated status.

For sampled region rows, source/original/shadow membership preserves list
identity token, generation, counts and ordered geometry/sprite/entity index
hashes before/after main traversal. Flags report separate shadow storage,
shadow fingerprint unchanged, original fingerprint changed and borrowed shadow
source fingerprint changed. `mainOuterOverlap` is true/false for a complete
main scan; null means no overlap seen in an incomplete prefix. The first eight
shadow regions are a sample, not proof about every overlapping region.

Java identity tokens/hashes are process-local and may collide; they are
inspection aids, never correctness/permission keys. Coordinate hashes are
portable order descriptors, but generation/count hashes need matching phase
and state context. Do not compare raw identity values across separate clients.
Fingerprints do not establish mesh revisions, GPU draw parity or image quality.

## Sprite-selection model and its limits

The read-only model visits main outer region lists, as pinned
`tickVisibleRenders()` does. It reads public `ShadowVisibilityListsController.isActive()`
when available and optional per-region shadow accessors. In a shadow phase it
models selecting the separate shadow list only when its generation matches
manager `lastUpdatedFrame`; otherwise it uses the original list. With ShaderLOD
absent/inactive, it models shared original-list selection. Lookup failures are
unknown/error evidence, not proof of baseline semantics.

The comparison hypothesis is independently derived from the *actual current
Iris shadow outer traversal*: for a main-container region visited in that
shadow generation, its current shadow outer membership represents what the
original shared array would have received; otherwise use original membership.
Hashes include region chunk coordinates, sprite-section count and ordered local
section indices. The report includes source choice counts, bounded membership
hashes and nullable `matchesCurrentTraversalSharedHypothesis`.

This is a model of section selection, not instrumentation of actual
`SpriteUtil.markSpriteActive` execution, sprite array contents, animated alpha,
texture updates or GPU draws. Different earlier shared-array histories can
also differ from this current-traversal hypothesis. A matching model does not
verify the redirect actually executed or certify visual parity; inspect real
water/animated texture behavior separately.

## Source/fixture evidence and runtime acceptance

Temporary stubs and fixtures under `/tmp/shaderlod-visibility-fixture/` compile
the actual observer plus existing diagnostic helper without a game. Enabled
and default-disabled JVM fixture runs passed:

- No capture/reflection before explicit arming or while property-disabled.
- Separate shadow fingerprint remains intact after original main rewrite.
- Shared baseline borrowed inner membership changes despite stable outer identity.
- Generation-aware sprite model agrees with independently supplied current
  shadow traversal membership in the fixture.
- Intervening shadow traversal is rejected, sampling stops after eight attempts,
  and disarm clears pending references.

These establish the bounded bookkeeping/model, not actual transformed Sodium
fields or injection application. No Gradle build or Minecraft client was run by
this worker. Coordinator must compile/register and run original absent-mod,
mod-present disabled, and enabled separation diagnostic profiles. Require actual
field resolution, completed overlapping region pairs and source generations;
record unknowns if static main traversal does not occur. Confirm shadow hashes
remain stable in enabled overlapping rows while originals can change, and retain
shared-baseline mutation evidence. Check sprite model and actual animation
visually. Performance proof runs must keep the diagnostic property false.
