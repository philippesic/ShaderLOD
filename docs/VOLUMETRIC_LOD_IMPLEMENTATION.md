# Shared volume LOD reference consumer

2026-10-05. The existing primary volumetric integration now consumes the general
ShaderLOD work policy. This source freeze is private and default off. It adds no
Photon density, noise, projection, sample-cache or preset optimization. The prior
march remains byte-for-byte identical, with body SHA-256
`94f07353d7daf4e63f1c438c6dc55edeceeb8aa2cbfeead5af8ab083b148fedb`.
The general 1.5 × rendering-throughput goal remains unachieved.

## Entry points and ownership

`shaders/world0/deferred1.fsh` → `/program/d1_clouds.fsh` →
`/include/sky/clouds.glsl` → `draw_cumulus_clouds` →
`shaderlod_volume_work_target` → shared `shaderlod_work_decide`.
An eligible continuous target enters `shaderlod_volume_draw_cumulus`.
Full/fallback targets call literal `shaderlod_original_draw_cumulus_clouds`.
Only Overworld deferred1's analytic transmittance path consumes this reference.
Other dimensions/stages, the transmittance LUT and private-off source retain
original preprocessed tokens. No other shader pack is added.

Owned sources are the common `volume_budget.glsl`, the existing
`shaderpacks/photon-volumetric-lod/` consumer/README, volumetric builder/manifest/
fixtures and this document. Frozen shared `work_policy.glsl` includes
`effect_policy.glsl`; both are explicitly packaged and pinned with the consumer.
`shading_rate.glsl` is optional for spatial consumers and is not used or packaged
by this count consumer. Shared API and runtime Java registration are separately
owned; the binding owner receives exact archive/member/options evidence below.

No Minecraft client, shader reload, renderer/GPU operation, Gradle invocation or
main/dev compiled-tree change was performed by this owner. Root/runtime operator
owns all driver reloads and measurements. No owned live process remains.

## Shared policy, physical inputs and fallback

The source requires exactly `shaderlodCapabilitiesVersion == 1` and
`shaderlodVolumeIntegrationAvailable == 1`. Absent uniforms initialize to zero;
missing, unknown or unavailable operation capability retains original work before
intersections, footprint calculations or `textureSize`. A capability value alone
is not evidence of actual rendering. Runtime binding must separately verify the
archive, required members and effective Iris options.

`ShaderlodWorkPolicy` carries core2, known effects extension1/2, global enable,
Volumetrics mode and resolved bands/ratios. The shared mechanism validates modes,
versions, numerical bands, transitions and retained ratios. Mode0 INHERIT
explicitly resolves the shared/default safe unity policy: core bands, ratios1/1.
The old Photon-only inherited ratios0.85/0.75 are removed. Mode1 FULL stays
original. Mode2 ADAPTIVE obtains all six general effect fields: Mode, Near, Far,
Transition, MediumWorkRatio and FarWorkRatio. There is no private tuning preset.
Unknown protocol/mode, global off and invalid/unordered/nonfinite bands/ratios
return original work. A shared preflight rejects these cases before volume setup;
its preliminary zero distance is only an eligibility gate, not the final budget.

The consumer supplies actual upstream horizon/zenith primary count interpolated
by `abs(ray.y)`, a legal minimum8 cells, physical radial entry distance, finite
positive interval, final-pixel footprint, resolved feature scale and overhead
protection. Invalid unsigned count/caller floor retains original before int casts;
the previous 65536-cell domain remains. Counts below8 retain their upstream path.

Photon atmosphere units and its shifted planet origin stay upstream. The radial
entry position in camera-relative blocks is explicitly
`direction * (dists.x / CLOUDS_SCALE)`, and distance is its Euclidean length.
The viewer's atmosphere origin shift is subtracted analytically by using the ray
displacement from that same viewer. Normalized world direction is checked first.
Interval length is likewise divided by `CLOUDS_SCALE`. Bands use Minecraft blocks
and full centered transition widths; shared domain allows far <=4096 blocks,
width <=512 blocks and positive width >=1/1024 block. Zero width selects the tier
at its exact boundary. There is no endpoint-depth or view-Z distance shortcut.

The existing final-resolution `view_res`, focal projection scale and off-axis
foreshortening calculate a finite-pixel cone bound. Fine-noise base dimensions
supply feature size `1 / (0.005 * max(textureSize) * CLOUDS_SCALE)` blocks.
These existing adapter inputs use no fragment derivatives or temporal selector.
The feature size is an eligibility heuristic; nonlinear cloud shaping prevents
any claim of band-limited density or imperceptible quadrature error.

