# General LOD opportunities and exact AO reuse probe

Source freeze: 2026-10-05. Active goal:
[General LOD performance](goals/general-lod-50-percent.md).

The first bounded implementation reuses consecutive depth fetches in an ambient
occlusion horizon march. It preserves every upstream ray position, radius, sample
count, lighting contribution and temporal seed. This is a transferable adjacent
rendering optimization; Photon is its explicit pinned reference consumer. It is
an experiment with unmeasured GPU benefit, not a demonstrated LOD gain or a claim
that AO reuse can deliver the 1.5x total-throughput target.

## Provenance and current boundaries

Inspected local `dev/upstream/photon_v1.3b.zip`, the original Photon v1.3b by
Benjamin Stott (SixthSurge), version ID `gUv7fBPN`, selected by
`dev/runtime.lock.json`. Archive SHA-256:
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
The original license SHA-256 is
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
Preserve that license and [Photon attribution](../patches/photon/ATTRIBUTION.md)
for every derivative. ShaderLOD's MIT license does not replace Photon's terms.

Inspected `scripts/build_shaderpack.py`, the current explicit Photon manifest,
existing LOD helper, previous goal checkpoint, STATUS and EVALUATION. Existing
shared/helper/core Photon files remain owned by other work. The new probe derives
directly from original Photon; it does not apply the existing multi-effect patch,
change its protected lighting defaults or overlay unconnected common helpers.

| Original archive member | SHA-256 | Relevant entry points/lines |
| --- | --- | --- |
| `shaders/include/lighting/ao/gtao.glsl` | `0889647aa827357ba9e5101431b4294299a078055f7f3537468a94fb6430b0d3` | `compute_maximum_horizon_angle`, 15–71; `compute_gtao`, 73–163 |
| `shaders/program/d3_ao.fsh` | `5a47a1a64788105b5d197db8bc1a9aff1cdf17727c3c1804c01573c76863c266` | GTAO include 80–82; half linear render scale 84; AO invocation 167–174; temporal reconstruction 177–246 |
| `shaders/program/d4_deferred_shading.fsh` | `212081e707c20c78e17cbfd3380b2f22eb696cc1aa14bdf006c07d59f53f67e0` | Four AO/depth taps 317–333; depth-aware upscale 414–453 |
| `shaders/settings.glsl` | `93401d268fe859cc1df81929ad41156757a98db80f80cf32af80d9cdca96c394` | Existing POM cutoff 399; TAAU options 470–473; new private option anchored at settings guard |
| `shaders/shaders.properties` | `f8cc8df60f930612e8817e4176a34886c1a9de778c15427a5468ecbc4f71ee9a` | AO/history/fog buffer linear scales 255–257; cloud temporal scales 263–274 |
| `shaders/program/gbuffers_all_solid.fsh` | `9ff3f873baf0d651b15aad100af3275b1a745d11fe6aeb37aad6aa94b97285da` | POM cutoff 239–246; unconditional explicit gradients 248–249; normal/specular reads before alpha rejection 285–305 |
| `shaders/include/surface/parallax.glsl` | `5957b6101095bdb3abe7b5864915d5d636a9eb826602c018e3face47cdc81033` | Explicit `textureGrad` 12–15; faded POM march 29–60 |

The Overworld entry is `shaders/world0/deferred3.fsh`, which includes
`shaders/program/d3_ao.fsh` under `PROGRAM_DEFERRED3`/`WORLD_OVERWORLD`.
Nether/End wrappers select the same program. The runtime must prove `SHADER_AO`
actually selects GTAO before interpreting any result; SSAO/NONE are unchanged.

## Ranked opportunities

