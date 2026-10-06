# Distant irradiance spatial-rate prototype

2026-10-05 source freeze in isolated `lod-lighting-rate` worktree, base `67ef5ba`.
This is an implemented, assembled **default-off approximation**, not the earlier
constant lighting cost control. Main's frozen cost probes and Java/runtime files
were not edited. Shader/driver compilation, visual quality, compatibility and GPU
benefit remain unverified.

The prototype evaluates original diffuse illumination once at a static corner
of a valid distant 2×2 receiver tile, then resolves each original full-resolution
material/albedo. It caches an irradiance coefficient, never final scene color.
Original current-pixel specular functions, edge derivatives, fog and composition
remain common. Representative AO×shadow visibility is shared for highlights;
that spatial approximation is explicit and needs visual acceptance.

## Source and stage graph

Original Photon v1.3b by Benjamin Stott/SixthSurge, artifact `gUv7fBPN`, base ZIP
SHA-256 `120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Original LICENSE SHA-256
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5` is preserved.
Derived prepass code retains Photon attribution. The copied common continuity
helper retains its MIT notice and exact source hash.

Nine bounded upstream members change: settings/properties/buffers, original d4
fragment program, PCSS/SSRT helpers, and three dimensions' d4 fragment wrappers.
Nine new overlays provide six d5 wrappers, the prepass, V2 policy and continuity
helper. The manifest pins every original/modified member and every overlay.
The builder rejects changed sources, ambiguous anchors, added overlay files,
collisions, license drift and outputs outside ignored `build/`.

| Private/profile state | Stage 4 | Stage 5 | Buffer 2 |
| --- | --- | --- | --- |
| Option OFF or unsupported pack features | Original full viewport/full original shading | Disabled | Original RGBA16 |
| Option ON, supported graph, Overworld | Half viewport irradiance prepass | Full-resolution original-material resolve | Full-size RGBA16F; lower-left quarter stores half-resolution cache |
| Same private graph, Nether/End | Half viewport writes explicit invalid alpha | Full original dimension shading | Transient cache unused by resolve |

`scale.deferred4=0.5` is pass-wide, so all dimensions get the numbered graph when
private ON. This prevents shrinking an untouched Nether/End d4 shader. Extra
invalidating pass overhead in those dimensions is unmeasured. No compute stage,
subgroup, new logical render target or cadence change is required.

Prepass output is `RENDERTARGETS: 2`. Resolve is the new numeric `deferred5` entry
while retaining `PROGRAM_DEFERRED4` shader semantics. Iris's existing main/alt
render-target flip machinery supplies the prepass output to resolve; actual
attachment/flip and driver state still require runtime inspection. Every visited
cache texel writes valid data or alpha −1 each frame. Odd viewport-edge tiles use
original lighting. No prior-frame cache or implicit clear is trusted.

Upstream gbuffer1/buffer2 is unused by opaque shading with normal/specular maps
OFF until optional TAAU preparation; the prototype requires TAAU OFF. Mapped
materials, colored shadows/VPS/colored lights/LPV/cloud shadows, SH skylight,
DH/VOXY, blocky clouds and true environment SSR select the original graph.
Sky-only reflection functions remain full rate. Ordinary Iris supports logical
0…15; no Voxy-specific buffer16 is borrowed. Buffer2 is a spare logical slot, not a claim that its storage was already
allocated: Iris can create it lazily. Two full-size RGBA16F ping-pong textures can
add about76MiB at2976×1672, plus bandwidth/cache effects. One additional resolve sampler is
needed; the 14-dynamic-unit Mac limit and actual linked sampler set must be
validated, not inferred from declaration counts.

Buffer directives in Photon are inside a block comment. The private/original
format selection wraps the whole comment **outside** it; actual pinned Jcpp and
Iris constant-directive tests prove a single RGBA16F or RGBA16 directive survives.
Stage wrappers load settings before selecting prepass/original. Disabled shading
functions and graph are original; raw wrapper bytes/include order change, so do
not call a patched-disabled archive byte-identical to upstream.

## Actual V2 control consumption

