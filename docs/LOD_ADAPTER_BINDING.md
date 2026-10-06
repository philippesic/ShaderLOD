# Explicit Iris adapter runtime binding

The mod now separates adapter declarations, exact source eligibility, live Iris
availability, and rendered consumption. Core protocol 2 and effects protocol 2,
their fields, config defaults and commands keep their existing meanings.
An eligible declaration is not evidence that a shader compiled, linked, consumed
the uniform, saved physical work, preserved quality, or improved throughput.

## Registration and frame lifecycle

`EffectUniformsMixin.shaderlod$registerCapabilities` uses the existing pinned
`CommonUniforms.generalCommonUniforms` TAIL seam. It validates a loaded pack once
per actual `ShaderPack` object identity. It does not inject at reload TAIL, use a
pack-name substring, rewrite GLSL, or test pipeline readiness during construction.
The cache holds weak pack identities, including negative source results. A reload
with a new pack object needs a new registration even if its name or bytes match.

`IrisRuntimeAccess` uses Iris 1.8.12 `getCurrentPack()` (an Optional),
`getCurrentPackName()` and `getShaderpacksDirectory()` to locate the selected
source. The display name only locates the container; no name confers support.
Source validation requires both a registered complete source digest and exact
named consumer/helper member digests. ZIPs use SHA-256 of the complete archive
bytes. Folder packs use SHA-256 of sorted UTF-8 rows `member + NUL + memberSHA256
+ LF`. Folder and ZIP identities are separate pins. Paths, duplicate ZIP members,
symlinks, malformed pins and read failures cannot enable an adapter.

Actual option acceptance uses `ShaderPack.getShaderPackOptions()`, its `OptionSet`
and `OptionValues`. Only keys declared in the loaded Boolean/String option maps
are read, including Iris's effective defaults. Queued options, `.txt` contents,
mod configuration and internal program defines do not establish an active key.
Only required keys are examined at a frame boundary; source files are not reread.

Initial capability state is unavailable. The existing `EffectUniformFrame`
notifier listener snapshots effect controls and binding availability together.
All uniform suppliers use that snapshot until the next notifier frame. Live
availability requires the same validated pack identity, `!Iris.isFallback()`,
`IrisApi.isShaderPackInUse()` and accepted effective required options. The pinned
Iris API implementation checks the current non-vanilla pipeline. The binding never
uses `isPackInUseQuick()`. A `currentPack` that survives fallback grants no work
capability. Unknown, absent, unvalidated, conflicting, incompatible or unavailable
bindings supply no operations; the shared shader policy must retain upstream work.

## Capability schema 1

Two additive `uniform1i(PER_FRAME, ...)` registrations are separate from the
existing 43 effects fields:

| Uniform | Meaning |
| --- | --- |
| `shaderlodCapabilitiesVersion` | 1, the exact capability transport schema |
| `shaderlodVolumeIntegrationAvailable` | 1 only when the validated live adapter declares the shared count operation; otherwise 0 |

`LodOperations.VOLUME_INTEGRATION` is the pack-independent operation key:
`Effect.VOLUMETRICS`, `shaderlod:volume_integration`, `WorkKind.COUNT`.
It does not imply support for other volume operations, rates, or effects.
The shared consumer must require exact schema 1 and availability 1 before applying
a requested budget, in addition to existing core/effect protocol, enable, mode,
input validity and safety gates. Descriptor selection alone is not rendered
consumption proof.

## First reference and extensibility

`PhotonReferenceAdapter` declares identity
`shaderlod:photon-volume-integration` / `photon-v1.3b/shared-work-v1`, explicit
core `{2}`, effects `{1, 2}` and capabilities `{1}` protocol requirements, and
volume integration count floor 8. Two source bindings now use the newly reviewed
archives, each with ten exact consuming/entry/helper/license member pins:

| Archive arm | Complete archive SHA-256 |
| --- | --- |
| Default off | `5de7561dd24cdca80860a1d5f2c60400e74163a489cc163e916f3fd47feb27de` |
| Default on | `5f3772c8e97a89590b9a55a24d330de93543324befec6419aba9aed69991e5d3` |

Pins were reviewed from `dev/logs/shared-volume-consumer-source-freeze.json`,
manifest `40efd7e702e41388033255d48a007c543e1055ccb4e2513c1908c2d82b8f2677`,
then registered as fixed literals. Runtime metadata never confers support.
The source consumer owner identified required effective public options:
`SHADERLOD_VOLUMETRIC_INTEGRATION_LOD=true`, `CLOUDS_CUMULUS=true`, and
`BLOCKY_CLOUDS=false`. Internal stage/world/LUT defines are not public option keys.

