# Embedded API Base provenance in the stack matrix

Inspected 2026-10-05 by `pack_research`, from the pinned runtime jars and retained
`dev/logs/takeover-stack-forest-r1/` measurement JSON. No client, build, compiler,
shader/source edit, or existing lock mutation was performed. The new
`dev/runtime-artifact-equivalences.json` records a coordinator-authorized narrow
exception; it is not a replacement for raw artifact fingerprints.

The changed `fabric_api_base` hash comes from **two different embedded copies in
Iris and Sodium**. All 17 class files and the manifest/icon are byte-identical.
The only extracted payload difference is the minimum NeoForge version in
`META-INF/neoforge.mods.toml`. Archive timestamps and entry order differ too.
Thus this is a metadata and packaging difference, not an observed API Base code
difference. Same version alone would not have established this result.

## Exact origins

Both parent jars contain:

`META-INF/jarjar/fabric-api-base-0.4.42+d1308ded19.jar`

Their JarJar metadata declares the identical coordinate
`org.sinytra.forgified-fabric-api:fabric-api-base`, artifact version
`0.4.42+d1308ded19`, and allowed range `[0.4.42+d1308ded19,)`.

| Parent | Parent SHA256 | Embedded size / SHA256 | Embedded required NeoForge range |
| --- | --- | --- | --- |
| `dev/runtime/iris-neoforge-1.8.12+mc1.21.1.jar` | `3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83` | 34,807 bytes / `620fa28434a74dfa9e94af33a3e3bf70a939e9a4b0fcae1fbc01ba2c24f32531` | `[21.1.169,)` |
| `dev/runtime/sodium-neoforge-0.6.13+mc1.21.1.jar` | `7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403` | 34,806 bytes / `1aa448a0eff484c7b9f5a6aedbc5050a8d17999f31949dfebb20a7b977f80a07` | `[21.1.115,)` |

Both children advertise `fabric_api_base`, the same implementation version,
Apache-2.0, authors Sinytra/FabricMC, and Minecraft range `[1.21.1,1.22)`.
These are **Forgified Fabric API modules for NeoForge**, not evidence that this
runtime is a Fabric mod implementation. Parent release pins remain in
`dev/runtime.lock.json`.

The pinned EntityCulling parent SHA256 remains
`3983b981da4a0583c7e58184a8631a23e56cc3b81f4cb53491ca600382879b82`.
Its two nested dependencies are already pinned in
`dev/performance-mods.lock.json`:

- `META-INF/jars/TRansition-1.0.25-1.21.1-neoforge-SNAPSHOT.jar`, SHA256
  `c87bc7e6cfd2eee58d472fbf436a228eb3d037565dd4c6c6de0c143a6d662647`.
- `META-INF/jars/TRender-1.0.17-1.21.1-neoforge-SNAPSHOT.jar`, SHA256
  `506484fad73db4bb06a6d51df117062cbf6bace2aca60eee227c10f17b030492`.

Neither nested dependency contains another nested jar or API Base event classes.
Neither is the source of the two reported API Base raw hashes. No newly discovered
performance-mod nested library pin is needed for this finding.

## Extracted content comparison

The children each have 38 ZIP entries, including 20 regular files: 17 `.class`
files, the manifest, the icon and the NeoForge mod descriptor. Entry-name sets
are equal and neither has duplicate entry names. Each extracted payload was
compared by its exact entry name, not position in the archive or CRC alone.

The sole descriptor change is:

```diff
 [[dependencies.fabric_api_base]]
 modId = "neoforge"
 type = "required"
-versionRange = "[21.1.169,)"
+versionRange = "[21.1.115,)"
 ordering = "NONE"
 side = "BOTH"
```

Both descriptors are 748 bytes. Their SHA256 values are respectively
`5f6be55b97d223bc9a95792026a8fecad1215710a9c8e94247ad3fe7e1d9bc9e`
and `249046698cf78d7d6ccdd3d5c8c1e837a728fcfc822d0fedfca2e82abaf36d4b`.
The descriptors become byte-identical when that one exact bound is substituted.

For an independently reproducible content fingerprint, sort exact regular-entry
names and concatenate UTF-8 rows of
`filename + U+0000 + lowercase SHA256(uncompressed payload) + LF`, then SHA256
that concatenation. Directory entries are excluded. Both copies have:

| Selection | Count | Equal canonical SHA256 |
| --- | --- | --- |
| Classes, including generated entry point | 17 | `34704804f9e2834e3114d6029792a5353bd1a09ea9fa814883bb912ca9ace10a` |
| Resources excluding the changed descriptor | 2 | `4afce8cc02b58dc62b4faa2e6a5c8e75ec2bc9c76e348dea42491b0175ea2385` |
| All files excluding the changed descriptor | 19 | `f075c9f89451356a91e59ff0c5a534c8358f3f7885747ad6c79a490cb5aceffa` |

Individual unchanged resources:

- `META-INF/MANIFEST.MF`:
  `77ef0f6728061c80b36d09d02c5af3be49372e44d62ed81d5d154bbfd89ccf70`.
- `assets/fabric-api-base/icon.png`:
  `0b1853217e523298a1a708afb1e9772397c851de93d3b4f5870b48557abf1255`.

All 38 entry timestamps differ: Iris entries are dated 2025-05-04 and Sodium
entries 2025-03-31. Entry order differs. Comparing ZIP metadata by entry name
also finds descriptor compressed size 377 versus 376, changed descriptor CRC,
and 14 changed header offsets. Other inspected per-entry metadata fields and
archive comments agree. Total uncompressed bytes are 50,848 in each archive.
These ZIP timestamps are packaging facts, not independently established source
commit/build dates. An upstream source revision for the two repackagings has not
been established and is unnecessary for the direct payload comparison.

## What the captures demonstrate

At the inspected snapshot, completed reports 001–007 contain these selections:

| Capture | Stack | API Base provider identified by exact raw hash |
| --- | --- | --- |
| 001, block 1 | minimal | Iris copy |
| 002, block 1 | optimized | Iris copy |
| 003, block 1 | optimized-culling | Sodium copy |
| 004, block 2 | optimized | Sodium copy |
| 005, block 2 | optimized-culling | Sodium copy |
| 006, block 2 | minimal | Sodium copy |
| 007, block 3 | optimized-culling | Iris copy |

Iris/Sodium parent raw hashes are unchanged in these reports. This establishes
that selection varies even for stacks without EntityCulling, and that the
culling stack can select either copy. It does not establish why the loader
chooses a particular provider on a given launch. Resolver ordering/determinism
was not traced; do not attribute the choice causally to EntityCulling.

`WorkloadSession` records hashes from NeoForge's actual loaded mod-file paths.
Nested paths serialize as empty strings in these reports but their raw bytes
hash to the identified child archives. These hashes identify the loader-selected
mod file, not post-transform runtime class bytes. The retained `.previous.json`
files can describe earlier workload state; the table uses `-measurement.json`.

## Qualified comparison boundary

Pinned NeoForge **21.1.253** satisfies both dependency lower bounds. Static
inspection proves there is no packaged API Base bytecode/resource difference
apart from a dependency descriptor accepted by this exact loader version.
It is reasonable to treat this exact pair as equivalent **for API Base identity
in comparisons on this pinned runtime**, while retaining raw variants.
That inference does not prove complete application behavior equivalence, exclude
transformation/class-loading differences, or prove performance equality.

The new registry requires these exact loaded versions, not marketing filenames:

- Minecraft `1.21.1`.
- NeoForge `21.1.253`.
- Iris `1.8.12-snapshot+mc1.21.1-local` (the version string inside the pinned
  official 1.8.12 artifact).
- Sodium `0.6.13+mc1.21.1`.
- API Base `0.4.42+d1308ded19`, with only the two recorded raw hashes.

It also requires exact Iris/Sodium parent SHA256 values above and the actual
Minecraft/NeoForge runtime artifact hash
`a3f878f97a229b543a2c872c4a3c03b19ebd3efbd11af2caed9c258c08aebf55`.
The development runtime records Minecraft and NeoForge against that same
`build/moddev/artifacts/neoforge-21.1.253.jar`; this is the observed artifact
identity for this launch setup, not a generic Minecraft distribution hash.

An analyzer should keep strict raw hashing by default. Explicit opt-in may
accept only this exact registry pair when every required version and parent
hash matches, report each run's raw hash/provider, and explain this narrow
exception. Unknown versions/hashes, other changed mods, options, scene or input
failures still reject. Never normalize every jar or waive checks based on a
matching version string. The original strict rejection remains correct and
must be preserved as evidence.

Next practical experiment belongs to the coordinator: after the live matrix
ends, analyze retained captures with and without the qualified exception, then
if needed trace repeated original-pack launches and provider selection before
changing dependency packaging. Do not mutate Iris/Sodium jars to force a match
inside the live matrix. No performance result is accepted by this research alone.
