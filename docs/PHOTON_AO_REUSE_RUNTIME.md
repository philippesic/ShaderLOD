# Photon AO consecutive depth reuse runtime plan

2026-10-05 source checkpoint. Runtime ownership remains with root. This owner
changed only `scripts/prepare_photon_ao_reuse_profiles.py`, its new test module,
and this document. No production archive, real profile/save copy, compiler,
client, shader reload or GPU measurement was performed by this owner. Seven
preparation fixtures passed, including full archive/member/source qualification
against a temporary test ZIP and read-only checks of both inspected source
presets. Temporary fixtures are deleted by the tests. Driver compilation,
output parity, physical fetch avoidance and benefit are unverified.

The private probe comes from literal original Photon v1.3b, with only GTAO and
settings modified. Its default-off `SHADERLOD_AO_DEPTH_REUSE_PROBE` controls
consecutive valid-coordinate depth-texel reuse, retaining upstream sample budgets
and math. When compiled in, bridge version 2 and global enabled exactly 1 select
the private kernel; other values select the literal original kernel. The new
per-effect policy/ratio interface is not consumed by this probe. This is adjacent
work avoidance evidence for the general LOD goal, not an accepted LOD gain.

Source manifest `patches/photon-experiments/ao-depth-reuse-probe.json` is frozen at
SHA-256 `0a017ece0d9c9080e727879fc1dbdad312efb6385139a63bc1ba23560aa9729a`.
Original archive SHA-256 is
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
The preparer recomputes the reviewed default-off patch, compares every upstream
member, rejects added/missing/unsafe/duplicate members, verifies the unchanged
license, and checks private metadata and both changed member hashes. The only
extra archive member is `shaderlod-ao-depth-reuse-probe.json`. Private archive
SHA-256 is computed and recorded after this qualification; it is not accepted
from an arbitrary archive whitelist. Metadata `consumed_core_fields=[]` describes
the default-off archive; preparation separately records version/global fields
consumed under the identical explicit true override in both patched arms.

## Inputs and arms

| Scene | Inspected stopped source | Fixed pose/time |
| --- | --- | --- |
| forest | `run-workloads/native-terrain-b15-r2/forest/high` | x256.5/y95/z768.5, yaw135/pitch9, time7000 |
| coastalwater | `run-workloads/photon-ssr-cost-controls-r1/coastalwater/high` | x500.5/y68/z660.5, yaw87.38/pitch3.735, time12500 |

Both use clear weather, PERFORMANCE mod preset and the existing M3 reduced
Photon source options. GTAO remains selected with 3 horizon steps, 2 slices and
radius 2.0. Other native options, resource packs, Minecraft options and scene
inputs are retained per source; source presets need not be equal across scenes.
Initial pilot uses forest only. A useful signal justifies later coastal and
repeated balanced order controls; flat/regressive results may stop the probe.

| Arm | Selected archive | Main mod | Probe option | Global enabled |
| --- | --- | --- | --- | --- |
| high | exact original Photon | absent (`runClientLodOff`) | absent | false |
| patched-off | reviewed default-off private archive | present (`runClientLodOn`) | true | false |
| adaptive | same private archive | present (`runClientLodOn`) | true | true |

Patched sidecars are byte-equivalent; fresh mod configs differ only in global
enable. Terrain, shadow-culling, visibility-list and coherence experiments remain
false. Unspecified additive per-effect fields retain INHERIT defaults. Minimal
stack/noDH, requested/effective 24 chunks, actual native 2976×1672 and fixed
wetness 0 are required for the initial check. The forest source's exact pinned
DH jar is removed only from each fresh copy; source jars are preserved. Coastal
source has no profile jars. NeoForge 21.1.253/Minecraft 1.21.1/Java 21,
Iris 1.8.12-local/Sodium 0.6.13/Photon v1.3b remain pinned through the runtime
lock and actual runner dependency/class fingerprints. DH 3.3.3 compatibility is
separate from this noDH experiment.

