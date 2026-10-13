# Development throughput: bounded transcript audit

Audit: 2026-10-13 UTC. Read-only investigation while ShaderLOD runtime work is paused. No clients, builds, benchmarks, configuration changes, or worker launches were performed. This report is the only repository file edited by this investigator.

## Finding

The strongest demonstrated bottleneck is **repeated integration of caller/observer assumptions that do not match the real consumer**, compounded by prioritizing additional proof machinery before delivering broadly usable candidates. It is not defensible to attribute all elapsed time to model compute, all checks to waste, or all failures to one cause.

A useful counterexample is in the same main session: after the human explicitly prioritized manual all-family coverage at 21:41 UTC on October 11, the team delivered ten candidate JARs at 22:31, approximately 50 minutes later. Workers made complementary selection, renderer, and shader-adapter changes; the parent integrated and tested them. That is real candidate-delivery progress, not a demonstrated all-family speedup. The preceding BSL source-identity investigation occupied a 71-minute observed interval (20:28–21:39) without establishing optimizer support. These intervals have different scopes and cannot be treated as a controlled productivity experiment, but they identify a concrete prioritization opportunity.

## Sample and method

Discovered SQLite schemas first; queried `state_5.sqlite` read-only with `cwd` restricted to this repository. Read selected JSONL rollouts directly. No database or session was modified, no unrelated project conversation was examined, and no raw transcript was copied into Git. References below are local evidence identifiers, not public artifacts.

Three main sessions were inventoried and selected visible message/tool events examined. Three child sessions were additionally examined for the successful manual-coverage interval and deadline handoff. This is a purposive sample of successes and failures, not a random sample or a census of project time.

| Alias | Local rollout path | Event count | Top-level tool-call events | Compactions |
|---|---|---:|---:|---:|
| A | `~/.codex/sessions/2026/10/09/rollout-2026-10-09T22-00-14-01a1242e-e157-7b43-9ad4-6041703aa277.jsonl` | 10,194 | 1,027 | 4 |
| B | `~/.codex/sessions/2026/10/10/rollout-2026-10-10T18-02-14-01a1287b-57d6-7f62-b8e9-e9816e854d8d.jsonl` | 5,381 | 522 | 2 |
| C | `~/.codex/sessions/2026/10/11/rollout-2026-10-11T01-18-48-01a12a0b-094d-7bb2-b524-d33ac257a28d.jsonl` | 17,100 | 1,732 | 7 |

Counts came from JSONL record types: all rows for event count; `response_item` payload `function_call`/`custom_tool_call` for calls; `compacted` for compactions. Total: 32,675 events, 3,281 top-level calls, 13 compactions. Orchestrator calls can contain several shell operations, and event records duplicate some displayed content; these are not independent experiment counts. Session A's inventory ends October 11 00:32:57; B's October 11 19:44:02; C's October 13 01:39:23. Late metadata and user events mean session extent is not execution duration.

Child rollouts:

- D: `~/.codex/sessions/2026/10/11/rollout-2026-10-11T01-42-48-01a12a20-ff83-7df2-861f-ba4fd1ce642d.jsonl` (selection and later preparation).
- E: `~/.codex/sessions/2026/10/11/rollout-2026-10-11T01-42-58-01a12a21-2849-7741-8199-04450b584631.jsonl` (renderer coverage).
- F: `~/.codex/sessions/2026/10/11/rollout-2026-10-11T01-51-17-01a12a28-c679-7b91-afbb-9bd7da1bd212.jsonl` (shader-work adapters/review).

All times below are UTC. Line numbers identify the inspected snapshot. Findings rely on visible messages, tool results, and project records, not private reasoning. Some tool arguments are encoded in the local log, so call names alone cannot establish their full intent. No CPU/GPU utilization trace or complete per-operation cost accounting was available; wall spans include reasoning, tools, reviews, waiting and potentially inactivity.

## Representative episodes

### 1. A failed experiment incorrectly ended the broader work

A:10190, October 10 18:40:20: the assistant explicitly acknowledged, “I stopped because those two experiments failed their gates,” followed by the admission that it had wrongly treated this as the whole project's stopping point. The same response distinguished a Photon prototype with no performance benefit from unproved Reimagined reuse and reported no retained new shader optimization.

This confirms an outcome-management error in that episode. It does not establish that the entire earlier work was wasted: a negative performance result can be useful evidence. The error was failure to select the next authorized project action after closing the experiments.

### 2. Real-consumer defects surfaced serially during native NeoForge bring-up

