# Primary volumetric integration LOD implementation

2026-10-05. Source freeze by `volumetric_lod`; private/default off. No client,
shader reload, driver compilation or GPU timing was performed by this worker.
Root/runtime operator retains exclusive client/GPU ownership. This implements a
parallel performance track; it does not promote another supported shader.

## Owned files and actual entry points

- `shaderpacks/common/shaders/include/shaderlod/volume_budget.glsl`: new,
  uniform-free policy and interval partition helper, usable by explicit adapters.
- `shaderpacks/photon-volumetric-lod/shaders/include/shaderlod/photon_volume_adapter.glsl`:
  pinned Photon reference consumer, explicit density sampling and actual marcher.
- `patches/photon-experiments/volumetric-lod.json`: pinned replacement/addition
  manifest. Only upstream `cumulus.glsl` and `settings.glsl` change.
- `scripts/build_photon_volumetric_lod.py`: deterministic ignored ZIP assembly,
  exact original/member/helper/license fingerprints, fail-closed path/scope checks.
- `tests/test_photon_volumetric_lod.py`: source, numerical and actual Iris parser checks.

Shader route: `shaders/world0/deferred1.fsh` → `program/d1_clouds.fsh` →
`include/sky/clouds.glsl` → `draw_cumulus_clouds`. The selected source keeps the
literal original function as `shaderlod_original_draw_cumulus_clouds` and adds a
wrapper that resolves eligibility **before** the density/lighting march. Eligible
rays call `shaderlod_volume_draw_cumulus`; its primary loop iterates
`uint(ceil(work_target))` times rather than the original count. Secondary density
rays keep their original counts, geometric step growth and extents. Existing
`transmittance < 0.075` early exit is retained. Other cloud layers, cached sky,
air fog, water fog, shadow-map cadence and the pass graph remain upstream.

Air fog was inspected directly: its normal camera lies inside the 40..320-block
fog volume, and water fog begins inside the water volume at <=50-block ranges.
Preserving full inside-volume integration makes those poor candidates for this
bounded track. This code instead reduces distant primary integration in actual
cumulus rays. It does not repeat the previous fine-density0.5, secondary-extent,
lighting-cache or literal-secondary experiments.

## Policy, units, normalization and fallback

Core version must equal2, global enabled must equal1, and effect extension version
must equal1 or2. The interface worker confirmed that all six Volumetrics fields
have the same contract in both extensions. Mode0 INHERIT uses core near/far/full
centered transition widths and this adapter's retained medium/far ratios0.85/0.75.
Mode1 FULL calls the original function. Mode2 ADAPTIVE uses
`shaderlodVolumetricsNear/Far/Transition/MediumWorkRatio/FarWorkRatio`. Unknown
versions/modes, missing transport, NaN/Inf, invalid ordered bands/ratios, global
off, empty intervals and upstream counts below the8-step floor stay original.
The new protocol is source referenced; driver linkage/consumption is unverified.

Atmosphere positions, shell intersections and step lengths retain Photon's native
units. The adapter divides actual entry/interval distances by `CLOUDS_SCALE` to
obtain camera-relative blocks. It uses final-resolution `view_res`, projection
focal scales and view-ray foreshortening to derive a conservative finite-pixel
cone bound, without fragment derivatives. The fine-noise texture's actual base
level dimensions supply a voxel feature scale:
`1 / (0.005 * max(textureSize) * CLOUDS_SCALE)` blocks. That is a conservative
eligibility heuristic; nonlinear shaping means it does not prove that density is
band limited or that sample reduction is imperceptible. Reduction smoothly starts
when the smallest pixel footprint exceeds one voxel and reaches its policy cap
at two voxels. Narrower footprints retain original work.

Inside/above the physical cloud shell, including one atmosphere unit of boundary
rounding protection, retains full work. World-space overhead views restore full
work smoothly over `abs(ray.y)=0.35..0.65`; >=0.65 remains original. Actual volume
entry controls the distance bands, so cloud geometry beginning near the viewer
retains its near integration rather than being classified by the far endpoint.
This does not discard a near interval to manufacture savings.

Distance, footprint and overhead weights combine into a **continuous** retained
work target; no frame-noise count selector is added. A count target q allocates
ceil(q) cells of width interval/q, shortening the final cell to the remaining
interval. Each sample uses the original primary jitter within its own cell. Every
optical-depth term multiplies the actual cell length, and apparent-distance
weights also include cell length so the short last cell is not overrepresented.
Configured cell lengths sum to the unchanged full ray interval, subject to the
same upstream transmittance early exit. No density, extinction, scattering
coefficient or fog strength is reduced. Integer q recovers ordinary equal-width
quadrature; at integer count crossings the last cell's contribution tends to0.
Upstream phase functions, secondary illumination and distance fade laws remain.

