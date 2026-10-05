# MakeUp water SSR experiment

Source/static handoff: 2026-10-05. Root owns Minecraft, reloads, captures and GPU
timings. No client or GLSL compiler is launched by this adapter worker. An
assembled ZIP or passing fixtures does not establish shader compilation.

## Pin and scope

MakeUp Ultra Fast 9.5g, Modrinth version `pgb0JjoN`, release SHA256
`0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8`.
Source revision `ad2d9327ad485429e4eaf2b3f98cd738ada609de`; candidate research
verified all 344 release shader files against it. Exact publisher SHA512 and
upstream source/archive pins are in `dev/shader-candidates.lock.json`. This is
an exploratory adapter, not a supported second pack. Java config/common protocol
is not connected. Preserve the upstream LGPL v3 notices, LICENSE, README and
GPL v3 text; the archive includes editable GLSL and the complete literal patch.

Modified entry points: `shaders/lib/water.glsl::reflection_calc`, reached from
`water_shader` in `shaders/common/water_blocks_fragment.glsl`, included by
`gbuffers_water.fsh` and `gbuffers_hand_water.fsh` under root/world0/world1/world-1.
Option declarations/UI are bounded changes to `lib/config.glsl`,
`shaders.properties` and `lang/en_US.lang`. New helper:
`lib/shaderlod/water_ssr.glsl`. Original `fastRaymarch`,
`cristal_reflection_calc`, DH and VOXY water, all lighting and shadows are retained.

## Behavior and quality budget

Native `fastRaymarch` uses `exp2(i + dither)` exploration then binary refinement,
with hidden-surface classification and `no_hidden_steps < 3` fallback in the same
ten-iteration loop. Reducing iterations and scaling stride cannot preserve those
decisions merely by preserving ray extent. This experiment therefore uses the
authorized safer fade/skip alternative rather than claiming equivalent sample
reduction.

`SHADERLOD_MAKEUP_WATER_SSR=0` is the default and selects the complete original
`reflection_calc` text. The disabled GLSL excludes the added helper. Pack member
bytes still contain option/preprocessor additions; the off function and original
ray function are exact original text. The source checks distinguish those facts.

When enabled with native `REFLECTION_SLIDER=2`, radial distance comes from
`dot(fragpos, fragpos)` in Minecraft block units. Pinned
`src/position_vertex_water.glsl` sets `fragposition = (gl_ModelViewMatrix *
gl_Vertex).xyz`; the fragment passes that originating camera/view-relative value.
Full reflection weight remains through 96 blocks, smoothstep fades it to zero
through 160, and the far branch returns before any SSR depth or scene fetch.
Options clamp start to zero and end to at least start + 1 block. There is no
moving random mask; camera motion still needs temporal quality inspection.

Every retained trace uses original search/refinement math, ten iterations and
original exponential ray coverage. Fade pixels retain the full trace budget.
Beyond the end, the existing `water_shader` fallback receives reflection alpha
zero and uses its precomputed sky/cloud color; `infinite=1` follows the native
offscreen sun-glint path. This removes distant on-screen reflected geometry.
Sun-glint differences at the fade end must be inspected because the native
`infinite` value describes ray outcome rather than the blend weight. Native
reflection-off/projected-image controls remain original code and ignore the new
option. No medium/far shortened raytrace kernels are implemented.

## Explicit sampling and near limits

The enabled helper replaces only its copied depth fetch with
`texture2DLod(depthtex1, ..., 0.0)`; the active reflection function fetches
`texture2DLod(gaux1, ..., 0.0)`. This avoids reliance on implicit derivatives
across the nonuniform distance skip. Both operations occur after the skip;
there is no discarded unconditional trace or late branch hiding it.

Pinned MakeUp declares `gaux1Format = R11F_G11F_B10F` in
`common/final_fragment.glsl`; it requests mipmaps only for colortex0/colortex1
in composite/composite1. No `gaux1MipmapEnabled` or `colortex4MipmapEnabled`
directive exists. Depthtex1 has no mipmap declaration. All eight water entry
points enable `GL_ARB_shader_texture_lod` in GLSL 120.

Static inspection of the actual pinned Iris 1.8.12 jar (SHA256
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`)
with `javap -c -p` shows `RenderTarget.setupTexture` initializes noninteger
targets with GL_LINEAR (9729) for MIN/MAG and CLAMP_TO_EDGE (33071), allocating
level zero. `DepthTexture` allocates level zero with GL_NEAREST (9728) MIN/MAG
and CLAMP_TO_EDGE. `ProgramDirectives` starts mipmappedBuffers empty and fills
it only from enabled pack directives. Combined with the pinned declarations,
this supports base-level sampling equivalence as a source inference. The enabled
near path is **not byte-identical**: its fetch syntax differs. Exact driver
pixels, sampler bindings and near motion still require a runtime comparison.

## Build and source checks

```
python3 scripts/build_makeup_adapter.py
python3 -m unittest discover -s tests -p test_makeup_adapter.py -v
```

Default input is the ignored pinned release under
`dev/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip`; output is ignored
`build/MakeUp-ShaderLOD-Experimental-9.5g.zip`. Builder verifies both archive
hashes, source revision agreement, every touched/origin/sampler source hash,
unique literal anchors, archive paths, duplicate entries and retained license.
Rebuild ordering, timestamps and permissions are fixed. Overlay collision or a
changed pin fails closed. Fixtures exercise these guards and archive determinism;
available pinned-source checks verify the unchanged origin/glass/DH paths and
the copied ten-step search math. No fixture result is a driver check.

Worker check result: all **7 tests passed** (2026-10-05), including real pinned
archive/source inspection when the ignored original was present. Deterministic
packaging passed on small fixture archives. No real derivative artifact build,
GLSL compilation, shader reload, in-world comparison or timing was performed
by this worker. A source review corrected an include-guard collision with the
option macro before handoff; the guard contract is now checked explicitly.

## Root runtime procedure and needed measurement

Use locked Minecraft 1.21.1 / NeoForge 21.1.253 / Iris 1.8.12 / Sodium 0.6.13
on the recorded M3 Max first with DH absent. Start with the original MakeUp ZIP
and a modest native medium profile, `REFLECTION_SLIDER=2`, AA_TYPE=2, no DOF or
motion blur. Confirm native water SSR actually renders before derivative reload.
If a Mac incompatibility appears, retain the failing log and isolate it against
original first; do not silently remove reflection/shadow quality from one arm.

Then compare original, derivative off, derivative on (96/160), and native
projected reflection (`REFLECTION_SLIDER=1`) at matching resolution, scene,
stack, weather/time, wetness, camera and render radius. The projected arm is a
different quality control, not a ShaderLOD gain. Prefer a shoreline view with
near water, reflection-rich bank/trees, thin silhouettes, glass, sky boundaries
and water beyond 160 blocks. Move toward/away from the band and inspect shimmer,
near pixels, sun glint, underwater/refraction and offscreen fallback. Lower
32/64 bands may be a diagnostic to prove classification only; do not conflate
those quality settings with the 96/160 candidate. Test DH installed separately:
DH water itself remains untouched and the detail/DH boundary may reveal mismatch.

After compilation/rendering and quality capture, run repeated balanced serial
off/on GPU elapsed-time captures, retain median/tails and all failures. Measure
the water/translucent pass with GPU tooling if available, plus existing
game-render scope with CPU/frame clocks. Record pack/option/version/artifact
hashes and warmup. Ordinary-scene controls and actual count of far-water pixels
are needed to interpret a null result. No measured original/off/on GPU times
or runtime quality evidence are available at this source handoff; no speedup,
compatibility or accepted-quality claim follows.
