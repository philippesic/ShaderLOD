# Development CPU and GPU timings

The development harness measures real OpenGL timer queries. FPS and CPU clocks
are not used to calculate GPU milliseconds. It is compiled only in `devClient`,
and the released ShaderLOD jar does not contain its mixins or query code.

## Scopes and backend

`game-render` begins at `GameRenderer.render` entry and ends at its return, while
a world is present. It includes world geometry, Iris rendering, DH rendering
inside that call, shader passes, and HUD drawing. It excludes buffer swap,
Minecraft's frame limiter, simulation, and rendering submitted outside that
method. This is GPU timeline elapsed time for completion of the marked command
sequence, not an isolated shader ALU occupancy or hardware utilization counter.
Driver scheduling and CPU command-starvation gaps can affect it.

Extended attribution adds the following pinned entrypoints. These are command
intervals, not mutually exclusive busy-time fractions:

| Scope | Actual boundary and interpretation |
| --- | --- |
| `render-and-blit` | `Minecraft.runTick`: before `RenderSystem.clear` through just before `Window.updateDisplay`; includes render setup, GameRenderer, HUD and main-target screen blit; excludes swap, events and limiter. |
| `terrain/world/opaque+cutout` | Sodium 0.6.13 `drawChunkLayer(RenderType.solid(),...)` draws both SOLID and CUTOUT. The separate cutout/cutout-mipped calls are no-ops in this pinned implementation and are not measured. |
| `terrain/world/translucent` | Actual Sodium `drawChunkLayer(RenderType.translucent(),...)`; includes vanilla water and other translucent terrain. |
| `terrain-setup/world` | Sodium `setupTerrain`: visibility/culling, section preparation and any upload commands within the method. GPU elapsed includes command-starvation time during CPU preparation; this is not geometry draw busy time. |
| `terrain/shadow/...`, `terrain-setup/shadow` | Same entrypoints while the real `ShadowRenderer.ACTIVE` flag is set; separated from world terrain. |
| `dh/world/opaque`, `dh/world/translucent` | DH 3.3.3 inner `LodRenderer.renderTerrain(IDhTerrainRenderer, RenderBufferHandler, RenderParams, boolean, IProfilerWrapper)`: boolean true means opaque, false means transparent. Measures buffer lookup and real renderer delegation when buffers exist. This is the inner opaque flag, not the outer deferred flag. Transparent includes water and other DH transparency. |
| `dh-total/world/main`, `dh-total/world/deferred` | Entire DH `render` / `renderDeferred`, including render-list construction, setup, events, terrain calls and cleanup. These overlap DH draw scopes and must not be added to them. |
| `dh/shadow/...`, `dh-total/shadow/...` | DH calls while the same Iris shadow flag is active, if that workload invokes them. |
| `buffered-draws/world/opaque`, `.../general_transparent`, etc. | Real Iris `FullyBufferedMultiBufferSource.endBatchWithType(TransparencyType)` draws. Includes entity and block-entity batches, not exclusively individual entities. `remaining` measures its actual `endBatch()` flush. Shadow versions use the Iris shadow flag. |

Individual `EntityRenderDispatcher.render` callbacks mainly build buffered
vertices and are measured as CPU `entity-build/world` or `entity-build/shadow`.
They are not labeled as GPU entity draws. The corresponding GPU batch submission
is measured at the actual Iris flush entrypoints above. Empty enabled scopes can
legitimately yield zero or tiny query intervals; raw samples preserve this.

The pinned Iris 1.8.12 `CompositeRenderer.renderAll` debug groups identify actual
programs: `iris/deferred`, `iris/deferred1`, `iris/composite`, etc. Individual
passes include their compute work (if present), mipmap generation, bindings,
uniform updates, and fullscreen draw, up to the debug group's end. They measure
the real enabled passes, rather than assuming a numeric index always names the
same effect. `iris/shadow-map` measures `ShadowRenderer.renderShadows`; it is
shadow-map generation, separate from Photon shadow filtering. `iris/final`
measures `FinalPassRenderer.renderFinalPass`, including its copies/swaps.

