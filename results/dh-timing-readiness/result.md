# Distant Horizons timing on the RTX 2080 Ti resolves a 0.1% no-change bound and detects a 0.8% GPU saving

The readiness plan agreed with Philip and reviewed with astra required four checks before timing could select or retain a DH optimization. All four were built, and both frozen DH campaigns met the acceptance fixed before they ran:

1. a fixed DH workload;
2. a DH no-change campaign;
3. a GPU detection control;
4. screening for foreign GPU work.

| Campaign, six fresh sessions × four blocks at 3840×2160 with DH | Session effects | 95% interval for the median | Frozen acceptance | Outcome |
| --- | --- | --- | --- | --- |
| No change: original against the identity provider | −0.014% to +0.060% | −0.014% to +0.060% | within ±0.25% | Met |
| Detection: removing a development load from the final pass | +0.73% to +0.85% | +0.73% to +0.85% | lower bound above zero | Met |

In all twelve sessions:
- readiness reached `stable`;
- every window was `clean` of foreign GPU work;
- each session restored the same frozen DH dataset;
- DH submitted 343.5 buffer draws per frame on the route, within 0.1% across sessions;
- the assessments listed no problems.

Both recommendations remain `do_not_promote` only because these controls have no quality review, as declared. The pilot projected the detection effect at about 1%; the campaign measured 0.81%. That is consistent with the load being scaled down from 32 to 22 iterations after a pilot whose effect was 1.36–1.54%.

## What changed

- **Fixed DH workload.**
  - `prepare-dh-dataset` freezes a slot's DH databases with a manifest identity, and each DH session restores them before launch.
  - Sessions override DH's generator plan to `DISABLED` through DH's API.
  - Status and captures carry `dh_settings`, which the comparison configuration guard checks.
- **Submission witness.** The session counts `DhApiBeforeBufferRenderEvent` callbacks, reported per frame as `dh_submission`. Witness-on and witness-off pilot sessions ran at 82.8 and 82.7 FPS, so its cost is not distinguishable from session noise.
- **Readiness.** A comparison `stability` rule:
  1. screenshots each case;
  2. holds a 150 s conditioning period, polling status so the client does not close itself after 120 s idle;
  3. then needs 15 consecutive intervals within 1.5% frame rate and 1% DH draws per frame, within 180 s.

  DH 3.3.3 exposes no signal that its loading has finished, so this bounds leftover loading without proving it is absent.
- **Contamination.** 1 Hz telemetry records the processes NVML reports on the GPU, against an ambient baseline taken when the session starts. A window is clean, foreign or unobserved. WSL work appears on native Windows as process 4; a 12 s WSL CUDA job flagged exactly the window it overlapped.
- **Balanced starts.** Campaign definitions must lead with each arm in half their sessions.
- **Qualification refusals.** `qualify --policy` refuses:
  - unbalanced, foreign or unobserved windows;
  - an unprepared dataset or unstable readiness;
  - uncounted DH submission;
  - DH settings or datasets that differ between sessions.

[Benchmark guide](../../docs/benchmarks.md#distant-horizons-workload).

## Conditions and limitations

Evidence level: live-functional with bounded live measurement controls; not live-performance-qualified for any optimization. RTX 2080 Ti under native Windows on a virtual display, original Complementary Reimagined r5.9.3 with its original options, DH 3.3.3, and the `coast-pan` route in the smoke world at render distance 8, noon, clear. The DH dataset was built by one generation session that stopped growing after 90 s; whether DH finished or paused generating is not observable.

The session decline found earlier is thermal and power behaviour. In the pilot, the GPU warmed from 33 to 62 °C while its clock fell from 1950 to 1875 MHz and frame rate from 84 to 82 FPS over about three minutes. Campaign telemetry also saw the software power-cap reason at peaks of about 310 W. The conditioning period absorbs most of this, and the balanced blocks absorb the rest.

What these controls establish:
- the no-change result bounds the median null effect for this scene, not every session's stability;
- the detection result shows that a final-pass load of this size is detected, not that every 1% optimization will be, nor how accurately its magnitude is measured;
- one-second process sampling can miss a shorter foreign job.

The pilot sessions' raw outputs were deleted before this record was written; their figures come from the run logs and the task record.

## Decision

Retain. Timing results on this machine and workload may now select or retain a DH candidate, within these limits. Before a substantial implementation, add contrasting views (mixed near and far terrain, and a low-DH or transition case) and a candidate-specific quality review; timing acceptance does not replace either. Optimization stays parked until Philip signs off.

## Evidence and executed source

[Compact measurements](measurements.json) sha256:87dd4ba6573490696f5c0950dfa7da5f0484baba8b0a4a9487e262d800a67971

The record holds both campaigns' assessments, every window's throughput, drift, limiter state, DH draws per frame and contamination verdict. It also has each session's readiness tail, screenshot identities, dataset restoration and telemetry summary. Campaigns ran at commit a8fa239, after the acceptance was committed in `benchmarks/README.md`. Raw session directories were deleted after extraction.
