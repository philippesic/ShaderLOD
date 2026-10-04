# Next Photon work-avoidance candidates

Source audit: 2026-10-04, Photon v1.3b and the current explicit ShaderLOD patch.
The initial research audit ran while the coordinator planned its frozen 36-run
benchmark matrix. After its first baseline was aborted for harness option
normalization, the coordinator authorized candidate 1 implementation and static
checks before the restarted matrix. Candidates 2/3 remain research only. No
clients were launched. Rankings indicate implementation value, not measured
pass cost or guaranteed speedup.

The inspected ordinary Mac profile has `BORDER_FOG=false`, blocky clouds off,
`VL=true`, colored shafts/cloud shadows/voxel lighting/reflections off,
protected GTAO and PCF, and three water octaves. The default volumetric-fog
render scale is 0.50 in each dimension. Cloud temporal upscale remains 4.
These constraints make fullscreen work avoidance more relevant than adding
another distant PCF reduction. Original surface lighting must remain protected.

## 1. Depth-first deferred dispatch: avoid unused terrain atmosphere

Exact target: `program/d4_deferred_shading.fsh:main`, the `// Atmosphere` block
before `if (depth == 1.0)`. The combined sun/moon overload in
`include/sky/atmosphere.glsl:atmosphere_scattering` performs four 3D LUT reads
and six `nvidia_phase_area` calls (three RGB calls each for sun and moon), plus
LUT-coordinate construction and phase/color arithmetic. It executes in source
for every deferred pixel. With border fog and blocky clouds disabled, its result
is consumed only by the sky branch. Terrain rainbow rendering does not consume
the atmosphere variable.

Proposed algorithm: when both options are absent, compute atmosphere inside
the existing sky branch and keep its exact original function and arguments.
When either option is enabled, preserve the original shared computation because
blocky-cloud compositing and terrain border fog need it. A compile-time candidate
switch supplies a zero-additional-work original path when disabled. If live mod
disable must restore the original placement, use the validated uniform enable
as a pass-coherent fallback predicate; record that runtime predicate separately
from the compile-time specialization. Do not set terrain atmosphere to an
approximation or switch off sky features.

Source-level work avoided per qualifying terrain pixel is four LUT reads and
six phase-area calls, with their otherwise unused preparation. Sky pixels keep
the original calculation. This is the highest-value initial candidate because
it affects the full-resolution terrain shading pass without lowering samples.
The driver may already sink some unused work into the original sky branch;
source counts therefore are not proof that four physical texture operations are
currently issued. Only a measured delta or suitable driver disassembly settles it.

Texture-sampling evidence: the LUT is bound from the existing raw
`image/atmosphere/scattering.dat` 3D RGB16F resource by
`shaders.properties: texture.deferred.depthtex0`. The function uses `texture`,
which has implicit LOD behavior. Pinned Iris 1.8.12 bytecode inspection of `GlTexture` establishes raw custom
texture setup: its constructor sets GL_TEXTURE_MIN_FILTER to GL_LINEAR or
GL_NEAREST (non-mipmap), GL_TEXTURE_MAX_LEVEL to zero, and MIN_LOD/MAX_LOD to
zero. `CustomTextureManager` creates GlTexture for raw custom textures. The LUT
therefore cannot select another mip at a varying sky/terrain boundary. This
resolves the implicit-derivative LOD concern on the pinned binding; retain the
check when porting to another loader/texture path. No new sampler is needed.

A small companion change is independently safe to try: move the existing exact
sky return in `program/d3_ao.fsh` immediately after its combined-depth fetch.
Currently it comes after one gbuffer fetch, two noise fetches, vanilla/DH depth
classification, view/scene reconstruction and previous-frame reprojection.
The sky outputs are constants. Moving that return avoids at least three
otherwise discarded fetches and those transforms on sky pixels; with DH active
it also avoids two classification depth fetches. Use combined depth, rather
than vanilla depth, so DH terrain never takes this return. Do not move or remove
any terrain AO or temporal rejection work. Benchmark this companion separately
before combining it with atmosphere placement.

