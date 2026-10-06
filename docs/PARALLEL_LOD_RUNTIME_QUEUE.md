# Parallel ShaderLOD implementation: runtime QA queue

2026-10-05 source checkpoint. User direction is independent implementation across
several LOD/rendering areas with one serial GPU owner. Root owns integration and
the client/GPU until explicit handoff. Runtime operator owns only
`scripts/prepare_parallel_lod_profiles.py`, its new test module and this document.
No real profile/world preparation, client, build, shader assembly or GPU operation
was performed during this freeze. Six queue preparation fixtures pass; downloads,
source/parser checks and assembly are separate from driver/rendering acceptance.

Root integrated main interfaces V2 at `0a4d7a9` with 60 Java tests/build/dev compile
passing, preserving the old dirty fullscreen/ComparisonClient edits. Runtime
must record current class/resource/JAR fingerprints per launch; old AO/density
class hashes must not be reused as evidence for this integrated producer.

## Untimed feature order

| Priority/family | Source | First bounded evidence | Limit/boundary |
| --- | --- | --- | --- |
| 1 reflection | canonical coastal glint | pack compile/render, sole active gate, five typed core readbacks where full-screen consumer is captured; inspect near/far reflections and depth binding | conservative source footprint error is not quality or fetch-count proof |
| 2 procedural | same coast | pack/water compile, gate, full native image and glint/near-water review; noisetex state in actual applicable observer scope | old observer sees only full-screen draws, so water-raster core binding is initially unobserved; normal variance/energy unaccepted |
| 3 volume | cloud-visible coast; forest available separately | compile, five core values, effect version/mode/bands/ratios linked inventory and recorded requested policy, applicable 3D/local coverage sampling | existing observer does not read effect values; actual step reduction, inside/overhead and motion quality remain unproved |
| lighting-cost, after root assembly | forest rough receivers/coast | one normal debug0 compile/core smoke; optional fresh debug1 eligibility cue and shadow state | deliberate coefficient/AO/shadow changes; not production LOD quality; separate from lighting-rate worktree implementation |

Implementers keep coding/testing in owned worktrees during these GPU-timing-false
smokes. No FPS, CPU/GPU cost, benefit or steady-state conclusion follows from
these runs. Getter instrumentation can stall. Class/resources/selected archives
must not change within each run; snapshot and reject changes. Only one client,
reload/world and GPU operator at once. No other-pack/Fabric implementation is
added. Root reviews this preparation freeze before the first real copy/client.

## Exact frozen families

| Family | Manifest SHA-256 | Default-off archive SHA-256 |
| --- | --- | --- |
| reflection r2 | `0c0905612ddacf358b92c2d112bb17f2d107de224551d418a6a67c8529ccf4ec` | `485590fa1a70ce43e4fe271a8a19f869d0cbf69767f1bda435b5deed9e41aa35` |
| volume | `08e1611a77a2f6d6364b91eda692ea2e300b1c3c2891c87d7abd6ecec98def9b` | `3685e47e94cd08f3994053f300981bbdd92ae108a65f7fadb700bd6d9422a6b5` |
| procedural, parser-repaired r3 | `b529fdb01aabef5670128e04a25224bfd4368c2a63641214023c352099d100e2` | `267f6358e87b8dd51e4e5a6fcf26f999344f8e1abf79fb36c5bd99fa0d7bd9ea` |
| lighting-cost cue | `630dffa44bbe096412295e2755edf8989ca881b246a0fbb73cff8c778d450ac9` | root assembly pending; computed only after complete source qualification |

The corrected procedural freeze supersedes d504/894c: old partial discovery
fixtures missed include-guard options. The owner now reports actual complete
52/55-include-graph Iris parser coverage, sole public gate, equal upstream string
options and 13 passing fixtures. The qualifier unblocks only the exact corrected
manifest/helpers/archive/metadata, with no widened option allowance.

