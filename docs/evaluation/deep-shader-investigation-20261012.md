# Active cloud-work optimization — October 12, 2026

**Recommend the BSL 10.1.8 primary-cloud detail bound, candidate r2.** It removes
two of three noise lookups whenever the already-required coarse sample proves
that the final native density is zero. This is a stronger premise than the
inspected count reductions: it preserves each native integration step and its
density rather than approximating a differently sampled volume.

The exact [shader patch](../../patches/bsl-cloud-detail-bound-r2/source.patch),
[six-file integration patch](../../patches/bsl-cloud-detail-bound-r2/integration.patch)
and [pins](../../patches/bsl-cloud-detail-bound-r2/receipt.json) are delivered.
Parent applied the integration patch to a fresh source copy, verified all six
postimages, reproduced the candidate ZIP byte for byte, and obtained an empty
resource-sync diff. The maintained product and sealed runtime inputs remain
unchanged. The [integration receipt](../../dev/logs/deep-shader-investigation-20261012/parent-integration/receipt.json)
records the actual operations. Independent Astra maximum-effort
[review accepts r2 for isolated source integration](deep-shader-adversarial-review-20261012.md).
Rendering, image quality and useful total-frame gain remain unmeasured.

This was a separate human-authorized offline investigation, beginning October12
at22:00 Pacific, with a two-hour ceiling. Astra at maximum reasoning effort owned
the volumetric mathematics, independent alternatives and adversarial review;
Sol implemented candidates and authentic Java consumers. Parent owned selection,
synthesis and integration. The existing nine-family, five-version, two-loader
90-cell objective, runtime pause, sealed Reimagined next operation and remaining
1,200-second runtime allocation are preserved. No native Goal was created or
resumed, and no Minecraft/GPU/Gradle/remote operation ran.

## Actual work and selected route

The original BSL archive is pinned to
`36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5`.
The authentic saved Fabric26.1.2 reference has Overworld, clear weather,
`CLOUDS=2`, `CLOUD_BASE=0`, `CLOUD_DETAIL=1.0`, `CLOUD_AMOUNT=10.0`,
`CLOUD_THICKNESS=5`, `ADVANCED_MATERIALS=false` and native TAA. Aurora is off.
Its native384 map is preserved. The selected consumer is
`world0/deferred1.fsh → program/deferred1.glsl → DrawCloudVolumetric(...,false)
→ CloudSample`. Balanced selects the generated graph. Eligible Performance
selects the native renderer graph and bypasses this new shader work; it is not
a stronger version of this candidate.

Each reached base0 cloud sample originally fetches one red base value and two
blue detail values. Native maximum loop visits can be33 because its count is
`int(min(planeDifference/sampleLength,32)+1)`. Opacity, foreground and coverage
conditions determine reached work. The candidate changes neither that count
nor its integer arithmetic. It adds no preliminary ray traversal, texture,
render pass, buffer or history attachment.

## Equation and invariants

Let b be coarse noise, d interpolated detail noise, c native height coverage,
s sun coverage, A cloud amount, and w=`.0476*CLOUD_DETAIL`. In clear weather the
native pre-clamp expression is

```text
y = 21*mix(b,d,w) - c - (3*s+A)
density = CloudApplyDensity(max(y,0))
U = 21*mix(b,1,w) - c - (3*s+A)
```

The pinned normalized noise and linear sampling put d in[0,1]. All five native
detail settings put w in[.01428,.08092]. Thus y≤U in real arithmetic. The
candidate substitutes density0 only when its bounded finite-domain checks pass
and U<−.001. Equality and ambiguous near-zero values retain both detail reads.
This is a zero-density certificate, not a small-contribution threshold.
Native density scaling and division by `sqrt(q*q+.5)` preserve q=0.

The actual shader retains the native ordered expression. The .001 negative gap
is large relative to the observed binary32 lowering differences, but the finite
CPU checks are not a universal GLSL precision proof. The source keeps ordered
base/gradient/sun/coverage and finite-coordinate guards. Clear eligibility is
the uniform `rainStrength==0` condition, selected before any runtime result;
other weather enters the native function before classification.

Every caller update still executes with that zero density: sample lighting,
distance fade, cloud opacity, softened opacity, first-cloud/cloudViewLength and
final feedback. There is no loop continue, changed stride, alpha rescaling,
shifted dither or temporal modification. Reflections, skybox, other dimensions,
DH/Voxy and the advanced-material shader mode take their original sampling
paths. Other PBR resource combinations still need compatibility evidence.

