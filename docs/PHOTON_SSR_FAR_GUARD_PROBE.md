# Photon far-SSR full-trace diagnostic

2026-10-05 static handoff. Owner: fresh performance chat's bounded
`photon_cloud_guard` worker. **This is a default-off quality/causality control
that intentionally restores expensive far tracing. It is not an optimization.**
Only the four newly assigned builder/manifest/tests/document files were written.
Main/shared Photon patches/helpers/tests, Java, the earlier cloud experiments
and runtime files remain untouched. No full archive was built, no compiler ran,
and no client/reload/GPU measurement was performed by this worker. Root owns
all next driver, moving-quality and timing checks.

## Why this control is bounded

[PHOTON_GLINT_SOURCE_AUDIT.md](PHOTON_GLINT_SOURCE_AUDIT.md) identified the far
SSR no-trace sky fallback as one plausible cause of an enabled bright-water band.
Root then completed the original/disabled/enabled native 10 FPS triplet, 201
frames per arm. The enabled broad band was absent in original/disabled at the
matched inspected pose. Compile-time alloff lacked the band, with 200/201 images
and one retained writer-backpressure skip. Those are separate quality evidence,
not FPS/GPU measurements.

Root's SSR-only native 2 FPS sequence (session 7364) completed 41/41 frames,
with NONE fog/no-full-collision/hash checks and the band present at inspected
frame 10. Water-only session 30204 completed 41/41 with the same context/hash
checks and no band at inspected frame 10. SSR therefore suffices to reproduce
the band at this pose. This does not universally clear other effects or prove
moving-quality acceptance from a low-rate sequence.

This control tests the far skip independently of medium trace caps, water
normal/parallax changes, direct sun highlight or a brightness adjustment.
If it removes the band, that establishes a useful local causal distinction;
it does not by itself select a production quality policy or establish a speedup.

## Exact input, attribution and output source pins

Input is immutable B15 `build/photon-lighting-tier-no-atmosphere.zip`, SHA-256
`b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c`.
This is the Photon v1.3b protected-lighting/no-atmosphere derivative, not current
main-tree sources or any earlier cloud probe. The builder verifies the whole
archive before reading members. It does not import the shared patch engine or
overlays, and refuses output that overwrites B15 or leaves ignored `build/`.

Reviewed anchors are in
[ssr-far-guard-probe.json](../patches/photon-experiments/ssr-far-guard-probe.json).
One fixed literal anchor changes the derivative's local trace tier in
`shaders/include/lighting/specular_lighting.glsl`; another adds an independent
default-off setting. No extra helper or new trace call is added.

| Member | B15 SHA-256 | Candidate SHA-256 |
| --- | --- | --- |
| `shaders/include/lighting/specular_lighting.glsl` | `6a4a8c53675f7287cf28168913f3e3e2b7ce9bd9d2fb5755548a27927ee4d92d` | `d57fce9683b3675f60edb08dd7f52168833852c7b57e15196387d337e8938df8` |
| `shaders/settings.glsl`, diagnostic off | `d1f571e5d96ecffdf7ebcfcf28a13d34dc5bfd5c3606702e06832f95f88aa3cd` | `acfe98c49ed20ddf7118ff9c2faf1b65d1f2222c56187b6cec7cc905eb08aed7` |
| `shaders/settings.glsl`, diagnostic on | same B15 source | `33e39309f4e9a31990ce722c32cd56bbd981880f6447756f87bbbebebad69602` |

Photon attribution and the full upstream license remain included unchanged.
Photon is by Benjamin Stott / SixthSurge, under its own license separate from
the mod's. `LICENSE` SHA-256 is
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
B15's embedded `shaderlod-build.json` is preserved byte-for-byte as baseline
metadata, SHA-256
`5653a52f590ce10cd75fbaff24f1ed053e49564ade7b4c79bb2747f69feed084`.
Each output adds `shaderlod-ssr-far-full-trace-probe.json` with its arm,
input/manifest/member hashes, policy and unverified runtime status. No remote
publication or distribution occurred.

## Local policy and every downstream effect

In exact B15, `trace_specular_ray` begins its environment section under
`#ifdef ENVIRONMENT_REFLECTIONS`, then computes:

```glsl
int quality = shaderlod_ssr_tier(length(view_pos), dither);
```

The independent pack option is `PHOTON_SSR_FAR_FULL_TRACE_PROBE`, default off.
When defined, the only added runtime statement is:

```glsl
if (quality == 2) quality = 0;
```

