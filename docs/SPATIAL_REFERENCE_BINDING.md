# Photon shared spatial source and layout binding

2026-10-05. Source/runtime integration is frozen for the parent build and driver
check. No shader compilation, actual allocation, quality, savings or project-goal
acceptance follows from this registration.

[`PhotonSpatialReferenceAdapter`](../src/main/java/com/pp/shaderlod/integration/PhotonSpatialReferenceAdapter.java)
registers the explicit Photon v1.3b consumer through the general adapter API.
Its identity is `shaderlod:photon-shared-spatial`, revision
`v1.3b-shared-spatial-bd9fbf3f70bb117a1825f630cc3a20e34089bd7bf9f8dd14377e2cc72ac582fa`.
It declares only opaque lighting RATE operation
`shaderlod:opaque_spatial_irradiance`, exact retained rates `{1, 0.25}`, and export
`shaderlodOpaqueSpatialAvailable`. Core 2, effects 2 and capabilities 1 are required
explicitly. The unchanged volume reference and external startup registration
remain available. Composition has no filename matching, source discovery or shader
rewriting. General registration and layout validation contain no Photon algorithm.

## Exact source and options

The independently frozen ledger is `build/photon-shared-spatial-freeze.json`.
Its exact OFF/ON container, member and option pins are copied into Java, not read
from generated metadata at runtime. Each derivative binding pins all 25 members,
including LICENSE, buffers/settings/properties, three world producer/consumer
entries and vertices, original lighting edits and every consumed shared helper.

| Arm | SHA-256 | Spatial qualification |
| --- | --- | --- |
| Original | `c4e956dfee9ceebb6df245ea4792f5626cbc2d0ff995ccf9b49766d2286b57f1` | No binding; capability unavailable |
| Private OFF | `a8c004f5be0b341ed117220da0d2e2a0dadeefd62fd899ff64a3100c19a54ceb` | Explicit effective ON override required |
| Private ON | `c2790b61bd4d096e24eedbf9ca800d35541ca81e8ec5efeecec122b764dbf6f0` | Compiled ON default can qualify |

Both derivatives require loaded Iris `SHADERLOD_SHARED_SPATIAL=true` and these
13 loaded options false: `BLOCKY_CLOUDS`, `BORDER_FOG`, `CLOUD_SHADOWS`,
`COLORED_LIGHTS`, `ENVIRONMENT_REFLECTIONS`, `GALAXY`, `NORMAL_MAPPING`,
`SHADOW_COLOR`, `SHADOW_VPS`, `SH_SKYLIGHT`, `SKY_REFLECTIONS`, `SPECULAR_MAPPING`,
`TAAU`. Values are exact and case-sensitive. A missing discovered key cannot be
substituted with configuration bytes or a guessed default. Unknown archives,
changed pinned members, incompatible protocols and unsupported/missing options
remain full work. DH/VOXY, other-world, water/material/environment and policy
restrictions are additional frozen shader guards; capability availability alone
does not prove those guards pass or that quarter work executed.

## Optional generic runtime layout requirement

[`RuntimeLayoutRequirement`](../src/main/java/com/pp/shaderlod/integration/RuntimeLayoutRequirement.java)
contains an immutable declaration and inclusive minimum/maximum surface dimensions.
`AdapterRegistration.register(descriptor, sources, exports, layoutRequirement)`
and the corresponding public `IrisAdapterRuntime.register` overload are additive;
existing three-argument registrations carry no requirement and retain their
previous semantics. Sealing copies the adapter-identity-to-requirement map.
Registration remains atomic and startup-only; no protocol or config default changed.

At the existing shared frame snapshot, source/protocol/options/pipeline selection
runs before the optional layout gate. `RuntimeAccess.renderedSurface()` defaults
to unavailable. A registered requirement with unavailable dimensions returns
`LAYOUT_DIMENSIONS_UNAVAILABLE`; a failed domain/declaration check returns
`LAYOUT_INCOMPATIBLE`. Both snapshots contain no available operations. Validation
is cached once per validated pack identity and last equal dimensions; resize
revalidates. Pack reload establishes a fresh source/layout cache. No hashing or
source I/O occurs per frame, and suppliers still read only `EffectUniformFrame`.

Pinned Iris 1.8.12 bytecode proves `ViewportUniforms` reads
`Minecraft.getInstance().getMainRenderTarget().width/height` for `viewWidth` and
`viewHeight`. `IrisRenderingPipeline` passes those same actual main-image dimensions
to its `RenderTargets` constructor and `resizeIfNeeded`. `IrisRuntimeAccess` uses
this seam; it does not use window/display dimensions or call GL. Nonpositive or
absent targets return unavailable. Read-only evidence, including exact Iris JAR
digest and complete disassembly:
`dev/logs/spatial-reference-binding-iris-dimensions.log`.
This proves the inspected API relationship; resize ordering and actual target
allocations remain driver checks.

## Declared affected pass graph and certificate storage

