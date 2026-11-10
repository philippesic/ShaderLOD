# ShaderLOD-next

Agent-first NeoForge 21.1.253 / Minecraft 1.21.1 port. The first target is the pinned Complementary Reimagined r5.9.3 archive with Distant Horizons 3.3.3. The source includes native shader selection, transaction recovery, isolated optimization loading, and development-only bounded CPU/GPU frame instrumentation. Live functionality and performance remain unqualified until their explicit gates pass.

Read the [active porting plan](docs/PORTING_PLAN.md) for progress and stopping conditions. Benchmark redesign and high-level agent operations remain deferred. Historical optimizations stay in the old repository.

Install Python 3.13 and Git, then run `./check`. Setup verifies digest-pinned Temurin 21.0.13+9, Gradle 9.2.1 and hashed Python wheels in user-local caches. Dependency locks and verification metadata are enforced. First acquisition requires network; normal warmed checks reuse caches. Enable the local hook with `git config core.hooksPath .githooks`.

| Command | Scope |
| --- | --- |
| `./check --fast` | Partial edit feedback: compile, formatting, types, boundaries and CPU tests |
| `./check` | Full CPU gate, including policy/tool specimens |
| `./check --fresh` | Full gate with task reuse disabled |
| `./verify --cpu` | Synthetic contract/publication round trip |
| `./verify --runtime` | Explicit refusal until live verification exists |
| `python3 -m shaderlod_host doctor` | Current host capabilities; no hardware launch |
| `python3 -m shaderlod_host --help` | Versioned bounded CPU plan/run/verify/report interface |

The main JAR contains no development mod or historical optimizations. [Optimization modules](docs/optimizations.md) own their source, descriptor and tests; registration is generated and consumed by the packaged loader. Its mod config screen stages an archive and optional provider IDs; Apply performs a reload and requires matching observed activation. Off restores the captured native preimage. Cancel discards unapplied UI edits. Failed restoration retains the recovery journal and refuses replacement. Pending native Iris option queues must be applied/cancelled in Iris first. Original shader options, resolution and AA are preserved. A native rendering witness is distinct from visual correctness and performance qualification.

The Linux host executor freezes declared sources, pins its executable, bounds attempts and time, and owns child process groups with a watchdog. Windows execution explicitly refuses pending qualification. Neither CPU fixtures nor an idle GPU observation establishes permission or safe hardware exclusion. Keep live jobs within an allocated host/device window.

| Accepted optimization | Scope | Evidence |
| --- | --- | --- |

No new optimization has performance evidence. [Findings](FINDINGS.md) retain historical positive, negative and inconclusive observations with their original scopes.

[System map](docs/SYSTEM_MAP.md), [check policy changes](checks/policy-changes.md), [source disposition](docs/source-disposition.md), [evidence levels](docs/evidence-levels.md). This repository remains local-only; no remote or hosted review protection is configured.
