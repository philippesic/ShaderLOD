# Repository overhaul

## Goal
Make this repository's agent environment one where the correct path is the easy one and wrong paths fail early with a named fix, now that the port is finished.

## Authorized
Philip approved, on 10 November 2026: removing port documents, rewriting AGENTS.md as a hub, the document convention with `tasks/`, rewriting FINDINGS, and making `./check` fast with the full check in the pre-commit hook. Each later component needs his decision before implementation.

## Done when
Every remaining component below has been reviewed with Philip, approved changes are committed with `./check --full` passing, and fresh-agent before/after trials have been run or explicitly declined.

## State
Done: documents and state, instructions, check modes and hook, feedback diagnostics, autonomy wording, live evidence location. Decided: no AGENTS.md size limit. Remaining: Iris and DH dependency handling (proposal with Philip), rules audit (D3 and D6d owner lists name paths that do not exist), tests, infrastructure once Philip adds a remote (then CI running `./check --full`).

## Next
Agree the Iris and DH dependency approach with Philip.
