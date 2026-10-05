# General LOD target and cost audit

2026-10-05. Bounded `performance_audit` source/data review for
[the active general LOD goal](goals/general-lod-50-percent.md). Only this document,
`scripts/analyze_lod_budget.py` and `tests/test_lod_budget.py` are owned here.
No client, GPU run, shader/compiler, encoder or build was launched. Existing
harnesses, archives, captures, raw reports and failed manifests are unchanged.

The 50% throughput target requires **33.333% less total frame time** versus
matched original Photon. Existing evidence does not establish a stable 10%
original-versus-active starting gain. The latest six-arm pilot regressed in
both ordinary scenes. Qualified pass intervals suggest investigation areas,
but an unresolved instrumentation difference prevents treating them as absolute
effect budgets. A small bounded parity improvement remains useful; it should
not be promoted as sufficient for the target without new aggregate evidence.

## Pinned inputs and observed evidence

Read STATUS, the active goal, EVALUATION, AGENT_OPERATIONS, native results,
GPU_TIMING, the attribution plan and reduced-effects plan. Primary inputs are
the actual saved JSON summaries/provenance/validation and original Photon ZIP,
not current web advice. This audit inspected every selected small summary and
qualified matrix; raw attribution reports were SHA-256 hashed but their large
sample arrays were not independently reanalysed. Existing validators report
accepted input/window/source/PNG contracts; this audit does not independently
establish shader compilation or visual quality.

Measured stack: Minecraft 1.21.1 / NeoForge 21.1.253 / Java 21 / Iris
`1.8.12-snapshot+mc1.21.1-local` / Sodium `0.6.13+mc1.21.1` / Photon v1.3b,
Apple M3 Max OpenGL 4.1 Metal 91.7, reduced Mac options. The six-arm and attribution
captures physically omit DH and main ShaderLOD in original arms. DH 3.3.3 is
pinned for compatibility but these data do not measure its costs or rendering.
No full desktop Ultra/POM/voxel or other hardware result follows.

Recorded loaded SHA-256 in six-arm original forest measurement:

| Artifact | SHA-256 |
| --- | --- |
| NeoForge development runtime JAR | `a3f878f97a229b543a2c872c4a3c03b19ebd3efbd11af2caed9c258c08aebf55` |
| Local Iris JAR | `3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83` |
| Sodium JAR | `7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403` |
| Original Photon ZIP, independently rehashed | `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` |
| Reduced far-SSR active ZIP, recorded pin | `636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83` |

The public Iris download hash in `dev/runtime.lock.json` is not a substitute
for the loaded local JAR hash. The six-arm manifest records Git HEAD
`6c3e848b59ada7f0eec7e64d7b18da41f5e88775` and runtime-lock SHA-256
`7cca986f9580a32861d33fa7f6174b58babd207a119962d8d323c5abe6728a7a`.
Do not infer that current checkout/source fingerprints describe an older run.

### Original versus active pilot

Directory: `dev/logs/photon-reduced-five-effects-pilot-r1/`. One independent JVM
per arm, one block, native 2976×1672, minimal stack/no DH, original requested and
effective radius 24, fixed wetness 0, 45 s warmup/25 s capture. Frame-only elapsed
queries every 5 render frames, detailed CPU layers disabled. Original/disabled/
active controls preserve copied worlds and upstream requested options. Original
uses no main mod; disabled/active use the same reduced archive and global false/
true. Current scene/patch quality is still separately qualified/unaccepted.

| Scene / arm | Capture FPS | GPU samples | GPU mean / median / p95 ms | Mean runTick wall ms |
| --- | ---: | ---: | ---: | ---: |
| Forest original | 72.1140 | 360 | 12.5230 / 12.3980 / 14.5060 | 13.8456 |
| Forest disabled | 71.8362 | 359 | 12.6092 / 12.4225 / 15.0090 | 13.8997 |
| Forest active | 68.0766 | 339 | 13.3693 / 13.2130 / 14.9169 | 14.6697 |
| Coast original | 57.7734 | 288 | 16.0884 / 15.9570 / 17.5274 | 17.2865 |
| Coast disabled | 56.3912 | 281 | 16.4463 / 16.3713 / 18.3490 | 17.7115 |
| Coast active | 57.0609 | 284 | 16.2199 / 16.1934 / 17.4245 | 17.5020 |

All clients completed; the original aggregation failed on the private switch
quality classification. The original failed manifest is retained. Qualified
recovery does not erase its failure or provide repetitions. See
[the exact recovery limitation](PHOTON_REDUCED_EFFECTS_PLAN.md).

Active versus original GPU means regressed 6.76% forest/0.82% coast, and capture
throughput regressed 5.60%/1.23%. GPU and total-frame percentages differ because
their scopes differ. Thousands of frames are not thousands of independent JVM
runs; a one-run MAD of zero provides no uncertainty estimate.

