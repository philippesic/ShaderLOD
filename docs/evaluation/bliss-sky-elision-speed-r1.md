# Bliss sky SSAO dead-work speed candidate R1

**Private runnable source candidate; rendering, quality and speed unverified.**
October 10, 2026. Owner `/root/bliss_sildur_speed` owns only this report,
`scripts/build_bliss_sky_elision_r1.py`, `tests/test_bliss_sky_elision_r1.py`,
`patches/bliss-experiments/sky-elision-r1.json` and ignored
`dev/logs/speed-replication-r1/bliss-sky-elision/`. Parent owns runtime, reloads,
source integration, builds, staging and acceptance. No client or build launched;
no owned live process remains.

The human objective is practical original-relative gains comparable to Photon's
qualified approximately 9% reference. This is distinct from Bliss's closed fixed
0.85 raster and cloud-pruning trials; neither is altered or retried. Sildur's
closed SSDO/cloud/receiver work is preserved, and its renderer confirmation is
parent-owned.

## Mechanism and expected benefit

Actual native entry is `world0/composite1.fsh` → `dimensions/composite.fsh`.
With the authentic retained native 463 option map, `indirect_effect=1`,
`Ambient_SSS=true` and the native `DENOISE_SSS_AND_SSAO` definition activate
seven-tap SSAO/ambient subsurface scattering. Native code calls `SSAO`, then
unconditionally replaces its result with `vec2(1,0)` for `swappedDepth>=1`.
The candidate returns that same sky result before the helper call. All geometry,
near detail, hand and foliage calls remain native; no distance/sample/scale/filter
or scene/preset adjustment is proposed.

Each qualifying sky pixel can avoid up to seven explicit depth fetches plus
native sample-position trigonometry, depth reconstruction and AO/SSS arithmetic.
This is source avoidance, not measured hardware savings: the Apple compiler may
already remove the discarded call or flatten the new branch. Expected practical
total-frame gain is **0–10%, a hypothesis**, with roughly 2–10% plausible if sky
coverage and this pass's measured share are substantial. If SSAO consumes fraction
S of original frame time and eligible sky contributes fraction F of its cost,
the optimistic total throughput gain is `1/(1-S*F)-1`, before branch costs.
Neither S nor F has been measured for this candidate.

Intrinsic overhead is a depth/uniform predicate and branch, with no new texture
read, image allocation, temporal history, reconstruction or CPU frame operation.
Quality risk is low because native already discards exactly this result; all
implicit texture calls remain outside the branch and unchanged, and the entire
SSAO helper remains exact. Real compiler/rendered still and motion behavior remain
unverified. Source equivalence cannot stand in for rendering or speed.

## Inputs, artifacts and restoration

Original is Bliss 2.1.2 Chocapic13 edit, SHA
`f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c`.
All 347 original file members remain; only `shaders/dimensions/composite.fsh`
changes. All 346 others, including license/credits/README, native settings,
properties, AA/history, pass graph and texture assets, are byte exact.
Private local testing only; no redistribution permission claimed.

The builder requires exact original native override bytes SHA
`cc80720a057a3beb7da884e5cd54df371fcae990cc7ca601accc271f7d1d73b2`, copied
from the authentic prior original sidecar, and appends only the private mode.
Expected option maps are explicitly **predicted**, requiring actual Iris map
comparison: native 463 original and native 464 derivative with sole extra
`SHADERLOD_BLISS_SKY_ELISION`.

| Archive under ignored evidence directory | SHA256 |
| --- | --- |
| `Bliss-SkyElision-disabled-r1.zip` | `b205ae53e227a9e3166a26610c460cb61ee96a123f4c71f0be862ac0cf5b047f` |
| `Bliss-SkyElision-control-r1.zip` | `29fcbdfbcd9829febb920339b756eba2dae72133bedbc4b91a701f88ff292039` |
| `Bliss-SkyElision-candidate-r1.zip` | `6ab63cbb1b2c6ce2367e63c9afebfee51a693e3c4b25bca7588c3815fb0843b7` |

Mode 0 is source-native disabled; mode 1 calls full native work; mode 2 skips only
native-discarded sky work. Exact Iris 10812/MC 12101/Mac/Overworld/IS_IRIS are
required, with DH/VOXY falling back to native source. Runtime activation requires
bridge 2, enabled 1, preset 0..2. Missing preset initializes to -1; Off, Quality 3,
missing/unknown transport/preset preserve native. Native AA selection is untouched.
The mode 1 uniform declarations may optimize out because they are unused; do not
require linked control uniforms as an invented consumer proof.

Real Off/restoration means reselecting the pinned original plus its original
native sidecar; normal UI recognition/Apply/Cancel/failed-Apply restoration is not
implemented or accepted in this private prototype. Unsupported versions/DH have
source fallback evidence only. DH/PBR rendering, other loaders/versions, normal
UI lifecycle and hardware breadth remain unverified.

## Checks and parent next action

Commands actually run: private builder; focused unittest discovery; `clang -E -P`
and `glslangValidator -S frag` through that test; pinned Iris `javap StandardMacros`.
**Five focused tests pass**, covering actual source/options/archive pins, all
unchanged members, whole disabled/fallback native token parity, Nether/End,
unchanged helper/implicit calls, control/preset truth cases, pin/overwrite refusal,
and all three active wrapper GLSL 330 compatibility parses.

The first static parse failed because the harness omitted native `MC_HAND_DEPTH`;
failure remains in `tests-failure-r1.txt`. Actual pinned Iris StandardMacros
bytecode establishes 0.125. The harness was corrected; no shader artifact changed.
The parent requested quiet while fallback checks were running; exact owned
PID 14430/session 42403 was stopped and verified absent. After explicit quiet
release the focused tests passed in 3.614 s (session 12510 exited 0). This is source
parsing with a declared Iris macro context, not Iris transformed GLSL 120, actual
Apple driver linking or measured speed.

Parent can use existing fresh bounded original/candidate runs and ordinary
paired captures with native 463/predicted464 discovery and real mod core transport.
No new GPU observer, sampler probe, framework or diagnostic tint is required.
After acceptable ordinary still/motion appearance, run one early matched
**original → full-work control → candidate → original return** bracket using the
existing averaged frame-count runner, fixed retained coast/day 7000/clear/nativeTAA,
same hardware/build/native options/resolution and warmup/window durations. Quiet
all workers/builds during timing. Report elapsed milliseconds per rendered frame
and original-return drift; GPU pass timings may attribute cost if already available.
Original is the denominator. Flat/regressive closes this fixed trial unchanged.

Raw evidence: `manifest.json` has all original/derivative member hashes and
sidecars; `expected-*-native*.json` contains predicted maps; `source-mode*.fsh`,
`glslang-mode*.txt`, `tests.txt`, and `iris-standard-macros.txt` contain checks.
Preparation consumed one bounded source attempt with a parent-requested quiet
pause. Parent retains the next runtime decision; no project completion claimed.
