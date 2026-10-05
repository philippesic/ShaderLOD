# Independent lighting-rate source review

2026-10-05. Reviewed integrated prototype at source commit `24f4d3d`, manifest
SHA-256 `d41c94e023d08fcf22798e29248d19ee294148f27008615742af78bcb6419cf4`,
prepass SHA-256 `457f060c9cd14cf796ac3af6b26e316bc2e1e00263b91d504a2df8fedc36f5d2`.
Original Photon ZIP is
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
This review inspected actual source, immutable replacements and canonical prior
runtime reports. It did not run a client, shader compiler, GPU capture or Gradle.
Source fixture success is not rendering, visual quality or performance proof.

## Actionable finding: AO edge check misses tile-member inputs

**Historical quality acceptance blocker; source repair recorded below. Not an
observed driver failure.**
`shaderpacks/photon-lighting-rate/shaders/program/shaderlod_lighting_prepass.fsh`
lines 203–219 read the representative's four AO/depth taps; lines 301–303 reject
only their scalar AO spread. The other three full-resolution tile members do not
necessarily use those same reconstruction taps. Away from screen borders, the
actual union is **3×3**, rather than the representative's 2×2 subset.

With TAAU off, a representative full-resolution pixel center `2N+0.5` maps to
`half_res_pos=N-0.25`, `i=N-1`, `f=0.75`. Its right tile member `2N+1.5` maps to
`N+0.25`, `i=N`, `f=0.25`. Thus the right member reads column `N+1`, absent from
the representative. The upper member similarly adds row `N+1`. Geometry, normal,
material and lightmap continuity can all pass while these AO values change.

A concrete scalar counterexample is equal reconstruction depths, representative
four AO values all 1, and AO 0.2 in the next column. Representative spread is 0
and its reconstructed AO is 1; the right member's original reconstruction is
`0.75*1 + 0.25*0.2 = 0.8`. The current cache can remove that contact darkening.
This is an exact source/arithmetic counterexample, not a captured visual defect.
Near-screen edges, GLSL `ivec2` conversion truncates negative fractional values
toward zero, so a fixed unconditionally offset 3×3 fetch is also insufficiently
specified. Bounds must use actual reconstructed member neighborhoods.

Bounded proposed fix: derive each member's original AO base index using the same
upstream coordinate expression; check the complete union with texture-dimension
guards, finite valid inputs and the conservative spread threshold before shadows.
Preserve original representative AO/depth weighting and diffuse arithmetic. A
scalar regression should cover the formerly missed outer column/row, zero/equal
spread, threshold endpoints, first/last/odd viewport edges and invalid dimensions.
Any extra classifier fetch cost must be included in whole-frame measurements.
Root assigned this repair to the quality worker after this review; old source and
archive hashes must not silently remain qualification pins for a repaired version.

## Strict profile eligibility and actual transport

Canonical forest report
`run-workloads/native-terrain-b15-r2/forest/high/workload-measurement.json` hashes
to `6f7662a31427f0d0863d926909c67a57b0dfba3c7ee2539c857f699c7ce3da5e`.
Its actual loaded option map hashes to
`4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745` and has
NORMAL_MAPPING, SPECULAR_MAPPING, SHADOW_COLOR, SHADOW_VPS, COLORED_LIGHTS,
CLOUD_SHADOWS, SH_SKYLIGHT, TAAU, BLOCKY_CLOUDS and ENVIRONMENT_REFLECTIONS all false.
These satisfy the pack-option part of the graph predicate. However, that exact
original forest report has **`dhPresent=true`**. A fresh explicitly prepared noDH
profile is required for this consumer; this review does not establish that the
original installed-DH profile selected the cache graph. Do not alter its settings
or infer DH absence from shader option values. Runtime must verify actual macros.

Canonical coastal report
`run-workloads/photon-ssr-cost-controls-r1/coastalwater/high/workload-measurement.json`
hashes to `9524f3fdc9dec30f59a03704ad45f9cffa84b2058fe2ed48ea6a3095c31ba252`.
Its loaded option map hashes to
`ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669` and has
**ENVIRONMENT_REFLECTIONS=true**. The override file omits that key, which does not
mean false. This coast must remain original-graph fallback; turning reflections
off to manufacture cache eligibility would change the comparison's quality.
Both reports recorded 2976×1672 and shader wetness 0. These are historical input
qualification records, not this prototype's runtime evidence.

