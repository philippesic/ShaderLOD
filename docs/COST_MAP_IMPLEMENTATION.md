# Live cost-map completion follow-up — 2026-11-08

The live launcher and bounded Asahi checks now exist and have run successfully.
This supersedes the earlier checkpoint's “no client launched” limitation. It
**does not claim complete stage 2–4 design acceptance**: worker/upload timing,
all-four-pack enabled semantic graph joins, timestamp/elapsed driver qualification,
process replication/rank uncertainty and full legacy migration remain incomplete.
No section 10 campaign, stage 5 qualification, stage 6 review, RTX/Nsight,
training or optimization work was started.

## New implementation

- `profile --live` owns a prepared managed slot through the existing exact-process
  supervisor, configuration backups/CAS restoration and whole-save backup/reset.
  World copies use `run_storage.copytree`. It uses the existing GPU-backed display
  selector and native 2560×1600 fullscreen, limits the requested capture to 1–30 s,
  checks actual CPU/GPU samples and graph replacement, and exports each immutable
  cohort. The current scene is the prepared authored save, explicitly identified.
- Development-only native guard refuses every optimization request (including
  TERRAIN_RADIUS); production artifacts do not contain this guard or other dev
  classes. Main mod is present because the timer hooks require its measurement
  APIs; its runtime was disabled in these tests. No shader optimization was enabled.
- Frame boundaries observe pipeline object replacement, dimension/disconnect,
  vanilla distance, effective DH rendering/mode/radius/quality/generation and mesh
  readiness. A change drains/disposes queries and finalizes the old cohort before
  issuing new observations. Pipeline replacement alone advances graph generation;
  readiness/settings can end a cohort without replacing that graph. Parsed and
  linked graph/target evidence is exported from the actual pipeline. Hook scopes
  register as observed; unobserved programs are not fabricated as timed scopes.
- Canonical physical DH joins retain main/deferred, world/shadow and opacity in
  CPU/GPU invocation records. Reports expose physical joins separately from semantic
  features. [Pinned-hook review and limitations](COST_MAP_DH_HOOKS.md).
- Effective settings, thread CPU/heap endpoints and DH submission/index counters
  remain observations, not task timing/residency. A declared
  `--dh-transition-check` toggles rendering off/on, using API override ownership
  and restoration (including clearing an originally absent API value). Normal
  capture does not alter generation, DH radius/mode or vanilla distance.
- Optional DH JAR staging is pinned, predeclared in the operation backup map and
  restored/removed at shutdown. Whole-profile verification passed after each
  successful run; generated DH state remains in retained world-after evidence.

## Live evidence

[Compact results and capture checksums](../dev/experiments/cost-map-live-20261108/RESULTS.json).
Raw captures, graph inventories, exports, receipts and console logs remain under
that directory and outside Git. These were actual Minecraft/Apple GPU runs:

- First attempt (`run-01`) crashed on absent main-mod RuntimeMeasurement classes.
  Log/crash evidence retained; process stopped, configuration restored and saved
  world checked byte-identical to the pre-entry backup. This is not a successful run.
- `run-02`: original MakeUp, absent DH; CPU/GPU hooks resolved, readiness changed
  cohorts, canonical JSON/CSV/HTML exported. Absent-DH rows remain N/A/null.
- `run-03-dh`: installed/enabled DH, existing DEFAULT/256-chunk settings and
  generation=true; main/deferred/opaque/translucent/render-list hooks fired;
  submission counters advanced. Deliberate Iris reload ended the old graph cohort.
- `run-04-graph`: two actual graph inventories, each with 522 parsed program rows,
  seven linked-program rows and seven target rows. Graph 1 yielded 18,506 measured
  CPU events and 1,217 GPU terminal samples; graph 2 yielded 14,344 and 991.
