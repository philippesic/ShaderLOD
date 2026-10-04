# Automatic DH coverage: feasibility and staged design

2026-10-04. Research only; no automatic coverage implementation or runtime proof.
The inspected artifact is DH 3.3.3 for Minecraft 1.21.1, SHA1
`4357b4d19fab818780c94f0e43dad1cc931634b2`, pinned in
`dev/runtime.lock.json`. Findings below come from `javap -c -p` on that binary.
Temporary disassembly is in `/tmp/shaderlod-coverage-audit/`, outside Git.

**Decision:** a bounded internal adapter can report nominal uploaded section
footprints, but a read-only poll of the existing quadtree/buffer fields cannot
conservatively certify a complete replacement ring. Missing per-column source
provenance and mutation epochs are the principal blockers. Keep manual opt-in
and travel revocation. If automatic geometry policy becomes the next priority,
first add a diagnostic observer, then version-pinned metadata/lifecycle hooks;
never activate from a ready-section count or repeated matching counts.

## Exact seams and what they mean

| Pinned seam | Useful evidence | Limitation |
| --- | --- | --- |
| `SharedApi.tryGetDhClientWorld()` → `IDhClientWorld.getClientLevel(ILevelWrapper)` | Existing client-level lookup without `getOrLoadClientLevel` | Must match the actual current wrapper/world; do not create levels to inspect them. |
| `IDhLevel.getRenderBufferHandler()`; `DhClientLevel.clientside` / `DhClientServerLevel.clientside` | Renderer and level ownership | Single-player and multiplayer differ; wrapper/dimension names alone do not establish identity. |
| `ClientLevelModule.ClientRenderStateRef` | Atomic reference to current render-state identity | Does not make its children immutable or prevent close after reading. |
| `ClientRenderState.quadtree`, `renderBufferHandler`, `viewDiameterInBlocks` | Current structures and configured extent | Actual selected/complete coverage can be smaller than configured extent. |
| `LodQuadTree.populateListWithEnabledRenderSections(ArrayList)` | Copies enabled selection before view frustum culling | Uses blocking `enabledRenderSectionLock.lock()` and copies object references, not immutable section state. |
| Private `enabledSections`, `altEnabledSections`, `enabledRenderSectionLock`, `treeTickLock` | Selection publication and structural synchronization points | Different locks protect different operations; no exposed coverage generation. |
| `LodRenderSection.pos`, `getRenderingEnabled()`, `renderDataDirty`, `renderBufferContainer`, `gpuUploadInProgress()` | Selected section position, revision-in-progress and current container | Fields are not a coherent atomic snapshot. `canRender()` is only container non-null; `gpuUploadComplete()` is container non-null and not dirty. |
| `LodBufferContainer.pos`, `minCornerBlockPos`, `buffersUploaded`, opaque/transparent wrapper arrays | Uploaded container identity and nominal footprint | No complete-column mask, source revision or immutable closed flag. Empty/non-null wrapper counts cannot certify coverage. |
| `DhSectionPos.getMinCornerBlockX/Z`, `getBlockWidth`, `getDetailLevel`, `getChildByIndex`, `contains` | Exact section geometry and hierarchy | Section detail and data detail differ; do not treat either as Minecraft chunk coordinates. |
| `RenderBufferHandler.buildRenderList(RenderParams)` | Actual per-pass culling/selection | Normal and Iris-shadow passes use different frusta. `getColumnRenderBuffers()` returns a live, culled set reused by later passes. |

DH’s public terrain/render API offers no equivalent complete uploaded-ring
summary; see [DH_COVERAGE_AUDIT.md](DH_COVERAGE_AUDIT.md). Public repository reads
can block and do not establish render completion.

## Why uploaded footprints alone are insufficient

`FullDataToRenderDataTransformer.transformCompleteFullDataToColumnData` iterates
64×64 source columns. Despite its name, the helper
`updateOrReplaceRenderDataViewColumnWithFullDataColumn` simply returns for a null
or empty column. The surrounding transformer can still return a non-empty
`ColumnRenderSource`, so part of a section may be absent while other columns
produce geometry. `FullDataSourceV2.isEmpty=false` means something exists, not
that all columns are complete.

