# Effect and texture readback observer

2026-10-05. Development-only implementation in the isolated `lod-interfaces`
worktree. Owned files: `CloudSamplerStateProbe.java`, new
`EffectRuntimeReadbackTest.java`, and this document. The separate raster-hook
owner owns its mixin/plugin/resource changes. No game, GL context or GPU run was
launched by this owner; root must integrate/rebuild before diagnostic runtime.

The existing `shaderlod.dev.cloudSamplerProbe` property still controls all query
and report work. False is an early return before any GL query for both public
callbacks. `beforeQuadDraw()` remains compatible with the existing fullscreen
hook; `beforeRasterDraw()` serves the independently reviewed pinned Sodium
submission hook. Each entry records `draw_origin` as `iris-fullscreen-quad` or
`sodium-raster-submission`. Origin is a hook location, not an observed shader-pack
program name or proof of effect work.

## Uniform schema

The top-level report `schema=1`, old `core_uniform_schema=2`, and seven
`core_uniforms` names/types/statuses retain their meanings. The additive
`effect_uniform_schema=2` identifies a separate `effect_uniforms` map in every
captured entry. It selects 13 exact fields, not every field in the producer's
43-field extension:

| Names | Expected linked shape |
| --- | --- |
| `shaderlodEffectsVersion` | Scalar int; expected protocol version 2 stored separately from actual value |
| `shaderlodVolumetricsMode`, `shaderlodOpaqueLightingMode` | Scalar int |
| `shaderlodVolumetricsNear`, `shaderlodOpaqueLightingNear` | Scalar float |
| `shaderlodVolumetricsFar`, `shaderlodOpaqueLightingFar` | Scalar float |
| `shaderlodVolumetricsTransition`, `shaderlodOpaqueLightingTransition` | Scalar float |
| `shaderlodVolumetricsMediumWorkRatio`, `shaderlodOpaqueLightingMediumWorkRatio` | Scalar float |
| `shaderlodVolumetricsFarWorkRatio`, `shaderlodOpaqueLightingFarWorkRatio` | Scalar float |

For each field the observer records expected type, linked-inventory presence,
actual active type/size/name, location and typed getter value when legal. Missing/
inactive/truncated fields, non-scalar/wrong-type fields, negative locations,
non-finite returned floats and getter exceptions stay `status=unknown` with a
reason; no source option/config/supplier is evaluated as a substitute. Location
zero is valid. Queried values retain `queried_without_gl_error_consumption` status:
the observer never consumes `glGetError`, so inspect runtime GL logs separately.

The expected version does not replace the actual version or imply acceptance.
For example, an observed version 1 remains an actual value 1; the adapter/runtime
validator decides whether that version is accepted for its specific effect. A
linked value at one first draw establishes only that recorded program state.
It does not establish settled camera/control state, later reload behavior, effect
execution, reduced physical work or performance/quality acceptance.

## Sampling and level-zero allocation

Active scalar `sampler2DShadow` joins scalar 2D/3D sampler inspection, using
`GL_TEXTURE_2D` binding queries while preserving its distinct uniform type name.
Arrays and other sampler types remain unsupported/inventory-only, except watched
aliases receive an explicit unknown record. This does not add texture bindings.

Both texture and nonzero bound sampler-object parameters include comparison mode
and function when supported, with numeric values and recognized GL names. Effective
parameters come from the sampler object when it overrides texture sampling;
texture base/max level and allocation remain properties of the texture object.
Unsupported comparison capability remains unknown. Comparison state is reported,
not changed, and does not by itself prove a shadow lookup equivalent or valid.

`texture_level_zero` records width/height/internal format for a bound nonzero 2D
image, and actual queried depth for 3D. Two-dimensional records do not invent a
queried depth. Unsupported getters, absent texture objects, zero/nonmeaningful
dimensions/formats or exceptions remain unknown. This finally permits an actual
driver allocation check for the source-declared 64³ noise textures; older reports
did not query allocations and must not be relabeled as having that evidence.
No pixel contents, other mip allocations, completeness or fetch counts are read.

The scan remains bounded to 64 first-seen numeric program IDs per JVM and 4096
active uniforms per program, with a new 1024-byte/name bound. Truncation is recorded.
Relinking/reusing an already-seen numeric ID is not a fresh observation; use a fresh
diagnostic JVM for independent controls/reloads. Existing reports remain historical
and are not migrated. Fresh profile/log roots are required for new acceptance.

The only transient state change is active texture selection for binding/parameter
getters. The existing `finally` restores the original active texture even after
a sampler query failure, and before/after values are reported. No texture/sampler
binding, sampler parameter, current program, uniform or GL error state is set.
All queries run on the owning draw/render thread. Getter calls can stall, so this
observer must remain outside GPU performance timing windows.

## Completed checks and reproduction

In the isolated worktree, `./gradlew compileDevClientJava` passed, including the
separately owned raster callback against the new public method. The independent
Java fixture passed seven groups with no GL context: disabled callbacks, actual
typed readback branches, location zero, unknown/malformed fields, negative
locations, NaN/getter exceptions, unchanged core schema, shadow type/compare names
and 2D/3D allocation formatting. Its fake reader exercises only the three scalar
getter seams; it does not emulate a GL driver or prove runtime state restoration.

Classpath preparation: `./gradlew writeClientLodOnLegacyClasspath`. Compile/run
the fixture with Java 21, `-proc:none`, classpath comprising
`build/classes/java/devClient`, `build/classes/java/main` and the newline entries
in `build/moddev/clientLodOnLegacyClasspath.txt`; put output in ignored
`build/dev-timing-tests` and execute
`com.pp.shaderlod.dev.timing.EffectRuntimeReadbackTest`. No build script change or
test framework was added. Source checks confirm state restoration in `finally`,
absence of uniform/binding/parameter setters and GL error consumption, and the
production JAR remains free of dev-client observer/test classes.

Root/runtime acceptance still needs a fresh opt-in **untimed** diagnostic client:
observe exact linked effect fields and values, scalar shadow comparison state,
allocation dimensions, matching before/after active texture, no GL errors, and
correct callback application after binding. Required shader stages may optimize
some declared fields out; report only those actually linked/read back. Ordinary
rendering/quality/timing acceptance remains separate. Root and the runtime operator
own integration, launch, GPU and exact process handles.
