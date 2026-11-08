# Benchmark guide

For the local Asahi machine without a desktop, see [headless development](HEADLESS.md).
The maintained Linux ARM64 launcher selects its installed GPU-backed virtual
display when no display is explicitly selected; the native 2560×1600 viewport
and all normal benchmark guards remain required.

For the prepared RTX 2080 Ti Windows host, see [Windows benchmarking](WINDOWS_BENCHMARK.md).

Use the maintained command for discovery, planning, execution and results:

```sh
python3 scripts/run_benchmark.py discover
python3 scripts/run_benchmark.py baseline --slot SLOT --pack ORIGINAL.zip --pack-options OPTIONS.txt --output FRESH_BASELINE
python3 scripts/run_benchmark.py plan --slot SLOT --pack ORIGINAL.zip --pack-options OPTIONS.txt --feature FEATURE --output FRESH_REPORT
python3 scripts/run_benchmark.py check --slot SLOT --pack ORIGINAL.zip --pack-options OPTIONS.txt --feature FEATURE --output FRESH_CHECK_REPORT
python3 scripts/run_benchmark.py test --slot SLOT --pack ORIGINAL.zip --pack-options OPTIONS.txt --feature FEATURE --output FRESH_REPORT
python3 scripts/run_benchmark.py report --output REPORT
```

`discover` lists local managed slots, worlds and shader archives. It is an inventory, not compatibility approval. Use `--profile` instead of `--slot` for an explicit prepared client. Set `--width` and `--height` to the intended native viewport (the local Asahi fullscreen mode is 2560×1600). Select the exact original pack/options for the feature; private candidates use `--candidate` instead of `--feature`. See the [candidate reference](BENCHMARK_CANDIDATES.md) for specialized scenes, compatibility checks and qualification protocols.

`baseline` renders the original pack with the main mod absent in the backed-up
authored save. After 120 seconds conditioning it captures three unchanged
20-second native windows, with the standard ownership, readiness, sample and
restoration checks. The report gives frame sample variance/SD (ms²/ms), and
separately variance/SD of the three window means and first-to-last drift.
These describe frame jitter and within-process variation, respectively; neither
is a confidence interval. Repeat in fresh processes to measure between-run
variation. Keep absent-mod baselines separate from the matched Master-Off
controls in optimization comparisons. Startup failure supplies no frame-time
measurement. `native-startup` remains a short original-only startup diagnostic.

`--gl-context native` preserves Minecraft's normal context request. For explicit
desktop API coverage, maintained comparisons also accept `3.2`, `4.3`, `4.5` and
`4.6`. These request real GLFW contexts; they do not override reported versions
or capabilities. Explicit requests disable NeoForge's reusable early window in
the owned profile, with the normal configuration backup/CAS/restoration contract.
The returned desktop version must meet the requested minimum and is recorded in
`summary.json.gl_context`. A driver returning4.6 for a4.5 request is labeled4.6;
it does not supply an independent4.5 result. Keep actual context/configuration
cohorts and their native baselines separate. Frozen Bliss qualification retains
its existing native-context protocol and refuses an explicit context override.
`--expected-renderer 'RTX 5080'` additionally refuses a ready client on a different
GPU, including a hardware integrated GPU. It validates the observed renderer
before scoring; it does not select the GPU or change personal driver settings.

`--performance-stack optimized` selects the exact pinned ModernFix, FerriteCore
and ImmediatelyFast tuple for G03 and its matched original-only startup/baselines or byte-identical native controls.
Default remains `minimal`. Other private/frozen protocols reject this override.
Preflight verifies every optional JAR, launch uses the maintained stack selector,
and runtime readiness checks the actual loaded module versions before scoring.
Optional artifacts are frozen operation inputs. Keep stack cohorts separate;
selecting the tuple is not proof that G03 compiled, executed, or helped.

`plan` runs the standard test's static checks without compiling, launching or creating the report. It lists the resolved launch plan, schedule, per-check elapsed time, failures and remedies. A stale build is reported; `test` rebuilds it automatically before launch. Native options and canonical profile inputs are checked; preserve unexplained differences before resetting a managed slot. Runtime-only checks still run in the client.

`check` accepts the same candidate arguments and exercises activation, available source/work checks, short captures and restoration without the two-minute conditioning period. It reports no performance or visual qualification. Use it when checking whether a newly integrated candidate runs; use `test` for a development performance comparison. Frozen Bliss qualification cannot use `check`.

`test` uses the fixed native/candidate/native-return protocol described below. `report` reads existing reports without launching anything, including historical reports. Its decision gives drift comparison precedence over an averaged positive mean, and identifies invalid runs or recovery requirements. It does not grant optimization closure or compatibility qualification. Phase timings in `summary.json` include nested profile preparation/restoration details; do not sum nested details into the total again.

Windows ownership JSON reads and atomic publication retry transient sharing errors
for at most three seconds, including CRT EACCES/EPERM errors that lack a Windows
error number; persistent denial still fails the operation. Normal
configuration restoration includes the five numbered FML backups for each
ShaderLOD client config. For an older interrupted report that omitted these
paths, `recover --output REPORT --preserve-generated-backups` retains newly
created known backups in that report before checking the complete original
profile map. Existing backups and unknown files retain normal ownership checks;
recovery never replays measurements.

For optimization iteration use the maintained standard test, on the authorized machine with a prepared managed client slot and authentic original pack and options:

