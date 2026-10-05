# Sustained performance and compatibility development

Created: 2026-10-04. Status: active.

## Authorization and objective

The user authorized sustained agent-team development with broad implementation
freedom, keeping Photon first, NeoForge and the current Minecraft target. Improve
performance and the mod, benchmark with/without performance mods, and expand to
other popular shader packs only after meaningful Photon gains are established.
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
5. Only after gate 4, choose and pin another popular, licensable shader based on
   current evidence. Add explicit integration with its own disabled/original,
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
