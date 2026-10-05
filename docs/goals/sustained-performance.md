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


Source/evidence checkpoint committedfc94094. No push/publication. Unrelated
SHADOW_VISIBILITY_CACHE_DESIGN.md remainsunstaged underoriginalownership.
Nativepilot92027 stilllive after firstoriginalcapture: actual2976×1672,
61.49FPS, GPUmean15.090ms, CPUthread9.084ms/wall16.239ms. This is a separate
moreGPUlimited resolution arm; not performanceacceptance and not compared to
scale1 for aLODgain. Continue same-resolution off/active before any inference.


### Native pilot terminal and next action

92027 exit0/all3complete; actual2976×1672. Original/off/active61.49/61.19/49.96FPS,
GPU15.090/15.211/19.146ms, CPUthread9.084/9.163/9.261ms. ActiveGPU regressed while
thread time nearlyheld. PHOTON_NATIVE_RESOLUTION.md retains failure and exact
limits. No gate/defaultpromotion. Nextsafeaction is native-resolution effect
isolation original/off/alloff/cloudonly/wateronly/fogonly/ssr/material, followed
by repeat/sourceforensics on suspectedcause. Do not presume dynamicloops are
responsible, or add a sourcefix before this qualification. No client is live
at this terminal checkpoint; root retains soleGPUlease. Cameraquality source
is outsideGradle and underworker review; integrate only after freeze/review.


LIVE exec26695 native Photon effectisolation: same B15archivehash and upstream
settings, nine serialarmsoneblock (original/globaloff/alloff/fiveisolatedeffects/
allactive), minimal/DHpresent, nativewindowScale2,40swarm20scapture,GPU1/16,
fixedwetness0. Rawdev/logs/photon-native-effects-r1, prepared source copies at
run-workloads/native-effect-isolation. Runner/classes frozen; nootherclient/
heavybuild. Rootownssource/evidence; effectpreparer nowselectsexact named frozen
B15via--effect-archive lighting-tier-no-atmosphere, preservinghashguards.
Foureffectfixturespassed; no source/shaderalgorithm change.
Cameraquality worker remainsoutsideclasspath pendingreview.


## Native sweep terminal; within-JVM effects repeats

26695exit0/allnine; source sweep no proven culprit. Same-JVM effects50296
exit0/complete accepted strictanalysis: OFF/ON/OFF/ON60.17/65.15/61.41/64.90FPS;
GPU15.580/14.177/15.318/14.267ms (9.00/6.86% reductions). Retained butqualified
for inherited automatic screenshot withinphase2, corrected before cleanrepeat.
PHOTON_NATIVE_RESOLUTION.md preserves mixedcross-JVM and positivecorrelateddata;
no15%gate or qualitypromotion.

Camera+effects mode build52685 passed; productionJar excludes all devclasses.
Camera standalone pinned21.1.253 compilealso passed after correcting missing
classpath input in its first compilercommand; realmotion stillunverified.
Camera source integrated onlyin devClient; mainprotocol untouched.

LIVE reverse effects exec1395; log
dev/logs/photon-native-effects-inprocess-reverse-r2.log. Samepack/world/pipeline
and immutablefiveflags; ON/OFF/ON/OFF, noautomatic shots, native/minimal/DH,
40swarm10ssettle20scapture,GPU1/16,fixedwetness0. Rootowns soleGPU. Next terminal
analyze/preserve, forwardcleanrepeat, then actualCameraQuality smoke/sequence.


Native camera/effects integration source nowcompiles. Mainconfig/transport and
existing shaderhelpers/patches remain protocol-owned and untouched. Root's
camera code is transferred into devClient and hashes actual loaded jars/class
directory inputs, with world/pipeline reference guards. Only unmeasuredmotion
quality will use it; first runtime smoke remains next after cleanphasecontrols.


Clean reverse1395 terminalexit0/strictanalysisaccepted: ON/OFF/ON/OFF
66.46/60.84/65.40/61.68FPS; GPU13.789/15.566/13.981/14.873ms.
AdjacentGPUreductions11.42/5.99%, frame8.45/5.69%. Noautomaticlandscape shots.
Report/imagehashes and exactloadedclass/jar/config/options checked; raw
dev/logs/photon-native-effects-inprocess-reverse-r2-benchmark.json plusanalysis.
This supports a native-resolution effect signal but not15%/second-workload or
independentrunacceptance. Keepcross-JVM lossesandfirstqualifiedcontrolretained.

Cleanforward exec62833 terminalexit0; logdev/logs/photon-native-effects-inprocess-forward-r3.log,
sameimmutablepack/profile/world andOFF/ON/OFF/ON. No sourcebuild/edit used by
client untilterminal. Nextpreserve/analyze thenCameraQuality originalsmoke2FPS/
20secondmotion asunmeasuredquality; sourceJSON alreadyprepared at
run-workloads/makeup-takeover/shoreline-motion-smoke.json.


Cleanforward62833 strictanalysis/imagesaccepted: OFF/ON/OFF/ON61.76/66.48/
61.83/66.29FPS; GPU14.988/13.753/14.907/13.714ms. AdjacentGPU8.24/8.01%,
frame7.11/6.72%. Two clean correlatedsessions support effectsignal, not15%gate.

CameraOriginal12937 exit0/all41imagescomplete; exactactualtrajectory/hashes
verified, noHUD/perfgain claim. RootroutewrongdirectionintoTerrain: see
CAMERA_QUALITY_HARNESS.md. LIVE activecamerasmoke exec72018 uses sameimmutable
r1 path (preservecomparison), logdev/logs/makeup-camera-active-smoke-r1.log.
NoGPUtiming/performanceclaims. Correctedr2waterpath separatelyprepared; do not
mutateactive requestfile. Afterterminal runmatched original/off/on corrected
path, inspect quality and prove shaderdistance behavior before promotion.


### Existing-core adapter exploration

Read-only threadstatus confirmed original protocol owner is idle afterhandoff,
with files stillreserved; no message sent/no ownership taken. Main transport
CommonUniformsMixin unconditionally registers existing protocol2 scalaruniforms.
Fresh MakeUp worker now owns only new isolated bridgeprobe builder/manifest/
sourcefixtures/doc/optionaloverlay, consuming those exact corefields without
editing main or shared helpers. Defaultoff, explicitshaderopt-in, packfallback
for absent/unknownprotocol, originalglobal-off path, radial near/far mapping and
separate water diagnosticcolorcue. This is exploratory root-owned adapter work,
not the owner's future per-effect transport. Realconnection/support/performance
remainunverified untilroot shadercompile/render/config controls.

Corrected originalwater cameramotion exec47470 terminalexit0/all41, log
dev/logs/makeup-camera-original-water-r2.log; same immutable20s/2FPS request
z680.5→640.5. Firstactivewrong-route72018 exited0/all41imagescomplete; retained
asunmeasuredharness smoke. After correctedoriginal inspectroute then off/on.


Corrected originalr2 UUID56ff265a-5895-46db-9b3a-0c768de1f6d0 complete41/41.
RootfinalPNG confirms valid open shore/water route, not inside terrain.
Next active sameimmutable request launch trackedbyactualexec handle; log
dev/logs/makeup-camera-active-water-r2.log. Qualityonly, GPUtimingfalse.
Existing-core bridgeprobe source worker consumes onlyexact protocol2 globals
without main/shared edits and fallsbackonunknownversions; raw uniforms are
transported unconditionally in currentCommonUniformsMixin. Actual newadapter
config connection remains pending driver/diagnostic tests.


Corrected activewater65445 exit0/complete41PNGcallbacks/hashes, UUID
d5e62389-8f04-40cd-9c91-d0e77d25809b. RootfinalPNG retainsnear bank/water detail;
farSSRloss expected, no fulltemporal/glint acceptance at2FPS. Correctedoriginal
UUID56ff265a-5895-46db-9b3a-0c768de1f6d0 exactsame20s trajectory andrequest.
Original sampledMP4 encodedforreview underdev/logs; no performanceinference.
Disabledwater source launch handle10992 has log
dev/logs/makeup-camera-disabled-water-r2.log; authoritativeterminal check is
required beforeanynewclient. Bridgeprobe worker stillownsnewfilesonly.
Nextcontinuation: collectdisabled qualitycomplete/hash check, inspectmatched
near/farframes and stronger10FPStemporal/underwater/glint cases; assemble/test
newcorebridgeprobe exactsource and diagnostic actualmod controls; retainnative
cleanforward/reverse8%effects signal withoutclaiming15%or secondworkload.


Disabled10992 exited0/all41hashes verified; UUID reported in rootcollection.
All corrected original/off/on2FPS sequences complete. No game is live at this
terminalcheckpoint. Root ownsGPU; bridgeworker source stillfinishing. Compile/
assemble/review its standalone probe before diagnostic actualuniform tests.
Source/evidence commits mustexcludeoriginalowner's unstaged
SHADOW_VISIBILITY_CACHE_DESIGN.md and allignoredrawcaptures/worlds.


Checkpoint7945a3c commits verifiednative controls and devCameraQuality capture;
fc94094 retains prior experiments/evidence. No pushes/publication. Main/shared
protocol files untouched. Corrected original/off/on2FPSmotion allcomplete41/41
PNGcallbacks/hashes and actualtrajectorymetadata. High-rate qualitypending.

Isolatedcorebridge worker completed8sourcefixtures; rootassembled exactnew
MakeUpCoreBridge2ProbeZIP and fourclonedstopped profiles at
run-workloads/makeup-core-bridge-probe/coastalwater. Diagnosticwatercue1,
coreoptin1, legacywaterswitch0, samemod-absent/disabled/enabled/changed8→16
versus96→160bands. This consumesexistingversion2globals without anymain edit.
Runtime/connection remainpendinguntilfourrendered cues inspected; noGPUtiming
and no performance inference from diagnostic earlyreturn. Nativecue runner
handle reported below; root retains exclusiveGPU.


## MakeUp existing-core transport rendered

35659 exit0/allfouraccepted; sourcecue absentmagenta/disabledyellow/enabled
96→160 green-to-blue/8→16 closerbluefade. RootallPNGs inspected andSHAchecked.
Samearchive/options; onlyphysicalmodpresence orloadedexistingglobal/near/far
changed. Actualexistingcoreuniformconsumption verified in newadapteronM3/Iris.
No avoidedwork/performance/support claim fromdiagnostic. Sourceandactualrecord
MAKEUP_CORE_BRIDGE_PROBE.md; helperdefaultoff andunknownversionsfallback.
No game currentlylive; rootsoleGPU. Next cue-off render/quality/GPU controls,
then meaningfulPhoton optimization/secondworkload and broaderadapterverification.
Native8%clean signal remainsqualified below15%goal; sharedfullobjectiveactive.


Checkpointb003e05 commits MakeUpexisting-core consumption source andrealcue
evidence. Next normalrenderingprofilesfreshlyprepared withcue0; oldcue1
rawprofiles/reports unmodified. Nativeproof runner launched selectedabsent/
disabled/enabled96→160/enabled8→16, minimal/noDH,35swarm20scapture,GPU1/16,
dynamicclearweather. ExactsamebridgeZIP andupstreamquality. Root mustinspect
normalreflections/disabledrestoration andpreserve single-pilotlimits. Raw
dev/logs/makeup-core-bridge-render-r1; exclusiveGPUoperatorroot.
Fullnativegoal remainsactive; no stage1/15%/supportedsecondpack completion.