Earlier B15 native global controls showed roughly 6–11% lower GPU intervals
within a JVM. They compare patched/global-disabled with patched/global-enabled,
have correlated phases, and some have captured-image/CPU-load qualifications.
They do not establish an original/mod-absent gain. Reduced five-effect N5 forward
contrasts 9.23%/~0.057% and reverse 8.85%/16.13% are inconsistent; the slow final
OFF phase is retained. Neither the best pair nor sum of different effect
contrasts is an accepted starting budget.

### Attribution and instrumentation concern

Directories: `dev/logs/photon-native-attribution-{forest,coastal}-r1/` and
`dev/logs/photon-native-attribution-frameonly-r2/`. Original-only fresh copies,
45 s warmup/60 s capture, sample 5, detailed CPU layers enabled. First two rotate
seven GPU categories; the third uses frame-only GPU queries on the same coastal
source world/options/class fingerprints. Effective coastal option digest is
`ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669` in both.

| Capture | Rendered frames | Whole-render GPU samples | GPU mean / median / p95 ms | FPS |
| --- | ---: | ---: | ---: | ---: |
| Forest attribution | 4202 | 120 | 13.0483 / 12.7291 / 15.8262 | 70.0267 |
| Coastal attribution | 2387 | 68 | 23.8452 / 23.0064 / 29.0587 | 39.7790 |
| Coastal frame-only | 3706 | 740 | 14.8650 / 14.7677 / 16.0502 | 61.7544 |

The coastal difference is 8.9803 ms / 37.66% of the slower GPU interval, already
larger than the numerical target reduction. **It is an instrumentation concern,
not an LOD gain or proven query penalty.** One sequential separate-JVM pair
cannot identify causation; aggregate queries also observe different frame sets.
Detailed CPU layers remain on, so this does not measure their overhead. Do not
renormalize pass values by23.8452/14.8650 or subtract them from the faster run.

Elapsed queries are not busy-time counters; game-render excludes swap/limiter/
simulation. Seven categories rotate on different frames because queries cannot
nest. Tile attribution, zeros, CPU submission gaps and sampling affect results.
Forest render-and-blit 12.290 ms is below its contained game-render 13.048 ms mean,
showing why those different-frame means cannot reconstruct a frame hierarchy.
Counter coverage includes all 16 residues, which reduces one alias concern but
does not observe shader ray execution. Preserve all zeros/drops; zero shadow
medians or setup results do not establish free work.

## Target arithmetic and honest upper bounds

For throughput multiplier `S=1.5`, target total duration is `T0/S`. If an
independent critical-path effect is fraction `p` and sped up `k` times, the
optimistic retained duration is `1-p+p/k`; achievable multiplier is its inverse.
This assumes additive cost, unchanged remaining work, zero transition/branch/
reconstruction overhead, and no CPU/presentation bottleneck migration. It is a
conditional upper bound, not a model fit to the current pass data.

| Hypothetical effect speedup | Minimum affected total-frame share to reach 1.5× |
| --- | ---: |
| Complete removal | 33.333% |
| 4× | 44.444% |
| 3× | 50.000% |
| 2× | 66.667% |

A completely removable 20% share can produce at most 1.25× throughput under these
assumptions. For distant work only, use `p = effect share × distant-work share`;
pixel fraction is not a cost fraction. Distance LOD that halves an effect whose
total-frame share is 20% saves at most 10% duration before overhead, even if every
pixel is eligible. No calibrated current effect share is established here.

If 10% faster were later independently accepted, 1.10× throughput corresponds
to 9.091% lower duration; reaching 1.50× would still require 26.667% less duration
than that improved candidate. If “10%” instead means GPU-time reduction, it is
11.111% GPU-interval throughput uplift and says nothing by itself about total FPS.

The following target gaps use the six-arm saved means, retaining negative results:

| Metric / scene | Original ms | Current active ms | Arithmetic1.5× target ms | Further reduction from active |
| --- | ---: | ---: | ---: | ---: |
| Capture-average total frame, forest | 13.8669 | 14.6893 | 9.2446 | 5.4447ms /37.07% |
| Capture-average total frame, coast | 17.3090 | 17.5252 | 11.5393 | 5.9858ms /34.16% |
| GPU timeline mean, forest | 12.5230 | 13.3693 | 8.3486 | 5.0207ms /37.55% |
| GPU timeline mean, coast | 16.0884 | 16.2199 | 10.7256 | 5.4943ms /33.87% |

