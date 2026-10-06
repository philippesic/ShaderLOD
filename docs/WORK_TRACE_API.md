# Shared diagnostic work trace

2026-10-05. Schema 1 is an explicit, sampler-free diagnostic API in
[work_trace.glsl](../shaderpacks/common/shaders/include/shaderlod/work_trace.glsl).
The first consumer is the separately owned Photon volume diagnostic reference.
This API does not instrument or rewrite shader sources, register uniforms,
declare a shader option, allocate attachments, or change the work policy.

The [volume audit](SHARED_VOLUME_WORK_AUDIT.md) shows why negotiated counts cannot
stand in for executed work: a coarser march can visit more occupied cells or miss
an early exit. The counters below increment at reached operations independently
of budget metadata. They count instrumented shader source evaluations; they do
not measure hardware texture transactions, GPU time or production speed.

## Storage and calls

The caller supplies the private, default-off `SHADERLOD_WORK_TRACE` shader
option and includes the helper. An enabled shader owns one `ShaderlodWorkTrace`
per fragment invocation, either as a local or an invocation-local GLSL global.
Declare with **no trailing semicolon**; the declaration macro owns it. Other
macros use ordinary statement semicolons:

```glsl
#include "/include/shaderlod/work_trace.glsl"
SHADERLOD_TRACE_DECLARE(adapter_trace)

// Once at fragment entry, before any instrumented operation:
SHADERLOD_TRACE_INIT(adapter_trace);

// When the actual selected path and its budgets are known:
SHADERLOD_TRACE_SELECT(adapter_trace, original_uint, requested_float,
    selected_float, SHADERLOD_TRACE_PATH_ADAPTED, reason_uint);

// Each call must sit at its declared, reached operation:
SHADERLOD_TRACE_PRIMARY(adapter_trace);
SHADERLOD_TRACE_DENSITY(adapter_trace);
SHADERLOD_TRACE_SECONDARY(adapter_trace);
SHADERLOD_TRACE_TEXTURE(adapter_trace);

#ifdef SHADERLOD_WORK_TRACE
counts_out = shaderlod_trace_encode(adapter_trace, 0u);
selection_out = shaderlod_trace_encode(adapter_trace, 1u);
schema_out = shaderlod_trace_encode(adapter_trace, 2u);
#endif
```

`INIT` clears counters and sets valid zero metadata/path NONE. `SELECT` sets
metadata without resetting counters. `PRIMARY`, `DENSITY`, `SECONDARY` and
`TEXTURE` each add exactly one to their independent uint counter. Instrument
the original and adapted paths using the same declared operation scopes. Never
populate counts by multiplying a negotiated budget or by anticipating loop
iterations, density occupancy, early exits or texture branches. Repeated texture
expressions require one marker each, immediately at the expressions; a marker
before a branch containing optional fetches overcounts them.

Each adapter must document primary iteration boundaries, which density
functions and secondary loops are counted, and which source texture-value
evaluations are included. `textureSize` is metadata and is not a texture-value
evaluation. Reaching a density function that immediately returns still counts
one density evaluation; its unreached texture expressions count zero. A
secondary visit and its density call are distinct counters. These scopes can
overlap and must not be added as a universal work total.

`SHADERLOD_TRACE_INVALIDATE(trace)` marks unsupported or incomplete instrumentation
invalid. Metadata failure and count overflow are sticky until `INIT`. Increment
saturates before adding beyond the representable limit, invalidates the whole
record, and never wraps. Encoding rechecks all counters and metadata to reject
corrupted fields too. Signed inputs must be validated before uint conversion;
negative signed values are not valid work counts.

## Schema 1 codec and decoder contract

Emit all three pages through three simultaneous MRT outputs in the **same draw**.
The caller/capture manifest declares the attachment identity and page mapping.
Independent draws cannot establish a same-fragment record even with matching
camera/time labels. Use GL4.1-compatible RGBA32F attachments and raw RGBA float
readback with blending disabled. Preserve values directly: no normalization,
filtering, reconstruction, blending, gamma conversion, tone mapping or
intermediate RGBA16F/RGBA8 storage. Capture must verify actual attachment format,
dimensions, read target, completeness and producer identity, independently of
the option/configuration text.

| Page | R | G | B | A |
| --- | --- | --- | --- | --- |
| 0 | primary_iterations | density_evaluations | secondary_visits | texture_evaluations |
| 1 | original_work | requested_work | selected_work | path_tag |
| 2 | schema = 1 | reason_tag | valid = 1 | reserved = 0 |

All counters, original_work, tags and schema fields are exact integers in the
closed range **0 to 16,777,215**, stored as binary32 float values. All integers
in this range are exactly representable. This deliberately excludes 16,777,216
so every accepted increment remains inside a fixed bounded codec domain.
`requested_work` and `selected_work` are finite, nonnegative binary32 continuous
budgets in the same numeric range, with fractional values preserved. They are
labels, not counters. The general tracer does not enforce requested ≤ original
or selected ≤ requested; detecting an unexpected adapter relationship is part
of the diagnostic. Derive an integer selected cap as `ceil(selected_work)` if
the adapter uses that rule; never call it executed work.

