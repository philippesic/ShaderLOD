# Recorded renderer measurement qualification

This source-only change unlocks the next distinct26.2 Fabric MakeUp private
submission-copy comparison. It preserves the existing finite runner, immutable
input/freezes, source ancestry, quality and Off restoration checks. It does not
launch a client or reopen the closed1.21.11 MakeUp trial.

The dev-side AFK repair already works: authentic1.21.11 r2 reports260/NONE/
MINIMIZED and0invalid frames. Its5.885→7.414→12.790ms/render bracket has117.33%
return drift and opposite comparisons. No causal gain or regression is accepted.

The remaining26.2 gap is narrower: `rateAdmitted` permits transient configured
and effective120/NONE between good260 snapshots. The patch pins the cap at the
start of the actual measured window, latches a rejected frame on any change,
retains configured/effective bounds and observation count, and preserves an
unavailable observation in the completed-render denominator. Early failure
receipts preserve the partial window counts and explicitly invalidate the whole
measurement. No frame is selected out, input timer reset or pacing simulated.

Input → consumer → output → recovery:

- Actual pinned26.2 `GameRenderer.renderLevel` RETURN hook reads its version's
  `Minecraft.getFramerateLimitTracker`, policy and native window facts.
- `PortRendererFrames` retains every completed world render and rate observation;
  `FabricRoundtrip` latches failure before reset branches and emits window bounds.
- The actual prepared finite runner calls `qualify_renderer_bracket.py` after its
  exact stop/release, writing `measurement-qualification.json` before acceptance.
  Existing loaded native100/AA, command consumption, freeze, still and restoration
  assertions remain in place.
- Qualification failure retains the raw receipt and qualified error output.
  Extending a comparison requires scientific review of the actual avoided-work
  denominator and both original comparisons. No automatic rerun or numeric drift
  threshold is introduced.

The qualifier supports only observed1.21.11 and patched26.2 report schemas.
Minecraft1.21.1 has the older limiter and no newer inactivity tracker; its current
probe must continue through its own legacy adapter. Passing newer tracker fields
as1.21.1 fails explicitly. No later cell inherits support by version naming.

Cached1.21.11 stage fills are reload/warmup-inclusive:111/90057 commands removed
(0.1233%), with no timed-window new reductions. Cached reuse is possible; these
counts neither establish zero submitted savings nor per-frame savings. The
qualifier records this cheap workload observation before any extended comparison,
without inventing a total-frame speedup bound.26.2 records measured helper
input/output and copied GL entries separately from GPU completion.

## Integration after the current owner's release

`patches/benchmark-rate-window-262/baseline-sha256.json` names all5actual live
files and their exact before/after SHA-256. They are untracked/dirty in the current
main tree and absent from this clean worktree; the patch intentionally preserves
that ownership. Verify every before hash before applying `consumer.patch`; if any
changed, reconcile with the owner rather than blindly applying. Copy the new
qualifier into main before applying the runner hunk. Do not modify current frozen
builds or prior freezes. A future fresh attempt must include the helper and runner
in its supervision freeze and rebuild the changed dev consumer/resources with the
resource owner's authorization, then freeze them. Previous runtime evidence is
still unchanged; this patch has no compiled/runtime26.2 acceptance.

Cheap checks performed:8stdlib unit tests; direct authentic1.21.11 r2 receipt
qualification; `git apply --check` against actual live files. Test-only virtual
observations use the inspected1.21.11 boundary (>60000ms→min(cap,30)), including
a good final snapshot, while retaining all4frames. Tests also cover absent facts,
focus/iconification, unavailable count, equal-cap drift, nonfinite denominator,
legacy schema rejection and cached-denominator labeling. Fixture provenance:
`tests/fixtures/renderer-rate-12111-r2.json` records the raw report path and SHA.
Java compilation, actual callback behavior,26.2 driver execution and speed are
unverified. No client/build/GPU or owned background jobs were created.

Use the tested mechanical installer for that handoff (default is read-only):

```sh
python3 scripts/install_renderer_rate_patch.py --root /Users/pippo/github/shaderlod --output /tmp/renderer-rate-patch-check.json
# Only after the active owner's exact release and fresh census, on a future attempt:
python3 scripts/install_renderer_rate_patch.py --root /Users/pippo/github/shaderlod --apply --release-receipt /absolute/current-owner-release.json --output /tmp/renderer-rate-patch-installed.json
```

The installer checks all five baseline hashes and `git apply --check` before any
mutation, refuses a differing existing qualifier, requires zero-clients/free-lock
release evidence for apply, and verifies exact after hashes. The runner's `errors`
are trial errors: a failed startup with successful resource release can receive
the repair. Those errors and status are retained in the installation output;
the release receipt stays unchanged. Explicit `releaseErrors`, if present, must
be empty. Release booleans do not establish exact owner/process binding or a live
lane census. Parent must verify current ownership release and a fresh census;
the installer explicitly records both boundaries as unverified. It does not rebuild or
rewrite old freezes: `requiresFreshDevBuildAndAdmissionFreeze=true` remains in
its output. Installation was exercised on61,856bytes of isolated authentic
five-file preimages, including failed release and wrong-preimage rejection;
main source was left untouched. Nine self-contained stdlib tests pass with zero
skips. To repeat the authentic install check with admitted preimages, set
`SHADER_RATE_SOURCE_FIXTURE=/tmp/autonomy-shader-source-fixture` when running
`tests/test_renderer_rate_patch_install.py`; normal tests use a tiny synthetic
patch fixture and make no Java/runtime claim. The follow-up failed-but-released
trial fixture and installer changes are source-only and await focused parent
testing after the active BSL quiet window; the earlier nine-test result does not
validate this later revision.

Latest-owner rebase: complete five-file before bytes are preserved at
`/tmp/autonomy-shader-preimages-owner-update-20261011` with a complete SHA/size
manifest (62,812bytes). The owner added `GlfwStartup` pre-world viewport/focus
setup and three startup fields in `FabricRoundtrip`; all remain byte-preserved
in the rebased result. Only that file’s before/after hashes and three patch hunk
positions changed. The live source stayed untouched. After a fresh idle-client
census, nine focused tests and authentic five-copy installation passed, including
the failed-but-released trial fixture. The qualifier and four other baseline
source files are unchanged. For this revision use the new snapshot path as
`SHADER_RATE_SOURCE_FIXTURE`; Java/driver and fresh admission remain unverified.
