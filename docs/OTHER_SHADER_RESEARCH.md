# Other shader candidates

Inspected 2026-10-05. Research owner: `pack_research`; scope ends at this report
and `dev/shader-candidates.lock.json`. No shader, Java, helper, build or client
changes were made. All archives/extractions are ignored under
`dev/upstream/other-pack-research/`.

Recommend **MakeUp Ultra Fast 9.5g** for the first bounded adapter experiment,
with **Miniature 2.19** as the MIT fallback. This ranks suitability for an
explicit, distributable experiment on this host, not visual quality or measured
performance. Complementary/BSL/Bliss remain valuable compatibility targets,
but their permissions and/or Mac paths need more care.

## Constraints and evidence

The target remains Minecraft 1.21.1, NeoForge 21.1.253, Iris 1.8.12, Sodium
0.6.13, Apple M3 Max/OpenGL 4.1, with optional DH 3.3.3. Exact runtime artifact
hashes remain in `dev/runtime.lock.json`; do not replace that lock with a newer
Iris simply because a pack recommends latest versions.

Every release below advertises Minecraft 1.21.1 in its publisher's Modrinth API
version metadata. Downloaded release SHA512 values match that metadata; local
SHA256 and exact Git source pins are recorded in the candidate lock. This is
**download verification and static inspection only**. No candidate was compiled
by a GLSL validator, loaded by Iris, rendered with/without DH, benchmarked or
visually accepted. Loader metadata does not prove this NeoForge combination.

Working assumption: shaderpack GLSL is loader-independent once this pinned Iris
build supplies its uniforms/programs. That assumption needs runtime checking.
Mac support claims from another Iris version, an M1, or a pack author do not
establish M3 Max/NeoForge/DH 3.3.3 support here.

## Ranked shortlist

