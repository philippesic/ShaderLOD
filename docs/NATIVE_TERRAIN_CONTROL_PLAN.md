# Native-resolution guarded terrain control plan

Source-only plan, 2026-10-05. No preparation, builds, clients or GPU measurements
were run for this document. The coordinator owns the sole GPU/client lease and
must verify its current live-job checkpoint before executing any step below.

Native 2976×1672 protected-lighting B15 same-JVM controls show exploratory
approximately 6–11% forest and 7–9% coastal GPU reductions; the ordinary 15%
gate remains unmet. Previous requested24/effective16/DH128 measurements were at
another resolution/checkpoint. They do not establish the current native combined
result. This experiment separately measures existing detail-radius reduction
and shader behavior, with the terrain policy still default-off.

## Four explicit serial arms

Use existing `run_proof_matrix.py` cases; no new case or policy is required.

| Case | Profile under the new root | Task | Pack/global enable | Terrain configuration | Required actual detailed radius |
| --- | --- | --- | --- | --- | --- |
| `forest-high` | `forest/high` | `runClientLodOff` | Original Photon; ShaderLOD absent | Disabled; acknowledgement false | Requested24/effective24 |
| `forest-patched-off` | `forest/patched-off` | `runClientLodOn` | Frozen B15; global disabled | Disabled; acknowledgement false | Requested24/effective24 |
| `forest-adaptive` | `forest/adaptive` | `runClientLodOn` | Same B15; global enabled; all five effect options true | Disabled; acknowledgement false | Requested24/effective24 |
| `forest-geometry` | `forest/geometry` | `runClientLodOn` | Exact adaptive pack/options/config except the terrain opt-in/acknowledgement | Existing cap16; manual warmed-scene acknowledgement true | Requested24/effective16 |

All arms retain DH128, native2976×1672, minimal Iris/Sodium runtime, the same
stopped source save, and the forest camera256.5/95.0/768.5, yaw135, pitch9,
daytime7000, clear weather and fixed shader wetness0. Those camera values come
from the inspected native forest manifest; reverify its current source before
copying. Preserve its PERFORMANCE preset, near32/far96/transition8, upstream
quality and resource packs. Do not change requested distance to16 in any arm.

Pin B15 `build/photon-lighting-tier-no-atmosphere.zip` SHA256
`b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c`;
original Photon SHA256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Use the existing locked Minecraft1.21.1/NeoForge21.1.253/Iris1.8.12/Sodium0.6.13/
DH3.3.3 set and retain exact loaded versions/hashes, including the actual Iris
snapshot/local version string. Preserve licenses/build metadata; no archive
rebuilding or protocol/shader changes enter this experiment.

## Fresh preparation after all clients stop

1. Identify a stopped original-high forest profile containing the audited warm
   DH database and original-Photon effective option evidence. The existing
   `run-workloads/native-effect-isolation/forest/high` is a source candidate;
   verify its save lock, archive, report and source manifest before using it.
   Use a **new** destination, for example `run-workloads/native-terrain-b15-r1`.
   Never overwrite or repurpose earlier negative controls or measured profiles.
2. Qualify the exact B15 and inherited upstream settings with the existing
   effect preparer, first dry-run and then prepare under the runtime owner's
   exclusive lease:

   ```sh
   python3 scripts/prepare_effect_profiles.py \
     --source-proof-root run-workloads/native-effect-isolation \
     --output run-workloads/native-terrain-b15-r1 --scenes forest \
     --effect-archive lighting-tier-no-atmosphere --dry-run
   python3 scripts/prepare_effect_profiles.py \
     --source-proof-root run-workloads/native-effect-isolation \
     --output run-workloads/native-terrain-b15-r1 --scenes forest \
     --effect-archive lighting-tier-no-atmosphere
   ```

   Keep DH present; do not pass `--without-dh`. Preparation creates additional
   effect controls, but select only the four table arms for this matrix. The
   preparer verifies source provenance, original/candidate hashes and attribution,
   preserves upstream defaults/options, locks the source save while copying, and
   records equal initial world fingerprints. This is source qualification, not
   proof of runtime DH coverage.
