# ShaderLOD status

Updated: 2026-10-05.

Active goal: [General LOD performance: 50% faster](goals/general-lod-50-percent.md).
The user explicitly superseded the previous sustained-performance direction:
general distance LOD/adjacent work avoidance, reusable mod interfaces, little
discernible quality loss, Photon as the only supported shader, and consistent
native agent teams. Target: 1.5x total rendering throughput (33.333% less frame
time) against matched original Photon, with repeated GPU/total-frame and quality
evidence. This target is unachieved. The previous 15% gate is superseded; historical
evidence and unresolved checks below remain retained.

Latest integrated commit `800eca2` validates adapter layout declarations and
same-draw executed-work diagnostics. The current coordinated build passed 131 Java
tests, production assembly and dev compilation, including packed RGB formats
and shared-spatial registration. No new performance gain is accepted. Photon-specific tuning
is set aside; Photon supplies inputs and original operations to shared LOD code.
Historical checks and failures remain below; the operational checkpoint is at
the end of the active goal.

## Ownership and current work

The user clarified the product: build the general, extensible LOD system. Photon
is the first explicit adapter and test case. Pack-specific fetch-cache/projection
optimization is set aside. A planned standalone 4 × 4 pack was stopped before any
files were created; its shared region guard remains an unconsumed utility.

Commit `000f355` implements immutable adapter capabilities, explicit source
bindings, public extension registration and shared shader work decisions. New
adapters register descriptors, source pins and capability exports before pipeline
binding; late registration is rejected. Core 2 and effects 2 keep their meanings.
Capability schema 1 is separate. Unknown sources/protocols/options and renderer
fallback retain original work. No automatic rewriting or new pack support.

The first volume consumer calls the common work policy. Its march body, density,
optical normalization and secondary work are preserved; INHERIT is unity and
ADAPTIVE uses the general effect controls. Current test/build/dev compilation
passes 103 Java tests; shader acceptance includes 15 source/math/grammar fixtures.
Production JAR SHA-256:
`aa65cf9157e0c5dac7db6ea65d38a42b3eec06a71838e394389063ead5b3e672`.

Current-revision runtime r2 rendered exact reference `5de756…27de` without fallback.
Actual typed readback confirms capability schema 1, availability 1, core 2/global
1 and effects 2/ADAPTIVE/512–1536 blocks/128-block transition/0.85–0.75 ratios.
Native window/input and sampler-state guards pass. This proves the shared bridge,
not physical iteration savings, motion quality or performance. Evidence:
[Shared runtime](SHARED_LOD_RUNTIME.md), [extension API](LOD_EXTENSION_API.md).

The first repeated-performance pilot stopped after three of nine planned runs on
an artifact guard. Independent exact-registry qualification confirmed a known API
Base metadata-only difference; failed raw status is retained. In that one block,
adaptive GPU median increased 71.6% and frame median 64.8% versus original. This is
an adverse diagnostic, not a repeated or causal conclusion. No useful gain.
[Pilot](SHARED_LOD_PERFORMANCE.md), [work audit](SHARED_VOLUME_WORK_AUDIT.md).

Shared spatial planning and producer-certified reconstruction are source-tested.
The explicit Photon callback now passes 5 adapter fixtures, including 1,008 Iris
parses, 332 private-OFF token comparisons, 1,352 upstream layout checks and two
complete offline semantic compilations. No actual driver, quality or speed
acceptance follows. [Reference contract](SHARED_SPATIAL_REFERENCE.md).

Executed-work diagnostic r1 failed an attachment extent guard. R2 repaired only
the diagnostic layout and captured three same-draw RGBA32F pages successfully in
both arms. Per 16-ray snapshot, original primary/density/secondary counts were
209/409/200; shared counts were 193/385/192. Temporal pairing is unproved, so
these are observations rather than causal work savings or speed evidence.
[Runtime evidence](WORK_TRACE_RUNTIME.md).

The binding and dev-readback workers finished their freezes; root integrated
them. Active worker `spatial_runtime_preparation` owns exact matched untimed
controls. Root owns checkpoint and idle GPU; no Minecraft client is live.
Next: review the controls and validate the shared spatial adapter in the actual
renderer before quality and repeated performance measurements. The 1.5 × throughput and quality target remains unmet.

