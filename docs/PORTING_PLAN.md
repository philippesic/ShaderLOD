# ShaderLOD-next: agent-first porting plan

Status: implementation of packages A–D and lightweight agent development exercises authorized by Philip on 10 November 2026. Benchmark redesign (E), optimization research/workflow proof (F), and high-level skills/shared agent operations (G) remain deferred. Live work requires a currently available compatible machine; an unavailable allocation is a partial state, never a synthetic substitute. Current implementation and verification status is maintained below.

## 1. Outcome and settled constraints

The goal is a short, reliable development loop: inspect measured expensive work, state a plausible mechanism, implement one optimization through a supported extension, verify activation and correctness, measure through the maintained harness, iterate, then close with scoped evidence. More checks, instructions, or generated code are not success by themselves.

Philip's latest decisions govern this plan:

- Preserve `/home/philip/ShaderLOD` and the lowercase historical checkout. Leave historical evidence there. Refactor selected behavior into next; do not transplant its candidate graph or clean up the old repositories.
- Next may be substantially rebuilt, but remains local-only with no remote/push. Keep small reversible commits and preserve unrelated edits.
- Start with Complementary Reimagined and DH on a compatible first machine. Add shaders and hardware gradually. DH absence must still be safe; enabled-DH behavior is part of the first intended product.
- Include native shader selection, config, Apply/Off/Cancel/recovery, and the basic machinery for developing and loading NEW optimizations. All old candidates remain parked. Do not port auto-tuning, unrelated loader/version targets, or old campaign launchers.
- Preserve original archives, effective native settings, resolution and AA. Unsupported combinations must remain explicit; do not silently lower settings to obtain a pass.
- Ban comments/docstrings in owned code. Refactor unclear code into understandable types, names, and responsibilities. Do not merely move excuse-laden comments into decisions. Preserve required attribution through narrow exceptions; shader directives are executable syntax, not comments.
- Root-only instructions; `CLAUDE.md` references `AGENTS.md`. No arbitrary instruction byte cap. One findings index if it remains useful, with accepted results showcased in README and other findings separately accessible.
- Agents work autonomously within the authorized task. Routine tests, check repairs, bounded recovery, and safe resource reclamation do not require repeated approval. New goals, destructive operations, unsupported scope changes, and weakened acceptance semantics do.
- No hosted CI/PR/review bureaucracy is required. Local verification must stand on its own. Actual benchmark methodology remains a later design task; this plan specifies interfaces and integrity requirements only.

The hierarchy for every recurring problem is: eliminate the bad state through architecture; encode the invariant in types/API behavior; verify it with tests/static checks; use a tool or skill for operational procedure; use instructions for the remaining judgment. A new rule must explain the failure it prevents and the legitimate path it leaves available.

## 2. Audit verdict and evidence

The skeleton is useful acquisition and checker infrastructure, not yet an agent-ready development architecture. Keep the pinned tools, standard validators, evidence distinctions, and useful test specimens. Replace policies that match incidental text with checks of actual ownership and behavior. The biggest missing component is a cohesive optimization extension and observation path.

### Directly verified in this audit

| Finding | Evidence | Consequence / required response |
|---|---|---|
| The working tree's full check fails before audit edits | `./check` reached `repoPolicy` and failed S4; the existing edit in `docs/remaining-stages.md` introduced the words “porting plan.” In-memory validation of committed HEAD passed repository policy. | This is a working-tree false positive, not proof that the committed foundation never passed. Replace prose sniffing; do not rename documents to evade it. |
| Python accepts `True` as both measurement and count | `decode_sample({"kind":"measured","value":True,"count":True})` returns `Measured(value=True, count=True)` in `checks/contracts.py`. | Parse exact wire types; share Java/Python conformance fixtures. |
| Failed serialization leaves a partial final result | `publish(path, {"unsupported": object()})` raises TypeError but leaves a file containing an unfinished JSON object. | Exclusive creation prevents overwrite, not partial publication or crash durability. Validate first and finalize atomically with an explicit completion boundary. |
| Receipt provenance is caller text | `receipt("cpu-integration", "anything")` succeeds. | Current labels are synthetic conventions. Later live claims must be derived from runtime-owned records and verified joins, not arbitrary labels. This is not an anti-fraud security boundary. |
| Java architecture checks are coupled to `foundation.*` names | `src/test/java/foundation/BoundaryTest.java`; main/dev source sets are currently empty. | Classify every authored compiled class by actual source-set/package role; assert rule coverage. Do not mistake empty boundaries for successful production integration. |
| Provider discovery only produces names | `checks/policy.py:discover`, `checks/driver.py:discoverProviders`, `tests/test_foundation.py:test_discovery_no_shared_edit`. | No runtime loader, generated registration, or optimization extension currently proves discovery works end to end. |
| Findings/test inventories freeze today's skeleton | `policy.py` requires seven finding rows; `driver.py:test_counts` rejects unexpected test names. | Protect required behaviors/suites without making every new test/finding a protected administrative edit. |
| Reports mix counts of files, validators, and tests | `checks/driver.py`, `checks/selftest.py`. | Report actual executions, cache reuse, assertion/specimen identities, and not-run lanes separately. Some self-test rows summarize real JUnit/tool assertions elsewhere; they are not independent executions. |
| CPU loop ignores incremental task inputs for custom steps | `build.gradle`, `checks/tooltests.py`. | The failed run ended in about four Gradle-reported seconds before expensive self-tests. This is NOT a full-suite latency measurement. Delegate inspection of historical logs found full runs of roughly 65–95 seconds; rerun and measure after repair. |
| Bootstrap recovery is incomplete | `checks/bootstrap.py` downloads to final names and trusts an existing extraction directory. | Interrupted downloads/extraction can require manual cleanup; use verified staging and atomic installation. |
| Old runtime state ownership is valuable but entangled | Old `PackSelectionService`, `PackSelectionTransaction`, `OptimizationEditTransaction`, `scripts/experiment_lifecycle.py`, `scripts/run_storage.py`. | Reuse behavior and refusal/recovery fixtures, not central switches and candidate-specific lifecycle dependencies. |