3. With all profile worlds stopped and locked through `unlocked_world`, clone
   the newly prepared `forest/adaptive` into **new** `forest/geometry`. Exclude
   transient session locks, logs, screenshots and old workload request/measurement
   files. Check `world_fingerprint` on the new copy against adaptive and the new
   source manifest. Use these existing helpers from `setup_comparison.py`; do
   not mistake the source fingerprint from an earlier launch for current bytes.
   Do not run `prepare_diagnostics.py`: it is hard-coded to an older root, clones
   original-high, and rewrites preset/warmup rather than producing this B15 arm.
4. Preserve every non-terrain config value and shader option from adaptive.
   Make only these geometry settings explicit in its existing TOML:

   ```toml
   [terrain]
   enabled = true
   vanillaChunkCap = 16
   warmSourceConfirmed = true
   warmupSeconds = 30
   ```

   The true acknowledgement is an explicitly forced **manual warmed-scene
   diagnostic**, confined to this inspected fixed camera/save. Record who
   inspected/acknowledged it and the retained DH ring images. It is not an
   automatic coverage result or a recommended setting for users. Configure the
   first three arms with terrain disabled/acknowledgement false, cap16 and the
   same30-second policy warmup. Keep global enabled only for adaptive/geometry.
5. Ensure the shared `forest/control.json` contains no `enabled`,
   `terrainEnabled`, `terrainChunkCap` or `warmSourceConfirmed` overrides.
   `ComparisonClient.applyControl` would otherwise overwrite per-arm config.
   Preserve camera/time/weather/preset/debug controls identically. Keep raw
   `options.txt` requested render distance24, DH TOML radius128 and all DH mesh
   settings identical. Explicitly set all rendering experiments false in every
   mod-bearing arm: optimizeShadowCulling, separateShadowVisibilityLists and
   shadowFrustumCoherence.
6. Extend the new workload manifest with the geometry copied-world fingerprint,
   profile name and its identical adaptive `quality_options`. Record the B15
   hash, exact config delta, cap16, forced manual acknowledgement and planned
   requested/effective radii. Describe the prepared collection as mixed
   shader/geometry controls (`shader_only=false`), not a shader-only optimization;
   preserve original source provenance. Record geometry as an explicit per-arm
   entry rather than implying every profile enables it. Hash all final inputs
   after these changes and before launch.

For all three B15 arms require CLOUDS/WATER/FOG/SSR/MATERIALS literal true,
USE_MOD_CONFIG/PRESERVE_LIGHTING true, and CACHED_SKY/WATER_ANALYTIC/DEBUG false.
The complete effective map must match across those arms; global enable is a
mod config value, not a changed pack option. Preserve original shadow/AO work:
audited current forest options include shadowMapResolution2048,
shadowDistance128, PCF min6/max12, screen-space shadow steps10, GTAO slices2 and
horizon steps3. Their unchanged values and protected-lighting option are required;
they do not guarantee unchanged shadows after geometry replacement.

## Inspect the warmed scene before timing

Use an expendable inspection copy, preserving the timing copies and source
save. At the fixed camera, inspect original24/DH128 and the acknowledged
policy24→16/DH128 with B15 protected lighting. Verify real near/distant DH land
and the replacement ring from16 to24 chunks, tree silhouettes/shadows, water,
the vanilla/DH seam, fog and entity-view changes. A loaded mod list, database
bounds, DH128 setting, renderer readiness or elapsed warmup does not prove that
the ring exists at the required detail.

Existing inspected-scene commands are `/shaderlod terrain cap 16`,
`/shaderlod terrain enabled true`, `/shaderlod terrain confirm` and
`/shaderlod terrain revoke`. Setting the cap revokes acknowledgement; apply it
before confirming. Confirmation uses cached eligibility and never bypasses
warmup. `/shaderlod enabled false` or policy disable should restore effective24;
restore the full requested24 setting if any inspection changed it. Capture
restoration and re-confirmation separately from timed windows.

The source guard binds acknowledgement to world/pipeline/camera identity,
horizontal chunk/vertical section, requested radius, cap and DH reach. Persisted
true can bind the first eligible startup scene after stable warmup. Travel,
reload, readiness loss or relevant config changes revoke it; returning does not
re-arm it. Do not force re-confirmation during a timing window. Each fresh JVM
must settle independently. Cold caches or failed coverage inspection stop the
geometry trial; do not reduce the cap below16, disable guards or choose an easier
camera to rescue its timings.

