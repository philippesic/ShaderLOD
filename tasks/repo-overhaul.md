# Development readiness

## Goal
Make the repository ready for new optimization development and testing. Completion means usable development tools and agreed working methods, not an accepted optimization or an exhausted research program.

## Authorized
Perform routine development-direction repairs, tests, instruction cleanup and task organization autonomously. Bring Philip changes to guarantees, scope or working conventions. Results, measurement methods and functional qualification belong to benchmark design. Disk-space policy belongs to agent ops. Backup setup follows development readiness.

## Done when
The three blockers are resolved: benchmark design, agent ops, and machine choice. Active documentation describes the current project and its development workflow. The agreed evidence workflow has passed an end-to-end smoke test. Necessary development tooling works on the chosen machine and remaining limitations are explicit.

## State
The mod foundation, local checks and pre-commit hook work. Full checks passed with 60 Python tests. A clean temporary clone passed ./check using shared tool and Minecraft caches; cold-cache installation was not tested. All ten existing FINDINGS references matched their recorded digests. No optimization is accepted.

The three blocking workstreams are [benchmark design](benchmark-design.md), [agent ops](agent-ops.md), and [machine choice](machine-choice.md). Evidence presentation and closeout are under discussion within agent ops; the current evidence convention remains in effect until agreement. Remote CI is excluded. No remote is configured, so commits remain local. Backup setup is deferred until readiness is complete.

## Next
Discuss the proposed evidence system, then implement the agreed format, selectively import relevant historical investigations, and smoke-test closeout. Finish development-direction repairs without seeking component-by-component approval. Address measurement, profiling, correctness qualification and live experiment design in benchmark design. Do not require a successful new optimization or fresh-agent comparison to declare the repository ready.
