# Captured shadow-frustum query replay

`scripts/replay_frustum_coherence.py` is an offline tuning tool for the pinned
Java helper. It never launches Minecraft or edits production code. Its inputs
are one captured first same-frame/base-frustum query sequence and the exact
helper source from that capture. This is a diagnostic cost model, not an FPS
or whole-frame performance measurement.

## Trace and provenance

The tool reads `shadow_coherence_oracle.replayTrace` from a workload report,
or the trace object directly. The capture worker owns its source/schema and
actual runtime recording. Required fields match the agreed capture record:

- `kind=BASE`, exact pinned `frustumClass`, `frustumIdentity`, `renderFrame`,
  `prepareOrPlaneGeneration`, `planeCount`.
- `contextAvailable` and context `available/ownerAssociationVerified` must be
  explicit true. Context identity/count must match the trace.
- Context `helperSourceSha256` must equal the actual source read by the tool.
  `fma` is an explicit boolean; `rawPlaneBits` contains only the active four-
  coefficient rows. `rawDistanceLimitBits` is signed/raw int64, with its kind
  and optional hexadecimal field checked against the same bytes.
- Ordered query rows start at index zero and contain six signed/raw int32
  `rawBoundsBits` endpoints plus `original` and `actual` booleans.
- Counts, `sequenceEnded`, `sequenceBoundaryObserved`, `truncated`, `complete`, `originalUnavailable` and
  `revisitedAfterClose` are explicit. Unknown context, unavailable original
  results or a revisited closed scope are rejected.

The capture's `proofCacheStateCaptured=false` is explicit. This replay starts
with an empty helper and cannot reconstruct runtime proofs created before the
first observed callback. Cold replay hit ratios can therefore differ from the
runtime counter distribution. An observed sequence boundary is required before
labeling a nontruncated capture complete; ending a measurement alone is weaker.

Float values are reconstructed in Java with `Float.intBitsToFloat`, and the
distance with `Double.longBitsToDouble`, via an explicit binary transfer.
Negative coordinates, signed zero and NaN payload bits are never recovered
from lossy decimal JSON. Non-finite inputs are retained and counted explicitly;
they are not silently excluded. A truncated/incomplete capture is marked as
**partial recorded prefix only**, never full-frame/world coverage.

Reversed captures are rejected by this initial tool. Their alternate distance
box acceptance requires additional context and its original ordering. Identity
hashes alone do not establish owner association; the capture must verify it.

## Explicit source variants

The actual `ShadowFrustumCoherence.java` and its actual pure-JDK
`ShadowFrustumCoherenceDiagnostics.java` are copied into generated directories.
Four exact single-occurrence source anchors may change:

1. Slot count: 64, 256 or 512.
2. Coarse lower-grid multiplier: 64, 32 or 16 blocks.
3. Coarse extent: grid +32 blocks, respectively 96/64/48.
4. Cell divisor: the same grid value.

The nine combinations are a fixed matrix, not a generic source rewriting
system. Every other helper expression/guard, including original floating-point
containment proof, remains identical. Missing/duplicated anchors fail. Each
variant gets its own source SHA, exact anchor change list and isolated compiler
output. Capture, original helper, diagnostics and Java runner hashes are recorded.

Prepare sources without Java execution:

```sh
python3 scripts/replay_frustum_coherence.py dev/logs/TRACE_REPORT.json \
  --output dev/logs/frustum-replay/unique-run
```

Only after the coordinator releases a CPU window, use a **new output path**:

```sh
python3 scripts/replay_frustum_coherence.py dev/logs/TRACE_REPORT.json \
  --output dev/logs/frustum-replay/unique-executed-run --execute
```

Generated artifacts must live under ignored `build/` or `dev/logs/`; existing
directories are refused. Java 21 is required and its version recorded. On Mac,
the tool resolves the installed Java 21 JDK; `--java-home` allows an explicit
path. Root continues to own runtime/GPU profiling and production integration.

## Correctness and counters

Each variant replays the same query order with a fresh helper context. The Java
runner independently reconstructs pinned Iris's full positive/negative
classifier and rejecting distance predicate, comparing that reference against
every captured original boolean. It also compares every candidate decision
`canAccept || fullOriginalFallback` to the same original. Any mismatch, including
captured actual-versus-original mismatch, makes execution fail.

The counter JVM enables the helper's original final diagnostic gate. It counts
selected fast accepts, new-proof versus cached-proof accepts, cached fallback,
new fallback, invalid AABBs and coarse containment fallback. A new proof accept
is not a reused proof. Reference rejects are separated into distance and plane
rejects; they do not prove that all such queries admit a shared outside proof.

`manifest.json` retains per-variant counts, generated sources, compiler logs
and raw replay stdout/stderr. Passing this bounded prefix does not establish
other frames, camera angles, daylight, dimensions or optional-mod compatibility.

## Optional diagnostic CPU timings

`--execute --timing` adds a separate JVM per variant with the final counter gate
false. Correctness is checked before warming/timing; mismatched variants are
not timed. Warmup/repeats are bounded and baseline/candidate measurement order
alternates. The timed candidate includes a new frame-local helper/arrays,
coarse checks and **full original fallback**, including child distance rejection
and both original plane expressions. Results consume a checksum. No timing
arm uses enabled diagnostic counters.

