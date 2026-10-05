# Reflection footprint refinement LOD reference adapter

Source freeze 2026-10-05. Independent reflection implementation track; owns only
this report, `scripts/build_photon_reflection_lod.py`,
`tests/test_photon_reflection_lod.py`,
`patches/photon-experiments/reflection-lod.json`, and
`shaderpacks/photon-reflection-lod/**`. Other workers own Java, shared shader
helpers and other experiments. Root owns client/reloads/GPU; this worker ran
no client, Gradle, GLSL driver compiler, GPU query or image processing.

## Implemented behavior

A transferable distance/projected-footprint policy shortens binary reflection
refinement only when remaining spatial uncertainty is bounded. Full original
intersection count, sampled ray positions, extent, dither, depth tolerance,
projection/depth sampler and environment coverage remain. No untraced far segment
is replaced with sky. Both smooth-water and rough reflection callers use this
adapter; original rough ray counts, BSDF factors and accumulation normalization
remain. The separate original raytracer stays byte-identical, so AO/SSGI callers
are unaffected. There is no hierarchy/new pass/new target allocation.

Entry points are original `trace_specular_ray` in
`shaders/include/lighting/specular_lighting.glsl` and new
`shaderlod_reflection_depth_trace` / `shaderlod_raymarch_depth_cached` in
`shaders/include/shaderlod/reflection_depth_cache.glsl`. Compile option
`SHADERLOD_REFLECTION_DEPTH_CACHE` is default off; its name includes the
secondary exact integer-depth reuse path. Pinned settings and the single
specular caller are the only changed original members; one helper is added.
Original Photon v1.3b by Benjamin Stott (SixthSurge), original LICENSE retained.

Activation requires core protocol2, global enable exactly1, finite nonnegative
near/far/transition with far>near and 0<width<=far-near, finite receiver distance,
valid original dither, positive original budgets and bounded supported counts
(intersection<=256, refinement<=64). Unknown/absent mod, disabled bridge, invalid
policy/counts and full near execute the literal upstream tracer. These are
source-consumed core fields; live uniform linkage/reload remains unverified.
Extension1 per-effect fields, debug and preset are not consumed. No pack option
is represented as an actually verified mod connection.

Distance is Euclidean camera-relative view space in Minecraft blocks. Below
near+width/2 (default36 blocks), the original trace executes. Smoothstep from
near+width/2 to far+width/2 (default36..100 blocks) increases tolerated remaining
hit displacement from zero to0.25 depth-texture texel. The transition is continuous
in error tolerance; discrete iteration changes still require motion QA.
Footprint is calculated explicitly from the projected ray step and effective
view resolution/TAAU scale, without derivatives or implicit LOD sampling.

At least one original refinement update executes before stopping. Every possible
remaining original position must fit inside the same valid fetched depth texel
with conservative boundary margins. Thus its sampled hand/sky/terrain depth and
original final-depth eligibility are preserved. In addition, the entire remaining
z envelope must lie uniformly inside/outside the original depth-hit predicate
with a margin, projected UV displacement must be<=0.25texel, and remaining screen
depth uncertainty<=1/16 original depth tolerance. Screen depth is nonlinear:
two endpoints of the actual ray segment and current point are inverse-projected
using the same Photon helper as final hit composition. Homogeneous W must have
no pole, and the conservative Euclidean view displacement must stay within0.049block, reserving1mm toward
a0.05block cap. Near/far view displacement must not be inferred from NDC error.
This bound does not prove perceptual parity of reprojected color or fog.

A secondary cache reuses only the immediately preceding valid integer texel's
LOD0 depth before issuing `texelFetch`; invalid/out-of-range fetches are never
reused. Original ray arithmetic and every coarse hit test remain. First refinement
can reuse its just-found coarse depth. Thin edges, different texels and failed
error/classification envelopes continue the original refinement updates. No ray
weights or normalization change, and original sky/environment/fog composition is
retained. Fixed-depth source assumptions require immutable sampled attachment
through a draw, as original explicit depthtex1/combined depth usage intends.

