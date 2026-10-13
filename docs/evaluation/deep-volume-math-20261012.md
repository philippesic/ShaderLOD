# Volumetric transfer investigation — October 12, 2026

**Recommendation:** retain the isolated Bliss 2.1.2 primary-cloud quadrature
port as the strongest inspected *sample-budget* transfer on a family with
working Mac originals. It reuses the existing controls and physical segment
integration. At actual native defaults, Balanced removes only **6.7–10% of
the selected primary march iterations**. Performance would remove 20–25%
if the generated graph is selected, but current qualified Performance can
bypass the patch through native renderer batching. This limits the total
benefit and puts a source-qualified BSL exact-zero density shortcut ahead of
Bliss if the parent's separate sampler/source review accepts it. No visual
acceptance or useful total-frame speed follows from this report.

Parent `/root` owns selection, implementation acceptance, integration and all
runtime work. Worker `/root/volume_math` writes only this report and ignored
`dev/logs/deep-shader-investigation-20261012/math/`. No Minecraft, GPU, Gradle,
remote process, native Goal, other agent or production source change was
launched by this worker. Existing runtime pauses and full 90-cell scope remain.

**Actual inputs and selected consumer.** The original archive is
`dev/upstream/other-pack-research/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip`,
SHA-256 `f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c`.
The native cloud member `shaders/lib/volumetricClouds.glsl` is
`3750109b2f73a19c825ff886396edb6327f3da07ae389bc1358f53b937b7229a`;
`shaders/dimensions/composite2.fsh` is
`de7eb25909fb3296d59816d596ec27917d6bc353079ab977d25d2f367c6eafb3`.
The actual entry is **world0/composite3.fsh → dimensions/composite2.fsh →
renderClouds → renderLayer**, not world0/composite2.fsh. The latter includes
dimensions/composite1.fsh, a different stage. Native primary include is line147,
call line405; the function is in the Overworld branch.

The authentic prior runtime observation at
`dev/logs/manual-shader-fps-20261011/bliss-r1/runtime-report.json`
(SHA `814c0b1a6efb7bd607118ed6af4e0de7f68988dec693d13e11ccf962f11bfca5`)
records original rendering at **2026-10-12 00:56:49 UTC**, Fabric Minecraft
26.1.2, Iris1.11.4, Sodium0.9.2, no DH, Overworld, 1920×1200,
camera256.5/95/768.5, yaw135/pitch9. Its actual 463-option map has
VOLUMETRIC_CLOUDS/CLOUDS_INTERSECT_TERRAIN/HQ_CLOUDS and all three layers true;
minRayMarchSteps=maxRayMarchSteps=15, VL_RENDER_RESOLUTION=.5, TAA=true,
TAA_UPSCALING=false, fbmAmount=.5, Daily_Weather=true. Specular, screen-space
and sky reflections are false. These are observed loaded options, not only
default declarations or proposed maps. Per-ray cloud occupancy remains unknown.

At 00:58:01 UTC the same report observes Performance selectedGraph
`certified-native-original`, rendererEligible=true, original463 options.
Current `src/main/java/com/pp/shaderlod/integration/makeupquad/RendererRolloutPolicy.java`
returns `route != null`; current PackSelectionService chooses a native graph
when `preset == PERFORMANCE && available`. Thus the older frontier/audit text
describing Bliss as research-flag-only does **not** describe this current
consumer. Balanced reliably requests the generated adapter; actual successful
Apply/loaded source must still be verified. Do not force another route or use a
larger preset to make the benefit claim. The report's later Balanced failure
was a camera-contract failure, and its prepared retry stays untouched.

**Exact implementation contract sent to the Sol owner.** Mark only the existing
primary Overworld include with `SHADERLOD_BLISS_PRIMARY_CLOUD_BUDGET` immediately
before the line147 volume include. Within the volume member, include the existing
`/lib/shaderlod/adapter.glsl` only under that marker. Its include guard shares
the three existing Core uniforms/helper with the following overworld_fog include.
Immediately after native line444:

```glsl
maxIT_clouds = int(clamp(maxIT_clouds / sqrt(exp2(viewPos.y)),0.0, maxIT));
```

insert the owned, Overworld, VOLUMETRIC_CLOUDS, no-DH/no-Voxy compile guard and
`maxIT_clouds = shaderlod_effect_budget(maxIT_clouds, 4);`. This must precede
native line459 `dV_viewTEST`, its `mult`, both layer step vectors and both
layerStartingPosition calls. The existing helper retains native work for Off,
Quality3, bridge other than2, enabled other than1, invalid preset/count/minimum.
Do not alter its policy, native options, per-step body, shadow density helpers,
cloud-depth parameters, outputs or temporal filters. No new per-step work is
required. Other callers—including the deferred sky atlas, translucent-pass
clouds and ground/cloud-shadow sampling—remain native.

