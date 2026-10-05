# General LOD measurement reliability

2026-10-05. Bounded `performance_audit` review; only this new document is owned.
Read the active goal/checkpoint, saved AO measurements, their CPU/GPU samples,
current timing/control sources and pinned Minecraft/Iris source/bytecode. No
client, GPU, compiler, test, encoder or image processing ran. All data analysis
and static disassembly ended before root's density timing freeze; only this
text was completed afterwards. Historical negative results remain authoritative.

**Recommendation:** retain the density pilot as a cost diagnostic, then use
an exact-original-Photon, main-mod-present versus main-mod-absent control if its
result still needs attribution. Existing WorkloadSession supports that control
without another harness. A same-JVM all-effects-off global control also exists,
but does not isolate the new addon or accept the current density archive. Fixed
focus and query-off corroboration are necessary controls before promoting a
large shader gain; they have not run here.

## What the AO pilot actually shows

Authoritative directory: `dev/logs/photon-ao-depth-reuse-forest-pilot-r1/`.
One serial JVM per arm, 45 s warmup/25 s capture, frame-only GPU queries every
five render frames, detailed CPU layers off. Native 2976×1672, minimal/noDH,
requested/effective 24 chunks, wetness 0, same prepared original world and pose.
See [the runtime evidence](PHOTON_AO_REUSE_RUNTIME.md) for source/PNG/config
contracts and quality limits.

| Arm | GPU mean / median / p95 ms | runTick wall mean / p95 ms | runTick thread mean ms | Swap wall mean ms | Focus |
| --- | ---: | ---: | ---: | ---: | --- |
| Original, main mod absent | 13.2830 / 13.0994 / 15.5850 | 14.5845 / 21.1953 | 8.0920 | 0.8958 | false, 501/501 observations |
| Private source, global false | 12.5266 / 12.2953 / 14.9350 | 13.7416 / 20.0771 | 7.3651 | 0.4195 | true, 501/501 |
| Same private source, global true | 17.7031 / 16.9734 / 22.7853 | 18.7975 / 27.1561 | 8.6871 | 0.7392 | false, 502/502 |

Whole-render GPU counts 342/362/266, runTick counts 1711/1816/1330. All exported
GPU records have epoch 2 and frameId residue 1 modulo 5, matching
`GpuSamplingPolicy.schedule`: `(frame-1)%interval==0`. No zero GPU samples
occurred. These are correlated samples, not independent performance repeats.

Ordered thirds of each exported stream reveal within-run movement:

| Arm | GPU means, first/middle/last third ms | runTick wall means, first/middle/last third ms |
| --- | --- | --- |
| Original | 12.5295 / 13.4689 / 13.8507 | 13.9551 / 14.7292 / 15.0683 |
| Global false | 12.5158 / 12.5237 / 12.5403 | 13.7092 / 13.7867 / 13.7290 |
| Global true | 16.6712 / 19.3399 / 17.0866 | 17.7069 / 20.5925 / 18.0946 |

These thirds are equal record counts, not equal wall-time windows. The active
GPU middle-to-first difference is 2.6687 ms, much larger than the older qualified
~0.398ms forest AO-pass clue. That earlier pass was measured under different
instrumentation and cannot be subtracted as an absolute effect budget. The
aggregate slowdown is observed; its cause is unresolved. Increasing both CPU
wall and GPU intervals does not identify shader busy time, driver waits or
host scheduling.

For runTick frames with an exported GPU result, minus other runTick frames,
mean wall differences were −0.3985/+0.1422/+0.3310ms for original/off/on. Actual
matched counts were 342/362/265; the active last GPU frame has no completed
runTick record. Frame-result membership is not a perfect query-schedule census
at capture edges. These observational differences change sign and do not
establish a common query penalty or its absence; other frames can also be
affected by shared pipeline/query work. Polling only on query frames is source
behavior, not proof that perturbation ends within that frame.

## Focus, clocks and current instrumentation limits