The independent audit additionally found legal `world/` and `region/` packages, PNG assets, a schema's `frames` key, in-memory `StringIO.write`, and GPU-related thread synchronization can trip broad scanners. Source inspection confirms these mechanisms. Its historical timing figures are reported observations, not new measurements here. Shebang exception expiry is another deterministic future failure unrelated to source changes.

No old runtime measurements were rerun. No GPU clients were launched. Neither the historical Reimagined/DH Asahi failure nor the absence of accepted old candidates proves that future targets cannot work.

## 3. What to add, remove, and adapt

### Add

1. A typed optimization module contract, generated registration, activation witnesses, and add/remove extension tests.
2. Explicit owners for configuration transactions, runtime lifecycle, artifact publication, and disposable workspaces; recovery is part of their APIs.
3. One versioned wire contract with Java/Python conformance fixtures and closed decoding at boundaries.
4. A fast CPU feedback lane and a complete closure lane; trustworthy incremental reuse and actionable diagnostics.
5. A bounded runtime execution interface shared by verification, profiling, and later benchmarks; structured status, cancellation, recovery, and resource accounting.
6. Managed storage classification and safe reclamation before resource failure becomes a blocker.
7. One current plan, one findings index, immutable evidence references, and generated/validated accepted-result presentation.
8. An explicit optimization workflow tying candidate selection to observed cost and scope; end-to-end rehearsal before claiming agent readiness.

### Remove

1. Text/filename bans that infer raw output, plans, GPU allocation, or architectural ownership from common words.
2. The hard instruction-size cap, seven-finding limit, and exact global test-name list.
3. Mandatory hosted CI/CODEOWNERS artifacts and personal review of every test/check edit as completion requirements.
4. Stage-number literals in runtime refusal logic; missing capabilities should be reported by capability, not migration numbering.
5. Unused discovery outputs, misleading report counts, and checks with no subjects masquerading as production coverage.
6. Stale MakeUp-first/four-shader-first instructions and per-piece approval stops.
7. Rejected candidate source, hooks, registrations, and fixtures that no longer protect shared behavior from the active tree after closure and preservation.

### Adapt

1. Keep a comment ban, but distinguish comments from shader preprocessor syntax, licenses, string data, and digest-pinned third-party inputs.
2. Keep one owner per durable value; do not turn the result publisher into the only package allowed to perform any I/O.
3. Keep required-test/no-skip guarantees; make suite obligations and behavior IDs stable while normal test discovery grows.
4. Keep self-tests, but rerun expensive tool specimens when their inputs change and at explicit clean verification, not blindly on every edit.
5. Keep pack isolation; generate a deterministic typed provider index consumed by the actual application and CLI.
6. Keep one findings index, not one ever-growing narrative file or multiple manually maintained verdict tables.
7. Keep no GPU scheduling/lease service. Replace the racy “check processes, then launch” contract with demonstrable single-owner execution; see the explicit policy tradeoff in section 8.
8. Keep source parity and CPU-before-live gates, but make them executable progress gates within authorized work rather than requests for Philip's attention.

## 4. Product boundaries and source refactor

Remain in one Gradle project unless a demonstrated classpath/packaging problem requires a later change. Keep the number of concepts small. Names below are responsibilities, not a mandate for a class per noun.

| Boundary | Owns | Must not own |
|---|---|---|
| Pure domain/contracts | Pack/candidate identities, settings, supported/unsupported decisions, immutable operation values | Game classes, filesystem, GL, process launch |
| Pack provider | Exact source identity, semantic binding, capability requirements, supported extension sites | Other pack providers or runtime execution |
| Optimization module | One mechanism, applicable capability/pack constraints, changed resources, activation evidence requirements | Shared engine dispatch, direct settings writes, evidence verdicts |
| Application transaction | Prepare/apply/observe/commit/restore state transitions and one current selection | Pack-specific algorithm branches |
| Iris/DH/NeoForge adapters | Pinned hook seams, actual runtime observations, necessary thread-affine mutations | Statistical conclusions or fallback fabrication |
| Development instrumentation | Bounded collectors, missingness/health, runtime control and profiling export | Shipping product activation policy |
| Host runner | Process/profile ownership, storage preparation, ordered requests, cancellation, recovery | Pack algorithms, direct construction of positive verdicts |
| Evidence publisher/analyzer boundary | Validated finalized records and derived reports respectively | Rewriting raw observations or mutating live settings |

Classify compiled outputs and use package visibility and narrow interfaces to prevent accidental access. Architecture tests cover all classes, forbidden edges, cycles, and forbidden concrete adapters. Explicitly mark intentionally empty components during the skeleton stage; require nonempty coverage when the corresponding slice lands. Standard ArchUnit checks are preferable to accumulating custom regex approximations.

### Optimization extension contract

Each candidate lives in one module directory with a stable ID, supported input identities/capabilities, parameter schema, targeted measured work, transformation or runtime behavior, and test fixtures. Generated registration drives selection/config/UI and development discovery. Unsupported input is a typed refusal with a reason; recognition is not qualification.

Start with the smallest transformation path that pinned Iris can actually support. Spike and verify the integration seam before freezing its API. Do not promise in-memory GLSL rewriting without proving the seam. A derived archive is acceptable if originals remain immutable, generated identity is distinct, and apply/recovery is transactional. New mechanisms may legitimately require a reviewed engine capability; “no common-engine edits” applies to another candidate using an existing capability, not every imaginable optimization.

Separate requested, prepared, linked, observed-active, and restored state. UI toggles or successfully written bytes cannot establish execution. Record the loaded artifact, graph generation, effective settings, and mechanism-specific witness. Reload/dimension/DH transitions invalidate stale prepared state. Configuration persists IDs/parameters rather than a growing candidate enum.

The seam spike chooses a generated index or an established registration mechanism such as ServiceLoader. In either case, the packaged registration must match the declared module directories and require no manual common-code edit. First prove discovery and removal with a non-shipping identity/no-op test provider. It demonstrates plumbing, never a speedup. After the profiler and redesigned benchmark exist, demonstrate one authorized real candidate through the same API. It may be rejected; a trustworthy negative closure is successful infrastructure validation.

