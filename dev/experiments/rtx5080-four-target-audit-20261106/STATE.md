# RTX5080 audit working state

Active and incomplete. The user requests all16 candidates/four targets, hardware
and GL repairs, work/correctness evidence, independent timings, variance and charts.
No comprehensive optimization-benefit conclusion or final64-cell chart exists yet.
Canonical source is /home/philip/ShaderLOD on branch rtx5080-audit; native Windows
gets verified runtime inputs. No2080Ti allocation is in use.

## Current native operation

Task ShaderLOD-RTX5080-Scoring-V1-Block1 is active in console Session1, request
C:\ShaderLODBench\requests\queue-scoring-v1-block1.json, ledger
queue-scoring-v1-block1.json. It contains59 maintained operations. The four
additional Complementary GL4.6 baselines complete valid/restored. At the latest
inspection34 operations finished; a boundary pause is requested after the active
Unbound native-PBR G09 run so four first-pass failures can be repaired. Do not
replace native sources/classes until the actual ledger and exact release are
verified. The source used by the live batch remains unchanged.

The initial31 finished included27 valid and four invalid, all restored. Two
failures used native3.2 where their backend requires3.3/4.1; two remaining shader
gates excluded Windows. Canonical fixes and six functional checks are prepared
and locally tested, not yet deployed. See [repair evidence](FIRST_PASS_REPAIRS.md).
Old MakeUp G06 timings need separation from the corrected entry implementation.
The old failed Reimagined4.6 r1 remains invalid/recovered evidence; its fresh retry
and independent repeat both complete successfully. The task has no trigger and
its outer24h limit does not close the campaign.

V3 completed six valid, restored/released functional operations: repaired C01 GPU
admission, its separate primitive witness, and both Complementary VRS checks in
real4.5/4.6. The following Reimagined GL4.6 baseline failed on ownership-file
access before any scored window. The exact cause was reproduced: CPython's CRT
file open raises PermissionError(errno13) without winerror, bypassing the earlier
Windows-number-only retry. The minimal fix includes this error shape in the same
bounded3s read/publication retry.103 benchmark tests pass on both hosts, including
a real native exclusive-handle/read-release regression fixture. Explicit recovery
preserves conflicting owned inputs and verifies configuration/world reset/release
without replay. See [Windows IO evidence](WINDOWS_IO.md).

## Measurements and proof retained

- All four native/minimal original-only baselines have two independent processes,
  each120s-conditioned with three20s windows. Native viewport3840×2160/AA is
  preserved. Main mod is absent for these baselines. Frame variance, window-mean
  variance and equal-weight process variance remain separate.
- Separate BSL optimized-stack/native and minimal/GL4.6 baselines each have two
  processes. Do not pool these with the primary native/minimal baseline cards.
  Native3.2 and actual4.3/4.5/4.6 original startup/restoration checks pass.
- BSL G03 has one valid optimized-stack/native ABBA score. Apparent reduction
  −0.21459% is unresolved at n1. Its rewritten actual CUTOUT submissions are
  proven untimed, with observer disabled during scoring. Independent BAAB remains
  required. See [G03 repair/proof](G03_PORT.md).
- MakeUp G03 functional admission passes, including actual linked rewritten CUTOUT
  submissions and restoration. It has no scored repeat yet.
- MakeUp G01 has its first valid ABBA process: matched native/candidate means
  2.0292553/1.9628349ms, apparent reduction3.27314% at n1. Actual final swap copies
  fall from six to four and return to six. This is a promising screen, pending
  independent BAAB, null controls and bounded quality. Frame/window variance and
  limitations are in [G01 evidence](G01_PORT.md). N02/N03 share this route without
  independent or additive credit.
- BSL C02 functional checks pass on actual4.5 and4.6 with NV capabilities,
  one qualified bloom program and advancing actual activation/restoration counts.
  Controls advance neither. This is no performance or quality result. Both
  Complementary native ports also pass4.5/4.6 functional validation. Performance
  and quality remain pending. See [C02 scope](C02_PORT.md).

The descriptive analyzer reads the maintained raw frame samples and keeps
context/stack/options/camera cohorts separate. Compact baseline-progress.json
includes BSL GL4.6 as a separate cohort. Every failed attempt is retained; no
missing or refused cell receives an invented timing bar. The observed/port and
comparison manifests identify acquisition; comparison blocks remain pending.

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

Native source/classes were verified and archived before each replacement: four
tracked freeze receipts identify the complete bindings and retained ZIPs. All
four ZIPs are also hash-verified under Asahi's ignored frozen/ directory. Full
native report images/graphs/logs remain preserved; timing-only transfers are
independently hash-verified and do not authorize deleting native originals.

## Capacity and remaining work

Latest C: observation is59.3GiB free. This is sufficient above the32GiB reserve;
80–100GiB was conservative headroom for accumulating large captures. Process
4K motion sequentially (about11.2GiB raw per363-frame capture), then compress and
transfer verified evidence. Cleanup remains ShaderLOD-only. NVIDIA caches,
other-project checkpoints, personal profiles and shared settings are untouched.
The earlier capacity increase is not attributed to our cleanup.

Next: complete both opposite-order comparison blocks and matched null controls,
repair concrete runtime failures, investigate slow
or inactive routes, acquire bounded quality for plausible survivors, classify
all64 cells with evidence, publish variance charts and audit final restoration.
A failed check or this checkpoint does not close the campaign. Source-only/lifetime
reviews remain in [current review](SOURCE_REVIEW.md), distinct from measurements.
