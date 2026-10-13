# ShaderLOD development throughput review

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
