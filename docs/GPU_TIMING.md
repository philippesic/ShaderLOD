# Development GPU timings

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
  `GL_TIME_ELAPSED`. These queries cannot nest. Even render frames measure the
  entire `game-render`; odd render frames measure each sequential Iris pass,
  shadow-map, and final stage. Frame and pass statistics therefore come from
  different interleaved frames and must not be added or compared as a per-frame
  decomposition. This supports Apple drivers without assuming their timestamp
  counter works.
- If neither target has useful bits, the overlay reports `GPU: unavailable`.
  Errors or 16 consecutive zero world-frame results disable timing with an
  explicit diagnostic. A GL error observed at frame end may originate from the
  renderer; the timer conservatively stops rather than displaying questionable
  results. Results still pending after 600 render frames also stop timing.

## Asynchronous samples and statistics

Each scope owns twelve query slots. Results are first polled at least two render
frames after issuance, and fetched as unsigned 64-bit nanoseconds only when
`GL_QUERY_RESULT_AVAILABLE` is true. There is no `glFinish`, wait loop, immediate
query-result read, or synchronous timestamp read. A full query ring drops the
new sample instead of overwriting a pending result or stalling the renderer.
Elapsed queries already owned by another tool are respected by skipping the
colliding sample. Query names are deleted before Minecraft closes its GL window.

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

## Controls and evidence

Comparison clients enable timing by default. Other dev runs opt in with JVM
property `-Dshaderlod.dev.gpuTiming=true`. Set
`-Dshaderlod.dev.gpuPassTiming=false` for the lowest-overhead whole-render
benchmark: elapsed queries then measure every world frame, with pass timing
disabled. Timestamp mode similarly disables pass marks. Use identical timing
settings for baseline and LOD phases. Timer queries themselves carry overhead;
serial A/B measurements should report the backend and pass-timing setting.

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
sample export work. The 32-bit elapsed counter is sufficient for ordinary frame
durations; timestamps are not used. Frame-only mode measures every world frame.
HUD statistics are refreshed four times a second to avoid sorting every pass's
history twice per rendered frame. The original-Photon baseline runs this harness
with the main ShaderLOD mod absent.

Dependency-free rolling-statistics check:

```sh
mkdir -p build/dev-timing-tests
javac -d build/dev-timing-tests src/devClient/java/com/pp/shaderlod/dev/timing/TimingStatistics.java src/devClientTest/java/com/pp/shaderlod/dev/timing/TimingStatisticsTest.java
java -cp build/dev-timing-tests com.pp.shaderlod.dev.timing.TimingStatisticsTest
```