Retained detail lookups use explicit LOD0. An early return followed by implicit
texture reads would introduce an undefined-derivative boundary. The native
wrapper already enables the ARB texture-LOD extension. Historical actual BSL
readback showed RGBA8, level0, isotropic nonmip LINEAR sampling; the pinned
Iris1.11.4 implementation chooses LINEAR_REPEAT for the pinned blur/repeat PNG.
Those are credible implementation inputs, not a fresh sampler observation.
The first runtime admission must verify the effective primary noise binding.

## Work reduction and overhead

For K reached eligible samples, R successful zero certificates and F finite-domain
fallbacks after the initial base read, nominal lookup counts are

```text
native:     3K
candidate:  3K - 2R + F
```

Each certificate removes both detail lookups and the remaining native density
combination at that sample. A domain fallback can replay the native base read;
the F term charges that extra work. Runtime R/K and F are unknown. Source lookup
counts do not establish hardware transactions: filtering, caches, compiler
flattening and partially occupied execution groups can alter the saving.

The authentic512² noise image contains262,144 texels. At texel centers with
native detail1/amount10, clear weather and sunCoverage0, the independent census
found the following headroom:

| Height fraction | Certified texels | Fraction | Nominal three-read reduction, F=0 |
| --- | ---: | ---: | ---: |
| .125 | 93,788 |35.78%|23.85%|
| .5 |131,303|50.09%|33.39%|
| .75 |194,574|74.22%|49.48%|
| 0 or1 |255,136|97.33%|64.88%|

These are texture envelopes, not reached-ray, bilinear or screen-pixel coverage.
The candidate adds dependent arithmetic/comparisons between base and detail,
one rejection branch, live coordinates/registers and outer control checks.
It can reduce texture overlap or lose savings through divergent quad execution.
The nonclear path still pays the outer decision. If f is the unknown original
frame-time share of these noise queries, ideal total saving is at most roughly
`f*(2R-F)/(3K)` before those costs. No measured f or FPS percentage is available.

## Alternatives and early exclusions

The [volumetric investigation](deep-volume-math-20261012.md) produced a complete
isolated [Bliss primary-cloud candidate](../../dev/logs/deep-shader-investigation-20261012/bliss/candidate-r1/receipt.json).
Actual HQ options give angle-adjusted native10–15 and Balanced9–14, only
6.7–10% fewer nominal primary visits. Its count changes precede both geometric
step and optical multiplier, retaining the native90/100 calibration. For a
homogeneous full layer, `T'=T*exp(-mu*h)` and
`C'=C+L*(1-exp(-mu*h))*T` telescope to `T=exp(-mu*N*h)` and `C=L*(1-T)`;
keeping N*h preserves absorption and light together. Constant raw noise does
not mean constant effective extinction because the native height fade varies.
Terrain prefixes, inside-layer starts, thin density/light bands and opacity
termination defeat image equivalence. BSL's retained sample values therefore
offer the better initial visual-risk/removed-work tradeoff.

Bliss passed source recovery, selected-wrapper preprocessing and GLSL syntax
checks. Its modern broad Iris inventory reaches duplicate-option logging and
requires an initialized loader; exact current dependencies do not make that
standalone path valid. Both real NeoForge and Fabric loader failures are retained
under its evidence directory. Its predicted466 generated options remain
unverified by that parser. No fake loader or relaxed map was used.

BSL/Solas alpha-only count correction preserves constant opacity but not their
coupled lighting, distance fade and depth feedback. BSL shafts/AO and other
small native AO groups round back under Balanced. Aurora and Sildur volume are
disabled in the pertinent references. Bliss coarse SSR changes support and
thin-geometry hits. AstraLex/Solas native Mac blockers remain explicit. The
already-prepared End-cloud/hash/weight-carry work and all closed cost trials
remain intact; this new primary density bound does not rescue the closed BSL
whole-reflected-ray classifier or Bliss pruning experiment.

## Integration and executed evidence

The six-file patch updates the maintained BSL recipe and helper, their packaged
mirrors, catalog current/legacy identities, and the exact NeoForge26.2
`BslSourceCertificate.RECIPE_SHA`. No new uniform, option, sampler configuration
or renderer route is introduced. The preset uniform defaults to−1 so an absent
preset fails to native. Recipe pins include the native noise PNG and metadata.
The shared resource roots already feed all ten builds; target compilation and
packaging remain future adoption work. The new certificate join is required
source coherence, not a NeoForge runtime validation claim.

