# Native Photon cost attribution plan

2026-10-05. Source-only plan by the fresh performance chat's bounded
`optimization_research` agent. Only this new document was written. No compiler,
client, build, encoder, shader change or GPU experiment was run. Root remains
the sole GPU operator and prepares/runs this plan after its current client ends.

**Root's immediate experiment:** original Photon with main mod and DH absent,
native2976×1672, existing seven-category attribution, detailed CPU timing and
interval5. Forest root session64539 and coastal48687 completed exit0 with
accepted input/class/artifact/native-window/PNG contracts at this checkpoint.
They identify baseline costs without claiming an LOD gain. No new effect cut or
attribution hook is required. Source-preserving B15 same-JVM comparisons are a
later step, after these original captures and an instrumentation control.

## Evidence and immutable source boundaries

Current private reduced-SSR same-JVM forward adjacent GPU changes were +2.10%
and −13.78%; reverse −0.64% and +0.73%. No stable benefit was established.
B15 all-module native controls showed roughly 6–11% within-session signals;
the ordinary ≥15% gate remains unmet. Earlier serial regressions, negative
terrain GPU phases and the B15 glint-quality failure remain part of the evidence.
See [native results](PHOTON_NATIVE_RESOLUTION.md),
[SSR controls](NATIVE_INPROCESS_SSR_COST.md) and
[glint audit](PHOTON_GLINT_SOURCE_AUDIT.md).

Pinned archives:

| Source | SHA-256 |
| --- | --- |
| Original Photon v1.3b, `dev/upstream/photon_v1.3b.zip` | `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` |
| Protected B15, `build/photon-lighting-tier-no-atmosphere.zip` | `b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c` |
| Existing private reduced far-SSR enabled archive, as qualified by SSR-cost mode | `636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83` |

The private archive is for its existing SSR-cost diagnostic only; do not replace
B15 silently in a general effects session. Main/shared source ownership remains
with the protocol chat. This plan changes instrumentation settings and uses
the existing global bridge control, not shader source or module quality.

Local source snapshot SHA-256 (runtime transformed classes are separate):

- `GpuTimers.java`: `7c515fbc606809e35a2ebcabe7a82dc8d198ec4a4ec13b6fdc30307b4087feeb`.
- `GpuSamplingPolicy.java`: `cd42fbdfdd9aebecce5d40c65d9c7b079a63519c41ec1b055dc45d6c6bf811f3`.
- `CpuTimers.java`: `f7787c52d98c38f2bd70773a9fb659865ba20e3b9eb8b8e59aca2cb6b9c8b91a`.
- `build.gradle`: `12956c565c3d00c0c028dbad6a46bb5e4cd5bdc3e559bb26fd210703e6bb773f`.

Retain actual class/resource/jar hashes in each run. Reuse a previously compiled,
frozen harness only when its source/API qualification still applies; excluding
`compileDevClientJava` does not freeze main Java classes or transformed runtime
classes by itself. No claim of shared byte-identical instrumentation across
historical sessions follows from these new source hashes.

## Existing scope-to-source mapping

Exact B15 Overworld wrapper members identify physical pass names. Runtime Iris
debug-group names and enabled passes still determine the actual exported scopes.

