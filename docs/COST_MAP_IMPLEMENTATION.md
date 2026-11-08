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