Total durations here equal 1000/captureFPS, not median frame times or GPU values.
Target FPS would be 108.171 forest / 86.660 coast for these particular baselines.
runTick clocks provide separate median/tail proxies with slightly different
boundaries. GPU rows are a separate arithmetic target, not a requirement that
GPU and total durations scale equally. Root should keep measured median/tail
total-frame behavior alongside average throughput in acceptance.

## Ranked investigation, not predicted performance

| Priority | General mechanism / explicit Photon consumer | Evidence and next stopping gate |
| --- | --- | --- |
| 1 | Distance/footprint budgets for volumetric integration and reconstruction | Cloud march `deferred1`1.427ms forest/4.069ms coast; reconstruction `deferred2`0.452/0.463ms. These are qualified clues only. Default march target is 1/16 native area already. Revisit only a mechanism that removes new work, preserves overhead/lighting/temporal quality and measures aggregate benefit; old extent/secondary/cloud guards gave no useful incremental gain. Do not inflate cloud settings. |
| 2 | Distant translucent shading/trace eligibility and avoidable overdraw | Translucent terrain0.136ms forest/3.684ms coast suggests coverage sensitivity. Interval combines raster, water, other translucent materials, waves and reflections. Preserve near glint/reflection hit coverage; earlier far-SSR skip caused a bright band and repaired tracing lacked stable savings. First use source-level exact guards/reuse, then measure both water-heavy and forest views. |
| 3 | Screen footprint sample reuse and budgets in screen-space effects | GTAO `deferred3`0.398/0.538ms, opaque lighting/reflections `deferred4`1.260/2.258ms are mixed qualified intervals. `lod_opportunities` proposes consecutive identical integer-depth-texel reuse while preserving fractional ray reconstruction and every horizon update. This is a bounded parity pilot; texture-cache/branch effects may erase savings. It is not currently a high-share 50% route. Approximate AO distance/radius budgets need a separate quality decision. |
| 4 | Conservative visibility/submission reuse without geometry loss | Historical command construction~0.145ms versus native submission~1.44ms in an older lower-resolution workload does not justify an invasive command cache. Actual prepared native forest command-fill wall~0.262ms overlaps execution/submission/parent scopes. New renderer work needs its own high-share evidence; do not add these CPU scopes or assume Java owns driver waits. |
| 5 | Selective material/parallax detail | Both audited original native summaries have `POM=false`, `POM_DISTANCE=32`, water-parallaxtrue and 3 wave iterations. Disabled POM cannot save time in these scenes; no ordinary default gain follows from a POM integration. Water finite-difference/analytic options alter quality and remain unaccepted. |

Keep protected shadows/AO, sky lighting, salient object reflections and sunlight
cadence as current quality constraints. Shadow-map generation and shadow
filtering are distinct. Default-off warmed cap 16 plus DH preserves reach but
loses distant structure; native timings changed sign, travel coverage is not
automatic, and DH is optional. It cannot supply an unconditional general goal
baseline. Global render-scale/preset cuts are existing controls with quality
changes, not accepted distance LOD work avoidance.

Recommended reusable interface evidence fields: effect capability/active status,
distance units and eligibility rule, requested/effective budget, quality guard,
fallback and exact adapter revision. A common budget interface can describe
these mechanisms; it does not establish that a pack consumed a uniform or avoided
physical GPU work. Photon remains the only active consumer in this goal.

## Minimal next control protocol for root

1. Freeze one selected candidate and the whole instrumentation/source/class/map
   state before running. Preserve matched fresh world copies, actual2976×1672,
   original options, radius 24, fixed wetness 0, minimal/noDH, camera/time, pacing
   and warmup. Root alone owns the client; exclude encoders/captures/builds during
   timed windows. Save all failed and negative runs.
2. If pass cost estimates are needed, first bound the instrumentation concern on
   original coastal: four serial fresh-JVM captures in order pass/frame/frame/pass,
  45 s warmup/25 s capture, sample 5 and detailedCPU true in both. This is a small
   drift-exposing diagnostic, not full acceptance or a causation proof. Existing
  60s pair remains preserved. If the large difference repeats, keep per-pass
   figures qualitative and use frame-only aggregate source interventions.
3. Primary candidate pilot: matched original/mod-absent, patched-disabled and
   enabled, frame-only sample 5, detailed CPU layers off, one short serial block
   per scene. Repeat a reverse-order block only if there is a concrete useful
   candidate signal. Then corroborate original/enabled total-frame behavior with
   GPU queries disabled and detailedCPU false, balancing forward/reverse order.
   This produces CPU/FPS evidence, not GPU milliseconds. There is no currently
   mapped Gradle `-PcpuTiming=false`; do not claim a completely uninstrumented
   control from that flag. Existing coarse clocks remain.