LIVE session91556 verifiedbyexec handle afterlaunch, soleownedGPUclient via
makeup-core-bridge-render-r1 matrixrunner. Continue bypollingthis exact handle
or authoritative process/log/manifest; neverstart anotherclient whilelive.
Ifterminal, analyzeall4normalrendercaptures andimages, then cue-off moving
quality/repeatedGPU andDHcompatibility. RetainnegativeMakeUp/Photonserial
pilots, sourcecore-diagnosticproof, clean native8%signals, optionalstack
negative/qualification andhardwarelimits. No source/main ownershiptransfer
or externalmessage/push/publication. Pendingunstaged ownerfile
SHADOW_VISIBILITY_CACHE_DESIGN.md remainsuntouched.


## Cue-off MakeUp terminal; Photon cloud control live

91556 exit0/allfouraccepted cue-off normalrender. Absent/disabled/enabled/
changedbands GPU6.150/6.160/6.086/6.225ms, frame6.639/6.457/6.583/6.658ms.
Nearwater/bank imagery inspected; no useful singlepilotgain/qualitypromotion.
MAKEUP_CORE_BRIDGE_PROBE.md records old shaderonlyfailure too.

LIVE exec34162 Photon cloud-only native same-JVM effectscontrol: immutable
B15, rootprofile native-effect-isolation/forest/cloud-only, OFF/ON/OFF/ON,
40sinitial10ssettle20scapture,GPU1/16,fixedwetness0,minimal/DHpresent,
windowScale2/hiddenHUD. Logdev/logs/photon-native-cloud-inprocess-r1.log.
RootsoleGPU; no sourcecompile/change untilterminal. Nextanalyze exactarchive/
options/semantics/images then fog/water isolatedcontrols, repeatanyuseful
effect and secondworkload. Cloud-secondary hoist worker onlynewstandalone
sourceprobe files outsideexistingpatches; no archivebuild/compiler/client.


Cloud34162 terminalexit0/strictaccepted; nativecloud-onlyOFF/ON/OFF/ON
GPU14.702/14.038/14.926/14.054ms. Adjacent4.52/5.84%lowerGPU, stablethread.
LIVEfog exec69423 sameconditions/profileforest/fog-only, log
dev/logs/photon-native-fog-inprocess-r1.log. Sourcefrozen untilterminal.
Miniature sourceplanner owns MINIATURE_RUNTIME_PLAN.md only, preparingexact
original noDH smoke requirements; no adaptation/support claim fromsource.
Cloudprobe workerhoists exactratio onlycount!=original; records emptyray
setup/registerrisk and no physicalsavings claim. Actualprobeunbuilt.


## Isolations terminal; cloud-secondary runtime probe

Fog69423 exit0/strictaccepted: GPUOFF/ON/OFF/ON15.093/14.968/15.544/15.429ms,
adjacent0.83/0.74% reductions with mixedframeeffect. Water24468 exit0/
strictaccepted: GPU14.809/15.208/14.906/14.929ms (−2.70/−0.15%), frame~0.1%.
No waterforestgain and no positiveclaimforeverymodule. Source/effectresults
retained in PHOTON_NATIVE_RESOLUTION.md andexactrawsession files.

Secondaryextent probe nine sourcefixtures passed, including702float32
ray/outputchecks; rootassembled disabled/enabled privatearchives fromexactB15
afterGPUterminal. No source-shared edit. Three fresh cloud-only profiles
prepared at run-workloads/cloud-secondary-extent-probe/forest.
LIVEexec53149 baseline/probeoff/probeon once, native2976×1672/minimal/DH,
40swarm20scapture/GPU1/16/fixedwetness0; log
dev/logs/photon-cloud-secondary-pilot-r1-runner.log. No otherclient/build.
Nextactualcompile/render/negativeorpositive pilotinspection, then matched
repeats/quality/sourcecheck. Hoist changes no samples/jitter/density/math;
emptyray setup andregisterliveness risks remain. Sourcefixture≠GPUgain.
MiniatureRuntimePlan complete/pinned sourceonly; next originalnoDH compatibility
smoke before adapting. Commonmain/sharedprotocol stilloriginalowned.


Cloudsecondary53149 terminalexit0/all3accepted: B15/off/on GPU14.538/14.454/
18.998ms, FPS63.76/62.56/48.77. Math-equivalent enabledsource pilot regressed;
defaultoff/no adoption. PHOTON_CLOUD_SECONDARY_PROBE.md retainsfailure/limits.
LIVEexec33846 enabledarchive a405f364...inoneJVM globalOFF/ON/OFF/ON, same
cloud-only/native/minimal/DH/40sinitial10ssettle20scapture/GPU1/16/fixedwetness0.
Logdev/logs/photon-cloud-secondary-inprocess-r1.log. RootsoleGPU; no source/
archive/classchange untilterminal. Next analyzeexacthash/states/PNGs, then
matchedsource repeats ifqualified; otherwise retainnegative andmoveon.
Miniatureoriginalbaseline noDH smoke and MakeUpDH/reload/motion acceptance
remain meaningful independentnextwork. Sharedfullobjective/gates unchanged.


## Hoist diagnostic terminal; additional baseline smoke

33846 exit0/strictaccepted: compiledhoist source globalOFF/ON/OFF/ON GPU
14.846/14.091/15.116/14.259ms (5.09/5.67%reductions). SimilarB15cloud4.52/5.84%;
no demonstratedincrementalhoist benefit, serial18.998msfailure retained.
No sharedintegration/defaultchange. Separate newliteral-secondary sourceprobe
worker tests onlyeligiblechangedlighting2/ambient1 fromB15, preservingdensity/
ratio/ray/jitter andallother/disabledgenericpaths. Sourcecannotproveunrolling.

Miniatureoriginal2.19 freshnoDHprofilesprepared fromstoppedshoreline. Explicit
source-default outer10/stride1.6/binary4, nativebinary2lowercontrol only; exact
releaseSHA verified. Originalfirstdriver smoke launched byroot, raw
dev/logs/miniature-original-smoke-r1; rootsoleGPU. No adapter/support/gain
promoted andno DHclaim. Exactjob handle followsauthoritativeexecoutput.
Afterterminal inspect shadererrors/fallback/renderedreflections/options, preserve
failure ifany, then explicitboundedadapter research/testing. FullPhoton and
modgoals/gates remainactive/unshrunk. SourceUniforms mainowneruntouched.


## Resume checkpoint: sampler evidence and DH baseline

No game was live at resume; preparation handle92787 completed exit0. Miniature
original smoke99413 and MakeUp cue-off91556 are terminal, as are all prior
Photon timing controls. Root remains sole GPU owner.

Sampler diagnostic78902 completed exit0 with timing disabled. Preserved report
`dev/logs/cloud-sampler-state-diagnostic-r2.json` SHA256
`b72de09d2adc5b70ff3d6dac7a1d1ff50de19e04664ae146b158f70b1895cd67`
observed non-mipmap linear filters, anisotropy1 and no bound sampler override
for complete cloud inputs. Active texture was restored. This narrowly qualifies
private literal-secondary assembly/testing on the exact B15 profile, not
universal derivative safety or pixel parity. Worker owns only new literal
builder/manifest/tests/doc; root must build after timing client is terminal.

LIVE exec51489 runs `makeup-original-dh-smoke-r1`, original pinned MakeUp9.5g,
minimal stack, DHpresent, shoreline profile
`run-workloads/makeup-dh-compatibility/coastalwater/high`, 40s warmup,
20s capture, GPU1/16, original resolution1488×836. Stop only this owned runner/
client if needed. Log `dev/logs/makeup-original-dh-smoke-r1-runner.log`.
Next inspect real DH terrain/VBO/render and shader failure evidence; DH mod
presence alone is insufficient. No simultaneous game or heavy build.

Miniature diagnostic worker is assigned only new pack-specific core cue source,
pinned builder/manifest/tests/doc/licenses. It will preserve all original SSR
samples and demonstrate existing core transport before any refinement adapter.
No main/shared ownership transfer, remote push, publication or external message.
Full sustained goal and ordinary15%/second-workload/quality gates remain active.


## DH baseline terminal; literal-secondary pilot live

51489 exit0/accepted original MakeUp9.5g with DH3.3.3 present. DH terrain/water
programs linked, renderer setup complete and fallbackfalse; shoreline PNG
inspected. Unsupported textureLOD extension warnings retained. Drawn DH VBO
count was not captured, so terrain acceptance incomplete. Frame8.358/GPU7.436/
thread7.372ms, single pilot and different DH input from earlier noDH captures.

Literal14 tests repeated; three private archives built after terminal. Enabled
SHA2564bd113c947daa57d191370067c6ba1514ff350602298e5dfa2ec15b150dc4758,
disabled344181d05ebc143be843e4d8ba8b873dc384ce915072cde5edf17723518647f0.
Fresh stoppedB15 cloud-only clones preserve effective coverage=true.
LIVEexec21212 runs exact B15/disabled/enabled serial oneblock, native2976×1672,
minimal/DHpresent,40s warmup20s capture/GPU1/16/fixedwetness0. RootsoleGPU.
Log `dev/logs/photon-cloud-literal-pilot-r1-runner.log`; profiles
`run-workloads/cloud-literal-secondary-probe/forest/literal-{base,off,on}`.
No otherclient/heavybuild untilterminal. Next inspect actualrender/compiler/
pixelquality and exactinputs; retain any regression, then repeat only justified
controls. Candidate mathematical structure does not prove sampler/output parity.

Workers: Miniature owns new diagnostic-only core consumer, SSR untouched;
sampler operator owns new two-file read-only terminal DH evidence utility and
bytecode review, no integration/client/build. Original shared/main owner remains
reserved and SHADOW_VISIBILITY_CACHE_DESIGN.md untouched. Full goal active.


## Literal pilot terminal; second workload preparation

21212 exit0/all three accepted. B15/probeoff/probeon GPU14.5028/14.6946/18.6599ms,
FPS62.186/63.022/51.225. Candidate driver compiled, fallbackfalse and PNGs
inspected without grossscene failure. Enabled single serial pilot regressed;
defaultoff/no integration or source-cause/derivativeparity claim. Retained full
raw evidence and limits in PHOTON_CLOUD_LITERAL_PROBE.md.

No Minecraft client live. Preparation exec55851 builds fresh stopped Photon
coastal effect profiles from actually rendered original SSR shoreline, exact
B15 protectedlighting/noatmo candidate, DHabsent. Source original SSR options
retained; original/adaptive/globaloff and per-effect configurations documented.
Next terminal prep, then same-JVM native all-active global control on this
second ordinary workload. Sole root GPU, no source ownership change.
Workers still Miniature diagnostic and terminal DH draw evidence, newfiles only.


Second-workload preparation55851 exit0. Root now launching native coastal
B15 adaptive, reflections enabled/DHabsent, same-JVM OFF/ON/OFF/ON global
effects control,40s initial10s settle20s capture/GPU1/16/fixedwetness0,
windowScale2. Exact handle follows tool launch output. Log
`dev/logs/photon-native-coastal-effects-forward-r1.log`. No otherclient/build.
Next validate full options/source/epoch/inputmap and inspect actualnormalwater,
then analyze bounded second-workload signal; not a≥15%acceptance fromonepilot.


LIVE coastal benchmark handle3149 verified; no concurrentclient/build. Root
found and records sampler resolution assumption error: r2 actual1488×836,
notnative2976×1672. Rawdata unchanged; native literalpilot extrapolated state
across resolution. Corrected PHOTON_CLOUD_LITERAL_PROBE/USER_LESSONS; old
builtmanifest qualification context flaggederroneous. Next after3149terminal:
strict coastal analysis, then sampler r3 B15 sameprofile with windowScale2 and
GPUtimingfalse; refresh literal sampler evidence/context only afterrealreport.
New DHdraw utility/mixin/doc frozen, notregistered/compiled yet; integration
and a separate actualDraw diagnostic follow. RootsoleGPU; fullgoalactive.


