# Working with ShaderLOD in T3 Code

Keep one coordinator for current project state and the client/GPU lane. Give it
a concrete deliverable and relevant scope, allocation or quality preferences;
it chooses useful bounded children. Ask for a separate top-level conversation
when you want independently managed work. Delegation mechanics live in the
[runtime adapter](T3_AGENT_RUNTIME.md).

Use **Steer** to change active work: clarify, ask for status, correct scope or
stop. Use **Queue** for a follow-on task after the turn finishes; a queued message
is not an immediate stop. A partial pause should name the affected work. The
coordinator preserves the instruction and reports what actually stopped.
Canceling model work may leave an external client/SSH job alive; its owner must
check and restore/release it before reporting its resource free.

Ask for an independent worktree when edits would conflict. It starts from
committed inputs; relevant dirty changes require an explicit input handoff.
Shared resources retain one integration owner, and unrelated dirty work stays
intact. The adapter covers launch details and setup receipts.

[STATUS](STATUS.md) is the sole current checkpoint. Ask “what changed since my
last update?” for outcome, baseline comparison, observation time, main delay and
next result. Its linked goal preserves acceptance and history; only relevant
sections need loading. A new session verifies jobs/owners rather than assuming
a checkpoint transferred a process. Harness changes do not resume shader work.

## Configured development defaults

ShaderLOD selects Codex GPT-6.1 Sol with high reasoning and standard service tier.
Its five T3 actions are preparation, compact checkpoint, Python tests, Java tests
and build. Preparation checks prerequisites without launching a client or build;
missing ignored runtime jars are separate from usable source tooling. The
checkpoint helper is project-local and needs no personal skill tree.

New threads use the local checkout while it contains uncommitted campaign work.
Automatic worktree cleanup is off to preserve ignored evidence; archive canonical
results before manually removing one. Automatic usage-limit resumption is off;
resume deliberately so ownership can be reconciled.

The project uses T3's browser and notifications. Unrelated artifact plugins and
the Codex App's node/native-UI bridge are disabled locally, leaving global setup
intact. For native application work, ask to enable the relevant project plugin/
MCP entry and verify its old-app dependency. Changed tools need a fresh or safely
restarted agent session, without restarting client/GPU jobs or the T3 server.

One acknowledged editor owns shared home-directory settings across projects;
worktrees do not isolate them. Shared hooks cannot start legacy Stop continuation
inside T3. No background service was installed. Explicitly request a native Goal
or schedule when wanted; the adapter explains their distinct lifecycles. An open
thread or reminder does not guarantee unattended execution.

## Useful short requests

- “Implement this fix through the real entry point and verify it; keep PC runtime paused.”
- “Inspect this issue and recommend changes; do not edit code.”
- “Catch me up since yesterday: baseline, actual result, main delay and next milestone.”
- “Use an independent worktree; preserve this campaign's GPU owner.”
- “Have Astra give one independent proceed/change/park review, then return control.”

You do not need to prescribe agent counts, role files or orchestration calls.
Codex and Claude share the project contract, but access and native capabilities
differ. The migration's Claude bridge probe returned “Credit balance is too low”
before model work; local instruction/skill links are prepared, while live Claude
behavior remains unverified.