The timer probes `GL_QUERY_COUNTER_BITS` for both `GL_TIMESTAMP` and
`GL_TIME_ELAPSED`; OpenGL 4.1 alone does not establish useful counter support.
The [Khronos specification](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_timer_query.txt)
allows zero counter bits, in which case no useful measurement exists. Runtime
logs record both values, GL version, renderer, and selected backend.

- With useful timestamps, pairs of `glQueryCounter` marks time frame, stage,
  and pass intervals in nanoseconds. Nested stages and passes are valid;
  `iris-stage/deferred`, etc. overlap their contained passes and must not be
  summed with them.
- With useful elapsed queries and zero useful timestamps, the fallback uses
  `GL_TIME_ELAPSED`. These queries cannot nest. Extended attribution rotates
  seven frame categories: `FRAME`, `IRIS`, `TERRAIN`, `DH_DRAW`, `DH_TOTAL`,
  `BUFFER_DRAWS`, `RENDER_AND_BLIT`. Only the selected category emits queries
  in that frame. For example, a DH-total query is active in a different frame
  from its nested opaque/transparent query. Each category gets one of every
  seven sampled render frames; statistics are interleaved estimates over a stable
  workload and cannot be added as a measured decomposition of one frame.
  `GpuTimers.samplingPolicy()` and the snapshot record the exact policy and
  selected category. Query stack collisions still skip a sample explicitly.
- Optional sparse cadence schedules GPU query work once every N runTick frames.
  N defaults to 1, preserving the original schedule. Categories advance by
  sampled-frame ordinal, so N=7 or N=16 cannot accidentally measure only one
  category. Unsampled frames issue no timer queries, query-result polls,
  capability probes, current-query state reads or timer error checks. Existing
  pending results are polled only when the next sampled frame arrives. CPU
  wall/thread clocks keep recording every frame independently.
- If neither target has useful bits, the overlay reports `GPU: unavailable`.
  Errors or 16 consecutive zero world-frame results disable timing with an
  explicit diagnostic. A GL error observed at frame end may originate from the
  renderer; the timer conservatively stops rather than displaying questionable
  results. Results still pending after 600 render frames also stop timing.

Nanosecond **units do not imply nanosecond precision**. ARB_timer_query issue 2
says granularity is implementation-dependent; issue 10 warns that tile-based
implementations can assign timings when the frame completes, nonlinearly and
depending on placement. These caveats matter on the Apple Metal/OpenGL driver.
A positive whole-render measurement establishes a useful aggregate GPU timeline
measurement, not that every tiny pass is individually resolved. A pass with
median zero is not proven free, cached, skipped or unimportant. In particular,
Photon's sky-map deferred program executes every frame in the pinned source;
a zero median does not establish that it was cached away.

Scope statistics export exact-zero count and minimum positive result (nullable
when none) as resolution diagnostics. The minimum positive result is not a
calibration of the driver's clock precision. Use whole-render and
render-and-blit deltas across matched real workloads as primary evidence;
per-pass/terrain/DH intervals are attribution clues with these limits. Do not
label `game-render minus sum(pass means)` as driver overhead or GPU busy time.
No synthetic workload was added to either comparison baseline, and no isolated
FBO calibration is claimed as executed.

## CPU wall and render-thread CPU

`CpuTimers` exports a separate stream using `System.nanoTime` for wall duration
and `ThreadMXBean.getCurrentThreadCpuTime` for the current Minecraft render
thread's consumed CPU time. The latter is enabled only when supported, and
unsupported values are null, not zero. These clocks are explicitly CPU data;
they are never substituted for GPU query results.

