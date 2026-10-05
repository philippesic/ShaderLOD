# B15 native-resolution effect source audit

2026-10-05. Read-only source/metadata audit by the fresh performance chat's
`optimization_research` worker. Only this report was written; no source edits,
compiler, client or GPU run was performed. Interactive measurements belong to
the coordinator. This report proposes conditional probes, not a causal diagnosis.

## Evidence boundary and exact input

The coordinator's native-window pilot used 2976×1672, minimal stack/DH present,
forest, protected lighting, cached sky off, detailed radius 24 and DH 128. Its
original / patched disabled / active means were 61.49 / 61.19 / 49.96 FPS,
15.090 / 15.211 / 19.146 ms GPU, and 9.084 / 9.163 / 9.261 ms render-thread time.
These are one-block observations, not proof of an implementation cause. Raw data:
`dev/logs/photon-native-window-pilot-r1`; context:
[PHOTON_NATIVE_RESOLUTION.md](PHOTON_NATIVE_RESOLUTION.md).

Inspected archives and SHA-256:

- B15 `build/photon-lighting-tier-no-atmosphere.zip`:
  `b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c`.
- Original `dev/upstream/photon_v1.3b.zip`:
  `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.

All source anchors below are archive-member line numbers. `U` means original,
`B15` means the actual derivative archive, not the currently edited main helper.
The source files remain protocol-owner property. B15's embedded
`shaderlod-build.json` identifies Photon v1.3b, protocol 2, its base SHA-512,
modified files and helper fingerprints. Other runtime artifacts are recorded
in the measurement exports: Minecraft 1.21.1 / NeoForge 21.1.253, Sodium
0.6.13, Iris's loaded label `1.8.12-snapshot+mc1.21.1-local`, DH 3.3.3, Apple
M3 Max/OpenGL 4.1. The recorded Iris artifact SHA-256 is
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`;
the loaded label alone is not an artifact identity.

Relevant B15 member SHA-256:

| Member under `shaders/include/` | SHA-256 |
| --- | --- |
| `shaderlod/lod.glsl` | `2ac35e39e408d953e2d40769db4302aeff2fadd8e5884c47cfc757fa7d294b7f` |
| `shaderlod/water_lod.glsl` | `d985339345a4273dd687ee6e8c6b330de925abead5404c3645c34ef6b8906e7a` |
| `shaderlod/water_height_static.glsl` | `0df0f5fcc3eaa6811a88d4c4f0e7fccf6412b299cc3c1578461e0d54abbea9d0` |
| `shaderlod/water_parallax_static.glsl` | `81ee952495a60f3b7c280aae190dbb6d2ef1fd29e3301ee41341f54762a6709c` |
| `sky/clouds/cumulus.glsl` | `a17f8fba86589b1b138063bbd3d1c261055f135b79ad4985579786abb1773d9c` |
| `sky/clouds/altocumulus.glsl` | `d13de78fb87924295a4dd90af455a1bfa86776a708e1b461e2a158636d550255` |
| `sky/clouds/cumulus_congestus.glsl` | `3e17ef9f8909861ec11a5d2bf8da7883333cb2816a3e1def2fed74d82d52f5b2` |
| `fog/overworld/raymarched.glsl` | `872f04ad3770c571fcfe67a074aa2bb55ea646714626af02d167f71d4d4a791a` |

The native adaptive measurement records PERFORMANCE, generic bands 32/96/8,
cloud bands 128/1024/64, cloud mode 0, temporal upscale 4, WATER_WAVE_ITERATIONS
3, water bands 96/256/32, WATER_PARALLAX true, analytic water false, VL true,
fog scale 0.50, colored shafts false, environment reflections false and fixed
shader wetness 0. LPV_VL=true alone does not enable its work: c0's LPV path
also requires COLORED_LIGHTS (`program/c0_vl.fsh:231–234`). No runtime count
telemetry, register count, issued-texture count or shader disassembly was read.

## Cloud source changes: smaller counts, different specialization opportunities

