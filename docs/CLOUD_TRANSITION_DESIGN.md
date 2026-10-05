# Conditional cloud-transition redesign

Prepared 2026-10-04 during the live nine-arm effect sweep. Research only: this
worker wrote this document and read pinned sources/complete reports; no source,
profiles, compilers, CPU fixtures or game clients were changed/run. Neither
clouds nor their transitions have been established as the slowdown's cause.
Only pursue these two options if repeated cloud-only controls identify useful
cloud work to optimize or a reproducible cloud-specific quality problem.

## Existing sampling and why shoreline differs from forest

`program/d1_clouds.fsh` builds the atmosphere-space viewer:

```text
air_viewer_pos = (0, planet_radius + CLOUDS_SCALE*(eyeAltitude-SEA_LEVEL), 0)
                 + CLOUDS_SCALE*gbufferModelViewInverse[3].xyz
```

Each volumetric layer intersects its own spherical shell. Cumulus and
altocumulus choose tier from `max(dists.x,0)/CLOUDS_SCALE`, then cap primary,
lighting and ambient counts. These are whole-ray budgets selected from entry
distance, rather than a per-sample cutoff. `PROGRAM_DEFERRED0` sky-map sampling
has its separate global cached-quality option; do not reinterpret its fixed
sea-level origin as the live camera or include it in a camera-height rule.

The exact default SEA_LEVEL is 63, not 64. Cumulus inner radius is
planet_radius+CLOUDS_CUMULUS_ALTITUDE. With altitude 1200 and scale 10 its nominal
bottom is 183 world blocks. Ignoring the small model-view/eye-position correction,
shore eye 68 gives overhead entry 115, and forest eye 95 gives 88. The near 128,
width 64 transition spans 96–160. Shore overhead is within the transition;
forest overhead is full. The source makes that difference predictable. Capture
actual eyeAltitude/model-view inputs before assigning exact per-pixel tiers.

In the current shoreline reports, loaded SEA_LEVEL 63, altitude 1200, thickness 1
and scale 10 agree with this geometry. They do not prove how many cloud steps
were executed. Partial sweep numbers are not used to select a culprit here.

The tier-selection noise and primary march jitter are currently the same value.
d1 reads spatial noise, applies `r1(frameCounter/checkerboard_area,seed)`, and
passes that dither both to `shaderlod_cloud_tier` and to the cloud ray origin.
At a full/medium transition probability p, medium uses dither<p and full uses
dither≥p. Each selected kernel therefore receives a conditioned subrange of
jitter, rather than an independent uniform phase. For an exactly 2x coarser
medium stride, the mixed first-sample offset in fine-stride units has mean
0.5+0.5*p²; independent tier choice would give 0.5+0.5*p. The conditional phase
distribution is a source-derived distinction, not proof of visible bias or
measured slowdown. Integer rounding means the stride ratio is not always 2x.

Adjacent transition pixels can choose primary counts near 40 versus 20, with
secondary lighting 6 versus 3, according to individual noise. Their loop exits,
empty-density branches and nested secondary marches can therefore diverge.
A SIMT group may retain execution for fuller lanes. The compiler can also
predicate or transform loops; source does not establish its actual cost. The
smooth angular original primary count already varies between pixels, so not
all existing divergence originates in ShaderLOD.

## Geometry-aware full-overhead preservation shared by both options

Use actual layer radii/options and the same viewer as the rendering function.
For a viewer below a spherical shell, the exact world-up entry distance is
obtained by the existing intersection function with `ray_dir=(0,1,0)` and
that layer's inner/outer radii. Analytically, when a hit exists below the shell:

```text
d_overhead = (sqrt(R_inner² - dot(viewer.xz,viewer.xz)) - viewer.y) / CLOUDS_SCALE
```