Requires core protocol2/global enabled1 and effects protocol2. Producer is the
new OpaqueLighting interface from `2892035`/main integration `0a4d7a9`.
No producer Java was copied into this shader worktree. Six existing core names
and seven effect names are declared; source usage is distinct from linked/runtime
consumption, which has not been demonstrated.

- INHERIT0 uses explicit opt-in private-pack policy: core near/far/transition,
  medium retained ratio1 and far ratio0.25. Opaque custom bands/ratios are unused
  in this mode. Shipped private option OFF preserves original rendering.
- FULL1 uses original lighting. Unknown versions/modes or invalid fields also
  use original lighting.
- ADAPTIVE2 uses `shaderlodOpaqueLightingNear/Far/Transition`,
  `MediumWorkRatio/FarWorkRatio`, and `Mode`; fields retain the exact V2 names.
  Finite nonoverlapping centered bands in radial view-space blocks are required.
  Width is bounded by `min(2*near, far-near)`.

Legal realized rates are K4/original and K1/one representative per four-pixel tile.
Ratio1 requests full rate; a positive ratio≤0.25 maps to the legal minimum K1.
Zero and intermediate K2/K3 requests conservatively fall back to full rate.
This is an intentional adapter subset, not arbitrary integer-cap consumption.
Near remains original. In centered transition bands the resolve computes both
original and cached lighting and blends them; that extra work is part of the
measurement. Fully valid/far pixels execute only cache lighting at resolve.

For control testing after producer integration:

```text
/shaderlod effects opaque_lighting mode adaptive
/shaderlod effects opaque_lighting bands 32 96 8
/shaderlod effects opaque_lighting ratios 1 0.25
```

Debug1 uses the existing core debug field to mark cache-influenced opaque pixels
green and fallback pixels red after common composition. Sky/hand remain original.
Later postprocessing alters colors; this is qualitative visibility only, not
physical counts, quality parity or performance. Debug0 is required for timings.

## Representative correctness and quality boundaries

Static phase `(0,0)` is fixed in the 2×2 screen tile; original projection/jitter
and current-frame inputs are retained. PCSS and SSRT noise lookups use the actual
representative full-resolution pixel rather than the prepass's half-viewport
fragment coordinate. The same pixel context is initialized to current coordinates
for full-rate resolve. Only private Overworld comparison sites use explicit
shadow level0; original off/unsupported sources retain original sampling.
Source no-mip/linear shadow-policy evidence exists; actual bound sampler and
near/OFF parity remain driver checks.

Each member rejects sky/hand/invalid depth/overlays and nonzero material masks.
Approved ordinary mask0 has no SSS/emission/metal/reflection behavior under the
strict profile. Roughness≥0.75 is checked cheaply using the pinned mask0 formula;
`rgb_to_hsl().z` is actually maximum RGB in this source. Representative decoded
material fields are independently checked before canonical-white factorization.
Source cannot identify every moving entity by a dedicated gbuffer flag; animated
geometry that meets these predicates needs motion QA.

The shared helper is copied from
`shaderpacks/common/shaders/include/shaderlod/surface_continuity.glsl`, SHA-256
`2fff58476c6d49d40eb61e5f6683dc58697ed964f128a3f444442677b06327b0`.
Caller provides matching view-space positions/normals, material IDs, lightmaps,
protected flags and the protected near-band start. The frozen helper rejects
mixed/singular/nonfinite geometry, normal/material/lightmap discontinuity and
plane residuals with footprint-scaled tolerances. Before shadow evaluation the
representative AO-neighbor span must be≤0.10. Invalid cache RGB is reset before
zero-weight mixing, preventing NaN contamination of original fallback.

The representative retains original AO reconstruction, filtered PCF/SSRT,
lightmap and diffuse calculations. With zero SSS/sheen/emission/nonmetal material,
canonical-white diffuse output is an albedo-independent coefficient; each resolve
pixel multiplies its actual albedo. Alpha stores representative scalar shadow×AO,
so current-pixel highlight BRDF/direction/material are retained but their visibility
is approximated. Half-float coefficient precision is also a change.

