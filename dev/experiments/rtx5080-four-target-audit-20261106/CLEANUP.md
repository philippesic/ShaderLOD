# RTX 5080 storage cleanup, 2026-11-06

The user authorized cleanup of old ShaderLOD junk. Cleanup completed. The original capacity blocker was subsequently resolved: the
latest native C: check has **60.8 GiB free**, sufficient for sequential captures
above the maintained32GiB reserve. Baselines are acquired and optimization testing
is active. The historical cleanup measurements below describe the earlier state.

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

## User scope decision and remaining capacity

The user explicitly selected “Keep cleanup limited to ShaderLOD.” The proposed
NVIDIA DXCache and both non-ShaderLOD checkpoint staging folders remain untouched;
there is no pending approval question. Do not revisit their deletion without a
new user instruction.

A fresh read-only check finds 5,287,284,736 bytes free (about4.924GiB). The five
retained ShaderLOD Prism trees total2,874,544,700 logical bytes (2.677GiB), including
worlds, recovery files, packs and reusable Java/world/payload inputs. Even removing
all these trees would not reach the32GiB reserve, so further small ShaderLOD purges
cannot unblock this campaign. They remain preserved. Evidence is in
cleanup-retained-size-check.json. No native profile copy or GPU acquisition ran.
The audit stays incomplete until additional backing-volume capacity is available;
do not lower the reserve or substitute WSL virtual free space.

## Subsequent capacity update

A fresh native C: observation reports67,826,868,224 bytes free (63.2GiB). Storage
now permits setup above the32GiB reserve. This later increase is not attributed to
our cleanup. No NVIDIA cache or other-project checkpoint deletion was performed.
The80–100GiB suggestion was conservative headroom, not a hard benchmark requirement.
Native setup and all four repeated original baselines have since completed.
The latest60.8GiB remains sufficient. Capture raw4K motion sequentially, compress/
transfer completed evidence, and recheck capacity before each admitted copy.
A363-frame RGBA sequence needs about11.2GiB raw; retaining several such sequences
simultaneously motivated the old headroom estimate. No fixed100GiB requirement
exists and cleanup remains ShaderLOD-only.