All three declared targets and both pass viewports resolve to full rendered
dimensions. The producer draws logical **0/2**; the consumer draws **0/3**, with
the original conditional target-0-only branch also declared.

| Logical target | Format | Declared quantity roles |
| --- | --- | --- |
| 0 | `R11F_G11F_B10F` | GENERAL completed original scene RGB, 3 channels |
| 2 | `RGBA32F` | Linear basis, 4 channels; bounded exact binary32 integers, 3 channels; binary32 minimum distance, 1 channel |
| 3 | `RGBA8` | GENERAL original auxiliary data, 4 channels |

Quantity views occupy different texels and their channel requirements are not
summed. In each 2 × 2 tile, `(0,0)` stores the four-component canonical-white
diffuse/AO-shadow basis, `(1,0)` stores
`(epoch, quantity_side_token, tile_linear_index, minimum_distance_blocks)`, and
the other lanes are invalid. Exact integer magnitude is explicitly bounded by
`2^24`: epoch `[0, 2^24]`, token `258`, tile index `[0, 2^24 - 1]`. Direct minimum
distance uses binary32. Surface dimensions must be `[2,8192]` per axis; thus CEIL
half-tile capacity is at most `2^24`. Integer texelFetch is declared for these
reads. The unchanged source guards reject mismatched texture dimensions, epoch,
certificate, invalid/nonfinite quantity and partial odd-edge tiles.

This Java declaration covers the affected cache/composition/auxiliary graph.
It does not claim to enumerate every unchanged upstream allocation or pass.
The separately accepted frozen source fixtures inspect all preserved upstream
graphics groups; their evidence and qualification remain in
[SHARED_SPATIAL_REFERENCE.md](SHARED_SPATIAL_REFERENCE.md). Neither source fixtures
nor this declaration prove actual driver allocation or shader consumption.

Producer reads and writes logical 0/2; consumer reads logical 0/2/3 and writes
logical 0/3. The declaration therefore explicitly requires
`DISTINCT_IMAGES_EXTERNALLY_VERIFIED` for logical feedback. It does not establish
that verification. The renderer must supply physically distinct input/output
images for every overlapping logical identity, complete the producer before
consumer cache reads, and preserve the required formats, viewports and fetches.
The frozen shader `shaderlod_photon_cache_contract()` remains an independent
runtime size/epoch guard. No declaration approval is actual driver allocation.

## Checks and integration handoff

Isolated Java 21 `javac --release 21 -proc:none` compilation plus JUnit Platform
execution passed **63 tests**, zero failures, skips or aborts. This includes eight
pure spatial cases and three explicit artifact-qualification cases, the existing
source/runtime/extension/frame/effects cases and
20 generic layout cases. Actual frozen OFF/ON archives and all 25 members match
the ledger; pinned Iris parsing proves effective default and override behavior.
Other checks cover original/unknown/changed-member full fallback, every missing
or unsupported option, exact legal domains/protocols, mixed-format storage,
invalid viewport/storage/domain, unavailable/out-of-range dimensions and resize,
atomic conflicts, immutable maps, volume preservation and external registration.
The two existing mixin/production expectations now include the third reference
capability export with safe initial value zero; core/effect counts are unchanged.

Raw log, source hashes, exact temporary output and launcher/classpath location:
`dev/logs/spatial-reference-binding-java21.log`. Only existing NeoForge
EventBusSubscriber removal warnings were observed. No Gradle, shared production/dev
class-resource mutation, client launch, shader reload or GPU check ran. No live
process remains owned by this worker. The parent owns the idle build/client/GPU
window and final integration acceptance.

Ordinary [`SpatialReferenceBindingTest`](../src/test/java/com/pp/shaderlod/SpatialReferenceBindingTest.java)
uses only tracked declarations and synthetic fingerprints. Its eight cases also
passed with the JVM working directory set to an empty temporary folder, containing
no private archives or ledger. Artifact qualification lives outside Gradle's
ordinary test source set in
[`SpatialSourceQualificationTest`](../tests/fixtures/java/SpatialSourceQualificationTest.java).
The isolated launcher explicitly compiles/selects that fixture. Its three cases
require the real generated ledger and all three frozen ZIPs; missing artifacts
fail assertions, with no assumptions or skip allowances. The source Python
fixture remains responsible for deterministic private assembly. A separate
missing-artifact control selects only that fixture from the empty directory and
fails all three cases as intended, with zero skips:
`dev/logs/spatial-reference-binding-missing-artifact-control.log`.

Next checks: coordinated production test/build and dev compile; exact artifact
freeze; actual producer/consumer typed capability/core/effect readback; effective
options and expected full/quarter paths; physical MRT 0/2 and 0/3 formats/sizes/
viewports, every feedback texture ID and producer completion; sampler limits and
filter/compare/LOD state; valid and invalid cache outputs; resize/source/world/
options/epoch/odd-edge fallback; original/OFF parity and representative still and
motion quality. Repeated matched quiet GPU plus total-frame measurements remain
necessary for the 1.5 × throughput goal. No goal completion is claimed here.
