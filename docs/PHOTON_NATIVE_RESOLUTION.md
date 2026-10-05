# Native-window Photon pilot

2026-10-05. exec92027 exit0/allthreecomplete, minimal/DHpresent, actual2976×1672
framebuffer,40swarm20scapture/GPU1/16/fixedwetness0, same forestcamera/quality/
radius24+DH128. Requested windowScale2 fits monitorworkarea and doubles the
comparison window in both dimensions. This is a separate resolutionexperiment,
not an artificially added effect workload. Never compare its baseline with a
scale1 active capture for a speedup.

| Arm | FPS | GPU mean ms | Thread mean ms | Frame duration ms |
| --- | ---: | ---: | ---: | ---: |
| Original |61.49|15.090|9.084|16.262|
| Patched disabled |61.19|15.211|9.163|16.342|
| Protected active B15 |49.96|19.146|9.261|20.017|

The active pilot regressed. More pixelwork exposed a stronger GPU limit; it did
not establish distanceLOD benefit. Same upstream loaded quality, wetness, camera
and detail radii; protected lighting true, cachedsky/geometry/culling off.
Native frame/elapsed values, exact loadedconfig/options/artifacthashes and
compiled-class/resource fingerprints are preserved under
dev/logs/photon-native-window-pilot-r1.

One block only; no accepted causal diagnosis or ordinary-scene gate. The GPU
interval can include CPUstarvation/driver effects. Loop/code-shape and sampling
branches are candidates, not proven culprits. Shaderoptions alone cannot settle
which physical work changed.

Next: original/off/alloff and isolated cloud/water/fog/SSR/material source
controls at this same resolution, retaining exact inputs and failures. Repeat
any suspected arm and inspect actual submitted shader source; only then alter
its bounded explicit kernel. Keep reduced Mac profile/hardware limits visible
and do not promote a failed candidate from lower source samplecounts.


## Native effect source sweep

exec26695 terminalexit0/allninecomplete. Frozen B15, same upstreamquality,
40swarm20scapture/GPU1/16/native2976×1672/minimal/DH/fixedwetness0. Oneblock.
Original/globaloff/allactive/alloff/materials/cloud/SSR/water/fog GPUmeans:
15.510/15.098/16.607/17.180/16.382/15.220/15.673/16.428/15.249ms.
Cloud/fog nearoriginal, alloffslower. MaterialPOM and SSR upstreamfeatures
inactive here. This sweep identifies no demonstrated shader-module culprit.
Rawdev/logs/photon-native-effects-r1; sourceaudit NATIVE_EFFECT_SOURCE_AUDIT.md.
Cloud secondarycount/extent math changed; Overworld fog was alreadydynamic;
water cheap octavekernels alreadyliteral. No blanket dynamic-loop diagnosis.

## First one-JVM effects control

exec50296 exit0/complete, session34f09990-5ef0-4920-abfb-44e9e2f07e80. Same
B15/allfiveeffectoptionsfixedtrue, globalenabled OFF/ON/OFF/ON, hiddenHUD,
40sinitial/10ssettle/20scapture, nativeframebuffer/minimal/DH/fixedwetness0.
Strict analyzer accepted phase/input/archive/options/artifacts and images.

| Phase | Enabled | FPS | GPU mean ms | Thread mean ms |
| --- | --- | ---: | ---: | ---: |
|1|OFF|60.17|15.580|8.852|
|2|ON|65.15|14.177|8.532|
|3|OFF|61.41|15.318|8.412|
|4|ON|64.90|14.267|8.475|

Adjacent enabled GPU reductions9.00%/6.86%; frame7.65%/5.38%. Correlatedphases,
not independent-JVM acceptance. Shader/module effect is exploratory and still
below ordinary15%target. Between-JVM negatives remainretained.

Root noticed inherited automatic landscape-1800 PNG in timedphase2. The run
is qualified for unplanned readback/write work; small gains need cleanrepeat.
Old automatic shots now gated while benchmark/workload owns capture. Rawreport
preserved asdev/logs/photon-native-effects-inprocess-r1-benchmark.json; analyzed
phases/images checked at originalprofile, outputadjacent-analysis JSON. Clean
forward/reverse repetitions are next; no promotion from this session.


## Isolated cloud control in one JVM

exec34162 exit0/strictanalysisaccepted; B15/cloud-only optionsfixed, other
five-effect modulesfalse, lightingprotected/cache/analytic/debugoff, native
2976×1672, minimal/DH,40sinitial10ssettle20scapture/GPU1/16/fixedwetness0.
OFF/ON/OFF/ONFPS61.353/64.348/61.415/64.174; GPUmeans14.702/14.038/14.926/14.054ms,
threadmeans8.748/8.705/8.463/8.686ms. AdjacentGPUreductions4.52/5.84%,
frame4.65/4.30%. These correlatedphases support cloudmodulecontribution, not
a secondary-loop cause or independent/second-workload acceptance.
Rawdev/logs/photon-native-cloud-inprocess-r1-{benchmark,analysis}.json andlog;
sourcephasePNG/hashes verifiedat native-effect-isolation/forest/cloud-only.
Next fog/watercontrols, cloudrepeat/secondworkload, then conditional math-
preserving secondaryextent hoist probe with source/off/live-disable controls.


