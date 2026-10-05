# Fresh-agent handoff

The user requested a fresh project chat with a native goal, while the original
chat focuses on common LOD protocols. The overall product is configurable LOD
across many shader packs, using explicit adapters. Photon remains the reference
implementation. The latest authorization permits web research and exploratory
tests of other popular shaders now; it does not authorize invented performance
claims, universal GLSL rewriting, remote pushes or publishing.

## Ownership

- Original chat owns common protocol/configuration: `src/main` config and Iris
  uniform transport, shared shader helpers, Photon protocol integration and
  corresponding tests. No GPU client is currently running.
- Fresh chat owns performance research, compatibility experiments, optional
  performance-mod stacks, benchmark execution and evidence. It becomes the sole
  owner of Minecraft clients/GPU experiments. It may use bounded native workers.
- Coordinate through durable checkpoints before overlapping edits. Existing
  pinned Photon patches/helpers belong to the protocol owner for this split;
  request or record a concrete file handoff before modifying them.
- Keep one active project goal in STATUS/goals. A fresh native goal in the new
  chat can execute that shared objective; do not declare the previous project
  work complete merely because responsibility transferred.

## Current evidence

Main checkpoint before this handoff: `a62ac95` (latest implementation `8da8943`).
Minecraft 1.21.1 / NeoForge 21.1.253 / Iris 1.8.12 / Sodium 0.6.13 / Photon v1.3b /
optional DH 3.3.3; exact locks under `dev/`. This is an Apple M3 Max / OpenGL 4.1
development machine. Full desktop Ultra, POM and voxel features are unverified.
DH+SSR exceeds the available sampler budget in the current reduced profile;
SSR without DH rendered in the inspected shoreline scene.

There is **no accepted meaningful, repeatable ordinary-scene performance win**.
Premium full-resolution clouds produced exploratory 13–29% GPU reductions;
ordinary forest repeats were much weaker. Earlier measurements omitted shader
wetness, now captured/controllable. No second pack is implemented.

Implemented shader effects include cloud/fog/water/SSR/material distance budgets
and experimental shadow/AO cuts. Lighting is protected by default after visible
distant-tree losses. Fixed-count water kernels and analytic normals were tested;
analytic defaults off. Cloud modes 1/2 are experimental, default mode 0.
Protected lighting now avoids generic tier calculation except inside debug.

Core Iris bridge remains protocol 2: global enable, preset, generic distances and
debug. Cloud/water bands and per-effect switches remain shader options, not a
verified live per-effect mod protocol. A new common extension has not yet been
implemented. Preserve old-pack compatibility when adding one.

Latest clean Photon archive:
`build/photon-lighting-tier-no-atmosphere.zip`, SHA256
`b15d3c50f192e0e3de8e912afba2cc531e7d0520e3cbe573a7e35b993528e16c`.
Iris rendering was verified. Same-JVM alloff controls showed no meaningful gain
or large enable penalty. The runtime still has unexplained cross-JVM variance.

CPU experiments are secondary and default off: positive-vertex frustum math,
separate shadow region lists and a frame-local acceptance cache. Ownership
snapshots confirmed separation; cache oracle matched 5.16 million real queries
with zero mismatches but accepted only 5.34% without fallback. Nine layouts
replayed 3,594 exact real queries with zero mismatches; all were slower in a
qualified CPU model (1.36–1.76x). Real four-phase cache control was mixed:
OFF 151.76/151.25 FPS, ON 146.89/152.07. Do not promote it.

No sunlight cadence or shadow-map refresh frequency has been changed. Source
inspection corrected a prior assumption: Minecraft ignores partial tick in
getTimeOfDay, so sunlight follows stored day-time updates, normally 20 Hz.
Possible CPU visibility reuse still needs complete keys, producer epochs,
touched-section stamp/direction replay and task ownership proof.

## Tools and runtime

Read `docs/REPEATED_RESULTS.md`, `IN_PROCESS_RESULTS.md`, `GPU_TIMING.md` and
`FRUSTUM_REPLAY.md` for measured limits. `run_proof_matrix.py` provides repeated
serial launches; `analyze_inprocess_control.py` validates four-phase controls;
`replay_frustum_coherence.py` is an offline CPU model, not FPS evidence.
Profiles and raw data are ignored under run-workloads/dev/logs. Baseline tasks
exclude the main mod physically. Screenshots and effective shader options,
artifact hashes, wetness and window/pacing states are preserved.

The minimal stack includes mandatory Iris/Sodium. Optimized adds ModernFix
5.27.24, FerriteCore 7.0.3 and ImmediatelyFast 1.6.14, pinned and actually loaded.
EntityCulling is pinned but its extra stack remains a pending runtime check.
Compare original high, original lower quality, patched disabled and active;
separate stack gains from ShaderLOD's incremental gain.

Last comparison windows (handles 5769/52317) exited normally. Side-by-side values
share CPU/GPU/compositor and do not establish isolated speedup. Both were forest
24 detailed chunks + DH128, protected lighting, cached sky/geometry/culling off.

## Preserved unfinished work

An interrupted, source-only key-probe worker left one untracked partial file:
`/Users/pippo/.codex/worktrees/shadow-key-probe/shaderlod/src/devClient/java/com/pp/shaderlod/dev/timing/ShadowKeyProbe.java`.
It has not been compiled, registered or rendered. Do not treat it as complete.
Other visibility/coherence managed worktrees hold earlier reviewed implementations
already integrated into main; preserve ignored logs before archiving them.
Main has an unstaged source-audit update to SHADOW_VISIBILITY_CACHE_DESIGN.md.

## Next work

Start the new native goal using the local start-goal-team skill. Investigate
high-cost distance-sensitive operations and documented methods through primary
sources, then build explicit, measurable experiments. Research additional
popular/licensable packs and test compatibility without claiming support from
downloads alone. Prioritize reusable shader LOD and real frame improvements;
keep renderer experiments bounded. Record negative results and moving-camera
quality, not only best FPS. The protocol chat will implement common effect
configuration and transport; avoid simultaneous file edits or shader reloads.


## Takeover receipt: 2026-10-05

Fresh chat `01a10adc-41ba-76a1-a5eb-1f27c6be8fef` created its native unbudgeted
goal and continues the existing sustained-performance project checkpoint. It
accepts sole GPU/client ownership. No Minecraft client was running on receipt.
First work is primary-source research and optional culling-stack validation,
followed by explicit pinned other-pack experiments. Existing protocol files
remain owned by the original chat.
