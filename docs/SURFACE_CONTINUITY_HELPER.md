# Surface continuity for spatial lighting LOD

2026-10-05. Original ShaderLOD helper in
`shaderpacks/common/shaders/include/shaderlod/surface_continuity.glsl`. Its frozen
SHA-256 is `2fff58476c6d49d40eb61e5f6683dc58697ed964f128a3f444442677b06327b0`.
This is a practical conservative receiver classifier, not a formal lighting error
certificate. No sampler, uniform, texture fetch, derivative, history, blur, albedo
input or Photon identifier appears in its API. Shader compilation/rendering and
quality remain unverified for this helper until a consumer is tested in-world.

## Integration API and original fallback

`shaderlod_surface_tile_eligible` accepts an enabled flag, four camera-relative
view positions **in blocks**, four normalized normals in the **same view space**,
four nonnegative integer material/class IDs, four normalized two-channel lightmap
values, four protected-member flags, a radial near distance in blocks, an optional
normalized AO/shadow spread, and `ShaderlodSurfaceLimits`. Array order is row-major
`(0,0), (1,0), (0,1), (1,1)`. The helper returns only a boolean. Passing world-space
normals alongside view positions is invalid integration even though both are
finite. The adapter must apply its view rotation to decoded geometric normals.

Disabled, invalid limits, nonfinite data, positions outside ±65536 blocks per
component, unnormalized normals, invalid lightmaps/material IDs, mixed material
IDs, inconsistent normals/lightmaps, singular/coincident reconstructed members,
plane separation or any protected/near member returns false. **False requires
the untouched original full-resolution lighting path.** This helper never selects
replacement lighting, drops fragments or smooths final color. Caller-side quality
or unsupported-preset rejection must retain that original path too.

The adapter flags sky/invalid depth, hand, out-of-bounds odd tile edges, overlays,
unsupported materials, special/wet/reflective receivers and incompatible geometry
sources as protected before calling. A sky placeholder that happens to contain a
finite position is not automatically recognizable here. Reject a whole tile;
never reconstruct a tile from only its accepted members. This keeps transition
holes and foreground/sky/hand boundaries on the original path.

Geometry uses all six pair separations projected onto each member's supplied
normal. A planar slope can have substantially different view Z yet zero normal
separation. This avoids requiring equal linear depth on slopes. It still rejects
sharp foreground steps and normal boundaries. The first two pixel edges must
span a nonsingular plane, and every pair must have distinct reconstructed
positions. Normals should be geometric/flat receiver normals; bump maps may
conservatively force fallback and require a separately verified adapter policy.

## Conservative defaults and units

| `ShaderlodSurfaceLimits` field | Default | Units / meaning |
| --- | ---: | --- |
| `plane_absolute_blocks` | 0.015 | Absolute allowed normal separation in blocks |
| `plane_relative` | 0.015 | Added separation per block of the smaller of tile edges 0→1 and 0→2 |
| `plane_max_blocks` | 0.08 | Hard cap on total allowed normal separation in blocks, independent of distance |
| `minimum_normal_cosine` | 0.995 | Minimum pairwise normal dot product (about 5.7°) |
| `maximum_lightmap_delta` | 1/255 | Maximum pairwise absolute difference per normalized lightmap channel |
| `minimum_area_ratio` | 0.0001 | Minimum cross-product area divided by the first two edge lengths; dimensionless |
| `maximum_ao_shadow_spread` | (0.10, 0.025) | Maximum normalized AO/shadow max-minus-min if available |

Normal squared length tolerance is 0.001; minimum distinct edge/pair length is
0.000001 blocks. Limits are checked for finiteness/range; no silent clamping of
invalid policy inputs. Near protection uses every member's radial view-space
distance, with `distance <= near_blocks` protected. No distant tile gains an
unbounded plane tolerance merely by being farther from the camera.

AO/shadow spread is optional **only when already available** to the adapter. With
`lighting_spread_available=false`, its values are ignored and the helper offers no
shadow-edge rejection. Geometry agreement cannot detect a thin cast shadow on
flat ground, including a shadow missed by every queried receiver. Even available
spread is not proof of absence of a subpixel shadow/penumbra/contact feature.
AO bent-normal disagreement requires an additional adapter-specific check if used.
An adapter may add stricter fallbacks; do not weaken limits merely to rescue poor
coverage. Thresholds need scene and motion validation, not aesthetic assumptions.

## Work avoidance, transitions and material detail

Call this eligibility helper before expensive representative AO/PCF/SSRT/diffuse
evaluation. Its small fixed pair checks are extra work whose real GPU cost must
be counted. Optional spreads should come from existing cheap inputs or a separately
measured edge probe; calculating full lighting at all four pixels to feed the
classifier would erase the intended saving. No derivative-based sampling pattern
or divergent implicit-LOD texture fetch is introduced by this helper.

An eligible tile permits an explicitly audited spatial adapter to share only
factorable diffuse irradiance. Preserve each pixel's upstream full-resolution
albedo/material detail, specular/reflections, overlays and final composition.
Material IDs must represent adapter-proven compatible factorization classes, not
merely look similar. Zero SSS/sheen/emission and nonmetal restrictions are outside
this generic helper, and belong to the Photon adapter.

The helper has no distance transition or sample-phase selection. The consuming
adapter owns stable tile phase, centered distance-band behavior and motion/TAA
checks. During a blend from original to cached lighting, original full-rate
lighting is required as well; that transition region pays both evaluations.
Invalid tiles stay wholly original rather than interpolating through missing
cache data. Avoid bilinear irradiance across tile eligibility/depth boundaries.

## Pinned Photon source inspection and acceptance

Inspected local original Photon v1.3b archive SHA-256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` and
`shaders/program/d4_deferred_shading.fsh` SHA-256
`212081e707c20c78e17cbfd3380b2f22eb696cc1aa14bdf006c07d59f53f67e0`.
Its relevant entry points are the terrain branch of deferred4: gbuffer unpacking
at lines 335–347, geometric normal/material construction at 358–369, diffuse
lighting at 552–575 and full-resolution highlight composition at 608 onward.
No pinned upstream file was edited. A Photon derivative must retain Benjamin
Stott/SixthSurge attribution and the upstream LICENSE. This original helper's
copy/hash and explicit include integration belong to the lighting-rate worker.

`tests/test_surface_continuity.py` contains a reviewed binary32 scalar reference,
independent seeded planar geometry properties and source hash/API checks. It
rounds additions, multiplications, subtraction and square roots to float32 in
source order; GLSL dot/cross instruction contraction or different rounding can
still differ near thresholds. A hash freezes the tested source; it does not execute
GLSL. Tests cover slopes with unequal Z, foreground depth steps, capped far
tolerance, material/lightmap/normal boundaries, NaN/infinity, invalid coordinates,
normal normalization, near/protected/disabled cases, singular tiles and optional
AO/shadow spreads. No driver compiler, Java/Gradle or client is invoked.

Required runtime evidence remains: consumer linkage and native viewport/format
checks, original/disabled/enabled in-world compilation, eligible-tile debug views,
near albedo parity, foreground/sky/hand/material transitions, thin/contact/cast
shadows, grazing slopes and camera motion/reload/TAA. Sole runtime/GPU ownership
and shader reload coordination stay with the root/operator. Measure repeated,
matched original and optimized **whole-frame GPU and total-frame timings**, including
classifier/prepass/fallback/transition overhead. No performance claim follows from
these source-only fixtures or from accepted-tile coverage.
