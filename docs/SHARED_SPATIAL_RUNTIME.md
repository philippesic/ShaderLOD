# Shared spatial driver controls

2026-10-05. Prepared and source-qualified; **no client launch, GPU check, shader
driver compilation, parity, DH rendering or performance acceptance**. This is a
bounded diagnostic of the general shared LOD mechanism with Photon as its explicit
source adapter. Root owns the idle GPU and any later launch.

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
Metal91.7; this operation did not query the GPU or verify the current driver.
Record actual GPU, driver, Java and OS from the launched diagnostic evidence.

## Commands actually run

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