Inside/above the shell, including its existing one-atmosphere-unit boundary
protection, stays original. Overhead views restore work smoothly over
`abs(ray.y)=0.35..0.65`; >=0.65 stays original. Narrow footprints retain full work.
The volume helper validates interval/footprint/feature/protection before the final
shared decision. It contains no duplicate distance, transition, ratio or mode
engine. Its existing quality weight is
`footprint_weight * (1 - protected_weight)`.

Refine by mixing full count toward `work.continuous_requested_work`, then clamp
to the legal floor/upstream count. Do not use `work.realized_work` or mix toward
an already floor-clamped request: those alter continuous transition behavior.
A target q marches ceil(q) cells of interval/q width, with a partial final cell
covering exactly the remaining interval. Original jitter samples within each
cell. Optical depth and apparent-distance weighting use actual cell length;
extinction/density/scattering strength is unchanged. Secondary ray counts,
geometric step growth/extents, phase functions and the upstream
`transmittance < 0.075` early exit are unchanged. Requested counts are numerical
budgets; neither accepted counts nor registration prove physical GPU work.

Existing explicit `textureLod(...,0)` density/coverage calls are unchanged.
The pinned prior report qualifies LINEAR nonmipmapped/base0/sampler0/anisotropy1
for its controlled runtime only; it does not qualify the new source on a driver.
Original fallback density sampling remains literal upstream. Driver sampler and
selected/global-off parity must be checked again.

## Static evidence and exact source binding

`python3 -m unittest discover -s tests -p test_photon_volumetric_lod.py -v`:
**15 tests pass, no skips**. Raw log:
`dev/logs/shared-volume-consumer-source-tests-r2.log`.
Fixtures cover restricted source-order binary32 policy/refinement (512 cases),
continuous unrounded/unfloored budgets, invalid/mode/version/capability fallback,
constant-medium Beer–Lambert transport, partial cells/interval residuals,
finite-pixel cone bounds, original secondary/early-exit source and march-body pin,
actual Iris OptionSet discovery/defaults/overrides, helper fingerprints and
three-arm deterministic assembly. Constant-medium/source fixtures are not
variable-cloud visual or physical execution evidence.

Actual pinned Iris Jcpp/EnhancedParser parses132 complete graphs:43 affected
entry points across original/off/on plus an original/off/on transmittance-LUT
control.44 off pairs and43 nonselected-stage/LUT pairs preserve preprocessed
tokens; exactly one graph contains the active shared consumer. A deliberate
broken shared-call delimiter is rejected. Required public flags are discovered
in the active Overworld deferred1 graph. The private gate is the sole added
Iris option; no shared guard or internal stage becomes an option. Initial fixture
failure from asserting active-path option discovery in every nonselected graph
is retained in `dev/logs/shared-volume-consumer-source-tests.log`; its scope was
corrected to the actual consuming graph.

Iris JAR SHA-256 `3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`,
embedded glsl-transformer2.0.1 `b8ca5e61b41ae18ddd5d4d5d6785cb9acd8fd7ac0c5c0b0689b872b039c11b9f`,
EnhancedParser class `11133ca7cbfbc89777b15f06a31323b26c1ab6448b1822f4086e9503f3527814`;
these are verified by the fixtures. Grammar is not driver compilation/linking.

All three actual assemblies are under ignored `build/`. Patched arms retain529
upstream members byte-for-byte, modify2, add4 explicit shared/adapter helpers and
metadata. Photon v1.3b by Benjamin Stott (SixthSurge) and its LICENSE are retained.
Exact original upstream ZIP is
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.

| Artifact | SHA-256 |
| --- | --- |
| `build/Photon-VolumetricLOD-original.zip` | `2766fd7942fb186fa9a8606aa4dbc40723594589dd7165fa1a520e6549860334` |
| `build/Photon-VolumetricLOD-disabled.zip` | `5de7561dd24cdca80860a1d5f2c60400e74163a489cc163e916f3fd47feb27de` |
| `build/Photon-VolumetricLOD-enabled.zip` | `5f3772c8e97a89590b9a55a24d330de93543324befec6419aba9aed69991e5d3` |
| Manifest | `40efd7e702e41388033255d48a007c543e1055ccb4e2513c1908c2d82b8f2677` |

