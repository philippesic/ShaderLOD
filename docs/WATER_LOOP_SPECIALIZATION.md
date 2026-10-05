# Water loop specialization and cloud sampling audit

Prepared 2026-10-04 from pinned Photon v1.3b, preserved water-only candidate 7ca,
current helpers and the SSR shoreline pilot. Research only: no source, shader,
Java, build, test or profile edits; no compilers or clients were run. Root owns
all runtime work. The pilot identifies a candidate regression to isolate, not
a proven compiler mechanism.

## Pilot evidence and remaining confounds

`dev/logs/ssr-lod-pilot` uses DH absent, the same shoreline camera/radius,
ENVIRONMENT_REFLECTIONS/SKY_REFLECTIONS enabled and default three water octaves.
The archive is original high or preserved water-only
`7cae01ced084885d6e66102b26803077fb0ed2093f854fe791b1ac8327abba19`;
the structural atmosphere guard is absent. Original/patched-disabled/active
captured roughly 139.87/139.33/113.05 FPS and 6.58/6.54/8.135 ms GPU mean in that
short pilot. A later finite-normal pilot was reported by root at 143.11 baseline
versus 133.99 active. Retain both; no single pair proves a causal shader path.

Telemetry is present at `shader_temporal_inputs_initial/start/end`. The first
three pilot reports record supplied wetness min/max 0, with 2098/2090/1696
measurement supplier reads respectively, day 0/world_age 350 and advancing
frameCounter/frameTimeCounter. An earlier worker message searched the wrong
JSON nesting and incorrectly suggested telemetry was missing; inspection of
these exact keys corrects that suggestion. Their clocks progress through
similar roughly 33–47 second intervals but their ending frame counts differ.
Wetness is controlled; animation/noise phase is not byte-identical.

Active still changes clouds, fog, SSR and water together. The two inspected
screenshots show the same shoreline and shadows; the active cloud sky looks
more finely grained. Static captures cannot distinguish moving shimmer from
one temporal/noise phase. Water normals also differ visibly as expected, and
the real animation clocks differ. Neither observation assigns the slowdown.

## The original near function is mathematically preserved but not source-preserved

Original `include/surface/water_normal.glsl:get_water_height` has two loops with
`i < WATER_WAVE_ITERATIONS`, a fixed-length noise array and a const normalization
based on that compile-time macro. Original `get_water_normal` calls it three
times. Original water parallax calls it initially and in its depth-march while
loop. For the current option, the compiler sees the literal budget 3 in every
height evaluation and can unroll the noise/math loops, use statically indexed
array elements and precompute constants. Actual Apple driver lowering is not
available from source alone, so eligibility is not proof of unrolling.

The 218992b patch changed height's signature to accept `int wave_iterations`,
both loop bounds to that parameter, array indexing to the corresponding runtime
index and normalization to a runtime exponent. Current 7ca retains that design.
Even near/disabled normal calls pass a variable produced by the LOD budget
helper into those functions. The current finite path reproduces original values
when that variable equals 3, but it does not literally call the untouched fixed 3
function. The comment "exact original evaluation" refers to operation/results,
not the compiler's original constant-loop code shape. That distinction matters.

An active per-pixel tier changes iterations across fragments. A compiler can
choose dynamic loops, extra bounds/index machinery, register-backed array
storage or predicated iterations rather than statically unrolling a literal 3.
Parallax nests these height loops inside its own variable-step while loop,
amplifying any such lowering difference. This is a plausible cost mechanism,
not evidence that the driver currently spills registers or executes extra work.
The patched-off pilot being close to original also means a universal pack-wide
dynamic-loop penalty is not established; enabled divergence/cheap-path lowering
or unrelated phase/work changes remain possible.

Analytic gradients have a dynamic loop too, no temporary wave array, but sine
and cosine rather than height's sine alone. Mixing analytic and finite paths
across the 80–112-block transition can make adjacent lanes execute different
algorithms; the original full path may still execute in some lanes even if most
pixels request cheap normals. A low sample count therefore does not guarantee
low wall/GPU time. Water at 160 blocks also retains new medium parallax whereas
old generic 32/96 LOD skipped it. The new conservative policy intentionally keeps
more nearby detail and work.

## Proposed static specialization, without changing the selected effect

