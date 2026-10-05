# Photon B15 glint-band source audit

2026-10-05. Bounded source and existing-image inspection by the fresh performance
chat's `optimization_research` agent. Only this report was written. No shader,
Java, world or harness changes, compilers, client launches or GPU runs were made.
Main/shared shaders remain reserved for the protocol owner; root owns clients.

**Observed:** original and global-disabled frame 50 show the western sun's
localized water reflection. Enabled B15 adds a broad pale/gold distant-water
band with an abrupt, nearly straight edge. No effect has yet been established
as its cause. Start with compile-time alloff and single-effect controls using
the same archive, request and upstream options. SSR and water are both plausible
distance boundaries in this exact view; neither should be promoted or fixed
solely from the image or geometry estimate.

## Exact inspected evidence

All three manifests are complete with 201 frames, request SHA-256
`6296e241779f516e2aa6b3379d34e0d0104247042c3bc99ad75a7dfac06bfcdc`.
The frame-50 camera is `(500.5,69.61999988555908,660.5)`, yaw `87.38257`,
pitch `3.735377`, time 12500, clear weather, native 2976×1672, minimal stack,
no DH, first person. The camera cell is air, actual FogType is NONE and its
full collision shape is false. These camera checks do not prove distant ray
visibility or a particular glint-producing shading path.

| Arm / run ID | Frame-0050 PNG SHA-256 |
| --- | --- |
| Original `d66ac242-6f0e-4292-af0f-973c37d2a131` | `192ad028a3ba2d9bbc4da84eb42bcbae2b9b420bc4437186713abc9726d86ea5` |
| Disabled `592394d3-9edc-435e-a78e-10ee28f5b2f3` | `f5a707f170f0f1fe3d89ac584a38893c2a0e146ff5be6b94f83702d3064bbcfb` |
| Enabled `7d5a0e26-d02c-4c76-9f47-51d3ef8db4a9` | `edd417a0cd53d8a462e493da6249a1c7f37b6e6b527f3cba5a174123d06bf9d0` |

Images/manifests are under
`run-workloads/photon-targeted-glint-r1/coastalwater/{high,patched-off,adaptive}/camera-quality/<run ID>/`.
Each image was viewed separately. The tool resized display to 2048×1151; source
PNGs remain native and unmodified. No pixel-error metric or all-frame motion
acceptance is claimed by this audit.

Original archive SHA-256:
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Both disabled/enabled use exact B15 SHA-256:
`b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c`.
Their effective option-map hash is identical:
`14b2ea3c377c18f2deaef7c2aaa3fbfa2065e2a13cf45a96fbe88d050ec5e308`.
Live bridge enable changes false→true, preset PERFORMANCE, generic bands 32/96/8;
pack SHADERLOD_ENABLED remains true with SHADERLOD_USE_MOD_CONFIG=true.

