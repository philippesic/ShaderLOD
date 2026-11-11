# Benchmark design

## Goal
Build three connected development workflows: a profiler that explains rendering cost, validation that establishes correctness and provides a quick performance screen, and full comparison that supports a retention decision. Each produces human-readable interactive reports and the structured data needed by agents and independent analysis.

## Authorized
Design and research, including repository integration and implementation sequencing. Philip requested the profiler, validation and full-comparison plans, interactive profiling, performance timelines within and across runs, and consideration of shader-specific profiling. The requirements inventory was challenged in two Sol debate rounds; the revised technical inventory had no remaining blocking objection. This does not authorize implementation, new live experiments, another machine, new acceptance thresholds or relaxed native quality. Existing routine development-direction repair authorization remains in force.

## Done when
Philip has settled the remaining product, coverage and resource choices; the maintained entry points implement the agreed workflows; and an authentic path through capture, failure handling, analysis, reporting and closeout is qualified on the allocated machine. Qualification must include relevant controls and adverse cases, not just successful fixtures. No accepted optimization is required to finish the benchmark system.

## State
Owner: the active benchmark-design conversation, main in /home/philip/ShaderLOD-next. Everything described below is a proposed implementation contract unless explicitly marked existing. No benchmark implementation or new live run has begun. Keep this plan in tasks while unfinished; move implemented procedures into current topic documentation as they become real.

**1. System boundaries and terminology**

One runner, evidence format and report system serve three workflows. These are not three independent launchers.

| Workflow | Primary decision | Output meaning |
| --- | --- | --- |
| Profiler | Where is time/work spent, and does the mechanism do what we expect? | Diagnostic explanation with attribution and coverage limits; no automatic acceptance |
| Validation | Does this candidate function correctly on selected cases, and is it worth a broader comparison? | Separate functional/visual checks and exploratory performance-screen results; never a retention verdict |
| Full comparison | Is the frozen candidate useful and safe across the agreed scope? | Per-case effects, uncertainty, regressions, coverage and a recommendation under an explicit policy |

Validation combines the earlier fast screen with correctness checks. Shared harness qualification is a prerequisite for all three, not a fourth routine benchmark. A validation correctness failure blocks promotion; an inconclusive speed screen can justify further investigation without pretending it passed performance acceptance.

Original reference means the original shader with ShaderLOD loaded and the tested optimization disabled. A mod-absent control measures ShaderLOD overhead separately. Candidate means exact changed code/artifacts and enabled modules. Incumbent means a previously retained configuration; compare combinations with both the incumbent and original. Do not replace the original reference or add singleton gains to predict a combination.

Preserve native shader settings, resolution and AA. Iris is mandatory. DH/PBR support requires observed rendering in an appropriate workload, not presence/configuration alone. Beneficial scene properties are not automatically enforceable product eligibility. Test unfavorable conditions inside the real activation envelope or narrow the claim. Whether retention may be scoped or must be broadly positive remains Philip's decision.

**2. Shared execution and evidence foundation**

Existing components: shaderlod_host CLI, config/storage owners, bounded CPU executor and recovery journal, Linux live watchdog/profile preparation, functional-v3 validator, development-only profiler, and close-results. Existing ./check --full remains the portable gate. Existing ./verify --runtime remains the documented functional smoke path until integration is exercised.

Current limitations: the CLI Request contract admits CPU purposes only; do not add a comparison label and thereby imply live support. DevelopmentProfiler measures render-callback wall duration, not full-frame pacing or thread CPU use. Its 4096 CPU samples and 64 completed GPU pairs are rolling diagnostics, not complete benchmark windows. ProfileExport is manual. There is no qualified scene controller, comparison analyzer or visual oracle. Live attempts and CPU attempts have separate lifecycle details that must be reconciled deliberately, not assumed interchangeable.

Proposed lifecycle: resolve plan and estimate cost -> verify allocation and inputs -> reserve attempt budget -> prepare an owned working profile -> launch -> verify loaded identity/activation -> warm up under the declared rule -> execute workload/capture -> drain observations within a bounded allowance -> restore/stop -> publish immutable attempt -> analyze/report -> close investigation if warranted. Failure at any stage must retain its stage, cause, partial outputs and restoration status. Readiness and finalization times are outside steady-state timing but remain outcomes.

A shared operation owns the deadline, attempt limit and cumulative budget. The existing worker/watchdog is the process owner; benchmark code must not launch its own competing supervisor. Resume validates source/protocol/input identities and quiescence, then starts a new attempt for unfinished work. Never resume halfway through a measured statistical block, refund a failed attempt, rewrite a completed receipt, or silently mix revisions. An explicitly new protocol starts a separate comparison.

