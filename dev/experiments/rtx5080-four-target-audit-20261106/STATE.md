# RTX 5080 audit working state

The task is active and incomplete. No RTX5080 baseline or candidate timing has
been acquired. The storage blocker is resolved: a fresh native Windows check
observes63.2GiB free on C:, enough to resume guarded setup while preserving the
maintained32GiB reserve. The earlier80–100GiB suggestion was conservative headroom,
not a required campaign minimum. Cleanup remains limited to ShaderLOD as directed;
NVIDIA caches and other-project checkpoints are untouched.

Native setup is in progress under C:\ShaderLODBench. Source is a snapshot of the
pushed canonical branch, not the historical lowercase checkout. Two native managed
slots (rtx5080-baseline and rtx5080-pbr) were prepared with run_storage.copytree and
checkout, after transport admission and checking515 profile-file identities.
All original archive identities match the versioned profiles. An isolated Python
environment has the pinned dependencies; a checksum-verified Microsoft JDK21 is
installed only in the owned tools directory. Initial Gradle dependency/build setup
is running in Session0, which is sufficient for compilation. Rendering will use
interactive console Session1 and prove the actual RTX5080 renderer/context.

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

Next: complete native build setup, then validate actual native/modern GL contexts
and functional/source/work admission before scored acquisition. Use the
maintained commands in PLAN.md; continue all16 goals across four targets with
diagnosis, repairs, repeated timing, limited-quality checks and variance charts.

Source branch: `rtx5080-audit`, tracking the established user-owned origin.
Preparation checkpoints are committed and pushed; raw host preflight inventories
remain local in this run directory. No target benchmark processes or background
jobs were started. Cleanup transfer/verification processes have completed; native build setup is
active and other projects' model/world data are protected.
