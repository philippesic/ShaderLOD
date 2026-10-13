# Deep shader investigation: independent adversarial review

Review owner: `/root/adversarial`, Astra maximum reasoning. October 12, 2026
Pacific. Offline source/model review only; no Minecraft, GPU, Gradle, remote
execution, native Goal, product adoption or checkpoint mutation. Report and
`dev/logs/deep-shader-investigation-20261012/review/` are the only owned writes.

**Final disposition: accept BSL candidate r2 as an isolated, implementation-ready
source optimization. No blocking source finding remains in the checked scope.**
This is an offline acceptance, not a claim of driver equality, quality, actual
work coverage or speed. The parent owns source adoption and the next action.
Bliss primary-cloud count reduction remains a weaker fallback with a smaller
reachable Balanced reduction and meaningful visual uncertainty.

Final observation: October 12, 2026, 22:20 Pacific. The reviewed product overlay
was not applied to the shared checkout by this reviewer; the runtime pause and
sealed Reimagined operation were preserved.

## Exact accepted artifact and independent checks

The reviewed [r2 source patch](../../dev/logs/deep-shader-investigation-20261012/bsl/candidate-r2/source.patch)
changes only the primary cloud sample call and its scoped helper, the temporary
deferred1 include marker, and the missing-preset initializer `-1`. The explicit
`fadeFaster` branch keeps the reflected call native. Its
[complete integration overlay](../../dev/logs/deep-shader-investigation-20261012/bsl/candidate-r2/integration.patch)
has six files: maintained recipe/helper, packaged recipe/helper, catalog and
NeoForge 26.2 certificate. R1 is retained; R2 replaces its ternary with explicit
`if/else` at the same call boundary and has identical bound arithmetic.

| Identity | SHA-256 |
| --- | --- |
| R2 executable source digest | `5be3da36fc1812c220f5ecf00b904a783c0286fa629630038074abaf8073d4ba` |
| R2 private source ZIP | `eb0fb12588da87411e8215f9797e6e65c4154e78e8929944eeb5e91b78226143` |
| R2 recipe / certificate pin | `0f7b05b5feb7ce7631ac86b13b6e6a7691cc11d26d34a39b1b137828be8180f7` |
| R2 integration patch | `0317dff0b8866b2f32a3e3bf65c9e355c27892153d392d29b110b83f9527b27e` |

Independent [source and threshold audit](../../dev/logs/deep-shader-investigation-20261012/review/candidate-r2-review.json)
reconstructed all 295 expected source/native members directly from the authentic
original and recipes, without importing the candidate preparer. Every prepared
member matches; only the three declared members differ from the current adapter.
The native post-density march body is byte-identical. All six overlay preimages
and outputs, helper pins, catalog identities and recipe/certificate join match.
`git apply --check` passes against the observed checkout.

The threshold audit evaluates 78,000 finite tuples concentrated at ±1, ±2 and
±8 float32 ULPs around the rejection boundary, plus broad endpoints. It varies
all five native detail options, all five amount options, 65 gradients, four sun
coverages, three detail values and three plausible mix lowerings. Of 42,070
rejected tuples, none permits positive native raw density. The largest native
raw value among them is `-0.0009984970092773438`; the largest modeled native-minus-
bound discrepancy is `1.9073486328125e-6`. These are finite CPU-model results,
not a universal precision theorem. A deliberately invalid mean-detail bound
fails: base `0.465` with detail assumed `0.5` gives `-0.2000`, while actual detail
`1` gives native `+0.2998`. Thus an average/detail heuristic would erase cloud;
the actual upper envelope matters.

An independently compiled copy of the **actual inserted helper** passes
[609 control and evaluation-order cases](../../dev/logs/deep-shader-investigation-20261012/review/actual-r2-guard-result.json).
Only two-component swizzle syntax is translated for C++; texture/native calls
are explicitly instrumented CPU functions. Invalid Core, every non-Balanced
preset and nonzero/NaN weather return before the base fetch. Rejection performs
one base and zero detail reads; an accepted nonzero case performs one base and
two detail reads. Eight invalid-domain cases preserve the original unscaled
coordinates at native fallback. This proves tested branch/evaluation behavior,
not real texture filtering or GLSL execution.

