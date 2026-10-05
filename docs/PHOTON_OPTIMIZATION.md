# Photon static water-loop candidate

Updated 2026-10-04 for the sustained performance goal. This is a statically
validated source hypothesis. Actual Apple driver compilation, repeated GPU
performance and moving-water quality remain coordinator checks. Photon remains
pinned to v1.3b, with unchanged original artifact hashes and retained license.

## Why the water implementation changed again

The previous explicit patch reduced water octave counts by passing a per-pixel
integer into the original height function. Both wave-noise and height loops then
used dynamic bounds, including inside parallax. Near/disabled results matched
upstream mathematically, but the compiler no longer saw its original constant
loop source. The first analytic shoreline pilot regressed around 140→113 FPS;
a later finite-normal pilot was also negative around 143→134. Those short pairs
changed other active effects too and do not prove dynamic loops or the analytic
normal caused the loss. See [WATER_LOOP_SPECIALIZATION.md](WATER_LOOP_SPECIALIZATION.md).

The candidate now retains these **original Photon function bodies byte for
byte** in `include/surface/water_normal.glsl`: gerstner_wave, water_waves_setup,
get_water_height, get_water_normal and get_water_parallax_coord. Original
functions keep compile-time WATER_WAVE_ITERATIONS and the four-step full
parallax source. Shadow caustic call sites remain upstream and unchanged.

Vanilla water and DH distant-water surface call sites invoke explicit LOD
wrappers. Tier 0/disabled returns the original full function. Medium/far normal
paths dispatch to fixed-count helper functions, and medium parallax dispatches
to a fixed octave/step pair. Far parallax retains the earlier coordinate-return
policy. Current enable/preset transport remains the existing Iris mod bridge;
unknown bridge versions retain shader-option fallback semantics.

## Literal-count specialization

New ShaderLOD includes implement fixed height/normal templates and a parallax
template. water_lod.glsl emits only reachable counts for the configured original
option and the three live presets. Original 3 finite mode emits only cheap 1/2
height-normal kernels, rather than eight unrelated kernels; equal full budgets
reuse the original function. Cheap loops and temporary arrays have literal
preprocessor counts, and their normalization has a compile-time exponent.
Medium parallax kernels use literal 3/2/1 step counts for Balanced/Performance/
Aggressive and call a literal cheap-height function or the original full height.
They preserve the original first-crossing while condition and interpolation.

The cap formulas are integer ceil ratios matching the existing helper:
medium B/P/A = ceil(3N/4), ceil(N/2), ceil(N/4);
far B/P/A = ceil(N/2), ceil(N/4), ceil(N/8), for supported original N 1–8.
They never increase the selected original count. The driver can now specialize
these fixed kernels; source does not establish actual unrolling, register
allocation or execution cost. Transition pixels still diverge between chosen
paths. Only one helper runs per selected pixel, rather than computing every
normal and blending afterward.

Near source is preserved literally, but calling the wrapper still adds tier
classification when enabled. Disabling through the bridge returns full quality;
compile-time disabled controls can eliminate even that dynamic branch structure.
Keep these two disabled-control meanings distinct when attributing small costs.

## Analytic normal remains an experimental, default-off option

`SHADERLOD_WATER_ANALYTIC` now defaults **false**. No repeatable measured positive
result established its benefit, and the severe first pilot motivates keeping
finite differences as the candidate default. A stored profile option=true still
overrides this source default; explicitly set false for finite benchmark arms.

The option remains available for an isolated test. Its medium/far derivative
kernels now also have literal counts. They differentiate the original squared
sine Gerstner term with locally constant noise, retain frequency/amplitude/
wavelength/rotation/normalization and scalar height variation, and omit noise
and height-variation texture derivatives. This is an approximation. Near and
shadow paths always retain original finite differences. Analytic helper selection
can change sampled texture/trigonometric work, but arithmetic counts and extra
sine/cosine calls do not prove a complete frame-time benefit.

## Existing options and quality boundaries

