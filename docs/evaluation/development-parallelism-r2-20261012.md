# Parallelism and the actual delivery critical path

Second bounded audit, 2026-10-13 UTC. Runtime/build/GPU work remains paused. This investigator edited only this report; no client, build, configuration change or child agent was launched. The parent owns any implementation and STATUS changes.

## Finding

The sampled team was often parallel at source authoring and serial at delivery. Giving each shader an agent would not remove the shared selection, loader, certificate, packaging, client and parent-integration dependencies. More source candidates can simply make a longer queue for that shared path. The clearest correction is to assign complete, reusable consumer slices and overlap preparation before the final immutable build binding, while reserving globally quiet periods for measurements that actually need them.

The human’s approximately 72 hours is their observed elapsed project experience. These records do not establish 72 hours of active/billed compute, utilization, or a nine-agent continuous run. They do establish preventable queueing and repeated parent-mediated integration. They also show genuinely useful parallel source work and a successful ten-JAR preview delivery.

## Evidence and method

Read global/repository agreements, compact managed STATUS, current agent operations, the first audit and its transcript index. Inspected only the already listed repository session paths below, without database writes or unrelated-project searches. Session C is the parent; D, E and F are its workers. All timestamps are UTC and line references are JSONL records in the inspected local snapshots. Tool assignment arguments are encoded in some records: assignment targets/times are visible, while task substance is established from the receiving worker’s acknowledgment, handback and actual report. No decryption or inference from encoded contents was attempted.

- C: `~/.codex/sessions/2026/10/11/rollout-2026-10-11T01-18-48-01a12a0b-094d-7bb2-b524-d33ac257a28d.jsonl`
- D: `~/.codex/sessions/2026/10/11/rollout-2026-10-11T01-42-48-01a12a20-ff83-7df2-861f-ba4fd1ce642d.jsonl`
- E: `~/.codex/sessions/2026/10/11/rollout-2026-10-11T01-42-58-01a12a21-2849-7741-8199-04450b584631.jsonl`
- F: `~/.codex/sessions/2026/10/11/rollout-2026-10-11T01-51-17-01a12a28-c679-7b91-afbb-9bd7da1bd212.jsonl`

Elapsed intervals below include reasoning, tools, waiting and integration. They are neither CPU times nor independently avoidable delay estimates. Terminal CPU HOLD establishes no active assignment for that worker in the interval, not that the parent was idle.

## Episode 1: usable preview, real parallel work, serial repair tail

October 11, manual all-family coverage. This is the successful first-audit episode, reconstructed here at assignment/handoff granularity.

| Event | Timestamp and raw reference | Critical-path meaning |
| --- | --- | --- |
| Human steering begins; parent removes BSL source diagnostic as preview prerequisite | 21:41:31 C:10243; acknowledgment 21:41:55 C:10253 | Defines deliverable: installable manual preview, with speed/quality unverified. |
| Renderer, selection and adapter work dispatched | 21:45:21 C:10286/E:3861; 21:45:42 C:10291/D:4596; 21:46:00 C:10298/F:2510 | Three useful concurrent partitions across families, rather than one worker per shader. |
| Renderer reports Apply ordering dependency, then source completion | 21:47:46 E:3934; 21:49:42 E:3972 | Availability must be captured before Apply suspends rendering; a cross-worker join. |
| Selection initial handback; parent combined tests | 21:52:47 D:4775; 21:53:55 C:10419 | Source completion was not transaction completion. |
| Selection fixture repair and renderer certificate fixture repair | 22:00:41–22:02:00 D:4779–4832; 22:00:55–22:01:26 E:3976–4008 | Parent test results fan back out to correct owners. |
| Six SSR adapters and authentic assemblies handed back | 22:07:49 F:2600 | Last initial source partition completes. |
| Restoration defect repair | 22:10:37–22:13:46 D:4833–4940 | Implicit defaults had expanded sparse option files; real correctness issue. |
| Stale-recipe and native recovery repair | 22:16:55–22:18:29 D:4941–5000 | Current fingerprint checked before writes; native recovery follows actual selection. |
| Final 123 tests pass; packaging; ten audited JARs ready | 22:25:54 C:10843; 22:31:09 C:10901 | Usable output 49m38s after steering. |

All three first assignments overlapped for approximately 3m43s (21:46:00–21:49:43). The longest initial assignment, adapter work, lasted 21m50s. From its completion to ready JARs was another 23m20s; that tail included necessary integration defects, checks and packaging. It cannot be called pure waste.

Selection worker D had approximately 19m40s of terminal intervals between its successive assigned turns: 7m54s, 8m38s and 3m09s. Those are opportunities to improve handoff granularity, not19m40s of guaranteed savings: the parent needed to produce test results before some repairs could be known. The useful alternative is to give the selection owner the complete Apply/Off/Quality/queued-option transaction and authentic affected tests at the outset, with a parent-reserved combined test slot, rather than treating the initial source patch as a finished slice. E and D were not independent shader teams: they shared one ordering contract. The adapter fingerprints also affected selection migration fixtures. A shader-only partition would conceal these joins.

