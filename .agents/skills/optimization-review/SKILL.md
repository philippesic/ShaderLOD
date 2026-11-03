---
name: optimization-review
description: Iteratively optimize ShaderLOD features or candidates through testing and independent review when the user requests an optimization review loop.
---

# Optimization review

Use this skill only when the user explicitly invokes it or requests an optimization and independent review loop. The user's prompt defines the objective, scope and any specific completion criteria.

## Working agent

Optimize the feature iteratively: investigate relevant history, develop and refine viable approaches, and use the maintained benchmark to evaluate them. Use the evidence to decide what to improve or try next.

Continue while a feasible, evidence-backed approach could materially improve the result. Choose each next iteration to address a concrete uncertainty or bottleneck. Do not invent speculative work merely to keep going. An inconclusive result or failed attempt alone does not close an optimization.

Before closing an underlying optimization, explain why the objective is met or no feasible, evidence-backed next step remains within scope. Keep blockers scoped to the affected work.

Record meaningful outcomes and their limitations in `docs/EXPERIMENTS.md`.

## Reviewer agent

When you believe the work is ready to close, launch an independent Astra/medium reviewer using `.codex/agents/shaderlod_reviewer.toml`.

If the runtime cannot select that role directly, include its complete `developer_instructions` verbatim in the review task. Select the model and effort explicitly; report a blocker if they or the instructions are unavailable.

Give the reviewer the user's objective, the changes, the evidence and the reason for stopping. Ask it to assess the stopping rationale. Follow up on material findings, retest as needed, and request a fresh review of changed work. Repeat until the reviewer supports closure or a real blocker prevents progress.

Report the outcome, remaining limitations and any blockers. Distinguish a conclusion about a tested approach from closure of the underlying optimization.
