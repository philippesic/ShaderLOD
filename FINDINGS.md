# Findings

Current answers within their stated scope. Read the linked result before applying a conclusion to new work. Historical results are not qualification of the current runtime. No optimization is accepted.

| ID | Conclusion | Result |
| --- | --- | --- |
| F1 | None of the sixteen historical candidates met the original keep bar; implementations remain in Git history | [Result](results/historical-optimization-retention/result.md) |
| F2 | Unchanged-shader null runs varied from −9.70% to +10.24%, so the calibration failed; a ~3% effect cannot be detected with that setup | [Result](results/asahi-null-calibration/result.md) |
| F3 | Bounded tasks, uploads, residency, hooks and restoration worked in original-pack runs | [Result](results/dh-observation-hooks/result.md) |
| F4 | GPU timing covered only 39 of 162 frame intervals, with unknown health, 43 unresolved queries and one imbalanced scope; it is not a cost breakdown | [Result](results/incomplete-gpu-cost-capture/result.md) |
| F5 | Reimagined and Unbound originals failed to link with DH enabled: "Too many fragment shader texture samplers" | [Result](results/complementary-dh-link-failures/result.md) |
| F6 | Turning DH generation off was restored correctly, but leftover loading kept the scene from reaching a steady state | [Result](results/dh-loading-after-generation-disabled/result.md) |
| F7 | The DH depth handoff passed CPU tests only; live use, conversion and shadow paths were never exercised | [Result](results/dh-depth-handoff-validation/result.md) |
| F8 | Before the sampler index correction, the original Reimagined pack failed native linking with DH, and ran out of texture units without DH; settings restoration still succeeded | [Result](results/asahi-sampler-index-failure/result.md) |
| F9 | The same composite1 sampler failure happens with ShaderLOD absent, so ShaderLOD does not cause it | [Result](results/asahi-dh-native-control/result.md) |
| F10 | Without DH, the shipping JAR passes native selection, real frames, settings-screen Cancel/Off, the identity provider and failed-apply restoration. With DH, the original pack still exceeds the fragment sampler limit | [Result](results/asahi-native-selection-restoration/result.md) |

## Candidate investigations

| ID | Conclusion | Result |
| --- | --- | --- |
| F11 | G01: Avoid framebuffer copies / identity transfers; no cross-pack/cross-machine qualification. | [Result](results/candidate-g01/result.md) |
| F12 | G02: Omit unused mip generations / passes; no cross-pack/cross-machine qualification. | [Result](results/candidate-g02/result.md) |
| F13 | G03: Reject transparent cutout fragments earlier; no cross-pack/cross-machine qualification. | [Result](results/candidate-g03/result.md) |
| F14 | G04: Skip noncontributing translucent lighting / cloud work; no cross-pack/cross-machine qualification. | [Result](results/candidate-g04/result.md) |
| F15 | G05: Reflection traversal / depth-query reuse; no cross-pack/cross-machine qualification. | [Result](results/candidate-g05/result.md) |
| F16 | G06: Reduce / avoid AO work; no cross-pack/cross-machine qualification. | [Result](results/candidate-g06/result.md) |
| F17 | G07: Reduce repeated filter/depth queries; no cross-pack/cross-machine qualification. | [Result](results/candidate-g07/result.md) |
| F18 | G08: Cloud occupancy / lighting reuse; no cross-pack/cross-machine qualification. | [Result](results/candidate-g08/result.md) |
| F19 | G09: POM height-query reuse / empty-prefix skipping; no cross-pack/cross-machine qualification. | [Result](results/candidate-g09/result.md) |
| F20 | G10: Reuse identical filter taps; no cross-pack/cross-machine qualification. | [Result](results/candidate-g10/result.md) |
| F21 | C01: Additional terrain occlusion queries; no cross-pack/cross-machine qualification. | [Result](results/candidate-c01/result.md) |
| F22 | C02: Hardware coarse shading; no cross-pack/cross-machine qualification. | [Result](results/candidate-c02/result.md) |
| F23 | N01: CPU command compaction / admission and scalar caching; no cross-pack/cross-machine qualification. | [Result](results/candidate-n01/result.md) |
| F24 | N02: Eliminate unused attachments and target pairs; no cross-pack/cross-machine qualification. | [Result](results/candidate-n02/result.md) |
| F25 | N03: Eliminate terminal copies with dead lifetime; no cross-pack/cross-machine qualification. | [Result](results/candidate-n03/result.md) |
| F26 | N04: Guard overwritten AO or exact zero work; no cross-pack/cross-machine qualification. | [Result](results/candidate-n04/result.md) |

