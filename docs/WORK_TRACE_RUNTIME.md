# Work-trace runtime diagnostic

2026-10-05. R2 completed original-body/global-OFF and shared-body/ADAPTIVE raw
work capture with all strict guards passing. Counts are diagnostic observations;
no fully synchronized cross-arm association, quality or speed claim is accepted.

The historical R1 first bounded original-body launch failed Iris pipeline
construction. The shared-body arm was not launched, and no retry was made.
Actual primary, density, secondary and texture counts remain **unavailable**.
This is a driver/runtime failure, not a zero-work result, speed measurement,
quality result or goal acceptance.

[Preparation/runner](../scripts/prepare_work_trace_runtime.py) owns fresh targets,
exact frozen registration qualification, diagnostic controls and terminal audit.
It reuses the stopped canonical-world inspector and copy policy read-only.
[Five fixtures](../tests/test_work_trace_runtime.py) pass; raw test log is
`build/work-trace-runtime-prep-tests.log`. No project build was run by this task.

## R1 frozen inputs and planned arms

Fresh profiles are under `run-workloads/work-trace-runtime-r1/coastalwater/`.
Complete hashes, all archive-member inventories, exact registration bytes,
requested/expected option maps and profile input inventories are retained in
`run-workloads/work-trace-runtime-r1/work-trace-preparation.json`.
Ledger: `build/photon-work-trace-freeze.json`. Source freeze revision: `6794654`.

| Arm | Selected diagnostic ZIP SHA-256 | Control | Runtime result |
| --- | --- | --- | --- |
| Original body | `4683c68bf5aca1d9fec4ae049bf72548ff6e4d2553f7ea11ce077a2b9332a25a` | ShaderLOD present, global OFF | Pipeline rejected |
| Shared body | `bd5c5a115a3aa62cb1c08af4b504ed29c1db88ed32d87b39eb3b7b39e535826c` | ShaderLOD present, global ON; volumetrics ADAPTIVE | Unlaunched |

Original registration SHA-256:
`5499b399232e9ced1a7a5844d609094c9b9f96b25d6bd414b1f12c14149717ba`.
Shared registration SHA-256:
`f19b3779aebd92213edc67cdea5ab0baf5219ae365850c88b5894e5b3015f5bf`.
Both archive variants default tracing ON. Shared additionally requests
`SHADERLOD_VOLUMETRIC_INTEGRATION_LOD=true`; its private default remains OFF.
The expected runtime maps retain all 366 canonical upstream options, plus only
`SHADERLOD_WORK_TRACE=true` and the shared integration gate where applicable.
These are preparation expectations: loaded Iris option equality was not verified
because the launch was stopped before the workload report.

Both worlds were copied from stopped canonical coastal source
`run-workloads/photon-ssr-cost-controls-r1/coastalwater/high`, SHA-256
`89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46`.
Planned matched controls: native window2976×1672 framebuffer/logical1488×836,
radius24, noDH/minimal, camera500.5/68/660.5 yaw87.38 pitch3.735, day12500,
clear weather, fixed wetness0, warmup25s/capture5s. Seven-effect config requests
volumetrics512/1536 blocks, transition128 blocks, medium0.85/far0.75, preserve
salient lighting; all other effects INHERIT/unity. Both main-mod bridges are
required for development pre-pipeline trace registration.

[Schema1](WORK_TRACE_API.md) is distinct from core2, effects2 and capability1.
Fragment output locations/GL attachments0/1/2 map to Photon
colortex9/10/11 and schema pages0/1/2, respectively. Counts, budget/path metadata
and validity metadata are separate pages. Diagnostic checkerboard index and r1
phase are fixed to zero by the frozen adapter. World age, wind, camera,
projection, TAA and coverage remain temporal inputs requiring true association;
no cross-arm comparison can be made from this failed run.

## R1 launch and blocker

GPU/client ownership transferred from root before launch and returned after
terminal process/inventory verification. Exact evidence directory:
`dev/logs/work-trace-runtime-r1/`.

