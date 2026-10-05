# Spatial shading LOD: minimal mod interface

2026-10-05. Source-only proposal for the
[general 50% goal](goals/general-lod-50-percent.md), coordinated with
`lod_opportunities`' lower-rate `DISTANT_DIFFUSE_IRRADIANCE` investigation. Only
this document is owned by `effect_api`; no Java/config/GLSL/resource edits,
tests, compiler, archive or client ran. This proposal is not an accepted shader
consumer or performance/quality result.

**Recommend:** reuse the existing immutable distance/work policy and config shape,
with one additive `OPAQUE_LIGHTING` effect only when root selects an explicit
spatial consumer. Its first declared Photon capability is opaque diffuse
irradiance reconstruction. Keep albedo, material decoding, specular/reflections,
geometry and existing shadow/AO policy controls separate. A four-to-one evaluation
rate is a plausible quality/performance tradeoff worth testing; it does not need
a formal worst-case image-error proof before a bounded experiment.

## What the budget means

The adapter's upstream unit is four diffuse irradiance evaluations for one 2×2
tile of **rendered opaque pixels**, not four display pixels regardless of TAAU.
`mediumWorkRatio`/`farWorkRatio` request a retained evaluation fraction. The pure
policy can calculate `ceil(4 * retainedRatio)`, clamped to a legal floor of one;
1/.75/.5/.25 correspond to requested counts 4/3/2/1. Do not multiply irradiance,
AO strength, shadow visibility, radiance, light intensity or albedo by this ratio.
Geometry/material edge rejection raises the legal floor to four independently
of the requested distance budget.

Use the nearest camera-relative block distance among all four valid receivers.
Full near quality means a tile cannot lower its rate when any member needs full
near shading. A conservative first experiment may retain all four through the
entire centered near transition band. Keep the documented distinction between
full-near safety and the policy's continuous distance interpolation. The policy
does not decide receiver similarity, reconstruction weights or rate legality.

The selected initial experiment supports only counts one and four. Declare that
limitation: requests for two/three select original four-evaluation fallback until
matching representative patterns/storage are implemented. Do not claim their
caps or smooth spatial transitions were consumed. The first cost control can
apply one representative only beyond the completed far band; motion/transition
inspection must then decide whether to implement intermediate rates or a stable
tile-coherent transition schedule. Such a schedule requires a declared rate
mapping; it is not automatically provided by smooth ratios or TAA.

## Proposed mod surface

`MATERIALS` is the wrong existing category: its reference adapter controls
POM/material work, while this experiment retains full-rate material and albedo
reads. `SHADOW_FILTERING`/`AMBIENT_OCCLUSION` describe protected lighting
sampling; reusing those controls would imply that their current protection or
quality budgets authorize a spatial diffuse change. `VOLUMETRICS` is unrelated.
Reuse their implementation shape, not their semantic identities.

A future root-owned integration would add `OPAQUE_LIGHTING` with stable suffix
`OpaqueLighting` and config section `effects.opaque_lighting`. Reuse `mode`,
`near`, `far`, `transition`, `mediumWorkRatio` and `farWorkRatio`; no tile-size,
reconstruction-tolerance, registry or driver-specific public config is needed
for the first pinned 2×2 adapter. Adapter-owned fixed guards must be recorded
alongside source hashes. Keep defaults INHERIT, 32/96/8, unity ratios and the
existing protection setting. Experimental ADAPTIVE/far=.25 is opt-in, not a
promoted default.

Use extension **version 2** to make the new field group distinguishable from
version 1's six-effect/37-field source contract. Existing core version 2 and all
existing per-effect field names/mode numbers retain their meanings. The additive
effect adds six fields, giving 43 extension fields including the version:

| Proposed field | Meaning |
| --- | --- |
| `shaderlodOpaqueLightingMode` | 0 INHERIT, 1 FULL, 2 ADAPTIVE |
| `shaderlodOpaqueLightingNear` | Full-to-medium centered boundary in blocks |
| `shaderlodOpaqueLightingFar` | Medium-to-far centered boundary in blocks |
| `shaderlodOpaqueLightingTransition` | Full centered band width |
| `shaderlodOpaqueLightingMediumWorkRatio` | Requested medium retained evaluation fraction |
| `shaderlodOpaqueLightingFarWorkRatio` | Requested far retained evaluation fraction |

The new consumer must require its exact accepted extension version, valid fields
and core global enable before reducing work. Absent mod/version 1/unknown mode,
unsupported pinned source, FULL or invalid conversion selects original full-rate
shading. INHERIT preserves the adapter's original/default pack policy, which is
full rate in this proposed adapter. An old version-1 consumer must explicitly
review version-2 acceptance; changing the number alone cannot establish support.
No new capability uniform or automatic shader rewriting is proposed.

Do not add OPAQUE_LIGHTING to the shadow/AO budget-cut category merely because
it participates in opaque lighting. Its separate opt-in controls spatial
diffuse evaluation; existing shadow/AO protections remain true and keep their
sample budgets/radii/strengths unchanged. The adapter must independently describe
whether its reconstructed quantity includes shadow/AO modulation. Keeping their
budgets full does not by itself prove unchanged spatial shadow detail.

## Photon material and reconstruction boundary

Pinned `d4_deferred_shading.fsh:335–367` decodes per-pixel albedo, material mask,
flat normal and lightmap levels. `material.glsl` additionally carries emission,
roughness, SSS/sheen, porosity, metal flags and specular parameters; a matching
material mask alone is not a similarity certificate. Current reduced Mac options
disable normal/specular mapping, but that cannot be assumed for future profiles.

