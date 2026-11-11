# Benchmark design

## Goal
Provide a usable method and workflow for developing, testing and evaluating optimizations, with an explicit definition of what each result establishes.

## Authorized
Prepare the discussion and identify missing capabilities. Agree measurement and acceptance decisions with Philip before implementing the design or running new experiments. Routine development-direction repairs remain authorized under development readiness.

## Done when
The agreed design and required tools support an end-to-end evaluation on the chosen machine, including controls, measurement validity, rendering correctness, restoration and understandable results. An accepted optimization is not required for readiness.

## State
This is one of three readiness blockers, alongside agent ops and machine choice. Existing pieces include a bounded host runner, functional probe and diagnostic CPU/GPU streams. The CPU stream measures elapsed render-callback duration, not full frame-start intervals or thread CPU consumption. No benchmark methodology or performance qualification exists yet.

Scope includes supported workloads and target coverage; reproducible scenes and native settings; functional and lifecycle qualification; rendered DH evidence; frame pacing and GPU measurements; null calibration and observer overhead; warmup, repetitions, order, statistics and stopping rules; image and motion correctness; per-pass profiling; and readable comparison reports. Historical results are inputs to design, not new-runtime qualification. The old four-pack/two-machine retention rule and "generally positive" need an explicit current interpretation.

## Next
Agree the intended decisions, target coverage, worthwhile effect size and correctness requirements. Design the method around the selected machine and the agreed evidence presentation. Reuse relevant historical inputs and lessons without copying obsolete launch procedures. Keep invalid, failed and inconclusive outcomes visible.
