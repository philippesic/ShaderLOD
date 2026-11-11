---
name: close-results
description: Close a meaningful investigation by verifying claims, preserving reproducible evidence, writing its result and knowledge index, assessing retained code, and validating cleanup. Use for requested investigation closeout, not routine edits or merely finishing a run.
---

# Close results

Read project instructions and locate the tracked result specification they name (normally docs/results.md). It owns presentation, figure conventions, evidence locations and cleanup rules. If none exists, propose a minimal format appropriate to the project; do not impose another project's research format.

Trace each substantive claim to executed evidence and identify scope, failures, uncertainty and missing provenance. Separate observations, causal hypotheses and disposition. Apply agreed acceptance criteria; do not invent them. A completed run is not an investigation closeout.

Produce a compact result in the project's format. Preserve numerical figure data and regeneration inputs. Record executed source identity, including uncommitted changes, and required dependencies. Historical imports remain historical. Missing provenance must be explicit and prevents unsupported reproducibility claims or destructive cleanup.

Update the short project knowledge map to the current scoped conclusion. Raw execution records stay immutable. Correct summaries explicitly; create a new result for a materially new investigation, keeping useful prior links.

Assess implementation disposition against the assigned decision. Before removing discarded code, verify recoverable source and necessary inputs. Retain shared tools or tests only when useful. Perform only authorized cleanup and never infer deletion permission from age or an ignore rule.

Run the project's required validation after cleanup. Close the assigned task only when the result, knowledge map, source preservation and validation are complete; record concrete unresolved work otherwise. A scientific conclusion may remain unresolved even when the assigned investigation is complete. Report what was executed, reused and not tested. Follow project commit/publication rules without granting new authority.


Use the common roles: AGENTS.md for rules, README.md for setup/navigation, docs/architecture.md for current architecture, docs/development.md for working procedures, docs/results.md for project presentation, tasks/README.md for unfinished-work conventions, FINDINGS.md for a short conclusion/link index, and descriptive results/<name>/ folders for durable knowledge. Keep result.json, charts and supporting assets conditional on usefulness; archived small reports may live in results/sources/. Never create one result per routine commit or session.

Follow the lifecycle: open task → investigate → write result if warranted → update current docs → prune obsolete code within scope → update FINDINGS → close task. Remove obsolete current guidance, and delete completed task files; Git preserves implementation and task history. Do not modify unrelated projects merely to impose this layout.