C records these separate observed boundaries on October 11:

| Time / line | Observed failure or correction | Implication |
|---|---|---|
| 09:50:16 / 1232 | First actual shadow draw failed: 16 vertex slots versus one binding descriptor | Actual driver contract needed correction; build success was insufficient. |
| 10:38:30 / 1618 | Helper classes in the Mixin-reserved package could not load | Plain compiled-shape checks missed loader semantics. |
| 10:52:49 / 1769 | Wrapper ran, but correction was not observed; six offline hashes matched | Offline identity did not prove the live admission gate. |
| 10:56:46 / 1843 | `Class.getResourceAsStream` looked in the helper's named module; existing verifier used the classloader | Existing working consumer contract had not been reused. |
| 11:08:08 / 1969 | Shadow and terrain gates passed; singular indexed feature draw still failed | Plural `executeDraws` coverage did not cover singular `executeDraw`. |

The first listed failure to the last spans 77m52s. This is an observed bring-up interval, not quantified wasted compute or a count of identical retries. The errors concern different layers, but collectively show why inspecting the complete entry-point/loader/resource path before another expensive client is valuable. `docs/USER_LESSONS.md` also preserves bufferless and late-binding draw-shape omissions; those additional cases were not independently counted here.

### 3. Caller and observer mismatches repeatedly interrupted otherwise useful runs

The following eight **distinct reported contract mismatches** in C were selected for inspection. They are not eight repetitions of one cause:

| Time / line, October 11 | Boundary mismatch | Existing evidence salvage |
|---|---|---|
| 12:50:36 / 3481 | UI readiness logged at tick 600, after the required capture window | Added early readiness observation; did not relax capture window. |
| 13:23:13 / 3880 | Caller waited at wrong capture receipt path | Saved real capture was validated; client was not repeated for this path error. |
| 16:36:05 / 6217 | Private Core-On probe inherited native-only mixin/Core-Off prelaunch checks | Refused before client creation; two refusals were reported, not two rendered trials. |
| 17:43:16–17:46:13 / 7352, 7389 | Iris emitted equivalent `f`-suffixed float literals; observer expected authored spelling | Narrow matcher correction; failed run preserved. |
| 19:01:15 / 8104 | Photon sidecar omitted the admitted portable native map | Corrected actual input join, preserving declared settings. |
| 23:04:10 / 11322 | Post-run checker still expected measurement=false after counters were enabled | Valid in-world receipts were reviewed separately; no needless client repeat. |
| 23:19:51–23:22:28 / 11733, 11795 | BSL expected map omitted two helper include guards enumerated by Iris | Exact added declarations identified; all 384 native settings unchanged. |
| 23:46:51–23:48:58 / 12263, 12293 | Incorrect serialized inactivity value caused AFK throttling | Fresh input correction across the seven intended profiles. |

B provides an earlier analogous sequence: October 11 01:30:39, line 1079, Java compilation omitted new mixin resources and the completed-frame counter stayed zero. At 01:35:53, line 1151, after resources were repaired, a real pinned GameTest resource pack missing from the static certificate blocked the On stage. These are two different producer/consumer mismatches in 5m14s, not evidence that the candidate itself was slow.

The reusable lesson is narrower than “test more”: test each changed adapter against the actual saved launcher, produced resources, runtime parser or real receipt before arming its dependent client step. More tests that reproduce the same synthetic assumption would not help.

### 4. A successful shift to direct candidate delivery

C:10247 records human steering at October 11 21:41:31: port general existing optimizations across all shaders first, with the human inspecting FPS and visual quality. C:10253 acknowledges this 24 seconds later. The previous compiler-source diagnostic was explicitly removed as a prerequisite for this coverage task.

Independent worker evidence supports execution, not just the parent's promise:

- E:3934–3972, 21:47:46–21:49:42: ten runtimes exposed existing native-route availability; an Apply-ordering issue was identified before integration.
- D:4775, 21:52:47: selection routing was implemented; source/tests were handed back with runtime still unverified. D:4937 and 4997 document actual transaction/restoration repairs at 22:13:46 and 22:18:29.
- F:2600, 22:07:49: six SSR adapters consumed existing near/far/transition controls; nine-family inventory and fingerprints were delivered, explicitly without speed/quality claims.
- C:10843, 22:25:54: 123 focused tests passed and packaging began.
- C:10901, 22:31:09: ten audited JARs were ready. C:10911, 22:32:08, delivered artifact and instructions.

