# Shared spatial driver controls

2026-10-05. Four bounded untimed controls rendered in-world and passed exact
source/options/window/input guards. Actual first-draw typed spatial capability
and opaque policy values were observed in private ON. **Exact consuming-stage
mapping, physical layout, parity, DH rendering, motion quality and performance
remain unverified.** The general mechanism uses Photon as its explicit source
adapter. All owned clients ended; the idle GPU returned to root.

[Preparer](../scripts/prepare_shared_spatial_runtime.py) never assembles shaders,
builds Java, launches a client, changes the source world or writes personal config.
[Tests](../tests/test_shared_spatial_runtime.py) verify strict positive source
qualification and mutations. Review source semantics separately in
[the reference freeze](SHARED_SPATIAL_REFERENCE.md) and actual typed readback limits
in [the capability probe contract](SPATIAL_CAPABILITY_READBACK.md).

The ignored prepared collection is
`run-workloads/shared-spatial-driver-r1/forest/`. Its
`shared-spatial-preparation.json` at the collection root contains all source/member
hashes, full upstream map, source loaded versions, exact runtime dependency lock,
requested options, unique per-arm request UUIDs, commands, copied-input hashes,
source registrations and stop procedures. Each arm has an independent world copy
and source provenance is preserved in `forest/source-provenance/`.

## Frozen controls and scene

| Sequential arm | Archive SHA-256 | Private option | Opaque policy |
| --- | --- | --- | --- |
| original | `c4e956dfee9ceebb6df245ea4792f5626cbc2d0ff995ccf9b49766d2286b57f1` | absent | FULL |
| privateOFF | `a8c004f5be0b341ed117220da0d2e2a0dadeefd62fd899ff64a3100c19a54ceb` | false | FULL |
| privateON-FULL | `c2790b61bd4d096e24eedbf9ca800d35541ca81e8ec5efeecec122b764dbf6f0` | true | FULL |
| privateON-ADAPTIVE | same exact enabled archive | true | ADAPTIVE |

The original archive contains original Photon source plus inspection metadata.
All four controls have the main mod present and global enable true, terrain
reduction off, and DH physically removed from each fresh copied profile. Source
DH remains untouched. FULL and ADAPTIVE request opaque lighting bands
32/96 blocks, an 8-block transition, medium retention1 and far retention0.25.
FULL overrides those ratios to original lighting; all other effects remain
INHERIT with unity ratios. These are explicit diagnostic values, not shipping
quality defaults. Private ON/FULL retains extra producer/consumer graph work;
original and private OFF are the graph parity controls.

The source is the reviewed **forest**,
`run-workloads/native-terrain-b15-r2/forest/high`, not the coastal volume profile.
Camera `(256.5,95,768.5)`, yaw135°, pitch9°, overworld time7000, clear weather,
fixed shader wetness0, native2976×1672 framebuffer/1488×836 window and24 chunks.
All366 actual upstream Iris effective options are held. Of these,354 declared
settings/explicit controls are written;12 computed/header defaults retain exact
upstream source semantics. No unsupported feature is silently disabled. Derivative
maps add only `SHADERLOD_SHARED_SPATIAL` with their arm's exact boolean value.
The complete expected actual maps, rather than source declarations alone, must
match later reports.

Pinned forest report SHA-256:
`6f7662a31427f0d0863d926909c67a57b0dfba3c7ee2539c857f699c7ce3da5e`.
Full canonical map:
`4efc7af7742bc445adf2b3b1454aa448a557e1d4b024103dd5b9f5758ed68745`.
Current stopped forest world:
`f752339d854ff2f0e0a7dc4e577dd6aedf2d0f69e39a9a7cae9dd0286c7be08b`.
This is the actual stopped source copy hash; the historical source ancestry hash
in its original manifest is retained separately, not substituted for it.
Reviewed complete freeze:
`15982ab52d0a474e660f643a18f8d1839f2ff46273187ee44bab77f95d8938df`.
Reviewed spatial manifest:
`bd9fbf3f70bb117a1825f630cc3a20e34089bd7bf9f8dd14377e2cc72ac582fa`.

