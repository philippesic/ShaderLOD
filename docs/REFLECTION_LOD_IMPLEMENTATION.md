# Reflection footprint refinement LOD reference adapter

Source freeze r2 (guard cost), 2026-10-05. Independent reflection implementation track; owns only
this report, `scripts/build_photon_reflection_lod.py`,
`tests/test_photon_reflection_lod.py`,
`patches/photon-experiments/reflection-lod.json`, and
`shaderpacks/photon-reflection-lod/**`. Other workers own Java, shared shader
helpers and other experiments. Root owns client/reloads/GPU; this worker ran
no client, Gradle, GLSL driver compiler, GPU query or image processing.

## Implemented behavior

A transferable distance/projected-footprint policy shortens binary reflection
refinement only when remaining spatial uncertainty is bounded. Full original
intersection count, sampled ray positions, extent, dither, depth tolerance,
projection/depth sampler and environment coverage remain. No untraced far segment
is replaced with sky. Both smooth-water and rough reflection callers use this
adapter; original rough ray counts, BSDF factors and accumulation normalization
remain. The separate original raytracer stays byte-identical, so AO/SSGI callers
are unaffected. There is no hierarchy/new pass/new target allocation.

Entry points are original `trace_specular_ray` in
`shaders/include/lighting/specular_lighting.glsl` and new
`shaderlod_reflection_depth_trace` / `shaderlod_raymarch_depth_cached` in
`shaders/include/shaderlod/reflection_depth_cache.glsl`. Compile option
`SHADERLOD_REFLECTION_DEPTH_CACHE` is default off; its name includes the
secondary exact integer-depth reuse path. Pinned settings and the single
specular caller are the only changed original members; one helper is added.
Original Photon v1.3b by Benjamin Stott (SixthSurge), original LICENSE retained.

Activation requires core protocol2, global enable exactly1, finite nonnegative
near/far/transition with far>near and 0<width<=far-near, finite receiver distance,
valid original dither, positive original budgets and bounded supported counts
(intersection<=256, refinement<=64). Unknown/absent mod, disabled bridge, invalid
policy/counts and full near execute the literal upstream tracer. These are
source-consumed core fields; live uniform linkage/reload remains unverified.
Extension1 per-effect fields, debug and preset are not consumed. No pack option
is represented as an actually verified mod connection.

Distance is Euclidean camera-relative view space in Minecraft blocks. Below
near+width/2 (default36 blocks), the original trace executes. Smoothstep from
near+width/2 to far+width/2 (default36..100 blocks) increases tolerated remaining
hit displacement from zero to0.25 depth-texture texel. The transition is continuous
in error tolerance; discrete iteration changes still require motion QA.
Footprint is calculated explicitly from the projected ray step and effective
view resolution/TAAU scale, without derivatives or implicit LOD sampling.

At least one original refinement update executes before stopping. Every possible
remaining original position must fit inside the same valid fetched depth texel
with conservative boundary margins. Thus its sampled hand/sky/terrain depth and
original final-depth eligibility are preserved. In addition, the entire remaining
z envelope must lie uniformly inside/outside the original depth-hit predicate
with a margin, projected UV displacement must be<=0.25texel, and remaining screen
depth uncertainty<=1/16 original depth tolerance. Screen depth is nonlinear:
the actual pinned Photon project() structure supplies a division-free homogeneous
endpoint bound, with an explicit float32 error envelope. Homogeneous W must have
no pole, and conservative Euclidean view displacement must stay within0.049block,
reserving1mm toward a0.05block cap. Near/far view displacement is never inferred
from NDC error alone.
This bound does not prove perceptual parity of reprojected color or fog.

A secondary cache reuses only the immediately preceding valid integer texel's
LOD0 depth before issuing `texelFetch`; invalid/out-of-range fetches are never
reused. Original ray arithmetic and every coarse hit test remain. First refinement
can reuse its just-found coarse depth. Thin edges, different texels and failed
error/classification envelopes continue the original refinement updates. No ray
weights or normalization change, and original sky/environment/fog composition is
retained. Fixed-depth source assumptions require immutable sampled attachment
through a draw, as original explicit depthtex1/combined depth usage intends.