| CPU scope | Meaning |
| --- | --- |
| `run-tick` | Whole `Minecraft.runTick` entry/return: scheduled tasks, simulation, render, blit, swap/events, frame limiter, yield and post-frame bookkeeping. Excludes other work in Minecraft's outer run loop. |
| `game-render` | Same GameRenderer boundary as the GPU scope; includes development query maintenance and CPU marker overhead within that call. |
| `render-and-blit` | Render setup/clear through immediately before `Window.updateDisplay`. |
| `swap-and-events` | `Window.updateDisplay`, which includes buffer swap and event processing in the pinned runtime. A large wall/thread difference can reflect waiting or scheduling, without by itself proving a GPU bottleneck. |
| `frame-limiter` | `RenderSystem.limitDisplayFPS` when called. At the uncapped 260 setting Minecraft does not call this method, so the scope may be absent. |
| Terrain, terrain setup, DH draw/total, buffered-draw and Iris names | CPU submission/preparation intervals at the corresponding GPU boundaries, recorded every frame when layer CPU timing is enabled. |
| `dh-render-list/world`, `.../shadow` | Actual DH `RenderBufferHandler.buildRenderList`: CPU visibility/list preparation, nested inside DH-total. |
| `entity-build/world`, `.../shadow` | Actual `EntityRenderDispatcher.render`: vertex construction and related entity renderer work, possibly multiple invocations per frame. |
| `block-entity-build/world`, `.../shadow` | Actual Sodium public `renderBlockEntities` entrypoint, CPU only. |
| `sodium-command-fill/world`, `.../shadow` | Pinned private static `DefaultChunkRenderer.fillCommandBuffer` per region/pass: section iteration, visible-face decisions and native-memory writes assembling multi-draw arguments. No GPU query or new command caching. |
| `sodium-execute-batch/world`, `.../shadow` | Pinned private static `DefaultChunkRenderer.executeDrawBatch`: tessellation/VAO bind, actual multi-draw submission, and unbind/close. |
| `sodium-draw-submit/world`, `.../shadow` | Actual `GLRenderDevice.ImmediateDrawCommandList.multiDrawElementsBaseVertex`: reads current primitive/index/batch arguments and invokes `GL32C.nglMultiDrawElementsBaseVertex`. CPU wall/thread around the native submission, not completed GPU draw time. |

CPU parent and child intervals overlap. Do not sum `run-tick`, `game-render`,
terrain and DH-total. Likewise, an individual entity's invocation mean is not
the per-frame entity total: aggregate repeated samples by `frameId` before
computing a workload fraction. Current-thread CPU omits DH workers, integrated
server threads, driver worker threads and the GPU. Wall minus thread CPU is
unaccounted off-thread/wait/scheduling time, not proven GPU waiting. Marker
syscalls and instrumentation overhead are included where they occur; quantify
them with matched enabled/disabled instrumentation runs before small claims.

Sodium fill/execute/submit scopes run once per region and render pass; aggregate
their raw invocations by `frameId`. World versions include all world terrain
passes, not exclusively opaque terrain. `sodium-execute-batch` contains
`sodium-draw-submit`; compare them, but do not sum them. Both and command-fill
are nested inside the parent terrain CPU interval. Fill measures assembly;
native submission includes Java/native transition, GL driver work and any wait
inside the call. Wall minus thread CPU cannot distinguish a GPU fence wait from
other scheduling. These fine scopes add more clock calls and allocations than
coarse terrain timing, so run the documented `cpuLayerTiming=false` control
before claiming small assembly or submission improvements. No shader or mesh
work is skipped by this instrumentation.

Pinned bytecode acceptance: `DefaultChunkRenderer.render` invokes
`fillCommandBuffer` at bytecode offset 133 and `executeDrawBatch` at offset 211;
the immediate draw wrapper invokes the real native GL32C function at offset 37.
Strict mixins target those existing methods, whose names/signatures were
inspected from the locked Sodium jar. Compilation alone does not establish
that these new narrow hooks ran; verify positive invocation records in the owned
client before drawing an assembly-versus-driver conclusion.

## Asynchronous samples and statistics