## Measurement, tooling and compatibility

| ID | Conclusion | Result |
| --- | --- | --- |
| F27 | Combining candidates did not establish a generally useful combination. | [Result](results/combination-effects/result.md) |
| F28 | Early candidate searches supplied scoped leads, not product acceptance. | [Result](results/early-candidate-search/result.md) |
| F29 | Repeats weakened several early apparent gains. | [Result](results/candidate-repeatability/result.md) |
| F30 | N01–N04 remained private research with incomplete qualification. | [Result](results/new-candidate-search/result.md) |
| F31 | Bliss qualification stopped at calibration and memory failures. | [Result](results/bliss-calibration-and-oom/result.md) |
| F32 | Memory and temperature observations did not establish the cause of timing drift. | [Result](results/memory-causality/result.md) |
| F33 | Reducing redundant reloads improved the workflow without proving shader speedups. | [Result](results/workflow-repair/result.md) |
| F34 | The RTX 2080 Ti screen exposed admission gaps and limited single-run evidence. | [Result](results/rtx2080ti-screen/result.md) |
| F35 | Two RTX 2080 Ti baseline blocks established only bounded functional evidence. | [Result](results/rtx2080ti-baseline/result.md) |
| F36 | Motion review found no gross defect but did not establish visual equivalence. | [Result](results/rtx5080-rendering-review/result.md) |
| F37 | Additional work observation did not explain away the RTX regressions. | [Result](results/rtx5080-regression-diagnosis/result.md) |
| F38 | DH profiling gained live observations but left important boundaries unqualified. | [Result](results/dh-profiling-coverage/result.md) |
| F39 | Historical benchmark designs and early control comparisons expose measurement limits; optional-stack and in-process controls did not establish consistent gains. | [Result](results/profiling-and-benchmark-design-lessons/result.md) |
| F40 | Earlier viewport, sampling-phase and runtime-readback mistakes provide concrete test-design lessons; old GL 4.1 sampler-zero/filter claims remain unproven | [Result](results/early-rendering-contract-lessons/result.md) |
| F41 | Historical reports, compact measurements and source recovery now live here; the old checkout and bulk snapshot are no longer dependencies. | [Result](results/historical-evidence-extraction/result.md) |

## Legacy MacBook Pro controls

| ID | Conclusion | Result |
| --- | --- | --- |
| F42 | The earlier program defined 61 controls across nine families; implementation coverage is not 549 performance wins. | [Result](results/legacy-mac-61-controls/result.md) |
| F43 | Combined native-shader CPU profiles supplied scoped positive Mac evidence, with drift and family-specific limits. | [Result](results/legacy-mac-native-cpu-profiles/result.md) |
| F44 | Fifteen later Mac backlog items closed with mixed/parked outcomes and no new generated-pack winner. | [Result](results/legacy-mac-backlog-outcomes/result.md) |

## Benchmark workflows

| ID | Conclusion | Result |
| --- | --- | --- |
| F45 | Reused-session validation/comparisons, profiling and frozen campaigns passed scoped RTX 2080 Ti/Asahi controls, including sustained captures and recovery. This qualifies the workflow, not an optimization or universal small-effect sensitivity. | [Result](results/benchmark-workflows/result.md) |

## Additional historical lessons and extraction

| ID | Conclusion | Result |
| --- | --- | --- |
| F46 | Older BSL approximate-POM and reflected-cloud trials established no useful original-relative gain in their tested wall view. | [Result](results/earlier-bsl-approximation-trials/result.md) |
| F47 | Earlier loader probes reached native rendering but did not establish stable speed or complete-scene quality; they do not expand current support. | [Result](results/earlier-loader-measurement-limits/result.md) |