The maintained Bliss recipe must pin the newly changed volume member and actual
entry wrapper; propagate its recipe/resource/catalog identity through the
existing adapter builder in an isolated root. Retain previous recognized source
identities. Reuse existing selection/recovery; no new loader ABI, sampler,
runtime route or broad rewriting mechanism. The Sol owner supplies exact overlay
bytes and adoption checks separately. Do not modify sealed Reimagined inputs.
NeoForge26.2 currently lacks a working Bliss correction certificate; the first
future candidate rendering should use an existing runnable Bliss tuple rather
than silently infer that compatibility.

**Equations and the preserved invariant.** Let N be native count, M the helper
count, v the native direction after curvature, H=100 the layer height. Native
geometric increment is `Δx = v H/(abs(v.y) N)`, while the optical multiplier is
`h = length(v 90/(abs(v.y) N))`. The **90 versus 100 ratio is intentional native
calibration** and must be preserved. Changing the count before both denominators
gives `M Δx′ = N Δx`, `M h′ = N h` in real arithmetic. Dither remains a fraction
of each stratum; physical support points change with the stratum width.

At sampled location i the native effective extinction is
`μ_i = cumulus_i * fadedDensity_i`; lighting L_i comes from the native three-tap
sun density kernel, cross-layer shadows, phase/powder terms and skylight.
For nonnegative finite values the native recurrence is

```
t_i = exp(-h μ_i)
C_(i+1) = C_i + L_i (1 - t_i) T_i
T_(i+1) = T_i t_i
```

Therefore a full visited homogeneous layer with constant **effective μ and L**
has `T_N = exp(-N h μ)` and `C_N = L(1-T_N)`. The candidate has exactly that
same real-arithmetic result, without multiplying radiance a second time by N/M.
The native exponential already carries the widened optical interval. Constant
raw cloud noise is not constant effective μ: the original bottom25-height fade
still varies. This is a test of the recurrence, not equality of native clouds.

The source also changes sample-dependent skylight occlusion: the single
upper-layer density evaluation before the primary loop uses its initial
rayProgress, which changes when the dithered increment changes. `cloudDepth`
is passed through these functions but has no assignment in this pinned body;
there is no first-contact feedback value to rescale. The existing compositing,
separate alpha and TAA remain source-identical, but their inputs change. Temporal
equivalence is not established by retaining their code.

**Integer work and total benefit.** Native viewPos normalization gives y in
[-1,1]. With the actual min/max15 options,
`N=int(clamp(15/sqrt(exp2(y)),0,15))` ranges from10 to15. The helper does not
always receive15.

| N | Balanced M | Generated Performance M |
| --- | --- | --- |
| 10 | 9 | 8 |
| 11 | 10 | 9 |
| 12 | 11 | 9 |
| 13 | 12 | 10 |
| 14 | 13 | 11 |
| 15 | 14 | 12 |

A fully occupied layer0 cell performs up to18 noise reads: four primary,
three three-read direct-light density calls, one three-read upper-layer call,
and two altostratus reads. Layer1 performs up to15. Thus full dense two-layer
N15→14 removes up to33 of495 per-cell reads; N15→12 up to99. Fixed upper-layer
skylight and altostratus work remain. Empty coverage can stop a density call after
two reads, and native terrain/opacity/bounds exits reduce visited work. Changed
positions can land in denser cells, so fewer primary steps do **not** guarantee
fewer actual total texture calls in a particular ray. No actual histogram or
hardware texture count was measured.

Added work is one existing helper invocation per primary renderClouds call,
uniform gate comparisons, ceiling/conversion and min/max; no new pass, texture,
history buffer, secondary trace, per-step check or explicit sampling change.
Native varying loop counts and divergent early exits already exist. The new
count can nevertheless alter derivatives and driver scheduling. If f is the
unknown original frame-time share of this selected cloud work, the optimistic
Balanced frame-time saving is approximately at most .10f before added overhead
and bottlenecks. For illustration only f=.3 gives at most3% frame-time reduction;
no measured value of f supports a larger promise.

**Approaches compared.**