```sh
python3 scripts/run_benchmark.py test \
  --profile /path/to/prepared/client \
  --pack /path/to/original-pack.zip \
  --pack-options /path/to/original-pack.zip.txt \
  --feature PUBLIC_OPTIMIZATION_ID \
  --output /path/to/fresh/report
```

The command automatically selects the existing Linux ARM64 launcher on that platform. Explicit `linux_arm64_runtime.py` commands remain supported. It finds an installed full JDK21 or a cached Gradle JDK21; an explicit JAVA_HOME retains precedence. Select an existing loader/version/scene slot; keep the report outside that slot and on the same filesystem as the profile. Confirm the lane is free and the inputs match the intended tuple before launch. Do not reset a slot owned by a client or interrupted operation. `python3 scripts/run_storage.py checkout --help` describes managed checkout. Use `run_storage.copytree` for profile/world copies; it enforces storage limits.

The standard test targets the prepared NeoForge 1.21.1 benchmark protocol. It renders native Off for 120 seconds before measurement, then acquires native Off, singleton candidate through normal Apply, then native Off return: 20 seconds each, with separate warmup, startup and recovery within a 900-second budget. Conditioning was added after repeated first-to-return drift; it does not prove stability. It records total-frame timing, drift, stills and restoration in the report. A valid slower result is still a result; a failed or invalid run is not a speed conclusion. Inspect `summary.json`, errors and restoration before proceeding. A short result is not proof of visual equivalence, DH/PBR compatibility or final closure. Compare matched total-frame measurements and disclose drift and scope.

C02 is an explicitly labeled 2×2 bloom quality tradeoff, with Master-On native
controls; its scoped exception is documented in the [candidate reference](BENCHMARK_CANDIDATES.md#c02-experimental-bloom-shading-rate).

## Choose the check for the question

| Question | Maintained route | What it does not establish |
| --- | --- | --- |
| What inputs and candidates exist? | `discover`, then `plan` with selected inputs | Runtime compatibility or ownership |
| Does this change activate and recover? | `check` | Performance or visual equivalence |
| Is the idea worth more investigation? | `test`, then `report` | Broad workload/machine qualification |
| What does native do without ShaderLOD? | `baseline` | Installed-Off overhead by itself |
| Does it preserve motion or work across packs/hardware? | Candidate-specific protocols in the reference | Coverage of untested scenes, transitions or machines |

Before designing a new study, establish the user's intended decision: deep
workload performance, compatibility breadth, quality, or a quick screen. Reuse
the supported commands above. Put the chosen claim, comparator, coverage, useful
metrics, controls and budget in the existing experiment record. Record exclusions
and fallback separately; do not impose a universal repetition count or payback
horizon. Performance requires actual-window execution and valid output evidence,
not merely enabling a setting. Source changes do not inherit earlier timings.

## Records and milestone cleanup

Keep current work in STATUS, durable attempts/decisions in EXPERIMENTS, supported
procedures here and specialized routes in BENCHMARK_CANDIDATES. Extend the existing
candidate entry when adding a route; retire superseded wrappers only after checking
imports, tests and historical reproduction dependencies. Keep active experiments in
`dev/experiments`, reusable inputs/clients in `dependencies` and `run-workloads`,
and retained historical evidence in `experiment-evidence`. Do not move a pinned
active path just to satisfy this layout.

After an investigation, preserve compact raw measurements, configuration/source
identity, conclusions and any images/motion needed to support the claim. Remove
owned disposable intermediates once dependencies and restoration are checked;
there is no background cleanup requirement. For old camera sequences, put a
nonempty `.storage-retire` file beside the frames only after recording the reason
and retained evidence location. `python3 scripts/run_storage.py prune-captures`
previews exact eligible files; `--apply` uses the existing ownership/restoration
checks. Age alone is not permission to discard motion evidence. `.storage-keep`
on an ancestor protects a subtree. Unknown or still-needed evidence stays intact.


## Cost-map diagnostic checkpoint

These commands never launch a client or enter scored A/B timing:

```sh
python3 scripts/run_benchmark.py profile-plan --fps 60 --seconds 20 --windows 3 --processes 2 --cadence 4 --scope-sets 7
python3 scripts/run_benchmark.py profile --input /absolute/path/capture.json --output /absolute/path/new-report
python3 scripts/run_benchmark.py profile-report --input /absolute/path/capture.json --output /absolute/path/new-report
```

`profile` currently imports an existing schema-1 canonical cohort; it is not yet
an owned live launcher. `profile-report` regenerates offline exports. Output must
be a new directory; existing finalized output is never overwritten. Legacy
unversioned `report.json` is read and labeled duration-only without inventing
endpoints. `profile-plan` estimates regularly active observations per scope set
using `floor(fps * seconds * windows * processes / cadence / scope_sets)` and exits
1 below `--minimum` (default 100). It does not inspect a live slot or qualify overhead.

CSV/JSON/HTML and separate CPU/GPU trace exports preserve missing values. Elapsed
GPU data has no timeline positions; uncorrelated GPU timestamps never share a CPU
trace. Cost labels and inclusive hierarchy are nonadditive. See
[COST_MAP_IMPLEMENTATION](COST_MAP_IMPLEMENTATION.md) for exact implemented scope,
test evidence and remaining stage 2–4 work. No four-pack capture study is implied.
