# Render-thread JFR diagnosis

The completed original-Photon diagnostic identifies repeated visibility work
and native GL submission/copy boundaries. It does **not** identify the cause of
the earlier 145→75 FPS baseline variation: this recording captured a fast run.
No shader, geometry, query or client changes were made by the analysis worker.

## Recording and controlled window

The coordinator launched Minecraft with JDK 21 startup Flight Recording, GPU
timing disabled, original Photon, ShaderLOD absent and DH absent. The first
attach-by-PID helper did not find the Gradle argfile-launched JVM and produced no
usable recording; the startup flag resolved that diagnostic setup problem.
The coordinator confirmed JVM exit before export/analysis.

- Completed recording: `dev/logs/original-render-startup.jfr`, 7,495,927 bytes,
  SHA256 `04e5bee34ae63a1317c6d7b9885619b78e80ade512c73f792ce42f4824bfa855`.
- Observed recording window: 2026-10-04 19:54:57.872–19:56:31.850 Pacific.
- Aligned capture: 19:55:51.033–19:56:31.033 Pacific, 40.0003 seconds, 100%
  overlap with the recording. Earlier startup/warmup events are analyzed
  separately, not included in the capture figures below.
- Console: `dev/logs/original-cpu-jfr-console.log`. Raw capture:
  `run-workloads/effects-ssr-shoreline/coastalwater/high/workload-measurement.json`.
  Generated summaries: `dev/logs/original-render-all-summary.json` and
  `dev/logs/original-render-capture-summary.json`; exported selected-event JSON:
  `dev/logs/original-render-startup.json`.

JFR labels the rendering thread `main` in this recording, whereas the console
uses `Render thread`. The parser identifies it from actual
`GameRenderer.render` / `Minecraft.runTick` stack presence. Filtering only by
the console's name would incorrectly report zero render samples.

This is an instrumented diagnostic, not a performance benchmark. Its capture
measured 144.12 FPS, runTick mean wall 6.933 / thread CPU 6.411 ms, GameRenderer
wall 6.057 / thread CPU 6.021 ms, and swap/events wall 0.448 / thread CPU
0.178 ms. There were zero GPU timing samples. JFR and the remaining CPU clocks
still have observation cost.

## What the samples show

The aligned window has 418 Java execution samples and 507 native-method samples
for the rendering thread, with no truncated or missing render stacks. Their
sampling periods differ in JDK 21's profile settings: Java 10 ms, native 20 ms.
Counts and proportions below belong to their own stream; they are not method
execution times, GPU occupancy, or additive workload fractions.

| Java top frame | Samples out of 418 |
| --- | ---: |
| `FrustumIntersection.testAab` | 75 |
| Advanced shadow frustum `checkCornerVisibility` | 46 |
| Advanced shadow frustum `testAab` | 41 |
| Sodium `DoubleBufferedQueue.ensureCapacity` | 28 |
| Sodium `VisibleChunkCollector.visit` | 23 |
| Sodium `OcclusionCuller.processQueue` | 22 |
| Sodium `DefaultChunkRenderer.fillCommandBuffer` | 6 |

262 Java stacks (62.7% of that sample stream) include Sodium `setupTerrain`,
and 141 include shadow rendering. These contexts overlap. Actual stack paths
show frustum tests, occlusion traversal and visible-list rebuilding. This favors
auditing visibility invalidation/reuse before adding a command-buffer cache;
it does not establish that any existing result is safe to reuse.

| Native Java/JNI boundary | Samples out of 507 |
| --- | ---: |
| `nglMultiDrawElementsBaseVertex` | 240 |
| `glCopyTexSubImage2D` | 80 |
| `nglDrawElements` | 43 |
| JNI `invokePV` under `glfwSwapBuffers` | 42 |
| `glGenerateMipmap` | 31 |
| `nglTexSubImage2D` | 25 |