| Approach | Work/quality assessment | Decision |
| --- | --- | --- |
| Bliss full-interval primary quadrature | Removes all work of selected cells, carries native absorption and source weights automatically; changes density, light, bounds and temporal noise | Implementation-ready private fallback candidate with modest Balanced ceiling |
| Retain all primary density, reduce/reuse only secondary cloud lighting | Can preserve primary opacity exactly, but three-tap direct lighting sees remote cloud support; smooth local density cannot certify smooth shadowing. Requires cache/selection overhead and a new reuse rule | Do not implement here; Photon already has prepared/rejected related lanes and no new decisive coverage premise was found |
| BSL/Solas replace per-step alpha by `1-(1-alpha)^(N/M)` | Preserves only constant-density transmittance. Native light weight `alpha*(1-cloud²)`, distance fade, softened cloudFaded and first-hit output have different coupled recurrences | Reject a count-only or alpha-only transfer; parent separately examines an exact-zero density shortcut |
| AstraLex shafts/clouds or Solas exponential shafts | Count affects empirical spatial powers, sums/normalization or nonlinear feedback; native Mac rendering remains blocked | Lower priority than runnable family; no unsafe count copy |
| Photon physical fog/primary clouds | Good mathematical seams, but nearby paths already have staged mechanisms and fixed no-gain or no-coverage evidence | Preserve closed work; no retuning/rescue |

For example, BSL homogeneous opacity has q=1-alpha and transmittance t. Across
two original steps its lighting's combined interpolation weight is
`1-(1-alpha*(2t-t²))*(1-alpha*(2tq-t²q²))`, which generally differs from the
single widened-alpha weight `(1-q²)*(2t-t²)`. Absorption correction alone fails
even that simple constant-input example before distance fade and depth feedback.

Preserved negatives include Bliss cloud-pruning R7 (candidate97/101FPS against
original112/110 and return105/107), Bliss fixed fog8→6 and raster/upscale
directions, and Photon primary-volume R5 (candidate117/112 against original117/117
and return108/112). Those noisy endpoint observations establish their fixed
no-gain decisions; they neither validate this new candidate nor close all
possible cloud work. No workload, preset, filter or threshold was changed to
rescue one of them. Prepared End-cloud, streaming hashes and weight carry were
read as prior work and were not rediscovered or altered.

**Checks actually run.** Read STATUS using checkpoint.py show, linked goal,
AGENT_OPERATIONS/EVALUATION, current throughput findings, native ZIPs and current
selection source. FSearch discovery worked initially; a later scoped multi_grep
returned transport-closed, so exact rg/direct archive reads supplied coverage.
Native ZIP/member hashes were independently calculated; extracted copies and
four-family pins are in
`dev/logs/deep-shader-investigation-20261012/math/native/pins.json`.

Command:

```sh
python3 dev/logs/deep-shader-investigation-20261012/math/check_bliss_quadrature.py
```

passes5,184 full homogeneous scalar comparisons across native10–15, both budgets,
six positive ray directions down to abs(y)=.0001, six extinction values, four
lighting values and three nonzero dithers. Maximum double absolute error against
the analytic result is1.94e-14; the explicitly rounded scalar binary32 model
reaches1.08e-5 with HDR lighting up to12. These are synthetic models of the
pinned recurrence, not compiled GLSL/driver, native textures, actual scene data,
or a shader error bound. Script SHA
`db1bf34d3e1669d19ede599cac95b147e709bd8cb7185b3ba2e23b47d23eb5ae`.
Results and counterexamples are in `math/quadrature-evidence.json`.

The model deliberately preserves counterexamples for camera inside the layer,
terrain-clipped prefixes, dither0 excluding the lower boundary, native opacity
termination, thin density bands and varying lighting. A synthetic4-height dense
band is sampled by native15 but missed entirely by selected12, producing a large
color difference. Homogeneous full-layer preservation is therefore insufficient
for image acceptance. An initial incorrect fixture band location was corrected
in the model only; `math/initial-fixture-failure.txt` records it. No implementation
gate was weakened. The Sol owner independently checks actual overlay assembly,
guards, unaffected callers, source recovery and consumer integration.

**Smallest decisive future observation.** After runtime is explicitly resumed,
use the existing runnable Fabric26.1.2 Bliss public-control consumer and frozen
native Overworld options. Observe successful original rendering, generated
Balanced source and actual Core2/1/0 reception, then native Quality/Off/Cancel
restoration. Existing source/debug facilities can establish selected N→M without
a new profiler. Inspect ordinary held and short moving views for cloud edges,
thin strands, terrain occlusion and TAA flicker, with native settle/wind/clock
limits recorded; a near/inside-layer image remains a required quality boundary
before broad support, not a way to enlarge a failed cost trial. Take the early
matched original/full-work-control/candidate/original-return total-frame sanity
on the unchanged chosen workload. Reject visible loss or no useful total gain
without tuning that fixed trial. DH/PBR, other tuples, actual hardware work and
motion quality remain unverified. This worker owns no live processes.
