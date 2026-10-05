# Explicit combined Photon LOD candidate

Source integration only. No combined driver compilation, linked uniform values,
quality acceptance or performance improvement has been established.

`scripts/build_photon_combined_lod.py` assembles exactly the reviewed reflection,
procedural water and primary volumetric tracks from original Photon v1.3b. Lighting
rate remains excluded until its source is frozen and the orchestrator chooses an
explicit fourth integration. This is a private experiment rather than a new shader
pack support claim or a generic shader rewriting API.

The three standalone builders each qualify their pinned changes independently
against the complete original archive. The combined builder refuses derivative
archives, changed original inventories, changed standalone manifests/builders,
changed helpers, unknown feature names, duplicated controls and output outside
ignored `build/`. No change to upstream noise assets, properties, samplers, render
targets, pass topology or original LICENSE is permitted.

The four changed original members are `settings.glsl`, specular lighting, the
shared translucent fragment program and cumulus clouds. Settings merges the three
reviewed declarations at the single exact `SETTINGS_INCLUDED` anchor. Each private
feature gate defaults off. Enable each explicitly, independently:

```sh
python3 scripts/build_photon_combined_lod.py
python3 scripts/build_photon_combined_lod.py --enable reflection
python3 scripts/build_photon_combined_lod.py --enable procedural --enable volumetric
python3 scripts/build_photon_combined_lod.py --enable reflection --enable procedural --enable volumetric
python3 -m unittest discover -s tests -p 'test_photon_combined_lod.py' -v
```

The builder copies all six standalone helper payloads. Three helpers consume
overlapping core uniforms in the same program. Only their exact reviewed declaration
blocks are replaced with one generated shared header; separate guards for the
two basic fields and three distance-policy fields preserve each isolated feature's
preprocessed tokens. This prevents duplicate globals regardless of include order.
The generated guards use `#if !defined`, preserving Iris option discovery without
turning an internal include guard into a user option. Helper calculations and
fallback logic remain the standalone implementation's exact bytes elsewhere.

Core version 2/global exactly 1 remains required. Volumetrics accepts effects
versions 1 or 2 and consumes the existing Volumetrics fields. Reflection and water
retain their existing core field consumption. A Java config or active source
uniform is not proof that a linked program received its expected value.

The fixture acceptance covers the full 531-member original inventory and original
license, byte-exact original restoration when all private guards are off, eight
independent gate combinations, deterministic ZIP/member hashes, fail-closed changed
inputs, full-source pinned Iris lexer boolean/string option union and independent
overrides, and C-preprocessed original/isolated token parity. C preprocessing removes
GLSL-only version/extension directives and resolves the pack's absolute include
paths in temporary fixtures; it does not compile GLSL. The fixture enables DOF to
exercise its otherwise disabled entry. All three original `composite3.fsh` entries
reach a dormant upstream TAAU source with an unmatched trailing `#endif`; the
fixtures retain and assert that original error instead of calling it a successful
preprocess result. These unmodified dormant entries are distinct from the 329 valid
entrypoint comparisons.

The runtime operator owns actual compilation, config linkage/reload, sampler state,
DH/noDH, near/far transitions, motion and visual review. Water can alter normals,
therefore also reflection rays and glints; the prior scalar water fixture measured
up to 22.115 degrees normal difference in its tested envelope, not perceptual
acceptance. Volume quadrature changes variable-density samples and early exits;
constant-medium math does not prove cloud quality. Reflection projection/cache
bookkeeping may cost more than the saved depth fetches. The combined stack can have
nonlinear interactions, register pressure or divergent branches. Individual
cost estimates must never be summed into a combined gain.

Only after untimed driver/quality acceptance should the runtime owner measure
balanced, quiet, matched fullscreen original/disabled/each feature/combined stacks,
with repeated total-frame and GPU intervals, actual artifact/scene/option/config
fingerprints, and query-free corroboration. A source freeze does not meet the 50%
throughput goal.

## Historical r1 artifact evidence (superseded)

Procedural r2 source freeze replaces the original helper include guards with
equivalent `#if !defined` guards. The combined assembler takes this corrected
standalone source directly; it makes no extra guard edits. Frozen manifest SHA256:
`238da80f9bf998fe12ba857ced929c2b79129fed895c95e51a569141977350fc`.
Builder SHA256:
`95dd7512646a141997ba9178a89c579d7e75fca0bdb20a47af82908d8050d538`.
The manifest records all three standalone builder/manifest hashes, complete
original inventory hash and all 11 changed/added member hashes. Original license
SHA256 remains `514402d18eedb0afe374344ca9f0fe87b7399779518184b8fc64e31674555fc5`.

