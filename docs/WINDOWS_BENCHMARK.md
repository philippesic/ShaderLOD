# Native Windows benchmarking

The RTX 2080 Ti host DESKTOP-T4JKGJB has an isolated runtime under
`C:\ShaderLODBench`: repository snapshot `ShaderLOD`, original profile
`canonical-client`, portable tools in `tools`, and `gradle-cache`. The prepared
managed slot is `rtx2080ti-baseline`. Preserve these reusable inputs and caches.
This is a copied source tree, so synchronize intended changes before testing.

Launch native Windows Python and JDK21 in the logged-in user's interactive
console session. Launching Windows Java through WSL SSH runs in Session 0,
which cannot supply the required monitor. A manually started temporary Task
Scheduler task with an Interactive principal can enter the console session;
remove that task when finished. No automatic trigger is needed. Keep the
console unlocked and do not use the PC during capture. Readiness must confirm
hardware NVIDIA rendering, 2560×1600 native framebuffer, focus, and no fallback.

Set JAVA_HOME to the extracted JDK, GRADLE_USER_HOME to the isolated cache, and
put portable Python and Java's bin directory on PATH. Install the pinned
`requirements-dev.txt` dependencies for native Windows. The embeddable Python
path file must include the repository and its scripts directory. PowerShell
must not treat ordinary native stderr as a terminating exception; inspect the
native process exit code and the completed report instead.

Use the maintained commands in [the benchmark guide](BENCHMARK.md). A matched
native variability block uses:

```powershell
python scripts/run_benchmark.py test --slot rtx2080ti-baseline `
  --pack 'dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip' `
  --pack-options dev/experiments/new-optimization-goals-20261102/bliss-native463.txt `
  --candidate n04-bliss-sky --scene saved --saved-pose 0 `
  --width 2560 --height 1600 --bliss-qualification null `
  --qualification-order ABBA --qualification-client-heap 1536m `
  --output dev/experiments/FRESH_REPORT
```

The maintained qualification protocol supplies 180 seconds of conditioning,
20 seconds of settling and four 60-second windows; do not override its schedule.
Repeat with BAAB and a new output path. A and B are byte-identical original
shaders. This measures apparent gains caused by noise; it tests no optimization.
Check validity, source/graph identity, configuration restoration, world reset,
and zero remaining clients before interpreting results. Two blocks describe
this session, not a universal noise threshold or cross-hardware speedup.

Windows process ownership uses native psutil cwd/argument-vector observations
and exact process birth identities, including NeoForge argument-file evidence.
World restoration holds Java-compatible locks and preserves the world root and
session.lock in place, because Windows prevents renaming their open parent
directory. Exact canonical input maps are checked afterward. Existing slots
with world locks should be reused through the maintained runner; generic
whole-slot checkout replacement is not verified for Windows.

Control results are published atomically only after completion, with a separate
CREATE_NEW pending marker preventing command replay. Published receipts remain
immutable, avoiding Windows read/replace sharing races. Recovery does not replay
uncertain commands. Mutable Python records retry only transient Windows
file-sharing errors for at most three seconds; they never repeat the associated
Minecraft command. Persistent access errors still fail and require recovery.

NVIDIA temperature, clocks, utilization and power are endpoint snapshots.
Windows memory observations include working set, private commit and total page
faults; Linux PSI, cgroup and separate minor/major fault counters are unavailable,
not zero. These observations do not establish absence of thermal throttling.

The individual-candidate campaign also prepared `rtx2080ti-pbr`, backed by
`C:\ShaderLODBench\canonical-pbr`, for the existing pinned advanced-material
fixture. Both new directories need Modify permission for the interactive
benchmark user; creating them from the remote administrative session alone
can leave the interactive process unable to read them.

C02 can be requested separately with `--candidate c02-bloom-vrs`, the pinned
BSL10.1.8 original and non-PBR native384 options. This is explicitly a **quality
tradeoff**: 2×2 shading of the bloom prefilter, not native image equivalence.
The maintained test checks hardware availability, original pack/options,
absence of other requested features, executed bloom draws and graphics-state
restoration. Both controls use Master On without the VRS request. An unsupported
context or failed activation produces no speedup result. This does not bypass
any runtime qualification gate or qualify general visual compatibility.
