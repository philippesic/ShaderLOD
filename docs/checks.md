# Checks

`./check` verifies the pinned tools and invokes `./gradlew --no-daemon check`. Gradle owns every lane and asserts that every required validator is present and enabled in the check graph. Formatting is read-only against authored files; formatter controls are generated in disposable directories. Java compiler tasks enable Error Prone explicitly, including tests. No source exclusions, ignored failures or blanket suppressions are configured. Test-only relaxations are disabled. Default Error Prone checks and javac warnings remain enabled; UnusedVariable is promoted to error. Checkstyle enables UnusedImports; Ruff enables F401; mypy runs strict.

| ID | Validator and invariant |
|---|---|
| D1 | Receipt contract rejects synthetic/live relabeling; runtime refuses with exit 2 |
| D2 | Convention descriptor discovery, duplicate/engine branch check, bytecode provider isolation |
| D3 | Bytecode and Python writer ownership; exclusive publisher refuses a second finalization |
| D4 | Checkstyle unused imports, dependency-analysis unused libraries, manifest-rooted reachability |
| D5 | Measured positive-count sample or missing with reason; closed health/evidence enums |
| D6a | Broad catches require an exact listed failure adapter |
| D6b | Bytecode/Python settings reads confined to the config owner |
| D6c | Bytecode/Python timer, fence and finish calls confined to measurement adapters |
| D6d | Python launch/option parsing confined to declared check entry files; future runner absent |
| D7 | Exact exception sites, reasons, review owner, decision, expiry and protected routing |
| D8 | Token scans reject comments/docstrings and unlisted directives, including Unicode Java escapes |
| D9 | System-map entries, symbols and test paths exist |
| D10 | Resolved corrections name an existing rule/test or explain uncheckability |
| S1 | CI and hook invoke ./check; disabled checks/source exclusions fail |
| S2 | Complete specimen manifest and required test inventory; crashes, skips and zero tests fail |
| S3 | Root-only instructions, AGENTS under 2048 bytes, exact CLAUDE mirror |
| S4 | Tracked/index plus nonignored authored file audit rejects raw outputs, renamed capture signatures, binaries and plans |
| S5 | One FINDINGS with seven scoped frozen-evidence rows, no STATUS successors |
| S6 | GPU lock/lease/semaphore artifacts and allocation APIs refused |
| T-JFMT / T-GFMT / T-PFMT | Pinned Java, Gradle Groovy and Python format checks |
| T-JTYPE / T-EP / T-CS | javac, Error Prone and Checkstyle deliberate failures |
| T-RUFF / T-MYPY | Python unused import and type failure |
| T-JUNIT / T-PYTEST | Deliberate assertion failure, passing control and required discovery |
| T-DEP / T-ARCH | Dependency-analysis and compiled boundary failures |

`checks/self-tests.json` is the exhaustive rule-to-specimen mapping. Specimens contain source as data; they add no comments to tracked code. The nine required comment failures are separately named line, block, javadoc, gradle, shader, docstring, suppression, unicode and prose-exception. Extra exact-license controls also run. Java bytecode specimens compile before rule evaluation so a compiler crash cannot impersonate a boundary failure. Tool specimens demand the expected diagnostic, not merely a nonzero exit.

The root manifest classifies contract roots and is used for bounded source reachability. ArchUnit checks compiled main, benchmarkCore and devClient outputs. This is a scoped guardrail rather than a whole-program proof of reflection behavior. New reflection/mixin roots must be declared. Descriptors are discovered by filename and explicit ID without a shared catalog edit.

Code scanners distinguish literals from comments. Markdown documentation and structured JSON specimen data are prose/data, not executable comments. Unknown authored source extensions fail until supported. Groovy-Eclipse P2 bundles and nested JARs are hash-verified before loading. Required wrapper attribution and each executable shebang are exact exceptions. There is no general vendor exemption.

The check emits per-step rule and test/file counts and the complete self-test table under `build/reports/foundation/`. Historical FINDINGS evidence is frozen by checksum; local handoff verifies those external bytes separately. CI validates the references' shape and does not pretend to fetch host-private evidence.

CODEOWNERS flags checks, locks, tests and workflow changes for Philip. No remote exists; required review protection and hosted execution are untested. Later benchmark coverage/noise analyzers, live receipts, correctness oracles and runtime launches are outside this check.