Geometry agreement and AO span do not guarantee cast-shadow-edge continuity.
Optional shadow spread is unavailable; no such guarantee is claimed. Thin shadow
patterns, moving foliage/casters, contact shadows, glint, texture/material edges,
camera motion/TAA and config transitions require matched QA. Conservative mask,
roughness and geometry guards may make coverage small. Invalid tiles add
classification work before full fallback; transition bands add representative
work plus original shading. No net speedup is implied by source evaluation rate.

## Completed checks and next runtime handoff

```sh
python3 -m unittest discover -s tests -p 'test_photon_lighting_rate_probe.py' -v
python3 scripts/build_photon_lighting_rate_probe.py --arm original
python3 scripts/build_photon_lighting_rate_probe.py --arm disabled
python3 scripts/build_photon_lighting_rate_probe.py --arm enabled
```

Eight tests passed in0.843s, no skips. Checks include immutable source/overlay
pins, complete archive preservation and deterministic actual ZIP assembly,
current-material/common-specular source, representative math/seed mapping, policy
boundaries/unsupported rates, invalid-cache fallback, copied continuity helper,
and actual pinned Iris lexer/Jcpp/directive/property parsing.

The CPU fixture invokes actual Iris `PropertiesPreprocessor`, `ConstDirectiveParser`,
viewport parser callback and program-name directive parser. Private immutable-data
constructors avoid the unrelated Minecraft error/UI/platform initialization in a
full shader-pack lifecycle. Existing pinned Minecraft/Guava/Fastutil/Brigadier/JOML
and embedded Jcpp dependencies are used; no game or GL context is launched.
This establishes property-source interpretation, not shader compilation/rendering.

Three actual ZIPs are under this worktree's ignored `build/`; hashes are recorded
in the root handoff. Metadata `shaderlod-lighting-rate-probe.json` records exact
upstream/overlay/member/manifest/license provenance, target2/viewport0.5, V2
control distinction, realized rates, temporal/transition behavior and honest
unverified quality/driver/GPU status. Keep archives fixed across runtime controls.

Root/runtime operator owns client/reloads/GPU. First compile original, option-OFF,
then private graph with producerV2 FULL/globalOFF, followed by ADAPTIVE1/0.25.
Verify actual stage ordering, viewport, buffer2 floating format and flip/no feedback,
linked field types/values, sampler headroom and visible eligible surfaces with
untimed debug1. Then check near/far/edge/motion/config toggles, ordinary and
Nether/End fallback, plus DH-installed original fallback separately. Timed runs
must be quiet/focus-matched, debug0, balanced original/OFF/ON with equal scenes,
preset/resolution and warmup. Record GPU and total-frame median/tail/variability;
no performance claim without matched original and optimized GPU timings.

## AO union repair and prepared forest controls — 2026-10-05

The independent quality review found that the representative's four AO taps
missed outer right/top taps used by the other tile members. That guard was not
sufficient for the full tile. The repaired prepass validates the full 3×3 union
with texture-size/bounds, equal AO/depth dimensions, finite normalized values and
AO span≤0.10 before the unchanged original representative reconstruction. It adds
up to9AO+9depth classifier fetches per candidate tile, then the original8rep taps.
Cast-shadow-edge continuity remains unaccepted; source checks do not establish
quality or a gain. The initial d41/713 source/archive pins above are superseded.

Frozen repaired manifest SHA-256:
`683cee198706d7289ef3994212edace9fd200ffeebff458e1c317a306751b3d4`.
Prepass overlay SHA-256:
`f4fb70f0deeafc3ffbfa5459dc57f632edc78bdd04f8b5c88c3391c384ac927b`.
The single private default-off archive for all private controls is
`build/Photon-LightingRatePrototype-disabled.zip`, SHA-256
`fca1a3115ad7cb5341807d7a06cb6939228da4603200fa831b5bdefd6eed206a`;
complete private metadata SHA-256
`7af75702a3d2abb33426eb66a02c18016def818d0a3d00849c84962cde945fa2`.
No frozen source was silently repaired during profile preparation.

