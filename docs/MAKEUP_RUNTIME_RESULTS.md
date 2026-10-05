# MakeUp exploratory runtime results

2026-10-05. Original MakeUp9.5g and experimental adapter off/on compiled and
rendered the inspected shoreline under the pinned MC1.21.1/NeoForge21.1.253/
Iris1.8.12/Sodium0.6.13, M3Max/OpenGL4.1 runtime, with DH absent. This is a
bounded runtime result, not accepted second-pack support. The Java/common
protocol is unconnected; controls are shader options.

Original archive SHA2560036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8.
Derivative SHA2566c3f423173ddf12f2ab3f10f6e18f6bc07f304aa70201b5bebe5902587c59671.
Root inspected original and active PNGs with visible near bank/water reflections;
no gross near shadow/terrain loss. Far-reflection content is intentionally faded
to native sky/cloud fallback; transition/glint/motion quality is not yet accepted.

## Retained pilot

exec45311 terminalexit0/allfourcomplete; dev/logs/makeup-adapter-pilot-r2. All
minimal/noDH, 24detailedchunks,1488×836, camera240.5/68/680.5 yaw−180pitch3,
clear/time7000. Native medium profile with SSRreflection slider2/AA2, except
lower uses native reflectionslider1. Warmup35s/capture15s, frameGPUquery1/16,
CPUlayeroff, actual shaderwetnessdynamic/raw0. Compiled local class/resource
inputs frozen before/after every run. Four independent launches but only one
block; no causal/performance acceptance from this pilot.

| Arm | FPS | GPU mean ms | CPU wall mean ms | Thread mean ms |
| --- | ---: | ---: | ---: | ---: |
| Original |150.63|6.183|6.631|6.285|
| Derivative off |152.04|6.325|6.571|6.258|
| Native projected reflection |150.85|6.157|6.623|6.314|
| Derivative on96/160 |119.85|7.749|8.335|7.828|

The active arm regressed in this capture. Keep defaultoff and retain this result;
there is no speedup or quality recommendation. Source-level avoided far texture
reads did not establish real benefit. The code shape, explicitLOD0 conversion
and known cross-JVM variation are hypotheses, not demonstrated causes.

Earlier original smoke exec88400 terminalexit0/complete150.85FPS, preserved
under dev/logs/makeup-original-smoke-r1. Pilotr1 preflight never launched a game:
Iris removed the all-default original optionfile after smoke. Corrected parser
recognizes absent defaultfiles and verifies desired experimentcontrols against
source defaults and actual loaded effectiveoptions. Failedrunnerlog retained.

Next: repeat off/on controls, preferably reload controls in one JVM with
history settling, source-isolation probes, moving-camera near/transition/far
quality and a separate DH-present original/adapter compatibility smoke. Existing
DH water is untouched by this vanilla-water SSR experiment. Otherdimensions,
water/glass/material boundaries, reload/UI and broader support remain unchecked.


The active pilot source probe has not identified a regression cause. Root has
assigned full-reflection diagnostic source variants: duplicate-original trace,
explicitLOD0 trace, and retained classifier with identical full trace on both
branches. All preserve original near/far reflection quality. The classifier
may be compiler-eliminated; emitted source alone is not physical GPU instruction
evidence. These source probes are not a replacement for repeated/reload controls.


## Full-reflection probes rendered

exec85787 exit0/allfivecomplete; dev/logs/makeup-full-reflection-probes-r1.
Same minimal/noDH shoreline, scale1,35swarm20scapture/GPU1/16. New dev bytecode
only adds benchmark-window property handling; class/resources frozen per matrix.
Every source probe preserves full SSR participation. Original/off/classifier-full/
duplicate-implicit/duplicate-explicit returned151.78/154.02/154.00/148.05/124.48FPS.
GPUmeans6.164/6.101/6.162/6.369/7.602ms; threadmeans6.274/6.174/6.194/6.411/7.373ms.
All real shader programs rendered with fallbackfalse and optionsverified.

Direct duplicate-explicit was slower in this singleblock, while classifier-full
with the same explicit fetch forms was nearbaseline. Source/control-flow shape,
compiler decisions and cross-JVM variation remain confounded. This does not
prove that explicitLOD0 causes the loss or that the classifier is cost-free;
equal calls can fold. Capture/source inspection plus repeated/reloadcontrols
are required. Exact diagnostic patches/quality intent in MAKEUP_REGRESSION_PROBES.md.
No sampler/form change or active adapter promotion follows.


## Original MakeUp with DH installed

Root runner51489 ended exit0, single accepted original9.5g/minimal/DHpresent
shoreline at1488×836,40s warmup20s capture/GPU1/16. Exact loaded DH3.3.3,
Iris/Sodium/NeoForge and archived source/options confirmed. DH terrain/water
shaders linked, fallback=false and renderer setup completed; driver emitted
unsupported GL_ARB_shader_texture_lod extension warnings. Inspected final PNG
shows near shoreline/water reflections and distant hazy land with no gross
failure. This run did not record VBO draw statistics, so actual distant terrain
draw acceptance remains incomplete. DH installation alone is insufficient.

Frame8.358ms/GPU7.436ms/thread7.372ms (~119.65FPS), one pilot only. Do not compare
this DH-present result to earlier noDH timings as an isolated adapter effect.
Raw `dev/logs/makeup-original-dh-smoke-r1` preserves full source/jar/classes,
actual options/window/camera, logs, capture and timing. Next bounded diagnostic
must observe real DH draw counts; no support promotion.


DH submission diagnostic28154 subsequently exited0: opted-in verified3.3.3
terrain hook recorded394725opaque and389269translucent world submissions,
cumulativewarmup included. Hook exercised, exactjarhash matched, renderer
notdisabled and fallbackfalse; finalshoreline/distantland/water PNG inspected.
These actualCPUdraw submissions improve compatibility evidence beyond module
presence/renderlistcounts. They do not prove GPUcompletion/distantpixelidentity,
waterboundary/underwater quality or shadow correctness. See DH_RENDER_EVIDENCE.md;
raw diagnosticGPUqueries0 is excluded from performance comparisons.
