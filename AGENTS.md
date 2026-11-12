# ShaderLOD

A NeoForge 1.21.1 client mod that selects Iris packs natively and applies optional
optimization modules. The canonical checkout is `/home/philip/ShaderLOD`.
Global working preferences apply; this file owns local correctness boundaries and
navigation. Confirm the target checkout before work.

## Start with the assigned work

Read the relevant task when assigned and load only the topic guidance needed.
An open task does not authorize resuming it. Scan [FINDINGS](FINDINGS.md) for related
investigations when researching an optimization or a previously studied failure;
follow relevant results rather than reading every supporting record.

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

- Preserve original shader-pack archives, user-selected settings, native output resolution and
  AA, and exact restoration on Off, Cancel and failed Apply. Internal approximations
  follow the perceptual-quality and eligibility requirements in [optimization
  direction](docs/optimization-philosophy.md); distance alone proves nothing.
- Keep packages within the [system map](docs/architecture.md). Settings belong to
  `shaderlod_host.config`, publication to `shaderlod_host.storage`, and launches to
  the maintained runtime owners. Unknown inputs refuse with a reason.
- No comments, Javadoc or docstrings in authored code, tests, scripts, build files
  or configuration. Rationale belongs in topic docs; exact exceptions follow
  [checks](docs/checks.md). Never skip or disable a check or remove a test to pass.
- A `TOOLS:` checksum mismatch requires stopping affected verification and reporting
  it; never edit a lock to obtain a pass. Use the maintained dependency-update path.
- Portable checks do not establish rendering or performance. Live claims require
  matching runtime-owned receipts from the [supported workflows](docs/benchmarks.md).

## Local authority and preservation

Routine factual updates to AGENTS.md, navigation, tests and current docs are part
of an authorized change. Changing working policy, correctness guarantees, native
quality or product support scope requires agreement. Keep reusable preferences in
global instructions and project mechanics in their topic docs.

- Bounded live work on the allocated Asahi display is permitted within the assigned
  scope. Other hardware needs an explicit allocation for that work; historical runs
  do not grant one. RTX 5080 is not allocated. Record budgets and allocation in the
  assigned task; historical hardware coverage is not a new allocation.
- Identify owned processes by host, boot and process birth, never PID alone. Use
  maintained recovery paths and preserve active/shared work.
- No scheduler, GPU lease service or automatic evidence expiry. Cleanup follows
  global ownership rules and the [result specification](docs/results.md). Temporary
  raw outputs live under `~/.local/share/shaderlod/evidence`; closeout extracts
  compact evidence into results/ and removes superseded bulky outputs within scope.
- The established user-owned origin is `git@github.com:philippesic/ShaderLOD.git`.
  Commit coherent checkpoints and push under the global publishing convention.
  Preserve implementation history; never force-push.
