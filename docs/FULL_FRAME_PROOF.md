# Full-frame investigation and conditional rendering proof

Measured 2026-10-04 on the pinned Minecraft 1.21.1 NeoForge/Iris/Photon/DH stack,
Apple M3 Max, OpenGL 4.1 Metal 91.7. This is a local prototype investigation.
Actual shader options, configuration and artifact hashes are embedded in raw
reports under ignored `run-workloads/` and `dev/logs/` directories.

## Why the earlier shader cuts helped little

The ordinary forest uses quarter-width/height cloud buffers by default. Terrain
and native draw submission remain costly even when distant shader samples are
removed. Full attribution recorded 111.9 FPS, frame wall time 8.93 ms and render
thread CPU time 7.83 ms. Vanilla opaque/cutout submission took 1.85 ms CPU. Narrow
hooks measured command construction at approximately 0.145 ms and native draw
submission at 1.44 ms. Parent and child scopes overlap; thread CPU includes
driver execution/spinning and does not establish a purely Java bottleneck.
An invasive command cache was not justified by the small construction cost.

Apple elapsed queries provide 32-bit counters but no useful timestamps, with
implementation-dependent granularity and tile attribution. GPU categories
rotate on different frames to avoid nested elapsed queries. Zero pass medians
do not mean free work or cache-update skipping: Photon redraws its sky map every
frame. GPU intervals are not busy-time utilization. Matched whole-render deltas,
FPS and separate CPU clocks are the primary evidence.

## Premium-cloud shader proof

The storm uses a supported premium setting: full-resolution cloud buffers,
sixteen times the default pixel area, with original ray counts. High and adaptive
share the same high settings, geometry, framebuffer, camera, weather and DH
reach. The lower preset retains quarter-resolution clouds and lower lighting
features. This workload is distinct from the ordinary default forest.

The first 512/4096-block cloud bands left most visible overhead clouds at full
cost. Photon cloud coordinates scale by 10; the 1200-unit shell is roughly 120
world blocks above sea level. At this camera the overhead entry is about 89
blocks. The revised candidate uses near/far/transition values 128/1024/64 and
Performance sampling budgets, preserving the overhead entry at full quality.
Original PCF, screen-space shadows and AO budgets remain protected. The cached
sky-light approximation is off.

Complete-JVM serial controls used 45 seconds warmup and 30 seconds capture,
frame-only GPU queries and disabled CPU layer profiling:

| Storm control | FPS | GPU render mean | Thread CPU/frame |
| --- | ---: | ---: | ---: |
| Original premium high | 62.98 | 15.124 ms | 7.430 ms |
| Original medium / quarter clouds | 112.93 | 8.197 ms | ~7.4 ms |
| High plus adaptive sampling | 72.43 | 13.113 ms | 7.518 ms |

Earlier matched high/adaptive runs measured 61.87/86.24 FPS and
15.369/10.865 ms. Observed shader reductions therefore ranged roughly 13–29%,
with substantial run variation. The best run is not guaranteed. Adding the
warmed detailed-terrain cap measured 90.93 FPS, 10.223 ms GPU and 5.907 ms CPU;
that combines renderer and shader quality changes.

The simple medium preset remains faster. Adaptive retains high cloud spatial
resolution and original lighting operations; whether that tradeoff is useful
requires visual inspection. This supports a conditional shader LOD opportunity
when active expensive effects dominate.

## Ordinary forest and terrain handoff

Forest controls retain DH's 128-chunk reach and original lighting. The final
candidate also uses protected-lighting shader LOD. The terrain policy changes
the effective detailed radius while preserving the stored request:

| Repeated forest control | FPS | GPU render mean | Thread CPU/frame |
| --- | ---: | ---: | ---: |
| Original high, detailed radius 24 | 124.30 | 7.482 ms | 7.283 ms |
| Original high plus warmed cap 16 | 138.02 | 6.582 ms | 5.912 ms |
| Original high plus coarser DH horizontal LOW | 90.41 | 10.389 ms | 8.862 ms |
| Final protected shader LOD plus cap 16 | 143.04 | 6.319 ms | 5.918 ms |