## Native coastal control and corrected sampler terminal

3149 exit0/strictaccepted exactB15 coastal effects OFF/ON/OFF/ON. GPU14.0414/
12.7941/13.8791/12.9075ms:8.88/7.00% adjacentreductions; frame8.38/5.99%.
Root normalwater/shoreline PNG inspected; preserved source/capture/inputhashes.
Secondworkload correlatedsignal, not≥15% or repeatedoriginalbaseline acceptance.

77521 native-size sampler r3 exit0, actual2976×1672 verified in retained
workloadjson, B15/minimal/DH/precompute=true, GPU samples0. Report
`dev/logs/cloud-sampler-state-diagnostic-r3.json` SHA256
5dbc7edf7fa5b11cebd2a4ac948a2c8e9553ddf467f5141831c29e162e283e67.
Reviewed samecompleteinput constraints pass; literalworker refreshes only
fourfrozen source/evidence files, with unchangedshader memberhashes. Prior r2
resolutionerror/history retained; no nativepixelparity or performanceclaim.

Root integrated opt-in DHdraw utility/mixin and default-off devplugin gate.
`compileDevClientJava build` passed; productionjar check found0dev entries.
Only dev/build sources changed. Root now launches originalMakeUp DHdraw
30s warmup5s diagnostic, GPUtimingfalse, fixedwetness0, standard1488×836,
log `dev/logs/makeup-dh-draw-diagnostic-r1.log`; authoritativehandle follows.
No secondclient/build untilterminal. Acceptance requires positiveworlddraws,
exactartifact/classhook and inspecteddistantimage, notlistcountalone.


## DH actual submissions terminal; BSL baseline live

28154 exit0; opted-in hooked _neoforge DHrenderer submitted394725opaque/
389269translucent positive-index worlddraws cumulativesincewarmup. ExactDH3.3.3
hashmatches; rendererexceptiondisabled=false, Irisfallbackfalse, GPUqueries0.
Rootshoreline/farland/water PNG inspected. Submission plus boundedimage evidence,
not completedGPU/perpixelwaterboundary/shadowcorrectness. Retained log/report/
PNG and precise list-vs-draw semantics in DH_RENDER_EVIDENCE.md.

BSLoriginal10.1.8 sourceplan complete andfreshprofiles27200terminal. Untouched
HIGHdefaults keep authorMac-incompatible voxel optionoff. Originalplusnative
WATER_PARALLAX=false only lowerqualitycontrol; no sourceadapter/redistribution.
Root launches `bsl-original-smoke-r1`, minimal/noDH, standard1488×836,
35s warmup20s capture/GPU1/16, inspectedshoreline. Authoritativehandle follows
execoutput; log `dev/logs/bsl-original-smoke-r1-runner.log`. RootsoleGPU;
nootherclient/build. Next originalshaderfailure/render/options inspection.
Miniaturecore8fixtures passed andexactarchivebuilt; fresh4cueprofiles87851
terminal, ready afterBSL. Cuechangesno SSRwork; no performanceclaim.
Literalworker refreshingnative r3 sourcequalification, originalshaderpatch
hashes unchanged. Originalmain/shared ownershipreserved; fullgoalactive.


LIVE BSLrunner handle6187 verified; soleGPU root, currentoriginalHIGH arm.
Literal r3qualifier source/tests/doc frozen16fixtures, correctactualhash
loading and separate source/diagnostic profiles; shaderpatchhashes unchanged.
Sampleroperator now owns only CloudSamplerStateProbe.java and itsdoc for
bounded all-active-scalar2D fullscreen capture, enabling future Miniaturedepth/
color filtering review. Existing r2/r3 rawdata untouched. No sourcecompileuntil
6187terminal; worker no builds/clients. Miniaturecue runtime comes next, then
original samplerdiagnostic ifrefinement experiment is stillworthwhile.


## BSL terminal; Miniature diagnostic live

6187 exit0/bothaccepted originalBSL10.1.8 HIGH/noDH. FulloriginalSHADOW/colored
shadow/lightshaft/TAA/REFLECTION2,2048/256shadowparameters, voxelguardsoff.
RootPNG inspected actualterrain/waterwaves/reflectedtrees/shadows, fallbackfalse.
OriginalGPU8.0697ms/FPS114.319 versusnativeWATER_PARALLAX=false7.8431/117.271:
2.81%GPU singlepilotdifferentquality, noLODgain/performancepromotion. Rawsource/
options/artifacts/samples/logs/PNGs retained. No derivative/distribution.

Scalar2D/fullscreen samplerprobe extension frozen and rootlaunchcompile next.
Miniaturecorecue runner launched4states absent/disabled/enabled96→160/8→16,
noDH/minimal,25s warmup8s capture/GPUqueriesdisabled. Sameexactprivatearchive,
optionsCORE_PROBE1/CORE_CUE1; nativeSSR/budgets unchanged. Log
`dev/logs/miniature-core-bridge-cue-r1-runner.log`; rootsoleGPU, actualhandle
followslaunchoutput. Next verifyall4actualwatercuecolors/hashes, compile/fallback
and actualcoreconnection. Diagnostic is no performance/quality acceptance.
Then originalMiniature scalar2D samplerstate and high-rate cue-off motion/repeat
nativecoastal controls, preserving≥15%/independentruns/quality pendinggates.


## Miniature core cue terminal; original sampler diagnostic live

21160 exit0/all4accepted. Root inspectedabsentmagenta/disabledyellow/enabled
96→160greenblue/8→16mostlybluewater. Actual existingcore2/globalenable/nearfar
reachMiniaturepixels; sampler/SSRfunctions/budgets unchanged. ZIP0855cf136e73ff
4773f5609440dfae25e5d8bf59a097810dd5edb1c9145fe97e. No optimization/support/
performanceclaim; eightfixtures and exactsource/artifact/PNGchecks retained.

LIVEexec55863 originalMiniature2.19/noDH/minimal/1488×836shoreline,
20s warmup5s diagnostic/GPUtimingfalse/fullscreen scalar2Dsamplerprobe,
log `dev/logs/miniature-sampler-state-diagnostic-r1.log`, profile
`run-workloads/miniature-original-probe/coastalwater/high`. RootsoleGPU.
Nextterminal preservejson/workload/PNG, reviewactivefinalpass depthtex0 and
colortex0/6/7 filters/LOD/aniso, then qualifiedexplicitdistance-refinement
sourcehypothesis versusnativeoriginal2-refinement control. BroaderPhoton
repeat/motion/15%gate andconfigqualityremainactive; mainsharedreserved.


## Checkpoint before continued refinement and quality work

55863 exit0: actualoriginalMiniature1488×836/minimal/noDH, GPUqueries0.
PreservedreportSHA b04ab014bd202a8e4ba48a539bb1c65867e10e13b3fa4694ef37e2f6187cc68e.
Complete depthtex0/colortex0/6/7 linkedset observedprogram16: non-mip
nearestdepth/linearcolor, aniso1/sampler0/bias0/base0; broadLOD/levelbounds,
activeunitrestored. Exactframebuffer/source verified separatelyinworkloadjson.
No client currentlylive. Nativegoalactive, rootsoleGPU. Priorhandles allterminal.

Frozenmilestone checks: Java/buildintegrationpassed; devclassesexcludedfrom
productionjar; actualDHhookaccepted; literal16, hoist9 andMiniaturecore8
lightweightfixtures passed, Pythoncompile/scopeddiff clean. Negativecloud
probesretained, BSLnativecontrolsinglepilot, Photonsecondworkloadpositivebut
correlated7–9%signal. No15%/independent-run/performance/support promotion.

New Miniaturerefinementworker owns onlynew5files (builder/patch/privatehelper/
tests/doc), acceptingexistingcore2 with4near/3mid/2far refinements onlynative4.
Outertrace/bracket/mask/jitter unchanged; activeassemblyqualifiedexactnative
samplerreport, privatefull4 explicitsampling controlrequired, syntaxstillneeds
actualIrisdriver. It doesnot ownpriorcuefiles/main/sharedbuilds/clients.
Rootnext: reverse nativecoastal effectscontrol plushighratecue-offquality,
then reviewworkerfreeze andboundedrealcompile/controlcaptures. Existingmain/
sharedownerreserved, originalSHADOW_VISIBILITY_CACHE_DESIGN.md unstaged/
untouched. No push/publication/messaging. Full goal/gates remainunshrunk.


Local checkpointcommitc3f7cc5 saves35source/evidence files afterfrozenchecks,
excluding originalowner SHADOW_VISIBILITY_CACHE_DESIGN.md. No push. LIVEexec18005
nativecoastal reverse ON/OFF/ON/OFF same-JVM effectscontrol, B15/minimal/noDH/
2976×1672/40s initial10s settle20s capture/GPU1/16/fixedwetness0. Log
`dev/logs/photon-native-coastal-effects-reverse-r2.log`; rootsoleGPU. No other
client/heavybuild whilelive. Nextstrictreverseanalysis, then10FPS original/
globaldisabled/active shoreline motion quality using samepathandcue0, followed
Miniaturemode1 full4explicit sampling vs mode2refinement privateprobe after
workerfreeze. Nativegoalactive, main/sharedfilesreserved; newworker5files only.


## Coastal reverse terminal; high-rate quality live

18005 exit0/strictaccepted reverse ON/OFF/ON/OFF GPU12.8727/14.2618/12.8202/
14.4741ms:9.74/11.43%adjacentGPUreductions, frame9.10/10.49%. Separatebyte-
fingerprintedharness revisions sincefirstcoastal due optindiagnostics; each
sessioninputs stable. Positivebalanced direction, no15%/modabsentfullacceptance.

LIVEexec47878 originalPhoton/noDH/minimal/native2976×1672 10FPSshorelinequality:
request `run-workloads/native-coastal-effects/shoreline-motion-10fps-r1.json`,
30s warmup20s motionz680.5→640.5/y68/x240.5/yaw180/pitch3, sameverifiedopen
watertrajectory withcorrect yaw forsource.201expectedPNGcallbacks. GPUtiming
false, cameraQualityOnlytrue; log
`dev/logs/photon-native-shoreline-original-motion-10fps-r1.log`. RootsoleGPU;
no benchmark/perfgainfromPNGreadbacks/encodes. Next validateactualcamera/input/
callbackhashes then identicalglobaldisabled/activequalitycaptures, inspect
motion/reflections/clouds. WorkerMiniatureprivateprototype andsinglefile
nativecap16sourceplan continuewithoutclient/build; fullgoalactive.


## High-rate capture incomplete; writer repair and third control

47878 terminalexit0 butstatusincomplete_capture22/201,179writerbackpressure.
NativePNGwrite~0.727s/12MB saturatesoneinflight bound. Raworiginalnative
session38bb5653-804e-443b-a3d1-34b0bf8427e6 retained asfailure, nothigh-rate
quality. Cameraowner owns CameraQualitySession.java/newFastPngWriter.java/doc
forisolatedlossless DeflaterPNG, noSTBglobalstate/GL/camera change. Encoding/
compression0..1/pixelAPI/classhash/12GiBboundedcap recorded; tinyroundtripfixture
allowed, nofullGradle/client. Rootwillcompile/retryfreshnative10FPS then matched
arms; oldrawdataimmutable.