Simpler control: a compile-time atmosphere-placement switch alone, with all
LOD sampling disabled and unchanged geometry. Keep `BORDER_FOG=false` and
blocky clouds off in both arms. It isolates an algorithmic cleanup from the
existing distance-quality changes. Add the AO return as a second isolated arm.

Runtime gate: actual Iris compile/render with DH present and absent; identical
sky and tree silhouettes during motion/TAA; sunrise, night, border fog on and
blocky clouds on fallback checks. Repeated original/patched-disabled/candidate
whole-render timings must improve beyond paired variation before adoption.
A source-only saving with indistinguishable timings is a failed performance
hypothesis, even if the cleanup remains useful.

## 2. Read filtered cloud color only when compositing can consume it

Exact targets: `program/c1_blend_layers.fsh` around `// Blend clouds in front of
translucents`, plus the terrain branch of `d4_deferred_shading.fsh`, and
`include/sky/clouds/sampling.glsl:read_clouds_and_aurora`.

`c1` calls the helper on every Overworld pixel, then consumes the returned color
only inside `if (is_translucent || is_translucent_lod)`. The helper performs a
pixel-age `texelFetch`, apparent-distance `textureGather`, filtered cloud-history
`textureLod`, and an extra ambient-scattering `texture` during lightning.
For opaque pixels the result is discarded. Move the eligible color work behind
that existing exact predicate. Preserve both vanilla and DH translucency flags;
vanilla front/back depth equality alone cannot exclude distant water.

Conservative first implementation: keep the apparent-distance gather outside
varying control flow, then defer pixel-age fetch and the explicit-LOD cloud
color read. This avoids two logical texture calls per opaque `c1` pixel. Keep
any implicit-derivative lightning lookup outside the branch until the existing
colortex12 sampler/mip state is proven, or carry its original gradients. Moving
the entire helper can avoid three calls but needs that derivative proof.
Cloud history in colortex11 deliberately has mipmaps; preserve its exact
calculated explicit LOD rather than forcing zero.

For deferred terrain, gather apparent distance before reading color. If its
squared distance is no smaller than the existing squared terrain view distance,
the terrain cloud blend is skipped already. Defer pixel age/color until that
predicate succeeds. Terrain rainbow code also reads cloud alpha: use the same
original rainbow maximum distance when skipping, rather than blindly replacing
its arguments. With finite cloud distance behind finite terrain, the existing
`min(view_distance, mix(cloud_distance, 1e6, ...))` equals view distance provided
both mix endpoints exceed it. Preserve the original path outside that proven
domain and when blocky clouds are enabled. Do not assume extreme custom render
radii are below the 1e6 sentinel without checking.

The simpler control is `c1` alone, excluding deferred/rainbow changes. Original
and candidate retain identical clouds, TAA history, lightning, fog, translucency
and geometry. At opaque pixels the helper's result has no consumer; this is work
avoidance rather than temporal-history reuse or another cloud-quality preset.

Runtime gate: fixed forest/coastal plus glass, vanilla water, DH water, rain and
lightning; moving camera and disocclusions; compare paired whole-render times
and pixel results, including colortex11 mips and colortex12 data behavior. A
driver may already predicate unused color reads, so do not claim operation
counts translate directly into GPU milliseconds.

## 3. Fog shadow-domain specialization and exact empty-ray returns

Exact target: `include/fog/overworld/raymarched.glsl:raymarch_air_fog`.
With Mac colored light shafts off, each fog step currently performs one
`shadowtex1` texel fetch even when distorted shadow coordinates lie outside
`[0,1]^3`. The comparison multiplies the tested depth by the in-domain boolean.
Outside the domain, intended lighting is fully lit; fog density, atmospheric
extinction and accumulated scattering still matter and must remain original.
DH extends fog rays far beyond the normal shadow-map footprint.