Prepared profiles and world snapshots are immutable inputs or owned working copies. The normal development path reuses a supervised client session and a prepared world slot; a run is a measurement operation, not necessarily a new JVM or world copy. Use shaderlod_host.storage.copytree and its capacity checks. Plans estimate trace/capture/working-copy storage and bound recorders. Capacity exhaustion yields retained incomplete evidence, not silent eviction or automatic deletion of unrelated artifacts. Disk policy remains an agent-ops decision; no scheduler or lease service is added.

**Session reuse and workload reproducibility**

Philip's direction: use reusable worlds and command-driven movement, like the old benchmark, to keep iteration short and prevent uncontrolled world/copy growth. Reuse is the default, not an optional late optimization. It does not mean an indefinitely running unowned client or a mutable world treated as a pristine reference.

- Keep one canonical, identified world snapshot and a small bounded pool of prepared writable slots. Use the existing capacity-checked copy path for initial preparation or an explicit reset, not a new full copy per measurement. Never modify the canonical snapshot or restore world files while the game has them open. World seed alone is not a sufficient identity.
- Pregenerate the finite route regions used for steady-state tests. Bound position/dimension and permitted route extent; refuse unexpected excursions instead of continually generating terrain. Streaming tests use declared preparation and residency states and separate slots/reset rules when necessary; repeated warmed traversal must not masquerade as cold generation/loading evidence. Pregenerated chunk loading and new terrain generation are distinct workloads.
- A session owns the JVM, loaded build identity, working profile, deadline and existing host lock across several sequential operations. Operations have separate capture windows, receipts and limits. A bounded idle allowance and total session deadline keep a client from living forever. Use the existing watchdog/ownership model, not a new background service or GPU lease manager. Session close stops its owned process and finalizes restoration; operation close restores the declared reusable state and records the remaining session owner.
- Add session open/status/close to the maintained CLI and a --session reference to profile/validate/compare. A session reference must match host/boot/process birth, loaded build, allocation and protocol capabilities. No implicit attachment to an arbitrary Minecraft process. Keep a fresh-session mode for qualification and required isolation.
- Execute typed commands for selecting an admitted workload/pose, restoring declared state, applying original/candidate, playing a finite route, capturing a window and restoring. Commands carry sequence IDs; acknowledgements identify the observed state/generation and actual checkpoint. Do not blindly replay a timed-out Apply or route command; reconcile observed state first. Commands are a shared controller contract, not ad-hoc chat commands or candidate-specific launcher scripts.
- Routes declare their simulation-time or monotonic-time progression, waypoints/interpolation, duration and assertions. Record actual position, orientation, tick, dimension and completion. Render-rate-dependent movement and capture-writer pacing must not change the route. Still-pose, real-time flight and temporal-quality replay remain distinct modes.
- Before reuse, check weather/time rules, relevant entities/random state, options, temporal history, pending chunk/DH work and cache preparation according to the workload. Equal coordinates alone are insufficient. Do not reset away a candidate's actual sustained-work harm; report time-to-readiness/backlog separately. If the required state cannot be recreated, mark that comparison invalid or use the explicit reset/fresh-session path.
- Reuse loaded code only while its exact identity is unchanged. Shader artifacts/options can change through the verified Apply/reload path; main Java, mixin, native dependency or incompatible dev-probe changes require a restart. Rebuilds must not silently replace classpath inputs of a live client. Skip a reload only when the loaded artifact/options/generation and progressing readiness are positively acknowledged unchanged.
- Same-session blocks reduce turnaround and improve matching, but share caches and history. They are correlated, not independent fresh-process replicates. Full comparison includes fresh-session blocks when process effects or reset limitations matter; restart the client while reusing the same prepared world infrastructure. Pilot carryover checks determine what must reset.

**Proposed turnaround budgets, not measured runtimes**

These are initial engineering targets for one allocated machine with dependencies/builds cached and an already healthy reusable session. They are proposals, not user-approved resource ceilings or statistical sufficiency claims. Planning must itemize build/startup, Apply/reload, readiness, capture, quality review/export and report costs; human inspection time is separate.

| Operation | Initial target | Scope and limit |
| --- | --- | --- |
| Frame-only profiler | 10–30 seconds capture; roughly 20–60 seconds to a report | One ready workload, no shader reload; deeper pass/CPU tracing needs its own cost estimate |
| Focused functional/visual validation | Roughly 2–5 minutes | A few affected cases and lifecycle actions; expensive reloads may dominate |
| Validation with a quick performance screen | Roughly 3–8 minutes total | A small original/candidate/return comparison with explicit warmup and motion sanity; not retention evidence |
| Bounded full comparison | Roughly 20–45 minutes for an initial single-pack, single-machine cohort | Illustrative 3 cases x 3 blocks x 3 arms x 20-second measured windows is 9 minutes of capture alone; settling/reloads, independent sessions and quality evidence add time. This is a cost example, not an adopted repeat count |
| Broad qualification campaign | Planned as multiple bounded comparisons | Several packs, machines or noisy cases can take hours; never describe this as a short routine check |

