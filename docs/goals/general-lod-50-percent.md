# General LOD performance: 50% faster

Created: 2026-10-05. Status: active. Native Codex Goal created in this chat.

## User direction and scope

The user explicitly requests sustained optimization/expansion of ShaderLOD with
consistent native agent teams. Improve total shader/rendering performance through
general distance LOD and adjacent rendering work avoidance, with little discernible
quality difference. Build reusable mod interfaces that ease future explicit shader
integrations. Photon is the only currently supported shader. Do not pursue new
pack implementations in this goal or substitute Photon-specific tuning for the
general product. Explicit pinned Photon code remains the reference consumer;
there is no universal GLSL rewriting system.

Retain NeoForge/Minecraft 1.21.1, Iris required, Sodium required by Iris, DH optional
for users and present in the compatibility runtime. Do not add Fabric. Preserve
attribution and licenses, and keep generated artifacts, clients, worlds, captures,
downloaded sources and raw logs out of Git. No remote push, publishing or external
messaging is authorized.

This goal explicitly supersedes [sustained-performance](sustained-performance.md)
and its provisional 15% gate. All prior failed/qualified evidence and unfinished
checks are retained. Existing other-pack experiments remain historical evidence,
not an instruction to continue expanding supported packs.

## Target and acceptance

Interpret "50% faster" as at least 1.5x total rendering throughput versus matched
original Photon (at least 33.333% lower frame time), not a 50% frame-time reduction.
Record throughput uplift and frame-time reduction together so the target stays
unambiguous. The user's roughly 10% starting estimate is consistent with some
historical controls, but no stable original-versus-active general gain is accepted.

Require repeated balanced-order original/mod-absent, patched-disabled and enabled
controls with equal scenes, presets, resolution, dependencies and warmup. Report
median/tail total-frame and GPU intervals, variability and instrumentation limits.
Demonstrate improvement in representative ordinary workloads, plus a second
workload without material regression. Attribute work avoidance separately from CPU
submission, GPU effect work and measurement overhead. FPS alone is insufficient.

Quality acceptance includes near/far views, transitions, camera movement, temporal
stability, thin objects, water/reflections, shadows and material boundaries relevant
to each change. Retain salient lighting; aggressive distant structure loss is not
an accepted default. Check reload/config behavior, unsupported adapter versions,
absent mod, Iris absence and DH-present/absent compatibility independently. Builds,
source tests and downloads do not establish driver compilation or rendering.

## Team rules and initial ownership

Follow [AGENT_OPERATIONS](../AGENT_OPERATIONS.md). Assign bounded deliverables,
owned files, acceptance evidence and a stopping boundary to each native subagent.
Inherit model settings. Use teams consistently for independent work, never parallel
edits to the same file. Root owns goal/status/decisions, integration and the GPU
until an explicit runtime handoff. Workers report actual checks and limitations.
Checkpoint before handoffs; do not rely on chat memory.

Existing local modifications at start: build.gradle,
docs/SHADOW_VISIBILITY_CACHE_DESIGN.md,
src/devClient/java/com/pp/shaderlod/dev/ComparisonClient.java and three untracked
Miniature preparation/runtime-plan files. Preserve these; ownership was reserved
by the prior checkpoint. Do not edit them during this initial team wave. Existing
main/config/uniform/helper/Photon files are likewise reserved by prior work; start
with explicit new-file partitions and review integration before modifying them.

## Initial evidence and next actions

Read STATUS, previous goal's latest checkpoints, PROJECT_OVERVIEW, EVALUATION,
DECISIONS and AGENT_OPERATIONS. Prior reduced five-effect Photon original/off/on
pilot regressed GPU time 6.76% forest and 0.82% coast; forward/reverse same-JVM
results were inconsistent. Seven existing core uniform fields were actually linked
in a Photon diagnostic. Per-effect mod transport is still absent. Lighting budget
cuts, cached sky, analytic water and CPU experiments remain protected/off.

Pinned environment: Minecraft 1.21.1 / NeoForge 21.1.253 / Java 21 / Iris
1.8.12-local / Sodium 0.6.13 / Photon v1.3b / optional DH 3.3.3. Exact artifact
hashes remain in dev/runtime.lock.json. Local M3 Max OpenGL 4.1 Metal reduced
profile does not validate full desktop features. Process inventory at goal start
found only a Gradle daemon (PID 30675), no live Minecraft/GPU client. The prior
Miniature exec28594 ownership marker is stale; do not claim its result without
validating its saved reports. No runtime job is launched by this checkpoint.

First wave: reusable per-effect budget/config model, bounded Photon work-avoidance
investigation, and evidence/bottleneck audit. Root will review source freezes,
choose a measured high-value integration, and run checks serially. Do not promise
the numerical target before evidence. The native goal stays active until achieved.

## First wave implementation checkpoint

`effect_api` owns new lod Java types, tests and EFFECT_POLICY_API.md; pure model
freeze complete, 14 numerical/off/protection/boundary test methods authored but
execution pending. `lod_opportunities` owns new AO-depth-reuse builder/manifest/
tests and GENERAL_LOD_OPPORTUNITIES.md. `performance_audit` owns new target-gap
calculator/tests and GENERAL_LOD_BUDGET_AUDIT.md. `ao_runtime` owns new private AO
profile preparer/tests and PHOTON_AO_REUSE_RUNTIME.md; no runtime handoff yet.

Both older ShaderLOD chats were inspected read-only and are idle/not loaded. The
new user-requested goal explicitly reassigns additive integration to root: new
ShaderLodEffectConfig, EffectUniformFrame, EffectUniformsMixin, plus one-line
config/mixin registration in ShaderLod.java and shaderlod.mixins.json. Existing
core config/bridge/helper/Photon patches and prior dirty files remain untouched.
New extension protocol1 is separate from unchanged core2, INHERIT/FULL/ADAPTIVE,
six effects with band/ratio controls, unity default caps and lighting protection.
State snapshots are lazy per frame so unused extension suppliers allocate nothing.
Source registration is not driver linkage or adapter-consumption evidence.

AO investigation rejected simply dropping repeated depth texels: reconstruction
uses each original ray position and changing it changes horizon/falloff. The
bounded private candidate caches only consecutive in-bounds texel depth while
retaining all original iterations, positions, radius, dither and horizon math.
Undefined out-of-bounds fetches must never be reused. Default-off explicit source
patch, literal original fallback and global enable control; no gain is assumed.

Root starting serial `./gradlew test build compileDevClientJava` with no client
live. After source freezes/review, assemble exact private archive, prepare equal
stopped-world arms and hand one runtime operator the first forest smoke/pilot.

## Source acceptance; AO runtime handoff

Final integrated Gradle test/build/compileDevClientJava succeeded: all 47 Java
tests pass, including 14 new policy tests. Production JAR SHA256
`78c5c3ba883e365e8c6ba97f2e3e0ad0eee4214cf4b7614871e2fb8d5481ba3f`,
zero dev profiling classes/mixin files. Worker review corrected double-reading
the protection flag and clarified centered-band comments. Pure source/schema
checks do not establish live extension consumption.