Retained first-wave experiments:
AO pilot runner exec89240 exited0, all three normal WorkloadSession contracts
passed and compiled source rendered. Original/off/on mean GPU13.2830/12.5266/
17.7031ms; FPS68.4552/72.6664/53.1302. No useful gain; candidate stays private/off.
One serial block, cross-arm focus differences and uncertain lightweight source-test
CPU overlap prevent quiet causal/repeated acceptance. Dev classes stayed frozen.
New effects config actually loaded with six INHERIT/unity controls; extension
shader consumption remains unverified. No Minecraft clients live. Runtime proof
and exact handles: PHOTON_AO_REUSE_RUNTIME.md. Density cost-control r1 rendered
original/off but failed strict validation: private option absent from Iris's active
map, and original focus differed. Enabled never launched; raw failures retained.
Pinned Iris parser inspection identified the combined guard discovery gap; nested
guards now pass actual-parser discovery/override/no-stage-leak tests. R2 completed
all source/options/driver/focus/window/input contracts. Diagnostic mean GPU
15.4416/15.3720/14.9639ms and FPS60.1758/60.8841/62.1495: only2.655% GPU reduction
versus disabled in one quality-changing block. Calibration is deferred; no causal/
repeated/quality gain promoted. An existing no-GPU-timing core/sampler readback
smoke is authorized to `ao_runtime`; no clients live at r2 return. Exact handles
and validation are in PHOTON_DENSITY_DETAIL_RUNTIME.md. Next plans investigate
far irradiance shading at lower rate with full-res textures retained. The 50% goal remains
unachieved; no shader support or default-quality reduction is promoted.

The following describes retained work from the superseded goal, not active
other-pack development or a current live-client assignment:

Main Java/config/uniform transport, shared helpers and existing Photon patches
remain based on `8da8943` and reserved for the original chat. This performance
chat owns exploratory adapters and all Minecraft/GPU clients. The core bridge
registers seven fields; per-effect mod transport is not implemented. Other-pack
core diagnostics consume version/global enable/near/far, not all core fields.
Pack options, mod configuration and actual shader consumption remain separate.

The reduced far-SSR five-effect Photon control passed with GPU contrasts
+9.23%/+0.057%, no useful repeated gain. Complementary full/sky/Potato
sequences each passed41-frame structural checks; matched views show reflection
loss/displacement. The reverse Photon control passed with8.85%/16.13% GPU
contrasts, but the forward result was9.23%/flat. The opt-in Photon diagnostic read back all seven linked core fields. The six-arm
pilot completed: active GPU regressed6.76%forest/0.82%coast versus original.
All captures passed; a narrow private-gate analysis repair retains the failed
raw manifest and derived qualified analysis. Root owns a Miniature MODE2
driver/readback smoke test.
Miniature’s separate seven-field private archive is built but driver/rendering
acceptance is pending. Balanced
mod-absent baselines and a second workload are being prepared. Full quality
acceptance remains pending.
Photon forest/coastal attribution passed input and
capture contracts. A matched-source coastal frame-only sample5 control retaining
detailed CPU timers measured14.86ms versus23.85ms in the pass-timed run. This
raises an instrumentation concern; one separate-JVM pair does not establish
causation, an LOD gain or absolute effect budgets. A frozen source plan now defines Miniature consumption of the remaining existing
core fields (preset/transition/debug). Its separate default-off private consumer
and balanced original/disabled/enabled profile preparation passed source checks and actual preparation/assembly; runtime acceptance is
pending. Common Java/config transport remains reserved. Root owns runtime.
Exact handles and stopping boundaries are in the active goal checkpoint.

## Current evidence

- Photon native 2976×1672 same-JVM global controls showed roughly 6–11% lower
  GPU intervals in forest/coastal sessions. Independent serial regressions are
  retained. This does not establish an ordinary ≥15% gain versus original.
  [Native results](PHOTON_NATIVE_RESOLUTION.md).
- The native cap-16 combined pilot showed 5.18% lower GPU time versus original
  with visibly coarser distant detail. Terrain-only control passed real ON16/
  OFF24 and acknowledgement restoration, but GPU contrasts changed sign
  (−30%/+10%) despite consistent CPU reductions. No stable gain promoted.
  [Terrain control](NATIVE_INPROCESS_TERRAIN.md).
