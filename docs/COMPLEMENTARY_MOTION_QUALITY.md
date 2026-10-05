# Complementary native water motion controls

This is bounded original-pack quality work, not a ShaderLOD adapter. Root has
rendered the pinned Complementary Reimagined r5.9.3 at native 2976×1672 with the
minimal Iris/Sodium stack, no DH and no main mod. The reported full/sky/Potato
pilot GPU means were 13.7679/12.3120/12.3002 ms. Root observed lost reflected trees
in sky-only and shifted projected geometry in Potato. Those tradeoffs have not
been accepted as adapter quality or a performance/support result.

This preparer creates fresh unchanged-release arms and a shared immutable
20-second **2 FPS / 41-frame** request. It does not launch, build, compile, encode
or change the source scene. Root owns real preparation, all clients/GPU and
actual image review. Lower sampling rate limits temporal conclusions: these
native sampled frames can expose moving reflection displacement, but cannot
exclude shimmer/flicker above 1 Hz or replace broader quality checks.

## Pin, branches and fixed scene

Complementary original ZIP SHA-256:
`fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38`.
`prepare_pack_experiment.prepare(..., 'complementary')` enforces that complete
r5.9.3 container and preserves the original ZIP in all three profiles. Its
existing source-world advisory lock and equal-copy fingerprint checks remain
in use. Unknown archive layouts are not generalized or rewritten.

| Arm | Only requested native shader option | Source behavior to inspect |
| --- | --- | --- |
| `high` | `WATER_REFLECT_QUALITY=2` | Original full water ray search/refinement |
| `water-sky` | `WATER_REFLECT_QUALITY=0` | Water geometry-reflection block skipped; background remains |
| `water-potato` | `WATER_REFLECT_QUALITY=1` | Native mirrored/projected image branch |

Inspected pinned `shaders/lib/common.glsl` and
`lib/materials/materialMethods/reflections.glsl`: native water default is2;
terrain reflection requires water quality>=1 outside the COMPOSITE path;
the water raymarch requires>=2. The noDH mirrored branch uses
`verticalStretch=0.013` and projected view position with depth/edge handling.
This changes reflection geometry, not merely a sample count. Other held loaded
controls are SHADER_STYLE1, RP_MODE1, block reflections3, detail2, clouds2,
shadow distance192, colored lighting0 and world-space reflections−1. Flat source
parsing does not prove conditional driver execution; actual Iris maps remain
authoritative. Licenses/attribution travel in the unchanged archive.