Root launches thirdcoastalforward OFF/ON/OFF/ON on FROZENcompiledharness,
explicit `-x compileDevClientJava` so camera sourceworkdoesnotcompile/change
measuredclasses. B15/minimal/noDH/native2976×1672/40s initial10s settle20s
capture/GPU1/16/fixedwetness0. Log
`dev/logs/photon-native-coastal-effects-forward-r3.log`; actualhandle follows.
RootsoleGPU; no fullbuild untilterminal. WorkerseparateMiniaturerefine5files,
nativecap16preparer+geometryvalidation3files, cameraencoder3files. Fullgoalactive.


LIVE thirdcoastalcontrol exec5121 verifiedfromlaunch; compileddev source task
excluded intentionally, rootsoleGPU. Nextpoll exacthandle, strictanalyze and
preservebenchmarkJSON/PNGs beforeanynextlaunch. Then wait/reviewfrozenfastwriter,
compile/devjar exclusion/pixelroundtrip check and fresh10FPSnativeoriginalretry.
Camera worker wasnotifiedofroot's one factual docappend duringownership; root
stoppededitingitsfiles, correctedcoordinationrecordinUSER_LESSONS. No other
source/fileownershiptransfer. Existingoriginalownerdesignfile staysunstaged.


5121 terminalexit0/strictaccepted thirdcoastalforward GPU14.3219/12.9224/
14.1451/12.6403ms (9.77/10.64%adjacentGPUreductions), frame8.84/8.90%.
Priorcompiledharness frozen/classchecks passed. Tinyencoderfixturecompile may
haveoverlapped CPUwork; preservedqualification, no15%orhardwarecausalupgrade.
NoMinecraftclientnowlive. Camera fastwriter6pixel/CRC/row/alpha roundtrips
passedstandalone atcompression0/1; workerfinishingfreeze. Rootmustwaitfreeze,
then compiledev/build/jarexclusion andfreshnative10FPSretry. AvailableSSD299GiB,
bounded12GiBpersequence fits; no rawcaptureoverwrite. Miniature5filesfrozen7
fixtures, activeassembly/pixelcontrolstillunbuilt; nativecap16prep/geometry
validationworkerfinishing3files. Fullgoalactive; rootsoleGPU.


## Fast PNG source frozen and retry launched

Camera3files frozen/reviewed. Six standalonepixel/alpha/row/CRC losslessroundtrips
passed; rootdev/fullbuild nowpassed andproductionjar0deventries including
FastPngWriter nestedclasses. PNGcompression0 explicitnewr2request; bounded12GiB
persequence,64MiBperPNG, oneimage/row/fixedbuffers/callbackhashguard preserved.
Priorincomplete22/201 r1manifest/images unchanged. CurrentSSD299GiB available.

Root launchingfresh nativeoriginalPhoton 10FPS r2sameopenwaterpath,
30s warmup20s capture/GPUtimingfalse/cameraOnly/noDH/minimal/2976×1672.
Log `dev/logs/photon-native-shoreline-original-motion-10fps-r2.log`, request
`run-workloads/native-coastal-effects/shoreline-motion-10fps-r2.json`.
Actualhandle followslaunchoutput. RootsoleGPU; nocongame/heavybuild. Next
validateall201callbackhashes/actualcamera/sourceencoding thenmatchedglobaloff/
oncapturedatidenticalrate/path. Failuremustremainqualified, notresampledaway.

Miniaturerefine7fixtures/rootPythoncompile passed; privateZIPbuilt SHA
 df2a3d2b50bebdba2e7eae1066bd33c03cc6f3234f6c7143ce660b431b0010e6.
Sameprofile sampler/evidencepins qualified; actualGL mode1/2stillunverified.
Nativecap16prepare/validationworkerfinishingfrozenhandoff. Fullgoalactive,
currentc3f7cc5 checkpoint plusnewunstagedsource/evidence; ownerdesignuntouched.


## Native fast writer actual201/201; matched disabled launched

69835 exit0/complete,201/201native10FPSPNGcallbacks/hashes,0skips. Original
Photon/noDH/minimal/samepath20.0728s401ticks; total4.003GB andmedianwrite31.69ms.
Rootfirst/mid/endPNG inspectedorientation/colors/openshoreline. Retainfailed
r1separate; thiswriter success is noGPU/render speedup. CurrentCameraQuality/
FastPngWriterclass/source/encoding metadataretained inactualmanifest.

Root launchingidenticalr2path/rate nativeB15 patched-off/globaldisabled quality,
30s warmup20s motion/GPUtimingfalse/cameraOnly/noDH/minimal; log
`dev/logs/photon-native-shoreline-disabled-motion-10fps-r2.log`, profile
`run-workloads/native-coastal-effects/coastalwater/patched-off`.
Actualhandle followslaunchoutput. SoleGPU root; next201callback/camerahash
validation, thensameadaptive/globalenabled quality. No glint/underwater/third-
person/fulltemporalacceptance inferred fromoneshoreline trajectory.

Nativecap16preparer/geometryvalidatorfrozen16new+18existinglightweightfixtures,
onlypreparedsourceplan; rootrealcopy/render/timing stillpending. Miniatureprobe
built/frozen7tests; mode1full4explicit vs mode2LOD driverquality/timingpending.
Fullgoalactive, no15%/sourcecause/support/defaultpromotion, ownerdesignuntouched.


Disablednativequality actuallylaunchedonce despiteoversizedexec imageoutput
hidinghandle; exactownedGradlePID89151/profile/log verified, no duplicateclient.
PID89151 nowterminal/logBUILD SUCCESSFUL. Disabledsequence complete201/201,
allPNGhashes verified; sourcefirst/mid/endoriginalPNGs nowinspectedonepertool
response (largeuncompressed images exceedIPC whenbatched). Originalpath
approachesbank atlastframe; no inside-blockintersectionseen. SameRGBcolors/
orientation andnormalwaterreflection, notglint/underwateracceptance.
Root launching adaptive/globalenabled identicalnative10FPSr2quality;
log `dev/logs/photon-native-shoreline-enabled-motion-10fps-r2.log`, profile
`run-workloads/native-coastal-effects/coastalwater/adaptive`. Actualhandle
followssmalllaunchresult; rootsoleGPU. Nextverify201frames/callbacks/poses,
inspectmatchedframes/motion thennativecap16controls/Miniaturedriverprobe.


## Native shoreline three-arm quality terminal

45286 enabledterminalexit0/complete201/201/0skips; original/disabled/enabled
all603PNGhashes verified. Same10FPS/r2request/native2976×1672/minimal/noDH,
maxeasedZerror0.000149blocks/eyeY69.62constant. Fullsource/pose/options/config/
artifact/encoding metadataretained; representativeoriginal0/100/200 andenabled
100PNGs inspectedfororientation/colors/nearshadows/waterreflection. Noinside-
blockintersectionseen; pathapproachesbank. Fullmotion/glint/underwater/
thirdperson/independentperformance acceptance stillpending; qualityunmeasured.
No Minecraftclientcurrentlylive, rootsoleGPU. Cameraowner nowonlynewdurable
standalonePNGfixture+runner2files, no session/doc overlap orcompilerbyworker.

Rootnativecap16preparation launchedafter16fixtures/staticguards passed:
`prepare_native_terrain_profiles.py --source-root run-workloads/native-effect-isolation
--output run-workloads/native-terrain-b15-r1 --acknowledge-warmed-scene`.
Log `dev/logs/native-terrain-b15-preparation-r1.log`; sourcecanonicalhighsave
all4arms, B15geometryclonednewadaptive, request24/effective16/DH128 andmanual
warmedack. Actualhandle followsoutput. Nextterminalprepinspectmanifest, pilot
4serialnative/minimal/DH controls withmandatoryactivepolicy/16boundary checks,
quality/actualDH separated; no default/travelcoverage or novelmeshclaim.
Miniaturemode1/full4explicit andmode2LOD privatecompiledrivertrial stillpending.
Fullgoalactive, ownerdesignuntouched, currentc3f7cc5 plusunstagedwork.


Nativepreparation91578 exit1 failedcopied-high fingerprint, failedoutputretained.
Rootconfirmed metadata mode=ro SQLiteSELECT creates empty-wal/32KiB-shm beside
WAL-mode source DB AFTERexpectedfingerprint. Reproducedisolatedcopywith21845rows;
source/copy current230filehashes equal. No logicalMinecraft/DBrow write inferred.
Workerrepair onlypreparer/tests/doc: querytemporaryDB+existingWAL snapshot,
no immutable=1 ignoringpendingWAL, no source-sidecar deletion/hashbypass.
Rootnextfreshoutputr2 afterfreeze/realchecks. No gamecurrentlylive, rootsoleGPU.
DurablefastPNGfixture2filesfrozen; rootstandalonechecknowlaunching. Fullgoalactive.


WALrepairfrozen21fixtures: metadataSQLread nowon temporaryDB+existingWAL,
sourceWal/Shm untouched; locks/fingerprint guards unchanged, pendingWALlatest
rows tested. Rootretrypreparationfreshoutputnative-terrain-b15-r2launched,
failedr1retained; logdev/logs/native-terrain-b15-preparation-r2.log. NoMinecraft
client live. CPUonly originalqualitypreviewencodinghandle33690 mustterminal
beforeanyGPU timing. DurablePNGfixture actualrootexecutionpassed6roundtrips/
247605pixel/CRC/boundchecks, outputbuild/fast-png-checks/check-n0nkruyx/check.json.
Raw603nativePNG files retained; previewdownsized1488×836/yuv420/CRF18 is not
nativepixelparity evidence. Rootnextterminalprep/encoder then4-case GPU pilot.


## Source-preserving native terrain preparation complete; pilot launched

51126 preparationr2 terminalexit0/statuscomplete; fourcanonicalworldhashes equal
fb0bbac2a38d9c3213a85d2987f58306f9d120cb221b53b1cb30bfdce7ec19a3.
Root21WAL/geometryfixtures passed; no sourceDBopen/sidecar deletion/fingerprint
bypass. Failedr1retained. Sourcehigh/off/adaptive exactoptions/config/archive,
geometryfromadaptiveB15 cap16/manualwarmack, request24/DH128. This is source/
copy evidence, notwarmeddistantpixelquality/travelcoverage acceptance.

All CPUpreviewencoders33690/94979/41256 terminalexit0, no heavybackgroundwork.
Root launchesfourserialnative2976×1672/minimal/DH/high(originalmodabsent)/
patchedglobaloff/adaptiveallactive/geometryexistingcap16 once,75s warmup30s
capture/GPU1/16/fixedwetness0. Profiles `run-workloads/native-terrain-b15-r2/forest`;
log `dev/logs/native-terrain-b15-pilot-r1-runner.log`. Actualhandlefollowslaunch.
RootsoleGPU; noanotherclient/heavybuild/test/encoder untilterminal. Geometry
mustreallybeactive/effective16 atbothboundariesandwarmstable, notsilently24.
Nextinspectactualrender/options/source/policy/PNG andboundedpilot, thenbalanced
repeatsifuseful. Separateshader/geometry/combined comparisons; neveraddpercentages
orlabelcapgain novelshader/meshoptimization. Fullgoalremainsactive.