| path_tag | Meaning |
| --- | --- |
| 0 NONE | No selected march/evaluation path yet; initialized budgets are zero |
| 1 ORIGINAL | Actual original body selected, including full/fallback paths |
| 2 ADAPTED | Actual adapted body selected |
| 3 FULL_TARGET | Explicit diagnostic adapted-body control at original target |

`reason_tag` is an adapter-declared exact uint; its complete mapping belongs in
the capture metadata. It may use existing shared policy reasons but the helper
has no Photon-specific meaning or constants. Unknown paths require a future
schema or an explicit invalid record rather than an invented interpretation.

For any overflow, invalid metadata or page outside 0/1/2, the encoder returns
**(-1, -1, -1, -1)**. An invalid trace emits that sentinel on every page. A
decoder must:

1. Require all three corresponding page pixels from the same draw/coordinates.
   Missing pages, unsupported attachments or failed reads are unknown, not zero.
2. Recognize twelve channels all equal to -1 as an invalid record, excluding it
   from valid work totals. Report invalid pixel counts separately.
3. Reject nonfinite values, partial sentinels, negative values, values above the
   bound and fractional counters/original_work/tags/schema/valid/reserved.
4. Require schema 1, valid 1, reserved 0 and path in 0..3. Budget fractions are
   legal; reason integers need the independently declared adapter mapping.
5. Retain requested/original/selected values separately from counts. Aggregate
   valid integer counts with a wide host integer and report coverage by path and
   reason. Never normalize to an image channel or silently clamp invalid values.

## Disabled baseline, units and quality

When `SHADERLOD_WORK_TRACE` is absent, every declaration/operation macro expands
to nothing, including argument evaluation. Counter storage, constants,
functions and codec do not appear in preprocessed ordinary shaders. Diagnostic
encoding/output declarations also need caller-side guards. This helper never
defines the option itself; it cannot enable diagnostics through a stage leak.
The production adapter and archive remain separately pinned by their owner.

Counts and budgets have work units declared by each adapter. This helper uses
no distance units. The existing shared policy uses radial camera-relative
distances in Minecraft blocks and centered transition widths; the adapter must
preserve those semantics and report its selected/fallback path. Tracing changes
no transition, sampler behavior, fallback, effect strength or quality setting.
Uninstrumented original Photon remains the performance baseline. Instrumented
diagnostics perturb compiler/register/output costs and are untimed evidence;
they cannot establish a production speedup or unchanged visual quality.

## Checks and handoff

`python3 -m unittest discover -s tests -p 'test_work_trace.py' -v` passes five
fixture groups, with no skips. Evidence log:
`dev/logs/work-trace-helper-tests.log` (ignored local output).

- The actual helper source executes through an isolated C++ builtin/type shim:
  independent operation sequences, occupancy/early-exit examples, fractional
  metadata, all 16,777,216 accepted integer roundtrips, each counter's overflow,
  sticky invalidity, nonfinite budgets, corrupted fields and invalid pages.
- The independent decoder fixture checks 4,096 binary32 counter tuples and
  rejects missing/malformed/nonfinite/partial-sentinel/schema records.
- `cpp` proves ordinary preprocessing removes storage, calls and deliberately
  invalid/side-effecting macro arguments.
- Five complete offline GLSL 4.10 fragment graphs compile: diagnostic off,
  pages 0/1/2 and an invalid-page sentinel. Static checks reject transport,
  sampling, derivative dependencies and helper-side option definitions.

Initial fixture failures were corrected rather than accepted: an overly broad
texture-name check matched the counter field, a host unused-constant warning and
preprocessor comments broke fixture assertions. Offline GLSL then caught a real
disabled global empty declaration; the declaration macro now owns its semicolon
and the caller omits a trailing one. The subsequent full fixture run passes.

Entry points are `shaderlod_trace_init`, `shaderlod_trace_select`,
`shaderlod_trace_increment`, `shaderlod_trace_valid` and `shaderlod_trace_encode`;
the public macros bind actual operation sites. Adapter and capture owners have
received the schema and declaration contract. Actual pinned Iris option discovery,
consumer counter coverage, driver compilation/readback, synchronized runtime
inputs and quality remain their separate acceptance checks. No client/reload,
project build, main/dev class output, archive or GPU job ran in this task; no
owned live processes remain. Root owns the idle GPU and schedules reloads.

Performance still requires matched original/disabled/optimized production
revisions, hardware, effective options, inputs, resolution/focus and warmup;
repeated GPU elapsed and total-frame timings under the quiet protocol, with
counters/readbacks excluded, plus separate still/motion quality checks. No
performance or project-completion claim follows from these fixtures.