## Bounded independent-JVM measurement

After inspection, freeze production/dev classes and resources, archives,
configs, controls and profile inputs. Do not use `benchmarkFeature=effects` for
this geometry comparison: its invariants explicitly forbid any terrain policy.
Use serial workload captures, separate JVMs and the four existing runner cases.

First clone the complete prepared four-arm collection into a separate stopped
pilot root, for example `run-workloads/native-terrain-b15-pilot-r1`, with verified
equal initial save fingerprints and matching controls/configs/manifests. Preserve
the main prepared collection untouched. Retain a one-block four-arm pilot
with75-second initial warmup and30-second capture, GPU1/16 and fixedwetness0. The longer warmup allows
the30-second policy gate plus post-cap mesh/upload settling; elapsed time alone
does not certify settling. If effective16/coverage/settling is not established,
reject the pilot and retain it. Adjust an explicitly recorded warmup protocol
for every arm in a fresh pilot, never an individual favorable run.

After a qualified pilot, run four blocks/16 JVMs. Four conditions with four
Williams blocks balance positions; the existing runner generates the actual
order. Keep the qualification pilot separate from the planned repeated result.
For a dry-run and then the runtime owner's matrix:

```sh
python3 scripts/run_proof_matrix.py \
  forest-high forest-patched-off forest-adaptive forest-geometry \
  --profiles-root run-workloads/native-terrain-b15-r1 --stacks minimal \
  --dh present --window-scale 2 --sample-interval 16 --fixed-wetness 0 \
  --warmup 75 --duration 30 --repeats 4 --tag photon-native-terrain-b15-r1 \
  --skip-build --dry-run
python3 scripts/run_proof_matrix.py \
  forest-high forest-patched-off forest-adaptive forest-geometry \
  --profiles-root run-workloads/native-terrain-b15-r1 --stacks minimal \
  --dh present --window-scale 2 --sample-interval 16 --fixed-wetness 0 \
  --warmup 75 --duration 30 --repeats 4 --tag photon-native-terrain-b15-r1 \
  --skip-build
```

`--skip-build` assumes the coordinator previously checked/froze the current
compiled runtime; it does not establish build success. For the pilot use its
separate `--profiles-root`, `--repeats 1` and a distinct new pilot tag. Pilot
launches change save/cache bytes; do not reuse those altered copies as the
repeated matrix's starting saves. Reverify the untouched main collection before
the four-block launch. Minimum planned warmup/capture time
is28minutes for16 runs, plus startup/shutdown. Run no other clients, compilers,
GPU probes or heavy preparation during the matrix. Keep every UUID, log,
report, screenshot, summary, failure and source/class fingerprint.

## Accept or reject the actual terrain arm

The current runner verifies stable report inputs but **does not require the
geometry arm to become effective16**. Its exit0 and default analysis alone are
insufficient. Before using any timings, review every retained geometry capture:

- Both boundary input states report requested24, effective16 and
  `terrain_active=true`; non-geometry arms report effective24/inactive terrain.
  Require `geometry_policy.active=true`, cap16, requestedChunks24,
  effectiveChunks16 and stableTicks≥warmupTicks. Reject an inactive/warming/
  unconfirmed arm even if the runner accepts it.
- Confirm loaded config/control provenance enables only the planned geometry
  arm and retains the manual true acknowledgement. Ordinary WorkloadSession
  captures a live LOD snapshot and raw config identities, rather than the full
  loaded semantic config available in in-process controls; preserve stopped
  config bytes and verify their captured hashes before parsing them.
- Confirm actual2976×1672 in every run, camera/time/weather/preset and hidden-HUD
  policy, fixed wetness supplier evidence, matching sampling/backend and stable
  visible, non-minimized window/pacing. Preserve frame limit/vsync/focus evidence
  and reject differing or contaminated capture windows.
- Require real Iris rendering without fallback/errors, exact original/B15
  hashes, full effective option maps and matching base runtime versions/artifacts
  apart from the intentional ShaderLOD presence difference in original-high.
  Any exact API Base equivalence must be separately qualified under its existing
  narrow registry, retaining raw variants; same version alone is insufficient.
