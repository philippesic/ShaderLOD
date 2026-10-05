# Miniature 2.19 private refinement probe

Prepared 2026-10-05. Default mode zero selects original SSR. This is an isolated
source experiment, separate from the proven core diagnostic; it does not change
that builder, helper or source. Main Java, Photon, MakeUp and shared helpers
remain unchanged. Root owns all archive assembly, shader reloads, driver work,
quality captures and GPU measurements. No derivative was built or compiled by
this worker, and no quality, support or performance claim follows.

## Source and attribution

Exact publisher Miniature 2.19 release SHA256:
`5f224e907657f3ffd95d37e37c27e555deef64fa56da1e13374f2c295241d7f0`;
publisher SHA512 and source hashes are in the refinement manifest/candidate lock.
The release differs from pinned Git source. **Miniature**, **Mateus A. Kreuch**,
Copyright (c) 2022 Mateus A. Kreuch, MIT license:
[official source](https://github.com/mateuskreuch/minecraft-miniature-shader),
[release](https://modrinth.com/shader/miniature-shader/version/LWmZ94RG).
The release omits its MIT notice; the existing restored LICENSE.Miniature
(SHA256 `397d80246d261805b953865428e9c244e5a7bd2bbbb6f3b37f16e884e697e4d1`)
is packaged unchanged, alongside editable source, full literal patch and this
modification notice. Private kernels derive from original getReflectionColor
under MIT terms. Modification date: 2026-10-05. No publication is performed.

Four source/UI members change: shader.h, program/final.fsh, shaders.properties,
lang/en_US.lang. Original common/getReflectionColor.fsh, transformations, math,
normal reconstruction, material mask, roughness/world quantization, composition,
lighting and shadows are copied unchanged. The new private helper is
common/shaderlodRefinement.glsl. Entry points remain the original GLSL120
world0/world1/world-1 final wrappers → program/final.fsh. This experiment uses
the original release directly and does not include the earlier core color cue.

## Qualification actually available

Root's original sampler diagnostic session 55863 is terminal. Exact report
`dev/logs/miniature-sampler-state-diagnostic-r1.json`, SHA256
`b04ab014bd202a8e4ba48a539bb1c65867e10e13b3fa4694ef37e2f6187cc68e`;
companion `miniature-sampler-state-diagnostic-r1-workload.json`, SHA256
`3e1549282d8659d12888c9f842de1fd6fc7def1fe34e43d910526c552af6ee5f`.
Original release, minimal stack, main mod/DH absent, overworld shoreline,
1488×836, native NORMAL effective options. Logical workload profile is named
high; qualification uses actual effective options, not that label. The logical
request still says gpu_queries=true; actual runtime gpuTiming=false and empty
gpu_samples establish that this was a sampler diagnostic, not a GPU timing run.

Linked program 16 has exact scalar sampler2D aliases depthtex0/colortex0/6/7.
Effective sampler object zero, anisotropy1, bias0, base level0. Depth MIN/MAG
NEAREST; all three color buffers MIN/MAG LINEAR, with non-mipmap MIN filters.
MIN/MAX_LOD are -1000/+1000 and MAX_LEVEL is 1000. These are **not** a level-zero
clamp; allocated mip images/completeness were not queried. Active texture was
restored. These observations support a controlled explicit base-level trial on
the same profile; they do not establish derivative bindings, pixel parity or
safe implicit derivatives under a newly divergent branch.

The builder checks exact evidence file hashes, original archive identity,
actual shader/fallback state at both boundaries, framebuffer, original mod/DH
absence, minimal stack, GPU-timing disable, native NORMAL options, exact loaded
Iris hash and each effective sampler tuple. It also verifies the inspected
Iris artifact and translator class hashes. Missing or changed qualification
fails closed even for a default-off assembly; no bypass flag is supplied.

Qualified native options include outer steps10, stride1.6, binary steps4,
REFLECTIONS10/BLOCK_REFLECTIONS5, shadow1024/distance128/entity0/pixel16,
enabled shadows/flat lighting and original wave1/speed2. Exact values are in
the manifest. This gate qualifies the original evidence only; root must verify
that the derivative actually retains these settings/sampler state.

## GLSL syntax inspection, not a compilation pass

The actual loaded Iris artifact is
`dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar`, SHA256
`3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83`.
Its metadata version string is local/snapshot, so the artifact hash is the
reproducible identity. Read-only `javap -c -p` inspection shows:

- TransformPatcher.lambda$static$1, legacy/no-profile branch: upgrades versions
  below330 to GLSL33 and CORE profile before the composite transformer.
- CompositeTransformer.transform invokes CommonTransformer.transform.
- CommonTransformer.transform renames texture2DLod calls to textureLod
  (bytecode offsets861–867), alongside ordinary texture2D → texture.

The three exact class hashes are in the manifest. This supports expressing
private kernels as texture2DLod(...,0.0) in a pack whose source wrappers remain
GLSL120, relying on the pinned Iris translation to core textureLod. It is not
standalone GLSL120 portability, a parser/driver pass, or a guarantee the new
source links. Root must inspect the transformed source and actual driver log;
if the explicit syntax fails, preserve/reject that trial rather than replacing
it with divergent implicit texture calls.

## Modes, disabled baseline and core boundary

The sole new shader option is SHADERLOD_MINIATURE_REFINE_MODE, default **0**:

| Mode | Eligible behavior | Intended control |
| --- | --- | --- |
| 0 | Original function/call; private helper excluded | Disabled source baseline |
| 1 | Literal four refinements with explicit base-level depth/color sampling | Sampling-form/full-quality-intent control |
| 2 | One literal 4/3/2 explicit kernel selected by originating radial distance | Distance refinement quality/work experiment |

The private helper is included only for modes1/2, native SSR_BINARY_STEPS==4
and SSR_MAX_STEPS==10. All other modes/counts take the untouched original
function directly. Mode1/2 dispatch also returns the original function when
version is absent/0/old1/unknown3, global enable is zero, or native stride is
not1.6. This eligibility decision depends only on core uniforms and compile-time
native options; original implicit texture source stays in uniform control flow.
There is no absent-mod use of zero near/far distances. Unsupported versions
remain unsupported even if a future transport retains the same field names.

Exactly existing core protocol2 is consumed: version(int), enable(int),
near/ far(float). Preset, transition, debug and per-effect fields are unused.
No new Java API, per-effect mod switch or live budget protocol is claimed.
The shader option selects the trial; existing global enable and near/far select
its runtime eligibility/distance. Core consumption was proven separately by
root's earlier diagnostic session21160, including absent magenta, disabled
yellow and changed96/160→8/16 bands. That does not prove this private function
compiled, rendered, preserved near pixels or avoided work.

## Exact change and quality budget

Each private function is a literal copy of the original SSR function, changing
only its name, the binary-refinement loop bound to4/3/2, and all its depth/color
fetches to explicit base-level syntax. Outer loop, adaptive increment
max(SSR_STEP_SIZE*abs(diffZ)*invR,1.0), reflected direction, offscreen exit,
epsilon/cutoffs, oldPos/curPos hit bracket, vignette/fresnel, final depth guard
and miss result are retained. Original native function and sampling syntax
remain intact. No light/shadow budgets change.

Mode2 computes one length(viewPos) from the originating reconstructed
camera/view-space position in Minecraft blocks. Near clamps to0; far clamps to
at least near+1. At/below near selects4, strictly between near/far selects3,
at/beyond far selects2. It is not feet length, raw depth, axial z, world origin,
reflected hit distance or time-varying noise. Integer transitions are stable for
a fixed camera but can create moving rings; this is deliberately not advertised
as a smooth quality transition. Root must inspect crossings and stop promotion
if quality fails. No second full SSR path or complete-path blend executes.

Only an eligible first hit inside the existing refinement cutoff saves samples:
at most one depth refinement fetch in the middle zone, two in the far zone.
Outer march coverage and first-hit bracket stay intact, but final refined UV,
hit acceptance, thin geometry and reflected color can change. Many pixels miss
or skip refinement already, so full-frame savings may be negligible. Near/full4
uses new fetch syntax and cannot be called exact pixel parity until mode1 versus
original is compared on the driver. All three literal copies enlarge source;
inlining, register allocation and instruction-cache effects can offset savings.
Mode1 allows the compiler to discard unused2/3 copies, so its code shape is not
a complete control for every mode2 compiler effect.

## Reproduction and root runtime controls

After root's GPU session is terminal:

```
python3 scripts/build_miniature_refinement_probe.py
python3 -m unittest discover -s tests -p test_miniature_refinement_probe.py -v
```

Ignored output: `build/Miniature-ShaderLOD-RefinementProbe-2.19.zip`, defaultmode0.
The same ZIP supplies all three option modes; root must set/verify effective
SHADERLOD_MINIATURE_REFINE_MODE explicitly for each trial. Builder copies all
untouched bytes, restores existing MIT notice, ships complete editable source,
literal patches/evidence pins and final member hashes, and uses fixed ordering,
timestamps, permissions and compression. Output cannot overwrite original.
No full ZIP build, compiler, client, shader reload or GPU capture occurs in this
worker task. All tracked changes are five new files.

Source evidence: **7 lightweight tests passed**, including literal-kernel
normalization back to the exact original function (only name/count/fetch form
differs), native-count/mode/core-version/global/stride boundary fixtures, exact
off-path source projection, unchanged original SSR/composition members,
sampler/profile/pipeline rejection cases, evidence/overlay/archive guards and
deterministic small fixture ZIPs. Real pinned evidence/classes/release were
checked read-only and patches applied in memory. No full probe assembly or
shader parsing/compilation occurred; these tests do not establish rendering.

Use the locked Iris artifact/runtime, minimal stack, DH absent, original NORMAL
options/resolution/shoreline. Start original versus mode0 with main mod present
to isolate packaging; then mode1 with version2/global enabled and matched
options. Inspect near/mid/far, thin reflected banks/trees, grazing views,
reflected blocks/glass, underwater/third-person and slow camera motion. Retain
driver errors, transformed source hashes, actual sampler state and captures.
Do not proceed to a sampling-cost inference if full4 imagery differs materially.

Then mode2 with near/far96/160 (and an8/16 classification/quality diagnostic if
useful). Inspect abrupt transitions and final-hit losses before timings.
Also test mode2 mod absent and globaldisabled, nonfour binary counts, outer
non10/stride non1.6 and changed core bands; each fallback must render original
native behavior. Unknown protocol versions have source fixtures only until a
separate controlled runtime verifies them. Do not mutate shared transport here.

After compile, sampler and quality acceptance, repeat balanced serial original/
mode0/mode1/mode2 captures with identical bands/scene/stack and no cue. Preserve
all runs, frame/CPU clocks and GPU median/tails; pass-level reflection/final
GPU evidence is preferable to total frame scope. Include native global
SSR_BINARY_STEPS2 as a separate quality control, not a ShaderLOD gain. Use a
second ordinary scene before promotion. No baseline/optimized GPU timing or
derivative runtime/quality result is available for this probe yet.


Root repeated7lightweight fixtures andPythoncompile successfully aftercoastal
GPUcontrol terminal. QualifiedprivateZIP assembled, SHA256
df2a3d2b50bebdba2e7eae1066bd33c03cc6f3234f6c7143ce660b431b0010e6,
metadataretained `dev/logs/miniature-refinement-build-metadata-r1.json`.
Defaultmode0; actualmode1/2Irisdriver andpixel/quality/performance unverified.
No existingcuearchive or rawsamplerreport wasrewritten.


## Actual full-four explicit-sampling driver control

Root54815 exit0/threeaccepted: original, privateMODE0off, MODE1full4explicit.
Qualifiedstandard1488×836/minimal/noDH shoreline,35s warmup20s capture/GPU1/16;
nativeSSR10/stride1.6/binary4 andreflectioncontrols fixed, probeglobalenabled/
96→160bands. Frozencompiledharness classguards passed; terrainworker source
edits notcompiled. Allfallbackfalse. Mode1actualIris translated/linked and
rendered normalterrain/water/tree reflections, rootfinalPNG inspectedwithout
grossfailure. This establishes syntax/rendering, notexactpixel/gradientparity
or movingedge/glint acceptance.

Original182.675FPS/GPU4.4219ms; off179.555/4.5044; full4explicit175.585/4.7083.
Singlepilotfull4explicit4.53%slowerGPUvsoff/6.48%vsoriginal; source/layout/
compiler/JVMvariance unresolved, retainednegativecontrol. No gain/default/
supportpromotion. Raw `dev/logs/miniature-refinement-full-control-r1` retains
allactualoptions/config/artifacts/inputs/samples/PNGs. Nextmatchedfull4versus
MODE2reducedrefinements atsamebands; no inference of savedGPUoperations from
sourcecount alone.


## Actual distance-mode pilot

Root35046 exit0/bothaccepted, samefrozencompiledharness/minimal/noDH/1488×836/
35s warmup20s capture/GPU1/16, full4explicit MODE1 vs4/3/2 MODE2 withglobaltrue/
96→160corebands andnative10/1.6/4. Bothfallbackfalse, normalrenderingrootPNG
inspected: nearbank/trees/waterreflection intact withoutgrossfailure. Exact
count-path coverage, farthin reflections/transitionmotion andGPUuniformreadback
remainadditional checks; loadedoptions/config do notaloneprovecheapbranch use.

MODE1GPU4.5430ms/FPS178.234/thread5.3356; MODE2GPU4.5432/FPS179.938/thread5.2722.
GPUdifferenceflat (~−0.004%time reduction), despite~0.95%frame difference.
Singlepilot no demonstratedavoidedGPUwork/gain; defaultoff retained. Raw
`dev/logs/miniature-refinement-lod-pilot-r1` preserves exactactualsource/options/
config/artifacts/samples/PNGs. Nextfreshdiagnosticreads linkednormalcoreuniforms
withoutGPUtiming; movingbudgetquality remainsseparate. Do notpromote this
prototype to supported/performance adapter based oncompile+configuration.