First isolate the loop structure with finite differences only. Preserve the
entire pinned original get_water_height/get_water_normal/get_water_parallax_coord
functions and their original names/signatures/bodies. Shadow calls continue to
use them directly. Add LOD wrappers and cheap helpers rather than teaching the
original helpers to accept a runtime iteration count.

- A wrapper classifies quality once for surface normal, and once for parallax.
  Tier 0 or disabled explicitly calls the original function. Unsupported bridge
  versions retain the current pack-option fallback before tier selection. Near geometry still has the original 3-bound height loops and original
  constant 4 parallax budget in this profile.
- Cheap helper bodies use a compile-time integer macro/literal for each supported
  octave count, not `const int` function parameters. GLSL parameter const means
  non-assignable; it does not require a compile-time call value. An explicit
  specialization gives the compiler literal bounds/indexes and allows fixed
  normalization. It still does not force a driver optimization.
- Runtime preset can change through the live bridge, so emit the finitely many
  counts required by Balanced/Performance/Aggressive for the configured original
  macro, then dispatch at the wrapper. Deduplicate equal counts. Three original
  octaves require only the 3 full, 2 cheap and 1 cheap cases, rather than 8 unrelated
  kernels. If a budget equals original, reuse original.
- Cheap parallax gets its own literal step-count/height-count pair. At Performance
  medium, height count 2 and march budget 2 are explicit; far returns original
  input coordinates under the existing policy. Keep the upstream while condition
  and interpolation, rather than replacing it with a new fixed-iteration loop
  that changes the first depth crossing. Full parallax is untouched.
- Normal specialization retains the original three finite-difference heights,
  current truncated-amplitude normalization and same h 0.1. Do not add an analytic
  approximation to this first control. Reintroduce specialized analytic helpers
  only in a separate arm after the finite loop control has been measured.

Compile-time budget formulas matching the current helper for original N 1–8:

```text
medium Balanced:    (3*N + 3) / 4
medium Performance: (N + 1) / 2
medium Aggressive:  (N + 3) / 4
far Balanced:       (N + 1) / 2
far Performance:    (N + 3) / 4
far Aggressive:     (N + 7) / 8
```

All divisions are integer. The current scales 3/4,1/2,1/4,1/8 are exactly binary
fractions; these ceil formulas produce identical caps for N 1–8 and never exceed
N. Preserve min-budget behavior if reusing the pattern for other effects.

| Original N | Medium B/P/A | Far B/P/A |
| ---: | --- | --- |
|1 |1/1/1 |1/1/1 |
|2 |2/1/1 |1/1/1 |
|3 |3/2/1 |2/1/1 |
|4 |3/2/1 |2/1/1 |
|5 |4/3/2 |3/2/1 |
|6 |5/3/2 |3/2/1 |
|7 |6/4/2 |4/2/1 |
|8 |6/4/2 |4/2/1 |

The wrapper branches still vary in transition regions. Do not promise coherent
execution there. Outside the bands each selected fixed path is stable for the
same surface region; preset and enable are uniform. Avoid evaluating all cheap
helpers and then blending their results. An optional hard-transition diagnostic
can measure divergence sensitivity, but visible rings are not an acceptable
shipping strategy and that diagnostic is a separate quality control.

Exact source consumers to switch intentionally: vanilla water normal/parallax
in `program/gbuffers_all_translucent.fsh`, DH distant-water normals in
`include/misc/distant_water.glsl`. Preserve `program/shadow.fsh` caustics on the
original full helper. Retaining original helper bodies plus wrappers makes the
near/shadow source contract independently auditable and avoids a universal GLSL
rewriter. Any template/helper derivative keeps Photon attribution.

## Mathematical and source acceptance for that future implementation

Use actual shader bodies, not a test that restates the implementation:

1. Verify original full height/normal/parallax function bodies against the pinned
   upstream source; near/disabled/shadow wrappers route to them. This establishes
   source fidelity, not identical machine instructions.
2. Execute actual specialized height functions versus actual current dynamic
   height with the same N for every N 1–8, multiple coordinates/times/flowing and
   rotated directions, procedural noise samples and height variation on/off.
   Compare heights and finite normals within documented floating-point tolerance.
   Constant noise alone is insufficient for finite differences: sample adapters
   must vary with coordinate identically for both paths.
3. Compare actual specialized parallax coordinates versus current medium
   dynamic-parallax implementation, including low/high slopes, horizontal/grazing
   rays and first-crossing interpolation. Preserve upstream undefined zero-height
   cases separately rather than claiming a new bound is algebraically identical.
