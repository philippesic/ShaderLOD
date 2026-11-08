# RTX5080 audit working state

Active and incomplete. The user requests all16 candidates/four targets, hardware
and GL repairs, work/correctness evidence, independent timings, variance and charts.
Provisional64-row charts exist; comprehensive optimization-benefit qualification is incomplete.
Canonical source is /home/philip/ShaderLOD on branch rtx5080-audit; native Windows
gets verified runtime inputs. No2080Ti allocation is in use.

## Current evidence — 2026-11-07

The main97-operation continuation finishes85 VALID/12 INVALID, all restored.
All eight Runtime-V4 hardware admission checks pass/restored after the recipe pin
and correctGL4.6 repairs. All24 follow-up comparisons/controls finish VALID and
restored. Automatic collection verifies456 transferred files and generates the
variance data and charts. There is no active performance acquisition.

The current descriptive reduction contains200 reports,175 valid and40 measured
primary cells, each with two independent processes and opposite comparison order.
22 rows now have explicit source-held/no-certified-route dispositions; two MakeUp N02/N03 rows share G01
and receive no additive or independent gain. Missing rows have explicit review
states and no fabricated timing bars. Actual contexts/cases/implementation pins
remain separate. Old MakeUp admission code cannot contribute to repaired cohorts.

MakeUp G01 mean frame-time reduction is3.244% (process SD0.041 percentage points),
BSL G02 is3.953% (SD0.119pp), and C02 bloom is2.244%/2.005%/1.770% for BSL/Reimagined/
Unbound (SD0.112/0.048/0.077pp). BSL G02 is the public singleton request, not the
separate private original-derived prototype. MakeUp N04's1.120% (SD0.272pp) is a
small preliminary screen. Several other routes have substantial regressions;
none has yet been established to be a hardware bug or irreducible cost. No final
quality/benefit qualification follows from these means.

`run-quality-v5.py` attempted eight maintained untimed checks: two separately
labeled JFR diagnostics, MakeUp G01, BSL G02, three C02 packs and MakeUp N04.
Both profile checks pass/restored; MakeUp G01 captures all363 requested frames.
BSL G02 and BSL C02 are INVALID/restored because a sequence is incomplete.
The G02 native arm writes119 frames and misses slots107/108 during a render
stall, with no writer pressure or errors. It is not a quality pass. A fresh retry
must retain121-frame completeness and preserve the failed attempt. Reimagined C02 is also incomplete; all three failures are missed render slots,
not write errors. The batch stopped at capacity before Unbound/N04. The failed
attempts and full raw bytes remain preserved. `run-quality-v6.py` now runs five
fresh checks after a recorder repair: BSL G02, the three C02 packs and MakeUp N04.
Both fresh BSL checks now finish VALID/restored with363/363 captures each; the
Complementary/N04 checks continue. The ignored quality-v6-status.json/outcomes.json/log
identify the exact live phase.
No source/classes used by active native checks may be changed.

Recorder repair: benchmark camera catch-up ticks and full image leases hold the
logical path until a render/write slot is available. All121 frames remain required;
real motion wall duration can grow, while shader clocks/history continue. Lossless
PNG level1 reduces disk use; the native standalone transport test round-trips all
RGBA pixels but takes about0.25s/image, so writer holds are necessary. Three Java
clock regression tests, two Python admission tests and dev compilation pass on
both hosts. Existing timing inputs are unchanged by this untimed recorder repair.

JFR diagnostics are outside performance runs. The initial15s recordings overlap
post-window observer reload; unfiltered samples cannot isolate candidate costs.
They do not establish a dominant Java hotspot or any GPU cost explanation.
Filtered exact candidate-window evidence is now retained in
cpu-profile-filtered-v5.json and REGRESSION_DIAGNOSIS.md. Sparse samples establish
no dominant Java hotspot or GPU cost explanation.

## Timing interpretation and artifacts

All four main baselines are original-only/minimal/native3.2, two independent
processes each with120s conditioning and three20s windows. Native3840×2160/AA is
preserved. Separate GL4.6 and BSL optimized-stack cohorts are not pooled with these
cards. Rows use their actual required context and matched in-process control.
Actual3.2/4.3/4.5/4.6 startup/restoration checks pass where requested.