Reference identity is `shaderlod:photon-volume-integration`, revision
`photon-v1.3b/shared-work-v1`, work operation `shaderlod:volume_integration`
(Volumetrics COUNT floor8). The disabled ZIP is the exact reference for separate
root/binding-owner registration. It stays default off. Required effective public
Iris options for availability are `SHADERLOD_VOLUMETRIC_INTEGRATION_LOD=true`,
`CLOUDS_CUMULUS=true`, `BLOCKY_CLOUDS=false`. A disabled effective option must
report unavailable. Internal WORLD_OVERWORLD/PROGRAM_DEFERRED1/LUT macros are
not user options. Turning the private option on through Iris leaves archive bytes
unchanged. The separately assembled enabled ZIP has a different settings pin;
registering it requires its own whole-archive/member binding.

Raw freeze/assembly report, including both arms' required member maps:
`dev/logs/shared-volume-consumer-source-freeze.json`.
Default-off binding member pins:

| ZIP member | SHA-256 |
| --- | --- |
| `LICENSE` | `514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5` |
| `shaders/world0/deferred1.fsh` | `23fc8e1c4a3bad9bf11b68b9d73ffb71db922b9f0438c5991f048e51635edee8` |
| `shaders/program/d1_clouds.fsh` | `1f0dd330b837becff393380283eb767e3a55fe48e9b04705bf825fde535d0cae` |
| `shaders/include/sky/clouds.glsl` | `07bac05a93b29cff55841d6f7425fe46d1555fe1e6b2cfc037d9fb9fee992972` |
| `shaders/include/sky/clouds/cumulus.glsl` | `5290d3c0c56693608c6cfe8efec831b67023334729d4f549b3551f430603d8e2` |
| `shaders/settings.glsl` | `b9b1c432b484d190c036f7cf243059f1f5d33c5c2c0564a9592c5323e05dd348` |
| `shaders/include/shaderlod/volume_budget.glsl` | `cf37ff0af1b3ebb6cc3f3f95ae83c708518a8e48d03545e8665962d14cf731ac` |
| `shaders/include/shaderlod/photon_volume_adapter.glsl` | `74349a36793081be528b34f9f047fc9ed195a8f2b12545caade0d4637af75905` |
| `shaders/include/shaderlod/effect_policy.glsl` | `5df5427a1cfa51e68446978040235061ab6db60ec3f5079d40ffa0c39bad6943` |
| `shaders/include/shaderlod/work_policy.glsl` | `1b70d1b34959778ac34ae9316797c23d858531ff366b55373bae1c7ee55ee309` |

The former standalone default-off archive
`3685e47e94cd08f3994053f300981bbdd92ae108a65f7fadb700bd6d9422a6b5`
and its historical evidence remain superseded source pins. Earlier standalone
rendering cannot establish driver acceptance of this shared-consumer freeze.
Changing the common volume helper invalidates older combined-pack helper pins;
combined packaging is root-owned and was not edited by this worker.

## Remaining driver, quality and performance acceptance

The runtime operator must validate the exact registered archive/option map,
linked integer capability schema/availability and core/effect uniforms, actual
mode/config reloads and rendered consuming program without fallback. Exercise
absent/unknown capability schema/availability, private-off, global-off, FULL,
INHERIT unity, ADAPTIVE unity/lowered ratios and unknown extension/core modes.
Measure physical primary cell work where possible; early exits/occupancy may
change realized counts. Config bytes alone do not supply LOD to unmodified packs.

Compare exact original mod-absent, private-disabled, selected/global-off and
selected/on at matched source/dependencies/preset/pose/cloud state/native
resolution. Inspect selected/global-off parity before attributing a difference
to LOD. Preserve near/inside/overhead views, silhouettes, thin wisps, overlap,
lighting and terrain/DH occlusion. Retain representative native stills and motion
through distance/footprint/integer-cell transitions; check popping/flicker.
A static source guard or coarse screenshot cannot certify those qualities.
The pinned Minecraft1.21.1/NeoForge21.1.253/Iris1.8.12/Sodium0.6.13/Photonv1.3b/
optionalDH3.3.3 set remains unchanged; new DH boundaries and other GPUs are
unverified.

After those gates, use repeated balanced-order original/disabled/enabled GPU
elapsed and total-frame windows, matched hardware/settings/inputs/resolution/
focus/warmup and quiet CPU conditions. Report median/p95/tails and variability;
include cloud-pass attribution where valid. Separate primary work avoidance from
query overhead, CPU starvation and occupancy/early exits. FPS alone, theoretical
cell reduction or capability negotiation is not a performance result.
No new shader-driver, quality, compatibility, GPU or throughput claim is made.
