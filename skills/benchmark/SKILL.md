---
name: benchmark
description: Run ShaderLOD live correctness checks, short comparisons, profiling or frozen qualification through the maintained repository workflows. Use for measurement work; ordinary portable checks do not require this skill.
---

Read `docs/benchmarks.md` for commands, ownership and recovery. Read `docs/profiling.md` for diagnostics/mappings or `docs/benchmark-definitions.md` when authoring a protocol. For optimization work, also read `docs/optimization-philosophy.md` and search FINDINGS for relevant prior results.

Choose the workflow by the decision: portable checks for edits; live validation for lifecycle correctness; a short paired comparison for development; profiling to explain variation; frozen qualification for retention/release evidence. Use `python3 -m shaderlod_host`, its tracked definitions and its planner. Repair harness defects in the harness. Do not substitute a custom timing wrapper or infer loaded behavior from a build, file hash or producer PASS alone.

Reuse an owned ready client/world for development. Fresh independent JVMs belong in qualification, not every edit. Keep native output/AA and original packs intact. Observe actual activation, source/generation joins, scene progression, context, failures and restoration. Do not hide candidate non-settling or loading behind unbounded warmup.

Freeze margins, primary outcome, cases, exposure, order, repetitions/stopping and budgets before confirmation. A first-pack result does not grant broad eligibility. Test the actual enabled combination and relevant adverse/reachable configurations. Missing DH rendering, PBR material use, secondary environments or perceptual review remain missing; flags and installed artifacts are insufficient witnesses.

Read structured results first, then the raw/timeline evidence relevant to the question. Frames within a JVM are dependent. An inconclusive interval is not equivalence. A complete capture is not qualified performance evidence. Diagnostic spans are shared intervals, not invented per-effect costs; unknown packs remain usable without semantic labels.

After a submitted command times out, reconcile its eventual outcome; never replay uncertain work. Recover only an identified owned session/state. Preserve failed attempts and partial chunks. Use explicit campaign pause/resume only at completed-session boundaries; unknown admitted work refuses replay. Close the client and verify `closed.json` and journals. Keep raw outputs external, and use the project result/closeout convention for durable lessons. This skill grants no additional machine allocation, budget, deletion or publication authority.
