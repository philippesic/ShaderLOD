---
name: start-goal-team
description: Turn a user-requested ShaderLOD goal into a durable checkpoint and coordinate scoped subagents. Use when the user requests a project goal or invokes this skill; ordinary feature edits do not start new goals.
---

# Start a ShaderLOD goal

Read `docs/STATUS.md`, `docs/PROJECT_OVERVIEW.md`, and the guidance needed for the
requested outcome. Translate the user's request into an objective, scope,
acceptance evidence, and independent work units. Preserve the chosen loader,
shader target, and development stage.

If an unfinished goal already exists, continue or clarify its relationship to
the request instead of replacing it silently. When the user has requested a new
goal and the native Goal tool is available, create it with that objective.
Specify a token budget only if the user requested one. Record durable state in
`docs/goals/<slug>.md` and link it from `docs/STATUS.md`.

Delegate independent work to bounded native subagents, using project roles when
available. Give each worker a deliverable, owned files, acceptance check,
stopping boundary, and reporting location. Inherit the current model settings
unless the user requests otherwise. Keep one owner of the interactive client
and GPU. A separate user-owned Codex chat requires an explicit request for a
new chat; this skill does not authorize remote pushes or publishing.

Use `docs/AGENT_OPERATIONS.md` for integration and handoffs. At milestones, save
actual checks, source versions, client/process ownership, remaining decisions,
and the exact next action. Record limitations without presenting planned checks
as completed evidence. Mark a native Goal complete only when its stated
outcome has been achieved.