This episode provides evidence against both extremes: more agents were not useless, and source completion alone did not create a shippable result. Once the preview scope was explicit, the team delivered it without a fresh FPS campaign. Requiring the earlier BSL source diagnostic for this same preview would have been an unnecessary dependency; requiring restoration repairs was useful.

## Episode 2: BSL port, preparation starts after its worker has already stopped

October 12, reuse MakeUp’s main-mod NeoForge 26.2 correction for BSL. This episode is particularly informative because the actual control client was short and successful.

| Stage | Timestamp and raw reference | Observation |
| --- | --- | --- |
| Next-family scout | 05:30:08 assignment C:16106/E:5355; 05:34:21 completion E:5397 | Names separate BSL source certificate plus narrow dispatcher. |
| Source and control consumer dispatched | 05:36:15 C:16188/E:5401; 05:36:45 C:16193/D:7402 | Two independent patch partitions begin. |
| Consumer staged and CPU HOLD | 05:41:45 D:7479/7482 | No live source change, jobs or runtime. |
| Source staged and CPU HOLD | 05:45:28 E:5487/5490 | Parent must combine patches, compile and package. |
| Parent integrated/compiling; independent review dispatched | 05:47:06 C:16308; 05:47:13 C:16309 | Shared parent integration path. |
| Caller/profile/final-operation preparation assigned | 05:56:38 C:16404/D:7483 | Consumer owner restarts 14m53s after its handback. |
| Source/build/package/review accepted | 05:58:29 C:16470 | Runtime remains pending. |
| Next-family scout runs alongside caller preparation | 05:59:29 E:5491–06:04:49 E:5538 | Real useful overlap already exists. |
| Caller sealed, producer/deadline still unstarted | 06:06:58 D:7572 | Seven refusal checks; authentic inputs; final parent operation ready. |
| Parent final operation begins | 06:07:10 C:16647 | Only 12s after handback: this handoff is efficient. |
| All workers CPU HOLD; no competing source/tests | 06:08:14 C:16675; 06:08:21 C:16684 | Control-only run, expressly no FPS claim. |
| Four-stage rendering/recovery success reported | 06:11:02 C:16742 | Native/generated/Quality/Off/Cancel, DEV corrections zero; no speed claim. |
| Next source/control tasks start | 06:12:32 C:16754; 06:12:50 C:16759 | Pipeline moves to Reimagined. |

From source assignment 05:36:15 to reported runtime success 06:11:02: 34m47s. Source and consumer assignment spans overlap 5m01s. Source handback to launch is 21m42s. Caller preparation alone occupies 10m20s after its delayed assignment. Launch operation to success report is 3m52s, including startup, stages, teardown and reporting; it is not a measured GPU duration.

The consumer owner’s 14m53s terminal interval is concrete. Some downstream preparation required the new JAR identity, but world/profile cloning, fixed native options, caller adaptation and refusal cases were already constrained by the source/consumer reports. The [preparation report](bsl-neoforge-262-main-input-preparation-20261011.md) confirms this mix of fixed inputs and late artifact bindings. Assigning that owner consumer **and** preparation as one deliverable could overlap a portion of the 10m20s with source integration/review. Upper bound for this particular overlap opportunity is the 10m20s preparation span, not the full 14m53s hold, and actual recoverable time is unknown. Preserve the late exact JAR/producer validation; never manufacture immutable identities before the build exists.

The global CPU hold during this no-FPS control run also deserves narrowing. Stable deployment inputs and exclusive client ownership are necessary. A ban on all isolated source preparation is stronger than the control claim requires. This sample does not prove contention would be harmless, nor authorize changing a live protocol. A future explicit control-only policy can allow isolated patches/read-only preparation while forbidding active deployment writes, builds and tests. Timed original/candidate/return windows should remain fully quiet. The potential overlap here is only several minutes; the larger opportunity was the late preparation assignment.

## Concrete alternative assignment structure

Use three bounded deliverables plus parent integration for the next comparable port; this is a proposal for an explicitly resumed port, not permission to resume paused work.

1. **Family source owner:** reuse the named working certificate/adapter, own only family-specific source/resources and an isolated patch to the shared dispatcher. Return authentic original/generated checks plus a frozen patch. Review at 10–15minutes; report a missing contract instead of starting a new framework.
2. **Consumer and preparation owner:** own the family control branch, authentic profile/caller adaptation and preparation operation together. Prepare independent fixed inputs immediately; leave current JAR hash and real producer output explicitly pending. Return one complete prepared operation after the parent publishes the built artifact. Same 10–15minute review boundary, inclusive of fixes.
3. **Bounded reviewer:** inspect the concrete changed family/transaction contract and affected authentic negatives, preferably while the parent compiles. Own no production edits or launch. Return defects or acceptance once, with a 3–5minute initial review target; consequential failures justify a specific extension.
4. **Parent/runtime owner:** integrate shared writes once, run the affected combined checks/build, publish immutable artifact identity, bind/freeze/launch via the maintained operation, then accept recovery. Parent should not become a fourth author of every family’s detailed fixtures and caller. During control-only execution, an already isolated next-family scout may proceed under an explicit non-timing policy; timed measurements retain global quiet.

