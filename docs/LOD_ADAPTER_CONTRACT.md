# Explicit LOD adapter contract

The dependency-free `com.pp.shaderlod.lod.adapter` API declares adapter capabilities
and negotiates work caps using the existing [effect policy](EFFECT_POLICY_API.md).
It is usable independently of NeoForge, Iris, pack files and a graphics context.
It does not discover, rewrite, select or load shaders. A descriptor declares
support; registration and an accepted cap prove no shader compilation,
consumption, quality, physical work avoidance or performance improvement.

## Declaration and extension

| Type | Contract |
| --- | --- |
| `AdapterIdentity` | Namespaced adapter id and exact revision string. Runtime/source bindings must separately verify source pins; a revision label is not a hash check. |
| `ProtocolRequirement` | Namespaced protocol id with an explicit set of accepted positive integer versions. Missing, unknown and newer versions are not implicitly compatible. |
| `WorkKey` | Existing `Effect`, namespaced adapter operation and `WorkKind`. An effect category alone never grants support for a suboperation. |
| `WorkKind` | COUNT (`count`) or RATE (`rate`). Stable string keys, never enum ordinals. Exact mathematical reuse is outside this cap API. |
| `WorkCapability` | A matching `CountFloor` or `DiscreteRates` legal domain. |
| `AdapterDescriptor` | Exact identity, required protocols and supported work capabilities. Empty or conflicting declarations are rejected. |
| `AdapterRegistry` | Immutable snapshot. `register` returns a new registry and rejects duplicate exact identities; different revisions can coexist. |

IDs use lower-case `namespace:path` syntax. Collections are defensively copied.
Conflicting duplicate protocol ids or work keys in one descriptor are errors;
registration never replaces an existing identity. There are no built-in pack
descriptors, names, source algorithms or defaults in this package. New adapters
register explicit declarations without modifying the registry. New operations
can use namespaced keys under the existing effect categories; adding a new effect
category still requires the existing policy/transport contract to be extended.

Required protocol ids belong to the declaration, rather than being inferred from
the registry's schema. A binding can explicitly require `shaderlod:core` version
2 and `shaderlod:effects` version 2, for example. Accepted version 1 of a group's
fields must be declared deliberately. Additional input protocols are ignored;
each declared requirement must match. This API does not resolve transport modes:
the binding must resolve INHERIT/FULL/ADAPTIVE before constructing `EffectPolicy`.
An unavailable or unresolved transport must retain the original path.

## Work negotiation

`WorkRequest` snapshots the exact adapter identity, protocol versions, work key,
resolved immutable policy, global enable, camera-relative Euclidean distance in
blocks, actual upstream count, and caller's legal floor. Null runtime policy/key,
invalid distance/count/floor, absent identity and missing versions fail open with
an explicit reason. A null request object is a programming error because it has
no upstream count to preserve. Declaration constructors reject malformed schema.

```java
var operation = new WorkKey(Effect.VOLUMETRICS,
        "example:integration", WorkKind.COUNT);
var descriptor = new AdapterDescriptor(
        new AdapterIdentity("example:adapter", "exact-source-revision"),
        List.of(new ProtocolRequirement("shaderlod:core", Set.of(2)),
                new ProtocolRequirement("shaderlod:effects", Set.of(2))),
        List.of(new WorkCapability(operation, new WorkCapability.CountFloor(1))));
var registry = new AdapterRegistry().register(descriptor);
var result = registry.negotiate(new WorkRequest(descriptor.identity(),
        Map.of("shaderlod:core", 2, "shaderlod:effects", 2), operation,
        resolvedPolicy, globalEnabled, distanceBlocks, actualUpstreamCount,
        pathSpecificMinimum));
int acceptedWorkCap = result.negotiatedWorkLimit();
```

This is an API example, not a supported adapter or quality recommendation.
Caller paths must retain the original operation for every rejected negotiation.
`WorkNegotiation.requestedBudget` preserves the numerical `WorkBudget` with its
continuous requested ratio and integer requested cap. `negotiatedWorkLimit` is
the cap accepted by the descriptor. Unsupported requests may therefore show a
smaller requested budget while the negotiated limit remains full upstream work.
`reductionRequested` and `reductionNegotiated` describe those two states only;
neither reports actual executed work. Runtime readbacks and operation counters
must be recorded separately. There is deliberately no physical-realization field.

COUNT delegates to `EffectPolicy.decide`: smoothstep distance transitions,
`ceil(upstream * ratio)`, clamping to upstream, and a legal floor. The effective
floor is `max(caller floor, declared floor)`. Zero explicitly permits skipping.
Zero upstream work stays zero. A positive upstream count below the declared floor
is unsupported and retains its original count. Large counts, including
`Integer.MAX_VALUE`, use the existing bounded numerical semantics.

RATE declares exact retained evaluation ratios, including 1 for full work. Both
the continuous requested ratio and the integer cap divided by upstream count
must be declared legal rates. Zero upstream work uses a full quantized ratio
because no evaluations exist. Unsupported intermediate rates or rounded counts
retain full work with `UNSUPPORTED_RATE`; the API does not silently snap rates.
For example, a declaration `{1, 0.25}` accepts one of four evaluations at 0.25,
but rejects a 0.5 transition or three of nine evaluations after upward rounding.
A zero rate must be explicitly declared. These exact-double membership checks
are Java contract checks, not promises of bit-exact GLSL float execution.

Global disable, effect disable, lighting protection and invalid numerical inputs
take precedence over descriptor checks and retain original work with the same
reason as the existing policy. Eligible requests then check exact registration,
each required protocol, effect, work key and legal domain, in that order. Thus a
disabled request reports disable even if the adapter is absent. Unsupported
effect, operation/kind, upstream domain or protocol never becomes a partial cap.
Shadow filtering and ambient occlusion stay protected when
`preserveSalientLighting` is true. Registration cannot bypass that policy.

## Integration boundary and acceptance

Runtime bindings must separately associate a descriptor with verified selected
source identity, required fields and actual consumer paths. A selected archive or
Iris pack object alone does not demonstrate rendering; fallback can leave source
identity available. The adapter supplies upstream units/count source, distance
source, legal domain, normalization and interval-preserving implementation,
quality/geometry guards, mod-absent and disabled paths, and DH compatibility.
Those obligations are not certified by `negotiate`.

Existing core/effects version 2 config and transport classes are unchanged by
this API. The root integration owner connects registration/binding; the shared
shader owner ports the numerical decision; the exclusive runtime owner verifies
field linking, effective modes, config reload, rendered consumer behavior and
physical work. Unsupported discrete requests must also use full fallback in any
shader port. Keep Java requested/accepted results distinct from shader work
observations. This API's acceptance is bounded to pure declarations/negotiation;
it cannot satisfy the project's 1.5 × rendering-throughput goal by itself.

On 2026-10-05, isolated Java 21 `javac` compilation and JUnit Platform execution
passed 28 tests: 14 new `AdapterRegistryTest` cases and 14 existing
`DistanceBandsTest`/`EffectPolicyTest` cases. Tests exercise extension snapshots,
conflicts, defensive copies, explicit protocol versions, effect/operation/kind
fallback, count rounding/floors/large counts, rate quantization/skip eligibility,
requested versus accepted caps, disable/protection, and invalid runtime input.
No Gradle invocation, main/dev class-resource output mutation, client, shader
driver, quality or timing check was performed. Raw acceptance evidence:
`dev/logs/adapter-contract-pure-api.log`; isolated classes/launcher are in the
temporary output directory recorded in that log. No owned live process remains.
