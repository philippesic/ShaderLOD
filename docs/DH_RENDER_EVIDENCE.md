# DH terminal draw evidence

The opt-in development hook records positive-index terrain `glDrawElements`
delegates that returned normally. The terminal JSON keeps these cumulative CPU
submission counts separate from DH's render-list debug statistics. Neither a
loaded mod, a nonempty render list nor submitted geometry alone establishes
visible distant terrain; that still requires matching scene/capture evidence
under `docs/EVALUATION.md`. Java compilation and real mixin/runtime acceptance
remain for the integration/GPU owner. The author launched no client.

## Locked bytecode findings

Inspected artifact: `dev/runtime/DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar`,
DH 3.3.3 on Minecraft 1.21.1 / NeoForge 21.1.253, SHA-256
`864e70def5b0d54d619b940b66bb2784e91d3f3fc253064fdc1b3fc5786cbfab`.
Iris 1.8.12 provides the existing public `ShadowRenderer.ACTIVE` classifier.

`RenderBufferHandler.getVboRenderDebugMenuString()Ljava/lang/String;` reads
private `visibleBufferCount` at bytecode 4, optionally adds `culledBufferCount`
at 52 for its denominator, and prints `VBO Render Count: [...]`. Despite that
label, `buildRenderList(RenderParams)V` sets `visibleBufferCount` from
`loadedNearToFarBuffers.size()` at 609–612. The shadow analogue is set at
595–598. The list adds an enabled nonnull `LodBufferContainer` at 512; it does
not count actual draw calls or allocated opaque/translucent VBOs.

`LodRenderer`'s inner terrain method only delegates that list to
`IDhTerrainRenderer.render(...)` at bytecode 19. The actual NeoForge terrain
implementation performs later skips:

- `GlDhTerrainShaderProgram_neoforge.render(...)` tests `buffersUploaded` at
  181–184 and skips unuploaded containers.
- Its boolean argument selects `vboOpaqueWrappers` at 303 or
  `vboTransparentWrappers` at 311. This is the **opaque flag**, not the outer
  `LodRenderer.renderTerrain(..., boolean deferred)` flag.
- Null VBOs skip at 339; zero vertex counts skip at 370–399; zero VBO or index
  buffer IDs skip at 404–444.
- `ILWJGLService.glDrawElements(IIII)V` occurs at 504 after these checks. It is
  the sole matching invocation (ordinal 0). Arguments are triangle mode 4,
  `(int)(vertexCount * 1.5)` indices, index-buffer GL type, and offset 0.

The unsuffixed `GlDhTerrainShaderProgram` has the same method descriptor and
one identical delegate site. Both present classes are targeted; the report
retains which implementation was actually invoked. Neither inspected terrain
class, `LodRenderer`, nor `RenderBufferHandler` retains exact last-frame
opaque/translucent draw counters. A terminal read-only accessor cannot recover
those counts; the report explicitly leaves last-frame counts unknown.

Exact mixin target method for both classes:

```text
render(Lcom/seibel/distanthorizons/core/render/RenderParams;ZLcom/seibel/distanthorizons/core/util/objects/SortedArraySet;Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V
```

The `@WrapOperation` invocation owner/descriptor is:

```text
Lcom/seibel/distanthorizons/lwjgl/ILWJGLService;glDrawElements(IIII)V
```

It uses ordinal 0, `require=1`, `expect=1`, `remap=false`, plus `@Pseudo` for
DH-absent launches. The original receiver/arguments are passed through once
without changes. Counts increment only after a normal return, for indices >0.
No GL getters, GPU queries or render/list/world mutations are introduced.
This proves a Java draw submission path, not driver completion or surviving
depth-tested pixels. The phase comes from the enclosing exact opaque argument;
world/shadow comes from Iris `ShadowRenderer.ACTIVE` before that call.

A cheap HEAD counter marks exercised renderer/phase hooks, including invocations
whose every buffer is skipped. An unobserved scope is reported as unknown,
instead of silently treating a missing/mismatched hook as zero drawn buffers.
`require=1` causes an incompatible loaded target to fail mixin application;
root must retain that failure and not claim a zero-draw pass. A path that bypasses
these classes remains outside this diagnostic's scope.