Candidate composition starts conservative: independent candidates are not automatically safe together. Declare overlapping resources/conflicts and reject combinations without an explicit composition rule. No automatic tuner or additive-speedup claims.

### Port behavior, not accidental structure

Characterize selected old contracts on an external copy before refactoring. Port native pack identity and archive safety, option snapshots/queues, Apply/Off/Cancel and failed-reload behavior, reload/world/dimension invalidation, optional DH loading, and relevant profiler event ownership. Retain useful rejection/recovery fixtures. Maintain a selected-source disposition for every removed override, config key and hook: irrelevant feature, equivalent native behavior demonstrated, or required behavior reimplemented. Trace consumers and failure paths; misleading historical names do not establish deadness. Separate structural parity from intentional behavior changes, and keep shared correctness guards in treatment and control. A known native defect must be characterized and explicitly retained as a limitation or corrected under a new shared identity, never silently treated as comparison purity. Do not import the old root initializer, central candidate enums, `CompileOptionControl`, pack recipes, or campaign scripts wholesale and strip them afterwards.

Before package B, lock the complete runtime set: Minecraft and NeoForge, exact Iris, Sodium and DH builds, Reimagined archive/member digests, settings preset, and required transitive integration dependencies. Reuse verified identities from history only after checking the actual acquired bytes and compatibility; the foundation's build pins alone are insufficient. Acquisition uses package A's staged verification. CPU parity receipts name the behavior, old/new source identities, intentional correction, assertions, and evidence level. Known bugs are negative fixtures, not behavior to preserve. The packaged JAR is checked for dev instrumentation, parked implementations, accidental mixins, and undeclared resources; source scans alone are insufficient.

## 5. Scope without making every iteration a compatibility campaign

Use one primary development cohort: pinned Reimagined, a declared settings preset/dimension, enabled DH, and an available machine that can render the original combination correctly. Choose it from current availability during execution; native Windows RTX 5080 is a candidate because the old project measured Reimagined there, but that does not prove this exact enabled-DH stack works or renew its allocation. Old RTX allocation is not standing access. Preserve absent/disabled DH behavior and native fallback as correctness cases without pretending they are the same performance cohort.

Distinguish four claims: recognized input, supported integration, observed working behavior, and performance-qualified optimization. A shared pack family or an adapter implementing an interface grants none of the later claims automatically.

Development iterations exercise the candidate's declared affected scope on the primary target. Changes to shared lifecycle, config, or discovery run the broader CPU contract suite. Compatibility expansion is a deliberate milestone: add a second structurally different provider through the existing seam and run the applicable live checks. Do not choose the second pack solely because it resembles Reimagined; choose the smallest useful test of the abstraction when expansion is authorized. Additional versions, GPUs, settings and PBR enter explicit scope cells later.

An extension exercise must show that adding a provider requires its implementation, descriptors and tests, not engine branching. It need not literally be one file. If a second provider exposes a flawed abstraction, improve the shared interface instead of adding special-case paths.

The old Asahi sampler-limit result remains scoped historical evidence. Static capabilities can reject known impossible cases early, but cannot universally predict shader-link success. First-machine admission needs an actual native link/render verification before optimization work. No silent sampler reduction. README claims remain scoped until broader evidence exists; “proven generally positive” is not rewritten as four packs times two machines.

## 6. Checks that are useful and fast

Retain `./check` as the complete CPU closure entry. Add an explicitly labeled fast invocation using the same rule implementations for edit feedback. A fast pass must never be presented as full completion. Pre-commit may use the fast lane; coherent milestones and handoff require the full relevant check and applicable runtime verification.

- Run cheap repository/schema checks before expensive setup where possible. Report rule ID, file/site, observed problem, expected owner, and the supported fix.
- Declare Gradle task inputs/outputs. Cache deterministic CPU results against source, checks, fixtures, dependency/tool identity and relevant environment. Changed checks invalidate their own proof. Prefer Gradle's native incremental machinery, including tool-specimen tasks, over a second custom cache. A stale report directory is not a current pass.
- Reuse compiler/build caches; parallelize independent CPU lanes. Keep heavy CPU work and housekeeping off the measurement host during scored GPU work when they could contaminate the workload.
- Run expensive bad/control tool specimens on checker/tool changes and in a clean verification mode. Full closure includes valid proof for the current identity; cached proof is labeled as reused. Test discovery and no-skip/no-zero assertions still execute for the required suites.
- Replace global name equality with test-engine discovery plus mandatory suite/contract coverage. A newly added ordinary test should not require an administrative registry edit. Required integration cases must not disappear silently.
- Check mutations of the safeguards themselves: remove a required task, change a package role, introduce an invalid provider, alter a fixture, stale a report, and rename a production package. Assertions must fail for the intended reason.
- Keep narrow policy exceptions with rationale and regression evidence. Normal tests and stricter checks are autonomous. A replacement or relaxation must carry a negative specimen showing the original defect is still rejected and a legitimate control showing the corrected path works. That is sufficient for routine implementation changes without personal approval. Changes to evidence levels, verdict promotion, restoration guarantees, or native-quality semantics require Philip's decision; a passing specimen cannot authorize a changed requirement. Independent review is useful for a specific unresolved semantic question, not a mandatory loop.
- Separate structural syntax allowances (shebangs) from temporary behavioral exceptions. Do not make a stable shebang expire with wall-clock time. Time-sensitive policy tests use an explicit test clock.
- Audit file categories by owned roots and artifact types. Allow legitimate assets and small declared fixtures. Do not reject `world`, `region`, `frames`, or ordinary plan prose globally. Unknown executable source types must enter the checker coverage map.

Derive small oracle expectations independently of production code: manually worked transaction/state cases, known historical counterexamples, and independently computed identity vectors across producer/consumer boundaries. Two agents or two generated consumers agreeing with the same incorrect schema is not independent proof. Exercise unknown versions and deliberately disagreeing consumers. Required runtime safety must use explicit validation/errors, not assertions that optimized Python or disabled Java assertions can erase; otherwise refuse that execution mode. CPU fakes qualify their named logic only, not native GL synchronization or a real renderer.