Cold setup/startup, slow shaders, source rebuilds and unstable workloads are additional costs measured by the pilot. Do not inherit the old blanket 120-second conditioning on every operation; replace it only with qualified arm-symmetric readiness/reset rules. The historical original/candidate/return path used 20-second windows within a 900-second cap, and historical functional examples took minutes; those records demonstrate overhead, not a speed promise for the new system.

When the budget cannot resolve the effect, finish inconclusive with preserved evidence and a next-step estimate. Never shorten scientifically necessary coverage, silently extend the run, or choose favorable windows to satisfy a wall-time target. Reuse unchanged immutable inputs, validated schemas/mappings and unaffected qualification; fresh paired baseline windows still matter when current thermal/session conditions can drift. A historical baseline point is not a free replacement for a contemporaneous control.

**3. Maintained entry points**

Extend python3 -m shaderlod_host with typed workflow modes. The following commands are proposed and do not exist yet:

```text
python3 -m shaderlod_host profile --spec benchmarks/profiles/example.json --plan
python3 -m shaderlod_host validate --spec benchmarks/validation/example.json --plan
python3 -m shaderlod_host compare --spec benchmarks/comparisons/example.json --plan
python3 -m shaderlod_host profile --spec SPEC --allocation HOST_ALLOCATION
python3 -m shaderlod_host validate --spec SPEC --allocation HOST_ALLOCATION
python3 -m shaderlod_host compare --spec SPEC --allocation HOST_ALLOCATION
python3 -m shaderlod_host report --result RECEIPT
python3 -m shaderlod_host verify --result RECEIPT
python3 -m shaderlod_host recover --operation OPERATION --ownership-root ROOT
```

Names such as example.json are illustrative. --plan is read-only: resolve inputs, show comparisons, expected capture modes, eligibility, reused qualification, missing capabilities, time/storage estimates and limits without reserving hardware or launching. Allocation identifies an explicitly authorized host/display/ownership root and bounds; a file cannot grant authority absent from the session. Live execution requires both specification limits and the matching allocation. Keep old CPU request modes working; add a versioned WorkflowPlan rather than weakening Request v1.

report rebuilds derived output offline from completed or clearly partial records; it does not launch or modify raw evidence. verify validates identities, schema, completeness and links; it does not elevate evidence level. recover extends the current recovery path for workflow operations. CLI output gives operation stage, concise verdict, evidence scope, report/receipt paths, missing coverage and a concrete repair. Structured output goes to a stable JSON mode and log progress to stderr; agents never parse decorative prose.

Process exit status describes execution, not desirability: 0 means a mechanically completed/valid workflow; 1 means failed execution or invalid capture; 2 means refused request or unsupported required capability. A measured regression may exit 0 while its structured assessment says regression. Validation correctness failures are explicit in the assessment and block comparison admission even if the execution completed. Define these cases in CLI tests and guidance so agents cannot treat exit 0 as acceptance.

**4. Plans and data contracts**

Track small authored definitions under benchmarks/: workloads, profiles, validation protocols, comparisons, shader mappings and schemas. Do not populate a speculative suite for every shader. Host-private allocation paths stay outside Git. Runtime artifact versions remain authoritative in dependencies/runtime.lock.json; plans reference identities rather than duplicating them.

A resolved plan contains: schema/protocol identity; workflow; question and allowed claim; original/candidate/incumbent identities; eligibility; workload references and exposure; actual runtime/configuration requirements; progression/alignment rules; warmup and measurement boundaries; capture modes; sampling/missingness rules; primary outcome and auxiliary metrics; order/random seed; repetition/stopping policy; correctness and performance criteria; retry/resource limits; required prior qualification; output root. Reject unknown fields/units and impossible combinations early. No hidden automatic weakening when a capability is absent.

Artifacts live under the existing external evidence root, proposed layout operations/<operation>/attempts/<attempt>/ with immutable manifest.json, events.jsonl, frames.jsonl, optional gpu.jsonl/counters.jsonl, capture manifests and logs. Retain finite numeric values with explicit units; missing data has a reason, never a fake zero. A completion receipt identifies hashes, byte sizes, schema/tool versions and source closure. Stream chunks safely and finalize once; partial chunks remain marked incomplete on failure. A rebuilt report is a separately identified derivative, never a rewrite of the attempt.

