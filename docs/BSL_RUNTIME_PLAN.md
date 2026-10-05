# BSL 10.1.8 original runtime plan

Source-only inspection, 2026-10-05, owner `pack_research`; only this document is
owned/changed. No download, compiler, build, client, shader/helper/Java change or
lock mutation. Initial compile/rendering, DH support on this runtime, and
performance remain unverified. Root retains all runtime ownership.

Recommend the **untouched default/HIGH original pack first**, minimal stack,
DH absent, in the existing shoreline. The source does not establish a need to
lower its ordinary default features for the Mac sampler budget. Keep voxel
multi-colored blocklighting off: it is already default off and its author
explicitly marks it incompatible with Mac.

## Pin, layout and permissions

Use [BSL 10.1.8 publisher version Y68yiql9](https://modrinth.com/shader/bsl-shaders/version/Y68yiql9):
`dev/upstream/other-pack-research/BSL_v10.1.8.zip`, 1,134,153 bytes, SHA256
`36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5`.
The existing candidate lock records its publisher-verified SHA512, Minecraft
1.21.1 and Iris/OptiFine metadata. This inspection reconfirmed the local SHA256.
There is no asserted upstream Git commit for this release.

The ZIP root contains **`shaders/` directly**, with no enclosing pack folder.
World folders wrap shared `program/*.glsl` routines. Options are declared in
`shaders/lib/settings.glsl`, named/described in `shaders/lang/en_US.lang`, and
assembled into profiles/screens/program toggles in `shaders/shaders.properties`.
Keep the original compressed archive. If overrides are needed, use the normal
Iris pack options sidecar `BSL_v10.1.8.zip.txt` beside the installed archive,
with `NAME=value` entries, rather than altering GLSL. Verify the *loaded*
canonical options; clearing a stale sidecar is necessary for source defaults.

Publisher metadata is **All Rights Reserved**, and this ZIP contains no license,
readme or derivative-sharing agreement. Banners credit Capt Tatsu and link
`https://capttatsu.com`; the settings header instructs users how to tune options.
That supports native option configuration, not an established redistribution
grant for a copied derivative. Public adaptation/distribution remains a decision
for the coordinator to resolve before distributing an adapter. This document
does not grant it or copy shader source into tracked adapter files. The existing
source visibility and absence of a license file do not make BSL MIT.

## Default and lowest profiles

The declared defaults match **HIGH** for every profile-controlled setting.
Profile names are MINIMUM, LOW, MEDIUM, HIGH and ULTRA; there is no POTATO or
NORMAL profile in this artifact. `shaders.properties` L4–8 expands as follows:

| Profile | AO | Light shafts | Shadows | Colored shadows | Filtering | Shadow map / distance | TAA |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MINIMUM | off | off | off | off | off | 512 / 128 blocks | off |
| LOW | off | off | on | off | off | 1024 / 128 | off |
| MEDIUM | on | off | on | off | on | 1536 / 192 | off |
| **HIGH / defaults** | on | on | on | on | on | 2048 / 256 | on |
| ULTRA | on | on | on | on | on | 3072 / 512 | on |

These profiles leave reflections/clouds/material settings at their independently
declared values; MINIMUM still has full native water reflections. A profile
comparison is a native quality comparison, not a ShaderLOD result.

Important additional defaults, all from `lib/settings.glsl`:

- `MULTICOLORED_BLOCKLIGHT=false` (L49), `MCBL_SS=false` (L64),
  `ADVANCED_MATERIALS=false` (L69).
- `REFLECTION=2` (Full), `REFLECTION_MODE=1` (Balanced),
  `REFLECTION_TRANSLUCENT=true`, `REFLECTION_PREVIOUS=false`, `REFLECTION_LOD=1`.
- `CLOUDS=2`, `CLOUD_BASE=0`; `WATER_NORMALS=1`, `WATER_PARALLAX=true`,
  `WATER_DETAIL=0.25`, `WATER_BUMP=1.00`, `WATER_SHARPNESS=0.2`.
- `REFRACTION=0`, `DOF=false`, `MOTION_BLUR=false`, `FXAA=true`, `TAA=true`,
  `TAA_MODE=0`, `AUTO_EXPOSURE=false`, `DIRTY_LENS=false`.

POM/self-shadow options appear enabled in their declarations, but advanced
materials is off, so their material-resource work is not the default ordinary
terrain workload. `SHADOW_LOD` and `REFLECTION_LOD` describe DH/Voxy behavior;
their names do not mean ShaderLOD integration or a generic camera-distance tier.

For an explicit source-default sidecar, the essential feature guard is simply
`MULTICOLORED_BLOCKLIGHT=false`; it restates the default. Do not accidentally
load a previous pack's user-option file and call that an untouched default.
Record all effective options, rather than only these selected values.

## GL and sampler boundaries on the M3 Max

Regular world fragment/vertex wrappers use GLSL 1.20; water/terrain wrappers
enable `GL_ARB_shader_texture_lod`. They use compatibility attributes, shadow
comparison sampling and explicit-gradient material calls. Iris translation and
the driver must compile/link them; the original version/extension declarations
alone prove neither Apple support nor actual sampler allocation.

Three `shadowcomp.csh` files declare **GLSL 4.30 compatibility**. They perform
3D voxel/light image writes and require compute/image functionality beyond this
host's OpenGL 4.1 path. `shaders.properties` L105–107 enables shadowcomp only with
`MULTICOLORED_BLOCKLIGHT`; its custom images and optional Iris
`CUSTOM_IMAGES FADE_VARIABLE` declaration are inside that same guard at L227.
The author option comment, `lang/en_US.lang` L126, explicitly says the voxel
feature is incompatible with Mac. This is an optional unsupported path, not an
unconditional baseline GLSL 4.30 requirement. Verify the runtime actually omits
the disabled compute/image path. Do not delete its files to hide a failure.

No unconditional `iris.features.required` declaration was found. The archive
also contains separate Voxy entrypoints; Voxy is absent in the proposed stack
and its presence in the ZIP is not a baseline requirement. There is no declared
minimum Iris version in the inspected release metadata; use the exact project
Iris lock rather than infer that its advertised Iris support covers every build.

Representative default **source-reachable candidates**, no DH/Voxy/advanced
materials/voxel lighting, are:

| Pass | Named source sampler candidates | What must be checked |
| --- | --- | --- |
| `gbuffers_water` | `texture`, `gaux2`, `depthtex1`, `noisetex`, `gaux1`, plus `shadowtex0`, `shadowtex1`, `shadowcolor0`: approximately 8 | Base atlas, scene reflection, noise/cloud data and ordinary colored shadows. Active linked uniforms may be fewer. |
| `composite` | `colortex0`, `colortex1`, `depthtex0`, `depthtex1`, `shadowtex0`, `shadowtex1`, `shadowcolor0`, `noisetex`: approximately 8 | Light shafts retain their shadow/noise sampling. |
| `deferred` | `depthtex0`, `noisetex`: 2 | AO enabled; DH would add its depth/projection path. |
| `deferred1` | `colortex0`, `colortex3`, `depthtex0`, `noisetex`, `colortex4`: up to 5 | AO result/sky/scene; unused declarations are not linked samplers. |
| `composite7` | `colortex1`, `colortex2`, `depthtex1`: 3 | TAA; colortex1 mipmaps are enabled by the program. |

Ordinary water outputs `DRAWBUFFERS:01`; extra refraction/MCBL targets are gated.
Deferred1 defaults use `DRAWBUFFERS:04`. BSL enables color-buffer mipmaps in
several post passes; do not assume every scene sample can be changed to LOD zero
without checking stage-specific allocation and usage.

The pinned Iris inspection in `docs/APPLE_FEATURE_BUDGET.md` establishes 14
dynamically available sampler units for composite programs on this 16-unit host,
after two Iris reservations; world/Sodium programs have different reservations
and external atlas bindings. These estimated source sets do not plainly exceed
those limits. They are **not** reflected driver counts or a successful link.

Source-preserving fallback options, only if the actual failure identifies their
path:

1. If voxel/compute/image code is unexpectedly active, verify
   `MULTICOLORED_BLOCKLIGHT=false` and `MCBL_SS=false`, preserving the error and
   prior effective options. Both are default values, not reduced quality here.
2. If ordinary composite sampler allocation fails, try only
   `LIGHT_SHAFT=false`; its composite sampler declarations L49–54 remove that
   shadow/noise group. Label the result **native light-shaft-off reduced quality**.
3. If water shadow samplers are the actual issue, try only
   `SHADOW_COLOR=false`; the shadow helper gates the second shadow depth/color
   samplers. Label **native uncolored-shadow reduced quality**.
4. MEDIUM or MINIMUM can be useful whole-profile diagnostic controls, but
   change several effects simultaneously. A reduced pass does not validate HIGH.

Do not disable SSR simply because another pack exhausted samplers on this host.
Changing `REFLECTION=1` is a useful later **native sky/specular-only** comparison,
not the requested full original baseline.

## DH needs a separate check

The release has `world*/dh_terrain` and `dh_water` wrappers and shared
`program/dh_terrain.glsl` / `dh_water.glsl`. Under `DISTANT_HORIZONS`,
`shaders.properties` L279 onward sets `dhShadow.enabled=false`, `dhClouds=off`,
and DH-water alpha blending. Deferred/composite gain DH depth samplers/projection
matrices. Ordinary water's raytrace can inspect DH depth under `REFLECTION_LOD=1`;
DH water uses its separate native reflection path.

This is concrete source integration, not demonstrated compatibility with DH
3.3.3. Later test DH installed and actually drawing the vanilla/LOD boundary,
water blending, underwater views, shadows and reflections. A no-DH baseline
must have the jar physically absent, not only its draw distance reduced.

## One bounded LOD candidate after the original smoke

The most direct default-workload candidate is **water parallax only**, with the
original native `WATER_PARALLAX=false` option as the lower-quality control.

`program/gbuffers_water.glsl::GetWaterHeightMap` L133 samples two noise layers
for default `WATER_NORMALS=1`. `GetParallaxWaves` L158–165 performs four height
evaluations: eight source noise fetches. `GetWaterNormal` L175–176 uses that
displaced position, then L184–187 performs four more height evaluations for its
normal: another eight fetches. Native parallax-off skips the first eight while
keeping the latter normal construction. Actual driver instructions/cache costs
and whole-frame savings remain unmeasured.

A future default-off explicit experiment could retain those four displacement
iterations near water, use fewer literal iterations in a medium band, and skip
displacement far away while retaining the original normal/lighting/SSR path.
Use reconstructed `length(viewPos)` in camera-space block units; the existing
varying `dist` is vertex-derived/interpolated and also used inside the native
displacement formula. Preserve that formula rather than silently replacing all
distance uses. Avoid changing `WATER_NORMALS`, turbulence or reflected clouds
in the same experiment.

A continuous transition can attenuate displacement in the near/medium band;
it saves the skipped evaluations only in the fully cheap region. Blending two
fully evaluated normals costs both kernels. Existing animated noise plus TAA
does not prove a newly dithered budget will be stable. Inspect grazing views,
reflection edges, wave shimmer, motion and the far-band boundary. Changing
parallax position changes normals and therefore reflection geometry; this is a
quality tradeoff even though lighting budgets remain untouched.

New nonuniform branches around `texture2D(noisetex, ...)` require a derivative
review. Prefer verified explicit LOD/gradient sampling on the active path, with
gradients prepared before divergence when needed. The wrappers already enable
ARB texture LOD, but actual driver translation and noise texture mip/filter
state still need verification. Preserve a complete original path when disabled.

Do not first cut `lib/reflections/raytrace.glsl`'s 30-iteration outer budget:
its collision-driven contraction/growth and `maxf` refinements affect reach and
hit precision. Full water reflections already use `maxf=4` when normals are on.
POM already has a native distance fade and is inactive in the default material
profile. Those are less useful first targets than isolating existing water work.
Any distributable derivative still depends on resolving BSL's permissions; this
worker proposes no adapter implementation or publication.

## Exact source anchors

Hashes are over this release's bytes, including CRLF:

| Release path | SHA256 |
| --- | --- |
| `shaders/lib/settings.glsl` | `5617a7a13c37373c27e742d2dffb777a1437bae83cf25693451c104915050285` |
| `shaders/shaders.properties` | `8856162fcf6846e1b56f5c092e6eaee04af687875f85e87cde87e1bd0a0d3169` |
| `shaders/lang/en_US.lang` | `72f08e1cf626555c160190268b4c6ad94f05c71bfe55ded0dbb737c17a71dc76` |
| `shaders/world0/gbuffers_water.fsh` | `39606a8d5c852168dc670580eeda8a62637d0019bb0c4ff2d9097f12e6448412` |
| `shaders/world0/shadowcomp.csh` | `faec3ac605a4bfdbfc90d93518b1c19141a4322ce8d4db46050b017447455d17` |
| `shaders/program/gbuffers_water.glsl` | `637f7135c810f33d8c0ea1f5b87096486417ed85a623c65e5d96de4227c2fb5f` |
| `shaders/program/composite.glsl` | `43cc8317bdebf3f7e03021a0817b3b8870074b014d895d367cbaaf11dce62a07` |
| `shaders/lib/reflections/raytrace.glsl` | `7e12a6a6472ea4cbff731e4b1e93698b9e21a391600facfd483c3e0211c5342a` |
| `shaders/program/dh_water.glsl` | `a8b7640d61721f75df4f6e95a191fa0bd0a6d9ae35d6147f1dc1acbbdd7e5358` |

## First smoke acceptance and rejection

Launch the original HIGH/default pack on locked MC 1.21.1 / NeoForge 21.1.253 /
Iris 1.8.12 / Sodium 0.6.13 / Java 21, minimal extras, no DH/Voxy, same shoreline
and pacing. Accept an **original smoke only** when actual Iris/driver compilation
and linking finish, the intended pack/pipeline remains active without fallback,
terrain/water/reflected objects/shadows are visible, and versions/archive/effective
options/captures/logs are retained. No speed comparison follows from one smoke.

Reject and preserve a compile, unsupported feature, sampler allocation, driver,
black-screen or fallback failure. Identify the first causal program/feature and
retry one native option reduction at a time if justified above. Record full
defaults and each reduced profile separately; never rewrite an archive to make
the original failure disappear. A build or source inspection cannot accept it.

After baseline rendering, compare only native water-parallax on/off and inspect
moving-camera quality before investing in distance code. Root decides whether
that control exposes enough actual cost and acceptable quality to justify a
later bounded adapter. DH compatibility and derivative distribution permissions
remain separate unresolved decisions.


## Actual original no-DH smoke

Root6187 exit0/twoaccepted: originalHIGH and nativeWATER_PARALLAX=false only.
MC1.21.1/NeoForge21.1.253/lockedIris1.8.12/Sodium0.6.13/minimal stack,
DHphysicallyabsent,35s warmup20s capture/GPU1/16,1488×836shoreline. Actual
loadedHIGH has2048shadowmap/256shadowdistance, lightshafts/coloredshadows/TAA,
fullREFLECTION2; voxel optionsfalse. Bothfallbackfalse. Root originalPNG
inspected: shoreline/shadows/reflectedtrees/waterwave surface rendered. This
establishes bounded original Mac rendering, not wholepack/DH/motion support.

Original114.319FPS/GPU8.0697ms/thread8.4297ms; nativeparallax-off117.271/
7.8431/8.2463. GPU2.81% lower inone serialblock atdifferent watergeometry
quality; no significance or ShaderLOD gain. Raw
`dev/logs/bsl-original-smoke-r1` retains all exactsource/artifacts/options,
window/camera/hashes/samples/logs/PNGs. No shader derivative was produced.
Permission for derivative distribution remains unestablished; nativeoptions
are ordinarylocal configuration. No performance/support/defaultpromotion.