| Option | Default | Meaning |
| --- | ---: | --- |
| SHADERLOD_WATER_ANALYTIC | false | Experimental literal-count analytic distant normals; finite differences otherwise. |
| SHADERLOD_WATER_NEAR | 96 blocks | Full-to-medium center; full through 80 at default width. |
| SHADERLOD_WATER_FAR | 256 blocks | Medium-to-low center; fully low from 272. |
| SHADERLOD_WATER_TRANSITION | 32 blocks | Probabilistic transition width. |
| SHADERLOD_CLOUD_NEAR/FAR/TRANSITION | 128/1024/64 blocks | Existing revised cloud policy; unchanged by this water implementation. |

Water and cloud bands remain shader options; the live mod supplies enable and
preset. Generic mod 32/96/8 bands still govern other surface effects. Water bands
are deliberately longer, preserving more water/parallax work than the old
32/96 policy. Original shadows/AO remain protected and cached sky approximation
stays off. The water specialization does not modify cloud ray spacing, cloud
noise or fog/SSR budgets.

Photon defaults to three original wave octaves. Balanced normal uses 3/2
medium/far, Performance 2/1 and Aggressive 1/1. These are selected kernel budgets,
not measured instructions or frame savings. Full near remains 3 and shadow
caustics remain original. At eight original octaves, medium B/P/A is 6/4/2 and
far is 4/2/1; those reachable kernels are compiled only when required by that
configured original count.

The main pack retains the previously introduced conditional terrain-atmosphere
calculation, unchanged by this task. It is distance-independent work avoidance
with unverified runtime benefit. A separate ignored-source-root artifact omits
only that patch for strict comparison against preserved water-only 7ca. Its
metadata identifies the exact source manifest/overlay hashes; it is an explicit
isolation artifact, not a silent source change.

## Static checks and runtime stopping boundary

The deterministic builder verifies pinned archive/source hashes and literal
anchors. Original water function-body equality and upstream shadow-file equality
are tested independently. Packaging preserves unmodified upstream bytes and
license. The only new upstream source target compared with the previous pack is
the distant-water call site in include/misc/distant_water.glsl.

Actual GLSL helper bodies are executed in CPU adapters against independently
instantiated original Photon height, finite-normal and parallax bodies for all
original settings 1–8. Fixtures span all presets/tiers, coordinate-dependent
procedural noise and constant noise, height variation on/off, flowing/still
rotation, several coordinates, times and grazing/regular view directions.
Finite height/normal/parallax results must agree to documented double-adapter
absolute tolerance 1e-9; real GPU floating-point tolerances remain separate.
Constant-noise gradient checks use independent central differences with 1e-5
slope tolerance. Shadow and near routing preserve the original full result even
when the analytic option is compiled. Preprocessed fixtures also assert that
only reachable cheap kernels are emitted.

The broad fragment matrix covers all three dimensions and 11 programs across 25
variants (825 compilations); a focused 192-compilation water matrix covers all
original settings 1–8, three presets, analytic on/off and vanilla/DH/shadow/
composite-water programs. Total 1017 GLSL compilations check syntax/types without
emulating Iris or the real GPU. Static success does not confirm a performance
improvement.

Coordinator next controls: original absent-mod, patched bridge-disabled, active
finite specialization and active analytic specialization on the same inspected
SSR shoreline. Use the no-atmosphere isolation archive if comparing directly
with the old 7ca pilot. Keep DH presence, wetness telemetry, geometry, camera,
resolution, upstream cloud/fog/SSR settings and real clock policy identical.
Repeat balanced complete-JVM pairs, retain variability and inspect motion,
transition bands, near water and shadow caustics. A separate effect-isolation
pack can restore clouds/fog/SSR to upstream if the combined active arm regresses.
The ordinary-scene performance gate remains unmet until those measurements
prove otherwise. No new shader support follows from static tests alone.

Static checkpoint: 13 shaderpack tests passed in 71.928 seconds, including 1017
fragment compilations. The final numerical fixture additionally passed in 14.673
seconds after expanding to three animation phases. Raw output remains ignored
at dev/logs/photon-static-water-tests.log and
photon-static-water-math-tests.log. No Minecraft process was launched here.

