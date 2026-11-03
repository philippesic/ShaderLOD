package com.pp.shaderlod.fabric.dev;

/** Dev-only completed world-render counter for the explicit finite renderer probe. */
public final class FabricRendererFrames {
    private static long completed;
    private static long compactCalls, inputCommands, outputCommands, reducedCalls;
    private FabricRendererFrames() {}
    public static void completeWorldRender() {
        if (Boolean.getBoolean("shaderlod.dev.fabricRendererSpeedProbe")) completed++;
    }
    public static long completed() { return completed; }
    public static void compacted(int input, int output) {
        if (!Boolean.getBoolean("shaderlod.dev.fabricRendererSpeedProbe")) return;
        compactCalls++; inputCommands += input; outputCommands += output;
        if (output < input) reducedCalls++;
    }
    public static long[] commands() { return new long[]{compactCalls, inputCommands, outputCommands, reducedCalls}; }
}
