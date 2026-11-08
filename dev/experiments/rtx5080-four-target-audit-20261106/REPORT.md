# RTX5080 four-target audit

The current screening audit is complete. Most routes are neutral, slower or held for missing proof. The clearest local reductions are MakeUp G01 copy omission (3.24%) and BSL G02 mip omission (3.95%). NVIDIA bloom VRS reduces means by1.77–2.24% over three packs, with a quality tradeoff and vendor-specific scope. No broadly useful shared v1/default optimization qualified.

All64 cells have dispositions:40 have two independent primary timing processes,22 have source-held/no-certified-route reasons, and two share G01 without additive credit. There are202 analyzed timing/admission reports (177 valid); the native terminal audit covers all217 operations, including additional quality/diagnostic/failed attempts. All217 are terminal/restored/released, both managed profiles match canonical inputs, and no Minecraft clients remain.

[Interactive charts](interactive-charts.html) · [PNG](candidate-frametimes.png) · [SVG](candidate-frametimes.svg) · [Within-window data](frame-variance.csv) · [Between-process data](between-run-variance.csv) · [Compact tracked measurements](timing-summary-v4.json)

## Baselines and variability

Native Windows11 / RTX5080 / driver617.42 /3840×2160 / original shader options and AA. Each original-only baseline omits ShaderLOD, conditions for120s and records three20s windows per process. There are two processes in each of nine separately retained baseline cohorts. Candidate rows use matched original-pack controls with ShaderLOD installed and Off; C02 uses On with empty public requests/VRS Off. The separate baseline/card difference is not a causal mod-overhead estimate.

| Pack | Actual GL / stack | Process mean, ms | Process SD, ms | Process variance, ms² |
|---|---|---:|---:|---:|
| MakeUp | 4.6 / minimal | 2.024277 | 0.000107 | 1.1484705e-08 |
| MakeUp | 3.2 / minimal | 2.025619 | 0.000105 | 1.096039e-08 |
| Reimagined | 3.2 / minimal | 3.240654 | 0.005711 | 3.2614667e-05 |
| BSL | 3.2 / minimal | 2.862315 | 0.000746 | 5.5649037e-07 |
| Unbound | 3.2 / minimal | 3.377851 | 0.002523 | 6.3677118e-06 |
| BSL | 3.2 / optimized | 2.860606 | 0.001796 | 3.2270441e-06 |
| BSL | 4.6 / minimal | 2.874912 | 0.006764 | 4.5747433e-05 |
| Reimagined | 4.6 / minimal | 3.236306 | 0.000647 | 4.1809869e-07 |
| Unbound | 4.6 / minimal | 3.371517 | 0.001284 | 1.6496287e-06 |

Frame jitter, variation between windows and between independent processes remain separate. Sample SD/variance are descriptive, not confidence intervals or IID-frame inference. Native3.2 and real4.3/4.5/4.6 startup/admission were exercised where relevant. Rows retain their actual required context; contexts, source versions, scenes, PBR cases and stacks are not pooled.

## All sixteen candidates

Values below are mean frame-time reduction ± process sample SD in percentage points (n2). Positive means faster. The charts/data provide absolute native/candidate frame times and both variance levels. Held means unmeasured with a specific reason, not zero or unsupported hardware. N01 uses natural terrain; G09 includes native-PBR cases; C01 uses its declared authored GPU case. Other cases remain separate in the every-attempt view.

| Goal | MakeUp | BSL | Reimagined | Unbound |
|---|---:|---:|---:|---:|
| G01 | +3.24% ±0.04 | held | held | held |
| G02 | held | +3.95% ±0.12 | -5.67% ±0.23 | -10.01% ±0.23 |
| G03 | +0.03% ±0.04 | -0.07% ±0.21 | -7.32% ±0.08 | -11.08% ±0.07 |
| G04 | +0.26% ±0.06 | held | held | held |
| G05 | -3.79% ±0.02 | -18.23% ±0.06 | -8.70% ±0.30 | -13.02% ±0.16 |
| G06 | +0.05% ±0.02 | -13.90% ±0.11 | -10.87% ±0.01 | -17.58% ±0.17 |
| G07 | -3.25% ±0.09 | -17.12% ±0.56 | -7.48% ±0.18 | -11.06% ±0.03 |
| G08 | -0.39% ±0.09 | held | -1.12% ±0.27 | -5.26% ±0.02 |
| G09 | held | -8.30% ±0.06 | -16.51% ±0.00 | -11.55% ±0.04 |
| G10 | -0.21% ±0.11 | held | held | held |
| C01 | -0.01% ±0.04 | held | held | held |
| C02 | held | +2.24% ±0.11 | +2.01% ±0.05 | +1.77% ±0.08 |
| N01 | -0.08% ±0.07 | -0.05% ±0.10 | -0.29% ±0.08 | +0.31% ±0.08 |
| N02 | shared G01 | held | held | held |
| N03 | shared G01 | held | held | held |
| N04 | +1.12% ±0.27 | -2.02% ±0.72 | -1.23% ±0.40 | -5.16% ±0.02 |

