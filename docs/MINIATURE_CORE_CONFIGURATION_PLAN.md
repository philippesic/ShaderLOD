# Miniature existing-core configuration plan

Frozen source-only plan, 2026-10-05. This task creates only this document. No
shader, patch, builder, Java/config/transport, shared helper or existing adapter
is edited; no download, build, compiler, client, encoder or GL call is made.
The original chat retains main transport/shared/Photon ownership. Root owns
future private adapters and all runtime work. The ordinary ≥15% performance
gate and broader configuration, quality and support acceptance remain unmet.

## Existing contract and evidence

Read-only inspection covered the exact Miniature release, core cue and private
refinement sources/builders/manifests/licenses, Java LodPreset/LodSettings/
ShaderLodConfig/CommonUniformsMixin and the existing Photon distance convention.
Uniform registration provides seven core fields; no per-effect mod transport
exists. This proposal consumes the existing fields in a Miniature-only helper;
it does not supply a framework, universal rewriting, new protocol version,
effect-count uniform or cross-pack preset equivalence.

| Core field | Type and actual sender | Proposed private consumer |
| --- | --- | --- |
| shaderlodBridgeVersion | int; constant2 | Accept exactly2; all other versions select original |
| shaderlodEnabled | int; snapshot boolean →0/1 | Zero restores original normal SSR |
| shaderlodPreset | int; PRESET.get().ordinal() | Exactly0/1/2 select documented budget table; reject unknown |
| shaderlodNear | float; snapshot.near() | Central full→middle threshold, radial view-space blocks |
| shaderlodFar | float; snapshot.far() | Central middle→far threshold, same units |
| shaderlodTransition | float; snapshot.transition() | Total width in blocks of each centered transition |
| shaderlodDebug | int; snapshot boolean →0/1 | Live diagnostic flag behind separate default-off shader diagnostic permission |

LodPreset's current order is **BALANCED=0, PERFORMANCE=1, AGGRESSIVE=2**.
Its Java comment explicitly makes ordinal0/1/2 the protocol2 contract; default
PRESET is PERFORMANCE. These are the sender's actual names/values, not
Miniature's native LOW/NORMAL/HIGH profiles. The proposed budgets below are
pack-local experimental meanings, not a statement that Photon or another pack
uses equivalent quality. Do not copy Photon's clamp(preset,0,2): an unknown
ordinal must restore original behavior, not silently become AGGRESSIVE.

Source inspection identities:

| Java source | SHA256 |
| --- | --- |
| LodPreset.java | 14474cbab94882637de61b6fc1616903a9b1697dcad9f161b289c0b20255017d |
| LodSettings.java | 08a0a9938b3aacda8f38111ee75f2cffa440bf41d7ccb57bcabf0d10025cc705 |
| ShaderLodConfig.java | b72aac8791592657ebee249c91fb10ef2591eb3669cff44df5b71070c7d553a9 |
| CommonUniformsMixin.java | 7e97be4ade495c6f59717c541e1a8782924bbdf283b1ac43c7dca64cc6f63980 |

All suppliers register PER_FRAME. Each distance/flag supplier calls snapshot
separately; the source does not establish an atomic seven-field configuration
transaction or instantaneous UI/file reload. Runtime acceptance must record
settled actual uniforms in the same linked draw after each requested change.

Miniature's existing color cue proved exactcore2/global/near/far consumption
in session21160. Normal MODE2 readback subsequently observed version2, enable1,
near96, far160 in linked program16, report SHA256
`33e724cc8dd4c4087888789768dd4ce2dc2703aa571c673a9b15e2c0cef4d01a`.
Neither proves Miniature preset, transition or debug consumption today; current
helpers do not declare/use those three fields. Linked values also do not prove
which refinement kernel/hit path executed.

Retain the negative/flat measurements from MINIATURE_REFINEMENT_PROBE.md:
original/off/full4 explicit GPU4.4219/4.5044/4.7083ms in one pilot; full4 versus
4/3/2 GPU4.5430/4.5432ms in the matched pilot. The latter is effectively flat,
not a speedup from its roughly0.95% FPS difference. Captured bank/water images
showed no gross failure; near pixel parity, thin/far reflections and transition
motion are not accepted. New configurability does not repair that evidence gap.

## Bounded proposed implementation

Create a new private derivative from the original release, preserving the
existing cue/refinement artifacts. Proposed new files, for a future explicitly
assigned implementation:

- scripts/build_miniature_core_config_probe.py
- patches/miniature/2.19-core-config-probe.json
- shaderpacks/miniature-core-config-probe/shaders/common/shaderlodCoreConfiguration.glsl
- tests/test_miniature_core_config_probe.py
- docs/MINIATURE_CORE_CONFIGURATION_EVIDENCE.md

