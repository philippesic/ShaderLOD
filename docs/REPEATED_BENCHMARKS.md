# Repeated serial benchmark protocol

The proof runner records independent JVM launches, uses interleaved condition
ordering and retains failures. The coordinator exclusively owns the GPU and must
close preview clients first. This tooling does not establish any new performance
result by itself.

## Inputs and controls

Prepare isolated profiles using the coordinator's proof-profile preparation
script under `run-workloads/photon-proof/`. Each scene contains `high`,
`patched-off`, and `adaptive` profiles. Original `high` has no ShaderLOD mod;
`patched-off` loads the patched pack and mod with its live config disabled;
`adaptive` enables distance logic. Include the simpler `lower` quality control
where appropriate. Shared `control.json` must not override `enabled` or terrain
settings when testing shader-only benefits. All profiles must derive from the
same warmed source save and carry `workload-manifest.json` provenance.

Iris requires Sodium on this target. `minimal` therefore means Iris/Sodium plus
required runtime components, without additional optional optimizers. `optimized`
adds the coordinator's pinned optional mod stack. The runner passes
`-PperformanceStack=minimal/optimized` and `-PincludeDh=true/false` to Gradle.
Actual loaded mod IDs/versions and profile mod-jar SHA-256 hashes are captured;
request labels alone are not evidence that a stack loaded successfully.

Before the first launch, the runner hashes archives, shader options, camera
control, scene provenance, and relevant config files. It verifies stable shader,
camera and source identity again before each repeat. Minecraft legitimately
rewrites `options.txt` at shutdown, so its hash is recorded but not treated as an
immutable subsequent-launch input. Iris also rewrites pack option files with a
timestamp and removes explicit default values. Raw option-file bytes are retained
as evidence but are **not** compared with prelaunch bytes. The runner parses pack
source defaults to normalize requested input files, then validates every requested
active option against Iris's loaded effective values. Unrecognized requests fail;
only an unchanged upstream `INFO` default can be absent, because that profile
information selector is not asserted to control performance.

The runtime reads `Iris.getCurrentPack().get().getShaderPackOptions()`, enumerates
`getOptionSet().getBooleanOptions()/getStringOptions()`, and resolves values with
`getOptionValues().getBooleanValueOrDefault(name)/getStringValueOrDefault(name)`.
It records the sorted complete effective map and canonical JSON SHA-256 at both
measurement boundaries. These APIs were inspected in the pinned Iris jar.
Effective values must stay identical throughout capture and across repeat
launches for the same condition. Paired quality comparison uses these loaded
values, including defaults, instead of incomplete normalized option-file text.
Actual framebuffer, requested/effective
radius, camera, day/weather, enabled/preset state and Iris pipeline state are
captured at both measurement boundaries. Changed inputs, disabled shaders,
fallback, wrong pack or wrong LOD state invalidate the run. Source-save hashes
are preparation provenance; they do not assert byte-for-byte equality of mutable
save files after Minecraft has written them.

A per-run UUID in `workload-request.json` must match the fresh report. Prior
reports move into the output directory; an old report cannot satisfy a new run.
The runner checks process exclusion, waits for the complete Gradle/JVM exit,
rejects fatal shader/control/write error markers, and preserves invalid fresh
reports. On timeout it terminates only its own process group. Known resource
property and audio warnings are not automatically treated as shader failures.
There is also an advisory lock preventing two matrix runners at once. Process
inspection covers the project's dev launchers and common direct Minecraft Java
launchers; manually operated exotic launchers still require operator exclusion.

Each condition appears once in each repeat block. Williams rows balance order
positions and carryover over a complete design cycle; a partial 3–5-repeat cycle
is interleaved but is **not** claimed perfectly balanced. A two-condition,
four-repeat matrix gives AB/BA/AB/BA. Both runtime stacks can be interleaved in
one matrix instead of measuring the whole minimal stack before optimized.

## Commands

Start with a dry run (no writes, build or client launch):

