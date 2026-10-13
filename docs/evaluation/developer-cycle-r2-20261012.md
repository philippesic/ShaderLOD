# Developer cycle audit, second pass

Observed 2026-10-13 UTC; shader execution and Gradle remain paused. Parent subsequently assigned the three utility/test files below plus this report; the proposal history is retained below. Process inspection at approximately 02:23 UTC found no Java/Gradle/Minecraft process (only the inspection shell and its matcher). No build, client or GPU work was launched.

## Finding

The next narrow reuse opportunity is the existing offline Iris option parser. Six private `actual-options.jsh` programs in `dev/logs/concept-transfer-r1` repeat archive traversal, Boolean reference collection, OptionSet construction, native-map loading, manual generated-option augmentation, value validation and report serialization. They cover BSL 26.1.2/26.2, MakeUp 26.1.2/26.2 and Complementary Reimagined/Unbound. The maintained Photon preparation separately embeds a similar Java inventory, but its `discover` function hardcodes the Photon manifest and dependency versions. Current shared adapter authoring does not expose either operation.

A recorded BSL caller expected 385 options but loaded 387, missing helper declarations; its 75-second failed trial is documented in `development-throughput-harness-20261012.md`. Later private BSL discovery manually lists four added declarations, then asserts that exact list. A reusable operation can discover that delta from the actual original/generated archives instead, preserving the native map and using actual pinned Iris option semantics for requested values.

## Important boundary

Broad discovery is not the actual loaded program graph. Saved BSL evidence reports 385/389 broad declarations versus 384/388 supplied/native-plus-generated maps. Reimagined reports 403 broad versus 398 supplied native keys. These differences must remain visible. Calling the broad inventory a complete loaded-map derivation would introduce precisely the assumption this audit is trying to remove. It must neither auto-delete native keys nor replace strict runtime equality with subset equality.

The finite useful output is: actual broad declaration sets/defaults; missing or invalid supplied native values; discovered added/removed declarations between the two archives; and a proposed native-plus-added map explicitly labeled pending loaded equality. Real selected/include-root discovery would require additional Iris source-contract investigation and is outside this bounded initial proposal.

## Existing input to consumer path

1. `build_supported_pack_adapters.py` checks pinned original members/helpers and assembles a generated ZIP. Its first-audit sync command already propagates resources/catalog identity.
2. Shared `PackSelectionService` selects the generated or native graph from enabled/preset and renderer availability. `PERFORMANCE && available && RECIPES.contains(adapterId)` chooses the native renderer route; therefore generated-source work is not necessarily the selected Performance graph.
3. `PackSelectionTransaction.iris` obtains real declared defaults and effective option values from the loaded Iris ShaderPack; the apply service handles archive/options/recovery.
4. Existing family tests exercise public settings with nativeRendererAvailable true and false, but some fixtures reconstruct declarations independently. `BslQuadSettingsSelectionTest.bslDeclaredDefaults` already uses actual Iris OptionAnnotatedSource/OptionSet; its source traversal includes four suffixes, while Photon inventory includes seven.
5. Ten packages already share main Java/resources through Gradle source sets. NeoForge 26.2 consumes shared adapter and native-map resources and selected shared tests. Serial all-ten invocation may add process/configuration overhead, but this audit has no matched timing evidence that compile time dominates. Do not claim parallel builds are the primary speedup or run them during pause.

## Proposed implementation and ownership

- `scripts/discover_iris_options.py`: explicit archive(s), native map, exact Iris JAR and explicit dependency/classpath inputs; verify files and run one bounded Java source command. Emit inventory/delta without changing source, runtime sidecars or historical reports.
- `scripts/java/IrisOptionInventory.java`: reuse actual OptionAnnotatedSource/OptionSet/MutableOptionValues APIs from the six private programs, deterministic source ordering and all supported GLSL suffixes, outputs through Gson. No ShaderPack singleton, fake platform or driver.
- `tests/test_iris_option_inventory.py`: cheap pure input/output refusals plus authentic BSL and Reimagined originals/generated archives using existing local pinned Iris dependencies. Compare native values and declaration deltas against saved private records, with broad-vs-loaded difference explicit. No Gradle.

A builder option can call this helper if assigned; do not make discovery mandatory for normal archive assembly or retroactively alter sealed Reimagined preparation. Explicit original/generated paths permit Photon and all family archives without another adapter registry.

This removes four repeated code-authoring activities per fresh parser preparation: writing Java/JShell discovery code; embedding archive/native-map paths and classpath invocation in a new private program; manually enumerating newly discovered helper options; parsing/serializing parser output. Supplying and selecting the correct local JDK/JAR dependency paths remains explicit caller work; the utility does not infer a loader tuple or eliminate dependency selection. Six extant scripts demonstrate recurrence. Their elapsed authoring time is not retained here, so no time-saved or throughput factor is claimed. Build compilation, selected graph consumption, visual quality and FPS remain distinct.


## Implemented and exercised

