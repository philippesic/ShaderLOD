# Sustained performance and compatibility development

Created: 2026-10-04. Status: active.

## Authorization and objective

The user authorized sustained agent-team development with broad implementation
freedom, keeping Photon first, NeoForge and the current Minecraft target. Improve
performance and the mod, benchmark with/without performance mods, and expand to
other popular shader packs. The fresh-chat authorization permits research and
exploratory testing now; accepted support and performance still require evidence.
The user will check in periodically; routine implementation choices do not
require approval. No remote publication or external messages are implied.

## Constraints

Retain Minecraft 1.21.1, NeoForge 21.1.253, Iris 1.8.12, Sodium 0.6.13,
Photon v1.3b and optional DH 3.3.3 unless a compatibility fix is documented.
Iris requires Sodium here, so the minimal shader runtime still contains both;
"without performance mods" means without additional optional optimizers.
Keep explicit pinned pack patches and honest CPU/GPU/quality distinctions.
Do not claim Mac-only results establish full desktop shader compatibility.

## Milestones and acceptance

1. Automate repeated, balanced-order serial benchmarks with options/artifact
   fingerprints, warmup, median/tail CPU/GPU/frame metrics, and contamination
   detection. Compare mod-absent original, patched disabled, and active profiles;
   include simple existing-quality controls rather than inflating the baseline.
2. Improve measured Photon bottlenecks and safe geometry/DH integration.
   Preserve salient lighting; distance transitions and camera motion must be
   inspected. Geometry remains opt-in until automatic coverage is proven.
3. Run minimal and selected optional performance-mod stacks with exact pinned
   versions/hashes, actual loading and in-world checks. Separate stack gains from
   ShaderLOD's incremental gains. Include DH-present/absent paths.
4. Require repeatable useful gains: provisional target at least 15% median frame
   or GPU render reduction in an ordinary representative scene, plus a second
   workload without material regression. Record uncertainty, CPU/GPU limits and
   visual costs. Existing manual radius changes alone do not establish a novel
   shader benefit. Refine this engineering gate from evidence, transparently.
5. Research, pin and experimentally test other popular, licensable shaders now
   under the latest user authorization. Add explicit integration with its own disabled/original,
   active, runtime, transition and performance checks. No generic rewriting.

## Team and runtime ownership

Coordinator owns integration, build.gradle, goal/status/decisions, interactive
clients, serial GPU experiments and final evidence. Independent workers receive
bounded files, checks and stopping boundaries. No concurrent shader reloads or
benchmark clients. Preserve world saves and generated reports outside Git.

## Initial checkpoint

Previous commit: 218992b. Last evidence: forest combined candidate 124 to 143 FPS
and GPU 7.482 to 6.319 ms; premium cloud shader-only reductions 13–29% with large
variation. Terrain cap exposes existing DH tradeoff and needs warmed-scene
confirmation. These results motivate work but do not satisfy the new repeated,
ordinary-scene and multi-stack gate by themselves.

Next: bounded implementation/research workers, followed by integrated repeat
benchmarks. Live inspection clients from the prior goal must be identified and
closed only when the coordinator begins exclusive serial measurements.

## First implementation checkpoint

Four bounded workers completed: Photon water/atmosphere candidates, conservative
DH policy safeguards, optional optimizer pinning, and repeated benchmark tooling.
Second research pass documents blockers to automatic DH coverage and later
Photon candidates. Production source and benchmark source are now frozen for
serial experiments; no other agent owns a game or GPU.

- Water analytic normal removes repeated height/noise evaluations at medium/far
  distance; near/disabled/shadow use original path. Separate water bands 96/256/32.
- Cloud defaults now 128/1024/64; lighting protection and cached-sky-off remain.
- Conditional atmosphere evaluation preserves sky and options requiring it;
  compiler may already remove work, so benefit is unverified.
- Geometry acknowledgement invalidates on travel, world/pipeline/readiness,
  request/cap and DH-range changes; hot getter restores immediately as a setting.
  Mesh rebuild completion is separate. Explicit confirm/revoke commands and
  NeoForge config screen added. Runtime travel check remains pending.
- ModernFix 5.27.24, FerriteCore 7.0.3 and ImmediatelyFast 1.6.14 loaded and
  rendered in original-Photon/DH shoreline inspection. All artifacts pinned and
  hash checked. Culling stack and DH-absent run remain pending.
- Java build and 10 geometry tests pass. 20 Python tests pass, including nine
  benchmark tests, 11 shaderpack tests and 792 GLSL compilations. Actual candidate
  Iris rendering/performance still needs serial runs.

The first matrix `dev/logs/sustained-v2-main` rejected its first run because Iris
rewrote default option files. This was a guard failure, not a performance result.
Raw evidence is preserved. The corrected tool captures loaded Iris effective
options, verifies requested values and stable canonical maps, and retains raw
byte hashes without equating normalization with changed quality.