These bounds are planning/review targets grounded in the observed 5–10minute source slices and 10minute caller preparation, not promised throughput. If two workers need the same shared file, use staged patches against one agreed preimage and one parent integration; do not let nine family agents mutate a shared certificate/catalog/selection service. Keep only one or two prepared candidates ahead of the client so obsolete artifact bindings do not accumulate.

Track assignment→complete prepared slice, prepared slice→candidate, candidate→control outcome, and client attempts caused by contract mismatch in the existing milestone. Do not add another dashboard or require timing bookkeeping before a port. A lower source-to-client queue and fewer parent repair turns would demonstrate improvement; worker count would not.

## Cost of this second audit

The first audit already diagnosed delivery ordering and introduced a maintained final operation. Repeating its inventory or writing more general instructions would reproduce the overhead under criticism. This investigation therefore stops at two timestamped critical paths and one bounded scheduling change. It does not recommend a new scheduler, universal launch framework, full 72-hour census, or another performance gate before previews. The parent should implement a concrete improvement only if it removes a demonstrated dependency within the existing audit ceiling; otherwise retain this evidence and stop the audit. Neither this report nor any extra instruction is itself a shader-support or speed result.


## Independent review: maintained offline Iris declaration discovery

The parent restarted this worker for a bounded independent review of `scripts/discover_iris_options.py`, `scripts/java/IrisOptionInventory.java` and `tests/test_iris_option_inventory.py`. Author freeze was acknowledged before execution. Only standalone existing-JDK Java source runs and small CPU-only Python tests were authorized; no Gradle, Minecraft, GPU or current-plan mutation occurred.

Initial four tests passed independently in 4.675 seconds, including authentic BSL/Reimagined generated assemblies and saved parser maps. Code preserves every supplied native observed value, rejects native-key overrides and removed original declarations, and discovers helper include-guard options using Iris’s actual OptionAnnotatedSource/OptionSet APIs. It keeps the broad inventory separate from the proposed native-plus-added map and marks loadedMapVerified=false. Original broad-only declarations remain excluded. Changed defaults are reported, while a preserved native value that remains valid may proceed; this is appropriate for a proposal anchored to observations, not proof of native-default equivalence.

An independent negative uncovered a genuine initial bug. In a temporary copy of the actual generated BSL archive, only `shaders/lib/settings.glsl` was changed from `#define FXAA_EDGE_SENSITIVITY 1 //[0 1 2]` to `#define FXAA_EDGE_SENSITIVITY 0 //[0 2]`. The authentic native map retained value 1. Discovery incorrectly succeeded: MutableOptionValues preserved a supplied string outside the declared domain. The author was notified and confirmed StringOption.getAllowedValues is needed in addition to effective-value comparison. This failure is retained here even if repaired.

Independent positive consumer check: all 388 candidateExpectedOptions exactly equaled the actual successful BSL MAIN receipt’s `on.loadedIrisOptions` at `run-workloads/concept-transfer-r1/bsl-neoforge-main/run-workloads/port-262-runtime-r1/neoforge/shaderlod-dev/neoforge-roundtrip-1791785247497.json`, SHA256 `dc7f8326ab733707f306699d5eccffac6681b87adbe5a9e70939a1cf5f44141f`. The sealed caller’s strict equality remains intact; the new report can supply its proposed generated map after extraction, but is not wired into that caller. No existing preparation was rewritten. This is one authentic saved-map compatibility result, not runtime adoption or a universal loaded-graph claim.


Final review result: accepted within the offline inventory scope after repair. The author added explicit membership in Iris StringOption.getAllowedValues and the authentic narrowed-domain regression. After a new freeze, this reviewer independently ran all five tests: **5 passed, zero skipped, 4.408 seconds**. Independently repeated the original mutation against the real generated BSL archive (the maintained regression uses the original archive as its mutation base): it now refuses the preserved invalid value. The fresh corrected BSL report still exactly matches all 388 values in the same actual MAIN receipt. Historical r1 reports remain retained; corrected output is under `dev/logs/developer-cycle-r2/option-discovery-r2`.

Reviewed final SHA256 values:

- Python wrapper: `4e0921b737f628cb58af20b390c80f38e1f056cb492d86b6ad8f36b3bfd92e76`.
- Java parser: `617d2e52dc47f1b39780bf3685c217b2cd005d7aeb9e1adb661abf9d7dec3a03`.
- Tests: `e6ba24a8c3d9e2ea5d67f4c4742ad21af9870e38a3cc016a40fb03bfa92df6a9`.

Remaining limits: supplied native-map provenance is the caller’s responsibility; broad discovery does not select the loaded include graph; dependency hashes are recorded but runtime compatibility is demonstrated only for the explicitly used local Iris JAR. Changed defaults remain visible rather than automatically rejecting a still-valid observed value. Existing strict actual-loaded equality is still required before runtime admission. No measured developer-time saving, new rendering support or FPS gain follows from these checks.
