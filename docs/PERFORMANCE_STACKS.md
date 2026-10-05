# Optional performance stacks

Checked 2026-10-04 for Minecraft 1.21.1, NeoForge 21.1.253, Java 21,
Iris 1.8.12, Sodium 0.6.13 and optional DH 3.3.3. The base runtime remains
pinned by `dev/runtime.lock.json`. Iris requires Sodium in this environment;
"without performance mods" means without the additional optimizers below.

## Pinned artifacts and preparation

`dev/performance-mods.lock.json` records exact Modrinth version IDs, published
dates, URLs, sizes, SHA256/SHA512 hashes, project licenses, JAR dependency ranges
and hashes of bundled libraries. All four artifacts were downloaded and verified.
Modrinth metadata and the actual `META-INF/neoforge.mods.toml` agree on this
target. Static compatibility is established; startup, actual in-world rendering
and measured benefits are separate coordinator checks.

| Stack | Additional mods | Purpose |
| --- | --- | --- |
| `minimal` | None | Original Iris/Sodium baseline, with DH independently controlled |
| `optimized` | ModernFix, FerriteCore, ImmediatelyFast | Small general optimizer stack, upstream defaults |
| `optimized-culling` | Optimized plus EntityCulling | Entity-rich compatibility/control experiment |

| Mod | Version | License | Actual JAR requirements |
| --- | --- | --- | --- |
| [ModernFix](https://modrinth.com/mod/modernfix/version/5HLHxQ2F) | 5.27.24+mc1.21.1 | LGPL-3.0-only | MC `[1.21,1.22)`, NeoForge `[21.0.111-beta,)` |
| [FerriteCore](https://modrinth.com/mod/ferrite-core/version/x7kQWVju) | 7.0.3-neoforge | MIT | MC `[1.21.1,1.22)`, NeoForge `[21.1.218,)` |
| [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast/version/OUpXxw4n) | 1.6.14+1.21.1-neoforge | LGPL-3.0-or-later | MC `[1.21,1.21.1]`, NeoForge `[21.0.0,)` |
| [EntityCulling](https://modrinth.com/mod/entityculling/version/8w8FfUPs) | 1.11.2 | tr7zw Protective License | MC `[1.21.1]`, NeoForge `[20.2,)` |

ImmediatelyFast bundles Reflect 1.6.2. EntityCulling bundles TRansition
1.0.25 and TRender 1.0.17 built for 1.21.1 NeoForge; no extra external
dependency downloads are required by these selected Modrinth releases.
These are local development dependencies, not redistributed inside ShaderLOD.
EntityCulling has a restrictive custom license; its project also limits binary
redistribution outside supported modpack services. [Project terms](https://github.com/tr7zw/EntityCulling).

Prepare or verify the cache:

```sh
python3 scripts/setup_performance_mods.py --stack optimized
python3 scripts/setup_performance_mods.py --stack optimized-culling --verify-only
python3 scripts/setup_performance_mods.py --stack optimized --verify-only --print-files
```

The downloader writes only the exact locked artifacts under ignored
`dev/runtime/performance/`. It fails on corrupted cached files and validates
download size plus SHA256 and SHA512 before an atomic replacement. It does not
change a running client, game directory, optimizer configuration or base lock.
It refuses a target version mismatch with the base runtime lock.

Gradle selects only the named stack via `-PperformanceStack=...`; `minimal` is
the default. Runtime inputs are the lock's `stacks[stack]` members, mapped to
`dev/runtime/performance/${artifact.filename}`, never a wildcard containing all
downloaded JARs. The optional JARs belong to `localRuntime`, including the
absent-main-mod development baseline, rather than production dependencies:

```sh
./gradlew runClientLodOff -PperformanceStack=optimized -PrunDirectory=run-workloads/forest/high
./gradlew runClientLodOn -PperformanceStack=optimized -PrunDirectory=run-workloads/forest/final-adaptive
```

Use isolated profiles when switching stacks. A copied game's `mods/` directory
can contaminate a nominal minimal run, and optimizer-generated config files
persist after a mod is removed. Record the actual loaded mod list, effective
settings and configuration fingerprints in each benchmark.

## Expected budgets and compatibility inspection

ModernFix targets loading, memory, bugs, world-generation and assorted CPU
costs. FerriteCore primarily reduces memory use. Neither is a shader sampler
optimization, and a large steady-frame improvement in a warmed vanilla scene
is not assumed. Keep ModernFix's experimental dynamic resources at upstream
defaults; enabling a model-loading redesign would be a different experiment.
[ModernFix description](https://modrinth.com/mod/modernfix),
[FerriteCore description](https://modrinth.com/mod/ferrite-core).

ImmediatelyFast batches immediate rendering for entities, particles, text and
HUDs. Its benefit depends on the CPU work visible in the scene. The selected
JAR has explicit Iris compatibility: it accesses `ImmediateState` fields
present in pinned Iris and clears outline buffers at the end of Iris shadow
rendering. Its plugin enables that compatibility only when Iris is installed.
No DH-specific mixin was found in its listed configs. This narrows the static
risk but does not prove the combination renders correctly.
[ImmediatelyFast description/source](https://github.com/RaphiMC/ImmediatelyFast).

The newest README mentions `fix_slow_buffer_upload_on_apple_gpu`; that option
is absent from this 1.21.1 JAR's configuration classes. Its older
`fast_buffer_upload` option exists. Do not attribute Mac measurements to the
newer option or force an unrecognized config field.

ImmediatelyFast also touches GameRenderer and RenderType/buffer flushing.
Its inspected GameRenderer wrappers retain the original render/HUD calls;
they do not cancel ShaderLOD's outer frame-timing methods. However, added
flushes and replacement buffer sources may change the work covered by the
Iris-specific `FullyBufferedMultiBufferSource` profiling hook. Frame timing
remains the comparison metric; do not interpret a disappearing buffered-draw
scope as eliminated cost. Run low-overhead captures with `cpuLayerTiming=false`;
recheck layer scopes before using them for attribution on the optimized stack.

An [open upstream report](https://github.com/RaphiMC/ImmediatelyFast/issues/512)
describes invisible in-world Iris configuration UI on MC 1.21.1 / Iris 1.8.12 /
Sodium 0.6.13 / ImmediatelyFast 1.6.10 with many additional HUD mods. This is
not an established failure of the selected minimal optimizer stack. Check the
in-world shader settings UI; if reproduced, isolate HUD batching and record
the changed configuration. OptiFine/OptiFabric are explicitly unsupported by
ImmediatelyFast and are excluded.

EntityCulling skips hidden entities/block entities using asynchronous visibility
work. Ordinary terrain blocks, clouds, DH terrain and Photon raymarch steps
are not its target. The current forest/cloud scenes suppress mob spawning;
a gain from EntityCulling there cannot be presumed. Use a separate entity-rich
scene with visible and occluded entities. Check shadow casters and block entities
rendering beyond their block bounds. The selected JAR's listed mixins touch
entity/block-entity render dispatch; no explicit Iris/DH-specific class reference
was found in top-level bytecode. Iris behavior still needs a runtime check.
[EntityCulling description](https://modrinth.com/mod/entityculling).

## Benchmark interpretation

Use identical scenes/settings for original, patched-disabled and active packs
within each stack. Keep the mandatory Iris/Sodium versions constant and vary DH
separately. Compare ShaderLOD's incremental gain inside a stack, then report
the stack's independent gain. Keep a manual-radius and simple lower-preset
control, because smaller vanilla detail radius is an existing DH tradeoff.
Balanced run order and repeated complete JVM termination help expose thermal,
cache, background-process and first-launch variance.

The optional stack's loading and visual checks remain unverified at this
research checkpoint. No performance number or general compatibility claim is
established by artifact hashes or advertised project support.

## Future shader candidate research

Research only; no integration is started before the Photon gate in
`docs/goals/sustained-performance.md`. Popularity figures below are cumulative
Modrinth project downloads retrieved on 2026-10-04, not active installations or
a measure of usage on MC 1.21.1.

| Candidate | Downloads | Integration assessment |
| --- | ---: | --- |
| [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined) | 68,405,781 | Largest candidate here, custom derivative terms need resolution |
| [Complementary Unbound](https://modrinth.com/shader/complementary-unbound) | 44,521,071 | Shares source family; same licensing caution |
| [BSL](https://modrinth.com/shader/bsl-shaders) | 30,053,587 | Modrinth metadata says all rights reserved; no source URL supplied |
| [Bliss](https://modrinth.com/shader/bliss-shader) | 14,904,579 | Public source, but current license file says all rights reserved |
| [MakeUp Ultra Fast](https://modrinth.com/shader/makeup-ultra-fast-shaders) | 13,143,824 | LGPL-3.0-or-later; strongest straightforward derivative candidate |

Recommend examining MakeUp first after the gate: public source, permissive
modification/derivative workflow under LGPL obligations, and substantial usage.
The repository contains DH code, but source presence is not proof of rendering
on the pinned runtime. It already focuses on performance, so remaining LOD
headroom must be measured, not assumed.
[Official source and attribution request](https://github.com/javiergcim/MakeUpUltraFast),
[DH source](https://github.com/javiergcim/MakeUpUltraFast/blob/master/shaders/src/fog_vertex.glsl).

Complementary's current custom grant requires a derivative to look noticeably
different and include significant human-written visual additions, as well as
name/attribution restrictions. That is a poor fit for a transparent performance
patch without a different grant or an appropriate upstream-supported mechanism.
The file explicitly limits its grant to versions carrying that exact license;
review a selected release's own license before deciding.
[Complementary license](https://github.com/ComplementaryDevelopment/ComplementaryReimagined/blob/main/License.txt).
Bliss's published source license is restrictive despite source visibility.
[Bliss license](https://github.com/X0nk/Bliss-Shader/blob/Stable/LICENSE.md).


## 2026-10-05 continuation

Latest authorization permits other-pack research/experimental testing before
Photon's ordinary-scene performance gate. Current exact releases/licenses are
in OTHER_SHADER_RESEARCH.md and dev/shader-candidates.lock.json. Original
MakeUp9.5g and the default-off adapter rendered withoutDH; active pilot regressed
and no support/gain is promoted. See MAKEUP_RUNTIME_RESULTS.md.

The minimal/optimized/optimized-culling original-Photon forest matrix completed
with actual loaded parent/embedded hashes and retained failures/qualification.
No consistent stack gain was established; entity-rich/UI/shadow quality checks
remain narrower outstanding work. See STACK_FOREST_RESULTS.md.
