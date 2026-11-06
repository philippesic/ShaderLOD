# November 5 audit and direction snapshot

Historical detail; [STATUS](STATUS.md) owns current work. This snapshot preserves
the existing scope and protocol without changing benchmark rules.

# Current state

Updated: 2026-11-04. This section is the authoritative development direction; historical closures, exclusions and pauses below do not close or exclude the current audit.

## V1 scope

Support **Complementary Reimagined, Complementary Unbound, BSL and MakeUp Ultra Fast**. These are four packs / three families: Complementary's two styles share a family but both profiles need testing. Other packs are outside the initial support commitment; their existing measurements remain useful historical evidence.

Build a plug-and-play Iris/Sodium mod using shared renderer/LOD mechanisms across the supported packs and GPU vendors. Small, unobtrusive quality tradeoffs are acceptable; preserve native screen resolution and AA, and assess motion, shimmer, ghosting and transitions. Pack-specific and vendor-specific implementations remain outside v1 scope. Selecting four validation targets does not authorize four separate optimization implementations.

## Current task: validate the 16 candidates across shaders and machines

All **G01–G10, C01–C02 and N01–N04** are included in the audit. This is a new applicability/performance validation task, not a resumption of the old per-machine optimization campaign or a commitment to ship all 16.

1. Screen each candidate for a credible general mechanism, potential saving, quality risk and maintenance cost. Consolidate overlapping ideas; defer niche/vendor-specific approaches with reasons, including C02 if its only viable implementation remains NVIDIA-specific.
2. Repair viable general implementations, activation/diagnostic failures and test setup as needed to obtain trustworthy measurements. Preserve correctness guards; extending support requires evidence, not simply removing a platform restriction. First establish unmodified native baselines on the four targets, including the previously failing pinned Complementary startup.
3. Before substantial engineering, screen supported shader families, representative scenes/settings and motion on at least two GPU families. Include cases where the optimization cannot help; measure fallback overhead. Use maintained benchmark commands, matched native controls, noise checks and repeated/order-balanced measurements for promising results.
4. Report applicability, actual activation/work evidence, gains/regressions, visual findings and a v1 disposition for each candidate. Keep unsupported, deferred, blocked and invalid cases distinct from measured zero or negative gain. Only surviving broadly useful candidates proceed to production engineering; default enablement remains unqualified.

Starting evidence: the [RTX 2080 Ti pass](../dev/experiments/rtx2080ti-individuals-20261104/results.json) has seven valid single-bracket timings and nine unmeasured variants. Each goal used one chosen pack/scene; this does not establish cross-shader coverage. Historical Bliss/Photon/Solas results do not qualify the new four-pack support scope.

**Asahi four-target audit complete (2026-11-05):** all64 goal/target cells have
explicit dispositions;36 have valid timing evidence. Original packs, native
2560×1600 and AA were preserved. Each target has two original-only baseline
processes with three20s windows after120s conditioning, plus ABBA/BAAB identical-pack
controls. Matched candidate controls keep ShaderLOD installed and Off; differences
from separate original-only baselines are not a causal mod-overhead estimate.

Two local MakeUp results repeated across four confirmation processes each
(twoABBA/twoBAAB): G01 framebuffer-copy/storage removal averaged **8.14% less frame
time**, effect sampleSD1.78percentage points; G06 exact AO attenuation/center-depth
reuse averaged **4.14%**, SD1.64points. These are separate effects, not additive.
N02/N03's MakeUp opportunity is consolidated with G01. Neither is newly qualified
for default enablement or broad shared-renderer production work. G06's other
three targets use a distinct half-resolution AO route and were slower.

Other runnable routes showed higher frame times or no established useful gain.
N01 Unbound's discovery gain did not repeat (four confirmations:−0.06%, SD1.54points).
G04 and G10 discoveries also failed confirmation. Enabled native-PBR Complementary
height caches were much slower; their RP_MODE1 no-POM fallback cases remain separate.
C02 lacks its required NV capabilities here; BSL G03 is unsupported by the current
minimal stack. Source-only lifetime/geometry and missing-general-route cases are
held with reasons, without invented zero-gain bars or pack/vendor bypasses.

Shared setup repairs include the development Iris sampler index namespace,
ordinary controller/observer classification, and strict observed D24 storage
recognition reused by AO/depth/reflection contracts. Actual-window producer,
consumer and permission counters distinguish working paths from guarded fallback.
The bounded quality recorder now flushes each raw lossless PNG on its writer
thread; two successful fresh retries retain the same6fps/121-image/zero-missed-slot
requirements. Failed capture attempts remain invalid. Contact sheets and native
frame spot checks found no gross defect in reviewed MakeUp gallery/water/honey
cases; faster shimmer, brief ghosting and all LOD transitions remain unqualified.

[Interactive charts and all64 dispositions](../dev/experiments/asahi-four-target-audit-20261104/interactive-charts.html),
[standalone charts](../dev/experiments/asahi-four-target-audit-20261104/charts.html),
[complete results](../dev/experiments/asahi-four-target-audit-20261104/results.json),
[within-window variance](../dev/experiments/asahi-four-target-audit-20261104/frame-variance.csv),
[between-process variance](../dev/experiments/asahi-four-target-audit-20261104/between-run-variance.csv)
and [work evidence](../dev/experiments/asahi-four-target-audit-20261104/work-evidence.json)
keep scenes, phases, implementations and activation qualifications separate.
SD bars describe variability, not confidence intervals; single-process between-run
variance is unavailable. The [terminal audit](../dev/experiments/asahi-four-target-audit-20261104/terminal-audit.json)
verifies122 operations terminal/restored/released, both reused slots matching
canonical inputs and zero clients. Recovery supplements preserve the original
failed startup summaries. [Checkpoint](../dev/experiments/asahi-four-target-audit-20261104/checkpoint.json), [conclusions](../dev/experiments/asahi-four-target-audit-20261104/audit-conclusions.json) and [retained storage](../dev/experiments/asahi-four-target-audit-20261104/storage-inventory.json).

This completes the authorized Asahi pass. The cross-machine screening requirement
remains open: no2080Ti tests were launched during this pass, and its availability
must be renewed before use. A second GPU family, broad DH/PBR coverage, negligible
fallback overhead, full temporal acceptance and net setup economics remain
unqualified. Prior historical RTX measurements do not fill this four-target matrix.

## Hardware and scheduling

Initial cross-machine screening can use Apple/Asahi and NVIDIA when available; AMD and other untested platforms remain unqualified. The latest user-directed handoff gave the RTX 2080 Ti exclusively to another task: obtain fresh availability before launching GPU work there. The RTX 5080 was last reported unavailable. These are last-known allocation notes, not a live process census. Continue independent source/harness work while hardware is unavailable; this documentation update does not itself launch tests.

## Continuing and revisiting work

Use [candidate reference](BENCHMARK_CANDIDATES.md) for supported routes and
[experiment history](EXPERIMENTS.md) for prior attempts. The older campaign
[status snapshot](STATUS_HISTORY_20261105.md) is preserved separately.
At a meaningful milestone update this record with the current objective, latest
result and limits, active operation/evidence path, and next decision/action.
Link machine receipts rather than copying process counters or every progress update.
A dated ownership note must be checked against the live operation before reuse.