Proof profiles: `run-workloads/photon-proof-v2/{forest,cloudstorm,coastalwater}`,
original high / patched disabled / active; all terrain policy disabled. Cloud
scene is premium full resolution; forest and shoreline use default cloud buffer
resolution. Shoreline 240.5/68/680.5, yaw 180, pitch 3 was inspected in world.
Candidate archive SHA256 999eae1c4c3551e0f02d45ae06b517c87f966b87b7ca6a8e779a16566c6a003b.

Next: rerun semantic-guarded repeated matrix across minimal and optimized stacks,
then water, terrain lifecycle and optional-DH checks. No new shader integration
until the evidence gate is met. Original preview PIDs were already stopped when
this goal began; do not reuse stale process identifiers.

## Live repeated-matrix checkpoint

Source checkpoint: 3f594b2. Corrected matrix `dev/logs/sustained-v3-main` is
running under unified exec session 11350. Coordinator owns it; do not launch
another client or compile/change frozen production/shader code until it ends.
Runner console: `dev/logs/sustained-v3-main-runner.log`. Manifest and per-run
reports are inside its directory. There are 36 runs (3 blocks, 6 conditions,
2 stacks), 35-second warmup and 20-second capture. The matrix preserves failures
and performs paired analysis only after all repeats are complete.

Early results are mixed and are NOT acceptance evidence: first minimal forest
original/disabled/active were 121.1 / 118.7 / 93.6 FPS; optimized active was 121.7.
First minimal cloud original/disabled/active were 57.4 / 59.4 / 81.2; optimized
original/disabled/active were 58.8 / 52.7 / 62.8. Do not discard poor runs or
promote best ones. Stable loaded shader options, actual camera/radii, versions
and archive fingerprints are recorded; no pipeline fallback in accepted runs.

A source-only [regression audit](../PHOTON_REGRESSION_AUDIT.md) proposes atmosphere,
analytic-normal and distance-band isolation using preserved candidate archives.
It establishes no culprit. Next continuation: wait for matrix completion,
inspect paired distributions and screenshots, then run targeted isolation/water
rather than adding more optimizations blindly. Photon performance gate and new
shader support remain pending. Native goal stays active.

## Continuation checkpoint: input audit and isolation tools

The matrix session 11350 was revalidated live and progressed through its second
block. First minimal forest slowdown did not repeat (block 2 active 120.6 FPS
versus original 113.7). Keep all repeats; no source culprit is established.

New tested preparation tool `scripts/prepare_isolations.py` creates fresh
original/disabled/active roots for analytic versus structural packs, analytic
normal on/off and current versus older water bands. Six fixture tests passed.
No actual copies or clients were run during the exclusive matrix. See
`docs/ISOLATION_WORKLOADS.md`; run preparation only after matrix termination.

Source audit exposed an omitted temporal input: Iris smoothed wetness feeds
Photon cloud humidity/wind. Initial clear-to-rain copies and later already-rainy
saves may initialize differently despite recorded rain=1. Actual wetness was not
captured, so it is a plausible mechanism rather than demonstrated cause. Treat
this matrix as exploratory until a follow-up captures/settles relevant inputs.
Do not invent precision by freezing frameTimeCounter alone: cloud geometry uses
world_age based on world day/time, already frozen by the harness.

`docs/APPLE_FEATURE_BUDGET.md` identifies a narrow future driver probe: same
reduced profile, DH absent, environment and sky reflections enabled. Iris reserves
two composite units, leaving 14 dynamic sampler slots here; source estimates
suggest SSR can fit without DH but may exceed the budget with DH. This is not
runtime support yet. Source also confirms DH water SSR executes in composite1,
not dh_water's early-return Gbuffer path. Test rather than assume sampler counts.

Next after the live matrix: retain/analyze all repeats, add wetness telemetry and
settled-weather checks, inspect images, then run targeted water/atmosphere and
DH-absent SSR probes. Production/shader sources remain frozen while matrix runs.

## Matrix completion and next implementation

Session 11350 ended with exit 0. All 36 captures and paired analysis completed.
See docs/REPEATED_RESULTS.md for retained median/range limits. Median paired GPU
reductions: forest 5.83% minimal / 1.72% optimized; premium storm 25.91% / 23.43%.
The ordinary performance gate is unmet; cloud input wetness was not captured,
so cloud values remain exploratory. No shader expansion is authorized by a
claimed gate pass yet.

The benchmark worker now owns dev-only per-registration wetness telemetry, an
optional fixed-wetness diagnostic, screenshot preservation and isolation
preparer correction. Root owns build.gradle property mapping and clients. No
Minecraft client is live; wait for worker freeze before compiling/launching.
The real isolation preparer passed shoreline preparation without DH under
run-workloads/ssr-probe/coastalwater; common ENVIRONMENT_REFLECTIONS and
SKY_REFLECTIONS are enabled on high/off/active, normal/specular mapping and
galaxy explicitly disabled. This is static preparation, not a link/render pass.
Root's attempted forest isolation dry-run exposed non-Settings include guards
in Iris's full option map; preparer correction is pending with the worker.

