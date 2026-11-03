package com.pp.shaderlod.fabric.dev;

/** Pure window qualification for the explicit native-only MC1.21.1 control. */
public final class FabricNativeCostWindow {
    private boolean measuring;
    private boolean expectedEnabled;
    private long frames, invalidFrames;
    private int minEffective, maxEffective, minStored, maxStored;
    private String firstInvalid = "";
    public static boolean flagsAllow(String family, boolean speed, boolean source, boolean original,
            boolean dh, boolean ui, boolean roundtrip) {
        return "bsl".equals(family) && speed && !source && !original && !dh && !ui && roundtrip;
    }
    public static boolean singleFlagsAllow(String phase, String family, boolean speed, boolean source,
            boolean original, boolean dh, boolean ui, boolean roundtrip, boolean nativeControl) {
        return ("original".equals(phase) || "on".equals(phase) || "original-return".equals(phase))
                && flagsAllow(family, speed, source, original, dh, ui, roundtrip) && !nativeControl;
    }
    public static boolean phaseEnabled(String phase) { return "on".equals(phase); }
    public void begin() { begin(false); }
    public void begin(boolean expectedEnabled) {
        this.expectedEnabled = expectedEnabled;
        measuring = true; frames = 0;
        minEffective = minStored = Integer.MAX_VALUE;
        maxEffective = maxStored = Integer.MIN_VALUE;
        // Never clear the attempt-wide invalid latch when gates reset a window.
    }
    public void end() { measuring = false; }
    public void observe(int configured, int stored, int effective, boolean focused,
            boolean nativeFocused, boolean iconified, boolean viewport, boolean ready,
            boolean vsync, boolean coreOff, boolean rendererInactive) {
        if (!measuring) return;
        frames++;
        minEffective = Math.min(minEffective, effective); maxEffective = Math.max(maxEffective, effective);
        minStored = Math.min(minStored, stored); maxStored = Math.max(maxStored, stored);
        if (configured != 260 || stored != 260 || effective != 260 || !focused || !nativeFocused
                || iconified || !viewport || !ready || vsync || coreOff == expectedEnabled || rendererInactive == expectedEnabled) {
            invalidFrames++;
            if (firstInvalid.isEmpty()) firstInvalid = "configured=" + configured + ",stored=" + stored
                    + ",effective=" + effective + ",focus=" + focused + ",nativeFocus=" + nativeFocused
                    + ",iconified=" + iconified + ",viewport=" + viewport + ",ready=" + ready
                    + ",vsync=" + vsync + ",expectedEnabled=" + expectedEnabled + ",coreOff=" + coreOff + ",rendererInactive=" + rendererInactive;
        }
    }
    public long frames() { return frames; }
    public long invalidFrames() { return invalidFrames; }
    public int minEffective() { return minEffective; }
    public int maxEffective() { return maxEffective; }
    public int minStored() { return minStored; }
    public int maxStored() { return maxStored; }
    public String firstInvalid() { return firstInvalid; }
}
