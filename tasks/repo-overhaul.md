# Repository overhaul

## Goal
Make this repository's agent environment one where the correct path is the easy one and wrong paths fail early with a named fix, now that the port is finished.

## Authorized
Philip approved, on 10 November 2026: removing port documents, rewriting AGENTS.md as a hub, the document convention with `tasks/`, rewriting FINDINGS, and making `./check` fast with the full check in the pre-commit hook. Each later component needs his decision before implementation.

## Done when
Every remaining component below has been reviewed with Philip, approved changes are committed with `./check --full` passing, and fresh-agent before/after trials have been run or explicitly declined.

## State
Done: documents and state, instructions, check modes and hook. Remaining components: feedback diagnostics, rules audit, tests, autonomy, dependency integration, evidence and reporting, infrastructure. Known items for them: diagnostics arrive inside Python tracebacks and Gradle prints every task line; D3 and D6d owner lists name paths that do not exist (`python/publisher.py`, `shaderlod_host/storage/`, `shaderlod_host/runtime/`); no AGENTS.md size budget, matching Philip's earlier "no arbitrary byte cap" decision; no remote, so the only copy of the repository is this disk.

## Next
Review feedback diagnostics with Philip.