Proposed algorithm: determine the shadow-domain predicate before fetching.
Inside, perform the exact original depth lookup/comparison. Outside, use the
existing fully-lit result and skip the lookup. For colored shafts, preserve its
original explicit fully-lit outside-domain fallback, avoiding three discarded
lookups there. This retains sampling inside the map and retains the entire fog
integration; it does not remove distant tree shadows from surface shading.
The lookup guard can be coherent across distant regions, but divergence and
compiler predication still need measurement.

Caution: the current non-colored branch can form an out-of-range integer texel
coordinate before `texelFetch`. That is not a well-defined source of arbitrary
shadow depth. The intended result is one when valid depth values are nonnegative;
record the change as restoring the explicit outside-map fallback, and validate
boundaries rather than promising bit identity against undefined fetch behavior.
At coordinate exactly one, integer coordinates may equal the texture size;
include a careful original-boundary compatibility decision and tests.

After ray clipping, an exactly zero ray length can return
`mat2x3(vec3(0), vec3(1))` before constructing/marching its minimum step budget.
Place a zero-length-input guard before normalization as well to avoid undefined
inverse length. This is a correctness/empty-volume cleanup, not permission to
return identity for merely thin fog or small scattering. Do not use an epsilon
that skips visible light shafts. Ordinary forest cameras inside the fog volume
may rarely hit this path, so it is unlikely to explain a large gain alone.

Simpler control: shadow-domain guard only, same fog step count and protected
lighting, no early termination or distance budget changes. Inspect elevated
DH views, map edges, colored-shaft compatibility and temporal motion. Keep fog
at the original 0.50 resolution. Accept only a repeatable improvement with the
same visible scattering/transmittance and stable shadow boundaries.

## Boundaries and rejected shortcuts

These candidates require no GPU readback, added sampler, new render target or
runtime global quality cut. Local per-pixel depth predicates already exist;
there is no need to claim GPU-wide tile classification on the Mac GL4.1 path.
Do not reuse cached sky values as if they were current camera atmosphere, drop
AO, shorten fog extent, or skip cloud temporal history merely because present
terrain covers one pixel: d2 upscaling already handles coverage/disocclusion,
including a combined-depth output needed later for DH. More aggressive temporal
cloud/water reuse needs a separate history-validity proof and is not among
these recommended initial candidates.

Candidate 1 was implemented only after the coordinator explicitly paused
the first matrix and authorized it. Candidates 2/3 start only after the next
frozen matrix ends. Each candidate needs
its own literal pinned patch, parser/GLSL checks, actual driver rendering,
original-quality control and paired measurements. Keep the disabled path
original and cheap. None of this audit assigns pass milliseconds or claims the
new sustained ordinary-scene performance gate has been met.


## Candidate 1 implementation checkpoint

The explicit pinned patch replaces only d4's atmosphere assignment with a
`shaderlod_needs_atmosphere(depth)` guarded call. Its original arguments are
unchanged. The helper returns true for sky and whenever ShaderLOD is disabled;
with BORDER_FOG or BLOCKY_CLOUDS defined it compiles to unconditional true.
Thus protected normal/shadow/AO, cloud history, terrain fog and resource bindings
are untouched. This is distance-independent structural work avoidance and must
be described that way, rather than counted as an additional distance effect.
The AO early-return companion was not implemented.

The C++ helper fixtures execute the actual guard body and cover all four
border-fog/blocky-cloud combinations, enabled and bridge-disabled terrain,
and sky. The GLSL matrix adds an active Mac-like no-border/no-blocky variant
and a blocky-cloud fallback variant alongside default border-fog, standalone,
disabled, all dimensions and DH programs. Existing gradient and bridge tests
remain included. Runtime quality and performance are still unverified.

Static result: combined discovery passed 20 tests in 54.672 seconds, including
11 shaderpack tests and 792 GLSL compilations. Raw output is retained outside Git
at `dev/logs/photon-atmosphere-static-tests.log`. This does not confirm driver
compilation or a gain.
