# Photon fine-density cost-control runtime plan

2026-10-05 source-only checkpoint. Root owns client/GPU/build. This runtime
owner initially owned only this document; root subsequently authorized the
small shared-copy refactor and density wrapper/tests below. No real archive,
profile/save, config, client, compiler or timing changes were made by this owner
in this wave. The AO pilot
is terminal and is not repeated. Builder/manifest/test ownership belongs to
`lod_opportunities`; source freeze and root's fresh runtime handoff are required
before execution.

## Bounded question and reviewed source contract

Measure whether main-pass cumulus fine-density sampling is worth implementing
as projected procedural-detail LOD. The diagnostic replaces the fine
`worley_1` 3D texture value with declared 0.5. Coarse density, literal ray
counts/extents/seeds, other passes and cloud reconstruction remain original.
The replacement changes nonlinear density, occupancy, transmittance and early
exits. A faster result is a quality-changing source intervention, not an
isolated fetch budget, calibrated mean-opacity approximation, quality-equivalent
LOD gain or progress toward the accepted 50% throughput target. A flat or
regressive first result ends this candidate before calibration work.

The worker and runtime plan agree on:

| Item | Contract |
| --- | --- |
| Builder | `scripts/build_photon_density_detail_probe.py` |
| Source manifest | `patches/photon-experiments/density-detail-probe.json` |
| Original archive | Photon v1.3b, SHA-256 `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` |
| Private archive | `build/Photon-DensityDetailCostControl-disabled.zip` |
| Metadata member | `shaderlod-density-detail-probe.json` |
| Modified members | `shaders/include/sky/clouds/cumulus.glsl`, `shaders/settings.glsl` only |
| Private gate | `SHADERLOD_DENSITY_DETAIL_COST_CONTROL`, default off |
| Activation | gate AND PROGRAM_DEFERRED1 AND core version 2 AND global enabled exactly 1 |
| Diagnostic value | 0.5, `quality_changing_cost_control` |
| Core source consumption | version/global only under selected gate; no effect-extension consumer |
| Fallback | original fine fetch for gate off/other programs/unknown or absent core/global off |

Qualify the private archive against the worker's frozen manifest SHA-256 and
its recomputed `patch_members(..., 'disabled')`, then compare every upstream
member. Require exactly two changed files, the one metadata extra member,
unchanged original LICENSE and no missing/unsafe/duplicate/additional files.
Check base pin, manifest/member/license hashes, default-off gate and all frozen
metadata fields, including diagnostic/quality/program/activation/physical caveats.
Compute the resulting archive
SHA-256 after source qualification and preserve it throughout the run. Do not
accept an arbitrary archive path/hash list. For the rejected r1 attempt, the worker froze manifest SHA-256
`5b66b98cf065857d4df66e0ebbdcfbec281e619e8cde85e8dc58e44fc46fb1bd`,
patched cumulus member
`e64f2c71ce370203ab506f14e54ff64230013cf0f7eef5fe5e56e3ad2ae25db9`
and disabled settings member
`18914450d1be097f7fd2f1cb8c620ff2338200a4ea61a7902d140a3f8ec4b7ad`.
Root assembled the actual default-off archive at SHA-256
`0c0ac07a08a3c1e2c58658aabea95a564cc425795ff91c6a6b77bc0d8f2287d6`.
These are retained r1 source/assembly evidence, not current corrected gate or a
measured saving. The corrected source freeze is recorded below; a fresh corrected
archive and runtime handoff remain pending.

## Existing coastal source and smallest control

Use the stopped original profile
`run-workloads/photon-ssr-cost-controls-r1/coastalwater/high`. Its source
report SHA-256 is
`9524f3fdc9dec30f59a03704ad45f9cffa84b2058fe2ed48ea6a3095c31ba252`;
effective option SHA-256 is
`ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669`.
This exact report completed a minimal/noDH, 25-second original control at
2976×1672, requested/effective 24 chunks, with fixed wetness 0. The inspected
pose is x500.5/y68/z660.5, yaw87.38/pitch3.735, time12500, clear weather,
Overworld, PERFORMANCE mod preset. Its saved native final PNG was viewed
resized to 2048×1151: a prominent sunset cloud field fills the sky above the
shoreline. This is actual cloud-visible scene evidence. The pose/source are
previously inspected; the procedural density/cloud shape is not calibrated.
Do not substitute the forest effective option hash or force a different preset.

Hold source options: CLOUDS_TEMPORAL_UPSCALING=4, CLOUDS_SCALE=10.0,
cumulus primary horizon/zenith 40/20, lighting/ambient 6/2, precomputed local
coverage true, cumulus detail strength/density/coverage 1.00, cloud shadows
false, colored lights false and TAAU false. Preserve all other native options,
including water/reflections, rather than silently disabling competing work.
The contribution of individual visible layers is not measured by this still;
the source's selected main cumulus path and actual driver render must still
be checked in the new run.

