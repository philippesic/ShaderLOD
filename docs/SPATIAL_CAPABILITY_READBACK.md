# Shared spatial linked capability readback

2026-10-05 source checkpoint. Development observer only; no linked driver,
rendered spatial capability, physical work, quality or performance acceptance.

`CloudSamplerStateProbe` adds `shaderlodOpaqueSpatialAvailable` to its existing
bounded capability-name list. The `logs/cloud-sampler-state.json` report retains
top-level schema 1, capability schema 1, core schema 2/seven fields and effects
schema 2/thirteen fields. The separate `capability_uniforms` object now contains
the existing `shaderlodCapabilitiesVersion` and
`shaderlodVolumeIntegrationAvailable` plus the new spatial availability field.
All three use the same actual linked-uniform inventory and scalar integer getter
path; there is no spatial-specific observer mechanism or supplier evaluation.

For the spatial field, typed observation requires inventory `GL_INT` (5124),
size 1, a nonnegative queried location and
`status=queried_without_gl_error_consumption`. The returned integer is preserved
exactly. Values 0 and 1 remain actual unavailable/available observations; other
integers remain actual observations requiring separate protocol rejection.
Missing/inactive/bounded-scan-omitted entries, wrong type, arrays, negative
locations and Java query exceptions remain `status=unknown`, with a reason and
no value. Unknown is not unavailable. Capability version expectation 1 remains
separate metadata and cannot replace the actual sampled version.

Explicit registration/source binding exports this name dynamically. Merely
including the name in the observer establishes no adapter support, source
eligibility or availability. The observer does not call suppliers, inspect pack
sources, infer support from options/config or bind/mutate a GL program. Capability
records are initialized unknown before linked introspection, including early
capture failures. The property `shaderlod.dev.cloudSamplerProbe` still defaults
off; fullscreen and raster callbacks return before GL/report work when off.
The existing sampler active-texture `finally` restoration is unchanged.

## Diagnostic extraction and acceptance

Only the client/GPU owner may launch the opt-in observer, in a fresh **untimed**
JVM outside performance windows. Preserve exact source/artifact/options/config
pins, the raw report and client shader/link/GL/capture logs. Extract each complete
`capability_uniforms` record alongside `current_program`, `timestamp_utc`,
`draw_origin`, linked state, scan bounds/truncation and `capture_failure`.
For completed sampler captures verify matching active texture before/after.
Do not rewrite missing values as zero or substitute configured/supplier values.

The unchanged `effect_uniforms` object already captures the actual spatial
consumer controls: scalar `GL_INT` `shaderlodOpaqueLightingMode`, and scalar
`GL_FLOAT` `shaderlodOpaqueLightingNear`, `shaderlodOpaqueLightingFar`,
`shaderlodOpaqueLightingTransition`, `shaderlodOpaqueLightingMediumWorkRatio`
and `shaderlodOpaqueLightingFarWorkRatio`. Near/far/transition are camera-relative
Minecraft **blocks**, not chunks. Those records need their own linked inventory,
scalar shape, nonnegative location and queried-status checks; config bytes alone
do not establish effective linked policy. Missing fields remain unknown.

The observer samples only the first observed draw per numeric GL program ID,
at most 64 IDs per JVM. Scans remain bounded to 4096 uniforms and 1024-byte names.
The timestamp precedes introspection, not each getter. Draw origins
`iris-fullscreen-quad` and `sodium-raster-submission` identify hook context,
not shader stage. There is no consuming-stage filter, relink/ID-reuse resampling
or settled/live-reload guarantee. An early availability zero must remain zero;
it cannot establish later absence. Relate actual program IDs to the pinned
producer and consumer stages using separate operator evidence.

Actual capability 1 at a mapped consuming stage is one linkage check. It does
not establish producer completion, layout/attachment/sampler correctness,
cache/certificate validity, reduced physical work, motion/still quality or the
1.5x total-rendering-throughput goal. Those remain separate runtime obligations
in [the shared spatial reference](SHARED_SPATIAL_REFERENCE.md).

## Scoped checks and handoff

Compiled only the probe and the two fixture sources with Java 21, `-proc:none`,
existing dev/main classes, the entries in
`build/moddev/clientLodOnLegacyClasspath.txt`, and the existing pinned
`build/moddev/artifacts/neoforge-21.1.253-merged.jar`. All generated classes/logs
were written to a temporary directory; no Gradle/build output was mutated.
Each fixture executed in a separate JVM with the newly compiled probe first on
the classpath:

- `CapabilityReadbackTest`: 9 groups passed, exit 0. Actual spatial 0/1/unexpected
  integer retention, typed scalar getters/location zero, missing/truncated
  inventory, wrong float/bool type and array shape, negative locations and
  independent lookup/getter failures are covered. Seven isolated spatial failure
  cases preserve actual version 1 and volume availability 0. Disabled callbacks
  and separate core7/effect13 shapes remain covered.
- Unchanged `EffectRuntimeReadbackTest`: 7 groups passed, exit 0, including all
  thirteen typed effect fields, core7, disabled callbacks and sampler/texture
  formatter fallbacks.

Exact passing log and source/command provenance are respectively
`/var/folders/45/xr_x4hl921qg64tj4ln3jp_r0000gp/T/shaderlod-spatial-capability-b4sst1x5/fixture-results.log`
and `provenance.json` in that directory. The initial compile omitted the
Minecraft-containing merged JAR and failed on `Minecraft`; retained log/provenance
are in
`/var/folders/45/xr_x4hl921qg64tj4ln3jp_r0000gp/T/shaderlod-spatial-capability-i1mu1vq3/`.
No fixture ran in that failed attempt. Temporary evidence may be cleaned by the
host; the parent may copy it into ignored durable evidence during integration.

Scoped whitespace validation passed. Source ownership is frozen for the parent
to integrate and perform the single coordinated build. No Minecraft process,
shader reload, GPU experiment or live worker-owned process remains. The mocks
establish formatter behavior and off-state no-op behavior; actual GL querying,
state restoration, consuming-stage identity and settled capability remain
unverified runtime boundaries.

Parent integration copied the passing fixture output and provenance into
`dev/logs/spatial-capability-fixtures/` for durable local evidence.
