# Frozen fullscreen LOD benchmark batch

2026-10-05. `performance_audit` owns this document, the new
`prepare_fullscreen_lod_batch.py`/test, and the additive fullscreen/explicit-case
runner and comparison-family guards. Shader builders, shared QA preparer and
Java/runtime ownership remain with their assigned workers. No actual canonical
copy, client, GL reload, GPU measurement, build, compiler or encoder ran here.
Temporary copy fixtures and source/CLI checks are not runtime evidence.

**Current next action: one original forest fullscreen untimed qualification,
after root reviews the changes. No six-arm timing launch.** Procedural untimed
QA failed with IrisTransformPatcher missing RPAREN in
`dev/logs/parallel-procedural-qa-r1`. The corrected combined r2 pins below are source-qualified after root reviewed
procedural r3 and reflection r2 repairs. They are driver-unverified, not a claim
that the corrected source fails. Fresh reflection-only, procedural-only and
combined records stay runtime-blocked, so timed preflight rejects any selected
batch containing them. New standalone/combined driver checks are required before
unblocking. The r1 parse failure remains retained. Lighting stays excluded.
The final replacement manifest/default-off archive set is now integrated here;
standalone QA reflection r2/procedural r3 pins match it. Parser/source acceptance
never substitutes for actual compilation, quality or performance.

## Concrete source/profile controls

`scripts/prepare_fullscreen_lod_batch.py` prepares fresh forest/coastal controls
from the same canonical sources used by the existing strict QA queue. It reuses
that queue's exact reflection-qualified canonical scene/options inspection, then
independently qualifies the combined archive against its complete pinned original
inventory, three reviewed standalone builders/manifests, all transformed helper
bytes, every payload member, full canonical metadata and original license. The
extra canonical reflection dependency is recorded explicitly; it is not accepted
as combined qualification. No arbitrary shader-source rewriting or archive
whitelist expansion is introduced.

All private arms use **one** default-off `Photon-CombinedLOD-disabled.zip`.
Sidecars select only the three reviewed public switches. Original retains all 366
upstream effective options and has main mod absent; private maps must restore
that exact original effective-option digest after removing those three flags.

| Arm / case suffix | Main mod | Global | Reflection / volume / procedural gates | Interpretation |
| --- | --- | --- | --- | --- |
| high | absent | unavailable | no private options | Matched original baseline |
| patched-off | present | false | false /false /false | All features compiled off; integration overhead control |
| reflection-only | present | true | true /false /false | Corrected reflection integration; new driver QA pending |
| volume-only | present | true | false /true /false | Primary volume integration alone |
| procedural-only | present | true | false /false /true | Water procedural integration; currently blocked |
| combined | present | true | true /true /true | Actual interaction of three tracks; currently blocked |

Case prefixes are `forest-` for `forest/` and `water-` for `coastalwater/`.
For example `water-volume-only` selects `coastalwater/volume-only` and
`runClientLodOn`. Original `forest-high`/`water-high` uses `runClientLodOff`.
All existing case mappings/default windowed behavior remain intact.

Compare each arm to original, and separately to patched-off. Patched-off versus
a single feature changes compile gates as well as global enable: this measures
whole integration, not same-gates isolation of physical effect work. Additional
same-gates/global-disabled controls are conditional follow-ups only if attribution
needs them. Never sum the three individual gains into a combined gain.

Every private profile records the same current producer2 requested effect config:
Volumetrics ADAPTIVE 512/1536/128 with 0.85/0.75 retained ratios; six other effects
INHERIT/unity/protected lighting. Core PERFORMANCE/32/96/transition 8/debug false;
terrain/culling/lists/coherence all off. Volume typed readback in the preceding
untimed QA supplies actual version 2/Mode 2/bands/ratios evidence for its standalone
source. It does not prove combined linking, far eligibility, physical iterations,
quality or benefit. Original ignores the mod config; do not report its values as
consumed. Defaults for released users are unchanged.

Source headers retain the canonical source's 2976×1672 windowed evidence separately
and set **new expected framebuffer to null**. They never rename old windowed
results as fullscreen. New actual physical/logical dimensions, fullscreen flag,
focus and pacing must come from a separate current-class smoke.

## Fresh preparation and first original-only smoke

Pure preview, already checked without writing profiles:

```sh
python3 scripts/prepare_fullscreen_lod_batch.py --output run-workloads/fullscreen-lod-batch-plan-r1 --scene both --dry-run
```

Root/operator actual preparation, only after the current untimed client terminates
and root reviews this final corrected combined/standalone source qualification:

```sh
python3 scripts/prepare_fullscreen_lod_batch.py --output run-workloads/fullscreen-lod-batch-master-r1 --scene both
```

That command currently prepares source controls and retains blocked-arm markers;
it does not authorize launching them. Fresh outputs cannot collide with prior
profiles or sit outside ignored roots. Canonical worlds are save-lock/hash checked,
copied equally and unchanged; only the already qualified fresh forest DH JAR is
removed. Source logs/captures/reports stay intact. Partial failures remain marked
failed. All main/dev/resource trees are fingerprinted before/after preparation,
including current effects producer and observer classes. Source header class
presence is not runtime version/readback proof.

Keep masters untouched for later fresh timing copies. Root/operator must first
make a separate stopped original forest smoke copy, preserving its manifest/source
digests and rejecting old reports/requests/logs/PNG state. Use the same existing
copy/save-lock discipline; no source world is launched or overwritten.

Original-only fullscreen command after that copy exists:

```sh
python3 scripts/run_proof_matrix.py forest-high --profiles-root run-workloads/fullscreen-lod-original-smoke-r1 --stacks minimal --dh absent --fullscreen --window-scale 1 --sample-interval 5 --fixed-wetness 0 --warmup 25 --duration 5 --repeats 1 --no-gpu-queries --skip-build --freeze-dev-classes --tag fullscreen-lod-original-smoke-r1
```

Root naturally leaves the game foreground through late warmup/capture; expect
focused true when that state is observed. Settle any fullscreen transition during
warmup only. Do not activate Codex, change focus, resize or alter options during
the 5 s capture. If focus is unknown/mixed, visibility is lost, or dimensions/
pacing/fullscreen change, retain failure and use a fresh retry. A stable observed
false focus is not rewritten as true; root decides whether to repeat it under
the intended gameplay focus. Smoke FPS/CPU output is **not a performance result**.

The hook already exists in compiled dirty `build.gradle`/ComparisonClient:
`--fullscreen` forwards `-PcomparisonFullscreen=true`. WorkloadSession reports
actual GLFW observations and cached logical/framebuffer sizes after warmup. The
runner requires sampled fullscreen true, visible/noniconified, stable available
focus, matching logical and physical boundary/count dimensions, vsync false,
configured 260 pacing, pause-on-lost-focus false and no capture transitions.
260 is the matched configured setting; do not describe it as measured unlimited
throughput or infer a host scheduling state from it.

Successful smoke stores `fullscreen_reference` in its matrix manifest, including
actual framebuffer, logical window, focus and pacing. Timing must use
`--fullscreen-reference dev/logs/fullscreen-lod-original-smoke-r1/matrix-manifest.json`.
That option pins actual physical/logical sizes/focus and requires unchanged
compiled main/dev/resources. `--expected-framebuffer WIDTH HEIGHT` is available
for an explicit physical pin; it cannot conflict with the smoke. No monitor
dimensions are assumed in this plan. Hardware/version/hash identities belong to
the new report, not an old source result.

## Candidate QA and eventual dedicated timing

Prior reflection r1 QA does not qualify its corrected r2 source. Unchanged
volume standalone QA does not replace fullscreen combined-family QA. After original smoke and source review, test patched-off and unchanged volume-only without GPU queries after review,
same current classes,
actual fullscreen dimensions and source pins. Separate opt-in typed sampler/
uniform readback QA can run beforehand using `cloudSamplerProbe=true`; it must
use its own reports and cannot enter timing. Corrected reflection, procedural and combined wait for
their new source-specific driver/quality QA; old reflection r1 success is not
transferred to r2. Procedural r3 untimed retry is currently operator-owned. No all-six launch is approved by this document.

When every chosen arm passes the required QA, root prepares new equal timing
copies from untouched masters. Freeze source archives/options/configs, producer2
main/dev/resources and current local Iris/Sodium artifacts for the whole batch.
One operator owns every client/reload/capture. Source teams may develop and run
fixtures during untimed diagnostics. During dedicated 25 s captures, all other
tests/builds/archive assembly/encoders/image processing stop until client terminal.
No class/pack/config updates between arms or repeats; a change requires a fresh
batch/smoke, not continued aggregation under old hashes.

Choose N5 frame-only GPU timing for this batch; N16 is available but would be a
separate matched batch. CPU detailed layers are **explicitly disabled** through
`-PcpuLayerTiming=false` →`shaderlod.dev.cpuLayerTiming` →`CpuTimers.LAYERS`.
The fullscreen validator checks the actual exported property false. Coarse
runTick/render/blit/swap clocks remain; this is not completely uninstrumented.