Three fresh stopped clones retain a mod-absent reference with only two private
arms, all handled by the existing runner labels:

| Runner case | Pack | Mod | Private option | Global |
| --- | --- | --- | --- | --- |
| water-high | exact original Photon | absent | absent | false |
| water-patched-off | default-off private source | present | true | false |
| water-adaptive | same private source | present | true | true |

Patched native option sidecars are equal, and their fresh configs differ only
in global enable. Other CPU/rendering/terrain experiments are off; effects
extension config defaults INHERIT/unity/protected lighting. This same-source
global OFF/ON driver and cost control is the smallest useful intervention;
the original reference also exposes baseline-to-patched startup/drift changes.
One serial block is a candidate kill test, not a repeated acceptance design.
No second scene, reverse order, sampler profiling project or calibrated path
starts without root's result review and new bounded scope.

## Minimal preparation reuse source freeze

The existing AO preparer already owns the useful invariant code: fresh/ignored
output validation, live-client rejection, all-source stopped-save locks,
canonical equal-world copies, source/provenance hash guards, fresh noDH removal,
source history preservation, per-arm config/pack setup and failure retention.
Its AO qualifier, private gate, fixed manifest path and GTAO requirements are
not valid density-control identities. Reusing it by monkeypatching module
globals would silently weaken pins and is rejected.

Root authorized and this owner implemented explicit parameters in the existing
copy orchestration for the read-only source inspector, experiment identity,
preparation marker name and qualification manifest key. Source manifest identity
comes from the qualified inspector record. Defaults preserve the AO contract.
The inspector returns the existing qualified scene record shape; the locked
copy loop remains one implementation and validates/fingerprints its source
record before and after copying. A density wrapper supplies a separately
pinned density archive/source inspector and cloud option requirements, then
delegates the cloning operation. This adds explicit arguments and a pinned
qualifier wrapper; no second copying framework,
universal patch registry, globals substitution or timing-harness edit.

New `scripts/prepare_photon_density_detail_profiles.py` reuses `archive_contents`, `archive_defaults`,
`filter_effective_options`, `normalize_requested_options`, scene/control and
runtime-lock validation primitives. Its dedicated source contract replaces the
AO-specific source test, not the common upstream option parity checks. Both
private arms write the same true density gate. Preserve the exact original
archive/mod-absent baseline and the complete actual member diff.

The unchanged AO test module passed all seven fixtures after the refactor.
Five new `tests/test_photon_density_detail_profiles.py` fixtures passed, covering
fresh ignored coastal plans, full actual upstream/member/metadata qualification
in a temporary source-fixture ZIP and read-only original coastal inspection,
rejected member/metadata contamination, exact cloud settings/gate parity,
viewport/scene rejection and canonical tiny stopped-save copies through the
shared callback with density-specific markers/manifest identity. Source fixtures
are deleted and no production archive or real-world preparation is performed by
these tests. No Java, runtime classes, timing harness or shared shader was changed.

```sh
python3 -m unittest discover -s tests -p test_photon_ao_reuse_profiles.py -v
python3 -m unittest discover -s tests -p test_photon_density_detail_profiles.py -v
python3 scripts/prepare_photon_density_detail_profiles.py \
  --output run-workloads/photon-density-detail-coast-pilot-r2 --dry-run
# After a fresh root runtime handoff only:
python3 scripts/prepare_photon_density_detail_profiles.py \
  --output run-workloads/photon-density-detail-coast-pilot-r2
```

Preparation should reserve a fresh ignored root such as
`run-workloads/photon-density-detail-coast-pilot-r1`; record canonical world and
source/copied config/resource/pack/option/control inputs, frozen source revision,
runtime lock, dev/main class/resource trees and actual artifact hashes. Hold
the stopped source lock through source qualification and all equal clone checks.
Keep prior reports/images/worlds intact. Archive metadata alone is not driver
compilation or shader activation proof.

## Fixed focus, quiet timing and planned runner command

Choose **visible, non-iconified and continuously unfocused** during capture for
all three arms. Arm and acknowledge a bounded focus watcher **before starting
the runner**, without creating its output directory. After each new client has
applied its camera and entered the 45-second warmup, it brings Codex to the foreground exactly once
using the native app activation capability (or `/usr/bin/open -a Codex` on this
recorded Mac). Do this early in warmup, never during capture. Leave the game
visible and do not touch game/UI focus again until its timed window ends.
The existing WindowStateTracker must observe only focused=false throughout
every capture, no unknown/focus changes, visible=true/non-iconified, identical
1488×836 logical/2976×1672 framebuffer and fixed pacing. If the operator cannot
establish that policy before capture, preserve and reject that attempt. Do not
repair a mismatched focus sample by relabeling the manifest or inferring that
pause-on-lost-focus=false makes focus irrelevant.

