package com.pp.shaderlod.dev.timing;

import java.util.EnumMap;

/** Ensures each elapsed frame selects exactly one category; no overlapping active policy. */
public final class GpuSamplingPolicyTest {
    public static void main(String[] arguments) {
        var counts = new EnumMap<GpuSamplingPolicy.Category, Integer>(GpuSamplingPolicy.Category.class);
        for (long frame = 1; frame <= 700; frame++) {
            var category = GpuSamplingPolicy.category(frame, true, true);
            counts.merge(category, 1, Integer::sum);
            if (GpuSamplingPolicy.category(frame, false, true) != GpuSamplingPolicy.Category.FRAME)
                throw new AssertionError("frame-only policy sampled another category");
            var alternate = GpuSamplingPolicy.category(frame, true, false);
            if (alternate != (frame % 2 == 0 ? GpuSamplingPolicy.Category.FRAME : GpuSamplingPolicy.Category.IRIS))
                throw new AssertionError("legacy alternating policy changed");
        }
        for (var category : GpuSamplingPolicy.Category.values())
            if (counts.get(category) != 100) throw new AssertionError("unequal category sampling: " + counts);
        for (int interval : new int[] {1, 2, 7, 16, 60}) {
            counts.clear();
            long marked = 0;
            for (long frame = 1; frame <= 700L * interval; frame++) {
                var decision = GpuSamplingPolicy.schedule(frame, interval, true, true);
                boolean expectedSample = (frame - 1) % interval == 0;
                if (decision.sampled() != expectedSample || decision.permitsQueryWork() != expectedSample)
                    throw new AssertionError("unsampled frame allowed query work");
                if (!decision.sampled()) continue;
                marked++;
                if (decision.sampleOrdinal() != marked) throw new AssertionError("sample ordinal skipped");
                counts.merge(decision.category(), 1, Integer::sum);
            }
            if (marked != 700) throw new AssertionError("bad sparse cadence count");
            for (var category : GpuSamplingPolicy.Category.values())
                if (counts.get(category) != 100) throw new AssertionError("sparse cadence aliased categories: " + counts);
        }
        for (long frame = 1; frame <= 700; frame++)
            if (GpuSamplingPolicy.schedule(frame, 1, true, true).category() != GpuSamplingPolicy.category(frame, true, true))
                throw new AssertionError("default interval1 changed historical schedule");
        for (int invalid : new int[] {0, 61}) {
            boolean rejected = false;
            try { GpuSamplingPolicy.schedule(1, invalid, true, true); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("invalid sparse interval accepted");
        }
        System.out.println("GPU non-nesting sampling-policy checks passed");
    }
}
