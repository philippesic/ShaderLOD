# ShaderLOD status

Updated: 2026-10-03.

Completed goal: [Set up the ShaderLOD development environment](goals/setup-environment.md).
Status: setup complete; no new project goal has been requested.

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
and GPU performance have not been measured. First PCF LOD exists; SSR/POM and
DH-specific LOD remain future MVP work. No remote host, publishing destination,
or push workflow is configured.

Next useful work: measure PCF LOD enabled versus disabled on a representative
vanilla scene with DH absent, then decide whether to expand the effect set.
