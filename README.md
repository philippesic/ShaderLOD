# ShaderLOD

A NeoForge client mod and explicit Photon patch for distance-based shader quality.
Distance budgets now cover shadow filtering, screen-space shadows, GTAO/SSAO,
volumetric fog, LPV fog, clouds, SSR, and parallax. Balanced, Performance, and
Aggressive presets preserve near work and reduce distant sampling. Starting
terrain boundaries are 32/96 blocks; cloud bands use 512/4096 blocks.

Development targets **Minecraft 1.21.1**, chosen for the established NeoForge
ecosystem. [Version selection](docs/VERSION_SELECTION.md) records the public
evidence and its limits. Iris is required; Distant Horizons is optional.
DH terrain and water use their correct depth/projection in shared downstream
quality decisions. DH geometry/generation detail is unchanged.

## Start development

Install Java 21 and Python 3.10 or newer. The Gradle wrapper and Java toolchain
resolver are included. On a clean checkout:

```sh
python3 scripts/setup_runtime.py
./gradlew build
./gradlew runClient
```

`setup_runtime.py` downloads checksum-pinned Iris, Sodium, DH, and Photon,
assembles `run/shaderpacks/Photon-ShaderLOD.zip`, and initializes two independent
game directories. It preserves existing game options. It does not modify your
Minecraft launcher profiles, existing worlds, or accounts.

| Command | Runtime |
| --- | --- |
| `./gradlew runClient` | Iris + Sodium + DH, directory `run/` |
| `./gradlew runClientIris` | Iris + Sodium without DH, directory `run-iris/` |
| `./gradlew runClient -PsmokeTest=true` | Creates/opens the dedicated seeded smoke world, captures LOD states and the horizon, logs DH render statistics |
| `./gradlew runClientIris -PsmokeTest=true` | Same scene without DH |
| `python3 scripts/doctor.py` | Checks artifact hashes and summarizes runtime logs |

The smoke harness is a separate development source set and mod. It is excluded
from the distributable ShaderLOD jar. It changes only its dedicated world and
temporary in-memory rendering settings. Generated jars, saves, captures, and
downloaded dependencies are ignored by Git.

On macOS, setup initializes a reduced Photon profile because Apple OpenGL has
a limited texture budget. It disables SH skylight, colored shadows/lights, cloud
shadows, environment/sky reflections, variable penumbras, and border fog while retaining
PCF. This is a development smoke profile, not a validation of Photon Ultra or
general Apple compatibility. Other hosts initialize Photon's normal defaults;
`--profile standard` explicitly chooses those on a fresh game directory.

## Landscape comparison and GPU timings

After warming the source smoke world once, prepare independent cached profiles:

```sh
python3 scripts/setup_comparison.py
./gradlew runClientLodOn
# In a second terminal:
./gradlew runClientLodOff
```

These open two floating windows with matched cameras, vanilla24 chunks and DH128
chunks. Left runs ShaderLOD; right runs original Photon with ShaderLOD absent.
Both include the development timer harness and display FPS plus real GPU render
and pass milliseconds. Apple uses asynchronous elapsed queries; frame and pass
samples alternate because those queries cannot nest. Timings from concurrent
windows share the GPU; use the serial benchmark for performance evidence.

```sh
./gradlew runClientLodOn -PgpuBenchmark=true -PgpuPassTiming=false
python3 scripts/summarize_benchmark.py run-landscape/lod-on/benchmark.json
```

The benchmark uses one client and the same patched pack, alternating OFF/ON
twice after warmup. Close other game clients first. Raw samples are written to
the ignored run directory. Edit `run-landscape/control.json` to move both cameras
or choose a preset; the right-hand original Photon ignores LOD-only controls.
See [comparison preparation](docs/COMPARISON.md) and [GPU scopes](docs/GPU_TIMING.md).

## LOD controls

Use `/shaderlod status`, `/shaderlod preset balanced|performance|aggressive`, `/shaderlod enabled true|false`, and
`/shaderlod debug true|false` in a world. The patched Photon pack reads live
NeoForge values through the pinned Iris uniform bridge. The client config is
`run/config/shaderlod-client.toml` (or `run-iris/config/…`). Reload or restart
after editing a config file. The ShaderLOD screen in Iris has fallback pack
options; disable **Use mod config** to use those instead.

Build and packaging checks:

```sh
./gradlew build
python3 -m unittest discover -s tests -v
```

Install `glslangValidator` to run the 495 fragment-program compilation variants;
that check is skipped when the executable is absent. Iris/OpenGL runtime checks
are separate. Results and limitations are recorded with the active goal;
individual pass savings do not imply a proportional whole-frame improvement.

## Project guidance

- [Setup and runtime evidence](docs/SETUP.md)
- [Current goal and status](docs/STATUS.md)
- [Photon patch and uniform contract](docs/PHOTON_INTEGRATION.md)
- [Evaluation procedure](docs/EVALUATION.md)
- [Agent workflow](AGENTS.md) and [team operations](docs/AGENT_OPERATIONS.md)

Agent roles and checkpoint hooks live in `.codex/`. The repository-local
`$start-goal-team` skill lives in `.agents/skills/`; it records requested goals
and coordinates bounded agents while inheriting your model settings.

ShaderLOD's original code is MIT licensed. Photon retains its own upstream
license, which the pack builder preserves. Review that license before publishing
a Photon derivative; the mod jar contains no upstream Photon shaders or mod jars.
