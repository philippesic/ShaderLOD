# ShaderLOD foundation

NeoForge 21.1.253 / Minecraft 1.21.1 foundation. No mod classes, launches, benchmarks or optimizations are present. The four future targets are MakeUp 9.5g, BSL 10.1.8, Complementary Reimagined r5.9.3 and Unbound r5.9.3. Distant Horizons 3.3.3 is optional at the later runtime stage.

Install Python 3.13 and Git, then run `./check`. It verifies the wrapper and installs digest-pinned Temurin 21.0.13+9 and hashed Python wheels into user-local caches. Nothing changes system Java. Gradle 9.2.1 verifies dependencies against the committed metadata and locks. Network access is needed on first setup. To enable the hook in a clone, run `git config core.hooksPath .githooks`.

`./check` owns read-only formatting, Java compilation and lint, Checkstyle, dependency analysis, JUnit/ArchUnit, Python formatting/lint/type/tests, repository policies and their self-tests. It invokes `./gradlew --no-daemon check`. Check reports go in ignored `build/reports/foundation/`. A failed tool, missing test or skipped suite fails the check.

`./verify --cpu` runs the synthetic contract round trip. `./verify --runtime` exits 2 until stage 4. Neither compilation nor CPU verification demonstrates live behavior. There is no benchmark entry in this stage; one runner is reserved for stage 4.

[System map](docs/SYSTEM_MAP.md), [checks](docs/checks.md), [evidence levels](docs/evidence-levels.md), [decisions](docs/decisions/acceptance.md), [findings](FINDINGS.md).

Hosted CI, x86 execution and remote review enforcement are untested. This repo has no remote. CODEOWNERS routes protected changes for Philip's review; no branch protection is installed locally.