| Timer scope | B15 source / work | What it cannot isolate |
| --- | --- | --- |
| `iris/deferred` | `world0/deferred.fsh` → `program/d0_sky_map.fsh`: fixed sky map, lighting storage, sky/cloud evaluation | Individual atmosphere/cloud/noise calls or cached-lighting quality; cached sky is off here |
| `iris/deferred1` | `world0/deferred1.fsh` → `program/d1_clouds.fsh`; `:211` calls volumetric cloud layers | Primary vs secondary samples, density vs scattering, loop/register/texture-unit costs |
| `iris/deferred2` | `program/d2_clouds_upscaling.fsh`: full-resolution temporal reconstruction, reprojection and history | Original cloud march cost; no new march occurs merely because this pass is full resolution |
| `iris/deferred3` | `program/d3_ao.fsh`, AO scale `:84` is 0.5; protected AO budgets | Individual GTAO filtering vs reprojection or history bandwidth |
| `iris/deferred4` | `program/d4_deferred_shading.fsh`, opaque deferred surface lighting; SSR call `:602` plus atmosphere/sky, shadow/AO filtering and composition | SSR alone; most surface lighting stays original and the pass has several consumers |
| `iris/composite` | `world0/composite.fsh` → `program/c0_vl.fsh`; air fog call `:180`, optional underwater/End/LPV paths | Fog density vs shadow fetches vs transmittance; backend/resource work is included |
| `iris/composite1` | `program/c1_blend_layers.fsh`: refraction/layer blending, foreground clouds `:430–442`, fog `:447–450`; DH-water shading only when that integration executes | Vanilla-water normal/SSR work, which was already submitted in water geometry |
| `iris/composite4` | `world0/composite4.fsh` → `program/c4_taa_exposure.fsh`: temporal antialiasing, exposure and associated history | A distance-LOD effect; a large interval does not justify reducing its quality |
| `terrain/world/translucent` | Sodium translucent terrain draw; `world0/gbuffers_water.fsh` → `gbuffers_all_translucent.fsh`; water parallax `:468`, normal `:477`, SSR `:647` | Water alone vs glass/other translucency, normal vs SSR vs raster/overdraw |
| `terrain/world/opaque+cutout` | Sodium solid call includes solid and cutout; POM/material geometry in `gbuffers_all_solid.fsh` | Separate cutout/cutout-mipped costs; their pinned calls are no-ops |
| `iris/shadow-map`, shadow terrain scopes | Actual shadow generation / Sodium shadow draws | Surface shadow-map filtering in deferred/translucent lighting |
| DH draw/total scopes | Existing DH terrain delegates vs whole DH stages, if DH is present and submitting | Guaranteed geometry replacement coverage or water-only cost |
| `buffered-draws/world/...` | Actual Iris entity/block-entity batch flushes | Per-entity completed GPU cost; entity callback timers measure CPU vertex building |
| `iris/final`, `render-and-blit` | Final pass/copies vs broader clear/render/screen-blit interval | Presentation/swap/limiter costs outside their boundaries |

At native 2976×1672 with TAAU render scale 1, cloud upscale 4 uses 744×418
march targets, **1/16 of native pixels**, not one quarter of pixels.
`shaders.properties:273–275` sets colortex9/10 to 0.25 in each dimension.
Fog/AO half dimensions are 1488×836, one quarter of pixels. Fixed sky map target
is 192×108 (`:253`), cloud coverage target 512×512 (`:261`). Actual framebuffer/
viewport settings remain authoritative; verify TAAU/render scales from effective
options before using these dimensions. Upscale/history/terrain passes can
dominate despite small march buffers. Do not enlarge clouds to manufacture cost.

Iris pass intervals also include compute (if enabled), mipmap generation,
bindings, uniform updates and fullscreen draws. `deferred4_a` is gated by
SH_SKYLIGHT at `shaders.properties:174`; its absent record cannot prove zero
cost unless the option and actual program state explain absence. No pass timer
exposes register allocation, occupancy, physical ray counts or GPU utilization.

## Exact profiles and controls

Root prepared fresh original profiles from these accepted sources and removed
DH physically from the fresh runtime copies. Previous forest source had DH;
the new original forest has no DH, so it is not a matched DH-present comparison.
Parent control/manifest and copied-world/class/input hashes are retained in
`run-workloads/photon-native-attribution-r1/{forest,coastalwater}/attribution-provenance.json`.
Do not overwrite either source or prepared evidence.

| Immediate original workload | Accepted source → prepared runtime | Fixed control |
| --- | --- | --- |
| Forest | `native-terrain-b15-r2/forest/high` → `photon-native-attribution-r1/forest/high` | `(256.5,95,768.5)`, yaw135/pitch9, time7000 clear |
| Coastal glint | `photon-ssr-cost-controls-r1/coastalwater/high` → `photon-native-attribution-r1/coastalwater/high` | `(500.5,68,660.5)`, yaw87.38/pitch3.735, time12500 clear |

