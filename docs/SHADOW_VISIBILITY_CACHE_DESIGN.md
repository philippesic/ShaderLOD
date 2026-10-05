# Shadow-only visibility traversal reuse design

Source-only design, 2026-10-04. No implementation, builds, games or benchmarks
were performed for this document. Inputs are pinned Sodium 0.6.13 commit
`8672650501117e72f5a809867092378fb5bc908a` and the exact Iris 1.8.12 NeoForge
artifact described in `VISIBILITY_REUSE_AUDIT.md`. Its ignored source and verbose
bytecode review paths are the source authority for the line/offset references
below. The default-off inner-list prerequisite compiled and passed model tests;
that is neither a performance gain nor proof this cache is safe.

## Bounded opportunity and recommendation

Cache **only the shadow graph search/list construction** when a complete
structural epoch and exact traversal inputs are unchanged. Main traversal
continues normally. Preserve Iris's forced dirty mark, manager update's frame
increment and Iris's cancellation after list construction; do not clear the
shared dirty flag or cancel terrain setup at HEAD.

This could avoid repeated exact work while a camera, sun and graph remain
unchanged, but exact input equality probably misses on most normal moving-sun
frames. A daylight-frozen benchmark can test mechanism correctness; it cannot
establish useful gameplay acceptance. A conservative frustum envelope is a
possible later policy with extra draw cost, requiring a real bound rather
than approximate matrix equality. Neither implementation is approved by this
source-only design.

## Side effects the cache must reproduce

`RenderSectionManager.update` increments `lastUpdatedFrame`, calls
`createTerrainRenderList(camera,viewport,frame,spectator)`, then normally clears
its dirty flag (`RenderSectionManager.java:131–136`). Iris cancels shadow update
after that call, before the clear. Keeping the call boundary means the cache
can replace/replay its body while the original frame increment and Iris
after-call behavior remain. A boolean head-cancel of update would change them.

The original construction does more than return a draw-list pointer:

| State | Producer | Consumers/replay obligation |
| --- | --- | --- |
| Shadow outer render list | Collector creation and manager field write | Terrain draw/list counts need the same ordered per-region membership |
| Owner-local inner lists | Region getter, reset/add/sort | Independent shadow arrays must retain the exact section IDs/order/counts |
| Shadow task maps/queues | Reset plus collector `getRebuildLists()` | Queues cannot retain stale pending/cancelled jobs or lose new jobs |
| Manager traversal stamp | Update increments before construction | Continue original increment; do not confuse cached stamp with game frame |
| Region-list visible stamp | Collector resets owner list with current traversal stamp | Retag cached shadow list for current generation without clearing membership |
| Section visible stamp | Culler enqueue/nearby/origin operations | Replay every touched section, including non-rendering sections |
| Section incoming directions | Culler initializes then unions graph paths | Replay recorded final direction bits; don't overwrite unrelated main state |
| Region sort/order | Collector + local sorting | Exact key includes camera section/region origin; translucent order remains unchanged |
| Sprite activation | `tickVisibleRenders` main-container entries | Existing generation-aware sprite adapter must see current shadow provenance |

The important distinction is **touched versus rendered**. In
`OcclusionCuller.java:156–166`, enqueue stamps a section and updates incoming
directions before `processQueue` later rejects it for distance/frustum. Empty
sections also participate in traversal/task scheduling. Nearby-section and
origin paths stamp separately (lines 250–254 and 291–307). Therefore retaining
only `VisibleChunkCollector.visit` objects or geometry arrays is insufficient.

`RenderSectionManager.isSectionVisible` compares a section stamp against the
current manager stamp (lines 282–292). Sodium's entity visibility path calls
it (`SodiumWorldRenderer.java:479–526`). Even if a particular shadow scene has
no entities, those side effects cannot be omitted as "unused." Main traversal
will subsequently overwrite the same shared section graph metadata normally.

On an exact cache hit, replay the captured touched-section set's stamps and
final direction unions, refresh each cached region-list generation, and restore
the shadow outer container/empty task maps for the current invocation. This is
an O(touched sections) operation, not free constant-time reuse. It can still
avoid repeated plane/distance tests, graph connectivity expansion, allocations
and sorting. Compare replay cost to those avoided operations before claiming
whole-frame benefit.

New inner-list retagging needs an explicit method that changes only its private
generation field. Calling `reset(frame)` would erase membership. No such retag
API was added by the no-skip prerequisite. Unknown/provenance-incomplete data
always falls back to original traversal.

## Genuine producer epochs

One shared boolean is not a freshness certificate. A cache needs a monotonic
structural generation bound to a particular manager/world and a separate
pending-task generation. Instrument all actual writers first:

| Hook/reason | Actual source behavior | Required invalidation |
| --- | --- | --- |
| Manager constructor, renderer `initRenderer`, unload/reload | New manager/region/section identities; starts dirty | Drop the entire cache, never copy snapshots between managers/worlds |
| `onSectionAdded` success | New node/neighbor connections and pending initial build; direct dirty true | Structural and task epochs; duplicate no-op adds need not falsely certify freshness |
| `onSectionRemoved` success | Disconnects/removes/deletes section and sort data; direct dirty true | Structural/task epochs and disposed-reference invalidation |
| `scheduleRebuild` promotion | New pending type, cache invalidation, direct dirty true | Task epoch immediately; structural epoch conservatively until successful build is observed |
| `uploadChunks/processChunkBuildResults` | Uploads before conditional OR of graph-changing info | Structural epoch for built/flag/connectivity/global-entity changes; preserve all uploads regardless of cache |
| Sort scheduling/promotion | `scheduleSort` changes pending state without necessarily marking graph dirty | Pending-task/sort epoch; cached queues cannot infer safety from clean graph alone |
| Task cancellation/retirement | Tokens, pending types, submit/upload stamps change | Task epoch; no stale queue/token reuse |
| Public `markGraphDirty` / external terrain-update request | Broad caller-provided invalidation | Conservatively invalidate unless the exact redundant shadow-force call is identified |
| Camera/frustum/search/mode change | Ordinary setup marks dirty and updates sorting/camera state | Exact phase input key; these operations still run outside cache replacement |
| Pipeline/pack/resources/vertex-format change | Replaces renderer/frustum/program settings | Pipeline generation and manager identity; force complete miss |

The repeated Iris shadow force is a **reason**, not structural world change:
`ShadowRenderer.renderShadows` bytecode 399 → Sodium LevelRendererMixin
`needsUpdate` RETURN → `scheduleTerrainUpdate` → `markGraphDirty`. Classify only
that exact call boundary, with a nonallocating render-thread scope if later
implemented. Unknown marks remain conservative misses. Do not suppress the
mark itself: preserving main rebuild is part of this bounded design.

Direct field writes and `needsGraphUpdate |= processChunkBuildResults` must be
covered, not merely public `markGraphDirty`. Uploads with unchanged mesh-info
can change GPU storage/translucent data even when they do not need a visibility
rebuild; preserving normal draw/storage APIs matters. Cache only membership,
never baked native draw pointers or index-arena offsets.

A conservative first exact cache should refuse a hit whenever any relevant
pending build/sort/cancellation queue or job is present, or its task epoch
changed. This avoids trying to reuse/recollect mutable shadow task queues in
the first prototype. It reduces hit rate during loading/editing and must be
reported as a restriction, not hidden by waiting only for a static benchmark.

## Exact key and expiry

Required key fields include manager and world identity, pipeline/resource
generation, current supported Photon/runtime identity, shadow culling class
(advanced/reversed/box/nonculling), exact prepared plane coefficients/count,
distance-box parameters, camera origin/position/rotation, viewport camera
transform and section origin, search distance, effective occlusion/spectator
mode, shadow range/projection settings and DH projection/readiness selection.
The key is per shadow terrain traversal, not a global "same camera" flag.

Actual plane comparison may be a better validity key than comparing only an
input projection matrix, because it directly represents the test performed.
Compare every value exactly; any incomplete/non-finite/unknown input misses.
Hashing alone is insufficient: compare stored values after a hash match.
Use original conversion/rounding semantics for relative float endpoints.

`ShadowRenderer.createShadowFrustum` constructs light direction from celestial
uniforms, normalizes it, selects captured vanilla or DH projection, multiplies
by captured model view and generates advanced/reversed planes. Its bytecode
at 423–580 records these dependencies. `AdvancedShadowCullingFrustum` chooses
back/edge planes according to light/plane relationships; edge construction
includes cross products and division by squared edge length. Quantizing the
sun angle or matrix can change included casters near boundaries.

Normal daylight interpolates continuously. Exact celestial direction and
generated coefficients will commonly change even with a stationary player.
Camera motion, bobbing/FOV/projection changes and DH projection updates cause
additional misses. Measure actual hit-rate in real time before engineering
for only frozen-clock captures. Unknown metadata cannot be declared stable by
an elapsed warmup or a heuristic epsilon.

## Later choice: conservative frustum envelope

A safe envelope must contain the **union** of caster acceptance sets over a
declared bounded camera/light interval. It can include extra sections; it may
never omit a current accepted section. Approximate equality of two frusta does
not provide that property.

One mathematically bounded construction uses stable baseline halfspaces. For
a baseline plane `L0(x)=n0·x+d0`, bound every corresponding current plane over
the interval by `||n(t)-n0|| <= eta`, `|d(t)-d0| <= beta`, and bound relevant
camera-relative points by `||x|| <= R`. Also account for camera translation
`delta` and outward-rounded floating-point evaluation error `epsilon`.
Relax the cached plane by a margin at least

