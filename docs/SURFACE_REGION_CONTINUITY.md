# Receiver continuity for distant shading regions

2026-10-05. Original ShaderLOD MIT helper:
`shaderpacks/common/shaders/include/shaderlod/surface_region_continuity.glsl`,
SHA-256 `497c38b723df0255e9b07b5b62e695d644f0d0eb19538f14db5f95846e9cc040`.
It implements a reusable geometry/material eligibility gate for explicit
distance-dependent shading-frequency adapters. Existing 2×2 helper, Photon
sources, Java transport and runtime are unchanged by this contribution. This
helper has no uniforms, samplers, derivatives, transport or shader-pack names.

## Exact integration contract

```glsl
bool shaderlod_surface_region_eligible(
    bool enabled, int region_width,
    vec3 positions_view[16], vec3 normals_view[16], int material_ids[16],
    vec2 lightmaps[16], bool protected_members[16], float near_blocks,
    bool lighting_spread_available, vec2 ao_shadow_spread,
    ShaderlodRegionLimits limits);
```

`shaderlod_region_default_limits()` constructs the conservative defaults below.
The helper is standalone; an adapter copies this exact owned source into its
explicit include tree, records its hash/license and includes it once. Repeated
inclusion is guarded with `#if !defined` to avoid Iris boolean-option discovery
leaks. It does not modify or rewrite arbitrary shaders.

Array storage is fixed at16 elements for GLSL4.1. The active members are the first
`region_width*region_width` elements, **packed row-major with stride=region_width**.
Width2 therefore uses indices0…3, not a subset at stride4. Initialize inactive
padding to benign values even though the function does not inspect it. Width4
uses all16. Width1 explicitly returns false for original full-rate shading;
unsupported widths, disabled controls and invalid limits also return false.
**False always requires the untouched original full-resolution lighting path.**

All positions are camera-relative **view-space blocks** and normals are normalized
in the **same view space**. Normals should represent receiver geometry; a world
normal must be rotated into view space by the adapter before use. Positions
reconstruct receiver geometry from already validated current-frame depth. The
helper compares position separations along receiver normals, which permits
planar slopes with unequal view Z; it does not require equal raw/linear depth.
Raw invalid/sky depth is not recognizable from a finite placeholder position:
the adapter must flag it or reject the region before this call.

Material IDs are nonnegative adapter-defined factorization classes, and all must
match. Matching IDs alone do not establish diffuse factorability. Each actual
consumer must still exclude SSS/sheen/emission/metals, strong specular/reflections,
wetness and incompatible material maps. Normal/lightmap/geometry agreement is
separate from that material proof.

`protected_members` covers each sky, hand, invalid-depth, out-of-bounds odd-region
edge, overlay, special or otherwise unsupported receiver. Any protected member
rejects the whole region. Every member must be radially farther than `near_blocks`;
the minimum distance governs near protection. No foreground/sky/hand member is
blurred into the region by this helper, and no subset of accepted corners can
license a failed interior pixel.

## All-member geometric checks and limits

Every active member is checked for finite positions/normals/lightmaps, position
components within±65536 blocks, normal squared-length error≤0.001, normalized
lightmap channels[0,1], matching material and near/protected status. Every pair
is checked for distinct position, normal agreement, lightmap agreement and
normal-projected plane separation in **both** member normals. Width4 has120 pair
checks; these are real classifier work, not an assumed free proof.

Every local screen cell is checked, including all nine interior/outer cells at
width4. Each cell's two triangles must have nonzero/non-singular area, distinct
adjacent edges and consistent winding. Coincident, collinear or folded geometry
returns original. The smallest adjacent pixel-edge length across **all** cells
sets the footprint-scaled tolerance, so increasing a region to4×4 does not loosen
plane eligibility merely by increasing its overall span.

| Limit | Default | Units / meaning |
| --- | ---: | --- |
| `plane_absolute_blocks` | 0.015 | Absolute normal separation in blocks |
| `plane_relative` | 0.015 | Additional separation per block of the smallest adjacent pixel edge |
| `plane_max_blocks` | 0.08 | Hard cap on total separation, independent of world distance/region width |
| `minimum_normal_cosine` | 0.995 | Minimum pairwise supplied normal dot product, nominally about5.7° |
| `maximum_lightmap_delta` | 1/255 | Maximum pairwise normalized difference per channel |
| `minimum_area_ratio` | 0.0001 | Triangle area divided by its adjacent edge lengths, dimensionless |
| `maximum_ao_shadow_spread` | (0.10,0.025) | Optional normalized max-minus-min AO/shadow threshold |