## Static acceptance and exact artifacts

`python3 -m unittest discover -s tests -p test_photon_reflection_lod.py -v`
passed12 tests, no skips. Coverage: literal disabled original spec/settings bytes,
unchanged raytracer/license, original coarse loop/weights/composition, fail-closed
pins/scope/errors, near/fallback policy,3000 exact cache models,5000 bounded
refinement/hand/sky/error models, undefined-coordinate nonreuse, deterministic
archive/license/member-scope assembly, and actual pinned Iris
OptionAnnotatedSource/OptionSet/MutableOptionValues option discovery/defaults/both
overrides. The private gate is the sole new boolean option; stage macro does not
leak. The actual lexer fixture uses local Java source launcher, no Gradle/download
or GLSL driver compilation. Legacy interval/cache fixtures use host double arithmetic. New guard fixtures
use rounded float32 expressions, actual successive original updates (every sign
sequence through7updates), original three-projection endpoint checks and the
pinned project() shape with FMA/non-FMA project variants. The1400deterministic
matrix/position trials accept>500cases including>40nonlinear far-depth cases;
all accepted original future positions remain<=0.05block. NaN/Inf, extreme
matrices/rays, cancellation, both W signs and pole/underflow cases are checked.
These host fixtures do not prove every GLSL compiler lowering or actual DH/TAAU
driver matrix; their source guard deliberately fails closed outside its envelope.

Original archive SHA256:
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Original LICENSE SHA256:
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
Manifest:
`0c0905612ddacf358b92c2d112bb17f2d107de224551d418a6a67c8529ccf4ec`.
Helper:
`50d5de6999f19157efe43430cf8c7942985e0ca3dad42d0f4ae36fbc1cc7dc4d`.
Default-off archive `build/Photon-ReflectionFootprintLOD-disabled.zip`:
`485590fa1a70ce43e4fe271a8a19f869d0cbf69767f1bda435b5deed9e41aa35`.
Explicit-on source archive `build/Photon-ReflectionFootprintLOD-enabled.zip`:
`2d461ed725bc24cc27faf9bc9674ed90362350885c8cb66f8988c9bac9d5af58`.
Both have2 modified original members,529 unchanged original members, one added
helper and private metadata. Builders reject nonignored outputs, unknown archive,
unsafe/duplicate ZIP paths, changed source/license/helper and unknown control arms.
Generated archives remain ignored. Source-off means upstream paths; global-off
with the option compiled still includes wrapper validation overhead.

## Runtime stopping boundary and needed measurement

No runtime evidence exists for this r2 adapter. The prior r1 archive
d11de4…a82e16 compiled/rendered in the native noDH untimed smoke and linked
core2/enable1/32/96/8. Its GPU stream was empty; one still was not motion/parity
acceptance. That r1 evidence does not establish r2 driver rendering or cost. A substantial remaining risk is
branch/register/cache-key/projection ALU overhead exceeding saved depth fetches,
particularly the reduced-profile4-refinement configuration. Prior AO texel-cache
regression and far-SSR caps with inconsistent gains are adverse evidence, not a
reason to assert this candidate works. Do not promote defaults or infer GPU gain
from scalar loop savings. This bounded refinement implementation was selected over
unsafe empty-segment advances without a conservative depth hierarchy; the actual
full coarse ray still visits every original sample and hits remain available.

Root/runtime operator should first qualify the exact archive/manifest/helper and
private Iris option, compile/render the native reduced-profile noDH reflection
scene, and read back the five consumed core fields. Keep same default-off archive
and private option true for global OFF/ON measurements, with original Photon
mod-absent as independent baseline. Use balanced OFF/ON reversal and repeated
original/off/on total-frame plus GPU elapsed timing with matched fullscreen
resolution/scene/clock/wetness/focus/warmup; pause CPU builds during measured windows.
Reject cross-arm focus/resolution/history/option changes. Runtime preparation must
explicitly qualify these new hashes; existing SSR-cost source whitelist cannot be
repurposed as acceptance for this new adapter.

