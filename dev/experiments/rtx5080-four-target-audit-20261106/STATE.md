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
installed only in the owned tools directory. Native Gradle compilation and both client run preparations pass. The first
native startup completed successfully in interactive console Session1 at3840×2160
on the actual RTX5080, returning desktop GL3.2. Configuration/world restoration
passed and zero clients remain from that operation. Explicit GL4.6 startup also passes on the actual RTX5080, with restoration and
zero clients. MakeUp and Reimagined native/GL3.2 baseline r1 processes have both
completed validly (three20s windows each,120s conditioning), restored and released.
Their original shaders/neutral collector are unchanged by the current G03 patch.

MakeUp G03 functional r1 failed actual work admission, restored and released.
Its native linked CUTOUT is byte-identical to the existing complete semantic
fixture. The source-only path was gated to Asahi, so a finite Windows RTX5080
host/platform repair is under live validation. See G03_PORT.md. MakeUp native r2 passes all arms, with an actual linked rewritten CUTOUT,
completed native submissions, restoration and zero clients.
The maintained runner also now exposes the exact three-mod optimized stack for
G03 and matched original baselines, with artifact/version proof.96 Python tests
pass on both hosts (2 Asahi /11 Windows skips); relevant Java source/policy tests
and development compilation pass. No G03 performance or visual claim is made.

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
  regressions and development compilation pass. Native5080 GL3.2 and explicit GL4.6 startup proof now pass.
- Added an explicit expected-renderer guard so a compatible GL context on the
  integrated GPU cannot be accepted as an RTX5080 measurement. This guard records
  the observation and does not modify driver preferences.
- Preserved seven exact native/PBR option maps and original archive identities
  under versioned benchmarks/profiles/v1. Frozen historical inputs stay in place.

Next: finish actual modern GL context proof and functional/source/work admission
before scored acquisition. Use the
maintained commands in PLAN.md; continue all16 goals across four targets with
diagnosis, repairs, repeated timing, limited-quality checks and variance charts.

Source branch: `rtx5080-audit`, tracking user-owned origin. Preparation is pushed;
current G03 source/harness checkpoint includes MakeUp functional proof and BSL
optional-stack recovery fixes. Active native task:
ShaderLOD-RTX5080-BSL-G03-Check-Optimized-Native-R2, request
C:\ShaderLODBench\requests\bsl-g03-check-optimized-native-r2.json. Do not mutate
its pinned inputs. BSL r1 failed before scoring on release/module version labels
and then required whole-profile recovery because four new module settings files
were untracked. The maintained recovery preserved those files, restored all inputs
and ended failed-restored with no replay. Its old frozen Python/lock inputs are
retained under recovery-source-before. The normal harness now predeclares those
settings and validates the exact JAR module versions.


Initial native preflights r1/r2 did not launch Java because copied profile/marker
ACLs excluded the non-elevated desktop token. Only owned C:\ShaderLODBench access
was corrected. Windows inheritance flags do not apply a file ACE to protected
private JSON markers, so both workspace.json files needed direct Modify grants.
Session1 owns subsequent maintained runtime receipts. The manual Task Scheduler
launcher has no automatic triggers; retire each owned task once terminal.
