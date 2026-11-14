# Live functional verification

`./verify --runtime` launches the real game client with the shipping JAR and a separate development probe mod. It checks native selection, activation and restoration. It never produces a performance result.

## Target

The working live setup is the Asahi MacBook Air (Apple M1, Mesa, 2560×1600) with pinned Complementary Reimagined r5.9.3, its original options, native resolution and native AA, without Distant Horizons. The intended target has DH enabled, and that configuration is blocked on Asahi: the original pack exceeds the fragment sampler limit with DH, with or without ShaderLOD ([F9, F10](../FINDINGS.md)). Running without DH (`--without-dh`) does not substitute for it.

The primary development machine is the RTX 2080 Ti under native Windows, on a virtual display. Its benchmark surface is 3840×2160, where the GPU is the bottleneck; 2560×1440 is a secondary check closer to ordinary play, at which CPU and GPU time per frame are about equal. Original Reimagined runs and validates there with and without DH at 3840×2160 ([F48](../FINDINGS.md)). Its earlier controls ran at 1024×768 with the frame limiter at 60 and cannot show a speedup. It is a separate configuration, not a substitute for the Asahi surface.

## RTX 2080 Ti Windows setup

The machine has no monitor attached. A signed virtual display (Virtual Display Driver 25.7.23, device `ROOT\DISPLAY\0000`, driver store entry `oem6.inf`) gives the logged-in desktop a 2560×1440 surface; 3840×2160 is also offered. Its settings live in `C:\VirtualDisplayDriver\vdd_settings.xml`. Installing it added the SignPath Foundation certificate (thumbprint `3CF8CF26D8BA266C3A483AB7D26D4A818E317D76`) to the machine's trusted publishers. The authors advise removing the driver before a major GPU driver update. To remove it from an elevated prompt: `pnputil /remove-device "ROOT\DISPLAY\0000"`, `pnputil /delete-driver oem6.inf /uninstall`, delete that certificate from `Cert:\LocalMachine\TrustedPublisher`, and delete `C:\VirtualDisplayDriver`.

Native Windows execution needs a Windows path, so the game runs from a working copy at `C:\ShaderLOD`. Philip accepted this location on 2026-11-13 as the one exception to keeping project state inside the checkout. It holds the host package, benchmark definitions, dependencies and built classes of one commit, named in its `SOURCE_COMMIT`, plus prepared state that survives updates: the profile slots, `tools`, `build/moddev` launch descriptions, `ownership` and `.local`.

Refresh it from a clean WSL checkout after each commit that matters for a run:

```
python3 -m shaderlod_host sync-windows --output-root /mnt/c/ShaderLOD
```

It builds with the pinned JDK, checks the pinned runtime artifacts, and replaces only the code; it refuses while a Java process runs on Windows or tracked files have uncommitted changes. A fresh copy is prepared once, from Windows, in this order:

1. `prepare-windows` makes the no-DH slot `run-benchmark`.
2. `freeze-windows-runtime`.
3. `prepare-windows-dh --profile CANONICAL` makes the slot `run-benchmark-dh`, with Distant Horizons and its own `build\moddev\windows-launch-dh.json`.
4. `prepare-profile` for each slot.
5. `contain-windows-runtime --source-root OLD --ownership-root C:\ShaderLOD\ownership` copies everything the launch still reads from the old runtime location (the JDK and the library JARs) into `.local\artifacts\windows-runtime\external`. It rewrites the launch descriptions to point there and refuses if any outside reference remains.

