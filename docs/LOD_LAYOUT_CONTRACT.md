# Explicit adapter target and pass layout

2026-10-05. The dependency-free
[`layout`](../src/main/java/com/pp/shaderlod/lod/adapter/layout/AdapterLayout.java)
API validates immutable, adapter-supplied image allocations, quantity views,
reads, simultaneous draw groups and full-target viewports. It complements the
[work adapter contract](LOD_ADAPTER_CONTRACT.md) and
[spatial pipeline](SPATIAL_LOD_PIPELINE.md). It contains no pack names, attachment
indices, GLSL parser, rewriting, source discovery or automatic source certificate.
An accepted declaration proves structural consistency of that declaration only.

The motivating diagnostic failure resized target 11 while a later upstream draw
still bound targets 11 and 12 at different dimensions. A producer-only target
check would miss this conflict. Adapters must declare **every reachable draw
group**, including preserved upstream groups. The validator compares every
attachment to the first, including a third or later attachment; it never stops
after one matching pair.

| Declaration | Meaning |
| --- | --- |
| `Dimensions` | Positive integer level-zero image or viewport dimensions. |
| `TargetSize.Absolute` | Fixed allocation dimensions. |
| `TargetSize.Relative` | Independent rational width/height scales of the rendered surface, each with explicit FLOOR or CEIL. |
| `TargetDeclaration` | Namespaced logical image identity, size, reviewed format and one or more quantity views. |
| `Quantity` | Namespaced basis/schema key, storage role, required channels and, for bounded float integers, an explicit maximum magnitude. |
| `PassDeclaration.Read` | Explicit image identity and numeric/unfiltered fetch declaration. |
| `PassDeclaration.DrawGroup` | Attachments simultaneously drawn with one viewport. Separate groups may use different dimensions. |
| `AdapterLayout` | Immutable targets and ordered pass declarations. Duplicate target/pass identities are schema errors. |
| `LayoutValidation` | Immutable resolved target dimensions and located failures; `valid()` means no structural failure. |

Sizes use actual rendered surface dimensions, not display size. Positive int
scale terms multiply in long; even maximum int terms plus the CEIL adjustment
fit long. Resolution rejects zero after FLOOR and any result above
`Integer.MAX_VALUE`, rather than truncating or wrapping. At 1709 × 961,
CEIL half/quarter allocations are 855 × 481 and 428 × 241. This matches the
spatial kernel's allocation formula; incomplete receiver tiles still require
the adapter's full fallback. A declaration can never prove that shader mapping.

Every group must have equal attachment dimensions and a viewport equal to those
dimensions. Smaller viewports fail `VIEWPORT_UNDERSIZED`; larger ones fail
`VIEWPORT_OVERSIZED`. A viewport smaller on one axis and larger on another reports
both. Partial draws, atlas region offsets and mip/subresource layouts are outside
this initial contract. Do not suppress this full-target check to authorize them.
Unknown read/write identities and failed allocation formulas invalidate the
layout; remaining independent checks still run.

Direct read/write overlap is rejected under `FeedbackPolicy.UNSUPPORTED`.
`DISTINCT_IMAGES_EXTERNALLY_VERIFIED` declares that the same logical identity
uses **physically distinct** read/write images, for example reviewed ping-pong.
It never authorizes sampling the same image being drawn. The adapter/runtime
owner must establish those distinct images; the validator cannot inspect GL
bindings, aliasing, pass completion or renderer flip/clear behavior.

## Storage and exact certificates

Each quantity view declares its own channel requirement; views can occupy
different texels of one allocation. Their channel counts are not summed. Packing,
locations, lifetimes, units and decode remain explicit adapter obligations.
Known formats are deliberately reviewed enum entries; unsupported formats need
an explicit API extension and checks, rather than inference from shader text.

