# Development

[AGENTS](../AGENTS.md) routes assigned work to the relevant procedure. Follow the setup below, including enabling the hook once. Read the
assigned task and relevant topic docs; use the [architecture](architecture.md) to
locate ownership and entry points. For optimization work, use the
[optimize skill](../skills/optimize/SKILL.md) and relevant prior FINDINGS.

## Setup

Use Python 3.13 and Git. Enable the local hook once per clone:

```
git config core.hooksPath .githooks
./check
```

The first check acquires and verifies the pinned JDK, Gradle and Python tools;
subsequent runs reuse user-local caches. See [dependencies](dependencies.md) for
pins and repair/update paths. Run `./check --full` for the complete portable gate.
To build a development JAR after setup, use the pinned JDK:

```
JAVA_HOME=~/.local/share/shaderlod-jdks/jdk-21.0.13+9 ./gradlew --no-daemon jar
```

The shipping JAR is `build/libs/shaderlod-0.1.0.jar`. The development probe JAR is
separate and is not a distribution artifact. Live profiles, worlds and captures
are external inputs prepared through [runtime](runtime.md) and [benchmarks](benchmarks.md).
A source clone does not include those artifacts.

## Topic map

- [Architecture](architecture.md): code ownership, behavior and entry points.
- [Checks](checks.md): validation selection, coverage and failure repairs.
- [Iris integration](iris-integration.md): activation, native settings and compatibility.
- [Dependencies](dependencies.md): pins and the supported update path.
- [Optimization direction](optimization-philosophy.md) and [modules](optimizations.md): mechanisms and implementation contracts.
- [Benchmark workflows](benchmarks.md), [definitions](benchmark-definitions.md) and [profiling](profiling.md): measurements and their limits.
- [Results](results.md), [FINDINGS](../FINDINGS.md) and [tasks](../tasks/README.md): evidence, prior conclusions and unfinished work.

## Change and validate

Keep the change within its authorized goal. Make invalid states explicit at the
owning boundary and use supported extension points. [Optimization modules](optimizations.md),
[Iris integration](iris-integration.md) and [dependency updates](dependencies.md)
own their particular implementation rules.

Choose validation by the behavior or claim in [checks](checks.md). Use fast feedback
during edits and the full portable gate at coherent checkpoints and handoff. The
hook runs that gate. Live lifecycle checks, performance screens and qualification
answer different questions; use the [benchmark procedure](benchmarks.md) as relevant.
A documentation-only edit does not require a game launch.

Maintaining affected tests, commands, navigation and current explanations is part
of the change. Update the existing [entry-point map](architecture.md#entry-points)
when a supported entry point changes. Keep command details in their topic owner;
link them from navigation rather than creating another command list. Existing
behavioral tests may suffice; add tests for meaningful new behavior and failure cases.
Keep required-test changes and any safeguard replacement within the
[check maintenance rules](checks.md#tests).

For multi-session work, keep authorization, current implementation, evidence and
next action in the assigned task. Distinguish proposed decisions from approved
ones. Rewrite state rather than appending progress logs. A question during work
steers the task; it does not erase its objective or existing authorization.

## Documents and completion

| Home | Owns |
| --- | --- |
| `AGENTS.md` | Local boundaries and routing every agent needs |
| `README.md` | Introduction, setup and topic navigation |
| `docs/architecture.md` | Current ownership, behavior and supported entry points |
| `docs/checks.md` | Validation selection, coverage and failure repairs |
| Other topic docs | Current detailed mechanics and their maintained commands |
| `tasks/NAME.md` | One unfinished assigned goal, scope, current state and next action |
| `results/NAME/` | Durable scoped conclusions and compact supporting data |
| `FINDINGS.md` | Short conclusion/link index |
| Git history | Implementation history |

For meaningful investigation closeout use the pinned
[close-results skill](../skills/close-results/SKILL.md) with [results](results.md).
Routine fixes do not automatically need result pages. Preserve required source and
inputs before authorized pruning, update affected current docs and findings, validate
the remaining project, and delete the completed task. A bounded investigation can
finish with an honestly unresolved result. Reading an open task does not assign it;
see the [task convention](../tasks/README.md).

## Skill ownership and updates

`skills/optimize` and `skills/benchmark` own project procedures and evolve here with
their supporting docs. They call maintained commands and do not provide competing
runners. Global `benchmark-design` designs or repairs methods; `repo-environment`
handles environment work; `correct` repairs requested recurring workflow failures.
They do not add automatic agent reviews or assignments.

`skills/close-results` is a pinned copy of the shared p-stack procedure. Its
[project specification](results.md#result-presentation-and-closeout) records the
source revision and update path. Use the tracked copy for this project so an
unrelated global sync does not silently change its closeout procedure. Shared
updates are deliberate reviewed copies, not independently maintained variants.

Evaluate these instructions through ordinary work. Skill/schema and link checks
establish structural validity; they do not demonstrate better agent outcomes. Repair
observed friction without adding a standing agent evaluation or maintenance framework.