198 multi-draw stacks follow normal world terrain and 42 follow Iris shadow
terrain. The native boundary is real submission; it does not expose the driver
instruction below it or distinguish executing driver code from waiting inside
the call. Copy stacks include Photon's/Iris's final-pass copies, shadow depth
copies and shared depth handling; mipmap stacks include actual composite
mipmapping. Animated sprite uploads also appear. A JNI function name is resolved
with its caller context, so swap calls are not mislabeled as shader draw work.
No native query API appears among these render samples; sampling absence alone
does not prove an API was never called. GPU timing was explicitly disabled.

Five-second capture bins retain similar leading frustum and multi-draw paths.
The recording therefore gives a coherent description of this fast baseline,
not a measured transition into the slow regime.

## CPU consumption, waits, GC and JIT

JDK thread load divides CPU time by wall duration and active processor count,
as shown in the [OpenJDK 21 implementation](https://raw.githubusercontent.com/openjdk/jdk21u/master/src/hotspot/share/jfr/periodic/jfrThreadCPULoadEvent.cpp).
The recording reports 14 hardware CPU threads. If that equals the active count,
render-thread load corresponds to approximately 92.5% of one CPU core, matching
6.411 ms × 144.12 FPS ≈ 92.4% from the separately exported clock records.
The Java and native render paths together consume substantial thread CPU.
This does not assign that CPU consumption to individual sampled methods.

The recording's aggregate JVM CPULoad is much lower than its render-thread load,
so those aggregate counters do not corroborate one another. The parser flags
the discrepancy; it must not be used to claim that the process was almost idle.
Machine-total CPU load averaged about 15.2% over capture. These trailing load
events do not measure GPU utilization or explain other runs' host state.

Three top-level GC pause intervals total 16.702 ms across the 40-second capture
(about 0.042% of wall duration); one pause is 12.318 ms. This can affect a few
frames but does not explain a sustained halving of throughput in this run.
There are no recorded render-thread park, monitor-enter, monitor-wait or sleep
events during capture. JDK profile settings threshold these at 10 ms, and native
driver waits are not Java monitor events, so shorter/native waits remain possible.

Four slow C2 compilations occurred during capture, including LevelRenderer and
the development overlay. Their durations are compiler-thread activity, not
render-thread pauses. Startup has much more compilation and GC, which is why
startup/warmup is not pooled with capture. Absence of other Compilation events
does not exclude JIT work below the profile's 100 ms threshold.

The useful next comparison is a similarly aligned recording from the **slow**
baseline, with unchanged scene/options and CPU-only clocks, plus host-state
telemetry owned by the coordinator. Compare culling/list contexts, native
submission/copy/swap contexts, CPU wall/thread ratios, waits, GC and compilation
by time bin. This recording alone cannot choose between driver behavior,
host scheduling/frequency, viewport invalidation or another cause of variability.

## Reproduction and parser checks

Only export a completed recording after its owner confirms recording/JVM
termination. The script requires an explicit closed-recording flag for binary
inputs and rejects a file whose size/mtime changes during export:

```sh
python3 scripts/analyze_render_jfr.py dev/logs/original-render-startup.jfr --recording-closed --export-json dev/logs/original-render-startup.json --output dev/logs/original-render-all-summary.json
python3 scripts/analyze_render_jfr.py dev/logs/original-render-startup.json --measurement run-workloads/effects-ssr-shoreline/coastalwater/high/workload-measurement.json --output dev/logs/original-render-capture-summary.json
python3 -m unittest tests.test_render_jfr
```

The tool uses JDK `jfr print --json` with selected event types and stack depth 64.
It reports top and deduplicated inclusive counts, overlapping contexts, render
thread states, five-second bins, CPU load, clipped/union wait and GC durations,
slow compilations and requested capture overlap. `--render-thread` can override
automatic selection; `--start` / `--end` accept absolute ISO timestamps.

Eight tiny fixture tests cover ISO durations/timezones, thread filtering and
actual-render auto-detection, separate streams, recursive inclusive counts,
JNI swap context, clipped cross-bin waits, GC-versus-collection duration,
compiler-thread distinction, missing traces, CPU normalization reference and
partial/non-overlapping windows. They pass. Raw recordings/exports/reports stay
ignored by Git; only the parser, fixtures and this evidence document are tracked.
