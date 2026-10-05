# Per-effect work policy

The dependency-free `com.pp.shaderlod.lod` model describes requested work caps for
explicit shader adapters. It does not register Iris uniforms, read NeoForge config,
patch GLSL, or establish shader consumption. The new general LOD goal uses Photon
as its reference consumer; another pack would need an independently reviewed
adapter and its own runtime evidence.

## Immutable model

`EffectPolicy(enabled, preserveSalientLighting, bands, mediumWorkRatio,
farWorkRatio)` snapshots one override. `DistanceBands(nearBlocks, farBlocks,
transitionBlocks)` uses camera-relative Euclidean distance in Minecraft block
units. Transition width is the **full** width centered on each boundary, matching
the existing core contract: 32/96/8 means transitions at 28–36 and 92–100 blocks.
Smoothstep blends retained work from 1 to the medium ratio, then to the far ratio.
Zero width changes tiers exactly at the boundary. Bands cannot overlap or extend
below zero, and endpoint arithmetic must remain finite.

Ratios satisfy `0 <= farWorkRatio <= mediumWorkRatio <= 1`. They cap the fraction
of an adapter's actual upstream work count; they are not universal sample counts,
effect strength, radius, shader quality preset, or screen resolution. Constructor
validation rejects malformed configuration. Runtime decisions preserve original
work for global disable, effect disable, protected lighting, invalid/null effect,
non-finite/negative distance, negative work count, or an invalid retained floor.

The protection flag is part of the immutable policy. With it enabled, shadow
filtering and ambient occlusion keep their upstream budgets even if smaller ratios
were configured. Other effects still need their own quality review: the flag does
not imply that all reflection, water, volumetric or material reductions are safe.

| Effect | Stable uniform key suffix | Default block bands | Protected lighting |
| --- | --- | --- | --- |
| Shadow filtering | `ShadowFiltering` | 32 / 96 / 8 | Yes |
| Ambient occlusion | `AmbientOcclusion` | 32 / 96 / 8 | Yes |
| Reflections | `Reflections` | 32 / 96 / 8 | No |
| Volumetrics | `Volumetrics` | 32 / 96 / 8 | No |
| Water surface | `WaterSurface` | 96 / 256 / 32 | No |
| Materials | `Materials` | 32 / 96 / 8 | No |

Do not serialize enum ordinals. These suffixes are stable identifiers; an adapter
must document which operations each broad effect category actually covers.
Volumetrics does not imply every fog/cloud operation shares the same useful bands
or even has a valid surface-distance input.

`defaultsFor(effect)` disables the new override, sets both ratios to 1, and enables
lighting protection. 32/96 are project starting points, not measured optima; water
96/256/32 comes from existing Photon water bands. Unity/off defaults reflect the
current evidence: the recent five-effect pilot regressed, lower shadow/AO budgets
lost salient distant structure, and a far reflection skip produced a visible water
band. No new quality reduction is promoted by this API. See
[STATUS](STATUS.md), [DECISIONS](DECISIONS.md), and the
[Photon glint audit](PHOTON_GLINT_SOURCE_AUDIT.md).

## Explicit adapter use

```java
var policy = new EffectPolicy(true, true, new DistanceBands(32, 96, 8), 0.5, 0.25);
var budget = policy.decide(globalEnabled, Effect.REFLECTIONS,
        cameraRelativeDistanceBlocks, upstreamTraceSteps, minimumLegalTraceSteps);
int requestedTraceSteps = budget.workLimit();
```

This example requests a reduction; it is not a recommended Photon preset. The
adapter supplies the actual upstream count from the selected pack options and
feature path. It also supplies the legal retained floor: zero permits skipping
all work; one preserves at least one iteration. A zero upstream budget remains
zero. Invalid upstream counts are returned unchanged so the caller retains its
original path rather than inventing a valid-looking reduced budget.

