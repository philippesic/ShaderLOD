# The same composite1 sampler failure happens with ShaderLOD absent, so ShaderLOD does not cause it

The negative control places the observed failure outside a requirement for ShaderLOD to be present. It does not identify the full underlying cause or prove every other configuration behaves the same way.

## Conditions and limitations

Scope: Pinned Iris, Sodium, DH and Reimagined on Asahi, 2560×1600. Evidence level: live-functional. Negative control only. This is a historical evidence import; no new experiment was executed.

## Decision

Unresolved. Keep the knowledge; this does not authorize reviving an implementation or broaden the tested scope.

## Evidence and source

[asahi-native-control.json](../sources/asahi-native-control.json) sha256:9641bdb8edb43606e5d1a90c388003a5a631f3d341864faa014c4c9493b0f66b

Executed-source identities remain in the retained reports. Authored frozen-source variants are recoverable through the [source map](../historical-evidence-extraction/source-recovery.json), using main Git history or compact source records. This does not promise a replay of the old driver, world or execution environment.
