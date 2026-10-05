# Development camera quality capture

`src/devClient/java/com/pp/shaderlod/dev/CameraQualitySession.java` is the integrated
development capture utility. It compiled and completed bounded runtime smokes
recorded below; broader quality acceptance remains pending.
The GPU/client owner must review, integrate, compile and inspect actual moving
captures before treating it as runtime evidence. This worker changed only that
source and this document; common protocol code, existing harnesses, shader
sources, build configuration and client processes are untouched.

The harness addresses the missing moving-camera quality check for original,
derivative-off and derivative-on packs. It records quality evidence only.
Framebuffer readback, PNG compression, disk writes and fingerprinting affect
timing. Its frame counters and wall times identify source frames and capture
gaps; they must not be analyzed as a GPU/FPS speedup.

## Activation and integration

Only `shaderlod.dev.cameraQuality` activates it. Its value is the path of a
local JSON request, read once and limited to 64 KiB. An absent/blank property
returns `null` without opening files, creating threads or moving the player.
`shaderlod.dev.cameraQualityOnly=true` additionally authorizes stopping this
same client after the final manifest and PNG callback handling. The session
must have acquired its local spectator UUID; a rejected request does not close
an unacquired client.

The runtime owner can copy the reviewed file into
`src/devClient/java/com/pp/shaderlod/dev/CameraQualitySession.java`. Keep it in
the dev client source set and ensure it remains absent from the production jar.
Small hooks in `ComparisonClient` are sufficient:

```java
private CameraQualitySession cameraQuality;

// In the existing one-time configured block, after scene/control preparation:
cameraQuality = CameraQualitySession.createIfRequested(
        client.gameDirectory.toPath());

// After applyControl() in ClientTickEvent.Post, before normal workload handling:
if (cameraQuality != null) {
    cameraQuality.tick(client);
    GpuTimers.drainSamples();
    CpuTimers.drainSamples();
    return;
}

// At the START of draw(RenderGuiEvent.Post), before hideGui's early return:
if (cameraQuality != null) {
    event.getGuiGraphics().flush();
    cameraQuality.render(Minecraft.getInstance());
}
```

The owner must gate creation of `WorkloadSession` and `BenchmarkSession` when
the explicit camera-quality property is present, and place the tick branch
before both old manual/automatic screenshot schedules. The configured block
must preserve the prepared ShaderLOD settings rather than apply its ordinary
comparison enable/preset defaults. A rejected request must retain
its rejection manifest and must not silently become a benchmark. Use a
dedicated quality launch; do not enable `measureWorkload`, `measureOnly`,
`benchmark` or `benchmarkOnly` in that launch. The ordinary comparison client
still opens the prepared landscape world and applies its existing control.
Original absent-mod runs use the existing `compare=off` dev client.

If launching through Gradle, forward these two project properties explicitly
in the dev run configuration; Gradle JVM properties do not automatically reach
the game JVM:

```groovy
if (project.hasProperty('cameraQuality')) {
    systemProperty 'shaderlod.dev.cameraQuality', project.property('cameraQuality').toString()
}
if (project.hasProperty('cameraQualityOnly')) {
    systemProperty 'shaderlod.dev.cameraQualityOnly', project.property('cameraQualityOnly').toString()
}
```

Include `cameraQualityOnly` in the existing serial-window decision when useful
for window placement. Window dimensions, HUD visibility, pack/mod configuration,
fixed wetness if used, resource packs and render radii remain the prepared
profile's responsibility. The harness does not change those settings or reload
shaders. A hidden HUD is recommended; its state is held constant and recorded.
The configured block currently forces `hideGui=false`; an explicit quality
launch must set its desired stable state before creating/starting the session.
Cached 1.21.1 NeoForge `GuiLayerManager` source posts `RenderGuiEvent.Post` even
when vanilla HUD layers are hidden, but confirm this in the actual integrated
runtime. Flush pending GUI batches before the screenshot hook.

## Request example and path behavior

This 24-second example starts at the existing noDH shoreline player position.
It is an example, not an executed or quality-approved trajectory. Place it in
an ignored prepared runtime directory and use the same request bytes for all
pack arms. The first waypoint must match the prepared `control.json` position
and yaw/pitch within 0.05 blocks/0.1 degrees. Coordinates are **player feet**;
captured `mainCamera` coordinates include the actual interpolated eye height.

```json
{
  "schema": 1,
  "label": "shoreline-motion-r1",
  "warmupSeconds": 30,
  "fps": 10,
  "waypoints": [
    {"seconds": 0,  "x": 240.5, "y": 68.0, "z": 680.5, "yaw": -180.0, "pitch": 3.0},
    {"seconds": 8,  "x": 244.5, "y": 68.0, "z": 668.5, "yaw": -165.0, "pitch": 5.0},
    {"seconds": 16, "x": 236.5, "y": 69.0, "z": 672.5, "yaw": -195.0, "pitch": 2.0},
    {"seconds": 24, "x": 240.5, "y": 68.0, "z": 680.5, "yaw": -180.0, "pitch": 3.0}
  ]
}
```

