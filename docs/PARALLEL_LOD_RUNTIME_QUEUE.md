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
| reflection | `0c20609c50ee65d98ec803eb96294a0d727bff82d81a6634abb1504518d89e1a` | `d11de4c94fb58520e9972a816e9739486fa8578b876fbd20c17aa933a4a82e16` |
| volume | `08e1611a77a2f6d6364b91eda692ea2e300b1c3c2891c87d7abd6ecec98def9b` | `3685e47e94cd08f3994053f300981bbdd92ae108a65f7fadb700bd6d9422a6b5` |
| procedural, corrected r2 | `5849bc24c84f98b42a4ad7a85b013cdab6bb229a3f8c2d8e8c48d6097cd9e687` | `eb9fa7db90fffff1ee264b90098a2b14a8dd322d3e8a111b8460fc329eea0719` |
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
