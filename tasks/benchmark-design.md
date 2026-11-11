# Benchmark design

## Goal
Build a shared benchmark system with three measurement workflows: an extensible profiler, approximately one-minute development comparisons, and defensible contribution/release qualification. Keep short loading/correctness validation in the check workflow. Produce interactive performance timelines and matching structured evidence while reusing controlled client sessions and worlds.

## Authorized
Design, research, independent Sol review and consolidation of this plan are authorized. Implementation and new game runs await Philip's next instruction. RTX 2080 Ti is the selected qualification target; host, OS and access remain to be established. Occasional MacBook Pro testing is secondary; Asahi and macOS are distinct environments. The user wants one plug-and-play product, broadly reusable mechanisms and preservation of perceptual quality rather than pixel identity. The concrete matrix and time estimates below are an initial pilot design, not measured qualification or permission to exceed resource limits. No individual approximation or optimization is accepted by approving the harness design.

## Done when
Maintained commands implement the agreed workflows; authentic captures, failure/recovery paths, observer effects, controls, analysis and reports are qualified on the allocated machine; and navigation, agent procedure and result closeout are current. Track planned, implemented, exercised and qualified separately. No accepted optimization is required to finish the harness. Automatic retention decisions additionally require the explicit benefit, harm and quality policy for the contribution.

## State
Owner: the active benchmark-design conversation, main in /home/philip/ShaderLOD-next. This is the consolidated proposed implementation contract. No new benchmark code or live experiment has begun. Keep this plan in tasks while unfinished; move working procedures into current documentation as they become real.

**1. Decisions, workflows and terminology**

One runner, controller, evidence format and report pipeline serve these roles:

| Role | Decision | Cadence and permitted conclusion |
| --- | --- | --- |
| Correctness checks | Does it load, activate, render and restore? | Portable checks during edits; focused live checks after relevant rendering changes. No speed claim |
| Development comparison | Does the candidate look promising on the chosen route? | Aim for about one minute total in a ready session, sometimes two; FPS/frame-time timelines, exploratory result only |
| Profiler | Where is time/work spent, and why? | On demand; generic frame capture, optional qualified stage/pack diagnostics; no automatic retention |
| Qualification | Can we defend retaining or releasing this exact configuration? | Frozen contribution or release suite with breadth, repetition, quality, interactions and explicit limits |

Shared harness qualification is a prerequisite reused by dependency identity, not another expensive routine check. A real correctness failure blocks promotion. An inconclusive development speed result can justify deeper work; it is not acceptance. Qualification is broader and more controlled evidence, not simply a longer version of the development route.

Original means original shader contents with ShaderLOD loaded and all optimizations disabled. A mod-absent control measures installed-Off overhead separately. Incumbent means the previously retained configuration. Candidate identifies the exact built/loaded code and enabled set. Release comparisons include original and previous release where distinct; a new contribution compares incumbent plus the change against incumbent, with original/standalone controls below. Never add singleton gains to predict a combination.

Keep original archives, user-selected shader settings, output resolution and AA unchanged; internal approximations may be investigated under the perceptual policy below. Off/Cancel/failed Apply restore the previous state. Iris is mandatory. DH/PBR support needs observed rendering/material work, not installed/enabled flags. Favorable scene properties cannot masquerade as enforceable eligibility. The actual automatic activation envelope determines adverse coverage and fallback obligations.

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
- Same-session blocks reduce turnaround and improve matching, but share caches and history. They are correlated, not independent fresh-process replicates. Qualification includes fresh-session blocks when process effects or reset limitations matter; restart the client while reusing the same prepared world infrastructure. Pilot carryover checks determine what must reset.

**Development cadence and turnaround**

Existing ./check is the portable fast lane, historically about 5–8 seconds warm; ./check --full remains the checkpoint/pre-commit gate. Neither launches a game. Live correctness checks remain separately and explicitly invoked, with allocation and affected scope. A render-only edit should not repeat all supervisor fault injection, all optional integrations or release qualification.

The development comparison targets one minute from a healthy ready-session request to a report, with approximately two minutes as a fallback planning envelope, not an automatically granted extension. Estimate and report actual Apply/reload, readiness, capture, export and restoration costs. Cold build/startup and human review are additional and visible. Choose short contemporary original/candidate windows and a return/control check where reset/drift requires it. A 60-second target does not mean 60 seconds per arm or mandatory 20-second windows. The pilot allocates the total honestly; if reloads prevent useful measurements, expose the real cost rather than omitting controls, weakening readiness or silently exceeding the execution budget.

Development output includes FPS over time (frame count divided by elapsed time in declared bins) and frame-time traces, phase markers, gaps, baseline/candidate overlays and a small numerical summary. Do not average instantaneous reciprocal frame times into throughput. Optional low-rate resource/thermal observations and untimed screenshots help investigation. The short run can expose substantial regressions or promising effects; weak tail support or noise produces an explicit limitation/inconclusive result, never a claim that nothing regressed.

