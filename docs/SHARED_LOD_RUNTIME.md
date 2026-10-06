# General LOD system runtime audit

2026-10-05. This bounded audit uses Photon v1.3b as the first explicit reference
consumer of the general `shaderlod:volume_integration` operation. It establishes
no project completion, physical integration count, parity, motion quality or
performance claim. Only the ON profile is authorized to launch here.

## Preparation

`scripts/prepare_shared_lod_runtime.py` pins the complete registered disabled ZIP
`5de7561dd24cdca80860a1d5f2c60400e74163a489cc163e916f3fd47feb27de`, manifest
`40efd7e702e41388033255d48a007c543e1055ccb4e2513c1908c2d82b8f2677` and reviewed
source freeze `bce667558457a932361bb379774bc505226d3205d8392e89980b766db7d866d3`.
Qualification compares every original/helper/member byte, complete metadata,
ten registered reference member hashes and retained license. No source detection
from a pack display name confers eligibility.

Canonical source is `run-workloads/photon-ssr-cost-controls-r1/coastalwater/high`:
report `9524f3fdc9dec30f59a03704ad45f9cffa84b2058fe2ed48ea6a3095c31ba252`,
effective options `ecedfafc0a911b154db42ed786ffc468a694097ecacfb99852efeaa3dd6a5669`,
world `89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46`.
Any drift is rejected before copying. Fresh original/off/on controls preserve
equal stopped-world fingerprints and minimal/noDH profiles. Existing copy and
canonical qualification utilities remain unedited.

All 366 upstream effective options are pinned: 354 public settings are written,
and 12 non-settings stage/include defaults retain their unchanged source values.
All 366 must match the actual loaded Iris map in the runtime audit. Only private
integration option `SHADERLOD_VOLUMETRIC_INTEGRATION_LOD=true` is added for off/on;
the global mod toggle distinguishes them. Required actual options also include
`CLOUDS_CUMULUS=true` and `BLOCKY_CLOUDS=false`.

Seven effects sections are written for every arm. Volumetrics requests ADAPTIVE,
512/1536/128 blocks and .85/.75 medium/far work ratios. Other effects request
INHERIT/unity; all preserve salient lighting. Config bytes alone establish no
linked consumption or capability availability.

Checks actually completed:

```sh
python3 -m unittest discover -s tests -p test_shared_lod_runtime.py -v
python3 scripts/prepare_shared_lod_runtime.py --output run-workloads/shared-lod-system-qa-r1
```

Five fixtures pass: exact original/reference/archive/canonical option eligibility,
member/metadata/repacked ZIP rejection, canonical world drift rejection, fresh
ignored output checks and seven effect/global controls. Actual preparation
completed with all three canonical world copies equal. Evidence:
`dev/logs/shared-lod-runtime-source-tests.log`,
`dev/logs/shared-lod-runtime-preparation.json` and
`run-workloads/shared-lod-system-qa-r1/shared-lod-preparation.json`.

## Runtime boundary and ownership

The operator reserves one interactive client/GPU. Native WINDOWED 2976×1672,
logical 1488×836, fixed coastal pose 500.5/68/660.5, yaw87.38/pitch3.735,
time12500, clear weather and wetness0. Warmup25s/capture5s, GPU/pass/CPU layer
timing false, existing cloudSamplerProbe true. The warmup-only focus watcher
must acknowledge before launch; no focus action occurs during capture.

Full prelaunch class/resource inventories are stored in
`dev/logs/shared-lod-runtime-prelaunch-inventory.json`. Parent independently
confirmed all 75 main and 107 dev class members equal the reviewed build. Its
aggregate digest uses a different serialization, with no member differences.
Runtime `development_fingerprints()` main/dev digests are
`57c8d842dd3c2f2990209333d60fd4dd92d5870afe7f4c61238a459ebef789c4` /
`2a1ce5aac3d86e67f8b64bfbc854cdd0cb969032578e2c6b9c44a17e599fc2a5`;
main/dev resource digests are
`c508ff1ff2524a15867cb317c3b2109abeca19c9b5d30d99162c7e3c1757e5f6` /
`d0892e8d32091b03b74f98ea3f0f8f4a8588989b4a5cf338aae4eb65c56fe8ee`.

