# Historical measurement tools contain reusable design work with explicit gaps

The old system separated functional checks, conditioned performance comparisons, native controls and untimed work inspection. Its cost-map design distinguishes frame pacing, CPU wall work and asynchronous GPU intervals; these cannot be added into a single frame-time budget. Exact native option maps, source catalogs and planning records are preserved for reuse. The design is not current implementation.

## Additional historical controls (imported 2026-11-11)

| Investigation | Observed conclusion | Consequence for benchmark design |
| --- | --- | --- |
| Two four-phase OFF/ON/OFF/ON sessions, October 4 | All-effect-off controls were nearly flat or changed sign. Neither a large reproducible enablement penalty nor a performance gain was established | Phases within one JVM are correlated; a changed shader archive between sessions is a separate intervention. Frame samples are not independent experimental repetitions |
| Nine original-Photon optional-stack captures, October 5 | Three launches per stack did not establish a consistent benefit from the tested optional optimizer/culling stacks; ShaderLOD's main mod was absent | Hold the mod stack constant for shader comparisons. A terrain-heavy fixed forest does not qualify entity-rich culling behavior |

The first comparison used a clear shoreline without DH and retained missing linked-program/uniform evidence. The second used M3 Max, NeoForge 1.21.1, Photon v1.3b and DH at 1488×836; this is historical evidence, not the current native-resolution contract. The stack analysis required an explicit qualification for differing embedded dependency identities. Neither report independently fingerprinted the loaded development class directories during every capture. Full timing tables, inputs and those limitations remain in the compact source reports below. No historical acceptance thresholds or launch instructions become current policy.

## Conditions and limitations

Evidence level: source-inspected historical import. Archived procedure and design inspection only; proposed profiler overhead budgets were not measured acceptance results. No new measurements were executed. Historical live observations retain their original scope; none qualifies the current runtime.

## Decision

Retain the definitions and lessons. Benchmark design should select and implement the needed pieces, preserving missing coverage and uncertainty.

## Evidence and executed source

[Archived guides and exact native option maps](../sources/historical-guides.json) sha256:f95002fbc25f748753622b87ad733ba419e9494cf46b2ae66e93ad173f77a61e

Full run records, source history, frozen inputs and supporting scripts are retained in [the historical artifact snapshot](/home/philip/.local/share/shaderlod-next/evidence/history/original-repository). See [artifact access](../../docs/results.md#historical-artifact-access) for path mapping, verification and known remote-only evidence.

[Original in-process and optional-stack reports](../sources/early-control-comparisons.json) sha256:a23680d04094fdba1701c9d81d4ce6f627d0a4582fe8203669f3bf6c6f0f4e6b