Paths in this table are below `run-workloads/`. Both use original Photon hash,
minimal stack, requested/effective24, no main mod, no DH, fixed wetness0 and
their original upstream quality. They are one unchanged-source capture per JVM,
not four bridge phases. The coastal glint pose is distinct from the earlier
south-facing ordinary coastal scene; do not silently combine their timings.

Later paired profiles, if baseline attribution warrants them:

| Workload | Existing source profile / control | Required held state |
| --- | --- | --- |
| Ordinary forest | `run-workloads/native-effect-isolation/forest/adaptive`; parent control `(256.5,95,768.5)`, yaw135/pitch9, time7000 clear | B15, minimal, DH present/reach128, requested/effective24, all modules true |
| Ordinary coastal | `run-workloads/native-coastal-effects/coastalwater/adaptive`; parent control `(240.5,68,680.5)`, yaw180/pitch3, time7000 clear | B15, minimal, DH absent, requested/effective24, original environment/sky reflections enabled, all modules true |
| Separate repaired far-SSR diagnostic | `run-workloads/photon-ssr-inprocess-reduced-reverse-r2/coastalwater/adaptive`; parent control `(500.5,68,660.5)`, yaw87.38/pitch3.735, time12500 clear | Exact private reduced hash, `ssr_cost`, only SSR module true, no DH, private switch true in all phases |

Root must check that a cloned profile still contains its expected archive,
control, options and save. Paths are references, not instructions to move mod
files. Ordinary forest/coastal use `benchmarkFeature=effects` and change only
live enable in OFF/ON/OFF/ON; reverse changes start order in a second fresh
session. Configured debug, cached sky, analytic water, terrain policy and every
CPU culling/list/coherence experiment remain off. Protected lighting and
use-mod-config remain true. Shader/module settings and sampler bindings remain
fixed for all phases. Preserve exact clocks/weather/wetness and window pacing.

B15's glint failure means its aggregate attribution is diagnostic, not accepted
quality. Do not interpret a B15 speedup as usable SSR optimization. Run the
quality-repaired SSR diagnostic as a separate source-qualified workload only
if its cost still needs localization; current forward/reverse results are flat
or inconsistent. Do not combine its percentage with ordinary B15 results.

## Instrumentation tiers, all already available

| Tier | Gradle properties | Purpose |
| --- | --- | --- |
| Primary aggregate | `gpuTiming=true gpuPassTiming=false gpuSampleInterval=16 cpuLayerTiming=false` | Preserve current low-overhead whole-render comparison |
| Attribution capture | `gpuTiming=true gpuPassTiming=true gpuAttribution=true gpuSampleInterval=5 cpuLayerTiming=true` | Seven-category GPU clues plus all detailed CPU scopes; root uses45s warmup/60s capture |
| GPU-query-free overhead control | `gpuTiming=false cpuLayerTiming=false` | Whole CPU clocks/FPS without GPU queries; produces no GPU milliseconds |
| Optional pass corroboration | `gpuTiming=true gpuPassTiming=true gpuAttribution=false gpuSampleInterval=5 cpuLayerTiming=false` | More IRIS pass samples, but two-category sampling has a checkerboard-parity limitation |

Do not pass a fabricated category-select flag: none exists. `build.gradle`
maps GPU flags and `cpuLayerTiming` at `:109–153`; it does **not** currently map
`-PcpuTiming=false` to the client. CPU timing defaults on for comparison clients.
The above GPU-query-free control is therefore CPU-only, not completely
instrumentation-free. A fully timer-free experiment is outside this existing
launch mapping unless root deliberately provides a verified client JVM property.

`run_proof_matrix.py:482` hardcodes frame-only/layer-off settings. Its current
CLI cannot request attribution; do not invent `--pass-timing`/`--attribution`.
Use direct existing Gradle mappings and existing benchmark analyzer instead.

