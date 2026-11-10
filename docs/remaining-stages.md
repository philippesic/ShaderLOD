# Remaining port stages

This is the consolidated plan for the work that remains after stage 0, the non-source foundation already in this repository at the former path `/home/philip/shaderlod-next`. The remaining stages are source port, benchmark, GPU qualification, and skills. Nothing advances a stage without Philip's explicit go. This document is planning only; it does not authorize a source port, client launch, benchmark, or hardware run.

The stage-1 plan used its own numbers: stage 3 is source, 4a is the harness, 4b is real verify, and 4c is qualification. Those map onto Philip's names below. Skills were left out of scope there as well.

## Sources

- `/home/philip/.t3/scratch/2026-11-10-shaderlod-migration-stage-1-read-d4cf15a1/PORTING_PLAN.md` on Asahi (philip-macbookair), 551 lines, prepared 9 November 2026. Cited as the stage-1 plan.
- ShaderLOD foundation proposal Page: https://cursor.com/canvas/store-2a326036-ee6e-460b-a22f-a5bffbf66171/86dc4e44-31dd-47f0-984f-b55c1ef9266c
- `/home/philip/.t3/scratch/2026-11-10-shaderlod-migration-stage-2-build-94c75c59/FOUNDATION_REPORT.md`
- This repository's `README.md`, `FINDINGS.md`, `docs/SYSTEM_MAP.md`, `docs/checks.md`, `docs/evidence-levels.md`, and `docs/decisions/`
- `/workspace/research/migration/BRIEF.md`

## Current state

Stage 0 is complete: a local repo with no remote, no mod classes, no game or GPU work, `./check` as the one CPU command, `./verify --cpu` for the synthetic contract path, and `./verify --runtime` exiting 2 until stage 4. The four future targets are MakeUp 9.5g, BSL 10.1.8, Complementary Reimagined r5.9.3, and Unbound r5.9.3. Distant Horizons 3.3.3 is optional at the later runtime stage. Philip's acceptance wording is "proven generally positive." None of the sixteen old audit candidates is admitted.

The plan estimated foundation at 4–8.5 engineer-days, source at 5–10, and harness, verify, and qualification at 6–14.

## Source port

### Scope

Port one piece at a time, each with a CPU parity receipt, then an explicit go before the next piece (plan section 4 and stage 3):

- P1: immutable settings, distance, and effect contracts.
- P2: exact pack and source identity for the four originals.
- P3: minimal config plus NeoForge and Iris lifecycle, only essential mixins; strip parked uniform registrations.
- P4: native selection transaction and minimal Apply/Cancel UI.
- P5: read-only DH state and readiness adapter. No geometry or shadow optimizations.
- P6: dependency-neutral profiler contracts, then the sink adapter. Old UNKNOWN fixtures stay diagnostic, never qualified.

The first pack provider is the clean native MakeUp reference. Comments are stripped before import; useful math moves to decisions. Live hook and output parity wait for verify.

What is not ported: parked optimizations, auto-tuning, shader mutations, terrain or shadow reductions, Fabric or other-target trees, the lowercase snapshot, experiment outputs, or a "runtime done" claim.

### Entry criteria

Philip's go after stage 0. The plan's first-product recommendation is native pack selection, config, and Iris bridge plus the dev profiler, with the listed exclusions. Confirm that surface before the first piece if it is still open.

### Exit evidence and gates

For each piece: `./check` plus a CPU parity receipt plus the artifact boundary check. Evidence label at most CPU-scoped; never called runtime-complete. Old tests must first be characterized on a copy of the frozen source, not by writing into either checkout. Optional hardware tests are `NOT_RUN_REQUIRES_GPU`, never counted as passing CPU tests. Missing JDK or pack ZIP must fail with an acquisition remedy, not skip.

Plan effort: P1 and P2 0.5–1 day each; P3 and P4 1–2 days each; P5 1–2 days; P6 1–2 days.

### Risks

Comment stripping can change behavior if done naively, including Python `__doc__` consumers and shader preprocessor lines. Optional DH class loading must stay isolated. Copied optimization JSON can reactivate absent paths and must be checked in assembled JARs, not only imports.

## Benchmark

### Scope

Stage 4a in the plan: one runner, lifecycle, and publisher; workload, scene, and scope providers; identity and noise analyzer; coverage and health schema; legacy read-only imports; CPU verify. The sole public entry is `python3 scripts/run_benchmark.py` with modes `plan`, `correctness`, `baseline`, `compare`, `profile`, `report`, and `recover`. `./verify --runtime` delegates its smoke to that correctness engine and is not a second launcher.

