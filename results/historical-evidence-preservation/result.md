# Locally available historical evidence is preserved independently of the old checkout

Readable candidate and investigation results now point to compact local sources or a separately named artifact snapshot. The snapshot includes the complete original checkout, Git history, ignored experiments, run freezes, dependencies and locally available inputs. The earlier document and retired-code archives are also preserved and indexed.

## Conditions and limitations

Evidence level: cpu-integration. 185689 regular files were verified by SHA256 and 415 symlinks by target text. No copied symlink resolves into the old checkout. External system-tool links remain explicit in the receipt. This establishes local byte preservation, not universal experimental reproducibility. Remote-only Windows captures and previously missing inputs are not recovered. The snapshot shares storage on the same disk and is not a disaster-recovery backup.

## Decision

Retain this evidence store as the backing archive for the new knowledge index. Future development can use the new repository and its artifact store without browsing the old checkout. Do not infer permission to delete remote data or claim that every historical artifact ever mentioned exists here.

## Evidence and source

[Preservation receipt and coverage inventory](../sources/historical-preservation.json) sha256:f7aa48ef4394be8598d7b6a32db6a3133bb21ae496249a2f6e38b5926821f8a9

The receipt identifies the original source revision, full per-file manifest, earlier archives, external links and remaining limitations. See [artifact access](../../docs/results.md#historical-artifact-access) for relocation rules. Original reports retain their original paths as provenance; those paths are interpreted beneath the preserved snapshot.