The current copy was prepared from a legacy runtime that has since been retired, and it reads nothing outside `C:\ShaderLOD`. Its source profile is kept at `C:\ShaderLOD\canonical-client` for re-preparing slots. Recreating the copy from nothing would need a new adopted runtime. To freeze a settled DH dataset for a slot:
1. Open a DH session with `start.ps1 -DistantHorizons -DhGeneration`.
2. Hold the scene's case poses until the world's `DistantHorizons.sqlite` stops growing, then close the session.
3. Run `prepare-dh-dataset` (see [benchmarks](benchmarks.md#distant-horizons-workload)).

`-WithoutDhWitness` opens a session without the DH submission counter. Commands run with `C:\ShaderLOD\tools\python\python.exe`; the game itself must start through `benchmarks/windows/start.ps1` so that it lands in the logged-in desktop. A session switches the fullscreen video mode to the surface its comparison or validation names, so the desktop resolution does not need changing. The GPU is shared with other projects: check that it is idle before a run.

## Running it

```
./verify --runtime --profile run-client-absent-dh --without-dh --display .local/display/environment.json --ownership-root .local/ownership --timeout 180
```

All of `--profile`, `--display`, `--ownership-root` and `--timeout` are required; a bare `./verify --runtime` refuses. The receipt and its log go to `.local/artifacts/attempts/<UTC time>-<mode>.json`, and the run prints the path as `RECEIPT:`. `--receipt PATH` overrides the location, with repository-local outputs restricted to `.local/artifacts`; source, `build/` and profile receipt destinations refuse. Project work uses the local artifact root. Use only a display and host window that is actually allocated; the command does not grant that. Prepare world copies with `shaderlod_host.storage.copytree`; never mutate a shared source world. A completed receipt is never overwritten.

Bootstrap builds the main JAR, runs the package audit and the development probe, and reads the exact Java command from `build/moddev/functional-launch.json`. The lower-level executor is `python3 -m shaderlod_host.runtime_live --help`. `--shipping-jar` tests the packaged registration; `--native-control` runs without ShaderLOD as a negative reference and has no probe receipt.

## What the probe checks

The probe drives the real settings screen widgets through: unapplied Cancel, native Apply with unchanged TAA, Off, Apply of the identity provider, Cancel while active, and a deliberately invalid shader compilation followed by restoration. For each step it records archive hashes, increasing generations, options, native dimensions, rendered-section counts and output image hashes. The host validator joins these observations itself; a producer's PASS field alone fails validation.

Active needs a submitted frame through the selected pipeline (see [Iris integration](iris-integration.md)). Output PNGs are evidence that something rendered, not an image-quality oracle. The receipt reader accepts only schema `shaderlod-functional-v3` and refuses older schemas.

## Process ownership

The project does not use a benchmark scheduler or GPU lease service. The bounded
manual Windows bridge in the [benchmark guide](benchmarks.md) is not such a service.

The Linux watchdog owns the Java process group and stops it at the timeout, even if the submitting agent disappears. A nonblocking host/device advisory lock excludes other cooperating runs and returns busy instead of waiting; it does not see uncooperative processes, so check for other running clients before launch. Owned processes are identified by host, boot and process-birth identity, never by PID alone.

Runtime executable inputs are hashed before and after each attempt; drift invalidates the attempt. This covers declared JVM inputs, compiled resources and discovered JARs, not every system library.

`python3 -m shaderlod_host recover` marks an interrupted attempt only after its recorded host, boot and process group are provably quiescent. It never kills a possibly foreign process, never refunds a spent attempt and never rewrites a published result. Missing ownership metadata, another host or boot, or a live group make it refuse.

## Benchmark sessions and profiling

The shared Linux/native-Windows session controller, quick in-session validation, short comparisons and frozen multi-session qualification are documented in [benchmarks](benchmarks.md). It uses the same settings-screen functional probe and independent host validator. [Profiling](profiling.md) describes full-frame streams, renderer-stage diagnostics, pack mappings and separate motion captures.

Native Windows supervision uses a kill-on-close owned process job; Linux uses an owned process group. Both refuse unknown process identity. `doctor --probe-worker --output-root .local/artifacts/FRESH` exercises normal exit, deadline, direct-parent loss and campaign-ancestor loss cleanup. Raw evidence and executable input snapshots stay under ignored `.local/artifacts` until verified closeout.

DH-enabled functional acceptance requires structured `dh.enabled=true`; mod presence or a readiness string alone is insufficient. This proves configured enablement only, not rendered DH coverage. The Asahi native DH sampler-limit failure remains a blocked cell. No-DH validation does not replace it. Exhaustive loaded-system-library attestation and universal image/performance guarantees are not provided.

## Asahi display setup

The maintained virtual display uses KWin, Xwayland, D-Bus, `glxinfo` and `xrandr`
provided by the host. Its native surface is 2560×1600 with hardware GL acceleration.
The tracked template is `shaderlod_host/templates/systemd-units.json`; generate local
units with:

```
python3 -m shaderlod_host display-units --output-root .local/services
systemd-analyze --user verify .local/services/shaderlod-display.service .local/services/shaderlod-lid.service
```

Registration changes affect the host. Back up any existing owned units inside
`.local/backups` and coordinate with active clients before replacing them. Link the
generated files using absolute paths, reload, then enable the allocated services:

```
systemctl --user link "$PWD/.local/services/shaderlod-display.service" "$PWD/.local/services/shaderlod-lid.service"
systemctl --user daemon-reload
systemctl --user enable --now shaderlod-display.service shaderlod-lid.service
python3 -m shaderlod_host display-check --timeout 30
```

The lid service inhibits lid-close sleep for this development host; it does not grant
runtime allocation. Display configuration/data and the private environment file stay
under `.local/display`; the publisher is maintained Python source in this repo.
`display-check` verifies the current service invocation, private environment file,
live Wayland socket, hardware GL and native surface with a bounded deadline. Readiness
is not game, image-quality or performance qualification. The service's start check
uses the same command, so a broken display fails startup instead of publishing stale
readiness. Stop clients before restart or relocation of the checkout.

Only registration symlinks under the user systemd configuration and ephemeral
`/run/user/<uid>` sockets live outside the checkout. To retire this setup, stop owned
clients, disable/stop both services, remove only their registration symlinks and
reload the user manager. All persistent project state can then be managed here.