| Operation | Original source | B15 active source |
| --- | --- | --- |
| Cumulus primary | `uint(mix(H,Z,abs(ray_dir.y)))`, U `cumulus.glsl:220–221`; loop `:274` already variable | Same base, then three tier caps `:259–270`; primary loop `:319` remains variable |
| Altocumulus primary | Variable mix, U `altocumulus.glsl:222–223`; loop `:283` | Same base plus three tier caps `:261–272`; loop `:328` |
| Congestus primary | `const uint` option count U `cumulus_congestus.glsl:207`; loop `:275` | Mutable `uint` option count `:216`, tier-capped `:259–270`; loop `:320` |
| Secondary counts | Constant lighting/ambient call-site values; optical-depth formal parameter is `const uint` | Mutable lighting/ambient counts; formal `uint step_count, uint original_steps` |
| Secondary extent setup | Base length divided by count | Same base plus conditional count-ratio / geometric-series correction |
| Scattering approximation | Eight iterations per nonempty primary sample | Same fixed eight iterations |

Detailed secondary anchors:

- Cumulus: U `:110–132`, B15 `:112–141`; correction `:125–127`, loop `:134`;
  two calls per nonempty primary sample at B15 `:359–372`.
- Altocumulus: U `:114–137`, B15 `:116–146`; correction `:130–132`, loop
  `:139`; two calls at B15 `:361–374`.
- Congestus: U `:120–143`, B15 `:122–152`; correction `:136–138`, loop
  `:145`; two calls at B15 `:349–362`.

The `const` formal parameter is an immutability qualifier, not proof of
compile-time unrolling. Original actual call-site counts are compile-time pack
options, which makes constant propagation/specialization possible. B15's actual
counts depend on distance/noise/preset. Whether the driver specializes, unrolls,
or uses rolled loops remains unknown.

For each reduced secondary invocation the added source contains one inequality
branch, two `exp2` calls, two ratio divisions and scalar conversion/arithmetic,
in addition to the original base-length division. This occurs twice per
nonempty primary sample. One `exp2` input is an original pack constant and may
fold; the count expressions are ray-invariant and may hoist or simplify. Those
are source-expression counts, not a claim that four physical exponentials are
issued per primary sample.

The three `shaderlod_budget` calls are before the primary loop, not inside it.
They use `lod.glsl:79–85`: tier-zero return, preset lookup, nested scale selection,
ceil and min/max. Mode 0 compile-time selection excludes the alternative
overhead-geometry/mode-1/mode-2 block; functions present elsewhere in the ZIP do
not prove their execution. Cached-sky-off returns tier zero for its fixed-origin
path. B15 has not added another scattering loop or density texture per step.

For the recorded options, source-selected cap examples are:

| Layer / count | Original | PERFORMANCE medium | PERFORMANCE far |
| --- | ---: | ---: | ---: |
| Cumulus horizon / zenith primary | 40 / 20 | 20 / 10 | 10 / 5 |
| Cumulus lighting / ambient | 6 / 2 | 3 / 1 | 2 / 1 |
| Altocumulus horizon / zenith primary | 12 / 6 | 6 / 4 | 4 / 4 |
| Altocumulus lighting / ambient | 4 / 2 | 2 / 1 | 2 / 1 |
| Congestus primary | 20 | 10 | 5 |
| Congestus lighting / ambient | 6 / 2 | 3 / 1 | 2 / 1 |

Intermediate view directions use their original mixed base count before ceil/
minimum capping. For a particular ray with cap P, at most P primary density
calls and Q×(L+A) secondary density calls occur, with Q≤P the actual nonempty
primary samples before termination. The eight-iteration scattering loop runs
Q times. Q is not held constant by lowering P: sample positions, density hits,
secondary density values and opacity termination change. Thus budget reduction
does not establish the proportional reduction in executed work or equal quality.

**Conditional probes, only if isolated cloud evidence warrants them:**

1. Hoist secondary initial-step lengths outside the primary loop. Compute the
   existing B15 expression once for light and once for sky per cloud ray, then
   pass each exact result to the optical-depth loop. Preserve the original
   no-correction branch when counts match. This tests repeated setup, retaining
   counts, sample jitter, growth order and total extent. Hoisting may be redundant
   with compiler optimization and can extend live ranges/register pressure.
