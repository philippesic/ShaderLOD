# The tested Asahi setup could not reliably resolve small performance gains

Unchanged shaders produced apparent gains and losses between **−9.70% and +10.24%** across six fresh processes. A comparison that changes nothing should show approximately zero effect. This variation made the tested setup unsuitable for claiming a small optimization benefit.

[View the six-process chart](figure.html). [Numerical data](result.json).

The equal-weight mean apparent gain was +1.24%, but the historical approximate 95% interval ran from −6.22% to +8.70%. All four prespecified scene-stability checks failed. Averaging the runs into a positive number would hide the failed calibration.

## Conditions and limitations

Historical live-functional evidence, inspected and reformatted here; no new benchmark was run. Six processes each supplied four 60-second windows, alternating ABBA and BAAB order. A and B used identical shader contents. The setup was Bliss/native463, mixed-terrain pose 0, Apple M1/Asahi, 2560×1600, closed lid, KWin virtual display and a 1536 MB heap.

This is one scene and one historical setup, not a general verdict on Asahi or virtual displays. The second planned scene was not completed. The interval treats process blocks as replicates; time dependence limits formal inference. All valid blocks were retained, including a documented settings-save overlap. Thermal effects were plausible but unproven; GPU/presentation/wait costs were not isolated.

## Decision

**Revise the measurement setup; leave the cause unresolved.** Do not use this cohort to support small speedup claims. A future controlled unchanged-shader calibration must demonstrate sufficient stability for the intended effect size. No optimization implementation is retained or removed by this import.

## Evidence and source

- [Original interpretation](/home/philip/.local/share/shaderlod-next/evidence/history/original-repository/dev/experiments/headless-development-20261103/final-interpretation.json), sha256:5fa3c3c89f48e9878bbcbc69ed74cccc266e6736e10c77e2d9f5a210f73191d3
- [Original per-process measurements](/home/philip/.local/share/shaderlod-next/evidence/history/original-repository/dev/experiments/headless-development-20261103/baseline-cohort.json), sha256:d616011763cbc29d4afdb40e53eb13331a49956a9eef32eb4da9ee5f029da125
- Each row in [result.json](result.json) identifies its original run directory and the verified digest of its executed build-binding.json source/dependency manifest, alongside plan.json, measured windows and restoration records. Executed-source provenance is retained there; this import has not established that every historical source and dependency can be reconstructed. No original artifacts were deleted.
- Regenerate the figure from retained compact data: `python3 results/asahi-null-calibration/figure.py > results/asahi-null-calibration/figure.html` from the repository root. The generator uses only the Python standard library.

## Closeout

The six chart values were checked against the original cohort. Their mean matches the original interpretation. This closes the presentation/import example; the calibration problem remains unresolved and belongs to benchmark design. Original execution records are unchanged. Repository validation is recorded by the containing commit's local check run.
