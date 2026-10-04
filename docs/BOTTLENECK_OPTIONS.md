# Rendering bottleneck options

Audit checkpoint: 2026-10-04. Scope is pinned Photon v1.3b, Iris 1.8.12,
Sodium 0.6.13, DH 3.3.3, Minecraft 1.21.1, and the current Apple M3 Max
profile. This is source/bytecode inspection and a proposal for bounded tests;
new optimizations and performance claims require the coordinator's measurements.
No shader/renderer source was changed for this audit.

## What the current evidence supports

The previous serial measurements found real reductions in cloud, fog, AO and
lighting passes but only 1–5.5% in the whole GameRenderer GPU timeline interval.
The pass-profile OFF means were clouds 0.539ms, fog 0.164ms, AO 0.057ms,
and deferred lighting 0.358ms. These came from alternating query frames and
cannot be summed into an exact single-frame decomposition. As a rough planning
ceiling, their approximately 1.1ms budget is small against the 8–9ms render
interval. Eliminating AO entirely trades tree depth for roughly hundredths of a
millisecond. The user rejected that tradeoff.

A mechanism saving half of its own work must cover about 30% of a frame to save
15% overall, before extra overhead. A 20% mechanism needs roughly 75% coverage
for that target. Find a large enough measured fraction before another round of
sample caps. GPU elapsed intervals also include command-starvation/scheduling
intervals; unaccounted time is not evidence of GPU busy overhead. CPU wall and
thread time, GPU draw scopes, and pass scopes must be interpreted together.

Current inactive Mac features include environment/sky reflections, colored
lighting, colored shadows, variable penumbra shadows, and cloud shadows. More
SSR/POM/LPV caps cannot explain the current frame. Their successful compilation
is not a measured contributor. Record every high/lower/adaptive option and the
same sampler-safe feature mask; do not call a masked profile full Photon Ultra.

## Best first controls

| Control | Exact knob/seam | What it resolves | Boundaries |
| --- | --- | --- | --- |
| Original high vs simple lower internal resolution | Photon `TAA`, `TAAU`, `TAAU_RENDER_SCALE=0.75` vs 1.0 | Whether fragment/fill work, including terrain shading, has a large reducible fraction | Global image-quality tradeoff; original Photon control, not distance LOD; validate DH depth and motion |
| Original high vs coarser DH at fixed reach | DH `horizontalQuality=LOW` vs current MEDIUM, same `lodChunkRenderDistanceRadius=128`, same vertical quality | Whether distant geometry/submit volume dominates | Rebuild/warm changed LOD meshes before timing; preserve the same data coverage and camera |
| Original high vs lower cloud spatial quality | Photon `CLOUDS_TEMPORAL_UPSCALING=2` vs 4, with common ray budgets | Whether full ray pixel count rather than per-ray samples offers worthwhile headroom in a legitimately detailed supported setting | Current default already uses 4; this is an optional detailed workload and must not be represented as the shipped High preset |
| Original vs patched dynamic OFF vs patched static OFF | Patched OFF bridge vs `SHADERLOD_USE_MOD_CONFIG=false` and `SHADERLOD_ENABLED=false` | Whether dynamic quality infrastructure itself changes compiler/runtime cost | Match common settings, use repeated serial runs, reload and settle shader histories |
| Same workload at two output resolutions | Original pack and same quality at e.g. 1488×836 and 1984×1115 | Whether total elapsed scales with raster area or remains largely CPU/geometry limited | Report resolution as a separate control; do not mix into the adaptive quality speedup |

Photon's existing TAAU lowers linear raster scale; 0.75 means approximately
56% of the original pixel area for affected rendering, not a 44% frame-time
promise. It adds its own neighborhood/upscale work. It is particularly useful
as an upper-bound control before inventing selective low-resolution shading.
The source uses `taau_render_scale` across geometry and fullscreen programs;
actual Iris/DH viewport behavior still needs driver validation.

The current cloud pass is already quarter linear resolution (1/16 pixel area),
AO and fog use half linear buffer sizes, and sky-map output is only 192×108.
A lower-resolution cloud or fog prototype alone has a modest ceiling in the
current scene. Use a realistic cloud-detailed workload as a second scene if
clouds become large enough, and compare to the same original lower setting.