The preparer first rejects a live client, then takes the existing nonblocking
stopped-save locks before inspection/copying. It hashes source config/resource
packs, archive, option sidecar, Minecraft options, control, previous workload
manifest/report and reviewed patch; copies canonical equal worlds without the
session lock; records every fresh non-world input and canonical world hashes;
and verifies source world/input equality afterwards. Prior measurements/captures
are excluded from fresh profiles and retained in the source, with original
config/control/options/manifest/report copied to provenance. Existing outputs
are rejected. Partial failed output is preserved with a failure marker under
ignored build/ or run-workloads/.

## Commands after explicit root runtime handoff

The commands below are planned, not executed. Root assembles the private pack
and freezes Java/dev classes before handing off the client/GPU. The runtime
operator records process/session IDs, exact command, hardware/driver, class/JAR
hashes, source revision, archive/member hashes and how to stop the owned client
in the active checkpoint. Recorded prior hardware is Apple M3 Max OpenGL 4.1
Metal 91.7; actual run logs must confirm it. A title screen alone is not an
in-world compile/quality check. No DH terrain claim follows from noDH success.

```sh
python3 -m unittest discover -s tests -p test_photon_ao_reuse_profiles.py -v
python3 scripts/prepare_photon_ao_reuse_profiles.py \
  --scene forest --output run-workloads/photon-ao-reuse-pilot-r1 --dry-run
# Root only, before the explicit runtime handoff:
python3 scripts/build_photon_ao_reuse_probe.py --arm disabled
# Runtime operator only, after handoff:
python3 scripts/prepare_photon_ao_reuse_profiles.py \
  --scene forest --output run-workloads/photon-ao-reuse-pilot-r1
python3 scripts/run_proof_matrix.py forest-high forest-patched-off forest-adaptive \
  --profiles-root run-workloads/photon-ao-reuse-pilot-r1 \
  --stacks minimal --dh absent --repeats 1 --warmup 45 --duration 25 \
  --sample-interval 5 --window-scale 2 --fixed-wetness 0 \
  --skip-build --freeze-dev-classes --tag photon-ao-reuse-pilot-r1
```

This uses the existing normal WorkloadSession and report validator. Per-pass
timing and CPU layer timing remain false as the runner already specifies.
The runner reserves its matrix lock, waits for serial JVM exit, and retains
logs/reports/final PNGs under `dev/logs/photon-ao-reuse-pilot-r1`. It preserves
failed fresh reports and validates native loaded option, mod/dependency,
camera/control, stable input, temporal/wetness and GPU timing contracts. Runtime
review must additionally check actual 2976×1672 and requested/effective 24 at
both report boundaries, upstream option equality across arms, the loaded true
probe option in patched arms, no fallback, and actual bridge activation evidence
available from logs/readback. Source branch selection alone is not live uniform
proof. Re-qualify selected archive/member metadata against the frozen patch if
analysis happens after source changes; do not modify metadata to repair a failure.

Inspect all three final images at native resolution, especially near/far
contact shadows, thin foliage, depth edges and water/shoreline. One still image
does not establish pixel parity because temporal phase can differ across JVMs.
No reduced sample budget is accepted. If a useful first signal exists, require
matched movement/temporal edge checks and repeated ordinary controls before
promotion; report original/off/on contrasts, variability, total-frame medians
and tails, GPU intervals and instrumentation limits. No numerical gain is
promised. Same-JVM measurements require an additional reviewed qualification
and are deferred; the SSR benchmark policy cannot be reused for AO.

After the pilot the operator must let owned processes exit or stop only the
recorded process group, verify no owned Minecraft client remains, preserve all
outputs, and checkpoint results plus the next useful action before returning
GPU ownership. No process was owned at this source-only stopping boundary.

## Active forest pilot runtime handoff

Root explicitly transferred sole interactive client/GPU ownership to
`/root/ao_runtime` after the source freeze. Root completed 47 Java tests and
final production/dev compilation, then assembled the private default-off archive
SHA-256 `86f375aaf1b7d55a751595ca1087f14be155ac475d2e271f70be323d6829e9b9`.
No rebuild or class changes are permitted during the pilot.

Preparation command started in tool exec session `95967`:

```sh
python3 scripts/prepare_photon_ao_reuse_profiles.py --scene forest \
  --output run-workloads/photon-ao-depth-reuse-forest-pilot-r1
```