Seven target arithmetic tests, 11 AO source/math fixtures and seven AO preparation
fixtures pass. Root assembled actual private default-off archive SHA256
`86f375aaf1b7d55a751595ca1087f14be155ac475d2e271f70be323d6829e9b9`,
manifest SHA256 `0a017ece0d9c9080e727879fc1dbdad312efb6385139a63bc1ba23560aa9729a`.
Two upstream members changed, 529 unchanged, license preserved. Only core version
and global enable are source-consumed when the compile option is selected; neither
all seven core fields nor extension1 is consumed by this private probe. Original
archive remains SHA256 `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.

Root transfers sole client/GPU ownership to `ao_runtime` after verifying no client
live. Operator may prepare fresh forest3-arm profiles, run one original/off/on
serial45s-warm25s-capture pilot at native2976x1672/minimal/noDH/sample5/frame-only/
CPUlayersoff/fixedwetness0, frozen dev classes, then validate and inspect images.
Record exact job/PID/run directory/archive/control/stop handles before continuing.
Do not extend into reverse repeats/other scenes without a useful candidate signal
and root decision. All other workers are source-only and may not build/encode/
compile/run clients while timed captures live. Root also freezes builds.

Audit says recent active pilot requires another37.07% forest/34.16% coast total
duration reduction to reach1.5x from those negative results. AO's qualified small
pass share cannot explain the target; this is a bounded parity gate. Source-only
next wave investigates higher-share projected translucent/volumetric work and a
minimal shared policy helper. No useful new gain, quality acceptance, shader
driver result or completed target is claimed at this handoff.

### AO pilot live handles

`ao_runtime` actual preparation exec95967 exited0. Fresh profiles
`run-workloads/photon-ao-depth-reuse-forest-pilot-r1/forest/{high,patched-off,adaptive}`
have equal canonical source/copy SHA256
`f752339d854ff2f0e0a7dc4e577dd6aedf2d0f69e39a9a7cae9dd0286c7be08b`.
Private archive/source qualification accepted and DH physically removed only from
fresh copies. Runner exec89240/PID37369; first original Java PID37388 at handoff.
Raw reports/console/provenance/PNGs will be retained under
`dev/logs/photon-ao-depth-reuse-forest-pilot-r1/`. Operator owns exact stop handles
and updates PHOTON_AO_REUSE_RUNTIME.md. Root/other workers do no builds, shader
assembly, clients or image processing during these timed windows. Terminal
contracts, actual compilation/rendering and result remain pending.

### Source-only next wave and timing qualification

`effect_api` froze a uniform-free general GLSL work-cap helper and eight binary32
source/scalar fixtures. These source tests passed; no GLSL compiler/driver result,
automatic packaging or adapter consumption follows. Domains/fail-open guards and
possible one-item float-versus-Java-double cap differences are explicit. Work-count
ratios must never be used as volumetric density coefficients.

`lod_opportunities` froze PROJECTED_DETAIL_LOD_PLAN.md: actual sample distance and
analytic pixel cone for unresolved fine procedural cloud density, full near/interior
boundary protection and original ray counts/integration. Current cloud temporal
factor is already4, POM/material maps off and water octaves3. Nonlinear opacity
calibration and sampler validation are unresolved. First candidate stopping gate
is a knowingly quality-changing fine-noise cost control; no implementation or
speedup is claimed from that plan.

Root's initial timing boundary allowed source fixtures. The new GLSL suite ran
about4.6seconds while the AO pilot was live; pyc import marker20:16:49Z is not an
exact suite timing interval. Root instructed all workers to stop test processes
during timings and runtime to retain possible external CPU activity qualification.
Do not promote the current one-block pilot as a quiet/causal/repeated gain. This
coordination correction is recorded in USER_LESSONS; failures/data stay retained.

## AO pilot terminal; first implementation milestone

Runner exec89240 exited0; matrix-manifest and matrix-analysis complete under
`dev/logs/photon-ao-depth-reuse-forest-pilot-r1`. All original/off/on requests,
archive/options/control/runtime/config/window/temporal/PNG and frozen-class
contracts passed. Actual native2976x1672 scene rendering/noFallback established
for the private source with its gate selected. New effects-client config was
created and loaded: six INHERIT modes, unity budgets and protected lighting.
This establishes startup/config registration, not extension uniform readback or
shader consumption. Private probe source only consumes core version/enable;
normal WorkloadSession does not read back uniforms or count physical depth fetches.

| Forest arm | Capture FPS | Mean GPU ms | Mean total ms (1000/FPS) |
| --- | ---: | ---: | ---: |
| Original, mod absent | 68.4552 | 13.2830 | 14.6081 |
| Same private source, global disabled | 72.6664 | 12.5266 | 13.7615 |
| Same private source, global enabled | 53.1302 | 17.7031 | 18.8217 |

Active GPU interval is33.28% above original and41.32% above disabled, unfavorable
for the candidate. This is one serial run per arm, not a proven source cause or
repeatable effect. Original/enabled remained unfocused; disabled remained focused;
all visible/noniconified/vsync-off/260cap/native24chunks. Uncertain source-fixture
CPU overlap is separately retained. Decline further AO repeats/secondscene now:
no useful signal and too-small qualified AO share to justify chasing the target.
Keep probe default off; do not integrate it into the normal Photon derivative.

Operator confirms clients=[] and class fingerprints unchanged. Root reran the
eight new common GLSL source/scalar fixtures only after all timed clients exited;
all pass. No common GLSL driver compilation or adapter consumption is established.
Runtime operator finishes owned PNG review/manifest audit before formal return.

Final operator audit accepted all366 original effective options, equal patched
maps/private-gate-on, native24/24/clear fixed pose, three PNG hashes/dimensions,
frozen class/source pins and neutral effects config. The actual new config SHA256
is `c02b81bc4ad7c90ac2e23e839845ddcfa8d7ab6909a380161f319ed414530984` in both
patched arms. Resized2048x1151 still review found no obvious broad contact-light,
depth-edge, foliage or shoreline failure; sheep/temporal phases differed, and
neither native pixel parity nor motion quality is accepted. Ignored extra audit:
`dev/logs/photon-ao-depth-reuse-forest-pilot-r1/ao-runtime-audit.json`.
Operator formally returned GPU/client ownership to root; all clients stopped.

Next action: assign a small pinned main-pass fine-procedural-density cost control
from PROJECTED_DETAIL_LOD_PLAN.md, preserving ray counts/extents and all other
passes. It is deliberately quality-changing and may alter occupancy; use it only
to decide whether calibrated footprint/detail LOD merits implementation, never as
accepted50% progress. Run one cloud-visible ordinary coastal/forest control with
quiet teams and fixed focus, then stop if flat/regressive. General interfaces and
policy tests are implemented; the performance/quality/compatibility goal remains
active and unachieved. No other-pack implementation, remote push or publish.

Source-only second implementation wave started: `lod_opportunities` owns only
new density-detail cost-control builder/manifest/tests/evidence doc; `ao_runtime`
owns only a new density runtime plan until a minimal preparation change is
reviewed. Root alone owns GPU/build. No diagnostic density control is assembled,
compiled, rendered or measured yet. Completed API/audit workers are idle; reuse
native teams for subsequent independent work. Before any next timing handoff,
all workers must freeze builds, tests and image processing and record fixed focus.

Local integration checkpoint `373804c` (Start general LOD goal) saved verified
first-wave sources/docs/tests. Prior dirty build.gradle, shadow design and
ComparisonClient edits and Miniature preparation files remain uncommitted and
untouched by this goal. No remote push. Density second-wave files are still
worker-owned source in progress; await source freeze before tests/assembly, then
runtime handoff. Native Goal remains active with no token budget requested.

### Density source freeze and review

`lod_opportunities` froze four new density-detail source-control files, and root
reran all eight source/scalar fixtures after the AO clients exited: pass. Manifest
SHA256 `5b66b98cf065857d4df66e0ebbdcfbec281e619e8cde85e8dc58e44fc46fb1bd`.
Root reviewed the uniform-coherent branch before the original fine texture fetch,
main PROGRAM_DEFERRED1 gate, core2/enable1 requirement, literal original fallback,
default-off option, and unchanged coarse/ray/integration/reconstruction sources.
Fixed0.5 changes density/occupancy and downstream early exits; it is exploratory
cost control, not an isolated fetch budget, accepted quality LOD or strict upper
bound. No actual archive assembly, driver compilation or timing yet.

`ao_runtime` is authorized to factor the existing AO preparer's stopped-scene
copy loop with default-preserving explicit inspector/experiment/marker/qualification
parameters and add a small exact-density qualifier wrapper/test/runtime plan.
No globals monkeypatching, generic source rewriting or new runtime harness. Preserve
the seven existing AO preparation fixtures and keep runtime/GPU/build with root.
Next exact action: review that source freeze, run bounded fixtures, assemble pinned
disabled density archive, prepare equal coastal cloud-visible arms, then hand off
quiet fixed-focus original/off/on cost-control capture. Stop before calibration
if the source region provides no useful aggregate signal.

## Continuation audit and next team wave

Previous goal turn was progress: committed general policy/config/Iris interfaces,
passed integration/source tests, and acquired the unfavorable actual AO pilot.
This continuation re-read STATUS/checkpoint and inspected processes: no client or
proof runner live. Root assembled actual density default-off archive SHA256
`0c0ac07a08a3c1e2c58658aabea95a564cc425795ff91c6a6b77bc0d8f2287d6`;
all member/source/license metadata qualification passed assembly, but compilation,
rendering and cost remain pending.

`ao_runtime` continues the minimal preparation refactor/wrapper. Root alone owns
GPU/build. `performance_audit` owns only new GENERAL_LOD_MEASUREMENT_RELIABILITY.md,
read/data review of variance/instrumentation/focus with a minimal executable neutral
control recommendation. `effect_api` owns only new FOOTPRINT_LIGHTING_LOD_PLAN.md,
source investigation of footprint/error-aware protected lighting (no budget/default
changes). Both are read-only during timed windows; no tests/compilers/encoders.
`lod_opportunities` density source is frozen. Current native objective is unchanged;
50% acceptance and full quality/config/compatibility proof are still unmet.

### Density preparation accepted; runtime handoff

Root independently passed seven unchanged AO preparation fixtures plus five new
density fixtures, reviewed the default-preserving stopped-copy callback seam and
the density wrapper's complete pinned source/member/metadata/license qualification.
Coastal native report/option pins, cloud40/20 light6 ambient2, original366-option
parity and noDH/native24 guards remain explicit. No Java/class/resource mutation.

Root transfers sole GPU/client ownership to `ao_runtime` for actual fresh
`run-workloads/photon-density-detail-coast-pilot-r1` preparation and one serial
water-high/water-patched-off/water-adaptive capture,45warm25capture,N5,frame-only,
CPUlayersoff,native2976x1672,minimal/noDH,wetness0,frozenclasses. Operator sets Codex
foreground once early in each warmup; all captures must be visible/noniconified,
stable unfocused with identical pacing. Reject any unknown/transition/cross-arm
focus difference. No tests/builds/assembly/encoders/image processing by other agents
until all clients terminal. Runtime operator records exact live/stop handles next.

No density driver/timing/quality acceptance yet. The fixed0.5 intervention changes
density and occupancy; only a source-region cost hypothesis is under test. Root
may consider a balanced repeat only for useful signal; no automatic calibration.

### Density r1 terminal: Iris option-discovery integration failure

`ao_runtime` actual preparation exec77224 exited0, canonical source/copies
`89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46`.
Runner exec84020/PID40110 exited1 after original and patched-off rendering:
strict validator rejected missing active Iris option
`SHADERLOD_DENSITY_DETAIL_COST_CONTROL`. Enabled was never launched. Retain raw
failed manifest/reports/PNGs under dev/logs/photon-density-detail-coast-pilot-r1;
no diagnostic activation/performance acceptance or source cost claim follows.

Warmup focus watcher exec29951/PID40294 stopped by exact SIGTERM (exit143), all
Minecraft clients stopped, classes/resources unchanged. Original focus action was
missed and deliberately not performed during capture; off warmup action succeeded.
Any new run must start the focus watcher before the first launch and reject unknown
or mismatched observed focus. Root owns GPU again.

The combined `#if defined SWITCH && defined PROGRAM_DEFERRED1` usage is a candidate
Iris boolean-option discovery issue, not yet a verified parser cause. Root assigned
`lod_opportunities` its four density source files to inspect pinned Iris parser,
correct explicit nested guard syntax and add actual parser/source-discovery
coverage. Own archive_defaults/source fixtures had passed but did not prove Iris
option discovery. `ao_runtime` updates only exact qualifier pins/tests/runtime
evidence after corrected builder freeze. Preserve r1 archive and reports; use a
fresh archive/profile/tag for retry. No unchanged-mode acceptance relabeling.

