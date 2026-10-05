# Distant diffuse lighting at a lower spatial rate

2026-10-05 source-only feasibility plan. No shader, builder, compiler, test,
archive or runtime changed. Photon remains the sole target. The density r2 pilot
reported original/off/on GPU means 15.4416/15.3720/14.9639 ms: one quality-changing
block is insufficient motivation for density calibration or evidence of the
1.5x-throughput goal. Investigate a larger opaque lighting region instead.

**Feasible first experiment:** evaluate complete original diffuse illumination
and receiver shadows once per valid distant 2×2 tile, cache an albedo-independent
coefficient, and multiply it by each pixel's original full-resolution material
albedo. Preserve full-resolution specular calculations, overlays, fog and cloud
composition. Near/unsafe pixels execute original shading. This requires a real
prepass/resolve graph and a spare texture with audited lifetime; it is not a
late branch around already computed lighting or a final-color blur.

## Pinned separation and actual costs

Photon v1.3b original ZIP SHA-256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Retain Benjamin Stott/SixthSurge attribution and original Photon LICENSE.

| Source member | SHA-256 | Required boundary |
| --- | --- | --- |
| `shaders/program/d4_deferred_shading.fsh` | `212081e707c20c78e17cbfd3380b2f22eb696cc1aa14bdf006c07d59f53f67e0` | Materials 335–409; AO reconstruction 317–333/414–464; shadows 483–543; diffuse 552–575; highlights 580–582; reflections 591–603; full-resolution final composition 608–680 |
| `shaders/include/lighting/diffuse_lighting.glsl` | `e4d10857d779d0e461290c0d09c594cbaf9ac45e1a3db541d4ce9b7af4abaf04` | SSS 68–117/169–195; sky 220–250; blocklight 252–272; emission 274; final albedo multiplication 284–285 |
| `shaders/include/surface/material.glsl` | `23b1ab4f304d2c02ffe497006b5feefd649ab3176c01251fe5e8cd79cee66d78` | Material fields 10–22; defaults 140–153; ordinary mask 0 branch 167–174 |
| `shaders/program/d4_deferred_shading.vsh` | `787f03ad720b84af642fd4772e2ae397b6ced1425650be65f860e0a41dcc8d40` | Flat light/ambient colors; optional sky SH; original view uniforms and exposure |

The expensive region is not just `get_diffuse_lighting`. Before it, each terrain
pixel currently reads four AO and four AO-depth taps, reconstructs bent normal,
evaluates original filtered shadows and possibly SSRT, and constructs light
products. The reduced profile uses original PCF options 6–12 and SSRT option 10;
these are configured budgets, not measured executed fetch counts. Diffuse itself
adds directional/bounced light, SSS, sky/block light, darkness/night vision/cave
terms and material emission. Material decoding, sky/cloud/atmosphere processing,
specular, edge highlight and final composition would remain largely full rate.
No fresh reliable isolated cost/share is known for the removable region.

`get_diffuse_lighting` is not generally albedo independent: SSS uses albedo,
sheen includes its reciprocal, and emission may already encode albedo. Dividing
lit color by representative albedo is invalid and unstable at dark pixels.
First restrict to ordinary nonmetal, zero SSS/sheen/emission materials. For those
inputs, evaluate the original diffuse function with canonical white albedo and
zero exceptional fields; its result is the coefficient including `1/pi`.
Current full-resolution albedo then reproduces its high-frequency texture detail.
Do not extend this factorization to foliage, metals or emission by assumption.

## Two-pass implementation boundary

1. After original AO `deferred3`, run a quarter-pixel-count lighting prepass.
   Cache one coefficient per 2×2 tile. Cheap validity checks precede all AO
   reconstruction, PCF/SSRT and diffuse work. Invalid/near tiles write invalid
   alpha and perform no expensive lighting.
2. Full-resolution resolve decodes current material/albedo as upstream. A valid
   far tile supplies coefficient RGB and representative scalar shadow in A;
   otherwise execute the untouched full-rate lighting region. Highlights and
   reflections retain each pixel's current material, normal, view direction and
   original functions. Eligible rough surfaces reuse the scalar receiver-shadow
   factor; strong specular/reflection surfaces fall back to fully original shading.

