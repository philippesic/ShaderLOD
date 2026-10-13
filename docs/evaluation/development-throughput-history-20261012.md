# Development throughput: history and current evidence

Read-only investigation of implementation/history on 2026-10-13 UTC; only this report is authored. The shader/runtime objective remains paused. No builds, tests, clients, GPU work, benchmark retries, Git staging or resource changes were performed. Parent owns integration and current state. This report separates observed output from hypotheses about why progress was slow; commit/file counts are not elapsed effort.

## What has actually been delivered

The repository contains real practical gains, real control/recovery ports, unsuccessful shader experiments, and substantial supporting machinery. These categories cannot substitute for one another.

* **Practical renderer gains:** the fixed NeoForge MC1.21.1 MakeUp bracket yields 126.9847 → 180.4031 → 125.0535 FPS, +42.0668% candidate throughput against first original. Sildur yields 84.3678 → 103.0274 → 85.3110, +22.1169%. I independently read all six original/candidate/return workload reports, verified each SHA against its release, and recomputed frames/elapsed. These use original shader archives, main-mod-absent originals, and a renderer command coalescer. They are not proof of reduced shader sampling, all-family gain, UI recovery, motion quality or newer-version gain. Reviews: [MakeUp](makeup-speed-replication-averages-r1-review.md), [Sildur](sildur-speed-replication-averages-r1-review.md), committed in `09e3573`.
* **Current preview has no demonstrated speed improvement:** independently recomputed the seven raw `dev/logs/manual-shader-fps-20261011/*/runtime-report.json` reports. Six families completed five-stage same-mod Off/Performance/Balanced/Quality/Off brackets; all six Performance values are below first Off, and native return declines 20–32%. Bliss has only two accepted windows before view mismatch. These observations do not support precise causal regressions because native drift is large. Solas and AstraLex originals remain blocked on this Mac. [Current table](manual-preview-fps-20261011.md).
* **Usable control/runtime increments:** BSL NeoForge26.2 main-JAR original384/generated388/Quality384/Off384 and Cancel are accepted, including actual production correction and GL-entry/native-body observations. Aurora remains disabled, so this does not establish executed sample reduction or FPS benefit. Reimagined main-JAR preparation is accepted but runtime remains paused/pending. [Independent BSL review](bsl-neoforge-262-main-control-runtime-review-20261011.md); managed STATUS observed 2026-10-12T21:14:21Z.
* **Direct shader reuse exists:** current manifests for R/U, MakeUp, BSL, Solas and AstraLex reuse `patches/adapters/shared/uniform_sample_budget.glsl`. Four volumetric source ports retain the native integration interval rather than truncating it, with separate archive pins and compile/runtime guards. MakeUp and R/U have later rendering/control increments; those do not yet establish speed or equivalent motion quality. [Independent source review](volumetric-concept-transfer-review-20261011.md), [goal milestones](../goals/cross-shader-performance-integration.md).

The intended denominator is nine families × five versions × two loaders = 90 tuples. The six complete current FPS rows cover six distinct families on one tuple each, not six accepted performance cells and not six of nine globally supported families. This audit does not manufacture a full90 acceptance census from partial artifacts.

## Historical shape and its limitations

At the initial audit census, HEAD was `d75cac3`. There are 101 reachable commits: 3 dated October3, 22 October4, 65 October5, 10 October10 and 1 October11. These are recorded commit dates, not a measure of work performed on each day. Much later work exists only in the shared worktree.

The initial worktree had 54 modified tracked files (+7,898/−403 lines) and 2,396 untracked files, 2,450 status entries total. Of those entries, 1,001 were under docs, 424 ports, 393 dev, 290 src, 96 tests and 95 scripts. Counts use NUL-delimited Git porcelain, avoiding quoted-path errors. HEAD tracks only546 files; all eight newer port directories and standalone Fabric work are absent from HEAD's tracked top-level inventory. There are528 files in docs/evaluation at this census. These are evidence of integration/document volume, not528 experiments or failures. Parallel audit edits can change counts after this snapshot.

Across all101 commits, `git log --numstat` reports95,066 added+deleted text lines, counting repeated changes: docs37,208 (39.1%), scripts13,771 (14.5%), tests16,261 (17.1%), src14,450 (15.2%), shaderpacks6,416 (6.7%), patches4,893 (5.1%), remainder2.3%. Category boundaries are coarse: src includes dev observers; scripts include actual adapter builders. It would be false to call all scripts/tests/docs waste or convert their line share into an effort share.

Representative progression:

| Anchor | Concrete work/result |
| --- | --- |
| `a9ca73b` | Initial environment:50 files/2,530 additions. |
| `7d190c1`, `bef2e01` | Specialized water kernels, followed by retained water regressions. |
| `0a4d7a9`, `000f355` | Parallel feature integration then shared policy/adapter runtime:38 and47 changed files; genuine reusable infrastructure. |
| `137095d`, `967d1f0`, `3880166` | Spatial reconstruction, integration and driver controls; source/driver evidence preceded useful net-cost acceptance. |
| `9915a42`, `dec9f40`, `7cc7d31`, `6966e6c`, `1076f77` | Preflight, clone lifecycle, session identity and guarded integration repairs. Necessary repairs also consumed delivery attention. |
| `09e3573` | Accepted MakeUp/Sildur scene-level renderer gains plus failed/qualified other candidates. |
| `d75cac3` and current uncommitted work | Priority returns to existing family support; current multi-version/control/volumetric ports substantially exceed committed history. |