All capture observations were visible/noniconified, vsync false, configured
cap 260, pause-on-lost-focus false, with no observed transitions. Maximum sampled
window gaps were 63.36/59.57/76.91ms. Original and active lost focus during warmup;
disabled stayed focused. Existing per-arm guards accepted these states; they do
not establish matched cross-arm focus or continuous host scheduling.

Pinned generated Minecraft 1.21.1 source `Minecraft.getFramerateLimit()` returns
the window's configured limit while a world exists. Its `runTick` invokes
`RenderSystem.limitDisplayFPS` only below 260. There is **no focus test in that
limiter path**, and these saved CPU streams contain no frame-limiter scope.
Do not apply newer Minecraft background-cap assumptions to this version.
This rules out a specific source-level vanilla limiter explanation; focus may
still correlate with unmeasured platform/window scheduling. No causal focus
claim or actual host clock/thermal measurement is available.

Iris source counters advance: original 3183→4895, off 3072→4889,
on 2792→4123, matching rendered-frame counts 1712/1817/1331. Frame time counters
and shader phase remain real, despite fixed day/weather/wetness. Different
temporal phases, moving sheep, unrecorded system activity and source changes
remain qualifications; do not name any as the cause. A reported 4.6 s source
fixture run lacks an exact start/end overlap interval. No controlled effect
of that work was measured, and its presence must not erase the unfavorable run.

WorkloadSession drains GPU/CPU records on existing workload ticks. CPU coarse
runTick/game-render/render-and-blit/swap clocks remain enabled when detailed
layers are off. Window observation is outside GPU scope hooks; report writing
and final PNG follow capture termination. The normal report lacks per-supplier
timings/counts for the core/extension, hardware utilization, host clocks/thermal
state and a full per-frame shader-work census. GPU game-render excludes swap,
limiter and simulation; elapsed queries can include CPU command starvation.
The prior 23.8452→14.8650ms pass/frame-only difference remains an independent
instrumentation concern, not an LOD saving.

## Can a neutral bridge control use the existing tools?

**Exact shader-neutral control:** original Photon ZIP contains no `shaderlod`
bytes or uniforms. Select that exact archive/options/scene in two fresh matched
profiles; launch one with `runClientLodOn` and all renderer experiments off,
the other with `runClientLodOff`. Both use ordinary WorkloadSession. This keeps
shader source identical while changing main-mod presence. It measures total
mod/lifecycle/notifier integration cost, including the new addon, against no
main mod; it does not isolate the addon from the older core mod.

**Existing same-JVM near-neutral control:** BenchmarkSession feature `global`
already alternates OFF/ON/OFF/ON. It requires all five SHADERLOD effect flags
false, protected lighting/use-mod-config true, cached sky/analytic/debug false,
terrain and CPU experiments off, hidden HUD, fixed wetness, and a no-atmosphere
guard archive. B15 is a pinned eligible source:
`build/photon-lighting-tier-no-atmosphere.zip`, SHA256
`b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c`.
This keeps the loaded source/JVM/pipeline stable while testing global flag
transitions and same-state drift. Retained generic tier/scaffolding means it is
an intended effect-neutral control, not byte-identical original Photon.

**Limit:** feature `global`/`effects` requires the complete five-flag map and
bridge/lighting options. Exact original Photon and the current private density
archive do not meet that contract; `ssr_cost` also has different exact pins.
Do not force them through those analyzers or declare a standalone report a
validated same-JVM experiment. The density source can instead use its same
private archive with global false/true in matched standalone WorkloadSession
clients and preparation/input contracts. That preserves source identity but
retains independent-JVM drift.

## What the new addon can cost with no active extension users

Current `EffectUniformsMixin` registers 37 PER_FRAME fields plus one
`EffectUniformFrame` notifier listener per registration group. Every notifier
callback clears a reference even if no effect supplier is consumed. Registration
allocations/location lookups belong to pipeline construction and warmup;
callback delivery remains per frame.