Root pauses all other worker test processes, builds, archive assembly, encoders,
image reads/processing and GPU jobs before the first timed launch. Source text
reads and brief logs/status/checkpoint updates may continue. The operator does
only the existing runner, warmup focus setup and minimal log/status monitoring
during this pilot. Actual focus comes from the report, not the activation
command's success. Capture-start/end timestamps and any external work are
retained. Configured pacing is not observed host scheduling/thermal equivalence.

The following is the planned normal WorkloadSession command after preparation,
final source/Java freeze and explicit root GPU handoff; it is not executed:

```sh
python3 scripts/run_proof_matrix.py water-high water-patched-off water-adaptive \
  --profiles-root run-workloads/photon-density-detail-coast-pilot-r2 \
  --stacks minimal --dh absent --repeats 1 --warmup 45 --duration 25 \
  --sample-interval 5 --window-scale 2 --fixed-wetness 0 \
  --skip-build --freeze-dev-classes --tag photon-density-detail-coast-pilot-r2
```

Per-pass and CPU layer timing are false as this runner already specifies.
Record exact exec/PID/client/profile/process-group stop handles in this document
before continuing a live run. Runtime version/artifact expectations remain
Minecraft 1.21.1/NeoForge 21.1.253/Java 21/Iris 1.8.12-local/Sodium 0.6.13,
Photon v1.3b and actual noDH. Apple M3 Max/GL 4.1 Metal 91.7 is the last recorded
driver; new logs must confirm it. Root freezes all class/resource trees and the
runner checks them before/after each serial JVM; no compiler/build is allowed.

## Terminal checks and decision boundary

After all timed windows exit, validate the existing UUID/request/options/control,
loaded mod/dependency, fixed wetness, CPU/GPU and fresh PNG contracts. Explicitly
check native/radius/pose/time/weather at both boundaries, quiet-work qualification,
the fixed focus/pacing policy, fully equal patched option maps and unchanged
upstream options relative to original. Re-qualify source/member/metadata and
record generated secondary effects config startup/defaults separately.
The existing WorkloadSession has no core/extension uniform readback or physical
fetch counter: option/global/bridge registration and source gating are intended
activation evidence, not an observed texture-call count or density-field proof.
Title-screen success alone is insufficient; require actual in-world rendering.

Inspect original/off/on final images only after timing stops. Check near/far
cloud occupancy, edge shape, brightness/transmittance, sun halo, reflected light,
shoreline/water-glint and any unexpected other-pass change. Intentional density
differences must be reported, never treated as tolerated production quality.
Static scalar/source checks and unchanged counters do not establish driver
arithmetic/opacity equivalence. One still cannot establish movement/wind/reset
or temporal quality. DH compatibility/terrain rendering is a separate future
check, not a noDH result.

Report total-frame mean/median/tails, GPU intervals, CPU wall/thread timings,
both OFF/ON and original/ON contrasts and all limitations. Do not call a faster
quality-changing diagnostic the fine texture's isolated cost or an accepted
shader speedup. If flat/regressive, stop. If useful, root may authorize a quiet
balanced repeat, then separate texture/sampler/source/occupancy qualification
and calibrated far/detail design; no automatic calibration investment follows.
Preserve reports/captures and verify owned clients are stopped before returning
GPU ownership. No runtime process is owned at this source-only checkpoint.

## Active coastal runtime handoff

Root explicitly transferred sole client/GPU ownership to `/root/ao_runtime`
after independently passing AO 7/density 5 fixtures, rechecking no live clients,
and freezing the density archive/source pins above. No Java/class changes occurred
after the prior 47 Java tests/build/dev compile. Root and other workers stop
all tests, builds, shader assembly, encoders and image processing until terminal
completion. Operator records are appended here; no result is inferred yet.

Actual preparation started in exec session `77224`:

```sh
python3 scripts/prepare_photon_density_detail_profiles.py \
  --output run-workloads/photon-density-detail-coast-pilot-r1
```

No Minecraft launch has occurred yet. Preparation is stopped only through this
exact session handle if necessary; existing outputs/sources are preserved.

Preparation exec `77224` exited 0. All source/copied worlds were canonical SHA-256
`89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46`;
private pack/manifest matched the frozen pins above and profile mod directories
were physically empty. Source report/config/option/provenance inputs remained
unchanged. Exact runtime-lock SHA-256 remains
`7cca986f9580a32861d33fa7f6174b58babd207a119962d8d323c5abe6728a7a`.

The exact three-arm command above started in exec session `84020`, beginning
`water-high` at `run-workloads/photon-density-detail-coast-pilot-r1/coastalwater/high`.
Raw artifacts are under `dev/logs/photon-density-detail-coast-pilot-r1/`.
The existing runner reserves its matrix lock, owns serial child commands and
timeout/interruption process-group cleanup, and freezes class/resource hashes.
Stop only through exact exec session `84020`/recorded runner/client PID if
necessary, never through a broad Java/Minecraft match. Focus setup is applied
only during warmup and reported below. All runtime results remain pending.