| Rank | Transferable mechanism and concrete source | Cost hypothesis | Quality risk and acceptance boundary |
| --- | --- | --- | --- |
| 1; implemented source experiment | Consecutive immutable depth-texel reuse in GTAO horizon march, `gtao.glsl` 39–68 | Projected subpixel ray steps can reread the same integer texel. Avoid the fetch itself before execution. Hardware texture caching already helps, so extra branches/registers can make this flat or slower. | Preserve the complete fractional-coordinate horizon calculation; prove original/off/on rendering parity and GPU timing. No approximate sample removal. |
| 2; held | Projected-footprint-bounded horizon budgets, `gtao.glsl` 24–39 and 103–138 | Use the projected support in depth-buffer texels to bound oversampling while retaining the full world-space radius. Distance alone is a weak proxy across FOV, resolution, direction and DH projection. | Repeated depth texels still have different reconstructed positions. A budget change alters horizon extrema and temporal noise. Preserve bent normals/ambient SSS; keep current protection/default full budget; require image/motion evidence before assigning implementation. |
| 3; held | Per-effect AO resolution with depth-aware reconstruction, `d3_ao.fsh` 84–89, 222; properties 255/257; `d4_deferred_shading.fsh` 317–333 | Retain the entire lighting algorithm but evaluate it at fewer fragments. Linear scale 0.5→0.375 gives 56.25% of AO pixels, a geometric count only, not a predicted GPU or frame-time gain. | Update AO/history buffer dimensions and all matching coordinate/reprojection consumers together. Thin foliage, disocclusion and normals may smear. Shared fog buffer 7 must remain independently scoped; global TAAU/resolution changes are distinct controls. |
| 4; held | Provably unused AO sky-path work, `d3_ao.fsh` 95–134 | Return immediately after the combined-depth fetch on sky, before gbuffer/noise/DH transforms and reprojection. Output ambient/history constants already match upstream. | Driver may already eliminate unused work. Combined depth already accounts for DH; prove the exact sky predicate for all variants. This is likely a small saving and should follow measured pass attribution. |
| 5; deferred | Projected material footprint and transparent-pixel work, `gbuffers_all_solid.fsh` 239–305 | Reduce expensive material work when its projected detail is subpixel; avoid normal/specular work on alpha-rejected fragments. | Ordinary profiles may lack POM/material maps. POM already stops at 32 blocks. Moving implicit texture samples behind alpha/discard risks undefined derivatives; explicit gradients must be captured before divergent control flow and sampling equivalence established. No such reorder is implemented. |

Cloud temporal resolution is already an explicit Photon mechanism, not a missing
general abstraction. SSR, cached sky, cloud literal-secondary kernels and analytic
water remain historical experiments. Do not extend their failed hypotheses as the
next implementation. Renderer terrain-distance reduction changed visible structure
and is outside this source probe.

## Why prior defaults did not establish the goal

Historical global OFF/ON same-JVM controls showed approximately 6–11% GPU reductions
in some native workloads, while the original/disabled/enabled five-effect pilot
regressed active GPU time by 6.76% forest and 0.82% coast. These have different
controls and must not be combined into an accepted original-versus-active gain.
Reduced far SSR controls changed sign or were flat, and a bright water band exposed
quality problems. Cloud consumer/extent/literal probes supplied no useful
incremental gain. These are recorded results; they do not prove a single cause.

Potential explanations needing measurements include work in protected lighting
remaining at upstream cost, sparse effect coverage, texture-cache reuse, branch
divergence and savings being dominated by unchanged passes. The matched-source
coastal pass-timed versus frame-only pair (23.8452 versus 14.8650 ms) raises an
instrumentation concern. One separate-JVM pair does not establish its cause or
absolute pass shares. Do not rank effect costs from that inflated profile.

The goal's target is at least 33.333% lower total frame time for 1.5x throughput.
If an effect occupies fraction `f` of GPU time and reduces its cost by fraction
`r`, its idealized GPU reduction is only `f*r`; CPU and other unchanged frame work
further constrain total-frame improvement. Neither fraction is measured for this
probe. Exact fetch reuse is the first correctness test, not a numerical promise.

## Frozen implementation and equivalence argument

Owned new files:

- `scripts/build_photon_ao_reuse_probe.py`
- `patches/photon-experiments/ao-depth-reuse-probe.json`
- `tests/test_photon_ao_reuse_probe.py`
- this document

Only upstream GTAO and settings members change; all other upstream payloads and
LICENSE remain identical. The builder checks the archive SHA-256, member hashes,
unique anchors, private source fingerprints, exact modification scope and safe
paths. It records original/disabled/enabled arm and hashes in the additional
`shaderlod-ao-depth-reuse-probe.json` archive member. It writes only ignored
`build/` ZIPs and cannot overwrite the original source archive.

