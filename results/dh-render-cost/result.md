# Distant Horizons terrain is the largest single GPU cost in a Reimagined frame at 4K

At 3840×2160 on the RTX 2080 Ti, Distant Horizons (DH) added 3.2 ms of GPU time to an 8.7 ms frame. Most of that went to one draw, `LOD Opaque`, which at 2.37 ms was the most expensive group in the frame, ahead of the pack's main lighting pass `deferred1` (1.74 ms). In a two-point fit across resolutions, about two thirds of that draw scaled with pixel count; this split is a hypothesis. DH also took about 2.3 ms of render-thread CPU per frame at either resolution. These numbers say where DH-specific work would act; they do not show that any of it can be removed.

| Group, ms per sampled frame | GPU 4K with DH | GPU 4K without DH | GPU 1440p with DH | CPU 4K with DH |
| --- | --- | --- | --- | --- |
| Whole frame | 11.88 | 8.70 | 6.65 | — |
| `DH LOD Opaque` | 2.37 | — | 1.48 | 0.72 |
| `DH LOD Transparent` | 0.44 | — | 0.26 | 0.77 |
| Other DH stages | 0.04 | — | 0.04 | 0.77 |
| `deferred/deferred1` | 1.74 | 1.69 | 0.80 | 0.09 |
| `composite/composite1` | 1.35 | 1.12 | 0.61 | 0.04 |
| `Terrain solid` without DH stages | 1.02 | 1.05 | 0.54 | 0.22 |

Values are from 82–145 sampled frames per profile, all with complete GPU queries. The two 4K DH profiles, run back to back, agreed within 0.05 ms in every group listed. Throughput in the matching comparison windows was 83–84 FPS with DH and 114–116 FPS without.

## What the numbers suggest

These are observations from one scene; the causes are supported hypotheses, not established.

- **DH opaque shading.** A straight line through the two resolutions gives about 0.19 ms per megapixel plus about 0.8 ms that did not scale in these observations. At 4K that suggests about 1.6 ms of per-pixel work on distant terrain. Two points cannot separate per-pixel work from resolution-dependent scheduling or instrumentation effects, so treat this split as a hypothesis for choosing experiments. Per-pixel work on distant terrain is the kind of work ShaderLOD exists to reduce. The pack's DH terrain program, how many DH fragments are later hidden by nearer terrain, and how much detail survives at that distance are the questions to answer next.
- **DH geometry.** The 0.8 ms of `LOD Opaque` that did not scale may be geometry work, which DH's own quality and distance settings control rather than the pack; this is not established.
- **Pack passes over DH.** `composite1` cost 0.23 ms more with DH at 4K, about 20%. Some full-screen pass work grows with DH content; which pass effect does so is not identified.
- **CPU submission.** DH's render-thread time was about 2.3 ms regardless of resolution, with `LOD Transparent` taking more CPU (0.77 ms) than GPU (0.44 ms). At 4K the frame is GPU-bound, so this time did not limit throughput; at lower resolutions or on a faster GPU it will matter more.
- **Shadows.** DH sections inside the shadow pass took under 0.01 ms, so DH terrain is not a shadow-map cost in this configuration.

## What changed

The development profiler now reports DH's own render stages as render groups. DH labels its stages for Minecraft's profiler (`LOD Opaque`, `LOD Transparent`, `LOD SSAO`, `LOD Fog`, `Apply to MC` and others); an optional development-only mixin configuration reports them under whichever Iris group is open, for example `Terrain solid/DH-RenderLevel/DH LOD Opaque`. Only DH's top two levels become groups. A first version reported every section and failed: DH's `Custom Objects` stage opens about 370 sections per frame, which exhausted the GPU query pool, lost 27,618 queries and left 1 complete frame of 59. It also inflated that stage's CPU time to about 3 ms, against 0.26 ms once deeper sections were folded in. [Profiling guide](../../docs/profiling.md#render-groups-which-pass-is-expensive).

## Conditions and limitations

Evidence level: live-functional with bounded diagnostic measurement on one machine; not live-performance-qualified. RTX 2080 Ti under native Windows on a virtual display, original Complementary Reimagined r5.9.3 with its original options, DH 3.3.3 with the canonical profile's configuration (distance 256, mode DEFAULT), render distance 8, one fixed view over the coast in the smoke world at noon in clear weather. How much DH terrain was on screen was not measured, so the DH costs belong to this view and these settings. Sampled frames carry extra queries; the 4K DH stage profiles ran at 82.6–82.7 FPS against 83.2–84.3 FPS in plain windows. Group shares are where time went, not what removing a pass would save. A first DH comparison exceeded its 120-second budget and timed out; its late capture was reconciled before the session continued, and both quick 2080 Ti comparisons now allow 180 seconds.

## Decision

Retain the DH stage groups as the standard way to profile DH. The candidate areas for DH-specific work, in order of measured GPU size at 4K: per-pixel shading in `LOD Opaque`, DH geometry volume, pack passes that grow with DH content, and render-thread submission. Optimization remains parked until Philip approves readiness. Before choosing among them, profile more views and DH distances, and measure how many DH fragments are hidden by nearer terrain.

## Four views from the same spot, 2026-11-13

Later the same day, four views were profiled from the spawn point at 3840×2160, with DH on the frozen dataset and generation disabled. The screenshots changed the picture of the main benchmark scene. `coast-pan` looks mostly at a large flat sand platform the player stands on; real terrain appears only in a band at the horizon. The `inland` view shows ordinary mixed near and far terrain: hills, trees and a lake.

| View (yaw, pitch) | FPS | GPU ms per frame | DH GPU ms | DH share | DH draws per frame |
| --- | --- | --- | --- | --- | --- |
| coast (−35°, 30°) | 85.1 | 11.59 | 2.75 | 24% | 344 |
| horizon (−35°, 0°) | 91.4 | 10.78 | 1.90 | 18% | 292 |
| inland (145°, 15°) | 75.7 | 13.03 | 3.96 | 30% | 330 |
| down (55°, 70°) | 99.1 | 9.92 | 1.93 | 19% | 156 |

Looking almost straight down at the platform, no DH terrain is visible, yet DH still took 1.93 ms of GPU time and submitted 156 buffers per frame. This suggests that much of DH's cost here goes into terrain that never reaches the screen, but it is one observation: whether that work is occluded fragments, vertex work or something else is not measured. Short paired windows in all four views agreed within about 0.1%, and every profile's GPU attribution was usable.

The tracked comparisons gain `inland-pan` (mixed near and far terrain, the highest DH share) and `ground-look` (no visible DH terrain) in `benchmarks/comparisons/rtx2080ti-views.json`. Each of the three views has a fixed-pose profile in `benchmarks/profiles/rtx2080ti-*-stages.json`. [Survey record](view-survey.json) sha256:f290b28a3d7f124643a91ed6b253624387ee3cada577d4b76bdec321e2ffdd4f. The record holds the screenshot identities; the images were inspected and not retained.

## Evidence and executed source

[Compact measurements](measurements.json) sha256:f43d968d0b6961855d6a000257611fabe7295fe02eedb9244a563a7dfdb8bafc

The record holds every group of the five profiles with query health, frame-stream hashes, the throughput windows of the four comparisons in the same sessions, and the failed unbounded attempt. Profiles ran at commit 30c9306; the unbounded attempt at 5dfdca4. Raw session directories were deleted after extraction.