Another explicit adapter adds its own declaration and `SourceBinding` without
changing Photon code, for example by extending the production composition in
`IrisAdapterRuntime.create()`:

```java
var registry = new AdapterRegistry()
        .register(PhotonReferenceAdapter.descriptor())
        .register(otherAdapterDescriptor);
var sources = List.of(otherExactSourceBinding); // separate from declarations
var runtime = new AdapterRuntimeBinding(registry, sources, exactProtocolVersions,
        runtimeAccess, PackSourceReader::read);
```

Each `SourceBinding` names an exact registered `AdapterIdentity`, full source
digest, map of exact member digests, and required actual option keys with accepted
effective values. New adapters that declare this existing shared volume operation
can use the existing availability uniform. Additional operations need their own
explicit transport/consumer contract; broad effect declarations confer none.

## Checks and unverified boundaries

Java 21 `javac -proc:none` compiled the binding, frozen adapter API, both bounded
integration edits and tests against the pinned Iris/NeoForge APIs into temporary
output, with no Gradle or `build/classes`/resources writes. JUnit ran 20 tests:
17 new runtime binding checks and 3 existing frame/effects registration checks,
all passing. Evidence: `dev/logs/adapter-runtime-binding-java21.log`; temporary
output `/var/folders/45/xr_x4hl921qg64tj4ln3jp_r0000gp/T/shaderlod-adapter-binding-jyyyh0lf`.
The log records source digests and the runner. Pinned API disassembly/hash evidence
is `dev/logs/adapter-runtime-binding-iris-apis.log`.

Coverage includes once-per-identity validation before a pipeline exists, same-name
reload with equal-but-distinct identities, surviving-pack fallback, absent/unknown/
unreadable/mismatched/ambiguous sources, missing/incompatible protocols, exact
operations, missing/disabled options, real archive/folder fingerprints, unsafe
members, runtime failure, coherent suppliers, actual additive mixin registration,
and actual pinned Iris option defaults/overrides with undeclared keys rejected.
The existing effects callback still registers exactly 43 original names/types.

The frozen-archive follow-up passed 21/21 isolated tests, including qualification
of both real archives, all ten exact pins, actual Iris-discovered effective
defaults/overrides, rejection of disabled/unreachable/missing options, and unknown
source fallback. Log: `dev/logs/adapter-runtime-frozen-reference-java21.log`.
The real-archive fixture skips explicitly when generated assets are absent; this
qualification ran with both present and zero skips.

The first coordinated `./gradlew test build compileDevClientJava` compiled main
and reused unchanged dev output, then failed 1 of 92 tests because plain Gradle
JUnit has no Minecraft `LoadingModList`. Iris's platform service loader wraps its
initialization NPE as `ServiceConfigurationError`. The runtime availability
boundary now handles that error explicitly, with an injected regression. Retained
failure: `build/adapter-binding-integrated-build.log` and
`dev/logs/adapter-runtime-binding-first-gradle-failure.xml`.

After the capability observer's source freeze, the coordinated retry of
`./gradlew test build compileDevClientJava` completed successfully: **93 tests,
zero skips, zero failures/errors**. Main and dev code compilation both executed.
The binding suite contains 19 tests. The original effects registration still
passes unchanged. Log: `build/adapter-binding-integrated-build-retry.log`;
structured test/JAR audit: `dev/logs/adapter-binding-integrated-freeze.json`.
Production `build/libs/shaderlod-0.1.0-dev.jar` SHA-256:
`555cd935025d43a46b71ec0815115def4368ceca5b99f66e390f6d3cbe26e7c4`.
It includes all eight integration classes and their nested types, with no dev
classes, dev mixins or dev mod metadata. The newly compiled dev observer is
separate from this production artifact; its no-GL fixture is owned by the
capability-readback worker.

No client, shader reload, GPU check, target-hardware timing, or project acceptance
was performed by this worker. No live process is owned. Actual linked shader
readback remains an integration need.
The source reader observes the on-disk selected container at
registration; it does not prove a linked program's in-memory source. Shaderpack
files must stay fixed while loading, and the runtime owner must separately prove
effective consumption, reload/fallback behavior and source provenance. Descriptor
eligibility must never be presented as quality or performance acceptance.