`get_diffuse_lighting` is not already a clean albedo-independent irradiance
function: SSS/sheen uses albedo, emission is added before its final albedo factor,
and the metal diffuse correction is material-dependent. Do not cache its final
RGB and divide by representative albedo. Factor only an explicitly reviewed
irradiance component, or initially restrict eligible receivers to simple opaque
nonmetal, nonemissive, non-SSS, unmapped materials. Retain the original material
emission, SSS, specular/reflection and local lighting composition where applicable.
Multiply reconstructed irradiance by each recipient's own full-resolution albedo.
Albedo texture variation itself need not reject an otherwise coherent tile: that
detail is intentionally retained at full rate.

The four-member classifier must reject sky/hand, water/translucency, overlay/
entity mismatch, invalid/out-of-bounds receivers, depth discontinuities, facing/
normal discontinuities, materially different lighting parameters and incompatible
lightmap levels. Begin with DH disabled; DH/vanilla projection boundaries select
full original until explicitly implemented. Use linear/view-space depth or a
receiver-plane test, not a raw reversed-depth delta. Guard normal/material/
lightmap variation independently; distance alone cannot authorize cross-surface
reuse. Guard thresholds are pragmatic starting values requiring quality tuning,
not an exactness requirement that forbids the experiment.

The shader owner selected **combined diffuse irradiance with representative
upstream shadow/AO**, rather than sharing only cheap sky/blocklight arithmetic.
Initially exclude SSS/sheen/emission/metals, mapped/wet materials, strong specular/
reflection paths, overlays/hand/sky and DH boundaries. Store irradiance RGB and
scalar opaque shadow in alpha; colored shadows stay outside the initial consumer.
Per-recipient albedo and eligible specular math remain full resolution, but using
the representative shadow factor in that math is still a spatial approximation.
All original shadow/AO counts, radii and strength parameters remain unchanged for
each evaluated representative; this does not mean all four original spatial
lighting evaluations occurred.

AO/bent-normal and shadow inputs can vary on a tile containing one smooth
geometric surface. Full-resolution albedo and a geometry-only classifier cannot
preserve a cast-shadow boundary by themselves. Declare this limitation and inspect
cast-shadow/foliage motion early. If it produces visible failures, a pragmatic
next guard can compare existing reconstructed AO variation and cheap center-shadow
comparisons across the four members, rejecting large spreads or ambiguous edge
values. This is a practical quality guard to tune, not a formal no-occluder proof;
its extra fetch cost must enter the kill test. Do not silently claim default
protection establishes such a fallback, or reject the entire spatial experiment
only because this guard cannot prove every worst-case pixel.

## Coherence, storage and acceptance

All members must agree on one static tile origin, rate, validity decision and
representative selection. The chosen shader plan generates a producer cache
every frame, then reconstructs eligible recipients; it does not reuse history or
randomly rotate representatives across frames. OpenGL 4.1 has
no guaranteed fragment invocation sharing; neighboring fragments cannot read a
representative's output during the same draw. A shader branch selecting one of
four fragments proves no reuse without legal inter-pass storage/synchronization.
The shader owner corrected an initial `colortex16` suggestion: pinned ordinary
Iris exposes target indices 0–15, and an external Voxy-related declaration does
not make target 16 an ordinary free cache. The revised candidate is `colortex2`
only for a strict reduced profile with NORMAL_MAPPING=false, SPECULAR_MAPPING=false
and TAAU=false, after proving its gbuffer/clipping uses are inactive. Every read,
write, render-target list, viewport and format must be audited before repurposing
it; an apparently unused sampler is not enough. A lower-rate producer viewport
must not shrink unrelated full-resolution gbuffer attachments. Verify actual
active bindings, driver limits and DH variants rather than assuming source
declarations count live units. Unsupported maps/Voxy/DH/TAAU/resource combinations
select compile-time original fallback. This conditional slot reuse is an adapter
restriction, not a general mod API assumption.
Audit Photon attachment ownership, dimensions/formats, ping-pong and clear rules;
never sample an attachment being written or reuse stale irradiance after reload,
camera/world change, resizing or mode changes. Unsupported resource state falls
back to original rendering.

Reuse lazy immutable controls per registration/frame and validate float conversion.
Snapshot consistency within a uniform group is not proof that every pass sees
the same generation after a mid-frame config reload; root must establish an
update boundary or carry a coherent producer/consumer state. A uniform-coherent
global FULL/OFF switch must not reconstruct data generated under another mode.

Record the exact consumed fields: core global enable/version, extension version,
the new mode/bands and only the ratios/rates actually used. Uniform declarations,
NeoForge settings and package metadata remain distinct from linked driver fields,
executed reconstruction and avoided evaluations. A single ordinary explicit
consumer plus its existing measurements is preferable to another diagnostic
framework. List excluded material/dimension/DH paths as unsupported or full-rate
fallback, not partial support.

Root/shader owner should first assess whether the eligible diffuse work saves
enough to pay for tile classification, extra storage/passes and reconstruction.
Then inspect original/off/on near/far, albedo detail, contact/cast-shadow edges,
thin foliage, emissive/SSS/metal boundaries, movement and temporal disocclusion.
Iterate practical thresholds/rates to obtain little discernible quality loss;
revert or stop for flat timing or visible failures. Repeat balanced whole-frame
GPU and total-time controls after source/quality acceptance. No 75% effect-work
reduction or 50% total-throughput gain follows from the 4→1 count alone.