Runner PID `40110`, original Java PID `40139`. Original completed naturally
and passed the existing runner validator at 61.3989 FPS; patched-off is live.
The operator's original focus action was reached after capture had started and
was skipped, leaving capture focus untouched. Original strict focus is therefore
pending actual report review; no success is inferred from not taking an action.

A bounded warmup focus watcher started in exec session `29951`: it reads only
each serial console's GPU-timer-ready/measurement-start marker, calls
`/usr/bin/open -a Codex` once during early warmup, records the action, and stops
after the final arm's action. It sleeps one second between brief log checks;
it runs no tests/builds/image/GL operations. The skipped original and successful
off action at `2026-10-05T20:36:58.715809Z` (return 0) are retained in
`dev/logs/photon-density-detail-coast-pilot-r1/warmup-focus-actions.json`.
This is operator focus/status automation, not a timing-harness change. It will
not act if a measurement-start marker is already present. The original strict
policy is accepted/rejected only from observed capture flags after terminal.

## Rejected r1 result and stopped handoff

Runner exec `84020`/PID `40110` exited 1 after the disabled JVM finished naturally.
Original Java `40139` and disabled Java `40252` are stopped; enabled was never
launched. The exact focus watcher PID `40294` was terminated with SIGTERM and
exec `29951` exited 143. Final `minecraft_processes()` returned `[]`, class and
resource fingerprints remained unchanged, and root explicitly retook sole
client/GPU ownership. No second scene or diagnostic repeat was launched.

The reproducible rejection at `scripts/run_proof_matrix.py:289` was:

```text
ValueError: Requested option is not an active Iris option: SHADERLOD_DENSITY_DETAIL_COST_CONTROL
```

The original passed the legacy runner validator. The disabled raw report/PNG
were retained before strict option validation: its matrix `source_inputs`
explicitly requests private gate=true, but the actual effective Iris map lacks
the gate. It loaded the exact frozen private archive, registered the protocol-2
bridge and rendered an in-world pipeline with shaders in use/no fallback on
Apple M3 Max / GL 4.1 Metal 91.7. This demonstrates pack startup/rendering;
it does not demonstrate a compiled selected diagnostic or source activation.
Root assigned the shader owner to inspect pinned Iris option discovery and
produce a new recognized gate/source freeze. The missing gate alone rejects
the diagnostic independently of any timing/focus result. No validator, raw
report, option map or private metadata was modified to bypass that failure.

Separately, the strict fixed focus audit rejected the original: its capture
had 501 focused=true observations and zero unfocused/unknown observations.
Disabled had 502 unfocused observations and zero focused/unknown. Both were
visible/non-iconified with no capture transitions, actual 2976×1672 and 24/24
chunks. The operator reached the first manual focus setup too late and skipped
the action once measurement was underway. This operator sequencing error
caused the fixed policy to be unestablished; no user mistake or host-focus
performance cause is inferred. The later warmup watcher successfully applied
Codex focus for disabled before its capture, but cannot correct the first arm.
The two arms cannot form a focus-matched control, even after a source-gate fix.

| r1 arm | Capture start UTC | Written UTC | Raw FPS | Raw mean/median GPU ms | Qualification |
| --- | --- | --- | --- | --- | --- |
| original | 2026-10-05T20:36:07.907098Z | 2026-10-05T20:36:32.904077Z | 61.3989 | 15.0579 / 14.9764 | focused; strict policy rejected |
| disabled | 2026-10-05T20:37:34.363480Z | 2026-10-05T20:37:59.407687Z | 60.6435 | 15.4394 / 15.2662 | requested true gate absent; runner rejected |
| enabled | not launched | not launched | — | — | no result |

Raw metrics are preserved for provenance only. No diagnostic performance,
uniform linking/readback, physical fine fetch avoidance or density/occupancy
change can be inferred from this incomplete rejected attempt. Both retained
2976×1672 PNG hashes verified; the images were inspected after terminal and
displayed resized to 2048×1151. They show the expected cloud-visible sunset,
shoreline and water glint, with no accepted on/off diagnostic image to inspect.
Similarity of these two stills is not density/quality acceptance.

Raw matrix/report/log/PNGs remain in
`dev/logs/photon-density-detail-coast-pilot-r1/`; original/off profiles and all
provenance remain in `run-workloads/photon-density-detail-coast-pilot-r1/`.
Additive audit `density-runtime-rejected-audit.json` records the independent
gate and focus rejections without rewriting the raw runner status. It also
records exact stop handles, native PNG checks, capture times and unchanged
class fingerprints. No matrix analysis was produced because the runner stopped
at strict gate validation.

Next useful action is the shader owner's new source freeze. Only after that
freeze will this owner update the strict wrapper manifest/member expectations
and source fixtures; no pin relaxation is authorized. Root must assemble the
corrected archive and approve a fresh `r2` profile root/tag. Start and acknowledge
the bounded warmup focus watcher **before** the runner; do not create the runner
output directory from that watcher before the runner reserves it. Every first
and later warmup receives its focus action automatically, followed by report
rejection if focused/unknown/changed capture flags occur. Old r1 worlds/reports
must not be reused or relabeled. Await a new explicit root GPU handoff; no
runtime process remains owned here.