- `run-05-transitions`: six cohorts across four graph generations, including
  INSTALLED_RENDERING_DISABLED between enabled cohorts. Rendering API override,
  config, optional JAR and whole save restored. Enabled cohorts deliberately remain
  ENABLED_NOT_READY; submissions alone do not prove horizon residency. Partial
  final frames/unresolved query tails stay visible in health and event records.

Example maintained command (no A/B score):

```sh
python3 scripts/run_benchmark.py profile --live --dh --dh-transition-check \
  --profile run-workloads/world-pool/neoforge-1211-asahi-pbr/client \
  --pack MakeUpNativeOriginal.zip --seconds 8 \
  --output dev/experiments/cost-map-live-20261108/FRESH_RUN
```

`run-06-final` reran the current client path with a four-second window plus the
declared DH transition check: four cohorts, four graph generations, enabled →
disabled → enabled, hardware samples, exports and full restoration passed. It also
records DH API world-loaded readiness separately from unverified residency.

`run-07-guard` verified the final per-cohort guard reset: the last enabled cohort
has a valid guard after reload and DH off/on. Transition-ending cohorts retain
explicit pack/reload ownership-change failures; they are not scored A/B windows.
All processes exited, optional DH JAR/API override and the complete slot restored.
Verification logs/checksums are retained beside the raw runs under `verification/`.

## Machine and verification

Origin was synchronized before edits (9afbaa0). `loginctl` showed an active tty
session, no Plasma desktop; the already-running private KWin/Xwayland service
provided DISPLAY=:0, WAYLAND_DISPLAY=wayland-shaderlod,
XDG_RUNTIME_DIR=/run/user/1000, empty XAUTHORITY and its private D-Bus address.
`headless_display.wait_ready` checked service invocation ownership, GLX
Accelerated=yes / Apple M1 / Mesa 25.1.0 and xrandr 2560×1600. Minecraft independently
reported that GPU, native framebuffer, fullscreen and shaders without fallback.
No Xvfb or software renderer was used.

Deleted measured regenerable cache data totals 2,341,635,814 bytes plus old Gradle
daemon logs: Gradle build cache, configuration-cache reports, Mesa shader cache,
DNF user metadata cache, NeoForm intermediates and cached downloaded JDK archive.
The installed JDK, dependencies and experiment records were preserved. Initial
free space was 33,917,800,448 bytes; post-cleanup observed free space was
36,331,495,424 bytes. Rebuilding NeoForm and retaining live evidence consumed some
of that gain; final space is recorded in RESULTS.json. Btrfs reports update
asynchronously, so deleted logical bytes and net free-space change differ.

Focused verification: ten cost-map Python tests (including all 82 contract and
44 ownership checks, physical context/boundary checks, dynamic live inventory,
launcher admission refusals and offline round trips); 30 lifecycle, 55 dev-client
and 14 workflow tests. The dev-client deadline fixture initially exhausted its
finite fake-clock list because publication reads also read that clock; it now
keeps time stationary after reaching the deadline. An initial lifecycle/dev-client
invocation omitted PYTHONPATH=scripts and was rerun with it. Production jar:
2,736 entries, zero dev/CostMap/TimingMixin entries. All 63 broader benchmark regressions passed in 603.363 s; these were synthetic
process/recovery fixtures, not additional GPU benchmarks. The live canonical
report also regenerated byte-equivalent JSON through `profile-report`.

---

# Cost-map implementation checkpoint — 2026-11-08

Authority: Philip's “go build the whole thing and test it well e2e” authorizes
stages 2–4 and bounded Asahi pipeline testing. This checkpoint **does not yet
complete stages 2–4**. The design remains COST_MAP.md. No optimization, training,
RTX work, Nsight, section 10 campaign or stage 6 review was performed.

## Implemented and checked

- Dependency-free `CaptureOwner` connects the stage-1 registry to a mockable query
  backend. Ownership changes latch on frame boundaries; elapsed queries cannot
  nest; ordinary unsampled frames issue/poll nothing. Late retrieval retains issue
  identity. Shutdown has bounded availability attempts, deletes queries and emits
  unresolved tails. Worker tokens cannot be reassigned to a later frame/epoch.
  `OwnedOverrides` exercises absent-value restoration and concurrent-edit handling.
