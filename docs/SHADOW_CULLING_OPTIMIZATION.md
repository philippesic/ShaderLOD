# Exact shadow-frustum CPU optimization candidates

Source/bytecode audit and first implementation, 2026-10-04. Pinned inputs are Iris NeoForge
1.8.12 (`t3ruzodq`) and Sodium NeoForge 0.6.13 (`Pb3OXVqC`) in
`dev/runtime.lock.json`; exact class bytecode was inspected with `javap -p -c`.
This retains the current MC 1.21.1 / NeoForge / Photon target.

## Implemented first candidate

The first boolean-only candidate is implemented in production
`ShadowCullingMath.java`, `ShadowCullingController.java` and
`mixin/ShadowCullingFrustumMixin.java`. No tri-state lazy-negative change or
visibility caching was implemented. The mixin has exact `@Overwrite testAab`
paths for the base frustum and its reversed subclass; its disabled path keeps
the original distance/box ordering and tri-state predicate. It introduces no
callback object or varargs wrapper on either path. The reversed implementation
calls the base mixin's plane helper through a small inherited interface.

Register both `ShadowCullingFrustumMixin` and
`ShadowCullingFrustumMixin$Reversed` in the main mixin configuration. Targets
are pinned Iris class/method/field signatures with `remap=false`, including
`planes`, `planeCount`, `boxCuller`, `FMA_SUPPORT` and reversed `distanceCuller`.
Compile acceptance alone does not prove Mixin application or transform order;
the coordinator must perform actual runtime validation after registration.

The first actual coordinator smoke launch failed before rendering: the nested
Reversed mixin could not resolve an inherited `checkCornerVisibility` shadow.
The failure is retained in
`dev/logs/culling-smoke/001-b1-water-culling-minimal-console.log`; it is not a
runtime pass. The repair removes all inherited shadows from that child: it
shadows only the subclass's declared `distanceCuller`, while the base-injected
`PlaneAccess` interface exposes original classification and reversed-box
acceptance helpers. Those helpers access members on their actual declaring
target. The child inherits the interface through the transformed base class.
This preserves decision ordering with scalar interface calls and introduces
no callback, varargs, reflection or allocation. The repaired transformation
still needs the coordinator's new runtime check.

Controller hooks are `configure(boolean)` for global/feature enablement,
`setEligible(boolean)` for tick-time Photon/world/pipeline eligibility and
`isActive()` for one volatile boolean read in the AABB path. Setters are
client-thread APIs. Initially both flags are false. The root integration owns
config defaults, pack detection, reload/world invalidation and eligibility;
no Iris/GL/reflection/config reads or allocation occur in the math/hot helper.

`./gradlew compileJava test --tests com.pp.shaderlod.ShadowCullingMathTest`
passed. Eight tests compare the optimized boolean to an independent full
tri-state oracle reconstructed from pinned Iris bytecode, including 50,000
random plane/box sets under both FMA policies, all return classifications,
plane-count boundaries, tangency/adjacent ULPs, negative coefficients,
signed zeros, NaNs, infinities, overflow/subnormals and inverted endpoints.
A case deliberately distinguishes FMA from separate rounding; a signed-zero
case distinguishes `<0` selection from raw sign-bit selection. Controller
configuration/eligibility invalidation is also tested. These are meaningful
math/config tests, not a claim that actual distance-culler dispatch or runtime
visible-caster lists have been checked. No microbenchmark or game launch was
performed by this worker.

The exact Iris JAR declares `LGPL-3.0-only`. Its behavior/bytecode is the
attributed reference here; do not confuse this with Photon's separate custom
shader agreement or assume the repository's MIT notice changes upstream terms.

The coordinator supplied a good no-GPU-query baseline near 144 FPS. Its JFR
Java samples included frustum-related frames (75), advanced-frustum corner
testing (46) and AABB testing (41) among 418 samples; native sampling separately
included multidraw (240) and texture copy (80) among 507. These are sampling
observations, with overlapping stacks and different denominators. They are
not execution milliseconds, percentages of whole-frame cost, or evidence that
an earlier slow 75-FPS run had the same cause.

## The pinned algorithm is already a vertex-plane test

`net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum`
does **not** allocate/evaluate eight corner vectors per AABB. Its constructor
builds up to thirteen planes, including light-extruded edge planes, with JOML
vectors. Per-box `checkCornerVisibility(FFFFFF)I` instead chooses positive and
negative vertices using coefficient signs and evaluates two dot expressions
per plane. Constructor vector allocation should not be confused with this
hot per-box path.

The relevant pinned methods and bytecode offsets:

| Method | Behavior |
| --- | --- |
| `checkCornerVisibility`, offsets 24–73 | Positive vertex: select min coordinate if coefficient `<0`, otherwise max |
| Same, 75–114 / 205–238 | Evaluate positive dot; reject unless result `>= -plane[3]` |
| Same, 115–198 / 239–322 | Evaluate negative dot and update fully-inside flag, for every surviving plane |
| Same, 335–345 | Return 1 if fully inside, 2 if intersecting; outside returned 0 earlier |
| `testAab`, 0–53 | Preserve Sodium distance box rejection, call tri-state check, use only `>0` |
| `isVisible(DDDDDD)`, 0–74 | Subtract camera in double, cast each endpoint to float, then classify |
| `fastAabbTest` | Distance check plus tri-state result, potentially consumed as a classification |

FMA support is selected from HotSpot's `UseFMA` flag. Positive dot order is
`fma(px,x, fma(py,y, pz*z))`; negative dot uses
`fma(px,x, fma(py,y, fma(pz,z,pw)))`. In the fallback, `safeFMA(a,b,c)` is
separate float multiplication then float addition. Preserve that rounding
order; algebraic equivalence over real numbers is insufficient at the plane
boundary.

## Rank 1: boolean-only positive-vertex test for Sodium

Sodium's hot boolean entry point does not need the fully-inside classification.
Keep the existing box-culler check, then evaluate only each plane's positive
vertex with the **same** arithmetic and comparison as the tri-state routine:

```java
for (int i = 0; i < planeCount; ++i) {
    float[] p = planes[i];
    float x = p[0] < 0.0f ? minX : maxX;
    float y = p[1] < 0.0f ? minY : maxY;
    float z = p[2] < 0.0f ? minZ : maxZ;
    float dot = useFma
        ? Math.fma(p[0], x, Math.fma(p[1], y, p[2] * z))
        : p[0] * x + (p[1] * y + p[2] * z);
    if (!(dot >= -p[3])) return false;
}
return true;
```

This pseudocode describes the proposed pure math path, not an existing change.
Use explicitly grouped float intermediates matching `safeFMA` if a test/compiler
setting could otherwise alter the arithmetic. Do not alter plane generation,
distance limits, inputs, clipping padding or visible section lists.

Proof against the original `checkCornerVisibility(...) > 0` is direct:
the original returns zero if and only if one of those positive comparisons
fails. Its negative comparisons can only select the nonzero result 1 versus
2. Discarding them in a boolean-only consumer therefore produces the same
result for all float inputs, including unusual ones, provided positive
selection/evaluation/rejection is unchanged.

| Per surviving plane | Original FMA path | Proposed boolean path |
| --- | --- | --- |
| Dot work | 5 FMA calls + 1 ordinary multiply | 2 FMA calls + 1 ordinary multiply |
| Coordinate sign selections | 6 | 3 |
| Fully-inside flag/comparison | Always computed | Absent |
| Non-FMA work | 6 multiplications + 5 additions | 3 multiplications + 2 additions |

An AABB rejected by its first positive test saves little, while intersecting
or fully visible boxes save the negative work for all preceding/surviving
planes. With thirteen surviving planes this removes up to 39 FMA evaluations;
these are source operation counts, not a promised instruction/time saving.
The JIT may inline and optimize the original; measure compiled behavior.

Do **not** simply redirect to the existing `checkCornerVisibilityBool`:
that method always calls FMA and rejects with `< -pw`. A NaN positive dot
passes that rejection, whereas the tri-state acceptance `>= -pw` rejects it.
Its bytecode uses `fcmpg` for the former and `fcmpl` for the latter. They are
not exact substitutes, even if ordinary finite scene inputs usually agree.

Keep these details exact:

- Tangency (`dot == -pw`) remains visible. Preserve the original sign branch
  for both `+0.0` and `-0.0`; do not replace it with raw sign-bit selection.
- NaN coefficients/endpoints, infinities, overflow, subnormal values and
  inverted min/max endpoints retain the original evaluation behavior. Do not
  normalize/sort/clamp or add a new "conservative" NaN rule.
- Sodium inputs are already camera relative. Subtracting frustum x/y/z again
  would corrupt culling. Preserve `boxCuller.isCulledSodium` exactly.
- Reversed frusta override `testAab`: first distance-cull, then immediately
  accept a box inside their alternate box culler, otherwise classify. Keep
  that order and change only their final boolean plane-test call.

A narrow version-pinned mixin can replace only the tri-state invocation used
by `AdvancedShadowCullingFrustum.testAab` and its reversed override with a
boolean helper encoded as 0/1. Other callers retain exact 0/1/2 behavior.
Avoid a thread-local "boolean mode" or globally weakening the classifier.
Preserve disabled behavior and fail clearly if the exact target methods change.

## Verified Sodium and DH consumer paths

Pinned Sodium `render.viewport.Viewport.isBoxVisible(IIIFFF)` computes each
relative center from integer camera origin plus fractional offset, then calls
the frustum interface's `testAab` with center minus/plus extents (offsets 60–99).
`chunk.occlusion.OcclusionCuller.isWithinFrustum` supplies section centers and
**9.125f** extents. Preserve that padding; changing it is geometry culling,
not equivalent math optimization.