- Nine original-Photon forest runs across minimal, optimized and culling stacks
  established no consistent stack benefit. Exact artifacts and a narrowly
  audited Iris/Sodium API Base metadata difference are recorded.
  [Stack results](STACK_FOREST_RESULTS.md).
- Native original/disabled/enabled shoreline, sun-glint and underwater triplets
  each completed 603 verified PNG frames. SSR-only far skipping reproduced a
  bright water band; water-only did not at that pose. Private full/reduced far
  tracing removed the inspected band. Serial cost regressed; reduced same-JVM
  forward GPU contrasts were +2.10%/−13.78% and reverse −0.64%/+0.73%, with no
  stable benefit. Full motion/transition quality remains unaccepted.
  [Glint audit](PHOTON_GLINT_SOURCE_AUDIT.md),
  [SSR cost control](NATIVE_INPROCESS_SSR_COST.md).
- Underwater retries completed 201/201 frames per arm with actual WATER,
  no full-block camera collision, hashes and safe writer draining. Enabled
  peak two pending images exercised the bounded option; original/off peaks
  one cannot prove that option caused throughput improvement. Prior incomplete
  captures remain retained. [Capture evidence](CAMERA_QUALITY_HARNESS.md).
- MakeUp 9.5g rendered noDH; original DH linked programs and submitted actual
  distant world draws. Core water cues proved global enable/near/far, but normal
  rendering established no useful gain. Miniature 2.19 rendered original and
  experimental paths, with core diagnostic pixels and normal GPU readback;
  its refinement pilot was flat. BSL original HIGH rendered noDH; native
  parallax-off changed GPU time 2.81% at different quality.
- Complementary Reimagined r5.9.3 original HIGH rendered noDH with no fallback.
  Full/sky/Potato native-water pilot GPU times were 13.77/12.31/12.30 ms. Sky
  loses object reflections and Potato shifts them; neither is a ShaderLOD
  adapter or quality-equivalent gain. New-biome/End expression warnings remain.
  [Pack evidence](PACK_COMPATIBILITY_MATRIX.md),
  [Complementary runtime](COMPLEMENTARY_RUNTIME_PLAN.md).

Cloud consumer/extent-hoist/literal-secondary private probes established no
useful incremental gain and remain off. Shadow/AO budgets are protected by
default. Cached sky, analytic water and CPU frustum/cache/list experiments remain
off. Terrain reduction requires explicit warmed-scene acknowledgement; automatic
travel coverage is incomplete. No sunlight or shadow-map cadence was changed.

## Environment and validation limits

Minecraft 1.21.1 / NeoForge 21.1.253 / Java 21 / Iris 1.8.12-local / Sodium
0.6.13 / Photon v1.3b / optional DH 3.3.3 are pinned in
`dev/runtime.lock.json`. Iris and Sodium are required; additional performance
mods are optional. Apple M3 Max OpenGL 4.1 Metal 91.7 uses reduced Photon options.
Full desktop Ultra/POM/voxel features and other hardware remain unverified.
GPU queries use asynchronous elapsed counters; timestamps are unavailable.
[Timing limits](GPU_TIMING.md), [evaluation rules](EVALUATION.md).

Latest dev integration compiled successfully; production JAR excludes dev
profiling classes/mixins. Thirty-eight in-process, 24 stack, seven Complementary
preparation and 19 prior matrix fixtures passed. Static tests/builds do not
establish runtime compilation, physical trace counts, quality or support.

Previous completed goals: [full-frame proof](goals/full-frame-proof.md),
[GPU/DH proof](goals/gpu-lod-dh-comparison.md),
[setup](goals/setup-environment.md). Earlier premium-cloud and geometry gains
remain qualified by their workloads and quality changes. Historical repeated
results and JFR analysis are in [repeated results](REPEATED_RESULTS.md) and
[CPU forensics](RENDER_CPU_FORENSICS.md); the first JFR observed the fast regime.

No remote host, push, publishing destination or external messaging is authorized.
Generated clients, worlds, binaries, captures and raw reports stay outside Git.