- Runner exec session40210, PID51947; Gradle launcher PID/session51980.
- Minecraft PID51994, parent Gradle daemon30675, process group30675.
- Request UUID `c4656a31-6340-4ac2-b4cd-4862be89f7c2`.
- Early warmup focus watcher was armed before launch and executed `open -a Codex`
  at `2026-10-06T01:51:58.121195+00:00`; no capture began.

Preparation:

```sh
python3 scripts/prepare_work_trace_runtime.py --output run-workloads/work-trace-runtime-r1
python3 scripts/prepare_work_trace_runtime.py --run --profiles run-workloads/work-trace-runtime-r1 --output dev/logs/work-trace-runtime-r1
```

The exact original client command recorded before/at launch:

```sh
./gradlew runClientLodOn \
  -PrunDirectory=/Users/pippo/github/shaderlod/run-workloads/work-trace-runtime-r1/coastalwater/original \
  -PperformanceStack=minimal -PincludeDh=false -PmeasureWorkload=true -PmeasureOnly=true \
  -PinitialWarmup=25 -PmeasurementSeconds=5 -PbenchmarkWindowScale=2 \
  -PcomparisonFullscreen=false -PfixedWetness=0 -PworkTrace=true -PworkTraceOnly=true \
  -PworkTraceMetadata=/Users/pippo/github/shaderlod/build/Photon-WorkTrace-original-on.registration.json \
  -PworkTraceOutput=/Users/pippo/github/shaderlod/dev/logs/work-trace-runtime-r1/original-raw-work-trace.json \
  -PgpuTiming=false -PcpuTiming=false -PcpuLayerTiming=false -PgpuPassTiming=false \
  -PcloudSamplerProbe=false -PdhDrawEvidence=false -PwaterSceneSurvey=false \
  -PshadowObserve=false -PvisibilityLists=false -PshadowCoherenceOracle=false \
  -x compileJava -x compileDevClientJava -x processResources -x processDevClientResources
```

Hardware/logged runtime: Apple M3 Max, Apple OpenGL4.1 Metal91.7, LWJGL3.3.3+5,
Minecraft1.21.1, NeoForge21.1.253, Iris1.8.12-snapshot+mc1.21.1-local,
Sodium0.6.13+mc1.21.1, ShaderLOD0.1.0-dev, traced Photonv1.3b; DH absent.

`original-console.log` lines173–176 and265–268 retain the actual exception:

```text
Failed to create shader rendering pipeline, disabling shaders!
java.lang.IllegalStateException: Pass sizes must match for drawbuffers [11, 12]
Original width: 427 New width: 1708 Original height: 240 New height: 960
```

The exception originates in pinned Iris `CompositeRenderer.<init>` line146.
At line309 the runtime logs `shadersInUse=false, fallback=true` after loading
the world. This is an in-world fallback failure, not a successful shader check
or a DH terrain check. The observed427×240/1708×960 are pipeline-construction
sizes, preceding the requested final native window; no capture extent passed.

Read-only archive inspection finds `program/d1_clouds.fsh` declares targets9/10/11,
while upstream `program/d2_clouds_upscaling.fsh` still declares11/12 (or11/12/15).
The diagnostic resize makes colortex11 cloud resolution; colortex12 remains
full resolution. This is a concrete downstream attachment-size conflict to
resolve before another launch. No shader/helper/archive/class changes were made
by the runtime operator.

The raw trace header retains `captureWindowFinished=false`, empty `captures`,
`drawsObserved=0`, `matchingDraws=0`. Formats/extents, raw same-draw pages,
decoded counts, descriptor availability, marker type/value and state restoration
remain **unverified**. GL error flags are deliberately unobserved by the capture
API; no claim of GL-error-free execution follows from a log alone.

## R1 terminal ownership and next action

