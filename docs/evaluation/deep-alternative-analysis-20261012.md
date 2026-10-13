# Deep shader investigation: independent alternatives

October 12, 2026, Pacific time. Owner `/root/alternative`, Astra maximum reasoning.
Offline analysis only; no Minecraft, GPU, Gradle, remote execution or native Goal
operation. Product sources and the paused Reimagined preparation were not changed.
Parent owns integration, prioritization and any later runtime operation.

**Recommend BSL 10.1.8 primary-cloud empty-density detail rejection, initially for
clear-weather Balanced on Fabric 26.1.2.** This transfers the existing Photon /
Complementary conservative empty-density mechanism to a different active native
consumer. Every certified sample avoids two of its three noise reads. It changes
no integration interval, sample count, density value, absorption recurrence,
lighting recurrence, first-cloud position or temporal feedback in the admitted
finite/sampler domain. The strongest uncertainty is actual eligible cloud work
and whether saved texture work exceeds branch/register/arithmetic overhead.

The parent assigned implementation to `/root/consumer_facts` after this handback;
this report specifies the source contract and supplies independent evidence. It
does not claim that the implementation has passed review, rendered or improved FPS.

## Actual reference and alternatives

The authentic [saved BSL reference](../../dev/logs/manual-shader-fps-20261011/bsl-r2/runtime-report.json)
records Overworld, camera `(256.5,95,768.5)`, yaw135/pitch9, day7000, rain0,
Apple M3 Max / OpenGL4.1 Metal91.7, Iris1.11.4+MC26.1.2 and Sodium0.9.2.
Native options include CLOUDS2, CLOUD_BASE0, CLOUD_DETAIL1, CLOUD_AMOUNT10,
CLOUD_DENSITY4, CLOUD_THICKNESS5, CLOUD_SCALE12, native TAA enabled,
ADVANCED_MATERIALS=false and AURORA0. Actual original and Performance select the
native graph; actual Balanced selects the generated recipe. This is a saved
observation from October11, not a current client census. Its generated map387
predates the current Aurora adapter addition: it proves the route, not the new
candidate's loaded options. Discover the latter from the actual assembled ZIP.

Current source path is `world0/deferred1.fsh` → `program/deferred1.glsl:561` →
`DrawCloudVolumetric` → `CloudSample`. The primary caller supplies `fadeFaster=false`.
The reflected caller supplies true and is inside ADVANCED_MATERIALS; it is excluded.
The cloud loop has native count `int(min(planeDifference/sampleLength,32)+1)`,
up to33 visits before native opacity/foreground exits. There are three noise
lookups per reached base0 density sample: one red-channel base and two blue-channel
detail reads. Density and loops are actually enabled by the recorded native
options. Source and options do not measure reached visits, sky coverage or GPU cost.

| Approach | Actual opportunity | Decision |
| --- | --- | --- |
| BSL primary-cloud bound | Three noise reads become one at certified-empty samples; no count rounding and no extra ray traversal | Strongest alternative; implement the bounded candidate |
| Bliss water screen-space reflection count | Native `SCREENSPACE_REFLECTIONS=true`, `SSR_STEPS30`; `quality=mix(15,30,fresnel)` and inclusive `i<=int(quality)` yield16–31 maximum coarse reads | Lowering quality and rescaling stride risks missing thin geometry and changing depth acceptance/reprojection. This is coarse traversal, not the existing safe post-crossing refinement seam. Reject for this allocation |
| Ordinary AO count copies | MakeUp native5 groups, Complementary native quality2 has4 groups, Bliss has2 slices×3 horizon steps; Balanced ceil(.875N) leaves every independently meaningful group unchanged | No useful selected Balanced work; do not manufacture a reduction by changing grouping or native options |

Bliss's separate capitalized `Screen_Space_Reflections` option is false in the
actual native map. Its material-reflection helper cannot be credited as active
water work merely because its name also contains SSR. Likewise BSL Aurora is off;
the current Aurora24→21 port does not remove active work in this reference.

The [closed reflected-cloud trial](bsl-reflected-cloud-early-cost-r2.md) classified
up to33 native positions to reject a whole reflected ray beyond a radial fade.
It established no useful gain and remains closed unchanged. This candidate uses
the already-required base lookup at each primary sample and bounds only its
remaining detail contribution. It has no geometric prepass, reflected-ray cutoff,
new workload or tuning of that trial. Prepared End-cloud, streaming hashes and
weight carry were inspected as prior work and are not rediscovered here. Earlier
Photon/Unbound/Bliss cloud rejections also supply negative benefit evidence: an
algebraically valid density skip does not imply useful whole-frame speed.

## Equations and preservation

Let `b` be the native base value, `d` the interpolated detail value, `g` the native
height fraction, `s` sunCoverage, `r` rainStrength and `A` CLOUD_AMOUNT. Define
`w=.0476*CLOUD_DETAIL` and `c=CloudCoverageDefault(g)`. The native pre-clamp value is