| Record | Essential facts |
| --- | --- |
| Manifest | Built and loaded code/artifact identities, dirty-source snapshot, effective options/resources, OS/driver/GPU/JVM, display/native quality, instrumentation/adapter revisions, workload ancestry, allocation/budgets |
| Frame | Run/attempt/arm/block/window, sequence and generation, monotonic timestamp, application frame interval, declared CPU scope when enabled, simulation tick/route checkpoint, validity and event links |
| GPU observation | Frame/generation/span ID, raw program identity, clock domain, start/end or elapsed units, query availability and sampling probability/rule; lost/pending/invalid observations are separate records/counts |
| Counter | Metric ID, unit, scope, value and opportunity denominator; sampled/exact/estimated status; never imply shader instruction counts from host call counts |
| Event | Load, warmup, capture, reload, cache reset, route step, GC/queue/pressure observation when enabled, failure and restoration, with clock domain |
| Visual capture | Source frame/pose/simulation-time/history state, cadence and gaps, image/video hash, color/exposure representation and matched-reference identity |
| Summary | Execution status, validity by metric family, completeness, functional/visual outcomes, performance assessment, per-case uncertainty/regressions, evidence level and missing claims |

Use JSON summaries and compressed JSONL streams initially: inspectable, streamable and no mandatory database. Introduce a columnar format only if measured scale warrants it, without changing semantics. GPU and CPU clocks are distinct; frame IDs associate observations but do not invent synchronized timestamps. Rejected attempts stay in reliability accounting even when no valid timing effect can be estimated.

**5. Profiler design**

Purpose: inspect frame-time variation, find costly paths and test a mechanism on a real workload. Accept original or candidate configurations; comparisons made while deeply instrumented remain diagnostic until observer effects are qualified.

Capture tiers share the same event model:

- Frame mode: complete application-frame intervals, phase markers, lightweight validity/condition observations and requested bounded CPU scopes. This is the base for outcome comparisons. A complete frame stream must not be a trailing ring-buffer sample.
- Pass mode: sampled asynchronous GPU spans and associated CPU submission scopes at actual renderer boundaries, with raw program/pass IDs, geometry/draw/dispatch/target facts where obtainable and requested counters. Record all sampling and missingness. Do not stall for query results or adaptively favor cheap frames. Bounded query pools and buffered off-thread writes need overhead tests; full queues create visible gaps, not hidden loss or route slowdown.
- Targeted mode: a specific adapter adds counters or CPU/JVM/queue diagnostics for the question. It declares cost, capabilities and qualification requirements. Source-altering probes or effect ablations create separate artifacts/runs and cannot become native-quality acceptance evidence.

The base collector works on unrecognized shader packs within an admitted runtime. It can report frame and verified renderer/program boundaries without semantic effect labels. Lack of a shader mapping must not prevent generic profiling; lack of a required timing capability must produce a limited report or explicit refusal according to the plan.

**Profiler extensibility: three layers, one pipeline**

| Layer | Responsibility | Change boundary |
| --- | --- | --- |
| Generic collector | Frames, clocks, buffered records, spans, sampling, query lifecycle, validity | Independent of pack names; adapters emit typed observations |
| Iris/runtime adapter | Actual stage/program/compute/draw boundaries and source-to-loaded-program identity | Pinned Iris/Sodium/DH integration; verified selectors plus live qualification |
| Shader semantic mapping | Describe which known programs/variants implement categories such as shadows, AO, fog, reflections or tone mapping | Data keyed by exact supported pack/member identities, option/dimension predicates and graph signatures |

Add a narrow optional code adapter only when data mapping cannot observe the required mechanism. It must consume declared hooks and emit typed counters, not own a launcher, report renderer, arbitrary file writes or hidden shader edits. Keep observational adapters independent of OptimizationProvider, which owns transformations. The profiler must work with ShaderLOD optimizations absent or disabled too.

A mapping declares its ID/version, admitted pack/member identities, relevant effective options and dimensions, expected graph nodes, raw-pass selectors, semantic labels, provenance and tests. Bind to loaded graph/program identity, not filenames alone. A changed/derived shader requires explicit compatible lineage or a new mapping; otherwise labels become unmapped while raw observations remain available. Unknown options/graphs do not inherit labels by pack-name similarity. Mapping revisions are evidence identities and cannot silently rewrite old reports.

Distinguish three facts in every report: observed timed boundary; semantic label supported by source inspection; causal attribution supported by a controlled experiment. Semantic membership is not a measured split. Hypothetical example: one composite program contains fog and reflection work. Report its measured span as "fog + reflections (combined)"; do not invent separate milliseconds or divide by source length. Several programs may implement one effect, and one program may implement several effects. Show shared nodes once and link their tags; any aggregate needs declared non-overlapping spans and sampling-compatible weighting. Otherwise mark non-additive. No forced 100% stacked chart or fabricated "other" from subtraction across overlapping scopes.

On tile-based GPUs, pass placement can change timer meaning. Qualify counter support, useful resolution, sampling and perturbation response before presenting pass rankings as useful attribution; disclose the hardware limitation. CPU wall, CPU thread work, GPU elapsed and display intervals remain separate tracks. A per-pass timing is not exclusive hardware occupancy or an effect-body timer.

Admission path for a new shader: inventory loaded graph and options -> inspect relevant source -> author a minimal mapping -> test supported/unknown variants -> exercise its real boundaries/coverage -> qualify observer effects -> publish its supported scope. First implement generic collection and one real mapping for the selected pack; prove an unmapped pack remains usable. Do not build a universal shader parser or require adapters for all historical packs up front.