### Density r2 source/parser acceptance

Pinned Iris OptionAnnotatedSource bytecode and actual pure lexer prove the r1
cause: references are discovered from standalone #ifdef/#ifndef, not combined
#if defined guards. Corrected source uses outer #ifdef private gate and inner
#if defined PROGRAM_DEFERRED1, avoiding an unintended stage-macro option. Actual
lexer/OptionSet/MutableOptionValues tests prove gate discovery, default false/true,
both overrides, private gate as the sole added option/reference and no PROGRAM
leak. Root reran all nine source/parser/scalar fixtures: pass (no skips).

Corrected manifest SHA256 `542e6e61b1565532ce1d8975416917a29a866bc07165f80d5536d1a6124ad9dd`,
cumulus `3a5b3fbae654259567e76e660fac012fa0c2743e60c09519945f57d2e7440024`.
Root assembled actual corrected default-off archive SHA256
`066fc9862085cbd977d039c8a9efc31b4c9423eb018ec318533f0248adfdd1c4`.
R1 archives/reports remain in their fresh profile/log roots; no failure erased.
Strict wrapper pins updated only to the corrected source; original/coastal/native
cloud/viewport/runtime/license pins unchanged. Root reran seven prior AO and five
density preparation fixtures: pass. Java/classes/resources unchanged.

Root will transfer sole GPU to `ao_runtime` for fresh r2 preparation/capture only
after the bounded focus watcher acknowledges ready before runner launch. All
workers freeze tests/builds/assembly/encoders/images again. Use source/scene/control
and timing settings from r1, require observed fixed focus and exactly one private
option delta. Actual corrected driver/link/activation/performance remain pending.

### Density r2 live handles

Preparation exec84245 exited0; source and all copies have canonical world SHA256
`89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46`.
Private archive066fc.../manifest542e... qualify; profile mods empty. Watcher
exec83230/PID41590 acknowledged ARMED while output absent before runner, did not
create runner output. First warmup focus action20:51:25.865675Z succeeded before
capture. Runner exec8247/PID41611; original client PID41630 at launch. Operator
owns exact stop handles and next phase tracking in PHOTON_DENSITY_DETAIL_RUNTIME.md.
Raw output/profile roots photon-density-detail-coast-pilot-r2. Root/other workers
remain quiet; actual capture focus, gate, contracts, driver result and cost pending.

## Density r2 terminal; no calibration promoted

Runner exec8247 exited0; watcher exec83230 exited0 after three early warmup focus
actions. Operator validated UUID/source/archive/options/config/temporal/classes,
native2976x1672/requested-effective24/noDH, and focus=false/visible/noniconified/
unknown0/no transitions in all arms. All366 upstream options unchanged; only the
private true gate added, no stage-macro leak, patched maps equal. Class/resource
trees unchanged and all clients/watchers stopped; GPU returned to root.

| Coastal arm | FPS | Mean total ms | GPU mean/median/p95 ms |
| --- | ---: | ---: | ---: |
| Original, mod absent | 60.1758 | 16.6180 | 15.4416/15.3553/16.8092 |
| Same private source, global off | 60.8841 | 16.4246 | 15.3720/15.2213/17.1652 |
| Same private source, global on | 62.1495 | 16.0902 | 14.9639/14.8469/16.3433 |

