# RTX 5080 storage cleanup, 2026-11-06

The user authorized cleanup of old ShaderLOD junk. Cleanup completed, but the
benchmark is still blocked by capacity: Windows C: has **4.925 GiB free**, below
the maintained 32 GiB reserve before new inputs/builds/captures. No RTX5080
baseline or optimization timing has been acquired.

## Removed and preserved

Retired 37 old ShaderLOD backup/staging/probe directories directly beneath
PrismLauncher and the bulky samples.csv/metadata.properties files from historical
tune-* runs in the ShaderLOD-5080-Tuning instance. Removed 8,229 file entries with
6,509,099,493 logical bytes. Measured free space rose from 1,277,575,168 to
5,288,157,184 bytes: **3.735 GiB actually recovered**. Hard-linked backups explain
why summed file lengths overstate physical reclamation. A post-check observed
5,288,001,536 free bytes; small concurrent volume changes are possible.

Before deletion, streamed an archive to this canonical Asahi checkout, outside
Git: old-5080-runs.tar.gz, 2,878,128,953 bytes. All 8,323 archived file entries
were checked against an independently acquired source SHA-256 manifest. Regular
entries were read/decompressed and hashed; hard-link entries had to reference an
already verified member with the identical expected size/hash. Source membership,
sizes, modification times and hashes were checked again before removal. No Java
client or Prism launcher was running. A post-check confirms all 37 retired roots
are absent and hashes all 94 retained recovery files successfully.

Kept the tuning root undo.properties and per-run undo-original.properties files
in place. Kept the existing playable instances, personal/world saves, screenshots,
original shader packs, active configuration and fixture recovery record. Kept
shaderlod-testing-r1 and its reusable Java/world/payload inputs. No other project's
checkpoints, shared caches, or Windows/WSL configuration were modified. WSL's
roughly 5 GiB Temp swap.vhdx is live storage, not disposable Temp junk.

## Recovery and evidence

The independent archive and exact manifests remain on Asahi in this directory;
this push backs up the authored procedure and findings, not those ignored bytes.
Preserve the archive/manifests. To recover, inspect the named archive members and
restore only the desired old files to their original PrismLauncher-relative paths;
do not extract it over an active profile. The current 94 recovery files already
remain usable in place. Archive identity and selected roots are in
cleanup-backup-verification.json; per-file identities are in
cleanup-source-manifest.json. Deletions/free-space evidence is in
cleanup-deletion-receipt.json and cleanup-post-check.json. Read-only inventories
are cleanup-*.json/jsonl and cleanup-inventory-native.txt.

preserve-old-runs.py performs preservation/verification only; retire-old-runs.py
requires the verified receipt and rechecks source identities before deletion.
They were exercised on the actual host; Python compilation checks also pass.
These are campaign-specific procedures requiring their retained manifests, not
an automatic cleanup service or permission to remove arbitrary ignored files.

## Remaining input

A structured question is pending about expanding scope to NVIDIA's regenerable
DXCache (~11.7 GiB) and two non-ShaderLOD checkpoint staging directories (~15.8 GiB):
C:\binary-eagle-component-20261105-01 and
C:\binary-eagle-dflash2-smoke-20261106-01. No deletion there is authorized yet;
checkpoint preservation and verification must precede any approved retirement.
Their size estimates do not guarantee enough headroom for setup and 4K captures.
Continue checking real backing-volume capacity; do not lower the benchmark reserve
or mistake WSL virtual free space for native Windows free space.
