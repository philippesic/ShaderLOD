# Miniature 2.19 runtime and refinement experiment

Source-only plan, 2026-10-05, owner `pack_research`. Only this document was
created for this task. No candidate lock change, downloads, adapter code,
compiler, build or client launch. Initial Miniature rendering, integration,
support and performance remain unverified by this worker.

## Exact artifact and baseline

Use the existing [publisher release](https://modrinth.com/shader/miniature-shader/version/LWmZ94RG):
`miniature-shader-2.19.zip`, 43,913 bytes, SHA256
`5f224e907657f3ffd95d37e37c27e555deef64fa56da1e13374f2c295241d7f0`.
Publisher SHA512 already verified in `dev/shader-candidates.lock.json`. Read
the extracted **release**, `dev/upstream/other-pack-research/miniature-release/`,
not current upstream source: pinned Git `bdb6e0020b4318d4e845902cbcc3e3552b0e9274`
is not byte-identical to this release.

First launch: original archive, Minecraft 1.21.1 / NeoForge 21.1.253 / pinned
Iris 1.8.12 and Sodium 0.6.13 / Java 21 / Apple M3 Max, **DH physically absent**.
Use the existing minimal-stack workload and a shoreline with visible near and
far water plus reflected trees. Preserve the archive, effective Iris option map,
loaded artifacts/versions, camera, detail radius, window/pacing, weather/wetness,
time, screenshots and driver errors. The pack has no DH shader programs; do not
treat a no-DH render as DH compatibility.

The original source defaults match the pack's NORMAL profile. Preserve all
defaults initially; explicitly select NORMAL only if the option loader needs
a profile value. Essential recorded values are:

| Option | Original/default control | SSR refinement-only lower-quality control |
| --- | --- | --- |
| `SSR_MAX_STEPS` | `10` | `10` |
| `SSR_STEP_SIZE` | `1.6` | `1.6` |
| `SSR_BINARY_STEPS` | `4` | **`2`** |
| `REFLECTIONS` | `10` | `10` |
| `BLOCK_REFLECTIONS` | `5` | `5` |
| `shadowMapResolution` | `1024` | `1024` |
| `shadowDistance` | `128.0` | `128.0` |
| `SHADOW_ENTITY` | `0` | `0` |
| `SHADOW_PIXEL` | `16` | `16` |
| `ENABLE_SHADOWS`, `FLAT_LIGHTING` | enabled | enabled |
| `WATER_WAVE_SIZE`, `WATER_WAVE_SPEED` | `1`, `2` | `1`, `2` |
| Fog, colors, brightness, remaining options | unchanged source defaults | identical effective map |

Use the same source/profile and override **only** `SSR_BINARY_STEPS=2` in the
lower-quality arm. HIGH changes shadows and outer SSR steps; LOW/LOWEST change
lighting/shadows/block reflections, so none isolates the proposed experiment.
`REFLECTIONS=0` is a possible separate visual diagnostic, but the source still
calls SSR before multiplying its contribution by that option; removal of GPU
work would depend on compiler elimination. Do not claim it explicitly skips SSR.

## Program and feature requirements

The Overworld wrapper `shaders/world0/final.fsh` L1 uses `#version 120` and
includes `/program/final.fsh`. Its matching vertex program uses `ftransform()`
and a texture-coordinate varying. This relies on Iris's compatibility shader
translation; it is not a raw macOS core-context shader claim.

The final fragment declares four distinct samplers:

- `colortex0`: scene color, also sampled at the reflected hit.
- `colortex6`: encoded normal.
- `colortex7`: reflectivity, roughness and validity marker.
- `depthtex0`: opaque scene depth used for reconstruction and tracing.

`program/textured_lit.fsh` L130 and `program/translucent.fsh` L69 write
`DRAWBUFFERS:067`. Terrain shading separately uses texture/lightmap/shadowtex1;
translucent vertex shading samples the lightmap. Attachment index 7 does not
mean final uses eight samplers. Actual Iris-generated bindings/target formats
must be inspected in the loaded pipeline; the four source sampler declarations
alone are not a driver-link result.

No compute files, image-load/store code, custom-image requirements, explicit
mipmap-enable directives or DH programs were found in this release.
`shaders.properties` L40 lists optional `BLOCK_EMISSION_ATTRIBUTE`; it is not
a declared mandatory feature. For Minecraft 1.21.1, the `<12111` guards at
L42–45 disable gbuffers_line programs. Leave those upstream guards intact.
Baseline uses `texture2D` and existing matrix/depth helpers; no new texture or
framebuffer is needed for the refinement proposal. These observations suggest
a small source feature footprint, not established Apple compatibility.

## SSR behavior that must be preserved

`program/final.fsh` L23 masks SSR by reflectivity and a validity marker;
L38 reconstructs view position and L48 calls `getReflectionColor`.
`common/getReflectionColor.fsh` L11 computes reflected direction, rejecting
`R.z >= -0.05`. Its outer loop L24 has ten source-default iterations, offscreen
exit L28, and depth fetch/reconstruction L31–35. After each non-hit, L60 adds:

`max(SSR_STEP_SIZE * abs(diffZ) * invR, 1.0)`

This increment depends on sampled depth and ray direction. A five-step kernel
with doubled increment is **not** extent-preserving, equivalent to ten steps,
or guaranteed to retain the same first-hit bracket. Do not introduce that cut.

On the first `diffZ < 0` hit, L38–39 brackets `oldPos` and `curPos`. Only when
`diffZ > -16.0` does L42 refine the bracket with four midpoint depth samples.
The final guard L53 can discard a hit based on refined depth; L55 samples scene
color at the final refined UV. Thus reducing refinement leaves the outer march
and first bracket unchanged, but can change reflected color, hit acceptance and
edges. It is a quality approximation, not an exact optimization.

## Small proposed distance experiment

Prefer **4 near / 2 far binary refinements**, preserving all outer iterations,
step sizes, cutoffs, masks, reflected direction, roughness noise and miss return.
Only eligible first hits within the existing cutoff can save work: at most two
depth fetches per such hit. Many pixels miss, exit early, fail the mask or skip
refinement already; full-frame savings may be negligible. No gain is predicted
as an accepted result.

Compute one radial distance from reconstructed `viewPos` in camera/view-space
block units. `common/transformations.glsl` L62–63 unprojects normalized depth;
`view2feet` adds the model-view inverse translation, while `view2eye` only
applies its rotation. Use **`length(viewPos)` consistently with the existing
core protocol's camera/view-space distance convention**, not raw depth,
`abs(viewPos.z)`, absolute world position or the reflected ray's final hit
distance. Check third-person, view-bobbing and projection behavior in runtime;
do not silently mix feet-based distance with camera-based thresholds.

Initial diagnostic bands could retain four refinements through 96 blocks and
reach two at 160 blocks. They are candidate options for inspecting transitions,
not recommended defaults. A broad transition can select a midpoint budget of
three as well. Discrete budgets can create moving rings even when color/shadow
budgets are untouched. A useful diagnostic alternative performs a fixed maximum
of four refinements while retaining the second/fourth outcomes and blending
them smoothly; that tests transition quality but **saves no refinement fetches**.
If that quality diagnostic fails, stop before benchmarking a cheap variant.

For a savings variant, prefer reviewed literal refinement kernels or a
well-bounded uniform maximum with early exit; test the generated shader rather
than assuming a dynamic loop compiles cheaply. Do not run both complete SSR
paths per pixel and label the blend an optimization. A world-stable budget
selector can spread a transition, but without a demonstrated temporal filter
it can create grain/edge crawling. No new per-frame random dithering is proposed.

Noise already comes from `common/math.glsl::random3` L9, applied at
`program/final.fsh` L45 to quantized `worldPos`. The quantization scale changes
with feet-position distance through 16 blocks (L41–42), and roughness zero leaves
water unperturbed. Preserve this behavior: adding time-dependent noise or
changing quantization would confound refinement quality and performance.

**Derivative limit:** existing `texture2D` calls occur inside nonuniform masks,
ray exits and hit branches. Adding distance-selected refinement introduces
another source of divergent texture access. Implicit derivatives in such
control flow cannot be used as a correctness argument. A modified active path
should use a verified explicit base-level depth/color sampling form, or provide
reviewed valid gradients. GLSL 1.20 fragment explicit-LOD syntax is not guaranteed
by the original version declaration; check pinned Iris translation/driver
support before adopting `texture2DLod`/`textureLod`. No mipmap-enable directive
was found, but verify actual allocated mip levels/filtering before asserting
base-level fetches reproduce existing sampling. Keep a preprocessed original
path for adapter-off; do not globally rewrite all pack samples in this experiment.

## Existing core protocol 2: proposal only

Current `src/main/.../mixin/CommonUniformsMixin.java` registers per-frame
`shaderlodBridgeVersion=2`, `shaderlodEnabled`, `shaderlodNear`, `shaderlodFar`,
`shaderlodTransition`, `shaderlodPreset`, and `shaderlodDebug` with Iris.
The Photon helper documents their distances as camera/view-space blocks.
Miniature does not declare or consume them now; uniform registration elsewhere
does not prove a connection to this pack.

A future pack-local opt-in consumer can declare these existing core fields
inside its adapter helper, accept **exactly version 2**, validate/sanitize its
bands, and use global enable plus generic distances for refinement tiers.
No Java or transport change is needed for that proposal. Keep the adapter
compile-time switch default off. With the mod absent, version zero/unknown, or
global enable false, select the untouched original function; test each case.
Do not apply zero-initialized absent-mod distances or silently interpret protocol
3 as protocol 2. Preset mappings, if added, must be documented explicitly;
the first kernel may ignore preset and use one budget pair.

`shaderlodDebug` could show a temporary band/status cue independent of SSR
visibility, helping verify live values even when there is no qualifying hit.
Use mod-enabled/disabled plus a changed distance threshold to demonstrate actual
pack consumption. Debug must be off during measurements. There is no protocol-2
per-effect enable or refinement-count field, so a Miniature-only shader option
still owns the effect switch/budgets. Do not describe that option as live mod
configuration until the later protocol owner supplies and verifies it.

## Pin anchors and attribution

All following SHA256 values are over the exact release's bytes, including CRLF:

| Exact release path | SHA256 |
| --- | --- |
| `shaders/shader.h` | `45996412988252ae8b9a00f7985234c80359fa4ba92ed78bdf04a07c68c4ae1a` |
| `shaders/shaders.properties` | `69ba499df39e90faa80cb1150c67f3d259ca1e6c0f25c55872fbc7991e5aa22b` |
| `shaders/world0/final.fsh` | `5ac992a581f02476aa67437c6da799ddbf2577efa43571e0a2dff25b3e4ebbb4` |
| `shaders/program/final.fsh` | `6f87f795107ab99f94ea3be70510bf0138b138329cc4e6fa8e9224f01d70f2dc` |
| `shaders/program/final.vsh` | `b98c8ab4ded43ae437db542963ebf2e644532f34f13ae0d71d9d080d0aea8ea1` |
| `shaders/common/getReflectionColor.fsh` | `a8db323fe976b4c4c9635a53d0d11d752dfa9140e06de411db11ffa8c759f481` |
| `shaders/common/transformations.glsl` | `99818a16438afdaf0193f86d68fd7c98012091ac6592959fc30642231595b0fb` |
| `shaders/common/math.glsl` | `c81d486dda419e420c5ee0c8947dc2a752f3e11310a0bc2791afcb98d31d53a4` |

The source MIT notice is **Copyright (c) 2022 Mateus A. Kreuch** in the pinned
[upstream LICENSE](https://github.com/mateuskreuch/minecraft-miniature-shader/blob/bdb6e0020b4318d4e845902cbcc3e3552b0e9274/LICENSE),
SHA256 `397d80246d261805b953865428e9c244e5a7bd2bbbb6f3b37f16e884e697e4d1`.
Publisher metadata also declares MIT. The 2.19 release ZIP contains no license;
a future derivative must retain/restore the full copyright and permission
notice, with attribution to Miniature and its author. The main mod license does
not excuse omitting the upstream notice. This task copied no source into tracked
adapter files.

## Acceptance and stopping boundaries

First accept only an **original-pack runtime smoke**: actual Iris compile/link,
visible terrain/water/reflections, no fallback, exact versions/hash/options,
and retained errors/captures. Then compare original defaults versus the single
global binary-refinement control before implementing distance logic.

Future adapter acceptance requires patched-off preserving original preprocessing,
near/full budget agreement, untouched outer hit brackets, actual driver rendering,
mod-absent/disabled/unknown-protocol behavior, and live threshold/debug proof.
Inspect a slow camera sweep and shoreline translation for edge loss, rings,
reflected thin geometry, grazing views, underwater/third-person behavior and
block/glass reflections. Compare original, original global-two-refinement,
patched-off and active in repeated serial captures with the same stack/scene.
Use a second ordinary scene before promoting performance. Retain all runs and
separate compilation, visual quality, protocol connection and timing evidence.

Unresolved decisions return to the coordinator: whether original 2.19 renders
on the locked Mac runtime; whether refinement-only savings justify an adapter;
whether a discrete transition meets quality expectations; and which explicit
LOD sampling form the pinned Iris pipeline supports. No support or gain follows
from this plan or the download/source checks.


## Original pack actually rendered

Rootsmoke99413 exit0/bothaccepted onlockedminimal1.21.1/NeoForge/Iris/Sodium
withoutDH,35swarm20scapture/GPU1/16,1488×836inspectedshoreline. OriginalNORMAL
179.21FPS/GPU4.567ms/thread5.288ms; binaryrefinement2control182.25/4.474/5.199.
RootPNGshowsbank/water reflections andshadows, fallbackfalse. Singleblock
nativelowerqualitycontrol, not distanceadaptergain or support/DHproof.
Rawdev/logs/miniature-original-smoke-r1 retainsallversions/options/archive/
class/jar/inputhashes, PNGs anddriverlogs. No derivativeor mainchange.