What is in: one writer for finalized result records; workloads that prove their own premise; no `--force`; correctness kept separate from timing; no GPU lock system, only a refusal if a client is already running.

What is not in: resurrecting dozens of `run_*` campaign callers; pooling mismatched DH, display, or pack identities; treating a synthetic fixture as live; default-enabling any candidate.

### Entry criteria

Philip's go after the source pieces are CPU-accepted. Then a later, separate go is required before bounded real verification, with a concrete matrix and storage budget.

### Exit evidence and gates

All selected old parity plus new noise, missingness, health, and refusal specimens pass on CPU. Real runtime verification remains pending. Seeded run order is a required field in the stage-4 workload schema (Dune item 6e, deferred from foundation).

Plan effort: 3–6 days for 4a.

### Risks

The closed-lid Bliss/native463 controls spanned −9.70% to +10.24%, with process means rising 76.65 to 100.69 ms (+31.36%) and four failed stability gates. That is a specific failed calibration, not a universal ±10% bar. A 3% gain cannot be accepted in a cohort with that control drift. Six to 24 null blocks per cohort are a proposed calibration starting allocation, not an authorized run.

## GPU qualification

### Scope

Stage 4b: real system verify, starting with one original MakeUp scene, then BSL, no-DH and DH state, reload and restore, and controlled failures; an approved framebuffer oracle; Complementary DH link failures kept honest.

Stage 4c: observer overhead, per-scope cadence feasibility, repeated controls, noise and MDE, and the four-pack, two-machine coverage in separate cohorts. No optimizations resurrected.

What is not in: lowering sampler settings to get Complementary or Unbound linking; pooling Asahi time with RTX time; calling live-functional work performance-qualified; using old artifacts to qualify new binaries or drivers.

### Entry criteria

Philip's go after 4a, with a concrete matrix, display and power setup, and storage budget. At plan time about 33 GiB were free, near the old 32 GiB reserve plus save-restoration headroom. A real native framebuffer or scene oracle must be accepted before verify can certify output.

### Exit evidence and gates

- `./check` plus the applicable `./verify --runtime` receipt, exact artifacts, and health. Unknown health is a fail for qualification.
- Sparse coverage cannot be treated as a full breakdown: the inspected MakeUp capture had GPU results on 39 of 162 complete intervals (24.07%), 43 unresolved queries, and one imbalanced scope. Proposed design thresholds, to calibrate later: at least 100 valid observations per required scope and at least 95% valid completion of scheduled opportunities. These are not proven sufficient.
- Retention still requires the approved entire target matrix. Blocked or missing cells are not positive. Historic retention advice is dated evidence, not a new selection.
- Reimagined and Unbound failed DH-enabled linking on this Asahi runtime with "Too many fragment shader texture samplers." Those remain negative fixtures.

Plan effort: 1–3 engineering days plus setup for 4b; 2–5 engineering days plus approved hardware time for 4c, extendable if variance or blockers persist.

### Risks

Display and power setup on Asahi is unsettled; closed-lid headless timings did not qualify. Driver or pack changes create new identities. Disk estimates must include downloads, golden saves, fail and recovery copies, and report duplication.

## Skills

Out of scope. The stage-1 plan gives no inventory, entry criterion, exit gate, or estimate. A later request is required.

## Open questions awaiting Philip

From the plan's section 11.3 and the foundation proposal. Some location questions are partly settled by the existence of this repo; the rest remain open.

1. Permanent remote and license. The directory is being renamed to `ShaderLOD-next` to match the GitHub remote `philippesic/ShaderLOD`. That does not choose a remote for this repo.
2. Confirm the minimal first product surface recommended above.
3. Generality matrix: which DH, scenes, settings, and PBR cells are mandatory, plus the practical effect target and precision budgets. A smaller diagnostic matrix cannot produce general retention. The exact meaning of "generally positive" is to be settled at the benchmark stage; the plan still warns against rewriting it as "every pack on both machines" without Philip.
4. A stable allowed display and power setup on Asahi.
5. Where frozen ignored evidence and fixtures remain backed up, and how much disk can be allocated. The lowercase copy should stay frozen until its 1,017 unique ignored files are backed up; the plan authorizes no deletion.
6. Pins that were completed in stage 0 no longer block foundation; driver changes remain explicit cohorts.
7. The real native framebuffer or scene correctness oracle.
8. How to treat Complementary DH linking limits on future hardware; do not relax samplers as an unreviewed workaround.