The first cache allows only `SHADOW_COLOR=false`, so receiver shadow RGB is a
single scalar. Use alpha `<0` for invalid and `[0,1]` for that scalar. No colored
shadow approximation or missing-shadow fallback. Original AO reconstruction,
PCF/SSRT counts/distribution/radius and diffuse function execute at the computed
representative; shadow casters, sunlight cadence and shadow-map refresh are
unchanged. This still approximates spatial lighting between representatives.

Prototype material whitelist should initially be raw material mask 0 under the
verified reduced options. Its pinned branch has no SSS/emission/metal behavior;
cheap raw packed-field tests avoid running the full expensive material helper
four extra times in the classifier. Resolve still runs that helper per pixel.
Reject any options that invalidate this interpretation; widening the whitelist
requires explicit source proof and quality evidence.

## Texture lifetime, stage and Mac slot audit

Pinned Iris JAR SHA-256
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`.
Read-only `javap -p -c` inspection established:

- `PackRenderTargetDirectives` creates baseline supported targets with `i<16`:
  ordinary logical slots 0–15. Upstream `colortex16` is Voxy-specific, **not a
  free ordinary Iris render target**.
- `ProgramSet.readProgramArray(..., boolean)` loads numbered graphics passes
  base/1…99. Letter suffixes in `readComputeArray` are compute passes; do not
  propose `deferred3_a.fsh` or a GL 4.1 compute implementation.
- `ShaderProperties` parses `scale.<pass>` into `ViewportData`; `ProgramDirectives`
  forwards it; `CompositeRenderer` multiplies target view width/height by its
  scale before setting the viewport. A real `scale.deferred4=0.5` prepass can
  execute one fragment per 2×2 full-resolution tile without reducing cloud buffers.
- The existing fragment limit/Iris reserved-unit audit gives 14 dynamic units
  on this 16-unit Mac. Every linked fallback sampler still counts; a runtime
  global-off branch does not free a texture unit.

| Buffer | Lifetime and decision |
| --- | --- |
| `colortex2` | Best first cache candidate. Full-resolution RGBA16 gbuffer1 is neither written by solid shaders nor used by d4 when normal/specular mapping are off. It is next overwritten by optional TAAU min/max preparation `c3`; current profile has TAAU off. Use strict maps-off/TAAU-off profile and a private RGBA16F format override for signed invalid alpha/HDR coefficients. |
| `colortex9/10` | Cloud current buffers die after d2, but are quarter-linear resolution at current cloud factor 4. Reusing them for a simple 2×2 cache would require packing or altering cloud render dimensions/viewport; avoid that first. |
| `colortex11/12` | Live cloud history/data, including next frame. Not free. |
| `colortex3/13` | Overlay/refraction/translucent/fog or galaxy lifetimes and formats. Reuse introduces avoidable consumer/clear/format hazards. |
| `colortex1/4/5/6/7/8/14/15` | Gbuffer, sky, temporal scene/AO/fog/coverage or DH depth consumers remain live. Do not borrow them. |

Keep colortex2 physically full resolution: the half viewport writes its lower-left
quarter as a logically half-resolution cache; resolver fetches `pixel/2` there.
Do not apply a global half-size override to colortex2 or change cloud factor.
Inspect actual target flip/main-versus-alt tracking and format directives before
driver acceptance. Every visited cache pixel must write valid data or explicit
invalid alpha each frame; never rely on stale contents/implicit clears.

Insertion needs new numbered graphics work: private prepass at `deferred4` and
original shade/resolve at new `deferred5`, retaining `PROGRAM_DEFERRED4` as the
original shader's semantic macro. Private off leaves original d4 and disables
new d5. Private on/global off runs a cheap invalidating prepass and full original
resolve. Existing `deferred4_a.csh` is optional SH work; first prototype requires
SH_SKYLIGHT off and must not silently move/enable it. A later SH-compatible graph
needs an explicit compute association audit.

One new active sampler (`colortex2`) is added to resolve. Existing source estimates
suggest reduced noDH may have headroom, but they are not fresh reflected counts.
Require actual linked sampler enumeration and Iris registration. Initial private
adapter excludes DH/VOXY, normal/specular maps, TAAU, colored shadows/LPV and SH
skylight; unsupported configurations compile/run the full original graph, not a
partially allocated cache. DH users keep original rendering; later optimization
requires checked sampler headroom and explicit vanilla/DH boundary handling.

## Classifier, transitions and temporal behavior

Use stable screen tile origin `2*(pixel/2)` and a fixed representative phase,
initially `(0,0)`. No frame-rotating sample phase/history cache. Evaluate every
frame using the current original projection/jitter; let upstream TAA accumulate
as usual. Fixed tiling still needs motion tests for swimming and shadow flicker.

All four tile members must be in bounds, terrain, non-hand, the approved material
class and lightmap range; reject overlays, moving entities/special masks, water,
wetness/puddles, material maps, strong specular/reflection surfaces and nonfinite
inputs. Compare reconstructed positions against the representative surface plane,
not merely raw depth or equal Z on sloped ground; check decoded normal agreement
and lightmap differences. Start with conservative tolerances and expose them as
private adapter parameters. Odd viewport edges and failed weights use original.

Geometry/material agreement cannot detect a cast-shadow edge on a flat surface.
Consider one original center shadow comparison per tile member plus local
AO/bent-normal variation as inexpensive practical lighting-edge rejection.
Measure this cost; it can reduce the gain. It is not a proof of PCF/SSRT spatial
equivalence. Reject uncertain penumbra/contact regions and inspect thin foliage
shadows, ground shadow boundaries and animated shadows early. Budget preservation
at representatives alone does not preserve visible spatial shadow detail.

Distance is radial camera/view-space blocks, conservatively the minimum of all
four member distances. Near tiles always use full rate. A centered distance band
can blend the coefficient/receiver-shadow factor with original full shading;
transition pixels pay both evaluations. Fully far, valid tiles take only cache
lighting. Make that extra band cost explicit. A 2×2 screen tile is the spatial
footprint; distance does not prove lighting is low frequency, so validity/quality
checks remain necessary. No new derivatives inside divergent shading branches.

This first consumer has discrete rates K=4 or K=1 per four-pixel tile. Do not
claim a generic integer count cap of K=2/3 is consumed, or multiply lighting by a
retained-work ratio. A future OPAQUE_LIGHTING adapter can expose validated spatial
rate/distance controls; mod config, declarations and actual consumption stay
separate. Static approximation does not need new temporal-history interfaces.

## Minimal kill test and feasibility gate

Before substantial pass-graph work, a narrowly pinned **quality-changing cost
control** can replace only the eligible far lighting region with cheap declared
irradiance/scalar-shadow values, while retaining current albedo/material and
full-resolution specular functions/composition. Its scalar-shadow diagnostic
changes lighting, including highlight illumination; it establishes neither
quality parity nor an isolated effect budget. Keep near/invalid pixels original.
If even this aggressive regional upper-bound control has little repeated
frame-only GPU/total-frame signal, stop before a prepass/resolve implementation.

If the signal merits it, the smallest honest optimization test is the actual
K1 cache plus K4 fallback above. Preserve original/default-off controls; measure
global off/on with fixed scene/focus/warmup, quiet teams and balanced order.
Require near texture/albedo parity, stable distant surfaces, thin/material/depth
edges, cast-shadow/SSS/glint cases and motion/TAA before accepting little
discernible loss. Measure whole-frame GPU/total time including prepass and all
fallback/edge overhead. A debug accepted-tile map helps interpret eligible area,
but no eligible fraction, GPU saving or 50% target progress is established here.

The main risk is coverage: forest foliage/SSS, wet reflective coasts and near
geometry may mostly fall back, while all pixels still pay classification and
full-rate material work. The sampler/pass/format implementation cost is real.
Proceed only if the bounded far lighting region has materially useful measured
cost; otherwise widen neither quality loss nor architecture to rescue a weak
signal. The transferable strategy is reusable illumination sampling plus
full-resolution material resolve in explicit deferred adapters, not a universal
shader rewrite.
