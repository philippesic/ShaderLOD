# Results and closeout

## Levels

Every finding carries one level. A higher level is never implied by a lower one.

| Level | Means |
| --- | --- |
| source-inspected | Source or a historical record was read; nothing new was executed |
| cpu-synthetic | A synthetic contract or model ran without live inputs |
| cpu-integration | Real CPU components ran together; no live rendering |
| live-functional | Runtime receipts prove the stated function on the stated machine only |
| live-performance-qualified | Matching correctness, coverage, health and calibrated comparison evidence |

Compilation is not a level. A live level needs a runtime receipt. Missing data stays missing with a reason; it is never zero. Health is PASS or FAIL. No level admits an optimization by itself. The acceptance bar for keeping an optimization is Philip's wording, "proven generally positive"; each contribution freezes its useful gain, tolerated harm and perceptual criteria through the [benchmark protocol](benchmark-definitions.md).

## Where evidence lives

- Working raw outputs (logs, captures, PNGs, receipts) stay outside Git under `~/.local/share/shaderlod/evidence/`. `./verify --runtime` writes each attempt's receipt and log to `attempts/` there by default and refuses receipt paths inside the repository. Older bundles sit in directories named by their manifest digest.
- `build/`, `run-client*/` profiles and other ignored repository directories are working copies. Before reclaiming them, preserve any unclassified or finding-dependent output. Retained compact evidence belongs in results/; raw inputs still needed during an investigation belong under the external evidence root, not in disposable build/profile trees.
- `results/sources/` holds compact, dated records and archived reports that back a finding. They are never edited after the finding cites them; a correction is a new record.
- `FINDINGS.md` is a short question/conclusion/result map. Scope belongs in the conclusion.
- `results/<descriptive-name>/result.md` explains each meaningful investigation. Keep supporting compact numerical data and figure-generation inputs beside it; temporary raw logs stay external until verified extraction and cleanup.

## Reporting

- Report what was executed, what was reused from cache and what was not run. Skipped is not passed; `PARTIAL` is not full.
- Keep failed, blocked and unfavorable results. A failure that was later worked around stays in the record.
- Scope every claim to the machine, pack, settings and DH state it ran on. A CPU pass says nothing about live behavior; a live-functional pass says nothing about performance.
- Label causes as confirmed, supported hypothesis or unknown.

## Result presentation and closeout

Use the tracked [close-results procedure](../skills/close-results/SKILL.md).
Its canonical shared source is p-stack `policy/skills/close-results/SKILL.md`, pinned
here to revision `0a8b4561f5af1cb7adc6238c0e171d38e0298c4c`. This document owns the
project's result format, validation and cleanup boundaries; shared procedure changes
do not set ShaderLOD acceptance criteria.

To update the pinned copy, compare the canonical revision with the tracked skill,
review it against this specification, copy the approved bytes and update this
revision together. Validate the skill and local links, run the project gate and
commit both files. Keep shared improvements in p-stack rather than editing two
independent procedures. Global p-stack sync does not update this project copy.

A result starts with a finding-led title and a short explanation of what happened and why it matters. Include a useful chart for measurements or a small table for functional outcomes. State tested conditions, limitations, evidence level, and the decision: retain, revise, discard or unresolved. Separate observations from causes and from acceptance decisions. An unresolved result names what evidence would resolve it. Failed investigations receive the same treatment as successful ones. Do not create a result for every routine check.

Figures must name quantities and units, show controls and uncertainty when available, and distinguish independent runs from samples within a run. Keep numerical data and executable regeneration instructions. Do not invent error bars or interpret missing observations as zero. Figures explain the result; logs are supporting material.

Identify the executed source, including any relevant dirty patch, dependency versions and tested inputs. Keep compact measurements with SHA-256 identities sufficient to check the conclusions and regenerate figures. Preserve failures, exclusions and missingness in that retained data. Mark unavailable provenance explicitly and limit reproducibility claims accordingly.

