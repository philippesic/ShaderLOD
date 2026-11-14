# Profiling

Use [benchmark sessions](benchmarks.md) to reuse a ready client and world. Frame capture is the outcome measure; sampled stage instrumentation is diagnostic and can change the workload. Keep minimally instrumented comparisons authoritative.

```
python3 -m shaderlod_host profile --spec benchmarks/profiles/frame.json --plan
python3 -m shaderlod_host profile --spec benchmarks/profiles/frame.json --session .local/artifacts/session --output-root .local/artifacts/fresh-profile
python3 -m shaderlod_host profile --spec benchmarks/profiles/stages.json --session .local/artifacts/session --output-root .local/artifacts/fresh-stage-profile
python3 -m shaderlod_host verify --result .local/artifacts/fresh-profile/receipt.json
python3 -m shaderlod_host report --result .local/artifacts/fresh-profile/receipt.json --output-root .local/artifacts/fresh-render
python3 -m shaderlod_host report --frames .local/artifacts/frames.jsonl --output-root .local/artifacts/fresh-import
```

Open `report.html` locally without a server or network. It offers frame-time/FPS and optional CPU/GPU stage tracks, time selection, panning, point tooltips, route markers, a selected-interval stage table and CSV export. `summary.json`, `observation.json` and exact `frames.jsonl` support agents without HTML scraping. Raw spikes and gaps remain visible. Orange markers identify diagnostic submissions; periodic timing spikes may be observer effects, not shader behavior. An imported stream has no newly established live provenance. `report --result` verifies preserved evidence and writes a separate current rendering with source/renderer identities. Original reports and raw records remain unchanged. Comparison derivatives link back to raw source assets; campaign derivatives use the history view. Complete frame streams show zero-throughput bins during a stall; missing observations never become invented zero timings. Receipt verification checks file identity and frame reduction, not qualification by itself.

The collector records consecutive application frame entries in a finite 200,000-frame buffer and publishes immutable one-second chunks asynchronously. Generation changes, lost focus/context, overflow or incomplete spool invalidate the outcome. A crash can preserve partial chunks but cannot produce a completed-window verdict. Typical captures are 10–20 seconds; qualified scope permits bounded five-minute sustained windows. An excessive frame rate can exhaust the buffer; choose a fitting declared window, never silently downsample acceptance data.

CPU stage intervals are render-thread wall time between public NeoForge boundaries. They are not thread CPU consumption. GPU timestamp intervals are asynchronous and sampled every tenth render callback when supported; pending/lost/invalid queries, overflow and counter bits are reported. Unavailable GPU measurements are not zero. GPU elapsed includes scheduling and possible idle time; nanosecond units do not imply nanosecond precision. Public scene/shadow boundaries include shared engine and shader work. On tile-based GPUs, timing placement can further limit interpretation. Do not turn these spans into exclusive occupancy, sum overlapping scopes, or claim GPU savings from CPU timestamps.

## Pack-specific meaning and extensions

The base profiler works on an unmapped original pack. Exact archive/dimension/option mappings in `benchmarks/mappings/` add semantic membership to observed intervals. Reimagined's initial mapping distinguishes combined shadow-scene and main-scene work using actual Iris shadow-pass state. It does not invent separate AO, fog or reflection timings.

The stage boundaries are the game's level-render stages, and a shader pack's post-processing does not have its own. Iris runs deferred passes just before translucent terrain and its composite and final passes just before the after-level stage. Those two stage intervals therefore mix geometry with the whole post-processing chain; in retained captures they held about 52% of measured GPU time for Reimagined on RTX 2080 Ti, about 61% for Reimagined on Asahi and about 65% for MakeUp on Asahi.

## Render groups: which pass is expensive

A diagnostic capture also records `pass_summary`. Iris names every piece of its own work as a render group: each world phase (`Terrain solid`, `Shadow Entities`, `Sky`), each pack program in a full-screen stage (`deferred/deferred1`, `composite/composite6`), `final`, `shadowcomp` and `Clear textures`. The development mod observes those group events and attributes the time between consecutive events to the innermost open group, so the groups do not overlap and together cover the frame. Work outside any group is `ungrouped`. The names come from Iris and the pack; no mapping file is needed, and an unknown pack works the same way.

Each group reports GPU and CPU milliseconds per sampled frame and its share of GPU time. GPU values use only frames in which every interval's query completed, and the summary states how many that was. Read them with three cautions. Sampled frames carry extra queries, so their total exceeds an ordinary frame, noticeably so on tile-based GPUs. A group whose GPU time equals its CPU time is waiting on the CPU to submit work, not shading. A share says where time went in this scene; it does not promise that removing the pass saves that much.

Distant Horizons names its own render stages for Minecraft's profiler: `LOD GL setup`, `LOD build render list`, `LOD Clear`, `LOD Opaque`, `Custom Objects`, `LOD SSAO`, `LOD Transparent`, `LOD Fog`, `Fade Far Clip Fade`, `Apply to MC` and `LOD cleanup`. With DH installed, the development mod also reports each of these as a render group prefixed `DH `, nested under whichever Iris group was open, for example `Terrain solid/DH-RenderLevel/DH LOD Opaque`. Only DH's top two levels become groups; its per-object sections inside `Custom Objects` are folded into that stage, because hundreds of intervals per frame exhaust the GPU query pool. DH work that does not pass through its profiler sections stays in the surrounding group. MakeUp exercises the generic unmapped path. Original source, observed boundary and causal attribution remain distinct facts.

To add a mapping, inspect the exact original archive and relevant programs, declare the supported options/dimension and label only observed boundaries. Exercise supported and unknown variants; an unknown version remains unmapped. A mapping revision gets a new identity and never rewrites old reports. If an effect shares a program/span, label the combination. A more precise effect split requires a new verified instrumentation boundary, not dividing a combined duration by source size.

To add a collector, keep OpenGL query ownership under `dev/measurement`, preserve explicit missingness and source/generation joins, and qualify its observer effects on both reference and candidate when the mechanism changes collection frequency or scheduling. The module admission point remains the maintained profile/comparison specification. Do not add a separate process launcher or a universal shader parser. Deep JFR/counter investigations are focused extensions, not permanent overhead on the one-minute loop.

## Quality captures

`benchmarks/comparisons/*-quality.json` are separate short motion/image workflows. GPU readback occurs on the render thread; PNG encoding uses two encoding workers with a four-image queue (plus the two active images). Queue pressure drops an image explicitly; it never paces the route. The route never waits for encoding capacity. `quality.json` records actual times, poses, cadence, maximum gap, queue failures and dropped images; `quality.html` provides playback. These are untimed quality observations, not performance windows. Insufficient cadence or progression is explicit and prevents using that sequence as adequate motion evidence.

Unchanged-image variability calibrates false alarms; a known visible defect tests only the sensitivity exercised. Inspect onset/recovery and relevant regions. Brief shimmer between captured frames can remain unseen. Human review, viewing conditions and mechanism-specific sequences define perceptual scope; checksums and non-uniform images cannot approve quality.
