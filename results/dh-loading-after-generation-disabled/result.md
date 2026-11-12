# Turning DH generation off was restored correctly, but leftover loading kept the scene from reaching a steady state

Changing the generation setting did not remove already pending work. Future steady-scene tests must observe remaining loading instead of assuming the setting guarantees stability.

## Conditions and limitations

Scope: Asahi run-08, original repository. Evidence level: source-inspected historical import of originally live observations; not an intact current runtime receipt bundle. Negative premise check only. This is a historical evidence import; no new experiment was executed.

## Decision

Unresolved. Keep the knowledge; this does not authorize reviving an implementation or broaden the tested scope.

## Evidence and source

[RESULTS.json retained record](../sources/history/cost-map-coverage-20261108-03.json) sha256:e57c1e89d8907c2bf0d956b8deefafc5936da91dc2034c8946a6f171d2ac48d2

Executed-source identities remain in the retained reports. Authored frozen-source variants are recoverable through the [source map](../historical-evidence-extraction/source-recovery.json), using main Git history or compact source records. This does not promise a replay of the old driver, world or execution environment.
