# Checkpoint operation provenance

`checkpoint.py` began as a byte-for-byte project-local adoption of Philip's existing
`~/.codex/skills/autonomous-project-setup/scripts/checkpoint.py`, copied on
2026-10-19. Source SHA-256:
`a97795140dacb50078797df764f3fd467391f7e0dd4219ea9df5bcb58392b365`.

This is existing personal project tooling, not an imported third-party package.
It uses the Python standard library. The digest above pins the original adoption;
the personal source and this copy subsequently received the display change below. Other migration originals are
backed up under `~/.codex/backups/t3-shaderlod-20261019T212131Z`.

The pinned adoption preserves both existing schemas, generated STATUS text,
prior-file SHA checks, exact historical archives, allocation/progress guards and
cooperative writer locks. Its data remains `docs/STATUS.md`; this directory is
not another state store. Use `python3 scripts/project_checkpoint.py` as the public
entry point. Future changes should update this provenance and relevant tests.

## Display refinement, 2026-10-19

Only `human_view` changed: the default read-only summary separates readable fields
and routes full allocations/history to `--json` or `--full`. Canonical rendering,
schemas, allocation/progress guards and update behavior remain unchanged. Existing
managed STATUS bytes still validate; project checkpoint tests pass. The current
vendored file matches the current personal helper byte for byte. Current SHA-256:
`f679addce7a3524488df8b1052b9fea7793a61e6e656cd628a2bd30a7c78d148`.
