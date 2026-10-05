# Miniature 2.19 core diagnostic consumer

Prepared 2026-10-05. This probe consumes existing core state for a diagnostic
overlay only. It does not adapt, disable or shorten SSR. Both new shader options
default off. Main transport, Photon, MakeUp and shared helpers are unchanged.
Root owns all archive assembly, driver compilation, reloads, captures and GPU
work. This worker stops at source/fixture evidence.

## Exact source and attribution

Pinned publisher release `miniature-shader-2.19.zip`, Modrinth `LWmZ94RG`, SHA256
`5f224e907657f3ffd95d37e37c27e555deef64fa56da1e13374f2c295241d7f0`.
Publisher SHA512, release URL and runtime locks are recorded in
`dev/shader-candidates.lock.json`. This release is not byte-identical to pinned
Git revision `bdb6e0020b4318d4e845902cbcc3e3552b0e9274`; only the release is
patched. Exact source CRLF bytes are retained in literal anchors/replacements.

Miniature is by **Mateus A. Kreuch**,
[official source](https://github.com/mateuskreuch/minecraft-miniature-shader),
[publisher release](https://modrinth.com/shader/miniature-shader/version/LWmZ94RG).
Copyright (c) 2022 Mateus A. Kreuch. The release omits its MIT notice, so the full
unchanged notice is restored from the pinned source LICENSE, SHA256
`397d80246d261805b953865428e9c244e5a7bd2bbbb6f3b37f16e884e697e4d1`.
`patches/miniature/LICENSE.Miniature` and ATTRIBUTION.md accompany the derivative.
Editable shaders, exact source patch and modification notice are packaged. New
source modifications use MIT terms. No publishing is performed.

Four existing source/UI members change: `shaders/shader.h`,
`shaders/program/final.fsh`, `shaders/shaders.properties`,
`shaders/lang/en_US.lang`. A new texture-free helper is added as
`shaders/common/shaderlodCoreProbe.glsl`. All remaining archive members are
copied byte-for-byte. No getReflectionColor, transformations, math/noise,
normal reconstruction, mask, shadow, lighting, outer ray step, hit bracket,
binary refinement, fallback or sampling budget is modified.

## Defaults and core contract

In the reflection-options diagnostic sub-screen:

| Shader option | Default | Meaning |
| --- | ---: | --- |
| SHADERLOD_MINIATURE_CORE_PROBE | 0 | Opt into four existing core fields for diagnostic color |
| SHADERLOD_MINIATURE_CORE_CUE | 0 | Show a status/band overlay on existing qualifying reflective pixels |

Cue zero excludes the helper include and overlay statements regardless of core
opt-in. The original final program's source is restored after preprocessing
those new guarded blocks out; original SSR call, uniforms and native output
remain intact. The added option definitions do not alter existing macros or
profiles. Restricted source-projection fixtures check exact original CRLF bytes
after removing only added diagnostic blocks/definitions; actual Iris-expanded
source and driver-image equivalence are still runtime checks.

With cue one, only the existing mask qualifies pixels:
`reflectivity > MIN_REFLECTIVITY && abs(reflectivityAndRoughness.z - 0.5) < 0.01`.
The original SSR call and reflection mix run in that mask before the cue is
applied. Qualifying reflective pixels may have an SSR miss and still show the
diagnostic, which helps distinguish consumption from reflection hit quality.
Water, glass or reflective blocks can qualify; other pixels remain untouched.
The helper adds no sampler and performs no texture access or derivative call.

The cue blends 80% status color with 20% original reflected scene rather than
discarding the original result. This retains its source data dependency and
does not introduce a new branch around original texture calls. The displayed
palette can therefore be tinted by the scene; it is not a promise of exact
RGB screen values. Native output alpha remains unchanged. Diagnostic visuals
and GPU cost are not the normal rendering baseline; cue must be off for both
quality comparisons and timing.

Only opted-in code declares the existing core uniforms:
shaderlodBridgeVersion (int), shaderlodEnabled (int), shaderlodNear (float),
shaderlodFar (float). The source transport registers those unconditionally
with Iris at PER_FRAME frequency and sentinel version 2. Exactly version 2 is
accepted. Absent/zero, old 1 and future/unknown 3 ignore transported distances
and show the unsupported status. Compatibility with a future version must be
reviewed rather than inferred from matching field names.

| Diagnostic state | Status palette before scene blend |
| --- | --- |
| Core option zero | Gray |
| Core option one; version absent/unknown | Magenta |
| Accepted version 2; global enable zero | Yellow |
| Accepted/enabled; within near distance | Green |
| Accepted/enabled; between near and far | Smooth green-to-blue gradient |
| Accepted/enabled; at/beyond far | Blue |

Global enable changes the diagnostic state only. It does **not** disable native
Miniature SSR or enable a LOD optimization. No preset, transition, debug or
per-effect fields are consumed; they are neither declared nor given invented
behavior. The shader cue is independent of shaderlodDebug. No new Java or
transport behavior is required or supplied.

## Units and transition

Distance is `length(viewPos)`, the originating reconstructed view-space position
in camera-relative Minecraft blocks. Existing screen2view reconstruction remains
unchanged. It is not absolute world position, feet-position length, axial depth
or reflected hit distance. Near clamps to zero; far clamps to at least near +
1 block. One smoothstep spans those values, without temporal random selection
or discrete rings. Generic mod defaults are 32/96; they affect cue bands only
after valid opt-in. Test third-person/view-bobbing behavior before interpreting
camera distance visually. No sampling transition or quality fallback is changed.

## Build and source evidence

After root's exclusive GPU work is terminal:

```
python3 scripts/build_miniature_bridge_probe.py
python3 -m unittest discover -s tests -p test_miniature_bridge_probe.py -v
```

Default ignored output: `build/Miniature-ShaderLOD-CoreProbe-2.19.zip`.
The builder checks both release hashes, version/lock agreement, exact source
hashes and unique anchors, safe/unique archive paths, restored MIT license hash,
exact overlay hash, member collisions and input overwrite. Fixed member order,
timestamps, permissions and compression make output reproducible. It packages
source, attribution, restored license, this contract and build metadata with
runtime validation false. No full derivative ZIP, GLSL compiler or client is
run by this worker; only small fixture packaging and read-only source checks.

Worker evidence: **8 lightweight tests passed**. Fixtures cover exact CRLF
off-path source projection, version boundaries and early unsupported/disabled
status returns, no new samplers, original SSR result dependency, negative/duplicate
anchors, member/archive/overlay/license guards and deterministic small ZIPs.
Read-only inspection applied patches in memory to the actual release and
confirmed exactly four members changed; all other members, SSR function and
budgets are byte-identical. This is source evidence, not driver preprocessing.

## Existing evidence and root checks still needed

The original Miniature NORMAL pack rendered under root smoke session 99413 on
the locked Minecraft1.21.1/NeoForge21.1.253/Iris1.8.12/Sodium0.6.13 minimal stack
without DH. Original 179.21 FPS/GPU4.567ms and the native two-refinement control
182.25/GPU4.474 are a single original-pack pilot, not this diagnostic's result
or a distance optimization. Preserved evidence:
`dev/logs/miniature-original-smoke-r1`; see MINIATURE_RUNTIME_PLAN.md. Miniature
has no DH programs; the original smoke does not establish DH support.

Root should verify original versus derivative with cue zero first, including
identical actual option maps, SSR source and imagery. Then enable cue with core
zero (gray), core one with mod absent (magenta), mod present/disabled (yellow),
and enabled with two distinctly different near/far pairs (green/blue bands).
Change config while retaining pack source/scene and inspect actual consumed
uniform behavior on qualifying pixels, including shoreline water/reflective
bank and a camera sweep. Preserve version/class/jar/options/source/archive
hashes and all failures. A uniform registration log alone is not transport
proof; changing pixels under controlled config supplies the diagnostic evidence.
Unknown-version behavior has source fixtures only until separately exercised
under a controlled runtime; do not mutate shared transport for this task.

Turn cue off afterward and confirm original reflections, shadows, motion/noise
and image return. Successful cue transport does not prove avoided work,
rendering quality, refinement adaptation, pack support or speedup. Any future
SSR adaptation needs an independently reviewed explicit sampling strategy,
off/near/transition quality checks and repeated paired baseline/optimized GPU
timings, preferably with water/reflection-pass attribution. This diagnostic
contains no optimized arm and makes no performance claim.


## Actual core connection diagnostic

Root8fixtures repeated; deterministicprivateZIP SHA256
0855cf136e73ff4773f5609440dfae25e5d8bf59a097810dd5edb1c9145fe97e.
Runner21160 exit0/allfouraccepted, lockedMC1.21.1/NeoForge/Iris/Sodium,
minimal/DHphysicallyabsent, same1488×836shoreline,25s warmup8s capture,
GPUqueriesdisabled. OptionsCORE_PROBE1/CORE_CUE1 identical; only mainmod
presence or existingconfig globalenable/near/far change. Allfallbackfalse,
actualversion/config/source/artifact/capturehashes validated.

RootallfourPNGs inspected: absentmagenta, valid-disabledyellow,
enabled96→160 nearbrightgreen/farblue, changed8→16 substantiallyblue witha
smallforegroundgreenregion. NativeSSRreflection remainsscene-tintedunder
80%cue. These actual pixels prove existingcoreversion2/globalenable/radial
near/far consumption in this isolatedMiniatureconsumer on thishost. No SSR
budget/work reduction, normalimageparity, mod support or speedup follows.
Preset/transition/debug/per-effectfieldsremainunconsumed. Raw
`dev/logs/miniature-core-bridge-cue-r1` retains allinputs/logs/captures/failures.
Unknown1/3version andopt-out have sourcefixtures only. Nextcue0 restoration,
originalfinalpass samplerstate, then a separatequalifiedrefinement experiment
ifworthwhile; never describe thisdiagnostic as an optimization adapter.
