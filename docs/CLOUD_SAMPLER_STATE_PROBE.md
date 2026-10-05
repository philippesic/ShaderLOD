# Opt-in cloud sampler-state diagnostic

This development-only probe records the actual bound sampler state before Iris
full-screen draws. It does not establish that divergent implicit texture
derivatives are safe, prove a shader compiled/rendered, or measure performance.
No diagnostic client has been launched by the probe author. Runtime acceptance
and Java compilation belong to the GPU owner after integration.

## Pinned source and draw hook

The inspected runtime is Minecraft 1.21.1 / NeoForge 21.1.253 / Iris
1.8.12+1.21.1-neoforge. Local Iris jar SHA-256:
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`.
The B15 archive `build/photon-lighting-tier-no-atmosphere.zip` SHA-256 is
`b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c`.
These are source identities, not runtime acceptance.

The exact mixin target is
`net.irisshaders.iris.pathways.FullScreenQuadRenderer.renderQuad()V`, `HEAD`,
`remap=false`, `require=1`. Bytecode from the locked jar shows:

- `CompositeRenderer.renderAll()V`: `Program.use()` at bytecode 369;
  `CustomUniforms.push()` at 403; `FullScreenQuadRenderer.renderQuad()` at 409.
- `Program.use()V`: bind program at 10, uniform update at 17, sampler update
  at 24, image update at 31, return at 34.
- `FullScreenQuadRenderer.renderQuad()V`: polygon mode override at 0, actual
  `VertexBuffer.draw()` at 7, polygon mode restore at 10.

The hook therefore reads after binding/update and before the draw. It can also
observe other full-screen programs; it does not label a numeric GL program as a
particular Iris pass. The existing `cloud_worley_sampler_candidate` field means
the linked inventory contains an active `sampler3D`, not proven cloud-stage
identity or a guarantee that it is a Worley texture.
Compute dispatches, geometry draws and unused programs are outside this hook.

Reproduce the inspection without starting a client:

```sh
shasum -a 256 dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar build/photon-lighting-tier-no-atmosphere.zip
/usr/libexec/java_home -v 21 --exec javap -classpath dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar -c -p net.irisshaders.iris.pathways.FullScreenQuadRenderer net.irisshaders.iris.pipeline.CompositeRenderer net.irisshaders.iris.gl.program.Program
```

B15 `shaders/program/d0_sky_map.fsh` and `d1_clouds.fsh` declare:

| Source role | Actual linked uniform name | Source type |
| --- | --- | --- |
| Coverage map / cloud shadow map | `colortex8` | `sampler2D` |
| Coverage/base noise | `noisetex` | `sampler2D` |
| `SAMPLER_WORLEY_BUBBLY` macro | `colortex6` | `sampler3D` |
| `SAMPLER_WORLEY_SWIRLEY` macro | `colortex7` | `sampler3D` |

`shaders/shaders.properties` binds deferred `colortex6.1` and `colortex7.1`
to the 64×64×64 R8 Worley data files. Other passes reuse `colortex6`/`colortex7`
for unrelated 2D buffers; the diagnostic reads linked type rather than assuming
the cloud declaration applies to all programs.

## Integration and opt-in

The integration owner must register `CloudSamplerStateMixin` in the existing
development mixin JSON and forward the run property to the JVM:
`shaderlod.dev.cloudSamplerProbe=true`. Nothing is registered in production.
Suggested Gradle forwarding is the same pattern as the other dev toggles:

```groovy
if (project.hasProperty('cloudSamplerProbe')) {
    systemProperty 'shaderlod.dev.cloudSamplerProbe', project.property('cloudSamplerProbe').toString()
}
```

Run a separately reserved diagnostic client with `-PcloudSamplerProbe=true`
and `-PgpuTiming=false`. The owner must choose and record the exact run task,
profile, pack hash, options, scene, GPU/driver, logs and process handle. Queries
and synchronous report writes can stall rendering, so these captures must not
be used as performance samples. Preserve the resulting JSON before another
diagnostic run overwrites the same profile's file.

## Captured state and limits

Output is `<gameDirectory>/logs/cloud-sampler-state.json`, inside ignored
runtime directories. It is rewritten through a temporary file after each new
program. It includes UTC timestamps, GL version/renderer/vendor/GLSL version,
context support flags, and at most 64 unique nonzero numeric program IDs per
JVM. Numeric ID reuse after shader reload will not create a new record; use a
fresh JVM for each shader configuration. There is a 4096-active-uniform scan
limit per program, with truncation explicit in the report.

The `samplers` object retains the watched aliases, including their explicit
missing/inactive entries, and also captures every active scalar `sampler2D`
and `sampler3D` found within the bounded linked-uniform scan. Linked names and
existing JSON field names are preserved; `linked_uniforms` retains the scanned
type/size inventory. Other sampler types (including `sampler2DShadow`) and
sampler arrays outside the watched aliases are inventory only; watched
unsupported types/arrays receive an explicit unknown binding.

Each selected active scalar sampler has its linked type/name/size/location,
`glGetUniformi` unit, bound texture ID and 2D/3D target. The texture object's
min/mag filters, base/max levels, min/max LOD, LOD bias and supported anisotropy
are recorded. If sampler objects are supported, the unit's indexed
`GL_SAMPLER_BINDING` is queried. A nonzero sampler object contributes its own
filter/LOD/bias/anisotropy parameters as the effective sampling parameters;
texture base/max levels still belong to the texture. Allocated mip images and
texture completeness are not queried. Unknown/missing/inactive/unsupported
fields are explicit and cannot be interpreted as safe sampling.

The probe only changes `GL_ACTIVE_TEXTURE` temporarily and restores the actual
prior value in `finally`. It uses raw GL activation and restoration, so it does
not alter Minecraft's cached active-unit bookkeeping. It never binds a texture,
sampler, framebuffer or program, changes texture/sampler parameters, calls
`glFinish`, creates GPU queries, or consumes `glGetError`. Legal queries are
capability/type guarded; Java query failures are recorded or stop the probe.
Driver GL errors are not detected by return values alone, so the report labels
queried values as unchecked and the owner must inspect GL/runtime logs.

The first draw's bindings can differ from later weather/options/pipeline state.
A finite snapshot cannot prove branch uniformity, defined implicit gradients,
all cloud layers/options, or harmless literal-count dispatch. Missing cloud
samplers in one profile mean missing evidence for that density path. A sampling
review must combine this snapshot with exact source/GLSL rules and retain any
unresolved derivative behavior as a blocker for the literal probe.

## Scalar 2D extension for the next Miniature diagnostic

The selection extension enables the next original-Miniature full-screen
diagnostic to capture `depthtex0` and color inputs such as `colortex0`,
`colortex6` and `colortex7` if those exact names are active scalar `sampler2D`
uniforms. It also captures other actual linked scalar 2D/3D names, so an Iris
rename is not assumed to preserve a source alias. This source change has not
yet been compiled or exercised by its author; the root owner performs both
after the current client run is terminal.

The hook still covers only `FullScreenQuadRenderer` draws. It cannot establish
BSL water/gbuffers sampling state or any geometry draw's texture state. A
non-mipmap filter or a zero LOD range constrains that captured binding's
sampling parameters; it does not prove defined derivatives throughout
divergent control flow, branch uniformity, driver instruction behavior, full
texture-coordinate equivalence or pixel parity. Miniature source/adapter
acceptance still needs its own rendering and quality comparisons.

The previously retained r2/r3 reports and their bytes remain unchanged. The
additive `sampler_selection` header describes newly generated reports; existing
JSON field names, 3D candidate flag, 64-program limit, 4096-uniform scan limit,
opt-in, getters and active-texture restoration are retained. The extension
introduces no new GL query/parameter/binding operation; it applies the existing
scalar sampler inspection to more linked uniforms and can therefore perform
more diagnostic getters. Use a fresh reserved profile/JVM with GPU timing
disabled and retain its report separately.


## Root actual runtime evidence

Integration/build passed; diagnostic23176(r1) and78902(r2) exit0 withIris/B15
in-world, GPUtimingfalse andexplicitlocalcoverage true. R1source aliases missed
remapped3D names; retainedr1JSON/log. Rootaddedbounded linkeduniform inventory
andallactive3D sampler capture. R2captured22programs; cloudcandidate17/18
coveragecolortex8/noisetex/customtex0/1/2 actualbindings hadsamplerobject0,
linearMIN/MAG andanisotropy1. Noise/custom3D min/maxLOD0/base/maxlevel0;
coveragebase0/maxlevel1000/minLOD−1000/max1000 butMINfilterLINEAR (non-mipmap).
Active texture unitrestored before/after for everycapturedprogram.

Rawdev/logs/cloud-sampler-state-diagnostic-r2.json SHA256
b72de09d2adc5b70ff3d6dac7a1d1ff50de19e04664ae146b158f70b1895cd67.
This removes practicalmip/anisotropicdependence for thispinned source/profile
experiment, not a universallanguage or otherprofile derivative guarantee.
Queriesdo notconsume glGetError; enumvaluesarevalid butruntime-transformed
texture coordinates/driverinstructions remainunverified. No timing/effectgain
fromthisdiagnostic. Rootauthorizes controlledliteral-source assembly only
qualified tothisreport; actualrenderer output/quality andGPU testsstillrequired.


## Root actual scalar-2D Miniature capture

55863 exit0, originalMiniature2.19/minimal/noDH/1488×836,20s warmup5s capture,
GPUtimingfalse (0samples). Report
`dev/logs/miniature-sampler-state-diagnostic-r1.json` SHA256
b04ab014bd202a8e4ba48a539bb1c65867e10e13b3fa4694ef37e2f6187cc68e,
companionworkloadjson retains exactsource/options/versions/classes/inputs.
Linkedprogram16 contains depthtex0 andcolortex0/6/7. Allsamplerobject0/aniso1/
bias0/base0; depthMIN/MAGNEAREST, colorsMIN/MAGLINEAR. Filtersnon-mipmap,
butLOD±1000 andmaxlevel1000 arebroad, notlevel0clamping. Activeunit33984
restored. Numericprogram16 is not independentlynamedfinal; exactmatching
uniformset/source helpsidentify a candidate, notallstages/reloads guarantee.
Controlled sameprofile sourceexperiments stillrequireactualpixels/motion;
no derivative/parity or shaderoptimization claim followsfromthesegetters.