Reproduce the read-only inspection:

```sh
shasum -a 256 dev/runtime/DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar
/usr/libexec/java_home -v 21 --exec javap -classpath dev/runtime/DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar -p -c com.seibel.distanthorizons.core.render.RenderBufferHandler com.seibel.distanthorizons.core.render.renderer.LodRenderer com.seibel.distanthorizons.common.render.openGl.terrain.GlDhTerrainShaderProgram com.seibel.distanthorizons.common.render.openGl.terrain.GlDhTerrainShaderProgram_neoforge
```

## Integration and terminal snapshot

The root owner registers `DhDrawEvidenceMixin` in development mixin JSON and
forwards `-PdhDrawEvidence=true` as JVM property
`shaderlod.dev.dhDrawEvidence=true`. Diagnostic runs use `-PgpuTiming=false`.
The owner chooses and records exact component/pack identities, options, scene,
GPU/driver, profile, command, logs and client handle. These counters include
warmup and all earlier opted-in draws; they are not a measured-window GPU result.

After measurement is finished, while the world is still present and on the
render thread, call `DhRenderEvidence.captureTerminalSnapshot()` once and add
the returned Gson `JsonObject` to the terminal workload report. The first call
freezes counters and cached snapshot for this JVM; later calls return copies
without additional DH reflection/file reads. Do not call this from periodic
overlay/report code or before the measurement finishes. The utility does no
file writing; the report owner saves output in its ignored profile/log directory.

The snapshot records installed metadata version, loaded DH artifact path/hash
and whether it matches the inspected jar. Render-list private numeric fields
are only interpreted when that hash matches. It records the raw debug string,
renderer-disabled exception state, retained validation message, and private
visible/culled world/shadow list counts. Each failed/unavailable API/reflection
field is unknown rather than a guessed draw count. World/level/handler resolution
uses the existing read-only `tryGetDhClientWorld`, `getClientLevel` and
`getRenderBufferHandler` path; it never calls `getOrLoadLevel`, renderer creation
getters or buffer upload methods. The validated accessors return current
world/map/atomic-reference state without loading or creating terrain.

Four cumulative scopes report hooked terrain invocations, normally returned
positive-index draw-call counts and submitted index totals: world opaque,
world translucent, shadow opaque, shadow translucent. Zero is meaningful only
for a scope whose renderer invocation was observed. Counts do not identify
distance, covered pixels, nonempty water, shader correctness or normal render
mode. Combine positive **world** submissions with inspected distant geometry,
selected pack/no-fallback logs and exact scene/options to assess DH rendering.
Shadow submissions alone do not establish visible DH terrain. No last-frame
identity or completed GPU timing is inferred.


## Root actual integration and runtime

Root compiled dev sources and production build successfully; production jar
contains no dev entries. Registered mixin is gated off by DevEvidenceMixinPlugin
unless explicit dhDrawEvidence=true, preserving ordinary timing runs.
WorkloadSession captures terminal JSON only after measurement ends; the opt-in
flag is retained in runtime properties.

Diagnostic28154 exit0, originalMakeUp9.5g/minimal/DH3.3.3/lockedIris,
1488×836 shoreline,30s warmup5s capture/fixedwetness0/GPUtimingfalse. Hook
exercised exact _neoforge renderer: worldopaque394725/worldtranslucent389269
positive-index submitted draws, cumulative including warmup. Both scope
invocations4116; total submittedindices26414977962/372874338. No shadow
invocation observed, explicitlyunknown. Loaded DHhash matches pin, renderer
notdisabled by exceptions, fallbackfalse. Lastlist123visible/213culled remains
separate from actualsubmission counters. FinalPNG inspected: distant hazy land
and water visible with normalnear reflections. This establishes active DH
worlddraw integration alongside bounded image evidence, not per-pixel distance/
water/shadow correctness or completedGPUtime. Raw
`dev/logs/makeup-dh-draw-diagnostic-r1-{workload.json,final.png}` and `.log`.
No speedup claim from diagnostic counters or its displayedFPS.
