# Raw work-trace readback

2026-10-05. Development-only source is frozen for integration and driver checks.
This implements the capture side of [schema 1](WORK_TRACE_API.md) for an
explicitly registered adapter. It does not establish a performance gain,
production parity, visual quality or successful driver rendering.

`WorkTraceEvidence.initialize()` runs at the start of `SmokeClient` construction,
before normal scene registration and Iris's first uniform/pipeline sealing. The
default-off path returns before metadata I/O, adapter registration or GL access.
`DevEvidenceMixinPlugin` omits `WorkTraceReadbackMixin` unless the work-trace JVM
property is true. Existing sampler hooks remain unchanged.

## Registration and controls

The operator supplies a reviewed registration JSON and the exact archive named
by its absolute `capture.source` path. The generic capture contains no Photon
identity, member names, shader options or archive digest. Metadata version 1
contains:

- `descriptor`: exact namespaced identity/revision, protocol versions and COUNT
  capabilities with their minimum retained work.
- `sourceBindings`: exact container SHA-256, exact consuming-member SHA-256 pins
  and allowed effective option values. The supplied archive must match exactly
  one binding before registration. No wildcard source matching is performed.
- `exports`: new operation/uniform exports. Existing shared exports are reused
  by omission; the selected marker must be one of these registered exports.
- `capture`: absolute `source`, three distinct framebuffer `attachments`,
  `markerUniform`, `markerValue: 1`, `gridSide`, `maxCaptures`, `intervalDraws`.
  Array order maps schema pages 0/1/2 to fragment output locations 0/1/2.
- Additional reviewed fields such as `traceContract` retain adapter reason/path
  mappings, instrumentation units and source provenance in the output verbatim.

Metadata validation uses the same `AdapterRegistration` validator as the public
extension API. Runtime capture also requires the exact descriptor and its
exported operations to be AVAILABLE in the actual Iris runtime snapshot. The
producer must have the actual active scalar `GL_INT` marker at a valid location
and value 1. A registered descriptor or option text alone is insufficient.

Required JVM properties are:

```text
-Dshaderlod.dev.workTrace=true
-Dshaderlod.dev.workTraceOnly=true
-Dshaderlod.dev.workTraceMetadata=/absolute/reviewed.registration.json
-Dshaderlod.dev.workTraceOutput=/absolute/existing-directory/raw-work-trace.json
-Dshaderlod.dev.gpuTiming=false
-Dshaderlod.dev.cpuTiming=false
-Dshaderlod.dev.cpuLayerTiming=false
-Dshaderlod.dev.measureWorkload=true
-Dshaderlod.dev.measureOnly=true
```

Workload mode supplies the existing fixed scene, warmup and bounded shutdown;
it does not supply performance evidence in these runs. GPU/CPU timing and
benchmark mode are rejected. `workTraceOnly=true` explicitly qualifies all
frame rows and durations as diagnostic. Trace records and the workload report
say `performanceEvidence: false`; the workload report also says
`diagnostic_only: true`. Preserve normal global OFF/ON configuration through
the workload path. An original-body diagnostic still requires the main mod's
uniform bridge for the marker, so it is distinct from a mod-absent production
performance baseline.

## Hook and state checks

The pinned local Iris 1.8.12 JAR is
`dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar`, SHA-256
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`.
Actual `javap -c -p` inspection and the executable pinned-bytecode fixture verify:

- `FullScreenQuadRenderer.renderQuad()V` draws at bytecode offset 7, restores
  polygon mode at 10 and returns at 13. TAIL follows the actual draw without a
  framebuffer, viewport or program change.
- `CompositeRenderer.renderAll()` installs the viewport and calls `Program.use()`
  before the draw, then restores blending and binds the main target afterward.
- `GlFramebuffer` attaches indexed 2D textures at `GL_COLOR_ATTACHMENT0 + index`.

`WorkloadSession` arms capture at its existing post-warmup/input-fingerprint
boundary and disarms it before terminal reporting/screenshots. Startup draws do
not consume the limit. Cadence counts matching diagnostic draws, avoiding an
accidental cadence that repeatedly selects another composite program.

At TAIL, capture queries the actual program, draw FBO and viewport. For all three
outputs it checks actual draw-buffer mapping, disabled indexed blending and an
all-enabled color mask. Scissor, rasterizer discard, depth and stencil tests are
unsupported because they can leave stale pixels in a diagnostic attachment.
The target must be complete, and each actual attached 2D texture must report
RGBA32F with matching dimensions covering the viewport.

For each grid coordinate it selects and reads all three attachments before any
next draw, using `GL_RGBA/GL_FLOAT` 1 × 1 reads. There are at most 8 × 8 unique
coordinates, 16 capture records and 192 RGBA reads per record; the supplied
default 4 × 4 grid uses 48 RGBA reads. This intentionally synchronous diagnostic
can stall rendering. Its data and elapsed durations cannot be used as speed
evidence.

Capture restores the target FBO's own read-buffer selector and the original
read FBO/selector, pack PBO, pack alignment/row length/skips/byte order/image
height, and any temporarily bound 2D texture. Every restoration is attempted
even if another restoration fails. Draw FBO, viewport, active texture, program
and samplers are not changed. Restoration failures are explicit UNKNOWN rows.

## Output and acceptance boundary

The JSON preserves reviewed metadata and bounded raw page values at each
coordinate, plus separate decoded counts/budgets/path/reason fields. It records
actual attachment texture handles, mip levels, formats and dimensions; actual
program/viewport/FBO; typed marker/frame-counter/time uniforms; render-frame and
draw ordinals; UTC/monotonic capture time and world game time. Missing temporal
uniforms remain explicitly unknown. GL error flags are not consumed; the report
states this limitation. Pixel buffers start as NaNs so failed reads do not
silently become zero counters.

Strict decoding rejects missing pages, nonfinite values, fractional integer
fields, out-of-range values, partial invalid sentinels, wrong schema/valid/
reserved fields and unsupported path tags. All twelve -1 channels mean invalid
instrumentation, never zero work. Budgets retain their continuous values;
`selectedCeilTarget` is metadata and is distinct from primary iterations. Reason
tags remain integers; interpretation uses the adapter's reviewed mapping.
Raw data for invalid records is retained. Empty capture windows end with
`UNKNOWN_NO_MATCHING_DIAGNOSTIC_DRAW` rather than invented measurements.

Checks actually run with JDK 21.0.9 into isolated
`build/work-trace-readback-tests`, without Gradle or main/dev output writes:

- Compilation of the new capture class, mixin and standalone fixture.
- Six no-GL fixture groups covering default-off calls, timing rejection,
  simultaneous reads, tight pack state, independent FBO selectors, restore and
  read failures, mapping/blending/color/scissor rejection, unsupported formats/
  extents, grid bounds, schema/sentinel decoding and exact source mutation.
- Exact source/member validation of all four frozen original/shared OFF/ON
  registration metadata files.
- The actual pinned-bytecode acceptance fixture described above.

Evidence: `build/work-trace-readback-tests.log` and
`build/work-trace-pinned-bytecode-acceptance.txt`. After a coordinated dev compile,
the standalone test takes a registration path and optional pinned Iris path:

```text
java -cp <isolated-tests>:<pinned-dev-classpath> com.pp.shaderlod.dev.timing.WorkTraceReadbackTest build/Photon-WorkTrace-shared-off.registration.json dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar
```

The parent owns coordinated dev compilation and the sole client/GPU lane.
Actual shader compilation, RGBA32F allocation, scene capture, linked marker
values, source-work reduction and driver state restoration remain unverified.
No client, GPU process or other live job is owned by this capture task.