Frame-only profiling normally captures 10–30 seconds, aiming for roughly 20–60 seconds to report without reload. Deeper instrumentation declares its extra cost and scope. Qualification costs are itemized in section 7; the old 20–45 minute single-pack proposal is replaced by the broader milestone suite. Budget exhaustion retains incomplete evidence and stops; it does not grant extra work or weaker coverage.

**Optimization philosophy and perceptual quality**

Philip's product direction is one automatic ShaderLOD installation, initially developed on one pack but intended to benefit many. Prefer mechanisms based on shared rendering work: eliminate redundant work, improve resource use, or bound approximation by visual relevance. A first-pack implementation is a prototype of a mechanism, not proof of broad applicability. Capability/backend differences may justify internal strategies without separate products or manual per-pack tuning.

Current providers bind exact archive/member identities and replacement bytes. Retain this conservative admission initially. A reusable mechanism should own its algorithm and assumptions, with small versioned pack integrations translating resource/program semantics. Do not scatter pack names through core code, duplicate the algorithm per pack, or introduce unsafe universal text rewriting. Structural recognition can be added deliberately with supported-pattern tests and fail-closed unknown inputs. Source recognition proves applicability, not a speedup. Profile semantic mappings do not automatically establish transformation safety.

Before promoting a purported general mechanism, demonstrate it on a second meaningfully different pack, using one shared algorithm; record pack-specific integration size and unresolved assumptions. This is a generalization check, not universal qualification. A useful single-pack prototype can remain experimental; do not retain growing bespoke implementations merely because each has a favorable benchmark. Future module metadata/assessment should record mechanism, assumptions, integration boundary, demonstrated breadth, fallback and quality obligations. Final enforcement belongs in docs/optimizations.md, benchmark specifications and the project skill; propose AGENTS navigation/rules to Philip rather than silently changing his file.

Design quality standard: preserve perceived appearance during representative and adverse play, including motion and transitions, while permitting internal approximations with demonstrated benefit. Preserve original archives, user settings, output resolution and AA, and exact Off/Cancel/failure restoration. Pixel identity is not required. Distance alone does not prove invisibility: screen footprint, contrast, zoom/FOV, reflected effects, motion, disocclusion and thresholds can expose artifacts. Test entry/exit transitions and worst relevant conditions, not just distant settled stills. Record every intentional approximation and targeted review evidence. Clearly visible degradation is outside the default free-speedup promise; any separate quality/performance mode would be a separate product decision. This rule is not approval of arbitrary quality reductions or permission to relax a failed test. Review conditions include output size, viewing scale, color/HDR handling and motion cadence; record the supported viewing conditions. Use masked/randomized A/B labels where practical, unchanged controls and a known defect. Pixel metrics locate differences; human review establishes only the tested perceptual scope, not universal invisibility.

**Old-system failures the new implementation must address**

Source inspection included the preserved docs/BENCHMARK.md and scripts/run_benchmark.py, imported result pages, and the opening investigation in the historical baseline-variance thread. The old harness already had a short check, planning, restoration and reuse improvements; do not claim those concepts are new.

| Historical issue | Required improvement and qualification |
| --- | --- |
| Short check still paid minutes of launch/reload overhead | Reused session and prepared world; itemized phase timings; measure actual focused-check turnaround |
| Identical shaders gave large apparent gains/losses | Unchanged controls establish detectable effect size on 2080 Ti; balanced blocks, drift/time plots and explicit inconclusive outcomes; more frames alone do not fix bias |
| Memory pressure/paging and unexplained stalls outside GameRenderer | Low-rate time-aligned host/process memory and pressure/paging observations, available GPU memory/clocks/temperature with actual sensor identity; complete frame intervals alongside narrower render scopes. Qualify observer cost; unavailable telemetry stays unavailable. Candidate-induced pressure is harm, not a reason to discard its attempt |
| GPU capture covered only 39 of 162 intervals, with pending and imbalanced scopes | Complete frame outcome stream; explicit query coverage/loss and no cost attribution from incomplete convenience samples |
| Motion recorder held route progression for writer capacity | Fixed declared route clock, actual checkpoints and gap records; separate quality capture from timed windows; refuse inadequate capture rather than quietly changing playback |
| Candidate-specific cases accumulated in central runner | Shared lifecycle plus isolated mechanism/pack adapters; second-pack implementation test before settling extension contracts |

Evidence: results/asahi-null-calibration, incomplete-gpu-cost-capture, rtx5080-rendering-review, workflow-repair and profiling-and-benchmark-design-lessons. Historical thread: <t3-thread://v2/de4b951c-7cc7-4d2d-bbb0-1f3e6d7f79f7/b5bf09ef-eb21-4f92-b1d4-b9524ebc85d2>. The early thread report establishes paging/OOM observations and an unavailable GPU-thermal measurement, not a final diagnosis of every source of variance. New qualification must show these failure modes are caught; design alone does not establish that they are fixed.

**3. Maintained entry points**