**Profiler human and agent output**

A local, offline report.html opens without a server or network. It contains a zoomable/pannable frame-time graph, selectable CPU/GPU tracks, phase/event markers, interval selection, raw point tooltips and a synchronized pass/category table. Selecting a slow interval shows relevant events, program spans and captures where recorded. Show capability, missingness, sampling and validity near the graph, not buried in logs. Provide a compact findings-oriented summary: where variation occurred, what was actually observed, and what cannot be concluded.

The report uses the same versioned analysis output as summary.json. Include distributions, named window summaries and links to exact raw records so agents can ask "which intervals regressed and with what coverage?" without scraping HTML. UI downsampling must preserve extrema and gaps and identify aggregation; analysis uses the complete admitted data, never downsampled display points. Smoothing is optional and labeled, with raw data accessible. Invalid windows remain visible.

A small self-contained renderer with bundled HTML/JS/CSS is the proposed first implementation, using Canvas/SVG where appropriate. No remote scripts or heavyweight dashboard service. Keep report rendering behind one interface if a library later proves necessary. Embed bounded display data for portable viewing, retain exact streams separately, and offer compact CSV/JSON exports and a static figure for results pages. Escape labels and embedded data safely. An unsupported browser or missing raw asset gives a readable summary, not a blank report. Report portability, offline loading and trace/render performance are tested.

Profiler completion criteria: generic frame capture plus one qualified pass/mapping path; wrong-version mapping refuses semantic labels; combined/unmapped/overlapping work is honest; query exhaustion and source drift are visible; human selections and agent summaries agree; overhead is measured; no optimization or acceptance claim follows merely from a diagnostic report.

**6. Validation design**

Purpose: test candidate behavior cheaply enough for development and provide a bounded screen before expensive full comparisons. Reuse ./check --full for portable source/contracts and the existing FunctionalProbe/verify_functional behavior; never replace them with a producer PASS flag.

A validation specification names the candidate scope, a small set of admitted cases, required functional/visual checks and optional screen protocol. The output has separate functional, visual, measurement-validity and exploratory-performance assessments. Inactive, unsupported, blocked, failed and inconclusive are distinguishable. No single green badge means all scopes passed.

Sequence:

1. Plan/preflight: verify inputs and supported selectors, native settings, eligibility, workload availability and allocation; determine affected reusable qualification. A failed CPU gate or required unsupported runtime capability refuses launch.
2. Functional smoke: original and candidate actually link/render; activation joins expected artifacts/generations; no fallback; execute relevant Cancel/Off/failed Apply and restoration. Test unsupported inputs and settings changes where the candidate changes those paths.
3. Representative cases: opportunity-rich and opportunity-poor/adverse workloads, with a real moving-view check early. DH/PBR cases observe actual rendering/material use when required by candidate scope. Pick cases from mechanisms, not a fixed four-pack inheritance.
4. Visual validation: separate untimed stills and short sequence captures with matched pose/time/history semantics, reference variability, targeted region inspection and optional perceptual maps. Record cadence/gaps. Temporal candidates add disocclusion, cuts, reset and convergence. Keep quality and performance capture modes separate so writer backpressure cannot alter speed or route timing unnoticed.
5. Optional fast performance screen: declared balanced original/candidate blocks, fixed progression and arm-symmetric warmup/admission. Retain readiness latency and non-settling outcomes. Collect minimally instrumented frames, time-series drift, throughput, p50/p95 and adequately supported tails/slow-frame counts. Repetitions and budget come from pilot sensitivity. Report promising/regression/inconclusive, never retained.
6. Restore, publish, report and identify the next justified action. A correctness failure blocks admission to full comparison. Limited visual coverage remains limited rather than becoming automatic equivalence.

Harness qualification, distinct from every validation run, must show detection of a known slowdown, an inactive candidate, missing output and bounded known visual defects. It must preserve partial failures and recover interrupted ownership safely. Test controls qualify only the exercised classes; no claim of detecting arbitrary bugs. Unchanged-versus-unchanged trials characterize false positives/noise. Validate threshold direction and units with hand-verifiable fixtures.

Validation report: concise case/check matrix, expected versus observed behavior, before/after/difference stills, sequence access, explicit human-review status, restoration facts and per-case screen timelines. Its structured result binds every check to evidence and states missing cases. Human review decisions are separately recorded with identity/date/scope; image hashes do not substitute for review. No screenshots or footage are required from every frame or every successful routine invocation.

Validation completion criteria: authentic candidate/reference lifecycle exercised; the known fault classes are caught; malformed and incomplete results cannot promote; fast-screen statistics reproduce from the captured data; performance and quality modes do not contaminate one another; cost fits the chosen iteration budget. It does not establish retention, wide compatibility or display latency.

