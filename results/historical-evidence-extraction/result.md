# Historical knowledge no longer depends on the old checkout or bulk archive

The useful locally available reports, measurements and authored source are retained here. The old working checkout and its historical snapshot can be retired without losing the evidence used by the current findings. This is a verified extraction decision, not a claim that every old experimental assertion is correct or that all historical runs can be replayed.

| Material | Retained evidence | Disposition of bulk material |
| --- | --- | --- |
| Earlier document archive | 1,069 supporting reports plus 1,042 unique quantitative ledger excerpts; superseded and contradictory reports remain historical | 1,026 evolving ledger/goal/handoff entries and 14 obsolete instruction entries need no full archive |
| Later campaigns and controls | Per-run/window statistics, failures, refusals, conditions, original source identities and scoped interpretations in the [catalog](catalog.md) | Repeated runtime state, verbose CPU traces and copied profiles/worlds are unnecessary |
| Nested older RTX archive | All 16 identified result/summary files extracted; 10,419 members enumerated | The 2.9 GB archive is unnecessary for these conclusions |
| Authored frozen source | 20,590 source copies reduce to 2,963 unique identities: 2,939 recoverable from main Git blobs and 24 additional text records | Source/class freezes and compiled copies are unnecessary |
| Detailed GPU capture | Frame boundaries, final GPU outcomes and health fields independently reproduce 39 covered intervals out of 162, 43 unresolved queries and one imbalanced scope | CPU event trace and duplicate pending events are unnecessary |
| Old screenshots and videos | Prior analysis, numerical image summaries and explicit visual-review limits | No new pixel-equivalence or independent visual re-review claim depends on the originals |

The supporting collection contains 3,957 records in 82 data files, plus its catalog and integrity manifest (76.7 MB uncompressed). These are reports, attempts, source variants and condition maps, not that many independent experiments. Large native-option maps are retained once. Failed and inconclusive attempts were not filtered out.

## Conditions and limitations

Evidence level: cpu-integration for extraction, integrity checks, source recovery and isolated offline verification; source-inspected for historical experimental claims. No game, new shader benchmark or visual qualification was run. M3 Max/macOS, M1/Asahi and native Windows measurements remain separate, with their original packs, versions, resolutions and measurement definitions. Cached FPS endpoints are not frame-time distributions; incomplete GPU timing remains incomplete.

The inventory covers 185,689 regular files and 415 symlinks in the recorded old snapshot, including Git and generated working material. The extraction accounts for every file group and every entry in the 2,109-entry earlier document archive. It also inspected 1,243 Git report versions, source archives and the nested RTX archive. Keeping a report preserves its account and limits, not independent validation of its assertions. Unique quantitative ledger excerpts remain explicitly reported historical numbers. Operational diaries, reusable downloads and unchanged environment copies are deliberately not knowledge to retain.

Previously missing Mac artifacts and remote-only Windows captures were not recovered. Exact worlds, driver environments, compiled binaries and full raw temporal traces are intentionally omitted. This loses the ability to ask arbitrary new questions of those raw captures; it does not invalidate the scoped conclusions retained here. A future optimization must use new measurements, not claim old quality or hardware qualification.

## Decision

Retain the compact evidence and preserved implementation history. Retire the old working checkout and duplicate bulk archives after switching the workspace and checking that no new work appeared there. Do not delete the new repository's prepared runtime profiles, new benchmark evidence or small Git recovery bundles as part of that retirement. Extraction itself did not delete the old checkout or archive.

## Verification and evidence

Run `python3 results/historical-evidence-extraction/verify.py` from a full source clone. It checks retained-file hashes, exact source-report bytes, split-record reconstruction, option-map references, extra-source identities and key measurement reductions. Git-backed source recovery is independently checked by reading each mapped blob and comparing its SHA-256. The unchanged-shader figure regenerates from its six retained rows using `python3 results/asahi-null-calibration/figure.py`.

[Coverage and disposition data](result.json) sha256:86d9102c8acadcaf64c82187e8c4d386cd09cae2b6bcb300e9d6e6e0dace85d0

[Retained-file integrity manifest](../sources/history/files.json) sha256:1b3201b1c10fc498e9a3d193ced7163bb0a41d5bb86d0466021e885b504a03ba

[Authored-source recovery](source-recovery.json). [Extraction method](method.json). [Catalog](catalog.md).

## Correction

2026-11-12: Earlier preservation and handoff wording overstated how fully knowledge had been extracted. It retained the bulk snapshot and still depended on it. This extraction replaces that dependency with repository-owned supporting records and explicitly permits disposal of superseded bulk outputs. It does not upgrade historical results to current qualification.

[Isolated verification and check coverage](verification.json) sha256:af04506d17127ebc5dc9fd26c85131149bf6495e512c4411495b2c5781c3e99a

[Final old-checkout byte audit](old-checkout-audit.json) sha256:6bde95fdcbb69606383f12bf5d9ac255aaed8b150bceb58fb229053dd233c00e

[Fresh GitHub clone validation](published-validation.json) sha256:900ec2fc6e4639950ba7cb98dac57f1aac81191ee9521facecb03b343e6bcbe5

[Final legacy Git recovery](git-recovery.json) sha256:200a0c139f4cc4676063723149f8fbb2e050b2044768978985da53d30af37a3b

2026-11-12 storage closeout: the verified bulk history snapshot has been deleted.
The retired clean checkout was moved into ignored `.local/pending-delete/`, pruned
of extracted generated outputs and marked ready for deletion. A later scan found
four separate older home-directory copies; their [supplementary extraction](retirement.md)
retains additional reports, observations and source variants. Its limits apply
alongside this result. Current project paths and cleanup live in
[the result specification](../../docs/results.md#where-evidence-lives).
