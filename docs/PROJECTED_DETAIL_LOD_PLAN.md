# Projected procedural density detail: next bounded candidate

2026-10-05 source-only plan. No prototype, archive, compiler, client, encoder or
GPU work performed. The frozen AO experiment is unchanged.

Investigate **far, unresolved cumulus density detail**, preserving the coarse
cloud shape, primary/lighting ray counts and integration intervals. This is a
general procedural-detail LOD mechanism with an explicit Photon consumer. It
targets work repeated throughout a volumetric pass, unlike AO depth reuse's
small qualified pass budget. Actual total cost and benefit remain unmeasured.
First measure an intentionally quality-changing upper-bound control; discard
the candidate promptly if that control saves little GPU time.

## Exact source and existing work avoidance

Original Photon v1.3b by Benjamin Stott (SixthSurge), local
`dev/upstream/photon_v1.3b.zip`, SHA-256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Preserve its original LICENSE and repository Photon attribution.

| Member | SHA-256 | Relevant source |
| --- | --- | --- |
| `shaders/include/sky/clouds/cumulus.glsl` | `1923542cd9ad862a6166a542dbdcb1597fd70316e4338b236deefc2ccb932ba5` | Density 36–107; two detail textures 79–82; nonlinear erosion/shaping 88–105; optical-depth density calls 125–129; primary density 284; lighting rays 314–325 |
| `shaders/program/d1_clouds.fsh` | `1f0dd330b837becff393380283eb767e3a55fe48e9b04705bf825fde535d0cae` | Checkerboard 128–129/161–165; selected projection 170–183; actual viewer/ray 186–196; draw call 211–217 |
| `shaders/program/d2_clouds_upscaling.fsh` | `2b026e4bf367527213264b333b267c649a1ac85ba3105155b71085878c3c204a` | Current/previous distances and terrain rejection 247–270; existing reprojection/history retained |
| `shaders/shaders.properties` | `f8cc8df60f930612e8817e4176a34886c1a9de778c15427a5468ecbc4f71ee9a` | 3D R8 64³ detail textures 350–351; world-age seconds 470 |
| `shaders/image/worley_swirley.dat` | `6e13f05304287ae28efbcf73a44903a1dea77104617a8e591e4617a4bb1b0d8d` | Fine periodic texture; 262144 bytes |

Upstream already rejects points outside the shell/local coverage map, rejects
nonpositive shaped base density before detail, rejects invisible shells/terrain,
stops primary rays below transmittance 0.075, and renders a quarter-linear cloud
buffer with temporal reconstruction in the current profile. Preserve all these
checks. The cached sky path already halves primary counts; leave that path and
cloud-shadow/preparation generation untouched. Existing cloud secondary-budget,
extent, overhead, literal-cache and analytic probes are not this mechanism.

Actual saved original forest/coastal pilot measurement options establish
`CLOUDS_TEMPORAL_UPSCALING=4`, primary horizon/zenith 40/20, light/ambient 6/2,
`CLOUDS_SCALE=10.0`, volumetric fog on at 0.50, POM/normal/specular mapping off,
water waves/parallax on with three octaves. Their full profiles differ: forest
effective-option SHA-256 is
`4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745`,
coastal SHA-256 is
`ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669`.
Sources: original arms `001-b1-forest-high-minimal-measurement.json` and
`006-b1-water-high-minimal-measurement.json` under
`dev/logs/photon-reduced-five-effects-pilot-r1/`. Cloud temporal factor is already
the maximum native option; native material-map cuts have no work to remove here.

## Spatial and temporal inputs

The fine texture coordinate is `(pos + 0.4*wind)*0.005`; the coarse detail texture
uses `(pos + 0.2*wind)*0.0009`. Each is 64³ and repeats. At scale 10, fine lattice
spacing is `1/(64*0.005*10) = 0.3125` camera/world blocks; coarse lattice spacing
is about 1.736 blocks. Full repeat periods are 20 and 111.111 blocks. These are
source coordinate/lattice scales, not a proof that nonlinear density is strictly
band-limited. Density squares detail, erodes coarse coverage and applies a
rational contrast function, so a texture-cell Nyquist argument alone is unsafe.

`wind = wind_velocity*world_age`; prior `pos` translation plus the fine term gives
1.4 times wind velocity in fine coordinates. Fine lattice motion is
`64*0.005*1.4*|wind_velocity|` texels per world-age second. Actual camera motion
adds relative motion. `world_age=((worldDay%128)*24000+worldTime)/20` can wrap;
frozen benchmark world time does not test normal cloud-wind evolution. Keep
upstream checkerboard phase, jitter, temporal history and reset behavior.

Use the actual cloud viewer `air_viewer_pos` and **current density evaluation
point**, including secondary-ray points, to compute observer distance divided by
`CLOUDS_SCALE`. Do not use fragment terrain depth, planet radius, `length(pos)`,
or distance from the first shell sample. Upstream primary `ray_origin` is already
at the shell entry plus jitter; its `distance_to_sample` omits camera-to-shell
distance and is unsuitable as the LOD metric.