- Existing live GPU timers now bound scope/query allocation and group depth,
  expose completed-queue eviction and unresolved-tail counts, detect open groups
  across boundaries and support disarm/disposal. Cost-map elapsed surveys alternate Iris leaf and
  operation visits over 14 sampled frames; other legacy category visits remain
  unchanged. Use 14 scope sets for a conservative feasibility estimate of these
  split visits. Sparse cadence/rotation tests cover this schedule. CPU timers bound histories/depth,
  discard stale open scopes at boundaries and disarm explicitly. Scored guard
  acquisition disarms detailed timers and rejects a live canonical owner.
- `BottleneckProbe.prepare` no longer disables generation or changes DH settings.
  Configure changes only explicitly supplied generation/radius/mode/distance.
  Vanilla distance restoration checks the last owned value; failed commands attempt
  cleanup and retain failed canonical records. Normal diagnostic stop finalizes
  canonical output with before/after and guard evidence, retaining legacy files.
- Optional `CostMapSession` receives a complete stage-1 Capture plus a declared
  graph inventory before collection, joins existing CPU/GPU hooks to registered
  events, retains endpoints, hierarchy, late retrieval and explicit unknown rows,
  and writes `capture.json`. Observed hooks missing from declared inventory get
  bounded unknown-coverage entries and overflow counts instead of vanishing. This is opt-in via the existing development probe's
  `profile_on` request (`capture`, `manifest`, `scopes`). Scopes require `name`,
  `stage`, `program`, `operation`; graph generation is currently fixed at 1.
  Graph changes require finalizing this session and starting a new capture.
- Cost-map-only Iris API hooks cover copy, mip, blit, buffer clear and compute;
  presentation hooks separate swap buffers and event polling. These were compiled
  against pinned Iris/Minecraft APIs and inspected with javap, **not live-validated**.
  Enable them only with `shaderlod.dev.costMap=true`. They do not cover every
  possible GL operation or supply resource/extent identity.
- Offline module `scripts/cost_map.py` joins append-only observations by event and
  dimension, rejects duplicate terminals, calculates conditional invocation/frame
  summaries and descriptive variance, retains unknown thread clocks, and emits
  raw/summary CSV, canonical JSON, searchable HTML, independent CPU/GPU trace files
  and finalization checksums. Elapsed-only GPU data has no trace positions.
  Reports include DH coverage, hierarchy, CPU interval and investigation views.
  No additive parent/child or overlapping-feature total is computed.
- Tracked `docs/cost-map-catalog.json` records exact archive/member hashes and
  world0 DH terrain/water source availability for all four originals;
  `scripts/cost_map_source_inventory.py` regenerates it without modifying packs.
  Four-pack source seeds implement the design's initial descriptions. Activation
  requires supplied source/options/graph/DH evidence; unknown versions remain
  unmapped. **These are not reviewed enabled-DH graph catalogs.** Mandatory DH
  concern rows cover all states, with workers remaining unknown when rendering
  is disabled. Missing timing remains null; absent DH is not_applicable.
- Sampling feasibility estimates frames per set and rejects insufficient plans.
  It is a declared-input estimate, not an overhead qualification or live preflight.

## Evidence

Commands actually run:

```sh
python3 -m unittest discover -s tests -p test_cost_map_contract.py -v
python3 -m unittest discover -s tests -p 'test_cost_map*.py' -v
./gradlew compileDevClientJava jar --offline
python3 -m unittest discover -s tests -p test_run_benchmark.py -v
python3 -m unittest discover -s tests -p test_benchmark_workflow.py -v
python3 scripts/run_benchmark.py profile-plan --fps 60 --seconds 20 --windows 3 --processes 2 --cadence 4 --scope-sets 7
python3 scripts/run_benchmark.py profile-plan --fps 60 --seconds 1 --windows 1 --processes 1 --cadence 4 --scope-sets 7
```

