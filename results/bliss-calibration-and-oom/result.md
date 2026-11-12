# Bliss qualification stopped at calibration and memory failures

The unchanged-shader calibration failed or remained incomplete across successive protocols. Atomic reload and lower-heap diagnostics exercised repairs but did not prove memory exhaustion fixed. No candidate timing blocks were qualified; partial windows from interrupted processes are not speed evidence.



## Conditions and limitations

Evidence level: source-inspected historical import. The exact historical runtime, conditions, failed attempts and limitations are in the preserved source record. No historical allocation or pending instruction authorizes new work. No new measurements were executed. Historical live observations retain their original scope; none qualifies the current runtime.

## Decision

Revise the measurement environment and validate it before scoring optimizations. Cause attribution remains unresolved.

## Evidence and executed source

[bliss-calibration-and-oom-source.json](../sources/bliss-calibration-and-oom-source.json) sha256:f49abfd18486ecc32bd974fa775c43ac5ac92e9a8e17f9b273d35503089b0012

Compact source reports and retained measurements are in the [historical evidence catalog](../historical-evidence-extraction/catalog.md). Original paths inside those records are provenance labels, not file dependencies. See [historical evidence access](../../docs/results.md#historical-artifact-access) for source recovery and intentional limits.