Raw records remain immutable while retained; this does not require permanent retention of every output. Closeout extracts useful measurements, conditions, limitations and conclusions into results/, verifies the retained evidence against the raw attempt, and then deletes superseded bulky outputs within the investigation's authorized scope. Keep only inputs or records still necessary to support the claim and not adequately represented by the compact result. Logs, captures, caches and frozen environments are not retained merely because they were produced. Do not delete active/shared work or select away unfavorable evidence. Record what was discarded and any resulting reproduction limits. Existing historical archives need this extraction and verification before deletion; this policy is not blanket permission to erase them.

A summary correction records the date, changed claim and reason in the result; materially new investigations get new results. Update FINDINGS to the current scoped conclusion without hiding relevant prior results.

Before removing rejected implementation, verify that source and necessary dependency identities are recoverable. Preserve reusable code and regression tests only when they still serve the project. Apply authorized cleanup and validate the remaining project with ./check --full before closing its task. Closeout includes extracting evidence and removing superseded outputs; it grants no unrelated deletion or publication authority.

Historical imports identify what was inspected, do not relabel old measurements as new runs, and disclose missing source or inputs. Completed result pages may coexist with an unresolved scientific question; keep a task open only when further work is assigned.

## Common document roles and lifecycle

AGENTS.md holds rules every agent needs; README.md introduces and navigates the project. Current architecture belongs in docs/architecture.md and development procedures in docs/development.md. Other docs remain current topic guides. Never accumulate obsolete instructions or commit diaries in docs.

Use one descriptive result folder per coherent question or investigation, not per run, commit or session. Basic result sections are: conclusion-led title and explanation; useful chart or table when warranted; conditions and limitations; decision; evidence and executed-source references. Optional result.json holds structured facts or figure inputs. Optional figures and small tables stay beside result.md. Small supporting reports belong in results/sources/. Large execution records are temporary external working material unless a specific result requires their continued retention.

FINDINGS contains a short scoped conclusion and link for each result. Group by topic when helpful. Negative and inconclusive results are established knowledge about what happened, not proof of universal impossibility. Avoid copying full writeups into the index.

Open task → investigate → write result if warranted → update current docs → prune obsolete code within scope → update FINDINGS → close task. Tasks follow tasks/README.md and are deleted when complete. A routine fix updates code, tests and current docs; create a result only for durable knowledge.

## Historical artifact access

Historical knowledge and compact supporting evidence live in this repository. Start with FINDINGS and the [historical catalog](../results/historical-evidence-extraction/catalog.md); its structured index maps original paths and input hashes to retained reports, per-run statistics, conditions and source records. Old paths embedded inside immutable source records are provenance labels, not instructions to open the old checkout or archive. Do not treat obsolete allocations, acceptance decisions or launch commands inside them as current policy.

The [extraction result](../results/historical-evidence-extraction/result.md) records coverage and intentional omissions. The old working checkout, its duplicate historical snapshot and earlier TAR/ZIP archives are not dependencies of these retained findings. Full-resolution captures, copied worlds, repeated traces, compiled binaries and frozen environments are intentionally unnecessary for the retained claims. Prior visual observations remain historical reports, not fresh independent image-quality proof. Previously missing Mac/Windows inputs remain missing; no exact historical render replay is promised.

The [source-recovery map](../results/historical-evidence-extraction/source-recovery.json) identifies authored source in main's Git history or compact retained source records, including dirty variants that were absent from published history. Use a full Git clone for history-backed source recovery. Validate compact evidence with `python3 results/historical-evidence-extraction/verify.py`; this is an offline evidence check, not a new benchmark or live qualification.

The active artifact directory is `~/.local/share/shaderlod/evidence`. The old `~/.local/share/shaderlod-next` data path is a compatibility symlink to `~/.local/share/shaderlod`, so immutable receipts and recovery records remain accessible at their recorded locations. Use the canonical name for new work. Historical source checkout paths inside records remain provenance labels; do not recreate or use a second `ShaderLOD-next` checkout.

The user-approved coauthor-trailer removal changed 31 newer commit IDs without changing their source trees. [Commit identity mapping](../results/sources/commit-id-map.json) resolves earlier citations to equivalent rewritten commits; original execution records retain their recorded identities. The mapping identifies the small Git recovery bundle for original commit metadata. The older repository's published branch tips were not rewritten.
