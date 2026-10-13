# Complementary prior-weight reuse: bounded offline assessment

Observed October 12 Pacific / October 13 UTC, 2026. Assigned Astra reasoning
only; no maintained shader, sealed input, runtime, Gradle, Goal or checkpoint
mutation. Parent owns acceptance and any isolated implementation.

**The recurrence is sound under a fixed evaluation model, and lazy initialization
can preserve native zero-work exits. The arithmetic mechanism was already tried
and closed without useful original-relative gain.** A small isolated candidate
is justified by the human's explicit offline request, solely to make the lazy,
current-generated-path case reviewable. It does not justify reopening the closed
runtime trial or predicting a gain.

## Correct the proposal's novelty claim

The [second methods audit](shader-transfer-methods-r2-20261012.md) called this
unimplemented. Repository evidence contradicts that broad description:
[R1 source review](reimagined-vl-weight-source-review-r1.md) admitted exactly this
prior-next-weight reuse, and [R1 early cost](reimagined-vl-weight-early-cost-r1.md)
closed the frozen candidate unretained. Original 130/132, full-work 131/130,
candidate 122/125, original-return 124/126 are two cached FPS observations per arm.
They establish no useful original-relative gain and no precise causal slowdown.
This report reuses that evidence; it has not rerun its historical audit.

| Aspect | Closed R1 | Bounded current proposal |
|---|---|---|
| Arithmetic | Carry previous `nextWeight` | Same mechanism |
| First evaluation | Eager native F(0) before trace | At native first `currentWeight` site |
| Immediate near-ray exit | Extra unused F(0) | No weight evaluation |
| Consumer | Private duplicated/wrapped native function; Performance1; native near10–15 | Existing generated volume body; Balanced0; existing near budget9–14 for the default native10–15 domain |
| Compile scope | Pinned MC1.21.1/Mac/Iris/Reimagined/style1/medium/etc. | Keep current generated Overworld composite1/RP1 exclusions, preferably select one family initially |
| Runtime | R1 cue/still/cost existed, motion incomplete | None for new lazy/current-generated combination |

Balanced here describes the existing user-selected generated route, not a tuning
change to rescue R1. Fewer selected intervals usually leave fewer weight
recomputations available to remove. Unbound shares source but does not inherit
Reimagined driver, quality or performance evidence.

## Authentic source and consumer

The executed [check](../../dev/logs/offline-delivery-sprint-20261012/complementary-reasoning/check.py)
and [receipt](../../dev/logs/offline-delivery-sprint-20261012/complementary-reasoning/receipt.json)
verify both original full archive pins and current recipe assembly:

| Family r5.9.3 | Original archive SHA256 | Current assembled source digest |
|---|---|---|
| Reimagined | `fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38` | `920212803e992b80ba58da1e00a54bc45b4ad5ddac1edae1b392002c90ee2cc7` |
| Unbound | `98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a` | `9118b112be859fda810582c78dd1409d0273cbe8fa9ed72cc32a22c9ddc09ed6` |

Both original `shaders/lib/atmospherics/volumetricLight/volumetricLight.glsl`
members hash to `d380c3243df73b390433c015fcb5b9182cf98a511b49f7a98d5ccac1313ae8ff`.
Both current assembled members hash to
`08146df1b05c2ab46e3bb9e28ece7d174445dee8f5dce3e6b76845eae8bf6ca0`.
Original files live at `dev/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip`
and `dev/upstream/pack-expansion/ComplementaryUnbound_r5.9.3.zip`.

Native `world0/composite1.fsh` defines FRAGMENT_SHADER/OVERWORLD/COMPOSITE1
and includes `program/composite1.glsl`; that program includes the volume member
at native line74 and calls `GetVolumetricLight` at line238. Current recipes
include the existing adapter in the volume member and budget nearSamples after
native near-count modifications, before interval denominators. No weight reuse
is in this current generated body. The recipe `shader_source_digest` is the
original-input identity, not the generated-output digest; the model's first
attempt compared those incorrectly, diagnosed this API contract, and corrected
the assertion before its passing run. No product defect resulted.

## Recurrence and control flow

Native `lastDistance=0` is line163. The inclusive loop begins at183.
`F(x)=smoothstep1(pow(x/maxDistance,fogCurve))`, with native scalar smoothstep
implemented as `x*x*(3-2*x)` in `lib/util/commonFunctions.glsl`; it is not a
clamping operation. The denominator and exponent are finalized before the loop
and never assigned inside it. There is no `continue`.

1. The vlMult return at64 occurs before any tracing setup; leave it there.
2. Near samples compute unchanged distance and scenePos; their ray-end break193
   occurs before weights. A lazy candidate performs no F(0) for that exit.
3. Far samples compute unchanged scenePos before clamping nextDistance to rayEnd.
   Keep this distinction. End's break243 also precedes weights; an Overworld-only
   candidate must leave End native anyway.
4. Native current/next weights at265–266 precede slice subtraction267 and
   stepResult269. Entity break274 follows those calculations but precedes
   accumulation283 and lastDistance285. Do not move either weight below the
   entity test merely because its result is unused on that exit.
5. After accumulation, assign the cache from that exact nextWeight alongside
   lastDistance. At the next iteration lastDistance equals the previous
   nextDistance, so the unchanged F has the same input and parameters.