Exact original-forest launch recipe, executed by root (not this worker):

```sh
./gradlew runClientLodOff \
  -PrunDirectory=/Users/pippo/github/shaderlod/run-workloads/photon-native-attribution-r1/forest/high \
  -PperformanceStack=minimal -PincludeDh=false \
  -PmeasureWorkload=true -PmeasureOnly=true \
  -PinitialWarmup=45 -PmeasurementSeconds=60 \
  -PgpuTiming=true -PgpuPassTiming=true -PgpuAttribution=true \
  -PgpuSampleInterval=5 -PcpuLayerTiming=true \
  -PfixedWetness=0 -PbenchmarkWindowScale=2 -x compileDevClientJava
```

The coastal launch uses the same flags and
`-PrunDirectory=/Users/pippo/github/shaderlod/run-workloads/photon-native-attribution-r1/coastalwater/high`.
These prepared profiles are already in use/evidence; any repeat needs another
fresh clone. `WorkloadSession` writes `workload-measurement.json`, not
`benchmark.json`; do not feed it to the in-process analyzer. Root's existing
workload contracts/summary helper must validate its input, archive, artifacts,
window and class identity. No unsupported runner CLI option is needed.

A later B15 four-phase control instead uses `runClientLodOn`, its separate fresh
qualified profile and the existing `gpuBenchmark=true benchmarkOnly=true
benchmarkFeature=effects benchmarkStartEnabled=false` properties, with matched
warmup/settle/capture durations. Reverse sets startEnabled=true. Only those
reports go to `scripts/analyze_inprocess_control.py` with expected B15 hash.
Actual2976×1672, not windowScale2 alone, is required throughout. No launch during
another client or concurrent protocol-source rebuild is authorized by this plan.

## Sampling, overlap and resolution safeguards

This M3 runtime previously reported timestampBits=0, elapsedBits=32,
ELAPSED_ALTERNATING. Recheck actual backend/counters; do not infer support from
OpenGL version. Seven-category frames are FRAME, IRIS, TERRAIN, DH_DRAW,
DH_TOTAL, BUFFER_DRAWS and RENDER_AND_BLIT. Each category recurs every `7*N`
runTick frames, not every N. In no-DH coastal runs, the two DH categories still
consume schedule slots. Do not classify missing DH records as zero submitted
work. At 50–60 FPS and N=5, a 60s capture provides roughly 86–103 opportunities
per category; actual successful counts, drops and pending results decide usability.

For cloud upscale4, `d1_clouds.fsh:128,162` uses frameCounter modulo16 for
checkerboard offset; `:208` advances outer noise by frameCounter/16. N=16 can
repeatedly query one offset. Seven-category N=5 recurs at35 frames, coprime to16
**if** Iris frameCounter and measured runTick frames advance one-to-one.
Use existing phase frameCounter/frameId boundaries to check that assumption;
they do not expose every queried frame's exact shader counter. Label this an
alias-reduction diagnostic, not proven full coverage. Two-category recurrence
is `2*N`, always even, so each IRIS scope can sample only a subset of16 offsets.
Do not prefer its denser statistics without acknowledging that limitation.

There is no nested elapsed decomposition. FRAME, pass and terrain records come
from different frames. DH-total contains DH draws; shadow-map contains shadow
terrain; CPU game-render contains terrain/Iris/driver submission; execute-batch
contains native draw-submit. **Do not sum parents and children, sum different
category medians, or subtract pass totals from game-render as driver cost.**
Elapsed intervals include command starvation/scheduling and tile effects; the
unmarked gaps between leaf passes are not measured GPU busy/idle time.
The [Khronos timer specification](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_timer_query.txt)
defines completion timing, non-nesting elapsed queries and tile-order caveats.

