# Shared volume physical-work audit

2026-10-05. Read-only source/math and existing-report audit; owner
`volume_work_audit`. Only this report was created. No source, manifest, archive,
class or runtime configuration changed; no build, parser suite, client, GPU job
or child worker launched. Root retains the idle GPU. No owned live processes.

The common mechanism bounds the negotiated primary-cell cap. It does **not**
bound executed primary density calls, occupied-cell lighting calls, texture
fetches or elapsed GPU work against the original. Two mathematical
counterexamples below establish that distinction. They do not establish that
either event caused the failed pilot's slowdown. Next evidence should separate
actual executed work from integration overhead and timing instrumentation.

## Scope and pinned evidence

Direction comes from [STATUS](STATUS.md) and the latest
[general LOD goal](goals/general-lod-50-percent.md): reusable LOD system, Photon
as explicit first reference, 1.5× total rendering throughput and little
discernible quality loss. Neither target is achieved by this audit.

Inspected current bodies against `000f355^` =
`fc6fdf2a720d5d940bf1d628eb5b34dd8a328ea4`; HEAD when read was
`f501d5ad9e25aac7a659d0ba03e3019e9dc1bc07`. Current frozen files:

| File | SHA-256 |
| --- | --- |
| `shaderpacks/common/shaders/include/shaderlod/volume_budget.glsl` | `cf37ff0af1b3ebb6cc3f3f95ae83c708518a8e48d03545e8665962d14cf731ac` |
| `shaderpacks/common/shaders/include/shaderlod/work_policy.glsl` | `1b70d1b34959778ac34ae9316797c23d858531ff366b55373bae1c7ee55ee309` |
| `shaderpacks/photon-volumetric-lod/shaders/include/shaderlod/photon_volume_adapter.glsl` | `74349a36793081be528b34f9f047fc9ed195a8f2b12545caade0d4637af75905` |
| `patches/photon-experiments/volumetric-lod.json` | `40efd7e702e41388033255d48a007c543e1055ccb4e2513c1908c2d82b8f2677` |

Original archive read directly: `dev/upstream/photon_v1.3b.zip`, SHA-256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Original member `shaders/include/sky/clouds/cumulus.glsl` SHA-256
`1923542cd9ad862a6166a542dbdcb1597fd70316e4338b236deefc2ccb932ba5`.
Also read original `coverage_map.glsl` and `program/d1_clouds.fsh` to resolve
local coverage and the selected-stage call. No current web documentation was
needed: the pinned source and recorded runtime are the primary evidence.

Version boundary: Minecraft 1.21.1 / NeoForge 21.1.253 / Java 21 /
Iris 1.8.12-local / Sodium 0.6.13 / Photon v1.3b. The pilot is minimal/noDH,
2976×1672 on Apple M3 Max OpenGL 4.1 Metal 91.7, GLSL 4.10. The source requires
core 2, effects 1 or 2, capability schema 1/volume availability 1. Other packs,
hardware, DH, malformed option counts and actual driver instruction counts are
outside the evidence.

## Requested work and count conversions

[work_policy.glsl](../shaderpacks/common/shaders/include/shaderlod/work_policy.glsl:57)
validates transported bands, protocols, integer count domain and legal floor.
It computes `continuous_requested_work = upstream_work * ratio`, then ceil and
floor-clamps an integer `realized_work`. The volume consumer intentionally uses
the continuous request instead of that rounded integer.

[volume_budget.glsl](../shaderpacks/common/shaders/include/shaderlod/volume_budget.glsl:12)
requires `1 <= minimum <= original <= 65536` before uint→int conversions.
It returns `target = clamp(mix(N, N*r, w), minimum, N)`, where
`w = footprint_weight * (1 - protected_weight)` lies in [0,1] for validated
inputs. Thus target cannot exceed N. The frozen consumer obtains N by uint
conversion of the direction-dependent 40→20 mix, matching the original selected
DEFERRED1 stage's truncation. The `/2` DEFERRED0 counts inside the copied body
are inactive: manifest injection explicitly selects DEFERRED1 and excludes the
atmosphere-transmittance LUT. This conclusion depends on those stage guards.

The active loop uses `uint(ceil(target))`, so its *maximum* primary iterations
also cannot exceed integer N. For N=39 and ratio .75, target=29.25 means 30
cells, a 23.08% cap reduction. A transition or a partially restored quality
weight can reduce target by less than one without reducing the integer cap at
all, while still selecting the active path and changing its grid. Far .75 and
medium .85 are requests; near, overhead, insufficient footprint and fallback
can restore full work. There is no count overflow or deliberate budget→density
conversion in the validated frozen path. The policy is evaluated once in
preflight and again with actual distance/quality; preflight results are not the
executed cell count.

## What changes physical work

The diff against `000f355^` shows the shared-system change alters policy,
capability checks and radial entry calculation. The copied march, density,
secondary optical-depth and continuous-cell helpers are unchanged from that
prior implementation. INHERIT previously used private .85/.75; it now resolves
unity and therefore selects original. ADAPTIVE delegates to the common policy.