All use exact original Photon v1.3b archive SHA-256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`,
unchanged original LICENSE SHA-256
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
The explicit immutable family table invokes only the four reviewed pure source
patchers. It independently fixes member scope, verifies frozen manifest bytes,
recomputes exact original-derived members, compares every actual upstream member,
helper/source hash and license, checks complete metadata and default-off option,
then checks the ready archive pin. This is not an arbitrary ZIP whitelist or
universal shader rewriting system. Pending lighting assembly still requires its
entire reviewed eligibility/cue/sampler/coefficient/quality metadata contract.

Reflection changes two members and adds its one cache helper; volume changes two
and adds the shared budget plus explicit Photon adapter; procedural changes two
and adds three water/footprint/parallax helpers; lighting-cost changes three with
no helper. All other upstream members are byte-equal. Full ready metadata byte
hashes are pinned in the table so policy/activation/fallback/quality claims cannot
be silently relabeled. Source helper changes require a new reviewed freeze.

## Scenes, configs and preparation

Use original stopped `run-workloads/photon-ssr-cost-controls-r1/coastalwater/high`
or `run-workloads/native-terrain-b15-r2/forest/high`. Exact report/loaded-option
pins remain respectively 9524f3/ ecedfa and 6f7662/4efc7a (full hashes in code).
Both report actual native 2976×1672 and requested/effective 24. Coast pose remains
x500.5/y68/z660.5/yaw87.38/pitch3.735/time12500; forest remains
x256.5/y95/z768.5/yaw135/pitch9/time7000. Clear weather/Overworld, fixed shader
wetness0 and source PERFORMANCE mod preset. Upstream options, budgets, map flags,
resource packs and Minecraft options are unchanged. Coast has enabled ordinary
reflections, water waves/parallax with three octaves and visible sunset clouds;
no preset reduction is introduced to manufacture work savings.

Shared existing stopped-save locks/canonical-copy loop is reused through its
explicit inspector callback. Source report/options/control/config/archive/helper/
resource inputs are hashed, equal copied worlds verified, histories retained,
fresh DH removal scoped to the qualified forest jar, and source equality checked
afterward. Existing outputs are rejected; partial failures are kept. After copies,
fresh requested effects/control files are recorded with updated hashes and a
preparing marker until complete; sources remain unchanged. All output belongs
under ignored build/ or run-workloads/.

Each family prepares original/high (mod absent), selected private/global-off and
selected private/global-on controls using the same private gate=true in both
patched arms. Initial smoke launches only adaptive. Fresh effects config records
all **seven** V2 effects INHERIT/unity/protected lighting, including opaque_lighting.
Volume INHERIT explicitly uses its adapter's .85/.75 retained ratios with core
bands 32/96/8; loaded file unity does not mean its INHERIT consumer uses unity.
Optional `--volume-mode adaptive` requests only volumetrics .85/.75 at
512/1536/128; record that separately and verify actual linked values when an
observer supports them. No density/extinction coefficient is scaled by ratios.
Optional `--debug-cue` applies only to lighting-cost, in a fresh root/control
with debug=true; normal debug0 uses a separate profile/provenance.

Source fixture commands and planned first preparation (not executed):

```sh
python3 -m unittest discover -s tests -p test_parallel_lod_profiles.py -v
python3 scripts/prepare_parallel_lod_profiles.py --family reflection \
  --output run-workloads/parallel-lod-reflection-qa-r1 --dry-run
# Only after root review/handoff:
python3 scripts/prepare_parallel_lod_profiles.py --family reflection \
  --output run-workloads/parallel-lod-reflection-qa-r1
```

Six fixtures cover actual frozen ready source/helper/license/metadata verification,
read-only canonical forest/coast option qualification, injected member/helper and
metadata rejection, strict pending lighting metadata, seven neutral/conservative
requested configs and tiny canonical copies with complete new control hashes.
No real-world clone, production assembly, Java or GLSL compiler/client runs in
this fixture suite. Existing copy helper/builders/tests/classes are unedited.

## Launch and evidence boundaries

Use the existing WorkloadSession, fresh UUID/request and `profile_inputs`, frozen
pre/post main/dev/resource fingerprints, minimal stack/noDH and current V2 main
mod. Arm warmup-only focus before launch, no directory precreation; require actual
capture visible/non-iconified/unfocused and unchanged pacing, plus native24.
Use 25s warmup/5s capture, GPU timing false, CPU layers/pass timers false and fixed
wetness0. `cloudSamplerProbe=true` is useful for full-screen core/linked-inventory/
sampler checks; it is diagnostic, not performance sampling. Record exact command,
exec/PID/stop handles, pack/manifest/helper hashes, producer class hashes, GPU/driver/
OS/Java, requested configs and actual reports in this queue before continuing.

Planned launch shape for the first adaptive profile:

```sh
./gradlew runClientLodOn -PrunDirectory=ABSOLUTE_FRESH_ADAPTIVE_PROFILE \
  -PperformanceStack=minimal -PincludeDh=false -PmeasureWorkload=true -PmeasureOnly=true \
  -PinitialWarmup=25 -PmeasurementSeconds=5 -PgpuTiming=false \
  -PgpuPassTiming=false -PcpuLayerTiming=false -PcloudSamplerProbe=true \
  -PbenchmarkWindowScale=2 -PfixedWetness=0 -x compileDevClientJava
