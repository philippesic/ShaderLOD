# User and agent lessons

Record only demonstrated coordination problems or corrected misunderstandings.
Use context, evidence, agent contributions, and a practical lesson. Keep the
record respectful; uncertainty and product disagreements are not confirmed
user errors.

## Choose the target ecosystem before inheriting build versions

On 2026-10-03, the agent initially adopted the neighboring mod's Minecraft
26.1.2 target because the runtime dependencies existed. The user then clarified
that broad adoption should determine the first target. Current pack releases
and version-download evidence supported NeoForge 1.21.1, so setup was migrated
before acceptance. Reuse build infrastructure independently of its version pins;
check the audience criterion before selecting dependencies. Compatibility alone
does not establish popularity. This was an agent assumption corrected by the
user, not a user mistake.

## Separate a visual preview from a performance sanity check

The first side-by-side scene demonstrated controls and quality zones, but its
sparse geometry, substantial sky, and Photon early-outs made it a weak PCF
performance workload. When the user reported equal/slightly lower LOD FPS,
the source audit found little medium-tier work to remove. Explain effect scope
and actual baseline work before presenting a performance comparison. Label the
right-hand client accurately: it retained ShaderLOD with LOD disabled after the
user requested restoring the previous windows; the mod-absent baseline was not
completed. Keep live windows available while investigating read-only.

## Prioritize visual value over the number of effects modified

On2026-10-04 the user observed missing shadows on distant trees and rejected
the aggressive profile's roughly0.5ms saving as poor value. The agent had noticed
flatter distant foliage in captures but emphasized implemented effects and timing
results without clearly flagging that visual cost. Far AO was bypassed despite
its whole pass costing only0.057ms in the earlier profile; screen-space shadows
also became extremely sparse. Preserve important low-frequency lighting cues,
use effect-specific importance criteria, and evaluate quality alongside time.
Passing compilation/runtime checks and measuring a gain do not establish a
worthwhile optimization or a shipping-quality preset.

## Verify effective and temporal shader inputs

During the sustained goal on 2026-10-04, the initial repeated runner compared
option-file bytes. Iris normalized those files by removing defaults and adding
comments, so the guard rejected a semantically unchanged baseline. The benchmark
worker corrected verification to read loaded Iris option values and retain raw
hashes as separate evidence. Preserve failed captures instead of silently
relabeling them as performance results.

Further source auditing by the shader worker found that fixed rain/day time do
not fully specify Photon clouds: Iris smooths wetness and Photon uses it for
weather humidity and wind. Fresh clear saves switched to rain can initialize
differently from reused rainy saves. Actual wetness was not logged in the first
36-run matrix, so the proposed explanation remains unverified in those captures.
Record effective options and relevant temporal uniforms, inspect initialization,
and verify steady inputs before claiming a matched cloud workload. Frame count
alone and a fixed warmup duration do not prove that every shader input settled.


## Restarting completed native workers

During the frustum replay handoff, the coordinator sent a CPU-window release with
send_message after the worker had completed its turn. That queued information but
did not start execution. Checking process/output state exposed the idle handoff;
followup_task started the authorized replay. Use followup_task for new execution
by completed workers, and send_message for steering an already-running worker.
This was a coordinator error, not a missing user authorization or user mistake.


## 2026-10-05: evidence-file assumptions corrected

Fresh performance root initially required every shaderoption file to persist.
Iris deleted the original MakeUp all-default file after a successful smoke, so
the next pilot failed before launching. Root corrected missing-default
normalization against the retained preparationcontrols and actual loaded Iris
options, preserved the failed log and corrected the prematurely planned live
checkpoint to the real session45311. This was an agent/harness assumption, not
a shader failure or user mistake.

Stackanalysis worker caught different raw API Base hashes despite same version.
Packresearch traced them to Iris/Sodium nested copies and proved identical
classes/resources except a NeoForge minimum bound satisfied by pinned21.1.253.
Root retained strict rejection and required a separate exact-hash/runtime
qualified mode. Same-version strings alone are insufficient artifact evidence.


## 2026-10-05: automatic screenshot contamination

