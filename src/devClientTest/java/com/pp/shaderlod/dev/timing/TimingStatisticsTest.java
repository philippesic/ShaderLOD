package com.pp.shaderlod.dev.timing;

/** Dependency-free unit check: javac this file with TimingStatistics.java, then run main. */
public final class TimingStatisticsTest {
    public static void main(String[] arguments) {
        var history = new TimingStatistics(4);
        if (history.values().samples() != 0 || !Double.isNaN(history.values().meanMs()))
            throw new AssertionError("empty history must not report a real timing");
        for (long millis : new long[] {1, 2, 10, 3}) history.add(millis * 1_000_000);
        assertNear(4, history.values().meanMs(), "ns -> ms mean");
        assertNear(2.5, history.values().medianMs(), "even median");
        assertNear(10, history.values().p95Ms(), "nearest-rank p95");
        history.add(5_000_000);
        if (history.values().samples() != 4 || history.values().totalSamples() != 5)
            throw new AssertionError("bounded history must retain cumulative count");
        assertNear(5, history.values().meanMs(), "rolling eviction of oldest sample");
        history.clear();
        history.add(9_000_000);
        if (history.values().samples() != 1 || history.values().totalSamples() != 1)
            throw new AssertionError("reset must clear epoch history");
        assertNear(9, history.values().medianMs(), "singleton median");
        assertNear(9, history.values().p95Ms(), "singleton percentile");
        boolean rejected = false;
        try { history.add(-1); } catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("negative query duration accepted");
        System.out.println("GPU timing statistics checks passed");
    }

    private static void assertNear(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000001) throw new AssertionError(message + ": " + actual);
    }
}
