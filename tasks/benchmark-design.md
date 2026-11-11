# Benchmark design

## Goal
Design the measurement method that lets an optimization be judged "proven generally positive", then use it for the first new optimization.

## Authorized
Nothing yet. Philip deferred this work; start it only when he asks.

## Done when
Philip approves a method covering scenes, warmup, repetitions, statistics, stop rules, quality tolerance and the target matrix, and one candidate has been accepted or rejected with it.

## State
Not started. Existing pieces: the development profiler (CPU frame intervals, asynchronous GPU timestamps with loss/pending health), the bounded host runner and the evidence contracts. Missing: per-pass cost maps, calibrated profiler overhead, a null calibration, an image-correctness oracle, a stable Asahi display and power setup, and a definition of "generally positive". F2 shows the old closed-lid setup was too noisy for ~3% effects; its numbers are not defaults.

## Next
Wait for Philip. Open questions to bring him: the target matrix (packs, DH states, scenes, settings, machines), the practical effect size worth detecting, the correctness oracle, and the allowed display and power setup.
