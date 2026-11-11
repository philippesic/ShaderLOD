# ShaderLOD-next

A NeoForge 1.21.1 / Minecraft 1.21.1 client mod for Iris shader packs. Its settings screen selects a pack natively and applies optional optimization modules to it. Apply reloads Iris and counts the selection as active only after a frame is submitted through the selected pipeline. Off restores the previous native settings, Cancel discards unapplied edits, and a failed Apply restores automatically.

The first target is Complementary Reimagined r5.9.3 with Distant Horizons 3.3.3. Without DH it passes live functional verification on an Asahi M1. With DH enabled, the original pack exceeds the GPU's fragment sampler limit; see [FINDINGS](FINDINGS.md) F9 and F10. No optimization exists yet, and nothing is performance-qualified.

## Setup

Install Python 3.13 and Git, then run `./check`. The first run downloads and verifies the pinned JDK, Gradle and Python tools into user-local caches, and enables the pre-commit hook.

| Command | Purpose |
| --- | --- |
| `./check` | Fast edit feedback; prints `PARTIAL` |
| `./check --full` | Full CPU gate; run by the pre-commit hook |
| `./check --fresh` | Full gate without task reuse |
| `./verify --cpu` | Synthetic contract and publication round trip |
| `./verify --runtime …` | Live functional probe on an allocated machine; see [live verification](docs/runtime.md) |
| `python3 -m shaderlod_host doctor` | Host capabilities, without launching anything |
| `python3 -m shaderlod_host --help` | Host runner: plan, run, verify, report, recover |

## Accepted optimizations

| Optimization | Scope | Evidence |
| --- | --- | --- |

None yet. Agent rules and the documentation map are in [AGENTS.md](AGENTS.md).