Seven fixtures passed, zero failures/errors/skips, in 50.990 seconds:
`python3 -m unittest discover -s tests -p 'test_photon_combined_lod.py' -v`.
Log: ignored `build/combined-tests.log`. Coverage includes byte restoration of all
531 original members; 329 valid entrypoints OFF/original, each of three isolated
features against its standalone over all 329 entrypoints, and combined all-on
preprocessing with at most one declaration of each shared core uniform per program.
The original and combined dormant TAAU errors are asserted separately for the
remaining three entries. Fixtures use `clang -E -P -x c`, MC_VERSION=12101,
IS_IRIS, IRIS_FEATURE_SSBO and DOF; these defines exercise source routes rather
than describe a measured client profile. Pinned Iris actual lexer covers every
GLSL/fragment/vertex/geometry/compute source member, including every helper;
boolean references and options add exactly the three public gates, string options
are identical, and each gate overrides independently. Iris may add another `//`
when writing an already-disabled define; the fixture verifies disabled semantics
without assuming identical comment spelling.

These eight r1 private archives, their artifact ledger and their test log are
retained under ignored `build/photon-combined-lod-r1/`. The hashes below describe
that historical freeze. They are superseded by the corrected freeze below.

| Enabled tracks (suffix after `Photon-CombinedLOD-`) | Archive SHA256 |
| --- | --- |
| disabled | b6eb562df5ecdae8521e8aa7dbe0ba395e53f28e55466f0eabfeb11ce70a9df9 |
| reflection | eb3b5da5a7e321f1649ae6201cbb9f137a4890b079e01c439029bb397392bc61 |
| procedural | 20ae93a4195a922fd35066871f0f23f7f7953760bb80d9df3b25493b23d28986 |
| volumetric | 705fa8b8d92ebd75fe57ac3c40f5692017d896b3a329eeef6e75d06304f32fb2 |
| reflection-procedural | aff48686db5937f334d0ecb9cfbd30a56478517a4f6b95baf091aa56b9d7046a |
| reflection-volumetric | c8704dbf636a05a81ab7639372aeef7dc4cd180df0127236fb220920eedea212 |
| procedural-volumetric | 78ea7b1ee709c57b4e34b898b8fa29b73ed6bd7bd16b9938338831e523a991f6 |
| reflection-procedural-volumetric | 6114d170135735b78894379c957083e9ee61c134661c40b3808f1708ab3909bd |

Shared generated header SHA256:
`2af74bf4e5c69df359ae1fb14acd5415bcf885cd25481641abb47671d3c4f186`.
All source hashes are assembly evidence; combined runtime compilation, linkage,
quality, motion, reload, DH compatibility and measured performance remain pending.

## Corrected combined r2 freeze

The r1 source and hashes above are superseded. The actual standalone procedural
r2 client rejected a missing parenthesis in `procedural_water.glsl` under Iris's
GLSL transformer, falling back to vanilla rendering. That rejection is retained
in `dev/logs/parallel-procedural-qa-r1` and `PARALLEL_LOD_RUNTIME_QUEUE.md`.
The previous option lexer and C-preprocessor checks did not validate full GLSL
grammar. No driver acceptance follows from those historical checks.

Root integrated procedural repair commit `6eb12b1975e3b9ef8d7c61cdf009df868a4f5144`
and reflection guard repair commit `8c27393`. This combined freeze takes both
approved source revisions. Procedural adds exactly the missing parenthesis without
changing arithmetic. Reflection reduces guard overhead while preserving its
bounded refinement contract. Volumetric sources are unchanged; lighting remains
excluded. Prior reflection/volume driver observations apply to their exact older
archives; they do not establish compilation of this combined archive or the
revised reflection helper.

Eight fixtures passed with zero failures/errors/skips in 82.515 seconds:

```sh
python3 -m unittest discover -s tests -p 'test_photon_combined_lod.py' -v
```

