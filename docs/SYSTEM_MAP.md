# System map

## Layout

| Path | Holds |
| --- | --- |
| `src/main/java/com/pp/shaderlod/domain` | Immutable values: pack identity, selection, activation, native state, texture-unit budget |
| `src/main/java/com/pp/shaderlod/engine` | Provider discovery, optimization registry, selection transaction |
| `src/main/java/com/pp/shaderlod/storage` | Bounded pack archives, atomic files, recovery journal, native write scope |
| `src/main/java/com/pp/shaderlod/integration` | Iris, NeoForge and optional DH adapters, settings screen, mixins ([Iris integration](iris-integration.md)) |
| `src/devClient` | Development-only mod: functional probe, identity and failing test providers, profiler. Never in the shipping JAR |
| `src/benchmarkCore` | Dependency-free measurement and evidence contracts |
| `src/test/java` | JUnit tests, including compiled ArchUnit boundaries |
| `optimizations/<id>/` | Optimization modules, none yet ([optimizations](optimizations.md)) |
| `shaderlod_host` | Python host runner: CLI, bounded execution, storage, live functional runner |
| `checks` | Bootstrap, check driver, policy rules, rule specimens ([checks](checks.md)) |
| `tests` | Python tests |
| `dependencies` | Digest locks ([dependencies](dependencies.md)) |
| `evidence` | Frozen evidence records referenced by `FINDINGS.md` |
| `tasks` | Open multi-session tasks |

Package boundaries and forbidden dependencies are enforced by ArchUnit against compiled output, not by this table.

## Entry points

| Entry | Trigger | Owned state/output | Dependencies | Test |
|---|---|---|---|---|
| `checks/bootstrap.py:main` | `./check`, `./verify` | Verified tool and runtime acquisition | Hash locks | `tests/test_foundation.py` |
| `checks/driver.py:main` | Gradle check steps | CPU reports and suite scope | Static tools | `tests/test_foundation.py` |
| `checks/contracts.py:decode_sample` | Wire boundary | Measured or missing values | Standard library | `tests/test_foundation.py` |
| `shaderlod_host/runtime.py:execute` | `python3 -m shaderlod_host run` | Frozen source, attempt budget journal, owned watchdog | Request and storage contracts | `tests/test_host.py` |
| `shaderlod_host/runtime.py:recover_execution` | `python3 -m shaderlod_host recover` | Quiescence check, preserved attempt budget | Child and watchdog identity | `tests/test_host.py` |
| `shaderlod_host/storage.py:publish` | Result finalization | Exclusive atomic completed result | Owned filesystem | `tests/test_host.py` |
| `shaderlod_host/runtime_live.py:main` | `./verify --runtime` | Live client attempt and functional receipt | Built JARs, pinned runtime | `tests/test_functional_contract.py` |

## Apply and recovery flow

The settings screen stages an archive and provider IDs. Apply runs `SelectionTransaction`: it journals the native preimage, writes through `NativeWriteScope`, asks Iris to reload, and moves to LINKED when the archive is loaded and to ACTIVE after a submitted-frame witness. Off restores the preimage. Cancel discards unapplied edits. A failed reload restores the preimage; if restoration fails, the journal stays and new selections refuse until recovery succeeds on the next `IrisConfig.load`.
