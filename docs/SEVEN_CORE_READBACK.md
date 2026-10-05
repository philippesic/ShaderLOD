# Seven-core linked uniform readback

The opt-in dev-only CloudSamplerStateProbe now records all seven existing core
uniform names. This does not add production transport or per-effect fields.
The report keeps schema1 and adds core_uniform_schema2; older four-field reports
and their exact hashes remain unchanged historical evidence.

| Field | Expected active scalar type |
| --- | --- |
| shaderlodBridgeVersion | int |
| shaderlodEnabled | int |
| shaderlodPreset | int |
| shaderlodDebug | int |
| shaderlodNear | float |
| shaderlodFar | float |
| shaderlodTransition | float |

Type selection uses explicit names rather than array positions. The probe checks
linked type/size and location before a typed getter. Missing, optimized-out,
truncated, non-scalar or differently typed entries remain unknown; no zero value
is fabricated. It reads driver state without setters, supplier reevaluation or
extra smoothers. Existing texture binding restoration and sampler inspection
remain unchanged.

The hook is the pinned Iris full-screen quad draw. It captures once per numeric
GL program ID, at most64 programs and4096 active uniforms per program. It does
not prove program source identity, which budget executed, physical ray counts,
atomic config upload or a later live reload. A first-draw readback may precede
settled configuration; actual report values must be checked against the intended
scene. Numeric program IDs can be reused. Getter stalls make this an opt-in
non-performance session; GPU timing remains off.

Root ran Gradle build/compileDevClientJava successfully after the small dev
extension. Production JAR contains no dev classes/mixins. Root session76970 completed exit0, existing Photon reduced archive, native
2976x1672/minimal/noDH,25s warmup/5s capture, cloudSamplerProbe=true/GPUfalse.
Raw provenance/request/compiled hashes are in
run-workloads/photon-seven-core-readback-r1/coastalwater/probe-provenance.json.
The ordinary performance goal and new Miniature field consumption remain
separate acceptance work.


Root validated actual workload UUID/archive/options/control/artifacts/native
window/temporal/class/screenshot contracts. The schema2 report contains22first-
draw program records. Numeric program21 exposed all seven fields with matching
active scalar types, nonnegative locations and values: version2/enabled1/preset1/
near32/far96/transition8/debug0. Program20/22 exposed six fields, others fewer;
inactive entries remain unknown. No GL query/capture failure was observed in the
runtime log. Shutdown OpenAL warnings are separate from GL state.

This is a Photon normal-output readback, not a Miniature new-consumer result.
Numeric program21 is not by itself a source-stage identity or later live-reload
proof. No physical branch/trace-count or GPU benefit inferred. Report/provenance/
validated workload/final PNG are preserved at
 dev/logs/photon-seven-core-readback-r1. All owned runtime jobs are terminal.

Readback report SHA256: 3640bae4bc260d8775024062c8aad51253fb5a30e06dc9d0a0d5c33fa030f4cb