Measure cold setup, warm no-change checks, one-file edit checks, changed-check self-tests, and failure-to-diagnostic delay during implementation. Optimize the largest repeated cost first. No numerical latency promise is justified by this audit. Do not trade away scientific validity to hit a timer.

## 7. Benchmark and profiler interface only

Benchmark redesign is a separate work package. This document does NOT choose scenes, repetitions, warmup, sample counts, statistical procedures, confidence levels, numerical thresholds, stop rules, quality tolerances, or the generality matrix. Old proposed numbers are not inherited defaults.

Expose one maintained development runner with discover/plan, verify, profile, compare, status, cancel, recover, and report operations, implemented as modes of the same lifecycle rather than independent launch scripts. Exact CLI spelling can follow the existing maintained entry. Pure report/analysis work never launches a client. Missing methodology reports unavailable; a synthetic stand-in cannot return a live verdict.

Name the supported schema versions and readers for requests, control messages, captures and terminal results. Unknown versions or producer/consumer disagreement refuse before mutation or qualification. Intentional migration creates a new artifact with source lineage and preserves the original; no automatic broad legacy support or schema-registry service.

The boundary carries:

- A versioned request: purpose, source/artifact identity, candidate/parameters, native baseline identity, pack/settings/world/dimension/DH identity, requested machine capabilities, methodology ID, and authorized resource bounds.
- Observed runtime state separate from requested state: actual loaded artifacts, renderer, linked graph/settings, activation, readiness, and measurement mode.
- Progress and terminal states: admitted or refused, preparing, running, restoring, complete, failed, cancelled, recovery-required. Completion is not synonymous with valid evidence.
- Immutable result references with execution status, validity/reasons, correctness, measured/missing data, health, coverage, restoration state, and applicable scope. Retention/default enablement remain distinct decisions.
- An analyzer result that can be positive, negative, inconclusive or not measured, with uncertainty and limitations supplied by the later methodology. Invalid results cannot be promoted by a force flag.

Authority, readiness, evidence validity, selection and scientific usefulness are independent facts. Authorization cannot validate stale/corrupt evidence; green evidence cannot grant execution authority. Editable approval fields and content hashes establish neither permission nor trust. Selection may reference an older valid result rather than the latest attempt.

Admission and terminal reports expose the same facts in bounded machine-readable and plain-language forms: the goal, actual mechanism activation (or not yet observed), starting source/runtime state, limiting risk, resource budget, stop action and evidence scope. Distinguish operational progress, scientific validity/usefulness and progress toward the objective. Label causes confirmed, supported hypothesis or unknown. A running renderer with insufficient observations is not a successful experiment; a scoped gain is not general qualification.

Profile and scored comparison are distinct modes. Requests declare instrumentation, observations attest to the actual enabled collectors, and the analyzer rejects contaminated timing or mismatched instrumentation. Intrusive scope queries, screenshots, graph dumps and diagnostic exports do not run in scored windows. Numerical overhead budgets and any justified timing instrumentation belong to package E.

Profile output identifies expensive named work with units, coverage and missingness. It must distinguish CPU wall work, GPU intervals, overlap and waits; do not sum overlapping intervals into a frame total. An optimization brief links the measured opportunity, mechanism, affected boundary, and disconfirming observation. Lack of useful profile evidence triggers diagnosis or a bounded exploratory hypothesis, not a fabricated ranking.

Fast development comes from cached builds, reusable prepared slots, offline analysis, and avoiding unnecessary startup/copy/export work. The interface may support multiple isolated requests per client only after reset/identity/contamination tests pass. Whether reuse is scientifically appropriate is decided by benchmark design, not assumed here. Likewise, streaming progress and cancellation do not authorize statistically invalid early stopping.

Content-addressed artifacts may deduplicate bytes and memoize deterministic analysis of frozen input. They must NOT memoize physical performance: identical declared inputs do not imply identical thermal state, scheduler behavior, or future measurements. Historical evidence can inform planning but is not a fresh run.

## 8. Runtime ownership, recovery and storage

Use one bounded owner of the complete runtime operation. It prepares a slot, journals intended mutation and preimages, launches the exact client, verifies actual state, restores, and finalizes. No alternative launch wrappers. The initial supported host implementations are Linux and native Windows; qualify the actually used platform and keep the other explicitly unverified until its tests run. Process identity, fail-fast exclusion, path safety and atomic publication must have real platform implementations, not a POSIX-only shell recipe. Transfer the built JAR, dependency/input manifest and request to an authorized target host with identity verification; return compact and raw evidence to the configured canonical evidence root. Use configured Tailscale aliases with LAN fallback where transfer is needed, retain host-key verification, and create no Git remote. Installation/build tools on the target are pinned as well. Each attempt binds an immutable source snapshot including dirty tracked content, untracked/generated executable inputs, the built JAR and actual loaded relevant libraries/dependencies, not HEAD alone. Source or loaded-input drift starts a new attempt and invalidates relevant qualification; no silent reuse. Occasional concurrent implementation uses separate worktrees and serial integration checks. Worktrees do not isolate devices, ports, environments, caches or artifact roots.

The execution owner enforces supplied wall-time/resource bounds, stop action, retry count and cumulative failed-attempt cost without relying on an agent or notification channel. Persist accounting across restart; missing or duplicate wakeups cannot extend authority or restart indefinitely. Choose explicit bounded operational defaults within the authorized task if needed, without inventing scientific stopping rules. Test lost notifications, exhausted retries and owner disappearance.

Control requests for stop/cancel/retry bind to the current operation, attempt and owner. Reject foreign/stale/replayed requests, including delayed cancellation after restart. Use existing harness/local access controls; if a network control endpoint is introduced, explicitly authenticate access and restrict scope. Do not invent a credential service. Authenticated access is not Philip's policy authorization, and shared credentials do not establish tenant isolation. A client crash and a runner crash both enter the same recoverable protocol. Recovery runs before another operation can mutate an unresolved profile.

