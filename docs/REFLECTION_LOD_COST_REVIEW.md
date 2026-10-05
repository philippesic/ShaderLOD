# Frozen reflection adapter: source cost review

2026-10-05. Review-only owner of this new document. The adapter, manifest,
builder, tests, archive/runtime pins and other workers' files remain unchanged.
No client, build, driver compiler, profiler, image operation or heavy test ran.
The active general50% objective remains. This is a source operation/candidate
eligibility audit, not measured performance or quality acceptance.

## Current evidence and inspected source

The default-off archive SHA256
`d11de4c94fb58520e9972a816e9739486fa8578b876fbd20c17aa933a4a82e16`
compiled/rendered in the untimed native noDH coastal smoke. The runtime queue
records core2/enable1/near32/far96/width8 typed values,366unchanged upstream options,
privategate=true, no fallback and no GPU samples. A single still shows no obvious
broad bright band; original/off parity and motion quality remain unaccepted.
The first fullscreen depth allocation1708x960 and later raster2976x1672 reflect
warmup resize; these are actual observations, not an assumed universal allocation.
Readbacks do not reveal how many rays hit, cache keys repeat, guards fail, or
refinements stop. There is no measured typical cost/savings distribution.

Frozen helper SHA256
`b5dd57b6f6155ada9489416ad94c5b4be00286615b9fce71ecf09400968ca44a`;
manifest `0c20609c50ee65d98ec803eb96294a0d727bff82d81a6634abb1504518d89e1a`.
Line references below address
`shaderpacks/photon-reflection-lod/shaders/include/shaderlod/reflection_depth_cache.glsl`.
Pinned original Photon v1.3b archive is12089776…f1ead; original raytracer SHA256
`10aecc98cec4db7828787423000730bb2b16b06bd4730fd72fe72cb46c0d6aba`.
Original `shaders/settings.glsl`413–416 defines raycount4, smooth intersections16,
rough8 and refinements4, with supported refinement options0..8. The wrapper
accepts at most64refinements but ordinary upstream options do not request64.

Original `shaders/include/global.glsl`238–242 defines the actual projection helper:
`project(m,p) = vec4(m[0].x,m[1].y,m[2].zw) * p.xyzz + m[3]`.
Global member SHA256
`6235933d0045282ba9d7822d55a40aaff6b76ac862c550e33f9b8a4229d63c3d`.
This fixed diagonal/perspective structure, used by original hit composition,
allows a cheaper sound homogeneous endpoint-error bound. Do not assume arbitrary
matrix structure; the future patch must pin this actual helper too.

## Dynamic source work: conditional counts, not driver instructions

Let K be original coarse fetch sites reached (0..I), R be original refinement
budget, and m be refinements remaining after an update. A coarse miss has no
refinement. A successful hit has K>=1 and R=4 in the inspected ordinary profile.

| Conditional path per ray | Original | Frozen selected adapter |
| --- | --- | --- |
| Coarse miss, every key distinct | K depth fetches | Same K; one textureSize plus K cache/bounds/key tests and state updates; zero fetch saving |
| Coarse hit, following refinement keys distinct | K+R fetches | K+R−1 fetches if first hit texel valid; cache checks at every site plus up to R−1 envelope attempts |
| Successful first refinement stop, all later sites same texel | K+R fetches | K fetches if coarse keys distinct; one update and one envelope/projection attempt |
| Same-texel path with stopping disabled/failed | K+R fetches | Also K fetches when all refinement sites repeat hit key; R cheap cached updates plus repeated guards |
| Near/invalid policy/global-off | Original trace | Original trace plus compiled wrapper validation; distance length currently evaluated even before checking bridge |

**The early-stop proof yields zero additional depth-fetch savings over the
adapter's existing cache-only continuation.** Once the proof succeeds, every
remaining site lies within the same valid immutable fetched texel. Its cache
would already reuse that depth at all m subsequent sites. Early stop removes m
ray-step multiplications, coordinate/key/bounds checks, depth-hit predicates and
position updates; the expensive inverse projections cannot be credited with m
additional saved physical fetches. Relative original Photon, fetching once instead
of R times is an exact cache saving; it is a separate hypothesis from refinement
iteration LOD. CPU scalar-model counts are not physical compiled texture counts.

The per-site coordinate multiplies/casts already exist upstream. Added cache work
at lines70–81 /101–112 includes4bounds comparisons, two-key comparison when valid,
boolean reductions/branches and carried key/depth/valid state. Integer key reuse
adds no depth sample. On distinct-key misses this is pure overhead. TextureSize
is one query per selected ray; drivers may lower it to a uniform, not a sampled
texture operation. Register occupancy/cost is unknown.

