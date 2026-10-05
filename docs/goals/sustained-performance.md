# Sustained performance and compatibility development

Created: 2026-10-04. Status: active.

## Authorization and objective

The user authorized sustained agent-team development with broad implementation
freedom, keeping Photon first, NeoForge and the current Minecraft target. Improve
performance and the mod, benchmark with/without performance mods, and expand to
other popular shader packs only after meaningful Photon gains are established.
The user will check in periodically; routine implementation choices do not
require approval. No remote publication or external messages are implied.

## Constraints

Retain Minecraft 1.21.1, NeoForge 21.1.253, Iris 1.8.12, Sodium 0.6.13,
Photon v1.3b and optional DH 3.3.3 unless a compatibility fix is documented.
Iris requires Sodium here, so the minimal shader runtime still contains both;
"without performance mods" means without additional optional optimizers.
Keep explicit pinned pack patches and honest CPU/GPU/quality distinctions.
Do not claim Mac-only results establish full desktop shader compatibility.

## Milestones and acceptance

1. Automate repeated, balanced-order serial benchmarks with options/artifact
   fingerprints, warmup, median/tail CPU/GPU/frame metrics, and contamination
   detection. Compare mod-absent original, patched disabled, and active profiles;
   include simple existing-quality controls rather than inflating the baseline.
2. Improve measured Photon bottlenecks and safe geometry/DH integration.
   Preserve salient lighting; distance transitions and camera motion must be
   inspected. Geometry remains opt-in until automatic coverage is proven.
3. Run minimal and selected optional performance-mod stacks with exact pinned
   versions/hashes, actual loading and in-world checks. Separate stack gains from
   ShaderLOD's incremental gains. Include DH-present/absent paths.
4. Require repeatable useful gains: provisional target at least 15% median frame
   or GPU render reduction in an ordinary representative scene, plus a second
   workload without material regression. Record uncertainty, CPU/GPU limits and
   visual costs. Existing manual radius changes alone do not establish a novel
   shader benefit. Refine this engineering gate from evidence, transparently.
5. Only after gate 4, choose and pin another popular, licensable shader based on
   current evidence. Add explicit integration with its own disabled/original,
   active, runtime, transition and performance checks. No generic rewriting.

## Team and runtime ownership

Coordinator owns integration, build.gradle, goal/status/decisions, interactive
clients, serial GPU experiments and final evidence. Independent workers receive
bounded files, checks and stopping boundaries. No concurrent shader reloads or
benchmark clients. Preserve world saves and generated reports outside Git.

## Initial checkpoint

Previous commit: 218992b. Last evidence: forest combined candidate 124 to 143 FPS
and GPU 7.482 to 6.319 ms; premium cloud shader-only reductions 13–29% with large
variation. Terrain cap exposes existing DH tradeoff and needs warmed-scene
confirmation. These results motivate work but do not satisfy the new repeated,
ordinary-scene and multi-stack gate by themselves.

Next: bounded implementation/research workers, followed by integrated repeat
benchmarks. Live inspection clients from the prior goal must be identified and
closed only when the coordinator begins exclusive serial measurements.

## First implementation checkpoint

Four bounded workers completed: Photon water/atmosphere candidates, conservative
DH policy safeguards, optional optimizer pinning, and repeated benchmark tooling.
Second research pass documents blockers to automatic DH coverage and later
Photon candidates. Production source and benchmark source are now frozen for
serial experiments; no other agent owns a game or GPU.

- Water analytic normal removes repeated height/noise evaluations at medium/far
  distance; near/disabled/shadow use original path. Separate water bands 96/256/32.
- Cloud defaults now 128/1024/64; lighting protection and cached-sky-off remain.
- Conditional atmosphere evaluation preserves sky and options requiring it;
  compiler may already remove work, so benefit is unverified.
- Geometry acknowledgement invalidates on travel, world/pipeline/readiness,
  request/cap and DH-range changes; hot getter restores immediately as a setting.
  Mesh rebuild completion is separate. Explicit confirm/revoke commands and
  NeoForge config screen added. Runtime travel check remains pending.
- ModernFix 5.27.24, FerriteCore 7.0.3 and ImmediatelyFast 1.6.14 loaded and
  rendered in original-Photon/DH shoreline inspection. All artifacts pinned and
  hash checked. Culling stack and DH-absent run remain pending.
- Java build and 10 geometry tests pass. 20 Python tests pass, including nine
  benchmark tests, 11 shaderpack tests and 792 GLSL compilations. Actual candidate
  Iris rendering/performance still needs serial runs.

The first matrix `dev/logs/sustained-v2-main` rejected its first run because Iris
rewrote default option files. This was a guard failure, not a performance result.
Raw evidence is preserved. The corrected tool captures loaded Iris effective
options, verifies requested values and stable canonical maps, and retains raw
byte hashes without equating normalization with changed quality.

Proof profiles: `run-workloads/photon-proof-v2/{forest,cloudstorm,coastalwater}`,
original high / patched disabled / active; all terrain policy disabled. Cloud
scene is premium full resolution; forest and shoreline use default cloud buffer
resolution. Shoreline 240.5/68/680.5, yaw 180, pitch 3 was inspected in world.
Candidate archive SHA256 999eae1c4c3551e0f02d45ae06b517c87f966b87b7ca6a8e779a16566c6a003b.

