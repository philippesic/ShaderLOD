# Runtime compatibility review

Reviewed 2026-10-03. This is a static/API audit with a read-only inspection of
the orchestrator's first client log; the reviewer did not launch or operate a
client. Runtime acceptance remains with the active setup goal.

## Recommendation

Keep Minecraft 1.21.1, NeoForge 21.1.253, Java 21, Iris 1.8.12, Sodium 0.6.13,
and DH 3.3.3. The inspected DH APIs do not justify moving to Iris 1.8.14 beta
and Sodium 0.8.13. The first observed rendering blocker is an Apple texture
unit limit, not a missing DH API.

Exact artifact URLs and SHA-512 values are in `dev/runtime.lock.json`.
The local Iris SHA-512 matched the published lock value. Primary release
metadata from [Iris t3ruzodq](https://api.modrinth.com/v2/version/t3ruzodq)
explicitly requires Sodium version ID `Pb3OXVqC`, which is the selected 0.6.13.
[Iris beta KduFYu4t](https://api.modrinth.com/v2/version/KduFYu4t) describes a
move to Sodium 0.8; adopting that line changes the renderer integration and
requires a separate audit. [DH 9w34y8ai](https://api.modrinth.com/v2/version/9w34y8ai)
is the selected release and records Iris fixes for Minecraft 26.1.2+; this
does not establish a required Iris update for Minecraft 1.21.1.

## Metadata and dependency bounds

The official Iris stable artifact advertises 1.8.12 but contains the detected
mod version `1.8.12-snapshot+mc1.21.1-local` and Minecraft range
`[1.21,1.21.1)`. This initially appeared incompatible; checking the actual
loader resolves it. FML loader 4.0.45 `VersionSupportMatrix` explicitly tries
Minecraft `1.21` after a failed normal check on Minecraft `1.21.1`. This
fallback is not limited to development launches. Iris 1.8.8 and 1.8.14 beta
artifacts contain the same Minecraft range, so changing Iris versions to
address that range would not improve correctness.

An actual Maven 3.9.9 `VersionRange` check returned `true` for the detected
Iris string against ShaderLOD's `[1.8.12,1.8.13)` range. A bare snapshot has
different qualifier semantics; do not infer rejection from its displayed name.
The current range admits the pinned artifact and rejects the 1.8.14 beta line.
It does not verify artifact contents or their hashes at runtime; the bootstrap
lock provides reproducibility, and further Iris builds require a new audit.

ShaderLOD is client-only, requires Iris, and declares DH optional. Its mixin
configuration is required with `defaultRequire: 1`; an absent renamed target
should fail startup rather than silently remove the bridge. NeoForge and
Minecraft bounds are narrow. After this review, ShaderLOD tightened the DH
range to `[3.3.3,3.3.4)` and added a Sodium `[0.6.13,0.7)` bound; later
renderer releases require another audit. Sodium selection is exact in the development lock; upstream Iris's
jar accepts `[0.6,)`, so that upstream bound alone does not prevent an
incompatible Sodium 0.8 installation.

## DH API and initialization

DH 3.3.3 reports API 7.2.0. A bytecode audit inspected all 25 Iris
`compat/dh` classes, extracted 67 external DH member references, and matched
each owner/name/JVM descriptor against DH 3.3.3 declarations and inherited
members: zero missing references. The implemented generic shader and
framebuffer methods also match DH's abstract interface contracts.

Iris queues event binding through `DhApiAfterDhInitEvent`; the initialization
callback invokes shader loading and registers render/texture callbacks.
`DhApi.Delayed.configs`, `renderProxy`, graphics configuration, inherited
event injector `bind`, and override injector methods remain present. This
establishes static linkage plausibility, not successful DH initialization or
rendering. The useful runtime milestones are `Queuing DH event binding...`,
`DH Ready, binding Iris event handlers...`, and `DH Iris events bound.`

The first inspected client log reported `Distant Horizons present=false`
despite the intended combined profile. The orchestrator identified discovery
through an additional runtime configuration as the cause and is moving the
development DH artifact into the owned run directory's `mods/`. Verify the
next launch's discovered mod list and those event milestones; do not call the
combined runtime working from classpath presence alone.

## Uniform bridge

`CommonUniforms.generalCommonUniforms(UniformHolder, FrameUpdateNotifier,
PackDirectives)` matches the mixin's static callback. Integer uniforms use
`IntSupplier`; float uniforms explicitly use the matching `DoubleSupplier`
overload. Registration occurs at the method tail and updates per frame.
The first client log recorded `ShaderLOD Iris uniform bridge registered
(protocol 1)`, demonstrating that this injection was reached on the pinned
Iris artifact.

The shader sentinel selects mod configuration only when
`shaderlodBridgeVersion == 1`, otherwise shader options remain available.
Registration is not proof the values reached the GPU: suppliers run later
when uniforms update. NeoForge `ConfigValue.get()` throws before its config
loads; the snapshot currently has no pre-load guard. If an early program
update exposes that ordering, return explicit defaults while the spec is
unloaded, or publish a validated immutable settings snapshot from config
loading/reloading events. No such exception was observed in the inspected
launch. Near/far/transition cross-field clamping is present.

The bridge currently registers for shader programs generally; only the
explicitly patched pack declares and uses these names. This is a maintained
Iris seam and Photon patch foundation, not Photon version detection or an
arbitrary-pack integration.

## Observed hardware blocker

The first orchestrator-owned launch in `run/logs/latest.log` reached Iris,
selected `Photon-ShaderLOD.zip`, and reached bridge registration. Pipeline
creation then failed with:

```text
java.lang.IllegalStateException: No more available texture units while activating sampler shadowtex0
```

The stack points to Iris shadow sampler registration. Recorded hardware was
Apple M3 Max, OpenGL 4.1 Metal - 91.7, macOS 27.0, Java 21.0.9. This does not
show successful Photon rendering, config transport, DH terrain, or LOD effect
behavior. A compatible GPU remains the decisive verification path if reduced
smoke-test shader options cannot fit the driver's sampler budget. Do not
upgrade Iris/Sodium based on this exception without evidence that the newer
renderer solves this hardware resource constraint.
