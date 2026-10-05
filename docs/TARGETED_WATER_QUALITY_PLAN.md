# Targeted water quality plan

Source-only handoff, 2026-10-05. This plan has not been executed. Root owns scene
preparation, the sole client/GPU, any dev observations, and actual acceptance.
This worker changed only this document: no Java, scripts, shader sources, saves,
requests, builds, clients or encoders. The completed native Photon shoreline
triplet contains 603 verified 10 FPS frames, but does not establish sun-glint,
underwater or third-person correctness.

Use fresh copies of the same existing coastal save and distinct immutable
requests. Scene time/weather are initial prepared controls, held constant during
each session. The camera request moves only the owned spectator; do not change
time/weather, pack options, config or camera mode during a running session.
Do not excavate water, place markers, generate missing survey chunks, edit
cached saves, or modify the world to make a proposed route pass.

## Source basis and distance units

Pinned Photon v1.3b original archive SHA-256 is
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
Inspected the original in
`run-workloads/native-coastal-effects/coastalwater/high/shaderpacks/photon_v1.3b.zip`:
`settings.glsl` sets native `sunPathRotation=-35`; `gbuffers_water.fsh` includes
`program/gbuffers_all_translucent.fsh`, whose direct specular highlight uses the
surface normal, view direction and `light_dir`. The latter program's original
SHA-256 is `01c04bc017069cf19c319a0a0875ca706554575dd01e291e17cad8565645b82f`.
Its water fog and Snell-window paths depend on `isEyeInWater`. Photon shader
properties transform normalized Iris `sunPosition` back to world space for
`sun_dir`; `p0_clouds_prep.vsh` also contains a non-Iris analytical sun fallback.

The current explicit Photon water adapter uses
`distance(world_pos,cameraPosition)` for wave/parallax quality. The completed
adaptive native capture loaded water-specific bands **96/256/32 blocks**. Its
generic mod near/far values **32/96** do not replace those shader water options.
Near full quality extends through 80 blocks; medium/far transition spans
240–272 blocks. Keep `SHADERLOD_WATER_ANALYTIC=false` and original native wave
settings for the first targeted check. Parallax/normal choices can affect glint
without changing the direct-lighting BRDF itself.

Pinned MakeUp 9.5g original SHA-256 is
`0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8`;
source revision `ad2d9327ad485429e4eaf2b3f98cd738ada609de` has byte-matched release
shader files. Inspected `dev/upstream/other-pack-research/makeup-ultra-fast/`:
`lib/config.glsl` sets native `sunPathRotation=-25`; `lib/water.glsl` computes
sun reflection from alignment of the reflected view ray and astronomical light,
with a threshold of 0.995, sky visibility, weather and `infinite` ray outcome.
At `worldTime>12900` it chooses the moon, so late-night/sunrise requests must not
be mislabeled a MakeUp sun-glint check. Water refraction, the fragment's sky
fallback and deferred underwater handling have explicit `isEyeInWater` branches.

MakeUp's water vertex stores the originating **view-relative** `fragposition`;
the experimental SSR helper uses its squared length in block units. Its native
raymarch adapter retains full traces through **96**, fades reflection weight
through **160**, then skips the trace. The far fallback's `infinite=1` can change
glint even when sky reflection remains present. This shader-option adapter has
no automatically inferred Java enable bridge; use its verified pack switch.
See [MAKEUP_ADAPTER_EXPERIMENT.md](MAKEUP_ADAPTER_EXPERIMENT.md).