Keep zero results, zero fraction and minimum positive result visible. A zero
median for deferred sky or a small cloud pass is unresolved timer granularity,
not proof of cache/free work. It also does not license adding synthetic workload.
The sky program executes in source; no pixel parity or meaningful clock precision
follows from its tiny query values. Ring drops, stale epochs, missing backend,
GL errors or query shutdown invalidate attribution rather than becoming zero cost.

## Actual original captures: qualified cost clues

Root reports both completed sessions passed the existing source/input/artifact/
class/window/PNG contracts. This worker inspected only the small summaries,
not the approximately190MB forest raw report or the coastal million-row CPU
stream. Summary paths are
`dev/logs/photon-native-attribution-{forest,coastal}-r1/summary.json`.
Both use the documented45s/60s/sample5/seven-category/detailed-CPU recipe.

| Observed scope, GPU mean ms | Forest | Coastal glint |
| --- | ---: | ---: |
| `game-render` | 13.048 | 23.845 |
| `iris/deferred` sky map | 0.747 | 1.809 |
| `iris/deferred1` cloud march | 1.427 | 4.069 |
| `iris/deferred2` cloud reconstruction | 0.452 | 0.463 |
| `iris/deferred3` AO | 0.398 | 0.538 |
| `iris/deferred4` opaque deferred lighting/reflections | 1.260 | 2.258 |
| `iris/composite` volumetric integration | 0.686 | 1.095 |
| `iris/composite1` layer composition | 0.485 | 0.633 |
| `iris/composite4` TAA/exposure | 0.534 | 1.049 |
| `iris/final` | 1.285 | 1.285 |
| `terrain/world/opaque+cutout` | 1.029 | 1.231 |
| `terrain/world/translucent` | 0.136 | 3.684 |
| `iris/shadow-map` | 0.394 | 0.529 |
| `render-and-blit` | 12.290 | 23.380 |

Each listed scope has120 forest /68 coastal records. Forest FPS70.027, coastal
39.779. These are different cameras/time/material coverage, not an A/B pair;
no sum or workload-percentage decomposition is calculated. Clouds and
translucent terrain are useful coastal intervals to revisit; original-water SSR,
normal/parallax/raster cost cannot be separated within the latter by this timer.

The coastal aggregate is materially slower than the earlier roughly15ms
frame-only original glint controls. Those runs differ in instrumentation and
session conditions; do not label23.845ms the uninstrumented budget or attribute
the difference to pass queries, CPU markers, weather, driver or a shader cause.
Within these captures, render-and-blit mean is below contained game-render
mean: their records come from different category frames, illustrating why
parent/child ordering cannot be reconstructed from these averages.

Both `terrain-setup/world` GPU streams are all-zero, while their CPU work is
nonzero. Forest CPU setup mean0.429ms wall /0.427ms thread. Zero GPU setup
does not establish free preparation. Small shadow/sky intervals also include
zeros: coastal shadow-map zero fraction35.29%, sky-map7.35%; preserve the
ambiguity rather than removing zero samples. Median and tail remain in summaries.

Forest CPU game-render mean11.009ms wall /7.481ms thread; coastal15.690/7.180ms.
Forest per-frame CPU command-fill wall0.262ms, execute-batch2.146ms and nested
native-submit2.013ms. They are original submission intervals, include timing
overhead and must not be added to their parents or labeled a shader cost.
The coastal wall/thread gap is unaccounted waiting/off-thread/scheduling time,
not a proven GPU fence or driver stall.

**Root's matched instrumentation control is now running:** session80447 uses
another fresh original coastal clone, same45s warmup/60s capture, sample5,
`cpuLayerTiming=true`, but `gpuPassTiming=false`; attribution remains true but
is ignored in frame-only policy. Root reports source-world/class/options/scene/
native-window/minimal/no-DH inputs held equal. Completion and analysis are not
claimed at this report freeze. This retains detailed CPU marker overhead and changes
the GPU query regime from seven categories to frame-only. It also changes
which frames carry aggregate queries, so it is not a perfect isolated test of
one marker. Reverse/repeat if a substantial timing difference appears. A
subsequent `gpuTiming=false cpuLayerTiming=true` control can assess the whole
query regime without calling its FPS values GPU milliseconds. No extra run was
performed or required to freeze this report.

