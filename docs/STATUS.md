# ShaderLOD status

Updated: 2026-10-03.

Active goal: [GPU timing and broader Photon/DH LOD](goals/gpu-lod-dh-comparison.md).
Status: active; shader expansion, timing instrumentation, and landscape comparison in progress.

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
and GPU performance have not been measured. First PCF LOD exists; SSR/POM and
DH-specific LOD remain future MVP work. No remote host, publishing destination,
or push workflow is configured.

Next useful work: measure PCF LOD enabled versus disabled on a representative
vanilla scene with DH absent, then decide whether to expand the effect set.

The first two-window visual preview showed nearly identical FPS; no performance
benefit is established. An independent source/options audit found medium PCF
often saves zero lookups because Photon already exits after four. Far savings
cover only the remaining shadow-map range near 96–128 blocks. The sparse fixture
is a visual smoke test. Actual lookup-count instrumentation and a single-client
GPU timing test are still needed; see `docs/PHOTON_INTEGRATION.md`.