Minecraft1.21.1 / NeoForge21.1.253 / Java21 / Iris
`1.8.12-snapshot+mc1.21.1-local` / Sodium`0.6.13+mc1.21.1` / Photonv1.3b are
held. DH3.3.3 is the source installed jar, removed only from copies for this
noDH diagnostic. The complete source loaded-mod map and dependency artifact
hashes are in the preparation ledger. Root's integrated build is `967d1f0`,
production JAR SHA-256
`0d499564c24d652c782a5c0e6b26fbae4a93b323542c5a4c58bbeee58da8ae9f`.
Preparation does not freeze Java classes/resources across a later build: the
operator must record and verify the actual final development fingerprints at
launch and completion. Previously recorded hardware is AppleM3Max/OpenGL4.1
Metal91.7; preparation did not query the GPU or verify the current driver.
Record actual GPU, driver, Java and OS from the launched diagnostic evidence.

## Preparation commands actually run

```sh
python3 -m unittest discover -s tests -p test_shared_spatial_runtime.py -v > build/shared-spatial-runtime-tests-r1.log 2>&1
python3 scripts/prepare_shared_spatial_runtime.py --qualify-only --output run-workloads/shared-spatial-qualified-r1
python3 scripts/prepare_shared_spatial_runtime.py --dry-run --output run-workloads/shared-spatial-driver-r1 > build/shared-spatial-runtime-dry-run-r1.json
python3 scripts/prepare_shared_spatial_runtime.py --output run-workloads/shared-spatial-driver-r1 > build/shared-spatial-runtime-prepare-r1.log 2>&1
```

Nine tests passed, zero failures/skips,2.221s. They reject changed inventory,
members, license, arbitrary metadata claims, layout, options, archive container,
source registration ledger, source world, coastal report, computed full-map
mutation, stale requests and timing contamination. Qualification is positive
against all three frozen archives and the actual forest source. Actual preparation
completed; all four copied world hashes matched and source input/world hashes
remained equal. The preparer shell session25636 exited0; no owned live processes.
Final source/tests rerun and copy audit are recorded in ignored
`build/shared-spatial-runtime-tests-final.log` and
`build/shared-spatial-runtime-copy-audit.json`.

## Runtime handoff and acceptance boundaries

Commands are reviewable in each arm's preparation row. They use `runClientLodOn`,
minimal stack/noDH,25s warmup/5s capture, `cloudSamplerProbe=true`, and explicitly
disable GPU timing, CPU timing, layer/pass timing and other diagnostics. Java and
resource compilation tasks are excluded, so root's reviewed frozen outputs must
already exist. No performance acceptance may be derived from these runs.

Before launching, the sole operator must acquire `dev/logs/.proof-matrix.lock`,
verify no live Minecraft process, recheck source/options/world/config/input and
production/dev fingerprints, and record exact launch command, runUUID,
launcherPID/session, JavaPID, log path, selected shader, scene and actual system
identity. Capture focus is explicitly held false across controls; an existing
early-warmup focus helper may establish this before capture. No focus/window
changes during capture. Commands are held until root's launch decision; the
preparer contains no `--run` path.

Run sequentially. Original/privateOFF first establish actual original rendering
and still parity. PrivateON/FULL then establishes that the private graph compiles
and renders while keeping full lighting. Only after those guards pass proceed
to privateON/ADAPTIVE. Failure stops the selected sequence; retain raw failed
evidence and diagnose before retrying. On completion/abort stop only the exact
owned JavaPID and exact launcher process group, with SIGTERM then a20s bound
before SIGKILL of those identities. Verify they are absent before handing GPU
ownership back. Never stop unrelated processes by broad name.

`validate_report` enforces request identity, complete effective map, exact source,
mod/noDH/global state, empty timing streams, native extent/radius, stable inputs,
forest pose/weather/time, warmup/capture, focus/visibility/iconification and actual
fixed wetness observations. Do not send these diagnostics through a generic
timed validator that requires CPU samples. Preserve the original report and
label any derived analysis diagnostic-only.

The typed probe output is `logs/cloud-sampler-state.json`; retain console,
`workload-measurement.json`, fresh screenshot and probe output per arm. Root must
map observed linked programs to actual producer/consumer stages independently.
First-seen numeric program IDs alone prove neither stage identity nor settled
availability. Private ON requires actual scalarGL_INT capability schema1 and
spatial availability1 plus core2/global1, effects2, exact opaque mode and all five
band/ratio values at actual consuming stages. A linked zero must remain zero;
missing/inactive/invalid fields remain unknown. Original/privateOFF may have those
private uniforms optimized out; absence is not an observed zero or failure of
original rendering.