## Analysis and bounded stopping decision

Validate original-workload source/config/window/artifacts with its retained
contracts; use the strict in-process analyzer and restoration checks for later
four-phase reports. Then group raw GPU/CPU records by `(session, phase, epoch, scope,
frameId)` before reporting per-frame totals for repeated scope invocations.
Report actual sample counts, mean/median/tail and zero/drop diagnostics per scope.
Never divide one invocation mean by frame time to claim a workload fraction.

Detailed CPU wall/thread clocks are recorded every frame. For query overhead,
compare query-bearing versus non-query frames using exported frame IDs and
the documented schedule, with phase-boundary settling removed. Record coarse
game-render/run-tick and swap/limiter behavior. CPU thread time omits server,
DH/driver worker threads; wall minus thread is waiting/off-thread/scheduling
time, not proof of GPU fences. The query-bearing comparison is observational;
confirm small claimed savings with matched frame-only / CPU-only controls.

For same-JVM OFF→ON/ON→OFF, compare each scope's own adjacent phase change and
same-state drift. A consistent deferred1 signal directs the next cloud source
question; terrain-translucent changes identify a combined water/raster/SSR
interval, not one formula; deferred4 mixes opaque lighting/reflections; composite
mixes volumetric integration. If CPU native-submit wall shifts while thread
stays similar, retain it as a submission/wait clue without naming a driver cause.

If a scope is unresolved, overlaps too broadly, or gives mixed forward/reverse
direction, stop at that limitation. Repeat only the implicated scope/workload
with unchanged source/settings; choose a bounded source-preserving probe later
through root/protocol ownership. Do not grow the plan into an effect cut, trace
rewrite, radius change, long GPU matrix or driver investigation automatically.
An affected-pass improvement with flat aggregate time is localized evidence,
not the ordinary ≥15% acceptance gate. Native GPU timeline, CPU, visual quality
and runtime compatibility remain separate results.


## Root matched-source frame-only control terminal

exec80447 exited0; request0293267c-3708-49c4-a5ff-c8f490b4081e accepted existing
UUID/pack/options/control/artifact/class/window/temporal/PNG contracts. Same
stopped-source save89c962...c7b46, camera/options/native/minimal/noDH/mainabsent,
45s warmup/60s capture/sample5/detailedCPUtrue. GPU pass timing alone requested
false in this separate JVM. Actual policy reports frame-only every5renderframes.
3706frames/61.754FPS, 740game-render samples: GPUmean14.8650/median14.7677/
p9516.0502ms; thread7.9865ms. DetailedCPU1671405events retained.
Raw dev/logs/photon-native-attribution-frameonly-r2 measurement/provenance/
validation/summary/finalPNG and console .log.

The 23.8452→14.8650ms difference is consistent with an instrumentation concern,
but one sequential pair cannot establish pass-query causation or discard the
slower record. It supplies no LOD gain. Treat per-pass means as qualified clues,
not absolute effect budgets or fractions of an uninstrumented frame. A balanced
repeat would test the instrumentation hypothesis if further attribution depends
on those numbers. Keep lower-overhead frame-only acceptance measurements
separate; do not normalize pass means by a scalar or subtract them from14.8650.


Root checked temporal boundaries and exported GPU frame indices: source counter
3140→7342 matched4202forest rendered frames,1299→3686 matched2387coastal,
2748→6454 matched3706frame-only. Both attribution `iris/deferred1` and frame
streams included all16 render-index residues (forest7–8samples/residue,
coastal4–5); frame-only had46–47/residue. This supports the alias-reduction
intent. It does not observe per-query shader frameCounter, physical uniforms,
all checkerboard pixels or actual cloud ray execution. Raw small coverage report:
dev/logs/photon-native-attribution-counter-coverage-r1.json. Keep this evidence
separate from the instrumented GPU intervals and quality/performance gates.
