# Dependencies and pins

Every tool and runtime input is pinned by digest. `./check` and `./verify` bootstrap them into user-local caches under `~/.cache/shaderlod-foundation` and verify each hash before use. First acquisition needs network; later runs reuse the caches.

| File | Pins |
| --- | --- |
| `dependencies/check-tools.lock.json` | Gradle 9.2.1 distribution and official wrapper JAR, Temurin 21.0.13+9, checker tool versions |
| `dependencies/python.lock` | Hashed Python wheels for Ruff, mypy and pytest |
| `dependencies/greclipse.lock.json` | Groovy-Eclipse 4.39 bundle JARs and nested JARs used by the Gradle formatter |
| `dependencies/runtime.lock.json` | Minecraft 1.21.1, NeoForge 21.1.253, Iris, Sodium and Distant Horizons JARs |
| `dependencies/linux-arm64-runtime.lock.json` | Linux ARM64 native libraries for the game client |
| `dependencies/primary-pack.json` | Complementary Reimagined r5.9.3 archive, its settings preset and DH expectation |
| `gradle.lockfile`, `buildscript-gradle.lockfile` | Resolved Maven dependency graph |

Downloads go to a staging file and are renamed into place only after the hash matches. Toolchain auto-download is disabled; the JDK comes only from the pinned archive.

Known tool constraints:

- Spotless runs google-java-format 2.0.0. Version 1.37.0 fails with `InvocationTargetException` on this toolchain.
- JUnit uses its API and engine artifacts directly, because the dependency analysis rejects the unused aggregator.
- The Gradle wrapper JAR is the official 9.2.1 artifact, verified against services.gradle.org.

## When a pin no longer matches

A `TOOLS:` checksum or version error means the bytes on disk or at the URL differ from the lock. Do not edit the hash, version or lock to make it pass. Stop and report the mismatching artifact, the expected and observed digests and the URL. Updating a pin is a task the user approves; it changes the runtime identity, and earlier live findings no longer apply to the new build.

A `TOOLS: incomplete JDK` error names an interrupted installation directory. That directory is owned by bootstrap and can be removed so the next run reinstalls it.

Changing Iris, Sodium, DH, NeoForge or Minecraft versions also requires re-checking the mixin selectors in [Iris integration](iris-integration.md); a non-matching selector fails startup.