Iris `ReversedAdvancedShadowCullingFrustum.testAab` also ultimately uses the
tri-state classifier's boolean result, but has distinct distance/box behavior
as described above. Photon colored-light variants can select reversed culling;
the current Mac mask's disabled colored lights do not validate that branch.

Iris's DH mixin `compat.dh.mixin.MixinAdvancedShadowCullingFrustum.intersects`
builds a world-space box using the provided section x/z/width and saved world
min/max Y, calls `isVisible(DDDDDD)`, then uses nonzero. This is a separate
potential boolean consumer, but it needs original double subtraction/float
conversion and the DH interface's dimensional bounds preserved. Do not fold
it into the first Sodium-only prototype without differential tests.

## Rank 2: stop negative work after intersection is established

For tri-state consumers that genuinely need 1/2, keep every positive plane
test and the original return codes. Compute the negative expression only
while the fully-inside flag is still true. Once any negative comparison fails,
the final result can never return 1, so later negative expressions cannot
change the final result; they have no side effects. Outside rejection still
runs on all following planes and can still return 0.

This preserves finite and non-finite arithmetic outcomes used by the result;
it omits only pure expressions whose boolean conjunction is already false.
It can save up to three FMA evaluations for each subsequent surviving plane,
but adds a flag branch and does not help boxes inside all planes. Benchmark
inside/intersect/reject distributions before adopting it. This is secondary
to removing all negative work in the actual boolean Sodium consumer.

Do not change to a center/extents radius formula as the first optimization:
it introduces half-sums, absolute-value products and different rounding.
Precomputed plane sign masks are a smaller optional follow-up, but constructor
cost and JIT constant/branch behavior must justify them. The current method
already has no per-box allocations to eliminate.

## Visibility preparation is not demonstrated redundant work

`ShadowRenderer.renderShadows` prepares terrain and entity frusta separately
(bytecode calls at offsets 359 and 651), invokes terrain setup with its shadow
frustum (421), and wraps the phase with culling-state save/restore where the
LevelRenderer implements `CullingDataCache` (272 / 1064). Different frusta can
have different distance/entity settings; these calls are not interchangeable.

Pinned `compat.sodium.mixin.MixinRenderSectionManagerShadow` already keeps
separate shadow render/task lists and suppresses chunk update/upload work
inside the shadow phase. `MixinSodiumWorldRenderer` forces a shadow graph
rebuild when the interpolated sun angle changes, otherwise delegates to
`RenderSectionManager.needsUpdate`. It also terminates the shadow rebuild
loop rather than repeating the ordinary world loop. Sodium world setup
already uses graph-dirty/needs-update state.

A second world/shadow visibility pass therefore cannot be removed just
because the camera stayed still. Correct reuse needs epochs covering world
and dimension identity, renderer/pipeline reload, exact camera/frustum planes,
sun angle/light vector and shadow projection/texel snap, render/distance
settings, fog/occlusion mode, section add/remove/rebuild/upload and visibility
graph changes, plus the appropriate world versus shadow list generation.
Entities, block entities and optional DH buffers have their own lifetimes.
Until those generations are actually available and tested, cross-frame list
caching is a higher-risk unproven design, not a ranked implementation.

## Acceptance plan and stop boundary

1. Differential pure math tests compare the new boolean result to original
   tri-state `>0` for both FMA and fallback arithmetic. Generate plane sets
   and boxes with inside/intersect/outside cases, zero planes, tangency and
   adjacent ULP boundaries, negative coordinates, signed zeros, subnormals,
   huge/overflow values, infinities, NaNs and inverted endpoints. Test the
   reversed box/distance acceptance order separately. Preserve the original
   code as an independent oracle instead of mirroring the new helper.
2. Test the tri-state lazy-negative candidate against exact original 0/1/2
   results over the same cases. Test caller conversion behavior separately,
   including Sodium camera-relative values and optional DH world-space casts.
3. A warmed Java 21 microbenchmark compares baseline/helper with observable
   result checksums and realistic mixed plane/box distributions; repeat runs
   with FMA enabled/disabled. Inspect allocation and JIT inlining rather than
   assuming helper extraction saves work. This does not promise whole-frame
   speedup.
4. Runtime validation uses the original pinned Photon quality settings,
   unchanged section/shadow distances, matched camera and movement. Compare
   exact visible-section/caster identities and counts, shadows at plane edges,
   world/chunk reloads and reversed/optional-DH cases where supported. Then
   repeated serial frame/CPU timing compares feature absent/disabled/enabled,
   with and without the optional optimizer stack. GPU queries stay disabled
   for the CPU proof unless separately shown low overhead.

Stop if differential correctness fails or compiled tests/runtime show no
useful net benefit. Native multidraw and copy samples motivate other profiling,
but this pure CPU change cannot eliminate driver submission or texture-copy
work. It also cannot establish a generalized shader benefit or unlock second
shader support by itself.