Pinned Iris bytecode establishes that `ProgramUniforms.Builder.location` returns
empty for location −1, and `LocationalUniformHolder.uniform1i/uniform1f` only adds
the uniform through `OptionalInt.ifPresent`. `ProgramUniforms.update` updates
linked PER_FRAME uniforms when its frame counter changes. The custom-expression
path initially retains input suppliers, but `CustomUniforms.optimise` removes
unused dependency nodes; IrisRenderingPipeline calls it during construction,
then calls notifier.onNewFrame before customUniforms.update while rendering.

`EffectUniformFrame.control` snapshots all six effects only when a supplier
queries it; subsequent suppliers in that registration group reuse the snapshot
until the callback clears it. Original Photon and B15 contain no extension
consumer names. Thus avoiding per-effect snapshots for those packs is supported
by source expectations, while unconditional notifier overhead remains. There
are no runtime addon supplier counts here. Do not claim zero overhead or that
absence of extension consumers proves an inactive mixin.

Global enable false/true never disables the addon registration/listener. Both AO
patched arms used the same new main classes and INHERIT/unity/protected effect
config SHA256 `c02b81bc4ad7c90ac2e23e839845ddcfa8d7ab6909a380161f319ed414530984`.
Their difference cannot isolate addon cost. Likewise, config mode FULL or
INHERIT is not an addon-disable switch. A pure addon-only ablation would need
a separate reviewed source/build change; no such existing switch or microtimer
was found. Return that optional choice to root instead of expanding the harness.

## Ranked concrete controls and stopping boundaries

1. **For the already authorized density pilot:** fixed focus throughout late
   warmup/capture in all original/off/on arms; same source for off/on, same
   matched world/options, actual native dimensions, N5/frame-only/layer-off,
   one client and quiet workers. Retain first/middle/last movement and negative
   results. If source effect is flat/regressive or swamped by same-state drift,
   stop before density calibration; this pilot is not 50% acceptance.
2. **If mod overhead remains the question:** four small original-shader clients
   in absent/present/present/absent order, fresh matched copies, focused,
  45 s warmup/25 s capture, GPU N5/frame-only/layer-off. Repeated same-state
   observations expose the scale of ordinary drift. This separates unchanged
   shader/mod-presence effects from candidate shader work. A single pair is a
   preliminary bound only; no effect-specific supplier attribution follows.
3. **If profiling remains the question:** on that same neutral original-with-mod
   condition, frame-only/GPU-off/GPU-off/frame-only with identical coarse CPU
   clocks, focus, scene and layer-off. CPU/FPS deltas assess query-regime
   perturbation separately; GPU-off produces no GPU milliseconds. No existing
   mapped Gradle `-PcpuTiming=false` is available, so this is query-free rather
   than fully instrumentation-free. Do this only if the first controls leave
   a material unresolved question, not an automatic factorial matrix.
4. **If bridge transition drift remains the question:** reuse B15 all-effects-off
   global control with the same focused window and N5 settings. One four-phase
   session can expose within-JVM same-state drift/enable contrast; reverse order
   only if useful. Suppliers remain present in both states, so this cannot
   measure per-supplier cost or isolate new addon registration overhead.

Executable launch pattern after root prepares a **fresh** exact-original profile
and freezes compiled inputs (not executed here):

```sh
./gradlew runClientLodOn -PrunDirectory=run-workloads/general-lod-reliability-r1/forest/original-mod-on -PperformanceStack=minimal -PincludeDh=false -PmeasureWorkload=true -PmeasureOnly=true -PinitialWarmup=45 -PmeasurementSeconds=25 -PgpuTiming=true -PgpuPassTiming=false -PgpuSampleInterval=5 -PcpuLayerTiming=false -PbenchmarkWindowScale=2 -PfixedWetness=0 -x compileDevClientJava
```

The no-main counterpart changes task to `runClientLodOff` and uses its own fresh
equal profile. Query-off changes only `-PgpuTiming=false`; leave the scheduled
cadence argument recorded. Main/dev compiled source fingerprints must be frozen
before launch and checked after; skipping a compile task alone is insufficient.
Existing benchmark launch flags for the eligible B15 profile are
`-PgpuBenchmark=true -PbenchmarkOnly=true -PbenchmarkFeature=global
-PbenchmarkStartEnabled=false -PbenchmarkSettleSeconds=10`, replacing
`measureWorkload/measureOnly`; they must not be combined.