Parent transferred sole GPU/client after accepting preparation. Focus watcher
exec86747/PID40557 acknowledged ARMED before launcher exec72874/PID40585.
Fresh request UUID is `7164200b-435c-4b00-8245-01d29fb0d33f`. Command, process
rows and exact stop handles are retained before/at launch in
`dev/logs/shared-lod-system-qa-r1/smoke-manifest.json` and `runtime-handles.json`:

```sh
./gradlew runClientLodOn \
  -PrunDirectory=/Users/pippo/github/shaderlod/run-workloads/shared-lod-system-qa-r1/coastalwater/adaptive \
  -PperformanceStack=minimal -PincludeDh=false -PmeasureWorkload=true -PmeasureOnly=true \
  -PcloudSamplerProbe=true -PgpuTiming=false -PgpuPassTiming=false -PcpuLayerTiming=false \
  -PinitialWarmup=25 -PmeasurementSeconds=5 -PbenchmarkWindowScale=2 -PfixedWetness=0 \
  -PgpuSampleInterval=5 -PcomparisonFullscreen=false \
  -x compileDevClientJava -x compileJava -x processResources -x processDevClientResources
```

The owned launcher uses the existing `wait_command` timeout/child-group stop
procedure. Stop only its exact exec72874 and, if still live, watcher PID40557.
Terminal runtime acceptance remains pending at this launch checkpoint.

Acceptance requires driver compile/render/no fallback, exact source/effective
options/config/class/scene/window guards and actual typed capability schema1 and
volume availability1 at valid GL_INT scalar locations. Preserve initial zero or
unknown states exactly. First-draw observation does not prove settled lifecycle;
availability0 is not replaced with configured or source-inferred values. Inspect
actual core/effect fields, sampler allocations and restored active texture state.
Inspect the native cloud still only after terminal completion. Stop after one
terminal audit and formally return GPU; no retries, candidate expansion or timing.

## Terminal result

The single ON driver/render/readback audit passed its scoped guards. Watcher
exec86747/PID40557 and launcher exec72874/PID40585 exited0. The Minecraft client
ended naturally; no live client or owned process remains. The operator formally
returned sole client/GPU ownership to root. No second arm, retry or timing run
was launched. Original/off profiles remain unlaunched with canonical worlds
unchanged; source files/world and compiled classes/resources also remain equal.

Minecraft1.21.1 / NeoForge21.1.253 / Iris1.8.12-snapshot+mc1.21.1-local /
Sodium0.6.13+mc1.21.1 / ShaderLOD0.1.0-dev rendered the pinned consumer in world,
without fallback or DH. This is stronger than a title-screen launch; it supplies
no DH terrain rendering evidence. Actual loaded Iris and Sodium SHA512 match
the runtime lock, and all loaded artifact SHA256 values are preserved in the
audit. Hardware is Apple M3 Max, OpenGL4.1 Metal91.7, GLSL4.10, macOS27.0 and
Oracle Java21.0.9+7-LTS-338. The original Photon source/version/license pins remain
part of complete archive qualification.

Capture 2026-10-06T00:42:29.463124Z–00:42:34.504286Z held WINDOWED physical
2976×1672 and logical1488×836. All102 observations were available, visible,
non-iconified and unfocused, with zero capture transitions, unchanged frame
limit260/vsyncfalse/pause-on-lost-focusfalse. The watcher acted only at
00:42:05.777949Z, during warmup. Pose, time12500, clear weather, fixed actual
wetness0 and detailed24-chunk radius pass. All366 upstream options plus the
sole integration=true addition match the actual effective Iris map exactly;
effective hash `829e9f4dcdbc698c9fb31c8c1373bd5e0f81ef3968c2a3392f30b268477b95eb`.
NeoForge regenerated configuration comments/layout; both parsed core/effects
structures exactly match preparation, and actual linked volume values confirm
the requested interface fields separately.

The source-inferred cloud consumer fullscreen program18 was sampled at its first
observed draw, 00:42:04.661913Z. The report supplies schemas core2/effects2/
capabilities1; program linked, inventory bounded without truncation, no probe
failure. Actual integer/float getters supply the following values, with scalar
size1 and valid locations:

