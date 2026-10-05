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

## Frozen artifact evidence

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

All eight final private archives are assembled deterministically under ignored
`build/`. `build/combined-lod-artifacts.json` retains exact paths/archive/member
hashes; `combined-lod.json` keeps maintained member/helper pins in Git.

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
