# Photon temporal shadow reuse: source audit and prototype boundary

2026-10-04. Research only. No source changes, shader reload, compilation, client
or performance measurement was performed for this design. Audited binaries:

- Iris `iris-neoforge-1.8.12+mc1.21.1.jar`, SHA1
  `a3e6355915c7d3b2bc392724795113e51d289378`.
- Photon `photon_v1.3b.zip`, SHA1
  `d975d25c9686de5f10e8b86c8518613cdf3f040a`.

Exact Java findings come from `javap -c -p` on the pinned Iris artifact, with
local disassembly under `/tmp/shaderlod-shadow-audit/`. Photon references below
are paths inside its pinned ZIP. Current Iris online documentation supports the
semantics, but newer upstream branches are not evidence for the 1.8.12 binary.

**Recommendation:** investigate a default-off, bounded two-frame shadow cadence
only after a diagnostic observer measures eligibility and current shadow costs.
An exact identical-input cache is a useful correctness control, but default
Photon waving, dynamic casters and continuously changing sun inputs are likely
to make its live hit rate negligible. Do not ship a frozen-scene cache as proof
of general gameplay performance. Reuse requires coordinated clears, consumers'
matrices/origin and all shadow outputs; a renderer HEAD cancellation is unsafe.

## Iris does not already cache the complete shadow map

