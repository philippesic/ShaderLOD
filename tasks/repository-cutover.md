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
No raw archive deletion or force-push is authorized.

## Done when
Remote main contains the exact reviewed new tree and both prior histories, old branch
history remains recoverable after branch removal, a fresh published clone passes
portable setup/checks, and remaining workspace/deletion actions have clear prerequisites.

## State
Owner: this conversation, main in /home/philip/ShaderLOD-next. Both working checkouts
were clean before changes. The original remote has main and rtx5080-audit; the new
repository started with an independent Git root. Guidance/skills and benchmark work
are complete. Long-term hardware and backup setup remain separate decisions.

The retained artifact snapshot at
/home/philip/.local/share/shaderlod-next/evidence/history/original-repository holds
original source, Git history and ignored evidence. Readable results do not replace
raw measurements and reproduction inputs. Verify current working files against this
snapshot before recommending removal of /home/philip/ShaderLOD. Preserve any new or
changed valuable material separately rather than rewriting the historical snapshot.

## Next
Preserve verified Git recovery bundles, audit the old working tree against retained
artifacts, validate the new source from a clean clone, and publish a tree-preserving
cutover commit retaining both legacy branch tips. Verify remote main before removing
the legacy branch. Leave rename, T3 project selection and old-working-checkout deletion
with the planned later cutover; retain the external evidence archive.
