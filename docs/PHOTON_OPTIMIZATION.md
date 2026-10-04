# Photon water-normal candidate and usable cloud defaults

Prepared 2026-10-04 for the sustained performance goal. Owner: shader worker.
This is a statically validated rendering hypothesis; runtime compilation,
transition fidelity and GPU gains belong to the coordinator's measurements.
Pinned Photon remains v1.3b; original archive hashes are unchanged.

## Mechanism

Photon's `include/surface/water_normal.glsl:get_water_normal` computes three
wave heights at adjacent coordinates, subtracts them, and normalizes the result.
Each height evaluation samples one noise texture per wave octave and evaluates
one Gerstner-wave sine per octave. With height variation enabled, each height
also samples its variation texture. Existing ShaderLOD reduced octave counts
at distance but retained all three evaluations.

The candidate evaluates analytic wave slopes once for medium/far water. The
Gerstner height is `(0.5 + 0.5 sin(phi))^2`, so its spatial derivative with
locally constant noise is `(0.5 + 0.5 sin(phi)) cos(phi) * dphi/dcoord`.
The new `get_water_gradient` accumulates that derivative with the original
frequency, wavelength, amplitude, rotation and height normalization. It keeps
height variation as a multiplier. This avoids two thirds of the wave noise
texture fetches and two height evaluations in affected surface pixels. The
new gradient uses both sine and cosine, so the arithmetic reduction is smaller
than the texture/evaluation reduction and GPU benefit is unproven.

The noise texture derivative and slower height-variation derivative are omitted.
This changes distant normal detail and is an explicit approximation. It does
not flatten the water normal or modify geometric shadows/AO. It is unsuitable
for a claim of mathematically identical full-noise normals. Near, disabled and
shadow caustic paths keep the original three finite-difference heights and
original normalization expression. The branch selects one path before those
expensive evaluations; it does not evaluate both normals and blend afterward.

## Options and budgets

| Option | Default | Meaning |
| --- | ---: | --- |
| `SHADERLOD_WATER_ANALYTIC` | true | Analytic medium/far normals; false retains finite differences at the selected octave count. |
| `SHADERLOD_WATER_NEAR` | 96 blocks | Center of full-to-medium transition. Full quality through 80 blocks at the default width. |
| `SHADERLOD_WATER_FAR` | 256 blocks | Center of medium-to-low transition. Fully low from 272 blocks. |
| `SHADERLOD_WATER_TRANSITION` | 32 blocks | Smooth probabilistic tier transitions; zero gives hard thresholds. |
| `SHADERLOD_CLOUD_NEAR` | 128 blocks | Replaces ineffective legacy 512-block default. |
| `SHADERLOD_CLOUD_FAR` | 1024 blocks | Replaces legacy 4096-block default. |
| `SHADERLOD_CLOUD_TRANSITION` | 64 blocks | Full cloud sampling through 96 blocks. |

Water normal and water parallax use the same water bands. The mod's validated
uniform bridge supplies enabled/preset live; the water/cloud bands remain
shader-pack options. They do not pretend to follow the generic mod 32/96 bands.
Disabling ShaderLOD through bridge version 1/2 produces tier zero at all
water distances. Unknown bridge versions use pack fallbacks.

Photon defaults to three wave octaves. At those original settings, Balanced
uses 3/2 medium/far, Performance 2/1, and Aggressive 1/1. The Performance
medium normal drops wave-noise fetches from 9 original to 2 (the former
finite-difference LOD used 6), before parallax and other work.

An eight-octave original water setting keeps eight near octaves. Balanced uses
6/4 medium/far octaves; Performance uses 4/2; Aggressive uses 2/1. Those budgets
are caps and never increase original settings. At Performance medium quality,
normal wave-noise fetches fall from the original 24 to 4 rather than the former
LOD's 12. Optional variation fetches fall from 3 to 1. This count excludes
parallax, reflection, fog and other pixel work. Far water parallax still follows
the prior skip policy; medium parallax still reduces its own steps.

The 128/1024/64 cloud defaults match the previous measured premium-cloud
candidate, rather than adding a new cloud algorithm. Original surface lighting
is still protected and cached sky lighting remains off. Stored user options
can retain older cloud bands and override these defaults; explicitly record
resolved profile options in every benchmark.

## Checks and acceptance boundary

The package builder still verifies exact pinned source hashes and unambiguous
literal patch anchors. The source targets remain settings/properties/language
and the already supported water-normal include. No new sampler is introduced.
The license and all unmodified upstream bytes remain preserved.

`python3 -m unittest discover -s tests -v` checks reproducible packaging, source
hash failures, option legality, actual helper distance math, dynamic bridge
on/off/preset behavior and actual GLSL gradient bodies against independent
finite differences of actual wave-height bodies with constant noise. The
CPU gradient oracle spans 1/2/4/8 octaves, multiple positions/directions and
height variation both on and off. It checks derivative sign, scale, rotation
and normalization; real texture derivatives are deliberately outside its claim.
Static result: 11 tests passed in 45.146 seconds; raw output is retained at
`dev/logs/photon-optimization-static-tests.log` outside Git.

The fragment matrix expands all three dimensions and 11 original/vanilla/DH
programs across 22 configurations (726 GLSL compilations), including analytic
off, height variation off, disabled LOD, protected/legacy lighting and shadow
caustic variants. These checks do not emulate Iris or the OpenGL driver.

Coordinator runtime comparisons should include original mod-absent, patched
bridge-disabled, active analytic-off and active analytic-on with identical
`WATER_WAVES=true`/`WATER_PARALLAX` settings. Keep geometry policy off to isolate
shader benefit. Use original wave iteration settings for both original and
active; do not inflate the high baseline. Inspect near water, distance bands,
flowing water, reflections and camera motion before advertising quality.
Record whole-render CPU/GPU results and repeat variability. Ordinary forest
water and a coastal view are candidate workloads; the premium-cloud result
remains separate. There are no new measured performance claims in this file.


A subsequent coordinator-authorized structural candidate guards unused terrain
atmosphere work while retaining original enabled sky and border/blocky-cloud
behavior. It is independent of distance budgets. See
[PHOTON_NEXT_OPTIMIZATIONS.md](PHOTON_NEXT_OPTIMIZATIONS.md) for exact source and
pinned Iris sampler evidence. The expanded matrix has 24 variants / 792 fragment
compilations; runtime benefit remains unverified.
