# RTX 5080 audit working state

The task is active and incomplete. No RTX5080 baseline or candidate timing has
been acquired. The pending user question asks for capacity on Windows C: or a
review of exact cleanup candidates. Last observed free capacity is about1GiB,
below the maintained32GiB reserve; source/runtime setup and acquisitions wait
for this required external input. Do not override the reserve or treat WSL's
virtual free space as free capacity on the backing Windows volume.

Completed independent preparation:

- Confirmed RTX5080/driver617.42, idle GPU and prior workload release.
- Confirmed active native Windows console at3840×2160; Tailscale aliases use
  private-key authentication and verified host keys.
- Inspected existing Prism game/Java caches and old shader runs without editing
  personal profiles. Native p-stack Python lacks the benchmark's psutil module.
- Searched Asahi/2080Ti history and recorded per-goal work proof and regression
  investigations in SOURCE_READINESS.md. No prior unsupported/losing case is
  automatically an RTX5080 result.
- Added real benchmark-only OpenGL context selection to the maintained runner,
  including actual-version proof and owned early-window configuration handling.
  Local93 benchmark tests (2 skips),55 launcher tests, Java context/sampler
  regressions and development compilation pass. Live5080 context proof remains.
- Added an explicit expected-renderer guard so a compatible GL context on the
  integrated GPU cannot be accepted as an RTX5080 measurement. This guard records
  the observation and does not modify driver preferences.
- Preserved seven exact native/PBR option maps and original archive identities
  under versioned benchmarks/profiles/v1. Frozen historical inputs stay in place.

Next: once capacity is available, prepare an isolated native Windows runtime
from the pushed canonical branch, validate actual native/modern GL contexts and
complete functional/source/work admission before scored acquisition. Use the
maintained commands in PLAN.md; continue all16 goals across four targets with
diagnosis, repairs, repeated timing, limited-quality checks and variance charts.

Source branch: `rtx5080-audit`, tracking the established user-owned origin.
Preparation checkpoints are committed and pushed; raw host preflight inventories
remain local in this run directory. No target benchmark processes or background
jobs were started. Storage/model/world data owned by other work are protected.