These names are a scope proposal, not created files. Root can delegate that
partition; no shared/main edit is required. A dev-only readback extension, if
needed to capture all seven fields, needs its own root-owned file assignment;
it must not alter production uniform providers merely to manufacture evidence.

Proposed shader-only options, both default off:
SHADERLOD_MINIATURE_CORE_CONFIG_MODE (0 original,1 full4 explicit control,
2 fixed-four transition-quality diagnostic,3 configured savings trial) and
SHADERLOD_MINIATURE_CORE_DEBUG_ALLOW (0/1 permission for mod-debug overlay).
They are pack controls, not new live mod fields. The same private ZIP may hold
all modes; actual effective options and hashes must identify each arm.

Mode0 excludes every new helper/uniform/branch and restores original source
selection regardless of main enable/debug. Modes1–3 remain private opt-in and
native-NORMAL-qualified. Begin with quality diagnostic mode2; savings mode3
must not be promoted before transition quality is inspected. Mode1 is a full4
sampling/layout control, not an optimization. Unknown option modes fall back.

Normal eligibility is a dynamically uniform decision before active ray work:
exactversion2, globalenabled, knownpreset0/1/2, validbands, qualified native
SSR_BINARY_STEPS4/SSR_MAX_STEPS10/SSR_STEP_SIZE1.6 and verified profile/sampler
contract. Failure returns untouched original getReflectionColor, with its
native count/texture source. Nonfour native counts retain that native count,
not a forced4. Absent/old1/future3 cannot apply zero/default core values. Existing
normal rendering should remain original on global disable and restoration.

## Proposed preset mapping

| Actual ordinal/name | Full near | Middle | Far | Source meaning |
| --- | ---: | ---: | ---: | --- |
| 0 BALANCED | 4 | 4 | 3 | One refinement saved only on eligible far hits |
| 1 PERFORMANCE | 4 | 3 | 2 | Existing private 4/3/2 proposal, with reviewed transition behavior |
| 2 AGGRESSIVE | 4 | 2 | 2 | Reach the existing two-step budget in the middle; no new one-step far kernel |

All values are binary refinements after the original outer hit bracket, not
outer ray steps, ray length, shadows, lighting or reflection opacity. They never
exceed the original four. AGGRESSIVE differs meaningfully in its middle region
without introducing a new untested one-step kernel. A one-step far experiment
would require its own quality decision/evidence and is outside this first plan.
This table is a proposed quality budget, not an accepted product preset.

Native outer ten-step adaptive march, stride increment, offscreen/cutoff tests,
reflected direction, first bracket, roughness noise/world quantization,
vignette/fresnel, final-depth acceptance, misses and caller composition remain
unchanged. Reduced refinement can still change final hit acceptance, reflected
UV/color and thin silhouettes. Near keeps the original four-refinement math/
budget; active samples use the qualified explicit form, so exact near pixels
remain a driver comparison rather than a source promise.

## Physical units, validation and transition semantics

Java defaults: near32, far96, transition8 blocks. Snapshot repairs far to at
least near+1 and clamps transition to min(2*near,far-near). LodSettings rejects
nonfinite values, near≤0, far≤near, negative or overlapping width. Shader input
must independently reject invalid values; do not silently clamp malformed
core bands into a different quality setting in this new consumer.

Use positive bounded checks matching existing Java ranges: 1≤near≤4095,
2≤far≤4096, far>near, 0≤transition≤512 and transition≤min(2*near,far-near).
The conjunction rejects NaN via false ordered comparisons and infinity via
finite bounds; it needs no GLSL120 isnan/isinf assumption. Every validation
input is a uniform. Unknown ordinal or invalid bands restores original normal
SSR before any distance classifier. Java-repaired raw config values are valid
transported values; an invalid TOML edit cannot be assumed to exercise an
invalid-uniform fallback. Negative uniform cases require source fixtures or a
separate controlled diagnostic, never unauthorized production-provider edits.

Distance is length(viewPos) from the original screen2view reconstruction, in
camera/view-space Minecraft blocks. Preserve feet/world coordinates only for
the pack's existing roughness behavior. Do not use feet length, abs(z), depth,
world origin or the reflected ray endpoint for the core zones.

Adopt the existing core/Photon centered-width convention locally, without
importing its shared helper or temporal random selector. With N=near, F=far,
W=transition, full→middle occupies [N-W/2,N+W/2], middle→far occupies
[F-W/2,F+W/2]. W is each band's total physical width, not a fraction, a whole
near-to-far interval or a refinement count. Valid width keeps bands nonoverlapping
and the near band's lower edge nonnegative. Example96/160/W8 gives92–100 and
156–164. The full near output is assured through the lower edge; the centered
near transition intentionally straddles the ideal N threshold. Do not describe
every pixel below N as untouched full-quality output when W>0.