Observed OFF→ON GPU reduction2.655% (0.4081ms), mean-frame reduction2.036%,
throughput+2.078%; original→ON GPU3.094%, throughput+3.280%. One quiet serial
quality-changing block, not causal/repeated/quality-equivalent acceptance or an
isolated fetch budget/strict ceiling. GPU within-run third-mean ranges0.183–0.271ms
are substantial relative to the contrast but are not a noise floor/CI. Mean render
thread CPU rises4.83% in ON. Root defers calibration/repeats at this weak aggregate
signal; do not promote a default. Density/occupancy/early-exit changes are retained.

Actual gate discovery and driver rendering now pass. Resized2048x1151 stills show
similar broad cloud/sun/shore composition, without conclusively isolated fine
opacity differences; no parity or physical texture-count proof. Raw profiles/logs/
PNGs/audit preserved under photon-density-detail-coast-pilot-r2. DENSITY_DETAIL_COST_DECISION
records exact hashes/drift and future minimal reverse-order/query-off controls.

Root authorized one existing sampler/core readback smoke (no GPU timing) to verify
core version2/enable1 linkage before closing the source region. `ao_runtime` sole
GPU owner for fresh photon-density-core-readback-r1 adaptive profile,25warm5capture,
GPUfalse/CPUlayersfalse/cloudSamplerProbe=true/frozenclasses; no new harness or
source code. Firstdraw uniform/sampler state is diagnostic, not settled reload,
density0.5 output or physical fetch-count proof. Exact handles to follow.

Next source-only team: DISTANT_LIGHTING_RATE_PLAN and SPATIAL_LOD_INTERFACE_PLAN
investigate a 2x2 far diffuse-irradiance cache with full-res albedo/specular retained,
near/material/depth/normal/light edge fallbacks. Ordinary Iris supports targets0..15;
Voxy colortex16 is not a free slot. Strict reduced-profile conditional colortex2
liveness/format/viewport/stage route is source-audited, not driver accepted. Keep
existing lighting protections and INHERIT defaults until actual quality/cost proof.
No code/performance acceptance follows from these plans; numerical goal unchanged.

### Density core binding smoke terminal

Existing sampler observer smoke preparation7277/watcher56490/launcher62163 exited0,
fresh UUID `ffec820f-e57e-4888-9abe-9c39af18e56e`, profile/log root
photon-density-core-readback-r1. Source/options/native24/fixed pose/weather/focus/
request/temporal/PNG/frozen-class contracts pass. GPU sample stream empty and no
timing inference. Exactly one of22 first-observed draw programs (numeric18) exposes
GL_INT size1 version2/location31 and enabled1/location38 at21:19:59.807476Z. The
frozen source declares this pair only under private cumulus PROGRAM_DEFERRED1;
main-cloud attribution is source inference, not an observed program name. Other
five core fields are inactive/unknown; no extension uniform readback.

Three remapped scalar3D samplers customtex1/0/2 are bound to nonzero objects with
LINEAR/base-max-level0/LOD0/aniso1/sampler0. Allocation dimensions/content were not
queried;64³ remains a source declaration. Firstdraw preceded fixed-camera control;
this proves sampled binding, not settled reload, density0.5 output, physical fetch
savings or quality parity. OpenAL shutdown warning retained, no graphics/probe
failure marker. All clients/watchers stopped, class/resource trees unchanged and
operator formally returned GPU ownership to root. No further density calibration.

Next action: `lod_opportunities` owns four new bounded lighting-region cost-control
files (builder/manifest/tests/evidence), not a pass graph or API expansion yet.
Approved simple far receivers only: decoded zeroSSS/sheen/emission/nonmetal/no
reflection mask, valid core2/global1/near/far/width, sky/hand/overlay/wetness/fullnear
fallback. The intentional control bypasses AO reconstruction/PCF/SSRT/diffuse
prefix and substitutes cheap declared values; current fullres material/highlight/
reflection/edge/fog composition stays common, but changed shared AO/shadow inputs
must be disclosed. Only this private-scoped PCSS's three comparisons use explicit
LOD0 based on pinned no-mip LINEAR_HW source; driver equivalence pending. Require
actual Iris parser sole-option coverage and original nonselected/off bytes before
assembly/driver. No tests/build/client during later timed handoff. Root owns GPU.

## User correction: independent parallel implementation

User explicitly reiterated that teams must spread work across many areas at once,
not mostly support one experiment. Root accepts the correction: serial GPU ownership
does not justify serial source implementation. Preserve the full50% objective and
quality evidence. Launch independent implementation tracks for distant lighting,
reflections, procedural/water detail, volumetric integration and mod interfaces.
Use isolated worktrees for main Java or overlapping adapter code, explicit distinct
new files elsewhere. Workers continue source work in parallel; coordinate heavy
local tests/builds away from measured25s windows and keep one GPU operator.
Record each code owner, acceptance check, stopping boundary and integration queue.
No new native goal, platform, supported pack, remote push or external message.

### Parallel code ownership and current freezes

User also questioned performance comparability. Root clarified that prior5.6%/
1.2% FPS regressions were a particular experimental five-effect candidate in serial
windowed native-size captures, not a replay of the user's roughly10% faster
fullscreen observation. FPS=renderedframes/captureseconds afterwarmup; asynchronous
GPUelapsed every5frames is separate. CPU load/focus/clocks/source/window changes
remain unisolated; no contrary universal current-LOD claim. Future benchmark batches
must be quiet, matched source/settings/fullscreen and balanced, while source
implementation remains parallel. Root primarily orchestrates these tracks:

| Agent | Owned implementation | Workspace and stopping boundary |
| --- | --- | --- |
| effect_api | OpaqueLighting/effectsVersion2/43fields, config commands, coherent snapshots, reload handling | Managed lod-interfaces worktree; commit2892035,60tests/build pass, root integrated exactpatch and3test-classpath lines preserving prior dirtybuild; runtime/reload/consumption pending |
| lod_opportunities | 2x2 distant irradiance prepass/resolve, full-res albedo/specular, practical edge fallbacks | Managed lod-lighting-rate worktree; actual code in progress, no GPU/source support claim |
| reflection_lod | Bounded projected refinement termination+depth reuse, original full traversal/coverage | MAIN distinctnewfiles;9source/parser/mathtests pass, default-off archive d11de4c94fb58520e9972a816e9739486fa8578b876fbd20c17aa933a4a82e16; driver/quality/cost pending |
| procedural_lod | Footprint-filtered water octaves with actual displaced coordinates/parallax, fullnear/caustics | MAIN distinctnewfiles;13tests pass, default-off archive894c5c37de5d0427f283775a01fec0de1a4fde5a2162d108993bd648b5b96a1a; sampled far normals22.115degrees risk, no quality/gain acceptance |
| volumetric_lod | General interval/budget helper and primary cumulus integration, unchanged total interval/secondary work | MAIN distinctnewfiles;12tests pass, default-off archive3685e47e94cd08f3994053f300981bbdd92ae108a65f7fadb700bd6d9422a6b5; active controls/runtime quality/counts/cost pending |
| lod_quality | Reusable2x2 surface continuity/near/depth/normal/material/lightmap guards | MAIN three newfiles;9tests/300planarfixtures pass, helper2fff58476c6d49d40eb61e5f6683dc58697ed964f128a3f444442677b06327b0; handed tolighting implementation, shader/quality unverified |
| ao_runtime | Shared explicit source-qualified profile preparation and driver/quality queue | Ownnewprepare_parallel_lod_profiles.py/test/PARALLEL_LOD_RUNTIME_QUEUE.md; source implementation in progress, no launch until rootreview; oneGPUowner only |

Managed paths: `/Users/pippo/.codex/worktrees/lod-interfaces/shaderlod` and
`/Users/pippo/.codex/worktrees/lod-lighting-rate/shaderlod`, based on67ef5ba,
registered tothischat. Ignored dev/runtime/upstream are read-only symlinks to main;
no binaries/symlinks committed. MAIN finalinterface integration
`./gradlew test build compileDevClientJava` passed (test task/fromcache60 matching
inputs); no current Minecraft client, root owns GPU. No main sampler/dev harness
change; archived r2 proofs remain historical class fingerprints. Heavy CPU checks
pause only during dedicated timed windows; untimed compilation/quality diagnostics
permit parallel source development with no performance inference.

