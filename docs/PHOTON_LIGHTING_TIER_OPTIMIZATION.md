# Protected lighting tier and live-debug classification

Prepared 2026-10-04 as a bounded source-scaffolding candidate. Only two Photon
call sites and their tests changed: deferred shading and translucent shading.
No helper, shader option/default, effect budget, noise/jitter, ray-march,
geometry, Java or benchmark code changed. Root owns actual driver rendering
and repeated measurements; no performance gain is claimed from this patch.

## Existing unnecessary consumer

`program/d4_deferred_shading.fsh` previously reconstructed the combined view
position and computed generic ShaderLOD quality for every terrain pixel. That
local tier had exactly two consumers: the filtered-shadow argument and the
late debug tint. `SHADERLOD_PRESERVE_LIGHTING=true` makes lighting_tier return 0,
so the shadow consumer does not need generic distance/noise classification.
With runtime debug false, the tint also has no active consumer. The actual
post-Iris sources in the previous audit still exposed the generic calculation;
the driver may already have eliminated or sunk some of it.

Other effects do not consume this local variable. AO and SSRT classify through
their own protected helper calls; fog, SSR, materials and clouds have their
separate policies. This task does not change those paths or promise to restore
all protected-lighting loop bodies to source-identical upstream code.

`gbuffers_all_translucent.fsh` also evaluated generic quality solely as the
filtered-shadow argument, despite protection. No translucent debug tint uses
that argument. Its protected call now passes literal 0.

## New source behavior

Deferred shading initializes the lighting argument to 0. It computes the original
sky/hand-gated generic tier only in a compile-time `!PRESERVE_LIGHTING` branch.
Protected lighting therefore receives full quality without that classification.

Debug colors retain their existing live predicate and terrain/non-hand routing.
Inside the existing debug-enabled branch:

- With protection true, compute generic debug quality there from the same
  combined view position, pixel and frame counter, then mix the same color at 0.7.
- With protection false, reuse the tier already calculated for lighting. Do not
  compute it a second time.

Sky stays outside the terrain debug branch; hand still skips tint. Global LOD
disable yields the same tier-zero green debug color if live debug remains on.
The helper mathematics and frame inputs are unchanged, so moving a deterministic
same-frame call preserves its selected color. Runtime debug=false can avoid the
call; debug=true retains exact color semantics. No debug option or bridge behavior
was removed to obtain a benchmark result.

This preserves original full lighting behavior under protection, while retaining
legacy distance lighting behavior when protection is explicitly false. The
translucent call's false branch keeps its original argument expression. Any
remaining driver overhead or optimizer outcome is unmeasured.

## Acceptance evidence

Source consumer checks assert that the deferred local tier appears only in its
initializer/legacy assignment, shadow call and debug alias. Tests inspect the
protected literal-zero argument and unchanged lighting helper contract.

CPU fixtures execute actual patched source snippets and actual shader helper
bodies against the preceding control flow. They cover protection on/off,
debug on/off, LOD enabled/disabled, sky/hand/terrain combinations and stable/
transition near/far distances. Resulting effective lighting tier and final debug
RGB must match. Counted actual helper entries establish these source-control
properties:

| Case | Deferred generic tier calls | Translucent shadow-argument tier calls |
| --- | ---: | ---: |
| Protected, debug off | 0 | 0 |
| Protected, debug on, non-hand terrain | 1 | 0 |
| Protected sky/hand | 0 | 0 |
| Unprotected non-hand terrain | 1, even with debug on |1 when the call is evaluated |

Counts are CPU executions of shader control/math adapters, not GPU instructions
or runtime profiling. Purkinje input is held identical; its unmodified caller
operation is not an optimization. Debug colors and 0.7 mix match within the
fixture's 1e-12 double-adapter tolerance. The near-distance helper and bridge
implementation are untouched.

The GLSL matrix adds protected/legacy debug with DH to existing all-dimension,
cloud mode/effect flag, shadow/material and water-count configurations. Driver
compilation remains a separate owned check. Existing timer-query variability and
the earlier non-repeating 77 FPS all-off result are not assigned to this change.

## Runtime control after static freeze

Use a new clean no-atmosphere archive, preserving old cloud-transition 8fba bytes
as the comparison control. Both use identical effect flags, cloud mode and
upstream quality, lighting protection true, debug false, geometry off and the
same measured camera/clock/wetness protocol. Compare balanced complete-JVM
pairs and whole-render metrics under the same query cadence. A separate debug
inspection should enable the live debug config, check near/medium/far terrain,
hand and sky, then disable it without changing shader quality.

For legacy compatibility, render a separate protection=false debug on/off check;
near/medium/far color and lighting must match the preceding pack. DH-present
routing still uses the already reconstructed combined projection position;
no new depth reconstruction was introduced. If timings are indistinguishable,
report this as source cleanup with no demonstrated speedup. A small measured
delta cannot be described as explaining or repairing prior large variable runs.

Static result: 19 shaderpack tests passed in 133.237 seconds, including 1512 GLSL
fragment compilations. Raw output is retained outside Git at
`dev/logs/photon-lighting-tier-tests.log`; the targeted two math/consumer tests
also passed independently. Heavy test execution has stopped.

| Artifact | SHA256 |
| --- | --- |
| `build/photon-lighting-tier-no-atmosphere.zip` (clean comparison candidate) | `b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c` |
| `build/photon-lighting-tier-candidate.zip` (main source, existing atmosphere guard) | `00ab90a172e0b7dc76298858ece71d80d09af1b3e1d833ec3e8005bb83c9fa82` |

The clean source root is `build/lighting-tier-no-atmosphere-source`; manifest
SHA256 `06c480df36e627db3232721c57a585cfe2e96fb7fd41c95d6e578318c44da45d`.
Both archives embed shaderlod-build.json source/overlay fingerprints, also
retained in ignored build/photon-lighting-tier-candidates.json.

The old clean 8fba archive retains its exact prior hash. Comparing its entries
with the new clean candidate finds exactly three changed files: build metadata,
d4 deferred shader and gbuffers_all_translucent. All shader helper, cloud/water/
fog/SSR source, options and defaults are byte-identical. This is a narrow
scaffolding comparison ready for root's owned runtime; rendering and gain remain
unverified.