Next: compile worker telemetry, launch original DH-absent shoreline SSR smoke
with short matched capture and inspect driver/errors/actual reflections. Then
measure fixed-wetness cloud controls and water/atmosphere isolations, plus
geometry travel/reload restoration before recommending any new default.

## Water specialization checkpoint

Wetness telemetry and screenshot preservation committed as 156fbce. Original
SSR without DH rendered and exported actual applied/raw zero with real clocks;
first tag failed only yaw normalization and remains excluded. Corrected SSR
pilot original/disabled/active: 139.87 / 139.33 / 113.05 FPS, GPU6.580 /6.540 /
8.135 ms. Finite-no-analytic pilot: original143.11/6.404, active133.99/6.887.
All remain exploratory single pairs. See REPEATED_RESULTS.md. No recommendation
or gate pass follows from them.

Shader worker restored original water functions/constant loops and introduced
literal-count cheap kernels; analytical approximation defaults off. 13
shaderpack tests and1017GLSL compilations plus actual-function numerical fixtures
passed. Main candidate with existing atmosphere guard SHAfe089ff2020168342309713a07082ebc0eddb233daec31ac0ee61c9a264ce8f8.
Clean no-atmosphere candidate SHA39fcc8132e958157e02a13ea2055ad6fdbaed7a92ca54133148d03b0b7667436,
archive build/photon-static-water-no-atmosphere.zip. Root preparation supports
both exact hashes and created run-workloads/ssr-static with analytic=false.

Live pilot session5789: high/off/active, minimal stack, no DH, fixedwetness0,
35s warmup/15s capture, tagssr-static-pilot. Root owns soleGPU; source ZIPs are
frozen. Wait for terminal handle before any other launch/compile/heavy preparation.
After it ends, inspect baseline/off/active and images; repeat promising results,
or isolate cloud/SSR and specialize further rather than proclaiming a win.

Shadow-cache and pass-split source designs now exist. A shadow renderer HEAD
cancel alone is unsafe (targets cleared earlier; matrices regenerated elsewhere).
Sky-only pass split frees only one sampler and does not solve both DH+SSR
blockers. Both designs remain unimplemented research, with costs/quality gates.
Photon's own license text/attribution is retained under patches/photon; it is not
the mod's MIT license. Source checkpoint pending commit below; goal remains active.

## Static-water pilot result and current ownership

Session5789 ended with exit0; ssr-static-pilot complete. Single-pair original/
disabled/active were143.79 /139.33 /123.18FPS, GPU6.378 /6.597 /7.435ms, thread
CPU6.446 /6.604 /6.575ms. The static-loop restoration did not establish a gain.
It is not recommended on this evidence. All controlled wetness values remain
zero and screenshots are retained; do not attribute this loss solely to loops
or discard it. Source implementation committed as7d190c1.

Root launched no further game after that terminal result. Shader worker now
owns explicit compile-time effect-isolation switches for clouds, water, fog,
SSR and materials in pinned shader/helper/tests. Off paths should preprocess
back to original functions, not merely set a runtime loop count. No Java or
benchmark tool edits are assigned. Root waits for worker freeze before building
new runtime profiles; CPU static checks are permitted while no client is live.

Next continuation must revalidate the worker, integrate tested switches, build
a clean no-atmosphere archive, and run all-off/cloud-only/water-only/SSR-only
controls on the inspected shoreline with fixedwetness0. This determines which
active effect causes the measured overhead. Then optimize measured causes and
repeat real-world quality/performance checks. Shadow/pass-split designs remain
unimplemented; new shader support remains gated. No blocker requires user input.

## Effect isolation integrated and live

Previous turn classified as progress: actual SSR driver rendering, wetness
telemetry, corrected guards, retained negative pilots and constant-loop shader
implementation changed authoritative state and experiment selection. This turn
integrated explicit effect controls and the read-only shadow observer.

Clean effect archive SHA256 ab1b6d859829fe3e0925b69cdd3759369c44bc876cc97de77d9878ed27b8658d,
build/photon-effects-no-atmosphere.zip. Main with atmosphere guard SHA256
44fe27e24b51c3fbffd37fb2ba844f9a2420b507f1c9d424fa6d2174c143a16d.
Five pack compile-time effect switches select original full include code when
off; water-off emits no cheap kernels. They are not live mod config fields.
15 shaderpack tests and1248GLSLcompilations passed; no gain inferred.

Effect tooling tests: four preparation tests,14proof tests,7isolation tests.
Root prepared nine independent hashed copies at
run-workloads/effects-ssr-shoreline/coastalwater, fixedwetness0/noDH/minimal,
matching observed SSR-enabled upstream settings and camera. Source maps,
license/hash guards, world locks and failure preservation remain enabled.

Shadow observer only reads existing expressions/matrix supplier returns; never
skips/clears/changes rendering. configurePhotonFeatures/beginMeasurement/snapshot
wired intoWorkloadSession; two mixins registered. Standalone policy and dev
compilation passed. ExactReusableFrames stays0 under unknown metadata; observation
is not permission to reuse maps. Root mapped -PshadowObserve=true, defaultfalse.
First original effect capture ran with hooks registered but observationfalse;
actual enabled observer capture/overhead still pending separately.