Keep result publication separate from a state journal and disposable scratch writes. Validate/serialize a result before publication; commit a complete record atomically on the same filesystem, refuse replacement, and distinguish interruption from a finalized result. Durability requirements include flush/fsync and directory behavior on supported platforms. Readers only accept completed records. Failed recovery must preserve diagnostic inputs rather than publish success.

The state journal must allow idempotent recovery from every externally visible mutation boundary. Compare current bytes/identity against the operation's last owned version before restoring; preserve unrelated user edits. Use one parameterized fault harness across the actual mutations: Iris selection properties, effective/queued shader options, DH settings, generated packs and prepared-slot/save state. Test abrupt termination, disk-full writes, partial downloads, failed reload, duplicate requests, stale responses and recovery failure. A process termination releases process resources; it does not restore settings by itself. Process-kill tests establish process-crash behavior, not power-loss durability. Document filesystem assumptions and require separate evidence before claiming power-loss guarantees.

**GPU policy tradeoff:** the current global ban on GPU locks is too broad to distinguish a scheduler service from a minimal ownership primitive, and process scanning has a launch race. Do not build a lease server or agent-managed lock protocol. Recommended adaptation: one executor with a non-blocking OS exclusion primitive held for its lifetime, automatic release on owner death, and a busy refusal for a concurrent request. Choose a primitive whose behavior is tested on Linux and Windows. No queue, waiting protocol, lease, timeout-based ownership, or persistent scheduling service is needed. This is a proposed narrow change to the approved no-lock policy, not permission to implement a hidden lock system. If that adaptation is declined, runtime launch must be restricted to one external owner and concurrent submission stays unsupported; a process scan alone cannot claim concurrency safety.

The independent reviewer considers this merely implementing the already-allowed refusal correctly; this plan conservatively records it as a narrow policy adaptation because Philip explicitly accepted the previous no-lock wording. It can be approved with this plan, without revisiting each runtime launch.

Any cooperating GPU projects must use the same host/device ownership namespace or an existing external owner; a checkout-local lock cannot exclude another project. Advisory exclusion only covers participants. Detect ambient/uncooperative occupancy and refuse or report uncertainty rather than promise global exclusivity. External exclusive-window authorization may supply ownership where a repo prohibits reservation machinery. This alignment defines exclusion semantics, not a universal lock implementation or service. Rechecking disk headroom does not reserve capacity against unrelated writers.

No “world is stopped” claim based only on a stale PID. Bind host/boot and process-birth identity, owned profile, operation generation and recovery records. Do not terminate unrelated clients. Do not introduce a job queue or keep an executor running without a task need. CPU work may proceed elsewhere while the GPU owner works; measurement-host contamination is checked separately.

### Resource reclamation without micromanagement

Port the useful capacity/copy/slot protections from `run_storage`; do not copy its arbitrary historical reserve/retention numbers as universal policy. Size admission includes incoming data, peak staging, recovery preimages, expected output and a configured reserve. Disk pressure is an operational state with an automated remedy, not an immediate handoff.

Classify managed artifacts as reproducible cache, disposable workspace, active input, recovery input, canonical world, or retained evidence. Only explicitly disposable/reproducible inactive entries may be reclaimed automatically, under configured roots and ownership checks. Plan and apply reclamation within one owned operation; revalidate identity and active references immediately before each removal. Journal each deletion's path, classification, size, identity and reconstruction recipe. Refuse symlinks/path escape. Shared tool caches such as `~/.gradle` are outside this port's reclaim roots; any future cleanup uses the owning tool's supported mechanism. Old-repo trees are never reclaim roots. Age or an ignored filename alone is never sufficient.

The normal path is inspect capacity → reclaim eligible artifacts → recheck capacity → admit work. Bound retries and preserve useful caches where rebuilding costs more than their space benefit. If insufficient reclaimable space remains, continue independent work and report the exact additional need or preservation decision. Do not delete old-repo evidence to avoid asking a necessary question.

A shared disk-reclaim skill is useful as a thin interface to deterministic tooling. A global helper/subagent may invoke it for other projects, but each project supplies its classification/roots and holds its preservation policy. The agent cannot expand deletion authority. This plan does not install a global skill, spawn a permanent helper, or edit personal configuration. Validate the project mechanism first, then extract a shared capability under separate implementation scope.

## 9. Plans, findings, evidence and pruning

Keep this one current plan in Git. README/root instructions point to it; execution updates its compact stage state and unresolved decisions in place. Do not append daily narratives. When superseded, retain durable decisions and references, remove the obsolete active document, and let Git preserve history. Retire `remaining-stages.md` and contradictory decisions during the authorized infrastructure update, preserving the user's existing edit first. This audit does not delete them.

Scratch is external, task-owned and explicitly non-authoritative. Each active task has one workspace and an ownership/retention record. Before closing, promote reusable source, decisions, tests and compact findings into Git; delete disposable scratch after checking ownership. Preserve valuable unknown material pending classification. Default context/search entry points cover current source and current docs, not an archive of every old plan. No stale plan can silently regain authority because an agent found it through a broad search.

Use one structured findings index with stable IDs, question/mechanism, scope, disposition, revision/artifact references, evidence level, limits and reopen conditions. README's accepted table and the negative view are mechanically validated against the index; negative/inconclusive records have a separate view of the same index. Two evidence pools are useful presentation/retention categories, not two independent sources of truth. Preserve F1–F7 as historical, not newly verified, and add compact indexed references to relevant prior Reimagined/DH mechanisms so agents can avoid repeating known failures without loading old plans. The accepted table starts empty and remains so until eligible new evidence exists. All relevant attempts remain visible within a finding; showcasing a positive result must not hide its applicable regressions or unsupported scope.

Accepted evidence needs reproducible source/artifact identity, exact supported scope, correctness/performance receipts and limitations. Negative evidence needs enough mechanism/context to explain why it failed and when revisiting is justified. Preserve crash/recovery artifacts until resolved. Future retention policies may prune raw data explicitly, but the record must then state that full reproduction evidence is no longer available. A hash alone is not a backup.

