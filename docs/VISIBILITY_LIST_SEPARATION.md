# Experimental inner visibility-list separation

Implemented in isolated worktree
`/Users/pippo/.codex/worktrees/visibility-lists/shaderlod`, starting at `edffbef`.
No main-checkout edits, Gradle build, game launch or benchmark was performed
during the coordinator's frozen GPU matrix. After the coordinator released a
CPU window, Java build/dev compilation and ownership fixtures passed. Actual
Mixin/rendering validation remains pending the coordinator's runtime check.

This is a default-off **correctness prerequisite for later reuse**, not a
performance optimization. It neither skips nor caches any traversal. Existing
graph dirtying, generation increments, task collection/submission, uploads,
frame stamps, clipping, mesh/geometry quality and draw APIs remain unchanged.

## Implementation and source contract

Pinned Sodium 0.6.13 `RenderRegion` owns one persistent mutable
`ChunkRenderList`. Its public constructor takes `RenderRegion`; the getter
returns that original list. Pinned Iris 1.8.12 has separate outer shadow lists,
but collector membership still aliases those region objects. Exact source
revision, local ignored review files and annotated bytecode are recorded in
`VISIBILITY_REUSE_AUDIT.md`.

New production components:

- `ShadowListStorage<T>` holds the original and lazily constructs one alternate
  shadow list. There is no static owner map or saved world/manager reference.
- `ShadowRegionListsMixin` overwrites only Sodium `RenderRegion.getRenderList`.
  Disabled/main paths return the original field. An active shadow getter lazily
  creates owner-local storage and `new ChunkRenderList(thisRegion)`.
- `ShadowVisibilityListsController` caches one active boolean. Per-region
  getters perform no config/Iris pack inspection, GL call or reflection.
- `ShadowVisibilityListsRuntime` reads config and existing Photon names once
  per client tick, requiring global enablement, an in-world shader pack,
  recognized Photon and no Iris fallback.
- `rendering.separateShadowVisibilityLists=false` is the new experimental
  default. NeoForge's client tick listener and both mixins are registered.
  Sodium's pinned artifact is added to main compile-only inputs; its runtime
  remains required by Iris exactly as before.

Lists are owner-local and allocated only on the first eligible shadow visit.
The noncapturing constructor function is initialized once on the transformed
class, not per getter. This adds a storage object, one shadow list and its three
`byte[256]` arrays per shadow-visited region; roughly 0.9–1.1 KiB per region
with typical compressed-reference HotSpot layout, an estimate pending actual
heap inspection. Main/disabled getters retain the original object identity.
Disabling/re-enabling keeps the same alternate storage for that live region;
forced traversals still repopulate it. World/manager replacement constructs
new regions/storage, allowing the old graph to be collected normally.

## Exact existing sprite-consumer behavior

This requirement prevents a superficially correct list split from silently
changing animated-shadow texture activation.

Pinned `RenderSectionManager.tickVisibleRenders` iterates its **main outer
container**, even during shadow setup. In the original shared-list design,
regions visited during that shadow traversal have their inner arrays overwritten
with shadow membership; regions not visited keep their preceding main data.
Regions not in that main outer container are not visited by this sprite tick.

`ShadowSpriteListsMixin` redirects only its
`ChunkRenderList.sectionsWithSpritesIterator()` invocation. In an active shadow
phase, for each existing main-container entry it returns the shadow iterator
only when that owner's alternate list has the current manager
`lastUpdatedFrame`. Otherwise it returns the original main iterator. The caller
continues using the same region and same section/sprite iteration. Main and
disabled paths invoke the original iterator directly. No region is added to
the outer container; no stamp is rewritten; no sprite is ticked by a new hook.

This reproduces the original shared-array selection using existing generation
provenance. It is not a change to "tick all shadow sprites" or broad animation
policy. Frame-wrap equality follows the same original integer-generation
comparison; no elapsed-time or camera-distance substitute is introduced.

The membership fixture models main regions A/B/C and shadow visits B/D, with
an older shadow list for C. Legacy shared arrays and separated sprite selection
both produce `[A sprites, current B shadow sprites, C main sprites]`; D is
excluded because it was not in the original main container. This test passed.
Actual transformed generation/order behavior still
requires runtime evidence.

## Developer snapshots

`src/devClient/.../VisibilityListDiagnostics.java` exposes an explicit
`capture(RenderRegion)` method only; no rendering/timing hook invokes it.
`-PvisibilityLists=true` maps to `shaderlod.dev.visibilityLists=true`. Without
that property, capture returns null before lookup/iteration. With it, an
explicit caller records region/list identities, generation, geometry/sprite/
entity counts and ordered membership fingerprints. Reflection occurs only
in this developer snapshot and supports an absent-main-mod baseline: lacking
the injected accessors, it captures the original region getter with no shadow
list. Snapshotting allocates and should be used outside timing captures.

No automated runtime capture hook or performance-arm instrumentation was added.
The coordinator must arrange calls at chosen phase boundaries to compare the
same owner before/after main and shadow traversals. An existing alternate list
is not itself proof the feature is currently enabled or its generation current.

## Pending acceptance and limits

`ShadowListStorageTest` contains four bounded fixtures for lazy creation,
mixed-pass mutation/reset and toggles, independent replacement ownership, and
sprite parity against the old shared-array model.
`./gradlew build compileDevClientJava` passed after the coordinator released a
CPU window. All 24 JUnit tests passed, including the four new ownership fixtures.
Production jar inspection confirms developer snapshots are excluded.
The first compile caught a wrong iterator type in this worker's redirect:
Sodium returns `net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator`,
not fastutil's similarly named interface. The import and exact invocation
descriptor were corrected and checked against the pinned JAR with `javap -s`.
The final build passed; that correction is not runtime Mixin evidence.
Static inspection confirmed
public constructor/getter/iterator signatures; it does not prove Mixin runtime
application, other-mod compatibility or graphics correctness.

Next validation should:

1. Compile main/dev code and run the focused ownership fixtures. Check injected
   field/method signatures and property/config defaults. Preserve the original
   failed/unsupported transform evidence if any; do not label startup as render.
2. Run a separate diagnostic session with the feature disabled/enabled, original
   Photon quality unchanged, forced traversals retained. Compare ordered
   caster/region membership and sprite selections at phase boundaries.
3. Exercise camera motion, missing/new regions, block edits, async build/sort
   completion, renderer/world/shader reloads and toggles, plus optional DH and
   optimizer stacks. Confirm task progress, casts and list identities.
4. Measure default-off and active branch/memory cost separately. Expect no
   traversal reduction and make no speedup claim from this prerequisite.

Shared `RenderSection.lastVisibleFrame`/incoming directions and manager epochs
remain shared and unchanged. They still block naive traversal reuse. Complete
producer/consumer modeling from the audit is required before any subsequent
skip. Eligibility is cached on ticks; the engine experiment is limited to the
current pinned layouts and tested Photon names, not advertised as a universal
Sodium/Iris compatibility layer.
