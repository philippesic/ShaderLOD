# Photon reduced far-SSR private probe

2026-10-05 static handoff. Owner: fresh performance chat's bounded
`photon_cloud_guard` worker. **Default off; quality and GPU cost unverified.**
Only this new document, builder, manifest and source tests were written. The
full-trace diagnostic, main/shared shaders/helpers/tests, Java and earlier
experiments remain untouched. No archive build, compiler, client, reload or GPU
run was performed by this worker. Root is the sole runtime operator.

## Evidence motivating this separate probe

Root's original/disabled/enabled glint captures found an enabled bright distant
water band. The alloff control lacked it; SSR-only reproduced it at the inspected
pose, while water-only did not. Root then ran the separate
[far full-trace diagnostic](PHOTON_SSR_FAR_GUARD_PROBE.md): disabled session 48076
and enabled session 30338 each completed native 2 FPS, 41/41 frames, zero skips,
with all NONE/no-full-collision/hash checks. At inspected frame 10, the disabled
band persisted, while far tier 2→0 full tracing removed it and restored
original-like far reflections.

That is pose-specific quality/causality evidence for the hard far skip/fallback.
It is not native pixel parity, complete moving-quality acceptance or a
performance result. The reduced probe maps tier 2→1 and must demonstrate its
own quality; full tracing's success does not guarantee fewer trace/refinement
steps will preserve the same hits or remove the band.

## Exact source and ownership boundary

Input is immutable B15 `build/photon-lighting-tier-no-atmosphere.zip`, SHA-256
`b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c`.
It is Photon v1.3b's protected-lighting/no-atmosphere derivative. The builder
does not read the full-trace archive, current overlays, shared helpers or the
existing main patch builder. It verifies the complete archive and applies one
unique literal anchor to each of two fixed members:

| Member | B15 SHA-256 | Reduced candidate SHA-256 |
| --- | --- | --- |
| `shaders/include/lighting/specular_lighting.glsl` | `6a4a8c53675f7287cf28168913f3e3e2b7ce9bd9d2fb5755548a27927ee4d92d` | `6db453ffc732600265d642d3fd4b7ff9a2c34fda66ec64010ec9f9e24afa471a` |
| `shaders/settings.glsl`, probe off | `d1f571e5d96ecffdf7ebcfcf28a13d34dc5bfd5c3606702e06832f95f88aa3cd` | `0f4099b0cf7bd4f24494f12cbeb89c1bde891d5e3f58921a8154e9ab789e04b2` |
| `shaders/settings.glsl`, probe on | same B15 source | `a61e7f1c13a78fd3c6eb9ce72d9aec079b707b14c1ae2c3fb6251aa072de7299` |

The recipe is
[ssr-far-reduced-probe.json](../patches/photon-experiments/ssr-far-reduced-probe.json).
Photon attribution and license remain unchanged. Photon is by Benjamin Stott /
SixthSurge under its own license, separate from the mod's. `LICENSE` SHA-256 is
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
B15's entire `shaderlod-build.json` remains as baseline provenance, SHA-256
`5653a52f590ce10cd75fbaff24f1ed053e49564ade7b4c79bb2747f69feed084`.
Outputs add only `shaderlod-ssr-far-reduced-probe.json` for the new arm,
input/manifest/member pins, policy and unverified runtime status. No publication
or distribution occurred.

## Local tier policy and exact selected budgets

Independent shader option `PHOTON_SSR_FAR_REDUCED_TRACE_PROBE` is default off.
Inside the existing `ENVIRONMENT_REFLECTIONS` section of `trace_specular_ray`,
immediately after the unchanged distance/dither classification, it adds only:

```glsl
if (quality == 2) quality = 1;
```

The existing three downstream local-quality uses remain literal B15:

- `if (quality != 2)` now reaches the existing far raymarch instead of skipping.
- `shaderlod_budget(int(intersection_step_count), quality, 2)` selects B15's
  tier-1 cap from the original caller's smooth or rough intersection count.
- `quality == 0 ? refinement_step_count : min(refinement_step_count, 2u)`
  selects B15's existing tier-1 refinement cap.

The original options are smooth intersections 16, rough intersections 8 and
refinement 4. Selected far counts when the private probe is on are:

| Live preset | Smooth intersection | Rough intersection | Refinement |
| --- | ---: | ---: | ---: |
| Balanced | 12 | 6 | 2 |
| PERFORMANCE | 8 | 4 | 2 |
| Aggressive | 4 | 2 | 2 |

