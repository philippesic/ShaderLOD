# Repeated original versus reduced-SSR five-effect controls

Source-only preparation workflow; real preparation, Iris rendering, quality and
performance acceptance remain pending. The previous combined reduced control's
same-JVM forward GPU contrasts were +9.23% and approximately flat; reverse were
+8.85%/+16.13%. Those inconsistent correlated phases do not establish the15%
ordinary original-baseline gate. This plan adds an explicit original-mod-absent
comparison in two scenes, preserving all results rather than selecting a pair.

`scripts/prepare_reduced_effects_profiles.py` creates exactly these layouts:

| Scene | Original | Patched disabled | Combined active |
| --- | --- | --- | --- |
| Forest | `forest/high` | `forest/patched-off` | `forest/adaptive` |
| Coastal glint | `coastalwater/high` | `coastalwater/patched-off` | `coastalwater/adaptive` |

Existing runner cases are `forest-high/forest-patched-off/forest-adaptive` and
`water-high/water-patched-off/water-adaptive`. High uses `runClientLodOff`, which
physically excludes main ShaderLOD. The other arms use `runClientLodOn` with
actual global false/true respectively; both share the same private reduced
archive and complete requested shader map. Runtime must verify actual flags.

## Exact source and held quality

Original Photon SHA256:
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Private reduced-enabled SHA256:
`636a164832f5d4915548e288491a83e33f14a591f72987c77b7d8931e9800f83`,
from `build/Photon-SSRFarReducedTraceProbe-enabled.zip`. No rebuilding or shader
changes occur. Qualification checks exact archive/member/probe metadata,
unchanged base/bridge metadata and retained original license. The private gate
is fixed true; a disabled or unknown source is rejected.

Default canonical sources are explicit stopped original profiles:

- Forest: `run-workloads/native-terrain-b15-r2/forest/high`, inspected
  x256.5/y95/z768.5/yaw135/pitch9/daytime7000.
- Coast: `run-workloads/photon-ssr-cost-controls-r1/coastalwater/high`, inspected
  x500.5/y68/z660.5/yaw87.380/pitch3.735/daytime12500.

Preserve source original native options, clear weather, requested24 and existing
Minecraft/resource/Sodium/DH config files. No change enables extra upstream
features to inflate the workload: forest SSR can remain inactive under its
original settings even though the combined SSR module flag is true. The source
must carry matching complete original loaded-option evidence. Source settings
are normalized through exact original/derivative declarations; undeclared
controls, changed requested values or changed upstream defaults reject.

Fresh TOML holds PERFORMANCE/32/96/transition8/debugfalse; terrain and its
acknowledgement are false, cap16/warmup30 remain inert, and CPU frustum/list/
coherence experiments are false. The five patched effect switches, use-mod-
config, preserve-lighting and private reduced gate are true; cache/analytic/
debug are false. Only global enable differs between disabled and active.
Parent control global/terrain overrides are removed and recorded, preserving
the full inspected pose/time/preset/weather control. Old capture triggers reject.

The source forest contains one pinned DH jar. Only that **fresh copied** jar is
hash-verified and removed from outputs. The source remains intact; coastal must
already contain no profile mod jars. Unknown optional jars reject rather than
being silently removed. Iris/Sodium remain the minimal shader stack selected by
the runner. All six profiles require physical DH absence and `--dh absent`.

## Preparation and retained evidence

```sh
python3 scripts/prepare_reduced_effects_profiles.py \
  --scene both --output run-workloads/photon-reduced-five-effects-r1 --dry-run
python3 scripts/prepare_reduced_effects_profiles.py \
  --scene both --output run-workloads/photon-reduced-five-effects-r1
```

Only root executes actual preparation after verifying the GPU/client lease is
idle. `--scene forest` or `--scene coastalwater` limits the output; explicit
source/archive path arguments retain the same strict pins and inspected poses.
No symlink/latest source selection or existing-output replacement is allowed.
Dry-run reads no archives or saves and creates nothing.

Actual preparation refuses live Minecraft, holds both selected source save
locks, fingerprints each canonical stopped world and copies it equally into
that scene's three arms. It never opens the source SQLite cache. Source save and
config/archive/report hashes are rechecked after copying. Fresh profiles exclude
old camera outputs, screenshots, logs and benchmark/workload requests/reports.
Source history stays untouched; textual original config/control/provenance bytes
are retained under each scene's `source-provenance` directory. New manifests
record actual canonical/copied hashes, selected source/options, exact archives,
control changes, experimental arm config and physical DH removal. Failed partial
preparations remain with an error status; use a new destination for retries.

