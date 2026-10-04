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