Root inspected the first native effects-mode control log and found the older
ComparisonClient automatic landscape screenshot at1800ticks during phase2.
The new benchmark already schedules its own phase-boundary screenshots, so
that inherited capture added unplanned readback/write work inside a timed
phase. Root retained/qualifiedr1 and gated old automatic captures whenever
benchmark/workload capture owns the client. Clean forward/reverse repetitions
are required before treating a small effect as reliable. This is an agent
instrumentation correction, not a user mistake.


## 2026-10-05: camera path direction corrected

Fresh root first chose z680.5→720.5 as forwardshoreline motion at yaw−180.
Actual captured finalframe went throughterrain. Minecraft yaw−180 faces negativeZ;
root corrected a separate immutable r2request towardz640.5. The first all41
verified images remain harness/trajectory evidence only, not approved water
quality. Cameraquality worker's actual-camera metadata exposed the mismatch;
this was root's route assumption, not a user or shader failure.


## Sampler diagnostic resolution provenance

Root's carried summary described sampler r2 as native2976×1672. The actual
retained workload report and launch log establish1488×836 in
`run-workloads/cloud-sampler-state-probe/forest/secondary-base`. The cloud
worker repeated the supplied context; root owns the mistaken assumption.
Exact report/filter data remain valid, but native literalpilot extrapolated
resolution and did not itself record samplerstate. Correct the qualifier and
capture native-size samplerstate before relying on exactnative-profile evidence.
Use actual window/input reports rather than the source profile's prior resolution.


## Camera-document ownership overlap

Root appended a factual failedcapture checkpoint to CAMERA_QUALITY_HARNESS.md
while the camera worker owned that document for encoder repair. Root notified
the worker to preserve the appendix and stopped edits to its three owned files
until freeze. No source/oldrawimage change resulted. Keep worker-owned docs
under the same edit ownership as their code during active work; root milestone
notes belong in STATUS/activegoal until handoff.


## Large tool response can hide completed side effects

Root batched three20MBuncompressednativePNG images witha clientlaunch inone
exec. The combined79.7MBresponse exceededthe64MiBIPC limit andhidthelaunch
handle, although theclientanddocwrites hadrun. Rootverified exactGradlePID/
runDirectory/logs, avoidedduplicate launch, inspectedimagesonepercall andused
process/log completion asauthoritative. Keep largeimagesseparatefrommutations
and do not treat atransportfailure asproof that actiondidnotoccur.


## Read-only SQLite connection is not a file-immutable inspection

New native terrain preparer's metadata SELECT used mode=ro on a stopped WAL-mode
DH database after fingerprinting the source. SQLite created empty-wal and32KiB-
shm files, causing strict copied-save rejection. Root reproduced this on an
isolated realDBcopy (21845rows) and found source/copy currentfiles identical.
The planner/fixture owner had tested synthetic metadata but notthisactual WAL
side effect. Repairmetadata inspection on an isolated database+WAL snapshot,
preserve real sourcefiles andstrict worldhash guards; do notignore mismatch or
assume mode=ro prevents filesystem side effects. Failedpreparationr1 retained.


## Prepared time needs client/server synchronization before quality acquisition

Root's firstnewglint scene12500 failedbeforePNG because qualityacquisition
sawthe newpose andfrozenserver butsnapshotted theclient's olddaytime7000.
Separate control/player/time packets canarriveondifferentticks; earlier7000
scenes hidthisassumption. Rootretained575eb8ba…zero-framefailure and addedan
acquisition barrier requiringclient dimension/daytime equalobserved frozen
server scene beforeinitialfingerprint. Afteracquisition alltime/config guards
staystrict; no toleratedcapture change or worldclock override added.


## Local camera rotation differs from position interpolation

The camera-quality worker's new Complementary validator, and root's source
review, assumed yaw/pitch followed observed partial-tick path seconds. The
original41-frame runtime exposed a false rejection up to0.185degrees. Pinned
Minecraft1.21.1/NeoForge21.1.253 LocalPlayer view methods return current rotation
when not riding; Camera.setup interpolates position/eye height separately.
Root verified all41 angles against motion_tick/20 (maximum error0.000007degrees),
corrected the validator's angle clock and added partial-clock/player-angle checks
and a regression fixture. Seven tests and the original actual sequence pass;
the tolerance and raw captures were not changed. This was an agent assumption,
not a user error. Position and rotation clocks must be checked independently.