```

Old observer reads core seven values plus all active linked-uniform inventory;
it does not read the 43 effect-interface values and does not observe water raster
draws. Registration/config/interface version2 and typed linked names do not prove
actual GPU extension version/mode/ratio values or selected work. Observer updates
are independently owned and root integrates/rebuilds before any client that uses
them; record their current class fingerprints/actual supported scope per smoke.
Source-inferred numeric program identity, first draw versus settled state and
unobserved/inactive fields must remain explicit. No new counter/harness is added
by this queue. Sampler allocation/compare state is unverified until a capable
actual observer records it; source dimensions are not actual allocations.

After each terminal JVM, validate request/archive/options (sole new gate), config,
camera/time/radius/native viewport, actual mod/dependency/fallback, window/focus,
temporal wetness, class stability and fresh PNG/hash. Inspect appropriate near/far
reflection, water-glint, cloud/edge and material behavior; smoke stills are not
motion/temporal/full quality acceptance. Preserve failures, stop only owned
processes and return root GPU ownership. Record per-family result with build,
title-screen, in-world compile/render, linkage/config, quality and performance
as separate statuses. No DH terrain result follows from noDH.

## Later performance batch

User requests true fullscreen where possible. Existing dirty compiled hook is
`-PcomparisonFullscreen=true`; observe actual fullscreen/window/framebuffer/pacing,
do not equate scale2 with fullscreen. A fullscreen change may change dimensions:
freeze a new matched baseline/control set at its actual resolution before timing,
rather than relabeling old 2976×1672 windowed evidence. The first bounded source-
native smoke and later fullscreen qualification are distinct evidence.

Only root schedules a dedicated measured batch after candidate QA: same **current**
main/dev classes across original/off/on, fixed fullscreen/dimensions/scene/focus,
balanced reverse order, repeated total-frame and asynchronous GPU intervals,
matched query-off frame control, retained failures and variability. During those
measured windows other local CPU tests/builds/assembly/encoders stop. Root may
later assemble combined adapters with explicit source conflict/parity review;
these standalone qualifiers do not certify arbitrary merges. The 50% target,
quality and compatibility acceptance remain unachieved by this queue.

## First reflection QA live handles

Root reviewed/passed six preparation fixtures and explicitly handed the sole
client/GPU slot to runtime. Root integrated observer `6a4d835` and raster hook
`610f7ce`: 60 Java tests/build/dev compile, dev resources/classpath and three
actual raster-hook fixtures passed. Other agents may continue CPU work during
this untimed GPU-false smoke; no cost inference is allowed.

Actual preparation exec `79834` exited 0 at
`run-workloads/parallel-reflection-qa-r1`; source/all copied canonical world
SHA-256 is `89c9629693e97f7e0c12d41a9ef2cd5a4fa82cd9585644b2dbf2b32bab8c7b46`.
Reflection archive/manifest/helper/metadata pins above qualified. Only adaptive
is launched; high/disabled copies remain unopened. Focus watcher exec `54250`,
PID `95954`, acknowledged ARMED before launch, with no output mkdir.

Single-smoke ignored launcher `dev/logs/parallel-reflection-qa-r1-launch.py` runs
existing request/wait/WorkloadSession validators; it is not a new source harness.
Launcher exec `47437`, PID `96022`; Java PID `96042`; fresh UUID
`08fed9f1-43df-4a6b-ada7-0d6160bc5cfb`. Exact raw request, command, source/config/
world provenance and pre-class fingerprints are retained in
`dev/logs/parallel-reflection-qa-r1/smoke-manifest.json`.

```sh
./gradlew runClientLodOn \
  -PrunDirectory=/Users/pippo/github/shaderlod/run-workloads/parallel-reflection-qa-r1/coastalwater/adaptive \
  -PperformanceStack=minimal -PincludeDh=false -PmeasureWorkload=true -PmeasureOnly=true \
  -PcloudSamplerProbe=true -PgpuTiming=false -PgpuPassTiming=false -PcpuLayerTiming=false \
  -PinitialWarmup=25 -PmeasurementSeconds=5 -PbenchmarkWindowScale=2 \
  -PfixedWetness=0 -PgpuSampleInterval=5 -x compileDevClientJava