Quantization uses `ceil(upstream * retainedRatio)` and clamps to the legal floor
and upstream count. Apart from the declared floor, rounding retains less than one
additional work item above the continuous requested count. A cap cannot increase
the upstream count, including `Integer.MAX_VALUE`. `WorkBudget.retentionRatio`
reports the continuous request; `workLimit` is the integer result after rounding
and the floor. `reason` describes policy eligibility, and `reduced()` reports only
that the requested integer limit shrank. Neither proves fewer physical GPU
operations.

Shader ports must reproduce smoothstep, finite-input guards, non-overlap, protected
lighting, upward integer rounding and bounded caps. Drivers generally evaluate
GLSL floats with lower precision than Java doubles; transport must validate its
float range and use source fixtures plus actual driver/readback checks. This Java
model is an oracle for intended policy, not proof of bit-exact GLSL execution.
An adapter that changes loop counts must also preserve its integration interval,
normalization, upstream early-exit rules and any minimum legal refinement work.
It must not replace semantic work solely because an effect enum exists.

Exact work reuse is a separate capability. Reusing an identical texel/depth fetch
while preserving original iteration positions and evaluation math can remain
eligible when shadow/AO budgets are protected or ratios are 1. The work-cap model
neither enables nor forbids that independent adapter optimization. For example,
duplicate AO taps can share an identical depth fetch but cannot skip horizon math
that still depends on the original fractional ray position.

## Registered additive extension: source state

The integration source now registers a separate NeoForge client config and Iris
uniform extension. `ShaderLod.java` registers `ShaderLodEffectConfig.SPEC` in
`shaderlod-effects-client.toml`. Its six `effects.<effect>` sections use enum names
in lower snake case: `shadow_filtering`, `ambient_occlusion`, `reflections`,
`volumetrics`, `water_surface`, and `materials`. Each section exposes `mode`,
`near`, `far`, `transition`, `mediumWorkRatio`, `farWorkRatio`, and
`preserveSalientLighting`. Defaults are INHERIT, the table's block bands, unity
ratios and lighting protection enabled.

The config spec bounds near to 1–4095 and far to 2–4096; snapshot conversion
then raises far to at least near+1. Transition's config range is
0–512; conversion additionally caps it to `min(near*2, far-near)`. Far ratio is
capped to medium ratio. These are deterministic normalization rules in the config
integration, while direct construction of the pure API still rejects malformed
input. The pure API supports finite positive distances beyond those config ranges.
Snapshot construction itself does not catch invalid/non-finite raw config values;
the config spec is responsible for validating persisted entries. Do not infer a
transport exception recovery path from the numerical API's invalid-runtime guards.

For shadow/AO with protection enabled, the config snapshot publishes both ratios
as 1 while retaining the immutable policy protection flag. Protection is therefore
baked into the transmitted ratios; there is no separate protection uniform. This
protects quality budget counts and does not disable independent exact work reuse.

`EffectUniformsMixin`, listed in `shaderlod.mixins.json`, injects at the tail of
pinned Iris `CommonUniforms.generalCommonUniforms`. It registers 37 PER_FRAME
fields: one integer `shaderlodEffectsVersion` with value 1, plus these six fields
for each `shaderlod<Suffix>` prefix in the effect table above:

| Field suffix | Iris registration | Source value |
| --- | --- | --- |
| `Mode` | Integer | Explicit mode protocol value 0/1/2 |
| `Near` | Float from double supplier | Validated `nearBlocks` |
| `Far` | Float from double supplier | Validated `farBlocks` |
| `Transition` | Float from double supplier | Validated full `transitionBlocks` width |
| `MediumWorkRatio` | Float from double supplier | Validated/protected medium retained ratio |
| `FarWorkRatio` | Float from double supplier | Validated/protected far retained ratio |

