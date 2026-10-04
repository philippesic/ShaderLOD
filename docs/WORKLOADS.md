# Controlled rendering workloads

`scripts/prepare_workloads.py` prepares three independent profiles per scene:

| Profile | Shader archive | Common quality | ShaderLOD |
|---|---|---|---|
| `high` | Original pinned Photon v1.3b | Upstream high, with common Mac compatibility changes | Disabled/inert |
| `lower` | Original pinned Photon v1.3b | Upstream medium, with the same Mac changes | Disabled/inert |
| `adaptive` | Reviewed patched Photon | Same common settings as high | Balanced by default |

The high/adaptive settings preserve upstream default sample counts. This avoids
inflating only the baseline to produce a favorable optimization result. Lower
is a practical simpler preset: it removes entity shadows, screen-space shadows,
water parallax, and shader AO according to upstream medium's intended feature
choices. It retains the same 2,048 shadow map resolution and volumetric fog.
Adaptive retains those high-quality features near the camera and uses the
existing distance policy farther away. Cached-sky approximation is explicitly
off; the rejected aggressive tree-lighting comparison is not the default.

## Preparing

```sh
python3 scripts/build_shaderpack.py
python3 scripts/prepare_workloads.py --config-only --dry-run
# Stop clients using the source world before copying.
python3 scripts/prepare_workloads.py --scene forest
python3 scripts/prepare_workloads.py --scene cloudstorm
# Optional premium cloud quality, applied equally to original high and adaptive.
python3 scripts/prepare_workloads.py --scene cloudstorm --cloud-upscaling 1
```

The source defaults to `run-landscape/lod-on`, with the warmed
`ShaderLOD-Landscape` save. Output is `run-workloads/<scene>/{high,lower,adaptive}`.
All three initial saves, including DH caches, are hash-checked against one
source. Default vanilla distance is 24 chunks, simulation distance 8, DH radius
128 chunks. DH generation is disabled and its processing is limited to two
threads with runtime ratio 0.5. Wait for upload/processing queues to settle.

`--render-distance 16|24|32` changes the matched geometry/submission workload.
`--cloud-upscaling 1|2|3|4` selects a documented Photon cloud resolution option
for high/adaptive; lower retains upstream default 4. Default 4 renders cloud
buffers `colortex9` and `colortex10` at quarter width/height, or 1/16 of full
pixel area. Values 3 and 2 select one-third and half width/height; value 1 uses
full resolution, 16 times default cloud-buffer pixel area. This premium quality
reduces reliance on temporal cloud reconstruction and increases buffer memory
and shading work. It changes resolution equally for high/adaptive, preserves
original sample counts, and introduces no extra texture bindings. Report it as
**premium full-resolution clouds**, not original default high. Source evidence
is the explicit `size.buffer.colortex9/10` branches in pinned
`shaders/shaders.properties`; actual driver performance remains to be measured.
`--camera X Y Z YAW PITCH` supplies a common scene camera. `--source`,
`--source-world`, `--output`, `--pack`, and `--preset` allow explicit overrides.
Each scene needs three save copies; prepare only scenes that will be measured.

`--config-only` writes packs/configuration without reading or copying the source
save, allowing configuration review while the source client is active. It still
refuses to modify an active destination save. These profiles are not launchable
until real saves are copied. `--dry-run` changes nothing. Ordinary preparation
preserves existing saves; `--replace-worlds` first backs them up before making
fresh matched copies. None of these operations launch or stop Minecraft.

## Scenes and controls

| Scene | Intended workload | Default control |
|---|---|---|
| `forest` | Vanilla/DH geometry, leaf alpha testing, tree lighting | Inherited forest camera, clear, time 7,000 |
| `cloudstorm` | Cloud layers and rainy volumetric fog | Same position, upward pitch -15°, rain, time 1,800 |
| `coastalwater` | Visible water surface/translucency, shoreline and DH water | Inherited position, clear; shoreline camera requires verification |

The known forest camera is `(256.5,95,768.5)`, yaw 135°, pitch 9°. Source
`control.json` overrides that fallback. Coastalwater is a preparation category,
not proof of a water-heavy view: the runtime owner must choose and inspect an
actual shoreline camera. Operator-supplied coordinates also require in-world
verification. Do not build an artificial water platform for acceptance.

Each scene's shared `control.json` follows the existing development client's
flat camera schema (`x`, `y`, `z`, `yaw`, `pitch`, `time`, `preset`, `debug`) and
adds `weather`, which the coordinator's client integration must honor. Shader
options and world copies remain identical across the high/adaptive controls.
`workload-manifest.json` records settings, exact runtime lock, pack/source hashes,
cache coverage, copy equality, and whether preparation was configuration-only.
Runtime profiles/manifests/logs remain ignored generated files.

## Pinned option and hardware evidence

The script expands `profile.high` and `profile.medium` from the checksum-locked
upstream `shaders/shaders.properties`, then validates every selected value
against declarations and allowed arrays in `shaders/settings.glsl`. The old
`GTAO` boolean in those profile strings has no declaration in v1.3b settings;
the script maps its intent to the actual selector
`SHADER_AO_GTAO`/`SHADER_AO_NONE`. The properties' `CLOUDS_DAILY_WEATHER` screen
entry is similarly undeclared and is not emitted as a fake control.

Upstream default budgets are retained: cumulus 40 horizon/20 zenith primary
steps, six lighting/two ambient steps; GTAO two slices/three horizon steps;
screen-space shadows ten steps; water three wave iterations. Air fog has
hardcoded 8–25 steps in the pinned source, not a shader option to invent.
Primary source is the exact archive locked in `dev/runtime.lock.json`, from
[Photon v1.3b](https://modrinth.com/shader/photon/version/gUv7fBPN), whose
[upstream source](https://github.com/sixthsurge/photon) is separately maintained.

The Mac compatibility changes disable the same eight features in every profile:
SH skylight, colored shadows, colored lights, cloud shadows, environment/sky
reflections, variable shadow penumbras, and border fog. This preserves the
previously rendered sampler-safe feature set, with no new sampler bindings.
New high/medium combinations still require actual Iris-transformed Apple
linking and in-world validation. Option declarations or static GLSL compilation
do not prove compliance with the Mac's 16 fragment texture-unit limit.

SSR remains disabled on this host until an actual DH-enabled Iris/Apple linker
check establishes the sampler budget. Desktop SSR is a separate unverified
workload. POM needs a compatible heightmap resource pack and is also excluded.
Clouds, default fog, GTAO, and water remain useful supported test candidates.

## Acceptance evidence

Measure one client at a time, with matched resolution/camera/time/weather and
warmed DH/temporal histories. Run original high, original lower, and adaptive
controls, including repeated/alternating runs to expose variability. Compare
GPU-render time, pass fractions, CPU wall/thread time, FPS, and visual structure
separately. The proof target is roughly 15–20% GPU-render savings in a realistic
workload; an option file is not proof of that gain. Report the measured ceiling
or failed hypothesis if the target is unattainable. A lower-quality original
preset provides a useful quality/performance control against adaptive cuts.
