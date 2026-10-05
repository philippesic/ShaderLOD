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

Latest checkpoint: `0a4d7a9` (independent parallel shader/quality implementations
and integrated protocol2/interface commands;60Java tests). Prior `67ef5ba` preserves
corrected density runtime/core binding and the team direction. Earlier
checkpoints: `270f992` (123 motion frames), `fc86429` (camera rotation
validator), `a3dd9cd` (motion controls), `8249ce3` (attribution sample spread). Historical failures and raw reports
remain retained; the latest operational checkpoint is at the end of the goal.

## Ownership and current work

Root primarily orchestrates independent parallel implementation tracks, per the
user's reiterated direction. `effect_api` implemented protocol2/43effect fields,
OpaqueLighting controls/commands/coherent reload snapshots in an isolated worktree;
root integrated it and main test/build/dev compilation passes all60tests. Core2
stays unchanged; defaults INHERIT/unity/protected lighting. Actual extension
linkage/config reload in the client remains unverified.

`reflection_lod` implemented bounded footprint refinement (9tests),
`procedural_lod` implemented displaced-footprint water/parallax (13tests),
`volumetric_lod` implemented normalized primary integration (12tests), and
`lod_quality` implemented reusable edge/near/plane guards (9tests). All are
source-tested/default-off; driver/quality/performance acceptance pending.
`lod_opportunities` builds the 2x2 irradiance prepass/resolve in its isolated
lighting-rate worktree. `ao_runtime` prepares a shared source-qualified untimed
driver/quality queue; no launch before rootreview. Source development stays
parallel; one GPU operator and dedicated quiet benchmark windows. Root currently
has transferred soleGPU to `ao_runtime`: reflection untimed QA completed with
actual in-world compile/noFallback and five core values read in fullscreen/raster
draws. No obvious bright water band in the inspected still; motion/parity and
performance unverified. All firstQA processes stopped. Next authorized volume
untimed QA checks actual effect2 controls; exact ownership/paths/pins/live handles
are in the active goal and PARALLEL_LOD_RUNTIME_QUEUE.md.

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