Compared with original Photon, the following integration differences matter:

- **Early exits remain data dependent.** Both bodies return on zero coverage,
  blocked/missed shell and terrain, skip empty density, and break when running
  transmittance falls below .075. Primary density has shell/coverage-map bounds
  and an altitude-shaped density exit before its two Worley samples. Changing
  sample locations changes all these outcomes. An active path with cap 30 can
  execute more cells than an original cap-40 path that broke after one cell.
- **Sampling positions change with the continuous target.** Original samples
  are `entry + (i + jitter)*L/N`. Active samples are
  `entry + i*L/target + jitter*cell_length(i)`. For integer targets these are
  equal-width grids at the new spacing; for fractional targets the final cell
  is shorter and its jitter is restricted to that remaining cell. The cell
  lengths sum to L in real arithmetic. They do not preserve original density
  occupancy, optical depth or the transmittance-break index in a varying field.
  The same frame dither input is used; positions and the per-cell `hash2` that
  dithers secondary rays change. There is no new stochastic count selector.
- **Secondary work is not reduced independently.** Each primary cell surviving
  the density check executes six direct-light and two ambient density calls,
  then the original eight-iteration scattering approximation. Those secondary
  loops have no optical-depth early break. Executed density-call count is
  `P + 8*Q`, with P reached primary cells and Q cells entering lighting. A lower
  P cap does not imply a lower Q. The density function itself can exit before
  detail, so even this call count does not prove equal texture or ALU cost.
- **No new lighting-strength normalization shortcut.** Active extinction uses
  `density * extinction_coeff * cell_length`; the original scattering integral
  `(1 - step_transmittance)/extinction_coeff` remains. Cell-length-weighted
  apparent-distance accumulators replace equal-width accumulators, needed for
  the partial cell. For a uniform field and no early truncation this preserves
  total optical thickness. It does not restore exact heterogeneous lighting,
  because density, secondary hash, ray origin and break location changed.
- **Selected and fallback paths execute one complete march.** Wrapper lines
  373–450 return either original or active; they do not evaluate both results
  and mix them. However, active-eligible rays evaluate spherical-shell
  intersection in the wrapper and again in the selected inner body. Rays that
  later fall back also repeat that setup in original. Coverage-zero is checked
  after wrapper shell intersection, while original checks it before setup.
  The wrapper does not prove planet occlusion before footprint/sampler queries;
  the inner body may immediately reject such a ray. Disabled/FULL/unity and
  capability-invalid paths return before these expensive wrapper steps.
- **Additional per-cell arithmetic is explicit.** Active cells compute a
  clamped remaining length and a sampled position; the cell sample helper
  itself recomputes cell length. Constant fold/common-subexpression elimination
  may remove some arithmetic, but source inspection cannot prove that driver
  result. Inlined original and active functions also increase compiled-program
  alternatives; branch/register/compiler effects remain unmeasured.
- **Sampler fetches are conditional on density, not work eligibility alone.**
  Shared helpers fetch no textures. Wrapper `textureSize` is metadata query,
  not a texture-value fetch. Once shaped density survives, both Worley fetches
  are written unconditionally even if a runtime weight were zero; this was
  already true upstream. Local coverage writes two noisetex reads, but the
  pilot has PRECOMPUTE_LOCAL_COVERAGE=true and uses one coverage-map fetch
  instead. Active density and secondary calls use explicit `textureLod(...,0)`
  where original uses implicit `texture(...)`; local coverage does likewise.
  This distinction predates the shared-policy refactor. Prior untimed QA
  reports linear sampling, no sampler objects and LOD-0 noise allocations; it
  does not establish equal instruction cost or fresh timed-arm sampler state.

Source locations:
[wrapper](../shaderpacks/photon-volumetric-lod/shaders/include/shaderlod/photon_volume_adapter.glsl:371),
[loop](../shaderpacks/photon-volumetric-lod/shaders/include/shaderlod/photon_volume_adapter.glsl:256),
[density](../shaderpacks/photon-volumetric-lod/shaders/include/shaderlod/photon_volume_adapter.glsl:71),
[secondary loop](../shaderpacks/photon-volumetric-lod/shaders/include/shaderlod/photon_volume_adapter.glsl:145).

### Small mathematical checks actually run

A short Python scalar calculation used normalized interval L=1, jitter=.5,
original N=40, candidate target=30, the existing .075 transmittance break and
six+two secondary calls per occupied cell. It ran no real noise, GLSL or GPU.

| Synthetic density along primary ray | Original P/Q/density calls | Reduced P/Q/density calls |
| --- | --- | --- |
| density=1 on [.012,.013], otherwise 0; extinction=20000 | 1 / 1 / 9 | 30 / 0 / 30 |
| density=.1 on [.016,.017] and [.049,.051], otherwise 0; extinction=1 | 40 / 0 / 40 | 30 / 2 / 46 |

The first original point .0125 hits opaque density; the coarser grid misses it.
The second coarser grid hits .016667 and .05, while original misses both narrow
regions. Smooth narrow density bumps can have the same sampled values. These
are counterexamples to a general executed-work guarantee, not simulations of
the pinned cloud scene or proof of a visual acceptable change.

