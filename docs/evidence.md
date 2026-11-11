# Evidence and reporting

## Levels

Every finding carries one level. A higher level is never implied by a lower one.

| Level | Means |
| --- | --- |
| source-inspected | Source or a historical record was read; nothing new was executed |
| cpu-synthetic | A synthetic contract or model ran without live inputs |
| cpu-integration | Real CPU components ran together; no live rendering |
| live-functional | Runtime receipts prove the stated function on the stated machine only |
| live-performance-qualified | Matching correctness, coverage, health and calibrated comparison evidence |

Compilation is not a level. A live level needs a runtime receipt. Missing data stays missing with a reason; it is never zero. Health is PASS or FAIL. No level admits an optimization by itself. The acceptance bar for keeping an optimization is Philip's wording, "proven generally positive"; its exact meaning is part of [benchmark design](../tasks/benchmark-design.md).

## Where evidence lives

- Raw outputs (logs, captures, PNGs, receipts) stay outside Git under `~/.local/share/shaderlod-next/evidence/`. `./verify --runtime` writes each attempt's receipt and log to `attempts/` there by default and refuses receipt paths inside the repository. Older bundles sit in directories named by their manifest digest.
- `build/`, `run-client*/` profiles and other ignored repository directories are working copies. Before reclaiming them, preserve any unclassified or finding-dependent output. Anything a finding depends on must be under the evidence root, not there.
- `evidence/*.json` holds compact, dated records that back a finding. They are never edited after the finding cites them; a correction is a new record.
- `FINDINGS.md` is a short question/conclusion/result map. Scope belongs in the conclusion.
- `results/<id>/result.md` explains each meaningful investigation. Keep supporting compact numerical data and figure-generation inputs beside it; raw logs stay behind evidence links.

## Reporting

- Report what was executed, what was reused from cache and what was not run. Skipped is not passed; `PARTIAL` is not full.
- Keep failed, blocked and unfavorable results. A failure that was later worked around stays in the record.
- Scope every claim to the machine, pack, settings and DH state it ran on. A CPU pass says nothing about live behavior; a live-functional pass says nothing about performance.
- Label causes as confirmed, supported hypothesis or unknown.

## Result presentation and closeout

Use the shared `investigation-closeout` skill; its tracked source is `skills/investigation-closeout/SKILL.md`. This document is the project specification it reads.

A result starts with a finding-led title and a short explanation of what happened and why it matters. Include a useful chart for measurements or a small table for functional outcomes. State tested conditions, limitations, evidence level, and the decision: retain, revise, discard or unresolved. Separate observations from causes and from acceptance decisions. An unresolved result names what evidence would resolve it. Failed investigations receive the same treatment as successful ones. Do not create a result for every routine check.

Figures must name quantities and units, show controls and uncertainty when available, and distinguish independent runs from samples within a run. Keep numerical data and executable regeneration instructions. Do not invent error bars or interpret missing observations as zero. Figures explain the result; logs are supporting material.

Link frozen records with SHA-256 identities and the executed source, including any dirty patch or snapshot. Mark unavailable provenance explicitly; it prevents reproducibility claims and deletion of potentially necessary inputs, not publication of an honestly limited historical summary.

Raw records remain immutable. A summary correction records the date, changed claim and reason in the result; materially new investigations get new results. Update FINDINGS to the current scoped conclusion without hiding relevant prior results.

Before removing rejected implementation, verify that source and necessary dependencies are recoverable. Preserve reusable code and regression tests only when they still serve the project. Apply authorized cleanup and validate the remaining project with ./check --full before closing its task. Disk-space policy belongs to agent ops; closeout grants no new deletion or publication authority.

Historical imports identify what was inspected, do not relabel old measurements as new runs, and disclose missing source or inputs. Completed result pages may coexist with an unresolved scientific question; keep a task open only when further work is assigned.