The source is the stopped original Photon profile:
`run-workloads/photon-ssr-cost-controls-r1/coastalwater/high`. Its archive must
have original Photon SHA-256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`, shaders enabled,
physical profile jar directory empty, requested detailed radius24 and a save
`level.dat`. Source control is checked readonly:

```json
{"x":500.5,"y":68.0,"z":660.5,"yaw":87.38,"pitch":3.735,"time":12500,"weather":"clear","preset":"PERFORMANCE","debug":false}
```

The first waypoint is copied from that exact parent control. **Do not reuse the
earlier yaw78/pitch2.2-start Photon request:** it would not reach the harness's
required first-pose match. Positions stay fixed in the same previously inspected
column; only yaw/pitch sweep. No underwater/depth/third-person proof is implied.

## Preparation

After root verifies the source client is stopped, use a fresh path:

```sh
python3 scripts/prepare_complementary_quality_profiles.py prepare --source run-workloads/photon-ssr-cost-controls-r1/coastalwater/high --output run-workloads/complementary-motion-quality-r1
```

The script creates `coastalwater/high`, `coastalwater/water-sky` and
`coastalwater/water-potato` through the existing family preparer. Every arm has
the same copied world and game options, the exact original release ZIP and only
its native water option override. It records source/control/game-option hashes,
the prepared world fingerprint, options and camera request hash in
`complementary-quality-preparation.json`. That marker appears only after all
preparation checks pass. Actual native window size remains a runtime check.

Existing outputs and source/target nesting are refused. The source control and
copied game options are rechecked; an old manual capture instruction or source
`camera-quality` history is rejected rather than overwritten or erased. Use a
clean stopped source if necessary. A failed copy can leave its fresh partial
directory for inspection; choose a different output for a retry, never reuse it.
Source worlds, requests, logs and previously captured images remain untouched.

The shared `camera-request.json` is:

```json
{
  "schema":1,"label":"complementary-native-water-motion-r1",
  "warmupSeconds":30,"fps":2,"pngCompressionLevel":0,
  "maximumInFlightImages":1,
  "expectedCameraFluid":"NONE","rejectFullBlockCamera":true,
  "waypoints":[
    {"x":500.5,"y":68.0,"z":660.5,"yaw":87.38,"pitch":3.735,"seconds":0},
    {"x":500.5,"y":68.0,"z":660.5,"yaw":96.0,"pitch":5.0,"seconds":7},
    {"x":500.5,"y":68.0,"z":660.5,"yaw":78.0,"pitch":2.2,"seconds":14},
    {"x":500.5,"y":68.0,"z":660.5,"yaw":87.38,"pitch":3.735,"seconds":20}
  ]
}
```

## Three serial native quality runs

Root uses the existing compiled, verified dev harness, with no measurement or
benchmark mode. These exact commands physically exclude the main mod through
`runClientLodOff`, select minimal/noDH and the native scale2 window, and keep
GPU timing off. Never run the three windows concurrently. If source dev classes
need a build, root performs it before any client and rechecks artifact identity;
the following commands explicitly avoid recompiling dev classes during runs.

```sh
./gradlew --offline runClientLodOff -x compileDevClientJava -PrunDirectory="$PWD/run-workloads/complementary-motion-quality-r1/coastalwater/high" -PincludeDh=false -PperformanceStack=minimal -PbenchmarkWindowScale=2 -PgpuTiming=false -PfixedWetness=0 -PcameraQuality="$PWD/run-workloads/complementary-motion-quality-r1/camera-request.json" -PcameraQualityOnly=true
```

After that owned process terminates and its exact sequence is validated:

```sh
./gradlew --offline runClientLodOff -x compileDevClientJava -PrunDirectory="$PWD/run-workloads/complementary-motion-quality-r1/coastalwater/water-sky" -PincludeDh=false -PperformanceStack=minimal -PbenchmarkWindowScale=2 -PgpuTiming=false -PfixedWetness=0 -PcameraQuality="$PWD/run-workloads/complementary-motion-quality-r1/camera-request.json" -PcameraQualityOnly=true
```

After the second owned process terminates and is validated:

```sh
./gradlew --offline runClientLodOff -x compileDevClientJava -PrunDirectory="$PWD/run-workloads/complementary-motion-quality-r1/coastalwater/water-potato" -PincludeDh=false -PperformanceStack=minimal -PbenchmarkWindowScale=2 -PgpuTiming=false -PfixedWetness=0 -PcameraQuality="$PWD/run-workloads/complementary-motion-quality-r1/camera-request.json" -PcameraQualityOnly=true
```

If the actual framebuffer is1488×836 or another size, retain that result as a
rejected native contract and diagnose the window setup. Do not silently change
the expected size, source control, rate, options or labels to promote it.

## Structural validation before image review

Choose the **explicit UUID manifest from the owned launch**; the validator does
not select a latest/first directory or pool old runs. Replace the UUID tokens:

```sh
python3 scripts/prepare_complementary_quality_profiles.py validate --output run-workloads/complementary-motion-quality-r1 --arm high --manifest run-workloads/complementary-motion-quality-r1/coastalwater/high/camera-quality/HIGH_UUID/manifest.json
python3 scripts/prepare_complementary_quality_profiles.py validate --output run-workloads/complementary-motion-quality-r1 --arm water-sky --manifest run-workloads/complementary-motion-quality-r1/coastalwater/water-sky/camera-quality/SKY_UUID/manifest.json
python3 scripts/prepare_complementary_quality_profiles.py validate --output run-workloads/complementary-motion-quality-r1 --arm water-potato --manifest run-workloads/complementary-motion-quality-r1/coastalwater/water-potato/camera-quality/POTATO_UUID/manifest.json
```

The readonly validator requires complete status, all41 unique ordered slots and
callbacks, zero skips, no pending image/callback leases, selected one-image bound,
unchanged request/control/artifacts, original archive hash, loaded native options
at every boundary, main/DH/optional-stack absence and dev/Iris/Sodium provenance.
Requested/effective detailed radius must be24 and all recorded framebuffers and
actual PNG IHDRs2976×1672/RGBA8. Every on-disk PNG hash/size and IHDR CRC is checked;
full raster decoding is left to root's image review and the tested writer.

All frames must have known NONE/no-full-collision/first-person camera contexts,
loaded actual chunk-cache guards and finite near-plane positions. The player
must stay500.5/68/660.5, rendered X/Z within0.0002 blocks, stable eyeY about69.62,
advancing source render frames and finite Iris clocks. Recorded path time must
stay within0.1 seconds of its half-second slot, and rendered yaw/pitch within
0.05 degrees of the current motion tick’s bounded eased request. LocalPlayer
rotation uses the current tick, while recorded path time/position use the partial
tick; the validator checks both clocks independently. Context and screenshot camera values
must agree. These checks do not prove glint/ray visibility, partial-shape
clearance, continuous fluid state between samples or visual parity.

Root then compares near/mid/far water and reflected bank/tree features through
the sweep. Inspect projected feature shift, screen-edge loss, depth intersections,
glint movement and temporal stability; align actual recorded cameras/path times
and account for advancing histories rather than assume identical pixel phase.
Sky-only's reflected-tree loss and Potato's projected displacement are native
tradeoffs to judge honestly. PNG capture overhead is unmeasured quality work;
do not derive a GPU optimization or adapter support from these sequences.

## Lightweight source checks

Run only the scoped test module:

```sh
python3 -m unittest discover -s tests -p test_complementary_quality_profiles.py -v
```

Fixtures use the real pinned ignored archives when available, tiny synthetic
save files and synthetic PNG-header/manifest records. They do not download,
copy the real world, launch Minecraft or encode native images. Archive-dependent
tests skip clearly if local pins are absent. Passing preparation/source fixtures
is not runtime motion/quality acceptance. Root owns real preparation and every
41-frame capture/inspection gate; failures and old history remain retained.


Root integration: all current six scoped tests passed after source freeze,
including the final lock-delegation and option-boundary guards. Real fresh
preparation completed at run-workloads/complementary-motion-quality-r1,
worldSHA89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46,
requestSHA9886fda859251159de7b02f395e2ada5657e8d777dd2b589365b7a859c9a2289.
Original HIGH exec83288 exited0; all41 frames passed the corrected structural
validator. Native sky-only exec48047 is running; actual visual acceptance remains
separate.


Root corrected an initial validator false positive using pinned Minecraft/NeoForge
source. LocalPlayer.getViewXRot returns current XRot; non-passenger getViewYRot
returns current YRot (source lines192–199). Camera.setup forwards these view
rotations, then interpolates position/eye height (lines54–61). The camera rig
applies the eased pose at motion_tick/20, whereas observed_path_seconds tracks
(motion_tick−1+tick_delta)/20. Angle validation now uses the current tick and
also verifies player angles; partial/path clock validation remains strict.
The0.05degree tolerance was not relaxed. All41 original frames match the current
requested angles within0.000007degrees. Seven fixtures pass, including rejection
of the old interpolated-angle model. No capture/PNG/request/rig source changed.
Initial failed-validator note retained in dev/logs; raw manifest remains intact.

Pinned source member SHA256: {"net/minecraft/client/Camera.java": "012faacc33b2b597237a6b81e369ef49f876a97a08b22db736db64baae411178", "net/minecraft/client/player/LocalPlayer.java": "3b2fefe879002cfe64da769ce487fbe87607f815585669721595d90a808c5308"}


## Three native sequences structurally accepted

All three owned processes exited0. Corrected validator accepted all123 native
frames (41/arm), zero skips, all hashes/PNG dimensions/options/source/artifact/
slot/callback/pose/NONE/no-full-collision contracts, pending writer count0 and
one-image peak1. Stable eyeY69.61999988555908. Exact loaded versions were
Minecraft1.21.1, NeoForge21.1.253, Iris1.8.12-snapshot+mc1.21.1-local and
Sodium0.6.13+mc1.21.1; main ShaderLOD/DH/optional optimizers absent. Iris's
bundled Fabric API components are dependencies, not a Fabric mod implementation.

| Arm | Owned sequence UUID | Median/max PNG write ms |
| --- | --- | --- |
| Full | 0c35fb78-3727-4afa-b248-357e09e36347 | 32.14 / 50.08 |
| Sky | a4a7be0f-5571-42bb-b617-fc5066ea7604 | 34.78 / 45.41 |
| Potato | 2f886bf7-9cef-4c44-a18b-f20a562ce5ed | 35.75 / 49.62 |

Root inspected matched slot14 in all three: yaw95.99726/pitch4.9995737 and
identical camera position. Full retains reflected shoreline/trees; sky removes
those reflected objects; Potato visibly displaces the reflected trees/shore
relative to full. Shader clocks/history differ; no pixel parity or continuous
motion-quality acceptance is asserted. The2FPS sweep remains sampled evidence,
not exclusion of high-frequency flicker, underwater/DH/near-far ring support.
No derivative or ShaderLOD adapter produced, no cheaper mode promoted.

Raw manifests copied into dev/logs/complementary-motion-{high,water-sky,
water-potato}-r1-manifest.json; summary in
 dev/logs/complementary-motion-quality-r1-summary.json. Native PNG sequences
remain in the explicit profile UUID directories. Console logs retain original/
sky/Potato warnings. All runtime jobs terminal; root owns future GPU runs.
