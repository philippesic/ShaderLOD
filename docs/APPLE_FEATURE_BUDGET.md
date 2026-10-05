# Photon SSR on the Apple development host

Source/bytecode investigation, 2026-10-04. No shader compile, driver link,
client launch or benchmark was performed for these new combinations. The
coordinator owns the live serial matrix. This is a bounded diagnostic plan,
not additional compatibility evidence.

## Exact inputs and existing evidence

Photon v1.3b, Modrinth version `gUv7fBPN`, local
`dev/upstream/photon_v1.3b.zip`, SHA256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Iris NeoForge 1.8.12, Modrinth version `t3ruzodq`, local
`dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar`, SHA256
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`.
The embedded Iris version is its previously verified snapshot/local label.
Exact artifact URLs and upstream hashes are in `dev/runtime.lock.json`.
[Photon artifact metadata](https://api.modrinth.com/v2/version/gUv7fBPN),
[Iris artifact metadata](https://api.modrinth.com/v2/version/t3ruzodq).

Prior setup linked a reduced deferred4 profile and rendered Iris/Photon/DH on
the M3 Max. An earlier fuller profile failed in Iris sampler registration with
`No more available texture units while activating sampler shadowtex0`.
`docs/SETUP.md` records linked counts of 18 versus 14 from that investigation;
it also cautions that this did not include every Iris transformation. Do not
reuse those counts as a freshly measured driver result for SSR or other profiles.

## The important Iris allocation constraint

Inspection of the exact JAR with `javap -p -c` found:

- `gl.sampler.SamplerLimits` reads GL enum 34930 (`GL_MAX_TEXTURE_IMAGE_UNITS`),
  the fragment-stage limit, for `maxTextureUnits`.
- `samplers.IrisSamplers` sets `COMPOSITE_RESERVED_TEXTURE_UNITS` to `{1,2}`.
- `pipeline.CompositeRenderer` passes that set into `ProgramBuilder.begin`.
- `gl.program.ProgramSamplers$Builder` begins with
  `remainingUnits = maxTextureUnits - reservedTextureUnits.size()`.
- Its `addDynamicSampler` checks the linked uniform location and skips `-1`
  uniforms. Several names registered for one sampler supplier share one unit.
  It does not allocate a unit merely because an unused uniform was declared.

Thus a 16-fragment-texture-unit host has **14 dynamically allocatable units in
these deferred/composite programs under this Iris version**, before checking
whether the underlying GL program could physically use 16. The limit is not
simply a count of global GLSL declarations, nor is the final error's sampler
necessarily the feature causing exhaustion. Registration order matters.

These observations refer to exact JAR classes, not an assumed current GitHub
branch. Reserved units for world/Sodium programs differ (`{0,1,2}` and `{0,2}`);
their external samplers and dynamic samplers require separate accounting.

## Real SSR and the likely deferred4 budget

Pinned Photon `include/lighting/specular_lighting.glsl:212–226` calls
`raymarch_depth_buffer` only under `ENVIRONMENT_REFLECTIONS`. Its hit path
samples previous scene color `colortex5` at line 256 and previous fog
`colortex7` at line 258. `SKY_REFLECTIONS` only supplies the sky fallback using
`colortex4` at lines 187–195 and 228–231. Sky-only reflections are not SSR.
The raytracer samples a depth source selected in
`include/misc/raytracer.glsl:8–16,66–71`.

The source-live deferred4 set for the current reduced Overworld mask is
estimated below. These are likely retained samplers from reachable operations,
**not newly reflected driver uniforms**. Default-off normal/specular maps and
galaxy are assumed, as are ordinary clouds and MC 1.21.1 entity draws.

| Group | Likely retained names | Why |
| --- | --- | --- |
| Material/overlays | `colortex1`, `colortex3` | Gbuffer and glint/damage data, d4 lines 202–208 |
| Sky and atmosphere | `colortex0`, `colortex4`, `depthtex0`, `noisetex` | Skytextured output, vertex lighting, atmosphere LUT, moon/dither |
| Clouds | `colortex11`, `colortex12` | Filtered cloud color, apparent distance and temporal age |
| AO | `colortex6`, `colortex14` | AO/bent normals and bilateral depth weights, d4 lines 326–333 |
| Vanilla depth | `depthtex1` | Ordinary scene depth, or DH/vanilla classification |
| Protected shadows | `shadowtex1` | PCF comparison depth |
| DH-only additions | `colortex15`, `dhDepthTex` | Combined depth and LOD classification, d4 lines 201–215 |
| SSR additions | `colortex5`, `colortex7` | Previous scene color/fog, specular lines 256–258 |

This estimates **12 without DH / 14 with DH**, rising to **14 without DH /
16 with DH** when true SSR is enabled. `dhDepthTex1` is declared in the shared
LOD include but is not automatically live in deferred4. It is used by other
programs. `colortex2` and galaxy's `colortex13` are likewise not included just
because they appear in declarations. Prove the exact set with the driver and
Iris-generated source before treating these estimates as limits.

`include/misc/lod_mod_support.glsl:8–41` selects `colortex15` and `dhDepthTex`
when `DISTANT_HORIZONS` is defined, and lines 120–125 select `depthtex1` without
a LOD mod. Removing DH is the clean first control. Merely hiding its terrain
in a GUI may leave the shader macro and samplers active; record the actual
preprocessed macro rather than assuming a configuration toggle freed units.

## Minimal ranked driver probes

First create an isolated **DH-absent** profile using the existing masked
settings, with only environment/sky reflections restored:

```properties
SH_SKYLIGHT=false
SHADOW_COLOR=false
COLORED_LIGHTS=false
CLOUD_SHADOWS=false
ENVIRONMENT_REFLECTIONS=true
SKY_REFLECTIONS=true
SHADOW_VPS=false
BORDER_FOG=false
NORMAL_MAPPING=false
SPECULAR_MAPPING=false
GALAXY=false
```

Keep `SHADOW`, `SHADOW_PCF`, AO, cloud resolution, SSR step settings, framebuffer
size, source pack and scene identical between original/patched-disabled/active.
The last three lines make existing defaults explicit; they do **not** buy three
new free units relative to the current baseline. The two reflection changes
should expose the real smooth SSR path within the estimated 14-unit budget.
This is a reduced Apple SSR diagnostic, never full Photon Ultra.

Use actual water reflecting nearby terrain/buildings or hardcoded reflective
materials such as ice/obsidian/metals. `include/surface/material.glsl:24–35`
gives water `ssr_multiplier=1`; hardcoded material branches at 233–238,
264–269 and 276–284 also enable reflections. Rough multi-ray SSR is separately
guarded by both `SSR_ROUGHNESS_SUPPORT` and `SPECULAR_MAPPING` at specular line
316; the above profile does not prove that path. Merely changing a numeric
SSR option on a dry forest does not prove SSR executed.

Ranked checks after stopping the main matrix:

1. Link/render original Photon with the exact DH-absent settings above. Record
   driver uniform names/types, units assigned by Iris, relevant GL limits,
   generated final sources, shaders-in-use and fallback status. Check
   deferred4 plus vanilla water/translucent programs.
2. Toggle `ENVIRONMENT_REFLECTIONS=false` while leaving `SKY_REFLECTIONS=true`.
   Verify reflected nearby objects disappear while sky reflection remains.
   This visual control distinguishes actual SSR from specular highlights and
   sky fallback. Inspect in motion for history/fog artifacts.
3. Run original, patched-disabled and active LOD with the same linked settings
   in a reflection-heavy representative scene, repeated serially. Compare
   overall and affected-pass timings; don't change clouds or shadow quality
   between the controls. Inspect medium/far transition bands.
4. Only then add DH with the exact same settings. Estimate 16 dynamic samplers
   versus current 14-slot allocator; retain a precise failure or success report.
   Check `deferred4`, `gbuffers_water`, other translucent programs and
   `dh_water`. Do not broaden to every optional effect simultaneously.

The existing GLSL checks prove compiler validity of their existing
matrix, not this driver's final active sampler allocation or new SSR rendering.

## Other masked features

| Feature | Likely sampler/capability impact | Useful next action |
| --- | --- | --- |
| `SKY_REFLECTIONS` | Reuses `colortex4`, already needed in deferred4 vertex lighting | Enable with SSR; sky-only is a separate negative control |
| `CLOUD_SHADOWS` | Adds live cloud map `colortex8` to shading/translucent paths | One-at-a-time probe after SSR; no slot expected to spare with DH |
| `SHADOW_VPS` | Activates raw `shadowtex0` blocker search in pcss lines 230–250 | Keep off in first probe; it adds cost and likely a unit |
| `SHADOW_COLOR` | Adds `shadowcolor0` and raw `shadowtex0` paths | Keep off; preserving PCF does not require colored shadows |
| `BORDER_FOG` | Uses `colortex4`, already live in vertex lighting | No expected deferred4 unit saving; retain current baseline for quality comparability |
| `SH_SKYLIGHT` | Sky map already live, but enables `deferred4_a.csh` | Capability issue: pinned wrapper requires GLSL 4.30 compute/image store |
| `COLORED_LIGHTS` | Adds `light_sampler_a/b`, image-backed LPV and GLSL 4.30 setup/floodfill compute | Capability issue beyond samplers; leave off on current OpenGL 4.1 host |

Primary archive pointers: `shaders.properties:171–173,347–395`,
`world0/deferred4_a.csh:1–3`, `program/d4a_generate_sky_sh.csh:14–20`,
`world0/setup.csh:1–4`, `world0/shadowcomp.csh:1–4`,
`program/lpv/floodfill.csh:14–35`. These compute requirements explain why
freeing texture units alone is not sufficient to restore every feature on Mac.

Vanilla translucent source declares atlas, normals/specular, history/fog,
sky, depth and shadow samplers at `program/gbuffers_all_translucent.fsh:51–81`.
DH water declares history/fog, sky, vanilla depth and shadows at
`program/dh_water.fsh:46–71`, and uses `dhDepthTex1` for back-depth at line 210.
Their exact live counts depend on material mapping, refraction, program
variant and reserved external texture units. The deferred4 estimate must not
be copied onto these programs.

For the same exact mask, a typical vanilla `gbuffers_water`/translucent
estimate with true SSR is `{gtexture, noisetex, colortex4, colortex5,
colortex7, depthtex1, shadowtex1}`: seven retained samplers. Without SSR the
two history samplers can become dead. With DH plus SSR it additionally needs
`colortex15` for the combined ray depth and `dhDepthTex1` for back-depth
classification (translucent lines 387–394), estimating nine. These are source
sets, not nine dynamically allocated units: `gtexture` is externally bound,
and the applicable world/Sodium reservations have to be counted separately.
Normal/specular mappings, VPS and cloud shadows change the set. This makes
deferred4 the first likely blocker rather than assuming water is equally full.

There is an important DH split: pinned `program/dh_water.fsh:202–204` returns
early for water after storing its material Gbuffer. That program has no call
to `get_specular_reflections`; declarations for `colortex5/7` from its shared
include do not establish active SSR there. Actual **DH water reflections run
in `composite1`**, through `program/c1_blend_layers.fsh:366–390` calling
`include/misc/distant_water.glsl:105–121`. Composite1 defines `SSRT_LOD` at
line 130, so this ray uses `dhDepthTex`, not deferred4's combined-depth sampler.
Include composite1 in the linked-sampler audit and pass timing; looking only
at dh_water would miss the real reflection work.

With DH, SSR, VL and this reduced mask, composite1's likely retained set is
`{colortex0, colortex1, colortex3, colortex4, colortex5, colortex6, colortex7,
colortex11, colortex12, colortex13, depthtex0, depthtex1, dhDepthTex,
dhDepthTex1, noisetex}`: up to fifteen distinct samplers, versus the composite
allocator's fourteen available dynamic slots. Here `colortex7` is already
used for current fog, and SSR reuses it for its source-defined reflection
operation; `colortex5` and SSR noise are the extra candidates. Conditional
deadness and exact fog options must be checked by the final driver link.
`colortex15`, shadow samplers and cloud-shadow `colortex8` are deliberately
not counted just because this program/includes declare them. Composite1 can
be a second DH+SSR limit even if deferred4 is made to fit.

The pinned ShaderLOD patch changes the common
`include/lighting/specular_lighting.glsl` function: near retains original
intersection/refinement work; medium uses `shaderlod_budget` and at most two
refinement steps; far skips the environment trace and keeps sky fallback.
That function is shared by opaque deferred4, vanilla translucent/water, and
the composite1 DH water path. Rough SSR's ray-count reduction only executes
with specular mapping enabled; a smooth-water probe tests intersection and
refinement LOD, not the rough multi-ray loop. Thus both shoreline surfaces
and reflected object distances must be chosen deliberately, with near/full,
transition and medium/far surfaces visible.

## If DH plus SSR exceeds the current allocator

There is no source proof that the masked DH+SSR program requires **17+ physical
fragment units**. The estimate is 16 while Iris's two reservations constrain
it to 14 dynamic bindings. First determine the actual driver/Iris set. A
Photon-specific diagnostic of releasing composite reservations would require
separate evidence that those units have no external consumers during the pass,
that texture/sampler state is restored correctly, and that vanilla/DH/other
programs survive reloads. Do not silently change Iris's global allocator or
assume reservations are unnecessary.

If allocator relaxation is unsafe or physical active samplers still exceed
the host limit, a pinned Photon **separate SSR resolve pass** is a plausible
fallback. Move reflection evaluation after base deferred lighting: reflection
resolve reads material/depth/history and the shaded base color but does not
need diffuse shadows, AO or skytextured output. It must apply the same local
fog, cloud occlusion, water handling and projection conventions. Reusing
existing ping-pong color targets could avoid a new permanent history texture,
but framebuffer flip order and target liveness need explicit proof.

A separate pass adds a fullscreen dispatch, base-color reads/writes and
possibly duplicated material reconstruction/visibility sampling; its gain
cannot be assumed on this tile-based GPU. Establish a per-pass live-sampler
table and link it, compare images/motion against the original SSR path on a
host that can run it, then benchmark total frame cost with SSR enabled/disabled.
Do not sacrifice distant tree shadows to expose SSR, and do not treat reduced
vanilla distance as shader-ray savings. This would be one explicit pinned
Photon patch, not a universal GLSL rewriting framework.
