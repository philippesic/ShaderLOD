# Additional work observation did not explain away the RTX regressions

Native work and explicit GL-context diagnostics did not establish a cause for the observed regressions or prove implementations hardware-optimal. G06 intentionally used a full-size target with half viewport; its allocation was not automatically a resize bug.



## Conditions and limitations

Evidence level: source-inspected historical import. The exact historical runtime, conditions, failed attempts and limitations are in the preserved source record. No historical allocation or pending instruction authorizes new work. No new measurements were executed. Historical live observations retain their original scope; none qualifies the current runtime.

## Decision

Retain the observations; unresolved bottlenecks require targeted profiling instead of implementation speculation.

## Evidence and executed source

[rtx5080-regression-diagnosis-source.json](../sources/rtx5080-regression-diagnosis-source.json) sha256:1a41f81fcdeb05874bc00412ad377d2ebf03eb3f536553aac620ca2f8d766aa2

Full run records, source history, frozen inputs and supporting scripts are retained in [the historical artifact snapshot](/home/philip/.local/share/shaderlod-next/evidence/history/original-repository). See [artifact access](../../docs/results.md#historical-artifact-access) for path mapping, verification and known remote-only evidence.
