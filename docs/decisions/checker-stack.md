> Historical foundation decision. Current scope and any superseded mechanisms are governed by [the approved porting plan](../PORTING_PLAN.md) and [policy changes](../../checks/policy-changes.md).

# Checker compatibility

Gradle 9.2.1, ModDev 2.0.141 and Temurin 21.0.13+9 run the pinned checker stack on Linux ARM64. Spotless 8.11.0 with google-java-format 1.37.0 failed with InvocationTargetException. The approved alternative 2.0.0 passes without suppression. JUnit uses direct API and engine dependencies after dependency-analysis rejected the unused aggregator. No broad suppressions are accepted.

Groovy-Eclipse 4.39 resolves 37 Eclipse/P2 bundle JARs plus six nested JARs outside Maven. Their exact digests and acquisition locations are in dependencies/greclipse.lock.json; bootstrap verifies each before Gradle can load it. Foojay 1.0.0 remains configured, with automatic toolchain downloads disabled. The JDK archive bootstrap selects the exact approved build. A task-graph assertion fails S1 when a required validator is disabled or missing.