## Larger mechanisms, gated on attribution

### 1. Distance-aware geometry transition into DH

If measured Sodium opaque/cutout raster or submission dominates, first test a
plain shorter vanilla radius with the same warmed DH reach. This establishes
what existing coarse geometry can save. Rendering vanilla 24 chunks while DH
also supplies coarse scenery needlessly preserves detailed distant meshes if
that extra precision is no longer visible. The simple lower-radius setting is
a required control, not itself a new ShaderLOD implementation.

An adaptive prototype could retain full vanilla geometry near the camera and
reduce outer detailed rings in favor of DH, with a stable overlap/fade interval
and enough hysteresis to avoid rebuilding on every frame. Do not discard random
sections or skip cutout draws: that loses tree silhouettes, causes holes, and
invalidates lighting. Do not shorten the independent shadow-caster coverage just
because visible terrain changes; inspect distant casts and water boundaries.

Audited seams:

- `SodiumWorldRenderer.setupTerrain(Camera, Viewport, boolean, boolean)` prepares
  visibility; `drawChunkLayer(RenderType, ChunkRenderMatrices, double, double,
  double)` submits a terrain layer.
- `DefaultChunkRenderer.render(...)` walks region render lists,
  `fillCommandBuffer(...)` emits draw commands, and `executeDrawBatch(...)` uses
  `DrawCommandList.multiDrawElementsBaseVertex`. Sodium already batches draws;
  replacing it with per-section calls is not an optimization.
- `RenderBufferHandler.buildRenderList(RenderParams)` builds DH visibility and
  exposes visible/culled counts through its debug strings.
- `LodQuadTree.calcExpectedDetailLevel(...)`, `calcDetailLevelFromDistance(...)`,
  and `updateDetailLevelVariables()` control selection with
  `detailDropOffDistanceUnit` / `detailDropOffLogBase` and leaf detail limits.
- `LodRenderer.render(...)`, `renderDeferred(...)`, and private
  `renderTerrain(...)` separate DH phases. The concrete
  `GlDhTerrainRenderer.render(...)` delegates to its terrain shader program.

Prefer bounded DH quality/config changes through its audited API before a new
mesh renderer. Changing horizontal/vertical quality triggers background data or
mesh work; exclude that rebuild/upload period and keep both configurations warm.
Current `maxHorizontalResolution=BLOCK`, `horizontalQuality=MEDIUM`,
`verticalQuality=MEDIUM`, `transparency=COMPLETE`, and
`vanillaFadeMode=DOUBLE_PASS` are useful controls. A SINGLE_PASS fade comparison
is a separate quality tradeoff and matters only if the fade stages actually cost
appreciable time under Iris. Retain overdraw prevention; don't render overlapping
vanilla/DH opaque layers merely to create an expensive baseline.

### 2. Depth-aware low-resolution lighting, keeping occlusion/shadows

If deferred fragment shading becomes large at a genuinely detailed supported
preset, move costly lighting into a reduced-resolution output and reconstruct
with depth, normal, and material guards. Keep near geometry and disocclusions at
full quality. A comparison to original Photon TAAU and simple lower lighting
quality determines whether selective reconstruction is worth its complexity.

Exact Photon seams are `d4_deferred_shading.fsh` for the final opaque lighting,
`d3_ao.fsh` for half-resolution AO/history, and `d4_deferred_shading.fsh`'s
existing depth-weighted four-neighbor AO reconstruction. The latter demonstrates
how combined-depth inverse projection is used across vanilla and DH. A new
lighting target/pipeline needs explicit buffer ownership, format, viewport,
coordinate changes, and consumer declarations; it is not a safe numeric option
change. Iris supports per-pass viewport scaling through
`CompositeRenderer.Pass.viewportScale`; `renderAll()` binds a pass's framebuffer
and viewport before the fullscreen draw.

Fog currently uses `smooth_filter` in `c1_blend_layers.fsh`, not a depth-aware
reconstruction. Adding a quarter-resolution fog buffer without a bilateral
upsampler risks haloing foreground silhouettes. Fog/AO share half-resolution
colortex6/7/14 at different stages, so shrinking those buffers blindly changes
multiple effects and can break history/depth correspondence. The output change
must preserve each stage's exact interpretation. Additional sampler bindings
may exceed the Mac's budget; do not silently remove lighting to make room.

