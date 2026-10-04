# Opt-in detailed terrain cap

The geometry prototype caps the **effective** vanilla render radius while DH
keeps its existing long reach. It is disabled by default and requires explicit
confirmation that the current scene's replacement ring has warmed DH terrain.
This is an existing vanilla/DH geometry tradeoff exposed as a reversible policy;
it does not add a mesh renderer or promise a particular speedup.

## Configuration and wiring

```toml
[terrain]
enabled = false
vanillaChunkCap = 16
warmSourceConfirmed = false
warmupSeconds = 30
```

The main ShaderLOD enable flag must also be true. `vanillaChunkCap` accepts
8–32 chunks, and warmup accepts 5–120 seconds. Enable the policy only in a world
whose DH replacement terrain you have inspected and warmed; then set
`warmSourceConfirmed=true`. The acknowledgement binds to the world/dimension instance, Iris pipeline,
camera entity, current 16-block horizontal chunk and vertical section, requested
radius, cap and DH distance. A persisted true waits through initial startup and
warmup before first binding. After activation, crossing a horizontal chunk or
vertical section boundary, changing the camera/world/pipeline/radius/cap/DH
distance, or losing readiness revokes it. Returning to the old position does
not re-enable the cap. Clear `warmSourceConfirmed`, inspect the new replacement
ring, then set it true again. The 16-block cells limit acknowledged travel;
they do not prove coverage within a cell.

The main constructor registers the client event handler once:

```java
NeoForge.EVENT_BUS.addListener(GeometryLodController::onClientTick);
```

`GeometryLodController.status()` exposes active/requested/effective radius,
configured cap, eligibility reason, API failure detail, and stable/warmup tick
counts for a HUD or diagnostic command. `confirmCurrentScene()` accepts an
explicit inspected-scene action using cached live eligibility; it re-arms
acknowledgement without bypassing warmup. The caller sets/saves
`warmSourceConfirmed=true` on success. `revokeCurrentScene()` restores the
original effective radius immediately; clearing/saving the flag makes revocation
persistent. No new shader uniform or sampler is
required. Geometry can use original Photon while ShaderLOD is loaded; that
allows a geometry-only comparison with original shadow/AO quality.

## Behavior

An injection at `Options.getEffectiveRenderDistance()` return applies
`min(originalEffectiveDistance, cap)` only while the cached policy is active.
It never writes the raw `renderDistance` option, `options.txt`, or the client's
broadcast render preference. A manual or server-imposed radius below the cap
remains below it. If the user changes the requested radius while capped, turning
the policy off recovers that **current** value rather than an old snapshot.

Sodium and the pinned DH NeoForge wrapper both read this getter, so DH's vanilla
overdraw boundary sees the same radius as detailed rendering. The policy does
not modify DH128 (or any configured DH distance), its mesh quality, or terrain
generation. It also changes other consumers of the effective radius, including
vanilla fog/entity view scaling; this is a radius policy, not merely a draw-call
cache. Inspect the vanilla/DH seam, water and shadow coverage.

Sodium detects an effective-distance change in `setupTerrain` and rebuilds its
renderer. Use fixed caps and explicit changes; do not tune this value from noisy
per-frame FPS. Initial enable/disable and changing the cap are excluded from
steady-state timings until rebuild/upload and terrain overlap settle.

## Readiness and restoration

At client tick boundaries (nominally 20Hz), the controller requires a loaded
world, an active Iris shader pipeline with a DH terrain program and initialized
DH compatibility instance, and public DH API world/config readiness with DH
rendering enabled. Those checks use the exact pinned Iris seams and cached
reflection handles for the optional public DH API. DH must use its normal
`DEFAULT` rendering mode and its configured distance must reach the full
requested vanilla radius. Debug triangles and shorter DH reach are ineligible.
The hot getter performs no
reflection, config reads, allocations, GL calls, or renderer reloads.

A stable world/pipeline/readiness interval must reach the configured warmup.
Policy/mod disable, DH rendering disable, Iris fallback/failure and unload
recover the original effective radius by the next client tick. World/dimension
identity and camera-cell changes also bypass the cap immediately in the getter,
before the next tick. A getter-observed excursion latches revocation even if the
camera returns between ticks. Null/uninitialized DH API, absent DH, and API linkage failures fail open
with a diagnostic reason. The code never calls Iris `DHCompat.checkFrame()` or
`hasRenderingEnabled()` from an Options getter, because those methods can cause
pipeline reloads.

**Readiness is not a cache-coverage proof.** Initialized renderer resources and
30 seconds elapsed cannot establish that every replacement chunk exists. The
prototype therefore requires the explicit scene acknowledgement and is intended
for inspected, warmed scenes. Travel now restores full detailed radius
conservatively, but movement within a cell, terrain edits, DH mesh-quality
changes, independent render-cache clearing and third-person camera offsets are
not automatic coverage proofs or fully detected invalidation events. Disable it
before exploring uncached areas or changing DH mesh settings. Automatic coverage
verification/recovery is future work. The exact public API and internal seams
are audited in [DH_COVERAGE_AUDIT.md](DH_COVERAGE_AUDIT.md).

## Verification

Static checks:

```sh
./gradlew compileJava test --console=plain
```

Ten dependency-free policy tests cover all eligibility guards, exact warmup
boundary, server/manual limits, current-preference restoration, safe cap bounds,
initial startup eligibility, acknowledgement isolation and latched invalidation,
negative-coordinate travel boundaries and insufficient DH reach. Build success does not
establish mixin application, actual DH overlap, or performance.

Required runtime checks: original vanilla24/DH128, simple original
vanilla16/DH128, and requested24/effective16/DH128 policy with original lighting.
Record CPU world submission and complete CPU/GPU/frame metrics after settling.
Toggle policy/mod/DH/shaders off, change worlds/dimensions, edit the manual radius,
and verify restoration. Inspect cold-cache behavior with confirmation false,
near objects, distant tree silhouettes/shadows, the boundary and moving water.
Cross a chunk/vertical-section boundary, return without re-acknowledging, switch
camera entities, reload shaders, reduce DH reach below the requested radius and
select DH debug/disabled rendering modes. Verify uncapped restoration in each
case and explicit false→true acknowledgement after a newly settled scene.
The coordinator records actual client evidence separately; the added checks are
currently unit/static-verified, not new runtime acceptance evidence.