Extend python3 -m shaderlod_host; do not add another launcher. Commands below are proposed, not implemented. Keep correctness validation separate from timed development/qualification scopes:

```text
python3 -m shaderlod_host validate --spec benchmarks/validation/focused.json --plan
python3 -m shaderlod_host profile --spec benchmarks/profiles/frame.json --plan
python3 -m shaderlod_host compare --spec benchmarks/comparisons/development.json --plan
python3 -m shaderlod_host compare --spec benchmarks/comparisons/contribution.json --plan
python3 -m shaderlod_host compare --spec benchmarks/comparisons/release.json --plan
python3 -m shaderlod_host validate --spec SPEC --allocation HOST_ALLOCATION --session SESSION
python3 -m shaderlod_host profile --spec SPEC --allocation HOST_ALLOCATION --session SESSION
python3 -m shaderlod_host compare --spec SPEC --allocation HOST_ALLOCATION --session SESSION
python3 -m shaderlod_host report --result RECEIPT
python3 -m shaderlod_host verify --result RECEIPT
python3 -m shaderlod_host recover --operation OPERATION --ownership-root ROOT
```

Comparison specs have a required development, contribution or release scope. Qualification may open declared fresh-session blocks through the same owner; the session option never overrides reset requirements. Existing ./verify --runtime remains usable until the integrated focused validation path is exercised and documentation migrated.

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
| Summary | Actual enabled set and selection/fallback reason per case; execution status, validity by metric family, completeness, functional/visual outcomes, performance assessment, per-case uncertainty/regressions, evidence level and missing claims |

Use JSON summaries and compressed JSONL streams initially: inspectable, streamable and no mandatory database. Introduce a columnar format only if measured scale warrants it, without changing semantics. GPU and CPU clocks are distinct; frame IDs associate observations but do not invent synchronized timestamps. Rejected attempts stay in reliability accounting even when no valid timing effect can be estimated.

**5. Profiler design**

Purpose: inspect frame-time variation, find costly paths and test a mechanism on a real workload. Accept original or candidate configurations; comparisons made while deeply instrumented remain diagnostic until observer effects are qualified.

Capture tiers share the same event model:

- Frame mode: complete application-frame intervals, phase markers, lightweight validity/condition observations and requested bounded CPU scopes. This is the base for outcome comparisons. A complete frame stream must not be a trailing ring-buffer sample.
- Pass mode: sampled asynchronous GPU spans and associated CPU submission scopes at actual renderer boundaries, with raw program/pass IDs, geometry/draw/dispatch/target facts where obtainable and requested counters. Record all sampling and missingness. Do not stall for query results or adaptively favor cheap frames. Bounded query pools and buffered off-thread writes need overhead tests; full queues create visible gaps, not hidden loss or route slowdown.
- Targeted mode: a specific adapter adds counters or CPU/JVM/queue diagnostics for the question. It declares cost, capabilities and qualification requirements. Source-altering probes or effect ablations create separate artifacts/runs and cannot substitute for performance/quality evidence from the actual candidate intended to ship.

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

Admission path for a new shader: inventory loaded graph and options -> inspect relevant source -> author a minimal mapping -> test supported/unknown variants -> exercise its real boundaries/coverage -> qualify observer effects -> publish its supported scope. First implement generic collection and one real mapping for the selected pack; prove an unmapped pack remains usable. Exercise a second structurally different pack before treating the adapter boundary as settled. Do not build a universal shader parser or require adapters for all historical packs up front.

**Profiler human and agent output**

A local, offline report.html opens without a server or network. It contains a zoomable/pannable frame-time graph, selectable CPU/GPU tracks, phase/event markers, interval selection, raw point tooltips and a synchronized pass/category table. Selecting a slow interval shows relevant events, program spans and captures where recorded. Show capability, missingness, sampling and validity near the graph, not buried in logs. Provide a compact findings-oriented summary: where variation occurred, what was actually observed, and what cannot be concluded.

The report uses the same versioned analysis output as summary.json. Include distributions, named window summaries and links to exact raw records so agents can ask "which intervals regressed and with what coverage?" without scraping HTML. UI downsampling must preserve extrema and gaps and identify aggregation; analysis uses the complete admitted data, never downsampled display points. Smoothing is optional and labeled, with raw data accessible. Invalid windows remain visible.

A small self-contained renderer with bundled HTML/JS/CSS is the proposed first implementation, using Canvas/SVG where appropriate. No remote scripts or heavyweight dashboard service. Keep report rendering behind one interface if a library later proves necessary. Embed bounded display data for portable viewing, retain exact streams separately, and offer compact CSV/JSON exports and a static figure for results pages. Escape labels and embedded data safely. An unsupported browser or missing raw asset gives a readable summary, not a blank report. Report portability, offline loading and trace/render performance are tested.

Profiler completion criteria: generic frame capture plus one qualified pass/mapping path; wrong-version mapping refuses semantic labels; combined/unmapped/overlapping work is honest; query exhaustion and source drift are visible; human selections and agent summaries agree; overhead is measured; no optimization or acceptance claim follows merely from a diagnostic report.