4. Check runtime bridge presets/tier boundaries and all count/step combinations;
   compile full driver-independent matrix for original settings 1–8, DH on/off,
   shadow, analytic disabled and material/reflection options. A manageable focused
   water-program matrix can cover N/preset while the broad existing matrix
   covers other options, avoiding thousands of redundant unrelated compiles.

## Cloud stride audit and sky-grain control

Current cumulus, altocumulus and congestus reduce `primary_steps` **before**
computing `step_length = ray_length / primary_steps`. Their origin is
`entry + step_length*dither` and every sample advances by that coarser stride.
The full clipped ray extent remains covered statistically; this is not simply
truncating the original fine march. Coarser density integration can miss thin
features, change opacity termination, apparent cloud distance and temporal
reprojection. Preserved ray extent does not establish preserved image quality.

Secondary rays grow steps geometrically by 2. The compensation preserves total
extent when reducing O original steps to N:

```text
new_initial_length = (C/N) * (N/O) * (2^O - 1)/(2^N - 1)
original_total_extent = 2*(C/O)*(2^O - 1)
new_total_extent      = 2*new_initial_length*(2^N - 1)
```

The totals agree, but the coarser locations and quadrature weights do not.
Lower secondary counts can cause noisy cloud lighting even with preserved
optical-depth extent. The compensation uses runtime exp2 and division per
secondary-ray call; literal-count specializations could precompute it, but that
is a separate cloud control, not proof water loops caused this pilot.

Cloud tier choice currently receives the **same dither** as the primary march.
In a full-to-medium transition with medium probability p, cheap rays always use
dither<p and full rays dither≥p. Their sample phases are therefore conditional
rather than independent uniform jitter within each chosen budget. For a 2x
coarser medium stride, the mixed first-sample offset in units of the fine stride
has expectation 0.5+0.5*p², whereas an independent tier choice gives 0.5+0.5*p.
This does not alone prove the screenshot grain, but it shows the quality switch
and density quadrature are coupled. A future visual/math control should use
independent tier noise and march noise, without additional samplers, while
preserving both marginal distributions. Do not change that sampling during
water loop isolation.

At shoreline eye altitude near 68, the 120-world-block-above-sea-level cumulus
bottom is about 116 blocks overhead (less the exact eye offset). With cloud
bands 128/1024/64, that lies inside 96–160's full/medium transition even overhead.
The forest eye near 95 put overhead entry near 89, safely full. The same default
cloud policy therefore affects more salient overhead clouds in the shoreline
than the prior forest. Check captured actual eyeAltitude before quoting an
exact pixel tier. One screenshot cannot certify stable cloud transitions.

## Minimal causal experiment sequence

After root authorizes implementation, prepare **isolation** packs with one
explicit source change at a time and record new hashes. Keep current proven
wetness telemetry, fixed 0, DH absent, SSR options, radius 24, camera and timing
cadence matched. Keep real clocks/counter for representative tests; use optional
static animation only as a separately labeled diagnostic.

1. Existing water-only 7ca active finite versus active analytic, repeated in
   balanced order. Root's short finite pilot suggests a smaller loss, but repeats
   are required. Do not treat original high versus finite as an analytic-only
   comparison because clouds/fog/SSR also change.
2. Construct a water-only-effect isolation: restore upstream clouds/fog/SSR
   behavior in an explicit derivative while keeping the water helper/tier policy.
   Compare current dynamic finite with static-specialized finite on this same
   derivative. Geometry and cloud algorithms must match in these arms. An
   existing knob that simply turns WATER_WAVES/PARALLAX off is a useful attribution
   control, but changes visual quality and does not prove specialization.
3. Compare that isolated water derivative active versus its own disabled/original
   control. If static specialization is no faster or still slower beyond paired
   noise, reject it as a performance solution rather than advertising literal
   loop counts. Keep original near rendering available.
4. Independently isolate cloud LOD with water/fog/SSR kept original, inspect
   overhead cloud transition/motion and compare dither coupling versus an
   independent-choice diagnostic. Neither water nor cloud candidate advances
   shader support until measured ordinary-scene gains survive quality inspection.

There is no compiler/register-pressure conclusion or new performance claim in
this proposal. It gives an explicit source contract and controls capable of
rejecting the hypothesis, rather than assuming fewer samples must be faster.