| Actual linked field | GL type | Value | Location |
| --- | --- | --- | --- |
| shaderlodCapabilitiesVersion | GL_INT5124 | 1 | 37 |
| shaderlodVolumeIntegrationAvailable | GL_INT5124 | 1 | 5 |
| shaderlodBridgeVersion | GL_INT5124 | 2 | 21 |
| shaderlodEnabled | GL_INT5124 | 1 | 22 |
| shaderlodNear / Far / Transition | GL_FLOAT5126 | 32 / 96 / 8 | 49 / 33 / 59 |
| shaderlodEffectsVersion | GL_INT5124 | 2 | 42 |
| shaderlodVolumetricsMode | GL_INT5124 | 2 (ADAPTIVE) | 34 |
| shaderlodVolumetricsNear / Far / Transition | GL_FLOAT5126 | 512 / 1536 / 128 | 44 / 15 / 20 |
| shaderlodVolumetricsMediumWorkRatio / FarWorkRatio | GL_FLOAT5126 | .85 / .75 | 11 / 69 |

Core preset/debug and the unrelated OpaqueLighting fields are unknown/inactive
in this program. The other27 observed programs have unknown/inactive capability
fields; they are not converted to unavailable values. No availability0 was read
at a valid location in this run. Actual availability1 and exact source/options
eligibility do not certify physical integrations or settled/reload behavior.

Actual cloud density textures customtex1/unit4/object54 and
customtex2/unit5/object55 allocate64³ R8 (33321); customtex0/unit6/object56
allocates32×64×32 RGB16F (34843). Actual noisetex/unit7/object57 allocates512²
RGBA8 (32856). These use sampler0, LINEAR MIN/MAG, base/max0, min/maxLOD0,
bias0, anisotropy1. The numeric aliases are tied to the separately pinned source;
allocation proves neither image content nor texture completeness. Active texture
33984 is restored in program18, and before/after state matches in all28 captures.
Getters do not consume `glGetError`. No shader compilation, fallback, probe or
GL-error marker occurred; retained warnings include unused vertex attributes,
optional absent-mod/mixin references, Photon option menu entries and Apple's
`gldCopyBufferSubData: NEEDS IMPLEMENTATION` diagnostic.

Native2976×1672 `smoke-final.png` was inspected at original resolution after the
client stopped: clouds, fog, terrain and water render without obvious gross
holes or stripes. This is one still; no matched baseline/off comparison, motion,
flicker, parity or quality acceptance follows. GPU/pass/CPU layer timing is off;
no FPS/CPU/GPU performance inference is made.

Exact command/request/handles, workload, effective inputs, typed maps, allocations,
log qualifications and raw hashes are preserved in
`dev/logs/shared-lod-system-qa-r1/shared-lod-terminal-audit.json`.
The read-only audit ran once with exit0:

```sh
python3 dev/logs/shared-lod-system-qa-r1-audit.py
```

Raw console, workload, sampler state, focus action, manifest, PNG and audit output
remain in `dev/logs/shared-lod-system-qa-r1/`. The next useful separately reviewed
system step is matched original/off/on physical-work/parity/quality evidence and
then repeated quiet performance comparisons. This owner stops at the completed
bounded audit; the unchanged1.5× throughput goal remains unachieved.

## Legacy preparer pin maintenance

After GPU return, root's six legacy profile fixtures retained one strict rejection:
`Frozen feature manifest changed`, logged in
`build/shared-lod-legacy-profile-check.log`. The explicit volume family now pins
the reviewed shared-system manifest40efd7… / ZIP5de756… and complete metadata
`b7ce3bac5c13e35e13ce2e58a0bc1fb5bf6fc628d2a5f6b8cafc83f3107c0761`, with four
explicit volume-budget/adapter/effect/work helpers. Whole archive/member/helper/
license/metadata checks remain strict; no wildcard exception was added. Added
fixtures assert capability schema1, the general volume operation, unity INHERIT,
required options/reference members and rejection of altered capability/unity/
field metadata or shared helper bytes. Other historical families are unchanged.

`python3 -m unittest discover -s tests -p test_parallel_lod_profiles.py -v`
passes all six fixtures; scoped `git diff --check` also passes. Fresh output is
`build/shared-lod-legacy-profile-refresh-check.log`. The old failed log remains
preserved. No client/build/GPU operation occurred; root retains resource ownership.
The old standalone volume runtime is not inherited by the shared-source qualifier.
Combined/fullscreen three-track experiments remain set aside without refresh or
launch. This follow-up stops at tested pin maintenance.

## Current public extension lifecycle verification, r2

