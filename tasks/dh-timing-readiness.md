# Make Distant Horizons timing results trustworthy

## Goal
Timing results on the RTX 2080 Ti can select or retain a Distant Horizons (DH) optimization candidate. Gate 2 of the readiness plan agreed with Philip and reviewed with astra on 2026-11-13.

## Authorized
On 2026-11-13 Philip chose to build the four checks below before any optimization work. Bounded live runs on the 2080 Ti are allowed when its GPU is idle; it is shared with the wait-hint-kv project. Optimization candidates stay parked: no candidate may be selected, retained or rejected on timing until this task is done and Philip signs off.

## Done when
- **A. Same DH workload every run.**
  - A prepared, settled DH dataset is used, with generation disabled and frozen in world preparation; its identity is recorded at preparation.
  - Effective DH settings (configuration hash, distance, quality, mode and generation) are recorded, and comparisons and campaigns refuse when they differ.
  - An empirical readiness rule is applied, documented as not proving that loading has finished: a pilot-chosen conditioning period, stable frame rate and DH submission count over consecutive intervals, and a timeout.
  - A per-frame `DhApiBeforeBufferRenderEvent` count is validated as a submission witness, and its overhead is qualified.
  - A screenshot is taken per case.
- **B. DH no-change campaign.**
  - 1 Hz GPU telemetry is on by default for Windows sessions and campaigns.
  - Conditioning, reload and teardown budgets are set from a pilot and then frozen.
  - Campaign admission refuses unless the generated schedules lead with each arm in half the sessions.
  - The acceptance policy is frozen in advance: confidence, session count, cases, surfaces, and a median null-effect interval within ±0.25%.
- **C. GPU detection control.**
  - A development-only provider adds runtime-dependent, nontrivial GPU work to one full-screen pass, and its execution is verified.
  - It is piloted near a 1% frame-time effect and frozen, and a frozen campaign detects it in the expected direction.
  - No claim of magnitude accuracy is made.
- **D. GPU contamination.**
  - Admission refuses when foreign GPU compute processes are present.
  - Presence is sampled at 1 Hz throughout measured work and invalidates affected windows; unknown observations block promotion.
  - The check is validated once on the native-Windows and WSL arrangement.
- Results are recorded, and FINDINGS and the docs are updated.

## State
D and B.1/B.3 are built (2026-11-13), but not yet validated on the 2080 Ti.
- Telemetry samples now include GPU compute PIDs from NVML.
- Each comparison window gets a `gpu_contamination` verdict, and qualification refuses anything but `clean`.
- `session-open --telemetry` refuses while compute processes are present; the Windows session script always passes `--telemetry`, as campaigns already did.
- Campaign definitions must alternate the leading arm, and the tracked 2080 Ti campaigns use seeds 0 and 1.
- Not yet validated: whether native-Windows NVML lists WSL CUDA processes, so check that first.

The DH 3.3.3 public API has no pending-work signal (checked against the pinned jar). The 2080 Ti telemetry from the earlier campaigns was deleted during extraction.

## Next
Validate D on the 2080 Ti with a harmless WSL CUDA process. Then build A (DH settings and dataset identity, empirical readiness, buffer-render count witness, screenshots) and C (GPU detection provider), then run the pilots and frozen campaigns.