`R*eta + max(||n(t)||)*delta + beta + epsilon`.

If a current point satisfies its plane, its baseline expression is no smaller
than the negative of this margin. The relaxed baseline plane therefore contains
it. With valid corresponding constraints, intersecting these relaxed halfspaces
gives a conservative envelope. Box/section acceptance uses the padded AABBs,
including Sodium's 9.125f section extent; the error bound must cover corners
and exact float evaluation, not just section centers.

Important conditions make this a design rather than a ready formula:

- A cached plane needs a valid corresponding constraint throughout the interval.
  If light/back-plane classification or edge topology can remove it, drop that
  constraint from the envelope or force a rebuild. Endpoint sampling alone
  cannot bound the nonlinear cross-product/division path.
- Prove coefficient intervals and finite domain `R` from actual bounded
  camera, light, projection and distance-box inputs. Near-degenerate edge
  denominators or unknown/non-finite values require a miss or a broader safe
  fallback, not an arbitrary epsilon.
- The cached distance/search domain must include the union of current camera
  distance tests. A camera-cell-expanded box/cylinder supplies a simple bound;
  use the same strict/inclusive source comparisons with outward margins.
- Traversal pruning must be compatible with the envelope. For the ordinary
  shadow path, `ShadowRenderer` temporarily disables `smartCull`; confirm the
  effective flag from the actual call. If occlusion is enabled, camera-dependent
  angle masks/origin outward directions can change reachability. A superset
  plane volume alone does not prove a superset graph walk. Restrict that variant
  or build a conservative unoccluded candidate universe explicitly.

A trivially conservative fallback is all available sections within the union
of shadow distance/search bounds, ignoring advanced clipping. That is useful
as a correctness oracle, but may add many geometry draws/native submissions.
It is not an attractive optimization by assumption. A tighter bounded envelope
can amortize graph search over tiny movement/sun intervals, but its additional
caster/section count, draw calls and GPU cost must be measured separately from
CPU traversal savings. Shadow-map extra content and translucent/colored
shadow ordering still require visual validation.

This policy preserves all current casters while accepting extra work; it is
distinct from reducing vanilla radius or dropping distant shadows. A reduced
geometry/detail radius changes quality/reach and remains a separate existing
knob. No fixed angular envelope or tolerance is recommended before bound and
cost evidence.

## Lifecycle and acceptance matrix

| Case | Required behavior / evidence |
| --- | --- |
| First eligible frame or unknown key | Full traversal, capture complete touched/list/task provenance |
| Exact unchanged key, empty task epochs | Replay all side effects; same ordered memberships/caster set and current stamps |
| Sun moves with player still | Exact cache misses unless a separately proven envelope contains current frustum |
| Camera rotates/translates/FOV changes | Miss or proven envelope coverage; preserve nearby/origin traversal behavior |
| New/removed/replaced region/section | Immediate epoch invalidation; no dereference of disposed owners |
| Block edit and async build upload | Current task scheduling/storage upload continues; refreshed memberships when flags/connectivity change |
| Pending sort/token completion | Miss/recollect under first conservative design; no lost/stale jobs |
| Main traversal after reused shadow | Forced dirty remains true; ordinary main rebuild and current entity visibility still work |
| Animated surfaces/shadow casters | Sprite adapter observes replayed current region stamps; compare actual activation identity sets |
| World/dimension/manager/shader reload | Cache fully dropped, no cross-identity snapshots |
| Feature toggled/global disabled/fallback | Original traversal immediately; safe repopulation and default-off behavior |
| Optional DH enters/leaves/projection changes | Miss unless exact selected input remains proven unchanged; DH's own terrain lifecycle untouched |
| Integer generation wrap/long runs | Same upstream equality semantics with epoch wrap protected; no accidental ancient hits |
| Reversed/colored-light or other frustum class | Unsupported until separate proof; fall back to original behavior |

Start with no-skip reason counters and immutable diagnostic snapshots. Then
prove exact-cache output against an always-traverse oracle on every lifecycle
case, including enqueued-but-rejected and empty sections. Inspect actual sprite,
entity/task progress and movement images. A second diagnostic can compare a
conservative envelope's candidates against current original acceptance and
assert set containment each frame without changing rendered results.

Only after correctness, measure repeated serial main/shadow traversal CPU,
replay CPU, total frame/GPU/native draw cost and real-play hit/miss reasons.
Include walking/turning/daylight, loading/edits, DH and optimizer-stack controls.
A frozen-world-age/fixed-camera improvement alone fails this acceptance.
The Photon ordinary performance gate and second shader expansion remain
independent requirements. There is no safe traversal skip implemented here.