## Corrected source freeze for fresh r2

After the retained r1 failure, the shader owner inspected the actual pinned
Iris source lexer/OptionSet/application behavior. Nine source/parser fixtures
passed: the old combined private/program `#if` did not discover the private
boolean option; corrected outer `#ifdef SHADERLOD_DENSITY_DETAIL_COST_CONTROL`
with inner `#if defined PROGRAM_DEFERRED1` discovers it. Defaults false/true and
opposite overrides apply in both directions. The exact original stage entry,
settings and cumulus sources gain only this private option; PROGRAM_DEFERRED1
is not leaked into the user option map. These fixtures establish actual parser
behavior, not GL driver compilation, live uniform linking or diagnostic rendering.

Corrected manifest SHA-256:
`542e6e61b1565532ce1d8975416917a29a866bc07165f80d5536d1a6124ad9dd`.
Corrected cumulus member SHA-256:
`3a5b3fbae654259567e76e660fac012fa0c2743e60c09519945f57d2e7440024`.
Builder SHA-256 reported by root:
`4a725424d491b669243dcc9d48704c747ab2247253e97804a191c0bd9e027adc`.
Settings members are unchanged from the prior source freeze; the private option
continues to default off. Original archive, source coastal report/options,
license, counts, pose and all other source requirements remain pinned unchanged.

The density preparer now requires the corrected manifest fingerprint. Its five
fixtures still pass, with explicit source expectations for both nested private
guards and the corrected cumulus fingerprint supplementing the worker's real
Iris parser tests. The unchanged seven AO preparation fixtures still pass.
The strict qualifier continues to recompute the patch and compare all actual
members and complete metadata; no source/report/hash qualification was relaxed.
No corrected production archive assembly, real-world preparation, Gradle,
client or GPU operation was performed by this owner during this update.

R2 must use fresh profiles/tag `photon-density-detail-coast-pilot-r2`. Start
the watcher and observe its armed acknowledgement before the runner, storing
any watcher script/log outside the not-yet-created runner output directory.
It must create no output directory before the runner's reservation. Poll the
fresh console for early warmup GPU-timer readiness, perform exactly one
foreground action per arm, and record it. If measurement already started,
skip the action and reject the run from report focus; never change focus during
capture. Stop the watcher on runner failure or after its final warmup action.
Preserve r1 unchanged. Runtime aggregate option parity must differ from original
only by the private gate, with no PROGRAM_DEFERRED1 or new unrelated option.
Any absent gate or cross-arm focus mismatch rejects the attempt independently.

## R2 active runtime handles

Root independently passed the corrected nine Iris/source fixtures, seven AO
fixtures and five density preparation fixtures, assembled corrected archive
SHA-256 `066fc9862085cbd977d039c8a9efc31b4c9423eb018ec318533f0248adfdd1c4`,
and transferred sole client/GPU ownership to `/root/ao_runtime`. Java/classes
remain frozen from the prior 47 Java tests/build/dev compile. Root/workers stop
tests/builds/assembly/encoders/image processing until this pilot is terminal.

Actual r2 preparation exec `84245` exited 0. Source/copied canonical world hashes
remain equal to `89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46`.
The corrected pack and manifest pins qualified; no profile mod jars are present.
R1 remains preserved and separate.

Bounded focus watcher started first, exec `83230`, PID `41590`, command:

```sh
python3 dev/logs/photon-density-detail-focus-r2.py
```

Its acknowledged output before runner launch was:

```text
ARMED: before runner; output absent; warmup-only Codex activation; no mkdir
```

Ignored focus script SHA-256 is
`954366a61c2004557b3ffd842738cd68d250f6b15194cd612969dbd188182946`.
It records action timestamps to the runner-created output directory only after
warmup starts, stops without focus actions on runner failure, and exits after
the final arm's warmup action. It performs only brief one-second log/status
polls and the authorized foreground action, no images/tests/builds/GL work.

The exact r2 runner command above then started in exec `8247`, PID `41611`;
first original Java PID `41630`. Current initial profile is
`run-workloads/photon-density-detail-coast-pilot-r2/coastalwater/high`, followed
by patched-off/adaptive. Raw records are under
`dev/logs/photon-density-detail-coast-pilot-r2/`. Stop only exact owned runner
exec `8247`/PID or watcher exec `83230`/PID; existing runner manages its own
child-group interruption/timeout. Actual driver/gate/focus/performance/quality
remain pending; the armed watcher alone is not focus proof.
Original early-warmup Codex activation at `2026-10-05T20:51:25.865675Z`
returned 0; the watcher was already armed before this client's startup.
Original completed naturally and passed the runner at 60.1758 FPS.
Disabled early-warmup Codex activation at `2026-10-05T20:52:52.551409Z`
returned 0; disabled subsequently completed and passed strict runner option
validation at 60.8841 FPS. The requested true private gate is now observed as
an active Iris option; aggregate-option/focus/quality audit remains pending.
The runner advanced to enabled arm 3. No source cost/quality gain is inferred.

