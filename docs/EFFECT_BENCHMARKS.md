# Independent Photon effect controls

The preparation tool selects the preserved `effects-no-atmosphere` candidate:
`build/photon-effects-no-atmosphere.zip`, SHA256
`ab1b6d859829fe3e0925b69cdd3759369c44bc876cc97de77d9878ed27b8658d`.
It verifies exact bytes, pinned upstream metadata and retained license through
`scripts/prepare_isolations.py`. No atmosphere guard is present; analytic water
is explicitly false. This document reports tooling, not a performance result.

The default source is the stopped original-high shoreline profile at
`run-workloads/ssr-probe/coastalwater/high`. Its upstream SSR/reflection settings
must be retained; the preparer copies recorded effective settings and provenance
rather than replacing them with a default profile. Original high, runtime
patched-off and all active arms use matched scene/control/world data, lighting
protection, cached-sky false, water 96/256/32 and cloud 128/1024/64. Geometry stays
disabled. The inherited source camera/time/weather and runtime options remain
unchanged. Choose other prepared scene roots with `--source-proof-root` and
`--scenes`; the runner supports forest/cloud/water prefixes.

| Profile / water case suffix | Main mod/global live LOD | Compile-time effect switches |
| --- | --- | --- |
| `high` | Main mod absent | Pinned original Photon |
| `patched-off` | Present, false | All five enabled in source |
| `alloff` | Present, true | All five false |
| `cloud-only` | Present, true | `SHADERLOD_CLOUDS` only |
| `water-only` | Present, true | `SHADERLOD_WATER` only |
| `ssr-only` | Present, true | `SHADERLOD_SSR` only |
| `fog-only` | Present, true | `SHADERLOD_FOG` only |
| `materials-only` | Present, true | `SHADERLOD_MATERIALS` only |
| `adaptive` | Present, true | All five true |

The patched profiles share one exact candidate archive; shader options select
its explicit source branches. Runtime patched-off retains dynamic bridge
branches while alloff can compile away the effect LOD paths. This distinction
can diagnose code-shape overhead. Materials-only is an overhead/negative control
when POM or heightmap work is inactive; no material optimization is established
merely by toggling the option. Selected effects can also be absent from a given
scene, so source budgets do not prove measurable executed work.

## Preparation and first sweep

Close all source/target clients before preparation. Use a fresh output root;
existing outputs are refused unless the explicit manifest-owned backup option
is requested. The source/target locks, original/candidate attribution checks,
raw source fingerprints, full effective-map evidence and copied save hashes are
inherited from isolation preparation. Extra arms are cloned from the stopped
adaptive copy under its world lock, independently hashed, and recorded in scene
provenance. Failure remains visible in the root manifest; incomplete effect
preparation marks the affected scene configuration-only until complete.

```sh
# Validate metadata/configuration only; no world hash/copy or client launch.
python3 scripts/prepare_effect_profiles.py --source-proof-root run-workloads/ssr-probe --output run-workloads/effects-ssr-shoreline --without-dh --dry-run
# Actual fresh copies, only after source clients stop.
python3 scripts/prepare_effect_profiles.py --source-proof-root run-workloads/ssr-probe --output run-workloads/effects-ssr-shoreline --without-dh
# Nine independent JVM controls, exploratory single sweep.
python3 scripts/run_proof_matrix.py water-high water-patched-off water-alloff water-cloud-only water-water-only water-ssr-only water-fog-only water-materials-only water-adaptive --profiles-root run-workloads/effects-ssr-shoreline --stacks minimal --dh absent --repeats 1 --warmup 35 --duration 15 --fixed-wetness 0 --tag effects-shoreline-r1
```

Nine runs require at least 7.5 minutes of warmup/capture plus JVM startup and
shutdown. All timing guards, live enabled checks, actual option fingerprints,
wetness telemetry and fresh screenshot preservation apply. The CLI
`--fixed-wetness 0` selects a dev-only shader-weather control for every arm;
real rain/smoother clocks and frame counter still advance. It is not a production
optimization. Record the inherited shoreline's actual rain and shader wetness.

Select the promising effects for two to three balanced repetitions with fresh
output tags before drawing conclusions. For example:

```sh
python3 scripts/run_proof_matrix.py water-high water-patched-off water-alloff water-water-only water-ssr-only water-adaptive --profiles-root run-workloads/effects-ssr-shoreline --stacks minimal --dh absent --repeats 3 --warmup 35 --duration 15 --fixed-wetness 0 --tag effects-shoreline-r3
```

The analyzer requires identical upstream effective quality options for alloff
and every `*-only` arm, and describes `SHADERLOD_*` option changes separately
under `intentional_input_changes.shaderlod_pack_options`. By default it compares
each condition against scene original-high within the same block. To compare
active/individual effects directly to alloff, reanalyze the same complete matrix:

```sh
python3 scripts/analyze_proof_matrix.py dev/logs/effects-shoreline-r3/matrix-manifest.json --baseline water-alloff --output dev/logs/effects-shoreline-r3/against-alloff.json
```

Different archives/options, quality changes and framework overhead must not be
collapsed into a shader-only gain. A single sweep is diagnostic, not acceptance
of the sustained performance gate. Preserve poor/anomalous controls and inspect
per-run images; source switches and valid timings do not establish acceptable
visual fidelity. No additional shader pack integration follows until Photon
passes the agreed repeated ordinary-scene gate.

Implementation checks: four effect-planning/guard tests, fourteen proof-tooling
tests and seven isolation tests passed. Python compilation passed. No Minecraft,
real world copies or new shader compilation ran during this tooling task.