LIVE session43582, tag effects-shoreline-r1, console
 dev/logs/effects-shoreline-r1-runner.log. Nine balanced serial controls:
original/high, global-disabled, active alloff, five singles and allactive.
35swarm/15scapture, realclock, fixedwetness0; observationfalse. First original
completed139.84FPS; no conclusion fromit. ROOT owns onlyclient/GPU. Freeze all
used code/archive/configinputs untilsessionterminal; no other launch/heavyCPUtest.

After completion: analyzeallcomparisons and rerunagainstwater-alloff; inspect
images/cloud speckle, choose targetedcandidates and repeated checks. Run a
separate observerdiagnostic afterexclusive sweep. Photon ordinary gate and new
shader target remain pending, with no user-dependent blocker.

## End-of-turn authoritative checkpoint

Effect9sweep43582, sourceforensics84385, sparse6sweep75927, originalCPU diagnostic
3757/86174 and culling12matrix5164 all terminal. No owned Minecraft client is
running at this checkpoint. Raw reports/images/source/JFR are ignored under
 dev/logs. See REPEATED_RESULTS.md, EFFECT_SOURCE_FORENSICS.md and
 RENDER_CPU_FORENSICS.md. Original itself varied145→75FPS; no causal claim from
query/compiled-source changes. The startupJFR7.1MB record is complete; its
40-second window is a good144FPS baseline, not a captured slow regime.

JFR tooling tests8passed. Source-only hotpath audit identified redundant negative
vertex math in boolean shadow frustum. Production helper/controller/base and
reversedmixins implemented, config/tick cache wired. EightJUnittests(50ksets×two
FMApolicies +edges) pass. Firstsmoke failed inherited@Shadowlookup; repaired
throughbasehelpers, newsmoke91889failed/62894success recordsretained. Success
rootcontrolleractive=true, originalarchive selected, fallback=false. Paired
12matrixcompleted; minimalCPUregressed1.7–3.4%, optimizedgainvariable. Rendering
optimizeShadowCulling defaultfalse now; no actualaccepted15%ordinarywin.

Source integration includes default-off shadowinputobserver (runtime enabled
capture stillpending), dev wetnessexports and exacteffecttoggles. Currentcode
awaitsfinalcommit below. Root must not alter scope or mark goal complete: Photon
meaningful repeatableordinarygains and secondpopularshader remainunmet.

NEXT ACTION: checkpoint committedwork, then source-grounded cloudonly candidate
from CLOUD_TRANSITION_DESIGN afterreview (independenttiernoise orcontinuousbudget
control), measuredwith original/alloff/cloudonly in default shore+premium storm,
fixedwetness, balanced repeats andNoQuery crosscheck. Separate enabledobserver
runtime, geometrytravel/reload checks, and entitycullingstack remainpending.
No genuine user-dependent blocker; nativegoal staysactive. Verify authoritative
process handles before restarting any interrupted work; terminaljobsabove do not
needrestarting merelytoobserveunchanged state.


## Cloud candidates and benchmark-state checkpoint

The cloud-only candidate preserves original mode 0 and adds experimental mode 1
(separate tier noise) and mode 2 (smooth bounded integer budgets), with actual
layer/viewer overhead protection for new modes. Source checks and 1,446 GLSL
compilations passed; no driver or performance claim follows from them. Clean
archive SHA256 8fbaa117d87b4f729ea64ecce4d86390cdc56d03cd3b5a02ec2be9ffe2213235,
`build/photon-cloud-transition-no-atmosphere.zip`. The main source retains the
older atmosphere experiment; the clean archive explicitly excludes it.

Seven fresh matched shoreline profiles prepared under
`run-workloads/cloud-shore-default/coastalwater`, from stopped measured original
SSR shoreline, cloud upscale 4, DH absent. Cloud-current, independent,
independent-unprotected and smooth arms keep the other four effect flags off,
lighting protected, cached sky/analytic water off. Original and all-off/global-off
controls retain common upstream settings and counts. Source presets unchanged.
Real driver compilation, normal-motion quality and performance remain pending.

New report-only window telemetry reads focus/iconified/visible once per existing
workload tick and records sizes, pacing, counts and transitions. Serial-only
window placement is now identical for all arms; normal comparison previews still
tile left/right. No focus or pacing is changed. Strict benchmark guards require
actual culling activity/config and exact cloud mode/effect/protection recipes.
34 preparation/guard fixtures and Java build/compileDevClientJava passed; the
window accumulator standalone fixture passed. Shader worker, profile worker and
telemetry/guard workers are frozen. Root owns all clients/GPU.

Visibility audit found coupled forced shadow dirtying and mutable per-region
membership arrays despite separate outer lists. Upstream already caches when
clean. No traversal skip was added. Separate shadow-owned inner membership lists
are only a prerequisite; dirty epochs, tasks, section stamps and animation
consumers must be modeled before exact reuse. See VISIBILITY_REUSE_AUDIT.md.

