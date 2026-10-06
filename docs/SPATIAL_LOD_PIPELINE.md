# Shared spatial evaluation and reconstruction kernel

2026-10-05. ShaderLOD original MIT mechanism:
[`spatial_pipeline.glsl`](../shaderpacks/common/shaders/include/shaderlod/spatial_pipeline.glsl).
It implements fixed screen tiles, all-member receiver classification, current-frame
cache handling and linear reconstruction. It contains no Photon algorithm,
material decoder, sampler, implicit derivative, uniform, temporal reuse or source
rewriter. Existing Photon sources/archives, Java and work/volume helpers are unchanged.
This is a bounded adapter handoff, not another supported pack or production pass graph.

Include `work_policy.glsl`, `spatial_plan.glsl` and
`surface_region_continuity.glsl` before `spatial_pipeline.glsl`. Copy those explicit
common files into an adapter's reviewed include tree, preserve MIT attribution and
record their hashes. Repeated guards use `#if !defined` rather than declaring Iris
boolean options. The [spatial planner](SPATIAL_LOD_PLAN.md) supplies distance
tier weights; the [region guard](SURFACE_REGION_CONTINUITY.md) supplies the existing
sampler-free geometry/material predicate. Its thresholds and optional lighting
spread remain heuristics; neither cache metadata nor this pipeline establishes a
bound on missed thin cast shadows.

## Adapter callbacks and declared quantity

The complete callback declarations are in the kernel:

```glsl
ShaderlodSpatialSurface shaderlod_spatial_adapter_surface(ivec2 pixel);
ShaderlodSpatialSpread shaderlod_spatial_adapter_spread(ivec2 origin, int side);
ShaderlodSpatialPlan shaderlod_spatial_adapter_plan(float distance_blocks);
vec4 shaderlod_spatial_adapter_evaluate_linear(ivec2 pixel, ShaderlodSpatialSurface surface);
ShaderlodSpatialCache shaderlod_spatial_adapter_cache(int side, ivec2 tile_coordinate);
```

`surface` supplies current-frame camera-relative **view-space blocks**, a normalized
normal in that same space, a nonnegative adapter-defined factorization class,
normalized lightmap and explicit receiver protection. Protection includes sky,
hand, invalid depth, overlays, unsupported material/projection/DH paths and all
other exclusions required by the declared factorization. Every member and pair
passes the existing guard, including interior pixels at 4 × 4. The kernel rejects
incomplete edge tiles before any out-of-bounds surface callback. Spread probes
must describe all members/relevant reconstruction neighborhoods; availability=false
makes no lighting-edge assertion. The adapter must not reconstruct invalid depth
into a benign position and thereby accidentally mark it eligible.

`plan` calls the shared planner with the adapter's immutable current-frame policy,
capability/quality/protection inputs and declared rate mask. Distances are radial
Euclidean lengths in blocks, never raw depth or view Z. The producer passes the
minimum distance across all tile members. The consumer replans at the minimum
across its active reconstruction tiles; if this introduces an unprepared tier,
it selects full. Producer/consumer policy and surface snapshots must agree.
A separately supplied `near_blocks` guard protects every member through an
adapter-declared full-quality distance; invalid guards retain original shading.

`evaluate_linear` supplies **albedo-independent irradiance or another explicitly
declared linear vec4 basis**. All channels must permit the same linear sum and
units. The quantity key identifies that exact basis/units; it does not authorize
arbitrary shading factors. Nonlinear BRDF, normal, visibility decisions, tone
mapping or final RGB colors cannot be cached and reinterpreted by this kernel.
No dividing final shaded color by representative albedo. Full-resolution albedo,
material decoding, emission, specular/reflections, fog and final composition
remain at the recipient pixel. If a declared irradiance includes representative
AO/shadow, that spatial approximation must be disclosed and tested; full sample
counts at a representative do not preserve all recipient shadow detail.

Callbacks reached from representative or fallback-dependent control flow must
use derivative-free data or reviewed explicit gradients/LOD and sampler state.
The kernel cannot make implicit derivatives valid. Establish gradients before
divergent flow if the adapter needs them; no unreviewed `texture` sampling belongs
inside `evaluate_linear` or `surface` callbacks under these branches.

## Producer, storage and consumer obligations

The public producer entry is:

```glsl
ShaderlodSpatialCache shaderlod_spatial_produce(bool enabled, bool cache_contract_valid, int side,
    ivec2 tile_coordinate, ivec2 dimensions, float near_blocks,
    ShaderlodRegionLimits limits, int quantity_key, int epoch);
```

`dimensions` describes rendered surface pixels, not display resolution. Legal
sides are 2 and 4: half and quarter dimensions on each axis, nominally one
evaluation per 4 or 16 receiver pixels. Cache dimensions are `ceil(dimensions/side)`
per axis, computed by `shaderlod_spatial_cache_dimensions`. Tile origin is
`tile_coordinate*side`. The deterministic representative is
`origin + ((side-1)/2, (side-1)/2)`: offset (0,0) at side 2 and (1,1) at side 4.
There is no random phase or temporal rotation. Partial odd-edge tiles publish
invalid entries and reconstruct at full resolution.

The producer classifies first, queries the shared plan at minimum member distance,
and evaluates once only if that tier has positive weight. Disabled producer,
unsupported/invalid plan, rejected region or undemanded tier publishes invalid
without evaluating. `linear_quantity` is accepted only if finite.

A real adapter must provide distinct current-frame lower-rate producer draws,
legal output attachments and a later full-resolution consumer draw. Neighboring
fragments cannot share the representative result during the same draw on GL 4.1.
The kernel owns no pass numbering, attachment index, viewport allocation or format.
Audit exact pinned Iris/pack source graph, attachment lifetimes, render-target
lists, clear/ping-pong behavior, texture dimensions, limits, read/write hazards,
DH/projection variants and sampler coordinates before integration. Render a 2 × 2
producer over its half-size cache viewport and a 4 × 4 producer over its quarter-size
viewport, or use another explicitly proved allocation with matching pixel mapping.
Never shrink unrelated full-resolution gbuffer attachments. Every reachable entry
must be written/cleared invalid each frame, including rejected or disabled tiles.
The consumer reads only completed producer outputs, with nearest integer tile
addressing; never bilinear-filter validity or cross an invalid tile hole.

`ShaderlodSpatialCache` is a producer certificate containing validity, integer
quantity key, epoch, side and tile coordinates, plus the minimum member distance
in blocks. The library sets validity only after all members pass the guard and
the declared representative evaluation is finite. Store metadata in an exact
representable integer attachment (e.g. a reviewed integer texture using integer fetch), or an adapter-proved exact packing and
collision policy. Half-float tags cannot prove current epoch equality. An epoch
must change on frame, resize, source/options/policy reload and any camera/world
change invalidating inputs; producer and consumer share it. Define overflow/wrap
and prevent old entries surviving a reused epoch (clear/reset); signed integer
increment overflow is not an acceptable implicit policy. The library compares
exact supplied ints, but cannot verify attachment format, freshness or resource
ownership. Store minimum distance as exact binary32 (e.g. R32F) or prove a
conservative rounding policy that never increases it. An approximate distance
rounded upward can authorize a later tier incorrectly. RGB precision/format and
its quality error also remain adapter duties.

`cache_contract_valid` is an explicit adapter proof/gate for supported exact
metadata storage, current dimensions/attachments, completed ordered producer
writes and one **immutable surface, protection, policy and guard snapshot** shared
by both stages. False rejects before any callback. A bare valid bit does not prove
geometry unchanged: rewriting depth/material/protection between stages, failing
to bump/reset the epoch after changes or decoding approximate tags invalidates
this contract and must select full. Full-resolution material composition remains
per pixel outside this kernel; excluded/protected receivers were certified at
producer time, and the immutable snapshot is what makes that certificate usable.

The full-resolution consumer entry is:

```glsl
ShaderlodSpatialResult shaderlod_spatial_resolve(bool cache_contract_valid,
    ivec2 pixel, ivec2 dimensions,
    ShaderlodSpatialPlan initial_plan, float near_blocks,
    ShaderlodRegionLimits limits, int quantity_key, int epoch);
```