## Fog and water controls

Fog69423 andwater24468 terminalexit0/strictanalysisaccepted, same native
profile/method, eachone correlatedfourphase session. FogGPUOFF/ON/OFF/ON
15.093/14.968/15.544/15.429ms, adjacentreductions0.83/0.74%; frame1.96/−0.10%.
WaterGPU14.809/15.208/14.906/14.929ms, adjacent−2.70/−0.15%; frame+0.07/+0.11%.
Threadtimesroughlylevel. Fogsmall andwaternousefulforestsignal, not a benefit
claim for everyeffect or any shoreline/fullwater workload.
Rawdev/logs/photon-native-{fog,water}-inprocess-r1-{benchmark,analysis}.json
andlogs retainnegativearms andverifiedsource/images. Aggregate8%signal is
not decomposed by addingthese overlapping/independentlycaptured intervals.
Cloudsetup sourceprobe shouldbe tested as its ownsource intervention, with
unchanged budgets/motion quality and retainedemptyray/register risks.


## Native coastal second-workload control

Root3149 exit0; strict effects analyzer accepted exactB15 and every phase input,
window, artifact, flag map, screenshot hash and unchanged world/pipeline.
Minimal/DHabsent, original SSR reflections enabled, native2976×1672,
40s initial warmup10s settle20s capture/GPU1/16/fixedwetness0. Archive and
all five effect options fixed; only existing live globalenable changed.

OFF/ON/OFF/ON GPU14.0414/12.7941/13.8791/12.9075ms, FPS65.487/71.474/66.450/
70.680, thread7.1643/7.1018/6.7304/7.1689ms. Adjacent GPUreductions8.88/7.00%,
frame8.38/5.99%. Root enabledphasePNG inspected: shoreline terrain/shadows and
nearwater tree reflections visible, no gross failure. Moving edge/glint/
underwater and restoration imagery remain separate quality work.

This is a positive correlated within-session signal on a second ordinary
workload. It is not repeated independent-run/≥15%acceptance, nor an original
mod-absent comparison, and cannot be added to the forest percentage. Raw
`dev/logs/photon-native-coastal-effects-forward-r1-{benchmark,analysis}.json`
and `.log` retain all inputs/captures. Sourceprofiles
`run-workloads/native-coastal-effects/coastalwater/adaptive`.


Second coastal reverse session18005 exit0/strictaccepted, ON/OFF/ON/OFF.
GPU12.8727/14.2618/12.8202/14.4741ms, FPS70.550/64.132/70.881/63.447,
thread6.9335/6.8356/6.7553/6.7195ms. Adjacent GPUreductions9.74/11.43%,
frame9.10/10.49%. Same B15/options/resolution/scene/minimal/noDH; diagnostic
sampler and DHdraw properties absent/off. Devinstrumentationclass/source hashes
changed sincefirstcoastal session as probeswere integrated; eachsession's
artifact/inputhashes separatelyvalidated. No claim of byte-identical cross-run
harness or IIDframe samples. The positive direction repeatsinbalanced global
controls, stillbelow15% and not originalmod-absent repeatedperformancegate.
Raw `dev/logs/photon-native-coastal-effects-reverse-r2-{benchmark,analysis}.json`
and `.log`. Movingquality and nativecapgeometrytradeoff remain separatechecks.


Third coastal forward5121 exit0/strictaccepted withpriorcompiledharness held
fixed (`-x compileDevClientJava`), OFF/ON/OFF/ON. GPU14.3219/12.9224/14.1451/
12.6403ms, FPS64.658/70.929/65.815/72.243, thread7.0564/6.8435/6.6958/6.6925.
AdjacentGPUreductions9.77/10.64%, frame8.84/8.90%. SameB15/globalcontrol,
options/nativewindow/minimal/noDH. A tiny standalonePNGencoderfixture compilation
wasallowedinparallel withsourcework andmayoverlap thissession; exactexternal
CPUloadtimingwasnotinstrumented, so do not treatthis as a stronger isolated
hardwarecausalclaim. Firsttwo balancedcoastal controlsremain separatelyretained.
Allsource/artifact/window/pipeline checks passedwithinthissession. Raw
`dev/logs/photon-native-coastal-effects-forward-r3-{benchmark,analysis}.json`
and `.log`. None establish≥15% or originalabsentmod acceptance.
