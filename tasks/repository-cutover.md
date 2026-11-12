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
This is not standing force-push authority. No blanket historical archive deletion is
authorized: extract useful evidence and verify compact results before pruning it.

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

The retained artifact snapshot at
/home/philip/.local/share/shaderlod-next/evidence/history/original-repository holds
original source, Git history and ignored evidence. The current old checkout's 184,669
regular files and symlinks outside .git all matched this snapshot, with no missing or
changed entries. Git preservation was checked separately. No symlink from the new
checkout resolves into the old checkout. Some compact results still depend on reports
or inputs in the archive: the import did not yet meet Philip's no-archive-dependency
goal. Extract useful evidence, verify claims and figure regeneration from the retained
compact data, and then prune superseded bulky material. Do not retain the complete
snapshot by default. These copies share a disk and are not an independent disaster backup.

Cutover receipts, audit, recovery bundles and clean-clone check log are under
/home/philip/.local/share/shaderlod-next/evidence/repository-cutover-fkco2lv0.
Rename, T3 workspace change and old-checkout deletion have not been performed.
Long-term hardware and independent backup setup remain separate decisions.

## Next
When Philip requests the final switch, confirm both checkouts have no new active work
or owned processes, recheck preservation of any changes since this audit, rename the
new checkout and select it in T3, then retire the old working checkout. Complete
historical evidence extraction before pruning the archive; retain Git recovery bundles
for the approved metadata rewrite. Validate path-sensitive setup after the rename
and remove this task and its AGENTS navigation when the switch is finished.