Checkpoint4ba3875 savesverifiedfastPNG/native603framequality, durable6roundtrip
fixture, Miniatureprivateprobe andnativecap16source/geometryguards/WALrepair.
Originalowner SHADOW_VISIBILITY_CACHE_DESIGN.md excluded, no remote push.
LIVEexec5636 authoritative four-armnativeforest/minimal/DH pilot; baseline001
completed59.909FPS, patched-off002currentlynext; noanalysis ofpartialpilot.
RootsoleGPU. Nextresume poll5636/log/manifest andverifyliveclientbeforework,
theninspectall4acceptedsource/options/policy/nativewindow/PNGs andactualGPU
means. Do notstartanotherclient/heavytests/compiler/encoderwhilelive. Ifgeometry
rejects dueinactive/effective24/warmcondition, preservefailure andfixevidence,
notbypass. Nextbalancedrepeat onlyifqualified,useful; Miniaturemode1/full4
explicit thenmode2/refinement driverqualitystillpending. Fullnativegoalactive,
ordinary15%/independent-originalcontrols/quality/configsupport unfinished.
Allworkerscurrentlyfrozen/completed; no handoff/newchat/messageauthorization.


5636 terminalexit0/all4accepted. Original/off/geometry/adaptive nativeforest
GPU15.2692/16.7714/14.4778/15.0119ms; FPS59.909/54.841/62.968/61.675,
thread9.122/8.796/7.406/8.975. Geometryrealactive16 atbothboundaries, policy
stable2105/warm600; initialcanonicalsavehashes equal. CombinedGPU5.18%vs
original, geometryGPU3.56%vsadaptive, weakframegain/clearCPUdrop. Singlepilot
below15%, no sourcecause/quality/defaultpromotion. RootgeometryPNGinspection
pending. Workerowns explicitdevterrainonly same-JVM mode+strictanalyzer5files,
includingrecordedmanualguard re-arm afterON (sameinspectedsceneonly), notauto
travelcoverage orconfig-onlytoggle. No mainpolicychanges.

RootpreparingfreshMiniatureprivate refinement controls; nextoriginal/mode0/
mode1full4explicit actualdriver. Frozencompileddevharness usedduringworker
sourceedits via --skip-build --freeze-dev-classes; fingerprintguards remain.
NoMinecraftclientcurrentlylive; actualprep handle followsoutput. Fullgoalactive.


Rootoriginal/geometrynativePNGinspectioncompleted: nearscene/shadows preserved
atcoarseinspection; distantleftplateau/tree detail visiblycoarser underexisting
cap16/DHreplacement. Explicitgeometryqualitytradeoff, notequivalent image or
protectedfarappearanceproof. No defaultpromotion forweak~5%combinedGPU pilot.

9904 Miniaturepreparationterminalexit0. LIVEexec54815 original/minimal/noDH/
1488×836 vs privateMODE0off/MODE1full4explicit samplingcontrol,35s warmup20s
capture/GPU1/16. ExactqualifiedZIPdf2a3d2…; config96/160/coreglobaltrue forprobe
arms; originalnativecounts10/1.6/4 andsameoptions/worldcopy. Log
`dev/logs/miniature-refinement-full-control-r1-runner.log`, profiles
`run-workloads/miniature-refinement-probe/coastalwater`. Frozencompileddev
classes via --skip-build --freeze-dev-classes whileterrain worker edits dev
sources. RootsoleGPU; no fullcompiler/client/encoderuntilterminal. Nextdriver/
fallthrough/options/PNGsamplingcontrol beforeMODE2LOD. Fullgoalactive.


## Miniature full-four driver terminal; distance pilot launched

54815 terminalexit0/all3accepted original/MODE0/MODE1, fallbackfalse. Actual
Iris330translation/explicitLOD shaderpath compiled/rendered; rootnormalwater/
reflection/terrainPNG inspected, exactpixel/motionparity unverified. GPU4.4219/
4.5044/4.7083ms, FPS182.675/179.555/175.585. Full4explicitnegativecontrol4.53%
slowerGPUvsoff, no shadergain/sourcecause/defaultsupportclaim.

Root launchingfull4explicit/MODE2LOD matchedserialpilot35s warmup20s capture/
GPU1/16/minimal/noDH/1488×836/exactqualifiedZIP and96→160corebands. Log
`dev/logs/miniature-refinement-lod-pilot-r1-runner.log`; frozencompileddevclasses
preserved via --skip-build --freeze-dev-classes whileterrainworker edits5files.
Actualhandle followslaunchresult, rootsoleGPU. Nextinspectall2 actualmode/
coreeligibility/options/PNGs andGPU thenmovingtransitions/disable/absent tests
aswarranted. Native15%/broaderconfiguration andquality remainunfinished.


35046 terminalexit0/all2accepted. MiniatureMODE1/MODE2 GPU4.5430/4.5432ms,
FPS178.234/179.938, thread5.3356/5.2722; GPUflat, nosavings claim. ActualMODE2
compiled/rendered fallbackfalse, rootnearwater/reflections/terrainPNG inspected,
normalcoreGPUvalues/cheapbranchcoverage/transitionquality stillunverified.
Reportsource exactqualifiedZIP unchanged, modeoptions/config/source validated.

Samplerworker2filesfrozenadds exact4normalcore GPUuniformvalue readbacks,
active/types/location guarded, no supplierreevaluation/writes/GLstate change.
Rootcompilemustwaitterrainworker5devfilesfreeze. No gamecurrentlylive. Next
compile/preserveruntimeartifacts/devexclusion, freshMiniatureMODE2/noGPUtime
sampler/corediagnostic, then source-frozen terraincontrol longsettle/rearm.
Targetedwaterquality planner owns onlynewdoc; confirmedcurrent north-looking
shoreline cannotprove sun-glint coverage. Fullgoal/gates remainactive.


Checkpoint0c2ac16 savesnative4-arm results/geometryquality distinction,
MiniatureactualMODE1/2driver controls/flatGPU pilot andexplicitpackmatrix.
Frozen-devrunnerflag dry-run guard/Pythoncompile passed; no main/shared code
ororiginalownerdesign staged, no push. AllGPUclients54815/35046/5636 terminal,
noMinecraftcurrentlylive; rootsoleGPU.

Activeworkers: stack_analysisowns BenchmarkSession/WorkloadInputs/analyzer/
fixture/newterrain-doc explicitsame-JVM terraincontrol5files, no compiler/client;
camera_qualityowns onlyTARGETED_WATER_QUALITY_PLAN.md, nogame; sampler2files
normalcore GPUreadback frozen/rootowned. Rootnext waitterrainfreeze, review
strictguards/re-arm/restore andlightweighttests, compiledev/build/jarexclusion,
thenfreshMODE2Miniature/noGPUtime coreuniform diagnostic. Afterwards longsettle
terrain-only control onalreadyinspected fixedscene. Targetedsunglint/underwater
profiles needactualfluid/geometry verification, notnorthshorelineassumption.
Fullordinary15%/broadconfig/performance/quality/supportgoal intact andactive.


## Terrain/control/core/survey integration compiled

Terrain5files frozen29inprocess+24stack fixtures; root29fixtures rerunpassed.
Manualfixed-scene re-arm explicitlyrecorded andguarded, globaltrue onlyexisting
terrainflagselected; actualON16/OFF24bothboundarypolicies required. Survey2new
files frozen withcache-specificloaded checks/boundedguardedreads; rootforwarded
waterSceneSurvey andterminalreportproperty. Rootdev/fullbuildpassed firstattempt,
productionjar contains0dev entries. Main/shared/sourcepolicy untouched.

RootlaunchesfreshcopiedMiniatureMODE2normalcore/samplerdiagnostic (sourceworld
fingerprint matched/source unchanged),20s warmup5s capture/minimal/noDH/1488×836/
GPUtimingfalse/cloudSamplerProbe true/fixedwetness0. Log
`dev/logs/miniature-normal-core-diagnostic-r1.log`; profile
`run-workloads/miniature-normal-core-diagnostic/coastalwater/refinement-lod`.
Actualhandle followslaunchoutput. RootsoleGPU; nextactualcore2/enabled/nearfar
values withtype/location/programscope, thenlongterrainonly nativecontrol.
Do notinferfragmentcheapbranch/hits fromuniformvalues alone. Targetedwater
quality surveyneedsfreshoriginalscene andloadedonlyread observations next.
Fullgoalactive; no15%/broaderconfig/qualitycompletion ordefaultchange.


## Normal Miniature GPU core verified; terrain-only control live

21144 diagnosticterminalexit0/GPUqueries0. FreshMODE2normalrendererprogram16
activecore GPUreadbacks version2/enabled1/near96/far160 withcorrectscalar
int/floattypes/locations. Report33e724cc8dd4c4087888789768dd4ce2dc2703aa571c673a9b15e2c0cef4d01a
preservedalongsideactual1488×836/minimal/noDH/world/options/classmetadata.
Normalshadertransportverified, notfragmentbudget/hitcoverage orspeedup;
flat4.5430/4.5432mspilot retained.

Freshterraincloneworldhash826e16de47307dafe77ee8afabdbe4f75d4f1efbf4559b56becd8a17ad620db7
matchedstoppedgeometrysource. Rootnowlaunching4phaseOFF/ON/OFF/ON native
terrain-only control: sameB15/globaltrue/all5effects/protectedlighting/DH128/
requested24, explicit existingcap16/manualsceneconfirmationre-arm onceperON.
75s initialwarmup75s settle30s capture/GPU1/16/fixedwetness0/2976×1672/minimal.
Log `dev/logs/native-terrain-inprocess-forward-r1.log`; profile
`run-workloads/native-terrain-inprocess-r1/forest/geometry`.
Actualhandle followslaunchresult. RootsoleGPU; no heavybuild/tests/encoder/
secondclientwhilelive. Nextactualidentity/re-arm/16ON24OFF/warm/screenshot/
artifact strictanalysis; failurespreserved, no bypass. Rootsurveyclasscompiled
butsurveypropertyoffduringtiming. Targetedglint/underwaterplan source-only;
loadedclientcellsurveyafterterrainterminal. Fullgoal/gatesactive.


Checkpoint7f5d5be saves13files compiledterrain/core/survey integration,29fixture
strictanalyzer controls, targetedwaterplan andactualnormalcore GPUreadback.
Originalowner SHADOW_VISIBILITY_CACHE_DESIGN.md excluded/no push. LIVEexec89036
verifiedcurrenthandle, terrainonlyOFF/ON/OFF/ON firstOFFsettling, globalshader
stilltrue/detailedrequested24/DH128, native2976×1672/minimal/75s initial75s
settle30s capture/GPU1/16. RootsoleGPU, no otherclient/heavybuild/tests/encoder.
Log `dev/logs/native-terrain-inprocess-forward-r1.log`; rootnextpollsamehandle,
retainactualfailure/benchmarkJSON/PNG, strict source/scene/rearm/policyboundary/
window/artifactanalysis. No inferred continuouscoverage/defaultchange.
Afterterminal loaded-onlyoriginalcoastal WaterSceneSurvey/noGPUtiming, choose
surveyed glint/underwater routes, sourcefrozenquality+restoration tests. All
workersfrozen/completed, fullgoalactive with15%/broadconfig/qualitygates unmet.


## Terrain control terminal; loaded-only water survey launched

89036 terminalexit0/strictaccepted all4 realON16/OFF24/policy/re-arm/world/
pack/window/artifact/restore checks. GPU15.2141/19.8161/16.1152/14.4899ms,
thread8.3002/6.8212/8.2620/6.9585: CPUdrops17.82/15.78%, GPU−30.25/+10.09%.
RetainfirstONregression, no stable15%/sourcecause/auto-coverage/defaultpromotion.
AllbenchmarkJSON/PNGs/capturemetadata retained andanalyzed.