## Five causes and smallest useful corrections

### 1. Source mechanisms and evidence machinery expanded before cheap net-benefit rejection

The October5 spatial integration is the clearest example. `137095d` adds1,160 lines in six files; `967d1f0` adds3,433 lines across40 files. [Structural cost audit](../SPATIAL_COST_AUDIT.md) later identifies an extra full-screen pass, widened cache, duplicated classification and nominal94.9MiB/frame extra attachment output at the held4.98M-pixel resolution. Even full fallback retains enabled graph overhead. [Rejection pilot](../SPATIAL_REJECTION_PILOT.md) reports original15.4881ms/frame, private Off25.1078, and failed FULL113.5953; ADAPTIVE never launched. FULL is failed evidence, not an accepted causal cost comparison, and Off drift prevents precise attribution.

**Inference:** the construction order exposed the project to expensive negative discoveries after broad integration. This is not proof that every earlier line was unnecessary. Counterevidence: reusable policies, actual driver controls and retained negative evidence are useful; the pilot correctly stopped without weakening its sample threshold.

**Minimal correction:** before widening a new mechanism, use its existing original/full-cost/enabled consumer for one bounded net-cost/active-work screen. For today's human priority, port an existing mechanism/control path through one real tuple before adding architecture. Record which user outcome a helper unlocks and stop adding helpers once the consumer is sufficient. Do not revive this closed spatial trial.

### 2. Producer/consumer contract gaps repeatedly emerged only in real launches

[USER_LESSONS](../USER_LESSONS.md) names concrete agent omissions: Java compiled without refreshed dev resources, guessed newer API methods, direct-argv admission dropping NeoForge program argfiles, finite startup deadlines expiring across handoffs, Mixin helpers loaded from a reserved package, partial native option maps, authored GLSL checked instead of Iris serialization, and helper include guards omitted from expected option counts. These are distinct failures sharing a boundary problem: offline success was attached to a different artifact or shape from the actual consumer. Several are corroborated by current source entry points and raw manual-preview startup/results; the report does not independently replay every historical failure.

**Counterevidence:** current `dev_client.py`, saved-plan preflight and focused authentic-producer tests address several exact recurrences. More generic guards are not the default fix.

**Minimal correction:** every changed boundary gets one real saved producer example and the actual consumer invocation before runtime. Batch independent cheap failures. Generate deadline/producer/freeze/preflight/launch in one local operation after readiness, and separate source artifact acceptance from launcher release. Extend the existing checker/runner, preserving original failures; do not duplicate another framework or pretend static checks prove rendering.

### 3. Completion of a patch was confused with adoption by the active operation

The October11 lesson “A staged fix did not reach the active workflow” documents a reviewed rate patch and qualifier left outside the continuing agent's live Java and historical runners. Commits `e355f9a`, `1076f77` and `a31a5ac`, plus [finite caller guidance](../RENDERER_BRACKET_CONSUMER.md), show the repair/adoption chain. A queued or documented handoff was not deployment. Large dirty state compounds ambiguity: almost the entire newer-port implementation is outside committed HEAD, so a commit review cannot reconstruct the delivered candidate.

**Counterevidence:** current BSL runtime review ties all main origins to an exact delivered JAR, observes DEV correction zero and real main correction progress; that is successful adoption evidence. Untracked files are preserved work, not proof of loss or duplication.

**Minimal correction:** finish each bounded integration with one exact normal-entrypoint receipt: source/resource inputs → produced artifact → selected caller → actual origin. Keep coherent scoped commits when the parent has integrated accepted files; never broadly add the2,396 untracked files or delete them. A workflow improvement counts only after its next real consumer uses it, or is clearly pending while runtime is paused.

### 4. Transferable concepts were sometimes replaced by rediscovery or a borrowed benefit claim

The October7 [active-cost audit](../REUSABLE_ACTIVE_COST_AUDIT_R1.md) had to correct its “new” atmospheric-liveness opportunity: `shaderlod_needs_atmosphere(depth)` already existed in older structural work. Its isolated benefit was still unproven. Conversely, today's uniform sample-budget ports show a concrete reuse path that required no new general policy: near-volume R/U, MakeUp cloud slab, BSL Aurora, and later Solas/Astra sites. The missing work was pinned source-specific caller/interval adaptation and public control/recovery consumption.

However, reuse of a successful **renderer percentage** is invalid. Root uses a fill-return hook;26.1.2 uses cached fills with gate-transition invalidation;26.2 uses `SubmissionQuadCopy` at submission with private copies. Current six complete Performance windows observe only4 commands removed from29,720 inputs (BSL3/4,768; Sildur1/3,076; four other families0). These are cached-fill observations, not per-frame GL submissions, so neither a GPU-savings ratio nor absence of all renderer benefit follows.