| Rank | Exact release | Why test / primary evidence | Remaining concern |
| --- | --- | --- | --- |
| 1 | [MakeUp Ultra Fast 9.5g](https://modrinth.com/shader/makeup-ultra-fast-shaders/version/pgb0JjoN), 2026-10-04 | LGPL-3.0-or-later; publisher lists 13.2M downloads, Iris, optional effects and modification permission. Source README requires Iris 1.5.1+. | Publisher lists Windows/Linux testing, not Apple. Separate DH water path needs its own check. |
| 2 | [Miniature 2.19](https://modrinth.com/shader/miniature-shader/version/LWmZ94RG), 2026-07-29 | MIT upstream; publisher lists 4.8M downloads and Iris. Small post-process SSR makes adapter scope easy to inspect. | Release has no DH programs found and no Apple claim established. Do not add DH support to the first experiment. |
| 3 | [Super Duper Vanilla 1.3.8](https://modrinth.com/shader/super-duper-vanilla/version/KB0sOLSc), 2025-07-08 | Author explicitly documents Apple M1 + Iris 1.6.10+/Minecraft 1.18.2+, GLSL 3.3 and existing DH programs. | Custom derivative license expects a visually/functionally distinct edit. Narrow quality-preserving redistribution needs resolution. |
| 4 | [Complementary Reimagined r5.9.3](https://modrinth.com/shader/complementary-reimagined/version/Bqen1mJX), 2026-09-15 | Popular reference with explicit DH programs and modular clouds/reflections/materials. | License 1.7's derivative conditions conflict with a tiny visually similar LOD edit. This report does not grant adaptation/distribution permission. |
| 5 | [BSL 10.1.8](https://modrinth.com/shader/bsl-shaders/version/Y68yiql9), 2026-09-21 | Official publisher release; explicit DH paths; SSR/POM routines are easy to locate. | Publisher marks ARR; release ZIP contains no license/readme. No affirmative derivative grant established. |
| 6 | [Bliss 2.1.2](https://modrinth.com/shader/bliss-shader/version/kC2Y8q1P), 2025-11-23 | Explicit DH, cloud and water code; release contains conditional Chocapic derivative permissions. | Author's Apple issue says DH+Bliss cannot load; current Stable source changed its license to ARR. |

The MakeUp publisher's tested-platform list and modification statement are on
its [official project page](https://modrinth.com/shader/makeup-ultra-fast-shaders)
and [upstream README](https://github.com/javiergcim/MakeUpUltraFast/blob/ad2d9327ad485429e4eaf2b3f98cd738ada609de/README.md).
Miniature's scope and reach come from its
[publisher page](https://modrinth.com/shader/miniature-shader). Super Duper
Vanilla's platform and shader-language claims come from its pinned
[README](https://github.com/Eldeston/Super-Duper-Vanilla/blob/1ca20aa4dc47fa750888e523a9325ba558478583/README.md)
and [developer documentation](https://github.com/Eldeston/Super-Duper-Vanilla/blob/1ca20aa4dc47fa750888e523a9325ba558478583/DOCUMENTATION.md).

BSL's [official website](https://capttatsu.com/bslshaders/) says 10.1.7 while
its linked publisher API provides 10.1.8. Pin the inspected 10.1.8 archive,
not the website's inconsistent current-version text. The suspected GitHub
`CaptTatsu/BSLShaders` returned 404; it is not an authoritative source pin.

## Licenses at the pins

These are source facts and engineering distribution boundaries, not a conclusion
that every future packaging scheme satisfies a license.

- MakeUp's downloaded `LICENSE` is LGPL v3 and publisher metadata says
  LGPL-3.0-or-later. Keep shader modifications under applicable upstream terms,
  retain credits/notices, record modifications, and supply corresponding source
  and applicable GPL/LGPL text with a distributed derivative. The mod's MIT
  license does not replace the shader's license. No upstream notification is
  required by its README. [Pinned license](https://github.com/javiergcim/MakeUpUltraFast/blob/ad2d9327ad485429e4eaf2b3f98cd738ada609de/LICENSE).
- Miniature's upstream MIT license permits adaptation/distribution with its
  copyright and permission notice retained. The 2.19 release ZIP omits the
  license, so a derivative must restore the upstream notice rather than repeat
  that omission. [Pinned license](https://github.com/mateuskreuch/minecraft-miniature-shader/blob/bdb6e0020b4318d4e845902cbcc3e3552b0e9274/LICENSE).
- Super Duper Vanilla license 1.6 expressly permits personal modifications.
  Distributed edits need its license/notices, credits, different naming and
  visually/functionally distinct results; setting changes alone do not qualify.
  [Pinned license](https://github.com/Eldeston/Super-Duper-Vanilla/blob/1ca20aa4dc47fa750888e523a9325ba558478583/LICENSE).
- Complementary license 1.7 requires attribution, its unchanged/latest applicable
  license, a distinct name without Complementary, noticeable visual differences
  across common scenarios including daytime Overworld, and significant
  human-written visual additions. It also restricts unmodified modpack uploads
  to publisher systems. A narrow LOD edit cannot be presumed to meet those
  conditions; patch-only packaging is not an established exception.
  [Pinned license](https://github.com/ComplementaryDevelopment/ComplementaryReimagined/blob/c09950df0650b27dac260c1b0f69afc11387aa6f/License.txt).
- BSL: ARR metadata plus absent archive notice leaves public modification and
  redistribution unresolved. Do not commit copied shader source or derivatives
  based on visibility of GLSL alone. Original-pack compatibility inspection is
  useful independently of that unresolved distribution choice.
- Bliss: the **2.1.2 release** includes conditional modified-sharing terms:
  Chocapic attribution, required edit naming, preserved sharing rules, no
  monetizing links, useful changes beyond constants. The **current Stable
  81e403e source** instead has only an ARR notice. They are different artifacts;
  do not apply release permissions to the current source tree.
  [Stable notice](https://github.com/X0nk/Bliss-Shader/blob/81e403ed308141039a09d792a36f8eb328898a60/LICENSE.md).

## Source pins and effect boundaries

The lock distinguishes publisher releases from current Git snapshots. MakeUp
9.5g has all **344 shader files byte-identical** to source
`ad2d9327ad485429e4eaf2b3f98cd738ada609de`. Other inspected source/release trees
are not byte-identical; this includes line-ending differences and possibly
substantive updates. No semantic equivalence is asserted. Use the exact release
paths below unless a row explicitly specifies the source revision.

| Artifact / inspected revision | Distance-sensitive entry points | Static interpretation |
| --- | --- | --- |
| MakeUp 9.5g / `ad2d9327ad485429e4eaf2b3f98cd738ada609de` | `shaders/lib/water.glsl`: `fastRaymarch` L9, loop L30; `reflection_calc` L176; `water_shader` L200. `shaders/lib/config.glsl` L151 sets `RAYMARCH_STEPS=10`. | Ordinary water/glass marches use the view-space surface origin. `SSR_TYPE=0` already supplies a projected-image approximation; a distance selector could reuse it while retaining original near SSR. |
| Same MakeUp pin | `shaders/common/water_blocks_fragment.glsl` L191; `shaders/lib/volumetric_clouds.glsl`: `get_cloud`, sample loop L44. `shaders/lib/ao.glsl`: `dbao`, loop L26. | Reflected clouds use half the configured cloud samples already. Clouds need distance to their layer intersection, not the water surface. AO has a fixed `AOSTEPS` budget and existing attenuation; it is lighting-sensitive and a later candidate. |
| Same MakeUp pin, DH | `shaders/lib/water_dh.glsl`: `reflection_calc_dh` L70; `shaders/common/solid_dh_water_fragment.glsl`. | DH water already uses a single projected point, not the ordinary ten-step SSR march. Do not claim ordinary-water sample cuts save DH work. |
| Miniature 2.19 release | `shaders/program/final.fsh` L48 calls `shaders/common/getReflectionColor.fsh` L11; loops L24 and L42. `shaders/shader.h` L24/L26 defines `SSR_MAX_STEPS=10`, `SSR_BINARY_STEPS=4`. | Fullscreen SSR is masked by reflectivity. `viewPos` is reconstructed from depth; scalar camera distance can select a cheaper literal kernel. Existing miss returns no reflection. Small baseline budgets may leave little incremental gain. |
| Super Duper Vanilla source `1ca20aa4dc47fa750888e523a9325ba558478583` | `shaders/lib/rayTracing/rayTracer.glsl`; `volumetricClouds.glsl`; `volumetricLight.glsl`; `shaders/main/modded/dh_water.glsl`. | Documented modular passes. Cloud steps already increase with traversed distance; replacing that policy blindly could undersample thick layers. Existing quality options are necessary controls. |
| Complementary source `c09950df0650b27dac260c1b0f69afc11387aa6f` | `shaders/lib/materials/materialMethods/reflections.glsl`: `GetReflection` L34, 30/38-sample loop L97; `pomEffects.glsl`; `shaders/lib/atmospherics/clouds/mainClouds.glsl`: `GetClouds` L48; `shaders/program/dh_water.glsl`. | Modular explicit integration possible technically. Reflection function already receives `lViewPos`, but license suitability must precede distributable adaptation. |
| BSL 10.1.8 release | `shaders/lib/reflections/raytrace.glsl`: `Raytrace` L31 receives `maxf/stp/ref/inc`; `simpleReflections.glsl`, `roughReflections.glsl`. `shaders/program/gbuffers_terrain.glsl` L172 sets `parallaxFade`; `shaders/lib/surface/parallax.glsl`. | Existing POM distance fade needs to be measured as a native-quality control before inventing another tier. `iris.features.optional=CUSTOM_IMAGES FADE_VARIABLE` and compute files exist: inspect their guards before a Mac launch. |
| Bliss 2.1.2 release | `shaders/lib/volumetricClouds.glsl`: `maxIT_clouds/maxIT` from normal/LQ ray budgets; cloud shading inner loops. `shaders/lib/waterBump.glsl`; `shaders/dimensions/DH_translucent.fsh`. | Cloud/weather inputs require settling/telemetry. Source complexity plus the upstream Apple/DH blocker makes it a poor first host experiment. |

The pinned MakeUp [water source](https://github.com/javiergcim/MakeUpUltraFast/blob/ad2d9327ad485429e4eaf2b3f98cd738ada609de/shaders/lib/water.glsl),
[DH water source](https://github.com/javiergcim/MakeUpUltraFast/blob/ad2d9327ad485429e4eaf2b3f98cd738ada609de/shaders/lib/water_dh.glsl)
and [developer guide](https://github.com/javiergcim/MakeUpUltraFast/blob/ad2d9327ad485429e4eaf2b3f98cd738ada609de/ForDevelopers.EN.md)
are the preferred next implementation references. Root/world folders wrap
shared `common` passes, `lib` declares functions, and `src` inserts snippets
inside main. Use explicit patch anchors and hashes, not broad text rewriting.

## First small experiment for the runtime/adapter owner

1. Load **unmodified MakeUp 9.5g**, first DH absent, on the exact locked minimal
   runtime. Record profile/effective options, archive hash, actual compilation,
   visible water/reflections and driver logs. Repeat the original-pack smoke with
   DH 3.3.3. Retain failures. Do not edit a pack to conceal baseline incompatibility.
2. If the baseline renders, prepare a default-off explicit water-only adapter.
   Scope starts in `lib/water.glsl::reflection_calc`, preserving glass and DH
   paths. Distance is `length(fragpos)` from view space, confirmed by
   `src/position_vertex_water.glsl` assigning `viewSpacePos4D.xyz`.
3. Preserve original ten-step SSR near the camera. Beyond a conservative far
   band, compare the existing projected-image approximation against retaining
   sky reflection; fade original SSR participation through a transition band.
   Do not blend two fully evaluated paths everywhere and expect work reduction.
   The transition may evaluate both temporarily, or use a reviewed stable
   selector, with shimmer/ghosting checked during motion. Initial bands are
   experimental options, not recommended defaults or live mod config.
4. Compare original raymarch, original global low-reflection option, patched
   disabled and active in the same shoreline scene. Keep near reflections,
   lighting, clouds, detail radius and weather matched. Run repeated serial
   captures and a moving-camera quality pass, then an ordinary second scene.
   Keep stack gains separate from adapter gains. This worker ran none of them.

Predictions: skipping far SSR may reduce depth/color fetches when far water
occupies substantial pixels. Ten baseline steps and fragmented water coverage
may produce negligible full-frame gains; different reflected objects or a moving
ring may outweigh savings. Neither prediction is evidence of performance.

Unresolved choices returned to the coordinator: MakeUp versus Miniature first;
whether far reflection approximation passes the user's quality expectations;
whether a second DH-capable pack should take priority over a simpler MIT proof;
and whether to seek upstream permission later for a narrow Complementary/BSL
adapter. No external messaging is authorized or performed.

Bliss's author says defaults without DH may work on Apple, while DH+Bliss and
some settings do not; issue 181 is closed as not planned. This is an upstream
limitation statement, not an observed failure in our client.
[Author's Apple issue](https://github.com/X0nk/Bliss-Shader/issues/181).