Next: root serial seven-arm shoreline driver pilot with fixed wetness 0, followed
by normal-resolution rainy controls and balanced repeats of promising candidates.
Record actual window telemetry and keep every negative result. No ordinary gate
pass or second shader support yet; native goal remains active.


## Live cloud driver pilot

Source checkpoint edffbef. Root launched sole serial runner session45404,
`dev/logs/cloud-shore-default-r1-runner.log`, tagcloud-shore-default-r1. Seven
shoreline controls, minimal stack/DH absent, cloud upscale4, fixedwetness0,
35swarmup/15scapture, sparse GPU interval16. Do not run a second client, modify
frozen main production/dev/shader source or profiles, or run heavy CPU tests
until the session terminates. An initial CLI invocation rejected unsupported
--cases before creating a matrix/client; its console is retained separately.

Workerperformance_stack has isolated worktree
`/Users/pippo/.codex/worktrees/visibility-lists/shaderlod`, basededffbef. Its
bounded task is a default-off per-region shadow membership-list separation
prerequisite without skipping traversal, marks or task/animation consumers.
No builds/heavytests/GPU permitted during live root capture. Main source is
unaffected; integrate only after review and runtime correctness evidence.

Nextcontinuation: poll actualsession45404 for terminal/newoutput, inspect fresh
manifest/reports/windowobservations/images, retainnegativecomparisons, then
choose repeats/raincloudscene. If a realguard/compilefailure appears, preserve
it and repair causally before retry. No Photon gate pass; native goal active.


## Cloud driver pilot complete

Session45404 terminalexit0/all7accepted; no root clientlive. Full result and
actual window-state evidence appended to REPEATED_RESULTS.md. Original145.23FPS,
smooth143.70; alloff114.74 and protectedindependent116.55. Both slow arms remained
focused/visible with unchanged sampled pacing/sizes. These are single runs,
not gain or cause proof. New modes compiled/rendered successfully; no ordinary
performancegatepass. Root did not enable new modes by default.

Workerperformance_stack has an authorized CPU/build window in isolated visibility
worktree only, after root GPUcapture ended. No games permitted. Prototype separates
inner membership lists and preserves sprite activation; it never skips traversal.
Root reviewed static getter/storage/sprite-generation diff; compile/tests and
actual runtime generation parity still pending. Workerbenchmark_repeat owns only
IN_PROCESS_CONTROL_DESIGN.md for a same-JVM/alloff globalbridge control; no Java
changes assigned yet. Main source frozen/clean except this documentation.

Nextcontinuation: collect workercompile/test and design, integrate reviewed
prerequisite only into a separate diagnostic profile, then verify enabled/disabled
sprite/list ownership and camera/world/reload without claiming speedup. Implement
bounded in-process controls with current fingerprints/window/wetness and CPU-only
variant before another broad cloud matrix. Preserve bad alloff evidence and seek
actual native wait/source effect cause. Nativegoal active; no userblocker.


## Ownership diagnostics and in-process controls integrated

Previous goal turn was progress: new cloud implementation/drivercaptures changed
source and authoritative evidence. Current source38a657d/bdc8222 integrates default-
off per-region shadow-list ownership, dev phase snapshots, shared WorkloadInputs,
strict four-phase in-process alloff controls, and protected-lighting tier work
avoidance. 24 JUnit+34Pythonfixtures and integrated build/devcompile passed.
Shader19tests/1512GLSLcompilations passed. New clean lighting archive SHA256
b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c.
Only d4/translucent call sites plus metadata differ from8fba; helpers/defaults/effects
unchanged. Prepared seven fresh profiles at lighting-tier-shore-default.

Original-Photon ownership enabled49846 and disabled/baseline44245 allterminalexit0,
no root clientlive.8pairs/0errorsperarm. Enabled64sampled shadowobjects distinctand
unchanged aftermain; controls64borrowedfingerprints changed, outergeneration/count
hashes changed8/8controls vs0/8enabled. Sprite models match current hypothesis,
not actual sprite activations. Root staticimages show no gross near shadow/geometry
loss. Details/limits in VISIBILITY_LIST_SEPARATION.md; no skip or gain claim.

Workerperformance_stack has CPUwindow for isolated coherence worktree
`/Users/pippo/.codex/worktrees/frustum-coherence/shaderlod`, base5a06ccd. New default-
off candidate proves containing coarse boxes with original positive-plane arithmetic
then reuses accepts within the current frustum/preparation. Conservative fallback,
64slots, invalidation and distance constraints; nine tests await execution. Root
reviewed source bounds but actualMixin/runtime and gain unverified. No games byworker.

