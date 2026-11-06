# Asahi fast-path handoff

Fast-path acceptance completed on Linux ARM64 Asahi / Apple M1, MC1.21.1,
NeoForge21.1.253, pinned Iris1.8.12/Sodium0.6.13, BSL10.1.8 native384/TAA,
Overworld authored save, RD12, no DH. No shortlist item closes from this smoke.
No commits, pushes, paid resources, promotion, Windows/PC or C02 work occurred.

The final real normal-public native → `dead_mip_chain` → native bracket passed:
[terminal summary](smoke-final/summary.json), [operation/process/configuration proof](smoke-final/operation.json),
[static admission](final-preflight.json), [exact command](final-command.json),
[canonical/profile/world reset proof](FINAL-RESTORATION.json).
Candidate selected the generated BSL adapter, compile option
`SHADERLOD_BSL_DEAD_MIP_CHAIN=1`, actual-source/API/current-history admission,
and parsed composite6/7 mip lists `[]`; native and return selected original BSL
and `[1]` mip lists. All 384 non-ShaderLOD native options were checked. This is
selected active graph/render evidence, not new executed-GL witness evidence.

[Native](smoke-final/native.png), [candidate](smoke-final/candidate.png) and
[return](smoke-final/return.png) are real completed 2560×1600 PNGs. The authored
terrain, trees, materials, shadows and sky rendered without gross artifacts on
inspection; no image equivalence, motion, DH/PBR or broader-family qualification
is inferred. Three valid six-second windows retained187/189/181 samples, with
medians32.988/32.460/33.491ms. This tiny cold bracket proves workflow, **not a
statistically reliable gain**. Full-frame hot comparisons and independent review
remain the successor's work.

Measured costs (seconds):

| Stage | Seconds |
| --- | ---: |
| Managed pool preparation | 0.201 |
| Static preflight | 0.314 |
| Runtime preparation/startup | 18.328 |
| World/viewport/initial warmup | 25.931 |
| Native Apply/readiness + requested5s warmup | 11.543 |
| Candidate Apply/readiness + requested5s warmup | 15.175 |
| Return Apply/readiness + requested5s warmup | 14.565 |
| Native/candidate/return measurement orchestration | 8.046 / 8.038 / 8.180 |
| Configuration restoration + process release | 11.361 |
| Caller total | 123.898 |
| External runtime command wall time | 124.443 |
| Post-run canonical pool reset | 0.205 |

Each timing window requested6s; measurement orchestration also includes status,
focus and export/guard verification. Arm costs include1s configuration-settlement
boundaries and actual public Apply/reloads. These are observed end-to-end costs,
not isolated GPU timing or a promised future launch latency. The offline build
producer was up-to-date and completed successfully in4s; [build log](build.log).

## Changes and concrete failures repaired

- `scripts/linux_arm64_runtime.py`: existing wrapper now supports `build` and
  `benchmark`, portable JDK lookup via explicit configuration or javac on PATH,
  bounded Air defaults, offline producer-backed receipt with prior-hash check and
  backup. Existing client/native pin checks retained.
- `scripts/run_benchmark.py`: adds prepared-world selection, explicit expected
  viewport and `public-smoke`; reuses existing pool admission, lifecycle,
  supervision, controls and exports. Linux focus uses the in-game owned control;
  Mac fallback stays Mac-only. Actual world-load readiness precedes saved pose.
  Bounded partial-JSON retry handles the existing Java receipt writer's
  truncate/write visibility. PNG paths resolve against the owned profile; full
  decode, unique request IDs, copy hash and completion are checked before success.
- `scripts/experiment_lifecycle.py`: per-operation unique request IDs prevent stale
  screenshot/receipt reuse, strict expected viewport binding supports Asahi,
  candidate runtime plan binds capture identity without altering original launch
  plan. Unexpected configuration conflicts no longer prevent exact client/launcher
  stopping; restoration still refuses uncertain bytes.
- Mutation acknowledgments remain inside a declared owned action and preserve
  pre-action CAS. They wait for a bounded1s stable configuration map to cover the
  observed delayed family persistence. This is cooperative ownership, not an OS
  protection against arbitrary external edits; external conflict handling remains
  fail-closed. No claim is made that a short stable period eliminates every future
  asynchronous persistence source.
- `scripts/run_fast_checks.py`: explicit self-contained lifecycle/storage/preflight
  group; authentic pack/history checks selected with `--integration`. Missing
  selected inputs return2 with exact paths before tests. Real32GiB storage-copy
  reserve stays intact; fixture temp roots use the project filesystem rather than
  the smaller `/tmp` mount. Child fixture imports receive explicit PYTHONPATH.