The final candidate reduced GPU render time by 15.54% and increased FPS about
15.1%. Earlier geometry runs measured 89.79 to 135.10 FPS and 10.433 to 6.737 ms;
that larger result is not the repeatable advertised figure. Coarser DH did not
help and is absent from the final candidate.

Original Photon TAAU at 0.75 scale separately measured 120.66 FPS / 7.764 ms.
Because forest baselines varied from 90 to 124 FPS, this single diagnostic does
not establish a robust advantage. It is an existing global resolution control.
The detailed 16 / DH 128 split is also an existing configurable tradeoff exposed
through a guarded, reversible policy; it does not introduce a mesh renderer.

The terrain policy is default-off and requires explicit warmed-scene coverage
confirmation. Runtime disable restored the current original radius of 24;
re-enable returned to 16 after warmup. The raw request stayed at 24. Automatic
cache coverage during travel remains unfinished. See [GEOMETRY_LOD.md](GEOMETRY_LOD.md).

## Observation controls and unsuccessful cases

One queried medium run measured 61 FPS with unusually high CPU time of 14.28 ms.
It is retained as anomalous. CPU-only and queried repeats measured 113.45 and
112.93 FPS, so query overhead is not an established cause of the anomaly.
Sparse queries are available for previews; CPU clocks still run every frame.
Query-bearing frames may remain intrusive, and GPU time is never fabricated
from inverse FPS.

One DH-low diagnostic briefly overlapped another startup; it was excluded and
repeated using the runner that waits for complete JVM termination. Ineffective
cloud bands, coarser DH and command-caching hypotheses are recorded above.

## Reproduction and handoff

Prepare the warmed source runtime as described in [WORKLOADS.md](WORKLOADS.md).
Close comparison clients before running serial benchmarks. Preserve existing
profiles or move them aside before fresh preparation:

```sh
python3 scripts/prepare_workloads.py --scene cloudstorm --cloud-upscaling 1 --preset PERFORMANCE
# Set adaptive cloud near/far/transition to 128.0/1024.0/64.0.
# Keep SHADERLOD_PRESERVE_LIGHTING=true and cached sky lighting false.
python3 scripts/run_proof_matrix.py cloud-lower cloud-high cloud-adaptive --tag matched-repeat
python3 scripts/prepare_workloads.py --scene forest
python3 scripts/prepare_diagnostics.py
python3 scripts/run_proof_matrix.py forest-high forest-geometry forest-dh-low --tag geometry-repeat
```

The cloud band edits apply to `shaderpacks/Photon-ShaderLOD.zip.txt` in the
adaptive profile. Diagnostic preparation copies the stopped forest high world;
all comparison saves derive from the same warmed source. Actual inputs are in
`*-measurement.json`. Summaries separate CPU and GPU and group repeated
invocations only within each frame; overlapping scopes cannot be added together.
Combined candidate copies use the same protected shader options and explicit
terrain settings documented in [GEOMETRY_LOD.md](GEOMETRY_LOD.md).

The final live pair is the ordinary forest: protected ShaderLOD with effective
cap 16 on the left, original Photon without ShaderLOD at radius 24 on the right,
both DH 128. FPS, GPU, CPU and actual radius are visible. The premium-cloud proof
is a separate workload. Concurrent windows share the GPU; serial captures above
provide performance evidence. Full desktop SSR, voxel lighting, POM and Ultra
conclusions remain unverified on suitable hardware.

Inspect the terrain seam and cloud sampling tradeoff before deciding whether
these conditional gains justify an opt-in MVP. More cameras, motion, automatic
coverage checks and hardware testing are needed before making the policy a default.