RootfreshoriginalPhoton/noDH coastalcell survey launched25s warmup5s diagnostic/
GPUtimingfalse/waterSceneSurveytrue. Freshcopyworldmatchedsource, sourceunchanged;
profile `run-workloads/water-scene-survey-r1/coastalwater/high`, log
`dev/logs/water-scene-survey-r1.log`. Frozencompiledclasses (`-xcompileDevClientJava`)
whilecamera worker owns3sourcefiles forperframefluid/fullcollision guard, no
compiler/clientbyworker. Actualhandle followslaunch. RootsoleGPU; nextactual
loadedwatercolumns/collision/surface/cameraFogType observations, selectsurveyed
westglint/underwater origin beforetargetedquality. Fullgoal/gates remainactive.


## Loaded coastal survey terminal; original glint capture launched

3103 surveyexit0/GPUqueries0, originalPhoton/noDH/minimal. All615cells observed
loaded/direct615; camera69.62/240.5/680.5air/FogNONE/firstperson/no collision.
x500,z640/660/680 water60..62(surface62.8889), air63..70; x300two columns had
lowerwater only, sosurveyedx500initialanchor chosen. Sampledpointsnotcontinuous
corridor/glintproof. Rawactualsurvey/workload/log retained.

Camera3filesfreeze integrated/rootdev/fullbuildpassed; freshcamera context
FogType/fullcollision/loaded guards andoptionalexpectedNONE/WATER failbefore
PNG, legacyrequestsrecord-only. ExistingPixelencoder/singleflight/hashguards
unchanged. NewexplicitPhotonwaterqualitypreparer pinsoriginal/B15/noDH, copies
onecanonicalsave into3arms andrecordsnewcontrol/request/worldhashes. Sixcopied
worlds acrossglint/underwater rootsallcanonicalf8f3b866…a365, sourceunchanged.

Rootlaunch originalglintprobe atx500.5/y68/z660.5 time12500, yaw78→87.380→96→
87.380→78, pitch2.2→3.735→5→3.735→2.2; native2976×1672/10FPS201slots/
30s warm20s motion/minimal/noDH/expectedFluidNONE/rejectFullBlock true/
GPUtimingfalse. Profile `run-workloads/photon-targeted-glint-r1/coastalwater/high`,
requestparentcamera-request.json, log `dev/logs/photon-targeted-glint-original-r1.log`.
Actualhandle followslaunchresult; rootsoleGPU. Next201hash/context/actualsun-
glint visibility verification beforematchedoff/on; failedNoGlint scene not
acceptedastargetedquality. Underwaterrootpreparedy60 butunexecuted, expects
actualWATER; allnativegoal15%/config/qualitygates remainactive.


31621 glintfirstattempt terminalexit0 butqualityfailed0PNGs: clientdaytime7000
snapshottedbefore12500 preparedtimepacket synchronization. Failed575eb8ba…
retained. Rootcamera acquisition nowwaits clientdimension/daytime equals
frozenserverbeforeinitialinput snapshot; allstrictcapturing guardsunchanged.
Rootdev/fullbuildpassed. Sameimmutable x500/time12500/request/native10FPS
originalretrylaunched, separate `dev/logs/photon-targeted-glint-original-r2.log`,
samepreparedprofile/newUUID; actualhandle followsoutput. RootsoleGPU; next
actual201NONE/no-fullcollision/hashchecks andvisiblesun-glint beforecandidate.
No qualityclaim fromfailedattempt/build. TerrainmixedGPU resultretained.
Fullgoalactive, maindesignuntouched, no remoteactions/defaultchanges.


## Original targeted sun glint complete; disabled launched

95096 retryterminalexit0/complete201/201/0skips/allhashes checked; perframe
camera_context recordsactualNONE/no-fullcollision/loadedcamera guards. New
startupbarrier andcapture fluidcontracts exercised. Rootframe50 actualsun/
waterglintvisible onnativeoriginal atx500.5/time12500/source-derivedwestern
angle. Boundedoriginalglintcoverage established, notfulltemporal/candidate
quality. Failed575eb8ba…zero-frameattempt retained; successfuld66ac242…UUID
andmanifest/log/PNGs immutable. Preparedwaterprofile/scriptall3equalworldpins.

Rootlaunchsameoriginalglint request onB15globaldisabled/patch-off, native/
10FPS/minimal/noDH/GPUtimingfalse/expectedNONE/no-fullblock, log
`dev/logs/photon-targeted-glint-disabled-r1.log`. Actualhandlefollowslaunch.
RootsoleGPU; next201contexts/hashes, thenadaptiveglobalenabled andactualglint
comparison. Underwateroriginalfirstnext withexpectedWATER. Fullgoalactive;
15%/configsupport/motionfar/thirdperson uncompleted, no defaultpromotion.


Checkpoint5e6d042 saves8files real615cellsurvey, boundedsun-glint original,
perframefluid/collisioncontracts/startupsyncbarrier, mixedterrainresults,
andpinnedqualityprofilepreparer. Rootall201successfulglintframecontexts explicitly
assertedNONE/nonfullcollision andallhashesverified. Failedinitialzero-frame
sequencepreserved. No maindesign/source/policy/default changes, no push.
LIVEexec28500 verifiedcurrenthandle: disabled/B15westglintnative10FPScapture,
log `dev/logs/photon-targeted-glint-disabled-r1.log`, rootsoleGPU. No other
client/heavybuild/encoder. Nextpollexacthandle, validateall201actualcontexts/
hashes, thenadaptiveenabledsameimmutable request andcomparison. Afterwards
originalunderwater sourcex500/y60 route withstrictWATER/non-full-collision
beforematchedarms; no untested continuouscorridor orthirdperson claim. All
workerscurrentlycomplete/frozen. Full15%/broadconfig/quality/supportgoal active.


28500 disabledglintterminalexit0/complete201/201/0skips; all201actualNONE/
nonfullcollision contexts andon-diskSHAchecks passed, UUID592394d3-9edc-435e-
a78e-10ee28f5b2f3. Rootframe50disabledglintPNG inspected; fullsequence still
separatefromnativepixel/temporalparity claim. Sourceclass/pack/config/pose
metadata retained. LIVEexec77297 adaptiveglobalenabled samex500/time12500/
westwardsweep/native10FPS/201slots/expectedNONE/noncollision/minimal/noDH/
GPUtimingfalse, log `dev/logs/photon-targeted-glint-enabled-r1.log`. RootsoleGPU;
next validate201contexts/hashes/actualglint thenunderwateroriginalfirst.
Fullgoalactive; nochanges main/shared/defaults/15%acceptance.


## Glint triplet complete with visible candidate band; underwater original live

77297 enabledglint terminalexit0/201/201/0skips, allactualNONE/noncollision/
on-diskhash checks passed. UUID7d5a0e26-d02c-4c76-9f47-51d3ef8db4a9.
Rootframe50enabledinspectionfinds broadbrightwaterband absentoriginal/off;
qualitativecandidatequalityproblem, notpassoridentifiedcause. Glintitself
stillvisible. Rootsourceauditor owns onlyPHOTON_GLINT_SOURCE_AUDIT.md for
exactB15/water/SSR/fog/cloud optiongates andminimalhypotheses, no shaderchange
beforeactualisolation. Allrawtriplet603frames/context/class/source unchanged.

LIVEexec43689 underwateroriginal/noDH/minimal/native10FPS x500.5/y60/z660.5→
640.5/yaw180/pitch−10/time7000, expectedcameraWATER/no-fullcollision,
30s warm20s motion/GPUtimingfalse. Log
`dev/logs/photon-targeted-underwater-original-r1.log`; rootsoleGPU. Next201
contexts/hashes/actualunderwater image thenmatchedarms. Source-script optional
samepackalloff/water/SSR/fog/cloud qualityisolations added; actualprep/waterband
isolationaftercurrentcaptureterminal, no compiler/heavycopies whilelive.
Fullgoalactive; no15%/broadconfig/glint-quality/defaultpromotion.


43689 underwateroriginal terminalexit0/incomplete176written/25writerbackpressure.
All176capturedWATER/no-fullcollision/hashes verified; rootframe100actual
underwater sceneinspected. Native2976×1672/10FPS incompleteevidence retained,
no fulltemporalacceptance; explicitstandard1488×83610FPS labelledretry next.
Newimmutable requestcamera-request-standard-r2.json prepared, oldunchanged.

5193 glintisolationspreparation terminalexit0/freshcanonical8-arm rootsource;
original/off/adaptive plusalloff/water-only/SSR-only/fog-only/cloud-only options
pinned B15/noDH/globalenabled, no lightingbudget changes. Rootsourceauditdoc
frozen identifiespotentialfarSSR skip/fallback≥100 andwaterband80..112 overlap,
no causeproof. Rootlaunchalloffglintnative10FPS/samecontrol/GPUtimingfalse,
log `dev/logs/photon-glint-alloff-r1.log`, actualhandle followslaunchresult;
rootsoleGPU. Next201contracts/hashes/PNG thenSSR-only/water-only isolate band.
Fullgoalactive; no15%/default/supportpromotion, allfailedcapturerawretained.


34617 alloffterminalexit0/incomplete200/201/onebackpressure, allwrittenNONE/
noncollision/hashchecks passed; rootframe50bandabsent, narrowingmoduleswithout
fullquality/cause claim. Rawbd82c2b5… preserved. Newexplicitnative2FPS41-slot
samepath isolationrequestcamera-request-2fps-r2.json (old10FPSimmutable), for
actualSSR-only/water-only appearance identification nottemporalacceptance.
LIVEexec7364 SSR-only/genericallothersoff, B15/globaltrue/native/noDH/minimal/
30s warm20s sweep/GPUtimingfalse/frozencompileddevclasses (`-xcompileDevClientJava`),
log `dev/logs/photon-glint-ssr-only-2fps-r2.log`. RootsoleGPU.

Camera worker ownsCameraQualitySession/doc pluspureCaptureWriteTracker/helper
fixture4files for OPTIONALmaxInFlightImages1..2/default1/capacity1singlewriter/
strictboundedmemory/count/peak/slotcallback/drain/hash/errors. No compiler/client
byworker. Rootnativeunderwater10FPSfailure176/201 retained; standardresolution
retryisadditional only, notsubstitute. Newmax2native retryafterfreeze/compile
requiredifthroughputhypothesisworks. NextSSR/waterbandisolate beforeanyprivate
shaderpatch; normalstaging/15%/broadconfig/qualitygoal remainsactive.


## Glint band isolated to SSR in bounded view

7364 SSR-only and30204water-only terminal/complete41/41/0skips each atnative
2FPS, all82contexts/hashes verified. Rootframe10SSR-onlybandpresent, water-
onlybandabsent. Alloffbandabsent; SSR sufficesatthispose, no universalwater/
fog/cloud qualityclearance or10FPSacceptance. Sourceauditor correctednaming:
B15specmember gatestrace viaquality!=2, notdo_ssr variable. Privateprobeworker
owns4newfiles only, defaultoff localtier2→0 remap restoringoriginalintersection/
refinement/tracepath whilepreservingcaller/feature/near-mid/disable gates;
roughraycountLOD stillpresent, downstreamfog/sky contextdocumented. Rootno
shaderpatchselectedforrelease/default; goalrequiresactualcontrol/qualityproof.
NoMinecraftcurrentlylive, rootsoleGPU. Camera4fileboundedmax2/helperfixture
sourceworkactive, nocompiler/clientbyworker. Nextallworkerfreeze, rootcompile/
fixture/devexclusion, nativeunderwater10FPSmax2 retry; privateSSRguard source
review/build+actualsameglintguardoff/on afterwards. Fullgoalactive/unshrunk.


