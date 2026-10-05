# Photon pass split feasibility

Read-only design audit, 2026-10-04. No implementation, shader compilation,
build or client launch was performed for this design. Pinned sources are the
Photon v1.3b archive and Iris 1.8.12 JAR identified by exact SHA256 values in
`APPLE_FEATURE_BUDGET.md` and upstream metadata in `dev/runtime.lock.json`.
Archive paths/line numbers below refer to those original bytes.

The coordinator has now linked and rendered original Photon with real SSR
on the Mac **without DH**. DH+SSR remains unverified. The objective here is
to identify a correct bounded topology, not assume compatibility work improves
performance or establishes the ordinary-workload performance gate.

## Conclusion

A sky resolve before opaque shading can reuse existing `colortex0` without
destroying the material Gbuffers. It should remove the atmosphere scattering
LUT from opaque deferred4, but saves only **one** dynamically bound sampler
in the masked DH+SSR source estimate: 16 becomes 15, still above this Iris
version's 14 dynamic composite slots. It also leaves composite1's separate
DH water SSR pressure unresolved. **Sky-only splitting is insufficient as
the complete DH+SSR solution.**

A two-phase lighting/SSR-postprocess split can plausibly fit deferred4, and a
two-phase layer/final-fog split can plausibly fit composite1. Both introduce
an extra HDR storage boundary, so they are not mathematically bit-exact under
Photon's existing packed HDR format. Treat them as compatibility prototypes
requiring explicit image/motion parity and performance checks. Neither should
be described as an already quality-preserving optimization.

## Existing resources and stage order

`include/buffers.glsl:4–19,21–40` defines these lifetimes:

| Target | Format / size | Existing live contents |
| --- | --- | --- |
| 0 | `R11F_G11F_B10F`, full resolution | Skytextured before d4, shaded scene afterward, later bloom/final color |
| 1 / 2 | `RGBA16`, full resolution | Packed material Gbuffers through composite1 |
| 3 | `RGBA8`, full resolution | Glint/damage overlay through d4, refraction after translucent draw, later bloomy fog |
| 4 | `RGBA16F`, 192×108 | Sky map, light palette and SH data |
| 5 | `RGBA16F`, full resolution | Persistent previous scene color |
| 6 | `RGBA16`, half width/height | Persistent AO, then temporary fog transmittance via flip |
| 7 | `RGB16F`, half width/height | Persistent fog scattering |
| 9 / 10 | Cloud-dependent resolution | Low-resolution cloud results consumed by cloud temporal upscale |
| 11 / 12 | Full resolution HDR/data | Persistent clouds, age, apparent distance and indirect scattering |
| 13 | `RGBA16F`, full resolution | Translucent color layer |
| 14 | `RG16F`, half width/height | Persistent AO depth/history age |
| 15 | `R32F`, full resolution | Combined vanilla/DH depth |

Target 0 is not a material Gbuffer. Preserving its terrain pixels does not
substitute for retaining targets 1/2. Target 0 has no alpha channel for an
extra validity flag. Target 3 cannot store HDR lighting without a format and
lifetime redesign. Histories 5/6/7/11/12/14 cannot be casually borrowed.

The existing deferred stages are d0 sky-map generation, d1 cloud rendering
and combined depth, d2 cloud upscale, d3 AO, d4 opaque/sky shading. Wrapper
`world0/deferred2.fsh:1–5` selects `d2_clouds_upscaling.fsh`; d3 writes targets
6/14 (`program/d3_ao.fsh:14–19`). These slots are occupied. d4 writes 0 and,
on MC 1.21.1, clears 3 (`d4_deferred_shading.fsh:14–21,193–195`).

Translucent world/DH rendering follows deferred shading. DH water writes
its material data to target 1 and defers water color; vanilla water writes
refraction 3 and translucent color 13. Composite0 computes fog; composite1
blends layers, resolves DH water, applies clouds and final fog. Target 6's
explicit `flip.composite1.colortex6 = true` in `shaders.properties:245` is
part of AO/fog history preservation, not an arbitrary extra flip.

Iris tracks a per-target main/alternate texture and changes the sampled side
between writing passes. A new fullscreen pass must sample the previous side
and write the alternate side, then expose the new side to every consumer.
Prove these generated framebuffer mappings rather than adding compensating
flips until an image looks plausible. Extra 0 flips also affect the side later
translucent/blending consumers receive.

## Sky resolve into existing target 0

For the current mask, create a full-resolution pass after d3 and before d4.
It reads combined depth, computes the exact existing sky branch for sky pixels,
and copies the original target-0 texel for terrain pixels. It writes only 0.
Do not clear target 3 or write targets 1/2 in this pass. Then opaque d4 copies
resolved 0 for sky pixels and executes its original terrain branch.

