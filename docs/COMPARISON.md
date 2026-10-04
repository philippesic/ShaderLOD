# Photon + Distant Horizons comparison

The landscape pair uses independent copies of a warmed world in
`run-landscape/lod-on` and `run-landscape/lod-off`. It replaces the earlier
Iris-only pillar preview. Both clients receive identical shader quality options,
vanilla distance, DH configuration, and initial world/DH data. Both original and
patched Photon archives are present in each profile, preserving their licenses.

The final left window uses patched Photon with ShaderLOD enabled and the
`PERFORMANCE` preset. The right selects original Photon v1.3b and is launched
without the ShaderLOD mod. The development comparison/timing harness and DH
remain present in both. The right's ShaderLOD configuration file is inert when
the mod is absent. This is a clean visual baseline, while serial timing toggles
LOD inside **one patched-pack client** to isolate shader LOD's effect.

## Preparing profiles

Stop clients using the source and destination worlds before copying. The script
checks `session.lock` with a POSIX advisory lock and fails if a world is open.
It does not stop or launch games.

```sh
python3 scripts/build_shaderpack.py
python3 scripts/setup_comparison.py --dry-run
python3 scripts/setup_comparison.py
```

Defaults: source `run/saves/ShaderLOD-Smoke`; destination save name
`ShaderLOD-Landscape`; vanilla render distance **24 chunks** (384 blocks),
simulation distance 8, DH radius **128 chunks** (2,048 blocks), two DH processing
threads with a 0.5 runtime ratio, and background DH generation disabled.
The development client chooses the camera after loading. Preparation does not
alter the source save or add artificial geometry.

The source DH database audit found 16,377 detail-0 sections with X/Z positions
-64 through 63, and coarser levels through detail 8. This establishes cached
data exists; visible distant terrain must still be verified in-world. Use a
scenic camera beyond the sparse fixture near the origin.

Optional `--dh-radius 256` uses a 4,096-block radius. `--dh-threads 2|3|4` and
`--render-distance 16|24|32` select matched settings. `--source`,
`--source-world`, `--output`, and `--pack` override their respective paths.
`--generate` enables `SURFACE_THEN_CHUNKS` for warm-up; disable generation and
wait for processing queues to settle before measuring. A larger radius does
not establish that the entire radius has cached terrain.

Rerunning preparation refreshes managed configuration, Photon packs/options,
Sodium options if present in the source, and the checksum-verified pinned DH
jar. Existing saves are preserved. Use `--replace-worlds` for a fresh matched
pair; existing saves are first moved into each profile's `save-backups/`
directory. If only one save exists, preparation requires that option. Opened
profiles can diverge through player/world state, so use fresh copies when exact
initial data matters. Preparation does not touch `run-compare`.

`run-landscape/comparison-manifest.json` records source/copy world SHA-256,
patched pack SHA-256 and metadata, runtime artifact hashes, cache coverage,
and config overrides. Runtime profiles, saves, manifests, logs, and captures
are ignored generated files.

## Audited DH 3.3.3 configuration

The script modifies generated `run/config/DistantHorizons.toml`, preserving
unmodified settings/comments. Missing expected keys cause preparation to fail.

| TOML key | Default comparison value |
|---|---|
| `common.multiThreading.numberOfThreads` | `2` |
| `common.multiThreading.threadRunTimeRatio` | `"0.5"` |
| `common.worldGenerator.generatorPlan` | `"DISABLED"` |
| `common.worldGenerator.showGenerationProgress` | `"DISABLED"` |
| `client.advanced.graphics.quality.lodChunkRenderDistanceRadius` | `128` |
| `client.advanced.debugging.rendererMode` | `"DEFAULT"` |
| `client.advanced.debugging.lodOnlyMode` | `false` |
| `client.advanced.graphics.genericRendering.enableCloudRendering` | `false` |
| `client.advanced.autoUpdater.enableAutoUpdater` | `false` |
| `client.advanced.autoUpdater.enableSilentUpdates` | `false` |

DH generic clouds are disabled equally to avoid an additional cloud layer.
Photon clouds remain enabled. On the local Mac both packs disable the same
eight Photon options for sampler/driver compatibility: `SH_SKYLIGHT`,
`SHADOW_COLOR`, `COLORED_LIGHTS`, `CLOUD_SHADOWS`, `ENVIRONMENT_REFLECTIONS`,
`SKY_REFLECTIONS`, `SHADOW_VPS`, and `BORDER_FOG`. This reduced profile is not
upstream Ultra quality.

## Running and interpreting

```sh
./gradlew runClientLodOn
./gradlew runClientLodOff
```

The development client selects matching cameras and fixed time/weather,
displays FPS/GPU timing, and positions floating windows. These tasks must use
the new `run-landscape` directories and open `ShaderLOD-Landscape`.

Use **one patched-pack client** for warmed alternating LOD-off/LOD-on GPU
measurements with the same resolution/camera and settled DH queues. Record
query scope, sample counts, and milliseconds independently from FPS. The
two-client preview shares one GPU, CPU, memory, and compositor; its values give
live context for visual inspection, not an isolated speedup measurement.
Cached data alone does not establish shader compilation, rendered DH geometry,
shader savings, or acceptable visual quality.
# Final validated preview

The current control file selects `AGGRESSIVE`. Preparation enables the opt-in
`SHADERLOD_CACHED_SKY` option in the patched pack; it reduces global cached-cloud
lighting quality and is explicitly separate from distance LOD. Balanced restores
its original budget. The original Photon baseline ignores ShaderLOD-only options.
The final preview has the main ShaderLOD mod absent on the right; both clients
retain only the same timing harness and common renderer/DH settings.

FPS, real GPU render milliseconds, median/p95, and cloud/AO/lighting/fog/shadow
pass timings are visible. Apple elapsed queries measure frames and passes on
alternate frames. The two clients share the GPU; standalone timing results and
quality limitations are in [GPU_RESULTS.md](GPU_RESULTS.md).

Edit `run-landscape/control.json` to move both cameras (x/y/z/yaw/pitch/time),
choose `BALANCED`, `PERFORMANCE`, or `AGGRESSIVE`, and toggle the left debug view.
Controls are polled twice a second. In the left client,
`/shaderlod preset performance` also changes the live quality preset. A control
file update reapplies its preset. Right uses original Photon and has no
ShaderLOD commands. Normal mouse/keyboard movement remains available in both.