Before any default, inspect full-near, transition endpoints, far terrain/sky/hand
reflections, thin poles, silhouette/color edges, moving water/glint, grazing views,
slow translation/yaw reversal, original4 and higher refinement options, TAA/TAAU,
DH-present combined projection/depth plus noDH, absent/invalid bridge, shader and
config reload. Confirm no new bright band and bounded-world-error fallback under
far-depth amplification. A successful driver render is separate from quality and
from GPU improvement. Stop/reject if safety ALU regresses or eligible footprint
savings provide no useful repeated total-frame/GPU signal; keep failure evidence.


## R2 guard cost revision and numerical invariants

Implemented only review items1–3 in the isolated managed
`/Users/pippo/.codex/worktrees/reflection-guard-cost/shaderlod` worktree based
24f4d3d. Main archives/runtime/combined pins are unchanged. Coarse depth cache
(item4), all original fetch positions/weights, sampler/projection selection,
0.25texel threshold,1/16depth tolerance and policy interface are unchanged.
No new options, consumer uniforms, passes or target slots are introduced.

Uniform policy must activate before radial length is computed. The refinement
stop guard accepts remaining2..7 only. Remaining1 or>7 runs every original cached
update; larger total budgets may become eligible later when their remaining count
falls to<=7, so this is a per-attempt conservative subset, not a whole-ray count
change. Extent uses the exact float32 binary fraction
`1 − (1 << (7−remaining))/128`; this removes two exp2 expression evaluations and
accuracy assumptions. Necessary pixel/span-z limits execute before texel envelope
or inverse-space work. There is still no late texture fetch whose result is thrown
away. Early stop saves no additional depth fetches over exact cached continuation;
it saves only original cached refinement updates/key/hit tests. Cache and stopping
cost/benefit must remain separate in measurements.

`shaderlod_reflection_view_bound` replaces three endpoint projections/three vec3
divides with one homogeneous center/change calculation, beginning with scalar z/W.
Its z error is a necessary cheap rejection before x/y work. It uses the actual
pinned global project helper (global SHA256
6235933d0045282ba9d7822d55a40aaff6b76ac862c550e33f9b8a4229d63c3d).
The manifest and executable fixture pin both this member and exact helper text;
the existing builder's full original ZIP SHA authenticates the same source, so
no builder changes were needed.

For H=project(M,2pos−1), dH=diag(M)*(2step*extent).xyzz, the exact endpoint change
is ±(dH.xyz*H.w−H.xyz*dH.w)/(H.w*(H.w±dH.w)). Without rounding, absolute W has
minimum |H.w|−|dH.w| along the segment. The guard adds a homogeneous difference
error envelope E, reduces minimum W by E.w, and inflates the numerator by
`E.xyz*|H.w| + |H.xyz|*E.w`, expression cancellation error and two projected
divisions' rounding bounds. No actual divisions are computed in the guard.
Finite numerator/denominator squares and conservative outward factors reject
Inf<=Inf, NaN and denominator poles.

The E envelope is1e−6 times positive matrix/coordinate/offset/current/change terms,
plus1e−30. For round-to-nearest float32 u=2^-24, at most7successive future position
adds have gamma7=7u/(1−7u)<4.18e−7. The chosen positive scale covers that accumulated
position error, both NDC conversions, project/change multiplication/addition and
FMA/non-FMA differences; it uses matrix magnitudes rather than only cancellation-
small H. Finite matrix entries/offsets<=1e6 and pos/step components<=4 bound all
products/squares below overflow. W and its error-reduced minimum must exceed1e−6.
Numerator inflation1.00001, denominator deflation0.99999 and a second squared-cap
deflation cover rounding in the positive bound expressions. The1mm final world
reserve is retained. The explicit tiny error floor dominates float32 underflow
error in bounded products; tiny squared numerators cannot bypass a pole rejection.
No same-z shortcut or unverified derivative shortcut was added.

This conservative envelope can reject most far-depth attempts, so fewer source
divides does not imply a GPU or total-frame improvement. Additional safety ALU,
cache branches/registers and footprint eligibility distribution remain risks.
First r2 acceptance needs a fresh source-qualified driver/reload/readback smoke;
then matched repeated original/off/on GPU and total-frame comparisons plus a
cache-only attribution control and reflection/fog/motion checks. Archive pins in
queued r1 and combined runs must never be silently replaced with this revision.
