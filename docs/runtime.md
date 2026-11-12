# Live functional verification

`./verify --runtime` launches the real game client with the shipping JAR and a separate development probe mod. It checks native selection, activation and restoration. It never produces a performance result.

## Target

The existing live setup is the Asahi MacBook Air (Apple M1, Mesa, 2560×1600) with pinned Complementary Reimagined r5.9.3, its original options, native resolution and native AA, with Distant Horizons enabled. Running without DH (`--without-dh`) is a separate diagnostic and does not substitute for the DH-enabled target. The benchmark implementation was additionally allocated native Windows RTX 2080 Ti access. Its actual observed native monitor was 1024×768; it is a separate configuration, not a substitute for the Asahi surface.

## Running it

```
./verify --runtime --profile run-client-absent-dh --without-dh --display /run/user/1000/shaderlod-display/environment.json --ownership-root /run/user/1000/shaderlod-ownership --timeout 180
```

All of `--profile`, `--display`, `--ownership-root` and `--timeout` are required; a bare `./verify --runtime` refuses. The receipt and its log go to `~/.local/share/shaderlod-next/evidence/attempts/<UTC time>-<mode>.json`, and the run prints the path as `RECEIPT:`. `--receipt PATH` overrides the location, but a path inside the repository refuses, because `build/` and profile directories are disposable. Use only a display and host window that is actually allocated; the command does not grant that. Prepare world copies with `shaderlod_host.storage.copytree`; never mutate a shared source world. A completed receipt is never overwritten.

Bootstrap builds the main JAR, runs the package audit and the development probe, and reads the exact Java command from `build/moddev/functional-launch.json`. The lower-level executor is `python3 -m shaderlod_host.runtime_live --help`. `--shipping-jar` tests the packaged registration; `--native-control` runs without ShaderLOD as a negative reference and has no probe receipt.

## What the probe checks

The probe drives the real settings screen widgets through: unapplied Cancel, native Apply with unchanged TAA, Off, Apply of the identity provider, Cancel while active, and a deliberately invalid shader compilation followed by restoration. For each step it records archive hashes, increasing generations, options, native dimensions, rendered-section counts and output image hashes. The host validator joins these observations itself; a producer's PASS field alone fails validation.

Active needs a submitted frame through the selected pipeline (see [Iris integration](iris-integration.md)). Output PNGs are evidence that something rendered, not an image-quality oracle. The receipt reader accepts only schema `shaderlod-functional-v3` and refuses older schemas.

## Process ownership

The Linux watchdog owns the Java process group and stops it at the timeout, even if the submitting agent disappears. A nonblocking host/device advisory lock excludes other cooperating runs and returns busy instead of waiting; it does not see uncooperative processes, so check for other running clients before launch. Owned processes are identified by host, boot and process-birth identity, never by PID alone.

Runtime executable inputs are hashed before and after each attempt; drift invalidates the attempt. This covers declared JVM inputs, compiled resources and discovered JARs, not every system library.

`python3 -m shaderlod_host recover` marks an interrupted attempt only after its recorded host, boot and process group are provably quiescent. It never kills a possibly foreign process, never refunds a spent attempt and never rewrites a published result. Missing ownership metadata, another host or boot, or a live group make it refuse.

## Benchmark sessions and profiling

The shared Linux/native-Windows session controller, quick in-session validation, short comparisons and frozen multi-session qualification are documented in [benchmarks](benchmarks.md). It uses the same settings-screen functional probe and independent host validator. [Profiling](profiling.md) describes full-frame streams, renderer-stage diagnostics, pack mappings and separate motion captures.

Native Windows supervision uses a kill-on-close owned process job; Linux uses an owned process group. Both refuse unknown process identity. `doctor --probe-worker --output-root EXTERNAL` exercises normal exit, deadline, direct-parent loss and campaign-ancestor loss cleanup. Raw evidence and executable input snapshots remain external.

DH-enabled functional acceptance requires structured `dh.enabled=true`; mod presence or a readiness string alone is insufficient. This proves configured enablement only, not rendered DH coverage. The Asahi native DH sampler-limit failure remains a blocked cell. No-DH validation does not replace it. Exhaustive loaded-system-library attestation and universal image/performance guarantees are not provided.
