# Development throughput: adapter architecture audit

Observed 2026-10-13 UTC during the human-authorized paused-project audit. Read-only source investigation plus one offline Python test run. No Gradle, client, GPU, benchmark, runtime support promotion or changes to paused STATUS. Parent owns integration and any implementation assignment.

## Decision

The project already has the right reusable shader mechanism: explicit pinned manifests, shared budget helpers, a single reversible selection service and shared Java/resources consumed by ten builds. Another generic shader framework would add work. The immediate authoring bottleneck that can be removed is synchronizing a selected maintained recipe/helper into its classpath resource copy and updating its actual assembled source digest in the Java catalog. Extend the existing builder to do this finite operation; do not add a monitoring system.

Separately, the existing adapter test file has two directly reproduced obsolete assertions. Repair the assertions around their actual effect boundaries so each legitimate new consumer does not require fighting a global assumption about all patches.

## Actual input-to-consumer path

- `scripts/build_supported_pack_adapters.py:35` validates full executable/control source digest, member hashes, unique patch anchors and helper hashes, then assembles eight non-Photon families. Its `build` at line60 writes a deterministic private ZIP, retaining original credits. It currently does not update packaged resources or Java recognition.
- `src/main/java/com/pp/shaderlod/packs/PackSelectionService.java:303` loads recipes from `assets/shaderlod/adapters/patches/...`. Its `Recipe.patch` at line356 applies the same manifest concept on public Apply. Its state/recipe/source checks and option merge/restoration at lines94–218 are the common recovery mechanism. Photon is an explicit distinct schema with enabled settings member hashes; it must not be silently treated as the eight-family schema.
- Maintained `patches/adapters/*.json` and helpers therefore have classpath mirrors under `src/main/resources/assets/shaderlod/adapters/patches/...`. `SupportedPackCatalog.java:31–50` separately records original and current assembled digest, with historical recognition retained at lines66–74.
- Java and resources already flow to version/loader builds: e.g. `ports/26.2/neoforge/build.gradle:15–22`, Fabric26.2 lines17–24, and both26.3 build files use the same main Java and shared resource directories. Fabric1.21.1 similarly imports shared Java at `fabric/build.gradle:47` and shared adapter resources at line155. The root uses its own normal main source/resource roots. There are not ten independent copies of the maintained GLSL adapters or catalog to generate.
- Version-specific `MakeUpQuadRuntime`/API pins/mixins are explicitly excluded from shared source in port builds. These encode real Iris/Sodium ABI differences and newer copied-submission versus older cached-fill paths. Copying their declarations across versions is not sufficient support. The unrelated control caller/producer duplication is being audited by a separate owner.

## Reuse already achieved and limits that remain intrinsic

The shared `patches/adapters/shared/uniform_sample_budget.glsl:7–12` returns native counts on unknown bridge, disabled, Quality, invalid inputs; its existing retention is ceil(.875N) for Balanced and ceil(.75N) for Performance. MakeUp, Complementary, BSL, Solas, Sildur, Bliss and Astra AO bind this helper. `refinement_budget.glsl` independently owns distance bands from selected near/far/transition controls; it is no longer a fixed96/192 helper.

Direct transfers are already real, not hypothetical. The MakeUp cloud caller supplies count before native slab stride and weighting; Complementary alters near count before all integration denominators; BSL changes the deferred sky Aurora24 caller while leaving reflected Aurora12 native. The four-family independent source report, `volumetric-concept-transfer-review-20261011.md:5–17`, documents authentic originals and unchanged native bodies/interval weighting. Solas uses the same policy but must compensate stride and additive mean explicitly (`solas-aurora-transfer-review-20261011.md`, arithmetic section). These are small pack-specific joins onto one policy, not justification for universal GLSL inference.

