# Native-resolution in-process effect control

`benchmarkFeature=effects` adds an explicit four-phase global Iris bridge
control with the five Photon effect options held constant. It accepts all-active,
mixed or all-off effect maps. Each phase changes only `lod.enabled`; it does not
reload the shader pack or alter a module option. The existing default `global`
mode still requires all five modules off, and `coherence` still controls only
the original-Photon shadow coherence flag with global enable held true.

This is dev-only source integration, not shader support or performance evidence.
The runtime coordinator owns compilation and all client/GPU launches. No main
config, uniform transport, shader source or adapter was changed for this mode.

## Runtime setup and evidence

Use a frozen no-atmosphere Photon archive and prepared profile with the same
native framebuffer, camera/save, upstream quality, detailed/DH radii, runtime
artifacts, sampling cadence and pacing as the experiment being investigated.
The existing Gradle mappings accept these properties:

- `-PgpuBenchmark=true -PbenchmarkOnly=true -PbenchmarkFeature=effects` selects
  the new control and exits after the report/images complete.
- `-PbenchmarkStartEnabled=false` selects OFF/ON/OFF/ON; `true` reverses the order
  to ON/OFF/ON/OFF. Keep separate immutable output profiles/reports for each run.
- `-PbenchmarkSettleSeconds=...` supplies the existing between-phase warmup.
  Initial warmup, measurement duration, GPU/CPU controls, fixed wetness and
  `benchmarkWindowScale` use their existing mappings. Actual captured framebuffer
  dimensions remain authoritative; scale alone does not establish resolution.

Workload capture and benchmark capture are mutually exclusive. The session
preserves previous reports, records unique session/phase UUIDs, retains phase
images/hashes, and restores the initial global enable and HUD state on completion
or failure. A source check rejects the Photon lighting atmosphere guard before
the first effects phase and exports `atmosphere_guard_absent=true` only after
that check passes. The report method is distinct:

> One JVM/world/pipeline; fixed five-module effect options; only live global bridge enabled changes

At each boundary, the runtime requires actual ShaderLOD presence, a live Iris
pipeline without fallback, a hidden HUD, and the expected global enable value.
Terrain policy, culling, visibility lists and coherence must all be unconfigured
and inactive. Loaded semantic config remains identical except enable; debug,
cached sky and analytic water are false, and preserve-lighting/use-mod-config
are true. All five `SHADERLOD_CLOUDS`, `SHADERLOD_WATER`, `SHADERLOD_FOG`,
`SHADERLOD_SSR` and `SHADERLOD_MATERIALS` options must exist with literal string
values `true` or `false`. A module flag being true does not prove its underlying
upstream feature is enabled or its work survives compilation.

The existing boundary and tick checks preserve the archive hash, complete
effective option map, semantic config, camera/world inputs and world/pipeline
object identity throughout the four phases. GPU backend/sample epochs, source
frame ranges, timing policies and fixed wetness supplier observations keep their
existing checks. The report hashes loaded artifacts and bounded development
directory trees; changes to those frozen files during a run remain forbidden.
Window telemetry is retained with its sampled-state limitations.

## Analysis and acceptance

```sh
python3 scripts/analyze_inprocess_control.py PROFILE/benchmark.json \
  --expected-shader-sha256 EXACT_ARCHIVE_SHA256 --profile PROFILE \
  --output dev/logs/EXPERIMENT/effects-inprocess-analysis.json
python3 -m unittest discover -s tests -p test_inprocess_control.py
```

The analyzer accepts active effect flags only when `benchmark_feature` and the
instrumentation feature explicitly say `effects`, the method matches, the
selected flag is `lod.enabled`, and the atmosphere guard check is recorded as
passed. It rejects missing/nonliteral effect flags, unintended option/config
changes, CPU feature activity, malformed identities, shader/archive changes,
timing epoch reuse, image/temporal failures and incorrect phase alternation.
Complete effective option maps and runtime artifacts must match across compared
sessions. A different archive is recorded as a separate source intervention;
it does not imply a particular code path caused a difference.

Report phase timings and the two adjacent ON-versus-OFF contrasts separately
from same-state drift. These are four correlated phases in one JVM, not four
independent JVM replicates; no frame-derived confidence interval or independent
run statistics apply. Reverse-order controls help inspect drift but do not
establish a universal gain. GPU intervals, CPU wall/thread clocks and derived
frame duration remain separate. Window unknowns remain unknown. The pipeline
identity check is a declared runtime source check; exported raw object IDs and
runtime linked-uniform readback are unavailable.

The native nine-arm pilot did not identify a module as the cause of the active
regression. Original/global-off were approximately 61 FPS; all-active and
all-off were approximately 56 and 55 FPS in separate JVMs. Keep those retained
results and failures. This new control holds a single compiled pack/world/JVM
steady while checking whether the global bridge changes timings with fixed
effect options. It is a diagnostic, not a correction to that pilot's result.

Tool acceptance is 20 lightweight Python fixtures, including all nine existing
global/coherence fixtures, forward/reverse effects controls, malformed/missing
flags, option and semantic drift, bridge/lighting protections, artifact/session
differences, timing-free controls and static Java routing/method checks. No Java
compiler or client was run by this file owner. The coordinator must compile the
integrated dev sources, then retain successful or failed native four-phase runs
and inspect screenshots before drawing runtime/performance conclusions.
