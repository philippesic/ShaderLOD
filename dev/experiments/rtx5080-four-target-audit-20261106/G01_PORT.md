# Native MakeUp G01 validation

The repaired harness completes a valid native Windows RTX5080 ABBA comparison at
3840×2160/native AA, native3.2 context/minimal stack/gallery pose0. The first
attempt is invalid partial evidence; r2 is the first valid independent process.
Its apparent mean reduction is3.27314%, against native control drift−0.27452%.
This is a promising screen, not a repeated benefit or motion-quality conclusion.
Between-process variance is unavailable at n1. The opposite-order process,
matched null controls and bounded quality checks remain required.

| Window | Mean ms | Frame SD ms | Frame variance ms² | p95 ms |
| --- | ---: | ---: | ---: | ---: |
| candidate_0 | 1.964217507 | 0.135169320 | 0.018270745147 | 2.0700 |
| candidate_1 | 1.961452379 | 0.079418276 | 0.006307262588 | 2.0497 |
| native_0 | 2.032044530 | 0.098154504 | 0.009634306580 | 2.1309 |
| native_1 | 2.026466080 | 0.071009644 | 0.005042369572 | 2.1135 |

Native-window mean variance is1.555955359e−5ms²; candidate-window mean variance
is3.822965407e−6ms². These describe window-to-window movement within this process;
they are distinct from per-frame jitter and independent-process variance.
The first candidate window has higher frame SD than the other candidate window;
its full samples are retained rather than trimmed.

Untimed observations see six native final swap copies per completed final pass,
and four candidate copies: target0 and target7 copies disappear, while targets
6/4/1/3 remain. Both native return windows recover all six copies. Metadata
failures, stream mismatches, unknown byte/extent and fallback-copy counts are
zero. The copy observer is disabled before scored capture. These are executed
copy-call witnesses, not measured memory-controller bandwidth or a generalized
alias/history certificate. N02/N03 share this implementation without additive
credit. Native source/settings and selected-work graphs remain separate evidence.

The operation finishes complete with configuration restored, zero clients and
whole-profile reset verified. Evidence: makeup-g01-primary-test-abba-r2/
(summary.json, operation.json, build-binding.json and windows/* mirrored here
with per-file/archive verification; full images/graphs/logs remain native).
The earlier failed run retains its generated config backup and frozen source
archive. Both source freezes are also copied to Asahi/frozen with verified hashes.