```text
x = 21 * mix(b, d, w)
y = mix(x - c, 21 - 2.5*c, .33*r) - (3*s + A)
q = max(y, 0)
density = CloudApplyDensity(q)
```

Pinned normalized, linearly filtered noise gives `0<=b,d<=1`. All selectable
CLOUD_DETAIL values give `0.01428<=w<=0.08092`; the expression is nondecreasing
in `d`. For the chosen clear-only scope `r==0`, use

```text
U = (21 * mix(b, 1, w) - c) - (3*s + A)
if finite-domain && U < -0.001: density = 0
else: read both detail samples and execute native CloudCombineDefault
```

In real arithmetic `y<=U`. The negative margin keeps the decision away from zero
under ordinary binary32 arithmetic and different `mix` associations. The margin
is a conservative arithmetic guard, not a visible-contribution cutoff: only
native zero density is omitted. Equality, near-zero ambiguity and invalid inputs
retain both reads. A zero input remains exactly zero through native density
scaling and division by `sqrt(q*q+.5)`.

Skip admission retains compact ordered checks: `b,g,s` in[0,1], coverage in[0,6]
and both detail coordinates with absolute value below1e20. These reject NaN and
infinite inputs and guarantee finite omitted coordinates after the native
floor/offset arithmetic. Normalized base output alone cannot establish validity
of an omitted detail read's coordinates. Clear-weather eligibility is a uniform
early guard; rain, snow/weather transitions and unsupported contexts call native
CloudSample before classifier work. This scope was chosen before any runtime
trial because the authentic current baseline is clear and the noise census shows
much weaker headroom in rain. No native weather setting is changed.

For a certified sample the existing caller still performs every native update
with density0: native position/dither, sample lighting, distanceFade,
`cloud=mix(cloud,1,noise)`, cloudFaded, first-hit/cloudViewLength and final feedback.
Do not replace this with a loop `continue`, rescale alpha, change stride or move
the saturation break. Since the per-sample density is retained, the difficult
recurrences need no approximation. Temporal sequence and sampling locations stay
native; no additional history, buffer, texture or pass exists.

## Sampling and integration contract

The native wrapper declares GLSL120 and already enables
`GL_ARB_shader_texture_lod`. Retained detail reads on the active candidate path
must use explicit `texture2DLod(noisetex,coord,0.0)`. Leaving implicit reads after
a divergent rejection would introduce an undefined-derivative boundary. The
[Khronos extension](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_shader_texture_lod.txt)
explicitly supplies fragment LOD functions for nonuniform control flow; isotropic
LOD does not establish equivalence when anisotropic sampling is active.

The [old actual BSL sampler observation](bsl-reflected-cloud-distance-cue-runtime-r2-review.md)
recorded primary deferred1's noisetex512²RGBA8, min/magLINEAR, base/max0,
LODmin/max0, anisotropy1, bias0, repeat, sampler0. This is historical evidence on
MC1.21.1/Iris1.8.12, not current Fabric26.1.2 state. Fresh decompilation of the
actual pinned Fabric26.1.2 Iris1.11.4 JAR shows
`NativeImageBackedCustomTexture.getSampling()` selects `GlSampler.LINEAR_REPEAT`
for native PNG blurtrue/clampfalse; constructor/static code gives nonmip min/mag
LINEAR, repeat and no comparison. This is a concrete offline sampling premise.
The next runtime check must observe the effective primary noisetex binding; do
not infer a physical sampler certificate from a shader uniform or old receipt.

Exact implementation requirements sent to the parent/source owner:

- Preserve native `CloudSample` and `CloudSampleDetail` verbatim. Add a small
  wrapper after native CloudSample, before `float InvLerp`. Its uniform invalid
  core/preset, nonclear or nonprimary path immediately calls native CloudSample.
  Active path reproduces base0 coordinate/base/coverage arithmetic, applies the
  bound, and calls a two-read LOD0 detail clone plus native combination otherwise.
