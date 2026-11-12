# ShaderLOD

A NeoForge 1.21.1 client mod that selects Iris packs natively and applies optional
optimization modules. The primary development machine is RTX 2080 Ti (WSL),
with an Asahi secondary checkout. Both use `/home/philip/ShaderLOD`.
Global AGENTS.md owns working preferences and authorization; this file adds project
navigation and correctness boundaries.

## Start with the assigned work

For optimization research or a previously studied failure, scan
[FINDINGS](FINDINGS.md) and follow the relevant results.

| Work | Procedure and context |
| --- | --- |
| Ordinary code or documentation change | [Development](docs/development.md), relevant [architecture](docs/architecture.md), [check selection](docs/checks.md) |
| Optimization investigation or implementation | [Optimize skill](skills/optimize/SKILL.md) and [optimization direction](docs/optimization-philosophy.md) |
| Run validation, profiling or comparisons | [Benchmark skill](skills/benchmark/SKILL.md); [workflow commands](docs/benchmarks.md) |
| Maintain documentation or task state | [Document roles and completion](docs/development.md#documents-and-completion); [task convention](tasks/README.md) |
| Close a meaningful investigation | Pinned [close-results skill](skills/close-results/SKILL.md) and [project result specification](docs/results.md) |
| Dependency or runtime change | [Dependency updates](docs/dependencies.md), [runtime verification](docs/runtime.md), relevant [Iris integration](docs/iris-integration.md) |

These project skills are tracked here; read them at the indicated trigger even if
a global skill catalog does not list them. Shared `benchmark-design` is for designing
or repairing measurement methods; ordinary runs use the project benchmark skill.
[README](README.md) introduces the product. Setup and the full topic map live in
[development](docs/development.md). Evidence format, artifact extraction and deletion
belong to [results and closeout](docs/results.md#result-presentation-and-closeout).

## Correctness and code boundaries

- Preserve original shader-pack archives, user-selected settings, native output
  resolution and AA, and exact restoration on Off, Cancel and failed Apply.
  Internal approximations follow the perceptual-quality and eligibility requirements in [optimization
  direction](docs/optimization-philosophy.md); distance alone proves nothing.
- Keep packages within the [system map](docs/architecture.md). Settings belong to
  `shaderlod_host.config`, publication to `shaderlod_host.storage`, and launches to
  the maintained runtime owners. Unknown inputs refuse with a reason.
- The global comment preference is enforced here across authored code, tests,
  scripts, build files and configuration, including Javadoc. Only the
  [exact project exceptions](docs/checks.md#comment-exceptions) apply. Never skip or
  disable a check or remove a test to pass.
- A `TOOLS:` checksum mismatch requires stopping affected verification and reporting
  it; never edit a lock to obtain a pass. Use the maintained dependency-update path.
- Live claims require matching runtime-owned receipts from the
  [supported workflows](docs/benchmarks.md).

## Project resources

- Bounded live work on the allocated Asahi display is permitted within the assigned
  scope. Other hardware needs an explicit allocation for that work; historical runs
  do not grant one. RTX 5080 is not allocated.
- Runtime ownership and recovery follow the
  [runtime guide](docs/runtime.md#process-ownership).
- Project-owned persistent state stays inside this checkout. Temporary outputs use
  ignored `.local/artifacts`; the
  [result specification](docs/results.md) owns extraction, retention and deletion.
- Established origin: `git@github.com:philippesic/ShaderLOD.git`.