**7. Full-comparison design**

Purpose: evaluate a frozen candidate or combination against original and, where relevant, incumbent over an explicitly chosen scope. Requires compatible measurement qualification and applicable candidate validation. A limited or stale prerequisite is displayed and cannot silently qualify omitted cases.

Before running, freeze protocol identity, primary outcome and effect denominator, per-case regression policy, candidate-selection handling, repetition/stopping rule, useful effect and harm thresholds, workload set and exposure, operating mode, capture obligations and resource limits. User choices are unresolved; the tool must not invent them. Exploratory analysis is labeled separately. Confirmation used to tune a revision becomes development evidence for that revision.

Run plan:

- Use independent process/session blocks as justified by pilot variance and reset requirements, not correlated frames as independent replicates. Balance/randomize order with a recorded seed. Within-process comparisons require tested restoration of relevant caches/history; retain carryover checks and use fresh processes when a reset cannot be established.
- Match workloads by declared progression clock and checkpoints. Separate fixed-state throughput, real-time movement, loading/streaming and transition-inclusive outcomes. Actual route duration/ticks and useful completion must match the comparison's question. Do not select a favorable stable suffix or quietly extend warmup for one arm.
- Repeat on additional independently chosen scenes/routes and relevant environments inside the actual activation scope. Cases include expected opportunity, little opportunity and plausible harm. Native cap/VSync results remain distinct from explicitly authorized uncapped headroom diagnostics. A single machine supports a single-machine conclusion.
- Capture complete lightweight outcome streams. Frame throughput is count divided by elapsed time; frame duration uses declared boundaries. Preserve distributions, slow-frame episodes, variation and independent-block effects/uncertainty. GPU/CPU diagnostics explain only the cases they actually cover and are not mandatory for a bounded frame-production result.
- Revisit motion/correctness and transitions on the frozen artifact. A sustained representative segment tests drift, memory/cache evolution and backlog. Mechanisms that defer work require completion/recovery evidence; mechanisms with preparation overhead report cost and recurring benefit separately. Session-level value needs an agreed usage horizon or break-even analysis.
- Keep every attempt and case visible. Invalid captures are excluded from timing estimands only under the declared rule, with reliability counts retained. A candidate causing failures or non-settling cannot disappear through exclusion. Per-case harm cannot be hidden by a pooled average; any aggregate has explicit weights and is secondary to coverage/individual cases unless an agreed policy says otherwise.
- Finalize with complete/incomplete execution, validity per metric, supported/blocked/untested coverage, correctness outcome and policy recommendation. No retention decision is emitted when required policy or evidence is missing. Re-run only affected qualification after changes, based on recorded dependency identities.

**Performance over time: required report views**

| View | X-axis and alignment | What it reveals |
| --- | --- | --- |
| Within one run | Monotonic elapsed seconds; phase boundaries; optional declared route position | Warmup, stutters, gradual drift, transitions and capture gaps |
| Across matched arms/runs | Elapsed time since the same phase or route checkpoint, selectable overlays and small multiples | Baseline/candidate behavior at multiple comparable points rather than a single average |
| Across development history | Chronological run/block date/order with source/protocol IDs; baseline and candidate points plus uncertainty | Whether gains persist across sessions/revisions and whether the baseline itself drifted |

Preserve real elapsed time and raw event identities. Do not stretch traces to align frame counts, join gaps or pointwise subtract unmatched stochastic frames. Matched route/window deltas are valid only under the declared alignment; report sample counts and run-level uncertainty at each checkpoint. For static scenes without a common progression, time overlays are descriptive and block summaries carry inference. Show all repeats individually before an optional aggregate. Percentile/rolling curves use declared window units, sample support and labeled smoothing; repeated time points are not independent significance tests.

The chronological history view is regenerated offline from immutable comparison manifests, not a new mutable database or commit-history diary. Include failed/incomplete attempts as annotated markers. Partition incompatible hardware, driver, pack/settings, workload, cap/resolution, protocol, instrumentation or mapping identities into separate series. Do not connect incomparable points or pool distributions across them. An intentional bridge comparison can establish a labeled relation but cannot retroactively rewrite old evidence. Baseline drift stays visible alongside relative candidate effects.

Report contents: outcome and coverage summary; these three interactive views; case distributions and independent-block effects; regression table; quality evidence; setup/memory/backlog costs where applicable; uncertainty and limitations; exact machine-readable exports. Drill down to individual attempts and profiler evidence without converting instrumented diagnostics into acceptance timing. Rebuilding a report preserves raw data and records analyzer/report version.

Full-comparison completion criteria: the agreed matrix executes or explicitly accounts for every required missing case; known regression/no-op/missingness controls behave correctly; numerical summaries and charts agree with raw data; eligibility and policy are explicit; retained recommendation can be traced to independent evidence and quality checks. A complete inconclusive comparison is a legitimate outcome.

