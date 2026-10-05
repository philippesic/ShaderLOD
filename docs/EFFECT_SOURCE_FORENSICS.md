# Actual Iris source forensics for the effect sweep

Audited 2026-10-04 after root's CPU-only source capture completed. Source trees
are preserved at `dev/logs/effect-source-forensics/source-dumps/{high,patched-off,alloff}`.
Only this document was written. No shader edits, GLSL compilers, CPU tests or
clients were run during this task; lightweight source/hash comparisons read
complete copied files. The later sparse repeat matrix is owned exclusively by
root and is not modified by this audit.

## What this evidence establishes

These are Iris's actual post-preprocessing/translation source dumps submitted
toward the driver, not option maps or our independent GLSL-flattening fixture.
They prove which source branches/constants/wrappers Iris emitted in this
recorded run. They are not GPU machine code or proof that the driver unrolled,
spilled, predicated or eliminated particular functions.

The preceding nine-arm queried sweep showed original 139.84, bridge-off 140.63,
all-active 130.39 and all-off 77.35 FPS, among its retained observations. The
separate source-capture repeats used 15 seconds warmup/10 capture, GPU queries
false, fixed shader wetness 0 and the same inspected SSR shoreline. Their raw
reports record **139.75 original, 141.99 bridge-off, 140.05 all-off FPS**, with zero
GPU samples. The large all-off loss therefore did not repeat in that run. This
does not prove timer queries caused it; instrumentation, elapsed workload/state,
order and host variation are not isolated by one differing-protocol repeat.

Use original/bridge-off/all-off comparisons from repeated matched protocols
before selecting a regression cause. The source findings below identify small
cleanup/probe opportunities, not an explanation of the prior 77 FPS capture.

## Correct program identities and artifact fingerprints

The numeric dump prefix is an export index, not a Photon pass number. Identity
was checked through actual function contents and, where present, the associated
JSON fragment/vertex names. In particular `003_deferred1.fsh` is the main cloud
rendering pass; `002_deferred.fsh` is its omnidirectional sky-map path. Sodium's
actual terrain water variant is `098_sodium_gbuffers_water.fsh`; the similarly
named vanilla `035_terrain_translucent.fsh` was checked as an additional variant,
not assumed to be the submitted terrain shader. Opaque Sodium variants are 096
and 097. Shadow water is 095. Composite1 is the separate later layer blend, not
an interchangeable synonym for cloud rendering.

Each copied tree contains 270 files. The canonical tree digest is SHA256 of UTF8
JSON mapping each immediate file name to its SHA256, keys sorted, separators
`,`/`:` without whitespace. All files are included, including JSON/properties.

| Tree | Canonical inventory SHA256 |
| --- | --- |
| high | `0e12458de124ee00f6ba1d41b252f867aad8ec80fd53e8107c3553a675987188` |
| patched-off | `338b32af19586072949a7dad0024ae18a9fe2f2ffb6bd20b1552f6365c676556` |
| alloff | `fa15dcf0997979bd4d52c426d1708c52ba0db97de4300baa70ec2497c34d2eb7` |

Selected raw source SHA256 values:

| File | High | Bridge-off | All-off |
| --- | --- | --- | --- |
| 003_deferred1.fsh | `6441fe23c5caebfe3a0002b5b7f573d2208d2ecaa7782b91ce9f74d84e710400` | `afb9264def788aa03b9bdb3a5f36dd1682457fc43e6e90e7dad78fcc13986f3b` | `138311013abfa4bfa5e9af36928eb9a168882e0c512a1c3dce1201b85186f884` |
| 005_deferred3.fsh | `854df1e818fc24de23c3626803881d71bc716c0dedc8b60cb53553065de71b02` | `43b779f1b2ecfa2da3c1f8f91b0d597db22ab53b0693df30a2a434a276cbc095` | `1f022110d7a697511b771da8e747a5ef7628482ff5b2f9dfb526b79254d33d99` |
| 006_deferred4.fsh | `4368a0f585b76fb3c959fc229f4e46cd646aa0b77ca9de7b1dba2d9b009f5e05` | `cfd10f7225b04cce40186426940289091be077de79a073239bc12200c49634b9` | `14e4ad0fb6c508eebbd0652b63b69761c2255ae429d261c7e3b08d05b8cdb0b3` |
| 007_composite.fsh | `18f034cfb535d5b5f20c35839cb07bcd7c3f4890e461ada5636344e7d3548738` | `476bee67a399e1f5121b489069a97092a436253d512256df8f08c2ffcb987444` | `6f50b5e7df4a408c9830816d809a3ebe7edc9a8eb88741f4871e092901cfa718` |
| 098_sodium_gbuffers_water.fsh | `97e90ebe17d4ef69e0ef3a5fc94448ce3ad4e4990d41b585c42bc47878701087` | `16e5ac93e5fec753608dbbf151c7583b173bd37036772a4c822cef04dfe67124` | `6a655519b375af0d0fd695670b7dddd98501f00e3ccd391eb59275876a35e3a9` |

