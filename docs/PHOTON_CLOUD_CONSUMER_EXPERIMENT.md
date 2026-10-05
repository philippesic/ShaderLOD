# Photon composite1 cloud-consumer experiment

Static checkpoint: 2026-10-05. Owner: fresh performance chat's bounded
`photon_cloud_guard` agent. This is candidate 1 from
[PERFORMANCE_METHOD_RESEARCH.md](PERFORMANCE_METHOD_RESEARCH.md), implemented as
a separate original-upstream archive recipe. Ten lightweight structural tests
passed. No pack ZIP was built, no compiler or client ran, and no runtime,
visual-parity or performance evidence exists for this experiment yet. The root
coordinator owns its next driver compilation, shader reload and GPU measurements.

Only new experiment files were written. Existing Photon patches, build tooling,
shared helpers, Java configuration and protocol transport remain owned by the
original chat. This recipe deliberately consumes the immutable original Photon
archive, without the current ShaderLOD overlays, so its effect can be isolated.
It establishes no new distance LOD policy or connection to mod configuration.

## Source and entry points

Pinned upstream: Photon v1.3b by Benjamin Stott / SixthSurge,
`dev/upstream/photon_v1.3b.zip`, SHA-256
`120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead`.
The full archive hash is verified before reading or writing the experiment.

