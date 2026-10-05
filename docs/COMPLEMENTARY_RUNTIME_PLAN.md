# Complementary Reimagined r5.9.3 original runtime plan

Source inspection, 2026-10-05, owner `pack_research`; only this document is
changed. No archive/lock/source/helper/Java changes, downloads, builds, compilers,
clients or GPU work. Initial rendering, support, DH compatibility and performance
remain unverified by this worker. Root owns the runtime and common protocol.

Recommend **original source defaults/HIGH first**, minimal stack, DH physically
absent, in the existing shoreline. This release already has explicit Mac guards
for unsupported voxel/world-reflection paths. Do not lower ordinary features
before an actual default-program failure identifies a need.

## Exact archive and options

The [publisher release Bqen1mJX](https://modrinth.com/shader/complementary-reimagined/version/Bqen1mJX)
is `ComplementaryReimagined_r5.9.3.zip`, 553,397 bytes, SHA256
`fed6c879e732cf7fc7bc6570ebb1def43235aa7e96f62859fbdbdbfa0b484b38`.
The local SHA256 was reconfirmed; publisher SHA512 is already verified in
`dev/shader-candidates.lock.json`. The existing extraction is
`dev/upstream/other-pack-research/complementary-release/`.

`shaders/` is directly at ZIP root alongside `License.txt`, installation text,
placebo files/folders and `.gitignore`. Leave the original archive intact. Do
not remove unfamiliar files or disabled compute entrypoints. Current Git source
`c09950df...` is a separate, non-byte-identical pin; use the release source here.

User options are declared in `shaders/lib/common.glsl`, profiles/screens and
pipeline settings in `shaders/shaders.properties`, and explanations in
`shaders/lang/en_US.lang`. World wrappers include shared `program/*.glsl`.
Use Iris options or a `ComplementaryReimagined_r5.9.3.zip.txt` sidecar beside
the installed archive for native overrides, not source edits. Preserve/clear
stale option files as appropriate and record the actual loaded canonical map.

Defaults match **HIGH** in every profile-controlled setting. Key defaults:

- `SHADER_STYLE=1` (Reimagined), `RP_MODE=1` (integrated PBR); native
  `WATER_STYLE_DEFINE=-1` resolves to `WATER_STYLE=1`, and cloud style to 1.
- `SHADOW_QUALITY=2`, `shadowDistance=192.0`, `ENTITY_SHADOW=1`.
- `WATER_REFLECT_QUALITY=2`, `BLOCK_REFLECT_QUALITY=3`,
  `DETAIL_QUALITY=2`, `CLOUD_QUALITY=2`, `LIGHTSHAFT_QUALI_DEFINE=2`,
  `SSAO_QUALI_DEFINE=2`, `FXAA_DEFINE=1`, `ANISOTROPIC_FILTER=0`.
- `COLORED_LIGHTING=0`, `WORLD_SPACE_REFLECTIONS=-1`, `RAIN_PUDDLES=0`.
- `TAA_DEFINE=-1` resolves to TAA enabled at `DETAIL_QUALITY>=2`;
  `REFLECTION_RES=0.5`. HIGH does not activate custom PBR/POM, Unbound-style
  procedural water, reflected sky effects or the high-detail refraction path.

Profile anchors are `shaders.properties` L2–9:

| Profile | Shadow quality / distance | Water / block reflection quality | Detail / cloud quality | Colored lighting / world reflections |
| --- | --- | --- | --- | --- |
| POTATO | -1 / 64 | 1 / 0 | 0 / 1 | 0 / -1 |
| VERYLOW | 0 / 64 | 1 / 0 | 0 / 1 | 0 / -1 |
| LOW | 0 / 96 | 2 / 1 | 2 / 2 | 0 / -1 |
| MEDIUM | 2 / 128 | 2 / 1 | 2 / 2 | 0 / -1 |
| **HIGH / defaults** | 2 / 192 | 2 / 3 | 2 / 2 | 0 / -1 |
| VERYHIGH | 2 / 192 | 2 / 3 | 3 / 2 | 192 / -1 |
| ULTRA | 2 / 224 | 2 / 3 | 3 / 3 | 256 / 1 |
| COMPLEMENTARY | 3 / 256 | 2 / 3 | 3 / 3 | 512 / 1 |

Other profile changes include light shafts, FXAA, anisotropy, entity shadows and
puddles. Whole-profile comparisons are native quality controls, not ShaderLOD
gains. The lowest named profile is POTATO, not MINIMUM. Water quality names in
the language file L197–202: -1 Off, 0 Sky only, 1 Potato, 2 Medium (screen-space
ray-traced). Default water reflections are already the highest value of this
particular option; it is independent of the overall HIGH profile name.

## Mac and Iris feature boundaries

Raster wrappers mostly use GLSL 1.30. The three shadow vertex wrappers use
GLSL 4.00 compatibility; the three shadowcomp compute wrappers use GLSL 4.30
compatibility. Iris translation of compatibility inputs remains part of actual
compile/link acceptance. The compute path exceeds this host's OpenGL 4.1 path,
but is **conditional**, not an unconditional baseline requirement.

- `common.glsl` L33–59 admits internal colored lighting only when custom images
  exist, shadows are enabled, the platform is not `MC_OS_MAC`, and the program
  is not DH/Voxy. The fallback sets colored lighting to zero internally and
  world-space reflections to -1.
- `shaders.properties` L72–74 also forces `COLORED_LIGHTING=0` on Mac before
  image/pipeline handling. L109–112 disables all shadowcomp programs when it is
  zero. L160 onward allocates voxel/floodfill images only above zero.
- World-space reflection images/SSBOs are nested under colored lighting and
  `WORLD_SPACE_REFLECTIONS>0` guards. Defaults activate neither. Do not enable
  them on this host or delete their code to disguise a failure.
- L116 lists `CUSTOM_IMAGES SSBO BLOCK_EMISSION_ATTRIBUTE FADE_VARIABLE
  ENTITY_TRANSLUCENT` as **optional** Iris features. No unconditional
  `iris.features.required` declaration was found. Missing optional features
  may still affect appearance/program behavior and must be recorded.
- Reflection-buffer scaling at L118–122 is gated by block-emission feature
  presence, used by the author to distinguish Iris 1.7+ from older versions.
  `mainClouds.glsl` L56 onward has a separate `IRIS_VERSION>=10800` position
  path. These branches do not establish a universal minimum Iris version or
  prove the locked 1.8.12 renders this release.

Use locked Minecraft 1.21.1 / NeoForge 21.1.253 / Iris 1.8.12 / Sodium 0.6.13 /
Java 21 / M3 Max. Verify actual `MC_OS_MAC` preprocessing, disabled compute/image
paths and optional-feature macros in Iris-generated source. Native HIGH is
appropriate for the first smoke; a newer Iris or remote GPU is not implied.

## Sampler and pass inspection

`lib/uniforms.glsl` declares many samplers globally. Do not total those declarations
and conclude they are all linked in every pass. Iris ignores inactive linked
uniforms and aliases may share suppliers. Driver reflection and actual Iris
allocation are the acceptance evidence.

Representative source uses under default no-DH/no-Voxy/non-voxel settings:

| Pass | Relevant candidates and operations |
| --- | --- |
| `gbuffers_water` | Atlas `tex`; `noisetex`; opaque `depthtex1`; reflected scene `gaux2`; ordinary shadow depth/color group. `gaux2` is also used for cloud apparent depth. Other translucent material paths can retain additional samplers. |
| `deferred1` | `colortex0`, `depthtex0`, `noisetex`; cloud-layer shadow query `shadowtex0`; conditional material/fade data in `colortex4/6`, light-shaft state in `colortex5`. AO and clouds share this pass. |
| `composite` | `colortex0`, `colortex4`, `depthtex0/1`, `colortex6`, `noisetex`, reflection color/mips `colortex5`; opaque reflection history can additionally use `colortex1/7`. |
| `composite1` | Scene `colortex0`, vanilla depth `depthtex0/1`, conditionally `colortex6`; light shafts use shadow depth/color textures and noise. DH/Voxy adds depth sources. |

Default water outputs `DRAWBUFFERS:03`; high-detail refraction/extra-mask paths
add other targets. Deferred1 enables colortex0 mipmaps; composite enables
colortex5 mipmaps for rough reflections. Reflection resolution defaults to half
size when the supported-feature branch applies. Texture aliases/custom sources
are specified at properties L231–233 (`noise`, cloud-water texture and gaux4).
These source sets suggest a modest ordinary baseline footprint, not a complete
linked sampler count or proven driver headroom.

The project's exact Iris inspection records 14 dynamically allocatable units
in composite programs on this host after reservations
(`docs/APPLE_FEATURE_BUDGET.md`). World/Sodium binding reservations differ.
`program/composite1.glsl` L24–29 contains a Mac+DH/Voxy guard removing uses of
colortex6, with an upstream comment referring to an eight-sampler Mac limit.
Treat that as the author's rationale, **not a measured eight-unit limit for this
M3 Max**. Preserve the guard; runtime samplers/limits decide the actual result.

Source-preserving native controls if a real failure identifies their path:

1. Assert `COLORED_LIGHTING=0`, `WORLD_SPACE_REFLECTIONS=-1` and preserve the
   Mac macro. These restate defaults; they are not reduced quality on HIGH.
2. For a water reflection shader/sampler failure, try only
   `WATER_REFLECT_QUALITY=0` (native sky-only). For opaque reflection failure,
   try only `BLOCK_REFLECT_QUALITY=0`. Label the reduced reflection quality and
   retain the original failure/options.
3. For an identified light-shaft failure, try only
   `LIGHTSHAFT_QUALI_DEFINE=0`. For an identified default cloud path failure,
   `CLOUD_QUALITY=0` is a native cloud-off diagnostic. Neither validates HIGH.

Do not apply all reductions speculatively, rewrite the archive, or remove entry
files. A successful reduced profile is separate evidence from default support.

## DH and candidate work after the baseline

The artifact contains dedicated DH terrain/water wrappers and
`program/dh_terrain.glsl` / `dh_water.glsl`. Properties L138–143 defines DH water
blending, disables DH shadow rendering and disables DH clouds. Ordinary water
SSR can read DH opaque depth at `reflections.glsl` L109–118; there are separate
DH material/projection paths. No-DH success cannot accept DH 3.3.3 compatibility.
Later inspect actual LOD terrain/water, seam, alpha blending, shadows, underwater
views and reflections with the locked DH artifact present.

For the **default Reimagined water workload**, prefer investigating water SSR
distance selection over a procedural-wave patch. Default water style is 1 and
puddles zero, so the eight-fetch Unbound-style water parallax loop at
`specificMaterials/translucents/water.glsl` L88–90 is inactive. Enabling style 3
to create that workload would be a deliberate quality/workload change.

Ordinary water calls `GetReflection` in `program/gbuffers_water.glsl` L255,
with reconstructed `viewPos` and `lViewPos=length(viewPos)` from L178–182. The
native SSR path in `materialMethods/reflections.glsl` L69–133 uses up to 30
iterations with six collision refinements when world-space reflections are off.
Its collision-triggered contraction/expansion interleaves with traversal; merely
halving the outer count or doubling steps does not preserve reach or first hits.

First compare original `WATER_REFLECT_QUALITY=2` against native sky-only (0) and
Potato projected-image (1), leaving block reflections/lighting/clouds unchanged.
This tests whether the real cost and quality tradeoff merit distance logic.
A possible explicit, default-off future adapter would preserve full near-water
SSR and skip its trace at far surface distance, keeping native sky/specular
background and all water lighting/alpha/foam. Apply it only to ordinary water,
preserving glass, opaque and DH branches. Use camera-space `lViewPos` in blocks,
not reflected-hit distance or normalized depth; review its material mask.

Transition blending must avoid evaluating both expensive paths throughout the
far region. Returning fewer hits or a projected approximation can lose reflected
trees, cause rings and alter motion stability even if shadows are unchanged.
The pack already varies trace dither with TAA/frame inputs; adding random budget
changes can create new noise. New nonuniform sampling requires explicit
LOD/gradient review. Preserve intended colortex5 rough-reflection mips and prove
gaux2/depth sampler states rather than globally forcing LOD zero. A disabled
adapter must preserve original preprocessing. No candidate gain or integration
is established by this report.

## License and evidence boundary

The exact release contains **Complementary License Agreement 1.7**. Normal
gameplay/content creation is permitted. Unmodified modpack redistribution has
specific attribution/responsibility/publisher-platform requirements.
Redistributed modified packs require official-site attribution, the applicable
unchanged license, a distinct name without Complementary, noticeable changes
across common scenarios including daytime Overworld, no alternative license
terms, and significant human-written visual additions. Other uses not granted
are reserved. These are conditions on this pin, not the main mod's MIT license.

A narrow quality-preserving LOD patch cannot be presumed to meet those derivative
conditions; patch-only distribution is not an established exception. The future
adapter above is a technical research candidate. Return permissions/product
packaging to the coordinator before adaptation/distribution decisions. No
upstream messaging is authorized or performed here.

Exact release source SHA256 anchors (raw bytes):

| Path | SHA256 |
| --- | --- |
| `License.txt` | `e8d4ee004e2a706303e89651e5cafa2f06af4d04f373fa17fc8251880a995add` |
| `shaders/lib/common.glsl` | `fccb97c8ff5094996ea073d1d1c732fb841e3c6baf1f160b066e73e29d6e7dbe` |
| `shaders/lib/uniforms.glsl` | `3b77e2dbb3a0ad36d4dcf234300bd4b5d9f9af0acc5a3ed57ad5539a4be5bbe3` |
| `shaders/shaders.properties` | `b29be050d6296bc5df769a66e351adbaaa9108eec801eb0442137a7a81266ab7` |
| `shaders/world0/gbuffers_water.fsh` | `2e60f0b681bf75f8f70b70c403aa3ae4f2cdf67c06c8d0a462e3fce239dddb2c` |
| `shaders/world0/shadowcomp.csh` | `a55d1c19d6f3b4f2019efe0bf4ca5c53e5f11b6b0e0416f482ce1f9053d97192` |
| `shaders/lib/materials/materialMethods/reflections.glsl` | `3b6d3b6477680a89ab56a2c0ae053b56b7b6b8cb39dabfba3d688772dac005ec` |
| `shaders/program/dh_water.glsl` | `9818d398941afe439c113b4de904905eacd3e5aef1c8efb6c6ed487bf95c8f59` |

First smoke acceptance requires actual Iris/driver compile/link, intended pack
active without fallback, visible shoreline terrain/water/reflected objects and
shadows, plus exact loaded versions/hash/effective options and retained captures.
Preserve compile/feature/sampler/driver/black-screen/fallback failures and identify
their first causal program before one native-control retry. A source read,
download, build or lower-quality pass does not accept original default support.

Only after that smoke compare native reflection controls and moving-camera quality;
repeat balanced serial timing and a second ordinary scene before performance
promotion. The root decides whether real work/quality evidence and permissions
justify a later explicit adapter. This file is frozen at the source-only boundary.

## Native-control preparation checkpoint

The coordinator subsequently authorized bounded preparation tooling, without a
derivative or runtime launch. `prepare_pack_experiment.py --family complementary`
now creates three original-release arms in the inspected coastalwater scene:

| Arm / matrix case | Only recorded shader option | Runtime task |
| --- | --- | --- |
| `high` / `complementary-high` | `WATER_REFLECT_QUALITY=2` (original source default) | `runClientLodOff` |
| `water-sky` / `complementary-water-sky` | `WATER_REFLECT_QUALITY=0` | `runClientLodOff` |
| `water-potato` / `complementary-water-potato` | `WATER_REFLECT_QUALITY=1` | `runClientLodOff` |

All arms copy the exact original ZIP unchanged, replace stale pack/options,
retain the same actual world fingerprint and source control/camera, remove the
locked DH jar, and clear stale workload results. HIGH records its default-valued
option explicitly so the loaded Iris value can be checked; Iris may remove that
line or the empty sidecar without changing its prepared identity. Other settings
remain source defaults. These tasks physically exclude the main mod; this is
native original-pack work, not a core protocol consumer or adapter.

The new `common.glsl` option layout is accepted only after the **entire archive
SHA256 identifies this exact r5.9.3 release**. Existing Photon/MakeUp/Miniature/BSL
layout precedence is unchanged; an unknown common.glsl pack is rejected. Parsed
declarations normalize controls but do not evaluate the preprocessor or prove
effective Mac feature values. Actual Iris option maps/generated source remain
authoritative during the smoke.

Lightweight validation passed: seven new preparation/parser fixtures and the
nineteen existing proof-matrix fixtures. Checks cover original archive bytes,
world/control/options identity, source preservation, no-DH arms, native pin
rejection, default-file removal, actual selected profile fingerprints, original
mod-absent case tasks and prior parser/case behavior. Real workload profiles have
**not** been prepared or launched by this worker.

After the live client has stopped, use a **fresh** output:

```sh
python3 scripts/prepare_pack_experiment.py --family complementary --source run-workloads/photon-ssr-cost-controls-r1/coastalwater/high --output run-workloads/complementary-native-controls-r1
```

The source was checked read-only: matching coastalwater layout, existing saved
world, non-configuration-only/no-DH parent manifest, and retained control
`x=500.5, y=68.0, z=660.5, yaw=87.38, pitch=3.735`. No real-world fingerprint or
copy was taken during the live reverse SSR capture. Preparation will acquire the
source world lock and fingerprint the stopped save. It refuses an existing or
overlapping output; do not overwrite a previous runtime result.

Root's first original smoke, after fresh preparation and exclusive runtime:

```sh
python3 scripts/run_proof_matrix.py complementary-high --profiles-root run-workloads/complementary-native-controls-r1 --stacks minimal --dh absent --repeats 1 --warmup 35 --duration 15 --tag complementary-original-smoke-r1
```

Only after accepted rendering and visual inspection, the matched native matrix
can use cases `complementary-high complementary-water-sky
complementary-water-potato` with the same profiles root, minimal stack, DH absent
and three repeats under another fresh tag. Keep source/compiled/GPU inputs frozen
during captures. The worker stops here; compilation/rendering/performance and
default-versus-reduced quality acceptance belong to root.


## Root original runtime smoke

Original HIGH completed: runner exec90231 exited0; matrix statuscomplete, sole
case accepted. Unchanged release SHA256fed6c879...84b38, exact loaded artifacts,
no main ShaderLOD/noDH, native2976x1672, detailed24/24, no Iris fallback.
35s warmup/15s capture, GPU sparse1/16, artificial wetness0. Actual Iris option
map confirmed style1/RP1/water2/block3/detail2/cloud2/shadowDistance192.0/
coloredlighting0/worldreflection-1. Compile/link and visible Overworld rendering
succeeded without speculative feature reductions or source edits.

Root final capture shows shoreline terrain, tree/terrain reflections, water,
block clouds and sunset lighting; image display was resized, not pixel-parity
QA. First-person sun-glint pose same x500.5/y68/z660.5/87.38/3.735/day12500.
GPU mean14.0710ms/median14.0836/p9514.6663, FPS64.242, render-thread7.1419ms.
Single smoke establishes no gain, motion quality, adapter or general support.

Iris warnings remain: unresolved BIOME_PALE_GARDEN/inPaleGarden,
BIOME_SULFUR_CAVES/inSulfurCaves and endFlashIntensity/endFlashFactor0.
They did not trigger fallback in this Overworld scene. New-biome/End behavior
remains unverified; do not erase the warnings or claim full dimension coverage.
Mac preprocessing/optional sampler allocation were not separately dumped here.

Raw dev/logs/complementary-original-smoke-r1 contains exact matrix/inputs/
artifacts/options/summary/console/final PNG. Root prepared a second fresh set
from the same stopped original source for native water controls; those costs
and images remain pending. License remains unchanged; no derivative produced.


## Native water reflection pilot terminal

exec38518 exit0; three cases completed and runner accepted actual fixed source,
artifacts/options/control/window/wetness/class/input/PNG contracts. Minimal/noDH/
mainabsent/native2976x1672/35swarm20scapture/GPU1/16, same full pinned release.

| Native water option | FPS | Mean GPU ms | Render-thread ms |
| --- | --- | --- | --- |
| Full2 | 65.590 | 13.7679 | 7.1079 |
| Sky0 | 73.126 | 12.3120 | 7.1449 |
| Potato1 | 72.759 | 12.3002 | 7.1385 |

Single serial pilot sky/Potato GPU reductions10.57%/10.66%, different quality.
Root sky image loses reflected shoreline/trees; Potato retains projected
reflections but tree/terrain positions visibly differ from full. This is no
ShaderLOD adapter or quality-equivalent gain. Static resized inspection cannot
accept motion/near-far transitions. New-biome/End expression warnings retained
in all logs. Source-exact mod-absent native experiments require repeated controls
and motion checks before any broader inference. Raw complete matrix/captures:
dev/logs/complementary-native-water-pilot-r1. No derivative or license change.
