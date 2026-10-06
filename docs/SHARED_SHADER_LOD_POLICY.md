# Shared shader LOD decisions

The shared mechanism wraps the existing uniform-free
[`effect_policy.glsl`](../shaderpacks/common/shaders/include/shaderlod/effect_policy.glsl)
numerical engine. It does not choose a pack preset or infer a distance from depth.
The Java `DistanceBands`/`EffectPolicy` semantics remain the policy oracle. No
upstream shader attribution was removed; these files contain ShaderLOD mechanism
code and no copied Photon algorithm.

Entry points are
[`shaderlod_work_decide`](../shaderpacks/common/shaders/include/shaderlod/work_policy.glsl)
and [`shaderlod_shading_rate_decide`](../shaderpacks/common/shaders/include/shaderlod/shading_rate.glsl).
Include `work_policy.glsl` first; it explicitly includes the maintained common
`effect_policy.glsl`. Include `shading_rate.glsl` only for a spatial consumer.
The archive assembler must copy these named common sources into `/include/shaderlod/`.
There are no uniforms, options, samplers, derivatives, frame selectors or projection
assumptions. Helpers use GLSL 4.10-compatible constructs.

`ShaderlodWorkPolicy` supplies these fields:

| Field | Meaning |
| --- | --- |
| `core_version` | Must be 2; missing/unknown versions retain original work. |
| `effects_version`, `minimum_effects_version` | Extension 1 or 2; the consumer requires 1 for an original effect group or 2 for OpaqueLighting. No forward-version guess. |
| `global_enabled` | False retains original work. |
| `mode` | 0 INHERIT, 1 FULL, 2 ADAPTIVE; unknown values retain original work. |
| `inherited_policy_resolved` | Mode 0 may use supplied bands/ratios only when the consumer explicitly resolved its existing inherited policy. Otherwise full. |
| `policy_enabled` | False retains original work. For ADAPTIVE, normally true after the adapter's explicit gate. |
| `bands_blocks` | Radial camera-relative Euclidean near/far/full centered transition width in Minecraft blocks. |
| `retained_ratios` | Medium/far fraction of the actual upstream work count, with `0 <= far <= medium <= 1`. Never radiance, density, radius or effect strength. |

The adapter also supplies `capability_valid` (pinned revision, explicit operation,
transport/capability schema and distance source), `quality_valid`, `protected_work`,
distance, its actual upstream count and legal floor. Quality validity and protection
must be established before expensive consumer work. The common mechanism does not
fetch textures to discover those inputs. Near/invalid/member protection may be
supplied explicitly; absent proof of quality must supply `quality_valid=false`.
Protected shadow/AO budgets still permit separately declared exact work reuse.

The underlying transport-safe numerical domain is unchanged: far <= 4096 blocks,
width <= 512 blocks, positive width >= 1/1024 block, distance <= 16777216 blocks,
count <= 16777216. Invalid or unsupported values retain the caller's original
count, including an invalid negative count; callers must preserve their original
path rather than loop over malformed inputs. This narrower domain is explicit;
the Java API accepts other finite distances and larger integers.

Transitions match the Java model: 32/96/8 has centered intervals 28–36 and 92–100.
Before the first interval the ratio is 1. Zero width changes tiers exactly at the
boundary. Touching transitions are allowed; overlapping/negative/nonfinite bands
are rejected. The helper does not reinterpret the near boundary or substitute
thresholds. A consumer requiring full work through a larger near region supplies
its own declared protection guard.

`ShaderlodWorkDecision` separates:

- `retained_ratio`, `medium_blend`, `far_blend`: continuous distance request.
- `continuous_requested_work`: binary32 upstream × ratio before legal floor.
- `continuous_work`: continuous request clamped to the legal floor and upstream.
- `requested_work`: upward integer rounding before legal floor.
- `realized_work`: integer count after legal floor, bounded by upstream. This is
  negotiated numerical work, not evidence of executed GPU operations.
- `upstream_work` and `reason`: original count and eligibility/fallback diagnostic.

Reasons are POLICY=0, INVALID=1, GLOBAL_DISABLED=2, FULL=3,
UNRESOLVED_INHERIT=4, EFFECT_DISABLED=5, PROTECTED=6, UNSUPPORTED=7,
QUALITY_FALLBACK=8. Numerical/protocol validation precedes other reasons; a valid
disabled/full/protected/unsupported path returns unity ratio, original counts and
zero transition weights. The original/mod-absent and disabled baseline therefore
retains the pack's selected upstream operation. Binary32 arithmetic can cross an
integer threshold relative to Java doubles; tests explicitly bound that difference
rather than claim bit-exact driver execution.

## Concrete continuous volume call