6. No iteration can reach weights for the first time at i>0: all earlier exits
   terminate the loop. Therefore `i==0` is a sufficient lazy first-use check;
   no separate initialized Boolean is needed. The current source includes
   i<=totalSamples, so totalSamples0 still attempts i0; the pre-weight break can
   nevertheless give zero weight evaluations. A negative total would give none.

Retain the original first expression even when ordinary positive finite
maxDistance and fogCurve imply F(0)=0. Do not substitute literal0, infer a clamp,
change pow/smoothstep order, carry sliceWeight, or change coloring/accumulation.
No sample count, position, lookup, shadow eligibility, dither, native AA, final
color transform or feedback update is removed. Native feedback singleton is
excluded by the current volume predicate and should remain excluded.

## Smallest explicit candidate for Sol

Use a disposable copy of one maintained manifest and the existing builder. Add
three unique exact-anchor patches to the same volume member; retain original
source pins, unchanged additions and existing volume-count patch. No function
duplication, option, uniform, Java policy or generic rewrite is needed.

At native `float lastDistance = 0.0;`, append declarations under exactly the
current volume compile guard:

```glsl
#if SHADERLOD_REFINEMENT_ADAPTER == 1 && defined COMPOSITE1 && defined OVERWORLD && !defined DISTANT_HORIZONS && !defined VOXY && RP_MODE == 1 && !defined GENERATED_NORMALS && !defined COATED_TEXTURES
    bool shaderlodReuseWeight = shaderlodBridgeVersion == 2 &&
        shaderlodEnabled == 1 && shaderlodPreset == 0 &&
        isEyeInWater == 0 && translucentMult == vec3(1.0) &&
        viewWidth + viewHeight - gl_FragCoord.x - gl_FragCoord.y >= 1.5;
    float shaderlodPreviousWeight = 0.0;
#endif
```

Replace only the currentWeight declaration with an exact compile-time fallback:

```glsl
#if /* the identical guard above */
        float currentWeight;
        if (shaderlodReuseWeight && i > 0) currentWeight = shaderlodPreviousWeight;
        else currentWeight = smoothstep1(pow(lastDistance / maxDistance, fogCurve));
#else
        float currentWeight = smoothstep1(pow(lastDistance / maxDistance, fogCurve));
#endif
```

Immediately after `lastDistance = nextDistance;`, under the identical guard:

```glsl
        if (shaderlodReuseWeight) shaderlodPreviousWeight = nextWeight;
```

The initialized scalar0 is storage initialization only; it is never read as
F(0). First reached weights always execute the native F(lastDistance). Off,
Quality3, Performance1, Aggressive2, absent/invalid bridge, invalid enabled and
other presets recompute both native expressions in this isolated Balanced-only
proposal. Native Performance renderer selection remains untouched. Compile
excluded paths must preprocess to the current generated baseline. Runtime
excluded paths retain native weight expressions but may still have source-level
guard/declaration overhead; do not promise zero instructions after compilation.
Uniform eligibility is assumed constant during one invocation; this is the
existing core-uniform transport contract, not a new mutable loop input.

For a source-only candidate, changing the generated digest requires private
metadata and a reviewable future recipe/resource/catalog overlay if desired;
the unchanged current product will not recognize an arbitrary new private ZIP.
Do not apply it to sealed inputs or use the paused operation to test it.

## Executed model, cost and limits

`python3 dev/logs/offline-delivery-sprint-20261012/complementary-reasoning/check.py`
passed both authentic assemblies and **9,072 paired trace cases**, including
native/current counts4/6/9/10/14/15/30, dither0/.5/.999, zero/short/near/far ray
endpoints, entity exits before accumulation, surface-depth conditions,
fogCurve.5/.7/.9, enabled/fallback and Overworld/End break modeling. All recorded
sample positions, weight values, slice values and accumulation state match.
The model uses host pow rounded to binary32 with explicit operation rounding;
it is not GLSL pow, Iris, texture sampling or complete shader execution.

Another **200 comparisons** cover zero/infinite/NaN denominator, zero/negative
exponent and repeated/nonmonotonic/nonfinite distances under the model. NaN
classification is compared, not payloads or driver exceptional semantics.
No equality claim is made for GLSL-undefined inputs, driver-dependent pow
accuracy, contraction, denormal treatment or register-rounding changes.

For m visits to the weight site, native evaluates F 2m times. Lazy active carry
evaluates 0 when m=0, otherwise m+1, saving m−1. If k slices accumulate followed
by an entity-rejected slice, m=k+1, so savings are k; the simpler k−1 count only
holds without that final weight-bearing rejected attempt. The loop still does
all lookups and integration work. Added costs are a live float, eligibility
Boolean, first-iteration condition and cache updates; hardware compilation may
eliminate the native redundancy already. A fraction of arithmetic expressions
removed is not the same fraction of volume cost or total frame cost. Historical
R1 no-gain evidence makes plausible total benefit small/uncertain; no percentage
is supported for this case.

Next decisive offline observation: Sol's exact three-patch candidate should
recover the current member byte-for-byte on removal, preserve all other members,
pass actual preprocessing exclusions/runtime fallback source tests, and be
independently reviewed. Any future driver question requires separate explicit
resume authorization and cannot replace the existing paused Reimagined
main-JAR rendering/recovery action. No owned live processes remain.