For sun geometry, readonly Python class inspection of the pinned Iris 1.8.12
jar found the celestial transform order Y(-90°), Z(sunPathRotation), X(skyAngle).
`CelestialUniforms.class` SHA-256 is
`250cfa1d237780d607586d892c1d648e17c6c964414cfeb36995ecbcff6208f5`.
The matching [official 1.21.1 source](https://raw.githubusercontent.com/IrisShaders/Iris/1.21.1/common/src/main/java/net/irisshaders/iris/uniforms/CelestialUniforms.java)
is explanatory; the moving branch is not a substitute for the pinned jar.
Cached Minecraft `DimensionType.timeOfDay` supplies the nonlinear sky angle.
Derived world-space sun unit vector, with time T and native rotation r:

```text
u = fract(T/24000 - 0.25)
a = 2*pi * (2*u + (1-cos(pi*u))/2) / 3
S = (-sin(a), cos(a)*cos(r), -cos(a)*sin(r))
ideal flat-water gaze = (S.x, -S.y, S.z)
yaw = degrees(atan2(-S.x, S.z)); pitch = degrees(asin(S.y))
```

This is an initial flat-surface aim, not proof of where animated-wave glint
actually appears. Loaded native rotations, observed sun direction, waves,
refraction, visibility and surface height must confirm it at runtime.

| Prepared time | Photon aim, yaw / pitch | MakeUp aim, yaw / pitch | Purpose |
| --- | --- | --- | --- |
| 7000 | 17.929° / 53.649° | 23.707° / 63.011° | Existing time; steep reflection direction |
| 12500 | 87.380° / 3.735° | 88.069° / 4.133° | Low west sun; near/transition glint |
| 12700 | 89.216° / 1.119° | 89.422° / 1.238° | Grazing west sun; farther water |

Negative native rotation puts the daylight sun in the +Z/south half-plane.
The completed yaw 180/negative-Z shoreline route therefore cannot be assumed to
show direct sun glint. Do not change the sun-path option to fit that old route.

## Readonly scene survey before requests

Candidate above-water anchor is player feet **240.5/68/660.5**, midway along the
previous trajectory. Existing clips establish camera motion there, not water
extent below it or to its west. Read only already loaded client blocks/chunks;
if required blocks are unavailable, defer the route rather than trigger new
generation. Record the save copy identity and survey observations, including:

- Water/fluid surface heights and block/collision states at the anchor and along
  candidate westward sightlines. Check camera and near-plane clearance.
- At time12500/12700, visible water covering near, transition and far ranges,
  sun/glint not hidden by land/clouds, and an observed glint on the **original**
  pack. A bright patch alone is insufficient without the source sun/view geometry.
- Underwater camera points along x=240.5 / y=60 / z=668.5→648.5, including the bottom and
  intervening collision shapes. **Y=60 is a tentative probe, not a proved depth.**

If coverage fails, root must choose a surveyed alternative on the same existing
save and create a new labeled request shared across its arms. No assertion in
this plan says the proposed westward water or underwater corridor exists.

## Exact glint controls and candidate requests

All candidate controls use `weather:"clear"`, `preset:"PERFORMANCE"`,
`debug:false` and the prepared profile's existing frozen-day/weather policy.
Above-water controls use x=240.5 / y=68 / z=660.5. The following table fixes the first
pose and time; every waypoint request must start at that exact pose:

| Scene | Time | First yaw | First pitch |
| --- | --- | --- | --- |
| glint-near-transition, either pack | 12500 | 78 | 2.2 |
| glint-far, either pack | 12700 | 80 | 0.6 |
| underwater candidate | 7000 | 180 | -10 |
| third-person static survey | 7000 | 180 | 3 |

Example initial **glint-near-transition** control; prepare it before the session:

```json
{"x":240.5,"y":68.0,"z":660.5,"yaw":78.0,"pitch":2.2,"time":12500,"weather":"clear","preset":"PERFORMANCE","debug":false}
```

Photon near/transition request, conditional on the westward survey:

```json
{
  "schema":1,"label":"photon-glint-near-transition-r1",
  "warmupSeconds":30,"fps":10,"pngCompressionLevel":0,
  "waypoints":[
    {"seconds":0,"x":240.5,"y":68.0,"z":660.5,"yaw":78.0,"pitch":2.2},
    {"seconds":5,"x":240.5,"y":68.0,"z":660.5,"yaw":87.380,"pitch":3.735},
    {"seconds":10,"x":240.5,"y":68.0,"z":660.5,"yaw":96.0,"pitch":5.0},
    {"seconds":15,"x":240.5,"y":68.0,"z":660.5,"yaw":87.380,"pitch":3.735},
    {"seconds":20,"x":240.5,"y":68.0,"z":660.5,"yaw":78.0,"pitch":2.2}
  ]
}
```

For MakeUp's exact counterpart, use label `makeup-glint-near-transition-r1` and
replace **both** t=5/t=15 central poses with yaw 88.069 / pitch 4.133; all remaining
numbers and the initial control are identical. Keep this new source immutable.

Photon far request: initial control is the same x/y/z, time 12700 / yaw 80 / pitch 0.6:

```json
{
  "schema":1,"label":"photon-glint-far-r1",
  "warmupSeconds":30,"fps":10,"pngCompressionLevel":0,
  "waypoints":[
    {"seconds":0,"x":240.5,"y":68.0,"z":660.5,"yaw":80.0,"pitch":0.6},
    {"seconds":5,"x":240.5,"y":68.0,"z":660.5,"yaw":89.216,"pitch":1.119},
    {"seconds":10,"x":240.5,"y":68.0,"z":660.5,"yaw":98.0,"pitch":2.2},
    {"seconds":15,"x":240.5,"y":68.0,"z":660.5,"yaw":89.216,"pitch":1.119},
    {"seconds":20,"x":240.5,"y":68.0,"z":660.5,"yaw":80.0,"pitch":0.6}
  ]
}
```

MakeUp counterpart uses label `makeup-glint-far-r1` and t=5/t=15
yaw 89.422 / pitch 1.238. For a measured surface height h, flat-water intersection
range is `(actualCameraY-h)/sin(pitch)`. At the **illustrative, unverified** h=63
and previously observed eyeY=69.62, Photon pitches 5/3.735/1.119 give approximately
76/102/339 blocks. This explains the chosen sweeps but does not classify actual
pixels: use measured water positions and adapter distances. The time-12500 sequence
spans MakeUp's far fade and Photon's near transition/medium; the 12700 sequence
seeks Photon's >=272 far region. Inspect whether the actual view/radius contains
those water surfaces before claiming coverage.

## Underwater candidate

Only after readonly survey confirms camera and near-plane clearance, prepare:

```json
{"x":240.5,"y":60.0,"z":668.5,"yaw":180.0,"pitch":-10.0,"time":7000,"weather":"clear","preset":"PERFORMANCE","debug":false}
```

Use one immutable request per pack; substitute only label `makeup-underwater-r1`
for its counterpart. Every arm of a pack uses identical bytes:

```json
{
  "schema":1,"label":"photon-underwater-r1",
  "warmupSeconds":30,"fps":10,"pngCompressionLevel":0,
  "waypoints":[
    {"seconds":0,"x":240.5,"y":60.0,"z":668.5,"yaw":180.0,"pitch":-10.0},
    {"seconds":10,"x":240.5,"y":60.0,"z":658.5,"yaw":175.0,"pitch":-5.0},
    {"seconds":20,"x":240.5,"y":60.0,"z":648.5,"yaw":185.0,"pitch":-10.0}
  ]
}
```

The previous 1.62-block eye offset would put the camera at y=61.62; neither that
offset nor water above this candidate may be assumed without actual capture.
Require `mainCamera.getFluidInCamera()==FogType.WATER` at every underwater frame.
Check bed/side walls, underwater surface/refraction, normal discontinuities,
fog/Snell-window edges and disable restoration. This short route does not prove
far underwater distance-band coverage; water fog may hide that range entirely.
An interface-crossing route is a separate request after measuring surface height,
with exact crossing frame/fluid state recorded, not a guessed extension of y=60.

## Third-person distance units: separate pending procedure

The current session's `requireOwner()` requires first person and rejects third
person. No valid existing camera-quality JSON can enable this check. Do not
bypass that guard or mark a rejected capture successful. Root may first do a
manual stationary original/off/on survey with the session **inactive**, or
coordinate a bounded camera-mode/observation addition before automation.

Candidate fixed player control is x=240.5 / y=68 / z=660.5, yaw 180 / pitch 3, time 7000,
clear weather. Survey FIRST_PERSON, THIRD_PERSON_BACK and THIRD_PERSON_FRONT
as separate held modes, warm at least 30 seconds per prepared view. No movement,
source/pack reload or mode toggle inside an armed first-person session. If using
existing control screenshots, safe names are `water-third-first-r1`,
`water-third-back-r1` and `water-third-front-r1`; add `capture` only after the
original view is prepared and warmed. Its two-second screenshot delay is not
the required warmup and does not provide the missing observation fields.

Cached 1.21.1 `Camera.setup` moves detached views back by a nominal 4 blocks,
subject to NeoForge distance hooks/entity scale and eight collision rays;
front mode also rotates yaw 180/pitch sign. Record actual mainCamera rather than
assuming an exact 4-block displacement. The
[official Iris camera source](https://raw.githubusercontent.com/IrisShaders/Iris/1.21.1/common/src/main/java/net/irisshaders/iris/uniforms/CameraUniforms.java)
uses mainCamera for the unshifted shader camera position. Nearby points should
therefore use the **rendered camera**, not player feet, in both adapters' block
distances; observe actual supplier values on the pinned runtime before accepting.

Select unmodified, surveyed water reference points near the loaded water band's
thresholds. For each mode record point Q, player P, actual camera C,
`length(Q-C)` and `length(Q-P)`, and the reconstructed originating view-relative
distance. A render-camera displacement can move a fixed surface through a band
without player motion. Target points must actually straddle a threshold; a
4-block change deep within one tier cannot prove camera-origin correctness.
Avatar occlusion, collision-shortened zoom and front-mode gaze are separate
quality changes. If the distance/normal supplier observation is unavailable,
keep third-person units unverified rather than infer them from screenshots.

## Required observations and triplet/restoration controls

Existing manifests already retain actual camera/player poses, source frame/tick
deltas, clocks, pack/options/config/runtime hashes, native framebuffer, PNG
encoding/callback/hash status and frozen scene guards. They **do not currently
record** camera FogType, block/fluid geometry, glint aim or shader-delivered water
distance/underwater state. Root must gather these before targeted acceptance:

- Per-frame mainCamera fluid/FogType, camera block and fluid tag/height,
  `cameraY < blockY+fluidHeight`, player-eye fluid separately, camera-viewpoint
  block state, and camera/near-plane intersection with solid collision shapes.
  Water is not itself a solid obstruction; waterlogged solids still need checks.
  Cache/source `Camera.getFluidInCamera()` uses the rendered camera's position
  against the local water surface. Do not replace this with a guessed sea level.
- Delivered `isEyeInWater`, sunPosition/light direction and sunPathRotation,
  advancing animation/history inputs, actual glint ROI and any cloud/terrain
  occlusion. Observe normal uniform evaluation, without extra smoother calls.
- Loaded water-specific bands, effect switch, applied budget/eligibility and
  selected surface distances. Diagnostic colors belong in separate captures;
  debug-off images establish appearance. GUI or player coordinates alone do
  not establish the fragment branch or offscreen fallback.

For each surveyed scene run **original → derivative off → derivative on →
derivative off restoration**, serially, at held native quality/window/radii and
identical request/time/weather/save inputs within that pack. Keep unrelated
effects, analytic water experiments, terrain caps and debug off for a targeted
water-only diagnostic; separately retain the existing combined preset if desired.
No stack/DH change inside this first noDH comparison; DH needs its own boundary
and underwater checks later.

Photon off must verify the actual Iris global enable/bridge and water option
state; on must verify effective water 96/256/32 rather than generic 32/96. MakeUp
off/on must verify `SHADERLOD_MAKEUP_WATER_SSR=0/1`, native
`REFLECTION_SLIDER=2`, `SUN_REFLECTION=1`, native rotation and exact adapter bands;
do not assume toggling Java config controls this experimental pack. If a newer
explicit bridge adapter is selected, pin and prove its own transport first.
Defaults remain off; restoration is the same derivative/off input state again.
Fresh launches or reloads reset histories, so repeat warmup and retain that fact.

Inspect full near surfaces, both band edges and far fallback over motion,
glint intensity/position/pop, reflection loss, refraction boundaries and water
fog. MakeUp's far reflection fade is an intentional quality loss; judge whether
it is acceptable without relabeling it exact parity. Advancing normal clocks
and dither prevent naïve identical-frame pixel claims. Require all 201 slots and
on-disk PNG hashes for each 20-second 10 FPS sequence; retain skipped/failed
attempts separately. Ten FPS sampling still cannot exclude higher-frequency
shimmer; additional owned real-time observation is needed for that claim.

This document passes only static request/math review. All requests are 20 seconds,
201 slots, warmup 30, fps 10, compression 0, at most 5 waypoints and well below the
512-block/32-block-per-second/90-degree-per-second bounds. No world depth,
sun-glint appearance, underwater rendering, third-person distance units, shader
compilation, support or performance gate is accepted from this plan.


## First actual Photon glint coverage

Rootloaded-only615cellsurvey foundx500 sampledcolumns640/660/680 water60..62,
surface62.8889 andair63..70; selectedx500.5/y68/z660.5 originaltime12500
westwardsweep afterx300columns showedlower/nonuniform watersurface. Newroot
perframecontracts expectedNONE/no-fullcollision compiled. Initialattemptfailed
beforePNG dueclienttimepacket sync, retained. Rootacquisitionbarrier fixed;
retry95096 exit0/complete201/201/0skips/allPNGhashes verified, native2976×1672/
10FPS/minimal/noDH. Actualcamera Eye69.62 observedNONE/fullcollisionfalse.

Rootframe50atnearideal87.380/3.735 inspected: lowwesternsun anditsbrightwater
reflection visible, originalwatersurface/glint coverageestablished inthis
boundedview. Source/actualpose/time12500/loadedwater observations support
identification; this is no fullglint-temporal or candidatequality acceptance.
Sequence d66ac242-6f0e-4292-af0f-973c37d2a131, rawlog/manifest
`dev/logs/photon-targeted-glint-original-r2` withsourcePNGs underprofile.
Matchedglobaloff/on on sameimmutable request stillneeded. Underwater route
prepared butnotexecuted; thirdpersonremainsunsupportedbycurrentharness.
