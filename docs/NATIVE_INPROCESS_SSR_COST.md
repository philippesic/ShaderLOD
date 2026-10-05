# Native same-JVM SSR cost controls

Source-only mode `benchmarkFeature=ssr_cost`; Java compilation and four-phase
runtime acceptance remain pending. It preserves existing global, effects,
coherence and terrain controls and their strict invariants.

The terminal four-JVM glint pilot71320 accepted original/skip/reduced/full at
GPU15.0436/15.0882/18.9037/18.3937ms, with threads approximately7.56/7.57/7.67/
7.58ms. Full and reduced remove the inspected bright band, while original/skip
quality remains unacceptable there. Source/JVM variation prevents assigning the
serial differences to a specific path. This diagnostic toggles global enable in
one fixed archive/pipeline/world; it does not repair or promote the bad skip.

## Three explicit source qualifications

| Policy | Exact archive SHA256 | Fixed private gate |
| --- | --- | --- |
| B15 far skip | `b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c` | Neither private gate exists |
| Private full far trace | `41ecbee8b72039654fb0d25b16a841e3a2f4500025ec918687f94c0b8cba1555` | `PHOTON_SSR_FAR_FULL_TRACE_PROBE=true` |
| Private reduced far trace | `636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83` | `PHOTON_SSR_FAR_REDUCED_TRACE_PROBE=true` |

`WorkloadInputs.qualifySsrCostArchive()` performs read-only exact archive and
member verification before phases. It checks settings/specular lighting plus
the unchanged LOD helper, no-atmosphere program, license and baseline build
metadata. Private policies also require their exact enabled metadata member,
manifest/source pin, switch and enabled arm. The report exports observed member
hashes and parsed private metadata. The Python analyzer validates that complete
qualification, including the private metadata's frozen canonical fingerprint.
Unknown, disabled, mixed, differently patched or generically whitelisted packs
are rejected. The effective private switch must remain true even in OFF phases;
the opposite private switch must be absent. This is source qualification, not a
shader compilation, linked-uniform or physical ray-count proof.

OFF changes only the existing live global bridge flag to false and intends the
original B15 trace budgets. ON uses that same archive's skip/full/reduced policy.
The private far remap selects local tier0 for full or tier1 for reduced when
the original LOD tier is2; global OFF selects original tier0 upstream. These
source semantics do not establish compiler-generated work or output parity.

## Strict held inputs

Only SSR is true among the five effect options; clouds/water/fog/materials remain
false. Use-mod-config and preserve-lighting are true; debug/cached-sky/analytic
water are false. Environment and sky reflections must actually be enabled.
Terrain policy/confirmation/activity, all CPU culling/list/coherence flags and
diagnostic observers are false. Complete shader maps and semantic config,
including all remaining parameters, stay identical except global enable.

Require actual DH absence, the pinned Minecraft1.21.1/NeoForge21.1.253/
Iris1.8.12-local/Sodium0.6.13 runtime, requested/effective24, native2976×1672,
hidden HUD, stable first-person/FOV and the fixed glint pose:
x500.5/y68/z660.5/yaw87.380/pitch3.735, Overworld/daytime12500, clear weather
and fixed shader wetness0. Pose/time/radius identity is checked at existing ticks
after the first capture reference is fixed; complete boundary checks preserve
archive/control/camera/config/window identities. World/pipeline references must
remain unchanged. Actual sampled window/pacing states must be available, stable,
visible/non-minimized and vsync off; unknowns and capture transitions reject.
Counts and IDs are observations, not continuous scheduling/coverage evidence.

Existing timing epoch/source-frame/image/hash/wetness checks remain in force.
The source restores and verifies the original global flag on completion/failure.
The code currently exposes `PhaseMarker` and preserves optional
`uniform_observation` payloads, but has **no uniform observer implementation**.
No observation or GPU readback is fabricated; config flag evidence and prior
bridge proof do not count physical uniform/trace calls in this session.

## Coordinator execution after source freeze