```

Pre-launch main class tree is
`17286dd585d5c1b34f2f0332a869a3bb6920a52d021c84d6bf3ae48732eef9f3`;
dev class tree `a5e7d5f8d30bd4d4b275c933b3d155522ce09cfca07f7d786928465685f8330f`;
dev resources `d0892e8d32091b03b74f98ea3f0f8f4a8588989b4a5cf338aae4eb65c56fe8ee`.
Stop only exact launcher exec/PID, Java `96042` if needed, or watcher
exec/PID; existing helper handles its own child-group interruption/timeout.
Raw console/workload/sampler/PNG and focus actions are under the same ignored
log root. Driver/core/sampler/quality validation remains pending until terminal.

### Reflection QA terminal

Preparation `79834`, watcher `54250`/PID95954 and launcher `47437`/PID96022 exited0;
Java96042 stopped naturally. All original366 options plus sole gate=true, UUID,
source/archive/helper/metadata/license, actual native2976×1672/radius24, fixed pose/
weather/time/wetness, visible/non-iconified/unfocused capture, PNG/hash and current
producer class/resource guards pass. GPU samples are empty; CPU work continued
elsewhere and no performance inference is made. Raw audit is
`dev/logs/parallel-reflection-qa-r1/reflection-qa-audit.json`.

Driver compiled/rendered the selected pack with no fallback. Observer captured28
programs with core/effect schema2 and both draw origins. First fullscreen ID21 at
23:12:06.824927Z and raster ID458 at23:12:07.702483Z each expose GL_INT scalar
version2/enabled1 and GL_FLOAT scalar near32/far96/width8 at valid locations;
both link, scans are untruncated and active texture state restores. Other core/
effect fields are inactive/unknown as declared by this reflection-only consumer.
The seven-effect config is INHERIT/unity/protected; registration/source V2 and
that file are not an effect-consumer proof. No probe/graphics failure marker;
OpenAL shutdown warning remains in the raw console.

Observed noisetex allocation512²/RGBA8 is LINEAR/base0/LOD0/aniso1, sampler0.
Shadowtex1 allocation2048²/depth is LINEAR/LEQUAL comparison, sampler0; its
base/max0/1000 and broad LOD range are bounds, not allocated mip proof. Depthtex1
is NEAREST/no comparison/depth32F: first fullscreen allocation1708×960 preceded
viewport resize, while raster allocation2976×1672 matches native capture.
These are first-draw states, not settled-frame/reload histories or physical
reflection fetch/early-termination counts. Program identities are source-inferred,
not names directly observed by the numeric hooks.

The full image was viewed resized; exact native-pixel glint, near-water and
far-shore crops were additionally inspected after terminal using bundled Pillow,
without changing raw pixels. No obvious broad straight bright-water band was
seen; glint, water detail and shore reflections remain visible. One active still
does not establish original/off/on parity, projected-error bounds, movement,
thin-edge/transition quality or performance. Raw PNG/crops remain ignored.
Capture23:12:32.415641Z–23:12:37.457934Z; focus action23:12:08.486972Z returned0
before warmup ended. All owned processes absent and GPU returned to root;
root accepted this outcome and separately authorizes the next volume smoke.

## Volume explicit-ADAPTIVE QA live handles

Root separately handed the sole client/GPU slot for one untimed volume smoke.
Actual preparation exec `98690` exited0 at `run-workloads/parallel-volume-qa-r1`,
family volume exact3685e47... with unchanged canonical89c962... worlds and
explicit requested Volumetrics ADAPTIVE512/1536/128, medium .85/far .75. Other
six effects remain INHERIT/unity/protected. Only adaptive launches; source files
and other profiles remain unchanged.

Warmup watcher exec `93401`/PID99841 acknowledged ARMED before launch.
Launcher exec `56444`/PID99847, Java99874; request UUID
`cb2a68f1-3f6e-44e6-8044-612694069c36`. Raw command/request/input/class provenance,
console/workload/sampler/PNG are under `dev/logs/parallel-volume-qa-r1/`.
Exact launch command is the prior reflection command with runDirectory replaced
by `/Users/pippo/github/shaderlod/run-workloads/parallel-volume-qa-r1/coastalwater/adaptive`;
25warm5capture, GPUfalse/CPUlayersfalse/cloudSamplerProbe, native2/minimal/noDH/
wetness0/frozen dev classes remain. Stop only these exact exec/PID handles.
Pre-class/resource fingerprints equal the reflection run. Source workers continue
CPU work; no FPS/CPU/GPU inference. Driver/actual effect values/samplers pending.

### Volume QA terminal and GPU return

Preparation98690, watcher93401/PID99841, launcher56444/PID99847 exited0; Java99874
stopped naturally, all clients absent and class/resource trees unchanged. Request/
archive/source/helper/license/fullmetadata, sole new true gate over original366
options, native2976×1672/radius24/coast pose/time/weather/wetness/config/PNG and
visible/non-iconified/unfocused capture guards pass. GPU samples0; source CPU
work continued, and no performance metric is inferred. Focus action
23:25:54.740446Z preceded capture23:26:19.137187Z–23:26:24.180215Z with102
unfocused/unknown0/no-transition observations. Raw additive audit is
`dev/logs/parallel-volume-qa-r1/volume-qa-audit.json`.

Driver compiled/rendered without fallback. First fullscreen numeric program18
at23:25:53.375999Z actually reads scalar core2/1/near32/far96/width8 plus:

| Actual effect field | Type | Location | Value |
| --- | --- | --- | --- |
| EffectsVersion | GL_INT | 40 | 2 |
| VolumetricsMode | GL_INT | 33 | 2 (ADAPTIVE) |
| VolumetricsNear | GL_FLOAT | 42 | 512 |
| VolumetricsFar | GL_FLOAT | 14 | 1536 |
| VolumetricsTransition | GL_FLOAT | 19 | 128 |
| VolumetricsMediumWorkRatio | GL_FLOAT | 10 | .85 |
| VolumetricsFarWorkRatio | GL_FLOAT | 67 | .75 |

All actual `shaderlod`-prefixed uniforms have size1, valid locations and queried
values; this proves sampled V2 producer→shader linkage, beyond a loaded-file
inference. Linked/scan/state-restore/probe checks pass; other unused core/Opaque
fields remain unknown/inactive. The source-inferred main-cloud first draw predates
settled camera/warmup; this is not a reload/history/iteration-count proof. Near,
inside/above, overhead, footprint and count floors may dispatch original work.

Actual customtex1 and customtex2 scalar3D bindings allocate64×64×64, R8/internal
33321, with LINEAR MIN/MAG, base/max0, LOD0, bias0, anisotropy1, sampler0. The
third customtex0 is32×64×32/internal34843; it is a distinct lookup, not mislabeled
64³ noise. Allocation is level0 only; content/mip completeness and exact remapped
source-file identity are not observed. Active texture restores and no capture/
probe failure was found. Raw states and locations are preserved.

Full native image plus exact cloud-edge/thin-cloud crops reviewed after terminal
show no obvious holes or gross cloud failure. Visible grain/edge texture cannot
be attributed to the new marcher from an unmatched active still. No variable-
medium, off/on parity, motion/wind, inside/overhead or temporal quality acceptance,
physical iteration savings or performance benefit follows. The frozen pack stays
private/default off. Root receives sole GPU ownership again; no procedural,
combined, cue, timing or repeat client is launched without its next handoff.

## Procedural corrected-r2 QA live handles

Root explicitly hands the sole GPU/client slot for one fresh procedural smoke.
Preparation61151 exited0 at `run-workloads/parallel-procedural-qa-r1`, exact
default-offeb9fa7.../manifest5849... plus fully verified three helpers/metadata;
canonical source/copies89c962... unchanged. Original3 water octaves, parallax,
waves and displacement true are preserved, with only private gate=true added.
The shader consumes only core version/enable; requested core32/96/8 remains
config/capture evidence, not a claimed procedural distance-policy connection.

Watcher8692/PID5994 ARMED before launcher8612/PID5996; UUID
`d6b4ba77-e2f7-4141-bb39-331c1ec10894`. All main/dev/resource prehashes equal
reflection/volume. Raw log/request/class/provenance/workload/sampler/PNG paths
are `dev/logs/parallel-procedural-qa-r1/`, with identical preceding smoke command
except runDirectory
`/Users/pippo/github/shaderlod/run-workloads/parallel-procedural-qa-r1/coastalwater/adaptive`.
Only adaptive, 25warm5capture, GPUfalse/CPUlayersfalse/new observer/native2/
minimal/noDH/wetness0/frozenclasses. Stop only these exact owned handles.
Other agents continue source CPU work; performance and terminal QA pending.

### Procedural r2 rejected; GPU returned

Launcher8612/PID5996 exited1 after the existing wait helper rejected shader
pipeline/fallback markers. Watcher8692/PID5994 exited0; no clients remain and
main/dev/resource fingerprints are unchanged. The client stopped naturally;
its transient OS PID was not captured before completion. No second launch or
timing occurred. Root retook GPU ownership and assigned isolated parser repair.

Exact Iris transform failure (twice, console lines298–299 and549–550):

```text
terrain_translucent: org.antlr.v4.runtime.misc.ParseCancellationException:
line 14969:77 missing RPAREN at '{'
TransformPatcher.transformInternal(TransformPatcher.java:225)
```

This is a GLSL transform/parser failure before successful shader pipeline
acceptance. Actual runtime reported shadersInUse=false/fallback=true, despite
valid frozen pack/source preparation and intended gate=true. Preserve existing
disabled-cloud-shadow preprocessor warnings separately; the decisive error is
the translucent program parser rejection. No procedural core value/sampler or
shader quality proof is inferred from fallback or partial-program inventories.
Configured32/96/8 is not shader transport here: the source only consumes core
version/enable and hardcodes full near32..48, octave .125..25 cycles/rasterpixel
and parallax expansion2..4 guards. None of that policy was driver-accepted.

Raw console/manifest/workload/sampler/focus and a copied fallback PNG remain
under `dev/logs/parallel-procedural-qa-r1/`, with additive
`procedural-rejected-audit.json`; source/provenance/failed profile remain at
`run-workloads/parallel-procedural-qa-r1/`. No preprocessed/patched shader dump
was found by rg --files in that fresh profile. Exact log/context/path was sent
to `procedural_parser_repair`; no secret/environment data was included.
No private ShaderLOD performance, rendering, binding, motion or parity acceptance
is claimed. Root validates/reviews a new parser/source freeze and fresh retry
scope; runtime holds no process and does not launch another candidate itself.

## Procedural r3 terminal retry

Root integrated exact repair6eb12b1; strict pins now b529fdb.../267f6358... and
complete metadata3d4d75e47541d80ad3850a511c9b72101741233efb8cb426eccb6ec79bfb11f7.
Default-off consumed fields remain[]; source-selected version/enable are recorded
separately. Six queue qualification fixtures pass. Reflection r2 pins were also
updated for valid all-family source tests, with no new reflection client. Rejected
r2 evidence stays preserved and is not reclassified.

Fresh prep6367 exited0 at `run-workloads/parallel-procedural-qa-r3`, canonical
89c962... copies and source3octaves/parallax true unchanged. Watcher57388/PID20602
ARMED before launcher26601/PID20612; Java20626; UUID
`8260f845-dc54-4148-8daa-65309797c1a1`. Same exact preceding smoke command with
r3 adaptive runDirectory, 25warm5/GPUfalse/CPUlayersfalse/newobserver/native2/
minimalnoDH/wetness0. MAIN17286dd.../DEVa5e7.../devresourcesd089... remained frozen.
Watcher and launcher exited0; Java stopped naturally, all clients absent.

Driver now compiles/renders without fallback. All original366 options plus sole
gate=true, UUID/native/radius/pose/time/weather/config/temporal/PNG/class guards
pass. Raster numeric458 at23:55:10.694137Z reads GL_INT scalar version2/location26
and enabled1/location27. Other core/effect fields remain unknown/inactive; the
shader does not consume configured32/96/8. Actual source policy stays full
near32..48, .125..25 cycles/rasterpixel and parallax expansion2..4 restoration.
No core-distance-mod→water-band connection is fabricated.

Actual raster noisetex unit6/object57 allocates512² RGBA8/internal32856, LINEAR
MIN/MAG, base/max0, LOD0/bias0/aniso1/sampler0; active texture restored and scans/
probe/linked checks pass. First-draw source-inferred water scope is not a settled
reload or physical octave/fetch/trigonometry counter. Native full image and exact
glint/near-water/far-water crops inspected after terminal show no obvious broad
bright band or holes; variance, glint energy, matched off/on, movement and temporal
quality remain unaccepted. No FPS/CPU/GPU inference; source workers continued.

Raw command/provenance/log/workload/sampler/PNG/crops plus
`procedural-r3-qa-audit.json` remain in `dev/logs/parallel-procedural-qa-r3/`.
Formal GPU ownership returns to root with no owned processes. No second retry,
lighting/combined/timing client was launched. The next separately authorized
action is the original forest true-fullscreen qualification after this return.

## Original fullscreen measurement-baseline qualification

User clarified that the extensible general LOD system is the product, with
Photon only its first adapter/test case. Runtime continues only the separately
authorized original forest fullscreen baseline; no Photon-specific micro-
optimization/candidate/timing follows without shared-system integration review.

Initial master preparation failed before directory reservation: serialized
original path was str passed to file_hash(Path). Owner repaired and source-tested
Path normalization plus the real inspector callback, committed92e958e; nine new
and19 legacy fixtures passed. No copy/client occurred in that rejection. Retried
prep34747 exited0 at `run-workloads/fullscreen-lod-batch-master-r1`, bothscenes/
12arms; full original/combined/source/helper/metadata qualification and class
guards pass. Forest worldf752.../coast89c962... all canonical equal, noDH copies.
Masters remain untouched; corrected candidate driver-block markers are retained.

Independent stopped original forest copy at
`run-workloads/fullscreen-lod-original-smoke-r1/forest/high` preserves exact
world/control/manifest/config/pack/file hashes, strips requests/logs/reports/
captures/sampler history and records `original-copy-provenance.json`. Masters
remain equal/unlaunched. Root authorized only this original query-free smoke:

```sh
python3 scripts/run_proof_matrix.py forest-high \
  --profiles-root run-workloads/fullscreen-lod-original-smoke-r1 \
  --stacks minimal --dh absent --fullscreen --window-scale 1 \
  --sample-interval 5 --fixed-wetness 0 --warmup 25 --duration 5 --repeats 1 \
  --no-gpu-queries --skip-build --freeze-dev-classes --tag fullscreen-lod-original-smoke-r1
