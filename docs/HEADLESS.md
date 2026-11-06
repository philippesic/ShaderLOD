# Headless development on the Asahi host

The host runs without SDDM or the Plasma desktop. Minecraft still needs a display
server. `shaderlod-display.service` supplies a private KWin virtual output and
Xwayland, using the Apple M1 GPU at 2560×1600, scale 1. It does not depend on the
physical panel or lid state. This is a different presentation backend from the
old desktop; do not pool its timing results with old desktop measurements.

The service uses its own D-Bus session and configuration under
`~/.local/share/shaderlod-display/`. It does not load the Plasma workspace or
change personal desktop settings. Its environment file is private, lives under
`$XDG_RUNTIME_DIR/shaderlod-display/`, and is checked against the current service
invocation. Readiness rejects software GL rendering and incorrect resolution.

## Normal use

Use the commands in [the benchmark guide](BENCHMARK.md). On Linux ARM64,
`run_benchmark.py test`, `check`, and `native-startup` automatically start and
select the installed headless service if neither DISPLAY nor WAYLAND_DISPLAY is
set. `plan`, discovery, report, build, and recovery do not start it. An explicitly
selected desktop display takes precedence.

For interactive development launches, use `scripts/linux_arm64_runtime.py client
on|off` with the ordinary `dev_client.py` arguments. This also selects the
headless display. Direct Gradle or `dev_client.py` launches need a suitable
display environment; they do not automatically select one. A virtual display
does not provide remote viewing by itself. Benchmark still captures remain
available in the report directory.

```sh
python3 scripts/headless_display.py --wait
systemctl --user status shaderlod-display.service
journalctl --user -u shaderlod-display.service
```

Stop the service only after all owned Minecraft clients have exited:

```sh
systemctl --user stop shaderlod-display.service
```

The next maintained launch starts it again. The installed user service is enabled
at login-manager startup, with user lingering enabled for this host. It is
independent of T3 and network services. No reboot is needed to restart it.

## Installation and recovery

The checked-in unit assumes the checkout is `~/ShaderLOD`. Back up any existing
unit before installing, and adjust its script path if the checkout moves:

```sh
mkdir -p ~/.config/systemd/user ~/.local/share/shaderlod-display/config ~/.local/share/shaderlod-display/data
cp scripts/systemd/shaderlod-display.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now shaderlod-display.service
```

Dependencies are the existing `kwin_wayland`, Xwayland, `dbus-run-session`,
`glxinfo`, `xrandr`, Python 3 and user systemd installation. Native Java, Iris and
shader requirements remain unchanged. Do not substitute Xvfb/software rendering
for performance testing.

`shaderlod-lid.service` holds logind's low-level `handle-lid-switch` inhibitor.
This overrides the base `HandleLidSwitch=suspend` setting: logind takes no action
on lid events while the service holds the lock, on battery or external power.
It is enabled independently of the display and T3, using the existing lingering
user manager, and needs no administrator credential. The base system policy is
unchanged. Verify the live lock with `systemd-inhibit --list` and the enabled
service with `systemctl --user is-enabled shaderlod-lid.service`. Stopping that
service restores the base lid behavior. It does not block explicit suspend or
protect against an exhausted battery.

Install the lid service (back up an existing unit before replacement):

```sh
cp scripts/systemd/shaderlod-lid.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now shaderlod-lid.service
systemd-inhibit --list
```

To return to the full desktop, stop the headless service with no client running,
then enable/start `sddm.service` with administrator privileges. Preserve network
and T3 startup configuration. The local backup and validation evidence for the
headless transition is in `dev/experiments/headless-development-20261103/`.

## Network startup on this host

The active Wi-Fi profile now has autoconnect enabled, no per-user permission
restriction, and a system-saved secret instead of a desktop secret-agent
requirement. The original profile settings were backed up privately under
`~/.local/state/shaderlod-headless/backups/` before the update. The live connection
was preserved; the saved settings and secret availability were verified without
printing the password. A cold boot or reconnect has not been tested after this
change. T3 is enabled as a user service and the user manager has lingering enabled.

## Functional validation

With the physical lid closed, the maintained launcher started the virtual display
from stopped state and completed the four-window clock-integrity check. Separate
Bliss and MakeUp checks verified rendering, shader activation, restoration and
client cleanup. The Minecraft runtime reported hardware Apple M1 rendering,
2560×1600, fullscreen, focused and VSync disabled. Display restart and readiness
were checked as well. This covers the tested development paths; it does not prove
cold-boot networking or remote interactive viewing.

## Measurement limits

Hardware acceleration and correct dimensions are necessary, not sufficient for
stable timings. Qualification must also verify actual Minecraft renderer,
fullscreen/focus, native shader options/AA, activation/restoration, raw capture
integrity, memory pressure and repeated original-byte null controls. Keep
functional checks separate from performance results. A passed local validation
does not certify every shader, loader, DH/PBR combination or future driver.

The first closed-lid baseline (2026-11-03 local date) completed six fresh-process
Bliss/native463 mixed-terrain null blocks, 24 valid 60-second captures, with exact
restoration and no remaining clients. **Timing is not calibrated:** process means
rose from 76.65 to 100.69 ms (+31.36%); identical shaders produced apparent effects
from −9.70% to +10.24%, with an approximate process-level 95% interval of
[−6.22%, +8.70%]. All four preregistered stability gates failed. No timed cgroup
memory-limit throttling was recorded. Battery warming is indirect evidence;
CPU/GPU thermal throttling was not established. This single scene does not fulfill
the separate two-scene calibration contract. A brief Wi-Fi settings save during
block 2 is disclosed and retained. See the [interpretation and evidence](../dev/experiments/headless-development-20261103/final-interpretation.json).
