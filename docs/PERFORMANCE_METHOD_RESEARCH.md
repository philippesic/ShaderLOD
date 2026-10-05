# Focused Photon performance-method research

Research checkpoint: 2026-10-05. Owner: fresh performance chat's bounded
`optimization_research` agent. Only this document was written. Pinned source
inspection and primary web research were performed; no build, driver compilation,
client launch, GPU run or new image comparison was performed.

The next recommended experiment is **cloud color reads behind the existing
translucency predicate**, isolated to composite1. It has a small implementation
surface and preserves the intended visible result. Its benefit remains unknown.
The rank below reflects experiment cost and fidelity risk, not measured pass
cost. The first three entries revisit unimplemented ideas already recorded in
[PHOTON_NEXT_OPTIMIZATIONS.md](PHOTON_NEXT_OPTIMIZATIONS.md); they are not new
runtime findings. Entries four and five add bounded research directions.

## Scope, assumptions and provenance

Target: Minecraft 1.21.1, NeoForge 21.1.253, Java 21, Iris 1.8.12, Sodium
0.6.13, Photon v1.3b and optional DH 3.3.3, on the Apple M3 Max OpenGL 4.1
runtime. Exact artifact URLs, version IDs and SHA-512 hashes remain in
[runtime.lock.json](../dev/runtime.lock.json). This report inspected the original
`dev/upstream/photon_v1.3b.zip`, SHA-256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Line anchors below refer to members of that archive, unless labeled local.
They must be rechecked against a candidate produced by the protocol owner.

Source-member SHA-256 fingerprints:

| Archive member under `shaders/` | SHA-256 |
| --- | --- |
| `program/c1_blend_layers.fsh` | `9a22e67280a57927c9dc8ca7ef194abb64b636de6a736651aa5b743035052cad` |
| `include/sky/clouds/sampling.glsl` | `66ce14d260db584e36dfc028a09a6234251713f0882718eb64a1f4548f6607d2` |
| `include/fog/overworld/raymarched.glsl` | `242cf128c4b18b6fd7ac080806de83fcd766676837e929f32fe4f7c613547ea5` |
| `include/misc/raytracer.glsl` | `10aecc98cec4db7828787423000730bb2b16b06bd4730fd72fe72cb46c0d6aba` |
| `include/sky/clouds/cumulus.glsl` | `1923542cd9ad862a6166a542dbdcb1597fd70316e4338b236deefc2ccb932ba5` |

The inspected local `shaderpacks/photon/shaders/include/shaderlod/lod.glsl`
fingerprint was
`2ac35e39e408d953e2d40769db4302aeff2fadd8e5884c47cfc757fa7d294b7f`.
It belongs to the original protocol chat; this report does not authorize edits.

Assumptions for the first pilots: current reduced Mac profile, original protected
surface lighting, unchanged terrain radius, matched wetness/world age and real
advancing frameCounter. Default clouds already use temporal upscale 4 and fog
uses half scale in each dimension. Full desktop POM/voxel/Ultra and DH+SSR remain
unvalidated here. Research citations establish methods, not compatibility with
this pack or hardware. The handoff records no accepted repeatable meaningful
ordinary-scene improvement; CPU acceptance caches remain off.

## 1. Consume cloud color only on eligible translucent pixels

**Observed source:** `program/c1_blend_layers.fsh:430–442` reads
`read_clouds_and_aurora` before checking `is_translucent || is_translucent_lod`.
Only that branch consumes the color. The helper at
`include/sky/clouds/sampling.glsl:5–24` fetches pixel age, gathers apparent
distance, reads filtered color with explicit LOD, and conditionally samples
lightning scattering.

**Experiment:** keep apparent-distance gather and the lightning implicit-LOD
sample in their original control-flow context initially. Defer pixel-age
`texelFetch` and cloud-color `textureLod` until the existing translucency
predicate succeeds. Preserve the original filter LOD, returned alpha/color,
refracted coordinates and both vanilla/DH flags. Do not change deferred terrain
clouds or rainbow behavior in this first arm.

