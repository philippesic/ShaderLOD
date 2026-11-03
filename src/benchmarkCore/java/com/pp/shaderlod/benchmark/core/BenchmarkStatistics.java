package com.pp.shaderlod.benchmark.core;

import com.pp.shaderlod.measurement.FrameMeasurement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Descriptive statistics and chronological, non-overlapping blocks; no IID frame inference. */
public final class BenchmarkStatistics {
    private BenchmarkStatistics() {}
    public record Block(long firstFrame, long lastFrame, long epoch, int count,
                        double durationMs, double meanMs) {}
    public record Summary(int requestedCount, int count, int missingCount, int invalidCount,
            double durationMs, double meanMs, double throughputFps, double medianMs,
            double p95Ms, double p99Ms, int stutterCount, double stutterThresholdMs,
            int p99TailObservations, String tailStatus, List<Block> blocks,
            int unblockedValidCount, boolean chronologyValid, List<String> reasons,
            AnalysisPolicy policy) {
        public Summary {
            blocks = List.copyOf(blocks);
            reasons = List.copyOf(reasons);
            Objects.requireNonNull(policy);
        }
    }

    public static Summary summarize(List<FrameMeasurement.Sample> samples, AnalysisPolicy policy) {
        Objects.requireNonNull(samples); Objects.requireNonNull(policy);
        var values = new ArrayList<Double>();
        var blocks = new ArrayList<Block>();
        var reasons = new ArrayList<String>();
        int missing = 0, invalid = 0, stutters = 0, blockCount = 0, blocked = 0;
        double sum = 0, blockSum = 0;
        long previousFrame = -1, previousEpoch = -1, firstFrame = -1;
        boolean chronology = true, seen = false;
        for (var sample : samples) {
            if (sample == null) {
                missing++; blockCount = 0; blockSum = 0;
                continue;
            }
            boolean contiguous = seen && sample.epoch() == previousEpoch && sample.frame() == previousFrame + 1;
            if (seen && (sample.epoch() != previousEpoch || sample.frame() <= previousFrame)) {
                chronology = false;
            }
            if (!contiguous) {
                if (seen && sample.epoch() == previousEpoch && sample.frame() > previousFrame + 1
                        && !reasons.contains("frame ID gaps; blocks split without joining across missing capture"))
                    reasons.add("frame ID gaps; blocks split without joining across missing capture");
                blockCount = 0; blockSum = 0;
            }
            previousFrame = sample.frame(); previousEpoch = sample.epoch(); seen = true;
            Long ns = sample.frameWallNs();
            if (ns == null) { missing++; blockCount = 0; blockSum = 0; continue; }
            if (ns <= 0) { invalid++; blockCount = 0; blockSum = 0; continue; }
            double ms = ns / 1_000_000.0;
            values.add(ms); sum += ms;
            if (ms > policy.stutterThresholdMs()) stutters++;
            if (blockCount == 0) firstFrame = sample.frame();
            blockSum += ms; blockCount++;
            if (blockCount == policy.blockFrames()) {
                if (blockSum >= policy.minimumBlockDurationMs()) {
                    blocks.add(new Block(firstFrame, sample.frame(), sample.epoch(), blockCount,
                            blockSum, blockSum / blockCount));
                    blocked += blockCount;
                }
                blockCount = 0; blockSum = 0;
            }
        }
        if (!chronology) reasons.add("non-monotonic frame IDs or mixed epochs");
        if (missing > 0) reasons.add("missing total-frame clock samples; blocks split at gaps");
        if (invalid > 0) reasons.add("nonpositive total-frame clock samples");
        if (blocks.size() < policy.minimumBlocksPerWindow()) reasons.add("insufficient complete duration-qualified chronological blocks");
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(sorted); // quantiles only; blocks above use untouched chronology
        int n = sorted.length;
        double mean = n == 0 ? Double.NaN : sum / n;
        double median = n == 0 ? Double.NaN : (n % 2 == 0
                ? (sorted[n / 2 - 1] + sorted[n / 2]) / 2 : sorted[n / 2]);
        return new Summary(samples.size(), n, missing, invalid, sum, mean,
                n == 0 ? Double.NaN : 1000 / mean, median, percentile(sorted, .95),
                percentile(sorted, .99), stutters, policy.stutterThresholdMs(),
                n == 0 ? 0 : n - (int)Math.ceil(n * .99) + 1,
                "descriptive-only; nearest-rank p95/p99; no tail inference; duration is sum of observed frame intervals",
                blocks, n - blocked, chronology, reasons, policy);
    }

    /** Required timing coverage, independent of optional CPU/GPU clocks and effect inference. */
    public static boolean completeCoverage(Summary s) {
        return s.count() > 0 && s.missingCount() == 0 && s.invalidCount() == 0 && s.chronologyValid()
                && s.reasons().stream().noneMatch(reason -> reason.startsWith("frame ID gaps"));
    }

    private static double percentile(double[] sorted, double quantile) {
        return sorted.length == 0 ? Double.NaN : sorted[(int)Math.ceil(sorted.length * quantile) - 1];
    }
}