**6. Correctness and development comparison**

Correctness validation reuses ./check --full and FunctionalProbe/verify_functional. A producer PASS flag is not an independent check. Its specification selects affected functional/visual obligations: actual link/render, activation joined to artifacts/generation, no fallback masquerading as activation, and relevant Off/Cancel/failed-Apply restoration. Changed lifecycle paths require their adverse cases; unaffected harness tests can be reused by dependency identity. DH/PBR cells witness actual rendering/material use. Output separates functional, visual and evidence-validity status; absent, unsupported, blocked and untested remain distinct.

The development comparison follows preflight -> bounded arm-symmetric readiness -> matched short original/candidate route windows -> optional return control -> restore/publish/report. Capture complete lightweight frames; deep CPU/pass tracing belongs in profiler mode. The optional screen may compare original/candidate or incumbent/candidate as specified; never call the incumbent an original baseline. Select minimally sufficient affected scenes, with an early motion sanity check. Timed windows exclude screenshot/video writing unless differential observer qualification supports it. Report promising/regression/inconclusive, never retained. Confirmation of a new candidate cannot reuse an earlier candidate's timing merely because the change seems small.

Harness admission separately exercises unchanged controls, a known slowdown, inactive candidate, malformed/missing/truncated output, bounded visual defect and interrupted recovery. Controls establish only the sensitivities tested. Missing GPU detail need not invalidate complete frame-production evidence, but it removes GPU attribution. Short tails, censored samples and unsupported timing domains cannot become confident claims.

**7. Contribution and release qualification**

Qualification binds a claim to an exact activation and support envelope. A contribution evaluates a new mechanism in its proposed enabled set; a release evaluates the actual packaged combination and relevant prior release. Reuse still-applicable qualification by explicit input/dependency/claim identities, while acquiring current paired controls for drift-sensitive comparisons. A release does not automatically rerun every historical investigation or every possible subset.

**Coverage contract and machine roles**

Before timing, record hardware/OS/driver, Minecraft/loader/Iris/mod tuple, packs/versions/options, dimensions, resource/PBR packs, DH mode, output resolution/AA/cap, automatic eligibility and enabled combinations. Select one pinned runtime tuple initially. Pack names alone do not specify coverage. Map every intended activation/configuration class to an active test, a justified equivalent representative, a tested safe fallback or an explicitly unqualified claim. Weather, settings, dimensions, caps and DH/PBR are selected by the changed mechanism's dependencies, not a Cartesian product. A favorable-case limit that the implementation cannot enforce does not justify enabling everywhere.

| Environment | Contribution | Release and claim boundary |
| --- | --- | --- |
| RTX 2080 Ti, identified host/OS/driver | Primary full performance and quality suite | Full actual-combination suite; conclusions limited to measured configuration |
| One available MacBook Pro environment | Targeted portability when relevant to mechanism | Reduced compatibility/quality/performance suite if that environment is claimed supported; Asahi and macOS are separate |
| Additional GPUs/runtimes | Only for a relevant mechanism/support obligation | No automatic NVIDIA-family, AMD, Intel, Metal or cross-version claim. Missing required cells block that claim or require enforceable fallback |

A skipped secondary machine does not become a passing portability test. The first primary-only result may qualify a bounded contribution without claiming cross-platform release coverage. Use the real supported shipping path: if the pinned loader/runtime cannot operate on a proposed secondary platform, report unsupported rather than silently substituting another runtime.

**Initial pack and workload matrix**

Three pack roles balance breadth and cost: development pack; a structurally different pack with actual active optimization; and a boundary pack exercising another implementation or safe refusal. Two different active packs are needed to demonstrate the proposed general mechanism. Fallback-only coverage is not a speedup result. Exact pack choices follow source/graph inspection; Reimagined is an available starting input, not a completed matrix. Reuse historical worlds/routes with exposure labels; designate confirmation cases before tuning rather than relabeling old cases as unseen.

For each active pack, select four workload roles: opportunity-rich route, ordinary mixed route, low-opportunity/adverse route, and mechanism-specific stress case. A temporal mechanism adds history cuts/disocclusion/convergence; a distance approximation adds transition crossings/high contrast/zoom where supported; deferred work adds useful completion/backlog recovery. One route may exercise several risks, but document the mapping. Off-path poses or a separately selected confirmation route challenge overfitting to scripted traversal. This is a bounded coverage sample, not a population estimate of all gameplay. Mechanisms that defer work must match useful completion and account for backlog outside the timed window. Preparation/reload costs and relevant peak memory are reported separately from recurring gains; a session-benefit claim requires a declared usage horizon or break-even analysis.

**Execution sequence and initial cost model**

