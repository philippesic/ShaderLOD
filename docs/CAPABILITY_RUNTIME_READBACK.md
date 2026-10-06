# General LOD capability readback

2026-10-05 source checkpoint. This development-only observer samples the general
capability bridge from the current linked GL program. Photon is the first explicit
adapter. This checkpoint establishes no runtime availability, physical work,
quality or performance result.

`CloudSamplerStateProbe` adds `capability_uniform_schema=1` to the existing
`logs/cloud-sampler-state.json` header and a separate `capability_uniforms` map to
each captured program. Top-level `schema=1`, `core_uniform_schema=2`, all seven
core fields, `effect_uniform_schema=2` and all thirteen effect fields stay intact.

| Field | Required linked shape | Interpretation |
| --- | --- | --- |
| `shaderlodCapabilitiesVersion` | Scalar `GL_INT` | Actual sampled integer; `expected_protocol_version=1` is separate metadata |
| `shaderlodVolumeIntegrationAvailable` | Scalar `GL_INT` | Actual sampled integer; expected bridge semantics are 0 unavailable, 1 available |

The existing bounded linked-uniform inventory determines whether a getter is
legal. Each record contains expected type, inventory presence and, when found,
actual uniform name/type/size. Only scalar `GL_INT` reaches a location query and
`glGetUniformi(program, location)`. Location zero is valid. Missing/inactive fields,
bounded-scan omissions, wrong types, arrays, negative locations and Java query
exceptions remain `status=unknown`, with a reason and no value. Early capture
failure initializes both fields as unknown before attempting linked introspection.

A legal getter records `status=queried_without_gl_error_consumption`. No
`glGetError` call is added; this status alone cannot certify absence of GL errors.
No supplier invocation, source detection, option/config fallback, program binding
or uniform mutation contributes to these values. Unexpected versions and integer
values are retained exactly; a successful getter is separate from protocol
acceptance. In particular, a sampled availability 0 remains an actual 0.

The property `shaderlod.dev.cloudSamplerProbe` still defaults off. Both fullscreen
and raster callbacks return before any GL query/report work when off. The only
existing transient state change is active texture selection during sampler reads;
the existing `finally` restoration is unchanged. No new hook or settled-phase
sampling is introduced.

## Runtime extraction contract

The sole client/GPU operator owns a fresh **untimed** diagnostic JVM with
`-Dshaderlod.dev.cloudSamplerProbe=true`, exact artifact/source pins, effective
options/config and a fresh profile log root. Preserve the raw report and client
logs. This observer can stall and must stay outside performance windows.

Extract each capability observation with its program ID, `timestamp_utc`,
`draw_origin`, linked status and scan bounds. The entry timestamp is captured
immediately before the program introspection; it is not an exact per-getter clock.
Origins are `iris-fullscreen-quad` or `sodium-raster-submission`; neither identifies
a shader stage or proves effect execution. A program is captured only at its first
observed draw, at most 64 numeric IDs per JVM. Uniform scans are bounded to 4096
entries and 1024-byte names. ID reuse/relinking does not produce another sample.

For example, this read-only extraction preserves raw field records without
converting missing state into a boolean or substituting configured values:

```sh
python3 - /absolute/profile/logs/cloud-sampler-state.json <<'PY'
import json, sys
report = json.load(open(sys.argv[1]))
assert report.get("capability_uniform_schema") == 1, "fresh schema-1 report required"
for entry in report["programs"]:
    print(json.dumps({key: entry.get(key) for key in (
        "timestamp_utc", "current_program", "draw_origin", "linked",
        "active_uniform_count", "uniform_scan_truncated", "uniform_name_length_truncated",
        "capability_uniforms", "capture_failure", "active_texture_before", "active_texture_after"
    )}, sort_keys=True))
PY
```

Accept typed observation only with actual inventory type `GL_INT` (5124), size 1,
nonnegative location and queried status. Evaluate actual protocol version 1 and
availability 0/1 separately. Unknown is not unavailable. Availability 0 at an early
draw does not establish later capability absence: adapter pipeline construction
may initially expose unavailable state, and this first-draw observer cannot prove
settled or live-reload state. Do not replace an early 0 with a later supplier
readback or a source-based inference. Retain it with its actual time and origin.

The operator must inspect client logs for shader/link/GL/capture failures and
verify matching active texture before/after for completed sampler captures.
Relate a program to a particular consuming shader using separately pinned
source/hook evidence. Linked capability values alone do not prove the common
work decision ran, fewer physical integrations executed, or rendered quality and
total throughput passed. Driver compilation, actual consumer linkage, settled
lifecycle and physical work remain separate acceptance boundaries.

## Source checks

The new `CapabilityReadbackTest` runs the actual formatter and mockable
lookup/integer getter seam without a GL context. Eight fixture groups cover the
property-off callbacks, exact scalar values and location zero, initial unavailable
and unexpected integers, missing inventory, wrong type/non-scalar shape, negative
locations, independent lookup/getter exceptions and separate legacy map shapes.
The retained `EffectRuntimeReadbackTest` must also pass unchanged.

After the integration owner's dev compilation, compile these two fixture sources
with Java 21 and `-proc:none`, using `build/classes/java/devClient`,
`build/classes/java/main` and the newline entries in
`build/moddev/clientLodOnLegacyClasspath.txt`. Write only to ignored
`build/capability-readback-tests`, then execute each class in separate JVMs.
Compilation and fixtures establish source behavior only. The worker launches no
Minecraft client, shader reload or GPU experiment and owns no live process.

Completed integration: the adapter binding owner ran the coordinated
`./gradlew test build compileDevClientJava` retry successfully; dev compilation
included the frozen observer source. Its log is
`build/adapter-binding-integrated-build-retry.log`. This worker then compiled
and ran both fixtures in separate JVMs: `CapabilityReadbackTest` passed all eight
groups and the unchanged `EffectRuntimeReadbackTest` passed all seven groups,
both exit 0. Exact fixture output is
`build/capability-readback-tests/fixture-results.log`. Scoped `git diff --check`
passed. These ignored logs are retained locally; no runtime report was produced.