Checkpointb9f4110 savesactualSSR-only/water-only bandisolation andboundedprivate
controlhypothesis;3bdc625 savedoriginal/off/onregression+scriptcontrols. No main/
sharedshader/policy/design staged, no remoteactions. Allclients7364/30204/34617/
43689terminal; noGPUclientlive. Activeworkerscamera_quality owns4files capture-
tracker/max2/doc/purefixture; photon_cloud_guard owns4newprivateSSRfarcontrol
files; bothsourceonly/no compiler/client. Rootnextverifytheirfreeze+API/fixtures,
compiledev/build/exclusion, rootnativeunderwater201slotmax2 retry withimmutable
newlabel andretained176/201failure. ThenprivateSSRguardoff/on actualglint to
proveoriginalfartrace restoresartifactbeforeanypracticaloptimization decision.
Fullgoalactive: ordinary15%/broadconfig/quality/support stillunachieved.


## Private far SSR quality control assembled; disabled launched

Root11sourcefixture tests passed. PrivateB15 guarddisabledZIP SHA03d6115056fc4
c2009839d1c572555d64e698089c27cc16ec4724a6f4ac1474c; enabled41ecbee8b72039654
fb0d25b16a841e3a2f4500025ec918687f94c0b8cba1555. Onlyspecmembertier2→0 and
independentsettings declaration; sourceoffexactB15, roughraycountstillLOD,
originalcaller/texture/projection/hit/shadow/BRDF untouched. Morefartrace work
forcausality, nooptimizationclaim. Source/hash/license/metadata controlsretained.
FreshSSR-onlyprivate off/on profiles matchedstoppedsourceworld/options/config;
sourceunchanged/private-experiment.json recordsactualnewZIP/options/copyhashes.

Rootlaunchdisabledguardnative2FPS41-slot glintsweep/noDH/minimal/globaltrue/
GPUtimingfalse/expectedNONE/no-fullcollision, sameimmutableinputasSSR-only.
Log `dev/logs/photon-ssr-far-guard-disabled-quality-r1.log`, profiles
`run-workloads/photon-ssr-far-guard-quality-r1/coastalwater/{disabled,enabled}`.
Actualhandle followslaunchoutput. RootsoleGPU; frozencompileddevclasseswhile
cameracapture4filesworker finishes source. Nextactualprivateoffbandreproduction
thenenabledoriginalfartrace image/actualcompile. Do not inferqualityfix or
budgetcoverage withoutactualpixels. Nativeunderwatermax2 retryawaitsfreeze/
compile/purefixture; fullgoalactive, main/shared/defaults untouched.


48076 privateguarddisabled terminalexit0/41/41/0skips, allNONE/noncollision/
hashchecks passed; actualprivateoff render accepted, bandPNGinspectionpending.
Camera4filesfrozen boundedmax1/default ormax2 explicit; rootdev/fullbuildpassed,
productionjarexcludesall devclasses, pureCaptureWriteTracker fixturepassed
pending/slot/rejection/singlewriter/drain/thread invariants. No oldrawmutations.

Rootfreshimmutablecamera-request-native-inflight2-r3.json/newlabel, native
2976×1672/10FPS201slots/WATER/non-fullcollision, samex500.5/y60/z660.5→640.5/
30s warm20s motion/minimal/noDH/GPUtimingfalse. Originalunderwaterretrylaunched,
log `dev/logs/photon-targeted-underwater-original-native-inflight2-r3.log`.
Actualhandle followslaunchoutput; rootsoleGPU. Nextvalidateactualleases/peak≤2/
queue≤1/drain/201contexts/hash consistency, retainifstillbackpressure. Then
privateSSRguardenabledglint toassessbandrestoration; moreworknotoptimization.
Fullgoalactive, main/shared/defaultpolicy/ownerdesign untouched.


## Native underwater capture201/201; privateSSRguard enabled launched

7545 nativeunderwatermax2 retryterminalexit0/complete201/201/0skips. All201
actualWATER/nonfullcollision/PNGhash/slotidentity checks passed. Finaltracker
pending0/accepted201/settled201/rejected0, peakpending/images≤2/writing1/queue≤1.
Original176/201failureunchanged; no shader/perfspeedup orfullqualityclaim.
Raw2743e7ff…/manifest/log retained. Remainingmatchedunderwateroff/on native.

Privateguardoffactualframe10bandreproduces. RootlaunchguardenabledSSR-only
native2FPS41-slot sameglint/nonecontract/noDH/minimal/GPUtimingfalse, log
`dev/logs/photon-ssr-far-guard-enabled-quality-r1.log`. Actualhandle follows
launchoutput; rootsoleGPU. Compilerclassharnesschangedbetweenoff/ON because
captureboundfixintegrated; privateShadercode/count/control comparison remains
exact, no nativepixel/hardware causalparityclaim. Preparedparentmanifest
updatedafterOFFterminaltoactualinitialsource/copyhash d9dbd58e…/privatearms
(notoldinheritedcanonical), withrawOFFmanifest untouched. Next41contexts/hashes/
imagebandcheck, thenmatchedwatercapture andpotentialpracticalSSR qualityguard.
Fullgoalactive; originalshared/main/defaults/design untouched.


Actualsuccessfulunderwatertrackerpeakpending/images was1 despiteconfigured
max2. Recordexplicitly: no evidence two-simultaneousimages causedsuccess;
fixturecoversbound2 paths, runtimeprovesonlyobservedpeak1/drain/201slots.
No causalwriter/rendering speedup inferred. Rawfailednative176/201 andnew201/
201 remainboth retained. CurrentprivateSSR guard30338 soleGPU running.


30338 guardON terminal41/41/0skips/allNONE/noncollision/hashes, actualprivate
compiled/rendered. Rootmatchedframe10tier2→0trace restoredremovesbrightband,
offreproduces. SupportsfarSSRskip causeforthisboundedview; notfullmoving/pixel
parity orspeedup. Fournewfar2→1 reduced-trace probe files delegatedshaderworker,
existingdiagnostic/sharedhelper untouched/defaultoff; quality/coststillunverified.

Rootnowlaunchmatchedunderwaterdisabled/B15/native10FPS/max2/201WATER/no-full-
collision slots,samex500y60z660→640/minimal/noDH/GPUtimingfalse. Log
`dev/logs/photon-targeted-underwater-disabled-native-inflight2-r3.log`;
actualhandle followslaunchoutput. RootsoleGPU; nextactual201/drain/peak/context/
hashchecks thenenabled. Sourceoperatornewprivate4filesonly, nobuild/clientbyworker.
Fullgoalactive/unshrunk; originalunderwater176failureand201retry bothretained.


81132 underwaterradius-disabled terminalexit0/201/201/0skips allWATER/nonfull-
collision/hashes/slotidentity verified; finallease0/settled201/bounds≤2, actual
peakpending1 again. No evidence max2usedsimultaneously causedsuccess. Raw
5bdb5470…/manifest/log retained. LIVEexec80918 enabled/B15matchedunderwater
native2976×1672/10FPS201WATERslots/max2/minimal/noDH/GPUtimingfalse/samepath,
log `dev/logs/photon-targeted-underwater-enabled-native-inflight2-r3.log`.
RootsoleGPU; next201/hash/lease/contextchecks andactualunderwater appearance.

Checkpointcommitaftertooloutput savesboundedtracker/capture API+31fixture/
actualnative retry andprivateSSR tier2→0 source11fixtures/qualityband removal.
Rootno originalownerdesign staged, no shared/main/policy/defaults orremoteactions.
NewreducedfarTier2→1 probe4filesworkeractive/source-only, codepreservesexisting
geometry/jitter CODE notidenticalhit/sample positions; rootactualrender/cost
stillpending. Fullnativegoal15%/broadconfig/quality/support remainsactive.


Checkpoint4dfcdfc actual8files commit. LIVE80918 verifiedcurrenthandle
matchedenabledunderwater; rootsoleGPU. Nextpollsamehandle/log, no otherclient/
heavybuild/encoder; validate201actualWATER/noncollision/slots/drain/peaks/hashes
thencomparetargetedunderwater baseline/off/on. ReducedSSRfarprobe new4files
sourceworkeractive; rootreview/build afterfreeze, realglinttracequality before
cost/repeatedperformance. Statusconsolidatedtoactualcurrentevidence, no
uniformtransport/speculatedquality or15%completionclaim. Fullgoalactive.


AUTHORITATIVE lifecyclecorrection: write_stdin80918 returnedexit0 (terminal),
notrunning. Enablednativeunderwater201/201/0skips, allactualWATER/nonfull-
collision/slotidentity/hash checks passed; finallease0/settled201/bound≤2/queue≤1,
peakrecordedfromactualmanifest. Allnativeunderwatertriplet603 complete frames,
visualcomparisonstillpending. NoMinecraftclientlive now, rootsoleGPU. Previous
LIVEline superseded bythisactualhandle result; neverstartclientbasedonold
checkpointwithoutrevalidating. Nextimagecomparison thenreducedSSRprobe freeze/
source/runtimequality/cost. Goalactive, no fullquality/default/15%promotion.


Underwateroriginal/off/on now201/201/0skips each, all603WATER/noncollision/
hash/slotidentity/drainchecks passed. Enabledactualpeakpending2 exercisedtwo
leases (original/offpeaks1). Rootenabledframe100underwaterSnell/sky/water
inspectedwithoutgrossgeometryfailure; fulltemporal/pixelparity/multiscene
quality stillunproven. Allnativeoldfailuresretained, no fastershaderclaim.

ReducedSSRfar4filesfrozen; root12staticfixtures passed/built disabledSHA
73275ceb7a3f740bac6a1cc317a69719295b1141de222bcbe566dc9081a1ae98/enabled
636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83.
Exactlocalfar2→1, existingPERFORMANCE8/4/2 budgets, near/mid/roughraycount/
caller/projection/texture codeunchanged, samplepositions/hits/tolerance not
claimedidentical. FreshSSR-onlyoff/on source/copy/options prepared/pinned.
Rootlaunchreducedenablednative2FPS41glintslots/noDH/minimal/globaltrue/
NONE/noncollision/GPUtimingfalse, log
`dev/logs/photon-ssr-far-reduced-enabled-quality-r1.log`; profiles
`run-workloads/photon-ssr-far-reduced-quality-r1/coastalwater`.
Actualhandlefollowslaunchoutput, rootsoleGPU. Nextactual41contracts/hashes/
band/glint/trace quality beforeGPUperformance controls ordefaultdecision.
Fullgoalactive; main/shared/defaults/design stillreserved/unmodified.


Checkpoint409734a saves4newreducedSSRsource/provenance/test/doc files;12root
fixtures andactualbuilddone, runtimequalitypending. 4dfcdfc savedcapturetracker/
fullSSRtracequalitycontrol. Allworkerscurrentlyfrozen/completed; no maindesign/
sharedhelper/defaults staged, no remoteactions. LIVEexec3811 verifiedcurrent
private reducedSSRenabledglintnative2FPS41slots/noDH/minimal/globaltrue/
NONE/noncollision/GPUtimingfalse, log
`dev/logs/photon-ssr-far-reduced-enabled-quality-r1.log`. RootsoleGPU; nextpoll
samehandle andvalidate41contexts/hashes/traceglint/band removal. Ifquality
survives, serialGPU controls original/B15skip/fulltrace/reduced atidentical
resolution/sourceflags andbalanced repeats; costnotinferredfromsample counts.
No heavybuild/tests/encoder/secondclientwhilelive. Allunderwater603native
validframes+boundedlease runtimeproof retained, fullerimage/motion parity
stillpending. Fullordinary15%/broadconfig/quality/supportgoal remainsactive.