### Parallel integration checkpoint

Root saved source/interface integration as0a4d7a9. Main test XML proves60tests,
0failures/errors/skips; test/build/devcompile succeeded, using matching Gradle
cache entries for unchanged inputs. Source-only reflection9/procedural13/volume12/
continuity9 fixtures and artifact hashes are recorded in their owned reports;
these do not establish driver/quality/gain. Original procedural overlayLICENSE
bytes intentionally retained with upstream whitespace, exempted only that file
from whitespace lint; allotherstagedchecks passed. Prior dirty fullscreen build
hook, ComparisonClient and shadow-design files and Miniaturepreparation remain
unstaged/untouched. Only newtest-classpath build hunk integrated/committed.

RootGPU currentlyidle. Next exact actions: review ao_runtime's sharedexplicit-pins
queue sourcefreeze, then serial untimed driver/config/core/qualitysmokes for ready
reflection/volume/water candidates while lighting implementation continues in its
worktree. Untimed results cannot be performance numbers. Volume inherits conservative
0.85/0.75 budgets or requires explicitrecordedADAPTIVE settings; command/producer
v2/sourcefields linkage remains real-driver acceptance. Old observer reads seven
core fields and inventories active uniforms; it does not read all43effectvalues.
Queue that observability gap explicitly ratherthan infer values fromsavedconfig.
AftercandidateQA, schedule a quiet true-fullscreen matchedoriginal/disabled/enabled
balanced benchmark batch with actual source/class/scene/config/window fingerprints,
query-free total-throughput corroboration for useful signals. Goal remains active;
no new LODperformance orquality claim, target not reduced.

## Continuation: runtime observability and combined integration

Previous turn was progress: independent source implementations were committed,
interface worktree code integrated and main60tests passed. Current state inspected:
no live Minecraft/proofrunner; main retains only prior dirty fullscreen/shadow/
ComparisonClient/Miniature files plus new integration work. Full objective retained.

Root assigned effect_api in isolated lod-interfaces worktree a bounded extension
to the existing opt-in observer: read actual effectsVersion2 and Volumetrics/
OpaqueLighting fields, 2D-shadow sampler state and allocation information. No
supplier callbacks/setters, no query when disabled; source/driver distinction.
lod_quality owns separate raster-hook/mixin-list/plugin files in the same worktree
with explicit partitions. Source hook targets pinned Sodium native draw boundary,
only applies when cloudSamplerProbe=true;3source/plugin/bytecode fixtures pass.
Compile/integration/runtime acceptance still pending. Their Gradle is coordinated.

feature_integration owns explicit three-track combined builder/manifest/tests/doc
in MAIN, not a universal rewriter. It identified duplicated core declarations
across copied helpers and procedural #ifndef include-guard option leaks. Root
assigned procedural_lod its own exact guard/full-recursive-Iris-parser correction;
old d504.../894c... procedural pins are superseded pending freeze. Runtime queue
must not copy old procedural artifacts. Combined writer continues with exact
known payload transforms and waits for final source pins. All-off/isolated-switch
shader parity and complete source-option union are required; no gain summed.

ao_runtime owns shared explicit-pin profile preparation/queue implementation,
currently source work only. Reflection/volume remain source-frozen and ready for
first untimed compile/quality tests after rootreview. Lighting 2x2 pass implementation
continues independently in its worktree. Root owns idle GPU; ordinary source/tests
continue in parallel. Performance tests wait for quiet fullscreen matched batches.

### Integrated observer and first untimed runtime handoff

effect_api commit6a4d835 adds read-only effect-schema2 version/Volumetrics/Opaque
values (13fields), scalar2DShadow comparison state, level0 allocations and draw
origin, preserving core7. lod_quality commit610f7ce adds the opt-in-only Sodium
raster boundary hook. Root applied both exact owner patches; main test/build/
compileDevClientJava and processDevClientResources/classpath generation pass.
Main60tests unchanged/valid; root3rasterhook fixtures pass. Runtime state/query
restoration/effectvalues still require real GL context; no diagnostic timing claim.

Procedural r2 manifest5849bc24c84f98b42a4ad7a85b013cdab6bb229a3f8c2d8e8c48d6097cd9e687
replaces only3helper guards and fullrecursiveIrisfixture coverage.13tests pass,
default-off archiveeb9fa7db90fffff1ee264b90098a2b14a8dd322d3e8a111b8460fc329eea0719.
Old helper option leaks are reproduced; no logic/quality claim changed.
Combined manifest238da80f9bf998fe12ba857ced929c2b79129fed895c95e51a569141977350fc
and default-offb6eb562df5ecdae8521e8aa7dbe0ba395e53f28e55466f0eabfeb11ce70a9df9
are frozen:7fixtures pass, all531original members restore,329valid entrypoints
off/isolated parity, exact3publicoptions and8deterministic arms.3unmodified dormant
upstreamTAAU errors explicitly retained. No actualGLcompiler/performance proof.

Root reviewed queue and independently passed6preparationfixtures. New soleGPU
handoff to ao_runtime: fresh parallel-reflection-qa-r1/coastalwater/adaptive,25warm/
5capture, GPUfalse/CPUlayersfalse/cloudSamplerProbe=true/native2976x1672/minimalnoDH/
wetness0/frozenproducer-v2classes/resources. Onlyreflectionfirst; no timings or
additional launch untilfirstresult reviewed. Source/code/tests may continue in
parallel because this is untimed; no other client/reloads/GPU. Exactlive/stophandles
tofollow in PARALLEL_LOD_RUNTIME_QUEUE.md. Probe reads sampled binding/state, not
physical savedrefinements, settledreload orquality parity. Goal remainsactive.

### Reflection QA live handles

Actualpreparation exec79834 exited0; canonical source/copies89c962... and exact
private d11de4... archive qualify. Watcher exec54250/PID95954 ARMED beforelauncher.
Launcher exec47437/PID96022, Java96042, UUID
`08fed9f1-43df-4a6b-ada7-0d6160bc5cfb`. Profile/log roots parallel-reflection-qa-r1,
onlyadaptive. MAINclasshash17286dd585d5c1b34f2f0332a869a3bb6920a52d021c84d6bf3ae48732eef9f3,
DEVclasshasha5e7d5f8d30bd4d4b275c933b3d155522ce09cfca07f7d786928465685f8330f,
current observer/raster resources frozen. Operator owns exactstop handles and
validation in PARALLEL_LOD_RUNTIME_QUEUE.md.25warm5capture, GPUfalse/minimalnoDH/
native2/wetness0; no FPS/GPU inference. OtherCPU/sourcework maycontinue, no other
client/reloads/GPU. Root next awaits actualterminal driver/field/sampler/visual
contracts before further queue launches; timedperformance/50% proof stillunmet.

### Reflection untimed QA terminal; volume next

Launcher47437 and watcher54250 exited0; client96042 ended naturally and all
processes absent. Operator confirms unchanged main/dev/resource hashes, emptyGPU
sample stream. Exactd11de4 reflection pack compiled/rendered, shaders=true/noFallback,
all366upstream options unchanged and soleprivategate=true. New observer28programs
captures core2/enable1/near32/far96/width8 with matching scalar types/locations in
fullscreenprogram21 and raster458; before/after active texture restored. This is
sampled connection proof, not physical refinement saving or live-reload guarantee.

