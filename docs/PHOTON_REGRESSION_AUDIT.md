# Photon candidate regression isolation

Source-only audit checkpoint, 2026-10-04. The coordinator's 36-run matrix remains
active. No shader/source/test changes, compilers, package builds or game launches
were performed for this audit. The first block motivates isolation; it does not
identify a cause or establish final performance.

## What the first captures actually match

Inspected schema-2 raw reports `001` through `006` under
`dev/logs/sustained-v3-main/`, including loaded effective shader options rather
than only Iris's normalized options-file bytes.

| First capture | Stack | FPS | GPU render mean | Render-thread CPU / whole tick |
| --- | --- | ---: | ---: | ---: |
| Forest original high | minimal | 121.09 | 7.697 ms | 7.564 ms |
| Forest patched, bridge disabled | minimal | 118.73 | 7.821 ms | 7.644 ms |
| Forest active candidate | minimal | 93.56 | 9.935 ms | 9.410 ms |
| Premium cloud active | optimized | 62.77 | 15.185 ms | see raw report |
| Premium cloud patched disabled | optimized | 52.72 | 18.234 ms | see raw report |
| Premium cloud original high | minimal | 57.42 | 16.612 ms | see raw report |

Cloud original/minimal must not be treated as a matched optimized-stack control.
The cloud active/off captures share the optimized stack, but remain one block;
complete repeated results govern the conclusion.

Forest active/off have identical complete resolved shader-option maps, the same
loaded mod set and identical DH configuration text. Original high differs in
loaded ShaderLOD and pack, as intended. Shared upstream shader options match
original high; the only additional option keys are ShaderLOD's own options.
All three use 1488x836, requested/effective radius 24, the same forest camera
256.5/95/768.5 yaw 135 pitch 9, time 7000, clear weather and DH present. Start/end
meaningful states are stable. Both patched captures report PERFORMANCE, near32,
far96, width8, debug false, terrain policy inactive; bridge enable is the changed
setting. Pack option `SHADERLOD_ENABLED=true` is identical, because the live mod
bridge supplies false/true. Neither report shows Iris fallback.

Resolved controls include protected lighting true, cached sky false,
BORDER_FOG/BLOCKY_CLOUDS false, WATER_WAVES/PARALLAX true, three wave octaves,
water bands 96/256/32, analytic normals true, cloud bands 128/1024/64 and default
cloud upscale4. The cloud scene instead has upscale1 and rain. This excludes an
obvious quality-option/radius mismatch in these captures; it does not establish
identical worker activity, driver clocks, heap state or upload completion.

Forest GameRenderer thread time is 7.122/7.189/8.801 ms in original/off/active;
whole-tick thread time also increases in the active capture. Thread time includes
GL driver execution or spinning and cannot alone assign the loss to Java work.
The small original/off difference and larger active difference in this one block
suggest isolating enabled shader behavior, while preserving all evidence and
waiting for the remaining blocks. Do not treat either difference as proven
query overhead or proven shader regression yet.

## Atmosphere guard audit

The structural pack introduces `shaderlod_needs_atmosphere(depth)`. With
BORDER_FOG/BLOCKY_CLOUDS absent, it is `depth == 1.0 || !shaderlod_enabled()`.
The source then places the original combined sun/moon atmosphere call inside
that predicate. Enabled terrain can discard an otherwise unused calculation;
disabled and sky paths execute the original inputs and calculation.

The disabled check is uniform but depth varies across pixels. The shader already
has a sky/terrain branch afterward; the new earlier branch can change lowering,
register lifetimes and whether a compiler predicates or sinks the atmosphere
work. The original compiler may already sink some unused work into its sky path.
It is therefore possible for the explicit branch to add cost without removing
physical work. This is a hypothesis, not a source-proven cause. Do not add another
branch-based optimization to conceal it.

The LUT's raw custom binding on pinned Iris has non-mipmap filtering and maximum
level/minimum LOD/maximum LOD zero. The implicit-derivative mip selection concern
is resolved for that exact binding; it does not prove the new branch is faster.
There is no extra sampler, shadow/AO change or atmosphere approximation in it.

Clean isolation already exists: the water-only and structural candidate ZIPs
have exactly three differing entries: d4 source, helper (the atmosphere predicate
addition), and build metadata. All other bytes, including water, cloud defaults,
settings and properties, are identical. Compare these active packs with identical
loaded options and current mod code before changing the guard.

## Analytic water and older policy audit

`get_water_gradient` correctly differentiates the existing squared-sine Gerstner
term while treating sampled wave noise as locally constant. It retains original
frequency, amplitude, wavelength, rotation, amplitude normalization and scalar
height variation. Static actual-function derivative fixtures passed multiple
positions/directions and 1/2/4/8 octaves. The omitted noise/height-variation
texture derivatives and replacement of the original forward finite difference
are known approximation costs; no sign/scale/rotation error was identified by
this source audit. Those fixtures do not validate moving-water fidelity.

