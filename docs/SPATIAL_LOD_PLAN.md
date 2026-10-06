# Shared spatial LOD reconstruction plan

The general mechanism in
[`spatial_plan.glsl`](../shaderpacks/common/shaders/include/shaderlod/spatial_plan.glsl)
turns a validated shared work policy into explicit full, quarter and sixteenth
evaluation tiers with centered reconstruction transitions. It is ShaderLOD code;
no Photon algorithm or upstream attribution was copied or removed. Existing
policy helpers, reference Photon archives/manifests, mod transport and runtime
classes are unchanged by this bounded assignment.

Include `work_policy.glsl` first, then `spatial_plan.glsl`. The public entry point is:

```glsl
ShaderlodSpatialPlan plan = shaderlod_spatial_plan_decide(policy,
    capability_valid, quality_valid, protected_work,
    conservative_region_distance_blocks, supported_rates);
```

`policy` is the existing `ShaderlodWorkPolicy`. All protocol, mode, numeric-domain,
global-enable, inheritance, quality and protection checks delegate to
`shaderlod_work_decide`. Sixteen virtual members with zero minimum reuse that
validation and continuous distance request; their integer work count does not
select a spatial tier. There are no uniforms, samplers, derivatives, projection
assumptions, options, new transport or capability schema.

Distance is radial camera-relative Euclidean distance in Minecraft blocks.
Adapters supply the nearest valid receiver distance across their complete region
and must resolve near/member protection before expensive work. Distance alone is
not a quality certificate. The shared plan does not classify geometry, establish
surface continuity, allocate caches, synchronize passes or perform lighting.

`supported_rates` is a bit mask: bit 1 means full, bit 2 means one evaluation per
2 × 2 region, and bit 4 means one per 4 × 4 region. Full is mandatory. Policy
medium/far endpoints must be exactly 1, 0.25 or 0.0625 and both must be supported.
Other valid ratios, missing endpoint capabilities and requests for skipping retain
full reconstruction at every distance with UNSUPPORTED reason. Invalid masks
retain full with INVALID reason. There is no rounding to the nearest tier, nor
implicit interpolation through an unavailable tier. A caller can deliberately
choose a supported policy before invoking the helper.

| Result | Meaning |
| --- | --- |
| `weights` | Nonnegative normalized linear reconstruction weights: x full, y quarter, z sixteenth |
| `requested_ratio` | Continuous shared-policy request, retained as diagnostic even when tier support forces full |
| `reconstruction_ratio` | Weight-average legal evaluation fractions; describes reconstruction, not physical cost |
| `evaluation_ratio` | Sum of the fractions of every positive-weight tier, before classification/cache/reconstruction cost |
| `evaluate_full`, `evaluate_medium`, `evaluate_far` | Exactly the positive-weight tiers that must have valid current evaluations |
| `reason` | Existing SHADERLOD_WORK_* reason; POLICY only for a supported eligible plan |

`shaderlod_spatial_plan_full(requested_ratio, reason)` is the full fallback
constructor. Use it with already validated diagnostics. Unknown protocols,
invalid distances/bands/ratios, disabled global/effect state, FULL, unresolved
INHERIT, missing capability, protection or failed quality use weights (1,0,0),
full evaluation and the original shader path. Ordinary shared-policy fallbacks
report unity request. A supported unity policy also collapses to one full
evaluation. The original/mod-absent and disabled baseline therefore needs no
spatial cache or reduced producer work. Call the plan and all guards before
expensive evaluations; blending or branching after full evaluation does not avoid
that work.

## Transitions and evaluation cost

Let m/f be the existing near/far centered smoothstep blends, clamped to [0,1]
to keep endpoint rounding from producing negative weights. The full-policy,
medium-policy and far-policy endpoint contributions are respectively
`(1-m)*(1-f)`, `m*(1-f)` and `f`. Each contribution is added to its exact legal
tier. Equal endpoints collapse into a single evaluation. Existing validation
forbids overlapping transition bands, so at most two distinct tiers are active.
Weights sum to one within binary32 rounding. Zero-width bands switch exactly at
the boundary; touching bands retain the middle endpoint where they meet.

For 32/96/8 blocks and medium/far ratios 0.25/0.0625, near transition endpoints are
28–36 blocks and far endpoints are 92–100 blocks:

| Distance (blocks) | Full / quarter / sixteenth weights | Request | Active tier evaluation ratio |
| ---: | --- | ---: | ---: |
| 28 | 1 / 0 / 0 | 1 | 1 |
| 32 | 0.5 / 0.5 / 0 | 0.625 | 1.25 |
| 36–92 | 0 / 1 / 0 | 0.25 | 0.25 |
| 96 | 0 / 0.5 / 0.5 | 0.15625 | 0.3125 |
| 100+ | 0 / 0 / 1 | 0.0625 | 0.0625 |

Full plus quarter evaluation in the first transition is more work than full
alone, even before overhead. A full-to-sixteenth blend similarly costs 1.0625.
The continuous request is not a strict physical cap. The adapter must evaluate
each active tier or read a valid current-frame result, then reconstruct the
same declared linear quantity with these weights. Do not multiply lighting
strength, density, radiance or material values by retention. Per-recipient
full-resolution material/albedo composition remains the adapter's responsibility.

Invalid cache entries, invalid receiver regions, resource mismatches and unknown
producer readiness must resolve to original full evaluation. Do not mix through
holes or use stale output across reloads, modes, world changes or resizing. See
[`SURFACE_REGION_CONTINUITY.md`](SURFACE_REGION_CONTINUITY.md) for the separate
all-member surface guard and its lighting-frequency limitations. The shared
pipeline worker owns producer/resolve contracts and actual legal storage.

## Acceptance and remaining boundaries

Acceptance checks are implemented in
[`tests/test_spatial_lod_plan.py`](../tests/test_spatial_lod_plan.py): source-order
binary32 interpretation; independent scalar reconstruction oracle; real
dependency-free Java `DistanceBands`/`EffectPolicy` comparisons; exact endpoints,
float32 neighboring boundary values, centered/touching/zero-width transitions,
every legal endpoint and capability-mask combination, unsupported fractions,
protocol/mode/quality/protection fallbacks and numerical domain errors. Pinned
Iris Jcpp and its embedded GLSL transformer parse complete GLSL 410 harnesses,
including repeated include guards, actual Iris option isolation and malformed
syntax rejection.

After the runtime operator's formal GPU/client return,
`python3 -m unittest discover -s tests -p test_spatial_lod_plan.py -v` passed all
10 tests in 6.573 s with no skips. Evidence includes 162 actual Java-policy
comparisons, 600 seeded scalar-oracle distances across all six legal endpoint
combinations and 4096 binary32 distances near completed smoothstep endpoints.
The actual pinned toolchain parsed 18 complete GLSL 410 translation units and
rejected the malformed-parameter control. Actual Iris option discovery found no
helper options or references; repeated inclusion preserved one struct definition.
Tested helper SHA-256:
`70738064fd0dad8b63af95868be55d7df6aba337f398b3d6ff6ca5c5f5709033`.

Parser provenance is Iris outer JAR
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`,
embedded GLSL transformer
`b8ca5e61b41ae18ddd5d4d5d6785cb9acd8fd7ac0c5c0b0689b872b039c11b9f`,
EnhancedParser class
`11133ca7cbfbc89777b15f06a31323b26c1ab6448b1822f4086e9503f3527814`,
and ANTLR runtime
`54665d2838cc66458343468efc539e454fc95b46a8a04b13c6ac43fc9be63505`.
The fixture verifies these exact bytes before parsing.

No client, shader reload, Gradle build, Java runtime class write or GPU measurement
ran in this helper assignment; temporary Java oracle/parser classes were removed
with their temporary directories. No owned live processes remain. The restricted
interpreter is not GLSL execution; the grammar parser does not resolve
types/overloads, compile/link programs or exercise Iris rendering.

The downstream adapter still needs actual driver compilation, producer/resolve
readiness, linked controls and disabled parity. Quality evidence must cover
near texture detail, cast/contact-shadow edges, thin foliage, material/lightmap
boundaries, motion, popping/flicker and disocclusion. Sole client/GPU ownership
and coordinated reloads apply. Performance requires repeated matched original
and candidate GPU/total-frame timings, including full classification, producer,
cache traffic, fallback, transition and resolve costs. Nominal evaluation rates
and this plan establish no quality equivalence or throughput gain.
