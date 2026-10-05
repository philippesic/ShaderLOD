# Far opaque lighting-region cost control

Source/parser freeze: 2026-10-05. **Quality-changing regional cost control**;
not an optimization, isolated diffuse budget, strict savings ceiling or evidence
of quality-equivalent LOD. It tests whether the eligible region merits the
prepass/resolve strategy in [DISTANT_LIGHTING_RATE_PLAN](DISTANT_LIGHTING_RATE_PLAN.md).
No new pass, buffer, cache, per-effect transport or API version is implemented.

## Pins and exact intervention

Original Photon v1.3b by Benjamin Stott (SixthSurge), version ID `gUv7fBPN`, ZIP
SHA-256 `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Original LICENSE is retained, SHA-256
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
Preserve [Photon attribution](../patches/photon/ATTRIBUTION.md); mod MIT terms
do not replace the upstream shader license.

Only three upstream members change:

| Member | Original SHA-256 | Scope |
| --- | --- | --- |
| `shaders/program/d4_deferred_shading.fsh` | `212081e707c20c78e17cbfd3380b2f22eb696cc1aa14bdf006c07d59f53f67e0` | Eligibility; AO/shadow/diffuse regional fork |
| `shaders/include/lighting/shadows/pcss.glsl` | `c12e98fa923306687bfa5ac2b00214d45b1614ebafb719e22839f3420750a76b` | Three private-profile shadow comparisons use explicit level 0 |
| `shaders/settings.glsl` | `93401d268fe859cc1df81929ad41156757a98db80f80cf32af80d9cdca96c394` | Default-off private option |

Entry: `world0/deferred4.fsh` → `program/d4_deferred_shading.fsh`.
`SHADERLOD_FAR_LIGHTING_COST_CONTROL` defaults off and uses an Iris-recognized
outer `#ifdef`; stage/profile guards use compound `#if defined` expressions to
avoid registering internal/stage options. Only the reduced Overworld
`PROGRAM_DEFERRED4` profile applies: normal/specular mapping, colored shadows,
VPS, colored lights/LPV, SH skylight, DH, VOXY, TAAU and blocky clouds must be off.
Private off or unsupported profile preprocesses to every original member byte.

The original full-resolution material decode and rain/material processing execute
before eligibility. Eligible receivers then skip all eight AO/AO-depth prefetches,
AO reconstruction, filtered shadow/SSRT calls and `get_diffuse_lighting`.
They use AO=1, bent normal=current normal, ambient SSS=0, shadows=1, SSS depth=0
and declared diffuse coefficient `vec3(0.25)*material.albedo/pi`.
These values intentionally change lighting; they are not calibrated irradiance.

Original per-pixel light products, highlight/reflection functions, edge highlight,
fog, cloud/rainbow composition and Purkinje shift remain common source outside
the fork. Current albedo, normal and material resolution are preserved. However,
highlight illumination also receives changed AO/shadow inputs, and downstream
image/history/early-exit behavior can change. The control does not isolate diffuse
arithmetic or establish a strict upper bound on removable cost.

Noneligible receivers retain original AO/shadow/diffuse expressions. In the private
profile AO prefetch moves after material decode into the fallback branch; this
changes latency scheduling and can affect OFF timing. Exact default-off source
parity and expected noneligible output parity are distinct checks.

## Eligibility and disabled baseline

Use existing `shaderlodBridgeVersion`/`shaderlodEnabled` as integer uniforms and
`shaderlodNear`/`shaderlodFar`/`shaderlodTransition` as floats. Activation requires
version 2, enable exactly 1, finite near≥1, far>near, and centered total transition
width between 0 and far−near. Reject nonfinite resulting edge or view distance.
The width must also be at most twice near, matching the core contract; the full
bound is `width <= min(2*near, far-near)`.
Distance is radial camera-relative view-space blocks, not world height or view Z.
Require `distance >= far + width/2`: near and the entire transition band remain
full quality. For core 32/96/8, the diagnostic starts at 100 blocks. Its hard
cutoff has no quality interpolation and is not a proposed production transition.

Require non-hand, outside water, exactly dry finite wetness, finite position/normal
and no legacy overlay. Raw material mask must be 0 **and** decoded fields must be
nonmetal/non-hardcoded-metal, zero SSS/sheen/porosity/emission/reflection multiplier,
finite albedo/f0, roughness within 0.75…1 and maximum f0≤0.04. The pinned mask-0
material branch has ordinary defaults; checking decoded fields independently
prevents a mask alone being treated as a full material guarantee. Sky follows its
original outer branch. Missing/unknown core, invalid bands or rejected receivers
take original lighting; no shader-option distance fallback is invented.

These five core fields control the regional intervention. A sixth existing
integer field, `shaderlodDebug`, controls only the optional coverage cue. Preset
and per-effect policy remain unconnected. Coverage is unmeasured and may be very
small: foliage SSS, water, glossy surfaces, near geometry and unsupported features
fall back. A flat result without demonstrated eligible far receivers does not
establish that opaque lighting generally costs little.