On rejection, remove the candidate from the working tree, generated registrations, packaged resources, UI and config defaults. Preserve a reachable Git revision/tag and evidence references first; check that source and required inputs can actually be recovered. Keep regression tests for shared bugs it exposed. Do not remove old-repo implementations/evidence in this port. No source comments are needed to point to evidence: the candidate descriptor has its finding ID; the index points back to source and artifacts.

Keep a small system map only for cross-boundary flows that source layout cannot communicate quickly: Apply/recovery, development execution, evidence publication. Validate names and command examples, but do not mistake link existence for behavioral proof. Decisions describe current constraints and rationale, with explicit supersession/revisit conditions. They must not become a second set of immutable instructions or a home for rationalizing poor code.

## 10. Execution order and acceptance

These are implementation work packages, not current authorization to execute them. Once a package or the whole port is authorized, agents proceed through its routine steps autonomously. A failed gate causes diagnosis and repair within scope, not a claim of completion or a request to approve the next trivial edit.

| Package | Work | Exit evidence |
|---|---|---|
| A — repair the foundation | Reconcile current instructions/docs; remove false-positive policies and hosted-review obligations; fix bootstrap/publication/contracts; implement meaningful architectural coverage and incremental checks | Fresh-clone setup, warm edit checks, interrupted acquisition recovery, mandatory-lane mutation tests; actual reports distinguish executed/reused/not-run. Full CPU check passes. |
| B — source and extension spine on CPU | Characterize old behavior on a copy; implement identities, native config/selection transaction, Iris/DH adapters, generated provider registration and new optimization loading API; package checks | Behavioral parity/correction receipts and per-key/hook safety disposition; independently derived small oracles; no parked code; absent-DH classloading; add/remove provider fixture; recovery state-machine fault tests; compile against pinned interfaces. CPU-complete only. |
| C — profiler and execution interface | Refactor existing bounded collectors and export, without new profiler capabilities or a cost-map campaign; implement wire contracts; build one runner, process/profile/storage ownership, evidence publication, cancellation/recovery; methodology remains absent | Explicit Linux/Windows host adapters and platform test coverage (untested hardware named); identity-verified artifact transfer without a remote; cross-language fixtures, fake-adapter end-to-end orchestration, concurrent admission tests, crash/disk-full recovery, disposable-data reclamation tests, unknown-version/disagreement and lineage-preserving migration checks; stale-state and invalid-evidence refusal; lost-wakeup/cumulative-budget, foreign-control/PID-reuse and dependency-safe reclaim tests. No synthetic live claims. |
| D — bounded live functional integration | On an available compatible machine verify native Reimagined + DH first, then config/selection, extension activation, reload/Off/Cancel/failure restoration, and profiler collection | Actual linked/observed state and output checks, complete restoration, declared health/coverage and limits. Live-functional only; not performance-qualified. If native target fails, preserve failure and try another authorized compatible environment, not altered pack settings. |
| E — benchmark redesign and integration | Separate later methodology work, using section 7 interfaces; establish appropriate fast development and confirmation semantics | Later approved benchmark acceptance and hardware evidence. No old numerical plan is presumed valid. This document deliberately does not supply the design. |
| F — agent workflow proof and compatibility expansion | One authorized fresh candidate completes the full loop; then a second pack/hardware adapter exercises the boundaries when useful and authorized | Agent uses profiler evidence, supported extension, real correctness/performance tools, recovery and scoped closure without ad-hoc launchers. Accepted or well-supported rejected outcome is valid. Record friction and repair the highest-level cause. |
| G — small agent/interface improvements | Task-specific optimization workflow, runtime recovery, storage reclaim and correction skills; concise root guidance; optional shared-tool extraction | Commands/examples actually run; fresh-context agent can locate current work/evidence, explain a deliberately failed run with correct mechanism and causal status, and continue safely. No redundant policy copies or permanent agent infrastructure without demonstrated need. |

G can accompany the relevant earlier package; it is not a prerequisite bureaucracy layer. D follows a working CPU execution slice, not completion of all future shader support or all statistical analysis. E remains a separate design activity. Define completion by milestone: **foundation/source/runtime port complete** means A–D plus the G pieces used there, including live loading of the non-shipping test provider; it does not wait for statistical benchmark design. **Benchmark migration complete** requires E's separately designed exits. **Agent optimization workflow validated** requires F after E and authorization for the new candidate. The overall program requested here spans all three; do not call the whole program complete at A–D or block a legitimate runtime-port milestone on E/F. Unavailable hardware remains a documented partial state. A visible frame is necessary but insufficient for D: native selection, actual linked identity, activation and recovery must be asserted against the intended behavior, with a meaningful output check appropriate to the exercised path. Broad visual-equivalence and performance claims wait for E; do not invent image tolerances here.

### Autonomy boundary

Within scope: refactor, add tests, fix harness defects in the harness, update routine checks, use prepared slots, reclaim classified disposable storage, recover owned processes/config, commit coherent work, and iterate after failure. No need for Philip between P1/P2 or similar source pieces.

Escalate: expanding product/target claims, changing native quality or acceptance semantics, deleting valuable/unknown data, using unallocated hardware, extending a stated budget, publishing externally, or choosing among materially different unresolved product goals. An agent may challenge a flawed check and replace it with a better invariant, but must preserve its real safety requirement and show the original defect remains caught. “The check failed” alone is not permission to weaken it.

## 11. Research basis, limits and debate

The research was organized around architecture, fast feedback, long-running task continuity, operational ownership, evidence lifecycle and counterexamples to instruction-heavy solutions. Primary engineering reports and tool documentation inform mechanisms; none proves that this specific Java/GL project will become faster or scientifically sound merely by following the plan.