4. Keep quality captures separate: near/far/transition, camera motion, thin
   geometry, material boundaries, reflections/glint, shadows and history
   stability relevant to the candidate. In a parity pilot look for image/temporal
   equality before considering approximate budgets. A zero static difference
   does not prove all moving views.
5. Report adjacent/source run changes, same-state drift, actual GPU sample/drop/
   zero counts, frame throughput and runTick median/p95, GPU median/p95 and
   run-level variability. Never infer independent confidence from per-frame
   counts or compare differently instrumented arms. Affected-pass improvement
   with flat aggregate cost is a localized result only. A repeated useful pilot
   warrants the balanced three-arm acceptance protocol in the active goal.

No GPU protocol above ran in this audit. Return to root: which bounded parity
candidate to integrate, whether profiling concern blocks pass ranking, and what
approximate quality budget is acceptable. The user owns any major quality fork.

## Exact local evidence handles and arithmetic checks

SHA-256 digests recomputed 2026-10-05:

| File under `dev/logs/` | SHA-256 |
| --- | --- |
| `photon-native-attribution-forest-r1/summary.json` | `175307b678bf61926a1a71b31467fd6e4934a5f93f1d22c313394de06e1d9533` |
| `photon-native-attribution-forest-r1/measurement.json` | `ad461e766bf9bc97008c99cbfc8fcfe9b8a624a87c4f4029288470f480b90207` |
| `photon-native-attribution-coastal-r1/summary.json` | `1c27c4bb750d573d56f84b6db65f3dba2f751df52490b6d7e1777824a1b5a4b7` |
| `photon-native-attribution-coastal-r1/measurement.json` | `ba8892ab499ac99c0bf81b23c0c4eca66f72e4e3e643f2c8f2c7e43a7cbf4714` |
| `photon-native-attribution-frameonly-r2/summary.json` | `e3f80b4645db66dffd1b6a55201e7eccf80ae8d0d6eb3a8ccf02965a74bfe1dd` |
| `photon-native-attribution-frameonly-r2/measurement.json` | `cc82c156d83e50c8d4c4eb8887e6e66503d8958e3b01813e4864aa21166066bd` |
| `photon-reduced-five-effects-pilot-r1/matrix-manifest.json` failed | `6045f8379ff1ebe34700de41b8ca009e05489de9591483da4f56251d7bdf3077` |
| `photon-reduced-five-effects-pilot-r1/matrix-manifest-qualified.json` | `6a37fe9e63ddf49c22a82b3524044351c3bd269ae3c2f52daadfeb9e09387900` |
| `photon-reduced-five-effects-pilot-r1/matrix-analysis-qualified.json` | `adbd9bb2a80b25f8d55d1aa77a12d74b363bccf8ce8dbc9b0859c05996e38fde` |

Six-arm per-arm summaries/measurements use prefixes 001 forest original,
002 forest disabled,003 water active,004 forest active,005 water disabled,
006 water original; complete paths are in both retained manifests. Source-world
digests are forest `f752339d854ff2f0e0a7dc4e577dd6aedf2d0f69e39a9a7cae9dd0286c7be08b`
and coast `89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46`.
Attribution dev-class aggregate is
`3edcfc739a8ded2b4b7dfbb8462f647e3e067f15863f40e9c180343f183e0db3`,
six-arm is `8b3772ca4478f675dfc13f02ecf3974b3a72d06919ac397eebda4e71fda73b62`.
They differ; do not combine sessions as identical harness captures.

Original Photon inspected member hashes: `shaders/include/lighting/ao/gtao.glsl`
`0889647aa827357ba9e5101431b4294299a078055f7f3537468a94fb6430b0d3`,
`shaders/program/d3_ao.fsh`
`5a47a1a64788105b5d197db8bc1a9aff1cdf17727c3c1804c01573c76863c266`.
Pass/source mapping and its other immutable members remain in
[the attribution plan](PHOTON_NATIVE_ATTRIBUTION_PLAN.md).

The dependency-free calculator accepts explicitly labeled scalars. It never
validates a report, turns GPU data into FPS, sums effect costs, estimates quality
or treats a conditional Amdahl bound as measured speedup. Examples using actual
six-arm means:

```sh
python3 scripts/analyze_lod_budget.py --kind fps --baseline 72.11401790279642 --current 68.07658573321122
python3 scripts/analyze_lod_budget.py --kind gpu-ms --baseline 12.522953172222222 --current 13.369340746312684
python3 -m unittest discover -s tests -p test_lod_budget.py -v
```

Seven arithmetic/validation tests pass. The first execution had one failure:
the exact theoretical 2/3 affected-share × 2 speedup was classified below target
because of floating-point rounding. Comparison now explicitly tolerates 1e-12;
the rerun passes. This is a calculator repair, not a performance result. No
runtime failure or negative performance observation was discarded.