Labels accept 1–64 ASCII letters, digits, underscores and hyphens. The source
is parsed to immutable records; exact raw source bytes and their SHA-256 are
retained. The original request is rehashed with bounded reads once each second
and at finalization. Changing it fails the session.

Waypoints number 2–32 and start at time zero. All values must be numeric and
finite. X/Z are within ±29,999,000, Y within ±2048, yaw within ±3600 and pitch
within ±90 degrees. Segment duration is at least 0.25 seconds; total duration is
20–30 seconds and total travel at most 512 blocks. The frame rate is an integer
1–10, and duration must end on a capture slot. Peak eased movement/rotation is
limited to 32 blocks/s and 90 degrees/s. Yaw is unwrapped along the shortest
arc; long intentional rotations need intermediate waypoints.

The session waits at most 120 seconds for the prepared local singleplayer
spectator and matching first waypoint. It reads server state on the server
thread and requires disabled daylight/weather cycles; it makes no rule, time,
weather, block, entity or shader changes. These controls freeze daylight and
weather, **not** every simulation clock, mob, animation or shader history.
Actual world time/weather and Iris clocks remain in the evidence. Scene
preparation and any decision to freeze additional inputs belong to the runtime
owner.

After acquiring the UUID it holds the first pose for at least 30 seconds and
600 ticks (configurable up to 120 seconds). The owned player's delta movement
is cleared, position and rotation are set at normal Post ticks, and Minecraft's
previous-tick fields provide normal rendered interpolation. The initial snap
uses `setOldPosAndRot()` only before warmup. The moving path uses cubic easing
between waypoints with zero endpoint velocity and deterministic 20 Hz samples.
No OS input, direct mainCamera repositioning or render-frame teleport is used.

Capture scheduling uses path time and the observed render tick delta. Source
wall times are retained separately; slow ticks do not silently become a faster
path. Motion has a 60-second real-time bound. Server scene observations must
complete within five seconds after an owned scene starts. A changed UUID,
dimension, pause/menu, camera entity/type, shader fallback, loaded options,
render radius, framebuffer, control hash or tracked configuration fails the
session. Rain/thunder are required to remain settled during capture. Keep hands
off movement and mouse input during the owned run; source screenshots prove
the actual camera rather than assuming the planned camera was reached.

## Output and bounds

All outputs go to
`<gameDirectory>/camera-quality/<fresh-UUID>/`. Prepared game directories under
`run-workloads/` are already ignored. Do not run against a tracked game
directory or add these generated files to Git. The output root must not be a
symbolic link and resolves directly inside the game directory. Labels and
request content never become paths. Filenames are fixed `request.json`,
`manifest.json` and generated `frame-NNNN.png`; the manifest uses a sibling
temporary file followed by replacement.

The 24-second example requests 241 slots, including start and end; the maximum
is 301. OpenGL readback is on the render thread immediately after the frame's
GUI pass. PNG writing/hashing uses one daemon writer with **one in-flight
image**, no queue of native images. The framebuffer is bounded to 4096×2160
pixels (about 34 MiB of RGBA source pixels). Each PNG is limited to 64 MiB;
the sequence has a 2 GiB disk budget with capacity reserved before the next
capture. These are experiment bounds, not promises that readback/compression
is fast. Slower storage/readback may require using `fps: 5` or `fps: 2`.

If a render misses a scheduled slot or the writer is still busy, that slot is
retained as `missed_render_slot` or `writer_backpressure`. The harness never
duplicates a later screenshot to fill missing motion. PNG write completion
returns to the client thread and records hash, size, UTC completion and write
wall time. The final manifest waits for that callback, with a 30-second timeout;
the manifest is also checkpointed after every successful write callback.
an unresolved writer remains explicit in the failed report. A timeout can leave
the daemon writer finishing an owned file, but cannot be called completed
capture evidence.

Each captured checkpoint records actual rendered mainCamera position/yaw/pitch,
local player pose, motion tick/delta, planned/observed path seconds, actual
wall time, session render count, Iris source frame counter and frame-time
sources, the dev GPU frame ID, framebuffer size, world time/weather, actual
loaded pack, capture-boundary archive/options hashes and screenshot completion.
GPU frame IDs may stay zero if the existing GPU timer is disabled; they are not
a GPU measurement here. Full loaded shader options, mod versions, initial/start/
end input snapshots, class bytecode identity, exact request/config and advancing
shader temporal inputs are retained in the manifest. Archive hashes are checked
at initial/start/end boundaries; loaded options/config/control are checked each
second. Mid-frame pack reloads are not permitted and need separate coverage.