Keep ordinary pose, source age and clocks documented; do not freeze all shader
time to produce a result on a different workload. If testing focus directly is
still necessary, use the same neutral source/settings in focused/unfocused/
unfocused/focused standalone clients. That is a distinct condition and does not
justify retroactively explaining AO. Build/test/capture jobs stay outside timed
windows. Require same-direction balanced results greater than observed drift,
query-off total-throughput corroboration, separate GPU statistics, and independent
quality/compatibility acceptance before treating any candidate as goal progress.

## Evidence identifiers

All SHA256 below are captured artifact/source identities, not remote versions:

| Evidence | SHA256 |
| --- | --- |
| AO001 original measurement | `45f2a1a1d44d0872c61efcb0c788ed4f714a7ff7e01eab68f54af424d69a4d5e` |
| AO002 off measurement | `90fdbf0c510f3e29d10815a0110f1640d6f6f2775430759fc5655211c1b32789` |
| AO003 active measurement | `8a9b431883109bdf61df47210eed5ca4ae9499962c4688bf52ceb83eb52a2842` |
| AO matrix-manifest.json | `99f650540e4a029df6cb958cdf75d7d74fe5178e2b37e58bfbd7de004fe4e407` |
| AO matrix-analysis.json | `5f159c64b1a6d1d6591412078be026cf0c97b1fe59bc7c02b095c963ae3265dd` |
| AO ao-runtime-audit.json | `c451ca5ba9d9160920c20f59a259de5d3be9ad7980a787524c220b71b30e9b1a` |
| Original Photon | `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` |
| AO private source, shared off/on | `86f375aaf1b7d55a751595ca1087f14be155ac475d2e271f70be323d6829e9b9` |
| Loaded local Iris 1.8.12 | `3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83` |
| Loaded Sodium 0.6.13 | `7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403` |
| Loaded NeoForge 21.1.253 artifact | `a3f878f97a229b543a2c872c4a3c03b19ebd3efbd11af2caed9c258c08aebf55` |
| AO main-class tree | `6debde5638cd965e798f3bf5d7cbf9d7586510f954c44efdf753e91401b5402f` |
| AO dev-class tree | `26f1d00e2bac0b6a0aa169ad6914ffc12c80365e221dbb12dba72355298b9e76` |

Runner HEAD `df40a8b1eaf93665b4604443123b2d505e61458e` plus class inventories
record local integration. Loaded stack includes Minecraft 1.21.1, Java 21, local
Iris 1.8.12/Sodium 0.6.13, noDH, Apple M3 Max/OpenGL 4.1 Metal 91.7. No desktop
Ultra, different GPU or DH behavior is established. Focus/clock/measurement
details come from the three `*-measurement.json` files named by the manifest.

Current source hashes: EffectUniformFrame.java
`1c7e605f7dee1e5fbebb777aa58d5b8fd34e7f873c3a29f8700d2b77afab1f9a`,
EffectUniformsMixin.java
`97f433eab7bbd84d3849ca26acf8c63fd40ade48d1829c1027ab1eb0ca54b49e`.
Generated Minecraft source jar `build/moddev/artifacts/neoforge-21.1.253-sources.jar`
SHA256 `abf3bed3623112c03cd16602f8cbe3ba09c7566e4aa610fd310530f7b4013d83`;
Minecraft.java member
`10bb994d90796f512da38ace6b1cc0255fbadbd0677c4af3f9d54f76dacf7fae`.
Static `javap -p -c` inspection used the pinned loaded Iris JAR and the named
ProgramUniforms/LocationalUniformHolder/CustomUniforms/FrameUpdateNotifier/
IrisRenderingPipeline classes. This does not independently reverify transformed
runtime class behavior or addon supplier execution.

Root decides whether the density result warrants any reliability control,
whether exact-original mod overhead needs bounding, and whether an addon-only
ablation is justified. No additional benchmark or framework is started here.
