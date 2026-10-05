# Photon literal-secondary private probe and sampling boundary

2026-10-05. Owner: fresh performance chat's bounded `photon_cloud_guard` worker.
**Controlled enabled assembly is now qualified by the actual native r3
diagnostic**, which supersedes the r2 resolution/profile assumption. The experiment remains
default off. Enabled assembly requires the exact reviewed sampler report and
validated constraints; other profiles/state/version combinations remain outside
this qualification. The original arm preserves B15 bytes, and compile-time probe
off selects exact B15 shader source. The initial sampling concern and blocked
design checkpoint are retained below and in the manifest.

Sixteen lightweight structural/sampler-constraint tests pass. No full archive
was assembled and no compiler or client ran by this worker. Root's actual
sampler evidence applies to B15. Root's native literal pilot rendered and
regressed, as retained below; pixel parity, moving quality and incremental
benefit remain unaccepted.
This worker changed only its four newly assigned builder/manifest/test/document
files. The earlier hoist, original/shared Photon patches/helpers/tests and Java
remain untouched. Root owns all builds, the GPU, reloads and runtime decisions.

## Input and intended source shape

Immutable baseline is B15 `build/photon-lighting-tier-no-atmosphere.zip`,
SHA-256 `b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c`.
This design does not use the hoist archive or current main-tree overlays.
The original Photon v1.3b attribution and `LICENSE` are preserved; license
SHA-256 is
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
B15's entire embedded `shaderlod-build.json` is retained as baseline provenance,
SHA-256 `5653a52f590ce10cd75fbaff24f1ed053e49564ade7b4c79bb2747f69feed084`.
No publication or distribution occurred.

[cloud-literal-probe.json](../patches/photon-experiments/cloud-literal-probe.json)
contains source changes only for three cloud members and an independent
default-off `PHOTON_CLOUD_LITERAL_SECONDARY_PROBE` settings declaration:

| Member | B15 SHA-256 | Candidate member SHA-256 |
| --- | --- | --- |
| `shaders/include/sky/clouds/cumulus.glsl` | `a17f8fba86589b1b138063bbd3d1c261055f135b79ad4985579786abb1773d9c` | `6aaf8e76d9eea451c175906cc79e2e2974b42331d295f5575924c57a7bc145c8` |
| `shaders/include/sky/clouds/altocumulus.glsl` | `d13de78fb87924295a4dd90af455a1bfa86776a708e1b461e2a158636d550255` | `b0e5896fce5e70a28ba30a47d69f4c9be0f4d55141020be79a9d4eb77a70c601` |
| `shaders/include/sky/clouds/cumulus_congestus.glsl` | `3e17ef9f8909861ec11a5d2bf8da7883333cb2816a3e1def2fed74d82d52f5b2` | `1794102547224d0c18848e5e0457426f4469f02b9cf8ba03e33baca3dff5ad70` |
| `shaders/settings.glsl`, probe off | `d1f571e5d96ecffdf7ebcfcf28a13d34dc5bfd5c3606702e06832f95f88aa3cd` | `be53384080cf670fd5590783cca208bd23b399cdd61afbe1e40e9f47ab5243e6` |
| `shaders/settings.glsl`, probe on | same B15 source | `519590e2c44ef5c804a1431ebd07ad8506e371236d704a07c3012a4894d4b6da` |

The existing generic optical-depth function remains literal B15 source. Each
private clone changes only its name/signature and adds local
`const uint step_count = 2u` for light or `1u` for ambient, plus
`const uint original_steps =` the layer's existing compile-time option.
The complete function body, including base division, count-inequality branch
and left-associated extent-correction expression, remains unchanged:

```glsl
(float(step_count) / float(original_steps)) *
    (exp2(float(original_steps)) - 1.0) / (exp2(float(step_count)) - 1.0)
```

The occupied-sample call site selects a private light kernel only for
`lighting_steps != ORIGINAL_LIGHTING && lighting_steps == 2u`, or ambient kernel
only for `ambient_steps != ORIGINAL_AMBIENT && ambient_steps == 1u`. All other
counts, full near/tier-zero/live-disabled states and equal option counts call
the original generic function. Extent setup remains per-density invocation;
there is no hoist or empty-ray precomputation. Primary counts, ray positions,
jitter, secondary step growth/update order, density calls, scattering and
output math are unchanged source. Selecting the switch off recovers
each B15 member exactly in structural fixtures. These arithmetic/source facts
do not prove sampler or driver parity.

No distance units or quality policy change. B15 converts cloud-shell entry
distance to camera-relative world blocks using `CLOUDS_SCALE`; existing bands,
transition noise, cached-sky policy and all live presets remain. Secondary
lengths retain scaled cloud coordinates and the existing extent correction.
No temporal quality, sampling phase, lighting budget or new transition is added.
Original/disabled remains the quality fallback until actual literal-probe
rendering and moving-quality evidence supports any further decision. This pack
option has no new Java configuration or uniform transport.