## Reduced SSR quality rendered; cost pilot live

3811 terminalexit0/41/41/0skips actualreducedguardnativeglint; allNONE/nonfull-
collision/hashchecks passed. Rootframe10broadbandgone/sunreflectionvisible,
boundedqualityoutcome supportsreducedfartrace experiment, notfulltransition/
motion/pixel parity orspeedup. Rawa52402d4…retained.

Rootnewpinnedfour-armSSRcostpreparer actual95325terminal, allcopiedcanonical
saves equal f8f3b866…a365, SourceArchiveoptions/control pins explicit. Controls
originalmodabsent, B15farSkip, fullTrace guard, reducedTrace guard, onlySSR
moduleactive patchedarms, globalenabled andgeneric32/96, protectlighting/
cachedskyfalse/analyticfalse unchanged. Fixedglintmidposex500.5/y68/z660.5/
87.380yaw/3.735pitch/time12500. Rootpilotlaunches4serialnative2976×1672/
minimal/noDH/45s warm25s capture/GPU1/16/fixedwetness0; log
`dev/logs/photon-ssr-cost-pilot-r1-runner.log`, profiles
`run-workloads/photon-ssr-cost-controls-r1/coastalwater`.
Actualhandle followslaunchoutput. RootsoleGPU, no otherclient/heavybuild/test/
encoderwhiletimed. Nextall4actualartifact/options/window/lighting/maps/PNG and
GPU/CPU frames, matchedrepeats ifuseful; qualitybadskip mustnotpromoteforcost.
Fullgoalactive/ordinary15%/broaderconfig/quality acceptance unfinished.


Checkpointf7f30d6 savesfour-armcostpreparer/cases andactualreducedquality result;
RootPythoncompile/scopeddiffcheck passed. LIVEexec71320 verifiedcurrenthandle:
original00160.686FPS andskip00261.172FPS completed; reduced003 currentlynext,
full004 stillpending. Do notinterpretpartialpilot asGPU/quality/performance
acceptance. RootsoleGPU, no heavycompiler/tests/encoder/secondclient. Nextpoll
samehandle/log/manifest, strict all4source/options/core/window/artifact/PNG and
GPU/CPU comparison; retainfailed/anomalousarms. Qualitybadskip remainsbad even
iffast. Balancedrepeat onlyafterusefulqualifiedcostsignal. Allsourceworkers
complete/frozen; main/shared/defaults/ownerdesign unchanged. Fullordinary15%/
broadconfig/quality/supportgoal active/unshrunk; no remoteactions.


## SSR cost pilot terminal and negative

71320 exit0/complete/all4accepted. Original/skip/reduced/full GPU15.0436/
15.0882/18.9037/18.3937ms; FPS60.686/61.172/49.497/50.883, threads7.5632/
7.5656/7.6709/7.5777. Reduced/full~25.66/22.27%slowerGPUthanoriginal, reduced
notfasterthanfull; qualitybadskipnotpromoted. No source/JVM/driver/hit/thermal
causeasserted. Allrawsamples/versions/options/classes/window/controls/PNGs
preserved. Ordinary15%gate stillunmet.

No gamecurrentlylive. Workerstack_analysisowns5dev/analyzer/test/newdoc files
explicitssr_cost mode, acceptsONLYexactB15/privatefull-enabled/privatereduced-
enabled pins andSSR-only/fixedconfig/world/pipeline/nativewindow; oldmodes
remainstrict. Rootreviews/freezes/compiles then actualwithinarchive ON policy/
OFF originaltracebudget controls, notcrossarchivecausality. No main/helpers/
shader/defaults changed. Fullgoalactive/unshrunk; allfailed/negative evidence
retained. Nextmeaningfulwork aftersourcefreeze; shaderqualitycompromise remains
experimental andnotacceptedperformancesupport.


LatestnegativeSSRcost evidencecommitted afteractualfullmatrixcomplete. No
Minecraftclientlive; rootsoleGPU. Sourceworker ssr_cost5files active, rootno
compiler untilfreeze. Lastreadonlyoriginalchat inspection confirmed "Plan
Photon shader LOD MVP" latestturnwasfreshhandoff; statusnotLoaded, ownership
remainsreserved. No message sent orassumedcommontransport workdone. Existing
CommonUniforms actuallyregistersonly7corefields(version/enabled/preset/near/
far/transition/debug), no per-effectfieldtransport; effects remainpackoptions
whereunconsumed. This is stillgapagainstbroadergoal, notcompletion ornewblocked
claimwhileindependentwork progresses. Next strictsourcepin/codefreeze/build/
same-JVM source-policycontrols, thenfurtherpack/configquality. Fullgoalactive.


Checkpoint057ed06 actualnegativecosttable committed. Currentrootruntimeprobe
allterminal/noMinecraftclient, sourceworkerstack_analysis activelyowns5files
pinnedssr_costmodality (nomain/sharedshader edits). Rootnextconfirmfreeze,
reviewallstrictguards/prereqs/pins/testcoverage, rootPythonfixtures/build/devjar
exclusion thenfreshwithinarcSSR-only global ON/OFFnativecontrols. Allarchive
fingerprints/sourcequalityfailures/negativeperformance preserved; no goal
completion/shrinking orblockedstate whileconcrete independentprogress possible.
Fullgoal active; broadercoreconsumer features mayuseexisting7fields but no
per-effecttransport existsyet, originalchatownershipreserved untilconcrete
handoff. No remotehost/message/push/publication authorization inferred.


## Pinned SSR control integrated; reduced session live

Root verified prior game jobs terminal and worker freezes. The five-file
ssr_cost integration passed 38 in-process and 24 stack fixtures. Gradle build
and compileDevClientJava succeeded; production JAR contains zero dev classes
or dev mixin entries. Exact three archive/member/metadata/source-policy guards
remain distinct from physical trace work or linked uniform observations.

Fresh stopped reduced profile clone:
run-workloads/photon-ssr-inprocess-reduced-r1/coastalwater/ssr-reduced.
Source/copied world SHA256:
9d6a03387745991f560253fa1908bddce45259bb8af2f21c501697d33e3e3f9e.
Root owns LIVE exec37106: runClientLodOn, minimal/noDH, ssr_cost,
OFF/ON/OFF/ON, 45s initial warmup/10s settle/25s capture, native2976x1672,
GPU1/16/wetness0/fixed sun-glint pose. Reduced archive SHA256:
636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83.
Log dev/logs/photon-ssr-inprocess-reduced-r1.log. Stop only this owned job via
its exec handle if needed. No other client/build/encoder during timing.
Next strict analyzer and phase images; retain all failed/negative evidence.

stack_analysis completed/frozen. pack_research completed Complementary source
plan and now owns ONLY scripts/prepare_pack_experiment.py,
scripts/run_proof_matrix.py, NEW tests/test_complementary_profiles.py and
its docs/COMPLEMENTARY_RUNTIME_PLAN.md for original/native options preparation.
No compiler/client/derivative work. Root sole GPU. Main/config/helpers still
reserved; one optional human ownership question pending, silence grants nothing.
Full goal active, ordinary >=15% and broader support/configuration gates unmet.


## SSR reduced forward terminal; reverse live

exec37106 exited0, all4 phases strict accepted. Session c1716926-bb04-4a2d-89df-
02b3bf34c8c5 OFF/ON/OFF/ON GPU15.4489/15.1246/15.2039/17.2996ms,
FPS59.963/61.205/60.815/53.997, thread6.9566/6.7892/6.7278/6.7025.
Adjacent GPU reduction +2.10%/-13.78%, OFF drift1.59% reduction and ON drift
14.38% increase. Mixed/no stable improvement, no cause claimed. All4 PNG hashes,
actual source/member metadata/options/window/config/scene/epoch/restore checks
passed. Phase2 sunreflection visible/broad bright band absent, limited pose view.
Raw dev/logs/photon-ssr-inprocess-reduced-r1-{benchmark,analysis}.json/.log.

Root sole GPU now LIVEexec93853 reverse ON/OFF/ON/OFF, same flags/timings and
archive, fresh profile run-workloads/photon-ssr-inprocess-reduced-reverse-r2/
coastalwater/ssr-reduced copied same stoppedsource world9d6a0338...e3e3f9e.
Log dev/logs/photon-ssr-inprocess-reduced-reverse-r2.log. No heavybuild/tests/
encoder/otherclient. Next poll/analyze separately, retain mixed/negative results,
then review Complementary preparation worker freeze before original runtime.
Full goal remains active; original main/config/shared design ownership preserved.


## Reverse SSR terminal; original Complementary smoke live

exec93853 exited0, all4 phases strict accepted. Reverse ON/OFF/ON/OFF GPU
15.8412/15.7409/15.7564/15.8719ms, FPS58.203/58.804/58.948/58.477,
thread6.9644/6.8038/6.6908/6.6253. GPU reductions-0.637%/+0.728%, flat;
forward/reverse establishes no stable reducedtrace gain. Rawbenchmark/analysis/
log retained under dev/logs/photon-ssr-inprocess-reduced-reverse-r2-*.

pack_research frozen/completed4files: explicit untouched original Complementary
family, three cases modabsent, exact-release-only common.glsl layout. Root7new+
19prior matrix fixtures pass; actual fresh preparation completed all3profiles at
run-workloads/complementary-native-controls-r1/coastalwater. Original archive
fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38 unchanged;
HIGH source defaults, native water sky0/Potato1 separatequality controls/noDH.
Root sole GPU now LIVEexec90231 runner complementary-high, native2976x1672,
minimal/noDH/35warm15capture/GPU1/16/wetness0, --skip-build --freeze-dev-classes.
Log dev/logs/complementary-original-smoke-r1-runner.log; runner result root
 dev/logs/complementary-original-smoke-r1. No otherclient/build/encoder. Next
actual compile/link/render/options/artifact and retainedimage inspection; keep
first failure before narrowly justified native fallback retry. Main/shared/
originalowned design untouched, no remoteaction, fullgoal stillactive/unshrunk.


## Complementary original rendered; native water cost pilot live

exec90231 exit0, original HIGH accepted at native2976x1672/minimal/noDH/
mainmodabsent. Actual Iris nofallback and option mapstyle1/RP1/water2/block3/
detail2/cloud2/shadow192/coloredlighting0/worldreflection-1, detailed24/24.
GPUmean14.0710/median14.0836/p9514.6663ms, FPS64.242, thread7.1419ms.
Root inspected actual final PNG shoreline/trees/water/reflected objects/clouds/
sunsetlighting. Uniform warning newbiome/End expressions retained; no claims
for those dimensions/features/DH/motion/general support. Original archive intact.
Raw dev/logs/complementary-original-smoke-r1 complete.

Root prepared fresh equal stopped-save native controls at
run-workloads/complementary-native-controls-r2/coastalwater. Root soleGPU
LIVEexec38518 matrix3arms complementary-high/water-sky/water-potato, minimal/
noDH/mainabsent, native2976x1672/35warm20capture/GPU1/16/wetness0,
--skip-build --freeze-dev-classes, repeat1 (pilot only).
Log dev/logs/complementary-native-water-pilot-r1-runner.log; results sameprefix
folder. No otherclient/heavybuild/tests/encoder. Next actual three terminal
contracts, costs, visual tradeoff before meaningful repeated/movingcamera work.
Allworkers completed/frozen; shared main/config/helper ownership stillreserved.
Fullgoalactive: ordinary15%, broadconfig/adapterquality/support remainunmet.