`analyze.py` generates results.json, frame-variance.csv, between-run-variance.csv,
standalone candidate-frametimes.png/.svg and charts.html. `render-report.py`
generates interactive-charts.html with all64 matrix rows and exact timing data.
Within-window frame jitter, window-mean variation and equal-weight independent
process variation stay separate. No IID-frame confidence interval is claimed.
The40 measured rows include declared gpu-authored and native-rp3-pbr cases;
previous primary-case selection wrongly omitted these three rows and is repaired.

`analyze-quality-archive.py` processes full-resolution preserved PNG bytes without
extracting all raw frames. RGB8 differences require actual-pose and native-return
variation review; they are not HDR equivalence, all-scene or timing evidence.
Capturing a complete sequence alone does not establish acceptable quality.
MakeUp G01 RGB8 diagnostics cover all121 paired slots. Candidate/native MAE4.14
levels exceeds native/return2.45; their shader animation clocks differ substantially.
Reviewed contact sheets show differently positioned clouds, with no obvious gross
scene loss, but this is not deterministic equivalence or final visual qualification.

## Repairs and recovery

The first G01 attempt stopped on transient Windows ownership-file access; release
succeeded, but final reset refused an untracked generated FML config backup.
Bounded ownership read retries and predeclared numbered FML backups now pass101
benchmark and30 lifecycle Python tests on both hosts. Explicit maintained recovery
retains the old backup bytes, verifies full reset and replays no measurements.

The first C01 GPU check refused its depth-range/polygon guard and restored fully.
The guard incorrectly required two polygon enums even in a core profile. It now
uses the actual profile mask, checks one core or both compatibility modes, retains
strict depth/raster refusal and exposes last observed state.22 C01 Java tests plus
compilation pass on both hosts. The fresh native check observes core mask1,
[0,1] depth and[6914,0] polygon output, confirming the untouched second slot.
Actual candidate proxy-query progress is positive with zero CPU result reads.
The C01 GPU retry and primitive check both finish VALID/restored/reset/released.
The gallery witness suppresses zero native primitives in32 frames in each of
GPU no-wait, GPU wait and CPU-decision modes; it establishes no geometry savings
in that bounded scene. Natural opportunity search and scored timings remain
pending. See [C01 repair](C01_PORT.md).

Native source/classes were verified and archived before each replacement: nine
tracked freeze receipts identify the complete bindings and retained ZIPs. All
nine ZIPs are also hash-verified under Asahi's ignored frozen/ directory. Terminal report image/graph bytes remain preserved in verified archives; logs and
compact native report evidence remain in place. Timing-only transfers are
independently hash-verified and do not authorize deleting native originals.

## Capacity and remaining work

The verified world-restore archive preserves10.35GiB of source recovery copies and
reclaims10.37GiB. A second independently verified archive preserves12.59GiB of
terminal report PNG/world-before/world-after/world-restore evidence and retires
only those copies, leaving52.74GiB native free before quality capture. Both are
ShaderLOD-only; live/canonical worlds and report metadata remain. Native graph
evidence is now preserved off-host in a separately verified content-addressed
archive:994 terminal roots,3.61GiB logical bytes,24MiB archive; reclaim4.12GiB
including allocation overhead. Every file is rehashed before retirement.
See terminal-world-copies-v1-verification.json and terminal-heavy-v2-verification.json.
The first world-copy archive is migrated to a verified content-addressed ZIP
preserving all18512 paths with62 unique files, recovering3.20GiB on Asahi. Its
linked migration receipt supersedes the old retired TAR location. Six new terminal
world copies are likewise preserved/reclaimed separately.
Raw motion bytes remain recoverable in per-case/per-arm ZIPs, kept native or mirrored
according to measured capacity. Failed captures are preserved too.

Two OpenGL4.6 G03 diagnostic comparisons are prepared for Complementary, separately
from native3.2 primary timings. run-modern-regression-v7.py automatically waits for
the full quality/archive phase before launching any scored window. Material
context changes need fresh confirmation before primary promotion.

Next: finish active quality checks, inspect
pixels and actual poses, investigate regressions and remaining source opportunities,
classify all64 cells, update final charts, and audit restoration/owned cleanup.
The campaign remains active and incomplete. No full-deliverable ETA is established.

Further owned storage preservation: terminal timing windows/receipts are archived
off-host and every file rehashed before native retirement (2.49GiB reclaimed).
A small final terminal PNG/world-copy group likewise reclaims0.63GiB. Report
metadata/build bindings, extracted timing mirrors, source inputs and raw quality
archives remain. Retiring17 local transport ZIPs recovers0.48GiB only after every
extracted byte re-verifies against its original transfer manifest. No other
projects/shared caches are touched.