Next: collect coherence build/fuzz proof, then root same-JVM OFF/ON/OFF/ON alloff
old8fba control with HUDhidden/fixedwetness0 and stableworld/pipeline; reverse and
query-free checks, then compare new lighting-tier candidate. Do not claim absolute
FPSgain versus old visible-HUD captures. After correctness review integrate
coherence into separate runtimecontrol and testactualvisibility againstoriginal
before performance. No ordinary Photon gate or second shader yet. Nativegoalactive.


## In-process controls and spatial-coherence oracle complete

Mainbbb70d5 integrates frame-local coherence/defaultoff + diagnosticoracle and
analyzer. All33JUnit,41smallPythonfixtures, build/devcompile passed. SameJVMold36243
andnew6158 controls terminalcomplete; allphaseimages/UUIDs andsemanticIRissettings
validated. OldOFF/ON meanFPS virtuallyequal151.76; newallphases~154. No reproducible
large enablepenalty or meaningfulordinarygain. Bothonecorrelatedsession perarchive,
hiddenHUD; earlierbadcross-JVMregime remainsunexplained. See IN_PROCESS_RESULTS.md.

Coherenceoracle38529 terminalexit0,5.157Mbasequeries/0mismatches, active true and
originalPhoton/fallbackfalse. Actualcacheaccept5.34% only; timingsnotvalid withoracle
on. Reversed0queries unverified. DetailsinSHADOW_FRUSTUM_COHERENCE.md. No rootgame
live atthischeckpoint; no performanceacceptance or secondshader yet.

CURRENTTEAM: coverage_policy owns gatedfirst-frame/frustum raw-float querytrace
andexplicitcold contextcapture in helper/diagnostics/devOracle, APIsunchanged.
performance_stack owns scripts/replay_frustum_coherence.py, tests/test_frustum_replay.py,
FRUSTUM_REPLAY.md; explicitsource-anchor variants grids64/32/16 +extentgrid+32,
slots64/256/512, exactoutputchecks andseparateinstrumentedcounts/optionalnanotiming
processes. No games/builds/heavyreplay untilrootCPUwindow. Rootownsclients/source
integration. Shader/benchmark workersfrozen; analysis/fixturechecks complete.

NEXT: revalidateworkerfreeze, compile trace and rootcapture10s again GPUoff; verify
sameowner/context/generation/complete trace, preserveexactsourcehelperhash, run
CPUreplay againstactualreferenceoutputs andselectcandidate. Thenrealoracle with
movingcamera/sun andruntimeperformancecontrols (oracleOFF) underbothstacks. Never
advertise offlineop/nanocounts asGPU/FPSgain. Keepfulloriginalgoalactive/unshrunk.


## User check-in and comparison inspection

User returned and requested status plusfloatingwindows. Root openedlatest protected
PhotonB15 vsphysicallyabsentmod/originalPhoton, forestcamera,24vanilla/DH128,minimal,
allshaderLOD effectsactive, preservedlighting,cachedsky/geometry/frustumexperiments
off. Handles5769/52317 launched andrenderedleftx8/rightx760. Both subsequently
stoppednormally (logs23:42:03/07); no rootclientlive. Shared-client35%GPUdifference
is notserialacceptance; savedCPUthread9.36/9.52ms wasmostlyunchanged. Capturesalso
have differenttimes, so donottreattheirFPS/GPUratioas anisolatedpairedwin.

User clarified general-purpose configurable shaderLOD asproductgoal. Core remains
commonIrisprotocol/effectconfig/explicitpackadapters; Photonproof precedesexpansion.
Sun cadence wasinspected, notchanged; shadowmaprefreshunchanged. Rendererexperiments
secondary. Nativefullobjectiveunchanged; rootwillreport concretefeaturelist and
limits. Shadowkeyprobe remainsisolatedsource-only; no GPUworkerlaunches.

Preinspectioncoherencecontrol50905 endedexit0/complete. OFF151.76/151.25FPS,
ON146.89/152.07; no consistentgain. Analysis acceptedactualimages/pins under
 dev/logs/coherence-inprocess-r1-analysis.json. Realtrace3594queriescomplete and
9replayvariantsexactbutCPUmodelallslower1.36–1.76x. No promotion; defaultoff.
Nexttechnicalwork prioritizesactualkey/producercorrectness onlyasboundedsecondary
research plusreusableconfiguration/adapterdesign without claimingunsupportedpack
support or droppingtheperformancegate. No userblocker; nativegoalactive.


## Fresh performance-chat takeover: 2026-10-05

Native goal active in chat `01a10adc-41ba-76a1-a5eb-1f27c6be8fef`, with no token
budget. It executes this existing shared project goal; previous unfinished work
has not been marked complete. Handoff baseline `1000977`; pre-existing unstaged
SHADOW_VISIBILITY_CACHE_DESIGN.md belongs to its original owner.

Original chat owns main Java configuration/uniform transport, shared shader
helpers, existing Photon protocol patches and related tests. Fresh chat owns
performance/web research, optional stack benchmarks, exploratory pack adapters,
and all Minecraft/GPU launches. No client is live at takeover. Protocol-owned
files require a concrete handoff before edits; bounded workers may inspect them
read-only. No publishing, remote push, remote host or external messaging.