For every nonterminal refinement, lines128–148 currently evaluate an exp2,
remaining-span3-vector arithmetic, three projected2D scales (span/lower/upper),
rounding margin,4envelope comparisons, original-hit interval classification and
pixel/z tests before inverse projection. Expanded scalar source expressions amount
to approximately21multiplications,19add/subtract operations,7absolute values,
4max operations and at least10scalar comparisons plus reductions/conversions.
These counts deliberately exclude loop/cache/update work. They are source-expression
counts; compilers can hoist, fold, fuse or predicate them, so they are not an
instruction/cycle/bandwidth estimate. A failed cheap pixel/z bound still pays for
bounds/classification because the current checks are late at146–148.

If the scalar/envelope checks pass,153–165 recomputes the same exp2/extent, does
three `project` calls (each4multiplies+4adds under the actual pinned helper),
three vec3 homogeneous divides (9scalar quotients),6coordinate differences,
6abs,3max, a3component dot and W guards. Some divisions may share reciprocals;
no compiled instruction form is known. A failed W or world-error check adds
this work and then continues original refinement.

| Original budget | Maximum envelope attempts if no stop | Maximum inverse attempts if all cheap guards pass but world bound fails | Extra exp2 calls in that extreme |
| --- | ---: | ---: | ---: |
| R4 | 3 | 3, hence9project and9vec3-divide expressions | 6 |
| R8 | 7 | 7, hence21project and21vec3-divide expressions | 14 |

These maxima are mutually conditional. The z bound restricts common R4 paths:
when depth_tolerance=3*abs(original coarse step.z), first-refinement remaining
span is(7/16)*abs(coarse step.z), but accepted span is at most(3/16)*abs(step.z).
The first stop therefore cannot pass for a nonzero step.z under that tolerance
term. At the second update, m2 gives remaining span3/16 and can pass at equality.
First-update stops need a sufficiently larger tolerance from the alternative
0.02/view_pos.z² term, or zero/nearly-zero step.z. Thus an unconditional m>=3
eligibility gate would suppress ordinary potential stops in this dominant branch.
Use m>=2 for a bounded cost policy; do not claim first-update3-step saving as a
typical R4 result. All of these statements follow the source, not measured ray
frequency.

Worst original smooth hit at K16/R4 reaches20depth sites. For distinct keys the
candidate guarantees only first-refinement reuse (19fetches) while paying cache
and guard work. A same-refinement-texel hit can use16fetches, whether expensive
stopping succeeds or not. Rough rays multiply this source work by actually
accepted ray samples (upstream raycount4 is a loop budget, not guaranteed executed
traces). Most coarse-miss/full-near cases cannot obtain refinement savings at all.
No aggregate scene time follows from these numbers.

## Exact future patch proposal, requiring a new reviewed source freeze

Do not apply this proposal to archives being tested. Root must assign source
ownership, revise manifest/helper pins, update combined transforms/profile
qualification, and rerun relevant source/parser/driver gates before a new run.
No change to distance units, core protocol2, disabled baseline, full traversal
coverage or normalized reflection accumulation is proposed.

1. At helper181–196, put the uniform bridge/version/global-validity fallback before
   `length(view_pos)`. Preserve finite policy/count/dither validation. Compute radial
   blocks only after uniform policy can activate. Invalid/absent/global-off should
   dispatch directly to the original tracer without evaluating length/smoothstep.
   This alters only work selection, not any ray output.

2. At126–148, evaluate the necessary pixel/z bounds immediately after computing
   remaining span. Keep extent in one scalar for use below; delete the duplicate
   exp2 at153. Optionally require m>=2, a conservative quality policy that executes
   remaining original updates when only one would be skipped. Do not require m>=3
   in ordinary R4. Proposed order:

   ```glsl
   int remaining_steps = int(refinement_step_count) - i - 1;
   if (remaining_steps < 2 || !cache_valid || !(refinement_tolerance_pixels > 0.0))
       continue;
   float extent = 1.0 - exp2(-float(remaining_steps));
   vec3 remaining_span = abs(ray_step) * extent;
   vec2 span_pixels = remaining_span.xy * view_res * taau_render_scale;
   if (!(max(span_pixels.x, span_pixels.y) <= refinement_tolerance_pixels &&
         remaining_span.z <= depth_tolerance * 0.0625))
       continue;
   // Only now evaluate one-texel envelope and uniform hit-predicate margins.
   ```

   These are necessary conditions already required by frozen acceptance, now
   evaluated before geometry/envelope work. Negated <= form fails closed on NaNs.
   Full original arithmetic runs on failure; no late texture fetch is performed
   and discarded. Extent reuse is the same exact expression/result as frozen code.