**8. Repository implementation map**

| Location | Planned responsibility |
| --- | --- |
| shaderlod_host/__main__.py | CLI routing for profile/validate/compare and offline report/verify/recovery; no extra launcher |
| shaderlod_host/contracts.py plus a focused benchmark contract module | Versioned plan/run/observation/summary types, strict decoding, capability and compatibility checks |
| shaderlod_host/runtime.py, runtime_live.py and worker.py | Reuse process ownership, deadlines, attempt accounting, preparation and recovery behind one workflow lifecycle; retain CPU/live distinctions |
| shaderlod_host/config.py and storage.py | Sole configuration and publication/copy owners; atomic manifests, preserved partial data and capacity checks |
| Focused workload, analysis and report modules under shaderlod_host | Scene orchestration, pure reduction/comparison, offline human report; no hidden process launches or direct alternate persistence |
| src/benchmarkCore | Dependency-free observation/schema/validity contracts reusable by dev capture and tests |
| src/devClient | Scenario controller, functional tests and capture; excluded from shipping JAR |
| src/devClient/.../measurement | GPU query ownership and runtime observation adapters; collectors call this boundary |
| Development-only integration hooks | Pinned Iris pass/program hooks only where needed; no profiler behavior in ordinary shipping code |
| benchmarks/workloads, profiles, validation, comparisons, shader-mappings, schemas | Authored definitions and small admission fixtures, all tracked; raw worlds/captures remain external |
| tests and src/test | Contract, analysis, report, adapter and lifecycle tests; focused fixtures, no blanket snapshots of changing product output |

Verify the exact pinned dependency before choosing hook sites. Inspection of the installed Iris 1.8.12 JAR found CompositeRenderer.renderAll(), a private passes list and per-pass names/programs/computes; these are candidate integration seams, not a public profiling API or proof of injection safety. Current Iris docs describe program stages but may include features newer than the pinned runtime. Prefer available supported events; isolate necessary mixins/accessors in the dev runtime adapter, cover selectors against the pinned JAR and qualify actual boundaries live. Do not patch dependency binaries or scatter shader-name conditions through core code.

Update ArchUnit/package audits for new owned modules while preserving shipping exclusion and state ownership. Include dev-only mixin descriptors in selector validation. Keep GL calls under the measurement adapter; do not bypass D6c. Any narrow exception requires the existing exact exception procedure. Add report assets/schemas to the language and authored-asset policy with meaningful tests; do not globally exempt JS/HTML or large JSON. Checks and Gradle cache inputs must cover schemas, shader mappings, renderer assets, qualification fixtures and protocol definitions so edits invalidate affected checks.

The existing no-comment convention applies to added code. Rendering assets are source, not ignored generated output. No remote CI is added; local portable tests remain in ./check --full, with separately invoked live qualification.

**9. Agent navigation and routine workflow**

When implementation lands, README lists the three commands and their decisions; docs/development.md routes agents by intent; docs/runtime.md owns allocation, launch and restoration; new docs/benchmarks.md documents the real workflow/specs/metrics and limitations; docs/profiling.md owns adapter admission and attribution rules; docs/results.md retains the established closeout and evidence roles. Update these with working features, not placeholder claims. Propose a short AGENTS.md navigation addition for Philip, preserving his ownership of that file.

Add one project skill, skills/benchmark/SKILL.md, once there is a usable end-to-end procedure. It should route an assigned question to profile, validation or comparison; read the current task and relevant FINDINGS; plan before live work; inspect cost/allocation; run the bounded workflow; interpret execution, validity, quality and performance separately; invoke close-results when warranted. Do not make three copies of the same procedure or embed thresholds in both skill and protocol.

Typical agent path: inspect prior findings -> state the optimization mechanism and permitted claim -> plan a profile if the bottleneck is unknown -> implement within the assigned scope -> portable checks -> validation -> frozen full comparison when justified -> concise result/FINDINGS/cleanup. A fast validation screen can stop an unpromising direction without a full comparison. A profiler is not mandatory when the mechanism and required evidence are already known. No workflow automatically grants more experiments or modifies thresholds to obtain acceptance.

Reports give agents a short JSON summary first and paths to exact details. Durable conclusions go to results/<question>/result.md with useful static figure/compact data and a link to the full interactive report. Full captures and per-frame streams stay in external artifacts. FINDINGS indexes conclusions, not every run. A comparison request does not automatically close an investigation; source preservation, implementation disposition and remaining-project validation still follow close-results.

**10. Implementation and qualification sequence**