First bounded work: primary-source optimization research, pinned/licensed shader
shortlist, then the pending EntityCulling stack runtime validation and matched
minimal/optimized/culling controls. Research and exploratory pack tests can begin
before the ordinary Photon gain gate; they do not establish accepted support.
CPU acceptance caches remain off. Preserve exact locks, failures, effective
options, screenshots and separate CPU/GPU/quality evidence.


### Live stack-only matrix

Fresh GPU owner launched exec session `34175`, console
`dev/logs/takeover-stack-forest-r1-runner.log`, manifest directory
`dev/logs/takeover-stack-forest-r1`. Nine original-Photon forest runs, three blocks
across minimal/optimized/optimized-culling, DH present, 24 detailed chunks + DH128,
45-second warmup/25-second capture, GPU query 1/16, fixed shader wetness 0.
Profile root `run-workloads/stack-takeover`; original mod-absent task
`runClientLodOff`. These estimate optional stack effects only, not ShaderLOD gain.
Exact optional artifact versions/hashes are now validated from actual loaded
reports; all four optimizer jars verified against existing lock before launch.
18 runner tests passed. No result is accepted until terminal completion and
matched input/window analysis. Do not launch another game or heavy CPU tests.

Workers: optimization_research owns PERFORMANCE_METHOD_RESEARCH.md; pack_research
owns OTHER_SHADER_RESEARCH.md and candidate lock only; stack_analysis owns new
stack analyzer/test/method document. Existing Photon source and main Java remain
protocol-owned. Root owns runner/tests and GPU. Source-only research may continue
during the matrix. Next: collect terminal reports and visual checks, then choose
an explicit additional-pack experiment from pinned/licensed upstream evidence.


### Independent shader experiment ownership

Research delivered PERFORMANCE_METHOD_RESEARCH.md with primary sources and
five bounded hypotheses. Root assigned photon_cloud_guard only new
`patches/photon-experiments/cloud-consumer-guard.json`, standalone experiment
builder/tests/documentation. It operates on immutable original Photon bytes,
not protocol-owned existing patches/helpers. The hypothesis skips unconsumed
composite1 cloud-color work; it is a structural optimization, not itself LOD.

makeup_adapter owns only new `patches/makeup/`, `shaderpacks/makeup/`, a separate
builder/tests and MAKEUP_ADAPTER_EXPERIMENT.md. MakeUp9.5g is the provisional
first LGPL explicit adapter; pinned/licensed baseline research is finishing.
The first experiment reduces distant vanilla-water SSR work while preserving
near/original and offscreen fallback paths. Shader options are experimental; no
connection to Java/common effect protocol is claimed. No shader driver compile
or render of either new experiment has happened. Both workers may run only
lightweight source fixtures during the exclusive stack matrix.


### Stack provenance qualification in progress

First strict stack comparison identified different `fabric_api_base` raw hashes
selected from Iris/Sodium nested jars despite identical version. This is retained
as a strict rejection, not discarded. Source inspection found all17 classes and
all resources identical except the NeoForge lower dependency bound
([21.1.169,) versus [21.1.115,)); both accept pinned21.1.253. A separate exact-hash
equivalence registry/report is being prepared, with an explicit opt-in qualified
analyzer mode. This is not same-version or arbitrary nested-jar equivalence.
Final report must retain raw identities and state this qualification.


## Stack completion and first other-pack rendering

Stack exec34175 terminal exit0/all9 complete. STACK_FOREST_RESULTS.md records
qualified block comparisons: optimized frame reductions+3.18/−10.48/+0.83%;
optimized-culling vsminimal−15.02/−1.57/+0.91%. No consistent gain or default
change. Strict API Base hash rejection retained; qualified exact-hash exception
proved classes/resources equal apart from a satisfied loader lower bound.
Current source/tests:19 runner,24 stack,10 Photon guard,7 MakeUp fixtures pass.
Production/common Java and existing Photon patch/helper files untouched.

Unmodified MakeUp9.5g smoke exec88400 terminal exit0/complete: selected original
archive hash0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8,
Iris in-world rendering/fallbackfalse, native medium + reflection slider2 / AA2,
noDH, inspected shoreline240.5/68/680.5 yaw−180 pitch3. Root inspected PNG with
visible bank/water reflections; original single capture150.85FPS is smoke
evidence only, not a speedup or general support. Rawdev/logs/makeup-original-smoke-r1.

Dev overlay now uses actual pack name, pack-neutral baseline labels/program
names, and serial/shared measurement distinction. This changed dev bytecode
after stack matrix. Future runner stores compiled local class/resource hashes
after build and rejects any changes before/after each launch. No pooling old
and new dev bytes from version strings.

LIVE exec45311: MakeUp original/off/on/native lower
pilot, four serial launches at run-workloads/makeup-takeover, minimal/noDH,35s
warmup15s capture, GPU1/16, dynamic shader wetness (clear weather). Directory
dev/logs/makeup-adapter-pilot-r2; root owns soleGPU. No heavy tests or other
client until terminal. Original/off/on quality and actual shader-option/driver
checks precede repeated performance acceptance. Photon guard archives are built
and three forest profiles prepared, but neither derivative rendered yet.


