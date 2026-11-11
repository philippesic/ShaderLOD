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
- `build/`, `run-client*/` profiles and other ignored repository directories are disposable working copies. Anything a finding depends on must be under the evidence root, not there.
- `evidence/*.json` holds compact, dated records that back a finding. They are never edited after the finding cites them; a correction is a new record.
- `FINDINGS.md` cites each record by path and `sha256:` digest.

## Reporting

- Report what was executed, what was reused from cache and what was not run. Skipped is not passed; `PARTIAL` is not full.
- Keep failed, blocked and unfavorable results. A failure that was later worked around stays in the record.
- Scope every claim to the machine, pack, settings and DH state it ran on. A CPU pass says nothing about live behavior; a live-functional pass says nothing about performance.
- Label causes as confirmed, supported hypothesis or unknown.