Reuse/calculate this uniform geometry once per layer/draw when possible, not a
second full intersection per ray just to choose a budget. A flat vertex-stage
value or a reused scalar camera expression needs its own driver/interface check.
The radial approximation `(R_inner-length(viewer))/scale` is cheaper and uses
an already computed length, but differs from exact world-up if viewer.xz is
nonzero; label it if used and add a precision guard. Float cancellation at planet
radius matters. Do not assume shader coordinates are unscaled world blocks.

For a viewer inside the shell, current shell entry is zero and full near quality
is retained. Above the shell, world-up may miss: do not fabricate an overhead
hit. Preserve a conservative full near policy for actual close cloud hits and
separately validate downward views. Congestus additionally intersects a
30000–50000 shader-unit cylindrical ring; the overhead ray can miss that ring,
so its low nominal altitude is not an overhead cloud that should force a new
full-quality region. Do not inflate its distant ring to full quality by using
only an inner-radius gap.

After existing settings sanitization, shift both thresholds together if needed:

```text
shift = max(0, d_overhead + width/2 + precision_guard - configured_near)
near_effective = configured_near + shift
far_effective  = configured_far  + shift
```

The width and gap remain unchanged, avoiding overlapping bands. Full quality
then reaches the nominal overhead entry. Example with no extra guard: shore 115
moves near 128→147 and far 1024→1043; full starts 115. Forest 88 needs no shift.
A small angular cone could be preserved with an actual shell intersection for
its boundary ray, but choosing that cone is a quality policy and needs measured
tradeoffs; do not silently invent it. Minimal full-overhead preservation is the
first proposed control.

Weather does **not** move the nominal Cumulus inner radius in this pinned source.
It changes coverage, stratus/detail and extinction through get_clouds_parameters.
`l0_altitude_scale=0.8/(dynamic_thickness*clouds_cumulus_thickness)`, where
`dynamic_thickness` ranges 0.5–1 according to coverage. Density's altitude fraction
is `(radius-R_inner)*l0_altitude_scale`; its visible support can therefore differ
within the geometric shell. Nominal outer radius is still
R_inner+altitude_option*thickness_option. Preserve the whole overhead ray budget
from geometric entry, rather than guessing its first nonzero density sample
from weather or assuming a universal 120-block layer. If coverage is zero,
retain the existing no-hit early return and avoid unnecessary policy work.

Full-overhead preservation generally restores more work at the shoreline.
It is a quality correction, not a claimed performance optimization. If it
eliminates gains, retain that negative evidence rather than raising upstream
quality merely to manufacture a speedup.

## Option 1: minimal decorrelated stochastic selection

Keep existing Full/Medium/Far budgets, ray extents and marching implementation.
Choose the tier with a distinct decorrelated scalar hash from current ray/tile
coordinates plus checkerboard-cycle index; keep the original march dither
unchanged. Do not take a second noisy approximation of density or add a sampler.
A distinct hash is not automatically statistically independent: future fixtures
should test joint tier/jitter distributions over pixels/phases rather than
assuming an offset proves independence.

First use per-ray selection to isolate coupling. A second controlled variant
can share tier-choice noise within a small 2x2 block of the low-resolution cloud
pass, helping nearby lanes choose the same budget. Use low-resolution ray/tile
coordinates consistently with checkerboard offsets and cycle index, so the
pattern does not accidentally change within one upscaling cycle. Keep TAA's
frameCounter advancing. Tile sharing can create blocks or moving boundaries;
reject it if its visual cost outweighs any measured coherence benefit.

Apply the geometry-aware near/far shift separately from noise decorrelation
first, so improvement in appearance can be assigned. The stochastic approach
still mixes widely separated budgets and can still have lane divergence;
independence is a sampling correction, not guaranteed faster execution.

## Option 2: deterministic smooth, bounded integer budgets

Replace stochastic tier selection with smooth entry-distance scale weights.
Keep the original angular primary count N, configured preset endpoint ratios,
minimum caps and the same sanitized near/far geometry. Interpolate full-to-medium
and medium-to-far budgets continuously across their nonoverlapping bands, then
select a conservative integer ceiling:

```text
M = smoothstep(near-width/2, near+width/2, entry_distance)
L = smoothstep(far -width/2, far +width/2, entry_distance)
medium_count = existing_budget(N, medium_tier, minimum)
far_count    = existing_budget(N, far_tier, minimum)
target_count = mix(mix(N, medium_count, M), far_count, L)
count = clamp(ceil(target_count), min(minimum,N), N)
```

With zero-width bands, use defined hard comparisons instead of undefined
smoothstep edges. At positive width, a nearby pixel usually differs by one
iteration instead of switching 40↔20. The count is steady at a fixed camera,
without a new frame-dependent budget selector. That could reduce temporal
variance and SIMD divergence, but creates count contours and additional dynamic
loop bounds; the driver may lower them poorly. A literal-count dispatch for
secondary counts is another separately measured control, not an assumed cure.

Retain primary `step_length=ray_length/count` and jittered origin. The original
volume extent is sampled coarsely; do not truncate the fine march or shorten
fog/cloud depth. Recompute secondary geometric step coefficients using each
selected count and original extent, as current compensation does. Nearby count
changes still shift quadrature and apparent cloud distance; less variance is
not identical scattering. No global resolution, density, extinction, maximum
ray-length or original high sample count is changed in this proposal.

## Temporal validity and acceptance

d2 cloud upscaling reprojects using current apparent distance and wind, compares
previous depth and invalidates on disocclusion, hand, world_age_changed or NaNs.
Its history is not keyed to sample budget. Keep all existing invalidation and
checkerboard/combined-DH-depth outputs. Do not skip history because one current
pixel has terrain or reuse stale apparent distance after a budget change.

Evaluate camera motion across the band, entering/leaving a cloud layer and
weather transitions with normal clocks. If count changes produce persistent
history bias or ghosting, demonstrate it before considering a budget-history
confidence term. Do not globally reset history every frame, reserve another
sampler, or add readbacks as part of this first redesign. Static-time diagnostic
captures can align comparisons but cannot replace normal-motion acceptance.

Use three source-equivalent controls per scene: original absent-mod, clean
isolation pack all five effects off, and clouds-only on. Then compare either
proposed cloud candidate against current clouds-only with identical upstream
settings. Water/fog/SSR/material LOD stay off, protected lighting=true,
cached sky=false, terrain cap off. Compare all-off against mod-disabled too,
because integration/lighting/debug scaffolding remains. Keep measured shader
wetness and world_age matched and exported; earlier variation is not resolved
by day time alone.

Run both inspected shoreline and rainstorm, each at default cloud upscale 4 and
premium upscale 1. Preserve original primary 40/20, lighting 6 and ambient 2 counts
in every same-quality pair. Do not advertise premium-only results as ordinary
forest/shoreline gains. Repeat balanced complete-JVM arms and report median/tail
whole-render CPU/GPU metrics and variability, not only theoretical savings.

Record minimum/maximum **selected** primary/secondary counts from the actual
policy and camera domain, and bounds on executed counts separately. Executed
primary work can be zero on rejected rays, shortened by transmittance<0.075,
or skip secondary work on empty density. The selected primary cap is therefore
not the executed count. A dedicated existing-output debug capture or suitable
shader capture may report actual executed primary/light/ambient counters in a
separate instrumentation session; do not claim those values are captured by
current timer queries or infer them from FPS. Keep diagnostic counter/image
changes out of timing arms. Until observed, actual executed min/max are labeled
unavailable rather than fabricated.

Quality acceptance includes near overhead clouds, silhouettes, rain-lit cloud
interiors, moving camera, scrolling wind, cloud-plane crossings and DH edges.
Speckle, blocks or shimmer are failure evidence even if sample counts decrease.
If cloud-only does not account for the problem, stop this redesign and address
the measured contributor. No shader changes or new performance claim were made
by this design task.
