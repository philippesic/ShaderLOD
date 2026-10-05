# Photon cloud-secondary extent-scale probe

2026-10-05 static handoff. Owner: fresh performance chat's bounded
`photon_cloud_guard` worker. Only this document, its new builder/manifest/tests
were written. Main/shared Photon helpers, patches, source tests and Java files
remain reserved to the original protocol chat. No complete ZIP build, compiler,
shader reload, client launch or GPU measurement was performed by this worker.
The root operator owns all such next steps after its live isolation runs end.

This implements the first conditional cloud probe in
[NATIVE_EFFECT_SOURCE_AUDIT.md](NATIVE_EFFECT_SOURCE_AUDIT.md). Root reports a
qualified native-window, one-JVM cloud-only OFF/ON/OFF/ON control with adjacent
GPU reductions of 4.52% and 5.84%. That result concerns the existing B15 cloud
module. It does not measure this new probe or establish a cost for secondary
extent setup. The prior approximately 8% all-module result is similarly not
evidence for this source change. No performance or physical-operation claim
is made here.

## Exact baseline and changes

Immutable input: `build/photon-lighting-tier-no-atmosphere.zip`, Photon v1.3b
B15 protected-lighting/no-atmosphere derivative, SHA-256
`b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c`.
The builder consumes this archive directly. It does not rebuild the baseline
from the original archive, read current shared overlays, or invoke the existing
Photon builder. Changed main-tree sources cannot silently become probe inputs.

Reviewed literal anchors and source/output fingerprints are in
[cloud-secondary-probe.json](../patches/photon-experiments/cloud-secondary-probe.json).
Only these archive members change:

| Member | B15 SHA-256 | Probe SHA-256 |
| --- | --- | --- |
| `shaders/include/sky/clouds/cumulus.glsl` | `a17f8fba86589b1b138063bbd3d1c261055f135b79ad4985579786abb1773d9c` | `05da1b9b1c68b7e7e7c45cb0b485871cf5a11035a8122bd0be0adc10d8ba9114` |
| `shaders/include/sky/clouds/altocumulus.glsl` | `d13de78fb87924295a4dd90af455a1bfa86776a708e1b461e2a158636d550255` | `35a189103069aa96ef5e6add78d1a3ea592a55a80958373a0f34f546eba36091` |
| `shaders/include/sky/clouds/cumulus_congestus.glsl` | `3e17ef9f8909861ec11a5d2bf8da7883333cb2816a3e1def2fed74d82d52f5b2` | `87c1acc1975fb3eb788a68c627488a993225d12aaf13bca332fffa8cfa653c86` |
| `shaders/settings.glsl`, probe off | `d1f571e5d96ecffdf7ebcfcf28a13d34dc5bfd5c3606702e06832f95f88aa3cd` | `22681254cf65e59e3b9b9b6a6c87954f62f99b4dded5fe2ca068083a344d66e0` |
| `shaders/settings.glsl`, probe on | same B15 source | `98af4a747a14e01702ce5c605df13c080fd69368df0ff0d84bf3ec814f0c8134` |

The three cloud files have five bounded replacements each: ratio helper and
conditional parameter, conditional ratio use, pre-primary setup, and the two
secondary call arguments. Settings has one new independent default-off option.
No shader budget or existing setting changes. Archive output is restricted to
ignored `build/`; the immutable B15 input cannot be overwritten. Fixed entry
order/timestamps/permissions and compression make outputs reproducible.

The Photon attribution and all license bytes remain present. This is a derivative
of SixthSurge/Benjamin Stott's Photon, whose license is separate from the mod's.
`LICENSE` SHA-256 is
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
The entire B15 `shaderlod-build.json` is retained byte-for-byte, SHA-256
`5653a52f590ce10cd75fbaff24f1ed053e49564ade7b4c79bb2747f69feed084`.
It describes the baseline; the added `shaderlod-cloud-secondary-probe.json`
records the probe arm, input/manifest/member hashes and unverified runtime state.
No publication/distribution was performed.

## Algebra and rendering semantics

Each B15 secondary optical-depth call initializes its base `step_length` using
the selected count. If selected and original counts differ, it multiplies by:

```glsl
(float(step_count) / float(original_steps)) *
    (exp2(float(original_steps)) - 1.0) / (exp2(float(step_count)) - 1.0)
```

Selected lighting and ambient counts are finalized by the existing mode/preset
selection before the primary march and never assigned inside it. Original
counts are compile-time pack options. This ratio therefore depends on neither
primary sample position nor secondary jitter, density, direction or sun time.
It is dimensionless. The probe evaluates this exact, left-associated expression
once for light and once for sky after final count selection, then passes the
values into the secondary function. Neither count division is cancelled, and
the expression is not changed to an equivalent differently rounded formula.

Each caller computes its ratio only under its explicit
`selectedCount != originalCount` guard. Extent variables initialize to 1.0, but
the optical-depth function retains the original inequality branch, so full-count
paths perform no identity multiplication. The secondary base-length division
and any altocumulus sunrise/sunset factor remain in the original function.
Equal-count near/tier-zero/live-disabled paths retain original count, initial
length and sample math. The full compile-time `SHADERLOD_CLOUDS`-off source tail
is untouched, and continues selecting the original Photon functions.

Primary origin, jitter, loop count, density ordering, transmittance break and
empty-sample skip are unchanged. Secondary position update, step doubling,
per-primary `hash2(fract(ray_pos))` light/sky jitter, density call and weighted
accumulation are literal B15 source. Scattering/output/history code is untouched.
No texture operation or derivative changes scope. There is no new LOD policy,
sample reduction, transition smoothing or quality approximation.

Distance tiering still uses B15 cloud-shell entry distance divided by
`CLOUDS_SCALE` to convert atmosphere coordinates to camera-relative world blocks,
with the existing near/far/transition bands and all live presets. Secondary
lengths remain in the existing scaled cloud-coordinate units (B15 labels them
metres). The geometric-series extent correction is retained. Temporal upscale,
frame progression, sampling noise and cached-sky policy are unchanged.

`PHOTON_CLOUD_SECONDARY_EXTENT_PROBE` is an independent compile-time pack option,
default off. It has no new Java configuration or uniform transport. Off selects
the literal B15 parameter/math/call sites and emits no new helper/setup code.
The structural selector tests recover every affected B15 source member exactly
after selecting only probe directives away. An additional `original` arm keeps
every baseline member byte unchanged. These are source restoration checks,
not proof of identical driver compilation. Original/disabled remains the quality
and performance fallback until real evidence supports promotion.

## Risks and static evidence

Moving pure setup earlier preserves its semantic value, but changes when it is
evaluated. A cloud-shell ray with zero dense samples previously never called
the secondary function; a reduced-count ray now performs its two count-guarded
ratio setups before discovering that all samples are empty. Sparse rays may
therefore regress. Full-count rays avoid ratio evaluation, but still carry the
guarded source/control shape. Additional caller values can extend live ranges
and affect register pressure. The compiler might already hoist or specialize
the B15 expression. No shader disassembly, register count, occupancy or issued
exponential/texture count is available. Source cleanup cannot establish savings.

The CPU float32 model preserves expression association and finds identical
secondary sample coordinates, final positions and weighted analytical-density
outputs in 702 fixtures across positive advertised counts 1–12, reduced/equal
counts, layer lengths, sunrise/sunset factors and jitters. It uses CPU `exp2` and
a synthetic density field; it is not GLSL execution or Photon texture output.
Driver optimization can differ across program shapes, so mathematical/source
parity still requires real visual and timing checks.

Static command:

```sh
python3 -m unittest discover -s tests -p 'test_photon_cloud_secondary_probe.py' -v
```

Nine tests pass, covering exact pinned input/license/metadata, disabled source,
unchanged archive members/primary body, ratio-expression identity, count guards,
one-per-ray placement, algebra fixtures and corruption/scope/output guards.
These checks read the immutable archive and manipulate source in memory; they
never assemble a full shader pack, invoke a compiler or touch a client/world.

## Operator handoff and stopping boundary

After the root GPU owner finishes current cloud/fog/water controls, it may build:

```sh
python3 scripts/build_photon_cloud_secondary_probe.py --arm original
python3 scripts/build_photon_cloud_secondary_probe.py --arm disabled
python3 scripts/build_photon_cloud_secondary_probe.py --arm enabled
```

Outputs are `build/Photon-CloudSecondaryExtentProbe-{arm}.zip`. Whole ZIP hashes
must be recorded then; none exist in this checkpoint. The root operator controls
all shader reloads. Actual entry points are Overworld `deferred1.fsh` →
`program/d1_clouds.fsh` → cloud includes, and cached-sky `deferred.fsh` →
`program/d0_sky_map.fsh` → sky/cloud includes. Verify both normal and cached-sky
compilation. Cirrus, sky-only sampling, fog, water and other dimensions remain
unchanged source members.

First establish actual Iris driver compilation and rendering with locked
Minecraft 1.21.1 / NeoForge 21.1.253 / Iris 1.8.12 / Sodium 0.6.13 / Photon
v1.3b and optional DH 3.3.3, recording exact loaded hashes/labels. Then verify
probe-off restoration, full near/live-global-disabled behavior, reduced medium/
far clouds, all B15 cloud modes/presets, cached-sky on/off, both ordinary upscale
4 and premium upscale 1, dense storm and sparse/empty clear rays. Match all
quality inputs, frame progression, resolution and terrain radius. Inspect moving
clouds, horizon pans, translation, band crossings and reload/history resets.

Only then run balanced repeated serial original-B15 / probe-disabled /
probe-enabled GPU controls with one client and warmed histories. Measure cloud
pass elapsed time and whole-render GPU time separately, with frame/render-thread
limits, plus both dense and sparse workloads. A cloud pass gain can leave the
whole frame unchanged. Preserve compiler failures and flat/regressing samples;
do not attribute B15's cloud-module gain to this ratio without these controls.
Use existing delayed nonblocking timers, without waiting/glFinish or concurrent
clients. Reject visible cloud/light changes, and close this hypothesis after a
qualified flat/regressing pilot; do not combine another rewrite to rescue it.
No production/shared integration is assigned by this handoff.


## First actual runtime pilot

Rootbuilt disabled/enabledarchives after priorisolations stopped. exec53149
exit0/all3accepted: B15baseline/probeoff/probeon, native2976×1672/minimal/DH,
cloud-only enabled andallothermodule/CPUexperiments off,40swarm20scapture/GPU1/16
/fixedwetness0. Baseline/off/onFPS63.76/62.56/48.77; GPU14.538/14.454/18.998ms,
thread9.345/9.494/9.080ms. RealIris rendering succeeded for all sources.
The enabled launch regressed strongly. Defaultoff; no cleanup/adoption from
source-math equivalence. No causal claim from one between-JVM block.

Rawdev/logs/photon-cloud-secondary-pilot-r1 retainsallreports/PNG/options/
archive/class/jarhashes. EnabledarchiveSHA256
a405f364691f264f389c7ccc9fbf8962c39eab5cc81249f023eeeca27a538216.
Next is a one-JVM globalOFF/ON/OFF/ON diagnostic on that same compiled probe:
full-count phase avoids ratio math; enabledcount phase uses hoist. It does not
isolate pure hoist from B15 budgetchange and cannot replace disabled/enabled
source controls. Rootwill retainnegative/same-state drift and inspect images.


## Same compiled probe, global control

33846 exit0/strictanalysisaccepted: GPUOFF/ON/OFF/ON14.846/14.091/15.116/14.259ms,
FPS61.25/64.21/60.44/63.76. AdjacentGPU5.09/5.67%, frame4.61/5.20%.
Same four correlatedphases andconditions as the B15cloud control. This shows
existingcloud work reduction remains under the probe inthissession; it does
not estimate the incrementalhoist, which needs matchedsource comparisons.
B15cloud-only earlier4.52/5.84%is similar, not evidence thissource improves it.
Slow serialON launch remainsretained. Defaultoff, no integration into shared
Photon or furthercompounding/source rewrite fromthese results.
Rawdev/logs/photon-cloud-secondary-inprocess-r1-{benchmark,analysis}.json/log.