The pinned `ShadowRenderer.renderShadows(LevelRendererAccessor, Camera)` draws
on each invocation unless overridden shadow distance is zero. Its method has
no last-input/last-map validity cache. The `CullingDataCache.saveState()` and
`restoreState()` calls preserve normal-world culling state around the shadow
pass; Iris/Sodium maintain separate shadow lists and task lists. Those are not
temporal shadow-texture reuse. `ShadowMatrices.snapModelViewToGrid` stabilizes
the spatial grid; [shadowIntervalSize](https://shaders.properties/current/reference/constants/shadowintervalsize/)
is a spatial interval in blocks, not an update frequency. Photon defaults to
2 blocks.

## Transaction boundaries and exact integration seams

| Seam in pinned Iris | Observed behavior | Requirement for reuse |
| --- | --- | --- |
| `IrisRenderingPipeline.beginLevelRendering()` | Sets world-rendering state, clears custom images, then clears shadow depth/color and dispatches shadow compute programs before later frame notification/custom uniform updates. | Decide reuse before shadow-target clearing; skip only shadow operations, not the entire begin method or unrelated image/frame updates. A full-clear request forces refresh. |
| `shadowRenderTargets`, `shadowClearPasses`, `shadowClearPassesFull`, `shadowComputes` | Normal depth clear uses the depth framebuffer plus `RenderSystem.clear(GL_DEPTH_BUFFER_BIT)`; color clear passes run separately. | Depth-only suppression is insufficient. Preserve the coherent output set and reject unknown compute/image dependencies. Do not clear full-clear-required state without performing its clear. |
| `IrisRenderingPipeline.renderShadows(...)` | Delegates to its `ShadowRenderer`. | A coordinated skip here can avoid work before `ACTIVE` or renderer state changes. It cannot undo an earlier clear. |
| `ShadowRenderer.renderShadows(...)` | Sets `ACTIVE`, swaps render buffers, viewport and projections; creates shadow frustum, calls terrain setup, draws solid/cutout/cutout-mipped, entities/player/block entities, copies pre-translucent depth, draws translucent terrain, generates mipmaps, restores GL/culling/buffers and runs shadow composites. | Refresh must use the original full path. Never cancel halfway and leave projection, phase, buffer or `ACTIVE` state changed. Reuse must not suppress normal-world chunk processing. |
| `ShadowRenderTargets.copyPreTranslucentDepth()` | Maintains a second depth texture via framebuffer blit or another depth-copy strategy. | Reuse both `shadowtex0` and opaque-only `shadowtex1`; do not copy a cleared or stale partial map over a valid cached output. |
| `ShadowRenderer.generateMipmaps()` and `ShadowCompositeRenderer.renderAll()` | Optional shadow mipmaps and compute/draw composites run after shadow geometry. | Unchanged cached levels need no regeneration, but any composite that changes independently or flips targets requires separate handling or rejection. |
| `MatrixUniforms.addMatrixUniforms(...)` → `addShadowMatrix(...)` | Registers PER_FRAME shadow model-view/projection and inverse suppliers. The model-view supplier calls `ShadowRenderer.createShadowModelView(...)` again; it does not simply return `ShadowRenderer.MODELVIEW`. | Freeze/replace the sampling suppliers coherently with the cached map. Leaving renderer statics alone does not preserve consumers' matrices. |
| `ShadowRenderer.createShadowModelView(...)`, `getShadowAngle()` | Uses unshifted camera position and world sky time interpolated with captured tick delta; day/night selects sun/moon. | A cache key must include the actual transform inputs, daylight selection and camera origin. Frozen integer world time alone is inadequate. |
| `ShadowMatrices.createModelViewMatrix(...)`, `createOrthoMatrix(...)` | Sun rotation plus spatial snap; projection depends on distance/near/far. `-1` near/far directives resolve via DH distance. | Initial prototype supports reviewed orthographic Photon only. Force refresh/rejection on projection/DH changes; no reuse of old depth with a newly generated light transform. |

Iris's NeoForge `MixinShadowRenderer` also invokes an Immersive Engineering
compatibility callback during shadow block-entity rendering if installed.
Unknown third-party shadow callbacks/side effects are ineligible in the first
prototype. Add explicit compatibility evidence rather than assuming that the
minimal stack covers modded renderers.

## Photon dependencies that invalidate exact reuse

`shaders/program/shadow.vsh` includes `include/vertex/displacement.glsl` and
calls `animate_vertex` for non-entity geometry in world coordinates. The latter
uses `frameTimeCounter` for leaf/plant gusts and water displacement, `eyePosition`
for plants pushed by the player, `rainStrength`, and weather wind. Weather wind
in `include/weather/core.glsl` depends on `world_age`, biome inputs and wetness.
Freezing `world_age` does not freeze `frameTimeCounter`, weather smoothing or
player interactions. Geometry vertex positions can change with no chunk rebuild.

`shaders/program/shadow.fsh` samples caster texture alpha/color at LOD0 and
writes colored shadow transmission. Resource-pack animated alpha/tint can
change while all block/entity positions remain fixed. With `WATER_CAUSTICS`,
water-normal evaluation is temporal; shadowcolor becomes dynamic even if a depth
mask is unchanged. Transparent depth, water attenuation and colored glass must
be tested separately; the opaque depth map is not the whole result.

With `COLORED_LIGHTS`, shadow vertices call
`include/lighting/lpv/voxelization.glsl::update_voxel_map`, writing the voxel
image. Shadow composites dispatch `program/lpv/floodfill.csh`. The voxel image
is configured to be cleared, and its coordinates include fractional camera
position. Skipping geometry while retaining unrelated image clears/floodfill
would destroy or incorrectly update lighting. **Reject colored-lights/image
users in the first prototype**, rather than claiming cached shadow depth covers
these side effects. Photon `dhShadow.enabled=false` currently excludes DH shadow
geometry; a changed directive or another pack requires a separate audit.

Entities and block entities render at partial-tick poses. Idle limb/head changes,
armor/hurt effects, animation time, signs/banners, pistons and modded renderers
can alter caster silhouettes/materials without a position change. `shadowPlayer`
is true in Photon properties even when `ENTITY_SHADOWS` is false; check Iris's
actual player-render directive. Hashing entity position/count or reusing an old
visible-entity list cannot certify dynamic geometry. In a strict whole-map cache,
unknown dynamic casters must force refresh. Avoid scanning/rendering every model
just to determine whether rendering can be skipped.

## Two candidate policies, with different claims

**Exact static-scene control.** Permit reuse only with identical reviewed caster
inputs, texture/material revisions, source/mesh generation, selected caster
volume, all shadow transform inputs and camera origin. Prefer epoch counters for
terrain/resource changes and O(1) snapshots, not scanning blocks/entities or
reading textures. Reject dynamic shadow features/casters unless their exact
render-input epochs exist. No elapsed-time rule establishes equality. Paused or
fixed-time worlds with static textures, no dynamic casters and waving/caustics
disabled may benefit, but those are narrower conditions than ordinary Photon.
If proving equality requires changing animation settings, apply the same settings
to the original control and label the changed workload. This control measures
transaction correctness and an upper bound, not general live-world usefulness.

**Bounded approximate cadence.** Start with every second displayed frame only where
that yields a nominal refresh interval no greater than roughly 16.7 ms (about
120 FPS or higher). Otherwise use the original per-frame pass. Also reject
reuse when the current map age plus the estimated next frame interval crosses
that budget; actual resumed frames must refresh if already stale. This is a
scheduling bound to measure, not a promise against stalls. Age, frame spacing
and quality threshold are product choices to test; low FPS must not silently
produce 30 Hz or slower maps.
Keep full shadow resolution, radius, filtering and lighting. Refresh immediately
on changes that cannot be tolerated: terrain/mesh/resource epochs, new caster
visibility, camera-envelope exit, teleport, sky-light day/night change, reload,
world/dimension, shader options, DH projection or compatibility failure. Unknown
inputs return to original rendering. An active world with slowly changing sun
and foliage can then reuse intermediate maps without requiring frozen time,
but it is explicitly a temporal quality tradeoff. Player/model animation delays
need moving-entity visual acceptance; conservative fallback can eliminate gains
in caster-heavy scenes. Do not label this exact caching.

The initial approximate prototype should keep camera pose/origin identical and
collect refresh reasons; deliberate camera-motion support follows only after
consistency/coverage work below. Its useful live workload must still advance
world time, animation clocks and entities. Seeing gains only in the fixed-camera,
fixed-time benchmark fails the product acceptance gate.

## Matrices, motion margin and consistent world coordinates

On refresh, save the exact shadow projection `P`, model-view `M`, inverses,
camera origin `C`, map-output/target identities and all relevant epochs together.
For a current camera-relative position `p`, the cached map represents:

```text
clip = P_cached × M_cached × (p + C_current − C_cached)
```

Photon applies its unchanged shadow-space distortion after projection. Initial
no-motion reuse requires `C_current=C_cached`; reuse all cached matrices and
inverse suppliers coherently. Moving-camera support must explicitly translate
current scene coordinates into the cached origin using the equation above (or
its exactly equivalent matrix composition). That is a coordinate remapping of
the cached projection, not newly projecting old depth with current sun matrices.
Keep the old projection and its inverse paired; audit every Photon consumer of
shadow-space depth, normals, SSS, PCSS and colored transmission. Retaining old
Java `MODELVIEW` while Iris independently supplies new GLSL matrices is forbidden.

Iris's shadow frustum is derived partly from the current main-camera projection
and light direction, so a reused map may lack casters visible after a camera
turn even with correct coordinate remapping. Initial policy refreshes on yaw,
pitch, camera mode and FOV changes. Later motion reuse must render a certified
expanded caster volume and retain a border/margin; extra refresh-frame work and
worse culling can erase savings. Verify worst-case receiver/caster bounds,
including off-screen objects casting onto visible terrain. Main-view overlap
alone is insufficient. Grid snapping uses floating `%` with signed remainders;
match the pinned implementation exactly and test negative coordinates/grid
crossings, rather than assuming mathematical-floor snapping.

For any approximation, estimate projected shadow displacement after Photon's
nonlinear distortion and screen-space magnification. A uniform texel bound from
`2×shadowDistance/resolution` alone does not cover the denser distorted center.
Reject unknown fast motion instead of extrapolating entity poses. Temporal
history/TAA can hide some changes but does not prove fidelity.

## Cost target and actionable prototype acceptance

The earlier forest CPU shadow scope was about 1.23 ms and included child terrain
work. It cannot be added to those child timings. Re-measure on the current
minimal and optimized stacks before predicting gains. Removing half of that
scope offers at most about 0.615 ms of that measured CPU work before cache checks,
refresh overhead and bottleneck overlap; it does not promise equivalent GPU or
frame savings. A full frozen-map upper bound can be larger but is not a live
cadence result. This candidate may help combine smaller ordinary-scene gains;
it does not establish the 15% performance gate by itself.

Proposed guard budget: O(1) cached config/identity/epoch/scalar checks per frame,
no heap allocations, reflection, GL reads, fences, terrain enumeration or model
hashing; target ≤0.05 ms p95 CPU guard cost, then tighten toward ≤0.02 ms if useful.
These are acceptance budgets, not measured values. A diagnostic can report
actual-map-refresh/reuse counts, map age, refresh reason and guard wall time.
Exclude instrumentation overhead from production comparisons and compare
patched-disabled against absent-original to expose integration cost.

The next authorized prototype boundary should be:

1. Add observer-only eligibility/reason counters; identify exact-input hit rate
   under unfrozen forest, shoreline and entities before implementing cancellation.
2. Implement one coordinated default-off transaction for pinned reduced Photon:
   colored lights/unknown images/composites/callbacks rejected, complete shadow
   target outputs retained, sampling matrix/origin suppliers coherent, first
   frame/full-clear always refreshed. Original refresh code remains intact.
3. Check disabled/original parity and exact static control first. Exercise shader
   reload, pack swap, failure, world switch, respawn, DH present/absent, screen
   resize, render radius and shadow option changes, animated alpha, water/glass,
   block edits/chunk uploads, moving entities/player and pause/unpause. Reuse
   must not leave `ACTIVE`, viewport, projection, render buffers or phases wrong.
4. Test two-frame cadence with unfrozen clocks and same-quality controls,
   fixed camera first, then reviewed motion. Record shadow CPU/GPU refresh versus
   reuse frame distributions, full-frame median/p95, guard cost, update cadence,
   worst map age and screenshots/video around moving silhouettes. Include at
   least ordinary forest plus dynamic-caster/shoreline cases in both stacks.
5. Stop if guarded live hit rate is low, quality cost is obvious, refresh tails
   regress, or guard/expanded-frustum cost consumes the gain. Leave default-off;
   document failure instead of increasing the baseline or freezing more inputs.

No implementation is authorized by this document alone; coordinator integrates
or rejects the prototype after current probes finish. A terrain/static versus
dynamic-caster split is a possible later path, but needs extra base-depth/color
copy/merge targets, current-frame caster draws and measured bandwidth costs.
It is not a cheap extension of whole-map HEAD cancellation.
