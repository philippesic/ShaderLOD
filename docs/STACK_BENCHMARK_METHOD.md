# Optional optimizer stack comparisons

`scripts/analyze_stack_matrix.py` validates a complete schema-2 proof matrix and
compares the **same case** across the pinned `minimal`, `optimized`, and
`optimized-culling` stacks. Minimal already includes Iris/Sodium. Optional mods
and nested libraries come from `dev/performance-mods.lock.json`; the manifest
must retain that lock's exact SHA256. Use a fresh output directory for each
matrix and keep every launch, failure, report, image and summary. The coordinator
owns the only client/GPU session and freezes measured sources/build outputs.

```sh
python3 scripts/analyze_stack_matrix.py dev/logs/EXPERIMENT/matrix-manifest.json \
  --output dev/logs/EXPERIMENT/stack-analysis.json
python3 -m unittest discover -s tests -p test_stack_matrix.py
```

The analyzer owns no clients and changes no capture inputs. Its output separates
stack-only effects from ShaderLOD's incremental effect. Comparing original versus
active LOD remains a separate within-stack experiment. Compilation, actual
rendering, compatibility and visual quality also require separate evidence.

## Required comparisons

For each case and block, pair all selected stack combinations: minimal versus
optimized, minimal versus optimized-culling, and optimized versus
optimized-culling when all three are selected. Report each block's percent time
reduction, then the median, range and median absolute deviation across independent
JVM blocks. A positive reduction means less time. Do not pool frames across
launches or compute confidence intervals from autocorrelated frames. GPU render
intervals, CPU wall/thread clocks, FPS-derived frame duration and each run's p95
remain separate metrics. Repeated blocks reduce order drift but do not establish
simultaneous controls; partial Williams cycles do not fully balance position.

Every case × selected stack × block must exist exactly once, with complete runs
and a complete matrix status. Each run needs a unique valid UUID, matching
manifest/report requests, unique retained measurement/summary paths, and UTC
capture/write timestamps inside its launch interval. Launch intervals may not
overlap. Recomputed retained-sample metrics must match the saved summary. An
invalid or failed run rejects the whole matrix; rerun into a fresh directory
instead of filtering it out. New manifests should declare `cases` and `stacks`
independently of their run rows. Legacy stack selection inferred from rows cannot
detect removal of every row of an entire stack; this limit is reported explicitly.

Hold these inputs identical within a case, across stacks **and across blocks**:

- Exact shader archive SHA256 and complete loaded Iris effective option map,
  including every `SHADERLOD_` option; requested settings and recorded option
  hashes must agree. Lower quality is a separate case.
- ShaderLOD/DH presence, loaded base-mod versions and loaded artifact SHA256s.
  Mandatory Minecraft/NeoForge/Iris/Sodium identities must be present. Only
  locked optimizer mods and their explicit locked embedded mods may differ.
- Fixed source-world SHA256, actual dimension, camera, weather/time, framebuffer,
  requested and effective detail radii, live LOD settings and feature activity.
- Semantic Iris properties and DH TOML. Comment/order-only rewrites are harmless
  when their captured content verifies equivalence. Complete loaded ShaderLOD
  semantics may replace raw config identity; ordinary captures lacking them
  conservatively require equal raw config SHA256 and live LOD snapshots.
- Measurement request, actual warmup, GPU backend, sampling cadence and remaining
  runtime instrumentation properties. Shader wetness must match: fixed controls
  require observed suppliers applying the requested value at both boundaries and
  throughout capture; dynamic controls need equal observed wetness boundaries and
  applied ranges. World rain alone is insufficient.
- Available window observations: stable focus/visibility/minimization, window
  and framebuffer sizes, vsync, fullscreen, focus-pause policy and configured
  frame limit. Reject capture transitions, unavailable observations, hidden or
  minimized windows, and unfocused windows with focus-pause enabled. Counts and
  timestamp/frame IDs vary naturally and are not cross-run identities. Uniformly
  older captures without window telemetry are accepted only with an explicit
  missing-evidence limit; mixing present and absent evidence is rejected.