Actual noise allocation512x512RGBA8/LINEAR/base0 and shadow2048x2048depth/LINEAR/
LEQUAL comparison; depthtex1NEAREST firstfullscreen1708x960 thenraster2976x1672
reflects early viewportresize, not contradiction ofnativecapture. SevenINHERIT/
unity effects loaded; reflectiondeclares no effectconsumer, inactivevaluesunknown.
Nativeglint/near/far still crops show no obvious broad brightwaterband; singleactive
still is not original/off parity ormotion/temporal acceptance. No FPS/GPU claim.
Raw firstQA evidence retained underparallel-reflection-qa-r1; operator returnedGPU.

Root authorizes next serial untimed volumeQA while source implementation continues:
freshparallel-volume-qa-r1/adaptive, exact3685e47...pack, explicitADAPTIVE
512/1536/128,.85/.75,25warm5capture,GPUfalse/newobserver/nativeresolution/minimalnoDH/
wetness0/frozenclasses. Verify actualeffectsversion2/VolumetricsMode2/bands/ratios
via typedreadback, notsavedconfiguration inference; originaldispatch/overheadguard
maystillselectfull integration. Source counts/linkedvalues do not prove physical
iterations. Operator soleGPU; no secondlaunch/timing untilrootreviews nextresult.

### Volume driver milestone and next parallel wave

Volume launcher56444/watcher93401 exited0; Java99874 stopped naturally, all
owned processes absent, frozen main/dev/resource trees unchanged. Exact QA audit
is `dev/logs/parallel-volume-qa-r1/volume-qa-audit.json`; GPU stream empty.
Selected pack compiled/rendered without fallback and actual scalar readback
establishes effectsVersion2, VolumetricsMode2, bands512/1536/128 and ratios.85/.75
at valid locations. Core2/enable1/32/96/8 also read. Actual customtex1/2 allocate
64³ R8 with LINEAR/base-max0/LOD0 sampling; customtex0 is a distinct lookup.
Native cloud still/crops show no obvious gross failure. Guards may retain full
integration; no physical iteration saving, motion parity or speedup accepted.
Operator formally returned GPU before the next handoff.

Root integrated lighting worker commitdb016f9's exact13newfiles and assembled
default-off ZIP713e729701acba72a5c22e62e23bfc7b7782eaa82e70502e185631a7e15e710e.
Main eight source/math/actual-Iris/parser/assembly tests pass,0failure/skips.
Manifestd41c94e023d08fcf22798e29248d19ee294148f27008615742af78bcb6419cf4.
This is actual half-viewport prepass/full-resolution resolve code; buffer2 format,
attachment/flip/sampler limits and rendered quality remain driver acceptance.
Coastal ENVIRONMENT_REFLECTIONS=true selects the original graph; the held forest
profile has false and must qualify all other unsupported options independently.

Parallel bounded assignments: lighting owner prepares NEW explicit forest profile
preparer/fixtures and its implementation report; quality owner writes independent
LIGHTING_RATE_REVIEW; procedural owner develops NEW common normal-error-budget
helper/math fixtures/report without changing frozen water pins; reflection owner
writes cost review, leaving its pins unchanged. Benchmark owner finishes NEW
fullscreen batch preparation/fixtures and narrow additive runner forwarding.
No parallel edits to owned files; no unsupported pack/framework expansion.

Producer detail corrected during independent review: OPAQUE_LIGHTING is currently
not designated salientLighting, so preserveSalientLighting=true does not cap its
ADAPTIVE ratios. INHERIT mode0 allows the explicitly selected private adapter's
1/.25 policy. Prepared tests will retain protection=true and explicitly request
ADAPTIVE32/96/8/1/.25; disabling protection is not required. AO/shadow designated
salient effects remain protected. Requested configuration and actual transport
remain separate evidence.

Root hands sole client/GPU to ao_runtime for ONE fresh untimed procedural-r2
`parallel-procedural-qa-r1/adaptive` coastal smoke: exacteb9fa7db...0719 ZIP,
privategate=true,core2/global1/32/96/8,25warm5capture,GPUfalse/CPUlayersfalse,
newobserver,native2976x1672,minimalnoDH,wetness0/frozenclasses. Check original
3-octave/parallax profile, shader compile/noFallback, actual linked controls and
samplers, then native water/glint/near/far stills. Exact handles go in runtime
queue. Stop at terminal audit and formal GPU return; no secondlaunch or timing.
Source work continues during this untimed diagnostic. Repeated quiet fullscreen
and quality/compatibility acceptance remain outstanding;50%goal staysactive.

### Procedural driver rejection; independent repairs continue

Procedural preparation61151 passed; watcher8692/PID5994 armed before launcher8612/
PID5996, requestd6b4ba77-e2f7-4141-bb39-331c1ec10894. Exact source3octaves/
parallax/waves/displacement confirmed. Launcher exited1 on actual Iris
TransformPatcher failure for terrain_translucent: ParseCancellationException
line14969:77 missingRPAREN at'{'. shadersInUse=false/fallback=true. Watcher exited0,
all clients stopped and class/resources unchanged. Raw failure stays in
dev/logs/parallel-procedural-qa-r1. No connection/sampler/quality proof can be
derived from vanilla fallback; no second launch. Existing source tests did not
cover this full transformer parse. Procedural and combined runtime batches must
wait for corrected source, new pins and driver retry.

Root created attached isolated procedural-parser-repair worktree at24f4d3d and
assigned procedural_parser_repair the procedural sources/manifest/builder/tests/
report there. Reproduce oldfailure using actual pinned fullparser, repair syntax
without changing math, and return fresh pins/commit; no MAIN edits/client.
Existing procedural owner continues NEW common normal-error-budget work.

Lighting independent review found representative2x2 AO eligibility misses outer
neighbors used by other tile members' reconstruction. lod_quality now owns ONLY
lighting prepass/manifest/existing shaderfixture for a bounded full-union guard
repair and fresh pins. Lighting profile owner continues separate NEW preparer/
fixtures/report and waits for new pins before final qualification. Driver launch
is deferred until known quality gap is fixed; old713/d41 remain historical freeze.

Reflection source audit found expensive early-stop proof saves no extra depth
fetches beyond cache-only continuation. It can save refinement arithmetic, but
cost is unmeasured. Root created attached reflection-guard-cost worktree at24f4d3d;
reflection owner implements cheap necessary gates/reused extent/homogeneous
displacement bound with finite/pole/float32 fixtures, preserving MAIN freeze.
Review in REFLECTION_LOD_COST_REVIEW.md. No gain inferred from source operation
counts. Benchmark preparation continues with explicit driver rejection noted.

### Integrated source repairs; driver retry and combined refresh

Previous goal turn was progress: commits24f4d3d/525bda2 integrated actual distant
lighting code and recorded driver evidence/rejections; bounded workers implemented
independent fixes. Continuation revalidated all agents and processes; no previous
Minecraft client survived. Operator formally returned GPU after rejected r2.

Root reviewed/applied procedural repair6eb12b1975e3b9ef8d7c61cdf009df868a4f5144
from attached procedural-parser-repair worktree. Exactly one missing ')' changed
the shader, no arithmetic.14fixtures pass,75 complete affected actual Iris Jcpp/
EnhancedParser graphs pass; reinserting broken delimiter reproduces failure at
column77 in all3water dimensions. Actual main disabled assembly hash
267f6358e87b8dd51e4e5a6fcf26f999344f8e1abf79fb36c5bd99fa0d7bd9ea;
manifestb529fdb01aabef5670128e04a25224bfd4368c2a63641214023c352099d100e2.
This supersedes r2 source pins, not its retained failed runtime evidence.

Root reviewed/applied reflection guard revision8c2739385e39ca4c201cc98cd9eeb45c7dc9c194
from attached reflection-guard-cost worktree. Early uniform/necessary footprint/z
gates, exact extent and finite homogeneous error envelope replace late endpoint
divisions. Original coarse cache/coverage/weights preserved.12fixtures pass,
including1400 float32 matrix/position trials, FMA/non-FMA and finite/pole/extreme
cases. Actual main disabled assembly485590fa1a70ce43e4fe271a8a19f869d0cbf69767f1bda435b5deed9e41aa35;
manifest0c0905612ddacf358b92c2d112bb17f2d107de224551d418a6a67c8529ccf4ec.
Additional safety ALU/failed eligibility can outweigh removed divisions; no net
GPU/quality acceptance for r2. Prior reflection driver result applies only to r1.