Each scope owns twelve query slots. Results are first polled at least two render
frames after issuance, and fetched as unsigned 64-bit nanoseconds only when
`GL_QUERY_RESULT_AVAILABLE` is true. There is no `glFinish`, wait loop, immediate
query-result read, or synchronous timestamp read. A full query ring drops the
new sample instead of overwriting a pending result or stalling the renderer.
Elapsed queries already owned by another tool are respected by skipping the
colliding sample. Query names are deleted before Minecraft closes its GL window.
With sparse cadence, the next poll may be N frames later; availability still
gates result retrieval, never a wait. The existing 600-render-frame pending
timeout remains measured in real frames, and the interval is bounded at 1–60.

The HUD API `GpuTimers.summary()` describes `game-render` rolling arithmetic
mean, median, and nearest-rank p95 in milliseconds. Each scope retains up to
240 completed samples. `snapshot()` also reports sample count, lifetime count
since reset, ring drops, pending count, backend, and useful counter bits. No
samples produce NaN statistics and should be shown as warming rather than
serialized as normal JSON numbers.

`drainSamples()` returns completed `Sample(scope, milliseconds, frameId, epoch)`
records without touching OpenGL or waiting. A bounded queue keeps at most 8192
unconsumed results. Call it regularly during benchmark runs. `resetSamples()`
starts a new measurement epoch, clears statistics and completed results, and
discards pending query results issued under older epochs. This prevents baseline
GPU commands from entering the LOD measurement after a quality switch. All
methods are intended for the Minecraft render thread.

`GpuTimers.resetSamples()` also resets CPU history and measurement epoch.
`CpuTimers.drainSamples()` returns
`Sample(scope, wallMilliseconds, threadMilliseconds, frameId, epoch)`, separately
from GPU samples. The CPU queue retains at most 16384 unconsumed records and
reports queue drops. Open CPU scopes issued before a reset are discarded on
return without disturbing nested stack order. `CpuTimers.snapshot()` reports
per-scope wall mean/median/p95 and nullable thread CPU equivalents;
`CpuTimers.summary()` displays whole run-tick wall/thread means. CPU and GPU
frame IDs share the runTick index. Clock timing, ring behavior, rotation and
export shape have static/unit verification; real rendering hook application
still needs runtime validation for each added seam.

## Controls and evidence

Comparison clients enable timing by default. Other dev runs opt in with JVM
property `-Dshaderlod.dev.gpuTiming=true`. Set
`-Dshaderlod.dev.gpuPassTiming=false` for the lowest-overhead whole-render
benchmark: elapsed queries then measure every sampled world frame, with pass timing
disabled. Timestamp mode similarly disables pass marks. Use identical timing
settings for baseline and LOD phases. Timer queries themselves carry overhead;
serial A/B measurements should report the backend and pass-timing setting.

The default `-Dshaderlod.dev.gpuAttribution=true` selects the seven-category
schedule. `false` restores the earlier two-category FRAME/IRIS alternation.
`-Dshaderlod.dev.cpuTiming=false` disables all CPU marks;
`-Dshaderlod.dev.cpuLayerTiming=false` retains whole CPU frame/render/swap/limiter
clocks while disabling detailed CPU layers. For an instrumentation-off overhead
check disable both GPU and CPU timing. Keep overhead checks separate from actual
quality savings.

`-Dshaderlod.dev.gpuSampleInterval=16` schedules one GPU-query frame out of every
16; the default remains 1 for historical comparisons. The accepted range is
1–60 (out-of-range integers are clamped and malformed values fall back to 1,
with a log message). `samplingPolicy` includes cadence and polling policy;
snapshot adds `sampleInterval`, `scheduledSampledFrames`, and an `UNSAMPLED`
category on other frames. Scheduled frame count describes slots, not successful
results; use actual exported scope counts and ring drops to assess evidence.
At N=16 with seven-category attribution, each category is observed once every
112 frames. Consider frame-only sampling when collecting short sparse runs.