The initial12 fixtures passed: real immutable pins/layouts, fresh/symlink/path
guards, fixed config/control/option normalization, source checksum/missing
evidence rejection, tiny mocked-qualified canonical copies/source preservation,
world-lock failure and retained unmanaged-mod failure. Production pin constants
are never changed by these tests. Tiny copy fixtures inject qualified archive
records and stand-in bytes; they do not establish actual archive/world preparation.
No downloads, real preparation, compiler, build, encoder, client or GPU ran by
this source owner. Existing runner/helpers/main/shader/default files are untouched.

## Root runtime sequence and interpretation

First run a small native pilot in fresh retained pilot copies. Actual2976×1672,
minimal/noDH, fixed wetness0, sample5/frame-only and CPU layers off must match the
preceding N5 controls. Verify original/disabled/active shader compilation,
loaded maps/global flags, exact artifacts/class fingerprints, camera/time,
requested/effective24, native window/pacing and images before the repeated run.
Keep the untouched prepared master separate from pilot-mutated saves.

After pilot qualification, run a new three-block serial matrix with all six
cases, preserving failed and negative runs. Example coordinator command:

```sh
python3 scripts/run_proof_matrix.py \
  forest-high forest-patched-off forest-adaptive \
  water-high water-patched-off water-adaptive \
  --profiles-root FRESH_TIMING_COPIES --stacks minimal --dh absent \
  --window-scale 2 --sample-interval 5 --fixed-wetness 0 \
  --warmup 45 --duration 25 --repeats 3 --tag photon-reduced-five-effects-r1 \
  --skip-build
```

Compile/freeze the source beforehand; `--skip-build` does not prove compilation.
Three Williams blocks expose order/JVM drift but are a partial positional cycle.
Pair within blocks and separately report active versus original, disabled versus
original and active versus disabled for each scene. GPU, CPU and derived frame
durations remain separate; frame counts do not create independent confidence
intervals. Do not add shader/geometry percentages or call this source proof a
meaningful original-baseline performance win.

Glint/motion/underwater/transition quality, second-scene repeat consistency,
ordinary15% acceptance, broad configuration/support and human default choices
remain separate. Original/skip glint quality failures remain retained. Positive
timings cannot rescue an unacceptable output or complete the shared goal.


Root integration: all12 current fixtures passed. Actual both-scene preparation
completed at run-workloads/photon-reduced-five-effects-r1. Each scene's three
canonical copies match: forestf752339d...c7be08b, coastal89c96296...c7b46.
Original source/config/archive checks stayed equal; pinned DH was removed only
from fresh forest outputs. Fresh full/off/active TOML flags and full effective
option-derived native maps are recorded in the new manifests.

Root cloned all six untouched masters into a separate pilot collection,
run-workloads/photon-reduced-five-effects-pilot-r1, with every world hash checked.
Exec48805 is running six serial cases, minimal/noDH/native2976x1672,45warm25capture,
GPU sample5/frame-only, CPU layers off, fixed wetness0. Compiled classes remain
frozen; pilot repeat1 is no acceptance repeat. Actual terminal contracts/images/
costs remain pending before the three-block run or promotion.


## Six-arm pilot terminal and negative

Exec48805 exited1 only at final aggregation; all six individual clients completed
and passed runner input/artifact/config/source/window/PNG checks. Root revalidated
them and retained the original failed matrix. The aggregator had treated the
private PHOTON_SSR_FAR_REDUCED_TRACE_PROBE switch as upstream quality. A narrow
repair accepts only the exact original/reduced archives plus full prepared-family
member/metadata qualification and enabled private gate. Other native/private
quality changes still reject. Four regression fixtures and19 prior matrix tests
passed. Recovery analysis uses matrix-manifest-qualified.json, with the original
manifest hash/status/reason preserved; raw failed manifest remains unchanged.

| Scene/arm | FPS | GPU mean / median ms | Thread ms |
| --- | --- | --- | --- |
| Forest original | 72.114 | 12.5230 / 12.3980 | 7.4453 |
| Forest disabled | 71.836 | 12.6092 / 12.4225 | 7.2791 |
| Forest active | 68.077 | 13.3693 / 13.2130 | 7.2799 |
| Coast original | 57.773 | 16.0884 / 15.9570 | 7.4650 |
| Coast disabled | 56.391 | 16.4463 / 16.3712 | 7.4088 |
| Coast active | 57.061 | 16.2199 / 16.1934 | 7.4051 |

Active mean GPU regressed6.76%forest/0.82%coast versus original (median6.57%/
1.48% regressions). Single serial pilot only; no useful original-baseline gain
or cause established. Earlier positive same-JVM phases remain retained and
cannot override this result. Do not launch three repeats merely to select a
better outcome; revisit only when a concrete new signal/control warrants them.
Source intervention annotations do not assert equivalent quality. Miniature
configuration/driver evidence remains useful independent work.