The actual Iris 1.11.4 Fabric 26.1.2 parser yields native 384 → expected generated
388 with no changed native defaults or new internal marker. I independently
rehashed all parser inputs and checked every native option. The suspected
`#ifdef` cleanup declaration issue is **not reproduced** by the real parser.
The full candidate also passes the author's actual expanded-wrapper GLSLang
330-compatibility syntax check; this is not Iris's transformed OpenGL program.

I independently replayed the [real Java consumer test](../../dev/logs/deep-shader-investigation-20261012/review/r2-consumer-replay-provenance.json)
in a fresh receiver, additionally compiling current `BslQuadEligibility` rather
than using its older dependency class. The 2.455-second test passes with no
dependency stubs or skips: old certificate accepts native but refuses the new
recipe; updated certificate accepts freshly produced generated 388; native
option, recipe, state, archive, marker and original-archive tampering refuse;
restoration re-admits; Quality/Off preserve native archive/sidecar bytes; inactive
generated state is admitted only during the explicit native-return transition.
Every generated shader member matches the reviewed candidate.

The separate [transaction recovery receipt](../../dev/logs/deep-shader-investigation-20261012/consumer-checks/recovery-r2/bsl-transaction-recovery.json)
was inspected against its actual fixture and current transaction source hash
`b6622bd116057f3b42c6107738148880777e221dc1a44be063055907c34d82a2`.
Its two cases execute current `PackSelectionTransaction`, service, recipe and
filesystem for prepared Balanced Cancel and an injected reload exception, then
restore exact owned files, selection and option queue while preserving an
unrelated file. Only the Iris backend selection/save/queue boundary is simulated.
Actual UI, Iris writes and driver reload recovery remain unverified.

## Actual source and consumer checks

The maintained BSL 10.1.8 recipe pins original archive
`36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5`
and `shaders/lib/atmospherics/clouds.glsl`
`c0e21ee39082d8154f6aa8792a77f8a60c7fd3967614d56b1abaa06887256750`.
The inspected original `CloudSample` obtains one base lookup and two detail
lookups at lines 106–110. `DrawCloudVolumetric` calls it at line 410 before
separate lighting, distance-fade, opacity, faded-opacity and cloud-view-length
updates at lines 413–432. Returning the same zero density from `CloudSample`
preserves those updates. A `continue` in the caller would not be an equivalent
implementation.

Native `CLOUDS=2`, `CLOUD_BASE=0`, `CLOUD_DETAIL=1.0` select that substantial
three-lookup density path. Primary rendering calls `DrawCloudVolumetric` from
`program/deferred1.glsl:562`; a distinct PBR reflection call is at line 398.
The selected candidate changes the primary `fadeFaster=false` call only. Its
future saved workload must establish active pixels and sample rejection; source
loop maxima cannot stand in for actual executed work.

The public selection route is material. Current
`RendererRolloutPolicy.allows()` accepts every non-null family, including BSL
and Bliss; old research-opt-in descriptions are stale. Qualified Overworld
Performance can retain the native renderer graph through
`PackSelectionService.nativeRendererGraph()`. Balanced selects the generated
shader path. Off and Quality restore the original. This distinguishes actual
available shader work from an unreachable Performance count.

## Numerical and derivative objections to challenge in the exact patch

For the BSL candidate, set `a = 0.0476*CLOUD_DETAIL`, `b = noiseBase`,
`d = noiseDetail`, `c = noiseCoverage`, `r = 0.33*rainStrength` and
`s = 3*sunCoverage + CLOUD_AMOUNT`. Native pre-clamp density is

`F(b,d,c,r,s) = mix(21*mix(b,d,a)-c, 21-2.5*c, r)-s`.