Elapsed from steering to “ready”: 49m38s; to final delivery: 50m37s. This does not prove nine runtime passes or useful speedup. The delivered candidates were genuinely usable for the requested manual inspection. C:11438, 23:10:10, later recorded actual Unbound batching: 225 removed commands out of 246,105 (~0.09%), appropriately labeled execution evidence rather than a meaningful FPS gain.

The record does **not** support saying this steering was simply ignored. It was acknowledged and acted upon quickly. The more supportable criticism is that the previous prioritization required human intervention, and the longer project repeatedly returned to costly verification plumbing.

### 5. Expiring handoff remained a recurring mechanical problem

C:15929, October 12 05:12:55, is a completed preparation handoff with deadline 05:16:04.523 and parent review/launch still pending. C:15959 at 05:15:33 contains the actual failed preflight assertion `130 < remaining <= 330`. Only approximately 31 seconds remained, so the operation correctly refused before launch. C:15966 uses `followup_task` at 05:16:16; D:7379 at 05:16:26 acknowledges one producer refresh. C:15993 reports the fresh launch at 05:17:42.

The initial handoff to refused preflight took 2m38s; first handoff to launched report took 4m47s. This directly confirms one avoidable expiry/refusal and its repair, not that all 4m47s was wasted. `docs/USER_LESSONS.md` already describes two earlier plans expiring under the same deadline-before-review design. Thus the lesson existed before the inspected recurrence; another prose warning alone would be insufficient.

A later handoff, C:16644 at 06:06:59, explicitly leaves producer/deadline pending and gives the parent the final freeze/preflight/launch operation. The paused Reimagined handoff C:17046, October 12 21:13:44, follows that improved arrangement. Preserve it rather than invent another deadline framework.

## Prompt, coordination and outcome claims

- **Prompt burden is measurable; causation is not.** C contains 22 repeated native-goal continuation messages totaling 171,251 characters (approximately 7,784 each), plus 188 developer-message records totaling 322,908 characters. Some are necessary runtime/hook updates; counts are persisted text, not simultaneous context, unique rules, billed tokens or attributable slowdown. Seven compactions in C support a context-management burden hypothesis, but do not prove prompt overload caused any specific defect. Keep active outcome/authorization/next step compact and avoid adding another narrative ledger.
- **Wrong worker reuse is not demonstrated by these October 10–12 samples.** B contains 48 `followup_task`, 80 `send_message`; C contains 191 and 195 respectively. Messages to running workers are valid. A historical confirmed mistake exists in `docs/USER_LESSONS.md` (“Restarting completed native workers,” including October 7), but counts alone cannot show October 11 recurrence. The explicit expiry repair above used the correct restart operation.
- **Local tests were usually not misrepresented as measured outcomes.** The inspected manual delivery and subsequent updates explicitly separate tests/builds, rendering and FPS. The problem is allocation and delivery ordering, not a demonstrated broad false-PASS habit. Do not remove those evidence distinctions.
- **“Unnecessary gates” needs specificity.** Exact resource ownership, native restoration and honest speed claims remain useful. The BSL source-identity prerequisite for manual coverage was removable because the human accepted a preview with limited evidence. It does not follow that source identity is unnecessary for a later renderer-certification claim.
- **Actual speed remained uncertain.** C's October 12 01:18 response explicitly acknowledges baseline drift and no comparable fresh gains; the current paused checkpoint likewise claims compatibility/recovery progress, not new FPS gains. This audit does not independently validate performance measurements.

## Improvements ranked for this project