AstraLex is a counterexample to copying a count replacement: its Aurora multiplies accumulated color by q every iteration and has padded countN+5; reducing N changes q^N. Its volumetric light/cloud loops also lack the necessary count-derived interval/normalization. `solas-astralex-volumetric-budget-port-20261011.md:23` records why the simple port stopped before edits. This is intrinsic shader math, not an architecture defect. The newer AO paired-depth adapter has its own valid normalization and radial resampling at `patches/adapters/astralex.json:60–70`, but still lacks working original Mac rendering.

Historical successes are mechanism-specific. `docs/PACK_COMPATIBILITY_MATRIX.md:60` records original-relative renderer throughput signals for MakeUp/Sildur1.21.1 and Photon native temporal upscaling as a different mechanism. The same newer MakeUp route removes only one draw command per measured frame and has closed no-gain trials. These do not establish that another abstraction or more coverage counters produce speed. Solas/Astra original driver failures remain prerequisites; do not spend the resumed lane repeating their unchanged failure premise.

## Are new ports reachable and expensive?

`PackSelectionService.java:83–89` chooses original graph for eligible Performance and for Quality; Balanced goes through the generated shader-work adapter. Thus a successful native Performance renderer route bypasses the newly ported shader sample budgets. This is deliberate existing product routing but must be considered before spending a port/measurement attempt. Do not silently reroute product presets as an audit fix.

- MakeUp default deferred sky count10 becomes9 on Balanced; meaningful selected work exists, but total-frame gain is unmeasured.
- Complementary's near-volume path is default-qualified, with explicit context/material/translucency exclusions; the workload still depends on actual scene/pass eligibility.
- BSL Aurora is AURORA0 under recorded native defaults. Its actual generated388 control/rendering acceptance is useful recovery evidence, but this new budget saves no default-reference-scene Aurora work.
- Astra nativeAO12 forms six groups; Balanced ceil(6*.875)=6 retains12, while Performance ceil(6*.75)=5 gives10. Astra has no accepted native renderer route, so the generated Performance path remains conceptually reachable, subject to its original driver blocker. Balanced alone cannot establish avoided AO samples.
- Solas Aurora is optional and has an original driver prerequisite. Source coverage is not the next efficient runnable speed premise.

A family opportunity should start with the actual selected preset, route, native feature switch and native count, not merely a patchable loop. This assessment belongs in the existing attempt, not a new generalized gating framework.

## Reproduced cheap failure

Command: `python3 -m unittest discover -s tests -p test_supported_pack_adapters.py`.

Observed8 tests in0.118s, two failures, six successes. `test_shared_budget_fails_full_and_only_hit_refinement_is_budgeted` at line82 still requires the removed fixed-band expression `smoothstep(96.0, 192.0, distance_blocks)`. `test_new_water_bindings_preserve_coarse_and_pbr_consumers` at line128 forbids `!defined ADVANCED_MATERIALS` across every patch; the new unrelated Aurora caller correctly has that exclusion. These are test contract drift, not evidence to remove native material guards. Some subsequent broad assertions are hidden behind the first failure and require the same effect-scoped repair.

Real-source test entry at line104 silently skips a missing original. This is acceptable convenience for an optional local suite, but its generic green summary must not be described as eight real assemblies unless the original count is recorded. The proposed sync operation requires one explicit actual original and cannot skip it.

## Narrow implementation proposal and ownership

First choice: extend `scripts/build_supported_pack_adapters.py` with explicit selected-family `--sync-resources`, retaining current `--adapter/--base` and ordinary ZIP behavior. Assemble the actual original with unchanged strict pins; validate catalog original identity; prepare exact selected manifest/helper classpath bytes and replace only that family's current assembled digest in Java. No source-member/helper auto-repinning, new family inference, legacy deletion or support promotion. Shared helper updates must not invalidate another manifest silently. Validate all planned outputs before writing, reject ambiguous catalog selection, preserve unrelated bytes and make repeated invocation a no-op. On current already-integrated sources the operation should report no changes. This eliminates manual authoring repetition across all ten consumers through their existing shared roots.