The model still omits actual Mixin/virtual call layout, frustum construction,
controller/accessor overhead, the visibility graph, driver submission and GPU
work. JIT inlining/escape analysis, GC and this one query distribution can differ
from Minecraft. A nanosecond ranking only selects candidates for real profiling;
it is not a frame gain or an operation-count guarantee. Any final implementation
needs matched original/disabled/enabled actual-frame runs and visual/caster checks.

## Later outside-proof hypothesis

The earlier aggregate oracle rejected about 31% of child queries. The new
replay reports actual captured reference distance/plane rejection counts, but
does not add rejection caching or change the production boolean API.

A future tri-state cache could prove **all contained children outside** when
the coarse box is strictly disjoint from a rejecting distance box, or when
the original positive-expression maximum vertex is strictly `< -plane.w`,
with finite coefficient/extreme evaluation guards. Weak monotonicity then
bounds every child's positive vertex below that same threshold. Tangency
cannot become a rejection. A remembered failed acceptance proof is not an
outside proof: overlapping/partial coarse boxes must retain original fallback.

For reversed frusta, a plane-outside proof is insufficient when any child can
hit the alternate box's early acceptance. Require the entire coarse domain
to be disjoint from that acceptance box too, unless the rejecting distance
constraint already proves all children rejected. This is a distinct future
API/math/property-test change and is not included in the nine source variants.

## First captured replay result

After the coordinator's capture and explicit CPU release, the nine variants
compiled and replayed with Java 21.0.9. Evidence is preserved under ignored
`dev/logs/frustum-replay-r1/`, including per-variant source, compiler logs,
counter/timing stdout and the complete manifest.

Input `dev/logs/frustum-coherence-trace-measurement.json` SHA256:
`d44b6ebf2cfe03c372d250f31e6cc3dd8438fb5091af59740eb9b44a8dc7aea3`.
Captured helper SHA256:
`f730516be75ca91ae915d548d5454cfb92507d478af4f3fba9262c3b476254bb`.
Diagnostics SHA256:
`6a838ceb20b2eafa5ca733d852405a302f6e518ac7c4a09fb284e19ba28a6fc1`.
Generated runner/source hashes and exact variant edits remain in the manifest.

The complete first BASE sequence contains 3,594 queries, frame 4622, preparation
generation 9, eight planes, FMA enabled and distance 128. Owner/context verified;
no truncation, unavailable reference, revisit, non-finite endpoint/coefficient or
captured mismatch. Every variant produced zero original-reference and candidate
decision mismatches. Initial proof-cache contents remain uncaptured, so this is
a cold replay rather than a reproduced runtime cache history.

| Grid / extent / slots | Fast accepts | New / reused accepts | Baseline / candidate median µs per trace | Median paired time ratio |
| --- | ---: | ---: | ---: | ---: |
| 64 / 96 / 64 | 192 (5.34%) | 27 / 165 | 48.40 / 67.00 | 1.383 |
| 64 / 96 / 256 | 192 (5.34%) | 5 / 187 | 51.94 / 70.94 | 1.366 |
| 64 / 96 / 512 | 192 (5.34%) | 3 / 189 | 52.04 / 71.21 | 1.367 |
| 32 / 64 / 64 | 480 (13.36%) | 223 / 257 | 51.83 / 74.15 | 1.430 |
| 32 / 64 / 256 | 480 (13.36%) | 151 / 329 | 51.85 / 70.83 | 1.367 |
| 32 / 64 / 512 | 480 (13.36%) | 129 / 351 | 50.04 / 68.71 | 1.362 |
| 16 / 48 / 64 | 622 (17.31%) | 622 / 0 | 51.62 / 90.75 | 1.757 |
| 16 / 48 / 256 | 622 (17.31%) | 622 / 0 | 51.75 / 90.58 | 1.751 |
| 16 / 48 / 512 | 622 (17.31%) | 622 / 0 | 51.71 / 90.21 | 1.747 |

All accept-only variants were slower in this full-fallback CPU model. Smaller
grids increased selected acceptance but the 16-block variants had no proof
reuse; every acceptance required a new coarse proof. More slots reduced some
collisions but did not yield a winning modeled cost. None should be promoted
from this evidence. Ratios are diagnostic CPU costs, not Minecraft frame-time
or FPS changes.

There were 1,105 reference rejects (30.75%): 811 distance rejects (22.57% of all
queries) and 294 plane rejects (8.18%). Most rejection already happens through
cheap original child-distance comparisons. An outside-proof cache cannot be
assumed useful merely from the aggregate rejection percentage.

The first preflight failed because capture `originalUnavailable` is a boolean
false, while the initial fixture modeled an integer count. The parser now accepts
explicit false (and rejects true), with a fixture preserving that schema case;
original trace/helper bytes were unchanged. Eight tiny parser/source-anchor/
binary/provenance fixtures passed again. No game launch or production source
edit occurred during replay.
