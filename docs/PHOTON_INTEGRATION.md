# Photon integration

ShaderLOD starts with one explicit modification to pinned Photon v1.3b: distance-dependent shadow-map PCF sample budgets on opaque vanilla terrain and entities. The NeoForge mod supplies live configuration through a versioned Iris uniform bridge. The shaderpack also runs with its own options when that bridge is unavailable.

This is a first optimization experiment, not a measured performance result. GLSL compilation checks and in-world rendering have passed locally using a reduced Apple profile. Live debug tint and enable toggles demonstrate mod-to-GPU configuration transport. DH terrain buffers rendered under that profile. Upstream Photon does not support Apple GPUs generally; these bounded smoke checks do not validate its standard/Ultra preset or GPU savings. See [runtime evidence](SETUP.md).

## Build and select

From the repository root:

```sh
python3 scripts/build_shaderpack.py
python3 -m unittest discover -s tests -p test_shaderpack.py -v
```

The builder writes `run/shaderpacks/Photon-ShaderLOD.zip`. Select **Photon-ShaderLOD** in Iris's shaderpack menu. Its **ShaderLOD** options screen contains distance thresholds, transition width, an enable switch, a debug overlay, and a mod-config switch. The build downloads nothing: prepare the pinned archive via the runtime setup first.

The pack is assembled from the original ZIP, 15 reviewed literal replacements in `patches/photon/v1.3b.json`, and the new helper at `shaderpacks/photon/shaders/include/shaderlod/lod.glsl`. Each patched source file has a SHA-256 guard; the original ZIP must match every hash in `dev/runtime.lock.json`. Missing or repeated anchors abort assembly. Overlays can add files but cannot silently replace upstream files. Archive paths, timestamps, ordering, and permissions are normalized. Repeated builds with the same Python/zlib inputs produce identical ZIP bytes. `shaderlod-build.json` records base, manifest, and overlay hashes inside the output.

Photon's original `LICENSE` and all untouched files remain in the assembled pack. Photon uses a custom license: it allows source inspection and modifications, with restrictions on redistribution through monetized platforms. Keep the upstream license with substantial copies and review it before distributing a derivative; local development packaging does not grant additional publication rights.

## First effect

| Zone | Default distance | Shadow filter work |
| --- | --- | --- |
| Full | Below 32 blocks | Photon's existing adaptive PCF budget, including its early-out |
| Medium | 32–96 blocks | Cap the same PCF operation at four samples |
| Far | Beyond 96 blocks | One centered shadow sample |

Distance is `length(position_view)`, measured from the rendered camera. Default transition bands are eight blocks wide, centered on each boundary: 28–36 and 92–100. Interleaved gradient noise selects one quality tier per pixel; frame rotation lets Photon's TAA integrate the transition. This does not evaluate both qualities and blend their results. Transition width zero restores strict boundaries. Threshold sanitization guarantees ordered distances and non-overlapping bands even when shader options are changed independently.

The PCF patch adjusts the first-loop limit and early-out normalization to match the actual sample budget. Colored-shadow sampling follows that budget, and the far sample is centered rather than jittered. Blocker search, variable penumbra estimation, subsurface scattering, shadow distances, screen-space shadows, and non-shadow effects keep their upstream implementations. Lower sample counts therefore reduce only part of the shadow work; they do not imply proportional frame-time savings.

The deferred pass passes tier zero for DH/Voxy coarse geometry and the player's hand. The translucent shader call also passes tier zero. All upstream `dh_*` entrypoints and DH implementations remain byte-identical. Installing DH can exercise Photon's existing support, but ShaderLOD-specific DH quality reduction belongs to a later stage.

Debug tint shows full/medium/far as green/amber/red on opaque vanilla geometry. It excludes the sky, DH/Voxy geometry, and the hand. This tint is applied in the deferred pass; later transparency, fog, exposure, and post-processing can modify its appearance. It visualizes the chosen tier even where the shadow operation exits early or shadows are disabled.

## Uniform contract

`SHADERLOD_USE_MOD_CONFIG` is enabled by default. A helper uses the bridge only when `shaderlodBridgeVersion == 1`; otherwise it uses pack options. Unregistered uniforms remain zero, so the sentinel prevents absent-mod zero values from overriding useful pack defaults. Disabling the mod-config option removes all bridge uniform declarations.

| Uniform | GLSL type | Meaning |
| --- | --- | --- |
| `shaderlodBridgeVersion` | `int` | Version sentinel; one selects this contract |
| `shaderlodEnabled` | `int` | Nonzero enables quality reduction |
| `shaderlodNear` | `float` | Near boundary in blocks |
| `shaderlodFar` | `float` | Far boundary in blocks |
| `shaderlodTransition` | `float` | Entire transition band width in blocks |
| `shaderlodDebug` | `int` | Nonzero enables tier tint |

The Iris hook registers these uniforms per frame. It uses Iris internals, so the mod's Iris dependency range must remain bounded to the audited version until a new version is tested. There are deliberately no `uniform.*` property expressions for these names: Iris expression-defined custom uniforms are a different mechanism from Java-supplied mod values.

Fallback pack options are `SHADERLOD_ENABLED`, `SHADERLOD_NEAR`, `SHADERLOD_FAR`, `SHADERLOD_TRANSITION`, and `SHADERLOD_DEBUG`. When a working bridge is selected, the mod controls all five values, including debug and enable flags; changing a pack fallback option then has no live effect until the mod-config switch is disabled.

## Verification and next work

Packaging tests cover deterministic output, corrupted base rejection, changed source rejection, ambiguous/missing anchors, license preservation, and preservation of every upstream file outside the explicit targets. When `glslangValidator` is available, the suite compiles the actual deferred and translucent fragment programs across all three dimensions with eight option combinations (48 compilations), including DH, debug, no bridge, shadows disabled, PCF disabled, colored shadows disabled, and VPS disabled. Include expansion and preprocessor line joining approximate those preparation steps in Iris; passing these checks does not validate Iris's full shader transformation or a GPU driver.

For the first rendering proof:

1. On a Photon-supported desktop GPU, load the pinned NeoForge/Iris client with the patched pack and a repeatable terrain scene containing geometry on both sides of each threshold.
2. Enable debug, move through the transition bands, and verify the hand and DH terrain retain their original path. Toggle mod-config to confirm fallback options work.
3. Disable debug. Compare identical cameras with LOD enabled and disabled under the same Photon options, resolution, render distance, weather, time, and warmed-up world.
4. Record GPU frame times and inspect shadow edges, colored shadows, SSS foliage, moving cameras, and TAA-disabled noise. Do not infer speedups from CPU frame rate alone.
5. Only after a useful quality/performance result, add SSR/POM and a separate DH policy with its own visual and timing checks.

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
