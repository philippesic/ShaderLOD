# General shared LOD performance pilot

2026-10-05 protocol, written before timing. This operation measures the current
general shared-volume system in000f355, with Photon as its first explicit reference
consumer. It introduces no pack tuning, shader knobs or quality reduction. This
is a qualified pilot; it cannot establish the50% goal, physical integration counts,
near/far parity, still/motion quality, DH or another hardware target.

## Acceptance fixed before launch

Nine independent JVMs form three blocks: original/disabled/adaptive,
disabled/adaptive/original, adaptive/original/disabled. Each arm occurs once in
each position; this balances position, not full carryover. All nine profiles are
prepared before timing, from the same stopped canonical coastal save. Independent
copies avoid repeat-JVM save/config history. The original has upstream Photon and
no main mod. Disabled/adaptive use the same exact registered private source with
integration optionTRUE; only the global mod enable differs. Seven effect sections
retain the existing512/1536/128/.85/.75 ADAPTIVE request for volume, INHERIT/unity
for the others, and salient-lighting protection. No config/source/class update is
permitted during the batch; NeoForge's semantic-equivalent comment/layout rewrite
is retained and qualified.

Current JAR must be
`aa65cf9157e0c5dac7db6ea65d38a42b3eec06a71838e394389063ead5b3e672`;
the complete class/resource member inventories freeze at batch start. Original
source120897… and private5de756…/manifest40efd7…/complete metadata/helpers/license
remain strict. Actual366 upstream effective options must match the canonical map
exactly. Only the declared integration=true flag is added for the private arms;
no arbitrary private-option exemption or old combined family is used. Current
typed schema1/availability1 linkage is separately established by
`dev/logs/shared-lod-system-qa-r2/shared-lod-terminal-audit.json`.
Observers/readbacks remain off throughout timing, so that linkage is not asserted
as newly sampled for each timed program.

Every run must pass existing matrix UUID/input/mod/source/noFallback/pose and
optional-stack/temporal validators, plus exact shared-system gates:

- Native WINDOWED2976×1672, logical1488×836, visible/non-iconified and stable
  UNFOCUSED throughout every sampled capture observation, with zero transitions.
- Fixed coastal500.5/68/660.5, yaw87.38/pitch3.735, time12500, clear weather,
  observed fixed wetness0, native24 detailed chunks, minimal Iris/Sodium/noDH.
- Identical pacing: frame limit260, vsyncfalse, pause-on-lost-focusfalse. Each
  warmup-only watcher arms before launch, activates Codex only after the camera is
  applied, then exits before capture. No focus changes during capture.
- Warmup25s/capture20s. Frame-only asynchronous GPUelapsed N5; actual samples
  must be game-render/measurement epoch2 at frame IDs1 modulo5. Pass/layer timers,
  sampler readback, DH evidence, water survey, shadow observer, visibility lists
  and coherence oracle are explicitlyOFF. Do not sum pass timings.
- All nine actual scene/runtime inventories match, apart from the intentional
  main-mod presence. All source and compiled inventories stay frozen. Every
  raw failure and run artifact is retained; the first failure stops the batch.
  Observation/tool timeout never restarts a client or substitutes another run.

Report per-run GPUelapsed and total-frame `run-tick` wall median/p95, and
elapsed-time/rendered-frame mean. Aggregate the three independent JVM values
per arm with median/min/max/MAD and paired per-block time reductions. Throughput
ratio is original elapsed-per-frame / adaptive elapsed-per-frame;1.5× means
50% higher throughput and33.333% less time for the same work. Frames are
autocorrelated samples, not independent experimental replicates. GPUelapsed
includes command-starvation intervals and is not GPU busy time. Real temporal
shader clocks/phase and final images are not frame-aligned across JVMs.

A useful consistent pilot signal requires all three original→adaptive blocks to
improve both GPUmedian and total-frame median, median paired reductions of at
least5% for both, and improved elapsed-per-frame throughput in each block. This
is a decision to consider a separately scoped query-free corroboration, not a
relaxed50% gate or accepted quality claim. This operation stops at nine runs and
analysis; no extra run chases a positive result. Quiet query-free corroboration
is useful only if that fixed signal exists.

## Ownership, commands and context