Source metadata includes `FullDataSourceV2.columnGenerationSteps` and per-column
data lists. It is not retained by `LodBufferContainer`. The transformer caller
`LodRenderSection.getRenderSourceForPos` closes the full data source after
conversion; later meshes therefore cannot be audited by reading a retained
source object. Re-querying the database can return a different revision and
incur storage work. `DOWN_SAMPLED` and “known empty air” also need explicit
semantics; “some non-air vertices” is not a substitute. The pinned downsampling
helper `determineMinWorldGenStepForTwoByTwoColumn` takes a numeric minimum, but
other merge paths can assign `DOWN_SAMPLED`. A completeness proof must trace
those paths and never assume that one enum value certifies all child columns.

## Lifecycle and race constraints

`LodQuadTree.tryTick` uses `treeTickLock.tryLock()`. During its update, the tree
builds an alternate enabled list, swaps lists under `enabledRenderSectionLock`,
then disables/deletes other nodes. Holding the enabled-list lock protects list
publication, not every section/container operation.

Upload completion in `LodRenderSection.lambda$uploadToGpuAsync$0` replaces the
section’s container, closes the old container and clears dirty state.
`LodBufferContainer.close()` clears `buffersUploaded` and queues render-thread
cleanup that nulls/closes wrapper entries. `LodRenderSection.close()` disables
rendering, cancels work and closes a container without necessarily clearing its
reference. Thus non-null, upload-complete or twice-equal snapshots are not
sufficient liveness proofs. Do not call build/upload/close methods from an
observer, acquire a section monitor that can block on its synchronized builder,
retain VBO references for later use, or inspect GL state to recover this fact.

`ClientLevelModule.stopRenderer()` compare-and-sets the state reference to null
then closes its quadtree. Cache clearing keeps the same render-state identity
and calls `LodQuadTree.clearRenderDataCache()` under the tree lock. Terrain
updates queue section reloads. Identity polling therefore misses meaningful
within-state invalidation. Sampling on the render thread avoids some GL cleanup
races but does not freeze DH’s tree/source work. A positive certificate needs
explicit revision publication and invalidation before destructive mutation.

## Proposed diagnostic observer

A first implementation should publish only `UNKNOWN`, `INCOMPLETE` and
`NOMINAL_UPLOADED_FOOTPRINTS`, with missing cells and reasons. None authorize
automatic capping.

Use cached optional reflection handles with a strict artifact/version gate.
Prefer capturing primitive positions and container readiness while DH already
processes its enabled selection, avoiding another full scan. A pure polling
prototype can use private `enabledRenderSectionLock.tryLock()` with no wait,
a preallocated scratch buffer and a hard size limit; skip when contended or too
large. It must remain diagnostic because the lock does not freeze containers.
Do not call the public blocking list-copy method from a hot policy path, and do
not carry live list iterators across frames. Publish immutable primitive
snapshots; the Options getter reads only a cached decision and guards.

Suggested initial observer budget: run at most 4 Hz, at most 128 ring-intersecting
section records or 0.1 ms per step, ≤4,096 total records, ≤128 KiB primitive
snapshot storage and ≤16 KiB coverage bitmap storage per candidate region. These are proposed bounds to measure,
not verified costs. Cap lock-held inspection at a much smaller measured budget;
if snapshot publication cannot fit it, instrument publication rather than
extending the lock. Any overflow, contention, stale snapshot or budget exhaustion
returns `UNKNOWN`. Do not concatenate partial live snapshots into positive
coverage without a shared stable generation.

## Metadata needed for a positive certificate

A future adapter must associate each uploaded container with immutable source
coverage/revision metadata captured during DH’s existing conversion, not a new
storage read. Candidate hooks are the transformer return, section mesh-build
completion, successful container publication, and container close. Carry metadata
through source→builder→container identity; matching section position alone can
attach old metadata to a replacement upload. Treat missing association as unknown.

Publish a per-level selection generation and coverage epoch. Invalidate before
cache clear, reload/dirty marking, node deletion, container replacement/close,
world unload and renderer stop/config rebuild. Also track mesh-affecting DH
settings, shader pipeline, projection/camera mode, transparency and overdraw
settings. Stable immutable snapshots may then be processed across frames only
while epoch and identities remain unchanged. Independent worker callbacks need
well-defined release/acquire publication; the existing ordinary fields do not
provide that contract. This is explicit pinned integration, not arbitrary
reflection of another DH version.

## Ring and hierarchy algorithm after those blockers are solved