| Step | Deliverable | Exit evidence |
| --- | --- | --- |
| 1. Freeze scope and one vertical slice | One allocated host, original pack, workload, candidate/test control and capture boundaries; time/storage/quality policies | Reviewed executable plan with unresolved requirements refused, not defaulted |
| 2. Shared capture and reporting | Typed plans, bounded stream capture, full-frame timeline, structured summaries, offline report | CPU tests for gaps/units/identity/corruption, hand-recomputed summaries, report smoke/accessibility/offline checks; one authentic bounded capture |
| 3. Validate the instrument and lifecycle | No-op/known slowdown/known visual defect, warmup accounting, observer checks, timeout/recovery/restoration | Preserved success and failure receipts; useful sensitivity and collection cost on selected host |
| 4. Profiler extension | Generic renderer spans and one shader semantic mapping plus unmapped fallback | Actual graph/boundary evidence, stale-map refusal, non-additive labeling, query limits and measured overhead |
| 5. Candidate validation | Reused functional probe, representative quality cases, optional fast screen | Active/inactive/failed candidate cases distinguish correctly; affected quality review complete; screen does not claim retention |
| 6. Full comparison and history | Frozen comparison plans, independent-block analysis, all three timeline views and report catalog | A no-op and controlled regression are interpreted correctly across repeated runs; incompatible series separated; partial attempts accounted |
| 7. Expand agreed coverage and close design task | Additional selected workloads/adapters/platforms and documented operation | Coverage-specific qualification, settled agent workflow, reviewed cost, current docs/skill and preserved evidence |

Steps are small milestones, not permission to build all future capabilities immediately. A tiny test control may exercise the system without being a retained optimization. The first selected pack can be Reimagined because inputs exist, but its Asahi+DH native failure makes that cell blocked; a no-DH pilot must be explicitly scoped and never substituted for DH qualification. Machine choice can change the first authentic path.

Cost forecasting precedes execution. Estimate frames from observed rate and duration, stream bytes from measured bytes/record, spans from selected passes and sampling, visual volume from frame dimensions/cadence/encoding, and profile copies from actual allocated storage. Measure costs in the pilot; do not promise arbitrary seconds or repeat counts. Reports distinguish expected versus worst-case allowed time/storage. Agent ops owns cleanup defaults and retention budgets.

**11. Research, sufficiency and unresolved choices**

Technical requirements were challenged by Sol and revised for progression equivalence, warmup harm, differential observer effects, frozen decisions, enforceable eligibility and defect-detection sensitivity. The agreed principle is that every identified material way to choose the wrong candidate needs a check or an honest claim limitation. Further mechanisms can add focused requirements without requiring an exhaustive suite now.

Sources and boundaries:

- [Khronos timer queries](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_timer_query.txt): asynchronous completion, separate timing domains and tile-rendering placement caveats.
- [Iris program overview](https://shaders.properties/current/reference/programs/overview/): stage/program structure; names do not establish effect cost. Newer docs are not proof of pinned 1.8.12 support.
- [PresentMon](https://github.com/GameTechDev/PresentMon): possible platform-specific frame/display cross-check with documented OpenGL limits; not an adopted dependency or available Asahi capability.
- [Kalibera and Jones](https://kar.kent.ac.uk/33611/): repetition at relevant variance levels and effect uncertainty, not universal repetition counts.
- [VM warmup study](https://arxiv.org/abs/1602.00602): fixed warmup does not guarantee stable VM behavior; no Minecraft-specific duration follows.
- [NVIDIA FLIP](https://github.com/NVlabs/flip): image differences assist localized inspection, not universal temporal equivalence.
- [JDK 21 JFR](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshoot-performance-issues-using-jfr.html): diagnostics can perturb execution; observer cost is workload-specific.
- Local results: asahi-null-calibration, incomplete-gpu-cost-capture, rtx5080-rendering-review, early-rendering-contract-lessons, combination-effects and profiling-and-benchmark-design-lessons preserve relevant historical failures.

Not required by default: all-pass/full-counter tracing on every comparison, a universal shader parser, a dashboard server, a new telemetry database, all-settings/all-machine Cartesian coverage, permanent full-session video, external power equipment or input-to-photon apparatus. Add these only for a specific decision that needs them. Missing GPU diagnostics can coexist with valid frame-production evidence; missing presentation observations prevent claims about displayed smoothness. Known controls establish specific sensitivities, not universal bug detection.

Still requiring Philip's choices: selected machine/allocation; initial shader/workload and DH/PBR coverage; confirmation or revision of the proposed turnaround budgets; broad versus scoped retention; worthwhile improvement and tolerated harm; visual acceptance criteria within the native-quality boundary; iteration/full-comparison budgets; storage/preservation policy with agent ops; session horizon if preparation amortization matters. The architecture and data contracts above can be reviewed before those numerical choices, but a full acceptance run cannot invent them.

## Next
Review these three workflow plans, especially shader-specific mapping versus measurable attribution and the three time-series report views. Review the reusable-session/route design and proposed turnaround budgets, set the remaining scope/resource/acceptance choices and authorize the first implementation milestone. Then implement and qualify incrementally through maintained entry points; update current docs only for implemented behavior. No live benchmark has been run for this design.
