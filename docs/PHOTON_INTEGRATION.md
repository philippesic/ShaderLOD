# Photon integration

ShaderLOD is an explicit integration with pinned Photon v1.3b. The NeoForge mod
supplies live settings through Iris; a reviewed patch manifest lowers actual
sample/ray budgets in shadow PCF, screen-space shadows, GTAO/SSAO, volumetric
fog, LPV fog, volumetric clouds, water wave normals/parallax, SSR, and POM. Installing DH uses Photon's
existing combined buffers and projection transforms, with the same distance
policy applied to coarse terrain rather than excluding it.

These are rendering experiments. Static compilation establishes source validity;
it does not establish GPU savings or acceptable visual quality. The runtime
coordinator records driver/render and timing evidence in the active goal. The
Apple development profile disables some Photon features to fit its sampler
limit; SSR/POM/LPV checks can be static even when the comparison does not run them.

## Build and select

```sh
python3 scripts/build_shaderpack.py
python3 -m unittest discover -s tests -p test_shaderpack.py -v
```

The builder writes `run/shaderpacks/Photon-ShaderLOD.zip`; select it in Iris.
The **ShaderLOD** options screen exposes enabled, mod bridge, geometry distances,
transition width, preset, separate cloud distances, and tier colors. Default
geometry distances are 32/96 blocks, with eight-block transition bands. Cloud
entry distances use independent 512/4096-block thresholds with a 256-block band.
The fallback preset is Performance. `SHADERLOD_CACHED_SKY` is an opt-in global
lighting/reflection approximation and is disabled by default.

The archive comes from the exact Photon ZIP in `dev/runtime.lock.json`, reviewed
literal replacements in `patches/photon/v1.3b.json`, and the new helper in
`shaderpacks/photon/shaders/include/shaderlod/lod.glsl`. Source SHA-256 guards
and unique anchors fail closed. Overlays cannot replace upstream files. Builds
normalize paths, timestamps, order, and permissions and embed provenance in
`shaderlod-build.json`. This setup adds no universal GLSL rewriting mechanism.

Photon's license and untouched source files remain byte-identical. Photon has a
custom license with redistribution restrictions through monetized platforms;
retain its license with substantial copies and review before distribution.

## Work reduction

Near quality preserves each original budget. Disabled LOD always selects near
quality, including when the mod config is selected. Budgets are caps: no preset
raises common Photon work, and the disabled comparison uses the same source and
common settings. Fractional caps round up and obey an effect-specific minimum.

| Preset | Medium budget fraction | Far budget fraction | Far AO |
| --- | ---: | ---: | --- |
| Balanced (0) | 75% | 50% | Reduced |
| Performance (1) | 50% | 25% | Reduced |
| Aggressive (2) | 25% | 12.5% | Bypassed |

Some effects have explicit discrete policies as well as these fractions:

| Effect | Actual modified work | Performance example |
| --- | --- | --- |
| Shadow PCF | Filtering loop and initial early-out count | Original adaptive/4 initial → 2 medium → 1 far |
| Screen-space shadows | Depth ray loop, geometric spacing normalized | 10 → 5 → 3 depth samples maximum |
| GTAO | Slice count and both horizon ray lengths/counts; correct averaging | Default 2 slices × 2 rays × 3 steps = 12 → 4 → 2 depth reads maximum |
| SSAO | Depth sample loop, angle distribution, averaging | Original → half → quarter (minimum 2) |
| Air/end/water fog | March count and whole-ray optical integration spacing | 25 → 13 → 7 steps (minimum 3) |
| LPV fog | March count and normalized geometric distribution | 24 → 12 → 6 steps (minimum 3) |
| Cumulus/altocumulus/congestus clouds | Primary density march and attached lighting/ambient rays | Horizon 40 primary → 20 → 10 (minimum 4); 6 lighting → 3 → 2, 2 ambient → 1 |
| Water surface | Wave noise/Gerstner octaves in all three finite-difference height samples; parallax march | 3 octaves → 2 → 1; 4 parallax steps → 2 → off |
| SSR | Rough ray count, intersection/refinement loops; far bypass of environment trace | 4 rough rays → 2 → 1; 16 intersection → 8 → 0; sky fallback remains |
| POM | Height traversal step size, shadow sample loop, far bypass | Original samples → half budget → off inside POM's own range |