- Define `SHADERLOD_BSL_PRIMARY_CLOUD_CONTEXT` only inside deferred1's fragment
  section. At DrawCloudVolumetric's unique `CloudSample(cloudCoord,wind,
  cloudGradient,sunCoverage,dither)` call pass `!fadeFaster` to the wrapper.
  Restrict compilation to OVERWORLD/CLOUDS2/CLOUD_BASE0, no advanced materials,
  DH or Voxy. Skybox, water, reflected and other-dimension callers remain native.
- Reuse the BSL adapter and existing bridge2/enabled1/preset0 Balanced transport.
  Initialize its isolated preset uniform to−1; unknown/missing preset retains
  native work. No new uniforms or renderer-route changes. Off, Quality, Cancel,
  failed Apply and mismatch use the existing selection/recovery path.
- Pin original cloud/deferred/wrapper/settings/properties/noisePNG/noise metadata
  members. Update isolated adapter helper pin, recipe, packaged mirrors and
  catalog through existing `--sync-resources`, preserving prior current identity
  as legacy. Include the NeoForge26.2 `BslSourceCertificate.RECIPE_SHA` join if the
  shared overlay changes its recognized recipe. This is an integration
  requirement, not NeoForge26.2 runtime support.
- Produce a full-work explicitLOD0 control using the same sampled coordinates
  and combination, plus the ordinary candidate. Neither control-only improvement
  nor unused classifier arithmetic in a control proves original-relative benefit.

## Work count, overhead and evidence

For `D` reached eligible samples, `C` proven empty and `F` domain fallbacks that
replay native sampling after the initial base fetch, nominal shader noise reads
change from `3D` to `3D-2C+F`: reduction `(2C-F)/(3D)`. Physical transactions and hardware
instructions may differ through caches, quad execution, compiler scheduling or
branch flattening. Add classifier ALU, domain comparisons, branch and live
registers; rainy/native-fallback cases still pay the outer uniform decision.
The native base fetch cannot be removed. A pre-base coverage-only bound was
rejected: it admits no useful current-native clear/rain density rejection and
would add overhead. No fresh global proof service or classifier pass is needed.

The authentic512² RGBA PNG was decoded and all262,144 red texels counted at texel
centers. At native detail1/amount10, clear weather and sunCoverage0:

| Native height fraction g | Certified base texels | Fraction | Nominal three-read reduction |
| --- | ---: | ---: | ---: |
| .125 | 93,788 |35.78%|23.85%|
| .5 |131,303|50.09%|33.39%|
| .75 |194,574|74.22%|49.48%|
| 0 or1 |255,136|97.33%|64.88%|

This census establishes headroom in the authentic noise, **not actual fragment,
bilinear-interpolant or reached-ray coverage**. At rain1/g.125/sun0 only307 texels
qualify (0.117%), supporting the clear-only initial scope. Screen cloud fraction,
native early exits and spatial correlation remain unknown.

Checks actually run:

- [analyze.py](../../dev/logs/deep-shader-investigation-20261012/alternative/analyze.py)
  verifies archive/member pins, decodes the native noise, records its histogram,
  evaluates bounded census scenarios and inspects actual Iris bytecode. Its
  [receipt](../../dev/logs/deep-shader-investigation-20261012/alternative/receipt.json)
  records120,000 CPU cases across all selectable detail/amount values and three
  `mix` associations:63,396 admitted, zero false zero-density rejections; greatest
  native-minus-bound difference5.72e−6. This model includes rain only as analysis.
- [clear_scope.py](../../dev/logs/deep-shader-investigation-20261012/alternative/clear_scope.py)
  concentrates200,000 cases around the proposed−.001 boundary using the simplified
  clear-only bound versus native rain0 arithmetic.104,968 admitted, zero false
  rejections; greatest native-minus-bound difference1.91e−6. The
  [clear receipt](../../dev/logs/deep-shader-investigation-20261012/alternative/clear-scope-receipt.json)
  also pins the authentic selected routes/options/scene above.

These are finite binary32 CPU checks, not universal GLSL numerical guarantees,
Iris compilation, driver execution or quality/FPS evidence. No source parser or
product test was run by this analysis owner. The initially unavailable system
PIL/Numpy imports were resolved by using the existing caller Python environment's
Pillow and scalar math; no dependency installation occurred. Javap21 successfully
inspected the actual JAR; its text is retained beside the receipt. No live process
or exclusive resource is owned at handback.

The PNG SHA256 is `5ff93225ed7018e90822824a599e4e080e985fb882fdc15ac9e117dd8ba22a34`;
metadata `e9c800d49d941e58d1c9f114b4564886b0c984369fab42515935497948ea8440`;
full archive `36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5`.
The receipt retains every other exact source pin and Iris archive hash.

## Smallest decisive observation after a separately authorized resume

Use the existing authentic Fabric26.1.2 Overworld scene and current native options,
with Apply while Balanced is selected. In one bounded untimed admission establish
native rendering, candidate source/core consumption and the effective primary
noise sampler; compare original with explicitLOD0 full-work control. Then compare
ordinary candidate cloud edges, sun-reveal contours, horizon, moving camera and
cloud-depth interaction with foreground/transparent surfaces. Reuse existing
capture/sampler/finite-client tools. A narrow optional actual-reject cue is useful
only if selected-path execution cannot otherwise be established; it is not timing.

Take a short matched original/control/enabled/original-return cost observation
early, including all bridge and classification overhead and recording native
return drift. Failed original/control equivalence, absent useful execution or
no useful total benefit closes this fixed candidate without changing the scene,
margin, native options or cloud workload. DH/PBR/other dimensions deliberately
fall back; their actual compatibility/restoration evidence remains required.
The full nine-family, five-version, two-loader project scope and existing runtime
pause/allocation are unchanged. Parent chooses integration and that next action.