Root transferred sole GPU/client to `shared_system_runtime`. Both
`spatial_transitions` and `spatial_pipeline` acknowledged they will pause all
tests/builds/parsers/encoders for the entire batch; only lightweight source edits
may continue. They resume only after formal GPU return. No child worker is used.
The runner snapshots load and the top15 CPU processes only before/after runs;
it does not stop unrelated apps. Host activity remains a qualification, and
configured pacing does not prove scheduler/thermal-state equivalence.

Owned new files are `scripts/run_shared_lod_benchmark.py`,
`tests/test_shared_lod_benchmark.py` and this report. The wrapper reuses existing
matrix launch/validation functions through the explicit mapping:
original→water-high/runClientLodOff, disabled→water-patched-off/runClientLodOn,
adaptive→water-adaptive/runClientLodOn. Existing matrix/preparer/harness sources
are unedited. All generated worlds, captures, logs and inventories stay ignored.

```sh
python3 -m unittest discover -s tests -p test_shared_lod_benchmark.py -v
python3 scripts/run_shared_lod_benchmark.py --prepare \
  --profiles-root run-workloads/shared-lod-benchmark-r1
python3 scripts/run_shared_lod_benchmark.py --run \
  --profiles-root run-workloads/shared-lod-benchmark-r1 \
  --output dev/logs/shared-lod-benchmark-r1
```

The runner uses the existing proof-matrix lock and exclusive-runtime check.
Every exact Gradle command/request UUID and runner PID is recorded before launch;
the warmup watcher records actual client Java PID before capture. The existing
`wait_command` timeout/KeyboardInterrupt stops its owned child process group.
Never kill by broad process name. Exact runner exec/PID and formal return will be
recorded below when launched/terminal. Preparation and wrapper checks remain
pending at this protocol checkpoint; no timed client has been launched.

## Terminal result: incomplete batch, strict rejection retained

Six wrapper fixtures passed before launch, logged in
`dev/logs/shared-lod-benchmark-source-tests.log`. A fixture's initial temporary
path comparison failed because macOS resolves `/var` to `/private/var`; its
assertion was corrected to compare resolved paths. The initial failure is retained
in `dev/logs/shared-lod-benchmark-source-tests-first-failure.log`.
Actual preparation and nine-profile preflight completed; all initial worlds
matched canonical89c962…, exact source/member/metadata/config guards passed,
and class/resource inventories matched the previously verified current revision.
Evidence is `dev/logs/shared-lod-benchmark-preparation.json` and
`run-workloads/shared-lod-benchmark-r1/shared-benchmark-preparation.json`.

Runner exec25403/PID46215 launched three JVMs, then stopped at the first rejection.
Original/disabled passed individually. Adaptive rendered and passed its source,
effective options, scene, window, temporal and timing gates, but failed the
cross-run strict raw loaded-artifact inventory. No remaining block/run launched,
no timeout/restart occurred and no qualified exception was silently introduced.
Raw failed status and every O/D/A report remain preserved; there is no accepted
comparison, run spread, consistent signal, query-free corroboration or50% proof.

| Arm, block1 | Request UUID | Actual Java PID | GPUmedian/p95, ms | Total-frame wall median/p95, ms | GPU samples |
| --- | --- | --- | --- | --- | --- |
| Original | 1a89a508-dd60-44cb-8a19-f6b9f1ca71fa | 46239 | 15.124 / 16.845 | 16.222 / 23.353 | 243 |
| Disabled | f5584edd-6b8d-4fa4-b4e1-93cf9d627968 | 46402 | 15.737 / 18.032 | 16.617 / 23.599 | 235 |
| Adaptive, rejected comparison | 81a3ff67-315d-4db2-a5f4-a4bf638e0cad | 46553 | 25.949 / 33.238 | 26.726 / 36.671 | 144 |

These are raw diagnostic values, including the rejected adaptive report. They
are one serial block; no paired/repeated effect estimate is accepted. Original
capture01:19:08.993608Z–01:19:29.044594Z, disabled01:20:12.998113Z–01:20:33.043498Z,
adaptive01:21:15.889635Z–01:21:35.950255Z on2026-10-06UTC. Each warmup watcher
activated before capture, then stopped; exact commands and guard outcomes are
in `dev/logs/shared-lod-benchmark-r1/shared-benchmark-manifest.json`. Each native
capture stayed visible/non-iconified/unfocused with no window transition, no
fallback and exact held366upstream options. Observer/pass/layer properties were
off; GPUelapsed N5 and CPU measurement epoch/scope gates passed. The current
main/dev/resources remained frozen at terminal; source was unmodified. Images
are retained, but no quality inspection or comparison is promoted here.

