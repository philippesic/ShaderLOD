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
