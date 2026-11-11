# Locally available historical evidence is preserved independently of the old checkout

Readable candidate and investigation results now point to compact local sources or a separately named artifact snapshot. The snapshot includes the complete original checkout, Git history, ignored experiments, run freezes, dependencies and locally available inputs. The earlier document and retired-code archives are also preserved and indexed.

## Conditions and limitations

Evidence level: cpu-integration. 185689 regular files were verified by SHA256 and 415 symlinks by target text. No copied symlink resolves into the old checkout. External system-tool links remain explicit in the receipt. This establishes local byte preservation, not universal experimental reproducibility. Remote-only Windows captures and previously missing inputs are not recovered. The snapshot shares storage on the same disk and is not a disaster-recovery backup.

## Decision

Retain this evidence store as the backing archive for the new knowledge index. Future development can use the new repository and its artifact store without browsing the old checkout. Do not infer permission to delete remote data or claim that every historical artifact ever mentioned exists here.

## Evidence and source

[Preservation receipt and coverage inventory](../sources/historical-preservation.json) sha256:f7aa48ef4394be8598d7b6a32db6a3133bb21ae496249a2f6e38b5926821f8a9

The receipt identifies the original source revision, full per-file manifest, earlier archives, external links and remaining limitations. See [artifact access](../../docs/results.md#historical-artifact-access) for relocation rules. Original reports retain their original paths as provenance; those paths are interpreted beneath the preserved snapshot.

## Correction

2026-11-10: The initial readable import omitted the legacy 61-control MacBook Pro program even though its archive bytes were preserved. The legacy-mac-61-controls catalog and two companion results now expose its 549 family/control records and later local impact findings. Preservation completeness must not be described as complete semantic review of every archived investigation.


## Knowledge coverage

Byte preservation and readable knowledge coverage are separate claims. The [earlier archive index](../sources/earlier-archive-index.json) contains 2,109 entries: 1,007 history snapshots, 607 evaluation records, 429 top-level documents, 35 research records, and 31 other entries. These counts describe locations, not unique investigations or verified conclusions.

| Material | Readable coverage | Remaining boundary |
| --- | --- | --- |
| Later sixteen-candidate campaigns, repeats and combinations | Individual candidate results, combination/repeatability results and platform screens | Historical scope only; no current qualification |
| Legacy Mac 61-control program | Full control catalog, native CPU profiles and fifteen-item backlog consolidation | Applicability is not performance; underlying reports were not all individually adjudicated |
| Measurement and rendering-contract lessons | Profiling lessons, early rendering lessons and explicit sampler correction | Selected lessons, not an exhaustive semantic audit |
| October in-process and optional-stack controls | [Profiling and benchmark-design lessons](../profiling-and-benchmark-design-lessons/result.md), added 2026-11-11 | Missing runtime provenance and limited workloads remain explicit |
| Older loader/version ports, pack-specific trials and research reports | Searchable archive index and preserved originals | Not systematically mapped to result pages or exclusion decisions; consult relevant reports before reviving a matching direction |
| Historical task diaries and old launch instructions | Preserved as reference, deliberately not restored as current guidance | They do not authorize work or define the maintained system |
| Remote-only or previously missing raw inputs | Disclosed in preservation limitations | Not recovered |

The current repository and its retained artifact store suffice to access the locally preserved material without the old checkout. They do not establish that every useful historical lesson has already been extracted. Before selecting a specific optimization family or importing an old mechanism, benchmark preparation should check its matching archive records and adopt any additional durable conclusion. No claim of exhaustive semantic completeness or permission to delete unclassified evidence follows.
