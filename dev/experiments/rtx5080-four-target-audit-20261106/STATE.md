# RTX5080 audit state

The current screening audit is complete. No active benchmark, quality capture,
profiler or scheduled benchmark task remains. The canonical source is
/home/philip/ShaderLOD on branch rtx5080-audit; source/checkpoints go to user-owned
origin. No2080Ti allocation was used.

[Final report](REPORT.md) owns the results/limits. [Conclusions](audit-conclusions.json),
[compact timings](timing-summary-v4.json), [source reasons](source-review-findings.json)
and [terminal audit](terminal-audit-v7.json) are tracked. Raw evidence locations and
backup migrations are in [ARTIFACTS](ARTIFACTS.md); ignored does not mean disposable.

All64 cells are classified:40 primary n2 measurements,22 source-held/no-certified-route
rows, two shared G01 routes. Main analysis contains202 timing/admission reports,
177 valid/25 invalid. The terminal census covers217 native operations,187 valid/
30 invalid, all restored/reset/released, both slots canonical and zero clients.
Failed attempts and older implementation cohorts remain separate.

MakeUp G01 and BSL G02 show scoped reductions3.244%/3.953%, process SD0.041/0.119pp.
C02 gives2.244%/2.005%/1.770% over BSL/Reimagined/Unbound with NVIDIA/quality scope.
MakeUp N04's1.120% (SD0.272pp) is a small sky-only observation. Most other routes
are slower/inconclusive. No shared v1/default optimization is qualified.

All24 repaired comparisons/controls and eight preceding functional checks pass.
Two extra real4.6 G03 diagnostics remain6.99%/10.81% slower on Complementary; their
n1 estimates stay separate from primary n2 native-context data. The572-window
chronological audit flags no exploratory2% quarter/pair drift; no samples are
trimmed. [Regression investigation](REGRESSION_DIAGNOSIS.md) distinguishes actual
work, intentional G06 storage/draw costs and unresolved cost attribution.

All six planned quality sequences complete363 frames each. All2,178 successful
PNGs were decoded/hashed and121 paired slots per case compared; selected images
and unscaled crops were reviewed. No gross defect was seen. This is bounded RGB8
screening, not universal HDR/motion/DH/PBR acceptance. Three earlier incomplete
sequences remain invalid with raw evidence. V6's logical clock holds for rendering/
writer capacity; wall duration can grow while shader clocks/history remain live.

The first V6 queue completed four checks, then stopped before the last launch at
its conservative6GiB headroom check. Verified retirement of redundant setup/freeze
archives and later terminal copies permitted a fresh single tail check using the
measured5GiB MakeUp allowance, preserving the32GiB reserve. The tail and two modern
comparisons pass. Earlier status/log files retain those phase outcomes; the final
report/audit supersedes their historical "analysis pending" stage labels.

All nine source-freeze ZIPs remain verified off-host; redundant native copies are
retired with a tracked receipt. World-copy archive migration, terminal graph/raw
window preservation and transport retirement retain recoverable evidence. Observed
free-space deltas are not exclusive reclamation estimates. No other projects,
NVIDIA cache, personal profile or shared settings were cleaned.

Remaining limits concern held mechanisms, hardware-optimal variants, precise cost
attribution, broad compatibility/temporal quality and production/default work.
They are not silently classified as zero gain or missing NVIDIA capability. Renew
hardware availability before a new campaign; do not replay old phase helpers.