This comes from the existing tier-1 scales 0.75/0.50/0.25, ceil, minimum 2 and
final cap by original count. It does not hardcode those counts into GLSL;
custom advertised upstream options continue passing their caller arguments.
Small-count fixtures check original counts 1–4, including originals below the
helper's nominal minimum; the final cap never increases them. Refinement 1
stays 1, 2 stays 2, and 3/4 cap to 2. Those fixtures verify policy only.

Near/tier-zero remains full upstream tracing, medium remains existing B15
tier-1 tracing, and global live disable still returns tier zero. The separate
rough reflection `SSR_RAY_COUNT` LOD still calls the original far classifier;
it is not remapped or restored here. Water excludes the rough branch, and the
first glint profile has specular mapping off. Rough materials need their own
driver/quality evidence.

Caller/environment eligibility is unchanged: environment off still emits
`hit=false` with no raymarch; material `ssr_multiplier`, `NoL` exits, POM/material
feature gates and water/rough selection remain literal source. Exact B15 has
no named `do_ssr` variable; this control replaces only its selected-tier far
skip. No upstream false/no-call case is indiscriminately re-enabled.

## Distance, fallback and sampling consequences

Distance remains `length(view_pos)` in camera-relative view block units, with
the unchanged classifier, live bands and dither. Current 32/96/8 bands are stable
near through 28, medium 36–92 and far from 100 blocks. Within 92–100, selected
far pixels use tier-1 tracing instead of no trace; selected medium pixels already
use tier 1. Zero transition keeps the existing far boundary at 96. No new
threshold, noise, transition, wave, ray direction, jitter input, projection,
geometry or thickness formula is introduced.

Diagnostic-off preprocessing recovers exact B15 source. Compile-time
SHADERLOD_SSR-off still selects its unchanged original Photon effect tail,
regardless of this option. The `original` builder arm preserves B15 bytes;
it does not mean the separate upstream original Photon control. Root should
include upstream original additionally for quality comparison. This pack
option adds no Java configuration or shared protocol field; zero default change.

Source raytracer inspection shows the original intersection count influences
`step_length = ray_length / intersection_step_count`, the
`screen_pos + dither * ray_step` start and
`max(abs(ray_step.z) * 3.0, 0.02 / sqr(view_pos.z))` depth tolerance. Those formulas
and the original refinement/hand/sky tests are unchanged, but passing reduced
counts changes sampling positions, spacing, tolerance and possible hit decisions.
Fewer refinement steps can move the final hit and its validity. Unchanged ray
source is not identical sample geometry, traced extent, hit semantics or physical
ALU work. No compiled shader/disassembly or runtime depth/ray replay was inspected.

Successful far traces can change existing `hit_pos`, sky hit/skylight falloff,
history reprojection, border attenuation and hit-dependent reflected fog.
The existing sky/no-hit/invalid-history fallback remains intact. Direct GGX sun/
moon, Fresnel, material BRDF, light/shadow factors, water normal/parallax and
composition are unchanged. No glint dimming, fog alteration or new texture form
was used to hide the band.

The added branch has only an integer assignment, with no new trace or enclosing
sampling branch. The unchanged depth march uses `texelFetch`, scene history
uses `textureLod`, and Overworld sky uses explicit-LOD bicubic filtering.
Existing hit-dependent implicit fog `texture(colortex7, hit_uv_prev)` and
non-Overworld implicit sky sampling remain. Far hit coverage/history can change,
so there is no universal derivative or pixel-parity claim. Cloud sampler evidence
does not qualify these reflection programs. First runtime scope is the reduced
Overworld/no-DH glint profile; DH+SSR, desktop feature settings, POM, rough
materials and other dimensions remain separate compatibility/quality checks.

## Static checks and root commands

Executed only:

```sh
python3 -m unittest discover -s tests -p 'test_photon_ssr_far_reduced_probe.py' -v
```

**12 lightweight tests passed**, covering exact archive/license/metadata,
disabled byte restoration, enabled/disabled declaration delta, environment-false
and upstream caller gates, every local-quality use, rough ray-count preservation,
unchanged texture/ray/fog/sky source, all-preset native/small counts, transition
edges, and corrupted/missing/ambiguous source and output guards. CPU policy
fixtures are not GLSL, texture, ray-hit or GPU execution. No full ZIP, compiler,
world copy or client ran.

After root has the exclusive terminal runtime checkpoint:

```sh
python3 scripts/build_photon_ssr_far_reduced_probe.py --arm original
python3 scripts/build_photon_ssr_far_reduced_probe.py --arm disabled
python3 scripts/build_photon_ssr_far_reduced_probe.py --arm enabled
```

Outputs are ignored `build/Photon-SSRFarReducedTraceProbe-{arm}.zip`. The builder
pins every source form, rejects unsafe/duplicate members and nonignored output,
cannot overwrite B15, and fixes entry order/timestamps/permissions/compression.
Whole ZIP hashes must be recorded after building; none exist in this checkpoint.
Entry points are vanilla water/translucent reflection through
`program/gbuffers_all_translucent.fsh`, deferred surface reflection through
`program/d4_deferred_shading.fsh` and unchanged LOD-water consumers where present.
Root owns builds, driver compilation, all shader reloads and measurements.

First compare upstream original, reduced guard-off, the separate full-trace
control and reduced guard-on at the matched glint request and effective options.
Verify actual no-fallback rendering, near/mid restoration, band removal versus
full tracing, grazing sun, reflected terrain/thin geometry, screen edges,
hand/sky misses, slow translation and yaw reversal. Use advancing clocks/history
and complete native moving sequences; 2 FPS pose controls do not establish
10 FPS temporal acceptance. Reject band recurrence, reflection loss/pop or shimmer.

Only after useful quality evidence, compare matched affected-SSR and whole-render
GPU times in balanced serial controls without PNG readback, one client and exact
resolution/pacing/weather/terrain/options/artifacts with warmed histories.
Include original full quality and the full-trace repair at comparable visible
quality, not only the invalid cheap far-sky fallback. Requested lower counts do
not prove fewer physical operations, a frame improvement or the project gate.
Keep flat/regressing samples and CPU limits; no adoption, integration, support
promotion or default change follows from source tests or the earlier full-trace
pose success.


## Actual reduced tracing quality control

Root3811 terminalexit0/complete41/41/0skips, actualenabledprivateZIP/native
2976×1672/minimal/noDH/SSR-only/globaltrue/time12500/glintsweep atx500.
All41actualNONE/noncollision/PNGhashchecks passed. Rootframe10inspection
findsbroadbandremoved andsunreflectionvisible, approachingfull-trace control
inthispose. No fullmotion/near-midtransition/thinreflection/pixelparity or
physicalwork guarantee. Sequencea52402d4… andrawmanifest/log retained.

Freshfour-armcostcontrols sharecanonicalsavef8f3b866…a365, originalsource/
B15skip/fulltrace/reduced16vs8 sourcebudgets, onlySSRmoduleactive inpatched
arms, fixedcamera87.380/3.735/time12500. Native serial45s warm25s capture/
GPU1/16/minimal/noDH pilot launched; actualcostpending. Morefarworkvsskip and
fewerselectedintersection/refinementsvsfull do not alone establish fasterGPU.
No source/main/default/support promotion.


## Native four-arm cost pilot: no useful gain

Root71320 exit0/statuscomplete/all4accepted, fixedglintpose/time12500/native
2976×1672/minimal/noDH/45s warm25s capture/GPU1/16/fixedwetness0. Initial
worldcopiesequal, actualsource/options/class/window/controls/loadedartifacts
validated; allfallbackfalse. Original/full/reduced rendered sameboundedscene.

| Arm | FPS | GPU mean ms | GPU p95 ms | Thread mean ms |
| --- | ---: | ---: | ---: | ---: |
| Original |60.686|15.0436|15.8895|7.5632|
| B15 skip |61.172|15.0882|16.8025|7.5656|
| Reduced far trace |49.497|18.9037|21.2377|7.6709|
| Full far trace |50.883|18.3937|19.8995|7.5777|

Reduced/fulltrace regressed~25.66/22.27%GPU versusoriginal inthisone serial
pilot; skipwas~0.30%slowerGPUandhasobservedqualityfailure. Reduced didnotbeat
fulltrace here. Fewerselectedsource steps do notprove physicaloperations or
cheaperGPUtimeline. No source/JVM/driver/thermal/hit-semanticcause asserted;
retainallarms. Raw `dev/logs/photon-ssr-cost-pilot-r1` includes samples/PNGs/
exactinput/artifact maps. No release/default/performancepromotion.

An explicit pinned SSR-only same-JVM global control is beingprepared to compare
ON policy/OFF originaltracebudgets within each fixedarchive. It cannotcompare
acrossarchives causally or turnthisnegative pilotintoa15%gain. Broaderquality/
transition andmatchedrepeats remainunmet.
