# Miniature seven-field private consumer: source handoff

Implemented 2026-10-05 in five new scoped files only. Existing core cue/refinement
sources/builders, Java/config/transport, shared helpers, Photon and other adapters
remain unchanged. Root owns all builds, driver compilation, reloads, images and
GPU measurements. This worker runs lightweight source/math/evidence fixtures
only; no full pack ZIP, compiler, client, encoder or GL call occurs.

Experimental derivative of **Miniature 2.19**, **Mateus A. Kreuch**,
Copyright (c) 2022, MIT:
[official source](https://github.com/mateuskreuch/minecraft-miniature-shader),
[release](https://modrinth.com/shader/miniature-shader/version/LWmZ94RG).
Exact original release SHA256
`5f224e907657f3ffd95d37e37c27e555deef64fa56da1e13374f2c295241d7f0`.
Its omitted full MIT notice is restored from existing LICENSE.Miniature,
SHA256 `397d80246d261805b953865428e9c244e5a7bd2bbbb6f3b37f16e884e697e4d1`.
Editable source, complete literal patches, original attribution and this
modification notice are packaged; new source modifications use MIT terms.

## Scope and defaults

New builder: scripts/build_miniature_core_config_probe.py. Exact CRLF patch
manifest: patches/miniature/2.19-core-config-probe.json. New shader helper:
shaderpacks/miniature-core-config-probe/shaders/common/shaderlodCoreConfiguration.glsl.
Source/math fixtures: tests/test_miniature_core_config_probe.py.

Exactly four upstream members change: shader.h, program/final.fsh,
shaders.properties and lang/en_US.lang. The original getReflectionColor.fsh
member is retained byte-for-byte. Original mask, normal/roughness/jitter,
transformations/math, outer march/adaptive stride, first-hit bracket, cutoffs,
vignette/fresnel, miss/depth-acceptance logic, caller composition, lighting and
shadows remain original. World final wrappers still include program/final.fsh.

Both shader controls default0:

| SHADERLOD_MINIATURE_CORE_CONFIG_MODE | Behavior when qualified/eligible |
| ---: | --- |
| 0 | Literal original call; new helper/uniforms/branches excluded |
| 1 | One full-four explicit-sampling control; no distance-selected output |
| 2 | One fixed-four refinement chain with retained transition endpoints; no refinement-fetch savings claim |
| 3 | One chain bounded by maximum actually needed endpoint count, from2/3/4 |

SHADERLOD_MINIATURE_CORE_DEBUG_ALLOW0 excludes palette selection. Allow1 permits
the existing live mod debug flag to select a separate requested-budget view.
Unknown mode selects original; native binary count other than4 or outer count
other than10 excludes the helper and retains that native original count. A
different native stride also returns original. No per-effect mod field or
universal shader integration is invented.

## Seven existing fields and strict normal fallback

| Existing core field | Type | Consumption |
| --- | --- | --- |
| shaderlodBridgeVersion | int | Exactly2 only |
| shaderlodEnabled | int | Exactly0/1; only1 eligible |
| shaderlodPreset | int | Exactly0/1/2; not clamped |
| shaderlodNear | float | Radial centered full→middle threshold |
| shaderlodFar | float | Radial centered middle→far threshold |
| shaderlodTransition | float | Each band's total centered width in blocks |
| shaderlodDebug | int | Exactly0/1; diagnostic only when permission1 |

Every normal fallback decision depends only on uniforms/native compile-time
options, before active ray work. Absent/old1/future3, unknown enabled/debug ints,
unknown preset, invalid bands, global disable or unqualified native stride
return the untouched original getReflectionColor function, including its original
implicit sampling source. Unknown flags are rejected even if their diagnostic
permission is off. Original is also selected on invalid fields/disable while
debug1: latest strict fallback requirement takes priority over the plan's
optional disabled/error status colors. Existing standalone cue remains a
separate diagnostic for absence/disabled status.

Band validation uses positive bounded conjunctions: near1..4095, far2..4096
and greater than near, width0..512 and at most min(2*near,far-near). NaN ordered
comparisons fail; infinities fail finite bounds. No isnan/isinf syntax is needed,
and malformed fields are never silently clamped. Java's raw config repairs are
different from invalid GPU uniform cases; source fixtures cover the latter.

Current actual enum ordinals map as follows; these are private Miniature
refinement budgets, not native profile names or accepted cross-pack quality:

| Ordinal/preset | Full | Middle | Far |
| --- | ---: | ---: | ---: |
| 0 BALANCED | 4 | 4 | 3 |
| 1 PERFORMANCE | 4 | 3 | 2 |
| 2 AGGRESSIVE | 4 | 2 | 2 |

No one-step kernel or outer ray cut is introduced. Mode1 remains full4 for all
valid presets; fields still participate in its validity contract. Modes2/3
select budgets from originating length(viewPos) in camera/view-space blocks,
not feet/world origin, raw/axial depth or reflected endpoint distance.

## One chain and transition result algebra

W=0 uses d<near full, near≤d<far middle and d≥far far. Positive W centers bands
at near/far: [near-W/2,near+W/2] and [far-W/2,far+W/2]. For96/160/W8 these are
92..100 and156..164. Full near output is assured through the lower edge; the
near transition straddles the ideal near threshold. Valid widths prevent overlap.
Smoothstep weights require no temporal noise or new accumulation assumption.
Equal adjacent budgets collapse to one count/result; pure weights return the
endpoint directly. Smooth weights do not guarantee moving-hit quality.

On an eligible first hit inside the native refinement cutoff, classify the
originating viewPos and run one original midpoint chain. Preserve the initial
oldPos/curPos bracket and every original a/b update. Capture UV/depth at the
needed counts2/3/4. Mode1 always4. Mode2 always4 even when the returned result
uses count2 or3; it is a transition-quality diagnostic. Mode3 stops at maximum
needed count; pure2/3 can avoid two/one depth refinements. Near4 and a4→lower
transition still execute4. Misses/cutoff failures do no policy work and keep
their native return. There is no second complete SSR trace or path blend.

Evaluate one endpoint color outside a genuine unequal-count transition, at
most two inside one; each endpoint independently preserves the native final
depth guard/vignette/fresnel. Invalid-hit endpoints return native zero RGBA.
For a transition, blend premultiplied reflected RGB contribution and opacity,
then divide contribution by nonzero opacity to reconstruct the native return.
Exactly zero opacity returns zero; pure endpoint weights return that endpoint
without reconstruction. Nonzero signed opacity is not silently clamped, retaining
the algebraic native composition contract. For physical nonnegative opacities,
zero implies no contribution. A signed cancellation with nonzero contribution
cannot be represented by native zero-alpha RGBA and is outside qualified
physical endpoint assumptions; quality inspection must catch abnormal values.

Independent fixtures compare the original caller's composed color against the
weighted composition of both endpoint results, including valid-to-miss and
both-zero cases. Straight independent RGB/alpha interpolation is deliberately
not used, avoiding the artificial darkening of valid-to-miss blending.

Every active depth/color fetch is texture2DLod(...,0.0). The new budget/loop
branches do not rely on implicit derivatives. Original fallback's branch is
dynamically uniform; it preserves the upstream sampling pattern. Existing
caller samples before the dispatcher remain unchanged. No sampler or texture
binding is added. Endpoint storage, loop breaks, extra color samples, division,
divergence and register/code layout may offset depth savings; physical executed
counts are unverified and no performance gain is predicted.

## Debug meaning and limits

Only fully valid/enabled protocol2 plus DEBUG_ALLOW1/debug1 selects debug.
Palette is requested budget4green,3yellow,2red, smoothly blended with transition
weights. This is a budget palette rather than a distinct geometric-zone color
when adjacent zone budgets are equal; BALANCED middle remains green and
AGGRESSIVE middle/far remain red. Native caller reflectivity/composition still
affects displayed colors. Debug bypasses SSR and is intentionally not normal
quality, actual hit/kernel coverage or GPU performance evidence. Debug0 returns
normal work; DEBUG_ALLOW0 must be used for cost measurements. All invalid/
disabled cases return original, so no untrusted debug value paints an error cue.

## Qualification, build and evidence boundary

Assembly is gated by exact original sampler reportb04ab014… and companion
workload3e154928… (full hashes/paths in manifest), original NORMAL profile,
minimal stack/mainmod+DH absent, native1488×836/no actual GPU timing, linked
program16 sampler tuple and actual loaded Iris artifact/class hashes.
Nonmip MIN/MAG filters match: depthNEAREST, colorLINEAR; samplerobject0,
aniso1/bias0/base0. LOD±1000/MAX_LEVEL1000 is not level0 clamp; allocated mip
images/completeness remain unqueried. These baseline facts qualify a controlled
trial, not this derivative's actual samplers or pixels.

Inspected loaded Iris jar3e3f94d2… translates the legacy/no-profile GLSL120
composite to330core and maps texture2DLod→textureLod. Earlier private explicit4
rendered; this new endpoint loop/validation/debug code has not compiled on the
driver. Preserve failures rather than switching to implicit active sampling.
The builder imports existing read-only qualification/archive validators, edits
none of their sources, rejects changed pins/anchors/collisions/overwrite and
uses deterministic ZIP order/timestamps/permissions/compression. Full MIT
license, source/ancestry, complete patch and this notice accompany output.

Root build after source freeze:

```
python3 scripts/build_miniature_core_config_probe.py
python3 -m unittest discover -s tests -p test_miniature_core_config_probe.py -v
```

Ignored output: build/Miniature-ShaderLOD-CoreConfigProbe-2.19.zip, mode0 and
debug permission0. This worker builds only tiny fixture ZIPs; no full probe
archive or shader parser/compiler is run. No new runtime field-consumption,
normal-image parity, transition quality, actual refinement count or timing
result is established by this handoff.

Root extended its dev-only readback inventory to seven core fields with
core_uniform_schema2 and compiled that diagnostic while all clients were
stopped. This remains preparation: new actual normal linked-program values
are required. The historical pinned sampler report is unchanged. Its first-
draw-only scope cannot establish every later live config value by itself.

Root subsequently accepted all seven values in a separate Photon diagnostic,
numeric program21: version2/enabled1/PERFORMANCE1/near32/far96/transition8/debug0,
report SHA2563640bae4bc260d8775024062c8aad51253fb5a30e06dc9d0a0d5c33fa030f4cb.
This verifies that diagnostic on Photon, not this Miniature program, later
reload values, source stage or physical budgets. No Miniature runtime claim is
transferred from it.

Worker source checks: **8 lightweight tests passed**. They cover all seven
types, source flag/version/preset rejection predicates and nonfinite/invalid
band boundaries, centered/equal/zero-width policy cases, independent native
composition algebra over1000 endpoint cases, one-chain prefix/stops, original
pre-hit march/fallback/composition preservation, mode0 guarded source projection,
qualification/source/overlay/anchor guards and deterministic tiny fixture ZIPs.
Pinned real evidence/release were checked read-only and patches applied in
memory. These are static/math fixtures; no shader execution/counts are measured.

## Remaining root acceptance

Verify original/mode0/globaldisabled and all native/protocol/flag/band fallback
contracts with debugoff. Inspect all seven linked types/values in the new normal
program; old four-field/cue reports do not verify this consumer. Cycle each
actual preset, transition0/8/32, near/far96/160 then distinct thresholds and
return, recording settled actual config/GPU values and source/artifact hashes.
With debug permission fixed1, change mod debug0→1→0 without source/option reload
and verify normal image restoration. Configuration suppliers are per-frame,
not an asserted atomic transaction. Unknown/unreachable uniform cases remain
source-only until a separately controlled runtime diagnostic exercises them;
do not modify reserved production transport to create them.

First compare mode1/full4 explicit to original/off near/reflection pixels.
Then mode2 quality before mode3: slow band crossings, thin geometry, grazing
water, reflected blocks/glass, missed hits, underwater/third-person/view bobbing
and moving occluders. Check blending for darkening/ghosting and both band edges.
Debug color indicates requested count, not a qualifying hit; use independent
count/pass evidence for actual savings. No DH program exists; DH compatibility
requires its own check.

After driver, sampler, quality and consumption checks, repeat balanced serial
original/mode0/mode1/mode2/mode3/native-lower controls, DEBUG_ALLOW0 and matched
scene/options/config/stack/resolution. Retain failures, GPU median/tails plus
CPU/frame clocks and preferably SSR/final-pass scope/eligible-hit coverage.
The prior4.5430/4.5432ms distance pilot was flat, and explicit4 previously
regressed in a single pilot; neither is replaced by this source work. Ordinary
≥15% performance and broad configuration/support/quality gates remain unmet.


Root integration: all eight current source/math fixtures passed. Qualified
private ZIP assembled with exact source/sampler/Iris gates:
 build/Miniature-ShaderLOD-CoreConfigProbe-2.19.zip
SHA256e0e6a037f586f4ac0dbc1e264f52516675740d64d4c5e2e8914eaac6f3bc7552.
111members, defaultmode0, restored MIT notice and four reviewed upstream member
changes. Embedded source-only validation remains explicitly uncompiled/unrendered;
root actual driver/normal seven-field/quality/refinement-count evidence is pending.
Do not rebuild this frozen archive merely because runtime prose later changes.
The existing Photon seven-core readback verifies the dev diagnostic, not this
Miniature consumer. No main/transport/shared-source changes were introduced.


## First driver smoke: temporal request rejected

The frozen MODE2 archive compiled/linked and rendered without fallback in root
exec88931, which exited0. Numeric program16 read back all seven expected fields:
version2/enabled1/presetPERFORMANCE1/near96/far160/transition8/debug0. This is
first-draw data, not a later live-change observation or refinement count proof.

The generic smoke request incorrectly required fixed wetness0. Temporal validation
rejected it because no wetness supplier was observed. Exact frozen shader source
has no wetness token; this pack cannot be assumed to consume Photon's weather
control. The original request/report/log/readback are retained with a rejected
contract note under dev/logs/miniature-seven-core-quality-smoke-r1. No tolerance
or temporal guard was relaxed. A new profile/request uses normal shader weather
(fixed_wetness=null, no fixedWetness JVM property), with actual clear scene/rain
recorded. Rootexec28594 retry is running; full smoke acceptance remains pending.
No source/ZIP/Java changes or performance inference follow from the retry.