This mechanism can retain low-frequency AO and shadow structure rather than
returning neutral far AO. However, current AO is too small to justify a special
renderer alone. Continue treating AO bypass as a diagnostic, not a default.

### 3. Temporal reuse of sky/cloud/fog work

The sky-map texture is cached *for sampling* by reflections and lighting, but
Photon's `d0_sky_map.fsh`/`.vsh` contain no periodic update gate. Iris
`CompositeRenderer.renderAll()` iterates and draws the configured pass every
frame. A zero median elapsed query does not prove refresh skipping. The map
includes special lighting-color texels at x=191, and it uses a fixed sea-level
cloud origin rather than the rendered camera altitude.

A bounded refresh prototype must keep light/ambient palette texels current and
refresh cloud-bearing map texels less often, with explicit invalidation for
shader reload, dimension, sun/weather discontinuity, lightning and changed
quality. Preserve the existing texture contents on skipped cloud updates;
`discard` alone is unsafe if the target is cleared or ping-pong-swapped. Iris
buffer-flip and clear directives need audit before implementing reuse. This is
an explicit temporal approximation, not distance LOD, and stale reflections/
lighting must be tested under motion and storms.

`d1_clouds.fsh` and `d2_clouds_upscaling.fsh` already implement temporal
reprojection/upscaling, with colortex9/10 low-res buffers and cloud history in
colortex11/12. Lower refresh rate or checkerboard work can reuse that machinery
only with valid reprojection, disocclusion rejection, and camera-cut invalidation.
Prove the cloud fraction is large first; lowering refresh of a 0.4ms pass cannot
produce a 15% improvement in an 8ms frame by itself.

### 4. CPU submission and immutable state

Pinned Iris `ProgramUniforms.update()` evaluates PER_FRAME uniforms once per
program/frame. `IntUniform.updateValue()` compares a cached value before calling
`glUniform1i`. Floats use the same style of value caching. Constant ShaderLOD
values are therefore not necessarily uploaded every draw, despite their
PER_FRAME registration.

ShaderLOD currently calls `ShaderLodConfig.snapshot()` separately for several
suppliers; each computes cross-field bounds and constructs a record. Cache an
immutable sanitized snapshot on config/control change, or once per frame,
if CPU measurements show meaningful cost. This is a bounded cleanup, with no
reason yet to expect millisecond gains. A hypothetical “remove uniform upload”
rewrite would duplicate functionality Iris already provides.

Iris `CompositeRenderer.renderAll()` also updates programs, custom uniforms,
samplers/images, mipmaps and framebuffer/viewport state, and clears active
program/sampler state after the stage. Measure relevant CPU methods before
removing state transitions. Some state invalidation protects integration with
Minecraft, Sodium and DH; deleting it speculatively can corrupt later passes.

## Dynamic loops and the disabled comparison

A uniform quality decision can stop a loop early while still making its bound
nonconstant to the driver. This may inhibit unrolling, change register pressure
or instruction scheduling, even in the disabled mode. GLSL compilation does not
expose Apple ISA/occupancy and does not prove this explanation. Nearby distance
zones are usually spatially coherent except their transition bands; do not blame
warp divergence solely because branches exist.

The diagnostic sequence should use the same upstream common quality:

1. Original Photon high.
2. Patched Photon with live bridge OFF.
3. Patched Photon with bridge disabled and LOD compile-time disabled.
4. Patched adaptive with important occlusion/shadow structure retained.
5. Original lower quality as a separate practical control.

If (2) regresses and (3) recovers, investigate branch/unrolling overhead with a
single affected pass. If all three are close, do not spend days specializing
loop shaders. A prototype using constant maximum loops plus uniform early
breaks may compile differently but still needs measured evidence. Static shader
quality variants can recover specialization at the cost of reloads/variant
management; do not introduce a universal rewriter or claim continuous adaptation
when switching programs requires shader reload.

## Acceptance for a prototype