## R2 terminal result and returned ownership

Runner exec `8247`/PID `41611` exited 0 after all three serial JVMs completed
naturally. Original Java `41630` and enabled Java `42058` are stopped. Focus
watcher exec `83230`/PID `41590` exited 0 immediately after its last warmup
action, before enabled capture. Final live-client inventory is empty and frozen
class/resource fingerprints match matrix start. No second scene/repeat, compiler,
build or extra profiling was launched. Sole client/GPU ownership is returned
to root after this checkpoint.

The exact original/off/on runner accepted fresh UUID reports, loaded mod/dependency,
archive/options/control/camera, boundary stability, fixed wetness, CPU/GPU streams
and PNG contracts. Additional terminal audit passed under
`dev/logs/photon-density-detail-coast-pilot-r2/density-runtime-audit.json`:
all 366 original effective options are unchanged, the fully equal private maps
add only `SHADERLOD_DENSITY_DETAIL_COST_CONTROL=true`, and PROGRAM_DEFERRED1 is
not introduced as an option. Actual global states were false/true. Re-qualified
private archive/member/complete metadata/license match the corrected source freeze.
Actual requested/effective radius was 24/24 at native 2976×1672 in the fixed
coastal pose/time/weather at both boundaries. All terrain/culling/visibility/
coherence experiments remained false, noDH actual, shaders in use/no fallback.

All capture observations were available, visible/non-iconified and focused=false
with unknown focus=0, no state transitions and fixed 1488×836 logical/2976×1672
framebuffer dimensions. Counts were 502/502/501 observations, not time fractions.
Pacing was identically vsync false, cap 260, fullscreen false,
pause-on-lost-focus false. The acknowledged pre-runner watcher acted in early
warmup only and recorded three successful Codex activations:

| Arm | Warmup focus action UTC | Capture start UTC | Report written UTC |
| --- | --- | --- | --- |
| original | 2026-10-05T20:51:25.865675Z | 2026-10-05T20:52:10.361559Z | 2026-10-05T20:52:35.400778Z |
| disabled | 2026-10-05T20:52:52.551409Z | 2026-10-05T20:53:37.140793Z | 2026-10-05T20:54:02.185373Z |
| enabled | 2026-10-05T20:54:19.196168Z | 2026-10-05T20:55:03.769156Z | 2026-10-05T20:55:28.768466Z |

Root and workers were instructed to maintain the quiet boundary; no contrary
activity was reported. The only owned monitoring was the runner and bounded
focus/status watcher. No tests/builds/assembly/encoders/
image processing were performed by this owner during capture. This records the
coordination boundary, not continuous external process/GPU/thermal monitoring.
Sampled focus/pacing parity does not establish equivalent host scheduling/clocks.

| Arm | FPS | Mean frame ms (1000/FPS) | Frame wall median/p95 ms | GPU mean/median/p95 ms | Thread mean ms |
| --- | --- | --- | --- | --- | --- |
| original | 60.1758 | 16.6180 | 16.3062 / 23.1605 | 15.4416 / 15.3553 / 16.8092 | 7.7597 |
| disabled | 60.8841 | 16.4246 | 16.1934 / 23.0032 | 15.3720 / 15.2213 / 17.1652 | 7.4837 |
| enabled | 62.1495 | 16.0902 | 15.9111 / 22.5294 | 14.9639 / 14.8469 / 16.3433 | 7.8453 |

GPU sample counts are 301/304/310; correlated frames are not independent repeats.
Total-frame wall/thread values use the existing run-tick stream, separately
from GPU elapsed intervals. Enabled versus disabled observed 2.655% lower mean
GPU interval, 2.036% lower mean frame time and 2.078% more throughput. Versus
original, the figures are 3.094%, 3.176% and 3.280% respectively. This is a small
exploratory difference in one serial quality-changing block, not a reproducible
or causal saving, isolated texture-fetch budget, calibrated density gain or
quality-equivalent LOD acceptance. Density/occupied-sample/early-exit changes
and time/order effects cannot be separated here. The 50% goal remains unachieved.

Actual driver was Apple M3 Max / OpenGL 4.1 Metal 91.7, ELAPSED_ALTERNATING
frame-only GPU queries every five frames, timestamp bits 0 / elapsed bits 32.
Host `sw_vers` after terminal reports macOS 27.0 build 26A428; launch logs report
Oracle Java HotSpot 21.0.9+7-LTS-338. Loaded versions were Minecraft 1.21.1,
NeoForge 21.1.253, Iris 1.8.12-snapshot+mc1.21.1-local, Sodium 0.6.13+mc1.21.1,
and ShaderLOD 0.1.0-dev only in private arms. Actual artifact hashes are in each
report/audit; Iris and Sodium match the pinned runtime's observed local artifacts.
Main/dev tree hashes remain
`6debde5638cd965e798f3bf5d7cbf9d7586510f954c44efdf753e91401b5402f` /
`26f1d00e2bac0b6a0aa169ad6914ffc12c80365e221dbb12dba72355298b9e76`.
Matrix Git HEAD is `dd78c2bdbaba264ec548cd8f693fa8514b786d9f`; class/resource
inventories capture the exact integrated runtime independently of that revision.