Driver failure was observed during warmup. The operator verified then sent
SIGTERM only to owned Minecraft PID51994. The client ran under the pre-existing
Gradle daemon's group, so the daemon group was preserved. The launcher/runner
ended; no second arm started. `manual-driver-stop.json` records the exact process
row and stop action. The runner now detects fatal logs before waiting for a full
capture and stops the exact exclusively owned client PID separately from its
launcher group.

`work-trace-terminal-audit.json` remains FAILED and records the specific driver
blocker, no counts, diagnostic_only=true/performanceEvidence=false, exact command,
request and frozen inventories. PID51947/51980/51994 are absent; no Minecraft
client remains. GPU/client ownership returned to root. Existing Gradle daemon30675
is not owned by this operation and was not stopped.

Current actual frozen inventories:114 dev classes,79 main classes,2 dev resource
files and2 main resource files. Full member hashes/digests are in the terminal
audit and remain identical after the run. Production JAR stays
`aa65cf9157e0c5dac7db6ea65d38a42b3eec06a71838e394389063ead5b3e672`.
Canonical source and unlaunched shared-world fingerprints remain89c962…c7b46.

Next useful action belongs to the source/adapter owner: preserve this failure,
resolve the downstream11/12 size conflict without changing trace units or page
identity, refreeze exact archives/registrations, then request a new bounded
original-first runtime assignment. This assignment is finished at its failure
stopping boundary. No quality, speed or project-completion claim is made.


## R2 corrected layout and terminal capture

Root authorized one bounded retry after the source owner repaired and refroze the
layout. R1 reports, worlds and failures remain preserved. The current preparer
explicitly pins the reviewed corrected manifest and both enabled archive and
registration hashes. It checks the four guarded11/12/15 extent blocks, complete
member inventories and unchanged9/10/11 trace-page mapping; no gate was relaxed.

Corrected manifest SHA-256:
`23ae2d36851b71a4a8f60981a428a535f3687420da34b471c17b3a43c45a4a2f`.

| R2 arm | Diagnostic ZIP SHA-256 | Registration SHA-256 |
| --- | --- | --- |
| Original | `89e0a379b9b6a4eeda07fa564c209fdd8487420e114a1e3aa4ffa4620ddd1b70` | `a144222e52b8b88847e84c395cb13706c3feca6a3687a9a0969bcce9754d6b68` |
| Shared | `e8519e91eb463379ec8ecc17658fb22f4130ae9ca72971b900f2ebb343970088` | `b27ee4d831aa44b03481a65d21abee7c86e47cee87cf688d31409f1b9c69471f` |

Preparation and fixtures:

```sh
python3 -m unittest discover -s tests -p test_work_trace_runtime.py -v
python3 scripts/prepare_work_trace_runtime.py --output run-workloads/work-trace-runtime-r2
python3 scripts/prepare_work_trace_runtime.py --run --profiles run-workloads/work-trace-runtime-r2 --output dev/logs/work-trace-runtime-r2
```

Five fixtures passed (`build/work-trace-runtime-r2-prep-tests.log`); strict prep
completed (`build/work-trace-runtime-r2-preparation.log`). R2 uses the R1 exact
client command above with only runDirectory/output changed from r1 to r2, then
original profile/registration/output changed to adaptive/shared/adaptive for the
second arm. Exact complete command arrays and fresh requests are preserved in
`dev/logs/work-trace-runtime-r2/work-trace-terminal-audit.json`. Both use
`runClientLodOn` with global OFF/ON supplied by config. All GPU/CPU timing and
build tasks remain disabled; the workload reports contain empty GPU and CPU
sample arrays and diagnostic_only=true/performanceEvidence=false. No duration,
FPS, wall row, query or trace throughput is promoted as performance evidence.

Ownership: root transferred the idle GPU before the original launch. Tool exec
session51973, runner54046. Original launcher/session54049, Minecraft54061;
UUID `fa69b168-49ea-4066-92f3-9d9ebe46f544`. Original completed all strict raw
capture gates before shared was started. Shared launcher/session54213,
Minecraft54231; UUID `c27269a4-1be1-470b-ae99-06c9c742279f`. Both client process
records name parent/group30675 (pre-existing Gradle daemon). Both warmup focus
watchers were armed before launch and completed before capture. Launchers,
clients and runner exited naturally with code0; no forced stop or third launch.