These are source-level maximum operations, not hardware texture fetch counts or
measured pass time. Photon already early-outs in many places. Gains depend on
scene coverage, occupancy, divergence, cache behavior, driver compilation, and
features actually enabled. Shadow-map generation, TAA, bloom, sky atmosphere,
voxel updates, water caustic-map generation, and thin cloud layers remain upstream.
We do not claim complete LOD of every Photon effect.

GTAO retains the same radial and angular extent with fewer samples and normalizes
by actual slice count. Aggressive far AO returns neutral occlusion and the
surface normal before constructing or marching any horizon ray. Screen-space
shadows and LPV preserve geometric ray extent by recalculating the initial step.
Fog keeps the whole volume extent and scattering equations but integrates with
larger steps: foreground light shafts can change even when the endpoint is far.
Water fog is capped by upstream at 50 blocks, so it usually reaches only medium
quality; this is intentional.

Clouds use the camera-to-shell **entry** distance in atmosphere coordinates,
divided by `CLOUDS_SCALE` to recover world blocks. Clouds just overhead retain
full quality even when sky depth is 1. Primary sample spacing preserves the
whole volume, secondary geometric rays preserve their original summed extent,
and optical integration uses actual spacing. Primary/secondary caps apply to
the live cloud pass. By default the cached sky-map keeps original work.
`SHADERLOD_CACHED_SKY` optionally lowers its cloud primary and secondary budgets:
Balanced remains full, Performance uses the medium caps, and Aggressive uses far
caps (12.5% before effect minima). Disabled LOD remains full with the option on.
The cached pass already uses half the live primary budget upstream. Its cloud
samples originate at a fixed sea-level atmosphere position, so this is explicitly
a global lighting/reflection approximation rather than live camera-distance LOD.
It retains primary and secondary ray extent and integration normalization. Sky
atmosphere, sun/moon, aurora, and the cached lighting-color texels stay unchanged.
Inspect reflected clouds and ambient lighting if enabling it; no gain is promised.
Cloud shading's scattering iterations and density formulas remain upstream.
These coarser samples can lose small cloud details and introduce TAA noise.

Water wave normals select quality from `distance(world_pos, cameraPosition)` in
both vanilla translucent shading and the common DH water composition helper.
Each finite-difference height evaluation samples only the actual octave count and
renormalizes the geometric amplitude sum. Reduced count removes high-frequency
wave detail while retaining the low-frequency normal and all three derivative
samples. Water parallax reduces its march and octave budgets in medium quality;
far quality returns the original coordinate without a height march. Shadow
caustic-map generation keeps its original wave budget. This introduces no new
texture bindings and should be inspected for moving highlights and transitions.

SSR far quality bypasses environment depth raymarching and still computes sky
fallback and Fresnel. Near/mid/far policies apply through the common specular
helper, including translucent callers. Rough reflections use actual ray count
in their average. Current Mac common options may disable these paths.

POM already ends at 32 blocks by default, so ordinary 32/96 geometry boundaries
would save nothing. Its own bands are 50% and 85% of `POM_DISTANCE`, with a
10%-of-range transition width. Far POM bypasses UV displacement and self-shadow;
medium uses larger depth steps and fewer self-shadow samples. POM requires a
compatible normal/height resource pack and is not demonstrated by default blocks.

## DH and transitions

The deferred shading and AO passes reconstruct `position_view` from Photon's
combined depth and **combined inverse projection**. DH fragments therefore use
real camera distance and can select the far policy. The distant screen-space
shadow routine retains Photon's projection/depth choice and reduces its actual
march; PCF savings alone would end at the shadow-map boundary. Fog selects
Photon's DH inverse projection for DH depth; its back-position conversion also
uses that selected projection. No new sampler bindings were added.

The `dh_terrain`, `dh_water`, and `dh_shadow` entrypoints are unchanged. Terrain
quality changes in shared downstream effects; DH water gets the shared fog
policy. ShaderLOD does not change DH chunk geometry, generation, or mesh detail.

Tier transitions choose one budget per pixel with interleaved noise and temporal
rotation where available; they do not evaluate both paths and blend their cost.
Noise work is avoided outside transition bands for the deferred/AO classifiers.
Debug colors show green/amber/red tiers on opaque terrain including DH, excluding
sky and hand. They do not visualize actual executed sample counts. Later fog,
transparency and post-processing can modify the tint. Tier flips can shimmer,
especially without TAA; inspect movement as well as static images.

## Uniform contract

