# ShaderLOD project overview

ShaderLOD explores reducing expensive Photon shader work as scene distance
increases. The first platform is NeoForge with Iris required. Photon is the
explicit compatibility target, and DH is optional for the mod while included
in the development runtime for compatibility checks.

The initial distance proposal is full quality through 32 blocks, medium quality
through 96 blocks, and low quality beyond that. These are starting defaults,
not a measured optimum. Define distance in camera-relative block units and
use transition bands or stable dithering to avoid a visible moving ring.

## Development sequence

1. Establish a reproducible NeoForge client skeleton and compatible Iris,
   Sodium, Photon, and DH runtime. Add durable goal/team infrastructure and a
   small LOD foundation that can be inspected and tested.
2. Prove one distance-sensitive Photon effect works under Iris with DH absent.
   Compare the same scene with the change disabled and enabled; inspect both
   visual transitions and GPU execution time before expanding infrastructure.
3. Build the Photon MVP around several measured effects. Candidate effects are
   POM/material work, shadow filtering, and SSR. Add validated configuration
   transport and useful quality presets as evidence supports them.
4. Extend LOD to DH terrain/water/shadows and validate the vanilla/DH boundary.
   Keep DH optional and test both installed and absent configurations.

## Architecture boundaries

The NeoForge client mod owns config, loader/runtime detection, and integration
lifecycle. Photon owns explicit GLSL LOD logic through a pinned, maintained
patch or derivative. Iris provides the shader runtime. DH extends terrain
rendering and eventually needs dedicated integration checks.

A Java config file does not automatically become an Iris custom uniform.
Document and test the actual bridge before claiming the mod controls shader
quality live. Shader-pack options can support the first proof independently.
Do not expand into automatic rewriting of arbitrary shader packs during setup.

## Initial risks

Distance branches may save little GPU work when neighboring pixels diverge.
Some effects cannot safely change samples or ray-march steps without changing
appearance or temporal stability. Loader and renderer versions must be
compatible as a set. Photon may require hardware/driver features unavailable
on the development Mac; record this as a runtime limit rather than calling
static source inspection a rendering pass.

The setup goal establishes an environment and evidence base. It does not
promise an FPS gain, freeze the product's final quality thresholds, or complete
the later Photon/DH MVP.
