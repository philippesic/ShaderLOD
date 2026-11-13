# Additional work observation did not explain away the RTX regressions

Native work and explicit GL-context diagnostics did not establish a cause for the observed regressions or prove implementations hardware-optimal. G06 intentionally used a full-size target with half viewport; its allocation was not automatically a resize bug.



## Conditions and limitations

Evidence level: source-inspected historical import. The exact historical runtime, conditions, failed attempts and limitations are in the preserved source record. No historical allocation or pending instruction authorizes new work. No new measurements were executed. Historical live observations retain their original scope; none qualifies the current runtime.

## Decision

Retain the observations; unresolved bottlenecks require targeted profiling instead of implementation speculation.

2026-11-12 addition, from the retained campaign tables: on Reimagined, Unbound and BSL, 22 of the 23 measured shader-rewriting cells on RTX 5080 were slower than the original, 18 of them by more than 5%, including candidates that only remove work (G02, G03, N04). The same candidates stayed within about 4% of zero on MakeUp, and the two candidates the records label as native-path changes (N01, C02) showed no such loss. The diagnosed runs counted identical draw and pass events in both arms. The retained RTX 5080 controls compare the original against itself; none passes an unchanged pack through the rewriting path. The data therefore cannot separate "these mechanisms cost more than they save" from "applying a rewrite to these packs carries a shared cost". The current identity provider can supply that missing control. Reason for the addition: the pattern across candidates was not stated, and it bears on whether those mechanisms were rejected or their application path was.

## Evidence and executed source

[rtx5080-regression-diagnosis-source.json](../sources/rtx5080-regression-diagnosis-source.json) sha256:1a41f81fcdeb05874bc00412ad377d2ebf03eb3f536553aac620ca2f8d766aa2

Compact source reports and retained measurements are in the [historical evidence catalog](../historical-evidence-extraction/catalog.md). Original paths inside those records are provenance labels, not file dependencies. See [historical evidence access](../../docs/results.md#historical-artifact-access) for source recovery and intentional limits.