The split must retain original skytextured contribution, sun/moon/stars,
atmosphere, clouds/aurora, common fog and sky Purkinje order. Moving only the
expensive LUT sample into a prepass while leaving stars/clouds in opaque d4
does not eliminate the atmosphere dependency if border fog still needs it.

| d4 source-live sampler | After sky split | Reason |
| --- | --- | --- |
| `depthtex0` atmosphere LUT | Can disappear with current `BORDER_FOG=false`, ordinary clouds | LUT result serves sky; border fog/blocky-cloud paths need separate accounting |
| `colortex0` | Still live | Copy resolved sky into d4 output |
| `colortex4` | Still live | Vertex light palette and reflection sky fallback |
| `noisetex` | Still live | Protected shadow/SSR dither |
| `colortex11/12` | Still live | Clouds may be in front of terrain, d4 lines 653–660 |
| `colortex1/3`, `colortex6/14` | Still live | Material/overlay and protected AO lighting |
| `depthtex1`, `colortex15`, `dhDepthTex` | Still live with DH | Hand handling, combined depth and classification |
| `shadowtex1`, `colortex5/7` | Still live | Protected PCF and SSR history/fog |

The vertex d4 declares the atmosphere LUT, but its actual color expressions
use `get_sun_exposure/get_sun_tint/get_moon_exposure/get_moon_tint`; those
functions do not sample it (`lighting/colors/light_color.glsl:10–56`). Merely
removing fragment declarations is insufficient: final link reflection must
show the LUT is dead in both stages.

There is no valid inference that this frees clouds, AO or another binding.
Current source estimate is 15, not 14. Additional header declarations such
as dead `dhDepthTex1` do not count; active-uniform reflection remains the proof.
Other dimensions, `BORDER_FOG`, blocky clouds, galaxy/PBR and compute SH
variants require their own matrix rather than the masked estimate.

## Stronger deferred split: base lighting, then SSR and postprocess

This is a more useful compatibility prototype than sky-only splitting:

1. Base pass computes original material/normal/AO/diffuse/specular highlight
   through d4 line 583, then writes un-postprocessed linear lighting to 0.
   For sky pixels it copies original skytextured 0 without resolving sky yet.
   Preserve overlay target 3 until the next pass.
2. Resolve pass reads 0, reconstructs the same material/normal/positions and
   exact overlay-modified albedo where required, evaluates SSR, then applies
   the original edge highlight, border/common fog, terrain-cloud composition,
   rainbows and Purkinje in original order. It resolves the original sky branch
   from copied skytextured 0. It writes final 0 and clears overlay target 3.

The base pass's masked DH estimate is eleven samplers:
`{colortex0, colortex1, colortex3, colortex4, colortex6, colortex14,
noisetex, depthtex1, colortex15, dhDepthTex, shadowtex1}`.
The resolve estimate is thirteen:
`{colortex0, colortex1, colortex3, colortex4, colortex5, colortex7,
noisetex, depthtex0, depthtex1, colortex15, dhDepthTex, colortex11,
colortex12}`.
PBR's `colortex2` adds one to each. These lists follow operations in each
phase, not all include/header declarations; actual link and transformed Iris
source still determine the final allocation.

`colortex3` is needed in resolve when reconstructing a hardcoded reflective
metal's albedo tint after overlays. Clearing it in the base pass would change
that color. Do not expose the intermediate base color as target-5 scene
history; the existing end-of-frame consumer must continue copying the final
resolved scene. Fog history remains untouched.

This preserves expression order within each pixel apart from intermediate
storage: base lighting is now rounded to `R11F_G11F_B10F` before SSR is added,
instead of once after the original complete shader. It can produce visible
differences in dark/specular/night scenes. Changing target 0 to `RGBA16F`
reduces that error but increases memory/bandwidth across the whole pipeline
and changes the control's format. A new floating-point scratch target or a
proven temporary target-13 lifecycle are other explicit options, not free
solutions; even half-float storage is not identical to an unrounded GLSL
intermediate. Record tolerance and quality findings honestly.

Target 13 is unused until translucent draws, but borrowing it would require
clearing/restoring it after resolve and proving Iris's subsequent Gbuffer
draw target uses the clean correct side. It is not approved by this source
audit. Targets 9/10 have variable cloud resolution and cannot simply hold a
full-resolution opaque HDR buffer.

## Composite1's separate DH water blocker

Actual DH water SSR is called by `c1_blend_layers.fsh:366–390` through
`misc/distant_water.glsl:105–121`. `dh_water.fsh:202–204` returns early for
water after writing its Gbuffer; it is not the expensive reflection consumer.
`SSRT_LOD` at c1 line 130 selects DH ray depth. Current masked DH+SSR+VL
source estimate is up to fifteen dynamic samplers, also above fourteen.