| Stage | Concrete work | Initial elapsed estimate |
| --- | --- | --- |
| Preflight and current health | Input/load/activation/restoration checks; bounded unchanged control against an already qualified instrument | 3–6 minutes; not a fresh proof of sensitivity |
| Main contribution matrix | Up to 3 active packs x 4 cases x 3 paired blocks x 2 arms x 30 seconds = 36 minutes capture | 50–80 minutes including ordinary reload/reset overhead |
| Targeted quality | Matched stills and short motion sequences, adverse transitions, separate from timing | 5–12 minutes capture; roughly 10–20 minutes human review separately |
| Sustained behavior | Five-minute segment for each of two arms on a demanding relevant case; memory/backlog/drift/recovery | 12–18 minutes; bounded observation, not lifetime/leak proof |
| Interaction supplement | Original/A/B/A+B on two selected cases, 3 blocks, 30 seconds per cell = 12 minutes capture | 18–30 minutes before deducting observations matching artifact, workload, preparation, instrumentation and statistical block design |
| Secondary environment | Up to 3 active packs x 2 cases x 2 paired blocks x 2 arms x 30 seconds = 12 minutes capture, plus targeted correctness/quality | 25–45 minutes; coarse portability evidence, not a small-effect guarantee |

The main matrix uses A incumbent versus A+B new contribution. Three paired blocks across at least two sessions are the pilot allocation, not three independent process replicates. Both arms occur within each relevant session; do not confound arm, pack or condition with run order. Counterbalance/randomize order with a recorded seed, including pack/workload scheduling across sessions. With three pairs, exact equal AB/BA counts are impossible; distribute the imbalance and record it. Paired windows and session effects remain separate in analysis. A pilot estimates relevant variance and sets sufficient independent-session replication before confirmatory execution. If two sessions cannot support useful inference, add preplanned sessions within budget or remain inconclusive; never bootstrap thousands of frames as independent runs.

Full release compares original, previous retained release and new shipped configuration when distinct. Three arms in the same main matrix require 54 minutes of capture; sustained checks and secondary estimates also grow if all three arms are required. Deduplicate identical reference states explicitly. Add a bounded mod-absent versus installed-Off comparison when qualifying a release or changing always-on code; original-with-mod does not measure installation overhead. Earlier Off-overhead evidence can be reused only when the relevant implementation/runtime remains applicable.

Planning envelopes: approximately 1.5–2.5 hours primary contribution, 2–3 hours primary release, plus secondary coverage and human review. The listed contribution stages sum to about 88–146 minutes before cold setup, retries or extra cells. These are estimates, not hard authorization or promises. Exact plans calculate capture count/duration, per-arm reload/settle cost, startup count, quality IO and teardown using pilot measurements. Slow reloads, extra option/DH/PBR cells, three-arm secondary runs, insufficient precision or added interaction cases can exceed the envelope; report the revised estimate before execution and respect agreed ceilings. The one-minute development loop is never burdened with this suite after every edit. Human review adds roughly 10–20 minutes, making the illustrative combined contribution effort about 98–166 minutes before cold setup, extra coverage and retries; machine time and operator effort are reported separately.

**Interaction policy**

Let A be the incumbent enabled set and B the new optimization. On two risk-selected cases run original (0), A, B and A+B with the same source/settings and balanced complete blocks. Inspect actual executed transformations and quality as well as timing: a fast combination may have silently disabled one member. An original/A/B/A+B comparison identifies aggregate interaction with A; it does not identify which member of A caused it. On a declared mean-frame-time scale, interaction can be summarized as (time_AB - time_A) - (time_B - time_0), with block-level uncertainty. Do not add percentage speedups or assume additivity on another metric.

Run the actual shipped combination broadly. Select extra pair/group tests from shared passes, buffers, precision, cache/history, ordering and opportunity dependencies. When an interaction appears, use disable-one or focused factorial follow-up as exploratory diagnosis, then confirm the revised frozen set. A few pairwise checks cannot rule out higher-order interactions. Fractional designs must state which effects are confounded; no automatic exhaustive subset campaign.

Record the combinations the product can actually select, including automatic fallback when a module is ineligible and user toggles if exposed. Full-set success does not qualify all subsets. Each reachable configuration class needs focused evidence or a justified representative; high-risk unqualified combinations need enforced conflicts/eligibility or an explicitly limited product claim before release. The benchmark does not silently remove user controls or choose the product UI. Source-level conflict declarations and deterministic transformation-order tests complement live tests; they do not prove visual/performance compatibility.

**Decision and statistical rules**

Freeze the artifact, primary effect/formula, harm margins, quality criteria, workload identities/exposure, comparison family, order, missingness/exclusion rules, repetitions/stopping and budget before looking at confirmation outcomes. Preserve the declared cap/VSync operating mode; a capped run may establish delivered throughput without revealing rendering headroom. Any uncapped headroom comparison is a separately declared mode, not a replacement for native-mode outcomes. Primary throughput is frames divided by elapsed time; report the chosen relative effect denominator. Per-case frame durations, supported tails/slow episodes, setup latency, memory and reliability remain visible. A pooled headline cannot hide harm; weights and aggregation are declared and secondary unless explicitly approved.