Still outstanding: actual producer0/2 and consumer0/3 attachments, full viewport,
RGBA32F cache2 and original target0/3 formats, distinct physical input/output0,
producer completion and later bindings, sampler unit/filter/compare/LOD state,
valid and invalid certificate/quantity outputs, resize/odd edges/epoch/reload,
near/far/transition motion, foliage/albedo/thin shadows/water/reflections,
optional-DH fallback, actual physical work and repeated quiet total-frame/GPU
performance. A title screen is startup only. In-world compilation is distinct
from these checks. No goal completion or gain follows from this handoff.

## Completed four-arm untimed driver diagnostic

Root authorized and reserved this bounded client/GPU lane after commit`c716f68`.
The runtime operator acquired the proof lock and requalified all frozen archives,
all four prepared profiles, exact configs/options/registrations/worlds, the
production JAR and final main/dev classes/resources. No compilation, resource
build, tuning, manual shader reload or timed experiment occurred. The existing
early-warmup focus helper ran before each capture and exited before the window.

Exact executed wrapper:

```sh
python3 scripts/run_shared_spatial_diagnostic.py --profiles run-workloads/shared-spatial-driver-r1 --output dev/logs/shared-spatial-driver-r1 > dev/logs/shared-spatial-driver-r1-runner.log 2>&1
```

[Runner](../scripts/run_shared_spatial_diagnostic.py) uses each held prepared
command in reviewed order, stops the sequence on the first failure and preserves
raw artifacts. Two pure fixtures in
[its test](../tests/test_shared_spatial_diagnostic.py) passed before launch; they
reject changed commands before file access, retain actual capability0/1/unknown,
and reject capture/state-restoration failures. Raw fixture output is
`build/shared-spatial-diagnostic-tests-r1.log`.

All four controls exited0, rendered the exact selected source without Iris
fallback, and passed exact complete366/367 option maps, native extent24-chunk
radius, unchanged inputs, global enable, forest camera/time/weather, fixed wetness
supplier observations,25s warmup/5s capture, false focus/visible/noniconified
window and matched actual component artifacts. GPU and CPU timing streams were
empty in every raw report. Fresh2976×1672 screenshots were copied for every arm.
These are diagnostic captures; their overlay FPS and wall durations are not
performance evidence. Still parity and motion quality have not been accepted.

| Arm | Run UUID | Launcher PID/session | Exact Java PID |
| --- | --- | --- | --- |
| original | `f0ab504a-d910-4443-adf2-c2e5223943bd` |61692/61692|61708|
| privateOFF | `9adaaabe-fd0e-4bea-be24-e5a7b0e221d0` |62048/62048|62062|
| privateON-FULL | `679868d9-aac1-4742-a61c-a5ca2a77a19c` |62268/62268|62284|
| privateON-ADAPTIVE | `8340c2e3-4f01-4007-b67c-3364a37679a8` |62736/62736|62754|

Runner PID/session 61678/61678, shell session 62693 exited 0. Each Java process was
discovered and its exact process identity persisted while live. Java clients were
children of the existing Gradle daemon, outside the new launcher session; cleanup
targets exact client identities and the exact launcher group rather than the
shared daemon group. All listed client/launcher identities ended normally;
terminal verification found no Minecraft PIDs. The lock was released and root
received the idle GPU. Source files and stopped source world still match their
preparation pins. Main/dev outputs stayed unchanged across all controls.

