# System map

| Entry | Trigger | Owned state/output | Dependencies | Test |
|---|---|---|---|---|
| `checks/bootstrap.py:main` | ./check | Verified tool/runtime acquisition | Hash locks | `tests/test_foundation.py` |
| `checks/driver.py:main` | Gradle check | CPU reports and honest suite scope | Static tools | `tests/test_foundation.py` |
| `checks/contracts.py:decode_sample` | Synthetic wire boundary | Measured/missing immutable values | Standard library | `tests/test_foundation.py` |
| `shaderlod_host/runtime.py:execute` | host run | Frozen source, budget journal, owned watchdog | Closed request/storage contracts | `tests/test_host.py` |
| `shaderlod_host/storage.py:publish` | result finalization | Exclusive atomic completed result | Owned filesystem | `tests/test_host.py` |

Java responsibilities: `domain` contains immutable values; `engine` owns provider discovery and selection transitions; `storage` owns bounded archives and durable file recovery. `integration` binds pinned Iris/NeoForge and optional read-only DH. `devClient` instrumentation is packaged separately. Production source-set coverage and forbidden dependencies are checked against compiled output, not this map's prose.

CPU exercise evidence does not establish live renderer correctness. The live D gate remains separate from CPU orchestration. Benchmark methodology and high-level skills are deferred.
