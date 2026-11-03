package com.pp.shaderlod.fabric.dev.budget;

import java.util.Arrays;

/** Bounded rolling history. Values are real query nanoseconds, converted only here. */
public final class TimingStatistics {
    private final double[] milliseconds;
    private int count;
    private int next;
    private long total;

    public TimingStatistics(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        milliseconds = new double[capacity];
    }

    public void add(long nanoseconds) {
        if (nanoseconds < 0) throw new IllegalArgumentException("negative duration");
        milliseconds[next] = nanoseconds / 1_000_000.0;
        next = (next + 1) % milliseconds.length;
        count = Math.min(count + 1, milliseconds.length);
        total++;
    }

    public Values values() {
        if (count == 0) return new Values(0, 0, 0.0, 0.0, 0.0, 0, null);
        double[] sorted = Arrays.copyOf(milliseconds, count);
        Arrays.sort(sorted);
        double mean = Arrays.stream(sorted).average().orElseThrow();
        double median = count % 2 == 0 ? (sorted[count / 2 - 1] + sorted[count / 2]) / 2 : sorted[count / 2];
        int zero = 0;
        while (zero < count && sorted[zero] == 0) zero++;
        return new Values(count, total, mean, median, sorted[(int) Math.ceil(count * .95) - 1],
                zero, zero == count ? null : sorted[zero]);
    }

    public void clear() { count = 0; next = 0; total = 0; }

    public record Values(int samples, long totalSamples, double meanMs, double medianMs, double p95Ms,
                         int zeroSamples, Double minPositiveMs) {}
}