Derive a conservative pixel cone from neighboring unprojected normalized rays,
using the same selected vanilla/DH projection and TAAU scale as the main ray.
For point distance `s` in atmosphere units, transverse footprint is approximately
`s*max(length(dir_x-dir), length(dir_y-dir))`. Multiply by `0.005*64` for fine
texture-cell coverage. Compute directions explicitly before marches; use no
screen derivatives within divergent ray loops. Start with **one final display
pixel**, not the 4×4 checkerboard tile, so retained temporal detail is protected.

Primary linear step length and secondary exponential step lengths are known at
their callers. They are longitudinal integration spacing, not pixel-cone width.
Preserve them; do not enlarge the cone by `max(pixel_width, ray_step)` and assume
the resulting isotropic density average preserves optical depth. Secondary points
use the observer cone at their own position while retaining all light-ray inputs.

## Proposed bounded private patch

Change only a private original-derived `cumulus.glsl`, `d1_clouds.fsh`, settings
option and new density-policy helper. Activate only in `PROGRAM_DEFERRED1`; other
layers, cached sky, shadow maps and preparation remain original. Pass/set the
explicit observer/cone context once before `draw_clouds`, rather than altering
all cloud-layer signatures. Validate context; unknown programs, invalid projection,
unknown bridge, absent/disabled mod and disabled private option use the original
density function. Default off, with original function and archive-member pins.

Keep the coarse `worley_0` sample and base coverage unchanged. Separate fine
erosion into `d` (density after coarse erosion) and
`beta = detail_weight_y*dampen(clamp01(1-d))*detail_fade`. A conservative interior
guard must ensure **all** fine values in the normalized R8 range still produce
output above the upstream `eps` after the original altitude/contrast shaping.
Only then can fine-detail filtering occur; keep uncertain boundaries exact to
protect cloud silhouette and the primary occupied/empty classification.

Full near density remains literal original. A radial block-distance band is only
the protective near gate; the pixel-cone threshold controls unresolved detail.
Blend smoothly toward a calibrated far fine-erosion moment across a footprint
band; evaluate original fine noise through that transition. At its fully filtered
endpoint, branch **before** the fine 3D fetch and omit it. No stochastic transition
or double march. Initial thresholds are private test parameters, not product
defaults; keep full quality until both distance and footprint guards qualify.

Calibration must use the actual texture and actual trilinear sampling model.
`E[w²]` is not `E[w]²`, and average filtered erosion does not preserve average
final density, transmittance or cloud brightness through the nonlinear shaping.
Never call a 0.5 replacement mean-opacity preserving. Verify distribution, bias
and boundary behavior across coarse densities/altitudes/lighting; retain original
when a bounded cheap calibration is inadequate. An extra 3D transfer lookup can
replace rather than save the texture fetch, defeating the cost hypothesis.

The previously inspected private sampler report describes linear base-only volume
sampling for its exact Mac profile; it does not validate a new profile/source.
Pinned `.mcmeta` requests blur/repeat, but does not prove actual sampler/mip state.
Recheck bound volume dimensions, base/max levels, filter and anisotropy. No usable
prefiltered mip chain is established. Explicit `textureLod(...,0)` in the new
active density function needs that scoped equivalence check; preserve literal
original implicit samples in the disabled kernel. Do not assume derivatives from
branching density marches are meaningful.

This is a procedural-detail capability, not a ray-count budget: the common
effect policy must not interpret a retained-work ratio as multiplying density.
The adapter owns footprint mapping, calibration and truthful consumed fields.

## Minimal kill test and stopping rule

Before implementing the calibrated path, assign a tiny default-off pinned control
that replaces **only cumulus fine 3D sampling** with a fixed diagnostic value in
all main-pass density calls. Keep coarse noise, all primary/light/ambient counts,
ray extents, seeds, phase functions and reconstruction unchanged. This knowingly
changes quality/occupancy and cannot establish a quality-equivalent speedup or a
strict isolated fetch budget; it only tests whether this source region merits
investment. Use original, disabled and same-source global OFF/ON controls with
verified options; root/runtime operator alone assembles/reloads/measures.

Require an ordinary cloud-visible scene plus forest/coast, GPU frame-only repeated
balanced timings and matched total-frame timing. Reject a flat/regressive result
before calibration. If useful, first freeze source/member/sampler evidence, then
implement interior/full-near filtering and test sun-facing cloud edges, wind and
camera motion, weather, observer elevation inside/above the shell, temporal resets,
water glint through clouds and DH/noDH boundaries. A fast diagnostic is not
acceptance. If edge guards rarely qualify, calibration shifts brightness, or
sampler/transfer work consumes the saving, stop this mechanism. Neither an AO
gain nor this unmeasured candidate is evidence of the 50% throughput target.