## Private source gates need end-to-end comparison coverage

The new Photon reduced-effects preparer and root fixtures preserved upstream
options and qualified the private archive, but did not exercise the final matrix
analysis. All six actual captures passed individual checks; aggregation rejected
the added private SSR gate as an upstream option difference. Root retained the
failed raw manifest, revalidated every capture, and added an exact-pin/member/
metadata/family/gate exception that annotates a source intervention. Other quality
mismatches still reject; four new fixtures and19 prior tests passed. Recovery
analysis is a separate derived manifest with original hash/status retained.
This was an agent integration gap, not a user error or a reason to erase failure.


## Weather controls need a real pack consumer

Root reused the Photon fixed-wetness0 smoke request for Miniature. The new shader
compiled/rendered and exposed all7core fields, but temporal validation correctly
rejected missing wetness supplier observations. The frozen Miniature shader
sources contain no wetness token. Root preserved the rejected request/report,
kept the guard strict and made a fresh normal-weather request with actual clear
scene weather. A JVM control is not proof that each pack consumes that uniform.
This was a root assumption, not a user mistake or shader compilation failure.


## Protection must use one value per effect snapshot

Root's first additive per-effect config snapshot read the lighting-protection
flag twice. `effect_api` review identified that a reload between those reads
could pair protected policy metadata with reduced transmitted ratios. Root
captured the flag once and used that same value for policy and transport, then
the integrated build and 47 Java tests passed. No runtime occurrence of that
race was observed; this was a source-level integration correction contributed
by the reviewer, not a user mistake. Frame caching does not by itself establish
an atomic transaction across all independently editable config fields.


## Quiet GPU windows include source-test processes

During the first general-LOD AO pilot, root prohibited builds/compilers/encoders
but allowed lightweight source/math fixtures. `effect_api` executed an eight-test
binary32 source suite lasting about4.6seconds while the serial pilot was live.
Its import-cache mtime is2026-10-05T20:16:49Z, but exact suite start/end and capture
overlap are not established. Root corrected the team boundary to forbid test
processes during timed windows and instructed `ao_runtime` to retain the pilot
with possible external CPU activity qualified. No interference or effect on
GPU/FPS is proven, and the data must not be promoted as a quiet causal comparison.
This was root's coordination boundary, not a user error or worker violation.


## GLSL guards must also satisfy Iris option discovery

The first density diagnostic passed our archive-default/source fixtures but failed
actual Iris option validation: its requested switch was absent from the effective
option map. `lod_opportunities` inspected the pinned Iris OptionAnnotatedSource
bytecode: option references recognize standalone #ifdef/#ifndef, while combined
#if defined guards are not discovered by that path. Root retained the failed r1
reports/archive and assigned nested guards plus an actual pinned-parser fixture
before retry. Valid GLSL and our defaults parser do not prove Iris recognizes a
shader-pack option. This was an agent integration-test gap, not a user error.


## Arm focus automation must precede the first client

In density r1, `ao_runtime` reached the first manual focus action after capture
started and correctly skipped changing focus during measurement. The original
capture therefore remained focused while disabled was unfocused. The operator's
subsequent watcher fixed only later warmups. Root retained both independent
rejections (missing Iris gate and focus mismatch); no relabeling or gain claim.
The retry must arm/acknowledge the warmup-only watcher before the runner launches,
then validate actual report focus for every arm. This was the runtime agent's
sequencing correction, not a user mistake or proof that focus caused slower FPS.


## Parallel teams must own independent implementation tracks

The user corrected root's general-LOD workflow: several workers had supported
one shader experiment at a time, leaving most implementation serial. Parallel
research/documents around one candidate did not satisfy the requested breadth.
Root is assigning independent code tracks for distant lighting, reflections,
procedural detail, volumetric sampling and mod interfaces, with distinct files
or isolated worktrees. Only interactive clients/GPU measurements and heavy local
checks during capture need serialization; source development continues in parallel.
This was root's coordination misunderstanding, not a user error.


## Experimental serial results are not the user's fullscreen comparison