Private option `SHADERLOD_AO_DEPTH_REUSE_PROBE` defaults off. With the option off,
selecting its preprocessor branches restores all original source bytes. With it
on, the uniform-coherent dispatcher calls the cache kernel only when
`shaderlodBridgeVersion == 2 && shaderlodEnabled == 1`. Absent/unsupported bridge,
global OFF, and malformed enable values call the retained original kernel. Only
these two existing fields are consumed; new per-effect API/transport is not
connected to this probe. No per-effect ratio, preset, near/far or debug consumption
is inferred from uniform declarations.

The original `compute_maximum_horizon_angle` remains byte-for-byte intact. The
cache kernel duplicates it and changes only the depth-fetch site plus cache state:

1. Convert the original clamped ray position to the original integer texel.
2. Reuse the previous scalar depth only when that texel is in texture bounds and
   matches the immediately previous valid texel.
3. Otherwise execute the original `texelFetch` inside the miss branch. Invalid
   texels always fetch and clear validity: upstream out-of-range results are
   undefined, so their repeatability is not assumed.
4. Run every original early continue, fractional-coordinate reconstruction,
   falloff, cosine maximum and angle conversion without modification.

The AO pass reads combined depth but writes AO/history buffers 6/14. Under the
pinned source's read-only depth binding, repeated valid level-zero texel fetches
return the same value; sampler derivatives/filtering do not enter `texelFetch`.
Cache state is local to one horizon ray, cannot survive frames/reloads, and is
updated before early continue. Cache reuse never drops an iteration or substitutes
the previous fractional ray position. Driver compilation and output parity still
need runtime checks; source equivalence cannot guarantee driver optimization.

Distance remains upstream camera-relative view-space block units. This exact
reuse has no distance cutoff or transition band: projected sample spacing makes
it naturally relevant where texels repeat. Changing the global gate or projection
changes only reuse opportunities, with expected unchanged lighting output. No
frame-random selection or new dither is introduced. Unknown inputs preserve the
original kernel; undefined edge fetches preserve original behavior by bypassing
the cache. Quality fallback is full original lighting, never AO=1 or missing
shadows. The new effect-policy owner agrees that exact reuse is a separate
capability from quality-budget cuts, so lighting preservation may remain enabled.

## Completed static evidence and runtime handoff

Ran `python3 -m unittest discover -s tests -p 'test_photon_ao_reuse_probe.py' -v`:
11 source/math fixtures passed. They read the pinned original archive without
assembling output, check byte-exact original/default-off branches, normalize the
active kernel back to original math, enforce member/option/license pins, reject
mutated source/manifests, and exercise valid repeats, changing undefined edge
fetches and 400 randomized projected rays. A counterexample proves that same
depth texel does not imply redundant fractional-coordinate horizon arithmetic.
These are static/scalar evidence; no GLSL compiler, game, image or GPU check ran.

Root/runtime operator owns assembly, shader reloads and the GPU. After source
review, actual build commands are:

```sh
python3 scripts/build_photon_ao_reuse_probe.py --arm original
python3 scripts/build_photon_ao_reuse_probe.py --arm disabled
python3 scripts/build_photon_ao_reuse_probe.py --arm enabled
```

The enabled archive should support global OFF/ON controls without changing shader
source. Keep exact original and default-off archives as separate compiler and
mod-absent controls. Assemble once, freeze archive/member hashes, and record actual
option values and mod config. Confirm driver compilation/no fallback, in-world
GTAO rendering, linked bridge fields, uniform readback and settled live OFF/ON
behavior. No diagnostic counts were added; do not claim physical fetch reduction
from the host fixtures or a linked enable field alone.

Capture matched forest/coastal near/far, thin foliage, sky/depth edge, AO/bent-normal
boundaries and motion/temporal frames. Require unchanged lighting across global
switches and shader reloads. Test DH absent first; test real DH draws, boundary and
projection behavior separately. Inspect the pinned actual depth binding and its
dimensions if edge behavior differs.

Measure repeated balanced original/mod-absent, default-off and enabled global
OFF/ON frame-only GPU intervals with matched resolution, preset, scene and warmup;
record median/tail total-frame and GPU time, variability and order drift. The
existing M3 Max supports asynchronous elapsed queries, not GPU timestamps. A
separate limited GTAO-pass or capture measurement can investigate executed fetch
opportunities, but validate timing overhead before deriving cost fractions. Avoid
diagnostic/readback work in timed windows. Reject flat/regressive cache results;
move to the next evidence-ranked mechanism rather than promoting a parity-only
optimization or calling an unmeasured source-count saving a performance gain.
