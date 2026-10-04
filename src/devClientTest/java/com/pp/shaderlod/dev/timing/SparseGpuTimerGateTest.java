package com.pp.shaderlod.dev.timing;

/** Invokes timer entrypoints without a GL context; unsampled frames must never reach GL. */
public final class SparseGpuTimerGateTest {
    public static void main(String[] arguments) {
        System.setProperty("shaderlod.dev.gpuTiming", "true");
        System.setProperty("shaderlod.dev.gpuSampleInterval", "16");
        System.setProperty("shaderlod.dev.cpuTiming", "true");
        GpuTimers.beginTick(true); // First frame is scheduled, but do not issue work without a context.
        if (!GpuTimers.isSampledFrame()) throw new AssertionError("first sparse slot missing");
        GpuTimers.endTick();
        for (int frame = 2; frame <= 16; frame++) {
            GpuTimers.beginTick(true);
            if (GpuTimers.isSampledFrame()) throw new AssertionError("unsampled slot scheduled work");
            CpuTimers.beginFrame(GpuTimers.frameId(), true);
            CpuTimers.begin("sample-gate-cpu");
            GpuTimers.beginFrame(true);
            GpuTimers.beginIrisGroup("deferred");
            GpuTimers.endIrisGroup();
            GpuTimers.beginStandalonePass("final");
            GpuTimers.endStandalonePass();
            GpuTimers.beginCategory("terrain-test", GpuSamplingPolicy.Category.TERRAIN);
            GpuTimers.endCategory();
            GpuTimers.endFrame();
            CpuTimers.end("sample-gate-cpu");
            GpuTimers.endTick();
            var snapshot = GpuTimers.snapshot();
            if (!snapshot.backend().equals("UNINITIALIZED") || !snapshot.scopes().isEmpty()
                    || snapshot.pendingQueries() != 0 || !snapshot.samplingCategory().equals("UNSAMPLED"))
                throw new AssertionError("unsampled entrypoint reached GL/query initialization: " + snapshot);
        }
        if (CpuTimers.drainSamples().size() != 15) throw new AssertionError("sparse GPU cadence suppressed CPU records");
        GpuTimers.beginTick(true);
        if (!GpuTimers.isSampledFrame() || GpuTimers.frameId() != 17)
            throw new AssertionError("second sparse slot missing");
        GpuTimers.endTick();
        System.out.println("Sparse GPU no-context API gating and independent CPU checks passed");
    }
}