The failed runner exited1; all three client Java PIDs and runner were verified
absent, and exclusive-runtime check returned no clients. Sole GPU/client formally
returned to root before diagnosis. Both spatial workers were notified they may
resume heavy checks. No owned live process remains, and no additional timed run
or build follows this batch.

## Read-only mismatch diagnosis

The sole raw artifact difference was `fabric_api_base`0.4.42+d1308ded19:
original/disabled loaded
`1aa448a0eff484c7b9f5a6aedbc5050a8d17999f31949dfebb20a7b977f80a07`;
adaptive loaded
`620fa28434a74dfa9e94af33a3e3bf70a939e9a4b0fcae1fbc01ba2c24f32531`.
The nested loader path serializes as an empty string, so the actual reported
hash and direct nested byte inventory establish origin. Source inspection did
not assume equivalence from a version string.

The read-only diagnosis re-extracted
`META-INF/jarjar/fabric-api-base-0.4.42+d1308ded19.jar` from the actual pinned
parents and verified whole parent SHA256 and runtime-lock SHA512:

| Actual parent path | Actual parent SHA256 | Nested child SHA256 / size |
| --- | --- | --- |
| dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar | 3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83 | 620fa284… / 34807bytes |
| dev/runtime/sodium-neoforge-0.6.13+mc1.21.1.jar | 7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403 | 1aa448a0… / 34806bytes |

All three captures report Minecraft1.21.1/NeoForge21.1.253 against the actual
`build/moddev/artifacts/neoforge-21.1.253.jar` SHA256
`a3f878f97a229b543a2c872c4a3c03b19ebd3efbd11af2caed9c258c08aebf55`, independently
matched to current disk bytes. Iris1.8.12-snapshot+mc1.21.1-local and
Sodium0.6.13+mc1.21.1 actual loaded parent identities also match these bytes.
Recorded GPU/driver is Apple M3 Max/OpenGL4.1 Metal91.7; no target-hardware claim.

Both children have the same38 ZIP names, no duplicates, and20 regular files.
All17 class files, `META-INF/MANIFEST.MF` and `assets/fabric-api-base/icon.png` are
byte-identical. The only differing payload is `META-INF/neoforge.mods.toml`,
whose exact one-line NeoForge dependency lower bound is `[21.1.169,)` in Iris
versus `[21.1.115,)` in Sodium. Replacing that exact bound makes descriptors
byte-identical. Current21.1.253 satisfies both. Recomputed sorted member payload
fingerprints are classes
`34704804f9e2834e3114d6029792a5353bd1a09ea9fa814883bb912ca9ace10a`, unchanged
resources `4afce8cc02b58dc62b4faa2e6a5c8e75ec2bc9c76e348dea42491b0175ea2385`, and
all files except descriptor
`f075c9f89451356a91e59ff0c5a534c8358f3f7885747ad6c79a490cb5aceffa`.

This reproduces the exact narrowly reviewed condition in
[CULLING_EMBEDDED_PROVENANCE.md](CULLING_EMBEDDED_PROVENANCE.md) and
`dev/runtime-artifact-equivalences.json`. A read-only check against that registry
also finds every other loaded identity equal, excluding intentional main-mod
presence. No runtime transformed class bytes or resolver determinism were
observed. No failed report/manifest is relabeled, no runner gate changes and no
accepted performance comparison follow from this diagnosis. Root decides any
independent qualification or future experiment.

## Host context and coarse CPU trend

Outside-window prelaunch load averages(1/5/15min) were1.96/2.08/2.22,
2.88/2.38/2.33 and2.98/2.58/2.41 for O/D/A. Snapshots show WindowServer20.6%,
25.0%,32.3%; ChatGPT11.5%,91.6%,8.2%; adaptive prelaunch also has Dock9.9%.
Runner preflight/postrun hashing appears in its cumulative CPU snapshots. These
are process snapshots outside windows, not continuous capture-window load or
evidence of equivalent thermal/clocks/scheduling. No unrelated app was stopped.
Workers acknowledged heavy-check pause; runtime enforcement is not inferred
from that acknowledgment.