This changes only the local trace policy; it does not alter the shared tier
classifier or helper values. The local `quality` has exactly three downstream
uses, all retained as literal B15 source:

| Existing use | Consequence for selected far tier when diagnostic is on |
| --- | --- |
| `if (quality != 2)` | Runs the existing environment raymarch instead of skipping it. |
| `shaderlod_budget(int(intersection_step_count), quality, 2)` | Tier 0 returns the original caller intersection count without a cap. |
| `quality == 0 ? refinement_step_count : min(refinement_step_count, 2u)` | Returns the original caller refinement count. |

The caller already selects `SSR_INTERSECTION_STEPS_SMOOTH` for mirror-like
water/surfaces or `SSR_INTERSECTION_STEPS_ROUGH` for eligible rough reflections,
and passes `SSR_REFINEMENT_STEPS`. The diagnostic respects those upstream
options instead of hardcoding 16/4. In the inspected smooth-water profile those
options are 16/4; PERFORMANCE medium remains 8/2. Other caller counts remain
their selected upstream values at diagnostic far.

**The rough reflection ray count is not restored.** Its separate
`shaderlod_budget(SSR_RAY_COUNT, shaderlod_ssr_tier(...), 1)` remains B15 and
does not consume this local variable. The first actual glint workload has
specular mapping off and water excludes the rough branch, but broader rough
material rendering remains a separate check.

The changed trace result can propagate through existing downstream code:

- `hit_pos`, initialized to zero in B15, can now hold an actual far ray result.
  The same sky-reflection call uses it for `hit_sky`/skylight falloff.
- The unchanged `if (hit)` path can now reconstruct/reproject the far hit;
  previous-scene `textureLod`, reflected fog `texture`, analytical fog/shadow
  calculation and border attenuation may therefore be consumed there.
- The existing no-hit and invalid-history-UV fallback still return sky
  radiance. A restored trace can still miss, hit sky/hand, leave screen bounds,
  or fail history reprojection; this control does not force a reflected hit.

These are intended output consequences of restoring upstream trace work, not
changes to fog/sky math. Direct GGX sun/moon highlight, material/BRDF/Fresnel,
water normal/parallax, lighting/shadow factors and all output composition stay
literal source. There is no sun suppression or glint-intensity adjustment.

## Eligibility, distance, transitions and disabled baseline

Exact B15 contains no named `do_ssr` variable; its far skip is the
`quality != 2` branch. The diagnostic is inside the existing
ENVIRONMENT_REFLECTIONS compile-time guard. Environment off still emits
`hit=false`/zero hit position with no trace; sky-only reflection is not
indiscriminately turned into SSR. `material.ssr_multiplier > eps` caller gates,
`NoL < eps` exits, water versus rough-material selection, POM/material feature
gates and configured smooth/rough defaults remain unchanged. A caller that
upstream would not invoke cannot be re-enabled by this local assignment.

SSR distance remains `length(view_pos)` in camera-relative view block units.
The source classifier, live enable/preset, dither and generic bands remain
unchanged. For current 32/96/8 bands, full near is stable through 28 blocks,
medium is stable from 36 through 92, and far is stable at 100 and beyond.
Within 92–100, only pixels selected as tier 2 by existing transition noise
receive full tracing; selected tier 1 retains its existing cap. With transition
width zero, far begins at the existing 96-block threshold. No new boundary,
noise, jitter or transition logic is introduced. This intentionally leaves
medium cheaper while restoring far work; it is a causal control rather than
a proposed monotonic production quality policy.

Live global disable/tier zero remains original full tracing. Compile-time
SHADERLOD_SSR-off still selects the entire unchanged pinned original effect
tail in this B15 member, regardless of the diagnostic option. Diagnostic
compile-time off recovers the exact B15 source after selecting only its added
directives away; no new runtime work survives that source selection. The
`original` builder arm preserves B15 source bytes. Here “original” means the
builder's B15 input, **not** the separate mod-absent original Photon control.
Root should use actual upstream Photon additionally for the requested comparison.
This pack option adds no Java field or uniform transport. Defaults stay off.

## Sampling/context qualification

The added branch contains only an integer assignment. No texture operation,
sampling form, new enclosing sampling branch or second raymarch is introduced.
The unchanged depth march/refinement uses `texelFetch`; projection, ray extent,
thickness/tolerance, hand/sky tests, ray origin/direction and dither are untouched.
Overworld sky filtering uses the existing explicit-LOD bicubic path, and scene
history uses existing `textureLod`.