The producer does **not** force opaque lighting to FULL or unity when protection
is true. `src/main/java/com/pp/shaderlod/lod/Effect.java:11` declares
`OPAQUE_LIGHTING("OpaqueLighting", false)`; the protection rule at
`ShaderLodEffectConfig.java:130` requires `effect.salientLighting()`. Only AO and
shadow-filtering identities currently satisfy that rule. Default
`preserveSalientLighting=true` is retained in requested policy but does not guard
opaque spatial reconstruction. The config comment explicitly describes preserving
sample budgets, not certifying spatial quality. No protection flag is exported in
the shader V2 interface. This is current code behavior, not authorization to
disable lighting protection in runtime profiles.

`EffectUniformsMixin.java:29` exports mode unchanged and lines 30–39 export the
normalized transport bands/ratios. In the private lighting shader:

| Request | Transport | Shader behavior with private graph/core2/global1/effects2 |
| --- | --- | --- |
| INHERIT, default unity ratios, protection true | mode0, ratios1/1 | Private pack policy ignores custom ratios; core bands and 1/0.25 apply |
| ADAPTIVE, ratios1/0.25, protection true | mode2, ratios1/0.25 | Legal K4→K1 far transition; protection false is unnecessary |
| ADAPTIVE, unity ratios | mode2, ratios1/1 | Full original lighting; graph/prepass still has overhead |
| FULL | mode1 | Full original lighting; private graph still has overhead |
| Global disabled or invalid version/packet | supplied mode; shader gate fails | Full original lighting within selected private graph |

Concrete fresh forest request for testing is `effects.opaque_lighting` mode
`ADAPTIVE`, near32, far96, transition8, mediumWorkRatio1, farWorkRatio0.25,
**preserveSalientLighting=true**, with global enabled/core2/effects2, debug1 only
for untimed eligibility and debug0 for timings. Other effects/protections stay
unchanged. The existing commands are:

```text
/shaderlod effects opaque_lighting mode adaptive
/shaderlod effects opaque_lighting bands 32 96 8
/shaderlod effects opaque_lighting ratios 1 0.25
```

Persist the requested file and verify linked transport values separately; commands
and source declarations do not establish consumption. Private option OFF is the
original graph. Private option ON/globalOFF is a distinct control that retains
graph allocation, invalidating prepass and full-rate resolve overhead.

## Buffer2, viewport and read/write review

The graph predicate is manifest line 8 and is repeated at the numbered wrapper,
properties and buffer format boundaries. In supported states, d4 writes only
target2 and d5 resolves full resolution using original `PROGRAM_DEFERRED4`
semantics. The prepass declares colortex2 at line 45 but has no active fetch from
it under maps-off scope; a declaration alone is not feedback. d5 reads buffer2
and writes original targets0/3. No source-level simultaneous read/write feedback
was found in the supported graph. Driver attachment/flip behavior is unverified.

Original d3 AO reads colortex1 when NORMAL_MAPPING is absent (pinned
`shaders/program/d3_ao.fsh:97`); its buffer2 read is maps-only. Original d4 maps
reads are also excluded. Original c4 TAA buffer2 sampling at line348 is in the
TAAU branch, excluded by this graph. Thus the inspected ordinary reduced profile
has no identified later live gbuffer1 consumer. Keep maps/TAAU/DH/VOXY exclusions;
this does not authorize widening the slot's lifetime to other configurations.

`scale.deferred4=0.5` scales the viewport rather than physical buffer2 dimensions.
At2976×1672 the cache region should be1488×836 in the lower-left of the full-size
RGBA16F target. `prepass:478–480` maps cache texels to full-resolution tile origins;
the resolve replacement at manifest line88 uses `texel/2` and an explicit
`2*cache_texel+1 < view_res` guard. Odd final rows/columns therefore remain original
and never sample the unwritten cache fringe. Every visited texel initializes
alpha−1 at prepass line474 before early returns. No stale cache or clear assumption
is needed if viewport/flip mapping is actually correct.

