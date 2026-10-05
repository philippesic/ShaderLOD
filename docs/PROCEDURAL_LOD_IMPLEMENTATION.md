# Projected procedural detail LOD source freeze

2026-10-05. Owner: `procedural_lod`; bounded read-only mathematics advisor:
`procedural_lod/parallax_math`. Source implementation is private, default off,
experimental and **unaccepted for quality or performance**. Neither worker owned
clients, shader reloads or GPU measurement. Root retains runtime ownership.

## Scope and actual interface

General carrier/modulator footprint weighting lives in
`shaderpacks/photon-procedural-lod/shaders/include/shaderlod/procedural_footprint.glsl`.
It accepts caller-supplied procedural coordinate partials in units per raster
pixel, a carrier in cycles per coordinate unit and a modulation gradient bound.
It uses those units directly; camera distance alone does not select an octave.
The explicit Photon consumer retains 1..8 `WATER_WAVE_ITERATIONS` options.
No universal rewriting, other pack support or default preset change is introduced.

Only two upstream members are patched: `shaders/settings.glsl` and
`shaders/program/gbuffers_all_translucent.fsh`. Three private helper files are
added. The original water source, its Belmu Gerstner comment, original Photon
LICENSE (also included in this derivative source directory), all shadow/caustic code and DH entry points remain unchanged. Relevant
entry points are world0/world-1/world1 `gbuffers_water.fsh`, through the common
translucent program, only with `PROGRAM_GBUFFERS_WATER` and `WATER_WAVES`.
Hand-water and other programs do not select the private route.

The sole added pack option is `SHADERLOD_PROCEDURAL_FOOTPRINT`, commented out by
default. The selected route checks existing core `shaderlodBridgeVersion == 2`
and `shaderlodEnabled == 1`. It consumes no extension1 per-effect fields, near/far
mod config or new Java API. Near/far and footprint thresholds below are explicit
private source policy, not a verified mod-config connection. Actual uniform
linkage/readback for this derivative is pending.

## Footprint, work avoidance and fallback

The unchanged Photon normal evaluates **three**, not five, height samples at
`coord`, `coord+(0.1,0)` and `coord+(0,0.1)`. This revision retains those finite
differences, the original normal influence, flow classification, rain/skylight
and grazing suppression. It does not use the prior analytic-gradient approximation.

Full original normal evaluation remains selected through 32 Minecraft blocks.
A smoothstep from 32 to 48 blocks enables footprint filtering. Each octave's
carrier-plus-noise upper phase-frequency bound is evaluated using the actual
raster partials of its procedural coordinate. Squared sine has a second harmonic:
weights fade between 0.125 and 0.25 fundamental cycles per raster pixel (eight to
four pixels per fundamental cycle). The first octave always stays full; full-N
amplitude normalization remains unchanged. Footprint weights are computed once
and shared by the three height evaluations.

Zero-weight octaves skip noise lookup and trigonometry **before** that work.
Each omitted octave contributes its exact uniform-phase mean, 3/8, multiplied
by its original amplitude. Low-frequency height variation remains sampled and
multiplies the entire mean-containing field. This avoids amplifying the coarse
wave through reduced-N normalization or lowering mean height merely by dropping
amplitudes. Retained coarse height/slope amplitudes are preserved.

This is not a calibrated conditional noise mean or a guarantee of equivalent
normal variance/specular energy. High-frequency slope variance is intentionally
removed; no BRDF roughness compensation is applied. The modulation bound is an
upper bound, so it can conservatively overfilter actual resolved detail. Near
identity and phase mean do not establish distant sun-glint parity. The angle
fixture below exposes a material quality risk that runtime review must resolve.

`WATER_PARALLAX=true` is supported. Before any discard, the first `main` block
initializes coordinates in all invocations, reproduces upstream corrected TBN and
flow setup, and evaluates full-quality parallax on water primitives when core
activation is valid. Glass does not run that height march. Returned coordinates
are reused at the original parallax site. After the material branch and adaptive
while have completed, unconditional `dFdx/dFdy` calls capture actual displaced
coordinates before the later material/normal LOD branch. Thus this does not guess
a world-distance ray cone or reuse the unwarped coordinate footprint.