`SHADERLOD_USE_MOD_CONFIG` defaults on. Registered uniforms use sentinel 2 for
the expanded contract. Sentinel 1 remains accepted for earlier settings, with
preset falling back to the pack option. Missing/unregistered uniforms are zero,
so an absent mod uses all pack defaults. Disable mod config to test fallback.

| Uniform | GLSL type | Meaning |
| --- | --- | --- |
| `shaderlodBridgeVersion` | `int` | 2 selects expanded contract; 1 accepts original values |
| `shaderlodEnabled` | `int` | Nonzero enables reductions |
| `shaderlodNear` | `float` | Geometry near boundary in blocks |
| `shaderlodFar` | `float` | Geometry far boundary in blocks |
| `shaderlodTransition` | `float` | Entire geometry transition band width |
| `shaderlodDebug` | `int` | Nonzero enables tier colors |
| `shaderlodPreset` | `int` | 0 Balanced / 1 Performance / 2 Aggressive |

Cloud thresholds and relative material bands are shader policies; the cloud
thresholds have pack options, and are not new Java bridge uniforms. Configuring
geometry ranges does not reclassify all sky pixels as far. Iris uniform
registration is an internal hook bounded to the audited Iris version. Expression
custom uniforms in shader properties are a different mechanism; none are added
for Java-supplied names.

## Verification

Packaging checks cover deterministic output, corrupted base/source rejection,
ambiguous/missing anchors, license retention, and byte preservation outside the
explicit manifest targets. With `glslangValidator`, the suite compiles deferred
shading, translucent shading, AO, clouds, cached sky, fog, terrain, DH terrain,
DH water, water composition, and shadow caustic generation across all dimensions and option variants including optional cached-sky LOD, bridge fallback,
disabled LOD, every preset, DH, SSAO/no AO, shadows toggles, and SSR/POM materials.
495 fragment compilation variants passed locally (11 programs × 3 dimensions × 15 option variants).
Tests expand actual shader includes and join preprocessor continuations. This is
GLSL syntax/type evidence, not Iris transformation or Apple GPU-driver evidence.

For runtime acceptance, keep common options and camera/scene identical, warm DH
terrain and temporal histories, inspect near/mid/far geometry and cloud details,
and use serial alternating LOD-on/off measurements with actual GPU queries.
A two-client shared-GPU view is a visual comparison and cannot isolate speedup.

## Initial comparison audit (2026-10-03)

The floating preview clients use the identical patched pack with LOD enabled
and disabled; ShaderLOD is loaded in both. This isolates the shader enable
switch, but is not a mod-absent baseline. Both share the Mac GPU and the reduced
option set. Recorded snapshots were ON 113/115 FPS and OFF 115/113 FPS; the
user also observed approximately equal FPS, sometimes ON 2–3% lower. These
observations do not establish a speedup or an isolated regression.

The patch does change the actual `shadow_pcf` sampling loops. However, Photon
already exits after its initial four lookups on uniformly lit or shadowed pixels:

| Shadow-map PCF region | Full/disabled | Medium | Far |
| --- | ---: | ---: | ---: |
| Uniformly lit or shadowed | 4 | 4 | 1 |
| Mixed edge, ordinary stone under current options | 7–11 | 4 | 1 |
| Large SSS blur | Up to 12 | 4 | 1 |
| Outside the shadow-map range | 0 | 0 | 0 |

These are GLSL sampler lookups, not raw hardware texel comparisons. With
variable penumbras disabled, ordinary full budgets follow
`min(12, int(6 + max(1, 0.125 / D²)))`, where the in-map shadow distortion
factor `D` is at least 0.15. The unchanged shadow distance is 128 blocks.
Far LOD becomes fully active at 100 blocks after its transition, leaving roughly
100–128 blocks of potential far savings; projection bounds can reduce that
range. Beyond it, Photon skips PCF and its screen-space shadow path is unchanged.

The sparse pillar fixture contains extensive sky, near floor, and uniformly
lit stone. It was suitable for visualizing zones, not a strong performance
kill test. Medium LOD often removes no lookups there. Clouds, GTAO, volumetric
fog, shadow-map generation, and translucent shading keep their upstream work.
Enabled LOD adds tier/noise calculations; their compiled cost and GPU branch
behavior have not been measured, and could outweigh small savings.

The next decisive check should visualize actual executed PCF lookup counts,
including zero for bypasses, and force far quality on shadow-mapped terrain.
Then time one client with alternating enabled/disabled states after warmup.
A temporary 32-sample versus one-sample stress mode can test the upper bound,
but must be reported as an artificial diagnostic rather than a normal-preset gain.