Parent assigned the narrow utility after the proposal, excluding builder edits and all prepared-session mutations. The three files above now implement the operation. It accepts original/generated ZIPs, native observation JSON, explicit Iris JAR+SHA256, exact Java executable and dependency JARs, and optional string-valued overrides restricted to newly added declarations. It preserves native keys/values, validates them through Iris on both archives, rejects removed declarations or invalid/unknown overrides, and reports original/generated broad inventories and changed defaults separately. A changed default is allowed only when the preserved supplied native value is still accepted by the generated parser. Actual loaded equality remains required.

`candidateExpectedOptions` is the derived candidate map; `excludedOriginalDeclarations` makes the broad/loaded boundary visible. This is a preparation output, not automatic runtime adoption: current sealed caller/source files are unchanged. Fresh preparation can use this map where its existing caller expects the native-plus-generated map. The current Reimagined `run.py:100` separately asserts the exact native 398 plus four declarations; independent review checks the equivalence without launching it.

Verification command:

```sh
python3 -m unittest discover -s tests -p test_iris_option_inventory.py -v
```

Initial result: four tests passed, zero skips, 3.739s. Authentic tests assemble current BSL and Reimagined recipes with the maintained builder into temporary directories, then run pinned Iris 1.11.4 from local JDK 25 using real OptionAnnotatedSource/OptionSet/MutableOptionValues. They match saved effective parser maps exactly: BSL 384→388 and Reimagined 398→402. Broad declarations remain 385→389 and 403→407, excluding the original unused 1 and 5 declarations from expected maps. Negative checks cover duplicate/non-string maps, bad Iris pin before process creation, removed declarations, unknown or invalid added overrides, and attempts to change native observed keys. The synthetic geometry-stage case additionally proves `.gsh` traversal and preserved values despite a changed native default.

Two actual CLI invocations independently exited 0 on the saved original/generated archive pairs. Exact executable/arguments, exit codes, stdout/stderr and all parser/JDK/source/archive/native-map hashes are retained in the ignored fresh receiver:

- `dev/logs/developer-cycle-r2/option-discovery-r1/bsl-command.json` and `bsl.json`.
- `dev/logs/developer-cycle-r2/option-discovery-r1/reimagined-command.json` and `reimagined.json`.

Iris fixture SHA256 is 69bdb6899ba006cf91b16565e4445a7a4f331e39b790c81c99626814bf6e22df, not inferred from a filename. The utility requires this caller-supplied pin and records exact dependency hashes; it does not download or select dependencies automatically. Each Java subprocess was bounded to 30 seconds and exited before handback; no persistent child, Gradle, client or GPU process was started. Output report paths must be new; historical evidence is never overwritten.

Code handed to independent review with SHA256:

- Python utility 4e0921b737f628cb58af20b390c80f38e1f056cb492d86b6ad8f36b3bfd92e76.
- Java parser f588eb1eed91c35346a4437ca25cde0ab8a8819e5896e5308fcd9c3d7c45ee6f.
- Test ce03ba4a7f08f47d9ffe2c71cf9cb2d379d131a122d574a1aca68f9e0c3f8d08.

All nine families can provide explicit archive/native-map inputs to the same parser. Only BSL and Reimagined have authentic acceptance in this change; other families/tuples remain unverified. This removes repeated parser authoring and manual declaration-delta guesses, not the need for real selected-program, recovery, quality or speed evidence. No elapsed productivity improvement has yet been measured.

### Independent review correction

The reviewer found that Iris `MutableOptionValues` preserves an arbitrary string even when the candidate removes it from the declared domain. The first implementation's effective-value comparison therefore did not establish range validity. The authentic negative changed BSL `FXAA_EDGE_SENSITIVITY` from default 1 with `[0 1 2]` to default 0 with `[0 2]`, while the observed native map retained 1. The utility initially accepted this invalid candidate. This failure is preserved as the reason for the correction.

The Java validator now explicitly checks `StringOption.getAllowedValues()` before its effective-value comparison. A regression uses the actual BSL archive and native map with that one declaration narrowed. Final author verification: five tests passed, zero skips, 4.130s. Both actual CLI invocations were repeated successfully into a fresh `dev/logs/developer-cycle-r2/option-discovery-r2` receiver; the first receiver remains intact. Use r2 reports for final code evidence. Final Java hash is 617d2e52dc47f1b39780bf3685c217b2cd005d7aeb9e1adb661abf9d7dec3a03; final test hash is e6ba24a8c3d9e2ea5d67f4c4742ad21af9870e38a3cc016a40fb03bfa92df6a9; Python hash is unchanged. Independent reviewer was notified of this refreeze before rerunning.

Independent review accepted the corrected offline scope: five tests passed, zero skipped, 4.408s; a separately generated BSL narrowed-domain mutation now refused, and the r2 BSL candidate map equals the actual saved BSL MAIN 388 loaded map. Final hashes matched the refreeze. The reviewer retained the original failure and repair in `development-parallelism-r2-20261012.md`. No live jobs remain owned by this worker or reviewer.