[GLSL 4.10 section 3.8.2](https://registry.khronos.org/OpenGL/specs/gl/GLSLangSpec.4.10.pdf)
says uniform control resumes after divergent substatements complete. Derivatives
at that reconverged point are raster approximations, not exact analytic Jacobians.
All operands are initialized, there is no discard or shader exit before capture,
and material/TBN inputs are flat within a primitive. Derivatives of the validity
flag execute in separate unconditional statements, avoiding short-circuit evaluation.

The full-height/parallax private clone preserves original arithmetic/loop counts
and only qualifies noise samples at explicit LOD0, plus invalid-path guards. Pinned
Photon custom `image/noise.png` is 512²; its mcmeta says blur=true/clamp=false.
Pinned Iris custom-PNG upload bytecode selects LINEAR min/mag and level-zero upload.
Reduced height uses explicit `textureGrad` with captured partials. These paths
introduce no implicit-gradient sample inside new dynamic control. Actual sampler
binding/mip state and driver sampling equivalence still require runtime readback;
upstream texture syntax alone does not prove undefined sampling with a no-mip
texture. Full near normal calls remain literal original; active full parallax is
mathematically preserved but no bit-identical GPU claim is made.

Zero initial height, singular/reversed tangent direction, singular interpolation,
nonfinite coordinates or excessive march count mark the private coordinate invalid.
The original downstream parallax/normal route remains the fallback, including
upstream behavior on those inputs; the private safety guards do not claim to fix
an upstream zero-height interpolation issue. A quad validity boundary also selects
full normal. Large parallax footprint amplification fades filtering out between
2× and 4× the unwarped footprint; at 4× full original normal is selected. This
protects unstable displaced-coordinate regions without a hard normal switch.

Private option off or other programs restore every upstream source byte after
resolving only new guards. Core absent/unknown or global off retains original
parallax and normal work. Enabled-source/global-off can retain small derivative/
control overhead; it is a distinct timing arm from pure original. Singular
persistence >=2.0 also falls back; Photon itself has singular full normalization
at persistence2.0, so this is baseline retention, not a mathematical repair.

## Static and mathematical evidence

Executed `python3 -m unittest discover -s tests -p test_photon_procedural_lod.py -v`:
**13 tests passed, no skips**, under approximately one second on this host.

Checks include exact private-off/nonselected upstream source restoration; unchanged
license/water/shadow/DH members; fail-closed source/helper/archive pins and output
scope; original full-height arithmetic restoration after reversing only sample
qualification; scalar full near identity; full-N/coarse amplitude retention;
exact 3/8 phase mean; bounded bilinear noise variation; smooth distance/footprint/
parallax-confidence boundaries; 1..8 octave fetch omission; flow/noise repeat
seams/height-variation/frequency/persistence/ray safeguards.

Actual pinned Iris `OptionAnnotatedSource`, `OptionSet` and `MutableOptionValues`
ran through its real Java lexer. Complete settings/program/stage inputs retain
exact baseline boolean options/references plus the sole private gate. The upstream
shared program already references `PROGRAM_GBUFFERS_WATER`; this test checks no
additional stage leak rather than wrongly asserting baseline absence. Default
false/true and both overrides pass. This is option parsing, not driver compilation.

The independently expressed scalar parallax reference differs by at most
`3.457650183405701e-13` coordinate units in the chosen fixture set. Neighboring
actual mapped coordinates produce up to `249.7361861445753`× footprint expansion,
with five terminal-count switches, supporting the explicit confidence fallback.
Normal fixtures using actual pinned green-channel noise, 1/3/8 octaves, several
flows/times and influence values through 0.38 produce a maximum sampled angular
change of **22.115413607216066 degrees** when fine waves are fully filtered. Near
change is zero in the scalar model. This is a finite fixture envelope, not a
worst-case proof or a perceptual acceptance bound. Distant quality is unaccepted.

No client, Gradle build, GL shader compiler, runtime reload, driver program linkage,
physical texture-count observation or GPU timing ran on this track. No performance
claim follows from skipped source statements or these tests.

## Exact assembly

Pinned original Photon v1.3b ZIP SHA256:
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Original LICENSE SHA256:
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
Pinned Iris 1.8.12-local JAR SHA256:
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`.
Manifest `patches/photon-experiments/procedural-lod.json` SHA256:
`d504b81be34ce45b63e37bc51e2c663f470bd173b2e77a4e372e31af36578a9d`.

All three arms were actually assembled by `scripts/build_photon_procedural_lod.py`
under ignored `build/`. Their ZIPs include attribution and audit metadata.
Original arm retains all 531 upstream members unchanged; disabled/enabled retain
529, change exactly two and add three helpers. Container bytes differ from original
because deterministic packaging/audit metadata are added; the actual pinned
original ZIP remains the comparison reference.

| Assembled source arm | Ignored archive SHA256 |
| --- | --- |
| Original members plus metadata | `38f57e66d73cc61c4faf061fc510410b661582bd8d2562292406d4552d5c461f` |
| Private option default off | `894c5c37de5d0427f283775a01fec0de1a4fde5a2162d108993bd648b5b96a1a` |
| Private option default on | `4423eb17943e18b75adb5173899ec7bf716d56a3aeb68e64f35fbae076139992` |

## Runtime queue and measurement needed

Root/operator must explicitly claim one client/reload/GPU slot before testing.
Use existing locked Minecraft1.21.1/NeoForge21.1.253/Iris1.8.12-local/Sodium0.6.13/
Photonv1.3b runtime; DH3.3.3 present/absent remains a separate compatibility check.
Stop at source freeze until integration review. Other feature tracks may continue
source work but freeze heavy tests/builds/assembly during measured capture windows.

First smoke is current native2976×1672 water-heavy coastal/sun-glint view, matched
upstream options including three octaves and WATER_PARALLAX=true. Confirm actual
private option map, driver compilation/rendering, core2/global1 and noisetex sampler
LINEAR/level0/normalized custom image binding; no unsupported-stage leak. Compare
literal original, private-source/global-off and private-source/global-on at the
same pose/time/weather/resolution with fixed focus and workload contracts. Check
near/mid/far, glancing incidence, flow/vertical sides, moving camera, wet/noon glint,
repeat seams, material edges, underwater and reload/off restoration. Include a glass
scene to check newly hoisted water-stage work does not regress unrelated materials.

If quality fails, retain off and revise the filter/energy treatment before timing
promotion. If it passes, use repeated balanced quiet blocks, matched total-frame
and asynchronous elapsed GPU intervals with median/p95 and variance; keep shader
query overhead control separate. Original versus optimized GPU timings are required
before claiming performance, and any total gain is workload-specific until repeated
across a second ordinary scene. Full parallax remains full work, so three-octave
normal savings have limited headroom and cannot by themselves establish the 50%
project target.
