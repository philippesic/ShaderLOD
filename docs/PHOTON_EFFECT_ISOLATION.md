# Photon effect-isolation controls

Prepared 2026-10-04 after static-water pilot reports still showed an active
slowdown. These switches add causal controls, not another optimization or a
performance claim. Root owns driver rendering, serial measurements and visual
acceptance; this worker changed only Photon patches/helpers/tests and this doc.

## Pack options and scope

All five booleans default true in Photon settings and appear in ShaderLOD's
shader-options screen. They are **compile-time pack options**, not live mod
settings. The existing bridge still supplies global enabled/preset. Resolved
option maps and archive hashes must be recorded after reload/launch.

| Option | Affected work | Off behavior |
| --- | --- | --- |
| SHADERLOD_CLOUDS | Cumulus, altocumulus, congestus primary/lighting/ambient caps; optional cached cloud tiers | Select pinned original cloud includes, original const secondary counts/signatures, and zero all cloud/cached-sky helper tiers. |
| SHADERLOD_WATER | Surface normals/octaves and water parallax, vanilla and distant-water shading | Emit no cheap kernels; wrappers directly call untouched original normal/parallax functions without classifying quality. |
| SHADERLOD_FOG | Overworld air, underwater, End and LPV volumetric ray budgets | Select pinned original includes, restoring original counts/const declarations and ray work. |
| SHADERLOD_SSR | Shared smooth reflection trace and rough multi-ray reflection budgets | Select pinned original specular-lighting include, restoring original trace/refinement behavior and constant SSR_RAY_COUNT loop/normalization. |
| SHADERLOD_MATERIALS | POM march/shadow budgets and far POM rejection | Select pinned original parallax include and bypass extra solid-program POM rejection. |

Flags do not switch Photon's underlying effects off. For example SSR=false
here means original full SSR, not ENVIRONMENT_REFLECTIONS=false. Water=false
retains original waves and parallax if those Photon options are enabled.
Materials currently means the explicit POM integration; it does not disable
normal/specular maps or ordinary material lighting. Original shadow caustics
remain unchanged regardless of the water flag.

Lighting protection is unchanged. Use SHADERLOD_PRESERVE_LIGHTING=true, cached
sky=false and debug=false in causal captures. These five switches do not own
PCF/SSRT/AO/debug scaffolding. The original absent-mod and global bridge-disabled
controls remain necessary to quantify retained integration cost. An all-off
pack is not advertised as byte-identical to the complete original shaderpack.

The main source pack also retains the earlier structural atmosphere guard,
independent of these effect toggles. **Use the clean no-atmosphere isolation
archive below** for all-off/single-effect attribution. Otherwise main all-off
would still contain that additional active structural candidate.

## How original source is selected

Each modified cloud/fog/SSR/POM include now has one explicit compile-time
branch: on selects its reviewed existing derivative; off selects the complete
pinned original include contents. This intentionally restores original loop
constants, parameter signatures and normalization rather than simply passing
runtime tier 0 into altered dynamic loops. The SHA-verified literal manifest
carries both branches. It is larger because those original bytes are retained;
there is no general source-rewriting engine or heuristic runtime transform.

Water keeps its original functions untouched in both modes. water_lod.glsl
emits static kernels only inside SHADERLOD_WATER; its always-available surface
wrappers select original functions directly when the flag is absent. Shadow
calls stay original. The material call-site rejection is separately guarded in
gbuffers_all_solid.fsh so materials-off also restores original POM eligibility.

Fog and SSR use new dedicated `shaderlod_fog_tier` and `shaderlod_ssr_tier` at
owned call sites. Generic tier helpers remain owned by lighting/debug and are
not globally redefined. Cloud, cached-sky, water and material helper getters
return zero when their respective effect is off, including standalone uses and
bridge-disabled cases. Off clouds overrides the optional global cached-sky
quality flag even if that global option is true.

## Exact profile recipes

Keep global mod enabled=true, preset=PERFORMANCE, terrain policy disabled,
SHADERLOD_USE_MOD_CONFIG=true and pack SHADERLOD_ENABLED=true. Set protected
lighting=true, cached sky=false, debug=false, analytic water=false. The upstream
Photon settings, scene, clocks, wetness telemetry, framebuffer and DH presence
must match between arms. Existing stored shader options can override defaults;
write the values explicitly and inspect Iris's effective map.

Use the following five rows as the complete flag map for each profile:

| Capture | CLOUDS | WATER | FOG | SSR | MATERIALS |
| --- | --- | --- | --- | --- | --- |
| all-off | false | false | false | false | false |
| clouds-only | true | false | false | false | false |
| water-only | false | true | false | false | false |
| fog-only | false | false | true | false | false |
| SSR-only | false | false | false | true | false |
| materials-only | false | false | false | false | true |
| all effects, finite water | true | true | true | true | true |

Each column is prefixed SHADERLOD_. For example water-only writes:

```properties
SHADERLOD_CLOUDS=false
SHADERLOD_WATER=true
SHADERLOD_FOG=false
SHADERLOD_SSR=false
SHADERLOD_MATERIALS=false
SHADERLOD_WATER_ANALYTIC=false
SHADERLOD_PRESERVE_LIGHTING=true
SHADERLOD_CACHED_SKY=false
SHADERLOD_DEBUG=false
SHADERLOD_USE_MOD_CONFIG=true
SHADERLOD_ENABLED=true
```

The live mod then supplies enabled=true and PERFORMANCE. Switching only the
live mod enabled=false produces a global-disabled arm while preserving the
same selected flags. For a compile-time global-disabled control, set
SHADERLOD_USE_MOD_CONFIG=false and SHADERLOD_ENABLED=false; otherwise the live
bridge takes precedence. Distinguish those controls in report names.

SSR-only requires actual environment reflections to be enabled in all compared
arms to measure that trace. Materials-only similarly requires a suitable POM
resource/material workload; absent POM cannot establish a saving. Fog-only
needs VL or the corresponding LPV path active. DH-related checks are separate
from DH-absent shoreline SSR controls and remain subject to the actual driver's
sampler budget. Effect switches do not make unsupported shader features safe.

## Static checks and next runtime boundary

Tests verify default-on settings, exact original off-branch bytes, dedicated
Fog/SSR call-site partitioning, actual helper tiers for all-off/all-on/every
single effect and live bridge disable, original water/shadow bodies, and no
water kernels when water is off. Actual water CPU fixtures cover every 1–8
original octave count/preset, procedural/constant noise, finite normals and
parallax, analytic derivatives, multiple phases and shadow/off routing.

The broad GLSL matrix adds all-off and all five singles across all dimensions/
programs, plus LPV/colored-light original-off source. Existing standalone,
global-disabled, dynamic bridge, shadow/material and analytic variants remain.
With 32 broad configurations across 3 dimensions and 11 programs, plus 192 focused
water configurations, the total is 1248 fragment compilations. This is syntax/
type evidence, not Iris driver linkage, frame time or fidelity.

After static validation root can prepare original absent-mod, patched global
false, active all-off and each single on the same inspected scene. First compare
all-off against global false to expose retained global/lighting/debug integration
cost; then compare singles against all-off. Repeat balanced complete-JVM runs
with the same clock/weather policy and preserve all raw samples. Only after
individual paths are understood should useful combinations be tested. The
ordinary meaningful-performance gate remains unmet; adding shaders is not
justified by having more knobs or lower theoretical sample counts.

Static result: 15 shaderpack tests passed in 103.548 seconds, including 1248 GLSL
fragment compilations. Raw output is retained outside Git at
`dev/logs/photon-effect-flags-tests.log`; targeted effect/water math also passed.
No runtime or performance pass is claimed.

| Ready artifact | SHA256 |
| --- | --- |
| `build/photon-effects-no-atmosphere.zip` (recommended causal input) | `ab1b6d859829fe3e0925b69cdd3759369c44bc876cc97de77d9878ed27b8658d` |
| `build/photon-effects-candidate.zip` (main source, existing atmosphere guard) | `44fe27e24b51c3fbffd37fb2ba844f9a2420b507f1c9d424fa6d2174c143a16d` |

The clean source root is `build/effect-isolation-no-atmosphere-source`, with
manifest SHA256 `cb4553b01f62718c21270bae4283f8753ffd26badd61b9ace876147ef16688b6`.
Main manifest SHA256 is `4f95fd221f197cb277c0653ac42a4843c94e5ab036f41b6b8ccadf1319f04e35`.
Each ZIP embeds shaderlod-build.json; ignored build/photon-effects-candidates.json
retains both exact metadata maps. The clean d4 source matches preserved 7ca
byte for byte, while the main artifact intentionally retains the guard.