Run profiles are fresh under that ignored directory. Three-arm runtime is
pending until preparation completes. No Minecraft launch has occurred yet.
Preparation session `95967` exited 0. All source/copied world fingerprints were
`f752339d854ff2f0e0a7dc4e577dd6aedf2d0f69e39a9a7cae9dd0286c7be08b`.
Runtime-lock SHA-256 was
`7cca986f9580a32861d33fa7f6174b58babd207a119962d8d323c5abe6728a7a`.
Source and copied private archive/manifest matched the pins above. Forest DH
was physically removed only from the fresh copies, all profile mods empty.

Runtime runner started in exec session `89240`, beginning the original arm:

```sh
python3 scripts/run_proof_matrix.py forest-high forest-patched-off forest-adaptive \
  --profiles-root run-workloads/photon-ao-depth-reuse-forest-pilot-r1 \
  --stacks minimal --dh absent --repeats 1 --warmup 45 --duration 25 \
  --sample-interval 5 --window-scale 2 --fixed-wetness 0 \
  --skip-build --freeze-dev-classes --tag photon-ao-depth-reuse-forest-pilot-r1
```

Matrix lock, exact child commands/process group management and frozen runtime
fingerprints are owned by the existing runner. Active profile initially
`run-workloads/photon-ao-depth-reuse-forest-pilot-r1/forest/high`; its subsequent
arms are `patched-off`, then `adaptive`. Raw log/report/PNG paths are
`dev/logs/photon-ao-depth-reuse-forest-pilot-r1/` and its matrix manifest. Stop
via the exact runner exec session `89240` or its recorded PID/process group;
the runner handles child-group termination on interruption/timeout. Never
terminate a broad Java/Minecraft process match. No image processing occurs
while timed windows run. Runtime compilation/rendering/result remain pending.

Runner PID is `37369` (exec session `89240`). Original Java PID `37388`
completed naturally; arm 1 passed the existing report validator at
68.4552 FPS. The runner advanced to patched-off arm 2. This is preliminary
control status; no gain/quality/activation conclusion is drawn while running.
Patched-off Java PID `37531` also completed naturally and passed the existing
report validator at 72.6664 FPS. The runner advanced to adaptive arm 3.

## Forest pilot result and stopped handoff

Exec `89240` exited 0 after all three serial JVMs completed naturally. Adaptive
Java PID `37682` exited; runner PID `37369` is no longer active. Read-only
`minecraft_processes()` returned `[]` and the development class/resource
fingerprints remained equal to the matrix start after terminal completion.
Sole client/GPU ownership is returned to root. No reverse/second-scene run or
additional compiler/profile experiment was launched.

The existing runner accepted all three fresh UUID reports, archive/options,
camera/control, stable boundary inputs, noDH/main-mod presence, optional stack,
fixed-wetness and CPU/GPU contracts. Actual in-world Photon rendering occurred
on Apple M3 Max / OpenGL 4.1 Metal 91.7, using ELAPSED_ALTERNATING frame-only
queries every 5 frames (32 elapsed bits, no timestamp support). Runtime versions
were Minecraft 1.21.1 / NeoForge 21.1.253 / Iris
1.8.12-snapshot+mc1.21.1-local / Sodium 0.6.13+mc1.21.1. Both patched runs loaded
ShaderLOD 0.1.0-dev and the baseline did not. Actual artifacts and all individual
class hashes are retained in reports/matrix, rather than inferred from versions.

Additional terminal audit passed and is retained as
`dev/logs/photon-ao-depth-reuse-forest-pilot-r1/ao-runtime-audit.json`:
all 366 original effective shader options were equal in both patched arms;
patched effective option maps were fully equal with probe=true; live global
settings were false/true respectively; native framebuffer 2976×1672 and
requested/effective 24 chunks were present at both boundaries; exact forest
pose/time/weather were retained; shaders were in use without fallback; noDH
and all CPU culling/visibility/coherence flags were false. All three PNG hashes
and native dimensions verified. Private archive/license/member/metadata was
re-qualified against the frozen manifest after terminal completion.

