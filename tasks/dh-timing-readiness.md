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
D, B.1 and B.3 are built, and D is validated on the 2080 Ti (2026-11-13).
- Native-Windows NVML lists desktop processes such as dwm and explorer, so these form each session's ambient baseline.
- WSL CUDA work appears as process 4. A 12 s WSL CUDA job flagged exactly the overlapping window as `foreign`; the other windows read `clean`.
- Telemetry recorded the GPU warming from 30 to 53 °C during a session.

A is built but not yet exercised live:
- the `prepare-dh-dataset` freeze, with restore on each DH session;
- `dh_settings` in status and in the configuration guard;
- the generator plan overridden to `DISABLED` during sessions, unless `--dh-generation` is given;
- the `DhApiBeforeBufferRenderEvent` counter and per-capture `dh_submission`;
- the comparison `stability` rule with case screenshots;
- qualification checks for DH runs.

C is not built. The DH 3.3.3 public API has no pending-work signal.

## Next
1. Build the settled dataset on the 2080 Ti (`-DhGeneration` session, then `prepare-dh-dataset`).
2. Pilot DH readiness to choose conditioning and tolerances, and measure the witness overhead with `-WithoutDhWitness`.
3. Build C.
4. Write the frozen DH no-change and detection campaigns, and run them.
