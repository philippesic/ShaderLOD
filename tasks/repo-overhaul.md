# Repository overhaul

## Goal
Make this repository's agent environment one where the correct path is the easy one and wrong paths fail early with a named fix, now that the port is finished.

## Authorized
Philip approved, on 10 November 2026: removing port documents, rewriting AGENTS.md as a hub, the document convention with `tasks/`, rewriting FINDINGS, and making `./check` fast with the full check in the pre-commit hook. He also approved lock-driven dependency metadata, CPU checks of Iris mixin target methods, and local checks plus pre-commit with no remote CI. Other components need his decision before implementation.

## Done when
Every remaining component below has been reviewed with Philip, approved changes are committed with `./check --full` passing, and fresh-agent before/after trials have been run or explicitly declined.

## State
Done: documents and state, instructions, check modes and hook, feedback diagnostics, autonomy wording, live evidence location, lock-driven dependency filenames and metadata ranges, Iris target-method checks. Decided: no AGENTS.md size limit; no remote CI. No remote is configured; commits remain local. No runtime versions changed and no game was launched for this dependency work.

## Next
Review the remaining components together, then implement approved work in one batch:

1. Rules: audit every policy and exception against current code and its bad/control specimens. D3 and D6d currently allow nonexistent owner paths (including python/publisher.py and the shaderlod_host/runtime/ package prefix). Remove stale allowances and check for bypasses without weakening current guarantees.
2. Tests: review behavioral coverage, determinism, required-test inventory, and the path for adding tests. Exercise representative agent mistakes and confirm diagnostics point to a repair. Keep portable checks separate from runtime evidence.
3. Local infrastructure: check clean-clone bootstrap, enabled hooks, lock consistency and artifact preservation. Decide whether a user-owned remote is wanted for backup; remote CI is excluded regardless.
4. Live verification and evidence: audit recovery, receipt completeness and failure preservation against current commands. Preserve leftover probe artifacts in run-client-absent-dh before that profile is reused. This audit does not authorize retrying the known DH failure without a new hypothesis.
5. Efficacy: decide whether to run fresh-agent before/after trials on the same bounded tasks. Until measured, claim only that safeguards pass their checks, not improved agent performance.

Separate product decisions remain in tasks/dh-enabled-target.md and tasks/benchmark-design.md. They are not prerequisites for completing the repository environment overhaul and remain deferred.