1. **Use two explicit completion levels in the existing frontier:** delivered manual candidate versus certified runtime/performance result. For a requested preview, require installable output, exact scoped adapter, controls/recovery and known blockers; do not make unrelated provenance perfection a delivery prerequisite. Keep FPS/quality unverified until actually checked. The 50-minute delivery episode is the acceptance model.
2. **Make existing caller boundaries consume real producer outputs.** For any changed launcher/parser/observer, exercise one saved authentic argv, resource set, option map and receipt through the actual entry point before the dependent client. Reuse these fixtures for the small named defects; do not create a new general observer framework. Acceptance: the wrong path/default/extra-option cases above fail cheaply while the authentic record passes.
3. **Finish review before starting the finite clock.** Retain the already adopted parent-owned producer → freeze → preflight → launch operation. Worker handoffs contain prepared inputs and no live deadline; deadline creation occurs in that final operation. Acceptance: an arbitrarily delayed human/model handoff cannot expire a run that has not been armed; source/plan changes still invalidate their required checks.
4. **At an auxiliary review trigger, decide against the user deliverable.** Count preparation, repairs and reviews in one attempt. Require a concrete outcome unlocked by the next repair; if it does not block the authorized preview, ship the bounded preview and retain the diagnostic as separate unfinished evidence. A failed candidate triggers the next authorized hypothesis, not project completion.
5. **Reuse compact typed state instead of duplicating instructions.** Preserve authorization and current steering in STATUS; treat the stale immutable native-goal narrative as historical scope where it conflicts with newer steering. Add only missing enforceable operations, not longer repetitive reminders. Any claimed prompt reduction should be measured by actual injected context, not repository word count alone.
6. **Keep worker restart and acceptance evidence precise.** Use `followup_task` for a terminal worker and one acknowledgment check, as the successful sampled correction did. Independently validate consequential claims, but let a reviewer identify concrete defects rather than create an open-ended launch gate. No evidence here justifies an extra universal review layer.

These recommendations preserve real quality/restoration gates and the full 90-cell scope. They change which evidence blocks which deliverable, reduce avoidable consumer mismatches and remove model handoff latency from finite deadlines.

## Independent review of audit improvements

Subsequent bounded review, October 13: parent-owned prompt changes and the author's maintained NeoForge operation were inspected. No implementation files were edited by this reviewer. Tests were run only after the author explicitly froze source/tests and acknowledged readiness. Shader runtime remained paused; the only real child processes exercised were short Python test programs, not Gradle, Minecraft or GPU jobs.

Reviewed operation `scripts/run_neoforge_control.py` SHA-256 `50fb6c440d30c7f7e364f237dbd54ccd1f4e2784ffd289c1da8ae8b4f9217f5c` and tests `tests/test_neoforge_control_operation.py` SHA-256 `81f35647be7e3a8589874fe3f874015ffc1b080ab17d4669fa6df5fbae5ee6f3`. Independently ran `python3 -m unittest discover -s tests -p test_neoforge_control_operation.py -v`: **15 tests passed in 1.464 seconds**, none skipped. Scoped whitespace/diff check passed.

Accepted within this offline scope:

- Default inspection uses the actual pending Reimagined plan, forbids `Popen`, performs no writes/stamp, and labels runtime admission false. The current sealed operation cannot execute through the new maintained entry point because its preparation did not freeze that entry point.
- Exact BSL/Reimagined selectors, pinned caller hashes and explicit command/property grammar preserve the original family acceptance logic. This moves repeated producer/deadline/freeze/preflight choreography into one maintained entry point; it does not silently replace the family caller or rewrite paused inputs.
- Final stamping and real producer are in one operation, after preparation. Producer wait and caller wait are finite. Generated classpaths, both ModDev folder providers, profile and unchanged source/resource sets are checked before the family caller is admitted.
- Failure cleanup uses recorded exact ownership and existing stop/launcher retirement operations. Changed identity refuses signaling. A valid prior caller release receipt is preserved only after fresh absence checks.
- During initial review, producer identity discovery and receipt writing occurred outside the cleanup handler after `Popen`. I reported the risk of a live child escaping that failure path. The author repaired it before freezing: the retained PID is published before census; metadata failures are handled; unidentified children are not signaled; continued ownership is explicit if the bounded wait cannot establish exit. Dedicated missing-identity and write-failure tests pass.

Limitations remain explicit: no fresh real Gradle producer or full family caller/client was launched during this audit, so this is not runtime adoption evidence. Producer cleanup controls the recorded wrapper PID, not an arbitrary descendant group; failure does not certify that all descendants are absent. Missing identity can leave an explicitly retained live process requiring exact investigation. These are safer refusal boundaries, not proof of automatic recovery from every OS failure. The maintained operation remains deliberately limited to its two pinned family callers.

Prompt review covered `AGENTS.md`, `docs/AGENT_OPERATIONS.md`, `.codex/agents/feature_owner.toml` and `.codex/agents/shader_owner.toml`. No blocking contradiction or new approval requirement was found. The changes specify current deliverable, reuse, actual selected consumer and claim-specific acceptance; the compact assignment is explicitly not a new document or launch gate. They preserve pause/allocation, native quality/restoration and distinct speed/compatibility claims while removing the implication that every preview needs every historical proof protocol. The instruction to exercise an actual public consumer should continue to be interpreted at the assigned claim's level: a source/control preview does not automatically authorize or require a new FPS campaign.