Stage 1: all 82 checks pass; sampling/statistics mains pass. CaptureOwner: 44
checks pass. The six Python cost-map tests pass, including pinned four-pack source inventory hashes
and a headless test of the actual `CostMapSession` sink used by live timers. That
test compiles against cached Gson and checks CPU root endpoints, partial final
frames and original-issue versus late-retrieval frame identity through export. The end-to-end test compiles
actual versioned records, registry and lifecycle owner, runs a mock elapsed
backend, serializes registry batches, applies catalog/coverage joins, exports all
formats and reads the canonical records back. Golden CPU mean/median/p95 and
variance, GPU sample counts, null thread clocks, absent DH, no GPU trace positions,
immutable finalization and duplicate rejection are asserted. This tests a synthetic
pipeline, not Minecraft/driver instrumentation. Feasibility commands respectively
pass and deliberately fail (exit 1) before any launch.

Broader regression results: `test_run_benchmark.py` passed 63 tests (599.785 s);
`test_benchmark_workflow.py` passed 14 tests. These use headless process/recovery
fixtures; their synthetic optimization cases do not reopen parked candidates or
supply new performance evidence.

Production jar inspection found 2,736 entries and no `com/pp/shaderlod/dev/`,
`shaderlod-dev`, `CostMap` or `TimingMixin` entries. No display variables (`DISPLAY`
or `WAYLAND_DISPLAY`) were available. Filesystem free space was approximately
32 GiB, at the maintained reserve before client growth. No Minecraft client,
world/slot copy, configuration override, GPU workload or scene lease was started.
There is therefore no live restoration or machine-overhead claim.

## Remaining authorized implementation work

This checkpoint must not be presented as section 11 acceptance:

- Unify the mockable lifecycle backend and live timer adapter; qualify actual
  timestamp/elapsed driver behavior and exception cleanup. Make disconnect, DH
  readiness/residency and effective-setting changes drive immutable cohort changes,
  rather than relying on caller-declared boundaries and end receipts.
- Supply graph inventory automatically from the compiled graph, complete resource
  roles/extent and DH stage/world-shadow/opacity joins (live canonical adapter
  currently marks those contexts unknown). Review absent/enabled mappings against
  all four pinned graphs; add static source/graph fixtures. Validate hooks at runtime.
- Wire an owned live `profile` launcher into the maintained harness; currently
  `profile` imports an existing canonical cohort, and `profile-plan` only estimates
  sample feasibility. No live launch/recovery wrapper is claimed.
- Finish process-level replication summaries, coverage-aware rank uncertainty
  and full legacy migration beyond read-only duration-report preservation. The
  current report now has millisecond tables, frame/window distributions, an
  instrumented CPU wall flame hierarchy, separate timestamp clock-generation
  traces, bounded 4,096-event detail with omission counts and input checksum validation.
- Broaden lifecycle fault injection to every real DH override receipt and late
  worker/readiness/residency transition; current tests cover pure ownership models.

Stage 5 machine qualification remains separate. Section 10 captures and stage 6
review remain unauthorized. Generation-task and driver-upload execution timings
remain unknown; no steady generation-disabled capture can answer generation cost.


Maintained CLI e2e also ran `profile --input` on the Java-generated fixture, then
`profile-report --input` on the exported canonical capture and asserted identical
JSON reports. Raw synthetic artifacts are retained under
`dev/experiments/cost-map-pipeline-20261108/`; they contain no hardware measurements.

The final renderer was also checked with `node --check` on its embedded JavaScript.
`git diff --check` passed. The source inventory generator ran as:

```sh
python3 scripts/cost_map_source_inventory.py > docs/cost-map-catalog.json
```

An initial generation attempt targeted a nonexistent `config/` directory; it was
corrected to `docs/` and the complete six-test suite reran successfully.