1. Define the terrain volume removed by the exact uncapped and capped Sodium
   section-selection rules. Start conservatively with an outer square enclosing
   every baseline selected chunk, including border chunks, and exclude only a
   verified guaranteed inner vanilla region. Do not certify only the forward
   camera frustum. Include DH/Photon overdraw and fade overlap. Photon v1.3b
   `dh_terrain.fsh` and `dh_water.fsh` start/end fading at
   `far - DH_OVERDRAW_DISTANCE - DH_OVERDRAW_FADE_LENGTH` and
   `far - DH_OVERDRAW_DISTANCE`; defaults are 16 blocks each. Establish the runtime
   `far` value and vanilla seam first; guessed cap×16 geometry is insufficient.
2. Expand the region for the true interpolated camera, third-person offset,
   candidate movement allowance and restoration latency. Use exact DH
   `RenderParams.exactCameraPosition` when appropriate; entity position alone
   does not describe all views. Initially limit activation to reviewed surface
   views; caves/underwater/culling rules require separate acceptance.
3. Decode each selected section via pinned `DhSectionPos` helpers into a half-open
   rectangle `[minX,minX+width) × [minZ,minZ+width)`, using checked long arithmetic.
   For raster cells use mathematical floor division, including negatives;
   `-0.1` and `-16.1` must not map to zero/−1 by truncation. Test axes, section
   boundaries and extreme world positions. Do not manually reinterpret packed
   coordinate bits.
4. Intersect those rectangles with the candidate ring. Only current selected,
   live uploaded containers with matching immutable metadata contribute coverage.
   An enabled coarse parent may cover its children if its complete-column
   metadata covers the entire queried area; DH’s `onDetailLevelTooLow` can retain
   a renderable parent and disable children until all four are ready. Do not
   require absent fine children when a certified parent covers them, and do not
   count disabled parent geometry simply because its container remains alive.
   For subdivision, coverage is parent-valid OR the union of certified selected
   children fully covering the query—not child count≥some threshold.
5. Conservatively reject partial cells. A fixed 16-block bitmap can represent
   nominal ring coverage only if every marked cell is entirely covered. When
   a source column is coarser or unknown, inspect its metadata-supported footprint
   explicitly. Reject incomplete source columns; known empty columns need a
   validated representation distinct from absent data. Opaque and transparent
   requirements differ: no water vertices alone does not prove water absent.
6. Include adjacent seam dependencies: the section builder loads four cardinal
   sources through `getRenderSourceForPos(..., direction)` and tests neighboring
   detail levels. Require valid neighbor provenance at coarse/fine boundaries,
   with a halo at least one relevant data-column width. Edge stitching quality
   and source completeness are separate checks. Expand or reject where the
   required neighbor lies outside the certified snapshot.
7. Publish a certificate containing region, allowed camera envelope, source,
   selection/upload epochs, world/pipeline identities and tested config signature.
   A ttl is only an additional guard; epoch invalidation is required. Any hole,
   stale/unknown state, unsupported setting or movement beyond the certified
   envelope restores the original request and revokes eligibility.

## Shadows, restoration and acceptance

A terrain-ring certificate does not preserve shadows automatically. The shadow
pass has its own frustum, while reducing the vanilla radius can remove detailed
casters, foliage, entities and block entities. Retain a detailed radius that
contains the verified shadow-caster region or separately prove DH shadow
coverage for the pinned Photon programs; comparing shadow-distance scalar to
cap alone does not prove projected caster coverage. Screen-space shadows and
reflection input also change when detailed geometry changes. This is a geometry
quality tradeoff even if shading budgets stay identical.

Uncapping an Options getter restores the *setting* immediately. Sodium then
needs time to rebuild/upload previously released detailed terrain. Safe travel
therefore needs a certified DH lookahead margin until detailed geometry has
actually recovered, or retained warm vanilla meshes. Teleports, high speeds and
cache clearing can invalidate that margin; fail-open policy is necessary but
cannot retroactively prevent a transient hole. Do not promise seamless arbitrary
travel from tick-time restoration alone or adjust radii every frame. Start with
fixed caps, activation dwell and verified steady scenes.

The first acceptance suite should exercise partial-column cold caches, coarse
parent fallback, all camera headings, negative-coordinate boundaries, source
updates during amortized scans, uploads failing/replacing/closing, independent
cache clear, renderer stop, dimension changes, third-person views, fast travel
and teleport restoration. Measure observer CPU/tail cost separately from gains;
inspect seam water/foliage/shadows and record actual selected/upload generations.
A positive automatic policy should remain off by default until those failures
restore safely and repeated motion tests pass. The immediate next deliverable
should be the bounded diagnostic observer, not automatic activation.