Implement only after attribution identifies a fraction large enough for the
proof target. Report original high, original lower, patched disabled, and
adaptive results with repeated warmed serial blocks, matched timings overhead,
GPU render and CPU metrics, sample counts and variability. Include movement,
TAA stability, near silhouettes, far tree depth/shadows, vanilla/DH seam, water
and disocclusions. A 15% saving achieved by a simple existing lower preset with
better fidelity than adaptive is evidence to prefer that preset, not to claim
success for new distance LOD infrastructure.

Source evidence is the hash-locked local Photon ZIP and exact runtime jars in
`dev/runtime.lock.json`; methods above were verified with `javap -p -c` against
those jars. Runtime support and which renderer implementation is active belong
to the measured client, not this read-only audit.

## CPU-attributed geometry prototype proposal

The coordinator's forest attribution now shows approximately 7.83ms runTick
thread CPU within an 8.93ms frame wall interval, GameRenderer 7.36ms thread CPU,
vanilla opaque/cutout submission 1.85ms, translucency 0.29ms, terrain setup
0.40ms, and DH opaque/translucent submission 0.44/0.54ms. This supports a CPU
submission hypothesis and a bounded geometry control. It does not prove that
reducing radius 24→16 will save a particular fraction, or that a GPU interval
can be subtracted from CPU time. Report final measurements in the results doc.

### Opt-in detailed-terrain cap with DH preserving long reach

Prototype configuration: `geometry.enabled=false` by default and
`geometry.vanillaChunkCap=16` (minimum 8); users retain their normal render
setting. Scope initially includes the supported Photon/DH combination in a
loaded world. Keep shader sample LOD separate so an ablation can enable geometry
without aggressive far AO or shadow cuts. The geometry policy need not require
the patched pack when the original pinned Photon also supports DH; it must
require ShaderLOD's main enable flag and explicit geometry opt-in. The original
baseline with ShaderLOD absent receives no cap.

Recommended seam is a return-value cap on
`Options.getEffectiveRenderDistance()`: return `min(originalReturn, cap)` while
the latched policy is active, otherwise return the original value. Do not write
`Options.renderDistance().set(...)`, persist a replacement user preference, or
broadcast a lower server distance. The raw option and `options.txt` stay intact,
and the server-imposed minimum already present in the original return survives.
The HUD/config should display both requested and effective radius. User edits
remain authoritative: values below the cap remain below it; increasing the
requested value changes only the remembered/manual preference while the cap is
active. Turning geometry/mod/DH off immediately recovers the current preference,
not a stale value captured before the user edited it.

This common getter is deliberate. Pinned Sodium
`SodiumWorldRenderer.setupTerrain` compares it to the stored renderer radius and
calls `reload()` when it changes; `reload()` constructs a new
`RenderSectionManager` using that radius. Pinned DH's NeoForge
`MinecraftRenderWrapper_neoforge.getRenderDistance()` also calls the same
getter, so overdraw/fade can follow the new vanilla boundary. A Sodium-only
radius override with DH still believing vanilla is 24 can leave a missing
16–24-chunk ring. Changing the getter also affects fog/entity-view scale and
other vanilla consumers; document that consequence rather than presenting this
as a pure batching optimization. Shadow caster coverage and actual DH overlap
must be validated, not assumed.

Change the effective cap only at explicit policy/lifecycle transitions, not from
instantaneous FPS every frame. Radius changes destroy/rebuild Sodium renderer
state. At initial enable and disable, suspend benchmark sampling until section
rebuild/upload and DH overlap settle. Do not choose a fluctuating radius while
measuring the static kill test.

### DH readiness, optional dependency, and fail-open behavior

Public pinned APIs give useful readiness checks:

- Test NeoForge mod presence before loading the optional DH adapter class.
  Public `DhApi.Delayed.configs`/`worldProxy`/`renderProxy` may initially be null.
- Require `worldProxy.worldLoaded()` and
  `configs.graphics().renderingEnabled().getValue()` true. Check the current
  world/dimension identity separately; a loaded DH world alone does not identify
  the same active dimension.
