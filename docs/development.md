# Development

[AGENTS](../AGENTS.md) routes assigned work to the relevant procedure. Follow the setup below, including enabling the hook once. Read the
assigned task and relevant topic docs; use the [architecture](architecture.md) to
locate ownership and entry points. For optimization work, use the
[optimize skill](../skills/optimize/SKILL.md) and relevant prior FINDINGS.

## Machines and T3

RTX 2080 Ti (WSL) is the primary development machine; Asahi is secondary. Each
has its own `/home/philip/ShaderLOD` checkout of the established origin. Push source
changes before switching machines and fast-forward a clean destination checkout.
The Mac retains only `.git` at `/Users/pippo/github/shaderlod` for historical
repository identity and threads; it is not a development checkout. RTX 5080 has
no ShaderLOD checkout.
Ignored tools, prepared clients/worlds and host setup are local resources, not part
of Git synchronization. A source checkout alone does not qualify a runtime or GPU. On the 2080 Ti, editing
and portable checks run in WSL; GPU benchmarks use the maintained native Windows
supervision and preparation path in [benchmarks](benchmarks.md).

The 2080 Ti T3 project is pinned. Secondary and historical registrations retain
their threads; the Mac registration points to its Git-only folder. Pinning and archiving do not set
new-thread routing: the current Mac T3 client inherits the current thread or draft’s
machine, and grouped repositories may still select an archived representative.
Explicitly select RTX 2080 Ti in the new draft’s machine selector before submitting.
Philip chose explicit machine selection rather than a T3 client change. Before
sending a new draft, confirm its machine is RTX 2080 Ti (`DESKTOP-T4JKGJB`) and
its project is ShaderLOD. No persistent per-project routing default is configured.
The shared project favicon is tracked at `assets/shaderlod.svg`.

## Setup

Use Python 3.13 and Git. If the host has a different Python version, install a
project-local 3.13 interpreter at `.local/tools/python/bin/python3`; `./check` and
`./verify` prefer it without changing the system Python. On the 2080 Ti this points
to the project-local standalone Python 3.13.2 installation. Use that interpreter
for guide commands written as `python3` too, or put its `bin` directory first in
that terminal’s PATH. This does not require changing global shell settings.
Enable the local hook once per clone:

```
git config core.hooksPath .githooks
./check
```

The first check acquires and verifies the pinned JDK, Gradle and Python tools;
subsequent runs reuse ignored repository-local caches. See [dependencies](dependencies.md) for
pins and repair/update paths. Run `./check --full` for the complete portable gate.
To build a development JAR after setup, use the pinned JDK:

```
JAVA_HOME="$PWD/.local/tools/jdks/jdk-21.0.13+9" ./gradlew --no-daemon jar
```

The shipping JAR is `build/libs/shaderlod-0.1.0.jar`. The development probe JAR is
separate and is not a distribution artifact. Live profiles, worlds and captures
are ignored local working inputs prepared through [runtime](runtime.md) and [benchmarks](benchmarks.md).
A source clone does not include those artifacts. Keep project-owned persistent data
inside this checkout, apart from the [Windows working copy](runtime.md#rtx-2080-ti-windows-setup); the [storage layout](results.md#where-evidence-lives) distinguishes
tracked findings, working resources and disposable outputs.

## Topic map

- [Architecture](architecture.md): code ownership, behavior and entry points.
- [Checks](checks.md): validation selection, coverage and failure repairs.
- [Iris integration](iris-integration.md): activation, native settings and compatibility.
- [Dependencies](dependencies.md): pins and the supported update path.
- [Optimization direction](optimization-philosophy.md) and [modules](optimizations.md): mechanisms and implementation contracts.
- [Benchmark workflows](benchmarks.md), [definitions](benchmark-definitions.md) and [profiling](profiling.md): measurements and their limits.
- [Results](results.md), [FINDINGS](../FINDINGS.md) and [tasks](../tasks/README.md): evidence, prior conclusions and unfinished work.

## Change and validate

Make invalid states explicit at the owning boundary and use supported extension points.
[Optimization modules](optimizations.md),
[Iris integration](iris-integration.md) and [dependency updates](dependencies.md)
own their particular implementation rules.

Choose validation by the behavior or claim in [checks](checks.md). Use fast feedback
during edits and the full portable gate at coherent checkpoints and handoff. The
hook runs that gate. Live lifecycle checks, performance screens and qualification
answer different questions; use the [benchmark procedure](benchmarks.md) as relevant.
A documentation-only edit does not require a game launch.

Update the [entry-point map](architecture.md#entry-points) when a supported entry point
changes. Command details belong in their topic guide; navigation links there.
Required-test changes and any safeguard replacement follow the
[check maintenance rules](checks.md#tests).

## Documents and completion

| Home | Owns |
| --- | --- |
| `AGENTS.md` | Local boundaries and routing every agent needs |
| `README.md` | Product introduction, requirements and usage |
| `docs/development.md` | Contributor setup, topic navigation and development procedure |
| `docs/architecture.md` | Current ownership, behavior and supported entry points |
| `docs/checks.md` | Validation selection, coverage and failure repairs |
| Other topic docs | Current detailed mechanics and their maintained commands |
| `tasks/NAME.md` | One unfinished assigned goal, scope, current state and next action |
| `results/NAME/` | Durable scoped conclusions and compact supporting data |
| `FINDINGS.md` | Short conclusion/link index |
| Git history | Implementation history |

For meaningful investigation closeout use the pinned
[close-results skill](../skills/close-results/SKILL.md) with [results](results.md).
The result specification defines when a result is warranted and what to retain;
the [task convention](../tasks/README.md) defines completion and task removal.

## Skill ownership and updates

`skills/optimize` and `skills/benchmark` own project procedures and evolve here with
their supporting docs. They call maintained commands and do not provide competing
runners. Global `benchmark-design` designs or repairs methods; `repo-environment`
handles environment work; `correct` repairs requested recurring workflow failures.

`skills/close-results` is a pinned copy of the shared p-stack procedure. Its
[project specification](results.md#result-presentation-and-closeout) records the
source revision and update path. Use the tracked copy for this project so an
unrelated global sync does not silently change its closeout procedure. Shared
updates are deliberate reviewed copies, not independently maintained variants.