Choose an analysis appropriate to actual block/session variance. No method label or large frame count repairs two poorly sampled sessions. Individual confidence intervals are not simultaneous certainty across packs/cases/metrics; when acceptance makes a family-wide claim, predeclare a suitable joint/error-control policy or restrict the claim. Lack of significant regression is not evidence of non-inferiority: an accepted no-material-harm claim requires uncertainty bounded against the agreed harm margin. Effect thresholds are product choices informed by pilot sensitivity, not inferred from whichever gain appears.

Do not repeatedly add runs until a preferred result appears. Use a fixed confirmatory sample plan derived from separate calibration, or a specified sequential method with valid stopping. Retuning from confirmation consumes its holdout role; new confirmation is required for the revised claim. Screening observations remain exploratory. Unknown/failed/missing cases remain in coverage and reliability accounting; exclusions need prespecified measurement-invalidity reasons. Candidate-induced memory pressure, non-settling or deferred backlog are harms, not environmental excuses to erase attempts.

Warmup/admission is arm-symmetric and bounded. Preserve transition-inclusive readiness and non-settling separately from conditioned speed. Record available low-rate machine conditions with sensor identity, missingness and observer cost: process/host memory, paging/pressure, GPU memory, temperature, clocks and available power/clock-limiting reasons. On NVIDIA, prefer a versioned NVML adapter where practical over parsing decorative nvidia-smi output; probe capabilities on the actual 2080 Ti/driver and mark unsupported fields. Telemetry and order balancing do not themselves remove thermal/cache/history carryover: qualify order effects and reset/isolation rules, and retain candidate-induced heating/backlog as outcomes. Do not force clock locks, unlimited warmup or thermal restarts solely to obtain favorable windows. A five-minute sustained segment can reveal drift but cannot prove equilibrium or rule out a longer-horizon defect; mechanisms with a relevant longer horizon require targeted extension or a narrower claim.

Verdict separates execution completeness, measurement validity, correctness, perceptual review, speed/harm, support coverage and recommendation. Retain only if the agreed policy is met. Valid slow, inconclusive and blocked outcomes are legitimate. A release report includes exact reproduction inputs, full per-case results, failed attempts, limitations, reusable numerical data and source identity. It is not a claim of universal imperceptibility or every-machine speedup.

**Performance over time: required report views**

| View | X-axis and alignment | What it reveals |
| --- | --- | --- |
| Within one run | Monotonic elapsed seconds; phase boundaries; optional declared route position | Warmup, stutters, gradual drift, transitions and capture gaps |
| Across matched arms/runs | Elapsed time since the same phase or route checkpoint, selectable overlays and small multiples | Baseline/candidate behavior at multiple comparable points rather than a single average |
| Across development history | Chronological run/block date/order with source/protocol IDs; baseline and candidate points plus uncertainty | Whether gains persist across sessions/revisions and whether the baseline itself drifted |

Preserve real elapsed time and raw event identities. Do not stretch traces to align frame counts, join gaps or pointwise subtract unmatched stochastic frames. Matched route/window deltas are valid only under the declared alignment; report sample counts and run-level uncertainty at each checkpoint. For static scenes without a common progression, time overlays are descriptive and block summaries carry inference. Show all repeats individually before an optional aggregate. Percentile/rolling curves use declared window units, sample support and labeled smoothing; repeated time points are not independent significance tests.

The chronological history view is regenerated offline from immutable comparison manifests, not a new mutable database or commit-history diary. Include failed/incomplete attempts as annotated markers. Partition incompatible hardware, driver, pack/settings, workload, cap/resolution, protocol, instrumentation or mapping identities into separate series. Do not connect incomparable points or pool distributions across them. An intentional bridge comparison can establish a labeled relation but cannot retroactively rewrite old evidence. Baseline drift stays visible alongside relative candidate effects.

Report contents: outcome and coverage summary; these three interactive views; case distributions and independent-block effects; regression table; quality evidence; setup/memory/backlog costs where applicable; uncertainty and limitations; exact machine-readable exports. Drill down to individual attempts and profiler evidence without converting instrumented diagnostics into acceptance timing. Rebuilding a report preserves raw data and records analyzer/report version.

Qualification completion criteria: the agreed matrix executes or explicitly accounts for every required missing case; known regression/no-op/missingness controls behave correctly; numerical summaries and charts agree with raw data; eligibility and policy are explicit; retained recommendation can be traced to independent evidence and quality checks. A complete inconclusive comparison is a legitimate outcome.

**8. Repository implementation map**

| Location | Planned responsibility |
| --- | --- |
| shaderlod_host/__main__.py | CLI routing for profile, correctness validate, scoped compare and offline report/verify/recovery; no extra launcher |
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

When implementation lands, README distinguishes checks, development comparison, profiler and qualification with their commands and decisions; docs/development.md routes agents by intent; docs/runtime.md owns allocation, launch and restoration; new docs/benchmarks.md documents the real workflow/specs/metrics and limitations; docs/profiling.md owns adapter admission and attribution rules; docs/results.md retains the established closeout and evidence roles. Update these with working features, not placeholder claims. Propose a short AGENTS.md navigation addition for Philip, preserving his ownership of that file.

