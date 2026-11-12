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
| `shaderlod_host` | Python host runner: CLI, bounded execution, storage, supervised live sessions, comparisons, diagnostics, qualification and offline reports |
| `checks` | Bootstrap, check driver, policy rules, rule specimens ([checks](checks.md)) |
| `tests` | Python tests |
| `dependencies` | Digest locks ([dependencies](dependencies.md)) |
| `results` | Readable conclusions indexed by `FINDINGS.md`; frozen compact supporting records in `results/sources/` |
| External artifact root | Large raw evidence under `~/.local/share/shaderlod/evidence`; access and identity rules in [results](results.md) |
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
| `shaderlod_host/benchmark.py:analyze_frames` | Offline import or live fixed-scene capture | Complete-stream validity and descriptive frame analysis | Strict frame records | `tests/test_benchmark.py` |
| `shaderlod_host/runtime_live.py:benchmark_session` | `python3 -m shaderlod_host session-open` | Foreground Linux/native-Windows client, watchdog, external command/results | Prepared profile and allocated display | `tests/test_benchmark.py` |
| `shaderlod_host/comparison.py:execute_comparison` | `python3 -m shaderlod_host compare` | Frozen plan, paired routes, attempts and restored shader state | Live control protocol and frame receipts | `tests/test_comparison.py` |
| `shaderlod_host/validation.py:validate_session` | `python3 -m shaderlod_host validate` | Functional probe receipt and preserved output | Existing host functional validator | `tests/test_functional_contract.py` |
| `shaderlod_host/runtime_live.py:execute_campaign` | `python3 -m shaderlod_host qualify --spec` | Fixed fresh-session campaign and failure accounting | Session supervisor and frozen policy | `tests/test_campaign.py` |
| `shaderlod_host/assessment.py:assess` | `python3 -m shaderlod_host qualify --policy` | Session-level effects, bounded policy verdict | Immutable comparison/frame evidence | `tests/test_assessment.py` |
| `shaderlod_host/reports.py:history` | `python3 -m shaderlod_host history` | Offline compatible-series catalog | Verified comparison reductions | `tests/test_assessment.py` |

## Apply and recovery flow

The settings screen stages an archive and provider IDs. Apply runs `SelectionTransaction`: it journals the native preimage, writes through `NativeWriteScope`, asks Iris to reload, and moves to LINKED when the archive is loaded and to ACTIVE after a submitted-frame witness. Off restores the preimage. Cancel discards unapplied edits. A failed reload restores the preimage; if restoration fails, the journal stays and new selections refuse until recovery succeeds on the next `IrisConfig.load`.

## Benchmark ownership

The host owns plans, finite budgets, admission, receipts and analysis. The development-only controller owns real game selection, world-condition/pose control, capture and restoration. The existing integration/storage owners remain authoritative for native settings and file journals. GPU query ownership stays under `dev/measurement`. Semantic mappings label observed boundaries; they cannot manufacture timings or product eligibility. Raw evidence and input snapshots live externally. See [benchmarks](benchmarks.md).
