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
Built, piloted and committed (2026-11-13). The frozen campaigns are running.
- **D is validated live.**
  - Native-Windows NVML lists desktop processes, so these form each session's ambient baseline.
  - WSL CUDA work appears as process 4, and a 12 s WSL CUDA job flagged exactly the overlapping window.
- **A: frozen dataset.** The settled DH dataset is frozen on `run-benchmark-dh`, identity 9a5a026b…. A generation session grew it from 62.7 to 72.0 MB in 90 s, after which it held still for 3.5 min. Sessions restore it; one restored a rewritten database.
- **A: witness and its cost.** The witness counted a constant 330 draws per frame at the fixed pose. Its cost is not distinguishable from session noise: 82.8 against 82.7 FPS with it on and off.
- **B pilot: thermal decline.** At 4K with DH, frame rate fell from 84 to 82 FPS over about 3 min while the GPU warmed from 33 to 62 °C and its clock fell from 1950 to 1875 MHz, then flattened.
- **Frozen readiness rule.** Conditioning 150 s, then 15 intervals within 1.5% frame rate and 1% DH draws per frame, with a 180 s timeout.
- **C pilot.** `gpu-load-probe` with 32 iterations raised the final-pass GPU time from 0.133 to 0.298 ms and slowed frames by 1.36–1.54%. It is frozen at 22 iterations, aiming at about 1%.
- **Fixed during piloting.** Sessions close after 120 s without commands, so conditioning now polls status. With the witness off, windows now read unobserved instead of zero.
- **Acceptance.** Fixed in `benchmarks/README.md` at a8fa239, before the first run.

## Next
1. Finish `dh-null-01` and `dh-detect-01` on the 2080 Ti.
2. Judge them against the frozen acceptance, record the result and FINDINGS, and report to Philip.
3. If both pass, Gate 2 is met for this workload; E (more views and quality review) still precedes any substantial implementation.
