# Photon fine-density cost control

Source freeze: 2026-10-05. This is a **quality-changing cost control**, not an
accepted LOD optimization, filtered density approximation, isolated texture-fetch
budget or demonstrated performance gain. It tests whether fine 3D cumulus-density
sampling merits the later projected-detail work in
[PROJECTED_DETAIL_LOD_PLAN](PROJECTED_DETAIL_LOD_PLAN.md).

## Exact scope, baseline and activation

Original Photon v1.3b by Benjamin Stott (SixthSurge), version ID `gUv7fBPN`, local
`dev/upstream/photon_v1.3b.zip`, SHA-256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
The original LICENSE is preserved byte-for-byte, SHA-256
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
Retain [Photon attribution](../patches/photon/ATTRIBUTION.md); the mod's MIT
license does not replace the upstream shader terms.

Only two upstream payloads change:

| Member | Original SHA-256 | Private source change |
| --- | --- | --- |
| `shaders/include/sky/clouds/cumulus.glsl` | `1923542cd9ad862a6166a542dbdcb1597fd70316e4338b236deefc2ccb932ba5` | Gated two-field declarations and fine `worley_1` fetch site, original lines 81–82 |
| `shaders/settings.glsl` | `93401d268fe859cc1df81929ad41156757a98db80f80cf32af80d9cdca96c394` | Default-off private option |

`SHADERLOD_DENSITY_DETAIL_COST_CONTROL` defaults off. When it is on and the
program defines `PROGRAM_DEFERRED1`, strict bridge version 2/global enabled 1
uses a declared `worley_1=0.5` diagnostic. Absent/unknown bridge, invalid enable
value, global off, other programs or private off retains the literal original
fine texture sample. The branch encloses the fetch itself. Only existing
`shaderlodBridgeVersion` and `shaderlodEnabled` are consumed; no per-effect policy,
ratio, preset, near/far, transition or debug field is wired.

Overworld entry: `shaders/world0/deferred1.fsh` → `program/d1_clouds.fsh` →
`clouds.glsl` → `draw_cumulus_clouds`/`clouds_cumulus_density`.
The intervention affects that main pass's cumulus primary and secondary density
evaluations. Cached sky (`PROGRAM_DEFERRED0`), preparation/cloud shadows, other
cloud layers, water, materials, lighting and reconstruction remain source original.

Coarse `worley_0`, coverage and shell checks, original primary/light/ambient count
constants, march extent/step length, primary/secondary seeds, phase functions and
all upstream early-exit statements remain unchanged. However, replacing fine
erosion changes density, occupancy, accumulated transmittance and apparent
distance; those changes can alter actual early exits and executed secondary work.
It may substantially change cloud appearance. A faster result cannot establish a
quality-equivalent gain or assign an isolated cost to one texture instruction.

0.5 is a declared diagnostic, not a measured texture mean. Squaring and nonlinear
density contrast mean that even the actual raw texture mean would not preserve
mean opacity. No calibration or corrective coefficients are claimed. This control
has no distance/footprint threshold or quality transition. Upstream positions
remain atmosphere coordinates; any later LOD must convert actual observer/sample
distance to block units with `CLOUDS_SCALE`, as the separate plan specifies.

## Builder and static evidence

Owned new files are the builder, manifest, test module and this document. Builder
`scripts/build_photon_density_detail_probe.py` verifies archive/member/license
pins, reviewed unique anchors, disabled/enabled fingerprints and exact modification
scope. It accepts original/disabled/enabled arms, refuses output outside ignored
`build/` and cannot overwrite the original archive. All upstream payload names and
bytes outside the two members remain unchanged. One metadata member is added:
`shaderlod-density-detail-probe.json`.

Ran:

```sh
python3 -m unittest discover -s tests -p 'test_photon_density_detail_probe.py' -v
```

Eight source/scalar fixtures passed in 0.018 seconds. They verify default-off and
non-main-program byte parity for every original member, the sole fine-fetch source
intervention, strict activation, option/license/member scope, rejected source or
manifest mutations, literal original fallback, and a counterexample to nonlinear
mean/occupancy preservation. No output ZIP, GLSL compiler, game, image, physical
fetch-count or GPU timing check was performed by this worker.

After review, root/runtime operator can assemble once and freeze output hashes:

```sh
python3 scripts/build_photon_density_detail_probe.py --arm original
python3 scripts/build_photon_density_detail_probe.py --arm disabled
python3 scripts/build_photon_density_detail_probe.py --arm enabled
```

Default outputs are `build/Photon-DensityDetailCostControl-{arm}.zip`. Metadata
keys include `experiment=photon-cumulus-fine-density-cost-control`, `arm`,
`base_version/base_sha256`, `manifest_sha256`, `switch/switch_enabled`,
`modified_upstream_files`, `member_sha256`, `license_sha256`,
`unchanged_upstream_member_count`, `consumed_core_fields`, `activation`,
`program_scope`, `diagnostic_fine_noise=0.5`,
`quality_class=quality_changing_cost_control`, `source_policy`,
`physical_work_caveat`, `runtime_compilation=unverified` and `gpu_benefit=unmeasured`.
Use this explicit intervention class when qualifying profile/source differences;
do not silently classify it as ordinary upstream-quality parity.

## Runtime gate and stopping boundary

Root/runtime operator owns assembly, driver compilation, client focus, reloads,
captures and GPU measurements. First prove a main-pass cumulus-visible scene and
settled literal original OFF behavior. Then inspect the enabled cloud image before
timing; if clouds vanish or the signal depends on gross opacity changes, retain
that failed diagnostic and stop rather than treating it as an optimization.

Existing coastal scene source for a fresh immutable copy is
`run-workloads/photon-ssr-cost-controls-r1/coastalwater/high`, Overworld
x500.5/y68/z660.5, yaw87.38/pitch3.735, time12500/clear. Cloud visibility is not
established by shader options alone; check a matched rendered capture. Prior
original coastal measurement option hash is
`ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669`;
original forest hash is
`4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745`.
Those are distinct profiles despite common cloud factor 4/counts 40/20/6/2.

Run a short original/default-off/enabled smoke, then repeated same-source global
OFF/ON frame-only intervals with matched resolution/preset/scene/weather, warmup,
quiet agent CPU activity and balanced order. Record total-frame and GPU median,
tail, variability and actual global values; keep instrumentation/readback outside
timed windows. Confirm no fallback and ordinary cloud visibility; a narrow
linked-field readback alone does not prove execution of the density path. If the
aggregate signal is flat/regressive, stop before calibration or projected-detail
implementation. If useful, require later full-near/edge preservation, controlled
far filtering, motion/weather/temporal quality and DH/noDH acceptance separately.