3. Replace153–165 three project/divide calls with the perspective-structure bound
   below. Preserve all texel/classification/pixel/depth guards and 0.049block norm
   reserve. Compute scalar z/W parts first; reject on necessary world-z displacement
   before constructing x/y expressions. This tests the actual world-space error,
   not a screen-depth proxy. Using M=SSRT_PROJECTION_MATRIX_INVERSE, P=2ray_pos−1
   and Delta=2ray_step*extent, define:

   ```text
   H = project(M,P)
   dH = vec4(M[0].x,M[1].y,M[2].zw) * Delta.xyzz
   w_min = abs(H.w) − abs(dH.w)
   D = abs(H.w) * w_min
   N.xyz = dH.xyz * H.w − H.xyz * dH.w
   ```

   Require finite inputs/intermediates, w_min>1e−6, abs(H.w)>1e−6 and D>0.
   A necessary cheap scalar z gate is `abs(N.z) <= 0.049*D`. If false,
   Euclidean0.049block error is impossible; continue original refinements.
   Then accept only `dot(N,N) <= 0.002401*D*D`, with finite squared terms and a
   documented conservative FP margin. Scaling N/D by a shared finite power-of-two
   may avoid overflow/underflow if later fixtures need it; never accept Inf<=Inf.

   Proof: endpoint difference for component j is
   `±(dH.j*H.w − H.j*dH.w)/(H.w*(H.w±dH.w))`.
   Since abs(H.w)>abs(dH.w), the homogeneous denominator has no pole over the
   entire remaining segment and every component is monotone. Its maximum absolute
   endpoint error is at most abs(N.j)/D. Thus sqrt(dot(N,N))/D bounds Euclidean
   displacement over the entire segment. It is the same conservative coordinate
   envelope that frozen max(abs(endpoint−center)) constructs, without three
   homogeneous vector divisions. In exact arithmetic this is sound for Photon's
   actual project helper, including whichever DH/combined inverse matrix the
   original code passes. It is not a claim about arbitrary new projection code.

   The z/W cheap precheck needs scalar `H.z/H.w` numerators and homogeneous changes
   only: H.z=M[2].z*P.z+M[3].z, H.w=M[2].w*P.z+M[3].w,
   dH.z=M[2].z*Delta.z, dH.w=M[2].w*Delta.z. No inverse division or x/y work is
   needed on z rejection. Computing N.z with the same multiply/subtract expression
   is preferred initially; an algebraic determinant cancellation shortcut needs
   its own floating-error fixtures before adoption.

   Successful full checks need one project plus one homogeneous delta rather than
   three project calls, no homogeneous divide and one reused extent. The reduction
   is in source math, not proven GPU cost. Existing 1mm margin is not a general
   IEEE error proof: test high-depth amplification, cancellation, W-near-zero,
   large/small coordinates, all signs, actual matrices and conversion rounding
   before making any safety-equivalence assertion.

4. At60–81, consider restoring literal original coarse texelFetch and saving only
   the successful hit's key/depth for first refinement. No-hit rays then avoid K
   cache branches/state updates, and distinct-key coarse hits avoid useless checks.
   This removes rare exact coarse duplicate-key reuse but changes no sample
   positions/depth results. Allocate/query cache dimensions only after a hit, or
   query once when actually validating that hit. This is a separate measured
   implementation choice, not automatic authorization to modify frozen source.
   Keep invalid/out-of-bounds behavior unmodified and never reuse undefined depth.

A once-only expensive eligibility attempt can be a conservative additional cost
policy, but should not be the first repair: pixel span becomes smaller on subsequent
updates, so first failure does not imply future failure. An m>=2 gate plus necessary
bounds and closed-form world test attacks work without throwing away every R4
candidate. If further ALU remains costlier than cached continuation, explicitly
retain cache-only control for attribution rather than credit early stop with fetch
savings it cannot produce.

## Next acceptance and interpretation

Numerical fixtures should compare the closed-form bound to all original endpoint
projections and sampled interior positions under the pinned project helper, including
NaN/Inf/poles, hand/sky classification, depth boundaries, tiny spans, large view
positions and float32 rounding. Keep literal off/global-invalid/full-near source
fallback and original ray/weight tests. Source acceptance remains separate from
actual compiler lowering, GPU elapsed measurements and reflected-color/fog QA.

A future cost comparison must include original/mod-absent, same private/global-off,
exact cache-only continuation and footprint early-stop arms with equal source
qualification/options/focus/resolution/warmup, balanced ordering and repeated GPU
and total-frame intervals. A cache-only arm is a private explicit diagnostic,
not an upstream quality/preset reduction. Do not fabricate eligible/hit distributions
from first-draw readbacks. A source counter requires a separately reviewed diagnostic
whose instrumentation overhead cannot be included as ordinary performance proof.
The current driver-rendered freeze remains available as provenance; this report
contains no measured gain/regression or change to default/support/goal status.
