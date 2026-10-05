# Opt-in raster sampler and uniform readback

2026-10-05 source checkpoint. `RasterSamplerStateMixin` adds a development-only
callback immediately before actual Sodium indexed raster submission. It feeds
the existing bounded `CloudSamplerStateProbe.beforeRasterDraw()` observer so
linked water/terrain reflection programs can be inspected as well as fullscreen
programs. This is readback infrastructure, not a shader optimization or a GPU
timing instrument. No client, shader reload or GPU operation was performed here.

## Exact pinned target and capture boundary

Local Sodium 0.6.13 artifact:
`dev/runtime/sodium-neoforge-0.6.13+mc1.21.1.jar`, SHA-256
`7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403`.
The target class is
`net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList`;
its class member SHA-256 is
`60d7958e257a4ee5c22e9ca299dd8ca124c2f113d0d970b731526f510dae538b`.

The exact target method descriptor is:

```text
multiDrawElementsBaseVertex(Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlIndexType;)V
```

`javap -p -s -c` established one native submission at bytecode offset 37:

```text
INVOKESTATIC org/lwjgl/opengl/GL32C.nglMultiDrawElementsBaseVertex (IJIJIJ)V
```

The mixin uses `@At(value="INVOKE", target=..., shift=BEFORE)` with `require=1`,
`allow=1` and `remap=false`. It observes the current GL program immediately before
that native call, after argument assembly. Its callback invokes only
`CloudSamplerStateProbe.beforeRasterDraw()`; it does not change/cancel the draw,
evaluate a uniform supplier, select a shader, add GPU queries or infer a pass from
`ShadowRenderer.ACTIVE`.

Read-only pinned bytecode inspection also established the binding chain:

1. `DefaultChunkRenderer.render` invokes `ShaderChunkRenderer.begin` at offset 3
   before matrices/region uniforms and `executeDrawBatch` at offset 211.
2. `ShaderChunkRenderer.begin` selects the program, invokes `GlProgram.bind` at
   offset 33 and `ChunkShaderInterface.setupState` at offset 46.
3. Pinned Iris 1.8.12 redirects the selected Sodium program through
   `MixinShaderChunkRenderer.redirectIrisProgram`. Its `SodiumShader.setupState`
   performs `updateUniforms` at offset 5, image update at 12 and texture binding at
   16; `updateUniforms` updates samplers, program uniforms and custom uniforms.
4. `executeDrawBatch` calls `beginTessellating`, then the command list's
   `multiDrawElementsBaseVertex`. The inspected immediate method contains no
   program rebind or shader-state preparation before native submission.

Iris artifact SHA-256 is
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`.
Relevant Sodium class hashes are
`de983844ab8aece524a9830a9b6ee173cc5e4b5a1777ba06cf0d8a6af5c150bc`
(`ShaderChunkRenderer`) and
`e57ee69430ac4a79ab4608e2a3341b5af2ce08c5ecc84406502a9d0379a6333f`
(`DefaultChunkRenderer`). This proves the source/bytecode placement rather than
assuming method HEAD sees the final bindings. Mixin transformation and actual
runtime readback still require their own acceptance.

## Opt-in baseline and observer scope

`DevEvidenceMixinPlugin.shouldApplyMixin` enables **only this new mixin** when
`Boolean.getBoolean("shaderlod.dev.cloudSamplerProbe")` is true. An unset/false
property rejects its application; ordinary baseline Sodium classes receive no
new raster callback and no getter calls from this hook. The existing
`DhDrawEvidenceMixin` property rule and all other mixin decisions remain intact.
This does not remove the pre-existing fullscreen hook or other timing mixins.

The existing observer retains its own enabled guard, first-seen numeric-program
deduplication, shared 64-program cap, bounded 4096-uniform scan, failure handling,
and texture-active-unit restoration. The raster entry's `draw_origin` is exactly
`sodium-raster-submission`; fullscreen entries use `iris-fullscreen-quad`. No
program-name, water-stage, world-versus-shadow or successful physical draw claim
follows merely from that origin. A program first captured elsewhere is not
recaptured here. Program ID reuse and shader reloads therefore require fresh JVMs
for clean first-draw comparisons. First draw is not a settled camera/reload proof.

The observer reads actual linked GL program values through its existing core and
extension schema. Missing/inactive/unsupported fields remain unknown. Selection
is not widened through application-side supplier reevaluation. Getter/IO overhead
can stall and materially change performance; diagnostic runs must have GPU timing
disabled and must not be compared to ordinary throughput runs.

Everything added or changed here is under `src/devClient`, with a dev mixin
resource and development documentation/tests. Production `jar` uses the main
source set; it should contain no `RasterSamplerStateMixin`, observer, plugin or
`shaderlod-dev.mixins.json`. Expected integration rebuild scope includes the new
raster mixin, `DevEvidenceMixinPlugin`, mixin resource and the separately owned
observer/schema classes. Re-freeze the complete dev class/resource trees before
any later timing capture; do not mix an older class tree with the new resource.

## Source acceptance and remaining runtime evidence

`python3 -m unittest discover -s tests -p test_raster_readback_hook.py -v`
passed all three tests in 0.470 seconds, including the local pinned artifact
fixture. Tests inspect the exact native instruction/descriptor/hash, source-only
single callback and registration, then compile and execute the **real** plugin
against interface stubs for false/true combinations of both properties and every
existing registered mixin. They do not apply Mixin or create a GL context. Missing
pinned artifacts/JDK cause the explicitly optional artifact/plugin fixture to
skip, so inspect test output before treating those checks as demonstrated.

Shared `./gradlew compileDevClientJava` passed in 949 ms under the observer owner's
coordinated build, with this hook and the new `beforeRasterDraw()` API present.
Compilation establishes Java integration only. Runtime work is
reserved for the sole root/operator after integration. Required proof is a fresh
opt-in JVM with no GPU timing, successful pinned Mixin application at the native
instruction, actually rendered terrain/water, raster-origin linked program
entries, sampler/core/extension values and query-state restoration. Source shader
identity and whether the draw was shadow or world are separate evidence. A later
baseline should verify the false property omitted the new raster injection.
This hook establishes no speedup; performance comparisons require separately
frozen, probe-disabled original/optimized GPU and total-frame measurements.
