# ShaderLOD development throughput review

The [second investigation](#second-investigation-parallelism-and-useful-work)
adds reconstructed critical paths, concrete zero-work cases, a direct End-cloud
port, reusable Iris option preparation and a measured Java allocation improvement.

October 12, 2026, Pacific time. Human-authorized investigation and improvements,
with a 60-minute elapsed budget and four GPT-6 Astra workers. The existing shader
runtime goal remains paused; this maintenance does not spend or reset its remaining
1,200-second allocation. Findings describe the sampled record, not an estimate of
total agent hours.

## Conclusion

The largest demonstrated opportunities are better delivery sequencing and reuse of
actual launcher/consumer contracts. The project already shares its shader policy,
selection/recovery service and adapter resources across ten builds. Another general
shader framework would not address the recurring problems we found.

Agents produced substantial real work, including old MakeUp/Sildur renderer gains
and newer rendering/control ports. But expensive validation and preparation often
advanced before a usable candidate, while some optimization ports targeted inactive
or very small workloads. Repeated hand-built caller assumptions then caused failures
that were unrelated to shader performance. The correction is to port a named working
mechanism through its actual selected consumer, automate repetitive authoring and
launch steps, and make the evidence required depend on the requested deliverable.

## What supports that conclusion

| Finding | Evidence and limitation | Practical consequence |
| --- | --- | --- |
| Delivery ordering materially mattered | After explicit all-family manual-preview steering, ten JARs were ready in 49m38s. The preceding BSL source investigation occupied a 71-minute observed interval without optimizer acceptance. Different scopes mean this is not a controlled productivity ratio. | Deliver the requested preview with honest limitations; do not make an unrelated certification project its prerequisite. |
| Consumer assumptions repeatedly failed | Eight distinct caller/observer mismatches sampled: receipt path, loaded options, float serialization, stale assertion, readiness timing and other concrete joins. Several valid captures were salvaged without rerunning the client. | Replay actual saved producer/receipt shapes through changed code before its dependent launch. More synthetic tests of the wrong assumption do not help. |
| Deadline orchestration was a mechanical recurrence | One independently checked handoff left only 31 seconds at preflight, below the existing 130-second minimum. Earlier instances were already in USER_LESSONS. Later private operations fixed sequencing. | Maintain that final operation once; stamp only after preparation/review, then produce/freeze/preflight/launch without a model handoff. |
| Source coverage is not necessarily active optimization | BSL Aurora is off in the native reference. Astra AO12 remains12 under Balanced and becomes10 under Performance, but its original Mac rendering is blocked. Eligible Performance can select renderer batching instead of the generated shader graph. | State preset, selected route and active native work in each port assignment. Inactive-effect work is coverage, not evidence of speed. |
| Earlier gains do not transfer automatically | Six hash-verified reports reproduce old NeoForge1.21.1 MakeUp +42.07% and Sildur +22.12% throughput. Those are renderer coalescing results. Six current Fabric26.1.2 brackets show no demonstrated gain, with native-return drift of 20–32%. | Reuse implementations and evidence for unchanged dependencies; measure benefit on the actual new tuple before quoting a percentage. |
| The committed history is incomplete | 101 commits were inspected, but the initial worktree also had 54 modified tracked and 2,396 untracked files; newer ports were outside HEAD. | Review working artifacts and transcripts alongside Git. Commit counts, changed lines and file dates cannot establish agent time or productivity. |

The transcript sample covered six sessions, including inventories of 32,675 records,
3,281 top-level tool calls and 13 compactions in three main sessions. It was selected
to examine successes and failures, not randomly sampled. We did **not** substantiate
systematic wrong worker restarting or a broad habit of labeling unit tests as FPS
gains in those sessions. Repeated instructions create a plausible context burden,
but their measured text volume does not prove they caused specific defects.

Detailed evidence and independent cross-checks:
[Git and outcome history](evaluation/development-throughput-history-20261012.md),
[transcript episodes](evaluation/development-throughput-transcripts-20261012.md),
[architecture](evaluation/development-throughput-architecture-20261012.md),
[harness](evaluation/development-throughput-harness-20261012.md).

## Implemented improvements

**Adapter authoring now has one maintained propagation operation.**
[The existing builder](../scripts/build_supported_pack_adapters.py) assembles the
explicit original with unchanged strict pins, previews the exact packaged recipe,
helper and catalog updates, and writes them only with `--apply`. It preserves the
previous current identity as legacy and rejects incompatible shared-helper consumers.
All ten targets already consume these shared resource roots.

```sh
python3 scripts/build_supported_pack_adapters.py \
  --adapter bsl-shaders \
  --base dev/upstream/other-pack-research/BSL_v10.1.8.zip \
  --sync-resources
```

Add `--apply` after authoring a legitimate pinned change to perform the previewed
updates. Ordinary `--output <new.zip>` assembly remains available. Photon has a
different manifest and stays with its existing builder. A coordinated shared-helper
policy change still needs coordinated integration; this single-family command will
refuse to break other packaged recipes. Writes are atomic per file, not across the
whole operation; ordinary file ownership remains required.

**Final control-launch choreography is maintained rather than copied.**
[The new operation](../scripts/run_neoforge_control.py) extracts the common final
producer sequence for the two actual BSL/Reimagined NeoForge26.2 MAIN callers.
It preserves those exact family acceptance callers and existing supervision.

```sh
python3 scripts/run_neoforge_control.py --session \
  /Users/pippo/github/shaderlod/dev/logs/concept-transfer-r1/reimagined-neoforge-main/runtime-r1
```

This command is read-only and was exercised on the actual pending session. It
does not arm a deadline. A future fresh preparation must freeze the new operation;
then explicit `--execute` performs the uninterrupted final sequence. The sealed
Reimagined operation deliberately remains unchanged and cannot be silently migrated.
Actual Gradle/driver adoption of the new entry is still pending. Cleanup failures
retain exact ownership rather than claiming the lane is free.

**Port assignments now name the useful result and reuse boundary.**
[AGENT_OPERATIONS](AGENT_OPERATIONS.md), [AGENTS](../AGENTS.md), and the feature/shader
role instructions now direct agents to name the working mechanism, missing joins,
selected consumer, relevant acceptance and total attempt budget. They explicitly
distinguish preview/control delivery from a performance claim, preserve the full
90-cell objective and require evidence reuse for unchanged dependencies. This is a
change in emphasis and workflow, not proof of a measured reduction in model context
or agent hours. [Official Astra guidance](https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra)
also recommends contextual instructions, proportional testing and explicit completion
boundaries; the project conclusions above come from local evidence.

## Verification and limits

- Adapter tests: 14 passed, including authentic BSL changed-output propagation in a
  disposable root, idempotence, prior identity retention, conflict refusal and
  ordinary ZIP behavior. Two obsolete broad assertions were replaced with
  effect-specific checks; shader guards were preserved.
- All eight non-Photon originals assembled in current-repository dry runs with
  zero pending resource/catalog changes. Current packaged resources were untouched.
- Control operation tests: 15 passed, including actual saved-plan inspection with
  process creation forbidden, malformed input refusals, exact cleanup, and two real
  harmless Python subprocesses. Independent review found a producer metadata error
  path outside cleanup; it was corrected and verified before acceptance.
- Both code changes received independent Astra review. Prompt TOML parses and its
  instructions were independently checked against the sampled human steering.
- All 735 paused Reimagined preparation pins still matched; its deadline remained
  unstarted and no producer/client ownership receipt existed during verification.

These are authoring and process-control results. No Minecraft client, Gradle build,
GPU trial, new FPS result, motion-quality result or DH/PBR result was produced.
Actual future port turnaround remains unmeasured.

## Next decision and how to judge improvement

The existing next shader action remains Reimagined NeoForge26.2 main-JAR rendering
and Quality/Off/Cancel recovery **after explicit resume**, using its sealed operation.
The new maintained control entry is for fresh preparations; it does not add a
migration gate to that already prepared work.

For the next three authorized ports, record in the existing goal milestone:
time from assignment to the requested candidate/control result, number and cause
of client launches, preparation/review time where actually observed, and whether
existing implementation/evidence was reused. Keep compatibility completion,
executed work and measured speed separate. Fewer launch attempts caused by stale
inputs, no expiry across handoffs, and fewer manual mirror/catalog edits are the
concrete improvement criteria. Do not promise a speedup factor without those data.

## Second investigation: parallelism and useful work

The follow-up asked why one worker per shader had not produced proportionate
progress over the user's roughly 72-hour experience. A fresh four-Astra team
investigated the actual dependency chain and optimization yield. Together with
the parent, it delivered two reproducible candidates and an immediately usable
developer tool. This is a second authorized
offline operation; Minecraft/GPU execution and the existing shader allocation
remain paused. The sampled records explain mechanisms of delay, not a complete
accounting of those 72 hours.

### Why source parallelism did not become delivery parallelism

In the BSL NeoForge26.2 episode, source work finished at 05:45:29 UTC. Caller
preparation was assigned at 05:56:38 and sealed at 06:06:58; launch followed at
06:07:10. Its owner had been terminal between assignments for 14m53s. The whole
source-to-runtime episode took about 35 minutes, with 21m42s between source
handback and launch. The parent was integrating/reviewing during that interval;
it is not all idle or recoverable time. But assigning preparation only after those
steps put an otherwise partly independent 10m20s task on the critical path.

In the ten-JAR preview episode, real parallel source work still left a 23m20s
integration/repair/package tail after the last initial source handback. Repeated
short repair turns and parent-mediated handoffs added dependency latency. The
shared loader, selection, producer and single client did not become independent
merely because shader files were assigned to different workers.

The operation instructions now assign consumer changes **and** authentic caller
preparation together, with late binding of the final artifact. They distinguish
exclusive frozen-input ownership from global quiet timing. Fresh untimed control
operations can permit isolated next-family preparation; existing sealed quiet
contracts remain intact. These changes target the observed queue rather than
increasing the number of unfinished shader branches.
[Timestamped critical paths](evaluation/development-parallelism-r2-20261012.md).

### Several apparent ports had no useful selected work

| Consumer | What the second audit established | Meaning |
| --- | --- | --- |
| MakeUp's older reflection budget | Native loop has ten total iterations, starts in coarse search, and can have at most nine refinements. Balanced's limit of nine cannot end the loop early. | This particular Balanced port removes no iterations; the later cloud budget is a different mechanism. |
| Sildur volume | Recorded native option `Volumetric_Lighting=false`; independently, `ceil(6 × .875)=6`. | The actual reference has no such work, and Balanced would retain all six samples anyway. |
| BSL Aurora | Native reference has Aurora disabled. | Source/control coverage does not improve that reference scene's speed. |
| BSL AO / shafts proposed count copies | Four AO groups and seven shaft positions both round back to their native counts under Balanced. Shafts also need proper domain resampling. | An easy count substitution is either a no-op or incomplete mathematics. |
| Qualified Overworld Performance | It can choose native renderer batching, bypassing generated shader budgets. End has a different route because all ten native renderer gates require Overworld. | A useful source edit must be connected to the actual dimension/preset consumer. |

Earlier MakeUp/Sildur wins came from renderer work with different removable work
and implementation costs. They were not a large universal shader optimization
waiting to be copied nine times. The current cloud/near-volume additions also
**postdate** the six-family FPS table: that older table cannot establish whether
the newer additions help. Independent native-driver blockers for Solas/Astra and
newer-loader bring-up are additional serial work, not shader-loop porting.
[Nine-family applicability and evidence](evaluation/optimization-yield-r2-20261012.md).

For future assignments, the decisive short question is: which selected native
operation goes from N to N′, over which active pixels/passes, and what stays the
same? Answer it from the existing source/options before investing in the full
validation chain. This is especially useful for conservative integer budgets;
more agents cannot turn a rounded-back count into a speedup.

### Direct work completed in this pass

**1. MakeUp End clouds: actual concept transfer.**
[Preparation script](../scripts/prepare_makeup_end_cloud_budget.py) reuses the
unchanged Overworld sample-budget helper at the native End-cloud integration site.
It preserves the slab endpoints and step-length weighting; eligible native ten
iterations become nine Balanced or eight Performance when Apply occurs in End.
Only one shader member changes. The output includes a separate candidate ZIP and
the complete recipe/resource/catalog integration patch, retaining the prior
recognized identity. Six offline tests and independent source review passed.

Use [candidate-r2](../dev/logs/makeup-end-cloud-budget-r2-20261012/candidate-r2/receipt.json)
and its [unapplied patch](../dev/logs/makeup-end-cloud-budget-r2-20261012/candidate-r2/integration.patch).
The initial metadata wrongly generalized the Overworld route to End; parent found
the dimension gate and author/reviewer corrected their analysis in a fresh receipt.
Native End rendering on the chosen tuple must be established first. The existing
NeoForge26.2 binding correction is Overworld-only, and portal reselection remains
unverified. Ten-to-nine iterations is not a ten-percent whole-frame FPS gain.
[Shader mathematics, checks and next consumer](evaluation/shader-transfer-methods-r2-20261012.md).

**2. Real Iris option preparation, reusable across family inputs.**
[Discovery utility](../scripts/discover_iris_options.py) replaces six private
Java/JShell copies and manual guesses about new option declarations. It consumes
actual original/generated archives, the observed native map and explicit pinned
Iris/JDK dependencies. BSL's 384→388 and Reimagined's 398→402 candidate maps match
saved evidence; an independent comparison also matched BSL's actual runtime map.
Five tests passed after review exposed and fixed acceptance of a native string
value removed from the generated declaration's allowed range. It emits a proposed
map, preserving strict runtime equality and distinguishing unused broad declarations.
[Usage, actual commands and boundaries](evaluation/developer-cycle-r2-20261012.md).

**3. Production hash allocation: a general performance candidate.**
The NeoForge26.2 compatibility path hashes whole shader archives every 50ms, even
without diagnostic counters. Existing BSL evidence records 2,437 source-stamp
reads; its original archive is 1,134,153 bytes. These are logical reads/allocations,
not evidence of physical disk traffic or the cause of earlier Fabric FPS results.

[The staged streaming change](../scripts/prepare_binding_digest_streaming.py)
keeps the same full-byte SHA-256, cadence, state checks and source refusal rules,
but uses a 64KiB buffer. It changes three exact certificate call sites and adds one
small helper. On the actual BSL archive, standalone JVM allocation fell from
**1,135,080 to 66,376 bytes per full hash**, independently reproduced over ten
warmed calls. Four tests compile the actual three certificate classes and exercise
hash equality, chunk boundaries, same-size/restored-mtime tampering, read/provider
failures, precise patch scope and overwrite refusal. Reviewer caught an exception
type change in the first helper; it was corrected to preserve the existing caught
failure path. No frame-time improvement is claimed.

[Exact unapplied overlay](../dev/logs/binding-digest-streaming-r2-20261012/candidate/integration.patch),
[independent receipt](../dev/logs/binding-digest-streaming-r2-20261012/independent-review/receipt.json).
This reduces repeated temporary allocation; it does not reduce hash cadence or
file bytes read. The generated marker/state branch is source-preserved rather
than newly executed in these standalone tests.

### Adoption boundary

The Iris preparation utility and revised assignments are usable now. The End-cloud
and streaming-hash product changes are reviewed, reproducible **unapplied source
candidates**; no updated mod JAR or game result is claimed. Keeping them isolated
preserves the sealed Reimagined next operation. Actual integration/build/runtime
verification belongs after explicit resume, without changing the current chosen
next shader action or its remaining allocation. Complementary's redundant prior
volume-weight evaluation is a further concrete arithmetic hypothesis in the source
report; compiler elimination and benefit remain unmeasured, so it was not expanded
into another experiment here.