Both private profiles created identical secondary effects config, all six INHERIT
modes, ratios 1/1 and protected lighting, SHA-256
`c02b81bc4ad7c90ac2e23e839845ddcfa8d7ab6909a380161f319ed414530984`.
Protocol-2 registration, loaded true gate, global states and normal driver rendering
establish the available activation evidence. WorkloadSession does not observe
core/extension uniforms, physical texture calls or density/occupancy values.
No extension linking/consumption, verified 0.5 GPU readback or fetch-count proof
is fabricated. The source explicitly changes fine density when its valid core
predicate is reached; that mathematical intervention is not opacity calibrated.

All three native PNG dimensions/hashes verified and images were inspected only
after timing. The viewer resized them to 2048×1151. The broad cloud field,
sun/shoreline/water-glint composition and foreground water structure remained
similar; fine cloud-density/edge differences were not conclusively isolated in
these resized separate-JVM stills. This does not establish fine/opacity parity
or absence of the declared quality change. No native-pixel, movement, wind,
temporal reset, weather-range, inside-cloud or DH acceptance follows. The archive
remains an intentional quality-changing diagnostic with default-off gate.

Original/off/on effective option hashes were
`ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669` /
`4eb676875da8ada5aa494c5591f185ae9f0f3cc557b439c868d761e2ce4fd3af` /
the same private hash. PNG hashes in that order were
`ff7e80dcf555c25fa457d72ef3f37961fc2d7a3d93683bc697a1639ba4d34869`,
`e3fed690531c0b208e840f1b66405b64673765db42c415facd7259ed3306d3e6`,
`893157b8a2d148235f3942412ce270f28743f74257289b5c8e9d39b7b4ed8c28`.
Profiles/provenance remain under `run-workloads/photon-density-detail-coast-pilot-r2/`;
matrix, reports, summaries, analysis, logs, final PNGs and audit remain under
`dev/logs/photon-density-detail-coast-pilot-r2/`. R1 failures are preserved.
Next useful action is root's signal/measurement-reliability decision. This owner
does not authorize a repeat, second scene or calibration from this small pilot.

## One existing-observer core/sampler smoke

Root explicitly handed sole client/GPU ownership to `/root/ao_runtime` for one
bounded diagnostic after r2, using the unchanged existing `cloudSamplerProbe`.
This is not another density timing/calibration run. No main/mod/harness/shader
source, new observer or shader option was added. Root/workers stop tests/builds/
encoders/images during the diagnostic. GPU timing is false and getter overhead
precludes FPS/CPU/GPU performance inference.

Fresh wrapper preparation exec `7277` exited 0 at
`run-workloads/photon-density-core-readback-r1`; only its adaptive profile is
launched, with the same corrected archive, manifest, source/copied canonical
world and original coastal input pins. The other prepared arms are not launched.
Bounded focus watcher exec `56490` acknowledged before launch:

```text
ARMED: one smoke; before launch; output absent; no mkdir
```

Exact ignored launcher `dev/logs/photon-density-core-smoke-r1.py` starts the
existing `wait_command`/WorkloadSession with a fresh UUID request and preserved
`profile_inputs` plus pre-launch class/resource inventories. It is a one-run
artifact under ignored dev/logs, not a source framework. Launcher exec `62163`
uses request UUID `ffec820f-e57e-4888-9abe-9c39af18e56e`. Its exact command:

```sh
./gradlew runClientLodOn \
  -PrunDirectory=/Users/pippo/github/shaderlod/run-workloads/photon-density-core-readback-r1/coastalwater/adaptive \
  -PperformanceStack=minimal -PincludeDh=false -PmeasureWorkload=true -PmeasureOnly=true \
  -PcloudSamplerProbe=true -PgpuTiming=false -PgpuPassTiming=false -PcpuLayerTiming=false \
  -PinitialWarmup=25 -PmeasurementSeconds=5 -PbenchmarkWindowScale=2 \
  -PfixedWetness=0 -PgpuSampleInterval=5 -x compileDevClientJava
```

Raw console/manifest/workload/sampler/PNG records are reserved under
`dev/logs/photon-density-core-readback-r1/`. Stop only the exact owned exec
`62163`/client PID or focus exec `56490`/PID; the imported existing wait helper
handles its own child-group interruption/timeout. Readback/rendering and final
scope/input validation remain pending. The existing observer captures seven
core fields at the first full-screen draw per numeric program ID; it does not
query texture allocation dimensions or effect-extension fields, so those facts
cannot be claimed from this smoke.

### Core smoke terminal result

