package com.pp.shaderlod.port.dev;
/** Dev-only render completions and actual compactor results; no GL reads or second transform. */
public final class PortRendererFrames {
    private static long frames, calls, input, output, reduced;
    private PortRendererFrames() {}
    private static boolean measuring;
    private static long invalidMeasuredFrames;
    private static int minEffective = Integer.MAX_VALUE, maxEffective;
    private static String firstLimiterViolation;
    public static void beginWindow() { measuring = true; minEffective = Integer.MAX_VALUE; maxEffective = 0; }
    public static void endWindow() { measuring = false; }
    public static long invalidMeasuredFrames() { return invalidMeasuredFrames; }
    public static int minEffective() { return minEffective; }
    public static int maxEffective() { return maxEffective; }
    public static String firstLimiterViolation() { return firstLimiterViolation; }
    public static boolean limiterAllows(int configured, int effective, String reason,
            boolean minimized, boolean focused, boolean iconified) {
        return configured > 0 && effective == configured && "NONE".equals(reason)
                && minimized && focused && !iconified;
    }
    private static void rejectMeasuredFrame(String reason) {
        if (!measuring) return;
        invalidMeasuredFrames++;
        if (firstLimiterViolation == null) firstLimiterViolation = reason;
    }
    public static void unavailableFrame(String reason) {
        if (!Boolean.getBoolean("shaderlod.dev.portRendererSpeedProbe")) return;
        frames++; rejectMeasuredFrame("Limiter observation unavailable: " + reason);
    }
    public static void completed(int configured, int effective, String reason,
            boolean minimized, boolean focused, boolean iconified) {
        if (!Boolean.getBoolean("shaderlod.dev.portRendererSpeedProbe")) return;
        frames++;
        if (!measuring) return;
        minEffective = Math.min(minEffective, effective); maxEffective = Math.max(maxEffective, effective);
        if (!limiterAllows(configured, effective, reason, minimized, focused, iconified))
            rejectMeasuredFrame("configured=" + configured + ",effective=" + effective + ",reason=" + reason
                    + ",MINIMIZED=" + minimized + ",focused=" + focused + ",iconified=" + iconified);
    }
    public static long frames() { return frames; }
    public static void compacted(int before, int after) {
        if (!Boolean.getBoolean("shaderlod.dev.portRendererSpeedProbe")) return;
        calls++; input += before; output += after; if (after < before) reduced++;
    }
    public static boolean stageCommandsAllow(boolean enabled, long calls, long before, long after, long reduced) {
        if (calls < 0 || before < 0 || after < 0 || reduced < 0 || reduced > calls || after > before) return false;
        return enabled ? calls > 0 && reduced > 0 && before > after
                : calls == 0 && before == 0 && after == 0 && reduced == 0;
    }
    public static long[] commands() { return new long[]{calls, input, output, reduced}; }
}
