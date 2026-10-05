# Four-phase alloff controls

The first two four-phase GPU-query controls completed on 2026-10-04. Their
within-session OFF/ON results are nearly flat or change sign. They do not show a
large reproducible live-enable penalty, and they do not establish a ShaderLOD
performance gain. Each session is one JVM with four correlated phases; these are
exploratory diagnostics, not independent-run statistics or ordinary-scene gate
acceptance.

Both use the inspected clear shoreline, 24 detailed chunks, no DH, original
reflection settings, all five effect modules false, protected lighting, analytic
water false, geometry/culling/list features false and shader wetness fixed at 0.
HUD is hidden. Initial warmup is followed by four OFF/ON/OFF/ON phases, each with
8 seconds settling and approximately 20 seconds capture. GPU queries sample one
frame in 16; CPU layer timing is disabled. Frames, animation clocks and raw
smoother state continue normally.

| Session | Archive SHA256 | Source intervention |
| --- | --- | --- |
| Existing alloff | `8fbaa117d87b4f729ea64ecce4d86390cdc56d03cd3b5a02ec2be9ffe2213235` | Preserved cloud-transition candidate with effect modules off |
| Lighting-tier alloff | `b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c` | Separate shader source change moving/protecting tier scaffolding |

The archive change is a separate source intervention between JVMs. It cannot be
collapsed into the within-session global-flag effect or attributed to one uniform
call. The ordinary-scene ShaderLOD gate remains unmet.

## Observed phases

Values below summarize raw serial captures. GPU values are actual elapsed
interval means, not inverse FPS or utilization. CPU wall/thread values describe
the run-tick scope and include its driver effects; scope overlaps must not be
summed.

| Session / phase | Enabled | FPS | GPU mean ms | CPU wall mean ms | Thread mean ms |
| --- | --- | ---: | ---: | ---: | ---: |
| Existing 1 | OFF | 153.19 | 5.865 | 6.521 | 5.633 |
| Existing 2 | ON | 150.44 | 6.141 | 6.642 | 5.881 |
| Existing 3 | OFF | 150.34 | 6.058 | 6.645 | 5.898 |
| Existing 4 | ON | 153.09 | 5.967 | 6.526 | 5.699 |
| Lighting-tier 1 | OFF | 154.11 | 5.827 | 6.483 | 5.576 |
| Lighting-tier 2 | ON | 154.07 | 5.929 | 6.484 | 5.598 |
| Lighting-tier 3 | OFF | 153.79 | 5.870 | 6.497 | 5.641 |
| Lighting-tier 4 | ON | 153.60 | 5.868 | 6.505 | 5.646 |

Adjacent ON-versus-OFF time reductions are positive when ON is faster:

| Session / pair | Frame-duration reduction | GPU-mean reduction | Thread-mean reduction |
| --- | ---: | ---: | ---: |
| Existing 1→2 | -1.83% | -4.70% | -4.40% |
| Existing 3→4 | +1.80% | +1.51% | +3.38% |
| Lighting-tier 1→2 | -0.02% | -1.76% | -0.40% |
| Lighting-tier 3→4 | -0.12% | +0.04% | -0.10% |

The opposing signs in the existing control and almost unchanged lighting-tier
frame durations support caution about earlier between-JVM enable comparisons.
They do not identify a thermal, cache, weather or driver cause. GPU query
interaction and temporal phase remain possible contributors. Reverse ordering,
query-free CPU-only and further sessions are needed before a causal conclusion.
No confidence interval or significance claim is produced from thousands of
correlated frame samples.

Both sessions' capture windows report focused/visible true, iconified false,
and no reported transitions. This is sampled window evidence, not continuous
OS activity, thermal or clock telemetry. Raw temporal and unknown fields remain
in the analyses. Config enabled observations are intended bridge values;
missing linked-program/uniform evidence is not invented.

## Analyzer and provenance checks

`scripts/analyze_inprocess_control.py` requires four complete correctly
alternating phases with unique UUIDs and nonoverlapping source-frame intervals,
matching frame counts/durations, fresh homogeneous instrument epochs, supported
requested timing backends, complete loaded semantic config/effective options,
fixed shader wetness and exact actual/preparation archive SHA256. It compares
all stable inputs after removing only expected global enablement and replacing
the raw ShaderLOD config-byte identity with complete loaded semantic fields.
A query-free capture is valid with an empty GPU stream and no GPU effect estimate;
CPU-only results are never renamed GPU wins.

The completed session method declares the implementation's reference-equality
world/pipeline guards. Raw object IDs were not exported, so the analyzer cannot
independently reconstruct them. It states that limit explicitly. Runtime
external jar hashes/loaded versions are checked across sessions; development
class directories were not individually hashed in each live report. The
coordinator preserved `dev/logs/inprocess-frozen-runtime-context.json` after
these captures, before rebuilding the later coherence integration. That is
qualified post-capture context, not retroactive proof of a per-phase bytecode
fingerprint. Future mixed compiled-mod runs must not be pooled with these by
assuming the same version string means the same code.

Passing `--profile` verifies every unique phase PNG filename and actual SHA256.
It also verifies that the stopped profile's Iris configuration bytes match the
capture's raw hash before parsing semantic properties. These two sessions had
different Iris raw hashes caused by timestamp comments; the verified property
maps match, including colorSpace, maxShadowRenderDistance, shader selection and
other controls. The raw hashes/maps remain in comparison metadata. Without
those verified maps the strict comparison rejects the hash difference. A real
property difference is rejected, not discarded as formatting.

Raw reports and images are unchanged. Generated summaries are ignored outputs:

```sh
python3 scripts/analyze_inprocess_control.py dev/logs/inprocess-old-alloff-gpu-measurement.json --profile run-workloads/cloud-shore-default/coastalwater/alloff --output dev/logs/inprocess-old-alloff-gpu-analysis.json
python3 scripts/analyze_inprocess_control.py dev/logs/inprocess-tier-alloff-gpu-measurement.json --profile run-workloads/lighting-tier-shore-default/coastalwater/alloff --output dev/logs/inprocess-tier-alloff-gpu-analysis.json
python3 scripts/analyze_inprocess_control.py dev/logs/inprocess-old-alloff-gpu-measurement.json dev/logs/inprocess-tier-alloff-gpu-measurement.json --profiles run-workloads/cloud-shore-default/coastalwater/alloff run-workloads/lighting-tier-shore-default/coastalwater/alloff --output dev/logs/inprocess-gpu-comparison.json
```

Seven small fixture tests pass, covering malformed phases, UUID/epoch/frame-range
errors, semantic options/config mutations, query-free/timer-free handling,
unknown window retention, image corruption and verified semantic Iris comparison.
No Java, build, Minecraft launch or GPU work was performed by the analyzer worker.
