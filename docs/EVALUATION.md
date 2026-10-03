# Evaluation

## Setup evidence

| Check | Required evidence | What it establishes |
| --- | --- | --- |
| NeoForge skeleton | Successful build/test command and output artifact | Java and build setup |
| Runtime dependencies | Pinned versions, artifact sources and hashes | Reproducible dependency selection |
| Client startup | Launch command and runtime log | Loader/mod startup |
| Photon under Iris | Selected pack, successful compilation and in-world scene | Working shader runtime on recorded hardware |
| DH installed | Mod loaded and actual distant terrain in a scene | DH render integration |
| DH absent | Iris/Photon client renders without DH | Optional dependency behavior |
| Initial LOD | Disabled/enabled comparison plus near/far/transition inspection | Observable first distance LOD behavior |
| Agent setup | Parse profiles/config, execute hook, validate local skill | Local infrastructure correctness |

A shader parser or lint pass is useful but does not substitute for shader
compilation on the Minecraft OpenGL driver. Report partial checks explicitly.
A setup pass need not prove the entire later optimization hypothesis.

## Performance proof

Use a repeatable scene with a fixed seed/save, coordinates, camera direction,
time/weather, resolution, render distance, resource pack, and Photon preset.
Record Minecraft/NeoForge/Iris/Sodium/DH versions, Photon revision and patch,
GPU, driver, OS, Java, mod config, and every relevant shader option.

Warm up chunk loading, shader compilation, temporal effects, and GPU clocks.
Compare the same patched pack with LOD disabled versus enabled so unrelated
upstream changes do not enter the result. Keep DH absent for the first normal
terrain optimization proof; test DH as a separate compatibility/extension run.
Use repeated windows or interleaved runs to expose noise and drift.

Measure GPU frame time when tooling on that hardware permits it, with per-pass
or capture evidence for the modified effect. Record median and tail behavior,
measurement method, sample duration, and variability. FPS may diagnose total
frame behavior but is insufficient to attribute a GPU optimization. CPU/chunk
loading bottlenecks can hide the result.

Before the first performance decision, record the acceptance criterion with the
user in `docs/DECISIONS.md`. The illustrative 8 ms to 6 ms example in the idea
outline is a motivating example, not a frozen requirement. If gains are within
noise, investigate whether branching or changed samples actually reduce work
before adding more packaging.

## Visual and compatibility checks

Inspect near objects, transition bands, distant geometry, thin geometry,
reflections, water, moving camera, temporal accumulation, and hard material
boundaries for the effect changed. Capture the baseline and LOD view from the
same camera. Include debug LOD colors when useful; a debug overlay itself does
not prove that an expensive effect changes its workload.

For the MVP, expand to DH terrain/water and the vanilla/DH depth boundary,
shader reloads, config reloads, unsupported Photon versions, Iris absence,
DH absence, and renderer version mismatches. Only promote a component set to
supported after its runtime behavior has been checked.

Keep raw logs/captures/worlds outside Git and link reproducible local paths or
summarized reports from the active goal. Never label a planned check as passed.
