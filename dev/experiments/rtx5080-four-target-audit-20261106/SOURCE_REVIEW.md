# Current native source review

These findings concern the pinned native option maps and current original4K
Overworld graphs. Source applicability is separate from actual execution, image
quality and performance. Pending lifetime evidence is not a zero speedup.

The [frontend comparison](frontend-parity.json) verifies all24 captured attached
terrain stages (SOLID/CUTOUT/translucent vertex/fragment for four packs) against
Asahi's original baselines, and exact equality of the full option maps. Fullscreen
program objects are linked, but their detached attached-stage text is unavailable
in this exporter; they are not included in that parity claim. Parsed ProgramSource
exports and actual bound programs/targets remain separate evidence. GPU binaries,
physical image lifetimes and performance are not transferred from Asahi.

MakeUp G02 has two native mip declarations. composite.vsh reads colortex1 at
log2(min(viewWidth,viewHeight))-1 for exposure, which is positive at3840×2160.
composite1.fsh passes colortex0 to mipmap_bloom, whose samples use positive softLod.
Both vertex and fragment consumers matter; inspecting only composite.fsh's LOD0
read would incorrectly call the first chain dead. Current native options retain
exposure/bloom. No omission is justified by the present inventory.

MakeUp G09 has no native POM traversal/height-map consumer in this profile. The
actual terrain sampler interfaces and source remain the original base-color,
lighting/shadow interfaces. A POM-cache request cannot demonstrate work savings
where no native POM query executes. Other targets' separately pinned PBR profiles
remain real opportunities and are included in the comparison definitions.

C01's native MakeUp SOLID stages match its existing complete source requirements.
The other three SOLID vertex stages differ: BSL calls WavingBlocks before projection;
both Complementary stages call DoWave and include an additional material depth
adjustment. This is not a missing NVIDIA capability. Their geometry needs its own
conservative bounds/material and raster proof before reusing the MakeUp query
permission. A simple host-list expansion would be incorrect.

BSL's native composite4 bloom kernel uses a6×6 offset grid; both Complementary
kernels use7×7 grids. Distinct offsets have nonzero pixel spacing at4K. The MakeUp
G10 exact-coordinate reuse proof does not establish duplicate queries in these
kernels. Edge clamping, cross-tile implicit LOD/derivatives and approximate paired
filtering require separate proofs; no unmeasured gain or generic impossibility is
claimed. The exact MakeUp kernel and its native-equivalent control remain scheduled.

BSL's VolumeLightReuse flag has no operative native consumer in the current route.
Its historical primary-cloud density bound instead pins clear-weather Balanced
and an older target integration. It is not silently substituted for the present
native/Performance lighting-reuse test. Likewise, G04's MakeUp contribution guard
has no already qualified cross-pack equivalent for the other three outputs.

Complementary's identity-pass prototype remains a real source opportunity, with
matching WORLD_BLUR=0/MOTION_BLUR_EFFECT=-1. Its own launch certificate additionally
requires complete physical bank/history/clear and lifecycle/image evidence.
Existing original/next-frame native inventories will be examined before any
omission; the historical held status is not an inherited RTX5080 performance result.
N02/N03 similarly need complete read/write/history and custom-texture ownership.
The overlapping MakeUp copy route gets no additional independent credit.

C02 now has a finite source/target extension for the two separate Complementary
bloom outputs. See [port scope and validation](C02_PORT.md). MakeUp's multiple/full
scene outputs require a different isolated producer; the BSL pass predicate cannot
be broadened by name alone. Actual NV capability/work, quality and timing remain
pending on all three executable bloom targets.