Next: rerun semantic-guarded repeated matrix across minimal and optimized stacks,
then water, terrain lifecycle and optional-DH checks. No new shader integration
until the evidence gate is met. Original preview PIDs were already stopped when
this goal began; do not reuse stale process identifiers.

## Live repeated-matrix checkpoint

Source checkpoint: 3f594b2. Corrected matrix `dev/logs/sustained-v3-main` is
running under unified exec session 11350. Coordinator owns it; do not launch
another client or compile/change frozen production/shader code until it ends.
Runner console: `dev/logs/sustained-v3-main-runner.log`. Manifest and per-run
reports are inside its directory. There are 36 runs (3 blocks, 6 conditions,
2 stacks), 35-second warmup and 20-second capture. The matrix preserves failures
and performs paired analysis only after all repeats are complete.

Early results are mixed and are NOT acceptance evidence: first minimal forest
original/disabled/active were 121.1 / 118.7 / 93.6 FPS; optimized active was 121.7.
First minimal cloud original/disabled/active were 57.4 / 59.4 / 81.2; optimized
original/disabled/active were 58.8 / 52.7 / 62.8. Do not discard poor runs or
promote best ones. Stable loaded shader options, actual camera/radii, versions
and archive fingerprints are recorded; no pipeline fallback in accepted runs.

A source-only [regression audit](../PHOTON_REGRESSION_AUDIT.md) proposes atmosphere,
analytic-normal and distance-band isolation using preserved candidate archives.
It establishes no culprit. Next continuation: wait for matrix completion,
inspect paired distributions and screenshots, then run targeted isolation/water
rather than adding more optimizations blindly. Photon performance gate and new
shader support remain pending. Native goal stays active.

## Continuation checkpoint: input audit and isolation tools

The matrix session 11350 was revalidated live and progressed through its second
block. First minimal forest slowdown did not repeat (block 2 active 120.6 FPS
versus original 113.7). Keep all repeats; no source culprit is established.

New tested preparation tool `scripts/prepare_isolations.py` creates fresh
original/disabled/active roots for analytic versus structural packs, analytic
normal on/off and current versus older water bands. Six fixture tests passed.
No actual copies or clients were run during the exclusive matrix. See
`docs/ISOLATION_WORKLOADS.md`; run preparation only after matrix termination.

Source audit exposed an omitted temporal input: Iris smoothed wetness feeds
Photon cloud humidity/wind. Initial clear-to-rain copies and later already-rainy
saves may initialize differently despite recorded rain=1. Actual wetness was not
captured, so it is a plausible mechanism rather than demonstrated cause. Treat
this matrix as exploratory until a follow-up captures/settles relevant inputs.
Do not invent precision by freezing frameTimeCounter alone: cloud geometry uses
world_age based on world day/time, already frozen by the harness.

`docs/APPLE_FEATURE_BUDGET.md` identifies a narrow future driver probe: same
reduced profile, DH absent, environment and sky reflections enabled. Iris reserves
two composite units, leaving 14 dynamic sampler slots here; source estimates
suggest SSR can fit without DH but may exceed the budget with DH. This is not
runtime support yet. Source also confirms DH water SSR executes in composite1,
not dh_water's early-return Gbuffer path. Test rather than assume sampler counts.

Next after the live matrix: retain/analyze all repeats, add wetness telemetry and
settled-weather checks, inspect images, then run targeted water/atmosphere and
DH-absent SSR probes. Production/shader sources remain frozen while matrix runs.

## Matrix completion and next implementation

Session 11350 ended with exit 0. All 36 captures and paired analysis completed.
See docs/REPEATED_RESULTS.md for retained median/range limits. Median paired GPU
reductions: forest 5.83% minimal / 1.72% optimized; premium storm 25.91% / 23.43%.
The ordinary performance gate is unmet; cloud input wetness was not captured,
so cloud values remain exploratory. No shader expansion is authorized by a
claimed gate pass yet.

The benchmark worker now owns dev-only per-registration wetness telemetry, an
optional fixed-wetness diagnostic, screenshot preservation and isolation
preparer correction. Root owns build.gradle property mapping and clients. No
Minecraft client is live; wait for worker freeze before compiling/launching.
The real isolation preparer passed shoreline preparation without DH under
run-workloads/ssr-probe/coastalwater; common ENVIRONMENT_REFLECTIONS and
SKY_REFLECTIONS are enabled on high/off/active, normal/specular mapping and
galaxy explicitly disabled. This is static preparation, not a link/render pass.
Root's attempted forest isolation dry-run exposed non-Settings include guards
in Iris's full option map; preparer correction is pending with the worker.

Next: compile worker telemetry, launch original DH-absent shoreline SSR smoke
with short matched capture and inspect driver/errors/actual reflections. Then
measure fixed-wetness cloud controls and water/atmosphere isolations, plus
geometry travel/reload restoration before recommending any new default.