## Optional untimed coverage cue

With a compatible private profile, bridge version 2 and `shaderlodDebug == 1`,
the end of the common opaque d4 branch replaces the completed color with green
for eligible receivers or red for rejected receivers. This occurs after material,
derivative/edge, highlight/reflection, fog/cloud/rainbow and Purkinje composition;
it introduces no new samples or derivatives. Sky and hand retain original colors.
Global OFF or rejected bands/materials produce red opaque cues, rather than
claiming that those receivers bypass work. Unsupported profile, private OFF,
unknown/absent bridge, and debug values other than exactly 1 show no marker.
Normal debug 0 retains the regional cost-control output and classification;
the tightened malformed-band validation is the sole eligibility correction.

The cue uses the existing mod debug field and adds no second Iris option. It
establishes qualitative eligible-receiver visibility only, not pixel counts,
physical shader execution, rates, quality parity or GPU savings. Later blending,
TAA, color grading and tone mapping can alter the RGB; do not use an exact byte
threshold as coverage proof. Run debug 1 with no GPU timing and read back the six
core values if needed; normal timed profiles must use debug 0 in separate runs.

## Derivative and sampler boundary

Edge highlight's derivatives and common full-resolution specular/composition stay
outside the new branch. AO/depth and SSRT use explicit `texelFetch`. To avoid
relying on implicit derivatives for original PCF reached through the new receiver
fork, only this private-profile PCSS helper uses
`textureLod(shadowtex1, comparison_coord, 0.0)`. Counts, coordinates, distribution,
radius, weights and upstream PCF early exits remain unchanged. This helper change
also applies to private global OFF and noneligible receivers; it is disclosed
rather than called byte-original fallback.

Read-only disassembly of pinned Iris JAR SHA-256
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`
established `PackShadowDirectives.SamplingSettings` defaults mipmap=false and
nearest=false. Pinned Photon has no shadow mip directives and requests hardware
filtering 1. `ShadowRenderTargets.getSamplerFor` selects `LINEAR_HW` for that
configuration; `GlSampler` sets MIN/MAG to LINEAR and changes MIN to mip filters
only when its mip flag is true. This supports the scoped base-level source policy.
Actual bound sampler/filter/anisotropy and near/global-OFF parity remain driver
checks; no sampler readback or runtime sampling-equivalence claim is made.

## Static acceptance and handoff

Owned new builder/manifest/tests/document only. Builder verifies original archive,
member/license hashes, unique literal anchors, expected private fingerprints and
exact modification scope; outputs only ignored `build/` ZIPs, preserving all
other original payloads and adding `shaderlod-lighting-cost-probe.json` metadata.

```sh
python3 -m unittest discover -s tests -p 'test_photon_lighting_cost_probe.py' -v
```

Ten source/scalar/parser fixtures passed in 0.551 seconds, no skips. They verify
all-member disabled/unsupported byte parity, retained original fallback expressions,
common material/specular/edge/composition source, fetch placement, three-member
scope/license, six typed core fields, eligibility boundaries/nonfinite fallback,
approved material fields, scoped PCSS level-0 normalization, rejected mutations,
optional final-cue placement/semantics, and actual pinned Iris option discovery/defaults/overrides. The pure lexer includes
modified d4/PCSS/settings plus the original stage entry; the public gate is the
sole added boolean/reference, with no stage/helper leak. Temporary Java source
launch uses existing dependencies; no Gradle, GLSL compiler, pack assembly, client
or GPU check ran.

Root/runtime operator reviews and assembles:

```sh
python3 scripts/build_photon_lighting_cost_probe.py --arm original
python3 scripts/build_photon_lighting_cost_probe.py --arm disabled
python3 scripts/build_photon_lighting_cost_probe.py --arm enabled
```

Outputs: `build/Photon-LightingRegionCostControl-{arm}.zip`.
Metadata keeps the existing immutable base/member/license/arm/switch contract,
`experiment=photon-far-opaque-lighting-cost-control`, six consumed core fields,
`quality_class=quality_changing_cost_control`, explicit `eligibility` and
`sampling_contract`, `coverage_cue`, coefficient 0.25/AO-shadow 1 diagnostics, and unverified
compilation/unmeasured benefit. Qualifiers must permit only this declared private
option difference and preserve all other effective-option guards.

First prove driver compile/no fallback, actual gate/core float/debug values, global OFF
and near/fallback behavior, scoped shadow sampling and visible eligible far rough
receivers using a separate untimed debug-1 cue. Then measure repeated quiet/focus-matched original/OFF/ON frame-only
GPU and total-frame intervals with balanced order and equal scenes/warmup. Keep
sampler/uniform diagnostics out of timed windows. A useful regional signal
justifies the actual spatial cache/quality experiment; flat or regressive controls
stop before pass-graph investment. Neither result is a quality-equivalent gain
or acceptance of changing receiver shadows in the product.
