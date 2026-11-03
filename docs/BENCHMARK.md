# Benchmark guide

Use the maintained command for discovery, planning, execution and results:

```sh
python3 scripts/run_benchmark.py discover
python3 scripts/run_benchmark.py plan --slot SLOT --pack ORIGINAL.zip --pack-options OPTIONS.txt --feature FEATURE --output FRESH_REPORT
python3 scripts/run_benchmark.py check --slot SLOT --pack ORIGINAL.zip --pack-options OPTIONS.txt --feature FEATURE --output FRESH_CHECK_REPORT
python3 scripts/run_benchmark.py test --slot SLOT --pack ORIGINAL.zip --pack-options OPTIONS.txt --feature FEATURE --output FRESH_REPORT
python3 scripts/run_benchmark.py report --output REPORT
```

`discover` lists local managed slots, worlds and shader archives. It is an inventory, not compatibility approval. Use `--profile` instead of `--slot` for an explicit prepared client. Set `--width` and `--height` to the intended native viewport (the local Asahi fullscreen mode is 2560×1600). Select the exact original pack/options for the feature; private candidates use `--candidate` instead of `--feature`. See the [candidate reference](BENCHMARK_CANDIDATES.md) for specialized scenes, compatibility checks and qualification protocols.

`plan` runs the standard test's static checks without compiling, launching or creating the report. It lists the resolved launch plan, schedule, per-check elapsed time, failures and remedies. A stale build is reported; `test` rebuilds it automatically before launch. Native options and canonical profile inputs are checked; preserve unexplained differences before resetting a managed slot. Runtime-only checks still run in the client.

`check` accepts the same candidate arguments and exercises activation, available source/work checks, short captures and restoration without the two-minute conditioning period. It reports no performance or visual qualification. Use it when checking whether a newly integrated candidate runs; use `test` for a development performance comparison. Frozen Bliss qualification cannot use `check`.

`test` uses the fixed native/candidate/native-return protocol described below. `report` reads existing reports without launching anything, including historical reports. Its decision gives drift comparison precedence over an averaged positive mean, and identifies invalid runs or recovery requirements. It does not grant optimization closure or compatibility qualification. Phase timings in `summary.json` include nested profile preparation/restoration details; do not sum nested details into the total again.

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