For example, the exact AO names are `shaderlodAmbientOcclusionMode`,
`shaderlodAmbientOcclusionNear`, `shaderlodAmbientOcclusionFar`,
`shaderlodAmbientOcclusionTransition`, `shaderlodAmbientOcclusionMediumWorkRatio`,
and `shaderlodAmbientOcclusionFarWorkRatio`. Enum ordinals are not used for modes.
The existing seven core fields, including `shaderlodBridgeVersion=2` and global
`shaderlodEnabled`, retain their existing registration and semantics.

An `EffectUniformFrame` instance belongs to each registration group. Its frame
notifier callback clears cached controls; the first queried per-effect supplier
lazily takes one immutable map snapshot, and subsequent suppliers in that group
reuse it for the frame. Registration of the constant extension version alone does
not query the config. This is source-level supplier behavior, not a measured
overhead reduction or proof that a particular program linked these uniforms.

No Photon adapter consumption, driver linking/readback, config reload behavior,
runtime mixin application or performance gain has been established by this source
review. Build acceptance and the GPU/runtime owner must record those checks
separately.

| Mode | Intended adapter behavior |
| --- | --- |
| 0: INHERIT | Keep existing adapter/core policy; additive defaults use this mode. |
| 1: FULL | Retain the pack's original work for this effect. |
| 2: ADAPTIVE | Apply the validated custom policy when global enable and protection permit. |

`EffectPolicy.enabled=false` means retain original work at the numerical layer;
it does **not** mean INHERIT. Resolve mode before invoking the custom numerical
model. Global disable selects the original pack work even when a per-effect mode
is INHERIT. Unknown protocol versions/modes, unsupported pinned pack revisions,
absent mod, missing transport, or invalid converted fields must select the
original pack path. A declared effect name is not evidence that any path consumes
its ratios, and missing capability must not silently become a partial override.

Record adapter declarations separately from actual consumption:

| Adapter declaration | Consumption evidence required before acceptance |
| --- | --- |
| Pinned pack revision/source and maintained patch hashes | Selected archive and linked program/source identity |
| Protocol version, supported fields and effect/suboperation mapping | Driver link/readback of those exact fields |
| Distance source/units, upstream count source, legal floor and quantization | Original/off/on in-world behavior and config/reload checks |
| Quality cap versus exact reuse capability, lighting guard behavior | Relevant loop/fetch/work evidence and protected/full-path controls |
| DH paths supported or unsupported | Actual DH-present/absent rendering and boundary checks |

Keep this as an explicit adapter contract; no automatic GLSL rewriting or
capability registry is introduced. The next integration should consume one
measured effect in pinned Photon sources, retain original/mod-absent and disabled
controls, and report compilation, work avoidance, repeated GPU/total-frame timing,
and transition/motion quality separately.

## Checks and ownership boundary

`DistanceBandsTest` and `EffectPolicyTest` cover transition endpoints/centers,
zero-width/touching bands, invalid configuration/runtime inputs, off states,
lighting protection, caller floors, zero work, large integer counts, stable keys,
and bounded monotonic quantization across representative distances.

Root can run `./gradlew test --tests 'com.pp.shaderlod.lod.*'` after the source
freeze. The worker deliberately did not invoke Gradle, a compiler or a Minecraft
client while the first team wave was running. These tests are source deliverables
until root records their execution. No shader compilation, live transport,
quality acceptance or performance gain is established by this API.

Integration acceptance should also exercise all default INHERIT modes without
changing the core config; cross-field normalization (far<=near, overlapping width,
far ratio>medium); protected AO/shadow ratios under ADAPTIVE; unprotected ratios;
and FULL's distinction from INHERIT. A controlled reload test should confirm one
protection value is captured per effect snapshot and that within-frame suppliers
reuse one control map while the next frame observes new config. A fake uniform
holder can check all 37 exact names/types if the pinned Iris test seam permits it;
source assertions alone do not establish runtime mixin application. A diagnostic
pack must link and read back the extension before claiming actual transport, and
ordinary effect code must consume it before claiming adaptation. These are test
recommendations, not worker-executed checks.