Add one project skill, skills/benchmark/SKILL.md, once there is a usable end-to-end procedure. It should route an assigned question to correctness checks, development comparison, profiler or contribution/release qualification; read the current task and relevant FINDINGS; plan before live work; inspect cost/allocation; run the bounded workflow; interpret execution, validity, quality and performance separately; invoke close-results when warranted. Do not make three copies of the same procedure or embed thresholds in both skill and protocol.

Typical agent path: inspect prior findings -> state the optimization mechanism and permitted claim -> plan a profile if the bottleneck is unknown -> implement within the assigned scope -> portable/focused live checks -> short development comparison when useful -> frozen contribution/release qualification when justified -> concise result/FINDINGS/cleanup. A short development comparison can stop an unpromising direction without a full comparison. A profiler is not mandatory when the mechanism and required evidence are already known. No workflow automatically grants more experiments or modifies thresholds to obtain acceptance.

Reports give agents a short JSON summary first and paths to exact details. Durable conclusions go to results/<question>/result.md with useful static figure/compact data and a link to the full interactive report. Full captures and per-frame streams stay in external artifacts. FINDINGS indexes conclusions, not every run. A comparison request does not automatically close an investigation; source preservation, implementation disposition and remaining-project validation still follow close-results.

**10. Implementation and qualification sequence**

| Step | Deliverable | Exit evidence |
| --- | --- | --- |
| 1. Freeze scope and one vertical slice | One allocated host, original pack, workload, candidate/test control and capture boundaries; time/storage/quality policies | Reviewed executable plan with unresolved requirements refused, not defaulted |
| 2. Shared capture and reporting | Typed plans, bounded stream capture, full-frame timeline, structured summaries, offline report | CPU tests for gaps/units/identity/corruption, hand-recomputed summaries, report smoke/accessibility/offline checks; one authentic bounded capture |
| 3. Validate the instrument and lifecycle | No-op/known slowdown/known visual defect, warmup accounting, observer checks, timeout/recovery/restoration | Preserved success and failure receipts; useful sensitivity and collection cost on selected host |
| 4. Profiler extension | Generic renderer spans and one shader semantic mapping plus unmapped fallback | Actual graph/boundary evidence, stale-map refusal, non-additive labeling, query limits and measured overhead |
| 5. Correctness and development loop | Reused functional probe, affected quality cases, one-minute comparison planning | Active/inactive/failed candidate cases distinguish correctly; affected quality review complete; screen does not claim retention |
| 6. Qualification and history | Frozen contribution/release plans with interaction coverage, independent-block analysis, all three timeline views and report catalog | A no-op and controlled regression are interpreted correctly across repeated runs; incompatible series separated; partial attempts accounted |
| 7. Expand agreed coverage and close design task | Additional selected workloads/adapters/platforms and documented operation | Coverage-specific qualification, settled agent workflow, reviewed cost, current docs/skill and preserved evidence |

Steps are small milestones, not permission to build all future capabilities immediately. A tiny test control may exercise the system without being a retained optimization. The first selected pack can be Reimagined because inputs exist, but its Asahi+DH native failure makes that cell blocked; a no-DH pilot must be explicitly scoped and never substituted for DH qualification. Machine choice can change the first authentic path.

Cost forecasting precedes execution. Estimate frames from observed rate and duration, stream bytes from measured bytes/record, spans from selected passes and sampling, visual volume from frame dimensions/cadence/encoding, and profile copies from actual allocated storage. Measure costs in the pilot; do not promise arbitrary seconds or repeat counts. Reports distinguish expected versus worst-case allowed time/storage. Agent ops owns cleanup defaults and retention budgets.

**11. Research, sufficiency and unresolved choices**

Earlier Sol debate established progression equivalence, warmup harm, differential observer effects, frozen decisions, enforceable eligibility and defect-detection sensitivity. The latest requested adversarial review found the architecture sufficient but required bounded contract fixes. Those fixes are incorporated here; the reviewer did not run or qualify this implementation, and did not review a final post-edit revision.

Review: <t3-thread://v2/de4b951c-7cc7-4d2d-bbb0-1f3e6d7f79f7/thread:delegated-task:command%3Amcp%3A191d8c76-d27d-4a89-b1d4-33995646030a%3Adelegate-task%3A5e10b118-a66b-4501-827b-51939dce2c1c>.