Loaded versions: Minecraft 1.21.1 / NeoForge 21.1.253 / Sodium 0.6.13 /
Iris `1.8.12-snapshot+mc1.21.1-local`. Exact jar fingerprints are in manifests;
Iris SHA-256 is
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`.
No DH artifact is loaded. B15 remains Photon v1.3b/protocol 2 on Apple M3 Max
OpenGL 4.1, with the reduced profile's feature limits.

Relevant matched options: environment and sky reflections true, smooth SSR
intersection steps 16 / refinement steps 4, waves 3 octaves, water parallax true,
analytic water false, water bands 96/256/32, VL true, cloudy fog noise true,
cloud mode 0/upscale 4, cached sky false, protected lighting true, cloud shadows
false, POM/normal mapping/specular mapping false. All five ShaderLOD effect
switches were true. Original/off/on shader frame counters at this frame were
2137/2122/2319; animation clocks 34.26981/35.0187/34.89674 seconds. Thus matching
camera/request does not give identical stochastic/wave/history phases.

## Relevant paths and actual switch boundaries

All anchors below refer to members of exact B15 ZIP, not current main files.
The root should inspect effective maps after each switch reload: false means
the macro is undefined, not a preprocessor definition with value zero.

**Water geometry/detail.** `program/gbuffers_all_translucent.fsh:468–484` calls
water parallax then water normal wrappers. `include/shaderlod/water_lod.glsl`
`:208–209` and `:256–257` directly restore original functions when WATER is off
or the program is shadow. With WATER on, `:211–223` classifies camera-relative
radial distance and dispatches original or fixed normal kernels. At N=3 and
PERFORMANCE this selects original 3 octaves near, 2 medium, 1 far.
`:259–266` retains original parallax near, chooses the PERFORMANCE medium kernel
(2 spacing steps and 2 height octaves), and returns unchanged coordinates far.
The original parallax uses spacing count 4 and 3 height octaves. The march is
data-dependent, so spacing count is not guaranteed executed iterations.

Finite normal kernels still take three height samples; analytic normals are off.
Lower octaves change slopes, not merely sampling cost. Both direct sun highlight
and reflected-ray direction consume the resulting normal. WATER off restores
wave/parallax detail while preserving Photon's water shading/refraction.

**SSR/environment fallback.** `include/lighting/specular_lighting.glsl:1–2`
selects the entire derivative only when SHADERLOD_SSR is defined. Its `:441–872`
off branch contains pinned original source. For water the mirror-like path
`:396–436` calls `trace_specular_ray`; the multi-ray rough branch excludes water
at `:323–324` and is additionally inactive with specular mapping false.

At `:217–229` the derivative uses **generic** bands, `length(view_pos)` and
the ray dither. Tier 1 caps smooth tracing to 8 intersection / 2 refinement
steps at PERFORMANCE; tier 2 does **no environment trace**. `hit_pos` remains
zero. Sky reflection still runs at `:235–239`, and no-hit returns it at
`:292–293`. Therefore SSR tier 2 can replace reflected screen terrain with
unoccluded sky radiance rather than simply a lower-detail terrain reflection.
Original-hit composition instead reads previous scene color and fog and applies
analytic reflected fog at `:263–291`. SKY_REFLECTIONS and
ENVIRONMENT_REFLECTIONS remain on in all isolation arms.

Direct sun GGX highlight is separate: `gbuffers_all_translucent.fsh:630–633`
adds `get_specular_highlight * light_color * shadows * cloud_shadows` before
environment reflection. Its formula is unchanged in the derivative. SSR-off
does not disable this direct glint. Water-normal changes can still change it;
SSR changes can alter the surrounding radiance and reflected sun/sky appearance.

**Fog.** `include/fog/overworld/raymarched.glsl:1–3` selects the FOG derivative;
its off branch begins at `:229` with complete original source. Active `:93–106`
clips ray length and caps the original length-based march with generic bands.
The new cap changes shadow/noise sample locations over the same fog interval.
`program/c0_vl.fsh:168–185` supplies the dither and ray; final foreground fog
composition is `c1_blend_layers.fsh:447–450`. A fog count discontinuity can
change apparent brightness even though the underlying fog extent remains.
SHADERLOD_FOG does not disable original analytic fog in the sky map or in SSR
hit composition; those paths remain as before.

**Clouds/sun visibility.** The cloud switch chooses original/derivative
cumulus, altocumulus and congestus includes; it does not globally remove clouds.
Cumulus on-source `:262–270` applies entry-distance cloud bands; off source
starts at `:434`. Corresponding altocumulus off starts at `:440`, congestus
at `:442`. Cirrus/noctilucent, weather and compositor remain upstream.

Cached-sky-off forces tier zero for deferred0 (`lod.glsl:143–150`,
`cumulus.glsl:261–270`). The reflection sky map calls `draw_sky` at
`d0_sky_map.fsh:145`, which uses cloud composition through `sky.glsl:216–281`.
That fixed-map path has no intended cloud-budget reduction here, although this
audit does not prove pixel identity of the rendered map/histories. Foreground
clouds can still affect water at `c1_blend_layers.fsh:430–442`, if apparent cloud
distance is in front of the translucent surface. Cloud shadows are false in
these captures. Their include is byte-identical upstream/B15; changing cloud
LOD does not rewrite its density function.

**Materials.** SHADERLOD_MATERIALS gates surface POM include and the solid
program's far-POM eligibility (`gbuffers_all_solid.fsh:244–245`). POM is false
in the actual glint arms. It is not the water-wave/parallax switch and is low
priority for this observation. An alloff residual still warrants checking
shared/protected-lighting, shader state and temporal inputs rather than assuming
materials explains it.

## Minimal option isolation matrix

Keep B15, global enable=true, PERFORMANCE, same source request/native framebuffer,
no DH, upstream reflection/wave/fog/cloud options, cached sky=false, protected
lighting=true, debug=false, analytic water=false and terrain policy=false.
Set each five-flag map explicitly and verify its resolved hash.

| Arm | CLOUDS | WATER | FOG | SSR | MATERIALS | What it tests |
| --- | --- | --- | --- | --- | --- | --- |
| alloff | false | false | false | false | false | Original effect bodies with live bridge enabled; isolates retained integration/state from LOD effects |
| SSR-only | false | false | false | true | false | Trace caps/far sky fallback with original water normals/parallax |
| water-only | false | true | false | false | false | Normal/octave/parallax choices with original reflection tracing |
| fog-only | false | false | true | false | false | Ray-sampling/integration changes with original water/SSR/clouds |
| cloud-only | true | false | false | false | false | Cloud budgets/composition with other effects original |

Column names have SHADERLOD_ prefix. Use alloff first, then SSR-only and
water-only as direct glint-path discriminators; fog/cloud follow for full scope.
These controls change compilation, so rewarm each shader/history and preserve
failures. Recheck original/off/on reversal near the implicated result.

If alloff reproduces the band, single-effect attribution is not clean: inspect
actual shader/bridge state, existing integration paths, exposure and animation
input differences before rewriting an effect. If one single reproduces it,
also remove that effect from all-active to test necessity. If singles are clean
but all-active is not, try water+SSR then the implicated fog/cloud combination.
None of these frames are suitable FPS measurements: PNG readback/writing stalls.

## Geometry estimates: useful overlap, no cause proof

[The targeted quality plan](TARGETED_WATER_QUALITY_PLAN.md) records a loaded
survey water surface of 62.8889 around this anchor. Assuming that flat level
extends to the viewed water, eye height above it is 6.7311 blocks. For radial
camera distance R, flat-water downward angle is `asin(6.7311/R)`.

| Boundary | Radial distance | Downward angle estimate |
| --- | ---: | ---: |
| Water full→medium transition | 80–112 | 4.827°→3.446° |
| SSR / unclipped air-fog medium→far transition | 92–100 | 4.196°→3.860° |
| Water medium→far transition | 240–272 | 1.607°→1.418° |
| Ideal flat glint aim at frame 50 | 103.319 | 3.735° |

Water distance uses world position minus camera position; SSR uses view-space
length. They approximately agree for this first-person rigid transform. Fog
uses its own clipped integration length, so its threshold cannot be assigned to
the same surface solely from camera radial distance. Sun radius 2°, waves,
surface variation and normal slopes spread glint beyond the ideal flat point.
No depth readback or projection matrix was inspected; do not assign exact screen
rows or call the straight image edge a measured 96/256-block ring.

The overlap explains why either water-normal tier or SSR fallback could appear
near the bright reflection. It does not eliminate fog/composition or establish
which changed pixels belong to a distance band.

## Conditional private-patch hypotheses after isolation only

No patch is selected or implemented by this audit. Keep any later experiment
in a pinned ignored/private source tree, with its base B15 hash, exact member
hashes/replacements and new archive hash. Coordinate source ownership first.

If SSR-only reproduces the band and alloff is clean, one narrow control is to
restore the original 16/4 environment trace **only for tier 2**, retaining B15
tier-1 behavior. The target is `trace_specular_ray:217–229`; leave sky fallback,
direct highlight, Fresnel, fog, water normals and composition unchanged. This
tests the no-trace far fallback independently from medium count reductions.
It restores expensive far work and carries no predicted speedup. A second
experiment with fewer far trace samples is justified only after this control
and thin/grazing-reflection quality checks. Default any experimental toggle off.

If water-only reproduces the band, split two private controls: restore original
normal in `water_lod.glsl:204–251` while retaining its parallax LOD, then restore
original parallax `:253–268` while retaining normal LOD. This separates slope
changes from coordinate displacement. Do not enable analytic normals or move
both distance bands in the same probe. Preserve original shadow paths, macro-off
and live-disabled behavior; no shader-wide rewriting or glint suppression.

If fog/cloud is implicated, first compare its original sampling bodies; no new
lighting approximation is warranted. Moving a threshold or weakening sun/sky
brightness can hide the band without correcting the cause and is not acceptance.

All hypotheses require the matched native sequence at 10 FPS or better, normal
advancing clocks/history, near and both water band edges, slow translation and
yaw reversal, grazing sun, reflected terrain and screen edges. Check band/ring
motion, glint intensity/position, texture pop and temporal shimmer separately.
The current bright band fails visual acceptance for this view regardless of
any earlier GPU gain. Performance tests must later run without capture readback;
quality repair alone does not establish the shared performance gate.

## Member fingerprints for a later pinned probe

| B15 member under `shaders/` | SHA-256 |
| --- | --- |
| `include/lighting/specular_lighting.glsl` | `6a4a8c53675f7287cf28168913f3e3e2b7ce9bd9d2fb5755548a27927ee4d92d` |
| `include/shaderlod/water_lod.glsl` | `d985339345a4273dd687ee6e8c6b330de925abead5404c3645c34ef6b8906e7a` |
| `include/shaderlod/water_height_static.glsl` | `0df0f5fcc3eaa6811a88d4c4f0e7fccf6412b299cc3c1578461e0d54abbea9d0` |
| `include/shaderlod/water_parallax_static.glsl` | `81ee952495a60f3b7c280aae190dbb6d2ef1fd29e3301ee41341f54762a6709c` |
| `include/fog/overworld/raymarched.glsl` | `872f04ad3770c571fcfe67a074aa2bb55ea646714626af02d167f71d4d4a791a` |
| `program/gbuffers_all_translucent.fsh` | `85cada2d33c3224f23070b8e11682a5cd2e087c03e8a23b3727868a0cb5b963d` |
| `program/c1_blend_layers.fsh` | `9a22e67280a57927c9dc8ca7ef194abb64b636de6a736651aa5b743035052cad` |

The c1 member is byte-identical to upstream, as are cloud sampling, sky.glsl,
cloud-shadow include and p0 cloud preparation. Downstream visible differences
can still propagate through their unchanged code. Actual linked/rendered
behavior remains the coordinator's evidence boundary.
