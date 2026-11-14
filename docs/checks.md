# Checks

| Command | What it runs | When |
| --- | --- | --- |
| `./check` | Fast lane: compile, Error Prone, Checkstyle, Spotless, dependency analysis, ArchUnit boundaries, Ruff, mypy, Java and Python tests, repository policy, comment ban, exception audit. Labels itself `PARTIAL`. | While editing |
| `./check --full` | Everything portable: the fast lane plus rule specimens, tool self-tests and the shipping-JAR package audit. The pre-commit hook runs this. Checks stay local; remote CI is not used. | Before every commit and at handoff |
| `./check --fresh` | `--full` with Gradle task reuse disabled | After changing check tooling, or when reuse is suspect |

A warm `./check` after a one-file edit takes about 5 to 8 seconds. A warm `--full` with no changes takes about 9 seconds; `--fresh` takes about 100 seconds, mostly tool self-tests. Enable the hook once per clone with `git config core.hooksPath .githooks`; no check command changes Git configuration.

`build/reports/foundation/gradle-tasks.json` lists every task as executed, reused, not-run or failed, and labels the run `full` or `partial`. A partial pass is not a full pass, and reused results are not fresh executions. Nothing here grants live-functional or performance evidence.

## Select the evidence needed

| Need | Maintained lane | Limit |
| --- | --- | --- |
| Ordinary code, test or documentation edit | `./check` during work; `./check --full` at checkpoints and handoff | Portable correctness only; no live or speed claim |
| Rendering lifecycle, activation or restoration change | Ready-session `validate`; use `./verify --runtime` when startup or packaged launch behavior also needs exercise | Relevant live functional behavior, not perceptual equivalence |
| Candidate optimization during development | Short paired `compare`, with separate relevant quality captures | Exploratory evidence; not retention or release acceptance |
| Explain costly work or frame variation | `profile`, starting with frames and adding diagnostics as needed | Instrumentation can perturb work; spans do not prove exclusive effect costs |
| Retain a substantial contribution or defend a release | Frozen `qualify` protocol, scoped coverage and explicit quality review | Only the declared candidate, policy and supported envelope |
| Recheck preserved evidence or rebuild its view | Offline `verify`, `report` or `history` | Integrity or a derived view; no new runtime observations |

Detailed invocation, costs, ownership and recovery belong in the
[benchmark guide](benchmarks.md), [profiler guide](profiling.md) and
[standalone runtime guide](runtime.md). Select affected cases rather than launching
all lanes after every change. Changes to measurements need affected instrument and
analysis checks; changes only to prose do not require fresh GPU evidence. Exclusions,
failed attempts and missing integration coverage remain explicit.

## Tests

Add Python tests under `tests/` and Java tests under `src/test/java`; discovery picks them up. `checks/tests.json` lists required test names that must exist and pass; ordinary new tests need no entry. Failed, skipped or zero-test suites fail the check.

## Rules

Each failure starts with a rule ID. Fix the cause; do not weaken the rule. A rule may be replaced only by one that still rejects its bad specimen in `checks/self-tests.json` and accepts the control.

| Rule | Rejects | Fix |
| --- | --- | --- |
| D1 | Live evidence labels without runtime-owned verification; bare `./verify --runtime` | Use runtime-owned receipts from maintained live workflows; standalone `./verify --runtime` requires explicit allocation arguments. Keep CPU evidence scoped |
| D3 | File writes outside the storage, runtime and check owners | Publish results through `shaderlod_host.storage.publish` or `checks/publisher.py` |
| D5 | Measurements without a count, missing values without a reason, unknown health | Use `Measured` with a positive count or `Missing` with a reason; health is PASS or FAIL |
| D6a | Broad `catch`/`except` | Catch the specific exception, or add an exact failure adapter that rethrows or returns `"unavailable"` |
| D6b | Environment reads outside `shaderlod_host/config.py` and bootstrap | Read settings through `shaderlod_host.config` |
| D6c | GPU query or sync calls outside the measurement adapter | Keep GL query calls in `devClient/.../measurement` |
| D6d | Process launch or argument parsing outside the runtime and CLI owners | Launch through `shaderlod_host.runtime`; add options to `shaderlod_host/__main__.py` |
| D7 | Incomplete, wildcard, stale or unlisted exceptions | Add an exact entry to `checks/exceptions.json` with token, site, reason and `decision` doc, or remove the suppression |
| D8 | Comments, Javadoc, docstrings, unknown source languages | Delete the comment and make the code say it through names and types; tool directives need a D7 entry |
| D9 | `docs/architecture.md` rows whose entry or test no longer exists | Update the row to the current symbol and test |
| S1 | Disabled validators, excluded sources, a changed pre-commit hook | Restore the task or hook; a check cannot be turned off to pass |
| S2 | Missing required tests, specimens, suites or configuration | Restore the test, or add the missing bad/control specimen pair |
| S3 | Nested `AGENTS.md`/`CLAUDE.md`, or `CLAUDE.md` other than `@AGENTS.md` | Keep one root `AGENTS.md`; edit it instead |
| S4 | Tracked binaries, raw outputs, generated files, oversized authored files | Keep raw output outside Git; declare legitimate assets with digest and purpose in `checks/fixtures.json` |
| S5 | Status/history documents, task files without the template, findings without scope or evidence hash | Put open work in `tasks/NAME.md`, a short map in `FINDINGS.md` linking result pages with scope, evidence level and `sha256:` provenance |
| T-* | Real third-party tool failures (formatters, types, tests, architecture) | Read the tool output above the failure. Python formatting: run the command printed with T-PFMT. Java and Gradle formatting: `GRADLE_USER_HOME="$PWD/.local/gradle" JAVA_HOME="$PWD/.local/tools/jdks/jdk-21.0.13+9" ./gradlew --no-daemon -q spotlessApply` |

A failing run ends with one line naming the failed Gradle task, for example `CHECK: partial FAIL in :commentBan`; the rule lines above it give the location.

## Comment exceptions

Owned code, tests, scripts, build files and configuration contain no comments, Javadoc or docstrings. Markdown prose and string literals are allowed. The only exceptions are exact shebangs, the required Apache wrapper notice and the Mixin lint directives described in [Iris integration](iris-integration.md), in the shipping integration mixins and the development-only mixins. Each is listed in `checks/exceptions.json` with its exact token and site. Shader preprocessor lines are executable syntax, not comments. SVG assets follow the XML comment rule; color literals and XML namespace URLs remain valid.