| Built artifact | SHA256 |
| --- | --- |
| build/photon-static-water-candidate.zip (main source, existing atmosphere guard) | fe089ff2020168342309713a07082ebc0eddb233daec31ac0ee61c9a264ce8f8 |
| build/photon-static-water-no-atmosphere.zip (strict water isolation) | 39fcc8132e958157e02a13ea2055ad6fdbaed7a92ca54133148d03b0b7667436 |

The isolation source root is build/static-water-no-atmosphere-source. Its
manifest SHA256 is a65c1f1b1d787d5fabb97485cadccc684c1ebf9275dd9469a8ed1fe40459b9ae;
the main manifest is 6d80df2a9537df2eb66285c412a62e4397eb70ac18e92869b29532b9947efaa4.
Both archives embed shaderlod-build.json with exact per-overlay hashes. The
isolation cloud-march files and d4 source were checked byte-identical to the
preserved 7ca water-only archive. It does not add another algorithmic change.

## Experimental cloud-transition selectors

The subsequent cloud-only candidate adds `SHADERLOD_CLOUD_MODE=0/1/2` (default 0)
and boolean `SHADERLOD_CLOUD_OVERHEAD` (default true, ignored in mode 0).
Mode 0 preserves the existing stochastic budget block. Mode 1 uses separate
checkerboard-cycle tier noise while keeping march dither; mode 2 smoothly
interpolates bounded integer counts. Overhead protection derives actual
per-layer camera/shell geometry, shifts near/far together and fails full for
above/invalid protected geometry. The distant congestus ring does not become
an artificial overhead near region. These are pack options, not live mod fields.

Only cloud selection and its option declarations changed in this task; water,
fog, SSR, density/scattering and d2 temporal history code were not modified.
The new modes may restore quality and more work overhead. They do not guarantee
better lane coherence or meaningful performance. Previous single-capture losses
and unchanged-baseline variance did not identify a cause.

Use the clean cloud archive and explicit controls with other effect flags false:

| Arm | CLOUD_MODE | CLOUD_OVERHEAD |
| --- | ---: | --- |
| Current cloud policy | 0 | false (ignored) |
| Separate tier noise | 1 | true |
| Smooth integer budgets | 2 | true |
| Separate noise without camera floor | 1 | false |

All these arms use CLOUDS=true, WATER/FOG/SSR/MATERIALS=false, global enabled=true,
lighting protected=true, cached sky=false, analytic water=false and terrain cap
off. Preserve original upstream counts/resolution and measured wetness/world
age in every pair. Modes 1/2 with protectionfalse can isolate sampling from the
camera-floor quality correction. Original absent-mod, patched-disabled and
all-off remain separate controls. See
[CLOUD_TRANSITION_DESIGN.md](CLOUD_TRANSITION_DESIGN.md) for geometric and temporal
limits. Real clocks and moving-camera quality checks remain required.

The broad 16-test suite passed in 125.613 seconds, including 1446 GLSL fragment
compilations. Final added zero-width and density/history preservation checks
passed in a separate 2-test run in 4.787 seconds. No performance result follows
from these checks. Logs: dev/logs/photon-cloud-transition-tests.log and
photon-cloud-candidate-math-tests.log (ignored).

| Artifact | SHA256 |
| --- | --- |
| `build/photon-cloud-transition-no-atmosphere.zip` (recommended probe) | `8fbaa117d87b4f729ea64ecce4d86390cdc56d03cd3b5a02ec2be9ffe2213235` |
| `build/photon-cloud-transition-candidate.zip` (main, existing atmosphere guard) | `a918fb460e8f36623314150ddd9f74fbffd4c64d1d3205e4d927fa8d1db29968` |

Clean source root: build/cloud-transition-no-atmosphere-source; manifest hash
`32e322da3e28ae437c77233a726a97cbbb35355999f04b3da00cb72a7929a9f3`.
Main manifest hash `f003582f28abcaddaf1f1d771e5792ae9d34a1c314ec2941a0c7a49b59f25147`.
Both embed exact shaderlod-build.json overlay/source metadata, also retained in
ignored build/photon-cloud-transition-candidates.json. New modes remain
experimental with mode 0 default until runtime evidence supports a change.
