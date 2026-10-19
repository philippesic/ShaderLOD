# Runtime adapter for T3 Code and native agents

The shared assignment/acceptance contract is in [agent operations](AGENT_OPERATIONS.md).
This document maps it to runtime tools; injected current tool contracts take
precedence if names or capabilities change. Check the live catalog before use.

## Delegation and review

Discover T3 provider instances/models with `orchestrator_capabilities`; configured
custom models may be absent from native tool model lists. Inherit the parent
model/reasoning settings unless explicitly selected otherwise. A provider/model
label does not itself establish a different task authority.

| Operation | Native subagent tools | T3 delegated tasks |
| --- | --- | --- |
| Start bounded child work | `spawn_agent` with supported role/model | `delegate_task` with target provider/model; retain returned `taskId` |
| Steer work already executing | `send_message` | Use only currently exposed task-control capabilities; do not message the backing thread as a task continuation |
| Continue a completed worker | `followup_task`; verify acknowledgment | New `delegate_task`, including original brief, prior findings/responses and unresolved objections |
| Independent review round | Fresh reviewer or supported new turn | New `delegate_task` every round, distinct `clientRequestId` per round, stable when retrying that round |
| Observe / cancel | Native state/events / exact agent interruption | `task_status` / `task_cancel` using `taskId` |

Prefer native same-provider tools when they support the selected model and the
task needs no separate checkout. Use T3 delegation for cross-provider work,
explicitly T3-owned children, unavailable native models, or a separate worktree.
Retain accepted task identities before retrying ambiguous launch responses;
inspect actual state to avoid duplicates. Never use `t3_thread_send` on a delegated
`childThreadId` to start another review round. That thread is backing storage.

Top-level `t3_thread_launch`/`create_threads` create ordinary user conversations.
Use them only when the user explicitly requests separate/new/top-level threads.
They are not the implementation of a request for subagents or parallel work.

## Worktrees and handoff

For a child needing another checkout use T3 `delegate_task` with
`isolation: "worktree"`. It starts from the parent local commit; uncommitted edits
are not copied. The preparation entry point is
`python3 scripts/setup_agent_worktree.py`; inspect its help for options. Do not
commit, copy the dirty checkout, or move an attached thread merely to satisfy an
assumption about isolation. Parent chooses a deliberate snapshot/patch handoff
when the task depends on uncommitted inputs, and verifies those inputs arrived.
Keep runtime worlds, captures and client ownership outside speculative worktrees.

The T3 preparation action invokes the canonical project's read-only setup script
with the new checkout as `--root`. This deliberately bootstraps older committed
bases that lack the new script; it does not copy uncommitted source into a child.
Any source needed beyond that bootstrap still requires an explicit input handoff.
The checkout's STATUS and instructions can also be old commit snapshots. Include
the canonical current STATUS path, current human scope and resource-owner terms
in the child brief; do not treat branch-local historical state as a new authority.

A task cancellation or thread stop does not by itself establish that an external
SSH process, GPU client or local child has exited. Retain exact process identity
and its stop/recovery path, then verify actual state before releasing ownership.
Keep one continuation owner and one stdout reader for each operation.

## Objectives, Goals and scheduling

STATUS and its linked goal record are portable durable project state. A native
Goal is a runtime continuation facility: create it only on an explicit new-Goal
request, use `get_goal` first, and verify actual state. If unavailable, report that
boundary while completing authorized independent work; do not silently create
a substitute schedule or top-level thread. A document is not a running Goal.

T3 child tasks are delegated work, ordinary threads are conversations, and
`schedule_task` creates persistent recurring automation. Create a schedule only
when requested, with the structured schedule object required by the live tool.
No hook, reminder or checkpoint proves execution or app uptime. Preserve current
human pauses, allocations and ownership across runtime changes.

## Tool availability

Do not assume `.codex/config.toml`, role TOMLs, hooks or skill UI metadata are
consumed identically by every provider. A model label in config is a default,
not evidence of which provider actually ran. Verify effective capability/settings
in a fresh session when validating the harness. T3's injected runtime instructions
describe the supported transport fallback if tools are lazily exposed or absent
from an ACP model's catalog; do not invent a shell-based agent launcher.

For an actual thread's model/effort, use `t3_thread_configuration`. The provider
catalog's option defaults/currentValue fields can differ from a thread's explicit
selection; they do not establish the effort used by that run.

On T3 0.1.0 with `projectSettingsFolded=true`, the action runner resolves
`projectSettingsOverrides[projectId].defaultProjectScripts` before environment
defaults. Saving `project.scripts` through `t3_project_update` alone did not make
the setup action run. The settings owner must import the action list through
normal project settings or merge that effective override, preserving other
projects. Verify an actual setup-script/terminal receipt; metadata and a generic
"Workspace ready" event do not prove the registered command executed. Recheck
this version-specific behavior when T3 changes.

For browser work prefer T3 `preview_*`: start with `preview_status`, then
`preview_open` if no automation surface is attached. Respect human-owned tabs.
For PR work register each worked PR with `link_pull_request` when exposed. Use
the provided watcher for requested PR monitoring instead of a custom polling job.