- [Lauren Tan's garden post, mirrored original](https://x.noodl3.net/poteto/status/2090546476464451907): the relevant analogy is ongoing care of constraints and removal of spreading bad patterns. [Original talk post](https://x.com/poteto/status/2102050467505430555) is identifiable; [mirror](https://x.noodl3.net/poteto/status/2102050467505430555) exposes the post, not a verified full transcript. Dune is an internal agent-friendly framework associated with Grok Bot, not a dependency to import. The accessible [secondary talk reconstruction](https://zenn.dev/karashizuke/articles/agent-trust-2000pr?locale=en) is a research lead, not primary technical proof; its codebase-first hierarchy matches the user's stated intent. This audit did not inspect Dune source or independently verify productivity claims.
- [Lauren Tan's original workshop page](https://maven.com/p/e23d9c/how-cursor-turned-ai-agents-into-better-engineers), identified by the Binary Eagle peer and opened here, lists verification, refactoring (32:34), and Dune architecture (41:00). This verifies the public reference, not the complete talk transcript or private implementation. [Camunda's Software Garden enforcement ladder](https://camunda.github.io/agentic-engineering-explainers/enforce.html) supplies a complementary first-party account of prioritizing structural constraints, narrow exceptions and checks.
- [Anthropic, effective long-running harnesses](https://www.anthropic.com/research/effective-harnesses-for-long-running-agents): supports explicit task state, incremental completion and real end-to-end checks. Its web-app workflow is not a GPU benchmark protocol.
- [Stripe, Minions part 2](https://stripe.dev/blog/minions-stripes-one-shot-end-to-end-coding-agents-part-2): supports prepared environments and deterministic feedback integrated into agent work. Adopt those mechanisms, not its large-scale CI/PR organization.
- [Birgitta Böckeler, harness engineering](https://martinfowler.com/articles/harness-engineering.html): distinguishes guidance from sensors and emphasizes early feedback. This plan uses deterministic tools for mechanical invariants and reserves agent review for semantic questions.
- [Cursor, self-driving codebases](https://cursor.com/blog/self-driving-codebases): reports coordination contention, stale context and orchestration failures as well as successes. It argues against transplanting a large multi-agent system into this small serialized-GPU workflow.
- [Anthropic, long-running application development](https://www.anthropic.com/research/harness-design-long-running-apps): independent evaluation can expose self-assessment failures, but orchestration has real cost and depends on models/tasks. Use focused review for consequential boundaries; do not impose an infinite mandatory debate loop.
- [Evaluating AGENTS.md](https://arxiv.org/abs/2602.11988v3) and [On the Impact of AGENTS.md](https://arxiv.org/abs/2601.20404v2): empirical results differ across task sets and measures. Neither supports a universal instruction-length threshold. Keep nonstandard commands/constraints clear, avoid redundant repository summaries, and observe actual agent behavior.
- [ArchUnit guide](https://www.archunit.org/userguide/html/000_Index.html) and [Gradle incremental builds](https://docs.gradle.org/9.2.1/userguide/incremental_build.html): established mechanisms for compiled architecture checks and declared-input reuse. They do not validate the current custom implementation automatically.

Research limits: X direct fetches returned 403; the garden wording was available through a mirror. Theo's video was identified through an indexed summary but its full primary content was not inspected; no technical recommendation depends on that summary. Actual benchmark design, new driver compatibility, hardware access and performance remain unverified.

### Debate record — concluded after two rounds

Round 1 used Opus 5.5 via Cursor at high effort. It independently inspected the skeleton and old-source seams and agreed that typed extension paths, fast meaningful checks, scoped evidence, safe recovery, current documentation and staged compatibility are the central missing capabilities.

Round 2 explicitly reported roughly 90% agreement and accepted the parent's objections: no physical-performance memoization, no intrusive profiling in scored timing, no universal pre-launch link prediction, no literal one-file-provider requirement, no absolute prohibition on extending engine capabilities, and accurate descriptions of self-test summary rows. Its material additions are incorporated: a full runtime pin set, Linux/Windows behavior and artifact transfer, contamination rules in the interface body, explicit completion milestones, mechanical routine check-replacement criteria, and deletion receipts with shared caches excluded.

Two reviewer suggestions were not adopted literally. First, a rendered frame alone cannot certify functional correctness; package D still needs behavior-specific assertions, while broad quality tolerances remain later work. Second, redefining the entire requested program as A–D would omit the benchmark migration and workflow validation the user asked to plan. Instead, A–D is a separately completable runtime-port milestone and E/F remain explicit later milestones. The reviewer's queue proposal was removed; the minimum runtime owner refuses concurrent work. Minimal OS exclusion remains an explicit narrow policy adaptation, not silent reinterpretation of the user's no-lock preference.

Agreement is on the add/remove/adapt list and the principal architecture, feedback, evidence and autonomy mechanisms. This is reasoned design agreement, not measured proof of agent productivity or runtime correctness. No further debate round is necessary for the remaining bounded policy choice.

### Cross-project coverage agreement — 10 November 2026

Philip requested comparison with the two peer port audits. Their project-specific plans were read through the existing threads and supplied document bodies: Binary Eagle's `docs/port-overhaul.md` and the cache project's `docs/porting-overhaul.md`. The three owners agreed on shared behavioral obligations, then each amended its own plan. No shared framework or runtime implementation is introduced.

The main gaps closed here are explicit human scientific reporting; separation of authority/readiness/validity/selection/usefulness; budgets that survive agent absence; immutable dirty/generated/loaded identity; independently derived oracles; unknown-schema refusal and lineage-preserving migration; owner-bound controls; and per-key/hook safety-disposition evidence. The cache audit's misleading active “prefetch” keys demonstrate why removal by name is unsafe. Binary Eagle's training history demonstrates why operational health alone can conceal scientifically uninformative work. These are peer-project lessons, not claims that those exact defects occurred in ShaderLOD.

We agree on host/device exclusion semantics across cooperating projects, not a universal GPU lock. ShaderLOD retains its explicit narrow OS-primitive proposal; the cache project keeps reservation machinery external, and Binary Eagle removes its overly broad lease/capacity-reservation claim. Full-commit versus fast-edit checks, proof caching, registration, instruction limits, filenames and skill availability remain project-specific. No benchmark methodology, automatic evidence deletion, universal checklist service, scheduler, or autonomous deletion agent is added.

The additions are acceptance obligations within packages B/C/G and the existing runtime contracts, not another stage. They preserve the fixed comment ban and autonomous work within authorized scope. Agreement does not supersede Philip's project-specific instructions or establish hardware correctness.

## 12. Historical audit handoff

Only planning documentation is delivered by this task. Existing `docs/remaining-stages.md` edits are preserved. The full check was run before this document existed and failed on the existing prose-triggered policy; this document is intentionally a real in-repo plan and also conflicts with that old policy. Do not call the tree green or modify executable checks just to hide this during a planning-only audit. Foundation repair is package A.

The plan must be reviewed for scope and implementation authorization. The existing skeleton, tests and instructions remain unchanged by this document. No source port, benchmark redesign, runtime job, storage reclamation or global skill installation has occurred. No remote/push is configured or attempted.

Final verification: the normal commit attempt reran `./check`; Python formatting/lint/type checks and all seven Python tests passed, while cached Java tasks were reused. Repository policy then failed with `S4: raw output or plan path` and `S4: renamed raw output or plan`. Later self-test/report lanes did not execute. The pre-commit hook blocked the commit; it was not bypassed. This document remains an uncommitted local file. Whitespace and cited local-code-path checks passed.


## 13. Authorized implementation and agent exercises

Current work: A foundation and B source spine implemented and CPU-tested; C Linux execution, bounded CPU/GPU frame collection/export and conservative interrupted-attempt recovery implemented. D passed the shipping-JAR selection/UI/identity-provider/recovery flow without DH on Philip's specified Asahi MacBook Air. The required DH-enabled native original still fails its fragment sampler resource limit. A–D as a whole therefore remain incomplete. No RTX 5080 workload was launched. E/F and high-level G remain deferred; no benchmark methodology is part of this task. Historical audit text above describes the pre-implementation state.

Use fresh-context capability-gated tasks: routine file/test change; narrow policy diagnosis; optimization registration add/remove; config restoration defect; bounded interruption/recovery; and truthful mixed-evidence reporting. Include a correct no-change case and paired authorized-proceed/unauthorized-refusal cases. Public behavior requirements remain visible; independently derived acceptance checks and held-out variants cannot be redefined by the evaluated agent. Calibrate each assessor with a correct and plausible-wrong solution.

Record exact task/snapshot/model/harness, budget/cache state, terminal evidence/diff, unaided outcome, failure classification, unsafe attempts/actions, interventions, elapsed/tool waiting, and unsupported claims. Freeze unaided failure before optional hints; retain assisted outcomes separately. Product correctness, agent usability and causal productivity improvement are distinct claims. A one-off successful task establishes only scoped feasibility. Safety violations or false qualification block the affected workflow until adjudicated, regardless of speed.

Only consequential repository-design decisions justify bounded matched repeated trials with controlled task capability/model/harness and balanced order. Preserve all failures and blocked attempts; do not average only successes or run until a preferred change wins. Ordinary deterministic regressions remain the frequent check; agent exercises run at relevant milestones or recurring failures. Use disposable CPU fixtures; actual loading/restoration remains the separate D hardware gate.


### Implementation checkpoint

The current CPU evidence covers 31 Java tests and 49 Python tests, compiled architecture/package boundaries, formatting, typing, dependency analysis and paired policy/tool controls. The ordinary full gate and normal commit hook remain required. [Final verification](evidence/port-verification.json) names actual execution/reuse and identities; cached steps are not fresh test executions.

A genuine isolated fresh-context agent repaired a real numeric admission defect without hints, policy/lock changes or hardware use, then passed the full CPU gate. Independent held-out assessment accepted 19/19 cases, rejected the uncorrected baseline and accepted an independently bounded reference. It correctly reported a full/partial receipt labeling defect, fixed separately by the parent. The earlier shared-tree trial was invalidated by concurrent edits and is excluded. [Agent exercises](agent-exercises.md) records one scoped usability demonstration, unavailable timing detail and unrun task families; no causal productivity gain is claimed.

A separate deterministic packaged-JAR plant/weed exercise proves that adding only a module directory activates registration and removing only that directory leaves no class/service orphans. Providers depend on contracts rather than concrete integration/storage owners. No old optimization was imported; the development identity and deliberately failing providers never ship in the main JAR.

The live [Asahi evidence](evidence/asahi-functional.json) preserves all attempts and schema evolution. A necessary old native binding-index correction was reimplemented after inspection separated combined texture-unit indices from fragment resource counts. This fixes absent-DH native allocation without modifying shader archive bytes, options, resolution or AA. Required exact-descriptor Iris callbacks journal native serialization and record pipeline frame submission. LINKED remains distinct from ACTIVE; v3 verifies nonempty rendered sections and output hashes after bounded settling. Original/identity output was visually inspected for terrain, sky and HUD. No image-equivalence threshold or performance claim was invented.

The shipping main JAR passed absent-DH native selection, unchanged-TAA configuration, submitted identity-provider frames, unapplied Cancel, Cancel while active, Off and injected compile-failure restoration. Native options and dimensions were preserved, declared executable inputs stayed unchanged and no recovery journal remained. Bounded CPU and asynchronous GPU observations carry generation/frame ownership and explicit loss/pending health; they qualify collection only, not calibrated overhead or performance. These receipts retain diagnostic instrumentation as a separate nonshipping mod.

The DH-enabled original still fails composite1 with “Too many fragment shader texture samplers” on Asahi, including the independent native reference without ShaderLOD. With the final shipping JAR it again fails before port activation; read-only DH presence/config is observed but rendered DH coverage is unqualified. This is the remaining required D gate. No setting reduction or alternative-machine result substitutes for it. Further native compatibility changes that alter shader behavior/quality or the accepted runtime scope require a consequential decision.

Linux CPU interruption tests kill the submitting runner, verify its watchdog stops owned children/grandchildren and leaves a foreign process alone, then reconcile only a quiescent recorded group and retain its spent attempt budget. Missing ownership metadata, another host/boot and unresolved lifetimes refuse. Windows runtime supervision/cross-host transfer, exhaustive loaded-system-library attestation, per-pass profiling and calibrated overhead remain unavailable; no broad platform or profiler qualification is implied. Benchmark redesign, real optimization research and high-level skills remain deferred. The old repository and pre-existing unrelated next-repo edits are preserved; work remains local-only with no remote.