W=0 has hard, deterministic thresholds: d<N uses full, N≤d<F middle, d≥F far.
W>0 uses smoothstep weights inside each band and one pure budget outside them.
Where adjacent budgets are equal (BALANCED near, AGGRESSIVE far), collapse to
one result/sample path. No temporal noise, frame dither, new roughness pattern
or unverified temporal accumulator is proposed. Smooth weights are mathematically
continuous; they do not guarantee stable moving hit geometry or accepted motion.

Transition-quality mode2 runs **one** original outer march and **one** fixed
four-step refinement chain on eligible hits, retaining the states after2/3/4.
Select one endpoint outside bands; inside a band evaluate at most the two
needed endpoint results from that same chain. No two complete SSR functions
execute. This diagnostic saves no refinement-depth fetches and can add a color
fetch in bands; it is a quality control, never performance evidence.

Blend endpoint results as reflected premultiplied contribution and opacity,
then reconstruct the unpremultiplied return only when opacity>0. That preserves
the existing caller's mix(base,reflection.rgb,reflection.a*reflectivity*factor).
Straight independent RGB/alpha interpolation can darken a valid-to-miss blend
because a miss returns zero RGBA; do not use it as a transition shortcut. At
pure weights return the selected endpoint directly. Preserve each endpoint's
original final depth guard/vignette/fresnel rather than sharing an approximate
acceptance result. Validate this algebra and edge cases before rendering.

Only after quality acceptance should savings mode3 bound the same single
refinement chain by the maximum of the actually needed endpoint counts (≤4).
Classify before refinement fetches; a hit-local policy may avoid classification
for misses/cutoff failures while always using the originating viewPos. A bounded
maximum-four loop/early exit or reviewed literal body is possible. Explicit
depth/color LOD0 accesses are mandatory in the active divergent budget path;
do not infer valid implicit derivatives from the baseline's existing branches.
Endpoint storage, extra color fetches, divisions, divergence and compiler/
register pressure may cancel any saved depth work. No gain is predicted.

## Debug is a separate diagnostic contract

With config mode nonzero and DEBUG_ALLOW1, exactversion2 shaderlodDebug!=0
selects the private diagnostic. Debug permission defaults0 and removes diagnostic
code from benchmark builds. Leave the permission fixed1 while testing live
debug false→true→false, then use permission0 for performance arms. Unknown
versions cannot interpret the debug field; they retain original normal output.

Eligible debug uses the same requested budget/transition policy: full/middle/
far colors green/yellow/red (consistent with current mod debug intent), with a
documented budget brightness legend4/3/2. Invalid preset/bands under known2 can
show a distinct error status; disabled under known2 can show yellow/status,
but debug-on images are intentionally diagnostic and do not assert normal
fallback image parity. Mode0 and debug-off fallbacks remain original. Existing
standalone cue can test absent-version status independently; do not add a new
production protocol field for that purpose.

The overlay is restricted to original qualifying reflection pixels and adds
no sampler. It must show requested policy, not claim an eligible hit/refinement
actually occurred. Reflection misses can display a requested far budget while
skipping refinement naturally. Debug must be off for normal quality/cost checks.
Palette changes prove value consumption at most; physical work needs separate
source/pass/counter and GPU evidence. Native color should return when debug is
cleared without a shader-option/source reload.

## Exact future patch anchors and packaging

Base publisher release SHA256
5f224e907657f3ffd95d37e37c27e555deef64fa56da1e13374f2c295241d7f0,
not current Git source or a renamed old probe. Preserve exact CRLF bytes:

| Original member | SHA256 | Bounded anchor/action |
| --- | --- | --- |
| shaders/shader.h | 45996412988252ae8b9a00f7985234c80359fa4ba92ed78bdf04a07c68c4ae1a | Add default0 private mode/debug permission before first SHADOW_DARKNESS definition; retain original options |
| shaders/program/final.fsh | 6f87f795107ab99f94ea3be70510bf0138b138329cc4e6fa8e9224f01d70f2dc | Guarded private include after /common/getReflectionColor.fsh; replace only the unique getReflectionColor(depth,normal,viewPos) call with guarded dispatcher; retain mask/normal/composition |
| shaders/shaders.properties | 69ba499df39e90faa80cb1150c67f3d259ca1e6c0f25c55872fbc7991e5aa22b | Add explicit diagnostic/config submenu to exact REFLECTIONS_SCREEN line; no profile changes |
| shaders/lang/en_US.lang | 35c88cbc487d3eea837604945ca57d997f83ac7b0dcadf088a261357251092ca | Add option/legend comments after exact header; retain upstream translations |
| shaders/common/getReflectionColor.fsh | a8db323fe976b4c4c9635a53d0d11d752dfa9140e06de411db11ffa8c759f481 | Unchanged original fallback/vignette source; private body derives from pinned bytes |

