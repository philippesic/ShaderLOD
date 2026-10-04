# ShaderLOD status

Updated: 2026-10-04.

Active goal: [Complete frame attribution and rendering proof](goals/full-frame-proof.md).
Status: active; full CPU/GPU attribution and controlled workload tests in progress.

Completed goal: [GPU timing and broader Photon/DH LOD](goals/gpu-lod-dh-comparison.md).
Status: comparison implementation and validation complete; windows open for user visual inspection.

Completed goal: [Set up the ShaderLOD development environment](goals/setup-environment.md).

Scope: NeoForge client skeleton, required Iris, Photon integration foundation,
optional DH present in the development runtime, and reusable local agent/docs
infrastructure. First rendering proof precedes the larger Photon/DH MVP.

Minecraft 1.21.1 / NeoForge 21.1.253 / Java 21 is the first target, following
the user's request to prioritize the established ecosystem. Exact Iris, Sodium,
DH, and Photon artifacts are locked in `dev/runtime.lock.json`.

Verified: Java build and JUnit tests, six shaderpack tests including 48 GLSL
compilations, Iris/Photon in-world rendering with DH present and absent, live
LOD debug/enable controls, DH renderer initialization and visible terrain-buffer
counts, and local agent profiles/hook/skill validation. See [setup evidence](SETUP.md).

The Apple M3 Max uses a reduced Photon profile; standard/Ultra desktop rendering
remains unverified. Multi-effect LOD now covers shadows, AO, fog, clouds,
SSR/POM when enabled, and water. DH uses correct reconstructed distances in
shared shading. No remote host, publishing destination, or push is configured.

Real asynchronous GPU elapsed queries work on this host (timestampBits0,
elapsedBits32). Final serial aggressive+cached-lighting test measured GPU render
7.960→7.523ms (5.49% mean reduction), with variability and scope limits in
[GPU_RESULTS.md](GPU_RESULTS.md). Six packaging tests and495GLSL compilations,
Java checks, timing-statistics tests, original-Photon absent-mod baseline,
and actual DH landscape rendering pass.

Final pair: vanilla24/DH128chunks, matching landscape/camera; left aggressive
ShaderLOD with cached-lighting reduction, right original Photon without ShaderLOD.
FPS/GPUms/pass timings visible. Both clients remain running for user inspection.
User assessment on2026-10-04: distant trees visibly lose shadows/depth for a
small gain. Aggressive quality is not accepted as a worthwhile default.
Next: preserve occlusion/shadow structure, isolate individual visual costs,
and profile untargeted world/DH rendering plus CPU submission before further cuts.

The earlier PCF-only pillar preview showed nearly identical FPS. That audit
remains in `PHOTON_INTEGRATION.md`; it motivated the expanded measured work.
