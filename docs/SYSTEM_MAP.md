# System map

| Entry | Trigger | Owned state/output | Dependencies | Test |
|---|---|---|---|---|
| `checks/bootstrap.py:main` | ./check | External tool caches | Hashed pins | `tests/test_foundation.py` |
| `checks/driver.py:main` | Gradle check | CPU check reports | Static tools and policies | `tests/test_foundation.py` |
| `checks/verify.py:cpu` | ./verify --cpu | Temporary synthetic receipt | Contracts and publisher | `tests/test_foundation.py` |
| `checks/contracts.py:decode_sample` | Contract decode | Immutable values | Standard library | `tests/test_foundation.py` |
| `checks/publisher.py:publish` | Synthetic finalization | One durable value | Exclusive creation | `tests/test_foundation.py` |

No loader, client, runtime measurement or benchmark flow exists yet.
