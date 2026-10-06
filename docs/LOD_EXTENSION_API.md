# Runtime adapter extension API

An explicit adapter can now join the production Iris runtime without editing
Photon or the mixin. `IrisAdapterRuntime.register(descriptor, sources, exports)`
registers its declaration, independently pinned source bindings and exact
operation-to-uniform exports during client startup. Photon remains the sole
supported reference; registering a descriptor does not establish shader support.
The pure [adapter contract](LOD_ADAPTER_CONTRACT.md) and
[source/runtime binding contract](LOD_ADAPTER_BINDING.md) still apply.

## Startup registration

Call the API before Iris starts the first `generalCommonUniforms` registration.
The mixin seals at that method's HEAD, before core/effects/capability suppliers
bind. Its existing tail callbacks also seal when invoked independently. The
production runtime takes one immutable descriptor/source/export/option-key
snapshot and uses it for all subsequent pipelines and shader reloads.
Registration after that boundary throws `IllegalStateException` with an explicit
startup requirement. There is no hot adapter registration or cache invalidation.
An early `IrisAdapterRuntime.snapshot()` returns unavailable without sealing.

For example, client initialization can declare the following operation and
register independently reviewed source bindings:

```java
var operation = new WorkKey(Effect.REFLECTIONS,
        "example:reflection_steps", WorkKind.COUNT);
var descriptor = new AdapterDescriptor(
        new AdapterIdentity("example:reference", "exact-reviewed-revision"),
        List.of(new ProtocolRequirement("shaderlod:core", Set.of(2)),
                new ProtocolRequirement("shaderlod:effects", Set.of(2)),
                new ProtocolRequirement("shaderlod:capabilities", Set.of(1))),
        List.of(new WorkCapability(operation, new WorkCapability.CountFloor(1))));
// reviewedSources is a List<SourceBinding> with this exact descriptor identity,
// full container digests, exact consuming member digests and required Iris options.
IrisAdapterRuntime.register(descriptor, reviewedSources,
        List.of(new CapabilityExport(operation, "shaderlodExampleReflectionAvailable")));
```

This is an API example, not a new supported pack or shader consumer. Registration
does not discover classes or packs, rewrite GLSL, or infer support from names.
The runtime still reads selected source bytes only during pack registration and
checks effective loaded Iris options at the existing frame boundary.

`AdapterRegistration` is the independently usable composition facade. Its
`seal()` method returns the same immutable snapshot on each call;
`Sealed.bind(runtimeAccess, sourceReader)` creates an explicit binding. Production
initialization delegates reference declarations to `ReferenceComposition` inside
`IrisAdapterRuntime`; the generic lifecycle and resolver have no Photon branches.
The pinned `PhotonReferenceAdapter` sources are unchanged.

## Validation and shared exports

Registration is atomic: a rejected bundle leaves no descriptor, source or export
behind. The API rejects:

- Duplicate exact adapter identities, including identical repeat declarations.
- Source bindings naming another descriptor identity, or any repeated full source
  digest within or across registrations. One source must resolve unambiguously.
- Missing runtime protocols or incompatible explicit accepted versions. Production
  offers core 2, effects 2 and capabilities 1. Every runtime descriptor must
  explicitly require capabilities 1; no protocol compatibility is inferred.
- Exports for operations absent from the submitting descriptor, repeated exported
  operations, and uniform names already assigned to another export.
- Invalid GLSL names, names outside the `shaderlod` namespace, reserved double
  underscores, and collisions with existing core/effects/schema fields.

The existing contract constructors additionally validate namespaced operation and
adapter ids, protocol versions, exact revisions, legal work domains, member paths,
digests and actual Iris option keys. Collections are defensively copied.

Several adapters may declare the same exact operation. They reuse its already
registered uniform by passing no new export for that operation. A second name
for an existing operation is rejected. A descriptor can also declare operations
without exporting them; declaration alone supplies no uniform. Empty source
bindings are allowed and cannot select a runtime capability.

## Capability schema and frame behavior

Schema 1 retains `shaderlodCapabilitiesVersion = 1` and the default
`shaderlodVolumeIntegrationAvailable` mapping to the shared volume count
operation. With no extensions the mixin still registers exactly these two
capability fields, alongside the unchanged 43 effect fields and seven core
fields. Additional exports are additive `uniform1i(PER_FRAME, ...)` booleans.
Their shader consumers must require exact capabilities schema 1 and availability
1, plus their existing core/effect enable, mode, input and quality gates.

Each `CapabilityExport.supplier(frame)` captures its operation once at uniform
binding. Calls read only `EffectUniformFrame.binding()`, shared with all effect
controls for that notifier frame. An unknown operation or any unavailable state
returns 0. Runtime/source/option changes cannot split suppliers within the same
frame; the next notifier callback refreshes the common snapshot. There is no
source hashing, collection construction, or runtime access per uniform call.

## Acceptance evidence and limits

Isolated Java 21 `javac -proc:none` compilation and JUnit Platform execution
passed **32 tests, zero failures and zero skips**: ten new extension cases,
19 runtime binding cases and three existing frame/effects registration cases.
The second fake adapter declares a custom reflection count operation with an
explicit uniform. Tests verify exact selection/options, zero for unsupported
sources/operations, atomic collision/identity/protocol rejection, immutable
sealing, late registration, and coherent suppliers with one runtime read per
frame. An isolated class-loader test exercises the actual public production
registration and mixin callbacks, adding the custom export while preserving
process-global default registration for the original schema tests.

Evidence: `dev/logs/adapter-extension-java21.log`, with exact source hashes;
temporary output and launcher:
`/var/folders/45/xr_x4hl921qg64tj4ln3jp_r0000gp/T/shaderlod-extension-fo0cm6pq`.
No Gradle, build/classes/resources write, client operation, shader reload or GPU
check was performed. No live process is owned. The runtime operator retains the
current client and frozen outputs. A coordinated post-runtime Gradle compilation
is still required. Actual additional shader consumption, startup lifecycle in
Minecraft, reload behavior, quality and performance remain unverified. This
extension API does not establish progress against the 1.5 × throughput gate.
