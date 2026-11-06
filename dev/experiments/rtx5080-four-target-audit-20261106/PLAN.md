# RTX 5080 four-target audit

User authorized the complete rerun on 2026-11-06, including repairs of
hardware/context failures and investigation of unexpectedly slow implementations.
This is sustained work, not a quick screen or a promise to ship sixteen flags.
Canonical source is `/home/philip/ShaderLOD` on Asahi; Windows gets a verified
runtime copy. Source, tests, definitions and compact findings are committed here;
raw captures, worlds, downloads and host-private paths stay outside Git.

## Decision and coverage

Determine which of G01–G10, C01–C02 and N01–N04 actually remove useful work on
the RTX 5080, across Complementary Reimagined and Unbound, BSL and MakeUp Ultra
Fast. Both Complementary profiles are tested, as one shader family. Reuse exact
original archives/full native settings from the Asahi audit, including separate
native-PBR parallax cases and no-op/fallback cases. Preserve native AA and the
actual Windows monitor mode: preflight observes 3840×2160, not Asahi's 2560×1600.
Cross-machine absolute times therefore have different viewport scope.

Use native Windows hardware OpenGL in the logged-in interactive console session.
WSL GPU/CUDA visibility does not establish this. Observe real GL version, renderer,
extensions and entry points. Exercise Minecraft's native context and explicitly
requested modern contexts where required (notably GL4.5 DSA for C02 and GL4.3
diagnostics); record the actual returned version. Never spoof capability strings.
Use matched baselines/controls for each materially different measured context;
do not mix contexts or treat a request as proof of the returned version.

## Procedure

1. Verify allocation, desktop/session, free storage, toolchain and canonical
   client inputs. Reuse prepared inputs/caches and `run_storage.copytree` checks.
2. Audit all64 cells and relevant historical failures. Use maintained `plan` and
   `check` commands for actual source, ownership, producer/consumer and work
   evidence. Repair viable generic source/runtime/GL contract mistakes. Missing
   native work, unsupported hardware and unsafe lifetime proof are distinct from
   repairable admission failures; no guard is removed solely to fill a cell.
3. Diagnose unexpectedly slow active routes, including instrumentation cost,
   extra programs/copies, native-equivalent compiler output, redundant admission
   checks and fallback work. Keep diagnostic acquisition outside scored windows.
   Measure repaired variants afresh. Do not infer that a slower mean is a bug,
   or that successful Apply proves useful execution.
4. Acquire two independent native-only baseline processes per target in the
   primary actual context. Use matched identical-pack null controls, including
   relevant scene/profile variants. Run at least two independent comparisons for
   executable cells, balancing ABBA/BAAB where the maintained route supports it;
   otherwise retain separate native/candidate/native brackets and matched
   native-equivalent controls. Extend repeats when noise, disagreement or useful
   apparent effects warrant it. No precision/benefit claim from independent-frame
   assumptions or a single discovery result.
5. Exercise varied relevant scenes, PBR where native work requires it, cases with
   little/no opportunity, and moving-camera quality for plausible survivors.
   Check shimmer, ghosting, disocclusion and transitions with honestly bounded
   acceptance. Preserve resolution/AA, errors and all restoration checks.
6. Export original baselines and every optimization with frame-time SD/variance
   within windows, window-mean variation and between-process variance when n>1.
   Keep scenes, context versions, implementations, phases and proven/fallback
   work cohorts separate. Publish interactive and standalone charts for all64
   cells, with explicit reasons instead of invented bars for unmeasured cells.
7. Verify all owned operations restored/released, retain evidence and useful
   caches, remove owned scratch, commit coherent checkpoints and push the branch.

The maintained `scripts/run_benchmark.py` owns timing, conditioning, controls,
admission, recovery and storage. No competing acquisition wrapper is introduced.
No user time limit was set. A checkpoint, failed check, low disk space or first
negative timing is not completion. Independent authorized preparation continues
while a required external input is pending.

## Initial findings and pending input

The configured Tailscale SSH alias reaches DESKTOP-E78LJSI with verified host key
and private-key authentication. NVIDIA reports RTX5080/driver617.42,0% utilization.
The previous GPU owner reports released work. Native Windows console session1
is active and its display is3840×2160. No Minecraft client was observed.

The user subsequently authorized cleanup of old ShaderLOD junk. The completed
[cleanup](CLEANUP.md) recovered3.735GiB physically after verified off-machine
preservation; about4.925GiB is now free, still below the32GiB reserve. The user explicitly declined expanding cleanup beyond ShaderLOD. NVIDIA caches
and other-project checkpoints remain untouched. The known retained ShaderLOD
Prism trees total only2.677GiB logically and contain useful recovery/world/runtime
inputs; removing all of them would still fall far short of the reserve. No native
profile copy or scored acquisition has started. Additional backing-volume capacity
remains required; this is not a negative optimization result.

## Capacity resolved and native setup resumed

A later native check observes63.2GiB free on C:. This is enough to start; the
80–100GiB suggestion was an initial allowance for build/cache growth and retained
4K captures, not a fixed admission minimum. The maintained32GiB reserve stays in
force. Capacity-checked canonical profiles and managed native/PBR slots now exist;
original archive identities match and the isolated native toolchain is prepared.
Initial dependency/build setup is in progress. Keep compact timing records and
preserve completed large captures off-machine as needed, instead of requiring all
campaign captures to accumulate on C:. No scope expansion to other-project cleanup.
