# Development setup and verification

Observed on 2026-10-03 on an Apple M3 Max (30 GPU cores), macOS 27.0 arm64,
OpenGL `4.1 Metal - 91.7`, Oracle Java 21.0.9. The game framebuffer was
1708 × 960. These are setup checks, not performance benchmarks.

## Pinned environment

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.253 |
| ModDevGradle | 2.0.141 |
| Gradle wrapper | 9.2.1 |
| Java target | 21 |
| Iris | 1.8.12 + mc1.21.1, NeoForge |
| Sodium | 0.6.13 + mc1.21.1, NeoForge |
| Distant Horizons | 3.3.3 + mc1.21.1 |
| Photon | v1.3b |

Exact upstream artifact URLs and SHA-512/SHA-1 values are in
[`dev/runtime.lock.json`](../dev/runtime.lock.json). Photon assembly adds source
SHA-256 guards, a patch manifest, and the ShaderLOD helper. No dependency jar
or upstream shader archive is committed.

The Iris artifact's embedded version is `1.8.12-snapshot+mc1.21.1-local` despite
the public stable release label. This is the checksum-verified official artifact,
not a local rebuild. The loader accepts its dependency metadata. The independent
[runtime review](RUNTIME_REVIEW.md) explains the version comparison and NeoForge
Minecraft compatibility fallback and audits Iris's references to the DH API.

## Reproduction

```sh
python3 scripts/setup_runtime.py
./gradlew build
./gradlew runClient -PsmokeTest=true
```

For DH-absent validation, stop the first client, then run:

```sh
./gradlew runClientIris -PsmokeTest=true
```

Without `-PsmokeTest=true`, the launch opens the ordinary client. The separate
`shaderlod_dev` development mod is inert unless that property is set and is
excluded from the production jar.

The harness uses seed `8675309`, world `ShaderLOD-Smoke`, a stone platform
at y=130, and pillars at approximately 16, 56, and 110 blocks. It fixes noon,
clear weather, mob spawning, and the initial camera, then captures normal LOD,
debug zones, and disabled LOD from the same camera. It later turns toward the
horizon and logs DH's visible/total terrain-buffer count when DH is installed.
Re-running preserves that dedicated save, so distant generation can warm up.
It uses a 4 GiB heap and disables pause-on-focus-loss in memory for the run.

The bootstrap preserves existing options. `--profile mac` initializes these
Photon options for both the patched and baseline archive:

```properties
SH_SKYLIGHT=false
SHADOW_COLOR=false
COLORED_LIGHTS=false
CLOUD_SHADOWS=false
ENVIRONMENT_REFLECTIONS=false
SKY_REFLECTIONS=false
SHADOW_VPS=false
BORDER_FOG=false
```

Link reflection showed the first seven options reduce Photon's deferred4 sampler count from 18
to 14; border fog does not reduce the linked count because the vertex program
also uses its sampler. PCF, GTAO, fog, clouds, and DH terrain remain active.
The source/linker count does not include all possible Iris transformations.
For new standard desktop profiles use `--profile standard`; this does not
overwrite an existing Mac profile's settings.

## Evidence

`./gradlew build` passed on the final 1.21.1 target. JUnit checks strict block
boundaries, disabled LOD, invalid ordering, non-finite values, and overlapping
transition-band rejection. Six packaging tests passed, including deterministic
ZIP output, upstream/hash/anchor guards, license and untouched DH preservation,
and 48 real deferred/translucent fragment compilations using glslangValidator.

The Iris-only client also rendered the same normal/debug/disabled scene with
`DH=false`, `shadersInUse=true`, and `fallback=false`. Its screenshots verify
that DH is optional for the first LOD effect.

The combined client discovered Iris, Sodium, DH, ShaderLOD, and the development
harness. It registered protocol-1 uniforms and reported:

```text
pack=Photon-ShaderLOD.zip, patchedPackName=true,
shadersInUse=true, fallback=false, DH=true,
enabled=true, near/far=32.0/96.0, transition=8.0
```

DH initialized its OpenGL renderer and shader programs. Its in-world render
statistics reached `VBO Render Count: [159/448]`, later `[154/448]`; horizon
captures show distant terrain. The LOD debug capture shows green, amber, and
red vanilla geometry; the disabled-LOD debug capture shows every vanilla zone
green while distant DH terrain keeps its ordinary shading. These changing
screenshots verify live integer configuration transport through the Iris hook.

Local evidence lives in ignored generated directories:

- `dev/logs/dh-smoke-console.log` and `dev/logs/dh-smoke-latest.log`
- `dev/logs/final-client-console.log` and `run/logs/latest.log` for the combined client left open at handoff
- `dev/logs/iris-smoke-console.log` and `run-iris/logs/latest.log`
- `run/screenshots/shaderlod-smoke-quality.png`
- `run/screenshots/shaderlod-smoke-debug.png`
- `run/screenshots/shaderlod-smoke-disabled-debug.png`
- `run/screenshots/shaderlod-smoke-horizon-900.png`
- `run/screenshots/shaderlod-smoke-horizon-1500.png`
- equivalent screenshots in `run-iris/screenshots/`
- `dev/runtime-doctor.json`, produced by `python3 scripts/doctor.py`

Agent config and four profiles parse as TOML. The advisory hook executed for
startup/resume/compact; the local `start-goal-team` skill passed skill-creator's
validator. The repository config preserves the user's model preferences.

## Limits and next work

The first combined profile failed with `No more available texture units while
activating sampler shadowtex0`; reducing optional sampler use resolved it.
Apple also logged unsupported extension warnings. Upstream's general Apple
support position has not changed. Standard Photon/Ultra, colored shadows,
reflections, variable penumbras, other dimensions, and movement/TAA stability
still need desktop-GPU validation.

The PCF patch is executing inside the active pipeline, and zone/control behavior
is visible. Debug colors alone do not prove a GPU speedup or validate all
shadow quality tradeoffs. Next, benchmark PCF enabled versus disabled on a
normal vanilla scene with DH absent, using fixed options and GPU frame timing.
After that, consider SSR/POM and dedicated DH LOD. This setup does not claim
the larger MVP is implemented.