## Static acceptance and exact artifacts

`python3 -m unittest discover -s tests -p test_photon_reflection_lod.py -v`
passed9 tests, no skips. Coverage: literal disabled original spec/settings bytes,
unchanged raytracer/license, original coarse loop/weights/composition, fail-closed
pins/scope/errors, near/fallback policy,3000 exact cache models,5000 bounded
refinement/hand/sky/error models, undefined-coordinate nonreuse, deterministic
archive/license/member-scope assembly, and actual pinned Iris
OptionAnnotatedSource/OptionSet/MutableOptionValues option discovery/defaults/both
overrides. The private gate is the sole new boolean option; stage macro does not
leak. The actual lexer fixture uses local Java source launcher, no Gradle/download
or GLSL driver compilation. Scalar fixtures use host double arithmetic and simple
perspective; they do not establish every GLSL float rounding or DH/TAAU matrix.

Original archive SHA256:
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Original LICENSE SHA256:
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
Manifest:
`0c20609c50ee65d98ec803eb96294a0d727bff82d81a6634abb1504518d89e1a`.
Helper:
`b5dd57b6f6155ada9489416ad94c5b4be00286615b9fce71ecf09400968ca44a`.
Default-off archive `build/Photon-ReflectionFootprintLOD-disabled.zip`:
`d11de4c94fb58520e9972a816e9739486fa8578b876fbd20c17aa933a4a82e16`.
Explicit-on source archive `build/Photon-ReflectionFootprintLOD-enabled.zip`:
`38b892a9ff4de1c19c77198ff8d3f553ada4ed070d7ea274d0e79a09ab602fe1`.
Both have2 modified original members,529 unchanged original members, one added
helper and private metadata. Builders reject nonignored outputs, unknown archive,
unsafe/duplicate ZIP paths, changed source/license/helper and unknown control arms.
Generated archives remain ignored. Source-off means upstream paths; global-off
with the option compiled still includes wrapper validation overhead.

## Runtime stopping boundary and needed measurement

No runtime evidence exists for this adapter. A substantial remaining risk is
branch/register/cache-key/projection ALU overhead exceeding saved depth fetches,
particularly the reduced-profile4-refinement configuration. Prior AO texel-cache
regression and far-SSR caps with inconsistent gains are adverse evidence, not a
reason to assert this candidate works. Do not promote defaults or infer GPU gain
from scalar loop savings. This bounded refinement implementation was selected over
unsafe empty-segment advances without a conservative depth hierarchy; the actual
full coarse ray still visits every original sample and hits remain available.

Root/runtime operator should first qualify the exact archive/manifest/helper and
private Iris option, compile/render the native reduced-profile noDH reflection
scene, and read back the five consumed core fields. Keep same default-off archive
and private option true for global OFF/ON measurements, with original Photon
mod-absent as independent baseline. Use balanced OFF/ON reversal and repeated
original/off/on total-frame plus GPU elapsed timing with matched fullscreen
resolution/scene/clock/wetness/focus/warmup; pause CPU builds during measured windows.
Reject cross-arm focus/resolution/history/option changes. Runtime preparation must
explicitly qualify these new hashes; existing SSR-cost source whitelist cannot be
repurposed as acceptance for this new adapter.

Before any default, inspect full-near, transition endpoints, far terrain/sky/hand
reflections, thin poles, silhouette/color edges, moving water/glint, grazing views,
slow translation/yaw reversal, original4 and higher refinement options, TAA/TAAU,
DH-present combined projection/depth plus noDH, absent/invalid bridge, shader and
config reload. Confirm no new bright band and bounded-world-error fallback under
far-depth amplification. A successful driver render is separate from quality and
from GPU improvement. Stop/reject if safety ALU regresses or eligible footprint
savings provide no useful repeated total-frame/GPU signal; keep failure evidence.
