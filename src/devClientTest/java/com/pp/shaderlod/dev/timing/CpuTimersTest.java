package com.pp.shaderlod.dev.timing;

/** Checks separate CPU export, nesting, draining and epoch exclusion without creating a GL context. */
public final class CpuTimersTest {
    public static void main(String[] arguments) {
        System.setProperty("shaderlod.dev.cpuTiming", "true");
        CpuTimers.beginFrame(42, true);
        CpuTimers.begin("outer");
        CpuTimers.beginLayer("inner");
        CpuTimers.endLayer("inner");
        CpuTimers.end("outer");
        var samples = CpuTimers.drainSamples();
        if (samples.size() != 2 || !samples.getFirst().scope().equals("inner") || !samples.getLast().scope().equals("outer"))
            throw new AssertionError("CPU nesting/export incorrect");
        for (var sample : samples) {
            if (sample.frameId() != 42 || sample.wallMilliseconds() < 0
                    || (sample.threadMilliseconds() != null && sample.threadMilliseconds() < 0))
                throw new AssertionError("bad CPU timing record");
        }
        if (!CpuTimers.drainSamples().isEmpty()) throw new AssertionError("drain returned duplicate CPU records");
        CpuTimers.begin("old-epoch");
        CpuTimers.resetSamples();
        CpuTimers.beginLayer("new-epoch");
        CpuTimers.endLayer("new-epoch");
        CpuTimers.end("old-epoch");
        samples = CpuTimers.drainSamples();
        if (samples.size() != 1 || !samples.getFirst().scope().equals("new-epoch") || samples.getFirst().epoch() != 1)
            throw new AssertionError("CPU reset leaked an old open scope");
        CpuTimers.beginFrame(43, false);
        CpuTimers.begin("no-world");
        CpuTimers.end("no-world");
        if (!CpuTimers.drainSamples().isEmpty()) throw new AssertionError("title-screen CPU timings leaked into benchmark");
        System.out.println("CPU timing scope/export/reset checks passed");
    }
}