Optimizer validation uses **loaded** artifact identities, not only jars copied
into a profile. Parent versions/hashes must match the lock. EntityCulling's
`transition` 1.0.25 and `trender` 1.0.17 must also match their locked nested hashes;
ImmediatelyFast's Reflect is a Java library without a loaded NeoForge mod ID.
Profile jars are checked independently when present. Loaded files containing
multiple mod IDs are expanded per ID; nested files can have empty paths and
still carry exact hashes. An additional unrecognized mod is a base mismatch.

## Evidence limits and retained failures

Development class directories currently record a path without a byte hash.
Equal paths do not prove identical compiled classes; the runtime owner must keep
build inputs frozen. The analyzer reports this limit. GLFW state is sampled at
workload ticks and may miss changes between observations; configured pacing is
not actual host scheduling, thermal state or GPU clocks. Stable capture windows
do not prove identical warmup loading history. Images and moving-camera quality
must be inspected separately before any support or performance promotion.

The first takeover forest matrix exposed an actual nested-provider mismatch:
minimal loaded Iris's `fabric_api_base` bytes
`620fa28434a74dfa9e94af33a3e3bf70a939e9a4b0fcae1fbc01ba2c24f32531`, while
optimized-culling loaded Sodium's copy
`1aa448a0eff484c7b9f5a6aedbc5050a8d17999f31949dfebb20a7b977f80a07`.
Both report version `0.4.42+d1308ded19`, but the loaded artifacts differ. A
read-only comparison of completed block-1 captures in
`dev/logs/takeover-stack-forest-r1` passes minimal/optimized and rejects
minimal/optimized-culling. Direct nested ZIP hashing identifies those exact
copies in the pinned Iris/Sodium parent jars. This is a guard finding, not a
performance result. Preserve the matrix; resolve deterministic provider selection
or separately document exact equivalence before relaxing any artifact guard.
The analyzer must not silently classify this base change as an optimizer gain.

The follow-up [embedded provenance audit](CULLING_EMBEDDED_PROVENANCE.md) proves
all 17 class payloads and both remaining resource payloads are byte-identical.
The only extracted payload difference is the NeoForge dependency lower bound
`[21.1.169,)` versus `[21.1.115,)`, both satisfied by the exact 21.1.253 runtime.
Container timestamps/order/offsets differ too. Selection varied beyond the culling
stack; the optional mod does not establish causation or deterministic resolution.

The coordinator approved an explicitly qualified comparison of **only these
two audited hashes**. Preserve strict rejection diagnostics and write qualified
analysis into a separate output:

```sh
python3 scripts/analyze_stack_matrix.py dev/logs/EXPERIMENT/matrix-manifest.json \
  --equivalences dev/runtime-artifact-equivalences.json \
  --output dev/logs/EXPERIMENT/stack-analysis-qualified.json
```

Default comparison remains strict. The opt-in validates the reviewed registry's
exact hash pair, payload proof and metadata difference, then requires the exact
loaded library version plus Minecraft/NeoForge/Iris/Sodium versions and parent
hashes recorded in that registry. It rejects unknown library bytes, parent bytes,
runtime changes, additional payload changes and other module substitutions. Every
paired block retains each side's raw hash and audited parent-copy attribution,
and output retains the registry path/hash/proof and qualification. This rule
does not establish runtime transformed-class equality or deterministic resolver
selection, and does not relax comparisons of any other loaded artifact.

Acceptance for this tooling is automated fixture validation, not shader runtime
acceptance. Fixtures exercise correct block pairing, every stack pair, complete
matrix checks, shader/config/runtime mismatches, locked nested hashes, UUID/time
freshness, overlapping launches, window/pacing changes, summary tampering,
strict-default rejection and narrowly qualified opt-in boundaries.
