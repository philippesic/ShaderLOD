# Adding an optimization module

Start from measured work only after the separately designed profiler/benchmark workflow is available. The present identity probe validates loading and is not a speedup.

An implementation using the current archive transformation capability lives under `optimizations/<id>/`. Its `provider.json` contains exactly one `class` string naming a public `OptimizationProvider` implementation in `com.pp.shaderlod.providers.<module>`. Java sources go under `java/`, and independent tests under `test/`. Keep the provider, its descriptor and its tests in that directory. Gradle discovers the source roots and generates the ServiceLoader resource consumed by the actual registry; adding/removing a module requires no shared engine, config or service-list edit.

The provider names its stable ID, exact supported archive/member identities, conflicts and preimage-anchored replacements. Unknown input or overlapping edits refuse. Returning no replacements demonstrates identity loading only. Pack metadata and license members remain preserved in derived archives; originals are never modified. Configuration carries IDs, not an expanding enum. Runtime activation additionally needs the matching linked archive and live witness.

Run focused tests while editing, then `./check` for full CPU and packaged-registration verification. Preserve evidence/source reachability before removing a rejected module. New capabilities beyond archive transformations require a deliberate engine/API change and its own acceptance evidence.