Minimum edge/pair length is0.000001 blocks. All limits are range/finite checked;
invalid policy data never silently broadens eligibility. Defaults are conservative
starting points for quality testing, not demonstrated safe universal thresholds
or a formal error certificate. Binary32 precision loss that collapses coordinates
to singular geometry retains original shading.

Optional AO/shadow spread must already describe the **complete receiver region**
or be supplied by an explicitly measured adapter probe. With availability=false,
the helper makes no lighting-edge check. Even finite low spread is heuristic:
geometry agreement cannot detect a thin cast shadow on a planar surface, and
finite receiver probes may all miss it. Derive the union of all original receiver
reconstruction neighborhoods using the adapter's actual sampling coordinates.
Do not check only representative taps or assumed corner samples. The consuming
adapter owns actual texture dimensions, bounds and AO/shadow input collection.

## Work avoidance, transitions and quality

Run this gate before representative expensive lighting. The adapter supplies its
own stable screen-region origin, radial LOD bands, discrete evaluation-rate
selection and original fallback. This helper performs no texture fetch, lighting,
albedo sampling, sample-phase rotation, history reuse, rate selection or blur.

For factorable diffuse lighting, share an albedo-independent coefficient while
retaining each pixel's original full-resolution material/albedo, specular,
reflections, overlays and final composition. A4×4 representative is an explicit
spatial approximation. Shared shadow/highlight visibility or half-float cache
precision must be disclosed by the adapter and inspected independently.

The helper supplies no distance blend. The adapter owns centered transition
bands, minimum-region distance and stable phase. Transition pixels blending
cached and original lighting must evaluate both; failed regions remain wholly
original. Never interpolate through invalid cache holes or apply bilinear reuse
across foreground/material/sky boundaries. Geometry/material agreement does not
prove lighting frequency is low enough, even far from the camera.

The quality worker handed this API/hash directly to `lod_opportunities`. Root
then halted the standalone4×4 pack expansion to prioritize the shared shading-rate
policy and adapter contract; no consumer copy or actual integration exists yet.
This bounded geometry helper remains ready for that shared-system handoff. No
existing2×2 adapter or mod configuration was edited here. Mod settings, declared
uniforms and actual shader consumption remain separate evidence.

## Acceptance and required runtime evidence

`python3 -m unittest discover -s tests -p test_surface_region_continuity.py -v`
passed all9 fixtures in0.721s, with no skips. They cover240 seeded planar/slope
cases; all16 individual depth/material/lightmap/normal/near/protected changes;
interior defects with unchanged corners; pairwise normal disagreement missed by
an anchor-only check; every-cell degeneracy, folded/coincident geometry, float32
coordinate collapse, NaN/infinity, invalid policy/domain, widths1/2/4, active
prefix storage, capped footprint tolerance and optional spread thresholds.

A reviewed scalar reference uses binary32 arithmetic primitives from the existing
2×2 test module. This is not GLSL execution, and driver instruction contraction or
rounding can differ near thresholds. The source hash binds this reviewed helper
to the fixtures without claiming a numerical lighting certificate.

Actual pinned Iris `OptionAnnotatedSource` verifies no helper references/options
leak. Actual Iris Jcpp and embedded glsl-transformer `EnhancedParser` parse three
GLSL410 complete harness translation units using widths1/2/4, including repeated
helper inclusion. A deliberately malformed function parameter is rejected.
The parser is grammar-only: it does not resolve GLSL types/overloads, execute Iris
transformations or compile/link a GL program. Provenance is Iris outer JAR
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`, embedded
transformer `b8ca5e61b41ae18ddd5d4d5d6785cb9acd8fd7ac0c5c0b0689b872b039c11b9f`,
EnhancedParser class
`11133ca7cbfbc89777b15f06a31323b26c1ab6448b1822f4086e9503f3527814`, and ANTLR
runtime `54665d2838cc66458343468efc539e454fc95b46a8a04b13c6ac43fc9be63505`.

No client, GPU, shader reload or Java/Gradle build ran. Actual adapter entry points,
pass/texture layout, linked controls, disabled parity and driver compilation need
their own acceptance. Runtime QA must cover near texture detail, interior thin
geometry, material/lightmap transitions, flat-ground cast/contact shadows,
grazing surfaces, motion/TAA and reload/config changes. Sole runtime/operator
ownership applies. Measure repeated matched original and optimized whole-frame
GPU and total-frame timings, including all16 classification/member reads,
120-pair/18-triangle checks, AO/shadow probes, fallback/transition and cache costs.
No performance claim follows from this helper or nominal1-per16 shading rate.