Lighting AOunion fix source freeze683cee198706d7289ef3994212edace9fd200ffeebff458e1c317a306751b3d4,
disabledfca1a3115ad7cb5341807d7a06cb6939228da4603200fa831b5bdefd6eed206a.
Nine fixtures pass; full member AO union/dimensions/finite/edge guards and literal
representative math tested. Classifier adds at most9AO+9depth reads per selected
tile, included in future measured cost. Quality owner adds full translation-unit
grammar checks in owned fixture/report before runtime; profile owner prepares
separate forest controls with these new pins and retained lighting protection.

feature_integration owns combined refresh for both new water/reflection pins,
complete affected grammar coverage and all-off/isolated source parity. Benchmark
owner's old-pin tests correctly reject changed manifest; it waits for replacement
freeze before final preparation. No all-six performance run authorized. Source
eligibility remains separate from fresh standalone and combined driver acceptance.

Root authorizes sole GPU to ao_runtime AFTER its explicit family-pin update,
qualification fixtures and fresh stopped-world preparation: ONE untimed
parallel-procedural-qa-r3/adaptive coastal retry,25warm5capture/GPUfalse/CPUlayersfalse/
cloudSamplerProbe=true/native2976x1672/minimalnoDH/wetness0/frozenclasses. Actual
consumed core version/enable only; configured bands are inactive here. Record
exact handles before launch, compile/noFallback/typed bindings/samplers/native
still audit, then stop and return GPU. No second launch/timing; other source work
continues. Next prospective run is an original forest true-fullscreen qualification
to discover dimensions/focus/pacing once benchmark code is frozen and retry returns.
50% throughput, repeated GPU/frame, motion quality and compatibility remain unmet.

### Rendering repair accepted; performance loop prioritized

Procedural r3 launcher26601/watcher57388 exited0; Java20626 and all clients stopped,
classes unchanged. Exact267f6358... shader compiled/rendered without fallback,
native/pose/options/config contracts pass. Raster program458 reads core GL_INT
version2/location26 and enabled1/location27; other configured bands remain inactive.
Actual noise unit6/object57 allocates512² RGBA8 LINEAR/base-max0/LOD0, active texture
restored. Native water/glint crops show no obvious gross band/holes. No physical
wave counts, off/on/motion quality or FPS/GPU gain claim. Formal audit and GPU
return are in PARALLEL_LOD_RUNTIME_QUEUE.md and ignored procedural-r3-qa-audit.json.

Combined r2 manifestdcbb31d5878387bd00bdb048703be9b6c22539b30844ee8054dcc455a533cf19,
default-off87e12ac10401cf59c36d76eaabd1b7fdb9b8dc66365dfebdcfa2c7e7a329e41a.
Eight fixtures pass including411 complete affected Iris Jcpp/GLSL parses across
eight combinations, three old-syntax regressions and original/isolated parity.
Main commits4c0f178/ebcf2f8 preserve these source changes. Driver/QoS not inherited
from standalone sources. Lighting profile source preparer actual stopped copies
completed: seven fixtures and five equal canonical forest worlds, fixed FCA1 ZIP,
explicit OpaqueADAPTIVE/protectiontrue plus separate optionOFF and debugcue controls.
No lighting driver or performance proof yet.

Normal-error helper14tests and source-domain report committed5679cf2. The older
22.115degree fixture is an over-range flowing-water stress case, not reachable
flow settings; supported sampled domain/default maxima are retained in report,
still unaccepted visually. Strict0.5degree budget allows no full octave skip for
ordinary3wave settings. Variance helper12tests found no justified automatic water
roughness compensation; mirror SSR directions and correlated moments prevent
that claim. No consumer integration or performance credit; this track stopped.

Fullscreen code committed2556b61 after nine new and19legacy tests passed. First
actual master preflight found a str-versus-Path callback mismatch before any copy
or client. A new real callback test also exposed all-archive include macros being
miscounted as public settings options. Benchmark owner repairs normalization and
exact settings-declaration check without weakening whole-source/metadata or actual
option guards. Original master-r1 target remains absent; no fresh fullscreen
result claimed. Sole operator may retry that unreserved operation after tested
fix, then launch ONE original-only25warm/5capture query-free true-fullscreen forest
qualification with natural stable foreground and observers/CPUlayers off.
Record exact handles; no LOD performance batch yet. Other source work may continue.

The user again explicitly asks whether work focuses on general LOD performance.
Root confirms the reusable work-reduction direction but acknowledges excess helper/
validation work before measured benefit. Prioritize actual shading-frequency and
volumetric budget reductions, earlier matched feedback and rejecting costly micro
optimizations on evidence. New parallel implementation ownership: lod_quality
NEWcommon surface_region_continuity.glsl/fixtures/report (all up-to16members);
lod_opportunities NEW4x4 irradiance adapter/builder/manifest/tests/report. One
representative per16 far pixels, full-resolution materials/near/invalid preserved,
explicit pinned Photon first reference; no universal rewriter/default quality cut.
Existing2x2 code/controls frozen for separate QA. Normal variance owner finishes
and stops, no more unused helper research. Goal target remains50% with little
discernible difference; no new measured gain or reduced acceptance criterion.

### Latest user clarification: the LOD system is the product

The user explicitly rejects shader-specific optimization as the development
objective. Build the general, extensible LOD system; Photon is the first explicit
adapter and test case. Root acknowledges drift into Photon reflection-cache and
projection tuning. Those experiments are set aside rather than promoted. The
standalone 4 × 4 Photon task was stopped before any files were created. Its shared
region continuity helper finished nine source/math/parser fixtures and remains
unconsumed. No further pack-specific micro-optimization or helper expansion.

Next bounded team implements reusable adapter/work-capability contracts and
shared shader-side distance/work decisions, then integrates one existing Photon
distance LOD consumer through that common contract. The mechanisms must live in
the shared system; pack code supplies effect inputs and performs the declared
operation. Preserve Iris/NeoForge, optional DH, explicit pinned adapters, actual
consumption evidence, and the unchanged 1.5 × throughput target. No new native
goal or automatic rewriting system. Latest working agreements limit normal
parallelism to four useful workers, at most eight open workers, parent scheduling
and one exclusive client/GPU owner; no recursive untracked teams.

Original fullscreen qualification remains live and authorized for a general
measurement baseline: runner 45970 / PID 29578, Java 29602, forest-high only,
original Photon, no mod, no DH, query-free 25 s warmup / 5 s capture. Masters and
independent stopped-world copy qualified after routine callback repairs. Logs:
dev/logs/fullscreen-lod-original-smoke-r1. Operator ao_runtime owns this exact
client and stop procedure; no focus watcher or capture focus changes. No candidate
run follows without shared-system review and a new handoff. This is not a LOD
performance result; classes and actual window conditions still require audit.

### Shared system milestone: implementation and rendered connection

Commit `000f355` implements the clarified direction: pure adapter/work-capability
contracts, explicit source binding, public registration of descriptors/source
pins/capability exports, immutable sealing before Iris pipeline binding, and
common shader work decisions. Core 2/effects 2 semantics remain unchanged; the
capability schema is separately versioned at 1. Photon is a separate reference
registration, not a policy engine. Unknown sources/options/protocols and renderer
fallback retain full work. No universal rewriting or additional shader support.