The existing primary volumetric integration is the bounded reference consumer.
Construct `ShaderlodWorkPolicy` from its resolved core/effect controls, actual
upstream integration count and minimum legal cells. Call:

```glsl
ShaderlodWorkDecision work = shaderlod_work_decide(policy,
    declared_volume_capability_valid, interval_and_quality_valid,
    protected_volume_view, radial_entry_distance_blocks,
    int(original_cells), int(minimum_cells));
```

`radial_entry_distance_blocks` must be the length of a camera-relative entry
position in blocks, with any adapter origin shift applied explicitly. A volume
endpoint's view Z or a surface depth alone does not establish this distance.
`interval_and_quality_valid` must include the existing finite positive interval,
resolved feature scale, final-pixel footprint and protected-view weight checks.
A typed capability schema may provide `shaderlodCapabilitiesVersion == 1 &&
shaderlodVolumeIntegrationAvailable == 1`; only the explicit adapter declares
those uniforms and validates its pinned source/options.

For the existing footprint/protected-view quality refinement, retain its declared
weight `footprint_weight * (1 - protected_weight)`, mix the full upstream target
toward `work.continuous_requested_work`, then apply its legal floor. Do not replace
this continuous target with `work.realized_work`: the existing
`shaderlod_volume_cell_length` and `shaderlod_volume_cell_sample` preserve the
whole integration interval with a last partial cell. A full fallback target must
select the original integration path, preserving jitter, density/extinction,
normalization and early exits. Continuous work is quadrature budget, never
extinction/scattering strength. A hard protection guard returns full before that
refinement. The consumer connection and its exact entry program remain root-owned;
this source freeze does not prove consumption.

## Discrete spatial rates

The optional rate helper takes a work decision, region member count, supported
mask, region validity and protection. Legal region sizes are 1, 4, 16. Rate side
lengths are 1, 2, 4; retained evaluations are 1, 1/4, 1/16 respectively. Mask bits
1/2/4 declare those rates; full rate bit 1 is mandatory. Region size and count
must agree with the upstream work decision. The legal floor must also permit the
requested rate's exact evaluation count.

`requested_rate=0` means an intermediate continuous request is unsupported.
There is no snapping or dithering. An unsupported intermediate, missing declared
rate, insufficient region size, quality failure or protected member realizes rate
1 and the original member count. `requested_work` reports the original numerical
floor-clamped request; `realized_work` reports negotiated evaluations.
`requested_ratio` remains the continuous request and `realized_ratio` becomes 1
on fallback. Interpolation regions may therefore remain full until an exact
supported ratio is reached; motion/pop quality must be verified separately.

The existing `shaderlod_surface_region_eligible` can supply a reviewed 2×2/4×4
region-valid boolean. Its geometric continuity does not certify shadow, AO,
reflection or lighting continuity; the consumer must add its required guards.
No new unconsumed classifier or pack optimization is introduced here.

## Source acceptance and remaining evidence

`python3 -m unittest discover -s tests -p test_shared_lod_policy.py -v` passes
seven fixtures: owned source-order binary32 decisions; protection/modes/version
and invalid fallback; floors/zero/touching/minimum-width transitions; legal and
unsupported rates; 170 cases compared to actual compiled dependency-free Java
policy; and 18 complete GLSL 4.10 graphs preprocessed/parsed through pinned Iris.
Java receives the same transported binary32 scalar inputs, then evaluates the
oracle in double precision. The fixture allows <= 2e-7 ratio error and <= 1 integer
item at quantization thresholds. The source fixture is deliberately limited and
is not a GLSL virtual machine or a GPU driver.

The grammar fixture checks repeated include guards, malformed-parameter rejection
and no Iris boolean/string option leakage. Pins: Iris jar SHA-256
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`,
embedded glsl-transformer 2.0.1 jar
`b8ca5e61b41ae18ddd5d4d5d6785cb9acd8fd7ac0c5c0b0689b872b039c11b9f`,
EnhancedParser class
`11133ca7cbfbc89777b15f06a31323b26c1ab6448b1822f4086e9503f3527814`.
The existing effect helper's guard changed from `#ifndef` to `#if !defined` to
avoid exposing an include guard as an Iris option; numerical functions are unchanged.

No client, shader reload, GL driver execution, main/dev compilation or GPU job
was launched by this owner. The runtime operator retains exclusive client/GPU
ownership. Consumer driver linking/readback, physical cell/evaluation counts,
full/disabled parity, DH boundaries, still/motion quality and repeated matched
GPU/total-frame timings remain required. No speed or quality claim follows from
these static checks. Measure original Photon, shared-consumer-disabled and enabled
arms at matched inputs/options/resolution/hardware with repeated GPU/total-frame
samples; retain quality and compatibility gates independently. No owned live
process survives this source acceptance.