## Why arithmetic equivalence is insufficient

The new count predicate varies between rays. It places cloned density
evaluation inside a new branch. B15 density functions contain implicit
`texture()` operations whose control-flow context can therefore change:

- Cumulus: local coverage `colortex8` at member line 51 and 3D Worley samplers
  at lines 82/84.
- Altocumulus: `noisetex` at lines 43/45/47 and 3D Worley at line 91.
- Congestus: `noisetex` at lines 61–66 and 3D Worley at lines 95/97.

The old code already calls density from varying loops/branches. That fact does
not establish that another branch preserves implicit derivative availability,
LOD selection or filtering. Source-body equality and numerical arithmetic
fixtures cannot establish texture-output parity. No density lookup was changed
to explicit LOD, gradients or a different jitter to bypass this issue.

## Pinned sampler evidence and limits

Read-only inspection used the exact development Iris artifact
`dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar`, SHA-256
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`.
`javap -c -p` inspected allocation paths; no class was compiled or modified.
These initial observations are static creation-path evidence. The later live
capture below supplies final bound sampler constraints for one profile.

| Input | Verified source/bytecode evidence | Still unverified |
| --- | --- | --- |
| Worley volumes | B15 properties request `texture.deferred.colortex6.1 = image/worley_bubbly.dat TEXTURE_3D R8 64 64 64 RED UNSIGNED_BYTE` and corresponding `colortex7.1` swirley data. Both `.mcmeta` files request blur=true/clamp=false. Iris `CustomTextureManager.createCustomTexture` routes raw 3D data into `GlTexture`. `GlTexture` selects MIN/MAG LINEAR for blur=true and sets MAX_LEVEL=0, MIN_LOD=0, MAX_LOD=0, LOD_BIAS=0. | Final texture/sampler object bound to each affected program, any subsequent filter/anisotropy state changes, and actual runtime validation of those properties. |
| Custom `noisetex` | B15 requests `texture.noise = image/noise.png`; its `.mcmeta` requests blur=true/clamp=false. Iris routes PNG into `NativeImageBackedCustomTexture`; constructor explicitly sets MIN/MAG LINEAR for blur=true. Its upload calls NativeImage at level 0. | Full inherited texture allocation/state path and final bound sampler overrides/anisotropy; level-0 upload alone is not proof of all sampling state. |
| Local coverage `colortex8` | `d1_clouds.fsh` defines local coverage when `CLOUDS_CUMULUS_PRECOMPUTE_LOCAL_COVERAGE` is enabled. `d0_sky_map.fsh` also defines it unconditionally for the cached-sky path. No B15 shader declares `colortex8MipmapEnabled`. Iris `RenderTarget.setupTexture` creates noninteger targets with MIN/MAG LINEAR. | Final bound coverage texture/sampler state, later pipeline mip/filter changes and anisotropy. Absence of one pack declaration does not prove complete runtime state. |

`NoiseTexture` was also inspected: it creates LINEAR filtering, MAX_LEVEL=0
and zero LOD bounds/bias. B15 uses the custom PNG path, so that class's state
cannot substitute for proof of the actual `noisetex` path. None of the inspected
creation methods explicitly establishes final anisotropic filtering state for
every sampler after binding. Neither a screenshot nor a clean compile would
establish that missing derivative/sampler proof.

At the initial checkpoint, these creation paths suggested base-only noise
sampling but did not justify the new branch across all density inputs. The
worker retained a design-only preview and blocked enabled assembly. This was a
real source-inspection finding, not a claim that B15 had visibly failed. Root
then commissioned a separate read-only development sampler probe; this worker
did not alter Java or add telemetry. The initial finding remains in
`initial_safety_finding` in the manifest.

## Actual sampler checkpoint and controlled qualification

Root native session **77521 ended with exit 0**, collecting
`dev/logs/cloud-sampler-state-diagnostic-r3.json`,
`dev/logs/cloud-sampler-state-diagnostic-r3.log` and
`dev/logs/cloud-sampler-state-diagnostic-r3-workload.json`.
The sampler report SHA-256 is
`5dbc7edf7fa5b11cebd2a4ac948a2c8e9553ddf467f5141831c29e162e283e67`;
the workload report SHA-256 is
`a1807bcab12be4b0cf2580763567a8b07d203ca2b8d812b48a90e70e7a765fef`.
The actual diagnostic profile is
`run-workloads/cloud-sampler-state-probe/forest/secondary-base`; its raw report is
`run-workloads/cloud-sampler-state-probe/forest/secondary-base/logs/cloud-sampler-state.json`.
Its source profile is `run-workloads/native-effect-isolation/forest/cloud-only`.
The workload independently records exact B15, framebuffer 2976×1672 at start/end
and top level, logical window 1488×836, minimal stack/DH present, effective local
coverage true, GPU timing false and zero GPU samples. These are different
dimension and profile fields; neither is inferred from the source profile.
Runtime is Apple
M3 Max/OpenGL `4.1 Metal - 91.7`, the exact Iris artifact above and locked
Minecraft 1.21.1 / NeoForge 21.1.253 / Sodium 0.6.13 / DH 3.3.3.

The report observes numeric programs 17/18 with the complete cloud-input set:

| Linked input | Bound state in both measured programs |
| --- | --- |
| `customtex0/1/2`, sampler3D | Iris-remapped 3D inputs, textures 57/54/55; sampler object 0; MIN/MAG `GL_LINEAR` (9729), MIN/MAX_LOD 0, BASE/MAX_LEVEL 0, LOD_BIAS 0, anisotropy 1. |
| `noisetex`, sampler2D | Texture 64, sampler object 0; MIN/MAG LINEAR, LOD/level bounds 0, bias 0, anisotropy 1. |
| `colortex8`, sampler2D | Coverage texture 62, sampler object 0; MIN/MAG LINEAR, MIN/MAX_LOD −1000/1000, BASE/MAX_LEVEL 0/1000, bias 0, anisotropy 1. Its MIN filter is non-mipmap even though level/LOD bounds are broad. |

Every captured program restored active texture state. The diagnostic reads
after Iris sampler binding at fullscreen draw. Broad sampler-candidate flags
also include program 21 with a single atmosphere LUT; the validator selects the
complete five-input set and does not assign stage names from numeric IDs or
uniform names. Linked remapped aliases explain why original `colortex6/7`
uniforms can be absent while actual custom 3D inputs are present.

The actual non-mipmap linear filters and anisotropy 1 constrain derivative-based
filtering for this measured case. Combined with source inspection, root
authorized a private active-assembly/rendering experiment. This is not universal
GLSL derivative proof or visible-output parity. Queries are explicitly labeled
`queried_without_gl_error_consumption`; they do not consume `glGetError`.
Allocated mip images/texture completeness were not queried. The capture is the
first fullscreen draw per numeric program ID in one JVM, so later state changes,
reloads, other profile options, versions, platforms or samplers need fresh review.
Even a correctly compiled candidate still requires actual pixels and motion.

## Static checks and callable boundary

Executed:

```sh
python3 -m unittest discover -s tests -p 'test_photon_cloud_literal_probe.py' -v
```

Result: **16 tests passed**. They check exact input/license/metadata, default-off
design restoration, private names/counts and literal body equality, eligibility
truth tables, original occupied-sample call placement, unchanged nonassigned
archive members, corruption/ambiguous-anchor/output guards, retained initial
sampling finding, exact native r3 sampler/workload reports, corrected dimension/
profile context, missing/changed/r2-report rejection, and negative
filter/mipmap/anisotropy/binding/scan/state-restoration constraints. No numerical
texture/derivative fixtures were run. `preview_design` validates source in memory;
these tests never assemble a full pack or invoke a compiler/client.

The builder accepts exact B15. `original` preserves baseline bytes; `disabled`
contains the default-off option and conditional kernels, with an exact original
source fallback; `enabled` defines that option. Each arm adds
`shaderlod-cloud-literal-probe.json` provenance retaining the baseline metadata,
member pins, evidence hash and controlled-profile qualification. Its
default output name is ignored
`build/Photon-CloudLiteralSecondaryProbe-disabled.zip`; ordering/timestamps/
permissions/compression are deterministic. No such archive was built here and
no new output ZIP hash is asserted here. Enabled `build` requires the reviewed native r3 report,
checks its exact hash and measured constraints before any archive write, and
rejects changed/missing evidence. The default report path is the preserved raw
path above; `--sampler-evidence` can locate an identical preserved copy. This
does not enforce runtime sampler state after the ZIP is moved or loaded.
`validate_sampler_report` returns constraint checks only, without a hash,
resolution, profile or stage-identity claim. `load_sampler_evidence` computes the
actual file hash, matches the reviewed r3 pin and then adds `report_sha256`.
The r2 report can satisfy the same sampler constraints without qualifying as
native r3 evidence. Its metadata and archives remain historical records.

Root's next build commands, after it owns a terminal runtime checkpoint:

```sh
python3 scripts/build_photon_cloud_literal_probe.py --arm original
python3 scripts/build_photon_cloud_literal_probe.py --arm disabled
python3 scripts/build_photon_cloud_literal_probe.py --arm enabled
```

The shader entry points are Overworld `deferred1.fsh` →
`program/d1_clouds.fsh` and cached-sky `deferred.fsh` →
`program/d0_sky_map.fsh` through their cloud includes. Main/helper protocol,
water, fog, primary cloud march and other archive members remain reserved.

## Runtime quality and GPU measurement still needed

The prior hoist pilot reported B15/probe-off/probe-on GPU means
14.538/14.454/18.998 ms. Its same-JVM global budget signal remained approximately
5.1–5.7%, comparable to B15. Those results did not establish incremental hoist
benefit, and that experiment stays default off. They motivate a separate
specialization hypothesis, not a gain claim for this probe.

Local `const` values and literal counts can permit specialization; they do not
guarantee unrolling, lower physical instructions or faster rendering. Extra
private kernels/branches can increase program size, register pressure and
divergence. The dispatch is per occupied sample; no new ray count
or selected scalar sample budget changes. No disassembly/occupancy data exists.

Root must now separately establish literal-probe driver compilation/rendering
and moving-cloud parity in the qualified context, then compare exact B15,
probe disabled and enabled in balanced repeated serial controls. Measure cloud
pass and whole-render GPU elapsed time, frame/render-thread limits and clear/
sparse versus dense storm workloads. Preserve locked Minecraft/NeoForge/Iris/
Sodium/Photon/DH artifacts, resolution, history warmup, terrain radius, weather,
actual preset/count options and advancing temporal inputs. Verify near/live
disable, generic fallback counts, both layer paths, cached sky and transition
motion. Root owns those reloads and measurements. A flat/regressing qualified
pilot closes the hypothesis; no combined rewrite or support promotion follows.


## Root controlled assembly under the prior r2 qualifier

After the DH baseline client was terminal, root repeated14 source/report tests
and assembled all three arms. ZIP SHA256:

- Original-provenance arm: `60cef2644560fc918fd52dbc4f9c070fc6927f8aed09c14cc7a322f542b27202`.
- Disabled: `344181d05ebc143be843e4d8ba8b873dc384ce915072cde5edf17723518647f0`.
- Enabled: `4bd113c947daa57d191370067c6ba1514ff350602298e5dfa2ec15b150dc4758`.

Runtime comparison uses untouched B15 as baseline, plus disabled/enabled
archives. Fresh profiles clone stopped B15 cloud-only/minimal/DH configuration
and preserve effective local coverage=true (Iris may omit this source-default
option from its normalized file). Requested/effective options remain recorded.
At that assembly checkpoint, candidate rendering, quality and speed were
pending; the following section retains the actual pilot.


## First actual driver pilot: regression retained

Root21212 ended exit0, all three accepted exact-source/options/input checks.
Native2976×1672, minimal/DHpresent, B15 cloud-only localcoverage=true,
40s warmup20s capture, GPU elapsed1/16, fixedwetness0. No sampler diagnostic
was active during timing. All rendered with fallbackfalse. Root inspected
baseline and enabled final PNGs: same scene structure and no gross cloud or
terrain failure; advancing animation/mobs differ, so these are not pixel parity
or moving-quality acceptance.

| Arm | FPS | Mean render GPU ms |
| --- | ---: | ---: |
| Exact B15 |62.186|14.5028|
| Probe disabled |63.022|14.6946|
| Probe enabled |51.225|18.6599|

The enabled single pilot regressed28.7% in GPU interval versus B15 and27.0%
versus disabled. Between-JVM/source-order variance and compiler behavior remain
unresolved; no physical instruction, register or universal kernel cause is
asserted. It establishes no incremental benefit, and the private probe remains
off/unintegrated. No need to promote it or spend a broad matrix before a useful
qualified signal. Raw `dev/logs/photon-cloud-literal-pilot-r1` preserves all
failures, actual samples, sources/options/artifacts/window/camera and captures.
The prior sampler constraints do not turn this image into derivative parity.


### Resolution qualification correction

Root audited r2's actual workload report after this pilot: framebuffer1488×836,
not2976×1672. The source clone/options match, but the nativepilot extrapolated
resolution without a native-state snapshot. Earlier manifest/nativecontext
text is retained in built provenance and identified here as erroneous. The
measured native regression stays valid. Actual native r3 now refreshes the
sampler-state qualifier; it does not turn the earlier pilot into pixel parity,
moving-quality acceptance or an optimization win.

The manifest preserves r2's previous qualifier in `prior_qualifications`, with
root's corrected 1488×836 framebuffer and actual diagnostic profile alongside
the mistaken initial context. Current qualification points to r3 and its
workload hash. No old raw report, built archive or negative pilot was rewritten.
Evidence/context metadata and the evidence-loader hash labeling changed in this
refresh; all literal patch and shader member fingerprints remain unchanged.
A future rebuild will have a new
manifest/build-metadata ZIP hash and must be labeled separately from the r2
archives listed above. This worker assembled no new archive or client.