Log: ignored `build/combined-tests-r2.log`. All previous complete-inventory,
literal-off, deterministic combination, actual option-lexer and exhaustive C token
parity checks passed again. The new fixture expands each complete literal include
graph, applies options with actual Iris `OptionAnnotatedSource`, preprocesses using
actual `JcppProcessor`, and parses using the pinned `EnhancedParser` with thrown
parse errors enabled. It covers all 43 entrypoints reaching any of the three changed
program members across Overworld, Nether and End: 43 original graphs, 344 combined
graphs for all eight independent feature subsets and 24 additional water graphs
with parallax off. All 411 complete parses succeed. Three deliberately restored
old malformed-helper cases reproduce `missing RPAREN at '{'`. The separate three
unchanged dormant TAAU preprocessing errors remain explicitly asserted.

The grammar fixture uses the held coastal options with BORDER_FOG, CLOUD_SHADOWS,
SHADOW_COLOR, SHADOW_VPS and SH_SKYLIGHT false; MC_VERSION=12101, MC_GL_VERSION=410,
MC_GLSL_VERSION=410 and IS_IRIS=1. It verifies the pinned Iris archive and the embedded
glsl-transformer 2.0.1 archive/class plus ANTLR 4.13.1 hashes in the corrected
procedural manifest. This is full grammar acceptance for those source routes,
not execution of `TransformPatcher`, GL shader compilation, linked controls or
rendering. No client/GPU process was started by this integration worker.

Current combined manifest SHA256:
`dcbb31d5878387bd00bdb048703be9b6c22539b30844ee8054dcc455a533cf19`.
Combined builder is unchanged:
`95dd7512646a141997ba9178a89c579d7e75fca0bdb20a47af82908d8050d538`.
Fixture SHA256:
`bee3f7f4fde187425a56de398f6a75d90e6caeb24aa0355cb2c2a413e1de7edd`.
Track manifest pins are reflection
`0c0905612ddacf358b92c2d112bb17f2d107de224551d418a6a67c8529ccf4ec`,
procedural `b529fdb01aabef5670128e04a25224bfd4368c2a63641214023c352099d100e2`,
and unchanged volumetric
`08e1611a77a2f6d6364b91eda692ea2e300b1c3c2891c87d7abd6ecec98def9b`.

The changed copied helper pins are reflection
`01e19f3a72b39322dcf9501d206cdc54dd3ac5bf0f77ad4322078aa2c7030e3c`
and procedural water
`49ed8b4b7522c700a007cec737b37b08e10cb3e8790beef6bdaf72d5531f95dc`.
All other combined member/helper/license pins remain those in the maintained
manifest. Original LICENSE bytes/hash and generated shared core header are unchanged.

Final archives live at `build/Photon-CombinedLOD-<suffix>.zip`. The ignored
`build/combined-lod-artifacts-r2.json` and current `build/combined-lod-artifacts.json`
retain every archive hash, member/helper digest and full canonical metadata object
for downstream independent qualification. Archive metadata remains structurally
identical to r1; manifest/source hashes change and all runtime claims remain
`unverified`/`unmeasured`. The previous r1 archives/ledger/log are preserved separately.

| Suffix | Corrected archive SHA256 |
| --- | --- |
| disabled | 87e12ac10401cf59c36d76eaabd1b7fdb9b8dc66365dfebdcfa2c7e7a329e41a |
| reflection | e1c8ccf781b23aa737e31d04dd5b50e11a66b62f6b55c6b3a664efee3aee2437 |
| procedural | 00caf32e515cfde057cbd076cdb57a6d43650571cb18d8b9945e408bfc6788db |
| volumetric | 2b40c617439f6209d827a06f03a984deb9454fea549f3fe51046f86ec4493fa4 |
| reflection-procedural | fc27f968b53c9e8ecaa29dc92e4a95be61dd3b5733332e3691df2164c1abaac6 |
| reflection-volumetric | 4b1e263da43a5cfe2bd754d7cc75b47d4bddd10c3280aab05a08728ed953a76b |
| procedural-volumetric | 8ffd853c0fa92c6e15715c41527edc422e5b926556c81d04b6e24e41168347d1 |
| reflection-procedural-volumetric | 3a482b05827a68eaeb3df95b60c4fd76a7c5feb230c0f5bc0e0d6aebfedc2884 |

Runtime operator must retry actual driver compilation and inspect interactions,
linked controls, sampler state, quality, motion/reload and DH/noDH compatibility
before any quiet fullscreen timing or performance claim. The 50% goal is unchanged.
