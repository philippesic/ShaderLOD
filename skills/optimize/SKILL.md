---
name: optimize
description: Investigate or implement ShaderLOD optimization mechanisms from prior findings through scoped validation, comparison and a finished decision. Use for optimization work; ordinary bug fixes and standalone benchmark runs follow their own procedures.
---

# Develop a ShaderLOD optimization

Read [optimization direction](../../docs/optimization-philosophy.md), the relevant
FINDINGS entries and any assigned task. Use [module contracts](../../docs/optimizations.md)
and the relevant architecture when implementation needs them. Keep project-specific
commands and policies in those documents rather than copying them into this skill.

Establish the costly work, the proposed mechanism and why it differs from relevant
previous attempts. Profile when the bottleneck is unknown; existing trustworthy
measurements can already answer that question. Distinguish a reusable mechanism
from its pack binding. Explain the opportunity, applicability, fallback, expected
benefit and likely functional or perceptual harm before committing to a large change.
An exact-source admission check establishes applicability, not value or broadness.

Develop the smallest useful version within the assigned scope. State what actually
changes and what must remain equivalent. Readiness, deferred work, history, settings
and interactions matter when the mechanism affects them. Record consequential
choices and exposure in the current task when work spans sessions; a hypothesis
is not an approved support claim or acceptance threshold.

Use [check selection](../../docs/checks.md) for correctness and the
[benchmark skill](../benchmark/SKILL.md) for a short relevant comparison. Keep native
quality controls and original inputs intact. Validate actual activation and workload
progression. Separate functional validity, perceptual evidence, speed and uncertainty.
A noisy result is unresolved; an inactive path cannot establish a useful optimization.
Investigate contrary results instead of selecting favorable scenes or repeating until
one passes. Reuse an owned healthy session only while its executed inputs match.

When evidence makes the idea worth advancing, freeze the candidate, useful-gain and
harm margins, quality criteria and support envelope before confirmation. Resolve
unsettled product judgments with Philip. Use the benchmark procedure to choose
relevant adverse cases, a different active pack for broader claims, and the actual
enabled combination. Do not run full qualification after every edit or treat a
single-pack screen as a release verdict. Broader engine capabilities or allocations
need their own authorized scope; this skill does not grant them.

Finish the assigned investigation with a scoped decision: retain, revise, discard
or unresolved. Use the pinned [close-results procedure](../close-results/SKILL.md)
when there is a durable lesson, including useful failures. Preserve executed source
and required inputs before authorized pruning. Routine supporting fixes update code,
tests and current docs without separate result pages. Finishing a run alone is not
completion; continue independent authorized work when one part is blocked.
