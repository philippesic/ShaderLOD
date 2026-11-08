# ShaderLOD measurement and profiling design

2026-11-08 Pacific. Requested by Philip (VP). **Design only; not implemented or
runtime-validated.** This expands the existing cost-map sketch in place. No
Minecraft client, benchmark, GPU workload, Nsight capture or training was run to
prepare it. Research comprised repository/source inspection and primary profiler
documentation. All overhead numbers below are proposed acceptance budgets, not
measurements.

[STATUS](STATUS.md#latest-outcome-and-next-action) owns current direction, allocation
and retention decisions. [BENCHMARK](BENCHMARK.md) owns supported commands and
performance proof. This document owns the proposed profiling design; proposed
commands here are not operational instructions. On implementation, document the
supported interface in BENCHMARK rather than maintaining two command guides.

## 1. Recommendation for Philip

Build a **development-only cost map**, extending `BottleneckProbe`, `GpuTimers`,
`CpuTimers` and their existing Iris/Sodium/DH hooks. Expose it as a separate
`profile` mode in the maintained benchmark harness, with its own capture ownership
and reports. Use a simple offline report for decisions and an exportable trace for
investigation. Do not put detailed profiling inside normal scored A/B windows.

The first screen should answer: **which named stages cost the most, what features
do they contain, how repeatable is that cost, and what remains unknown?** Philip
should be able to expand “shadows,” “fog,” “terrain,” or “post-processing” into
actual passes and submission work, select an area for investigation, and see the
same categories across the four packs. A cost map is a way to choose work, not an
automatic optimization selector or evidence of an achievable speedup.

Provide three related views with explicit units and denominators:

- **Frame pacing:** total frame-start intervals, their distribution and variation.
- **CPU:** render-thread wall intervals, thread CPU consumption, known blocking
  calls and unattributed off-CPU time; optional sampled worker/JVM detail.
- **GPU:** measured command-stream elapsed intervals by named stage/pass/draw
  group, with feature labels, missing coverage and sampling uncertainty.

Do not promise an additive “CPU + GPU + wait = frame” chart. CPU submission and
GPU execution overlap and can cross frame boundaries. OpenGL timers do not expose
hardware busy time, all queues, occupancy, instruction cost or the critical path.
A shader function inside a mixed pass is not separately timed just because it has
a recognizable name.

This serves the decision after the completed 16-candidate screen: G01–G10,
C01–C02 and N01–N04 remain parked. There is no new optimization in this plan.
The native RTX 5080 baselines were already approximately 2.02–3.38 ms/frame;
small absolute observer costs matter. Reuse the completed [RTX report](../dev/experiments/rtx5080-four-target-audit-20261106/REPORT.md)
and [Asahi conclusions](../dev/experiments/asahi-four-target-audit-20261104/audit-conclusions.json)
as context, not pass measurements they never collected. Current STATUS supersedes
historical recommendations in those records.

## 2. What serious profilers establish as the standard

The useful standard is a navigable hierarchy tied to actual execution, accompanied
by uncertainty and a way to distinguish work from waiting. Adopt these ideas, not
vendor layouts. Documentation below was consulted on 2026-11-08; validate installed
tool/API/driver support again before any later capture.

| Tool and primary research | What matters for this design | Honest use in ShaderLOD |
| --- | --- | --- |
| **NVIDIA Nsight Graphics:** [GPU Trace overview](https://docs.nvidia.com/nsight-graphics/UserGuide/gpu-trace-overview.html), [timeline reference](https://docs.nvidia.com/nsight-graphics/UserGuide/gpu-trace-ui.html), [historical Range Profiler](https://archive.docs.nvidia.com/nsight-graphics/2022.4/UserGuide/index.html#range_profiler) | GPU Trace relates events and queues to hardware utilization; the historical Range Profiler groups actions by markers/program/targets and lets the user drill into expensive ranges. Separate elapsed cost from hardware limiters. | Occasional RTX 5080 calibration, subject to installed-version support. The [current API matrix](https://docs.nvidia.com/nsight-graphics/UserGuide/appendix.html) lists OpenGL GPU Trace but not OpenGL shader profiling. Historical range functionality is not a promise that an old release supports Blackwell. No Nsight Graphics capture on Asahi. |
| **Nsight Systems:** [OpenGL trace](https://docs.nvidia.com/nsight-systems/UserGuide/index.html#opengl-trace) | Correlates CPU API calls, scheduling and GPU workload; exposes swap and synchronization behavior. Its OpenGL workload trace batches calls using timestamps, and debug ranges report incremental cost that does not fully represent overlapping draws. | Use on RTX for CPU/driver/queue/wait questions when supported, separately from Graphics hardware counters. Even an external trace has semantic limits; its GL ranges are not an unquestionable busy-time oracle. |
| **AMD Radeon GPU Profiler:** [events](https://gpuopen.com/manuals/rgp_manual/events_windows/), [queue timing](https://gpuopen.com/manuals/rgp_manual/overview_windows/), [requirements](https://gpuopen.com/rgp/) | Hierarchical user events, consistent marker colors, wave occupancy, barriers and submit/enqueue/execute distinctions explain why “long range” does not mean “shader arithmetic.” | Design reference. Current graphics support is DX12/Vulkan on supported AMD hardware, not this Iris/OpenGL workload or either allocated GPU. Do not add an AMD dependency or infer AMD compatibility. |
| **Microsoft PIX:** [Timing Captures](https://devblogs.microsoft.com/pix/timing-captures-new/), [GPU Captures](https://devblogs.microsoft.com/pix/gpu-captures/) | Timing captures preserve live CPU/GPU relationships. GPU replay distinguishes execution duration from end-of-pipe duration and may serialize queues that overlapped in the game. Named markers connect overview to individual actions. | Design reference for lanes, parent/child ranges and replay warnings. PIX GPU captures target D3D12, not native Iris/OpenGL. Do not port or translate the renderer to obtain a profile. |
| **RenderDoc:** [counter viewer](https://github.com/baldurk/renderdoc/blob/v1.x/docs/window/performance_counter_viewer.rst), [replay tradeoffs](https://github.com/baldurk/renderdoc/blob/v1.x/docs/how/how_control_replay.rst) | Event/resource inspection is valuable for understanding which draw produced an image; available counters can identify expensive captured events. Replay changes execution conditions. | Optional graph/resource verification if the exact driver supports capture. Not the main live frame profiler, CPU-wait tool or proof of original frame throughput. Counter availability is backend-dependent. |
| **Tracy:** [project](https://github.com/wolfpld/tracy), [OpenGL implementation](https://github.com/wolfpld/tracy/blob/master/public/tracy/TracyOpenGL.hpp) | Instrumented CPU/GPU zones, frame association and delayed query collection provide an approachable model. Its GL calibration code illustrates that correlation can introduce synchronization. | Borrow zone identity and on-demand capture ideas. Do not replace the Java timers with a new JNI/native profiler dependency in v1, or import synchronizing calibration into measured windows. |
| **Superluminal:** [sampling and instrumentation](https://superluminal.eu/docs/documentation.html) | Sampled stacks find costs outside anticipated scopes. Executing, waiting and unattributed time remain distinct, with inclusive/exclusive views. | Optional Windows native/driver CPU investigation. Java symbol/JIT visibility must be verified; do not promise it from native stack support. Its sampling rate is not an appropriate default to copy blindly. |
| **JFR/JDK Mission Control and async-profiler:** [JFR overhead](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshoot-performance-issues-using-jfr.html), [async-profiler modes](https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilingModes.md), [platforms](https://github.com/async-profiler/async-profiler) | JFR provides execution samples, GC/JIT and thresholded wait events. async-profiler separates CPU and wall sampling and includes Java/native stacks. Sample shares and wall durations answer different questions. | Reuse JFR analysis for separate untimed diagnosis. async-profiler is a later optional Linux arm64 path after permission/JVM checks; maintained platforms listed are Linux/macOS, not native Windows. Neither yields GPU percentages. Published JFR overhead is not ShaderLOD calibration. |
| **Apple Instruments / Metal System Trace:** [Metal tools](https://developer.apple.com/metal/tools/), [timestamp conversion](https://developer.apple.com/documentation/metal/converting-gpu-timestamps-into-cpu-time) | Parallel CPU/GPU timelines, encoder labels and explicit timestamp correlation show the desired relationship between named work and hardware execution. | Reference for presentation and occasional separately scoped macOS/Metal investigation only. Fedora Asahi OpenGL is not a Metal application; macOS measurements cannot validate this Linux stack. |
| **gpuvis / Linux tracing:** [overview](https://github.com/mikesart/gpuvis/wiki/Overview), [Mesa Asahi](https://docs.mesa3d.org/drivers/asahi.html) | ftrace events and markers can connect scheduling, presentation and GPU activity where driver tracepoints exist. | Optional Asahi investigation after checking the actual kernel/Mesa event coverage and permissions. A GPU trace viewer does not create missing Asahi events. Mesa's documented macOS reverse-engineering wrapper is not a ready Linux frame profiler. |
| **Perfetto:** [track events](https://perfetto.dev/docs/instrumentation/track-events), [clock domains](https://perfetto.dev/docs/concepts/clock-sync) | Separate tracks for parallel work, nested slices for sequential scopes, counters/flows, and explicit clock correlation. | Preferred interoperable trace destination alongside a small offline report. An exported trace is visualization, not additional measurement. Uncorrelated clocks must not appear synchronized merely because a viewer uses one ruler. |

The common lesson: overview first, exact named scope next, deeper capture only
when needed. Hardware traces and sampled stacks complement lightweight ranges.
They do not justify attributing every millisecond to a feature or multiplying a
pass-time reduction into an expected FPS improvement.

## 3. Starting point and concrete gaps

All Java paths in this section are relative to
`src/devClient/java/com/pp/shaderlod/dev/`. Keep instrumentation in the development
source set and separate diagnostic artifacts. [build.gradle](../build.gradle)
already separates development/controller/neutral benchmark jars from main.

| Existing component inspected | Preserve | Extend or correct in future implementation |
| --- | --- | --- |
| [`GpuTimers`](../src/devClient/java/com/pp/shaderlod/dev/timing/GpuTimers.java) | Capability selection, timestamp and elapsed backends, delayed availability polling, frame/epoch ownership, non-nesting checks, failure shutdown | Export endpoints and invocation/parent identity. Bound total scope/query allocation, not only each scope's 12-slot ring. Account for completed-queue eviction (currently the 8192-sample queue evicts without a dedicated exported eviction count). Separate pending-tail loss from ring exhaustion and unsupported clocks. |
| [`GpuSamplingPolicy`](../src/devClient/java/com/pp/shaderlod/dev/timing/GpuSamplingPolicy.java) and [tests](../src/devClientTest/java/com/pp/shaderlod/dev/timing/GpuSamplingPolicyTest.java) | Sampled-frame ordinal rotation and no query issue/poll on unsampled frames | Add named nonoverlapping scope sets, fair coverage and declared phase/seed. Preserve tests for cadence aliasing; avoid always sampling the same temporal-history phase. |
| [`CpuTimers`](../src/devClient/java/com/pp/shaderlod/dev/timing/CpuTimers.java) | Independent wall/thread CPU clocks, Iris stage/leaf scopes, frame-boundary recording latch, epoch filtering and queue-drop count | Retain start/end and nesting IDs, sample eligibility and thread-clock availability. Bound allocations; replace hot-path strings with registered IDs. The 240-sample rolling histories are HUD statistics, not whole-capture results. |
| `CompositeRendererTimingMixin` | Real Iris debug-group names; stage/leaf nesting; preserves Iris push/pop | Bind to graph generation and actual program identity. Validate hook coverage against pinned Iris. A program may contain multiple draws/dispatches or no effective work. |
| `ShadowRendererTimingMixin`, `FinalPassTimingMixin` | Existing shadow-map and final scopes | Shadow-map generation is not shadow filtering. Split final draw, mip generation, copies and blits at actual call boundaries. Graph witnesses already touch some of these sites; they are not equivalent to timed operation scopes. |
| `MinecraftFrameTimingMixin`, `GameRendererTimingMixin` | `run-tick`, `game-render`, `render-and-blit`, `swap-and-events`, limiter and frame identity | Record frame-start intervals with the benchmark's boundary convention. Split swap from event polling. `game-render` is not the whole submitted frame and neither GPU root measures scanout. |
| Sodium terrain/command/draw timing mixins | World vs shadow, opaque+cutout vs translucent, command fill vs execution wrapper vs native submission | Resolve active program/material group where practical. The existing opaque+cutout grouping must not be presented as individually measured opaque and cutout. CPU around `setupTerrain` is useful; a GPU interval around CPU-only setup is not terrain shading. |
| `EntityBuildTimingMixin`, `BufferedDrawTimingMixin` | Entity vertex building is CPU-only; actual buffered flushes carry GPU scopes | Do not assign the GPU flush to each entity callback. Add batch/program tags where available; retain mixed entity/block-entity groups when inseparable. |
| DH terrain/total/render-list timing mixins | Separate opaque/translucent, main/deferred, world/shadow and CPU list work | Parent/child accounting and graph-specific availability. Missing DH is `not_applicable`, not a measured zero or compatibility certificate. |
| [`BottleneckProbe`](../src/devClient/java/com/pp/shaderlod/dev/BottleneckProbe.java) | Explicit manual profiling, scene/configuration ownership, independent windows and `cpu.csv`/`gpu.csv`/`report.json` export | Define exact start/stop fences, bounded tail drain and per-record validity. Current before/after snapshots and textual partial-frame warning are not a complete event contract. Heavy work witnesses need their own inventory phase. |
| [ComparisonClient](../src/devClient/java/com/pp/shaderlod/dev/ComparisonClient.java), [README landscape notes](../README.md#landscape-comparison-and-gpu-timings) | Existing GPU/pass-ms display and asynchronous elapsed-query warning | Reuse labels/schema where sensible; simultaneous landscape windows share a GPU and cannot supply isolated baselines. No always-on new overlay required. |
| [`run_benchmark.py`](../scripts/run_benchmark.py), [`analyze_render_jfr.py`](../scripts/analyze_render_jfr.py) | Lifecycle/protocol and existing JFR limitations | Add a diagnostic command and offline report path, not a parallel launcher. Keep JFR CPU samples, native samples, waits and GC categories separate. |

These are implementation requirements, not fixes made by this document.

## 4. Architecture and ownership

### Separate purpose, shared harness

| Placement | Benefit | Problem | Decision |
| --- | --- | --- | --- |
| Detailed timers inside ordinary A/B scoring | Convenient matching of a pass to a score | Perturbs tiny frames, risks observer leakage and changes the proof protocol | Reject |
| Independent application/launcher | Flexible profiler experience | Duplicates input validation, worlds, restoration and remote operation handling | Reject for v1 |
| Explicit profiling subcommand in maintained harness, backed by existing dev probe | Shared preparation and recovery, clear diagnostic identity, exportable results | Requires lifecycle integration and a distinct result schema | Recommend |
| Optional profiling attachment after A/B scoring | Convenient later investigation of a proven change | Must recondition and revalidate; cannot retroactively explain scored frames | Later convenience using the same profiling engine |

Proposed interface: `profile` selects original native packs and a capture tier;
`profile-plan` (or equivalent dry-run option) validates a plan without launching;
an offline report operation reads saved captures. Final names belong in BENCHMARK
when implemented. A new top-level Python launcher, live daemon or production UI
is unnecessary. Reject candidate/optimization requests in native cost-map mode.

Responsibilities:

1. **Harness:** managed-slot lease, capacity checks, input identity, process
   startup/stop, conditioning, operation journal and restoration.
2. **BottleneckProbe coordinator:** one capture owner, lifecycle, frame-boundary
   arm/disarm receipts, window/tier/cadence and bounded in-memory collection.
3. **Scope registry and timers:** stable integer scope IDs, CPU wall/thread clocks,
   backend-specific GPU queries and explicit missing-result records. Existing
   mixins call this layer; no shader rewriting.
4. **Graph inventory and feature catalog:** captured outside profile windows;
   resolve original archive, effective options, selected sources, active programs,
   targets and graph generation into reviewed semantic labels.
5. **Offline analyzer/exporter:** validates joins and coverage, computes window and
   process statistics, creates report/CSV/trace. It must not silently repair
   malformed or incomplete captures into valid-looking totals.

The coordinator state machine is: idle → leased/prepared → inventory →
conditioning → armed-at-frame-boundary → capturing → stopped-at-frame-boundary →
bounded drain → export → restore/release. Errors and Cancel enter disarm and
recovery from every state. A graph reload, dimension/pack change, lost context or
unbalanced scope ends the current cohort; never merge old queries into a new graph.

Keep the existing measurement guard's protections, but distinguish a diagnostic
owner from a scored owner. Before scoring, verify **effective** timer state,
work-witness state, pending collector/drain activity and outstanding image leases,
not just requested flags. No diagnostic query polling, file writer, JFR session,
external capture or observer is allowed to survive into scored timing. Preserve
whatever minimal scoring instrumentation BENCHMARK already requires; this plan
adds none to its scored intervals. GPU/CPU latches take effect at boundaries,
so a request to turn off is not sufficient acknowledgment.

## 5. What “individual features” means

### Physical scopes first, semantic features second

Maintain two structures, not one ambiguous tree:

- **Execution hierarchy:** context → frame submission → stage → program/pass →
  invocation → draw/dispatch/copy/mip group. This has measured intervals.
- **Feature catalog:** named concerns such as bloom, shadows, fog and water, linked
  many-to-many to those scopes. This has evidence and attribution limits.

A pass key includes pack archive identity, effective options/preset, dimension,
Iris version, graph generation, stage/program and source identity. A GL program
number alone is unstable after reload; a string like `composite4` is not a global
feature name. Record invocation and parent IDs because the same name can recur.
Preserve actual viewport/target dimensions, resource roles and draw/dispatch kind.
Inventory source/target metadata once while disarmed; don't query GL state or
reparse shader text at each draw. Invalidate cached identity when the graph changes.

A reviewed catalog entry contains feature ID/display name, matching graph/program
identity, option/dimension conditions, source-member/include evidence, resource
producer/consumer roles, mapping revision and confidence. Labels may be shared
across packs; mappings must be versioned per family and style. This is pack-specific
**descriptive metadata**, not pack-specific optimization code. Unknown versions
still get raw pass names and an `unmapped` label; they do not inherit a guessed map.

### Initial source-backed mapping seeds

The original local archives were read without compiling or running them:
Complementary Reimagined/Unbound r5.9.3, BSL 10.1.8 and MakeUp Ultra Fast 9.5g under
`dependencies/upstream/other-pack-research/`. The following are source-location
seeds, **not a certified inventory of enabled/executed features**. Recheck effective
preprocessing, both vertex and fragment stages, compute stages and the observed
graph before activating any catalog match. Source includes alone do not establish
branch execution or exclusive cost.

| Family | Useful named-pass seeds | Required interpretation |
| --- | --- | --- |
| Complementary Reimagined / Unbound | `world0/*.fsh` delegates to `program/*.glsl`. `deferred1` contains SSAO logic and atmosphere/material work; `composite` contains cloud/reflection/material paths; `composite1` includes volumetric light, shadow sampling, water fog/refraction and reflection blur. `composite4` builds bloom tiles; `composite5` combines bloom; `composite6` has TAA and `composite7` conditional FXAA. | Keep both style identities even where sources resemble each other. Bloom fog can move among passes with options; never infer one immutable location. Deferred/composite costs often remain mixed. LPV/voxel lighting needs actual enabled producer/propagation/consumer inventory, including compute if present. |
| BSL | `program/deferred.glsl` produces AO; `deferred1` contains AO use and atmosphere/cloud work. `composite` contains light-shaft/water-fog paths. `composite4` builds bloom tiles and `composite5` consumes them with other post work. `composite6` includes FXAA; `composite7` conditional TAA. | Distinguish AO production from later consumption. The audit explicitly found native FXAA work and temporal writes in other composites; a pass that looks like a copy is not necessarily disposable or featureless. |
| MakeUp | `common/deferred_fragment.glsl` contains AO and volumetric clouds. `composite_fragment.glsl` includes volumetric light and bloom-source work. `composite1_fragment.glsl` includes bloom/blur; `composite2_fragment.glsl` contains TAA/motion-blur paths; `final_fragment.glsl` includes tone mapping and optional final effects. | Preserve mixed AO/cloud and temporal costs. Include vertex-stage exposure consumption and bloom mips: the [native source review](../dev/experiments/rtx5080-four-target-audit-20261106/SOURCE_REVIEW.md) records both. A fragment-only map is incomplete. |

For SSR/water and shadow filtering, inspect `gbuffers_*` and their includes as
well as deferred/composite stages. Actual program bindings under terrain and
buffered draws connect these shaders to runtime scope IDs. Never label all
`gbuffers_water` time “SSR”: shading, refraction, fog and reflection can share that
program. Likewise, do not call all of shadow-map generation “shadow filtering.”

### Required vocabulary and achievable resolution

| Feature/stage Philip can select | v1 measurement boundary | What cannot be isolated by that boundary |
| --- | --- | --- |
| Shadows | Shadow-map root, world/shadow terrain and buffered draws; separate identified shadow composites | Filtering/PCF/PCSS embedded in terrain, water or lighting shaders remains part of mixed passes |
| G-buffer / deferred lighting | Actual `gbuffers_*` program groups and deferred passes | Per-material shader branches unless they are already separate draws/programs |
| SSAO / GTAO | AO producer/filter passes where distinct; named consumers | AO mixed with clouds/lighting is tagged, not allocated a fabricated fraction; do not rename SSAO as GTAO |
| Volumetric / LPV fog | Named volume/voxel/propagation/resolve passes if enabled, including dispatches | LPV is not assumed present; inlined fog/filter costs are mixed |
| Clouds | Cloud producer/history/filter/compose passes and embedded cloud paths | Cloud marching in a mixed shader cannot have its own timer |
| SSR / reflections | Trace, history, blur and resolve scopes where separate; mixed water/material programs | Ray-march cost and reflection-only percentage inside a combined draw |
| Bloom / AA / upsample | Producer tiles/mips, combine, TAA/history, FXAA and actual upsample/blit stages | Native AA stays unchanged; an upsample stage is not assumed just to fill a row |
| Water / translucency | Vanilla and DH translucent terrain, buffered transparency groups and active programs | Water versus glass inside one batch; overlapping feature tags are not additive |
| Vanilla vs DH terrain | Independent world/shadow opaque/translucent roots, DH total and render-list CPU | DH worker work or compatibility when DH is absent; opaque+cutout remains grouped until measured separately |
| Entities / block entities | CPU builds and GPU buffered flush groups | GPU time per entity inferred from vertex building |
| Copies / blits / clears / mips | Actual operation sites, grouped by source/destination/role | Hardware bandwidth or “wasted” work; a long copy interval may include dependencies |
| CPU renderer / submission / presentation | Sodium setup/fill/execute/submit, Iris orchestration, swap, event poll, limiter; explicit known waits | Driver-internal execution, worker critical path and GPU idle without further evidence |

### Mixed and spanning features

A dedicated feature pass can contribute its measured interval once to that
feature. A feature spanning several dedicated nonoverlapping passes can have a
union cost for the same sampled frame. Its shared passes appear as separate
“contains this feature” rows. A mixed pass gets a stable composite label, for
example “AO + clouds,” and lists both tags. Do not split by source lines, texture
reads, branch counts, guessed weights or catalog author preference.

Feature search results may overlap; disable additive totals across overlapping
tags. The accounting view assigns each exclusive physical interval exactly once,
including explicit mixed and unmapped rows. “Time in passes containing fog” is
not “fog costs this much.” Turning fog off would change shaders, caches, history
and output; such an ablation is a separate future experiment, not v1 attribution
and not proof of independent removable cost. No feature toggles or synthetic
shader splitting are part of native captures.

Per-draw detail is bounded: fullscreen passes and copies often already have a
useful single-action boundary. Terrain multidraw and entity batches stay groups
in the default tier. A focused capture may inspect selected draw invocations;
it must not expand or split a multidraw to manufacture individual timings. Full
hardware draw/wave attribution belongs to supported external tools.

## 6. Measurement semantics and data contract

### Records

Use a versioned canonical JSON manifest plus bounded event tables; keep integer
nanoseconds until display. Exported IDs must be joinable without parsing names.

| Record | Minimum fields |
| --- | --- |
| Capture/cohort | Schema/tool/source versions, capture/run/process ID, operation receipt, native/absent/installed-Off identity, archive/options/resource-pack identity, preset, dimension, scene/world/camera, native resolution/AA, Iris/Sodium/DH/mod/JDK versions, GPU/driver/OS/Mesa/kernel, actual GL context, vsync/cap/display/compositor, graph/catalog revision, tier and sampling seed/cadence, restoration status |
| Window/frame | Window/epoch/frame ID; frame-start and next-start monotonic times; root boundaries; graph generation; warmup/partial/transition flags; sampled scope-set IDs and eligibility; end reason |
| Scope/invocation | Registered ID/name, parent/event ID, execution ordinal, stage/program identity, operation kind, thread/context ID, feature links and confidence, expected/observed status |
| CPU event | Wall start/end, thread CPU delta or null, clock source/resolution, sampling state; known wait-site classification and evidence level |
| GPU event | Issue frame/epoch, retrieval frame, begin/end query values when timestamps exist, elapsed duration, clock domain/counter width, backend, context, scope/invocation, availability/validity reason |
| Coverage/health | Eligible/executed/scheduled/issued/completed counts, scope and query-pool limits, conflicts, queue evictions, timeout/reset/disjoint indicators if available, unresolved tail, imbalanced scopes, timer overhead diagnostics, dropped bytes/events |
| Aggregate | Cohort/scope/metric/denominator, frames/windows/processes represented, mean/median/p95/SD/variance, missingness, inclusion policy, attribution grade and uncertainty |

Use explicit statuses: measured, unsampled, not executed, disabled by verified
configuration, not applicable, unsupported, conflict, dropped, pending,
invalid and unknown. Zero is reserved for a valid measured duration (possibly
below clock resolution) or a separately identified structural absence. An absent
CSV row is never automatically a zero-cost frame. CPU thread time unavailable
must remain null, not zero CPU activity.

### CPU accounting

For a same-thread scope, `wall = end - start`; thread CPU is the corresponding
current-thread clock delta. `wall - thread CPU` is **off-CPU/unattributed wall
time**, not automatically GPU wait. Clock resolution can create tiny negative
differences; report tolerance/invalidity rather than hiding large discrepancies.
This difference does not locate waiting inside the interval.

Split the pinned window implementation's swap call and event polling under the
existing parent. Report swap-call wall and thread CPU separately. A long swap call
may reflect vsync, the compositor, queue throttling or GPU completion; label its
off-CPU difference a swap-blocking estimate with cause unknown. Limiter/sleep is
separate. Instrument known fence/resource/query wait sites only where present;
do not insert new fences to discover waiting. A driver may also busy-spin, so
high thread CPU in a GL call is not necessarily useful Java work.

Use JFR or a separate sampled CPU capture for GC pauses, JIT, workers, scheduling
and native stacks. Whole-process CPU includes multiple cores and can exceed wall
time; it is not a render-thread slice. Whole-window worker CPU deltas do not
identify which frame needed that worker. Keep thresholded JFR waits and sampled
stack shares separate from measured scope durations and from one another.

### GPU accounting

Timestamp endpoints delimit command-stream completion progress, not exact shader
execution start/stop on every GPU unit. Elapsed intervals can include dependencies,
pipeline gaps and interference from other GPU work. See the [ARB timer-query
specification](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_timer_query.txt).
No timer backend supplies an occupancy or hardware busy counter.

For valid nested endpoints on one clock/context, derive a parent's exclusive
interval measure by subtracting the **union** of its complete contained children.
Do not sum overlapping children or subtract a sampled subset and label the
remainder “pure parent work.” Name it “outside observed children,” including
unmeasured work/gaps. Reject broken containment. Same-stream sequential intervals
can form a command-timeline stack; that is not a physical shader-unit occupancy
stack. Overlapping contexts/queues, if later collected, need separate lanes.

Elapsed-only records have durations but no GPU positions. They can rank sampled
scope costs, not reconstruct a same-frame GPU timeline. Parent and child sampled
on different frames cannot yield measured exclusive time. Do not subtract their
means to manufacture a residual; show separately observed rollups and leaves.

### Frame, clocks and percentages

Keep total frame-start intervals as the primary pacing quantity, matching the
standard benchmark convention. It is not GPU completion or display latency.
A GPU query's frame ID identifies submission ownership, not the frame during which
its result was read or its pixels scanned out.

CPU `nanoTime` and GL timestamp clocks are distinct. V1 records each clock and
shows separate rulers with an explicit unknown CPU/GPU offset. Submission IDs
link records without claiming queue latency. Never align lanes by first sample
and silently imply causality. Future correlation needs multiple anchors, offset/
drift/resolution uncertainty and an independently checked method; a synchronizing
GL timestamp read must remain outside representative capture windows.

Show CPU percentages against the named wall interval or thread-CPU total, and GPU
percentages only against a valid same-frame measured GPU root/union. Label the
denominator. A duration divided by frame-start interval is at most an elapsed-time
ratio, not GPU utilization; omit that percentage from the default report. Never
compute GPU idle as `frame - GPU`, GPU wait as `CPU wall - GPU`, or total frame cost
as a sum of CPU and GPU. Uninstrumented GPU space is unknown until supported
scheduler/hardware evidence explains it.

## 7. Capture tiers, sampling and speed budgets

### Three tiers

1. **Disarmed / everyday play:** no profiler queries, polling, exports or overlay;
   existing dev-only hooks return immediately. Nothing is shipped enabled.
2. **Survey:** coarse CPU roots plus sparse named CPU layers and GPU pass/groups.
   Record frame pacing every frame with the maintained minimal boundary collector.
   This is the main four-pack cost map.
3. **Focused detail:** short selected-scope capture with denser timestamps or
   narrower elapsed rotation; optional separate JFR/external trace. Clearly mark
   higher overhead. No automatic full per-draw capture of the whole game.

Initial survey settings to evaluate later: one eligible frame in 16, a maximum
of 64 timed GPU intervals per sampled frame (128 timestamp endpoints), and up to
128 detailed CPU invocations per sampled frame. These are bounded starting
settings, not guaranteed adequate coverage or recommended performance results.
If a selected group exceeds the cap, select a declared rotating subset and count
omissions; don't always keep only the first draws. Allow a separately declared
coarser cadence for expensive thread-CPU reads.

Timestamp backend: preserve nested endpoint support, but choose scope depth and
breadth by tier. Preallocate a globally bounded query pool after graph inventory;
per-name allocation in a hot frame does not scale. Keep at least the existing
delayed-availability behavior. Poll only on permitted frames under a bounded
poll budget. Never block for a result or use `glFinish`.

Elapsed backend: `GL_TIME_ELAPSED` queries cannot nest. Extend the existing seven
rotating categories to nonoverlapping named leaf sets. Keep frame roots and
inclusive rollups on separate sampled frames; subdivide IRIS or terrain sets when
query cost/coverage requires it. Do not begin a parent query that masks all its
interesting children. Preserve protection against another mod's active query;
record conflicts and degrade coverage rather than stealing ownership.

Use a reproducible balanced schedule with a recorded phase/seed. Rotate by
**sample ordinal**, not rendered-frame modulo, as the existing tests require.
Across repeated windows vary the phase or use a precomputed stratified schedule
to reduce aliasing with TAA/history and periodic work. Freeze the schedule within
a window. Adapt cadence only between windows, creating a new configuration cohort;
never adapt silently to whichever pass currently looks cheap.

CPU buffers also need bounded primitive storage, registered strings, no per-event
logging, and no collection-time stack capture by default. Do not perform graph
walks, shader hashing, image readback, serialization/compression or disk writes in
capture windows. Move completed data into bounded storage; if full, record loss
and stop/degrade the diagnostic explicitly. No unbounded queues or asynchronous
writer competing unnoticed with measured CPU work.

### Proposed acceptance budgets and later calibration

Survey target: **at most 1% mean total-frame perturbation** and at most 3% p95
perturbation against matched disarmed controls, assessed separately on each
pack/machine. At the recorded RTX baseline range, 1% is only about 20–34 µs per
frame. That arithmetic is a budget illustration, not a fresh measurement. For
focused captures, aim for at most 5% mean perturbation; above that, retain data
only as intrusive structural diagnosis, not representative costs. Disabled-hook
cost should be unresolved at a 0.2% practical sensitivity target; if the study
cannot resolve that, report the bound as unknown rather than claiming zero.

Later overhead study, separate from optimization scoring:

- Compare identical original input/scene stacks with development hooks disarmed
  and survey armed, in counterbalanced repeated windows/processes. Also compare
  the ordinary minimal collector to the disarmed profiling build to isolate
  dormant-hook cost. This requires prospective matched controls, not subtraction
  of historical absent-mod and installed-mod results.
- Compare CPU-only, GPU-only and combined collectors, then cadences/depths; select
  the cheapest configuration that preserves useful pass ranking and coverage.
  Use the same conditioning discipline and include return controls/drift.
- Measure sampled-frame stalls and following-frame stalls, query retrieval time,
  GC/allocation and tail latency as well as window mean/p95. Sparse perturbations
  can disappear in an average while still changing the GPU pipeline.
- Compare sparse distributions against a short denser capture and independent
  external evidence where supported. Check phase bias and whether expensive
  frames disproportionately lose queries. Empty timer pairs provide resolution/
  cost context; never blindly subtract them from every duration.
- Require observed uncertainty small enough to support the budget claim. If
  between-process noise exceeds 1%, “within budget” is not established. Reduce
  instrumentation, broaden repetition within authorization, or report failure.

## 8. Reliability, capture lifecycle and statistics

Inventory and verify original sources/options/graph before warming. Keep
resolution, native AA, camera, dimension, day/weather and workload pinned using
the existing scene machinery. Record rather than silently freeze Iris animation
clocks/history; world-time freezing does not make clouds or TAA deterministic.
Record actual thermal/power/display context and read-only clock telemetry where
available outside hot paths; do not lock clocks or change personal driver settings
as an implicit step.

Start at a complete frame boundary after conditioning and reset epochs without
unbalancing open scopes. Stop new events at a complete boundary. Drain already
issued GPU queries for a bounded unscored tail, preserving their original frame
identity and recording unresolved endpoints. A drain is a distinct state: it may
poll while no new samples issue, but it must finish before any scored operation.
Keep first/last partial frames explicit. A textual warning alone is insufficient;
the analyzer must enforce exclusions and give counts/reasons.

On timer faults, unavailable counters, context loss or scope imbalance, close only
owned queries safely, disarm and preserve the failure receipt. A CPU-only result
can remain useful but must be labeled partial; it cannot pass GPU capture
acceptance. Keep hardware/context capability decisions local. Do not force a
higher GL context just to make a chart available.

Statistics must distinguish:

- invocation cost from the sum/union of invocations **per eligible frame**;
- conditional cost when a feature executes from its occurrence rate;
- frame-to-frame jitter, variation among window means, and between-process
  variation;
- sparse observation count from total frames and effective independent repeats.

Report mean, median, p95, SD/variance and counts for appropriate metrics. Do not
stack pass p95s into a frame p95. For process comparisons use process-level
summaries; frames are autocorrelated, not thousands of independent experiments.
Optional block-bootstrap intervals need declared blocks and enough repeats;
with two processes, show both points/range and descriptive SD, not false precision.

For variable invocation counts, sample by frame/group independent of duration,
then aggregate per sampled frame. Occurrence rates require a separately calibrated
census or a representative sampling estimate; do not infer them from only
successful query returns. Cross-frame elapsed summaries must list the contributing
windows and assume stationarity explicitly. A drifted window does not supply a
credible synthetic “average frame.”

Initial completeness gates: no unexplained buffer eviction; at least 99% of
issued queries resolve or a clear partial-result status; target at least 100
valid frame observations per regularly active ranked scope across the repeated
capture, spanning multiple windows. These are engineering minimums, not statistical
certainty. Rare/disabled scopes get an explicit status, not fabricated samples.
Report full-frame rollup coverage separately from scope-query completion and from
semantic mapping coverage. If unmapped/mixed/uncovered work dominates, that is a
finding and a reason to improve measurement, not permission to guess.

Restoration remains mandatory on normal exit, Off, Cancel and failed Apply/error:
restore owned configuration/scene/display changes, stop owned collectors and
release leases. Unknown concurrent edits must be preserved under the existing
ownership/CAS rules. Keep query objects and buffers bounded and dispose them on
owned shutdown. Do not kill unrelated processes or delete old captures.

## 9. Exports and the decision report

Keep existing `cpu.csv`, `gpu.csv` and `report.json` concepts, with an explicit
schema version and migration readers for old duration-only captures. Add
`frames.csv`, scope/catalog metadata and a compact capture manifest. Raw records
are the canonical evidence; all charts are reproducible derivatives. CSV needs
proper quoting, defined nulls and integer-unit columns. Late data must not silently
rewrite a previously finalized report; use finalization state and checksums.

V1 report: one offline HTML file with embedded summarized data, searchable tables
and a bounded detail trace, plus CSV/JSON for other analysis. No server, cloud
upload or live overlay is necessary. Keep raw traces/captures in the operation's
owned ignored report tree; commit the schema, renderer, reviewed catalog and compact
findings under the [repository artifact policy](GIT_AND_ARTIFACTS.md).

Required views:

- **Overview:** pack/scene/machine/configuration, validity and overhead-calibration
  status; frame-start distribution and window/process points. Show milliseconds
  before percentages. Display historical baselines only as separately labeled
  context, never as matched controls.
- **Cost table:** named stage/feature, underlying pass IDs, CPU wall/thread time,
  GPU elapsed distribution, samples/coverage, mixed/unmapped status and variance.
  Sorting GPU and CPU independently should make sense. Shared semantic categories
  allow four-pack comparison without assuming identical algorithms.
- **Frame graph and hierarchy:** CPU wall stack with expandable children; separate
  thread-CPU totals; separate GPU command timeline for valid timestamp captures.
  Inclusive parents are context, not additional stacked cost. Keep gaps visible.
  On elapsed-only captures, show observed slices/distributions and a clearly
  labeled cross-frame summary, not a completed stacked frame.
- **Flame view:** instrumented CPU wall hierarchy or optional sampled CPU stacks,
  with units and source clearly distinguished. A sample-count flame graph must
  not be labeled milliseconds or GPU percentage.
- **Trace detail:** adjacent frames, stable feature colors plus text/patterns,
  scope hierarchy, actual program/draw-group identity and sampling/clock limits.
  Link mixed feature rows to all contributing intervals without duplicating cost.

Export Chrome Trace Event JSON consumable by Perfetto for CPU events in v1.
For uncorrelated GPU timestamps, export a separate GPU-only trace with its own
origin and explicit clock metadata; do not put falsely aligned GPU slices beside
CPU slices in a common timeline. Elapsed-only data stays duration tables/counters,
not invented start times. Later native Perfetto clock snapshots/flow events can
support validated combined traces. IDs can link submission to GPU records, but
uncalibrated flow arrows must not imply measured queue delay.

Philip's review should end with a small decision record: selected feature/pass
family, relevant packs/machines, observed cost and uncertainty, mixed/unknown
portion, question needing deeper evidence, and his chosen next investigation (or
“not worth pursuing”). No automatic G-number generation, recommendation of a new
optimization, or retention decision based solely on a cost ranking.

## 10. Later native four-pack capture program

**This section is a prospective study, requiring later runtime authorization.
It does not reopen or repeat the completed candidate audits.**

Use eight primary pack/machine cohorts: Reimagined, Unbound, BSL and MakeUp on
Asahi at 2560×1600, then the native Windows RTX 5080 at 3840×2160 when allocated.
Record the actual future driver; 617.42 describes the completed RTX audit, not an
assumed immutable environment. RTX 2080 Ti is outside allocation and contributes
no planned runs. Historical availability language elsewhere does not override STATUS.

1. Reuse prepared slots and audited original archive/options identities. Verify
   current inputs before use; do not silently reset drifted profiles. Profile/world
   copies, if necessary and authorized, use `run_storage.copytree` and its capacity
   checks. Preserve the maintained free-space reserve and report limits.
2. First qualify collector behavior and overhead on Asahi; its elapsed fallback
   must produce a useful report without NVIDIA features. Then validate timestamp
   behavior and very-short-frame overhead on RTX. Backend selection is by observed
   capability/counter health, not vendor-name assumptions.
3. Begin with one existing representative saved Overworld pose per pack/machine
   tied to the prior audit identity. Proposed initial schedule: the existing
   120-second conditioning discipline, three 20-second diagnostic windows in
   each of two fresh processes, with declared sampling phase variation. Keep
   ordinary original-only baselines, installed-Off controls and instrumented
   native-pack captures distinctly labeled. Native means original pack bytes,
   not automatically “main mod absent.” Prove any neutral/profiling artifact's
   dependency requirements before claiming an absent-mod diagnostic cohort.
4. Retain prospective disarmed controls for overhead, not a fresh rerun of all
   historic baselines or optimizations. With eight primary cohorts and two
   processes, the initial profile matrix is 16 sessions. Conditioning plus three
   windows alone is 48 minutes in total; startup, settling, controls, drain,
   recovery and calibration add time. This is a planning estimate, not an
   authorization, fixed completion promise or measured runtime.
5. After Philip sees the first map, use only the missing feature coverage to choose
   additional scenes: water/translucency, sky/clouds, dense entities or interior
   shadow/filtering. Run identical scene definitions where meaningful, keep results
   separate, and declare unobserved features. One pose is not a game-wide profile.
6. DH-enabled terrain and PBR/resource-pack modes are separate compatibility and
   graph cohorts after authorization and readiness evidence. Keep vocabulary/hooks
   in v1, but do not claim live DH/PBR coverage from the no-DH native matrix. Other
   dimensions, dynamic traversal and loading hitches are later studies.
7. Stop each operation with a capture-health and restoration receipt. Retain raw
   evidence locally, track compact findings/catalog revisions and link completed
   results through EXPERIMENTS. Update STATUS only with actual completion/limits.

Cross-machine comparison is primarily about **where time goes within each native
configuration**, not GPU speed ratios: resolution, CPU, OS, drivers and possibly
backend differ. Compare named scope rankings and coverage with uncertainty; do
not normalize Asahi timings to 4K or pool distributions across machines.

### External calibration, later and occasional

On an authorized RTX session, first verify the installed Nsight Graphics/Systems
versions support the actual OpenGL context, Blackwell GPU and driver, and that
required permissions are available. Do not install an old Range Profiler release
solely because it appeared in research. Preserve normal native context and
settings; any changed capture environment becomes a separate cohort.

Use the same reviewed scope IDs in Iris/KHR_debug marker streams where available;
keep stable feature colors in the offline catalog (GL markers themselves do not
supply the report's color semantics). Take short representative captures for
shadow, composite, terrain and copy-heavy regions. Compare event ordering, selected
program/target identity, range boundaries and broad cost ranking. Investigate
systematic disagreements rather than scaling internal timings to fit the vendor
report. Distinguish live traces from replay and incremental GL timings from
hardware activity/counters. Run captures separately to avoid competing query
collectors; matched scene/graph identity is necessary but does not make separate
captures identical frames.

Use Systems for CPU/scheduling/GL waits and Graphics for supported hardware
activity evidence. Any busy/idle conclusion must name its actual counter or
scheduler source and missing contexts. Calibration on NVIDIA verifies that path;
it does not validate Apple/Mesa timing. On Asahi, optional perf/JFR scheduling
analysis and gpuvis/Perfetto driver events can provide independent evidence only
after checking actual support. If no hardware activity source exists, retain
“GPU busy/idle unknown.” No v1 release criterion requires Nsight on Asahi.

## 11. Delivery sequence and acceptance

All steps below are future work. This design does not authorize starting them.

| Stage | Deliverable | Acceptance evidence |
| --- | --- | --- |
| 1. Event contract and registry | Versioned records, frame/epoch/invocation identity, complete missingness, bounded buffers | CPU-only synthetic tests for repeated names, scope balance, graph changes, late results, counter wrap/reset, partial boundaries, overflow and malformed records |
| 2. Timers and lifecycle | Extend existing timers/probe; disarmed/scored ownership gate; non-nesting leaf rotation; stop/drain/recovery | Mock query tests prove no queries/polls on unsampled ordinary frames, no nested elapsed query, no late cross-epoch contamination; error/Cancel/Off cleanup tests; production jar contents reject dev timers/mixins/resources |
| 3. Named coverage and catalog | Actual passes plus copy/mip/blit/clear and CPU swap/event splits; reviewed four-pack mappings | Static source/graph fixtures, unknown-version fallback, disabled/mixed feature cases; later runtime invocation and graph coverage checks |
| 4. Offline report/export | Tables, frame hierarchy, variance, CPU flame option and honest backend-specific trace | Fixture round trips and golden numeric summaries; no double counting, sparse zero fill, fabricated clock alignment or false GPU stack; existing reports still readable |
| 5. Authorized machine validation | Asahi then RTX capture/overhead evidence and representative external calibration | Per-cohort budgets, coverage/sample counts, query health, repeatability, native identity and verified restoration; unsupported capability stays explicit |
| 6. Philip's first review | Eight-cohort native cost map and selected next questions | Philip can trace each named cost to measured scopes and see unknowns; no optimization or promotion happens automatically |

V1 includes the existing two GPU backends, named passes/groups and CPU scopes,
feature metadata for the four pinned targets, mixed/unmapped accounting, capture
health, sparse survey/focused tiers, harness ownership and offline CSV/JSON/HTML/
trace exports. JFR remains an optional separate diagnostic attachment using the
existing analyzer. First-class integration of every external tool is unnecessary.

Later sophistication: validated CPU/GPU clock correlation, scheduler/off-CPU and
worker dependency traces, per-draw selected capture, richer resource-dependency
views, optional native Perfetto/Tracy adapters, broader graph/version mapping and
purpose-designed feature ablations. Hardware counters/ISA/occupancy remain
external specialist evidence unless separately scoped. None is required to get
an honest first pass-cost map to Philip.

## 12. Risks, non-goals and the proof boundary

The main risks are observer distortion at 2–3 ms frames, timer-driver differences,
non-nesting collisions, silent sample loss, temporal sampling bias, graph drift,
mixed feature labels, incorrect parent accounting and false clock correlation.
Every one has an explicit status or acceptance check above. Query placement can
alter batching or tile behavior even without blocking; a low CPU overhead number
alone does not validate GPU pass attribution. Missing instrumentation is a result
to show, not a reason to fill the graph with estimates.

This system will not claim pixel/HDR equivalence, motion quality, DH/PBR
compatibility, occupancy, ISA cost, memory bandwidth, per-pixel feature cost,
end-to-end input/display latency or a universal CPU/GPU bottleneck from timer
ratios. It will not change original shaders, native resolution/AA, enable an
optimization by default, train models, lock clocks, require a vendor SDK in the
mod, or make runtime adaptive optimization decisions. Detailed instrumentation
must remain absent from production jars, with artifact checks to enforce that.

**Cost map → Philip chooses a question → separately authorized investigation →
existing A/B proof.** A future change still needs the maintained native/candidate/
native-return total-frame protocol, ABBA/BAAB where applicable, conditioning,
drift review, quality stills/motion as required, compatibility and restoration.
All observers/work witnesses must be disarmed before scored intervals. A reduction
in a diagnostic pass cannot replace total-frame evidence or weaken the current
all-four-targets/both-completed-machines retention rule in STATUS.