Supply an initial shared plan from the pixel's already validated radial distance.
The kernel checks complete tile bounds and validates exact rate, tile coordinates,
epoch, quantity, finite value and conservative minimum-distance certificates
**before** evaluating the full-resolution linear basis needed during a transition.
It replans at the minimum distance from demanded certificates. Each producer
classified every interior/edge member; the consumer performs no repeated region
reads or 120-pair classifier. A full-basis transition reads only the current pixel
and retains full fallback if that receiver is explicitly protected/invalid. Any
rejected region, invalid/stale/wrong-quantity cache or invalid plan returns
`full_resolution_required=true`: the adapter must execute its untouched original
full-resolution shading path. The result's zero placeholder is not black shading.
A unity/full/disabled plan exits without surface, cache or evaluation callbacks.
A disabled adapter should omit the producer passes as well; issuing empty draws
still incurs pass overhead and cannot establish disabled parity.

Accepted reconstruction is
`full_quantity*w.x + half_tile_quantity*w.y + quarter_tile_quantity*w.z`.
Weights come from the shared planner and are finite, nonnegative, normalized with
consistent active-tier flags. Reconstruction is piecewise constant within each
certified tile; the smooth blend is between tiers, not unguarded spatial bilinear
interpolation. If replanning needs an absent cache tier, full fallback is safe and
may increase work near transitions. Full↔half executes original basis plus half
producer work, and half↔quarter executes both producers; their sum can exceed a
single target rate. A producer 4 × 4 classification still performs up to
16 reads, 120 pair checks and 18 triangle checks once per tile. Those costs, exact
certificate storage, extra passes, cache reads, full fallback and both transition
paths must enter whole-frame GPU measurements.

## Bounded verification and next evidence

[`test_spatial_lod_pipeline.py`](../tests/test_spatial_lod_pipeline.py) contains
complete minimal fake-adapter producer/consumer GLSL 410 translation units and a
host vector shim that compiles the actual kernel and existing region guard. The
host shim does not substitute a second classification/reconstruction algorithm.
Fixtures cover both coordinate mappings/representatives, odd edges before reads,
each member's mixed material/near/protected/plane fault, dual-tier/full blending,
invalid validity/epoch/quantity/rate/coordinate/distance/NaN/infinity cache entries,
unsupported certificate storage and full/disabled/illegal fallback. Cached resolves
assert zero receiver callbacks; a full-basis transition asserts one recipient
read. Interior discontinuities still reject at producer before evaluation.
The toy callback has no textures or Photon code. Host float semantics and offline
GLSL semantic compilation are separate from driver arithmetic and Iris execution.

After the operator formally returned the client/GPU, the bounded command
`python3 -m unittest discover -s tests -p test_spatial_lod_pipeline.py -v`
passed all **3 tests** in **0.563 s**, with no skips. This includes actual kernel/
region source host execution, 80 individual receiver faults across 2 × 2/4 × 4,
and glslang semantic compilation of three complete GLSL 410 fake producer/consumer
stages. First host compilation failed on a redundant scalar `abs` shim conflicting
with macOS libc++; removing that shim repaired the fixture, with no shader change.
The later all-pass result retains that initial harness failure as a harness failure.

Acceptance source SHA-256:
`d0f44c038320fb8f68bc3769888f69d7db9b23b47e335861e4d59e33530652c9`.
Fixture SHA-256:
`7f19c417077e1228abed18a067f68d543700df78423d244879617d3c78df57d9`.
Planner consumed by complete fixtures:
`70738064fd0dad8b63af95868be55d7df6aba337f398b3d6ff6ca5c5f5709033`.
Offline validator `/usr/local/bin/glslangValidator`, version `11:16.0.0`, SHA-256
`f1e06ffb9736c6ca6896a247af859add389c465d21a1ebc89e815f1cbdd8ef95`.
These hashes bind the reported checks to source/tools; they do not establish
runtime driver compilation or effective Iris consumption.

No client, GPU, shader reload or Java/Gradle build ran for this contribution.
No live process or client resource remains owned.
Before integration acceptance, verify linked effective options/capabilities,
actual producer outputs and full-resolution composition, original/OFF parity,
resizing/reload/cache invalidation, near and odd-edge fallback, transition motion,
albedo detail, material boundaries, thin foliage, water exclusion, contact/cast
shadows, reflections and optional DH. Coordinate all driver/client reloads with
the operator. Then collect repeated matched original, disabled and optimized
whole-frame GPU and total-frame timings on the same source/settings/hardware and
quiet workload. No performance gain or general project completion follows from
these kernel fixtures or nominal evaluation rates.