```sh
python3 scripts/run_proof_matrix.py forest-high forest-patched-off forest-adaptive --profiles-root run-workloads/photon-proof --stacks minimal optimized --repeats 3 --dry-run
```

Suggested initial matrices, once every profile and optional stack has passed a
runtime smoke check:

```sh
# Ordinary scene, ShaderLOD incremental gain within each stack: 18 JVM runs.
python3 scripts/run_proof_matrix.py forest-high forest-patched-off forest-adaptive --profiles-root run-workloads/photon-proof --stacks minimal optimized --repeats 3 --tag forest-stacks-r3
# Premium cloud scene plus simpler existing-quality control: 12 JVM runs.
python3 scripts/run_proof_matrix.py cloud-high cloud-patched-off cloud-adaptive cloud-lower --profiles-root run-workloads/photon-proof --stacks minimal --repeats 3 --tag cloud-controls-r3
# DH-absent compatibility/performance path: 9 JVM runs.
python3 scripts/run_proof_matrix.py forest-high forest-patched-off forest-adaptive --profiles-root run-workloads/photon-proof --stacks minimal --dh absent --repeats 3 --tag forest-no-dh-r3
# Refine a promising ordinary-scene result with five additional independent pairs.
python3 scripts/run_proof_matrix.py forest-high forest-adaptive --profiles-root run-workloads/photon-proof --stacks minimal --repeats 5 --tag forest-confirm-r5
```

Default warmup is 45 seconds, capture 30 seconds, timeout 300 seconds. Add JVM
startup/shutdown time: 18 runs take at least 22.5 minutes of warmup/capture and
usually substantially longer. Prefer completing one matrix before expanding the
set. `--skip-build` skips only the initial build. `--warmup`, `--duration`,
`--timeout`, `--sample-interval`, and `--no-gpu-queries` are explicit controls;
keep them matched within a comparison. CPU wall/thread clocks remain separate
from actual GPU measurements. CPU layer profiling is disabled by default in the
runner to reduce measurement overhead.

## Analysis and uncertainty

Each new tag produces `dev/logs/TAG/matrix-manifest.json`, console logs,
measurements, summaries and `matrix-analysis.json`. Existing tags are refused.
Failed matrices remain marked failed and are not silently ranked. Rerun with a
new tag after correcting the failure; preserve the failed evidence.
The initial `sustained-v2-main` matrix is retained as failed: its first baseline
hit the old raw-file guard after Iris normalized an otherwise equivalent option
file. This was a tooling mismatch, not a demonstrated shader-quality change.

The analysis reports the median, min/max and median absolute deviation across
**independent JVM runs** for FPS-derived frame duration, GPU mean/median/p95,
CPU wall/thread means and CPU wall p95. Candidate time reductions are paired
against the scene's original-high run in the same repeat block and runtime
stack. Positive percentages mean lower time. Runtime-stack comparisons are
separate from ShaderLOD's incremental comparisons. Geometry/detail changes and
simpler quality controls are annotated; upstream quality settings must match for
adaptive and patched-disabled comparisons.

Frame samples are autocorrelated. Thousands of frames are not thousands of
independent replicates. No IID confidence interval or significance claim is
produced. Three to five repeats remain exploratory; per-run p95 describes tail
latency and is not uncertainty on the speedup. Order drift, shared hardware
activity and thermal/cache state can still matter. Do not choose the best repeat
or drop an anomalous run because it weakens the result.

GPU scope boundaries, coarse Apple elapsed-query attribution, CPU/driver time
and overlapping scopes retain the limits in [GPU_TIMING.md](GPU_TIMING.md).
Correct timings do not prove visual quality: inspect transitions, motion and
salient lighting separately.

## Wetness and temporal observation

The dev harness now wraps the existing wetness `FloatSupplier` at the exact
pinned Iris 1.8.12 `CommonUniforms.generalCommonUniforms` registration. The
`UniformHolder.uniform1f` call is sliced between the `wetness` and `skyColor`
string constants and requires one match. The actual registration uses
`PER_TICK`. There is no `WorldInfoUniforms` class in this artifact. The original
supplier is evaluated exactly once each time Iris normally evaluates it; the
tracker never invokes a smoother while exporting a snapshot or reading back GL.