An independent two-phase consumer split is plausible:

- **Layer resolve:** retain refraction, DH water evaluation and original
  `blend_layers_with_fog`. Write intermediate color to 0. Read the original
  opaque color, target-3 refraction, target-13 translucent layer, target-1
  DH water material, light/sky palette, scene/fog history, vanilla/DH depths
  and SSR noise. Do not apply foreground clouds/final volumetric fog yet.
- **Final visibility:** read that color, sample clouds and current fog,
  reconstruct only the existing front/back/translucency predicates needed
  for foreground composition, then apply the original cloud/fog/underwater
  order and bloomy-fog output. Finish the target-6 history flip here.

Layer-resolve estimated set:
`{colortex0, colortex1, colortex3, colortex4, colortex5, colortex7,
colortex13, noisetex, depthtex0, depthtex1, dhDepthTex, dhDepthTex1}`
(twelve). `colortex7` remains required by SSR; declaring it a fog-only resource
and removing it would corrupt reflection fog handling. Final visibility's
masked set should need color 0, clouds 11/12, fog 6/7 and the four relevant
vanilla/DH depths, plus palette 4 for analytic/underwater variants. Link every
variant; do not assume one scene prunes a runtime branch.

For the masked `BORDER_FOG=false` path, phase two does not need the pre-layer
`original_color`. The enabled border-fog path does: original c1 mixes
`original_color` back after layer blending. The last texture's previous side
must be retained through an explicit proven binding or scratch target if
that option is supported. Similarly preserve every flag determining the
original cloud overlay. A first prototype should explicitly constrain the
tested feature mask rather than pretend the broader variants are exact.

This split also introduces packed-HDR intermediate rounding. It reduces
sampler pressure; it does not prove a speedup. Clouds must remain after
translucent blending in this split, matching the original source.

## Pass slots and metadata

Do not overwrite d2/d3 or occupied composite2 (DOF). A straightforward explicit
prototype can shift opaque d4 to physical deferred5 and insert its new first
phase at deferred4, while retaining the original program macro where shared
includes depend on `PROGRAM_DEFERRED4`. For a sky-only experiment, sky is d4
and opaque is d5; for the stronger split, base is d4 and resolve is d5.
Neither name determines resource correctness by itself.

The compute `deferred4_a` SH pass runs around this existing slot; establish
its actual ordering and relocate/retain it before its consumers. It remains
disabled on the Mac profile but cannot be ignored in a distributed patch.
Update `program.world*/...enabled` directives and all three dimension wrapper
sets together; keep source-world macros independent of physical slot numbers.

For composite splitting, keep composite0 fog, split old composite1 into
physical composite1 and composite2, and shift original composite2 onward by
one. Update option-dependent DOF/TAAU/bloom/motion/FXAA/Intel pass enables,
the explicit target-6 flip, targets and all dimensions. Verify the pinned
Iris supported slot range before choosing the final numbering. `_a` compute
suffixes are not assumed to create a second graphics pass.

## Validation and stopping gates

First prove no-DH parity against the original already-rendering SSR pack,
with LOD disabled, at matched resolution/settings. Compare lossless HDR or
pre-tonemap readbacks where practical, plus displayed images, motion, TAA
convergence, refracted water, reflected terrain, glint/damage overlays,
clouds in front of terrain/translucents, sunset/night and underwater. Include
PBR/border-fog variants only when their resource/order design is complete.
Quantify intermediate rounding; a similar screenshot alone is insufficient.

Then record actual post-Iris linked sampler names/types/unit bindings and
framebuffer targets at each new stage. Verify histories across frames and
shader reloads, every declared supported dimension, optional DH absent and
present, and optional optimizer stack. Only then run repeated serial timings
of original/disabled/active and the split-disabled control.

The extra fullscreen passes add dispatches, color reads/writes, material
reconstruction, FBO transitions and tile/bandwidth cost. A sky prepass can
also run more sky bookkeeping than the original branch alone. Report total
frame and pass cost; a newly enabled effect is a compatibility improvement,
not a performance improvement over the prior effect-disabled profile.

Current recommendation: finish the DH-absent SSR performance proof first.
If useful gains persist, prototype the stronger deferred split and the
composite consumer split with explicit parity tolerances. Skip sky-only as
the complete allocator fix. Do not expand the shader framework or second
pack solely because this topology could link.

## License and distribution

The pinned archive's root `LICENSE` is the **Photon Shaders License Agreement**,
not GPL. Keep it intact in substantial derivative copies, preserve attribution
and the ACES include's separate notices, and retain source/hash provenance.
Its grant has restrictions on paid/monetized derivative distribution; local
implementation/benchmarks do not authorize publication. This audit does not
change the existing builder's license-copy behavior or request distribution.