| Coarse mean, ms | Original | Disabled | Adaptive diagnostic |
| --- | --- | --- | --- |
| run-tick wall | 16.415 | 16.967 | 27.678 |
| run-tick CPU thread | 7.821 | 7.900 | 8.187 |
| game-render wall | 11.664 | 11.887 | 15.629 |
| game-render CPU thread | 6.986 | 7.024 | 7.067 |
| swap/events wall | .800 | .850 | .835 |

The large adaptive wall/GPU increase coincides with much smaller thread-time
changes and similar swap/events time. This does not distinguish GPU work from
command starvation, waits, scheduler/thermal state or other host activity. No
causal attribution, effect-work counter or source tuning follows.

Exact nested entry hashes, metadata diff, real loaded parent/runtime hashes,
all raw CPU streams, host snapshots and diagnosis provenance are retained in
`dev/logs/shared-lod-benchmark-r1/failed-batch-provenance-diagnosis.json`.
The sole read-only diagnosis command was
`python3 dev/logs/shared-lod-benchmark-r1-diagnose.py`, exit0. The original failed
manifest, incomplete analysis, three consoles/measurements/PNGs/focus actions and
source prep remain in their ignored roots. Scoped source/report whitespace checks
pass. This bounded operation ends with incomplete evidence and formal GPU return.

## Independent one-block qualification and future source freeze

After accepting the strict failed batch, root explicitly authorized a separate
derived qualification using the existing exact API Base registry. The new
`dev/logs/shared-lod-benchmark-r1/first-block-qualified-analysis.json` cites the
failed raw manifest, all three raw measurement hashes and the read-only diagnosis
hash. It rechecks UUID/freshness/input/options/native-window/scene/temporal/timing
gates and the exact current registry with extracted nested payload inventories.
The only independently qualified difference is the two audited API Base raw
variants on their exact locked parent/runtime hashes. The original manifest
remains byte-for-byte unchanged with statusFAILED. This is one nonrepeated block,
not the planned balanced three-block result. No gain is promoted.

| Qualified single-block contrast | GPUmedian time increase | GPU p95 time increase | Total-frame median time increase | Total-frame p95 time increase | Total throughput ratio |
| --- | --- | --- | --- | --- | --- |
| Original→adaptive | 71.57% | 97.32% | 64.75% | 57.03% | .5932× |
| Disabled→adaptive | 64.90% | 84.33% | 60.84% | 55.39% | .6131× |
| Original→disabled | 4.05% | 7.05% | 2.43% | 1.05% | .9675× |

Ratios use observed elapsed-time/rendered-frame means, not inverted medians or
summed passes. The adaptive arm is slower in every displayed contrast for this
single independently qualified block. No repeat/run-spread estimate, causal
shader-work explanation, physical work reduction or quality equivalence follows.
The prior host-context/thread-vs-wall limitations continue to apply; they are not
removed by the narrow library-content qualification. Source/physical work audit
is separately owned, and no additional launch is authorized here.

The future wrapper now calls the existing registry loader and qualified-identity
helper. It additionally checks actual parentSHA256/runtime-lockSHA512, exact
child raw bytes, every class/resource inventory fingerprint and the sole
compatible metadata lower-bound substitution before any future launch. It records
both raw and qualified identities; all other artifact, shader/source/option/
config/window/scene gates remain strict. The registry is named explicitly in the
future run manifest; this is no wildcard, version-string waiver or private-option
exemption. Current failed records are not altered by the source refresh.

Seven wrapper fixtures pass, including positive exact-registry comparison and
mutated embedded executable/raw child, unknown API Base and changed-parent hash
rejection. Fixtures requiring ignored local runtime evidence skip when that
material is absent. Fresh check log:
`dev/logs/shared-lod-benchmark-qualified-source-tests.log`. The separate derived
command `python3 dev/logs/shared-lod-benchmark-r1-qualify-first-block.py` exited0.
Scoped whitespace checks pass. Files stop at this tested source/report freeze;
no further client, GPU, timing or shader change occurred after formal return.
