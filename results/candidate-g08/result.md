# G08: Cloud occupancy / lighting reuse did not qualify for retention

This candidate was tested or explicitly held across the four-pack screen. A held cell means the implementation lacked the required applicability or correctness proof; it is not a measured zero or a hardware speed result. The final retention decision required positive results across all four shaders and both completed machines. No candidate met it.

| Shader | Asahi disposition | RTX 5080 disposition |
| --- | --- | --- |
| makeup | NO_GAIN_ESTABLISHED | MEASURED |
| reimagined | MEASURED_SLOWER | MEASURED |
| unbound | NO_GAIN_ESTABLISHED | MEASURED |
| bsl | NO_MAINTAINED_GENERAL_ROUTE | HELD_NO_QUALIFIED_GENERAL_ROUTE |

## Conditions and limitations

Evidence level: source-inspected historical import. Asahi at native 2560×1600 and RTX 5080 native Windows at 3840×2160, pinned original settings and AA. Per-cell scene, context, sample count and limitations are preserved in result.json and the source reports. Screen results do not establish image equivalence, broad DH/PBR compatibility or hardware-optimal implementations. No new measurements were executed. Historical live observations retain their original scope; none qualifies the current runtime.

## Decision

Discard from active implementation under the final retention decision. Revisit only with a changed mechanism, stronger correctness evidence or a deliberately revised acceptance scope; do not re-enable it solely because one cell improved.

## Evidence and executed source

[asahi-campaign-conclusions.json](../sources/asahi-campaign-conclusions.json) sha256:c18e64071107702e72a05b5a9c58fb6352f3d528b15d6cd272f631b4661c1e9c

[rtx5080-campaign-conclusions.json](../sources/rtx5080-campaign-conclusions.json) sha256:26bcd992d13d588db43197448aad8333f4646d280c982f0385aff64a79f3b2cb

[rtx5080-campaign-measurements.json](../sources/rtx5080-campaign-measurements.json) sha256:97989c9385528de5756a42128dce565e73e531e5d0c5d28b59e4e74c342dfc78

Full run records, source history, frozen inputs and supporting scripts are retained in [the historical artifact snapshot](/home/philip/.local/share/shaderlod-next/evidence/history/original-repository). See [artifact access](../../docs/results.md#historical-artifact-access) for path mapping, verification and known remote-only evidence.