## Raw pilot and CPU qualifications

Inputs are `dev/logs/shared-lod-benchmark-r1/00{1,2,3}-b1-*-measurement.json`,
original/disabled summaries, `shared-benchmark-manifest.json` and
`shared-benchmark-analysis.json`. The manifest ends failed with `Cross-run
scene/runtime mismatch`; adaptive is not accepted into the analysis. The
runtime operator owns that artifact diagnosis. No paired/repeated accepted
comparison exists; the following is raw diagnostic data only.

Computed by Python `statistics.median`, CPU epoch 2 and named scopes; GPU is
game-render N5. Wait column is median of each individual wall-minus-thread
sample, not difference of medians. Thread is render-thread CPU time, not total
process CPU. Overlapping scopes must not be added.

| Arm | GPU elapsed median ms / samples | run-tick wall median ms | run-tick thread median ms | run-tick wall−thread median ms | elapsed/rendered frame ms |
| --- | --- | --- | --- | --- | --- |
| Original | 15.124459 / 243 | 16.222166 | 7.150 | 9.008292 | 16.439155 |
| Disabled | 15.736584 / 235 | 16.616917 | 7.406 | 9.222583 | 16.991018 |
| Adaptive, artifact failed | 25.949417 / 144 | 26.725875 | 8.126 | 18.641167 | 27.711717 |

Adaptive versus disabled raw GPU delta is +10.212833 ms (+64.90%); raw total-frame
median delta is +10.108958 ms (+60.84%). Render-thread median increased .720 ms
(+9.72%), while per-sample waiting median increased 9.418584 ms. This is
consistent with additional waiting being a large component, but does not
identify GPU busy work, CPU starvation, synchronization or timing overhead.
GPUelapsed includes command starvation; its similarity to the wall delta is
not independent proof of shader causation.

GPU-sampled versus other run-tick wall medians were 16.298/16.210 ms original,
16.744/16.544 ms disabled, 26.870/26.653 ms adaptive. The regression is present
on unsampled frames too, so it is not confined to the sampled frames' direct
timer calls. Queue-wide timing effects remain possible. CPU layer timers and
sampler observers were false in all raw runtime properties; coarse CPU timers
were still active. Host snapshots before/after show Python, ChatGPT,
WindowServer and indexing CPU activity, but are not observations during capture
and do not prove contamination or its absence.

## Bounded next experiment and decision boundary

After root accepts the separate artifact diagnosis, the next useful question
is whether fewer *executed* calls reach the reference volume operation. Do not
restart a long performance batch from the failed adaptive number.

1. One untimed fixed-input diagnostic comparison, original/full and reduced,
   with the same projection, temporal/checkerboard phase, jitter, wind time,
   density inputs and sampler state. Record selection/fallback reason, N,
   target, ceil target, reached primary calls P, density-surviving cells Q,
   secondary calls and reached texture-fetch counts. Per-fragment local
   counters rendered to diagnostic outputs can avoid unsupported GL4.1 SSBO
   assumptions; read back outside timing. Preserve the production body and
   separate diagnostic archives/hashes. Counters establish execution, not
   production speed. Stop after the one fixed comparison and report histograms
   or totals by selected/fallback pixels.
2. If work rises, resampling/exit/occupancy is established as an integration
   issue; retain that evidence. If it falls, isolate overhead with a reviewed
   **diagnostic full-target active-body control** at target=N, alongside the
   original function under the same wrapper and inputs. Current unity mode
   always calls original and cannot serve as this control. This separates
   the continuous-cell/sampler/body cost from reducing count; it does not
   propose any pack-specific cache, noise cut or preset change. Root decides
   whether and who implements the diagnostic control.
3. Only after correct artifacts and work evidence, take a short repeated
   disabled/reduced timing comparison under the existing quiet protocol, then
   a matched query-free corroboration. Keep counts/readbacks out of timed
   arms. Compare run-tick wall and render-thread CPU with GPU elapsed; do not
   sum pass times. A query-free total-frame result separates production wall
   behavior from elapsed-query instrumentation, while work counters distinguish
   algorithmic behavior from CPU/driver waiting. Stop at the predefined run
   bound; still/motion quality remains its own gate.

No measured performance ceiling is justified: these data have no accepted
adaptive match and no isolated volume share. A limited *linear-cost model*
gives `speedup = 1/(1 - f*(1-r))` with volume share f and effective retained
ratio r. At r=.75, even f=1 yields 1.333×, short of 1.5×; at r=.85 it yields
1.176×. Floors, overhead protection, fallback and integer rounding reduce
this modeled benefit. Changed occupancy/early exits and compiler behavior
invalidate it as an absolute physical ceiling. No actual f or total gain is
claimed from prior pass timings or these failed raw arms.

Unresolved decisions returned to root: repair/accept the artifact provenance;
choose whether to authorize the bounded physical-work diagnostic and full-target
control; decide the next reusable integration change from those results. This
audit takes no ownership of source implementation, GPU scheduling or project
completion.
