# GPU timing and broader Photon/DH LOD

Created: 2026-10-03. Status: active.

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

Existing two-window preview uses identical patched pack with LOD on/off and
currently only modifies PCF. It has no GPU instrumentation and no DH. Current
working changes from that preview must be preserved. New work begins here.
Next: inspect shader programs and GL timing support, implement owned partitions,
build the landscape comparison, validate serial timings, then open final pair.
