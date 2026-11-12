# Repository cutover

## Goal
Make the new ShaderLOD codebase authoritative on the existing GitHub main branch,
preserving both implementation histories and required evidence before retiring the
old working checkout.

## Authorized
Philip approved resolving stale task records, a short user-focused README, preserving
both independent Git histories in a cutover commit, publishing the new tree to main,
and removing the other remote branch once its history is retained. Rename, T3 project
switch and old-checkout deletion are later cutover actions; do not perform them now.
Philip subsequently approved a one-time force-with-lease push to remove four Cursor
coauthor trailers, preserving recovery bundles and the old-to-new commit mapping.
This is not standing force-push authority. Philip assigned complete historical
extraction into this repository, with a return only when the old checkout and bulk
archive are no longer needed. Verify compact results before recommending retirement;
the actual rename, workspace switch and old-directory deletion remain later actions.

## Done when
Remote main contains the exact reviewed new tree and both prior histories, old branch
history remains recoverable after branch removal, a fresh published clone passes
portable setup/checks, and Philip's later workspace switch and old-checkout retirement
are complete without removing required artifacts.

## State
Owner: this conversation, main in /home/philip/ShaderLOD-next, tracking the existing
user-owned GitHub origin. Main now contains the rebuilt tree and both independent
histories through a normal fast-forward push. The remote rtx5080-audit branch was
removed after checking its tip was retained on main. The subsequent trailer cleanup
rewrites 31 newer commit IDs without source-tree changes; results/sources/commit-id-map.json
maps the original identities, which remain recoverable in the published-main bundle.
Legacy published branch tips are unchanged. Verified recovery bundles preserve both repositories; the legacy bundle
was restored and checked, including all 820 refs.

A fresh clone from GitHub passed ./check --full: 99 Gradle tasks executed, none reused
at task level, 104 Python and 38 Java tests passed. Downloaded tools and lower-level
dependency caches were reused. This documentation cutover adds no live rendering or
performance claim. The README is user-focused; contributor setup/navigation lives in
docs/development.md. Completed agent-operations work no longer has an active task.

Historical reports and compact measurements now live in results/sources/history,
indexed by results/historical-evidence-extraction/catalog.md. The extraction retains
failed/inconclusive records, earlier report versions, quantified ledger excerpts,
nested RTX controls and recoverable authored source. Existing findings reference this
data rather than old directories. Source recovery covers 2,963 unique authored sources:
2,939 main-history Git blobs and 24 additional text records. The 61-control Mac program
remains distinct from the later sixteen-candidate campaign. Detailed raw reanalysis
and exact old environment/world replay are deliberately outside the retained claims.

The old checkout's current 184,669 entries outside .git matched the safety snapshot
before extraction. No symlink from the new checkout resolves into the old checkout.
With both old locations hidden, the offline verifier passes, all 2,939 Git-backed
source variants recover with matching SHA-256, and the calibration figure regenerates
byte-identically. The isolated full gate passes (11 tasks executed/88 reused; 104
Python tests executed). A final byte audit checked all 184,669 current old-checkout
entries outside .git with zero differences. Neither old directory has been deleted. Keep the new repository's prepared runtime profiles/evidence and small Git
recovery bundles outside the old-directory retirement scope.

Cutover receipts, audit, recovery bundles and clean-clone check log are under
/home/philip/.local/share/shaderlod-next/evidence/repository-cutover-fkco2lv0.
Rename, T3 workspace change and old-checkout deletion have not been performed.
Long-term hardware and independent backup setup remain separate decisions.

## Next
Publish the verified extraction and confirm its evidence/source recovery from a
fresh remote clone. Extraction scope, retained-data integrity and intentional omissions
belong in results/historical-evidence-extraction/result.md.

When Philip requests the final switch, confirm both checkouts have no new active work
or owned processes, recheck preservation of any changes since this audit, rename the
new checkout and select it in T3, then retire the old working checkout and superseded
bulk historical archives; retain Git recovery bundles
for the approved metadata rewrite. Validate path-sensitive setup after the rename
and remove this task and its AGENTS navigation when the switch is finished.