New source-only preparer `scripts/prepare_photon_lighting_rate_profiles.py` reuses
existing stopped-save/copy utilities without editing their queues/harnesses. It
checks full archive inventory and every upstream/overlay payload, complete pinned
metadata, helper/license, held options/defaults and supported graph. Omitted
public flags resolve from the actual pinned source defaults; DH/VOXY are
compile-environment expectations, not invented shader-option defaults.
The canonical forest report had DH installed. Every fresh profile physically
removes only the qualified copied DH jar, and actual future launches must also
use the minimal noDH classpath. Static profile creation does not prove DH absence
in a running JVM.

Prepared controls explicitly request `effects.opaque_lighting` ADAPTIVE,
32/96/8 blocks, ratios1/0.25 and **preserveSalientLighting=true**. Source enum and
producer normalization show OpaqueLighting is non-salient, so this true flag does
not force its transport to unity. It remains true for all effects; shadow/AO
defaults stay protected. The earlier assumption that this experiment required
false was corrected from the actual producer rules. Requested config and expected
transport remain distinct from observed shader consumption.
A complete seven-effect config is written to the registered
`shaderlod-effects-client.toml`; other effects remain INHERIT/unity/true.

Actual preparation command completed without launching a client:

```sh
python3 scripts/prepare_photon_lighting_rate_profiles.py \
  --output run-workloads/photon-lighting-rate-controls-r1 --cue --option-off
```

| Relative profile under that root | Mod | Private option | Global | Debug | Purpose |
| --- | --- | --- | --- | --- | --- |
| `forest/high` | absent | absent | false stored/unused | false | Exact original Photon baseline |
| `forest/patched-off` | present | true | false | false | Private graph, global OFF |
| `forest/adaptive` | present | true | true | false | Private graph, explicit ADAPTIVE request |
| `option-off/forest/adaptive` | present | false | true | false | Same private ZIP, original-graph compilation/parity control |
| `coverage/forest/adaptive` | present | true | true | true | Separate untimed qualitative coverage cue |

All five copied saves match canonical SHA-256
`f752339d854ff2f0e0a7dc4e577dd6aedf2d0f69e39a9a7cae9dd0286c7be08b`.
The source save, source DH jar, history and configuration remain preserved.
All four private profiles use the same immutable `fca1…206a` ZIP. Source pose is
x256.5/y95/z768.5, yaw135/pitch9, time7000/clear, native2976×1672 and24chunks.
The held source report and option hashes are respectively
`6f7662a31427f0d0863d926909c67a57b0dfba3c7ee2539c857f699c7ce3da5e` and
`4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745`.

Final preparation marker is
`run-workloads/photon-lighting-rate-controls-r1/photon-lighting-rate-preparation.json`,
SHA-256 `0f78fd4d022914b53b21ab527006db37b6d4e5aef0ad4f752e0fe908579c5d45`.
Each arm records actual config/sidecar/input/archive hashes and equal-world
provenance. A separate copy-stage marker cannot masquerade as finished config
preparation; failures in the config stage retain a failed final marker.
The normal/cue effect config hash is
`ac9cbd358b15124d21f14149861c0f0dc1634eb504a57a09f15e210a51739e9f`.

Seven preparation fixtures passed in0.383s. They exercise actual frozen source
qualification, held/missing-default options, unsupported graph rejection, producer
protection semantics, fresh-path safety, stopped-copy invariants, actual per-arm
flags/config hashes, optional option-OFF/cue controls and retained failure state.
No shader/client/reload/GPU/timing work ran. Copy session23279 exited0; the runtime
operator was notified after it became terminal, before its independent launch.

Runtime handoff: begin with original and the separate option-OFF root, then
private graph/globalOFF, explicit ADAPTIVE and separate untimed cue. The optional
roots use the standard `forest/adaptive` path beneath `option-off` or `coverage`,
so existing launch plumbing can select those roots without a new case/queue.
Cue `timing_allowed=false` is recorded intent, not new harness enforcement; the
operator must keep GPU timing disabled there. Verify actual noDH stack, source
hashes/options, six core+Opaque V2 types/values, format/viewport/flip/sampler state
and qualitative eligible coverage before timing. Quiet/focus-matched debug0
balanced original/OFF/ON measurements and full quality acceptance remain pending.