- `tests/pinned_tools.py`, G06 source/observer tests: remove real Mac executable,
  archive and report path assumptions; explicit JDK/archive configuration and
  project-relative report roots. Historical prototype requirements retained.
- New focused regressions cover configuration conflict still stopping the exact
  client while refusing restore, explicit viewport mismatch and missing JDK.
  [Maintained usage](../../../docs/EXPERIMENT_LIFECYCLE.md).

Useful failed attempts are retained. r1 failed the async open→pose boundary and
restored/released. r2 detected a strict viewport mismatch and delayed BSL config
persistence; its first release ordering incorrectly left the client alive. The
executor corrected the observation, stopped the exact recorded client, retired
its launcher, preserved conflicting bytes in
[manual recovery](smoke-r2/manual-recovery.json), then reset only the manifested
pool. r3 correctly refused that occupied lane before release was reconciled.
Those old operation manifests remain `recovery-required`; do not pretend they
were automatically restored or recover them against a subsequently replaced pool
inode. Their resolution is the preserved manual proof and subsequent successful
canonical reset. r4 caught partial receipt JSON; r5/r6 caught relative/unfinished
PNG consumption. They restored/released. r7 and final passed, with no control
recovery errors. The final production runtime scripts match the final input
freeze; subsequent changes were docs/tests and optional test selection only.

## Checks and missing prerequisites

[Focused delivery log](focused-tests-delivery.log):173 tests passed in23.995s;
one macOS system-temp-alias case was explicitly platform-skipped. Includes real
Python subprocess lifecycle smoke, interruption/recovery, exact-stop conflict,
launcher, pool/storage and self-contained plan checks. Tests remained strict.
[G06 observer](g06-observer-tests-delivery.log):4 passed using configured Linux
JDK21, including real pure Java compilation/execution.

[Optional prerequisites](optional-prerequisites-final.log): selecting Photon,
Solas/AstraLex, Bliss and Reimagined returns2 with missing authentic archive paths.
Sildur preflight's three archive-dependent cases and historical renderer reports
are explicitly outside the self-contained group and available via `--integration`.
The initial broader [189-case run](focused-tests-final.log) exposed these actual
missing reports/archives plus `/tmp` below the unchanged32GiB floor; it is retained
as failing evidence, not rewritten as a pass.

[G06 source check](g06-source-tests.log):11 attempted, one error and one skip;
portable pinned-Iris source/layout checks pass, but the historical G06 prototype
ZIP/report was not transferred. The failure is an optional authentic history
prerequisite, not silently passed or reconstructed. Do not claim this full module
passed. No missing archive was downloaded or relicensed. An initial focused
runner child-import failure is retained; its obsolete runner/fixture was stopped
and removed after verifying exact ownership and preserving its failure log.

## Restoration, storage and next use

Final operation reports `complete`, exact configuration restoration and release
verified; zero clients, controller and supervisor absent. The wrapper used
`--no-daemon`; pre-existing shared Gradle daemon and reusable caches are preserved.
The final driver compares full canonical config hashes and world maps before and
after, then uses `run_storage.checkout` to restore the reusable pool world/profile.
[Final proof](FINAL-RESTORATION.json) verifies both canonical worlds, every source
config, lane/save locks and no clients. There is no transferred live job or lock.

[Storage stewardship](storage-steward.json) inventories approximately65.9MB of
useful final/failed evidence,69.9MB reusable pool and145.6MB existing compiled
outputs retained for the successor. Removed only an obsolete0.81MB own test
fixture after exact process proof, preserving its failure log. Observed free
space130.10GB before/after cleanup; no exclusive physical reclaim is claimed.
Canonical user inputs and other campaigns/evidence were not deleted or moved.

Reproduction command is in `final-command.json`; replace its output with a fresh
path, inspect static preflight, then add `--run` under sole runtime custody. Use
`linux_arm64_runtime.py build --jdk ...` after source edits, and keep the original
384-option sidecar/native AA. Reuse/reset `neoforge-1211-asahi-authored` using the
same canonical source; never reset a live or uncertain slot. Recover new
interrupted operations with the existing `run_benchmark.py recover` command,
respecting its inode/CAS refusal rather than overwriting conflicts.

Current campaign remains0/11closed. Latest G02 retained steady1.60% improvement
and failed assistant-chosen60s combined gate; interrupted Sol review is incomplete.
G03 stays parked/open under existing evidence. No old agents or unrelated paused
work were restarted. Root owns checkpoint/goal reconciliation and the authorized
single successor launch; this executor has no remaining live resources.