Other byte-identical whole sources across all three captures:

- 008_composite1.fsh: `335c7953fe0f1764eeea4afe3a5e929491e90bc5f3befad746891c032ad53e01`.
- 096_sodium_gbuffers_terrain_solid.fsh: `a722fab3886120d64d155f1eca952366017492336892a428d4163871c339f9cf`.
- 097_sodium_gbuffers_terrain.fsh: `7003ef659fe402d801555854b87162ac86b483c0b63c54dd67cff0de87d7ebd8`.

## Effect-off semantics are present in actual source

All-off's effect getters return literal 0: cloud, cached-sky, water, fog, SSR and
material. Its bridge function checks versions 1/2; enabled reads the live uniform
with pack-option fallback true, and debug reads its live uniform with fallback
false. Nothing in these captured bodies indicates an option-substitution or
include-guard anomaly that accidentally re-enabled an effect budget.

Cloud functions in high and all-off are byte-identical, including all three
volumetric draw functions and secondary optical-depth functions. Lighting and
ambient counts are restored as const declarations; current Cumulus is 6/2,
altocumulus 4/2. Bridge-off differs in source because flags remain on and runtime
enable returns tier 0: it retains modified dynamic counts and compensation code.
Thus compile-time all-off and live-disabled do not submit identical text, even
when their intended settings select full work.

For a stronger comparison, remove only ShaderLOD helper function definitions
and their seven scalar uniform declarations from all-off's deferred/deferred1
text, then collapse whitespace to single spaces. The remaining source is equal
to normalized high for each whole program, and contains no ShaderLOD call.
This is an explicit comparison transform of copies, not a modified runtime
source or proof that the driver's optimizer performs the same elimination.

Fog air/underwater function bodies in 007_composite are byte-identical high versus
all-off. The only whole-file textual difference is the back-depth reconstruction
call: explicit `screen_to_view_space(gbufferProjectionInverse,...)` replaces its
wrapper overload. With DH absent, that overload returns precisely the same
matrix-based helper; this is the earlier DH projection correction, not a fog
budget or a new shader sampler. Composite1 itself is byte-identical.

Sodium water's original gerstner/setup/height/normal/parallax bodies are identical
in high, bridge-off and all-off, with constant 3 loops in height. All-off emits no
cheap water kernels. Its added normal/parallax wrappers directly call those
original functions without quality classification. Its call sites use wrappers;
that is still extra compiler-visible structure compared with original, not a
source byte-identity claim for the entire water program.

The original water function body hashes, equal in all three:

| Function | SHA256 of exact translated function text |
| --- | --- |
| get_water_height | `9cfb4083791a0e7c9774026006e6837cfb6e9ccb1aaa2117b952e640bca55438` |
| get_water_normal | `2fcd45e7ba261037e6fe5140ef3462125d3f929e2c28d5c417358c0758a476a7` |
| get_water_parallax_coord | `26187b0d3c2237929dd4080f37a24264e097905c13c2d7011148976b855d8f40` |