`R11F_G11F_B10F` is a reviewed packed RGB floating format: stored component widths
11/11/10, no sign bits, 5 exponent bits and 6/6/5 stored fraction bits.
[`Khronos specification`](https://registry.khronos.org/DataFormat/specs/1.4/dataformat.1.4.inline.html)
defines these unsigned mini-float encodings. `componentBits(channel)` exposes each
width; scalar `componentBits()` returns the minimum width (10 for this format,
unchanged for existing uniform-width formats). It permits a three-channel float
quantity, has no stored alpha and cannot satisfy any exact metadata or binary32
distance role. The adapter must separately prove the quantity's nonnegative range
and acceptable rounding error; a linear declaration alone makes neither claim.

- `GENERAL` checks channel capacity; `LINEAR_QUANTITY` also requires float storage.
  Neither validates that a shader expression is a linear basis.
- `EXACT_SIGNED_INT32_METADATA` / `EXACT_UNSIGNED_INT32_METADATA` require matching
  32-bit integer storage and signed/unsigned integer `texelFetch` declarations.
  RGBA16I, floating point and signed/unsigned reinterpretations fail.
- `BINARY32_MINIMUM_DISTANCE` requires 32-bit float storage and
  `Fetch.FLOAT_TEXEL_FETCH`. Half-float or filtered reads cannot certify an exact
  conservative minimum. Raw float bits stored in integer metadata instead use
  the exact integer role; preservation via `floatBitsToInt` / `intBitsToFloat`
  remains source/driver proof.
- `EXACT_BINARY32_BOUNDED_INTEGER_METADATA` requires an explicit maximum integer
  magnitude in **[1, 16777216]**, 32-bit float storage and
  `Fetch.FLOAT_TEXEL_FETCH`. Every integer in that signed magnitude interval is
  representable in binary32. 16777217 is not; it rounds to 16777216. Ordinary
  float storage does not automatically grant this metadata role. Fractional,
  out-of-domain, stale or misdecoded shader values are not inspected by Java.

`FLOAT_OR_NORMALIZED` declares ordinary float/normalized reads; it cannot read
integer textures or satisfy exact float metadata/distance fetch requirements.
The integer fetch variants declare unfiltered integer addressing. Actual shader
instructions and runtime texture/sampler state must separately match declarations.

## Concrete reference adapter handoff

The diagnostic fixture declares three full-size RGBA32F outputs and the preserved
later two-output group. Its mismatch control declares two quarter-size outputs
plus a full-size third, and the historical resized/full-size upstream pair;
both fail. This is a reusable declaration example, not a new runtime repair.
The diagnostic source owner owns its actual Iris layout and repair.

The spatial adapter owner selected one full-size interleaved 2 × 2 RGBA32F image
in the existing idle Photon allocation. A linear quantity occupies one lane and
its certificate the adjacent lane. The other lanes are invalid. A representative
declaration uses generic adapter keys:

```java
var full = TargetSize.relative(1, 1, TargetSize.Rounding.CEIL);
var cache = new TargetDeclaration("example:interleaved", full,
        TargetFormat.RGBA32F, List.of(
        new TargetDeclaration.Quantity("example:linear_basis",
                TargetDeclaration.Role.LINEAR_QUANTITY, 4),
        new TargetDeclaration.Quantity("example:certificate",
                TargetDeclaration.Role.EXACT_BINARY32_BOUNDED_INTEGER_METADATA,
                3, 1L << 24),
        new TargetDeclaration.Quantity("example:minimum_distance",
                TargetDeclaration.Role.BINARY32_MINIMUM_DISTANCE, 1)));
// The reference producer writes full-size sky + cache in one group.
// A later consumer declares Read(cache.id(), Fetch.FLOAT_TEXEL_FETCH)
// and draws a completed/ping-ponged full-size composition image.
var acceptance = declaredAdapterLayout.validate(new Dimensions(renderWidth, renderHeight));
if (!acceptance.valid()) {
    // Retain the adapter's untouched full-resolution path; do not enable cache use.
}
```

Before wiring this layout into capability/source registration, the integration
owner must instantiate the adapter declaration and use failed validation as a
full-path gate on initialization, resize and source/options changes. This package
does not mutate registration, transport or production bindings. The reviewed
source graph, including all unchanged upstream groups, must be mapped to the
declaration by the adapter owner. Compare resolved allocations and viewports to
actual driver resources before enabling the runtime cache contract.

The selected reference packing separately guards texture dimensions equal to
rendered dimensions, width/height ≤ 8192, CEIL half-tile capacity ≤ 2^24,
nonnegative epoch ≤ 2^24, fixed quantity/side token 258, exact bounded tile index
and raw binary32 minimum distance. Its source must prove integer addressing,
complete odd-edge fallback, invalid-lane writes, epoch wrap/reset and no surviving
stale certificate. Java storage validation cannot establish any of those facts.
The source owner owns this bounded domain and kernel connection; the client/GPU
owner alone verifies rendered allocations, effective controls and actual outputs.

The revised source graph uses producer MRT **0/2** and consumer MRT **0/3** (or
the original conditional target-0-only branch). Target 0 retains its actual
`R11F_G11F_B10F` format and a three-channel quantity; target 2 is the interleaved
RGBA32F cache. Every group keeps full dimensions and the full viewport. The
declaration must keep target 0's format separate from the cache's exact metadata
format. Logical target 0's reads/writes require
`DISTINCT_IMAGES_EXTERNALLY_VERIFIED`: producer completion and Iris image flips
must prove distinct physical read/write images before cache use. This contract
does not create that proof. The shader owner supplies the exact active samplers,
all preserved groups and target 3's reviewed format for root's registration.

## Bounded acceptance

Isolated Java 21 `javac --release 21` and JUnit Platform execution passed **20/20
tests**, zero failures or skips, in 57 ms. Fixtures cover absolute/anisotropic
rational resolution, odd/tiny dimensions, maximum products and overflow, unknown
references, independent draw groups, the three-MRT/upstream-pair regressions,
viewport coverage, separate lower-rate producer groups with a full consumer,
full-size interleaved quantity/certificate views, exact signed/unsigned and
bounded binary32 metadata, unfiltered fetch declarations, direct feedback,
schema errors and defensive copies. Three added packed-format cases verify true
channel/precision properties, three-channel mixed-format MRT acceptance and
four-channel/exact-metadata rejection. Actual source hashes and the launcher are
recorded in the raw log:
`/var/folders/45/xr_x4hl921qg64tj4ln3jp_r0000gp/T/shaderlod-layout-contract-nzrwvkyn/acceptance-packed-format.log`.
Earlier 14-test and 17-test runs also passed; their prior logs are retained in
that directory.

No Gradle, production/dev class-resource output, Minecraft client, shader reload,
driver, GPU, quality or performance check ran. No live process remains owned.
Root still owns the idle GPU/build window and integration review. Layout tests
do not establish the project's rendering-throughput or quality goal.