The reference volume consumer delegates to shared policy and retains the exact
prior march body, density, optical normalization and secondary work. INHERIT
resolves unity; ADAPTIVE uses the six general controls. Default-off archive:
`5de7561dd24cdca80860a1d5f2c60400e74163a489cc163e916f3fd47feb27de`;
manifest `40efd7e702e41388033255d48a007c543e1055ccb4e2513c1908c2d82b8f2677`.
Both reviewed archive variants have ten exact reference member pins and actual
option requirements. Merely registering them is not rendering evidence.

Checks completed: 103 Java tests, zero failures/errors/skips, integrated test/build/
dev compilation; 15 volume source/math/grammar fixtures including 132 complete
graphs; seven shared policy fixtures with 170 Java-oracle cases; eight capability
and seven existing effect-readback fixture groups; six strict legacy-profile
fixtures after explicit shared-volume pin refresh. The first plain-JUnit Iris
ServiceLoader failure is retained and repaired with narrow unavailable-runtime
fallback and regression. No gate or physical-work claim was relaxed.

Current production JAR SHA-256:
`aa65cf9157e0c5dac7db6ea65d38a42b3eec06a71838e394389063ead5b3e672`.
Root verified no dev entries. Build log: `build/shared-lod-extension-build.log`.
Public extension tests cover another adapter/custom export, collisions, late
registration and coherent snapshots. See `LOD_EXTENSION_API.md` and
`LOD_ADAPTER_BINDING.md` for the explicit integration contract.

Current-revision r2 actual rendering passed: launcher 33338 / PID 43256, watcher
81739 / PID 43240, Java 43270 all exited and are absent. Exact source/options/
scene/config/class/resource/native-window guards pass. Program 18 sampled actual
scalar GL_INT capability schema 1 and availability 1 at valid locations, plus
core 2/enable 1 and effects 2/ADAPTIVE/512–1536 blocks/128-block transition/0.85–0.75
ratios. Noise allocations and active-texture restoration pass. No capability-zero
sample was observed. Native cloud still shows no gross holes/stripes; this does
not establish parity, movement, physical sample counts, DH or speed.
Evidence: `dev/logs/shared-lod-system-qa-r2/shared-lod-terminal-audit.json` and
`SHARED_LOD_RUNTIME.md`. Main runtime digest `73568930…`, dev `2a1ce5aa…`;
exact inventories are in the run. Earlier audit digests used a different
serialization: root verified identical member hashes before accepting them.

Original fullscreen attempt 45970 was rejected, not retried: capture focus,
iconification, visibility and framebuffer dimensions changed. No cause or
performance result inferred; raw evidence remains. All clients/builds stopped,
root owns idle GPU, all scoped workers reached handoffs. Next decisive work is
shared-system original/OFF/ON physical-work/parity/quality evidence and repeated
quiet GPU plus frame measurements. Spatial-tier transition planning is still
incomplete; exact-rate negotiation alone does not provide smooth transitions.
The 1.5 × throughput and little-discernible-difference goal remains active and
unachieved. No new performance gain is claimed.

### Performance pilot and next decisive measurement

Previous turn made concrete progress: shared system compiled, public extension
registration passed 103 Java tests and current-revision shader capability readback
passed. Continuation revalidated workers/processes and retained the full target.

A nine-run native-windowed pilot was prepared with fresh canonical copies and
position-balanced O/D/A, D/A/O, A/O/D blocks, 25 s warmup / 20 s capture, N5
frame-only GPU queries, observer/detailed CPU instrumentation off. Spatial workers
paused heavy checks during the batch. Runner 25403 / PID 46215 stopped on run 3's
exact artifact guard; all clients ended and class/resources remained frozen.
Only the first block ran; no retry or extra favorable-data search.

Independent read-only extraction reproduced the existing exact API Base pair:
17 classes/manifest/resources identical; only a compatible NeoForge minimum in
mods.toml differs. Parent Iris/Sodium and runtime byte hashes match. Separate
derived analysis cites every raw input and the exact equivalence registry; the
raw manifest remains FAILED. One-block medians in ms:

| Arm | GPU elapsed | Total-frame wall |
| --- | ---: | ---: |
| Original | 15.124459 | 16.222166 |
| Global OFF | 15.736584 | 16.616917 |
| ADAPTIVE | 25.949417 | 26.725875 |

ADAPTIVE/original throughput ratio 0.593220; GPU time +71.6%, frame median +64.8%.
These are qualified single-block diagnostics, not repeated/causal or quality
acceptance. Coarse consumed-thread changes are smaller than wall changes; host
load/app context varied and elapsed GPU can include starvation. Evidence:
SHARED_LOD_PERFORMANCE.md and ignored shared-lod-benchmark-r1 reports/diagnosis/
first-block-qualified-analysis.json. Future wrapper uses the established exact
registry with seven positive/mutation rejection fixtures; no wildcard bypass.

The work audit found no duplicate complete march, but resampling can change
occupancy and early exits. Synthetic source-faithful examples increase actual
density calls despite lower primary budgets; these are counterexamples, not
observed GPU counts. The reference march/density body stayed unchanged during the
shared refactor. Next decisive check is actual per-ray operation counts, not more
ratio tuning or Photon caches. See SHARED_VOLUME_WORK_AUDIT.md.

Shared spatial source in `137095d`: centered convex legal-tier plans and a
reconstruction kernel with producer-certified all-member continuity. Resolve
avoids repeated full-region classification under exact epoch/quantity/rate/tile
and immutable-input contract. Ten plan fixtures and three kernel fixtures pass,
including actual Java/GLSL/source execution. No live driver/quality/speed claim.

Active bounded owners: work_trace_mechanism (new common counter/codec + fixtures,
five test groups passed), work_trace_adapter (separate pinned diagnostic shader
builder/patch/overlays; shipping source unchanged), work_trace_capture (opt-in
dev registration/raw three-MRT readback + mock/bytecode tests, bounded scene-start
hook). Capture is diagnostic-only with CPU/GPU timing disabled; counts and three
exact pages belong to one draw. No universal instrumentation or shader rewrite.
Root owns idle GPU and build window; no client or timed launch authorized yet.
Preserve ordinary source erasure and all state restoration. Goal remains active;
repeated gain, physical savings, little discernible quality loss and compatibility
remain unproved.

### Diagnostic source freeze and handoff

`6794654` preserves the failed/derived pilot, its strict future wrapper and the
general work-trace mechanism plus diagnostic consumer/readback. Five helper test
groups, five diagnostic adapter fixtures (270 complete Iris graphs, 90 trace-OFF
parity checks), six readback fixture groups and pinned bytecode acceptance pass.
Production archive `5de756…27de` and JAR `aa65cf…e672` remain unchanged. Diagnostic
counters compile out of ordinary shaders. Three simultaneous RGBA32F attachments
capture actual work/budgets/schema from one draw after workload warmup, with state
restoration. CPU/GPU timing disabled and every diagnostic row excluded from
performance evidence. Source acceptance is not actual attachment/readback proof.

Root dev compile/resource generation succeeded in
`build/work-trace-dev-compile-retry.log`. An initial command named a nonexistent
classpath task and failed before compilation; its log is retained. Only dev files
and isolated workTrace/cpuTiming forwarding were integrated; unrelated fullscreen
and other user changes remain unstaged.

Fresh sole runtime owner `work_trace_runtime` prepares and may launch at most two
matched diagnostic controls: original-body global OFF first, shared ADAPTIVE ON
second only if first driver/raw capture succeeds. Both have mod present for
pre-pipeline diagnostic registration. Exact source/options/member/registration
ledger: `build/photon-work-trace-freeze.json`. No retry, tuning or performance claim.
Its NEW preparer/fixtures/WORK_TRACE_RUNTIME report are owned separately. At this
checkpoint no Minecraft process is live; owner will record exact handles before
launch. No other worker builds or operates GPU. Remaining decisive evidence is
actual synchronized counts/path validity, followed by a reviewed system change
and repeated ordinary timing. Goal stays active with the full target unachieved.
