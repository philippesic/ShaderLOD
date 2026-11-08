# Retained RTX5080 evidence

Raw files are deliberately outside Git. Compact measurements, source identities,
plans, scripts, findings and verification receipts are tracked. Native Windows
reports remain under `C:\ShaderLODBench\ShaderLOD\dev\experiments\rtx5080-four-target-audit-20261106`;
the canonical off-host artifact root is this directory on Asahi.

The [terminal audit](terminal-audit-v7.json) verifies all217 operations released,
restored and reset, both managed profiles equal their canonical sources, and zero
Minecraft clients. The final owned scheduled task was removed after checking its
exact helper/request identity, Ready state and successful exit. No other project's
data, NVIDIA cache, personal profile or shared configuration was cleaned.

The full original shader archives/options and managed/canonical client inputs are
retained. Timing results are mirrored in individual report directories, including
raw per-frame vectors, guards, before/after state, operations and build bindings.
The [compact timing summary](timing-summary-v4.json) and CSV/HTML/PNG/SVG can be
regenerated with `analyze.py` and `render-report.py` using the existing chart-env.

Large terminal duplicates were preserved before native retirement:

| Evidence | Retained off-host artifact / receipt |
|---|---|
| First178 world-restore copies | `terminal-world-copies-v1-content.zip`; [migration](terminal-world-copies-v1-migration.json). This replaces the old TAR location while retaining all18512 file paths/bytes with62 unique objects. |
| Terminal screenshots and before/after/restoration world copies | `terminal-heavy-v2.tar.gz`; [receipt](terminal-heavy-v2-verification.json). |
| Terminal native graph exports | `terminal-graphs-v3.zip`; [receipt](terminal-graphs-v3-verification.json). |
| Six later restoration copies | `terminal-world-copies-v4.zip`; [receipt](terminal-world-copies-v4-verification.json). |
| Terminal raw windows and control receipts | `terminal-timing-raw-v7.zip`; [receipt](terminal-timing-raw-v7-verification.json). Individual extracted timing mirrors also remain. |
| Later terminal screenshots/world copies | `terminal-heavy-v8.zip`, `terminal-heavy-v9.zip`; their tracked verification receipts. |
| Nine historical source/class freezes | `frozen/*.zip`; [native duplicate retirement](native-freeze-retirement.json). Native freeze ZIPs were removed only after rehashing both copies; all nine remain off-host. Never reuse an old freeze tag/receipt. |
| Redundant installed-tool/bootstrap download archives | `setup-archives/jdk21.zip`, `setup-archives/runtime-inputs.tar.gz`; [receipt](native-setup-archives-preservation.json). Installed JDK, canonical profiles and dependency caches remain native. |

Content-addressed ZIPs contain `PRESERVATION-MANIFEST.json` with every original
file path, byte length and SHA256, plus `objects/<sha256>` entries. To recover,
write each object's bytes to its recorded path as an independent file in a fresh
destination. Do not hardlink mutable recovery worlds. Original operation profile
maps retain input directory/mode metadata. TAR evidence retains its usual paths.
The source inventories and every archived file were checked again before native
retirement; archive creation alone was insufficient permission to delete originals.

Quality raw PNGs remain byte-exact in verified per-case/per-arm ZIPs. Host-side
`*-preservation.json` receipts identify the authoritative local/native location.
MakeUp G01's full successful capture and three earlier incomplete attempts remain
retained. Fresh BSL G02 is mirrored locally; most later complete sequences remain
under native `C:\ShaderLODBench\quality-archives`. Small temporary rehydrations for
pixel analysis were retired only while the verified off-host copy remained.
All2,178 successful images were decoded/hashed and all121 paired slots per case
compared; selected overview/unscaled crops and quantitative outputs are retained.
[Quality scope and numbers](quality-summary.json) define the acceptance limits.

Redundant local transport ZIPs were removed only after every extracted file still
matched its original independently verified transfer manifest. Their receipts and
extracted evidence remain; [retirement](transport-retirement.json) records the
initial batch. Logical byte totals and observed free-space changes are separate:
delayed release or concurrent activity can affect a measured free-space delta.
They are not summed as exclusive physical reclamation. The maintained32GiB reserve
was preserved; native free space was about33.19GiB after the completed campaign.

Authored phase helpers refuse replay/fresh-output collisions. V5 capture failures,
the V6 pre-launch headroom stop and the original waiting coordinator's failure
remain in logs. The single tail check used an explicit5GiB planning allowance
based on the measured full4K MakeUp compression/one-arm archival footprint; the
32GiB reserve was unchanged. It completed before the two final scored diagnostics.