For native finite option ranges, `0 < a < 1`, `0 <= r <= 0.33`, and normalized
linear-filtered noise has `0 <= d <= 1`. Thus `F(b,1,c,r,s)` bounds the native
value from above. A strictly negative, roundoff-safe bound certifies zero before
`CloudApplyDensity`, with no volume approximation or altered sample interval.
The exact patch passes the independent float32/source checks above while keeping
the driver-precision boundary explicit. Sampling an arbitrary
floating texture or accepting extrapolated rain/detail values would invalidate
the stated input envelope.

The two native detail lookups are implicit `texture2D` operations. A per-lane
early return before those calls creates new divergent control flow. The GLSL
specification leaves implicit derivatives undefined in such flow; existing
native divergence is not proof that another divergent path is harmless.
[Khronos GLSL 4.10, texture lookup functions](https://registry.khronos.org/OpenGL/specs/gl/GLSLangSpec.4.10.pdf).
R2 uses explicit level zero in the eligible detail branch under the pinned
non-mipmapped sampler contract. Moving
derivative instructions into an already divergent native loop is not a general
derivative-safety proof.

BSL `texture.noise=tex/noise.png`; its metadata requests blur and repeat. Prior
actual deferred1 evidence on the historical MC 1.21.1 NeoForge tuple records
512×512 RGBA8, min/mag LINEAR, base/max level zero, anisotropy one, bias zero,
sampler zero. This supports reuse of that implementation contract, not proof
of the future changed primary draw or another loader.
[Actual prior readback and identity limits](bsl-reflected-cloud-distance-cue-runtime-r2-review.md).
R2 retains a real source/native fallback outside its scope. Fresh inspection of
the pinned Iris Fabric 1.11.4 bytecode also follows the PNG metadata through
`NativeImageBackedCustomTexture.getSampling()` to `GlSampler.LINEAR_REPEAT`:
min/mag LINEAR, no mipmapping, repeat and comparison disabled. This supports the
source premise on the future Fabric tuple; actual bound texture/sampler state
and target-driver filtering parity still need an observation after resume.

The base lookup occurs before the new density rejection branch. The per-lane
numeric-domain fallback calls the native function and relies on the same
non-mipmapped sampler envelope; it is not a general claim that implicit texture
derivatives become defined for arbitrary unsupported samplers. Ordinary native
finite inputs satisfy these guards. DH, Voxy, advanced materials, other cloud
types/bases and other dimensions compile out the new helper; weather, Core and
preset exclusions return natively before coordinate work. Rain is deliberately
restricted to exactly zero in this new, pretrial design.

An optional coverage-only rejection before the base lookup was considered and
discarded: for default amount 10, gradient 0–1, rain 0–1 and sun coverage 0–1,
the base/detail both-one upper bound stays positive. It cannot earn useful
default sample savings merely by assuming that high cloud gradients are empty.

For `K` primary density samples entering the clear/Balanced path, `R` rejected
samples and `F` late numeric-domain fallbacks, source lookup count is
`3K - 2R + F`, versus native
`3K`. A late fallback has already fetched the base once before repeating the
native call. With ordinary finite native inputs `F=0`; the source reduction is
`2R/(3K)`. The primary loop can visit up to 33 positions because its cap is
`int(min(...,32)+1)`. No image-space or GPU invocation census was collected.
The authentic PNG texel histogram is only a source-space census with synthetic
gradient/sun inputs, not an estimate of `R/K` in the selected scene.

The principal cost risk is the added dependent ALU/comparison/branch between
base and detail reads, reduced texture-fetch overlap and partially occupied
execution groups. Saving two source-level lookups at a rejected sample is not
a measured two-thirds cloud-pass or frame-time gain.

## Bliss fallback boundaries

The authentic saved native map has 463 options, `HQ_CLOUDS=true`,
`minRayMarchSteps=maxRayMarchSteps=15`, `CLOUDS_INTERSECT_TERRAIN=true`,
`VOLUMETRIC_CLOUDS=true`, all three cloud layers enabled and `TAA=true`.
Its map is an Iris backend observation, explicitly not fresh GPU readback:
`dev/logs/bliss-volumetric-runtime-r1/native-options.json`.

In `volumetricClouds.glsl`, native angle adjustment computes
`N=int(clamp(15/sqrt(exp2(viewPos.y)),0,15))`, producing 10–15 for normalized
directions. Budgeting must follow that adjustment and precede both the geometric
step and extinction length. Balanced yields 9–14, saving one nominal visit
(6.7–10%) per full primary layer. Performance's nominal 8–12 is contingent on
the generated route actually being selected. The native geometric layer width
is 100 while optical length uses 90; preserving the native 0.9 calibration is
essential.

For constant extinction `mu` and constant nonnegative lighting `L`, each full
step is `C += L*(1-exp(-mu*h))*T`, `T *= exp(-mu*h)`. After `N` full steps,
`T=exp(-mu*N*h)` and `C=L*(1-T)`; changing `N` while retaining `N*h` preserves
that real-arithmetic invariant. It does not prove equality for variable density,
shadow lighting, floating-point execution or prefixes.

Native terrain early exits, bounding-box tests, camera-inside-layer starts and
`T<1e-5` termination change the admitted prefix. The initial upper-layer skylight
occlusion sample also moves when the starting offset changes. The complete
actual input chain includes native per-frame dither, the cloud alpha attachment,
bilateral upscale and later TAA. Their interfaces can remain unchanged while
still/motion appearance changes. These are honest visual-test obligations,
not grounds for silently calling the result image-equivalent.

## Integration and preserved negative evidence

Any BSL recipe change must reach the maintained recipe, packaged mirror and
catalog identity, keeping prior exact derivatives recognized. NeoForge 26.2's
actual compiled consumer additionally pins the recipe at
`ports/26.2/neoforge/src/main/java/com/pp/shaderlod/integration/bindingcompat/BslSourceCertificate.java:12`.
Its `RECIPE_SHA`, marker/state/recipe checks and exact generated option map must
remain coherent. Updating only a staged shader ZIP would leave ordinary
generated-source admission refusing the candidate. The certificate's exact
archive reconstruction and Off/native-return rules must remain strict.

The closed BSL reflected whole-ray classifier spent up to 33 classification
positions and established no useful original-relative gain at its frozen PBR
wall. It stays closed; this new per-density detail bound is a distinct premise,
not a threshold or scene rescue of that trial.
[Closed raw endpoint comparison](bsl-reflected-cloud-early-cost-r2.md).
Bliss's prior fog-count/resolution and raster-remap trials likewise do not
establish a primary-cumulus sample benefit. Already prepared End clouds,
streaming hashes and Complementary prior-weight carry are not new candidates.

## Smallest decisive deferred runtime observation

Use one existing clear Overworld view with the exact native BSL 384 map,
native TAA/FXAA, resolution and camera/weather/time. Public Balanced must select
the new generated 388 graph; confirm the actual primary deferred1 source/Core
and effective noise sampler once using existing observers. The current ordinary
native view is sufficient; do not move a closed reflected-cloud experiment's
workload to rescue that result.

Compare original → explicit-LOD full-detail control → bound candidate → original
return, with all code and filtering overhead charged against original. Inspect
cloud edges, thin clouds, their terrain boundary and a short native-TAA pan;
check native restoration and the excluded PBR/DH routes separately when those
claims are pursued. The source model predicts exact zero substitution under its
envelope, but current hardware may expose filter or precision differences.
Count actual rejected density visits only if the existing probe can do so
without a new instrumentation project. A positive shader saving with no useful
original-relative frame benefit does not earn a product speed claim.

No new runtime job is authorized by this review and no extra human approval is
needed to complete the offline handback. Actual runtime, quality, DH/PBR breadth,
all 90 family/version/loader cells and measured speed remain outstanding project
work. The parent can accept and integrate this exact source candidate now while
preserving the existing execution pause.