Every timed runner row explicitly sets `cloudSamplerProbe`, `dhDrawEvidence`,
`waterSceneSurvey`, `shadowObserve`, `visibilityLists` and `shadowCoherenceOracle`
false. Actual runtime properties must be false. `DevEvidenceMixinPlugin` then
does not apply RasterSamplerStateMixin; CloudSamplerStateProbe returns before
GL getters when disabled. Its fullscreen hook remains a guarded callback, not
an active sampler readback. Empty/no fresh sampler reports and absence of detailed
CPU layer scopes corroborate those source controls; classes alone do not prove
all runtime effects are free.

Eventual preview command, **currently blocked; use dry-run only**:

```sh
python3 scripts/run_proof_matrix.py forest-high forest-patched-off forest-reflection-only forest-volume-only forest-procedural-only forest-combined --profiles-root run-workloads/fullscreen-lod-timing-r1 --stacks minimal --dh absent --fullscreen --fullscreen-reference dev/logs/fullscreen-lod-original-smoke-r1/matrix-manifest.json --window-scale 1 --sample-interval 5 --fixed-wetness 0 --warmup 45 --duration 25 --repeats 2 --skip-build --freeze-dev-classes --tag fullscreen-lod-forest-r1 --dry-run
```

Coast uses the same arguments and six `water-` cases, a separate tag and the
same accepted physical/logical/focus reference. The current six-condition
Williams implementation produces these exact initial orders, using input
O=original,D=disabled,R=reflection,V=volume,P=procedural,C=combined:

| Block | Actual order |
| --- | --- |
| 1 | O,D,C,R,P,V |
| 2 | D,R,O,V,C,P |
| 3 | R,V,D,P,O,C |
| 4 | V,P,R,C,D,O |
| 5 | P,C,V,O,R,D |
| 6 | C,O,P,D,V,R |

Two blocks are minimal repeated triage with interleaved order, **not perfect
position balance or final 50% acceptance**. A complete six-block cycle balances
positions/carryover for these six conditions; extend only if useful evidence
warrants acceptance work. Do not run larger cycles simply to find a favorable
pair. Original/source quality mismatches, driver failures and negative arms
remain excluded explicitly and retained. The goal stays 1.5× total throughput
versus matched original with little discernible quality loss, not an easier
new percentage or a sum of candidate deltas.

## Host context, analysis and acceptance

Record lightweight process/load context immediately before a launch and after
its client terminates, outside timed windows. Operator names the exact window/
UTC interval and stores outputs under the ignored batch log root, for example:

```sh
ps -axo pid,ppid,pcpu,pmem,comm > dev/logs/SELECTED_BATCH/001-before-processes.txt
uptime > dev/logs/SELECTED_BATCH/001-before-load.txt
```

Repeat with `after` names following terminal. These snapshots are context,
not continuous GPU/thermal/utilization telemetry or causal explanations. Do not
stop unrelated processes; retain suspected external activity as a qualification.
Record actual pipeline/backend, GPU sample count/zeros/drops when exported,
cadence, run-level spread, FPS/capture duration and coarse wall/thread median/
p95. GPU elapsed intervals stay separate from CPU and 1000/FPS; no overlapping
pass sums or effect-budget subtraction. Fullscreen smoke/timing may change
pixel count materially; old 2976×1672 pilots cannot be the comparison baseline.

Existing analyzer now requires the complete explicit six-arm source family,
frozen archive/full metadata/member/helper/license qualification, exact three
gate maps and original option digest. Old controls/exemptions remain unchanged;
there is no wildcard allowance for new private options. Analyze original→each
arm normally; optional `--baseline forest-patched-off`/`water-patched-off`
provides integration comparisons with their stated limits. Promising GPU/total
signals then warrant a fresh balanced query-free original/disabled/winner control
using `--no-gpu-queries` and the same class/reference/settings; query-free data
provides no GPU milliseconds. Full near/far/transition motion, water/glints, thin
geometry, clouds/history, material boundaries, salient lighting, reload and
DH-present/absent compatibility remain separate acceptance checks.

## Current pins and source checks