```

Runner45970/PID29578; Java29602; only original120897.../modabsent. Current class/
resource prehashes unchanged from the above QA. All observers/layer/pass/GPU
timers disabled by the reviewed runner; no watcher, Codex activation, resize or
focus action. Natural game foreground is left intact; actual fullscreen/focus/
dimensions/pacing are discovered from the report, not assumed from the flag.
Raw output is `dev/logs/fullscreen-lod-original-smoke-r1/`; stop only those exact
owned exec/PID handles. This is untimed qualification, not FPS or gain evidence.

### Original fullscreen rejected and stopped

Runner45970 exited1 on `Runtime inputs changed during measurement`; Java29602
ended naturally, all clients absent and class/resource hashes unchanged. Capture
00:12:29.716725Z–00:12:34.734365Z had only framebuffer boundary changes:
1920×1200→3840×2400. Logical1920×1200/fullscreentrue/pacing260-vsyncfalse stayed;
at elapsed26.973143s, focus/visibility became false and iconifiedtrue. There were
22 visible-focused and71 hidden-iconified-unfocused observations. No operator
focus/activation/resize action occurred; cause is not established. No actual
fullscreen reference or performance result is accepted from this attempt.

Original120897.../366options/mainmodabsent/minimalnoDH/rendering-noFallback,
GPU0/all observersfalse/CPUlayersfalse and PNG preservation remain verified,
separately from the failed display-state contract. Masters/source provenance and
raw failed matrix/report/console/PNG plus `fullscreen-rejected-audit.json` remain
in the named ignored roots; initial preparation bug evidence is also saved as
`master-preparation-preflight-failure.txt`. No failure is relabeled or removed.
Root accepts rejection, retakes sole GPU ownership, and authorizes no retry or
candidate/timing. Future general-system measurement must settle Retina physical
dimensions and preserve visible focus before capture under a new reviewed plan.
Runtime holds no process; shared-system/source development continues independently.