Root integrated the public runtime extension API in commit000f355 and reports
`./gradlew test build compileDevClientJava` passed103 tests with no failures,
errors or skips. That source change requires one fresh lifecycle check; the r1
93-test revision remains historical evidence. Current production JAR SHA256
`aa65cf9157e0c5dac7db6ea65d38a42b3eec06a71838e394389063ead5b3e672` was checked
against actual disk bytes. Shader ZIP5de756…, manifest40efd7…, all helpers,
options and marcher remain unchanged; no source/contract edits are part of r2.

Fresh preparation at `run-workloads/shared-lod-system-qa-r2` completed with all
original/off/on worlds equal to canonical89c962… and the same seven-effect
controls. Preparation evidence is `dev/logs/shared-lod-runtime-r2-preparation.json`.
`development_fingerprints()` inventory is frozen at
`dev/logs/shared-lod-runtime-r2-prelaunch-inventory.json`:79 main class members,
main tree `735689306c38ffef8f266fa1801cb41a01df639877ef8ef8f140be178ce8831e`;
107 dev members, tree2a1ce5aa…; main/dev resourcesc508ff1f…/d0892e8d… unchanged.

Root transferred sole GPU/client for one ON windowed25s/5s observer smoke with
the identical canonical coast/native2976×1672/logical1488×836/minimalnoDH/wetness0
setup. Watcher exec81739/PID43240 acknowledged before launcher
exec33338/PID43256; Gradle wrapper PID43258 and client Java PID43270. Fresh request
UUID `c19f5021-62b2-4558-9bdd-55332dda4f50`. Command is exactly the r1 command above
with only its profile path changed to `shared-lod-system-qa-r2`; main/dev
compilation/resource tasks remain excluded. Exact prelaunch command/request and
handles are recorded in `dev/logs/shared-lod-system-qa-r2/smoke-manifest.json`
and `runtime-handles.json`. Stop only launcher exec33338 via the existing
child-group stop procedure, and watcher PID43240 if still live. Terminal r2
readback/render/state validation was pending at this launch checkpoint.

The single r2 audit completed with exit0. Watcher81739/PID43240,
launcher33338/PID43256, Gradle wrapper43258 and client Java43270 are all stopped;
exact PID queries and the exclusive-runtime utility report no live client.
Sole GPU/client ownership formally returned to root. No preparer/test/source/
contract change was needed, and no build or extra launch occurred.

Current-revision first-draw program18 at00:59:23.363852Z read scalar GL_INT
capability schema1/location37 and volume availability1/location5. All actual
core/effect values, types and locations match the r1 table above:
core2/enable1/32/96/8 and effects2/ADAPTIVE2/512/1536/128/.85/.75. Neither field
had a typed actual0 observation; the other27 programs retain unknown/inactive
capability fields. This verifies the current public-extension revision's built-in
startup registration through actual rendered uniform values. Third-party addon
runtime registration, later settled/reload state and physical work remain
unverified; source tests for an extension do not establish its rendered behavior.

Capture00:59:48.493526Z–00:59:53.498617Z passed all101 visible/non-iconified/
unfocused observations with native2976×1672/logical1488×836, unchanged windowed
pacing and no capture transitions. The warmup-only focus action occurred at
00:59:23.978718Z. Source files/world, unlaunched original/off copies and all
compiled class/resource inventories remain equal to prelaunch. Actual366upstream
options plus sole integration=true, source ZIP, core/effect config semantics,
pose/time/weather/wetness0/native24 and minimalnoDH guards all pass. The identical
sampler allocations/parameters and active-texture restoration pass in all28
captured programs. Driver renders in world with no fallback or shader/probe/GL
error marker; retained warning qualifications match r1.

The native still was inspected after terminal completion without gross cloud/
terrain/water holes or stripes. PNG SHA256
`9ec460417654253818bbae56ec1c53f83802a34ad1f1f8eb368251e824c70a94`.
No comparison/motion/parity/physical-work/quality/performance result is claimed.
The sole terminal audit command was
`python3 dev/logs/shared-lod-system-qa-r2-audit.py`; its output and exact current
revision/JAR/class provenance, maps, state, logs and PNG are retained in
`dev/logs/shared-lod-system-qa-r2/shared-lod-terminal-audit.json` and the adjacent
raw artifacts. The1.5× throughput goal remains unachieved.