Preparation `7277`, watcher `56490` and launcher `62163` all exited 0; the
launched adaptive JVM stopped naturally. The owned exact exec sessions were the
stop handles; OS PIDs were not captured before this short diagnostic ended.
Final client inventory is empty and class/resource inventories match pre-launch.
Only one adaptive client ran; no follow-up timing/reload/calibration run was
launched. Sole client/GPU ownership is returned to root after this checkpoint.

Existing WorkloadSession UUID/archive/option/control/loaded-mod/noDH/temporal
validators passed. Additional audit
`dev/logs/photon-density-core-readback-r1/core-sampler-audit.json` passed:
all 366 original options plus only private gate=true; fixed coastal
pose/time/weather, native 2976×1672 and radius 24/24 at both boundaries;
global enabled; stable unfocused/visible/non-iconified capture with 101
observations and no unknown/transition; identical pacing; native PNG/hash;
fresh sampler report; unchanged classes. Focus action at
`2026-10-05T21:20:01.026224Z` preceded the capture window
`2026-10-05T21:20:25.001959Z`–`2026-10-05T21:20:30.000471Z`.
GPU samples are empty and `gpuTiming=false` was observed. No raw FPS/CPU/GPU
metrics from this getter-stalling smoke are interpreted or compared.

The existing observer captured 22 numeric full-screen programs, below its
64-program limit. Exactly one observed linked program, ID 18, contained the
two core fields at its first observed draw `2026-10-05T21:19:59.807476Z`:

| Core field | Active GL type | Scalar size | Location | Readback value |
| --- | --- | --- | --- | --- |
| shaderlodBridgeVersion | GL_INT (5124) | 1 | 31 | 2 |
| shaderlodEnabled | GL_INT (5124) | 1 | 38 | 1 |

Its linked uniform count was 42, with no truncated scan/capture failure and
active texture unit restored. Getter status is
`queried_without_gl_error_consumption`; the observer intentionally does not
consume glGetError, so this is not a GL-error-free certification. Console
contains an OpenAL `Stop: Invalid name parameter` at shutdown, not a reported
graphics/probe failure. Near/far/preset/transition/debug were not in linked
inventory and remain unknown: inactive, optimized out or undeclared cannot be
distinguished. Effect-extension fields were not queried; no extension readback
or consumer proof follows.

Complete frozen pack inspection finds these core declarations only in private
`shaders/include/sky/clouds/cumulus.glsl`, behind the selected private gate and
PROGRAM_DEFERRED1 guard. Pinned `world0/deferred1.fsh` defines that program macro
and includes `program/d1_clouds.fsh`; no other changed source declares the pair.
Thus ID 18's main-cloud scope is an inference from unique pinned source
declarations plus actual linked fields in this Overworld pack, not a program
name directly reported by the observer. The queried 2/1 values satisfy the
source's private activation predicate at that first draw. Readback occurred
before the logged controlled camera application and warmup, so it is not a
settled reload/config-history proof. It does not measure a density result of
0.5, physical texture-fetch avoidance, occupied-sample/early-exit counts or
opacity/quality equivalence. The absence of readback in the r2 timed run itself
is not retroactively repaired by this separate diagnostic JVM.

At that same program/draw, active scalar sampler3D entries were:

| Remapped uniform | Unit | Bound 3D texture | Sampler object | MIN/MAG | Base/max level |
| --- | --- | --- | --- | --- | --- |
| customtex1 | 4 | 54 | 0 | GL_LINEAR / GL_LINEAR | 0 / 0 |
| customtex0 | 6 | 57 | 0 | GL_LINEAR / GL_LINEAR | 0 / 0 |
| customtex2 | 5 | 56 | 0 | GL_LINEAR / GL_LINEAR | 0 / 0 |

All use texture sampling parameters with min/max LOD 0, bias 0 and anisotropy 1;
no sampler-object override. Original source aliases colortex6/7 were remapped;
the observer does not establish a per-texture source-file identity. Source
`shaders.properties` declares the Worley bubbly/swirley images as R8 64³ at
deferred colortex6.1/colortex7.1, and d1 maps those aliases to coarse/fine density.
Those dimensions are source declarations: the unchanged observer did not query
actual texture allocation dimensions, mip completeness or texel content. Linear
base-only sampled state here is not a proof of safe divergent implicit derivatives
or a calibrated sampling model for a future LOD path.

Sampler JSON SHA-256 is
`aae777592f6b2e763a9626d159f4fb8686cb122260abc4f30dda9dc35b905494`;
PNG SHA-256 is
`b31edf716c7bc42649e281ecd04ac52bdfd489aadda6d745063a9bc838914404`.
Raw console/manifest/workload/sampler/PNG and additive audit are retained under
`dev/logs/photon-density-core-readback-r1/`; fresh profiles/provenance remain at
`run-workloads/photon-density-core-readback-r1/`. The source predicate was
actually bound in the scoped observed draw; no new performance or quality
acceptance follows. Root retains the density defer/next-direction decision.
