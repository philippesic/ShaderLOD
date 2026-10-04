# GPU timing and broader Photon/DH LOD

Created: 2026-10-03. Status: complete.

## Objective

Add real GPU timing, expand performance-relevant Photon distance LOD, integrate
DH into a long-range landscape, and leave two floating comparison windows open
with FPS and GPU milliseconds for user visual inspection.

## Acceptance

- GPU timings use actual supported OpenGL queries, asynchronous retrieval, and
  clearly defined frame/pass scopes; unsupported capability is explicit.
- Explicit pinned Photon patches reduce meaningful work in several effects,
  preserve a disabled baseline, expose useful presets, and cover DH geometry
  with correct depth/distance reconstruction.
- Compile/test relevant variants, then run actual Iris/DH clients on the local
  Apple GPU with matched common settings and a reproducible landscape scene.
- Record a warmed, single-client alternating baseline/LOD timing check separately
  from the shared-GPU visual preview. Report results without promising a gain.
- Final two windowed clients render matching landscapes with longer vanilla
  distance and warmed DH terrain, display FPS/GPU timings, and remain available
  for user inspection. Do not use the sparse pillar fixture for acceptance.

## Scope and ownership

NeoForge 1.21.1, Iris 1.8.12, Sodium 0.6.13, DH 3.3.3, Photon v1.3b. The local
Mac's sampler/driver limits apply; do not silently substitute CPU/FPS timing for
GPU timing or alter only the baseline's common quality settings. User authorized
broader LOD, DH integration, timing work, and opening comparison clients.

One coordinator owns game launches, GPU access, scripts/comparison scene, Java
config/uniform contract, and integration. Bounded agents own timing sources and
shader sources with explicit file partitions. No remote or publishing actions.

Initial candidate effects: fog marching, GTAO, cloud marching, opaque shadow
filtering, and SSR/material paths where compatible. Choose useful work based on
pinned source and actual hardware; document unimplemented effects rather than
claiming exhaustive coverage of every Photon pass.

## Current checkpoint

Expanded patch implements actual loop/ray reductions in PCF, screen-space
shadows, GTAO/SSAO, fog/LPV, clouds, SSR, and POM. Six packaging tests including
405 GLSL compilations pass. Java build and timing-statistics checks pass.
Bridge version 2 supplies preset0/1/2; enabled/disabled controls remain live.

The actual Mac driver reports timestampBits=0, elapsedBits=32. Asynchronous
GL_TIME_ELAPSED frame/pass-alternating queries work with positive world/frame
and pass results. New landscape renders at vanilla24/DH128chunks, source cache
generation disabled,2DHthreads. Shared camera selected x256.5,y95,z768.5,
yaw135,pitch9,time7000. DH reports124/348 visible/total buffers.

First single-client PERFORMANCE OFF/ON/OFF/ON pass-profile test completed:
game-render pooled means9.213/9.010ms (about2.2% lower), clouds0.539/0.415ms,
fog0.164/0.050ms, opaque lighting0.358/0.298ms. Frame/pass values are from
alternate frames and must not be summed. Full-frame gain is modest; additional
lower-overhead timing and preset validation remain. Raw data is in ignored
run-landscape/lod-on/benchmark-pass-profile.json.

Completed: original Photon baseline has `modPresent=false` and works with DH
and the independent timer harness. Frame-only aggressive test initially gave
1.35% mean improvement; profiling motivated cached-lighting and water reductions.
Final implementation has22guardedupstreamtargets, sixpackagingtests including
495GLSL compilations, Java build/test and timing-statistics checks passing.

Final aggressive + cached-lighting serial test: OFF7.960ms/ON7.523ms mean GPU
render interval (5.49% lower), medians7.996/7.671, p959.192/8.116. It used
4,690/4,919samples; pairwise mean reductions2.83% and8.00%. This is one host
and scene, not universal speedup or pureGPU utilization. Full methods/scopes,
raw reports and quality limits are in `docs/GPU_RESULTS.md`.

Final floating windows launched and validated: left patchedPhoton, ShaderLOD
present, AGGRESSIVE preset; right originalPhoton, ShaderLOD absent. Both have
DH, vanilla24/DH128chunks, samewarmedscene/camera/options, FPS/GPUrender/pass
overlay. Initial pair snapshots showed left71FPS/12.48ms and right67FPS/13.65ms;
shared-GPU snapshots are not a standalone benchmark. Both reported DH124/348
visible/totalbuffers. Shader fallbackfalse, no pipeline errors.

Live handoff: Java PID35268 left (tool session85804), PID35252 right (session88336).
Logs `dev/logs/final-landscape-on-console.log` and `final-landscape-off-console.log`.
Quit normally to save; verifyPID identity before targeted termination because
these identities are stale after relaunch. All bounded workers are finished.
No remote/publishing operations. User now owns visual fidelity assessment.
