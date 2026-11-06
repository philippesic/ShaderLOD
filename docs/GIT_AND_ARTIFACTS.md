# Git and artifact preservation

Track maintained source, tests, benchmark definitions, setup/configuration templates,
AGENTS.md, current state, indexed history, decisions and compact result summaries.
Keep credentials, machine-specific private settings, large models/data, raw captures,
logs and regenerable build caches outside Git. Ignored does not mean disposable.

## Current audit, 2026-11-05

Maintained source/document/configuration directories were checked for ignored files;
only expected generated/cache entries were found. Many useful files are untracked:
Git-visible is not committed, pushed or backed up. Review and commit them explicitly
before treating another clone as a complete copy. No commits or pushes were made.

- `experiment-evidence/fastpath/REPORT.md` and the small dependency preparation
  receipt are now Git-visible; raw report attachments are not thereby backed up.
- `dev/experiments/` includes frozen source copies, restoration inputs and raw
  evidence. It is not all disposable, despite being ignored.
- `run-workloads/` includes valuable canonical worlds as well as resettable clients.
  Preserve canonical worlds and recovery inputs separately before retiring copies.
- `dependencies/` contains pinned archives and local tools. Missing historical
  archives cannot necessarily be reconstructed from a filename or hash alone.

## At a milestone or machine handoff

Keep compact results and reusable code in ordinary Git-visible project directories.
For large valuable artifacts, record identity, location, purpose and recovery source
in the existing experiment/campaign record. Verify the recovery copy before deleting
the original. A second name, symlink, same-disk copy or an untested hash is not an
independent backup. No new per-run form or automatic cleanup is required.

This audit did not verify an independent backup of the ignored artifact trees.
Historical copies contain unique dirty/untracked work and must be reconciled before
retirement. Frozen source snapshots, nested repositories and third-party dependencies
retain their own history and exclusions; do not apply current rules over pinned runs.
Raw metadata inventories and audit limits are retained outside Git in the performing
agent's `~/.local/share/p-stack/repairs/20261105-ignore-audit/` receipt directory.
