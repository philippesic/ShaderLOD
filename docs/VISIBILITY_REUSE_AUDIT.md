# Sodium/Iris visibility reuse audit

Read-only source/bytecode investigation, 2026-10-04. No code, game, build or
benchmark changes were made. The failed/net-unhelpful positive-vertex math
experiment is a separate result; this audit concerns list traversal/lifetimes.
JFR samples motivate inspection but do not quantify traversal milliseconds.

## Inputs and reviewable sources

Sodium's release tag `mc1.21.1-0.6.13` resolves to commit
`8672650501117e72f5a809867092378fb5bc908a`. The downloaded source below matches
the inspected pinned 0.6.13 JAR's relevant methods/branches. The exact development
JAR remains the runtime authority (`Pb3OXVqC` in `dev/runtime.lock.json`).
[Sodium release](https://github.com/CaffeineMC/sodium/releases/tag/mc1.21.1-0.6.13).

Ignored source files downloaded from that commit, available for local review:

- `dev/upstream/visibility-audit/SodiumWorldRenderer.java`
- `dev/upstream/visibility-audit/RenderSectionManager.java`
- `dev/upstream/visibility-audit/OcclusionCuller.java`
- `dev/upstream/visibility-audit/VisibleChunkCollector.java`
- `dev/upstream/visibility-audit/RenderSection.java`
- `dev/upstream/visibility-audit/RenderRegion.java`
- `dev/upstream/visibility-audit/ChunkRenderList.java`
- `dev/upstream/visibility-audit/SortedRenderLists.java`
- `dev/upstream/visibility-audit/LevelRendererMixin.java`

Their upstream paths start at `common/src/main/java/net/caffeinemc/mods/sodium/`:
`client/render/SodiumWorldRenderer.java`,
`client/render/chunk/{RenderSectionManager,RenderSection}.java`,
`client/render/chunk/occlusion/OcclusionCuller.java`,
`client/render/chunk/lists/{VisibleChunkCollector,ChunkRenderList,SortedRenderLists}.java`,
`client/render/chunk/region/RenderRegion.java`, and
`mixin/core/render/world/LevelRendererMixin.java`.

Iris's exact artifact is NeoForge 1.8.12 (`t3ruzodq`), SHA256
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`.
Its embedded snapshot/local version is already documented. No matching source
revision was independently established in this audit; do not substitute a
newer branch's behavior. Verbose `javap -p -c -v` of the actual JAR, including
Mixin annotations and source line tables, is saved in these ignored files:

- `dev/upstream/visibility-audit/ShadowRenderer.javap.txt`
- `dev/upstream/visibility-audit/MixinRenderSectionManagerShadow.javap.txt`
- `dev/upstream/visibility-audit/MixinSodiumWorldRenderer.javap.txt`
- `dev/upstream/visibility-audit/MixinLevelRenderer.javap.txt`

Relevant class namespaces are `net.irisshaders.iris.shadows.ShadowRenderer`,
`net.irisshaders.iris.compat.sodium.mixin.*`, and
`net.irisshaders.iris.mixin.shadows.MixinLevelRenderer`.

## Current reuse and the forced shadow traversal

Sodium already avoids rebuilding a clean visibility graph. In
`SodiumWorldRenderer.setupTerrain`, lines 197–207 compare exact camera position,
pitch/yaw, fog distance and projection matrix. A change marks the graph dirty.
The update loop at lines 223–240 calls `RenderSectionManager.update` only if
`needsUpdate()` is true. Manager update builds lists then clears
`needsGraphUpdate` (`RenderSectionManager.java:131–136`). Thus unchanged-camera
static-world reuse exists upstream; a blanket "cache visibility" feature
would duplicate it.

However, the pinned Iris shadow path forces a traversal even when those
ordinary inputs are unchanged:

1. `ShadowRenderer.renderShadows` calls `LevelRenderer.needsUpdate()` before
   shadow terrain setup, bytecode offset 399.
2. Sodium's `LevelRendererMixin.java:113–116` injects at that method's RETURN
   and calls `SodiumWorldRenderer.scheduleTerrainUpdate()`.
3. That method marks its manager dirty (`SodiumWorldRenderer.java:144–149`).
4. Shadow setup then reaches the ordinary update loop. Iris's
   `iris$forceChunkGraphRebuildInShadowPass` also rebuilds when interpolated
   sun angle changes, otherwise returns the manager's dirty flag.
5. Iris's `cancelIfShadow` injects **after** `createTerrainRenderList` inside
   manager update, not at its HEAD. It returns before
   `needsGraphUpdate=false`. The shadow traversal has happened, and the dirty
   flag intentionally remains set for the later main traversal.

Consequently the shared flag is not safely "cleared too early" by shadows;
Iris explicitly preserves it. The unconditional dirtying plus preservation
can force both shadow and main traversals. Count the actual producer chain
before calling it waste: part of the behavior maintains shared mutable lists.
Iris's sun-angle gate alone cannot defeat the unconditional mark. The separate
`iris$forceEndGraphRebuild` makes the shadow update loop stop after one pass.

Iris already maintains separate outer `shadowRenderLists` and `shadowTaskLists`
in `MixinRenderSectionManagerShadow`. Its redirects cover list/task writes in
creation/reset, render-layer/list/count reads and task submission. It cancels
`updateChunks` and `uploadChunks` at HEAD while shadows are active. The vanilla
LevelRenderer save/restore mixin swaps its vanilla visible sections and camera
rotations; it does not snapshot Sodium's entire state.

## The hidden ownership problem: inner lists are shared

Separate outer lists are insufficient for reuse:

- `RenderRegion.java:46,60,209–210` owns one persistent `ChunkRenderList` and
  returns it from `getRenderList()`.
- `VisibleChunkCollector.java:43–53` obtains that object, resets it for the
  current traversal generation, adds section membership, and stores the same
  reference in its outer list.
- `createRenderLists` sorts those same objects (`VisibleChunkCollector.java:74–113`).
- `SortedRenderLists.java:14–17` merely stores an array of those references;
  it does not deep-copy section arrays.
- No inspected pinned Iris mixin separates the region's inner list. The JAR's
  Sodium region mixin concerns arenas rather than this list ownership.

A main traversal can therefore overwrite the membership/order/counts inside
an older shadow outer container, and a shadow traversal can mutate objects
referenced by a main container. Reusing one outer list after the other phase
has run is not an immutable snapshot. This is a concrete reason not to cancel
the force-dirty call or skip `setupTerrain` based solely on camera/sun equality.

## Safe narrow prerequisite: separate per-region inner lists

The ownership API needed for a default-off no-skip prototype is small:

| API / source | Role |
| --- | --- |
| `new ChunkRenderList(RenderRegion)` / lines 30–32 | Public constructor, ordinary Java arrays; no GL resource allocation |
| `reset(int frame)` / lines 34–40 | Resets geometry/sprite/entity counters and size; stores traversal generation |
| `add(RenderSection)` / lines 80–98 | Appends local section IDs according to current flags |
| `sortSections(SectionPos,int[])` / lines 46–77 | Reorders geometry membership by camera-relative region distance |
| `sectionsWithGeometryIterator`, sprite/entity iterators | Consumers of mutable membership arrays/counts |
| `RenderRegion.getRenderList()` / lines 209–210 | Single bounded substitution point for phase ownership |

Retain the original region list for the main phase. Lazily create one additional
list owned by the same region for the shadow phase, return it only when the
explicit feature/Photon eligibility cache and Iris shadow phase are active.
Keep all traversals, graph dirtying, generation increments, queue cleanup,
build scheduling and uploads unchanged. No cache skipping is authorized by
this prerequisite. Disabling must return the original getter behavior; the
existing forced traversals repopulate containers on each following phase.

Each list owns three `byte[256]` membership arrays: 768 raw bytes plus object
and array headers/counters. A typical compressed-reference HotSpot layout is
roughly 0.85–1 KiB additional heap per shadow-visited region, not a measured
host size. Region lifetime owns the extra list; avoid a static map that retains
unloaded regions. Lazy construction avoids paying for regions never used by
shadows. The getter adds cached feature/phase branches; construction is once
per region, not once per frame. Sorting already allocates a small histogram
per list (18 ints for the pinned 8×4×8 region shape); this prototype does not
remove that cost or promise faster frames.

Actual consumer checks are still required. In particular
`tickVisibleRenders()` reads main `renderLists` to activate animated sprites
(`RenderSectionManager.java:252–275`), and the inspected Iris shadow redirects
do not include that method. Shared-array mutation currently affects what it
sees during shadow setup. Isolation must inventory and deliberately preserve
the required sprite activation for both visible main surfaces and animated
shadow casters rather than silently dropping one set. Test counts/order and
animations; do not claim list isolation alone is perfectly behavior-neutral.

Direct Sodium types require the exact pinned Sodium compile-only artifact in
main compilation; existing runtime dependencies do not imply its classes are
already on the main compile classpath. Keep the production dependency policy
consistent with Iris's mandatory Sodium runtime.

## Dirty producer and consumer inventory

| Input/producer | Existing action | Required reuse treatment |
| --- | --- | --- |
| Manager creation/reload/world replacement | Manager starts dirty; old sections/resources destroyed | Bind all cache state to manager + world identity; never transfer it |
| Exact camera position/orientation/projection/fog change | Public `markGraphDirty`, world-renderer lines 197–207 | Separate phase keys; preserve original numeric comparisons and camera-relative transform |
| `LevelRenderer.needsUpdate`, explicit terrain update | Public mark chain above | Record reason; only suppress the precisely identified redundant shadow force after prerequisites |
| Section add/remove | Direct true writes, manager lines 213/240 | Invalidate both phase memberships, neighbor topology and queued work |
| Rebuild scheduled | Pending update promoted and direct true write, lines 598–615 | Task collection and geometry epoch; graph cannot be frozen while a build is pending |
| Build outputs uploaded | OR graph dirty with `processChunkBuildResults`, lines 295–308 | Both phases see changed built state/flags/connectivity/global entities |
| Section info update | `setRenderState` compares built/flags/visibility, `RenderSection.java:150–165` | Preserve selective dirtying; ordinary mesh/index updates still flow through region storage |
| Sort tasks/results and camera movement | `processGFNIMovement`, `scheduleSort`, upload integration | Preserve separate sort/task epochs; not every sort sets the graph-dirty flag |
| Spectator/solid origin, smart cull | `shouldUseOcclusionCulling`, manager lines 165–176 | Exact effective mode belongs in traversal key |
| Search/render radius | `getSearchDistance`, manager lines 153–162; radius reload in setup | Per-phase effective search radius and renderer identity |
| Iris sun/light/frustum/pipeline settings | Sun gate, new frustum creation, shadow range/settings | Exact generated plane/box/projection key and pipeline generation |
| DH frustum/projection presence | Shadow-frustum source can use DH projection | Optional DH readiness/projection belongs in shadow key; DH lists remain independently owned |

Iris disables Sodium fog occlusion while a shader pack is present in
`MixinRenderSectionManager.iris$disableFogOcclusion`; a changed fog distance
can still dirty ordinary setup. That suggests a possible redundant-key
component, but removing it alone without phase ownership/other producers
cannot establish safe reuse.

Manager `lastUpdatedFrame` increments per traversal, not per game frame.
`OcclusionCuller` uses that stamp plus each section's incoming-direction state
to avoid repeat visits. `RenderSection.lastVisibleFrame` is shared across
phases; `RenderSectionManager.isSectionVisible` compares it to the current
manager stamp (lines 282–292). Collector lists also carry that stamp. World
entity culling, rebuild submit/upload stamps and sprite activity consume related
state. A cached draw-list pointer does not preserve all those generations.

## A viable later reuse design requires additional work

After isolated inner lists are validated, introduce an explicit structural
dirty epoch bumped by **all** existing graph producers, including direct field
writes and relevant build-result updates. Each phase records its consumed epoch
and exact traversal-input key. World and shadow consumption must not erase
the other phase's invalidation. Reusing visibility can only replace the
graph search/list construction, never the whole terrain setup routine.

Separately retain/recollect pending task queues, sort triggers, section frame
membership and sprite/entity consumers. An initially simpler cache can reuse
only shadow visibility while main keeps its normal updates, provided shadow
memberships are isolated and every relevant structural/key producer invalidates
them. Even this requires the shared traversal-stamp consumers to be modeled;
an old generation cannot be mistaken for current main visibility.

No source-only proof currently establishes complete epochs for that change.
The next actionable step is **instrumentation without skipping**:

1. Count shadow force-dirty calls, other public marks, direct add/remove/rebuild
   writes, graph-changing uploads, phase traversal calls and consumed epochs.
   Attribute the exact force call without broad stack walking in the hot path.
2. Record phase camera/frustum/search/mode fingerprints, region/section list
   identities, ordered local membership hashes and collector generation stamps.
   Compare the same region's lists before/after the other phase to prove aliasing.
3. Add default-off inner-list ownership separation only; compare unchanged
   main/shadow identities, counts, order, animated sprites, task progress and
   render outputs during movement, loading/unloading, edits and shader/world
   reloads. Measure its memory/branch overhead independently.
4. Only after every producer/consumer is covered, consider reusing graph
   search for exact unchanged keys. Differential visibility/caster checks and
   frame timings must decide whether it has useful benefit.

A changed vanilla/DH detail radius reduces the work by changing visible detail;
it remains an existing geometry-quality knob. Correctly avoiding a repeated
identical traversal would be a distinct optimization preserving the same
section/caster set, but this audit does not claim that result has been achieved.
There is no safe one-line head cancellation identified here.