Pilotr1 did not launch: Iris deleted original MakeUp's all-default option file
after smoke, and the old preparer assumed every option file persisted. Retained
runnerlogr1. Runner now treats missing file as original source defaults and
validates it against the experiment preparation's intended controls; actual
loaded Iris options remain authoritative. Correctedr2 launched in45311. No
shader failure or performance result was caused by this input guard.


### MakeUp pilot terminal; Photon guard live

MakeUp pilot45311 exit0/allfourcomplete. Original/off/lower/on150.63/152.04/
150.85/119.85FPS; GPU6.183/6.325/6.157/7.749ms. Real active rendering succeeded
but this singleblock regressed, causeunproven. MAKEUP_RUNTIME_RESULTS.md retains
negative evidence, source/options-only control distinction and quality limits.
Defaultoff; no support/performance promotion. Makeupworker now owns only new
regressionprobe doc and optional separate builder/probe manifest/tests; existing
adapter files frozen. No compilers/clients byworker.

LIVE exec70093 Photon cloud-consumer forest matrix:original/disabled/enabled
threeblocks (9 serial runs), minimal/DHpresent,35swarm20scapture, GPU1/16,
fixedwetness0. Profile rootrun-workloads/photon-cloud-consumer-takeover; raw
dev/logs/photon-cloud-consumer-forest-r1 and adjacent runnerlog. Compiledlocal
inputs frozen. Root owns allGPU; no heavy builds/clients untilterminal. This
is exact consumer dispatch, not a distanceLOD gain byitself. Afterterminal,
inspect images and qualified repeats before any cleanup/default change.


## Photon guard complete; full-quality MakeUp probes live

Photon guard70093 terminalexit0/all9complete. Enabled/off medianframe+0.37%,
GPUmean+1.89%; enabled/original+0.68%frame,−0.13%GPU. No meaningfulordinary
gain or defaultpromotion. PHOTON_CLOUD_CONSUMER_RESULTS.md records bothbaselines
and quality/coverage limitations. Near/far surface lighting/source budgets
unchanged; fixedforest PNGs show no grossloss, not fullmotion/cloud-frontproof.

New dev benchmarkWindowScale property is owned only byfreshGPUchat: narrow
build.gradle run-systemproperty mapping, ComparisonClient window sizing and
WorkloadSession propertyexport. Scale2 doubles the former comparisonwindow's
logical dimensions when it fits the real monitor workarea; actual framebuffer
size remains authoritative. It creates a separate native-size resolution
experiment, without adding effects or reducing baseline quality. Mainconfig,
uniformtransport and existing Photon patches/helpers untouched.

LIVE exec85787 MakeUp full-reflection probes, five serialarmsonce: original,
derivativeoff, duplicateimplicit, duplicateexplicitLOD0, classifier-full.
Minimal/noDH,35swarm20scapture,GPU1/16, scale1, clear/dynamicwetness. Rootowns
soleGPU; rawdev/logs/makeup-full-reflection-probes-r1 and adjacent runnerlog.
Each diagnostic retains complete original reflectionwork/quality; compiler
may remove equal classifierbranches. No causalclaim before driver/repeatchecks.
Built hashes and rawsources in corresponding manifests/reports. Existing
adapter/helper source frozen; new probes use distinctarchives. Next after
terminal: inspect pairedsource/quality/results, repeated/reloadcontrols, and
native-size Photon original/disabled/active pilot at held quality/radii.


### Full-reflection terminal; native-window Photon pilot live

MakeUpprobes85787 exit0/all5complete. Original/off/classifier-full/duplicate-
implicit/duplicate-explicit151.78/154.02/154.00/148.05/124.48FPS. Directexplicit
regressed; equivalentclassifier-shapedexplicit didnot. Retainall; causeunproven.
MAKEUP_RUNTIME_RESULTS.md updates actualdriver evidence.

LIVE exec92027 Photon original/disabled/active native-window pilot, three serial
launchesonce, minimal/DHpresent,40swarm20scapture,GPU1/16,fixedwetness0, requested
windowScale2. Actualdimensions/windowpacing authoritative. Profile root
run-workloads/stack-takeover, rawdev/logs/photon-native-window-pilot-r1. Same
original/upstreamquality and protected lighting/radius24/DH128 as scale1;
active archiveB15. No quality inflation or cross-resolution gain comparison.

Cameraquality worker owns source outsideGradle at
experiments/camera-quality/CameraQualitySession.java and CAMERA_QUALITY_HARNESS.md.
It mustnot launch/compile or touch currentsource sets. Root integrates onlyafter
review and currentGPUterminal; smooth owned-player travel and PNGsequence are
unmeasured quality evidence, never speedup samples. Mainprotocol stilloriginal
chat owned; freshroot build.gradle change only benchmarkwindow propertymapping.
