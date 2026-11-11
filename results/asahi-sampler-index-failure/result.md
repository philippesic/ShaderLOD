# Before the sampler index correction, the original Reimagined pack failed native linking with DH, and ran out of texture units without DH; settings restoration still succeeded

This records the earlier failure and successful restoration. Read F10 for the corrected no-DH behavior; the old failure must not be treated as the current no-DH result.

## Conditions and limitations

Scope: This repository, Apple M1, 2560×1600, native settings. Evidence level: live-functional. The no-DH failure is fixed by the sampler index correction (F10); no performance result. This is a historical evidence import; no new experiment was executed.

## Decision

Unresolved. Keep the knowledge; this does not authorize reviving an implementation or broaden the tested scope.

## Evidence and source

[asahi-native-failure.json](../sources/asahi-native-failure.json) sha256:c6a2151e91d871346e9d6e586ba2721e731144530d0705f5b597c5fa53144c7e

Executed-source identity: follow the original investigation records linked above. This summary does not establish a recoverable source snapshot; verify it before reproducing or deleting dependencies.