Separate registrations can own separate smoothers. Telemetry therefore retains
bounded registration IDs, holder class/frequency labels, per-supplier raw/applied
last values, ranges and read counts. Those IDs are not shader program IDs:
program identity is explicitly unknown. An overall latest value cannot prove
all programs share the same state. The tracker makes no per-evaluation
allocations; registration and snapshot allocations happen outside frame hot
hooks. Capacity exhaustion fails visibly instead of silently omitting suppliers.

`shader_temporal_inputs_initial/start/end` are exported separately from the
strict input-state comparison. They include actual observed wetness and CPU
source getters for frame counter, frame time, frameTimeCounter, full day time,
world day/time, moon phase, rainStrength and Photon's derived world-age formula.
The source getters are not a readback of uploaded custom uniforms; biome
smoothing and all GPU histories remain unobserved. FrameCounter and real clocks
continue advancing. Normal dynamic wetness changes are labeled in the matrix
run metadata and do not invalidate exploratory captures merely because the
smoother advances. Missing observations are labeled explicitly.

An optional artificial steady shader-weather control is available:

```sh
python3 scripts/run_proof_matrix.py cloud-high cloud-patched-off cloud-adaptive --profiles-root run-workloads/photon-proof-v2 --stacks minimal --repeats 3 --warmup 35 --duration 20 --fixed-wetness 1 --tag cloud-fixed-wetness-r3
```

`--fixed-wetness` accepts finite values from 0 to 1 and maps to
`-PfixedWetness` / `shaderlod.dev.fixedWetness`. It applies from initial uniform
registration throughout warmup/capture, not by jumping weather at measurement
start. Every wrapped supplier still evaluates its original raw smoother once,
then publishes the fixed diagnostic value. Real rainStrength, smoothing clocks,
frameCounter and animation clocks remain unchanged. Per-registration observations
must prove the requested applied value at both boundaries and throughout the
capture range. Missing or wrong fixed telemetry fails the run. A default run
preserves the original wetness value exactly and rejects an unexpected fixed
policy. This is dev-only instrumentation and never a production weather feature.

The original 36-run matrix predates this telemetry and must not be retrospectively
claimed to have matched actual shader wetness. See
[PHOTON_TEMPORAL_INPUTS.md](PHOTON_TEMPORAL_INPUTS.md) for the source-based
hypothesis and remaining unmeasured state.

## Per-repeat images

After the complete client JVM closes, the runner preserves that repeat's fresh
`screenshots/workload-final.png` as `NNN-bB-CASE-STACK-final.png` in the matrix
output, with path and SHA-256 in its row. It checks freshness and PNG signature;
stale/missing images fail the run. Copying/hashing occurs after timing. The report
and image are retained before subsequent profile captures overwrite them.
Earlier matrix runs did not preserve every repeated image this way; no missing
image is fabricated retroactively.

Implementation checks passed: standalone Java policy/supplier tests verify
clamping, property bounds, original evaluation once, distinct raw suppliers,
fixed publishing and snapshot read independence; Python tests check all observed
supplier guards, dynamic drift, missing/stale image handling and PNG preservation.
`compileDevClientJava` passed. These checks do not establish runtime mixin
application. Before the next measured matrix, verify a real original/mod-absent
capture has supplier observations, fixed 1 reaches every observed registration,
raw values continue evolving, clocks/counters advance, and a fresh image is
preserved. Then run matched dynamic/fixed repeated controls and inspect images.

The prepared-camera guard compares yaw modulo 360 because Minecraft wraps yaw:
180, -180 and 540 degrees represent the same heading. XYZ and pitch comparisons
remain linear with the same 0.002 tolerance. Both prepared and observed camera
values must be finite numeric values; missing values, booleans, strings, NaN and
infinity are rejected before modular arithmetic. The failed
`ssr-original-probe` remains excluded from accepted performance evidence: its
180/-180 guard mismatch was corrected in tooling, not retrospectively accepted.
