# Turning DH generation off was restored correctly, but leftover loading kept the scene from reaching a steady state

Changing the generation setting did not remove already pending work. Future steady-scene tests must observe remaining loading instead of assuming the setting guarantees stability.

## Conditions and limitations

Scope: Asahi run-08, original repository. Evidence level: live-functional. Negative premise check only. This is a historical evidence import; no new experiment was executed.

## Decision

Unresolved. Keep the knowledge; this does not authorize reviving an implementation or broaden the tested scope.

## Evidence and source

[RESULTS.json](/home/philip/.local/share/shaderlod-next/evidence/history/original-repository/dev/experiments/cost-map-coverage-20261108/RESULTS.json) sha256:e57c1e89d8907c2bf0d956b8deefafc5936da91dc2034c8946a6f171d2ac48d2

Executed-source identity: the complete historical source checkout and run freezes are preserved in the artifact snapshot. Use the original build bindings to identify the executed revision; a current checkout alone does not prove a historical run used it. See [artifact access](../../docs/results.md#historical-artifact-access).