The [full-work LOD0 control](../../patches/bsl-cloud-detail-bound-r2/control/receipt.json)
and its [integration patch](../../patches/bsl-cloud-detail-bound-r2/control/integration.patch)
are also prepared against the original product preimages. Parent independently
reassembled it and checked that its only shader difference from r2 removes the
zero-return. It keeps both detail reads, all controls and every native update;
actual Iris still discovers384→388 and the selected fragment parses. Its
[source delta](../../patches/bsl-cloud-detail-bound-r2/control/source.patch)
is relative to candidate r2. Each arm needs its own exact recipe/catalog and
certificate identity; the two integration overlays are alternatives, not
sequential patches. The compiler may eliminate unused classifier arithmetic
in the control, so original remains the absolute cost denominator.

Candidate shader digest is
`5be3da36fc1812c220f5ecf00b904a783c0286fa629630038074abaf8073d4ba`;
recipe SHA is
`0f7b05b5feb7ce7631ac86b13b6e6a7691cc11d26d34a39b1b137828be8180f7`.
The reproducible preview ZIP SHA is
`eb0fb12588da87411e8215f9797e6e65c4154e78e8929944eeb5e91b78226143`.
Public service ZIPs have different private metadata; every shader/native member
matches the preview. The distinction was checked rather than requiring false
whole-ZIP identity across producers.

- Seven focused BSL tests passed with zero skips: authentic pins and body
  recovery, selected/excluded preprocessing, scalar bounds, overlay/refusals,
  real Iris384→388 with unchanged native declarations, actual selected-entry
  GLSL parsing, and staged public service/certificate checks.
- Independent Astra reassembled all295 authenticated members and checked six
  overlay preimages. Its78,000 near-boundary tuples yielded42,070 certificates
  and zero false zero-density decisions; the largest native raw value admitted
  was−.000998497. An instrumented translation of the actual inserted helper
  passed609 control/evaluation-order cases. These are CPU denominators.
- Independent Sol compiled actual public service/catalog/old-and-new certificate
  and current PackSelectionTransaction source, without stubs. Native384 and
  generated388 admission, stale recipe/map/state/marker/archive refusals,
  Balanced/Quality/Off, Cancel and injected reload-failure restoration passed.
  The Iris selection/queue/save backend was simulated; UI/native writer behavior
  remains outside this evidence. [Detailed consumer results](deep-candidate-consumers-20261012.md).
- Parent's fresh source integration reproduced the reviewed ZIP exactly and
  yielded zero pending resource-sync changes. Existing runtime inputs remained
  untouched; final preservation is recorded separately.

Parent's combined local recheck passed12 tests, zero skips, in11.193s. That run
exposed a fixed-path receipt writer in the new consumer fixture; its repeated
write replaced the original r2 consumer receipt. Original r1 and the independent
Astra r2 receiver remain preserved. The fixture was corrected to fresh receivers
and rechecked separately; the source candidate was unchanged. This is an evidence
storage defect, not a new rendering or shader failure.

## Smallest decisive runtime experiment

The next runtime operation in STATUS remains the already-sealed Reimagined
main-JAR rendering/recovery after explicit resume. This offline result adds no
gate to that operation. Thereafter use BSL's existing runnable Fabric26.1.2
consumer and the unchanged authentic clear Overworld workload for this candidate.

First perform a short untimed original/full-work-LOD0/candidate admission:
verify exact archive and native options, selected generated Balanced graph,
Core2/1/0 at the actual primary consumer, effective isotropic level0 LINEAR noise
binding, and real rejection activity. The full-work control is the same source
with only the zero-return removed; it separates sampler changes from rejection.
Use existing sampler/capture facilities and a private cue only if execution
cannot otherwise be established. A cue is not timing or ordinary quality.

Inspect native/control parity and ordinary candidate clouds in a held view and
short motion: thin edges, sun-reveal contours, horizon, foreground intersections
and temporal stability. Verify public Quality/Off/Cancel and failed-Apply native
recovery. Then take one short same-mod original/control/candidate/original-return
cost bracket, keeping scene/options/resolution/AA fixed, recording return drift
and charging all bridge/branch costs. Absent useful executed rejection, visible
loss, failed sampler parity or no useful total benefit closes the frozen
candidate without margin/filter/preset/workload tuning. No new profiler or
benchmark framework is needed. Other tuples and DH/PBR remain required work.