The user reported about10% faster LOD in an earlier fullscreen comparison and
challenged root's current-standing regression table. That table used one earlier
experimental reduced-SSR five-effect candidate versus original Photon, in serial
native-size windowed captures. It was not a controlled repetition of the user's
same-pack fullscreen setup. Root clarified source/order/window differences and
the capture method; CPU load/clocks remain plausible unisolated influences, not a
confirmed cause or a user mistake. Future performance summaries must name the
candidate and comparator and separate elapsed GPU time from FPS. Dedicated quiet
measurements should match the requested fullscreen mode and use balanced repeats.

## Check each effect's protection flag before preparing controls

Root initially told the lighting-profile worker that OpaqueLighting ADAPTIVE
controls required disabling preserveSalientLighting. Independent `lod_quality`
inspection found OPAQUE_LIGHTING is declared with salientLighting=false, so the
producer's normalization does not cap this effect. Root corrected the assignment:
retain protection=true, explicitly request the lighting ratios, and distinguish
requested settings from actual transport. AO/shadow designated salient effects
retain their protection. This was an agent assumption corrected by source review,
not a user error; it does not establish runtime lighting consumption.

## Keep the LOD mechanism in the shared system

The user clarified that the product is a general, extensible LOD system and
questioned Photon-specific optimization work. Root had allowed reflection-cache
and projection tuning, plus standalone pack prototypes, to dominate part of the
implementation queue. Root stopped the planned standalone 4 × 4 pack before
implementation and redirected the team to shared policies and adapter contracts.
Photon must demonstrate those common mechanisms as a consumer. This is a
coordination correction by the agent, not a user mistake. Source-tested unused
helpers and pack micro-optimizations are not evidence of the system's performance.

## Name the fingerprint algorithm at runtime handoff

Root supplied the binding build's sorted class-tree digest to a fresh runtime
worker, while the existing runtime utility hashes a differently serialized
member map. The worker correctly held launch on the mismatch. Root independently
compared all 75 main and 107 dev member hashes with the audit and disk; they were
identical. Future runtime handoffs use `development_fingerprints()` and record
its exact inventory, rather than treating differently defined digests as a file
change. This was a parent handoff mismatch, not a renderer or user failure.

## Loaded option values do not prove version-gated activation

The spatial adapter worker initially expected a loaded separate-entity option to
remove a sampler. Actual pinned preprocessing showed its macro is inside a
Minecraft-version condition that is false for the current runtime. The worker
removed the forced test define, retained the sampler-limit failure and proposed
an explicit pass decomposition preserving the original sky operation. Future
adapter eligibility must verify actual version-conditioned shader source, rather
than infer activation from an option map. This was corrected by agent source
verification; no user feature was disabled to make the check pass.


## Complete family joins before accepting a copied consumer — October13, 2026

The Unbound NeoForge26.2 feature owner ported the checker command/artifact branches
but omitted the final native-path/hash/count/style branches. Root reviewed and
adopted that partial copy; real producer preflight then selected MakeUp native100
and correctly refused before client launch. Root also supplied a stale archive
path, and the owner initially assumed style3 rather than the recorded style4.
The actual archive/map and failed receipts are retained. The repaired native
tuple block was exercised against actual producer inputs with wrong count/archive
negatives, and a fresh producer plus whole preflight passed. Keep field identity
through the actual consumer tail, not only the early selector; no fabricated
exports or weakened native gates.

The subsequent consumer completed four rendering/control stages but its watcher
failed a3sec lsof directory probe. Exact stop/release and the raw complete report
are retained. Read-only strict postflight supports component claims, not a whole
operation PASS. A probe timeout is not evidence that the process stopped.

Root also caught pre-existing checker edits in its local commit and narrowed the
commit to the port's exact scoped patches, preserving the working bytes and other
edits. Inspect both current preimages and the staged diff before committing.

## October13: verify the missing product join before copying compatibility machinery

Root initially framed NeoForge26.3 as a continuation of the NeoForge26.2 input
compatibility port. The explorer proposed production source certificates and
a draw observer after finding changed Renderpearl APIs. Root then required a
focused check of the existing public Apply and uniform consumer; those already
carry all current shader families. The explorer withdrew the unnecessary
production proposal before any edits.

Correction: trace the actual input-to-consumer-to-recovery path first. A working
version's diagnostic or compatibility mechanism does not establish a missing
product feature on another version. Reuse existing production joins and change
only the acceptance boundary needed for the current optimization port.
