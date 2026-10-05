# Photon cloud-consumer forest result

2026-10-05. All nine serial captures completed (exec70093, exit0). Original,
standalone disabled and enabled Photon v1.3b rendered under pinned NeoForge/Iris
with DH3.3.3. Source-only exact consumer dispatch compiled on the real Apple
M3Max/OpenGL4.1 driver. Default remains disabled; no meaningful ordinary-scene
performance gain or general-quality acceptance established. This optimization
is not distanceLOD by itself.

Source/builder: PHOTON_CLOUD_CONSUMER_EXPERIMENT.md. Disabled archiveSHA256
36fc08eb80c00ce228f779a72d0c2b86423a9d53df32f505edc5c6a18f42d4d1;
enabled fde2fdbff9abf9edce5e3aae73e14d4f03a265dddeab75e9e8df0cf4053d0fb7.
Only composite1 consumerdispatch changes; existing Photon protocol patches and
Java/common config untouched. Original/disabled/on main mod physically absent.

All minimal/DHpresent, forestcamera256.5/95/768.5 yaw135pitch9, detailed24/DH128,
1488×836 framebuffer, clear/time7000, shaderwetness fixed0. 35swarm/20scapture,
whole-render GPU1/16 asynchronous elapsed queries; CPU layers off. Compiled
local class/resource inputs frozen around launches. Actual semantic upstream
quality maps match after removing only PHOTON_CLOUD_CONSUMER_GUARD; its false/true
value is the intended source experiment, not upstream-quality reduction.

## Paired reductions

Positive is faster; three independent JVM blocks, partial interleaving not full
position balance. No frame-sample significance claim.

| Baseline → candidate | Frame % by block | GPU mean % by block |
| --- | --- | --- |
| forest-high → forest-cloudguard-off | +1.63, +0.31, +1.01 | -2.06, -4.77, +2.25 |
| forest-high → forest-cloudguard-on | +2.54, +0.68, +0.55 | -0.13, -1.03, +2.52 |
| forest-cloudguard-off → forest-cloudguard-on | +0.93, +0.37, -0.47 | +1.89, +3.57, +0.27 |
| forest-cloudguard-off → forest-high | -1.65, -0.31, -1.02 | +2.02, +4.55, -2.30 |

Enabled versus disabled median frame reduction+0.37%, GPUmean+1.89%. Enabled
versus original medianframe+0.68%, GPUmean−0.13%. These are small and differing
comparisons, not a15% gain. Source work-avoidance does not prove physical savings
and GPU timeline can include CPU starvation/driver scheduling.

Root inspected baseline/enabled PNGs: no gross tree/terrain/cloud/shadow change
in fixed forest; DH geometry rendered. This scene does not establish branch
coverage or correctness for clouds in front of glass/nearwater, lightning,
camera motion/disocclusion, blindness/darkness or dimensions. Those acceptance
cases remain pending. Affected composite1 pass attribution not measured here.

Raw dev/logs/photon-cloud-consumer-forest-r1 retains every report/PNG, actual
flags/hashes/UUIDs/window observations and both original-baseline/off-baseline
analyses. External loaded jars are hashed; embedded API Base provider may vary
with same exact classes/resources and qualified loaderbound as documented in
CULLING_EMBEDDED_PROVENANCE.md. This simple analyzer matches versions; broader
artifact qualification is separate, not silently claimed strict byteidentity.

Next: move to stronger measured bottlenecks. Any native-size-window test is a
separate held-constant resolution experiment; do not pool it with these captures
or manufacture a win by changing quality/radius. Keep guard experimental/off.
