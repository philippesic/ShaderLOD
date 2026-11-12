# Early rendering experiments exposed concrete contract and observation mistakes

The earlier archive contains useful lessons that the later candidate screen does not replace. They concern how to establish that a shader change actually does what its source suggests.

| Lesson | Historical observation | Consequence for new work |
| --- | --- | --- |
| Verify the shipped directive contract | BSL used a dimension-prefixed viewport property that did not match the actual Iris program key; the requested half viewport remained full-sized | Check actual viewport and shipped dependency behavior before interpreting work reduction |
| Derive the producer/consumer sample lattice | Complementary's reduced producer and resolver had a quarter-cell phase mismatch under the tested even viewport | Validate reconstruction coordinates; passing scalar tests still does not establish visual quality |
| Observe loaded and executed state | Capability uniform presence and native options are different from actual branch work | Keep configuration, linked state and executed-work evidence distinct |
| Preserve native settings through a real round trip | A historical macOS Reimagined/Replete check restored 398 loaded Iris options | Reuse the restoration test idea; it is not current-platform PBR qualification |
| Correct setup defects before judging speed | Corrected Bliss viewport sanity remained flat/inconclusive | A repaired harness does not imply an optimization benefit |

## Correction: historical sampler observations (2026-11-10)

The initial readable import omitted an important qualification. The archived GL 4.1 audit found that indexed `SAMPLER_BINDING` queries did not establish sampler bindings on that API. Earlier returned zeros therefore do not prove an unbound sampler or effective filtering. This affects BSL replay, Complementary copy/raster, MakeUp clouds, Sildur/BSL receivers, and shared-probe Bliss/SSR observations. Texture/source joins, recorded pixel equality, and closed negative or quality decisions retain their narrower scope; the correction does not establish a new failure cause or reopen those trials.

The proposed repair selected the texture unit, used the non-indexed getter, and restored the prior active unit. It was staged only: compilation and fake-binding tests passed, but integration and actual driver execution remained unverified. It cannot repair old receipts retroactively. Future evidence must establish that the observation method is valid on the actual API before treating a returned zero as proof.

[Original sampler audit and staged repair](../sources/history/earlier-cross-pack-methods-05.json) sha256:a3798da626d0f6f56216ce9c8fd984140c39ddeed0d94c301063a8d1a2dbb805

## Conditions and limitations

Evidence level: source-inspected historical import. These records include static derivations and separate live checks on older pinned setups, including macOS/M3 Max at 1920×1200. They must not be pooled with Asahi or RTX campaigns. Historical reports identify missing runtime checks and quality limits. Some old raw paths may already be absent locally; the archived prose does not reconstruct missing captures.

## Decision

Retain the contract and test-design lessons. Discard any assumption that old experimental settings or instructions are current defaults. No old implementation is enabled by this import.

## Evidence and executed source

[Selected original reports](../sources/earlier-rendering-lessons.json) sha256:2cb0dea6bf6516a6c053cd838ed1deb2e8f7b3922b8730cdc6cf3760f75e1f53

[Searchable index of the complete earlier archive](../sources/earlier-archive-index.json) sha256:c65724516babf780dab661d736b8c73ae8ffbeb6715bbf71619cdd93fc90b91a

Compact source reports and retained measurements are in the [historical evidence catalog](../historical-evidence-extraction/catalog.md). Original paths inside those records are provenance labels, not file dependencies. See [historical evidence access](../../docs/results.md#historical-artifact-access) for source recovery and intentional limits.
