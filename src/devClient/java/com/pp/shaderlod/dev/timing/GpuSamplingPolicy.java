package com.pp.shaderlod.dev.timing;

/** Pure policy separated from OpenGL so non-nesting schedules can be tested. */
public final class GpuSamplingPolicy {
    public enum Category { FRAME, IRIS, TERRAIN, DH_DRAW, DH_TOTAL, BUFFER_DRAWS, RENDER_AND_BLIT }
    private GpuSamplingPolicy() {}

    public static Decision schedule(long frame, int interval, boolean passTiming, boolean extended) {
        if (frame < 1) throw new IllegalArgumentException("frame must be positive");
        if (interval < 1 || interval > 60) throw new IllegalArgumentException("interval must be 1–60");
        long ordinal = (frame - 1) / interval + 1;
        boolean sampled = (frame - 1) % interval == 0;
        // Rotate by sampled-frame ordinal, never by the unsampled world-frame count.
        return new Decision(sampled, ordinal, category(ordinal, passTiming, extended));
    }

    /** A false decision prohibits issuing queries and polling pending query results. */
    public record Decision(boolean sampled, long sampleOrdinal, Category category) {
        public boolean permitsQueryWork() { return sampled; }
    }

    public static Category category(long frame, boolean passTiming, boolean extended) {
        if (!passTiming) return Category.FRAME;
        if (!extended) return frame % 2 == 0 ? Category.FRAME : Category.IRIS;
        Category[] categories = Category.values();
        return categories[Math.floorMod(frame - 1, categories.length)];
    }
}
