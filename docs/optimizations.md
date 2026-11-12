# Optimization modules

Follow [optimization direction](optimization-philosophy.md) and the [optimize skill](../skills/optimize/SKILL.md) before choosing a mechanism or claiming support.

No optimization exists yet. Start one only from measured work, which needs the profiler and benchmark workflow in [benchmark workflows](benchmarks.md). The development identity provider proves loading, not a speedup.

## Layout

A module lives in `optimizations/<id>/`:

- `provider.json` holds exactly one `class` string naming a public `OptimizationProvider` implementation in `com.pp.shaderlod.providers.<module>`.
- `java/` holds its sources, `test/` its tests.

Gradle discovers module source roots and generates the ServiceLoader registration the registry consumes. Adding or removing a module needs no edit to the engine, config or a service list. The package audit fails if the packaged registration disagrees with the module directories. Provider bytecode may depend only on `domain` and `engine` contracts, standard Java and its own module; ArchUnit rejects shortcuts into `integration` or `storage` and coupling between modules.

## Contract

A provider declares its stable ID, the exact archive and member identities it supports, its conflicts and its replacements, each anchored to the bytes it replaces. Unknown input and overlapping edits refuse with a reason. Derived archives keep pack metadata and license members; originals are never modified. Configuration stores IDs and parameters. Activation still needs the linked archive and a live frame witness ([Iris integration](iris-integration.md)).

The current capability is archive transformation. A mechanism that needs something else, such as a runtime hook, needs a deliberate engine change with its own tests.

## Removing a rejected module

Record the result in `FINDINGS.md` with the commit that still contains the module, then delete the module directory. Keep regression tests for any shared bug it exposed.
