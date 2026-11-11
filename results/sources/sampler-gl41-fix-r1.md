# Development evidence sampler query repair R1

2026-10-08 · `/root/sampler_gl41_fix` → `/root`. **Seven sampler queries in six
Java files are corrected in ignored staging only.** No active source, shader,
option, gate policy, existing report, goal, matrix or build output was changed.
Root owns integration/build/client/GPU; the separate BSL bridge repair and raw-query
worker are outside this assignment. No client, GL context, GPU, Gradle, peer or
cost trial was started. No performance or driver-success claim follows.

## API contract and exact repair

OpenGL4.1 §3.8.2 (printed p.192) selects a sampler unit using ACTIVE_TEXTURE and
queries SAMPLER_BINDING with GetIntegerv; table6.15 (printed p.356) identifies that
getter. ARB_sampler_objects likewise admits the token to non-indexed Get functions
and specifies GetIntegerv for the current sampler. `glGetIntegeri` accepting an
integer argument in LWJGL does not make this token a supported indexed query on
the pinned GL4.1 context. An old returned zero cannot certify an unbound sampler.
[OpenGL4.1 specification](https://registry.khronos.org/OpenGL/specs/gl/glspec41.core.pdf),
[ARB_sampler_objects](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_sampler_objects.txt).

All seven reads now use `GL11.glGetInteger(GL33.GL_SAMPLER_BINDING)` while the
requested unit is selected. Query exceptions propagate to existing failure paths;
no fake zero, error consumption, sampler rebinding or guard relaxation was added.

| Staged file | Context and restoration |
| --- | --- |
| `BslMipParity.java` | Both original-inspection and replay-input checks already select the input unit. Change only the getter; existing outer `finally` restores ACTIVE_TEXTURE. Both zero gates remain strict. |
| `CloudSamplerStateProbe.java` | Its sampler helper already selects the uniform's unit, with enclosing inventory restoration in `finally`. Change only the getter. Existing positive-sampler parameter observation remains intact. |
| `ComplementaryRasterActivationProbe.java` | Read sampler and physical texture in the same selected-unit scope, before restoring the saved unit. Retain the exact receipt fields and positive-sampler filter observations. |
| `CompositeCopyWitness.java` | Before the existing zero gate, save/select/read; restore in `finally` even when that gate or query fails. The later transfer-state inspection is unchanged. |
| `ComplementaryCopyGraph.java` | Preserve the unit-range and zero-override predicates/message. Read the sampler inside the existing save/select/physical-texture scope; retain its restoration check. |
| `SpatialLayoutEvidence.java` | `NativeDriver.samplerObject(unit)` independently saves/selects/reads/restores instead of relying on caller selection. Current callers already validate the unit; interface and abstract/fake driver contracts are unchanged. |

[Six full baseline/candidate copies and exact hashes](../../dev/logs/cross-shader-performance-r1/sampler-gl41-fix/baseline-candidate.json),
[integration diff](../../dev/logs/cross-shader-performance-r1/sampler-gl41-fix/integration.diff),
[frozen manifest](../../dev/logs/cross-shader-performance-r1/sampler-gl41-fix/manifest.json).
Root must verify all destination baseline hashes before applying; do not consume
an earlier source-complete message as release. The final manifest is written
atomically only after this report and the checks are complete.

## Earlier evidence requiring qualification

The audit records selected immutable raw receipts, their hashes and actual sampler
fields in [audit-raw-receipts.json](../../dev/logs/cross-shader-performance-r1/sampler-gl41-fix/audit-raw-receipts.json).
This narrows sampler claims; it does not rewrite old results or infer an actual
sampler override, particular driver error, readback failure cause or new rendering
failure. The independent source reviews listed below missed this GL-version/API
distinction where they admitted the existing zero gate.

- **BSL B13/replay:** [resource runtime](resource-runtime-r1.md),
  [parity primitive](bsl-mip-parity-r1.md), [parity review](bsl-mip-parity-review-r1.md)
  and [mip witness review](bsl-mip-witness-review-r1.md) admitted sampler0 or
  sampler/filter joins through BslMipParity/CloudSamplerStateProbe. That zero gate
  and effective sampling state are unproven on GL4.1. The recorded c7 generation
  deletion/c6 retention, source/physical-image joins, and 6,912,000 current plus
  18,432,000 history byte equality remain observations. **The replay does not yet
  establish an effective filter change:** a hypothetical nonzero sampler could
  override texture filter mutation. Raw pixel equality does not resolve that seam.
- **Complementary A6:** [copy primitive](complementary-copy-primitive-r4.md),
  its reciprocal/final sections in [parity review](bsl-mip-parity-review-r1.md),
  [graph preparation](complementary-copy-graph-preparation-r1.md),
  [graph review](complementary-copy-graph-review-r1.md),
  [viewport repair](complementary-copy-graph-viewport-r1.md) and the graph paragraph
  in [copy cost](complementary-copy-cost-r1.md) rely on the unsupported sampler0
  admission. R6's accepted source hash equals this assignment's exact
  CompositeCopyWitness baseline. Full packed P/Q equality remains recorded;
  the R2 31-event graph, native target extents, physical storage/flip joins,
  observed mip calls and swaps remain separate observations. Absence of sampler
  overrides and effective sampler filtering cannot be certified by those gates.
- **Complementary raster:** [activation probe](complementary-raster-activation-probe-r1.md)
  and [runtime R10](complementary-raster-runtime-r10.md) inherit this limitation.
  Actual R10/R11 activation receipts contain respectively **7/14 sampler_object
  fields, all zero** from the old getter. Their effective sampler/filter joins
  are unproven. Loaded source, linked control values, viewport/texture allocation,
  physical texture joins, normal Apply/Off/Quality and recorded stills remain
  observations. [R11 still review](complementary-raster-still-review-r11.md)'s
  visible softening and closed quality decision remain unchanged.
- **MakeUp B16:** [cloud-volume evaluation](cloud-volume-evaluation-r3.md) and
  [MakeUp runtime](makeup-cloud-runtime-r3.md) accept a sampler0 certificate and
  native effective filter/baseLOD state from CloudSamplerStateProbe. The actual
  R4 certificate's `sampledGaux2.samplerObject` is0; absence of overrides and
  derived effective sampling state are unproven. The full 1,048,576 asset bytes,
  PNG identity, texture-object parameter queries, constructor/source/pass joins,
  linked permission and recorded remaining-work cue retain their narrower scope.
- **Sildur and BSL A4:** [receiver runtime](receiver-runtime-r1.md) and
  [Sildur receiver review](sildur-receiver-coverage-r1.md) describe effective
  sampler state through the shared CloudSamplerStateProbe path. Sildur's
  sampler0/LINEAR-LEQUAL effective-state claim and any equivalent BSL effective
  claim cannot be accepted from this getter. Texture IDs/allocation/texture-object
  parameters, source/program/permission joins and actual coverage cues remain
  separate observations. A native PCF/support certificate does not follow.
- **Other shared-probe routes:** [Bliss chain review](bliss-chain-witness-review-r1.md)
  and [Bliss runtime](bliss-runtime-r1.md) retain physical image/source/Pass joins;
  their shared probe's sampler identity/effective-state fields are qualified.
  [SSR source/draw review](ssr-draw-source-join-r1.md) uses that same optional
  sampler probe; complete attached fragment/current-program hashes remain source
  evidence, while any effective sampler fields from it are unproven. No successful
  NativeDriver SpatialLayoutEvidence sampler receipt was identified in this bounded
  report audit; fake-driver checks never establish actual GL getter legality.

[BSL field review R2](bsl-field-review-r1.md) already identifies this API blocker
for the separate bridge. Root owns that repair and R3 attribution. Photon history's
[no-override source reasoning](photon-history-integration-r1.md) uses pinned Iris
sampler-construction bytecode, not these old runtime getter zeros; that independent
source fact is not invalidated by this audit. It remains distinct from a live
binding observation.

Closed negative BSL/copy/Bliss/MakeUp/SSR cost trials and the fixed raster quality
trial stay closed. Their qualified screenshots/endpoints and no-retention decisions
are not rerun, reopened, reclassified as gains or assigned a new failure cause.

## Checks and integration boundary

[check_staging.py](../../dev/logs/cross-shader-performance-r1/sampler-gl41-fix/check_staging.py)
passed **39 structural checks**, including all six baseline/candidate hashes,
unchanged active sources, exact seven-site inventory, preserved string gates/
receipt fields and exact reconstructed diff. `git apply --check` passed; the
staging directory is Git-ignored. No changes were applied to active source.

All **six complete staged files** pass isolated `javac --release21 -proc:none`
in0.91s against existing pinned NeoForge21.1.253/Iris1.8.12/LWJGL3.3.3 dependencies
and Sodium0.6.13 (SHA `7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403`).
Outputs are confined to staging. Exact [argv](../../dev/logs/cross-shader-performance-r1/sampler-gl41-fix/compile-argv.json)
and [compiler log](../../dev/logs/cross-shader-performance-r1/sampler-gl41-fix/compile.log)
are retained. Initial compilation lacked Sodium's terrain package in the borrowed
classpath; the original failure/argv are preserved. Adding the existing pinned
Sodium JAR fixed the compile, without changing any source or active build output.

The Java [fake-binding fixture](../../dev/logs/cross-shader-performance-r1/sampler-gl41-fix/SamplerScopeChecks.java)
executes extracted query scopes from all seven call sites with explicit caller
scaffolds for BSL/cloud outer restoration. **224 cases / 1,088 assertions pass**:
zero and distinct nonzero sampler bindings, saved units equal/different to requested,
injected query exceptions, strict positive-override rejection, returned sampler
identity, exact ACTIVE_TEXTURE restoration and untouched sampler-binding arrays.
It queries no OpenGL context. [Fixture log](../../dev/logs/cross-shader-performance-r1/sampler-gl41-fix/fixture.log),
[check receipt](../../dev/logs/cross-shader-performance-r1/sampler-gl41-fix/checks.json).

Root integration and ordinary development compile remain required; none was
performed here. Actual driver error-free query execution/restoration, consuming
sampler state and affected runtime joins are unverified. These source/fixture
checks cannot repair prior receipts retroactively, prove filtered-PCF bounds or
establish any quality/speed gain. Worker owns **no live process** and releases
staging/report only with the final frozen manifest.