Compile the integrated dev source only after other GPU jobs stop. Use a fresh
stopped clone of exactly one inspected `ssr-skip`, `ssr-full` or `ssr-reduced`
profile from `run-workloads/photon-ssr-cost-controls-r1/coastalwater`, preserving
its selected archive/options/control/save and earlier reports. Keep source,
artifacts and rendered settings frozen; actual native dimensions remain required.

```sh
./gradlew runClientLodOn \
  -PrunDirectory=FRESH_PROFILE -PperformanceStack=minimal -PincludeDh=false \
  -PgpuBenchmark=true -PbenchmarkOnly=true -PbenchmarkFeature=ssr_cost \
  -PbenchmarkStartEnabled=false -PinitialWarmup=45 \
  -PbenchmarkSettleSeconds=10 -PmeasurementSeconds=25 \
  -PgpuSampleInterval=16 -PgpuPassTiming=false -PcpuLayerTiming=false \
  -PfixedWetness=0 -PbenchmarkWindowScale=2
python3 scripts/analyze_inprocess_control.py FRESH_PROFILE/benchmark.json \
  --profile FRESH_PROFILE --expected-shader-sha256 EXACT_SELECTED_SHA256 \
  --output dev/logs/EXPERIMENT/ssr-cost-analysis.json
```

Run ON/OFF/ON/OFF in a separate retained profile/report if useful. Each archive
gets its own session analysis; the comparison helper rejects cross-archive
SSR-cost comparisons even if other fields could be normalized. Do not infer a
cross-archive source cause from separate JVMs or combine their frame samples.
`-x compileDevClientJava` can preserve an already compiled harness during other
source work, but cannot enable this mode until the coordinator compiles it.

Interpret two adjacent ON/OFF contrasts plus same-state drift as correlated
phases. No independent-run confidence interval, shader busy-time or physical
call count is inferred from frame/GPU intervals. GPU time includes timeline
effects; CPU clocks and frame duration remain separate. A cheaper bad-quality
skip is still rejected; full/reduced need separate glint/motion/underwater and
broader quality evidence, repeated ordinary performance and the existing goal
gates. No optimization/default/support/goal completion follows from this source.

Source acceptance: 38 lightweight in-process fixtures passed, preserving all29
prior controls and adding exact qualifications/gates, forward/reverse policies,
source/flag/scene/config mismatch, unknown window/pacing, actual screenshot/hash
checks, artifact/session identity and rejected cross-archive causality. Read-only
inspection confirmed the three frozen archive/member/probe metadata hashes.
No pack was rebuilt and no compiler, client or GPU experiment ran by this owner.


## First reduced-archive session (root runtime)

Root session c1716926-bb04-4a2d-89df-02b3bf34c8c5 completed all four phases;
exec37106 exited0. Strict analyzer accepted source/member/private metadata,
fixed effective options/config, native window and pacing, scene/radius/time,
world/pipeline, artifact identity, GPU epochs, all four PNG hashes and restoration.
45s initial warmup, 10s settle/25s capture, minimal/noDH, GPU every16frames.

| Phase | Global | FPS | Mean GPU ms | Render thread ms |
| --- | --- | --- | --- | --- |
| 1 | OFF | 59.963 | 15.4489 | 6.9566 |
| 2 | ON | 61.205 | 15.1246 | 6.7892 |
| 3 | OFF | 60.815 | 15.2039 | 6.7278 |
| 4 | ON | 53.997 | 17.2996 | 6.7025 |

First adjacent ON pair improved GPU2.10%; second worsened13.78%. OFF drift was
1.59% lower GPU while ON later phase was14.38% higher GPU. No stable benefit or
source/JVM/driver/thermal/hit cause established. Root inspected phase2 PNG:
visible sun reflection, no broad straight bright band; bounded fixed-pose quality
only. Native image was displayed resized; no native pixel parity claim.

Raw benchmark/analysis/log retained as dev/logs/photon-ssr-inprocess-reduced-r1-*
and profile run-workloads/photon-ssr-inprocess-reduced-r1/coastalwater/ssr-reduced.
Reverse order runs separately with same archive and an equal stopped-source save
clone; its result remains pending. Other policy archives remain separate source
interventions. This mixed session does not promote a default or satisfy ordinary
performance, motion-quality or broader configuration acceptance.
