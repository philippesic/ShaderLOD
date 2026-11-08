# RTX regression diagnosis

The scoped investigation is complete; no generic performance fix is established by this record.

The repaired timings have two independent processes per measured primary cell.
The [compact work audit](regression-work-summary.json) compares finite untimed
native/candidate packets from valid regressing comparisons, retaining each report
separately. It excludes a partial next frame, normalizes storage descriptors without
comparing unrelated GL object names, and does not enumerate every custom mod
allocation or infer GPU durations. Full packets remain in mirrored summaries.

Complementary G02 actually omits two mip-return events across two completed
frames; observed native draw/copy counts and colour image storage remain unchanged.
Targets12/13 already exist in the original graph. They are not evidence of accidental
adapter allocations. Its slow mean is not an inactive-mip request in this packet.
Other inspected G03/G05/G07/G09 regression packets likewise have unchanged observed
native event counts and colour-storage layouts. That does not prove absence of
shader/compiler cost, custom resource cost or CPU validation overhead.

G06 is different: the non-MakeUp AO routes add one observed quad per frame and
target10 at native3840x2160 in both banks. BSL uses RG32F; Complementary uses RGBA32F.
The source explicitly specifies a full-size allocation with a half-size viewport
in its lower-left rectangle. This is an intentional prototype contract, not a
failed half-size allocation request. AO producers clear to a sentinel and consumers
validate sampled fields/producer state before replacing native AO. Deleting the
clear or shrinking storage would require a new complete coverage/lifetime/coordinate
proof; the current allocation cannot simply be relabeled half-size memory. The
extra draw/storage are real costs retained in end-to-end frame times. Source:
`patches/adapters/{bsl-shaders,complementary-reimagined,complementary-unbound}.json`
and their AO producer/reconstruction helpers.

Two separate untimed Reimagined JFR checks sample the byte-identical native alias
and actual early-alpha candidate. The [filtered samples](cpu-profile-filtered-v5.json)
use the approximately3s candidate windows only, excluding observer reload. Sample
types retain their different sampling periods. The native/candidate windows contain
32/31 native-method samples, of which29/22 include glfwSwapBuffers; execution-sample
counts are11/6. Sparse project frames do not establish a dominant Java hotspot.
Neither sample ratios nor a swap stack are a GPU utilization or GPU-cost estimate.
The initial unfiltered15s aggregates included reload work and cannot isolate these
candidate costs.

The maintained window_start guard rejects active mip/graph/copy observers and
detailed CPU/GPU profilers. Actual-window runtime work counters remain distinct
from intrusive untimed observation. No JFR was attached during performance tests.
Original/native settings, AA, viewport, selected native PBR resources and source
admission are retained; passing a source/permission guard is not a saved-query count.

Two further comparisons complete VALID/restored for Complementary G03 in explicit OpenGL4.6,
with matched in-process native controls. These are separate diagnostic contexts,
not pooled replacements for native3.2 primary timings. The orchestration waits for
all quality operations and their archival work to finish before starting scored
windows. The candidates remain6.989%/10.806% slower on Reimagined/Unbound, close to the
primary native-context regressions; context selection does not explain the large
loss. These n1 diagnostics are not pooled into primary n2 estimates. The
572-window chronological audit flags no2% quarter/pair drift and trims no samples.
This narrows simple setup explanations without proving an optimal implementation
or isolating every regression bottleneck.
