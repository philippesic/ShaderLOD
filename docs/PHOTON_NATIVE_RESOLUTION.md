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
