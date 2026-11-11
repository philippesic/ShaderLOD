# Repository overhaul

## Goal
Make this repository's agent environment one where the correct path is the easy one and wrong paths fail early with a named fix, now that the port is finished.

## Authorized
Philip approved, on 10 November 2026: removing port documents, rewriting AGENTS.md as a hub, the document convention with `tasks/`, rewriting FINDINGS, and making `./check` fast with the full check in the pre-commit hook. He also approved lock-driven dependency metadata, CPU checks of Iris mixin target methods, and local checks plus pre-commit with no remote CI. Philip subsequently authorized routine overhaul repairs and verification autonomously; bring only decisions requiring his judgment.

## Done when
Every remaining component below has been reviewed with Philip, approved changes are committed with `./check --full` passing, and fresh-agent before/after trials have been run or explicitly declined.

## State
Completed: document structure, autonomy, quiet check modes and hook, external live evidence, lock-driven dependency metadata, Iris target-method checks, stale Python owner removal, required critical regression tests, runtime-lock cache input, actionable download checksum errors, and malformed-probe failure handling. Legacy probe outputs were copied with verified hashes to ~/.local/share/shaderlod-next/evidence/legacy-probe-20261110 before removing profile copies. No live run or runtime version change was made.

No remote is configured; commits remain local. Remote CI is excluded. Existing process tests exercise real watchdog behavior, so their bounded waits remain intentional. Rule specimens and tool checks validate enforcement, not improved agent productivity.

## Next
Continue routine auditing and repairs without seeking per-component approval. Remaining verification includes clean-clone setup, broader runtime interruption and preservation failure paths, evidence-reference integrity, and rule bypass coverage. Do not describe these as fully audited yet.

Bring Philip only concrete decisions: retiring a guarantee that no longer serves the project, choosing backup destinations, or changing the product target. Fresh-agent comparisons are optional validation, not a prerequisite for ordinary repairs.

Separate deferred product decisions remain in tasks/dh-enabled-target.md and tasks/benchmark-design.md. No new DH attempt is justified without a new hypothesis.