Query instrumentation itself may affect driver scheduling, tile flushes and
frame latency. A preliminary cloud/lower frame-only query run differed sharply
from a CPU-only run; that discrepancy is not yet established as a causal timer
effect without matched repetitions. The appropriate control is the same
scene/profile/resolution with CPU-only clocks, full-frequency queries and sparse
queries, repeated in alternating order. Sparse sampling reduces how often query
work occurs, but an individual query-bearing frame can still be perturbed by
its markers. Keep measured GPU values labeled as instrumented timeline durations;
do not turn CPU/FPS improvements from disabling queries into GPU milliseconds.
The exported absolute frame IDs allow CPU records to be compared between
query-bearing and other frames. Report cadence, counts and repeat variability.

Two concurrently rendering windows share the GPU, memory bandwidth, and driver
queues. Their numbers serve the visual preview and cannot establish a standalone
speedup. Benchmark a single warmed client with matched camera, render distance,
shader settings, resolution, DH cache, time, weather, and alternating quality
phases. Record raw samples, counts, median, mean, p95, and percent change. Keep
DH world generation and initial terrain uploads out of timed phases.

Static evidence: the pinned Iris bytecode has two `pushGroup` and three
`popGroup` call sites in `renderAll`, verified by `javap -p -c`. The strict
development mixin hooks these existing calls and preserves their behavior.
`./gradlew compileDevClientJava` verifies Java integration, not successful mixin
application, timer support, shader rendering, or GPU measurement. Runtime
validation belongs to the coordinator's owned client and is recorded with the
comparison results.

Runtime evidence on the Apple M3 Max: `timestampBits=0`, `elapsedBits=32`,
backend `ELAPSED_ALTERNATING`; positive game-render/pass results and the serial
sample export work in the prior two-category implementation. The 32-bit elapsed
counter can store ordinary frame durations; it does not promise precision or
linear pass attribution. Timestamps are not used. At the default interval 1,
frame-only mode measures every world frame.
HUD statistics are refreshed four times a second to avoid sorting every pass's
history twice per rendered frame. The original-Photon baseline runs this harness
with the main ShaderLOD mod absent.

Extended timing seams: Java compilation passes; category-policy/statistics/CPU
export checks pass. The standalone CPU test reports current-thread CPU time
supported on the local Java/Mac. Actual owned forest/high runtime on 2026-10-04
also succeeds: `run-workloads/forest/high/workload-measurement.json` records
3357 rendered frames over 30 seconds, 17746 GPU results and 171206 CPU results.
Seven-category GPU counts are balanced at 479–480 samples per ordinary scope.
Terrain setup/draw, DH opaque/translucent/total, buffered draw and main-frame
hooks all emit GPU samples; CPU shadow/world entities, block entities and DH
render-list hooks emit records. This verifies the extended seams in that runtime,
not the accuracy of a tiny zero-valued pass or a useful optimization.
The subsequently added Sodium command-fill/execute/native-submit hooks compile
but still require an owned runtime with their invocation records before they
can support a measured CPU assembly/submission conclusion.

Dependency-free rolling-statistics check:

```sh
mkdir -p build/dev-timing-tests
javac -d build/dev-timing-tests src/devClient/java/com/pp/shaderlod/dev/timing/TimingStatistics.java src/devClientTest/java/com/pp/shaderlod/dev/timing/TimingStatisticsTest.java
java -cp build/dev-timing-tests com.pp.shaderlod.dev.timing.TimingStatisticsTest
```

The additional `GpuSamplingPolicyTest` checks balanced seven-frame categories
and legacy/frame-only schedules, plus balanced sparse schedules at N=1,2,7,16,60;
`SparseGpuTimerGateTest` calls all public marker entrypoints on unsampled frames
without a GL context and verifies they never initialize/use GL, while CPU records
remain present. `CpuTimersTest` checks nested independent CPU
export, draining, title-screen exclusion and reset with open old-epoch scopes.
Compile these against `build/classes/java/devClient` plus the newline-delimited
classpath from `build/moddev/clientLodOnLegacyClasspath.txt` with `javac
-proc:none`. Put the fresh development classes before older standalone test
class output on the runtime classpath to avoid loading stale statistics classes.