All-off's `trace_specular_ray` matches high exactly in both deferred4 and Sodium
water (hash `99e035184133ce5880fd48f622752abca5a9075795343bd407a6ad77dcc14930`).
Bridge-off retains the SSR-tier branch and capped trace/refinement arguments
(hash `da742466eae434cf7c6fbe6534f3bb3f1b05e2d22dd5d1489f0b834702c914ea`).
The smooth trace is therefore actually restored. Rough multi-ray code is not
emitted in this narrow profile because SPECULAR_MAPPING=false; SSR_RAY_COUNT 4
alone does not make that path active. Full rough/material support remains a
separate configuration/driver check.

Declared sampler names/types/order match all three in the inspected programs:
6 in deferred/deferred1/ao, 16 in deferred4, 10 in fog, 13 in composite1, 10 in Sodium
water. These are declarations, not linked active-unit counts; optimizer dead-code
elimination can change actual active units. The helper adds seven scalar uniforms
but no sampler. This audit does not measure uniform upload overhead or driver
resource allocation.

## Remaining lighting/debug source differences

The five effect flags intentionally do not restore lighting/debug source.
Although all-off's `shaderlod_lighting_tier` literally returns0, its shader text
still includes these modified structures:

- Deferred3 GTAO computes generic quality, calls the constant-zero lighting
  helper, then uses dynamic slice_count/horizon_steps. Original source has
  literal 2 slices and 3 horizon steps. Its horizon helper signature also retains
  the new runtime step argument. This is source-constant restoration opportunity;
  the driver may already fold it to original work.
- Deferred4 and Sodium water shadow_pcf retain the quality argument and
  `initial_steps=min(4,step_count)` instead of original literal 4 first taps;
  an offset branch handles one-tap legacy quality. Protected quality should
  preserve original counts, but source/code shape is not original. The actual
  full step formula here starts at least 7 (6+filter_scale with filter_scale≥1),
  so protected initial_steps is mathematically 4, not an intended four→two cut.
- Deferred4 raymarch_shadow retains shaderlod_budget around original 10 SSRT
  steps and nonconst derived setup variables despite the constant-zero lighting
  tier. Do not confuse the unchanged outer get_screen_space_shadows wrapper with
  this modified inner raymarch.
- Deferred4's main computes generic distance quality unconditionally for terrain
  around all-off source line 2752, then uses it for protected shadow calls and a
  later runtime debug predicate. Sodium water passes a generic quality expression
  into its protected shadow call. Debug false is a runtime uniform, not a
  compile-time deletion of these calculations. The compiler may sink/eliminate
  unused work, but no driver instruction evidence was collected.

The clean artifact omits the structural atmosphere guard; captured all-off's
atmosphere calculation remains the original placement. It is not the retained
all-off difference in this source set. Shader code shape alone does not explain
why all-off 77 happened once and all-off 140 then repeated.

## Small next probe after the sparse matrix

Treat a protected-lighting source cleanup as a **small measured candidate**,
not a causal fix for the 77 FPS observation:

1. In an isolated archive, when SHADERLOD_PRESERVE_LIGHTING=true, select pinned
   original PCF/SSRT/GTAO/SSAO bodies and signatures/constant loops. Keep legacy
   lighting LOD available only in its explicit false branch. Restore matching
   call-site arguments; do not strip surface shadows or AO.
2. Separately move generic debug-only classification behind the existing runtime
   debug-enabled predicate when protected lighting has no other consumer. Keep
   live debug switching functional. Do not combine this with cloud sampling,
   SSR budgets or water changes before measuring the lighting-only arm.
3. Verify actual post-Iris sources again, then balanced serial all-off versus
   protected-original-source arms under the same query cadence. Include original
   absent-mod and live-disabled controls. If a small repeatable delta remains,
   attribute only that observed delta; no claim of recovering the old 77→140 gap.
4. Compare instrumentation schedules in a separate matched experiment. Current
   CPU-only source capture cannot establish a timer cause because it differs
   from the earlier queried sweep in protocol and runtime state.

No new toggles, shader patches or compile/runtime tests were introduced here.
Effect-specific inference waits for root's sparse repetitions and subsequent
source-equivalent controls. The ordinary performance gate remains unmet.