- Verify DH128 and all DH config/mesh settings unchanged. Confirm actual DH
  rendering and replacement-ring images separately; an API-ready reason alone
  does not establish submissions or per-pixel coverage. Reject visible holes,
  unacceptable distant tree/shadow/water changes or unresolved rebuilding/upload
  activity. Boundary state equality does not prove stability between boundaries.
- Require all16 planned runs and pair each contrast within its block. Retain
  failures; a complete fresh rerun replaces a rejected matrix for acceptance,
  without deleting it or selecting only favorable blocks.

## Attribution and remaining product choices

Report three independent contrasts using matched block pairs:

| Contrast | Meaning |
| --- | --- |
| Adaptive versus patched-off | Shader/global-enable increment at unchanged24/DH128; separate from earlier correlated one-JVM signals |
| Geometry versus adaptive | Existing16-chunk detail/DH replacement tradeoff, with identical B15/options/global enable |
| Geometry versus original-high | Combined product configuration versus original; includes source/mod, shader and geometry differences |

Also retain patched-off versus original-high as the disabled/source/runtime
control. The default analyzer uses original-high automatically; run it again
with `--baseline forest-adaptive` for the geometry increment and
`--baseline forest-patched-off` for the shader increment, into distinct retained
analysis files. It annotates changed effective radius/terrain state. Its
geometry quality-option differences are annotated rather than strictly refused,
so manually require **no** shader-option or DH quality change for geometry versus
adaptive before interpreting those pairs.

Use per-block percent time reductions and independent-run median/range/MAD;
keep GPU mean/tail, CPU wall/thread and FPS-derived frame duration separate.
Do not add shader and geometry percentages, pool frames for confidence intervals,
or count a combined≥15% geometry tradeoff as a new shader optimization. Preserve
negative source/JVM controls and cross-JVM variation. A promising fixed forest
combined result needs a second DH-present scene with the same quality/evidence
checks; the existing no-DH coastal shader control cannot validate terrain policy.

No major product choice changes here. Default terrain opt-in, the human's
acceptable detail/shadow/water tradeoff, and automatic coverage during travel
remain pending. Smooth movement within a guard cell is not coverage proof;
third-person offsets, edits, mesh-quality changes and cache resets are additional
known limits. Any travel/restoration quality run is a separate untimed experiment,
with explicit reinspection and re-acknowledgement. This plan neither authorizes
default promotion nor declares the sustained-performance goal complete.

## Preparation and acceptance source handoff

The bounded implementation is now `scripts/prepare_native_terrain_profiles.py`
and `tests/test_native_terrain_profiles.py`, plus geometry-only validation in
`run_proof_matrix.py`. This source handoff supersedes the earlier manual
preparation recipe and the earlier missing effective16 runner guard. No Java,
shader helper, policy, default or cap-range change was made.

The new preparer preserves the stopped high/patched-off/adaptive shader/config
sources, verifies exact original/B15 archive hashes, license/build metadata,
retained full effective option evidence, protected all-five-active switches,
unchanged upstream quality, requested24, matching DH128/mesh settings and the
pinned DH profile jar. It creates geometry from the **new adaptive profile**.
All output saves are restored from one locked, stopped high-source world and
verified against its newly computed fingerprint. Prior per-arm source save
differences remain source provenance; they are not silently used as different
starting worlds. Source config/archive/report hashes are rechecked after copying.

Controls lose per-profile enable/terrain overrides. Only the geometry config
enables cap16 and the explicit manual acknowledgement; every arm keeps the same
30-second policy warmup and CPU experiments false. The output manifest records
four explicit arms, exact configs/options, copied save hashes and pending runtime
acceptance. Existing destinations are rejected, and a failed partial preparation
is retained with failure status. Missing/empty canonical DH cache is rejected;
nonempty database metadata still does not prove replacement-ring coverage.

Run only after the coordinator verifies all clients stopped and has inspected
the fixed warmed scene. The acknowledgement switch is deliberate, not an
automatic cache-coverage assertion:

```sh
python3 scripts/prepare_native_terrain_profiles.py \
  --source-root run-workloads/native-effect-isolation \
  --output run-workloads/native-terrain-b15-r1 \
  --acknowledge-warmed-scene --dry-run
python3 scripts/prepare_native_terrain_profiles.py \
  --source-root run-workloads/native-effect-isolation \
  --output run-workloads/native-terrain-b15-r1 \
  --acknowledge-warmed-scene
```

Dry-run is a pure path/arm plan: it reads no source archives or saves and creates
no profiles. Actual preparation refuses a live Minecraft process and holds all
three source world locks. Use the separate pilot/inspection copies and the
frozen four-block runtime command above after the coordinator qualifies the
prepared inputs. Preparation does not build, compile or launch anything.

Every `*geometry` workload capture now requires actual mod/DH presence, no
shared-control overrides, and both boundary states reporting global enabled,
active terrain, requested24 and effective16. The exported end policy must be
active with cap16/requested24/effective16, warmupTicks600 and stableTicks at least
600, plus its active eligibility reason. Missing/zero/inactive/warming policy,
effective24 restoration, changed DH radius or missing/active CPU experiment
evidence rejects the geometry capture. Non-geometry validation is unchanged.
Full geometry-policy snapshots exist only at report end in the current exporter;
both boundary radius/activity states are checked, without claiming unavailable
start-policy or continuous coverage observations. The current guard specifically
qualifies this cap16/30-second/DH128 diagnostic, not arbitrary terrain policies.

Source acceptance: 16 lightweight new fixtures passed, including exact pins,
fresh-output/acknowledgement guards, tiny synthetic save restoration and adaptive
geometry cloning, rejected source/config/option/DH changes, lock failure,
retained failed preparation, both boundary guards and non-geometry restoration.
Eighteen existing lightweight proof fixtures passed; the compiler-dependent
fixture was excluded under this worker's no-compiler assignment. No real shader
archive or world copy, build, client or GPU experiment was run by this owner.
The coordinator still owns actual preparation, compilation/source freeze if
needed, terrain/render/quality inspection and all pilot/repeated timing evidence.

## WAL metadata repair after the retained preparation failure

Root's real preparation91578 exited1 and retained
`run-workloads/native-terrain-b15-r1` with `New copied world differs: high`.
The initial worker metadata helper opened the canonical DH database directly
using SQLite `mode=ro`. Root reproduced that read on an isolated copy of the
real WAL-mode database: the SELECT created a zero-byte `-wal` and32768-byte
`-shm` beside the database. Source and copy later matched all230 file hashes;
the pre-query fingerprint had been taken before those sidecars existed. This
was a metadata-reader side effect, not a copy-integrity or shader result. The
guard and failed evidence correctly remained in place.

`cached_dh_metadata` now copies the database and any existing `-wal` into a
system temporary directory and queries only that private snapshot. It does not
open the source with SQLite, copy its `-shm`, delete its sidecars, exclude them
from fingerprints, or weaken the world locks/copy guards. SQLite may recreate
SHM only in the private directory; the connection closes before cleanup, also
on failure. Copying a nonempty WAL preserves its latest committed rows; blindly
using `immutable=1` would not establish that. The snapshot assumes the caller
holds the stopped world lock, as actual preparation already does. Temporary
database/query sidecars stay outside Git and are cleaned automatically.

Twenty-one lightweight fixtures now pass. Five WAL additions verify: closed
WAL-mode source without sidecars stays byte-identical through full synthetic
preparation; a metadata read creates no source sidecars; nonempty WAL-only
committed rows remain visible while all source files/hashes stay unchanged;
zero-length WAL/existing SHM remain unchanged; and private snapshots are removed
after query errors. These use tiny real SQLite fixtures without Minecraft or
any real shader/world copies. No compiler/client/GPU check was run by the worker.

Preserve failed `r1` and use a fresh output after source freeze:

```sh
python3 scripts/prepare_native_terrain_profiles.py \
  --source-root run-workloads/native-effect-isolation \
  --output run-workloads/native-terrain-b15-r2 \
  --acknowledge-warmed-scene
```

Root still owns actual preparation and all rendering, DH coverage, quality and
timing acceptance. The repair changes no runner validation, shader, Java,
terrain policy, cap, default or automatic-travel claim.
