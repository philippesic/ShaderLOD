# Benchmark design

## Goal
Provide a usable method and workflow for developing, testing and evaluating optimizations, with an explicit definition of what each result establishes.

## Authorized
Prepare the discussion and identify missing capabilities. Agree measurement and acceptance decisions with Philip before implementing the design or running new experiments. Routine development-direction repairs remain authorized within the assigned development scope.

## Done when
The agreed design and required tools support an end-to-end evaluation on the chosen machine, including controls, measurement validity, rendering correctness, restoration and understandable results. An accepted optimization is not required to complete this method.

## State
Owner: the active benchmark-design conversation, working on main in /home/philip/ShaderLOD-next. Design is underway; implementation and qualification depend on agent ops and machine choice. Existing pieces include a bounded host runner, functional probe and diagnostic CPU/GPU streams. The CPU stream measures elapsed render-callback duration, not full frame-start intervals or thread CPU consumption. No benchmark methodology or performance qualification exists yet.

Scope includes supported workloads and target coverage; reproducible scenes and native settings; functional and lifecycle qualification; rendered DH evidence; frame pacing and GPU measurements; null calibration and observer overhead; warmup, repetitions, order, statistics and stopping rules; image and motion correctness; per-pass profiling; and readable comparison reports. Historical results are inputs to design, not new-runtime qualification. The old four-pack/two-machine retention rule and "generally positive" need an explicit current interpretation.

Initial inspection found three current ShaderLOD-Smoke profiles and preserved authored/PBR world pools in the external historical snapshot. Historical profiles retain exact Reimagined, Unbound, BSL and MakeUp options, with separate PBR opportunity settings. Their existence is verified; scene suitability, source ancestry and runtime readiness still need assessment before admission. These previously exercised assets are development inputs, not automatically fresh confirmation cases.

Proposed measurement roles, not yet agreed: diagnose active work and likely bottlenecks; cheaply screen a candidate against unchanged input; confirm useful gains and regressions across its declared scope. Use the existing runner and result lifecycle. Instrumentation, unchanged-versus-unchanged controls, rendered quality and feature-activation checks must prevent a plausible timing result from qualifying the wrong work. Do not infer independent evidence from correlated frames or phases, or infer equivalence from an inconclusive comparison.

The first pending user decision is whether retention may be scoped to explicit eligible shader packs/conditions with regression checks, or requires broad benefit across the supported set. Scoped retention is proposed, not adopted. Baseline definitions, workload breadth, worthwhile effect size, correctness criteria and runtime budgets remain undecided. No new benchmark or live experiment has run for this design.

## Next
Resolve the scoped-versus-broad retention question, then agree measurement roles, target coverage, worthwhile effect size and correctness requirements. Design the method around the selected machine and the agreed evidence presentation. Reuse relevant historical inputs and lessons without copying obsolete launch procedures. Keep invalid, failed and inconclusive outcomes visible.