2. Separately dispatch literal secondary-count kernels for only the reachable
   option/preset counts. Select once per ray and retain the existing extent
   correction, rather than shortening rays. Keep the original full kernel for
   live disable/tier zero; compile-time CLOUDS-off must still select pinned
   original includes. This tests constant propagation, but code duplication can
   increase program size/register allocation and mixed-tier divergence. Do not
   clone entire primary marches or silently freeze the live preset.

Moving count/extent setup must not change density ordering, termination or
secondary support. CPU fixtures/source checks do not prove driver throughput.

## Fog source changes: most main march counts were already variable

| Fog path | Original shape | B15 change and anchors |
| --- | --- | --- |
| Overworld air | `uint` count from clipped ray length, max 25; U `:93–98`, loop `:113` | One tier/budget call at B15 `:100` before step setup; same march body `:117–169` and fixed final four-scatter loop `:203` |
| Underwater | `uint` length-based count, 16–25; U `water_fog_vl.glsl:44–46`, loop `:77` | One pre-loop cap B15 `:50`; loop `:81`; fixed multiple-scatter structure retained |
| End | `uint` length-based count, 16–25; U `end_fog_vl.glsl:108–110`, loop `:134` | One pre-loop cap B15 `:114`; loop `:138` |
| LPV | `const uint LPV_VL_STEPS` and constant geometric step normalization; U `lpv_fog.glsl:103,127,137` | Mutable count B15 `:106–107`, mutable initial scale `:131–133`, loop `:141` |

Only LPV introduces a constant-to-varying main march here. Its `pow(1.1,count)`
can lose constant folding and its added `distance` occurs before a separate
`length_normalize` of the same ray (`:106–115`). That duplicated length is a
source fact, but this forest's colored-light-disabled configuration does not
establish that LPV executes. Do not use LPV to explain this Overworld pilot.

Air fog adds no per-step helper calls or fetch expressions. At the recorded
PERFORMANCE preset, a possible original count 8–25 maps to medium 4–13 / far
3–7; near preserves it. The same clipped ray length is redistributed over those
steps. Each actual iteration retains shadow lookup, noisy density and exponential
transmittance math; changing positions changes sampled values. Air's tier uses
the same dither passed as ray-sample jitter (`c0_vl.fsh:168–185`), so stochastic
budget selection can correlate with sampled phase within transition bands.

**Conditional fog probe:** preserve the original length-derived count and exact
tier selection, but substitute a compile-time PERFORMANCE scale path for the
uniform-preset scale dispatcher in this one experiment. Do not change loop
counts, jitter or integral support. Compare with a dynamic-preset arm and
original FOG-off source. This tests helper/control shape, not a new fog algorithm;
a production adapter must preserve live preset changes and original disable.
Literal whole-march kernels for every reachable count would expand code heavily;
do not implement them without repeated fog-specific evidence. Noise-decoupling
is a separate quality experiment, not a parity-preserving fix.

## Water source changes: literal cheap loops already exist

B15 `surface/water_normal.glsl` differs from upstream only by two includes; the
original normal, height and parallax bodies remain unchanged. Static cheap
kernels are generated by `shaderlod/water_lod.glsl:7–120`. With original N=3,
analytic=false, the reachable cheaper height kernels are N=1 and N=2; no N=3
cheap normal is generated. All live presets remain selectable.

| Operation | Original | B15 cheap path |
| --- | --- | --- |
| Height noise array and fetch loop | Array size `WATER_WAVE_ITERATIONS`; literal option count, U `water_normal.glsl:77–82` | Array size and loop bound literal `SHADERLOD_WATER_N`, `water_height_static.glsl:32–36` |
| Gerstner wave accumulation | Literal option-count loop U `:103–118` | Literal cheap-count loop `water_height_static.glsl:58–73` |
| Normal finite difference | Three height calls U `:144–146` | Same three calls `water_height_static.glsl:138–141` when analytic is off |
| Parallax | Const spacing count 4 but **data-dependent while**, U `:174,189–194` | Const spacing 3/2/1 by preset, same **data-dependent while**, `water_parallax_static.glsl:11,26–30` |

For one finite normal: original N=3 has nine logical octave-noise fetches and
nine Gerstner evaluations; N=2 has six each, N=1 has three each. If height
variation is compiled, all three height calls still fetch that separate noise.
These are per-call source counts before compiler reuse, not physical instruction
counts. Parallax performs one initial height call plus one per executed while
iteration; its spacing parameter is not a guaranteed literal trip count.
Neither upstream nor this derivative makes that while a fixed for-loop.