Analytic is not automatically cheaper arithmetic: it uses sine plus cosine,
loop-carried wavelength/frequency/amplitude, dynamic octave counts and a normal
approximation branch. The original also has costly wave/noise loops, but a
compiler may specialize the original three-octave setup differently. At medium
Performance with the default three original octaves, normal noise reads fall
from 9 to 2 versus original high (previous finite-difference LOD used 6). This says
nothing about total terrain submission or complete GPU cost. The new normal
branch changes only nonzero water tiers; near and shadow retain original heights.

The new water range is more conservative than the previous pack. Current full
water extends through 80, medium through 240, and low starts 272 blocks. The older
218992b helper used generic 32/96/8 bands: full through 28, fully medium 36–92,
fully low100+. For example, water at 160 blocks previously used one far octave
and skipped parallax; now it uses two medium octaves and medium parallax. This
can be more work than the previous active pack while preserving nearby water
quality. It is still reduced work compared with original high. Comparing old and
new active packs without matching bands conflates policy with the derivative.

The old generic `shaderlod_quality_at` checks stable near/medium/far interiors
before computing transition noise. New water quality computes that small scalar
noise helper on every enabled surface call before tier selection. Both water
normal and parallax request it independently. This is additional source work,
with no measurement establishing a material cost. Do not assume removing a few
noise arithmetic operations explains the observed whole-frame difference.

## Exact next controls after the frozen matrix

Use fresh profile copies and resolved-option fingerprints, retain original
radius24/DH128, geometry disabled, protected lighting and cached sky off, the
same camera/resolution/time/weather and identical timing cadence. Alternate
complete-JVM runs, measure at least three paired repetitions and retain anomalies.

1. **Structural guard isolation.** Active water-only archive versus active
   structural archive. Both have new water/cloud policy. This isolates the d4
   guard without a source edit. A repeatable structural loss selects the guard
   for rollback/replacement; no difference rejects it as the cause at this scope.
2. **Analytic isolation.** Water-only pack active with
   `SHADERLOD_WATER_ANALYTIC=true` versus false. Keep 96/256/32 and all other
   options/preset unchanged. The false option retains finite-difference normals
   at the same selected octave counts; it does not switch off other LOD effects.
   Repeat on both forest and the inspected coastal scene. Analytic false on the
   structural pack can be a confirmation arm, but first avoid mixing candidates.
3. **Previous-policy/source control.** Reconstruct 218992b's explicit patch and
   overlay in an isolated ignored build root. Retain current mod/runtime source
   and all current common upstream options. Set old pack cloud options explicitly
   to 128/1024/64 so its legacy defaults do not confound the comparison. Compare
   against water-only active with analytic=false and water 32/96/8, matching the
   old generic live mod 32/96/8 distances. This separates most new water policy
   and derivative work from earlier LOD behavior. The new scalar noise wrapper
   still differs, so label the remaining helper difference rather than claiming
   a perfectly byte-identical shader. A second water-only arm 96/256/32 then
   isolates the conservative water range policy on that same pack.
4. **Runtime versus compile-time disabled control, only if needed.** Existing
   bridge-disabled pack versus `SHADERLOD_USE_MOD_CONFIG=false` and
   `SHADERLOD_ENABLED=false` on the same patched archive, main mod present in
   both. The latter lets compilation eliminate LOD paths; the former retains
   dynamic uniform branches. Both must resolve tier zero. This can expose cost
   of dynamic code shape even when the off capture looks close to original.
   Do not compare a standalone fallback that accidentally remains enabled.

Useful existing artifacts (verified read-only during this audit):

| Artifact | SHA256 |
| --- | --- |
| `build/photon-analytic-candidate.zip` (water/cloud changes, no atmosphere guard) | `7cae01ced084885d6e66102b26803077fb0ed2093f854fe791b1ac8327abba19` |
| `build/photon-structural-candidate.zip` (current full candidate) | `999eae1c4c3551e0f02d45ae06b517c87f966b87b7ca6a8e779a16566c6a003b` |
| Pinned original archive, as loaded by reports | `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` |

For the old reconstruction, read `git show 218992b:patches/photon/v1.3b.json` and
all files listed by `git ls-tree -r --name-only 218992b -- shaderpacks/photon`
into a separate ignored root with a copy of the current exact runtime lock.
Use the existing deterministic builder's `root=` parameter and original archive
as `base=`. Do not checkout/reset 218992b in the shared workspace or change
source while another matrix is active. These are instructions for a future
owned operation; no legacy artifact was rebuilt during this audit.

Acceptance remains measured whole-render gain with preserved lighting and
acceptable water motion. A faster old radius cap is not evidence that the new
shader-only candidates work. If repeated active results regress, keep the
original/disabled path available and remove or opt out of the failed candidate
before pursuing additional shaders. This audit makes no final gate decision.