The existing hit-dependent reflected fog read uses implicit `texture(colortex7,
hit_uv_prev)`, and non-Overworld sky sampling uses implicit `texture(colortex4,
...)`. Their literal branch/math/form remains unchanged, but far rays can now
produce hits, so dynamic consumption and derivative context still need runtime
review. No gradients or explicit LOD were substituted. The cloud sampler
diagnostics concern different inputs/programs and do not qualify reflection
samplers here. Source identity cannot prove universal derivative safety, exact
pixels or history stability. The first operator test is the existing reduced
Overworld no-DH glint profile; DH+SSR/full desktop/POM/rough materials and other
dimensions are not established by it.

## Static checks and root commands

Executed only:

```sh
python3 -m unittest discover -s tests -p 'test_photon_ssr_far_guard_probe.py' -v
```

**11 lightweight tests passed**, including exact archive/license/metadata,
default-off byte restoration, enabled/disabled declaration delta, original
environment-false branch, caller gates, local-quality uses, rough ray-count
preservation, unchanged texture/ray/fog/sky source, upstream-count and band-edge
fixtures, and corruption/ambiguous-anchor/output guards. The band/count fixtures
are CPU policy models with source checks; they are not GLSL or texture execution.
No compiler, full ZIP build, world copy or GPU client ran.

After the root operator has an exclusive terminal runtime checkpoint, build:

```sh
python3 scripts/build_photon_ssr_far_guard_probe.py --arm original
python3 scripts/build_photon_ssr_far_guard_probe.py --arm disabled
python3 scripts/build_photon_ssr_far_guard_probe.py --arm enabled
```

Outputs are ignored `build/Photon-SSRFarFullTraceProbe-{arm}.zip`, with fixed
entry order/timestamps/permissions/compression. Whole ZIP hashes must be recorded
after building; none are asserted here. Shader entry points include vanilla
water/translucent Gbuffer through `program/gbuffers_all_translucent.fsh`, deferred
surface reflection through `program/d4_deferred_shading.fsh`, and unchanged LOD
water consumers where present. Root owns all shader reloads.

Next compare original Photon, exact B15 SSR-only guard-off and guard-on with the
same glint request, effective options and advancing temporal inputs; warm histories
after reload. First verify driver compilation/rendering without fallback and
near/mid unchanged versus off, then inspect whether the far band is removed,
reflected terrain/grazing sun/screen edges/hand and slow translation/yaw reversal.
The earlier 2 FPS controls identify appearance at poses; matched native 10 FPS
or better sequences still need complete callback/context/hash contracts.
Do not use PNG capture runs as performance measurements.

If moving quality improves, measure the added cloud-independent SSR/whole-render
GPU cost in balanced serial runs without capture readback, with one client,
exact archives/options/resolution/weather/terrain and warmed histories. Preserve
negative or flat results and inspect CPU limits separately. This control is
expected to request more far tracing, but no physical instruction count or
timing magnitude is predicted. If the band persists, retain that causal negative
and inspect the remaining hit/fallback path instead of moving thresholds or
weakening sun/sky to hide it. No shared integration, default change, optimization,
performance gate or support promotion is assigned by this handoff.


## Actual private off/on quality control

Root11fixture tests andactualZIPbuildpassed. Disabled03d6115056fc4c2009839d1c
572555d64e698089c27cc16ec4724a6f4ac1474c; enabled41ecbee8b72039654fb0d25b16a841e3a2f4500025ec918687f94c0b8cba1555.
Actualdisabled48076/enabled30338 exit0/41of41/0skips each, native2976×1672/
2FPS20s westglint/time12500/minimal/noDH/SSR-only/globaltrue/expectedNONE/
nonfullcollision. All82context/imagehashchecks verified. Rootframe10off
reproducesbrightdistantwaterband; tier2→0 restoredtrace on removesband in
matchedposeandrestoresoriginal-likeappearance. Thisboundedintervention supports
farSSRskip asobservedbandcause, notallmovingquality ornativepixelparity.
Moreintersection/refinement work intentional; no GPUgain measured. Harness
classesdifferbecausecaptureboundchangeswerecompiledbetweenruns; actualsource/
pose/options/classes individuallyrecorded, nocross-runbyte-identicalclaim.

DisabledUUIDf344e6f0…/enabled76b73034… sourceprofiles/rawmanifests/logs under
`dev/logs/photon-ssr-far-guard-{disabled,enabled}-quality-r1`. Originalrough
ray-countLOD unchanged. Rootnextseparatedefaultoff far-tier2→1 reducedtracing
probe tofindaquality/cost compromise; fulltracediagnosticremainsunintegrated.
No existingmain/shareddefault orsupportedpack change.
