# Reducing redundant reloads improved the workflow without proving shader speedups

The maintained runner reused readiness and consolidated private selection/observation reloads while preserving profile restoration. Before/after workflow observations were individual operational checks, not statistical performance claims. Fresh-process isolation and inexpensive backup protections remained justified.



## Conditions and limitations

Evidence level: source-inspected historical import. The exact historical runtime, conditions, failed attempts and limitations are in the preserved source record. No historical allocation or pending instruction authorizes new work. No new measurements were executed. Historical live observations retain their original scope; none qualifies the current runtime.

## Decision

Retain the workflow lessons; implement only capabilities needed by the new runner, with its own tests.

## Evidence and executed source

[workflow-repair-source.json](../sources/workflow-repair-source.json) sha256:a457f551abb8f598a2512e145bc9c728b95871015d80f345a4ea10d6240529be

Full run records, source history, frozen inputs and supporting scripts are retained in [the historical artifact snapshot](/home/philip/.local/share/shaderlod-next/evidence/history/original-repository). See [artifact access](../../docs/results.md#historical-artifact-access) for path mapping, verification and known remote-only evidence.