Introduced dispatch work:

- Normal wrapper `water_lod.glsl:211–249`: camera distance, tier noise, quality
  branch, preset-to-octave mapping, then a count-dispatch chain to literal kernels.
- Parallax wrapper `:259–266`: the same distance/tier classification, original
  near path, far bypass, uniform preset dispatch to a literal kernel.
- Both are called for the same vanilla-water fragment at
  `program/gbuffers_all_translucent.fsh:468–484`; world position, pixel and frame
  are unchanged between calls even though parallax updates texture coordinates.
  DH water calls only the normal wrapper at `misc/distant_water.glsl:77`.

`lod.glsl:278–283` checks enable then computes noise before its tier helper;
unlike the generic quality helper at `:113–120`, water has no source early
near/far fast path before noise generation. The tier helper itself still returns
early in stable bands. Repeated distance/noise/preset calculations may be folded
or shared by the compiler. Added branches/larger inlined normal/parallax choices
could affect register/code pressure even when most pixels take the original
path, but no driver evidence establishes this.

**Conditional water probes:** first share one classification result between
vanilla parallax and normal, preserving their separate kernels and world/pixel/
frame inputs. Then, independently, try a compile-time PERFORMANCE-only cheap
dispatch diagnostic while retaining explicit original/live-disabled branches.
It can prune unused preset kernels but cannot serve as the production live-preset
bridge unchanged. Keep DH's normal-only seam separate and retain original shadow
calls. Do not reintroduce dynamic octave loops or enable analytic normals to
answer this control-shape question. Derivatives, wave normalization, near/far
crossings and grazing water must still be inspected.

## Restoration and decision boundary

Compile-time effect-off selects literal original cloud/fog includes and direct
original water wrappers. The larger archive contains both preprocessing branches;
it does not run both branches. Live global disable is different: tier zero /
original counts are returned inside a derivative program, whose overall code
shape can remain changed. Equal visible outputs do not establish identical
compiled shaders. Constant-kernel experiments must explicitly preserve both
off mechanisms and original source/count/extent behavior.

At the final read-only snapshot, all nine effect summaries existed under
`dev/logs/photon-native-effects-r1`. These are serial single-block means, not
paired repeats or this worker's own GPU runs:

| Arm | FPS | GPU mean ms | GPU median ms |
| --- | ---: | ---: | ---: |
| Original | 61.11 | 15.510 | 14.990 |
| Patched global disabled | 61.23 | 15.098 | 14.942 |
| All active | 55.81 | 16.607 | 16.382 |
| Alloff | 54.79 | 17.180 | 17.010 |
| Materials only | 56.58 | 16.382 | 16.069 |
| Cloud only | 60.54 | 15.220 | 15.112 |
| SSR only | 58.99 | 15.673 | 15.552 |
| Water only | 58.98 | 16.428 | 15.621 |
| Fog only | 60.66 | 15.249 | 15.159 |

These values do not support selecting the visibly changed cloud loop as the
cause; nor does a source-original alloff arm establish the same whole-program
compilation. The water mean/median distinction also matters. The coordinator's
complete interpretation/repeat choice is pending; no implementation is
recommended before its assessment and repeated suspect-arm control.

The immediate next experiment is chosen by that assessment. Freeze current
production sources, preserve exact effective options/framebuffer/input states,
and repeat original / alloff / suspect-only in balanced order. If a suspect
persists, inspect the submitted Iris shader and test exactly one conditional
probe above. Record pass attribution and total elapsed/frame/thread clocks
separately, plus moving-camera quality. Program source length and scalar loop
counts are not register/occupancy measurements. A flat or negative probe closes
that hypothesis; it does not justify combining several rewrites.

This report reuses the primary-method references and GPU/derivative constraints
in [PERFORMANCE_METHOD_RESEARCH.md](PERFORMANCE_METHOD_RESEARCH.md), without
claiming a new generic optimization result. Major adapter/preset changes and
the actual suspected-effect decision remain with the coordinator/protocol owner.
