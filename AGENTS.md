# ShaderLOD-next

ShaderLOD is a NeoForge 1.21.1 client mod that selects Iris shader packs natively and loads optimization modules that transform them. The correctness boundary: original shader archives, effective shader options, native resolution and AA are never changed without the user's selection, and Off, Cancel and a failed Apply always restore the exact previous state. The existing live setup uses Complementary Reimagined r5.9.3 on Asahi. The development machine is pending selection; existing evidence does not grant allocation elsewhere.

## Where things are

- [README](README.md): setup and commands.
- Benchmark workflows: [Benchmarks](docs/benchmarks.md) and [benchmark skill](skills/benchmark/SKILL.md).
- Active development decisions: [agent ops](tasks/agent-ops.md) and [machine choice](tasks/machine-choice.md).
- [Architecture](docs/architecture.md): layout, entry points, Apply/recovery flow.
- [Development](docs/development.md): how to change, validate and close work.
- [Iris integration](docs/iris-integration.md): selection states, native writer journal, mixins, sampler indices, DH.
- [Optimization modules](docs/optimizations.md): how to add or remove one.
- [Live verification](docs/runtime.md): `./verify --runtime`, the probe, process ownership.
- [Checks](docs/checks.md): check modes, rule IDs and their fixes.
- [Dependencies](docs/dependencies.md): pins and what to do when one does not match.
- [Results](docs/results.md): project result specification and closeout rules. Use `close-results` for meaningful investigation closeout.
- Scan [FINDINGS](FINDINGS.md) for relevant established results; follow its links when useful. Read the assigned task and [task convention](tasks/README.md).

## Code

- No comments, Javadoc or docstrings in code, tests, scripts, build files or configuration. Say it with names, types and small functions. Put rationale in the topic doc or commit message.
- Keep each package to its role in the system map; ArchUnit enforces it. Make invalid states unrepresentable with types; refuse unsupported input with a reason instead of guessing.
- One owner per piece of state: settings via `shaderlod_host.config`, results via `shaderlod_host.storage.publish`, launches via `shaderlod_host.runtime`.
- Tests are small, behavioral and deterministic. Never skip, disable or delete a test to pass.
- Unsupported combinations stay explicit. Never lower shader settings, samplers, resolution or AA to obtain a pass.

## Validation

- Enable the pre-commit hook once per clone: `git config core.hooksPath .githooks`.
- `./check` is the fast lane (about 5 to 8 seconds warm) and labels itself `PARTIAL`.
- `./check --full` runs everything portable. The pre-commit hook runs it; run it before handoff too.
- When a check fails, fix the cause. The rule's fix is in [checks](docs/checks.md). Replacing a rule needs a stricter or equal rule that still rejects the original bad specimen.
- A `TOOLS:` checksum mismatch means stop and report; never edit a lock to pass.
- CPU checks say nothing about live behavior. Live claims need `./verify --runtime` receipts.

## Working autonomously

Work autonomously toward the goal Philip gives, within any limits he states. Do not stop for approval of steps that serve that goal: source and test changes, refactors, check repairs that keep the rule, bounded live attempts on the allocated Asahi machine, recovery of owned processes and settings, reclaiming files under owned roots that are explicitly disposable or reproducible, and commits. When a step fails, diagnose and repair it within the goal rather than handing back.

Ask Philip before: changing what counts as correct or a pass, changing native quality, expanding the target or product claims, deleting valuable or unclassified data, using any other machine (the RTX 5080 is not allocated), extending a time or resource budget, or publishing anything.

- Historical knowledge is indexed in FINDINGS; supporting records use the artifact location documented in docs/results.md.
- This repository has no remote. Do not add one or push.
- Identify owned processes by host, boot and process-birth identity, never PID alone. Do not stop processes you do not own.
- No scheduler, GPU lease service, automatic evidence expiry or autonomous deletion.
- Parallel work uses separate clones or worktrees; worktrees do not isolate the GPU, display, ports or caches.
- Preserve unrelated uncommitted edits. Commit coherent checkpoints with short subjects and no co-author trailers.

## Documents

| Home | Holds |
| --- | --- |
| `AGENTS.md` | Rules for every task. Philip owns it; propose changes |
| `docs/*.md` | How each part works now. Update in the same commit as the code; delete outdated text |
| `README.md` | Project introduction, setup and navigation |
| `FINDINGS.md` | Short index of scoped established conclusions and result links |
| `results/` | Readable investigation results, compact data and reproducible figures; explicit summary corrections |
| `results/sources/` | Frozen records backing findings. Never edited |
| `tasks/README.md` | Task convention |
| `skills/close-results/SKILL.md` | Shared investigation closeout procedure |
| `tasks/NAME.md` | One open multi-session task, one owner. Delete it when the task is done |
| Git history | What changed and when |

Keep current guidance in docs, unfinished work in tasks, and durable knowledge in results. Git is the implementation history. Follow [tasks/README.md](tasks/README.md) for the enforced task template and [docs/results.md](docs/results.md) for result presentation.

Work on a task file only when asked to work on that task. An open task file is context, not an instruction to resume it. A vague request such as "continue the unfinished work" is not a request for any particular task; ask which one is meant.

## Evidence and reporting

- Raw outputs stay outside Git under `~/.local/share/shaderlod-next/evidence`. Keep failed and unfavorable results.
- Done means exercised. Report what was executed, reused from cache and not run, and what remains untested.
- Write plainly: short sentences, no invented labels, scope every claim.