Also pin/copy transformations99818a16… and mathc81d486d… unchanged, and verify all
other archive members equal originals. Exact literal anchors must match once;
do not locate effects with generic GLSL heuristics. Restore the existing full
MIT notice397d8024… and author/official URLs in the new derivative, retain
editable source/patch, and mark modified date and experimental contract.

Reuse the current evidence gate as a reviewed design, not by editing its builder:
original sampler reportb04ab014…/workload3e154928…, matching native NORMAL and
nonmip filters, object0/aniso1/bias0/base0. LOD±1000/MAX_LEVEL1000 is not a level0
clamp, and allocated mips remain unqueried. Verify the actual derivative state.
Inspected loaded Iris jar3e3f94d2… upgrades legacy/no-profile composite GLSL120
to330core and maps texture2DLod→textureLod. Previous full4 private syntax rendered;
new endpoint/loop/debug code needs its own driver compile. Source/translation
inspection cannot establish its compilation or sampler/pixel equivalence.

Builder must reject changed evidence/release/member/overlay/license pins,
missing/duplicate anchors, unsafe/duplicate entries, collisions and input
overwrite. Use fixed ZIP ordering/timestamps/permissions/compression; ship
manifest/core-source inspection identities, original ancestry and all unverified
runtime flags. Proposed output is an ignored renamed private ZIP, defaultoff.

## Required future source fixtures and runtime acceptance

Source fixtures must cover mode0 exact guarded-source restoration, original
function/mask/composition/roughness/outer-march equality, original native-count
fallbacks, exact enum mapping/unknown ordinals, old/absent/future protocol,
disabled restoration, NaN/infinity/out-of-range/crossed/overlapping bands and
zero-width/touching-width boundary ties. Check centered physical widths,
single-chain endpoint capture, equal-budget collapse and contribution blending
against native final composition, including valid-to-miss and zero opacity.
Reject new implicit texture calls in active divergent paths and >4 budgets.
Test artifact/evidence/source/license guards and deterministic small fixtures.
These are future tests, not performed or passed by this documentation task.

Root runtime evidence, separately from source fixtures:

1. Original/private mode0 and known2/globaldisabled/debugoff must render matching
   native behavior. Test native binary2/8, outer16 or stride1.2 fallbacks as
   their own native quality settings. Record actual uniform types/values,
   source/options/config/jar/class/scene/framebuffer hashes and driver failures.
2. Confirm all seven fields in the actual linked normal/diagnostic draw, with
   debug permission as needed to keep debug active. Cycle BALANCED→PERFORMANCE→
   AGGRESSIVE→BALANCED at fixed distances and native options; show requested
   budgets and retained near/reflected image. Registration/ordinal source alone
   is not per-preset shader execution evidence.
3. Hold near/far96/160, cycle transition0→8→32→0 while preset fixed. Inspect
   band edges at the stated distances and matched camera poses; then change
   near/far and return. Record actual GPU values after config settling, not
   just raw TOML. Validate repaired snapshot values separately from invalid
   uniform fixtures; no production transport injection is authorized here.
4. With debug permission fixed1, cycle existing mod debug false→true→false
   without pack reload, inspecting colors/legend and restored native output.
   Global enable true→false→true with debugoff must restore original native
   reflections and reselect the configured policy. Record reload epochs and
   inputs; no assumption of atomic config callback behavior.
5. Quality mode2 first: slow radial sweeps/shoreline translation, grazing/thin
   reflections, water/block/glass, miss boundaries, third-person/view bobbing,
   underwater and moving occluders. Inspect both transition bands and pure near
   parity. Stable weights do not excuse ghosting, darkening, edge loss or rings.
6. Savings mode3 only after quality passes: same inputs/presets and fresh sampler
   verification. Compare original/mode0/full4/quality-only/savings/native-lower
   controls in repeated balanced serial captures, debug permission0. Measure
   GPU median/tails plus CPU/frame clocks, preferably final/SSR pass evidence,
   and quantify eligible-hit coverage. Include a second ordinary scene and
   keep all poor/failed runs. No ≥15% or support decision from a best run/FPS.

DH has no Miniature programs and remains a separate compatibility question.
This plan does not authorize a supported derivative, new transport ownership,
default promotion or goal completion. First useful deliverable is a correctly
configured, quality-reviewed private consumer; meaningful avoided GPU work and
ordinary-scene performance remain independent evidence gates.