The only modified upstream member is
`shaders/program/c1_blend_layers.fsh`. It is included by
`shaders/world0/composite1.fsh`, `shaders/world1/composite1.fsh` and
`shaders/world-1/composite1.fsh`. Only its Overworld cloud compositing block
changes. The upstream attribution in this member and every other upstream file,
including `LICENSE`, are retained. The Photon license is separate from the mod
license; its archive bytes have SHA-256
`514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.
No distribution or publication was performed.

Reviewed anchors are in
[cloud-consumer-guard.json](../patches/photon-experiments/cloud-consumer-guard.json).
Two literal replacements insert a default-off compile-time option and wrap the
single cloud consumer with a guarded alternative and the exact original block.
The builder accepts this fixed member and pinned revision only, verifies unique
anchors and hashes all three output-member forms. It imports neither the existing
Photon patch engine nor any overlay or configuration helpers.

| Arm | composite1 member SHA-256 |
| --- | --- |
| Original source | `9a22e67280a57927c9dc8ca7ef194abb64b636de6a736651aa5b743035052cad` |
| Experiment disabled | `f920ae650454162eaf951ebd0bcb70e7474e630f37d4712bfbf05f31b3b2d857` |
| Experiment enabled | `04ce05a2d053a6f8049dd2f08a2a0f761e67044ea59a45bfa3c0ce2373ce591d` |

## Consumer behavior and baseline

The original composite1 block calls `read_clouds_and_aurora(refracted_uv, ...)`
before checking `is_translucent || is_translucent_lod`. Its color is consumed only
inside that predicate and only when `clouds_apparent_distance < view_distance`.
The active arm places pixel-age `texelFetch`, filter-age arithmetic and cloud
color `textureLod` inside both existing predicates, directly before composition.
Vanilla and DH/Voxy translucency classification, refraction coordinates, alpha,
lightning intensity and blindness/darkness multiplication are preserved.

The apparent-distance `textureGather` remains at the outer Overworld scope.
The implicit-LOD lightning `texture(colortex12, ...)` stays at the same outer
scope under the original `LIGHTNING_FLASH_UNIFORM > 0.01` condition. Only its
result is consumed in the pixel-varying branch. No derivatives or explicit
gradients were invented, no early return/discard was introduced, and no
implicit-LOD texture operation was moved behind the translucency predicate.
The new branch contains only explicit-LOD/fetch operations and arithmetic.

`include/sky/clouds/sampling.glsl` remains byte-identical. Its original helper
still serves deferred terrain/sky, including rainbow behavior. Cloud generation,
temporal upscaling, history writes, near lighting, cloud marching, fog, AO,
shadow and water budgets are untouched. The old helper remains included in
composite1; driver dead-function/read elimination is not assumed or measured.

Distance continues in the original camera/view block units. Upstream
`program/d1_clouds.fsh` scales terrain distance with `CLOUDS_SCALE`, then stores
`result.apparent_distance * rcp(CLOUDS_SCALE)` in cloud data. Composite1 compares
the gathered/upscaled apparent distance to its existing `view_distance`; this
recipe preserves that conversion and comparison exactly. There is no new near,
far, transition or hysteresis boundary. Existing temporal filtering and
disocclusion behavior are retained; moving water/glass/cloud edges still need
runtime inspection.

The independent pack option is `PHOTON_CLOUD_CONSUMER_GUARD`, default off. It is
a compile-time shader control; no Java setting or live mod bridge controls it.
With the option undefined, the original cloud block is selected. The fixture
removes only experiment conditionals/declaration and recovers the original
member byte-for-byte; this is a structural check, not compiler evidence. The
`original` arm additionally leaves every upstream member's bytes untouched.
Each arm adds only its experiment provenance JSON outside the shader sources.
Disabled/original is the quality fallback until compilation, parity and repeated
GPU evidence justify any promotion. No effect quality tier is reduced.

## Build and static checks

After the root operator's current runtime matrix ends, build separate arms:

```sh
python3 scripts/build_photon_cloud_guard.py --arm original
python3 scripts/build_photon_cloud_guard.py --arm disabled
python3 scripts/build_photon_cloud_guard.py --arm enabled
```

Default outputs are ignored `build/Photon-CloudConsumerGuard-{arm}.zip`.
The builder rejects output outside ignored `build/`, unsafe/duplicate ZIP member
names, changed source/archive/license hashes, absent or ambiguous anchors, and
unexpected patched-member hashes. It cannot overwrite the upstream archive.
Fixed ordering, timestamps and permissions make pack builds reproducible.
Record whole ZIP hashes after building; none are recorded here because the
assigned stopping boundary prohibited pack builds during the live GPU matrix.

Executed static command:

```sh
python3 -m unittest discover -s tests -p 'test_photon_cloud_guard.py' -v
```

Result: **10 tests passed**, including real pinned-source/hash/attribution checks,
the exact disabled fallback, identical on/off declaration delta, consumer and
texture placement, filter/compositing expressions, corruption/ambiguity guards,
archive safety and ignored-output restrictions. These tests do not launch a
compiler, assemble a full pack, copy a world, or open a client.

## Runtime acceptance and needed GPU measurement

The root operator must first build/hash the arms and establish actual Iris
driver compilation/rendering with the exact locked Minecraft 1.21.1 / NeoForge
21.1.253 / Iris 1.8.12 / Sodium 0.6.13 runtime, Photon v1.3b and optional DH
3.3.3. Existing rendering evidence for other Photon archives does not establish
success for this source. Confirm effective switch state after reload; retain
compile failures and full options/artifact fingerprints.

Quality captures need ordinary water, glass, DH water/vanilla-DH overlap, clouds
in front of translucent terrain, lightning, blindness/darkness, motion,
disocclusion and reload/history reset. Include DH absent/present and Nether/End
unchanged paths. Use normal advancing temporal inputs; fixed wetness/time
diagnostics, if used, are separate from moving-world quality evidence.

Then compare original, disabled and enabled with a balanced repeated serial
pilot on forest and coastalwater, one GPU client, equal resolution/pacing,
unchanged near/effect budgets and terrain radius, warmed history/chunks,
matching weather and actual applied options. Measure affected composite1 GPU
elapsed time and whole-render GPU elapsed time, plus frame/CPU limits, using
the existing delayed nonblocking query path. Do not sum overlapping scopes.
These source changes can at most avoid two logical texture operations plus age
arithmetic on nonconsumer pixels; the compiler may already sink them or branch
divergence may offset any saving. No issued-operation count or speedup is known.

Acceptance requires real compilation/rendering, preserved visible compositing
and repeated affected-pass/whole-render measurements. A flat qualified pilot
ends this optimization hypothesis; retain its negative evidence. This
distance-independent dispatch experiment alone cannot pass the project's
ordinary-scene shader-LOD performance gate.