- `IrisApi.getInstance().isShaderPackInUse()` confirms shaders are active but
  does **not** confirm DH compatibility. Pinned internal
  `Iris.getPipelineManager().getPipelineNullable()` can be checked for
  `IrisRenderingPipeline`, with `getDHTerrainShader().isPresent()` and
  `getDHCompat().getInstance() != null`. Evaluate at a safe client/render
  lifecycle boundary, never while recursively computing render distance.
- A DH `DhApiBeforeRenderEvent` listener can latch successful rendering for the
  current world; guard `IrisApi.isRenderingShadowPass()` because the render-pass
  enum describes opaque/transparency rather than an explicit shadow pass.
  `getDhDepthTextureGlId()` returns `DhApiResult<Integer>` with `success` and
  `payload`; a valid positive texture can corroborate initialized GL rendering.
  It does not prove that the nearer replacement ring has complete cached meshes.

Do **not** call `DHCompat.hasRenderingEnabled()`/`checkFrame()` from an Options
getter. The audited bytecode can trigger an Iris pipeline reload when DH config
or GUI scale changes. That turns a nominal getter into a reentrant renderer
mutation. Compute a small immutable readiness/policy state once at an appropriate
client boundary and let the getter perform only a cheap boolean/minimum check.

Start fail-open: original radius until DH readiness is established. Clear
readiness immediately on world unload, dimension change, shader reload/failure,
DH disable or policy disable. API linkage/initialization failures disable only
the geometry policy and expose the reason; they do not crash optional-DH users.
Avoid rapid cap on/off if initial uploads are temporarily late. The first
production prototype should be a fixed opt-in cap in a warmed DH-supported world,
with honest cache-coverage limitations, not an automatic claim that distant
terrain can always replace all unloaded detailed chunks. Test a cold DH cache
and absent generation: fall back rather than show missing terrain.

### Coarser DH as a separate control

Keep `lodChunkRenderDistanceRadius=128` and first compare
`horizontalQuality=MEDIUM`/`LOW` with the same vertical quality and cache coverage.
DH's public `graphics().horizontalQuality()` returns an
`IDhApiConfigValue<EDhApiHorizontalQuality>` supporting `getTrueValue()` (user
value), `getApiValue()`, `setValue(...)`, and `clearValue()`. A later production
override can use that API, but restoring/clearing another mod's override is not
safe. Only clear an override this policy demonstrably owns; respect concurrent
manual/API changes. Do not include an unmeasured automatic DH quality override
in the first vanilla-cap prototype. It complicates both ownership and the
quality attribution.

### A CPU-only backend alternative preserving geometry

Sodium already uses `MultiDrawBatch` and `multiDrawElementsBaseVertex` per render
region. The concrete potential seam is caching opaque `fillCommandBuffer(...)`
results while visibility, section mesh allocations and camera-dependent face
selection remain unchanged. Still update region model matrices and submit all
the same triangles. First instrument `fillCommandBuffer`,
`setModelMatrixUniforms`, and `executeDrawBatch` separately: a 1.85ms whole-layer
CPU interval does not identify command assembly as its dominant portion.

This is not a trivial flag. `SectionRenderDataStorage` has no public content
revision: vertex/index setters/removers, buffer resize and delete can invalidate
stored draw pointers. `ChunkRenderList` is reset/sorted/filled and tracks visible
frames, not a stable membership revision. A correct cache therefore needs
explicit invalidation for those mutations, visibility membership, camera-cell
face changes, region disposal and renderer/shader reload. Translucent sorting
requires additional invalidation and should remain upstream in a first test.
Never cache raw pointers across buffer relocation without generation tracking.
If the isolated assembly cost is small, stop this approach; inventing a second
chunk renderer or disabling geometry to improve a batching benchmark would not
be the same mechanism.

Acceptance for the fixed-cap kill test: compare original 24/DH128, original
16/DH128 as the simple existing-setting control, and ShaderLOD's requested24 /
effective16 / DH128 policy. Keep original lighting/occlusion options, report
world submission/thread costs plus complete frame/GPU metrics, and inspect the
vanilla/DH boundary, tree silhouettes, water and shadows. The policy should
match the practical lower-radius control; any incremental claim must explain
what it actually adds beyond making that existing tradeoff explicit/reversible.