| Arm | FPS | Mean frame ms (1000/FPS) | Frame wall median/p95 ms | GPU mean/median/p95 ms | Thread mean ms |
| --- | --- | --- | --- | --- | --- |
| original | 68.4552 | 14.6081 | 14.2224 / 21.1953 | 13.2830 / 13.0994 / 15.5850 | 8.0920 |
| patched-off | 72.6664 | 13.7615 | 13.5401 / 20.0771 | 12.5266 / 12.2953 / 14.9350 | 7.3651 |
| enabled | 53.1302 | 18.8217 | 18.0654 / 27.1561 | 17.7031 / 16.9734 / 22.7853 | 8.6871 |

Frame wall/thread values use the existing run-tick stream and remain separate
from GPU intervals. GPU sample counts were 342/362/266; these are correlated
frame samples, not independent replicates. Enabled mean GPU intervals were
33.28% higher than original and 41.32% higher than disabled. Enabled mean frame
time was 28.84% higher than original (22.39% lower throughput). No useful first
performance signal was observed. This is one serial pilot; the figures do not
establish that the private shader code caused the regression, or that the
disabled arm's lower cost is an optimization.

Capture windows are retained exactly for overlap auditing:

| Arm | Start UTC | Report written UTC |
| --- | --- | --- |
| original | 2026-10-05T20:15:45.596204Z | 2026-10-05T20:16:10.601243Z |
| patched-off | 2026-10-05T20:17:13.588830Z | 2026-10-05T20:17:38.589738Z |
| enabled | 2026-10-05T20:18:40.255163Z | 2026-10-05T20:19:05.303935Z |

All capture window observations were available, visible/non-iconified, at
1488×836 logical/2976×1672 framebuffer, with no capture state transitions,
vsync false, configured cap 260 and pause-on-lost-focus false. Focus differed
between arms: original and enabled were continuously unfocused during capture;
patched-off was continuously focused. Warmup focus transitions occurred in the
other two arms. No focus cause or equivalent host scheduling behavior is
established. A source worker reported a 4.6-second Python/scalar fixture run
during this wave. Its pyc mtime was 20:16:49 UTC, but this is not exact suite
start/end and reruns can reuse pyc. Light external CPU activity overlap remains
unresolved; this pilot cannot be accepted as a quiet steady-state causal test.

The protocol-2 bridge registered successfully in both patched startups. The
new second config, `config/shaderlod-effects-client.toml`, was auto-created
equally in both patched profiles: all six effects INHERIT, ratios 1/1 and
preserveSalientLighting true. Both file SHA-256 values were
`c02b81bc4ad7c90ac2e23e839845ddcfa8d7ab6909a380161f319ed414530984`.
This establishes successful startup and recorded default config creation.
WorkloadSession has no uniform observer/readback in this mode: no observed
version/global GPU values, extension-1 linking/consumption, physical fetch
counter or real texture-reuse count is claimed. Loaded true probe option,
registered bridge, global state, source gating and rendered pipeline are the
available activation evidence.

The original/off/on final images were visually inspected only after timing
ended. The viewer resized 2976×1672 source pixels to 2048×1151; inspected views
showed near terraced ground, grass/foliage, distant treeline, shadow structure
and river/shoreline without an obvious broad lighting/depth-edge failure.
Sheep positions and overlay values differed. This is a bounded still-image
observation, not native-pixel parity, thin-edge completeness, camera movement
or temporal-quality acceptance. No broad quality/default promotion follows.

Git HEAD recorded by the runner was
`df40a8b1eaf93665b4604443123b2d505e61458e`, with root's local integrated changes
represented by frozen class/resource inventories. Main-class tree SHA-256 was
`6debde5638cd965e798f3bf5d7cbf9d7586510f954c44efdf753e91401b5402f`;
dev-class tree was
`26f1d00e2bac0b6a0aa169ad6914ffc12c80365e221dbb12dba72355298b9e76`.
Loaded Iris archive SHA-256 was
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`,
Sodium SHA-256
`7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403`.

Raw preparation/profile state is preserved in
`run-workloads/photon-ao-depth-reuse-forest-pilot-r1/`, and console logs,
measurements, summaries, analysis, PNGs, class/dependency inventories and audit
remain in `dev/logs/photon-ao-depth-reuse-forest-pilot-r1/`. Next useful action
is root's candidate decision. The private gate stays default-off; this owner
does not extend the regressive pilot without a new bounded root handoff.