| Review objection | Resolution in this contract |
| --- | --- |
| Three pairs in two sessions are not three independent runs | Session/block identities, grouped effects, variance-informed pilot, bounded fresh-session extension or inconclusive; no frame-level pseudoreplication |
| Selected winners and many comparisons can overstate confidence | Frozen claims/metrics/cases, exposure tracking, confirmation separation, explicit joint policy where claimed |
| Fallback pack counted as an active generalization | Separate active/fallback coverage and exact configuration/activation envelope |
| Full combination misses automatically reachable subsets | Record enabled set and selection reason per case; representative subset/conflict obligations |
| Optional stills cannot qualify approximation quality | Required targeted motion/transition review for affected mechanisms with reviewed regions/sequences and unresolved differences recorded |
| Temperature/order balance does not remove carryover | Qualified reset/order checks; available condition telemetry; no erasing candidate-induced sustained harm |
| Capture math and elapsed headline mixed human time | Resolved observation list and exact deduplication; 88–146 minutes machine plus 10–20 minutes human before additional coverage/setup |

Every identified material way to choose the wrong candidate has a check or an explicit claim limitation. Counts and durations are engineering starting points; none of the literature validates this exact shader matrix. Further mechanisms can add focused obligations without requiring an exhaustive suite now.

Sources and boundaries:

- [Khronos timer queries](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_timer_query.txt): asynchronous completion, separate timing domains and tile-rendering placement caveats.
- [Iris program overview](https://shaders.properties/current/reference/programs/overview/): stage/program structure; names do not establish effect cost. Newer docs are not proof of pinned 1.8.12 support.
- [PresentMon](https://github.com/GameTechDev/PresentMon): possible platform-specific frame/display cross-check with documented OpenGL limits; not an adopted dependency or available Asahi capability.
- [Kalibera and Jones](https://kar.kent.ac.uk/33611/): repetition at relevant variance levels and effect uncertainty, not universal repetition counts.
- [VM warmup study](https://arxiv.org/abs/1602.00602): fixed warmup does not guarantee stable VM behavior; no Minecraft-specific duration follows.
- [NVIDIA FLIP](https://github.com/NVlabs/flip): image differences assist localized inspection, not universal temporal equivalence.
- [JDK 21 JFR](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshoot-performance-issues-using-jfr.html): diagnostics can perturb execution; observer cost is workload-specific.
- [NIST randomized blocks](https://www.itl.nist.gov/div898/handbook/pri/section3/pri332.htm): block known nuisance conditions and randomize remaining order; does not make correlated windows independent.
- [NIST confounding](https://www.itl.nist.gov/div898/handbook/pri/section3/pri3343.htm): reduced factorial designs can mix main and interaction effects. Targeted four-cell checks do not qualify arbitrary subsets.
- [NIST simultaneous comparisons](https://www.itl.nist.gov/div898/handbook/prc/section4/prc473.htm): individual interval coverage differs from simultaneous coverage. This supports declaring the claim family, not blindly choosing one correction for every analysis.
- [Dwork et al., adaptive data reuse](https://arxiv.org/abs/1506.02629): repeated adaptive evaluation can overfit its holdout. Exposure/confirmation separation is the proportionate application here; no differential-privacy evaluation framework is required.
- [NVIDIA telemetry documentation](https://docs.nvidia.com/deploy/nvidia-smi/index.html): device-dependent available fields, unsupported values and API stability; temperatures/limit reasons are diagnostics, not causal attribution.
- [SPEC graphics run rules](https://spec.org/gwpg/gpc.static/current.html): general-purpose optimization and image-quality obligations motivate avoiding benchmark-specific shortcuts. ShaderLOD does not adopt or claim SPEC compliance. Mechanisms must not recognize benchmark route IDs or canned output to produce gains unavailable in ordinary play.
- Local results: asahi-null-calibration, incomplete-gpu-cost-capture, rtx5080-rendering-review, early-rendering-contract-lessons, combination-effects and profiling-and-benchmark-design-lessons preserve relevant historical failures.

Not required by default: all-pass/full-counter tracing on every comparison, a universal shader parser, a dashboard server, a new telemetry database, all-settings/all-machine Cartesian coverage, permanent full-session video, external power equipment or input-to-photon apparatus. Add these only for a specific decision that needs them. Missing GPU diagnostics can coexist with valid frame-production evidence; missing presentation observations prevent claims about displayed smoothness. Known controls establish specific sensitivities, not universal bug detection.

Implementation can begin after Philip authorizes it; no further architecture decision is presently required. Before the first live pilot, identify the 2080 Ti host/OS/access, exact pack/world inputs and bounded time/storage allocation. Before a retention/release verdict, settle meaningful benefit and tolerated harm, concrete perceptual review scope, supported configuration/combination envelope and required secondary coverage. These do not block pure contracts/controller/report implementation. Agent ops still owns broad storage/cleanup policy. A preparation-amortization claim needs a usage horizon. Do not reopen the agreed one-product, reuse, one-minute development or perceptual-quality direction as unanswered questions.

## Next
The requested research, Sol challenge and plan consolidation are complete. Await Philip's implementation instruction. Begin with typed workflow/session contracts, one controller/frame-capture/offline-report path and focused failure tests; acquire the selected host/input/budget details before live execution. Qualify reset, overhead, sensitivity and actual turnaround before expanding shader diagnostics and contribution/release coverage. Acceptance margins are required before retention recommendations, not before building the foundation.
