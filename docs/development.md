# Development

Read [AGENTS](../AGENTS.md) for working rules and [architecture](architecture.md) for code responsibilities. Scan [FINDINGS](../FINDINGS.md) for relevant prior results, then read only the result pages relevant to the change. Check the assigned [task](../tasks/README.md) for scope and current state.

## Change and validate

Follow [README setup](../README.md#setup), including enabling the pre-commit hook. Make routine implementation decisions within the assigned scope. Update current topic documentation with behavior changes; remove obsolete instructions. Use [optimization modules](optimizations.md) for module development and [dependencies](dependencies.md) for approved pin updates.

Run `./check` while editing; it is explicitly partial. Run `./check --full` before committing and at handoff. The hook runs the full gate. See [checks](checks.md) for failure repairs and fresh validation. Use [runtime verification](runtime.md) for allocated live functional work. CPU checks do not establish rendering correctness or performance; benchmark design defines those methods.

## Documents and completion

`docs/` describes how the project works now. `tasks/` owns unfinished work. `results/` preserves durable lessons and investigation decisions. `FINDINGS.md` is the short entrance to those results. Git owns implementation history.

For a meaningful investigation, follow [close-results](../skills/close-results/SKILL.md) and [results](results.md). A result is warranted for a durable lesson, meaningful comparison or decision that future work should remember; a routine bug fix does not automatically warrant one. Update current docs, remove obsolete implementation within scope after verifying recoverability, update FINDINGS and delete the completed task.

Raw execution records remain outside the source tree. Keep compact figure inputs beside a result only when useful, and small archived supporting reports in `results/sources/`. Do not manufacture a chart or structured record for every result.

For rendering work, use the maintained [benchmark workflows](benchmarks.md) and the project [benchmark skill](../skills/benchmark/SKILL.md). Follow [optimization direction](optimization-philosophy.md): a first-pack win does not grant broad eligibility. Portable checks, live correctness, diagnostic profiling and contribution qualification answer different questions.