Both arms rendered in the Overworld without fallback or DH. Actual loaded
versions are Minecraft1.21.1, NeoForge21.1.253, Iris1.8.12-snapshot+mc1.21.1-local,
Sodium0.6.13+mc1.21.1, ShaderLOD0.1.0-dev, Photonv1.3b diagnostic derivative;
Apple M3 Max/OpenGL4.1 Metal91.7. Exact loaded artifact inventories match across
arms; Iris/Sodium actual loaded hashes match pinned runtime bytes/SHA-512, and
the minimal optional-mod inventory passes the existing validator.

Loaded effective maps equal exactly366 inherited canonical options plus trace
(original367), or trace and shared integration(shared368). Mod presence,
global OFF/ON, stable camera/control/input state, native2976×1672/logical1488×836,
radius 24, unfocused/visible/non-iconified window with no transitions, fixed
wetness0 and warmup25s/capture5s all passed. Per-effect config semantics stayed
exactly as prepared. These are separate checks from actual selected work below.

All three same-draw images are actual RGBA32F(format34836), mip0,744×418, covering
viewport[0,0,744,418]. Their ordered target handles differ as expected across
arms; page identities remain0/1/2. Actual scalar GL_INT marker reads1 at a valid
location. Capture selects the registered exact descriptor/operation through its
runtime eligibility guard, then checks current producer program/FBO, draw
mapping, masks, blend/test state, complete target and real texture allocations.
Raw pixels decode VALID_SCHEMA_1 throughout: no missing/unknown/sentinel,
fractional-integer, unsupported-path/reason or schema errors. Every restoration
attempt returned without exception; no restoreErrors exists. This proves the
capture's restoration checks, not a separate before/after GL-state snapshot.
GL error flags remain deliberately unobserved.

| Arm | Captures | Valid sampled pixels | Primary | Density | Secondary | Texture evaluations | Paths |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Original | 6 | 96 | 1254 | 2454 | 1200 | 6318 | 96 original |
| Shared ADAPTIVE | 5 | 80 | 965 | 1925 | 960 | 4975 | 60 original/fallback,20 adapted |

These totals have different snapshot denominators and must not be compared as
matched work reductions. Counts overlap by declared unit and are not summed
into a universal work total. Per-coordinate counts/budgets were identical across
all snapshots **within each arm**. The central cloud row selected adapted
path2 with continuous targets31.45 or33.17345–33.17346, while protected/fallback
rays retain path1 and N/N/N. At sampled coordinate465,261:

| Separate arm observation | Original budget | Selected target | Primary reached | Density entries | Secondary visits | Texture evaluations |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Original body | 37 | 37 | 37 | 37 | 0 | 69 |
| Shared adapted body | 37 | 31.45 | 32 | 40 | 8 | 82 |

This directly records a lower selected budget alongside more density and
secondary operations in the separate shared observations. It is consistent
with the existing resampling/occupancy hypothesis; causality is unproved.

Per-draw gameTime/frameTimeCounter records do not match between arms; there are
zero exact recorded temporal pairs. `frameCounter` is inactive/UNKNOWN in the
linked producer because the diagnostic phase is fixed. Both boundary reports
show Photon world_age source625.0 at start/end, matching locked day12500; wind
age is therefore matched at those boundaries. No per-draw world_age, projection,
TAA offset, coverage/depth or wind-uniform snapshots establish fully synchronized
ray inputs. No causal cross-arm comparison is admitted. Stable repeated counts
within each arm do not replace that association.

Raw evidence under `dev/logs/work-trace-runtime-r2/`:

- `original-raw-work-trace.json`, `adaptive-raw-work-trace.json`: untouched same-draw pages.
- `original-workload-measurement.json`, `adaptive-workload-measurement.json`: loaded options/inputs/window/runtime evidence.
- `original-console.log`, `adaptive-console.log`: driver/program launch and shutdown logs.
- `work-trace-coordinate-diagnostic.json`: derived per-coordinate ranges, original input hashes and temporal limits.
- `work-trace-terminal-audit.json`: exact launch commands/requests/handles, full frozen class/resource inventories and terminal verification.

R2 final inventory equality passed:114dev/79main classes and2/2resources.
Production JARaa65cf…b3e672 and stopped canonical world89c962…c7b46 remain
unchanged. No owned process or Minecraft client remains; GPU/client formally
returned to root. Raw capture, driver rendering and selected work are verified;
quality, DH rendering, performance and the1.5× project goal remain unverified.


## Count denominators and remaining input dependencies

The16 raster coordinates per draw include8zero-primary rays. Normalizing each
arm to one16-coordinate snapshot (all snapshots repeat exact per-coordinate
values) gives separate descriptive counts:

| Arm | Primary per snapshot / per sampled ray | Density per snapshot / per sampled ray | Secondary per snapshot / per sampled ray | Texture per snapshot / per sampled ray |
| --- | --- | --- | --- | --- |
| Original | 209 /13.0625 | 409 /25.5625 | 200 /12.5 | 1053 /65.8125 |
| Shared | 193 /12.0625 | 385 /24.0625 | 192 /12 | 995 /62.1875 |

These normalized summaries correct the six-versus-five snapshot denominator.
They remain unpaired observations; no reduction percentage or causal effect is
accepted. Exact per-coordinate observations are more useful than the aggregate
for explaining where secondary work appeared.

The pinned source's private phase changes only two operations in Overworld
`d1_clouds.fsh`: checkerboard selection becomes`clouds_checkerboard_offsets[0]`,
and primary dither becomes`r1(0,dither)` after the existing noisetex read. It does
not change cumulus wind, density/coverage samples, matrices, terrain depth,
weather parameters or secondary positions. Both original and adapted density
wind use`world_age`; local coverage also adds`wind_velocity*world_age`.
`shaders.properties` defines that clock as
`((worldDay %128)*24000.0+worldTime)/20.0`. Both reported boundaries have
worldDay0/worldTime12500/world_age625.0. The changing captured worldGameTime is
total world ticks and must not be mistaken for this fixed Photon wind clock.
`frameTimeCounter` remains active in the actual producer and differs across the
JVMs; included aurora/sky calculations consume it, while the cumulus density wind
uses world_age instead. Fixed diagnostic phase does not freeze those functions.

Ray generation still reads`depth_max_4x4(depthtex1,taau_render_scale)`, transforms
through`gbufferProjectionInverse` and`gbufferModelViewInverse`, and uses
`eyeAltitude`. Its`screen_to_view_space(screen_pos,false)` call explicitly
skips subtraction of taa_offset. The upstream jitter_x/y properties still use
real frameCounter, and terrain/depth generation remains unfrozen. No actual
per-draw matrices or depth/coverage content were captured. Secondary dithering
is`hash2(fract(ray_pos))`, so different adapted ray positions can reach different
density/secondary work despite the same fixed primary phase. Sun/moon direction,
vertex-generated`CloudsParameters/get_weather()`, sampler content and weather
uniforms also remain producer inputs; endpoint controls do not substitute for
same-draw observation of every dependency.

The three pages at one coordinate in one actual cloud draw are legitimately
associated, with schema/count/budget fields validated as a single record. Within
an arm, repeated coordinates can be summarized as repeated raster observations.
Across arms, coordinates share raster position, source-fixed checkerboard/r1
phase and verified static scene/settings, but complete per-draw ray inputs are
not proven equal. Zero exact gameTime/frameTimeCounter pairs were found; even a
clock match alone would not prove matrix/depth/coverage equality. The report
therefore contains no fully matched original/adaptive ray pair. Source member
hashes and dependency details are retained in the derived coordinate diagnostic.