**Counterevidence:** reports usually state these limits correctly, and explicit family certificates protect correctness. The historical MakeUp/Sildur gain itself is independently reproducible from retained reports.

**Minimal correction:** assignments identify the exact reusable implementation and the one missing producer/consumer/recovery join. Look at existing negative evidence before calling an idea new. Require new tuple performance evidence before quoting old gain. Do not require a new benchmark campaign merely to deliver an authorized compatibility/control port.

### 5. Coverage activity can be mistaken for useful active optimization

BSL's current Aurora consumer preserves the native interval and shares the existing policy, but native384 has Aurora0. Balanced rendering on BSL therefore proves the generated pack/control path, not execution of its new sampling reduction. Solas/Astra source ports cannot cross their original Mac driver blockers. Astra's default-active AO opportunity is more relevant to avoided work than optional dormant paths, but still cannot demonstrate runtime here. [Common-budget scout](remaining-family-common-budget-opportunity-20261011.md) also correctly rejects unsafe recurrence simplification and Solas min4 count reductions that would do no work.

**Counterevidence:** source breadth, faithful Off/Quality/Cancel and a prepared compatible-hardware candidate are legitimate user outcomes; the human explicitly reprioritized family ports. Their value should not be erased because FPS is unearned.

**Minimal correction:** retain separate labels for source assembly, current-artifact rendering/control, actual active work, quality, speed, DH and PBR. Prefer a runnable family with a concrete active consumer for optimization work. Record blocked-original source ports as such and move to another runnable family; no repeated unchanged driver trial. For coverage-only work, declare the control/recovery milestone and avoid manufacturing an FPS claim.

## Recommended execution decision

Preserve the paused Reimagined main-JAR continuation and its existing frozen inputs. The strongest immediate workflow changes are authentic producer replay and mandatory active-consumer adoption of fixes; the strongest implementation reuse is the already delivered family-specific controller/sample-budget machinery. No evidence here warrants new universal GLSL rewriting, a replacement task database, another proof framework, a broad benchmark restart, or claiming that the old1.21.1 renderer percentages transferred.

Audit evidence is bounded: actual Git log/stat/status, current source/manifests, six hash-verified historical workload reports, seven current raw FPS reports, compact checkpoint and cited reviews/lessons were read. Historical screenshot quality and all90 compatibility cells were not independently revalidated. No elapsed-time allocation is inferred from commit dates, file mtimes or documentation volume.

## Independent review of selected audit changes

Follow-up review on2026-10-13 UTC accepts the bounded adapter-sync change in `scripts/build_supported_pack_adapters.py` and its focused tests. It removes real manual work: propagating one existing recipe/helper into the shared classpath resources and maintaining current/legacy Java recognition. Those shared roots already feed ten builds. It does not add a runtime certification gate, alter presets, invent a universal patcher, or claim shader benefit. Changing a shared policy across multiple families remains an explicit coordinated operation; this selected-family command deliberately refuses an inconsistent partial rollout.

Waited for author completion before tests. Independently ran `python3 -m unittest discover -s tests -p test_supported_pack_adapters.py`:14/14 passed in0.634s. Reviewed pin checks, unique catalog selection, exact previous-current preservation in legacy, unrelated digest retention, unsafe-path refusal, full prevalidation before writes, repeated no-op and helper mirror propagation. The author's final correction also checks other **packaged** manifests, so deliberately repinning maintained shared consumers alone cannot break still-old classpath consumers by copying a new helper. A duplicate early test main-guard observation was communicated during editing; final file has one guard and discover runs the full suite.

Independently exercised `sync_resources` dry-run against all eight authentic local originals, including Astra's ASCII-named archive:8/8 produced their current assembled identities and zero changed files. No actual catalog/resource synchronization was needed. Separately invoked the unchanged ordinary BSL CLI with `--adapter/--base/--output` into a temporary directory: every assembled shader/member byte equaled strict `apply`, only the two existing metadata files were added, validation flags remained false, and a second invocation refused output overwrite. This is offline ZIP assembly, not a Gradle build or shader compilation. No clients or runtime checks were run.

Limitation: writes are individually atomic replacements after validation, not a cross-file transaction; interruption can require rerunning the operation. The code explicitly documents this. No blocking issue found for the intended single-owner bounded authoring use. Java compilation/runtime support remains unverified and paused.

Also independently spot-checked the transcript report against raw rollout C's visible records: lines10247/10901 substantiate human priority at21:41:31 and ten-JAR ready message at22:31:09 (49m38.5s); lines15929/15959 substantiate deadline handed off05:12:55 and refused05:15:33 with31s remaining against a>130s requirement. Lines15966/15993 show an actual `followup_task` call and subsequent launch report. Encoded tool arguments were not interpreted. This corroborates one mechanical expiry episode and the delivery interval; it does not independently certify the ten JARs or assign all elapsed time to waste. No raw transcript was copied into Git.
