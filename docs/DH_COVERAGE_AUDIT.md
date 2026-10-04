# DH replacement coverage audit

Audited 2026-10-04 against the locked
`DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar`, SHA1
`4357b4d19fab818780c94f0e43dad1cc931634b2`. This audit used `jar tf` and
`javap -c -p` on that artifact. Online API documentation is supporting context;
it can change independently of the pinned binary. No new in-world acceptance
or automatic coverage guarantee is established by bytecode inspection.

## What the public API establishes

`DhApi.Delayed.configs` and `worldProxy` can be null before initialization.
`IDhApiWorldProxy.worldLoaded()` establishes world readiness. On
`IDhApiGraphicsConfig`, `renderingEnabled()`, `renderingMode()` and
`chunkRenderDistance()` establish enabled normal rendering and configured reach.
The controller checks these cached reflection handles on client ticks only.
`EDhApiRendererMode` in the pinned jar has `DEFAULT`, `DEBUG_TRIANGLE` and
`DISABLED`; only `DEFAULT` is eligible. DH configured reach must be at least the
uncapped requested vanilla radius. Neither these values nor warmup prove data,
meshes or successful draw coverage.

The public [terrain repository API](https://distant-horizons-team.gitlab.io/distant-horizons/com/seibel/distanthorizons/api/interfaces/data/IDhApiTerrainDataRepo.html)
can return terrain columns/regions. In the pinned implementation,
`DhApiTerrainDataRepo.getDataPointsAtPos` obtains
`IDhLevel.getFullDataProvider().getAsync(position)` and then calls
`CompletableFuture.get()`. Cache misses can therefore block on storage/provider
work. These queries are not used in the tick or Options getter. Even complete
repository data would establish source availability, not completed render
conversion/upload or correct render-list selection.

`IDhApiTerrainDataCache` only exposes `clear()` and `close()`; it has no public
coverage query. `IDhApiRenderProxy` exposes texture identities, renderer metadata,
transparent-pass controls and cache clearing, with no complete uploaded-section
coverage enumeration. A valid depth texture cannot certify the replacement ring.

`DhApiBeforeBufferRenderEvent.EventParam` supplies `modelPos`, in addition to
[render matrices and level information](https://distant-horizons-team.gitlab.io/distant-horizons/com/seibel/distanthorizons/api/methods/events/sharedParameterObjects/DhApiRenderParam.html).
It does not identify section detail/extent, missing terrain columns, or complete
360-degree coverage. Observing visible-buffer events would omit culled sections
and cannot establish safety when the camera turns. No buffer-count threshold is
used as coverage proof.

## Internal seams for future research

The pinned `LodRenderSection` has `pos`, `renderDataDirty`,
`renderBufferContainer`, `canRender()`, `gpuUploadComplete()` and rendering-enable
state. Bytecode shows `canRender()` only tests whether the buffer container is
non-null; `gpuUploadComplete()` tests container presence and `!renderDataDirty`.
These names alone are not proof of every terrain column existing, active
quadtree selection, or a complete ring under all views.

`RenderBufferHandler` owns `lodQuadTree` and exposes `getColumnRenderBuffers()`.
A robust version-pinned integration could investigate the client quadtree's
selected complete sections and their source completeness, conversion/upload
state, render-enabled selection and ring overlap. This would require independent
synchronization/thread-lifetime analysis and cold-cache/moving-camera tests.
Do not enumerate mutable renderer structures opportunistically in an Options
getter or equate a database hit with an uploaded render buffer.

## Implemented conservative alternative

The terrain feature remains default-off with explicit scene acknowledgement.
`GeometryCoverageGuard.Scene` binds world, Iris pipeline, camera entity, 16-block
X/Z cells and Y section, requested radius, cap and configured DH radius. Initial
true acknowledgement waits until a stable eligible scene has completed warmup,
so ordinary startup with no player/pipeline does not consume the confirmation.

After binding, mismatched or unavailable scenes revoke acknowledgement until
false→true confirmation. Crossing an X/Z chunk or vertical-section boundary
restores the original effective distance immediately in the getter; its local
revocation also survives an excursion-and-return between ticks. There are no
config reads, reflection, allocations, GL calls or renderer reloads there.
Tick-time invalidation covers readiness, normal rendering mode, DH reach,
pipeline/world/camera changes and radius/cap changes. The original Options
preference is never rewritten, and a lower server/manual radius is never raised.

Chunk/section boundaries provide a deliberately small, fixed guard around a
manual warmed view. They do not establish safe arbitrary travel: sub-cell
movement, third-person camera displacement, terrain edits, mesh-quality
changes and independent cache clearing remain risks. Automatically re-arming
on elapsed time would hide those risks, so this implementation does not do so.

## Acceptance evidence and remaining runtime checks

`./gradlew compileJava test --tests com.pp.shaderlod.GeometryLodPolicyTest
--console=plain` passes ten tests covering eligibility, restoration, startup,
scene invalidation, between-tick revocation, negative coordinates and reach.
That establishes source/build/unit behavior. The coordinator owns runtime checks:

- Start with persisted true, fixed camera, DH normal mode/reach128 and requested
  vanilla24: stay24 during warmup, then reach cap16 after a settled inspected view.
- Move across an X/Z chunk or Y-section boundary: return24 immediately; move back
  and wait: remain24. Explicit false→true after inspection can restore16.
- Reload shader pipeline, switch world/dimension/camera, change cap/request/DH
  distance or disable normal DH rendering: fail open and require new confirmation.
- Remove DH entirely, disable ShaderLOD/terrain/shaders, or trigger Iris fallback:
  retain current original effective radius without optional-DH class loading failure.
- Inspect water, shadows, seam overlap and cold terrain while rotating/moving;
  measure only after any Sodium rebuild/upload transition settles.

A successful checklist still validates an acknowledged scene policy; it does not
make automatic terrain coverage an implemented feature.
