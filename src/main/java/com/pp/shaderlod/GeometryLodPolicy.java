package com.pp.shaderlod;

/** Dependency-free eligibility and effective-radius policy; never changes a preference. */
public final class GeometryLodPolicy {
    public record Inputs(boolean modEnabled, boolean enabled, boolean worldPresent,
                         boolean irisDhReady, boolean dhRendering, boolean coverageConfirmed,
                         int stableTicks, int warmupTicks, int cap) {}

    public record Decision(boolean active, int cap, String reason) {
        public int apply(int originalEffectiveChunks) {
            return active ? Math.min(originalEffectiveChunks, cap) : originalEffectiveChunks;
        }
    }

    private GeometryLodPolicy() {}

    public static Decision decide(Inputs input) {
        int cap = Math.clamp(input.cap(), 8, 32);
        if (!input.modEnabled()) return new Decision(false, cap, "ShaderLOD disabled");
        if (!input.enabled()) return new Decision(false, cap, "terrain policy disabled");
        if (!input.worldPresent()) return new Decision(false, cap, "no world");
        if (!input.irisDhReady()) return new Decision(false, cap, "Iris/DH pipeline unavailable");
        if (!input.dhRendering()) return new Decision(false, cap, "DH rendering unavailable");
        if (!input.coverageConfirmed()) return new Decision(false, cap, "DH replacement coverage unconfirmed");
        if (input.stableTicks() < Math.max(0, input.warmupTicks())) {
            return new Decision(false, cap, "warming DH scene");
        }
        return new Decision(true, cap, "DH terrain cap active");
    }
}