Statuses distinguish `rejected_request`, `failed`, `incomplete_capture` and
`complete`. A completed sequence requires every requested slot's successful
write callback. Rejected or partial attempts are retained, not erased. Without
`cameraQualityOnly`, the harness restores its acquired player's original pose
when that same UUID remains present. It never stops another process by name.

## Acceptance and remaining integration checks

This draft's small acceptance check is source review plus scoped whitespace and
JSON/example/bounds validation. Static checks passed: the documented JSON is
valid and requests 241 slots over 24 seconds, its total travel is 30.649 blocks,
and its eased peak speeds remain below the limits. Both new files have no
whitespace diagnostics under `git diff --no-index --check /dev/null <file>`
(the exit status is 1 because these are new-file differences). Referenced
Screenshot, Camera, Entity, GameRules, CameraType and server-submit API
declarations were found in the cached legacy Minecraft/NeoForge source archive;
this is source inspection, not compilation or runtime proof. No build or runtime check was run by this
worker, because the sole GPU owner currently has a live matrix and owns builds.

Before using the harness, the owner must:

1. Compile the transferred source and both small hooks against the pinned
   Minecraft 1.21.1/NeoForge/Iris runtime; check disabled-property behavior and
   production-jar exclusion.
2. Prepare matched original/off/on quality profiles with exactly the same
   world, request bytes, control, resolution, radii and native shader quality.
   Retain runtime locks, loaded artifact hashes and compiled dev-source identity
   through the existing runner; this harness's mod versions alone do not prove
   artifact identity.
3. Run one short 20-second request first and inspect warmup, actual interpolated
   camera trajectory, yaw wrap, correct after-GUI framebuffer, first/end frames,
   callbacks/hashes and clean owned shutdown. Confirm screenshot absence when
   the activation property is missing.
4. Retain deliberate invalid-request and interrupted/changed-input failures;
   verify no file escapes, no queued image growth, and explicit incomplete
   status under write backpressure.
5. Run original/off/on sequences serially, compare retained source path times
   and actual cameras rather than filenames alone, and inspect near/transition/
   far surfaces, thin foliage, water/reflection borders and temporal stability.
   Captures are quality evidence; use independent repeated non-capturing runs
   for performance acceptance.

No moving quality, shader compilation, configuration bridge, pack compatibility
or performance gate is claimed by this prototype.


## Root integration checkpoint

Root transferred the reviewed prototype into
src/devClient/java/com/pp/shaderlod/dev/CameraQualitySession.java and added the
bounded ComparisonClient tick/render hooks and Gradle cameraQuality/property
forwarding. Prepared modconfig is preserved, quality mode uses hiddenHUD, old
benchmark/workload/screenshot paths are gated, and GUI batches flush before
capture. Invalid requests retain rejection and do not start a benchmark.
Root also added exact loaded jar/class-directory input fingerprints at start/end
and world/pipeline reference-identity guards, so same-name reloads cannot silently
reset histories. Runtime-transformed class bytes remain unverified.

The transfer is source-only so far; compiler/runtime acceptance stillpending
while the independent benchmark-mode worker completes its freeze. Prototype
source outsideclasspath is retained until integrated build verifies transfer.


## First actual harness smoke

Root integrated build passed and productionJar excludes CameraQualitySession.
Original MakeUp noDH smoke exec12937 exit0, UUID0389e031-c00e-4cc8-84f1-410aeb6bcda6,
statuscomplete/all41 PNGcallbacks and hashes verified. Actual rendered camera
followed the eased40block path with maxZerror0.000097blocks; eyeY69.62 for
playerfeet68, normalIris clocks advanced, expectedhiddenHUD and cleanshutdown.

The first root-chosen route z680.5→720.5 passed throughterrain (spectator) in
its finalPNG. It proves actual capture/interpolation rather than useful water
quality acceptance. First active route retains that same path for reproducible
smoke, then a distinct immutable corrected request moves towardwater at
z680.5→640.5. Minecraft yaw−180 faces negativeZ; root's first direction
assumption was wrong. No shoreline/transition/glint quality accepted fromr1.
Rawoutputs remain under each profile's ignored camera-quality/UUID directory.


Rootcompleted corrected original/off/on20second2FPS shoreline sequences, each
41/41callbacks and SHA256 verified, requestbytes/path/dimensions matched. The
transferred source is now the single maintained devClient copy; reviewed
outsideclasspath draft removed after integrated build/runtime verification.
These sampledclips verify movingcapture and preserve near/farframes; they do
not establish high-rate shimmer/glint/underwater/transition quality or speedup.