Owned implementation files proposed: builder and `tests/test_supported_pack_adapters.py`. Generated writes are confined to the selected adapter's declared resource mirrors and selected current digest in `SupportedPackCatalog.java`; parent approves integration of actual generated changes. Test in a temporary root copied from authentic maintained metadata and original archives: idempotence; exact byte propagation after a legitimate independently pinned recipe change; bad original/helper refusal before any write; ambiguous/wrong catalog refusal; unsafe paths; preservation of other family/current+legacy entries; deliberate mirror drift repair. Run representative real originals offline. No Gradle/client required; Java compile and driver remain unverified under pause.

Second choice: repair the stale tests in that same owned test file, limiting water-specific checks to actual reflection patch members and adding current selected-band/native-fallback assertions. Preserve coarse traversal/material recovery checks and inspect volume-specific caller/normalization boundaries independently. Do not weaken a gate to make a failing implementation pass.

No implementation edits were made while writing this report. The builder, resource tree and test file were already untracked in the shared checkout; preserve their preexisting contents and parent ownership. No recursive workers or owned live jobs.

## Implemented after parent scope assignment

Parent assigned only builder, focused tests and this report. The preceding proposal/no-edit statement records the initial audit handback. Implementation now adds `--sync-resources` (dry-run) and `--sync-resources --apply` to the existing builder. Standard `--output` behavior remains available; incompatible CLI modes refuse. Selected-family real source assembly and unchanged source/helper pins precede the write plan. One selected recipe and its declared helpers propagate into existing shared classpath resources; the selected current Java digest changes only after original identity matches. The previous current digest is appended to its legacy list, or a new legacy entry is created, without replacing other identities.

Both maintained and already-packaged consumers of a shared helper must match its bytes. A selected-family operation therefore refuses a coordinated shared-helper policy change whose other packaged recipes still pin the old bytes. Such a multi-family policy change needs explicit coordinated integration with all originals; it must not be silently applied through one family's command. This limitation preserves the existing consumer contracts without adding a batch framework. Photon remains outside this eight-family command because its manifest and enabled-member semantics differ.

All inputs/target bytes are checked before writes; source inputs and target snapshots are checked again immediately before mutation. File replacement is atomic individually. This is an authoring operation on a shared checkout, not a cross-file transaction or a concurrent-writer lock: parent must retain ordinary file ownership. An interrupted operation is recoverable by rerunning the same command. No current resource/catalog files were written in this audit.

Verified offline:

- Focused discovery: `python3 -m unittest discover -s tests -p test_supported_pack_adapters.py` —14 tests passed,0 skips,0.486s. Direct file invocation also14 passed,0.352s.
- Authentic BSL disposable-root tests exercise changed recipe/helper propagation, dry-run no mutation, idempotence, previous-current legacy retention, an absent legacy-family entry, source/catalog/duplicate/unsafe-path negatives, stale helper/shared-consumer refusal, coordinated maintained repin versus stale packaged consumer refusal, and unchanged unrelated identities. Existing assembly/build checks retain pinned failure behavior and deterministic output.
- Current repository dry-run assembled all8 real upstream archives and reported `changed_files: []` for every family. This proves current authoring/mirror/catalog coherence for this command's boundary, not Java/driver execution.
- The unchanged ordinary ZIP CLI built a temporary authentic BSL adapter with source digest `f386c864e7a8e39752982a332f963af8093dbb326094255a8428ae663bc728e1`; temporary output was removed on context exit.
- Repaired the two stale test assumptions by checking current selected near/far/transition fallback and limiting reflection-specific invariants to reflection/water patch members. No material guard or current shader behavior changed.

Example authoring command: `python3 scripts/build_supported_pack_adapters.py --adapter bsl-shaders --base dev/upstream/other-pack-research/BSL_v10.1.8.zip --sync-resources`; add `--apply` to write a reviewed plan. The ten existing builds consume the shared resources and require no per-target resource copy operation. No Gradle/client/GPU/benchmark jobs or Java compilation occurred; independent reviewer assigned by parent checks this implementation.
