# Agent operations

## Goals and teams

The native Codex Goal sustains work in a chat. `docs/STATUS.md` and its linked
`docs/goals/` checkpoint preserve the project between sessions. The user requests
new goals; the repository-local `$start-goal-team` skill supports that workflow.
Do not silently replace an unfinished goal or create a new user-owned chat.

Use native subagents for independent subtasks. Assign deliverables, files,
acceptance evidence, and stopping boundaries before launch. The project has
four optional profiles under `.codex/agents/`: `feature_owner`, `shader_owner`,
`runtime_operator`, and `research_advisor`. They inherit the parent model and
reasoning settings. Roles are responsibilities, not a required team size.

For initial setup, explicit file partitions in a shared checkout are practical.
For overlapping or larger edits, use isolated worktrees and integrate only
reviewed work. A worker reports findings to the orchestrator; the orchestrator
owns the final checkpoint and speaks to the user.

## Client and GPU ownership

One agent owns the interactive Minecraft client, shader reloads, world changes,
and GPU capture at a time. Record the run command, process/session identifier,
run directory, active shader pack, scene, and how to stop the owned process.
Before handing off, verify whether the client is still running. Do not stop
unrelated Java or game processes by broad process-name matching.

Build success establishes Java/build integration. A title screen establishes
startup. In-world rendering establishes shader compilation and scene behavior.
DH presence in a mod list does not establish DH terrain rendering. Use
`docs/EVALUATION.md` to keep these evidence levels distinct.

No remote GPU host is configured by this scaffold. If later authorized, record
hardware and connection procedures explicitly rather than copying another
project's host identities. A local Mac result does not validate Windows/Linux
GPU behavior.

## Checkpoints and handoffs

Update the active goal at milestones with completed work, exact checks and
results, selected dependency revisions, relevant commits, active agents,
worktrees, owned processes, unresolved decisions, and the next action. Keep
full logs and captures in ignored runtime/output directories and link them from
the checkpoint when needed.

The `SessionStart` hook emits a checkpoint reminder on startup, resume, and
compaction. It does not launch replacement chats, impose a context limit, or
transfer process ownership. After compaction, re-read the checkpoint and verify
live jobs; when a handoff is useful, finish or explicitly transfer live work
within the user's authorized scope. Durable files remain useful if hooks are
unavailable or their repository trust has not been enabled.

## Git and external actions

Do not copy sibling Git history, remotes, project identities, or user secrets.
Keep commits coherent and subjects short, with no coauthor trailers. Publishing,
remote pushes, and messages to external services require authorization from the
user's request; local agent instructions do not grant it. Preserve unmerged
work before removing any worktree. Avoid broad `git clean` in the shared checkout.

Downloaded jars, launch directories, shader source checkouts, world saves,
screenshots, GPU captures, and raw logs stay out of Git. Record versions/hashes
and keep local reproduction scripts plus maintained patches in Git. Preserve
upstream licenses and source ancestry for shader derivatives.

Configuration follows the official [subagent documentation](https://learn.chatgpt.com/docs/agent-configuration/subagents)
and [hook documentation](https://learn.chatgpt.com/docs/hooks). The repository
skill follows the [skills workflow](https://learn.chatgpt.com/docs/build-skills).