Actual target allocation, RGBA16F signed alpha, lower-left viewport, d4→d5 sampler
selection and nofeedback are required driver gates. Full-size ping-pong allocation
can add about76MiB at native resolution, plus bandwidth. Linking d5 adds a sampler
on a limited Mac GL implementation; source declaration counts cannot establish
active sampler headroom. No concrete linker failure was observed because no
driver compile was performed. The old actual-Iris parser fixture tests selected
directive/property interpretation, not complete profile graph rendering.

## Lighting semantics and handoff boundary

Canonical-white factorization is source-valid for approved mask0 with zero
SSS/sheen/emission and nonmetal behavior: original diffuse returns lighting times
material albedo at `diffuse_lighting.glsl:284`. Resolve retains each current-pixel
material/albedo and original specular functions/composition. It does share
representative scalar shadow×AO for highlight visibility, as documented, and
RGBA16F quantizes coefficients. These are quality changes requiring evidence.

Continuity receives matching view positions/normals and checks all four geometric
members before representative expensive work (`prepass:484–514`). Near/invalid,
sky/hand/mask/overlay/material/normal/lightmap/depth boundaries fall back. Distance
bands are radial blocks; transitions evaluate both original and cached lighting.
The helper's finite/plane checks do not license cast-shadow sharing on flat ground.
Optional shadow spread is passed unavailable. PCSS/SSRT noise uses the actual
representative full-resolution pixel; comparison sites use explicit shadow LOD0.
Actual sampler equality, near/OFF parity and spatial shadow/contact/motion quality
still require the sole runtime operator's checks. Full-rate shadow sample budgets
at one representative do not preserve full-resolution spatial shadow detail.

Proceed with bounded AO repair and new immutable pins, then an **untimed** fresh
noDH forest compile/binding/eligibility diagnostic. Coast remains fallback. Before
accepting quality, check contact/thin/cast-shadow edges, material/albedo parity,
foreground/sky/hand transitions, grazing slopes, motion/TAA and reload/config
changes. Before accepting performance, measure repeated matched original,
private/globalOFF and private/globalON whole-frame GPU and total-frame timings,
including prepass/classification/fallback/transition/memory overhead. This review
does not establish a speedup or progress toward the numerical target by itself.

## Resolved source finding: complete AO union

The assigned repair now derives all four actual upstream base indices (including
float-to-integer truncation at screen edges) and checks their complete bounded
union. `shaderlod_rate_ao_union_eligible` checks real colortex6/14 dimensions,
requires matching positive sizes, rejects incomplete viewport tiles and invalid
AO footprint bounds, then scans at most3×3 AO/depth pairs. Invalid/nonfinite data,
AO/depth outside normalized[0,1] or AO spread greater than0.10 retain original
lighting. The earlier representative-only AO-span check is removed. The original
representative reconstruction weights, sense check, hand handling and diffuse
arithmetic are unchanged; the source fixture checks the original AO block verbatim.

This classifier adds **up to9 AO plus9 AO-depth fetches** per selected tile before
the original representative's4 AO plus4 depth fetches. It may cost more than a
weaker classifier despite better eligibility correctness; count its overhead in
GPU comparisons. Near/right/top edge tiles with out-of-bounds upstream footprints
fall back rather than clamping or dropping taps to manufacture eligibility. The
repair does not detect a flat-surface cast-shadow edge and does not establish
quality or performance acceptance.

Nine source/scalar/actual-Iris preprocessing/assembly fixtures passed in0.879s,
with no skips. New regression covers the previously missed outer row/column,
zero/equal spread, binary32 threshold endpoints, normalized range/nonfinite data,
matching/mismatched/empty dimensions, first/last/odd viewport edges and coverage of
all original member footprints. No client, shader compiler, Java mod build or GPU
operation occurred. Existing builder was used unchanged for new exact archives:

| Artifact | SHA-256 |
| --- | --- |
| New manifest | `683cee198706d7289ef3994212edace9fd200ffeebff458e1c317a306751b3d4` |
| New prepass | `f4fb70f0deeafc3ffbfa5459dc57f632edc78bdd04f8b5c88c3391c384ac927b` |
| `build/Photon-LightingRatePrototype-disabled.zip` | `fca1a3115ad7cb5341807d7a06cb6939228da4603200fa831b5bdefd6eed206a` |
| Disabled archive metadata | `7af75702a3d2abb33426eb66a02c18016def818d0a3d00849c84962cde945fa2` |
| `build/Photon-LightingRatePrototype-enabled.zip` | `481b1d7aa4c23f2f5303890d204eb8573a064bbb0ec6812bfc7b241dc9475b3b` |
| Enabled archive metadata | `1779e0b966bd14d866cddf47d9e2b32a8cc57119b9ed61af40c7186f0a13ba21` |

Both patched archives contain541 members; disabled/enabled sizes are3781969/
3781970 bytes. Old header hashes remain historical review inputs and are
superseded for runtime qualification by this table. The profile owner received
the new immutable pins and overhead disclosure before qualification. Only these
two requested patched archives were reassembled; an original-arm metadata archive
must also be regenerated with the new manifest if the preparer's contract requires
it. Runtime ownership remains with root/operator.

## Complete translation-unit grammar acceptance

Added a tenth fixture to `tests/test_photon_lighting_rate_probe.py` using the
actual pinned Iris option lexer/Jcpp and embedded glsl-transformer parser. All
10 fixtures passed in6.021s, with no skips. The previous nine checks are retained.
This addresses the distinction between discovering options/parsing directives
and checking complete selected GLSL syntax.

The complete-program coverage is:

| Source state | Worlds / stages | Successful grammar parses |
| --- | --- | ---: |
| Original upstream deferred4 | Overworld, End, Nether × fragment/vertex | 6 |
| Patched deferred4/deferred5, private gate OFF and ON | All three worlds × fragment/vertex × two passes × two gate states | 24 |
| Deliberately malformed private helper, gate OFF | Overworld deferred4 fragment; invalid helper remains dormant | 1 |

Additionally, the same deliberately malformed helper with gate ON is rejected by
the parser (`missing RPAREN at 'return'`). It removes one condition-closing
parenthesis in a temporary in-memory copy, leaving all actual shader files
untouched. Thus31 complete translation units parse and one negative control
fails as required. Assertions independently verify selected cache output in
active deferred4, Overworld-only AO union/resolve code and original fallback
under deactivated or other-dimension resolve cases. Parsing dormant d5 sources
does not claim that Iris schedules d5 when the private graph is off.

The fixture uses the exact previously qualified forest loaded option map
`4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745` and explicit
Minecraft12101 / GL410 / GLSL410 / Iris macros, with no DH/VOXY macro. This models
the fresh reduced noDH graph, not the canonical source report's installed-DH
runtime. Each source file is processed through actual `OptionAnnotatedSource`
and `MutableOptionValues` **before** literal include expansion, matching Iris's
option stage. This matters because d4 wrappers include settings before global,
which references settings again: applying option edits to an already flattened
translation unit can update only one duplicate definition and select the wrong
branch. Shared graph references provide the actual per-file option set. No pack
settings or feature exclusions were changed to obtain these passes.

Pinned parser provenance:

| Component | SHA-256 |
| --- | --- |
| Iris1.8.12 local outer JAR | `3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83` |
| Embedded glsl-transformer2.0.1 JAR | `b8ca5e61b41ae18ddd5d4d5d6785cb9acd8fd7ac0c5c0b0689b872b039c11b9f` |
| Its `EnhancedParser.class` | `11133ca7cbfbc89777b15f06a31323b26c1ab6448b1822f4086e9503f3527814` |
| ANTLR4.13.1 runtime | `54665d2838cc66458343468efc539e454fc95b46a8a04b13c6ac43fc9be63505` |

The parser uses `setThrowParseErrors(true)` and parses the entire Jcpp output,
retaining version/extension directives. No missing library/JDK/profile causes a
silent skip. This is **grammar-only source evidence**: it does not execute Iris's
GLSL transformations, resolve GLSL types/overloads, establish sampler headroom,
link a program, prove render-target format/flip correctness, render a scene or
validate quality/performance. No overlay, manifest or builder changed during this
acceptance addition; the repaired shader/archive pins above remain unchanged.
Runtime proof and reloads remain assigned to the sole root/operator.
