# Initial GPU timing results

Observed 2026-10-03 on Apple M3 Max, macOS27.0, OpenGL4.1Metal91.7, Java21,
Minecraft1.21.1/NeoForge21.1.253/Iris1.8.12/Sodium0.6.13/DH3.3.3/Photonv1.3b.
The actual timer probe returned timestampBits0 and elapsedBits32. GPU values
come from non-blocking OpenGL elapsed queries, not inverse FPS or CPU clocks.

Scene: seed8675309, natural forest/coast landscape, camera256.5/95/768.5,
yaw135,pitch9,time7000, clear weather, 1488×836 framebuffer. Vanilla24 chunks,
DH128 chunks, generation disabled, two DH threads, warmed terrain cache.
Common Photon options use the reduced Mac profile. SSR/POM/LPV features were
compiled but inactive in this scene and are not claimed as measured contributors.

## Serial measurements

One patched-pack client alternated OFF/ON/OFF/ON. Each phase settled for eight
seconds and sampled twenty seconds. The disabled path retains original budgets;
compiler/unrolling differences from original Photon are not eliminated by this
test. World generation/initial loading was excluded by warmup; moving cloud
histories, driver scheduling, and clock drift remain sources of variation.

| Test | OFF mean GPU render | ON mean GPU render | Reduction |
| --- | ---: | ---: | ---: |
| Performance; frame/pass query alternation; cached lighting unchanged | 9.213ms | 9.010ms | 2.21% |
| Aggressive; frame-only queries; cached lighting unchanged | 7.895ms | 7.788ms | 1.35% |
| Aggressive; frame-only; water LOD + reduced cached lighting | 7.960ms | 7.523ms | 5.49% |

Different rows used different timing overhead/code versions and cannot be
compared as an incremental ablation. The final row is the final implementation
and option set. It used a 30-second initial warmup on an already warmed save,
4,690 OFF frame samples and 4,919 ON samples. OFF/ON medians were7.996/7.671ms;
p95 values9.192/8.116ms. Individual consecutive OFF→ON blocks improved mean
render time by2.83% and8.00%; the combined5.49% is not a universal guarantee.

`game-render` measures the GPU timeline interval around GameRenderer, including
world/Iris/DH/HUD work. It excludes buffer swap, simulation, and frame limiting.
CPU command-starvation gaps can affect the GPU timeline interval. It is not a
GPU busy-time/hardware-utilization counter. See `GPU_TIMING.md` for scope details.

The earlier Performance pass-profile test showed clouds0.539→0.415ms,
fog0.164→0.050ms, AO0.057→0.037ms, and opaque lighting0.358→0.298ms. Those pass
values came from alternate frames to the whole-render values; do not sum them.
They confirm several targeted passes became cheaper, while other work dominates
much of the frame.

## Reproduction and artifacts

```sh
python3 scripts/setup_comparison.py
./gradlew runClientLodOn -PgpuBenchmark=true -PgpuPassTiming=false -PinitialWarmup=30
python3 scripts/summarize_benchmark.py run-landscape/lod-on/benchmark.json
```

Set `preset` to `AGGRESSIVE` in `run-landscape/control.json` for the final test;
comparison preparation enables the opt-in cached-sky setting in the patched pack.
Keep other game clients closed during serial measurement. Use longer warmup
when the save is cold. The raw reports are ignored/generated artifacts:

- `run-landscape/lod-on/benchmark-pass-profile.json`
- `run-landscape/lod-on/benchmark-aggressive-frame.json`
- `run-landscape/lod-on/benchmark-final-frame.json`
- summaries under `dev/logs/benchmark-*-summary.json`
- `run-landscape/comparison-manifest.json` for source/cache/options/pack hashes
- landscape screenshots and per-client logs under `run-landscape/`

The final visual pair differs from the serial effect-isolation test: its right
client uses original Photon with ShaderLOD absent, while left uses the patched
pack and aggressive preset. Both have the timer harness and identical common
settings. Concurrent windows share GPU scheduling/memory bandwidth; their live
FPS/GPU values are a preview, not a second standalone speedup measurement.

The cached-lighting option is explicitly a global quality reduction, not
distance LOD. Aggressive LOD reduces distant AO, cloud detail, water detail,
fog/ray quality, and other enabled effects. User inspection of fidelity is the
next acceptance decision. No claim of full Photon Ultra or general Apple
support, movement/TAA stability, or performance on other GPUs is made.