The active density and coverage functions use explicit `textureLod(...,0.0)`,
including local coverage, both raw3D noise fields and direct noisetex coverage.
The consumer is restricted to PROGRAM_DEFERRED1's analytic atmosphere-transmittance
path. The prior exact sampler report confirms LINEAR nonmipmapped inputs,
anisotropy1, sampler object0 and baselevel0 in that controlled runtime. This is a
source qualification, not new archive sampler/parity acceptance; repeat these
checks in the new driver smoke. Original fallback density remains literal
upstream. Dynamic-path parity must be checked for the selected actual bindings.

Private option `SHADERLOD_VOLUMETRIC_INTEGRATION_LOD` defaults off. Outer standalone
`#ifdef` makes it discoverable by actual Iris; inner `#if defined` stage/LUT guards
prevent accidental stage options. Private off or another stage preprocesses every
modified upstream member to its original bytes. Helpers use `#if !defined` guards
to avoid introducing include-guard options. All global-off/unsupported cases call
original integration. Both build-time disabled source and runtime global-off are
separate controls and must be measured separately.

## Static and assembly evidence

Executed `python3 -m unittest discover -s tests -p test_photon_volumetric_lod.py -v`:
**12 tests pass, no skips**. This includes actual pinned Iris lexer/OptionSet and
MutableOptionValues discovery/defaults/overrides; the private gate is the sole
added option across patched sources plus both helper bodies, with no stage or
helper-guard leak. Numerical checks cover constant-medium Beer–Lambert
transmittance and scattering across integer/noninteger budgets, full interval and
sample endpoints, float32 interval residuals, original/floor/off/invalid/transition
cases, real primary count reduction and finite-pixel cone bounds over off-axis
rays. A constant medium fixture is not variable-cloud quality evidence. No driver
compiler, Minecraft client, Gradle build or performance measurement was run.

Three archives actually assembled under ignored `build/`. Patched controls each
retain529 upstream members byte-for-byte, change2 and add2 helpers plus metadata.
LICENSE SHA256 stays `514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
Photon v1.3b attribution to Benjamin Stott (SixthSurge) is preserved in the derivative.

| Artifact | SHA256 |
| --- | --- |
| Exact original Photon v1.3b ZIP | `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` |
| Manifest | `08e1611a77a2f6d6364b91eda692ea2e300b1c3c2891c87d7abd6ecec98def9b` |
| Assembled original source plus metadata | `ae9fe719b5874cb124c93c84c1ebc93bff7f612ec7f67495cfa7e0d3aa9cc560` |
| `build/Photon-VolumetricLOD-disabled.zip` | `3685e47e94cd08f3994053f300981bbdd92ae108a65f7fadb700bd6d9422a6b5` |
| `build/Photon-VolumetricLOD-enabled.zip` | `c27377dcfd9cef09a20362c25ac55b25224f9fad1a5b82c274128a95fa4e1cd6` |
| Prior controlled sampler report | `5dbc7edf7fa5b11cebd2a4ac948a2c8e9553ddf467f5141831c29e162e283e67` |

Minecraft1.21.1/NeoForge21.1.253/Iris1.8.12+1.21.1-neoforge/Sodium0.6.13/
Photonv1.3b/DH3.3.3/Java21 remain the pinned component set in
`dev/runtime.lock.json`; no artifact set was changed. Actual Iris parser JAR and
class fingerprints are pinned and verified by the manifest/test. DH compatibility
of the new adapter and any other driver are unverified.

## Minimal runtime acceptance and measurement needed

The runtime operator must select a cloud-rich pose with at least some actual
volume entries beyond the pixel-footprint threshold; otherwise an unchanged image
or timing cannot establish that reduced integration executed. First verify driver
compilation, linked core/extension controls, sampler sizes/filters and Iris's exact
private option delta. Check core enabled0/1, known extension1/2, absent/unknown
version, FULL, ADAPTIVE unity and lowered ratios, shader/config reloads; record
actual state rather than assuming config names control the shader.

Compare exact original mod-absent, private-disabled, selected/global-off and selected
on at equal Photon preset/native viewport/dependencies/cloud state. Keep the new
helper compiled but FULL or unity as an additional control if explicit LOD0/wrapper
cost or parity is uncertain. Original-versus-global-off must be visually inspected
before attributing an on/off difference to sample counts. Inspect cloud silhouettes,
thin wisps/occupancy, near/overhead/inside, layer overlap/apparent distance,
lighting, terrain occlusion, partial final cells and changing counts during camera
movement. Retain native stills and transitions/motion; resized broad-composition
stills do not establish parity. Check DH absent first and DH-present boundaries
separately; new support is not inferred from source.

Only then run repeated balanced-order asynchronous elapsed-GPU and total-frame
windows, with equal fullscreen dimensions/focus/warmup and quiet measured windows.
Report median/p95/tails and within/between-run variability. Compare query-off
frame controls if measurement overhead is material. Include the clouds pass where
reliable attribution is available, and separate primary work selection from early
exit/occupancy changes and CPU scheduling. FPS alone or the theoretical25% sample
cap is not a GPU or total-frame gain. No performance benefit or accepted quality
parity is claimed at this source freeze; the50% general goal remains unachieved.