Actual driver probe: Apple M3 Max, vendor Apple, OpenGL `4.1 Metal - 91.7`, GLSL 4.10.
Host macOS 27.0/build 26A428/Darwin 27.0.0/ARM64; Java 21.0.9+7-LTS-338. Loaded component
versions match the held versions above; main ShaderLOD`0.1.0-dev`, dev
harness`0.1.0`, noDH. Actual loaded Iris SHA-256:
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`;
Sodium:
`7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403`.
Full actual loaded versions/hashes and all compiled-member inventories are
preserved in the audit and derived summary, not replaced by lock declarations.
The console retains the same unsupported`GL_ARB_shader_image_load_store`
shadow-extension warnings in all controls; no selected-source shader compilation
failure/fallback or logged invalid-GL/error was found. Getters deliberately do not
consume GL error flags, so absence of logged errors is not an exhaustive GL check.

In each private ON JVM, first-observed linked programs 21/22 actually queried
scalar GL_INT capability schema 1, spatial availability 1, core 2/global 1 and
effects 2. Both actually queried the opaque mode (FULL 1 / ADAPTIVE 2),
near 32/far 96/transition 8 blocks and retained ratios 1/0.25. Every accepted field's
linked inventory type, scalar size 1, nonnegative location and queried status
was checked directly against the raw records. No queried spatial field was
available in original/privateOFF; those records remain unknown/inactive,
not fabricated zero observations. Other inactive core and volumetric fields
also remain unknown. Active-texture restoration matched every completed capture.

These numeric programs are **unmapped** to exact consuming stages. The first
draw is not settled/live-reload proof. No actual producer/consumer framebuffer
attachments, input/output0 physical distinction, cache2 completion/contents or
valid certificates were captured. The new physical layout diagnostic must resolve
those before stage/layout/work acceptance. Independent still-quality analysis and
motion/transition proof also remain separate.

Raw evidence directory: `dev/logs/shared-spatial-driver-r1/`. Authoritative terminal
status/commands/processes/compiled inventories:
`shared-spatial-terminal-audit.json`. Derived typed/hardware/hash summary with
each original input path/hash: `shared-spatial-diagnostic-summary.json`.
Each arm retains `*-workload-measurement.json`, `*-cloud-sampler-state.json`,
`*-final.png`, warmup-focusJSON and numbered console logs. Screenshot inspection
confirmed an actual forest/shore/cloud scene in original and FULL; no image or
parity acceptance is inferred from that gross visual check.

## Runner ownership correction before future launch

Root review found that the completed runner adopted any Minecraft PID appearing
after its exclusive empty launch, including during cleanup. Global appearance
alone cannot prove ownership: another client could start outside the cooperative
lock. This was a runner design error. The completed raw controls remain valid;
their recorded processes ended normally, and no unrelated process was stopped.
Their historical identity records are preserved rather than retroactively
relabelled as the stronger evidence below.

The corrected runner tracks a Java candidate only after an explicit stable live
identity and association with the current arm's unique canonical profile. Identity
now includes PID, parent PID, process group, start time and command. Narrow macOS
`lsof` queries select that exact PID's working-directory descriptor, or only the
two explicit arm paths `logs/latest.log` and
`saves/ShaderLOD-Landscape/session.lock`. A matching PID record and exact canonical
path are required, and process identity is rechecked after the query. It never
scans a whole open-file inventory, directory tree or another profile.

A foreign/ambiguous candidate stops the launch sequence and is recorded without
ownership or signaling authority. An exited candidate is not adopted. A reused
PID cannot inherit or reacquire an earlier client's authority. Cleanup applies
the same proof to late-created candidates, leaves unproven clients untouched,
and rechecks identity plus profile association immediately before each SIGTERM
or SIGKILL. Java signals target only the proven PID. The separately owned
launcher session can still be stopped by its exact process group; the shared
Gradle daemon group is never a Java cleanup target. Any remaining client keeps
terminal cleanup status failed and requires ownership review rather than broad
termination.

Eight scoped fixture groups now pass, including positive cwd and explicit-open-log
proof, a daemon-parent client, foreign and unavailable evidence, wrong returned
PID, identity changes during proof, no tracking/signaling of foreign clients,
proven-PID-only signaling, association loss and PID reuse. The initial fixture
file had a multiline `with` syntax error; no test ran in that failed invocation.
Both raw outcomes are retained:
`build/shared-spatial-diagnostic-ownership-tests-r1.log` (failure) and
`build/shared-spatial-diagnostic-ownership-tests-r2.log` (eight pass, 0.002 s).
Scoped whitespace checks passed. Corrected source freeze:
`build/shared-spatial-diagnostic-ownership-source-freeze.json`.

This correction was source/mock-tested only; no client, Java/resource build or
GPU action occurred. The installed platform reports `/usr/sbin/lsof` 4.91.
Live `lsof` association with a future Minecraft arm remains unverified; missing
permissions, unavailable cwd/logs, deleted-path annotations or a query timeout
fail closed as ambiguous. macOS provides no atomic PID handle in this runner:
start-time identity and immediate reproof reduce ordinary PID-reuse risk, but
the small identity-check-to-signal race remains a platform boundary. GPU stays
idle with root; no worker-owned live process remains.