Current producer2 compiled trees from root's untimed QA checkpoint:
main `17286dd585d5c1b34f2f0332a869a3bb6920a52d021c84d6bf3ae48732eef9f3`,
dev `a5e7d5f8d30bd4d4b275c933b3d155522ce09cfca07f7d786928465685f8330f`.
The preparer/runner record actual current trees; these historical handles do
not automatically qualify a later build. Minecraft 1.21.1 / NeoForge 21.1.253 /
Iris 1.8.12-local / Sodium 0.6.13 / Photon v1.3b, Java 21; DH optional but physically
absent in this normal-terrain batch. Loaded artifact hashes and actual hardware/
OS/driver must be retained per smoke/run; no full desktop profile inference.

| Frozen input | SHA256 |
| --- | --- |
| Original Photon | `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead` |
| Combined r2 manifest | `dcbb31d5878387bd00bdb048703be9b6c22539b30844ee8054dcc455a533cf19` |
| Default-off combined r2 archive | `87e12ac10401cf59c36d76eaabd1b7fdb9b8dc66365dfebdcfa2c7e7a329e41a` |
| Original LICENSE | `514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5` |
| Forest original option map | `4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745` |
| Coast original option map | `ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669` |

The initial eight new fixtures passed before the combined source update. An
intermediate nine-fixture check had seven passes and two fail-closed source errors:
`Frozen combined manifest changed`. The real-archive and analysis fixtures
rejected the stale 238da80f pin against the in-progress replacement. No test was
skipped or relabeled passed. Nineteen existing matrix fixtures and tracked diff
whitespace checks passed. After the complete reviewed replacement pin set was
integrated once, all nine new fixtures and all nineteen legacy fixtures passed
again; real-source qualification and a fresh r2 dry-run preview passed. The two
prior source rejections remain retained, not relabeled successes. These fixtures cover real
default-off ZIP qualification, helper/metadata/inventory mutations, tiny equal
world-copy/control preservation, dynamic fullscreen dimensions/focus/observer/
CPU-layer guards, legacy cases, explicit option-union rejection and known driver
failure preflight. Tests do not compile GLSL or prove rendering. The first patch
attempt used a wrong textual anchor and changed nothing; it was corrected before
tests. The source rejection remains retained as a check result; it is not a
runtime or performance result.

`combined_contract` contributed read-only metadata/API review; no files or runtime
were changed by that subagent. Root reviews the code, carries the procedural
failure/repair pins forward, and owns the next original-only smoke handoff.

Final corrected-pin checkpoint: combined metadata/member contract matches
`build/combined-lod-artifacts-r2.json`; full metadata semantics and 527 unchanged
original payload members remain unchanged. QA owner confirmed reflection r2
manifest0c090561…f4ec/ZIP485590fa…aa35 and procedural r3 manifestb529fdb0…100e2/
ZIP267f6358…bd9ea before any actual fullscreen copies. R1 combined pins
238da80f…/b6eb562d… and the stale-source test rejection remain historical evidence.
No actual fullscreen dimensions or performance result is claimed at this freeze.

## Actual preparation preflight repair

The operator's first actual command
`python3 scripts/prepare_fullscreen_lod_batch.py --output run-workloads/fullscreen-lod-batch-master-r1 --scene both`
failed before copies or client launch: the canonical inspector returned
`record['original']['path']` as a string, and `file_hash` attempted `.open`.
Direct-Path qualifier fixtures had missed the real callback contract. The failure
is retained; no preparation success or shader result is inferred.

`qualify_combined` now normalizes original/private archive paths at entry, and
`inspect_scene` normalizes its private argument before subsequent hashes. The
fixture now uses the actual canonical coastal inspector with string original/
private/root paths, not a mocked Path-only qualifier. That fixture then found
a second glue mismatch: archive_defaults inventories twelve added declarations,
including helper guards, while only the three reviewed settings declarations
are public options. Public declaration equality is now checked in the exact
whole-member-qualified settings.glsl payload; all upstream archive defaults and
complete source/member/helper/metadata/license pins remain enforced. Runtime
comparison still requires precisely the three active public gates. No wildcard
allowance or source qualification was removed.

The intermediate callback fixture's declaration error is retained here. After
both repairs, all nine new and nineteen legacy fixtures pass, and the actual
string-path qualifier call returns the frozen87e12ac1…e41a archive qualification.
The tool-output wrapper briefly had a printing-name error after awaiting the
checks; the checks were rerun with visible successful output rather than treating
the missing output as acceptance. Filesystem checks before and after testing
confirmed master-r1 absent and not a symlink. The operator may retry that same
unreserved target; an existing failed/partial reservation must never be reset.
No canonical copy, client/GPU, build/compiler, shader-source or encoder work was
performed by this audit owner during this repair.
