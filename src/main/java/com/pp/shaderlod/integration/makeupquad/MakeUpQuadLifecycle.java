package com.pp.shaderlod.integration.makeupquad;
/** No optional-library references. A tentative settings transaction cannot activate this backend. */
public final class MakeUpQuadLifecycle {
    private static volatile int depth;
    private static volatile long generation;
    private static long flagsGeneration = -1;
    private MakeUpQuadLifecycle() {}
    public static synchronized void beginSettingsApply() { depth++; generation++; }
    public static synchronized void endSettingsApply() { if (depth > 0) depth--; generation++; }
    public static synchronized void invalidate() { generation++; }
    /** Cheap hot-path epoch; flag generation is inspected independently. */
    public static long epoch() {return generation;}
    public static boolean suspended() { return depth != 0; }
    public static synchronized long generation() {
        long flags=com.pp.shaderlod.optimization.OptimizationConfig.generation();
        if(flags!=flagsGeneration) {flagsGeneration=flags;generation++;}
        return generation;
    }
}