[Each source-held reason and input pins](source-review-findings.json) · [Source review](SOURCE_REVIEW.md)

MakeUp N04 has a small sky-view-only1.12% reduction (SD0.27pp); fragment skip frequency and general benefit are not established. The two G01/G02 reductions are experimental observations, not pixel/HDR/default qualification. C02 remains outside shared cross-vendor v1 scope. Effects are independent observations and must not be added together.

## Repairs and regression checks

Repaired Windows ownership-file sharing/recovery, the core-profile C01 polygon-state guard, finite Windows source admission, authored/packaged helper drift and catalog pins, stale MakeUp recipe admission, and incorrect contexts for min-version paths. All eight focused hardware repair checks and all24 affected repeat/control operations pass/restored. Earlier failed and old-source attempts remain separate.

The additional OpenGL4.6 G03 comparisons remain6.99% slower on Reimagined and10.81% slower on Unbound. These n1 diagnostic contexts do not replace primary n2 estimates. All572 valid measured windows pass the exploratory2% chronological-quarter/candidate-window-pair review threshold; the largest first/last quarter change is1.094%. No samples are trimmed or warm-up-corrected. Observed graph/JFR checks find no simple extra-pass explanation outside the intentional G06 producer/target. They do not isolate every regression bottleneck or establish hardware-optimal implementations. [Detailed diagnosis](REGRESSION_DIAGNOSIS.md) · [Trend audit](time-trend-summary.json)

## Bounded image review

All2178 successful PNGs decoded/hashed and all121 paired slots per case compared in final RGB8. Selected frames0/60/120 reviewed visually; supplemental unscaled crops retained. Shader clocks/history and small rendered pose differences remain uncontrolled. RGB differences are descriptive against native-return variation, not deterministic equivalence. The logical20s path holds for rendering/writer capacity and may take longer in real time; these captures never supply performance bars. Brief shimmer/ghosting, HDR, all scenes/transitions, DH and broad PBR compatibility remain unqualified.

| Pack / route | Optimized vs native mean absolute RGB8 difference | Return vs native |
|---|---:|---:|
| MakeUp / G01 | 4.138 | 2.452 |
| BSL / G02 | 1.506 | 1.387 |
| BSL / C02 | 1.272 | 1.886 |
| Reimagined / C02 | 0.664 | 0.642 |
| Unbound / C02 | 1.159 | 1.104 |
| MakeUp / N04 | 3.312 | 5.081 |

No gross scene loss or obvious routing/block artifact was seen in reviewed samples. MakeUp G01 full-frame differences are larger than return variation, with visibly different cloud phases; the supplemental lower-half samples are comparable to native-return variation. RGB difference alone cannot qualify temporal correctness or acceptable quality. Three earlier incomplete captures remain invalid with raw bytes and metadata retained. The recorder repair holds the logical path after render/writer stalls instead of dropping requested slots; 121 complete PNGs per arm remain required. [Quality summary](quality-summary.json)

## Validation and retention

The Windows/readiness repairs passed103 maintained benchmark tests on both hosts; C01 passed22 Java tests. Adapter repair passed17 Python tests locally plus actual-original assembly/native catalog validation. The recorder clock passes three Java regressions and two Python request tests on both hosts with dev compilation. The per-arm archiver passes three local byte-preservation/live-client/concurrent-change tests and actual native archive verification. The native4K encoder fixture independently round-trips RGBA pixels at PNG levels0/1. [Terminal audit](terminal-audit-v7.json) verifies all217 operations and both canonical profiles.

Cleanup stayed within ShaderLOD. Original packs, live/canonical worlds, useful dependencies/caches and failed evidence remain. Retired raw/recovery copies have independently verified backups; redundant transport/setup/source-freeze copies were removed only after off-host byte verification. The32GiB reserve was preserved. [Artifact locations and recovery](ARTIFACTS.md)
