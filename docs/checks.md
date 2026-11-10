# Checks

The current enforcing lanes and semantic changes are described in [policy changes](../checks/policy-changes.md). `./check --fast` is partial feedback; `./check` is the full CPU gate; `./check --fresh` forces task execution. Required lanes cannot be omitted/disabled, required tests are minimum subsets, and failures/skips/empty suites refuse.

Java compilation, Error Prone, Checkstyle, Spotless, dependency analysis and compiled ArchUnit boundaries cover main, contracts and development outputs. Python uses Ruff, strict mypy, pytest and focused effect/policy rules. Paired negative/control specimens remain mandatory. Gradle reports actual execution versus reuse; bytecode/tool summaries are references, not extra test executions.

Reports live under ignored `build/reports/foundation`. Neither a CPU pass, compilation, static source inspection nor synthetic receipt grants live-functional/performance qualification. The repository has no hosted CI or remote protection.
