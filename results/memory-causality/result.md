# Memory and temperature observations did not establish the cause of timing drift

Recorded temperature was battery temperature, not CPU/GPU temperature. Paging and client faults occurred without timed GC collections in the inspected diagnostic. A later idle snapshot cannot reconstruct interference during earlier measurements.



## Conditions and limitations

Evidence level: source-inspected historical import. The exact historical runtime, conditions, failed attempts and limitations are in the preserved source record. No historical allocation or pending instruction authorizes new work. No new measurements were executed. Historical live observations retain their original scope; none qualifies the current runtime.

## Decision

Retain the diagnostic limits; do not blame thermal throttling, agents or memory pressure without a controlled causal test.

## Evidence and executed source

[memory-causality-source.json](../sources/memory-causality-source.json) sha256:a46d016a7a1726c8662dd297d12daa207bd4d3c0dceab935d236ad8950e4dc34

Compact source reports and retained measurements are in the [historical evidence catalog](../historical-evidence-extraction/catalog.md). Original paths inside those records are provenance labels, not file dependencies. See [historical evidence access](../../docs/results.md#historical-artifact-access) for source recovery and intentional limits.