**Work potentially avoided:** two logical texture operations plus filter-age
arithmetic on opaque composite1 pixels. This is distance-independent consumer
dispatch, not another cloud-distance budget. It can help the adapter's total
cost, but does not by itself prove shader LOD.

**Risks:** the compiler may already avoid discarded reads; a new varying branch
may add divergence. GLSL 4.10 §3.8.2 and §8.9 constrain non-uniform control flow
and implicit derivatives. Moving implicit-LOD operations requires verified
sampler state or gradients computed before divergence; a clean compile alone
does not establish correctness. [Khronos GLSL 4.10 specification](https://registry.khronos.org/OpenGL/specs/gl/GLSLangSpec.4.10.pdf).

**Acceptance:** static output/consumer inspection, then actual Iris rendering
with glass, ordinary water, DH water, lightning and overlapping near clouds.
Inspect motion/disocclusion and history resets. Require unchanged visible
compositing and a repeated composite1/whole-render improvement; if timing is
flat, retain the negative result and stop extending the guard.

## 2. Skip fog shadow reads outside the shadow-map domain

**Observed source:** `include/fog/overworld/raymarched.glsl:113–165` evaluates
distorted shadow coordinates and reads shadow depth every fog step. The colored
path reads two depths and shadow color before its outside-domain fallback;
the non-colored path reads one depth before multiplying comparison depth by an
in-domain flag. Fog density, extinction and scattering remain meaningful there.

**Experiment:** place only the shadow texture reads behind a valid-domain
predicate and use the intended fully-lit fog-shadow value outside it. Preserve
all in-domain lookup math and all density/integration steps. Test this alone,
with the existing original step count, before any fog sample adaptation.

**Work potentially avoided:** one shadow fetch per outside-domain sample on
the current non-colored profile, or three on colored-shaft variants. Far DH
rays may offer more qualifying samples; that coverage is a prediction requiring
a diagnostic capture. Surface shadow filtering is unchanged.

**Risks:** out-of-range `texelFetch` cannot establish reference parity. At a
normalized coordinate of exactly one, the integer texel can equal texture size.
The coordinator must choose and document a half-open valid texel domain or an
explicit edge rule. This is a correctness-sensitive boundary change, not a
promise of bit identity to invalid original reads. Domain divergence and
per-step predicates can cost more than the saved reads.

Spatially coherent conditions are preferable to random neighboring branches in
the general SIMD model. GPU Gems 2 §34.1/§34.2.4 explains that mechanism on old
NVIDIA hardware; it does not quantify M3 behavior. Apple's current M3 Metal
heat-map documentation independently separates control-flow divergence from
instruction cost. [GPU Flow-Control Idioms](https://developer.nvidia.com/gpugems/gpugems2/part-iv-general-purpose-computation-gpus-primer/chapter-34-gpu-flow-control-idioms),
[Apple performance heat maps](https://developer.apple.com/documentation/xcode/analyzing-apple-gpu-performance-using-performance-heatmaps-a17-m3).

**Acceptance:** original and guarded arms with matched fog counts, normal and
elevated cameras, shadow-map edges, sunrise/night, rain, colored shafts, DH
present/absent and moving camera. Compare fog transmittance/scattering and
map-edge continuity separately from GPU time. Reject visible light-shaft loss.

## 3. Dispatch constant sky AO output immediately after combined depth

**Observed source:** `program/d3_ao.fsh:95–134` reads combined depth, then reads
Gbuffer and two noise texels, optionally classifies vanilla/DH depths, reconstructs
positions and reprojects history. For `depth == 1.0` it writes constant ambient
and history-data outputs and returns. None of that preceding work is consumed
by the sky output.

**Experiment:** move the exact existing sky output/return directly after
combined-depth fetch. Retain the terrain AO algorithm, original constants and
hand logic. Never replace combined depth with vanilla depth; DH terrain must
reach the terrain branch.

**Work potentially avoided:** three logical fetches and reconstruction/history
arithmetic per sky pixel; two additional classification fetches with DH enabled.
This is another structural dispatch cleanup rather than distance-quality LOD.
AO is already half resolution, limiting its likely whole-frame contribution.

**Risks:** driver optimization may already sink the reads; early exits affect
derivative availability for surviving fragments. Audit all remaining AO/history
reads before moving the return. Do not assume output parity merely because sky
constants match. The GLSL specification above is the relevant language boundary.

**Acceptance:** actual Iris render in all supported dimensions, DH absent/present,
trees against sky, hand/sky edges and moving disocclusions. Check untouched
terrain AO and stable history, then repeat deferred3 and whole-render timing.
Reject a flat result as a performance hypothesis even if source cleanup is sound.

## 4. Test whether transition noise destroys sample-budget coherence

**Observed source:** local `lod.glsl:88–123` rotates per-pixel interleaved gradient
noise and uses it to select tiers within transition bands. Far/near stable
regions already bypass noise in `shaderlod_quality_at`. Cloud budgets can use
march noise, so tier selection and primary ray jitter may currently share an
input. Upstream cumulus primary steps vary with ray direction, density skips
lighting, and transmittance terminates the march: uniform tier selection alone
does not imply uniform execution.

**Experiment:** cloud-only diagnostic prototype, original ray jitter unchanged.
Separate tier-selection noise from ray-sample jitter. Compare original per-pixel
tier noise with a noise value shared by each 2×2 group of cloud-pass texels,
keeping per-pixel distance tests, probability thresholds, budgets and temporal
sequence advancing. Start with 2×2 only; no new classify pass, subgroup
instructions or persistent history cache. First show that transition bands
occupy enough pixels to matter. Near/far constant regions are the control.

**Work potentially avoided:** no guaranteed reduction in selected scalar samples.
The hypothesis is reduced mixed-budget execution within SIMD groups and less
inactive-lane work in transition regions. Group layout is unknown on the
OpenGL driver; 2×2 image groups do not prove SIMD alignment.

**Risks:** shared randomness preserves marginal tier probabilities only under
a suitable sequence; it changes spatial correlation and can create visible
blocks or low-frequency flicker. Screen-fixed groups can swim under camera
motion. Spatiotemporal blue-noise research demonstrates why spatial and temporal
error spectra matter; it supports the quality test, not this coarse-noise
optimization. [Wolfe et al., Scalar Spatiotemporal Blue Noise Masks](https://arxiv.org/abs/2112.09629),
especially the spatial/temporal analysis and volumetric application.

**Acceptance:** offline tier distribution and budget fixtures over distances and
frame phases; debug transition coverage in a separate unmeasured run; real
cloud jitter/history unchanged. Inspect slow camera motion, horizon pans,
camera translation and cloud/weather motion at ordinary upscale 4 and premium
upscale 1. Require stable cloud structure without blocks/rings, then paired
cloud-pass and whole-render gains. Do not report an offline lane model as M3
performance evidence. Preserve this as default-off if any visual cost is found.

## 5. Replace fixed SSR stepping with bounded pixel-space DDA research

**Observed source:** `include/misc/raytracer.glsl:31–78` normalizes a screen-space
direction, computes a boundary length, divides it by intersection step count,
and fetches one depth per step until hit/exit. Refinement fetches occur at
`:88–105`. A fixed count can revisit texels on short projected rays or skip
thin geometry on long rays. The current source does not record their frequency.

**Experiment:** first record/replay projected rays and integer depth-fetch
coordinates without changing the rendering algorithm. If duplicate visits are
common, prototype a separate Photon-only raytracer using perspective-correct
pixel-space DDA with original fallback color/composition. Start DH absent,
stride 1, bounded original maximum work and careful depth/thickness conversion.
Only after its quality/cost is understood test a modest larger stride on far,
rough surfaces, keeping glossy water conservative.

McGuire and Mara §3/Listings 2–4 describe contiguous pixel traversal, depth
interval tests, clipping and stride/jitter controls. They recommend coarse
stride for rough surfaces and explicitly show its artifacts. Their 2014 GPU
timings cannot predict this host. [Efficient GPU Screen-Space Ray Tracing](https://jcgt.org/published/0003/04/04/paper.pdf).

**Work potentially avoided:** redundant depth reads on short projected rays;
later coarse stride can reduce visits on qualifying far/rough surfaces. DDA can
instead visit more pixels than Photon's original budget, and requires extra
setup/interval math. Never claim fewer steps from its name alone.

**Risks:** changed hit semantics/thickness, self-intersections, hand clipping,
thin reflections, grazing water and screen edges. A camera-distance threshold
alone does not establish reflection-error tolerance. DH depth/projection
selection is a separate extension, and current DH+SSR sampler limits remain.
This experiment must not become a sampler-allocation pass split implicitly.

**Acceptance:** synthetic depth/ray fixtures plus actual no-DH SSR rendering;
near glossy water, far rough metals, thin poles, screen-edge rays, sunlit/night
and moving reflections. Compare hit masks and visible reflections against both
original SSR and a higher-sample reference, then repeated GPU/frame costs.
Promote distance stride only if motion quality survives and it beats a simple
existing lower-SSR-quality control at comparable fidelity.

## Measurement and stopping rules

The project's asynchronous elapsed queries measure a completion interval, not
isolated ALU occupancy. CPU command starvation, driver scheduling and tiling
can affect it. Khronos ARB_timer_query revision 13 §5.4, Issues 5/8/10 and its
example discussion specify completion timing, variance, tile-order caveats and
the cost of waiting for same-frame results. Keep delayed nonblocking reads;
do not add glFinish or nested elapsed queries. [ARB_timer_query specification](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_timer_query.txt).

Apple documents source-level cost/divergence tools for **Metal** on M3. No
successful Metal capture of this Iris/OpenGL path was established by this
research. Do not make direct Metal tooling a prerequisite, or label existing
elapsed timers hardware shader-occupancy counters.

For each arm, use one GPU client, identical window/resolution/pacing and shader
inputs, warmed chunks/histories, exact archive hashes/effective options, and
separate quality captures. Compare pinned original, candidate disabled and
candidate active without changing optional-mod stack or terrain radius. Start
with a small balanced repeated pilot before a long matrix. Existing attribution
scopes may overlap; do not sum their medians as total frame cost.

Prefer same-JVM controls where a compile-time switch is unnecessary, and retain
balanced serial archive controls to expose reload/JVM drift. Runtime reload
may reset histories, so warm up each arm. Match measured wetness, world age and
biome inputs; leave actual frameCounter advancing. Static animation/wetness
diagnostics must remain explicitly identified and followed by normal-motion
quality checks. See [GPU_TIMING.md](GPU_TIMING.md) and
[IN_PROCESS_RESULTS.md](IN_PROCESS_RESULTS.md).

First accept driver compilation/rendering, then visible parity or an explicit
quality budget, then repeatable affected-pass and whole-render improvement.
A pass gain with unchanged total frame time is effect evidence with a practical
bottleneck limit. It is not the shared project's ordinary-scene ≥15% acceptance
gate. Require its ordinary-scene plus second-workload evidence independently.
The compiler can eliminate apparent source work, so logical fetch counts are
upper-bound hypotheses rather than issued GPU operation counts.

## Returned decisions and practical next step

The coordinator should request a concrete shader-file handoff from the protocol
owner for the isolated composite1 cloud guard, or assign its implementation to
that owner. This research agent retains no shader/GPU ownership. Compile/render
that arm with original sample budgets, then run matched forest and coastal pilots
and inspect glass/DH water/lightning before extending it into deferred terrain.

Fog-domain edge behavior needs an explicit correctness decision. Coarse tier
noise and SSR traversal need their own exploratory quality tolerance, not a
silent change to protected defaults. Stop the three structural candidates after
flat qualified pilots; prioritize the remaining experiments only when actual
transition coverage or duplicate SSR texel visits supports the premise.
